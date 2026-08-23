package io.casehub.work.progress.runtime.service;

import io.casehub.work.progress.*;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.spi.ProgressInstanceStore;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SubtreeRollbackService {

    private final ProgressService progressService;
    private final ProgressInstanceStore instanceStore;
    private final ProgressEventStore eventStore;

    public SubtreeRollbackService(ProgressService progressService,
                                   ProgressInstanceStore instanceStore,
                                   ProgressEventStore eventStore) {
        this.progressService = progressService;
        this.instanceStore = instanceStore;
        this.eventStore = eventStore;
    }

    public SubtreeRollbackResult rollbackSubtree(UUID rootId, Instant targetTimestamp) {
        UUID operationId = UUID.randomUUID();

        ProgressInstance root = instanceStore.get(rootId)
                .orElseThrow(() -> new IllegalArgumentException("Progress instance not found: " + rootId));

        List<ProgressInstance> descendants = instanceStore.findDescendantsOf(rootId);
        List<ProgressInstance> allNodes = new ArrayList<>();
        allNodes.add(root);
        allNodes.addAll(descendants);

        List<ProgressInstance> leafNodes = allNodes.stream()
                .filter(n -> n.rollupStrategyId() == null)
                .toList();
        List<ProgressInstance> rollupNodes = allNodes.stream()
                .filter(n -> n.rollupStrategyId() != null)
                .toList();

        List<NodeRollbackOutcome> outcomes = new ArrayList<>();

        for (ProgressInstance node : leafNodes) {
            outcomes.add(rollbackNode(node, targetTimestamp, operationId));
        }

        Map<UUID, Integer> depthMap = computeDepths(root, descendants);
        List<ProgressInstance> sortedRollupNodes = rollupNodes.stream()
                .sorted(Comparator.comparingInt((ProgressInstance n) -> depthMap.getOrDefault(n.id(), 0)).reversed())
                .toList();

        for (ProgressInstance rollupNode : sortedRollupNodes) {
            if (rollupNode.createdAt().isAfter(targetTimestamp)) {
                outcomes.add(new NodeRollbackOutcome(rollupNode.id(),
                        NodeRollbackOutcome.Outcome.SKIPPED, "created after target timestamp",
                        null, null, false));
                continue;
            }

            List<ProgressInstance> children = instanceStore.findByParentProgressId(rollupNode.id());
            List<ProgressInstance> preTargetChildren = children.stream()
                    .filter(c -> !c.createdAt().isAfter(targetTimestamp))
                    .toList();

            try {
                ProgressInstance result = progressService.applyRollupState(
                        rollupNode.id(), preTargetChildren, operationId);
                if (result != null) {
                    outcomes.add(new NodeRollbackOutcome(rollupNode.id(),
                            NodeRollbackOutcome.Outcome.ROLLED_BACK, null,
                            rollupNode.state(), result.state(), false));
                } else {
                    outcomes.add(new NodeRollbackOutcome(rollupNode.id(),
                            NodeRollbackOutcome.Outcome.SKIPPED, "already at target state",
                            null, null, false));
                }
            } catch (Exception e) {
                outcomes.add(new NodeRollbackOutcome(rollupNode.id(),
                        NodeRollbackOutcome.Outcome.FAILED, e.getMessage(),
                        null, null, false));
            }
        }

        return new SubtreeRollbackResult(operationId, rootId, targetTimestamp, outcomes);
    }

    public SubtreeRollbackResult rollbackSubtreeToEvent(UUID rootId, UUID eventId) {
        ProgressUpdatedEvent event = eventStore.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        return rollbackSubtree(rootId, event.timestamp());
    }

    private NodeRollbackOutcome rollbackNode(ProgressInstance node, Instant targetTimestamp, UUID operationId) {
        if (node.createdAt().isAfter(targetTimestamp)) {
            return new NodeRollbackOutcome(node.id(),
                    NodeRollbackOutcome.Outcome.SKIPPED, "created after target timestamp",
                    null, null, false);
        }

        try {
            ProgressInstance result = progressService.rollbackToTimestamp(node.id(), targetTimestamp, operationId);
            if (result == null) {
                return new NodeRollbackOutcome(node.id(),
                        NodeRollbackOutcome.Outcome.SKIPPED, "already at target state",
                        null, null, false);
            }
            boolean bypassed = "denied".equalsIgnoreCase(node.rollbackPolicy());
            return new NodeRollbackOutcome(node.id(),
                    NodeRollbackOutcome.Outcome.ROLLED_BACK, null,
                    node.state(), result.state(), bypassed);
        } catch (IllegalStateException e) {
            return new NodeRollbackOutcome(node.id(),
                    NodeRollbackOutcome.Outcome.SKIPPED, e.getMessage(),
                    null, null, false);
        } catch (Exception e) {
            return new NodeRollbackOutcome(node.id(),
                    NodeRollbackOutcome.Outcome.FAILED, e.getMessage(),
                    null, null, false);
        }
    }

    private Map<UUID, Integer> computeDepths(ProgressInstance root, List<ProgressInstance> descendants) {
        Map<UUID, Integer> depths = new HashMap<>();
        depths.put(root.id(), 0);
        Map<UUID, List<ProgressInstance>> byParent = descendants.stream()
                .filter(d -> d.parentProgressId() != null)
                .collect(Collectors.groupingBy(ProgressInstance::parentProgressId));

        Queue<UUID> queue = new LinkedList<>();
        queue.add(root.id());
        while (!queue.isEmpty()) {
            UUID parentId = queue.poll();
            int parentDepth = depths.get(parentId);
            List<ProgressInstance> children = byParent.getOrDefault(parentId, List.of());
            for (ProgressInstance child : children) {
                depths.put(child.id(), parentDepth + 1);
                queue.add(child.id());
            }
        }
        return depths;
    }
}
