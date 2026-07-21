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
            rule.persistAndFlush();
            return rule;
        });
    }

    @Override
    public Optional<LabelRuleEntity> get(final UUID id) {
        return withTenantQuery(() ->
                LabelRuleEntity.find("id = ?1 AND tenancyId = ?2", id,
                        currentPrincipal.tenancyId()).firstResultOptional());
    }

    @Override
    public List<LabelRuleEntity> findEnabled() {
        return withTenantQuery(() ->
                LabelRuleEntity.list("enabled = true AND tenancyId = ?1 ORDER BY createdAt ASC",
                        currentPrincipal.tenancyId()));
    }

    @Override
    public List<LabelRuleEntity> scanAll() {
        return withTenantQuery(() ->
                LabelRuleEntity.list("tenancyId = ?1 ORDER BY createdAt ASC",
                        currentPrincipal.tenancyId()));
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            long deleted = LabelRuleEntity.delete("id = ?1 AND tenancyId = ?2", id,
                    currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}
