# HANDOFF — 2026-08-06

## Last Session

Closed `issue-869-humantask-resolved-scope` — fix for engine#869 where `HumanTaskScheduleHandler` ignored `resolvedScope` and `resolvedTitle` from `HumanTaskScheduleEvent`, always using static `target.scope()` and `target.title()`. Both `createInline()` and `handleTemplateMode()` now prefer resolved values when non-null, falling back to static values. Landed on origin/main as `5040f871`. PR #339 to casehubio still open.

## Immediate Next Step

Pick up #329 (progress model epic) or #800 (agent learning & memory, slot 83). Run `/work` to start.

## What's Left

- PR #339 to casehubio/work still open — merge when ready
- engine#647 work-end incomplete — rebase/squash/push/stamp/close remaining · XS · Low
- PLATFORM.md update for behavioral contracts capability ownership (AC4 from #647) — parent repo · S · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #329 | Epic: Progress model enhancements (#307, #309, #308) | L | Med | Slot 8 created |
| #800 | Agent Learning & Memory epic | XL | High | Slot 83 ready; brainstorming paused at scope |
| #330 | Epic: Queue summary — only #306 remains (caching/materialised views) | M | Med | #305 already closed |
| #298 | Replace event-as-request pattern with direct WorkItemCreator.create() | M | Med | Design |
| #152 | Split examples into core and full variants | M | Low | — |
