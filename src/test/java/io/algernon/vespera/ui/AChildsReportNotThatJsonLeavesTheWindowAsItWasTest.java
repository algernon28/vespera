package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code report.json} that is not that JSON leaves the window as it was (ADR-362 section 8), even where it names the
 * window's own child: an invocation whose {@code started} is null, which the schema requires and the jar always writes,
 * and an instant no clock of any zone can show, which the jar never writes, are not that JSON. The window then says of
 * its child, once it has ended, what it says of one that wrote no report, and goes on reading every second.
 *
 * <p>Each window test draws the window on the Headless platform (ADR-342 section 6) through {@link WindowForTests}; its
 * child is {@link PrintingJars#endingAsTold()}, writing the report it is handed with its own pid in it, so that the
 * report would be taken for its child's were it that JSON.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("739")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class AChildsReportNotThatJsonLeavesTheWindowAsItWasTest {

    /** The exit code of a child that failed. */
    private static final int FAILS = 1;

    /** The line the child prints before it ends, its last line of output that is not a report line. */
    private static final String ITS_LAST_LINE = "vespera run failed at extraction: the converter did not answer.";

    /** The word the window says a failed child ended with (ADR-278 section 5). */
    private static final String FAILED_OR_REFUSED = "Failed or refused";

    /** The earliest and the latest instant {@link Instant} holds, which no clock of any zone can show. */
    private static final String THE_EARLIEST_INSTANT = Instant.MIN.toString();
    private static final String THE_LATEST_INSTANT = Instant.MAX.toString();

    /** A job of one step, running, ended by the child with exit code 1, as {@link ReportFixtures} reads one. */
    private static final String A_FAILED_JOB = "{\"ended\": true, \"exitCode\": " + FAILS + ", \"outcome\": \"failed\","
            + " \"endedAt\": \"%s\", \"steps\": [{\"name\": \"census\", \"state\": \"failed\", \"done\": null,"
            + " \"total\": null}], \"charts\": [], \"closing\": \"" + ITS_LAST_LINE + "\"}";

    /**
     * What the window says once the child has ended: the kind of ending and the line the ending panel says, and the
     * child's line. Since ADR-432 §8 the ending panel says how a child that wrote no report of its own ended, the Report
     * tab gone.
     */
    private record Said(String kind, String howItEnded, String child) {
    }

    @Test
    @Story("The window shows only the watched invocation's report")
    @DisplayName("A child whose own report.json says it started at no time ends as one that wrote no report, its last line of output how it ended, and the window does not stop reading")
    void aChildsReportStartedAtNoTimeIsNoReportOfItsOwn(@TempDir Path folder) throws Exception {
        Said said = saidOnceTheChildEnded(folder, report -> report.replaceFirst("\"started\":\"[^\"]*\"",
                "\"started\":null"));

        claim("the child's report.json says it started at no time, not that JSON, so once it has ended the ending panel"
                        + " says it failed or was refused, with its last line of output, as of a child that wrote no"
                        + " report, and the child's line the same",
                () -> assertThat(said).satisfies(
                        each -> assertThat(each.kind()).isEqualTo(FAILED_OR_REFUSED),
                        each -> assertThat(each.howItEnded()).contains(ITS_LAST_LINE),
                        each -> assertThat(each.child()).contains(ITS_LAST_LINE)));
    }

    @Test
    @Story("The window shows only the watched invocation's report")
    @DisplayName("A child whose own report.json was last written at the latest instant there is ends as one that wrote no report, and the window does not stop reading")
    void aChildsReportWrittenAtTheLatestInstantIsNoReportOfItsOwn(@TempDir Path folder) throws Exception {
        Said said = saidOnceTheChildEnded(folder, report -> report.replaceFirst("\"writtenAt\":\"[^\"]*\"",
                "\"writtenAt\":\"" + THE_LATEST_INSTANT + "\""));

        claim("the child's report.json says it was last written at an instant no clock can show, not that JSON, so once"
                        + " it has ended the ending panel says it failed or was refused, and how it ended is its last"
                        + " line of output",
                () -> assertThat(List.of(said.kind(), said.howItEnded().contains(ITS_LAST_LINE)))
                        .containsExactly(FAILED_OR_REFUSED, true));
    }

    @Test
    @Story("The window shows only the watched invocation's report")
    @DisplayName("A child whose own report.json says it ended at the latest instant there is ends as one that wrote no report, and the window does not stop reading")
    void aChildsReportEndedAtTheLatestInstantIsNoReportOfItsOwn(@TempDir Path folder) throws Exception {
        Said said = saidOnceTheChildEnded(folder, report -> report.replaceFirst("\"ended\":\"[^\"]*\"",
                "\"ended\":\"" + THE_LATEST_INSTANT + "\""));

        claim("the child's report.json says it ended at an instant no clock can show, not that JSON, so once it has"
                        + " ended the ending panel says it failed or was refused, and how it ended is its last line of"
                        + " output",
                () -> assertThat(List.of(said.kind(), said.howItEnded().contains(ITS_LAST_LINE)))
                        .containsExactly(FAILED_OR_REFUSED, true));
    }

    @Test
    @Story("The window shows only the watched invocation's report")
    @DisplayName("A report.json whose start, end or last write is the earliest or the latest instant there is is not that JSON")
    void aReportAtAnInstantNoClockCanShowIsNotThatJson() {
        String report = ReportFixtures.reportOf(A_FAILED_JOB.formatted(Instant.now()), 1L,
                Instant.now().truncatedTo(ChronoUnit.MILLIS), Instant.now(), 1);
        List<String> atTheEdges = new ArrayList<>();
        for (String field : List.of("started", "ended", "writtenAt")) {
            for (String instant : List.of(THE_EARLIEST_INSTANT, THE_LATEST_INSTANT)) {
                atTheEdges.add(report.replaceFirst("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":\"" + instant
                        + "\""));
            }
        }

        claim("each of the six reports, its start, its end or its last write at the earliest or the latest instant, is"
                        + " read as not that JSON, and the report they were made from is read",
                () -> assertThat(atTheEdges).hasSize(6).doesNotContain(report).allSatisfy(each -> assertThatThrownBy(
                        () -> InvocationReportFile.read(each)).isExactlyInstanceOf(IllegalArgumentException.class)));
        claim("the report they were made from is that JSON",
                () -> assertThat(InvocationReportFile.read(report).invocation().pid()).isEqualTo(1L));
    }

    // ---- the child and the window

    /**
     * Starts the window's own child in a working directory under {@code folder}, telling it to write the report of
     * {@link #A_FAILED_JOB}, as {@code spoilt} makes it, print {@link #ITS_LAST_LINE} and fail; and reads what the window
     * says once the child has ended.
     */
    private static Said saidOnceTheChildEnded(Path folder, java.util.function.UnaryOperator<String> spoilt)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant started = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
        Instant ended = started.plusSeconds(2);
        String report = ReportFixtures.reportOf(A_FAILED_JOB.formatted(ended), 0L, started, ended, 1);
        String spoiltReport = spoilt.apply(report);
        assertThat(spoiltReport).as("the report handed to the child is spoilt as the test says").isNotEqualTo(report);
        toldToEnd(working, spoiltReport);
        ControlPanelState state = WindowForTests.aState(folder, PrintingJars.endingAsTold());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));

        try (WindowForTests window = WindowForTests.shown(state)) {
            window.readEverySecond();
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            String kind = window.awaiting(root -> shownText(root, "ending-kind"), FAILED_OR_REFUSED::equals);
            return window.read(root -> new Said(kind, shownText(root, "ending-line"), textOf(root, "child")));
        }
    }

    /** The words of the ending panel's text {@code id} where it is there and shown; empty otherwise. */
    private static String shownText(Parent root, String id) {
        return HeadlessToolkit.nodeWithId(root, id).filter(javafx.scene.Node::isVisible)
                .map(node -> ((TextInputControl) node).getText()).orElse("");
    }

    /** Tells the child started in {@code working} to print {@link #ITS_LAST_LINE}, write {@code report}, and fail. */
    private static void toldToEnd(Path working, String report) throws IOException {
        assertThat(report).as("the report handed to the child names its pid as the child replaces it")
                .contains(AChildEndingAsToldForTests.ITS_PID);
        Files.writeString(working.resolve(AChildEndingAsToldForTests.TOLD),
                FAILS + "\n" + ITS_LAST_LINE + "\n" + report + "\n", UTF_8);
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class).getText();
    }
}
