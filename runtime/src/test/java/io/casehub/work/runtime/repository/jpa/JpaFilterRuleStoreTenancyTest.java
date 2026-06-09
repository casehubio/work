package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.filter.FilterRule;
import io.casehub.work.runtime.repository.FilterRuleStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaFilterRuleStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaFilterRuleStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    FilterRuleStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private FilterRule newRule(String name, boolean enabled) {
        FilterRule rule = new FilterRule();
        rule.name = name;
        rule.enabled = enabled;
        rule.condition = "true";
        rule.createdAt = Instant.now();
        return rule;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        FilterRule rule = newRule("rule-a", true);
        assertThat(rule.tenancyId).isNull();

        store.put(rule);

        assertThat(rule.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantRule() {
        principal.setTenancyId(TENANT_A);
        FilterRule rule = newRule("rule-a", true);
        store.put(rule);
        UUID id = rule.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void allEnabled_returnOnlyCurrentTenantRules() {
        // Tenant A: create enabled rule
        principal.setTenancyId(TENANT_A);
        store.put(newRule("rule-a-enabled", true));
        store.put(newRule("rule-a-disabled", false));

        // Tenant B: create enabled rule
        principal.setTenancyId(TENANT_B);
        store.put(newRule("rule-b-enabled", true));

        // As tenant B, only see B's enabled rule
        List<FilterRule> resultB = store.allEnabled();
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).tenancyId).isEqualTo(TENANT_B);
        assertThat(resultB.get(0).name).isEqualTo("rule-b-enabled");

        // As tenant A, only see A's enabled rule (not the disabled one)
        principal.setTenancyId(TENANT_A);
        List<FilterRule> resultA = store.allEnabled();
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).tenancyId).isEqualTo(TENANT_A);
        assertThat(resultA.get(0).name).isEqualTo("rule-a-enabled");
    }

    @Test
    void delete_cannotDeleteAnotherTenantRule() {
        principal.setTenancyId(TENANT_A);
        FilterRule rule = newRule("rule-a", true);
        store.put(rule);
        UUID id = rule.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}
