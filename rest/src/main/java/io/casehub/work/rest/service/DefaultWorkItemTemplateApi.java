package io.casehub.work.rest.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.work.api.Outcome;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.spi.WorkItemTemplateApi;
import io.casehub.work.api.view.CreateTemplateRequest;
import io.casehub.work.api.view.InstantiateTemplateRequest;
import io.casehub.work.api.view.TemplateView;
import io.casehub.work.api.view.UpdateTemplateRequest;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.casehub.work.runtime.service.WorkItemTemplateValidationService;

@ApplicationScoped
public class DefaultWorkItemTemplateApi implements WorkItemTemplateApi {

    @Inject
    WorkItemTemplateService templateService;

    @Inject
    WorkItemTemplateStore templateStore;

    @Override
    @Transactional
    public TemplateView create(CreateTemplateRequest request, String tenancyId) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.createdBy() == null || request.createdBy().isBlank()) {
            throw new IllegalArgumentException("createdBy is required");
        }
        if (templateService.findByName(request.name()).isPresent()) {
            throw new IllegalStateException("template with name '" + request.name() + "' already exists");
        }

        final WorkItemTemplate t = new WorkItemTemplate();
        t.tenancyId = tenancyId;
        applyCreate(t, request);
        WorkItemTemplateValidationService.validate(t);
        templateStore.put(t);
        return toView(t);
    }

    @Override
    public List<TemplateView> listAll(String tenancyId) {
        return templateStore.scanAll().stream().map(this::toView).toList();
    }

    @Override
    public TemplateView getById(UUID templateId, String tenancyId) {
        return templateService.findById(templateId).map(this::toView).orElse(null);
    }

    @Override
    @Transactional
    public void delete(UUID templateId, String tenancyId) {
        if (!templateStore.delete(templateId)) {
            throw new IllegalArgumentException("Template not found");
        }
    }

    @Override
    @Transactional
    public TemplateView update(UUID templateId, UpdateTemplateRequest request, String tenancyId) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        final WorkItemTemplate t = templateService.findById(templateId).orElse(null);
        if (t == null) {
            return null;
        }

        if (!request.name().equals(t.name)) {
            if (templateService.findByName(request.name()).isPresent()) {
                throw new IllegalStateException("template with name '" + request.name() + "' already exists");
            }
        }

        applyUpdate(t, request);
        WorkItemTemplateValidationService.validate(t);
        t.version++;
        return toView(t);
    }

    @Override
    @Transactional
    public WorkItemView instantiate(UUID templateId, InstantiateTemplateRequest request, String tenancyId) {
        if (request == null || request.createdBy() == null || request.createdBy().isBlank()) {
            throw new IllegalArgumentException("createdBy is required");
        }
        final var createRequest = WorkItemCreateRequest.builder()
                .templateId(templateId)
                .title(request.title())
                .assigneeId(request.assigneeId())
                .createdBy(request.createdBy())
                .tenancyId(tenancyId)
                .build();
        final var wi = templateService.createFromTemplate(createRequest);
        return ViewMapper.toView(wi);
    }

    private void applyCreate(WorkItemTemplate t, CreateTemplateRequest r) {
        t.name = r.name();
        t.description = r.description();
        t.typePaths = r.typePaths();
        t.priority = r.priority() != null ? WorkItemPriority.valueOf(r.priority()) : null;
        t.candidateGroups = r.candidateGroups();
        t.candidateUsers = r.candidateUsers();
        t.requiredCapabilities = r.requiredCapabilities();
        t.defaultExpiryHours = r.defaultExpiryHours();
        t.defaultClaimHours = r.defaultClaimHours();
        t.defaultExpiryBusinessHours = r.defaultExpiryBusinessHours();
        t.defaultClaimBusinessHours = r.defaultClaimBusinessHours();
        t.defaultPayload = r.defaultPayload();
        t.labelPaths = r.labelPaths();
        t.instanceCount = r.instanceCount();
        t.requiredCount = r.requiredCount();
        t.parentRole = r.parentRole();
        t.assignmentStrategy = r.assignmentStrategy();
        t.onThresholdReached = r.onThresholdReached();
        t.allowSameAssignee = r.allowSameAssignee();
        t.outcomes = WorkItemTemplateService.encodeOutcomes(r.outcomes());
        t.inputDataSchema = r.inputDataSchema();
        t.outputDataSchema = r.outputDataSchema();
        t.excludedUsers = r.excludedUsers();
        t.excludedGroups = r.excludedGroups();
        t.scope = r.scope();
        t.createdBy = r.createdBy();
    }

    private void applyUpdate(WorkItemTemplate t, UpdateTemplateRequest r) {
        t.name = r.name();
        t.description = r.description();
        t.typePaths = r.typePaths();
        t.priority = r.priority() != null ? WorkItemPriority.valueOf(r.priority()) : null;
        t.candidateGroups = r.candidateGroups();
        t.candidateUsers = r.candidateUsers();
        t.requiredCapabilities = r.requiredCapabilities();
        t.defaultExpiryHours = r.defaultExpiryHours();
        t.defaultClaimHours = r.defaultClaimHours();
        t.defaultExpiryBusinessHours = r.defaultExpiryBusinessHours();
        t.defaultClaimBusinessHours = r.defaultClaimBusinessHours();
        t.defaultPayload = r.defaultPayload();
        t.labelPaths = r.labelPaths();
        t.instanceCount = r.instanceCount();
        t.requiredCount = r.requiredCount();
        t.parentRole = r.parentRole();
        t.assignmentStrategy = r.assignmentStrategy();
        t.onThresholdReached = r.onThresholdReached();
        t.allowSameAssignee = r.allowSameAssignee();
        t.outcomes = WorkItemTemplateService.encodeOutcomes(r.outcomes());
        t.inputDataSchema = r.inputDataSchema();
        t.outputDataSchema = r.outputDataSchema();
        t.excludedUsers = r.excludedUsers();
        t.excludedGroups = r.excludedGroups();
        t.scope = r.scope();
    }

    private TemplateView toView(WorkItemTemplate t) {
        return new TemplateView(
                t.id, t.version, t.name, t.description, t.typePaths,
                t.priority != null ? t.priority.name() : null,
                t.candidateGroups, t.candidateUsers, t.requiredCapabilities,
                t.defaultExpiryHours, t.defaultClaimHours,
                t.defaultExpiryBusinessHours, t.defaultClaimBusinessHours,
                t.defaultPayload, t.labelPaths,
                t.instanceCount, t.requiredCount, t.parentRole,
                t.assignmentStrategy, t.onThresholdReached, t.allowSameAssignee,
                t.outcomes == null ? null : WorkItemTemplateService.decodeOutcomes(t.outcomes),
                t.inputDataSchema, t.outputDataSchema,
                t.excludedUsers, t.excludedGroups, t.scope,
                t.createdBy, t.createdAt);
    }
}
