package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.Outcome;
import io.casehub.work.runtime.model.OutcomeCodecs;
import io.casehub.work.runtime.model.WorkItem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for {@link OutcomeValidator}. Uses @QuarkusTest to wire the CDI graph
 * (JexlConditionEvaluator dependency) without starting HTTP.
 */
@QuarkusTest
class OutcomeValidatorTest {

    @Inject
    OutcomeValidator validator;

    private WorkItem item;

    @BeforeEach
    void setup() {
        item = new WorkItem();
        item.id = UUID.randomUUID();
        item.permittedOutcomes = null;
    }

    // ── Unconstrained WorkItems ──────────────────────────────────────────────

    @Test
    void validate_noPermittedOutcomes_anyOutcomeAccepted() {
        item.permittedOutcomes = null;
        assertThatCode(() -> validator.validate(item, "anything", null, null, "actor"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_noPermittedOutcomes_nullOutcomeAccepted() {
        item.permittedOutcomes = null;
        assertThatCode(() -> validator.validate(item, null, null, null, "actor"))
                .doesNotThrowAnyException();
    }

    // ── Name validation ──────────────────────────────────────────────────────

    @Test
    void validate_outcomeInList_accepted() {
        setOutcomes(new Outcome("approved", "Approved", null));
        assertThatCode(() -> validator.validate(item, "approved", null, null, "actor"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_outcomeNotInList_throws() {
        setOutcomes(new Outcome("approved", "Approved", null));
        assertThatThrownBy(() -> validator.validate(item, "deferred", null, null, "actor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deferred")
                .hasMessageContaining("approved");
    }

    @Test
    void validate_nullOutcome_whenPermittedDeclared_throws() {
        setOutcomes(new Outcome("approved", null, null));
        assertThatThrownBy(() -> validator.validate(item, null, null, null, "actor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_blankOutcome_whenPermittedDeclared_throws() {
        setOutcomes(new Outcome("approved", null, null));
        assertThatThrownBy(() -> validator.validate(item, "  ", null, null, "actor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_outcomeTooLong_throws() {
        setOutcomes(new Outcome("approved", null, null));
        final String tooLong = "a".repeat(256);
        assertThatThrownBy(() -> validator.validate(item, tooLong, null, null, "actor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    // ── Condition evaluation ─────────────────────────────────────────────────

    @Test
    void validate_nullCondition_acceptedByNameOnly() {
        setOutcomes(new Outcome("approved", "Approved", null));
        assertThatCode(() -> validator.validate(item, "approved", "some resolution", null, "actor"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_conditionTrue_accepted() {
        // Condition tests actorId variable
        setOutcomes(new Outcome("manager-approve", null, "actorId.startsWith('mgr-')"));
        assertThatCode(() -> validator.validate(item, "manager-approve", null, null, "mgr-alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_conditionFalse_throws() {
        setOutcomes(new Outcome("manager-approve", null, "actorId.startsWith('mgr-')"));
        assertThatThrownBy(() -> validator.validate(item, "manager-approve", null, null, "regular-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manager-approve")
                .hasMessageContaining("condition not satisfied");
    }

    @Test
    void validate_conditionReferencesResolution_onCompletePath_works() {
        setOutcomes(new Outcome("approved", null, "resolution != null && resolution.contains('APPROVED')"));
        // complete path: resolution is non-null
        assertThatCode(() -> validator.validate(item, "approved", "APPROVED by manager", null, "actor"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_conditionReferencesResolution_onRejectPath_fails() {
        // On reject paths, resolution is null — condition checking it should fail
        setOutcomes(new Outcome("approved", null, "resolution != null"));
        assertThatThrownBy(() -> validator.validate(item, "approved", null, "reason text", "actor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("condition not satisfied");
    }

    @Test
    void validate_conditionReferencesReason_onRejectPath_works() {
        setOutcomes(new Outcome("reject", null, "reason != null && reason.length() > 0"));
        assertThatCode(() -> validator.validate(item, "reject", null, "Not acceptable", "actor"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_conditionReferencesActorId_works() {
        setOutcomes(new Outcome("escalate", null, "actorId == 'senior-reviewer'"));
        assertThatCode(() -> validator.validate(item, "escalate", null, null, "senior-reviewer"))
                .doesNotThrowAnyException();
    }

    // ── workItem.* context ──────────────────────────────────────────────────────

    @Test
    void validate_conditionReferencesWorkItemStatus_accepted() {
        item.status = io.casehub.work.runtime.model.WorkItemStatus.IN_PROGRESS;
        setOutcomes(new Outcome("approve", null, "workItem.status.name() == 'IN_PROGRESS'"));
        assertThatCode(() -> validator.validate(item, "approve", null, null, "actor"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_conditionReferencesWorkItemField_nullField_silentFalse() {
        // workItem.priority is null — JEXL silent mode returns false, not NPE
        item.status = null;
        setOutcomes(new Outcome("approve", null, "workItem.status.name() == 'IN_PROGRESS'"));
        assertThatThrownBy(() -> validator.validate(item, "approve", null, null, "actor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("condition not satisfied");
    }

    // ── Legacy format fallback ───────────────────────────────────────────────

    @Test
    void validate_legacyStringArrayFormat_stillWorks() {
        // Old format: plain string array, not Outcome objects
        item.permittedOutcomes = "[\"approved\",\"rejected\"]";
        assertThatCode(() -> validator.validate(item, "approved", null, null, "actor"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_legacyStringArrayFormat_invalidOutcome_throws() {
        item.permittedOutcomes = "[\"approved\",\"rejected\"]";
        assertThatThrownBy(() -> validator.validate(item, "deferred", null, null, "actor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Corrupt data guard ───────────────────────────────────────────────────

    @Test
    void validate_corruptPermittedOutcomes_throwsIllegalState() {
        item.permittedOutcomes = "not valid json {{{{";
        assertThatThrownBy(() -> validator.validate(item, "approved", null, null, "actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data integrity error");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setOutcomes(final Outcome... outcomes) {
        item.permittedOutcomes = OutcomeCodecs.encodeOutcomes(java.util.List.of(outcomes));
    }
}
