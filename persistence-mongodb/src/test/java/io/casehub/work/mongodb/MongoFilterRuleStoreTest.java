package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.filter.FilterRule;
import io.casehub.work.runtime.repository.FilterRuleStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoFilterRuleStoreTest {

    @Inject
    FilterRuleStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoFilterRuleDocument.deleteAll();
    }

    // ── Put ───────────────────────────────────────────────────────────────────

    @Test
    void put_assignsIdAndTimestamps() {
        final FilterRule rule = rule("Test Rule", "test condition");
        assertThat(rule.id).isNull();
        assertThat(rule.createdAt).isNull();

        store.put(rule);

        assertThat(rule.id).isNotNull();
        assertThat(rule.createdAt).isNotNull();
    }

    @Test
    void put_and_get_roundtrip() {
        final FilterRule rule = rule("Roundtrip Rule", "x == 1");
        rule.description = "Test description";
        rule.enabled = true;
        rule.events = "ADD,UPDATE";
        rule.actionsJson = "[{\"type\":\"log\"}]";

        store.put(rule);
        final Optional<FilterRule> found = store.get(rule.id);

        assertThat(found).isPresent();
        final FilterRule loaded = found.get();
        assertThat(loaded.id).isEqualTo(rule.id);
        assertThat(loaded.name).isEqualTo("Roundtrip Rule");
        assertThat(loaded.description).isEqualTo("Test description");
        assertThat(loaded.enabled).isTrue();
        assertThat(loaded.condition).isEqualTo("x == 1");
        assertThat(loaded.events).isEqualTo("ADD,UPDATE");
        assertThat(loaded.actionsJson).isEqualTo("[{\"type\":\"log\"}]");
        assertThat(loaded.createdAt).isNotNull();
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    @Test
    void get_returnsEmpty_whenNotFound() {
        assertThat(store.get(UUID.randomUUID())).isEmpty();
    }

    // ── AllEnabled ────────────────────────────────────────────────────────────

    @Test
    void allEnabled_returnsOnlyEnabled() {
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant t3 = Instant.now();

        final FilterRule rule1 = rule("Enabled 1", "a == 1");
        rule1.enabled = true;
        rule1.createdAt = t1;
        store.put(rule1);

        final FilterRule rule2 = rule("Disabled", "b == 2");
        rule2.enabled = false;
        rule2.createdAt = t2;
        store.put(rule2);

        final FilterRule rule3 = rule("Enabled 2", "c == 3");
        rule3.enabled = true;
        rule3.createdAt = t3;
        store.put(rule3);

        final List<FilterRule> results = store.allEnabled();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).name).isEqualTo("Enabled 1");
        assertThat(results.get(1).name).isEqualTo("Enabled 2");
    }

    @Test
    void allEnabled_orderedByCreatedAt() {
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant t3 = Instant.now();

        final FilterRule rule1 = rule("First", "a == 1");
        rule1.createdAt = t1;
        store.put(rule1);

        final FilterRule rule3 = rule("Third", "c == 3");
        rule3.createdAt = t3;
        store.put(rule3);

        final FilterRule rule2 = rule("Second", "b == 2");
        rule2.createdAt = t2;
        store.put(rule2);

        final List<FilterRule> results = store.allEnabled();

        assertThat(results).hasSize(3);
        assertThat(results.get(0).name).isEqualTo("First");
        assertThat(results.get(1).name).isEqualTo("Second");
        assertThat(results.get(2).name).isEqualTo("Third");
    }

    // ── ScanAll ───────────────────────────────────────────────────────────────

    @Test
    void scanAll_returnsAll() {
        final FilterRule rule1 = rule("Enabled", "a == 1");
        rule1.enabled = true;
        store.put(rule1);

        final FilterRule rule2 = rule("Disabled", "b == 2");
        rule2.enabled = false;
        store.put(rule2);

        final List<FilterRule> results = store.scanAll();

        assertThat(results).hasSize(2);
        assertThat(results.stream().map(r -> r.name)).containsExactlyInAnyOrder("Enabled", "Disabled");
    }

    @Test
    void scanAll_orderedByCreatedAt() {
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);

        final FilterRule rule1 = rule("First", "a == 1");
        rule1.createdAt = t1;
        store.put(rule1);

        final FilterRule rule2 = rule("Second", "b == 2");
        rule2.createdAt = t2;
        store.put(rule2);

        final List<FilterRule> results = store.scanAll();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).name).isEqualTo("First");
        assertThat(results.get(1).name).isEqualTo("Second");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returnsTrue_whenExists() {
        final FilterRule rule = rule("To Delete", "x == 1");
        store.put(rule);

        final boolean deleted = store.delete(rule.id);

        assertThat(deleted).isTrue();
        assertThat(store.get(rule.id)).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenNotFound() {
        final boolean deleted = store.delete(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_ruleInvisibleToOtherTenant() {
        final FilterRule rule = rule("Tenant 1 Rule", "x == 1");
        store.put(rule);

        principal.setTenancyId("tenant-2");

        assertThat(store.get(rule.id)).isEmpty();
        assertThat(store.allEnabled()).isEmpty();
        assertThat(store.scanAll()).isEmpty();
        assertThat(store.delete(rule.id)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FilterRule rule(final String name, final String condition) {
        final FilterRule rule = new FilterRule();
        rule.name = name;
        rule.condition = condition;
        rule.enabled = true;
        rule.events = "ADD,UPDATE,REMOVE";
        rule.actionsJson = "[]";
        return rule;
    }
}
