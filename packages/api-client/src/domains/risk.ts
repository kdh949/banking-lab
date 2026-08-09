import { createBankingApiClient, type BankingApiClientOptions } from "../client";
import { selectClientMethods } from "./select-client-methods";

export type {
  AmlCaseDto,
  AmlClosureCommand,
  BankingApiClientOptions,
  FdsAssignCommand,
  FdsCaseDto,
  FdsDecisionCommand,
  FdsDecisionRequestResponse,
  ParameterChangeRequestResponse,
  ParameterListResponse
} from "../client";

const riskMethodNames = [
  "fdsCases",
  "assignFdsCase",
  "requestFdsRelease",
  "requestFdsBlock",
  "fdsParameters",
  "fdsParameterHistory",
  "requestFdsParameterChange",
  "fdsAnalyticsEvidence",
  "amlCases",
  "requestAmlClosure"
] as const;

export function createRiskApiClient(options: BankingApiClientOptions) {
  return selectClientMethods(createBankingApiClient(options), riskMethodNames);
}

export type RiskApiClient = ReturnType<typeof createRiskApiClient>;
