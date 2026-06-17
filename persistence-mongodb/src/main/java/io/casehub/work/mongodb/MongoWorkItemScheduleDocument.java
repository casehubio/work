package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link WorkItemSchedule}.
 *
 * <p>
 * Stored in the {@code work_item_schedules} collection. Converted to and from the domain
 * {@link WorkItemSchedule} by {@link MongoWorkItemScheduleStore}.
 */
@MongoEntity(collection = "work_item_schedules")
public class MongoWorkItemScheduleDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public Long version;
    public String tenancyId;
    public String name;
    public String templateId;
    public String cronExpression;
    public boolean active;
    public String createdBy;
    public Instant createdAt;
    public Instant lastFiredAt;
    public Instant nextFireAt;

    /** Convert a domain {@link WorkItemSchedule} to a MongoDB document. */
    public static MongoWorkItemScheduleDocument from(final WorkItemSchedule schedule) {
        final MongoWorkItemScheduleDocument doc = new MongoWorkItemScheduleDocument();
        doc.id = schedule.id != null ? schedule.id.toString() : UUID.randomUUID().toString();
        doc.version = schedule.version;
        doc.tenancyId = schedule.tenancyId;
        doc.name = schedule.name;
        doc.templateId = schedule.templateId != null ? schedule.templateId.toString() : null;
        doc.cronExpression = schedule.cronExpression;
        doc.active = schedule.active;
        doc.createdBy = schedule.createdBy;
        doc.createdAt = schedule.createdAt;
        doc.lastFiredAt = schedule.lastFiredAt;
        doc.nextFireAt = schedule.nextFireAt;
        return doc;
    }

    /** Convert this document back to a domain {@link WorkItemSchedule}. */
    public WorkItemSchedule toDomain() {
        final WorkItemSchedule schedule = new WorkItemSchedule();
        schedule.id = UUID.fromString(id);
        schedule.version = version;
        schedule.tenancyId = tenancyId;
        schedule.name = name;
        schedule.templateId = templateId != null ? UUID.fromString(templateId) : null;
        schedule.cronExpression = cronExpression;
        schedule.active = active;
        schedule.createdBy = createdBy;
        schedule.createdAt = createdAt;
        schedule.lastFiredAt = lastFiredAt;
        schedule.nextFireAt = nextFireAt;
        return schedule;
    }
}
