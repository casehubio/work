package io.casehub.work.mongodb.core;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

public final class MongoDatabaseFactory {

    private MongoDatabaseFactory() {}

    public static MongoDatabase withPojoCodecs(MongoDatabase database) {
        CodecRegistry pojoCodec = CodecRegistries.fromProviders(
                PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry registry = CodecRegistries.fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(), pojoCodec);
        return database.withCodecRegistry(registry);
    }
}
