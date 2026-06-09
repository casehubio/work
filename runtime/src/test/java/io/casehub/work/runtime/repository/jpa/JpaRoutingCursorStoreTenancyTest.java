package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.runtime.model.RoutingCursor;
import io.casehub.work.runtime.model.RoutingCursorId;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaRoutingCursorStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that cursors are isolated per tenant — two tenants can maintain independent
 * cursor state for the same poolHash.
 */
@QuarkusTest
@TestTransaction
class JpaRoutingCursorStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    RoutingCursorStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    // -------------------------------------------------------------------------
    // acquireNext() tenant isolation — composite key (poolHash + tenancyId)
    // -------------------------------------------------------------------------

    @Test
    void acquireNext_createsSeparateCursors_perTenant() {
        String poolHash = "abcd1234"; // same poolHash for both tenants
        int poolSize = 3;

        // Tenant A acquires: should get index 0 (first call)
        principal.setTenancyId(TENANT_A);
        int indexA1 = store.acquireNext(poolHash, poolSize);
        assertThat(indexA1).isEqualTo(0);

        // Tenant B acquires same poolHash: should also get index 0 (independent cursor)
        principal.setTenancyId(TENANT_B);
        int indexB1 = store.acquireNext(poolHash, poolSize);
        assertThat(indexB1).isEqualTo(0);

        // Tenant A acquires again: should get index 1
        principal.setTenancyId(TENANT_A);
        int indexA2 = store.acquireNext(poolHash, poolSize);
        assertThat(indexA2).isEqualTo(1);

        // Tenant B acquires again: should get index 1 (independent sequence)
        principal.setTenancyId(TENANT_B);
        int indexB2 = store.acquireNext(poolHash, poolSize);
        assertThat(indexB2).isEqualTo(1);

        // Verify cursors are separate entities in DB
        RoutingCursorId idA = new RoutingCursorId(poolHash, TENANT_A);
        RoutingCursorId idB = new RoutingCursorId(poolHash, TENANT_B);

        RoutingCursor cursorA = RoutingCursor.findById(idA);
        RoutingCursor cursorB = RoutingCursor.findById(idB);

        assertThat(cursorA).isNotNull();
        assertThat(cursorB).isNotNull();
        assertThat(cursorA.lastIndex).isEqualTo(1);
        assertThat(cursorB.lastIndex).isEqualTo(1);
    }

    @Test
    void acquireNext_roundRobinWraps_independently_perTenant() {
        String poolHash = "xyz9999";
        int poolSize = 2;

        // Tenant A: acquire 0, 1, 0 (wraps)
        principal.setTenancyId(TENANT_A);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(0);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(1);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(0); // wrap

        // Tenant B: acquire 0, 1 (separate sequence)
        principal.setTenancyId(TENANT_B);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(0);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(1);

        // Tenant A continues: should get 1
        principal.setTenancyId(TENANT_A);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(1);
    }

    @Test
    void doAcquire_stampsTenancyId_onFirstCreate() {
        String poolHash = "first-create-test";
        int poolSize = 5;

        principal.setTenancyId(TENANT_A);
        store.acquireNext(poolHash, poolSize);

        RoutingCursorId id = new RoutingCursorId(poolHash, TENANT_A);
        RoutingCursor cursor = RoutingCursor.findById(id);

        assertThat(cursor).isNotNull();
        assertThat(cursor.tenancyId).isEqualTo(TENANT_A);
        assertThat(cursor.poolHash).isEqualTo(poolHash);
    }
}
