package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * The record of the runs this invocation minted or continued, one id per stage (ADR-154 §1).
 *
 * <p>{@link RunMint} records every run here at the moment {@code Runs.startRun} returns (ADR-157 §2)
 * -- including stage 1's inline mint in {@link ByteLevelReductionTasklet}, which builds a {@code
 * RunMint} of its own. A later stage that
 * needs an upstream run's id, or a place that reports what this invocation arrived at ({@link
 * NextAction}, {@link ArrangementGate}), reads it from here rather than looking it up over the walk
 * (ADR-099's rule) or recomputing it. This is the "job-execution-context handoff" ADR-099 refused and
 * ADR-154 §1 now relies on: since ADR-115, every invocation starts at census and passes through every
 * step in order, so the run of a stage before it that this invocation minted or continued is always in
 * hand by the time a later stage asks for it (ADR-154, Context §3).
 *
 * <p>Backed by the job execution's own {@link ExecutionContext} rather than a bean of its own: one
 * {@link org.springframework.batch.core.job.JobExecution} is one invocation under a resourceless job
 * repository (ADR-036), with no restarts, so its execution context already lives and dies with exactly
 * the scope this record needs, and is already shared by reference across every step of the job.
 * Instantiated inline wherever it is needed -- the same shape {@link UpstreamRuns} already uses for
 * {@code Ledger} -- rather than registered as a Spring bean, so nothing has to be added to a test
 * slice's wiring for it to be reachable.
 *
 * <p>One instance per caller, sharing the one {@link ExecutionContext} the constructor is handed. No id
 * is ever replaced within an invocation: each stage mints or continues its run at most once per job
 * execution, so a key is written at most once.
 */
class InvocationRuns {

    /** Namespaced so this record's keys cannot collide with anything else a step writes into the context. */
    private static final String KEY_PREFIX = "vespera.invocation-run.";

    /** Where the count of documents not copied is handed to the closing line; outside {@link #KEY_PREFIX}, which {@link #all} reads as runs. */
    private static final String DOCUMENTS_NOT_COPIED = "vespera.deliverable.documents-not-copied";

    /** Where what an earlier drawing left in the tree is handed to the closing line (ADR-280 §4). */
    private static final String DOCUMENTS_FOLDER_LEFT_BEHIND = "vespera.deliverable.documents-folder-left-behind";

    private static final String FOLDERS_LEFT_BEHIND = "vespera.deliverable.folders-left-behind";
    private static final String FILES_LEFT_BEHIND = "vespera.deliverable.files-left-behind";

    /** Where what the tree's code sets and the names file came to is handed to the closing line (ADR-296 §6). */
    private static final String CODE_FILES_NOT_COPIED = "vespera.deliverable.code-files-not-copied";

    private static final String DORMANT_CODE_SET_NAMES = "vespera.deliverable.dormant-code-set-names";

    /** Where the count of clusters standing without writing is handed to the account (ADR-319 §5.2). */
    private static final String CLUSTERS_STANDING = "vespera.generation.clusters-standing-without-writing";

    private final ExecutionContext executionContext;

    InvocationRuns(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    /** Records {@code runId} as the run this invocation minted or continued for {@code stage}. */
    void record(String stage, RunId runId) {
        executionContext.putString(KEY_PREFIX + stage, runId.value());
    }

    /** Every run this invocation minted or continued so far, by stage name (ADR-198). */
    Map<String, String> all() {
        Map<String, String> runs = new TreeMap<>();
        for (Map.Entry<String, Object> entry : executionContext.entrySet()) {
            if (entry.getKey().startsWith(KEY_PREFIX) && entry.getValue() instanceof String id) {
                runs.put(entry.getKey().substring(KEY_PREFIX.length()), id);
            }
        }
        return runs;
    }

    /**
     * Records how many documents' copies do not stand in the tree this invocation wrote, for the closing line
     * (ADR-274 §7). Written at most once, by the generation step.
     */
    void recordDocumentsNotCopied(int documents) {
        executionContext.putInt(DOCUMENTS_NOT_COPIED, documents);
    }

    /** The count {@link #recordDocumentsNotCopied} recorded, or empty where this invocation wrote no tree. */
    OptionalInt documentsNotCopied() {
        return executionContext.containsKey(DOCUMENTS_NOT_COPIED)
                ? OptionalInt.of(executionContext.getInt(DOCUMENTS_NOT_COPIED))
                : OptionalInt.empty();
    }

    /**
     * Records what the drawing found an earlier one left in the tree, for the closing line (ADR-280 §4, ADR-286 §4):
     * counts, never names.
     */
    void recordLeftBehind(boolean documentsFolder, int otherFolders, int files) {
        executionContext.putString(DOCUMENTS_FOLDER_LEFT_BEHIND, String.valueOf(documentsFolder));
        executionContext.putInt(FOLDERS_LEFT_BEHIND, otherFolders);
        executionContext.putInt(FILES_LEFT_BEHIND, files);
    }

    /** The count of files {@link #recordLeftBehind} recorded; 0 where none was recorded. */
    int filesLeftBehind() {
        return executionContext.getInt(FILES_LEFT_BEHIND, 0);
    }

    /** Whether {@link #recordLeftBehind} recorded a {@code documents/} folder; false where none was recorded. */
    boolean documentsFolderLeftBehind() {
        return Boolean.parseBoolean(executionContext.getString(DOCUMENTS_FOLDER_LEFT_BEHIND, "false"));
    }

    /** The count of other folders {@link #recordLeftBehind} recorded; 0 where none was recorded. */
    int foldersLeftBehind() {
        return executionContext.getInt(FOLDERS_LEFT_BEHIND, 0);
    }

    /**
     * Records how many files of code have no copy standing in the tree this invocation wrote, for the closing line;
     * not called where the tree has no code set (ADR-296 §6.1).
     */
    void recordCodeFilesNotCopied(int files) {
        executionContext.putInt(CODE_FILES_NOT_COPIED, files);
    }

    /** The count {@link #recordCodeFilesNotCopied} recorded, or empty where the tree has no code set. */
    OptionalInt codeFilesNotCopied() {
        return executionContext.containsKey(CODE_FILES_NOT_COPIED)
                ? OptionalInt.of(executionContext.getInt(CODE_FILES_NOT_COPIED))
                : OptionalInt.empty();
    }

    /** Records how many entries of the names file name no code set of the run, for the closing line (ADR-296 §6.2). */
    void recordDormantCodeSetNames(int entries) {
        executionContext.putInt(DORMANT_CODE_SET_NAMES, entries);
    }

    /** The count {@link #recordDormantCodeSetNames} recorded; 0 where none was recorded. */
    int dormantCodeSetNames() {
        return executionContext.getInt(DORMANT_CODE_SET_NAMES, 0);
    }

    /**
     * Records how many clusters stand without writing in the run whose generation step this invocation ended on
     * recorded finished, for the account (ADR-319 §5.2). Written at most once, by the generation step.
     */
    void recordClustersStanding(int clusters) {
        executionContext.putInt(CLUSTERS_STANDING, clusters);
    }

    /** The count {@link #recordClustersStanding} recorded; 0 where none was recorded. */
    int clustersStanding() {
        return executionContext.getInt(CLUSTERS_STANDING, 0);
    }

    /** The run this invocation minted or continued for {@code stage}, if it has run yet this invocation. */
    Optional<RunId> runOf(String stage) {
        return Optional.ofNullable(executionContext.getString(KEY_PREFIX + stage, null)).map(RunId::new);
    }
}
