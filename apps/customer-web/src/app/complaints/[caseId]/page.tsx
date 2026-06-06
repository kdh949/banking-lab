import { CustomerWorkflowRoutePage } from "../../../components/workflow-routes";

type ComplaintDetailPageProps = {
  readonly params: Promise<{ readonly caseId: string }>;
};

export default async function ComplaintDetailPage({ params }: ComplaintDetailPageProps) {
  const { caseId } = await params;
  return CustomerWorkflowRoutePage({ routeKey: "complaintDetail", routeParam: caseId });
}
