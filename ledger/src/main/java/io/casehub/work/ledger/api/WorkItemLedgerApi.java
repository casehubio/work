package io.casehub.work.ledger.api;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.ledger.api.dto.LedgerAttestationRequest;
import io.casehub.work.ledger.api.dto.LedgerEntryResponse;
import io.casehub.work.ledger.api.dto.ProvenanceRequest;
import io.casehub.work.ledger.model.WorkItemLedgerEntry;
import io.casehub.work.ledger.repository.WorkItemLedgerEntryRepository;
import io.casehub.work.runtime.service.WorkItemNotFoundException;

@McpDomain("work/work-ledger")
@ApplicationScoped
public class WorkItemLedgerApi {

    @Inject WorkItemLedgerEntryRepository ledgerRepo;
    @Inject WorkItemStore workItemStore;
    @Inject CurrentPrincipal currentPrincipal;
    @Inject LedgerConfig config;

    @PlatformQuery("Get all ledger entries for a work item with attestations")
    public List<LedgerEntryResponse> getLedger(@PathParam UUID workItemId,
                                              @ContextParam("tenancyId") String tenancyId) {
        workItemStore.get(workItemId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        final List<WorkItemLedgerEntry> entries = ledgerRepo.findByWorkItemId(workItemId);
        entries.forEach(WorkItemLedgerEntry::syncSupplementsFromJpa);
        return entries.stream()
                .map(e -> LedgerMapper.toResponse(e, ledgerRepo.findAttestationsByEntryId(e.id, tenancyId)))
                .toList();
    }

    @PlatformMutation("Set source entity provenance on the creation ledger entry")
    public void setProvenance(@PathParam UUID workItemId, ProvenanceRequest request,
                              @ContextParam("tenancyId") String tenancyId) {
        workItemStore.get(workItemId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        final List<WorkItemLedgerEntry> entries = ledgerRepo.findByWorkItemId(workItemId);
        final WorkItemLedgerEntry creationEntry = entries.stream()
                .filter(e -> e.sequenceNumber == 1)
                .findFirst()
                .orElse(null);

        if (creationEntry == null) {
            throw new IllegalArgumentException("No ledger entry found for this WorkItem");
        }

        if (creationEntry.provenance().isPresent()) {
            throw new IllegalStateException("Provenance already set for this WorkItem");
        }

        final var provenance = new JpaProvenanceSupplement();
        provenance.sourceEntityId = request.sourceEntityId();
        provenance.sourceEntityType = request.sourceEntityType();
        provenance.sourceEntitySystem = request.sourceEntitySystem();
        creationEntry.attach(provenance);
        ledgerRepo.save(creationEntry, tenancyId);
    }

    @PlatformMutation("Post a peer attestation on a ledger entry")
    @RestStatus(201)
    public void postAttestation(@PathParam UUID workItemId, @PathParam UUID entryId,
                                LedgerAttestationRequest request,
                                @ContextParam("tenancyId") String tenancyId) {
        if (!config.attestations().enabled()) {
            throw new IllegalStateException("Attestations are disabled");
        }

        final WorkItemLedgerEntry entry = ledgerRepo.findEntryById(entryId, tenancyId)
                .filter(e -> e instanceof WorkItemLedgerEntry)
                .map(e -> (WorkItemLedgerEntry) e)
                .filter(e -> workItemId.equals(e.subjectId))
                .orElse(null);

        if (entry == null) {
            throw new IllegalArgumentException("Ledger entry not found for this WorkItem");
        }

        final LedgerAttestation attestation = new io.casehub.ledger.runtime.model.LedgerAttestation();
        attestation.ledgerEntryId = entryId;
        attestation.subjectId = workItemId;
        attestation.attestorId = request.attestorId();
        attestation.attestorType = request.attestorType();
        attestation.verdict = request.verdict();
        attestation.evidence = request.evidence();
        attestation.confidence = request.confidence();

        ledgerRepo.saveAttestation(attestation, tenancyId);
    }
}
