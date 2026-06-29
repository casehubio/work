package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import io.casehub.work.api.LabelPersistence;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;

/**
 * Pure unit tests for template merge and payload logic.
 * No Quarkus, no DB — exercises mergeRequestWithTemplate() and mergePayload() only.
 */
class WorkItemTemplateServiceTest {

    private static final UUID DUMMY_TEMPLATE_ID = UUID.randomUUID();

    // ── mergeRequestWithTemplate: template defaults ──────────────────────────

    @Test
    void merge_usesTemplateNameAsTitle_whenRequestTitleNull() {
        final WorkItemTemplate t = template("Contract Review");
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.title).isEqualTo("Contract Review");
    }

    @Test
    void merge_usesRequestTitle_whenProvided() {
        final WorkItemTemplate t = template("Default Title");
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .templateId(DUMMY_TEMPLATE_ID).title("Specific Contract #44").createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.title).isEqualTo("Specific Contract #44");
    }

    @Test
    void merge_copiesCategory() {
        final WorkItemTemplate t = template("T");
        t.category = "legal";
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.category).isEqualTo("legal");
    }

    @Test
    void merge_copiesPriority() {
        final WorkItemTemplate t = template("T");
        t.priority = WorkItemPriority.HIGH;
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.priority).isEqualTo(WorkItemPriority.HIGH);
    }

    @Test
    void merge_copiesCandidateGroups() {
        final WorkItemTemplate t = template("T");
        t.candidateGroups = "legal-team,compliance-team";
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.candidateGroups).isEqualTo("legal-team,compliance-team");
    }

    @Test
    void merge_copiesDefaultPayload() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"nda\"}";
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.payload).isEqualTo("{\"type\":\"nda\"}");
    }

    @Test
    void merge_copiesScope() {
        final WorkItemTemplate t = template("T");
        t.scope = "casehubio/devtown/pr-review";
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.scope).isEqualTo("casehubio/devtown/pr-review");
    }

    @Test
    void merge_scopeIsNullWhenTemplateHasNoScope() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.scope).isNull();
    }

    @Test
    void merge_setsCreatedBy() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("finance-bot").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.createdBy).isEqualTo("finance-bot");
    }

    @Test
    void merge_setsAssigneeFromRequest() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .templateId(DUMMY_TEMPLATE_ID).assigneeId("alice").createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.assigneeId).isEqualTo("alice");
    }

    @Test
    void merge_nullFields_whenTemplateHasNoDefaults() {
        final WorkItemTemplate t = template("Minimal");
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.category).isNull();
        assertThat(merged.priority).isNull();
        assertThat(merged.candidateGroups).isNull();
        assertThat(merged.payload).isNull();
        assertThat(merged.assigneeId).isNull();
    }

    @Test
    void merge_setsCallerRef_whenProvided() {
        final WorkItemTemplate t = template("IRB Review");
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:irb-gate";
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .templateId(DUMMY_TEMPLATE_ID).callerRef(callerRef).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.callerRef).isEqualTo(callerRef);
    }

    @Test
    void merge_callerRefNull_whenNotProvided() {
        final WorkItemTemplate t = template("IRB Review");
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder().templateId(DUMMY_TEMPLATE_ID).createdBy("system").build();
        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(t, req, null);
        assertThat(merged.callerRef).isNull();
    }

    // ── parseLabels ─────────────────────────────────────────────────────────

    @Test
    void parseLabels_returnsEmptyList_whenNull() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = null;
        assertThat(WorkItemTemplateService.parseLabels(t)).isEmpty();
    }

    @Test
    void parseLabels_parsesJsonArray() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[\"intake/triage\",\"priority/high\"]";
        final var labels = WorkItemTemplateService.parseLabels(t);
        assertThat(labels).hasSize(2);
        assertThat(labels).extracting(l -> l.path)
                .containsExactly("intake/triage", "priority/high");
        assertThat(labels).extracting(l -> l.persistence)
                .containsOnly(LabelPersistence.MANUAL);
    }

    @Test
    void parseLabels_handlesEmptyArray() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[]";
        assertThat(WorkItemTemplateService.parseLabels(t)).isEmpty();
    }

    @Test
    void parseLabels_setsAppliedByToTemplate() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[\"security/incident\"]";
        final var labels = WorkItemTemplateService.parseLabels(t);
        assertThat(labels.get(0).appliedBy).isEqualTo("template");
    }

    // ── mergePayload ────────────────────────────────────────────────────────

    @Test
    void mergePayload_overlayNonNull_usesOverlay() {
        final String result = WorkItemTemplateService.mergePayload("{\"type\":\"default\"}", "{\"type\":\"override\"}");
        assertThat(result).isEqualTo("{\"type\":\"override\"}");
    }

    @Test
    void mergePayload_overlayNull_usesBase() {
        assertThat(WorkItemTemplateService.mergePayload("{\"type\":\"default\"}", null))
                .isEqualTo("{\"type\":\"default\"}");
    }

    @Test
    void mergePayload_overlayBlank_usesBase() {
        assertThat(WorkItemTemplateService.mergePayload("{\"type\":\"default\"}", "   "))
                .isEqualTo("{\"type\":\"default\"}");
    }

    @Test
    void mergePayload_baseNull_usesOverlay() {
        assertThat(WorkItemTemplateService.mergePayload(null, "{\"case\":\"42\"}"))
                .isEqualTo("{\"case\":\"42\"}");
    }

    @Test
    void mergePayload_disjointKeys_bothPreserved() {
        final String result = WorkItemTemplateService.mergePayload("{\"type\":\"default\"}", "{\"case\":\"42\"}");
        assertThat(result).contains("\"type\":\"default\"");
        assertThat(result).contains("\"case\":\"42\"");
    }

    @Test
    void mergePayload_overlayKeyWinsConflict_baseUniqueKeysPreserved() {
        final String result = WorkItemTemplateService.mergePayload(
                "{\"type\":\"base\",\"extra\":\"kept\"}", "{\"type\":\"override\"}");
        assertThat(result).contains("\"type\":\"override\"");
        assertThat(result).contains("\"extra\":\"kept\"");
    }

    @Test
    void mergePayload_nestedObjects_deepMerged() {
        final String result = WorkItemTemplateService.mergePayload("{\"data\":{\"x\":1}}", "{\"data\":{\"y\":2}}");
        assertThat(result).contains("\"x\":1");
        assertThat(result).contains("\"y\":2");
    }

    @Test
    void mergePayload_nonObjectOverlay_overlayWins() {
        final String result = WorkItemTemplateService.mergePayload("{\"type\":\"base\"}", "\"just a string\"");
        assertThat(result).isEqualTo("\"just a string\"");
    }

    @Test
    void mergePayload_arrayOverlay_overlayWins() {
        final String result = WorkItemTemplateService.mergePayload("{\"type\":\"base\"}", "[1,2,3]");
        assertThat(result).isEqualTo("[1,2,3]");
    }

    @Test
    void mergePayload_bothNull_returnsNull() {
        assertThat(WorkItemTemplateService.mergePayload(null, null)).isNull();
    }

    @Test
    void mergePayload_malformedBase_returnsOverlay() {
        assertThat(WorkItemTemplateService.mergePayload("not-json{{{", "{\"key\":\"value\"}"))
                .isEqualTo("{\"key\":\"value\"}");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private WorkItemTemplate template(final String name) {
        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = name;
        t.createdBy = "admin";
        return t;
    }
}
