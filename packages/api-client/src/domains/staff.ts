import { createBankingApiClient, type BankingApiClientOptions } from "../client";
import { selectClientMethods } from "./select-client-methods";

export { BankingApiError } from "../client";
export type {
  BankingApiClientOptions,
  MaskedCustomerDto,
  OperationalRetryQueueItemDto,
  OperatorApproval,
  StaffAccountDto,
  StaffApprovalExecutionResponse,
  StaffCustomerDetailDto,
  StaffJourneyResponse,
  StaffTransactionDto,
  StaffWorkflowTimelineEntryDto
} from "../client";

const staffMethodNames = [
  "staffCustomerSearch",
  "staffCustomerDetail",
  "staffAccountSearch",
  "staffTransactionSearch",
  "staffOperationalRetryQueue",
  "staffWorkflowTimeline",
  "staffJourney",
  "staffApprovals",
  "staffApproval",
  "approveStaffApproval",
  "rejectStaffApproval",
  "requestAccountHold",
  "requestAccountHoldRelease",
  "requestTransferLimitChange",
  "requestCustomerKycReview",
  "requestFeeWaiver",
  "requestTransactionCorrection"
] as const;

export function createStaffApiClient(options: BankingApiClientOptions) {
  return selectClientMethods(createBankingApiClient(options), staffMethodNames);
}

export type StaffApiClient = ReturnType<typeof createStaffApiClient>;
