package io.algernon.vespera.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code confluence/pages.json} as the publish reads it (ADR-316 §2), by the page list's contract and with no class of
 * {@code deliverable}: the generation run, its marker, and its pages parents first, each with its body and files.
 *
 * @param format the page list's format, {@value #FORMAT}
 * @param generationRun the generation run's id
 * @param marker the run's marker (ADR-310 §1)
 * @param pages the pages, parents first
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PageList(int format, String generationRun, String marker, List<Page> pages) {

    /** The page list's own name in {@code confluence/}. */
    static final String FILE_NAME = "pages.json";

    /** The one format this publish reads. */
    static final int FORMAT = 1;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A page as the list lists it (ADR-316 §2). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(String key, String parent, String title, String body, String bodySha256, List<PageFile> files) {}

    /** A file a page carries, its path from {@code confluence/} (ADR-316 §2). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PageFile(String name, String path, String kind, long size, String sha256) {

        /** A survivor's copy (ADR-313). */
        static final String COPY = "copy";

        /** A picture's file (ADR-314). */
        static final String PICTURE = "picture";

        /** Whether a site refusing it as too large skips it rather than stopping the publish (ADR-313 §3, §5). */
        boolean skippedWhenTooLarge() {
            return COPY.equals(kind) || PICTURE.equals(kind);
        }
    }

    /** What reading the list gave: the list, or the line refusing it. */
    sealed interface Read permits Read.Listed, Read.Refused {
        record Listed(PageList list) implements Read {}

        record Refused(String line) implements Read {}
    }

    /**
     * The page list in {@code confluence}, or why it cannot be published: it stands not, is not JSON, is of another
     * format, names another run, lists a parent after its child, or names a path outside the tree.
     */
    static Read in(Path confluence, String generationRun) {
        Path file = confluence.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return new Read.Refused("vespera publish sent nothing: no Confluence rendering is drawn in this working"
                    + " directory. Run vespera run to the deliverable, and publish again.");
        }
        PageList list;
        try {
            list = JSON.readValue(Files.readAllBytes(file), PageList.class);
        } catch (IOException | JacksonException unreadable) {
            return refused(file, "it cannot be read as the page list");
        }
        if (list.format() != FORMAT) {
            return refused(file, "it is of format " + list.format() + ", and this publish reads format " + FORMAT);
        }
        if (list.pages() == null || list.pages().isEmpty() || list.marker() == null || list.marker().isBlank()) {
            return refused(file, "it lists no page or no marker");
        }
        if (!generationRun.equals(list.generationRun())) {
            return refused(file, "it is the rendering of another generation run than the one the ledger names");
        }
        Optional<String> misshapen = misshapen(confluence, list.pages());
        return misshapen.<Read>map(why -> refused(file, why)).orElseGet(() -> new Read.Listed(list));
    }

    /** Why the pages are not a tree parents first with paths inside the deliverable tree, if they are not. */
    private static Optional<String> misshapen(Path confluence, List<Page> pages) {
        Set<String> listed = new HashSet<>();
        Path tree = confluence.toAbsolutePath().normalize().getParent();
        for (Page page : pages) {
            if (page.key() == null || page.title() == null || page.body() == null || page.bodySha256() == null) {
                return Optional.of("a page lacks its key, title, body or digest");
            }
            if (page.parent() == null ? !listed.isEmpty() : !listed.contains(page.parent())) {
                return Optional.of("the page " + page.key() + " is listed before its parent, or is a second root");
            }
            if (!listed.add(page.key())) {
                return Optional.of("the key " + page.key() + " is listed twice");
            }
            if (!inside(tree, confluence, page.body())) {
                return Optional.of("the body of " + page.key() + " lies outside the deliverable tree");
            }
            for (PageFile file : page.files() == null ? List.<PageFile>of() : page.files()) {
                if (file.name() == null || file.path() == null || file.sha256() == null
                        || !inside(tree, confluence, file.path())) {
                    return Optional.of("a file of " + page.key() + " lacks its name or digest, or lies outside the"
                            + " deliverable tree");
                }
            }
        }
        return Optional.empty();
    }

    private static boolean inside(Path tree, Path confluence, String path) {
        return tree != null && confluence.toAbsolutePath().normalize().resolve(path).normalize().startsWith(tree);
    }

    private static Read refused(Path file, String why) {
        return new Read.Refused("vespera publish sent nothing: the page list " + file + " cannot be published, as "
                + why + ". Run vespera run to draw the deliverable again, and publish again.");
    }

    /** The files a page carries, none where the list gives none. */
    static List<PageFile> filesOf(Page page) {
        return page.files() == null ? List.of() : page.files();
    }
}
