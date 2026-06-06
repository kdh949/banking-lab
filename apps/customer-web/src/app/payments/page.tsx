import { CustomerWorkflowRoutePage } from "../../components/workflow-routes";

export default async function PaymentsPage() {
  return CustomerWorkflowRoutePage({ routeKey: "payments" });
}
