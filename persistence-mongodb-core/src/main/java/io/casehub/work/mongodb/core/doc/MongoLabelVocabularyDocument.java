package io.casehub.work.mongodb.core.doc;

import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelVocabulary;

public class MongoLabelVocabularyDocument {

    @BsonId
    public String id;

    public String tenancyId;
    public String scope;
    public String name;

    public static MongoLabelVocabularyDocument from(final LabelVocabulary vocabulary) {
        final MongoLabelVocabularyDocument doc = new MongoLabelVocabularyDocument();
        doc.id = vocabulary.id != null ? vocabulary.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = vocabulary.tenancyId;
        doc.scope = vocabulary.scope != null ? vocabulary.scope.value() : null;
        doc.name = vocabulary.name;
        return doc;
    }

    public LabelVocabulary toDomain() {
        final LabelVocabulary vocabulary = new LabelVocabulary();
        vocabulary.id = UUID.fromString(id);
        vocabulary.tenancyId = tenancyId;
        vocabulary.scope = (scope == null || scope.isEmpty()) ? Path.root() : Path.parse(scope);
        vocabulary.name = name;
        return vocabulary;
    }
}
