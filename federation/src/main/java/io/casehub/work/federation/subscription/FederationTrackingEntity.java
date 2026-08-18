package io.casehub.work.federation.subscription;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "federation_subscription_tracking")
@IdClass(FederationTrackingEntity.TrackingId.class)
public class FederationTrackingEntity extends PanacheEntityBase {

    @Id
    @Column(name = "subscription_id")
    public UUID subscriptionId;

    @Id
    @Column(name = "work_item_id")
    public UUID workItemId;

    public static class TrackingId implements Serializable {
        public UUID subscriptionId;
        public UUID workItemId;

        public TrackingId() {}

        public TrackingId(UUID subscriptionId, UUID workItemId) {
            this.subscriptionId = subscriptionId;
            this.workItemId = workItemId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TrackingId t)) return false;
            return Objects.equals(subscriptionId, t.subscriptionId) && Objects.equals(workItemId, t.workItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subscriptionId, workItemId);
        }
    }
}
