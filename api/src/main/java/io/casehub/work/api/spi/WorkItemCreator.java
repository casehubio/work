package io.casehub.work.api.spi;

import java.util.Optional;

import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemRef;

public interface WorkItemCreator {

    WorkItemRef create(WorkItemCreateRequest request);

    Optional<WorkItemRef> findByCallerRef(String callerRef);

    Optional<WorkItemRef> findActiveByCallerRef(String callerRef);
}
