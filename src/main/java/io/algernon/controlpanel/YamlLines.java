package io.algernon.controlpanel;

import java.util.ArrayList;
import java.util.List;

/**
 * A file's text as lines that keep their own ends, so a rewrite can keep every byte it does not change, line ends
 * included (ADR-295 §2). The control panel reads YAML in {@code java.base} only, and only in the forms its three
 * files take (ADR-285 §3, ADR-295 §3, §6, §7); {@link NotInFormException} is how a reader says the text is another form.
 */
final class YamlLines {

    /** One line without its end; {@code eol} is {@code "\n"}, {@code "\r\n"} or {@code ""} for a last line with none. */
    record Line(String text, String eol) {
    }

    /** The text is not in a form the control panel reads; the caller says which file, in the line §2 gives. */
    static final class NotInFormException extends RuntimeException {

        NotInFormException() {
            super(null, null, false, false);
        }
    }

    private YamlLines() {
    }

    static List<Line> split(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int feed = text.indexOf('\n', start);
            if (feed < 0) {
                lines.add(new Line(text.substring(start), ""));
                break;
            }
            boolean returned = feed > start && text.charAt(feed - 1) == '\r';
            lines.add(new Line(text.substring(start, returned ? feed - 1 : feed), returned ? "\r\n" : "\n"));
            start = feed + 1;
        }
        return lines;
    }

    static String join(List<Line> lines) {
        StringBuilder text = new StringBuilder();
        for (Line line : lines) {
            text.append(line.text()).append(line.eol());
        }
        return text.toString();
    }

    /** The end the file's first ended line has, or a line feed for a file with none. */
    static String endOf(List<Line> lines) {
        return lines.stream().map(Line::eol).filter(eol -> !eol.isEmpty()).findFirst().orElse("\n");
    }

    /** Whether the line is empty or spaces alone. */
    static boolean blank(String text) {
        return text.chars().allMatch(c -> c == ' ');
    }

    /** The number of spaces the line begins with. */
    static int indentOf(String text) {
        int spaces = 0;
        while (spaces < text.length() && text.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    /**
     * {@code lines} with {@code added} placed after the line at {@code index}, in the file's own line end; the line at
     * {@code index} gets one if it was the file's last and had none, and the last added line has none then.
     */
    static void insertAfter(List<Line> lines, int index, List<String> added) {
        String end = endOf(lines);
        Line at = lines.get(index);
        boolean wasLast = at.eol().isEmpty();
        if (wasLast) {
            lines.set(index, new Line(at.text(), end));
        }
        for (int n = 0; n < added.size(); n++) {
            boolean last = wasLast && n == added.size() - 1;
            lines.add(index + 1 + n, new Line(added.get(n), last ? "" : end));
        }
    }
}
