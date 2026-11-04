package io.algernon.vespera.website;

import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.DeliverableRoot;
import io.algernon.vespera.deliverable.Plugin;
import io.algernon.vespera.ledger.ReplacingMove;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code vespera publish --to website}: refreshes one stable folder, {@code <working directory>/website/}, from the
 * rendering a drawing left, by digest, and ends (ADR-333 §7). It lies in the working directory whatever deliverable root
 * is named, and its name holds no generation run.
 *
 * <p><b>In order</b>, each file through {@code .part} and moved into place: the marker, {@value
 * DeliverableRoot#MARKER_FILE_NAME}; {@value #HELD_FILE_NAME}, holding what the previous publish listed and what the
 * rendering's list lists, so a publish stopped partway still knows what it wrote; every file the rendering's list names
 * whose standing file does not hash to its {@code sha256}, copies first and {@code index.html} last; {@value
 * #HELD_FILE_NAME} holding what the list lists alone; then the removals. A file standing with the right digest is not
 * written. A source that is not at the size and digest its list names stops the delivery, and nothing it holds is
 * written: redraw, and publish again.
 *
 * <p><b>What is removed</b>: a file the previous {@value #HELD_FILE_NAME} listed, the rendering's list no longer lists,
 * and which still hashes to what was listed. One the operator changed stays and is counted. A folder so emptied is
 * removed. Nothing else is touched; a file of the operator's at a name the list names is replaced, which is the one
 * thing of theirs a publish can overwrite.
 *
 * <p>Neither the marker nor {@value #HELD_FILE_NAME} names a path (ADR-333 §7), and no line names a document: a stop
 * names a word of ours and, for a failure of the machine, the type of what was thrown, never its message (ADR-274 §8).
 */
final class WebsiteDelivery {

    /** The website's own list of what its publishes wrote (ADR-333 §7). */
    static final String HELD_FILE_NAME = "vespera-website.json";

    private static final int HELD_FORMAT = 1;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One file a publish wrote, by its path in the website and the digest it was written with. */
    record Held(String path, String sha256) {}

    /** A stop that is ours to word. */
    private static final class StoppedException extends Exception {
        StoppedException(String reason) {
            super(reason, null, false, false);
        }
    }

    private final Path rendering;
    private final Path folder;
    private final ReplacingMove moves = ReplacingMove.ATOMIC;
    private int toWrite;
    private int written;

    private WebsiteDelivery(Path rendering, Path folder) {
        this.rendering = rendering;
        this.folder = folder;
    }

    /** Refreshes {@code <workingDirectory>/website/} from {@code rendering}, the website rendering a drawing left. */
    static Plugin.Delivery deliver(Path rendering, Path workingDirectory) {
        WebsiteDelivery delivery = new WebsiteDelivery(rendering, workingDirectory.resolve(DeliverableRoot.WEBSITE_FOLDER_NAME));
        try {
            return delivery.refresh();
        } catch (StoppedException stop) {
            return delivery.stopped(stop.getMessage());
        } catch (IOException | java.io.UncheckedIOException | tools.jackson.core.JacksonException failure) {
            return delivery.stopped("a file could not be read or written (" + failure.getClass().getSimpleName() + ")");
        }
    }

    private Plugin.Delivery refresh() throws IOException, StoppedException {
        List<ListedFile> listed = readList();
        List<Held> previous = readHeld();
        Files.createDirectories(folder);
        DeliverableRoot.markWebsite(folder, moves);

        List<Held> current = listed.stream().map(file -> new Held(file.path(), file.sha256())).toList();
        Set<Held> both = new LinkedHashSet<>(previous);
        both.addAll(current);
        writeHeld(List.copyOf(both));

        List<ListedFile> missing = new ArrayList<>();
        for (ListedFile file : listed) {
            if (!stands(file)) {
                missing.add(file);
            }
        }
        missing.sort(java.util.Comparator.comparingInt(WebsiteDelivery::order));
        toWrite = missing.size();
        for (ListedFile file : missing) {
            write(file);
            written++;
        }
        writeHeld(current);

        int[] removedAndKept = remove(previous, current);
        int standing = listed.size() - toWrite;
        Path index = folder.resolve(Deliverable.INDEX_FILE_NAME);
        return new Plugin.Delivery(true, "vespera publish refreshed the website at " + folder + ": " + written
                + " files written, " + standing + " standing, " + removedAndKept[0] + " removed, " + removedAndKept[1]
                + " changed by hand and kept. Open " + index + ", or run vespera serve.");
    }

    private Plugin.Delivery stopped(String reason) {
        return new Plugin.Delivery(false, "vespera publish stopped the website: " + reason + ". " + written + " of "
                + toWrite + " files written. Publish again to go on from there.");
    }

    /** Copies first, the index last: a reader who opens the index finds what it links. */
    private static int order(ListedFile file) {
        if (ListedFile.COPY.equals(file.kind())) {
            return 0;
        }
        return Deliverable.INDEX_FILE_NAME.equals(file.path()) ? 2 : 1;
    }

    /** Every file the rendering's list names, each path and source held to lie inside the folder it belongs to. */
    private List<ListedFile> readList() throws StoppedException {
        JsonNode list;
        try {
            list = JSON.readTree(rendering.resolve(ListedFile.LIST_FILE_NAME).toFile());
        } catch (RuntimeException unreadable) {
            throw new StoppedException("the rendering's list could not be read (" + unreadable.getClass().getSimpleName() + ")");
        }
        JsonNode files = list.path("files");
        if (!files.isArray()) {
            throw new StoppedException("the rendering's list names no files");
        }
        Path tree = rendering.toAbsolutePath().normalize().getParent();
        List<ListedFile> listed = new ArrayList<>();
        for (JsonNode node : files.valueStream().toList()) {
            ListedFile file = new ListedFile(
                    node.path("path").asString(""),
                    node.path("source").asString(""),
                    node.path("size").asLong(-1),
                    node.path("sha256").asString(""),
                    node.path("kind").asString(""));
            Path source = rendering.toAbsolutePath().resolve(file.source()).normalize();
            if (!isInside(file.path()) || file.size() < 0 || file.sha256().isEmpty() || file.source().isEmpty()
                    || tree == null || !source.startsWith(tree)) {
                throw new StoppedException("the rendering's list names a file that cannot belong to a website");
            }
            listed.add(file);
        }
        return listed;
    }

    /** Whether {@code path} is relative, {@code /} between its names, and lies inside the folder it is resolved in. */
    private static boolean isInside(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            return false;
        }
        return Stream.of(path.split("/", -1)).noneMatch(name -> name.isEmpty() || name.equals(".") || name.equals(".."));
    }

    /** What the previous publish listed; none where there was none, or where its list cannot be read. */
    private List<Held> readHeld() {
        Path held = folder.resolve(HELD_FILE_NAME);
        if (!Files.isRegularFile(held)) {
            return List.of();
        }
        try {
            List<Held> previous = new ArrayList<>();
            for (JsonNode node : JSON.readTree(held.toFile()).path("files").valueStream().toList()) {
                String path = node.path("path").asString("");
                if (isInside(path)) {
                    previous.add(new Held(path, node.path("sha256").asString("")));
                }
            }
            return previous;
        } catch (RuntimeException unreadable) {
            return List.of();
        }
    }

    private void writeHeld(List<Held> held) throws IOException {
        Path part = folder.resolve(HELD_FILE_NAME + Deliverable.PART_SUFFIX);
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("format", HELD_FORMAT);
        list.put("files", held);
        Files.writeString(part, JSON.writeValueAsString(list) + "\n", StandardCharsets.UTF_8);
        moves.intoPlace(part, folder.resolve(HELD_FILE_NAME));
    }

    /** Whether the file stands in the folder, hashing to the digest the list names. */
    private boolean stands(ListedFile file) throws IOException {
        Path standing = folder.resolve(file.path());
        return Files.isRegularFile(standing) && ListedFile.sha256Of(standing).equals(file.sha256());
    }

    /** Writes one file through its {@code .part}, from a source held to the size and digest the list names. */
    private void write(ListedFile file) throws IOException, StoppedException {
        Path source = rendering.toAbsolutePath().resolve(file.source()).normalize();
        Path target = folder.resolve(file.path());
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + Deliverable.PART_SUFFIX);
        MessageDigest digest = ListedFile.digest();
        long size;
        try (InputStream in = new DigestInputStream(Files.newInputStream(source), digest);
                OutputStream out = Files.newOutputStream(part)) {
            size = in.transferTo(out);
        }
        if (size != file.size() || !HexFormat.of().formatHex(digest.digest()).equals(file.sha256())) {
            Files.deleteIfExists(part);
            throw new StoppedException("a file of the rendering is not at the size and digest its list names, so the rendering"
                    + " has to be drawn again");
        }
        moves.intoPlace(part, target);
    }

    /**
     * Removes what the previous publish listed and the current list does not, where it still hashes to what was listed.
     *
     * @return how many were removed, and how many were kept because the operator changed them
     */
    private int[] remove(List<Held> previous, List<Held> current) throws IOException {
        Set<String> stay = new LinkedHashSet<>();
        current.forEach(held -> stay.add(held.path()));
        Map<String, Set<String>> digests = new LinkedHashMap<>();
        for (Held held : previous) {
            if (!stay.contains(held.path())) {
                digests.computeIfAbsent(held.path(), path -> new LinkedHashSet<>()).add(held.sha256());
            }
        }
        int removed = 0;
        int kept = 0;
        for (Map.Entry<String, Set<String>> dropped : digests.entrySet()) {
            Path file = folder.resolve(dropped.getKey());
            if (!Files.isRegularFile(file)) {
                continue;
            }
            if (isCurrent(file, stay)) {
                continue;
            }
            if (dropped.getValue().contains(ListedFile.sha256Of(file))) {
                Files.delete(file);
                removed++;
                Path above = file.getParent();
                while (!above.equals(folder) && above.startsWith(folder) && isEmpty(above)) {
                    Files.delete(above);
                    above = above.getParent();
                }
            } else {
                kept++;
            }
        }
        return new int[] {removed, kept};
    }

    /** Whether {@code file} is the same file some current path resolves to, as a copy renamed by case alone is. */
    private boolean isCurrent(Path file, Set<String> stay) throws IOException {
        for (String path : stay) {
            Path current = folder.resolve(path);
            if (Files.exists(current) && Files.isSameFile(current, file)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }
}
