FROM python:3.12-slim

WORKDIR /app
COPY analytics/aml-fds-python/pyproject.toml /app/pyproject.toml
COPY analytics/aml-fds-python/src /app/src

RUN pip install --no-cache-dir /app

CMD ["python", "-c", "from banking_lab_analytics import AmlFdsInput, score_transaction; print(score_transaction(AmlFdsInput(customer_id='SYN-CUS-001', amount_minor=0)))"]
