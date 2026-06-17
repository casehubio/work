package io.casehub.work.mongodb;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.bson.Document;

import com.mongodb.client.model.IndexOptions;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class MongoIndexInitializer {

    void onStart(@Observes StartupEvent event) {
        final IndexOptions unique = new IndexOptions().unique(true);

        MongoWorkItemTemplateDocument.mongoCollection().createIndex(
                new Document("name", 1).append("tenancyId", 1), unique);

        MongoWorkItemSpawnGroupDocument.mongoCollection().createIndex(
                new Document("parentId", 1).append("idempotencyKey", 1), unique);

        MongoWorkItemRelationDocument.mongoCollection().createIndex(
                new Document("sourceId", 1).append("targetId", 1).append("relationType", 1), unique);

        MongoIssueLinkDocument.mongoCollection().createIndex(
                new Document("workItemId", 1).append("trackerType", 1).append("externalRef", 1), unique);

        MongoLabelVocabularyDocument.mongoCollection().createIndex(
                new Document("scope", 1).append("tenancyId", 1), unique);
    }
}
