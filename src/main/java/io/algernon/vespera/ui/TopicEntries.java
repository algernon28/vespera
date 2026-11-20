package io.algernon.vespera.ui;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The rule of ADR-429 §2 for a name made plain, and §3's reasons for a line of {@code topics} the jar would refuse,
 * held in a copy of the control panel's own, since it links no class of the jar (ADR-285 §3). {@code
 * TheWindowAndTheJarMakeANamePlainAlikeTest} holds this copy and the jar's to the same answers.
 */
final class TopicEntries {

    private static final Pattern A_LINE_END = Pattern.compile("\r\n|\r|\n");

    private static final Pattern A_COMBINING_MARK = Pattern.compile("\\p{M}");

    private TopicEntries() {
    }

    /**
     * A name made plain: the blanks around it removed, decomposed, its combining marks removed, lower-cased under
     * {@link Locale#ROOT}, and composed again. Blanks within the name are kept.
     */
    static String identityOf(String name) {
        String decomposed = Normalizer.normalize(name.strip(), Normalizer.Form.NFD);
        String plain = A_COMBINING_MARK.matcher(decomposed).replaceAll("");
        return Normalizer.normalize(plain.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    /**
     * The entries of a value, one a line: the value with its blanks at its start and end removed, split at a line feed,
     * a carriage return and a line feed, or a carriage return alone, each as written. None for a value blank once its
     * blanks are removed.
     */
    static List<String> linesOf(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(A_LINE_END.split(value.strip(), -1));
    }

    /**
     * The reason the jar would refuse each of {@code lines}, at the same index: empty for a line that is a name, §3's
     * words for one that is blank or the topic of an earlier line again.
     */
    static List<Optional<String>> refusalsOf(List<String> lines) {
        List<Optional<String>> reasons = new ArrayList<>();
        Map<String, Integer> lineOf = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String name = lines.get(i).strip();
            if (name.isEmpty()) {
                reasons.add(Optional.of("line " + (i + 1) + " is blank, and each line names one topic. Remove it."));
                continue;
            }
            Integer earlier = lineOf.putIfAbsent(identityOf(name), i + 1);
            reasons.add(earlier == null ? Optional.empty()
                    : Optional.of("line " + (i + 1) + ", \"" + lines.get(i) + "\", is the topic \""
                            + lines.get(earlier - 1).strip() + "\" of line " + earlier + " again, two names being one"
                            + " topic where they differ only in case, accents or the spaces around them. Remove one"
                            + " of them."));
        }
        return reasons;
    }

    /** Whether the jar would refuse {@code value}: it holds a blank entry or a duplicate. */
    static boolean isRefused(String value) {
        return refusalsOf(linesOf(value)).stream().anyMatch(Optional::isPresent);
    }
}
