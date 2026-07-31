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
