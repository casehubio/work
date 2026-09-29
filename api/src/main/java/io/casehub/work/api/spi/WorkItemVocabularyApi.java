package io.casehub.work.api.spi;

import java.util.List;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.AddDefinitionRequest;
import io.casehub.work.api.view.AddDefinitionResult;
import io.casehub.work.api.view.LabelDefinitionView;

@McpDomain("work/vocabulary")
public interface WorkItemVocabularyApi {

    @PlatformQuery("List all label definitions")
    List<LabelDefinitionView> listAll(@ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Add a label definition to the vocabulary")
    @RestStatus(201)
    AddDefinitionResult addDefinition(AddDefinitionRequest request,
                                      @ContextParam("tenancyId") String tenancyId);
}
