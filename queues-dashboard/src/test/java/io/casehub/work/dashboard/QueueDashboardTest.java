package io.casehub.work.dashboard;

import dev.tamboui.tui.pilot.Pilot;
import dev.tamboui.tui.pilot.TuiTestRunner;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end Pilot tests for the Tamboui queue board dashboard.
 *
 * <p>
 * Uses {@link TuiTestRunner} to drive the dashboard headlessly — no real terminal needed.
 * The TUI runs on a background thread; the test thread interacts via {@link Pilot}.
 * Assertions check CDI bean state ({@link ReviewStepService#currentStep()}) after each
 * interaction, which is the correct pattern for TuiRunner-based apps.
 *
 * <p>
 * {@code dashboard::handleEvent} and {@code dashboard::renderBoard} are the exact method
 * references passed to {@link TuiRunner#run(dev.tamboui.tui.EventHandler, dev.tamboui.tui.Renderer)}
 * in production. {@code TuiTestRunner} drives the same code paths headlessly.
 *
 * <p>
 * {@code TestBackend} is published in {@code dev.tamboui:tamboui-core:test-fixtures} —
 * add that dependency alongside {@code tamboui-tui:test-fixtures} and these tests run
 * against the published Maven snapshot with no local Tamboui build required.
 */
@QuarkusTest
class QueueDashboardTest {

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    @Inject
    QueueDashboard dashboard;

    @Inject
    ReviewStepService stepService;

    @BeforeEach
    void reset() throws Exception {
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(stepService::reset);
    }

    @Test
    void press_s_once_advances_to_step1_setup() throws Exception {
        try (final TuiTestRunner test = TuiTestRunner.runTest(
                dashboard::handleEvent, dashboard::renderBoard)) {
            final Pilot pilot = test.pilot();

            pilot.press('s');
            awaitStep(1, "After one 's' press, step should be 1 (setup: 3 documents created)");
        }
    }

    @Test
    void press_s_twice_advances_to_step2_claim() throws Exception {
        try (final TuiTestRunner test = TuiTestRunner.runTest(
                dashboard::handleEvent, dashboard::renderBoard)) {
            final Pilot pilot = test.pilot();

            pilot.press('s');
            awaitStep(1, "step 1");
            pilot.press('s');
            awaitStep(2, "After two 's' presses, step should be 2 (claim: advisory ASSIGNED)");
        }
    }

    @Test
    void press_s_four_times_completes_full_scenario() throws Exception {
        try (final TuiTestRunner test = TuiTestRunner.runTest(
                dashboard::handleEvent, dashboard::renderBoard)) {
            final Pilot pilot = test.pilot();

            pilot.press('s');
            awaitStep(1, "setup");
            pilot.press('s');
            awaitStep(2, "claim");
            pilot.press('s');
            awaitStep(3, "start");
            pilot.press('s');
            awaitStep(4, "After four 's' presses, step should be 4 (complete: advisory COMPLETED)");

            final var advisoryId = stepService.getAdvisoryId();
            assertThat(advisoryId).isNotNull();
        }
    }

    @Test
    void press_r_resets_to_step0() throws Exception {
        try (final TuiTestRunner test = TuiTestRunner.runTest(
                dashboard::handleEvent, dashboard::renderBoard)) {
            final Pilot pilot = test.pilot();

            pilot.press('s');
            awaitStep(1, "setup");
            pilot.press('s');
            awaitStep(2, "claim");

            pilot.press('r');
            awaitStep(0, "After 'r', step should reset to 0");
        }
    }

    @Test
    void press_q_quits_gracefully() throws Exception {
        // Verifies the TUI runner shuts down cleanly on 'q' without hanging
        try (final TuiTestRunner test = TuiTestRunner.runTest(
                dashboard::handleEvent, dashboard::renderBoard)) {
            final Pilot pilot = test.pilot();

            pilot.press('q');
            pilot.pause(Duration.ofMillis(100));
        }
        // If we reach here without timeout, quit worked
    }

    @Test
    void render_does_not_throw_with_empty_item_list() throws Exception {
        // Verifies the renderer handles an empty queue gracefully
        try (final TuiTestRunner test = TuiTestRunner.runTest(
                dashboard::handleEvent, dashboard::renderBoard)) {
            final Pilot pilot = test.pilot();

            // No 's' pressed — queue is empty, renderBoard fires on each tick
            pilot.pause(Duration.ofMillis(100));

            // Still alive and step is still 0
            assertThat(stepService.currentStep()).isEqualTo(0);
        }
    }

    private void awaitStep(int expected, String description) {
        await(description).atMost(STEP_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() ->
                        assertThat(stepService.currentStep()).isEqualTo(expected));
    }
}
