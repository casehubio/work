# Quarkus WorkItems — Session Handover
**Date:** 2026-04-22

## Project Status

683+ tests, 0 failures. Major architectural separation completed this session.

| Module | Tests |
|---|---|
| quarkus-work-api | 15 |
| quarkus-work-core | 38 |
| runtime | 548 |
| workitems-flow | 32 |
| quarkus-workitems-queues | 82 |
| quarkus-workitems-ai | 8 |
| testing | 16 |
| (ledger, examples, integration-tests unchanged) | — |

## Branch State

Work lives on `feature/work-separation` (worktree at `.worktrees/work-separation`).
Issue #118 is closed. **Merge to `main` before starting next session.**

```bash
git checkout main
git merge feature/work-separation
```

## What Was Built This Session

**Epic #118 — `quarkus-work` / `quarkus-workitems` separation.**

Two new foundational modules extracted (groupId `io.quarkiverse.work`):

- **`quarkus-work-api`** — pure-Java SPI, zero runtime deps: `WorkerCandidate`, `SelectionContext`,
  `AssignmentDecision`, `AssignmentTrigger`, `WorkerSelectionStrategy`, `WorkerRegistry`,
  `WorkEventType`, `WorkLifecycleEvent`, `WorkloadProvider`, `EscalationPolicy`

- **`quarkus-work-core`** — Jandex library (not a Quarkus extension): `WorkBroker` (dispatches
  assignment via strategy), `LeastLoadedStrategy`, `ClaimFirstStrategy`, `NoOpWorkerRegistry`;
  filter engine: `FilterRegistryEngine`, `PermanentFilterRegistry`, `DynamicFilterRegistry`,
  `FilterRule`, `FilterRuleResource`, `JexlConditionEvaluator`, `FilterAction`, `FilterDefinition`,
  `FilterEvent`, `ActionDescriptor`

Two modules deleted:
- `quarkus-workitems-api` → absorbed into `quarkus-work-api`
- `quarkus-workitems-filter-registry` → dissolved into `quarkus-work-core` + `runtime/action/`

**Key runtime changes:**
- `WorkItemLifecycleEvent` now extends `WorkLifecycleEvent`; `source()` returns `Object`; URI via `sourceUri()`
- `EscalationPolicy` reduced to single `escalate(WorkLifecycleEvent)` method
- `ClaimDeadlineJob` fires `CLAIM_EXPIRED` event type (not `ESCALATED`)
- `FilterRegistryEngine` observes `WorkLifecycleEvent` (any subtype, not WorkItem-specific)
- New: `WorkItemContextBuilder.toMap(WorkItem)` — JEXL context builder (moved from queues module)
- New: `JpaWorkloadProvider` — implements `WorkloadProvider` via JPA
- New: `runtime/action/` — `ApplyLabelAction`, `OverrideCandidateGroupsAction`, `SetPriorityAction`

## Immediate Next Step

1. Merge `feature/work-separation` → `main`
2. Continue **Epic #100** — semantic skill matching:
   LangChain4j-backed `WorkerSelectionStrategy` that embeds worker skills and work item requirements,
   then scores candidates by cosine similarity. Lives in `quarkus-workitems-ai`.

## Priority Roadmap

| Priority | # | Epic | Status |
|---|---|---|---|
| 1 | #100 | AI-Native Features | **active** — next: semantic skill matching, AI-suggested resolution, escalation summarisation |
| 2 | #101 | Business-Hours Deadlines | **active** — BusinessCalendar SPI |
| 3 | #103 | Notifications | **active** — `quarkus-workitems-notifications` module |
| 4 | #104 | SLA Compliance Reporting | **active** — GET /workitems/reports/sla-breaches |
| 5 | #105 | Subprocess Spawning | **active** — WorkItemSpawnRule entity |
| 6 | #106 | Multi-Instance Tasks | **active** — MultiInstanceConfig on template |
| — | #92 | Distributed WorkItems | future — #93 (SSE) implementable now |
| — | #79 | External System Integrations | blocked — CaseHub/Qhorus not stable |
| — | #39 | ProvenanceLink (PROV-O) | blocked — awaiting #79 |
| ✅ | #102 | Workload-Aware Routing | complete |
| ✅ | #98, #99 | Form Schema, Audit History | complete |
| ✅ | #77,78,80,81 | Collaboration, Queues, Storage, Platform | complete |

## Open Issues

| Status | Detail |
|---|---|
| #118 closed | `quarkus-work` separation done |
| #117 deferred | RoundRobinStrategy (requires stateful cursor) |
| #79, #39 blocked | External integrations and provenance |

## References

| What | Path |
|---|---|
| Design tracker | `docs/DESIGN.md` |
| Primary design spec | `docs/specs/2026-04-14-tarkus-design.md` |
| Epic priority table | `CLAUDE.md` Work Tracking section |
| work-api SPI | `quarkus-work-api/src/main/java/io/quarkiverse/work/api/` |
| work-core filter engine | `quarkus-work-core/src/main/java/io/quarkiverse/work/core/filter/` |
| work-core strategies | `quarkus-work-core/src/main/java/io/quarkiverse/work/core/strategy/` |
| AI module | `quarkus-workitems-ai/` |
