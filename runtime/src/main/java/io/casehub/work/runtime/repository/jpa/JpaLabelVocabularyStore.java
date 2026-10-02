package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelVocabularyStore;

/**
 * Default JPA/Panache implementation of {@link LabelVocabularyStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaLabelVocabularyStore extends TenantAwareStore implements LabelVocabularyStore {

    @Override
    public LabelVocabulary put(final LabelVocabulary vocabulary) {
        return withTenantQuery(() -> {
            if (vocabulary.tenancyId == null) {
                vocabulary.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(vocabulary);
            em.flush();
            return vocabulary;
        });
    }

    @Override
    public Optional<LabelVocabulary> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelVocabulary WHERE id = ?1 AND tenancyId = ?2", LabelVocabulary.class)
                        .setParameter(1, id)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<LabelVocabulary> scanAll() {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelVocabulary WHERE tenancyId = ?1", LabelVocabulary.class)
                        .setParameter(1, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final int deleted = em.createQuery("DELETE FROM LabelVocabulary WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id)
                    .setParameter(2, currentPrincipal.tenancyId())
                    .executeUpdate();
            return deleted > 0;
        });
    }

    @Override
    public Optional<LabelVocabulary> findByScope(final Path scope) {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelVocabulary WHERE scope = ?1 AND tenancyId = ?2", LabelVocabulary.class)
                        .setParameter(1, scope)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    @Transactional(TxType.REQUIRES_NEW)
    public LabelVocabulary findOrCreate(final Path scope, final String name) {
        return withTenantQuery(() -> {
            final Optional<LabelVocabulary> existing = em.createQuery(
                            "FROM LabelVocabulary WHERE scope = ?1 AND tenancyId = ?2", LabelVocabulary.class)
                    .setParameter(1, scope)
                    .setParameter(2, currentPrincipal.tenancyId())
                    .getResultStream().findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
            final LabelVocabulary vocab = new LabelVocabulary();
            vocab.scope = scope;
            vocab.name = name;
            vocab.tenancyId = currentPrincipal.tenancyId();
            try {
                em.persist(vocab);
                em.flush();
                return vocab;
            } catch (PersistenceException e) {
                em.clear();
                return em.createQuery("FROM LabelVocabulary WHERE scope = ?1 AND tenancyId = ?2", LabelVocabulary.class)
                        .setParameter(1, scope)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Concurrent vocabulary creation failed", e));
            }
        });
    }
}
