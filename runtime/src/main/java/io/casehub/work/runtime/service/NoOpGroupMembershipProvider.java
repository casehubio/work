package io.casehub.work.runtime.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
/**
 * Default {@link GroupMembershipProvider} — returns an empty set for every group.
 * Replace with {@code @Alternative @Priority(1)} to connect a real directory
 * (LDAP, Keycloak, SCIM, etc.).
 *
 * <p>Not {@code @DefaultBean} — must beat platform-supplied default providers
 * ({@code MockGroupMembershipProvider} from casehub-platform-core) that are also
 * {@code @DefaultBean}. Two {@code @DefaultBean} beans cause ambiguity; a normal
 * bean wins outright.
 */
@ApplicationScoped
public class NoOpGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(final String groupName, final String tenancyId) {
        return Set.of();
    }
}
