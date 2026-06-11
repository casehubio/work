package io.casehub.work.ai;

import java.util.Set;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;

/** Minimal CurrentPrincipal for unit tests that construct stores outside CDI. */
public final class TestPrincipal implements CurrentPrincipal {

    public static final TestPrincipal DEFAULT = new TestPrincipal();

    @Override public String actorId() { return "test-user"; }
    @Override public Set<String> groups() { return Set.of(); }
    @Override public String tenancyId() { return TenancyConstants.DEFAULT_TENANT_ID; }
    @Override public boolean isCrossTenantAdmin() { return false; }
}
