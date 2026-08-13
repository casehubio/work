package io.casehub.work.memory;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link WorkItemStore} for ephemeral deployments
 * and tests. No datasource or Flyway configuration required.
 *
 * <p>
 * Tier 3 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * both JPA (Tier 1) and MongoDB (Tier 2) when on the classpath.
 *
 * <p>
 * Thread-safe. Data is ephemeral (lost on restart). Objects returned from the
 * store are shared references — concurrent field-level mutations to the same
 * object without calling {@link #put} are not guaranteed to be visible across
 * threads.
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class InMemoryWorkItemStore implements WorkItemStore {

    private final Map<UUID, WorkItem> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** CDI no-arg constructor. */
    public InMemoryWorkItemStore() {}

    /** Constructor for unit tests outside CDI. */
    public InMemoryWorkItemStore(CurrentPrincipal currentPrincipal) {
        this.currentPrincipal = currentPrincipal;
    }

    /** Removes all stored WorkItems. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItem put(final WorkItem workItem) {
        WorkItem stored = workItem;
        if (stored.id() == null) {
            stored = stored.toBuilder().id(UUID.randomUUID()).build();
        }
        if (stored.tenancyId() == null) {
            stored = stored.toBuilder().tenancyId(currentPrincipal.tenancyId()).build();
        }
        store.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<WorkItem> get(final UUID id) {
        final WorkItem item = store.get(id);
        if (item != null && currentPrincipal.tenancyId().equals(item.tenancyId())) {
            return Optional.of(item);
        }
        return Optional.empty();
    }

    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        final String tenancyId = query.tenancyId() != null ? query.tenancyId() : currentPrincipal.tenancyId();
        return store.values().stream()
                    .filter(wi -> tenancyId.equals(wi.tenancyId()))
                    .filter(wi -> matchesAssignment(wi, query))
                    .filter(wi -> matchesFilters(wi, query))
                    .toList();
    }

    /** Returns a copy of all stored items, for test inspection and administrative use. */
    public List<WorkItem> findAll() {
        return new ArrayList<>(store.values());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean matchesAssignment(final WorkItem wi, final WorkItemQuery q) {
        if (q.assigneeId() == null && (q.candidateGroups() == null || q.candidateGroups().isEmpty())
            && q.candidateUserId() == null) {
            return true;
        }
        if (q.assigneeId() != null && q.assigneeId().equals(wi.assigneeId())) {
            return true;
        }
        if (q.candidateUserId() != null && q.candidateUserId().equals(wi.assigneeId())) {
            return true;
        }
        if (q.assigneeId() != null && wi.candidateUsers() != null && containsToken(wi.candidateUsers(), q.assigneeId())) {
            return true;
        }
        if (q.candidateUserId() != null && wi.candidateUsers() != null
            && containsToken(wi.candidateUsers(), q.candidateUserId())) {
            return true;
        }
        if (q.candidateGroups() != null && wi.candidateGroups() != null) {
            for (final String g : q.candidateGroups()) {
                if (containsToken(wi.candidateGroups(), g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesFilters(final WorkItem wi, final WorkItemQuery q) {
        if (q.status() != null && wi.status() != q.status()) {
            return false;
        }
        if (q.statusIn() != null && !q.statusIn().contains(wi.status())) {
            return false;
        }
        if (q.priority() != null && wi.priority() != q.priority()) {
            return false;
        }
        if (q.type() != null) {
            final io.casehub.platform.api.path.Path queryPath = io.casehub.platform.api.path.Path.parse(q.type());
            boolean matched = wi.types().stream().anyMatch(t -> {
                final io.casehub.platform.api.path.Path typePath = io.casehub.platform.api.path.Path.parse(t);
                return typePath.equals(queryPath) || queryPath.isAncestorOf(typePath);
            });
            if (!matched) {return false;}
        }
        if (q.outcome() != null && !q.outcome().equals(wi.outcome())) {
            return false;
        }
        if (q.followUpBefore() != null && (wi.followUpDate() == null || wi.followUpDate().isAfter(q.followUpBefore()))) {
            return false;
        }
        if (q.expiresAtOrBefore() != null && (wi.expiresAt() == null || wi.expiresAt().isAfter(q.expiresAtOrBefore()))) {
            return false;
        }
        if (q.claimDeadlineOrBefore() != null
            && (wi.claimDeadline() == null || wi.claimDeadline().isAfter(q.claimDeadlineOrBefore()))) {
            return false;
        }
        if (q.labelPattern() != null) {
            final boolean matchesLabel = wi.labels() != null && wi.labels().stream()
                                                                  .anyMatch(l -> io.casehub.work.api.LabelPatternMatcher
                                                                                         .matchesPattern(q.labelPattern(), l.path()));
            if (!matchesLabel) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if {@code csv} contains {@code token} as an exact
     * comma-separated element (after trimming whitespace). Avoids substring
     * false-positives — e.g. {@code "bob"} does NOT match {@code "bobby"}.
     *
     * @param csv comma-separated string to search
     * @param token the exact token to look for
     * @return {@code true} if the token appears as a discrete element
     */
    private boolean containsToken(final String csv, final String token) {
        for (final String element : csv.split(",")) {
            if (element.trim().equals(token)) {
                return true;
            }
        }
        return false;
    }
}
