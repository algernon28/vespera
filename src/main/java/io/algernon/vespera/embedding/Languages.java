package io.algernon.vespera.embedding;

import com.github.pemistahl.lingua.api.Language;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The languages of a translation set (ADR-271 §2, §9): which segment of a path is a language folder, and a
 * language as stage 2 stored it (Lingua's name) read as its ISO 639-1 code, so that the two can be compared.
 * The closed list is Lingua's languages, the detector stage 2 runs.
 */
public final class Languages {

    /** Two lowercase letters, then optionally {@code -} or {@code _} and a two-letter region or a four-letter script. */
    private static final Pattern FOLDER = Pattern.compile("([a-z]{2})(?:[-_](?:[A-Za-z]{2}|[A-Za-z]{4}))?");

    private static final Set<String> CODES = Language.all().stream()
            .map(language -> language.getIsoCode639_1().toString())
            .collect(Collectors.toUnmodifiableSet());

    private Languages() {}

    /** The language {@code segment} names as a language folder, as its two-letter code, or none. */
    public static Optional<String> ofFolder(String segment) {
        Matcher matcher = FOLDER.matcher(segment);
        if (!matcher.matches() || !CODES.contains(matcher.group(1))) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    /** The code of the language stage 2 stored under Lingua's name, or none where it stored none. */
    public static Optional<String> ofDetected(String stored) {
        if (stored == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Language.valueOf(stored.toUpperCase(Locale.ROOT)))
                    .filter(language -> language != Language.UNKNOWN)
                    .map(language -> language.getIsoCode639_1().toString());
        } catch (IllegalArgumentException notALanguage) {
            return Optional.empty();
        }
    }
}
