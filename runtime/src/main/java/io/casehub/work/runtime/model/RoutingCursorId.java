package io.casehub.work.runtime.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link RoutingCursor}.
 *
 * <p>
 * Used as the {@code @IdClass} for RoutingCursor, combining {@code poolHash}
 * and {@code tenancyId} into a single identity.
 */
public class RoutingCursorId implements Serializable {
    public String poolHash;
    public String tenancyId;

    public RoutingCursorId() {}

    public RoutingCursorId(String poolHash, String tenancyId) {
        this.poolHash = poolHash;
        this.tenancyId = tenancyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoutingCursorId that)) return false;
        return Objects.equals(poolHash, that.poolHash) && Objects.equals(tenancyId, that.tenancyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(poolHash, tenancyId);
    }
}
