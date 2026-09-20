package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MongoDatabaseFactoryTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Test
    void withPojoCodecsReturnsWorkingDatabase() {
        var client = MongoClients.create(mongo.getConnectionString());
        MongoDatabase db = MongoDatabaseFactory.withPojoCodecs(client.getDatabase("test"));

        assertThat(db).isNotNull();
        assertThat(db.getCodecRegistry()).isNotNull();
    }

    @Test
    void withPojoCodecsIsIdempotent() {
        var client = MongoClients.create(mongo.getConnectionString());
        MongoDatabase db1 = MongoDatabaseFactory.withPojoCodecs(client.getDatabase("test"));
        MongoDatabase db2 = MongoDatabaseFactory.withPojoCodecs(db1);

        assertThat(db2).isNotNull();
        assertThat(db2.getCodecRegistry()).isNotNull();
    }
}
