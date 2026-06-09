package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.model.VocabularyScope;
import io.casehub.work.runtime.repository.LabelDefinitionStore;
import io.casehub.work.runtime.repository.LabelVocabularyStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaLabelDefinitionStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaLabelDefinitionStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    LabelDefinitionStore store;

    @Inject
    LabelVocabularyStore vocabularyStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    /** Create a minimal vocabulary in the current tenant. */
    private LabelVocabulary createVocabulary(String name) {
        LabelVocabulary vocab = new LabelVocabulary();
        vocab.scope = VocabularyScope.GLOBAL;
        vocab.name = name;
        return vocabularyStore.put(vocab);
    }

    private LabelDefinition newDefinition(UUID vocabularyId, String pathStr) {
        LabelDefinition def = new LabelDefinition();
        def.vocabularyId = vocabularyId;
        def.path = Path.of(pathStr);
        def.createdBy = "test";
        def.createdAt = Instant.now();
        return def;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        LabelVocabulary vocab = createVocabulary("vocab-a");

        LabelDefinition def = newDefinition(vocab.id, "legal/contracts");
        assertThat(def.tenancyId).isNull();

        store.put(def);

        assertThat(def.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantDefinition() {
        principal.setTenancyId(TENANT_A);
        LabelVocabulary vocab = createVocabulary("vocab-a");
        LabelDefinition def = newDefinition(vocab.id, "legal/nda");
        store.put(def);
        UUID id = def.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void findByVocabularyId_returnOnlyCurrentTenantDefinitions() {
        // Tenant A: create vocabulary and definition
        principal.setTenancyId(TENANT_A);
        LabelVocabulary vocabA = createVocabulary("vocab-a");
        store.put(newDefinition(vocabA.id, "legal/nda"));

        // Tenant B: create separate vocabulary and definition
        principal.setTenancyId(TENANT_B);
        LabelVocabulary vocabB = createVocabulary("vocab-b");
        store.put(newDefinition(vocabB.id, "legal/gdpr"));

        // As tenant B, only see B's definition
        List<LabelDefinition> resultB = store.findByVocabularyId(vocabB.id);
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).tenancyId).isEqualTo(TENANT_B);

        // As tenant A, should not see B's definition for A's vocabularyId
        principal.setTenancyId(TENANT_A);
        List<LabelDefinition> resultA = store.findByVocabularyId(vocabA.id);
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void findByPath_returnOnlyCurrentTenantDefinitions() {
        principal.setTenancyId(TENANT_A);
        LabelVocabulary vocab = createVocabulary("vocab-a");
        store.put(newDefinition(vocab.id, "legal/contracts"));

        Path path = Path.of("legal/contracts");

        principal.setTenancyId(TENANT_B);
        assertThat(store.findByPath(path)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findByPath(path)).hasSize(1);
    }

    @Test
    void delete_cannotDeleteAnotherTenantDefinition() {
        principal.setTenancyId(TENANT_A);
        LabelVocabulary vocab = createVocabulary("vocab-a");
        LabelDefinition def = newDefinition(vocab.id, "legal/nda");
        store.put(def);
        UUID id = def.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}
