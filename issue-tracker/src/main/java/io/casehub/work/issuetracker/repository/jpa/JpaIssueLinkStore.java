package io.casehub.work.issuetracker.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Default JPA implementation of {@link IssueLinkStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #save} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaIssueLinkStore extends TenantAwareStore implements IssueLinkStore {

    /** {@inheritDoc} */
    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        return withTenantQuery(() ->
            em.createQuery("FROM WorkItemIssueLink WHERE id = ?1 AND tenancyId = ?2", WorkItemIssueLink.class)
                    .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId())
                    .getResultStream().findFirst()
        );
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
            em.createQuery("FROM WorkItemIssueLink WHERE workItemId = ?1 AND tenancyId = ?2 ORDER BY linkedAt ASC", WorkItemIssueLink.class)
                    .setParameter(1, workItemId).setParameter(2, currentPrincipal.tenancyId())
                    .getResultList()
        );
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkItemIssueLink> findByRef(
            final UUID workItemId, final String trackerType, final String externalRef) {
        return withTenantQuery(() ->
            em.createQuery("FROM WorkItemIssueLink WHERE workItemId = ?1 AND trackerType = ?2 AND externalRef = ?3 AND tenancyId = ?4", WorkItemIssueLink.class)
                    .setParameter(1, workItemId).setParameter(2, trackerType).setParameter(3, externalRef).setParameter(4, currentPrincipal.tenancyId())
                    .getResultStream().findFirst()
        );
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        return withTenantQuery(() ->
            em.createQuery("FROM WorkItemIssueLink WHERE trackerType = ?1 AND externalRef = ?2 AND tenancyId = ?3", WorkItemIssueLink.class)
                    .setParameter(1, trackerType).setParameter(2, externalRef).setParameter(3, currentPrincipal.tenancyId())
                    .getResultList()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stamps {@code tenancyId} from the current principal when the entity does not
     * already carry one. Calls {@code em.persist()} and {@code em.flush()} to ensure
     * the entity is written immediately.
     */
    @Override
    public WorkItemIssueLink save(final WorkItemIssueLink link) {
        return withTenantQuery(() -> {
            if (link.tenancyId == null) {
                link.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(link);
            em.flush();
            return link;
        });
    }

    /** {@inheritDoc} */
    @Override
    public void delete(final WorkItemIssueLink link) {
        withTenantRun(() -> em.remove(em.contains(link) ? link : em.merge(link)));
    }
}
