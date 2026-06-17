package io.casehub.work.mongodb;

import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link LabelVocabulary}.
 *
 * <p>
 * Stored in the {@code label_vocabularies} collection. Converted to and from the domain
 * {@link LabelVocabulary} by {@link MongoLabelVocabularyStore}.
 *
 * <p>
 * The {@code scope} field is stored as a String (via {@link Path#value()}) and
 * reconstructed via {@link Path#parse(String)} on read.
 */
@MongoEntity(collection = "label_vocabularies")
public class MongoLabelVocabularyDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String scope;
    public String name;

    /** Convert a domain {@link LabelVocabulary} to a MongoDB document. */
    public static MongoLabelVocabularyDocument from(final LabelVocabulary vocabulary) {
        final MongoLabelVocabularyDocument doc = new MongoLabelVocabularyDocument();
        doc.id = vocabulary.id != null ? vocabulary.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = vocabulary.tenancyId;
        doc.scope = vocabulary.scope != null ? vocabulary.scope.value() : null;
        doc.name = vocabulary.name;
        return doc;
    }

    /** Convert this document back to a domain {@link LabelVocabulary}. */
    public LabelVocabulary toDomain() {
        final LabelVocabulary vocabulary = new LabelVocabulary();
        vocabulary.id = UUID.fromString(id);
        vocabulary.tenancyId = tenancyId;
        vocabulary.scope = (scope == null || scope.isEmpty()) ? Path.root() : Path.parse(scope);
        vocabulary.name = name;
        return vocabulary;
    }
}
