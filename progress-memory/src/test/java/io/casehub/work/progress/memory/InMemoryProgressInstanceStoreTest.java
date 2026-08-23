package io.casehub.work.progress.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryProgressInstanceStoreTest {

    private final InMemoryProgressInstanceStore store = new InMemoryProgressInstanceStore();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store.clear();
    }

    private ProgressInstance instance(UUID id, String scopeType, String scopeId, UUID parentId) {
        UUID rootId = parentId == null ? id : UUID.randomUUID();
        return new ProgressInstance(id, "tenant1", scopeType, scopeId,
                                    parentId, rootId, "percentage", null,
                                    mapper.createObjectNode().put("value", 50),
                                    ProgressStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());}

    @Test
    void putAndGetRoundTrip() {
        UUID id = UUID.randomUUID();
        ProgressInstance inst = instance(id, "workitem", "wi-1", null);
        store.put(inst);
        assertThat(store.get(id)).isPresent().contains(inst);
    }

    @Test
    void getMissingReturnsEmpty() {
        assertThat(store.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByScopeTypeAndScopeId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        store.put(instance(id1, "workitem", "wi-1", null));
        store.put(instance(id2, "workitem", "wi-2", null));

        var results = store.findByScopeTypeAndScopeId("workitem", "wi-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(id1);
    }

    @Test
    void findByScopeReturnsNewestFirst() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProgressInstance older = new ProgressInstance(id1, "t1", "workitem", "wi-1",
                                                      null, id1, "percentage", null,
                                                      mapper.createObjectNode().put("value", 50),
                                                      ProgressStatus.ACTIVE, null, null, null, Instant.now().minusSeconds(10), Instant.now());
        ProgressInstance newer = new ProgressInstance(id2, "t1", "workitem", "wi-1",
                                                      null, id2, "percentage", null,
                                                      mapper.createObjectNode().put("value", 80),
                                                      ProgressStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
        store.put(older);
        store.put(newer);

        var results = store.findByScopeTypeAndScopeId("workitem", "wi-1");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(id2);}

    @Test
    void findByParentProgressId() {
        UUID parentId = UUID.randomUUID();
        UUID child1 = UUID.randomUUID();
        UUID child2 = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        store.put(instance(parentId, "workitem", "wi-parent", null));
        store.put(instance(child1, "workitem", "wi-c1", parentId));
        store.put(instance(child2, "workitem", "wi-c2", parentId));
        store.put(instance(unrelated, "workitem", "wi-other", null));

        var results = store.findByParentProgressId(parentId);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProgressInstance::id).containsExactlyInAnyOrder(child1, child2);
    }


    @Test
    void findDescendantsOf_threeLevelTree_returnsAllDescendants() {
        UUID rootId       = UUID.randomUUID();
        UUID child1Id     = UUID.randomUUID();
        UUID child2Id     = UUID.randomUUID();
        UUID grandchildId = UUID.randomUUID();
        store.put(instance(rootId, "workitem", "root", null));
        store.put(instance(child1Id, "workitem", "c1", rootId));
        store.put(instance(child2Id, "workitem", "c2", rootId));
        store.put(instance(grandchildId, "workitem", "gc1", child1Id));

        var descendants = store.findDescendantsOf(rootId);
        assertThat(descendants).hasSize(3);
        assertThat(descendants).extracting(ProgressInstance::id)
                               .containsExactlyInAnyOrder(child1Id, child2Id, grandchildId);
    }

    @Test
    void findDescendantsOf_doesNotIncludeRoot() {
        UUID rootId  = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        store.put(instance(rootId, "workitem", "root", null));
        store.put(instance(childId, "workitem", "c1", rootId));

        var descendants = store.findDescendantsOf(rootId);
        assertThat(descendants).extracting(ProgressInstance::id).doesNotContain(rootId);
    }

    @Test
    void findDescendantsOf_midTreeNode_onlyReturnsTargetSubtree() {
        UUID rootId    = UUID.randomUUID();
        UUID branch1Id = UUID.randomUUID();
        UUID branch2Id = UUID.randomUUID();
        UUID leaf1Id   = UUID.randomUUID();
        store.put(instance(rootId, "workitem", "root", null));
        store.put(instance(branch1Id, "workitem", "b1", rootId));
        store.put(instance(branch2Id, "workitem", "b2", rootId));
        store.put(instance(leaf1Id, "workitem", "l1", branch1Id));

        var descendants = store.findDescendantsOf(branch1Id);
        assertThat(descendants).hasSize(1);
        assertThat(descendants.get(0).id()).isEqualTo(leaf1Id);
    }

    @Test
    void findDescendantsOf_leafNode_returnsEmpty() {
        UUID rootId = UUID.randomUUID();
        store.put(instance(rootId, "workitem", "root", null));

        assertThat(store.findDescendantsOf(rootId)).isEmpty();
    }

    @Test
    void putUpdatesExisting() {
        UUID             id       = UUID.randomUUID();
        ProgressInstance original = instance(id, "workitem", "wi-1", null);
        store.put(original);

        ProgressInstance updated = new ProgressInstance(id, "tenant1", "workitem", "wi-1",
                                                        null, id, "percentage", null,
                                                        mapper.createObjectNode().put("value", 80),
                                                        ProgressStatus.COMPLETED, null, null, null, original.createdAt(), Instant.now());
        store.put(updated);

        assertThat(store.get(id)).isPresent();
        assertThat(store.get(id).get().status()).isEqualTo(ProgressStatus.COMPLETED);}
}
