package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The invocation page {@code /invocation}, Stop's confirmation {@code /stop}, and {@code /label-auto} (ADR-302 §2,
 * §7): the one child the control panel started, shown as ADR-278 §5 and ADR-285 §5 say it ended, refreshed every
 * {@value #REFRESH_SECONDS} seconds while it runs; Stop asks to be confirmed and stops only that child (ADR-278 §6).
 * A {@code GET} of any of them stops and starts nothing (ADR-302 §1).
 */
final class InvocationPages {

    /** How often the page asks to be loaded again while the child runs. */
    static final int REFRESH_SECONDS = 2;

    /** §7's table: a row per step of {@code vespera run}, in the order they run, with what a stop there costs. */
    static final List<String[]> STOP_COSTS = List.of(
            new String[] {"census", "the entries walked since the last commit, about every thousand entries, which are walked again"},
            new String[] {"byte-level-reduction", "the step, done again from the start; quick next to extraction"},
            new String[] {"extraction", "at most the batch open when it stopped; no conversion stored is made again"},
            new String[] {"content-census", "the pass, done again"},
            new String[] {"redundancy-signature", "its own work, done again from the stored text; the other step's finished work stays"},
            new String[] {"content-redundancy", "its own work, done again; the signatures stay"},
            new String[] {"seed-extraction", "its own work, done again, the conversions read back from the extraction cache"},
            new String[] {"seed-corpus-comparison", "its own work, done again"},
            new String[] {"embedding-scoring", "nothing computed: every vector stored is kept"},
            new String[] {"translation", "its decisions, made again"},
            new String[] {"relevance-scoring", "the scores, computed again from the stored vectors"},
            new String[] {"relevance-floor", "nothing: it decides again on every invocation"},
            new String[] {"clustering", "the groups, formed again from the stored vectors"},
            new String[] {"relevance-report", "nothing: its two files are written again on every invocation that reaches it"},
            new String[] {"arrangement", "the arrangement, made again"},
            new String[] {"generation", "the group being written, asked about again; every group written is kept"});

    private final PanelSession session;

    InvocationPages(PanelSession session) {
        this.session = session;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/invocation", this::invocation);
        pages.put("/stop", this::stop);
        pages.put("/label-auto", this::labelAuto);
    }

    /**
     * Starts the invocation as {@link PanelSession#start} does and says where to go: {@code /invocation} once it
     * started, with any line storing the launch values gave; {@code refusedAt}, with the refusal as its outcome,
     * where it did not.
     */
    static String startAndSay(PanelSession session, PanelSession.Start kind, String refusedAt) {
        List<String> stored = new ArrayList<>();
        Optional<String> refusal = session.start(kind, stored);
        if (refusal.isPresent()) {
            session.say(refusedAt, refusal.get());
            return refusedAt;
        }
        if (!stored.isEmpty()) {
            session.say("/invocation", String.join("; ", stored));
        }
        return "/invocation";
    }

    private void invocation(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            Exchanges.methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        boolean runs = session.childRuns();
        Optional<Ending> ending = session.ending();
        String how = !session.childWasStarted() ? "none" : ending.map(InvocationPages::attribute).orElse("running");
        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(session.takeOutcome("/invocation")));
        String words = session.childWasStarted()
                ? session.childCommand() + ": " + wordsFor(how) + ending.filter(e -> e.exitCode() != 0)
                        .map(e -> " (exit code " + e.exitCode() + ")").orElse("")
                : "no invocation was started since the control panel started";
        body.append("<p id=\"child\" data-ending=\"").append(how).append("\">")
                .append(ControlPanelPage.escape(words)).append("</p>\n");
        if (runs) {
            body.append("<form name=\"stop\" method=\"get\" action=\"/stop\">")
                    .append(ControlPanelPage.button("stop", "Stop", false)).append("</form>\n");
        }
        body.append("<h2>Output</h2>\n<pre id=\"tail\">")
                .append(ControlPanelPage.escape(String.join("\n", session.tail()))).append("</pre>\n");
        String last = ending.flatMap(Ending::lastLine).orElseGet(() -> lastNonBlank(session.tail()));
        body.append(ControlPanelPage.text("p", "last-line", last)).append('\n');
        if (session.workingDirectory().isPresent()) {
            body.append("<p><a id=\"progress\" href=\"/report/progress.html\" target=\"_blank\" rel=\"noopener\">Progress</a></p>\n");
        }
        body.append("<h2>Reports</h2>\n").append(LaunchPages.reports(session));
        Exchanges.page(exchange, ControlPanelPage.render("Invocation", runs ? REFRESH_SECONDS : 0, body.toString()));
    }

    private void stop(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            if (!session.childRuns()) {
                Exchanges.redirect(exchange, "/invocation");
                return;
            }
            StringBuilder body = new StringBuilder();
            body.append(ControlPanelPage.text("p", "stop-sentence", Child.STOP_CONFIRMATION)).append('\n');
            if (session.childCommand().equals("run")) {
                body.append("<h2>What a stop costs at each step</h2>\n<table id=\"stop-costs\">\n");
                for (String[] row : STOP_COSTS) {
                    body.append("<tr data-step=\"").append(row[0]).append("\"><th>").append(row[0]).append("</th><td>")
                            .append(ControlPanelPage.escape(row[1])).append("</td></tr>\n");
                }
                body.append("</table>\n");
            }
            body.append("<form name=\"stop-confirmed\" method=\"post\" action=\"/stop\">")
                    .append(ControlPanelPage.button("stop", "Stop", false)).append("</form>\n");
            Exchanges.page(exchange, ControlPanelPage.render("Stop", 0, body.toString()));
            return;
        }
        try {
            session.stop();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        Exchanges.redirect(exchange, "/invocation");
    }

    private void labelAuto(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/");
            return;
        }
        Exchanges.form(exchange);
        Exchanges.redirect(exchange, startAndSay(session, PanelSession.Start.LABEL_AUTO, "/next"));
    }

    private static String attribute(Ending ending) {
        return switch (ending.howItEnded()) {
            case ENDED -> "ended";
            case FAILED_OR_REFUSED -> "failed-or-refused";
            case DEFECT_OF_THE_CONTROL_PANEL -> "defect";
            case STOPPED -> "stopped";
        };
    }

    private static String wordsFor(String how) {
        return switch (how) {
            case "running" -> "running";
            case "ended" -> "ended";
            case "failed-or-refused" -> "failed or was refused";
            case "defect" -> "ended on a defect of the control panel's own";
            case "stopped" -> "stopped by the control panel";
            default -> how;
        };
    }

    private static String lastNonBlank(List<String> lines) {
        return lines.reversed().stream().filter(line -> !line.isBlank()).findFirst().orElse("");
    }
}
