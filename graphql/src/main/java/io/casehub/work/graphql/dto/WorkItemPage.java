package io.casehub.work.graphql.dto;

import io.casehub.platform.graphql.PageInfo;
import java.util.List;
import org.eclipse.microprofile.graphql.Type;

@Type("WorkItemPage")
public record WorkItemPage(List<WorkItemType> items, PageInfo pageInfo) {}
