package io.algernon.controlpanel;

import io.algernon.controlpanel.YamlLines.Line;
import io.algernon.controlpanel.YamlLines.NotInFormException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The folders not relevant (ADR-334 §6): {@code not-relevant-folders.yaml} as the whole list of folders the operator
 * left, each scalar double-quoted, and the file read back only in that shape, plain or single-quoted by hand as well.
 * No stage reads the file and the jar never writes it. A refusal is an {@link IllegalArgumentException} whose message
 * is the one line.
 *
 * <p>A folder is written as occurrences' paths spell one: relative to the corpus root, {@code /} between its elements,
 * none empty, no backslash, no {@code ..} element, neither beginning nor ending with {@code /}; or, below a code set's
 * folder outside the corpus root, absolute as ADR-334 §5.4 spells it. A question lies below a folder listed where the
 * folder's elements are the first of the question's path's, compared without regard to case on Windows.
 */
final class NotRelevantFolders {

    static final String FILE_NAME = "not-relevant-folders.yaml";

    private static final boolean NAMES_IGNORE_CASE =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private static final String NOT_IN_FORM =
            FILE_NAME + " is not in the form the control panel writes it, so the control panel does not change it";

    private static final String FOLDER_OPENING = "- folder:";

    private NotRelevantFolders() {
    }

    /** The folders of the file in the order it holds them; none for a file that is absent or holds white space alone. */
    static List<String> read(Optional<String> text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> folders;
        try {
            folders = readForm(YamlLines.split(text.get()));
        } catch (NotInFormException other) {
            throw new IllegalArgumentException(NOT_IN_FORM);
        }
        Set<String> seen = new HashSet<>();
        for (String folder : folders) {
            if (!isWritten(folder) || !seen.add(folder)) {
                throw new IllegalArgumentException(NOT_IN_FORM);
            }
        }
        return folders;
    }

    /** The text of the file holding {@code folders} in their order. */
    static String render(List<String> folders) {
        if (folders.isEmpty()) {
            return "--- []\n";
        }
        StringBuilder text = new StringBuilder("---\n");
        for (String folder : folders) {
            text.append(FOLDER_OPENING).append(' ').append(YamlScalars.write(folder)).append('\n');
        }
        return text.toString();
    }

    /**
     * Whether {@code folder} is written as a folder not relevant may be: relative to the corpus root with {@code /}
     * between its elements, none empty and none {@code ..} or {@code .}, or absolute as ADR-334 §5.4 spells one.
     */
    static boolean isWritten(String folder) {
        if (folder.isEmpty() || folder.indexOf('\\') >= 0 || folder.endsWith("/") || folder.contains("\u0000")) {
            return false;
        }
        boolean absolute = isAbsolute(folder);
        String below = folder.startsWith("//") ? folder.substring(2) : absolute && folder.startsWith("/")
                ? folder.substring(1) : absolute ? folder.substring(3) : folder;
        if (below.isEmpty() || below.startsWith("/")) {
            return false;
        }
        for (String element : below.split("/", -1)) {
            if (element.isEmpty() || element.equals("..") || element.equals(".")) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code folder} is spelt absolute: beginning with a slash, or with a drive letter, a colon and a slash. */
    static boolean isAbsolute(String folder) {
        return folder.startsWith("/") || (folder.length() >= 3 && Character.isLetter(folder.charAt(0))
                && folder.charAt(0) < 128 && folder.startsWith(":/", 1));
    }

    /** Whether the question about {@code path} lies at or below {@code folder}, compared element by element. */
    static boolean lies(String path, String folder) {
        String[] below = path.split("/", -1);
        String[] above = folder.split("/", -1);
        if (below.length < above.length) {
            return false;
        }
        for (int i = 0; i < above.length; i++) {
            if (!(NAMES_IGNORE_CASE ? below[i].equalsIgnoreCase(above[i]) : below[i].equals(above[i]))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> readForm(List<Line> all) {
        List<Line> lines = new ArrayList<>(all);
        List<String> folders = new ArrayList<>();
        int i = skipBlanks(lines, 0);
        if (i < lines.size() && lines.get(i).text().equals("--- []")) {
            if (skipBlanks(lines, i + 1) < lines.size()) {
                throw new NotInFormException();
            }
            return folders;
        }
        if (i < lines.size() && lines.get(i).text().equals("---")) {
            i = skipBlanks(lines, i + 1);
        }
        while (i < lines.size()) {
            String line = lines.get(i).text();
            if (!line.startsWith(FOLDER_OPENING)) {
                throw new NotInFormException();
            }
            YamlScalars.Scalar folder = YamlScalars.read(lines, i, line.substring(FOLDER_OPENING.length()), 2);
            if (folder.plain() && YamlScalars.readsAsNoText(folder.text())) {
                throw new NotInFormException();
            }
            folders.add(folder.text());
            i = skipBlanks(lines, folder.lastLine() + 1);
        }
        return folders;
    }

    private static int skipBlanks(List<Line> lines, int from) {
        int i = from;
        while (i < lines.size() && YamlLines.blank(lines.get(i).text())) {
            i++;
        }
        return i;
    }
}
