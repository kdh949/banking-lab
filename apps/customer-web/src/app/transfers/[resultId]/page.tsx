import { CustomerTransferResultView } from "../../../components/CustomerSelfService";

type TransferResultPageProps = {
  readonly params: Promise<{ readonly resultId: string }>;
};

export default async function TransferResultPage({ params }: TransferResultPageProps) {
  const { resultId } = await params;
  return <CustomerTransferResultView resultId={resultId} />;
}
