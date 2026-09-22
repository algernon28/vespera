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

    /** ADR-052 — the test report is written for a reader outside the project. */
    public static final String THE_REPORT_IS_WRITTEN_FOR_AN_OUTSIDE_READER =
            FILE + "0052-the-test-report-is-written-for-a-reader-outside-the-project.md";

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

    /** ADR-067 — content identity is a SHA-256 hash in corpus, computed within size-matched groups. */
    public static final String CONTENT_IDENTITY_IS_A_SHA_256_HASH =
            FILE + "0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md";

    /** ADR-068 — broken is a cross-format floor plus per-format structural checks, no new dependency. */
    public static final String BROKEN_IS_A_CROSS_FORMAT_FLOOR_PLUS_PER_FORMAT_CHECKS =
            FILE + "0068-broken-is-a-cross-format-floor-plus-per-format-structural-checks-no-new-dependency.md";

    /** ADR-069 — a duplicate set resolves by earliest creation time, then path. */
    public static final String DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME =
            FILE + "0069-a-duplicate-set-resolves-by-earliest-creation-time-then-path.md";

    /** ADR-010 — extraction via Docling, with scanned PDFs in scope. */
    public static final String EXTRACTION_VIA_DOCLING = FILE + "0010-extraction-via-docling-scanned-pdfs-in-scope.md";

    /**
     * ADR-070 — extraction-failed splits on Docling's status, and degenerate output is a two-tier
     * floor. The record of which response fields carry the signal: {@code status}, {@code errors[]},
     * {@code confidence}, and a cache holding the whole response rather than extracted text.
     */
    public static final String EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS =
            FILE + "0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md";

    /** ADR-071 — Docling's invocation contract: one sync call, a local timeout, two streak breakers. */
    public static final String DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL = FILE
            + "0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md";

    /** ADR-029 — chunking: structure-first, with a measured LLM fallback. */
    public static final String CHUNKING_STRUCTURE_FIRST_WITH_A_MEASURED_LLM_FALLBACK =
            FILE + "0029-chunking-structure-first-with-a-measured-llm-fallback.md";

    /** ADR-044 — the bake-off re-chunks per candidate model. */
    public static final String THE_BAKE_OFF_RE_CHUNKS_PER_CANDIDATE_MODEL =
            FILE + "0044-the-bake-off-re-chunks-per-candidate-model.md";

    /**
     * ADR-073 — stage 2 writes the derived-metric columns; tokenizer- and shingle-dependent values
     * live under their own identity. Settles the shingle table's key, granularity as a code default,
     * and that a stage's implementation version spans every module its pass writes into.
     */
    public static final String STAGE_2_WRITES_DERIVED_METRICS = FILE
            + "0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md";

    /** ADR-038 — shingling moves to stage 3; boilerplate is detected before it distorts anything. */
    public static final String SHINGLING_MOVES_TO_STAGE_3 =
            FILE + "0038-shingling-moves-to-stage-3-boilerplate-detected-before-it-distorts-anything.md";

    /** ADR-018 — stage 4 uses MinHash with LSH banding. */
    public static final String STAGE_4_USES_MINHASH_WITH_LSH_BANDING =
            FILE + "0018-stage-4-uses-minhash-with-lsh-banding.md";

    /**
     * ADR-074 — stage 3 measures shingle document frequency; a boilerplate floor ships unset. The
     * decision settled by #58: the document-frequency pass over stage-2 survivors, the omission rule
     * for singleton hashes, the corpus-size denominator, and {@code boilerplateDocumentFrequencyFloor}
     * shipping unset per observe-before-enforce.
     */
    public static final String STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY = FILE
            + "0074-stage-3-measures-shingle-document-frequency-a-boilerplate-floor-ships-unset.md";

    /** ADR-075 — stage 3 writes a confidence-distribution report that calibrates tier 2. */
    public static final String STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT = FILE
            + "0075-stage-3-writes-a-confidence-distribution-report-that-calibrates-tier-2.md";

    /**
     * ADR-076 — exception types are named for the fault, and end in {@code Exception}. Also settles
     * what an exception message may say: the event, never the rule that fired and never a repeat of
     * the chained cause.
     */
    public static final String EXCEPTION_TYPES_ARE_NAMED_FOR_THE_FAULT =
            FILE + "0076-exception-types-are-named-for-the-fault-and-end-in-exception.md";

    /**
     * ADR-077 — a regenerated measurement is a fresh row set under its own run id, not an overwritten
     * table. Amends ADR-075's "Regenerated every run" clause for {@code confidence_distribution}; the
     * HTML file it also names is still overwritten in place.
     */
    public static final String A_REGENERATED_MEASUREMENT_IS_KEYED_PER_RUN =
            FILE + "0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md";

    /**
     * ADR-078 — tier 2 is a floor on the mean confidence score; {@code low_score} is not distributed.
     * Amends ADR-070's tier-2 definition and ADR-075's Decision sentence, both of which left the
     * threshold open to the worst-page score as well as the mean.
     */
    public static final String TIER_2_IS_A_FLOOR_ON_THE_MEAN_CONFIDENCE_SCORE = FILE
            + "0078-tier-2-is-a-floor-on-the-mean-confidence-score-low-score-is-not-distributed.md";

    /**
     * ADR-079 — {@code redundant-with} covers near-duplication and containment, and the fuller
     * rendering survives. Also fixes the direction: the contained document is redundant with its
     * container, never the reverse.
     */
    public static final String REDUNDANT_WITH_COVERS_NEAR_DUPLICATION_AND_CONTAINMENT = FILE
            + "0079-redundant-with-covers-near-duplication-and-containment-the-fuller-rendering-survives.md";

    /**
     * ADR-080 — the boilerplate floor is a gate, applied before signatures are computed. Also settles
     * that an absent {@code shingle_document_frequency} row is never boilerplate, and that an
     * all-boilerplate document is empty rather than redundant.
     */
    public static final String THE_BOILERPLATE_FLOOR_IS_A_GATE =
            FILE + "0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md";

    /**
     * ADR-081 — MinHash retrieves, shingle sets judge; 128 permutations in 16 bands, and containment
     * gets its own index over each document's rarest shared shingles.
     */
    public static final String MINHASH_RETRIEVES_SHINGLE_SETS_JUDGE =
            FILE + "0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands.md";

    /**
     * ADR-082 — stage 4 judges on its first run; its thresholds are code defaults, and it ships no
     * report. Also settles that only the pairs behind a verdict are stored.
     */
    public static final String STAGE_4_JUDGES_ON_ITS_FIRST_RUN = FILE
            + "0082-stage-4-judges-on-its-first-run-its-thresholds-are-code-defaults-and-it-ships-no-report.md";

    /**
     * ADR-083 — the seed set is extracted by stage 5, and an unusable seed is recorded rather than
     * gated on: no verdict is ever written against a seed occurrence, and scoring proceeds against
     * whatever survived extraction.
     */
    public static final String THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5 = FILE
            + "0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md";

    /**
     * ADR-084 — the embedding model is a profile gate, and a vector carries its whole embedder
     * identity rather than only the model's name.
     */
    public static final String THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE = FILE
            + "0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md";

    /**
     * ADR-085 — vectors live in a content-addressed SQLite cache with no run id, and the pairwise
     * distance matrix is never materialised.
     */
    public static final String VECTORS_LIVE_IN_SQLITE = FILE
            + "0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md";

    /**
     * ADR-086 — how far the seed set resembles the survivors it will be scored against is measured
     * before the embedding model is named, and reported rather than enforced: no verdict, no gate, no
     * single summarising figure.
     */
    public static final String SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED = FILE
            + "0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md";

    /**
     * ADR-089 — a stage's run names the immediately preceding stage's run upstream, because blocking
     * verdicts are cumulative across runs, so any pass over survivors is determined by every
     * verdict-writing run before it. A verdict-free stage stays in the chain.
     */
    public static final String A_RUN_NAMES_ITS_IMMEDIATE_PREDECESSOR_UPSTREAM = FILE
            + "0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md";

    /**
     * ADR-090 — the extractor identity is the sidecar's version map and the options the client sends,
     * never its URL.
     */
    public static final String THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP = FILE
            + "0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md";

    /**
     * ADR-091 — there is no tokenizer: the runtime counts tokens behind {@code truncate: false}, the
     * chunker enforces a deterministic word budget, and the embedder identity is what Ollama reports
     * plus what we send.
     */
    public static final String THERE_IS_NO_TOKENIZER = FILE
            + "0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md";

    /**
     * ADR-092 — the seed side of the mismatch comparison is measured by seed extraction, under the
     * measurement run: a seed carries an {@code extraction_metric} row of its own, that row is never
     * read as a verdict, and the compared population is the seeds that would actually be scored.
     */
    public static final String THE_SEED_SIDE_IS_MEASURED_BY_SEED_EXTRACTION = FILE
            + "0092-the-seed-side-of-the-mismatch-comparison-is-measured-by-seed-extraction-under-the-measurement-run.md";

    /**
     * ADR-093 — logging is explicit and process-scoped: console plus a rolling file, every step and
     * every occurrence at INFO, and stage progress on a percentage/count cadence where a denominator
     * exists.
     */
    public static final String LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED = FILE
            + "0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md";

    /**
     * ADR-020 — the relevance scoring function: score = max over seeds of (mean top-3 chunk
     * similarity), storing which seed won.
     */
    public static final String RELEVANCE_SCORING_FUNCTION = FILE + "0020-relevance-scoring-function.md";

    /**
     * ADR-094 — stage 1 decides what a file is from its bytes: one prefix read and signature tests in
     * a fixed order, with the filename admitted only to narrow within a class the bytes already
     * fixed, never to decide one and never to reach the {@code broken} verdict.
     */
    public static final String FORMAT_IS_DECIDED_FROM_THE_BYTES =
            FILE + "0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md";

    /**
     * ADR-095 — the detected format and its optional subtype are a stage-1 output keyed by occurrence
     * and run, and content matching no known format earns no verdict until the format mix has been
     * measured.
     */
    public static final String DETECTED_FORMAT_IS_A_STAGE_1_OUTPUT = FILE
            + "0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md";

    /**
     * ADR-088 — the relevance threshold is read off a stratified sample of sixty labels, and an unset
     * floor does not stop the run.
     */
    public static final String RELEVANCE_THRESHOLD_IS_SIXTY_LABELS = FILE
            + "0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md";

    /** ADR-096 — k retains neighbours regardless of distance, so the retained-edge similarities are reported. */
    public static final String K_RETAINS_NEIGHBOURS_REGARDLESS_OF_DISTANCE = FILE
            + "0096-k-retains-neighbours-regardless-of-distance-so-the-retained-edge-similarities-are-reported.md";

    /** ADR-045 — clustering runs within each seed partition, never corpus-wide. */
    public static final String CLUSTERING_RUNS_WITHIN_EACH_SEED_PARTITION =
            FILE + "0045-clustering-runs-within-each-seed-partition.md";

    /** ADR-087 — clusters are modularity communities over a k-nearest-neighbour graph, built in blocks. */
    public static final String CLUSTERS_ARE_MODULARITY_COMMUNITIES = FILE
            + "0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md";

    /** ADR-097 — a relevance label is keyed by the document's path and the seed set, not by the occurrence. */
    public static final String A_LABEL_IS_KEYED_BY_PATH_AND_SEED_SET = FILE
            + "0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md";

    /**
     * ADR-098 — getting from a folder to a curated archive is four invocations, and the operator is
     * told the next value rather than the stage they are at.
     */
    public static final String FOUR_INVOCATIONS_AND_THE_NEXT_VALUE = FILE
            + "0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md";

    /**
     * ADR-100 — Docling reads the bytes too, so stage 2 sends a canonical extension derived from the
     * detected format and nothing else.
     */
    public static final String DOCLING_READS_THE_BYTES_TOO = FILE
            + "0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md";

    /**
     * ADR-101 — the run ends at the generated documents; there is no publication stage and no
     * publication target.
     */
    public static final String THE_RUN_ENDS_AT_THE_GENERATED_DOCUMENTS = FILE
            + "0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md";

    /**
     * ADR-108 — 6b sends one exemplar-first call per cluster, each document contributing its leading
     * chunk, and verifies every response.
     */
    public static final String SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER =
            FILE + "0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md";

    /** ADR-110 — pipeline hands synthesis its inputs, so the module rule gains no second exception. */
    public static final String PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS = FILE
            + "0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md";

    /** ADR-103 — the deliverable is a Markdown tree in the working directory, one tree per run id. */
    public static final String THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN = FILE
            + "0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md";

    /** ADR-104 — the surviving originals stay where they are and the deliverable references them. */
    public static final String THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED =
            FILE + "0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md";

    /** ADR-106 — a cluster gets a derived label from 6a and a generated title from 6b. */
    public static final String A_CLUSTER_GETS_A_DERIVED_LABEL_AND_A_GENERATED_TITLE =
            FILE + "0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md";

    /**
     * ADR-112 — the arrangement is ordered by partition size and cluster mean score, and the path
     * carries the order.
     */
    public static final String THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE = FILE
            + "0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md";

    /** ADR-105 — stage 6a names the arrangement stage 5 already built, and {@code unattributed} is struck. */
    public static final String STAGE_6A_NAMES_THE_ARRANGEMENT_STAGE_5_BUILT = FILE
            + "0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md";

    /**
     * ADR-107 — the arrangement gate approves a named 6a run, and the operator's path becomes five
     * invocations.
     */
    public static final String THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN = FILE
            + "0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md";

    /**
     * ADR-099 — a stage's upstream run is looked up by stage and walk, not recomputed, and two
     * candidates stop the run.
     */
    public static final String AN_UPSTREAM_IS_LOOKED_UP_AND_TWO_CANDIDATES_STOP_THE_RUN = FILE
            + "0099-a-stages-upstream-run-is-looked-up-by-stage-and-walk-not-recomputed-and-two-candidates-stop-the-run.md";

    /**
     * ADR-114 — the generation model is named in application configuration with a code default,
     * overridable in the profile, and is not a gate.
     */
    public static final String THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT = FILE
            + "0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md";

    /**
     * ADR-115 — a re-walk that observed nothing new is discarded, and a run is continued under its
     * own id.
     */
    public static final String A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED = FILE
            + "0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md";

    /**
     * ADR-116 — a run's completion is recorded per step, because several steps share one run.
     */
    public static final String A_RUNS_COMPLETION_IS_RECORDED_PER_STEP = FILE
            + "0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md";

    /**
     * ADR-117 — the relevance floor joins the scoring run's identity, so a changed threshold is a
     * different run.
     */
    public static final String THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY = FILE
            + "0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md";

    /**
     * ADR-118 — the answers a person gave never join a run's identity, so the two steps that read
     * them record no completion.
     */
    public static final String THE_ANSWERS_NEVER_JOIN_A_RUNS_IDENTITY = FILE
            + "0118-the-answers-a-person-gave-never-join-a-runs-identity-so-the-two-steps-that-read-them-record-no-completion.md";

    /**
     * ADR-119 — {@code Profile}'s constructor overloads are deleted, and the convenience they bought
     * moves to a test fixture.
     */
    public static final String PROFILE_HAS_ONE_CONSTRUCTOR = FILE
            + "0119-profiles-constructor-overloads-are-deleted-and-the-convenience-they-bought-moves-to-a-test-fixture.md";

    /**
     * ADR-120 — a profile value's type is its key's, and an unreadable value is a third state beside
     * unset.
     */
    public static final String A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE = FILE
            + "0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md";

    /**
     * ADR-109 — a citation is an exemplar ordinal minted for one call, and the check is that it is in
     * range.
     */
    public static final String A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL = FILE
            + "0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md";

    /**
     * ADR-111 — a cluster fault is a row in synthesis, and a re-run under the same id repairs rather
     * than regenerates.
     */
    public static final String A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS = FILE
            + "0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md";

    /**
     * ADR-121 — a window with no room for documents is refused, and no call is ever made with none.
     */
    public static final String A_WINDOW_WITH_NO_ROOM_IS_REFUSED = FILE
            + "0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md";

    /**
     * ADR-122 — the vocabulary binds every name this project gives itself, and leaves the prose
     * rendered for a reader outside it alone.
     */
    public static final String THE_VOCABULARY_BINDS_OUR_NAMES_NOT_RENDERED_PROSE = FILE
            + "0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md";

    /**
     * ADR-123 — an answer carrying nothing is a schema violation, and only guaranteed response fields
     * are dereferenced.
     */
    public static final String AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION = FILE
            + "0123-an-answer-carrying-nothing-is-a-schema-violation-and-only-guaranteed-response-fields-are-dereferenced.md";

    /**
     * ADR-124 — both fields of a parsed answer are required, and missing writing is a schema violation
     * rather than an uncited one.
     */
    public static final String BOTH_FIELDS_OF_A_PARSED_ANSWER_ARE_REQUIRED = FILE
            + "0124-both-fields-of-a-parsed-answer-are-required-and-missing-writing-is-a-schema-violation-rather-than-an-uncited-one.md";

    /**
     * ADR-125 — an absent title and a blank one are told apart by what each left on disk, and
     * ADR-124's owed guarantee is spent.
     */
    public static final String AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART = FILE
            + "0125-an-absent-title-and-a-blank-one-are-told-apart-by-what-each-left-on-disk.md";

    /**
     * ADR-126 — the generator identity's wiring is pinned at the invocation, and the serving engine's
     * double can refuse.
     */
    public static final String THE_GENERATOR_IDENTITYS_WIRING_IS_PINNED_AT_THE_INVOCATION = FILE
            + "0126-the-generator-identitys-wiring-is-pinned-at-the-invocation-and-the-serving-engines-double-can-refuse.md";

    /**
     * ADR-127 - a wait on a contended database lock is SQLite's busy timeout in the URL, not
     * Hikari's connection timeout, which waits for a connection from the pool instead.
     */
    public static final String A_DATABASE_LOCK_WAIT_IS_SQLITES_BUSY_TIMEOUT = FILE
            + "0127-a-database-lock-is-waited-out-by-sqlites-busy-timeout-not-by-hikaris-connection-timeout.md";

    /**
     * ADR-128 - okio is pinned to the version okhttp declares, and a dependency scan belongs in CI.
     * The pin answers CVE-2023-3635, which the okio 2.10.0 that Maven's nearest-wins was selecting
     * carried; the scan decision says a finding is surfaced in CI as an advisory rather than a gate.
     */
    public static final String OKIO_IS_PINNED_AND_A_DEPENDENCY_SCAN_BELONGS_IN_CI = FILE
            + "0128-okio-is-pinned-to-the-version-okhttp-declares-and-a-dependency-scan-belongs-in-ci.md";

    /**
     * ADR-133 — the exemplars one call sent are recorded, and a cluster file numbers its membership
     * from that record rather than deriving relevance-score order a second time.
     */
    public static final String THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED = FILE
            + "0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md";

    /**
     * ADR-134 — a line break in a value written into the deliverable is folded to a space rather than
     * escaped or emitted as {@code <br>}, and three escaping rules stand in {@code Deliverable}
     * because there are three surroundings, which is not the duplication ADR-130 consolidated. Also
     * records the ASCII-only whitespace class as a decision, and keeps the backslash in the cell rule
     * on the ground that a rule inserting its own escape character must escape that character first.
     */
    public static final String A_BREAK_IS_FOLDED_AND_THREE_ESCAPING_RULES_STAND = FILE
            + "0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md";

    /**
     * ADR-135 — a membership entry links by a path relative to its own cluster file, or carries no
     * link at all where no relative path exists, and never an absolute {@code file:} target; ADR-103's
     * test binds through the renderer; the {@code <a id>} citation anchor stands because its failure
     * has no replacement and the link's does.
     */
    public static final String A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL = FILE
            + "0135-a-membership-entry-links-relatively-or-does-not-link-at-all-and-the-citation-anchor-stands.md";

    /**
     * ADR-136 -- the angle bracket and the ampersand are backslash-escaped rather than written as HTML
     * entities, in the cell rule, the membership rule and a new heading rule; the heading becomes a
     * fourth surrounding under ADR-134's own rule, and the CSV manifest is left alone because it
     * answers to a parser rather than to a reader.
     */
    public static final String THE_ANGLE_BRACKET_AND_THE_AMPERSAND_ARE_ESCAPED = FILE
            + "0136-the-angle-bracket-and-the-ampersand-are-backslash-escaped-and-the-heading-becomes-a-fourth-surrounding.md";

    /**
     * ADR-137 -- a membership entry's destination percent-encodes the ampersand as {@code %26}, joining
     * the {@code %28} and {@code %29} already written there, because a renderer decodes an entity
     * reference in a link destination as much as anywhere else; and the destination is counted as a
     * fifth surrounding, its rule escaping by percent-encoding because it answers to a resolver where
     * the three Markdown positions answer to a reader and the CSV to a parser.
     */
    public static final String A_DESTINATIONS_AMPERSAND_IS_PERCENT_ENCODED = FILE
            + "0137-a-destinations-ampersand-is-percent-encoded-and-the-destination-is-a-fifth-surrounding.md";

    /**
     * ADR-138 -- the two brackets are backslash-escaped in the heading rule and the cell rule as well,
     * unconditionally rather than only where what follows would close a link, because a link and an
     * image both form in an ATX heading and in a table cell; and the link-text rule stops escaping them
     * a second time, becoming a call to the cell rule, since a rule that escapes what another has just
     * inserted destroys the link it was written to keep whole. Amends the two false sentences ADR-136
     * carries about the heading's brackets and about {@code onOneLine}'s callers.
     */
    public static final String A_BRACKET_IS_ESCAPED_IN_EVERY_SURROUNDING_A_VALUE_IS_READ_IN = FILE
            + "0138-a-bracket-is-escaped-in-every-surrounding-a-value-is-read-in-and-the-link-text-stops-being-a-rule-of-its-own.md";

    /**
     * ADR-139 -- a conversion the converter refused leaves a row of its own, written from memory at the
     * end of the step rather than inside the chunk transaction a skip rolls back; and a step that went
     * on to complete resolves every such row into {@code extraction-failed}, because the sidecar having
     * answered for the neighbouring occurrences is the evidence that the refusal was about this file.
     * The failure stays a Spring Batch skip, so ADR-071's streak counts what it always counted.
     */
    public static final String A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW = FILE
            + "0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md";

    /**
     * ADR-140 -- stage 2 converts eight file occurrences at a time, and "consecutive" means consecutive on the
     * drain. Also states deliberately that ADR-071's synchronous call shape stands under concurrency, on
     * a reason its own record predates.
     */
    public static final String STAGE_2_CONVERTS_EIGHT_AT_A_TIME = FILE
            + "0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md";

    /**
     * ADR-141 -- the CLI exits with the command's exit code, and the scheduler no feature uses is
     * removed at its source.
     */
    public static final String THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE = FILE
            + "0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md";

    private Adr() {
    }
}
