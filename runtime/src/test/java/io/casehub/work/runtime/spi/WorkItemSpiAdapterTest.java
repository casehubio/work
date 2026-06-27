package io.casehub.work.runtime.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemRef;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkItemSpiAdapterTest {

    private WorkItemService workItemService;
    private WorkItemTemplateService workItemTemplateService;
    private WorkItemSpiAdapter adapter;

    @BeforeEach
    void setUp() {
        workItemService = mock(WorkItemService.class);
        workItemTemplateService = mock(WorkItemTemplateService.class);
        adapter = new WorkItemSpiAdapter(workItemService, workItemTemplateService);
    }

    @Test
    void create_noTemplateId_delegatesToWorkItemService() {
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test").createdBy("system").build();
        final WorkItem entity = new WorkItem();
        entity.id = UUID.randomUUID();
        entity.status = WorkItemStatus.PENDING;
        when(workItemService.create(request)).thenReturn(entity);

        final WorkItemRef ref = adapter.create(request);

        assertThat(ref.id()).isEqualTo(entity.id);
        assertThat(ref.status()).isEqualTo(WorkItemStatus.PENDING);
        verify(workItemTemplateService, never()).createFromTemplate(any());
    }

    @Test
    void create_withTemplateId_delegatesToTemplateService() {
        final UUID templateId = UUID.randomUUID();
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("test").templateId(templateId).createdBy("system").build();
        final WorkItem entity = new WorkItem();
        entity.id = UUID.randomUUID();
        entity.status = WorkItemStatus.PENDING;
        when(workItemTemplateService.createFromTemplate(request)).thenReturn(entity);

        final WorkItemRef ref = adapter.create(request);

        assertThat(ref.id()).isEqualTo(entity.id);
        verify(workItemService, never()).create(any());
    }

    @Test
    void findByCallerRef_delegatesAndConverts() {
        final WorkItem entity = new WorkItem();
        entity.id = UUID.randomUUID();
        entity.status = WorkItemStatus.IN_PROGRESS;
        entity.callerRef = "case:123/pi:456";
        entity.assigneeId = "alice";
        when(workItemService.findByCallerRef("case:123/pi:456")).thenReturn(Optional.of(entity));

        final Optional<WorkItemRef> result = adapter.findByCallerRef("case:123/pi:456");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(entity.id);
        assertThat(result.get().callerRef()).isEqualTo("case:123/pi:456");
        assertThat(result.get().assigneeId()).isEqualTo("alice");
    }

    @Test
    void findActiveByCallerRef_delegatesAndConverts() {
        final WorkItem entity = new WorkItem();
        entity.id = UUID.randomUUID();
        entity.status = WorkItemStatus.PENDING;
        when(workItemService.findActiveByCallerRef("ref-1")).thenReturn(Optional.of(entity));

        final Optional<WorkItemRef> result = adapter.findActiveByCallerRef("ref-1");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void cancel_delegatesToService() {
        final UUID id = UUID.randomUUID();
        final WorkItem entity = new WorkItem();
        entity.id = id;
        entity.status = WorkItemStatus.PENDING;
        when(workItemService.cancel(id, "system", "done")).thenReturn(entity);

        adapter.cancel(id, "system", "done");

        verify(workItemService).cancel(id, "system", "done");
    }

    @Test
    void cancel_idempotentOnTerminal() {
        final UUID id = UUID.randomUUID();
        final WorkItem entity = new WorkItem();
        entity.id = id;
        entity.status = WorkItemStatus.COMPLETED;
        when(workItemService.cancel(id, "system", "done"))
                .thenThrow(new IllegalStateException("Cannot cancel"));
        when(workItemService.findById(id)).thenReturn(Optional.of(entity));

        adapter.cancel(id, "system", "done");

        verify(workItemService).cancel(id, "system", "done");
    }

    @Test
    void cancel_rethrowsOnActiveNonCancellable() {
        final UUID id = UUID.randomUUID();
        final WorkItem entity = new WorkItem();
        entity.id = id;
        entity.status = WorkItemStatus.IN_PROGRESS;
        when(workItemService.cancel(id, "system", "done"))
                .thenThrow(new IllegalStateException("Cannot cancel"));
        when(workItemService.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> adapter.cancel(id, "system", "done"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void complete_idempotentOnTerminal() {
        final UUID id = UUID.randomUUID();
        final WorkItem entity = new WorkItem();
        entity.id = id;
        entity.status = WorkItemStatus.COMPLETED;
        when(workItemService.complete(eq(id), eq("alice"), eq("{}"), eq("approved"), any(), any()))
                .thenThrow(new IllegalStateException("Cannot complete"));
        when(workItemService.findById(id)).thenReturn(Optional.of(entity));

        adapter.complete(id, "alice", "{}", "approved", null, null);

        verify(workItemService).complete(id, "alice", "{}", "approved", null, null);
    }
}
