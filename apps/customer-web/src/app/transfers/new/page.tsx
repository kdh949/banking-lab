import { CustomerWorkflowRoutePage } from "../../../components/workflow-routes";

export default async function NewTransferPage() {
  return CustomerWorkflowRoutePage({ routeKey: "transferNew" });
}
