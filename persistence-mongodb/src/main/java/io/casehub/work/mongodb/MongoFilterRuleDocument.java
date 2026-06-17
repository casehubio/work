package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.filter.FilterRule;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link FilterRule}.
 *
 * <p>
 * Stored in the {@code filter_rules} collection. Converted to and from the domain
 * {@link FilterRule} by {@link MongoFilterRuleStore}.
 */
@MongoEntity(collection = "filter_rules")
public class MongoFilterRuleDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String name;
    public String description;
    public boolean enabled;
    public String condition;
    public String events;
    public String actionsJson;
    public Instant createdAt;

    /** Convert a domain {@link FilterRule} to a MongoDB document. */
    public static MongoFilterRuleDocument from(final FilterRule rule) {
        final MongoFilterRuleDocument doc = new MongoFilterRuleDocument();
        doc.id = rule.id != null ? rule.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = rule.tenancyId;
        doc.name = rule.name;
        doc.description = rule.description;
        doc.enabled = rule.enabled;
        doc.condition = rule.condition;
        doc.events = rule.events;
        doc.actionsJson = rule.actionsJson;
        doc.createdAt = rule.createdAt;
        return doc;
    }

    /** Convert this document back to a domain {@link FilterRule}. */
    public FilterRule toDomain() {
        final FilterRule rule = new FilterRule();
        rule.id = UUID.fromString(id);
        rule.tenancyId = tenancyId;
        rule.name = name;
        rule.description = description;
        rule.enabled = enabled;
        rule.condition = condition;
        rule.events = events;
        rule.actionsJson = actionsJson;
        rule.createdAt = createdAt;
        return rule;
    }
}
