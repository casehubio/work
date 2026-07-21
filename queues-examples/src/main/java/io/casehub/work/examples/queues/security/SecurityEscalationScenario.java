package io.casehub.work.examples.queues.security;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import java.time.Instant;
import java.util.UUID;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.examples.queues.QueueScenarioResponse;
import io.casehub.work.examples.queues.QueueScenarioStep;
import io.casehub.work.examples.queues.lifecycle.QueueEventLog;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scenario: Multi-Label Security Escalation.
 *
 * <p>
 * Demonstrates multi-label stacking and cascade escalation inspired by
 * Zendesk + PagerDuty dual-escalation patterns:
 * <ul>
 * <li>Filter A: {@code types.contains('security')} → {@code security/incident}</li>
 * <li>Filter B: {@code priority == 'URGENT'} → {@code priority/critical}</li>
 * <li>Filter C (cascade): {@code labels.contains('security/incident') && labels.contains('priority/critical')}
 * → {@code security/exec-escalate}</li>
 * </ul>
 *
 * <p>
 * Queue events per step:
 * <ol>
 * <li>HIGH security incident → ADDED to Security Incidents Queue only</li>
 * <li>URGENT breach → ADDED to Security Incidents Queue + ADDED to Priority Critical Queue +
 * ADDED to Security Exec Escalation Queue (cascade fires after both A and B labels present)</li>
 * </ol>
 *
 * <p>
 * Endpoint: {@code POST /queue-examples/security/run}
 */
@Path("/queue-examples/security")
@Produces(MediaType.APPLICATION_JSON)
public class SecurityEscalationScenario {
    @Inject
    SubjectViewStore viewStore;


    private static final Logger LOG = Logger.getLogger(SecurityEscalationScenario.class);

    @Inject
    WorkItemService workItemService;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    QueueEventLog eventLog;

    private void setupFilters() {
        if (LabelRuleEntity.count("name", "Security-A: Incident Detection") > 0)
            return;

        final LabelRuleEntity filterA = new LabelRuleEntity();
        filterA.name = "Security-A: Incident Detection";
        filterA.scope = io.casehub.platform.api.path.Path.root();
        filterA.conditionLanguage = "jexl";
        filterA.conditionExpression = "types.contains('security')";
        filterA.actionsJson = LabelRuleEntity.serializeActions(List.of(
                new LabelAction.Add("security/incident")));
        filterA.enabled = true;
        filterA.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        filterA.persist();

        final LabelRuleEntity filterB = new LabelRuleEntity();
        filterB.name = "Security-B: Critical Priority Flag";
        filterB.scope = io.casehub.platform.api.path.Path.root();
        filterB.conditionLanguage = "jexl";
        filterB.conditionExpression = "priority == 'URGENT'";
        filterB.actionsJson = LabelRuleEntity.serializeActions(List.of(
                new LabelAction.Add("priority/critical")));
        filterB.enabled = true;
        filterB.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        filterB.persist();

        final LabelRuleEntity filterC = new LabelRuleEntity();
        filterC.name = "Security-C: Critical Incident → Executive Escalation";
        filterC.scope = io.casehub.platform.api.path.Path.root();
        filterC.conditionLanguage = "jexl";
        filterC.conditionExpression = "labels.contains('security/incident') && labels.contains('priority/critical')";
        filterC.actionsJson = LabelRuleEntity.serializeActions(List.of(
                new LabelAction.Add("security/exec-escalate")));
        filterC.enabled = true;
        filterC.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        filterC.persist();
    }

    private void setupQueueViews() {
        var tid = TenancyConstants.DEFAULT_TENANT_ID;
        if (viewStore.findByTenancy(tid).stream().anyMatch(s -> s.name().equals("Security Incidents Queue"))) {return;}

        viewStore.save(new SubjectViewSpec(UUID.randomUUID(), "Security Incidents Queue", tid,
                                           "security/incident", io.casehub.platform.api.path.Path.root(),
                                           "createdAt", "ASC", null, Instant.now()));
        viewStore.save(new SubjectViewSpec(UUID.randomUUID(), "Priority Critical Queue", tid,
                                           "priority/critical", io.casehub.platform.api.path.Path.root(),
                                           "createdAt", "ASC", null, Instant.now()));
        viewStore.save(new SubjectViewSpec(UUID.randomUUID(), "Security Exec Escalation Queue", tid,
                                           "security/exec-escalate", io.casehub.platform.api.path.Path.root(),
                                           "createdAt", "ASC", null, Instant.now()));}

    /**
     * Run the multi-label security escalation scenario end to end.
     *
     * @return scenario response with steps, queue events per step, and exec-escalate queue contents
     */
    @POST
    @Path("/run")
    @Transactional
    public QueueScenarioResponse run() {
        setupFilters();
        setupQueueViews();
        eventLog.clear();
        final List<QueueScenarioStep> steps = new ArrayList<>();

        LOG.info("[SECURITY] Step 1/3: HIGH security incident — security/incident only (not URGENT)");
        final WorkItem highIncident = workItemService.create(WorkItemCreateRequest.builder()
                .title("Suspicious login attempts — automated bot detected")
                .description("Rate limiter flagged 2,400 failed login attempts from a single IP range over 10 minutes.")
                .types(List.of("security"))
                .formKey("login-anomaly")
                .priority(WorkItemPriority.HIGH)
                .candidateGroups("security-team")
                .createdBy("siem-system")
                .payload("{\"source_ip_range\": \"185.220.x.x\", \"attempts\": 2400, \"period_minutes\": 10}")
                .build());
        steps.add(new QueueScenarioStep(1,
                "HIGH security anomaly — filter A fires: security/incident; filter B (URGENT) does not match; filter C (cascade) cannot fire without priority/critical",
                highIncident.id, inferredPaths(highIncident), manualPaths(highIncident),
                formatEvents(eventLog.drain())));

        LOG.info("[SECURITY] Step 2/3: URGENT security breach — all 3 labels via cascade");
        final WorkItem criticalBreach = workItemService.create(WorkItemCreateRequest.builder()
                .title("Data breach confirmed — customer PII exfiltrated")
                .description("Forensic analysis confirms unauthorised exfiltration of 340,000 customer records.")
                .types(List.of("security"))
                .formKey("data-breach")
                .priority(WorkItemPriority.URGENT)
                .candidateGroups("security-team,legal-team,executive-team")
                .createdBy("forensics-system")
                .payload("{\"records_affected\": 340000, \"data_types\": [\"name\", \"email\", \"password_hash\"], " +
                        "\"gdpr_window_hours\": 72, \"incident_id\": \"SEC-BREACH-2026-001\"}")
                .build());
        steps.add(new QueueScenarioStep(2,
                "URGENT data breach — filter A: security/incident, filter B: priority/critical, filter C cascades: security/exec-escalate — 3 queue ADDED events",
                criticalBreach.id, inferredPaths(criticalBreach), manualPaths(criticalBreach),
                formatEvents(eventLog.drain())));

        LOG.info("[SECURITY] Step 3/3: security/exec-escalate queue — URGENT incident only");
        final List<UUID> execEscalateQueue = workItemStore
                .scan(WorkItemQuery.byLabelPattern("security/exec-escalate"))
                .stream().map(w -> w.id).toList();
        steps.add(new QueueScenarioStep(3,
                "security/exec-escalate queue — URGENT breach present; HIGH anomaly absent (did not meet cascade threshold)",
                null,
                List.of("security/exec-escalate contains " + execEscalateQueue.size() + " item(s)"),
                List.of(), List.of()));

        return new QueueScenarioResponse(
                "security-exec-escalation",
                "Multi-label stacking cascade: security+URGENT→exec-escalate; impossible with single-filter logic alone",
                steps, execEscalateQueue);
    }

    private List<String> inferredPaths(final WorkItem wi) {
        return wi.labels.stream().filter(l -> l.persistence == LabelPersistence.INFERRED)
                .map(l -> l.path).toList();
    }

    private List<String> manualPaths(final WorkItem wi) {
        return wi.labels.stream().filter(l -> l.persistence == LabelPersistence.MANUAL)
                .map(l -> l.path).toList();
    }

    private List<String> formatEvents(final List<QueueEventLog.Entry> entries) {
        return entries.stream()
                .map(e -> e.eventType().name() + " to " + e.queueName())
                .toList();
    }
}
