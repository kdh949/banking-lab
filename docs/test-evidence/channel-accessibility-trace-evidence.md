# Channel Accessibility and Trace Evidence

Recorded: 2026-08-10 08:54 KST

## Scope and standard

This checkpoint covers the core held-transfer pages at customer `/transfers/new`, call-center `/workspace`, and the staff-terminal transaction-code shell. It is a focused WCAG 2.2 AA review, not a claim that every repository route or every assistive-technology combination conforms.

The review uses the W3C [WCAG 2.2 Recommendation](https://www.w3.org/TR/WCAG22/) and [Trace Context Recommendation](https://www.w3.org/TR/trace-context/) as primary references. The trace boundary accepts and emits the W3C `traceparent` shape; invalid or unsafe correlation input is replaced rather than logged or echoed.

## Automated browser evidence

The Playwright audit in `apps/customer-web/e2e/channel-accessibility.spec.ts` executed against both core pages and passed 2/2. It checks:

- exactly one `main` landmark and one level-one heading;
- a declared document language;
- unique IDs and valid `aria-labelledby`/`aria-describedby` references;
- accessible labels for visible form controls, links, and buttons;
- image alternatives when images exist;
- computed text contrast at 4.5:1, or 3:1 for large text;
- keyboard reachability and a visible focus outline of at least two CSS pixels.

The staff Playwright keyboard-only case passed without pointer input: `Tab` to transaction-code search, type `FDS201`, `Enter` to execute, `Tab` into the roving workspace tab list, arrow-key tab change, reverse-tab to search, invalid-code dialog, focused close button, and `Escape` close. The modal traps `Tab`/`Shift+Tab` and restores prior focus. This covers the core behavior for WCAG 2.1.1, 2.1.2, 2.4.3, 2.4.7, 2.4.11, and 4.1.2.

The call-center browser case passed the complete synthetic softphone sequence: `IDLE -> RINGING -> CONNECTED -> HOLD -> AFTER_CALL -> IDLE`.

## Manual runtime review

The two core pages were separately inspected in headless Chromium at 1280 by 900 and at a 320 CSS-pixel viewport. The screenshots were visually reviewed during this checkpoint; temporary captures were not committed as durable release artifacts because checkpoint 10 owns the curated demo asset set.

Observed result:

- heading order is `h1` followed by peer `h2` panels with no skipped level;
- desktop reading order matches visual order;
- at 320 CSS pixels the sidebar is removed, panels become one column, all labels and controls remain visible, and both documents report `scrollWidth == clientWidth == 320`;
- customer transfer labels remain adjacent to their select/input/checkbox controls, and long synthetic idempotency content is clipped within its input rather than forcing document overflow;
- call-center reason, query, note, FDS handoff, and timeline panels remain in logical order;
- customer loaded results use polite status regions, errors use `role=alert`, and the call-center action message uses `aria-live=polite`;
- both pages loaded through `localhost` with zero console errors or warnings.

The review did not run a specific commercial screen reader or speech-recognition product. That remains a release-environment compatibility check if this synthetic workbench is promoted beyond portfolio/demo scope.

## Trace and sensitive-log evidence

The same-origin BFF now:

1. validates a supplied `x-request-id`, `traceparent`, and bounded printable `tracestate`;
2. generates a random request ID and W3C version-00 trace context when input is missing or invalid;
3. attaches the server-held bearer separately and never copies browser `Authorization` or cookies;
4. forwards only the channel capability path and safe correlation headers;
5. returns the safe request ID and trace context on success or dependency failure.

Spring installs a safe request ID before controller execution. Business-journey events and append-only audit payloads receive the same request ID and trace ID; access logs include method, route, status, safe correlation IDs, and `syntheticOnly=true` only. They do not log headers, query strings, request/response bodies, bearer values, note bodies, or raw PII.

PostgreSQL/Testcontainers evidence passed for the fixed trace `4bf92f3577b34da6a3ce929d0e0e4736`: the high-risk customer transfer remained `HELD`, its `TRANSFER_HELD` journey event and `COMMAND_REQUESTED` audit payload carried the same trace/request correlation, and no ledger posting was introduced. A request ID containing a bearer-like value was rejected; the access log contained a generated safe ID and did not contain the supplied secret marker.

## Commands and results

```text
npm run packages:typecheck                                      PASS
npm run next:customer-web:typecheck                             PASS
npm run next:call-center-console:typecheck                      PASS
npm run next:staff-terminal:typecheck                           PASS after one TypeScript inference correction
./gradlew :services:core-banking:compileKotlin
  :services:core-banking:compileIntegrationTestKotlin           PASS with JDK 21
./gradlew :services:core-banking:integrationTest
  --tests CustomerTransferApiParityIntegrationTest
  --tests ObservabilityActuatorIntegrationTest --rerun-tasks    PASS
node --test tests/channelBffSecurity.test.mjs
  tests/channelWorkbenchDocumentation.test.mjs                 PASS 6/6
playwright CP9 combined first run                               FAIL 2 audit assertions due to audit-helper name fallback
playwright channel-accessibility.spec.ts corrected rerun        PASS 2/2
playwright combined configured cases                            PASS 10, SKIP 3 live opt-ins, FAIL 2 audit-helper assertions
playwright corrected combined CP9 rerun                         PASS 12, SKIP 3 live opt-ins
localhost Chromium manual console/reflow inspection             PASS
three channel Next production builds                            PASS (26/12/11 routes)
```

The failed audit attempt is retained because it led to a correction in the test helper rather than a product exception or suppressed rule.
