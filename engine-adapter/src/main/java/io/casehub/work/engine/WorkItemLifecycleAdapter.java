/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.work.engine;

import io.casehub.api.context.ContextLayer;
import io.casehub.api.model.TaskStatus;
import io.casehub.engine.common.internal.event.CaseContextChangedEvent;
import io.casehub.engine.common.internal.event.EventBusAddresses;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.planning.plan.CasePlanModel;
import io.casehub.engine.planning.plan.PlanItem;
import io.casehub.engine.planning.registry.BlackboardRegistry;
import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItemEvent;
import io.casehub.work.api.WorkItemGroupLifecycleEvent;
import io.casehub.work.api.WorkItemRef;
import io.casehub.work.api.WorkItemStatus;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Translates terminal quarkus-work {@link WorkItemEvent}s and M-of-N {@link
 * WorkItemGroupLifecycleEvent}s into CaseHub PlanItem transitions and fires {@code CONTEXT_CHANGED}
 * to trigger engine re-evaluation.
 *
 * <p>Choreography path: the engine's binding evaluator picks up the next step automatically once
 * the PlanItem status changes and the context-changed signal arrives. Refs casehubio/work#136.
 *
 * <p>Only processes events whose {@code callerRef} matches the CaseHub format {@code
 * case:{caseId}/pi:{planItemId}} — other WorkItems are ignored.
 *
 * <p>ESCALATED is terminal — all SLA breach policy branches have been exhausted. The applier
 * transitions the PlanItem to FAULTED and writes a {@code workItemEscalated} signal to the case
 * context. SLA breach policies that re-route the WorkItem to new groups ({@code EscalateTo}) do
 * not set ESCALATED — the WorkItem stays PENDING with updated candidate groups. Refs #341.
 */
@ApplicationScoped
public class WorkItemLifecycleAdapter {

  private static final Logger LOG = Logger.getLogger(WorkItemLifecycleAdapter.class);
  @Inject BlackboardRegistry registry;

  @Inject CrossTenantCaseInstanceRepository caseInstanceRepository;

  @Inject EventBus eventBus;

  @Inject PlanItemCompletionApplier applier;

  @Inject ActionGateCompletionApplier gateApplier;

  public void onWorkItemLifecycle(@ObservesAsync WorkItemEvent wie) {

    final WorkItemStatus status = wie.status();

    if (status == WorkItemStatus.SUSPENDED) {
      handleSuspension(wie);
      return;
    }

    if (!status.isTerminal()) {
      handlePossibleResume(wie);
      return;
    }

    final CallerRef ref = CallerRef.parse(wie.callerRef());
    if (ref == null) return;

    if (ref instanceof GateRef gateRef) {
      routeGate(gateRef, status, wie.ref(), wie.tenancyId(), wie.ledgerEntryId());
      return;
    }

    if (!(ref instanceof PlanItemRef piRef)) return;

    if (registry.get(piRef.caseId()).isEmpty()) {
      LOG.debugf(
          "No CasePlanModel for caseId=%s — case may have completed or not use blackboard",
          piRef.caseId());
      return;
    }

    applier.apply(piRef.caseId(), piRef.planItemId(), status, wie.ref(), wie.ledgerEntryId());
  }

  public void onWorkItemGroupLifecycle(@ObservesAsync WorkItemGroupLifecycleEvent event) {
    GroupStatus status = event.groupStatus();
    if (!status.isTerminal()) return;

    CallerRef ref = CallerRef.parse(event.callerRef());
    if (!(ref instanceof PlanItemRef piRef)) return;

    CasePlanModel plan = registry.get(piRef.caseId()).orElse(null);
    if (plan == null) {
      LOG.debugf("No CasePlanModel for caseId=%s — group outcome ignored", piRef.caseId());
      return;
    }

    PlanItem item = plan.getPlanItem(piRef.planItemId()).orElse(null);
    if (item == null) {
      LOG.warnf(
          "PlanItem %s not found in case %s for group outcome", piRef.planItemId(), piRef.caseId());
      return;
    }

    if (!applyGroupStatus(item, status)) return;

    CaseInstance instance =
        caseInstanceRepository.findByUuid(piRef.caseId());
    if (instance == null) {
      LOG.warnf(
          "CaseInstance not found for caseId=%s — cannot fire CONTEXT_CHANGED", piRef.caseId());
      return;
    }

    eventBus.publish(
        EventBusAddresses.CONTEXT_CHANGED,
        new CaseContextChangedEvent(
            instance, instance.getCaseContext().snapshot(), ContextLayer.WORKING));
  }

  private boolean applyGroupStatus(PlanItem item, GroupStatus status) {
    try {
      switch (status) {
        case COMPLETED -> item.markCompleted();
        case REJECTED -> item.markRejected();
        default -> {
          return false;
        }
      }
      return true;
    } catch (IllegalStateException e) {
      LOG.warnf(
          "Cannot transition PlanItem %s (current=%s) for GroupStatus %s: %s",
          item.getPlanItemId(), item.getStatus(), status, e.getMessage());
      return false;
    }
  }


  private void handleSuspension(final WorkItemEvent event) {
    final CallerRef ref = CallerRef.parse(event.callerRef());
    if (!(ref instanceof PlanItemRef piRef)) return;

    final CasePlanModel plan = registry.get(piRef.caseId()).orElse(null);
    if (plan == null) return;

    plan.getPlanItem(piRef.planItemId())
        .ifPresent(
            item -> {
              try {
                item.markSuspended();
                LOG.infof("PlanItem %s suspended: caseId=%s", piRef.planItemId(), piRef.caseId());
              } catch (IllegalStateException e) {
                LOG.debugf(
                    "Cannot suspend PlanItem %s (status=%s): %s",
                    piRef.planItemId(), item.getStatus(), e.getMessage());
              }
            });
  }

  private void handlePossibleResume(final WorkItemEvent event) {
    final CallerRef ref = CallerRef.parse(event.callerRef());
    if (!(ref instanceof PlanItemRef piRef)) return;

    final CasePlanModel plan = registry.get(piRef.caseId()).orElse(null);
    if (plan == null) return;

    plan.getPlanItem(piRef.planItemId())
        .ifPresent(
            item -> {
              if (item.getStatus() == TaskStatus.SUSPENDED) {
                item.markResumed();
                LOG.infof("PlanItem %s resumed: caseId=%s", piRef.planItemId(), piRef.caseId());
              }
            });
  }

    private void routeGate(
            final GateRef gateRef, final WorkItemStatus status, final WorkItemRef ref,
            final String tenancyId, final java.util.UUID workLedgerEntryId) {
        gateApplier.apply(gateRef, status, ref, tenancyId, workLedgerEntryId);
    }
}
