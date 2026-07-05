package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link WorkItemTemplate}.
 *
 * <p>
 * Stored in the {@code work_item_templates} collection. Converted to and from the domain
 * {@link WorkItemTemplate} by {@link MongoWorkItemTemplateStore}.
 */
@MongoEntity(collection = "work_item_templates")
public class MongoWorkItemTemplateDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String name;
    public String description;
    public String category;
    public String priority;
    public String candidateGroups;
    public String candidateUsers;
    public String requiredCapabilities;
    public Integer defaultExpiryHours;
    public Integer defaultClaimHours;
    public Integer defaultExpiryBusinessHours;
    public Integer defaultClaimBusinessHours;
    public String defaultPayload;
    public String labelPaths;
    public String outcomes;
    public String excludedUsers;
    public String excludedGroups;
    public String scope;
    public String inputDataSchema;
    public String outputDataSchema;
    public Integer instanceCount;
    public Integer requiredCount;
    public String parentRole;
    public String assignmentStrategy;
    public String onThresholdReached;
    public Boolean allowSameAssignee;
    public long version;
    public String createdBy;
    public Instant createdAt;

    /** Convert a domain {@link WorkItemTemplate} to a MongoDB document. */
    public static MongoWorkItemTemplateDocument from(final WorkItemTemplate template) {
        final MongoWorkItemTemplateDocument doc = new MongoWorkItemTemplateDocument();
        doc.id = template.id != null ? template.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = template.tenancyId;
        doc.name = template.name;
        doc.description = template.description;
        doc.category = template.category;
        doc.priority = template.priority != null ? template.priority.name() : null;
        doc.candidateGroups = template.candidateGroups;
        doc.candidateUsers = template.candidateUsers;
        doc.requiredCapabilities = template.requiredCapabilities;
        doc.defaultExpiryHours = template.defaultExpiryHours;
        doc.defaultClaimHours = template.defaultClaimHours;
        doc.defaultExpiryBusinessHours = template.defaultExpiryBusinessHours;
        doc.defaultClaimBusinessHours = template.defaultClaimBusinessHours;
        doc.defaultPayload = template.defaultPayload;
        doc.labelPaths = template.labelPaths;
        doc.outcomes = template.outcomes;
        doc.excludedUsers = template.excludedUsers;
        doc.excludedGroups = template.excludedGroups;
        doc.scope = template.scope;
        doc.inputDataSchema = template.inputDataSchema;
        doc.outputDataSchema = template.outputDataSchema;
        doc.instanceCount = template.instanceCount;
        doc.requiredCount = template.requiredCount;
        doc.parentRole = template.parentRole;
        doc.assignmentStrategy = template.assignmentStrategy;
        doc.onThresholdReached = template.onThresholdReached;
        doc.allowSameAssignee = template.allowSameAssignee;
        doc.version = template.version;
        doc.createdBy = template.createdBy;
        doc.createdAt = template.createdAt;
        return doc;
    }

    /** Convert this document back to a domain {@link WorkItemTemplate}. */
    public WorkItemTemplate toDomain() {
        final WorkItemTemplate template = new WorkItemTemplate();
        template.id = UUID.fromString(id);
        template.tenancyId = tenancyId;
        template.name = name;
        template.description = description;
        template.category = category;
        template.priority = priority != null ? WorkItemPriority.valueOf(priority) : null;
        template.candidateGroups = candidateGroups;
        template.candidateUsers = candidateUsers;
        template.requiredCapabilities = requiredCapabilities;
        template.defaultExpiryHours = defaultExpiryHours;
        template.defaultClaimHours = defaultClaimHours;
        template.defaultExpiryBusinessHours = defaultExpiryBusinessHours;
        template.defaultClaimBusinessHours = defaultClaimBusinessHours;
        template.defaultPayload = defaultPayload;
        template.labelPaths = labelPaths;
        template.outcomes = outcomes;
        template.excludedUsers = excludedUsers;
        template.excludedGroups = excludedGroups;
        template.scope = scope;
        template.inputDataSchema = inputDataSchema;
        template.outputDataSchema = outputDataSchema;
        template.instanceCount = instanceCount;
        template.requiredCount = requiredCount;
        template.parentRole = parentRole;
        template.assignmentStrategy = assignmentStrategy;
        template.onThresholdReached = onThresholdReached;
        template.allowSameAssignee = allowSameAssignee;
        template.version = version;
        template.createdBy = createdBy;
        template.createdAt = createdAt;
        return template;
    }
}
