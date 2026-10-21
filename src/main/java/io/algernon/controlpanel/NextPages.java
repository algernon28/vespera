package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The next act {@code /next} (ADR-302 §8): what {@code next-invocation.properties} says the next invocation wants,
 * read after the control panel's own child ended with 0 and at no other time (ADR-294 §4), each value asked for with
 * its provenance, its cost and the reports that inform it, and saved by ADR-295's writes, one key after another, the
 * first refused ending the save. Where answers are wanted it leads to {@code /labels} and to {@code label-auto}.
 */
final class NextPages {

    /** §8's table: the reports each key is read off, in the order the README's table of values names them. */
    static final Map<String, List<String>> REPORTS_OF_A_KEY = Map.ofEntries(
            Map.entry("boilerplateDocumentFrequencyFloor", List.of("document-frequency.html")),
            Map.entry("relevanceScoreFloor",
                    List.of("relevance-labelling.html", "cluster-sizes.html", "seed-corpus-comparison.html")),
            Map.entry("degenerateOutputConfidenceFloor", List.of("confidence-distribution.html")),
            Map.entry("degenerateOutputWordFloor", List.of("word-count-distribution.html")),
            Map.entry("extractionAttempt", List.of("extraction-failures.html")),
            Map.entry("arrangementApproved", List.of("arrangement.html")),
            Map.entry("logTimestampShareFloor", List.of("format-mix.html")),
            Map.entry("codeNameEndings", List.of("format-mix.html")),
            Map.entry("outOfScopeNameEndings", List.of("format-mix.html")),
            Map.entry("configurationSettingShareFloor", List.of("format-mix.html")),
            Map.entry("preferredLanguages", List.of("translations.html")),
            Map.entry("translationSimilarityFloor", List.of("translations.html")),
            Map.entry("clusterPageByteCeiling", List.of("cluster-page-sizes.html")));

    /** {@code report-<key>}, the links to the reports §8's table names for the key; empty where it names none. */
    static String reportsOf(String key) {
        List<String> reports = REPORTS_OF_A_KEY.get(key);
        if (reports == null) {
            return "";
        }
        StringBuilder list = new StringBuilder("<ul id=\"report-").append(ControlPanelPage.escape(key)).append("\">");
        for (String report : reports) {
            list.append("<li>").append(ControlPanelPage.reportLink(report)).append("</li>");
        }
        return list.append("</ul>\n").toString();
    }

    private final ControlPanelState state;

    NextPages(ControlPanelState state) {
        this.state = state;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/next", this::next);
    }

    /** What the file says, or why the control panel offers nothing from it. */
    private record Read(Optional<NextInvocation> next, Optional<String> problem) {
    }

    private Read read() {
        Optional<Path> folder = state.workingDirectory();
        boolean endedWell = state.ending().map(ending -> ending.exitCode() == 0
                && ending.howItEnded() == HowItEnded.ENDED).orElse(false);
        if (folder.isEmpty() || !endedWell) {
            return new Read(Optional.empty(), Optional.empty());
        }
        try {
            return new Read(NextInvocation.read(folder.get()), Optional.empty());
        } catch (IllegalArgumentException | IOException unreadable) {
            return new Read(Optional.empty(), Optional.ofNullable(unreadable.getMessage()));
        }
    }

    /** The keys the next invocation wants, in the file's order, that the profile has; empty where none is read. */
    List<ProfileKey> wanted() {
        return read().next().map(next -> next.wants().stream()
                .map(ProfileKey::named).flatMap(Optional::stream).toList()).orElse(List.of());
    }

    private void next(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            save(exchange);
            return;
        }
        Read read = read();
        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(state.takeOutcome("/next")));
        String invocation = read.next().map(next -> next.invocation().name().toLowerCase(java.util.Locale.ROOT))
                .orElse("unknown");
        body.append("<p id=\"next-invocation\" data-invocation=\"").append(invocation).append("\">")
                .append(read.next().isPresent() ? "The next invocation is " + invocation
                        : ControlPanelPage.escape(read.problem().orElse("The next act is not known: no invocation of the control panel's has ended with 0")))
                .append("</p>\n");
        if (read.next().isPresent()) {
            NextInvocation next = read.next().get();
            List<ProfileKey> keys = wanted();
            if (!keys.isEmpty()) {
                body.append(form(keys, next));
            }
            if (next.arrangement().isPresent()) {
                Path file = state.workingDirectory().orElseThrow().toAbsolutePath().resolve("arrangement.html");
                body.append("<p>The arrangement's page, whose links open from your file manager: <code id=\"arrangement-file\">")
                        .append(ControlPanelPage.escape(file.toString())).append("</code></p>\n");
            }
            if (next.answer()) {
                body.append("<p><a id=\"labels-link\" href=\"/labels\">Answer the questions</a></p>\n")
                        .append("<form name=\"label-auto\" method=\"post\" action=\"/label-auto\">")
                        .append(ControlPanelPage.button("label-auto", "Have the local model answer", false))
                        .append("</form>\n");
            }
        }
        Exchanges.page(exchange, ControlPanelPage.render("Next", 0, body.toString()));
    }

    private String form(List<ProfileKey> keys, NextInvocation next) {
        StringBuilder form = new StringBuilder("<form name=\"next\" method=\"post\" action=\"/next\">\n");
        for (ProfileKey key : keys) {
            String name = key.key();
            String filled = key == ProfileKey.SEED_FOLDER ? state.values().lastSeedFolder()
                    : key == ProfileKey.ARRANGEMENT_APPROVED ? next.arrangement().orElse("") : "";
            form.append("<fieldset>\n");
            form.append(ControlPanelPage.field(name, name, filled, false));
            form.append(ControlPanelPage.field(name + ".provenance", name + " provenance", "", true));
            form.append(ControlPanelPage.text("p", "cost-" + name, ControlPanelPage.cost(key))).append('\n');
            form.append(reportsOf(name));
            form.append("</fieldset>\n");
        }
        form.append(ControlPanelPage.button("save", "Save", false)).append('\n')
                .append(ControlPanelPage.button("save-then-run", "Save, then Run", false)).append("\n</form>\n");
        return form.toString();
    }

    private void save(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = state.workingDirectory();
        List<ProfileKey> keys = wanted();
        if (folder.isEmpty() || keys.isEmpty()) {
            state.say("/next", "there is no value the next invocation wants to save");
            Exchanges.redirect(exchange, "/next");
            return;
        }
        List<String> saved = new ArrayList<>();
        for (ProfileKey key : keys) {
            String value = fields.getOrDefault(key.key(), "");
            String provenance = fields.getOrDefault(key.key() + ".provenance", "");
            Optional<String> refusal = state.write(folder.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.rewrite(current, key, value, provenance));
            if (refusal.isPresent()) {
                state.say("/next", refusal.get());
                Exchanges.redirect(exchange, "/next");
                return;
            }
            saved.add(key.key());
        }
        if (fields.containsKey("save-then-run")) {
            Exchanges.redirect(exchange, InvocationPages.startAndSay(state, ControlPanelState.Start.RUN, "/next"));
            return;
        }
        state.say("/next", String.join(", ", saved) + " saved in " + folder.get().resolve(ProfileEdit.FILE_NAME));
        Exchanges.redirect(exchange, "/next");
    }
}
