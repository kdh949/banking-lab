import { CustomerAccountStatementView } from "../../../../components/CustomerSelfService";

type AccountStatementPageProps = {
  readonly params: Promise<{ readonly accountId: string }>;
};

export default async function AccountStatementPage({ params }: AccountStatementPageProps) {
  const { accountId } = await params;
  return <CustomerAccountStatementView accountId={accountId} />;
}
