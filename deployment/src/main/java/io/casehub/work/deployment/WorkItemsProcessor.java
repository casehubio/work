package io.casehub.work.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;

/**
 * Quarkus build-time processor for the WorkItems extension.
 * Registers the "workitems" feature, SQL migration resources for native image,
 * and WorkItemsMigrationCustomizer as an unremovable CDI bean.
 */
class WorkItemsProcessor {

    private static final String FEATURE = "workitems";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    NativeImageResourcePatternsBuildItem registerMigrationResources() {
        return NativeImageResourcePatternsBuildItem.builder()
                .includeGlob("db/work/migration/*.sql")
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem registerStrategyBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        "io.casehub.work.core.strategy.LeastLoadedStrategy",
                        "io.casehub.work.core.strategy.ClaimFirstStrategy",
                        "io.casehub.work.core.strategy.RoundRobinStrategy",
                        "io.casehub.work.core.policy.ContinuationPolicy",
                        "io.casehub.work.core.policy.FreshClockPolicy",
                        "io.casehub.work.core.policy.SingleBudgetPolicy",
                        "io.casehub.work.core.policy.PhaseClockPolicy",
                        "io.casehub.work.runtime.service.NoOpSlaBreachPolicy",
                        "io.casehub.work.runtime.multiinstance.PoolAssignmentStrategy",
                        "io.casehub.work.runtime.multiinstance.RoundRobinAssignmentStrategy",
                        "io.casehub.work.runtime.multiinstance.ExplicitListAssignmentStrategy",
                        "io.casehub.work.runtime.multiinstance.CompositeInstanceAssignmentStrategy")
                .setUnremovable()
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem registerMigrationCustomizer() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses("io.casehub.work.runtime.flyway.WorkItemsMigrationCustomizer")
                .setUnremovable()
                .build();
    }
}
