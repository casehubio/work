package io.casehub.work.runtime.preferences;

import io.casehub.platform.api.preferences.IntPreference;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.spi.SlaBreachPolicy;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.service.BreachAction;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@Decorator
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 200)
public class PreferenceSlaBreachPolicyDecorator implements SlaBreachPolicy {

    private static final Logger LOG = Logger.getLogger(PreferenceSlaBreachPolicyDecorator.class);

    private final SlaBreachPolicy delegate;
    private final WorkItemsConfig config;

    @Inject
    PreferenceSlaBreachPolicyDecorator(@Delegate final SlaBreachPolicy delegate,
                                       final WorkItemsConfig config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public BreachDecision onBreach(final SlaBreachContext context) {
        final Preferences prefs = context.preferences();

        final PreferenceKey<BreachActionPreference> actionKey = switch (context.breachType()) {
            case COMPLETION_EXPIRED -> WorkPreferenceKeys.SLA_ON_COMPLETION_EXPIRY;
            case CLAIM_EXPIRED -> WorkPreferenceKeys.SLA_ON_CLAIM_EXPIRY;
        };

        final BreachActionPreference actionPref;
        try {
            actionPref = prefs.get(actionKey);
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to parse SLA preference for %s — falling back to policy",
                      actionKey.qualifiedName());
            return delegate.onBreach(context);
        }
        if (actionPref == null) {
            return delegate.onBreach(context);
        }

        if (actionPref.action() instanceof BreachAction.EscalateToAction esc
                && context.task().candidateGroups().contains(esc.group())) {
            return delegate.onBreach(context);
        }

        final Integer extensionHours = resolveExtensionHours(prefs, context.breachType());
        return actionPref.action().toBreachDecision(extensionHours, config.defaultExpiryHours());
    }

    private Integer resolveExtensionHours(Preferences prefs, BreachType type) {
        if (type == BreachType.CLAIM_EXPIRED) {
            IntPreference claimExt = prefs.get(WorkPreferenceKeys.SLA_CLAIM_EXTENSION_HOURS);
            if (claimExt != null) return claimExt.value();
        }
        IntPreference ext = prefs.get(WorkPreferenceKeys.SLA_EXTENSION_HOURS);
        return ext != null ? ext.value() : null;
    }
}
