import { StaffWorkflowRoutePage } from "../../../components/workflow-routes";

type StaffWorkflowPageProps = {
  readonly params: Promise<{ readonly businessReferenceId: string }>;
};

export default async function StaffWorkflowPage({ params }: StaffWorkflowPageProps) {
  const { businessReferenceId } = await params;
  return StaffWorkflowRoutePage({ routeKey: "workflows", routeParam: businessReferenceId });
}
