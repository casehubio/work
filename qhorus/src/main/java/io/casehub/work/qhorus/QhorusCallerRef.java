package io.casehub.work.qhorus;

import java.util.UUID;

public record QhorusCallerRef(UUID channelId, long messageId, String correlationId) {

    public static final String PREFIX = "qhorus:";

    public static boolean isQhorus(final String callerRef) {
        return callerRef != null && callerRef.startsWith(PREFIX);
    }

    public static QhorusCallerRef parse(final String callerRef) {
        if (!isQhorus(callerRef)) {
            throw new IllegalArgumentException("Not a Qhorus callerRef: " + callerRef);
        }
        final String body = callerRef.substring(PREFIX.length());
        final String[] parts = body.split("/", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed Qhorus callerRef (expected 3 segments): " + callerRef);
        }
        return new QhorusCallerRef(UUID.fromString(parts[0]), Long.parseLong(parts[1]), parts[2]);
    }

    public String encode() {
        return PREFIX + channelId + "/" + messageId + "/" + correlationId;
    }
}
