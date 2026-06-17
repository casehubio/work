package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import io.casehub.work.core.strategy.RoutingCursorStore;

/**
 * Tests for {@link MongoRoutingCursorStore}.
 *
 * <p>
 * No concurrency tests — atomicity is guaranteed by MongoDB's {@code findOneAndUpdate}.
 */
@QuarkusTest
class MongoRoutingCursorStoreTest {

    @Inject
    RoutingCursorStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void setUp() {
        principal.reset();
    }

    @AfterEach
    void cleanup() {
        MongoRoutingCursorDocument.deleteAll();
    }

    @Test
    void acquireNext_firstCall_returnsZero() {
        final String poolHash = "abc123";
        final int poolSize = 5;

        final int index = store.acquireNext(poolHash, poolSize);

        assertThat(index).isEqualTo(0);
    }

    @Test
    void acquireNext_sequential_advancesAndWraps() {
        final String poolHash = "def456";
        final int poolSize = 3;

        // First cycle: 0, 1, 2
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(0);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(1);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(2);

        // Second cycle: wraps back to 0, 1, 2
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(0);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(1);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(2);
    }

    @Test
    void acquireNext_tenantIsolation() {
        final String poolHash = "ghi789";
        final int poolSize = 3;

        // Tenant A advances cursor
        principal.setTenancyId("tenant-a");
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(0);
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(1);

        // Tenant B starts from 0 (independent cursor)
        principal.setTenancyId("tenant-b");
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(0);

        // Tenant A resumes from 2
        principal.setTenancyId("tenant-a");
        assertThat(store.acquireNext(poolHash, poolSize)).isEqualTo(2);
    }
}
