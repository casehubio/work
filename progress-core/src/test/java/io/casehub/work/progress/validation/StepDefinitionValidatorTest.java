package io.casehub.work.progress.validation;

import io.casehub.work.progress.StepDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepDefinitionValidatorTest {

    private final StepDefinitionValidator validator = new StepDefinitionValidator();

    @Test
    void validLinearChainPasses() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", false, List.of("a"), null),
                new StepDefinition("c", false, List.of("b"), null)
        );
        assertThatCode(() -> validator.validate(steps)).doesNotThrowAnyException();
    }

    @Test
    void validDagPasses() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", false, List.of("a"), null),
                new StepDefinition("c", false, List.of("a"), null),
                new StepDefinition("d", false, List.of("b", "c"), null)
        );
        assertThatCode(() -> validator.validate(steps)).doesNotThrowAnyException();
    }

    @Test
    void directCycleRejects() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of("b"), null),
                new StepDefinition("b", false, List.of("a"), null)
        );
        assertThatThrownBy(() -> validator.validate(steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void selfReferenceRejects() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of("a"), null)
        );
        assertThatThrownBy(() -> validator.validate(steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void transitiveCycleRejects() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of("c"), null),
                new StepDefinition("b", false, List.of("a"), null),
                new StepDefinition("c", false, List.of("b"), null)
        );
        assertThatThrownBy(() -> validator.validate(steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void unknownDependencyRejects() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of("nonexistent"), null)
        );
        assertThatThrownBy(() -> validator.validate(steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void allOptionalRejects() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", true, List.of(), null),
                new StepDefinition("b", true, List.of(), null)
        );
        assertThatThrownBy(() -> validator.validate(steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one required");
    }

    @Test
    void atLeastOneRequiredPasses() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("b", true, List.of(), null)
        );
        assertThatCode(() -> validator.validate(steps)).doesNotThrowAnyException();
    }

    @Test
    void duplicateStepNamesReject() {
        List<StepDefinition> steps = List.of(
                new StepDefinition("a", false, List.of(), null),
                new StepDefinition("a", false, List.of(), null)
        );
        assertThatThrownBy(() -> validator.validate(steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void emptyStepListRejects() {
        assertThatThrownBy(() -> validator.validate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
