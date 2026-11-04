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
 * The next step, a box of the home page's right column (ADR-340 §4; ADR-302 §8): what {@code next-invocation.properties}
 * says the next invocation wants, read after the control panel's own child ended with 0 and at no other time
 * (ADR-294 §4), each value asked for with its provenance, its cost and the reports that inform it, and saved by
 * ADR-295's writes, one key after another, the first refused ending the save. Where answers are wanted it leads to the
 * relevance section of the drawer and says to tick Autolabel. {@code /next} is no page any more: a {@code GET} lands on
 * the box, and a {@code POST} is the form {@code next}'s.
 */
final class NextPages {

    /** The prefix of every id the box draws that the run settings draw too, so that the home page holds none twice. */
    static final String ID_PREFIX = "next-";

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
            Map.entry("clusterPageByteCeiling", List.of("cluster-page-sizes.html")),
            Map.entry("clusterResolution", List.of("cluster-sizes.html")));

    /** {@code report-<key>}, the links to the reports §8's table names for the key; empty where it names none. */
    static String reportsOf(String key) {
        return reportsOf("", key);
    }

    /** As {@link #reportsOf(String)}, its id written after {@code idPrefix} (ADR-340 §4). */
    static String reportsOf(String idPrefix, String key) {
        List<String> reports = REPORTS_OF_A_KEY.get(key);
        if (reports == null) {
            return "";
        }
        StringBuilder list = new StringBuilder("<ul id=\"").append(ControlPanelPage.escape(idPrefix)).append("report-")
                .append(ControlPanelPage.escape(key)).append("\">");
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
        pages.put("/label-auto", this::labelAuto);
    }

    /** What the file says, or why the control panel offers nothing from it. */
    private record Read(Optional<NextInvocation> next, Optional<String> problem, String why) {
    }

    /** §4's three cases (ADR-340): why the box suggests nothing, as {@code data-why} has it, and in words. */
    private static final String NONE_ENDED = "none-ended";
    private static final String ENDED_BADLY = "ended-badly";
    private static final String SAID_NOTHING = "said-nothing";

    private static final Map<String, String> WHY_IN_WORDS = Map.of(
            NONE_ENDED, "No run has finished yet, so there is no next step to suggest. Press Run; once a run finishes,"
                    + " this box shows what it needs next.",
            ENDED_BADLY, "The last run did not finish, so there is no next step to suggest. The Invocation page says"
                    + " how it ended; press Run again once that is put right.",
            SAID_NOTHING, "The last run finished without saying what it needs next, so there is no next step to"
                    + " suggest.");

    private Read read() {
        Optional<Path> folder = state.workingDirectory();
        Optional<Ending> ending = state.ending();
        boolean endedWell = ending.map(each -> each.exitCode() == 0 && each.howItEnded() == HowItEnded.ENDED)
                .orElse(false);
        if (!endedWell) {
            return new Read(Optional.empty(), Optional.empty(), ending.isEmpty() ? NONE_ENDED : ENDED_BADLY);
        }
        if (folder.isEmpty()) {
            return new Read(Optional.empty(), Optional.empty(), SAID_NOTHING);
        }
        try {
            Optional<NextInvocation> next = NextInvocation.read(folder.get());
            return new Read(next, Optional.empty(), next.isEmpty() ? SAID_NOTHING : "");
        } catch (IllegalArgumentException | IOException unreadable) {
            return new Read(Optional.empty(), Optional.ofNullable(unreadable.getMessage()), "");
        }
    }

    /** The keys the next invocation wants, in the file's order, that the profile has; empty where none is read. */
    List<ProfileKey> wanted() {
        return read().next().map(next -> next.wants().stream()
                .map(ProfileKey::named).flatMap(Optional::stream).toList()).orElse(List.of());
    }

    /** {@code /next}: a {@code GET} lands on the box of the home page (ADR-340 §3); a {@code POST} is the form {@code next}'s. */
    private void next(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            save(exchange);
            return;
        }
        Exchanges.redirect(exchange, "/#next-step");
    }

    /** {@code /label-auto} is no page: a {@code GET} lands on the box of the next step, any other method is refused (ADR-340 §3). */
    private void labelAuto(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/#next-step");
            return;
        }
        Exchanges.methodNotAllowed(exchange, "GET, HEAD");
    }

    /** {@code #next-step}: the box of the home page's right column (ADR-340 §4). */
    String box() {
        Read read = read();
        StringBuilder body = new StringBuilder("<section class=\"card\" id=\"next-step\" data-column=\"right\">\n"
                + "<h2>Next step</h2>\n");
        String invocation = read.next().map(next -> next.invocation().name().toLowerCase(java.util.Locale.ROOT))
                .orElse("unknown");
        String why = read.next().isPresent() || read.problem().isPresent() ? "" : read.why();
        body.append("<p id=\"next-invocation\" data-invocation=\"").append(invocation).append('"')
                .append(why.isEmpty() ? "" : " data-why=\"" + why + "\"").append('>')
                .append(read.next().isPresent() ? "The next invocation is " + invocation
                        : ControlPanelPage.escape(read.problem().orElseGet(() -> WHY_IN_WORDS.get(read.why()))))
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
                body.append("<p><a id=\"labels-link\" href=\"/?open=values-relevance#values-relevance\">Answer by folder</a>")
                        .append(" Or tick Autolabel and press Label to have the local model answer.</p>\n");
            }
        }
        return body.append("</section>\n").toString();
    }

    private String form(List<ProfileKey> keys, NextInvocation next) {
        Map<String, String> held = state.heldForm("next");
        Map<ProfileKey, ProfileEdit.Stated> profile = ValuePages.profileOf(state);
        Optional<EffectiveValues> effective = state.effectiveValues();
        StringBuilder form = new StringBuilder("<form name=\"next\" method=\"post\" action=\"/next\">\n");
        for (ProfileKey key : keys) {
            String name = key.key();
            ProfileEdit.Stated stated = profile.get(key);
            String fileValue = stated == null || stated.value() == null ? "" : stated.value();
            String fileProvenance = stated == null || stated.provenance() == null ? "" : stated.provenance();
            String filled = key == ProfileKey.SEED_FOLDER ? (fileValue.isEmpty() ? state.values().lastSeedFolder() : fileValue)
                    : key == ProfileKey.ARRANGEMENT_APPROVED ? next.arrangement().orElse("")
                    : fileValue;
            String value = held.getOrDefault(name, filled);
            boolean seedFolder = key == ProfileKey.SEED_FOLDER;
            String forName = FolderPages.Target.NEXT_SEED_FOLDER.forName();
            form.append("<fieldset>\n");
            form.append(ControlPanelPage.keyControl(key, name, ID_PREFIX + "f-" + name, ID_PREFIX, value,
                    ValuePages.valueInUse(key, stated, effective).orElse(""),
                    seedFolder ? forName : "",
                    seedFolder ? FolderPages.statusElement(forName, FolderPages.statusOf(value, false)) : ""));
            form.append(ControlPanelPage.provenance(name + ".provenance", ID_PREFIX + "f-" + name + ".provenance",
                    "Why this value?", held.getOrDefault(name + ".provenance", fileProvenance),
                    ID_PREFIX + "hint-" + name + "-provenance"));
            form.append(ControlPanelPage.costElement(ID_PREFIX, key)).append('\n');
            form.append(reportsOf(ID_PREFIX, name));
            form.append("</fieldset>\n");
        }
        form.append(ControlPanelPage.button("save", "Save", false)).append('\n')
                .append(ControlPanelPage.button("save-then-run", "Save, then Run", false)).append("\n</form>\n");
        return form.toString();
    }

    private void save(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get() == FolderPages.Target.NEXT_SEED_FOLDER) {
            state.holdForm("next", FolderPages.withoutBrowse(fields, browse.get().field()));
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(),
                    fields.getOrDefault(browse.get().field(), "").stripTrailing()));
            return;
        }
        Optional<Path> folder = state.workingDirectory();
        List<ProfileKey> keys = wanted();
        if (folder.isEmpty() || keys.isEmpty()) {
            state.say("/", "there is no value the next invocation wants to save");
            Exchanges.redirect(exchange, "/#next-step");
            return;
        }
        List<String> saved = new ArrayList<>();
        Map<ProfileKey, ProfileEdit.Stated> inFile = ValuePages.profileOf(state);
        for (ProfileKey key : keys) {
            String value = fields.getOrDefault(key.key(), "");
            String provenance = fields.getOrDefault(key.key() + ".provenance", "");
            Optional<String> refusal = ProfileEdit.refusalOfSave(key, value, provenance,
                    Optional.ofNullable(inFile.get(key)), Optional.empty());
            if (refusal.isEmpty()) {
                refusal = state.write(folder.get(), ProfileEdit.FILE_NAME,
                        current -> ProfileEdit.rewrite(current, key, value, provenance));
            }
            if (refusal.isPresent()) {
                state.say("/", refusal.get());
                Exchanges.redirect(exchange, "/#next-step");
                return;
            }
            saved.add(key.key());
        }
        state.letGoOfForm("next");
        if (fields.containsKey("save-then-run")) {
            Exchanges.redirect(exchange, InvocationPages.startAndSay(state, ControlPanelState.Start.RUN, "/"));
            return;
        }
        state.say("/", String.join(", ", saved) + " saved in " + folder.get().resolve(ProfileEdit.FILE_NAME));
        Exchanges.redirect(exchange, "/#next-step");
    }
}
