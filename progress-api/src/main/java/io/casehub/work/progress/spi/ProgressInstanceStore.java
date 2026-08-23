package io.casehub.work.progress.spi;

import io.casehub.work.progress.ProgressInstance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgressInstanceStore {
    ProgressInstance put(ProgressInstance instance);

    Optional<ProgressInstance> get(UUID id);

    List<ProgressInstance> findByScopeTypeAndScopeId(String scopeType, String scopeId);

    List<ProgressInstance> findByParentProgressId(UUID parentProgressId);

    List<ProgressInstance> findDescendantsOf(UUID parentId);

}
