package io.casehub.work.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.work.notifications.model.WorkItemNotificationRule;

/**
 * Unit tests for rule matching logic — no CDI, no DB.
 */
class NotificationDispatcherTest {

    @Test
    void matchesEventType_true_whenEventTypeInList() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED,COMPLETED", null);
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", null)).isTrue();
        assertThat(NotificationDispatcher.matches(rule, "COMPLETED", null)).isTrue();
    }

    @Test
    void matchesEventType_false_whenEventTypeNotInList() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", null);
        assertThat(NotificationDispatcher.matches(rule, "EXPIRED", null)).isFalse();
    }

    @Test
    void matchesTypes_true_whenRuleTypesIsNull() {
        // null types = wildcard — matches any types
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", null);
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("loan"))).isTrue();
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("legal"))).isTrue();
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of())).isTrue();
    }

    @Test
    void matchesTypes_true_whenTypesMatch() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", "loan-application");
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("loan-application"))).isTrue();
    }

    @Test
    void matchesTypes_false_whenTypesDiffer() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", "loan-application");
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("legal"))).isFalse();
    }

    @Test
    void matchesTypes_true_whenWorkItemTypeIsDescendantOfRuleType() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", "legal");
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("legal/contract"))).isTrue();
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("legal/nda/renewal"))).isTrue();
    }

    @Test
    void matchesTypes_false_whenRuleTypeIsDeeperThanWorkItemType() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", "legal/contract");
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("legal"))).isFalse();
    }

    @Test
    void matchesTypes_false_whenRuleTypeIsPrefixButNotAncestor() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", "leg");
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of("legal"))).isFalse();
    }

    @Test
    void matchesTypes_false_whenWorkItemTypesEmptyButRuleHasTypes() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", "loan-application");
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of())).isFalse();
    }

    @Test
    void disabledRule_neverMatches() {
        final WorkItemNotificationRule rule = ruleFor("ASSIGNED", null);
        rule.enabled = false;
        assertThat(NotificationDispatcher.matches(rule, "ASSIGNED", java.util.List.of())).isFalse();
    }

    @Test
    void filterMatchingRules_returnsOnlyMatches() {
        final WorkItemNotificationRule r1 = ruleFor("ASSIGNED", null);
        final WorkItemNotificationRule r2 = ruleFor("EXPIRED", "loan");
        final WorkItemNotificationRule r3 = ruleFor("ASSIGNED,COMPLETED", "loan");
        final List<WorkItemNotificationRule> rules = List.of(r1, r2, r3);

        final List<WorkItemNotificationRule> matched = NotificationDispatcher.filterMatching(rules, "ASSIGNED", java.util.List.of("loan"));
        assertThat(matched).containsExactlyInAnyOrder(r1, r3);
    }

    @Test
    void filterMatchingRules_emptyList_returnsEmpty() {
        assertThat(NotificationDispatcher.filterMatching(List.of(), "ASSIGNED", java.util.List.of("loan")))
                .isEmpty();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static WorkItemNotificationRule ruleFor(final String eventTypes, final String types) {
        final WorkItemNotificationRule rule = new WorkItemNotificationRule();
        rule.id = UUID.randomUUID();
        rule.channelType = "test";
        rule.targetUrl = "https://example.com/hook";
        rule.eventTypes = eventTypes;
        rule.types = types;
        rule.enabled = true;
        return rule;
    }
}
