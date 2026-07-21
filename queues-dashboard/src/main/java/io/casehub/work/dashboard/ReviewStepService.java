package io.casehub.work.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.platform.api.path.Path;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.LabelRuleStore;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;

@ApplicationScoped
public class ReviewStepService {

    @Inject
    WorkItemService workItemService;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    Instance<LabelRule> cdiRules;

    @Inject
    LabelRuleStore labelRuleStore;

    @Inject
    CurrentPrincipal currentPrincipal;

    private final AtomicInteger         step           = new AtomicInteger(0);
    private final AtomicReference<UUID> advisoryId     = new AtomicReference<>();
    private final AtomicReference<UUID> releaseNotesId = new AtomicReference<>();
    private final AtomicReference<UUID> tutorialId     = new AtomicReference<>();

    public record StepResult(int step, String action, String detail, List<String> hints) {
    }

    public int cdiRuleCount() {
        int count = 0;
        for (@SuppressWarnings("unused") final LabelRule r : cdiRules) {
            count++;
        }
        return count;
    }

    public List<String> cdiRuleNames() {
        final List<String> names = new ArrayList<>();
        cdiRules.forEach(r -> names.add(r.name()));
        return names;
    }

    public int currentStep() {
        return step.get();
    }

    public String nextAction() {
        return switch (step.get()) {
            case 0 -> "Press 's': create 3 documents + setup filters";
            case 1 -> "Press 's': claim the security advisory";
            case 2 -> "Press 's': start the security advisory";
            case 3 -> "Press 's': complete the security advisory";
            case 4 -> "Press 's': reset — clear all review WorkItems";
            default -> "Unknown step — press 's' to reset";
        };
    }

    public UUID getAdvisoryId() {
        return advisoryId.get();
    }

    @Transactional
    public StepResult advance() {
        return switch (step.get()) {
            case 0 -> stepSetup();
            case 1 -> stepClaim();
            case 2 -> stepStart();
            case 3 -> stepComplete();
            default -> stepReset();
        };
    }

    @Transactional
    public StepResult reset() {
        return stepReset();
    }

    private StepResult stepSetup() {
        ensureFilters();

        final WorkItem advisory = workItemService.create(WorkItemCreateRequest.builder()
                                                                              .title("Security advisory: TLS 1.0 deprecation — migration guide")
                                                                              .description("Update TLS guide. Must publish before customer notification Friday.")
                                                                              .types(List.of("security-docs"))
                                                                              .formKey("migration-guide")
                                                                              .priority(WorkItemPriority.MEDIUM)
                                                                              .candidateGroups("security-writers,docs-team")
                                                                              .createdBy("doc-system")
                                                                              .payload("{\"doc_type\": \"security-advisory\"}")
                                                                              .build());
        advisoryId.set(advisory.id);

        final WorkItem releaseNotes = workItemService.create(WorkItemCreateRequest.builder()
                                                                                  .title("v3.2 release notes — features and breaking changes")
                                                                                  .description("Document all features and breaking changes for the v3.2 release.")
                                                                                  .types(List.of("release-docs"))
                                                                                  .formKey("release-notes")
                                                                                  .priority(WorkItemPriority.HIGH)
                                                                                  .candidateGroups("docs-team")
                                                                                  .createdBy("release-system")
                                                                                  .build());
        releaseNotesId.set(releaseNotes.id);

        final WorkItem tutorial = workItemService.create(WorkItemCreateRequest.builder()
                                                                              .title("Getting started tutorial — CaseHub Work quick start")
                                                                              .description("Write a 10-minute getting-started guide for new users.")
                                                                              .types(List.of("tutorials"))
                                                                              .formKey("quick-start")
                                                                              .priority(WorkItemPriority.MEDIUM)
                                                                              .candidateGroups("docs-team")
                                                                              .createdBy("doc-system")
                                                                              .build());
        tutorialId.set(tutorial.id);

        step.set(1);

        final WorkItem     fresh     = readFresh(advisory.id);
        final List<String> labels    = fresh.labels.stream().map(l -> l.path).sorted().toList();
        final List<String> ruleNames = cdiRuleNames();

        return new StepResult(1,
                              "Created 3 documents",
                              "CDI rules: " + ruleNames + ". Advisory labels: " + labels,
                              List.of("Urgent row should show advisory in Unassigned column",
                                      "If Urgent is empty, SecurityWritersFilter is not discovered",
                                      "Next: press 's' to claim the security advisory"));
    }

    private StepResult stepClaim() {
        final UUID id = advisoryId.get();
        if (id == null) {
            step.set(0);
            return new StepResult(0, "No advisory", "Run setup first (step 0)", List.of());
        }
        workItemService.claim(id, "senior-reviewer");
        step.set(2);
        final WorkItem     fresh  = readFresh(id);
        final List<String> labels = fresh.labels.stream().map(l -> l.path).sorted().toList();
        return new StepResult(2,
                              "Claimed security advisory",
                              "senior-reviewer claimed it. Status: ASSIGNED. Labels: " + labels,
                              List.of("Advisory should move: Unassigned → Claimed column",
                                      "Next: press 's' to start work"));
    }

    private StepResult stepStart() {
        final UUID id = advisoryId.get();
        if (id == null) {
            step.set(0);
            return new StepResult(0, "No advisory", "Run setup first (step 0)", List.of());
        }
        workItemService.start(id, "senior-reviewer");
        step.set(3);
        final WorkItem     fresh  = readFresh(id);
        final List<String> labels = fresh.labels.stream().map(l -> l.path).sorted().toList();
        return new StepResult(3,
                              "Started reviewing advisory",
                              "Status: IN_PROGRESS. Labels: " + labels,
                              List.of("Advisory should move: Claimed → Active column",
                                      "Next: press 's' to complete"));
    }

    private StepResult stepComplete() {
        final UUID id = advisoryId.get();
        if (id == null) {
            step.set(0);
            return new StepResult(0, "No advisory", "Run setup first (step 0)", List.of());
        }
        workItemService.complete(id, "senior-reviewer",
                                 "{\"approved\": true}",
                                 null,
                                 "Content verified. Ready to publish.",
                                 "DOC-REVIEW-POLICY-v1.4");
        step.set(4);
        final WorkItem     fresh  = readFresh(id);
        final List<String> labels = fresh.labels.stream().map(l -> l.path).sorted().toList();
        return new StepResult(4,
                              "Completed security advisory",
                              "Status: COMPLETED. Labels: " + labels + " (all INFERRED labels cleared)",
                              List.of("Advisory should disappear from all review queues",
                                      "Release notes and tutorial remain in their queues",
                                      "Next: press 's' to reset"));
    }

    private StepResult stepReset() {
        final List<UUID> ids = new ArrayList<>();
        if (advisoryId.get() != null) {
            ids.add(advisoryId.get());
        }
        if (releaseNotesId.get() != null) {
            ids.add(releaseNotesId.get());
        }
        if (tutorialId.get() != null) {
            ids.add(tutorialId.get());
        }
        for (final UUID id : ids) {
            if (id != null) {
                workItemStore.get(id).ifPresent(wi -> {
                    if (!wi.status.isTerminal()) {
                        workItemService.cancel(wi.id, "dashboard-reset", "Dashboard reset");
                    }
                });
            }
        }
        advisoryId.set(null);
        releaseNotesId.set(null);
        tutorialId.set(null);
        step.set(0);
        return new StepResult(0,
                              "Reset complete",
                              "All review WorkItems cancelled. Filters preserved for next run.",
                              List.of("Press 's' to create 3 new documents and begin again"));
    }

    WorkItem readFresh(final UUID id) {
        return workItemStore.get(id).orElseThrow();
    }

    @Transactional
    void ensureFilters() {
        if (LabelRuleEntity.count("name", "Review: Urgent + Pending") > 0) {
            return;
        }
        final String notTerminal = "!status.isTerminal()";

        persist("Review: Urgent + Pending", "jexl",
                "priority.name() == 'URGENT' && " + notTerminal + " && status.name() == 'PENDING'",
                List.of(new LabelAction.Add("review/urgent"), new LabelAction.Add("review/urgent/unassigned")));
        persist("Review: Urgent + Claimed", "jexl",
                "priority.name() == 'URGENT' && " + notTerminal + " && status.name() == 'ASSIGNED'",
                List.of(new LabelAction.Add("review/urgent"), new LabelAction.Add("review/urgent/claimed")));
        persist("Review: Urgent + Active", "jexl",
                "priority.name() == 'URGENT' && " + notTerminal + " && status.name() == 'IN_PROGRESS'",
                List.of(new LabelAction.Add("review/urgent"), new LabelAction.Add("review/urgent/active")));

        persist("Review: Standard + Pending", "jexl",
                "priority.name() == 'HIGH' && " + notTerminal + " && status.name() == 'PENDING'",
                List.of(new LabelAction.Add("review/standard"), new LabelAction.Add("review/standard/unassigned")));
        persist("Review: Standard + Claimed", "jexl",
                "priority.name() == 'HIGH' && " + notTerminal + " && status.name() == 'ASSIGNED'",
                List.of(new LabelAction.Add("review/standard"), new LabelAction.Add("review/standard/claimed")));
        persist("Review: Standard + Active", "jexl",
                "priority.name() == 'HIGH' && " + notTerminal + " && status.name() == 'IN_PROGRESS'",
                List.of(new LabelAction.Add("review/standard"), new LabelAction.Add("review/standard/active")));

        persist("Review: Routine + Pending", "jexl",
                "(priority.name() == 'MEDIUM' || priority.name() == 'LOW') && (candidateGroups == null || !candidateGroups.contains('security-writers')) && " + notTerminal + " && status.name() == 'PENDING'",
                List.of(new LabelAction.Add("review/routine"), new LabelAction.Add("review/routine/unassigned")));
        persist("Review: Routine + Claimed", "jexl",
                "(priority.name() == 'MEDIUM' || priority.name() == 'LOW') && (candidateGroups == null || !candidateGroups.contains('security-writers')) && " + notTerminal + " && status.name() == 'ASSIGNED'",
                List.of(new LabelAction.Add("review/routine"), new LabelAction.Add("review/routine/claimed")));
        persist("Review: Routine + Active", "jexl",
                "(priority.name() == 'MEDIUM' || priority.name() == 'LOW') && (candidateGroups == null || !candidateGroups.contains('security-writers')) && " + notTerminal + " && status.name() == 'IN_PROGRESS'",
                List.of(new LabelAction.Add("review/routine"), new LabelAction.Add("review/routine/active")));
    }

    private void persist(final String name, final String lang, final String expr,
                         final List<LabelAction> actions) {
        final LabelRuleEntity rule = new LabelRuleEntity();
        rule.name                = name;
        rule.scope               = Path.root();
        rule.conditionLanguage   = lang;
        rule.conditionExpression = expr;
        rule.actionsJson         = LabelRuleEntity.serializeActions(actions);
        rule.enabled             = true;
        rule.tenancyId           = currentPrincipal.tenancyId();
        labelRuleStore.put(rule);
    }
}
