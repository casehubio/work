package io.casehub.work.annotations.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class WorkAnnotationsProcessedBuildItem extends MultiBuildItem {

    private final String declaringClass;
    private final String methodName;

    public WorkAnnotationsProcessedBuildItem(String declaringClass, String methodName) {
        this.declaringClass = declaringClass;
        this.methodName = methodName;
    }

    public String declaringClass() {
        return declaringClass;
    }

    public String methodName() {
        return methodName;
    }
}
