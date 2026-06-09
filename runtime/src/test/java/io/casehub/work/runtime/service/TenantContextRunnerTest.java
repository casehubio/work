package io.casehub.work.runtime.service;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link TenantContextRunner} establishes a request context
 * whose {@link CurrentPrincipal} returns the given tenancy identity.
 *
 * <p>Uses a test profile that excludes {@code MutableCurrentPrincipal}
 * (the {@code @Alternative @Priority(100)} test bean) so that
 * {@link TenantScopedPrincipal} — a normal-priority request-scoped bean —
 * wins the CDI resolution inside the runner-activated context.
 */
@QuarkusTest
@TestProfile(TenantContextRunnerTest.Profile.class)
class TenantContextRunnerTest {

    @Inject
    TenantContextRunner tenantContextRunner;

    @Test
    void runInTenantContext_establishes_principal_with_given_tenancyId() {
        var result = new String[1];
        tenantContextRunner.runInTenantContext("test-tenant-abc", () -> {
            CurrentPrincipal principal = Arc.container()
                    .instance(CurrentPrincipal.class).get();
            result[0] = principal.tenancyId();
        });
        assertThat(result[0]).isEqualTo("test-tenant-abc");
    }

    @Test
    void runInTenantContext_principal_is_not_cross_tenant_admin() {
        var result = new boolean[1];
        tenantContextRunner.runInTenantContext("test-tenant-xyz", () -> {
            CurrentPrincipal principal = Arc.container()
                    .instance(CurrentPrincipal.class).get();
            result[0] = principal.isCrossTenantAdmin();
        });
        assertThat(result[0]).isFalse();
    }

    @Test
    void runInTenantContext_with_actor_id() {
        var result = new String[2];
        tenantContextRunner.runInTenantContext("tenant-42", "worker-bot", () -> {
            CurrentPrincipal principal = Arc.container()
                    .instance(CurrentPrincipal.class).get();
            result[0] = principal.tenancyId();
            result[1] = principal.actorId();
        });
        assertThat(result[0]).isEqualTo("tenant-42");
        assertThat(result[1]).isEqualTo("worker-bot");
    }

    /**
     * Test profile that excludes {@code MutableCurrentPrincipal} so
     * {@link TenantScopedPrincipal} is the resolved {@code CurrentPrincipal}.
     */
    public static class Profile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.arc.exclude-types",
                    "io.casehub.platform.mock.MockGroupMembershipProvider,"
                            + "io.casehub.work.runtime.test.MutableCurrentPrincipal");
        }
    }
}
