package io.casehub.work.api;

/**
 * Payload passed to {@link NotificationChannel#send} for each matched rule.
 *
 * <p>
 * Contains both the triggering lifecycle event and the matching rule, so
 * channel implementations can access WorkItem fields and rule-specific config
 * (target URL, HMAC secret, etc.) without additional lookups.
 *
 * @param event the lifecycle event that triggered this notification
 * @param ruleId the UUID of the matched rule
 * @param channelType the channel type string (e.g. {@code "slack"})
 * @param targetUrl the destination URL or address for this notification
 * @param secret optional HMAC secret for signed channels; null if not configured
 * @param types the type filter from the rule; null means all types matched
 */
public record NotificationPayload(
        WorkItemEvent event,
        java.util.UUID ruleId,
        String channelType,
        String targetUrl,
        String secret,
        String types) {
}
