package io.casehub.work.mongodb.core.doc;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;

public class MongoLabelDefinitionDocument {

    @BsonId
    public String id;

    public String tenancyId;
    public String path;
    public String vocabularyId;
    public String description;
    public String createdBy;
    public Instant createdAt;

    public static MongoLabelDefinitionDocument from(final LabelDefinition definition) {
        final MongoLabelDefinitionDocument doc = new MongoLabelDefinitionDocument();
        doc.id = definition.id != null ? definition.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = definition.tenancyId;
        doc.path = definition.path != null ? definition.path.value() : null;
        doc.vocabularyId = definition.vocabularyId != null ? definition.vocabularyId.toString() : null;
        doc.description = definition.description;
        doc.createdBy = definition.createdBy;
        doc.createdAt = definition.createdAt;
        return doc;
    }

    public LabelDefinition toDomain() {
        final LabelDefinition definition = new LabelDefinition();
        definition.id = UUID.fromString(id);
        definition.tenancyId = tenancyId;
        definition.path = path != null ? Path.parse(path) : null;
        definition.vocabularyId = vocabularyId != null ? UUID.fromString(vocabularyId) : null;
        definition.description = description;
        definition.createdBy = createdBy;
        definition.createdAt = createdAt;
        return definition;
    }
}
