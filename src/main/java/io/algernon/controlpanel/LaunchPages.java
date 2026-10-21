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
 * (ADR-302 §2, §4, §5, §6). A {@code GET} of any of them reads the working directory, the lock files and the sidecars
 * and changes nothing (ADR-302 §1); {@code /launch} and {@code /seed-folder} answer a {@code GET} with a {@code 303}
 * to {@code /}.
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
        List<String> warnings = new ArrayList<>(state.notes());
        warnings.addAll(values.warningsToRun());
        warnings.addAll(checks.docling().warnings());
        warnings.addAll(checks.ollama().warnings());

        StringBuilder body = new StringBuilder();
        body.append(ControlPanelPage.outcome(state.takeOutcome("/")));
        body.append("<form name=\"launch\" method=\"post\" action=\"/launch\">\n");
        body.append(ControlPanelPage.field("vespera.corpus-root", "Corpus root", values.corpusRoot(), false));
        body.append(ControlPanelPage.field("vespera.working-dir", "Working directory", values.workingDirectory(), false));
        body.append(ControlPanelPage.field("vespera.deliverable-root", "Deliverable root", values.deliverableRoot(), false));
        body.append(ControlPanelPage.field("vespera.docling.image", "Docling image", values.doclingImage(), false));
        body.append(ControlPanelPage.field("vespera.docling.base-url", "Docling address", values.doclingBaseUrl(), false));
        body.append(ControlPanelPage.field("spring.ai.ollama.base-url", "Ollama address", values.ollamaBaseUrl(), false));
        body.append(ControlPanelPage.field("vespera.label.model", "Label model", values.labelModel(), false));
        body.append(ControlPanelPage.button("run", "Run", disabled)).append('\n');
        body.append(ControlPanelPage.button("label", "Label", disabled)).append('\n');
        body.append(ControlPanelPage.button("check", "Check", false)).append("\n</form>\n");

        body.append("<h2>Locks</h2>\n");
        body.append(ControlPanelPage.text("p", "holder-working-directory", holders.workingDirectory().orElse(""))).append('\n');
        body.append(ControlPanelPage.text("p", "holder-corpus-root", holders.corpusRoot().orElse(""))).append('\n');
        if (disabled && !values.workingDirectory().isEmpty()) {
            body.append("<p>").append(ControlPanelPage.reportLink("progress.html")).append("</p>\n");
        }

        body.append("<h2>Warnings</h2>\n<ul id=\"warnings\" class=\"warnings\">");
        for (String warning : warnings) {
            body.append("<li>").append(ControlPanelPage.escape(warning)).append("</li>");
        }
        body.append("</ul>\n");

        body.append("<h2>Sidecars</h2>\n<ul>\n<li>Docling: <span id=\"docling-check\" data-check=\"")
                .append(checks.docling().result().attribute()).append("\">")
                .append(describe(checks.docling().result())).append("</span></li>\n")
                .append("<li>Ollama: <span id=\"ollama-check\" data-check=\"")
                .append(checks.ollama().result().attribute()).append("\">")
                .append(describe(checks.ollama().result())).append("</span></li>\n</ul>\n");

        body.append(seedFolderForm(values));
        body.append(freeSpace(values));
        body.append("<h2>Reports</h2>\n").append(reports(state));
        Exchanges.page(exchange, ControlPanelPage.render("Control panel", 0, body.toString()));
    }

    private String seedFolderForm(LaunchValues values) {
        return "<h2>Seed folder</h2>\n<p>A value of the profile in the working directory <code id=\"seed-folder-working-directory\">"
                + ControlPanelPage.escape(values.workingDirectory()) + "</code>.</p>\n"
                + "<form name=\"seed-folder\" method=\"post\" action=\"/seed-folder\">\n"
                + ControlPanelPage.field("seedFolder", "Seed folder", values.lastSeedFolder(), false)
                + ControlPanelPage.field("provenance", "Provenance", "", true)
                + ControlPanelPage.text("p", "cost-seedFolder", ControlPanelPage.cost(ProfileKey.SEED_FOLDER)) + "\n"
                + ControlPanelPage.button("save", "Save", false) + "\n</form>\n";
    }

    private static String describe(SidecarChecks.Result result) {
        return switch (result) {
            case NOT_CHECKED -> "not checked";
            case MATCHES -> "runs the image named";
            case ANOTHER_IMAGE -> "runs another image";
            case NOT_ANSWERING -> "not answering";
            case SERVES_ALL -> "serves every model named";
            case MISSING -> "does not serve a model named";
        };
    }

    private String freeSpace(LaunchValues values) {
        StringBuilder items = new StringBuilder();
        usable(values.workingDirectory()).ifPresent(bytes -> items.append(
                "<li data-folder=\"working-directory\" data-usable-bytes=\"" + bytes
                        + "\">Working directory: " + bytes + " bytes</li>"));
        usable(values.deliverableRoot()).ifPresent(bytes -> items.append(
                "<li data-folder=\"deliverable-root\" data-usable-bytes=\"" + bytes
                        + "\">Deliverable root: " + bytes + " bytes</li>"));
        return "<h2>Free space</h2>\n<ul id=\"free-space\">" + items + "</ul>\n"
                + ControlPanelPage.text("p", "free-space-rules", RULES_OF_FREE_SPACE) + "\n";
    }

    /** The usable bytes of the drive of {@code folder}, measured at its nearest ancestor that exists (ADR-302 §6). */
    private static Optional<Long> usable(String folder) {
        if (folder.isEmpty()) {
            return Optional.empty();
        }
        try {
            Path measured = Path.of(folder).toAbsolutePath().normalize();
            while (measured != null && Files.notExists(measured)) {
                measured = measured.getParent();
            }
            return measured == null ? Optional.empty() : Optional.of(Files.getFileStore(measured).getUsableSpace());
        } catch (IOException | RuntimeException unmeasured) {
            return Optional.empty();
        }
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
        return "<ul id=\"reports\">" + items + "</ul>\n";
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
        state.hold(new LaunchValues(posted(fields, "vespera.corpus-root"), posted(fields, "vespera.working-dir"),
                posted(fields, "vespera.deliverable-root"), posted(fields, "vespera.docling.image"),
                posted(fields, "vespera.docling.base-url"), posted(fields, "spring.ai.ollama.base-url"),
                posted(fields, "vespera.label.model"), old.lastSeedFolder()));
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
        String folder = posted(fields, "seedFolder");
        Optional<Path> workingDirectory = state.workingDirectory();
        if (workingDirectory.isEmpty()) {
            state.say("/", "seedFolder is not saved: the working directory is not named");
        } else {
            Optional<String> refusal = state.write(workingDirectory.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.rewrite(current, ProfileKey.SEED_FOLDER, folder, fields.getOrDefault("provenance", "")));
            if (refusal.isEmpty()) {
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
}
