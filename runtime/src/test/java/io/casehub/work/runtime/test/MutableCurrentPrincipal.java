package io.casehub.work.runtime.test;

import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;

/**
 * Test-only {@link CurrentPrincipal} with mutable state.
 *
 * <p>{@code @Alternative @Priority(100)} so it wins over
 * {@code MockCurrentPrincipal} ({@code @DefaultBean}) and
 * {@code TenantScopedPrincipal} (normal-priority) in test contexts.
 * Tests that exercise {@link io.casehub.work.runtime.service.TenantContextRunner}
 * should exclude this bean (see {@code TenantContextRunnerTest}).
 *
 * <p>Call {@link #reset()} in {@code @BeforeEach} to avoid state leaking
 * between test methods.
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class MutableCurrentPrincipal implements CurrentPrincipal {

    private String actorId = "test-user";
    private String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
    private boolean crossTenantAdmin = false;
    private Set<String> groups = Set.of();

    @Override
    public String actorId() {
        return actorId;
    }

    @Override
    public Set<String> groups() {
        return groups;
    }

    @Override
    public String tenancyId() {
        return tenancyId;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return crossTenantAdmin;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public void setTenancyId(String tenancyId) {
        this.tenancyId = tenancyId;
    }

    public void setCrossTenantAdmin(boolean crossTenantAdmin) {
        this.crossTenantAdmin = crossTenantAdmin;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    /**
     * Reset to defaults.  Call from {@code @BeforeEach}.
     */
    public void reset() {
        this.actorId = "test-user";
        this.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        this.crossTenantAdmin = false;
        this.groups = Set.of();
    }
}
