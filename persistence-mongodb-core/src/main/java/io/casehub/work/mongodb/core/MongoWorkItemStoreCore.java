package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.mongodb.core.doc.MongoWorkItemDocument;
import jakarta.persistence.OptimisticLockException;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoWorkItemStoreCore implements WorkItemStore {

    private final MongoCollection<MongoWorkItemDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoWorkItemStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("work_items", MongoWorkItemDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public WorkItem put(final WorkItem workItem) {
        WorkItem stored = workItem;
        if (stored.id() == null) {
            stored = stored.toBuilder().id(UUID.randomUUID()).build();
        }
        if (stored.tenancyId() == null) {
            stored = stored.toBuilder().tenancyId(currentPrincipal.tenancyId()).build();
        }
        final Instant now = Instant.now();
        if (stored.createdAt() == null) {
            stored = stored.toBuilder().createdAt(now).build();
        }
        stored = stored.toBuilder().updatedAt(now).build();

        final String idStr = stored.id().toString();

        final boolean exists = collection.find(new Document("_id", idStr)).first() != null;

        if (!exists) {
            final MongoWorkItemDocument doc = MongoWorkItemDocument.from(stored);
            doc.version = 0L;
            collection.insertOne(doc);
            stored = stored.toBuilder().version(0L).build();
        } else {
            final Long expectedVersion = stored.version();
            final long versionToMatch;
            if (expectedVersion != null) {
                versionToMatch = expectedVersion;
            } else {
                final MongoWorkItemDocument existing = collection.find(
                        new Document("_id", idStr)).first();
                versionToMatch = existing != null && existing.version != null ? existing.version : 0L;
            }
            final MongoWorkItemDocument doc = MongoWorkItemDocument.from(stored);
            doc.version = versionToMatch + 1;

            final Document filter = new Document("_id", idStr)
                    .append("version", versionToMatch);

            final UpdateResult result = collection.replaceOne(filter, doc);

            if (result.getModifiedCount() == 0) {
                throw new OptimisticLockException(
                        "Version conflict on WorkItem " + idStr
                                + " (expected version " + versionToMatch + ")");
            }
            stored = stored.toBuilder().version(doc.version).build();
        }
        return stored;
    }

    @Override
    public Optional<WorkItem> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        final Document filter = buildFilter(query);
        final List<MongoWorkItemDocument> docs = filter.isEmpty()
                ? collection.find().into(new ArrayList<>())
                : collection.find(filter).into(new ArrayList<>());
        return docs.stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    @Override
    public Optional<WorkItem> findByCallerRef(final String callerRef) {
        final Document filter = new Document("callerRef", callerRef)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemDocument doc = collection.find(filter)
                .sort(new Document("createdAt", -1)).first();
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public Optional<WorkItem> findActiveByCallerRef(final String callerRef) {
        final List<String> terminalNames = WorkItemStatus.TERMINAL_STATUSES.stream()
                .map(Enum::name).toList();
        final Document filter = new Document("callerRef", callerRef)
                .append("status", new Document("$nin", terminalNames))
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemDocument doc = collection.find(filter)
                .sort(new Document("createdAt", -1)).first();
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public Optional<WorkItem> findByOrigin(String originServiceId, UUID originWorkItemId) {
        final Document filter = new Document("originServiceId", originServiceId)
                .append("originWorkItemId", originWorkItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public List<WorkItem> findByParentId(final UUID parentId) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    @Override
    public List<WorkItem> findByParentIdExcludingStatuses(final UUID parentId,
                                                          final List<WorkItemStatus> excludeStatuses) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId())
                .append("status", new Document("$nin",
                        excludeStatuses.stream().map(Enum::name).toList()));
        return collection.find(filter).into(new ArrayList<>())
                .stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    @Override
    public List<WorkItem> findByParentIdWithStatuses(final UUID parentId,
                                                     final List<WorkItemStatus> statuses) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId())
                .append("status", new Document("$in",
                        statuses.stream().map(Enum::name).toList()));
        return collection.find(filter).into(new ArrayList<>())
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
        return collection.countDocuments(filter);
    }

    private Document buildFilter(final WorkItemQuery q) {
        final List<Document> ands = new ArrayList<>();

        ands.add(new Document("tenancyId", q.tenancyId() != null ? q.tenancyId() : currentPrincipal.tenancyId()));

        final boolean hasAssigneeId = q.assigneeId() != null;
        final boolean hasCandidateGroups = q.candidateGroups() != null && !q.candidateGroups().isEmpty();
        final boolean hasCandidateUserId = q.candidateUserId() != null;

        if (hasAssigneeId || hasCandidateGroups || hasCandidateUserId) {
            final List<Document> ors = new ArrayList<>();
            if (hasAssigneeId) {
                ors.add(new Document("assigneeId", q.assigneeId()));
                ors.add(new Document("candidateUsers", q.assigneeId()));
            }
            if (hasCandidateUserId && !hasAssigneeId) {
                ors.add(new Document("candidateUsers", q.candidateUserId()));
            }
            if (hasCandidateGroups) {
                ors.add(new Document("candidateGroups", new Document("$in", q.candidateGroups())));
            }
            ands.add(new Document("$or", ors));
        }

        if (q.status() != null) {
            ands.add(new Document("status", q.status().name()));
        }

        if (q.statusIn() != null && !q.statusIn().isEmpty()) {
            ands.add(new Document("status",
                    new Document("$in", q.statusIn().stream().map(Enum::name).toList())));
        }

        if (q.priority() != null) {
            ands.add(new Document("priority", q.priority().name()));
        }

        if (q.type() != null) {
            ands.add(new Document("types",
                    new Document("$regex", "^" + java.util.regex.Pattern.quote(q.type()) + "(/|$)")));
        }

        if (q.followUpBefore() != null) {
            ands.add(new Document("followUpDate",
                    new Document("$ne", null).append("$lte", toDate(q.followUpBefore()))));
        }

        if (q.expiresAtOrBefore() != null) {
            ands.add(new Document("expiresAt",
                    new Document("$ne", null).append("$lte", toDate(q.expiresAtOrBefore()))));
        }

        if (q.claimDeadlineOrBefore() != null) {
            ands.add(new Document("claimDeadline",
                    new Document("$ne", null).append("$lte", toDate(q.claimDeadlineOrBefore()))));
        }

        if (q.labelPattern() != null) {
            ands.add(buildLabelFilter(q.labelPattern()));
        }

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

    private static Date toDate(final Instant instant) {
        return Date.from(instant);
    }
}
