package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemNoteStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #append} and {@link #update} methods stamp {@code tenancyId} from the principal
 * when the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemNoteStore extends TenantAwareStore implements WorkItemNoteStore {

    @Override
    public WorkItemNote append(final WorkItemNote note) {
        return withTenantQuery(() -> {
            if (note.tenancyId == null) {
                note.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(note);
            em.flush();
            return note;
        });
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemNote WHERE id = ?1 AND tenancyId = ?2", WorkItemNote.class)
                        .setParameter(1, noteId).setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemNote WHERE workItemId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC", WorkItemNote.class)
                        .setParameter(1, workItemId).setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        return withTenantQuery(() -> {
            if (note.tenancyId == null) {
                note.tenancyId = currentPrincipal.tenancyId();
            }
            em.merge(note);
            em.flush();
            return note;
        });
    }

    @Override
    public boolean delete(final UUID noteId) {
        return withTenantQuery(() -> {
            int deleted = em.createQuery("DELETE FROM WorkItemNote WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, noteId).setParameter(2, currentPrincipal.tenancyId()).executeUpdate();
            return deleted > 0;
        });
    }
}
