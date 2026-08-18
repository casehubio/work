package io.casehub.work.runtime.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.Unremovable;

import io.casehub.platform.api.identity.TenancyConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Request-scoped holder for the active tenant identity.
 *
 * <p>In REST contexts, defaults match {@code MockCurrentPrincipal} from
 * casehub-platform (actorId = "system", tenancyId = default tenant UUID).
 * {@link TenantContextRunner} overrides these values when it activates a
 * programmatic request context for background/async work.
 */
@RequestScoped
@Unremovable
public class TenantHolder {

    @Inject
    @ConfigProperty(name = "casehub.tenancy.default-id",
                    defaultValue = TenancyConstants.DEFAULT_TENANT_ID)
    String configuredDefaultTenancyId;

    private String tenancyId;
    private String actorId = "system";

    @PostConstruct
    void init() {
        tenancyId = configuredDefaultTenancyId;
    }

    public String getTenancyId() {
        return tenancyId;
    }

    public void setTenancyId(String tenancyId) {
        this.tenancyId = tenancyId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }
}
