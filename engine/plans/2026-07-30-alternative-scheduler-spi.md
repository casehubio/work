# Alternative Scheduler SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #813 — Alternative Scheduler SPI
**Issue group:** #813

**Goal:** Fix scheduler SPI leaks and add db-scheduler as an alternative scheduler implementation.

**Architecture:** Fix two SPI leaks (`jobClass` field, 6-field cron) in `casehub-engine-common`, extract shared orchestration logic from `scheduler-quartz` into common, build a new `scheduler-dbscheduler` module implementing the same SPIs, and add contract tests proving both implementations honor the same semantics.

**Tech Stack:** Java 21, Quarkus 3.32.x, db-scheduler 16.x, H2 (in-memory for tests), PostgreSQL (production Flyway migration)

## Global Constraints

- Pre-release platform — breaking changes are free. Fix the design, never protect callers.
- IntelliJ MCP mandatory for all `.java` edits. Use `ide_edit_member`, `ide_replace_member`, `ide_insert_member`, `ide_create_file` for code. Use `ide_refactor_rename`, `ide_move_file`, `ide_refactor_safe_delete` for refactoring. Use `ide_diagnostics` and `ide_build_project` to verify.
- Both scheduler modules coexist. Installation selects one — no mix and match. Having both on the classpath is a CDI ambiguity error (deliberate).
- db-scheduler's built-in retry is NOT used. The engine's `RetryPolicies` + `RetryOrchestrator` own retry.
- Cron expressions use 5-field format (minute hour day-of-month month day-of-week). No `?`, no `L`/`W`/`#`, no seconds field.

---

### Task 1: SPI Leak Fixes — `JobType` Enum and `ScheduledJobRequest` Migration

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/internal/scheduler/JobType.java`
- Modify: `common/src/main/java/io/casehub/engine/common/internal/scheduler/ScheduledJobRequest.java`
- Modify: `common/src/main/java/io/casehub/engine/common/internal/scheduler/ScheduleStrategy.java` (CronSchedule validation)
- Modify: `runtime/src/main/java/io/casehub/engine/internal/scheduler/SchedulerService.java`
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/MilestoneActivatedEventHandler.java`
- Modify: `scheduler-quartz/src/main/java/io/casehub/engine/scheduler/quartz/QuartzJobScheduler.java`
- Modify: `common/src/main/java/io/casehub/engine/common/spi/scheduler/WorkerExecutionManager.java` (javadoc)
- Modify: `api/src/main/java/io/casehub/api/model/ScheduleTrigger.java` (javadoc)
- Modify: `schema/src/main/resources/schema/CaseDefinition.yaml` (cron description)
- Modify: `api/src/test/java/io/casehub/api/model/ScheduleTriggerTest.java` (cron format)
- Test: `common/src/test/java/io/casehub/engine/common/internal/scheduler/CronScheduleValidationTest.java`

**Interfaces:**
- Produces: `JobType` enum (`SCHEDULED_TRIGGER_UNCONDITIONAL`, `SCHEDULED_TRIGGER_CONDITIONAL`, `MILESTONE_SLA_TIMEOUT`)
- Produces: `ScheduledJobRequest.getJobType()` replacing `getJobClass()`
- Produces: `CronSchedule` with 5-field validation

- [ ] **Step 1: Write CronSchedule validation test**

```java
package io.casehub.engine.common.internal.scheduler;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CronScheduleValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "*/5 * * * *",       // every 5 minutes
        "0 0 * * *",         // midnight daily
        "30 8 * * 1-5",      // 8:30 weekdays
        "0 */2 * * *"        // every 2 hours
    })
    void validFiveFieldCron(String expression) {
        var cron = new ScheduleStrategy.CronSchedule(expression);
        assertThat(cron.expression()).isEqualTo(expression);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0 */5 * * * ?",     // 6-field Quartz (has seconds)
        "*/5 * * * ?",       // contains ?
        "0 0 L * *",         // contains L
        "0 0 15W * *",       // contains W
        "0 0 * * 6#3",       // contains #
        "* * * *",           // only 4 fields
        "* * * * * *"        // 6 fields
    })
    void invalidCronRejected(String expression) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ScheduleStrategy.CronSchedule(expression));
    }

    @Test
    void nullCronRejected() {
        assertThatNullPointerException()
            .isThrownBy(() -> new ScheduleStrategy.CronSchedule(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=CronScheduleValidationTest -DfailIfNoTests=false`
Expected: FAIL — CronSchedule currently accepts any string.

- [ ] **Step 3: Create `JobType` enum**

Use `ide_create_file`:
```java
package io.casehub.engine.common.internal.scheduler;

public enum JobType {
    SCHEDULED_TRIGGER_UNCONDITIONAL,
    SCHEDULED_TRIGGER_CONDITIONAL,
    MILESTONE_SLA_TIMEOUT
}
```

- [ ] **Step 4: Add 5-field validation to `CronSchedule`**

Use `ide_edit_member` on `ScheduleStrategy`, member `CronSchedule`:
```java
record CronSchedule(String expression) implements ScheduleStrategy {
    public CronSchedule {
        Objects.requireNonNull(expression, "expression must not be null");
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException(
                "Cron expression must have exactly 5 fields (minute hour day-of-month month day-of-week), got "
                    + fields.length + ": " + expression);
        }
        if (expression.contains("?") || expression.contains("L")
            || expression.contains("W") || expression.contains("#")) {
            throw new IllegalArgumentException(
                "Cron expression must not contain Quartz-specific characters (?, L, W, #): " + expression);
        }
    }
}
```

Update the `CronSchedule` javadoc from "Quartz cron expression" to "5-field cron expression (minute hour day-of-month month day-of-week)".

- [ ] **Step 5: Run CronSchedule test to verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=CronScheduleValidationTest`
Expected: PASS

- [ ] **Step 6: Migrate `ScheduledJobRequest` — replace `jobClass` with `jobType`**

Use `ide_edit_member` on `ScheduledJobRequest`:
- Remove `Class<?> jobClass` field, constructor param, getter, builder field, builder method
- Add `JobType jobType` field, constructor param, getter, builder field, builder method
- Update `toString()` to show `jobType` instead of `jobClass`

- [ ] **Step 7: Migrate callers**

Use `ide_replace_member` on `SchedulerService.scheduleWorker()`:
- Remove `jobData.put("triggerType", "unconditional")`
- Add `.jobType(JobType.SCHEDULED_TRIGGER_UNCONDITIONAL)` to the builder

Use `ide_replace_member` on `SchedulerService.scheduleConditionalWorker()`:
- Remove `jobData.put("triggerType", "conditional")`
- Add `.jobType(JobType.SCHEDULED_TRIGGER_CONDITIONAL)` to the builder

Use `ide_replace_member` on `MilestoneActivatedEventHandler.scheduleSlaTimeoutJob()`:
- Add `.jobType(JobType.MILESTONE_SLA_TIMEOUT)` to the builder

- [ ] **Step 8: Migrate `QuartzJobScheduler.resolveJobClass()`**

Use `ide_replace_member` on `QuartzJobScheduler.resolveJobClass()`:
- Switch on `request.getJobType()` instead of parsing `triggerType` from data map
- Map `JobType` → Quartz `Job` class

- [ ] **Step 9: Migrate cron expressions in tests**

Use `ide_find_references` to find all `CronSchedule` construction sites. Update Quartz-format expressions:
- `"0 */5 * * * ?"` → `"*/5 * * * *"`
- `"0 0 0 * * ?"` → `"0 0 * * *"`

Update `ScheduleTriggerTest` cron assertions to use 5-field format.

- [ ] **Step 10: SPI javadoc cleanup**

Use `ide_edit_member` on:
- `WorkerExecutionManager.getActiveCaseIds()` — change "Quartz jobs" to "tasks"
- `SchedulerService` class javadoc — remove `@see ScheduledTriggerJob` and `@see ConditionalScheduledTriggerJob`
- `ScheduleTrigger.cron()` javadoc — change "Quartz cron expression" to "5-field cron expression"

Update `CaseDefinition.yaml` schema description: "Quartz cron" → "5-field cron (minute hour day-of-month month day-of-week)".

- [ ] **Step 11: Remove dead methods from `QuartzWorkerExecutionManager`**

Use `ide_refactor_safe_delete` on the 4 unwired methods: `scheduleScheduledTrigger`, `scheduleConditionalTrigger`, `cancelScheduledTrigger`, `cancelAllScheduledTriggers`, and their private helpers (`createTriggerJobKey`, `createScheduledTriggerJob`, `createConditionalScheduledTriggerJob`, `createTriggerJobData`, `createQuartzTrigger`).

- [ ] **Step 12: Build and verify**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common,api,runtime,scheduler-quartz`
Expected: All tests pass. No compilation errors.

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "feat(#813): fix scheduler SPI leaks — JobType enum, 5-field cron, javadoc cleanup

- Replace vestigial jobClass with typed JobType enum on ScheduledJobRequest
- Add 5-field cron validation to CronSchedule (rejects ?, L, W, #, seconds)
- Migrate callers to JobType and 5-field cron
- Remove Quartz terminology from SPI javadoc
- Delete 4 unwired methods from QuartzWorkerExecutionManager

Refs #813"
```

---

### Task 2: Extract `WorkerTaskData` and `WorkerExecutionOrchestrator` to Common

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/internal/executor/WorkerTaskData.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/executor/RetryHandler.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/executor/WorkerExecutionOrchestrator.java`
- Modify: `scheduler-quartz/src/main/java/io/casehub/engine/scheduler/quartz/QuartzWorkerExecutionJob.java`
- Modify: `scheduler-quartz/src/main/java/io/casehub/engine/scheduler/quartz/WorkerRetryContext.java`
- Test: `common/src/test/java/io/casehub/engine/common/internal/executor/WorkerExecutionOrchestratorTest.java`

**Interfaces:**
- Consumes: `WorkerExecutor`, `CaseDefinitionRegistry`, `WorkerContextProvider`, `WorkerExecutionRecoveryService`, `CrossTenantEventLogRepository`, `WorkerExecutionConfig`, `BridgeResolver`, `EventBus` (all existing CDI beans)
- Produces: `WorkerTaskData` record — scheduler-agnostic replacement for `WorkerRetryContext`
- Produces: `RetryHandler` functional interface
- Produces: `WorkerExecutionOrchestrator.execute(WorkerTaskData, RetryHandler)` — all domain logic from `QuartzWorkerExecutionJob`

- [ ] **Step 1: Create `WorkerTaskData` record**

Use `ide_create_file`:
```java
package io.casehub.engine.common.internal.executor;

import java.util.UUID;

public record WorkerTaskData(
    String eventLogId,
    String inputDataHash,
    UUID caseId,
    String workerId,
    String tenancyId,
    String bindingName,
    UUID signalId
) {
    public WorkerTaskData withBindingName(String bindingName) {
        return new WorkerTaskData(eventLogId, inputDataHash, caseId, workerId, tenancyId, bindingName, signalId);
    }

    public WorkerTaskData withSignalId(UUID signalId) {
        return new WorkerTaskData(eventLogId, inputDataHash, caseId, workerId, tenancyId, bindingName, signalId);
    }
}
```

- [ ] **Step 2: Create `RetryHandler` interface**

Use `ide_create_file`:
```java
package io.casehub.engine.common.internal.executor;

@FunctionalInterface
public interface RetryHandler {
    void handleFailure(WorkerTaskData taskData, String errorMessage);
}
```

- [ ] **Step 3: Write `WorkerExecutionOrchestrator` test**

Use `ide_create_file` — unit test with mocks verifying:
- EventLog not found → calls `retryHandler.handleFailure()`
- CaseInstance not found → calls `retryHandler.handleFailure()`
- Worker not found → calls `retryHandler.handleFailure()`
- Successful execution → publishes `WORKER_EXECUTION_FINISHED` on event bus
- Exception during execution → calls `retryHandler.handleFailure()`

Structure follows the existing patterns from `QuartzWorkerExecutionJob` but with `WorkerTaskData` instead of `JobExecutionContext`.

- [ ] **Step 4: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=WorkerExecutionOrchestratorTest -DfailIfNoTests=false`
Expected: FAIL — class does not exist yet.

- [ ] **Step 5: Implement `WorkerExecutionOrchestrator`**

Use `ide_create_file`. Extract the domain logic from `QuartzWorkerExecutionJob.execute()` (lines 84-212):
- `eventLogId` resolution → `findEventLog()`
- CaseInstance recovery via `workerExecutionRecoveryService.loadOrRestoreCaseInstance()`
- CaseDefinition lookup, worker/capability resolution
- Context bridge integration (`bridgeResolver.resolveByTypeName()`, `initialise()`, `deserialise()`)
- Worker execution via `workerExecutor.execute()`
- Output extraction for live-view bridges
- Success publishing via `onSuccess()` → `eventBus.publish(WORKER_EXECUTION_FINISHED)`
- Failure routing via `retryHandler.handleFailure()`

The class is `@ApplicationScoped`, injecting the same CDI beans as `QuartzWorkerExecutionJob`.

- [ ] **Step 6: Run test to verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=WorkerExecutionOrchestratorTest`
Expected: PASS

- [ ] **Step 7: Slim down `QuartzWorkerExecutionJob` to thin adapter**

Use `ide_replace_member` on `QuartzWorkerExecutionJob.execute()`:
```java
@Override
public void execute(JobExecutionContext executionContext) {
    WorkerTaskData taskData = WorkerRetryContext.toTaskData(executionContext);
    orchestrator.execute(taskData, retryService::handleFailure);
}
```

Add `@Inject WorkerExecutionOrchestrator orchestrator` field. Remove all domain logic fields except `retryService`. Remove `onSuccess()`, `onFailure()`, `findEventLog()`, `deserializeExperiences()`, `toMap()` methods.

- [ ] **Step 8: Add `toTaskData()` factory to `WorkerRetryContext`**

Use `ide_insert_member` on `WorkerRetryContext`:
```java
static WorkerTaskData toTaskData(JobExecutionContext context) {
    WorkerRetryContext ctx = from(context);
    return new WorkerTaskData(
        ctx.eventLogId(), ctx.inputDataHash(), ctx.caseId(),
        ctx.workerId(), ctx.tenancyId(), ctx.bindingName(), ctx.signalId());
}
```

- [ ] **Step 9: Build and verify**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common,scheduler-quartz`
Expected: All tests pass.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor(#813): extract WorkerExecutionOrchestrator to common

- WorkerTaskData record replaces scheduler-specific job context types
- RetryHandler functional interface decouples retry from scheduler
- QuartzWorkerExecutionJob becomes thin adapter (~5 lines)
- Domain logic (EventLog resolution, CaseInstance recovery, bridge
  integration, output handling) now scheduler-agnostic

Refs #813"
```

---

### Task 3: Extract `RetryOrchestrator` to Common

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/internal/executor/RescheduleCallback.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/executor/RetryOrchestrator.java`
- Modify: `scheduler-quartz/src/main/java/io/casehub/engine/scheduler/quartz/QuartzRetryService.java`
- Test: `common/src/test/java/io/casehub/engine/common/internal/executor/RetryOrchestratorTest.java`

**Interfaces:**
- Consumes: `EventLogRepository`, `WorkerExecutionRecoveryService`, `CaseDefinitionRegistry`, `EventBus` (existing CDI beans)
- Consumes: `WorkerTaskData` (from Task 2)
- Produces: `RescheduleCallback` functional interface
- Produces: `RetryOrchestrator.handleFailure(WorkerTaskData, String, RescheduleCallback)` — all retry domain logic

- [ ] **Step 1: Create `RescheduleCallback` interface**

Use `ide_create_file`:
```java
package io.casehub.engine.common.internal.executor;

@FunctionalInterface
public interface RescheduleCallback {
    void reschedule(WorkerTaskData taskData, long delayMs);
}
```

- [ ] **Step 2: Write `RetryOrchestrator` test**

Use `ide_create_file`. Unit test with mocks verifying:
- First failure with retryable policy → calls `rescheduleCallback.reschedule()` with correct delay
- Exhausted retries → publishes `WORKER_RETRIES_EXHAUSTED` on event bus, does NOT call reschedule
- Failure EventLog is persisted with correct metadata
- RetryState is built from prior failure EventLogs

- [ ] **Step 3: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=RetryOrchestratorTest -DfailIfNoTests=false`
Expected: FAIL

- [ ] **Step 4: Implement `RetryOrchestrator`**

Use `ide_create_file`. Extract from `QuartzRetryService`:
- `handleFailure()` → persists failure EventLog
- `maybeRescheduleWorker()` → loads case, resolves retry policy, counts failures
- `applyRetryDecision()` → calls `RetryPolicies.evaluate()`, invokes callback or publishes exhaustion
- `resolveRetryPolicy()`, `countFailedAttempts()`, `buildRetryState()`, `buildFailureEventLog()` — all move verbatim

The only thing NOT extracted: `rescheduleWorker()` (builds Quartz `JobDetail`/`Trigger`) — that stays as the Quartz `RescheduleCallback`.

- [ ] **Step 5: Run test to verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=RetryOrchestratorTest`
Expected: PASS

- [ ] **Step 6: Slim down `QuartzRetryService` to thin adapter**

Use `ide_replace_member` on `QuartzRetryService`. It becomes ~30 lines:
- Constructor injects `RetryOrchestrator` and `QuartzWorkerSchedulerService`
- `handleFailure(WorkerRetryContext ctx, String errorMessage)` converts to `WorkerTaskData`, delegates to `retryOrchestrator.handleFailure(taskData, errorMessage, this::rescheduleViaQuartz)`
- `rescheduleViaQuartz(WorkerTaskData taskData, long delayMs)` — the existing `rescheduleWorker()` method building Quartz-specific `JobDetail`/`Trigger`

- [ ] **Step 7: Build and verify**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common,scheduler-quartz`
Expected: All tests pass.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(#813): extract RetryOrchestrator to common

- RescheduleCallback functional interface decouples reschedule from scheduler
- RetryOrchestrator owns failure persistence, policy resolution, retry
  decision — all scheduler-agnostic
- QuartzRetryService becomes thin adapter (~30 lines, Quartz reschedule only)

Refs #813"
```

---

### Task 4: `JobSchedulerContractTest` in Common

**Files:**
- Create: `common/src/test/java/io/casehub/engine/common/spi/scheduler/JobSchedulerContractTest.java`
- Test: runs as part of both `scheduler-quartz` and `scheduler-dbscheduler` test suites

**Interfaces:**
- Consumes: `JobScheduler` SPI, `ScheduledJobRequest`, `JobIdentifier`, `ScheduleStrategy`, `JobType`
- Produces: Abstract contract test that both scheduler modules extend

- [ ] **Step 1: Write `JobSchedulerContractTest`**

Use `ide_create_file`. Follows the existing `WorkerExecutionManagerContractTest` pattern:

```java
package io.casehub.engine.common.spi.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.common.internal.scheduler.*;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

public abstract class JobSchedulerContractTest {

    protected abstract JobScheduler createScheduler();

    @Test
    void schedule_doesNotThrow() {
        JobScheduler scheduler = createScheduler();
        ScheduledJobRequest request = ScheduledJobRequest.builder()
            .jobId(JobIdentifier.of("test-job", "test-group"))
            .schedule(new ScheduleStrategy.DelaySchedule(60000))
            .jobType(JobType.MILESTONE_SLA_TIMEOUT)
            .data(java.util.Map.of("caseId", "test-case"))
            .build();
        scheduler.schedule(request).await().indefinitely();
    }

    @Test
    void cancel_nonExistentReturnsFalse() {
        JobScheduler scheduler = createScheduler();
        Boolean result = scheduler.cancel(JobIdentifier.of("nonexistent", "group"))
            .await().indefinitely();
        assertThat(result).isFalse();
    }

    @Test
    void exists_nonExistentReturnsFalse() {
        JobScheduler scheduler = createScheduler();
        Boolean result = scheduler.exists(JobIdentifier.of("nonexistent", "group"))
            .await().indefinitely();
        assertThat(result).isFalse();
    }

    @Test
    void cancelGroup_emptyGroupReturnsZero() {
        JobScheduler scheduler = createScheduler();
        Integer count = scheduler.cancelGroup("empty-group").await().indefinitely();
        assertThat(count).isEqualTo(0);
    }

    @Test
    void schedule_thenExists() {
        JobScheduler scheduler = createScheduler();
        JobIdentifier jobId = JobIdentifier.of("exists-test", "test-group");
        scheduler.schedule(ScheduledJobRequest.builder()
            .jobId(jobId)
            .schedule(new ScheduleStrategy.DelaySchedule(300000))
            .jobType(JobType.SCHEDULED_TRIGGER_UNCONDITIONAL)
            .data(java.util.Map.of("caseId", "test"))
            .build()).await().indefinitely();
        assertThat(scheduler.exists(jobId).await().indefinitely()).isTrue();
    }

    @Test
    void schedule_thenCancel() {
        JobScheduler scheduler = createScheduler();
        JobIdentifier jobId = JobIdentifier.of("cancel-test", "test-group");
        scheduler.schedule(ScheduledJobRequest.builder()
            .jobId(jobId)
            .schedule(new ScheduleStrategy.DelaySchedule(300000))
            .jobType(JobType.SCHEDULED_TRIGGER_UNCONDITIONAL)
            .data(java.util.Map.of("caseId", "test"))
            .build()).await().indefinitely();
        assertThat(scheduler.cancel(jobId).await().indefinitely()).isTrue();
        assertThat(scheduler.exists(jobId).await().indefinitely()).isFalse();
    }

    @Test
    void cancelGroup_cancelsAllInGroup() {
        JobScheduler scheduler = createScheduler();
        String group = "case-group-test";
        for (int i = 0; i < 3; i++) {
            scheduler.schedule(ScheduledJobRequest.builder()
                .jobId(JobIdentifier.of("job-" + i, group))
                .schedule(new ScheduleStrategy.DelaySchedule(300000))
                .jobType(JobType.SCHEDULED_TRIGGER_UNCONDITIONAL)
                .data(java.util.Map.of("caseId", "test"))
                .build()).await().indefinitely();
        }
        Integer count = scheduler.cancelGroup(group).await().indefinitely();
        assertThat(count).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test-compile -pl common`
Expected: Compiles (abstract class, no tests run directly).

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(#813): add JobSchedulerContractTest in common

Abstract contract test for JobScheduler SPI — both scheduler modules
extend this to verify they honor the same semantics.

Refs #813"
```

---

### Task 5: `scheduler-dbscheduler` Module — Maven Setup and Lifecycle

**Files:**
- Create: `scheduler-dbscheduler/pom.xml`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/DbSchedulerLifecycle.java`
- Create: `scheduler-dbscheduler/src/main/resources/application.properties`
- Create: `scheduler-dbscheduler/src/main/resources/db/dbscheduler/scheduled_tasks.sql`
- Modify: `pom.xml` (root — add `<module>scheduler-dbscheduler</module>`)
- Test: `scheduler-dbscheduler/src/test/java/io/casehub/engine/scheduler/dbscheduler/DbSchedulerLifecycleTest.java`

**Interfaces:**
- Produces: `DbSchedulerLifecycle` — CDI producer for `SchedulerClient`
- Produces: Maven module wired into the build

- [ ] **Step 1: Create module directory and `pom.xml`**

Use `Write` (new file, not existing):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-engine-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-engine-scheduler-dbscheduler</artifactId>
    <name>Case Hub :: Scheduler :: db-scheduler</name>
    <description>db-scheduler-based implementation of JobScheduler SPI</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>com.github.kagkarlsson</groupId>
            <artifactId>db-scheduler</artifactId>
            <version>${version.db-scheduler}</version>
        </dependency>

        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-agroal</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-vertx</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-common</artifactId>
            <version>${project.version}</version>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.smallrye</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>make-index</id>
                        <goals><goal>jandex</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

Add `<property>` for db-scheduler version in root `pom.xml`:
```xml
<version.db-scheduler>16.12.0</version.db-scheduler>
```

Add `<module>scheduler-dbscheduler</module>` after `scheduler-quartz` in root `pom.xml`.

- [ ] **Step 2: Create `scheduled_tasks.sql`**

Use `Write` — the standard db-scheduler table DDL for PostgreSQL:
```sql
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    task_name TEXT NOT NULL,
    task_instance TEXT NOT NULL,
    task_data BYTEA,
    execution_time TIMESTAMP WITH TIME ZONE NOT NULL,
    picked BOOLEAN NOT NULL,
    picked_by TEXT,
    last_success TIMESTAMP WITH TIME ZONE,
    last_failure TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    priority SMALLINT,
    PRIMARY KEY (task_name, task_instance)
);
```

- [ ] **Step 3: Create `DbSchedulerLifecycle`**

Use `ide_create_file`:
```java
package io.casehub.engine.scheduler.dbscheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.Task;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DbSchedulerLifecycle {

    private static final Logger LOG = Logger.getLogger(DbSchedulerLifecycle.class);

    @Inject DataSource dataSource;
    @Inject Instance<Task<?>> tasks;

    @ConfigProperty(name = "casehub.scheduler.threads", defaultValue = "5")
    int threads;

    private Scheduler scheduler;

    void onStart(@Observes StartupEvent ev) {
        List<Task<?>> knownTasks = tasks.stream().toList();
        scheduler = Scheduler.create(dataSource, knownTasks)
            .threads(threads)
            .registerShutdownHook()
            .build();
        scheduler.start();
        LOG.infof("db-scheduler started with %d threads and %d known tasks",
            threads, knownTasks.size());
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (scheduler != null) {
            scheduler.stop();
            LOG.info("db-scheduler stopped");
        }
    }

    @Produces
    @ApplicationScoped
    public SchedulerClient schedulerClient() {
        return scheduler;
    }
}
```

- [ ] **Step 4: Create test `application.properties`**

Use `Write` at `scheduler-dbscheduler/src/test/resources/application.properties`:
```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:scheduler-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
quarkus.datasource.username=sa
quarkus.datasource.password=
```

- [ ] **Step 5: Write lifecycle test**

Test that `DbSchedulerLifecycle` starts and produces a usable `SchedulerClient`. Basic smoke test.

- [ ] **Step 6: Build module**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl scheduler-dbscheduler`
Expected: Compiles and lifecycle test passes.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(#813): add scheduler-dbscheduler module with lifecycle wiring

- New Maven module casehub-engine-scheduler-dbscheduler
- db-scheduler 16.12.0 dependency
- DbSchedulerLifecycle produces SchedulerClient via CDI
- H2 in-memory for tests, PostgreSQL DDL for production
- Builds and tests green

Refs #813"
```

---

### Task 6: `DbSchedulerJobScheduler` — `JobScheduler` Implementation

**Files:**
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/ScheduledJobData.java`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/ScheduledJobHandler.java`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/ScheduledJobDispatchHandler.java`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/DbSchedulerJobScheduler.java`
- Test: `scheduler-dbscheduler/src/test/java/io/casehub/engine/scheduler/dbscheduler/DbSchedulerJobSchedulerContractTest.java`

**Interfaces:**
- Consumes: `JobScheduler` SPI, `ScheduledJobRequest`, `JobIdentifier`, `ScheduleStrategy`, `JobType`
- Consumes: `SchedulerClient` (from Task 5)
- Produces: `DbSchedulerJobScheduler` implementing `JobScheduler`
- Produces: `ScheduledJobDispatchHandler` — single handler for `"scheduled-job"` task dispatching by `JobType`

- [ ] **Step 1: Create `ScheduledJobData` record**

```java
package io.casehub.engine.scheduler.dbscheduler;

import io.casehub.engine.common.internal.scheduler.JobType;
import java.io.Serializable;
import java.util.Map;

public record ScheduledJobData(
    JobType jobType,
    Map<String, Object> data
) implements Serializable {}
```

- [ ] **Step 2: Create `ScheduledJobHandler` interface**

```java
package io.casehub.engine.scheduler.dbscheduler;

public interface ScheduledJobHandler {
    void handle(ScheduledJobData data);
}
```

- [ ] **Step 3: Create `ScheduledJobDispatchHandler`**

```java
package io.casehub.engine.scheduler.dbscheduler;

import com.github.kagkarlsson.scheduler.task.ExecutionContext;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ScheduledJobDispatchHandler {

    @Inject ScheduledTriggerTaskHandler unconditional;
    @Inject ConditionalScheduledTriggerTaskHandler conditional;
    @Inject MilestoneSLATimeoutTaskHandler slaTimeout;

    public void execute(TaskInstance<ScheduledJobData> taskInstance, ExecutionContext ctx) {
        ScheduledJobData data = taskInstance.getData();
        switch (data.jobType()) {
            case SCHEDULED_TRIGGER_UNCONDITIONAL -> unconditional.handle(data);
            case SCHEDULED_TRIGGER_CONDITIONAL -> conditional.handle(data);
            case MILESTONE_SLA_TIMEOUT -> slaTimeout.handle(data);
        }
    }
}
```

- [ ] **Step 4: Write contract test extension**

```java
package io.casehub.engine.scheduler.dbscheduler;

import io.casehub.engine.common.spi.scheduler.JobSchedulerContractTest;
import io.casehub.engine.common.spi.scheduler.JobScheduler;
// ... test wiring with H2 + db-scheduler

class DbSchedulerJobSchedulerContractTest extends JobSchedulerContractTest {
    @Override
    protected JobScheduler createScheduler() {
        // Wire DbSchedulerJobScheduler with H2-backed SchedulerClient
    }
}
```

- [ ] **Step 5: Implement `DbSchedulerJobScheduler`**

Implements `JobScheduler`. Key mappings:
- `schedule()`: creates one-time task `"scheduled-job"` with instance ID `"{group}:{name}"`, `ScheduledJobData` carrying `JobType` + data map. For `CronSchedule` — uses db-scheduler's `RecurringTask` with Spring cron (5-field passthrough). For `DelaySchedule`/`FixedAtSchedule` — uses `OneTimeTask`.
- `cancel()`: `schedulerClient.cancel(TaskInstanceId.of("scheduled-job", "{group}:{name}"))`
- `exists()`: `schedulerClient.getScheduledExecution(...)` presence check
- `cancelGroup()`: filter `getScheduledExecutionsForTask("scheduled-job")` by instance ID prefix

- [ ] **Step 6: Run contract tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl scheduler-dbscheduler -Dtest=DbSchedulerJobSchedulerContractTest`
Expected: PASS — all contract tests green.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(#813): implement DbSchedulerJobScheduler

- ScheduledJobDispatchHandler routes by JobType to handler beans
- Instance ID convention: {group}:{name} for cancel/exists O(1) lookup
- cancelGroup via prefix scan on getScheduledExecutionsForTask
- Contract tests passing against H2 in-memory

Refs #813"
```

---

### Task 7: `DbSchedulerWorkerExecutionManager` — `WorkerExecutionManager` Implementation

**Files:**
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/WorkerExecutionTaskHandler.java`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/DbSchedulerRetryService.java`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/DbSchedulerWorkerExecutionManager.java`
- Test: `scheduler-dbscheduler/src/test/java/io/casehub/engine/scheduler/dbscheduler/DbSchedulerWorkerExecutionManagerTest.java`

**Interfaces:**
- Consumes: `WorkerExecutionManager` SPI, `WorkerExecutionOrchestrator`, `RetryOrchestrator` (from Tasks 2-3)
- Consumes: `SchedulerClient` (from Task 5), `WorkerExecutionKeys` (existing utility)
- Produces: `DbSchedulerWorkerExecutionManager` implementing `WorkerExecutionManager` (`@WorkerBackend`)

- [ ] **Step 1: Create `WorkerExecutionTaskHandler`**

Thin adapter: deserializes `WorkerTaskData` from task data, calls `WorkerExecutionOrchestrator.execute()`.

- [ ] **Step 2: Create `DbSchedulerRetryService`**

Thin adapter implementing `RetryHandler`: delegates to `RetryOrchestrator.handleFailure()` with a `RescheduleCallback` that calls `schedulerClient.schedule()` for a new one-time `"worker-execution"` task.

- [ ] **Step 3: Write tests**

Test `submit()` creates a db-scheduler task with correct instance ID (compound key from `WorkerExecutionKeys`). Test `getActiveWorkCount()` and `getActiveCaseIds()` query db-scheduler's execution state.

- [ ] **Step 4: Implement `DbSchedulerWorkerExecutionManager`**

`@WorkerBackend @ApplicationScoped`. Key methods:
- `submit()`: schedules one-time `"worker-execution"` task with compound key instance ID
- `supports()`: returns `true` (same as Quartz — all capabilities supported)
- `canExecute()`: delegates to `WorkerFunctionHandler` instances (same pattern as Quartz)
- `getActiveWorkCount()` / `getActiveCaseIds()`: query `schedulerClient.getScheduledExecutionsForTask("worker-execution")` filtering by worker name in task data

- [ ] **Step 5: Run tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl scheduler-dbscheduler`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(#813): implement DbSchedulerWorkerExecutionManager

- WorkerExecutionTaskHandler delegates to WorkerExecutionOrchestrator
- DbSchedulerRetryService delegates to RetryOrchestrator with
  db-scheduler reschedule callback
- Compound key instance ID for idempotent worker execution scheduling
- @WorkerBackend annotated for CompositeWorkerExecutionManager discovery

Refs #813"
```

---

### Task 8: Scheduled Job Handlers and Flyway Migration

**Files:**
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/ScheduledTriggerTaskHandler.java`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/ConditionalScheduledTriggerTaskHandler.java`
- Create: `scheduler-dbscheduler/src/main/java/io/casehub/engine/scheduler/dbscheduler/MilestoneSLATimeoutTaskHandler.java`
- Create: `persistence-hibernate/src/main/resources/db/migration/V1.1.0__Create_DbScheduler_Table.sql`
- Test: `scheduler-dbscheduler/src/test/java/io/casehub/engine/scheduler/dbscheduler/ScheduledJobDispatchHandlerTest.java`

**Interfaces:**
- Consumes: `ScheduledJobHandler` (from Task 6), `ScheduledJobData`
- Consumes: `CaseDefinitionRegistry`, `EventBus`, `WorkerExecutionRecoveryService`, `ExpressionEngineRegistry`, `CaseInstanceCache` (existing CDI beans)
- Produces: Three handler beans mirroring Quartz's `ScheduledTriggerJob`, `ConditionalScheduledTriggerJob`, `MilestoneSLATimeoutJob`

- [ ] **Step 1: Implement `ScheduledTriggerTaskHandler`**

Port logic from `ScheduledTriggerJob.execute()` — loads case from cache/repository, verifies RUNNING state, resolves worker/capability, publishes `WorkerScheduleEvent`. No Quartz types — reads from `ScheduledJobData` instead of `JobDataMap`.

- [ ] **Step 2: Implement `ConditionalScheduledTriggerTaskHandler`**

Same as above but evaluates binding `when` condition via `ExpressionEngineRegistry` before publishing.

- [ ] **Step 3: Implement `MilestoneSLATimeoutTaskHandler`**

Port logic from `MilestoneSLATimeoutJob.execute()` — checks milestone is still ACTIVE via EventLog lifecycle status query, publishes `MilestoneSLAViolatedEvent`.

- [ ] **Step 4: Write dispatch handler test**

Test that `ScheduledJobDispatchHandler` routes each `JobType` to the correct handler bean.

- [ ] **Step 5: Create Flyway migration**

Use `Write` at `persistence-hibernate/src/main/resources/db/migration/V1.1.0__Create_DbScheduler_Table.sql`:
```sql
-- db-scheduler scheduled_tasks table
-- Only used when casehub-engine-scheduler-dbscheduler is on the classpath
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    task_name TEXT NOT NULL,
    task_instance TEXT NOT NULL,
    task_data BYTEA,
    execution_time TIMESTAMP WITH TIME ZONE NOT NULL,
    picked BOOLEAN NOT NULL,
    picked_by TEXT,
    last_success TIMESTAMP WITH TIME ZONE,
    last_failure TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    priority SMALLINT,
    PRIMARY KEY (task_name, task_instance)
);
```

- [ ] **Step 6: Run all module tests**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl scheduler-dbscheduler`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(#813): add scheduled job handlers and Flyway migration

- ScheduledTriggerTaskHandler, ConditionalScheduledTriggerTaskHandler,
  MilestoneSLATimeoutTaskHandler — ports of Quartz job logic
- ScheduledJobDispatchHandler routes by JobType
- Flyway V1.1.0 creates db-scheduler table for persistence-hibernate

Refs #813"
```

---

### Task 9: Full Build Verification and CLAUDE.md Update

**Files:**
- Modify: `CLAUDE.md` (add scheduler-dbscheduler module documentation)

- [ ] **Step 1: Full build**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test`
Expected: All modules compile and test green.

- [ ] **Step 2: Verify no Quartz leaks in common/api**

Use `ide_search_text` for `quartz` (case-insensitive) in `common/` and `api/` source directories. Should find zero matches in production code.

- [ ] **Step 3: Update CLAUDE.md**

Add a section documenting the `scheduler-dbscheduler` module, its DataSource configuration, and the H2 in-memory path. Document the `WorkerExecutionOrchestrator` and `RetryOrchestrator` extraction in the Worker Execution Architecture section.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs(#813): update CLAUDE.md with scheduler-dbscheduler documentation

Refs #813"
```
