# HANDOFF — 2026-08-13

## Last Session

WorkItemStore SPI extraction (#337) — Tasks 7-10 completed. All runtime services, JPA/MongoDB/InMemory stores, and 9 downstream platform modules (rest, queues, ai, ledger, flow, issue-tracker, queues-dashboard, engine-adapter, qhorus) now compile against the `WorkItem` record from `api/`. The `toBuilder()` mutation pattern replaced all direct entity field mutation in services.

### What's Done

**Task 7 (runtime compilation):** 99 errors → 0. Changed 18 production files + 36 test files in runtime/. All services (WorkItemService, ExpiryLifecycleService, WorkItemSpawnService, etc.) use `WorkItem` records with `toBuilder()`. JPA stores map via `WorkItemEntityMapper` at persistence boundaries. `WorkItemLifecycleEvent`, `WorkItemAssignmentService`, `OutcomeValidator`, `WorkItemContextBuilder` all updated.

**Task 8 (InMemoryWorkItemStore):** Converted to store `WorkItem` records directly. Label matching uses `LabelPatternMatcher` from api/. Type matching uses `Set<String>`. Full pom.xml dependency shift deferred (other in-memory stores still need runtime).

**Task 9 (MongoDB stores):** `MongoWorkItemDocument.from()` accepts `WorkItem`, `toDomain()` returns `WorkItem` via builder. OCC preserved via version-checked `replaceOne`.

**Task 10 (import sweep):** All 9 downstream platform modules updated. `WorkItemMapper` (rest), `QueueBoardBuilder`, `LedgerEventCapture`, `GitHubIssueTrackerProvider`, `WebhookEventHandler`, AI services — all use `WorkItem` record accessors.

### What's NOT Done

**Examples + integration tests:** `examples/`, `queues-examples/`, `flow-examples/`, `integration-tests/`, `integration-tests-memory/` still reference `WorkItemEntity`. These files mix `WorkItem` records (from service calls) with JPA entities (`AuditEntry`, `LabelRuleEntity`, `WorkItemSpawnGroup`) that have public `.id` fields — text replacement can't distinguish them. **Do this in IntelliJ IDE with type-aware Find Usages.**

**Task 11 (progress API docs #333):** Not started. Independent of the SPI work.

**Runtime Quarkus integration tests:** Pre-existing CDI wiring gap — `CapabilityValidator` and `WorkerRegistry` unsatisfied in test context. Not caused by this change. Unit tests pass (33/33).

## Immediate Next Step

Fix the example and integration-test modules in IntelliJ IDE. Use Find Usages on `WorkItemService.create()` return type to identify all `WorkItemEntity` variables that should be `WorkItem`. Then do Task 11 (progress API docs).

## Cross-Module

**Blocking** (downstream repos committed but not pushed — push work first):
- aml, clinical, engine, examples, iot, devtown, life — entity rename committed on main, awaiting work SNAPSHOT publish

## References

| Artifact | Path |
|----------|------|
| Design spec | `specs/issue-333-progress-api-docs-spi-fix/2026-08-13-progress-docs-spi-extraction-design.md` |
| Plan | `plans/2026-08-13-progress-docs-spi-extraction.md` |
| Branch | `issue-333-progress-api-docs-spi-fix` |
| Issues | #333 (progress docs), #337 (SPI extraction), #340 (closed) |
