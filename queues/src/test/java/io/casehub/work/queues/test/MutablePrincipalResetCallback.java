package io.casehub.work.queues.test;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

/**
 * Resets {@link MutableCurrentPrincipal} before every {@code @QuarkusTest} method.
 *
 * <p>Prevents tenant state from leaking between test classes.
 */
public class MutablePrincipalResetCallback implements QuarkusTestBeforeEachCallback {

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        var container = Arc.container();
        if (container != null) {
            var handle = container.instance(MutableCurrentPrincipal.class);
            if (handle.isAvailable()) {
                handle.get().reset();
            }
        }
    }
}
