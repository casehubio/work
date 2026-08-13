# Goal Decomposition Binding Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #110 — feat: LLM goal decomposition — generating explicit typed plans from natural-language goals
**Issue group:** #110

**Goal:** Fix the three bugs preventing GoalDecomposer from bridging LLM-generated GoalSteps to engine Binding dispatch.

**Architecture:** Add `CaseDefinition.findBindingsByCapability()` for reverse lookup.
Extract `BindingExecutorResolver` from `PlanningStrategyLoopControl` as a shared utility.
Fix `DefaultGoalDecomposer` to resolve capability → binding name and use binding names
throughout compound creation and PlanItem persistence.

**Tech Stack:** Java 21, Quarkus 3.32.2, JUnit 5, Mockito, AssertJ

## Global Constraints

- No new modules — changes span `engine-api`, `engine-common`, and `planning`
- No new dependencies
- Existing `DefaultGoalDecomposerTest` must continue to pass (update, not replace)
- v1 limitation: one binding per capability in decomposed plans (first in declaration order)

---

### Task 1: Add `CaseDefinition.findBindingsByCapability()`

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/CaseDefinition.java`
- Test: `api/src/test/java/io/casehub/api/model/CaseDefinitionBindingLookupTest.java`

**Interfaces:**
- Consumes: `CaseDefinition.getBindings()`, `Binding.target()`, `CapabilityTarget.capability().name()`
- Produces: `CaseDefinition.findBindingsByCapability(String capabilityName) → List<Binding>` — returns bindings in declaration order whose target is a `CapabilityTarget` matching the given capability name

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.worker.api.Capability;
import org.junit.jupiter.api.Test;

class CaseDefinitionBindingLookupTest {

  @Test
  void findBindingsByCapability_singleMatch() {
    var cap = new Capability("analysis", "", "", null);
    var binding = Binding.builder().name("analyse").capability(cap)
        .on(new ContextChangeTrigger(".data != null")).build();
    var definition = CaseDefinition.builder()
        .namespace("test").name("test").version("1.0")
        .capabilities(cap)
        .bindings(binding)
        .build();

    var result = definition.findBindingsByCapability("analysis");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("analyse");
  }

  @Test
  void findBindingsByCapability_multipleBindings_declarationOrder() {
    var cap = new Capability("analysis", "", "", null);
    var b1 = Binding.builder().name("quick-analyse").capability(cap)
        .on(new ContextChangeTrigger(".data != null")).build();
    var b2 = Binding.builder().name("deep-analyse").capability(cap)
        .on(new ContextChangeTrigger(".data != null")).build();
    var definition = CaseDefinition.builder()
        .namespace("test").name("test").version("1.0")
        .capabilities(cap)
        .bindings(b1, b2)
        .build();

    var result = definition.findBindingsByCapability("analysis");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getName()).isEqualTo("quick-analyse");
    assertThat(result.get(1).getName()).isEqualTo("deep-analyse");
  }

  @Test
  void findBindingsByCapability_noMatch() {
    var cap = new Capability("research", "", "", null);
    var binding = Binding.builder().name("research-binding").capability(cap)
        .on(new ContextChangeTrigger(".topic != null")).build();
    var definition = CaseDefinition.builder()
        .namespace("test").name("test").version("1.0")
        .capabilities(cap)
        .bindings(binding)
        .build();

    var result = definition.findBindingsByCapability("nonexistent");

    assertThat(result).isEmpty();
  }

  @Test
  void findBindingsByCapability_excludesNonCapabilityTargets() {
    var cap = new Capability("analysis", "", "", null);
    var capBinding = Binding.builder().name("analyse").capability(cap)
        .on(new ContextChangeTrigger(".data != null")).build();
    var htBinding = Binding.builder().name("review")
        .humanTask(HumanTaskTarget.builder().build())
        .on(new ContextChangeTrigger(".needsReview == true")).build();
    var definition = CaseDefinition.builder()
        .namespace("test").name("test").version("1.0")
        .capabilities(cap)
        .bindings(capBinding, htBinding)
        .build();

    var result = definition.findBindingsByCapability("analysis");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("analyse");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl api -Dtest=CaseDefinitionBindingLookupTest -DfailIfNoTests=false -q`
Expected: compilation error — `findBindingsByCapability` does not exist

- [ ] **Step 3: Implement `findBindingsByCapability`**

Add to `CaseDefinition.java` after the existing `getBindings()` method (around line 150):

```java
public java.util.List<Binding> findBindingsByCapability(String capabilityName) {
    return bindings.stream()
        .filter(b -> b.target() instanceof CapabilityTarget ct
            && ct.capability().name().equals(capabilityName))
        .toList();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl api -Dtest=CaseDefinitionBindingLookupTest -q`
Expected: all 4 tests PASS

- [ ] **Step 5: Commit**

```
Refs #110: add CaseDefinition.findBindingsByCapability() reverse lookup
```

---

### Task 2: Extract `BindingExecutorResolver` shared utility

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/internal/routing/BindingExecutorResolver.java`
- Modify: `planning/src/main/java/io/casehub/engine/planning/control/PlanningStrategyLoopControl.java`
- Test: `common/src/test/java/io/casehub/engine/common/internal/routing/BindingExecutorResolverTest.java`

**Interfaces:**
- Consumes: `Binding.target()`, `CapabilityTarget.capability().name()`, `CaseDefinition.getWorkers()`, `Worker.capabilityNames()`, `ExecutorRef.of()`, `ExecutorRef.fromWorker()`
- Produces: `BindingExecutorResolver.resolve(Binding binding, CaseDefinition definition) → ExecutorRef` — static utility, no CDI

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.engine.common.internal.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.ExecutorRef;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

class BindingExecutorResolverTest {

  @Test
  void resolvesExecutorFromMatchingWorker() {
    var cap = new Capability("analysis", "", "", null);
    var worker = Worker.builder().name("analyst").capabilityName("analysis").noFunction().build();
    var binding = Binding.builder().name("analyse").capability(cap)
        .on(new ContextChangeTrigger(".data != null")).build();
    var definition = CaseDefinition.builder()
        .namespace("test").name("test").version("1.0")
        .capabilities(cap).workers(worker).bindings(binding).build();

    ExecutorRef result = BindingExecutorResolver.resolve(binding, definition);

    assertThat(result.name()).isEqualTo("analyst");
  }

  @Test
  void fallsBackToCapabilityNameWhenNoWorkerMatches() {
    var cap = new Capability("analysis", "", "", null);
    var binding = Binding.builder().name("analyse").capability(cap)
        .on(new ContextChangeTrigger(".data != null")).build();
    var definition = CaseDefinition.builder()
        .namespace("test").name("test").version("1.0")
        .capabilities(cap).bindings(binding).build();

    ExecutorRef result = BindingExecutorResolver.resolve(binding, definition);

    assertThat(result.name()).isEqualTo("analysis");
  }

  @Test
  void nonCapabilityTargetReturnsUnknown() {
    var binding = Binding.builder().name("review")
        .humanTask(HumanTaskTarget.builder().build())
        .on(new ContextChangeTrigger(".needsReview == true")).build();
    var definition = CaseDefinition.builder()
        .namespace("test").name("test").version("1.0").build();

    ExecutorRef result = BindingExecutorResolver.resolve(binding, definition);

    assertThat(result.name()).isEqualTo("unknown");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=BindingExecutorResolverTest -DfailIfNoTests=false -q`
Expected: compilation error — `BindingExecutorResolver` does not exist

- [ ] **Step 3: Implement `BindingExecutorResolver`**

```java
package io.casehub.engine.common.internal.routing;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CapabilityTarget;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ExecutorRef;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.SubCaseTarget;
import io.casehub.api.model.ExtensionTarget;
import io.casehub.worker.api.Worker;
import java.util.List;

public final class BindingExecutorResolver {

  private BindingExecutorResolver() {}

  public static ExecutorRef resolve(Binding binding, CaseDefinition definition) {
    return switch (binding.target()) {
      case null -> ExecutorRef.of("unknown");
      case CapabilityTarget ct -> {
        String capName = ct.capability().name();
        List<Worker> matching = definition.getWorkers().stream()
            .filter(w -> w.capabilityNames() != null && w.capabilityNames().contains(capName))
            .toList();
        yield matching.isEmpty()
            ? ExecutorRef.of(capName)
            : ExecutorRef.fromWorker(matching.get(0));
      }
      case SubCaseTarget st -> ExecutorRef.of("unknown");
      case HumanTaskTarget ht -> ExecutorRef.of("unknown");
      case ExtensionTarget et -> ExecutorRef.of("unknown");
    };
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl common -Dtest=BindingExecutorResolverTest -q`
Expected: all 3 tests PASS

- [ ] **Step 5: Delegate `PlanningStrategyLoopControl.resolveExecutor()` to `BindingExecutorResolver`**

Replace the body of `PlanningStrategyLoopControl.resolveExecutor()` (lines 287-319) with:

```java
private io.casehub.api.model.ExecutorRef resolveExecutor(
    Binding binding, PlanExecutionContext ctx) {
  return BindingExecutorResolver.resolve(binding, ctx.definition());
}
```

Add import: `import io.casehub.engine.common.internal.routing.BindingExecutorResolver;`

- [ ] **Step 6: Run planning module tests to verify no regressions**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl planning -q`
Expected: all existing tests PASS

- [ ] **Step 7: Commit**

```
Refs #110: extract BindingExecutorResolver from PlanningStrategyLoopControl
```

---

### Task 3: Fix `DefaultGoalDecomposer` binding bridge

**Files:**
- Modify: `planning/src/main/java/io/casehub/engine/planning/decomposition/DefaultGoalDecomposer.java`
- Modify: `planning/src/test/java/io/casehub/engine/planning/decomposition/DefaultGoalDecomposerTest.java`

**Interfaces:**
- Consumes: `CaseDefinition.findBindingsByCapability(String)` (Task 1), `BindingExecutorResolver.resolve(Binding, CaseDefinition)` (Task 2), `Binding.getName()`, `PlanItemDefinition.Compound.builder().binding(String)`, `PlanItemDefinition.Primitive(id, name, ExecutorRef, ExpressionEvaluator)`, `PlanItemSaveRequest.primitive(...)`
- Produces: Fixed GoalDecomposer that uses binding names for `scopedBindings`, PlanItem persistence, and resolved `ExecutorRef`

- [ ] **Step 1: Write new test — GoalStep with valid capability resolves to binding name**

Add to `DefaultGoalDecomposerTest.java`:

```java
@Test
void decomposesGoalStepsUsingBindingNames() {
  var caseId = UUID.randomUUID();
  var instance = new CaseInstance();
  instance.setUuid(caseId);
  instance.tenancyId = "tenant-1";

  var goal = new io.casehub.eidos.api.AgentGoal(
      "comprehensive-analysis", "Analyse data comprehensively",
      io.casehub.eidos.api.GoalPriority.PRIMARY,
      io.casehub.eidos.api.Visibility.PUBLIC, List.of());

  var descriptor = io.casehub.eidos.api.AgentDescriptor.builder()
      .agentId("agent-1").name("agent-1").slot("default").tenancyId("tenant-1")
      .goals(List.of(goal)).build();

  var cap = new Capability("data-gathering", "Gathers data", "", null);
  var worker = Worker.builder().name("research-agent")
      .capabilityName("data-gathering").noFunction().build();
  var binding = Binding.builder().name("gather")
      .capability(cap)
      .on(new io.casehub.api.model.ContextChangeTrigger(".sources != null"))
      .build();

  var definition = CaseDefinition.builder()
      .namespace("test").name("test").version("1.0")
      .capabilities(cap).workers(worker).bindings(binding)
      .decompositionStrategy("llm").build();
  setAgentDescriptors(definition, Map.of("research-agent", descriptor));

  var step = new GoalStep(UUID.randomUUID(), "Gather data from sources",
      "data-gathering", java.time.Instant.now());

  @SuppressWarnings("unchecked")
  DecompositionStrategy<JsonNode> strategy = mock(DecompositionStrategy.class);
  when(strategy.decompose(any(), any())).thenReturn(DagPlan.singleton(step));
  when(strategyResolver.resolve(any(), anyString())).thenReturn(strategy);
  when(abandonmentEvaluator.activeGoals(any())).thenReturn(List.of(goal));
  when(planItemStore.findByCaseId(caseId, "tenant-1")).thenReturn(List.of());

  var casePlanModel = mock(io.casehub.engine.planning.plan.CasePlanModel.class);
  when(blackboardRegistry.getOrCreate(any(), anyString())).thenReturn(casePlanModel);

  var context = mock(MutableCaseContext.class);
  var layer = mock(WritableLayer.class);
  when(context.layer(ContextLayer.WORKING)).thenReturn(layer);
  when(layer.asJsonNode()).thenReturn(MAPPER.createObjectNode());

  decomposer.decompose(instance, definition, context);

  // Verify compound registered with binding name "gather", not capability name "data-gathering"
  var compoundCaptor = ArgumentCaptor.forClass(
      io.casehub.engine.planning.plan.PlanItemDefinition.Compound.class);
  verify(casePlanModel).registerDefinition(compoundCaptor.capture());
  var compound = compoundCaptor.getValue();
  assertThat(compound.scopedBindings()).containsKey("gather");
  assertThat(compound.scopedBindings()).doesNotContainKey("data-gathering");

  // Verify PlanItem saved with binding name
  var saveCaptor = ArgumentCaptor.forClass(
      io.casehub.engine.common.internal.model.PlanItemSaveRequest.class);
  verify(planItemStore).save(saveCaptor.capture(), any());
  assertThat(saveCaptor.getValue().bindingName()).isEqualTo("gather");
}
```

- [ ] **Step 2: Write test — capability with no binding skips step**

```java
@Test
void skipsGoalStepWhenCapabilityHasNoBinding() {
  var caseId = UUID.randomUUID();
  var instance = new CaseInstance();
  instance.setUuid(caseId);
  instance.tenancyId = "tenant-1";

  var goal = new io.casehub.eidos.api.AgentGoal(
      "analyse", "Analyse data",
      io.casehub.eidos.api.GoalPriority.PRIMARY,
      io.casehub.eidos.api.Visibility.PUBLIC, List.of());

  var descriptor = io.casehub.eidos.api.AgentDescriptor.builder()
      .agentId("agent-1").name("agent-1").slot("default").tenancyId("tenant-1")
      .goals(List.of(goal)).build();

  var cap = new Capability("analysis", "", "", null);
  var worker = Worker.builder().name("w1").capabilityName("analysis").noFunction().build();
  // Capability exists but NO binding targets it
  var definition = CaseDefinition.builder()
      .namespace("test").name("test").version("1.0")
      .capabilities(cap).workers(worker)
      .decompositionStrategy("llm").build();
  setAgentDescriptors(definition, Map.of("w1", descriptor));

  var step = new GoalStep(UUID.randomUUID(), "Analyse", "analysis", java.time.Instant.now());

  @SuppressWarnings("unchecked")
  DecompositionStrategy<JsonNode> strategy = mock(DecompositionStrategy.class);
  when(strategy.decompose(any(), any())).thenReturn(DagPlan.singleton(step));
  when(strategyResolver.resolve(any(), anyString())).thenReturn(strategy);
  when(abandonmentEvaluator.activeGoals(any())).thenReturn(List.of(goal));
  when(planItemStore.findByCaseId(caseId, "tenant-1")).thenReturn(List.of());
  when(blackboardRegistry.getOrCreate(any(), anyString()))
      .thenReturn(mock(io.casehub.engine.planning.plan.CasePlanModel.class));

  var context = mock(MutableCaseContext.class);
  var layer = mock(WritableLayer.class);
  when(context.layer(ContextLayer.WORKING)).thenReturn(layer);
  when(layer.asJsonNode()).thenReturn(MAPPER.createObjectNode());

  decomposer.decompose(instance, definition, context);

  // No PlanItem saved, no compound registered
  verify(planItemStore, never()).save(any(), any());
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl planning -Dtest=DefaultGoalDecomposerTest -q`
Expected: `decomposesGoalStepsUsingBindingNames` FAILS (NPE from null executor or wrong binding name)

- [ ] **Step 4: Fix `DefaultGoalDecomposer.decomposeGoal()`**

Replace the validation loop and compound creation section of `decomposeGoal()` (lines 155-265). The key changes:

1. Validate by checking bindings exist (not just capability names)
2. Resolve binding names from `CaseDefinition.findBindingsByCapability()`
3. Use `BindingExecutorResolver.resolve()` for the Primitive executor
4. Use `binding.getName()` for scopedBindings and PlanItemSaveRequest

Replace the body from the `var validNodes` line (155) through the end of the method with:

```java
    var resolvedSteps = new java.util.ArrayList<ResolvedStep>();
    var skipped = new java.util.ArrayList<String>();
    for (var node : plan.nodes().values()) {
      if (!(node.task() instanceof GoalStep step)) continue;
      var bindings = definition.findBindingsByCapability(step.capabilityName());
      if (bindings.isEmpty()) {
        skipped.add(step.capabilityName());
        LOG.warnf("Decomposition step references capability '%s' with no binding — skipped",
            step.capabilityName());
        continue;
      }
      if (bindings.size() > 1) {
        LOG.warnf("Capability '%s' has %d bindings — using first ('%s'). "
            + "v1 limitation: one binding per capability in decomposed plans.",
            step.capabilityName(), bindings.size(), bindings.get(0).getName());
      }
      resolvedSteps.add(new ResolvedStep(node, step, bindings.get(0)));
    }

    if (resolvedSteps.isEmpty()) {
      if (definition.getPlanningConstraints() != null
          && definition.getPlanningConstraints().hasHardConstraints()) {
        var infeasibleLog = new EventLog();
        infeasibleLog.setCaseId(instance.getUuid());
        infeasibleLog.setEventType(CaseHubEventType.CONSTRAINTS_INFEASIBLE);
        infeasibleLog.setStreamType(EventStreamType.CASE);
        infeasibleLog.setTimestamp(Instant.now());
        var infeasibleMeta = OBJECT_MAPPER.createObjectNode();
        infeasibleMeta.put("goalName", goal.name());
        infeasibleMeta.put("strategyId", definition.getDecompositionStrategy());
        var pc = definition.getPlanningConstraints();
        if (pc.timeBudget() != null) infeasibleMeta.put("timeBudget", pc.timeBudget().toString());
        if (pc.resourceLimit() != null) infeasibleMeta.put("resourceLimit", pc.resourceLimit());
        if (!pc.costBudgets().isEmpty()) {
          infeasibleMeta.set("costBudgets", OBJECT_MAPPER.valueToTree(pc.costBudgets()));
        }
        infeasibleLog.setMetadata(infeasibleMeta);
        eventLogRepository.append(infeasibleLog, instance.tenancyId);
      }
      return;
    }

    var nodeList = resolvedSteps.stream()
        .map(r -> r.node)
        .collect(java.util.stream.Collectors.toList());
    if (!isLinearChain(nodeList)) {
      LOG.warnf("Decomposition produced non-linear plan for goal=%s — v1 supports sequential only",
          goal.name());
      return;
    }

    var availableSteps = new java.util.ArrayList<ResolvedStep>();
    for (var resolved : resolvedSteps) {
      var existing = scopedBindings.putIfAbsent(resolved.binding.getName(), goal.name());
      if (existing != null && !existing.equals(goal.name())) {
        LOG.warnf("Binding '%s' already scoped by goal '%s' — excluded from '%s'",
            resolved.binding.getName(), existing, goal.name());
      } else {
        availableSteps.add(resolved);
      }
    }

    if (availableSteps.isEmpty()) return;

    var compoundBuilder = PlanItemDefinition.Compound.builder(goal.name())
        .completion(CompletionSemantics.all())
        .dispatchMode(DispatchMode.CHOREOGRAPHED);

    for (int i = 0; i < availableSteps.size(); i++) {
      var resolved = availableSteps.get(i);
      var primitiveId = goal.name() + "-step-" + i;
      var executor = BindingExecutorResolver.resolve(resolved.binding, definition);
      compoundBuilder.child(
          new PlanItemDefinition.Primitive(primitiveId, resolved.step.description(), executor, null));
      compoundBuilder.binding(resolved.binding.getName());
    }

    var compound = compoundBuilder.build();
    casePlanModel.registerDefinition(compound);

    for (var resolved : availableSteps) {
      planItemStore.save(
          PlanItemSaveRequest.primitive(
              instance.getUuid(),
              resolved.step.id(),
              resolved.binding.getName(),
              TaskStatus.PENDING,
              Instant.now(),
              TargetType.CAPABILITY,
              null,
              instance.tenancyId,
              resolved.step.description(),
              null,
              null),
          instance.tenancyId);
    }

    var eventLog = new EventLog();
    eventLog.setCaseId(instance.getUuid());
    eventLog.setEventType(CaseHubEventType.GOAL_DECOMPOSED);
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());
    var meta = OBJECT_MAPPER.createObjectNode();
    meta.put("goalName", goal.name());
    meta.put("strategyId", definition.getDecompositionStrategy());
    meta.put("stepCount", availableSteps.size());
    if (!skipped.isEmpty()) {
      meta.set("skippedSteps", OBJECT_MAPPER.valueToTree(skipped));
    }
    eventLog.setMetadata(meta);
    eventLogRepository.append(eventLog, instance.tenancyId);
```

Add the `ResolvedStep` record and update imports at the top of the class:

```java
import io.casehub.engine.common.internal.routing.BindingExecutorResolver;

// Inside the class, after the LOG field:
private record ResolvedStep(
    DagNode<TaskNode.LeafTask<JsonNode>> node,
    GoalStep step,
    io.casehub.api.model.Binding binding) {}
```

Update `isLinearChain` signature to accept the generic node type:

```java
private boolean isLinearChain(List<DagNode<TaskNode.LeafTask<JsonNode>>> nodes) {
```

(No change needed — it already matches.)

Also update the `scopedBindings` type in `decompose()` from `ConcurrentHashMap<String, String>` — the key is now the binding name, not the capability name. No signature change needed, just semantic alignment (the comment should clarify).

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl planning -Dtest=DefaultGoalDecomposerTest -q`
Expected: all tests PASS (new and existing)

- [ ] **Step 6: Update the existing `emitsConstraintsInfeasibleWhenEmptyPlanWithHardConstraints` test**

The existing test creates a GoalStep with `capabilityName="nonexistent-capability"` and a capability named `"unknown-cap"`. With the fix, the validation now checks for bindings, not just capabilities. The test already has no binding for `"nonexistent-capability"`, so the step will be skipped and the infeasible event will still fire. Verify this test still passes as-is.

If it fails because the definition has no binding for `"unknown-cap"` either, add a binding:

```java
.bindings(Binding.builder().name("unknown-binding")
    .capability(new Capability("unknown-cap", "", "", null))
    .on(new io.casehub.api.model.ContextChangeTrigger(".x != null"))
    .build())
```

- [ ] **Step 7: Run full planning module test suite**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl planning -q`
Expected: all tests PASS

- [ ] **Step 8: Commit**

```
Refs #110: fix GoalDecomposer binding bridge — resolve capability to binding name
```

---

### Task 4: Run full build and update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (Goal Decomposition section)

**Interfaces:**
- Consumes: all previous tasks
- Produces: updated documentation, green build

- [ ] **Step 1: Run full project build**

Run: `mvn install -DskipTests -q && TESTCONTAINERS_RYUK_DISABLED=true mvn test -q`
Expected: full green build

- [ ] **Step 2: Update CLAUDE.md Goal Decomposition section**

Add after the existing `DefaultGoalDecomposer` description:

```markdown
`BindingExecutorResolver` (`common/internal/routing/`) — static utility resolving `Binding` → `ExecutorRef`. Shared between `PlanningStrategyLoopControl` (dispatch-time resolution) and `DefaultGoalDecomposer` (decomposition-time resolution). `ForwardReplanRevision` (plan adaptation) should use it for coherent resolution.
```

Add to the v1 constraints subsection:

```markdown
**v1 constraints:** Plans must be linear chains (sequential only). One binding per capability — when multiple bindings target the same capability, the first in `CaseDefinition.getBindings()` declaration order is selected (logged warning). Case definitions using LLM decomposition should not have mutually-exclusive trigger conditions across bindings targeting the same capability. The proper fix (capability-level scoping on Compound via `scopedCapabilities: Set<String>`) is deferred until a concrete multi-binding use case materializes.
```

- [ ] **Step 3: Commit**

```
Closes #110: goal decomposition binding bridge — docs and green build
```
