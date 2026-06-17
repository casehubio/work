package io.casehub.work.mongodb;

import java.time.Instant;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import org.bson.codecs.pojo.annotations.BsonId;

/**
 * MongoDB document for {@code RoutingCursorStore} state.
 *
 * <p>
 * Stores the last-assigned index for each unique candidate pool, partitioned by tenant.
 * This document class exists mainly so the collection is registered with Panache — the
 * store accesses the raw collection via {@code MongoClient} for atomic {@code findOneAndUpdate}.
 *
 * <p>
 * <strong>Composite key:</strong> {@code poolHash + ":" + tenancyId}
 */
@MongoEntity(collection = "routing_cursors")
public class MongoRoutingCursorDocument extends PanacheMongoEntityBase {

    /**
     * Composite key: {@code poolHash + ":" + tenancyId}.
     */
    @BsonId
    public String id;

    /**
     * Last assigned index (0-based). Advanced atomically via {@code $inc}.
     *
     * <p>
     * MUST be {@code long} (not {@code int}) — BSON int64 for overflow safety.
     */
    public long lastIndex;

    /**
     * Timestamp of the last cursor access.
     */
    public Instant lastAccessed;
}
