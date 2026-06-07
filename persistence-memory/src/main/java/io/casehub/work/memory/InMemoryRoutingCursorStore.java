package io.casehub.work.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.core.strategy.RoutingCursorStore;

/**
 * In-memory {@link RoutingCursorStore} for ephemeral deployments and tests.
 *
 * <p>
 * Tier 3 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * JPA (Tier 1) when on the classpath. No Tier 2 (MongoDB) exists for this SPI
 * yet (tracked as casehubio/work#253).
 *
 * <p>
 * Thread-safe (lock-free CAS loop). Data is ephemeral (lost on restart).
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryRoutingCursorStore implements RoutingCursorStore {

    private final ConcurrentHashMap<String, AtomicInteger> cursors = new ConcurrentHashMap<>();

    @Override
    public int acquireNext(final String poolHash, final int poolSize) {
        final AtomicInteger cursor = cursors.computeIfAbsent(poolHash, k -> new AtomicInteger(-1));
        while (true) {
            final int current = cursor.get();
            final int next = (current + 1) % poolSize;
            if (cursor.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /** Resets all cursors. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void reset() {
        cursors.clear();
    }
}
