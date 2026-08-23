package io.casehub.work.progress.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.*;
import io.casehub.work.progress.memory.InMemoryProgressEventStore;
import io.casehub.work.progress.memory.InMemoryProgressInstanceStore;
import io.casehub.work.progress.rollup.RollupEngine;
import io.casehub.work.progress.validation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubtreeRollbackServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemoryProgressInstanceStore instanceStore;
    private InMemoryProgressEventStore eventStore;
    private ProgressService progressService;
    private SubtreeRollbackService subtreeRollbackService;
    private List<ProgressUpdatedEvent> emittedEvents;

    @BeforeEach
    void setUp() {
        instanceStore = new InMemoryProgressInstanceStore();
        eventStore = new InMemoryProgressEventStore();
        emittedEvents = new ArrayList<>();

        progressService = new ProgressService(
                instanceStore, eventStore,
                List.of(new PercentageValidator(), new CountValidator(),
                        new StepShapeValidator(), new CustomShapeValidator()),
                new StepValidator(), new StepShapeValidator(),
                new RollbackDetector(), (expr, ctx) -> true,
                emittedEvents::add, new RollupEngine());

        subtreeRollbackService = new SubtreeRollbackService(
                progressService, instanceStore, eventStore);
    }

    @Test
    void rollbackSubtree_rollsBackLeafNodes() {
        ProgressInstance root = progressService.create(percentageRequest("root", 0));
        ProgressInstance child1 = progressService.attachChild(root.id(), percentageRequest("c1", 0));
        ProgressInstance child2 = progressService.attachChild(root.id(), percentageRequest("c2", 0));

        Instant targetTime = Instant.now();

        progressService.updateState(child1.id(), percentageState(50));
        progressService.updateState(child2.id(), percentageState(70));

        SubtreeRollbackResult result = subtreeRollbackService.rollbackSubtree(root.id(), targetTime);

        assertThat(result).isNotNull();
        assertThat(result.operationId()).isNotNull();

        long rolledBack = result.outcomes().stream()
                .filter(o -> o.outcome() == NodeRollbackOutcome.Outcome.ROLLED_BACK)
                .count();
        assertThat(rolledBack).isGreaterThanOrEqualTo(2);

        assertThat(instanceStore.get(child1.id()).get().state().get("value").asInt()).isEqualTo(0);
        assertThat(instanceStore.get(child2.id()).get().state().get("value").asInt()).isEqualTo(0);
    }

    @Test
    void rollbackSubtree_skipsPostTargetNodes() {
        ProgressInstance root = progressService.create(percentageRequest("root", 0));

        Instant targetTime = Instant.now();

        progressService.attachChild(root.id(), percentageRequest("c1", 50));

        SubtreeRollbackResult result = subtreeRollbackService.rollbackSubtree(root.id(), targetTime);

        long skipped = result.outcomes().stream()
                .filter(o -> o.outcome() == NodeRollbackOutcome.Outcome.SKIPPED)
                .filter(o -> o.reason() != null && o.reason().contains("created after target"))
                .count();
        assertThat(skipped).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rollbackSubtree_operationIdOnAllEvents() {
        ProgressInstance root = progressService.create(percentageRequest("root", 0));
        Instant targetTime = Instant.now();
        progressService.updateState(root.id(), percentageState(50));
        emittedEvents.clear();

        SubtreeRollbackResult result = subtreeRollbackService.rollbackSubtree(root.id(), targetTime);

        List<ProgressUpdatedEvent> rollbackEvents = emittedEvents.stream()
                .filter(e -> e.operationId() != null)
                .toList();
        assertThat(rollbackEvents).isNotEmpty();
        for (ProgressUpdatedEvent event : rollbackEvents) {
            assertThat(event.operationId()).isEqualTo(result.operationId());
        }
    }

    @Test
    void rollbackSubtreeToEvent_delegatesToTimestamp() {
        ProgressInstance root = progressService.create(percentageRequest("root", 0));
        progressService.updateState(root.id(), percentageState(50));

        List<ProgressUpdatedEvent> events = eventStore.findByProgressId(root.id());
        ProgressUpdatedEvent targetEvent = events.get(0);

        progressService.updateState(root.id(), percentageState(80));

        SubtreeRollbackResult result = subtreeRollbackService.rollbackSubtreeToEvent(root.id(), targetEvent.id());

        assertThat(result).isNotNull();
        assertThat(instanceStore.get(root.id()).get().state().get("value").asInt()).isEqualTo(0);
    }

    @Test
    void rollbackSubtree_policyBypassedFlagged() {
        ProgressInstance root = progressService.create(
                new ProgressCreateRequest("t1", "workitem", "root", "percentage", percentageState(0),
                        null, null, null, "denied", null));
        Instant targetTime = Instant.now();
        progressService.updateState(root.id(), percentageState(50));

        SubtreeRollbackResult result = subtreeRollbackService.rollbackSubtree(root.id(), targetTime);

        boolean anyBypassed = result.outcomes().stream()
                .filter(o -> o.outcome() == NodeRollbackOutcome.Outcome.ROLLED_BACK)
                .anyMatch(NodeRollbackOutcome::policyBypassed);
        assertThat(anyBypassed).isTrue();
    }

    @Test
    void rollbackSubtree_noOpWhenAlreadyAtTarget() {
        ProgressInstance root = progressService.create(percentageRequest("root", 0));
        Instant targetTime = Instant.now();

        SubtreeRollbackResult result = subtreeRollbackService.rollbackSubtree(root.id(), targetTime);

        long skipped = result.outcomes().stream()
                .filter(o -> o.outcome() == NodeRollbackOutcome.Outcome.SKIPPED)
                .filter(o -> "already at target state".equals(o.reason()))
                .count();
        assertThat(skipped).isEqualTo(1);
    }

    private ProgressCreateRequest percentageRequest(String scopeId, int value) {
        return new ProgressCreateRequest("t1", "workitem", scopeId, "percentage",
                percentageState(value), null, null, null, null, null);
    }

    private JsonNode percentageState(int value) {
        return MAPPER.createObjectNode().put("value", value);
    }
}
