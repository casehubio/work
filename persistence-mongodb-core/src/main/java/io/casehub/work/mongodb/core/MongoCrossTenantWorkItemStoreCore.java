package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.CrossTenantWorkItemStore;
import io.casehub.work.mongodb.core.doc.MongoWorkItemDocument;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MongoCrossTenantWorkItemStoreCore implements CrossTenantWorkItemStore {

    private static final List<String> TERMINAL_STATUS_NAMES =
            WorkItemStatus.TERMINAL_STATUSES.stream().map(Enum::name).toList();

    private final MongoCollection<MongoWorkItemDocument> collection;

    public MongoCrossTenantWorkItemStoreCore(MongoDatabase database) {
        this.collection = database.getCollection("work_items", MongoWorkItemDocument.class);
    }

    @Override
    public List<WorkItem> findActiveWithDeadlines() {
        Document filter = new Document("$and", List.of(
                new Document("status", new Document("$nin", TERMINAL_STATUS_NAMES)),
                new Document("$or", List.of(
                        new Document("expiresAt", new Document("$ne", null)),
                        new Document("claimDeadline", new Document("$ne", null))))));

        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemDocument::toDomain)
                .toList();
    }
}
