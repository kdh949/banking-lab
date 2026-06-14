"use client";

import { useEffect, useState } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type CustomerAccountDetailDto,
  type CustomerAccountListItemDto,
  type Customer360Dto,
  type CustomerAuthResponse,
  type CustomerProfileDto,
  type CustomerSelfServiceAccountOpeningRequestDto,
  type CustomerStatementArtifactDto,
  type CustomerStatementDto,
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

export function CustomerProfileView() {
  const [session] = useStoredSession();
  const [state, setState] = useState<LoadState<CustomerProfileDto>>({ status: "idle" });

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
      .customerProfile()
      .then((profile) => setState({ status: "loaded", value: profile }))
      .catch((error: unknown) => setState({ status: "failed", error: parseError(error) }));
  }, [session]);

  return (
    <SelfServiceShell title="Customer Profile" status={session ? "Masked profile" : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      {session ? <SessionPanel session={session} /> : null}
      <ChannelPanel title="Profile" eyebrow="CWB-003">
        <LoadBoundary state={state}>
          {(profile) => (
            <div className="self-service-result-stack">
              <dl className="self-service-definition-list">
                <div><dt>Name</dt><dd>{profile.maskedCustomerName}</dd></div>
                <div><dt>Phone</dt><dd>{profile.maskedPhone ?? "masked"}</dd></div>
                <div><dt>KYC</dt><dd>{profile.kycStatus}</dd></div>
                <div><dt>Onboarding</dt><dd>{profile.onboardingStatus}</dd></div>
                <div><dt>Duplicate check</dt><dd>{profile.duplicateCheckStatus}</dd></div>
                <div><dt>Next</dt><dd>{profile.nextRequiredAction}</dd></div>
              </dl>
              <OnboardingChecksTable checks={profile.onboardingChecks} />
            </div>
          )}
        </LoadBoundary>
      </ChannelPanel>
    </SelfServiceShell>
  );
}

export function CustomerOnboardingView() {
  const [session] = useStoredSession();
  const [profileState, setProfileState] = useState<LoadState<CustomerProfileDto>>({ status: "idle" });
  const [requestsState, setRequestsState] = useState<LoadState<readonly CustomerSelfServiceAccountOpeningRequestDto[]>>({ status: "idle" });
  const [resultState, setResultState] = useState<LoadState<CustomerSelfServiceAccountOpeningRequestDto>>({ status: "idle" });
  const [idempotencyKey, setIdempotencyKey] = useState(() => nextIdempotencyKey("CWB-AOR"));
  const [productCode, setProductCode] = useState("SYNTHETIC_DEPOSIT");
  const [accountAlias, setAccountAlias] = useState("");
  const [currency, setCurrency] = useState("KRW");
  const [initialDeposit, setInitialDeposit] = useState("0");
  const [termsAccepted, setTermsAccepted] = useState(false);

  const loadRequests = () => {
    if (!session) {
      setRequestsState({ status: "failed", error: authRequiredError() });
      return;
    }
    setRequestsState({ status: "loading" });
    authedClient(session)
      .customerAccountOpeningRequests()
      .then((response) => setRequestsState({ status: "loaded", value: response.items }))
      .catch((error: unknown) => setRequestsState({ status: "failed", error: parseError(error) }));
  };

  useEffect(() => {
    if (!apiBaseUrl) {
      setProfileState({ status: "failed", error: demoFallbackError() });
      setRequestsState({ status: "failed", error: demoFallbackError() });
      return;
    }
    if (!session) {
      setProfileState({ status: "failed", error: authRequiredError() });
      setRequestsState({ status: "failed", error: authRequiredError() });
      return;
    }
    setProfileState({ status: "loading" });
    authedClient(session)
      .customerProfile()
      .then((profile) => setProfileState({ status: "loaded", value: profile }))
      .catch((error: unknown) => setProfileState({ status: "failed", error: parseError(error) }));
    loadRequests();
  }, [session]);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!session) {
      setResultState({ status: "failed", error: authRequiredError() });
      return;
    }
    setResultState({ status: "loading" });
    try {
      const response = await authedClient(session).requestCustomerAccountOpening({
        idempotencyKey,
        productCode,
        accountAlias: accountAlias || null,
        currency,
        syntheticInitialDepositAmountMinor: Number(initialDeposit || "0"),
        termsAccepted
      });
      setResultState({ status: "loaded", value: response.item });
      loadRequests();
    } catch (error: unknown) {
      setResultState({ status: "failed", error: parseError(error) });
    }
  };

  return (
    <SelfServiceShell title="Onboarding" status={session ? "Profile and account-opening intake" : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelPanel title="Onboarding Checks" eyebrow="CWB-003">
        <LoadBoundary state={profileState}>
          {(profile) => <OnboardingChecksTable checks={profile.onboardingChecks} />}
        </LoadBoundary>
      </ChannelPanel>
      <ChannelPanel title="Account Opening Request" eyebrow="CWB-004">
        <form className="self-service-form" onSubmit={submit}>
          <label>Product <input value={productCode} onChange={(event) => setProductCode(event.target.value)} required /></label>
          <label>Alias <input value={accountAlias} onChange={(event) => setAccountAlias(event.target.value)} /></label>
          <label>Currency <input value={currency} onChange={(event) => setCurrency(event.target.value.toUpperCase())} required /></label>
          <label>Initial deposit minor <input value={initialDeposit} onChange={(event) => setInitialDeposit(event.target.value)} inputMode="numeric" /></label>
          <label>Idempotency key <input value={idempotencyKey} onChange={(event) => setIdempotencyKey(event.target.value)} required /></label>
          <label className="self-service-checkbox">Terms accepted <input type="checkbox" checked={termsAccepted} onChange={(event) => setTermsAccepted(event.target.checked)} required /></label>
          <div className="self-service-actions">
            <button type="submit" disabled={resultState.status === "loading"}>{resultState.status === "loading" ? "Submitting" : "Submit request"}</button>
            <button type="button" onClick={() => setIdempotencyKey(nextIdempotencyKey("CWB-AOR"))}>New key</button>
          </div>
        </form>
      </ChannelPanel>
      <AccountOpeningResultPanel state={resultState} />
      <ChannelPanel title="Requests" eyebrow="status">
        <LoadBoundary state={requestsState}>
          {(items) => <AccountOpeningRequestsTable items={items} />}
        </LoadBoundary>
      </ChannelPanel>
    </SelfServiceShell>
  );
}

export function Customer360View() {
  const [session] = useStoredSession();
  const [state, setState] = useState<LoadState<Customer360Dto>>({ status: "idle" });

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
      .customer360()
      .then((value) => setState({ status: "loaded", value }))
      .catch((error: unknown) => setState({ status: "failed", error: parseError(error) }));
  }, [session]);

  return (
    <SelfServiceShell title="Customer 360" status={session ? "Canonical read model" : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelPanel title="360 Summary" eyebrow="CWB-104">
        <LoadBoundary state={state}>
          {(value) => (
            <div className="self-service-result-stack">
              <ChannelMetricGrid>
                <ChannelMetric label="Accounts" value={value.accountSummary.totalAccounts} detail={`${value.accountSummary.activeAccounts} active`} />
                <ChannelMetric label="Available" value={formatMinor(value.accountSummary.totalAvailableBalanceMinor, value.accountSummary.currency)} />
                <ChannelMetric label="Loans" value={value.loanSummary.totalCount} />
                <ChannelMetric label="Cards" value={value.cardSummary.totalCount} />
              </ChannelMetricGrid>
              <Customer360AccountsTable value={value} />
              <RecentLedgerActivityTable items={value.recentLedgerActivity} />
            </div>
          )}
        </LoadBoundary>
      </ChannelPanel>
    </SelfServiceShell>
  );
}

export function CustomerAccountStatementView({ accountId }: { readonly accountId: string }) {
  return <CustomerStatementView accountId={accountId} scope="ACCOUNT" />;
}

export function CustomerConsolidatedStatementsView() {
  return <CustomerStatementView scope="CONSOLIDATED" />;
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
              <div><dt>Statement</dt><dd><a href={`/accounts/${encodeURIComponent(detail.accountId)}/statement`}>Open</a></dd></div>
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

function CustomerStatementView({
  accountId,
  scope
}: {
  readonly accountId?: string;
  readonly scope: "ACCOUNT" | "CONSOLIDATED";
}) {
  const [session] = useStoredSession();
  const defaultRange = defaultStatementRange();
  const [from, setFrom] = useState(defaultRange.from);
  const [to, setTo] = useState(defaultRange.to);
  const [state, setState] = useState<LoadState<CustomerStatementDto>>({ status: "idle" });
  const [artifactState, setArtifactState] = useState<LoadState<readonly CustomerStatementArtifactDto[]>>({ status: "idle" });

  const loadArtifacts = () => {
    if (!apiBaseUrl || !session) {
      return;
    }
    setArtifactState({ status: "loading" });
    authedClient(session)
      .customerStatementArtifacts()
      .then((response) => setArtifactState({ status: "loaded", value: response.items }))
      .catch((error: unknown) => setArtifactState({ status: "failed", error: parseError(error) }));
  };

  useEffect(() => {
    if (!apiBaseUrl) {
      setState({ status: "failed", error: demoFallbackError() });
      setArtifactState({ status: "failed", error: demoFallbackError() });
      return;
    }
    if (!session) {
      setState({ status: "failed", error: authRequiredError() });
      setArtifactState({ status: "failed", error: authRequiredError() });
      return;
    }
    loadArtifacts();
  }, [session]);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!session) {
      setState({ status: "failed", error: authRequiredError() });
      return;
    }
    setState({ status: "loading" });
    try {
      const client = authedClient(session);
      const statement = scope === "ACCOUNT"
        ? await client.customerAccountStatement(accountId ?? "", from, to)
        : await client.customerConsolidatedStatement(from, to);
      setState({ status: "loaded", value: statement });
      loadArtifacts();
    } catch (error: unknown) {
      setState({ status: "failed", error: parseError(error) });
    }
  };

  return (
    <SelfServiceShell title={scope === "ACCOUNT" ? `Account Statement ${accountId}` : "Consolidated Statements"} status={session ? "Ledger read-only" : "Login required"}>
      <DemoFallbackBanner />
      <SelfServiceNav />
      <ChannelPanel title="Statement Query" eyebrow={scope === "ACCOUNT" ? "CWB-105" : "CWB-106"}>
        <form className="self-service-form self-service-form-compact" onSubmit={submit}>
          <label>From <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} required /></label>
          <label>To <input type="date" value={to} onChange={(event) => setTo(event.target.value)} required /></label>
          <div className="self-service-actions">
            <button type="submit" disabled={state.status === "loading"}>{state.status === "loading" ? "Loading" : "Load statement"}</button>
          </div>
        </form>
      </ChannelPanel>
      <ChannelPanel title="Statement" eyebrow="artifact">
        <LoadBoundary state={state} idle="Choose a date range to generate a deterministic statement artifact.">
          {(statement) => <StatementSummary statement={statement} />}
        </LoadBoundary>
      </ChannelPanel>
      <ChannelPanel title="Artifact History" eyebrow="CWB-107">
        <LoadBoundary state={artifactState} idle="Statement artifacts appear after a statement is generated.">
          {(items) => <StatementArtifactsTable items={items} />}
        </LoadBoundary>
      </ChannelPanel>
    </SelfServiceShell>
  );
}

function OnboardingChecksTable({ checks }: { readonly checks: readonly CustomerProfileDto["onboardingChecks"][number][] }) {
  return (
    <ChannelTable>
      <thead>
        <tr>
          <th>Check</th>
          <th>Status</th>
          <th>Risk</th>
          <th>Created</th>
        </tr>
      </thead>
      <tbody>
        {checks.map((check) => (
          <tr key={check.checkId}>
            <td>{check.checkType}</td>
            <td><StatusBadge status={check.status} /></td>
            <td>{check.riskLevel}</td>
            <td>{check.createdAt}</td>
          </tr>
        ))}
      </tbody>
    </ChannelTable>
  );
}

function AccountOpeningResultPanel({ state }: { readonly state: LoadState<CustomerSelfServiceAccountOpeningRequestDto> }) {
  return (
    <ChannelPanel title="Request Result" eyebrow="intake only">
      <LoadBoundary state={state} idle="Submit a request to create customer intake without account or ledger rows.">
        {(item) => (
          <dl className="self-service-definition-list">
            <div><dt>Request</dt><dd>{item.requestId}</dd></div>
            <div><dt>Status</dt><dd>{item.status}</dd></div>
            <div><dt>Product</dt><dd>{item.requestedProductCode}</dd></div>
            <div><dt>Generated account</dt><dd>{item.generatedMaskedAccountNo ?? "staff review required"}</dd></div>
          </dl>
        )}
      </LoadBoundary>
    </ChannelPanel>
  );
}

function AccountOpeningRequestsTable({ items }: { readonly items: readonly CustomerSelfServiceAccountOpeningRequestDto[] }) {
  return (
    <ChannelTable>
      <thead>
        <tr>
          <th>Request</th>
          <th>Status</th>
          <th>Product</th>
          <th>Currency</th>
          <th>Generated</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.requestId}>
            <td>{item.requestId}</td>
            <td><StatusBadge status={item.status} /></td>
            <td>{item.requestedProductCode}</td>
            <td>{item.requestedCurrency}</td>
            <td>{item.generatedMaskedAccountNo ?? "pending"}</td>
          </tr>
        ))}
      </tbody>
    </ChannelTable>
  );
}

function Customer360AccountsTable({ value }: { readonly value: Customer360Dto }) {
  return (
    <ChannelTable>
      <thead>
        <tr>
          <th>Account</th>
          <th>Status</th>
          <th>Available</th>
          <th>Statement</th>
        </tr>
      </thead>
      <tbody>
        {value.accounts.map((account) => (
          <tr key={account.accountId}>
            <td>{account.maskedAccountNo}</td>
            <td>{account.status}</td>
            <td>{formatMinor(account.availableBalanceMinor, account.currency)}</td>
            <td><a href={`/accounts/${encodeURIComponent(account.accountId)}/statement`}>Open</a></td>
          </tr>
        ))}
      </tbody>
    </ChannelTable>
  );
}

function RecentLedgerActivityTable({ items }: { readonly items: readonly Customer360Dto["recentLedgerActivity"][number][] }) {
  return (
    <ChannelTable>
      <thead>
        <tr>
          <th>Transaction</th>
          <th>Account</th>
          <th>Direction</th>
          <th>Amount</th>
          <th>Date</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={`${item.transactionId}-${item.accountId}-${item.direction}`}>
            <td>{item.transactionId}</td>
            <td>{item.maskedAccountNo ?? item.accountId}</td>
            <td>{item.direction}</td>
            <td>{formatMinor(item.amountMinor, item.currency)}</td>
            <td>{item.businessDate}</td>
          </tr>
        ))}
      </tbody>
    </ChannelTable>
  );
}

function StatementSummary({ statement }: { readonly statement: CustomerStatementDto }) {
  return (
    <div className="self-service-result-stack">
      <dl className="self-service-definition-list">
        <div><dt>Statement</dt><dd>{statement.statementId ?? "pending"}</dd></div>
        <div><dt>Scope</dt><dd>{statement.statementScope ?? "CONSOLIDATED"}</dd></div>
        <div><dt>Opening</dt><dd>{formatMinor(statement.openingBalanceMinor, statement.currency)}</dd></div>
        <div><dt>Closing</dt><dd>{formatMinor(statement.closingBalanceMinor, statement.currency)}</dd></div>
        <div><dt>Source hash</dt><dd>{statement.sourceLedgerHash ?? "not generated"}</dd></div>
        <div><dt>Payload hash</dt><dd>{statement.payloadHash ?? "not generated"}</dd></div>
      </dl>
      <ChannelTable>
        <thead>
          <tr>
            <th>Date</th>
            <th>Transaction</th>
            <th>Account</th>
            <th>Direction</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          {statement.lines.map((line) => (
            <tr key={`${line.transactionId}-${line.accountId}-${line.direction}-${line.amountMinor}`}>
              <td>{line.businessDate}</td>
              <td>{line.transactionType}</td>
              <td>{line.accountId}</td>
              <td>{line.direction}</td>
              <td>{formatMinor(line.amountMinor, line.currency)}</td>
            </tr>
          ))}
        </tbody>
      </ChannelTable>
    </div>
  );
}

function StatementArtifactsTable({ items }: { readonly items: readonly CustomerStatementArtifactDto[] }) {
  return (
    <ChannelTable>
      <thead>
        <tr>
          <th>Statement</th>
          <th>Scope</th>
          <th>Range</th>
          <th>Payload</th>
          <th>Viewed</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.statementId}>
            <td>{item.statementId}</td>
            <td>{item.statementScope}</td>
            <td>{item.from} to {item.to}</td>
            <td>{item.payloadHash.slice(0, 12)}</td>
            <td>{item.lastViewedAt}</td>
          </tr>
        ))}
      </tbody>
    </ChannelTable>
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
        <a href="/profile">Profile</a>
        <a href="/onboarding">Onboarding</a>
        <a href="/360">360</a>
        <a href="/accounts">Accounts</a>
        <a href="/statements">Statements</a>
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

function defaultStatementRange(): { readonly from: string; readonly to: string } {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - 30);
  return {
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10)
  };
}
