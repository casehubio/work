package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "label_rules")
public class MongoLabelRuleDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String name;
    public String description;
    public String conditionLanguage;
    public String conditionExpression;
    public String actionsJson;
    public String triggerEvents;
    public String scope;
    public boolean enabled;
    public Instant createdAt;

    public static MongoLabelRuleDocument from(final LabelRuleEntity entity) {
        final MongoLabelRuleDocument doc = new MongoLabelRuleDocument();
        doc.id = entity.id != null ? entity.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = entity.tenancyId;
        doc.name = entity.name;
        doc.description = entity.description;
        doc.conditionLanguage = entity.conditionLanguage;
        doc.conditionExpression = entity.conditionExpression;
        doc.actionsJson = entity.actionsJson;
        doc.triggerEvents = entity.triggerEvents;
        doc.scope = entity.scope != null ? entity.scope.value() : null;
        doc.enabled = entity.enabled;
        doc.createdAt = entity.createdAt;
        return doc;
    }

    public LabelRuleEntity toDomain() {
        final LabelRuleEntity entity = new LabelRuleEntity();
        entity.id = UUID.fromString(id);
        entity.tenancyId = tenancyId;
        entity.name = name;
        entity.description = description;
        entity.conditionLanguage = conditionLanguage;
        entity.conditionExpression = conditionExpression;
        entity.actionsJson = actionsJson;
        entity.triggerEvents = triggerEvents;
        entity.scope = scope != null ? Path.parse(scope) : null;
        entity.enabled = enabled;
        entity.createdAt = createdAt;
        return entity;
    }
}
