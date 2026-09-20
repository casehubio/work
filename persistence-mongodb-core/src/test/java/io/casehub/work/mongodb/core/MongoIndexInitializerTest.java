package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MongoIndexInitializerTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    MongoDatabase database;

    @BeforeEach
    void setUp() {
        var client = MongoClients.create(mongo.getConnectionString());
        database = MongoDatabaseFactory.withPojoCodecs(
                client.getDatabase("test_" + System.nanoTime()));
    }

    @Test
    void initCreatesUniqueIndexes() {
        new MongoIndexInitializer(database).init();

        assertHasCompoundIndex("work_item_templates", "name", "tenancyId");
        assertHasCompoundIndex("work_item_spawn_groups", "parentId", "idempotencyKey");
        assertHasCompoundIndex("work_item_relations", "sourceId", "targetId");
        assertHasCompoundIndex("issue_links", "workItemId", "trackerType");
        assertHasCompoundIndex("label_vocabularies", "scope", "tenancyId");
    }

    @Test
    void initIsIdempotent() {
        new MongoIndexInitializer(database).init();
        new MongoIndexInitializer(database).init();

        List<Document> indexes = database.getCollection("work_item_templates")
                .listIndexes().into(new ArrayList<>());
        assertThat(indexes).hasSize(2);
    }

    private void assertHasCompoundIndex(String collectionName, String... fields) {
        List<Document> indexes = database.getCollection(collectionName)
                .listIndexes().into(new ArrayList<>());
        assertThat(indexes).hasSizeGreaterThan(1);
        boolean found = indexes.stream()
                .map(idx -> idx.get("key", Document.class))
                .filter(key -> key != null)
                .anyMatch(key -> {
                    for (String field : fields) {
                        if (!key.containsKey(field)) return false;
                    }
                    return true;
                });
        assertThat(found).as("Expected compound index on %s with fields %s",
                collectionName, List.of(fields)).isTrue();
    }
}
