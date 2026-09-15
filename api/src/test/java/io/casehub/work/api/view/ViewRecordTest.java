package io.casehub.work.api.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.CompensationStatus;
import io.casehub.work.api.DeclineTarget;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.api.Outcome;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;

class ViewRecordTest {

    @Test
    void workItemViewHasSameFieldsAsWorkItemResponse() {
        Set<String> expected = Set.of(
                "id", "title", "description", "types", "formKey",
                "status", "priority", "assigneeId", "owner",
                "candidateGroups", "candidateUsers", "requiredCapabilities",
                "createdBy", "delegationDeclineTarget", "delegationChain",
                "priorStatus", "payload", "resolution",
                "claimDeadline", "expiresAt", "followUpDate",
                "createdAt", "updatedAt", "assignedAt", "startedAt",
                "completedAt", "suspendedAt", "labels",
                "confidenceScore", "callerRef", "version",
                "templateId", "templateVersion", "outcome",
                "permittedOutcomes", "inputDataSchema", "outputDataSchema",
                "excludedUsers", "scope", "candidateScores",
                "routingExperiences", "compensationStatus", "compensatesWorkItemId");

        Set<String> actual = Arrays.stream(WorkItemView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    @Test
    void workItemViewConstruction() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        var label = new WorkItemLabelView("env/prod", LabelPersistence.MANUAL, "admin");
        var view = new WorkItemView(
                id, "Test", "desc", List.of("type1"), "form1",
                WorkItemStatus.PENDING, WorkItemPriority.HIGH,
                "user1", "owner1", "group1", "user2", "cap1",
                "creator", DeclineTarget.POOL, "chain",
                WorkItemStatus.PENDING, "payload", "resolution",
                now, now, now, now, now, now, now, now, now,
                List.of(label), 0.95, "ref1", 1L,
                UUID.randomUUID(), 2L, "approved",
                List.of(new Outcome("ok", "OK", null)),
                "inputSchema", "outputSchema",
                "excluded", "scope/path", "scores", "experiences",
                CompensationStatus.NONE, null);

        assertEquals(id, view.id());
        assertEquals("Test", view.title());
        assertEquals(WorkItemStatus.PENDING, view.status());
        assertEquals(1, view.labels().size());
        assertEquals("env/prod", view.labels().get(0).path());
        assertEquals(0.95, view.confidenceScore());
    }

    @Test
    void workItemWithAuditViewExtendsWorkItemViewFields() {
        Set<String> viewFields = Arrays.stream(WorkItemView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        Set<String> auditViewFields = Arrays.stream(WorkItemWithAuditView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertTrue(auditViewFields.containsAll(viewFields),
                "WorkItemWithAuditView must contain all WorkItemView fields");
        assertTrue(auditViewFields.contains("auditTrail"),
                "WorkItemWithAuditView must have auditTrail field");
        assertEquals(viewFields.size() + 1, auditViewFields.size(),
                "WorkItemWithAuditView should have exactly one extra field (auditTrail)");
    }

    @Test
    void workItemPagePagination() {
        var page = new WorkItemPage(List.of(), 0, false);
        assertEquals(0, page.totalCount());
        assertFalse(page.hasMore());
        assertTrue(page.items().isEmpty());
    }

    @Test
    void noteViewFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        var note = new WorkItemNoteView(id, "author1", "content", now, null);
        assertEquals(id, note.id());
        assertEquals("author1", note.author());
        assertEquals("content", note.content());
        assertEquals(now, note.createdAt());
        assertNull(note.editedAt());
    }

    @Test
    void linkViewFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        var link = new WorkItemLinkView(id, "https://example.com", "Example",
                "ATTACHMENT", "user1", now);
        assertEquals(id, link.id());
        assertEquals("https://example.com", link.url());
        assertEquals("ATTACHMENT", link.relationType());
    }

    @Test
    void relationViewFields() {
        UUID id = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant now = Instant.now();
        var rel = new WorkItemRelationView(id, sourceId, targetId,
                "PART_OF", "creator", now);
        assertEquals(sourceId, rel.sourceId());
        assertEquals(targetId, rel.targetId());
        assertEquals("PART_OF", rel.relationType());
    }

    @Test
    void auditEntryViewFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        var entry = new AuditEntryView(id, "CLAIMED", "actor1", "detail", now);
        assertEquals("CLAIMED", entry.event());
        assertEquals("actor1", entry.actor());
    }

    @Test
    void labelViewFields() {
        var label = new WorkItemLabelView("env/prod", LabelPersistence.MANUAL, "admin");
        assertEquals("env/prod", label.path());
        assertEquals(LabelPersistence.MANUAL, label.persistence());
        assertEquals("admin", label.appliedBy());
    }

    @Test
    void lifecycleRequestRecords() {
        var complete = new CompleteRequest("resolved", "approved");
        assertEquals("resolved", complete.resolution());
        assertEquals("approved", complete.outcome());

        var reject = new RejectRequest("bad input", "rejected");
        assertEquals("bad input", reject.reason());

        var cancel = new CancelRequest("no longer needed");
        assertEquals("no longer needed", cancel.reason());

        var delegate = new DelegateRequest("user2", DeclineTarget.POOL);
        assertEquals("user2", delegate.to());

        var suspend = new SuspendRequest("waiting for info");
        assertEquals("waiting for info", suspend.reason());

        var fault = new FaultRequest("system", "timeout");
        assertEquals("system", fault.actor());
        assertEquals("timeout", fault.errorDetail());

        var obsolete = new ObsoleteRequest("system", "superseded");
        assertEquals("system", obsolete.actor());

        var escalate = new EscalateRequest("managers", "urgent");
        assertEquals("managers", escalate.targetGroup());

        var extend = new ExtendRequest(Instant.parse("2026-12-31T00:00:00Z"));
        assertNotNull(extend.newExpiresAt());

        var deadline = new UpdateDeadlineRequest(Instant.parse("2026-12-31T00:00:00Z"));
        assertNotNull(deadline.newDeadline());

        var compensate = new CompensateRequest("Comp task", "group1", "actor1", "fix error");
        assertEquals("Comp task", compensate.title());
        assertEquals("group1", compensate.candidateGroups());
    }

    @Test
    void addNoteRequest() {
        var req = new AddNoteRequest("note content");
        assertEquals("note content", req.content());
    }

    @Test
    void addLinkRequest() {
        var req = new AddLinkRequest("https://example.com", "Example", "REFERENCE");
        assertEquals("https://example.com", req.url());
        assertEquals("REFERENCE", req.relationType());
    }

    @Test
    void addRelationRequest() {
        UUID targetId = UUID.randomUUID();
        var req = new AddRelationRequest(targetId, "BLOCKS");
        assertEquals(targetId, req.targetId());
        assertEquals("BLOCKS", req.relationType());
    }
}
