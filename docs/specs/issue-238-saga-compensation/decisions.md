## D1: Step advancement observer placement

**Choice:** Add CDI observer directly to CaseCompensationServiceImpl
**Alternatives:**
- Separate CompensationStepAdvancementHandler class — cleaner separation but adds a class and interface coupling for no real benefit
**Rationale:** The observer is the advancement side of the same saga state machine. The service already owns all required state (caseInstanceCache, blackboardRegistry, eventLogRepository). Keeping it cohesive avoids unnecessary indirection.
**Trade-offs:** CaseCompensationServiceImpl grows by ~30 lines; if compensation observes more event types in future, may warrant extraction
**Sources:** CaseCompensationServiceImpl.java, PlanItemStateChangedEvent.java, PlanItemCompletionHandler.java
**Exploration:** quick
**Status:** captured

## D2: Compensating binding dispatch mechanism

**Choice:** Direct WORKER_SCHEDULE publish from fireNextCompensationStep()
**Alternatives:**
- Add compensation path to PlanningStrategyLoopControl + CONTEXT_CHANGED — couples saga ordering to the event-driven react cycle, which is designed for concurrent evaluation not step-at-a-time ordering
- New CompensationBindingDispatcher service — premature abstraction; #387 will address HumanTask specifically
**Rationale:** Reuses the battle-tested WorkerScheduleEventHandler pipeline. Resolve Worker and Capability from CaseDefinition, construct WorkerScheduleEvent, publish. Non-CapabilityTarget bindings fault cleanly until #387 adds JudgmentTarget support.
**Trade-offs:** Only CapabilityTarget (worker) bindings are supported for compensation dispatch; JudgmentTarget deferred to #387
**Sources:** WorkerScheduleEvent.java, WorkerScheduleEventHandler.java, CaseContextChangedEventHandler.java (scheduleWorker pattern), PlanningStrategyLoopControl.java (COMPENSATING guard)
**Exploration:** quick
**Status:** captured

## D3: JudgmentTarget compensation dispatch via JudgmentScheduler SPI

**Choice:** Extend dispatchCompensatingBinding() with JudgmentScheduler SPI call, create JudgmentWorkItemScheduler in work engine-adapter
**Alternatives:**
- Use deprecated HumanTaskScheduler for compensation — wrong types (HumanTaskTarget vs JudgmentTarget), deprecated API
- New EventBus address for compensation judgment dispatch — adds infrastructure for what is essentially an SPI call
**Rationale:** JudgmentScheduler is the current SPI for judgment dispatch. Adding a JudgmentScheduler implementation in casehub-work that creates WorkItems is the correct long-term path — it also fixes the general case (non-compensation JudgmentTarget bindings). The saga coordinator calls the SPI consistently.
**Trade-offs:** JudgmentWorkItemScheduler replaces HumanTaskScheduleHandler as the primary WorkItem creation path for JudgmentTarget bindings — broader impact than just compensation
**Sources:** JudgmentScheduler.java, JudgmentScheduleRequest.java, HumanTaskScheduleHandler.java, CaseContextChangedEventHandler.publishJudgmentSchedule()
**Exploration:** quick
**Status:** captured

## D4: Fire PlanItemStateChangedEvent(COMPLETED) from work PlanItemCompletionApplier

**Choice:** Add COMPLETED event firing to work module's PlanItemCompletionApplier to match engine's PlanItemCompletionHandler
**Alternatives:**
- Have saga observe CONTEXT_CHANGED for completion — fragile, every context change triggers checks
**Rationale:** The engine's PlanItemCompletionHandler already fires PlanItemStateChangedEvent(COMPLETED). The work module's PlanItemCompletionApplier fires REJECTED and FAULTED but not COMPLETED — an inconsistency. Fixing this enables the saga advancement observer and benefits any future COMPLETED observers.
**Trade-offs:** None — strictly additive, matches existing pattern
**Sources:** PlanItemCompletionApplier.java (work), PlanItemCompletionHandler.java (engine), PlanItemStateChangedEvent.java
**Exploration:** quick
**Status:** captured
