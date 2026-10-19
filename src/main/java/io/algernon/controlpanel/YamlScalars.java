package io.algernon.controlpanel;

import io.algernon.controlpanel.YamlLines.Line;
import io.algernon.controlpanel.YamlLines.NotInFormException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The three kinds of scalar the control panel reads, each to the text the jar's Jackson reads from it, and the one
 * kind it writes (ADR-295 §3a): double-quoted with YAML's escapes, continued where the jar breaks a long line;
 * single-quoted on one line; plain on one line.
 */
final class YamlScalars {

    /**
     * A scalar read.
     *
     * @param text what it reads to
     * @param plain whether it was written plain, which the caller reads {@code null} and the like from
     * @param lastLine the index of the last line it ran over
     */
    record Scalar(String text, boolean plain, int lastLine) {
    }

    /** Text Jackson's JSON schema reads as a number: whole or decimal, no leading zero, no plus sign. */
    private static final Pattern A_NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]*)?([eE][-+]?[0-9]+)?");

    private static final Pattern NOT_A_NUMBER = Pattern.compile("[-+]?\\.(inf|Inf|INF)|\\.(nan|NaN|NAN)");

    private static final String INDICATORS = ",[]{}#&*!|>'\"%@`";

    private YamlScalars() {
    }

    /**
     * The scalar that follows a colon: {@code afterColon} is the rest of line {@code index} after it, and a
     * double-quoted scalar may run on over lines indented at least {@code continuationIndent} spaces.
     *
     * @throws NotInFormException where it is another kind, or a kind of these written another way
     */
    static Scalar read(List<Line> lines, int index, String afterColon, int continuationIndent) {
        int start = YamlLines.indentOf(afterColon);
        if (start == 0 || start == afterColon.length()) {
            throw new NotInFormException();
        }
        String text = afterColon.substring(start);
        char first = text.charAt(0);
        if (first == '"') {
            return doubleQuoted(lines, index, text, continuationIndent);
        }
        if (first == '\'') {
            return singleQuoted(index, text);
        }
        return plain(index, text);
    }

    /** Whether Jackson's JSON schema reads the plain scalar as null, a boolean or a number, and so as no text. */
    static boolean readsAsNoText(String plain) {
        return plain.equals("null") || plain.equals("true") || plain.equals("false") || A_NUMBER.matcher(plain).matches()
                || NOT_A_NUMBER.matcher(plain).matches();
    }

    /** The scalar written double-quoted on one line, escaped as ADR-295 §3 states. */
    static String write(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> quoted.append("\\\\");
                case '"' -> quoted.append("\\\"");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7F || c == 0x85 || c == 0x2028 || c == 0x2029) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static Scalar doubleQuoted(List<Line> lines, int index, String text, int continuationIndent) {
        StringBuilder value = new StringBuilder();
        int line = index;
        String current = text;
        int i = 1;
        while (true) {
            if (i >= current.length()) {
                throw new NotInFormException();
            }
            char c = current.charAt(i);
            if (c == '"') {
                if (!YamlLines.blank(current.substring(i + 1))) {
                    throw new NotInFormException();
                }
                return new Scalar(value.toString(), false, line);
            }
            if (c != '\\') {
                value.append(c);
                i++;
                continue;
            }
            if (i + 1 == current.length()) {
                line++;
                if (line >= lines.size()) {
                    throw new NotInFormException();
                }
                String next = lines.get(line).text();
                int indent = YamlLines.indentOf(next);
                if (indent < continuationIndent) {
                    throw new NotInFormException();
                }
                current = next.substring(indent);
                i = 0;
                continue;
            }
            i = escape(current, i + 1, value);
        }
    }

    /** Reads the escape whose letter is at {@code at}, appends what it stands for, and returns the next position. */
    private static int escape(String text, int at, StringBuilder value) {
        char letter = text.charAt(at);
        switch (letter) {
            case '0' -> value.append('\0');
            case 'a' -> value.append('\u0007');
            case 'b' -> value.append('\b');
            case 't', '\t' -> value.append('\t');
            case 'n' -> value.append('\n');
            case 'v' -> value.append('\u000B');
            case 'f' -> value.append('\f');
            case 'r' -> value.append('\r');
            case 'e' -> value.append('\u001B');
            case ' ', '"', '/', '\\' -> value.append(letter);
            case 'N' -> value.append('\u0085');
            case '_' -> value.append('\u00A0');
            case 'L' -> value.append('\u2028');
            case 'P' -> value.append('\u2029');
            case 'x' -> {
                return hexadecimal(text, at, 2, value);
            }
            case 'u' -> {
                return hexadecimal(text, at, 4, value);
            }
            case 'U' -> {
                return hexadecimal(text, at, 8, value);
            }
            default -> throw new NotInFormException();
        }
        return at + 1;
    }

    private static int hexadecimal(String text, int letterAt, int digits, StringBuilder value) {
        int end = letterAt + 1 + digits;
        if (end > text.length()) {
            throw new NotInFormException();
        }
        try {
            int codePoint = Integer.parseUnsignedInt(text.substring(letterAt + 1, end), 16);
            if (text.substring(letterAt + 1, end).chars().anyMatch(c -> Character.digit(c, 16) < 0)
                    || !Character.isValidCodePoint(codePoint)) {
                throw new NotInFormException();
            }
            value.appendCodePoint(codePoint);
        } catch (NumberFormatException e) {
            throw new NotInFormException();
        }
        return end;
    }

    private static Scalar singleQuoted(int index, String text) {
        StringBuilder value = new StringBuilder();
        int i = 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != '\'') {
                value.append(c);
                i++;
            } else if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                value.append('\'');
                i += 2;
            } else {
                if (!YamlLines.blank(text.substring(i + 1))) {
                    throw new NotInFormException();
                }
                return new Scalar(value.toString(), false, index);
            }
        }
        throw new NotInFormException();
    }

    private static Scalar plain(int index, String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
            end--;
        }
        String plain = text.substring(0, end);
        char first = plain.charAt(0);
        boolean spaceAfter = plain.length() == 1 || plain.charAt(1) == ' ';
        if (INDICATORS.indexOf(first) >= 0
                || ((first == '-' || first == '?' || first == ':') && spaceAfter)
                || plain.contains(": ") || plain.contains(" #") || plain.endsWith(":")) {
            throw new NotInFormException();
        }
        return new Scalar(plain, true, index);
    }
}
