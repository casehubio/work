package io.casehub.work.runtime.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.callback.CallbackInvoker;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CallbackWorkerSelectionStrategyDecoratorTest {

    private WorkerSelectionStrategy delegate;
    private CallbackRegistry callbackRegistry;
    private CallbackInvoker callbackInvoker;
    private CurrentPrincipal currentPrincipal;
    private CallbackWorkerSelectionStrategyDecorator decorator;

    private static final String TENANT_ID = "tenant-1";
    private static final String SPI_NAME = "worker-selection-strategy";

    @BeforeEach
    void setUp() {
        delegate = mock(WorkerSelectionStrategy.class);
        callbackRegistry = mock(CallbackRegistry.class);
        callbackInvoker = mock(CallbackInvoker.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        when(currentPrincipal.tenancyId()).thenReturn(TENANT_ID);
        when(delegate.id()).thenReturn("least-loaded");

        decorator = new CallbackWorkerSelectionStrategyDecorator(
                delegate, callbackRegistry, callbackInvoker, currentPrincipal);
    }

    @Test
    void id_alwaysDelegatesToWrappedBean() {
        assertThat(decorator.id()).isEqualTo("least-loaded");
    }

    @Test
    void select_noRegistrations_delegatesToLocal() {
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of());
        final AssignmentDecision expected = AssignmentDecision.assignTo("worker-1");
        when(delegate.select(any(), any())).thenReturn(expected);

        final AssignmentDecision result = decorator.select(testContext(), testCandidates());

        assertThat(result).isSameAs(expected);
        verify(callbackInvoker, never()).invoke(any(), any(), any(), any());
    }

    @Test
    void select_withRegistration_invokesRemote() {
        final CallbackRegistration reg = registration("http://app1/callback");
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of(reg));
        final AssignmentDecision remote = AssignmentDecision.assignTo("remote-worker");
        when(callbackInvoker.invoke(eq(reg), eq("select"), any(), eq(AssignmentDecision.class)))
                .thenReturn(remote);

        final AssignmentDecision result = decorator.select(testContext(), testCandidates());

        assertThat(result).isSameAs(remote);
        verify(delegate, never()).select(any(), any());
    }

    @Test
    void select_remoteReturnsNull_fallsBackToDelegate() {
        final CallbackRegistration reg = registration("http://app1/callback");
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of(reg));
        when(callbackInvoker.invoke(eq(reg), eq("select"), any(), eq(AssignmentDecision.class)))
                .thenReturn(null);
        final AssignmentDecision local = AssignmentDecision.assignTo("local-worker");
        when(delegate.select(any(), any())).thenReturn(local);

        final AssignmentDecision result = decorator.select(testContext(), testCandidates());

        assertThat(result).isSameAs(local);
    }

    @Test
    void select_remoteThrows_fallsBackToDelegate() {
        final CallbackRegistration reg = registration("http://app1/callback");
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of(reg));
        when(callbackInvoker.invoke(eq(reg), eq("select"), any(), eq(AssignmentDecision.class)))
                .thenThrow(new RuntimeException("connection refused"));
        final AssignmentDecision local = AssignmentDecision.assignTo("local-worker");
        when(delegate.select(any(), any())).thenReturn(local);

        final AssignmentDecision result = decorator.select(testContext(), testCandidates());

        assertThat(result).isSameAs(local);
    }

    @Test
    void triggers_alwaysDelegatesToWrappedBean() {
        assertThat(decorator.triggers()).isEqualTo(delegate.triggers());
    }

    private static SelectionContext testContext() {
        return new SelectionContext(
                List.of("review"), "MEDIUM", Set.of(),
                "reviewers", null, "Review task", null, null);
    }

    private static List<WorkerCandidate> testCandidates() {
        return List.of(
                WorkerCandidate.of("worker-1").withActiveWorkItemCount(3),
                WorkerCandidate.of("worker-2").withActiveWorkItemCount(1));
    }

    private static CallbackRegistration registration(final String url) {
        return new CallbackRegistration(
                UUID.randomUUID().toString(), SPI_NAME, url,
                "cred-ref", TENANT_ID, 5000,
                Map.of(), Instant.now(), Instant.now().plusSeconds(300),
                Instant.now());
    }
}
