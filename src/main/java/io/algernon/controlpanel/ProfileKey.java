package io.algernon.controlpanel;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The control panel's table of profile keys (ADR-295 §5): one constant for each key {@code profile.yaml} holds, in the
 * order the jar's profile holds them, with its type and the earliest run it enters. A key is named as the file spells
 * it and a run as the jar's stage is named, as text, because the control panel links no class of the jar
 * (ADR-285 §3); a contract test holds both to the jar's. {@code clusterPageByteCeiling} alone enters no run (ADR-303 §9).
 */
public enum ProfileKey {
    SEED_FOLDER("seedFolder", Kind.TEXT, Run.SEED_MEASUREMENT, "your own knowledge of the archive",
            "D:\\archive\\exemplars"),
    DEGENERATE_OUTPUT_CONFIDENCE_FLOOR("degenerateOutputConfidenceFloor", Kind.NUMBER, Run.EXTRACTION,
            "how well the text extraction went", "0.5"),
    DEGENERATE_OUTPUT_WORD_FLOOR("degenerateOutputWordFloor", Kind.NUMBER, Run.EXTRACTION,
            "how many words each kind of file held once its text was extracted, and what each floor would remove",
            "20"),
    BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR("boilerplateDocumentFrequencyFloor", Kind.NUMBER, Run.CONTENT_REDUNDANCY,
            "how many documents share the same passages", "0.4"),
    EMBEDDING_MODEL("embeddingModel", Kind.TEXT, Run.EMBEDDING_SCORING,
            "whichever model you can serve locally, if you want one other than bge-m3:latest", "bge-m3:latest"),
    RELEVANCE_SCORE_FLOOR("relevanceScoreFloor", Kind.NUMBER, Run.EMBEDDING_SCORING,
            "what each possible cut would cost you, in documents", "0.62"),
    ARRANGEMENT_APPROVED("arrangementApproved", Kind.TEXT, Run.GENERATION,
            "whether the groups the tool formed are worth writing over", "auto"),
    GENERATION_MODEL("generationModel", Kind.TEXT, Run.GENERATION,
            "whichever model you can serve locally, if you want a different one", "qwen3:8b"),
    GENERATION_CONTEXT_WINDOW("generationContextWindow", Kind.NUMBER, Run.GENERATION,
            "how much your own machine can read in one go", "32768"),
    CLUSTER_PAGE_BYTE_CEILING("clusterPageByteCeiling", Kind.NUMBER, null,
            "how large each group's page is when written whole, and how many groups pass each size", "10485760"),
    LOG_TIMESTAMP_SHARE_FLOOR("logTimestampShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION,
            "how much of each text file begins with a date or a time", "0.7"),
    EXTRACTION_ATTEMPT("extractionAttempt", Kind.NUMBER, Run.EXTRACTION,
            "whether the files the converter did not answer about are worth asking again", "2"),
    CODE_NAME_ENDINGS("codeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION,
            "which text files, or files of no known kind whose bytes read as text, are code by the ending of their"
                    + " name, kept apart from the documents",
            ".java .sql"),
    OUT_OF_SCOPE_NAME_ENDINGS("outOfScopeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION,
            "which files, of no known kind or text, you know are not documents by the ending of their name",
            ".ok .tmp"),
    TRANSLATION_SIMILARITY_FLOOR("translationSimilarityFloor", Kind.NUMBER, Run.EMBEDDING_SCORING,
            "how alike two documents in different languages must be to be taken for one", "0.85"),
    PREFERRED_LANGUAGES("preferredLanguages", Kind.LANGUAGES, Run.EMBEDDING_SCORING,
            "which language's copy you keep of a document held in several", "it, en"),
    CONFIGURATION_SETTING_SHARE_FLOOR("configurationSettingShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION,
            "how much of each text file is settings, which files open with a program's configuration, and which"
                    + " interface definitions were kept",
            "0.8"),
    DELIVERABLE_LANGUAGE("deliverableLanguage", Kind.TEXT, Run.GENERATION,
            "which language the people reading the pages read: en or it", "");

    /** The type of a key's value, which is how the jar reads it. */
    public enum Kind { TEXT, NUMBER, NAME_ENDINGS, LANGUAGES }

    /** The runs a key can enter, in the cascade's order. */
    public enum Run {
        BYTE_LEVEL_REDUCTION, EXTRACTION, CONTENT_CENSUS, CONTENT_REDUNDANCY, SEED_MEASUREMENT, EMBEDDING_SCORING,
        ARRANGEMENT, GENERATION;

        /** The name the jar mints this run's stage under. */
        public String stageName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * What a change of a key costs (ADR-295 §5).
     *
     * @param runs the key's earliest run and every later one, which the next invocation mints again
     * @param approvalCloses whether the arrangement is among them, so the approval written names one the invocation
     *     no longer arrives at
     * @param labelsRefused whether the label file names the old seed set, so {@code vespera label} refuses it
     */
    public record Cost(List<Run> runs, boolean approvalCloses, boolean labelsRefused) {
    }

    private final String key;
    private final Kind kind;
    private final Run earliestRun;
    private final String hint;
    private final String example;

    ProfileKey(String key, Kind kind, Run earliestRun, String hint, String example) {
        this.key = key;
        this.kind = kind;
        this.earliestRun = earliestRun;
        this.hint = hint;
        this.example = example;
    }

    /**
     * What the value is read from, as the second cell of the README's table of values says it with its code quotes
     * taken out (ADR-331 §8); a test holds it to the row. A page gives it a capital and a stop.
     */
    public String hint() {
        return hint;
    }

    /** An example of a value, none for the one key whose control is a choice; it names no real folder or corpus. */
    public String example() {
        return example;
    }

    /** The name {@code profile.yaml} spells. */
    public String key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    /** The earliest run the key enters, empty for the one key that enters none (ADR-303 §9). */
    public Optional<Run> earliestRun() {
        return Optional.ofNullable(earliestRun);
    }

    public Cost cost() {
        if (earliestRun == null) {
            return new Cost(List.of(), false, false);
        }
        List<Run> all = List.of(Run.values());
        List<Run> runs = all.subList(all.indexOf(earliestRun), all.size());
        return new Cost(runs, runs.contains(Run.ARRANGEMENT), this == SEED_FOLDER);
    }

    /** The key {@code profile.yaml} spells as {@code name}, empty for a name the profile does not have. */
    static Optional<ProfileKey> named(String name) {
        return Arrays.stream(values()).filter(candidate -> candidate.key.equals(name)).findFirst();
    }
}
