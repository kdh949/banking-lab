"use client";

import { useEffect, useState } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type CustomerAccountDetailDto,
  type CustomerAccountListItemDto,
  type CustomerAuthResponse,
  type CustomerTransactionDto,
  type CustomerTransferResponse,
  type CustomerTransferStatusDto,
  type InternalRecipientAccountDto
} from "@banking-lab/api-client";
import {
  ChannelBadge,
  ChannelMetric,
  ChannelMetricGrid,
  ChannelPanel,
  ChannelShell,
  ChannelTable
} from "../../../../packages/channel-ui/src";

type StoredCustomerSession = {
  readonly customerId: string;
  readonly username: string;
  readonly authorizationHeader: string;
  readonly expiresAt: string;
  readonly sessionId: string;
};

type UiError = {
  readonly code: string;
  readonly message: string;
  readonly statusCode?: number;
  readonly domain?: string;
  readonly route?: string;
};

type LoadState<T> =
  | { readonly status: "idle" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly value: T }
  | { readonly status: "failed"; readonly error: UiError };

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const sessionStorageKey = "bankingLabCustomerSyntheticSession";
const transferResultStorageKey = "bankingLabCustomerTransferResults";

export function CustomerSignupForm() {
  const [session, setSession] = useStoredSession();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [syntheticCustomerName, setSyntheticCustomerName] = useState("");
  const [syntheticPhone, setSyntheticPhone] = useState("010-0000-0000");
  const [syntheticAddress, setSyntheticAddress] = useState("Synthetic self-service address");
  const [idempotencyKey, setIdempotencyKey] = useState(() => nextIdempotencyKey("CWB-SIGNUP"));
  const [result, setResult] = useState<LoadState<CustomerAuthResponse>>({ status: "idle" });

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!apiBaseUrl) {
      setResult({ status: "failed", error: demoFallbackError() });
      return;
    }
    setResult({ status: "loading" });
    try {
      const response = await createBankingApiClient({ baseUrl: apiBaseUrl }).signupCustomer({
        idempotencyKey,
        username,
        password,
        syntheticCustomerName,
        syntheticPhone,
        syntheticAddress
      });
      saveSessionFromAuth(response);
      setSession(readStoredSession());
      setResult({ status: "loaded", value: response });
    } catch (error: unknown) {
      setResult({ status: "failed", error: parseError(error) });
    }
  };

  return (
    <SelfServiceShell title="Customer Signup" status={session ? `Signed in · ${session.username}` : "Synthetic auth"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelMetricGrid>
        <ChannelMetric label="Auth boundary" value="synthetic" detail="No real identity provider provisioning" />
        <ChannelMetric label="Password storage" value="hashed" detail="Plaintext never persisted" />
        <ChannelMetric label="Replay policy" value="idempotent" detail="Signup key returns replay status" />
      </ChannelMetricGrid>
      <ChannelPanel title="Signup" eyebrow="CWB-001">
        <form className="self-service-form" onSubmit={submit}>
          <label>
            Username
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
          </label>
          <label>
            Password
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete="new-password" required />
          </label>
          <label>
            Synthetic customer name
            <input value={syntheticCustomerName} onChange={(event) => setSyntheticCustomerName(event.target.value)} required />
          </label>
          <label>
            Synthetic phone
            <input value={syntheticPhone} onChange={(event) => setSyntheticPhone(event.target.value)} required />
          </label>
          <label>
            Synthetic address
            <input value={syntheticAddress} onChange={(event) => setSyntheticAddress(event.target.value)} required />
          </label>
          <label>
            Idempotency key
            <input value={idempotencyKey} onChange={(event) => setIdempotencyKey(event.target.value)} required />
          </label>
          <div className="self-service-actions">
            <button type="submit" disabled={result.status === "loading"}>{result.status === "loading" ? "Submitting" : "Create synthetic customer"}</button>
            <button type="button" onClick={() => setIdempotencyKey(nextIdempotencyKey("CWB-SIGNUP"))}>New key</button>
          </div>
        </form>
      </ChannelPanel>
      <AuthResultPanel state={result} />
    </SelfServiceShell>
  );
}

export function CustomerLoginForm() {
  const [session, setSession] = useStoredSession();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [result, setResult] = useState<LoadState<CustomerAuthResponse>>({ status: "idle" });

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!apiBaseUrl) {
      setResult({ status: "failed", error: demoFallbackError() });
      return;
    }
    setResult({ status: "loading" });
    try {
      const response = await createBankingApiClient({ baseUrl: apiBaseUrl }).loginCustomer({ username, password });
      saveSessionFromAuth(response);
      setSession(readStoredSession());
      setResult({ status: "loaded", value: response });
    } catch (error: unknown) {
      setResult({ status: "failed", error: parseError(error) });
    }
  };

  const signOut = () => {
    clearStoredSession();
    setSession(null);
  };

  return (
    <SelfServiceShell title="Customer Login" status={session ? `Signed in · ${session.username}` : "No local session"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelPanel title="Login" eyebrow="CWB-002">
        <form className="self-service-form self-service-form-compact" onSubmit={submit}>
          <label>
            Username
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
          </label>
          <label>
            Password
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete="current-password" required />
          </label>
          <div className="self-service-actions">
            <button type="submit" disabled={result.status === "loading"}>{result.status === "loading" ? "Signing in" : "Sign in"}</button>
            <button type="button" onClick={signOut} disabled={!session}>Clear session</button>
          </div>
        </form>
      </ChannelPanel>
      {session ? <SessionPanel session={session} /> : null}
      <AuthResultPanel state={result} />
    </SelfServiceShell>
  );
}

export function CustomerAccountsView() {
  const [session] = useStoredSession();
  const [state, setState] = useState<LoadState<readonly CustomerAccountListItemDto[]>>({ status: "idle" });

  useEffect(() => {
    if (!apiBaseUrl) {
      setState({ status: "failed", error: demoFallbackError() });
      return;
    }
    if (!session) {
      setState({ status: "failed", error: authRequiredError() });
      return;
    }
    setState({ status: "loading" });
    authedClient(session)
      .customerAccounts(session.customerId)
      .then((response) => setState({ status: "loaded", value: response.items }))
      .catch((error: unknown) => setState({ status: "failed", error: parseError(error) }));
  }, [session]);

  return (
    <SelfServiceShell title="Accounts" status={session ? `Customer · ${session.customerId}` : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      {session ? <SessionPanel session={session} /> : null}
      <ChannelPanel title="Owned Accounts" eyebrow="CWB-101">
        <LoadBoundary state={state}>
          {(accounts) => (
            <ChannelTable>
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Status</th>
                  <th>Available</th>
                  <th>Ledger</th>
                  <th>Detail</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map((account) => (
                  <tr key={account.accountId}>
                    <td>{account.maskedAccountNo}</td>
                    <td>{account.status}</td>
                    <td>{formatMinor(account.availableBalanceMinor, account.currency)}</td>
                    <td>{formatMinor(account.ledgerBalanceMinor, account.currency)}</td>
                    <td><a href={`/accounts/${encodeURIComponent(account.accountId)}`}>Open</a></td>
                  </tr>
                ))}
              </tbody>
            </ChannelTable>
          )}
        </LoadBoundary>
      </ChannelPanel>
    </SelfServiceShell>
  );
}

export function CustomerAccountDetailView({ accountId }: { readonly accountId: string }) {
  const [session] = useStoredSession();
  const [detailState, setDetailState] = useState<LoadState<CustomerAccountDetailDto>>({ status: "idle" });
  const [historyState, setHistoryState] = useState<LoadState<readonly CustomerTransactionDto[]>>({ status: "idle" });

  useEffect(() => {
    if (!apiBaseUrl) {
      setDetailState({ status: "failed", error: demoFallbackError() });
      setHistoryState({ status: "failed", error: demoFallbackError() });
      return;
    }
    if (!session) {
      setDetailState({ status: "failed", error: authRequiredError() });
      setHistoryState({ status: "failed", error: authRequiredError() });
      return;
    }
    const client = authedClient(session);
    setDetailState({ status: "loading" });
    setHistoryState({ status: "loading" });
    client
      .customerAccountDetail(accountId, session.customerId)
      .then((detail) => setDetailState({ status: "loaded", value: detail }))
      .catch((error: unknown) => setDetailState({ status: "failed", error: parseError(error) }));
    client
      .customerTransactions(session.customerId, accountId)
      .then((history) => setHistoryState({ status: "loaded", value: history.items }))
      .catch((error: unknown) => setHistoryState({ status: "failed", error: parseError(error) }));
  }, [accountId, session]);

  return (
    <SelfServiceShell title={`Account ${accountId}`} status={session ? "Masked detail" : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelPanel title="Account Detail" eyebrow="CWB-102">
        <LoadBoundary state={detailState}>
          {(detail) => (
            <dl className="self-service-definition-list">
              <div><dt>Masked account</dt><dd>{detail.maskedAccountNo}</dd></div>
              <div><dt>Status</dt><dd>{detail.status}</dd></div>
              <div><dt>Available</dt><dd>{formatMinor(detail.availableBalanceMinor, detail.currency)}</dd></div>
              <div><dt>Ledger</dt><dd>{formatMinor(detail.ledgerBalanceMinor, detail.currency)}</dd></div>
              <div><dt>Hold</dt><dd>{formatMinor(detail.holdAmountMinor, detail.currency)}</dd></div>
            </dl>
          )}
        </LoadBoundary>
      </ChannelPanel>
      <ChannelPanel title="Transactions" eyebrow="CWB-103">
        <LoadBoundary state={historyState}>
          {(items) => (
            <ChannelTable>
              <thead>
                <tr>
                  <th>Transaction</th>
                  <th>Type</th>
                  <th>Direction</th>
                  <th>Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={`${item.transactionId}-${item.direction}-${item.amountMinor}`}>
                    <td>{item.transactionId}</td>
                    <td>{item.transactionType}</td>
                    <td>{item.direction}</td>
                    <td>{formatMinor(item.amountMinor, item.currency)}</td>
                    <td>{item.status}</td>
                  </tr>
                ))}
              </tbody>
            </ChannelTable>
          )}
        </LoadBoundary>
      </ChannelPanel>
    </SelfServiceShell>
  );
}

export function CustomerTransferForm() {
  const [session] = useStoredSession();
  const [accountsState, setAccountsState] = useState<LoadState<readonly CustomerAccountListItemDto[]>>({ status: "idle" });
  const [fromAccountId, setFromAccountId] = useState("");
  const [recipientQuery, setRecipientQuery] = useState("");
  const [recipient, setRecipient] = useState<LoadState<InternalRecipientAccountDto>>({ status: "idle" });
  const [amountMinor, setAmountMinor] = useState("1000");
  const [idempotencyKey, setIdempotencyKey] = useState(() => nextIdempotencyKey("CWB-TRF"));
  const [result, setResult] = useState<LoadState<CustomerTransferResponse>>({ status: "idle" });

  useEffect(() => {
    if (!apiBaseUrl) {
      setAccountsState({ status: "failed", error: demoFallbackError() });
      return;
    }
    if (!session) {
      setAccountsState({ status: "failed", error: authRequiredError() });
      return;
    }
    setAccountsState({ status: "loading" });
    authedClient(session)
      .customerAccounts(session.customerId)
      .then((response) => {
        setAccountsState({ status: "loaded", value: response.items });
        setFromAccountId((current) => current || response.items[0]?.accountId || "");
      })
      .catch((error: unknown) => setAccountsState({ status: "failed", error: parseError(error) }));
  }, [session]);

  const lookupRecipient = async () => {
    if (!session) {
      setRecipient({ status: "failed", error: authRequiredError() });
      return;
    }
    setRecipient({ status: "loading" });
    try {
      const response = await authedClient(session).internalRecipientLookup(recipientQuery);
      setRecipient({ status: "loaded", value: response.item });
    } catch (error: unknown) {
      setRecipient({ status: "failed", error: parseError(error) });
    }
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!session) {
      setResult({ status: "failed", error: authRequiredError() });
      return;
    }
    if (recipient.status !== "loaded") {
      setResult({ status: "failed", error: { code: "RECIPIENT_LOOKUP_REQUIRED", message: "Look up an internal recipient before submitting." } });
      return;
    }
    setResult({ status: "loading" });
    try {
      const response = await authedClient(session).requestCustomerTransfer({
        customerId: session.customerId,
        fromAccountId,
        toAccountId: recipient.value.accountId,
        amountMinor: Number(amountMinor),
        idempotencyKey,
        requestedBy: session.username,
        reason: "Synthetic customer-web internal transfer"
      });
      rememberTransferResult(response);
      setResult({ status: "loaded", value: response });
    } catch (error: unknown) {
      setResult({ status: "failed", error: parseError(error) });
    }
  };

  const selectedAccounts = accountsState.status === "loaded" ? accountsState.value : [];

  return (
    <SelfServiceShell title="New Transfer" status={session ? "Internal synthetic only" : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelPanel title="Transfer" eyebrow="CWB-201">
        <form className="self-service-form" onSubmit={submit}>
          <label>
            Source account
            <select value={fromAccountId} onChange={(event) => setFromAccountId(event.target.value)} required>
              {selectedAccounts.map((account) => (
                <option key={account.accountId} value={account.accountId}>
                  {account.maskedAccountNo} · {formatMinor(account.availableBalanceMinor, account.currency)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Recipient account number or ID
            <input value={recipientQuery} onChange={(event) => setRecipientQuery(event.target.value)} required />
          </label>
          <label>
            Amount minor units
            <input value={amountMinor} onChange={(event) => setAmountMinor(event.target.value)} inputMode="numeric" required />
          </label>
          <label>
            Idempotency key
            <input value={idempotencyKey} onChange={(event) => setIdempotencyKey(event.target.value)} required />
          </label>
          <div className="self-service-actions">
            <button type="button" onClick={lookupRecipient} disabled={!recipientQuery || recipient.status === "loading"}>Lookup recipient</button>
            <button type="submit" disabled={result.status === "loading" || recipient.status !== "loaded"}>{result.status === "loading" ? "Submitting" : "Submit transfer"}</button>
            <button type="button" onClick={() => setIdempotencyKey(nextIdempotencyKey("CWB-TRF"))}>New key</button>
          </div>
        </form>
      </ChannelPanel>
      <RecipientPanel state={recipient} />
      <TransferResultPanel state={result} />
    </SelfServiceShell>
  );
}

export function CustomerTransferResultView({ resultId }: { readonly resultId: string }) {
  const [session] = useStoredSession();
  const [state, setState] = useState<LoadState<CustomerTransferStatusDto | CustomerTransferResponse>>({ status: "idle" });

  useEffect(() => {
    const remembered = readRememberedTransferResult(resultId);
    if (remembered) {
      setState({ status: "loaded", value: remembered });
    }
    if (!apiBaseUrl) {
      if (!remembered) {
        setState({ status: "failed", error: demoFallbackError() });
      }
      return;
    }
    if (!session) {
      if (!remembered) {
        setState({ status: "failed", error: authRequiredError() });
      }
      return;
    }
    authedClient(session)
      .customerTransfers(session.customerId)
      .then((response) => {
        const item = response.items.find((candidate) =>
          candidate.resultId === resultId ||
          candidate.idempotencyKey === resultId ||
          candidate.transactionId === resultId ||
          candidate.caseId === resultId
        );
        if (item) {
          setState({ status: "loaded", value: item });
        } else if (!remembered) {
          setState({ status: "failed", error: { code: "TRANSFER_RESULT_NOT_FOUND", message: "No transfer result matched this identifier." } });
        }
      })
      .catch((error: unknown) => {
        if (!remembered) {
          setState({ status: "failed", error: parseError(error) });
        }
      });
  }, [resultId, session]);

  return (
    <SelfServiceShell title={`Transfer Result ${resultId}`} status={session ? "Customer owned" : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelPanel title="Result" eyebrow="CWB-202 · CWB-203">
        <LoadBoundary state={state}>
          {(value) => <TransferStatusSummary value={value} />}
        </LoadBoundary>
      </ChannelPanel>
    </SelfServiceShell>
  );
}

function SelfServiceShell({ title, status, children }: { readonly title: string; readonly status: string; readonly children: React.ReactNode }) {
  return (
    <ChannelShell appId="customer-web" eyebrow="Customer self-service" title={title} status={status}>
      {children}
    </ChannelShell>
  );
}

function SelfServiceNav() {
  return (
    <ChannelPanel title="Self-Service Journey" eyebrow="synthetic E2E">
      <nav className="self-service-nav" aria-label="Customer self-service">
        <a href="/signup">Signup</a>
        <a href="/login">Login</a>
        <a href="/accounts">Accounts</a>
        <a href="/transfers/new">Transfer</a>
      </nav>
    </ChannelPanel>
  );
}

function DemoFallbackBanner() {
  if (apiBaseUrl) {
    return null;
  }
  return (
    <ChannelPanel title="Demo Fallback" eyebrow="configuration">
      <p className="self-service-muted">
        Live customer self-service calls are disabled because NEXT_PUBLIC_BANKING_API_BASE_URL is not configured. No hard-coded customer or account IDs are used on this route.
      </p>
    </ChannelPanel>
  );
}

function SessionPanel({ session }: { readonly session: StoredCustomerSession }) {
  return (
    <ChannelPanel title="Session" eyebrow="local synthetic token">
      <dl className="self-service-definition-list">
        <div><dt>Customer</dt><dd>{session.customerId}</dd></div>
        <div><dt>Username</dt><dd>{session.username}</dd></div>
        <div><dt>Session</dt><dd>{session.sessionId}</dd></div>
        <div><dt>Expires</dt><dd>{session.expiresAt}</dd></div>
      </dl>
    </ChannelPanel>
  );
}

function AuthResultPanel({ state }: { readonly state: LoadState<CustomerAuthResponse> }) {
  return (
    <ChannelPanel title="Auth Result" eyebrow="structured state">
      <LoadBoundary state={state} idle="Submit credentials to create a session.">
        {(response) => (
          <dl className="self-service-definition-list">
            <div><dt>Status</dt><dd>{response.replayed ? "REPLAYED" : "CUSTOMER_SESSION_ACTIVE"}</dd></div>
            <div><dt>Customer</dt><dd>{response.customer.customerId}</dd></div>
            <div><dt>Username</dt><dd>{response.customer.username}</dd></div>
            <div><dt>KYC</dt><dd>{response.customer.kycStatus}</dd></div>
            <div><dt>Next</dt><dd><a href="/accounts">Open accounts</a></dd></div>
          </dl>
        )}
      </LoadBoundary>
    </ChannelPanel>
  );
}

function RecipientPanel({ state }: { readonly state: LoadState<InternalRecipientAccountDto> }) {
  return (
    <ChannelPanel title="Recipient Lookup" eyebrow="internal only">
      <LoadBoundary state={state} idle="Look up a synthetic internal account before transfer submission.">
        {(recipient) => (
          <dl className="self-service-definition-list">
            <div><dt>Recipient</dt><dd>{recipient.recipientLabel}</dd></div>
            <div><dt>Account</dt><dd>{recipient.maskedAccountNo}</dd></div>
            <div><dt>Status</dt><dd>{recipient.status}</dd></div>
            <div><dt>Boundary</dt><dd>{recipient.internalOnly ? "INTERNAL_SYNTHETIC" : "BLOCKED"}</dd></div>
          </dl>
        )}
      </LoadBoundary>
    </ChannelPanel>
  );
}

function TransferResultPanel({ state }: { readonly state: LoadState<CustomerTransferResponse> }) {
  return (
    <ChannelPanel title="Transfer Result" eyebrow="posted · replay · held · failed">
      <LoadBoundary state={state} idle="Submit the transfer to view result state.">
        {(response) => (
          <div className="self-service-result-stack">
            <TransferStatusSummary value={response} />
            <a href={`/transfers/${encodeURIComponent(response.item.resultId ?? response.item.idempotencyKey)}`}>Open result page</a>
          </div>
        )}
      </LoadBoundary>
    </ChannelPanel>
  );
}

function TransferStatusSummary({ value }: { readonly value: CustomerTransferResponse | CustomerTransferStatusDto }) {
  const item = "item" in value ? value.item : value;
  const replayed = "replayed" in value ? value.replayed : false;
  const status = item.status;
  return (
    <dl className="self-service-definition-list">
      <div><dt>Status</dt><dd><StatusBadge status={replayed ? "REPLAYED" : status} /></dd></div>
      <div><dt>Result</dt><dd>{item.resultId ?? item.idempotencyKey ?? "pending"}</dd></div>
      <div><dt>Transaction</dt><dd>{item.transactionId ?? "not posted"}</dd></div>
      <div><dt>Case</dt><dd>{"caseId" in item ? item.caseId ?? "none" : item.caseId ?? "none"}</dd></div>
      <div><dt>Amount</dt><dd>{item.amountMinor == null ? "unknown" : formatMinor(item.amountMinor, item.currency ?? "KRW")}</dd></div>
      <div><dt>Message</dt><dd>{item.message ?? item.failureCode ?? "Balanced ledger state available when posted."}</dd></div>
    </dl>
  );
}

function StatusBadge({ status }: { readonly status: string }) {
  const critical = ["FAILED", "BLOCKED", "AUTHORIZATION_DENIED", "REQUEST_VALIDATION_FAILED"].includes(status);
  return <ChannelBadge tone={critical ? "critical" : "neutral"}>{status}</ChannelBadge>;
}

function LoadBoundary<T>({
  state,
  idle = "Ready.",
  children
}: {
  readonly state: LoadState<T>;
  readonly idle?: string;
  readonly children: (value: T) => React.ReactNode;
}) {
  if (state.status === "idle") {
    return <p className="self-service-muted">{idle}</p>;
  }
  if (state.status === "loading") {
    return <p className="self-service-muted">Loading</p>;
  }
  if (state.status === "failed") {
    return <StructuredErrorPanel error={state.error} />;
  }
  return <>{children(state.value)}</>;
}

function StructuredErrorPanel({ error }: { readonly error: UiError }) {
  return (
    <div className="self-service-error" role="alert">
      <strong>{error.code}</strong>
      <span>{error.message}</span>
      <small>{[error.domain, error.statusCode, error.route].filter(Boolean).join(" · ")}</small>
    </div>
  );
}

function useStoredSession(): readonly [StoredCustomerSession | null, (session: StoredCustomerSession | null) => void] {
  const [session, setSession] = useState<StoredCustomerSession | null>(null);
  useEffect(() => {
    setSession(readStoredSession());
  }, []);
  return [session, setSession] as const;
}

function readStoredSession(): StoredCustomerSession | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(sessionStorageKey);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as StoredCustomerSession;
  } catch {
    return null;
  }
}

function saveSessionFromAuth(response: CustomerAuthResponse) {
  const session: StoredCustomerSession = {
    customerId: response.session.customerId,
    username: response.customer.username,
    authorizationHeader: `${response.tokenType} ${response.bearerToken}`,
    expiresAt: response.expiresAt,
    sessionId: response.session.sessionId
  };
  window.localStorage.setItem(sessionStorageKey, JSON.stringify(session));
}

function clearStoredSession() {
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(sessionStorageKey);
  }
}

function authedClient(session: StoredCustomerSession) {
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: session.authorizationHeader
  });
}

function rememberTransferResult(response: CustomerTransferResponse) {
  if (typeof window === "undefined") {
    return;
  }
  const raw = window.localStorage.getItem(transferResultStorageKey);
  const existing = raw ? JSON.parse(raw) as Record<string, CustomerTransferResponse> : {};
  const keys = [response.item.resultId, response.item.idempotencyKey, response.item.transactionId].filter(Boolean) as string[];
  for (const key of keys) {
    existing[key] = response;
  }
  window.localStorage.setItem(transferResultStorageKey, JSON.stringify(existing));
}

function readRememberedTransferResult(resultId: string): CustomerTransferResponse | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(transferResultStorageKey);
  if (!raw) {
    return null;
  }
  try {
    const values = JSON.parse(raw) as Record<string, CustomerTransferResponse>;
    return values[resultId] ?? null;
  } catch {
    return null;
  }
}

function parseError(error: unknown): UiError {
  if (error instanceof BankingApiError) {
    try {
      const parsed = JSON.parse(error.body) as { readonly error?: Partial<UiError> };
      if (parsed.error?.code && parsed.error.message) {
        return {
          code: parsed.error.code,
          message: parsed.error.message,
          statusCode: parsed.error.statusCode,
          domain: parsed.error.domain,
          route: parsed.error.route
        };
      }
    } catch {
      return { code: "UNEXPECTED_FAILURE", message: error.body, statusCode: error.status };
    }
  }
  return { code: "UNEXPECTED_FAILURE", message: error instanceof Error ? error.message : "Unexpected failure" };
}

function demoFallbackError(): UiError {
  return {
    code: "DEMO_FALLBACK_API_NOT_CONFIGURED",
    message: "NEXT_PUBLIC_BANKING_API_BASE_URL is not configured for this browser route."
  };
}

function authRequiredError(): UiError {
  return {
    code: "CUSTOMER_SESSION_REQUIRED",
    message: "Sign up or log in to create a synthetic customer session."
  };
}

function nextIdempotencyKey(prefix: string): string {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${random}`.toUpperCase();
}

function formatMinor(amountMinor: number, currency: string): string {
  return `${amountMinor.toLocaleString("en-US")} ${currency}`;
}
