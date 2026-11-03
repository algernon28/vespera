package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.File;
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
 * The pages that write the files of a working directory the control panel changes (ADR-302 §8): {@code /labels} for the
 * folders not relevant in {@code not-relevant-folders.yaml} and the answers they give in {@code relevance-labels.yaml}
 * (ADR-334 §6), {@code /code-sets} for the names in {@code code-set-names.yaml} (ADR-295 §7) and the folders outside the
 * corpus root in the profile's {@code codeSetFolders} (ADR-334 §5.2), and {@code /profile} for every {@link ProfileKey}
 * as one form (ADR-334 §7). Every save is one of {@link WorkingDirectoryWrite}'s writes, answers a {@code 303} to its
 * page with the outcome, and is refused while the control panel's child runs; a {@code GET} writes nothing
 * (ADR-302 §1).
 *
 * <p>{@code /profile} edits every value as ADR-330 has it: each key's section is filled from what the file states and
 * carries the value in use and its source, a line saying what the key is and the measurement it points at; a save
 * writes every value changed in one write and none where one is refused, and writes nothing where none is changed; Reset
 * puts the form back to the file; and a file not in the form {@link ProfileEdit} reads is shown with its first line out
 * of form and repaired, where the repair loses nothing, at {@code /profile-repair} (ADR-330 §2 to §6).
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
        pages.put("/code-set-folders", this::codeSetFolders);
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

    /** The hint of a code set's name, shared by every row's (ADR-331 §8). */
    private static final String HINT_OF_A_NAME = "The name the deliverable gives the set. Clear it to remove the name.";

    /** The hint of a folder not relevant (ADR-334 §6). */
    private static final String HINT_OF_A_FOLDER_NOT_RELEVANT =
            "A folder of what you are not keeping: every question about a file below it is answered not relevant.";

    /** The hint of a folder outside the corpus root (ADR-334 §5.2). */
    private static final String HINT_OF_A_CODE_SET_FOLDER =
            "A folder of code anywhere on this machine, read with the corpus and kept as code sets of its own.";

    /** What the page says of a provenance beside a form that changes several values (ADR-334 §7). */
    private static final String HINT_OF_A_PROVENANCE =
            "Needed for each value you change: why you chose it, in your words. It is written beside it in profile.yaml.";

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

    /** The folders the profile's {@code codeSetFolders} states, in its order; none where it states none. */
    private List<String> codeSetFoldersStated() {
        ProfileEdit.Stated stated = profileOf(state).get(ProfileKey.CODE_SET_FOLDERS);
        return CodeSetFolderChecks.statedIn(stated == null ? null : stated.value());
    }

    // ---- /labels

    private void labels(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            saveNotRelevant(exchange);
            return;
        }
        String pending = state.takeOutcome("/labels");
        String problem = null;
        List<LabelAnswers.Question> questions = List.of();
        List<String> listed = List.of();
        if (state.workingDirectory().isEmpty()) {
            problem = NO_WORKING_DIRECTORY;
        } else {
            try {
                Optional<String> text = read(LabelAnswers.FILE_NAME);
                if (text.isPresent()) {
                    questions = LabelAnswers.read(text.get());
                }
                listed = NotRelevantFolders.read(read(NotRelevantFolders.FILE_NAME));
            } catch (IllegalArgumentException | IOException unreadable) {
                problem = unreadable.getMessage();
            }
        }
        StringBuilder body = new StringBuilder(ControlPanelPage.outcome(pending != null ? pending : problem));
        if (problem == null) {
            Map<String, String> held = state.heldForm("not-relevant");
            List<String> folders = listed;
            long below = questions.stream()
                    .filter(question -> folders.stream().anyMatch(folder -> NotRelevantFolders.lies(question.path(), folder)))
                    .count();
            body.append("<p id=\"questions\" data-questions=\"").append(questions.size())
                    .append("\" data-not-relevant=\"").append(below).append("\">")
                    .append(questions.size()).append(questions.size() == 1 ? " question" : " questions").append(", ")
                    .append(below).append(" of them in a folder not relevant.</p>\n");
            body.append("<form name=\"not-relevant\" method=\"post\" action=\"/labels\">\n<section class=\"card\">\n")
                    .append("<ul id=\"not-relevant-folders\">\n");
            int i = 1;
            for (String folder : listed) {
                body.append(listItem(folder, i++));
            }
            body.append("</ul>\n")
                    .append(ControlPanelPage.Field.of("new-not-relevant-folder", "Folder, from the corpus root")
                            .value(held.getOrDefault("new-not-relevant-folder", "")).example("archive/old-flows")
                            .hint("hint-new-not-relevant-folder", HINT_OF_A_FOLDER_NOT_RELEVANT)
                            .browse(FolderPages.Target.NOT_RELEVANT_FOLDER.forName())
                            .after(FolderPages.statusElement(FolderPages.Target.NOT_RELEVANT_FOLDER.forName(),
                                    FolderPages.statusOfCodeSetFolder(state.values().corpusRoot(),
                                            held.getOrDefault("new-not-relevant-folder", ""))))
                            .html())
                    .append("</section>\n").append(ControlPanelPage.button("save", "Save", false)).append('\n')
                    .append(ControlPanelPage.button("save-then-label", "Save, then Label", false)).append("\n</form>\n");
        }
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Relevance", 0, body.toString()));
    }

    /** One item of a list of folders: the folder, its hidden control and the button that removes it. */
    private static String listItem(String folder, int i) {
        return "<li data-folder=\"" + ControlPanelPage.escape(folder) + "\"><input type=\"hidden\" name=\"folder-" + i
                + "\" value=\"" + ControlPanelPage.escape(folder) + "\"> <code>" + ControlPanelPage.escape(folder)
                + "</code> " + ControlPanelPage.button("remove-" + i, "Remove", false) + "</li>\n";
    }

    private void saveNotRelevant(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = state.workingDirectory();
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get() == FolderPages.Target.NOT_RELEVANT_FOLDER) {
            state.holdForm("not-relevant", FolderPages.withoutBrowse(fields, browse.get().field()));
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(),
                    fields.getOrDefault(browse.get().field(), "").stripTrailing()));
            return;
        }
        if (folder.isEmpty()) {
            state.say("/labels", NO_WORKING_DIRECTORY);
            Exchanges.redirect(exchange, "/labels");
            return;
        }
        List<String> kept = new ArrayList<>();
        String removed = null;
        for (String name : fields.keySet()) {
            if (name.startsWith("remove-")) {
                removed = fields.get("folder-" + name.substring("remove-".length()));
            }
        }
        for (int i = 1; fields.containsKey("folder-" + i); i++) {
            String listed = fields.get("folder-" + i);
            if (!listed.equals(removed)) {
                kept.add(listed);
            }
        }
        String typed = fields.getOrDefault("new-not-relevant-folder", "").strip();
        if (removed == null && !typed.isEmpty()) {
            Optional<String> refusal = refusalOfNotRelevantFolder(typed, kept);
            if (refusal.isPresent()) {
                state.holdForm("not-relevant", FolderPages.withoutBrowse(fields));
                state.say("/labels", refusal.get());
                Exchanges.redirect(exchange, "/labels");
                return;
            }
            kept.add(typed);
        }
        int[] counts = new int[2];
        boolean[] answered = {false};
        List<WorkingDirectoryWrite.Change> changes = List.of(
                new WorkingDirectoryWrite.Change(NotRelevantFolders.FILE_NAME, current -> {
                    NotRelevantFolders.read(current);
                    return NotRelevantFolders.render(kept);
                }),
                new WorkingDirectoryWrite.Change(LabelAnswers.FILE_NAME, current -> {
                    if (current.isEmpty()) {
                        return null;
                    }
                    answered[0] = true;
                    return answersGivenBy(current.get(), kept, counts);
                }));
        Optional<String> refusal = state.write(folder.get(), changes);
        if (refusal.isPresent()) {
            state.holdForm("not-relevant", FolderPages.withoutBrowse(fields));
            state.say("/labels", refusal.get());
            Exchanges.redirect(exchange, "/labels");
            return;
        }
        state.letGoOfForm("not-relevant");
        if (fields.containsKey("save-then-label")) {
            Exchanges.redirect(exchange, InvocationPages.startAndSay(state, ControlPanelState.Start.LABEL, "/labels"));
            return;
        }
        Path file = folder.get().resolve(NotRelevantFolders.FILE_NAME);
        state.say("/labels", answered[0]
                ? "the folders not relevant are saved in " + file + ", and the " + counts[0] + " questions answered: "
                        + counts[1] + " not relevant, " + (counts[0] - counts[1]) + " relevant"
                : "the folders not relevant are saved in " + file + "; there are no questions to answer yet");
        Exchanges.redirect(exchange, "/labels");
    }

    /**
     * The label file's text with each question answered by the folders listed (ADR-334 §6): {@code false} where its
     * path lies at or below one, {@code true} otherwise. Only a {@code relevant} line whose answer changes is rewritten.
     *
     * @param counts set to the number of questions and the number answered not relevant
     */
    private static String answersGivenBy(String text, List<String> folders, int[] counts) {
        List<LabelAnswers.Question> questions = LabelAnswers.read(text);
        Map<String, Optional<Boolean>> changed = new LinkedHashMap<>();
        counts[0] = questions.size();
        counts[1] = 0;
        for (LabelAnswers.Question question : questions) {
            boolean relevant = folders.stream().noneMatch(folder -> NotRelevantFolders.lies(question.path(), folder));
            if (!relevant) {
                counts[1]++;
            }
            if (!question.relevant().equals(Optional.of(relevant))) {
                changed.put(question.path(), Optional.of(relevant));
            }
        }
        return LabelAnswers.rewrite(Optional.of(text), changed);
    }

    /** The line refusing {@code typed} as a folder not relevant, or none (ADR-334 §6). */
    private Optional<String> refusalOfNotRelevantFolder(String typed, List<String> listed) {
        String refused = "the folder " + typed + " is not added: ";
        if (typed.equals(".") || typed.equals("./")) {
            return Optional.of(refused + "the corpus root holds every question; name a folder below it");
        }
        boolean belowACodeSetFolder = NotRelevantFolders.isAbsolute(typed)
                && codeSetFoldersStated().stream().map(CodeSetFolderChecks::spelt)
                        .anyMatch(stated -> NotRelevantFolders.lies(typed, stated));
        if (!NotRelevantFolders.isWritten(typed) || (NotRelevantFolders.isAbsolute(typed) && !belowACodeSetFolder)) {
            return Optional.of(refused
                    + "it is not a folder below the corpus root, written with / between its elements");
        }
        if (listed.contains(typed)) {
            return Optional.of(refused + "it is listed already");
        }
        return Optional.empty();
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
                                    statusOfNewFolder(newFolder)))
                            .html())
                    .append(ControlPanelPage.Field.of("new-name", "Name").value(held.getOrDefault("new-name", ""))
                            .example("Billing engine").hint("hint-new-name", HINT_OF_A_NAME).html())
                    .append("</section>\n").append(ControlPanelPage.button("save", "Save", false)).append("\n</form>\n");
            body.append(codeSetFoldersForm());
        }
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Code sets", 0, body.toString()));
    }

    /** The status of the new set's folder: under the corpus root, or whole where it lies at or below a folder outside it. */
    private FolderPages.Status statusOfNewFolder(String newFolder) {
        return isBelowACodeSetFolder(newFolder) ? FolderPages.statusOf(newFolder, false)
                : FolderPages.statusOfCodeSetFolder(state.values().corpusRoot(), newFolder);
    }

    /** Whether {@code folder} is spelt absolute and lies at or below a folder the profile's {@code codeSetFolders} states. */
    private boolean isBelowACodeSetFolder(String folder) {
        String stripped = folder.strip();
        return NotRelevantFolders.isAbsolute(stripped) && codeSetFoldersStated().stream()
                .map(CodeSetFolderChecks::spelt).anyMatch(stated -> NotRelevantFolders.lies(stripped, stated));
    }

    /** The form {@code code-set-folders} (ADR-334 §5.2): the folders the profile states, one to add, and the reason. */
    private String codeSetFoldersForm() {
        Map<String, String> held = state.heldForm("code-set-folders");
        Map<ProfileKey, ProfileEdit.Stated> profile = profileOf(state);
        ProfileEdit.Stated stated = profile.get(ProfileKey.CODE_SET_FOLDERS);
        String provenance = stated == null || stated.provenance() == null ? "" : stated.provenance();
        StringBuilder form = new StringBuilder("<form name=\"code-set-folders\" method=\"post\""
                + " action=\"/code-set-folders\">\n<section class=\"card\">\n<h2>Folders outside the corpus root</h2>\n"
                + "<ul id=\"code-set-folders\">\n");
        int i = 1;
        for (String folder : CodeSetFolderChecks.statedIn(stated == null ? null : stated.value())) {
            form.append(listItem(folder, i++));
        }
        String typed = held.getOrDefault("new-code-set-folder", "");
        form.append("</ul>\n")
                .append(ControlPanelPage.Field.of("new-code-set-folder", "Folder outside the corpus root")
                        .value(typed).example(ProfileKey.CODE_SET_FOLDERS.example())
                        .hint("hint-new-code-set-folder", HINT_OF_A_CODE_SET_FOLDER)
                        .browse(FolderPages.Target.CODE_SET_FOLDER.forName())
                        .after(FolderPages.statusElement(FolderPages.Target.CODE_SET_FOLDER.forName(),
                                FolderPages.statusOf(typed, false)))
                        .html())
                .append(ControlPanelPage.provenance("codeSetFolders.provenance", "f-codeSetFolders-provenance",
                        "Why this value?", held.getOrDefault("codeSetFolders.provenance", provenance),
                        "hint-codeSetFolders-provenance"))
                .append(ControlPanelPage.costElement(ProfileKey.CODE_SET_FOLDERS)).append('\n')
                .append("</section>\n").append(ControlPanelPage.button("save", "Save", false)).append("\n</form>\n");
        return form.toString();
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
        String typedFolder = fields.getOrDefault("new-folder", "").strip();
        if (FolderPages.climbs(typedFolder) && !isBelowACodeSetFolder(typedFolder)) {
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

    // ---- /code-set-folders

    /** The form {@code code-set-folders}'s address: a {@code POST} adds or removes a folder; a {@code GET} lands on the page. */
    private void codeSetFolders(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/code-sets");
            return;
        }
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get() == FolderPages.Target.CODE_SET_FOLDER) {
            state.holdForm("code-set-folders", FolderPages.withoutBrowse(fields, browse.get().field()));
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(),
                    fields.getOrDefault(browse.get().field(), "").stripTrailing()));
            return;
        }
        Optional<Path> folder = state.workingDirectory();
        if (folder.isEmpty()) {
            state.say("/code-sets", NO_WORKING_DIRECTORY);
            Exchanges.redirect(exchange, "/code-sets");
            return;
        }
        ProfileEdit.Stated stated = profileOf(state).get(ProfileKey.CODE_SET_FOLDERS);
        List<String> folders = new ArrayList<>(CodeSetFolderChecks.statedIn(stated == null ? null : stated.value()));
        String removed = null;
        for (String name : fields.keySet()) {
            if (name.startsWith("remove-")) {
                removed = fields.get("folder-" + name.substring("remove-".length()));
            }
        }
        String typed = fields.getOrDefault("new-code-set-folder", "").stripTrailing();
        if (removed != null) {
            folders.remove(removed);
        } else {
            Optional<String> refusal = refusalOfCodeSetFolder(typed, folders);
            if (refusal.isPresent()) {
                state.holdForm("code-set-folders", FolderPages.withoutBrowse(fields));
                state.say("/code-sets", refusal.get());
                Exchanges.redirect(exchange, "/code-sets");
                return;
            }
            folders.add(typed);
        }
        String value = String.join(File.pathSeparator, folders);
        String provenance = fields.getOrDefault("codeSetFolders.provenance", "");
        Optional<String> refusal = ProfileEdit.refusalOfSave(ProfileKey.CODE_SET_FOLDERS, value, provenance,
                Optional.ofNullable(stated), Optional.empty());
        if (refusal.isEmpty()) {
            refusal = state.write(folder.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.rewrite(current, ProfileKey.CODE_SET_FOLDERS, value, provenance));
        }
        if (refusal.isPresent()) {
            state.holdForm("code-set-folders", FolderPages.withoutBrowse(fields));
            state.say("/code-sets", refusal.get());
        } else {
            state.letGoOfForm("code-set-folders");
            state.say("/code-sets", "codeSetFolders is saved in " + folder.get().resolve(ProfileEdit.FILE_NAME));
        }
        Exchanges.redirect(exchange, "/code-sets");
    }

    /** The line refusing {@code typed} as a folder to add, or none (ADR-334 §5.2), in the order of §5.1's start. */
    private Optional<String> refusalOfCodeSetFolder(String typed, List<String> stated) {
        if (typed.isBlank()) {
            return Optional.of("no folder is added: none is typed in the field");
        }
        LaunchValues values = state.values();
        Path work = state.workingDirectory().orElseThrow();
        ProfileEdit.Stated seed = profileOf(state).get(ProfileKey.SEED_FOLDER);
        String output = values.deliverableRoot().isBlank() ? work.resolve("deliverable").toString()
                : values.deliverableRoot();
        List<CodeSetFolderChecks.Apart> apart = new ArrayList<>(List.of(
                new CodeSetFolderChecks.Apart("corpus root", values.corpusRoot()),
                new CodeSetFolderChecks.Apart("working directory", work.toString()),
                new CodeSetFolderChecks.Apart("seed folder", seed == null || seed.value() == null ? "" : seed.value()),
                new CodeSetFolderChecks.Apart("output folder", output)));
        for (String listed : stated) {
            apart.add(new CodeSetFolderChecks.Apart("code set folder", listed));
        }
        return CodeSetFolderChecks.refusal(typed, apart);
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
        Map<String, String> typed = state.heldForm("profile");
        List<ProfileKey> shown = new ArrayList<>();
        StringBuilder list = new StringBuilder("<ul id=\"profile-list\">\n");
        StringBuilder sections = new StringBuilder("<div class=\"sections\">\n");
        for (ProfileKey key : ProfileKey.values()) {
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
            boolean seedFolder = key == ProfileKey.SEED_FOLDER;
            String forName = FolderPages.Target.PROFILE_SEED_FOLDER.forName();
            String value = typed.getOrDefault(key.key(), fileValue);
            sections.append("<section class=\"card\" id=\"key-").append(key.key()).append("\">\n")
                    .append("<h2>").append(key.key()).append("</h2>\n")
                    .append("<input type=\"hidden\" name=\"").append(key.key()).append(".drawn\" value=\"")
                    .append(ProfileEdit.drawnOf(key, fileValue, fileProvenance)).append("\">\n")
                    .append(ControlPanelPage.text("p", "about-" + key.key(), key.about())).append('\n')
                    .append(inUse(key, held, effective)).append('\n')
                    .append(ControlPanelPage.text("p", "current-" + key.key(), current)).append('\n')
                    .append(measurement(key, measured.get(key)))
                    .append(ControlPanelPage.keyControl(key, key.key(), "f-" + key.key() + "-value", "Value", value,
                            seedFolder ? forName : "",
                            seedFolder ? FolderPages.statusElement(forName, FolderPages.statusOf(value, false)) : "",
                            true))
                    .append(ControlPanelPage.Field.of(key.key() + ".provenance", "Why this value?")
                            .id("f-" + key.key() + "-provenance")
                            .value(typed.getOrDefault(key.key() + ".provenance", fileProvenance))
                            .example(ControlPanelPage.REASON_EXAMPLE)
                            .hint("hint-" + key.key() + "-provenance", HINT_OF_A_PROVENANCE).html())
                    .append(ControlPanelPage.costElement(key)).append('\n')
                    .append(NextPages.reportsOf(key.key()))
                    .append("</section>\n");
        }
        body.append("<div class=\"profile\">\n<div class=\"card\">\n").append(list).append("</ul>\n</div>\n")
                .append("<form name=\"profile\" method=\"post\" action=\"/profile\">\n")
                .append(sections).append("</div>\n")
                .append(ControlPanelPage.button("save", "Save", false)).append('\n')
                .append(ControlPanelPage.button("reset", "Reset", false)).append("\n</form>\n</div>\n")
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
     * What the page holds in place of the form where the file is not in the form the jar writes it (ADR-330 §6): the
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

    /** One value of the form posted, as the save compares and writes it. */
    private record Posted(ProfileKey key, String value, String provenance, Optional<String> drawn) {
    }

    private void saveProfile(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        Optional<Path> folder = state.workingDirectory();
        Optional<FolderPages.Target> browse = FolderPages.browsed(fields);
        if (browse.isPresent() && browse.get() == FolderPages.Target.PROFILE_SEED_FOLDER) {
            state.holdForm("profile", FolderPages.withoutBrowse(fields, browse.get().field()));
            Exchanges.redirect(exchange, FolderPages.addressOf(browse.get(),
                    fields.getOrDefault(browse.get().field(), "").stripTrailing()));
            return;
        }
        if (fields.containsKey("reset")) {
            state.letGoOfForm("profile");
            state.say("/profile", "the form is back to profile.yaml as last saved");
            Exchanges.redirect(exchange, "/profile");
            return;
        }
        if (folder.isEmpty()) {
            state.say("/profile", NO_WORKING_DIRECTORY);
            Exchanges.redirect(exchange, "/profile");
            return;
        }
        List<Posted> changed = new ArrayList<>();
        for (ProfileKey key : ProfileKey.values()) {
            if (!fields.containsKey(key.key()) && !fields.containsKey(key.key() + ".provenance")) {
                continue;
            }
            Posted posted = new Posted(key, fields.getOrDefault(key.key(), ""),
                    fields.getOrDefault(key.key() + ".provenance", ""), Optional.ofNullable(fields.get(key.key() + ".drawn")));
            if (posted.drawn().isPresent()
                    && ProfileEdit.drawnOf(key, posted.value(), posted.provenance()).equals(posted.drawn().get())) {
                continue;
            }
            changed.add(posted);
        }
        if (changed.isEmpty()) {
            state.letGoOfForm("profile");
            state.say("/profile", "nothing was written: every value is as profile.yaml states it");
            Exchanges.redirect(exchange, "/profile");
            return;
        }
        for (Posted each : changed) {
            Optional<String> refusal = ProfileEdit.refusalOfSave(each.key(), each.value(), each.provenance(),
                    statedIn(folder.get(), each.key()), each.drawn());
            if (refusal.isPresent()) {
                state.holdForm("profile", FolderPages.withoutBrowse(fields));
                state.say("/profile", refusal.get());
                Exchanges.redirect(exchange, "/profile#key-" + each.key().key());
                return;
            }
        }
        Optional<String> refusal = state.write(folder.get(), ProfileEdit.FILE_NAME, current -> {
            Optional<String> text = current;
            for (Posted each : changed) {
                text = Optional.of(ProfileEdit.rewrite(text, each.key(), each.value(), each.provenance()));
            }
            return text.orElseThrow();
        });
        String first = "/profile#key-" + changed.getFirst().key().key();
        if (refusal.isPresent()) {
            state.holdForm("profile", FolderPages.withoutBrowse(fields));
            state.say("/profile", refusal.get());
        } else {
            state.letGoOfForm("profile");
            state.say("/profile", String.join(", ", changed.stream().map(each -> each.key().key()).toList())
                    + " saved in " + folder.get().resolve(ProfileEdit.FILE_NAME));
        }
        Exchanges.redirect(exchange, first);
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
