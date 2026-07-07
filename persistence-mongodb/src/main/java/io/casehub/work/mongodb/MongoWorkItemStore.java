package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mongodb.client.result.UpdateResult;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import org.bson.Document;

import io.casehub.platform.api.identity.CurrentPrincipal;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * MongoDB implementation of {@link WorkItemStore}.
 *
 * <p>
 * Selected by CDI over the default {@code JpaWorkItemStore} when this module is on
 * the classpath. Translates {@link WorkItemQuery} to MongoDB {@link Document} filters:
 * assignment fields use {@code $or} logic; all other filters are combined with
 * {@code $and}. Label patterns use {@code $regex} on the embedded {@code labels.path}
 * array field.
 *
 * <p>
 * {@code candidateGroups} and {@code candidateUsers} are stored as arrays in MongoDB
 * (split from the JPA comma-separated string on write, rejoined on read), enabling
 * efficient {@code $in} and element-match queries.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemStore implements WorkItemStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkItem put(final WorkItem workItem) {
        if (workItem.id == null) {
            workItem.id = UUID.randomUUID();
        }
        if (workItem.tenancyId == null) {
            workItem.tenancyId = currentPrincipal.tenancyId();
        }
        final Instant now = Instant.now();
        if (workItem.createdAt == null) {
            workItem.createdAt = now;
        }
        workItem.updatedAt = now;

        final String idStr = workItem.id.toString();

        final boolean exists = MongoWorkItemDocument.find(
                new Document("_id", idStr)).firstResult() != null;

        if (!exists) {
            // New document — plain persist with version 0
            workItem.version = 0L;
            MongoWorkItemDocument.from(workItem).persist();
        } else {
            // Existing document — replaceOne with version check (OCC)
            final MongoWorkItemDocument doc = MongoWorkItemDocument.from(workItem);
            final long currentVersion = workItem.version != null ? workItem.version : 0L;
            doc.version = currentVersion + 1;

            final Document filter = new Document("_id", idStr)
                    .append("version", workItem.version);

            final UpdateResult result =
                    MongoWorkItemDocument.mongoCollection().replaceOne(filter, doc);

            if (result.getModifiedCount() == 0) {
                throw new OptimisticLockException(
                        "Version conflict on WorkItem " + idStr
                                + " (expected version " + workItem.version + ")");
            }

            workItem.version = doc.version;
        }
        return workItem;
    }

    @Override
    public Optional<WorkItem> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemDocument doc = MongoWorkItemDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        final Document filter = buildFilter(query);
        final List<MongoWorkItemDocument> docs = filter.isEmpty()
                ? MongoWorkItemDocument.listAll()
                : MongoWorkItemDocument.<MongoWorkItemDocument> find(filter).list();
        return docs.stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    @Override
    public Optional<WorkItem> findByCallerRef(final String callerRef) {
        final Document filter = new Document("callerRef", callerRef)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemDocument doc = MongoWorkItemDocument.find(filter,
                new Document("createdAt", -1)).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public Optional<WorkItem> findActiveByCallerRef(final String callerRef) {
        final List<String> terminalNames = WorkItemStatus.TERMINAL_STATUSES.stream()
                .map(Enum::name).toList();
        final Document filter = new Document("callerRef", callerRef)
                .append("status", new Document("$nin", terminalNames))
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemDocument doc = MongoWorkItemDocument.find(filter,
                new Document("createdAt", -1)).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public List<WorkItem> findByParentId(final UUID parentId) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoWorkItemDocument.<MongoWorkItemDocument> find(filter).list()
                .stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    @Override
    public List<WorkItem> findByParentIdExcludingStatuses(final UUID parentId,
            final List<WorkItemStatus> excludeStatuses) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId())
                .append("status", new Document("$nin",
                        excludeStatuses.stream().map(Enum::name).toList()));
        return MongoWorkItemDocument.<MongoWorkItemDocument> find(filter).list()
                .stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    @Override
    public List<WorkItem> findByParentIdWithStatuses(final UUID parentId,
            final List<WorkItemStatus> statuses) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId())
                .append("status", new Document("$in",
                        statuses.stream().map(Enum::name).toList()));
        return MongoWorkItemDocument.<MongoWorkItemDocument> find(filter).list()
                .stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    @Override
    public long countByParentAndAssignee(final UUID parentId, final String assigneeId,
            final UUID excludeId) {
        final List<String> terminalNames = WorkItemStatus.TERMINAL_STATUSES.stream()
                .map(Enum::name).toList();
        final Document filter = new Document("parentId", parentId.toString())
                .append("assigneeId", assigneeId)
                .append("tenancyId", currentPrincipal.tenancyId())
                .append("_id", new Document("$ne", excludeId.toString()))
                .append("status", new Document("$nin", terminalNames));
        return MongoWorkItemDocument.count(filter);
    }

    // ── Filter builder ────────────────────────────────────────────────────────

    private Document buildFilter(final WorkItemQuery q) {
        final List<Document> ands = new ArrayList<>();

        // Tenant filtering
        ands.add(new Document("tenancyId", q.tenancyId() != null ? q.tenancyId() : currentPrincipal.tenancyId()));

        // Assignment — OR logic across three dimensions
        final boolean hasAssigneeId = q.assigneeId() != null;
        final boolean hasCandidateGroups = q.candidateGroups() != null && !q.candidateGroups().isEmpty();
        final boolean hasCandidateUserId = q.candidateUserId() != null;

        if (hasAssigneeId || hasCandidateGroups || hasCandidateUserId) {
            final List<Document> ors = new ArrayList<>();
            if (hasAssigneeId) {
                ors.add(new Document("assigneeId", q.assigneeId()));
                // array contains q.assigneeId()
                ors.add(new Document("candidateUsers", q.assigneeId()));
            }
            if (hasCandidateUserId && !hasAssigneeId) {
                ors.add(new Document("candidateUsers", q.candidateUserId()));
            }
            if (hasCandidateGroups) {
                // array intersects q.candidateGroups()
                ors.add(new Document("candidateGroups", new Document("$in", q.candidateGroups())));
            }
            ands.add(new Document("$or", ors));
        }

        // Status (exact)
        if (q.status() != null) {
            ands.add(new Document("status", q.status().name()));
        }

        // StatusIn
        if (q.statusIn() != null && !q.statusIn().isEmpty()) {
            ands.add(new Document("status",
                    new Document("$in", q.statusIn().stream().map(Enum::name).toList())));
        }

        // Priority
        if (q.priority() != null) {
            ands.add(new Document("priority", q.priority().name()));
        }

        // Type — ancestor matching via regex
        if (q.type() != null) {
            ands.add(new Document("types",
                    new Document("$regex", "^" + java.util.regex.Pattern.quote(q.type()) + "(/|$)")));
        }

        // FollowUpBefore — field must exist (not null) and be <= threshold
        if (q.followUpBefore() != null) {
            ands.add(new Document("followUpDate",
                    new Document("$ne", null).append("$lte", toDate(q.followUpBefore()))));
        }

        // ExpiresAtOrBefore — field must exist (not null) and be <= threshold
        if (q.expiresAtOrBefore() != null) {
            ands.add(new Document("expiresAt",
                    new Document("$ne", null).append("$lte", toDate(q.expiresAtOrBefore()))));
        }

        // ClaimDeadlineOrBefore — field must exist (not null) and be <= threshold
        if (q.claimDeadlineOrBefore() != null) {
            ands.add(new Document("claimDeadline",
                    new Document("$ne", null).append("$lte", toDate(q.claimDeadlineOrBefore()))));
        }

        // Label pattern — regex on embedded array field
        if (q.labelPattern() != null) {
            ands.add(buildLabelFilter(q.labelPattern()));
        }

        // Outcome
        if (q.outcome() != null) {
            ands.add(new Document("outcome", q.outcome()));
        }

        if (ands.isEmpty()) {
            return new Document();
        }
        if (ands.size() == 1) {
            return ands.get(0);
        }
        return new Document("$and", ands);
    }

    private Document buildLabelFilter(final String pattern) {
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3) + "/";
            return new Document("labels.path", new Document("$regex", "^" + prefix));
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2) + "/";
            return new Document("labels.path", new Document("$regex", "^" + prefix + "[^/]+$"));
        }
        return new Document("labels.path", pattern);
    }

    /** Convert Instant to java.util.Date for BSON date comparison in raw Document queries. */
    private static Date toDate(final Instant instant) {
        return Date.from(instant);
    }
}
