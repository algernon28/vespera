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

    private Adr() {
    }
}
