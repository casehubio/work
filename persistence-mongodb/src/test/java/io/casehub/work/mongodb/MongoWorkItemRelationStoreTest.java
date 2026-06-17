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

import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemRelationStoreTest {

    @Inject
    WorkItemRelationStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoWorkItemRelationDocument.deleteAll();
    }

    // ── Put and Get ───────────────────────────────────────────────────────────

    @Test
    void put_assignsIdAndTimestamp() {
        final WorkItemRelation relation = relation(UUID.randomUUID(), UUID.randomUUID(), "BLOCKS");
        assertThat(relation.id).isNull();
        assertThat(relation.createdAt).isNull();

        store.put(relation);

        assertThat(relation.id).isNotNull();
        assertThat(relation.createdAt).isNotNull();
    }

    @Test
    void put_and_get_roundtrip() {
        final UUID sourceId = UUID.randomUUID();
        final UUID targetId = UUID.randomUUID();
        final WorkItemRelation relation = relation(sourceId, targetId, "PART_OF");
        relation.createdBy = "alice";

        store.put(relation);
        final Optional<WorkItemRelation> found = store.get(relation.id);

        assertThat(found).isPresent();
        final WorkItemRelation loaded = found.get();
        assertThat(loaded.id).isEqualTo(relation.id);
        assertThat(loaded.sourceId).isEqualTo(sourceId);
        assertThat(loaded.targetId).isEqualTo(targetId);
        assertThat(loaded.relationType).isEqualTo("PART_OF");
        assertThat(loaded.createdBy).isEqualTo("alice");
        assertThat(loaded.createdAt).isNotNull();
    }

    @Test
    void get_returnsEmpty_whenNotFound() {
        assertThat(store.get(UUID.randomUUID())).isEmpty();
    }

    // ── FindBySourceId ────────────────────────────────────────────────────────

    @Test
    void findBySourceId_returnsOutgoingRelations() {
        final UUID sourceId = UUID.randomUUID();
        final UUID target1 = UUID.randomUUID();
        final UUID target2 = UUID.randomUUID();

        final WorkItemRelation rel1 = relation(sourceId, target1, "BLOCKS");
        rel1.createdAt = Instant.now().minus(1, ChronoUnit.HOURS);
        store.put(rel1);

        final WorkItemRelation rel2 = relation(sourceId, target2, "PART_OF");
        rel2.createdAt = Instant.now();
        store.put(rel2);

        // Different source should not appear
        store.put(relation(UUID.randomUUID(), target1, "BLOCKS"));

        final List<WorkItemRelation> results = store.findBySourceId(sourceId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).targetId).isEqualTo(target1);
        assertThat(results.get(1).targetId).isEqualTo(target2);
    }

    @Test
    void findBySourceId_orderedByCreatedAt() {
        final UUID sourceId = UUID.randomUUID();
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        final Instant t3 = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        final WorkItemRelation rel1 = relation(sourceId, UUID.randomUUID(), "BLOCKS");
        rel1.createdAt = t3;
        store.put(rel1);

        final WorkItemRelation rel2 = relation(sourceId, UUID.randomUUID(), "PART_OF");
        rel2.createdAt = t1;
        store.put(rel2);

        final WorkItemRelation rel3 = relation(sourceId, UUID.randomUUID(), "DUPLICATES");
        rel3.createdAt = t2;
        store.put(rel3);

        final List<WorkItemRelation> results = store.findBySourceId(sourceId);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).createdAt).isEqualTo(t1);
        assertThat(results.get(1).createdAt).isEqualTo(t2);
        assertThat(results.get(2).createdAt).isEqualTo(t3);
    }

    // ── FindByTargetId ────────────────────────────────────────────────────────

    @Test
    void findByTargetId_returnsIncomingRelations() {
        final UUID targetId = UUID.randomUUID();
        final UUID source1 = UUID.randomUUID();
        final UUID source2 = UUID.randomUUID();

        final WorkItemRelation rel1 = relation(source1, targetId, "BLOCKS");
        rel1.createdAt = Instant.now().minus(1, ChronoUnit.HOURS);
        store.put(rel1);

        final WorkItemRelation rel2 = relation(source2, targetId, "PART_OF");
        rel2.createdAt = Instant.now();
        store.put(rel2);

        // Different target should not appear
        store.put(relation(source1, UUID.randomUUID(), "BLOCKS"));

        final List<WorkItemRelation> results = store.findByTargetId(targetId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).sourceId).isEqualTo(source1);
        assertThat(results.get(1).sourceId).isEqualTo(source2);
    }

    @Test
    void findByTargetId_orderedByCreatedAt() {
        final UUID targetId = UUID.randomUUID();
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        final Instant t3 = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        final WorkItemRelation rel1 = relation(UUID.randomUUID(), targetId, "BLOCKS");
        rel1.createdAt = t2;
        store.put(rel1);

        final WorkItemRelation rel2 = relation(UUID.randomUUID(), targetId, "PART_OF");
        rel2.createdAt = t3;
        store.put(rel2);

        final WorkItemRelation rel3 = relation(UUID.randomUUID(), targetId, "DUPLICATES");
        rel3.createdAt = t1;
        store.put(rel3);

        final List<WorkItemRelation> results = store.findByTargetId(targetId);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).createdAt).isEqualTo(t1);
        assertThat(results.get(1).createdAt).isEqualTo(t2);
        assertThat(results.get(2).createdAt).isEqualTo(t3);
    }

    // ── FindBySourceAndType ───────────────────────────────────────────────────

    @Test
    void findBySourceAndType_filtersCorrectly() {
        final UUID sourceId = UUID.randomUUID();
        final UUID target1 = UUID.randomUUID();
        final UUID target2 = UUID.randomUUID();
        final UUID target3 = UUID.randomUUID();

        final WorkItemRelation rel1 = relation(sourceId, target1, "BLOCKS");
        rel1.createdAt = Instant.now().minus(1, ChronoUnit.HOURS);
        store.put(rel1);

        final WorkItemRelation rel2 = relation(sourceId, target2, "BLOCKS");
        rel2.createdAt = Instant.now();
        store.put(rel2);

        // Different type
        store.put(relation(sourceId, target3, "PART_OF"));

        final List<WorkItemRelation> results = store.findBySourceAndType(sourceId, "BLOCKS");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).targetId).isEqualTo(target1);
        assertThat(results.get(1).targetId).isEqualTo(target2);
        assertThat(results).allMatch(r -> r.relationType.equals("BLOCKS"));
    }

    // ── FindByTargetAndType ───────────────────────────────────────────────────

    @Test
    void findByTargetAndType_filtersCorrectly() {
        final UUID targetId = UUID.randomUUID();
        final UUID source1 = UUID.randomUUID();
        final UUID source2 = UUID.randomUUID();
        final UUID source3 = UUID.randomUUID();

        final WorkItemRelation rel1 = relation(source1, targetId, "PART_OF");
        rel1.createdAt = Instant.now().minus(1, ChronoUnit.HOURS);
        store.put(rel1);

        final WorkItemRelation rel2 = relation(source2, targetId, "PART_OF");
        rel2.createdAt = Instant.now();
        store.put(rel2);

        // Different type
        store.put(relation(source3, targetId, "BLOCKS"));

        final List<WorkItemRelation> results = store.findByTargetAndType(targetId, "PART_OF");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).sourceId).isEqualTo(source1);
        assertThat(results.get(1).sourceId).isEqualTo(source2);
        assertThat(results).allMatch(r -> r.relationType.equals("PART_OF"));
    }

    // ── FindExisting ──────────────────────────────────────────────────────────

    @Test
    void findExisting_returnsPresent_whenExactMatch() {
        final UUID sourceId = UUID.randomUUID();
        final UUID targetId = UUID.randomUUID();
        final WorkItemRelation relation = relation(sourceId, targetId, "BLOCKS");

        store.put(relation);

        final Optional<WorkItemRelation> found = store.findExisting(sourceId, targetId, "BLOCKS");

        assertThat(found).isPresent();
        assertThat(found.get().id).isEqualTo(relation.id);
    }

    @Test
    void findExisting_returnsEmpty_whenNoMatch() {
        final UUID sourceId = UUID.randomUUID();
        final UUID targetId = UUID.randomUUID();

        store.put(relation(sourceId, targetId, "BLOCKS"));

        // Different type
        assertThat(store.findExisting(sourceId, targetId, "PART_OF")).isEmpty();

        // Different target
        assertThat(store.findExisting(sourceId, UUID.randomUUID(), "BLOCKS")).isEmpty();

        // Different source
        assertThat(store.findExisting(UUID.randomUUID(), targetId, "BLOCKS")).isEmpty();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returnsTrue_whenExists() {
        final WorkItemRelation relation = relation(UUID.randomUUID(), UUID.randomUUID(), "BLOCKS");
        store.put(relation);

        final boolean deleted = store.delete(relation.id);

        assertThat(deleted).isTrue();
        assertThat(store.get(relation.id)).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenNotFound() {
        final boolean deleted = store.delete(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_relationInvisibleToOtherTenant() {
        final UUID sourceId = UUID.randomUUID();
        final UUID targetId = UUID.randomUUID();
        final WorkItemRelation relation = relation(sourceId, targetId, "BLOCKS");
        store.put(relation);

        principal.setTenancyId("tenant-2");

        assertThat(store.get(relation.id)).isEmpty();
        assertThat(store.findBySourceId(sourceId)).isEmpty();
        assertThat(store.findByTargetId(targetId)).isEmpty();
        assertThat(store.findBySourceAndType(sourceId, "BLOCKS")).isEmpty();
        assertThat(store.findByTargetAndType(targetId, "BLOCKS")).isEmpty();
        assertThat(store.findExisting(sourceId, targetId, "BLOCKS")).isEmpty();
        assertThat(store.delete(relation.id)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItemRelation relation(final UUID sourceId, final UUID targetId, final String type) {
        final WorkItemRelation relation = new WorkItemRelation();
        relation.sourceId = sourceId;
        relation.targetId = targetId;
        relation.relationType = type;
        relation.createdBy = "test-user";
        return relation;
    }
}
