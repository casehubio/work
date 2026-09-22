package io.casehub.work.ai.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.ai.repository.WorkerSkillProfileStore;
import io.casehub.work.ai.skill.WorkerSkillProfile;
import io.casehub.work.api.spi.WorkItemSkillProfileApi;
import io.casehub.work.api.view.SkillProfileRequest;
import io.casehub.work.api.view.SkillProfileView;

@ApplicationScoped
public class DefaultWorkItemSkillProfileApi implements WorkItemSkillProfileApi {

    @Inject
    WorkerSkillProfileStore profileStore;

    @Override
    public void upsert(SkillProfileRequest request, String tenancyId) {
        if (request == null || request.workerId() == null || request.workerId().isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        final var existing = profileStore.get(request.workerId());
        if (existing.isEmpty()) {
            final var profile = new WorkerSkillProfile();
            profile.workerId = request.workerId();
            profile.narrative = request.narrative();
            profileStore.put(profile);
        } else {
            existing.get().narrative = request.narrative();
            profileStore.put(existing.get());
        }
    }

    @Override
    public List<SkillProfileView> listAll(String tenancyId) {
        return profileStore.scanAll().stream().map(this::toView).toList();
    }

    @Override
    public SkillProfileView get(String workerId, String tenancyId) {
        return profileStore.get(workerId).map(this::toView).orElse(null);
    }

    @Override
    public void delete(String workerId, String tenancyId) {
        if (!profileStore.delete(workerId)) {
            throw new IllegalArgumentException("Profile not found");
        }
    }

    private SkillProfileView toView(WorkerSkillProfile p) {
        return new SkillProfileView(p.workerId, p.narrative, p.createdAt, p.updatedAt);
    }
}
