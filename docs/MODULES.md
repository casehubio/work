# Module Map

Use `ide_find_class` / `ide_find_symbol` to locate specific classes. This file documents module ownership and structural constraints that the IDE can't tell you.

## Core Modules

| Module | Purpose | Key constraints |
|---|---|---|
| `api/` | Pure-Java SPI — no JPA, no REST | All SPIs, events, value objects. casehub-engine depends on this directly. |
| `core/` | Jandex library — no JPA, no REST | WorkBroker, built-in strategies, claim SLA policies; pure CDI. No filter classes — filter engine moved to `runtime/filter/` in #133. |
| `runtime/` | Extension runtime | WorkItem entity, JPA stores, filter engine, multi-instance coordinator, CloudEvent adapter (`WorkCloudEventAdapter`), dual-channel emitters (`WorkItemLifecycleEmitter`, `WorkItemGroupLifecycleEmitter`) |
| `rest/` | JAX-RS REST surface (`casehub-work-rest`) | Plain JAR + Jandex. Resources (12), request/response DTOs, exception mappers, WorkItemMapper. Opt-in via dependency — consumers who only need the Java SPI do not get REST auto-registered. |
| `deployment/` | Extension build-time | `WorkItemsProcessor` @BuildStep only |
| `persistence-memory/` | In-memory persistence (`casehub-work-persistence-memory`) | Thread-safe ConcurrentHashMap stores; Tier 3 `@Alternative @Priority(100)` — beats JPA and MongoDB. For tests, demos, and ephemeral deployment. |
| `docs/` | Architecture, design, specs | `ARCHITECTURE.md` (SPI contracts), `DESIGN.md` (roadmap + Flyway history), `GOTCHAS.md`, `FLYWAY.md` |
| `scripts/` | Build helpers | See `scripts/README.md` for usage and expected test times |

## Integration Modules (built)

| Module | Purpose |
|---|---|
| `engine-adapter/` | CaseHub engine adapter; creates WorkItems from HumanTask/ActionGate bindings, translates lifecycle events back to PlanItem transitions. Relocated from `casehub-engine-work-adapter`. |
| `qhorus/` | Qhorus bridge; MCP tools for agent→WorkItem requests (`request_human_work`, `check_work_status`, `wait_for_work`), outbound lifecycle adapter posts terminal speech acts to originating channels. |
| `flow/` | Quarkus-Flow CDI bridge (`HumanTaskFlowBridge`, `PendingWorkItemRegistry`, `WorkItemFlowEventListener`) |
| `annotations/` | Annotation-driven human-in-the-loop model (`@HumanApproval`, `@RequiresQuorum`, `@Escalate`, `@SkillMatch`). CDI interceptor for standalone use; descriptor build items for blocks-engine-adapter composition. |
| `flow-examples/` | Example scenarios for the Quarkus-Flow integration |
| `ledger/` | Optional accountability module (command/event ledger, hash chain, attestation, EigenTrust) |
| `queues/` | Optional label-based queue module; label filter chains, queue views, JEXL/JQ expression evaluation |
| `queues-dashboard/` | Optional queue dashboard UI |
| `queues-examples/` | Example scenarios for the queue module |
| `ai/` | AI-native features; confidence gating via `LowConfidenceFilterProducer`; `SemanticWorkerSelectionStrategy` (@Alternative @Priority(1)) for embedding-based worker scoring |
| ~~`notifications/`~~ | **Removed** (#315) — replaced by platform subscription engine. `WorkItemSubscriptionBridge` in `runtime/` inserts lifecycle events into the platform DataSource. Flyway V3000–V3002 moved to runtime; V3003 drops the table. |
| `reports/` | Optional SLA compliance reporting; `/reports/sla-breaches`, `/actors/{id}`, `/throughput`, `/queue-health` |
| `postgres-broadcaster/` | Optional distributed SSE; PostgreSQL LISTEN/NOTIFY for WorkItem events (`casehub_work_events`) |
| `queues-postgres-broadcaster/` | Optional distributed SSE for queue events (`casehub_work_queue_events`); depends on `casehub-work-queues` + `quarkus-reactive-pg-client` |
| `issue-tracker/` | Optional issue-tracker link module; `IssueTrackerProvider` SPI; GitHub and Jira webhook handlers. Flyway V5000. |
| `federation/` | Optional cross-service federation; `FederationGuardStore` (`@Decorator` on `WorkItemStore.put()`), `FederationReceiver` (inbound CloudEvents → shadow WorkItems), `FederationEventRouter` (outbound lifecycle events → subscriptions), `FederationProxyService` (`@Decorator` on `WorkItemOperations`), subscription model with filter-on-creation lock-on. Flyway V8000. |
| `client/` | Lightweight REST client for remote WorkItem operations (claim, complete, reject, delegate, release). No JPA, no CDI, no Quarkus dependencies. Used by `FederationProxy` and standalone consumers. |
| `examples/` | Runnable scenario demos; each runs via `POST /examples/{name}/run` |
| `persistence-mongodb/` | Optional MongoDB-backed stores for all runtime/core/issue-tracker SPIs; Tier 2 `@Alternative @Priority(1)`. Drop-in replacement for JPA — add to classpath, no consumer changes needed. |
| `integration-tests/` | `@QuarkusIntegrationTest` suite and native image validation |
| `integration-tests-memory/` | Ephemeral deployment ITs — `persistence-memory` stores with dummy H2 datasource (no Flyway, no schema); verifies full CRUD without a real database |

## Progress Modules (#237)

| Module | Purpose |
|---|---|
| `progress-api/` | Pure Java: `ProgressInstance`, `ProgressStatus`, `StepStatus`, `StepDefinition`, `ProgressUpdatedEvent`, `ProgressChangeType`, `ProgressCreateRequest`, `ProgressSnapshot`, `RollupStrategy` SPI, `ConditionEvaluator` SPI, `CustomRollbackDetector` SPI, `VisualisationModes`, `RollbackPolicies`, store SPIs. Package: `io.casehub.work.progress` |
| `progress-core/` | Pure Java + Jandex: shape validators (percentage, count, step, custom via `json-schema-validator`), DAG cycle detection, step dependency/condition validation, rollback detection (three-tier: built-in, `rollbackField`, pluggable `CustomRollbackDetector`), built-in rollup strategies (`count-completed`, `average-percentage`, `weighted-percentage`), `RollupEngine` |
| `progress-runtime/` | Quarkus extension (`casehub-work-progress`): `ProgressService` (incl. rollback, rollbackToEvent, getSnapshots), JPA entities (`ProgressInstanceEntity`, `ProgressEventEntity`), JPA stores, `RollupObserver` (`@ObservesAsync` + OCC retry), `ProgressEventBroadcaster` SPI + `LocalProgressEventBroadcaster`, `JqConditionEvaluator`. Flyway V7000–V7002. |
| `progress-rest/` | JAX-RS: CRUD + query + step convenience + rollback + snapshots + SSE streaming endpoints. `ProgressResource`, DTOs. Opt-in via dependency. |
| `progress-deployment/` | `ProgressProcessor` @BuildStep — registers rollup strategy beans |
| `progress-memory/` | In-memory stores (`@Alternative @Priority(100)`) for tests |
