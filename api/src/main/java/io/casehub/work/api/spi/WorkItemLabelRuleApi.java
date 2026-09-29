package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.CreateLabelRuleRequest;
import io.casehub.work.api.view.EvaluateExpressionRequest;
import io.casehub.work.api.view.EvaluateExpressionResult;
import io.casehub.work.api.view.LabelRuleView;

@McpDomain("work/label-rules")
public interface WorkItemLabelRuleApi {

    @PlatformQuery("List all label rules")
    List<LabelRuleView> list(@ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Create a label rule")
    @RestStatus(201)
    LabelRuleView create(CreateLabelRuleRequest request,
                         @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Update a label rule")
    LabelRuleView update(@PathParam UUID ruleId, CreateLabelRuleRequest request,
                         @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a label rule")
    void delete(@PathParam UUID ruleId,
                @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Evaluate an expression against a context")
    EvaluateExpressionResult evaluate(EvaluateExpressionRequest request,
                                      @ContextParam("tenancyId") String tenancyId);
}
