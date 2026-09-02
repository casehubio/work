# Compensation Step Advancement Handler

**Issue:** casehubio/work#386 (child of #238)
**Module:** casehub-engine/planning
**File:** `CaseCompensationServiceImpl.java`

## Problem

The saga coordinator creates compensating PlanItems but:
1. Does not dispatch them for execution (PlanItem stays PENDING)
2. Does not observe their completion to advance to the next step

The normal react cycle cannot handle compensation because
`PlanningStrategyLoopControl.select()` returns `List.of()` for
COMPENSATING status and filters out compensation bindings.

## Design

All changes are in `CaseCompensationServiceImpl`. Two additions:

### 1. Dispatch — in `fireNextCompensationStep()`

After creating the compensating PlanItem and adding it to the plan,
dispatch the binding for execution:

1. Mark the PlanItem as DISPATCHING via `tryMarkDispatching()`
2. Resolve the binding's target type:
   - **CapabilityTarget:** resolve Worker from `CaseDefinition.getWorkers()`
     by matching `executor.name()`, get Capability from the target, publish
     `WorkerScheduleEvent` to `WORKER_SCHEDULE`
   - **Other targets:** transition to COMPENSATION_FAULTED with error detail
     ("Unsupported compensation target type"). HumanTask support is #387.

### 2. Advancement — CDI observer on `PlanItemStateChangedEvent`

New method `onCompensationPlanItemStateChanged(@ObservesAsync PlanItemStateChangedEvent)`:

1. Look up PlanItem from `BlackboardRegistry` by `event.planItemId()`
2. Guard: `item.isCompensation()` must be true
3. Guard: case must be in `COMPENSATING` state
4. On `COMPLETED`:
   - Look up the original PlanItem via `item.getCompensatesItemId()`
   - Log `COMPENSATION_STEP_COMPLETED` event with original and
     compensating binding names
   - Call `fireNextCompensationStep()` to advance the saga
5. On `FAULTED`:
   - Call `transitionToFaulted()` with error detail

### Event flow

```
compensate() → COMPENSATING → fireNextCompensationStep()
  → create PlanItem → dispatch via WORKER_SCHEDULE
  → WorkerScheduleEventHandler executes worker
  → WORKER_EXECUTION_FINISHED
  → PlanItemCompletionHandler marks COMPLETED
  → fires PlanItemStateChangedEvent(COMPLETED)
  → onCompensationPlanItemStateChanged() observes
  → logs COMPENSATION_STEP_COMPLETED
  → calls fireNextCompensationStep() (next step)
  → ... repeats until no remaining items ...
  → transitionToCompensated() → COMPENSATED
```

Fault path:
```
  → PlanItem FAULTED
  → onCompensationPlanItemStateChanged() observes
  → transitionToFaulted() → COMPENSATION_FAULTED
```

### Dependencies added to CaseCompensationServiceImpl

- `CaseDefinition.getWorkers()` — already available via
  `caseDefinitionRegistry` (existing field)
- `WorkerScheduleEvent` — in `engine.common.internal.event`, transitively
  available via planning → engine-common dependency
- `Worker`, `Capability` — in `casehub-worker-api`, transitively available
  via planning → engine-api → worker-api

No new module dependencies.

## Test Plan

New test class: `CaseCompensationServiceImplTest` in
`planning/src/test/java/.../compensation/`

Unit tests with mocked dependencies:

1. **dispatch_publishes_workerScheduleEvent** — verify
   `fireNextCompensationStep()` publishes `WorkerScheduleEvent` with
   correct Worker, Capability, and bindingName
2. **dispatch_marks_planItem_dispatching** — verify PlanItem status
   transitions to DISPATCHING before dispatch
3. **dispatch_unsupported_target_faults** — verify non-CapabilityTarget
   binding transitions to COMPENSATION_FAULTED
4. **advancement_completed_fires_next_step** — verify
   `PlanItemStateChangedEvent(COMPLETED)` for a compensation PlanItem
   triggers `fireNextCompensationStep()`
5. **advancement_completed_logs_step_completed** — verify
   `COMPENSATION_STEP_COMPLETED` event is appended
6. **advancement_faulted_transitions_to_faulted** — verify
   `PlanItemStateChangedEvent(FAULTED)` for a compensation PlanItem
   transitions to COMPENSATION_FAULTED
7. **advancement_ignores_non_compensation_planItems** — verify
   normal PlanItem state changes are ignored
8. **advancement_ignores_when_not_compensating** — verify events are
   ignored when case is not in COMPENSATING state
9. **full_saga_two_steps** — end-to-end: two compensable PlanItems,
   verify both are compensated in reverse order and case reaches
   COMPENSATED

## Scope Limits

- Only `CapabilityTarget` (worker) bindings supported for compensation
  dispatch. HumanTask, SubCase, Judgment targets fault cleanly.
- No retry logic for faulted compensation steps (retry is via
  `COMPENSATION_FAULTED → compensate()` re-entry, already supported).
- No concurrent compensation steps (strict one-at-a-time ordering).

## References

- `CaseCompensationServiceImpl.java` — saga coordinator
- `PlanItemCompletionHandler.java` — fires `PlanItemStateChangedEvent(COMPLETED)` for worker completions
- `PlanItemStateChangedEvent.java` — CDI event carrying planItemId, bindingName, statuses, caseId
- `WorkerScheduleEvent.java` — event for dispatching workers
- `WorkerScheduleEventHandler.java` — consumes `WORKER_SCHEDULE` and executes workers
- `PlanningStrategyLoopControl.java` — confirms compensation bindings are excluded from normal react cycle
- `CaseStatusChangedHandler.java` — confirms COMPENSATING is handled in status change persistence
- `CaseStatus.java` — COMPENSATING is non-terminal, COMPENSATED is terminal
