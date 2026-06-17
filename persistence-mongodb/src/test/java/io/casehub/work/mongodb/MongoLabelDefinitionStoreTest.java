package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.repository.LabelDefinitionStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoLabelDefinitionStoreTest {

    @Inject
    LabelDefinitionStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoLabelDefinitionDocument.deleteAll();
    }

    // ── Put and Get ───────────────────────────────────────────────────────────

    @Test
    void put_assignsIdAndTimestamps() {
        final LabelDefinition definition = definition(Path.of("legal", "contracts"), UUID.randomUUID());
        assertThat(definition.id).isNull();
        assertThat(definition.createdAt).isNull();

        store.put(definition);

        assertThat(definition.id).isNotNull();
        assertThat(definition.createdAt).isNotNull();
    }

    @Test
    void put_and_get_roundtrip() {
        final Path path = Path.of("legal", "contracts", "nda");
        final UUID vocabularyId = UUID.randomUUID();
        final LabelDefinition definition = definition(path, vocabularyId);
        definition.description = "Non-disclosure agreements";
        definition.createdBy = "alice";

        store.put(definition);
        final Optional<LabelDefinition> found = store.get(definition.id);

        assertThat(found).isPresent();
        final LabelDefinition loaded = found.get();
        assertThat(loaded.id).isEqualTo(definition.id);
        assertThat(loaded.path).isEqualTo(path);
        assertThat(loaded.path.value()).isEqualTo("legal/contracts/nda");
        assertThat(loaded.vocabularyId).isEqualTo(vocabularyId);
        assertThat(loaded.description).isEqualTo("Non-disclosure agreements");
        assertThat(loaded.createdBy).isEqualTo("alice");
        assertThat(loaded.createdAt).isNotNull();
    }

    @Test
    void get_returnsEmpty_whenNotFound() {
        assertThat(store.get(UUID.randomUUID())).isEmpty();
    }

    // ── FindByVocabularyId ────────────────────────────────────────────────────

    @Test
    void findByVocabularyId_returnsAllDefinitionsForVocabulary() {
        final UUID vocabId = UUID.randomUUID();
        final UUID otherVocabId = UUID.randomUUID();

        store.put(definition(Path.of("legal", "contracts"), vocabId));
        store.put(definition(Path.of("legal", "privacy"), vocabId));
        store.put(definition(Path.of("finance", "budget"), otherVocabId));

        final List<LabelDefinition> results = store.findByVocabularyId(vocabId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.vocabularyId.equals(vocabId));
    }

    // ── FindByPath ────────────────────────────────────────────────────────────

    @Test
    void findByPath_returnsDefinitionsMatchingExactPath() {
        final Path targetPath = Path.of("legal", "contracts");
        final UUID vocabId1 = UUID.randomUUID();
        final UUID vocabId2 = UUID.randomUUID();

        store.put(definition(targetPath, vocabId1));
        store.put(definition(targetPath, vocabId2));
        store.put(definition(Path.of("legal", "privacy"), vocabId1));

        final List<LabelDefinition> results = store.findByPath(targetPath);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.path.equals(targetPath));
    }

    @Test
    void findByPath_returnsEmpty_whenNoMatch() {
        final List<LabelDefinition> results = store.findByPath(Path.of("nonexistent"));
        assertThat(results).isEmpty();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returnsTrue_whenExists() {
        final LabelDefinition definition = definition(Path.of("legal"), UUID.randomUUID());
        store.put(definition);

        final boolean deleted = store.delete(definition.id);

        assertThat(deleted).isTrue();
        assertThat(store.get(definition.id)).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenNotFound() {
        final boolean deleted = store.delete(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_definitionInvisibleToOtherTenant() {
        final Path path = Path.of("legal", "contracts");
        final UUID vocabularyId = UUID.randomUUID();
        final LabelDefinition definition = definition(path, vocabularyId);
        store.put(definition);

        principal.setTenancyId("tenant-2");

        assertThat(store.get(definition.id)).isEmpty();
        assertThat(store.findByVocabularyId(vocabularyId)).isEmpty();
        assertThat(store.findByPath(path)).isEmpty();
        assertThat(store.delete(definition.id)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LabelDefinition definition(final Path path, final UUID vocabularyId) {
        final LabelDefinition definition = new LabelDefinition();
        definition.path = path;
        definition.vocabularyId = vocabularyId;
        definition.createdBy = "test-user";
        return definition;
    }
}
