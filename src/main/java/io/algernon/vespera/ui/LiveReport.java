package io.algernon.vespera.ui;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;

/**
 * The Report tab, first of the tabs pane and not closable (ADR-362 §8): the invocation report drawn natively, never by
 * a {@code WebView} — the status line and its stale mark, the display and what was opened, the gates with their keys
 * and the reports each is read off, the refusals, the faults by type, the closing line and the next value, the twelve
 * analysis reports and the files. A report's name opens that report's tab, and the deliverable's index is handed to
 * the system; {@code report.html} and {@code report.json} are shown as text, never opened.
 */
final class LiveReport {

    /** The tab's id. */
    static final String ID = "live-report";

    /** A report is stale where it is this many of its own intervals behind the clock while it says it runs (ADR-267 §5). */
    static final int STALE_AFTER_INTERVALS = 3;

    static final String WAITING = "waiting for this invocation's report";
    static final String NO_REPORT = "this invocation wrote no report";
    static final String UNFINISHED = "ended without its last write";
    static final String STALE = "stale";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Tab tab = new Tab("Report");
    private final Label status = new Label(WAITING);
    private final Label stale = new Label();
    private final Label ended = new Label();
    private final Label display = new Label();
    private final VBox gates = new VBox();
    private final VBox refusals = new VBox();
    private final VBox faults = new VBox();
    private final Label closing = new Label();
    private final Label next = new Label();
    private final VBox analysis = new VBox();
    private final VBox files = new VBox();
    private InvocationReportFile.Invocation drawn;
    private boolean frozen;
    private final Consumer<String> openReport;
    private final Consumer<URI> handToTheSystem;

    /** {@code openReport} opens an analysis report's tab by its file name; {@code handToTheSystem} opens a document outside. */
    LiveReport(Consumer<String> openReport, Consumer<URI> handToTheSystem) {
        this.openReport = openReport;
        this.handToTheSystem = handToTheSystem;
        status.setId("report-status");
        stale.setId("report-stale");
        ended.setId("report-ended");
        display.setId("report-display");
        gates.setId("report-gates");
        refusals.setId("report-refusals");
        faults.setId("report-faults");
        closing.setId("report-closing");
        next.setId("report-next");
        analysis.setId("report-analysis");
        files.setId("report-files");
        for (Label label : List.of(status, stale, ended, display, closing, next)) {
            label.setWrapText(true);
        }
        stale.setVisible(false);
        stale.setManaged(false);
        VBox content = new VBox(status, stale, ended, display, gates, refusals, faults, closing, next, analysis, files);
        content.getStyleClass().add("tab-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        tab.setId(ID);
        tab.setClosable(false);
        tab.setContent(scroll);
    }

    Tab tab() {
        return tab;
    }

    /** The status line reads {@code words} and nothing else is drawn from a report: the invocation's has not been written. */
    void says(String words) {
        status.setText(words);
        drawn = null;
        mark(false);
        ended.setText("");
        clear();
    }

    /** The status line when the child ended without any report of its own, with how it ended. */
    void wroteNone(String howItEnded) {
        says(NO_REPORT);
        ended.setText(howItEnded);
    }

    /**
     * Draws {@code report}, the watched invocation's, at {@code now}. {@code childEnded} is true once the child it
     * was started as has ended, so that a report still saying {@code running} is shown as ended without its last write
     * and no longer marked stale by the clock; {@code howItEnded} is the window's words for that end, else empty.
     */
    void shows(InvocationReportFile.Report report, Instant now, boolean childEnded, String howItEnded) {
        InvocationReportFile.Invocation invocation = report.invocation();
        boolean unfinished = childEnded && !invocation.hasEnded();
        String state = unfinished ? UNFINISHED : invocation.state();
        status.setText(invocation.command() + " " + state
                + (invocation.outcome() == null ? "" : ": " + invocation.outcome())
                + ". Written at " + TIME.format(invocation.writtenAt().atZone(ZoneId.systemDefault()))
                + ", every " + invocation.interval() + " s.");
        drawn = invocation;
        frozen = childEnded;
        mark(!unfinished && !childEnded && isStale(invocation, now));
        ended.setText(howItEnded);
        display.setText("Display: " + (report.display() != null && report.display().present() ? "present" : "none")
                + (report.display() == null || report.display().reason() == null ? ""
                        : " (" + report.display().reason() + ")")
                + (report.opened() == null ? "" : ". Opened: "
                        + (report.opened().files().isEmpty() ? "nothing" : String.join(", ", report.opened().files()))
                        + " (" + report.opened().because() + ")."));
        clear();
        for (InvocationReportFile.Gate gate : report.gates()) {
            gates.getChildren().add(line(gate.step() + " stopped at a gate"
                    + (gate.key() == null ? "" : ": no value for " + gate.key())
                    + (gate.analysisReports().isEmpty() ? "" : ", read off " + String.join(", ", gate.analysisReports()))));
        }
        report.refusals().forEach(each -> refusals.getChildren().add(line(each)));
        for (InvocationReportFile.Fault fault : report.faults()) {
            faults.getChildren().add(line(fault.step() + " failed: " + fault.type()));
        }
        closing.setText(report.closing() == null ? "" : report.closing());
        next.setText(report.next() == null ? "" : "Next: " + report.next());
        for (InvocationReportFile.AnalysisReport each : report.analysisReports()) {
            if (each.written()) {
                Hyperlink link = new Hyperlink(each.name());
                link.setOnAction(event -> openReport.accept(each.name() + ".html"));
                analysis.getChildren().add(link);
            } else {
                Label grey = new Label(each.name() + " (not written yet)");
                grey.getStyleClass().add("grey");
                analysis.getChildren().add(grey);
            }
        }
        String index = indexOf(report);
        if (index != null) {
            Hyperlink link = new Hyperlink(index);
            link.setId("report-index");
            link.setOnAction(event -> handToTheSystem.accept(Path.of(index).toUri()));
            files.getChildren().add(link);
        }
        for (InvocationReportFile.FileWritten each : report.files()) {
            files.getChildren().add(line(each.role() + ": " + each.path()));
        }
    }

    /** The deliverable's index, where the invocation drew a tree and its index is there. */
    private static String indexOf(InvocationReportFile.Report report) {
        return Optional.ofNullable(report.places()).map(InvocationReportFile.Places::deliverableRoot)
                .map(root -> Path.of(root).resolve("index.html")).filter(Files::isRegularFile).map(Path::toString)
                .orElse(null);
    }

    /** Marks the report drawn last stale or not by the clock, where its child has not ended; it is read again only each interval. */
    void refreshStale(Instant now) {
        if (drawn != null && !frozen) {
            mark(isStale(drawn, now));
        }
    }

    /** Whether a report that says it runs was written more than three of its intervals before {@code now}. */
    static boolean isStale(InvocationReportFile.Invocation invocation, Instant now) {
        return "running".equals(invocation.state()) && Duration.between(invocation.writtenAt(), now)
                .compareTo(Duration.ofSeconds(STALE_AFTER_INTERVALS * invocation.interval())) > 0;
    }

    private void mark(boolean isStale) {
        stale.setText(isStale ? STALE : "");
        stale.setVisible(isStale);
        stale.setManaged(isStale);
    }

    private void clear() {
        gates.getChildren().clear();
        refusals.getChildren().clear();
        faults.getChildren().clear();
        analysis.getChildren().clear();
        files.getChildren().clear();
        closing.setText("");
        next.setText("");
        display.setText("");
    }

    private static Node line(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
}
