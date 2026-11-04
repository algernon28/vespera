package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The folders to process and the code sets in one tree of the corpus's folders, shown one folder at a time (ADR-340 §5):
 * the form {@code folders-to-process}, in the section {@code values-corpus} of the drawer. A folder unticked and saved is
 * written into the profile's {@code foldersNotWalked}; a folder marked as code is named in {@code code-set-names.yaml};
 * both files are written in one action, all or none. Moving through the tree writes nothing: what was ticked is held in
 * memory beside the files ({@link ControlPanelState#heldTree}), only where it differs from them.
 *
 * <p>No file is listed and none is read, the folders being listed as the folder page lists them (ADR-331 §5).
 */
final class FolderTree {

    /** The form's name and the path it posts to. */
    static final String FORM = "folders-to-process";

    /** What {@code data-walked} says of a row. */
    private static final String WALKED = "walked";
    private static final String NOT_WALKED = "not-walked";
    private static final String NOT_WALKED_ABOVE = "not-walked-above";
    private static final String OUTSIDE = "outside";

    private static final String PROVENANCE = "foldersNotWalked.provenance";

    private static final String HINT_OF_A_NAME =
            "The name the pages give this set of code. Left empty, it is the folder's own name.";

    private final ControlPanelState state;

    FolderTree(ControlPanelState state) {
        this.state = state;
    }

    /** What the files state of the folders: the list as the profile states it, and the code sets' names. */
    private record FilesStated(List<String> listed, ProfileEdit.Stated stated, List<CodeSetRenames.Rename> renames) {
    }

    /** The folder the tree shows: how it stands and, where it could be listed, its folder and the path of its rows. */
    private record Shown(String tree, String data, Optional<Path> folder, String base) {
    }

    // ---- drawing

    ValuePages.Section form(String tree) {
        FilesStated files;
        try {
            files = filesStated();
        } catch (IllegalArgumentException | IOException unreadable) {
            return new ValuePages.Section("", unreadable.getMessage());
        }
        ControlPanelState.TreeHold held = state.heldTree();
        Shown shown = shownFolder(tree, files);
        Map<String, String> heldForm = state.heldForm(FORM);
        StringBuilder html = new StringBuilder("<form name=\"" + FORM + "\" method=\"post\" action=\"/" + FORM + "\">\n");
        html.append("<input type=\"hidden\" name=\"tree\" value=\"").append(ControlPanelPage.escape(shown.tree()))
                .append("\">\n");
        html.append("<div id=\"folder-tree\" data-tree=\"").append(shown.data()).append("\" data-path=\"")
                .append(ControlPanelPage.escape(shown.tree())).append("\">\n");
        html.append(path(shown));
        if (shown.data().equals("listed")) {
            html.append(rows(shown, files, held));
        } else {
            html.append("<p class=\"hint\">").append(ControlPanelPage.escape(wordsOf(shown.data()))).append("</p>\n")
                    .append("<ul id=\"tree-rows\"></ul>\n");
        }
        html.append("</div>\n");
        String provenance = heldForm.getOrDefault(PROVENANCE, files.stated() == null || files.stated().provenance() == null
                ? "" : files.stated().provenance());
        String fileValue = files.stated() == null || files.stated().value() == null ? "" : files.stated().value();
        String fileProvenance = files.stated() == null || files.stated().provenance() == null ? ""
                : files.stated().provenance();
        html.append("<input type=\"hidden\" name=\"foldersNotWalked.drawn\" value=\"")
                .append(ProfileEdit.drawnOf(ProfileKey.FOLDERS_NOT_WALKED, fileValue, fileProvenance)).append("\">\n");
        html.append(ControlPanelPage.provenance(PROVENANCE, FORM + "-f-provenance", "Why this value?", provenance,
                FORM + "-hint-provenance"));
        html.append(ControlPanelPage.costElement(FORM + "-", ProfileKey.FOLDERS_NOT_WALKED)).append('\n');
        html.append(ControlPanelPage.button("save", "Save", false)).append('\n')
                .append(ControlPanelPage.button("reset", "Reset", false)).append("\n</form>\n");
        return new ValuePages.Section(html.toString(), null);
    }

    private static String wordsOf(String data) {
        return switch (data) {
            case "no-corpus-root" -> "No documents folder is named, so there are no folders to show. Name one under Folders.";
            case "no-such-folder" -> "There is no such folder in the documents folder.";
            case "cannot-be-listed" -> "This folder cannot be listed.";
            default -> "This folder lies outside the documents folder, and outside the code folders elsewhere.";
        };
    }

    /** {@code #tree-path}: a button for each folder above the one shown, the corpus root first, and the folder shown. */
    private String path(Shown shown) {
        StringBuilder path = new StringBuilder("<div id=\"tree-path\">");
        if (shown.data().equals("no-corpus-root")) {
            return path.append("</div>\n").toString();
        }
        List<String[]> above = shown.data().equals("listed") || shown.data().equals("no-such-folder")
                || shown.data().equals("cannot-be-listed") ? ancestors(shown.tree()) : List.<String[]>of(new String[] {rootName(), ""});
        int j = 0;
        for (String[] each : above) {
            path.append(ControlPanelPage.button("up-" + j++, each[0], false));
        }
        if (!shown.tree().isEmpty()) {
            path.append("<strong>").append(ControlPanelPage.escape(last(shown.tree()))).append("</strong>");
        } else if (shown.data().equals("listed")) {
            path.append("<strong>").append(ControlPanelPage.escape(rootName())).append("</strong>");
        }
        return path.append("</div>\n").toString();
    }

    /** The name of the corpus root, which the button for the folder above everything carries. */
    private String rootName() {
        String root = state.values().corpusRoot().stripTrailing();
        try {
            Path name = root.isEmpty() ? null : Path.of(root).getFileName();
            return name == null ? (root.isEmpty() ? "Documents folder" : root) : name.toString();
        } catch (InvalidPathException cannotBeHeld) {
            return "Documents folder";
        }
    }

    /**
     * The folders above the one {@code tree} names, as pairs of their words and their {@code tree}: the corpus root, then,
     * for a folder below a code folder elsewhere, that folder and each below it, spelt absolute.
     */
    private List<String[]> ancestors(String tree) {
        List<String[]> above = new ArrayList<>();
        above.add(new String[] {rootName(), ""});
        if (tree.isEmpty()) {
            return above;
        }
        if (NotRelevantFolders.isAbsolute(tree)) {
            String base = baseOf(tree);
            if (base != null) {
                int from = base.length();
                above.add(new String[] {last(base), base});
                for (int slash = tree.indexOf('/', from + 1); slash > 0; slash = tree.indexOf('/', slash + 1)) {
                    String parent = tree.substring(0, slash);
                    above.add(new String[] {last(parent), parent});
                }
                if (tree.equals(base)) {
                    above.remove(above.size() - 1);
                }
            }
            return above;
        }
        String[] elements = tree.split("/");
        StringBuilder parent = new StringBuilder();
        for (int i = 0; i < elements.length - 1; i++) {
            parent.append(i == 0 ? "" : "/").append(elements[i]);
            above.add(new String[] {elements[i], parent.toString()});
        }
        return above;
    }

    /** The code folder elsewhere, spelt, that {@code tree} lies at or below; none where it lies below none. */
    private String baseOf(String tree) {
        for (String folder : codeSetFolders()) {
            String spelt = CodeSetFolderChecks.spelt(folder);
            if (NotRelevantFolders.lies(tree, spelt)) {
                return spelt;
            }
        }
        return null;
    }

    private List<String> codeSetFolders() {
        ProfileEdit.Stated stated = ValuePages.profileOf(state).get(ProfileKey.CODE_SET_FOLDERS);
        return CodeSetFolderChecks.statedIn(stated == null ? null : stated.value());
    }

    private static String last(String folder) {
        return folder.substring(folder.lastIndexOf('/') + 1);
    }

    private FilesStated filesStated() throws IOException {
        Optional<Path> work = state.workingDirectory();
        Map<ProfileKey, ProfileEdit.Stated> profile = ValuePages.profileOf(state);
        ProfileEdit.Stated stated = profile.get(ProfileKey.FOLDERS_NOT_WALKED);
        List<String> listed = CodeSetFolderChecks.statedIn(stated == null ? null : stated.value());
        List<CodeSetRenames.Rename> renames = List.of();
        if (work.isPresent()) {
            Path names = work.get().resolve(CodeSetRenames.FILE_NAME);
            renames = CodeSetRenames.read(Files.isRegularFile(names)
                    ? Optional.of(Files.readString(names, StandardCharsets.UTF_8)) : Optional.empty());
        }
        return new FilesStated(listed, stated, renames);
    }

    /** How the tree stands at {@code tree}: the folder it names, as the folder page would find it. */
    private Shown shownFolder(String tree, FilesStated files) {
        String corpusRoot = state.values().corpusRoot();
        if (corpusRoot == null || corpusRoot.isBlank() || rootCannotBeHeld(corpusRoot)) {
            return new Shown("", "no-corpus-root", Optional.empty(), "");
        }
        if (NotRelevantFolders.isAbsolute(tree)) {
            String base = baseOf(tree);
            if (base == null || tree.endsWith("/") || tree.contains("//") || tree.contains("/../") || tree.contains("\\")) {
                return new Shown("", "outside", Optional.empty(), "");
            }
            Path folder;
            try {
                folder = Path.of(tree);
            } catch (InvalidPathException cannotBeHeld) {
                return new Shown("", "outside", Optional.empty(), "");
            }
            if (!Files.isDirectory(folder)) {
                return new Shown(tree, "no-such-folder", Optional.empty(), base);
            }
            return FolderPages.subfolders(folder).isPresent()
                    ? new Shown(tree, "listed", Optional.of(folder), base)
                    : new Shown(tree, "cannot-be-listed", Optional.empty(), base);
        }
        FolderPages.Under under = FolderPages.under(corpusRoot, tree);
        return switch (under.standing()) {
            case NO_CORPUS_ROOT -> new Shown("", "no-corpus-root", Optional.empty(), "");
            case OUTSIDE -> new Shown("", "outside", Optional.empty(), "");
            case NO_SUCH_FOLDER -> new Shown(tree, "no-such-folder", Optional.empty(), "");
            case FOUND -> FolderPages.subfolders(under.folder()).isPresent()
                    ? new Shown(under.relative(), "listed", Optional.of(under.folder()), "")
                    : new Shown(under.relative(), "cannot-be-listed", Optional.empty(), "");
        };
    }

    private static boolean rootCannotBeHeld(String corpusRoot) {
        try {
            Path.of(corpusRoot.stripTrailing());
            return false;
        } catch (InvalidPathException cannotBeHeld) {
            return true;
        }
    }

    /** The rows of the folder shown: its subfolders by name, and at the corpus root the code folders elsewhere. */
    private String rows(Shown shown, FilesStated files, ControlPanelState.TreeHold held) {
        List<String> folders = new ArrayList<>();
        List<String> names = FolderPages.subfolders(shown.folder().orElseThrow()).orElse(List.of());
        for (String name : names) {
            String parent = shown.tree();
            folders.add(parent.isEmpty() ? name : parent + "/" + name);
        }
        if (shown.tree().isEmpty()) {
            for (String folder : codeSetFolders()) {
                folders.add(CodeSetFolderChecks.spelt(folder));
            }
        }
        StringBuilder rows = new StringBuilder("<ul id=\"tree-rows\">\n");
        int i = 1;
        for (String folder : folders) {
            rows.append(row(i++, folder, files, held));
        }
        rows.append("</ul>\n");
        if (!folders.isEmpty()) {
            rows.append("<p class=\"hint\" id=\"hint-code-set-name\">").append(ControlPanelPage.escape(HINT_OF_A_NAME))
                    .append("</p>\n");
        }
        return rows.toString();
    }

    private String row(int i, String folder, FilesStated files, ControlPanelState.TreeHold held) {
        boolean absolute = NotRelevantFolders.isAbsolute(folder);
        String walked = absolute ? OUTSIDE : walkedOf(folder, files, held);
        boolean marked = held.marked().containsKey(folder) ? held.marked().get(folder) : renameOf(files, folder).isPresent();
        String name = held.names().containsKey(folder) ? held.names().get(folder)
                : renameOf(files, folder).map(CodeSetRenames.Rename::name).orElse("");
        boolean notRead = walked.equals(NOT_WALKED) || walked.equals(NOT_WALKED_ABOVE);
        StringBuilder row = new StringBuilder("<li data-folder=\"").append(ControlPanelPage.escape(folder))
                .append("\" data-walked=\"").append(walked).append("\" data-code-set=\"")
                .append(marked ? "marked" : "not-marked").append("\">\n");
        row.append("<input type=\"hidden\" name=\"folder-").append(i).append("\" value=\"")
                .append(ControlPanelPage.escape(folder)).append("\">")
                .append("<input type=\"hidden\" name=\"drawn-").append(i).append("\" value=\"").append(walked)
                .append("\">\n");
        row.append("<span class=\"folder-name\">").append(ControlPanelPage.escape(absolute ? folder : last(folder)))
                .append("</span>\n");
        if (!walked.equals(OUTSIDE)) {
            row.append("<label><input type=\"checkbox\" name=\"walk-").append(i).append('"')
                    .append(walked.equals(WALKED) ? " checked" : "")
                    .append(walked.equals(NOT_WALKED_ABOVE) ? " disabled" : "").append("> Read</label>\n");
        }
        row.append("<label><input type=\"checkbox\" name=\"code-set-").append(i).append('"')
                .append(marked ? " checked" : "").append(notRead ? " disabled" : "").append("> Code</label>\n");
        row.append("<label for=\"f-code-set-name-").append(i).append("\">Name shown in the pages</label>")
                .append("<input type=\"text\" class=\"code-set-name\" id=\"f-code-set-name-").append(i)
                .append("\" name=\"code-set-name-").append(i).append("\" value=\"").append(ControlPanelPage.escape(name))
                .append("\" placeholder=\"").append(ControlPanelPage.escape(ControlPanelPage.IN_USE + last(folder)))
                .append("\" aria-describedby=\"hint-code-set-name\">\n");
        row.append(ControlPanelPage.button("open-" + i, "Open", false)).append("\n</li>\n");
        return row.toString();
    }

    /** Whether the folder is read, and why not: the files' list, as the tree holds it changed. */
    private String walkedOf(String folder, FilesStated files, ControlPanelState.TreeHold held) {
        List<String> effective = effectiveList(files.listed(), held);
        for (String listed : effective) {
            if (!same(listed, folder) && NotRelevantFolders.lies(folder, listed)) {
                return NOT_WALKED_ABOVE;
            }
        }
        return containsFolder(effective, folder) ? NOT_WALKED : WALKED;
    }

    /** Whether two folders are the same, compared element by element, without regard to case where the walk does. */
    private static boolean same(String a, String b) {
        return NotRelevantFolders.lies(a, b) && NotRelevantFolders.lies(b, a);
    }

    private static boolean containsFolder(List<String> list, String folder) {
        return list.stream().anyMatch(each -> same(each, folder));
    }

    /** The list as the files state it, each folder the tree holds ticked out of it and each held unticked in it. */
    private static List<String> effectiveList(List<String> listed, ControlPanelState.TreeHold held) {
        List<String> effective = new ArrayList<>(listed);
        held.walked().forEach((folder, walked) -> {
            if (walked) {
                effective.removeIf(each -> same(each, folder));
            } else if (!containsFolder(effective, folder)) {
                effective.add(folder);
            }
        });
        return effective;
    }

    private static Optional<CodeSetRenames.Rename> renameOf(FilesStated files, String folder) {
        return files.renames().stream().filter(rename -> rename.folder().equals(folder)).findFirst();
    }

    // ---- /folders-to-process

    /** The address of the home page at the tree's folder {@code tree}, landing in the section. */
    static String addressOf(String tree) {
        return "/?open=values-corpus" + (tree.isEmpty() ? "" : "&tree=" + URLEncoder.encode(tree, StandardCharsets.UTF_8))
                + "#values-corpus";
    }

    /** A {@code GET} lands on the section; a {@code POST} presses Open, a folder above, Save or Reset. */
    void handle(HttpExchange exchange) throws IOException {
        if (Exchanges.isRead(exchange)) {
            Exchanges.redirect(exchange, "/?open=values-corpus#values-corpus");
            return;
        }
        Map<String, String> fields = Exchanges.form(exchange);
        String tree = fields.getOrDefault("tree", "");
        if (fields.containsKey("reset")) {
            state.letGoOfTree();
            Exchanges.redirect(exchange, addressOf(tree));
            return;
        }
        FilesStated files;
        try {
            files = filesStated();
        } catch (IllegalArgumentException | IOException unreadable) {
            state.say("/", unreadable.getMessage());
            Exchanges.redirect(exchange, addressOf(tree));
            return;
        }
        ControlPanelState.TreeHold merged = merge(fields, files);
        state.holdTree(merged);
        Map<String, String> typed = new HashMap<>();
        typed.put(PROVENANCE, fields.getOrDefault(PROVENANCE, ""));
        state.holdForm(FORM, typed);

        for (String name : fields.keySet()) {
            if (name.startsWith("open-")) {
                String folder = fields.get("folder-" + name.substring("open-".length()));
                Exchanges.redirect(exchange, addressOf(folder == null ? tree : folder));
                return;
            }
            if (name.startsWith("up-")) {
                List<String[]> above = ancestors(tree);
                try {
                    int j = Integer.parseInt(name.substring("up-".length()));
                    Exchanges.redirect(exchange, addressOf(j >= 0 && j < above.size() ? above.get(j)[1] : tree));
                } catch (NumberFormatException notANumber) {
                    Exchanges.redirect(exchange, addressOf(tree));
                }
                return;
            }
        }
        save(exchange, fields, tree, merged);
    }

    /**
     * What the tree holds once the rows posted are taken in: a row whose control was drawn active holds its state where it
     * differs from what the files state, and lets go of what it held where it does not; a row drawn disabled, or not
     * drawn, holds nothing and keeps what was held.
     */
    private ControlPanelState.TreeHold merge(Map<String, String> fields, FilesStated files) {
        ControlPanelState.TreeHold held = state.heldTree();
        Map<String, Boolean> walked = new HashMap<>(held.walked());
        Map<String, Boolean> marked = new HashMap<>(held.marked());
        Map<String, String> names = new HashMap<>(held.names());
        for (int i = 1; fields.containsKey("folder-" + i); i++) {
            String folder = fields.get("folder-" + i);
            boolean absolute = NotRelevantFolders.isAbsolute(folder);
            String drawn = fields.getOrDefault("drawn-" + i, absolute ? OUTSIDE : fileWalkedOf(folder, files));
            if (drawn.equals(WALKED) || drawn.equals(NOT_WALKED)) {
                boolean ticked = fields.containsKey("walk-" + i);
                boolean read = !containsFolder(files.listed(), folder);
                if (ticked == read) {
                    walked.remove(folder);
                } else {
                    walked.put(folder, ticked);
                }
            }
            if (drawn.equals(WALKED) || drawn.equals(OUTSIDE)) {
                boolean now = fields.containsKey("code-set-" + i);
                String name = fields.getOrDefault("code-set-name-" + i, "").strip();
                Optional<CodeSetRenames.Rename> rename = renameOf(files, folder);
                boolean differs = now != rename.isPresent() || (now && !name.equals(rename.get().name()));
                if (differs) {
                    marked.put(folder, now);
                    names.put(folder, name);
                } else {
                    marked.remove(folder);
                    names.remove(folder);
                }
            }
        }
        return new ControlPanelState.TreeHold(walked, marked, names);
    }

    private static String fileWalkedOf(String folder, FilesStated files) {
        for (String listed : files.listed()) {
            if (!same(listed, folder) && NotRelevantFolders.lies(folder, listed)) {
                return NOT_WALKED_ABOVE;
            }
        }
        return containsFolder(files.listed(), folder) ? NOT_WALKED : WALKED;
    }

    // ---- Save

    /** The list as it becomes: the folders held ticked taken out, those held unticked, none below another, added in path order. */
    private static List<String> listAfter(List<String> listed, ControlPanelState.TreeHold held) {
        List<String> list = new ArrayList<>(listed);
        List<String> added = new ArrayList<>();
        held.walked().forEach((folder, walked) -> {
            if (walked) {
                list.removeIf(each -> same(each, folder));
            } else if (!containsFolder(list, folder)) {
                added.add(folder);
            }
        });
        added.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo));
        for (String folder : added) {
            boolean below = list.stream().anyMatch(each -> NotRelevantFolders.lies(folder, each));
            if (!below) {
                list.add(folder);
            }
        }
        return list;
    }

    /** The names as they become: a folder marked named as typed or by its own name, one unmarked taken out, the rest kept. */
    private static List<CodeSetRenames.Rename> namesAfter(List<CodeSetRenames.Rename> renames,
            ControlPanelState.TreeHold held) {
        List<CodeSetRenames.Rename> after = new ArrayList<>(renames);
        List<CodeSetRenames.Rename> added = new ArrayList<>();
        held.marked().forEach((folder, marked) -> {
            String typed = held.names().getOrDefault(folder, "");
            String name = typed.isBlank() ? last(folder) : typed;
            int at = indexOf(after, folder);
            if (!marked) {
                if (at >= 0) {
                    after.remove(at);
                }
            } else if (at >= 0) {
                after.set(at, new CodeSetRenames.Rename(folder, name));
            } else {
                added.add(new CodeSetRenames.Rename(folder, name));
            }
        });
        added.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo).compare(a.folder(), b.folder()));
        after.addAll(added);
        return after;
    }

    private static int indexOf(List<CodeSetRenames.Rename> renames, String folder) {
        for (int i = 0; i < renames.size(); i++) {
            if (renames.get(i).folder().equals(folder)) {
                return i;
            }
        }
        return -1;
    }

    private void save(HttpExchange exchange, Map<String, String> fields, String tree, ControlPanelState.TreeHold held)
            throws IOException {
        Optional<Path> work = state.workingDirectory();
        String landing = addressOf(tree);
        if (work.isEmpty()) {
            state.say("/", ValuePages.NO_WORKING_DIRECTORY);
            Exchanges.redirect(exchange, landing);
            return;
        }
        FilesStated files;
        try {
            files = filesStated();
        } catch (IllegalArgumentException | IOException unreadable) {
            state.say("/", unreadable.getMessage());
            Exchanges.redirect(exchange, landing);
            return;
        }
        boolean listChanges = !listAfter(files.listed(), held).equals(files.listed());
        boolean namesChange = !namesAfter(files.renames(), held).equals(files.renames());
        if (!listChanges && !namesChange) {
            state.letGoOfTree();
            state.say("/", "nothing was written: every folder is as the files state it");
            Exchanges.redirect(exchange, landing);
            return;
        }
        String provenance = fields.getOrDefault(PROVENANCE, "");
        Optional<String> drawn = Optional.ofNullable(fields.get("foldersNotWalked.drawn"));
        int[] counts = new int[2];
        List<WorkingDirectoryWrite.Change> changes = List.of(
                new WorkingDirectoryWrite.Change(ProfileEdit.FILE_NAME, current -> {
                    Optional<ProfileEdit.Stated> stated = current.flatMap(text -> Optional.ofNullable(
                            ProfileEdit.read(text).get(ProfileKey.FOLDERS_NOT_WALKED)));
                    List<String> now = CodeSetFolderChecks.statedIn(stated.map(ProfileEdit.Stated::value).orElse(null));
                    List<String> after = listAfter(now, held);
                    counts[0] = after.size();
                    if (after.equals(now)) {
                        return null;
                    }
                    String value = String.join(File.pathSeparator, after);
                    Optional<String> refusal = ProfileEdit.refusalOfSave(ProfileKey.FOLDERS_NOT_WALKED, value, provenance,
                            stated, drawn);
                    if (refusal.isPresent()) {
                        throw new IllegalArgumentException(refusal.get());
                    }
                    return ProfileEdit.rewrite(current, ProfileKey.FOLDERS_NOT_WALKED, value, provenance);
                }),
                new WorkingDirectoryWrite.Change(CodeSetRenames.FILE_NAME, current -> {
                    List<CodeSetRenames.Rename> now = CodeSetRenames.read(current);
                    List<CodeSetRenames.Rename> after = namesAfter(now, held);
                    counts[1] = after.size();
                    return after.equals(now) ? null : CodeSetRenames.render(after);
                }));
        Optional<String> refusal = state.write(work.get(), changes);
        if (refusal.isPresent()) {
            Map<String, String> typed = new HashMap<>();
            typed.put(PROVENANCE, provenance);
            state.holdForm(FORM, typed);
            state.say("/", refusal.get());
        } else {
            state.letGoOfTree();
            state.say("/", "the folders to process are saved: " + counts[0] + " not read, " + counts[1]
                    + " named as code sets");
        }
        Exchanges.redirect(exchange, landing);
    }
}
