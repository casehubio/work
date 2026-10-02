package io.casehub.work.rest;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.casehub.work.runtime.service.LabelNotFoundException;

@Provider
public class LabelNotFoundExceptionMapper implements ExceptionMapper<LabelNotFoundException> {

    @Override
    public Response toResponse(final LabelNotFoundException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
