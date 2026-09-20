package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoAuditEntryDocument;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MongoAuditEntryStoreCore implements AuditEntryStore {

    private final MongoCollection<MongoAuditEntryDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoAuditEntryStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("audit_entries", MongoAuditEntryDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public void append(final AuditEntry entry) {
        if (entry.tenancyId == null) {
            entry.tenancyId = currentPrincipal.tenancyId();
        }
        collection.insertOne(MongoAuditEntryDocument.from(entry));
    }

    @Override
    public List<AuditEntry> findByWorkItemId(final UUID workItemId) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoAuditEntryDocument::toDomain)
                .toList();
    }

    @Override
    public List<AuditEntry> query(final AuditQuery query) {
        return collection.find(buildFilter(query))
                .skip(query.page() * query.size())
                .limit(query.size())
                .into(new ArrayList<>())
                .stream()
                .map(MongoAuditEntryDocument::toDomain)
                .toList();
    }

    @Override
    public long count(final AuditQuery query) {
        return collection.countDocuments(buildFilter(query));
    }

    private Document buildFilter(final AuditQuery query) {
        final Document filter = new Document();
        filter.append("tenancyId", currentPrincipal.tenancyId());
        if (query.actorId() != null) {
            filter.append("actor", query.actorId());
        }
        if (query.event() != null) {
            filter.append("event", query.event());
        }
        if (query.from() != null || query.to() != null) {
            final Document dateFilter = new Document();
            if (query.from() != null) {
                dateFilter.append("$gte", query.from());
            }
            if (query.to() != null) {
                dateFilter.append("$lte", query.to());
            }
            filter.append("occurredAt", dateFilter);
        }
        return filter;
    }
}
