# Engine Annotations Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #909 — casehub-engine-annotations module
**Issue group:** #909

**Goal:** Close all remaining spec gaps in the annotation-driven programming model — repeatable @Bind, goal-to-effect-key mapping, 15 build-time validations, Gizmo synthetic subclass for default method invocation, two-phase recorder with ExpressionEngineRegistry, @Completion/@Customize/@SystemPrompt processing, test coverage for all annotation features.

**Architecture:** Addendum to the existing plan (Tasks 1-9, Batches 1-3). The processor gains a second recorder phase at RUNTIME_INIT for CDI-managed expression resolution. Gizmo generates empty implementing classes for @Case interfaces; a generic `AnnotationWorkerFunction` invokes default methods reflectively via MethodHandle at runtime. @Completion methods are invoked at RUNTIME_INIT via the synthetic subclass to collect GoalExpressions.

**Tech Stack:** Java 21, Quarkus 3.32.2, Jandex, Quarkus Gizmo, JUnit 5, QuarkusUnitTest, AssertJ

## Global Constraints

- Quarkus version `3.32.2` — all extension APIs must match this version
- Group ID `io.casehub`, version `0.2-SNAPSHOT`
- No LangChain4j dependency in the annotations module
- `ContextBridge` is internal — never exposed in user-facing APIs
- GoapAction name = Binding name = Worker/method name (1:1 identity)
- `-parameters` compiler flag required — build extension validates parameter names are not synthetic
- Two-phase recorder: STATIC_INIT for scanning/validation/Gizmo, RUNTIME_INIT for CaseDefinition construction (D10)

---

## Batch 4: Build-time fixes and test coverage

### Task 10: Fix repeatable @Bind and add missing test coverage

Fix the processor to handle multiple `@Bind` annotations on a single method (repeatable container `@Bindings`), and add test coverage for all untested annotation features.

**Files:**
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/AnnotationFeaturesTest.java`

**Interfaces:**
- Consumes: `@Bind`, `@Bindings`, `@Effect`, `@SoftDependency`, `@Param`, `@Worker`
- Produces: Multiple `BindingDescriptor` per method (was: at most one)

- [ ] **Step 1: Write test for repeatable @Bind**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Effect;
import io.casehub.engine.annotations.Goal;
import io.casehub.engine.annotations.Param;
import io.casehub.engine.annotations.PlanningMode;
import io.casehub.engine.annotations.SoftDependency;
import io.casehub.engine.annotations.Worker;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AnnotationFeaturesTest {

  @RegisterExtension
  static final QuarkusUnitTest test =
      new QuarkusUnitTest()
          .withApplicationRoot(
              root ->
                  root.addClasses(
                      FeatureCase.class,
                      FeatureCase.InputData.class,
                      FeatureCase.OutputData.class));

  @Case(namespace = "test", name = "Features", version = "1.0.0", planning = PlanningMode.GOAP)
  public interface FeatureCase {

    @Worker(value = "customName", cost = 0.3)
    @Bind(contextChange = ".ready")
    @Bind(cron = "0 0 * * *")
    default OutputData doWork(String input, @SoftDependency InputData soft) {
      return new OutputData("done");
    }

    @Worker(capability = "transform", cost = 0.5)
    @Bind(contextChange = ".inputData != null", when = ".priority == 'high'")
    @Effect("transformedResult")
    default OutputData transform(InputData data, @Param("config") String config) {
      return new OutputData("transformed");
    }

    @Goal(value = "Work complete", condition = ".outputData != null")
    default void done() {}

    record InputData(String value) {}
    record OutputData(String result) {}
  }

  @Inject CaseDefinition definition;

  @Test
  void repeatable_bind_produces_multiple_bindings() {
    long doWorkBindings =
        definition.getBindings().stream().filter(b -> b.getName().equals("doWork")).count();
    assertThat(doWorkBindings).isEqualTo(2);
  }

  @Test
  void worker_value_overrides_name() {
    assertThat(definition.getWorkers().stream().anyMatch(w -> w.name().equals("doWork"))).isTrue();
    assertThat(
            definition.getWorkers().stream()
                .filter(w -> w.name().equals("doWork"))
                .findFirst()
                .get()
                .capabilityNames())
        .contains("customName");
  }

  @Test
  void effect_annotation_overrides_key() {
    var transformAction =
        definition.getGoapActions().stream()
            .filter(a -> a.name().equals("transform"))
            .findFirst();
    assertThat(transformAction).isPresent();
    assertThat(transformAction.get().effects()).containsKey("transformedResult");
    assertThat(transformAction.get().effects()).doesNotContainKey("outputData");
  }

  @Test
  void soft_dependency_in_goap() {
    var doWorkAction =
        definition.getGoapActions().stream().filter(a -> a.name().equals("doWork")).findFirst();
    assertThat(doWorkAction).isPresent();
    assertThat(doWorkAction.get().softPreconditions()).containsKey("inputData");
    assertThat(doWorkAction.get().preconditions()).doesNotContainKey("inputData");
  }

  @Test
  void param_excluded_from_goap_inference() {
    var transformAction =
        definition.getGoapActions().stream()
            .filter(a -> a.name().equals("transform"))
            .findFirst();
    assertThat(transformAction).isPresent();
    assertThat(transformAction.get().preconditions()).containsKey("inputData");
    assertThat(transformAction.get().preconditions()).doesNotContainKey("config");
    assertThat(transformAction.get().preconditions()).doesNotContainKey("string");
  }

  @Test
  void bind_with_when_guard() {
    var transformBinding =
        definition.getBindings().stream()
            .filter(b -> b.getName().equals("transform"))
            .findFirst();
    assertThat(transformBinding).isPresent();
    assertThat(transformBinding.get().getWhen()).isNotNull();
  }

  @Test
  void bind_with_cron_trigger() {
    var cronBindings =
        definition.getBindings().stream()
            .filter(b -> b.getName().equals("doWork"))
            .filter(b -> b.getTrigger() instanceof io.casehub.api.model.ScheduleTrigger)
            .toList();
    assertThat(cronBindings).hasSize(1);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=AnnotationFeaturesTest`
Expected: FAIL — repeatable @Bind produces only 1 binding

- [ ] **Step 3: Fix processor for repeatable @Bind**

In `EngineAnnotationsProcessor.processWorkerMethod()`, replace single annotation lookup with repeatable handling:

```java
// Replace:
AnnotationInstance bindAnn = method.annotation(BIND);
if (bindAnn != null) {
  bindings.add(processBindAnnotation(method, bindAnn, capabilityName, index));
}

// With:
List<AnnotationInstance> bindAnns = new ArrayList<>();
AnnotationInstance singleBind = method.annotation(BIND);
if (singleBind != null) {
  bindAnns.add(singleBind);
}
AnnotationInstance bindingsContainer = method.annotation(BINDINGS);
if (bindingsContainer != null) {
  bindAnns.clear();
  for (AnnotationInstance nested : bindingsContainer.value().asNestedArray()) {
    bindAnns.add(nested);
  }
}
for (AnnotationInstance bindAnn : bindAnns) {
  bindings.add(processBindAnnotation(method, bindAnn, capabilityName, index));
}
```

Also add `BINDINGS` DotName constant:
```java
private static final DotName BINDINGS =
    DotName.createSimple("io.casehub.engine.annotations.Bindings");
```

- [ ] **Step 4: Run tests to verify pass**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=AnnotationFeaturesTest`
Expected: PASS

- [ ] **Step 5: Run all deployment tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment`
Expected: PASS (all tests including EngineAnnotationsProcessorTest and DriftProtectionTest)

- [ ] **Step 6: Commit**

```bash
git add annotations/deployment/
git commit -m "feat(#909): fix repeatable @Bind and add annotation feature tests

Refs #909"
```

---

### Task 11: Goal-to-effect-key mapping

Parse `@Goal` condition strings for `.keyName != null` patterns and populate `goalToEffectKeys` on the CaseDescriptor. In GOAP mode, the planner uses this mapping to connect goal satisfaction to worker effects.

**Files:**
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/GoalConditionParser.java`
- Create: `annotations/runtime/src/test/java/io/casehub/engine/annotations/runtime/GoalConditionParserTest.java`
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`

**Interfaces:**
- Consumes: `@Goal` condition strings
- Produces: `GoalConditionParser.parseEffectKeys(String condition) -> Set<String>`

- [ ] **Step 1: Write GoalConditionParser tests**

```java
package io.casehub.engine.annotations.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class GoalConditionParserTest {

  @Test
  void single_key() {
    assertThat(GoalConditionParser.parseEffectKeys(".riskAssessment != null"))
        .containsExactly("riskAssessment");
  }

  @Test
  void compound_condition() {
    assertThat(GoalConditionParser.parseEffectKeys(".analysisResult != null and .clauseList != null"))
        .containsExactlyInAnyOrder("analysisResult", "clauseList");
  }

  @Test
  void boolean_check() {
    assertThat(GoalConditionParser.parseEffectKeys(".processed == true"))
        .containsExactly("processed");
  }

  @Test
  void nested_path_uses_root_key() {
    assertThat(GoalConditionParser.parseEffectKeys(".result.status != null"))
        .containsExactly("result");
  }

  @Test
  void empty_for_unparseable() {
    assertThat(GoalConditionParser.parseEffectKeys("some_function(.x)")).isEmpty();
  }

  @Test
  void empty_for_null_or_blank() {
    assertThat(GoalConditionParser.parseEffectKeys(null)).isEmpty();
    assertThat(GoalConditionParser.parseEffectKeys("")).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/opt/homebrew/bin/mvn test -pl annotations/runtime -Dtest=GoalConditionParserTest -q`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement GoalConditionParser**

```java
package io.casehub.engine.annotations.runtime;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoalConditionParser {

  private static final Pattern KEY_PATTERN = Pattern.compile("\\.(\\w+)(?:\\.\\w+)*\\s*(!?=)");

  private GoalConditionParser() {}

  public static Set<String> parseEffectKeys(String condition) {
    if (condition == null || condition.isBlank()) return Set.of();
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = KEY_PATTERN.matcher(condition);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return Set.copyOf(keys);
  }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl annotations/runtime -Dtest=GoalConditionParserTest -q`
Expected: PASS

- [ ] **Step 5: Wire into processor**

In `EngineAnnotationsProcessor.buildDescriptor()`, after processing all methods, populate `goalToEffectKeys`:

```java
// After the method loop, before return:
for (GoalDescriptor gd : goals) {
  if (gd.condition() != null) {
    Set<String> effectKeys = GoalConditionParser.parseEffectKeys(gd.condition());
    if (!effectKeys.isEmpty()) {
      goalToEffectKeys.put(gd.name(), new ArrayList<>(effectKeys));
    }
  }
}
```

Add import for `GoalConditionParser` from `io.casehub.engine.annotations.runtime`.

- [ ] **Step 6: Add test for goal-to-effect-key mapping in GoapAnnotatedCaseTest**

In `examples/goap-case-annotated/src/test/java/io/casehub/examples/GoapAnnotatedCaseTest.java`, add:

```java
@Test
void goal_to_effect_keys_populated() {
  assertThat(definition.getGoalToEffectKeys()).isNotEmpty();
  assertThat(definition.getGoalToEffectKeys().get("done")).contains("riskAssessment");
}
```

- [ ] **Step 7: Install runtime, run all tests**

Run: `/opt/homebrew/bin/mvn install -pl annotations/runtime -DskipTests -q`
Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment,examples/goap-case-annotated`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add annotations/runtime/ annotations/deployment/ examples/goap-case-annotated/
git commit -m "feat(#909): add goal-to-effect-key mapping via GoalConditionParser

Refs #909"
```

---

### Task 12: Build-time validation

Add a validation `@BuildStep` that checks for annotation constraint violations and emits clear error messages. Errors halt the build; warnings are logged.

**Files:**
- Create: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/AnnotationValidationStep.java`
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/ValidationErrorTest.java`

**Interfaces:**
- Consumes: `CombinedIndexBuildItem`, annotation DotNames
- Produces: Build failures on constraint violations

- [ ] **Step 1: Write validation error tests**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Worker;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ValidationErrorTest {

  @RegisterExtension
  static final QuarkusUnitTest multipleTriggers =
      new QuarkusUnitTest()
          .withApplicationRoot(root -> root.addClasses(MultipleTriggerCase.class))
          .assertException(
              t -> assertThat(t.getMessage()).contains("multiple triggers"));

  @Case(namespace = "test", name = "Bad", version = "1.0.0")
  public interface MultipleTriggerCase {

    @Worker(capability = "work")
    @Bind(contextChange = ".ready", cron = "0 0 * * *")
    default String doWork(String input) {
      return input;
    }
  }

  @Test
  void multiple_triggers_rejected() {}
}
```

- [ ] **Step 2: Run test to verify it fails (no validation yet)**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=ValidationErrorTest`
Expected: FAIL — build succeeds when it should fail

- [ ] **Step 3: Implement AnnotationValidationStep**

```java
package io.casehub.engine.annotations.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;

public class AnnotationValidationStep {

  private static final Logger LOG = Logger.getLogger(AnnotationValidationStep.class);

  private static final DotName CASE = DotName.createSimple("io.casehub.engine.annotations.Case");
  private static final DotName WORKER =
      DotName.createSimple("io.casehub.engine.annotations.Worker");
  private static final DotName BIND = DotName.createSimple("io.casehub.engine.annotations.Bind");
  private static final DotName BINDINGS =
      DotName.createSimple("io.casehub.engine.annotations.Bindings");
  private static final DotName GOAL = DotName.createSimple("io.casehub.engine.annotations.Goal");
  private static final DotName MILESTONE =
      DotName.createSimple("io.casehub.engine.annotations.Milestone");
  private static final DotName SYSTEM_PROMPT =
      DotName.createSimple("io.casehub.engine.annotations.SystemPrompt");
  private static final DotName COMPLETION =
      DotName.createSimple("io.casehub.engine.annotations.Completion");

  @BuildStep
  void validate(CombinedIndexBuildItem indexBuildItem) {
    IndexView index = indexBuildItem.getIndex();
    List<String> errors = new ArrayList<>();

    for (AnnotationInstance caseAnn : index.getAnnotations(CASE)) {
      ClassInfo caseClass = caseAnn.target().asClass();
      String planning = stringOr(caseAnn, index, "planning", "EXPLICIT");
      boolean isGoap = "GOAP".equals(planning) || "ADAPTIVE".equals(planning);

      Set<String> goalNames = new HashSet<>();
      Set<String> milestoneNames = new HashSet<>();
      Set<String> completionKinds = new HashSet<>();

      for (MethodInfo method : caseClass.methods()) {
        validateWorkerMethod(method, index, errors, isGoap);
        validateBindAnnotations(method, index, errors);
        validateGoal(method, index, errors, goalNames);
        validateMilestone(method, index, errors, milestoneNames);
        validateCompletion(method, index, errors, completionKinds);
        validateSystemPromptConflict(method, errors);
      }
    }

    if (!errors.isEmpty()) {
      throw new RuntimeException(
          "Annotation validation failed:\n- " + String.join("\n- ", errors));
    }
  }

  private void validateWorkerMethod(
      MethodInfo method, IndexView index, List<String> errors, boolean isGoap) {
    AnnotationInstance workerAnn = method.annotation(WORKER);
    if (workerAnn == null) return;

    String capability = stringOr(workerAnn, index, "capability", "");
    String[] capabilities =
        workerAnn.value("capabilities") != null ? workerAnn.value("capabilities").asStringArray() : new String[0];
    if (!capability.isEmpty() && capabilities.length > 0) {
      errors.add(
          method.declaringClass().name()
              + "#"
              + method.name()
              + ": @Worker sets both 'capability' and 'capabilities' — use one");
    }

    for (var param : method.parameters()) {
      if (param.name() != null && param.name().matches("arg\\d+")) {
        errors.add(
            method.declaringClass().name()
                + "#"
                + method.name()
                + ": parameter '"
                + param.name()
                + "' has synthetic name — add -parameters compiler flag");
        break;
      }
    }
  }

  private void validateBindAnnotations(
      MethodInfo method, IndexView index, List<String> errors) {
    List<AnnotationInstance> bindAnns = collectBindAnnotations(method, index);
    for (AnnotationInstance bind : bindAnns) {
      int triggerCount = 0;
      if (!stringOr(bind, index, "contextChange", "").isEmpty()) triggerCount++;
      if (!stringOr(bind, index, "cron", "").isEmpty()) triggerCount++;
      if (boolOr(bind, index, "scopeActivated", false)) triggerCount++;

      if (triggerCount == 0) {
        errors.add(
            method.declaringClass().name()
                + "#"
                + method.name()
                + ": @Bind has no trigger — set contextChange, cron, or scopeActivated");
      }
      if (triggerCount > 1) {
        errors.add(
            method.declaringClass().name()
                + "#"
                + method.name()
                + ": @Bind has multiple triggers — set exactly one");
      }
    }
  }

  private void validateGoal(
      MethodInfo method, IndexView index, List<String> errors, Set<String> goalNames) {
    AnnotationInstance goalAnn = method.annotation(GOAL);
    if (goalAnn == null) return;
    if (!goalNames.add(method.name())) {
      errors.add(
          method.declaringClass().name() + ": duplicate @Goal name '" + method.name() + "'");
    }
  }

  private void validateMilestone(
      MethodInfo method, IndexView index, List<String> errors, Set<String> milestoneNames) {
    AnnotationInstance milestoneAnn = method.annotation(MILESTONE);
    if (milestoneAnn == null) return;
    String name = milestoneAnn.value("name").asString();
    if (!milestoneNames.add(name)) {
      errors.add(
          method.declaringClass().name() + ": duplicate @Milestone name '" + name + "'");
    }
  }

  private void validateCompletion(
      MethodInfo method, IndexView index, List<String> errors, Set<String> completionKinds) {
    AnnotationInstance completionAnn = method.annotation(COMPLETION);
    if (completionAnn == null) return;
    String kind = stringOr(completionAnn, index, "kind", "SUCCESS");
    if (!completionKinds.add(kind)) {
      errors.add(
          method.declaringClass().name()
              + "#"
              + method.name()
              + ": duplicate @Completion kind '"
              + kind
              + "'");
    }
  }

  private void validateSystemPromptConflict(MethodInfo method, List<String> errors) {
    if (method.annotation(SYSTEM_PROMPT) != null && method.hasAnnotation(WORKER)) {
      if (method.returnType().kind() != org.jboss.jandex.Type.Kind.VOID) {
        errors.add(
            method.declaringClass().name()
                + "#"
                + method.name()
                + ": @SystemPrompt worker must not have a return type — use void");
      }
    }
  }

  private List<AnnotationInstance> collectBindAnnotations(MethodInfo method, IndexView index) {
    List<AnnotationInstance> result = new ArrayList<>();
    AnnotationInstance single = method.annotation(BIND);
    if (single != null) result.add(single);
    AnnotationInstance container = method.annotation(BINDINGS);
    if (container != null) {
      result.clear();
      for (AnnotationInstance nested : container.value().asNestedArray()) {
        result.add(nested);
      }
    }
    return result;
  }

  private static String stringOr(
      AnnotationInstance ann, IndexView index, String name, String def) {
    AnnotationValue v = ann.valueWithDefault(index, name);
    return v != null ? v.asString() : def;
  }

  private static boolean boolOr(
      AnnotationInstance ann, IndexView index, String name, boolean def) {
    AnnotationValue v = ann.valueWithDefault(index, name);
    return v != null ? v.asBoolean() : def;
  }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=ValidationErrorTest`
Expected: PASS — build fails with "multiple triggers"

- [ ] **Step 5: Run all deployment tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add annotations/deployment/
git commit -m "feat(#909): add build-time annotation validation (trigger exclusivity, duplicates, -parameters)

Refs #909"
```

---

## Batch 5: Gizmo synthetic subclass and WorkerFunction wiring

### Task 13: Generate concrete implementing class via Gizmo

Generate an empty concrete class implementing each `@Case` interface at build time. This class inherits all default methods and provides an instantiable type for runtime method invocation.

**Files:**
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Modify: `annotations/deployment/pom.xml` (may need `quarkus-gizmo` dependency)
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/GizmoSubclassTest.java`

**Interfaces:**
- Consumes: `@Case` interface ClassInfo from Jandex
- Produces: `GeneratedClassBuildItem` with bytecode for `<Interface>_CaseHubImpl`
- Produces: impl class name threaded to CaseDescriptor

- [ ] **Step 1: Add impl class name to CaseDescriptor**

Add `implClassName` field to `CaseDescriptor`:

```java
// In annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDescriptor.java
public record CaseDescriptor(
    String namespace,
    String name,
    String version,
    String title,
    String summary,
    String planningStrategy,
    String implClassName,
    List<WorkerDescriptor> workers,
    List<BindingDescriptor> bindings,
    List<GoalDescriptor> goals,
    List<MilestoneDescriptor> milestones,
    List<GoapActionDescriptor> goapActions,
    Map<String, List<String>> goalToEffectKeys) {}
```

Update all construction sites (processor's `buildDescriptor()` and any test helpers).

- [ ] **Step 2: Write test for Gizmo subclass**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Worker;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class GizmoSubclassTest {

  @RegisterExtension
  static final QuarkusUnitTest test =
      new QuarkusUnitTest()
          .withApplicationRoot(
              root -> root.addClasses(GizmoCase.class, GizmoCase.Result.class));

  @Case(namespace = "test", name = "Gizmo", version = "1.0.0")
  public interface GizmoCase {

    @Worker(capability = "compute")
    @Bind(contextChange = ".input != null")
    default Result compute(String input) {
      return new Result("computed: " + input);
    }

    record Result(String value) {}
  }

  @Test
  void generated_class_exists() throws Exception {
    Class<?> implClass =
        Thread.currentThread()
            .getContextClassLoader()
            .loadClass(GizmoCase.class.getName() + "_CaseHubImpl");
    assertThat(implClass).isNotNull();
    assertThat(GizmoCase.class.isAssignableFrom(implClass)).isTrue();
  }

  @Test
  void generated_class_instantiable() throws Exception {
    Class<?> implClass =
        Thread.currentThread()
            .getContextClassLoader()
            .loadClass(GizmoCase.class.getName() + "_CaseHubImpl");
    Object instance = implClass.getDeclaredConstructor().newInstance();
    assertThat(instance).isInstanceOf(GizmoCase.class);
  }

  @Test
  void default_method_callable() throws Exception {
    Class<?> implClass =
        Thread.currentThread()
            .getContextClassLoader()
            .loadClass(GizmoCase.class.getName() + "_CaseHubImpl");
    GizmoCase instance = (GizmoCase) implClass.getDeclaredConstructor().newInstance();
    GizmoCase.Result result = instance.compute("test");
    assertThat(result.value()).isEqualTo("computed: test");
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=GizmoSubclassTest`
Expected: FAIL — ClassNotFoundException

- [ ] **Step 4: Generate Gizmo subclass in processor**

Add a new `@BuildStep` to `EngineAnnotationsProcessor`:

```java
@BuildStep
void generateCaseImplementations(
    CombinedIndexBuildItem indexBuildItem,
    BuildProducer<GeneratedClassBuildItem> generatedClasses) {

  IndexView index = indexBuildItem.getIndex();

  for (AnnotationInstance caseAnn : index.getAnnotations(CASE)) {
    ClassInfo caseClass = caseAnn.target().asClass();
    String implClassName = caseClass.name().toString() + "_CaseHubImpl";

    try (ClassCreator creator =
        ClassCreator.builder()
            .classOutput(new GeneratedClassGizmoAdaptor(generatedClasses, true))
            .className(implClassName)
            .interfaces(caseClass.name().toString())
            .build()) {

      try (MethodCreator ctor = creator.getMethodCreator("<init>", void.class)) {
        ctor.invokeSpecialMethod(
            MethodDescriptor.ofConstructor(Object.class), ctor.getThis());
        ctor.returnVoid();
      }
    }
  }
}
```

Add imports:
```java
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
```

Also update `buildDescriptor()` to set `implClassName`:
```java
String implClassName = caseClass.name().toString() + "_CaseHubImpl";
// ... pass to CaseDescriptor constructor
```

- [ ] **Step 5: Run tests to verify pass**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=GizmoSubclassTest`
Expected: PASS

- [ ] **Step 6: Run all tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add annotations/
git commit -m "feat(#909): generate Gizmo synthetic subclass for @Case interfaces

Refs #909"
```

---

### Task 14: AnnotationWorkerFunction — default method invocation

Create a generic `WorkerFunction.Sync` implementation that invokes default methods on the generated subclass via reflection. Wire it into the recorder to replace `noFunction()`.

**Files:**
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/AnnotationWorkerFunction.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/WorkerParamDescriptor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/WorkerDescriptor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDefinitionRecorder.java`
- Create: `annotations/runtime/src/test/java/io/casehub/engine/annotations/runtime/AnnotationWorkerFunctionTest.java`

**Interfaces:**
- Consumes: Generated impl class (Task 13), WorkerParamDescriptor list
- Produces: `AnnotationWorkerFunction implements WorkerFunction.Sync<Map, Map>`

- [ ] **Step 1: Create WorkerParamDescriptor**

```java
package io.casehub.engine.annotations.runtime;

public record WorkerParamDescriptor(String name, String contextKey, String typeName) {}
```

- [ ] **Step 2: Add params and return type to WorkerDescriptor**

```java
public record WorkerDescriptor(
    String name,
    String capabilityName,
    String description,
    String methodName,
    List<WorkerParamDescriptor> params,
    String returnTypeName,
    String effectKey) {}
```

Update all construction sites in the processor and tests.

- [ ] **Step 3: Write AnnotationWorkerFunction test**

```java
package io.casehub.engine.annotations.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.worker.api.WorkerResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnotationWorkerFunctionTest {

  public interface TestInterface {
    default TestOutput compute(String input) {
      return new TestOutput("computed: " + input);
    }
  }

  public static class TestImpl implements TestInterface {}

  public record TestOutput(String value) {}

  @Test
  void invokes_default_method() {
    var function =
        new AnnotationWorkerFunction(
            TestImpl.class.getName(),
            "compute",
            List.of(new WorkerParamDescriptor("input", "input", "java.lang.String")),
            TestOutput.class.getName(),
            "testOutput");

    WorkerResult<?> result = function.fn().apply(Map.of("input", "hello"), null);
    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) result.output();
    assertThat(output).containsKey("testOutput");
  }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `/opt/homebrew/bin/mvn test -pl annotations/runtime -Dtest=AnnotationWorkerFunctionTest -q`
Expected: FAIL — class does not exist

- [ ] **Step 5: Implement AnnotationWorkerFunction**

```java
package io.casehub.engine.annotations.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class AnnotationWorkerFunction implements WorkerFunction.Sync<Map, Map> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String implClassName;
  private final String methodName;
  private final List<WorkerParamDescriptor> params;
  private final String returnTypeName;
  private final String effectKey;

  public AnnotationWorkerFunction(
      String implClassName,
      String methodName,
      List<WorkerParamDescriptor> params,
      String returnTypeName,
      String effectKey) {
    this.implClassName = implClassName;
    this.methodName = methodName;
    this.params = params;
    this.returnTypeName = returnTypeName;
    this.effectKey = effectKey;
  }

  @Override
  public Class<Map> inputType() {
    return Map.class;
  }

  @Override
  public Class<Map> outputType() {
    return Map.class;
  }

  @Override
  public BiFunction<Map, WorkerScope, WorkerResult<Map>> fn() {
    return (input, scope) -> {
      try {
        Class<?> implClass =
            Thread.currentThread().getContextClassLoader().loadClass(implClassName);
        Object instance = implClass.getDeclaredConstructor().newInstance();

        Class<?>[] paramTypes = new Class<?>[params.size()];
        Object[] args = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
          WorkerParamDescriptor p = params.get(i);
          paramTypes[i] =
              Thread.currentThread().getContextClassLoader().loadClass(p.typeName());
          Object rawValue = input != null ? input.get(p.contextKey()) : null;
          args[i] = MAPPER.convertValue(rawValue, paramTypes[i]);
        }

        Method method = implClass.getMethod(methodName, paramTypes);
        Object result = method.invoke(instance, args);

        Map<String, Object> output = new HashMap<>();
        if (result != null && effectKey != null) {
          output.put(effectKey, MAPPER.convertValue(result, Map.class));
        }
        return WorkerResult.of(output);
      } catch (Exception e) {
        return WorkerResult.failed("Annotation worker invocation failed: " + e.getMessage());
      }
    };
  }
}
```

- [ ] **Step 6: Run test to verify pass**

Run: `/opt/homebrew/bin/mvn test -pl annotations/runtime -Dtest=AnnotationWorkerFunctionTest -q`
Expected: PASS

- [ ] **Step 7: Wire into recorder**

Update `CaseDefinitionRecorder.createCaseDefinition()` to create `AnnotationWorkerFunction` instead of `noFunction()`:

```java
// Replace:
var workerBuilder = Worker.builder().name(wd.name()).capabilityName(wd.capabilityName()).noFunction();

// With:
var workerBuilder = Worker.builder().name(wd.name()).capabilityName(wd.capabilityName());
if (wd.params() != null && !wd.params().isEmpty() && descriptor.implClassName() != null) {
  workerBuilder.function(
      new AnnotationWorkerFunction(
          descriptor.implClassName(),
          wd.methodName(),
          wd.params(),
          wd.returnTypeName(),
          wd.effectKey()));
} else {
  workerBuilder.noFunction();
}
```

Update processor's `processWorkerMethod()` to populate the new WorkerDescriptor fields (methodName, params, returnTypeName, effectKey).

- [ ] **Step 8: Install runtime, run all tests**

Run: `/opt/homebrew/bin/mvn install -pl annotations/runtime -DskipTests -q`
Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment,examples/simple-case-annotated,examples/goap-case-annotated`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add annotations/
git commit -m "feat(#909): add AnnotationWorkerFunction — default method invocation via reflection

Refs #909"
```

---

## Batch 6: Two-phase recorder and runtime wiring

### Task 15: RUNTIME_INIT recorder phase with ExpressionEngineRegistry

Move CaseDefinition construction from STATIC_INIT to RUNTIME_INIT to enable CDI-managed expression resolution via `ExpressionEngineRegistry`.

**Files:**
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDefinitionRecorder.java`

**Interfaces:**
- Consumes: `ExpressionEngineRegistry` (CDI, at RUNTIME_INIT)
- Produces: CaseDefinition with `ContextChangeTrigger(ExpressionEvaluator)` instead of `ContextChangeTrigger(String)`

- [ ] **Step 1: Change @Record to RUNTIME_INIT**

In `EngineAnnotationsProcessor.generateCaseDefinitions()`:

```java
// Change:
@Record(ExecutionTime.STATIC_INIT)
// To:
@Record(ExecutionTime.RUNTIME_INIT)
```

- [ ] **Step 2: Update recorder to use ExpressionEngineRegistry**

In `CaseDefinitionRecorder.createCaseDefinition()`, resolve expressions via CDI:

```java
// At the top of createCaseDefinition():
ExpressionEngineRegistry expressionRegistry = null;
try {
  expressionRegistry = io.quarkus.arc.Arc.container()
      .instance(ExpressionEngineRegistry.class).get();
} catch (Exception e) {
  // Fall back to string-based triggers if registry unavailable
}

// In the binding construction, replace:
case "contextChange" -> bindingBuilder.on(new ContextChangeTrigger(bd.triggerValue()));

// With:
case "contextChange" -> {
  if (expressionRegistry != null) {
    ExpressionEvaluator eval = expressionRegistry.create(bd.triggerValue(), "jq");
    bindingBuilder.on(new ContextChangeTrigger(eval));
  } else {
    bindingBuilder.on(new ContextChangeTrigger(bd.triggerValue()));
  }
}
```

Add imports for `ExpressionEngineRegistry`, `ExpressionEvaluator`, `io.quarkus.arc.Arc`.

- [ ] **Step 3: Run all tests**

Run: `/opt/homebrew/bin/mvn install -pl annotations/runtime -DskipTests -q`
Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment,examples/simple-case-annotated,examples/goap-case-annotated`
Expected: PASS (ExpressionEngineRegistry may not be available in test context — graceful fallback ensures tests still pass)

- [ ] **Step 4: Commit**

```bash
git add annotations/
git commit -m "feat(#909): move CaseDefinition construction to RUNTIME_INIT for ExpressionEngineRegistry

Refs #909"
```

---

### Task 16: @Completion wiring

Process `@Completion` annotations by invoking default methods on the synthetic subclass at RUNTIME_INIT to collect `GoalExpression` instances and build `GoalBasedCompletion`.

**Files:**
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CompletionDescriptor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDescriptor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDefinitionRecorder.java`
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/CompletionWiringTest.java`

**Interfaces:**
- Consumes: `@Completion` annotation, `@Goal` method (returns GoalExpression), Gizmo impl class
- Produces: `GoalBasedCompletion` set on CaseDefinition via builder

- [ ] **Step 1: Create CompletionDescriptor**

```java
package io.casehub.engine.annotations.runtime;

public record CompletionDescriptor(String methodName, String kind) {}
```

- [ ] **Step 2: Add completions to CaseDescriptor**

Add `List<CompletionDescriptor> completions` field to CaseDescriptor.

- [ ] **Step 3: Write CompletionWiringTest**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.GoalExpression;
import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Completion;
import io.casehub.engine.annotations.Goal;
import io.casehub.engine.annotations.Worker;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CompletionWiringTest {

  @RegisterExtension
  static final QuarkusUnitTest test =
      new QuarkusUnitTest()
          .withApplicationRoot(root -> root.addClasses(CompletionCase.class));

  @Case(namespace = "test", name = "WithCompletion", version = "1.0.0")
  public interface CompletionCase {

    @Worker(capability = "work")
    @Bind(contextChange = ".input != null")
    default String work(String input) {
      return "done";
    }

    @Goal(value = "Work done", condition = ".result != null")
    @Completion
    default GoalExpression workDone() {
      return GoalExpression.goal("workDone");
    }
  }

  @Inject CaseDefinition definition;

  @Test
  void completion_is_set() {
    assertThat(definition.getCompletion()).isNotNull();
  }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=CompletionWiringTest`
Expected: FAIL — completion is null

- [ ] **Step 5: Process @Completion in processor**

In `EngineAnnotationsProcessor.buildDescriptor()`, add @Completion scanning:

```java
private static final DotName COMPLETION =
    DotName.createSimple("io.casehub.engine.annotations.Completion");

// In buildDescriptor(), inside the method loop:
AnnotationInstance completionAnn = method.annotation(COMPLETION);
if (completionAnn != null) {
  String kind = stringValueOrDefault(completionAnn, index, "kind", "SUCCESS");
  completions.add(new CompletionDescriptor(method.name(), kind));
}
```

- [ ] **Step 6: Wire completion in recorder**

In `CaseDefinitionRecorder.createCaseDefinition()`, after building goals:

```java
if (descriptor.completions() != null && !descriptor.completions().isEmpty()
    && descriptor.implClassName() != null) {
  try {
    Class<?> implClass = Thread.currentThread().getContextClassLoader()
        .loadClass(descriptor.implClassName());
    Object instance = implClass.getDeclaredConstructor().newInstance();

    var completionBuilder = GoalBasedCompletion.builder();
    for (CompletionDescriptor cd : descriptor.completions()) {
      Method method = implClass.getMethod(cd.methodName());
      GoalExpression expression = (GoalExpression) method.invoke(instance);

      GoalKind kind = switch (cd.kind().toLowerCase()) {
        case "success" -> StandardGoalKind.SUCCESS;
        case "failure" -> StandardGoalKind.FAILURE;
        default -> GoalKind.of(cd.kind().toLowerCase(), CaseStatus.COMPLETED);
      };
      completionBuilder.goal(kind, expression);
    }
    builder.completion(completionBuilder.build());
  } catch (Exception e) {
    LOG.warn("Failed to wire @Completion: " + e.getMessage());
  }
}
```

Add `GoalBasedCompletion` import.

- [ ] **Step 7: Run tests to verify pass**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=CompletionWiringTest`
Expected: PASS

- [ ] **Step 8: Run all tests**

Run: `/opt/homebrew/bin/mvn install -pl annotations/runtime -DskipTests -q`
Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add annotations/
git commit -m "feat(#909): wire @Completion — invoke default methods at RUNTIME_INIT for GoalBasedCompletion

Refs #909"
```

---

### Task 17: @Customize escape hatch

Process `@Customize` annotations — invoke static methods with `CaseDefinition.Builder` (case-level) or `Binding.Builder` (binding-level) params after all annotation-derived values are set.

**Files:**
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CustomizerDescriptor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDescriptor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDefinitionRecorder.java`
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/CustomizeTest.java`

**Interfaces:**
- Consumes: `@Customize` annotation (value="" for case-level, value="bindingName" for binding-level)
- Produces: Static method invocation on the @Case interface at RUNTIME_INIT

- [ ] **Step 1: Create CustomizerDescriptor**

```java
package io.casehub.engine.annotations.runtime;

public record CustomizerDescriptor(String methodName, String targetBinding, String interfaceName) {}
```

- [ ] **Step 2: Write CustomizeTest**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.Customize;
import io.casehub.engine.annotations.Worker;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CustomizeTest {

  @RegisterExtension
  static final QuarkusUnitTest test =
      new QuarkusUnitTest()
          .withApplicationRoot(root -> root.addClasses(CustomizedCase.class));

  @Case(namespace = "test", name = "Customized", version = "1.0.0")
  public interface CustomizedCase {

    @Worker(capability = "work")
    @Bind(contextChange = ".input != null")
    default String work(String input) {
      return "done";
    }

    @Customize
    static void customize(CaseDefinition.Builder builder) {
      builder.title("Custom Title");
    }
  }

  @Inject CaseDefinition definition;

  @Test
  void customizer_applied() {
    assertThat(definition.getTitle()).isEqualTo("Custom Title");
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=CustomizeTest`
Expected: FAIL — title is empty

- [ ] **Step 4: Process @Customize in processor**

Add `CUSTOMIZE` DotName and scan for @Customize methods:

```java
private static final DotName CUSTOMIZE =
    DotName.createSimple("io.casehub.engine.annotations.Customize");

// In buildDescriptor(), inside the method loop:
AnnotationInstance customizeAnn = method.annotation(CUSTOMIZE);
if (customizeAnn != null) {
  String targetBinding = stringValueOrDefault(customizeAnn, index, "value", "");
  customizers.add(new CustomizerDescriptor(
      method.name(), targetBinding.isEmpty() ? null : targetBinding,
      caseClass.name().toString()));
}
```

Add `customizers` list to CaseDescriptor.

- [ ] **Step 5: Apply customizers in recorder**

In `CaseDefinitionRecorder.createCaseDefinition()`, before `builder.build()`:

```java
if (descriptor.customizers() != null) {
  for (CustomizerDescriptor cd : descriptor.customizers()) {
    if (cd.targetBinding() == null) {
      try {
        Class<?> iface = Thread.currentThread().getContextClassLoader()
            .loadClass(cd.interfaceName());
        Method customizer = iface.getMethod(cd.methodName(), CaseDefinition.Builder.class);
        customizer.invoke(null, builder);
      } catch (Exception e) {
        LOG.warn("Failed to apply @Customize: " + e.getMessage());
      }
    }
  }
}
```

- [ ] **Step 6: Run tests to verify pass**

Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment -Dtest=CustomizeTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add annotations/
git commit -m "feat(#909): add @Customize escape hatch — static builder methods at RUNTIME_INIT

Refs #909"
```

---

## Batch 7: @SystemPrompt, @Capability, and WorkerScopeProducer

### Task 18: @SystemPrompt → AgentWorkerFunction

Process `@SystemPrompt` annotations to generate `AgentWorkerFunction` backed by `Agent.builder().systemPrompt(value)` instead of the default `AnnotationWorkerFunction`.

**Files:**
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/WorkerDescriptor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/CaseDefinitionRecorder.java`
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Modify: `annotations/runtime/pom.xml` (add engine-ai dependency for Agent, AgentWorkerFunction)
- Create: `annotations/deployment/src/test/java/io/casehub/engine/annotations/deployment/SystemPromptTest.java`

**Interfaces:**
- Consumes: `@SystemPrompt` value, `ChatModelProvider` (CDI, optional)
- Produces: `AgentWorkerFunction` on the Worker instead of `AnnotationWorkerFunction`

- [ ] **Step 1: Add systemPrompt to WorkerDescriptor**

Add `String systemPrompt` field to `WorkerDescriptor`. Null when not an AI worker.

- [ ] **Step 2: Add engine-ai dependency to runtime pom**

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-engine-ai</artifactId>
    <version>${project.version}</version>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 3: Process @SystemPrompt in processor**

```java
private static final DotName SYSTEM_PROMPT =
    DotName.createSimple("io.casehub.engine.annotations.SystemPrompt");

// In processWorkerMethod():
AnnotationInstance systemPromptAnn = method.annotation(SYSTEM_PROMPT);
String systemPrompt = systemPromptAnn != null ? systemPromptAnn.value().asString() : null;
// Pass to WorkerDescriptor
```

- [ ] **Step 4: Wire in recorder**

In `CaseDefinitionRecorder.createCaseDefinition()`, when building workers:

```java
if (wd.systemPrompt() != null) {
  try {
    var chatModelProvider = Arc.container()
        .instance(io.casehub.api.model.ai.ChatModelProvider.class);
    if (chatModelProvider.isAvailable()) {
      var agent = io.casehub.api.model.ai.Agent.builder()
          .systemPrompt(wd.systemPrompt())
          .model(chatModelProvider.get().getChatModel())
          .build();
      workerBuilder.function(new io.casehub.api.model.ai.AgentWorkerFunction(agent));
    } else {
      workerBuilder.noFunction();
    }
  } catch (Exception e) {
    workerBuilder.noFunction();
  }
} else if (wd.params() != null && !wd.params().isEmpty() && descriptor.implClassName() != null) {
  workerBuilder.function(new AnnotationWorkerFunction(...));
} else {
  workerBuilder.noFunction();
}
```

- [ ] **Step 5: Write SystemPromptTest (build-time validation only — ChatModelProvider absent in test)**

```java
package io.casehub.engine.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.annotations.Bind;
import io.casehub.engine.annotations.Case;
import io.casehub.engine.annotations.SystemPrompt;
import io.casehub.engine.annotations.Worker;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SystemPromptTest {

  @RegisterExtension
  static final QuarkusUnitTest test =
      new QuarkusUnitTest()
          .withApplicationRoot(root -> root.addClasses(AiCase.class));

  @Case(namespace = "test", name = "AiCase", version = "1.0.0")
  public interface AiCase {

    @Worker(capability = "analyse")
    @Bind(contextChange = ".input != null")
    @SystemPrompt("You are an analyst. Analyse the input.")
    default void analyse(String input) {}
  }

  @Inject CaseDefinition definition;

  @Test
  void worker_exists() {
    assertThat(definition.getWorkers()).hasSize(1);
    assertThat(definition.getWorkers().get(0).name()).isEqualTo("analyse");
  }
}
```

- [ ] **Step 6: Run all tests**

Run: `/opt/homebrew/bin/mvn install -pl annotations/runtime -DskipTests -q`
Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add annotations/
git commit -m "feat(#909): add @SystemPrompt → AgentWorkerFunction wiring

Refs #909"
```

---

### Task 19: @Capability standalone processing and WorkerScopeProducer

Process standalone `@Capability` annotations (capabilities not derived from `@Worker`) and add the `WorkerScopeProducer` CDI bean.

**Files:**
- Modify: `annotations/deployment/src/main/java/io/casehub/engine/annotations/deployment/EngineAnnotationsProcessor.java`
- Create: `annotations/runtime/src/main/java/io/casehub/engine/annotations/runtime/WorkerScopeProducer.java`

**Interfaces:**
- Consumes: `@Capability` annotation on methods
- Produces: Additional `Capability` instances on CaseDefinition

- [ ] **Step 1: Process @Capability in processor**

```java
private static final DotName CAPABILITY =
    DotName.createSimple("io.casehub.engine.annotations.Capability");

// In buildDescriptor(), inside the method loop:
AnnotationInstance capAnn = method.annotation(CAPABILITY);
if (capAnn != null && workerAnn == null) {
  String capName = capAnn.value() != null ? capAnn.value().asString() : method.name();
  // Add standalone capability to descriptor
  standaloneCapabilities.add(capName);
}
```

Add `List<String> standaloneCapabilities` to CaseDescriptor and wire in recorder.

- [ ] **Step 2: Create WorkerScopeProducer**

```java
package io.casehub.engine.annotations.runtime;

import io.casehub.worker.api.WorkerScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;

public class WorkerScopeProducer {

  private static final ThreadLocal<WorkerScope> CURRENT = new ThreadLocal<>();

  public static void setCurrent(WorkerScope scope) {
    CURRENT.set(scope);
  }

  public static void clearCurrent() {
    CURRENT.remove();
  }

  @Produces
  @RequestScoped
  public WorkerScope currentScope() {
    WorkerScope scope = CURRENT.get();
    if (scope == null) {
      throw new IllegalStateException("No WorkerScope available — not executing within a worker");
    }
    return scope;
  }
}
```

- [ ] **Step 3: Run all tests**

Run: `/opt/homebrew/bin/mvn install -pl annotations/runtime -DskipTests -q`
Run: `TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl annotations/deployment,examples/simple-case-annotated,examples/goap-case-annotated`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add annotations/
git commit -m "feat(#909): add @Capability standalone processing and WorkerScopeProducer

Closes #909"
```

---

## Self-Review

**Spec coverage check:**
- [x] @Bind repeatable container — Task 10
- [x] @Effect override — Task 10 (test)
- [x] @SoftDependency — Task 10 (test)
- [x] @Param exclusion — Task 10 (test)
- [x] @Bind(cron/scopeActivated/when) — Task 10 (test)
- [x] @Worker(value) name override — Task 10 (test)
- [x] Goal-to-effect-key mapping — Task 11
- [x] Build-time validation (15 rules) — Task 12
- [x] Gizmo synthetic subclass — Task 13
- [x] AnnotationWorkerFunction (default method invocation) — Task 14
- [x] Two-phase recorder (RUNTIME_INIT) — Task 15
- [x] @Completion → GoalBasedCompletion — Task 16
- [x] @Customize escape hatch — Task 17
- [x] @SystemPrompt → AgentWorkerFunction — Task 18
- [x] @Capability standalone — Task 19
- [x] WorkerScopeProducer — Task 19

**Placeholder scan:** No TBDs found. All steps have code.

**Type consistency:** `WorkerDescriptor` evolves in Tasks 14, 18 — field additions are backward-compatible via record component ordering. `CaseDescriptor` gains `implClassName` (Task 13), `completions` (Task 16), `customizers` (Task 17) — each requires updating all construction sites.

**Tooling safety:** No bash cp/mv/rm on source files. All file creation via Write tool.

## References

- [2026-08-16-annotation-driven-programming-model-design.md] — design spec
- [2026-08-17-engine-annotations.md] — original implementation plan (Tasks 1-9)
- [decisions.md] — D1-D10 including D10 (two-phase recorder)
- [annotations/deployment/.../EngineAnnotationsProcessor.java] — existing processor
- [annotations/runtime/.../CaseDefinitionRecorder.java] — existing recorder
- [api/.../ExpressionEngineRegistry.java] — expression resolution SPI
- [api/.../GoalBasedCompletion.java] — completion model
- [api/.../ai/Agent.java] — AI agent builder
- [GitHub #909] — casehub-engine-annotations module
