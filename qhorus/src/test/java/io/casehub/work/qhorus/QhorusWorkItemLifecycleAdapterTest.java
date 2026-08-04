package io.casehub.work.qhorus;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.work.api.WorkEventType;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemStatusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QhorusWorkItemLifecycleAdapterTest {

    private final List<MessageDispatch> dispatched = new ArrayList<>();
    private QhorusWorkItemLifecycleAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new QhorusWorkItemLifecycleAdapter();
        adapter.messageDispatcher = (MessageDispatcher) dispatch -> {
            dispatched.add(dispatch);
            return new DispatchResult(1L, dispatch.channelId(), dispatch.sender(),
                    dispatch.type(), dispatch.correlationId(), dispatch.inReplyTo(),
                    null, null, null, null, null, 0, null);
        };
        dispatched.clear();
    }

    @Test
    void terminalCompletedPostsDone() {
        var channelId = UUID.randomUUID();
        var callerRef = new QhorusCallerRef(channelId, 42L, "corr-1").encode();

        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.COMPLETED, UUID.randomUUID(), WorkItemStatus.COMPLETED,
                "human-1", "Approved", callerRef, "human-1", "reviewers",
                "approved", "default", Instant.now()));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).type()).isEqualTo(MessageType.DONE);
        assertThat(dispatched.get(0).channelId()).isEqualTo(channelId);
        assertThat(dispatched.get(0).correlationId()).isEqualTo("corr-1");
        assertThat(dispatched.get(0).inReplyTo()).isEqualTo(42L);
        assertThat(dispatched.get(0).sender()).isEqualTo("workitems");
        assertThat(dispatched.get(0).content()).contains("approved");
    }

    @Test
    void rejectedPostsFailure() {
        var callerRef = new QhorusCallerRef(UUID.randomUUID(), 1L, "corr-2").encode();

        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.REJECTED, UUID.randomUUID(), WorkItemStatus.REJECTED,
                "human-1", "Cannot complete", callerRef, "human-1", null,
                null, "default", Instant.now()));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).type()).isEqualTo(MessageType.FAILURE);
    }

    @Test
    void cancelledPostsDecline() {
        var callerRef = new QhorusCallerRef(UUID.randomUUID(), 1L, "corr-3").encode();

        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.CANCELLED, UUID.randomUUID(), WorkItemStatus.CANCELLED,
                "system", "Cancelled", callerRef, null, null,
                null, "default", Instant.now()));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).type()).isEqualTo(MessageType.DECLINE);
    }

    @Test
    void expiredPostsDecline() {
        var callerRef = new QhorusCallerRef(UUID.randomUUID(), 1L, "corr-4").encode();

        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.EXPIRED, UUID.randomUUID(), WorkItemStatus.EXPIRED,
                "system", "Deadline passed", callerRef, null, null,
                null, "default", Instant.now()));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).type()).isEqualTo(MessageType.DECLINE);
    }

    @Test
    void escalatedPostsFailure() {
        var callerRef = new QhorusCallerRef(UUID.randomUUID(), 1L, "corr-5").encode();

        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.ESCALATED, UUID.randomUUID(), WorkItemStatus.ESCALATED,
                "system", "Escalated", callerRef, null, null,
                null, "default", Instant.now()));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).type()).isEqualTo(MessageType.FAILURE);
    }

    @Test
    void nonQhorusCallerRefIsIgnored() {
        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.COMPLETED, UUID.randomUUID(), WorkItemStatus.COMPLETED,
                "human-1", "Done", "case:abc/pi:def", "human-1", null,
                "done", "default", Instant.now()));

        assertThat(dispatched).isEmpty();
    }

    @Test
    void nullCallerRefIsIgnored() {
        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.COMPLETED, UUID.randomUUID(), WorkItemStatus.COMPLETED,
                "human-1", "Done", null, "human-1", null,
                "done", "default", Instant.now()));

        assertThat(dispatched).isEmpty();
    }

    @Test
    void nonTerminalStatusIsIgnored() {
        var callerRef = new QhorusCallerRef(UUID.randomUUID(), 1L, "corr-6").encode();

        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.ASSIGNED, UUID.randomUUID(), WorkItemStatus.ASSIGNED,
                "human-1", "Claimed", callerRef, "human-1", null,
                null, "default", Instant.now()));

        assertThat(dispatched).isEmpty();
    }

    @Test
    void dispatchFailureDoesNotPropagate() {
        adapter.messageDispatcher = dispatch -> {
            throw new RuntimeException("Channel not found");
        };

        var callerRef = new QhorusCallerRef(UUID.randomUUID(), 1L, "corr-7").encode();

        adapter.onStatusChange(new WorkItemStatusEvent(
                WorkEventType.COMPLETED, UUID.randomUUID(), WorkItemStatus.COMPLETED,
                "human-1", "Done", callerRef, "human-1", null,
                "done", "default", Instant.now()));
    }
}
