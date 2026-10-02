package io.casehub.work.queues.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


/** Soft-assignment state for a WorkItem within the queue subsystem. */
@Entity
@Table(name = "work_item_queue_state")
public class WorkItemQueueState {

    /** Primary key — matches the WorkItem UUID; no auto-generation. */
    @Id
    @Column(name = "work_item_id")
    public UUID workItemId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    /**
     * When {@code true}, the assigned user has indicated they are willing to release
     * this WorkItem back to the queue so another actor may claim it.
     */
    public boolean relinquishable = false;

}
