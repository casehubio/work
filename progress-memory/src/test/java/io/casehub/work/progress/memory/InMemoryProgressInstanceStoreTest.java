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
