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
 *
 * <p>{@code /profile} edits every value as ADR-330 has it: each key's form is filled from what the file states and
 * carries the value in use and its source, a line saying what the key is and the measurement it points at; a save
 * of a value left alone writes nothing; and a file not in the form {@link ProfileEdit} reads is shown with its
 * first line out of form and repaired, where the repair loses nothing, at {@code /profile-repair} (§2 to §6).
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
        pages.put("/profile-repair", this::profileRepair);
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
        String text = null;
        Optional<Path> folder = state.workingDirectory();
        if (folder.isEmpty()) {
            problem = NO_WORKING_DIRECTORY;
        } else {
            try {
                text = read(ProfileEdit.FILE_NAME).orElse(null);
            } catch (IOException unreadable) {
                problem = unreadable.getMessage();
            }
        }
        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(pending != null ? pending : problem));
        folder.ifPresent(where -> body.append(profileFile(where)));
        Optional<ProfileRepair.Analysis> notInForm = text == null ? Optional.empty() : ProfileRepair.analyse(text);
        if (notInForm.isPresent()) {
            body.append(formProblem(text, notInForm.get()));
            Exchanges.page(exchange, ControlPanelPage.render(exchange, "Profile", 0, body.toString()));
            return;
        }
        Map<ProfileKey, ProfileEdit.Stated> stated = text == null ? Map.of() : ProfileEdit.read(text);
        Map<ProfileKey, ProfileEdit.Measurement> measured = text == null ? Map.of() : ProfileEdit.measurementsOf(text);
        Optional<EffectiveValues> effective = state.effectiveValues();
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
            String fileValue = held == null || held.value() == null ? "" : held.value();
            String fileProvenance = held == null || held.provenance() == null ? "" : held.provenance();
            String current = held == null || held.value() == null ? "not set"
                    : held.value() + (held.provenance() == null ? "" : " (" + held.provenance() + ")");
            list.append("<li><a href=\"#key-").append(key.key()).append("\">").append(key.key())
                    .append("<span class=\"current\">")
                    .append(ControlPanelPage.escape(held == null || held.value() == null ? "not set" : held.value()))
                    .append("</span></a></li>\n");
            Map<String, String> typed = state.heldForm(key.key());
            boolean seedFolder = key == ProfileKey.SEED_FOLDER;
            String forName = FolderPages.Target.PROFILE_SEED_FOLDER.forName();
            String value = typed.getOrDefault("value", fileValue);
            sections.append("<section class=\"card\" id=\"key-").append(key.key()).append("\">\n")
                    .append("<form name=\"").append(key.key()).append("\" method=\"post\" action=\"/profile\">\n")
                    .append("<h2>").append(key.key()).append("</h2>\n")
                    .append("<input type=\"hidden\" name=\"key\" value=\"").append(key.key()).append("\">\n")
                    .append("<input type=\"hidden\" name=\"drawn\" value=\"")
                    .append(ProfileEdit.drawnOf(key, fileValue, fileProvenance)).append("\">\n")
                    .append(ControlPanelPage.text("p", "about-" + key.key(), key.about())).append('\n')
                    .append(inUse(key, held, effective)).append('\n')
                    .append(ControlPanelPage.text("p", "current-" + key.key(), current)).append('\n')
                    .append(measurement(key, measured.get(key)))
                    .append(ControlPanelPage.keyControl(key, "value", "f-" + key.key() + "-value", "Value", value,
                            seedFolder ? forName : "",
                            seedFolder ? FolderPages.statusElement(forName, FolderPages.statusOf(value, false)) : ""))
                    .append(ControlPanelPage.provenance("provenance", "f-" + key.key() + "-provenance",
                            "Why this value?", typed.getOrDefault("provenance", fileProvenance),
                            "hint-" + key.key() + "-provenance"))
                    .append(ControlPanelPage.costElement(key)).append('\n')
                    .append(NextPages.reportsOf(key.key()))
                    .append(ControlPanelPage.button("save", "Save", false)).append("\n</form>\n</section>\n");
        }
        body.append("<div class=\"profile\">\n<div class=\"card\">\n").append(list).append("</ul>\n</div>\n")
                .append(sections).append("</div>\n</div>\n")
                .append(ControlPanelPage.listsFor(shown, state.lastChecks().ollama().models()));
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Profile", 0, body.toString()));
    }

    /** {@code profile-file}: whether the file and its folder are there, and where the first save will make the file (ADR-330 §7). */
    private static String profileFile(Path workingDirectory) {
        boolean fileThere = Files.isRegularFile(workingDirectory.resolve(ProfileEdit.FILE_NAME));
        boolean folderThere = Files.isDirectory(workingDirectory);
        String path = workingDirectory.resolve(ProfileEdit.FILE_NAME).toAbsolutePath().toString();
        String words = fileThere ? "profile.yaml is edited where it stands, at " + path + "."
                : folderThere ? "There is no profile.yaml yet: the first value saved makes it at " + path + "."
                : "There is no profile.yaml yet, and no working directory: the first value saved makes both, the file at "
                        + path + ".";
        return "<p id=\"profile-file\" data-file=\"" + (fileThere ? "present" : "absent") + "\" data-folder=\""
                + (folderThere ? "present" : "absent") + "\" data-path=\"" + ControlPanelPage.escape(path) + "\">"
                + ControlPanelPage.escape(words) + "</p>\n";
    }

    /**
     * {@code in-use-<key>} (ADR-330 §2): the value in use and where it came from. The profile's value where it names
     * one, whatever its provenance, and {@code unreadable} where the jar cannot read it; else what the jar's file says
     * lies beneath, marked fresh or stale; else {@code not-known} for a key with a value beneath it when the jar has not
     * said, and {@code unset}.
     */
    private static String inUse(ProfileKey key, ProfileEdit.Stated held, Optional<EffectiveValues> effective) {
        String stated = held == null || held.value() == null ? "" : held.value();
        String value;
        String source;
        String fresh = "";
        String words;
        Optional<EffectiveValues.Value> beneath = effective.flatMap(said -> said.named(key.key()));
        if (!stated.isBlank()) {
            boolean readable = ProfileEdit.isReadable(key, stated);
            value = stated;
            source = readable ? "profile" : "unreadable";
            words = readable ? "In use: " + stated + ", from profile.yaml."
                    : "profile.yaml states " + stated + ", which the jar cannot read; fix it here.";
        } else if (beneath.isPresent()) {
            value = beneath.get().value();
            source = beneath.get().source();
            fresh = effective.get().stale() ? "stale" : "fresh";
            words = "In use: " + value + ", from " + source + "."
                    + (effective.get().stale() ? " The jar that said so may have been replaced since." : "");
        } else {
            value = "";
            source = effective.isEmpty() && key.hasAValueBeneath() ? "not-known" : "unset";
            words = source.equals("not-known")
                    ? "The jar has not said yet what it would use; run an invocation to see it."
                    : "Nothing sets this value.";
        }
        return "<p id=\"in-use-" + ControlPanelPage.escape(key.key()) + "\" data-value=\"" + ControlPanelPage.escape(value)
                + "\" data-source=\"" + ControlPanelPage.escape(source) + "\"" + (fresh.isEmpty() ? "" : " data-fresh=\"" + fresh + "\"") + ">"
                + ControlPanelPage.escape(words) + "</p>";
    }

    /** {@code measurement-<key>}: where the file's measurement of the key lies and when it was last looked at; none where it has none. */
    private static String measurement(ProfileKey key, ProfileEdit.Measurement measured) {
        if (measured == null) {
            return "";
        }
        return "<p id=\"measurement-" + ControlPanelPage.escape(key.key()) + "\" data-source=\""
                + ControlPanelPage.escape(measured.source()) + "\" data-refreshed-at=\""
                + ControlPanelPage.escape(measured.refreshedAt()) + "\">Measured from "
                + ControlPanelPage.escape(measured.source()) + ", last looked at "
                + ControlPanelPage.escape(measured.refreshedAt()) + ".</p>\n";
    }

    /**
     * What the page holds in place of the forms where the file is not in the form the jar writes it (ADR-330 §6): the
     * first line that is not, the file's text, and, where what the repair changes is all that is wrong, what
     * each does and the form that makes them.
     */
    private static String formProblem(String text, ProfileRepair.Analysis analysis) {
        StringBuilder html = new StringBuilder("<p id=\"form-problem\" data-line=\"").append(analysis.firstLine())
                .append("\" data-repairable=\"").append(analysis.repairable()).append("\">")
                .append(ControlPanelPage.escape("profile.yaml is not in the form the control panel edits, from line "
                        + analysis.firstLine() + ". "
                        + (analysis.repairable() ? "The changes below put it in form and lose no value or provenance."
                                : "Fix it by hand: no change the control panel may make puts it in form.")
                        + " The jar reads the file in whatever form, and writes it back in its own at the next census."))
                .append("</p>\n<pre id=\"profile-text\">\n").append(ControlPanelPage.escape(text)).append("</pre>\n");
        if (analysis.repairable()) {
            html.append("<h2>Changes the repair makes</h2>\n<ul id=\"repair-changes\">\n");
            for (ProfileRepair.Change change : analysis.changes()) {
                html.append("<li data-line=\"").append(change.line()).append("\">Line ").append(change.line()).append(": ")
                        .append(ControlPanelPage.escape(change.words())).append("</li>\n");
            }
            html.append("</ul>\n<form name=\"repair\" method=\"post\" action=\"/profile-repair\">\n")
                    .append("<input type=\"hidden\" name=\"drawn\" value=\"").append(ProfileEdit.digestOf(text))
                    .append("\">\n").append(ControlPanelPage.button("repair", "Repair", false))
                    .append("\n</form>\n");
        }
        return html.toString();
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
            state.say("/profile", saveOne(folder.get(), key.get(), fields));
        }
        Exchanges.redirect(exchange, key.map(each -> "/profile#key-" + each.key()).orElse("/profile"));
    }

    /**
     * A save of one value, in ADR-330 §5's order: compared with what the page was drawn from, so that a value left alone
     * writes nothing and takes no lock; checked against the file and by §4; written. A refusal keeps what was typed
     * until the value is written or typed back to what the file states.
     *
     * @return the line the page shows
     */
    private String saveOne(Path folder, ProfileKey key, Map<String, String> fields) {
        String value = fields.getOrDefault("value", "");
        String provenance = fields.getOrDefault("provenance", "");
        Optional<String> drawn = Optional.ofNullable(fields.get("drawn"));
        if (drawn.isPresent() && ProfileEdit.drawnOf(key, value, provenance).equals(drawn.get())) {
            state.letGoOfForm(key.key());
            return key.key() + " is as profile.yaml states it: nothing was written";
        }
        Optional<String> refusal = ProfileEdit.refusalOfSave(key, value, provenance, statedIn(folder, key), drawn);
        if (refusal.isEmpty()) {
            refusal = state.write(folder, ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.rewrite(current, key, value, provenance));
        }
        if (refusal.isPresent()) {
            state.holdForm(key.key(), fields);
            return refusal.get();
        }
        state.letGoOfForm(key.key());
        return key.key() + " is saved in " + folder.resolve(ProfileEdit.FILE_NAME);
    }

    /** What the file states for {@code key} now; empty where it states none or cannot be read in the form. */
    private static Optional<ProfileEdit.Stated> statedIn(Path folder, ProfileKey key) {
        try {
            Path file = folder.resolve(ProfileEdit.FILE_NAME);
            return Files.isRegularFile(file)
                    ? Optional.ofNullable(ProfileEdit.read(Files.readString(file, StandardCharsets.UTF_8)).get(key))
                    : Optional.empty();
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    // ---- /profile-repair

    /** The repair's address: a {@code POST} repairs the file; a {@code GET} changes nothing and lands on {@code /profile}. */
    private void profileRepair(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/profile");
            return;
        }
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = state.workingDirectory();
        if (folder.isEmpty()) {
            state.say("/profile", NO_WORKING_DIRECTORY);
        } else {
            String drawn = fields.getOrDefault("drawn", "");
            String[] copyMade = {""};
            Optional<String> refusal = state.write(folder.get(), ProfileEdit.FILE_NAME,
                    current -> repaired(folder.get(), current, drawn, copyMade));
            state.say("/profile", refusal.orElse("profile.yaml is repaired; the file as it stood is kept as " + copyMade[0]));
        }
        Exchanges.redirect(exchange, "/profile");
    }

    /**
     * The repaired text, worked out again from the file under the lock (ADR-330 §6): refused where the file is not the
     * one the page showed or no repair puts it right, and only once a copy of it whole lies beside it, never over an
     * earlier one.
     */
    private static String repaired(Path folder, Optional<String> current, String drawn, String[] copyMade) {
        String text = current.orElseThrow(() -> new IllegalArgumentException(
                ProfileEdit.FILE_NAME + " is not there, so there is nothing to repair"));
        if (!ProfileEdit.digestOf(text).equals(drawn)) {
            throw new IllegalArgumentException(
                    ProfileEdit.FILE_NAME + " has changed since this page was drawn, so it was not repaired");
        }
        ProfileRepair.Analysis analysis = ProfileRepair.analyse(text).filter(ProfileRepair.Analysis::repairable)
                .orElseThrow(() -> new IllegalArgumentException(
                        ProfileEdit.FILE_NAME + " is in no form that the repair can put right, so it was not repaired"));
        copyMade[0] = WorkingDirectoryWrite.keepCopyBeforeRepair(folder, ProfileEdit.FILE_NAME);
        return analysis.repaired();
    }
}
