package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
        SidecarChecks.Checks checks = SidecarChecks.ask(values.doclingBaseUrl(), values.doclingImage(),
                values.ollamaBaseUrl(), models);
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
        body.append(servicesCard(values));
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
                        "Where the database, the reports and profile.yaml live. Create it if it is not there yet.", false)
                + folder(FolderPages.Target.DELIVERABLE_ROOT, "Output folder", values.deliverableRoot(),
                        "D:\\vespera\\deliverable",
                        "Where the finished pages go. Leave it empty to keep them in the working directory.", true)
                + freeSpace(values) + "</section>\n";
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

    private String servicesCard(LaunchValues values) {
        return "<section class=\"card\" id=\"card-services\" data-column=\"left\">\n<h2>Services</h2>\n"
                + ControlPanelPage.Field.of("vespera.docling.image", "Docling image").value(values.doclingImage())
                        .example(ControlPanelPage.DOCLING_IMAGE_CPU).list("docling-images")
                        .hint("hint-vespera.docling.image",
                                "The image the converter runs from. The two offered are the ones this build knows.")
                        .html()
                + ControlPanelPage.Field.of("vespera.docling.base-url", "Docling address")
                        .value(values.doclingBaseUrl()).example("http://localhost:5001")
                        .hint("hint-vespera.docling.base-url", "Where the converter answers.").html()
                + ControlPanelPage.Field.of("spring.ai.ollama.base-url", "Ollama address")
                        .value(values.ollamaBaseUrl()).example("http://localhost:11434")
                        .hint("hint-spring.ai.ollama.base-url", "Where the local models answer.").html()
                + ControlPanelPage.Field.of("vespera.label.model", "Label model").value(values.labelModel())
                        .example("qwen3:8b").list("models")
                        .hint("hint-vespera.label.model",
                                "The local model that answers the questions when you ask it to.")
                        .html()
                + "</section>\n";
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
                + ControlPanelPage.text("p", "cost-seedFolder", ControlPanelPage.cost(ProfileKey.SEED_FOLDER)) + "\n"
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
        card.append(row("docling", ControlPanelPage.stateOfCheck(checks.docling().result()),
                "Docling: " + ControlPanelPage.describe(checks.docling().result())));
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
        state.hold(new LaunchValues(postedFolder(fields, "vespera.corpus-root"), postedFolder(fields, "vespera.working-dir"),
                postedFolder(fields, "vespera.deliverable-root"), posted(fields, "vespera.docling.image"),
                posted(fields, "vespera.docling.base-url"), posted(fields, "spring.ai.ollama.base-url"),
                posted(fields, "vespera.label.model"), old.lastSeedFolder()));
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
                LaunchValues held = state.values();
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
