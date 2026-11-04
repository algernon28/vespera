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
 * The home page {@code /} and the path its two forms post to, {@code /launch} (ADR-340 §1 to §3; ADR-302 §2, §5, §6):
 * under the top bar it holds the reports in a box on the left, the tiles and the progress page in the middle, the next
 * step, the checklist of what Run needs and what this machine runs with on the right, and then the drawer of values.
 * The top bar's form {@code launch} posts no value and starts on the values held; the drawer's form
 * {@code launch-values} holds the seven launch values and the button that holds them and asks the sidecars again. A
 * {@code GET} of either reads the working directory, the lock files and the sidecars and writes no file (ADR-302 §1),
 * keeping only the sidecars' last answers in memory; {@code /launch} answers a {@code GET} with a {@code 303} to
 * {@code /}, and a Browse button with a {@code 303} to the folder page.
 */
final class LaunchPages {

    /** How often the script reloads the home page, where it shows no line of a press and the operator is not at work on it (ADR-340 §2). */
    static final int REFRESH_SECONDS = 15;

    private static final String RULES_OF_FREE_SPACE = "Stage 4b's build of its index of shingles needs 58 bytes free on the"
            + " working directory's drive for each shingle row of the stage-2 run it reads; stage 3's grouping writes"
            + " about 22 to 23 bytes, and stage 4b's build about 91, of temporary files for each row they sort; the"
            + " deliverable's copies need the survivors' bytes plus 1 GiB free on the deliverable's drive. The control"
            + " panel computes no need: the rows and bytes are in vespera.db, to which it opens no connection.";

    /** The seven launch values, by name, in the order ADR-302 §2 gives them. */
    static final List<String> LAUNCH_VALUES = List.of("vespera.corpus-root", "vespera.working-dir",
            "vespera.deliverable-root", "vespera.docling.image", "vespera.docling.base-url", "spring.ai.ollama.base-url",
            "vespera.label.model");

    private final ControlPanelState state;
    private final NextPages next;
    private final ValuePages values;

    LaunchPages(ControlPanelState state, NextPages next, ValuePages values) {
        this.state = state;
        this.next = next;
        this.values = values;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/", this::homePage);
        pages.put("/launch", this::launch);
    }

    private void homePage(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            Exchanges.methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        Map<String, String> query = FolderPages.queryOf(exchange.getRequestURI().getRawQuery());
        String requested = query.getOrDefault("open", "");
        String open = ValuePages.SECTIONS.contains(requested) ? requested : ValuePages.SECTIONS.getFirst();
        LaunchValues held = state.values();
        ControlPanelState.Holders holders = state.holders();
        boolean disabled = holders.any() || state.childRuns();
        List<String> models = modelsNamed(held);
        Optional<EffectiveValues> effective = state.effectiveValues();
        SidecarChecks.Checks checks = SidecarChecks.ask(
                namedOrSaid(held.doclingBaseUrl(), effective, "vespera.docling.base-url"),
                held.doclingImage(),
                namedOrSaid(held.ollamaBaseUrl(), effective, "spring.ai.ollama.base-url"), models);
        state.keepChecks(checks);
        List<String> warnings = new ArrayList<>(state.notes());
        warnings.addAll(held.warningsToRun());
        warnings.addAll(checks.docling().warnings());
        warnings.addAll(checks.ollama().warnings());
        String how = InvocationPages.how(state);

        ValuePages.Section corpus = values.corpusSection(open.equals("values-corpus"), query.getOrDefault("tree", ""));
        ValuePages.Section relevance = values.relevanceSection(open.equals("values-relevance"));
        ValuePages.Section profile = values.profileSection(open.equals("values-profile"));
        String pending = state.takeOutcome("/");
        String problem = Optional.ofNullable(corpus.problem()).or(() -> Optional.ofNullable(relevance.problem()))
                .or(() -> Optional.ofNullable(profile.problem())).orElse(null);

        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(pending != null ? pending : problem));
        body.append("<div class=\"home\">\n");
        body.append("<div class=\"column\">\n").append(reportsBox(disabled)).append("</div>\n");
        body.append("<div class=\"column\">\n")
                .append(ControlPanelPage.tiles(checks, FolderPages.freeSpaceOf(state.effectiveWorkingDirectory()), how,
                        InvocationPages.words(state, how), "centre"))
                .append(InvocationPages.progressView(state)).append("</div>\n");
        body.append("<div class=\"column\">\n").append(next.box()).append(readyToRun(held, holders, warnings, checks))
                .append(machineValuesBox(effective)).append("</div>\n");
        body.append("</div>\n");
        body.append(ControlPanelPage.doclingImages());
        body.append(ControlPanelPage.listsFor(List.of(ProfileKey.values()), checks.ollama().models()));
        body.append(drawer(held, checks, effective, open, corpus, relevance, profile));
        Exchanges.page(exchange, ControlPanelPage.render(exchange, state, "Home",
                pending != null ? 0 : REFRESH_SECONDS, body.toString()));
    }

    /** {@code #reports-box}: the reports in a box that folds, drawn open (ADR-340 §2). */
    private String reportsBox(boolean disabled) {
        StringBuilder box = new StringBuilder(
                "<details id=\"reports-box\" data-column=\"left\" open>\n<summary>Reports</summary>\n");
        if (disabled) {
            box.append("<p>").append(ControlPanelPage.reportLink("progress.html")).append("</p>\n");
        }
        return box.append(reports(state)).append("</details>\n").toString();
    }

    /** {@code #ready-to-run}: the preflight as a checklist, the warnings, the locks and the free space (ADR-340 §2). */
    private String readyToRun(LaunchValues held, ControlPanelState.Holders holders, List<String> warnings,
            SidecarChecks.Checks checks) {
        FolderPages.Status corpusRoot = FolderPages.statusOf(held.corpusRoot(), false);
        FolderPages.Status workingDirectory = workingDirectoryStatus(held);
        boolean locked = holders.any() || state.childRuns();
        StringBuilder card = new StringBuilder(
                "<section class=\"card\" id=\"ready-to-run\" data-column=\"right\">\n<h2>Ready to run?</h2>\n"
                        + "<ul id=\"preflight\">\n");
        card.append(row("corpus-root", corpusRoot.attribute().equals("found") ? "ok" : "bad",
                "Corpus root: " + corpusRoot.words()));
        card.append(row("working-directory", workingDirectory.attribute().equals("found") ? "ok" : "bad",
                "Working directory: " + workingDirectory.words()));
        if (checks.docling().result() == SidecarChecks.Result.ANOTHER_IMAGE) {
            String running = checks.docling().running().isEmpty() ? "no image" : checks.docling().running();
            card.append(row("docling", "bad", "Docling: the converter runs " + running + ", and Run would use "
                    + held.doclingImage() + "; extraction refuses a converter running another image."));
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
                .append('\n').append(freeSpace(held)).append("</section>\n");
        return card.toString();
    }

    private static String row(String row, String rowState, String words) {
        return "<li data-row=\"" + row + "\" data-state=\"" + rowState + "\">" + ControlPanelPage.escape(words)
                + "</li>\n";
    }

    private String freeSpace(LaunchValues held) {
        StringBuilder items = new StringBuilder();
        FolderPages.usable(state.effectiveWorkingDirectory()).ifPresent(bytes -> items.append(
                "<li data-folder=\"working-directory\" data-usable-bytes=\"" + bytes
                        + "\">Working directory: " + FolderPages.gib(bytes) + "</li>"));
        FolderPages.usable(held.deliverableRoot()).ifPresent(bytes -> items.append(
                "<li data-folder=\"deliverable-root\" data-usable-bytes=\"" + bytes
                        + "\">Output folder: " + FolderPages.gib(bytes) + "</li>"));
        return "<h2>Free space</h2>\n<ul id=\"free-space\">" + items + "</ul>\n"
                + ControlPanelPage.text("p", "free-space-rules", RULES_OF_FREE_SPACE) + "\n";
    }

    // ---- the drawer

    /**
     * {@code #values} (ADR-340 §3): the drawer of every value the control panel edits, five sections that fold, the
     * first two inside the form {@code launch-values}.
     */
    private String drawer(LaunchValues held, SidecarChecks.Checks checks, Optional<EffectiveValues> effective,
            String open, ValuePages.Section corpus, ValuePages.Section relevance, ValuePages.Section profile) {
        return "<aside id=\"values\">\n<div class=\"drawer-head\"><h2>Settings</h2>"
                + "<a id=\"close-values\" href=\"#top-bar\">Close</a></div>\n"
                + "<form name=\"launch-values\" method=\"post\" action=\"/launch\">\n"
                + ValuePages.section("values-folders", "Folders", open.equals("values-folders"), foldersFields(held))
                + ValuePages.section("values-services", "Services", open.equals("values-services"),
                        servicesFields(held, checks, effective))
                + ControlPanelPage.button("check", "Use these and check", false) + "\n</form>\n"
                + corpus.html() + relevance.html() + profile.html() + "</aside>\n";
    }

    private String foldersFields(LaunchValues held) {
        return folder(FolderPages.Target.CORPUS_ROOT, "Documents folder", held.corpusRoot(), "D:\\archive\\corpus",
                "The folder with the documents to process. Vespera only reads it and never changes anything in it.")
                + workingDirectoryField(held)
                + deliverableRootField(held);
    }

    /** A folder field of the launch values' form with its Browse button, its example, its hint and its status. */
    private static String folder(FolderPages.Target target, String label, String value, String example, String hint) {
        String name = target.field();
        return ControlPanelPage.Field.of(name, label).key(name).value(value).example(example)
                .hint("hint-" + name, hint).browse(target.forName())
                .after(FolderPages.statusElement(target.forName(), FolderPages.statusOf(value, false)))
                .html();
    }

    /** What the work folder says of its folder: the one typed, or its default where the field is empty (ADR-340 §6). */
    private FolderPages.Status workingDirectoryStatus(LaunchValues held) {
        return FolderPages.statusOf(held.workingDirectory().isEmpty() ? state.effectiveWorkingDirectory()
                : held.workingDirectory(), false);
    }

    /**
     * The Work folder: left empty, its default is shown as its placeholder and in {@code in-use-vespera.working-dir}
     * (ADR-340 §6); the field stays empty, so nothing the operator did not type is passed or saved.
     */
    private String workingDirectoryField(LaunchValues held) {
        FolderPages.Target target = FolderPages.Target.WORKING_DIRECTORY;
        String name = target.field();
        String typed = held.workingDirectory();
        boolean isTheDefault = typed.isEmpty();
        String inUse = state.effectiveWorkingDirectory();
        ControlPanelPage.Field field = ControlPanelPage.Field.of(name, "Work folder").key(name).value(typed)
                .hint("hint-" + name, "Where Vespera keeps its database, its reports and profile.yaml. Left empty, it is"
                        + " the folder this machine is set to use, or else the .vespera folder of the folder the control"
                        + " panel was started from, as shown. If it is not there yet, the first value you save makes it.")
                .browse(target.forName());
        // a default no path can hold names no folder in use: the example stands instead (ADR-340 §6)
        boolean heldNoPath = isTheDefault && inUse.isEmpty();
        if (isTheDefault && !heldNoPath) {
            field.inUse(inUse);
        } else {
            field.example("D:\\vespera\\work");
        }
        WorkingDirectoryDefault machine = state.workingDirectoryDefault();
        String source = isTheDefault ? machine.source() : "launch-value";
        String given = writtenWithCodes(machine.folder());
        String words = !isTheDefault ? "In use: " + typed + ", as typed here."
                : heldNoPath && machine.source().equals(WorkingDirectoryDefault.SYSTEM_PROPERTY)
                ? "None in use: the system property " + machine.name() + " the control panel was started with names "
                        + given + ", which is no path this system can hold, so no working directory is chosen."
                : heldNoPath && machine.source().equals(WorkingDirectoryDefault.ENVIRONMENT)
                ? "None in use: the environment variable " + machine.name() + " names " + given
                        + ", which is no path this system can hold, so no working directory is chosen."
                : switch (machine.source()) {
                    case WorkingDirectoryDefault.SYSTEM_PROPERTY -> "In use: " + inUse + ", from the system property "
                            + machine.name() + " the control panel was started with.";
                    case WorkingDirectoryDefault.ENVIRONMENT -> "In use: " + inUse + ", from the environment variable "
                            + machine.name() + ".";
                    default -> "In use: " + inUse
                            + ", the .vespera folder of the folder the control panel was started from.";
                };
        return field.after(FolderPages.statusElement(target.forName(), workingDirectoryStatus(held))
                + "<p id=\"in-use-" + name + "\" data-value=\"" + ControlPanelPage.escape(inUse) + "\" data-source=\""
                + source + "\""
                + (isTheDefault && !machine.name().isEmpty()
                        ? " data-source-name=\"" + ControlPanelPage.escape(machine.name()) + "\"" : "")
                + ">" + ControlPanelPage.escape(words)
                + "</p>\n").html();
    }

    /** The value as given, each character below U+0020 written {@code U+} and its four hexadecimal digits (ADR-340 §6). */
    private static String writtenWithCodes(String value) {
        StringBuilder written = new StringBuilder();
        value.chars().forEach(c -> {
            if (c < 0x20) {
                written.append(String.format("U+%04X", c));
            } else {
                written.append((char) c);
            }
        });
        return written.toString();
    }

    /**
     * The Output folder with the folder each run's tree is drawn in where it is left empty as its placeholder, and
     * {@code in-use-vespera.deliverable-root} beside it (ADR-334 §1): no example, which would read as a parent of trees.
     */
    private String deliverableRootField(LaunchValues held) {
        FolderPages.Target target = FolderPages.Target.DELIVERABLE_ROOT;
        String name = target.field();
        String working;
        String theDefault;
        try {
            working = state.effectiveWorkingDirectory().isBlank() ? ""
                    : Path.of(state.effectiveWorkingDirectory().stripTrailing()).toAbsolutePath().normalize().toString();
            theDefault = working.isEmpty() ? "" : Path.of(working).resolve("deliverable").toString();
        } catch (RuntimeException cannotBeHeld) {
            // a working directory the system cannot hold has no folder of trees; the status beside it says so
            working = state.effectiveWorkingDirectory().stripTrailing();
            theDefault = "";
        }
        String shown = (working.isEmpty() ? "<work folder>" : working) + File.separator + "deliverable"
                + File.separator + "<run id>";
        String typed = held.deliverableRoot();
        boolean isTheDefault = typed.isBlank() || (!theDefault.isEmpty() && namesTheSameFolder(typed, theDefault));
        String value = isTheDefault ? theDefault : typed;
        String hint = "Leave it empty to put each run's pages in a folder of their own under the work folder, as shown;"
                + " a folder named here holds one run's pages, so name an empty one.";
        return ControlPanelPage.Field.of(name, "Output folder").key(name).value(typed).inUse(shown)
                .hint("hint-" + name, hint).browse(target.forName())
                .after(FolderPages.statusElement(target.forName(), FolderPages.statusOf(typed, true))
                        + "<p id=\"in-use-" + name + "\" data-value=\"" + ControlPanelPage.escape(value)
                        + "\" data-source=\"" + (isTheDefault ? "default" : "launch-value") + "\">"
                        + ControlPanelPage.escape(isTheDefault
                                ? "In use: each run's pages go in a folder of their own under "
                                        + (theDefault.isEmpty() ? "the work folder's deliverable folder" : theDefault)
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

    /**
     * {@code #machine-values-box}: what the jar's file says of the keys above, with where each came from and no
     * control, in a box that folds, drawn closed (ADR-330 §12, ADR-340 §2).
     */
    private static String machineValuesBox(Optional<EffectiveValues> effective) {
        StringBuilder items = new StringBuilder();
        for (String key : MACHINE_VALUES) {
            effective.flatMap(said -> said.named(key)).ifPresent(value -> items.append("<li data-key=\"")
                    .append(ControlPanelPage.escape(key)).append("\" data-value=\"")
                    .append(ControlPanelPage.escape(value.value())).append("\" data-source=\"")
                    .append(ControlPanelPage.escape(value.source())).append("\">")
                    .append(ControlPanelPage.escape(key + ": " + value.value() + " (" + value.source() + ")"))
                    .append("</li>\n"));
        }
        return "<details id=\"machine-values-box\" data-column=\"right\">\n<summary>What this machine runs with</summary>\n"
                + "<p class=\"hint\">The application's own values: shown as the jar last said them, and changed where the"
                + " machine's configuration is, not here.</p>\n<ul id=\"machine-values\">\n" + items + "</ul>\n</details>\n";
    }

    private String servicesFields(LaunchValues held, SidecarChecks.Checks checks, Optional<EffectiveValues> effective) {
        String running = checks.docling().running();
        boolean another = checks.docling().result() == SidecarChecks.Result.ANOTHER_IMAGE && !running.isEmpty();
        boolean named = state.doclingImageIsNamed();
        return ControlPanelPage.Field.of("vespera.docling.image", "Converter build").key("vespera.docling.image")
                        .value(held.doclingImage())
                        .example(ControlPanelPage.DOCLING_IMAGE_GPU).list("docling-images")
                        .hint("hint-vespera.docling.image",
                                "Which build of the document converter to use: GPU where this machine has a graphics"
                                        + " card, CPU otherwise.")
                        .after("<p id=\"in-use-vespera.docling.image\" data-value=\""
                                + ControlPanelPage.escape(held.doclingImage()) + "\" data-source=\""
                                + (named ? "launch-value" : "preselected") + "\">"
                                + ControlPanelPage.escape(named
                                        ? "In use: " + held.doclingImage() + ", as typed here."
                                        : "In use: " + held.doclingImage() + ", chosen by the control panel; pick"
                                                + " the other if this machine has no graphics card.")
                                + "</p>\n")
                        .html()
                + "<p id=\"docling-running\" data-image=\"" + ControlPanelPage.escape(running) + "\">"
                + ControlPanelPage.escape(running.isEmpty() ? "The converter has not said which image it runs."
                        : "The converter says it runs " + running + ".")
                + "</p>\n"
                + (another ? ControlPanelPage.button("use-running-image", "Use the image the converter runs", false) + "\n"
                        : "")
                + serviceField("vespera.docling.base-url", "Converter address", held.doclingBaseUrl(),
                        "http://localhost:5001", "Where the document converter answers. Change it only if you moved it.",
                        "", effective)
                + serviceField("spring.ai.ollama.base-url", "Local models address", held.ollamaBaseUrl(),
                        "http://localhost:11434", "Where the local AI models answer.", "", effective)
                + serviceField("vespera.label.model", "Answering model", held.labelModel(), "qwen3:8b",
                        "The local model that answers the relevance questions when you press Label with Autolabel"
                                + " ticked.",
                        "models", effective);
    }

    /**
     * A field of the services section, left empty, with the value the jar last said it uses as its placeholder where the
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
        ControlPanelPage.Field field = ControlPanelPage.Field.of(name, label).key(name).value(typed)
                .hint("hint-" + name, hint);
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
    private List<String> modelsNamed(LaunchValues held) {
        List<String> models = new ArrayList<>();
        if (!held.labelModel().isEmpty()) {
            models.add(held.labelModel());
        }
        state.workingDirectory().ifPresent(folder -> {
            try {
                Path file = folder.resolve(ProfileEdit.FILE_NAME);
                if (Files.isRegularFile(file)) {
                    Map<ProfileKey, ProfileEdit.Stated> stated = ProfileEdit.read(Files.readString(file, StandardCharsets.UTF_8));
                    for (ProfileKey key : List.of(ProfileKey.EMBEDDING_MODEL, ProfileKey.GENERATION_MODEL)) {
                        ProfileEdit.Stated each = stated.get(key);
                        if (each != null && each.value() != null && !each.value().isBlank()
                                && !models.contains(each.value())) {
                            models.add(each.value());
                        }
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                // a profile the control panel cannot read names no model here; the run settings say why
            }
        });
        return models;
    }

    // ---- /launch

    /**
     * {@code /launch} (ADR-340 §1, §3): a post that names none of the seven launch values, which only the top bar's form
     * sends, keeps the values held; one that names them, which only the drawer's form sends, replaces them.
     */
    private void launch(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/");
            return;
        }
        Map<String, String> fields = Exchanges.form(exchange);
        boolean namesAValue = LAUNCH_VALUES.stream().anyMatch(fields::containsKey);
        if (namesAValue) {
            LaunchValues old = state.values();
            String image = posted(fields, "vespera.docling.image");
            if (image.equals(ControlPanelPage.DOCLING_IMAGE_GPU) && !state.doclingImageIsNamed()) {
                image = ""; // the preselection posted back as the form held it names nothing (ADR-334 §11)
            }
            state.hold(new LaunchValues(postedFolder(fields, "vespera.corpus-root"),
                    postedFolder(fields, "vespera.working-dir"), postedFolder(fields, "vespera.deliverable-root"), image,
                    posted(fields, "vespera.docling.base-url"), posted(fields, "spring.ai.ollama.base-url"),
                    posted(fields, "vespera.label.model"), old.lastSeedFolder()));
        }
        if (fields.containsKey("use-running-image")) {
            // Starts nothing and writes no file: the image the converter runs becomes the launch value (ADR-334 §2).
            String running = state.lastChecks().docling().running();
            if (!running.isEmpty()) {
                state.hold(state.values().withDoclingImage(running));
            }
            Exchanges.redirect(exchange, "/?open=values-services#values");
            return;
        }
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get().form().equals("launch-values")) {
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(), postedFolder(fields, browse.get().field())));
            return;
        }
        ControlPanelState.Start kind = fields.containsKey("run") ? ControlPanelState.Start.RUN
                : fields.containsKey("label")
                        ? (fields.containsKey("label-auto") ? ControlPanelState.Start.LABEL_AUTO
                                : ControlPanelState.Start.LABEL)
                : null;
        if (kind == null) {
            // Check: the sidecars are asked again as the page is drawn
            Exchanges.redirect(exchange, namesAValue ? "/?open=values-folders#values" : "/");
            return;
        }
        Exchanges.redirect(exchange, InvocationPages.startAndSay(state, kind, "/"));
    }

    private static String posted(Map<String, String> fields, String name) {
        return fields.getOrDefault(name, "").strip();
    }

    /** A folder keeps the white space before it, which a name may begin with, and loses what follows (ADR-331). */
    private static String postedFolder(Map<String, String> fields, String name) {
        return fields.getOrDefault(name, "").stripTrailing();
    }
}
