package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.filter.LabelRuleEntity;

public interface LabelRuleStore {

    LabelRuleEntity put(LabelRuleEntity rule);

    Optional<LabelRuleEntity> get(UUID id);

    List<LabelRuleEntity> findEnabled();

    List<LabelRuleEntity> scanAll();

    boolean delete(UUID id);
}
