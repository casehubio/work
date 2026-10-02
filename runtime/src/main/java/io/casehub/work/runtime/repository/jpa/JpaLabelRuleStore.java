package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;

@ApplicationScoped
public class JpaLabelRuleStore extends TenantAwareStore implements LabelRuleStore {

    @Override
    public LabelRuleEntity put(final LabelRuleEntity rule) {
        return withTenantQuery(() -> {
            if (rule.tenancyId == null) {
                rule.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(rule);
            em.flush();
            return rule;
        });
    }

    @Override
    public Optional<LabelRuleEntity> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelRuleEntity WHERE id = ?1 AND tenancyId = ?2", LabelRuleEntity.class)
                        .setParameter(1, id)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<LabelRuleEntity> findEnabled() {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelRuleEntity WHERE enabled = true AND tenancyId = ?1 ORDER BY createdAt ASC", LabelRuleEntity.class)
                        .setParameter(1, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public List<LabelRuleEntity> scanAll() {
        return withTenantQuery(() ->
                em.createQuery("FROM LabelRuleEntity WHERE tenancyId = ?1 ORDER BY createdAt ASC", LabelRuleEntity.class)
                        .setParameter(1, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            int deleted = em.createQuery("DELETE FROM LabelRuleEntity WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id)
                    .setParameter(2, currentPrincipal.tenancyId())
                    .executeUpdate();
            return deleted > 0;
        });
    }
}
