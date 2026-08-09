import { createBankingApiClient, type BankingApiClientOptions } from "../client";
import { selectClientMethods } from "./select-client-methods";

export type {
  BankingApiClientOptions,
  CallCenterAftercallTaskDto,
  CallCenterAftercallTaskCommand,
  CallCenterCustomerSearchResponse,
  CallCenterCustomerSummaryDto,
  CallCenterEscalationCommand,
  CallCenterEscalationDto,
  CallCenterInteractionDto,
  CallCenterInteractionListResponse,
  CallCenterNoteCommand,
  CallCenterNoteDto,
  CloseCallCenterInteractionCommand,
  StartCallCenterInteractionCommand
} from "../client";

const callCenterMethodNames = [
  "searchCallCenterCustomers",
  "startCallCenterInteraction",
  "callCenterInteraction",
  "addCallCenterNote",
  "createCallCenterAftercallTask",
  "escalateCallCenterInteraction",
  "closeCallCenterInteraction",
  "callCenterCustomerHistory"
] as const;

export function createCallCenterApiClient(options: BankingApiClientOptions) {
  return selectClientMethods(createBankingApiClient(options), callCenterMethodNames);
}

export type CallCenterApiClient = ReturnType<typeof createCallCenterApiClient>;
