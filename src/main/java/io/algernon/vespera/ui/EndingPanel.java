package io.algernon.vespera.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
 * stripe, five selectable texts and three buttons (§1, §4). It words no advice: every line is the jar's, the output's,
 * or one of the fixed words here. Every colour is the stylesheet's; no node here sets one.
 */
final class EndingPanel {

    /** The words by which a line says to set a value in the Profile window (ADR-389 §6). */
    static final String THE_PROFILE_WINDOW = "Profile window";

    static final String FAILED_OR_REFUSED = "Failed or refused";
    static final String DEFECT = "Defect of the control panel";
    static final String STOPPED = "Stopped";
    static final String ENDED = "Ended";
    static final String ENDED_WITHOUT_ITS_LAST_WRITE = "Ended without its last write";
    static final String A_BUG_OF_THE_CONTROL_PANEL = "This is a bug of the control panel, not of your settings.";

    private static final DateTimeFormatter DAY_AND_MINUTE = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final String BEFORE_ANY_STEP = "before any step";
    private static final String SEPARATOR = " · ";

    /**
     * What the panel says of one ending: each text, empty where it is not shown, whether the stripe and the kind are
     * drawn in the error colour (else the accent), and whether the ending has a {@code report.json} of its own.
     */
    record Said(String when, String kind, String line, String faults, String facts, boolean bad, boolean openReport) {
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
                durationOf(invocation), own.isPresent());
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
            return Optional.of(said(null, 0, report, cause, step, when, clock(written, zone), null, true));
        }
        if (invocation.exitCode() == null) {
            return Optional.empty();
        }
        Instant ended = invocation.ended() != null ? invocation.ended() : invocation.writtenAt();
        String when = "Last invocation, " + DAY_AND_MINUTE.withZone(zone).format(ended);
        return Optional.of(said(HowItEnded.of(false, invocation.exitCode()), invocation.exitCode(), report, cause, step,
                when, clock(ended, zone), durationOf(invocation), true));
    }

    /** {@code how} is null for an invocation that ended without its last write; {@code time} is then its last write's. */
    private static Said said(HowItEnded how, int exitCode, InvocationReportFile.Report report, List<String> cause,
            Optional<String> step, String when, String time, String duration, boolean ownReport) {
        String faults = report == null ? "" : String.join("\n", report.faults().stream().map(EndingPanel::faultOf).toList());
        if (how == null) {
            return new Said(when, ENDED_WITHOUT_ITS_LAST_WRITE, String.join("\n", cause), faults,
                    step.map(each -> "At " + each).orElse("Before any step") + SEPARATOR + "last written " + time,
                    false, ownReport);
        }
        return switch (how) {
            case FAILED_OR_REFUSED -> new Said(when, FAILED_OR_REFUSED, String.join("\n", cause), faults,
                    step.map(each -> "Stopped at " + each).orElse("Stopped before any step") + SEPARATOR + "exit "
                            + exitCode + SEPARATOR + "ended " + time, true, ownReport);
            case DEFECT_OF_THE_CONTROL_PANEL -> new Said(when, DEFECT, String.join("\n", cause), faults,
                    A_BUG_OF_THE_CONTROL_PANEL, true, ownReport);
            case STOPPED -> {
                List<String> lines = new ArrayList<>();
                lines.add("Stopped by you " + step.map(each -> "at " + each).orElse(BEFORE_ANY_STEP));
                if (report != null && report.closing() != null && !report.closing().isBlank()) {
                    lines.add(report.closing());
                }
                yield new Said(when, STOPPED, String.join("\n", lines), faults, "", false, ownReport);
            }
            case ENDED -> new Said(when, ENDED, String.join("\n", cause), faults,
                    "ended " + time + (duration == null ? "" : SEPARATOR + "took " + duration), false, ownReport);
        };
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
    private final Button openProfile = new Button("Open profile");
    private final Button openReport = new Button("Open report");
    private final Button copy = new Button("Copy details");
    private final Region stripe = new Region();
    private final HBox stripeAndBody = new HBox();
    private final ScrollPane scroll = new ScrollPane(stripeAndBody);

    /** {@code openProfile} opens the Profile window; {@code openReport} selects the Report tab. */
    EndingPanel(Runnable openProfile, Runnable openReport) {
        when.setId("ending-when");
        kind.setId("ending-kind");
        line.setId("ending-line");
        faults.setId("ending-faults");
        facts.setId("ending-facts");
        this.openProfile.setId("ending-open-profile");
        this.openReport.setId("ending-open-report");
        copy.setId("ending-copy");
        stripe.setId("ending-stripe");
        stripe.getStyleClass().add("ending-stripe");
        when.getStyleClass().add("dim");
        facts.getStyleClass().add("dim");
        kind.getStyleClass().add("field-name");
        this.openProfile.setOnAction(event -> openProfile.run());
        this.openReport.setOnAction(event -> openReport.run());
        copy.setOnAction(event -> copyTheTexts());
        HBox buttons = new HBox(this.openProfile, this.openReport, copy);
        buttons.getStyleClass().add("spaced-row");
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(when, kind, line, faults, facts, buttons);
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
        offer(openProfile, said.line().contains(THE_PROFILE_WINDOW));
        offer(openReport, said.openReport());
        offer(copy, true);
    }

    private static void text(SelectableText.Wrapping part, String words) {
        part.setText(words);
        part.setVisible(!words.isEmpty());
        part.setManaged(!words.isEmpty());
    }

    private static void offer(Button button, boolean offered) {
        button.setVisible(offered);
        button.setManaged(offered);
    }

    /** The texts shown, {@code ending-when} to {@code ending-facts}, a line each, onto the system's clipboard. */
    private void copyTheTexts() {
        List<String> shown = new ArrayList<>();
        for (SelectableText.Wrapping each : List.of(when, kind, line, faults, facts)) {
            if (each.isVisible() && !each.getText().isEmpty()) {
                shown.add(each.getText());
            }
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(String.join("\n", shown));
        Clipboard.getSystemClipboard().setContent(content);
    }
}
