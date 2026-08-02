package io.casehub.work.progress.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "progress_event")
public class ProgressEventEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "progress_id", nullable = false)
    public UUID progressId;

    @Column(name = "root_progress_id", nullable = false)
    public UUID rootProgressId;

    @Column(name = "scope_type", nullable = false)
    public String scopeType;

    @Column(name = "scope_id", nullable = false)
    public String scopeId;

    @Column(name = "change_type", nullable = false)
    public String changeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state")
    public JsonNode previousState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_state")
    public JsonNode currentState;

    @Column(nullable = false)
    public String status;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}
