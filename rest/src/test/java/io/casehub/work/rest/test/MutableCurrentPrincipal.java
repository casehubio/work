package io.casehub.work.rest.test;

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

    public void reset() {
        this.actorId = "test-user";
        this.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        this.crossTenantAdmin = false;
        this.groups = Set.of();
    }
}
