package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import io.casehub.platform.api.identity.CurrentPrincipal;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;

/**
 * MongoDB implementation of {@link WorkItemScheduleStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 *
 * <p>
 * The {@link #put(WorkItemSchedule)} method implements optimistic concurrency control
 * via MongoDB's {@code findOneAndUpdate} with a version field filter. This prevents
 * double-fire of recurring schedules in clustered deployments.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemScheduleStore implements WorkItemScheduleStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkItemSchedule put(final WorkItemSchedule schedule) {
        // Assign defaults for new entities
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

        // Check if this is a new document or an update
        final boolean exists = MongoWorkItemScheduleDocument.find(
                new Document("_id", idStr)).firstResult() != null;

        if (!exists) {
            // New document — plain persist with version 0
            schedule.version = 0L;
            MongoWorkItemScheduleDocument.from(schedule).persist();
        } else {
            // Existing document — findOneAndUpdate with version check (atomic OCC)
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

            final MongoWorkItemScheduleDocument result = (MongoWorkItemScheduleDocument)
                    MongoWorkItemScheduleDocument.mongoCollection()
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
        final MongoWorkItemScheduleDocument doc = MongoWorkItemScheduleDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemScheduleDocument::toDomain);
    }

    @Override
    public List<WorkItemSchedule> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemScheduleDocument> docs = MongoWorkItemScheduleDocument.<MongoWorkItemScheduleDocument> find(
                filter).list();
        return docs.stream()
                .map(MongoWorkItemScheduleDocument::toDomain)
                .sorted(Comparator.comparing(s -> s.name))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoWorkItemScheduleDocument.delete(filter) > 0;
    }

    @Override
    public List<WorkItemSchedule> findDue(final Instant now) {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId())
                .append("active", true)
                .append("nextFireAt", new Document("$ne", null).append("$lte", now));
        final List<MongoWorkItemScheduleDocument> docs = MongoWorkItemScheduleDocument.<MongoWorkItemScheduleDocument> find(
                filter).list();
        return docs.stream()
                .map(MongoWorkItemScheduleDocument::toDomain)
                .sorted(Comparator.comparing(s -> s.nextFireAt))
                .toList();
    }
}
