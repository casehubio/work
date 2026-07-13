package io.casehub.work.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.spi.ExclusionPolicy;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerRegistry;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import io.casehub.work.api.spi.WorkloadProvider;
import io.casehub.work.core.strategy.WorkBroker;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemStatus;

/**
 * Orchestrates worker selection for WorkItems on creation, release, and delegation.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>Resolve active strategy via {@link StrategyResolver} using the configured id</li>
 * <li>Build resolved candidate list from {@code candidateUsers} + {@code WorkerRegistry}</li>
 * <li>Populate {@code activeWorkItemCount} for each candidate via {@link WorkloadProvider}</li>
 * <li>Delegate trigger gating, capability filtering, and strategy dispatch to {@link WorkBroker}</li>
 * <li>Apply non-null fields of {@link AssignmentDecision} to the WorkItem</li>
 * </ol>
 *
 * <p>
 * Mutates the WorkItem in memory only. The caller's {@code @Transactional} boundary
 * flushes the changes to the database.
 */
@ApplicationScoped
public class WorkItemAssignmentService {

    private final StrategyResolver strategyResolver;
    private final WorkItemsConfig config;
    private final WorkerRegistry workerRegistry;
    private final WorkloadProvider workloadProvider;
    private final WorkBroker workBroker;
    private final ExclusionPolicy exclusionPolicy;

    @Inject
    public WorkItemAssignmentService(
            final StrategyResolver strategyResolver,
            final WorkItemsConfig config,
            final WorkerRegistry workerRegistry,
            final WorkloadProvider workloadProvider,
            final WorkBroker workBroker,
            final ExclusionPolicy exclusionPolicy) {
        this.strategyResolver = strategyResolver;
        this.config = config;
        this.workerRegistry = workerRegistry;
        this.workloadProvider = workloadProvider;
        this.workBroker = workBroker;
        this.exclusionPolicy = exclusionPolicy;
    }

    public void assign(final WorkItem workItem, final AssignmentTrigger trigger) {
        final WorkerSelectionStrategy strategy = activeStrategy();
        final List<WorkerCandidate> candidates = resolveCandidates(workItem);
        final SelectionContext context = new SelectionContext(
                workItem.types != null ? workItem.types.stream().map(t -> t.path).toList() : java.util.List.of(),
                workItem.priority != null ? workItem.priority.name() : null,
                CapabilityParser.parseLenient(workItem.requiredCapabilities),
                workItem.candidateGroups,
                workItem.candidateUsers,
                workItem.title,
                workItem.description,
                workItem.excludedUsers);

        final AssignmentDecision decision = workBroker.apply(context, trigger, candidates, strategy);
        applyDecision(workItem, decision);
    }

    private WorkerSelectionStrategy activeStrategy() {
        return strategyResolver.resolve(WorkerSelectionStrategy.class, config.routing().strategy());
    }

    private List<WorkerCandidate> resolveCandidates(final WorkItem workItem) {
        final List<WorkerCandidate> candidates = new ArrayList<>();

        // 1. candidateUsers — direct user IDs
        if (workItem.candidateUsers != null && !workItem.candidateUsers.isBlank()) {
            Arrays.stream(workItem.candidateUsers.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .forEach(id -> candidates.add(
                            WorkerCandidate.of(id).withActiveWorkItemCount(
                                    workloadProvider.getActiveWorkCount(id))));
        }

        // 2. candidateGroups — resolved via WorkerRegistry
        if (workItem.candidateGroups != null && !workItem.candidateGroups.isBlank()) {
            Arrays.stream(workItem.candidateGroups.split(","))
                    .map(String::trim)
                    .filter(g -> !g.isEmpty())
                    .flatMap(g -> workerRegistry.resolveGroup(g).stream())
                    .filter(c -> candidates.stream().noneMatch(e -> e.id().equals(c.id())))
                    .map(c -> c.activeWorkItemCount() > 0
                            ? c
                            : c.withActiveWorkItemCount(workloadProvider.getActiveWorkCount(c.id())))
                    .forEach(candidates::add);
        }

        // Filter excluded users — prevents excluded users from being auto-assigned
        if (workItem.excludedUsers != null) {
            candidates.removeIf(c -> exclusionPolicy.check(c.id(), workItem.excludedUsers).denied());
        }

        // Capability filtering and trigger gating are handled by WorkBroker.apply()
        return candidates;
    }

    private void applyDecision(final WorkItem workItem, final AssignmentDecision decision) {
        if (decision.assigneeId() != null) {
            workItem.assigneeId = decision.assigneeId();
            workItem.status = WorkItemStatus.ASSIGNED;
            workItem.assignedAt = Instant.now();
        }
        if (decision.candidateGroups() != null) {
            workItem.candidateGroups = decision.candidateGroups();
        }
        if (decision.candidateUsers() != null) {
            workItem.candidateUsers = decision.candidateUsers();
        }
    }
}
