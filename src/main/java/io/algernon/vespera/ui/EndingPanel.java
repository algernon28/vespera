package io.algernon.vespera.ui;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The ending panel beneath the output (ADR-417): a small panel that scrolls, saying how the last invocation of the
 * working directory chosen ended and why. It works out what it says from the window's own child's {@link Ending} and
 * the invocation report that child wrote, or from the report of an earlier invocation (§2, §3), and draws it as a
 * stripe, five selectable texts, the gates, the next value and the files of the ending's report where it holds them
 * (ADR-432 §8), and two buttons (§1, §4). It words no advice: every line is the jar's, the output's, or one of the
 * fixed words here. Every colour is the stylesheet's; no node here sets one.
 */
final class EndingPanel {

    /** The words by which a line says to set a value in the Profile window (ADR-389 §6). */
    static final String THE_PROFILE_WINDOW = "Profile window";

    static final String FAILED_OR_REFUSED = "Failed or refused";
    static final String DEFECT = "Defect of the control panel";
    static final String STOPPED = "Stopped";
    static final String ENDED = "Ended";
    static final String ENDED_WITHOUT_ITS_LAST_WRITE = "Ended without its last write";
    /** The kind and the line the panel says while no working directory is chosen (ADR-436 §4). */
    static final String NO_WORKING_DIRECTORY = "No working directory";
    static final String NONE_IS_CHOSEN = "No working directory is chosen. Choose one from the list, or press Choose a"
            + " folder\u2026 to pick a folder.";
    static final String A_BUG_OF_THE_CONTROL_PANEL = "This is a bug of the control panel, not of your settings.";

    private static final DateTimeFormatter DAY_AND_MINUTE = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final String BEFORE_ANY_STEP = "before any step";
    private static final String SEPARATOR = " · ";

    /**
     * What the panel says of one ending: each text, empty where it is not shown, whether the stripe and the kind are
     * drawn in the error colour (else the accent), and the parts of the ending's report (ADR-432 §8): a line for each
     * gate, the next value, a line for each file written, and the deliverable's index where it is there.
     */
    record Said(String when, String kind, String line, String faults, String facts, boolean bad, List<String> gates,
            String next, List<String> files, String index) {
    }

    // ---- what it says (§2, §3)

    /**
     * What the panel says of the window's own child's {@code ending}: {@code own} is the report the child wrote, as
     * ADR-362 §8 accepts it, empty where it wrote none, and {@code sawItEnd} the instant the window saw it end.
     */
    static Said ofTheChild(Ending ending, Optional<InvocationReportFile.Report> own, Instant sawItEnd, ZoneId zone) {
        InvocationReportFile.Report report = own.orElse(null);
        InvocationReportFile.Invocation invocation = report == null ? null : report.invocation();
        Instant ended = invocation != null && invocation.ended() != null ? invocation.ended() : sawItEnd;
        Optional<String> step = report == null ? Optional.empty() : stepOf(report);
        List<String> cause = causeOf(report);
        if (cause.isEmpty()) {
            ending.lastLine().ifPresent(cause::add);
        }
        return said(ending.howItEnded(), ending.exitCode(), report, cause, step, "", clock(ended, zone),
                durationOf(invocation));
    }

    /**
     * What the panel says of an earlier invocation, read from its {@code report.json}: dated, as it ended with its
     * exit code, or as it ended without its last write. Empty where the report holds no invocation, or one that has
     * ended without an exit code.
     */
    static Optional<Said> ofAnEarlier(InvocationReportFile.Report report, ZoneId zone) {
        InvocationReportFile.Invocation invocation = report.invocation();
        if (invocation == null) {
            return Optional.empty();
        }
        Optional<String> step = stepOf(report);
        List<String> cause = causeOf(report);
        if (!invocation.hasEnded()) {
            Instant written = invocation.writtenAt();
            String when = "Last invocation, " + DAY_AND_MINUTE.withZone(zone).format(written);
            return Optional.of(said(null, 0, report, cause, step, when, clock(written, zone), null));
        }
        if (invocation.exitCode() == null) {
            return Optional.empty();
        }
        Instant ended = invocation.ended() != null ? invocation.ended() : invocation.writtenAt();
        String when = "Last invocation, " + DAY_AND_MINUTE.withZone(zone).format(ended);
        return Optional.of(said(HowItEnded.of(false, invocation.exitCode()), invocation.exitCode(), report, cause, step,
                when, clock(ended, zone), durationOf(invocation)));
    }

    /** {@code how} is null for an invocation that ended without its last write; {@code time} is then its last write's. */
    private static Said said(HowItEnded how, int exitCode, InvocationReportFile.Report report, List<String> cause,
            Optional<String> step, String when, String time, String duration) {
        String faults = report == null ? "" : String.join("\n", report.faults().stream().map(EndingPanel::faultOf).toList());
        List<String> gates = report == null ? List.of() : report.gates().stream().map(EndingPanel::gateOf).toList();
        String next = report == null || report.next() == null ? "" : "Next: " + report.next();
        List<String> files = report == null ? List.of()
                : report.files().stream().map(each -> each.role() + ": " + each.path()).toList();
        String index = report == null ? "" : indexOf(report);
        if (how == null) {
            return new Said(when, ENDED_WITHOUT_ITS_LAST_WRITE, String.join("\n", cause), faults,
                    step.map(each -> "At " + each).orElse("Before any step") + SEPARATOR + "last written " + time,
                    false, gates, next, files, index);
        }
        return switch (how) {
            case FAILED_OR_REFUSED -> new Said(when, FAILED_OR_REFUSED, String.join("\n", cause), faults,
                    step.map(each -> "Stopped at " + each).orElse("Stopped before any step") + SEPARATOR + "exit "
                            + exitCode + SEPARATOR + "ended " + time, true, gates, next, files, index);
            case DEFECT_OF_THE_CONTROL_PANEL -> new Said(when, DEFECT, String.join("\n", cause), faults,
                    A_BUG_OF_THE_CONTROL_PANEL, true, gates, next, files, index);
            case STOPPED -> {
                List<String> lines = new ArrayList<>();
                lines.add("Stopped by you " + step.map(each -> "at " + each).orElse(BEFORE_ANY_STEP));
                if (report != null && report.closing() != null && !report.closing().isBlank()) {
                    lines.add(report.closing());
                }
                yield new Said(when, STOPPED, String.join("\n", lines), faults, "", false, gates, next, files, index);
            }
            case ENDED -> new Said(when, ENDED, String.join("\n", cause), faults,
                    "ended " + time + (duration == null ? "" : SEPARATOR + "took " + duration), false, gates, next,
                    files, index);
        };
    }

    /** A gate: the step stopped at it, the profile key whose absence stopped it, and the analysis reports that key is read off. */
    private static String gateOf(InvocationReportFile.Gate gate) {
        return gate.step() + " stopped at a gate"
                + (gate.key() == null ? "" : ": no value for " + gate.key())
                + (gate.analysisReports().isEmpty() ? "" : ", read off " + String.join(", ", gate.analysisReports()));
    }

    /** The deliverable's index, where the invocation drew a tree and its index is there; empty otherwise. */
    private static String indexOf(InvocationReportFile.Report report) {
        if (report.places() == null || report.places().deliverableRoot() == null) {
            return "";
        }
        try {
            Path index = Path.of(report.places().deliverableRoot()).resolve("index.html");
            return Files.isRegularFile(index) ? index.toString() : "";
        } catch (InvalidPathException unusable) {
            return "";
        }
    }

    /** Each {@code refusal}, whole, in order; then the {@code closing}, where it is none of them. */
    private static List<String> causeOf(InvocationReportFile.Report report) {
        List<String> lines = new ArrayList<>();
        if (report == null) {
            return lines;
        }
        lines.addAll(report.refusals());
        String closing = report.closing();
        if (closing != null && !closing.isBlank() && !lines.contains(closing)) {
            lines.add(closing);
        }
        return lines;
    }

    private static String faultOf(InvocationReportFile.Fault fault) {
        return (fault.step() == null ? "" : fault.step() + ": ") + fault.type()
                + (fault.causes().isEmpty() ? "" : " (caused by " + String.join(", ", fault.causes()) + ")");
    }

    /** The first step that failed; else the last that was running; else the step of the first fault naming one. */
    private static Optional<String> stepOf(InvocationReportFile.Report report) {
        for (InvocationReportFile.Step each : report.steps()) {
            if ("failed".equals(each.state())) {
                return Optional.of(each.name());
            }
        }
        String running = null;
        for (InvocationReportFile.Step each : report.steps()) {
            if ("running".equals(each.state())) {
                running = each.name();
            }
        }
        if (running != null) {
            return Optional.of(running);
        }
        return report.faults().stream().map(InvocationReportFile.Fault::step).filter(java.util.Objects::nonNull)
                .findFirst();
    }

    private static String clock(Instant at, ZoneId zone) {
        return CLOCK.withZone(zone).format(at);
    }

    /** {@code <h> h <m> min <s> s}, the hours written only from one hour and the minutes only from one minute; null where unknown. */
    private static String durationOf(InvocationReportFile.Invocation invocation) {
        if (invocation == null || invocation.started() == null || invocation.ended() == null
                || invocation.ended().isBefore(invocation.started())) {
            return null;
        }
        Duration took = Duration.between(invocation.started(), invocation.ended());
        long hours = took.toHours();
        long minutes = took.toMinutesPart();
        return (hours > 0 ? hours + " h " : "") + (hours > 0 || minutes > 0 ? minutes + " min " : "")
                + took.toSecondsPart() + " s";
    }

    // ---- what it draws (§1, §4, §5)

    private final SelectableText.Wrapping when = SelectableText.wrapping("");
    private final SelectableText.Wrapping kind = SelectableText.wrapping("");
    private final SelectableText.Wrapping line = SelectableText.wrapping("");
    private final SelectableText.Wrapping faults = SelectableText.wrapping("");
    private final SelectableText.Wrapping facts = SelectableText.wrapping("");
    private final VBox gates = new VBox();
    private final SelectableText.Wrapping next = SelectableText.wrapping("");
    private final VBox files = new VBox();
    private final Consumer<URI> handToTheSystem;
    private final Button openProfile = new Button("Open profile");
    private final Button chooseAFolder = new Button("Choose a folder\u2026");
    private final Button copy = new Button("Copy details");
    private final Region stripe = new Region();
    private final HBox stripeAndBody = new HBox();
    private final ScrollPane scroll = new ScrollPane(stripeAndBody);

    /**
     * {@code openProfile} opens the Profile window; {@code handToTheSystem} opens the deliverable's index, which the
     * ending's report names, outside the window; {@code chooseAFolder} does what Browse does (ADR-436 §5).
     */
    EndingPanel(Runnable openProfile, Consumer<URI> handToTheSystem, Runnable chooseAFolder) {
        this.handToTheSystem = handToTheSystem;
        when.setId("ending-when");
        kind.setId("ending-kind");
        line.setId("ending-line");
        faults.setId("ending-faults");
        facts.setId("ending-facts");
        gates.setId("ending-gates");
        next.setId("ending-next");
        files.setId("ending-files");
        this.openProfile.setId("ending-open-profile");
        this.chooseAFolder.setId("ending-choose-folder");
        copy.setId("ending-copy");
        stripe.setId("ending-stripe");
        stripe.getStyleClass().add("ending-stripe");
        when.getStyleClass().add("dim");
        facts.getStyleClass().add("dim");
        kind.getStyleClass().add("field-name");
        offer(gates, false);
        offer(files, false);
        this.openProfile.setOnAction(event -> openProfile.run());
        this.chooseAFolder.setOnAction(event -> chooseAFolder.run());
        offer(this.chooseAFolder, false);
        copy.setOnAction(event -> copyTheTexts());
        HBox buttons = new HBox(this.openProfile, this.chooseAFolder, copy);
        buttons.getStyleClass().add("spaced-row");
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(when, kind, line, faults, facts, gates, next, files, buttons);
        body.getStyleClass().add("ending-body");
        HBox.setHgrow(body, Priority.ALWAYS);
        stripeAndBody.setId("ending-panel");
        stripeAndBody.getStyleClass().add("ending-panel");
        stripeAndBody.getChildren().addAll(stripe, body);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("ending-scroll");
    }

    /** The scroll the panel stands in, to be put in the split beneath the output. */
    ScrollPane node() {
        return scroll;
    }

    /** Draws {@code said}. */
    void shows(Said said) {
        text(when, said.when());
        text(kind, said.kind());
        text(line, said.line());
        text(faults, said.faults());
        text(facts, said.facts());
        for (String colour : List.of("ending-bad", "ending-accent")) {
            stripe.getStyleClass().remove(colour);
            kind.getStyleClass().remove(colour);
        }
        String colour = said.bad() ? "ending-bad" : "ending-accent";
        stripe.getStyleClass().add(colour);
        kind.getStyleClass().add(colour);
        gates.getChildren().setAll(said.gates().stream().map(each -> (Node) SelectableText.wrapping(each)).toList());
        offer(gates, !said.gates().isEmpty());
        text(next, said.next());
        List<Node> filesShown = new ArrayList<>();
        if (!said.index().isEmpty()) {
            Hyperlink link = new Hyperlink(said.index());
            link.setId("ending-index");
            link.setOnAction(event -> handToTheSystem.accept(Path.of(said.index()).toUri()));
            filesShown.add(link);
        }
        said.files().forEach(each -> filesShown.add(SelectableText.wrapping(each)));
        files.getChildren().setAll(filesShown);
        offer(files, !filesShown.isEmpty());
        offer(openProfile, said.line().contains(THE_PROFILE_WINDOW));
        offer(chooseAFolder, false);
        offer(copy, true);
    }

    /**
     * Says that no working directory is chosen, in place of an ending (ADR-436 §4): the kind and the line, in the error
     * colour, no date, fault or facts, the button that picks a folder, and Copy details.
     */
    void showsThatNoneIsChosen() {
        shows(new Said("", NO_WORKING_DIRECTORY, NONE_IS_CHOSEN, "", "", true, List.of(), "", List.of(), ""));
        offer(chooseAFolder, true);
    }

    private static void text(SelectableText.Wrapping part, String words) {
        part.setText(words);
        part.setVisible(!words.isEmpty());
        part.setManaged(!words.isEmpty());
    }

    private static void offer(Node part, boolean offered) {
        part.setVisible(offered);
        part.setManaged(offered);
    }

    /**
     * The texts shown, {@code ending-when} to {@code ending-facts}, then the gates, the next value and the files, a line
     * each, onto the system's clipboard.
     */
    private void copyTheTexts() {
        List<String> shown = new ArrayList<>();
        for (SelectableText.Wrapping each : List.of(when, kind, line, faults, facts)) {
            if (each.isVisible() && !each.getText().isEmpty()) {
                shown.add(each.getText());
            }
        }
        for (Node each : gates.getChildren()) {
            if (each instanceof SelectableText.Wrapping gate) {
                shown.add(gate.getText());
            }
        }
        if (next.isVisible() && !next.getText().isEmpty()) {
            shown.add(next.getText());
        }
        for (Node each : files.getChildren()) {
            if (each instanceof SelectableText.Wrapping file) {
                shown.add(file.getText());
            } else if (each instanceof Hyperlink index) {
                shown.add(index.getText());
            }
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(String.join("\n", shown));
        Clipboard.getSystemClipboard().setContent(content);
    }
}
