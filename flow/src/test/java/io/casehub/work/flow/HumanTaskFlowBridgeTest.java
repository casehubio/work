package io.casehub.work.flow;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanTaskFlowBridgeTest {

    private WorkItemService workItemService;
    private PendingWorkItemRegistry registry;
    private HumanTaskFlowBridge bridge;

    @BeforeEach
    void setUp() {
        workItemService = mock(WorkItemService.class);
        registry = new PendingWorkItemRegistry();
        bridge = new HumanTaskFlowBridge();
        try {
            var serviceField = HumanTaskFlowBridge.class.getDeclaredField("workItemService");
            serviceField.setAccessible(true);
            serviceField.set(bridge, workItemService);
            var registryField = HumanTaskFlowBridge.class.getDeclaredField("registry");
            registryField.setAccessible(true);
            registryField.set(bridge, registry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void requestApprovalWithCreateRequest_createsWorkItemAndRegistersPending() {
        UUID testId = UUID.randomUUID();
        var request = WorkItemCreateRequest.builder()
            .title("Test approval")
            .candidateGroups("team-a")
            .priority(WorkItemPriority.HIGH)
            .callerRef("annotation:com.example.Svc.approve")
            .createdBy("work-annotations")
            .build();

        when(workItemService.create(any(WorkItemCreateRequest.class)))
            .thenReturn(WorkItem.builder().id(testId).status(WorkItemStatus.PENDING).build());

        Uni<String> result = bridge.requestApproval(request);

        assertThat(result).isNotNull();
        assertThat(registry.isPending(testId)).isTrue();
        verify(workItemService).create(request);
    }

    @Test
    void requestApprovalWithCreateRequest_passesRequestUnmodified() {
        UUID testId = UUID.randomUUID();
        var request = WorkItemCreateRequest.builder()
            .title("Custom title")
            .description("Custom desc")
            .priority(WorkItemPriority.URGENT)
            .candidateGroups("finance")
            .callerRef("annotation:com.example.Finance.approve")
            .createdBy("work-annotations")
            .routingStrategy("semantic")
            .minimumScore(0.8)
            .build();

        when(workItemService.create(request))
            .thenReturn(WorkItem.builder().id(testId).status(WorkItemStatus.PENDING).build());

        bridge.requestApproval(request);

        verify(workItemService).create(request);
    }
}
