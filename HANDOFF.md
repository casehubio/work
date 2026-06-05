# HANDOFF — 2026-06-05

## Last Session

CI fix session (#250). Four call sites from #245's `delegate()` API change (`DeclineTarget` parameter) had never been updated in modules outside `runtime`. Fixed in sequence as each module's failure became visible: `LedgerIntegrationTest` (compile error), `CreditDecisionScenario` (missing arg + wrong method — `claim()` → `acceptDelegation()`), `LedgerEventCapture` (two new event suffixes missing from `EVENT_META`, silently dropped), `WorkItemNativeIT` (status assertion stale — `PENDING` → `DELEGATED`). CI is green.

Key finding: `WorkItemLifecycleEvent.of("DELEGATION_ACCEPTED")` normalizes to `io.casehub.work.workitem.delegation_accepted`; `LedgerEventCapture.EVENT_META` keys are lowercase underscore suffixes. Any new lifecycle event in the service needs a matching entry in both places — the drop is silent.

## Immediate Next Step

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## What's Left

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## What's Next

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## References

- Garden: `GE-20260605-16a8fc` (jvm/) — WorkItemLifecycleEvent.of() lowercases event name; LedgerEventCapture EVENT_META must use normalized suffix
- Blog: `2026-06-05-mdp02-delegation-api-debt.md` — CI debt from delegation API change
- casehubio/parent#170 — downstream update still open (casehub-work.md references DESIGN.md/ARCHITECTURE.md)
