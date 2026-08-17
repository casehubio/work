package io.casehub.work.runtime.callback;

import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.callback.CallbackInvoker;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * CDI decorator that routes {@link WorkerSelectionStrategy#select} to a remote callback
 * registration when present. Falls through to the config-selected local strategy when no
 * callbacks are registered. Metadata methods ({@link #id()}, {@link #triggers()}) always
 * delegate to the wrapped bean.
 */
@Decorator
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 100)
public class CallbackWorkerSelectionStrategyDecorator implements WorkerSelectionStrategy {

    private static final Logger LOG = Logger.getLogger(CallbackWorkerSelectionStrategyDecorator.class);
    private static final String SPI_NAME = "worker-selection-strategy";

    private final WorkerSelectionStrategy delegate;
    private final CallbackRegistry callbackRegistry;
    private final CallbackInvoker callbackInvoker;
    private final CurrentPrincipal currentPrincipal;

    @Inject
    CallbackWorkerSelectionStrategyDecorator(@Delegate final WorkerSelectionStrategy delegate,
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
    public Set<AssignmentTrigger> triggers() {
        return delegate.triggers();
    }

    @Override
    public AssignmentDecision select(final SelectionContext context,
                                     final List<WorkerCandidate> candidates) {
        final String tenancyId = currentPrincipal.tenancyId();
        final List<CallbackRegistration> registrations =
                callbackRegistry.findBySpi(SPI_NAME, tenancyId);

        if (registrations.isEmpty()) {
            return delegate.select(context, candidates);
        }

        final CallbackRegistration reg = registrations.get(0);
        try {
            final AssignmentDecision result = callbackInvoker.invoke(
                    reg, "select",
                    new Object[]{context, candidates},
                    AssignmentDecision.class);
            if (result != null) {
                return result;
            }
        } catch (final Exception e) {
            LOG.warnf(e, "Remote worker selection strategy at %s failed — falling back to local",
                    reg.callbackUrl());
        }
        return delegate.select(context, candidates);
    }
}
