package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.casehub.work.mongodb.core.doc.MongoRoutingCursorDocument;
import io.casehub.work.runtime.repository.CrossTenantRoutingCursorStore;
import org.bson.Document;

import java.time.Instant;
import java.util.Date;

public class MongoCrossTenantRoutingCursorStoreCore implements CrossTenantRoutingCursorStore {

    private final MongoCollection<MongoRoutingCursorDocument> collection;

    public MongoCrossTenantRoutingCursorStoreCore(MongoDatabase database) {
        this.collection = database.getCollection("routing_cursors", MongoRoutingCursorDocument.class);
    }

    @Override
    public long cleanupStale(Instant cutoff) {
        return collection.deleteMany(
                new Document("lastAccessed", new Document("$lt", Date.from(cutoff)))
        ).getDeletedCount();
    }
}
