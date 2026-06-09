package io.casehub.work.issuetracker.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;

/**
 * Default JPA/Panache implementation of {@link IssueLinkStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #save} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaIssueLinkStore implements IssueLinkStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    /** {@inheritDoc} */
    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        return WorkItemIssueLink.find("id = ?1 AND tenancyId = ?2",
                id, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        return WorkItemIssueLink.list(
                "workItemId = ?1 AND tenancyId = ?2 ORDER BY linkedAt ASC",
                workItemId, currentPrincipal.tenancyId());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkItemIssueLink> findByRef(
            final UUID workItemId, final String trackerType, final String externalRef) {
        return WorkItemIssueLink.find(
                "workItemId = ?1 AND trackerType = ?2 AND externalRef = ?3 AND tenancyId = ?4",
                workItemId, trackerType, externalRef, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        return WorkItemIssueLink.list(
                "trackerType = ?1 AND externalRef = ?2 AND tenancyId = ?3",
                trackerType, externalRef, currentPrincipal.tenancyId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stamps {@code tenancyId} from the current principal when the entity does not
     * already carry one. Calls {@link WorkItemIssueLink#persistAndFlush()} to ensure
     * the entity is written immediately.
     */
    @Override
    public WorkItemIssueLink save(final WorkItemIssueLink link) {
        if (link.tenancyId == null) {
            link.tenancyId = currentPrincipal.tenancyId();
        }
        link.persistAndFlush();
        return link;
    }

    /** {@inheritDoc} */
    @Override
    public void delete(final WorkItemIssueLink link) {
        link.delete();
    }
}
