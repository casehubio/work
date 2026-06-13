package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import io.casehub.platform.api.identity.CurrentPrincipal;
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
            vocabulary.persistAndFlush();
            return vocabulary;
        });
    }

    @Override
    public Optional<LabelVocabulary> get(final UUID id) {
        return withTenantQuery(() ->
                LabelVocabulary.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<LabelVocabulary> scanAll() {
        return withTenantQuery(() ->
                LabelVocabulary.find("tenancyId = ?1", currentPrincipal.tenancyId())
                        .list());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = LabelVocabulary.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }

    @Override
    public Optional<LabelVocabulary> findByScope(final Path scope) {
        return withTenantQuery(() ->
                LabelVocabulary.find("scope = ?1 AND tenancyId = ?2", scope, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    @Transactional(TxType.REQUIRES_NEW)
    public LabelVocabulary findOrCreate(final Path scope, final String name) {
        return withTenantQuery(() -> {
            final Optional<LabelVocabulary> existing = LabelVocabulary
                    .find("scope = ?1 AND tenancyId = ?2", scope, currentPrincipal.tenancyId())
                    .<LabelVocabulary>firstResultOptional();
            if (existing.isPresent()) {
                return existing.get();
            }
            final LabelVocabulary vocab = new LabelVocabulary();
            vocab.scope = scope;
            vocab.name = name;
            vocab.tenancyId = currentPrincipal.tenancyId();
            try {
                vocab.persistAndFlush();
                return vocab;
            } catch (PersistenceException e) {
                LabelVocabulary.getEntityManager().clear();
                return LabelVocabulary
                        .find("scope = ?1 AND tenancyId = ?2", scope, currentPrincipal.tenancyId())
                        .<LabelVocabulary>firstResultOptional()
                        .orElseThrow(() -> new IllegalStateException(
                                "Concurrent vocabulary creation failed", e));
            }
        });
    }
}
