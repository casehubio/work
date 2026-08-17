package io.casehub.work.runtime.callback;

import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.callback.CallbackInvoker;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.spi.SlaBreachPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackSlaBreachPolicyDecoratorTest {

    private SlaBreachPolicy delegate;
    private CallbackRegistry callbackRegistry;
    private CallbackInvoker callbackInvoker;
    private CurrentPrincipal currentPrincipal;
    private CallbackSlaBreachPolicyDecorator decorator;

    private static final String TENANT_ID = "tenant-1";
    private static final String SPI_NAME = "sla-breach-policy";

    @BeforeEach
    void setUp() {
        delegate = mock(SlaBreachPolicy.class);
        callbackRegistry = mock(CallbackRegistry.class);
        callbackInvoker = mock(CallbackInvoker.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        when(currentPrincipal.tenancyId()).thenReturn(TENANT_ID);
        when(delegate.id()).thenReturn("no-op");

        decorator = new CallbackSlaBreachPolicyDecorator(
                delegate, callbackRegistry, callbackInvoker, currentPrincipal);
    }

    @Test
    void id_alwaysDelegatesToWrappedBean() {
        assertThat(decorator.id()).isEqualTo("no-op");
    }

    @Test
    void onBreach_noRegistrations_delegatesToLocal() {
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of());
        final BreachDecision expected = new BreachDecision.Fail("local-reason");
        when(delegate.onBreach(any())).thenReturn(expected);

        final BreachDecision result = decorator.onBreach(testContext());

        assertThat(result).isSameAs(expected);
        verify(callbackInvoker, never()).invoke(any(), any(), any(), any());
    }

    @Test
    void onBreach_withRegistration_invokesRemote() {
        final CallbackRegistration reg = registration("http://app1/callback");
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of(reg));
        final BreachDecision remote = new BreachDecision.EscalateTo(
                java.util.Set.of("managers"), Duration.ofHours(2));
        when(callbackInvoker.invoke(eq(reg), eq("onBreach"), any(), eq(BreachDecision.class)))
                .thenReturn(remote);

        final BreachDecision result = decorator.onBreach(testContext());

        assertThat(result).isSameAs(remote);
        verify(delegate, never()).onBreach(any());
    }

    @Test
    void onBreach_remoteReturnsNull_fallsBackToDelegate() {
        final CallbackRegistration reg = registration("http://app1/callback");
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of(reg));
        when(callbackInvoker.invoke(eq(reg), eq("onBreach"), any(), eq(BreachDecision.class)))
                .thenReturn(null);
        final BreachDecision local = new BreachDecision.Fail("fallback");
        when(delegate.onBreach(any())).thenReturn(local);

        final BreachDecision result = decorator.onBreach(testContext());

        assertThat(result).isSameAs(local);
    }

    @Test
    void onBreach_remoteThrows_fallsBackToDelegate() {
        final CallbackRegistration reg = registration("http://app1/callback");
        when(callbackRegistry.findBySpi(SPI_NAME, TENANT_ID)).thenReturn(List.of(reg));
        when(callbackInvoker.invoke(eq(reg), eq("onBreach"), any(), eq(BreachDecision.class)))
                .thenThrow(new RuntimeException("timeout"));
        final BreachDecision local = new BreachDecision.Fail("fallback");
        when(delegate.onBreach(any())).thenReturn(local);

        final BreachDecision result = decorator.onBreach(testContext());

        assertThat(result).isSameAs(local);
    }

    private static SlaBreachContext testContext() {
        final BreachedTask task = mock(BreachedTask.class);
        return new SlaBreachContext(BreachType.COMPLETION_EXPIRED, task,
                                    Path.root(), new MapPreferences(Map.of()));}

    private static CallbackRegistration registration(final String url) {
        return new CallbackRegistration(
                UUID.randomUUID().toString(), SPI_NAME, url,
                "cred-ref", TENANT_ID, 5000,
                Map.of(), Instant.now(), Instant.now().plusSeconds(300),
                Instant.now());
    }
}
