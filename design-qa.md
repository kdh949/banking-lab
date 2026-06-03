# Staff Terminal Common UI Component Design QA

final result: passed

## Reference

- `/Users/donghyunkim/Downloads/stitch_ (은행 통합단말 디자인)/_1/code.html`
- `/Users/donghyunkim/Downloads/stitch_ (은행 통합단말 디자인)/_2/code.html`
- Source screenshot used for visual comparison: `/private/tmp/banking-lab-stitch/stitch_/_1/screen.png`

## Prototype Capture

- `http://localhost:3002/`
- Current screenshot: `/private/tmp/staff-terminal-common-ui-components.png`
- Viewport/state: in-app browser desktop viewport, `_1` navigation workstation rendered at `/`

## Checks

- Top navigation now matches the source structure: brand, search, six business modules, support/settings separator, and completion button.
- Left mini sidebar and secondary 업무 메뉴 트리 match the source hierarchy, including expanded 수신 and collapsed 여신/외환 folders.
- Main workspace matches the source layout at the current in-app browser viewport: window tabs, task tabs, 16px canvas gutter, source panel heights, and right 인사이드뷰.
- Source panels now use the same content model as `_1/code.html`: 수신업무 중간화면, 수신업무 공지사항, 자주묻는 질문 empty state, 알면 편한 단말 메뉴얼, 신규화면 공지, and the compact inside-view product widgets.
- `_1` screen is now composed through reusable shell/sidebar/tabs/panel/table/field/status components, while preserving the rendered page hierarchy.
- `_2` profile sidebar, alert/progress/calendar widgets, right rail, portal dashboard panels, and footer link bar are captured as reusable component patterns without exposing a new route.
- Bottom status bar is scoped to the workspace, not the full app frame, matching the source.
- Material Symbols icons are self-hosted and rendered with source-like sizes by location. Browser verification found `materialFontLoaded=true`, `iconCount=48`, and `textGlyphLeakCount=0`.

## Accepted Differences

- The implementation remains in the Next.js staff-terminal app and keeps hidden manifest/API smoke evidence in the DOM for banking-lab control tests.
- `_2` is implemented as `TerminalPortalDashboard` for reuse and tests, but is not routed per the plan.
- The app keeps synthetic-lab constraints; no real customer PII, real money movement, real financial APIs, or real payment network integration was added.
