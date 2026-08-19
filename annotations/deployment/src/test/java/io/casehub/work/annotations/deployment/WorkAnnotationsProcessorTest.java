package io.casehub.work.annotations.deployment;

import io.casehub.work.annotations.Escalate;
import io.casehub.work.annotations.HumanApproval;
import io.casehub.work.annotations.RequiresQuorum;
import io.casehub.work.annotations.SkillMatch;
import io.casehub.work.api.OnThresholdReached;
import io.casehub.work.api.WorkItemPriority;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkAnnotationsProcessorTest {

    private WorkAnnotationsProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WorkAnnotationsProcessor();
    }

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
            try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
                indexer.index(stream);
            }
        }
        return indexer.complete();
    }

    @Test
    void validHumanApproval_noError() throws IOException {
        Index index = indexClasses(ValidApprovalService.class,
                HumanApproval.class, WorkItemPriority.class);
        Map<String, MethodInfo> methods = new HashMap<>();
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.HUMAN_APPROVAL, methods);
        assertThat(methods).hasSize(1);
        assertThatCode(() -> processor.validateMethod(methods.values().iterator().next(), index))
                .doesNotThrowAnyException();
    }

    @Test
    void escalateAlone_throwsError() throws IOException {
        Index index = indexClasses(EscalateAloneService.class, Escalate.class);
        Map<String, MethodInfo> methods = new HashMap<>();
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.ESCALATE, methods);
        assertThat(methods).hasSize(1);
        assertThatThrownBy(() -> processor.validateMethod(methods.values().iterator().next(), index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires @HumanApproval or @RequiresQuorum");
    }

    @Test
    void skillMatchAlone_throwsError() throws IOException {
        Index index = indexClasses(SkillMatchAloneService.class, SkillMatch.class);
        Map<String, MethodInfo> methods = new HashMap<>();
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.SKILL_MATCH, methods);
        assertThat(methods).hasSize(1);
        assertThatThrownBy(() -> processor.validateMethod(methods.values().iterator().next(), index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires @HumanApproval or @RequiresQuorum");
    }

    @Test
    void voidReturn_throwsError() throws IOException {
        Index index = indexClasses(VoidReturnService.class,
                HumanApproval.class, WorkItemPriority.class);
        Map<String, MethodInfo> methods = new HashMap<>();
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.HUMAN_APPROVAL, methods);
        assertThat(methods).hasSize(1);
        assertThatThrownBy(() -> processor.validateMethod(methods.values().iterator().next(), index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must return a non-void type");
    }

    @Test
    void quorumOutOfRange_throwsError() throws IOException {
        Index index = indexClasses(QuorumOutOfRangeService.class,
                RequiresQuorum.class, OnThresholdReached.class);
        Map<String, MethodInfo> methods = new HashMap<>();
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.REQUIRES_QUORUM, methods);
        assertThat(methods).hasSize(1);
        assertThatThrownBy(() -> processor.validateMethod(methods.values().iterator().next(), index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required (5) must be > 0 and <= instances (3)");
    }

    @Test
    void invalidDuration_throwsError() throws IOException {
        Index index = indexClasses(InvalidDurationService.class,
                HumanApproval.class, WorkItemPriority.class);
        Map<String, MethodInfo> methods = new HashMap<>();
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.HUMAN_APPROVAL, methods);
        assertThat(methods).hasSize(1);
        assertThatThrownBy(() -> processor.validateMethod(methods.values().iterator().next(), index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid ISO-8601 duration");
    }

    @Test
    void minimumScoreOutOfRange_throwsError() throws IOException {
        Index index = indexClasses(MinScoreOutOfRangeService.class,
                HumanApproval.class, WorkItemPriority.class, SkillMatch.class);
        Map<String, MethodInfo> methods = new HashMap<>();
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.HUMAN_APPROVAL, methods);
        processor.collectAnnotatedMethods(index, WorkAnnotationsProcessor.SKILL_MATCH, methods);
        assertThat(methods).hasSize(1);
        assertThatThrownBy(() -> processor.validateMethod(methods.values().iterator().next(), index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be in [0.0, 1.0] or -1.0");
    }

    @ApplicationScoped
    public static class ValidApprovalService {
        @HumanApproval(title = "Test", candidateGroups = "team")
        public String approve(String input) { return null; }
    }

    @ApplicationScoped
    public static class EscalateAloneService {
        @Escalate(onExpiry = "managers")
        public String escalate(String input) { return null; }
    }

    @ApplicationScoped
    public static class SkillMatchAloneService {
        @SkillMatch(strategy = "semantic")
        public String match(String input) { return null; }
    }

    @ApplicationScoped
    public static class VoidReturnService {
        @HumanApproval(title = "Void", candidateGroups = "team")
        public void approve(String input) {}
    }

    @ApplicationScoped
    public static class QuorumOutOfRangeService {
        @RequiresQuorum(instances = 3, required = 5)
        public String review(String input) { return null; }
    }

    @ApplicationScoped
    public static class InvalidDurationService {
        @HumanApproval(title = "Bad", candidateGroups = "team", claimDeadline = "not-a-duration")
        public String approve(String input) { return null; }
    }

    @ApplicationScoped
    public static class MinScoreOutOfRangeService {
        @HumanApproval(title = "Bad", candidateGroups = "team")
        @SkillMatch(minimumScore = 1.5)
        public String approve(String input) { return null; }
    }
}
