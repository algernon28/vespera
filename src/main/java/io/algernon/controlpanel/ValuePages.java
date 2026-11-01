package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The pages that write the files of a working directory the control panel changes (ADR-302 §8): {@code /labels} for
 * the answers in {@code relevance-labels.yaml} (ADR-295 §6), {@code /code-sets} for the names in
 * {@code code-set-names.yaml} (ADR-295 §7), and {@code /profile} for every {@link ProfileKey} the next act's form
 * does not hold (ADR-295 §1 to §5). Every save is one of {@link WorkingDirectoryWrite}'s writes, answers a {@code 303}
 * to its page with the outcome, and is refused while the control panel's child runs; a {@code GET} writes nothing
 * (ADR-302 §1).
 */
final class ValuePages {

    private final ControlPanelState state;
    private final NextPages next;

    ValuePages(ControlPanelState state, NextPages next) {
        this.state = state;
        this.next = next;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/labels", this::labels);
        pages.put("/code-sets", this::codeSets);
        pages.put("/profile", this::profile);
    }

    private Optional<String> read(String fileName) throws IOException {
        Optional<Path> folder = state.workingDirectory();
        if (folder.isEmpty()) {
            return Optional.empty();
        }
        Path file = folder.get().resolve(fileName);
        return Files.isRegularFile(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
    }

    private static final String NO_WORKING_DIRECTORY = "the working directory is not named, on the launch page";

    /** The hint of the answer to a question, shared by every question's select (ADR-331 §8). */
    private static final String HINT_OF_AN_ANSWER =
            "Whether this document belongs in what you are keeping. No answer leaves it for later.";

    /** The hint of a code set's name, shared by every row's (ADR-331 §8). */
    private static final String HINT_OF_A_NAME = "The name the deliverable gives the set. Clear it to remove the name.";

    /** What the profile in the working directory holds; empty where none is named, none is there or it cannot be read. */
    static Map<ProfileKey, ProfileEdit.Stated> profileOf(ControlPanelState state) {
        try {
            Optional<Path> folder = state.workingDirectory();
            if (folder.isPresent() && Files.isRegularFile(folder.get().resolve(ProfileEdit.FILE_NAME))) {
                return ProfileEdit.read(Files.readString(folder.get().resolve(ProfileEdit.FILE_NAME),
                        StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException unreadable) {
            // a profile the control panel cannot read offers no value; the profile page says why
        }
        return Map.of();
    }

    // ---- /labels

    private void labels(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            saveLabels(exchange);
            return;
        }
        StringBuilder body = new StringBuilder();
        String pending = state.takeOutcome("/labels");
        String problem = null;
        List<LabelAnswers.Question> questions = List.of();
        try {
            Optional<String> text = read(LabelAnswers.FILE_NAME);
            if (state.workingDirectory().isEmpty()) {
                problem = NO_WORKING_DIRECTORY;
            } else if (text.isEmpty()) {
                try {
                    LabelAnswers.rewrite(Optional.empty(), Map.of());
                } catch (IllegalArgumentException line) {
                    problem = line.getMessage();
                }
            } else {
                questions = LabelAnswers.read(text.get());
            }
        } catch (IllegalArgumentException | IOException unreadable) {
            problem = unreadable.getMessage();
        }
        body.append(ControlPanelPage.outcome(pending != null ? pending : problem));
        if (problem == null) {
            body.append("<form name=\"labels\" method=\"post\" action=\"/labels\">\n<section class=\"card\">\n")
                    .append("<p class=\"hint\" id=\"hint-relevant\">").append(HINT_OF_AN_ANSWER).append("</p>\n<ol>\n");
            int i = 1;
            for (LabelAnswers.Question question : questions) {
                body.append("<li><input type=\"hidden\" name=\"path-").append(i).append("\" value=\"")
                        .append(ControlPanelPage.escape(question.path())).append("\"> <code>")
                        .append(ControlPanelPage.escape(question.path())).append("</code> ")
                        .append("<select name=\"relevant-").append(i)
                        .append("\" aria-label=\"relevant\" aria-describedby=\"hint-relevant\">")
                        .append(option("", "no answer", question.relevant().isEmpty()))
                        .append(option("true", "relevant", question.relevant().equals(Optional.of(true))))
                        .append(option("false", "not relevant", question.relevant().equals(Optional.of(false))))
                        .append("</select></li>\n");
                i++;
            }
            body.append("</ol>\n</section>\n").append(ControlPanelPage.button("save", "Save", false)).append('\n')
                    .append(ControlPanelPage.button("save-then-label", "Save, then Label", false)).append("\n</form>\n");
        }
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Questions", 0, body.toString()));
    }

    private static String option(String value, String caption, boolean selected) {
        return "<option value=\"" + value + "\"" + (selected ? " selected" : "") + ">" + caption + "</option>";
    }

    private void saveLabels(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = state.workingDirectory();
        if (folder.isEmpty()) {
            state.say("/labels", NO_WORKING_DIRECTORY);
            Exchanges.redirect(exchange, "/labels");
            return;
        }
        Map<String, Optional<Boolean>> given = new LinkedHashMap<>();
        for (int i = 1; fields.containsKey("path-" + i); i++) {
            Optional<Boolean> answer = switch (fields.getOrDefault("relevant-" + i, "")) {
                case "" -> Optional.empty();
                case "true" -> Optional.of(true);
                case "false" -> Optional.of(false);
                default -> null;
            };
            if (answer == null) {
                state.say("/labels", "an answer is one of no answer, true and false");
                Exchanges.redirect(exchange, "/labels");
                return;
            }
            given.put(fields.get("path-" + i), answer);
        }
        Optional<String> refusal = state.write(folder.get(), LabelAnswers.FILE_NAME, current -> {
            Map<String, Optional<Boolean>> changed = new LinkedHashMap<>();
            Map<String, Optional<Boolean>> recorded = new LinkedHashMap<>();
            current.ifPresent(text -> LabelAnswers.read(text).forEach(q -> recorded.put(q.path(), q.relevant())));
            given.forEach((path, answer) -> {
                if (!answer.equals(recorded.get(path))) {
                    changed.put(path, answer);
                }
            });
            return LabelAnswers.rewrite(current, changed);
        });
        if (refusal.isPresent()) {
            state.say("/labels", refusal.get());
            Exchanges.redirect(exchange, "/labels");
        } else if (fields.containsKey("save-then-label")) {
            Exchanges.redirect(exchange, InvocationPages.startAndSay(state, ControlPanelState.Start.LABEL, "/labels"));
        } else {
            state.say("/labels", "the answers are saved in " + folder.get().resolve(LabelAnswers.FILE_NAME));
            Exchanges.redirect(exchange, "/labels");
        }
    }

    // ---- /code-sets

    private void codeSets(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            saveCodeSets(exchange);
            return;
        }
        String pending = state.takeOutcome("/code-sets");
        String problem = null;
        List<CodeSetRenames.Rename> renames = List.of();
        if (state.workingDirectory().isEmpty()) {
            problem = NO_WORKING_DIRECTORY;
        } else {
            try {
                renames = CodeSetRenames.read(read(CodeSetRenames.FILE_NAME));
            } catch (IllegalArgumentException | IOException unreadable) {
                problem = unreadable.getMessage();
            }
        }
        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(pending != null ? pending : problem));
        if (problem == null) {
            Map<String, String> held = state.heldForm("code-sets");
            body.append("<form name=\"code-sets\" method=\"post\" action=\"/code-sets\">\n<section class=\"card\">\n")
                    .append("<p class=\"hint\" id=\"hint-name\">").append(HINT_OF_A_NAME).append("</p>\n");
            int i = 1;
            for (CodeSetRenames.Rename rename : renames) {
                body.append("<input type=\"hidden\" name=\"folder-").append(i).append("\" value=\"")
                        .append(ControlPanelPage.escape(rename.folder())).append("\"><p><code>")
                        .append(ControlPanelPage.escape(rename.folder().isEmpty() ? "(the root)" : rename.folder()))
                        .append("</code></p>\n")
                        .append(ControlPanelPage.Field.of("name-" + i, "Name")
                                .value(held.getOrDefault("name-" + i, rename.name())).example("Billing engine")
                                .hint("hint-name", "").html());
                i++;
            }
            String newFolder = held.getOrDefault("new-folder", "");
            body.append("<h2>Add one</h2>\n")
                    .append(ControlPanelPage.Field.of("new-folder", "Folder, from the corpus root").value(newFolder)
                            .example("src/billing")
                            .hint("hint-new-folder", "The folder of the set of code, from the corpus root.")
                            .browse(FolderPages.Target.NEW_FOLDER.forName())
                            .after(FolderPages.statusElement(FolderPages.Target.NEW_FOLDER.forName(),
                                    FolderPages.statusOfCodeSetFolder(state.values().corpusRoot(), newFolder)))
                            .html())
                    .append(ControlPanelPage.Field.of("new-name", "Name").value(held.getOrDefault("new-name", ""))
                            .example("Billing engine").hint("hint-new-name", HINT_OF_A_NAME).html())
                    .append("</section>\n").append(ControlPanelPage.button("save", "Save", false)).append("\n</form>\n");
        }
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Code sets", 0, body.toString()));
    }

    private void saveCodeSets(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = state.workingDirectory();
        if (folder.isEmpty()) {
            state.say("/code-sets", NO_WORKING_DIRECTORY);
            Exchanges.redirect(exchange, "/code-sets");
            return;
        }
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get() == FolderPages.Target.NEW_FOLDER) {
            state.holdForm("code-sets", FolderPages.withoutBrowse(fields, browse.get().field()));
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(),
                    fields.getOrDefault("new-folder", "").stripTrailing()));
            return;
        }
        if (FolderPages.climbs(fields.getOrDefault("new-folder", "").strip())) {
            state.say("/code-sets", "the folder is not saved: a code set's folder lies under the corpus root, and the"
                    + " one named does not");
            Exchanges.redirect(exchange, "/code-sets");
            return;
        }
        List<CodeSetRenames.Rename> left = new ArrayList<>();
        for (int i = 1; fields.containsKey("folder-" + i); i++) {
            String name = fields.getOrDefault("name-" + i, "");
            if (!name.isBlank()) {
                left.add(new CodeSetRenames.Rename(fields.get("folder-" + i), name.strip()));
            }
        }
        String newFolder = fields.getOrDefault("new-folder", "").stripTrailing();
        String newName = fields.getOrDefault("new-name", "").strip();
        if (!newFolder.isEmpty() || !newName.isEmpty()) {
            left.add(new CodeSetRenames.Rename(newFolder, newName));
        }
        Optional<String> refusal = state.write(folder.get(), CodeSetRenames.FILE_NAME, current -> {
            CodeSetRenames.read(current);
            return CodeSetRenames.render(left);
        });
        if (refusal.isEmpty()) {
            state.letGoOfForm("code-sets");
        }
        state.say("/code-sets", refusal.orElse("the names are saved in " + folder.get().resolve(CodeSetRenames.FILE_NAME)));
        Exchanges.redirect(exchange, "/code-sets");
    }

    // ---- /profile

    private void profile(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            saveProfile(exchange);
            return;
        }
        String pending = state.takeOutcome("/profile");
        String problem = null;
        Map<ProfileKey, ProfileEdit.Stated> stated = Map.of();
        if (state.workingDirectory().isEmpty()) {
            problem = NO_WORKING_DIRECTORY;
        } else {
            try {
                stated = read(ProfileEdit.FILE_NAME).map(ProfileEdit::read).orElse(Map.of());
            } catch (IllegalArgumentException | IOException unreadable) {
                problem = unreadable.getMessage();
            }
        }
        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(pending != null ? pending : problem));
        List<ProfileKey> asked = next.wanted();
        List<ProfileKey> shown = new ArrayList<>();
        StringBuilder list = new StringBuilder("<ul id=\"profile-list\">\n");
        StringBuilder sections = new StringBuilder("<div class=\"sections\">\n");
        for (ProfileKey key : ProfileKey.values()) {
            if (asked.contains(key)) {
                continue;
            }
            shown.add(key);
            ProfileEdit.Stated held = stated.get(key);
            String current = held == null || held.value() == null ? "not set"
                    : held.value() + (held.provenance() == null ? "" : " (" + held.provenance() + ")");
            list.append("<li><a href=\"#key-").append(key.key()).append("\">").append(key.key())
                    .append("<span class=\"current\">")
                    .append(ControlPanelPage.escape(held == null || held.value() == null ? "not set" : held.value()))
                    .append("</span></a></li>\n");
            Map<String, String> typed = state.heldForm(key.key());
            boolean seedFolder = key == ProfileKey.SEED_FOLDER;
            String forName = FolderPages.Target.PROFILE_SEED_FOLDER.forName();
            String filled = key == ProfileKey.DELIVERABLE_LANGUAGE && held != null && held.value() != null
                    ? held.value() : "";
            String value = typed.getOrDefault("value", filled);
            sections.append("<section class=\"card\" id=\"key-").append(key.key()).append("\">\n")
                    .append("<form name=\"").append(key.key()).append("\" method=\"post\" action=\"/profile\">\n")
                    .append("<h2>").append(key.key()).append("</h2>\n")
                    .append("<input type=\"hidden\" name=\"key\" value=\"").append(key.key()).append("\">\n")
                    .append(ControlPanelPage.text("p", "current-" + key.key(), current)).append('\n')
                    .append(ControlPanelPage.keyControl(key, "value", "f-" + key.key() + "-value", "Value", value,
                            seedFolder ? forName : "",
                            seedFolder ? FolderPages.statusElement(forName, FolderPages.statusOf(value, false)) : ""))
                    .append(ControlPanelPage.provenance("provenance", "f-" + key.key() + "-provenance",
                            "Why this value?", typed.getOrDefault("provenance", ""), "hint-" + key.key() + "-provenance"))
                    .append(ControlPanelPage.text("p", "cost-" + key.key(), ControlPanelPage.cost(key))).append('\n')
                    .append(NextPages.reportsOf(key.key()))
                    .append(ControlPanelPage.button("save", "Save", false)).append("\n</form>\n</section>\n");
        }
        body.append("<div class=\"profile\">\n<div class=\"card\">\n").append(list).append("</ul>\n</div>\n")
                .append(sections).append("</div>\n</div>\n")
                .append(ControlPanelPage.listsFor(shown, state.lastChecks().ollama().models()));
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Profile", 0, body.toString()));
    }

    private void saveProfile(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = state.workingDirectory();
        Optional<ProfileKey> key = ProfileKey.named(fields.getOrDefault("key", ""));
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get() == FolderPages.Target.PROFILE_SEED_FOLDER && key.isPresent()) {
            state.holdForm(key.get().key(), FolderPages.withoutBrowse(fields, browse.get().field()));
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(),
                    fields.getOrDefault(browse.get().field(), "").stripTrailing()));
            return;
        }
        if (folder.isEmpty()) {
            state.say("/profile", NO_WORKING_DIRECTORY);
        } else if (key.isEmpty()) {
            state.say("/profile", "the profile has no value named " + fields.getOrDefault("key", ""));
        } else {
            String value = fields.getOrDefault("value", "");
            String provenance = fields.getOrDefault("provenance", "");
            Optional<String> refusal = state.write(folder.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.rewrite(current, key.get(), value, provenance));
            if (refusal.isEmpty()) {
                state.letGoOfForm(key.get().key());
            }
            state.say("/profile", refusal.orElse(key.get().key() + " is saved in "
                    + folder.get().resolve(ProfileEdit.FILE_NAME)));
        }
        Exchanges.redirect(exchange, key.map(each -> "/profile#key-" + each.key()).orElse("/profile"));
    }
}
