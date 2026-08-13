package io.casehub.work.api;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkItemBuilderTest {

    @Test
    void builderCreatesRecord() {
        UUID id = UUID.randomUUID();
        WorkItem item = WorkItem.builder()
                .id(id)
                .tenancyId("tenant-1")
                .title("Review contract")
                .status(WorkItemStatus.PENDING)
                .priority(WorkItemPriority.HIGH)
                .types(Set.of("legal", "compliance/audit"))
                .labels(List.of(new WorkItemLabel("urgent", LabelPersistence.MANUAL, "alice")))
                .build();

        assertEquals(id, item.id());
        assertEquals("tenant-1", item.tenancyId());
        assertEquals("Review contract", item.title());
        assertEquals(WorkItemStatus.PENDING, item.status());
        assertEquals(WorkItemPriority.HIGH, item.priority());
        assertEquals(Set.of("legal", "compliance/audit"), item.types());
        assertEquals(1, item.labels().size());
        assertEquals("urgent", item.labels().get(0).path());
    }

    @Test
    void toBuilderPreservesAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        WorkItem original = WorkItem.builder()
                .id(id)
                .tenancyId("t1")
                .title("Original")
                .status(WorkItemStatus.PENDING)
                .priority(WorkItemPriority.MEDIUM)
                .createdAt(now)
                .build();

        WorkItem modified = original.toBuilder()
                .status(WorkItemStatus.ASSIGNED)
                .assigneeId("alice")
                .build();

        assertEquals(id, modified.id());
        assertEquals("t1", modified.tenancyId());
        assertEquals("Original", modified.title());
        assertEquals(WorkItemStatus.ASSIGNED, modified.status());
        assertEquals("alice", modified.assigneeId());
        assertEquals(now, modified.createdAt());
    }

    @Test
    void allFieldsRoundTrip() {
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        Instant now = Instant.now();
        WorkItem original = WorkItem.builder()
                .id(id).tenancyId("t1").title("T").description("D").formKey("fk")
                .status(WorkItemStatus.IN_PROGRESS).priority(WorkItemPriority.URGENT)
                .assigneeId("a").owner("o").candidateGroups("g").candidateUsers("u")
                .requiredCapabilities("rc").createdBy("cb").delegationChain("dc")
                .delegationDeclineTarget(DeclineTarget.POOL).priorStatus(WorkItemStatus.ASSIGNED)
                .payload("p").resolution("r").claimDeadline(now).expiresAt(now)
                .followUpDate(now).createdAt(now).updatedAt(now).assignedAt(now)
                .startedAt(now).completedAt(now).suspendedAt(now)
                .accumulatedUnclaimedSeconds(99L).lastReturnedToPoolAt(now)
                .labels(List.of(new WorkItemLabel("x", LabelPersistence.MANUAL, "y")))
                .types(Set.of("t")).confidenceScore(0.9).callerRef("cr")
                .parentId(parentId).scope("s").templateId(templateId)
                .templateVersion(2L).permittedOutcomes("po").excludedUsers("eu")
                .outcome("oc").inputDataSchema("ids").outputDataSchema("ods")
                .payloadTypeName("ptn").resolutionTypeName("rtn")
                .candidateScores("cs").routingExperiences("re")
                .build();

        WorkItem roundTripped = original.toBuilder().build();
        assertEquals(original, roundTripped);
    }

    @Test
    void defaultsForCollections() {
        WorkItem item = WorkItem.builder().build();
        assertNotNull(item.labels());
        assertTrue(item.labels().isEmpty());
        assertNotNull(item.types());
        assertTrue(item.types().isEmpty());
    }
}
