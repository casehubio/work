# Agent Memory Patterns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #800 — Agent Learning & Memory
**Issue group:** #800

**Goal:** Wire agent-level experience recording from engine worker completion into neocortex memory, with configurable reflection triggering and personality evolution memory.

**Architecture:** Recorder pattern — `AgentExperienceRecorder` in engine-runtime calls neocortex's `ExperienceRecorder` SPI at worker completion time (parallel to `PersonalitySignalRecorder`). Configurable hybrid reflection trigger (importance threshold + completion count ceiling) publishes async events. Personality evolution CBR cases stored on JPAF transitions.

**Tech Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, casehub-neocortex-memory-api, casehub-eidos-api

## Global Constraints

- All code navigation and editing via IntelliJ MCP (`mcp__intellij-index__*` tools)
- Engine depends on neocortex-memory-api (interfaces only), never on neocortex-memory (implementation)
- Engine depends on eidos-api, never on eidos-runtime
- All optional dependencies injected via `Instance<>` — transparent no-op when absent
- Error isolation: memory recording failures never block case progression (MemoryEmitter pattern)
- Tests: `@QuarkusTest` with `casehub-persistence-memory`, surefire naming (`*Test.java`)
- Build before test: `mvn install -DskipTests -q` then `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl <module>`
- Naming bridge: engine uses `tenancyId`, neocortex uses `tenantId` — map at the bridge point
- All commits reference `Refs #800`

---

### Task 1: SPI Extraction — ExperienceRecorder + ReflectionOrchestrator

**Repo:** neocortex (slot: `/Users/mdproctor/claude/casehub/slots/83/neocortex`)

Extract interfaces from existing `ExperienceStream` and `ReflectionService` so engine can depend on memory-api (interfaces) rather than memory (implementations).

**Files:**
- Create: `memory-api/src/main/java/io/casehub/neocortex/memory/experience/ExperienceRecorder.java`
- Create: `memory-api/src/main/java/io/casehub/neocortex/memory/reflection/ReflectionOrchestrator.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/experience/runtime/ExperienceStream.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/reflection/runtime/ReflectionService.java`

**Interfaces:**
- Produces: `ExperienceRecorder.record(ExperienceEvent) → String`
- Produces: `ExperienceRecorder.recordAll(List<ExperienceEvent>) → ExperienceStoreResult`
- Produces: `ReflectionOrchestrator.reflect(String agentId, String tenantId, Instant since, int maxSourceMemories) → List<String>`

- [ ] **Step 1: Create ExperienceRecorder interface**

```java
// memory-api/src/main/java/io/casehub/neocortex/memory/experience/ExperienceRecorder.java
package io.casehub.neocortex.memory.experience;

import java.util.List;

public interface ExperienceRecorder {
    String record(ExperienceEvent event);
    ExperienceStoreResult recordAll(List<ExperienceEvent> events);
}
```

Use `ide_create_file` with project_path `/Users/mdproctor/claude/casehub/slots/83/neocortex/memory-api`.

- [ ] **Step 2: Make ExperienceStream implement ExperienceRecorder**

Use `ide_edit_member` on `ExperienceStream.java` to add `implements ExperienceRecorder` to the class declaration. The methods already match the interface — no body changes needed.

```java
@ApplicationScoped
public class ExperienceStream implements ExperienceRecorder {
```

- [ ] **Step 3: Create ReflectionOrchestrator interface**

```java
// memory-api/src/main/java/io/casehub/neocortex/memory/reflection/ReflectionOrchestrator.java
package io.casehub.neocortex.memory.reflection;

import java.time.Instant;
import java.util.List;

public interface ReflectionOrchestrator {
    List<String> reflect(String agentId, String tenantId, Instant since, int maxSourceMemories);
}
```

- [ ] **Step 4: Make ReflectionService implement ReflectionOrchestrator**

Add `implements ReflectionOrchestrator`. The existing `reflect(agentId, tenantId, since)` method needs a `maxSourceMemories` parameter added. Update the method signature and use `maxSourceMemories` as the query limit:

```java
@ApplicationScoped
public class ReflectionService implements ReflectionOrchestrator {
    // ...
    @Override
    public List<String> reflect(String agentId, String tenantId, Instant since, int maxSourceMemories) {
        var query = ExperienceQuery.forAgent(agentId, tenantId);
        if (since != null) query = query.withSince(since);
        if (maxSourceMemories > 0) query = query.withLimit(maxSourceMemories);
        var sources = store.query(query);
        if (sources.isEmpty()) return List.of();
        // ... rest unchanged
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl memory-api,memory -f /Users/mdproctor/claude/casehub/slots/83/neocortex/pom.xml -q`

- [ ] **Step 6: Run existing tests**

Run: `mvn test -pl memory -f /Users/mdproctor/claude/casehub/slots/83/neocortex/pom.xml -q`
Expected: PASS — no behavior changed, only interface added.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/83/neocortex add memory-api/src/main/java/io/casehub/neocortex/memory/experience/ExperienceRecorder.java memory-api/src/main/java/io/casehub/neocortex/memory/reflection/ReflectionOrchestrator.java memory/src/main/java/io/casehub/neocortex/memory/experience/runtime/ExperienceStream.java memory/src/main/java/io/casehub/neocortex/memory/reflection/runtime/ReflectionService.java
git -C /Users/mdproctor/claude/casehub/slots/83/neocortex commit -m "feat(#800): extract ExperienceRecorder and ReflectionOrchestrator SPIs

ExperienceStream implements ExperienceRecorder, ReflectionService
implements ReflectionOrchestrator. Engine depends on these API
interfaces rather than implementation classes.

Refs casehubio/engine#800"
```

---

### Task 2: ReflectionConfig on CaseDefinition

**Repo:** engine (slot: `/Users/mdproctor/claude/casehub/slots/83/engine`)

Add `ReflectionConfig` record and wire it into `CaseDefinition` builder + YAML mapper.

**Files:**
- Create: `api/src/main/java/io/casehub/api/model/ReflectionConfig.java`
- Modify: `api/src/main/java/io/casehub/api/model/CaseDefinition.java` (add field + builder method)
- Modify: `runtime/src/main/java/io/casehub/engine/internal/definition/CaseDefinitionYamlMapper.java` (parse `reflection:` block)
- Test: `api/src/test/java/io/casehub/api/model/ReflectionConfigTest.java`

**Interfaces:**
- Produces: `ReflectionConfig(double importanceThreshold, int completionCountCeiling, int maxSourceMemories, String synthesizerId)`
- Produces: `CaseDefinition.getReflectionConfig() → ReflectionConfig` (nullable)
- Produces: `CaseDefinition.Builder.reflectionConfig(ReflectionConfig) → Builder`

- [ ] **Step 1: Write test for ReflectionConfig validation**

```java
// api/src/test/java/io/casehub/api/model/ReflectionConfigTest.java
package io.casehub.api.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReflectionConfigTest {

    @Test
    void defaults() {
        var config = new ReflectionConfig(10.0, 20, 100, null);
        assertThat(config.importanceThreshold()).isEqualTo(10.0);
        assertThat(config.completionCountCeiling()).isEqualTo(20);
        assertThat(config.maxSourceMemories()).isEqualTo(100);
        assertThat(config.synthesizerId()).isNull();
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThatThrownBy(() -> new ReflectionConfig(-1.0, 20, 100, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroCeiling() {
        assertThatThrownBy(() -> new ReflectionConfig(10.0, 0, 100, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroMaxSources() {
        assertThatThrownBy(() -> new ReflectionConfig(10.0, 20, 0, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl api -Dtest=ReflectionConfigTest -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: FAIL — `ReflectionConfig` doesn't exist.

- [ ] **Step 3: Implement ReflectionConfig**

```java
// api/src/main/java/io/casehub/api/model/ReflectionConfig.java
package io.casehub.api.model;

public record ReflectionConfig(
    double importanceThreshold,
    int completionCountCeiling,
    int maxSourceMemories,
    String synthesizerId
) {
    public ReflectionConfig {
        if (importanceThreshold < 0)
            throw new IllegalArgumentException("importanceThreshold must be >= 0, got " + importanceThreshold);
        if (completionCountCeiling < 1)
            throw new IllegalArgumentException("completionCountCeiling must be >= 1, got " + completionCountCeiling);
        if (maxSourceMemories < 1)
            throw new IllegalArgumentException("maxSourceMemories must be >= 1, got " + maxSourceMemories);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl api -Dtest=ReflectionConfigTest -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: PASS

- [ ] **Step 5: Add reflectionConfig to CaseDefinition**

Use `ide_insert_member` on `CaseDefinition.java`:
- Add field: `private final ReflectionConfig reflectionConfig;` (nullable)
- Add getter: `public ReflectionConfig getReflectionConfig() { return reflectionConfig; }`
- Add builder method: `public Builder reflectionConfig(ReflectionConfig reflectionConfig) { this.reflectionConfig = reflectionConfig; return this; }`
- Wire field in constructor and `build()` method

Follow the exact pattern of `CbrConfig` on `CaseDefinition` — find it via `ide_find_references` on `CbrConfig` and replicate the field/getter/builder/constructor pattern.

- [ ] **Step 6: Add YAML mapping for `reflection:` block**

In `CaseDefinitionYamlMapper`, add parsing for the `reflection:` node under `spec:`. Follow the `cbr:` parsing pattern:

```java
var reflectionNode = specNode.get("reflection");
if (reflectionNode != null) {
    double threshold = reflectionNode.has("importanceThreshold")
        ? reflectionNode.get("importanceThreshold").asDouble() : 10.0;
    int ceiling = reflectionNode.has("completionCountCeiling")
        ? reflectionNode.get("completionCountCeiling").asInt() : 20;
    int maxSources = reflectionNode.has("maxSourceMemories")
        ? reflectionNode.get("maxSourceMemories").asInt() : 100;
    String synthesizerId = reflectionNode.has("synthesizerId")
        ? reflectionNode.get("synthesizerId").asText() : null;
    builder.reflectionConfig(new ReflectionConfig(threshold, ceiling, maxSources, synthesizerId));
}
```

- [ ] **Step 7: Run compilation and existing tests**

Run: `mvn install -DskipTests -q -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Run: `mvn test -pl api -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml -q`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/83/engine add api/src/main/java/io/casehub/api/model/ReflectionConfig.java api/src/test/java/io/casehub/api/model/ReflectionConfigTest.java
# Also add modified CaseDefinition.java and CaseDefinitionYamlMapper.java
git -C /Users/mdproctor/claude/casehub/slots/83/engine commit -m "feat(#800): add ReflectionConfig to CaseDefinition

New record type configuring hybrid reflection trigger: importance
threshold + completion count ceiling. YAML: reflection: block under
spec:. Nullable on CaseDefinition — absent means no reflection.

Refs #800"
```

---

### Task 3: AgentExperienceRecorder + Handler Wiring

**Repo:** engine

Core bridge — records agent experiences at worker completion and wires into `WorkflowExecutionCompletedHandler`.

**Files:**
- Create: `runtime/src/main/java/io/casehub/engine/internal/memory/AgentExperienceRecorder.java`
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` (add field + call sites)
- Modify: `common/src/main/java/io/casehub/engine/common/internal/event/EventBusAddresses.java` (add REFLECTION_TRIGGER)
- Test: `runtime/src/test/java/io/casehub/engine/internal/memory/AgentExperienceRecorderTest.java`

**Interfaces:**
- Consumes: `ExperienceRecorder.record(ExperienceEvent)` (from Task 1)
- Consumes: `CaseDefinition.getReflectionConfig()` (from Task 2)
- Consumes: `CaseDefinition.agentDescriptorFor(workerName) → Optional<AgentDescriptor>`
- Produces: `AgentExperienceRecorder.record(CaseInstance, String workerName, String capabilityName, WorkerOutcome<?> outcome)`

- [ ] **Step 1: Write test for experience recording on success**

```java
// runtime/src/test/java/io/casehub/engine/internal/memory/AgentExperienceRecorderTest.java
package io.casehub.engine.internal.memory;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ReflectionConfig;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.internal.worker.CaseDefinitionRegistry;
import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.Outcome;
import io.casehub.worker.api.WorkerOutcome;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class AgentExperienceRecorderTest {

    private AtomicReference<ExperienceEvent> captured;
    private AgentExperienceRecorder recorder;
    private CaseDefinitionRegistry registry;

    @BeforeEach
    void setUp() {
        captured = new AtomicReference<>();
        ExperienceRecorder mockRecorder = event -> {
            captured.set(event);
            return "mem-1";
        };
        // Build a CaseDefinition with an agent descriptor for "test-worker"
        // and a ReflectionConfig
        // Setup registry to return this definition
        // Create recorder with Instance<ExperienceRecorder> wrapping mockRecorder
    }

    @Test
    void recordsOutcomeOnSuccess() {
        var instance = buildCaseInstance("tenant-1");
        recorder.record(instance, "test-worker", "analysis", WorkerOutcome.success(Map.of()));

        var event = captured.get();
        assertThat(event).isInstanceOf(Outcome.class);
        var outcome = (Outcome) event;
        assertThat(outcome.agentId()).isEqualTo("test-agent");
        assertThat(outcome.tenantId()).isEqualTo("tenant-1");
        assertThat(outcome.result()).isEqualTo("SUCCESS");
        assertThat(outcome.capability()).isEqualTo("analysis");
        assertThat(outcome.importance()).isEqualTo(0.7);
    }

    @Test
    void recordsOutcomeOnFailure() {
        var instance = buildCaseInstance("tenant-1");
        recorder.record(instance, "test-worker", "analysis", WorkerOutcome.failed("timeout"));

        var outcome = (Outcome) captured.get();
        assertThat(outcome.result()).isEqualTo("FAILED");
        assertThat(outcome.importance()).isEqualTo(0.5);
    }

    @Test
    void noOpWhenRecorderAbsent() {
        // Create recorder with unsatisfied Instance<ExperienceRecorder>
        // Should not throw
        recorder.record(buildCaseInstance("t"), "w", "c", WorkerOutcome.success(Map.of()));
        assertThat(captured.get()).isNull();
    }

    @Test
    void noOpWhenNoAgentDescriptor() {
        // Worker has no agent descriptor in definition
        recorder.record(buildCaseInstance("t"), "unknown-worker", "c", WorkerOutcome.success(Map.of()));
        assertThat(captured.get()).isNull();
    }

    @Test
    void swallowsRecorderException() {
        // ExperienceRecorder that throws RuntimeException
        // Should log but not propagate
        assertThatNoException().isThrownBy(() ->
            recorder.record(buildCaseInstance("t"), "test-worker", "c", WorkerOutcome.success(Map.of())));
    }
}
```

The test setup requires mock `Instance<ExperienceRecorder>`, mock `CaseDefinitionRegistry`, and a `CaseDefinition` with an agent descriptor. Use anonymous inner classes or Mockito — follow the `PersonalitySignalRecorder` test pattern in the codebase.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl runtime -Dtest=AgentExperienceRecorderTest -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement AgentExperienceRecorder**

```java
// runtime/src/main/java/io/casehub/engine/internal/memory/AgentExperienceRecorder.java
package io.casehub.engine.internal.memory;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ReflectionConfig;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.internal.worker.CaseDefinitionRegistry;
import io.casehub.neocortex.memory.experience.*;
import io.casehub.worker.api.WorkerOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class AgentExperienceRecorder {

    private static final Logger LOG = Logger.getLogger(AgentExperienceRecorder.class);

    private static final Map<String, Double> IMPORTANCE_DEFAULTS = Map.of(
        "SUCCESS", 0.7, "COMPLETED", 0.7,
        "FAILED", 0.5, "DECLINED", 0.3, "EXPIRED", 0.2
    );

    private final Instance<ExperienceRecorder> experienceRecorder;
    private final CaseDefinitionRegistry registry;

    @Inject
    public AgentExperienceRecorder(Instance<ExperienceRecorder> experienceRecorder,
                                    CaseDefinitionRegistry registry) {
        this.experienceRecorder = experienceRecorder;
        this.registry = registry;
    }

    public void record(CaseInstance caseInstance, String workerName,
                       String capabilityName, WorkerOutcome<?> outcome) {
        if (experienceRecorder.isUnsatisfied()) return;

        var definition = registry.findByIdentity(
            caseInstance.getNamespace(), caseInstance.getName(), caseInstance.getVersion());
        if (definition.isEmpty()) return;

        var descriptor = definition.get().agentDescriptorFor(workerName);
        if (descriptor.isEmpty()) return;

        var agentId = descriptor.get().agentId();
        var resultStr = outcomeToResult(outcome);
        var importance = IMPORTANCE_DEFAULTS.getOrDefault(resultStr, 0.5);

        var metadata = new HashMap<String, String>();
        metadata.put(ExperienceAttributeKeys.EVENT_TYPE, "worker-completion");
        metadata.put(ExperienceAttributeKeys.RESULT, resultStr);
        if (capabilityName != null) {
            metadata.put(ExperienceAttributeKeys.CAPABILITY, capabilityName);
        }

        var event = new Outcome(
            agentId,
            caseInstance.getTenancyId(),
            caseInstance.getId().toString(),
            null,
            "Worker " + workerName + " " + resultStr + " on capability " + capabilityName,
            importance,
            metadata,
            resultStr,
            capabilityName
        );

        try {
            experienceRecorder.get().record(event);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to record experience for agent %s — continuing", agentId);
        }
    }

    private static String outcomeToResult(WorkerOutcome<?> outcome) {
        return switch (outcome) {
            case WorkerOutcome.Success<?> s -> "SUCCESS";
            case WorkerOutcome.Completed<?> c -> "COMPLETED";
            case WorkerOutcome.Declined<?> d -> "DECLINED";
            case WorkerOutcome.Failed<?> f -> "FAILED";
            case WorkerOutcome.Expired<?> e -> "EXPIRED";
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl runtime -Dtest=AgentExperienceRecorderTest -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: PASS

- [ ] **Step 5: Add EventBusAddresses.REFLECTION_TRIGGER**

Use `ide_insert_member` on `EventBusAddresses.java` in engine-common:

```java
public static final String REFLECTION_TRIGGER = "casehub.reflection.trigger";
```

- [ ] **Step 6: Wire into WorkflowExecutionCompletedHandler**

Add `AgentExperienceRecorder` as a field (following the `PersonalitySignalRecorder` pattern at line 92):

```java
field agentExperienceRecorder AgentExperienceRecorder
```

Call `agentExperienceRecorder.record()` at:
1. **Success path** (in `onWorkflowExecutionCompletedHandler`, after `personalitySignalRecorder.record()` at ~line 166)
2. **Failure path** (in `handleSemanticFailure`, after `personalitySignalRecorder.record()` at ~line 362)

```java
agentExperienceRecorder.record(caseInstance, event.workerName(),
    extractCapabilityTag(caseInstance, worker, event.bindingName()), outcome);
```

- [ ] **Step 7: Run compilation**

Run: `mvn compile -pl runtime -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml -q`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/83/engine add runtime/src/main/java/io/casehub/engine/internal/memory/AgentExperienceRecorder.java runtime/src/test/java/io/casehub/engine/internal/memory/AgentExperienceRecorderTest.java
# Also add modified WorkflowExecutionCompletedHandler.java and EventBusAddresses.java
git -C /Users/mdproctor/claude/casehub/slots/83/engine commit -m "feat(#800): AgentExperienceRecorder bridge — engine→neocortex

Records agent experiences at worker completion via ExperienceRecorder
SPI. Wired into WorkflowExecutionCompletedHandler at both success and
failure call sites. Error isolation: logs and swallows non-security
exceptions (MemoryEmitter pattern).

Refs #800"
```

---

### Task 4: Reflection Trigger — Threshold Tracking + Async Handler

**Repo:** engine

Configurable hybrid trigger that accumulates importance scores and completion counts per agent, firing `ReflectionTriggerEvent` when either threshold is exceeded.

**Files:**
- Modify: `runtime/src/main/java/io/casehub/engine/internal/memory/AgentExperienceRecorder.java` (add threshold tracking)
- Create: `runtime/src/main/java/io/casehub/engine/internal/memory/ReflectionTriggerEvent.java`
- Create: `runtime/src/main/java/io/casehub/engine/internal/memory/ReflectionTriggerHandler.java`
- Test: `runtime/src/test/java/io/casehub/engine/internal/memory/ReflectionTriggerTest.java`

**Interfaces:**
- Consumes: `ReflectionConfig` (from Task 2)
- Consumes: `ReflectionOrchestrator.reflect(agentId, tenantId, since, maxSourceMemories)` (from Task 1)
- Produces: `ReflectionTriggerEvent(String agentId, String tenancyId, Instant since, ReflectionConfig config)`

- [ ] **Step 1: Write test for threshold trigger**

```java
// runtime/src/test/java/io/casehub/engine/internal/memory/ReflectionTriggerTest.java
package io.casehub.engine.internal.memory;

import io.casehub.api.model.ReflectionConfig;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReflectionTriggerTest {

    @Test
    void triggersWhenImportanceThresholdExceeded() {
        // Create AgentExperienceRecorder with a mock event bus
        // Record experiences with importance 0.7 each
        // With threshold 2.0, should trigger after 3 recordings (3 × 0.7 = 2.1 > 2.0)
        // Verify ReflectionTriggerEvent published
    }

    @Test
    void triggersWhenCompletionCountCeilingReached() {
        // With ceiling 3, should trigger after 3 recordings regardless of importance
        // Use importance 0.01 (well below threshold) to ensure count triggers first
    }

    @Test
    void doesNotTriggerWithoutReflectionConfig() {
        // CaseDefinition has no reflectionConfig
        // Should record experience but never publish trigger event
    }

    @Test
    void countersResetOnSuccessfulReflection() {
        // Trigger, then simulate successful reflection callback
        // Next experience should start counting from zero
    }

    @Test
    void countersPreservedOnFailedReflection() {
        // Trigger, then simulate failed reflection callback
        // Counter should retain accumulated value — next experience may re-trigger
    }

    @Test
    void concurrentReflectionsSuppressed() {
        // Trigger reflection, then record another experience before reflection completes
        // Second recording should accumulate but NOT trigger a second reflection
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl runtime -Dtest=ReflectionTriggerTest -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: FAIL

- [ ] **Step 3: Create ReflectionTriggerEvent**

```java
// runtime/src/main/java/io/casehub/engine/internal/memory/ReflectionTriggerEvent.java
package io.casehub.engine.internal.memory;

import io.casehub.api.model.ReflectionConfig;
import java.time.Instant;

public record ReflectionTriggerEvent(
    String agentId,
    String tenancyId,
    Instant since,
    ReflectionConfig config
) {}
```

- [ ] **Step 4: Add threshold tracking to AgentExperienceRecorder**

Add to `AgentExperienceRecorder`:

```java
private final ConcurrentHashMap<String, TriggerState> triggerStates = new ConcurrentHashMap<>();
private final EventBus eventBus;

private record TriggerState(double accumulatedImportance, int completionCount,
                             boolean reflecting, Instant lastReflectionTime) {}

// After recording experience, check trigger:
private void checkTrigger(String agentId, String tenancyId, double importance,
                          ReflectionConfig config) {
    if (config == null) return;

    var key = agentId + ":" + tenancyId;
    var shouldTrigger = new boolean[]{false};
    var since = new Instant[]{null};

    triggerStates.compute(key, (k, state) -> {
        if (state == null) state = new TriggerState(0, 0, false, null);
        if (state.reflecting) {
            return new TriggerState(state.accumulatedImportance + importance,
                state.completionCount + 1, true, state.lastReflectionTime);
        }

        double newImportance = state.accumulatedImportance + importance;
        int newCount = state.completionCount + 1;

        if (newImportance >= config.importanceThreshold()
                || newCount >= config.completionCountCeiling()) {
            shouldTrigger[0] = true;
            since[0] = state.lastReflectionTime;
            return new TriggerState(newImportance, newCount, true, state.lastReflectionTime);
        }
        return new TriggerState(newImportance, newCount, false, state.lastReflectionTime);
    });

    if (shouldTrigger[0]) {
        eventBus.publish(EventBusAddresses.REFLECTION_TRIGGER,
            new ReflectionTriggerEvent(agentId, tenancyId, since[0], config));
    }
}

public void onReflectionComplete(String agentId, String tenancyId, boolean success) {
    var key = agentId + ":" + tenancyId;
    triggerStates.compute(key, (k, state) -> {
        if (state == null) return null;
        if (success) {
            return new TriggerState(0, 0, false, Instant.now());
        } else {
            return new TriggerState(state.accumulatedImportance, state.completionCount,
                false, state.lastReflectionTime);
        }
    });
}
```

- [ ] **Step 5: Create ReflectionTriggerHandler**

```java
// runtime/src/main/java/io/casehub/engine/internal/memory/ReflectionTriggerHandler.java
package io.casehub.engine.internal.memory;

import io.casehub.engine.common.internal.event.EventBusAddresses;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import io.quarkus.vertx.ConsumeEvent;

@ApplicationScoped
public class ReflectionTriggerHandler {

    private static final Logger LOG = Logger.getLogger(ReflectionTriggerHandler.class);

    private final Instance<ReflectionOrchestrator> orchestrator;
    private final AgentExperienceRecorder experienceRecorder;

    @Inject
    public ReflectionTriggerHandler(Instance<ReflectionOrchestrator> orchestrator,
                                     AgentExperienceRecorder experienceRecorder) {
        this.orchestrator = orchestrator;
        this.experienceRecorder = experienceRecorder;
    }

    @ConsumeEvent(EventBusAddresses.REFLECTION_TRIGGER)
    @RunOnVirtualThread
    public void onTrigger(ReflectionTriggerEvent event) {
        if (orchestrator.isUnsatisfied()) {
            experienceRecorder.onReflectionComplete(event.agentId(), event.tenancyId(), false);
            return;
        }
        try {
            var memoryIds = orchestrator.get().reflect(
                event.agentId(), event.tenancyId(), event.since(),
                event.config().maxSourceMemories());
            boolean success = memoryIds != null && !memoryIds.isEmpty();
            experienceRecorder.onReflectionComplete(event.agentId(), event.tenancyId(), success);
            if (success) {
                LOG.infof("Reflection for agent %s produced %d insights", event.agentId(), memoryIds.size());
            }
        } catch (Exception e) {
            LOG.warnf(e, "Reflection failed for agent %s — counters preserved for re-trigger", event.agentId());
            experienceRecorder.onReflectionComplete(event.agentId(), event.tenancyId(), false);
        }
    }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn test -pl runtime -Dtest=ReflectionTriggerTest -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/83/engine add runtime/src/main/java/io/casehub/engine/internal/memory/ReflectionTriggerEvent.java runtime/src/main/java/io/casehub/engine/internal/memory/ReflectionTriggerHandler.java runtime/src/test/java/io/casehub/engine/internal/memory/ReflectionTriggerTest.java
# Also add modified AgentExperienceRecorder.java
git -C /Users/mdproctor/claude/casehub/slots/83/engine commit -m "feat(#800): configurable hybrid reflection trigger

Importance threshold + completion count ceiling per agent. Counters
reset on success, preserved on failure for re-trigger. Concurrent
reflections suppressed via atomic reflecting flag.

Refs #800"
```

---

### Task 5: Personality Transition CBR Case Producer

**Repo:** engine

Store `PersonalityTransitionSchema` CBR cases when JPAF personality evolution occurs. The `PersonalitySignalRecorder.checkReflection()` method already detects evolution — extend it to store a CBR case.

**Files:**
- Modify: `runtime/src/main/java/io/casehub/engine/internal/routing/PersonalitySignalRecorder.java` (add CBR case storage on evolution)
- Test: `runtime/src/test/java/io/casehub/engine/internal/routing/PersonalityTransitionMemoryTest.java`

**Interfaces:**
- Consumes: `CbrCaseMemoryStore.store()` (existing)
- Consumes: `PersonalityTransitionSchema.schema()` (existing in neocortex-memory-api)
- Consumes: `DispositionEvolution.evaluate() → Evolved` (existing in eidos-api)

- [ ] **Step 1: Write test for CBR case stored on evolution**

```java
// runtime/src/test/java/io/casehub/engine/internal/routing/PersonalityTransitionMemoryTest.java
package io.casehub.engine.internal.routing;

import io.casehub.neocortex.memory.cbr.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PersonalityTransitionMemoryTest {

    @Test
    void storesTransitionCbrCaseOnEvolution() {
        // Setup PersonalitySignalRecorder with mock CbrCaseMemoryStore
        // Trigger evolution (simulate EvolutionPending → Evolved)
        // Verify CbrCaseMemoryStore.store() called with PersonalityTransitionSchema.CASE_TYPE
        // Verify features: agent_id, old_dominant, new_dominant, trigger_type
    }

    @Test
    void doesNotStoreCbrCaseOnDampened() {
        // Trigger dampening (EvolutionPending → Dampened)
        // Verify CbrCaseMemoryStore.store() NOT called
    }

    @Test
    void noOpWhenCbrStoreAbsent() {
        // CbrCaseMemoryStore not on classpath
        // Evolution should still proceed — CBR storage is best-effort
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — PersonalitySignalRecorder doesn't store CBR cases yet.

- [ ] **Step 3: Extend PersonalitySignalRecorder.checkReflection()**

Add `Instance<CbrCaseMemoryStore>` field. In `checkReflection()`, after `Evolved` result:

```java
if (!cbrStore.isUnsatisfied()) {
    try {
        var features = Map.of(
            "agent_id", FeatureValue.string(agentId),
            "old_dominant", FeatureValue.string(pending.candidateFunction()),
            "new_dominant", FeatureValue.string(evolved.newTypeLabel().split("-")[0]),
            "old_auxiliary", FeatureValue.string(/* extract from descriptor */),
            "new_auxiliary", FeatureValue.string(evolved.newTypeLabel().split("-")[1]),
            "trigger_type", FeatureValue.string(pending.type().toString()),
            "outcome", FeatureValue.string("unknown")
        );
        var cbrCase = new FeatureVectorCbrCase(
            "Agent " + agentId + " personality shift: " + evolved.previousTypeLabel() + " → " + evolved.newTypeLabel(),
            "Routing adapted to new profile",
            null, null, null, agentId, features
        );
        cbrStore.get().store(cbrCase, PersonalityTransitionSchema.CASE_TYPE,
            agentId, new MemoryDomain("personality"), tenancyId, null, null);
    } catch (Exception e) {
        LOG.warnf(e, "Failed to store personality transition CBR case for agent %s", agentId);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl runtime -Dtest=PersonalityTransitionMemoryTest -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: PASS

- [ ] **Step 5: Register the schema at startup**

Add a `@Observes StartupEvent` method (or add to existing startup observer) that calls `cbrStore.get().registerSchema(PersonalityTransitionSchema.schema())` — required before first store call. Guard with `cbrStore.isUnsatisfied()` check.

- [ ] **Step 6: Run full test suite**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl runtime -f /Users/mdproctor/claude/casehub/slots/83/engine/pom.xml`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/83/engine commit -m "feat(#800): store personality transition CBR cases on JPAF evolution

PersonalitySignalRecorder stores FeatureVectorCbrCase with
PersonalityTransitionSchema when disposition evolution produces
Evolved result. Best-effort — CBR store absence or failure doesn't
block personality evolution.

Refs #800"
```

---

## Neocortex Dependency Addition

Before Task 3 compiles, engine-runtime needs `casehub-neocortex-memory-api` as a compile dependency. Check if it's already present (engine already uses `CbrCaseMemoryStore` from neocortex-memory-api). If not, add to `runtime/pom.xml`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-neocortex-memory-api</artifactId>
</dependency>
```

The version is managed by the parent POM's `version.io.casehub.neocortex` property.

## Post-Implementation Verification

After all tasks complete:

1. `mvn install -DskipTests -q` — full build passes
2. `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl api,runtime` — all tests pass
3. `ide_diagnostics` on engine workspace — no compilation errors
4. Update `CLAUDE.md` with new infrastructure documentation
