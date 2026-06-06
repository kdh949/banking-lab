import { CustomerAccountDetailView } from "../../../components/CustomerSelfService";

type AccountDetailPageProps = {
  readonly params: Promise<{ readonly accountId: string }>;
};

export default async function AccountDetailPage({ params }: AccountDetailPageProps) {
  const { accountId } = await params;
  return <CustomerAccountDetailView accountId={accountId} />;
}
