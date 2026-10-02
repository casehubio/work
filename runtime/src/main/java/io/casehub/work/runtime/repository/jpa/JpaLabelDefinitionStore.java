package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.repository.LabelDefinitionStore;

/**
 * Default JPA/Panache implementation of {@link LabelDefinitionStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaLabelDefinitionStore extends TenantAwareStore implements LabelDefinitionStore {

    @Override
    public LabelDefinition put(final LabelDefinition definition) {
        return withTenantQuery(() -> {
            if (definition.tenancyId == null) {
                definition.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(definition);
            em.flush();
            return definition;
        });
    }

    @Override
    public Optional<LabelDefinition> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelDefinition WHERE id = ?1 AND tenancyId = ?2", LabelDefinition.class)
                        .setParameter(1, id)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<LabelDefinition> findByVocabularyId(final UUID vocabularyId) {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelDefinition WHERE vocabularyId = ?1 AND tenancyId = ?2", LabelDefinition.class)
                        .setParameter(1, vocabularyId)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public List<LabelDefinition> findByPath(final Path path) {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelDefinition WHERE path = ?1 AND tenancyId = ?2", LabelDefinition.class)
                        .setParameter(1, path)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final int deleted = em.createQuery("DELETE FROM LabelDefinition WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id)
                    .setParameter(2, currentPrincipal.tenancyId())
                    .executeUpdate();
            return deleted > 0;
        });
    }
}
