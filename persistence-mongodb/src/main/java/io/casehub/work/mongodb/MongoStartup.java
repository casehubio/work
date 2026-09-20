package io.casehub.work.mongodb;

import com.mongodb.client.MongoDatabase;
import io.casehub.work.mongodb.core.MongoIndexInitializer;
import io.casehub.work.mongodb.core.MongoTenancyMigration;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class MongoStartup {

    @Inject
    MongoDatabase database;

    void onStartup(@Observes StartupEvent event) {
        new MongoTenancyMigration(database).run();
        new MongoIndexInitializer(database).init();
    }
}
