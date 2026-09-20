package io.casehub.work.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.casehub.work.mongodb.core.MongoDatabaseFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MongoDatabaseProducer {

    @Produces
    @ApplicationScoped
    public MongoDatabase mongoDatabase(MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String dbName) {
        return MongoDatabaseFactory.withPojoCodecs(client.getDatabase(dbName));
    }
}
