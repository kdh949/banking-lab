import { CustomerWorkflowRoutePage } from "../../../components/workflow-routes";

type TransferResultPageProps = {
  readonly params: Promise<{ readonly resultId: string }>;
};

export default async function TransferResultPage({ params }: TransferResultPageProps) {
  const { resultId } = await params;
  return CustomerWorkflowRoutePage({ routeKey: "transferResult", routeParam: resultId });
}
