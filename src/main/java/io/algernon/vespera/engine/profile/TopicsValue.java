package io.algernon.vespera.engine.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The profile key whose answer is the topics the operator lists, one name a line (ADR-429 §1): {@code topics}. The
 * string stays one under {@code value}, as every key's does (ADR-061).
 *
 * <p>A line ends at a line feed, a carriage return followed by a line feed, or a carriage return alone. The value with
 * its blanks at its start and end removed is split into lines, each an entry; an entry's name is the line with the
 * blanks around it removed and nothing else changed. Two entries are one topic where their names made plain are equal
 * (§2); a blank entry or a duplicate makes the value {@link Refused}, in §3's line, whole.
 *
 * <p>The reading is computed rather than stored, for the reason {@link NumericValue}'s is (ADR-061).
 *
 * @param value what the profile says, exactly as the operator wrote it
 * @param provenance how the operator arrived at it
 * @param measurement census's pointer at the data that should inform it, or {@code null}
 */
public record TopicsValue(String value, String provenance, Measurement measurement) implements ProfileValue {

    private static final Pattern A_LINE_END = Pattern.compile("\r\n|\r|\n");

    private static final Pattern A_COMBINING_MARK = Pattern.compile("\\p{M}");

    /** A key census has created and nobody has answered. */
    public static TopicsValue unset() {
        return new TopicsValue(null, null, null);
    }

    /**
     * A name made plain (ADR-429 §2): the blanks around it removed, decomposed, its combining marks removed,
     * lower-cased under {@link Locale#ROOT}, and composed again. Blanks within the name are kept.
     */
    public static String identityOf(String name) {
        String decomposed = Normalizer.normalize(name.strip(), Normalizer.Form.NFD);
        String plain = A_COMBINING_MARK.matcher(decomposed).replaceAll("");
        return Normalizer.normalize(plain.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    /** What this key says: nothing, a refusal, or the names. */
    @JsonIgnore
    public Reading reading() {
        if (!isSet()) {
            return new Unset();
        }
        String[] lines = A_LINE_END.split(value().strip(), -1);
        List<String> names = new ArrayList<>();
        Map<String, Integer> lineOfIdentity = new HashMap<>();
        for (int i = 0; i < lines.length; i++) {
            int number = i + 1;
            String name = lines[i].strip();
            if (name.isEmpty()) {
                return new Refused("vespera run refused the topics in profile.yaml: line " + number
                        + " is blank, and each line names one topic. Remove it.");
            }
            Integer earlier = lineOfIdentity.putIfAbsent(identityOf(name), number);
            if (earlier != null) {
                return new Refused("vespera run refused the topics in profile.yaml: line " + number + ", \""
                        + lines[i] + "\", is the topic \"" + names.get(earlier - 1) + "\" of line " + earlier
                        + " again, two names being one topic where they differ only in case, accents or the spaces"
                        + " around them. Remove one of them.");
            }
            names.add(name);
        }
        return new Answered(List.copyOf(names));
    }

    /** Nobody has answered the key, the list is refused, or here are the names. */
    public sealed interface Reading permits Unset, Refused, Answered {}

    /** The key says nothing, and the topics are inferred. */
    public record Unset() implements Reading {}

    /**
     * The list holds a blank entry or a duplicate.
     *
     * @param line the closing line of {@code vespera run}'s refusal, whole (ADR-429 §3)
     */
    public record Refused(String line) implements Reading {}

    /**
     * The names the operator listed.
     *
     * @param names each as written with the blanks around it removed, in the order listed
     */
    public record Answered(List<String> names) implements Reading {}
}
