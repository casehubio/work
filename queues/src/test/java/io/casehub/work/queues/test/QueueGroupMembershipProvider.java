package io.casehub.work.queues.test;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;

/** Resolves the two default group providers on the queues test classpath. */
@ApplicationScoped
public class QueueGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(final String groupName, final String tenancyId) {
        return Set.of();
    }
}
