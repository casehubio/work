package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.progress.ConditionEvaluator;
import io.casehub.work.progress.StepDefinition;
import io.casehub.work.progress.StepStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepValidatorTest {

    private final StepValidator validator = new StepValidator();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConditionEvaluator alwaysTrue = (expr, ctx) -> true;
    private final ConditionEvaluator alwaysFalse = (expr, ctx) -> false;

    private final List<StepDefinition> linearChain = List.of(
            new StepDefinition("a", false, List.of(), null),
            new StepDefinition("b", false, List.of("a"), null),
            new StepDefinition("c", false, List.of("b"), null)
    );

    private ObjectNode stepsState(String... nameStatusPairs) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode steps = root.putObject("steps");
        for (int i = 0; i < nameStatusPairs.length; i += 2) {
            steps.putObject(nameStatusPairs[i]).put("status", nameStatusPairs[i + 1]);
        }
        return root;
    }

    @Test
    void activateRootStepWithNoDeps() {
        ObjectNode state = stepsState("a", "pending", "b", "pending", "c", "pending");
        assertThatCode(() -> validator.validateTransition("a", StepStatus.PENDING, StepStatus.ACTIVE,
                linearChain, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void activateStepWithDepCompleted() {
        ObjectNode state = stepsState("a", "completed", "b", "pending", "c", "pending");
        assertThatCode(() -> validator.validateTransition("b", StepStatus.PENDING, StepStatus.ACTIVE,
                linearChain, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void activateStepWithDepPendingRejects() {
        ObjectNode state = stepsState("a", "pending", "b", "pending", "c", "pending");
        assertThatThrownBy(() -> validator.validateTransition("b", StepStatus.PENDING, StepStatus.ACTIVE,
                linearChain, state, alwaysTrue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dependency");
    }

    @Test
    void activateStepWithDepSkippedPasses() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", true, List.of(), null),
                new StepDefinition("b", false, List.of("a"), null)
        );
        ObjectNode state = stepsState("a", "skipped", "b", "pending");
        assertThatCode(() -> validator.validateTransition("b", StepStatus.PENDING, StepStatus.ACTIVE,
                steps, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void skipNonOptionalRejects() {
        ObjectNode state = stepsState("a", "active", "b", "pending", "c", "pending");
        assertThatThrownBy(() -> validator.validateTransition("a", StepStatus.ACTIVE, StepStatus.SKIPPED,
                linearChain, state, alwaysTrue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("optional");
    }

    @Test
    void skipOptionalPasses() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", true, List.of("a"), null)
        );
        ObjectNode state = stepsState("a", "completed", "b", "active");
        assertThatCode(() -> validator.validateTransition("b", StepStatus.ACTIVE, StepStatus.SKIPPED,
                steps, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void completeActiveStepPasses() {
        ObjectNode state = stepsState("a", "active", "b", "pending", "c", "pending");
        assertThatCode(() -> validator.validateTransition("a", StepStatus.ACTIVE, StepStatus.COMPLETED,
                linearChain, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void completePendingStepRejects() {
        ObjectNode state = stepsState("a", "pending", "b", "pending", "c", "pending");
        assertThatThrownBy(() -> validator.validateTransition("a", StepStatus.PENDING, StepStatus.COMPLETED,
                linearChain, state, alwaysTrue))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failActiveStepPasses() {
        ObjectNode state = stepsState("a", "active", "b", "pending", "c", "pending");
        assertThatCode(() -> validator.validateTransition("a", StepStatus.ACTIVE, StepStatus.FAILED,
                linearChain, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void reactivateCompletedStepPasses() {
        ObjectNode state = stepsState("a", "completed", "b", "pending", "c", "pending");
        assertThatCode(() -> validator.validateTransition("a", StepStatus.COMPLETED, StepStatus.ACTIVE,
                linearChain, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void reactivateFailedStepPasses() {
        ObjectNode state = stepsState("a", "failed", "b", "pending", "c", "pending");
        assertThatCode(() -> validator.validateTransition("a", StepStatus.FAILED, StepStatus.ACTIVE,
                linearChain, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void transitionFromSkippedRejects() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", true, List.of(), null),
                new StepDefinition("b", false, List.of(), null)
        );
        ObjectNode state = stepsState("a", "skipped", "b", "pending");
        assertThatThrownBy(() -> validator.validateTransition("a", StepStatus.SKIPPED, StepStatus.ACTIVE,
                steps, state, alwaysTrue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void conditionFalseRejectsActivation() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), ".value > 10")
        );
        ObjectNode state = stepsState("a", "pending");
        assertThatThrownBy(() -> validator.validateTransition("a", StepStatus.PENDING, StepStatus.ACTIVE,
                steps, state, alwaysFalse))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("condition");
    }

    @Test
    void conditionTrueAllowsActivation() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), ".value > 10")
        );
        ObjectNode state = stepsState("a", "pending");
        assertThatCode(() -> validator.validateTransition("a", StepStatus.PENDING, StepStatus.ACTIVE,
                steps, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void parallelBranchesActivateIndependently() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("root", false, List.of(), null),
                new StepDefinition("branch1", false, List.of("root"), null),
                new StepDefinition("branch2", false, List.of("root"), null)
        );
        ObjectNode state = stepsState("root", "completed", "branch1", "pending", "branch2", "pending");
        assertThatCode(() -> validator.validateTransition("branch1", StepStatus.PENDING, StepStatus.ACTIVE,
                steps, state, alwaysTrue)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition("branch2", StepStatus.PENDING, StepStatus.ACTIVE,
                steps, state, alwaysTrue)).doesNotThrowAnyException();
    }

    @Test
    void derivedCompletionWhenAllRequiredDone() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", false, List.of("a"), null)
        );
        ObjectNode state = stepsState("a", "completed", "b", "completed");
        assertThat(validator.isDerivedCompletion(steps, state)).isTrue();
    }

    @Test
    void derivedCompletionWithOptionalPending() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", true, List.of("a"), null)
        );
        ObjectNode state = stepsState("a", "completed", "b", "pending");
        assertThat(validator.isDerivedCompletion(steps, state)).isTrue();
    }

    @Test
    void noDerivedCompletionWithRequiredPending() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", false, List.of("a"), null)
        );
        ObjectNode state = stepsState("a", "completed", "b", "pending");
        assertThat(validator.isDerivedCompletion(steps, state)).isFalse();
    }

    @Test
    void noDerivedCompletionWithRequiredFailed() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", false, List.of("a"), null)
        );
        ObjectNode state = stepsState("a", "completed", "b", "failed");
        assertThat(validator.isDerivedCompletion(steps, state)).isFalse();
    }

    @Test
    void derivedCompletionWithOptionalSkipped() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", true, List.of("a"), null)
        );
        ObjectNode state = stepsState("a", "completed", "b", "skipped");
        assertThat(validator.isDerivedCompletion(steps, state)).isTrue();
    }
}
