package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

@Testcontainers
public abstract class MongoStoreTestBase {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    protected MongoDatabase database;

    @BeforeEach
    void setUpDatabase() {
        MongoClient client = MongoClients.create(mongo.getConnectionString());
        database = MongoDatabaseFactory.withPojoCodecs(client.getDatabase("test_" + System.nanoTime()));
    }

    protected CurrentPrincipal principal(String tenancyId, String actorId) {
        return new CurrentPrincipal() {
            @Override public String actorId() { return actorId; }
            @Override public String tenancyId() { return tenancyId; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
    }
}
