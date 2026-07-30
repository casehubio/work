# Alternative Scheduler SPI — Design Spec

**Issue:** engine#813
**Date:** 2026-07-30
**Status:** Draft

## Problem

The engine's scheduler SPIs (`JobScheduler`, `WorkerExecutionManager`) are designed for pluggable scheduler backends, but two leaks couple them to Quartz:

1. `ScheduledJobRequest.jobClass` carries `Class<?>` that must implement `org.quartz.Job`
2. `CronSchedule` accepts Quartz 6-field cron expressions (seconds field, `?` character)

These leaks prevent a second scheduler implementation from slotting in cleanly.

Additionally, the engine lacks a modern, lightweight scheduler alternative. Quartz requires 11+ database tables for JDBC persistence and has a dated API. db-scheduler (Apache 2.0, ~1,600 stars, actively maintained) offers the same capabilities with 1 table, a modern fluent API, and built-in clustering via database polling.

## Decision

Fix both SPI leaks and build a `scheduler-dbscheduler` module in the same issue. The db-scheduler module validates the SPI changes — if it can't implement the SPIs cleanly, the SPI design is wrong.

Both scheduler modules coexist. An installation selects one — no mix and match. Quartz stays until db-scheduler proves itself across all four job types.

## Approach: Approach B — Fix SPI leaks + build db-scheduler module together

Selected over:
- **A (SPI fixes only):** No second implementation to validate the SPI design
- **C (New unified SPI):** Unnecessary — the current SPIs are sound, they just have two small leaks

## SPI Leak Fixes

### Remove `jobClass` from `ScheduledJobRequest`

Callers already signal intent via a `triggerType` string in the data map. Replace the implicit convention with a first-class typed field.

**New enum** in `io.casehub.engine.common.internal.scheduler`:

```java
public enum JobType {
    SCHEDULED_TRIGGER_UNCONDITIONAL,
    SCHEDULED_TRIGGER_CONDITIONAL,
    MILESTONE_SLA_TIMEOUT
}
```

**`ScheduledJobRequest` changes:**
- Remove `Class<?> jobClass` field
- Add `JobType jobType` field (required)
- Remove `Builder.jobClass(Class<?>)` method
- Add `Builder.jobType(JobType)` method

**Caller migration:**
- `SchedulerService.scheduleWorker()` — stop putting `triggerType` in data map, use `.jobType(SCHEDULED_TRIGGER_UNCONDITIONAL)`
- `SchedulerService.scheduleConditionalWorker()` — use `.jobType(SCHEDULED_TRIGGER_CONDITIONAL)`
- `MilestoneActivatedEventHandler.scheduleSlaTimeoutJob()` — use `.jobType(MILESTONE_SLA_TIMEOUT)`

**Scheduler module migration:**
- `QuartzJobScheduler.resolveJobClass()` — switch on `JobType` instead of parsing `triggerType` from data map. Maps `JobType` → Quartz `Job` class.
- `DbSchedulerJobScheduler` — maps `JobType` → db-scheduler task handler.

### Normalize Cron to 5-Field

`CronSchedule` validates at construction time: exactly 5 space-separated fields, no `?`, no `L`/`W`/`#`.

Format: `minute hour day-of-month month day-of-week`

**Translation at the edges:**
- **Quartz module:** prepends `0 ` (seconds=0), replaces `*` with `?` in day-of-week field when day-of-month is specific
- **db-scheduler module:** passes through directly (Spring-style cron is 5-field)

**Caller migration:** Existing Quartz-format expressions update at the definition site:
- `"0 */5 * * * ?"` → `"*/5 * * * *"`
- `"0 0 0 * * ?"` → `"0 0 * * *"`

**Schema update:** `CaseDefinition.yaml` schema description changes from "Quartz cron" to "5-field cron (minute hour day-of-month month day-of-week)".

## `scheduler-dbscheduler` Module

### Maven Coordinates

```xml
<artifactId>casehub-engine-scheduler-dbscheduler</artifactId>
```

### Dependencies

- `casehub-engine-common` (compile) — SPIs, domain types
- `casehub-engine-api` (compile) — CaseDefinition, Binding, Worker
- `db-scheduler` (compile) — scheduler library
- `quarkus-agroal` (compile) — DataSource injection
- `quarkus-arc` (compile) — CDI

### Production Classes

| Class | Implements | Role |
|-------|-----------|------|
| `DbSchedulerJobScheduler` | `JobScheduler` | Maps `ScheduledJobRequest` → db-scheduler tasks. Maps `JobType` → task handler. Implements `schedule`, `cancel`, `cancelGroup`, `exists`. |
| `DbSchedulerWorkerExecutionManager` | `WorkerExecutionManager` (`@WorkerBackend`) | Submits worker execution as one-time tasks. Tracks active work via db-scheduler query API. |
| `DbSchedulerLifecycle` | CDI producer | Builds `Scheduler` from injected `DataSource` on `StartupEvent`, registers task definitions, starts polling. Stops on `ShutdownEvent`. Produces `SchedulerClient` as `@ApplicationScoped`. |
| `WorkerExecutionTaskHandler` | db-scheduler `ExecutionHandler` | Resolves case/worker/capability from task data, delegates to `WorkerExecutor`, publishes success via event bus, routes failure to `DbSchedulerRetryService`. |
| `ScheduledTriggerTaskHandler` | `ExecutionHandler` | Loads case, verifies RUNNING state, publishes `WorkerScheduleEvent`. |
| `ConditionalScheduledTriggerTaskHandler` | `ExecutionHandler` | Same as above but evaluates binding `when` condition first. |
| `MilestoneSLATimeoutTaskHandler` | `ExecutionHandler` | Checks milestone is still ACTIVE, publishes `MilestoneSLAViolatedEvent`. |
| `DbSchedulerRetryService` | — | Persists `WORKER_EXECUTION_FAILED` event log, counts prior failures, calls `RetryPolicies.evaluate()`, reschedules or publishes `WORKER_RETRIES_EXHAUSTED`. |

### Lifecycle Wiring

```java
@ApplicationScoped
public class DbSchedulerLifecycle {
    @Inject DataSource dataSource;
    @Inject Instance<Task<?>> tasks;
    private Scheduler scheduler;

    void onStart(@Observes StartupEvent ev) {
        scheduler = Scheduler.create(dataSource)
            .knownTasks(/* collect from CDI */)
            .threads(/* from config */)
            .build();
        scheduler.start();
    }

    void onStop(@Observes ShutdownEvent ev) {
        scheduler.stop();
    }

    @Produces @ApplicationScoped
    public SchedulerClient client() { return scheduler; }
}
```

### Job Listener Equivalent

db-scheduler's `ExecutionInterceptor` provides before/after hooks per execution. `DbSchedulerWorkerExecutionManager` registers an interceptor to fire `WorkerExecutionStarted` lifecycle events and persist start EventLog entries — same role as `QuartzWorkerExecutionJobListener`.

## Consumer Module Decoupling

### Current State

6 modules depend on `scheduler-quartz` at compile scope: `runtime`, `planning`, `resilience`, `queue`, `actor-state`, `flow`. None reference Quartz types — they depend on it for CDI bean discovery.

### Target State

- Remove `casehub-engine-scheduler-quartz` as a compile dependency from all 6 modules
- Consumer modules depend only on `casehub-engine-common` (which owns the SPIs)
- The scheduler module is a runtime choice — added by the deployment assembly or test config
- `quarkus.index-dependency.scheduler-*` entries move to the point of selection

### Test Migration

No forced migration. Existing `@QuarkusTest` classes keep `scheduler-quartz` as a test dependency with `quarkus.quartz.store-type=ram`. New tests can use `scheduler-dbscheduler` with H2. Migration happens organically.

## Configuration and Table Management

### db-scheduler Table

Single `scheduled_tasks` table (12 columns).

**PostgreSQL production:** Flyway migration in `persistence-hibernate` alongside the existing Quartz `V1.0.0` migration. Both migrations coexist — tables don't conflict.

**H2 in-memory:** db-scheduler's built-in `startTasks.createIfNotExists()` handles table creation automatically.

### Application Properties

```properties
# db-scheduler config
casehub.scheduler.polling-interval=10s
casehub.scheduler.threads=5

# H2 in-memory path for lightweight installations
casehub.scheduler.datasource.db-kind=h2
casehub.scheduler.datasource.jdbc.url=jdbc:h2:mem:scheduler;DB_CLOSE_DELAY=-1
```

No `quarkus.quartz.*` properties needed when using db-scheduler. When using Quartz, nothing changes.

### Selection Mechanism

Installation picks one scheduler module on the classpath. Both modules' beans are `@ApplicationScoped` (not `@DefaultBean`). Having both on the classpath is a CDI ambiguity error — deliberate, forces a choice.

## Retry Handling

**Reuse:** `RetryPolicies.evaluate()` and `RetryDecision` (sealed: `Retry`/`Exhaust`) live in `common/internal/executor/` — scheduler-agnostic.

**db-scheduler's built-in retry is not used.** The engine's retry logic is richer — reads policy from `ExecutionPolicy`, counts from event log, uses `RetryPolicies` for backoff computation. db-scheduler's `FailureHandler` would duplicate and conflict.

**Flow:**
1. Worker execution fails → `WorkerExecutionTaskHandler` catches failure
2. Persists `WORKER_EXECUTION_FAILED` event log
3. Counts prior failures from event log
4. Calls `RetryPolicies.evaluate()` → `Retry(delay)` or `Exhaust(reason)`
5. `Retry`: reschedules via `schedulerClient.reschedule()` with computed delay
6. `Exhaust`: publishes `WORKER_RETRIES_EXHAUSTED` on event bus

Tasks are marked complete from db-scheduler's perspective on every execution. Retry is a new scheduled task, not a db-scheduler retry.

## Scope Boundary

### In Scope
- `JobType` enum and `ScheduledJobRequest` migration
- 5-field cron validation in `CronSchedule` and caller migration
- `scheduler-dbscheduler` module with all 8 production classes
- Consumer module dependency decoupling
- Flyway migration for db-scheduler table
- Configuration properties

### Out of Scope
- Removing `scheduler-quartz` — it stays as an alternative
- Migrating existing test suites from Quartz to db-scheduler
- Quartz cron features (`L`/`W`/`#`) — not supported by the 5-field SPI
- Clustering configuration (db-scheduler clusters by default, no engine work needed)
