package io.algernon.controlpanel;

import io.algernon.controlpanel.YamlLines.Line;
import io.algernon.controlpanel.YamlLines.NotInFormException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Writing the names of code sets (ADR-295 §7, in ADR-284 §9's shape): {@code code-set-names.yaml} as the whole list
 * of folders and names the operator left, each scalar double-quoted, and the file read back only in that shape, plain
 * or single-quoted by hand as well. A list or file {@code CodeSetNames} would refuse is refused here, with its reason
 * in its words. A refusal is an {@link IllegalArgumentException} whose message is the one line.
 */
public final class CodeSetRenames {

    public static final String FILE_NAME = "code-set-names.yaml";

    /** The name the operator gave the code set below {@code folder}, {@code ""} for the root's set. */
    public record Rename(String folder, String name) {
    }

    private static final String NOT_IN_FORM =
            FILE_NAME + " is not in the form the jar writes it, so the control panel does not change it";

    private static final String FOLDER_OPENING = "- folder:";

    private static final String NAME_OPENING = "  name:";

    private CodeSetRenames() {
    }

    /** The renames of the file in the order it holds them; none for a file that is absent or holds white space alone. */
    public static List<Rename> read(Optional<String> text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<Rename> renames;
        try {
            renames = readForm(YamlLines.split(text.get()));
        } catch (NotInFormException other) {
            throw new IllegalArgumentException(NOT_IN_FORM);
        }
        if (reasonToRefuse(renames).isPresent()) {
            throw new IllegalArgumentException(NOT_IN_FORM);
        }
        return renames;
    }

    /** §7's text of the file holding {@code renames} in their order. */
    public static String render(List<Rename> renames) {
        Optional<String> reason = reasonToRefuse(renames);
        if (reason.isPresent()) {
            throw new IllegalArgumentException(FILE_NAME + " is not saved: " + reason.get());
        }
        if (renames.isEmpty()) {
            return "--- []\n";
        }
        StringBuilder text = new StringBuilder("---\n");
        for (Rename rename : renames) {
            text.append(FOLDER_OPENING).append(' ').append(YamlScalars.write(rename.folder())).append('\n')
                    .append(NAME_OPENING).append(' ').append(YamlScalars.write(rename.name())).append('\n');
        }
        return text.toString();
    }

    /** The reason {@code CodeSetNames} gives for a list it refuses, in its words. */
    private static Optional<String> reasonToRefuse(List<Rename> renames) {
        Set<String> folders = new HashSet<>();
        for (Rename rename : renames) {
            String folder = rename.folder();
            if (!isFolder(folder)) {
                return Optional.of("the folder \"" + folder + "\" is not written relative to the corpus root, with /"
                        + " between its elements, none empty, and neither beginning nor ending with /");
            }
            if (rename.name().isBlank()) {
                return Optional.of("the name for the folder \"" + folder + "\" is blank");
            }
            if (!folders.add(folder)) {
                return Optional.of("the folder \"" + folder + "\" is given twice");
            }
        }
        return Optional.empty();
    }

    private static boolean isFolder(String folder) {
        return folder.isEmpty() || (folder.indexOf('\\') < 0 && !folder.startsWith("/") && !folder.endsWith("/")
                && !folder.contains("//"));
    }

    private static List<Rename> readForm(List<Line> all) {
        List<Line> lines = new ArrayList<>(all);
        List<Rename> renames = new ArrayList<>();
        int i = skipBlanks(lines, 0);
        if (i < lines.size() && lines.get(i).text().equals("--- []")) {
            if (skipBlanks(lines, i + 1) < lines.size()) {
                throw new NotInFormException();
            }
            return renames;
        }
        if (i < lines.size() && lines.get(i).text().equals("---")) {
            i = skipBlanks(lines, i + 1);
        }
        while (i < lines.size()) {
            String line = lines.get(i).text();
            if (!line.startsWith(FOLDER_OPENING)) {
                throw new NotInFormException();
            }
            YamlScalars.Scalar folder = text(YamlScalars.read(lines, i, line.substring(FOLDER_OPENING.length()), 2));
            i = skipBlanks(lines, folder.lastLine() + 1);
            if (i >= lines.size() || !lines.get(i).text().startsWith(NAME_OPENING)) {
                throw new NotInFormException();
            }
            YamlScalars.Scalar name = text(
                    YamlScalars.read(lines, i, lines.get(i).text().substring(NAME_OPENING.length()), 4));
            renames.add(new Rename(folder.text(), name.text()));
            i = skipBlanks(lines, name.lastLine() + 1);
        }
        return renames;
    }

    /** The scalar, once a plain one that Jackson's JSON schema reads as no text is refused. */
    private static YamlScalars.Scalar text(YamlScalars.Scalar scalar) {
        if (scalar.plain() && YamlScalars.readsAsNoText(scalar.text())) {
            throw new NotInFormException();
        }
        return scalar;
    }

    private static int skipBlanks(List<Line> lines, int from) {
        int i = from;
        while (i < lines.size() && YamlLines.blank(lines.get(i).text())) {
            i++;
        }
        return i;
    }
}
