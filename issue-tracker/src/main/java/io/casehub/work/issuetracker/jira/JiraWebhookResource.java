package io.casehub.work.issuetracker.jira;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformWebhook;
import io.casehub.platform.api.mcp.QueryParam;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventHandler;
import io.casehub.work.runtime.service.TenantHolder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@McpDomain(value = "work/webhooks", basePath = "/workitems")
@ApplicationScoped
public class JiraWebhookResource {

    private static final Logger LOG = Logger.getLogger(JiraWebhookResource.class);

    @Inject
    JiraIssueTrackerConfig config;

    final JiraWebhookParser parser = new JiraWebhookParser();

    @Inject
    WebhookEventHandler handler;

    @Inject
    TenantHolder tenantHolder;

    @PlatformWebhook("Receive Jira webhook event")
    @RestPath("/jira-webhook/{tenancyId}")
    public Response receiveJira(
            @PathParam String tenancyId,
            @QueryParam("secret") String secret,
            String body) {

        if (tenancyId == null || tenancyId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(Map.of("error", "tenancyId path parameter is required"))
                           .build();
        }

        final String configuredSecret = config.webhookSecret()
                                              .filter(s -> !s.isBlank())
                                              .orElse(null);
        if (configuredSecret == null) {
            LOG.warn("Jira webhook received but casehub.work.issue-tracker.jira.webhook-secret is not configured — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!verifySecret(configuredSecret, secret)) {
            LOG.warn("Jira webhook secret mismatch — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        tenantHolder.setTenancyId(tenancyId);

        try {
            final WebhookEvent event = parser.parse(Map.of(), body);
            if (event != null) {
                handler.handle(event);
            }
        } catch (final Exception e) {
            LOG.warnf("Jira webhook processing error (returning 200 to prevent retry): %s", e.getMessage());
        }

        return Response.ok().build();
    }

    private boolean verifySecret(final String expected, final String provided) {
        if (provided == null || provided.isBlank()) {return false;}
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
