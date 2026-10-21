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

    private final PanelSession session;
    private final NextPages next;

    ValuePages(PanelSession session, NextPages next) {
        this.session = session;
        this.next = next;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/labels", this::labels);
        pages.put("/code-sets", this::codeSets);
        pages.put("/profile", this::profile);
    }

    private Optional<String> read(String fileName) throws IOException {
        Optional<Path> folder = session.workingDirectory();
        if (folder.isEmpty()) {
            return Optional.empty();
        }
        Path file = folder.get().resolve(fileName);
        return Files.isRegularFile(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
    }

    private static final String NO_WORKING_DIRECTORY = "the working directory is not named, on the launch page";

    // ---- /labels

    private void labels(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            saveLabels(exchange);
            return;
        }
        StringBuilder body = new StringBuilder();
        String pending = session.takeOutcome("/labels");
        String problem = null;
        List<LabelAnswers.Question> questions = List.of();
        try {
            Optional<String> text = read(LabelAnswers.FILE_NAME);
            if (session.workingDirectory().isEmpty()) {
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
            body.append("<form name=\"labels\" method=\"post\" action=\"/labels\">\n<ol>\n");
            int i = 1;
            for (LabelAnswers.Question question : questions) {
                body.append("<li><input type=\"hidden\" name=\"path-").append(i).append("\" value=\"")
                        .append(ControlPanelPage.escape(question.path())).append("\"> <code>")
                        .append(ControlPanelPage.escape(question.path())).append("</code> ")
                        .append("<select name=\"relevant-").append(i).append("\" aria-label=\"relevant\">")
                        .append(option("", "no answer", question.relevant().isEmpty()))
                        .append(option("true", "relevant", question.relevant().equals(Optional.of(true))))
                        .append(option("false", "not relevant", question.relevant().equals(Optional.of(false))))
                        .append("</select></li>\n");
                i++;
            }
            body.append("</ol>\n").append(ControlPanelPage.button("save", "Save", false)).append('\n')
                    .append(ControlPanelPage.button("save-then-label", "Save, then Label", false)).append("\n</form>\n");
        }
        Exchanges.page(exchange, ControlPanelPage.render("Questions", 0, body.toString()));
    }

    private static String option(String value, String caption, boolean selected) {
        return "<option value=\"" + value + "\"" + (selected ? " selected" : "") + ">" + caption + "</option>";
    }

    private void saveLabels(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = session.workingDirectory();
        if (folder.isEmpty()) {
            session.say("/labels", NO_WORKING_DIRECTORY);
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
                session.say("/labels", "an answer is one of no answer, true and false");
                Exchanges.redirect(exchange, "/labels");
                return;
            }
            given.put(fields.get("path-" + i), answer);
        }
        Optional<String> refusal = session.writer().write(folder.get(), LabelAnswers.FILE_NAME, current -> {
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
            session.say("/labels", refusal.get());
            Exchanges.redirect(exchange, "/labels");
        } else if (fields.containsKey("save-then-label")) {
            Exchanges.redirect(exchange, InvocationPages.startAndSay(session, PanelSession.Start.LABEL, "/labels"));
        } else {
            session.say("/labels", "the answers are saved in " + folder.get().resolve(LabelAnswers.FILE_NAME));
            Exchanges.redirect(exchange, "/labels");
        }
    }

    // ---- /code-sets

    private void codeSets(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            saveCodeSets(exchange);
            return;
        }
        String pending = session.takeOutcome("/code-sets");
        String problem = null;
        List<CodeSetRenames.Rename> renames = List.of();
        if (session.workingDirectory().isEmpty()) {
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
            body.append("<form name=\"code-sets\" method=\"post\" action=\"/code-sets\">\n");
            int i = 1;
            for (CodeSetRenames.Rename rename : renames) {
                body.append("<p><input type=\"hidden\" name=\"folder-").append(i).append("\" value=\"")
                        .append(ControlPanelPage.escape(rename.folder())).append("\"><code>")
                        .append(ControlPanelPage.escape(rename.folder().isEmpty() ? "(the root)" : rename.folder()))
                        .append("</code>\n")
                        .append(ControlPanelPage.field("name-" + i, "Name", rename.name(), false)).append("</p>\n");
                i++;
            }
            body.append("<h2>Add one</h2>\n")
                    .append(ControlPanelPage.field("new-folder", "Folder, from the corpus root", "", false))
                    .append(ControlPanelPage.field("new-name", "Name", "", false))
                    .append(ControlPanelPage.button("save", "Save", false)).append("\n</form>\n");
        }
        Exchanges.page(exchange, ControlPanelPage.render("Code sets", 0, body.toString()));
    }

    private void saveCodeSets(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = session.workingDirectory();
        if (folder.isEmpty()) {
            session.say("/code-sets", NO_WORKING_DIRECTORY);
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
        String newFolder = fields.getOrDefault("new-folder", "").strip();
        String newName = fields.getOrDefault("new-name", "").strip();
        if (!newFolder.isEmpty() || !newName.isEmpty()) {
            left.add(new CodeSetRenames.Rename(newFolder, newName));
        }
        Optional<String> refusal = session.writer().write(folder.get(), CodeSetRenames.FILE_NAME, current -> {
            CodeSetRenames.read(current);
            return CodeSetRenames.render(left);
        });
        session.say("/code-sets", refusal.orElse("the names are saved in " + folder.get().resolve(CodeSetRenames.FILE_NAME)));
        Exchanges.redirect(exchange, "/code-sets");
    }

    // ---- /profile

    private void profile(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            saveProfile(exchange);
            return;
        }
        String pending = session.takeOutcome("/profile");
        String problem = null;
        Map<ProfileKey, ProfileEdit.Stated> stated = Map.of();
        if (session.workingDirectory().isEmpty()) {
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
        for (ProfileKey key : ProfileKey.values()) {
            if (asked.contains(key)) {
                continue;
            }
            ProfileEdit.Stated held = stated.get(key);
            String current = held == null || held.value() == null ? "not set"
                    : held.value() + (held.provenance() == null ? "" : " (" + held.provenance() + ")");
            body.append("<form name=\"").append(key.key()).append("\" method=\"post\" action=\"/profile\">\n")
                    .append("<h2>").append(key.key()).append("</h2>\n")
                    .append("<input type=\"hidden\" name=\"key\" value=\"").append(key.key()).append("\">\n")
                    .append(ControlPanelPage.text("p", "current-" + key.key(), current)).append('\n')
                    .append(ControlPanelPage.field("value", "Value", "", false))
                    .append(ControlPanelPage.field("provenance", "Provenance", "", true))
                    .append(ControlPanelPage.text("p", "cost-" + key.key(), ControlPanelPage.cost(key))).append('\n')
                    .append(ControlPanelPage.button("save", "Save", false)).append("\n</form>\n");
        }
        Exchanges.page(exchange, ControlPanelPage.render("Profile", 0, body.toString()));
    }

    private void saveProfile(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = session.workingDirectory();
        Optional<ProfileKey> key = ProfileKey.named(fields.getOrDefault("key", ""));
        if (folder.isEmpty()) {
            session.say("/profile", NO_WORKING_DIRECTORY);
        } else if (key.isEmpty()) {
            session.say("/profile", "the profile has no value named " + fields.getOrDefault("key", ""));
        } else {
            String value = fields.getOrDefault("value", "");
            String provenance = fields.getOrDefault("provenance", "");
            Optional<String> refusal = session.writer().write(folder.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.rewrite(current, key.get(), value, provenance));
            session.say("/profile", refusal.orElse(key.get().key() + " is saved in "
                    + folder.get().resolve(ProfileEdit.FILE_NAME)));
        }
        Exchanges.redirect(exchange, "/profile");
    }
}
