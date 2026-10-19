package io.algernon.vespera.deliverable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The operator's renames of code sets, read from {@value #FILE_NAME} beside the database (ADR-284 section 9): the
 * one reader of that file, which checks its shape and nothing else. Applying a name to a set, and reporting the
 * entries no set of the run has, are the drawing's.
 *
 * <p>A file that is not a list of entries, each a mapping of exactly the two strings {@code folder} and
 * {@code name}, is refused whole with the file's name in the message, so no name of it is applied. An absent file,
 * or one holding nothing, renames nothing. Nothing here writes the file.
 */
public final class CodeSetNames {

    private CodeSetNames() {}

    /** The file the operator renames sets in, in the working directory. */
    public static final String FILE_NAME = "code-set-names.yaml";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    /**
     * One entry of the file, as written: the set's folder relative to the corpus root, {@code ""} for the root's
     * set, and the name the operator typed.
     */
    public record Entry(String folder, String name) {}

    /**
     * The entries of {@value #FILE_NAME} in {@code workingDirectory}, in the file's order; none where the file is
     * absent or holds nothing.
     *
     * @throws IllegalArgumentException where the file is not a list of folders and names
     * @throws UncheckedIOException where the file is there and cannot be read
     */
    public static List<Entry> read(Path workingDirectory) {
        Path file = workingDirectory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(FILE_NAME + " could not be read", e);
        }
    }

    /**
     * The entries of {@code yaml}, the text of {@value #FILE_NAME}, in its order.
     *
     * @throws IllegalArgumentException where the text is not a list of folders and names
     */
    public static List<Entry> parse(String yaml) {
        JsonNode root;
        try {
            root = YAML.readTree(yaml);
        } catch (RuntimeException unreadable) {
            throw refused("it is not YAML: " + unreadable.getMessage());
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        if (!root.isArray()) {
            throw refused("it is not a list");
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> folders = new HashSet<>();
        for (JsonNode node : root) {
            if (!node.isObject() || node.size() != 2 || !node.has("folder") || !node.has("name")) {
                throw refused("an entry is not a mapping of exactly folder and name");
            }
            JsonNode folder = node.get("folder");
            JsonNode name = node.get("name");
            if (!folder.isString() || !name.isString()) {
                throw refused("an entry's folder and name are not both text");
            }
            if (!isFolder(folder.asString())) {
                throw refused("the folder \"" + folder.asString() + "\" is not written relative to the corpus root,"
                        + " with / between its elements, none empty, and neither beginning nor ending with /");
            }
            if (name.asString().isBlank()) {
                throw refused("the name for the folder \"" + folder.asString() + "\" is blank");
            }
            if (!folders.add(folder.asString())) {
                throw refused("the folder \"" + folder.asString() + "\" is given twice");
            }
            entries.add(new Entry(folder.asString(), name.asString()));
        }
        return List.copyOf(entries);
    }

    /** {@code ""}, or elements none empty joined by {@code /}, with no backslash. */
    private static boolean isFolder(String folder) {
        return folder.isEmpty()
                || (folder.indexOf('\\') < 0 && !folder.startsWith("/") && !folder.endsWith("/") && !folder.contains("//"));
    }

    private static IllegalArgumentException refused(String why) {
        return new IllegalArgumentException(FILE_NAME + " is refused, and none of its names is applied: " + why
                + ". Write it as a list of entries, each with a folder and a name.");
    }
}
