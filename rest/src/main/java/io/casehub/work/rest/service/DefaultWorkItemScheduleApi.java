package io.casehub.work.rest.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.spi.WorkItemScheduleApi;
import io.casehub.work.api.view.CreateScheduleRequest;
import io.casehub.work.api.view.ScheduleView;
import io.casehub.work.api.view.SetActiveRequest;
import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;
import io.casehub.work.runtime.service.WorkItemScheduleService;

@ApplicationScoped
public class DefaultWorkItemScheduleApi implements WorkItemScheduleApi {

    @Inject
    WorkItemScheduleService scheduleService;

    @Inject
    WorkItemScheduleStore scheduleStore;

    @Override
    public ScheduleView create(CreateScheduleRequest request, String tenancyId) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.templateId() == null || request.templateId().isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        if (request.cronExpression() == null || request.cronExpression().isBlank()) {
            throw new IllegalArgumentException("cronExpression is required");
        }
        try {
            final WorkItemSchedule s = scheduleService.create(
                    request.name(),
                    UUID.fromString(request.templateId()),
                    request.cronExpression(),
                    request.createdBy() != null ? request.createdBy() : "unknown");
            return toView(s);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid cron expression — use Quartz format, e.g. '0 0 9 * * ?'");
        }
    }

    @Override
    public List<ScheduleView> list(String tenancyId) {
        return scheduleStore.scanAll().stream().map(this::toView).toList();
    }

    @Override
    public ScheduleView get(UUID scheduleId, String tenancyId) {
        return scheduleService.findById(scheduleId)
                .map(this::toView)
                .orElse(null);
    }

    @Override
    public void delete(UUID scheduleId, String tenancyId) {
        if (!scheduleStore.delete(scheduleId)) {
            throw new IllegalArgumentException("Schedule not found");
        }
    }

    @Override
    public ScheduleView setActive(UUID scheduleId, SetActiveRequest request, String tenancyId) {
        if (request == null) {
            throw new IllegalArgumentException("body required: {\"active\": true|false}");
        }
        try {
            return scheduleService.setActive(scheduleId, request.active())
                    .map(this::toView)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not recompute nextFireAt: " + e.getMessage());
        }
    }

    private ScheduleView toView(WorkItemSchedule s) {
        return new ScheduleView(
                s.id, s.name, s.templateId, s.cronExpression,
                s.active, s.createdBy, s.createdAt, s.lastFiredAt, s.nextFireAt);
    }
}
