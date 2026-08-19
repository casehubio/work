package io.casehub.work.annotations.runtime;

import io.casehub.work.api.OnThresholdReached;
import java.util.List;

public record QuorumDescriptor(
    int instances,
    int required,
    List<String> candidateGroups,
    OnThresholdReached onThresholdReached,
    boolean allowSameAssignee
) {}
