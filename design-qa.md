# Staff Terminal Integrated Screen Design QA

final result: passed

## Reference

- `/private/tmp/banking-lab-stitch/stitch_/_1/screen.png`
- `/private/tmp/banking-lab-stitch/stitch_/_2/screen.png`
- `/private/tmp/banking-lab-stitch/stitch_/nexus_terminal_system/DESIGN.md`

## Prototype Capture

- `http://127.0.0.1:3002`
- `/private/tmp/staff-terminal-integrated-screen-v2.png`
- viewport: `1600x1280`

## Checks

- Matches the supplied high-density enterprise terminal frame: top navigation, mini utility sidebar, contextual menu tree, window tabs, task tabs, bento work area, right inside-view panel, and bottom status bar.
- Preserves banking-lab constraints by replacing source mockup real-bank/person/product cues with synthetic `Banking Lab`, `branch01`, `SYN-*`, `LAB-***-*`, manifest, audit, workflow, and API smoke data.
- Keeps manifest-first behavior visible through transaction-code input, role-aware menu, manifest-rendered screen cards, reason-required count, maker-checker count, masking summary, audit log panel, workflow timeline, and exception/retry panel.
- Removes dependency on externally loaded icon fonts after visual QA found Material Symbols fallback text rendering in the local capture.

## Accepted Differences

- Source mockup profile/payment/logo assets were not copied because the lab must not imply real customer PII, real payment brands, or a real financial institution.
- The right-side marketing/product widgets are represented as synthetic inside-view and API-backed smoke panels to keep parity with the current Next.js staff-terminal evidence path.
