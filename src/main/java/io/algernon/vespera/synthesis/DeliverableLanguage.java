package io.algernon.vespera.synthesis;

import java.util.Optional;

/**
 * The language a deliverable is written and drawn in (ADR-321 §1): the two the fixed words of every page have a
 * rendering in, each with its ISO 639-1 code as the profile writes it and a page's {@code lang} carries it, and the
 * English name the question asks for the writing in (§2).
 *
 * <p>{@code synthesis} holds them, and English as the default, as it holds the default window (ADR-108), so that
 * {@code pipeline} reads the profile's key and hands the language over as a plain value and holds no rule. A third
 * language is a record of its own, with a table like ADR-321 §4's.
 */
public enum DeliverableLanguage {
    ENGLISH("en", "English"),
    ITALIAN("it", "Italian");

    /** What an unset or blank key means: the language every page was drawn in before ADR-321. */
    public static final DeliverableLanguage DEFAULT = ENGLISH;

    private final String code;
    private final String englishName;

    DeliverableLanguage(String code, String englishName) {
        this.code = code;
        this.englishName = englishName;
    }

    /** The two-letter code, in lower case. */
    public String code() {
        return code;
    }

    /** The name the question uses for the language, in the English the instruction is written in. */
    public String englishName() {
        return englishName;
    }

    /**
     * The language {@code written} names: its code in any case, the blanks around it ignored; empty for anything
     * else, a blank included.
     */
    public static Optional<DeliverableLanguage> ofCode(String written) {
        if (written == null) {
            return Optional.empty();
        }
        String stripped = written.strip();
        for (DeliverableLanguage language : values()) {
            if (language.code.equalsIgnoreCase(stripped)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
