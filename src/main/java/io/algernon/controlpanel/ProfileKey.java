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
 * Each key also has its name in plain words and a line saying what it is, which the pages show beside the key itself
 * (ADR-340 §7).
 */
public enum ProfileKey {
    SEED_FOLDER("seedFolder", Kind.TEXT, Run.SEED_MEASUREMENT, "your own knowledge of the archive",
            "D:\\archive\\exemplars", "Example documents",
            "Documents you already know belong in what you keep; everything else is measured against them."),
    DEGENERATE_OUTPUT_CONFIDENCE_FLOOR("degenerateOutputConfidenceFloor", Kind.NUMBER, Run.EXTRACTION,
            "how well the text extraction went", "0.5", "Lowest text quality",
            "A file whose extracted text the converter rates below this, from 0 to 1, is set aside: its text is too"
                    + " poor to use."),
    DEGENERATE_OUTPUT_WORD_FLOOR("degenerateOutputWordFloor", Kind.NUMBER, Run.EXTRACTION,
            "how many words each kind of file held once its text was extracted, and what each floor would remove",
            "20", "Fewest words in a file",
            "A file holding fewer words than this once its text is extracted is set aside."),
    BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR("boilerplateDocumentFrequencyFloor", Kind.NUMBER, Run.CONTENT_REDUNDANCY,
            "how many documents share the same passages", "0.4", "Repeated text",
            "A passage found in at least this share of the documents, from 0 to 1, is taken for template text and not"
                    + " compared."),
    EMBEDDING_MODEL("embeddingModel", Kind.TEXT, Run.EMBEDDING_SCORING,
            "whichever model you can serve locally, if you want one other than hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M",
            "hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M",
            "Comparing model",
            "The local model that turns each text into numbers, so that documents can be compared."),
    RELEVANCE_SCORE_FLOOR("relevanceScoreFloor", Kind.NUMBER, Run.EMBEDDING_SCORING,
            "what each possible cut would cost you, in documents", "0.62", "Relevance cut-off",
            "A document less like your example documents than this is left out."),
    ARRANGEMENT_APPROVED("arrangementApproved", Kind.TEXT, Run.GENERATION,
            "whether the groups the tool formed are worth writing over", "auto", "Approved grouping",
            "The grouping you approve for writing, by its name, or auto to approve every grouping proposed."),
    GENERATION_MODEL("generationModel", Kind.TEXT, Run.GENERATION,
            "whichever model you can serve locally, if you want a different one", "qwen3:8b", "Writing model",
            "The local model that writes the text connecting the documents of each page."),
    GENERATION_CONTEXT_WINDOW("generationContextWindow", Kind.NUMBER, Run.GENERATION,
            "how much your own machine can read in one go", "32768", "Writing model's reading size",
            "How many tokens of a group the writing model reads in one go."),
    CLUSTER_PAGE_BYTE_CEILING("clusterPageByteCeiling", Kind.NUMBER, null,
            "how large each group's page is when written whole, and how many groups pass each size", "10485760",
            "Largest page",
            "A group whose page would be larger than this many bytes is split over several pages."),
    LOG_TIMESTAMP_SHARE_FLOOR("logTimestampShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION,
            "how much of each text file begins with a date or a time", "0.7", "Log detection",
            "A text file in which at least this share of lines begin with a date or a time, from 0 to 1, is taken for a"
                    + " log and left out."),
    EXTRACTION_ATTEMPT("extractionAttempt", Kind.NUMBER, Run.EXTRACTION,
            "whether the files the converter did not answer about are worth asking again", "2",
            "Ask again about failed files",
            "Raise it by one to ask the converter again about the files it did not answer about."),
    CODE_NAME_ENDINGS("codeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION,
            "which text files, or files of no known kind whose bytes read as text, are code by the ending of their"
                    + " name, kept apart from the documents",
            ".java .sql", "Code file endings",
            "File name endings that mark a file as code, kept apart from the documents."),
    OUT_OF_SCOPE_NAME_ENDINGS("outOfScopeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION,
            "which files, of no known kind or text, you know are not documents by the ending of their name",
            ".ok .tmp", "Endings left out",
            "File name endings that mark a file as no document at all, so that it is left out."),
    TRANSLATION_SIMILARITY_FLOOR("translationSimilarityFloor", Kind.NUMBER, Run.EMBEDDING_SCORING,
            "how alike two documents in different languages must be to be taken for one", "0.85",
            "Translation match",
            "How alike, from 0 to 1, two documents in different languages must be to be taken for one."),
    PREFERRED_LANGUAGES("preferredLanguages", Kind.LANGUAGES, Run.EMBEDDING_SCORING,
            "which language's copy you keep of a document held in several", "it, en", "Preferred languages",
            "Which language's copy to keep, first choice first, of a document held in several."),
    CONFIGURATION_SETTING_SHARE_FLOOR("configurationSettingShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION,
            "how much of each text file is settings, which files open with a program's configuration, and which"
                    + " interface definitions were kept",
            "0.8", "Configuration file detection",
            "A text file in which at least this share of lines are a program's settings, from 0 to 1, is taken for a"
                    + " configuration file and left out, unless it lies in a set of code."),
    DELIVERABLE_LANGUAGE("deliverableLanguage", Kind.TEXT, Run.GENERATION,
            "which language the people reading the pages read: en or it", "", "Language of the pages",
            "The language the finished pages are written in, English or Italian."),
    CLUSTER_RESOLUTION("clusterResolution", Kind.NUMBER, Run.EMBEDDING_SCORING,
            "how finely the documents under each exemplar divide into groups", "2.5", "Grouping detail",
            "How finely the documents under each example divide into groups: higher makes more, smaller groups."),
    CODE_SET_FOLDERS("codeSetFolders", Kind.FOLDERS, Run.BYTE_LEVEL_REDUCTION,
            "which folders outside the corpus root hold code that is read with the corpus", "D:\\code\\billing",
            "Code folders elsewhere",
            "Folders of code elsewhere on this machine, read with the documents and kept as code sets of their own."),
    FOLDERS_NOT_WALKED("foldersNotWalked", Kind.CORPUS_FOLDERS, Run.BYTE_LEVEL_REDUCTION,
            "which folders of the corpus hold nothing you want read", "archive/old-flows", "Folders not read",
            "Folders of the documents folder that Vespera never reads. Untick them under Folders to process.");

    /** The type of a key's value, which is how the jar reads it. */
    public enum Kind { TEXT, NUMBER, NAME_ENDINGS, LANGUAGES, FOLDERS, CORPUS_FOLDERS }

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
     * What a change of a key costs (ADR-295 §5, ADR-330 §9).
     *
     * @param runs the key's earliest run and every later one, which the next invocation mints again
     * @param approvalCloses whether the arrangement is among them, so the approval written names one the invocation
     *     no longer arrives at
     * @param labelsRefused whether the label file names the old seed set, so {@code vespera label} refuses it
     * @param sampleDrawnAgain whether the scoring run is among them, so the sample being answered is drawn again
     *     from the new run's id and the label file is written over
     */
    public record Cost(List<Run> runs, boolean approvalCloses, boolean labelsRefused, boolean sampleDrawnAgain) {
    }

    private final String key;
    private final Kind kind;
    private final Run earliestRun;
    private final String hint;
    private final String example;
    private final String plainName;
    private final String about;

    ProfileKey(String key, Kind kind, Run earliestRun, String hint, String example, String plainName, String about) {
        this.key = key;
        this.kind = kind;
        this.earliestRun = earliestRun;
        this.hint = hint;
        this.example = example;
        this.plainName = plainName;
        this.about = about;
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

    /** The key's name in plain words, which a page shows with the key beside it (ADR-340 §7). */
    public String plainName() {
        return plainName;
    }

    /** One line saying what the key is in plain words (ADR-340 §7). */
    public String about() {
        return about;
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

    /**
     * Whether the jar has a value beneath the profile's for this key, which {@code effective-values.properties} says
     * where the profile names none (ADR-330 §1, §2): the two models, the generation window, the deliverable's
     * language and the cluster resolution.
     */
    public boolean hasAValueBeneath() {
        return switch (this) {
            case EMBEDDING_MODEL, GENERATION_MODEL, GENERATION_CONTEXT_WINDOW, DELIVERABLE_LANGUAGE,
                    CLUSTER_RESOLUTION -> true;
            default -> false;
        };
    }

    public Cost cost() {
        if (earliestRun == null) {
            return new Cost(List.of(), false, false, false);
        }
        List<Run> all = List.of(Run.values());
        List<Run> runs = all.subList(all.indexOf(earliestRun), all.size());
        return new Cost(runs, runs.contains(Run.ARRANGEMENT), this == SEED_FOLDER, runs.contains(Run.EMBEDDING_SCORING));
    }

    /** The key {@code profile.yaml} spells as {@code name}, empty for a name the profile does not have. */
    static Optional<ProfileKey> named(String name) {
        return Arrays.stream(values()).filter(candidate -> candidate.key.equals(name)).findFirst();
    }
}
