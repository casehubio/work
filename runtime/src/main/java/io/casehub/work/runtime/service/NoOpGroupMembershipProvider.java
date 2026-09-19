package io.casehub.work.runtime.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.arc.DefaultBean;

/**
 * Default {@link GroupMembershipProvider} — returns an empty set for every group.
 * Activated via {@code @DefaultBean}; any consumer-supplied implementation
 * (LDAP, Keycloak, SCIM, etc.) automatically overrides this.
 *
 * <p>The platform also ships a {@code @DefaultBean} mock in casehub-platform-core.
 * The deployment processor ({@code WorkItemsProcessor}) excludes it at build time
 * to avoid two-default ambiguity.
 */
@ApplicationScoped
@DefaultBean
public class NoOpGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(final String groupName, final String tenancyId) {
        return Set.of();
    }
}
