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
    SEED_FOLDER("seedFolder", Kind.TEXT, Run.SEED_MEASUREMENT),
    DEGENERATE_OUTPUT_CONFIDENCE_FLOOR("degenerateOutputConfidenceFloor", Kind.NUMBER, Run.EXTRACTION),
    DEGENERATE_OUTPUT_WORD_FLOOR("degenerateOutputWordFloor", Kind.NUMBER, Run.EXTRACTION),
    BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR("boilerplateDocumentFrequencyFloor", Kind.NUMBER, Run.CONTENT_REDUNDANCY),
    EMBEDDING_MODEL("embeddingModel", Kind.TEXT, Run.EMBEDDING_SCORING),
    RELEVANCE_SCORE_FLOOR("relevanceScoreFloor", Kind.NUMBER, Run.EMBEDDING_SCORING),
    ARRANGEMENT_APPROVED("arrangementApproved", Kind.TEXT, Run.GENERATION),
    GENERATION_MODEL("generationModel", Kind.TEXT, Run.GENERATION),
    GENERATION_CONTEXT_WINDOW("generationContextWindow", Kind.NUMBER, Run.GENERATION),
    CLUSTER_PAGE_BYTE_CEILING("clusterPageByteCeiling", Kind.NUMBER, null),
    LOG_TIMESTAMP_SHARE_FLOOR("logTimestampShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION),
    EXTRACTION_ATTEMPT("extractionAttempt", Kind.NUMBER, Run.EXTRACTION),
    CODE_NAME_ENDINGS("codeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION),
    OUT_OF_SCOPE_NAME_ENDINGS("outOfScopeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION),
    TRANSLATION_SIMILARITY_FLOOR("translationSimilarityFloor", Kind.NUMBER, Run.EMBEDDING_SCORING),
    PREFERRED_LANGUAGES("preferredLanguages", Kind.LANGUAGES, Run.EMBEDDING_SCORING),
    CONFIGURATION_SETTING_SHARE_FLOOR("configurationSettingShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION),
    DELIVERABLE_LANGUAGE("deliverableLanguage", Kind.TEXT, Run.GENERATION);

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

    ProfileKey(String key, Kind kind, Run earliestRun) {
        this.key = key;
        this.kind = kind;
        this.earliestRun = earliestRun;
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
