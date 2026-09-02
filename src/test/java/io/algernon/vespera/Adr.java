package io.algernon.vespera;

/**
 * Links from a test to the decision it exists because of, for {@code @Link(type = "adr")}.
 *
 * <p>Why a constant per decision rather than an {@code allure.link.adr.pattern}: a pattern
 * substitutes one value into a URL, and an ADR file is named {@code NNNN-its-title.md}. The id
 * alone does not produce the path, so the id-to-file map has to exist somewhere — here, as the
 * docs renderer keeps its own copy read out of the ledger table.
 *
 * <p>Add an entry when a test needs one. A dead link here is a decision that was renamed, which is
 * worth noticing.
 *
 * <p>That the decision travels as a link at all, rather than as an id in a name a stranger has to
 * read, is ADR-052.
 *
 * <p>Lives in the root package for the same reason as {@link TestSteps}: a package of its own would
 * read as a further module to {@code ApplicationModules}, and {@code ModuleBoundariesTest} would
 * fail on it.
 */
public final class Adr {

    private static final String FILE = "https://github.com/algernon28/vespera/blob/main/docs/adr/";

    /** ADR-006 — census: measure before judging. */
    public static final String CENSUS_MEASURES_BEFORE_JUDGING = FILE + "0006-census-measure-before-judging.md";

    /** ADR-011 — managed containers: the tool owns its sidecars. */
    public static final String THE_TOOL_OWNS_ITS_SIDECARS =
            FILE + "0011-managed-containers-the-tool-owns-its-sidecars.md";

    /** ADR-012 — the extraction engine is configurable: the serving runtime is config, not code. */
    public static final String EXTRACTION_ENGINE_IS_CONFIGURABLE =
            FILE + "0012-extraction-engine-is-configurable.md";

    /** ADR-013 — Ollama is the default extraction engine, and serves locally. */
    public static final String OLLAMA_IS_THE_DEFAULT_ENGINE = FILE + "0013-ollama-is-the-default-engine.md";

    /**
     * ADR-037 — the Spring Modulith event publication registry is dropped, and {@code starter-core} is
     * retained for boundary checks. The decision that keeps Modulith on the classpath at all, and so the
     * one that makes a boundary test possible.
     */
    public static final String MODULITH_RETAINED_FOR_BOUNDARY_CHECKS =
            FILE + "0037-spring-modulith-event-publication-registry-dropped.md";

    /** ADR-039 — Chroma is derived; SQLite is authoritative for vectors. */
    public static final String CHROMA_IS_DERIVED =
            FILE + "0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md";

    /** ADR-040 — modules are capability-shaped, not stage-shaped. */
    public static final String MODULES_ARE_CAPABILITY_SHAPED =
            FILE + "0040-modules-are-capability-shaped-not-stage-shaped.md";

    /** ADR-041 — the ledger owns identity and verdicts; capabilities own their own tables. */
    public static final String LEDGER_OWNS_IDENTITY_AND_VERDICTS =
            FILE + "0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md";

    /**
     * ADR-050 — the pipeline has exclusive access to the corpus. Also the record that scopes the
     * excludes-nothing claim: the walk is accountable for every entry beneath the root it was given,
     * and cannot know whether that tree is the whole archive.
     */
    public static final String PIPELINE_HAS_EXCLUSIVE_ACCESS =
            FILE + "0050-the-pipeline-has-exclusive-access-to-the-corpus.md";

    /** ADR-051 — a file occurrence is identified by its path relative to the corpus root. */
    public static final String OCCURRENCE_IDENTIFIED_BY_RELATIVE_PATH =
            FILE + "0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md";

    /** ADR-053 — the walk anomaly vocabulary is three kinds. */
    public static final String WALK_ANOMALY_VOCABULARY_IS_THREE_KINDS =
            FILE + "0053-the-walk-anomaly-vocabulary-is-three-kinds.md";

    /** ADR-015 — identity is a surrogate key per file occurrence. */
    public static final String IDENTITY_IS_A_SURROGATE_KEY =
            FILE + "0015-identity-is-a-surrogate-key-per-file-occurrence.md";

    /** ADR-047 — the pipeline never blocks. */
    public static final String THE_PIPELINE_NEVER_BLOCKS = FILE + "0047-the-pipeline-never-blocks.md";

    /** ADR-048 — walk and run identity: a walk owns occurrences, a run owns verdicts. */
    public static final String WALK_AND_RUN_IDENTITY = FILE + "0048-walk-and-run-identity.md";

    /** ADR-054 — a corpus is its root path; the database lives in a configured working directory. */
    public static final String CORPUS_IS_ITS_ROOT_PATH =
            FILE + "0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md";

    /** ADR-055 — a walk is resumed under its own id until it finishes. */
    public static final String A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID =
            FILE + "0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md";

    /** ADR-056 — excludes nothing is checked by reconciliation at finish. */
    public static final String EXCLUDES_NOTHING_IS_RECONCILED =
            FILE + "0056-excludes-nothing-is-checked-by-reconciliation-at-finish.md";

    /** ADR-057 — the verdict vocabulary is eight values, a closed enum edited by a pull request. */
    public static final String VERDICT_VOCABULARY_IS_EIGHT_VALUES =
            FILE + "0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md";

    /** ADR-058 — a stage implementation version is the last commit touching its module. */
    public static final String IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT =
            FILE + "0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md";

    /** ADR-059 — schema version is one row per module, checked and refused independently. */
    public static final String SCHEMA_VERSION_IS_ONE_ROW_PER_MODULE =
            FILE + "0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md";

    /** ADR-060 — survivors is a ledger-owned item reader, not a view. */
    public static final String SURVIVORS_IS_AN_ITEM_READER =
            FILE + "0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md";

    /** ADR-061 — the profile is YAML, typed Java records, one object per value. */
    public static final String PROFILE_IS_YAML_TYPED_RECORDS =
            FILE + "0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md";

    /** ADR-062 — census merges new profile keys and never touches an existing value. */
    public static final String CENSUS_MERGES_AND_NEVER_OVERWRITES =
            FILE + "0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md";

    /** ADR-063 — census fixtures are generated in-test; scale is measured, not tested. */
    public static final String FIXTURES_ARE_GENERATED_IN_TEST =
            FILE + "0063-census-fixtures-are-generated-in-test-scale-is-measured-not-tested.md";

    /** ADR-064 — the walk instrument generalizes; a seed folder is walked too. */
    public static final String THE_WALK_INSTRUMENT_GENERALIZES =
            FILE + "0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md";

    /** ADR-065 — the walk algorithm is tested on an in-memory filesystem; identity stays on NTFS. */
    public static final String WALK_ALGORITHM_ON_AN_IN_MEMORY_FILESYSTEM =
            FILE + "0065-the-walk-algorithm-is-tested-on-an-in-memory-filesystem-identity-stays-on-ntfs.md";

    /** ADR-066 — the command line names the root; configuration is the fallback. */
    public static final String THE_COMMAND_LINE_NAMES_THE_ROOT =
            FILE + "0066-the-command-line-names-the-root-configuration-is-the-fallback.md";

    private Adr() {
    }
}
