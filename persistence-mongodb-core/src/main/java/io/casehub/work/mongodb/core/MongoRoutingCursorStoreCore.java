package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.mongodb.core.doc.MongoRoutingCursorDocument;
import org.bson.Document;

import java.time.Instant;

public class MongoRoutingCursorStoreCore implements RoutingCursorStore {

    private final MongoCollection<MongoRoutingCursorDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoRoutingCursorStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("routing_cursors", MongoRoutingCursorDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public int acquireNext(final String poolHash, final int poolSize) {
        final String id = poolHash + ":" + currentPrincipal.tenancyId();

        final Document filter = new Document("_id", id);
        final Document update = new Document()
                .append("$inc", new Document("lastIndex", 1L))
                .append("$set", new Document("lastAccessed", Instant.now()));

        final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER);

        final MongoRoutingCursorDocument result = collection.findOneAndUpdate(filter, update, options);

        final long rawIndex = result.lastIndex - 1;
        return Math.floorMod(rawIndex, poolSize);
    }
}
