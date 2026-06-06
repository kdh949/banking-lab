import { CustomerWorkflowRoutePage } from "../../../components/workflow-routes";

type AccountDetailPageProps = {
  readonly params: Promise<{ readonly accountId: string }>;
};

export default async function AccountDetailPage({ params }: AccountDetailPageProps) {
  const { accountId } = await params;
  return CustomerWorkflowRoutePage({ routeKey: "accountDetail", routeParam: accountId });
}
