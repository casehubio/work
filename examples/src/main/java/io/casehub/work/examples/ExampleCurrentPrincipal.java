package io.casehub.work.examples;

import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;

/**
 * Permissive {@link CurrentPrincipal} for example scenarios.
 *
 * <p>All scenario actors are members of every candidate group so that
 * {@code checkCandidateEligibility} in {@code WorkItemService.claim()}
 * never rejects a claim on group-membership grounds. Priority 300 beats
 * {@code TenantScopedPrincipal} (150) and {@code MutableCurrentPrincipal} (200).
 */
@RequestScoped
@Alternative
@Priority(300)
public class ExampleCurrentPrincipal implements CurrentPrincipal {

    private static final Set<String> ALL_GROUPS = Set.of(
            "finance-team", "compliance-team", "credit-team",
            "surveyor-team", "underwriting-team", "content-moderators",
            "legal-team", "doc-reviewers", "clinic-reception", "clinic-admin",
            "approvers", "reviewers", "legal-reviewer");

    @Override
    public String actorId() {
        return "example-user";
    }

    @Override
    public Set<String> groups() {
        return ALL_GROUPS;
    }

    @Override
    public String tenancyId() {
        return TenancyConstants.DEFAULT_TENANT_ID;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }
}
