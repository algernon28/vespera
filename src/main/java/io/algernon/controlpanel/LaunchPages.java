package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The launch page {@code /} and the two paths its forms post to, {@code /launch} and {@code /seed-folder}
 * (ADR-302 §2, §4, §5, §6; ADR-331 §3): the folders and the services on the left, the seed folder's own form under
 * them, the preflight and the reports on the right. A {@code GET} of any of them reads the working directory, the lock
 * files and the sidecars and writes no file (ADR-302 §1), keeping only the sidecars' last answers in memory;
 * {@code /launch} and {@code /seed-folder} answer a
 * {@code GET} with a {@code 303} to {@code /}, and a Browse button with a {@code 303} to the folder page.
 */
final class LaunchPages {

    private static final String RULES_OF_FREE_SPACE = "Stage 4b's build of its index of shingles needs 58 bytes free on the"
            + " working directory's drive for each shingle row of the stage-2 run it reads; stage 3's grouping writes"
            + " about 22 to 23 bytes, and stage 4b's build about 91, of temporary files for each row they sort; the"
            + " deliverable's copies need the survivors' bytes plus 1 GiB free on the deliverable's drive. The control"
            + " panel computes no need: the rows and bytes are in vespera.db, to which it opens no connection.";

    private final ControlPanelState state;

    LaunchPages(ControlPanelState state) {
        this.state = state;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/", this::launchPage);
        pages.put("/launch", this::launch);
        pages.put("/seed-folder", this::seedFolder);
    }

    private void launchPage(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            Exchanges.methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        LaunchValues values = state.values();
        ControlPanelState.Holders holders = state.holders();
        boolean disabled = holders.any() || state.childRuns();
        List<String> models = modelsNamed(values);
        Optional<EffectiveValues> effective = state.effectiveValues();
        SidecarChecks.Checks checks = SidecarChecks.ask(
                namedOrSaid(values.doclingBaseUrl(), effective, "vespera.docling.base-url"),
                values.doclingImage(),
                namedOrSaid(values.ollamaBaseUrl(), effective, "spring.ai.ollama.base-url"), models);
        state.keepChecks(checks);
        List<String> warnings = new ArrayList<>(state.notes());
        warnings.addAll(values.warningsToRun());
        warnings.addAll(checks.docling().warnings());
        warnings.addAll(checks.ollama().warnings());
        String how = InvocationPages.how(state);

        String actions = ControlPanelPage.button("run", "Run", disabled)
                + ControlPanelPage.button("label", "Label", disabled) + ControlPanelPage.button("check", "Check", false);
        StringBuilder body = new StringBuilder();
        body.append(ControlPanelPage.outcome(state.takeOutcome("/")));
        body.append(ControlPanelPage.tiles(checks, FolderPages.freeSpaceOf(values.workingDirectory()), how,
                InvocationPages.words(state, how)));
        body.append(foldersCard(values));
        body.append(servicesCard(values, checks, effective));
        body.append("</form>\n");
        body.append(seedFolderCard(values));
        body.append(preflightCard(values, holders, warnings, checks));
        body.append("<section class=\"card\" id=\"card-reports\" data-column=\"right\">\n<h2>Reports</h2>\n");
        if (disabled && !values.workingDirectory().isEmpty()) {
            body.append("<p>").append(ControlPanelPage.reportLink("progress.html")).append("</p>\n");
        }
        body.append(reports(state)).append("</section>\n");
        body.append(ControlPanelPage.doclingImages());
        body.append(ControlPanelPage.datalist("models", checks.ollama().models()));
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Launch", 0,
                "<form name=\"launch\" method=\"post\" action=\"/launch\">\n", actions, true, body.toString()));
    }

    private String foldersCard(LaunchValues values) {
        return "<section class=\"card\" id=\"card-folders\" data-column=\"left\">\n<h2>Folders</h2>\n"
                + folder(FolderPages.Target.CORPUS_ROOT, "Corpus root", values.corpusRoot(), "D:\\archive\\corpus",
                        "The folder of documents Vespera reads. It is never changed.", false)
                + folder(FolderPages.Target.WORKING_DIRECTORY, "Working directory", values.workingDirectory(),
                        "D:\\vespera\\work",
                        "Where the database, the reports and profile.yaml live. The first value saved makes it if it is not"
                                + " there yet.", false)
                + deliverableRootField(values)
                + freeSpace(values) + "</section>\n";
    }

    /**
     * The Output folder with the folder each run's tree is drawn in where it is left empty as its placeholder, and
     * {@code in-use-vespera.deliverable-root} beside it (ADR-334 §1): no example, which would read as a parent of trees.
     */
    private static String deliverableRootField(LaunchValues values) {
        FolderPages.Target target = FolderPages.Target.DELIVERABLE_ROOT;
        String name = target.field();
        String working;
        String theDefault;
        try {
            working = values.workingDirectory().isBlank() ? ""
                    : Path.of(values.workingDirectory().stripTrailing()).toAbsolutePath().normalize().toString();
            theDefault = working.isEmpty() ? "" : Path.of(working).resolve("deliverable").toString();
        } catch (RuntimeException cannotBeHeld) {
            // a working directory the system cannot hold has no folder of trees; the status beside it says so
            working = values.workingDirectory().stripTrailing();
            theDefault = "";
        }
        String shown = (working.isEmpty() ? "<working directory>" : working) + File.separator + "deliverable"
                + File.separator + "<run id>";
        String typed = values.deliverableRoot();
        boolean isTheDefault = typed.isBlank() || (!theDefault.isEmpty() && namesTheSameFolder(typed, theDefault));
        String value = isTheDefault ? theDefault : typed;
        String hint = "Leave it empty to put each run's pages in a folder of their own under the working directory, as"
                + " shown; a folder named here holds one run's pages, so name an empty one.";
        return ControlPanelPage.Field.of(name, "Output folder").value(typed).inUse(shown)
                .hint("hint-" + name, hint).browse(target.forName())
                .after(FolderPages.statusElement(target.forName(), FolderPages.statusOf(typed, true))
                        + "<p id=\"in-use-" + name + "\" data-value=\"" + ControlPanelPage.escape(value)
                        + "\" data-source=\"" + (isTheDefault ? "default" : "launch-value") + "\">"
                        + ControlPanelPage.escape(isTheDefault
                                ? "In use: each run's pages go in a folder of their own under "
                                        + (theDefault.isEmpty() ? "the working directory's deliverable folder" : theDefault)
                                        + "."
                                : "In use: " + typed + ", as typed here.")
                        + "</p>\n")
                .html();
    }

    /** Whether the two name the same folder, compared by their real paths where they exist, as ADR-276 §2 compares. */
    static boolean namesTheSameFolder(String typed, String theDefault) {
        try {
            Path one = Path.of(typed.stripTrailing()).toAbsolutePath().normalize();
            Path other = Path.of(theDefault).toAbsolutePath().normalize();
            if (Files.exists(one) && Files.exists(other)) {
                return Files.isSameFile(one, other);
            }
            return one.toString().equalsIgnoreCase(other.toString())
                    && (File.separatorChar == '\\' || one.toString().equals(other.toString()));
        } catch (IOException | RuntimeException cannotBeHeld) {
            return false;
        }
    }

    /** A folder field of the launch form with its Browse button, its example, its hint and its status. */
    private static String folder(FolderPages.Target target, String label, String value, String example, String hint,
            boolean createdByTheJar) {
        String name = target.field();
        return ControlPanelPage.Field.of(name, label).value(value).example(example).hint("hint-" + name, hint)
                .browse(target.forName())
                .after(FolderPages.statusElement(target.forName(), FolderPages.statusOf(value, createdByTheJar)))
                .html();
    }

    /**
     * The keys of {@code application.yaml} the control panel shows and does not edit, in the order ADR-330 §12 gives:
     * the application's own five, then the presets of the converter, of redundancy and of relevance.
     */
    private static final List<String> MACHINE_VALUES = List.of("vespera.sidecars.start", "vespera.sidecars.wait-at-most",
            "vespera.progress-interval", "vespera.open-browser", "vespera.clustering.neighbours",
            "vespera.docling.ocr-preset", "vespera.docling.timeouts-in-a-row", "vespera.redundancy.shingle-words",
            "vespera.redundancy.minhash.permutations", "vespera.redundancy.minhash.bands",
            "vespera.redundancy.minhash.rows-per-band", "vespera.redundancy.minhash.seed",
            "vespera.redundancy.near-duplicate-jaccard", "vespera.redundancy.containment-index",
            "vespera.redundancy.rare-shingle-sample", "vespera.redundancy.rare-shingle-hits",
            "vespera.relevance.top-similarities", "vespera.relevance.sample.bands",
            "vespera.relevance.sample.per-band");

    /** The launch value where one is named, else what the jar's file says it last used for {@code key}, else empty (ADR-330 §3, §11). */
    private static String namedOrSaid(String launchValue, Optional<EffectiveValues> effective, String key) {
        if (!launchValue.isBlank()) {
            return launchValue;
        }
        return effective.flatMap(said -> said.named(key)).map(EffectiveValues.Value::value).orElse("");
    }

    /** {@code machine-values}: what the jar's file says of the keys above, with where each came from and no control (ADR-330 §12). */
    private static String machineValues(Optional<EffectiveValues> effective) {
        StringBuilder items = new StringBuilder();
        for (String key : MACHINE_VALUES) {
            effective.flatMap(said -> said.named(key)).ifPresent(value -> items.append("<li data-key=\"")
                    .append(ControlPanelPage.escape(key)).append("\" data-value=\"")
                    .append(ControlPanelPage.escape(value.value())).append("\" data-source=\"")
                    .append(ControlPanelPage.escape(value.source())).append("\">")
                    .append(ControlPanelPage.escape(key + ": " + value.value() + " (" + value.source() + ")"))
                    .append("</li>\n"));
        }
        return "<h2>What this machine runs with</h2>\n<p class=\"hint\">The application's own values: shown as the jar last"
                + " said them, and changed where the machine's configuration is, not here.</p>\n<ul id=\"machine-values\">\n"
                + items + "</ul>\n";
    }

    private String servicesCard(LaunchValues values, SidecarChecks.Checks checks, Optional<EffectiveValues> effective) {
        String running = checks.docling().running();
        boolean another = checks.docling().result() == SidecarChecks.Result.ANOTHER_IMAGE && !running.isEmpty();
        boolean named = state.doclingImageIsNamed();
        return "<section class=\"card\" id=\"card-services\" data-column=\"left\">\n<h2>Services</h2>\n"
                + ControlPanelPage.Field.of("vespera.docling.image", "Docling image").value(values.doclingImage())
                        .example(ControlPanelPage.DOCLING_IMAGE_GPU).list("docling-images")
                        .hint("hint-vespera.docling.image",
                                "The image the converter runs from. The two offered are the ones this build knows.")
                        .after("<p id=\"in-use-vespera.docling.image\" data-value=\""
                                + ControlPanelPage.escape(values.doclingImage()) + "\" data-source=\""
                                + (named ? "launch-value" : "preselected") + "\">"
                                + ControlPanelPage.escape(named
                                        ? "In use: " + values.doclingImage() + ", as typed here."
                                        : "In use: " + values.doclingImage() + ", chosen by the control panel; pick"
                                                + " the other if this machine has no graphics card.")
                                + "</p>\n")
                        .html()
                + "<p id=\"docling-running\" data-image=\"" + ControlPanelPage.escape(running) + "\">"
                + ControlPanelPage.escape(running.isEmpty() ? "The converter has not said which image it runs."
                        : "The converter says it runs " + running + ".")
                + "</p>\n"
                + (another ? ControlPanelPage.button("use-running-image", "Use the image the converter runs", false) + "\n"
                        : "")
                + serviceField("vespera.docling.base-url", "Docling address", values.doclingBaseUrl(),
                        "http://localhost:5001", "Where the converter answers.", "", effective)
                + serviceField("spring.ai.ollama.base-url", "Ollama address", values.ollamaBaseUrl(),
                        "http://localhost:11434", "Where the local models answer.", "", effective)
                + serviceField("vespera.label.model", "Label model", values.labelModel(), "qwen3:8b",
                        "The local model that answers the questions when you ask it to.", "models", effective)
                + machineValues(effective)
                + "</section>\n";
    }

    /**
     * A field of the services card, left empty, with the value the jar last said it uses as its placeholder where the
     * file names it, and {@code in-use-<name>} beside it (ADR-334 §2).
     */
    private static String serviceField(String name, String label, String typed, String example, String hint,
            String list, Optional<EffectiveValues> effective) {
        Optional<EffectiveValues.Value> said = effective.flatMap(file -> file.named(name));
        String value;
        String source;
        String fresh = "";
        String words;
        if (!typed.isEmpty()) {
            value = typed;
            source = "launch-value";
            words = "In use: " + typed + ", as typed here.";
        } else if (said.isPresent()) {
            value = said.get().value();
            source = said.get().source();
            fresh = effective.get().stale() ? "stale" : "fresh";
            words = "In use: " + value + ", from " + source + "."
                    + (effective.get().stale() ? " The jar that said so may have been replaced since." : "");
        } else {
            value = "";
            source = "not-known";
            words = "The jar has not said yet what it would use; run an invocation to see it.";
        }
        ControlPanelPage.Field field = ControlPanelPage.Field.of(name, label).value(typed).hint("hint-" + name, hint);
        if (typed.isEmpty() && said.isPresent()) {
            field.inUse(said.get().value());
        } else {
            field.example(example);
        }
        if (!list.isEmpty()) {
            field.list(list);
        }
        return field.after("<p id=\"in-use-" + name + "\" data-value=\"" + ControlPanelPage.escape(value)
                + "\" data-source=\"" + ControlPanelPage.escape(source) + "\""
                + (fresh.isEmpty() ? "" : " data-fresh=\"" + fresh + "\"") + ">" + ControlPanelPage.escape(words)
                + "</p>\n").html();
    }

    private String seedFolderCard(LaunchValues values) {
        Map<String, String> held = state.heldForm("seed-folder");
        String folder = held.getOrDefault("seedFolder", values.lastSeedFolder());
        return "<section class=\"card\" id=\"card-seed-folder\" data-column=\"left\">\n<h2>Seed folder</h2>\n"
                + "<p>A value of the profile in the working directory <code id=\"seed-folder-working-directory\">"
                + ControlPanelPage.escape(values.workingDirectory()) + "</code>.</p>\n"
                + "<form name=\"seed-folder\" method=\"post\" action=\"/seed-folder\">\n"
                + ControlPanelPage.Field.of("seedFolder", "Seed folder").value(folder)
                        .example(ProfileKey.SEED_FOLDER.example())
                        .hint("hint-seedFolder", ControlPanelPage.hintOf(ProfileKey.SEED_FOLDER))
                        .browse(FolderPages.Target.SEED_FOLDER.forName())
                        .after(FolderPages.statusElement(FolderPages.Target.SEED_FOLDER.forName(),
                                FolderPages.statusOf(folder, false)))
                        .html()
                + ControlPanelPage.provenance("provenance", "f-provenance", "Why this value?",
                        held.getOrDefault("provenance", ""), "hint-provenance")
                + ControlPanelPage.costElement(ProfileKey.SEED_FOLDER) + "\n"
                + ControlPanelPage.button("save", "Save", false) + "\n</form>\n</section>\n";
    }

    private String preflightCard(LaunchValues values, ControlPanelState.Holders holders, List<String> warnings,
            SidecarChecks.Checks checks) {
        FolderPages.Status corpusRoot = FolderPages.statusOf(values.corpusRoot(), false);
        FolderPages.Status workingDirectory = FolderPages.statusOf(values.workingDirectory(), false);
        boolean locked = holders.any() || state.childRuns();
        StringBuilder card = new StringBuilder(
                "<section class=\"card\" id=\"card-preflight\" data-column=\"right\">\n<h2>Preflight</h2>\n"
                        + "<ul id=\"preflight\">\n");
        card.append(row("corpus-root", corpusRoot.attribute().equals("found") ? "ok" : "bad",
                "Corpus root: " + corpusRoot.words()));
        card.append(row("working-directory", workingDirectory.attribute().equals("found") ? "ok" : "bad",
                "Working directory: " + workingDirectory.words()));
        if (checks.docling().result() == SidecarChecks.Result.ANOTHER_IMAGE) {
            String running = checks.docling().running().isEmpty() ? "no image" : checks.docling().running();
            card.append(row("docling", "bad", "Docling: the converter runs " + running + ", and Run would use "
                    + values.doclingImage() + "; extraction refuses a converter running another image."));
        } else {
            card.append(row("docling", ControlPanelPage.stateOfCheck(checks.docling().result()),
                    "Docling: " + ControlPanelPage.describe(checks.docling().result())));
        }
        card.append(row("ollama", ControlPanelPage.stateOfCheck(checks.ollama().result()),
                "Ollama: " + ControlPanelPage.describe(checks.ollama().result())));
        card.append(row("locks", locked ? "bad" : "ok", locked
                ? (holders.any() ? "Locks: another invocation holds a folder"
                        : "Locks: the control panel's own command runs")
                : "Locks: nothing holds a folder"));
        card.append("</ul>\n<h2>Warnings</h2>\n<ul id=\"warnings\" class=\"warnings\">");
        for (String warning : warnings) {
            card.append("<li>").append(ControlPanelPage.escape(warning)).append("</li>");
        }
        card.append("</ul>\n").append(locked ? "<h2>Locks</h2>\n" : "")
                .append(ControlPanelPage.text("p", "holder-working-directory", holders.workingDirectory().orElse("")))
                .append('\n')
                .append(ControlPanelPage.text("p", "holder-corpus-root", holders.corpusRoot().orElse("")))
                .append("\n</section>\n");
        return card.toString();
    }

    private static String row(String row, String rowState, String words) {
        return "<li data-row=\"" + row + "\" data-state=\"" + rowState + "\">" + ControlPanelPage.escape(words)
                + "</li>\n";
    }

    private String freeSpace(LaunchValues values) {
        StringBuilder items = new StringBuilder();
        FolderPages.usable(values.workingDirectory()).ifPresent(bytes -> items.append(
                "<li data-folder=\"working-directory\" data-usable-bytes=\"" + bytes
                        + "\">Working directory: " + FolderPages.gib(bytes) + "</li>"));
        FolderPages.usable(values.deliverableRoot()).ifPresent(bytes -> items.append(
                "<li data-folder=\"deliverable-root\" data-usable-bytes=\"" + bytes
                        + "\">Output folder: " + FolderPages.gib(bytes) + "</li>"));
        return "<h2>Free space</h2>\n<ul id=\"free-space\">" + items + "</ul>\n"
                + ControlPanelPage.text("p", "free-space-rules", RULES_OF_FREE_SPACE) + "\n";
    }

    /** The links to the reports the working directory holds, as {@code <ul id="reports">}. */
    static String reports(ControlPanelState state) {
        StringBuilder items = new StringBuilder();
        state.workingDirectory().ifPresent(folder -> {
            for (String report : ControlPanelPages.REPORTS) {
                if (Files.isRegularFile(folder.resolve(report), LinkOption.NOFOLLOW_LINKS)) {
                    items.append("<li>").append(ControlPanelPage.reportLink(report)).append("</li>");
                }
            }
        });
        return "<ul id=\"reports\">" + (items.isEmpty() ? "<li>No reports yet</li>" : items) + "</ul>\n";
    }

    /** The models ADR-302 §5 names: the label model, and the embedding and generation models the profile holds. */
    private List<String> modelsNamed(LaunchValues values) {
        List<String> models = new ArrayList<>();
        if (!values.labelModel().isEmpty()) {
            models.add(values.labelModel());
        }
        state.workingDirectory().ifPresent(folder -> {
            try {
                Path file = folder.resolve(ProfileEdit.FILE_NAME);
                if (Files.isRegularFile(file)) {
                    Map<ProfileKey, ProfileEdit.Stated> stated = ProfileEdit.read(Files.readString(file, StandardCharsets.UTF_8));
                    for (ProfileKey key : List.of(ProfileKey.EMBEDDING_MODEL, ProfileKey.GENERATION_MODEL)) {
                        ProfileEdit.Stated held = stated.get(key);
                        if (held != null && held.value() != null && !held.value().isBlank()
                                && !models.contains(held.value())) {
                            models.add(held.value());
                        }
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                // a profile the control panel cannot read names no model here; the profile page says why
            }
        });
        return models;
    }

    private void launch(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/");
            return;
        }
        Map<String, String> fields = Exchanges.form(exchange);
        LaunchValues old = state.values();
        String image = posted(fields, "vespera.docling.image");
        if (image.equals(ControlPanelPage.DOCLING_IMAGE_GPU) && !state.doclingImageIsNamed()) {
            image = ""; // the preselection posted back as the form held it names nothing (ADR-334 §11)
        }
        state.hold(new LaunchValues(postedFolder(fields, "vespera.corpus-root"), postedFolder(fields, "vespera.working-dir"),
                postedFolder(fields, "vespera.deliverable-root"), image,
                posted(fields, "vespera.docling.base-url"), posted(fields, "spring.ai.ollama.base-url"),
                posted(fields, "vespera.label.model"), old.lastSeedFolder()));
        if (fields.containsKey("use-running-image")) {
            // Starts nothing and writes no file: the image the converter runs becomes the launch value (ADR-334 §2).
            String running = state.lastChecks().docling().running();
            if (!running.isEmpty()) {
                state.hold(state.values().withDoclingImage(running));
            }
            Exchanges.redirect(exchange, "/");
            return;
        }
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get().form().equals("launch")) {
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(), postedFolder(fields, browse.get().field())));
            return;
        }
        ControlPanelState.Start kind = fields.containsKey("run") ? ControlPanelState.Start.RUN
                : fields.containsKey("label") ? ControlPanelState.Start.LABEL : null;
        if (kind == null) {
            Exchanges.redirect(exchange, "/");
            return;
        }
        Exchanges.redirect(exchange, InvocationPages.startAndSay(state, kind, "/"));
    }

    private void seedFolder(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/");
            return;
        }
        Map<String, String> fields = Exchanges.form(exchange);
        String folder = postedFolder(fields, "seedFolder");
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get() == FolderPages.Target.SEED_FOLDER) {
            state.holdForm("seed-folder", FolderPages.withoutBrowse(fields, browse.get().field()));
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(), folder));
            return;
        }
        Optional<Path> workingDirectory = state.workingDirectory();
        if (workingDirectory.isEmpty()) {
            state.say("/", "seedFolder is not saved: the working directory is not named");
        } else {
            Optional<String> refusal = state.write(workingDirectory.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.rewrite(current, ProfileKey.SEED_FOLDER, folder, fields.getOrDefault("provenance", "")));
            if (refusal.isEmpty()) {
                state.letGoOfForm("seed-folder");
                LaunchValues held = state.heldValues();
                state.hold(new LaunchValues(held.corpusRoot(), held.workingDirectory(), held.deliverableRoot(),
                        held.doclingImage(), held.doclingBaseUrl(), held.ollamaBaseUrl(), held.labelModel(), folder));
            }
            state.say("/", refusal.orElse("seedFolder is saved in " + workingDirectory.get().resolve(ProfileEdit.FILE_NAME)));
        }
        Exchanges.redirect(exchange, "/");
    }

    private static String posted(Map<String, String> fields, String name) {
        return fields.getOrDefault(name, "").strip();
    }

    /** A folder keeps the white space before it, which a name may begin with, and loses what follows (ADR-331). */
    private static String postedFolder(Map<String, String> fields, String name) {
        return fields.getOrDefault(name, "").stripTrailing();
    }
}
