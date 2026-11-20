package io.algernon.vespera.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The invocation's status line and its stale mark, {@code report-status} and {@code report-stale}, which stand in the
 * header under the database's size while the window watches an invocation that has not ended, its own child's or a
 * foreign holder's, and are neither shown nor laid out otherwise (ADR-432 §8, as ADR-362 §8 has what they say). Once the
 * invocation has ended, the ending panel says how (ADR-417).
 */
final class InvocationsStatusLine {

    /** A report is stale where it is this many of its own intervals behind the clock while it says it runs (ADR-267 §5). */
    static final int STALE_AFTER_INTERVALS = 3;

    static final String WAITING = "waiting for this invocation's report";
    static final String STALE = "stale";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final SelectableText.Line status = SelectableText.line("");
    private final SelectableText.Line stale = SelectableText.line("");
    private InvocationReportFile.Invocation drawn;

    InvocationsStatusLine() {
        status.setId("report-status");
        stale.setId("report-stale");
        showing(status, false);
        showing(stale, false);
    }

    /** The status line, to stand in the header. */
    SelectableText.Line statusLine() {
        return status;
    }

    /** The stale mark, to stand in the header after the status line. */
    SelectableText.Line staleMark() {
        return stale;
    }

    /** The invocation is watched and its report is not read yet: the line says it waits, and no report is drawn. */
    void says(String words) {
        status.setText(words);
        showing(status, true);
        drawn = null;
        mark(false);
    }

    /** Neither the line nor the mark is shown: no invocation is watched, or the one watched has ended. */
    void hide() {
        drawn = null;
        showing(status, false);
        mark(false);
    }

    /**
     * Draws {@code report}, the watched invocation's, at {@code now}. Where {@code childEnded} is true, the child it was
     * started as having ended, or the report says it ended, nothing is shown: the ending panel says how it ended.
     */
    void shows(InvocationReportFile.Report report, Instant now, boolean childEnded) {
        InvocationReportFile.Invocation invocation = report.invocation();
        if (childEnded || invocation.hasEnded()) {
            hide();
            return;
        }
        status.setText(invocation.command() + " " + invocation.state()
                + (invocation.outcome() == null ? "" : ": " + invocation.outcome())
                + ". Written at " + TIME.format(invocation.writtenAt().atZone(ZoneId.systemDefault()))
                + ", every " + invocation.interval() + " s.");
        showing(status, true);
        drawn = invocation;
        mark(isStale(invocation, now));
    }

    /** Marks the report drawn last stale or not by the clock; it is read again only each interval. */
    void refreshStale(Instant now) {
        if (drawn != null) {
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
        showing(stale, isStale);
    }

    private static void showing(SelectableText.Line line, boolean shown) {
        line.setVisible(shown);
        line.setManaged(shown);
    }
}
