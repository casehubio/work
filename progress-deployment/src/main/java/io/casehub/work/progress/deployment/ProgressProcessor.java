package io.casehub.work.progress.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class ProgressProcessor {

    private static final String FEATURE = "workitems-progress";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerStrategyBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        "io.casehub.work.progress.rollup.CountCompletedStrategy",
                        "io.casehub.work.progress.rollup.AveragePercentageStrategy",
                        "io.casehub.work.progress.rollup.WeightedPercentageStrategy")
                .setUnremovable()
                .build();
    }
}
