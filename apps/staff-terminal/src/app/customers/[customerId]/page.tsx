import { StaffWorkflowRoutePage } from "../../../components/workflow-routes";

type StaffCustomerPageProps = {
  readonly params: Promise<{ readonly customerId: string }>;
};

export default async function StaffCustomerPage({ params }: StaffCustomerPageProps) {
  const { customerId } = await params;
  return StaffWorkflowRoutePage({ routeKey: "customers", routeParam: customerId });
}
