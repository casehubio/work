package io.casehub.work.annotations.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class WorkAnnotationsProcessor {

    private static final Logger LOG = Logger.getLogger(WorkAnnotationsProcessor.class);

    static final DotName HUMAN_APPROVAL = DotName.createSimple("io.casehub.work.annotations.HumanApproval");
    static final DotName REQUIRES_QUORUM = DotName.createSimple("io.casehub.work.annotations.RequiresQuorum");
    static final DotName ESCALATE = DotName.createSimple("io.casehub.work.annotations.Escalate");
    static final DotName SKILL_MATCH = DotName.createSimple("io.casehub.work.annotations.SkillMatch");

    @BuildStep
    void processWorkAnnotations(
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<WorkAnnotationsProcessedBuildItem> processedProducer) {

        IndexView index = indexBuildItem.getIndex();

        Map<String, MethodInfo> annotatedMethods = new HashMap<>();

        collectAnnotatedMethods(index, HUMAN_APPROVAL, annotatedMethods);
        collectAnnotatedMethods(index, REQUIRES_QUORUM, annotatedMethods);
        collectAnnotatedMethods(index, ESCALATE, annotatedMethods);
        collectAnnotatedMethods(index, SKILL_MATCH, annotatedMethods);

        for (MethodInfo method : annotatedMethods.values()) {
            validateMethod(method, index);

            String declaringClass = method.declaringClass().name().toString();
            processedProducer.produce(
                    new WorkAnnotationsProcessedBuildItem(declaringClass, method.name()));

            LOG.debugf("Processed work annotations on %s.%s", declaringClass, method.name());
        }
    }

    void collectAnnotatedMethods(IndexView index, DotName annotationName,
            Map<String, MethodInfo> methods) {
        for (AnnotationInstance ann : index.getAnnotations(annotationName)) {
            if (ann.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
                MethodInfo method = ann.target().asMethod();
                String key = method.declaringClass().name() + "#" + method.name();
                methods.putIfAbsent(key, method);
            }
        }
    }

    void validateMethod(MethodInfo method, IndexView index) {
        boolean hasApproval = method.hasAnnotation(HUMAN_APPROVAL);
        boolean hasQuorum = method.hasAnnotation(REQUIRES_QUORUM);
        boolean hasEscalate = method.hasAnnotation(ESCALATE);
        boolean hasSkillMatch = method.hasAnnotation(SKILL_MATCH);

        String methodRef = method.declaringClass().name() + "." + method.name();

        if (hasEscalate && !hasApproval && !hasQuorum) {
            throw new IllegalStateException(
                    "@Escalate on method '" + methodRef + "' requires @HumanApproval or @RequiresQuorum");
        }

        if (hasSkillMatch && !hasApproval && !hasQuorum) {
            throw new IllegalStateException(
                    "@SkillMatch on method '" + methodRef + "' requires @HumanApproval or @RequiresQuorum");
        }

        if ((hasApproval || hasQuorum) && method.returnType().kind() == Type.Kind.VOID) {
            throw new IllegalStateException(
                    "@HumanApproval on method '" + methodRef + "' must return a non-void type");
        }

        if (hasQuorum) {
            AnnotationInstance quorumAnn = method.annotation(REQUIRES_QUORUM);
            int instances = quorumAnn.value("instances").asInt();
            int required = quorumAnn.value("required").asInt();
            if (instances <= 0 || required <= 0 || required > instances) {
                throw new IllegalStateException(
                        "@RequiresQuorum on method '" + methodRef
                                + "': required (" + required + ") must be > 0 and <= instances (" + instances + ")");
            }
        }

        if (hasApproval) {
            AnnotationInstance approvalAnn = method.annotation(HUMAN_APPROVAL);
            validateDuration(approvalAnn, index, "claimDeadline", methodRef, "@HumanApproval.claimDeadline");
            validateDuration(approvalAnn, index, "expiresAt", methodRef, "@HumanApproval.expiresAt");
        }

        if (hasEscalate) {
            AnnotationInstance escalateAnn = method.annotation(ESCALATE);
            validateDuration(escalateAnn, index, "deadline", methodRef, "@Escalate.deadline");
        }

        if (hasSkillMatch) {
            AnnotationInstance skillMatchAnn = method.annotation(SKILL_MATCH);
            AnnotationValue minScoreValue = skillMatchAnn.valueWithDefault(index, "minimumScore");
            if (minScoreValue != null) {
                double minScore = minScoreValue.asDouble();
                if (minScore != -1.0 && (minScore < 0.0 || minScore > 1.0)) {
                    throw new IllegalStateException(
                            "@SkillMatch.minimumScore on method '" + methodRef
                                    + "' must be in [0.0, 1.0] or -1.0 (no floor), was: " + minScore);
                }
            }
        }

        if (hasApproval && hasQuorum) {
            AnnotationInstance approvalAnn = method.annotation(HUMAN_APPROVAL);
            AnnotationInstance quorumAnn = method.annotation(REQUIRES_QUORUM);
            AnnotationValue approvalGroups = approvalAnn.valueWithDefault(index, "candidateGroups");
            AnnotationValue quorumGroups = quorumAnn.valueWithDefault(index, "candidateGroups");
            if (approvalGroups != null && quorumGroups != null) {
                String[] ag = approvalGroups.asStringArray();
                String[] qg = quorumGroups.asStringArray();
                if (ag.length > 0 && qg.length > 0) {
                    boolean differ = ag.length != qg.length;
                    if (!differ) {
                        for (int i = 0; i < ag.length; i++) {
                            if (!ag[i].equals(qg[i])) {
                                differ = true;
                                break;
                            }
                        }
                    }
                    if (differ) {
                        LOG.warnf("Both @HumanApproval and @RequiresQuorum set candidateGroups on method '%s'"
                                + " — @HumanApproval takes precedence", methodRef);
                    }
                }
            }
        }
    }

    private void validateDuration(AnnotationInstance ann, IndexView index,
            String attributeName, String methodRef, String label) {
        AnnotationValue value = ann.valueWithDefault(index, attributeName);
        if (value != null) {
            String duration = value.asString();
            if (duration != null && !duration.isEmpty()) {
                try {
                    Duration.parse(duration);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            label + " on method '" + methodRef
                                    + "' is not a valid ISO-8601 duration: '" + duration + "'");
                }
            }
        }
    }
}
