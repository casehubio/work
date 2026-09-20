package io.casehub.work.flow;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@ApplicationScoped
public class TestGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        return Set.of();
    }
}
