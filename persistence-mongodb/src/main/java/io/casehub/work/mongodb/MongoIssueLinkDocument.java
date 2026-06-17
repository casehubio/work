package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link WorkItemIssueLink}.
 *
 * <p>
 * Stored in the {@code work_item_issue_links} collection. Converted to and from the domain
 * {@link WorkItemIssueLink} by {@link MongoIssueLinkStore}.
 */
@MongoEntity(collection = "work_item_issue_links")
public class MongoIssueLinkDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String workItemId;
    public String trackerType;
    public String externalRef;
    public String title;
    public String url;
    public String status;
    public Instant linkedAt;
    public String linkedBy;

    /** Convert a domain {@link WorkItemIssueLink} to a MongoDB document. */
    public static MongoIssueLinkDocument from(final WorkItemIssueLink link) {
        final MongoIssueLinkDocument doc = new MongoIssueLinkDocument();
        doc.id = link.id != null ? link.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = link.tenancyId;
        doc.workItemId = link.workItemId != null ? link.workItemId.toString() : null;
        doc.trackerType = link.trackerType;
        doc.externalRef = link.externalRef;
        doc.title = link.title;
        doc.url = link.url;
        doc.status = link.status;
        doc.linkedAt = link.linkedAt;
        doc.linkedBy = link.linkedBy;
        return doc;
    }

    /** Convert this document back to a domain {@link WorkItemIssueLink}. */
    public WorkItemIssueLink toDomain() {
        final WorkItemIssueLink link = new WorkItemIssueLink();
        link.id = UUID.fromString(id);
        link.tenancyId = tenancyId;
        link.workItemId = workItemId != null ? UUID.fromString(workItemId) : null;
        link.trackerType = trackerType;
        link.externalRef = externalRef;
        link.title = title;
        link.url = url;
        link.status = status;
        link.linkedAt = linkedAt;
        link.linkedBy = linkedBy;
        return link;
    }
}
