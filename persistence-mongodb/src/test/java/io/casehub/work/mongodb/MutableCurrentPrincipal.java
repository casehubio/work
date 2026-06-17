package io.casehub.work.mongodb;

import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;

@ApplicationScoped
@Alternative
@Priority(100)
public class MutableCurrentPrincipal implements CurrentPrincipal {

    private String actorId = "test-user";
    private String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;

    @Override
    public String actorId() {
        return actorId;
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }

    @Override
    public String tenancyId() {
        return tenancyId;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }

    public void setTenancyId(String tenancyId) {
        this.tenancyId = tenancyId;
    }

    public void reset() {
        this.actorId = "test-user";
        this.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
    }
}
