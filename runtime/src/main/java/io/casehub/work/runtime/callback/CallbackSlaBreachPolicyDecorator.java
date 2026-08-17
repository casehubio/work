package io.casehub.work.runtime.callback;

import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.callback.CallbackInvoker;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.spi.SlaBreachPolicy;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * CDI decorator that routes {@link SlaBreachPolicy#onBreach} to a remote callback registration
 * when present. Falls through to the config-selected local policy when no callbacks are registered.
 * Metadata methods ({@link #id()}) always delegate to the wrapped bean.
 */
@Decorator
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 100)
public class CallbackSlaBreachPolicyDecorator implements SlaBreachPolicy {

    private static final Logger LOG = Logger.getLogger(CallbackSlaBreachPolicyDecorator.class);
    private static final String SPI_NAME = "sla-breach-policy";

    private final SlaBreachPolicy delegate;
    private final CallbackRegistry callbackRegistry;
    private final CallbackInvoker callbackInvoker;
    private final CurrentPrincipal currentPrincipal;

    @Inject
    CallbackSlaBreachPolicyDecorator(@Delegate final SlaBreachPolicy delegate,
                                     final CallbackRegistry callbackRegistry,
                                     final CallbackInvoker callbackInvoker,
                                     final CurrentPrincipal currentPrincipal) {
        this.delegate = delegate;
        this.callbackRegistry = callbackRegistry;
        this.callbackInvoker = callbackInvoker;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public BreachDecision onBreach(final SlaBreachContext context) {
        final String tenancyId = currentPrincipal.tenancyId();
        final List<CallbackRegistration> registrations =
                callbackRegistry.findBySpi(SPI_NAME, tenancyId);

        if (registrations.isEmpty()) {
            return delegate.onBreach(context);
        }

        final CallbackRegistration reg = registrations.get(0);
        try {
            final BreachDecision result = callbackInvoker.invoke(
                    reg, "onBreach",
                    new Object[]{context},
                    BreachDecision.class);
            if (result != null) {
                return result;
            }
        } catch (final Exception e) {
            LOG.warnf(e, "Remote SLA breach policy at %s failed — falling back to local",
                    reg.callbackUrl());
        }
        return delegate.onBreach(context);
    }
}
