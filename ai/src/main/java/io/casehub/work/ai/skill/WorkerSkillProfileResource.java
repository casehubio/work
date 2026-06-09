package io.casehub.work.ai.skill;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.ai.repository.WorkerSkillProfileStore;

/**
 * REST API for managing worker skill profiles.
 *
 * <p>
 * Profiles are upserted by workerId. They feed {@link WorkerProfileSkillProfileProvider}
 * so the semantic strategy can match workers to work items by narrative similarity.
 */
@Path("/worker-skill-profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkerSkillProfileResource {

    @Inject
    WorkerSkillProfileStore profileStore;

    record ProfileRequest(String workerId, String narrative) {
    }

    /**
     * Upsert a worker skill profile by workerId.
     *
     * @param request the profile request containing workerId and narrative
     * @return 201 Created on success, 400 if workerId is missing
     */
    @POST
    @Transactional
    public Response upsert(final ProfileRequest request) {
        if (request == null || request.workerId() == null || request.workerId().isBlank()) {
            throw new WebApplicationException("workerId is required", 400);
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
        return Response.status(201).build();
    }

    /**
     * List all worker skill profiles.
     *
     * @return list of all profiles
     */
    @GET
    public List<WorkerSkillProfile> listAll() {
        return profileStore.scanAll();
    }

    /**
     * Get a worker skill profile by workerId.
     *
     * @param workerId the worker identifier
     * @return the profile, or 404 if not found
     */
    @GET
    @Path("/{workerId}")
    public WorkerSkillProfile get(@PathParam("workerId") final String workerId) {
        return profileStore.get(workerId)
                .orElseThrow(() -> new WebApplicationException(404));
    }

    /**
     * Delete a worker skill profile by workerId.
     *
     * @param workerId the worker identifier
     * @return 204 No Content on success, 404 if not found
     */
    @DELETE
    @Path("/{workerId}")
    @Transactional
    public Response delete(@PathParam("workerId") final String workerId) {
        final boolean deleted = profileStore.delete(workerId);
        return deleted ? Response.noContent().build() : Response.status(404).build();
    }
}
