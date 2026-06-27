package io.casehub.work.api;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkItemCreateRequestTest {

    @Test
    void builder_setsAllFields() {
        final Instant now = Instant.now();
        final UUID templateId = UUID.randomUUID();

        final io.casehub.work.api.WorkItemCreateRequest req = io.casehub.work.api.WorkItemCreateRequest.builder()
                                                                                                       .title("Review contract")
                                                                                                       .description("desc")
                                                                                                       .category("legal")
                                                                                                       .formKey("form-1")
                                                                                                       .priority(io.casehub.work.api.WorkItemPriority.HIGH)
                                                                                                       .assigneeId("alice")
                                                                                                       .candidateGroups("lawyers")
                                                                                                       .candidateUsers("bob")
                                                                                                       .requiredCapabilities("legal-review")
                                                                                                       .createdBy("system")
                                                                                                       .payload("{}")
                                                                                                       .claimDeadline(now)
                                                                                                       .expiresAt(now)
                                                                                                       .followUpDate(now)
                                                                                                       .labels(List.of())
                                                                                                       .confidenceScore(0.9)
                                                                                                       .callerRef("ref-1")
                                                                                                       .claimDeadlineBusinessHours(8)
                                                                                                       .expiresAtBusinessHours(40)
                                                                                                       .templateId(templateId)
                                                                                                       .permittedOutcomes(List.of(new Outcome("approved", "Approved", null), new Outcome("rejected", "Rejected", null)))
                                                                                                       .inputDataSchema("{\"type\":\"object\"}")
                                                                                                       .outputDataSchema("{\"type\":\"object\"}")
                                                                                                       .excludedUsers("charlie")
                                                                                                       .tenancyId("tenant-1")
                                                                                                       .build();

        assertEquals("Review contract", req.title);
        assertEquals("desc", req.description);
        assertEquals("legal", req.category);
        assertEquals("form-1", req.formKey);
        assertEquals(io.casehub.work.api.WorkItemPriority.HIGH, req.priority);
        assertEquals("alice", req.assigneeId);
        assertEquals("lawyers", req.candidateGroups);
        assertEquals("bob", req.candidateUsers);
        assertEquals("legal-review", req.requiredCapabilities);
        assertEquals("system", req.createdBy);
        assertEquals("{}", req.payload);
        assertEquals(now, req.claimDeadline);
        assertEquals(now, req.expiresAt);
        assertEquals(now, req.followUpDate);
        assertEquals(List.of(), req.labels);
        assertEquals(0.9, req.confidenceScore);
        assertEquals("ref-1", req.callerRef);
        assertEquals(8, req.claimDeadlineBusinessHours);
        assertEquals(40, req.expiresAtBusinessHours);
        assertEquals(templateId, req.templateId);
        assertEquals(List.of(new Outcome("approved", "Approved", null), new Outcome("rejected", "Rejected", null)), req.permittedOutcomes);
        assertEquals("{\"type\":\"object\"}", req.inputDataSchema);
        assertEquals("{\"type\":\"object\"}", req.outputDataSchema);
        assertEquals("charlie", req.excludedUsers);
        assertEquals("tenant-1", req.tenancyId);
    }

    @Test
    void builder_defaultsAllOptionalFieldsToNull() {
        final io.casehub.work.api.WorkItemCreateRequest req = io.casehub.work.api.WorkItemCreateRequest.builder()
                                                                                                       .title("Minimal")
                                                                                                       .build();

        assertEquals("Minimal", req.title);
        assertNull(req.description);
        assertNull(req.category);
        assertNull(req.formKey);
        assertNull(req.priority);
        assertNull(req.assigneeId);
        assertNull(req.candidateGroups);
        assertNull(req.candidateUsers);
        assertNull(req.requiredCapabilities);
        assertNull(req.createdBy);
        assertNull(req.payload);
        assertNull(req.claimDeadline);
        assertNull(req.expiresAt);
        assertNull(req.followUpDate);
        assertNull(req.labels);
        assertNull(req.confidenceScore);
        assertNull(req.callerRef);
        assertNull(req.claimDeadlineBusinessHours);
        assertNull(req.expiresAtBusinessHours);
        assertNull(req.templateId);
        assertNull(req.permittedOutcomes);
        assertNull(req.inputDataSchema);
        assertNull(req.outputDataSchema);
        assertNull(req.excludedUsers);
        assertNull(req.tenancyId);
    }

    @Test
    void build_rejectsNullTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> io.casehub.work.api.WorkItemCreateRequest.builder().build());
    }

    @Test
    void build_rejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> io.casehub.work.api.WorkItemCreateRequest.builder().title("").build());
        assertThrows(IllegalArgumentException.class,
                () -> io.casehub.work.api.WorkItemCreateRequest.builder().title("  ").build());
    }

    @Test
    void toBuilder_copiesAllFields() {
        final Instant now = Instant.now();
        final UUID templateId = UUID.randomUUID();

        final io.casehub.work.api.WorkItemCreateRequest original = io.casehub.work.api.WorkItemCreateRequest.builder()
                                                                                                            .title("original")
                                                                                                            .description("desc")
                                                                                                            .category("legal")
                                                                                                            .formKey("form-1")
                                                                                                            .priority(io.casehub.work.api.WorkItemPriority.HIGH)
                                                                                                            .assigneeId("alice")
                                                                                                            .candidateGroups("lawyers")
                                                                                                            .candidateUsers("bob")
                                                                                                            .requiredCapabilities("legal-review")
                                                                                                            .createdBy("system")
                                                                                                            .payload("{}")
                                                                                                            .claimDeadline(now)
                                                                                                            .expiresAt(now)
                                                                                                            .followUpDate(now)
                                                                                                            .labels(List.of())
                                                                                                            .confidenceScore(0.9)
                                                                                                            .callerRef("ref-1")
                                                                                                            .claimDeadlineBusinessHours(8)
                                                                                                            .expiresAtBusinessHours(40)
                                                                                                            .templateId(templateId)
                                                                                                            .permittedOutcomes(List.of(new Outcome("approved", null, null)))
                                                                                                            .inputDataSchema("{\"type\":\"object\"}")
                                                                                                            .outputDataSchema("{\"type\":\"object\"}")
                                                                                                            .excludedUsers("charlie")
                                                                                                            .tenancyId("tenant-1")
                                                                                                            .build();

        final io.casehub.work.api.WorkItemCreateRequest copy = original.toBuilder().title("copy").build();

        assertEquals("copy", copy.title);
        assertEquals("desc", copy.description);
        assertEquals("legal", copy.category);
        assertEquals("form-1", copy.formKey);
        assertEquals(io.casehub.work.api.WorkItemPriority.HIGH, copy.priority);
        assertEquals("alice", copy.assigneeId);
        assertEquals("lawyers", copy.candidateGroups);
        assertEquals("bob", copy.candidateUsers);
        assertEquals("legal-review", copy.requiredCapabilities);
        assertEquals("system", copy.createdBy);
        assertEquals("{}", copy.payload);
        assertEquals(now, copy.claimDeadline);
        assertEquals(now, copy.expiresAt);
        assertEquals(now, copy.followUpDate);
        assertEquals(List.of(), copy.labels);
        assertEquals(0.9, copy.confidenceScore);
        assertEquals("ref-1", copy.callerRef);
        assertEquals(8, copy.claimDeadlineBusinessHours);
        assertEquals(40, copy.expiresAtBusinessHours);
        assertEquals(templateId, copy.templateId);
        assertEquals(List.of(new Outcome("approved", null, null)), copy.permittedOutcomes);
        assertEquals("{\"type\":\"object\"}", copy.inputDataSchema);
        assertEquals("{\"type\":\"object\"}", copy.outputDataSchema);
        assertEquals("charlie", copy.excludedUsers);
        assertEquals("tenant-1", copy.tenancyId);
    }

    @Test
    void toBuilder_doesNotMutateOriginal() {
        final io.casehub.work.api.WorkItemCreateRequest original = io.casehub.work.api.WorkItemCreateRequest.builder()
                                                                                                            .title("original")
                                                                                                            .build();

        original.toBuilder().title("mutated").build();

        assertEquals("original", original.title);
    }

    @Test
    void equals_andHashCode_areConsistent() {
        final io.casehub.work.api.WorkItemCreateRequest a = io.casehub.work.api.WorkItemCreateRequest.builder().title("x").category("y").build();
        final io.casehub.work.api.WorkItemCreateRequest b = io.casehub.work.api.WorkItemCreateRequest.builder().title("x").category("y").build();
        final io.casehub.work.api.WorkItemCreateRequest c = io.casehub.work.api.WorkItemCreateRequest.builder().title("x").category("z").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void builderHasSetterForEveryField() {
        final long fieldCount = Arrays.stream(io.casehub.work.api.WorkItemCreateRequest.class.getFields())
                .filter(f -> Modifier.isFinal(f.getModifiers()))
                .count();

        final long setterCount = Arrays.stream(io.casehub.work.api.WorkItemCreateRequest.Builder.class.getDeclaredMethods())
                .filter(m -> m.getReturnType() == io.casehub.work.api.WorkItemCreateRequest.Builder.class)
                .count();

        assertEquals(fieldCount, setterCount,
                "WorkItemCreateRequest.Builder is missing a setter — add one for every public final field");
    }
}
