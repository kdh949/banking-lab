import { StaffWorkflowRoutePage } from "../../../components/workflow-routes";

type StaffAccountPageProps = {
  readonly params: Promise<{ readonly accountId: string }>;
};

export default async function StaffAccountPage({ params }: StaffAccountPageProps) {
  const { accountId } = await params;
  return StaffWorkflowRoutePage({ routeKey: "accounts", routeParam: accountId });
}
