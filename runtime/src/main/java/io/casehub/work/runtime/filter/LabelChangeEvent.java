package io.casehub.work.runtime.filter;

import io.casehub.work.runtime.model.WorkItem;

import java.util.List;

public record LabelChangeEvent(
    WorkItem workItem,
    List<LabelDelta> deltas
) {
    public record LabelDelta(String path, ChangeType changeType) {}

    public enum ChangeType { ADDED, REMOVED }

    public boolean hasAdditions() {
        return deltas.stream().anyMatch(d -> d.changeType == ChangeType.ADDED);
    }

    public boolean hasRemovals() {
        return deltas.stream().anyMatch(d -> d.changeType == ChangeType.REMOVED);
    }

    public List<LabelDelta> additions() {
        return deltas.stream().filter(d -> d.changeType == ChangeType.ADDED).toList();
    }

    public List<LabelDelta> removals() {
        return deltas.stream().filter(d -> d.changeType == ChangeType.REMOVED).toList();
    }
}
