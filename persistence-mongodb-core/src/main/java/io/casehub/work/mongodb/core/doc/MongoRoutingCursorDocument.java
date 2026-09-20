package io.casehub.work.mongodb.core.doc;

import java.time.Instant;

import org.bson.codecs.pojo.annotations.BsonId;

public class MongoRoutingCursorDocument {

    @BsonId
    public String id;

    public long lastIndex;

    public Instant lastAccessed;
}
