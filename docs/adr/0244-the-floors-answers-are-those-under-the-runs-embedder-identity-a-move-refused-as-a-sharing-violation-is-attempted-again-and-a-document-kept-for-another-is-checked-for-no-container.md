# ADR-244 — The floor's answers are those under the run's embedder identity, a move refused as a sharing violation is attempted again, and a document kept for another is checked for no container

> **Partly amended — see [ADR-245](0245-stage-4b-asks-a-page-whether-its-documents-are-kept-a-move-is-attempted-again-for-a-sharing-violation-only-on-windows-and-the-pager-writes-its-own-bound.md).** Of §3: a `FileSystemException` of no subclass is attempted again only on Windows, and thrown at once elsewhere. Of §4: whether a document is kept is asked of a page of documents at once, and not of each. Of §6: the pager writes the bound itself, and a statement it is given names none. Of §7: the seeds' content keys and chunk counts are asked a page of seeds at a time.

- **Date**: 2026-10-12
- **Status**: accepted on 2026-10-12.
- **Amends**: [ADR-242](0242-a-page-moved-over-its-place-is-moved-again-a-few-times-where-the-move-is-refused-as-access-denied.md) §3, *"Only a refusal as access denied is attempted again"*, and the first item of its *What this does not decide* (§3 here). [ADR-234](0234-no-redundancy-pointer-names-a-removed-document-a-cut-is-read-from-the-band-and-a-verdict-reason-is-written-in-one-locale.md) §1, *"though its candidates are gone through and counted as before"* and *"asked only of an occurrence a container was found for"* (§4). [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md) §3, in which answers `RelevanceFloor` hands `FloorReach`: those under the run's embedder identity where there are any, where §3 has every answer of the seed set (§1). Each carries a note at its head.
- **Applies, and does not amend**: [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md) §5, *"answers given from it apply the floor again"*, and §2, that every step of a run reads under what the run was minted under; [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md); [ADR-202](0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md) §2; [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §7; [ADR-231](0231-the-embedding-step-stops-unrecorded-where-the-embedding-model-was-pulled-while-it-ran-and-scoring-refuses-a-seed-whose-chunks-have-no-vector.md) §2.
- **Keeps**: ADR-226 §3's rule itself, that the floor removes only where the answers' distinct identities are none or exactly the run's; [ADR-241](0241-the-manifest-asks-a-pages-places-together-a-clusters-synthesis-doc-is-read-once-and-a-survivors-pictures-are-looked-up-by-its-listed-key.md) §4, a survivor's detected format read one survivor at a time in each of 6b's two passes over the pictures (§8).

## Context

Seven defects change what the code does:

- **The floor's answers were chosen by run.** The floor read the answers recorded from its scoring run's label file, or every answer where none was. The floor joins the scoring run's identity (ADR-117), so a floor set from a run's report is another run, from whose file no answer is recorded yet: after a pull, the new pull's sample answered and a floor then set, the floor's run read every answer, those given under the first pull among them, and removed nothing. `afterAPullTheThresholdAppliesOnceTheNewSampleIsAnsweredOnACorpusLargerThanOneSample` set its floor before the pull and did not meet the case.
- **The embedding step minted its run from a second read of the profile.** Its gates read the embedding model's name, the name was checked with Ollama (ADR-202 §2), and `StageRuns.embeddingScoring()` then read the profile again to mint the run. A model renamed in between embedded every chunk under the first name and minted the run under the second, which then found no vector; a model cleared in between threw out of the mint, where a gate would have shut the step.
- **A move refused as a sharing violation was thrown at once.** ADR-242 left it out: Java gives `ERROR_SHARING_VIOLATION` as a `FileSystemException` of no subclass, with no error code and a reason in the machine's language.
- **A document kept for another had its candidates gone through.** Whether a row of the run names a document as kept (ADR-234 §1) was asked after every one of its containment candidates was merged and scored, though nothing written in that time can change the answer.
- **Stage 4b's own read of one hash's occurrences** (ADR-237) turned an `SQLException` into Spring's through the template's translator, which answers nothing for SQLite's errors, having no SQL state to read: a failed read was thrown as a `NullPointerException`, and SQLite's message was lost.
- **`ledger.KeysetPages` ended a read on any short page**, whatever bound the caller wrote: a statement written with `LIMIT 500` was read as one page.
- **Stage 5d counted each survivor's chunks with a statement of its own**, added with ADR-231's refusal of a survivor with fewer vectors than chunks.

And one test held less than the code does: since ADR-241 the tree looks a survivor's pictures up by the content key its listing read, but `TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest` still allowed two lookups of the key for each survivor, so a lookup put back in each pass passed it.

## Decision

### 1. The floor reads the answers given under its run's embedder identity

`RelevanceLabels.forSeedSet(String seedSet, String embedderIdentity)` answers every answer given against the seed set under that identity, the scale the run's scores are on, or every answer given against it where none is. `RelevanceFloor.reachFor` passes it the identity the run's vectors carry, and no longer takes the run. `FloorReach`'s rule (ADR-226 §3) is unchanged and is handed those answers.

An answer under the run's identity was given from a label file a run of that scale wrote, whichever run it was, and that is what ADR-228 §5's *"answers given from it apply the floor again"* asks of a floor set after the new pull's sample was answered. Where no answer is under the run's identity, every answer is read and those of another scale keep the floor from applying, as before.

**What changes besides that case**: answers under the run's identity and under another, recorded together, now apply the floor, where ADR-226 §3 read them as recording no single scale. Answers under two identities, neither the run's, still remove nothing.

### 2. The embedding step mints its run under the model its gates read

`StageRuns.embeddingScoringUnder(String modelName)` mints the scoring run under the name given, where it has not been minted in the invocation, and answers the run already minted where it has. `EmbeddingScoringTasklet` mints its run through it with the name its gates read and Ollama was asked about, and sends every chunk under that name. A profile saved between the gates' read and the mint is read by the next run, as one saved after the mint is (ADR-228 §2); a model cleared in that moment neither shuts the step nor throws. `embeddingScoring()` still reads the profile for a step that mints the run without having read a name.

### 3. A move refused by an error Java has no more specific type for is attempted again

`ReplacingMove` attempts a move again, on ADR-242 §2's five attempts and waits, where it is refused with `AccessDeniedException` or with a `FileSystemException` whose class is `FileSystemException` itself. **The class is read, and never the reason**: Java keeps no error code, and the reason is Windows's message in the user's language. On Windows that type is how Java gives `ERROR_SHARING_VIOLATION` and `ERROR_LOCK_VIOLATION`, and so also every other error it has no type for, a full disk among them; on Linux and macOS, an input or output error, a full disk or a read-only file system. Each of those is said after the attempts are spent, 1.5 seconds later than before, with the same failure. A failure Java names by a subclass, a missing file or folder, a file that exists, a move that cannot be atomic, is thrown at once, and so is any `IOException` that is not a `FileSystemException`.

### 4. A document kept for another is checked for no container

Phase 2 asks whether a row of the run names an occurrence as kept before it reads the occurrence's rarest hashes, together with whether phase 1 removed it, and an occurrence so named is counted as checked with no candidate gone through. What is written is unchanged: such a document got no containment row before either. The question is asked once for each signed occurrence phase 2 has not removed, where it was asked for those a container was found for; it is one descent of the index on `redundant_with_occurrence_id`, and saves the rarest-hash read, the merge and the scoring of every candidate for each kept document.

### 5. Stage 4b's own read throws SQLite's failure as Spring's

Where the translator answers nothing, the read of one hash's occurrences, and the close of the statement it keeps for the pass, throw `UncategorizedSQLException` carrying the `SQLException`, as `JdbcTemplate` does for its own statements. No decision of ADR-237 changes.

### 6. A keyset read refuses a statement that does not end in a page

`KeysetPages` refuses, when it is built, a statement whose text does not end in `LIMIT 1000`, `ROWS_IN_A_PAGE`, with `IllegalArgumentException`. Every statement that ships ends so, and none changes by a byte, so no plan changes and no guard is edited. The pager does not write the bound itself: that would change the text of every caller.

### 7. Stage 5d asks a page of survivors' keys and chunk counts together

`RelevanceScoringTasklet` goes through the survivors a page of 1,000 at a time, as `Verdicts.survivors` reads them, and asks for each page the content keys stage 2 recorded (`ExtractionCacheKeys.requireForOccurrences`, ADR-240's read) and how many chunks each key has (`HybridChunker.chunkCounts`, new), in one statement each, before scoring the page's survivors one at a time as before. `ChunkCache.counts` is `SELECT content_hash, COUNT(*) FROM chunk_cache WHERE content_hash IN (…) AND chunker_identity = ? AND chunking_rule_identity = ? GROUP BY content_hash`, planned by SQLite 3.45 at two values and at a thousand as `SEARCH chunk_cache USING COVERING INDEX sqlite_autoindex_chunk_cache_1`, sorting nothing; `EveryStatementThatSortsIsRecordedTest` passes unedited. A page holds 1,000 occurrence ids, their keys and their counts while it is scored, a page as ADR-220 allows one. The seeds' counts are asked as they were.

### 8. What is left as it is

- **6b's detected format, read one survivor at a time in each of the two passes over the pictures** (ADR-241 §4, which leaves reading them a page at a time undecided). `detected_format` is `corpus`'s table, so a read of a page of formats is a new method of `corpus`, and a change to `corpus` moves every run id from stage 1 on, stage 1 reading the archive again (ADR-216 §1). Decided here, and carried by the next change that re-mints `corpus` for its own reasons: `DetectedFormats` answers a page of occurrences' formats in one statement, the listing reads them with the page's paths, keys and scores, and `SurvivorPictures` reads the format the listed survivor carries.
- **The review list's count** is moved inside the catch of its writing: a failed count is said in one error line and not thrown, as ADR-175 §7 has every other failure of the list. No decision changes.

### 9. Which run ids move

A stage's implementation version is the last commit touching a module `StageModules` names for it (ADR-058, as ADR-222 leaves it), and a run's id carries its upstream runs' (ADR-048). The change touches `similarity` (`RedundancyResolution`), `extraction` (`ChunkCache`, `HybridChunker`) and `embedding` (`RelevanceLabels`), and `ledger` and `pipeline`, which no stage names. `similarity` and `extraction` are named by extraction, content census and content redundancy, and the stages after them through their upstream runs, so **stages 2 to 6b move: extraction, content census, content redundancy, seed measurement, embedding scoring, arrangement and generation**. Byte-level reduction keeps its run id. No DDL, no schema version, no cache key, no recorded setting, no verdict kind and no line of the log change; every verdict of stage 4b is what it was. The floor's removals change only where answers under the run's identity stand beside answers under another (§1).

## Consequences

- **A floor set after the new pull's sample is answered applies in the invocation that sets it**, and answers given under an earlier pull no longer keep it from applying.
- **An operator's edit of the profile while the embedding step asks Ollama about its model** is read by the next run, and fails nothing.
- **A page held open by another program on Windows is put in its place** once the program lets go, whatever error Windows refused the move with; a failure no wait lifts is said 1.5 seconds later.
- **Stage 4b asks one indexed question more for each signed occurrence it has not removed**, and nothing more for a kept document.
- **Stage 5d asks two statements a page of survivors** where it asked two a survivor.

## Tests

| Class | What it holds |
| --- | --- |
| `pipeline.APullOfTheEmbeddingModelIsADifferentScoringRunInvocationTest.aThresholdSetAfterTheNewPullsSampleIsAnsweredAppliesAtOnce`, new | 30 documents, the first pull's sample answered, a pull, the second's sample answered, a floor set: the floor's run removes all 30 in the invocation that mints it |
| `pipeline.RelevanceFloorTest` | `labelsUnderThisRunsModelDecideWhateverWasAnsweredUnderAnother`, new: answers under the run's identity and another's apply the floor. `labelsSpanningTwoModelsRemoveNothing` is now `labelsSpanningTwoOtherModelsRemoveNothing`, its two identities neither the run's |
| `pipeline.AProfileEditedWhileStageFiveRunsInvocationTest`, two new | the model renamed, and cleared, while Ollama is asked about it: one run, named after the first model, scores and groups both documents and the invocation succeeds. `EmbeddingScriptedBeans` gains `whenFirstAskedWhereAModelRuns` |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest.aMoveRefusedTwiceAsASharingViolationIsMadeOnTheThirdAttempt`, new | a move refused twice with Windows's sharing violation is made on the third attempt. `anotherFailureIsNotTriedAgain` now fails with `NoSuchFileException`, a failure Java names |
| `similarity.RedundancyResolutionTest.aDocumentKeptForAnotherHasNoContainerCandidateGoneThrough`, new | a near-duplicate's survivor inside an anthology has no candidate gone through. `RedundancyResolutionBoundsItsCandidatesTest` counts 5 × 1,108 candidates where it counted 6 × 1,108 and 2, the first of the two containers that hold all and the removed container's survivor now kept before their turn; `RedundancyResolutionReportsItsCountsTest` adds a contained document so that a candidate is reported |
| `similarity.AFailedReadOfOneHashsOccurrencesIsThrownWithItsCauseTest`, new | a read of one hash's occurrences refused as SQLite refuses a locked database is thrown as a `DataAccessException` carrying SQLite's message |
| `ledger.KeysetPagesTest.aStatementBoundByAnotherNumberThanAPageIsRefused`, new | statements ending `LIMIT 500` and with no bound are refused before anything is asked |
| `pipeline.ScoringCountsAPageOfSurvivorsChunksTogetherInvocationTest`, new | of 12 survivors and one seed, one chunk count is asked by itself, the seed's, and one for the page |
| `pipeline.ReviewListThatFailsPartwayLeavesThePreviousOneTest.aFailedCountLeavesTheReviewListBeforeAndIsNotThrown`, new | a failed count leaves the list before, says one error line and throws nothing |
| `pipeline.TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest` | no content key is asked by itself in the tree, where up to two for each survivor were allowed |

## What this does not decide

- A move refused for longer than 1.5 seconds, as ADR-242 left it.
- Telling a sharing violation from the other errors Java gives as a `FileSystemException` of no subclass, which would need the error code Java does not keep.
- The detected formats of a page of survivors, until a change re-mints `corpus` (§8).
