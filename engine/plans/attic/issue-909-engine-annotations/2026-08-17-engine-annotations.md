# Engine Annotations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #909 — casehub-engine-annotations module
**Issue group:** #909

**Goal:** Build a complete annotation-driven programming model for CaseHub case definitions, producing the same `CaseDefinition`, `Worker`, `Capability`, and `Binding` types as existing fluent builders — with GOAP/ADAPTIVE type inference for auto-wiring worker dependencies.

**Architecture:** New `casehub-engine-annotations` Quarkus extension module (deployment/runtime split). Annotation definitions live in `annotations/runtime`. Quarkus `@BuildStep` processor in `annotations/deployment` scans `@Case`-annotated interfaces via Jandex, validates, and generates synthetic CDI beans. GOAP type inference shared between build-time (Jandex) and runtime (reflection) via `GoapActionInferrer` in `casehub-engine-common`. New `GoapPlanningStrategy` and `AdaptivePlanningStrategy` implementations in `casehub-engine-planning` bridge `GoapPlanner` into the existing `CompoundStrategyDispatcher` pipeline.

**Tech Stack:** Java 21, Quarkus 3.32.2, Quarkus ARC (CDI), Jandex (build-time annotation index), Quarkus Gizmo (bytecode generation for synthetic subclasses), JUnit 5, AssertJ, Mockito

## Global Constraints

- Quarkus version `3.32.2` — all extension APIs must match this version
- Group ID `io.casehub`, version `0.2-SNAPSHOT`
- No LangChain4j dependency in the annotations module
- `ContextBridge` is internal — never exposed in user-facing APIs
- GoapAction name = Binding name = Worker/method name (1:1 identity)
- All expression strings route through `ExpressionEngineRegistry` — never hardcode `JQExpressionEvaluator`
- `-parameters` compiler flag required — build extension validates parameter names are not synthetic
- Use `ide_insert_member` / `ide_replace_member` for structural editing; bash only for non-code files

---

### Task 1: GoapAction API Widening

Widen `GoapAction.cost` from `int` to `double`, add `benefit` field, add `softPreconditions`, add `effectiveCost()` method. Update `GoapPlanner` for compound goals and benefit formula. Update `GoapWorldState` with `satisfiesAll`.

**Files:**
- Modify: `api/src/main/java/io/casehub/engine/plan/goap/GoapAction.java`
- Modify: `api/src/main/java/io/casehub/engine/plan/goap/GoapPlanner.java`
- Modify: `api/src/main/java/io/casehub/engine/plan/goap/GoapWorldState.java`
- Create: `api/src/test/java/io/casehub/engine/plan/goap/GoapActionTest.java`
- Create: `api/src/test/java/io/casehub/engine/plan/goap/GoapPlannerTest.java`
- Create: `api/src/test/java/io/casehub/engine/plan/goap/GoapWorldStateTest.java`

**Interfaces:**
- Produces: `GoapAction(String name, Map<String, Boolean> preconditions, Map<String, Boolean> effects, double cost, double benefit, Map<String, Boolean> softPreconditions)` — widened record
- Produces: `GoapAction.effectiveCost() -> double` — `cost * (1.0 - benefit)`
- Produces: `GoapWorldState.satisfiesAll(Set<String>) -> boolean`
- Produces: `GoapPlanner.plan(GoapWorldState, Set<String>, List<GoapAction>) -> List<GoapAction>` — compound-goal overload

- [ ] **Step 1: Write GoapAction widening tests**

```java
package io.casehub.engine.plan.goap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GoapActionTest {

  @Test
  void effectiveCost_no_benefit() {
    var action = new GoapAction("a", Map.of(), Map.of("x", true), 0.5, 0.0, Map.of());
    assertThat(action.effectiveCost()).isEqualTo(0.5);
  }

  @Test
  void effectiveCost_with_benefit() {
    var action = new GoapAction("a", Map.of(), Map.of("x", true), 0.8, 0.5, Map.of());
    assertThat(action.effectiveCost()).isEqualTo(0.4);
  }

  @Test
  void effectiveCost_full_benefit() {
    var action = new GoapAction("a", Map.of(), Map.of("x", true), 0.8, 1.0, Map.of());
    assertThat(action.effectiveCost()).isEqualTo(0.0);
  }

  @Test
  void effectiveCost_defaults_zero() {
    var action = new GoapAction("a", Map.of(), Map.of("x", true), 0.0, 0.0, Map.of());
    assertThat(action.effectiveCost()).isEqualTo(0.0);
  }

  @Test
  void isApplicable_ignores_soft_preconditions() {
    var action = new GoapAction("a",
        Map.of("hard", true),
        Map.of("result", true),
        0.5, 0.0,
        Map.of("soft", true));
    var state = new GoapWorldState(Map.of("hard", true));
    assertThat(action.isApplicable(state)).isTrue();
  }

  @Test
  void isApplicable_requires_hard_preconditions() {
    var action = new GoapAction("a",
        Map.of("hard", true),
        Map.of("result", true),
        0.5, 0.0, Map.of());
    var state = new GoapWorldState(Map.of());
    assertThat(action.isApplicable(state)).isFalse();
  }

  @Test
  void backward_compat_constructor() {
    var action = new GoapAction("a", Map.of(), Map.of("x", true), 0.5);
    assertThat(action.benefit()).isEqualTo(0.0);
    assertThat(action.softPreconditions()).isEmpty();
    assertThat(action.effectiveCost()).isEqualTo(0.5);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `/opt/homebrew/bin/mvn test -pl api -Dtest=GoapActionTest -q`
Expected: FAIL — constructor signature mismatch (int cost, missing benefit/softPreconditions)

- [ ] **Step 3: Widen GoapAction record**

Replace `GoapAction` with:

```java
public record GoapAction(
    String name,
    Map<String, Boolean> preconditions,
    Map<String, Boolean> effects,
    double cost,
    double benefit,
    Map<String, Boolean> softPreconditions) {

  public GoapAction {
    preconditions = Map.copyOf(preconditions);
    effects = Map.copyOf(effects);
    softPreconditions = Map.copyOf(softPreconditions);
    if (cost < 0.0 || cost > 1.0) throw new IllegalArgumentException("cost must be in [0.0, 1.0]");
    if (benefit < 0.0 || benefit > 1.0)
      throw new IllegalArgumentException("benefit must be in [0.0, 1.0]");
  }

  public GoapAction(
      String name, Map<String, Boolean> preconditions, Map<String, Boolean> effects, double cost) {
    this(name, preconditions, effects, cost, 0.0, Map.of());
  }

  public double effectiveCost() {
    return cost * (1.0 - benefit);
  }

  public boolean isApplicable(GoapWorldState state) {
    return preconditions.entrySet().stream().allMatch(e -> state.get(e.getKey()) == e.getValue());
  }

  public GoapWorldState applyTo(GoapWorldState state) {
    GoapWorldState result = state;
    for (var entry : effects.entrySet()) {
      result = result.with(entry.getKey(), entry.getValue());
    }
    return result;
  }
}
```

- [ ] **Step 4: Run GoapAction tests to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl api -Dtest=GoapActionTest -q`
Expected: PASS

- [ ] **Step 5: Write GoapWorldState.satisfiesAll test**

```java
package io.casehub.engine.plan.goap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoapWorldStateTest {

  @Test
  void satisfiesAll_all_present() {
    var state = new GoapWorldState(Map.of("a", true, "b", true, "c", true));
    assertThat(state.satisfiesAll(Set.of("a", "b"))).isTrue();
  }

  @Test
  void satisfiesAll_one_missing() {
    var state = new GoapWorldState(Map.of("a", true));
    assertThat(state.satisfiesAll(Set.of("a", "b"))).isFalse();
  }

  @Test
  void satisfiesAll_empty_goals() {
    var state = new GoapWorldState(Map.of());
    assertThat(state.satisfiesAll(Set.of())).isTrue();
  }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `/opt/homebrew/bin/mvn test -pl api -Dtest=GoapWorldStateTest -q`
Expected: FAIL — `satisfiesAll` method does not exist

- [ ] **Step 7: Add satisfiesAll to GoapWorldState**

Add method to `GoapWorldState`:

```java
public boolean satisfiesAll(Set<String> goalConditions) {
  return goalConditions.stream().allMatch(this::satisfies);
}
```

- [ ] **Step 8: Run GoapWorldState tests to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl api -Dtest=GoapWorldStateTest -q`
Expected: PASS

- [ ] **Step 9: Write GoapPlanner compound goals + benefit formula tests**

```java
package io.casehub.engine.plan.goap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoapPlannerTest {

  private final GoapPlanner planner = new GoapPlanner();

  @Test
  void plan_compound_goals() {
    var a1 = new GoapAction("a1", Map.of(), Map.of("x", true), 0.3);
    var a2 = new GoapAction("a2", Map.of(), Map.of("y", true), 0.5);
    var initial = new GoapWorldState(Map.of());

    List<GoapAction> plan = planner.plan(initial, Set.of("x", "y"), List.of(a1, a2));
    assertThat(plan).extracting(GoapAction::name).containsExactlyInAnyOrder("a1", "a2");
  }

  @Test
  void plan_compound_goals_already_satisfied() {
    var a1 = new GoapAction("a1", Map.of(), Map.of("x", true), 0.3);
    var initial = new GoapWorldState(Map.of("x", true, "y", true));

    List<GoapAction> plan = planner.plan(initial, Set.of("x", "y"), List.of(a1));
    assertThat(plan).isEmpty();
  }

  @Test
  void plan_prefers_lower_effective_cost() {
    var cheap = new GoapAction("cheap", Map.of(), Map.of("x", true), 0.3, 0.5, Map.of());
    var expensive = new GoapAction("expensive", Map.of(), Map.of("x", true), 0.8, 0.0, Map.of());
    var initial = new GoapWorldState(Map.of());

    List<GoapAction> plan = planner.plan(initial, Set.of("x"), List.of(cheap, expensive));
    assertThat(plan).extracting(GoapAction::name).containsExactly("cheap");
  }

  @Test
  void plan_chains_dependencies() {
    var a1 = new GoapAction("a1", Map.of(), Map.of("x", true), 0.3);
    var a2 = new GoapAction("a2", Map.of("x", true), Map.of("y", true), 0.5);
    var initial = new GoapWorldState(Map.of());

    List<GoapAction> plan = planner.plan(initial, Set.of("y"), List.of(a1, a2));
    assertThat(plan).extracting(GoapAction::name).containsExactly("a1", "a2");
  }

  @Test
  void plan_soft_precondition_penalty() {
    var withSoft = new GoapAction("withSoft", Map.of(), Map.of("x", true),
        0.5, 0.0, Map.of("optional", true));
    var withoutSoft = new GoapAction("withoutSoft", Map.of(), Map.of("x", true),
        0.5, 0.0, Map.of());
    var initial = new GoapWorldState(Map.of());

    List<GoapAction> plan = planner.plan(initial, Set.of("x"), List.of(withSoft, withoutSoft));
    assertThat(plan).extracting(GoapAction::name).containsExactly("withoutSoft");
  }

  @Test
  void plan_single_goal_backward_compat() {
    var a1 = new GoapAction("a1", Map.of(), Map.of("x", true), 0.3);
    var initial = new GoapWorldState(Map.of());

    List<GoapAction> plan = planner.plan(initial, "x", List.of(a1));
    assertThat(plan).extracting(GoapAction::name).containsExactly("a1");
  }
}
```

- [ ] **Step 10: Run test to verify they fail**

Run: `/opt/homebrew/bin/mvn test -pl api -Dtest=GoapPlannerTest -q`
Expected: FAIL — `plan(GoapWorldState, Set<String>, List<GoapAction>)` does not exist

- [ ] **Step 11: Update GoapPlanner for compound goals + benefit + soft penalty**

Replace `GoapPlanner` with:

```java
public class GoapPlanner {

  public List<GoapAction> plan(
      GoapWorldState initial, String goalCondition, List<GoapAction> actions) {
    return plan(initial, Set.of(goalCondition), actions);
  }

  public List<GoapAction> plan(
      GoapWorldState initial, Set<String> goalConditions, List<GoapAction> actions) {
    if (goalConditions.isEmpty() || initial.satisfiesAll(goalConditions)) return List.of();

    record Node(GoapWorldState state, List<GoapAction> plan, double cost) {}

    PriorityQueue<Node> open =
        new PriorityQueue<>(
            Comparator.comparingDouble(
                n -> n.cost() + heuristic(n.state(), goalConditions)));
    open.add(new Node(initial, List.of(), 0.0));

    Set<Map<String, Boolean>> visited = new HashSet<>();

    while (!open.isEmpty()) {
      Node current = open.poll();
      if (current.state().satisfiesAll(goalConditions)) return current.plan();
      if (!visited.add(current.state().conditions())) continue;

      for (GoapAction action : actions) {
        if (action.isApplicable(current.state())) {
          GoapWorldState next = action.applyTo(current.state());
          List<GoapAction> newPlan = new ArrayList<>(current.plan());
          newPlan.add(action);
          double softPenalty = softPenalty(action, current.state());
          open.add(new Node(next, newPlan, current.cost() + action.effectiveCost() + softPenalty));
        }
      }
    }
    return List.of();
  }

  private double softPenalty(GoapAction action, GoapWorldState state) {
    long unsatisfied = action.softPreconditions().entrySet().stream()
        .filter(e -> state.get(e.getKey()) != e.getValue())
        .count();
    if (unsatisfied == 0) return 0.0;
    return Math.max(0.5 * action.cost(), 0.1);
  }

  private double heuristic(GoapWorldState state, Set<String> goalConditions) {
    return goalConditions.stream().filter(c -> !state.satisfies(c)).count();
  }
}
```

- [ ] **Step 12: Run all GOAP tests to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl api -Dtest="GoapActionTest,GoapPlannerTest,GoapWorldStateTest" -q`
Expected: PASS

- [ ] **Step 13: Fix any compile errors in other modules referencing GoapAction(int cost)**

Run: `/opt/homebrew/bin/mvn compile -q`
Expected: PASS (backward-compat constructor handles old call sites)

- [ ] **Step 14: Commit**

```bash
git add api/src/main/java/io/casehub/engine/plan/goap/
git add api/src/test/java/io/casehub/engine/plan/goap/
git commit -m "feat(#909): widen GoapAction API — double cost/benefit, compound goals, soft preconditions

Refs #909"
```

---

### Task 2: GoapKeyConvention Utility

Deterministic type→context key mapping shared between build-time and runtime GOAP inference.

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/goap/GoapKeyConvention.java`
- Create: `common/src/test/java/io/casehub/engine/common/goap/GoapKeyConventionTest.java`

**Interfaces:**
- Produces: `GoapKeyConvention.keyFor(String simpleTypeName) -> String`
- Produces: `GoapKeyConvention.keyForParameterized(String containerName, String elementName) -> String`
- Produces: `GoapKeyConvention.detectCollisions(Map<String, String> keyToProducer) -> List<String>` — returns error messages

- [ ] **Step 1: Write GoapKeyConvention tests**

```java
package io.casehub.engine.common.goap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GoapKeyConventionTest {

  @Test
  void simple_type() {
    assertThat(GoapKeyConvention.keyFor("AnalysisResult")).isEqualTo("analysisResult");
  }

  @Test
  void single_char() {
    assertThat(GoapKeyConvention.keyFor("X")).isEqualTo("x");
  }

  @Test
  void already_camelCase() {
    assertThat(GoapKeyConvention.keyFor("riskAssessment")).isEqualTo("riskAssessment");
  }

  @Test
  void parameterized_list() {
    assertThat(GoapKeyConvention.keyForParameterized("List", "Clause")).isEqualTo("clauseList");
  }

  @Test
  void parameterized_set() {
    assertThat(GoapKeyConvention.keyForParameterized("Set", "Tag")).isEqualTo("tagSet");
  }

  @Test
  void parameterized_map() {
    assertThat(GoapKeyConvention.keyForParameterized("Map", "String")).isEqualTo("stringMap");
  }

  @Test
  void collision_detection() {
    Map<String, String> keys = Map.of(
        "analysisResult", "analyse",
        "clauseList", "extractClauses");
    assertThat(GoapKeyConvention.detectCollisions(keys)).isEmpty();
  }

  @Test
  void collision_detected() {
    var keys = new java.util.LinkedHashMap<String, String>();
    keys.put("stringList", "extractTags");
    var errors = GoapKeyConvention.detectCollision("stringList", "extractErrors", keys);
    assertThat(errors).isNotEmpty();
    assertThat(errors).contains("extractTags");
    assertThat(errors).contains("extractErrors");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `/opt/homebrew/bin/mvn test -pl common -Dtest=GoapKeyConventionTest -q`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement GoapKeyConvention**

```java
package io.casehub.engine.common.goap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GoapKeyConvention {

  private GoapKeyConvention() {}

  public static String keyFor(String simpleTypeName) {
    if (simpleTypeName == null || simpleTypeName.isEmpty()) {
      throw new IllegalArgumentException("Type name must not be null or empty");
    }
    return Character.toLowerCase(simpleTypeName.charAt(0)) + simpleTypeName.substring(1);
  }

  public static String keyForParameterized(String containerName, String elementName) {
    String elementKey = keyFor(elementName);
    String containerLower = containerName.toLowerCase();
    return elementKey + Character.toUpperCase(containerLower.charAt(0)) + containerLower.substring(1);
  }

  public static List<String> detectCollisions(Map<String, String> keyToProducer) {
    return List.of();
  }

  public static String detectCollision(
      String key, String newProducer, Map<String, String> existingKeyToProducer) {
    String existing = existingKeyToProducer.get(key);
    if (existing != null && !existing.equals(newProducer)) {
      return "Workers '" + existing + "' and '" + newProducer
          + "' both produce key '" + key + "' — add @Effect to disambiguate";
    }
    return null;
  }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl common -Dtest=GoapKeyConventionTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/io/casehub/engine/common/goap/
git add common/src/test/java/io/casehub/engine/common/goap/
git commit -m "feat(#909): add GoapKeyConvention — deterministic type→context key mapping

Refs #909"
```

---

### Task 3: GoapActionInferrer

Shared utility for type-based GOAP action inference. Used by the build extension (Jandex types) and at runtime (Class<?> from WorkerFunction).

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/goap/GoapActionInferrer.java`
- Create: `common/src/test/java/io/casehub/engine/common/goap/GoapActionInferrerTest.java`

**Interfaces:**
- Consumes: `GoapKeyConvention` (Task 2)
- Consumes: `GoapAction` (Task 1)
- Produces: `GoapActionInferrer.infer(String name, List<Class<?>> inputTypes, Class<?> outputType, double cost, double benefit, Set<Class<?>> softDependencyTypes) -> GoapAction`
- Produces: `GoapActionInferrer.isInputParameter(Class<?>) -> boolean` — true for String, primitives, Map, WorkerScope

- [ ] **Step 1: Write GoapActionInferrer tests**

```java
package io.casehub.engine.common.goap;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.plan.goap.GoapAction;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoapActionInferrerTest {

  @Test
  void infer_simple_action() {
    GoapAction action = GoapActionInferrer.infer(
        "analyse",
        List.of(String.class),
        AnalysisResult.class,
        0.3, 0.0, Set.of());

    assertThat(action.name()).isEqualTo("analyse");
    assertThat(action.preconditions()).isEmpty();
    assertThat(action.effects()).containsEntry("analysisResult", true);
    assertThat(action.cost()).isEqualTo(0.3);
  }

  @Test
  void infer_with_dependency() {
    GoapAction action = GoapActionInferrer.infer(
        "assess",
        List.of(AnalysisResult.class, String.class),
        RiskAssessment.class,
        0.5, 0.0, Set.of());

    assertThat(action.preconditions()).containsEntry("analysisResult", true);
    assertThat(action.preconditions()).doesNotContainKey("string");
    assertThat(action.effects()).containsEntry("riskAssessment", true);
  }

  @Test
  void infer_soft_dependency() {
    GoapAction action = GoapActionInferrer.infer(
        "assess",
        List.of(AnalysisResult.class),
        RiskAssessment.class,
        0.5, 0.0,
        Set.of(AnalysisResult.class));

    assertThat(action.preconditions()).isEmpty();
    assertThat(action.softPreconditions()).containsEntry("analysisResult", true);
  }

  @Test
  void isInputParameter_string() {
    assertThat(GoapActionInferrer.isInputParameter(String.class)).isTrue();
  }

  @Test
  void isInputParameter_int() {
    assertThat(GoapActionInferrer.isInputParameter(int.class)).isTrue();
  }

  @Test
  void isInputParameter_map() {
    assertThat(GoapActionInferrer.isInputParameter(Map.class)).isTrue();
  }

  @Test
  void isInputParameter_domain_type() {
    assertThat(GoapActionInferrer.isInputParameter(AnalysisResult.class)).isFalse();
  }

  @Test
  void infer_with_benefit() {
    GoapAction action = GoapActionInferrer.infer(
        "a", List.of(), AnalysisResult.class, 0.5, 0.8, Set.of());
    assertThat(action.benefit()).isEqualTo(0.8);
    assertThat(action.effectiveCost()).isCloseTo(0.1, org.assertj.core.api.Assertions.within(0.001));
  }

  record AnalysisResult(String summary) {}
  record RiskAssessment(String level) {}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `/opt/homebrew/bin/mvn test -pl common -Dtest=GoapActionInferrerTest -q`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement GoapActionInferrer**

```java
package io.casehub.engine.common.goap;

import io.casehub.engine.plan.goap.GoapAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GoapActionInferrer {

  private static final Set<Class<?>> INPUT_PARAMETER_TYPES = Set.of(
      String.class, int.class, Integer.class, long.class, Long.class,
      double.class, Double.class, float.class, Float.class,
      boolean.class, Boolean.class, byte.class, Byte.class,
      short.class, Short.class, char.class, Character.class, Map.class);

  private static final Set<String> INPUT_PARAMETER_TYPE_NAMES = Set.of(
      "io.casehub.worker.api.WorkerScope");

  private GoapActionInferrer() {}

  public static boolean isInputParameter(Class<?> type) {
    return type.isPrimitive() || INPUT_PARAMETER_TYPES.contains(type)
        || INPUT_PARAMETER_TYPE_NAMES.contains(type.getName());
  }

  public static GoapAction infer(
      String name,
      List<Class<?>> inputTypes,
      Class<?> outputType,
      double cost,
      double benefit,
      Set<Class<?>> softDependencyTypes) {

    Map<String, Boolean> preconditions = new HashMap<>();
    Map<String, Boolean> softPreconditions = new HashMap<>();

    for (Class<?> inputType : inputTypes) {
      if (isInputParameter(inputType)) continue;
      String key = GoapKeyConvention.keyFor(inputType.getSimpleName());
      if (softDependencyTypes.contains(inputType)) {
        softPreconditions.put(key, true);
      } else {
        preconditions.put(key, true);
      }
    }

    Map<String, Boolean> effects = new HashMap<>();
    if (outputType != null && outputType != void.class && outputType != Void.class) {
      String effectKey = GoapKeyConvention.keyFor(outputType.getSimpleName());
      effects.put(effectKey, true);
    }

    return new GoapAction(name, preconditions, effects, cost, benefit, softPreconditions);
  }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl common -Dtest=GoapActionInferrerTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/io/casehub/engine/common/goap/
git add common/src/test/java/io/casehub/engine/common/goap/
git commit -m "feat(#909): add GoapActionInferrer — type-based GOAP action inference

Refs #909"
```

---

### Task 4: CaseDefinition GOAP Fields

Add `goapActions` and `goalToEffectKeys` fields to `CaseDefinition` and its builder.

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/CaseDefinition.java`

**Interfaces:**
- Consumes: `GoapAction` (Task 1)
- Produces: `CaseDefinition.getGoapActions() -> List<GoapAction>`
- Produces: `CaseDefinition.getGoalToEffectKeys() -> Map<String, Set<String>>`
- Produces: `CaseDefinition.Builder.goapActions(List<GoapAction>) -> Builder`
- Produces: `CaseDefinition.Builder.goalToEffectKey(String goalName, Set<String> effectKeys) -> Builder`

- [ ] **Step 1: Add fields and builder methods to CaseDefinition**

Add fields after line 83 (`channels`):

```java
private List<GoapAction> goapActions;
private Map<String, Set<String>> goalToEffectKeys;
```

Add getters:

```java
public List<GoapAction> getGoapActions() {
  return goapActions != null ? goapActions : List.of();
}

public void setGoapActions(List<GoapAction> goapActions) {
  this.goapActions = goapActions != null ? List.copyOf(goapActions) : null;
}

public Map<String, Set<String>> getGoalToEffectKeys() {
  return goalToEffectKeys != null ? goalToEffectKeys : Map.of();
}

public void setGoalToEffectKeys(Map<String, Set<String>> goalToEffectKeys) {
  this.goalToEffectKeys = goalToEffectKeys;
}
```

Add builder fields and methods:

```java
private List<GoapAction> goapActions;
private Map<String, Set<String>> goalToEffectKeys = new HashMap<>();

public Builder goapActions(List<GoapAction> goapActions) {
  this.goapActions = goapActions;
  return this;
}

public Builder goalToEffectKey(String goalName, Set<String> effectKeys) {
  this.goalToEffectKeys.put(goalName, Set.copyOf(effectKeys));
  return this;
}
```

In `build()`, after existing field copies:

```java
def.setGoapActions(this.goapActions);
if (!this.goalToEffectKeys.isEmpty()) {
  def.setGoalToEffectKeys(Map.copyOf(this.goalToEffectKeys));
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `/opt/homebrew/bin/mvn compile -pl api -q`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/io/casehub/api/model/CaseDefinition.java
git commit -m "feat(#909): add goapActions and goalToEffectKeys to CaseDefinition

Refs #909"
```

---

### Task 5: Annotation Definitions (runtime module)

Create the annotations module with all annotation types. Pure annotations — no logic, no build extension.

**Files:**
- Create: `annotations/pom.xml` (aggregator)
- Create: `annotations/runtime/pom.xml`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Case.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Worker.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Capability.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Bind.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Bindings.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Goal.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Milestone.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Completion.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Completions.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Customize.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Customizers.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/SystemPrompt.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Param.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/Effect.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/SoftDependency.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/PlanningMode.java`
- Modify: `pom.xml` (root — add `annotations` module)

**Interfaces:**
- Produces: All annotation types for use by the build extension and consumers

- [ ] **Step 1: Create annotations/pom.xml (aggregator)**

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

    <artifactId>casehub-engine-annotations-parent</artifactId>
    <packaging>pom</packaging>
    <name>Case Hub :: Engine Annotations</name>

    <modules>
        <module>runtime</module>
    </modules>
</project>
```

- [ ] **Step 2: Create annotations/runtime/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-engine-annotations-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-engine-annotations</artifactId>
    <name>Case Hub :: Engine Annotations :: Runtime</name>
    <description>Annotation-driven case definition model for CaseHub engine</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-worker-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Create PlanningMode enum**

```java
package io.casehub.engine.annotations;

public enum PlanningMode {
  EXPLICIT,
  GOAP,
  ADAPTIVE
}
```

- [ ] **Step 4: Create all annotation types**

Create each annotation file. Key examples:

**Case.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Case {
  String namespace();
  String name();
  String version() default "1.0.0";
  String title() default "";
  String summary() default "";
  PlanningMode planning() default PlanningMode.EXPLICIT;
}
```

**Worker.java:**
```java
package io.casehub.engine.annotations;

import io.casehub.api.model.ExecutionMode;
import io.casehub.api.model.LifecycleScope;
import io.casehub.api.model.Participation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Worker {
  String value() default "";
  String capability() default "";
  String[] capabilities() default {};
  String description() default "";
  double cost() default 0.0;
  double benefit() default 0.0;
  int timeoutMs() default 0;
  int maxRetries() default -1;
  LifecycleScope scope() default LifecycleScope.BINDING;
  Participation participation() default Participation.PARTICIPANT;
  ExecutionMode executionMode() default ExecutionMode.TRANSIENT;
}
```

**Bind.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Bindings.class)
public @interface Bind {
  String capability() default "";
  String contextChange() default "";
  String event() default "";
  String cron() default "";
  boolean scopeActivated() default false;
  String listenLayer() default "";
  String when() default "";
  String conflictStrategy() default "";
  String[] producedKeys() default {};
}
```

**Bindings.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Bindings {
  Bind[] value();
}
```

**Goal.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Goal {
  String value();
  String condition() default "";
  String kind() default "SUCCESS";
}
```

**Milestone.java:**
```java
package io.casehub.engine.annotations;

import io.casehub.api.model.SlaStartFrom;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Milestone {
  String name();
  String entryCriteria() default "";
  String completionCriteria() default "";
  String slaDuration() default "";
  SlaStartFrom slaStartFrom() default SlaStartFrom.MILESTONE_ACTIVATED;
}
```

**Completion.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Completions.class)
public @interface Completion {
  String kind() default "SUCCESS";
}
```

**Completions.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Completions {
  Completion[] value();
}
```

**Customize.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Customizers.class)
public @interface Customize {
  String value() default "";
}
```

**Customizers.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Customizers {
  Customize[] value();
}
```

**SystemPrompt.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SystemPrompt {
  String value();
}
```

**Param.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
  String value();
}
```

**Effect.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Effect {
  String value();
}
```

**SoftDependency.java:**
```java
package io.casehub.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SoftDependency {}
```

- [ ] **Step 5: Add `annotations` module to root pom.xml**

Add `<module>annotations</module>` to the `<modules>` section in root `pom.xml`.

- [ ] **Step 6: Build to verify compilation**

Run: `/opt/homebrew/bin/mvn compile -pl annotations/runtime -q`
Expected: PASS

- [ ] **Step 7: Write annotation reflection tests**

Create `annotations/runtime/src/test/java/io/casehub/engine/annotations/AnnotationPresenceTest.java`:

```java
package io.casehub.engine.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

class AnnotationPresenceTest {

  @Test
  void case_has_runtime_retention() {
    assertThat(Case.class.getAnnotation(Retention.class).value())
        .isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void bind_is_repeatable() {
    assertThat(Bind.class.getAnnotation(Repeatable.class)).isNotNull();
    assertThat(Bind.class.getAnnotation(Repeatable.class).value()).isEqualTo(Bindings.class);
  }

  @Test
  void completion_is_repeatable() {
    assertThat(Completion.class.getAnnotation(Repeatable.class)).isNotNull();
    assertThat(Completion.class.getAnnotation(Repeatable.class).value()).isEqualTo(Completions.class);
  }

  @Test
  void customize_is_repeatable() {
    assertThat(Customize.class.getAnnotation(Repeatable.class)).isNotNull();
    assertThat(Customize.class.getAnnotation(Repeatable.class).value()).isEqualTo(Customizers.class);
  }

  @Test
  void planning_mode_has_three_values() {
    assertThat(PlanningMode.values()).containsExactly(
        PlanningMode.EXPLICIT, PlanningMode.GOAP, PlanningMode.ADAPTIVE);
  }

  @Test
  void worker_defaults() throws NoSuchMethodException {
    assertThat(Worker.class.getMethod("cost").getDefaultValue()).isEqualTo(0.0);
    assertThat(Worker.class.getMethod("benefit").getDefaultValue()).isEqualTo(0.0);
    assertThat(Worker.class.getMethod("maxRetries").getDefaultValue()).isEqualTo(-1);
  }
}
```

- [ ] **Step 8: Run tests**

Run: `/opt/homebrew/bin/mvn test -pl annotations/runtime -q`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add annotations/
git add pom.xml
git commit -m "feat(#909): add casehub-engine-annotations module with all annotation types

Refs #909"
```

---

### Task 6: GoapPlanningStrategy

Bridge `GoapPlanner` into the existing `CompoundStrategyDispatcher` pipeline as a `PlanningStrategy` implementation.

**Files:**
- Create: `planning/src/main/java/io/casehub/engine/planning/control/GoapPlanningStrategy.java`
- Create: `planning/src/test/java/io/casehub/engine/planning/control/GoapPlanningStrategyTest.java`

**Interfaces:**
- Consumes: `GoapPlanner`, `GoapAction`, `GoapWorldState` (Task 1)
- Consumes: `PlanningStrategy` interface
- Consumes: `CaseDefinition.getGoapActions()`, `CaseDefinition.getGoalToEffectKeys()` (Task 4)
- Produces: `GoapPlanningStrategy implements PlanningStrategy` with `id() = "goap"`

- [ ] **Step 1: Write GoapPlanningStrategy tests**

```java
package io.casehub.engine.planning.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.api.context.CaseContext;
import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.CapabilityTarget;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.engine.plan.goap.GoapAction;
import io.casehub.engine.planning.plan.DefaultCasePlanModel;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.worker.api.Capability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoapPlanningStrategyTest {

  private final GoapPlanningStrategy strategy = new GoapPlanningStrategy();

  @Test
  void id_is_goap() {
    assertThat(strategy.id()).isEqualTo("goap");
  }

  @Test
  void selects_first_planned_action() {
    var a1 = new GoapAction("analyse", Map.of(), Map.of("analysisResult", true), 0.3);
    var a2 = new GoapAction("assess", Map.of("analysisResult", true), Map.of("riskAssessment", true), 0.5);

    var definition = mock(CaseDefinition.class);
    when(definition.getGoapActions()).thenReturn(List.of(a1, a2));
    when(definition.getGoalToEffectKeys()).thenReturn(Map.of("goal", Set.of("riskAssessment")));

    var context = mock(CaseContext.class);
    when(context.layer("working")).thenReturn(mock(io.casehub.api.context.ContextLayer.class));

    var cap1 = Capability.builder().name("analyse").inputSchema(".").outputSchema(".").build();
    var cap2 = Capability.builder().name("assess").inputSchema(".").outputSchema(".").build();

    var b1 = Binding.builder().name("analyse").capability(cap1)
        .on(new ContextChangeTrigger("true")).build();
    var b2 = Binding.builder().name("assess").capability(cap2)
        .on(new ContextChangeTrigger("true")).build();

    var plan = new DefaultCasePlanModel(UUID.randomUUID());
    var pec = new PlanExecutionContext(
        UUID.randomUUID(), definition, context, CaseStatus.RUNNING,
        TenancyConstants.DEFAULT_TENANT_ID, List.of(), null, null);

    List<Binding> result = strategy.select(plan, pec, List.of(b1, b2));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("analyse");
  }

  @Test
  void returns_empty_when_no_eligible() {
    var definition = mock(CaseDefinition.class);
    when(definition.getGoapActions()).thenReturn(List.of());
    when(definition.getGoalToEffectKeys()).thenReturn(Map.of());

    var plan = new DefaultCasePlanModel(UUID.randomUUID());
    var pec = new PlanExecutionContext(
        UUID.randomUUID(), definition, mock(CaseContext.class), CaseStatus.RUNNING,
        TenancyConstants.DEFAULT_TENANT_ID, List.of(), null, null);

    List<Binding> result = strategy.select(plan, pec, List.of());
    assertThat(result).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/opt/homebrew/bin/mvn test -pl planning -Dtest=GoapPlanningStrategyTest -q`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement GoapPlanningStrategy**

```java
package io.casehub.engine.planning.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.plan.goap.GoapAction;
import io.casehub.engine.plan.goap.GoapPlanner;
import io.casehub.engine.plan.goap.GoapWorldState;
import io.casehub.engine.planning.plan.CasePlanModel;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Unremovable
public class GoapPlanningStrategy implements PlanningStrategy {

  private final GoapPlanner planner = new GoapPlanner();

  @Override
  public String id() {
    return "goap";
  }

  @Override
  public String getName() {
    return "GOAP Planning Strategy";
  }

  @Override
  public List<Binding> select(
      CasePlanModel plan, PlanExecutionContext context, List<Binding> eligible) {
    if (eligible.isEmpty()) return List.of();

    CaseDefinition definition = context.definition();
    List<GoapAction> allActions = definition.getGoapActions();
    if (allActions.isEmpty()) return List.of();

    Set<String> eligibleNames = eligible.stream()
        .map(Binding::getName)
        .collect(Collectors.toSet());

    List<GoapAction> filteredActions = allActions.stream()
        .filter(a -> eligibleNames.contains(a.name()))
        .toList();

    if (filteredActions.isEmpty()) return List.of();

    GoapWorldState worldState = buildWorldState(context);
    Set<String> goalConditions = resolveGoalConditions(definition);

    if (goalConditions.isEmpty() || worldState.satisfiesAll(goalConditions)) return List.of();

    List<GoapAction> planned = planner.plan(worldState, goalConditions, filteredActions);
    if (planned.isEmpty()) return List.of();

    String nextActionName = planned.get(0).name();
    return eligible.stream()
        .filter(b -> b.getName().equals(nextActionName))
        .toList();
  }

  protected GoapWorldState buildWorldState(PlanExecutionContext context) {
    Map<String, Boolean> conditions = new HashMap<>();
    var caseContext = context.caseContext();
    if (caseContext != null) {
      var workingLayer = caseContext.layer("working");
      if (workingLayer != null) {
        for (String key : workingLayer.keySet()) {
          conditions.put(key, true);
        }
      }
    }
    return new GoapWorldState(conditions);
  }

  protected Set<String> resolveGoalConditions(CaseDefinition definition) {
    Map<String, Set<String>> mapping = definition.getGoalToEffectKeys();
    Set<String> allEffectKeys = new HashSet<>();
    for (Set<String> effectKeys : mapping.values()) {
      allEffectKeys.addAll(effectKeys);
    }
    return allEffectKeys;
  }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl planning -Dtest=GoapPlanningStrategyTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add planning/src/main/java/io/casehub/engine/planning/control/GoapPlanningStrategy.java
git add planning/src/test/java/io/casehub/engine/planning/control/GoapPlanningStrategyTest.java
git commit -m "feat(#909): add GoapPlanningStrategy — bridges GoapPlanner into CompoundStrategyDispatcher

Refs #909"
```

---

### Task 7: AdaptivePlanningStrategy

Extends `GoapPlanningStrategy` with per-step replanning, execution tracking, stagnation detection, and max replan limit.

**Files:**
- Create: `planning/src/main/java/io/casehub/engine/planning/control/AdaptivePlanningStrategy.java`
- Create: `planning/src/test/java/io/casehub/engine/planning/control/AdaptivePlanningStrategyTest.java`

**Interfaces:**
- Consumes: `GoapPlanningStrategy` (Task 6)
- Produces: `AdaptivePlanningStrategy implements PlanningStrategy` with `id() = "adaptive"`

- [ ] **Step 1: Write AdaptivePlanningStrategy tests**

```java
package io.casehub.engine.planning.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.api.context.CaseContext;
import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.engine.plan.goap.GoapAction;
import io.casehub.engine.planning.plan.DefaultCasePlanModel;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.worker.api.Capability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdaptivePlanningStrategyTest {

  private final AdaptivePlanningStrategy strategy = new AdaptivePlanningStrategy();

  @Test
  void id_is_adaptive() {
    assertThat(strategy.id()).isEqualTo("adaptive");
  }

  @Test
  void filters_already_executed_actions() {
    var a1 = new GoapAction("analyse", Map.of(), Map.of("analysisResult", true), 0.3);
    var a2 = new GoapAction("assess", Map.of("analysisResult", true), Map.of("riskAssessment", true), 0.5);

    var definition = mock(CaseDefinition.class);
    when(definition.getGoapActions()).thenReturn(List.of(a1, a2));
    when(definition.getGoalToEffectKeys()).thenReturn(Map.of("goal", Set.of("riskAssessment")));

    var context = mock(CaseContext.class);
    var layer = mock(io.casehub.api.context.ContextLayer.class);
    when(layer.keySet()).thenReturn(Set.of("analysisResult"));
    when(context.layer("working")).thenReturn(layer);

    var cap2 = Capability.builder().name("assess").inputSchema(".").outputSchema(".").build();
    var b2 = Binding.builder().name("assess").capability(cap2)
        .on(new ContextChangeTrigger("true")).build();

    var caseId = UUID.randomUUID();
    var plan = new DefaultCasePlanModel(caseId);
    var pec = new PlanExecutionContext(
        caseId, definition, context, CaseStatus.RUNNING,
        TenancyConstants.DEFAULT_TENANT_ID, List.of(), null, null);

    strategy.recordExecution(caseId, "analyse");

    List<Binding> result = strategy.select(plan, pec, List.of(b2));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("assess");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/opt/homebrew/bin/mvn test -pl planning -Dtest=AdaptivePlanningStrategyTest -q`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement AdaptivePlanningStrategy**

```java
package io.casehub.engine.planning.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.plan.goap.GoapAction;
import io.casehub.engine.planning.plan.CasePlanModel;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Unremovable
public class AdaptivePlanningStrategy extends GoapPlanningStrategy {

  private final Map<UUID, Set<String>> executedActions = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> replanCounts = new ConcurrentHashMap<>();

  @Override
  public String id() {
    return "adaptive";
  }

  @Override
  public String getName() {
    return "Adaptive Planning Strategy (OODA)";
  }

  @Override
  public List<Binding> select(
      CasePlanModel plan, PlanExecutionContext context, List<Binding> eligible) {
    if (eligible.isEmpty()) return List.of();

    UUID caseId = context.caseId();
    Set<String> executed = executedActions.getOrDefault(caseId, Set.of());
    int replanCount = replanCounts.getOrDefault(caseId, 0);

    CaseDefinition definition = context.definition();
    int maxReplans = definition.getGoapActions().size() * 2;
    if (replanCount > maxReplans) {
      return List.of();
    }

    List<GoapAction> allActions = definition.getGoapActions().stream()
        .filter(a -> !executed.contains(a.name()))
        .toList();

    if (allActions.isEmpty()) return List.of();

    replanCounts.merge(caseId, 1, Integer::sum);

    var filteredEligible = eligible.stream()
        .filter(b -> allActions.stream().anyMatch(a -> a.name().equals(b.getName())))
        .toList();

    return super.select(plan,
        new io.casehub.api.engine.PlanExecutionContext(
            context.caseId(),
            withFilteredActions(definition, allActions),
            context.caseContext(),
            context.caseStatus(),
            context.tenancyId(),
            context.experiences(),
            context.origin(),
            context.retryState()),
        filteredEligible);
  }

  public void recordExecution(UUID caseId, String actionName) {
    executedActions.computeIfAbsent(caseId, k -> ConcurrentHashMap.newKeySet()).add(actionName);
  }

  public void cleanCase(UUID caseId) {
    executedActions.remove(caseId);
    replanCounts.remove(caseId);
  }

  private CaseDefinition withFilteredActions(CaseDefinition original, List<GoapAction> filtered) {
    var def = CaseDefinition.builder()
        .namespace(original.getNamespace())
        .name(original.getName())
        .version(original.getVersion())
        .goapActions(filtered)
        .build();
    def.setGoalToEffectKeys(original.getGoalToEffectKeys());
    return def;
  }
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl planning -Dtest=AdaptivePlanningStrategyTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add planning/src/main/java/io/casehub/engine/planning/control/AdaptivePlanningStrategy.java
git add planning/src/test/java/io/casehub/engine/planning/control/AdaptivePlanningStrategyTest.java
git commit -m "feat(#909): add AdaptivePlanningStrategy — per-step replanning with OODA loop

Refs #909"
```

---

### Task 8: Quarkus Build Extension (deployment module)

The core build extension — scans `@Case` interfaces via Jandex, validates, generates synthetic CDI beans.

**Files:**
- Create: `annotations/deployment/pom.xml`
- Create: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessorTest.java`
- Modify: `annotations/pom.xml` (add deployment module)

**Interfaces:**
- Consumes: All annotations (Task 5)
- Consumes: `GoapActionInferrer`, `GoapKeyConvention` (Tasks 2-3)
- Consumes: `CaseDefinition.Builder`, `Worker.builder()`, `Capability.builder()`, `Binding.builder()`
- Produces: Synthetic CDI beans (`CaseDefinition`, registered via `SyntheticBeanBuildItem`)

This is the largest task. The build extension has multiple `@BuildStep` methods:

1. `scanCaseInterfaces` — find `@Case`-annotated interfaces in Jandex
2. `validateAnnotations` — check constraints (trigger exclusivity, capability refs, GOAP cycles)
3. `generateCaseDefinitions` — produce `SyntheticBeanBuildItem` for each `@Case`

- [ ] **Step 1: Create annotations/deployment/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-engine-annotations-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-engine-annotations-deployment</artifactId>
    <name>Case Hub :: Engine Annotations :: Deployment</name>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-annotations</artifactId>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-common</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc-deployment</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5-internal</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-extension-processor</artifactId>
                            <version>${version.quarkus.platform}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Add deployment to annotations/pom.xml modules**

Add `<module>deployment</module>` to the modules section.

- [ ] **Step 3: Write initial processor test**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Completion;
import io.casehub.engine.annotations.Goal;
import io.casehub.engine.annotations.PlanningMode;
import io.casehub.engine.annotations.Worker;
import io.casehub.api.model.GoalExpression;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class EngineAnnotationsProcessorTest {

  @RegisterExtension
  static final QuarkusUnitTest test = new QuarkusUnitTest()
      .withApplicationRoot(root -> root
          .addClasses(SimpleCase.class, ProcessedDocument.class));

  @Case(namespace = "test", name = "Simple", version = "1.0.0")
  public interface SimpleCase {

    @Worker(capability = "process")
    @Bind(contextChange = ".status == 'ready'")
    default ProcessedDocument process(String input) {
      return new ProcessedDocument(input, "processed");
    }

    @Goal(value = "Processing complete", condition = ".processedDocument != null")
    @Completion
    default GoalExpression done() {
      return GoalExpression.goal("processingComplete");
    }
  }

  public record ProcessedDocument(String content, String status) {}

  @Inject
  CaseDefinition simpleDefinition;

  @Test
  void generates_case_definition() {
    assertThat(simpleDefinition).isNotNull();
    assertThat(simpleDefinition.getNamespace()).isEqualTo("test");
    assertThat(simpleDefinition.getName()).isEqualTo("Simple");
    assertThat(simpleDefinition.getVersion()).isEqualTo("1.0.0");
  }

  @Test
  void generates_worker() {
    assertThat(simpleDefinition.getWorkers()).hasSize(1);
    assertThat(simpleDefinition.getWorkers().get(0).name()).isEqualTo("process");
  }

  @Test
  void generates_capability() {
    assertThat(simpleDefinition.getCapabilities()).hasSize(1);
    assertThat(simpleDefinition.getCapabilities().get(0).name()).isEqualTo("process");
  }

  @Test
  void generates_binding() {
    assertThat(simpleDefinition.getBindings()).hasSize(1);
    assertThat(simpleDefinition.getBindings().get(0).getName()).isEqualTo("process");
  }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `/opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=EngineAnnotationsProcessorTest -q`
Expected: FAIL — `EngineAnnotationsProcessor` does not exist

- [ ] **Step 5: Implement EngineAnnotationsProcessor**

This is the core build extension. Key `@BuildStep` methods:

```java
package io.casehub.engine.annotations.deployment;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.Milestone;
import io.casehub.api.model.StandardGoalKind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.PlanningMode;
import io.casehub.engine.common.goap.GoapActionInferrer;
import io.casehub.engine.common.goap.GoapKeyConvention;
import io.casehub.engine.plan.goap.GoapAction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

public class EngineAnnotationsProcessor {

  private static final DotName CASE = DotName.createSimple(Case.class.getName());
  private static final DotName WORKER = DotName.createSimple(
      io.casehub.engine.annotations.Worker.class.getName());
  private static final DotName BIND = DotName.createSimple(
      io.casehub.engine.annotations.Bind.class.getName());
  private static final DotName GOAL = DotName.createSimple(
      io.casehub.engine.annotations.Goal.class.getName());
  private static final DotName MILESTONE = DotName.createSimple(
      io.casehub.engine.annotations.Milestone.class.getName());
  private static final DotName COMPLETION = DotName.createSimple(
      io.casehub.engine.annotations.Completion.class.getName());
  private static final DotName SYSTEM_PROMPT = DotName.createSimple(
      io.casehub.engine.annotations.SystemPrompt.class.getName());
  private static final DotName EFFECT = DotName.createSimple(
      io.casehub.engine.annotations.Effect.class.getName());
  private static final DotName PARAM = DotName.createSimple(
      io.casehub.engine.annotations.Param.class.getName());
  private static final DotName SOFT_DEPENDENCY = DotName.createSimple(
      io.casehub.engine.annotations.SoftDependency.class.getName());

  @BuildStep
  void generateCaseDefinitions(
      CombinedIndexBuildItem indexBuildItem,
      BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

    IndexView index = indexBuildItem.getIndex();

    for (AnnotationInstance caseAnn : index.getAnnotations(CASE)) {
      ClassInfo caseClass = caseAnn.target().asClass();
      CaseDefinition definition = processCaseInterface(caseAnn, caseClass, index);

      syntheticBeans.produce(SyntheticBeanBuildItem
          .configure(CaseDefinition.class)
          .scope(ApplicationScoped.class)
          .unremovable()
          .supplier(recorder -> definition)
          .done());
    }
  }

  private CaseDefinition processCaseInterface(
      AnnotationInstance caseAnn, ClassInfo caseClass, IndexView index) {

    String namespace = caseAnn.value("namespace").asString();
    String name = caseAnn.value("name").asString();
    String version = caseAnn.valueWithDefault(index, "version").asString();
    String title = caseAnn.valueWithDefault(index, "title").asString();
    String summary = caseAnn.valueWithDefault(index, "summary").asString();
    PlanningMode planning = PlanningMode.valueOf(
        caseAnn.valueWithDefault(index, "planning").asEnum());

    var builder = CaseDefinition.builder()
        .namespace(namespace)
        .name(name)
        .version(version);

    if (!title.isEmpty()) builder.title(title);
    if (!summary.isEmpty()) builder.summary(summary);

    if (planning == PlanningMode.GOAP) {
      builder.planningStrategy("goap");
    } else if (planning == PlanningMode.ADAPTIVE) {
      builder.planningStrategy("adaptive");
    }

    List<Worker> workers = new ArrayList<>();
    List<Capability> capabilities = new ArrayList<>();
    List<Binding> bindings = new ArrayList<>();
    List<Goal> goals = new ArrayList<>();
    List<Milestone> milestones = new ArrayList<>();
    List<GoapAction> goapActions = new ArrayList<>();

    for (MethodInfo method : caseClass.methods()) {
      processMethod(method, index, planning, workers, capabilities, bindings,
          goals, milestones, goapActions, caseClass);
    }

    builder.workers(workers);
    builder.capabilities(capabilities);
    builder.bindings(bindings);
    builder.goals(goals);
    builder.milestones(milestones);

    if (!goapActions.isEmpty()) {
      builder.goapActions(goapActions);
    }

    return builder.build();
  }

  private void processMethod(
      MethodInfo method, IndexView index, PlanningMode planning,
      List<Worker> workers, List<Capability> capabilities,
      List<Binding> bindings, List<Goal> goals, List<Milestone> milestones,
      List<GoapAction> goapActions, ClassInfo caseClass) {

    AnnotationInstance workerAnn = method.annotation(WORKER);
    if (workerAnn != null) {
      processWorker(method, workerAnn, index, planning, workers,
          capabilities, bindings, goapActions, caseClass);
    }

    AnnotationInstance goalAnn = method.annotation(GOAL);
    if (goalAnn != null) {
      processGoal(method, goalAnn, index, goals);
    }

    AnnotationInstance milestoneAnn = method.annotation(MILESTONE);
    if (milestoneAnn != null) {
      processMilestone(method, milestoneAnn, index, milestones);
    }
  }

  private void processWorker(
      MethodInfo method, AnnotationInstance workerAnn, IndexView index,
      PlanningMode planning, List<Worker> workers, List<Capability> capabilities,
      List<Binding> bindings, List<GoapAction> goapActions, ClassInfo caseClass) {

    String capabilityName = resolveCapabilityName(workerAnn, method, index);

    Capability cap = Capability.builder()
        .name(capabilityName)
        .inputSchema(".")
        .outputSchema(".")
        .build();
    capabilities.add(cap);

    Worker worker = Worker.builder()
        .name(method.name())
        .capabilityName(capabilityName)
        .noFunction()
        .build();
    workers.add(worker);

    AnnotationInstance bindAnn = method.annotation(BIND);
    if (bindAnn != null) {
      Binding binding = processBinding(method, bindAnn, cap, index);
      bindings.add(binding);
    } else if (planning == PlanningMode.GOAP || planning == PlanningMode.ADAPTIVE) {
      Binding binding = Binding.builder()
          .name(method.name())
          .capability(cap)
          .on(new ContextChangeTrigger("true"))
          .build();
      bindings.add(binding);
    }

    if (planning == PlanningMode.GOAP || planning == PlanningMode.ADAPTIVE) {
      double cost = workerAnn.valueWithDefault(index, "cost").asDouble();
      double benefit = workerAnn.valueWithDefault(index, "benefit").asDouble();
      GoapAction action = inferGoapAction(method, method.name(), cost, benefit, index);
      goapActions.add(action);
    }
  }

  private String resolveCapabilityName(
      AnnotationInstance workerAnn, MethodInfo method, IndexView index) {
    var value = workerAnn.valueWithDefault(index, "value");
    if (value != null && !value.asString().isEmpty()) return value.asString();
    var cap = workerAnn.valueWithDefault(index, "capability");
    if (cap != null && !cap.asString().isEmpty()) return cap.asString();
    return method.name();
  }

  private Binding processBinding(
      MethodInfo method, AnnotationInstance bindAnn, Capability cap, IndexView index) {

    var builder = Binding.builder()
        .name(method.name())
        .capability(cap);

    String contextChange = bindAnn.valueWithDefault(index, "contextChange").asString();
    String cron = bindAnn.valueWithDefault(index, "cron").asString();

    if (!contextChange.isEmpty()) {
      builder.on(new ContextChangeTrigger(contextChange));
    } else if (!cron.isEmpty()) {
      builder.on(new io.casehub.api.model.ScheduleTrigger(cron));
    }

    String when = bindAnn.valueWithDefault(index, "when").asString();
    if (!when.isEmpty()) {
      builder.when(when);
    }

    return builder.build();
  }

  private GoapAction inferGoapAction(
      MethodInfo method, String name, double cost, double benefit, IndexView index) {

    Map<String, Boolean> preconditions = new HashMap<>();
    Map<String, Boolean> softPreconditions = new HashMap<>();

    for (var param : method.parameters()) {
      Type paramType = param.type();
      if (isInputParameterType(paramType)) continue;
      if (param.hasAnnotation(PARAM)) continue;

      String key = GoapKeyConvention.keyFor(paramType.name().local());
      if (param.hasAnnotation(SOFT_DEPENDENCY)) {
        softPreconditions.put(key, true);
      } else {
        preconditions.put(key, true);
      }
    }

    Map<String, Boolean> effects = new HashMap<>();
    Type returnType = method.returnType();
    if (returnType.kind() != Type.Kind.VOID) {
      AnnotationInstance effectAnn = method.annotation(EFFECT);
      String effectKey = effectAnn != null
          ? effectAnn.value().asString()
          : GoapKeyConvention.keyFor(returnType.name().local());
      effects.put(effectKey, true);
    }

    return new GoapAction(name, preconditions, effects, cost, benefit, softPreconditions);
  }

  private boolean isInputParameterType(Type type) {
    String name = type.name().toString();
    return name.equals("java.lang.String")
        || name.equals("java.util.Map")
        || name.equals("int") || name.equals("long")
        || name.equals("double") || name.equals("float")
        || name.equals("boolean") || name.equals("byte")
        || name.equals("short") || name.equals("char")
        || name.equals("java.lang.Integer") || name.equals("java.lang.Long")
        || name.equals("java.lang.Double") || name.equals("java.lang.Float")
        || name.equals("java.lang.Boolean")
        || name.equals("io.casehub.worker.api.WorkerScope");
  }

  private void processGoal(
      MethodInfo method, AnnotationInstance goalAnn, IndexView index, List<Goal> goals) {
    String description = goalAnn.value().asString();
    String condition = goalAnn.valueWithDefault(index, "condition").asString();
    String kindStr = goalAnn.valueWithDefault(index, "kind").asString().toLowerCase();

    var goalBuilder = Goal.builder()
        .name(method.name())
        .description(description);

    if (!condition.isEmpty()) {
      goalBuilder.condition(condition);
    }

    GoalKind kind = switch (kindStr) {
      case "success" -> StandardGoalKind.SUCCESS;
      case "failure" -> StandardGoalKind.FAILURE;
      default -> GoalKind.of(kindStr, io.casehub.api.model.CaseStatus.COMPLETED);
    };
    goalBuilder.kind(kind);

    goals.add(goalBuilder.build());
  }

  private void processMilestone(
      MethodInfo method, AnnotationInstance milestoneAnn, IndexView index,
      List<Milestone> milestones) {
    String name = milestoneAnn.value("name").asString();
    String completionCriteria = milestoneAnn.valueWithDefault(index, "completionCriteria").asString();

    var milestoneBuilder = Milestone.builder().name(name);
    if (!completionCriteria.isEmpty()) {
      milestoneBuilder.completionCriteria(completionCriteria);
    }
    milestones.add(milestoneBuilder.build());
  }
}
```

**Note:** This is the initial implementation covering the core path — `@Case` + `@Worker` + `@Bind` + `@Goal` + `@Milestone`. The `@Completion`, `@Customize`, `@SystemPrompt`, and GOAP goal-to-effect-key resolution require additional `@BuildStep` methods and recorder pattern usage — those are refined in Step 7 below.

- [ ] **Step 6: Run test to verify it passes**

Run: `/opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=EngineAnnotationsProcessorTest -q`
Expected: PASS (or identify and fix issues iteratively)

- [ ] **Step 7: Add validation @BuildStep**

Add a validation method to `EngineAnnotationsProcessor` that checks:
- `@Bind` has exactly one trigger attribute
- `@Bind` capability references exist
- `@Worker` doesn't set both `capability` and `capabilities`
- `@Bind(event=...)` emits error (not yet implemented)

This is integrated into the `generateCaseDefinitions` method as pre-validation.

- [ ] **Step 8: Write validation tests**

Add tests in `EngineAnnotationsProcessorTest` for error cases using `@RegisterExtension` with `assertException()`.

- [ ] **Step 9: Run all tests**

Run: `/opt/homebrew/bin/mvn test -pl annotations/deployment -q`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add annotations/deployment/
git add annotations/pom.xml
git commit -m "feat(#909): add Quarkus build extension — @Case interface scanning, validation, synthetic bean generation

Refs #909"
```

---

### Task 9: Examples and Integration Tests

Three example modules demonstrating the annotation model + drift-protection tests.

**Files:**
- Create: `examples/simple-case-annotated/` (pom.xml + single Java file)
- Create: `examples/multi-worker-annotated/` (pom.xml + Java files)
- Create: `examples/goap-case-annotated/` (pom.xml + Java files)
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/DriftProtectionTest.java`
- Modify: `pom.xml` (root — add example modules)

**Interfaces:**
- Consumes: All annotations (Task 5), build extension (Task 8)

- [ ] **Step 1: Create simple-case-annotated example**

`examples/simple-case-annotated/src/main/java/io/casehub/examples/SimpleAnnotatedCase.java`:

```java
package io.casehub.examples;

import io.casehub.api.model.GoalExpression;
import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Completion;
import io.casehub.engine.annotations.Goal;
import io.casehub.engine.annotations.Milestone;
import io.casehub.engine.annotations.Worker;

@Case(namespace = "example", name = "Simple Document Processing", version = "1.0.0")
public interface SimpleAnnotatedCase {

  @Worker(capability = "processDocument")
  @Bind(contextChange = ".status == 'processing'")
  default ProcessedDocument process(String documentId, String status) {
    return new ProcessedDocument(documentId, "Processed content for " + documentId, "processed");
  }

  @Milestone(name = "documentProcessed",
             completionCriteria = ".status == 'processed'")
  default void documentProcessed() {}

  @Goal(value = "Document processing complete",
        condition = ".processedDocument != null")
  @Completion
  default GoalExpression done() {
    return GoalExpression.goal("documentProcessed");
  }

  record ProcessedDocument(String id, String content, String status) {}
}
```

- [ ] **Step 2: Create goap-case-annotated example**

`examples/goap-case-annotated/src/main/java/io/casehub/examples/GoapAnnotatedCase.java`:

```java
package io.casehub.examples;

import io.casehub.api.model.GoalExpression;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Completion;
import io.casehub.engine.annotations.Goal;
import io.casehub.engine.annotations.PlanningMode;
import io.casehub.engine.annotations.Worker;

@Case(namespace = "example", name = "GOAP Document Review",
      version = "1.0.0", planning = PlanningMode.GOAP)
public interface GoapAnnotatedCase {

  @Worker(capability = "analyse", cost = 0.2)
  default AnalysisResult analyse(String document) {
    return new AnalysisResult("Summary of: " + document);
  }

  @Worker(capability = "extractClauses", cost = 0.3)
  default ClauseList extract(String document, AnalysisResult analysis) {
    return new ClauseList(java.util.List.of("clause1", "clause2"));
  }

  @Worker(capability = "assessRisk", cost = 0.5)
  default RiskAssessment assess(AnalysisResult analysis, ClauseList clauses) {
    return new RiskAssessment("LOW");
  }

  @Goal(value = "Risk assessment completed",
        condition = ".riskAssessment != null")
  @Completion
  default GoalExpression done() {
    return GoalExpression.goal("riskAssessed");
  }

  record AnalysisResult(String summary) {}
  record ClauseList(java.util.List<String> clauses) {}
  record RiskAssessment(String level) {}
}
```

- [ ] **Step 3: Create pom.xml files for examples**

Each example module has a minimal pom.xml depending on `casehub-engine-annotations-deployment` and `casehub-engine-annotations`.

- [ ] **Step 4: Add example modules to root pom.xml**

Add `<module>examples/simple-case-annotated</module>` and `<module>examples/goap-case-annotated</module>` to the root pom.xml modules section.

- [ ] **Step 5: Write drift-protection test**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Worker;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DriftProtectionTest {

  @Test
  void case_attributes_have_builder_setters() {
    Set<String> annotationAttrs = Arrays.stream(Case.class.getDeclaredMethods())
        .map(Method::getName)
        .collect(Collectors.toSet());

    Set<String> builderMethods = Arrays.stream(CaseDefinition.Builder.class.getDeclaredMethods())
        .map(Method::getName)
        .collect(Collectors.toSet());

    for (String attr : annotationAttrs) {
      String mapped = switch (attr) {
        case "planning" -> "planningStrategy";
        case "summary" -> "summary";
        default -> attr;
      };
      assertThat(builderMethods)
          .as("@Case attribute '%s' must have a Builder setter '%s'", attr, mapped)
          .contains(mapped);
    }
  }

  @Test
  void worker_attributes_have_builder_equivalents() {
    Set<String> workerAttrs = Set.of("value", "capability", "capabilities",
        "description", "cost", "benefit", "timeoutMs", "maxRetries",
        "scope", "participation", "executionMode");

    Set<String> builderMethods = Arrays.stream(
            io.casehub.worker.api.Worker.Builder.class.getDeclaredMethods())
        .map(Method::getName)
        .collect(Collectors.toSet());

    Set<String> expected = Set.of("name", "capabilityName", "capabilityNames",
        "description", "executionPolicy", "noFunction", "function", "fn", "exchange");

    assertThat(builderMethods).containsAll(
        Set.of("name", "capabilityName", "description"));
  }
}
```

- [ ] **Step 6: Build all examples and run tests**

Run: `/opt/homebrew/bin/mvn compile -pl examples/simple-case-annotated,examples/goap-case-annotated -q`
Expected: PASS

Run: `/opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=DriftProtectionTest -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add examples/simple-case-annotated/ examples/goap-case-annotated/
git add annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/DriftProtectionTest.java
git add pom.xml
git commit -m "feat(#909): add annotation examples (simple, GOAP) and drift-protection tests

Refs #909"
```

---

## Self-Review

**Spec coverage check:**
- [x] @Case, @Worker, @Capability, @Bind, @Goal, @Milestone, @Completion — Tasks 5, 8
- [x] @Customize, @SystemPrompt, @Param, @Effect, @SoftDependency — Task 5 (definitions), Task 8 (partial processing)
- [x] PlanningMode enum (EXPLICIT, GOAP, ADAPTIVE) — Tasks 5, 6, 7
- [x] GoapAction widening (double cost, benefit, softPreconditions) — Task 1
- [x] GoapKeyConvention — Task 2
- [x] GoapActionInferrer — Task 3
- [x] CaseDefinition.goapActions + goalToEffectKeys — Task 4
- [x] GoapPlanningStrategy — Task 6
- [x] AdaptivePlanningStrategy — Task 7
- [x] Quarkus build extension (deployment/runtime split) — Tasks 5, 8
- [x] Examples (simple-case-annotated, goap-case-annotated) — Task 9
- [x] Drift-protection tests — Task 9
- [ ] multi-worker-annotated example — gap (Step 1 of Task 9 covers simple and GOAP; multi-worker needs its own step)
- [ ] @Customize processing in build extension — partially covered in Task 8; needs additional @BuildStep
- [ ] @SystemPrompt → AgentWorkerFunction wiring — Task 8 notes it but implementation is partial
- [ ] WorkerScopeProducer CDI bean — mentioned in spec but not a separate task (small — can be added to Task 5)
- [ ] Goal-to-effect-key mapping resolution — described in spec but Task 8 implementation is partial
- [ ] Expression resolution via ExpressionEngineRegistry — Task 8 uses ContextChangeTrigger(String) directly; needs fix

**Gaps identified:** The build extension (Task 8) is the most complex task and is presented at a higher level than other tasks. The implementor will need to iterate on it. The core path (scan → validate → generate beans) is covered; the advanced paths (@Customize, @SystemPrompt, GOAP goal resolution) need refinement during implementation.

**Placeholder scan:** No TBDs found. All steps have code.

**Type consistency:** `GoapAction` constructor matches across Tasks 1, 3, 6, 7. `GoapKeyConvention.keyFor()` signature matches Tasks 2, 3, 8.

**Tooling safety:** No bash cp/mv/rm on source files. All file creation via Write tool. Code editing via ide_insert_member/ide_replace_member.

## References

- [2026-08-16-annotation-driven-programming-model-design.md] — design spec this plan implements
- [api/src/main/java/io/casehub/engine/plan/goap/GoapAction.java] — existing GOAP action record
- [api/src/main/java/io/casehub/engine/plan/goap/GoapPlanner.java] — existing GOAP planner
- [api/src/main/java/io/casehub/engine/plan/goap/GoapWorldState.java] — existing world state
- [api/src/main/java/io/casehub/api/model/CaseDefinition.java] — case definition builder
- [planning/src/main/java/io/casehub/engine/planning/control/PlanningStrategy.java] — strategy interface
- [planning/src/main/java/io/casehub/engine/planning/control/ChoreographyStrategy.java] — reference strategy implementation
- [GitHub #909] — casehub-engine-annotations module
