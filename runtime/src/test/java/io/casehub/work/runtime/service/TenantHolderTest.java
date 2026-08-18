package io.casehub.work.runtime.service;

import io.casehub.platform.api.identity.TenancyConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantHolderTest {

    private static TenantHolder holderWithDefault(final String configuredDefault) {
        final TenantHolder holder = new TenantHolder();
        holder.configuredDefaultTenancyId = configuredDefault;
        holder.init();
        return holder;
    }

    @Test
    void defaultTenancyId_usesConfiguredValue() {
        final TenantHolder holder = holderWithDefault("custom-tenant-id");
        assertThat(holder.getTenancyId()).isEqualTo("custom-tenant-id");
    }

    @Test
    void defaultTenancyId_fallsBackToConstant_whenNoConfigOverride() {
        final TenantHolder holder = holderWithDefault(TenancyConstants.DEFAULT_TENANT_ID);
        assertThat(holder.getTenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void setTenancyId_overridesDefault() {
        final TenantHolder holder = holderWithDefault("initial-tenant");
        holder.setTenancyId("overridden-tenant");
        assertThat(holder.getTenancyId()).isEqualTo("overridden-tenant");
    }
}
