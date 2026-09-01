package io.casehub.work.runtime.preferences;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.spi.SlaBreachPolicy;
import io.casehub.work.runtime.config.WorkItemsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreferenceSlaBreachPolicyDecoratorTest {

    private SlaBreachPolicy delegate;
    private WorkItemsConfig config;
    private PreferenceSlaBreachPolicyDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = mock(SlaBreachPolicy.class);
        when(delegate.id()).thenReturn("declarative");

        config = mock(WorkItemsConfig.class);
        when(config.defaultExpiryHours()).thenReturn(24);

        decorator = new PreferenceSlaBreachPolicyDecorator(delegate, config);
    }

    @Test
    void id_alwaysDelegates() {
        assertThat(decorator.id()).isEqualTo("declarative");
    }

    @Test
    void noPreference_delegates() {
        var ctx = ctx(BreachType.COMPLETION_EXPIRED, Map.of(), Set.of("ops"));
        var expected = new BreachDecision.Fail("fallback");
        when(delegate.onBreach(any())).thenReturn(expected);

        assertThat(decorator.onBreach(ctx)).isSameAs(expected);
    }

    @Test
    void completionPreference_returnsWithoutDelegating() {
        var ctx = ctx(BreachType.COMPLETION_EXPIRED,
                Map.of("casehub.work.sla.on-completion-expiry", "fail:tenant-reason"),
                Set.of("ops"));

        var result = decorator.onBreach(ctx);

        assertThat(result).isInstanceOf(BreachDecision.Fail.class);
        assertThat(((BreachDecision.Fail) result).reason()).isEqualTo("tenant-reason");
        verify(delegate, never()).onBreach(any());
    }

    @Test
    void claimPreference_routesToCorrectKey() {
        var ctx = ctx(BreachType.CLAIM_EXPIRED,
                Map.of("casehub.work.sla.on-claim-expiry", "exhausted:claim-timeout"),
                Set.of("ops"));

        var result = decorator.onBreach(ctx);

        assertThat(result).isInstanceOf(BreachDecision.Exhausted.class);
        verify(delegate, never()).onBreach(any());
    }

    @Test
    void completionPreference_doesNotMatchClaimExpiry() {
        var ctx = ctx(BreachType.CLAIM_EXPIRED,
                Map.of("casehub.work.sla.on-completion-expiry", "fail"),
                Set.of("ops"));
        var expected = new BreachDecision.Fail("fallback");
        when(delegate.onBreach(any())).thenReturn(expected);

        assertThat(decorator.onBreach(ctx)).isSameAs(expected);
    }

    @Test
    void extendAction_usesExtensionHoursPreference() {
        var ctx = ctx(BreachType.COMPLETION_EXPIRED,
                Map.of("casehub.work.sla.on-completion-expiry", "extend",
                       "casehub.work.sla.extension-hours", "8"),
                Set.of("ops"));

        var result = decorator.onBreach(ctx);

        assertThat(result).isInstanceOf(BreachDecision.Extend.class);
        assertThat(((BreachDecision.Extend) result).by()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void extendAction_fallsBackToConfigDefaultExpiryHours() {
        when(config.defaultExpiryHours()).thenReturn(12);
        var ctx = ctx(BreachType.COMPLETION_EXPIRED,
                Map.of("casehub.work.sla.on-completion-expiry", "extend"),
                Set.of("ops"));

        var result = decorator.onBreach(ctx);

        assertThat(result).isInstanceOf(BreachDecision.Extend.class);
        assertThat(((BreachDecision.Extend) result).by()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void claimExtensionHours_takesPrecedenceOverExtensionHours() {
        var ctx = ctx(BreachType.CLAIM_EXPIRED,
                Map.of("casehub.work.sla.on-claim-expiry", "extend",
                       "casehub.work.sla.extension-hours", "4",
                       "casehub.work.sla.claim-extension-hours", "8"),
                Set.of("ops"));

        var result = decorator.onBreach(ctx);

        assertThat(result).isInstanceOf(BreachDecision.Extend.class);
        assertThat(((BreachDecision.Extend) result).by()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void selfEscalation_delegates() {
        var ctx = ctx(BreachType.COMPLETION_EXPIRED,
                Map.of("casehub.work.sla.on-completion-expiry", "escalateTo:team-leads"),
                Set.of("team-leads"));
        var expected = new BreachDecision.Fail("fallback");
        when(delegate.onBreach(any())).thenReturn(expected);

        assertThat(decorator.onBreach(ctx)).isSameAs(expected);
    }

    @Test
    void escalateToNewGroup_works() {
        var ctx = ctx(BreachType.COMPLETION_EXPIRED,
                Map.of("casehub.work.sla.on-completion-expiry", "escalateTo:managers:PT2H"),
                Set.of("team-leads"));

        var result = decorator.onBreach(ctx);

        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        var esc = (BreachDecision.EscalateTo) result;
        assertThat(esc.groups()).containsExactly("managers");
        assertThat(esc.deadline()).isEqualTo(Duration.ofHours(2));
        verify(delegate, never()).onBreach(any());
    }

    @Test
    void malformedPreference_logsAndDelegates() {
        var ctx = ctx(BreachType.COMPLETION_EXPIRED,
                Map.of("casehub.work.sla.on-completion-expiry", "invalid-action"),
                Set.of("ops"));
        var expected = new BreachDecision.Fail("fallback");
        when(delegate.onBreach(any())).thenReturn(expected);

        assertThat(decorator.onBreach(ctx)).isSameAs(expected);
    }

    @Test
    void worksWithNonDeclarativeDelegate() {
        SlaBreachPolicy customDelegate = mock(SlaBreachPolicy.class);
        when(customDelegate.id()).thenReturn("custom-policy");
        when(customDelegate.onBreach(any())).thenReturn(new BreachDecision.Fail("custom"));
        var customDecorator = new PreferenceSlaBreachPolicyDecorator(customDelegate, config);

        assertThat(customDecorator.id()).isEqualTo("custom-policy");
        var ctx = ctx(BreachType.COMPLETION_EXPIRED, Map.of(), Set.of("ops"));
        assertThat(customDecorator.onBreach(ctx)).isInstanceOf(BreachDecision.Fail.class);
    }

    private static SlaBreachContext ctx(BreachType type, Map<String, Object> prefValues,
                                         Set<String> candidateGroups) {
        var task = new BreachedTask(UUID.randomUUID(), null, "test-item", candidateGroups);
        return new SlaBreachContext(type, task, Path.parse("casehubio/clinical"),
                new MapPreferences(prefValues));
    }
}
