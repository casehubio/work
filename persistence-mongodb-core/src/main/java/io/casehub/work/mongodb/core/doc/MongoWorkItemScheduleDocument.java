package io.casehub.work.mongodb.core.doc;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.model.WorkItemSchedule;

public class MongoWorkItemScheduleDocument {

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
