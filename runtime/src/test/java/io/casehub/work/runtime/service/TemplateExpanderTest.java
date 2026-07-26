package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.platform.api.identity.GroupMember;
import org.junit.jupiter.api.Test;

import java.util.Set;

class TemplateExpanderTest {

    private static final String TENANT = "test-tenant";

    // No-op provider: every group is unknown
    private static final io.casehub.platform.api.identity.GroupMembershipProvider EMPTY =
            (groupName, tenancyId) -> Set.of();

    // Provider that knows "legal-team" → {alice, bob}
    private static final io.casehub.platform.api.identity.GroupMembershipProvider LEGAL_TEAM =
            (groupName, tenancyId) -> "legal-team".equals(groupName)
                    ? Set.of(new GroupMember("alice", "Alice"), new GroupMember("bob", "Bob"))
                    : Set.of();

    @Test
    void merge_nullGroups_returnsExcludedUsersUnchanged() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers(null, "alice,bob", EMPTY, TENANT))
                .isEqualTo("alice,bob");
    }

    @Test
    void merge_blankGroups_returnsExcludedUsersUnchanged() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers("  ", "alice", EMPTY, TENANT))
                .isEqualTo("alice");
    }

    @Test
    void merge_nullGroupsAndNullUsers_returnsNull() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers(null, null, EMPTY, TENANT)).isNull();
    }

    @Test
    void merge_groupResolvesToMembers_membersAddedToUsers() {
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("legal-team", null, LEGAL_TEAM, TENANT);
        assertThat(result).contains("alice");
        assertThat(result).contains("bob");
    }

    @Test
    void merge_existingUsersPreserved_groupMembersAdded() {
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("legal-team", "carol", LEGAL_TEAM, TENANT);
        assertThat(result).contains("carol");
        assertThat(result).contains("alice");
        assertThat(result).contains("bob");
    }

    @Test
    void merge_groupMemberAlreadyInExcludedUsers_noDuplicate() {
        // alice is both in excludedUsers and in legal-team
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("legal-team", "alice", LEGAL_TEAM, TENANT);
        long aliceCount = java.util.Arrays.stream(result.split(","))
                .filter("alice"::equals).count();
        assertThat(aliceCount).isEqualTo(1);
    }

    @Test
    void merge_unknownGroup_returnsExcludedUsersUnchanged() {
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("unknown-group", "alice", EMPTY, TENANT);
        assertThat(result).isEqualTo("alice");
    }

    @Test
    void merge_unknownGroupAndNullUsers_returnsNull() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers("unknown-group", null, EMPTY, TENANT)).isNull();
    }

    @Test
    void merge_multipleGroupsCsv_allExpanded() {
        io.casehub.platform.api.identity.GroupMembershipProvider provider = (groupName, tenancyId) ->
                switch (groupName) {
                    case "team-a" -> Set.of(new GroupMember("alice", "Alice"));
                    case "team-b" -> Set.of(new GroupMember("bob", "Bob"));
                    default -> Set.of();
                };
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("team-a,team-b", null, provider, TENANT);
        assertThat(result).contains("alice");
        assertThat(result).contains("bob");
    }
}
