package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestTransaction
class JpaLabelRuleStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    LabelRuleStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private LabelRuleEntity newRule(String name, boolean enabled) {
        LabelRuleEntity rule = new LabelRuleEntity();
        rule.name = name;
        rule.enabled = enabled;
        rule.conditionLanguage = "jexl";
        rule.conditionExpression = "true";
        rule.createdAt = Instant.now();
        return rule;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        LabelRuleEntity rule = newRule("rule-a", true);
        assertThat(rule.tenancyId).isNull();

        store.put(rule);

        assertThat(rule.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantRule() {
        principal.setTenancyId(TENANT_A);
        LabelRuleEntity rule = newRule("rule-a", true);
        store.put(rule);
        UUID id = rule.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void findEnabled_returnOnlyCurrentTenantRules() {
        principal.setTenancyId(TENANT_A);
        store.put(newRule("rule-a-enabled", true));
        store.put(newRule("rule-a-disabled", false));

        principal.setTenancyId(TENANT_B);
        store.put(newRule("rule-b-enabled", true));

        List<LabelRuleEntity> resultB = store.findEnabled();
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).tenancyId).isEqualTo(TENANT_B);
        assertThat(resultB.get(0).name).isEqualTo("rule-b-enabled");

        principal.setTenancyId(TENANT_A);
        List<LabelRuleEntity> resultA = store.findEnabled();
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).tenancyId).isEqualTo(TENANT_A);
        assertThat(resultA.get(0).name).isEqualTo("rule-a-enabled");
    }

    @Test
    void delete_cannotDeleteAnotherTenantRule() {
        principal.setTenancyId(TENANT_A);
        LabelRuleEntity rule = newRule("rule-a", true);
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
