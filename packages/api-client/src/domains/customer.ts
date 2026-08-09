import { createBankingApiClient, type BankingApiClientOptions } from "../client";
import { selectClientMethods } from "./select-client-methods";

export type {
  BalanceCertificateDto,
  BankingApiClientOptions,
  Customer360Dto,
  CustomerAccountDetailDto,
  CustomerAccountListItemDto,
  CustomerAuthResponse,
  CustomerComplaintConfirmResponse,
  CustomerComplaintEntryResponse,
  CustomerJourneyDto,
  CustomerProfileDto,
  CustomerSelfServiceAccountOpeningRequestDto,
  CustomerStatementArtifactDto,
  CustomerStatementDto,
  CustomerTransactionDto,
  CustomerTransferResponse,
  CustomerTransferStatusDto,
  InternalRecipientAccountDto,
  TransactionConfirmationDto
} from "../client";
export { BankingApiError } from "../client";

const customerMethodNames = [
  "signupCustomer",
  "loginCustomer",
  "customerProfile",
  "requestCustomerAccountOpening",
  "customerAccountOpeningRequests",
  "customer360",
  "customerAccounts",
  "customerAccountDetail",
  "internalRecipientLookup",
  "requestCustomerTransfer",
  "customerTransactions",
  "customerStatement",
  "customerConsolidatedStatement",
  "customerAccountStatement",
  "customerStatementArtifacts",
  "transactionConfirmation",
  "balanceCertificate",
  "customerAccessHistory",
  "customerTransfers",
  "customerJourney",
  "requestCustomerComplaint",
  "customerComplaints",
  "confirmCustomerComplaint",
  "submitCustomerComplaintMaterial",
  "reopenCustomerComplaint",
  "complaintTypeGuide"
] as const;

export function createCustomerApiClient(options: BankingApiClientOptions) {
  return selectClientMethods(createBankingApiClient(options), customerMethodNames);
}

export type CustomerApiClient = ReturnType<typeof createCustomerApiClient>;
