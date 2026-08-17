package io.casehub.work.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

@Input("WorkItemFilterInput")
public record WorkItemFilterInput(
    String status,
    String assignee,
    String candidateGroups,
    String priority,
    String scope) {}
