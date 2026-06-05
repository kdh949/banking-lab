"use client";

import { useEffect, useState } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type BalanceCertificateDto,
  type CardAuthorizationResponse,
  type CardCaptureResponse,
  type CardDto,
  type CardIssueResponse,
  type CustomerAccessHistoryDto,
  type CustomerAccountDetailDto,
  type CustomerComplaintConfirmResponse,
  type CustomerComplaintEntryResponse,
  type CustomerStatementDto,
  type CustomerTransactionDto,
  type CustomerTransferStatusDto,
  type CustomerTransferResponse,
  type LoanApplicationResponse,
  type LoanExecutionResponse,
  type LoanPaymentResponse,
  type LoanDto,
  type PaymentAutopayAgreementResponse,
  type PaymentInstructionResponse,
  type ThreeDsSimulationDto,
  type TransactionConfirmationDto
} from "@banking-lab/api-client";
import { createOidcAuthorizationUrl, createPkcePair, createSimulatorBearerToken } from "@banking-lab/auth-client";

type ApiState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly account: CustomerAccountDetailDto }
  | { readonly status: "failed"; readonly message: string };

type KeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly account: CustomerAccountDetailDto; readonly tokenType: string; readonly bearerToken: string }
  | { readonly status: "failed"; readonly message: string };

type KeycloakCommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "replayed";
      readonly idempotencyKey: string;
      readonly first: CustomerTransferResponse;
      readonly retry: CustomerTransferResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type TransferRetryState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "replayed";
      readonly idempotencyKey: string;
      readonly first: CustomerTransferResponse;
      readonly retry: CustomerTransferResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type TransferFailureState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "rejected"; readonly error: StructuredErrorSummary }
  | { readonly status: "failed"; readonly message: string };

type HistoryStatusState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "loaded";
      readonly idempotencyKey: string;
      readonly historyItem: CustomerTransactionDto;
      readonly heldTransfer: CustomerTransferStatusDto;
    }
  | { readonly status: "failed"; readonly message: string };

type StatementReadModelState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "loaded";
      readonly statement: CustomerStatementDto;
      readonly confirmation: TransactionConfirmationDto;
      readonly certificate: BalanceCertificateDto;
      readonly accessHistory: CustomerAccessHistoryDto;
    }
  | { readonly status: "failed"; readonly message: string };

type LoanDomainState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "loaded";
      readonly application: LoanApplicationResponse;
      readonly execution: LoanExecutionResponse;
      readonly loan: LoanDto;
      readonly repayment: LoanPaymentResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type CardDomainState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "loaded";
      readonly issue: CardIssueResponse;
      readonly threeDs: ThreeDsSimulationDto;
      readonly authorization: CardAuthorizationResponse;
      readonly capture: CardCaptureResponse;
      readonly lostCard: CardDto;
    }
  | { readonly status: "failed"; readonly message: string };

type PaymentDomainState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "loaded";
      readonly instruction: PaymentInstructionResponse;
      readonly replay: PaymentInstructionResponse;
      readonly read: PaymentInstructionResponse;
      readonly autopay: PaymentAutopayAgreementResponse;
      readonly paused: PaymentAutopayAgreementResponse;
      readonly resumed: PaymentAutopayAgreementResponse;
      readonly canceled: PaymentAutopayAgreementResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type HeldFailedStatusState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "loaded";
      readonly held: CustomerTransferResponse;
      readonly failed: CustomerTransferResponse;
      readonly heldStatus: CustomerTransferStatusDto;
      readonly failedStatus: CustomerTransferStatusDto;
    }
  | { readonly status: "failed"; readonly message: string };

type ComplaintEntryState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "loaded"; readonly complaint: CustomerComplaintEntryResponse }
  | { readonly status: "failed"; readonly message: string };

type ComplaintConfirmState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "loaded"; readonly confirmation: CustomerComplaintConfirmResponse }
  | { readonly status: "failed"; readonly message: string };

interface StructuredErrorSummary {
  readonly code: string;
  readonly domain: string;
  readonly statusCode: number;
  readonly route: string;
  readonly message: string;
}

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const paymentApiBaseUrl = process.env.NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL || apiBaseUrl;
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const oidcStateKey = "bankingLabCustomerOidcState";
const oidcVerifierKey = "bankingLabCustomerOidcVerifier";
const oidcRedirectKey = "bankingLabCustomerOidcRedirectUri";

export function ApiBackedCustomerPanel() {
  const [state, setState] = useState<ApiState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [keycloakLoginState, setKeycloakLoginState] = useState<KeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakCommandState, setKeycloakCommandState] = useState<KeycloakCommandState>({ status: "idle" });
  const [keycloakTransferFailureState, setKeycloakTransferFailureState] = useState<TransferFailureState>({ status: "idle" });
  const [keycloakHistoryStatusState, setKeycloakHistoryStatusState] = useState<HistoryStatusState>({ status: "idle" });
  const [keycloakHeldFailedStatusState, setKeycloakHeldFailedStatusState] = useState<HeldFailedStatusState>({ status: "idle" });
  const [keycloakComplaintEntryState, setKeycloakComplaintEntryState] = useState<ComplaintEntryState>({ status: "idle" });
  const [keycloakComplaintConfirmState, setKeycloakComplaintConfirmState] = useState<ComplaintConfirmState>({ status: "idle" });
  const [transferRetryState, setTransferRetryState] = useState<TransferRetryState>({ status: "idle" });
  const [transferFailureState, setTransferFailureState] = useState<TransferFailureState>({ status: "idle" });
  const [historyStatusState, setHistoryStatusState] = useState<HistoryStatusState>({ status: "idle" });
  const [statementReadModelState, setStatementReadModelState] = useState<StatementReadModelState>({ status: "idle" });
  const [loanDomainState, setLoanDomainState] = useState<LoanDomainState>({ status: "idle" });
  const [cardDomainState, setCardDomainState] = useState<CardDomainState>({ status: "idle" });
  const [paymentDomainState, setPaymentDomainState] = useState<PaymentDomainState>({ status: "idle" });
  const [heldFailedStatusState, setHeldFailedStatusState] = useState<HeldFailedStatusState>({ status: "idle" });
  const [complaintEntryState, setComplaintEntryState] = useState<ComplaintEntryState>({ status: "idle" });
  const [complaintConfirmState, setComplaintConfirmState] = useState<ComplaintConfirmState>({ status: "idle" });

  useEffect(() => {
    if (!apiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: apiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "customer01",
        roles: ["CUSTOMER"],
        customerId: "SYN-CUS-001"
      })
    });

    client
      .customerAccountDetail("ACC-SYN-001-001", "SYN-CUS-001")
      .then((account) => {
        if (!cancelled) {
          setState({ status: "loaded", account });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState({ status: "failed", message: error instanceof Error ? error.message : "Unknown API failure" });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!apiBaseUrl || !keycloakBaseUrl) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const returnedState = params.get("state");
    if (!code || !returnedState) {
      return;
    }
    const expectedState = window.sessionStorage.getItem(oidcStateKey);
    const codeVerifier = window.sessionStorage.getItem(oidcVerifierKey);
    const redirectUri = window.sessionStorage.getItem(oidcRedirectKey) ?? `${window.location.origin}${window.location.pathname}`;
    if (!expectedState || !codeVerifier || returnedState !== expectedState) {
      setKeycloakLoginState({ status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    setKeycloakLoginState({ status: "exchanging" });
    fetch("/api/auth/keycloak-token", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        code,
        codeVerifier,
        redirectUri
      })
    })
      .then(async (response) => {
        const body = await response.json() as {
          accessToken?: string;
          tokenType?: string;
          error?: { message?: string };
        };
        if (!response.ok || !body.accessToken) {
          throw new Error(body.error?.message ?? "Keycloak token exchange failed");
        }
        const tokenType = body.tokenType ?? "Bearer";
        const client = createBankingApiClient({
          baseUrl: apiBaseUrl,
          bearerToken: `${tokenType} ${body.accessToken}`
        });
        const account = await client.customerAccountDetail("ACC-SYN-001-001", "SYN-CUS-001");
        if (!cancelled) {
          setKeycloakLoginState({ status: "loaded", account, tokenType, bearerToken: `${tokenType} ${body.accessToken}` });
          clearOidcSession();
          window.history.replaceState(null, "", window.location.pathname);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setKeycloakLoginState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak login failure" });
          clearOidcSession();
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const runKeycloakLoginSmoke = async () => {
    if (!apiBaseUrl || !keycloakBaseUrl || keycloakLoginState.status === "redirecting" || keycloakLoginState.status === "exchanging") {
      return;
    }
    setKeycloakLoginState({ status: "redirecting" });
    try {
      const redirectUri = `${window.location.origin}${window.location.pathname}`;
      const stateValue = globalThis.crypto.randomUUID();
      const pkce = await createPkcePair();
      window.sessionStorage.setItem(oidcStateKey, stateValue);
      window.sessionStorage.setItem(oidcVerifierKey, pkce.codeVerifier);
      window.sessionStorage.setItem(oidcRedirectKey, redirectUri);
      window.location.assign(
        createOidcAuthorizationUrl({
          issuerBaseUrl: keycloakBaseUrl,
          realm: "banking-lab",
          clientId: "customer-web",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      setKeycloakLoginState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak redirect failure" });
    }
  };

  const runKeycloakTransferRetrySmoke = async () => {
    if (!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakCommandState.status === "running") {
      return;
    }
    setKeycloakCommandState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakLoginState.bearerToken
      });
      const idempotencyKey = `CWB-OIDC-TRF-${Date.now()}`;
      const command = {
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-001-001",
        toAccountId: "ACC-SYN-002-001",
        amountMinor: 1234,
        idempotencyKey,
        requestedBy: "SYN-CUS-001",
        reason: "Browser Keycloak customer transfer retry smoke"
      };
      const first = await client.requestCustomerTransfer(command);
      const retry = await client.requestCustomerTransfer(command);
      if (!retry.replayed || first.item.transactionId !== retry.item.transactionId) {
        setKeycloakCommandState({ status: "failed", message: "Keycloak transfer retry did not replay the original transaction" });
        return;
      }
      setKeycloakCommandState({ status: "replayed", idempotencyKey, first, retry });
    } catch (error: unknown) {
      setKeycloakCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak transfer failure" });
    }
  };

  const runKeycloakTransferFailureSmoke = async () => {
    if (!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakTransferFailureState.status === "running") {
      return;
    }
    setKeycloakTransferFailureState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakLoginState.bearerToken
      });
      await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-CMD-TO",
        toAccountId: "ACC-SYN-CMD-FROM",
        amountMinor: 4_999_999,
        idempotencyKey: `CWB-OIDC-FAIL-${Date.now()}`,
        requestedBy: "SYN-CUS-001",
        reason: "Browser Keycloak customer transfer insufficient balance smoke"
      });
      setKeycloakTransferFailureState({ status: "failed", message: "Keycloak transfer failure smoke unexpectedly succeeded" });
    } catch (error: unknown) {
      const structuredError = parseStructuredError(error);
      if (structuredError.code !== "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE") {
        setKeycloakTransferFailureState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak transfer failure" });
        return;
      }
      setKeycloakTransferFailureState({ status: "rejected", error: structuredError });
    }
  };

  const runKeycloakHistoryStatusSmoke = async () => {
    if (!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakHistoryStatusState.status === "running") {
      return;
    }
    setKeycloakHistoryStatusState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakLoginState.bearerToken
      });
      const idempotencyKey = `CWB-OIDC-HIST-${Date.now()}`;
      const transfer = await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-001-001",
        toAccountId: "ACC-SYN-002-001",
        amountMinor: 2345,
        idempotencyKey,
        requestedBy: "SYN-CUS-001",
        reason: "Browser Keycloak customer transfer history smoke"
      });
      const history = await client.customerTransactions("SYN-CUS-001", "ACC-SYN-001-001");
      const historyItem = history.items.find((item) => item.transactionId === transfer.item.transactionId);
      const statuses = await client.customerTransfers("SYN-CUS-001");
      const heldTransfer = statuses.items.find((item) => item.caseId === "FDS-SYN-001" && item.transferStatus === "HELD");
      if (!historyItem || !heldTransfer) {
        setKeycloakHistoryStatusState({ status: "failed", message: "Keycloak customer history or held FDS status was not returned" });
        return;
      }
      setKeycloakHistoryStatusState({ status: "loaded", idempotencyKey, historyItem, heldTransfer });
    } catch (error: unknown) {
      setKeycloakHistoryStatusState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak history/status failure" });
    }
  };

  const runKeycloakHeldFailedStatusSmoke = async () => {
    if (!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakHeldFailedStatusState.status === "running") {
      return;
    }
    setKeycloakHeldFailedStatusState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakLoginState.bearerToken
      });
      const heldKey = `CWB-OIDC-HELD-${Date.now()}`;
      const failedKey = `CWB-OIDC-FAILED-${Date.now()}`;
      const held = await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-001-001",
        toAccountId: "ACC-SYN-002-001",
        amountMinor: 5_000_000,
        idempotencyKey: heldKey,
        requestedBy: "SYN-CUS-001",
        reason: "Browser Keycloak customer held transfer status smoke"
      });
      const failed = await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-001-001",
        toAccountId: "ACC-SYN-002-001",
        amountMinor: -1,
        idempotencyKey: failedKey,
        requestedBy: "SYN-CUS-001",
        reason: "Browser Keycloak customer failed transfer status smoke"
      });
      const statuses = await client.customerTransfers("SYN-CUS-001");
      const heldStatus = statuses.items.find((item) => item.idempotencyKey === heldKey && item.status === "HELD");
      const failedStatus = statuses.items.find((item) => item.idempotencyKey === failedKey && item.status === "FAILED");
      if (held.item.status !== "HELD" || failed.item.status !== "FAILED" || !heldStatus || !failedStatus) {
        setKeycloakHeldFailedStatusState({ status: "failed", message: "Keycloak held or failed transfer status was not returned" });
        return;
      }
      setKeycloakHeldFailedStatusState({ status: "loaded", held, failed, heldStatus, failedStatus });
    } catch (error: unknown) {
      setKeycloakHeldFailedStatusState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak held/failed status failure" });
    }
  };

  const runKeycloakComplaintEntrySmoke = async () => {
    if (!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakComplaintEntryState.status === "running") {
      return;
    }
    setKeycloakComplaintEntryState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakLoginState.bearerToken
      });
      const complaint = await client.requestCustomerComplaint({
        customerId: "SYN-CUS-001",
        category: "ACCOUNT_ACCESS",
        description: `Browser Keycloak customer complaint entry smoke ${Date.now()}`,
        reason: "Browser Keycloak customer complaint entry smoke"
      });
      if (!complaint.item.caseId.startsWith("CMP-") || complaint.item.status !== "RECEIVED") {
        setKeycloakComplaintEntryState({ status: "failed", message: "Keycloak complaint entry did not create a received case" });
        return;
      }
      setKeycloakComplaintEntryState({ status: "loaded", complaint });
    } catch (error: unknown) {
      setKeycloakComplaintEntryState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak complaint entry failure" });
    }
  };

  const runKeycloakComplaintConfirmSmoke = async () => {
    if (!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakComplaintConfirmState.status === "running") {
      return;
    }
    setKeycloakComplaintConfirmState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakLoginState.bearerToken
      });
      const confirmation = await client.confirmCustomerComplaint("CMP-SYN-CONFIRM-001", {
        customerId: "SYN-CUS-001",
        note: "Browser Keycloak customer accepted answer",
        reason: "Browser Keycloak customer complaint confirmation smoke"
      });
      const hasClosedTimeline = confirmation.item.timeline?.some((entry) => entry.type === "CLOSED") ?? false;
      if (confirmation.item.status !== "CLOSED" || !confirmation.item.customerConfirmedAt || !hasClosedTimeline) {
        setKeycloakComplaintConfirmState({ status: "failed", message: "Keycloak complaint confirmation did not close the answered case" });
        return;
      }
      setKeycloakComplaintConfirmState({ status: "loaded", confirmation });
    } catch (error: unknown) {
      setKeycloakComplaintConfirmState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak complaint confirmation failure" });
    }
  };

  const runTransferRetrySmoke = async () => {
    if (!apiBaseUrl || transferRetryState.status === "running") {
      return;
    }
    setTransferRetryState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer02",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-002"
        })
      });
      const idempotencyKey = `CWB-TRF-RETRY-${Date.now()}`;
      const command = {
        customerId: "SYN-CUS-002",
        fromAccountId: "ACC-SYN-CMD-FROM",
        toAccountId: "ACC-SYN-CMD-TO",
        amountMinor: 1000,
        idempotencyKey,
        requestedBy: "SYN-CUS-002",
        reason: "Browser customer transfer retry smoke"
      };
      const first = await client.requestCustomerTransfer(command);
      const retry = await client.requestCustomerTransfer(command);
      if (!retry.replayed || first.item.transactionId !== retry.item.transactionId) {
        setTransferRetryState({ status: "failed", message: "idempotent retry did not replay the original transaction" });
        return;
      }
      setTransferRetryState({ status: "replayed", idempotencyKey, first, retry });
    } catch (error: unknown) {
      setTransferRetryState({ status: "failed", message: error instanceof Error ? error.message : "Unknown transfer retry failure" });
    }
  };

  const runTransferFailureSmoke = async () => {
    if (!apiBaseUrl || transferFailureState.status === "running") {
      return;
    }
    setTransferFailureState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-CMD-TO",
        toAccountId: "ACC-SYN-CMD-FROM",
        amountMinor: 4_999_999,
        idempotencyKey: `CWB-TRF-FAIL-${Date.now()}`,
        requestedBy: "SYN-CUS-001",
        reason: "Browser customer transfer insufficient balance smoke"
      });
      setTransferFailureState({ status: "failed", message: "transfer failure smoke unexpectedly succeeded" });
    } catch (error: unknown) {
      const structuredError = parseStructuredError(error);
      if (structuredError.code !== "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE") {
        setTransferFailureState({ status: "failed", message: error instanceof Error ? error.message : "Unknown transfer failure" });
        return;
      }
      setTransferFailureState({ status: "rejected", error: structuredError });
    }
  };

  const runHistoryStatusSmoke = async () => {
    if (!apiBaseUrl || historyStatusState.status === "running") {
      return;
    }
    setHistoryStatusState({ status: "running" });
    try {
      const customer02Client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer02",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-002"
        })
      });
      const idempotencyKey = `CWB-HIST-${Date.now()}`;
      const transfer = await customer02Client.requestCustomerTransfer({
        customerId: "SYN-CUS-002",
        fromAccountId: "ACC-SYN-CMD-FROM",
        toAccountId: "ACC-SYN-CMD-TO",
        amountMinor: 2000,
        idempotencyKey,
        requestedBy: "SYN-CUS-002",
        reason: "Browser customer transfer history smoke"
      });
      const history = await customer02Client.customerTransactions("SYN-CUS-002", "ACC-SYN-CMD-FROM");
      const historyItem = history.items.find((item) => item.transactionId === transfer.item.transactionId);

      const customer01Client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const statuses = await customer01Client.customerTransfers("SYN-CUS-001");
      const heldTransfer = statuses.items.find((item) => item.caseId === "FDS-SYN-001" && item.transferStatus === "HELD");
      if (!historyItem || !heldTransfer) {
        setHistoryStatusState({ status: "failed", message: "customer history or held FDS status was not returned" });
        return;
      }
      setHistoryStatusState({ status: "loaded", idempotencyKey, historyItem, heldTransfer });
    } catch (error: unknown) {
      setHistoryStatusState({ status: "failed", message: error instanceof Error ? error.message : "Unknown history/status failure" });
    }
  };

  const runStatementReadModelSmoke = async () => {
    if (!apiBaseUrl || statementReadModelState.status === "running") {
      return;
    }
    setStatementReadModelState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const businessDate = "2026-02-06";
      const transfer = await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-001-001",
        toAccountId: "ACC-SYN-002-001",
        amountMinor: 777,
        idempotencyKey: `CWB-STMT-${Date.now()}`,
        requestedBy: "SYN-CUS-001",
        businessDate,
        reason: "Browser customer statement read-model smoke"
      });
      const transactionId = transfer.item.transactionId;
      if (!transactionId) {
        setStatementReadModelState({ status: "failed", message: "statement smoke transfer did not post a transaction" });
        return;
      }
      const statement = await client.customerStatement("SYN-CUS-001", businessDate, businessDate);
      const confirmation = await client.transactionConfirmation(transactionId);
      const certificate = await client.balanceCertificate("ACC-SYN-001-001", businessDate);
      const accessHistory = await client.customerAccessHistory("SYN-CUS-001");
      if (!confirmation.balanced || statement.lineCount === 0 || !accessHistory.items.some((item) => item.eventType === "STATEMENT_VIEW")) {
        setStatementReadModelState({ status: "failed", message: "statement read-model outputs were incomplete" });
        return;
      }
      setStatementReadModelState({ status: "loaded", statement, confirmation, certificate, accessHistory });
    } catch (error: unknown) {
      setStatementReadModelState({ status: "failed", message: error instanceof Error ? error.message : "Unknown statement read-model failure" });
    }
  };

  const runLoanDomainSmoke = async () => {
    if (!apiBaseUrl || loanDomainState.status === "running") {
      return;
    }
    setLoanDomainState({ status: "running" });
    try {
      const customerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const staffClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "manager01",
          roles: ["BRANCH_MANAGER"]
        })
      });
      const idempotencyKey = `CWB-LOAN-${Date.now()}`;
      const application = await customerClient.requestLoanApplication({
        customerId: "SYN-CUS-001",
        depositAccountId: "ACC-SYN-001-001",
        productId: "LOAN-PROD-SYN-PERSONAL-001",
        requestedAmountMinor: 120_000,
        requestedTermMonths: 12,
        syntheticMonthlyIncomeMinor: 3_000_000,
        syntheticMonthlyDebtMinor: 200_000,
        syntheticCreditGrade: "A",
        syntheticRiskGrade: "LOW",
        requestedBy: "customer01",
        requestedByRole: "CUSTOMER",
        reason: "Browser synthetic loan domain smoke",
        idempotencyKey
      });
      const approvalId = application.approval?.approvalId;
      if (!approvalId) {
        setLoanDomainState({ status: "failed", message: "loan application did not create an approval" });
        return;
      }
      const approvalExecution = await staffClient.approveStaffApproval(approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "LON-102"
      });
      const execution = approvalExecution.loanExecution;
      if (!execution) {
        setLoanDomainState({ status: "failed", message: "loan approval did not execute disbursement" });
        return;
      }
      const loan = await customerClient.loanDetail(execution.loan.loanId);
      const firstDue = loan.schedule.find((item) => item.status === "PENDING");
      const repayment = await customerClient.repayLoan(loan.loanId, {
        idempotencyKey: `${idempotencyKey}-REPAY`,
        principalMinor: firstDue?.principalMinor,
        interestMinor: firstDue?.interestMinor,
        requestedBy: "customer01",
        requestedChannel: "CUSTOMER_WEB",
        reason: "Browser synthetic loan repayment smoke"
      });
      if (repayment.ledgerTransaction.value.transactionType !== "LOAN_REPAYMENT") {
        setLoanDomainState({ status: "failed", message: "loan repayment did not post through the ledger" });
        return;
      }
      setLoanDomainState({ status: "loaded", application, execution, loan, repayment });
    } catch (error: unknown) {
      setLoanDomainState({ status: "failed", message: error instanceof Error ? error.message : "Unknown loan domain failure" });
    }
  };

  const runCardDomainSmoke = async () => {
    if (!apiBaseUrl || cardDomainState.status === "running") {
      return;
    }
    setCardDomainState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const runId = Date.now();
      const issue = await client.issueCard({
        customerId: "SYN-CUS-001",
        accountId: "ACC-SYN-001-001",
        panToken: `tok_pan_cwb_${runId}`,
        panLast4: "4242",
        dailyLimitMinor: 200_000,
        monthlyLimitMinor: 400_000,
        singleLimitMinor: 150_000,
        requestedBy: "customer01",
        requestedByRole: "CUSTOMER",
        reason: "Browser synthetic card issue smoke",
        idempotencyKey: `CWB-CARD-ISSUE-${runId}`
      });
      const threeDs = await client.simulateCardThreeDs({
        cardId: issue.item.cardId,
        amountMinor: 120_000,
        idempotencyKey: `CWB-CARD-3DS-${runId}`
      });
      const authorization = await client.authorizeCard({
        cardId: issue.item.cardId,
        amountMinor: 120_000,
        merchantName: "Synthetic Browser Merchant",
        threeDsAuthenticationId: threeDs.authenticationId,
        requestedBy: "customer01",
        requestedChannel: "CUSTOMER_WEB",
        reason: "Browser synthetic card authorization smoke",
        idempotencyKey: `CWB-CARD-AUTH-${runId}`
      });
      const capture = await client.captureCardAuthorization(authorization.item.authorizationId, {
        requestedBy: "customer01",
        requestedChannel: "CUSTOMER_WEB",
        reason: "Browser synthetic card capture smoke",
        idempotencyKey: `CWB-CARD-CAP-${runId}`
      });
      const lostCard = await client.reportCardLost(issue.item.cardId, {
        requestedBy: "customer01",
        requestedByRole: "CUSTOMER",
        reason: "Browser synthetic card loss smoke"
      });
      if (capture.ledgerTransaction.value.transactionType !== "CARD_CAPTURE" || lostCard.status !== "LOST") {
        setCardDomainState({ status: "failed", message: "card smoke did not complete capture/loss transitions" });
        return;
      }
      setCardDomainState({ status: "loaded", issue, threeDs, authorization, capture, lostCard });
    } catch (error: unknown) {
      setCardDomainState({ status: "failed", message: error instanceof Error ? error.message : "Unknown card domain failure" });
    }
  };

  const runPaymentDomainSmoke = async () => {
    if (!paymentApiBaseUrl || paymentDomainState.status === "running") {
      return;
    }
    setPaymentDomainState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: paymentApiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const runId = Date.now();
      const paymentCommand = {
        customerId: "SYN-CUS-001",
        debitAccountId: "ACC-SYN-001-001",
        billerId: "SYN-BILLER-UTIL-001",
        amountMinor: 5_000,
        currency: "KRW",
        idempotencyKey: `CWB-PAY-${runId}`,
        requestedBy: "customer01",
        requestedChannel: "CUSTOMER_WEB",
        reason: "Browser synthetic bill payment smoke"
      };
      const instruction = await client.createPaymentInstruction(paymentCommand);
      const replay = await client.createPaymentInstruction(paymentCommand);
      const read = await client.getPaymentInstruction(instruction.item.paymentInstructionId);
      if (
        !instruction.item.syntheticOnly ||
        !replay.replayed ||
        replay.item.paymentInstructionId !== instruction.item.paymentInstructionId ||
        read.item.lastOutboxEventId === null ||
        read.item.lastOutboxEventId === undefined
      ) {
        setPaymentDomainState({ status: "failed", message: "payment instruction did not persist idempotent synthetic outbox state" });
        return;
      }

      const autopay = await client.createAutopayAgreement({
        customerId: "SYN-CUS-001",
        debitAccountId: "ACC-SYN-001-001",
        billerId: "SYN-BILLER-UTIL-001",
        amountMinor: 7_000,
        currency: "KRW",
        frequency: "MONTHLY",
        nextRunOn: "2026-03-31",
        idempotencyKey: `CWB-APAY-${runId}`,
        requestedBy: "customer01",
        requestedChannel: "CUSTOMER_WEB",
        reason: "Browser synthetic autopay agreement smoke"
      });
      const paused = await client.pauseAutopayAgreement(autopay.item.autopayAgreementId, {
        idempotencyKey: `CWB-APAY-PAUSE-${runId}`,
        requestedBy: "customer01",
        reason: "Browser synthetic autopay pause smoke"
      });
      const resumed = await client.resumeAutopayAgreement(autopay.item.autopayAgreementId, {
        idempotencyKey: `CWB-APAY-RESUME-${runId}`,
        requestedBy: "customer01",
        reason: "Browser synthetic autopay resume smoke",
        nextRunOn: "2026-04-30"
      });
      const canceled = await client.cancelAutopayAgreement(autopay.item.autopayAgreementId, {
        idempotencyKey: `CWB-APAY-CANCEL-${runId}`,
        requestedBy: "customer01",
        reason: "Browser synthetic autopay cancel smoke"
      });
      if (
        !autopay.item.syntheticOnly ||
        autopay.item.status !== "ACTIVE" ||
        paused.item.status !== "PAUSED" ||
        resumed.item.status !== "ACTIVE" ||
        canceled.item.status !== "CANCELED"
      ) {
        setPaymentDomainState({ status: "failed", message: "autopay state transitions did not complete" });
        return;
      }
      setPaymentDomainState({ status: "loaded", instruction, replay, read, autopay, paused, resumed, canceled });
    } catch (error: unknown) {
      setPaymentDomainState({ status: "failed", message: error instanceof Error ? error.message : "Unknown payment domain failure" });
    }
  };

  const runHeldFailedStatusSmoke = async () => {
    if (!apiBaseUrl || heldFailedStatusState.status === "running") {
      return;
    }
    setHeldFailedStatusState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const heldKey = `CWB-HELD-${Date.now()}`;
      const failedKey = `CWB-FAILED-${Date.now()}`;
      const held = await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-001-001",
        toAccountId: "ACC-SYN-002-001",
        amountMinor: 5_000_000,
        idempotencyKey: heldKey,
        requestedBy: "SYN-CUS-001",
        reason: "Browser customer held transfer status smoke"
      });
      const failed = await client.requestCustomerTransfer({
        customerId: "SYN-CUS-001",
        fromAccountId: "ACC-SYN-001-001",
        toAccountId: "ACC-SYN-002-001",
        amountMinor: -1,
        idempotencyKey: failedKey,
        requestedBy: "SYN-CUS-001",
        reason: "Browser customer failed transfer status smoke"
      });
      const statuses = await client.customerTransfers("SYN-CUS-001");
      const heldStatus = statuses.items.find((item) => item.idempotencyKey === heldKey && item.status === "HELD");
      const failedStatus = statuses.items.find((item) => item.idempotencyKey === failedKey && item.status === "FAILED");
      if (held.item.status !== "HELD" || failed.item.status !== "FAILED" || !heldStatus || !failedStatus) {
        setHeldFailedStatusState({ status: "failed", message: "held or failed transfer status was not returned" });
        return;
      }
      setHeldFailedStatusState({ status: "loaded", held, failed, heldStatus, failedStatus });
    } catch (error: unknown) {
      setHeldFailedStatusState({ status: "failed", message: error instanceof Error ? error.message : "Unknown held/failed status failure" });
    }
  };

  const runComplaintEntrySmoke = async () => {
    if (!apiBaseUrl || complaintEntryState.status === "running") {
      return;
    }
    setComplaintEntryState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const complaint = await client.requestCustomerComplaint({
        customerId: "SYN-CUS-001",
        category: "ACCOUNT_ACCESS",
        description: `Browser customer complaint entry smoke ${Date.now()}`,
        reason: "Browser customer complaint entry smoke"
      });
      if (!complaint.item.caseId.startsWith("CMP-") || complaint.item.status !== "RECEIVED") {
        setComplaintEntryState({ status: "failed", message: "complaint entry did not create a received case" });
        return;
      }
      setComplaintEntryState({ status: "loaded", complaint });
    } catch (error: unknown) {
      setComplaintEntryState({ status: "failed", message: error instanceof Error ? error.message : "Unknown complaint entry failure" });
    }
  };

  const runComplaintConfirmSmoke = async () => {
    if (!apiBaseUrl || complaintConfirmState.status === "running") {
      return;
    }
    setComplaintConfirmState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const confirmation = await client.confirmCustomerComplaint("CMP-SYN-CONFIRM-001", {
        customerId: "SYN-CUS-001",
        note: "Browser customer accepted answer",
        reason: "Browser customer complaint confirmation smoke"
      });
      const hasClosedTimeline = confirmation.item.timeline?.some((entry) => entry.type === "CLOSED") ?? false;
      if (confirmation.item.status !== "CLOSED" || !confirmation.item.customerConfirmedAt || !hasClosedTimeline) {
        setComplaintConfirmState({ status: "failed", message: "complaint confirmation did not close the answered case" });
        return;
      }
      setComplaintConfirmState({ status: "loaded", confirmation });
    } catch (error: unknown) {
      setComplaintConfirmState({ status: "failed", message: error instanceof Error ? error.message : "Unknown complaint confirmation failure" });
    }
  };

  return (
    <section className="panel api-panel" aria-label="API-backed customer account detail">
      <h2>API-backed Account</h2>
      <dl data-testid="api-backed-customer-account">
        <div>
          <dt>Spring API</dt>
          <dd>{apiBaseUrl || "not configured"}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{statusLabel(state)}</dd>
        </div>
        {state.status === "loaded" ? (
          <>
            <div>
              <dt>Customer</dt>
              <dd>{state.account.customerId}</dd>
            </div>
            <div>
              <dt>Masked account</dt>
              <dd>{state.account.maskedAccountNo}</dd>
            </div>
            <div>
              <dt>Available</dt>
              <dd>
                {state.account.availableBalanceMinor} {state.account.currency}
              </dd>
            </div>
          </>
        ) : null}
        {state.status === "failed" ? (
          <div>
            <dt>Error</dt>
            <dd>{state.message}</dd>
          </div>
        ) : null}
      </dl>
      <div className="api-actions" data-testid="api-backed-customer-keycloak-login">
        <button
          type="button"
          onClick={runKeycloakLoginSmoke}
          disabled={!apiBaseUrl || !keycloakBaseUrl || keycloakLoginState.status === "redirecting" || keycloakLoginState.status === "exchanging"}
        >
          Sign in with Keycloak
        </button>
        <dl>
          <div>
            <dt>OIDC</dt>
            <dd>{keycloakLoginLabel(keycloakLoginState)}</dd>
          </div>
          {keycloakLoginState.status === "loaded" ? (
            <>
              <div>
                <dt>Token</dt>
                <dd>{keycloakLoginState.tokenType}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{keycloakLoginState.account.customerId}</dd>
              </div>
              <div>
                <dt>Masked account</dt>
                <dd>{keycloakLoginState.account.maskedAccountNo}</dd>
              </div>
              <div>
                <dt>Available</dt>
                <dd>
                  {keycloakLoginState.account.availableBalanceMinor} {keycloakLoginState.account.currency}
                </dd>
              </div>
            </>
          ) : null}
          {keycloakLoginState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakLoginState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakTransferRetrySmoke}
          disabled={!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakCommandState.status === "running"}
        >
          Run Keycloak transfer smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak command</dt>
            <dd>{keycloakCommandLabel(keycloakCommandState)}</dd>
          </div>
          {keycloakCommandState.status === "replayed" ? (
            <>
              <div>
                <dt>Idempotency</dt>
                <dd>{keycloakCommandState.idempotencyKey}</dd>
              </div>
              <div>
                <dt>First</dt>
                <dd>{keycloakCommandState.first.item.transactionId}</dd>
              </div>
              <div>
                <dt>Retry</dt>
                <dd>{keycloakCommandState.retry.item.transactionId}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{keycloakCommandState.retry.item.status}</dd>
              </div>
              <div>
                <dt>Replay</dt>
                <dd>{keycloakCommandState.retry.replayed ? "same transaction id" : "not replayed"}</dd>
              </div>
            </>
          ) : null}
          {keycloakCommandState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakTransferFailureSmoke}
          disabled={!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakTransferFailureState.status === "running"}
        >
          Run Keycloak transfer failure smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak failure</dt>
            <dd>{keycloakTransferFailureLabel(keycloakTransferFailureState)}</dd>
          </div>
          {keycloakTransferFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>Code</dt>
                <dd>{keycloakTransferFailureState.error.code}</dd>
              </div>
              <div>
                <dt>Domain</dt>
                <dd>{keycloakTransferFailureState.error.domain}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{keycloakTransferFailureState.error.statusCode}</dd>
              </div>
              <div>
                <dt>Route</dt>
                <dd>{keycloakTransferFailureState.error.route}</dd>
              </div>
            </>
          ) : null}
          {keycloakTransferFailureState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakTransferFailureState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakHistoryStatusSmoke}
          disabled={!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakHistoryStatusState.status === "running"}
        >
          Run Keycloak history status smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak history</dt>
            <dd>{keycloakHistoryStatusLabel(keycloakHistoryStatusState)}</dd>
          </div>
          {keycloakHistoryStatusState.status === "loaded" ? (
            <>
              <div>
                <dt>Idempotency</dt>
                <dd>{keycloakHistoryStatusState.idempotencyKey}</dd>
              </div>
              <div>
                <dt>Transaction</dt>
                <dd>{keycloakHistoryStatusState.historyItem.transactionId}</dd>
              </div>
              <div>
                <dt>Type</dt>
                <dd>{keycloakHistoryStatusState.historyItem.transactionType}</dd>
              </div>
              <div>
                <dt>Channel</dt>
                <dd>{keycloakHistoryStatusState.historyItem.requestedChannel}</dd>
              </div>
              <div>
                <dt>Direction</dt>
                <dd>{keycloakHistoryStatusState.historyItem.direction}</dd>
              </div>
              <div>
                <dt>FDS case</dt>
                <dd>{keycloakHistoryStatusState.heldTransfer.caseId}</dd>
              </div>
              <div>
                <dt>FDS status</dt>
                <dd>{keycloakHistoryStatusState.heldTransfer.transferStatus}</dd>
              </div>
              <div>
                <dt>FDS amount</dt>
                <dd>{keycloakHistoryStatusState.heldTransfer.amountMinor}</dd>
              </div>
            </>
          ) : null}
          {keycloakHistoryStatusState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakHistoryStatusState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakHeldFailedStatusSmoke}
          disabled={!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakHeldFailedStatusState.status === "running"}
        >
          Run Keycloak held failed status smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak status parity</dt>
            <dd>{keycloakHeldFailedStatusLabel(keycloakHeldFailedStatusState)}</dd>
          </div>
          {keycloakHeldFailedStatusState.status === "loaded" ? (
            <>
              <div>
                <dt>Held key</dt>
                <dd>{keycloakHeldFailedStatusState.held.item.idempotencyKey}</dd>
              </div>
              <div>
                <dt>Held result</dt>
                <dd>{keycloakHeldFailedStatusState.held.item.status}</dd>
              </div>
              <div>
                <dt>Held case</dt>
                <dd>{keycloakHeldFailedStatusState.held.item.caseId}</dd>
              </div>
              <div>
                <dt>Held list status</dt>
                <dd>{keycloakHeldFailedStatusState.heldStatus.status}</dd>
              </div>
              <div>
                <dt>Failed key</dt>
                <dd>{keycloakHeldFailedStatusState.failed.item.idempotencyKey}</dd>
              </div>
              <div>
                <dt>Failed result</dt>
                <dd>{keycloakHeldFailedStatusState.failed.item.status}</dd>
              </div>
              <div>
                <dt>Failure code</dt>
                <dd>{keycloakHeldFailedStatusState.failed.item.failureCode}</dd>
              </div>
              <div>
                <dt>Failed list status</dt>
                <dd>{keycloakHeldFailedStatusState.failedStatus.status}</dd>
              </div>
              <div>
                <dt>Failed list code</dt>
                <dd>{keycloakHeldFailedStatusState.failedStatus.failureCode}</dd>
              </div>
            </>
          ) : null}
          {keycloakHeldFailedStatusState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakHeldFailedStatusState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakComplaintEntrySmoke}
          disabled={!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakComplaintEntryState.status === "running"}
        >
          Run Keycloak complaint entry smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak complaint entry</dt>
            <dd>{keycloakComplaintEntryLabel(keycloakComplaintEntryState)}</dd>
          </div>
          {keycloakComplaintEntryState.status === "loaded" ? (
            <>
              <div>
                <dt>Case</dt>
                <dd>{keycloakComplaintEntryState.complaint.item.caseId}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{keycloakComplaintEntryState.complaint.item.customerId}</dd>
              </div>
              <div>
                <dt>Category</dt>
                <dd>{keycloakComplaintEntryState.complaint.item.category}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{keycloakComplaintEntryState.complaint.item.status}</dd>
              </div>
            </>
          ) : null}
          {keycloakComplaintEntryState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakComplaintEntryState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakComplaintConfirmSmoke}
          disabled={!apiBaseUrl || keycloakLoginState.status !== "loaded" || keycloakComplaintConfirmState.status === "running"}
        >
          Run Keycloak complaint confirm smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak confirmation</dt>
            <dd>{keycloakComplaintConfirmLabel(keycloakComplaintConfirmState)}</dd>
          </div>
          {keycloakComplaintConfirmState.status === "loaded" ? (
            <>
              <div>
                <dt>Case</dt>
                <dd>{keycloakComplaintConfirmState.confirmation.item.caseId}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{keycloakComplaintConfirmState.confirmation.item.customerId}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{keycloakComplaintConfirmState.confirmation.item.status}</dd>
              </div>
              <div>
                <dt>Confirmed</dt>
                <dd>{keycloakComplaintConfirmState.confirmation.item.customerConfirmedAt}</dd>
              </div>
              <div>
                <dt>Timeline</dt>
                <dd>{keycloakComplaintConfirmState.confirmation.item.timeline?.some((entry) => entry.type === "CLOSED") ? "CLOSED" : "missing"}</dd>
              </div>
            </>
          ) : null}
          {keycloakComplaintConfirmState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakComplaintConfirmState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-transfer-retry">
        <button type="button" onClick={runTransferRetrySmoke} disabled={!apiBaseUrl || transferRetryState.status === "running"}>
          Run transfer retry smoke
        </button>
        <dl>
          <div>
            <dt>Transfer</dt>
            <dd>{transferRetryLabel(transferRetryState)}</dd>
          </div>
          {transferRetryState.status === "replayed" ? (
            <>
              <div>
                <dt>Idempotency</dt>
                <dd>{transferRetryState.idempotencyKey}</dd>
              </div>
              <div>
                <dt>First</dt>
                <dd>{transferRetryState.first.item.transactionId}</dd>
              </div>
              <div>
                <dt>Retry</dt>
                <dd>{transferRetryState.retry.item.transactionId}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{transferRetryState.retry.item.status}</dd>
              </div>
              <div>
                <dt>Replay</dt>
                <dd>{transferRetryState.retry.replayed ? "same transaction id" : "not replayed"}</dd>
              </div>
            </>
          ) : null}
          {transferRetryState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{transferRetryState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-transfer-failure">
        <button type="button" onClick={runTransferFailureSmoke} disabled={!apiBaseUrl || transferFailureState.status === "running"}>
          Run transfer failure smoke
        </button>
        <dl>
          <div>
            <dt>Failure</dt>
            <dd>{transferFailureLabel(transferFailureState)}</dd>
          </div>
          {transferFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>Code</dt>
                <dd>{transferFailureState.error.code}</dd>
              </div>
              <div>
                <dt>Domain</dt>
                <dd>{transferFailureState.error.domain}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{transferFailureState.error.statusCode}</dd>
              </div>
              <div>
                <dt>Route</dt>
                <dd>{transferFailureState.error.route}</dd>
              </div>
            </>
          ) : null}
          {transferFailureState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{transferFailureState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-history-status">
        <button type="button" onClick={runHistoryStatusSmoke} disabled={!apiBaseUrl || historyStatusState.status === "running"}>
          Run history status smoke
        </button>
        <dl>
          <div>
            <dt>History</dt>
            <dd>{historyStatusLabel(historyStatusState)}</dd>
          </div>
          {historyStatusState.status === "loaded" ? (
            <>
              <div>
                <dt>Idempotency</dt>
                <dd>{historyStatusState.idempotencyKey}</dd>
              </div>
              <div>
                <dt>Transaction</dt>
                <dd>{historyStatusState.historyItem.transactionId}</dd>
              </div>
              <div>
                <dt>Type</dt>
                <dd>{historyStatusState.historyItem.transactionType}</dd>
              </div>
              <div>
                <dt>Channel</dt>
                <dd>{historyStatusState.historyItem.requestedChannel}</dd>
              </div>
              <div>
                <dt>Direction</dt>
                <dd>{historyStatusState.historyItem.direction}</dd>
              </div>
              <div>
                <dt>FDS case</dt>
                <dd>{historyStatusState.heldTransfer.caseId}</dd>
              </div>
              <div>
                <dt>FDS status</dt>
                <dd>{historyStatusState.heldTransfer.transferStatus}</dd>
              </div>
              <div>
                <dt>FDS amount</dt>
                <dd>{historyStatusState.heldTransfer.amountMinor}</dd>
              </div>
            </>
          ) : null}
          {historyStatusState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{historyStatusState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-statement-read-model">
        <button type="button" onClick={runStatementReadModelSmoke} disabled={!apiBaseUrl || statementReadModelState.status === "running"}>
          Run statement read model smoke
        </button>
        <dl>
          <div>
            <dt>Statement</dt>
            <dd>{statementReadModelLabel(statementReadModelState)}</dd>
          </div>
          {statementReadModelState.status === "loaded" ? (
            <>
              <div>
                <dt>Period</dt>
                <dd>{statementReadModelState.statement.from}</dd>
              </div>
              <div>
                <dt>Lines</dt>
                <dd>{statementReadModelState.statement.lineCount}</dd>
              </div>
              <div>
                <dt>Net</dt>
                <dd>{statementReadModelState.statement.netAmountMinor}</dd>
              </div>
              <div>
                <dt>Confirmation</dt>
                <dd>{statementReadModelState.confirmation.confirmationId}</dd>
              </div>
              <div>
                <dt>Balanced</dt>
                <dd>{statementReadModelState.confirmation.balanced ? "yes" : "no"}</dd>
              </div>
              <div>
                <dt>Certificate</dt>
                <dd>{statementReadModelState.certificate.certificateId}</dd>
              </div>
              <div>
                <dt>Access events</dt>
                <dd>{statementReadModelState.accessHistory.items.length}</dd>
              </div>
            </>
          ) : null}
          {statementReadModelState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{statementReadModelState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-loan-domain">
        <button type="button" onClick={runLoanDomainSmoke} disabled={!apiBaseUrl || loanDomainState.status === "running"}>
          Run loan domain smoke
        </button>
        <dl>
          <div>
            <dt>Loan</dt>
            <dd>{loanDomainLabel(loanDomainState)}</dd>
          </div>
          {loanDomainState.status === "loaded" ? (
            <>
              <div>
                <dt>Application</dt>
                <dd>{loanDomainState.application.item.applicationId}</dd>
              </div>
              <div>
                <dt>Approval</dt>
                <dd>{loanDomainState.application.approval?.approvalId}</dd>
              </div>
              <div>
                <dt>Loan ID</dt>
                <dd>{loanDomainState.execution.loan.loanId}</dd>
              </div>
              <div>
                <dt>Disbursement</dt>
                <dd>{loanDomainState.execution.ledgerTransaction.value.id}</dd>
              </div>
              <div>
                <dt>Schedule</dt>
                <dd>{loanDomainState.loan.schedule.length}</dd>
              </div>
              <div>
                <dt>Repayment</dt>
                <dd>{loanDomainState.repayment.ledgerTransaction.value.id}</dd>
              </div>
            </>
          ) : null}
          {loanDomainState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{loanDomainState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-card-domain">
        <button type="button" onClick={runCardDomainSmoke} disabled={!apiBaseUrl || cardDomainState.status === "running"}>
          Run card domain smoke
        </button>
        <dl>
          <div>
            <dt>Card</dt>
            <dd>{cardDomainLabel(cardDomainState)}</dd>
          </div>
          {cardDomainState.status === "loaded" ? (
            <>
              <div>
                <dt>Card ID</dt>
                <dd>{cardDomainState.issue.item.cardId}</dd>
              </div>
              <div>
                <dt>3DS</dt>
                <dd>{cardDomainState.threeDs.authenticationId}</dd>
              </div>
              <div>
                <dt>Authorization</dt>
                <dd>{cardDomainState.authorization.item.authorizationId}</dd>
              </div>
              <div>
                <dt>Capture</dt>
                <dd>{cardDomainState.capture.ledgerTransaction.value.id}</dd>
              </div>
              <div>
                <dt>Loss status</dt>
                <dd>{cardDomainState.lostCard.status}</dd>
              </div>
            </>
          ) : null}
          {cardDomainState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{cardDomainState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-payment-domain">
        <button type="button" onClick={runPaymentDomainSmoke} disabled={!paymentApiBaseUrl || paymentDomainState.status === "running"}>
          Run payment domain smoke
        </button>
        <dl>
          <div>
            <dt>Payment</dt>
            <dd>{paymentDomainLabel(paymentDomainState)}</dd>
          </div>
          {paymentDomainState.status === "loaded" ? (
            <>
              <div>
                <dt>Instruction</dt>
                <dd>{paymentDomainState.instruction.item.paymentInstructionId}</dd>
              </div>
              <div>
                <dt>Replay</dt>
                <dd>{paymentDomainState.replay.replayed ? "same instruction" : "not replayed"}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{paymentDomainState.read.item.status}</dd>
              </div>
              <div>
                <dt>Biller</dt>
                <dd>{paymentDomainState.read.item.billerName}</dd>
              </div>
              <div>
                <dt>Outbox</dt>
                <dd>{paymentDomainState.read.item.lastOutboxEventId}</dd>
              </div>
              <div>
                <dt>Autopay</dt>
                <dd>{paymentDomainState.autopay.item.autopayAgreementId}</dd>
              </div>
              <div>
                <dt>Autopay states</dt>
                <dd>
                  {paymentDomainState.paused.item.status} / {paymentDomainState.resumed.item.status} /{" "}
                  {paymentDomainState.canceled.item.status}
                </dd>
              </div>
              <div>
                <dt>Next run</dt>
                <dd>{paymentDomainState.resumed.item.nextRunOn}</dd>
              </div>
            </>
          ) : null}
          {paymentDomainState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{paymentDomainState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-held-failed-status">
        <button type="button" onClick={runHeldFailedStatusSmoke} disabled={!apiBaseUrl || heldFailedStatusState.status === "running"}>
          Run held failed status smoke
        </button>
        <dl>
          <div>
            <dt>Status parity</dt>
            <dd>{heldFailedStatusLabel(heldFailedStatusState)}</dd>
          </div>
          {heldFailedStatusState.status === "loaded" ? (
            <>
              <div>
                <dt>Held key</dt>
                <dd>{heldFailedStatusState.held.item.idempotencyKey}</dd>
              </div>
              <div>
                <dt>Held result</dt>
                <dd>{heldFailedStatusState.held.item.status}</dd>
              </div>
              <div>
                <dt>Held case</dt>
                <dd>{heldFailedStatusState.held.item.caseId}</dd>
              </div>
              <div>
                <dt>Held list status</dt>
                <dd>{heldFailedStatusState.heldStatus.status}</dd>
              </div>
              <div>
                <dt>Failed key</dt>
                <dd>{heldFailedStatusState.failed.item.idempotencyKey}</dd>
              </div>
              <div>
                <dt>Failed result</dt>
                <dd>{heldFailedStatusState.failed.item.status}</dd>
              </div>
              <div>
                <dt>Failure code</dt>
                <dd>{heldFailedStatusState.failed.item.failureCode}</dd>
              </div>
              <div>
                <dt>Failed list status</dt>
                <dd>{heldFailedStatusState.failedStatus.status}</dd>
              </div>
              <div>
                <dt>Failed list code</dt>
                <dd>{heldFailedStatusState.failedStatus.failureCode}</dd>
              </div>
            </>
          ) : null}
          {heldFailedStatusState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{heldFailedStatusState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-complaint-entry">
        <button type="button" onClick={runComplaintEntrySmoke} disabled={!apiBaseUrl || complaintEntryState.status === "running"}>
          Run complaint entry smoke
        </button>
        <dl>
          <div>
            <dt>Complaint</dt>
            <dd>{complaintEntryLabel(complaintEntryState)}</dd>
          </div>
          {complaintEntryState.status === "loaded" ? (
            <>
              <div>
                <dt>Case</dt>
                <dd>{complaintEntryState.complaint.item.caseId}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{complaintEntryState.complaint.item.customerId}</dd>
              </div>
              <div>
                <dt>Category</dt>
                <dd>{complaintEntryState.complaint.item.category}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{complaintEntryState.complaint.item.status}</dd>
              </div>
            </>
          ) : null}
          {complaintEntryState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{complaintEntryState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-customer-complaint-confirm">
        <button type="button" onClick={runComplaintConfirmSmoke} disabled={!apiBaseUrl || complaintConfirmState.status === "running"}>
          Run complaint confirm smoke
        </button>
        <dl>
          <div>
            <dt>Confirmation</dt>
            <dd>{complaintConfirmLabel(complaintConfirmState)}</dd>
          </div>
          {complaintConfirmState.status === "loaded" ? (
            <>
              <div>
                <dt>Case</dt>
                <dd>{complaintConfirmState.confirmation.item.caseId}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{complaintConfirmState.confirmation.item.customerId}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{complaintConfirmState.confirmation.item.status}</dd>
              </div>
              <div>
                <dt>Confirmed</dt>
                <dd>{complaintConfirmState.confirmation.item.customerConfirmedAt}</dd>
              </div>
              <div>
                <dt>Timeline</dt>
                <dd>{complaintConfirmState.confirmation.item.timeline?.some((entry) => entry.type === "CLOSED") ? "CLOSED" : "missing"}</dd>
              </div>
            </>
          ) : null}
          {complaintConfirmState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{complaintConfirmState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
    </section>
  );
}

function keycloakLoginLabel(state: KeycloakLoginState): string {
  if (state.status === "offline") {
    return "not configured";
  }
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "redirecting") {
    return "redirecting";
  }
  if (state.status === "exchanging") {
    return "exchanging token";
  }
  if (state.status === "loaded") {
    return "Keycloak account loaded";
  }
  return "failed";
}

function keycloakCommandLabel(state: KeycloakCommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "replayed") {
    return "Keycloak transfer replayed";
  }
  return "failed";
}

function keycloakTransferFailureLabel(state: TransferFailureState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "rejected") {
    return "Keycloak transfer rejected";
  }
  return "failed";
}

function keycloakHistoryStatusLabel(state: HistoryStatusState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "Keycloak history and held status loaded";
  }
  return "failed";
}

function keycloakHeldFailedStatusLabel(state: HeldFailedStatusState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "Keycloak held and failed statuses loaded";
  }
  return "failed";
}

function keycloakComplaintEntryLabel(state: ComplaintEntryState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "Keycloak complaint received";
  }
  return "failed";
}

function keycloakComplaintConfirmLabel(state: ComplaintConfirmState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "Keycloak complaint closed";
  }
  return "failed";
}

function statusLabel(state: ApiState): string {
  if (state.status === "offline") {
    return "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading";
  }
  if (state.status === "loaded") {
    return "masked account loaded";
  }
  return "failed";
}

function transferRetryLabel(state: TransferRetryState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "replayed") {
    return "transfer retry replayed";
  }
  return "failed";
}

function transferFailureLabel(state: TransferFailureState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "rejected") {
    return "transfer rejected";
  }
  return "failed";
}

function historyStatusLabel(state: HistoryStatusState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "history and held status loaded";
  }
  return "failed";
}

function statementReadModelLabel(state: StatementReadModelState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "statement artifacts loaded";
  }
  return "failed";
}

function loanDomainLabel(state: LoanDomainState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "loan posted";
  }
  return "failed";
}

function cardDomainLabel(state: CardDomainState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "card captured and lost";
  }
  return "failed";
}

function paymentDomainLabel(state: PaymentDomainState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "payment and autopay recorded";
  }
  return "failed";
}

function heldFailedStatusLabel(state: HeldFailedStatusState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "held and failed statuses loaded";
  }
  return "failed";
}

function complaintEntryLabel(state: ComplaintEntryState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "complaint received";
  }
  return "failed";
}

function complaintConfirmLabel(state: ComplaintConfirmState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "complaint closed";
  }
  return "failed";
}

function parseStructuredError(error: unknown): StructuredErrorSummary {
  if (error instanceof BankingApiError) {
    try {
      const parsed = JSON.parse(error.body) as {
        error?: {
          code?: string;
          domain?: string;
          statusCode?: number;
          route?: string;
          message?: string;
        };
      };
      return {
        code: parsed.error?.code ?? error.name,
        domain: parsed.error?.domain ?? "unknown",
        statusCode: parsed.error?.statusCode ?? error.status,
        route: parsed.error?.route ?? "unknown",
        message: parsed.error?.message ?? error.message
      };
    } catch {
      return {
        code: error.name,
        domain: "unknown",
        statusCode: error.status,
        route: "unknown",
        message: error.message
      };
    }
  }
  return {
    code: error instanceof Error ? error.name : "unknown_error",
    domain: "unknown",
    statusCode: 0,
    route: "unknown",
    message: error instanceof Error ? error.message : "Unknown transfer failure"
  };
}

function clearOidcSession() {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
}
