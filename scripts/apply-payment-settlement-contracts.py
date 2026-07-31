from pathlib import Path

openapi_path = Path("contracts/openapi/payment-service.yaml")
paths_fixture = Path("scripts/fixtures/payment-settlement-openapi-paths.yml")
schemas_fixture = Path("scripts/fixtures/payment-settlement-openapi-schemas.yml")

source = openapi_path.read_text()
paths_block = paths_fixture.read_text().rstrip() + "\n"
schemas_block = schemas_fixture.read_text().rstrip() + "\n"

if "/api/payments/settlement/imports:" not in source:
    marker = "\ncomponents:\n"
    if marker not in source:
        raise SystemExit("OpenAPI components marker was not found")
    source = source.replace(marker, "\n" + paths_block + marker, 1)

if "    ImportExternalSettlementCsvRequest:" not in source:
    marker = "  schemas:\n"
    if marker not in source:
        raise SystemExit("OpenAPI schemas marker was not found")
    source = source.replace(marker, marker + schemas_block, 1)

openapi_path.write_text(source)
