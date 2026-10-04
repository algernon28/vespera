# ADR-190 — Stage 6b's loop and stage 6a's lead-document rule live in `synthesis`, which still knows no stage

- **Date**: 2026-10-04
- **Status**: accepted
- **Amends**: [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md), to extend its hand-over. Its heading *"`pipeline` reads `document_cluster` and hands `synthesis` one cluster at a time"* and its sentence *"under the decision above, `pipeline` gathers the Docling title, the relevance score and the occurrence facts that ADR-106's label rule needs, leaving `synthesis` holding a table and a fallback chain"* now read: `pipeline` still reads `document_cluster` and everything else `synthesis` may not name, and hands it over either as plain values or through a callback `synthesis` owns and calls when it needs the answer, as `SurvivorPictures` already does (ADR-149). `synthesis` holds the loop over a run's clusters and the rule that finds a cluster's lead document, as well as the table and the fallback chain (§1). Its module rule, its two tables and everything else in it stand.
- **Amends**: [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md), in who the caller is, and nothing else. *"`ClusterSynthesis.nothingFitsIn` is widened … — it is what `GenerationTasklet` asks before it builds a call at all"*, *"Enforced only in `GenerationTasklet`, that is a claim about today's single call site"*, *"`GenerationTasklet` goes on asking `nothingFitsIn` before it builds a call, and goes on taking the unsendable branch decided below"* and *"That cluster takes the branch `GenerationTasklet` already has for a cluster it can offer no exemplars for"*: read `ClusterGeneration` for `GenerationTasklet` in each. The log line that names the cluster and the window is still written by `GenerationTasklet` (§5). `nothingFitsIn` stays public.
- **Amends**: [ADR-166](0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md) §4a and Consequences, in who walks past the cluster, and nothing else. *"`GenerationTasklet` already walks past that cluster without touching the streak"*, *"It needs `GenerationTasklet` to tell this fault from the other two without reading its detail"* and *"`GenerationTasklet` neither adds it to the consecutive-fault streak nor clears the streak for it"*: read `ClusterGeneration` in each. The counting call, `num_keep -1`, the ceiling at the window less one and the exemption itself carry across unchanged.
- **Amends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §9, its clause *"`GenerationTasklet` builds it from its own `JdbcTemplate`, as it builds `ClusterFaults`"*. `GenerationTasklet` still builds `DocumentPictures` that way. It no longer builds `ClusterFaults`, which is a bean (§2).
- **Amends**: [ADR-153](0153-the-whole-job-tests-share-one-slice-and-a-stages-configuration-class-stops-being-their-seam.md), its sentence *"`ClusterFaults` stays with the four tests that use it, because the annotation carries only what all of them name."* Every whole-job test now needs `ClusterFaults`, because `GenerationTasklet` is handed it, so `CascadeSliceTest` names it, with `ClusterGeneration`, and the five tests that named it in their own `@Import` stop doing so (Tests).
- **Amends**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md) §2, its parenthesis *"`ExtractionFaults` is deliberately not a `@Component` (§1's precedent, `ClusterFaults`)"*. `ExtractionFaults` stays unannotated, for the reason the rest of that sentence gives. `ClusterFaults` is no longer its precedent.
- **Records**: that [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md) settled scope 6 of [#320](https://github.com/algernon28/vespera/issues/320). The profile keys on the deliverable's index are read off `Profile`'s record components, in `pipeline`, as that record decided. Nothing here moves them.
- **Rests on**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) (`synthesis` knows no stage), [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (only `ClusterFaults` touches `cluster_fault`), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md) (every line the step writes), [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md), [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md) (the page is drawn from the rows recorded), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md), [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md), [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md), [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) (`SurvivorPictures`, the callback precedent), [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) §2 (the page is written on both branches), [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) (completion through `once`), [ADR-166](0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md), [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md), [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md), [ADR-188](0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md) (the reading of ADR-040 this applies).
- **Settles** [#408](https://github.com/algernon28/vespera/issues/408), under the decisions recorded on [#320](https://github.com/algernon28/vespera/issues/320) on 2026-10-04.

## Context

This is the third of the three records #320 planned, one per destination module. ADR-188 moved stage 1's rules into `corpus`, and [ADR-189](0189-stage-2s-judging-rules-and-the-extractor-identitys-composition-live-in-extraction-which-still-knows-no-stage.md) moved stage 2's into `extraction`. Each applies the same reading of ADR-040: a rule is a capability whether or not a stage calls it, so it belongs in the module that holds the capability, and `pipeline` keeps the step and the text that names it.

Stage 6a's and 6b's run ids already name `synthesis` and `pipeline` (`StageModules.ARRANGEMENT`, `StageModules.GENERATION`), so unlike stages 1 and 2 nothing here is about a run id that fails to move. The reason is the module rule's: what decides a cluster's label, its synthesis doc, its fault, the stop and the step's completion is a rule about clusters, and it sits in the composition root.

What `pipeline` holds today that decides one of those:

- **`GenerationTasklet.execute`**, inside the work it hands `TaskletSteps.once`: the walk over the arrangement's clusters, skipping each one that already carries a synthesis doc under the run (ADR-115); the two unsendable branches, an empty set of exemplars and ADR-121's `nothingFitsIn`, each recorded in a map the deliverable's pages read (ADR-174); the call through `ClusterSynthesis.docFor`; the fault row written for a `ClusterFaultException`; the streak of answers turned down in a row and its stop at `CONSECUTIVE_TURNED_DOWN_ANSWERS = 5` (ADR-111); ADR-166 §4a's exemption, read off `noAnswerWasAskedFor()`; the synthesis doc written for a believed answer, and the deletion of the cluster's fault row that follows it (ADR-111, #185); and the completion rule, read after the walk: the step is unfinished if any cluster was unsendable or any fault row stands under the run (ADR-116).
- **`ArrangementTasklet.leadDocumentOf`** (ADR-106): a cluster's lead is its member with the highest score, a member with no score weighed as `0.0`, the first of equal scores winning, and a cluster with no members stopping the step. It is asked twice for each cluster an invocation arranges, once by `labelFor` to name it and once by `reportOf` to draw the page.

`ClusterFaults` is built by `GenerationTasklet` from its `JdbcTemplate`, and its javadoc says why it is not a bean: nothing in `src/main` would inject one.

### What the move cannot take into `synthesis`

- **Gathering a cluster's documents.** Who is in a cluster, what each scored and what each opens with live in `embedding` and `extraction`, which `synthesis` may not name (ADR-110). So `GenerationTasklet.exemplarsOf` stays, with its rule that a member it cannot reach is left out (ADR-133) and its stop for a member with no score; so does `ArrangementTasklet.titleOf`, which reads the lead's title out of `extraction`'s cache.
- **Stopping the step.** `stopTheStep` sets Spring Batch's status, which a capability module does not name, and writes the error line that names the step.
- **Every line the operator reads**, including the four lines about a single cluster, which stay word for word under the same logger, `GenerationTasklet`'s. `synthesis` logs nothing today and starts nothing here.
- **The deliverable**, which `pipeline` writes from what it gathers (ADR-110, ADR-174), and **the record of completion** through `once` (ADR-157).

## Decision

### 1. What moves into `synthesis`

With its behaviour unchanged:

1. **The walk over a run's clusters**, from the set of clusters already written to the outcome: everything the first bullet of the Context lists except the gathering of a cluster's documents and the lines the operator reads.
2. **The breaker**: five answers turned down in a row stop the walk, and only a believed answer clears the count (ADR-111). `CONSECUTIVE_TURNED_DOWN_ANSWERS` moves with it.
3. **ADR-166 §4a's exemption**: a cluster none of whose documents the counting call finds room for is recorded with its fault, and neither adds to the streak nor clears it.
4. **The repair pass's deletion**: a believed answer's synthesis doc is written, then any fault row standing against that cluster under the run is deleted (ADR-111).
5. **The completion rule** (ADR-116): any unsendable cluster, or any fault row standing under the run once the walk ends, leaves the step unfinished.
6. **The lead-document rule** (ADR-106), over `synthesis`'s own `ClusteredDocument`, returning the lead and its label together.

### 2. Stage 6b's API

All in `io.algernon.vespera.synthesis`. `ClusterGeneration` and `ClusterFaults` are Spring components, as `ClusterSynthesis`, `SynthesisDocs` and `Clusters` already are. The rest carry no Spring annotation. No type names Spring Batch, and none logs.

```java
/** Stage 6b's walk over the clusters of one run (ADR-190). */
@Component
public class ClusterGeneration {
    /** How many answers turned down one after another stop the walk (ADR-111). */
    public static final int CONSECUTIVE_TURNED_DOWN_ANSWERS = 5;

    public ClusterGeneration(ClusterSynthesis clusterSynthesis, SynthesisDocs synthesisDocs, ClusterFaults clusterFaults);

    /**
     * Writes every cluster of clusters, in the order given, under the run generation. Writes rows in the
     * caller's transaction. A RuntimeException other than ClusterFaultException, from docFor, from
     * exemplars or from a write, leaves this method as it was raised, and so does an Error.
     */
    public GenerationOutcome write(RunId generation, List<RecordedCluster> clusters, ClusterExemplars exemplars,
            String modelName, int contextWindow, GenerationProgress progress);
}

/** How the walk ended. Each carries the clusters this invocation found unsendable, which ADR-174's pages need. */
public sealed interface GenerationOutcome {
    /** In the order the walk met them; unmodifiable. Empty on Finished by construction. */
    Map<ClusterSlot, Unwritten> unsendable();

    /** Nothing unsendable and no fault row standing: the step may record completion. */
    record Finished(int written, int alreadyWritten, Map<ClusterSlot, Unwritten> unsendable)
            implements GenerationOutcome {}

    /** Something unsendable, or a fault row standing under the run: the step must not record completion. */
    record LeftUnfinished(int standingFaults, int faultedThisInvocation, Map<ClusterSlot, Unwritten> unsendable)
            implements GenerationOutcome {}

    /** Five answers turned down in a row: the faults, oldest first, as an unmodifiable list. */
    record Stopped(List<ClusterFault> turnedDownInARow, Map<ClusterSlot, Unwritten> unsendable)
            implements GenerationOutcome {}
}

/**
 * Where the walk asks for one cluster's documents and the path of its seed, on SurvivorPictures'
 * precedent (ADR-149). pipeline implements it as a lambda.
 */
@FunctionalInterface
public interface ClusterExemplars {
    /**
     * Asked lazily: once for each cluster the walk reaches that is not already written, in the order
     * of the walk, and never for a cluster after the walk stopped. Gives the same answer each time it
     * is asked about the same cluster, and keeps nothing between calls. An empty list of exemplars
     * means the cluster has no document this run can send.
     */
    ClusterMaterial of(RecordedCluster cluster);
}

/** One cluster's documents, in any order, and the path of the seed it sits under, as ClusterCall carries it. */
public record ClusterMaterial(String seedPath, List<Exemplar> exemplars) {
    public ClusterMaterial { exemplars = List.copyOf(exemplars); }
}

/** What the walk tells its caller about each cluster it leaves unwritten, as it goes. */
public interface GenerationProgress {
    /** The exemplars came back empty. No call was made. */
    void noSendableDocument(RecordedCluster cluster);
    /** ClusterSynthesis.nothingFitsIn held for these documents. No call was made. */
    void nothingFitsTheWindow(RecordedCluster cluster, int documents, int contextWindow);
    /** docFor threw a fault whose noAnswerWasAskedFor() is true. Called before its row is written. */
    void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault);
    /** docFor threw any other fault. Called before its row is written. */
    void answerTurnedDown(RecordedCluster cluster, ClusterFault fault);
}
```

`ClusterFaults` gains `@Component` and nothing else. Its javadoc's *"Not `@Component`"* paragraph is replaced by one saying that `GenerationTasklet` and `ClusterGeneration` are both handed it, and that ADR-041 holds as before.

### 3. Stage 6a's API

```java
/** Where the lead document's Docling title is asked for (ADR-106). pipeline implements it as a lambda. */
@FunctionalInterface
public interface DocumentTitle {
    /** The occurrence's Docling-labelled title, or empty where its cached conversion carries none. */
    Optional<String> of(OccurrenceId occurrence);
}

/** A cluster's lead document and the label derived from it, found together. */
public record LabelledCluster(OccurrenceId leadDocument, ClusterLabel label) {}

/** ADR-106's lead-document rule. */
public final class LeadDocument {
    /**
     * The member of documents in cluster's partition and at cluster's ordinal with the highest score.
     * A member with a null score is weighed as 0.0; of equal scores the first in documents' order wins;
     * none at all is IllegalStateException("cluster " + cluster.ordinal() + " was arranged with no members").
     */
    public static OccurrenceId of(ArrangedCluster cluster, List<ClusteredDocument> documents);

    /**
     * The lead, by of, and ClusterLabel.derivedFrom(title.of(lead).orElse(null), pathOf.apply(lead),
     * cluster.ordinal()). Asks title once and pathOf once, both of the lead alone, title first.
     */
    public static LabelledCluster labelled(ArrangedCluster cluster, List<ClusteredDocument> documents,
            DocumentTitle title, Function<OccurrenceId, OccurrencePath> pathOf);
}
```

The lead's path arrives as a plain `Function` rather than a second callback type, because it is a lookup of `ledger`'s own facts and decides nothing. `ClusteredDocument` does not change. `DocumentTitle` shares its simple name with `extraction`'s `DocumentTitle`, a different type that holds the rule for reading a title out of a conversion. `ArrangementTasklet` implements the `synthesis` one with a method reference and names neither.

### 4. The behaviour each keeps

`ClusterGeneration.write` does exactly this, which is what `GenerationTasklet.execute` does today between reading the clusters and writing the tree:

1. Reads the slots that already carry a synthesis doc under `generation`, once, before the walk.
2. For each cluster, in the order given:
   - **Already written**: counted in `alreadyWritten`, with no call to `exemplars`, no report and no effect on the streak.
   - Otherwise it asks `exemplars.of(cluster)`, once.
   - **No exemplars**: `noSendableDocument`, then the slot is recorded as `Unwritten.NO_SENDABLE_DOCUMENT`. No call, no row, no effect on the streak.
   - **`ClusterSynthesis.nothingFitsIn(contextWindow, exemplars)`**: `nothingFitsTheWindow(cluster, exemplars.size(), contextWindow)`, then the slot is recorded as `Unwritten.NOTHING_FITS_THE_WINDOW`. No call, no row, no effect on the streak.
   - Otherwise it calls `docFor(new ClusterCall(cluster.label().value(), material.seedPath(), material.exemplars()), modelName, contextWindow)`.
   - **`ClusterFaultException`**: the report its `noAnswerWasAskedFor()` selects, then `ClusterFaults.record` under `generation`. Where an answer was asked for, the fault joins the streak, and at five the walk returns `Stopped` with the streak, before any later cluster is asked about. Where none was, the streak is untouched (ADR-166 §4a).
   - **A believed answer**: `SynthesisDocs.record`, then `ClusterFaults.delete` for that slot, then the streak is cleared.
3. After the walk it counts `ClusterFaults.forRun(generation)`. If any slot was unsendable or any fault stands, it returns `LeftUnfinished` with that count and the number faulted in this call. Otherwise it returns `Finished` with the number written in this call and the number already written.

`LeadDocument.of` filters `documents` to the cluster's seed and ordinal and takes the stream's maximum by score with null weighed as `0.0`. A stream's maximum keeps the first of equal elements, which is what `leadDocumentOf` does over the membership rows today.

### 5. What stays in `pipeline`, and how it uses the API

**`GenerationTasklet`** keeps the gates, the run, `once`, the gathering, `stopTheStep`, `writeDeliverable`, `whyUnwritten` (ADR-174), `profileValues` (ADR-186) and every line it writes, word for word. Its constructor takes `ClusterGeneration` in place of `ClusterSynthesis`, and `ClusterFaults` as an injected bean. It goes on taking the `JdbcTemplate`, for `DocumentPictures` alone. `CONSECUTIVE_TURNED_DOWN_ANSWERS` and the set of slots already written leave it. Inside `once`'s work, after reading the clusters as today, it calls:

```java
GenerationOutcome outcome = clusterGeneration.write(
        generation,
        recordedClusters,
        recorded -> {
            List<Exemplar> exemplars =
                    exemplarsOf(byCluster.getOrDefault(ClusterKey.of(recorded), List.of()), scores, canonicalRoot);
            return new ClusterMaterial(pathOf(recorded.cluster().winningSeed()), exemplars);
        },
        modelName,
        contextWindow,
        progressLines());
```

`progressLines()` returns a `GenerationProgress` that writes today's four warnings at `WARN` through `GenerationTasklet`'s own logger, each with the cluster's ordinal and partition order: *"has no document this run can send …"*, *"has {} document(s) this run could open but a reading window of {} leaves room for none of them …"*, *"has no document the serving engine counts inside the room for a question …"* and *"had its answer turned down …"*. Then it acts on the outcome:

- **`Stopped`**: `stopTheStep(contribution, chunkContext, stopped.turnedDownInARow(), generation)`, then `writeDeliverable(…, outcome.unsendable())`, then `false`.
- **`LeftUnfinished`**: today's warning with `unsendable().size()`, `standingFaults()` and `faultedThisInvocation()`, then `writeDeliverable(…, outcome.unsendable())`, then `false`.
- **`Finished`**: `writeDeliverable(…, outcome.unsendable())`, today's closing line with `written()` and `alreadyWritten()`, then `true`.

`exemplarsOf`, `openingChunkOf`, `hashOf` and `survivorsFor` keep their names and their bodies, so what ADR-133, ADR-151 and ADR-152 say about them stays true.

**`ArrangementTasklet`** keeps the gates, the run, `once`, the page and every line it writes. `labelFor` and `leadDocumentOf` leave it. `titleOf` and `pathObjectOf` stay, as the two lookups it hands `LeadDocument`.

- **A fresh arrangement**: it builds the `ClusteredDocument`s once, as today, for `Arrangement.partitionsOf`. For each arranged cluster it calls `LeadDocument.labelled(cluster, documents, this::titleOf, this::pathObjectOf)` once, records the cluster with `labelled.label()`, and keeps `labelled.leadDocument()` by the cluster's `ClusterSlot`. The page is drawn from `clusters.forRun(arrangement)`, as today (ADR-112, ADR-154 §2), each recorded cluster's lead taken from what was kept.
- **An arrangement already recorded**: it builds the `ClusteredDocument`s from the membership and the scores, and draws the page from `clusters.forRun(arrangement)`, each recorded cluster's lead found by `LeadDocument.of`. No title is read, as today.

`ArrangementReport` does not change.

### 6. Text owed with the code

So that no sentence in the source, or in the file that says what exists, puts the rules in the wrong module:

- `synthesis/package-info.java`: *"`pipeline` reads that table and hands this module one cluster's worth at a time"* and *"6a comes out thin on purpose: it holds a table, a fallback chain and an ordering rule"* are replaced by a statement that `pipeline` hands it values and callbacks, and that it holds the walk over a run's clusters, the stop, the completion rule and the lead-document rule as well, and still knows no stage.
- `ClusterFaults`: the *"Not `@Component`"* paragraph (§2). `extraction/DocumentPictures.java`: *"on `ClusterFaults`' own precedent"* goes.
- `ClusterFaultException` (its class javadoc and `noAnswerWasAskedFor`'s), `ClusterFaultKind.PROMPT_EVALUATION_CEILING`, and `ClusterSynthesis.nothingFitsIn` (*"Read by the tasklet"*): read `ClusterGeneration` where they name `GenerationTasklet` or the tasklet as the code that records the fault, leaves the streak alone or asks `nothingFitsIn`. Their sentences that say an exception *"escapes `GenerationTasklet` as a step failure"* stay true and stay.
- `AGENTS.md`'s list of what `synthesis` holds today gains the walk over a run's clusters, its stop and its completion rule, and the lead-document rule, once they are there.
- `GenerationTasklet`'s class javadoc: the paragraphs on completion and on the five-in-a-row stop say the rules are `ClusterGeneration`'s and that the step acts on its outcome. `ArrangementTasklet`'s javadoc on `reportOf` says the lead comes from `LeadDocument`.

### 7. ADR-040's rule holds

`synthesis` still knows no stage. Nothing that moves names one, and every line that does stays in `pipeline`. `synthesis` still declares `allowedDependencies = "ledger"` alone, names no Spring Batch type, and `ModuleBoundariesTest` gains no allowed dependency.

### 8. Scope 6 of #320 is ADR-186's

#320 planned a sixth scope, deriving the profile keys on the deliverable's index from `Profile`. ADR-186 did that on 2026-10-04, in `pipeline`, because `synthesis` may not name `profile` (ADR-110). This record does not touch `profileValues`.

### Sentences read and left standing

Each of these names `GenerationTasklet` or `ArrangementTasklet` and stays true after the move: ADR-111's *"an exception leaving `GenerationTasklet.execute` rolls back the very rows that say why it stopped"*, because the stop is still recorded rather than thrown; ADR-122's table row and sentence on `GenerationTasklet`'s log lines, because every line stays there; ADR-123's, ADR-124's and ADR-125's sentences on what escapes `GenerationTasklet`, because an exception that is not a `ClusterFaultException` passes through `ClusterGeneration` unchanged; ADR-133's sentence on `exemplarsOf` and ADR-152's table, because those methods stay; ADR-157's *"`GenerationTasklet` when its breaker trips or a fault stands"*, because that is still the tasklet's path out without completion; and ADR-186's sentences on `profileValues`.

## Consequences

- **The run ids of stages 3 to 6b move once**, on the first build that ships this, because it changes `pipeline` and `synthesis` and those stages name `pipeline` (ADR-058). Stages 1 and 2 keep theirs. No recorded setting changes, so `RunIdentityGoldenTest` passes unedited. The arrangement is minted again, and `arrangementApproved` must name the new one. #320 plans for this: no corpus run is started until ADR-188, ADR-189 and this record are all on main.
- **The same run gets the same clusters, labels, synthesis docs, faults, stop, completion and deliverable**, because the rules and their order are moved, not rewritten (§4). A probe captured, over fixed paths, `arrangement.html` from both of `ArrangementTasklet`'s branches, the deliverable trees and the step's lines from three invocations: one left unfinished by an unsendable cluster, two answers turned down and a cluster counted past the room; a repair pass that finished it; and a stop after five. It gave byte-identical captures on two runs of the same build once each run id was replaced by its stage. The capture is kept outside the repository, for the implementing change to be compared against.
- **The step's lines do not change**, in text, level or logger. Synthesis logs nothing.
- **One failure moves earlier, for a state no walk produces.** Today a winning seed with no recorded facts stops the step at the first cluster that is sent or, where none is sent, when the deliverable lists the survivors. Since the seed path is now gathered with the exemplars, it stops the step at the first cluster that is not already written. Either way the step fails and its transaction rolls back. The state cannot arise, because 6a resolves every seed's path in the same invocation before 6b runs.
- **A change to the walk, the stop, the completion rule or the lead rule now moves only 6a's and 6b's run ids** if it touches `synthesis` alone, where today it touches `pipeline` and moves stages 3 to 6b.
- **`ClusterFaults` is a bean**, so the whole-job slice names it, and a test that wants one is handed it.
- **Measured size**, which replaces #320's plan estimate. Code lines, with javadoc, comments, blank lines and imports left out and braces and annotations counted, on the implementing change: `synthesis` gains 132 — `ClusterGeneration` 81, `LeadDocument` 21, `GenerationOutcome` 9, `GenerationProgress` 6, `ClusterMaterial` 5, `ClusterExemplars` 4, `DocumentTitle` 4, `LabelledCluster` 1, and `ClusterFaults`' `@Component` 1. `pipeline` loses 35 — `GenerationTasklet` 448 to 419, `ArrangementTasklet` 166 to 160.

## Tests

Pinned by measurement first: the probe above, run against `main` at `7d27eaa`, and the existing tests run green there. Each new test was then run against a throwaway port of §2 and §3 in the test tree, and against sixteen mutated ports: a stop at six; an unsendable cluster, a cluster nothing fits and a cluster already written each clearing the streak; the §4a fault adding to the streak and clearing it; no deletion on success; completion counting this call's faults instead of those standing; completion ignoring unsendable clusters; exemplars asked for a cluster already written; the seed path not carried; the two fault reports swapped; a tie going to the last document; a document with no score skipped; the lead looked for across the whole partition; the title asked twice. Each mutation failed the test written for it.

- **`synthesis.ClusterGenerationTest`** (new, over the test database): the stop at five, with the five reasons in order, and the cluster after them neither asked about nor gathered; the threshold of five; a believed answer clearing the count; a cluster with no exemplars, one nothing fits and one already written, each inside a streak, neither adding to it nor clearing it, the first two carried in the outcome; ADR-166 §4a's two cases, `GenerationBreakerInvocationTest`'s two §4a claims at the class that now holds the rule; `Finished`, and `LeftUnfinished` for an unsendable cluster and for a fault standing from an earlier invocation that this one did not record; the repair pass's deletion; a refusal not about length reaching the caller with no fault row, `GenerationPromptRefusedInvocationTest`'s third claim at the class; each cluster's documents gathered once in order, its question carrying its label and seed path; each unwritten cluster reported once, by its kind.
- **`synthesis.LeadDocumentTest`** (new, pure): the lead is the cluster's own best document, not a better one in a neighbouring cluster or under another seed; a tie goes to the first offered; no score weighs as zero against a negative one; no members stops with today's message; the lead and the label come back together, the title asked once and of the lead alone; a lead with no title names the cluster by its filename.
- **`synthesis.CountedBeforeItIsAnsweredTest`, `synthesis.PromptRefusedAsPastTheWindowTest`**: already in `synthesis`, and about `ClusterSynthesis`, which does not move. Unedited.
- **`pipeline.GenerationBreakerInvocationTest`, `GenerationPromptRefusedInvocationTest`**, the two §4a tests among them, **`GenerationFaultInvocationTest`, `DeliverableInvocationTest`, `DeliverablePicturesInvocationTest`**: stay in `pipeline`, where they pin the step end to end: its exit code, its stop line, the pages, the completion record. Their one edit is that `ClusterFaults` leaves each one's own `@Import`, since the slice now names it (`CascadeSliceImportsTest`). No claim is touched.
- **`pipeline.CascadeSliceTest`**: names `ClusterGeneration` and `ClusterFaults`.
- **`RunIdentityGoldenTest`, `OperatorTextTest`, `ModuleBoundariesTest`, `CascadeSliceImportsTest`, `ArrangementInvocationTest`** and every other whole-job test: unedited, and green against the port.

Until the types in §2 and §3 existed in `src/main`, `ClusterGenerationTest`, `LeadDocumentTest` and `CascadeSliceTest` did not compile, and nothing in the test tree ran. That was the handoff to the implementer. The probe's capture, taken again on the implementing change, was identical to the one taken before it.

## What this does not decide

- **Whether `nothingFitsIn` should be narrowed** now that its caller is in its own package. It stays public.
- **Whether the per-cluster lines should name a group**, or anything else about their wording. They are moved word for word.
- **Whether a re-run should rewrite the deliverable from stored rows** rather than asking the model again (ADR-108, ADR-110). Still open.
- **Stage 1's and stage 2's rules**, which are ADR-188's and ADR-189's.
