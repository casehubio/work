package io.casehub.work.graphql.dto;

import io.casehub.platform.graphql.scalar.Json;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.graphql.Input;

@Input("CreateWorkItemInput")
public record CreateWorkItemInput(
    String title,
    String description,
    String formKey,
    String priority,
    String candidateGroups,
    String candidateUsers,
    String requiredCapabilities,
    List<String> types,
    String scope,
    Instant expiresAt,
    Instant claimDeadline,
    String payload) {}
