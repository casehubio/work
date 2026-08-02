package io.casehub.work.progress.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.work.progress.ProgressStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "progress_instance")
public class ProgressInstanceEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Version
    @Column(nullable = false)
    public Long version = 0L;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "scope_type", nullable = false)
    public String scopeType;

    @Column(name = "scope_id", nullable = false)
    public String scopeId;

    @Column(name = "parent_progress_id")
    public UUID parentProgressId;

    @Column(name = "root_progress_id", nullable = false)
    public UUID rootProgressId;

    @Column(name = "shape_type", nullable = false)
    public String shapeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    public JsonNode definition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public JsonNode state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ProgressStatus status;

    @Column(name = "rollup_strategy_id")
    public String rollupStrategyId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
