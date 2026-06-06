import { StaffWorkflowRoutePage } from "../../../components/workflow-routes";

type TransactionCodePageProps = {
  readonly params: Promise<{ readonly transactionCode: string }>;
};

export default async function TransactionCodePage({ params }: TransactionCodePageProps) {
  const { transactionCode } = await params;
  return StaffWorkflowRoutePage({ routeKey: "tx", routeParam: transactionCode });
}
