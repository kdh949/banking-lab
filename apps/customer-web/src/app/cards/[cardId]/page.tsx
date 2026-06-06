import { CustomerWorkflowRoutePage } from "../../../components/workflow-routes";

type CardDetailPageProps = {
  readonly params: Promise<{ readonly cardId: string }>;
};

export default async function CardDetailPage({ params }: CardDetailPageProps) {
  const { cardId } = await params;
  return CustomerWorkflowRoutePage({ routeKey: "cardDetail", routeParam: cardId });
}
