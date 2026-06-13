package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelVocabularyStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaLabelVocabularyStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaLabelVocabularyStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    LabelVocabularyStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private LabelVocabulary newVocabulary(String name) {
        LabelVocabulary vocab = new LabelVocabulary();
        vocab.scope = Path.root();
        vocab.name = name;
        return vocab;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        LabelVocabulary vocab = newVocabulary("vocab-a");
        assertThat(vocab.tenancyId).isNull();

        store.put(vocab);

        assertThat(vocab.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantVocabulary() {
        principal.setTenancyId(TENANT_A);
        LabelVocabulary vocab = newVocabulary("vocab-a");
        store.put(vocab);
        UUID id = vocab.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void scanAll_returnOnlyCurrentTenantVocabularies() {
        // Tenant A: create vocabulary
        principal.setTenancyId(TENANT_A);
        store.put(newVocabulary("vocab-a"));

        // Tenant B: create separate vocabulary
        principal.setTenancyId(TENANT_B);
        store.put(newVocabulary("vocab-b"));

        // As tenant B, only see B's vocabulary
        List<LabelVocabulary> resultB = store.scanAll();
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).tenancyId).isEqualTo(TENANT_B);
        assertThat(resultB.get(0).name).isEqualTo("vocab-b");

        // As tenant A, only see A's vocabulary
        principal.setTenancyId(TENANT_A);
        List<LabelVocabulary> resultA = store.scanAll();
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).tenancyId).isEqualTo(TENANT_A);
        assertThat(resultA.get(0).name).isEqualTo("vocab-a");
    }

    @Test
    void delete_cannotDeleteAnotherTenantVocabulary() {
        principal.setTenancyId(TENANT_A);
        LabelVocabulary vocab = newVocabulary("vocab-a");
        store.put(vocab);
        UUID id = vocab.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}
