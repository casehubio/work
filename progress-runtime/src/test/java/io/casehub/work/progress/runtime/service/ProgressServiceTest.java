package io.casehub.work.progress.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.progress.ConditionEvaluator;
import io.casehub.work.progress.ProgressChangeType;
import io.casehub.work.progress.ProgressCreateRequest;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressSnapshot;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.memory.InMemoryProgressEventStore;
import io.casehub.work.progress.memory.InMemoryProgressInstanceStore;
import io.casehub.work.progress.validation.CountValidator;
import io.casehub.work.progress.validation.CustomShapeValidator;
import io.casehub.work.progress.validation.PercentageValidator;
import io.casehub.work.progress.validation.RollbackDetector;
import io.casehub.work.progress.validation.ShapeValidator;
import io.casehub.work.progress.validation.StepShapeValidator;
import io.casehub.work.progress.validation.StepValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private InMemoryProgressInstanceStore instanceStore;
    private InMemoryProgressEventStore eventStore;
    private List<ProgressUpdatedEvent> emittedEvents;
    private ProgressService service;

    @BeforeEach
    void setUp() {
        instanceStore = new InMemoryProgressInstanceStore();
        eventStore    = new InMemoryProgressEventStore();
        emittedEvents = new ArrayList<>();

        List<ShapeValidator> validators = List.of(
                new PercentageValidator(), new CountValidator(),
                new StepShapeValidator(), new CustomShapeValidator());
        ConditionEvaluator conditionEvaluator = (expr, ctx) -> true;

        service = new ProgressService(
                instanceStore, eventStore, validators,
                new StepValidator(), new StepShapeValidator(),
                new RollbackDetector(), conditionEvaluator,
                emittedEvents::add);}

    private ProgressCreateRequest percentageRequest(int value) {
        return new ProgressCreateRequest("tenant1", "workitem", UUID.randomUUID().toString(),
                                         "percentage", mapper.createObjectNode().put("value", value),
                                         null, null, null, null, null);}

    private ProgressCreateRequest countRequest(int current, int total) {
        return new ProgressCreateRequest("tenant1", "workitem", UUID.randomUUID().toString(),
                                         "count", mapper.createObjectNode().put("current", current).put("total", total),
                                         null, null, null, null, null);}

    private ProgressCreateRequest stepRequest() {
        ArrayNode def = mapper.createArrayNode();
        def.addObject().put("name", "a").put("optional", false).putArray("dependsOn");
        def.addObject().put("name", "b").put("optional", false).putArray("dependsOn").add("a");
        def.addObject().put("name", "c").put("optional", true).putArray("dependsOn").add("b");

        ObjectNode state = mapper.createObjectNode();
        ObjectNode steps = state.putObject("steps");
        steps.putObject("a").put("status", "pending");
        steps.putObject("b").put("status", "pending");
        steps.putObject("c").put("status", "pending");

        return new ProgressCreateRequest("tenant1", "workitem", UUID.randomUUID().toString(),
                                         "step", state, null, null, def, null, null);}

    // --- CREATE ---

    @Test
    void create_percentageInstance() {
        ProgressInstance inst = service.create(percentageRequest(0));
        assertThat(inst.status()).isEqualTo(ProgressStatus.PENDING);
        assertThat(inst.shapeType()).isEqualTo("percentage");
        assertThat(inst.rootProgressId()).isEqualTo(inst.id());
        assertThat(emittedEvents).hasSize(1);
        assertThat(emittedEvents.get(0).changeType()).isEqualTo(ProgressChangeType.CREATED);
    }

    @Test
    void create_childInheritsRootProgressId() {
        ProgressInstance parent = service.create(percentageRequest(0));
        ProgressCreateRequest childReq = new ProgressCreateRequest("tenant1", "workitem",
                                                                   UUID.randomUUID().toString(), "percentage",
                                                                   mapper.createObjectNode().put("value", 0),
                                                                   parent.id(), null, null, null, null);
        ProgressInstance child = service.create(childReq);
        assertThat(child.parentProgressId()).isEqualTo(parent.id());
        assertThat(child.rootProgressId()).isEqualTo(parent.rootProgressId());}

    @Test
    void create_stepWithoutDefinitionRejects() {
        ObjectNode state = mapper.createObjectNode();
        state.putObject("steps").putObject("a").put("status", "pending");
        ProgressCreateRequest req = new ProgressCreateRequest("tenant1", "workitem",
                                                              UUID.randomUUID().toString(), "step", state, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class);}

    @Test
    void create_invalidShapeRejects() {
        ProgressCreateRequest req = new ProgressCreateRequest("tenant1", "workitem",
                                                              UUID.randomUUID().toString(), "percentage",
                                                              mapper.createObjectNode().put("value", 150),
                                                              null, null, null, null, null);
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class);}

    // --- UPDATE STATE ---

    @Test
    void updateState_pendingToActiveOnFirstUpdate() {
        ProgressInstance inst = service.create(percentageRequest(0));
        assertThat(inst.status()).isEqualTo(ProgressStatus.PENDING);

        ProgressInstance updated = service.updateState(inst.id(),
                mapper.createObjectNode().put("value", 25));
        assertThat(updated.status()).isEqualTo(ProgressStatus.ACTIVE);
        assertThat(emittedEvents).hasSize(2);
        assertThat(emittedEvents.get(1).changeType()).isEqualTo(ProgressChangeType.STATE_UPDATED);
    }

    @Test
    void updateState_validatesShape() {
        ProgressInstance inst = service.create(percentageRequest(0));
        assertThatThrownBy(() -> service.updateState(inst.id(),
                mapper.createObjectNode().put("value", 150)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateState_rollbackDetected() {
        ProgressInstance inst = service.create(percentageRequest(0));
        service.updateState(inst.id(), mapper.createObjectNode().put("value", 80));
        emittedEvents.clear();

        service.updateState(inst.id(), mapper.createObjectNode().put("value", 60));
        assertThat(emittedEvents).hasSize(1);
        assertThat(emittedEvents.get(0).changeType()).isEqualTo(ProgressChangeType.ROLLED_BACK);
    }

    // --- COMPLETE / FAIL / REACTIVATE ---

    @Test
    void complete_activeInstance() {
        ProgressInstance inst = service.create(percentageRequest(0));
        service.updateState(inst.id(), mapper.createObjectNode().put("value", 100));
        ProgressInstance completed = service.complete(inst.id());
        assertThat(completed.status()).isEqualTo(ProgressStatus.COMPLETED);
    }

    @Test
    void complete_pendingInstance() {
        ProgressInstance inst = service.create(percentageRequest(0));
        ProgressInstance completed = service.complete(inst.id());
        assertThat(completed.status()).isEqualTo(ProgressStatus.COMPLETED);
    }

    @Test
    void fail_activeInstance() {
        ProgressInstance inst = service.create(percentageRequest(0));
        service.updateState(inst.id(), mapper.createObjectNode().put("value", 50));
        ProgressInstance failed = service.fail(inst.id());
        assertThat(failed.status()).isEqualTo(ProgressStatus.FAILED);
    }

    @Test
    void reactivate_completedInstance() {
        ProgressInstance inst = service.create(percentageRequest(0));
        service.complete(inst.id());
        ProgressInstance reactivated = service.reactivate(inst.id());
        assertThat(reactivated.status()).isEqualTo(ProgressStatus.ACTIVE);
    }

    @Test
    void reactivate_activeRejects() {
        ProgressInstance inst = service.create(percentageRequest(0));
        service.updateState(inst.id(), mapper.createObjectNode().put("value", 50));
        assertThatThrownBy(() -> service.reactivate(inst.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- ATTACH CHILD ---

    @Test
    void attachChild_createsChildAndEmitsParentEvent() {
        ProgressInstance parent = service.create(countRequest(0, 3));
        emittedEvents.clear();

        ProgressCreateRequest childReq = new ProgressCreateRequest("tenant1", "workitem",
                                                                   UUID.randomUUID().toString(), "percentage",
                                                                   mapper.createObjectNode().put("value", 0),
                                                                   null, null, null, null, null);
        ProgressInstance child = service.attachChild(parent.id(), childReq);

        assertThat(child.parentProgressId()).isEqualTo(parent.id());
        assertThat(child.rootProgressId()).isEqualTo(parent.rootProgressId());
        assertThat(emittedEvents).hasSize(2);
        assertThat(emittedEvents.get(0).changeType()).isEqualTo(ProgressChangeType.CREATED);
        assertThat(emittedEvents.get(1).changeType()).isEqualTo(ProgressChangeType.CHILD_ATTACHED);
        assertThat(emittedEvents.get(1).progressId()).isEqualTo(parent.id());}

    // --- QUERIES ---

    @Test
    void findById_existing() {
        ProgressInstance inst = service.create(percentageRequest(50));
        assertThat(service.findById(inst.id())).isPresent();
    }

    @Test
    void findById_missing() {
        assertThat(service.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByScope_orderedByCreatedAtDesc() {
        String scopeId = UUID.randomUUID().toString();
        ProgressCreateRequest req1 = new ProgressCreateRequest("tenant1", "workitem", scopeId,
                                                               "percentage", mapper.createObjectNode().put("value", 10),
                                                               null, null, null, null, null);
        ProgressCreateRequest req2 = new ProgressCreateRequest("tenant1", "workitem", scopeId,
                                                               "percentage", mapper.createObjectNode().put("value", 20),
                                                               null, null, null, null, null);
        ProgressInstance first  = service.create(req1);
        ProgressInstance second = service.create(req2);

        List<ProgressInstance> results = service.findByScope("workitem", scopeId);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(second.id());}

    // --- STEP OPERATIONS ---

    @Test
    void startStep_rootStepActivates() {
        ProgressInstance inst = service.create(stepRequest());
        ProgressInstance updated = service.startStep(inst.id(), "a");

        JsonNode stepState = updated.state().get("steps").get("a");
        assertThat(stepState.get("status").asText()).isEqualTo("active");
        assertThat(updated.status()).isEqualTo(ProgressStatus.ACTIVE);
    }

    @Test
    void startStep_depNotMetRejects() {
        ProgressInstance inst = service.create(stepRequest());
        assertThatThrownBy(() -> service.startStep(inst.id(), "b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completeStep_activateAndComplete() {
        ProgressInstance inst = service.create(stepRequest());
        service.startStep(inst.id(), "a");
        ProgressInstance completed = service.completeStep(inst.id(), "a");

        JsonNode stepState = completed.state().get("steps").get("a");
        assertThat(stepState.get("status").asText()).isEqualTo("completed");
    }

    @Test
    void skipStep_optionalStepPasses() {
        ArrayNode def = mapper.createArrayNode();
        def.addObject().put("name", "a").put("optional", false).putArray("dependsOn");
        def.addObject().put("name", "b").put("optional", true).putArray("dependsOn");

        ObjectNode state = mapper.createObjectNode();
        ObjectNode steps = state.putObject("steps");
        steps.putObject("a").put("status", "pending");
        steps.putObject("b").put("status", "pending");

        ProgressCreateRequest req = new ProgressCreateRequest("tenant1", "workitem",
                                                              UUID.randomUUID().toString(), "step", state, null, null, def, null, null);
        ProgressInstance inst = service.create(req);

        service.startStep(inst.id(), "b");
        ProgressInstance skipped = service.skipStep(inst.id(), "b");

        JsonNode stepState = skipped.state().get("steps").get("b");
        assertThat(stepState.get("status").asText()).isEqualTo("skipped");}

    @Test
    void skipStep_nonOptionalRejects() {
        ProgressInstance inst = service.create(stepRequest());
        service.startStep(inst.id(), "a");
        assertThatThrownBy(() -> service.skipStep(inst.id(), "a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void derivedCompletion_allRequiredDone() {
        ProgressInstance inst = service.create(stepRequest());
        service.startStep(inst.id(), "a");
        service.completeStep(inst.id(), "a");
        service.startStep(inst.id(), "b");
        ProgressInstance result = service.completeStep(inst.id(), "b");

        assertThat(result.status()).isEqualTo(ProgressStatus.COMPLETED);
    }

    @Test
    void derivedCompletion_optionalPendingStillFires() {
        ProgressInstance inst = service.create(stepRequest());
        service.startStep(inst.id(), "a");
        service.completeStep(inst.id(), "a");
        service.startStep(inst.id(), "b");
        ProgressInstance result = service.completeStep(inst.id(), "b");

        assertThat(result.status()).isEqualTo(ProgressStatus.COMPLETED);
        JsonNode cStep = result.state().get("steps").get("c");
        assertThat(cStep.get("status").asText()).isEqualTo("pending");
    }

    @Test
    void failStep_instanceRemainsActive() {
        ProgressInstance inst = service.create(stepRequest());
        service.startStep(inst.id(), "a");
        ProgressInstance result = service.failStep(inst.id(), "a");

        JsonNode stepState = result.state().get("steps").get("a");
        assertThat(stepState.get("status").asText()).isEqualTo("failed");
        assertThat(result.status()).isEqualTo(ProgressStatus.ACTIVE);
    }

    @Test
    void updateStepState_setsDataField() {
        ProgressInstance inst = service.create(stepRequest());
        service.startStep(inst.id(), "a");
        ProgressInstance updated = service.updateStepState(inst.id(), "a",
                mapper.createObjectNode().put("notes", "halfway done"));

        JsonNode stepState = updated.state().get("steps").get("a");
        assertThat(stepState.get("data").get("notes").asText()).isEqualTo("halfway done");
    }

// --- VISUALISATION MODES ---

    @Test
    void create_withVisualisationMode_persists() {
        ObjectNode state = mapper.createObjectNode().put("value", 50);
        ProgressCreateRequest request = new ProgressCreateRequest(
                "tenant1", "workitem", "wi-1", "percentage", state,
                null, null, null, null, "gauge");
        ProgressInstance instance = service.create(request);
        assertThat(instance.visualisationMode()).isEqualTo("gauge");
    }

    @Test
    void create_nullVisualisationMode_accepted() {
        ProgressInstance instance = service.create(percentageRequest(50));
        assertThat(instance.visualisationMode()).isNull();
    }

    @Test
    void create_arbitraryVisualisationMode_accepted() {
        ObjectNode state = mapper.createObjectNode().put("value", 50);
        ProgressCreateRequest request = new ProgressCreateRequest(
                "tenant1", "workitem", "wi-1", "percentage", state,
                null, null, null, null, "gantt");
        ProgressInstance instance = service.create(request);
        assertThat(instance.visualisationMode()).isEqualTo("gantt");
    }

    @Test
    void updateState_preservesVisualisationMode() {
        ObjectNode state = mapper.createObjectNode().put("value", 50);
        ProgressCreateRequest request = new ProgressCreateRequest(
                "tenant1", "workitem", "wi-1", "percentage", state,
                null, null, null, null, "timeline");
        ProgressInstance instance = service.create(request);

        ObjectNode       newState = mapper.createObjectNode().put("value", 75);
        ProgressInstance updated  = service.updateState(instance.id(), newState);
        assertThat(updated.visualisationMode()).isEqualTo("timeline");
    }

    // --- ROLLBACK CONTROLS ---

    @Test
    void updateState_rollbackPolicyDenied_blocksBackwardUpdate() {
        ObjectNode state = mapper.createObjectNode().put("value", 80);
        ProgressCreateRequest request = new ProgressCreateRequest(
                "tenant1", "workitem", "wi-1", "percentage", state,
                null, null, null, "denied", null);
        ProgressInstance instance = service.create(request);

        ObjectNode backward = mapper.createObjectNode().put("value", 60);
        assertThatThrownBy(() -> service.updateState(instance.id(), backward))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("denied");
    }

    @Test
    void updateState_rollbackPolicyDenied_allowsForwardUpdate() {
        ObjectNode state = mapper.createObjectNode().put("value", 60);
        ProgressCreateRequest request = new ProgressCreateRequest(
                "tenant1", "workitem", "wi-1", "percentage", state,
                null, null, null, "denied", null);
        ProgressInstance instance = service.create(request);

        ObjectNode forward = mapper.createObjectNode().put("value", 80);
        ProgressInstance updated2 = service.updateState(instance.id(), forward);
        assertThat(updated2.state().get("value").asInt()).isEqualTo(80);
    }

    @Test
    void rollback_restoresPreviousState() {
        ProgressInstance instance = service.create(percentageRequest(50));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 70));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 90));

        ProgressInstance rolledBack = service.rollback(instance.id());
        assertThat(rolledBack.state().get("value").asInt()).isEqualTo(70);
    }

    @Test
    void rollback_consecutiveCallsGoFurtherBack() {
        ProgressInstance instance = service.create(percentageRequest(10));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 50));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 90));

        service.rollback(instance.id());
        ProgressInstance second = service.rollback(instance.id());
        assertThat(second.state().get("value").asInt()).isEqualTo(10);
    }

    @Test
    void rollback_bypassesDeniedPolicy() {
        ObjectNode state = mapper.createObjectNode().put("value", 50);
        ProgressCreateRequest request = new ProgressCreateRequest(
                "tenant1", "workitem", "wi-1", "percentage", state,
                null, null, null, "denied", null);
        ProgressInstance instance = service.create(request);
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 80));

        ProgressInstance rolledBack = service.rollback(instance.id());
        assertThat(rolledBack.state().get("value").asInt()).isEqualTo(50);
    }

    @Test
    void rollback_emitsRolledBackEvenWhenForward() {
        ProgressInstance instance = service.create(percentageRequest(50));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 80));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 60));

        emittedEvents.clear();
        service.rollback(instance.id());

        assertThat(emittedEvents).hasSize(1);
        assertThat(emittedEvents.get(0).changeType()).isEqualTo(ProgressChangeType.ROLLED_BACK);
        assertThat(emittedEvents.get(0).currentState().get("value").asInt()).isEqualTo(80);
    }

    @Test
    void rollback_noPreviousStateThrows() {
        ProgressInstance instance = service.create(percentageRequest(50));
        assertThatThrownBy(() -> service.rollback(instance.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No previous state");
    }

    @Test
    void rollbackToEvent_restoresStateAtEvent() {
        ProgressInstance instance = service.create(percentageRequest(10));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 50));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 90));

        List<ProgressUpdatedEvent> events = eventStore.findByProgressId(instance.id());
        UUID secondEventId = events.get(1).id();

        ProgressInstance restored = service.rollbackToEvent(instance.id(), secondEventId);
        assertThat(restored.state().get("value").asInt()).isEqualTo(50);
    }

    @Test
    void getSnapshots_returnsOrderedHistory() {
        ProgressInstance instance = service.create(percentageRequest(10));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 50));
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 90));

        List<ProgressSnapshot> snapshots = service.getSnapshots(instance.id(), 100);
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(0).state().get("value").asInt()).isEqualTo(10);
        assertThat(snapshots.get(1).state().get("value").asInt()).isEqualTo(50);
        assertThat(snapshots.get(2).state().get("value").asInt()).isEqualTo(90);
        assertThat(snapshots.get(0).eventId()).isNotNull();
    }

    @Test
    void getSnapshots_respectsLimit() {
        ProgressInstance instance = service.create(percentageRequest(10));
        for (int i = 20; i <= 100; i += 10) {
            service.updateState(instance.id(), mapper.createObjectNode().put("value", i));
        }

        List<ProgressSnapshot> snapshots = service.getSnapshots(instance.id(), 3);
        assertThat(snapshots).hasSize(3);
    }

    @Test
    void reactivation_independentOfRollbackPolicy() {
        ObjectNode state = mapper.createObjectNode().put("value", 100);
        ProgressCreateRequest request = new ProgressCreateRequest(
                "tenant1", "workitem", "wi-1", "percentage", state,
                null, null, null, "denied", null);
        ProgressInstance instance = service.create(request);
        service.updateState(instance.id(), mapper.createObjectNode().put("value", 100));
        service.complete(instance.id());

        ProgressInstance reactivated = service.reactivate(instance.id());
        assertThat(reactivated.status()).isEqualTo(ProgressStatus.ACTIVE);
    }
}
