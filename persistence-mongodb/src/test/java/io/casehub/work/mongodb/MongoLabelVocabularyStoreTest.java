package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelVocabularyStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoLabelVocabularyStoreTest {

    @Inject
    LabelVocabularyStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoLabelVocabularyDocument.deleteAll();
    }

    // ── Put and Get ───────────────────────────────────────────────────────────

    @Test
    void put_and_get_roundtrip() {
        final LabelVocabulary vocab = vocabulary(Path.of("org", "team"), "Team Vocab");

        store.put(vocab);

        final Optional<LabelVocabulary> found = store.get(vocab.id);
        assertThat(found).isPresent();
        final LabelVocabulary loaded = found.get();
        assertThat(loaded.id).isEqualTo(vocab.id);
        assertThat(loaded.scope).isEqualTo(Path.of("org", "team"));
        assertThat(loaded.name).isEqualTo("Team Vocab");
        assertThat(loaded.tenancyId).isEqualTo(principal.tenancyId());
    }

    @Test
    void put_assignsId_whenNull() {
        final LabelVocabulary vocab = vocabulary(Path.of("org"), "Org Vocab");
        assertThat(vocab.id).isNull();

        store.put(vocab);

        assertThat(vocab.id).isNotNull();
    }

    @Test
    void get_returnsEmpty_whenNotFound() {
        assertThat(store.get(UUID.randomUUID())).isEmpty();
    }

    // ── ScanAll ───────────────────────────────────────────────────────────────

    @Test
    void scanAll_returnsAllVocabularies() {
        store.put(vocabulary(Path.of("org"), "Org Vocab"));
        store.put(vocabulary(Path.of("org", "team"), "Team Vocab"));
        store.put(vocabulary(Path.root(), "Root Vocab"));

        final List<LabelVocabulary> all = store.scanAll();

        assertThat(all).hasSize(3);
        assertThat(all).extracting(v -> v.name)
                .containsExactlyInAnyOrder("Org Vocab", "Team Vocab", "Root Vocab");
    }

    // ── FindByScope ───────────────────────────────────────────────────────────

    @Test
    void findByScope_exactMatch() {
        final LabelVocabulary vocab = vocabulary(Path.of("org", "team"), "Team Vocab");
        store.put(vocab);

        final Optional<LabelVocabulary> found = store.findByScope(Path.of("org", "team"));

        assertThat(found).isPresent();
        assertThat(found.get().id).isEqualTo(vocab.id);
        assertThat(found.get().name).isEqualTo("Team Vocab");
    }

    @Test
    void findByScope_returnsEmpty_whenNotFound() {
        final Optional<LabelVocabulary> found = store.findByScope(Path.of("missing"));

        assertThat(found).isEmpty();
    }

    // ── FindOrCreate ──────────────────────────────────────────────────────────

    @Test
    void findOrCreate_createsWhenAbsent() {
        final LabelVocabulary vocab = store.findOrCreate(Path.of("org", "team"), "Team Vocab");

        assertThat(vocab).isNotNull();
        assertThat(vocab.id).isNotNull();
        assertThat(vocab.scope).isEqualTo(Path.of("org", "team"));
        assertThat(vocab.name).isEqualTo("Team Vocab");
        assertThat(vocab.tenancyId).isEqualTo(principal.tenancyId());

        // Verify persisted
        final Optional<LabelVocabulary> found = store.findByScope(Path.of("org", "team"));
        assertThat(found).isPresent();
        assertThat(found.get().id).isEqualTo(vocab.id);
    }

    @Test
    void findOrCreate_returnsExistingWhenPresent() {
        final LabelVocabulary existing = vocabulary(Path.of("org", "team"), "Original Name");
        store.put(existing);

        final LabelVocabulary found = store.findOrCreate(Path.of("org", "team"), "Different Name");

        assertThat(found.id).isEqualTo(existing.id);
        assertThat(found.name).isEqualTo("Original Name"); // Should NOT update to "Different Name"
    }

    @Test
    void findOrCreate_isAtomic_concurrentCallsReturnSameId() throws InterruptedException {
        final Path scope = Path.of("org", "concurrent");
        final UUID[] ids = new UUID[2];

        // Simulate concurrent calls
        final Thread t1 = new Thread(() -> {
            ids[0] = store.findOrCreate(scope, "Concurrent 1").id;
        });
        final Thread t2 = new Thread(() -> {
            ids[1] = store.findOrCreate(scope, "Concurrent 2").id;
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Both threads should get the same vocabulary ID
        assertThat(ids[0]).isNotNull();
        assertThat(ids[1]).isNotNull();
        assertThat(ids[0]).isEqualTo(ids[1]);

        // Verify only one vocabulary exists
        final List<LabelVocabulary> all = store.scanAll();
        final long count = all.stream()
                .filter(v -> v.scope.equals(scope))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returnsTrue_whenExists() {
        final LabelVocabulary vocab = vocabulary(Path.of("org", "team"), "Team Vocab");
        store.put(vocab);

        final boolean deleted = store.delete(vocab.id);

        assertThat(deleted).isTrue();
        assertThat(store.get(vocab.id)).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenNotFound() {
        final boolean deleted = store.delete(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_vocabularyInvisibleToOtherTenant() {
        final LabelVocabulary vocab = vocabulary(Path.of("org", "team"), "Tenant 1 Vocab");
        store.put(vocab);

        principal.setTenancyId("tenant-2");

        assertThat(store.get(vocab.id)).isEmpty();
        assertThat(store.findByScope(Path.of("org", "team"))).isEmpty();
        assertThat(store.scanAll()).isEmpty();
        assertThat(store.delete(vocab.id)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LabelVocabulary vocabulary(final Path scope, final String name) {
        final LabelVocabulary vocab = new LabelVocabulary();
        vocab.scope = scope;
        vocab.name = name;
        return vocab;
    }
}
