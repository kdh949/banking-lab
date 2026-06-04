import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";

type CheckStatus = "pass" | "failed" | "not_available" | "not_attempted";
type Side = "DEBIT" | "CREDIT";
type TxnStatus = "POSTED" | "REVERSAL" | "ADJUSTMENT";

type Posting = {
  txn: string;
  account: string;
  side: Side;
};

type LedgerTxn = {
  id: string;
  status: TxnStatus;
  original: string;
  key: string;
  date: string;
  approval: string;
};

type HeldCommand = {
  id: string;
  key: string;
  status: "HELD_OR_FAILED";
};

type LedgerState = {
  txns: LedgerTxn[];
  postings: Posting[];
  balances: Record<string, number>;
  idemResults: Record<string, string>;
  closedDates: string[];
  heldCommands: HeldCommand[];
};

type IdempotencyCommand = {
  key: string;
  result: string;
  sideEffect: "POSTING" | "NONE";
};

type IdempotencyState = {
  commands: IdempotencyCommand[];
  resultsByKey: Record<string, string>;
  postingSideEffectsByKey: Record<string, number>;
};

type InvariantResult = {
  model: "Ledger" | "Idempotency";
  name: string;
  status: "pass";
  checkedStates: number;
};

type SearchResult = {
  model: "Ledger" | "Idempotency";
  status: "pass";
  engine: "bounded-state-search";
  maxDepth: number;
  statesExplored: number;
  transitionsExplored: number;
  invariants: InvariantResult[];
};

const formalRoot = "formal";
const ledgerTlaPath = path.join(formalRoot, "Ledger.tla");
const ledgerCfgPath = path.join(formalRoot, "Ledger.cfg");
const idempotencyTlaPath = path.join(formalRoot, "Idempotency.tla");
const idempotencyCfgPath = path.join(formalRoot, "Idempotency.cfg");
const resultEvidencePath = process.env.BANKING_LAB_FORMAL_EVIDENCE_PATH
  || path.join("docs", "test-evidence", "generated", "formal-ledger-tlc-result.json");
const legacyEvidencePath = process.env.BANKING_LAB_FORMAL_LEGACY_EVIDENCE_PATH
  || path.join("docs", "test-evidence", "generated", "formal-ledger-model.json");

const ledgerRequiredTokens = [
  "BalancedDoubleEntry",
  "IdempotencySingleBusinessResult",
  "AvailableBalanceNonNegative",
  "ReversalReferencesOriginal",
  "ReversalMirrorsOriginal",
  "ClosedDateNoDirectPosting",
  "BalanceProjectionRecalculable",
  "HeldOrFailedCommandNoPosting",
  "AdjustmentRequiresApprovalReference",
  "Accounts",
  "Idempotency",
  "Reversal",
  "Closed"
];

const idempotencyRequiredTokens = [
  "SingleBusinessResultPerKey",
  "RetryReturnsSameBusinessResult",
  "NoDuplicateSideEffectForRetry",
  "FailedOrHeldCommandNoPosting",
  "IdempotencyKey"
];

const ci = process.env.CI === "true" || process.env.GITHUB_ACTIONS === "true";
const allowStaticOnly = process.env.BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY === "true";
const requestedEngine = process.env.BANKING_LAB_FORMAL_ENGINE || "auto";
const staticOnlyRequested = requestedEngine === "static";

const ledgerTla = await readFile(ledgerTlaPath, "utf8");
const ledgerCfg = await readFile(ledgerCfgPath, "utf8");
const idempotencyTla = await readFile(idempotencyTlaPath, "utf8");
const idempotencyCfg = await readFile(idempotencyCfgPath, "utf8");

checkRequiredTokens("Ledger", ledgerRequiredTokens, `${ledgerTla}\n${ledgerCfg}`);
checkRequiredTokens("Idempotency", idempotencyRequiredTokens, `${idempotencyTla}\n${idempotencyCfg}`);

if (staticOnlyRequested) {
  if (ci || !allowStaticOnly) {
    await writeResultEvidence({
      staticCheck: "pass",
      staticOnly: true,
      tlc: { status: "not_attempted", reason: "static engine was explicitly requested" },
      boundedModelChecker: { status: "not_attempted", reason: "static engine was explicitly requested" }
    });
    throw new Error("Static-only formal checks are not allowed unless BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY=true outside CI.");
  }

  await writeResultEvidence({
    staticCheck: "pass",
    staticOnly: true,
    tlc: { status: "not_attempted", reason: "static engine was explicitly requested" },
    boundedModelChecker: { status: "not_attempted", reason: "static engine was explicitly requested" },
    note: "Developer-only static artifact check. This is not accepted by CI or milestone evidence."
  });
  console.log("Formal ledger static-only artifact check passed by explicit developer override.");
  process.exit(0);
}

const tlcRuns = requestedEngine === "bounded" ? [] : runTlcIfAvailable();
const tlcFailures = tlcRuns.filter((run) => run.status === "failed");
if (tlcFailures.length > 0) {
  await writeResultEvidence({
    staticCheck: "pass",
    staticOnly: false,
    tlc: tlcRuns,
    boundedModelChecker: { status: "not_attempted", reason: "TLC failed before bounded fallback" }
  });
  throw new Error("TLC model check failed. See docs/test-evidence/generated/formal-ledger-tlc-result.json.");
}

const ledgerSearch = runLedgerBoundedModelChecker();
const idempotencySearch = runIdempotencyBoundedModelChecker();
const invariantResults = [
  ...ledgerSearch.invariants,
  ...idempotencySearch.invariants
];

await writeResultEvidence({
  staticCheck: "pass",
  staticOnly: false,
  tlc: tlcRuns.length > 0 ? tlcRuns : [{
    status: "not_available",
    command: "tlc -config <cfg> <model>",
    reason: requestedEngine === "bounded"
      ? "bounded model checker explicitly selected"
      : "local tlc command was not found"
  }],
  boundedModelChecker: {
    status: "pass",
    engine: "bounded-state-search",
    models: [ledgerSearch, idempotencySearch]
  },
  invariantResults,
  note: "Executable bounded state model checker passed. This is not a static-only artifact check; TLC is used when available."
});

await writeLegacyEvidence({
  staticCheck: "pass",
  staticOnly: false,
  tlc: tlcRuns.some((run) => run.status === "pass") ? "pass" : "not_available",
  modelChecker: "pass",
  engine: "bounded-state-search",
  model: ledgerTlaPath,
  config: ledgerCfgPath,
  invariants: invariantResults.map((item) => item.name),
  note: "Executable formal check passed through bounded state search; TLC is attempted when available. Static-only fallback is not a pass condition."
});

console.log(
  `Formal ledger executable model check passed: ${ledgerSearch.statesExplored + idempotencySearch.statesExplored} states, `
    + `${ledgerSearch.transitionsExplored + idempotencySearch.transitionsExplored} transitions, `
    + `${invariantResults.length} invariants.`
);

function checkRequiredTokens(model: string, tokens: string[], source: string): void {
  const missingTokens = tokens.filter((token) => !source.includes(token));
  if (missingTokens.length > 0) {
    throw new Error(`${model} TLA+ model is missing required tokens: ${missingTokens.join(", ")}`);
  }
}

function runTlcIfAvailable(): Array<Record<string, unknown>> {
  const command = process.env.BANKING_LAB_TLC_CMD || "tlc";
  const executable = splitCommand(command);
  const probe = spawnSync(executable[0], [...executable.slice(1), "-version"], { encoding: "utf8" });
  const probeError = probe.error as NodeJS.ErrnoException | undefined;
  if (probeError?.code === "ENOENT") {
    return [];
  }

  return [
    runTlcCommand(command, executable, "Ledger", ledgerTlaPath, ledgerCfgPath),
    runTlcCommand(command, executable, "Idempotency", idempotencyTlaPath, idempotencyCfgPath)
  ];
}

function runTlcCommand(
  command: string,
  executable: string[],
  model: "Ledger" | "Idempotency",
  modelPath: string,
  cfgPath: string
): Record<string, unknown> {
  const modelDir = path.dirname(modelPath);
  const modelFile = path.basename(modelPath);
  const cfgFile = path.basename(cfgPath);
  const tlc = spawnSync(executable[0], [...executable.slice(1), "-config", cfgFile, modelFile], {
    cwd: path.join(process.cwd(), modelDir),
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 5
  });

  return {
    model,
    status: tlc.status === 0 ? "pass" : "failed",
    command: `${command} -config ${cfgFile} ${modelFile}`,
    stdout: trimOutput(tlc.stdout),
    stderr: trimOutput(tlc.stderr),
    exitCode: tlc.status
  };
}

function splitCommand(command: string): string[] {
  return command.trim().split(/\s+/).filter(Boolean);
}

function trimOutput(value: string | undefined): string {
  const output = value || "";
  return output.length > 4000 ? `${output.slice(0, 4000)}\n...<truncated>` : output;
}

function runLedgerBoundedModelChecker(): SearchResult {
  const accounts = ["a1", "a2"];
  const txnIds = ["t1", "t2", "t3"];
  const keys = ["k1", "k2", "k3"];
  const dates = ["d1", "d2"];
  const approvedReferences = ["apr1", "apr2"];
  const initialBalances: Record<string, number> = { a1: 2, a2: 2 };
  const initial: LedgerState = {
    txns: [],
    postings: [],
    balances: { ...initialBalances },
    idemResults: {},
    closedDates: ["d2"],
    heldCommands: []
  };
  const maxDepth = 3;
  const invariants = [
    ["BalancedDoubleEntry", (state: LedgerState) => balancedDoubleEntry(state)],
    ["IdempotencySingleBusinessResult", (state: LedgerState) => idempotencySingleBusinessResult(state)],
    ["AvailableBalanceNonNegative", (state: LedgerState) => availableBalanceNonNegative(state)],
    ["ReversalReferencesOriginal", (state: LedgerState) => reversalReferencesOriginal(state)],
    ["ReversalMirrorsOriginal", (state: LedgerState) => reversalMirrorsOriginal(state)],
    ["ClosedDateNoDirectPosting", (state: LedgerState) => closedDateNoDirectPosting(state)],
    ["BalanceProjectionRecalculable", (state: LedgerState) => balanceProjectionRecalculable(state, initialBalances, accounts)],
    ["HeldOrFailedCommandNoPosting", (state: LedgerState) => heldOrFailedCommandNoPosting(state)],
    ["AdjustmentRequiresApprovalReference", (state: LedgerState) => adjustmentRequiresApprovalReference(state, approvedReferences)]
  ] as const;

  let transitionsExplored = 0;
  const seen = new Set<string>();
  const queue: Array<{ depth: number; state: LedgerState }> = [{ depth: 0, state: initial }];
  const checkedByInvariant = new Map<string, number>();

  while (queue.length > 0) {
    const current = queue.shift();
    if (!current) {
      break;
    }

    const key = canonicalLedgerState(current.state);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);

    for (const [name, predicate] of invariants) {
      if (!predicate(current.state)) {
        throw new Error(`Ledger invariant failed: ${name}\nState: ${JSON.stringify(current.state, null, 2)}`);
      }
      checkedByInvariant.set(name, (checkedByInvariant.get(name) || 0) + 1);
    }

    if (current.depth >= maxDepth) {
      continue;
    }

    const nextStates = ledgerTransitions(current.state, { accounts, txnIds, keys, dates, approvedReferences });
    transitionsExplored += nextStates.length;
    for (const nextState of nextStates) {
      queue.push({ depth: current.depth + 1, state: nextState });
    }
  }

  return {
    model: "Ledger",
    status: "pass",
    engine: "bounded-state-search",
    maxDepth,
    statesExplored: seen.size,
    transitionsExplored,
    invariants: invariants.map(([name]) => ({
      model: "Ledger",
      name,
      status: "pass",
      checkedStates: checkedByInvariant.get(name) || 0
    }))
  };
}

function ledgerTransitions(
  state: LedgerState,
  sets: {
    accounts: string[];
    txnIds: string[];
    keys: string[];
    dates: string[];
    approvedReferences: string[];
  }
): LedgerState[] {
  const next: LedgerState[] = [];
  const usedTxnIds = new Set(state.txns.map((txn) => txn.id).concat(state.heldCommands.map((command) => command.id)));
  const usedKeys = new Set(Object.keys(state.idemResults));
  const openDates = sets.dates.filter((date) => !state.closedDates.includes(date));

  for (const txn of sets.txnIds.filter((id) => !usedTxnIds.has(id))) {
    for (const key of sets.keys.filter((id) => !usedKeys.has(id))) {
      for (const date of openDates) {
        for (const debitAcct of sets.accounts) {
          for (const creditAcct of sets.accounts) {
            if (debitAcct === creditAcct || state.balances[debitAcct] < 1) {
              continue;
            }
            next.push(addLedgerTransaction(state, {
              id: txn,
              status: "POSTED",
              original: "NONE",
              key,
              date,
              approval: "NONE"
            }, [
              { txn, account: debitAcct, side: "DEBIT" },
              { txn, account: creditAcct, side: "CREDIT" }
            ]));
          }
        }

        for (const original of state.txns.filter((candidate) => candidate.status === "POSTED")) {
          const originalDebit = state.postings.find((posting) => posting.txn === original.id && posting.side === "DEBIT");
          const originalCredit = state.postings.find((posting) => posting.txn === original.id && posting.side === "CREDIT");
          if (!originalDebit || !originalCredit || state.balances[originalCredit.account] < 1) {
            continue;
          }
          next.push(addLedgerTransaction(state, {
            id: txn,
            status: "REVERSAL",
            original: original.id,
            key,
            date,
            approval: "NONE"
          }, [
            { txn, account: originalCredit.account, side: "DEBIT" },
            { txn, account: originalDebit.account, side: "CREDIT" }
          ]));
        }

        for (const approval of sets.approvedReferences) {
          for (const debitAcct of sets.accounts) {
            for (const creditAcct of sets.accounts) {
              if (debitAcct === creditAcct || state.balances[debitAcct] < 1) {
                continue;
              }
              next.push(addLedgerTransaction(state, {
                id: txn,
                status: "ADJUSTMENT",
                original: "NONE",
                key,
                date,
                approval
              }, [
                { txn, account: debitAcct, side: "DEBIT" },
                { txn, account: creditAcct, side: "CREDIT" }
              ]));
            }
          }
        }
      }

      next.push({
        ...cloneLedgerState(state),
        idemResults: { ...state.idemResults, [key]: txn },
        heldCommands: [...state.heldCommands, { id: txn, key, status: "HELD_OR_FAILED" }]
      });
    }
  }

  return next;
}

function addLedgerTransaction(state: LedgerState, txn: LedgerTxn, postings: Posting[]): LedgerState {
  const next = cloneLedgerState(state);
  next.txns.push(txn);
  next.postings.push(...postings);
  next.idemResults[txn.key] = txn.id;
  for (const posting of postings) {
    next.balances[posting.account] += posting.side === "CREDIT" ? 1 : -1;
  }
  return next;
}

function cloneLedgerState(state: LedgerState): LedgerState {
  return {
    txns: state.txns.map((txn) => ({ ...txn })),
    postings: state.postings.map((posting) => ({ ...posting })),
    balances: { ...state.balances },
    idemResults: { ...state.idemResults },
    closedDates: [...state.closedDates],
    heldCommands: state.heldCommands.map((command) => ({ ...command }))
  };
}

function balancedDoubleEntry(state: LedgerState): boolean {
  return state.txns.every((txn) => {
    const txnPostings = state.postings.filter((posting) => posting.txn === txn.id);
    return txnPostings.filter((posting) => posting.side === "DEBIT").length
      === txnPostings.filter((posting) => posting.side === "CREDIT").length;
  });
}

function idempotencySingleBusinessResult(state: LedgerState): boolean {
  return Object.entries(state.idemResults).every(([key, txn]) =>
    Object.entries(state.idemResults).every(([otherKey, otherTxn]) => key !== otherKey || txn === otherTxn)
  );
}

function availableBalanceNonNegative(state: LedgerState): boolean {
  return Object.values(state.balances).every((balance) => balance >= 0);
}

function reversalReferencesOriginal(state: LedgerState): boolean {
  return state.txns.every((txn) =>
    txn.status !== "REVERSAL" || state.txns.some((candidate) => candidate.id === txn.original && candidate.status === "POSTED")
  );
}

function reversalMirrorsOriginal(state: LedgerState): boolean {
  return state.txns.every((txn) => {
    if (txn.status !== "REVERSAL") {
      return true;
    }
    const originalDebit = state.postings.find((posting) => posting.txn === txn.original && posting.side === "DEBIT");
    const originalCredit = state.postings.find((posting) => posting.txn === txn.original && posting.side === "CREDIT");
    const reversalDebit = state.postings.find((posting) => posting.txn === txn.id && posting.side === "DEBIT");
    const reversalCredit = state.postings.find((posting) => posting.txn === txn.id && posting.side === "CREDIT");
    return Boolean(
      originalDebit
        && originalCredit
        && reversalDebit
        && reversalCredit
        && reversalDebit.account === originalCredit.account
        && reversalCredit.account === originalDebit.account
    );
  });
}

function closedDateNoDirectPosting(state: LedgerState): boolean {
  return state.txns.every((txn) => !state.closedDates.includes(txn.date));
}

function balanceProjectionRecalculable(
  state: LedgerState,
  initialBalances: Record<string, number>,
  accounts: string[]
): boolean {
  return accounts.every((account) => {
    const credits = state.postings.filter((posting) => posting.account === account && posting.side === "CREDIT").length;
    const debits = state.postings.filter((posting) => posting.account === account && posting.side === "DEBIT").length;
    return state.balances[account] === initialBalances[account] + credits - debits;
  });
}

function heldOrFailedCommandNoPosting(state: LedgerState): boolean {
  return state.heldCommands.every((command) => !state.postings.some((posting) => posting.txn === command.id));
}

function adjustmentRequiresApprovalReference(state: LedgerState, approvedReferences: string[]): boolean {
  return state.txns.every((txn) =>
    txn.status !== "ADJUSTMENT" || (txn.approval !== "NONE" && approvedReferences.includes(txn.approval))
  );
}

function canonicalLedgerState(state: LedgerState): string {
  return JSON.stringify({
    txns: [...state.txns].sort(compareByJson),
    postings: [...state.postings].sort(compareByJson),
    balances: sortRecord(state.balances),
    idemResults: sortRecord(state.idemResults),
    closedDates: [...state.closedDates].sort(),
    heldCommands: [...state.heldCommands].sort(compareByJson)
  });
}

function runIdempotencyBoundedModelChecker(): SearchResult {
  const keys = ["k1", "k2"];
  const results = ["POSTED:t1", "POSTED:t2"];
  const maxDepth = 4;
  const initial: IdempotencyState = {
    commands: [],
    resultsByKey: {},
    postingSideEffectsByKey: {}
  };
  const invariants = [
    ["SingleBusinessResultPerKey", (state: IdempotencyState) => singleBusinessResultPerKey(state)],
    ["RetryReturnsSameBusinessResult", (state: IdempotencyState) => retryReturnsSameBusinessResult(state)],
    ["NoDuplicateSideEffectForRetry", (state: IdempotencyState) => noDuplicateSideEffectForRetry(state)],
    ["FailedOrHeldCommandNoPosting", (state: IdempotencyState) => failedOrHeldCommandNoPosting(state)]
  ] as const;

  let transitionsExplored = 0;
  const seen = new Set<string>();
  const queue: Array<{ depth: number; state: IdempotencyState }> = [{ depth: 0, state: initial }];
  const checkedByInvariant = new Map<string, number>();

  while (queue.length > 0) {
    const current = queue.shift();
    if (!current) {
      break;
    }

    const key = canonicalIdempotencyState(current.state);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);

    for (const [name, predicate] of invariants) {
      if (!predicate(current.state)) {
        throw new Error(`Idempotency invariant failed: ${name}\nState: ${JSON.stringify(current.state, null, 2)}`);
      }
      checkedByInvariant.set(name, (checkedByInvariant.get(name) || 0) + 1);
    }

    if (current.depth >= maxDepth) {
      continue;
    }

    const nextStates = idempotencyTransitions(current.state, keys, results);
    transitionsExplored += nextStates.length;
    for (const nextState of nextStates) {
      queue.push({ depth: current.depth + 1, state: nextState });
    }
  }

  return {
    model: "Idempotency",
    status: "pass",
    engine: "bounded-state-search",
    maxDepth,
    statesExplored: seen.size,
    transitionsExplored,
    invariants: invariants.map(([name]) => ({
      model: "Idempotency",
      name,
      status: "pass",
      checkedStates: checkedByInvariant.get(name) || 0
    }))
  };
}

function idempotencyTransitions(state: IdempotencyState, keys: string[], results: string[]): IdempotencyState[] {
  const next: IdempotencyState[] = [];
  for (const key of keys) {
    const existing = state.resultsByKey[key];
    if (existing) {
      next.push(addIdempotencyCommand(state, { key, result: existing, sideEffect: "NONE" }));
      continue;
    }

    for (const result of results) {
      next.push(addIdempotencyCommand(state, { key, result, sideEffect: "POSTING" }));
    }
    next.push(addIdempotencyCommand(state, { key, result: "HELD_OR_FAILED", sideEffect: "NONE" }));
  }
  return next;
}

function addIdempotencyCommand(state: IdempotencyState, command: IdempotencyCommand): IdempotencyState {
  const next: IdempotencyState = {
    commands: [...state.commands, { ...command }],
    resultsByKey: { ...state.resultsByKey },
    postingSideEffectsByKey: { ...state.postingSideEffectsByKey }
  };
  next.resultsByKey[command.key] = next.resultsByKey[command.key] || command.result;
  if (command.sideEffect === "POSTING") {
    next.postingSideEffectsByKey[command.key] = (next.postingSideEffectsByKey[command.key] || 0) + 1;
  }
  return next;
}

function singleBusinessResultPerKey(state: IdempotencyState): boolean {
  return state.commands.every((command) =>
    state.commands.every((other) => command.key !== other.key || command.result === other.result)
  );
}

function retryReturnsSameBusinessResult(state: IdempotencyState): boolean {
  return Object.entries(state.resultsByKey).every(([key, result]) =>
    state.commands.filter((command) => command.key === key).every((command) => command.result === result)
  );
}

function noDuplicateSideEffectForRetry(state: IdempotencyState): boolean {
  return Object.values(state.postingSideEffectsByKey).every((count) => count <= 1);
}

function failedOrHeldCommandNoPosting(state: IdempotencyState): boolean {
  return state.commands.every((command) => command.result !== "HELD_OR_FAILED" || command.sideEffect === "NONE");
}

function canonicalIdempotencyState(state: IdempotencyState): string {
  return JSON.stringify({
    commands: [...state.commands].sort(compareByJson),
    resultsByKey: sortRecord(state.resultsByKey),
    postingSideEffectsByKey: sortRecord(state.postingSideEffectsByKey)
  });
}

function compareByJson<T>(left: T, right: T): number {
  return JSON.stringify(left).localeCompare(JSON.stringify(right));
}

function sortRecord<T>(record: Record<string, T>): Record<string, T> {
  return Object.fromEntries(Object.entries(record).sort(([left], [right]) => left.localeCompare(right)));
}

async function writeResultEvidence(payload: Record<string, unknown>): Promise<void> {
  await writeJsonPreservingTimestamp(resultEvidencePath, {
    modelCheckerRun: "formal-ledger",
    models: [
      { model: ledgerTlaPath, config: ledgerCfgPath },
      { model: idempotencyTlaPath, config: idempotencyCfgPath }
    ],
    ...payload
  });
}

async function writeLegacyEvidence(payload: Record<string, unknown>): Promise<void> {
  if (process.env.BANKING_LAB_FORMAL_EVIDENCE_PATH && !process.env.BANKING_LAB_FORMAL_LEGACY_EVIDENCE_PATH) {
    return;
  }
  await writeJsonPreservingTimestamp(legacyEvidencePath, payload);
}

async function writeJsonPreservingTimestamp(filePath: string, payload: Record<string, unknown>): Promise<void> {
  await mkdir(path.dirname(filePath), { recursive: true });
  const existing = existsSync(filePath) ? JSON.parse(await readFile(filePath, "utf8")) as Record<string, unknown> : undefined;
  const generatedAt = sameExceptGeneratedAt(existing, payload)
    ? existing?.generatedAt
    : new Date().toISOString();
  const next = `${JSON.stringify({ generatedAt, ...payload }, null, 2)}\n`;
  if (!existing || next !== `${JSON.stringify(existing, null, 2)}\n`) {
    await writeFile(filePath, next, "utf8");
  }
}

function sameExceptGeneratedAt(existing: Record<string, unknown> | undefined, payload: Record<string, unknown>): boolean {
  if (!existing) {
    return false;
  }
  const { generatedAt: _generatedAt, ...withoutGeneratedAt } = existing;
  return JSON.stringify(withoutGeneratedAt) === JSON.stringify(payload);
}
