package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The folder page {@code /folders} and what a folder field says of its folder (ADR-331 §5, §6). A Browse button posts
 * its form, which holds what was typed, and lands here: the subfolders of one folder are listed by name as links, with
 * the roots, Up, and a form that puts the folder chosen in the field and returns to the page it came from. No file's
 * name or content is listed or read, no {@code GET} writes anything, and the field is one of a closed list of seven.
 */
final class FolderPages {

    /** The fields the folder page chooses for, each with the form it is in, its control and the page it returns to (ADR-331 §5). */
    enum Target {
        CORPUS_ROOT("vespera.corpus-root", "launch", "vespera.corpus-root", "/"),
        WORKING_DIRECTORY("vespera.working-dir", "launch", "vespera.working-dir", "/"),
        DELIVERABLE_ROOT("vespera.deliverable-root", "launch", "vespera.deliverable-root", "/"),
        SEED_FOLDER("seedFolder", "seed-folder", "seedFolder", "/"),
        NEXT_SEED_FOLDER("next-seedFolder", "next", "seedFolder", "/next"),
        PROFILE_SEED_FOLDER("profile-seedFolder", "seedFolder", "value", "/profile#key-seedFolder"),
        NEW_FOLDER("new-folder", "code-sets", "new-folder", "/code-sets");

        private final String forName;
        private final String form;
        private final String field;
        private final String back;

        Target(String forName, String form, String field, String back) {
            this.forName = forName;
            this.form = form;
            this.field = field;
            this.back = back;
        }

        /** The name the address and the Browse button give the field. */
        String forName() {
            return forName;
        }

        /** The name of the form the field is in. */
        String form() {
            return form;
        }

        /** The name of the field's control in that form. */
        String field() {
            return field;
        }

        static Optional<Target> named(String forName) {
            for (Target target : values()) {
                if (target.forName.equals(forName)) {
                    return Optional.of(target);
                }
            }
            return Optional.empty();
        }
    }

    /** What a folder field says of its folder: the value of {@code data-status}, and the words. */
    record Status(String attribute, String words) {
    }

    /** How a code set's folder stands under the corpus root. */
    private enum Standing { NO_CORPUS_ROOT, OUTSIDE, NO_SUCH_FOLDER, FOUND }

    /** A code set's folder: how it stands, its folder where found, and the path from the corpus root written with {@code /}. */
    private record Under(Standing standing, Path folder, String relative) {
    }

    private static final double BYTES_IN_A_GIB = 1024d * 1024d * 1024d;

    private final ControlPanelState state;

    FolderPages(ControlPanelState state) {
        this.state = state;
    }

    void register(Map<String, HttpHandler> pages) {
        pages.put("/folders", this::folders);
    }

    // ---- what a folder field says

    /** The status of the folder {@code value} names; {@code createdByTheJar} where the jar creates the folder that is not there. */
    static Status statusOf(String value, boolean createdByTheJar) {
        if (value.isBlank()) {
            return new Status("not-chosen", "Not chosen");
        }
        Path folder;
        try {
            folder = Path.of(value.stripTrailing()).toAbsolutePath().normalize();
        } catch (RuntimeException cannotBeHeld) {
            return new Status("no-such-folder", "No such folder");
        }
        if (Files.isDirectory(folder)) {
            return found(folder);
        }
        if (createdByTheJar && Files.notExists(folder)) {
            return new Status("will-be-created", "Will be created");
        }
        return new Status("no-such-folder", "No such folder");
    }

    /** The status of a code set's folder {@code relative} to {@code corpusRoot}: read under the corpus root, never above it. */
    static Status statusOfCodeSetFolder(String corpusRoot, String relative) {
        if (relative.isBlank()) {
            return new Status("not-chosen", "Not chosen");
        }
        Under under = under(corpusRoot, relative.stripTrailing());
        return under.standing() == Standing.FOUND ? found(under.folder())
                : new Status("no-such-folder", "No such folder");
    }

    private static Status found(Path folder) {
        return usable(folder.toString()).map(bytes -> new Status("found", "Found, " + gib(bytes) + " free"))
                .orElse(new Status("found", "Found"));
    }

    /** {@code <p id="status-<forName>">} holding {@code status}. */
    static String statusElement(String forName, Status status) {
        return "<p class=\"status\" id=\"status-" + ControlPanelPage.escape(forName) + "\" data-status=\""
                + status.attribute() + "\">" + ControlPanelPage.escape(status.words()) + "</p>\n";
    }

    /** {@code bytes} in GiB to one decimal, as every page shows free space (ADR-331 §6). */
    static String gib(long bytes) {
        return String.format(Locale.ROOT, "%.1f GiB", bytes / BYTES_IN_A_GIB);
    }

    /** The usable bytes of the drive of {@code folder}, measured at its nearest ancestor that exists (ADR-302 §6). */
    static Optional<Long> usable(String folder) {
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

    /** The free space of the working directory's drive in words, as the tile shows it; "not chosen" where it is not named or its drive cannot be measured. */
    static String freeSpaceOf(String workingDirectory) {
        return usable(workingDirectory).map(FolderPages::gib).orElse("not chosen");
    }

    // ---- Browse

    /** The field a pressed Browse button names, empty where none of the form's fields is one. */
    static Optional<Target> browsed(Map<String, String> fields) {
        for (String name : fields.keySet()) {
            if (name.startsWith("browse-")) {
                Optional<Target> target = Target.named(name.substring("browse-".length()));
                if (target.isPresent()) {
                    return target;
                }
            }
        }
        return Optional.empty();
    }

    /** The address of the folder page for {@code target}, at {@code path}. */
    static String addressOf(Target target, String path) {
        return "/folders?for=" + URLEncoder.encode(target.forName, StandardCharsets.UTF_8) + "&path="
                + URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    /** What {@code fields} hold but for the Browse buttons, which are not the form's. */
    static Map<String, String> withoutBrowse(Map<String, String> fields) {
        Map<String, String> kept = new LinkedHashMap<>(fields);
        kept.keySet().removeIf(name -> name.startsWith("browse-"));
        return kept;
    }

    /** As {@link #withoutBrowse(Map)}, with the trailing white space of the control {@code field} stripped (ADR-331 §6). */
    static Map<String, String> withoutBrowse(Map<String, String> fields, String field) {
        Map<String, String> kept = withoutBrowse(fields);
        kept.computeIfPresent(field, (name, value) -> value.stripTrailing());
        return kept;
    }

    // ---- /folders

    private void folders(HttpExchange exchange) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            choose(exchange);
            return;
        }
        Map<String, String> query;
        try {
            query = query(exchange.getRequestURI().getRawQuery());
        } catch (IllegalArgumentException malformed) {
            Exchanges.notFound(exchange, NOT_A_FIELD);
            return;
        }
        if (!query.containsKey("for")) {
            Exchanges.redirect(exchange, "/");
            return;
        }
        Optional<Target> target = Target.named(query.get("for"));
        if (target.isEmpty()) {
            Exchanges.notFound(exchange, NOT_A_FIELD);
            return;
        }
        Exchanges.page(exchange, ControlPanelPage.render(exchange, "Folder", 0,
                page(target.get(), query.getOrDefault("path", ""))));
    }

    private static final String NOT_A_FIELD = "the folder page chooses a folder for none of the fields it was asked for";

    private static Map<String, String> query(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            String name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            query.putIfAbsent(name, value);
        }
        return query;
    }

    /** Use this folder: puts the path in the field of the closed list, in memory, and returns to the page it came from. */
    private void choose(HttpExchange exchange) throws IOException {
        Map<String, String> fields = Exchanges.form(exchange);
        if (!fields.containsKey("for")) {
            Exchanges.redirect(exchange, "/");
            return;
        }
        Optional<Target> named = Target.named(fields.get("for"));
        if (named.isEmpty()) {
            Exchanges.notFound(exchange, NOT_A_FIELD);
            return;
        }
        Target target = named.get();
        String path = fields.getOrDefault("path", "");
        if (target == Target.NEW_FOLDER) {
            Under under = under(state.values().corpusRoot(), path);
            if (under.standing() != Standing.FOUND) {
                state.say(target.back, switch (under.standing()) {
                    case NO_CORPUS_ROOT -> "the folder is not chosen: the corpus root is not named, on the launch page";
                    case OUTSIDE -> "the folder is not chosen: a code set's folder lies under the corpus root, and"
                            + " the one named does not";
                    default -> "the folder is not chosen: there is no such folder under the corpus root";
                });
                Exchanges.redirect(exchange, target.back);
                return;
            }
            path = under.relative();
        }
        if (target.form.equals("launch")) {
            LaunchValues held = state.values();
            state.hold(new LaunchValues(
                    target == Target.CORPUS_ROOT ? path : held.corpusRoot(),
                    target == Target.WORKING_DIRECTORY ? path : held.workingDirectory(),
                    target == Target.DELIVERABLE_ROOT ? path : held.deliverableRoot(), held.doclingImage(),
                    held.doclingBaseUrl(), held.ollamaBaseUrl(), held.labelModel(), held.lastSeedFolder()));
        } else {
            state.holdField(target.form, target.field, path);
        }
        Exchanges.redirect(exchange, target.back);
    }

    private String page(Target target, String path) {
        StringBuilder body = new StringBuilder("<section class=\"card\">\n");
        String back = "<p><a id=\"folder-back\" href=\"" + ControlPanelPage.escape(target.back) + "\">Back</a></p>\n";
        if (target == Target.NEW_FOLDER) {
            codeSetListing(body, target, path);
        } else {
            listing(body, target, path);
        }
        return body.append(back).append("</section>").toString();
    }

    private static void listing(StringBuilder body, Target target, String path) {
        if (path.isBlank()) {
            heading(body, "roots", "", "Choose a drive or a folder to open.");
            roots(body, target, rootDirectories());
            return;
        }
        Path folder;
        try {
            folder = Path.of(path).toAbsolutePath().normalize();
        } catch (RuntimeException cannotBeHeld) {
            heading(body, "no-such-folder", path, "No such folder.");
            roots(body, target, rootDirectories());
            return;
        }
        roots(body, target, rootDirectories());
        String shown = folder.toString();
        if (!Files.isDirectory(folder)) {
            heading(body, "no-such-folder", shown, "No such folder.");
            up(body, target, folder.getParent() == null ? null : folder.getParent().toString());
            return;
        }
        Optional<List<String>> names = subfolders(folder);
        if (names.isEmpty()) {
            heading(body, "cannot-be-listed", shown, "This folder cannot be read.");
            up(body, target, folder.getParent() == null ? null : folder.getParent().toString());
            return;
        }
        heading(body, "listed", shown, "Folders in " + shown);
        up(body, target, folder.getParent() == null ? null : folder.getParent().toString());
        List<String> links = new ArrayList<>();
        for (String name : names.get()) {
            links.add(folder.resolve(name).toString());
        }
        list(body, target, names.get(), links);
        use(body, target, shown);
    }

    private void codeSetListing(StringBuilder body, Target target, String path) {
        String corpusRoot = state.values().corpusRoot();
        Under under = under(corpusRoot, path);
        if (under.standing() == Standing.NO_CORPUS_ROOT) {
            heading(body, "no-corpus-root", "", "The corpus root is not named, on the launch page.");
            body.append("<ul id=\"folder-roots\"></ul>\n");
            return;
        }
        body.append("<ul id=\"folder-roots\"><li><a href=\"").append(ControlPanelPage.escape(addressOf(target, "")))
                .append("\">").append(ControlPanelPage.escape(corpusRoot.stripTrailing())).append("</a></li></ul>\n");
        if (under.standing() == Standing.OUTSIDE) {
            heading(body, "outside-the-corpus-root", "", "A code set's folder lies under the corpus root, and this one does not.");
            return;
        }
        if (under.standing() == Standing.NO_SUCH_FOLDER) {
            heading(body, "no-such-folder", path, "No such folder under the corpus root.");
            up(body, target, under.relative().isEmpty() ? null : parentOf(under.relative()));
            return;
        }
        Optional<List<String>> names = subfolders(under.folder());
        String relative = under.relative();
        if (names.isEmpty()) {
            heading(body, "cannot-be-listed", relative, "This folder cannot be read.");
            up(body, target, relative.isEmpty() ? null : parentOf(relative));
            return;
        }
        heading(body, "listed", relative, relative.isEmpty() ? "Folders in the corpus root" : "Folders in " + relative);
        up(body, target, relative.isEmpty() ? null : parentOf(relative));
        List<String> links = new ArrayList<>();
        for (String name : names.get()) {
            links.add(relative.isEmpty() ? name : relative + "/" + name);
        }
        list(body, target, names.get(), links);
        use(body, target, relative);
    }

    private static String parentOf(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? "" : relative.substring(0, slash);
    }

    private static void heading(StringBuilder body, String listing, String dataPath, String words) {
        body.append("<p id=\"folder-listing\" data-listing=\"").append(listing).append("\" data-path=\"")
                .append(ControlPanelPage.escape(dataPath)).append("\">").append(ControlPanelPage.escape(words))
                .append("</p>\n");
    }

    private static void roots(StringBuilder body, Target target, List<String> roots) {
        body.append("<ul id=\"folder-roots\">");
        for (String root : roots) {
            body.append("<li><a href=\"").append(ControlPanelPage.escape(addressOf(target, root))).append("\">")
                    .append(ControlPanelPage.escape(root)).append("</a></li>");
        }
        body.append("</ul>\n");
    }

    private static void up(StringBuilder body, Target target, String parent) {
        if (parent != null) {
            body.append("<p><a id=\"folder-up\" href=\"").append(ControlPanelPage.escape(addressOf(target, parent)))
                    .append("\">Up</a></p>\n");
        }
    }

    private static void list(StringBuilder body, Target target, List<String> names, List<String> paths) {
        body.append("<ul id=\"folder-list\">");
        for (int i = 0; i < names.size(); i++) {
            body.append("<li><a href=\"").append(ControlPanelPage.escape(addressOf(target, paths.get(i)))).append("\">")
                    .append(ControlPanelPage.escape(names.get(i))).append("</a></li>");
        }
        body.append("</ul>\n");
    }

    private static void use(StringBuilder body, Target target, String path) {
        body.append("<form name=\"use-folder\" method=\"post\" action=\"/folders\">\n"
                + "<input type=\"hidden\" name=\"for\" value=\"").append(ControlPanelPage.escape(target.forName))
                .append("\">\n<input type=\"hidden\" name=\"path\" value=\"").append(ControlPanelPage.escape(path))
                .append("\">\n").append(ControlPanelPage.button("use", "Use this folder", false)).append("\n</form>\n");
    }

    private static List<String> rootDirectories() {
        List<String> roots = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            roots.add(root.toString());
        }
        return roots;
    }

    /** The names of the folders in {@code folder}, in name order without regard to case; empty where it cannot be read. */
    private static Optional<List<String>> subfolders(Path folder) {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    names.add(entry.getFileName().toString());
                }
            }
        } catch (IOException | RuntimeException cannotBeListed) {
            return Optional.empty();
        }
        names.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo));
        return Optional.of(names);
    }

    /** Whether {@code relative} cannot be a folder under the corpus root by its text: a backslash, a leading {@code /}, a drive, or a {@code ..} element. */
    static boolean climbs(String relative) {
        if (relative.contains("\\") || relative.startsWith("/") || relative.matches("^[A-Za-z]:.*")) {
            return true;
        }
        for (String element : relative.split("/")) {
            if (element.equals("..")) {
                return true;
            }
        }
        return false;
    }

    /**
     * A code set's folder {@code relative} to the corpus root (ADR-331 §5): written with {@code /}, not absolute, with
     * no {@code ..} element and no backslash, and whose real path, links followed, lies under the corpus root's.
     */
    private static Under under(String corpusRoot, String relative) {
        if (corpusRoot == null || corpusRoot.isBlank()) {
            return new Under(Standing.NO_CORPUS_ROOT, null, "");
        }
        String named = relative;
        if (climbs(named)) {
            return new Under(Standing.OUTSIDE, null, "");
        }
        try {
            Path root = Path.of(corpusRoot.stripTrailing()).toAbsolutePath().normalize();
            Path folder = named.isEmpty() ? root : root.resolve(named).normalize();
            if (!folder.startsWith(root)) {
                return new Under(Standing.OUTSIDE, null, "");
            }
            String written = root.relativize(folder).toString().replace(root.getFileSystem().getSeparator(), "/");
            if (!Files.isDirectory(folder)) {
                return new Under(Standing.NO_SUCH_FOLDER, null, written);
            }
            if (!folder.toRealPath().startsWith(root.toRealPath())) {
                return new Under(Standing.OUTSIDE, null, "");
            }
            return new Under(Standing.FOUND, folder, written);
        } catch (IOException | RuntimeException cannotBeHeld) {
            return new Under(Standing.NO_SUCH_FOLDER, null, "");
        }
    }
}
