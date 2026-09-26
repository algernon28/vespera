package io.algernon.vespera.profile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * {@code profile.yaml} is not in the shape a profile takes, so it did not load (#321).
 *
 * <p>This is the strict half of ADR-120's split, the one that fails the load: a key nobody knows, a
 * key written flat as {@code seedFolder: /seeds} rather than with its answer nested beneath it, or a
 * file that is not YAML at all. It is not the unreadable state beside it. An unreadable value is one
 * the file carries in the right shape and nothing can act on, and it loads; this is a file that does
 * not, so no key in it has a state yet.
 *
 * <p><b>The message is the whole of what the operator is told</b>, so it is one line, and it says
 * what Jackson's own message does not: which file, which key where the reader got as far as one, and
 * the shape a key takes. It carries no Java type, because the operator wrote YAML and never met one,
 * and nothing about which part of the application asked for the profile first, because that is an
 * accident of start-up order the operator can do nothing with. Jackson's exception stays attached as
 * the cause, for whoever reads a log rather than a terminal.
 */
public class MisshapenProfileException extends RuntimeException {

    /** The shape every key takes, said once so no message can disagree with another about it. */
    private static final String SHAPE = " on a line of its own, with value: and provenance: indented beneath it.";

    MisshapenProfileException(Path file, JacksonException refusal) {
        super(describe(file, refusal), refusal);
    }

    private static String describe(Path file, JacksonException refusal) {
        List<String> path = refusal.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(name -> name != null)
                .toList();
        String key = path.isEmpty() ? null : path.getFirst();
        StringBuilder line = new StringBuilder("vespera cannot load the profile at ").append(file).append(": ");
        line.append(key == null ? "the file" : key);
        if (refusal instanceof UnrecognizedPropertyException unknown) {
            // No line number: Jackson reports an unknown key from where it has read past the key's
            // whole mapping, which was measured to be the line after the file's last one.
            if (path.size() == 1) {
                return line.append(" is not a key the profile has, which are ")
                        .append(names(unknown.getKnownPropertyIds()))
                        .append(". Write each key")
                        .append(SHAPE)
                        .toString();
            }
            line.append(" has ").append(path.getLast()).append(" beneath it, which a profile key does not take.");
        } else if (refusal instanceof DatabindException) {
            line.append(onLine(refusal.getLocation()))
                    .append(key == null ? " is not in the shape a profile takes." : " is not in the shape a profile key takes.");
        } else {
            line.append(onLine(refusal.getLocation()))
                    .append(" is not YAML this tool can read (")
                    .append(inOneLine(refusal.getOriginalMessage()))
                    .append(").");
        }
        return line.append(" Write ")
                .append(key == null ? "each key" : key + ":")
                .append(SHAPE)
                .toString();
    }

    /** Where the reader stopped, as a person counts lines in an editor, or nothing if it does not say. */
    private static String onLine(TokenStreamLocation location) {
        return location == null || location.getLineNr() < 1 ? "" : ", on line " + location.getLineNr() + ",";
    }

    private static String names(Collection<Object> known) {
        return known == null ? "not known" : known.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    /**
     * The parser's own account, kept to the lines that say what went wrong.
     *
     * <p>A YAML parser's message runs to several lines: what it was doing, where, the line quoted back
     * with a caret under the column, then what it found. Each of the where and the quoted lines is
     * indented, and the two sentences that are not are the ones worth keeping; the line number is
     * already in the message this is part of.
     */
    private static String inOneLine(String parserMessage) {
        String reason = parserMessage == null
                ? ""
                : parserMessage.lines()
                        .filter(text -> !text.isBlank() && !Character.isWhitespace(text.charAt(0)))
                        .map(String::strip)
                        .collect(Collectors.joining("; "));
        return reason.isEmpty() ? "the parser gave no reason" : reason;
    }
}
