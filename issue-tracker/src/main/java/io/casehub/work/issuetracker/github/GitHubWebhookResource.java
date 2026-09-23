package io.casehub.work.issuetracker.github;

import io.casehub.platform.api.mcp.HeaderParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformWebhook;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventHandler;
import io.casehub.work.runtime.service.TenantHolder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@McpDomain(value = "work/webhooks", basePath = "/workitems")
@ApplicationScoped
public class GitHubWebhookResource {

    private static final Logger    LOG         = Logger.getLogger(GitHubWebhookResource.class);
    private static final String    HMAC_SHA256 = "HmacSHA256";
    private static final HexFormat HEX         = HexFormat.of();

    @Inject
    GitHubIssueTrackerConfig config;

    final GitHubWebhookParser parser = new GitHubWebhookParser();

    @Inject
    WebhookEventHandler handler;

    @Inject
    TenantHolder tenantHolder;

    @PlatformWebhook("Receive GitHub Issues webhook event")
    @RestPath("/github-webhook/{tenancyId}")
    public Response receiveGitHub(
            @PathParam String tenancyId,
            @HeaderParam("X-Hub-Signature-256") String signature,
            String body) {

        if (tenancyId == null || tenancyId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(Map.of("error", "tenancyId path parameter is required"))
                           .build();
        }

        final String secret = config.webhookSecret().filter(s -> !s.isBlank()).orElse(null);
        if (secret == null) {
            LOG.warn("GitHub webhook received but casehub.work.issue-tracker.github.webhook-secret is not configured — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!verifySignature(secret, body, signature)) {
            LOG.warn("GitHub webhook HMAC verification failed — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        tenantHolder.setTenancyId(tenancyId);

        try {
            final WebhookEvent event = parser.parse(Map.of(), body);
            if (event != null) {
                handler.handle(event);
            }
        } catch (final Exception e) {
            LOG.warnf("GitHub webhook processing error (returning 200 to prevent retry): %s", e.getMessage());
        }

        return Response.ok().build();
    }

    private boolean verifySignature(final String secret, final String body, final String signature) {
        if (signature == null || signature.isBlank()) {return false;}
        try {
            final Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            final String expected = "sha256=" +
                                    HEX.formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            LOG.warnf("HMAC computation failed: %s", e.getMessage());
            return false;
        }
    }
}
