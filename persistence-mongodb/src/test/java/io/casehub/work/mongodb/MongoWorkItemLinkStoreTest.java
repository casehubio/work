package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.repository.WorkItemLinkStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemLinkStoreTest {

    @Inject
    WorkItemLinkStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoWorkItemLinkDocument.deleteAll();
    }

    // ── Put ───────────────────────────────────────────────────────────────────

    @Test
    void put_assignsIdAndTimestamps() {
        final WorkItemLink link = link(UUID.randomUUID(), "https://example.com");
        assertThat(link.id).isNull();
        assertThat(link.createdAt).isNull();

        store.put(link);

        assertThat(link.id).isNotNull();
        assertThat(link.createdAt).isNotNull();
    }

    @Test
    void put_and_get_roundtrip() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemLink link = link(workItemId, "https://example.com/doc");
        link.title = "Example Document";
        link.relationType = "documentation";
        link.linkedBy = "alice";

        store.put(link);
        final Optional<WorkItemLink> found = store.get(link.id);

        assertThat(found).isPresent();
        final WorkItemLink loaded = found.get();
        assertThat(loaded.id).isEqualTo(link.id);
        assertThat(loaded.workItemId).isEqualTo(workItemId);
        assertThat(loaded.url).isEqualTo("https://example.com/doc");
        assertThat(loaded.title).isEqualTo("Example Document");
        assertThat(loaded.relationType).isEqualTo("documentation");
        assertThat(loaded.linkedBy).isEqualTo("alice");
        assertThat(loaded.createdAt).isNotNull();
    }

    // ── FindByWorkItemId ──────────────────────────────────────────────────────

    @Test
    void findByWorkItemId_orderedByCreatedAt() {
        final UUID workItemId = UUID.randomUUID();
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant t3 = Instant.now();

        final WorkItemLink link1 = link(workItemId, "https://first.com");
        link1.createdAt = t1;
        store.put(link1);

        final WorkItemLink link3 = link(workItemId, "https://third.com");
        link3.createdAt = t3;
        store.put(link3);

        final WorkItemLink link2 = link(workItemId, "https://second.com");
        link2.createdAt = t2;
        store.put(link2);

        final List<WorkItemLink> results = store.findByWorkItemId(workItemId);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).url).isEqualTo("https://first.com");
        assertThat(results.get(1).url).isEqualTo("https://second.com");
        assertThat(results.get(2).url).isEqualTo("https://third.com");
    }

    // ── FindByWorkItemIdAndType ───────────────────────────────────────────────

    @Test
    void findByWorkItemIdAndType_filtersCorrectly() {
        final UUID workItemId = UUID.randomUUID();

        final WorkItemLink link1 = link(workItemId, "https://doc1.com");
        link1.relationType = "documentation";
        store.put(link1);

        final WorkItemLink link2 = link(workItemId, "https://attachment1.com");
        link2.relationType = "attachment";
        store.put(link2);

        final WorkItemLink link3 = link(workItemId, "https://doc2.com");
        link3.relationType = "documentation";
        store.put(link3);

        final List<WorkItemLink> docs = store.findByWorkItemIdAndType(workItemId, "documentation");
        final List<WorkItemLink> attachments = store.findByWorkItemIdAndType(workItemId, "attachment");

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).url).isEqualTo("https://doc1.com");
        assertThat(docs.get(1).url).isEqualTo("https://doc2.com");

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).url).isEqualTo("https://attachment1.com");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returnsTrue_whenExists() {
        final WorkItemLink link = link(UUID.randomUUID(), "https://to-delete.com");
        store.put(link);

        final boolean deleted = store.delete(link.id);

        assertThat(deleted).isTrue();
        assertThat(store.get(link.id)).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenNotFound() {
        final boolean deleted = store.delete(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_linkInvisibleToOtherTenant() {
        final WorkItemLink link = link(UUID.randomUUID(), "https://tenant1.com");
        store.put(link);

        principal.setTenancyId("tenant-2");

        assertThat(store.get(link.id)).isEmpty();
        assertThat(store.findByWorkItemId(link.workItemId)).isEmpty();
        assertThat(store.delete(link.id)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItemLink link(final UUID workItemId, final String url) {
        final WorkItemLink link = new WorkItemLink();
        link.workItemId = workItemId;
        link.url = url;
        link.relationType = "related";
        link.linkedBy = "test-user";
        return link;
    }
}
