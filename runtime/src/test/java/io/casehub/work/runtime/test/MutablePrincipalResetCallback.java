package io.casehub.work.runtime.test;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

/**
 * Resets {@link MutableCurrentPrincipal} before every {@code @QuarkusTest} method.
 *
 * <p>Prevents tenant state from leaking between test classes — the
 * {@code @ApplicationScoped} principal survives across test classes within
 * a single Quarkus test application restart, so a tenancy test that sets
 * {@code tenancyId = TENANT_B} would corrupt subsequent tests that expect
 * the default tenant.
 *
 * <p>Registered via ServiceLoader at
 * {@code META-INF/services/io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback}.
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
