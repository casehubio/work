package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoWorkItemScheduleDocument;
import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;
import jakarta.persistence.OptimisticLockException;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoWorkItemScheduleStoreCore implements WorkItemScheduleStore {

    private final MongoCollection<MongoWorkItemScheduleDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoWorkItemScheduleStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("work_item_schedules", MongoWorkItemScheduleDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public WorkItemSchedule put(final WorkItemSchedule schedule) {
        if (schedule.id == null) {
            schedule.id = UUID.randomUUID();
        }
        if (schedule.createdAt == null) {
            schedule.createdAt = Instant.now();
        }
        if (schedule.tenancyId == null) {
            schedule.tenancyId = currentPrincipal.tenancyId();
        }

        final String idStr = schedule.id.toString();

        final boolean exists = collection.find(new Document("_id", idStr)).first() != null;

        if (!exists) {
            schedule.version = 0L;
            collection.insertOne(MongoWorkItemScheduleDocument.from(schedule));
        } else {
            final Bson filter = Filters.and(
                    Filters.eq("_id", idStr),
                    Filters.eq("version", schedule.version));

            final Bson update = Updates.combine(
                    Updates.set("tenancyId", schedule.tenancyId),
                    Updates.set("name", schedule.name),
                    Updates.set("templateId", schedule.templateId != null ? schedule.templateId.toString() : null),
                    Updates.set("cronExpression", schedule.cronExpression),
                    Updates.set("active", schedule.active),
                    Updates.set("createdBy", schedule.createdBy),
                    Updates.set("createdAt", schedule.createdAt),
                    Updates.set("lastFiredAt", schedule.lastFiredAt),
                    Updates.set("nextFireAt", schedule.nextFireAt),
                    Updates.inc("version", 1L));

            final MongoWorkItemScheduleDocument result = collection
                    .findOneAndUpdate(filter, update,
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

            if (result == null) {
                throw new OptimisticLockException(
                        "Version conflict on WorkItemSchedule " + idStr);
            }

            schedule.version = result.version;
        }
        return schedule;
    }

    @Override
    public Optional<WorkItemSchedule> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemScheduleDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemScheduleDocument::toDomain);
    }

    @Override
    public List<WorkItemSchedule> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemScheduleDocument::toDomain)
                .sorted(Comparator.comparing(s -> s.name))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }

    @Override
    public List<WorkItemSchedule> findDue(final Instant now) {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId())
                .append("active", true)
                .append("nextFireAt", new Document("$ne", null).append("$lte", now));
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemScheduleDocument::toDomain)
                .sorted(Comparator.comparing(s -> s.nextFireAt))
                .toList();
    }
}
