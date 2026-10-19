package io.algernon.controlpanel;

import io.algernon.controlpanel.YamlLines.Line;
import io.algernon.controlpanel.YamlLines.NotInFormException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Writing relevance answers (ADR-295 §6): the {@code relevant} line of entries {@code relevance-labels.yaml} already
 * holds, found by path, and no other byte of the file, in a file read only in the form the jar writes it. A refusal
 * is an {@link IllegalArgumentException} whose message is the one line.
 */
public final class LabelAnswers {

    public static final String FILE_NAME = "relevance-labels.yaml";

    /** One entry of the file: the document's path, and its answer, empty where it is blank. */
    public record Question(String path, Optional<Boolean> relevant) {
    }

    private static final String NOT_IN_FORM =
            FILE_NAME + " is not in the form the jar writes it, so the control panel does not change it";

    private static final Pattern A_NUMBER = Pattern.compile("-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?");

    private static final Set<String> STAMPS = Set.of("generatedUnderRun", "generatedUnderEmbedder",
            "generatedUnderSeedSet");

    private static final String PATH_OPENING = "- path:";

    private LabelAnswers() {
    }

    /** The entries in the file's order, each with its path and answer. */
    public static List<Question> read(String text) {
        return parse(text).entries().stream().map(entry -> new Question(entry.path(), entry.relevant())).toList();
    }

    /** §6's text of the file with the {@code relevant} line of each path in {@code answersByPath} replaced. */
    public static String rewrite(Optional<String> current, Map<String, Optional<Boolean>> answersByPath) {
        if (current.isEmpty()) {
            throw new IllegalArgumentException(
                    FILE_NAME + " is not there, so there is no question to answer; vespera run writes it");
        }
        Parsed parsed = parse(current.get());
        Set<String> asked = new HashSet<>();
        parsed.entries().forEach(entry -> asked.add(entry.path()));
        for (String path : answersByPath.keySet()) {
            if (!asked.contains(path)) {
                throw new IllegalArgumentException(FILE_NAME + " holds no question about " + path);
            }
        }
        List<Line> lines = parsed.lines();
        for (int n = parsed.entries().size() - 1; n >= 0; n--) {
            Entry entry = parsed.entries().get(n);
            Optional<Boolean> answer = answersByPath.get(entry.path());
            if (answer == null) {
                continue;
            }
            String line = "  relevant: " + answer.map(String::valueOf).orElse("null");
            if (entry.relevantLine() >= 0) {
                lines.set(entry.relevantLine(), new Line(line, lines.get(entry.relevantLine()).eol()));
            } else {
                YamlLines.insertAfter(lines, entry.lastLine(), List.of(line));
            }
        }
        return YamlLines.join(lines);
    }

    private record Entry(String path, Optional<Boolean> relevant, int relevantLine, int lastLine) {
    }

    private record Parsed(List<Line> lines, List<Entry> entries) {
    }

    /** §6's form read, or {@link IllegalArgumentException} with §2's line. */
    private static Parsed parse(String text) {
        try {
            return parseForm(text);
        } catch (NotInFormException other) {
            throw new IllegalArgumentException(NOT_IN_FORM);
        }
    }

    private static Parsed parseForm(String text) {
        List<Line> lines = new ArrayList<>(YamlLines.split(text));
        List<Entry> entries = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        Set<String> stampsSeen = new HashSet<>();
        int i = skipBlanks(lines, 0);
        if (i < lines.size() && lines.get(i).text().equals("---")) {
            i = skipBlanks(lines, i + 1);
        }
        boolean documentsSeen = false;
        while (i < lines.size()) {
            String line = lines.get(i).text();
            if (!documentsSeen) {
                int colon = line.indexOf(':');
                String name = colon < 0 ? "" : line.substring(0, colon);
                String afterColon = colon < 0 ? "" : line.substring(colon + 1);
                if (STAMPS.contains(name)) {
                    if (!stampsSeen.add(name)) {
                        throw new NotInFormException();
                    }
                    YamlScalars.Scalar stamp = YamlScalars.read(lines, i, afterColon, 2);
                    if (stamp.plain() && !stamp.text().equals("null")) {
                        throw new NotInFormException();
                    }
                    i = skipBlanks(lines, stamp.lastLine() + 1);
                } else if (name.equals("documents")) {
                    documentsSeen = true;
                    if (!YamlLines.blank(afterColon) && !(afterColon.startsWith(" ") && afterColon.strip().equals("[]"))) {
                        throw new NotInFormException();
                    }
                    i = skipBlanks(lines, i + 1);
                } else {
                    throw new NotInFormException();
                }
                continue;
            }
            if (!line.startsWith(PATH_OPENING)) {
                throw new NotInFormException();
            }
            YamlScalars.Scalar path = YamlScalars.read(lines, i, line.substring(PATH_OPENING.length()), 2);
            if (path.plain() || !paths.add(path.text())) {
                throw new NotInFormException();
            }
            i = entryLines(lines, path.lastLine() + 1, path.text(), entries);
        }
        return new Parsed(lines, entries);
    }

    /** Reads the lines of the entry that follows its path line; returns the index of the line after them. */
    private static int entryLines(List<Line> lines, int from, String path, List<Entry> entries) {
        Set<String> seen = new HashSet<>();
        Optional<Boolean> relevant = Optional.empty();
        int relevantLine = -1;
        int lastLine = from - 1;
        int i = from;
        while (i < lines.size()) {
            String line = lines.get(i).text();
            if (YamlLines.blank(line)) {
                i++;
                continue;
            }
            if (line.startsWith("- ") || YamlLines.indentOf(line) == 0) {
                break;
            }
            if (YamlLines.indentOf(line) != 2 || line.charAt(2) == '\t') {
                throw new NotInFormException();
            }
            String entry = line.substring(2);
            int colon = entry.indexOf(':');
            String name = colon < 0 ? "" : entry.substring(0, colon);
            String afterColon = colon < 0 ? "" : entry.substring(colon + 1);
            if (!seen.add(name)) {
                throw new NotInFormException();
            }
            switch (name) {
                case "score", "band" -> {
                    if (!afterColon.startsWith(" ") || !A_NUMBER.matcher(afterColon.strip()).matches()) {
                        throw new NotInFormException();
                    }
                    lastLine = i;
                }
                case "closestSeed", "labelledBy" -> {
                    YamlScalars.Scalar scalar = YamlScalars.read(lines, i, afterColon, 2);
                    if (scalar.plain() && !scalar.text().equals("null")) {
                        throw new NotInFormException();
                    }
                    i = scalar.lastLine();
                    lastLine = i;
                }
                case "relevant" -> {
                    relevant = switch (afterColon.startsWith(" ") ? afterColon.strip() : "") {
                        case "true" -> Optional.of(true);
                        case "false" -> Optional.of(false);
                        case "null" -> Optional.empty();
                        default -> throw new NotInFormException();
                    };
                    relevantLine = i;
                    lastLine = i;
                }
                default -> throw new NotInFormException();
            }
            i++;
        }
        entries.add(new Entry(path, relevant, relevantLine, lastLine));
        return i;
    }

    private static int skipBlanks(List<Line> lines, int from) {
        int i = from;
        while (i < lines.size() && YamlLines.blank(lines.get(i).text())) {
            i++;
        }
        return i;
    }
}
