package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the static {@link WorkItemTemplateService#mergeRequestWithTemplate}
 * method — the merge semantics used by {@code createFromTemplate}.
 *
 * <p>
 * These are pure static tests with no CDI or JPA. The method is package-private
 * so the test lives in the same package.
 */
class WorkItemTemplateServiceCreateFromTemplateTest {

    // -----------------------------------------------------------------------
    // mergeRequestWithTemplate — request-wins semantics
    // -----------------------------------------------------------------------

    @Test
    void requestTitleOverridesTemplateName() {
        final WorkItemTemplate template = templateWith("Template Title");
        final WorkItemCreateRequest request = request("Override Title");

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        assertThat(merged.title).isEqualTo("Override Title");
    }

    @Test
    void requestFieldsOverrideTemplateDefaults() {
        final WorkItemTemplate template = templateWithAllDefaults();
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("Custom Title")
                .description("Custom Desc")
                .category("custom-cat")
                .priority(WorkItemPriority.URGENT)
                .candidateGroups("custom-group")
                .candidateUsers("custom-user")
                .requiredCapabilities("skill-x")
                .createdBy("custom-creator")
                .build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        assertThat(merged.title).isEqualTo("Custom Title");
        assertThat(merged.description).isEqualTo("Custom Desc");
        assertThat(merged.category).isEqualTo("custom-cat");
        assertThat(merged.priority).isEqualTo(WorkItemPriority.URGENT);
        assertThat(merged.candidateGroups).isEqualTo("custom-group");
        assertThat(merged.candidateUsers).isEqualTo("custom-user");
        assertThat(merged.requiredCapabilities).isEqualTo("skill-x");
        assertThat(merged.createdBy).isEqualTo("custom-creator");
    }

    @Test
    void minimalRequestInheritsTemplateDefaults() {
        final WorkItemTemplate template = templateWithAllDefaults();
        // Minimal request — only required fields
        final WorkItemCreateRequest request = request("test-title");

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        // Request title wins
        assertThat(merged.title).isEqualTo("test-title");
        // Template defaults used for everything else
        assertThat(merged.description).isEqualTo("Template Desc");
        assertThat(merged.category).isEqualTo("template-cat");
        assertThat(merged.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(merged.candidateGroups).isEqualTo("grp-a,grp-b");
        assertThat(merged.candidateUsers).isEqualTo("user-a");
        assertThat(merged.requiredCapabilities).isEqualTo("cap-1");
    }

    @Test
    void businessHoursFallBackToTemplateDefaults() {
        final WorkItemTemplate template = templateWithAllDefaults();
        template.defaultClaimBusinessHours = 8;
        template.defaultExpiryBusinessHours = 24;

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request("test"), null);

        assertThat(merged.claimDeadlineBusinessHours).isEqualTo(8);
        assertThat(merged.expiresAtBusinessHours).isEqualTo(24);
    }

    @Test
    void requestBusinessHoursOverrideTemplate() {
        final WorkItemTemplate template = templateWithAllDefaults();
        template.defaultClaimBusinessHours = 8;
        template.defaultExpiryBusinessHours = 24;

        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test")
                .createdBy("test")
                .claimDeadlineBusinessHours(4)
                .expiresAtBusinessHours(12)
                .build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        assertThat(merged.claimDeadlineBusinessHours).isEqualTo(4);
        assertThat(merged.expiresAtBusinessHours).isEqualTo(12);
    }

    @Test
    void scopeFallsBackToTemplateDefault() {
        final WorkItemTemplate template = templateWithAllDefaults();
        template.scope = "casehubio/devtown";

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request("test"), null);

        assertThat(merged.scope).isEqualTo("casehubio/devtown");
    }

    @Test
    void requestScopeOverridesTemplate() {
        final WorkItemTemplate template = templateWithAllDefaults();
        template.scope = "casehubio/devtown";

        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test")
                .createdBy("test")
                .scope("custom/scope")
                .build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        assertThat(merged.scope).isEqualTo("custom/scope");
    }

    @Test
    void expandedExcludedUsersOverridesRequestExcludedUsers() {
        final WorkItemTemplate template = templateWith("T");
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test").createdBy("test").excludedUsers("user-a").build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, "user-a,user-b");

        assertThat(merged.excludedUsers).isEqualTo("user-a,user-b");
    }

    @Test
    void nullExpandedExcludedUsersFallsBackToRequestExcludedUsers() {
        final WorkItemTemplate template = templateWith("T");
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test").createdBy("test").excludedUsers("user-a").build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        assertThat(merged.excludedUsers).isEqualTo("user-a");
    }

    @Test
    void tenancyIdPassedThroughFromRequest() {
        final WorkItemTemplate template = templateWith("T");
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test").createdBy("test").tenancyId("tenant-42").build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        assertThat(merged.tenancyId).isEqualTo("tenant-42");
    }

    @Test
    void templateIdPreservedInMergedRequest() {
        final UUID templateId = UUID.randomUUID();
        final WorkItemTemplate template = templateWith("T");
        template.id = templateId;
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test").createdBy("test").templateId(templateId).build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        assertThat(merged.templateId).isEqualTo(templateId);
    }

    @Test
    void payloadMergedFromTemplateAndRequest() {
        final WorkItemTemplate template = templateWith("T");
        template.defaultPayload = "{\"base\":\"value\"}";

        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test").createdBy("test").payload("{\"extra\":\"data\"}").build();

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request, null);

        // mergePayload deep-merges when both are JSON objects
        assertThat(merged.payload).contains("\"base\"");
        assertThat(merged.payload).contains("\"extra\"");
    }

    @Test
    void inputDataSchemaFallsBackToTemplate() {
        final WorkItemTemplate template = templateWith("T");
        template.inputDataSchema = "{\"type\":\"object\"}";

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request("test"), null);

        assertThat(merged.inputDataSchema).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void outputDataSchemaFallsBackToTemplate() {
        final WorkItemTemplate template = templateWith("T");
        template.outputDataSchema = "{\"type\":\"string\"}";

        final WorkItemCreateRequest merged = WorkItemTemplateService.mergeRequestWithTemplate(
                template, request("test"), null);

        assertThat(merged.outputDataSchema).isEqualTo("{\"type\":\"string\"}");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static WorkItemCreateRequest request(final String title) {
        return WorkItemCreateRequest.builder().title(title).createdBy("test").build();
    }

    private static WorkItemTemplate templateWith(final String name) {
        final WorkItemTemplate t = new WorkItemTemplate();
        t.id = UUID.randomUUID();
        t.name = name;
        t.createdBy = "test";
        t.tenancyId = "default";
        return t;
    }

    private static WorkItemTemplate templateWithAllDefaults() {
        final WorkItemTemplate t = templateWith("Template Name");
        t.description = "Template Desc";
        t.category = "template-cat";
        t.priority = WorkItemPriority.HIGH;
        t.candidateGroups = "grp-a,grp-b";
        t.candidateUsers = "user-a";
        t.requiredCapabilities = "cap-1";
        t.scope = "default-scope";
        return t;
    }
}
