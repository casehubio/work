package io.casehub.work.progress.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.progress.ConditionEvaluator;
import io.casehub.work.progress.ProgressChangeType;
import io.casehub.work.progress.ProgressCreateRequest;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressSnapshot;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.StepDefinition;
import io.casehub.work.progress.StepStatus;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import io.casehub.work.progress.validation.RollbackDetector;
import io.casehub.work.progress.validation.ShapeValidator;
import io.casehub.work.progress.validation.StepShapeValidator;
import io.casehub.work.progress.validation.StepValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProgressService {

    private final ProgressInstanceStore instanceStore;
    private final ProgressEventStore eventStore;
    private final Map<String, ShapeValidator> validators;
    private final StepValidator stepValidator;
    private final StepShapeValidator stepShapeValidator;
    private final RollbackDetector rollbackDetector;
    private final ConditionEvaluator conditionEvaluator;
    private final Consumer<ProgressUpdatedEvent> eventEmitter;

    public ProgressService(ProgressInstanceStore instanceStore,
                           ProgressEventStore eventStore,
                           List<ShapeValidator> validators,
                           StepValidator stepValidator,
                           StepShapeValidator stepShapeValidator,
                           RollbackDetector rollbackDetector,
                           ConditionEvaluator conditionEvaluator,
                           Consumer<ProgressUpdatedEvent> eventEmitter) {
        this.instanceStore = instanceStore;
        this.eventStore = eventStore;
        this.validators = validators.stream()
                .collect(Collectors.toMap(ShapeValidator::shapeType, v -> v));
        this.stepValidator = stepValidator;
        this.stepShapeValidator = stepShapeValidator;
        this.rollbackDetector = rollbackDetector;
        this.conditionEvaluator = conditionEvaluator;
        this.eventEmitter = eventEmitter;
    }

    public ProgressInstance create(ProgressCreateRequest request) {
        if ("step".equals(request.shapeType()) && request.definition() == null) {
            throw new IllegalArgumentException("Step shape requires a 'definition' field");
        }

        if ("step".equals(request.shapeType())) {
            stepShapeValidator.validateDefinition(request.definition());
        }

        validateShape(request.shapeType(), request.state(), request.definition());

        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();

        UUID rootProgressId;
        if (request.parentProgressId() != null) {
            ProgressInstance parent = requireInstance(request.parentProgressId());
            rootProgressId = parent.rootProgressId();
        } else {
            rootProgressId = id;
        }

        ProgressInstance instance = new ProgressInstance(
                id, request.tenancyId(), request.scopeType(), request.scopeId(),
                request.parentProgressId(), rootProgressId,
                request.shapeType(), request.definition(), request.state(),
                ProgressStatus.PENDING, request.rollupStrategyId(),
                request.rollbackPolicy(), request.visualisationMode(),
                now, now);

        instanceStore.put(instance);
        emitEvent(instance, null, ProgressChangeType.CREATED);
        return instance;}

    public ProgressInstance updateState(UUID id, JsonNode newState) {
        ProgressInstance instance = requireInstance(id);
        validateShape(instance.shapeType(), newState, instance.definition());

        ProgressStatus newStatus = instance.status();
        if (newStatus == ProgressStatus.PENDING) {
            newStatus = ProgressStatus.ACTIVE;
        }

        ProgressChangeType changeType;
        if (rollbackDetector.isRollback(instance.shapeType(), instance.state(), newState, instance.definition())) {
            if ("denied".equalsIgnoreCase(instance.rollbackPolicy())) {
                throw new IllegalStateException("Rollback denied by policy");
            }
            changeType = ProgressChangeType.ROLLED_BACK;
        } else {
            changeType = ProgressChangeType.STATE_UPDATED;
        }

        ProgressInstance updated = withState(instance, newState, newStatus);
        instanceStore.put(updated);
        emitEvent(updated, instance.state(), changeType);
        return updated;}

    public ProgressInstance complete(UUID id) {
        ProgressInstance instance = requireInstance(id);
        validateStatusTransition(instance.status(), ProgressStatus.COMPLETED);

        ProgressInstance updated = withStatus(instance, ProgressStatus.COMPLETED);
        instanceStore.put(updated);
        emitEvent(updated, instance.state(), ProgressChangeType.COMPLETED);
        return updated;
    }

    public ProgressInstance fail(UUID id) {
        ProgressInstance instance = requireInstance(id);
        validateStatusTransition(instance.status(), ProgressStatus.FAILED);

        ProgressInstance updated = withStatus(instance, ProgressStatus.FAILED);
        instanceStore.put(updated);
        emitEvent(updated, instance.state(), ProgressChangeType.FAILED);
        return updated;
    }

    public ProgressInstance reactivate(UUID id) {
        ProgressInstance instance = requireInstance(id);
        if (!instance.status().isQuiescent()) {
            throw new IllegalStateException(
                    "Can only reactivate quiescent instances, current status: " + instance.status());
        }

        ProgressInstance updated = withStatus(instance, ProgressStatus.ACTIVE);
        instanceStore.put(updated);
        emitEvent(updated, instance.state(), ProgressChangeType.REACTIVATED);
        return updated;
    }

    public ProgressInstance attachChild(UUID parentId, ProgressCreateRequest childRequest) {
        ProgressInstance parent = requireInstance(parentId);

        ProgressCreateRequest withParent = new ProgressCreateRequest(
                childRequest.tenancyId(), childRequest.scopeType(), childRequest.scopeId(),
                childRequest.shapeType(), childRequest.state(),
                parentId, childRequest.rollupStrategyId(), childRequest.definition(),
                childRequest.rollbackPolicy(), childRequest.visualisationMode());

        ProgressInstance child = create(withParent);

        emitEvent(parent, parent.state(), ProgressChangeType.CHILD_ATTACHED);
        return child;}

    public Optional<ProgressInstance> findById(UUID id) {
        return instanceStore.get(id);
    }

    public List<ProgressInstance> findByScope(String scopeType, String scopeId) {
        return instanceStore.findByScopeTypeAndScopeId(scopeType, scopeId);
    }

    public List<ProgressInstance> findChildren(UUID parentId) {
        return instanceStore.findByParentProgressId(parentId);
    }

    public ProgressInstance rollback(UUID id) {
        ProgressInstance           instance = requireInstance(id);
        List<ProgressUpdatedEvent> events   = eventStore.findByProgressId(id);

        List<JsonNode> forwardStates = new ArrayList<>();
        for (ProgressUpdatedEvent e : events) {
            if (e.changeType() == ProgressChangeType.STATE_UPDATED || e.changeType() == ProgressChangeType.CREATED) {
                forwardStates.add(e.currentState());
            }
        }

        JsonNode currentState = instance.state();
        for (int i = forwardStates.size() - 1; i >= 0; i--) {
            if (forwardStates.get(i).equals(currentState)) {
                if (i == 0) {
                    throw new IllegalStateException("No previous state to roll back to");
                }
                return applyRollbackState(instance, forwardStates.get(i - 1));
            }
        }

        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).currentState().equals(currentState) && events.get(i).previousState() != null) {
                return applyRollbackState(instance, events.get(i).previousState());
            }
        }

        throw new IllegalStateException("No previous state to roll back to");}

    public ProgressInstance rollbackToEvent(UUID id, UUID eventId) {
        ProgressInstance instance = requireInstance(id);
        ProgressUpdatedEvent event = eventStore.findById(eventId)
                                               .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        if (!event.progressId().equals(id)) {
            throw new IllegalArgumentException("Event " + eventId + " does not belong to instance " + id);
        }

        return applyRollbackState(instance, event.currentState());
    }

    public List<ProgressSnapshot> getSnapshots(UUID id, int limit) {
        return eventStore.findByProgressId(id).stream()
                         .map(e -> new ProgressSnapshot(e.id(), e.currentState(), e.status(), e.changeType(), e.timestamp()))
                         .limit(limit)
                         .toList();
    }

    private ProgressInstance applyRollbackState(ProgressInstance instance, JsonNode newState) {
        validateShape(instance.shapeType(), newState, instance.definition());

        ProgressStatus newStatus = instance.status();
        if (newStatus == ProgressStatus.PENDING) {
            newStatus = ProgressStatus.ACTIVE;
        }

        ProgressInstance updated = withState(instance, newState, newStatus);
        instanceStore.put(updated);
        emitEvent(updated, instance.state(), ProgressChangeType.ROLLED_BACK);
        return updated;
    }


    // --- Step convenience methods ---

    public ProgressInstance startStep(UUID id, String stepName) {
        ProgressInstance instance = requireActiveOrPending(id);
        List<StepDefinition> defs = StepShapeValidator.parseDefinition(instance.definition());
        StepStatus currentStatus = getStepStatus(instance, stepName);

        stepValidator.validateTransition(stepName, currentStatus, StepStatus.ACTIVE,
                defs, instance.state(), conditionEvaluator);

        return applyStepStatusChange(instance, stepName, StepStatus.ACTIVE, defs);
    }

    public ProgressInstance completeStep(UUID id, String stepName) {
        ProgressInstance instance = requireActiveOrPending(id);
        List<StepDefinition> defs = StepShapeValidator.parseDefinition(instance.definition());
        StepStatus currentStatus = getStepStatus(instance, stepName);

        stepValidator.validateTransition(stepName, currentStatus, StepStatus.COMPLETED,
                defs, instance.state(), conditionEvaluator);

        return applyStepStatusChange(instance, stepName, StepStatus.COMPLETED, defs);
    }

    public ProgressInstance skipStep(UUID id, String stepName) {
        ProgressInstance instance = requireActiveOrPending(id);
        List<StepDefinition> defs = StepShapeValidator.parseDefinition(instance.definition());
        StepStatus currentStatus = getStepStatus(instance, stepName);

        stepValidator.validateTransition(stepName, currentStatus, StepStatus.SKIPPED,
                defs, instance.state(), conditionEvaluator);

        return applyStepStatusChange(instance, stepName, StepStatus.SKIPPED, defs);
    }

    public ProgressInstance failStep(UUID id, String stepName) {
        ProgressInstance instance = requireActiveOrPending(id);
        List<StepDefinition> defs = StepShapeValidator.parseDefinition(instance.definition());
        StepStatus currentStatus = getStepStatus(instance, stepName);

        stepValidator.validateTransition(stepName, currentStatus, StepStatus.FAILED,
                defs, instance.state(), conditionEvaluator);

        return applyStepStatusChange(instance, stepName, StepStatus.FAILED, defs);
    }

    public ProgressInstance updateStepState(UUID id, String stepName, JsonNode data) {
        ProgressInstance instance = requireActiveOrPending(id);
        requireStepExists(instance, stepName);

        ObjectNode newState = instance.state().deepCopy();
        ObjectNode stepNode = (ObjectNode) newState.get("steps").get(stepName);
        stepNode.set("data", data);

        return updateState(id, newState);
    }

    // --- Private helpers ---

    private ProgressInstance applyStepStatusChange(ProgressInstance instance, String stepName,
                                                    StepStatus newStatus, List<StepDefinition> defs) {
        ObjectNode newState = instance.state().deepCopy();
        ObjectNode stepNode = (ObjectNode) newState.get("steps").get(stepName);
        stepNode.put("status", newStatus.name().toLowerCase(Locale.ROOT));

        ProgressStatus instanceStatus = instance.status();
        if (instanceStatus == ProgressStatus.PENDING) {
            instanceStatus = ProgressStatus.ACTIVE;
        }

        ProgressChangeType changeType = ProgressChangeType.STATE_UPDATED;
        if (rollbackDetector.isRollback(instance.shapeType(), instance.state(), newState, instance.definition())) {
            changeType = ProgressChangeType.ROLLED_BACK;
        }

        if (stepValidator.isDerivedCompletion(defs, newState)) {
            instanceStatus = ProgressStatus.COMPLETED;
            changeType = ProgressChangeType.COMPLETED;
        }

        ProgressInstance updated = withState(instance, newState, instanceStatus);
        instanceStore.put(updated);
        emitEvent(updated, instance.state(), changeType);
        return updated;
    }

    private StepStatus getStepStatus(ProgressInstance instance, String stepName) {
        requireStepExists(instance, stepName);
        JsonNode stepNode = instance.state().get("steps").get(stepName);
        String statusStr = stepNode.get("status").asText();
        return StepStatus.valueOf(statusStr.toUpperCase(Locale.ROOT));
    }

    private void requireStepExists(ProgressInstance instance, String stepName) {
        if (!"step".equals(instance.shapeType())) {
            throw new IllegalStateException("Step operations only valid for step-shaped instances");
        }
        JsonNode steps = instance.state().get("steps");
        if (steps == null || !steps.has(stepName)) {
            throw new IllegalArgumentException("Unknown step: " + stepName);
        }
    }

    private ProgressInstance requireInstance(UUID id) {
        return instanceStore.get(id)
                .orElseThrow(() -> new IllegalArgumentException("ProgressInstance not found: " + id));
    }

    private ProgressInstance requireActiveOrPending(UUID id) {
        ProgressInstance instance = requireInstance(id);
        if (instance.status().isQuiescent()) {
            throw new IllegalStateException(
                    "Instance is " + instance.status() + " — reactivate before modifying step state");
        }
        return instance;
    }

    private void validateShape(String shapeType, JsonNode state, JsonNode definition) {
        ShapeValidator validator = validators.get(shapeType);
        if (validator == null) {
            throw new IllegalArgumentException("Unknown shape type: " + shapeType);
        }
        validator.validate(state, definition);
    }

    private void validateStatusTransition(ProgressStatus from, ProgressStatus to) {
        boolean valid = switch (to) {
            case ACTIVE -> from.isQuiescent();
            case COMPLETED -> from == ProgressStatus.PENDING || from == ProgressStatus.ACTIVE;
            case FAILED -> from == ProgressStatus.PENDING || from == ProgressStatus.ACTIVE;
            case PENDING -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid transition: " + from + " -> " + to);
        }
    }

    private ProgressInstance withState(ProgressInstance instance, JsonNode newState,
                                       ProgressStatus newStatus) {
        return new ProgressInstance(
                instance.id(), instance.tenancyId(), instance.scopeType(), instance.scopeId(),
                instance.parentProgressId(), instance.rootProgressId(),
                instance.shapeType(), instance.definition(), newState,
                newStatus, instance.rollupStrategyId(),
                instance.rollbackPolicy(), instance.visualisationMode(),
                instance.createdAt(), Instant.now());}

    private ProgressInstance withStatus(ProgressInstance instance, ProgressStatus newStatus) {
        return withState(instance, instance.state(), newStatus);
    }

    private void emitEvent(ProgressInstance instance, JsonNode previousState,
                           ProgressChangeType changeType) {
        ProgressUpdatedEvent event = new ProgressUpdatedEvent(
                UUID.randomUUID(),
                instance.id(), instance.tenancyId(),
                instance.scopeType(), instance.scopeId(),
                instance.parentProgressId(), instance.rootProgressId(),
                instance.shapeType(), previousState, instance.state(),
                instance.status(), changeType, Instant.now());
        eventStore.append(event);
        eventEmitter.accept(event);}
}
