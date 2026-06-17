package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemTemplateStoreTest {

    @Inject
    WorkItemTemplateStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoWorkItemTemplateDocument.deleteAll();
    }

    // ── Put ───────────────────────────────────────────────────────────────────

    @Test
    void put_assignsIdAndTimestamps() {
        final WorkItemTemplate template = minimalTemplate("Test Template");
        assertThat(template.id).isNull();
        assertThat(template.createdAt).isNull();

        store.put(template);

        assertThat(template.id).isNotNull();
        assertThat(template.createdAt).isNotNull();
    }

    @Test
    void put_and_get_roundtrip() {
        final WorkItemTemplate template = richTemplate();

        store.put(template);
        final Optional<WorkItemTemplate> found = store.get(template.id);

        assertThat(found).isPresent();
        final WorkItemTemplate loaded = found.get();
        assertThat(loaded.id).isEqualTo(template.id);
        assertThat(loaded.name).isEqualTo("Loan Approval");
        assertThat(loaded.description).isEqualTo("Multi-stage loan approval workflow");
        assertThat(loaded.category).isEqualTo("finance");
        assertThat(loaded.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(loaded.candidateGroups).isEqualTo("underwriters,approvers");
        assertThat(loaded.candidateUsers).isEqualTo("alice,bob");
        assertThat(loaded.requiredCapabilities).isEqualTo("finance,audit");
        assertThat(loaded.defaultExpiryHours).isEqualTo(48);
        assertThat(loaded.defaultClaimHours).isEqualTo(8);
        assertThat(loaded.defaultExpiryBusinessHours).isEqualTo(24);
        assertThat(loaded.defaultClaimBusinessHours).isEqualTo(4);
        assertThat(loaded.defaultPayload).isEqualTo("{\"loanAmount\":50000}");
        assertThat(loaded.labelPaths).isEqualTo("[\"finance/loans\",\"priority/high\"]");
        assertThat(loaded.outcomes).isEqualTo("[{\"name\":\"approved\"},{\"name\":\"rejected\"}]");
        assertThat(loaded.excludedUsers).isEqualTo("charlie");
        assertThat(loaded.excludedGroups).isEqualTo("contractors");
        assertThat(loaded.scope).isEqualTo("casehubio/finance/loans");
        assertThat(loaded.inputDataSchema).isEqualTo("{\"type\":\"object\"}");
        assertThat(loaded.outputDataSchema).isEqualTo("{\"type\":\"object\"}");
        assertThat(loaded.instanceCount).isEqualTo(3);
        assertThat(loaded.requiredCount).isEqualTo(2);
        assertThat(loaded.parentRole).isEqualTo("COORDINATOR");
        assertThat(loaded.assignmentStrategy).isEqualTo("round-robin");
        assertThat(loaded.onThresholdReached).isEqualTo("SUSPEND");
        assertThat(loaded.allowSameAssignee).isTrue();
        assertThat(loaded.createdBy).isEqualTo("admin");
        assertThat(loaded.createdAt).isNotNull();
    }

    // ── GetByName ─────────────────────────────────────────────────────────────

    @Test
    void getByName_findsExact() {
        final WorkItemTemplate template = minimalTemplate("Unique Template");
        store.put(template);

        final Optional<WorkItemTemplate> found = store.getByName("Unique Template");

        assertThat(found).isPresent();
        assertThat(found.get().id).isEqualTo(template.id);
    }

    @Test
    void getByName_returnsEmpty_whenNotFound() {
        assertThat(store.getByName("Nonexistent")).isEmpty();
    }

    // ── ScanAll ───────────────────────────────────────────────────────────────

    @Test
    void scanAll_orderedByName() {
        final WorkItemTemplate t1 = minimalTemplate("Charlie");
        final WorkItemTemplate t2 = minimalTemplate("Alice");
        final WorkItemTemplate t3 = minimalTemplate("Bob");

        store.put(t1);
        store.put(t2);
        store.put(t3);

        final List<WorkItemTemplate> results = store.scanAll();

        assertThat(results).hasSize(3);
        assertThat(results.get(0).name).isEqualTo("Alice");
        assertThat(results.get(1).name).isEqualTo("Bob");
        assertThat(results.get(2).name).isEqualTo("Charlie");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesAndReturnsBoolean() {
        final WorkItemTemplate template = minimalTemplate("To Delete");
        store.put(template);

        final boolean deleted = store.delete(template.id);

        assertThat(deleted).isTrue();
        assertThat(store.get(template.id)).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenNotFound() {
        final boolean deleted = store.delete(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_templateInvisibleToOtherTenant() {
        final WorkItemTemplate template = minimalTemplate("Tenant 1 Template");
        store.put(template);

        principal.setTenancyId("tenant-2");

        assertThat(store.get(template.id)).isEmpty();
        assertThat(store.getByName("Tenant 1 Template")).isEmpty();
        assertThat(store.scanAll()).isEmpty();
        assertThat(store.delete(template.id)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItemTemplate minimalTemplate(final String name) {
        final WorkItemTemplate template = new WorkItemTemplate();
        template.name = name;
        template.createdBy = "test-user";
        return template;
    }

    private WorkItemTemplate richTemplate() {
        final WorkItemTemplate template = new WorkItemTemplate();
        template.name = "Loan Approval";
        template.description = "Multi-stage loan approval workflow";
        template.category = "finance";
        template.priority = WorkItemPriority.HIGH;
        template.candidateGroups = "underwriters,approvers";
        template.candidateUsers = "alice,bob";
        template.requiredCapabilities = "finance,audit";
        template.defaultExpiryHours = 48;
        template.defaultClaimHours = 8;
        template.defaultExpiryBusinessHours = 24;
        template.defaultClaimBusinessHours = 4;
        template.defaultPayload = "{\"loanAmount\":50000}";
        template.labelPaths = "[\"finance/loans\",\"priority/high\"]";
        template.outcomes = "[{\"name\":\"approved\"},{\"name\":\"rejected\"}]";
        template.excludedUsers = "charlie";
        template.excludedGroups = "contractors";
        template.scope = "casehubio/finance/loans";
        template.inputDataSchema = "{\"type\":\"object\"}";
        template.outputDataSchema = "{\"type\":\"object\"}";
        template.instanceCount = 3;
        template.requiredCount = 2;
        template.parentRole = "COORDINATOR";
        template.assignmentStrategy = "round-robin";
        template.onThresholdReached = "SUSPEND";
        template.allowSameAssignee = true;
        template.createdBy = "admin";
        return template;
    }
}
