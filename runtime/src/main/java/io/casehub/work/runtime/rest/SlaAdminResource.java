package io.casehub.work.runtime.rest;

import io.casehub.work.runtime.service.SlaDefaultsYamlLoader;
import jakarta.inject.Inject;
import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/workitems/admin/sla-config")
@HandWrittenEndpoint("Admin infrastructure endpoint — not a domain API")
public class SlaAdminResource {

    @Inject
    SlaDefaultsYamlLoader loader;

    @POST
    @Path("/reload")
    public Response reload() {
        loader.reload();
        return Response.ok("{\"reloaded\":true}").build();
    }
}
