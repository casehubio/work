package io.casehub.work.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLabel;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.LabelPersistence;

class QueueBoardBuilderTest {

    @Test
    void tier_urgentInferredLabel_returnsUrgent() {
        final var wi = workItemWithLabels(List.of("review/urgent", "review/urgent/unassigned"));
        assertThat(QueueBoardBuilder.tier(wi)).isEqualTo("review/urgent");
    }

    @Test
    void state_unassignedLabel_returnsUnassigned() {
        final var wi = workItemWithLabels(List.of("review/urgent", "review/urgent/unassigned"));
        assertThat(QueueBoardBuilder.state(wi)).isEqualTo("unassigned");
    }

    @Test
    void state_claimedLabel_returnsClaimed() {
        final var wi = workItemWithLabels(List.of("review/standard", "review/standard/claimed"));
        assertThat(QueueBoardBuilder.state(wi)).isEqualTo("claimed");
    }

    @Test
    void state_activeLabel_returnsActive() {
        final var wi = workItemWithLabels(List.of("review/routine", "review/routine/active"));
        assertThat(QueueBoardBuilder.state(wi)).isEqualTo("active");
    }

    @Test
    void tier_noLabels_returnsNull() {
        assertThat(QueueBoardBuilder.tier(WorkItem.builder().build())).isNull();
    }

    @Test
    void build_threeItems_correctlyBucketed() {
        final var urgent = workItemWithLabels("Security advisory", List.of("review/urgent", "review/urgent/unassigned"));
        final var standard = workItemWithLabels("Release notes", List.of("review/standard", "review/standard/unassigned"));
        final var routine = workItemWithLabels("Tutorial", List.of("review/routine", "review/routine/unassigned"));

        final var grid = QueueBoardBuilder.build(List.of(urgent, standard, routine));

        assertThat(grid.get("review/urgent").get("unassigned")).containsExactly("Security advisory");
        assertThat(grid.get("review/standard").get("unassigned")).containsExactly("Release notes");
        assertThat(grid.get("review/routine").get("unassigned")).containsExactly("Tutorial");
        assertThat(grid.get("review/urgent").get("claimed")).isEmpty();
    }

    @Test
    void build_itemWithNoTier_omittedFromGrid() {
        final var wi = WorkItem.builder().title("No tier item").build();
        final var grid = QueueBoardBuilder.build(List.of(wi));
        assertThat(grid.values().stream().flatMap(m -> m.values().stream()).allMatch(List::isEmpty)).isTrue();
    }

    @Test
    void formatCell_empty_returnsDash() {
        assertThat(QueueBoardBuilder.formatCell(List.of())).isEqualTo("\u2014");
    }

    @Test
    void formatCell_oneItem_returnsTitle() {
        assertThat(QueueBoardBuilder.formatCell(List.of("Security advisory"))).isEqualTo("Security advisory");
    }

    @Test
    void formatCell_multipleItems_showsPlusMore() {
        final var titles = List.of("First", "Second", "Third");
        assertThat(QueueBoardBuilder.formatCell(titles)).contains("(+2 more)");
    }

    private WorkItem workItemWithLabels(final List<String> paths) {
        return workItemWithLabels(null, paths);
    }

    private WorkItem workItemWithLabels(final String title, final List<String> paths) {
        return WorkItem.builder()
                .title(title)
                .labels(paths.stream()
                        .map(p -> new WorkItemLabel(p, LabelPersistence.INFERRED, "test-filter"))
                        .toList())
                .build();
    }
}
