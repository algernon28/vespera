package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The invocation page {@code /invocation}, Stop's confirmation {@code /stop} (ADR-302 §2, §7) and {@code /publish}
 * (ADR-308 §6), offered on the invocation page once a Confluence rendering is drawn: the one child the control panel
 * started, shown as ADR-278 §5 and ADR-285 §5 say it ended, refreshed every {@value #REFRESH_SECONDS} seconds while it
 * runs; Stop asks to be confirmed and stops only that child (ADR-278 §6). A {@code GET} of any of them stops and starts
 * nothing (ADR-302 §1). {@code /progress} is no page: it answers a {@code GET} with a {@code 303} to the home page's
 * {@code #progress-view} (ADR-340 §2, §3), whose content {@link #progressView} writes.
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

    private final ControlPanelState state;

    InvocationPages(ControlPanelState state) {
        this.state = state;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/invocation", this::invocation);
        pages.put("/progress", this::progress);
        pages.put("/stop", this::stop);
        pages.put("/publish", this::publish);
    }

    /**
     * Starts the invocation as {@link ControlPanelState#start} does and says where to go: {@code /invocation} once it
     * started, with any line storing the launch values gave; {@code refusedAt}, with the refusal as its outcome,
     * where it did not.
     */
    static String startAndSay(ControlPanelState state, ControlPanelState.Start kind, String refusedAt) {
        return startAndSay(state, kind, refusedAt, refusedAt);
    }

    /**
     * As {@link #startAndSay(ControlPanelState, ControlPanelState.Start, String)}, a refusal said on the page {@code page}
     * and the browser sent to {@code refusedAt}, the address of a section of that page (ADR-340 §3).
     */
    static String startAndSay(ControlPanelState state, ControlPanelState.Start kind, String page, String refusedAt) {
        List<String> stored = new ArrayList<>();
        Optional<String> refusal = state.start(kind, stored);
        if (refusal.isPresent()) {
            state.say(page, refusal.get());
            return refusedAt;
        }
        if (!stored.isEmpty()) {
            state.say("/invocation", String.join("; ", stored));
        }
        return "/invocation";
    }

    private void invocation(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            Exchanges.methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        boolean runs = state.childRuns();
        Optional<Ending> ending = state.ending();
        String how = how(state);
        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(state.takeOutcome("/invocation")));
        String words = words(state, how);
        body.append(ControlPanelPage.tiles(state.lastChecks(), FolderPages.freeSpaceOf(state.effectiveWorkingDirectory()),
                how, words));
        body.append("<p id=\"child\" data-ending=\"").append(how).append("\" data-state=\"")
                .append(ControlPanelPage.stateOfInvocation(how)).append("\">")
                .append(ControlPanelPage.escape(words)).append("</p>\n");
        if (runs) {
            body.append("<form name=\"stop\" method=\"get\" action=\"/stop\">")
                    .append(ControlPanelPage.button("stop", "Stop", false)).append("</form>\n");
        }
        if (!runs && state.aRenderingIsDrawn()) {
            body.append("<form name=\"publish\" method=\"post\" action=\"/publish\">")
                    .append(ControlPanelPage.button("publish", "Publish", false)).append("</form>\n");
        }
        body.append("<h2>Output</h2>\n<pre id=\"tail\">")
                .append(ControlPanelPage.escape(String.join("\n", state.tail()))).append("</pre>\n");
        String last = ending.flatMap(Ending::lastLine).orElseGet(() -> lastNonBlank(state.tail()));
        body.append(ControlPanelPage.text("p", "last-line", last)).append('\n');
        if (state.workingDirectory().isPresent()) {
            body.append("<p><a id=\"progress\" href=\"/#progress-view\">Progress</a></p>\n");
        }
        body.append("<h2>Reports</h2>\n").append(LaunchPages.reports(state));
        Exchanges.page(exchange, ControlPanelPage.render(exchange, state, "Invocation", runs ? REFRESH_SECONDS : 0,
                body.toString()));
    }

    /** The words with a working directory the system cannot hold (ADR-340 §2). */
    private static final String NO_WORKING_DIRECTORY = "No working directory this system can hold is chosen, so there is"
            + " no progress page to show. Choose one under Settings, and the progress page of what runs there is shown"
            + " here.";

    /** The words with a working directory that holds no progress page yet (ADR-335 §1). */
    private static final String NOT_WRITTEN = "This working directory holds no progress page yet. It is shown here once"
            + " Run or Label starts and the page is first written.";

    /**
     * {@code /progress} is no page (ADR-340 §3): a {@code GET} or {@code HEAD} answers {@code 303} to the home page's
     * progress view, any other method is refused.
     */
    private void progress(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            Exchanges.methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        Exchanges.redirect(exchange, "/#progress-view");
    }

    /**
     * {@code #progress-view} of the home page (ADR-340 §2; ADR-335 §1): the progress page the jar writes, framed at its
     * report path where the working directory holds one; else the words that say why not. The home page never asks to be
     * loaded again, so a progress page first written while it is open is shown at its next load.
     */
    static String progressView(ControlPanelState state) {
        Optional<Path> folder = state.workingDirectory();
        String which;
        String inside;
        if (folder.isEmpty()) {
            which = "no-working-directory";
            inside = "<p>" + ControlPanelPage.escape(NO_WORKING_DIRECTORY) + "</p>";
        } else if (!Files.isRegularFile(folder.get().resolve("progress.html"))) {
            which = "not-written";
            inside = "<p>" + ControlPanelPage.escape(NOT_WRITTEN) + "</p>";
        } else {
            which = "shown";
            inside = "<iframe id=\"progress-frame\" src=\"/report/progress.html\" title=\"Progress page\""
                    + " sandbox=\"allow-scripts\"></iframe>\n<p><a id=\"progress-own-window\""
                    + " href=\"/report/progress.html\" target=\"_blank\" rel=\"noopener\">Open in a window of its own</a></p>";
        }
        return "<div id=\"progress-view\" data-column=\"centre\" data-progress=\"" + which + "\">" + inside + "</div>\n";
    }

    private void stop(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            if (!state.childRuns()) {
                Exchanges.redirect(exchange, "/invocation");
                return;
            }
            StringBuilder body = new StringBuilder();
            body.append(ControlPanelPage.text("p", "stop-sentence", Child.STOP_CONFIRMATION)).append('\n');
            if (state.childCommand().equals("run")) {
                body.append("<h2>What a stop costs at each step</h2>\n<table id=\"stop-costs\">\n");
                for (String[] row : STOP_COSTS) {
                    body.append("<tr data-step=\"").append(row[0]).append("\"><th>").append(row[0]).append("</th><td>")
                            .append(ControlPanelPage.escape(row[1])).append("</td></tr>\n");
                }
                body.append("</table>\n");
            }
            body.append("<form name=\"stop-confirmed\" method=\"post\" action=\"/stop\">")
                    .append(ControlPanelPage.button("stop", "Stop", false)).append("</form>\n");
            Exchanges.page(exchange, ControlPanelPage.render(exchange, state, "Stop", 0, body.toString()));
            return;
        }
        try {
            state.stop();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        Exchanges.redirect(exchange, "/invocation");
    }

    /** Starts {@code publish} (ADR-308 §6); a {@code GET} starts nothing. */
    private void publish(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/invocation");
            return;
        }
        Exchanges.form(exchange);
        Exchanges.redirect(exchange, startAndSay(state, ControlPanelState.Start.PUBLISH, "/invocation"));
    }

    /** How the child is, as {@code data-ending} has it: {@code none}, {@code running}, or how it ended. */
    static String how(ControlPanelState state) {
        return !state.childWasStarted() ? "none" : state.ending().map(InvocationPages::attribute).orElse("running");
    }

    /** The child in words, as the invocation page and the tiles say it. */
    static String words(ControlPanelState state, String how) {
        return state.childWasStarted()
                ? state.childCommand() + ": " + wordsFor(how) + state.ending().filter(e -> e.exitCode() != 0)
                        .map(e -> " (exit code " + e.exitCode() + ")").orElse("")
                : "no invocation was started since the control panel started";
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
