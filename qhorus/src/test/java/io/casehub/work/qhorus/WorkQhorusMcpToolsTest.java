package io.casehub.work.qhorus;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemRef;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkQhorusMcpToolsTest {

    private WorkQhorusMcpTools tools;
    private final List<MessageDispatch> dispatched = new ArrayList<>();
    private final List<WorkItemCreateRequest> created = new ArrayList<>();
    private final AtomicReference<WorkItemRef> storedRef = new AtomicReference<>();
    private UUID testChannelId;

    @BeforeEach
    void setUp() {
        tools = new WorkQhorusMcpTools();
        dispatched.clear();
        created.clear();

        testChannelId = UUID.randomUUID();

        tools.channelReader = new ChannelReader() {
            @Override
            public Optional<Channel> findByName(String name) {
                if ("test/oversight".equals(name)) {
                    return Optional.of(Channel.builder("test/oversight").id(testChannelId)
                            .description("test").semantic(ChannelSemantic.APPEND).build());
                }
                return Optional.empty();
            }

            @Override public Optional<Channel> findById(UUID id) { return Optional.empty(); }
            @Override public List<Channel> findByNamePrefix(String prefix) { return List.of(); }
            @Override public List<Channel> listAll() { return List.of(); }
            @Override public List<Channel> scan(io.casehub.qhorus.api.store.query.ChannelQuery query) { return List.of(); }
            @Override public java.util.List<Channel> findByIds(java.util.Collection<UUID> ids) { return List.of(); }
        };

        tools.messageDispatcher = dispatch -> {
            dispatched.add(dispatch);
            return new DispatchResult(100L, dispatch.channelId(), dispatch.sender(),
                    dispatch.type(), dispatch.correlationId(), dispatch.inReplyTo(),
                    null, null, null, null, null, 0, null);
        };

        tools.workItemCreator = new WorkItemCreator() {
            @Override
            public WorkItemRef create(WorkItemCreateRequest request) {
                created.add(request);
                var ref = new WorkItemRef(UUID.randomUUID(), WorkItemStatus.PENDING,
                        request.callerRef, null, null, request.candidateGroups,
                        null, request.tenancyId, request.payload, null, null);
                storedRef.set(ref);
                return ref;
            }

            @Override
            public Optional<WorkItemRef> findByCallerRef(String callerRef) {
                var ref = storedRef.get();
                if (ref != null && ref.callerRef().equals(callerRef)) {
                    return Optional.of(ref);
                }
                return Optional.empty();
            }

            @Override
            public Optional<WorkItemRef> findActiveByCallerRef(String callerRef) {
                return findByCallerRef(callerRef);
            }

            @Override
            public void obsoleteByCallerRef(String callerRef) {}
        };

        tools.currentPrincipal = new CurrentPrincipal() {
            @Override public String tenancyId() { return "test-tenant"; }
            @Override public String actorId() { return "test-actor"; }
            @Override public boolean isCrossTenantAdmin() { return false; }
            @Override public java.util.Set<String> groups() { return java.util.Set.of(); }
            @Override public java.util.Set<String> roles() { return java.util.Set.of(); }
        };
    }

    @Test
    void requestHumanWorkCreatesWorkItemAndPostsQuery() {
        var result = tools.requestHumanWork("test/oversight", "Review PR #42",
                "Please review", "reviewers", null, null, null, "agent-1");

        assertThat(result.workItemId()).isNotNull();
        assertThat(result.correlationId()).isNotNull();
        assertThat(result.status()).isEqualTo("PENDING");

        assertThat(QhorusRef.isQhorus(result.callerRef())).isTrue();
        var ref = QhorusRef.parse(result.callerRef());
        assertThat(ref.channelId()).isEqualTo(testChannelId);
        assertThat(ref.messageId()).isEqualTo(100L);
        assertThat(ref.correlationId()).isEqualTo(result.correlationId());

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).type()).isEqualTo(MessageType.QUERY);
        assertThat(dispatched.get(0).channelId()).isEqualTo(testChannelId);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).title).isEqualTo("Review PR #42");
        assertThat(created.get(0).createdBy).isEqualTo("qhorus:agent-1");
    }

    @Test
    void requestHumanWorkRejectsNonexistentChannel() {
        assertThatThrownBy(() -> tools.requestHumanWork("nonexistent/channel",
                "Title", "Desc", null, null, null, null, "agent-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Channel not found");

        assertThat(dispatched).isEmpty();
        assertThat(created).isEmpty();
    }

    @Test
    void checkWorkStatusReturnsPending() {
        var result = tools.requestHumanWork("test/oversight", "Review",
                "Review needed", null, null, null, null, "agent-1");

        var status = tools.checkWorkStatus(result.callerRef());
        assertThat(status.status()).isEqualTo("PENDING");
        assertThat(status.workItemId()).isNotNull();
        assertThat(status.timedOut()).isFalse();
    }

    @Test
    void checkWorkStatusReturnsNotFound() {
        var status = tools.checkWorkStatus("qhorus:00000000-0000-0000-0000-000000000000/1/none");
        assertThat(status.status()).isEqualTo("NOT_FOUND");
        assertThat(status.workItemId()).isNull();
    }

    @Test
    void waitForWorkTimesOutOnNonTerminal() {
        var result = tools.requestHumanWork("test/oversight", "Review",
                "Review needed", null, null, null, null, "agent-1");

        var status = tools.waitForWork(result.callerRef(), 1, 1);
        assertThat(status.timedOut()).isTrue();
        assertThat(status.status()).isEqualTo("PENDING");
    }

    @Test
    void twoRequestsCreateDistinctWorkItems() {
        var r1 = tools.requestHumanWork("test/oversight", "Review 1",
                "First", null, null, null, null, "agent-1");
        var r2 = tools.requestHumanWork("test/oversight", "Review 2",
                "Second", null, null, null, null, "agent-1");

        assertThat(r1.workItemId()).isNotEqualTo(r2.workItemId());
        assertThat(r1.correlationId()).isNotEqualTo(r2.correlationId());
    }
}
