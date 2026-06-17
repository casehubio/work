package io.casehub.work.mongodb;

import java.time.Instant;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

import io.casehub.platform.api.identity.CurrentPrincipal;

import io.casehub.work.core.strategy.RoutingCursorStore;

/**
 * MongoDB implementation of {@link RoutingCursorStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All cursors are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 *
 * <p>
 * The {@link #acquireNext(String, int)} method uses MongoDB's atomic {@code findOneAndUpdate}
 * with {@code $inc} to advance the cursor safely under concurrent access.
 *
 * <p>
 * <strong>First call upsert behavior:</strong><br>
 * MongoDB's {@code $inc} initializes missing fields to 0 before incrementing.<br>
 * First call: {@code 0 + 1 = 1}, second call: {@code 1 + 1 = 2}, etc.<br>
 * The method subtracts 1 from the result to convert to 0-based indexing.
 *
 * <p>
 * <strong>Modulo wrapping:</strong><br>
 * Uses {@link Math#floorMod(long, int)} (not {@code %}) to ensure non-negative results.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoRoutingCursorStore implements RoutingCursorStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Inject
    MongoClient mongoClient;

    @Override
    public int acquireNext(final String poolHash, final int poolSize) {
        final String id = poolHash + ":" + currentPrincipal.tenancyId();

        // Atomic findOneAndUpdate with upsert
        // MongoDB's $inc initializes missing fields to 0 before incrementing
        // First call: 0 + 1 = 1, second call: 1 + 1 = 2, etc.
        // Subtract 1 to get 0-based indexing (1 → 0, 2 → 1, etc.)
        final Document filter = new Document("_id", id);
        final Document update = new Document()
                .append("$inc", new Document("lastIndex", 1L))
                .append("$set", new Document("lastAccessed", Instant.now()));

        final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER);

        final Document result = mongoClient.getDatabase("workitems")
                .getCollection("routing_cursors")
                .findOneAndUpdate(filter, update, options);

        // Returned value is 1-based (first call returns 1, second returns 2, etc.)
        // Convert to 0-based index
        final long rawIndex = result.getLong("lastIndex") - 1;
        return Math.floorMod(rawIndex, poolSize);
    }
}
