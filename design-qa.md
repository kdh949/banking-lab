# Staff Terminal Source Match Design QA

final result: passed

## Reference

- `/Users/donghyunkim/Downloads/stitch_ (은행 통합단말 디자인)/_1/code.html`
- Source screenshot used for visual comparison: `/private/tmp/banking-lab-stitch/stitch_/_1/screen.png`

## Prototype Capture

- `http://localhost:3002/`
- Current screenshot: `/private/tmp/staff-terminal-integrated-screen-v5.png`

## Checks

- Top navigation now matches the source structure: brand, search, six business modules, support/settings separator, and completion button.
- Left mini sidebar and secondary 업무 메뉴 트리 match the source hierarchy, including expanded 수신 and collapsed 여신/외환 folders.
- Main workspace matches the source layout at the current in-app browser viewport: window tabs, task tabs, 16px canvas gutter, source panel heights, and right 인사이드뷰.
- Source panels now use the same content model as `_1/code.html`: 수신업무 중간화면, 수신업무 공지사항, 자주묻는 질문 empty state, 알면 편한 단말 메뉴얼, 신규화면 공지, and the compact inside-view product widgets.
- Bottom status bar is scoped to the workspace, not the full app frame, matching the source.
- Material Symbols icons are self-hosted and rendered with source-like sizes by location.

## Accepted Differences

- The implementation remains in the Next.js staff-terminal app and keeps hidden manifest/API smoke evidence in the DOM for banking-lab control tests.
- The app keeps synthetic-lab constraints; no real customer PII, real money movement, real financial APIs, or real payment network integration was added.
