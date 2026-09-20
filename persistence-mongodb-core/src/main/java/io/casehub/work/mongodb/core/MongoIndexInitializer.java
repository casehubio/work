package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;

public class MongoIndexInitializer {

    private final MongoDatabase database;

    public MongoIndexInitializer(MongoDatabase database) {
        this.database = database;
    }

    public void init() {
        final IndexOptions unique = new IndexOptions().unique(true);

        database.getCollection("work_item_templates").createIndex(
                new Document("name", 1).append("tenancyId", 1), unique);

        database.getCollection("work_item_spawn_groups").createIndex(
                new Document("parentId", 1).append("idempotencyKey", 1), unique);

        database.getCollection("work_item_relations").createIndex(
                new Document("sourceId", 1).append("targetId", 1).append("relationType", 1), unique);

        database.getCollection("issue_links").createIndex(
                new Document("workItemId", 1).append("trackerType", 1).append("externalRef", 1), unique);

        database.getCollection("label_vocabularies").createIndex(
                new Document("scope", 1).append("tenancyId", 1), unique);
    }
}
