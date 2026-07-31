# Payment customer ownership boundary

Customer-facing payment and autopay requests are authorized from the validated bearer token, not from caller-controlled identity fields in the JSON body.

## Enforced rules

- `customerId` is replaced with the token `customerId` claim.
- `requestedBy` is replaced with the token subject.
- `requestedChannel` is fixed to `CUSTOMER_WEB` for customer commands.
- Debit-account ownership is checked against the Core Banking customer account API before a payment instruction or autopay agreement is created.
- Customer reads and customer commands return `404` when the payment instruction or autopay agreement is missing or belongs to another customer.
- Existing active autopay agreements created before this binding are paused by Flyway V005.
- Existing pending ledger-posting outbox events created before this binding are quarantined as dead-letter events by Flyway V005.

## Regression coverage

- Customer request binding is invoked explicitly by `PaymentController`; it does not rely on implicit `RequestBodyAdvice` argument replacement.
- MockMvc integration tests verify token-derived customer identity for payment and autopay creation and conceal cross-customer resources.
- The dependency lock pins patched Next.js, PostCSS, and sharp releases, and the repository test suite verifies those resolved versions.

The legacy request fields remain in the public request schema temporarily for client compatibility, but the server treats them as untrusted and overwrites them. A later contract cleanup can remove those fields after clients migrate.

All accounts, customers, tokens, billers, and payment flows remain synthetic-only.
