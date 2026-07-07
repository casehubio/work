package io.casehub.work.runtime.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class WorkItemType {

    @Column(name = "path", nullable = false, length = 500)
    public String path;

    public WorkItemType() {}

    public WorkItemType(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkItemType other)) return false;
        return java.util.Objects.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(path);
    }
}
