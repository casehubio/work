package io.casehub.work.queues.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "queue_snapshot")
public class QueueSnapshot extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "queue_view_id", nullable = false)
    public UUID queueViewId;

    @Column(name = "member_count", nullable = false)
    public long memberCount;

    @Column(name = "snapshot_at", nullable = false)
    public Instant snapshotAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
