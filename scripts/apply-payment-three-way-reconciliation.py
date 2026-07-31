import base64
import gzip
from pathlib import Path

payload = "".join(
    Path(f"scripts/.payment-reconciliation-part-{index:02d}").read_text().strip()
    for index in range(4)
)
source = gzip.decompress(base64.b64decode(payload)).decode("utf-8")
source = source.replace(
    "import org.springframework.context.annotation.Primary\n",
    "import org.springframework.context.annotation.Primary\nimport org.springframework.context.annotation.Import\n",
)
source = source.replace(
    "@AutoConfigureMockMvc\n@Testcontainers\nclass PaymentReconciliationIntegrationTest",
    "@AutoConfigureMockMvc\n@Import(PaymentReconciliationIntegrationTest.EvidenceConfig::class)\n@Testcontainers\nclass PaymentReconciliationIntegrationTest",
)
exec(compile(source, "apply-payment-reconciliation.py", "exec"))

# The reconciliation test intentionally disables the real Core Banking HTTP boundary
# and supplies a primary evidence client. PaymentOutboxDispatcherService still needs a
# CoreLedgerPostingClient bean for application-context construction, even though this
# test never dispatches a posting. Keep a fail-closed disabled implementation so the
# context can start without silently accepting a posting command.
posting_client_path = Path(
    "services/payment-service/src/main/kotlin/lab/banking/payment/core/HttpCoreLedgerPostingClient.kt"
)
posting_client_source = posting_client_path.read_text(encoding="utf-8")
if "class DisabledCoreLedgerPostingClient" not in posting_client_source:
    posting_client_path.write_text(
        posting_client_source.rstrip()
        + """


@Component
@ConditionalOnProperty(
    prefix = "banking-lab.payment-service.core-banking",
    name = ["http-enabled"],
    havingValue = "false"
)
class DisabledCoreLedgerPostingClient : CoreLedgerPostingClient {
    override fun postBillPayment(command: CoreLedgerPaymentPostingCommand): CoreLedgerPostingResult {
        throw IllegalStateException("core-banking HTTP ledger posting is disabled")
    }
}
""",
        encoding="utf-8",
    )
