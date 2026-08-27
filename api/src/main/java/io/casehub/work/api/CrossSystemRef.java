package io.casehub.work.api;

/**
 * Marker interface for typed cross-system references carried on WorkItem callerRef.
 *
 * <p>Each integration module provides its own implementations with domain-specific
 * accessors. work-api stays opaque about callerRef content — this interface provides
 * only {@link #system()} for runtime identification and {@link #encode()} for
 * string serialisation.
 *
 * @see io.casehub.work.engine.CallerRef
 */
public interface CrossSystemRef {

    String system();

    String encode();
}
