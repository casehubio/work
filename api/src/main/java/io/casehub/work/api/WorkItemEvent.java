package io.casehub.work.api;

import java.util.UUID;

public interface WorkItemEvent {

    WorkItemRef ref();

    default UUID workItemId() { return ref().id(); }
    default WorkItemStatus status() { return ref().status(); }
    default String callerRef() { return ref().callerRef(); }
    default String assigneeId() { return ref().assigneeId(); }
    default String resolution() { return ref().resolution(); }
    default String candidateGroups() { return ref().candidateGroups(); }
    default String outcome() { return ref().outcome(); }
    default String tenancyId() { return ref().tenancyId(); }
}
