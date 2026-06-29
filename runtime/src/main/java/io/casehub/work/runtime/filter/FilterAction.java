package io.casehub.work.runtime.filter;

import java.util.Map;

import io.casehub.work.runtime.model.WorkItem;

/**
 * SPI for actions that a filter rule can apply to a WorkItem when its condition matches.
 *
 * <p>
 * Implementations must be {@code @ApplicationScoped} CDI beans. The engine resolves
 * them by matching {@link ActionDescriptor#type()} to {@link #type()}.
 *
 * <p>
 * Built-in implementations (in quarkus-work): {@code APPLY_LABEL},
 * {@code OVERRIDE_CANDIDATE_GROUPS}, {@code SET_PRIORITY}.
 */
public interface FilterAction {

    /** The action type name used in {@link ActionDescriptor#type()}. Must be unique. */
    String type();

    /**
     * Apply this action to the given WorkItem.
     *
     * @param workItem the WorkItem to operate on
     * @param params action-specific parameters from the {@link ActionDescriptor}
     */
    void apply(WorkItem workItem, Map<String, Object> params);
}
