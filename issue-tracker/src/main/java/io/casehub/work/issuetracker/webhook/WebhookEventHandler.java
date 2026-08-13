package io.casehub.work.issuetracker.webhook;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.casehub.work.runtime.model.WorkItemLabelEntity;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Applies normalised {@link WebhookEvent} records to WorkItem state.
 * Called by tracker-specific webhook resources after HMAC verification and parsing.
 * Tracker vocabulary never reaches this class — all events arrive already normalised.
 */
@ApplicationScoped
public class WebhookEventHandler {

    private static final Logger LOG = Logger.getLogger(WebhookEventHandler.class);
    private static final String LINKED_WORKITEM_FOOTER = "\n\n---\n*Linked WorkItem:";

    @Inject
    IssueLinkStore linkStore;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    WorkItemService workItemService;

    /** Package-private constructor for unit testing without CDI. */
    WebhookEventHandler(
            final IssueLinkStore linkStore,
            final WorkItemStore workItemStore,
            final WorkItemService workItemService) {
        this.linkStore = linkStore;
        this.workItemStore = workItemStore;
        this.workItemService = workItemService;
    }

    WebhookEventHandler() {
        // CDI no-arg constructor
    }

    /**
     * Look up all WorkItems linked to the event's externalRef and apply the transition to each.
     * Failures per WorkItem are logged and swallowed — prevents tracker retries.
     */
    @Transactional
    public void handle(final WebhookEvent event) {
        final var links = linkStore.findByTrackerRef(event.trackerType(), event.externalRef());

        if (links.isEmpty()) {
            LOG.debugf("No WorkItem linked to %s:%s — ignoring", event.trackerType(), event.externalRef());
            return;
        }

        for (final var link : links) {
            workItemStore.get(link.workItemId)
                    .ifPresent(wi -> handle(link.workItemId, wi, event));
        }
    }

    void handle(final UUID workItemId, final io.casehub.work.api.WorkItem workItem, final WebhookEvent event) {
        if (workItem.status() != null && workItem.status().isTerminal()) {
            LOG.debugf("WorkItem %s is terminal (%s) — skipping %s event",
                       workItemId, workItem.status(), event.eventKind());
            return;
        }
        try {
            applyTransition(workItemId, workItem, event);
        } catch (final Exception e) {
            LOG.warnf("Failed to apply %s event to WorkItem %s: %s",
                      event.eventKind(), workItemId, e.getMessage());
        }
    }

    void applyTransition(final UUID workItemId, final io.casehub.work.api.WorkItem workItem, final WebhookEvent event) {
        switch (event.eventKind()) {
            case CLOSED -> applyClosed(workItemId, event);
            case ASSIGNED -> applyAssigned(workItemId, workItem, event);
            case UNASSIGNED -> workItemService.release(workItemId, event.actor());
            case TITLE_CHANGED -> workItemStore.put(workItem.toBuilder().title(event.newTitle()).build());
            case DESCRIPTION_CHANGED -> workItemStore.put(workItem.toBuilder().description(stripFooter(event.newDescription())).build());
            case PRIORITY_CHANGED -> workItemStore.put(workItem.toBuilder().priority(event.newPriority()).build());
            case LABEL_ADDED -> workItemService.addLabel(workItemId, event.labelValue(), "webhook");
            case LABEL_REMOVED -> workItemService.removeLabel(workItemId, event.labelValue());
        }
    }

    private void applyClosed(final UUID workItemId, final WebhookEvent event) {
        switch (event.normativeResolution()) {
            case DONE -> workItemService.completeFromSystem(workItemId, event.actor(), null);
            case DECLINE -> workItemService.cancel(workItemId, event.actor(), null);
            case FAILURE -> workItemService.reject(workItemId, event.actor(), null, null);
        }
    }

    private void applyAssigned(final UUID workItemId, final io.casehub.work.api.WorkItem workItem, final WebhookEvent event) {
        if (workItem.status() == WorkItemStatus.PENDING) {
            workItemService.claim(workItemId, event.newAssignee());
        } else {
            workItemStore.put(workItem.toBuilder().assigneeId(event.newAssignee()).build());
        }
    }

    private String stripFooter(final String description) {
        if (description == null) return null;
        final int idx = description.indexOf(LINKED_WORKITEM_FOOTER);
        return idx >= 0 ? description.substring(0, idx) : description;
    }

}
