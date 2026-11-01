package io.algernon.controlpanel;

import io.algernon.controlpanel.YamlLines.Line;
import io.algernon.controlpanel.YamlLines.NotInFormException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What can be made of a {@code profile.yaml} that is not in the form {@link ProfileEdit} reads (ADR-330 §6): the first
 * line that is not in it, and, where nothing else is wrong, the three changes that put it there and the text they make.
 * The three are a comment removed, whether a whole line's, one after a key or one after a scalar on its line, a
 * measurement's lines among them; a scalar of {@code value} or {@code provenance} written as ADR-295 §3 writes one; and
 * a plain or single-quoted scalar over several lines joined with one space, as YAML folds them.
 *
 * <p>Every other byte is kept, line ends included, and a value is never changed. What the three cannot put in form is
 * left to the operator: a key the profile does not hold, a key twice, a flow mapping, a block scalar, a tab in an
 * indent, a key written flat. Whether the repaired text is in form is decided by the reader that refuses the original,
 * so a repair offered is one the writes of ADR-295 will accept. {@link ProfileEdit#rewrite} is not changed.
 */
final class ProfileRepair {

    /** One change the repair makes: the line, from 1, it starts on, and what it does in words. */
    record Change(int line, String words) {
    }

    /**
     * What a file not in form comes to.
     *
     * @param firstLine the first line, from 1, that is not in the form
     * @param repairable whether the three changes alone put it in form
     * @param changes what the repair would do, in the file's order; empty where it cannot be made
     * @param repaired the text the repair makes; empty where it cannot be made
     */
    record Analysis(int firstLine, boolean repairable, List<Change> changes, String repaired) {
    }

    private static final Pattern A_KEY_WITH_A_COMMENT = Pattern.compile("(\\w+):[ \\t]+#.*");

    private ProfileRepair() {
    }

    /** The analysis of {@code text}, empty where it is in the form already. */
    static Optional<Analysis> analyse(String text) {
        int firstLine;
        try {
            ProfileEdit.requireForm(text);
            return Optional.empty();
        } catch (NotInFormException notInForm) {
            firstLine = Math.max(1, notInForm.line());
        }
        List<Line> lines = YamlLines.split(text);
        List<Line> out = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        boolean inMeasurement = false;
        int i = 0;
        while (i < lines.size()) {
            Line current = lines.get(i);
            String line = current.text();
            int indent = YamlLines.indentOf(line);
            String body = line.substring(indent);
            if (!YamlLines.blank(line) && body.startsWith("#")) {
                changes.add(new Change(i + 1, "removes the comment that is this whole line"));
                i++;
                continue;
            }
            if (!YamlLines.blank(line) && indent < 4) {
                inMeasurement = false;
            }
            if (YamlLines.blank(line)) {
                out.add(current);
                i++;
            } else if (indent == 0) {
                Matcher keyWithComment = A_KEY_WITH_A_COMMENT.matcher(body);
                if (keyWithComment.matches()) {
                    out.add(new Line(keyWithComment.group(1) + ":", current.eol()));
                    changes.add(new Change(i + 1, "removes the comment after the key"));
                } else {
                    out.add(current);
                }
                i++;
            } else if (indent == 2 && (body.startsWith("value:") || body.startsWith("provenance:"))) {
                i = repairEntry(lines, i, out, changes);
            } else if (indent == 2 && body.startsWith("measurement:")) {
                String after = body.substring("measurement:".length());
                String kept = withoutComment(after);
                inMeasurement = YamlLines.blank(kept);
                if (kept.length() != after.length()) {
                    out.add(new Line("  measurement:" + (YamlLines.blank(kept) ? "" : kept.stripTrailing()),
                            current.eol()));
                    changes.add(new Change(i + 1, "removes the comment after the measurement"));
                } else {
                    out.add(current);
                }
                i++;
            } else if (indent >= 4 && inMeasurement && body.indexOf(':') > 0) {
                int colon = body.indexOf(':');
                Optional<Tolerant> scalar = tolerant(body.substring(colon + 1));
                if (scalar.isPresent() && scalar.get().hadAComment()) {
                    out.add(new Line(line.substring(0, indent + colon + 1) + scalar.get().written(), current.eol()));
                    changes.add(new Change(i + 1, "removes the comment after a line of the measurement"));
                } else {
                    out.add(current);
                }
                i++;
            } else {
                out.add(current);
                i++;
            }
        }
        String repaired = YamlLines.join(out);
        boolean repairable = !changes.isEmpty();
        if (repairable) {
            try {
                ProfileEdit.requireForm(repaired);
            } catch (NotInFormException stillNotInForm) {
                repairable = false;
            }
        }
        return Optional.of(repairable ? new Analysis(firstLine, true, List.copyOf(changes), repaired)
                : new Analysis(firstLine, false, List.of(), ""));
    }

    /**
     * The {@code value} or {@code provenance} entry that opens at line {@code index}, put on one line without its
     * comment where it has either to lose. Returns the index of the line after the entry.
     */
    private static int repairEntry(List<Line> lines, int index, List<Line> out, List<Change> changes) {
        Line first = lines.get(index);
        String text = first.text();
        int colon = text.indexOf(':');
        String prefix = text.substring(0, colon + 1);
        String after = text.substring(colon + 1);

        Optional<Tolerant> scalar = tolerant(after);
        if (scalar.isPresent() && scalar.get().hadAComment()) {
            out.add(new Line(prefix + scalar.get().written(), first.eol()));
            changes.add(new Change(index + 1, "removes the comment after the " + prefix.strip().replace(":", "")));
            return index + 1;
        }
        Optional<Folded> folded = foldedFrom(lines, index, after);
        if (folded.isPresent()) {
            Line last = lines.get(folded.get().lastLine());
            out.add(new Line(prefix + folded.get().written(), last.eol()));
            changes.add(new Change(index + 1, "joins the " + prefix.strip().replace(":", "") + " over lines "
                    + (index + 1) + " to " + (folded.get().lastLine() + 1) + " into one line"));
            return folded.get().lastLine() + 1;
        }
        try {
            int last = YamlScalars.read(lines, index, after, 4).lastLine();
            for (int n = index; n <= last; n++) {
                out.add(lines.get(n));
            }
            return last + 1;
        } catch (NotInFormException other) {
            out.add(first);
            return index + 1;
        }
    }

    /** A scalar over several lines, folded onto one: how it is written, and the last line it ran over. */
    private record Folded(String written, int lastLine) {
    }

    /**
     * The plain or single-quoted scalar that opens on line {@code index} and runs on over more-indented lines, none of
     * them blank or a comment, folded with one space; empty where it does not run on.
     */
    private static Optional<Folded> foldedFrom(List<Line> lines, int index, String after) {
        int start = YamlLines.indentOf(after);
        if (start == 0 || start >= after.length()) {
            return Optional.empty();
        }
        String opening = after.substring(start);
        char first = opening.charAt(0);
        if (first == '"') {
            return Optional.empty();
        }
        StringBuilder joined = new StringBuilder(opening.stripTrailing());
        int n = index;
        if (first == '\'') {
            while (closingQuote(joined.toString()) < 0) {
                if (n + 1 >= lines.size() || !continues(lines.get(n + 1).text())) {
                    return Optional.empty();
                }
                joined.append(' ').append(lines.get(n + 1).text().strip());
                n++;
            }
        } else {
            while (n + 1 < lines.size() && continues(lines.get(n + 1).text())) {
                String continued = lines.get(n + 1).text().strip();
                int comment = commentAt(continued);
                String kept = comment < 0 ? continued : continued.substring(0, comment).stripTrailing();
                if (kept.contains(": ") || kept.endsWith(":")) {
                    return Optional.empty();
                }
                joined.append(' ').append(kept);
                n++;
                if (comment >= 0) {
                    break;
                }
            }
        }
        if (n == index) {
            return Optional.empty();
        }
        int last = n;
        return tolerant(" " + joined).map(scalar -> new Folded(scalar.written(), last));
    }

    /** Whether the line can continue a scalar of an entry: more indented than the entry, neither blank nor a comment. */
    private static boolean continues(String line) {
        int indent = YamlLines.indentOf(line);
        return !YamlLines.blank(line) && indent > 2 && !line.substring(indent).startsWith("#");
    }

    /** A scalar read off one line, as it is written back. */
    private record Tolerant(String text, boolean plain, boolean hadAComment) {

        /** The scalar as the profile is written: double-quoted as ADR-295 §3 has it, a plain {@code null} kept plain. */
        String written() {
            return plain && text.equals("null") ? " null" : " " + YamlScalars.write(text);
        }
    }

    /**
     * The scalar after a colon on one line, with any comment after it dropped; empty where the line holds none or
     * holds a kind this does not read.
     */
    private static Optional<Tolerant> tolerant(String after) {
        int start = YamlLines.indentOf(after);
        if (start == 0 || start >= after.length()) {
            return Optional.empty();
        }
        String scalar = after.substring(start);
        char first = scalar.charAt(0);
        try {
            if (first == '"' || first == '\'') {
                int end = closingQuote(scalar);
                if (end < 0) {
                    return Optional.empty();
                }
                String quoted = scalar.substring(0, end + 1);
                String rest = scalar.substring(end + 1);
                boolean comment = rest.stripLeading().startsWith("#");
                if (!YamlLines.blank(rest) && !comment) {
                    return Optional.empty();
                }
                String text = YamlScalars.read(List.of(new Line("", "")), 0, " " + quoted, 4).text();
                return Optional.of(new Tolerant(text, false, comment));
            }
            int comment = commentAt(scalar);
            String plain = (comment < 0 ? scalar : scalar.substring(0, comment)).stripTrailing();
            if (plain.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Tolerant(plain, true, comment >= 0));
        } catch (NotInFormException other) {
            return Optional.empty();
        }
    }

    /** The index of the quote closing the quoted scalar that begins {@code scalar}, or -1. */
    private static int closingQuote(String scalar) {
        char quote = scalar.charAt(0);
        int i = 1;
        while (i < scalar.length()) {
            char c = scalar.charAt(i);
            if (quote == '"' && c == '\\') {
                i += 2;
            } else if (c == quote) {
                if (quote == '\'' && i + 1 < scalar.length() && scalar.charAt(i + 1) == '\'') {
                    i += 2;
                } else {
                    return i;
                }
            } else {
                i++;
            }
        }
        return -1;
    }

    /** Where a comment begins in a plain scalar: a {@code #} after a space or a tab; -1 where there is none. */
    private static int commentAt(String plain) {
        for (int i = 1; i < plain.length(); i++) {
            if (plain.charAt(i) == '#' && (plain.charAt(i - 1) == ' ' || plain.charAt(i - 1) == '\t')) {
                return i;
            }
        }
        return -1;
    }

    /** {@code after} up to a comment that begins it or follows a space; the whole of it where there is none. */
    private static String withoutComment(String after) {
        int at = after.stripLeading().startsWith("#") ? YamlLines.indentOf(after) : commentAt(after);
        return at < 0 ? after : after.substring(0, at);
    }
}
