package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.repository.CrossTenantRoutingCursorStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoCrossTenantRoutingCursorStoreTest {

    @Inject
    MutableCurrentPrincipal principal;

    @Inject
    CrossTenantRoutingCursorStore store;

    @BeforeEach
    void setUp() {
        principal.reset();
        MongoRoutingCursorDocument.deleteAll();
    }

    @Test
    void cleanupStale_deletesOldCursorsAcrossTenants() {
        Instant stale = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant fresh = Instant.now();

        persistCursor("pool1:tenant-a", 3, stale);
        persistCursor("pool2:tenant-b", 7, stale);
        persistCursor("pool3:tenant-a", 1, fresh);

        Instant cutoff = Instant.now().minus(5, ChronoUnit.DAYS);
        long deleted = store.cleanupStale(cutoff);

        assertThat(deleted).isEqualTo(2);
        assertThat(MongoRoutingCursorDocument.count()).isEqualTo(1);
    }

    @Test
    void cleanupStale_returnsZero_whenNoneStale() {
        Instant fresh = Instant.now();
        persistCursor("pool1:tenant-a", 3, fresh);

        Instant cutoff = Instant.now().minus(5, ChronoUnit.DAYS);
        long deleted = store.cleanupStale(cutoff);

        assertThat(deleted).isEqualTo(0);
        assertThat(MongoRoutingCursorDocument.count()).isEqualTo(1);
    }

    @Test
    void cleanupStale_returnsZero_whenEmpty() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.DAYS);
        long deleted = store.cleanupStale(cutoff);

        assertThat(deleted).isEqualTo(0);
    }

    private void persistCursor(String id, long lastIndex, Instant lastAccessed) {
        MongoRoutingCursorDocument doc = new MongoRoutingCursorDocument();
        doc.id = id;
        doc.lastIndex = lastIndex;
        doc.lastAccessed = lastAccessed;
        doc.persist();
    }
}
