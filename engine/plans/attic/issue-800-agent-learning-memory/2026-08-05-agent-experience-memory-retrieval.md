# Agent Experience Recording & Memory-Informed Dispatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #801 — Reflection orchestration — periodic trigger for memory synthesis
**Issue group:** #801, #804

**Goal:** Wire neocortex ExperienceRecorder and ReflectionOrchestrator SPIs into the engine's worker completion and dispatch paths, enabling agents to accumulate experiences, trigger periodic reflection, and receive relevant memories at dispatch time.

**Architecture:** Two new engine-runtime beans (`AgentExperienceRecorder`, `AgentMemoryRetriever`) following the established `PersonalitySignalRecorder`/`GoalFailureRecorder` pattern. Configuration via two new records on `CaseDefinition` (`ReflectionTriggerConfig`, `MemoryRetrievalConfig`). Retrieved memories flow through `WorkerContext.memories` to workers at execution time.

**Tech Stack:** Java 21, Quarkus 3.32.2, CDI (`@ApplicationScoped`, `Instance<>`), `ConcurrentHashMap.compute()` for atomic state, virtual threads for async reflection.

## Global Constraints

- All new types in `engine-api` (`api/`) — consumer-visible records
- All new beans in `engine-runtime` (`runtime/`) — internal implementation
- Neocortex dependency: `casehub-neocortex-memory-api` (compile scope, already exists in runtime/pom.xml)
- `Instance<>.isResolvable()` guard on all neocortex SPI injections — transparent no-op when neocortex absent
- All exceptions caught and logged — never block case progression
- Pre-release: breaking changes to `WorkerContext` record are acceptable
- IntelliJ MCP required for all code operations — use `ide_insert_member`, `ide_replace_member`, `ide_edit_member` for Java files
- Workspace: `/Users/mdproctor/claude/casehub/slots/83/engine` (project_path for IntelliJ)

---

### Task 1: Config records and RetrievedMemory type (engine-api)

**Files:**
- Create: `api/src/main/java/io/casehub/api/model/ReflectionTriggerConfig.java`
- Create: `api/src/main/java/io/casehub/api/model/MemoryRetrievalConfig.java`
- Create: `api/src/main/java/io/casehub/api/model/RetrievedMemory.java`
- Test: `api/src/test/java/io/casehub/api/model/ReflectionTriggerConfigTest.java`
- Test: `api/src/test/java/io/casehub/api/model/MemoryRetrievalConfigTest.java`
- Test: `api/src/test/java/io/casehub/api/model/RetrievedMemoryTest.java`

**Interfaces:**
- Consumes: nothing (leaf types)
- Produces:
  - `ReflectionTriggerConfig(boolean enabled, double importanceThreshold, int maxUnreflectedOutcomes, int maxSourceMemories, Map<String, Double> importanceWeights)` — record with validation
  - `ReflectionTriggerConfig.defaults()` — static factory
  - `ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS` — `Map.of("SUCCESS", 0.3, "COMPLETED", 0.3, "DECLINED", 0.6, "FAILED", 0.8, "EXPIRED", 0.5)`
  - `MemoryRetrievalConfig(boolean enabled, int maxMemories, Set<String> domains)` — record with validation
  - `MemoryRetrievalConfig.defaults()` — static factory
  - `RetrievedMemory(String memoryId, String text, String domain, Instant createdAt, Map<String, String> attributes)` — record with null validation

- [ ] **Step 1: Write ReflectionTriggerConfig tests**

```java
package io.casehub.api.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReflectionTriggerConfigTest {

    @Test
    void defaults_returnsExpectedValues() {
        var config = ReflectionTriggerConfig.defaults();
        assertFalse(config.enabled());
        assertEquals(3.0, config.importanceThreshold());
        assertEquals(10, config.maxUnreflectedOutcomes());
        assertEquals(50, config.maxSourceMemories());
        assertEquals(0.3, config.importanceWeights().get("SUCCESS"));
        assertEquals(0.3, config.importanceWeights().get("COMPLETED"));
        assertEquals(0.6, config.importanceWeights().get("DECLINED"));
        assertEquals(0.8, config.importanceWeights().get("FAILED"));
        assertEquals(0.5, config.importanceWeights().get("EXPIRED"));
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReflectionTriggerConfig(true, -1.0, 10, 50, Map.of()));
    }

    @Test
    void rejectsThresholdAboveTen() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReflectionTriggerConfig(true, 11.0, 10, 50, Map.of()));
    }

    @Test
    void rejectsZeroMaxUnreflectedOutcomes() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReflectionTriggerConfig(true, 3.0, 0, 50, Map.of()));
    }

    @Test
    void rejectsZeroMaxSourceMemories() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReflectionTriggerConfig(true, 3.0, 10, 0, Map.of()));
    }

    @Test
    void importanceWeightsAreImmutable() {
        var weights = new java.util.HashMap<>(Map.of("SUCCESS", 0.5));
        var config = new ReflectionTriggerConfig(true, 3.0, 10, 50, weights);
        assertThrows(UnsupportedOperationException.class,
            () -> config.importanceWeights().put("NEW", 0.1));
    }

    @Test
    void nullImportanceWeightsDefaultsToEmpty() {
        var config = new ReflectionTriggerConfig(true, 3.0, 10, 50, null);
        assertTrue(config.importanceWeights().isEmpty());
    }
}
```

- [ ] **Step 2: Write MemoryRetrievalConfig tests**

```java
package io.casehub.api.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryRetrievalConfigTest {

    @Test
    void defaults_returnsExpectedValues() {
        var config = MemoryRetrievalConfig.defaults();
        assertFalse(config.enabled());
        assertEquals(10, config.maxMemories());
        assertEquals(Set.of("experience", "reflection"), config.domains());
    }

    @Test
    void rejectsZeroMaxMemories() {
        assertThrows(IllegalArgumentException.class,
            () -> new MemoryRetrievalConfig(true, 0, Set.of()));
    }

    @Test
    void domainsAreImmutable() {
        var domains = new java.util.HashSet<>(Set.of("experience"));
        var config = new MemoryRetrievalConfig(true, 10, domains);
        assertThrows(UnsupportedOperationException.class,
            () -> config.domains().add("new"));
    }

    @Test
    void nullDomainsDefaultsToEmpty() {
        var config = new MemoryRetrievalConfig(true, 10, null);
        assertTrue(config.domains().isEmpty());
    }
}
```

- [ ] **Step 3: Write RetrievedMemory tests**

```java
package io.casehub.api.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievedMemoryTest {

    @Test
    void constructsWithValidFields() {
        var mem = new RetrievedMemory("id-1", "text", "experience",
            Instant.now(), Map.of("key", "val"));
        assertEquals("id-1", mem.memoryId());
        assertEquals("text", mem.text());
        assertEquals("experience", mem.domain());
        assertEquals("val", mem.attributes().get("key"));
    }

    @Test
    void rejectsNullMemoryId() {
        assertThrows(NullPointerException.class,
            () -> new RetrievedMemory(null, "text", "domain", Instant.now(), Map.of()));
    }

    @Test
    void rejectsNullText() {
        assertThrows(NullPointerException.class,
            () -> new RetrievedMemory("id", null, "domain", Instant.now(), Map.of()));
    }

    @Test
    void rejectsNullDomain() {
        assertThrows(NullPointerException.class,
            () -> new RetrievedMemory("id", "text", null, Instant.now(), Map.of()));
    }

    @Test
    void nullAttributesDefaultsToEmpty() {
        var mem = new RetrievedMemory("id", "text", "domain", Instant.now(), null);
        assertTrue(mem.attributes().isEmpty());
    }

    @Test
    void attributesAreImmutable() {
        var attrs = new java.util.HashMap<>(Map.of("k", "v"));
        var mem = new RetrievedMemory("id", "text", "domain", Instant.now(), attrs);
        assertThrows(UnsupportedOperationException.class,
            () -> mem.attributes().put("new", "val"));
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn test -pl api -Dtest="ReflectionTriggerConfigTest,MemoryRetrievalConfigTest,RetrievedMemoryTest" -DfailIfNoTests=false -q`
Expected: compilation failure (classes don't exist yet)

- [ ] **Step 5: Implement ReflectionTriggerConfig**

Create `api/src/main/java/io/casehub/api/model/ReflectionTriggerConfig.java`:

```java
package io.casehub.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReflectionTriggerConfig(
    boolean enabled,
    double importanceThreshold,
    int maxUnreflectedOutcomes,
    int maxSourceMemories,
    Map<String, Double> importanceWeights
) {
    public static final Map<String, Double> DEFAULT_IMPORTANCE_WEIGHTS = Map.of(
        "SUCCESS", 0.3, "COMPLETED", 0.3, "DECLINED", 0.6, "FAILED", 0.8, "EXPIRED", 0.5);

    public ReflectionTriggerConfig {
        if (importanceThreshold < 0.0 || importanceThreshold > 10.0)
            throw new IllegalArgumentException("importanceThreshold must be in [0, 10]");
        if (maxUnreflectedOutcomes < 1)
            throw new IllegalArgumentException("maxUnreflectedOutcomes must be >= 1");
        if (maxSourceMemories < 1)
            throw new IllegalArgumentException("maxSourceMemories must be >= 1");
        importanceWeights = importanceWeights == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(importanceWeights));
    }

    public static ReflectionTriggerConfig defaults() {
        return new ReflectionTriggerConfig(false, 3.0, 10, 50, DEFAULT_IMPORTANCE_WEIGHTS);
    }
}
```

- [ ] **Step 6: Implement MemoryRetrievalConfig**

Create `api/src/main/java/io/casehub/api/model/MemoryRetrievalConfig.java`:

```java
package io.casehub.api.model;

import java.util.Set;

public record MemoryRetrievalConfig(
    boolean enabled,
    int maxMemories,
    Set<String> domains
) {
    public MemoryRetrievalConfig {
        if (maxMemories < 1)
            throw new IllegalArgumentException("maxMemories must be >= 1");
        domains = domains == null ? Set.of() : Set.copyOf(domains);
    }

    public static MemoryRetrievalConfig defaults() {
        return new MemoryRetrievalConfig(false, 10, Set.of("experience", "reflection"));
    }
}
```

- [ ] **Step 7: Implement RetrievedMemory**

Create `api/src/main/java/io/casehub/api/model/RetrievedMemory.java`:

```java
package io.casehub.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RetrievedMemory(
    String memoryId,
    String text,
    String domain,
    Instant createdAt,
    Map<String, String> attributes
) {
    public RetrievedMemory {
        Objects.requireNonNull(memoryId, "memoryId required");
        Objects.requireNonNull(text, "text required");
        Objects.requireNonNull(domain, "domain required");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn test -pl api -Dtest="ReflectionTriggerConfigTest,MemoryRetrievalConfigTest,RetrievedMemoryTest" -q`
Expected: all pass

- [ ] **Step 9: Commit**

```bash
git add api/src/main/java/io/casehub/api/model/ReflectionTriggerConfig.java \
       api/src/main/java/io/casehub/api/model/MemoryRetrievalConfig.java \
       api/src/main/java/io/casehub/api/model/RetrievedMemory.java \
       api/src/test/java/io/casehub/api/model/ReflectionTriggerConfigTest.java \
       api/src/test/java/io/casehub/api/model/MemoryRetrievalConfigTest.java \
       api/src/test/java/io/casehub/api/model/RetrievedMemoryTest.java
git commit -m "feat(#801): ReflectionTriggerConfig, MemoryRetrievalConfig, RetrievedMemory records

Refs #801, Refs #804"
```

---

### Task 2: WorkerContext.memories field (engine-api)

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/WorkerContext.java`
- Modify: `api/src/test/java/io/casehub/api/model/WorkerContextTest.java` (or create if not exists)

**Interfaces:**
- Consumes: `RetrievedMemory` from Task 1
- Produces: `WorkerContext.memories()` — `List<RetrievedMemory>`, immutable, defaults to `List.of()`

- [ ] **Step 1: Write tests for the new memories field**

Add to `WorkerContextTest.java` (create if needed):

```java
@Test
void eightArgConstructor_memoriesImmutable() {
    var mem = new RetrievedMemory("id", "text", "exp", Instant.now(), Map.of());
    var ctx = new WorkerContext("desc", UUID.randomUUID(), List.of(), List.of(),
        null, Map.of(), List.of(), List.of(mem));
    assertEquals(1, ctx.memories().size());
    assertThrows(UnsupportedOperationException.class,
        () -> ctx.memories().add(mem));
}

@Test
void sevenArgConstructor_memoriesEmpty() {
    var ctx = new WorkerContext("desc", UUID.randomUUID(), List.of(), List.of(),
        null, Map.of(), List.of());
    assertTrue(ctx.memories().isEmpty());
}

@Test
void nullMemories_defaultsToEmptyList() {
    var ctx = new WorkerContext("desc", UUID.randomUUID(), List.of(), List.of(),
        null, Map.of(), List.of(), null);
    assertNotNull(ctx.memories());
    assertTrue(ctx.memories().isEmpty());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl api -Dtest="WorkerContextTest" -q`
Expected: compilation failure (8-arg constructor doesn't exist)

- [ ] **Step 3: Add memories field to WorkerContext**

Use `ide_replace_member` to update the record. Add the `memories` field as the 8th component. Update compact constructor to handle null memories. Add backward-compatible 7-arg constructor that passes `List.of()` for memories.

The record becomes:
```java
public record WorkerContext(
    String taskDescription,
    UUID caseId,
    List<CaseChannel> channels,
    List<WorkerSummary> priorWorkers,
    PropagationContext propagationContext,
    Map<String, Object> properties,
    List<RetrievedExperience> experiences,
    List<RetrievedMemory> memories) {

  public WorkerContext {
    channels = channels == null ? List.of() : List.copyOf(channels);
    priorWorkers = priorWorkers == null ? List.of() : List.copyOf(priorWorkers);
    properties = properties == null ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    experiences = experiences == null ? List.of() : List.copyOf(experiences);
    memories = memories == null ? List.of() : List.copyOf(memories);
  }

  public WorkerContext(
      String taskDescription, UUID caseId, List<CaseChannel> channels,
      List<WorkerSummary> priorWorkers, PropagationContext propagationContext,
      Map<String, Object> properties, List<RetrievedExperience> experiences) {
    this(taskDescription, caseId, channels, priorWorkers,
         propagationContext, properties, experiences, List.of());
  }

  public WorkerContext(
      String taskDescription, UUID caseId, List<CaseChannel> channels,
      List<WorkerSummary> priorWorkers, PropagationContext propagationContext,
      Map<String, Object> properties) {
    this(taskDescription, caseId, channels, priorWorkers,
         propagationContext, properties, List.of());
  }
}
```

- [ ] **Step 4: Fix compilation errors from callers**

Use `ide_find_references` on the `WorkerContext` constructors to find all call sites. Update any that construct `WorkerContext` with positional args to use the correct overload. Most callers use the 6-arg or 7-arg constructor and will not break.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl api -Dtest="WorkerContextTest" -q`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java/io/casehub/api/model/WorkerContext.java \
       api/src/test/java/io/casehub/api/model/WorkerContextTest.java
git commit -m "feat(#804): add memories field to WorkerContext

Backward-compatible 7-arg and 6-arg constructors pass List.of().

Refs #804"
```

---

### Task 3: CaseDefinition fields + YAML mapping

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/CaseDefinition.java` (lines 68-78 fields, builder methods, getters/setters)
- Modify: `runtime/src/main/java/io/casehub/engine/internal/definition/CaseDefinitionYamlMapper.java`
- Test: `runtime/src/test/java/io/casehub/engine/internal/definition/CaseDefinitionYamlMapperTest.java` (add cases)

**Interfaces:**
- Consumes: `ReflectionTriggerConfig`, `MemoryRetrievalConfig` from Task 1
- Produces:
  - `CaseDefinition.getReflectionTrigger()` → `ReflectionTriggerConfig` (nullable)
  - `CaseDefinition.getMemoryRetrieval()` → `MemoryRetrievalConfig` (nullable)
  - `CaseDefinition.Builder.reflectionTrigger(ReflectionTriggerConfig)` → `Builder`
  - `CaseDefinition.Builder.memoryRetrieval(MemoryRetrievalConfig)` → `Builder`
  - YAML `spec.reflection:` and `spec.memoryRetrieval:` parsing

- [ ] **Step 1: Write YAML mapping tests**

Add to `CaseDefinitionYamlMapperTest`:

```java
@Test
void parsesReflectionBlock() throws Exception {
    String yaml = """
        name: test-case
        version: "1.0"
        spec:
          reflection:
            enabled: true
            importanceThreshold: 5.0
            maxUnreflectedOutcomes: 20
            maxSourceMemories: 100
            importanceWeights:
              SUCCESS: 0.1
              FAILED: 0.9
        """;
    CaseDefinition def = mapper.map(yaml);
    var config = def.getReflectionTrigger();
    assertNotNull(config);
    assertTrue(config.enabled());
    assertEquals(5.0, config.importanceThreshold());
    assertEquals(20, config.maxUnreflectedOutcomes());
    assertEquals(100, config.maxSourceMemories());
    assertEquals(0.1, config.importanceWeights().get("SUCCESS"));
    assertEquals(0.9, config.importanceWeights().get("FAILED"));
}

@Test
void parsesMemoryRetrievalBlock() throws Exception {
    String yaml = """
        name: test-case
        version: "1.0"
        spec:
          memoryRetrieval:
            enabled: true
            maxMemories: 5
            domains: [experience, reflection, relationship]
        """;
    CaseDefinition def = mapper.map(yaml);
    var config = def.getMemoryRetrieval();
    assertNotNull(config);
    assertTrue(config.enabled());
    assertEquals(5, config.maxMemories());
    assertEquals(Set.of("experience", "reflection", "relationship"), config.domains());
}

@Test
void missingBlocksReturnNull() throws Exception {
    String yaml = """
        name: test-case
        version: "1.0"
        """;
    CaseDefinition def = mapper.map(yaml);
    assertNull(def.getReflectionTrigger());
    assertNull(def.getMemoryRetrieval());
}

@Test
void missingImportanceWeightsUsesDefaults() throws Exception {
    String yaml = """
        name: test-case
        version: "1.0"
        spec:
          reflection:
            enabled: true
        """;
    CaseDefinition def = mapper.map(yaml);
    var config = def.getReflectionTrigger();
    assertNotNull(config);
    assertEquals(ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS,
        config.importanceWeights());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl runtime -Dtest="CaseDefinitionYamlMapperTest#parsesReflectionBlock+parsesMemoryRetrievalBlock+missingBlocksReturnNull+missingImportanceWeightsUsesDefaults" -q`
Expected: compilation failure

- [ ] **Step 3: Add fields to CaseDefinition**

Use `ide_insert_member` to add after `defaultQuorum` (line 78):

```java
private ReflectionTriggerConfig reflectionTrigger;
private MemoryRetrievalConfig memoryRetrieval;
```

Add getter/setter pairs via `ide_insert_member`:

```java
public ReflectionTriggerConfig getReflectionTrigger() { return reflectionTrigger; }
public void setReflectionTrigger(ReflectionTriggerConfig reflectionTrigger) { this.reflectionTrigger = reflectionTrigger; }
public MemoryRetrievalConfig getMemoryRetrieval() { return memoryRetrieval; }
public void setMemoryRetrieval(MemoryRetrievalConfig memoryRetrieval) { this.memoryRetrieval = memoryRetrieval; }
```

Add builder fields and methods in `Builder` class via `ide_insert_member`:

```java
// fields
private ReflectionTriggerConfig reflectionTrigger;
private MemoryRetrievalConfig memoryRetrieval;

// methods
public Builder reflectionTrigger(ReflectionTriggerConfig reflectionTrigger) {
    this.reflectionTrigger = reflectionTrigger;
    return this;
}
public Builder memoryRetrieval(MemoryRetrievalConfig memoryRetrieval) {
    this.memoryRetrieval = memoryRetrieval;
    return this;
}
```

Add to `build()` method (use `ide_edit_member` on `build`):

```java
def.setReflectionTrigger(reflectionTrigger);
def.setMemoryRetrieval(memoryRetrieval);
```

- [ ] **Step 4: Add YAML mapping for reflection and memoryRetrieval blocks**

In `CaseDefinitionYamlMapper`, use `ide_edit_member` on the mapping method to add parsing after the existing `routingSignalWeights` / `authorization` / `quorum` block. Follow the raw `JsonNode` pattern (not generated schema classes):

```java
// reflection
final JsonNode reflectionNode = specNode != null ? specNode.get("reflection") : null;
if (reflectionNode != null && reflectionNode.isObject()) {
    Map<String, Double> weights = ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS;
    JsonNode weightsNode = reflectionNode.get("importanceWeights");
    if (weightsNode != null && weightsNode.isObject()) {
        weights = new LinkedHashMap<>();
        var it = weightsNode.fields();
        while (it.hasNext()) {
            var e = it.next();
            weights.put(e.getKey(), e.getValue().asDouble());
        }
    }
    def.setReflectionTrigger(new ReflectionTriggerConfig(
        reflectionNode.has("enabled") && reflectionNode.get("enabled").asBoolean(),
        reflectionNode.has("importanceThreshold")
            ? reflectionNode.get("importanceThreshold").asDouble() : 3.0,
        reflectionNode.has("maxUnreflectedOutcomes")
            ? reflectionNode.get("maxUnreflectedOutcomes").asInt() : 10,
        reflectionNode.has("maxSourceMemories")
            ? reflectionNode.get("maxSourceMemories").asInt() : 50,
        Map.copyOf(weights)));
}

// memoryRetrieval
final JsonNode memRetrievalNode = specNode != null ? specNode.get("memoryRetrieval") : null;
if (memRetrievalNode != null && memRetrievalNode.isObject()) {
    Set<String> domains = Set.of();
    JsonNode domainsNode = memRetrievalNode.get("domains");
    if (domainsNode != null && domainsNode.isArray()) {
        var domainSet = new LinkedHashSet<String>();
        domainsNode.forEach(n -> domainSet.add(n.asText()));
        domains = Set.copyOf(domainSet);
    }
    def.setMemoryRetrieval(new MemoryRetrievalConfig(
        memRetrievalNode.has("enabled") && memRetrievalNode.get("enabled").asBoolean(),
        memRetrievalNode.has("maxMemories")
            ? memRetrievalNode.get("maxMemories").asInt() : 10,
        domains));
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl runtime -Dtest="CaseDefinitionYamlMapperTest#parsesReflectionBlock+parsesMemoryRetrievalBlock+missingBlocksReturnNull+missingImportanceWeightsUsesDefaults" -q`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java/io/casehub/api/model/CaseDefinition.java \
       runtime/src/main/java/io/casehub/engine/internal/definition/CaseDefinitionYamlMapper.java \
       runtime/src/test/java/io/casehub/engine/internal/definition/CaseDefinitionYamlMapperTest.java
git commit -m "feat(#801): CaseDefinition reflection/memoryRetrieval config + YAML mapping

Refs #801, Refs #804"
```

---

### Task 4: AgentExperienceRecorder

**Files:**
- Create: `runtime/src/main/java/io/casehub/engine/internal/memory/AgentExperienceRecorder.java`
- Test: `runtime/src/test/java/io/casehub/engine/internal/memory/AgentExperienceRecorderTest.java`

**Interfaces:**
- Consumes: `ExperienceRecorder` (neocortex SPI), `ReflectionOrchestrator` (neocortex SPI), `CaseDefinitionRegistry`, `ReflectionTriggerConfig` from Task 1, `Outcome` from neocortex memory-api
- Produces: `void record(CaseInstance, String workerName, String capabilityName, WorkerOutcome<?>, String bindingName)` — called by `WorkflowExecutionCompletedHandler`

- [ ] **Step 1: Write AgentExperienceRecorder tests**

```java
package io.casehub.engine.internal.memory;

import static org.junit.jupiter.api.Assertions.*;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ReflectionTriggerConfig;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.CaseDefinitionRegistry;
import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.Outcome;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.worker.api.WorkerOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentExperienceRecorderTest {

    private final List<ExperienceEvent> recorded = new ArrayList<>();
    private final AtomicInteger reflectionCount = new AtomicInteger();
    private CaseDefinition definition;
    private AgentExperienceRecorder recorder;

    @BeforeEach
    void setUp() {
        recorded.clear();
        reflectionCount.set(0);
        ExperienceRecorder expRecorder = event -> { recorded.add(event); return "mem-1"; };
        ReflectionOrchestrator orchestrator = (agentId, tenantId, since, max) -> {
            reflectionCount.incrementAndGet();
            return List.of("insight-1");
        };
        definition = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0")
            .reflectionTrigger(new ReflectionTriggerConfig(
                true, 3.0, 10, 50,
                ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS))
            .build();
        CaseDefinitionRegistry registry = new TestCaseDefinitionRegistry(definition);
        recorder = new AgentExperienceRecorder(expRecorder, orchestrator, registry);
    }

    @Test
    void recordsOutcomeAsExperienceEvent() {
        var instance = createInstance();
        recorder.record(instance, "agent-1", "analysis",
            WorkerOutcome.success(Map.of()), "binding-1");
        assertEquals(1, recorded.size());
        var event = (Outcome) recorded.get(0);
        assertEquals("agent-1", event.agentId());
        assertEquals("analysis", event.capability());
        assertEquals("SUCCESS", event.result());
    }

    @Test
    void successOutcomeHasCorrectImportance() {
        recorder.record(createInstance(), "agent-1", "cap",
            WorkerOutcome.success(Map.of()), "b");
        assertEquals(0.3, ((Outcome) recorded.get(0)).importance());
    }

    @Test
    void failedOutcomeHasCorrectImportance() {
        recorder.record(createInstance(), "agent-1", "cap",
            WorkerOutcome.failed("error"), "b");
        assertEquals(0.8, ((Outcome) recorded.get(0)).importance());
    }

    @Test
    void declinedOutcomeHasCorrectImportance() {
        recorder.record(createInstance(), "agent-1", "cap",
            WorkerOutcome.declined("reason"), "b");
        assertEquals(0.6, ((Outcome) recorded.get(0)).importance());
    }

    @Test
    void reflectionTriggersAtImportanceThreshold() throws Exception {
        var instance = createInstance();
        // 4 FAILED outcomes: 4 * 0.8 = 3.2 > threshold 3.0
        for (int i = 0; i < 4; i++) {
            recorder.record(instance, "agent-1", "cap",
                WorkerOutcome.failed("err"), "b");
        }
        Thread.sleep(200); // allow virtual thread to complete
        assertEquals(1, reflectionCount.get());
    }

    @Test
    void reflectionTriggersAtCountCeiling() throws Exception {
        var instance = createInstance();
        definition = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0")
            .reflectionTrigger(new ReflectionTriggerConfig(
                true, 100.0, 3, 50,
                ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS))
            .build();
        CaseDefinitionRegistry registry = new TestCaseDefinitionRegistry(definition);
        recorder = new AgentExperienceRecorder(
            event -> { recorded.add(event); return "mem-1"; },
            (a, t, s, m) -> { reflectionCount.incrementAndGet(); return List.of(); },
            registry);
        for (int i = 0; i < 3; i++) {
            recorder.record(instance, "agent-1", "cap",
                WorkerOutcome.success(Map.of()), "b");
        }
        Thread.sleep(200);
        assertEquals(1, reflectionCount.get());
    }

    @Test
    void noopWhenConfigDisabled() {
        definition = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0")
            .build(); // no reflectionTrigger
        recorder = new AgentExperienceRecorder(
            event -> { recorded.add(event); return "mem-1"; },
            (a, t, s, m) -> { reflectionCount.incrementAndGet(); return List.of(); },
            new TestCaseDefinitionRegistry(definition));
        recorder.record(createInstance(), "agent-1", "cap",
            WorkerOutcome.success(Map.of()), "b");
        assertEquals(1, recorded.size()); // still records
        assertEquals(0, reflectionCount.get()); // no reflection
    }

    private CaseInstance createInstance() {
        var instance = new CaseInstance();
        instance.id = 1L;
        instance.setCaseId(UUID.randomUUID());
        instance.setTenancyId("tenant-1");
        instance.setNamespace("ns");
        instance.setName("test");
        instance.setVersion("1.0");
        return instance;
    }
}
```

Note: `TestCaseDefinitionRegistry` is a minimal test helper that returns the given definition for any lookup. Create it as a static inner class in the test.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl runtime -Dtest="AgentExperienceRecorderTest" -q`
Expected: compilation failure

- [ ] **Step 3: Implement AgentExperienceRecorder**

Create `runtime/src/main/java/io/casehub/engine/internal/memory/AgentExperienceRecorder.java`:

```java
package io.casehub.engine.internal.memory;

import io.casehub.api.model.ReflectionTriggerConfig;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.CaseDefinitionRegistry;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.Outcome;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.worker.api.WorkerOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AgentExperienceRecorder {

    private static final Logger LOG = Logger.getLogger(AgentExperienceRecorder.class);

    private final ExperienceRecorder experienceRecorder;
    private final ReflectionOrchestrator reflectionOrchestrator;
    private final CaseDefinitionRegistry caseDefinitionRegistry;
    private final ConcurrentHashMap<String, ReflectionState> reflectionStates = new ConcurrentHashMap<>();

    @Inject
    public AgentExperienceRecorder(
            Instance<ExperienceRecorder> experienceRecorder,
            Instance<ReflectionOrchestrator> reflectionOrchestrator,
            CaseDefinitionRegistry caseDefinitionRegistry) {
        this.experienceRecorder = experienceRecorder.isResolvable() ? experienceRecorder.get() : null;
        this.reflectionOrchestrator = reflectionOrchestrator.isResolvable() ? reflectionOrchestrator.get() : null;
        this.caseDefinitionRegistry = caseDefinitionRegistry;
    }

    // Test constructor — direct injection without Instance<>
    AgentExperienceRecorder(
            ExperienceRecorder experienceRecorder,
            ReflectionOrchestrator reflectionOrchestrator,
            CaseDefinitionRegistry caseDefinitionRegistry) {
        this.experienceRecorder = experienceRecorder;
        this.reflectionOrchestrator = reflectionOrchestrator;
        this.caseDefinitionRegistry = caseDefinitionRegistry;
    }

    public void record(CaseInstance caseInstance, String workerName,
                       String capabilityName, WorkerOutcome<?> outcome,
                       String bindingName) {
        if (experienceRecorder == null) return;

        var config = lookupConfig(caseInstance);
        double importance = resolveImportance(outcome, config);

        try {
            var event = new Outcome(
                workerName,
                caseInstance.getTenancyId(),
                caseInstance.getCaseId().toString(),
                UUID.randomUUID().toString(),
                buildDescription(capabilityName, outcome),
                importance,
                Map.of("bindingName", bindingName,
                       "caseDefinitionName", caseInstance.getName()),
                outcomeKindName(outcome),
                capabilityName);
            experienceRecorder.record(event);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to record experience for agent %s", workerName);
        }

        evaluateReflectionTrigger(caseInstance, workerName, importance, config);
    }

    private ReflectionTriggerConfig lookupConfig(CaseInstance caseInstance) {
        try {
            var def = caseDefinitionRegistry.findByIdentity(
                caseInstance.getNamespace(), caseInstance.getName(),
                caseInstance.getVersion());
            return def.map(d -> {
                var caseDef = caseDefinitionRegistry.getCaseDefinition(
                    d.getNamespace(), d.getName(), d.getVersion());
                return caseDef.getReflectionTrigger();
            }).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private double resolveImportance(WorkerOutcome<?> outcome, ReflectionTriggerConfig config) {
        Map<String, Double> weights = config != null && !config.importanceWeights().isEmpty()
            ? config.importanceWeights()
            : ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS;
        String kind = outcomeKindName(outcome);
        return weights.getOrDefault(kind, 0.3);
    }

    private void evaluateReflectionTrigger(CaseInstance caseInstance, String workerName,
                                            double importance, ReflectionTriggerConfig config) {
        if (config == null || !config.enabled() || reflectionOrchestrator == null) return;

        String key = workerName + "|" + caseInstance.getTenancyId();
        var shouldReflect = new boolean[]{false};
        var since = new Instant[]{null};

        reflectionStates.compute(key, (k, state) -> {
            if (state == null) state = new ReflectionState();
            state.outcomeCount++;
            state.cumulativeImportance += importance;
            if (state.outcomeCount >= config.maxUnreflectedOutcomes()
                || state.cumulativeImportance >= config.importanceThreshold()) {
                since[0] = state.lastReflectionTime;
                state.outcomeCount = 0;
                state.cumulativeImportance = 0.0;
                state.lastReflectionTime = Instant.now();
                shouldReflect[0] = true;
            }
            return state;
        });

        if (shouldReflect[0]) {
            final Instant sinceFinal = since[0];
            Thread.startVirtualThread(() -> {
                try {
                    reflectionOrchestrator.reflect(
                        workerName, caseInstance.getTenancyId(),
                        sinceFinal, config.maxSourceMemories());
                } catch (Exception e) {
                    LOG.warnf(e, "Reflection failed for agent %s", workerName);
                }
            });
        }
    }

    private static String buildDescription(String capabilityName, WorkerOutcome<?> outcome) {
        return switch (outcome) {
            case WorkerOutcome.Success<?> s -> "Completed " + capabilityName;
            case WorkerOutcome.Completed<?> c -> "Completed " + capabilityName;
            case WorkerOutcome.Declined<?> d -> "Declined " + capabilityName + ": " + d.reason();
            case WorkerOutcome.Failed<?> f -> "Failed " + capabilityName + ": " + f.reason();
            case WorkerOutcome.Expired<?> e -> "Expired " + capabilityName + ": " + e.reason();
        };
    }

    private static String outcomeKindName(WorkerOutcome<?> outcome) {
        return switch (outcome) {
            case WorkerOutcome.Success<?> s -> "SUCCESS";
            case WorkerOutcome.Completed<?> c -> "COMPLETED";
            case WorkerOutcome.Declined<?> d -> "DECLINED";
            case WorkerOutcome.Failed<?> f -> "FAILED";
            case WorkerOutcome.Expired<?> e -> "EXPIRED";
        };
    }

    private static class ReflectionState {
        int outcomeCount;
        double cumulativeImportance;
        Instant lastReflectionTime;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl runtime -Dtest="AgentExperienceRecorderTest" -q`
Expected: all pass

- [ ] **Step 5: Commit**

```bash
git add runtime/src/main/java/io/casehub/engine/internal/memory/AgentExperienceRecorder.java \
       runtime/src/test/java/io/casehub/engine/internal/memory/AgentExperienceRecorderTest.java
git commit -m "feat(#801): AgentExperienceRecorder — experience recording + reflection trigger

Records worker outcomes via ExperienceRecorder SPI. Evaluates
ReflectionTriggerConfig thresholds and fires ReflectionOrchestrator
on virtual thread when met. Atomic via ConcurrentHashMap.compute().

Refs #801"
```

---

### Task 5: AgentMemoryRetriever

**Files:**
- Create: `runtime/src/main/java/io/casehub/engine/internal/memory/AgentMemoryRetriever.java`
- Test: `runtime/src/test/java/io/casehub/engine/internal/memory/AgentMemoryRetrieverTest.java`

**Interfaces:**
- Consumes: `CaseMemoryStore` (neocortex SPI), `MemoryRetrievalConfig` from Task 1, `CaseDefinition` from Task 3
- Produces: `List<RetrievedMemory> retrieve(String workerName, String tenantId, String capabilityName, CaseDefinition)` — called by `WorkerScheduleEventHandler`

- [ ] **Step 1: Write AgentMemoryRetriever tests**

```java
package io.casehub.engine.internal.memory;

import static org.junit.jupiter.api.Assertions.*;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.MemoryRetrievalConfig;
import io.casehub.api.model.RetrievedMemory;
import io.casehub.neocortex.memory.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AgentMemoryRetrieverTest {

    @Test
    void returnsEmptyWhenDisabled() {
        var def = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0").build();
        var retriever = new AgentMemoryRetriever(new InMemoryCaseMemoryStore());
        var result = retriever.retrieve("agent-1", "tenant-1", "analysis", def);
        assertTrue(result.isEmpty());
    }

    @Test
    void retrievesFromConfiguredDomains() {
        var store = new InMemoryCaseMemoryStore();
        store.store(new MemoryInput("agent-1", "experience memory",
            Map.of(), new MemoryDomain("experience"), "tenant-1", null));
        store.store(new MemoryInput("agent-1", "reflection insight",
            Map.of(), new MemoryDomain("reflection"), "tenant-1", null));
        store.store(new MemoryInput("agent-1", "relationship note",
            Map.of(), new MemoryDomain("relationship"), "tenant-1", null));

        var def = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0")
            .memoryRetrieval(new MemoryRetrievalConfig(true, 10,
                Set.of("experience", "reflection")))
            .build();

        var retriever = new AgentMemoryRetriever(store);
        var result = retriever.retrieve("agent-1", "tenant-1", "analysis", def);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(m -> m.domain().equals("experience")));
        assertTrue(result.stream().anyMatch(m -> m.domain().equals("reflection")));
        assertFalse(result.stream().anyMatch(m -> m.domain().equals("relationship")));
    }

    @Test
    void truncatesToMaxMemories() {
        var store = new InMemoryCaseMemoryStore();
        for (int i = 0; i < 20; i++) {
            store.store(new MemoryInput("agent-1", "memory " + i,
                Map.of(), new MemoryDomain("experience"), "tenant-1", null));
        }
        var def = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0")
            .memoryRetrieval(new MemoryRetrievalConfig(true, 5, Set.of("experience")))
            .build();
        var retriever = new AgentMemoryRetriever(store);
        var result = retriever.retrieve("agent-1", "tenant-1", "cap", def);
        assertEquals(5, result.size());
    }

    @Test
    void roundRobinInterleaveAcrossDomains() {
        var store = new InMemoryCaseMemoryStore();
        for (int i = 0; i < 5; i++) {
            store.store(new MemoryInput("agent-1", "exp-" + i,
                Map.of(), new MemoryDomain("experience"), "tenant-1", null));
        }
        for (int i = 0; i < 5; i++) {
            store.store(new MemoryInput("agent-1", "ref-" + i,
                Map.of(), new MemoryDomain("reflection"), "tenant-1", null));
        }
        var def = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0")
            .memoryRetrieval(new MemoryRetrievalConfig(true, 6,
                Set.of("experience", "reflection")))
            .build();
        var retriever = new AgentMemoryRetriever(store);
        var result = retriever.retrieve("agent-1", "tenant-1", "cap", def);
        assertEquals(6, result.size());
        // Round-robin: alternating domains
        assertNotEquals(result.get(0).domain(), result.get(1).domain());
    }

    @Test
    void returnsEmptyWhenStoreUnavailable() {
        var retriever = new AgentMemoryRetriever((CaseMemoryStore) null);
        var def = CaseDefinition.builder()
            .namespace("ns").name("test").version("1.0")
            .memoryRetrieval(MemoryRetrievalConfig.defaults())
            .build();
        var result = retriever.retrieve("agent-1", "tenant-1", "cap", def);
        assertTrue(result.isEmpty());
    }
}
```

Note: `InMemoryCaseMemoryStore` is from `casehub-neocortex-memory-inmem` (test dependency).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl runtime -Dtest="AgentMemoryRetrieverTest" -q`
Expected: compilation failure

- [ ] **Step 3: Implement AgentMemoryRetriever**

Create `runtime/src/main/java/io/casehub/engine/internal/memory/AgentMemoryRetriever.java`:

```java
package io.casehub.engine.internal.memory;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.MemoryRetrievalConfig;
import io.casehub.api.model.RetrievedMemory;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AgentMemoryRetriever {

    private static final Logger LOG = Logger.getLogger(AgentMemoryRetriever.class);

    private final CaseMemoryStore caseMemoryStore;

    @Inject
    public AgentMemoryRetriever(Instance<CaseMemoryStore> caseMemoryStore) {
        this.caseMemoryStore = caseMemoryStore.isResolvable() ? caseMemoryStore.get() : null;
    }

    // Test constructor
    AgentMemoryRetriever(CaseMemoryStore caseMemoryStore) {
        this.caseMemoryStore = caseMemoryStore;
    }

    public List<RetrievedMemory> retrieve(String workerName, String tenantId,
                                           String capabilityName,
                                           CaseDefinition caseDefinition) {
        if (caseMemoryStore == null) return List.of();

        MemoryRetrievalConfig config = caseDefinition.getMemoryRetrieval();
        if (config == null || !config.enabled()) return List.of();

        try {
            Set<String> domains = config.domains().isEmpty()
                ? Set.of("experience", "reflection") : config.domains();
            int perDomainLimit = Math.max(1, config.maxMemories() / domains.size());

            List<List<Memory>> perDomainResults = new ArrayList<>();
            for (String domain : domains) {
                List<Memory> memories = caseMemoryStore.query(
                    MemoryQuery.forEntity(workerName, new MemoryDomain(domain), tenantId)
                        .withQuestion(capabilityName)
                        .withLimit(perDomainLimit)
                        .withOrder(MemoryOrder.SALIENCE));
                perDomainResults.add(memories);
            }

            List<RetrievedMemory> merged = interleaveRoundRobin(
                perDomainResults, config.maxMemories());
            return List.copyOf(merged);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to retrieve memories for agent %s", workerName);
            return List.of();
        }
    }

    private List<RetrievedMemory> interleaveRoundRobin(
            List<List<Memory>> perDomainResults, int maxMemories) {
        List<RetrievedMemory> result = new ArrayList<>();
        int[] indices = new int[perDomainResults.size()];
        while (result.size() < maxMemories) {
            boolean added = false;
            for (int d = 0; d < perDomainResults.size(); d++) {
                if (result.size() >= maxMemories) break;
                List<Memory> domainList = perDomainResults.get(d);
                if (indices[d] < domainList.size()) {
                    result.add(toRetrievedMemory(domainList.get(indices[d])));
                    indices[d]++;
                    added = true;
                }
            }
            if (!added) break;
        }
        return result;
    }

    private static RetrievedMemory toRetrievedMemory(Memory memory) {
        return new RetrievedMemory(
            memory.memoryId(),
            memory.text(),
            memory.domain().value(),
            memory.createdAt(),
            memory.attributes() != null
                ? memory.attributes().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())))
                : java.util.Map.of());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl runtime -Dtest="AgentMemoryRetrieverTest" -q`
Expected: all pass

- [ ] **Step 5: Commit**

```bash
git add runtime/src/main/java/io/casehub/engine/internal/memory/AgentMemoryRetriever.java \
       runtime/src/test/java/io/casehub/engine/internal/memory/AgentMemoryRetrieverTest.java
git commit -m "feat(#804): AgentMemoryRetriever — memory-informed dispatch

Queries CaseMemoryStore per MemoryRetrievalConfig domains with
round-robin interleaving. Transparent no-op when neocortex absent.

Refs #804"
```

---

### Task 6: Wire into handlers + dispatch path

**Files:**
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` (inject + call AgentExperienceRecorder)
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkerScheduleEventHandler.java` (inject + call AgentMemoryRetriever, thread memories through EventLog)
- Modify: `runtime/src/main/java/io/casehub/engine/internal/executor/QuartzWorkerExecutionJob.java` (deserialize memories from EventLog, pass to WorkerContext)
- Test: integration test verifying end-to-end wiring

**Interfaces:**
- Consumes: `AgentExperienceRecorder.record()` from Task 4, `AgentMemoryRetriever.retrieve()` from Task 5
- Produces: memories available via `WorkerContext.memories()` at worker execution time

- [ ] **Step 1: Inject AgentExperienceRecorder into WorkflowExecutionCompletedHandler**

Use `ide_insert_member` to add field after `goalFailureRecorder` (line 93):

```java
@Inject AgentExperienceRecorder agentExperienceRecorder;
```

- [ ] **Step 2: Add recording call to success path**

In `onWorkflowExecutionCompletedHandler()`, after `recordSuccessOutcome()`, add:

```java
agentExperienceRecorder.record(caseInstance, worker.name(),
    extractCapabilityTag(caseInstance, worker, event.bindingName()),
    event.outcome(), event.bindingName());
```

- [ ] **Step 3: Add recording call to failure path**

In `handleSemanticFailure()`, alongside existing `personalitySignalRecorder.record()` and `goalFailureRecorder.record()` calls, add:

```java
agentExperienceRecorder.record(caseInstance, worker.name(),
    capabilityTag, event.outcome(), event.bindingName());
```

- [ ] **Step 4: Inject AgentMemoryRetriever into WorkerScheduleEventHandler**

Use `ide_insert_member` to add field:

```java
@Inject AgentMemoryRetriever agentMemoryRetriever;
```

- [ ] **Step 5: Call retriever and thread memories through EventLog**

In `onWorkerScheduleEventHandler()`, after input projection resolution and before `buildEventLog()`:

```java
CaseDefinition caseDefinition = caseDefinitionRegistry.getCaseDefinition(
    instance.getNamespace(), instance.getName(), instance.getVersion());
List<RetrievedMemory> memories = agentMemoryRetriever.retrieve(
    worker.name(), instance.getTenancyId(), capability.name(), caseDefinition);
```

Pass `memories` to `buildEventLog()` — add a parameter. In `buildMetadata()`, serialize memories to the EventLog metadata JSON node:

```java
if (memories != null && !memories.isEmpty()) {
    metadataNode.set("memories", OBJECT_MAPPER.valueToTree(memories));
    metadataNode.put("retrievedMemoryCount", memories.size());
}
```

- [ ] **Step 6: Deserialize memories in QuartzWorkerExecutionJob**

Add method to `QuartzWorkerExecutionJob`:

```java
private List<RetrievedMemory> deserializeMemories(EventLog eventLog) {
    try {
        JsonNode metadata = eventLog.getMetadata();
        if (metadata == null || !metadata.has("memories")) return List.of();
        return OBJECT_MAPPER.convertValue(
            metadata.get("memories"),
            new TypeReference<List<RetrievedMemory>>() {});
    } catch (Exception e) {
        LOG.warnf(e, "Failed to deserialize memories from EventLog %d", eventLog.getId());
        return List.of();
    }
}
```

Call this method and pass results to the 8-arg `WorkerContext` constructor:

```java
List<RetrievedMemory> memories = deserializeMemories(eventLog);
var workerContext = new WorkerContext(
    taskDescription, caseId, channels, priorWorkers,
    propagationContext, properties, experiences, memories);
```

- [ ] **Step 7: Run full test suite to verify no regressions**

Run: `mvn test -pl runtime -q`
Expected: all existing tests pass (new injections use existing CDI patterns)

- [ ] **Step 8: Verify with ide_diagnostics**

Run `ide_diagnostics` on all modified files to check for compilation errors or warnings.

- [ ] **Step 9: Commit**

```bash
git add runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java \
       runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkerScheduleEventHandler.java \
       runtime/src/main/java/io/casehub/engine/internal/executor/QuartzWorkerExecutionJob.java
git commit -m "feat(#801,#804): wire AgentExperienceRecorder and AgentMemoryRetriever into dispatch path

WorkflowExecutionCompletedHandler calls AgentExperienceRecorder on
success and failure paths. WorkerScheduleEventHandler calls
AgentMemoryRetriever and serializes memories to EventLog metadata.
QuartzWorkerExecutionJob deserializes and passes to WorkerContext.

Closes #801, Closes #804"
```

- [ ] **Step 10: Update CLAUDE.md and design journal**

Add new sections to CLAUDE.md documenting AgentExperienceRecorder and AgentMemoryRetriever. Update the design journal with what was built.
