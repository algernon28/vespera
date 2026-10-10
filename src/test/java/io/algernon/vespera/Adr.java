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

    /**
     * ADR-143 -- a conversion Docling failed and gave no category for is a verdict against the file,
     * like any other failure it reports about the file, and never counts toward the streak that stops
     * the step.
     */
    public static final String AN_UNCATEGORISED_FAILURE_IS_A_VERDICT = FILE
            + "0143-an-uncategorised-conversion-failure-is-a-verdict-against-the-file.md";

    /**
     * ADR-144 -- a chunk the runtime refuses as too long is split until every piece fits, by characters
     * once no whitespace is left, and only that refusal is split.
     */
    public static final String A_REFUSED_CHUNK_SPLITS_UNTIL_IT_FITS = FILE
            + "0144-a-chunk-the-runtime-refuses-as-too-long-is-split-until-every-piece-fits.md";

    /**
     * ADR-145 -- a table's cells are extracted text: every reader of a Docling response reads its text
     * items and its tables' rows, once each, in Docling's own reading order.
     */
    public static final String TABLE_CELLS_ARE_EXTRACTED_TEXT = FILE
            + "0145-a-tables-cells-are-extracted-text-read-once-in-docling-reading-order.md";

    /**
     * ADR-146 -- spreadsheets are out of scope: stage 1 recognises one from its bytes and removes it
     * with a ninth verdict, out-of-scope, before anything converts it.
     */
    public static final String SPREADSHEETS_ARE_OUT_OF_SCOPE = FILE
            + "0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md";

    /**
     * ADR-147 -- the Docling sidecar is a derived image with LibreOffice Writer and Impress, and the
     * image joins the extractor identity, because two images reporting the same versions were measured
     * to convert the same PDF differently.
     */
    public static final String THE_DOCLING_SIDECAR_IS_A_DERIVED_IMAGE = FILE
            + "0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md";

    /**
     * ADR-148 -- a backtick is backslash-escaped in the heading rule, the cell rule and the link-text
     * rule, unconditionally, because two of them in one value form a code span in every renderer
     * configuration measured and switch the other rules' escapes off; and a rendering hazard present
     * only in one renderer that diverges from the specification is recorded and moves no rule.
     */
    public static final String A_BACKTICK_IS_ESCAPED_IN_EVERY_SURROUNDING_A_VALUE_IS_READ_IN = FILE
            + "0148-a-backtick-is-escaped-in-every-surrounding-a-value-is-read-in-and-one-renderers-divergence-moves-no-rule.md";

    /**
     * ADR-149 -- a survivor's pictures are read from the extraction cache and written into the tree
     * beside its cluster file, under its membership entry, named from their own bytes; a picture whose
     * bytes recur among the documents the tree lists, or that Docling placed in its furniture layer, is
     * left out, and a document shows at most ten.
     */
    public static final String A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE = FILE
            + "0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md";

    /**
     * ADR-151 -- the manifest's content hash is every survivor's SHA-256, taken at 6b through
     * extraction's own hash, because stage 1 hashes only within size-matched groups and left 47 of
     * 65 measured rows blank.
     */
    public static final String THE_MANIFESTS_CONTENT_HASH_IS_EVERY_SURVIVORS_SHA_256 = FILE
            + "0151-the-manifests-content-hash-is-every-survivors-sha-256-taken-at-6b-through-extractions-own-hash.md";

    /**
     * ADR-150 -- every conversion asks Docling for each picture's pixels embedded in the answer, so a
     * PDF's pictures reach the cache; the extractor identity changes with it; a picture cropped from a
     * page recurs at the same place or as a near-copy rather than byte for byte, and either is furniture;
     * a standalone image file still shows nothing but its entry.
     */
    public static final String A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS = FILE
            + "0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md";

    /**
     * ADR-152 -- a sampled survivor whose file will not open when stage 5's labelling page is written
     * is still asked about, with a stated fallback in place of its opening and a warning naming the
     * file; the page reads the extraction cache and never converts; and a step that records its
     * completion stops the run on such a file instead, because tolerating it would seal the gap under
     * a finished run.
     */
    public static final String A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT = FILE
            + "0152-a-survivor-whose-file-will-not-open-is-still-asked-about-without-its-opening-and-a-step-that-records-completion-stops-instead.md";

    /**
     * ADR-153 -- the whole-job tests share one slice annotation carrying the import list they all
     * named, so a stage's configuration class stops being the seam they import (amends ADR-131); and
     * what each kind of run records it was derived from is pinned as literal text.
     */
    public static final String THE_WHOLE_JOB_TESTS_SHARE_ONE_SLICE = FILE
            + "0153-the-whole-job-tests-share-one-slice-and-a-stages-configuration-class-stops-being-their-seam.md";

    /**
     * ADR-154 -- a stage names as its upstream the run of the stage before it that this invocation
     * minted or continued, not whichever the walk holds (amends ADR-099); an arrangement approval opens
     * generation only on the arrangement this invocation made or continued (amends ADR-107); and what a
     * new build costs an operator part-way through an archive (amends ADR-058).
     */
    public static final String A_STAGE_READS_THE_UPSTREAM_RUN_THIS_INVOCATION_ARRIVED_AT = FILE
            + "0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md";

    /**
     * ADR-155 -- a seed file that will not open when seed extraction reads it is recorded as an
     * unusable seed under a reason of its own, and while any is, the step records no completion and
     * stage 5 goes no further in that invocation; the next invocation reads it again. A seed file that
     * stops opening after the step finished costs only a warning.
     */
    public static final String A_SEED_FILE_THAT_WILL_NOT_OPEN_IS_RECORDED_UNDER_A_REASON_OF_ITS_OWN = FILE
            + "0155-a-seed-file-that-will-not-open-is-recorded-under-a-reason-of-its-own-and-seed-extraction-records-no-completion-until-it-opens.md";

    /**
     * ADR-156 -- a run's survivors are the occurrences with no blocking verdict under that run or any
     * run upstream of it, so a verdict under a run the invocation did not arrive at stays recorded and
     * removes nothing, and a loosened floor brings documents back over a reused walk (amends ADR-060,
     * ADR-089, ADR-014, ADR-154).
     */
    public static final String A_RUNS_SURVIVORS_ARE_READ_THROUGH_ITS_UPSTREAM_RUNS = FILE
            + "0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md";

    /**
     * ADR-157 -- a stage asks for its run after its own gate, through one invocation-scoped holder, and
     * that explicit call is what keeps a gated stage from leaving a run row (amends ADR-080); one helper
     * mints every run; one completion listener; every step is named once (amends ADR-131).
     */
    public static final String A_STAGE_ASKS_FOR_ITS_RUN_AFTER_ITS_OWN_GATE = FILE
            + "0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md";

    /**
     * ADR-158 -- the operator starts the sidecars from {@code compose.yaml}, and the packaged jar
     * starts none (amends ADR-011).
     */
    public static final String THE_PACKAGED_JAR_STARTS_NO_SIDECAR = FILE
            + "0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md";

    /**
     * ADR-159 -- a generation call asks the model not to think before it answers, and the prompt
     * names the square brackets a citation is written in, with an example; a citation in any other
     * form is still no citation.
     */
    public static final String GENERATION_ASKS_FOR_NO_THINKING_AND_NAMES_THE_SQUARE_BRACKETS = FILE
            + "0159-generation-asks-for-no-thinking-and-the-prompt-names-the-square-brackets-a-citation-is-written-in.md";

    /**
     * ADR-160 -- the relevance report asks whether any seed produced text and whether a seed file would
     * not open before it reaches the scoring run, so it mints nothing behind either gate (amends
     * ADR-132, ADR-155).
     */
    public static final String THE_RELEVANCE_REPORT_ASKS_BOTH_SEED_USABILITY_QUESTIONS = FILE
            + "0160-the-relevance-report-asks-both-seed-usability-questions-before-it-reaches-the-scoring-run.md";

    /**
     * ADR-161 -- a generation call gives its instruction after the documents rather than before them,
     * and names a length in words the reply allowance holds; the allowance itself is not raised, and
     * an answer that runs out of room is not asked for again within the invocation (amends ADR-159 §2).
     */
    public static final String THE_INSTRUCTION_FOLLOWS_THE_DOCUMENTS_AND_NAMES_A_LENGTH = FILE
            + "0161-the-instruction-follows-the-documents-and-names-a-length-the-reply-allowance-holds.md";

    /**
     * ADR-162 -- writing whose last non-blank character is a closing brace belonging to no opening one
     * is the answer's own JSON frame, and is turned down as a schema violation rather than stripped or
     * believed (extends ADR-108).
     */
    public static final String WRITING_ENDING_WITH_THE_ANSWERS_CLOSING_BRACE_IS_TURNED_DOWN = FILE
            + "0162-writing-that-ends-with-the-answers-closing-brace-is-turned-down-as-malformed.md";

    /**
     * ADR-163 -- the Docling sidecar's image replaces the PDF parser its base ships with docling-parse
     * 7.17.0, pinned exactly, whose base fonts are published to other threads only once they are whole;
     * the image's tag names the base and the parser, and changes in step everywhere it is named
     * (amends ADR-147).
     */
    public static final String THE_DOCLING_SIDECAR_PINS_DOCLING_PARSE = FILE
            + "0163-the-docling-sidecar-pins-docling-parse-7-17-0-and-its-image-is-tagged-for-the-pin.md";

    /**
     * ADR-164 -- every service compose.yaml runs carries restart: unless-stopped, so a sidecar that
     * dies is started again by Docker and one the operator stopped stays stopped, across a restart of
     * the machine too; a step the sidecar died under still fails (extends ADR-158).
     */
    public static final String EVERY_SIDECAR_RESTARTS_UNLESS_THE_OPERATOR_STOPPED_IT = FILE
            + "0164-every-sidecar-restarts-unless-the-operator-stopped-it.md";

    /**
     * ADR-165 -- compose.gpu.yaml, named with a second -f, gives Ollama every NVIDIA GPU Docker can
     * reach; compose.yaml alone asks for no device, because a request no GPU can satisfy stops up; and
     * neither the embedder nor the generator identity gains a part for the device (extends ADR-158).
     */
    public static final String OLLAMA_IS_GIVEN_A_GPU_BY_AN_OVERRIDE_FILE = FILE
            + "0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md";

    /**
     * ADR-166 -- a counting call, the same request asking for one token, precedes every answering call,
     * and what is sent is the longest leading run of the proposed documents the serving engine counts
     * inside the window less the reply allowance; every call sends num_keep -1, so a cut question is
     * counted at the window less one, which is where the ceiling now sits (amends ADR-108, ADR-111,
     * ADR-121, extends ADR-091).
     */
    public static final String THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT = FILE
            + "0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md";

    /**
     * ADR-167 -- a BMP image is out of scope: stage 1 recognises one from its bytes, BM followed by the
     * length of a header a BMP carries, as its own detected format, and removes it with the out-of-scope
     * verdict; it is sent to Docling under the neutral name an image is sent under (extends ADR-146).
     */
    public static final String BMP_IMAGES_ARE_OUT_OF_SCOPE = FILE
            + "0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md";

    /**
     * ADR-168 -- a video is out of scope, whatever its container: stage 1 recognises one from its bytes,
     * by a container signature in the detection prefix, as its own detected format, and removes it with
     * the out-of-scope verdict; it is sent to Docling under the neutral name an unrecognised file is sent
     * under (extends ADR-146).
     */
    public static final String VIDEOS_ARE_OUT_OF_SCOPE = FILE
            + "0168-videos-are-out-of-scope-and-stage-1-recognises-one-by-its-container-signature.md";

    /**
     * ADR-169 -- the label file shows, beside each document it asks about, the answer already recorded
     * for it against the seed set; label compares each answered entry with the recorded one, a changed
     * answer replaces it and is named with both values on the line label prints, and a blank entry
     * retracts nothing (amends ADR-088).
     */
    public static final String THE_LABEL_FILE_SHOWS_THE_ANSWERS_ALREADY_RECORDED = FILE
            + "0169-the-label-file-shows-the-answers-already-recorded-and-a-changed-answer-replaces-the-old-one-and-is-reported.md";

    /**
     * ADR-170 -- the Docling sidecar's Containerfile takes its base as a build argument defaulting to the
     * CPU base, and compose.gpu.yaml builds it on docling-serve's CUDA 12.8 base of the same release,
     * gives it every NVIDIA GPU, and tags it apart, because its conversions differ and the sidecar cannot
     * say which image it is; the operator names the GPU tag in vespera.docling.image (amends ADR-165,
     * ADR-147).
     */
    public static final String DOCLING_RUNS_ON_THE_GPU_WITH_AN_IMAGE_TAG_OF_ITS_OWN = FILE
            + "0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md";

    /**
     * ADR-171 -- stage 1 leaves out of scope a text file over 16,000,000 bytes, the most Docling
     * converts inside the call timeout, and, once the profile's logTimestampShareFloor is set, a log:
     * a text file of ten lines or more whose lines, read from its first and last 64 KB, begin with a
     * timestamp at least that often. Both are read from the bytes and the size, never from the name
     * (extends ADR-146).
     */
    public static final String LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE = FILE
            + "0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md";

    /**
     * ADR-173 -- every column that references file_occurrence, walk or run is the first column of an
     * index on its own table, named table_by_column and added in the same change as the column, because
     * under foreign_keys=on SQLite reads the whole child table for each parent row deleted where none
     * is (rests on ADR-008, ADR-009, ADR-115).
     */
    public static final String EVERY_COLUMN_REFERENCING_AN_OCCURRENCE_A_WALK_OR_A_RUN_IS_INDEXED = FILE
            + "0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md";

    /**
     * ADR-172 -- docling-serve's max sync wait is pushed above Vespera's own call timeout, so a slow
     * file occurrence is decided by stage 2 as a timeout (ADR-071) rather than failing stage 2 over a 504.
     */
    public static final String DOCLING_SERVES_SYNCHRONOUS_WAIT_OUTLASTS_VESPERAS_CALL_TIMEOUT = FILE
            + "0172-docling-serves-synchronous-wait-outlasts-vesperas-call-timeout.md";

    /**
     * ADR-174 -- the page of a group nothing was written over says why, in one fixed sentence per case,
     * in words for a reader of the tree (amends ADR-161).
     */
    public static final String A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY = FILE
            + "0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md";

    /**
     * ADR-180 -- the shipped datasource opens vespera.db in SQLite's write-ahead-log mode with
     * synchronous=NORMAL, left to SQLite's automatic WAL checkpoint with the emptied write-ahead log cut
     * back to 512 MiB, and the working directory stays on a local disk; measured, the journal is not what
     * makes stage 2 slow (amends ADR-127, extends ADR-054, rests on ADR-008, ADR-009).
     */
    public static final String THE_DATABASE_USES_SQLITES_WRITE_AHEAD_LOG = FILE
            + "0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md";

    /**
     * ADR-178 -- a text file over the Docling ceiling with no subtype or a Markdown one, up to
     * 64,000,000 bytes, is cut into parts of at most 8,000,000 bytes after a blank line outside a fence,
     * or else at a line end, each part converted in turn on one worker and the answers merged into one
     * DoclingDocument; one failing part fails the file and is named; the rule joins the extractor
     * identity; HTML, CSV, AsciiDoc, UTF-16 and UTF-32 text over the ceiling and any text over the bound
     * stay out of scope (amends ADR-171, ADR-090's options).
     */
    public static final String TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS = FILE
            + "0178-text-over-the-docling-ceiling-is-converted-in-parts-and-merged-into-one-answer.md";

    /**
     * ADR-179 -- no entry point starts or stops a sidecar, because both compose artifacts leave the pom;
     * the Docling image reports the name it was built as in /version, as vespera-image, and a step
     * composing the extractor identity stops when that differs from vespera.docling.image; both Docling
     * tags move to -r2; and Ollama's models live in a named volume (amends ADR-158, ADR-011, ADR-170,
     * ADR-147, ADR-165; extends ADR-163).
     */
    public static final String NO_ENTRY_POINT_STARTS_THE_SIDECARS = FILE
            + "0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md";

    /**
     * ADR-181 -- a stopped stage 2 keeps what its committed chunks recorded under its own run id and
     * reads only the occurrences none of them recorded; the place is found in the ledger, never in a
     * saved reader position (amends ADR-115, ADR-116 for stage 2, ADR-139 section 2, ADR-180 section 2;
     * rests on ADR-036, ADR-140).
     */
    public static final String A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED = FILE
            + "0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md";

    /**
     * ADR-182 -- stage 2 drops shingle_by_hash before it writes, stage 4b builds it before containment
     * retrieval reads it, and schema.sql indexes shingle on run_id alone instead (amends ADR-081,
     * ADR-180 section 6; rests on ADR-173, ADR-181).
     */
    public static final String STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT = FILE
            + "0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md";

    /**
     * ADR-183 -- the extraction cache keeps a conversion or a document-scope failure and never an
     * answer the converter blamed on itself or a Docling-reported timeout; a row an earlier build wrote
     * is passed over on read and replaced, and the change ships with ADR-181 and ADR-182 so stage 2
     * replays once (amends ADR-140 section 5, ADR-139 section 3, ADR-181).
     */
    public static final String THE_EXTRACTION_CACHE_KEEPS_ONLY_ANSWERS_ABOUT_THE_DOCUMENT = FILE
            + "0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md";

    /**
     * ADR-175 -- a file whose call fails is marked extraction-failed and skipped, and only a sidecar that
     * stays gone stops the step: an HTTP error status is a rejection of that file, a dropped connection
     * waits for the health check and retries the file once, a sidecar not back within the bound or one
     * that drops the connection twice on five files in a row fails the step, and stage 2 writes the
     * review list of files it could not read (amends ADR-071, ADR-139, ADR-140; rests on ADR-155,
     * ADR-164, ADR-172, ADR-181, ADR-183).
     */
    public static final String A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED = FILE
            + "0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md";

    /**
     * ADR-177 -- an invocation holds an operating-system lock on vespera.lock in the working
     * directory until its process ends, so a second one is refused before it opens the database file;
     * a database file SQLite reports locked is named, and said to be held by another process (extends
     * ADR-050, ADR-054; rests on ADR-127, ADR-180).
     */
    public static final String ONE_INVOCATION_PER_WORKING_DIRECTORY = FILE
            + "0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md";

    /**
     * ADR-176 -- stage 2's reader keeps a window of its own, sixteen occurrences beyond the one it hands
     * over, read and dispatched across chunk boundaries, so the converter is fed while a chunk drains and
     * commits; the order occurrences are handed over in is unchanged (amends ADR-140, ADR-181's Consequences;
     * rests on ADR-181).
     */
    public static final String STAGE_2_READS_AHEAD_ACROSS_CHUNKS = FILE
            + "0176-stage-2-reads-ahead-across-chunks-so-the-converter-is-never-left-idle-between-them.md";

    /**
     * ADR-185 -- the profile key extractionAttempt joins stage 2's run identity when it is a number other
     * than 1, so raising it mints a new stage-2 run that asks the converter again about what the extraction
     * cache does not keep, and every later stage runs again under a run of its own; nothing is discarded,
     * and putting the value back arrives at the first attempt's runs (amends ADR-140 section 5, ADR-183
     * section 6, ADR-139, ADR-143, ADR-145; rests on ADR-117, ADR-156, ADR-181, ADR-183).
     */
    public static final String RAISING_THE_EXTRACTION_ATTEMPT_ASKS_THE_CONVERTER_AGAIN = FILE
            + "0185-stage-2-asks-the-converter-again-under-a-run-of-its-own-when-extractionattempt-is-raised-and-nothing-is-discarded.md";

    /**
     * ADR-187 -- a database statement that can take minutes is announced before it starts and when it
     * ends: stage 2 says so around its drop of shingle_by_hash, which it goes on making, and start-up
     * says so around each index schema.sql builds on a table that already holds rows, which stays in
     * schema.sql (amends ADR-182 sections 2.2 and 4 and its Consequences, ADR-173 Consequences; extends
     * ADR-093; rests on ADR-177; settles #401, #402).
     */
    public static final String A_DATABASE_STATEMENT_THAT_CAN_TAKE_MINUTES_IS_ANNOUNCED = FILE
            + "0187-a-database-statement-that-can-take-minutes-says-so-before-it-starts-and-when-it-ends.md";

    /**
     * ADR-191 -- stage 3 says how many shingle rows it is about to read, at most, before its one read of
     * stage 2's run, and its measured line states how long the measurement took; the bound is the span of
     * the run's own rowids, answered by similarity, the lines are written by pipeline, and nothing shortens
     * the read (extends ADR-187 section 1 and ADR-093; rests on ADR-182 section 2.1 and ADR-188; keeps
     * ADR-041; settles #410).
     */
    public static final String STAGE_3_SAYS_HOW_MANY_SHINGLE_ROWS_IT_IS_ABOUT_TO_READ = FILE
            + "0191-stage-3-says-how-many-shingle-rows-it-is-about-to-read-and-how-long-measuring-them-took.md";

    /**
     * ADR-184 -- five service-scope failures, or five occurrences that each drop the connection twice,
     * in a row on the drain stop stage 2 only when the converter, with nothing else in flight, then fails
     * to convert the shipped control PDF; only an answer about a file given in this invocation ends a
     * row (amends ADR-071, ADR-175 section 3a, ADR-181 section 4, ADR-140 section 2; settles #385, #393).
     */
    public static final String FIVE_FAILURES_IN_A_ROW_STOP_STAGE_2_ONLY_AFTER_A_FAILED_CONTROL_CONVERSION = FILE
            + "0184-five-failures-in-a-row-stop-stage-2-only-when-the-converter-then-fails-a-control-conversion.md";

    /**
     * ADR-186 -- the deliverable's index states every key the profile record declares, as the operator
     * wrote it and in the record's order, read off the record's components rather than listed by hand;
     * every test claim about every key reads the same components (answers ADR-103; rests on ADR-061,
     * ADR-110, ADR-120, ADR-185).
     */
    public static final String THE_INDEX_STATES_EVERY_PROFILE_KEY_READ_OFF_THE_RECORD = FILE
            + "0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md";

    /**
     * ADR-188 -- stage 1's broken and out-of-scope rules and its content-identity pass live in
     * {@code corpus}, handed extraction's text limits and reporting progress through callbacks, so
     * {@code corpus} still knows no stage (amends ADR-040, ADR-146, ADR-167, ADR-168, ADR-171; answers
     * ADR-167; rests on ADR-058, ADR-067, ADR-068, ADR-069, ADR-093, ADR-095, ADR-100, ADR-110, ADR-171,
     * ADR-178).
     */
    public static final String STAGE_1_RULES_LIVE_IN_CORPUS = FILE
            + "0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md";

    /**
     * ADR-189 -- every rule that decides a stage-2 verdict, an extraction fault or a stop lives in
     * {@code extraction}: the reading of each answer, the three counts of failures in a row and what
     * follows from each, the reading of the control conversion, the end-of-step resolution of extraction
     * faults and the extractor identity's composition; {@code pipeline} keeps the Batch shells, the
     * sending of the control conversion, the exceptions and the image check (amends ADR-139 section 3,
     * ADR-140 section 3, ADR-143, ADR-154 section 3, ADR-178 section 4, ADR-179 section 3, ADR-181, ADR-184
     * section 3; rests on ADR-012, ADR-040, ADR-058, ADR-070, ADR-071, ADR-090, ADR-100, ADR-110, ADR-140,
     * ADR-143, ADR-147, ADR-175, ADR-183, ADR-184).
     */
    public static final String STAGE_2_RULES_LIVE_IN_EXTRACTION = FILE
            + "0189-stage-2s-judging-rules-and-the-extractor-identitys-composition-live-in-extraction-which-still-knows-no-stage.md";

    /**
     * ADR-190 -- stage 6b's walk over the clusters, with its stop at five answers turned down in a row,
     * its completion rule, the repair pass's deletion of a fault row and ADR-166 section 4a's exemption,
     * lives in {@code synthesis}'s {@code ClusterGeneration}, handed each cluster's documents through a
     * callback; stage 6a's lead-document rule lives in {@code synthesis}'s {@code LeadDocument}, asked
     * once per cluster; {@code pipeline} keeps the step, its lines, the deliverable and the record of
     * completion (amends ADR-110, ADR-121, ADR-166 section 4a and Consequences, ADR-149 section 9,
     * ADR-153, ADR-139 section 2; records that ADR-186 settled #320's scope 6; rests on ADR-040, ADR-041,
     * ADR-058, ADR-093, ADR-106, ADR-108, ADR-110, ADR-111, ADR-112, ADR-115, ADR-116, ADR-121, ADR-133,
     * ADR-149, ADR-154, ADR-157, ADR-166, ADR-174, ADR-186, ADR-188; settles #408).
     */
    public static final String STAGE_6_RULES_LIVE_IN_SYNTHESIS = FILE
            + "0190-stage-6bs-loop-and-6as-lead-document-rule-live-in-synthesis-which-still-knows-no-stage.md";

    /**
     * ADR-192 -- every loop in every stage that reads or writes the database or a file, or calls the
     * embedding model, the generation model or the converter, has a progress counter of its own on the one line ADR-093 fixed, an inner loop
     * having one only where the outer item's time goes; a loop with no total is a running count; no
     * counter over a known total writes more than 100 lines; capability modules hand their counts over
     * through callbacks they own and write no line; the census writes its running count at the same running
     * cadence, wherever the entries are (amends ADR-093's cadence, ADR-190 sections 2 and 4; extends
     * ADR-188 section 2 and ADR-189 section 2; settles #412).
     */
    public static final String EVERY_LOOP_REPORTS_ITS_PROGRESS = FILE
            + "0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md";

    /**
     * ADR-193 -- a statement SQLite calls back during and whose total is cheap reports "about X% of N rows"
     * through SQLite's progress callback, every 100,000 steps on the connection that runs it, turned into
     * rows by a steps-per-row ratio pinned against the bundled SQLite; a build says when its rows are gone
     * through and its silent end begins; every other statement in scope says when it starts and how long
     * it took; the drop keeps ADR-187's two lines (amends ADR-187 section 1, ADR-182 section 2.4's line,
     * ADR-191 sections 1 and 3; extends ADR-093; settles #411).
     */
    public static final String STATEMENTS_REPORT_THEIR_PROGRESS = FILE
            + "0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md";

    /**
     * ADR-198: every invocation writes an account built from an allow-list that names no document: a file
     * in a folder outside every working directory, composed from typed values and never from a message, a path or a title; an exception
     * is kept as its type and never its message; the operator's own lines do not change (extends ADR-093;
     * rests on ADR-192 and ADR-076; settles #424).
     */
    public static final String EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT = FILE
            + "0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md";

    /**
     * ADR-197 — a local model answers the relevance questions and the floor is a rule over the labels,
     * so no document reaches a hosted model (settles #423).
     */
    public static final String A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE = FILE
            + "0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md";

    /**
     * ADR-202 — generation and embedding refuse an Ollama model that is not served on this machine,
     * through one check the labeller shares, before anything is sent (extends ADR-197 section 6 and
     * ADR-114; settles #431).
     */
    public static final String GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE = FILE
            + "0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md";

    /**
     * ADR-203 — the invocation account's folder is judged by every reading of its name (as text, where it
     * leads, as the file system walks it, and where this machine's own calls lead), a name that is there
     * and cannot be followed is refused with a line of its own, and the account is written only where it
     * was judged (amends ADR-198 sections 1, 6 and 7; rests on ADR-201 and ADR-196; settles #436).
     */
    public static final String THE_ACCOUNT_FOLDER_IS_JUDGED_BY_EVERY_READING_OF_ITS_NAME = FILE
            + "0203-the-invocation-accounts-folder-is-judged-by-every-reading-of-its-name-and-a-name-that-cannot-be-followed-is-refused.md";

    /**
     * ADR-200 — stage 1 reads a run's survivors in ascending size, twice, and never holds more than one
     * size of them: the read that sizes holds no survivor, only how many share the size in hand, and the
     * read that hashes holds one size at a time; the first pass holds none; one index on the walk and the size is added,
     * so no schema version moves; the rows of different sizes are now written in size order, where they
     * were written in the order each size was first met, and no test pins that order; stage 1 has no
     * drain left, so it has no timed statement (amends ADR-188 sections 1 and 2 in what they say of
     * the drain, ADR-192 sections 4 and 7 in two phrases, and ADR-193 sections 6, 7 and 9 and its Tests
     * for stage 1; rests on ADR-060 and ADR-156; settles #405).
     */
    public static final String STAGE_1_HOLDS_ONE_SIZE_AT_A_TIME = FILE
            + "0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md";

    /**
     * ADR-199: the statements ADR-193 left unnamed take its rule: the survivor count that sizes a stage's
     * counter is timed, stage 1's second one from outside {@code corpus}, and the reads of a stopped run's
     * faults and of the occurrences it measured are counted (extends ADR-193; rests on ADR-200; settles #429).
     */
    public static final String THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE = FILE
            + "0199-the-statements-adr-193-left-unnamed-take-its-rule.md";

    /**
     * ADR-204 -- part (b) of ADR-193, written out as it is built: every line with its stage's own name,
     * ADR-193 section 6 read again against the code, the four enums with their constants, the report's
     * read of the answers a model gave timed and the reads under {@code vespera label} given no line, and
     * the tests part (b) owed (extends ADR-193 sections 4, 6 and 7 and corrects one sentence of its Tests;
     * takes up one statement ADR-199 section 3 listed; rests on ADR-199, ADR-200 and ADR-197; #411).
     */
    public static final String PART_B_OF_THE_STATEMENTS_WRITTEN_OUT = FILE
            + "0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md";

    /**
     * ADR-205 -- the relevance report's loop over the answers a local model gave gets a counter of its
     * own, {@code Stage 5 (relevance report, model answers matched)}: one item an answer looked up in this
     * walk, matched or not, its total the answers a model gave for the seed set, opened after the timed
     * read of those answers has ended, and writing nothing over none (extends ADR-192 section 4; rests on
     * ADR-197 section 3 and ADR-204 sections 2 and 7; settles #444).
     */
    public static final String THE_REPORT_COUNTS_THE_ANSWERS_A_MODEL_GAVE = FILE
            + "0205-the-relevance-report-counts-the-answers-a-model-gave-as-it-looks-each-up.md";

    /**
     * ADR-206 -- stage 2 records, for every file occurrence it writes a metric row for, the key it looked
     * the extraction cache up under, in {@code extraction_cache_key}, and seed extraction does the same
     * under the measurement run; every step after reads that key, so none opens an archive file, and a
     * file changed or gone since stage 2 is not noticed; {@code extraction}'s schema version moves to 6
     * (amends ADR-151, ADR-152, ADR-149, ADR-192 section 4 and the reason in ADR-197 section 6; restores
     * two sentences of ADR-104; settles #349).
     */
    public static final String STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY = FILE
            + "0206-stage-2-records-the-key-it-looked-the-extraction-cache-up-under-and-no-step-after-it-opens-an-archive-file.md";

    /**
     * ADR-207 -- both SHA-256 methods, {@code corpus}'s and {@code extraction}'s, read a file through one
     * buffer of 64 KiB, so a file's size sets no limit on hashing it and every hash is the value it was;
     * the two methods stay two; an {@code Error} thrown while hashing is caught by nothing and stops the
     * step; and a file the file system will not hand over still stops stage 1 and stage 2, which this
     * record measures and leaves to a ticket of its own (amends one sentence of ADR-200's Context and
     * one word of ADR-151's, "the same streamed SHA-256"; settles what ADR-206 section 8 found; settles
     * the first half of #449).
     */
    public static final String A_FILE_IS_HASHED_THROUGH_A_FIXED_BUFFER = FILE
            + "0207-a-file-is-hashed-through-a-fixed-buffer-so-its-size-sets-no-limit.md";

    /**
     * ADR-208 -- {@code vespera label --auto} needs no corpus root, since it opens nothing under one,
     * and {@code --root} is no longer an option of {@code label}, with {@code --auto} or without, so a
     * command line that passes it is refused as a usage error; {@code vespera.corpus-root} is read by
     * {@code vespera run} alone, whose rule stands (amends ADR-197 section 6 and ADR-206 sections 4 and
     * 7; keeps ADR-066; settles #451 by the operator's choice of its option (b)).
     */
    public static final String LABEL_AUTO_NEEDS_NO_CORPUS_ROOT = FILE
            + "0208-vespera-label-auto-needs-no-corpus-root-and-root-is-no-longer-an-option-of-label.md";

    /**
     * ADR-209 -- {@code Ledger} holds four records, {@code Walks}, {@code Occurrences}, {@code Runs} and
     * {@code Verdicts}, and no method of its own; the survivors and a walk's occurrences are an {@code
     * Iterable} read a page of 1,000 at a time, so no module but {@code pipeline} names a Spring Batch
     * type; a table is named in SQL only by the module {@code schema.sql} says owns it, which moves five
     * statements and leaves ADR-198's counting exception standing; the schema stays one file and the six
     * schema-version classes stay; and every run id from stage 1 to 6b moves once (amends ADR-060's
     * consumer contract, ADR-059's "schema.sql per module", ADR-041's enforcement gap and ADR-049 as
     * the architecture document read it, ADR-193 section 7 in where three statements are written, and
     * ADR-200 section 1 in the reader's type; settles #350).
     */
    public static final String THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS = FILE
            + "0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md";

    /**
     * ADR-210 -- a file the file system will not hand over when stage 1 hashes it is left unhashed and
     * goes on, and one stage 2 cannot hash, send, or read whole to convert in parts earns
     * {@code extraction-failed} under a reason that
     * begins {@code could not be read: }, with no metric, key or cache row, no retry and no part in either
     * row of five; every such failure first asks whether the corpus root can still be listed, and one
     * that cannot stops the step naming the root and removes nothing; stage 1's broken check is unchanged
     * but for that check (amends ADR-206 section 7, the prefix sentence of ADR-175 section 7 and ADR-207
     * section 4; extends ADR-184 section 4 and ADR-206 section 2 by one row each; settles #452).
     */
    public static final String A_FILE_THAT_CANNOT_BE_READ_IS_MARKED_AND_THE_STEP_GOES_ON = FILE
            + "0210-a-file-that-cannot-be-read-is-marked-and-the-step-goes-on-and-a-corpus-root-that-can-no-longer-be-listed-stops-it.md";

    /**
     * ADR-211 -- no class holds a set of every survivor of a run: stage 3's confidence distribution and the
     * seed/corpus comparison read their own table's rows a page of survivors at a time, by key; stage 3's
     * document frequency is counted in the database, in one grouping statement that reports its progress from
     * SQLite's callbacks in lines that say {@code at least}, what ruled-out occurrences contributed taken off a
     * page of the walk's occurrences at a time; the comparison's quartiles are exact over at most four reads; 5f
     * asks the ledger which members of one partition at a time survive; 5c and 5d go through the survivors as
     * they are read; each page of the survivors and of a walk's occurrences is planned by the primary key and
     * sorts nothing; SQLite's temporary files go to the working directory, set once at start-up by a listener
     * {@code main} registers, and the grouping's temporary files, which grow with the run, are the one exception
     * made to ADR-060's bound; and the run ids of stages 2 to 6b move once (amends ADR-209 section 2 and one
     * Consequence, ADR-193 sections 1, 3, 6 and 7, ADR-204 sections 3 and 4, ADR-192 sections 4 and 5, one
     * Consequence of ADR-200, and ADR-191 in what its read is and in the words of its first line; adds to ADR-177
     * one thing done as the process starts; keeps ADR-060 otherwise; settles #456).
     */
    public static final String NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN = FILE
            + "0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md";

    /**
     * ADR-212 (accepted, and built on 2026-10-09) -- an agent may read what one script beside the private-paths guard prints about
     * a working directory, and nothing else in it: counts and sums out of {@code vespera.db} by fixed
     * statements, keyed by walk ids, run ids and the code's closed vocabularies, and how many files the
     * working directory holds and their size; the guard admits only the exact command that starts that
     * script, whose hash it pins; {@code vespera.log} stays refused (amends ADR-196 sections 1 and 2;
     * keeps ADR-201 and ADR-198). Its tests are Node tests under {@code src/test/hooks}.
     */
    public static final String AGGREGATE_COUNTS_THROUGH_ONE_PINNED_SCRIPT = FILE
            + "0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md";

    /**
     * ADR-213 -- each rule the deliverable is written by has one package-private class in {@code
     * synthesis}: {@code MarkdownSurroundings} (one constant per Markdown text surrounding),
     * {@code ArchiveLink}, {@code ManifestCsv}, {@code ClusterPage}, {@code IndexPage} and {@code
     * EntryPictures}, with {@code Deliverable} orchestrating; the one cluster key is {@code ClusterSlot},
     * the citation pattern is {@code Citation.AS_WRITTEN} and the filename stem {@code FilenameStem.of};
     * ADR-134, ADR-137 section 4 and ADR-138 section 5 stand as written, and ADR-134's reopen trigger
     * gains a test (settles #351).
     */
    public static final String EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS = FILE
            + "0213-each-rule-the-deliverable-is-written-by-has-one-class-and-the-cluster-key-the-citation-pattern-and-the-filename-stem-are-each-written-once.md";

    /**
     * ADR-214 -- Chroma is removed with everything that existed for it: the starter and the test
     * container in the pom, the configuration class that deferred the store, its property, its compose
     * service and its test container; the requirement that a vector database be kept for a later reader is
     * withdrawn with it, so a reader that wants one is a decision of its own; vectors live in SQLite alone,
     * where scoring and clustering already read them (supersedes ADR-142; amends ADR-001's vector
     * database, ADR-039, ADR-032 and ADR-085's decision to keep Chroma; settles #352's Chroma point).
     */
    public static final String CHROMA_IS_REMOVED_AND_VECTORS_LIVE_IN_SQLITE_ALONE = FILE
            + "0214-chroma-is-removed-and-vectors-live-in-sqlite-alone.md";

    /**
     * ADR-215 -- no agent writes into a {@code .claude} folder: the allow list names four places under
     * the home folder's and no longer the folder whole, two of them for reading only; every folder named
     * {@code .claude} is closed to Edit, Write, NotebookEdit and a shell command but beneath {@code
     * projects}, {@code plans} and {@code worktrees}, whatever a list says; and a shell command is refused
     * for any token whose text spells a closed path, whether or not the token is read as a path. ADR-212's
     * one command stays admitted, recognised before any path or text of it is read (amends ADR-196
     * sections 2, 4 and 5, and ADR-212 sections 4 and 5; keeps ADR-201; settles #459). Its tests are Node
     * tests under {@code src/test/hooks}.
     */
    public static final String NO_AGENT_WRITES_INTO_A_CLAUDE_FOLDER = FILE
            + "0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md";

    /**
     * ADR-216 -- the rest of #352: the actuator starter and its test starter, the batch test starter, Lombok
     * and the compiler configuration that only named annotation processors leave the pom; three methods
     * nothing shipped calls leave the code, and the embedding module's model-name pattern is written once;
     * the LLM chunking seam, the windowed fallback and a second Docling reference resolver are decided gone
     * and leave with the next change to extraction, and a test-only content-identity read with the next
     * change to corpus, both of which ADR-220's change was (#469); a javadoc states its own contract and
     * cites its ADR; AGENTS.md carries no history (amends ADR-029 and ADR-046; read with ADR-209 section 1).
     */
    public static final String NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS = FILE
            + "0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md";

    /**
     * ADR-217 -- on Windows the private-paths guard reads every path once more as Windows opens its
     * names, each without the stream name after a colon and then without the dots and spaces that end
     * it, so {@code wd.\report.html} is {@code wd\report.html}; that reading is judged as every other is,
     * the allow list and its {@code read} lines, the closed {@code .claude} folders, links and the working
     * directories included, and a name made only of dots, spaces or a stream name is refused there
     * (amends ADR-196 sections 3, 4 and 5, ADR-201 section 1 and ADR-215 section 8; settles #465). Its
     * tests are Node tests under {@code src/test/hooks}.
     */
    public static final String THE_GUARD_READS_EVERY_PATH_AS_WINDOWS_OPENS_ITS_NAMES = FILE
            + "0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md";

    /**
     * ADR-218 -- every statement of {@code src/main} whose temporary files grow with the corpus is an
     * exception to the bound read into the survivors record, by the operator's choice, each with its
     * measured bytes a row, or, for three reads and ten index builds whose rows were too few, or none, to
     * leave memory in the probe, those of a measured statement of the same plan: stage 4b's build of
     * {@code shingle_by_hash}, 91.6 of temporary files for each
     * row the {@code shingle} table keeps and 183 at the peak with the write-ahead log; the builds start-up
     * makes of an index a database lacks, as a class; and the reads that sort a row for each survivor,
     * verdict, vector or cluster, as a class, to be looked at again after #458. The working directory's
     * drive needs 183 bytes free for each row of {@code shingle}. No run id moves (settles #466; leaves
     * the rows of earlier runs to #468).
     */
    public static final String EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE = FILE
            + "0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md";

    /**
     * ADR-219 -- stage 3's grouping of the shingle rows runs with {@code shingle_by_hash} present after a
     * stop of stage 3 over one corpus root while another reaches stage 4b (and, until ADR-222, after a
     * build that moved {@code pipeline} alone); SQLite then answers it through that index, writing no
     * temporary file and taking 4.8 to 6.2 times as long on synthetic ledgers on a warm solid-state disk,
     * where it does not name its index. The statement names it, {@code INDEXED BY shingle_by_run_id}, by the operator's
     * choice, and is then planned through the index on the run in both states. The clause was to ship with
     * the next change that moves {@code similarity}, and shipped with ADR-220's, in pull request #478; the
     * run ids of stages 2 to 6b move with that change (amends ADR-182, ADR-211 and ADR-218; decides #473).
     */
    public static final String STAGE_3S_GROUPING_IS_PINNED_TO_THE_INDEX_ON_THE_RUN = FILE
            + "0219-stage-3s-grouping-names-the-index-on-the-run-and-the-clause-ships-with-the-next-change-to-similarity.md";

    /**
     * ADR-220 -- no class holds every occurrence of a run: a resumed stage 2 asks a page of survivors at a time
     * which it already recorded, and reads a stopped run's faults a page at a time; the census compares two walks a
     * page of each at a time; stage 4b finds its candidate pairs a page of signed occurrences at a time, an
     * occurrence's rarest shingles with its own rows, and asks by key which candidates are signed and which phase 1
     * removed; 5e counts the scores below the floor and writes a page at a time; the relevance report reads a page
     * of scores at a time and each band gives its twelve of least key; a stage-2 fault is written where its
     * set-aside is heard and resolved a page at a time; the review list is written as its rows come; a count reads
     * no row. 5f's cluster sizes, 6a and 6b are held for a ticket of their own (amends ADR-181 section 1, ADR-199
     * sections 1 and 2, ADR-193 sections 6 and 7, ADR-192 sections 2, 4 and 5, ADR-088's draw, ADR-139 section 2's
     * mechanism; #458). Its section 15 reads it against ADR-218: two of that record's sorting reads are gone, and
     * of the two sorting statements stage 4b gains, an occurrence's rarest shingles is bounded by one occurrence,
     * and a page's candidate pairs grows with the signed occurrences that share a bucket, is not measured, and is
     * excepted as it stands by the operator's choice, its size and its bounded form being #476's, as are one
     * occurrence's containment candidates, held on the heap. The reads of ADR-218 section 3 that are left and
     * that #472 does not take are #477's. Being the next change to {@code similarity}, it ships ADR-219's
     * clause in stage 3's grouping, which is ADR-219's decision and moves no run id this record did not.
     * Re-minting {@code extraction} and {@code corpus}, it also carries out the removals ADR-216 section 5
     * deferred to the next change to each (#469), which move no run id this record did not either.
     */
    public static final String NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN = FILE
            + "0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md";

    /**
     * ADR-221 -- {@code shingle_by_hash} is an index on {@code (shingle_parameter_identity, shingle_hash)} over
     * the rows of one stage-2 run, the run stage 4b reads, where ADR-182 built it over every run's rows. Stage
     * 4b builds it where it is absent or is not that run's, dropping first what it finds, and tells whose it is
     * by the statement {@code sqlite_master} keeps. The run id is written into the statement, so the build
     * refuses an id that is not 64 lowercase hexadecimal characters. Containment retrieval keeps the run as a
     * bound value, which SQLite matches to the index's own when it plans the statement with its values. The
     * build wrote 46.1 to 50.5 bytes for each row of the run at its peak on synthetic ledgers, where the
     * whole-table build wrote 182.8 to 185.6 for each row the table keeps. An earlier run's rows stay, at 277.5
     * bytes a shingle row in the file, and their removal is #481's (amends ADR-060, ADR-182, ADR-193,
     * ADR-204, ADR-211, ADR-218, ADR-219, ADR-222 and ADR-224; decides #468). The choices are the coordinating session's, to which the operator handed them.
     */
    public static final String THE_HASH_INDEX_IS_OVER_THE_ROWS_OF_THE_RUN_IN_HAND = FILE
            + "0221-shingle-by-hash-is-an-index-on-the-rows-of-the-run-in-hand-and-an-earlier-runs-rows-stay.md";

    /**
     * ADR-222 -- a stage's version names {@code pipeline} only while a class of {@code pipeline} holds a
     * rule that shapes that stage's output: a verdict, a cache key, a cluster or text of the deliverable.
     * Every class of {@code pipeline} was read; eight rules were found, one of seed measurement, two of
     * embedding scoring and five of generation, and they are the listed allowance. Content census, content
     * redundancy and arrangement stop naming {@code pipeline}; the other three keep it. Run ids move once,
     * from content census to generation (amends ADR-058, ADR-157 and ADR-219; decides #353).
     */
    public static final String A_STAGE_NAMES_PIPELINE_ONLY_WHILE_PIPELINE_HOLDS_A_RULE_OF_IT = FILE
            + "0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md";

    /**
     * ADR-223 -- 5f's size report, 6a and 6b go through one seed partition at a time: 5f keeps five numbers a
     * partition; 6a orders the partitions from one row a seed, arranges and records one partition's clusters
     * at a time, and writes its page as the partitions come; 6b reads the clusters a partition at a time, asks
     * by key whether a cluster is written, and writes the tree a partition at a time, a synthesis doc and a
     * fault asked by key; the manifest is a read of its own, a page of the run's members at a time in occurrence
     * order; the arrangement's page, the index and the manifest are written beside the target and moved into
     * place. The furniture rule stays over every picture of the tree and keeps a digest and a hash for each
     * distinct picture, and the clusters nothing could be sent for stay held, each with its reason. Four of
     * ADR-218's sorting reads go. The pages and the manifest are byte for byte the same. Built, on the tree
     * ADR-222 left: the run ids of stages 5, 6a and 6b move, and stages 1 to 4 do not. Its section 13 reads
     * what the build did beyond the record against the code, and sends back the two reads of every survivor,
     * each to be asked for by name, and the labels of 6a's two per-partition counters (amends ADR-220
     * section 9, ADR-218 section 3, ADR-193 sections 6 and 7, ADR-204 sections 3 and 4, ADR-192 sections 4 and
     * 5, ADR-190, ADR-150 section 5 and ADR-149 section 9; #472).
     */
    public static final String THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME = FILE
            + "0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md";

    /**
     * ADR-224 -- of the seven rows of ADR-218's table of sorting reads that no other ticket held, three
     * rows, five statements, sort nothing and four rows stay excepted from the bound read into the
     * survivors record, by the operator's choice, each with its measured bytes a row. The two reads of
     * the embedder identities ask for the least, and for the least and the greatest under one name; the invocation account's three counts by
     * kind or category are each one statement that selects counts and no column, naming every constant of
     * its enumeration, {@code other} being the total less those. No index is added. The containment
     * candidates, the review list's read by path, the winning seeds and the members of one partition stay
     * as they stand, and the containment candidates' bounded form goes to #476, after #468. Its two
     * edits, to {@code pipeline} and to {@code embedding}, move the run ids of seed measurement, embedding
     * scoring, arrangement and generation, and add none to content census or content redundancy. ADR-222
     * moves all six in the same build, so the edits add no replay to it, on the condition the record's
     * section 5 states: that no build with ADR-222 and without them is run over the working directory
     * first (amends ADR-218, ADR-220, ADR-198 and ADR-060's list of exceptions; decides #477).
     */
    public static final String THE_ACCOUNTS_COUNTS_AND_THE_EMBEDDER_IDENTITY_READS_SORT_NOTHING = FILE
            + "0224-the-invocation-accounts-counts-by-kind-and-the-two-reads-of-the-embedder-identities-sort-nothing-and-four-of-adr-218s-reads-stay-excepted.md";

    /**
     * ADR-226 -- the eight rules ADR-222 found in {@code pipeline} move, all in one change, and no stage's
     * version names {@code pipeline}: what a seed the floor stopped is sent as, and which detected formats
     * list their pictures, to {@code extraction}; what the relevance floor lets stage 5e do, as the two
     * actions and the reason, to {@code embedding}; how a cluster's exemplars are gathered, behind a callback,
     * why a cluster is unwritten, and a survivor with no score listed with 0.0, to {@code synthesis}; every
     * profile key as written, to {@code profile}, which generation's version now names. Persisted names,
     * cache keys and operator text are unchanged; every stage from extraction to generation is minted once
     * (amends ADR-222, ADR-058, ADR-157, ADR-190, ADR-186, ADR-100, ADR-150, ADR-133 and ADR-152; decides #479).
     */
    public static final String NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES = FILE
            + "0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md";

    /**
     * ADR-227 -- the relevance floor's step withdraws the below-threshold removals its scoring run has
     * standing in every case, the one where the vectors carry no single embedder identity included, so
     * {@code FloorReach} no longer answers whether to. Withdrawing publishes more, the direction ADR-042
     * warns of, and is taken on ADR-118 and ADR-088. The step's lines are unchanged; the run ids of seed
     * measurement, embedding scoring, arrangement and generation move (amends ADR-226; decides #486).
     */
    public static final String THE_FLOORS_STEP_WITHDRAWS_ITS_REMOVALS_IN_EVERY_CASE = FILE
            + "0227-the-relevance-floors-step-withdraws-its-standing-removals-where-the-vectors-carry-no-single-embedder-identity.md";

    /**
     * ADR-229 -- every run's rows are kept in the twenty-six tables keyed by a run, the database file is not
     * made smaller, and no command removes anything: the growth is stated table by table. A run over a walk
     * that is no longer its corpus root's latest finished walk is never arrived at again by {@code vespera
     * run}, and {@code vespera label} still reads the one the label file names. Nothing under {@code
     * src/main} changes, so no run id moves (amends ADR-221 section 7, its Consequences and what it left
     * undecided; decides #481).
     */
    public static final String EVERY_RUNS_ROWS_ARE_KEPT_AND_THE_FILE_IS_NOT_MADE_SMALLER = FILE
            + "0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md";

    private Adr() {
    }
}
