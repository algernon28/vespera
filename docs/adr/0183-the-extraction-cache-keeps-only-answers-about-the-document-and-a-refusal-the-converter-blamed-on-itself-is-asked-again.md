# ADR-183 — The extraction cache keeps only answers about the document, and a refusal the converter blamed on itself is asked again

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §5, third bullet. *"The retune path is the ledger's usual one, unchanged: discard stage 2's rows for the run and run it again"* did not hold for any category: the re-run met the refusal in the extraction cache and recorded it again, without asking the sidecar. From this record the re-run asks the sidecar. §6 says what the sentence still leaves out.
- **Amends**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md) §3, and the two sentences of §2 and Consequences that rest on it. §3's inference, *"the sidecar answered for this occurrence's neighbours, so the refusal is a property of what was uploaded"*, was also applied to refusals read out of the extraction cache. Those were answers from an earlier invocation, whose neighbours were not this invocation's neighbours. From this record every extraction fault a step resolves comes from a call made in that invocation, so §3's sentence holds as written (§5). §2's *"the refusal is deterministic, so it is faulted again"* becomes: the sidecar is asked again, and the occurrence is faulted again only if the sidecar refuses again. The Consequences' *"the retune path is unchanged … delete that stage's rows for the run, and run it again"* becomes true for the sidecar half, with §6's caveat.
- **Amends**: [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) §1, §4 and Consequences, wherever they say a faulted occurrence is judged again from its cached answer, and its "What this does not decide" entry on this question, which this record decides (§4). Its resume rule, its read filter, its deletions and its tests are untouched.
- **Rests on**: [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) and [ADR-143](0143-an-uncategorised-conversion-failure-is-a-verdict-against-the-file.md), whose reading of a response by scope is the one classification this record uses. [ADR-012](0012-extraction-engine-is-configurable.md), [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) and [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md): the cache is keyed by content hash and extractor identity, outside any run. [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md): what a commit under `extraction` moves. [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md): an old run's verdicts remove nothing from a new run. [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md) §2.5, whose release condition this record joins (§3).
- **Settles** [#383](https://github.com/algernon28/vespera/issues/383), which ADR-181's "What this does not decide" names.

**"Document scope" and "service scope" are ADR-070's words**, as `ExtractionItemProcessor` applies them today: a response that blames the document, and one that blames the sidecar. This record adds no third reading.

## Context

### What the code does, at `3143c53`

This section describes the code before this record, and its line citations are to `3143c53` on purpose. The Decision cites the code as built.

- **`ExtractionCache.put` stores every response it is given** (`extraction/ExtractionCache.java:57-70`), its `status` and `errors_json` included, with no filter. `get` (`:39-54`) returns whatever row the key finds.
- **`DoclingExtractor` writes every answered call.** `convert` (`extraction/DoclingExtractor.java:35`, and `:55` for content that arrives unhashed) writes on every miss; `remember` (`:86`) writes whatever it is handed. `cached` (`:77`) serves whatever row `get` finds.
- **Stage 2's reader serves every hit and writes every answer.** `ConversionDispatch.dispatchIfConvertible` (`pipeline/ConversionDispatch.java:145-153`) files a cache hit as already answered, so no call is made, and hands every answered call to `remember`. `PendingConversions.take` (`pipeline/PendingConversions.java:56-57`) runs that write only once an answer is in hand. **A call that never answered is therefore never cached**: the client's own timeout (`DoclingCallTimeoutException`) leaves no row.
- **The seed side and the embedding pass convert through `DoclingExtractor.convert`** (`pipeline/SeedConversions.java:34`), so they read and write the same cache under the same rule.
- **The extraction cache's key is its primary key**, `(content_hash, extractor_identity)` (`schema.sql:195-204`). A second row for the same key cannot be written; `put` on an existing key throws.

So **a response the converter blamed on itself is cached exactly like a conversion**, and every later read of the same content under the same extractor identity is served that refusal and never reaches the sidecar. The rows are whatever `ExtractionItemProcessor` reads as service scope (`pipeline/ExtractionItemProcessor.java:307-339`): a `failure` carrying `capacity`, `target_unavailable` or `internal`, including one where that category overrides a co-occurring `policy` or `source_unavailable`. A `failure` whose error is a Docling-reported `timeout` is cached too, and is read through ADR-071's streak (`:190-195`).

**`unknown` is not among them.** #383's body lists it, but ADR-143 made an uncategorised failure a verdict against the file. It is a document-scope answer and stays cached (§1).

### What that costs

- **A document removed because of the sidecar's state stays removed.** That holds across every re-run, every resume and ADR-140 §5's retune, until the extractor identity changes or someone deletes the row by hand. ADR-140 §5 names exactly this case, a `capacity` refusal provoked by eight calls in flight, and answers it with *"discard stage 2's rows for the run and run it again"*. That discard leaves `extraction_cache` alone, because the cache is keyed outside the run (ADR-070). So the re-run is served the same refusal, faults the occurrence again, and, if the step completes, removes it again (ADR-139 §3).
- **A resume can feed the breaker without asking the sidecar, and can wedge on it.** ADR-181 §1 reads a stage-2 run's survivors less every occurrence carrying a metric row. An extraction fault carries none, so the faulted occurrences of committed chunks are all read again. With everything already recorded filtered out, they reach the drain one after another. Refusals that were spread through the stage, each with conversions between them, so that ADR-071's breaker never saw five in a row, now arrive five in a row, each served from the cache. The breaker trips with the sidecar never asked, the step fails, and every later invocation of the same run does the same thing. **This follows any stop partway through the stage, not only a lost completion record.** The survivors reader reads in ascending occurrence id (`Ledger.survivors`), and a committed chunk's occurrences have lower ids than every occurrence not yet read, so on resume the faulted occurrences of committed chunks are read first, back to back, before anything else. Five of them anywhere in the committed chunks are enough. The case where only the completion record was lost (ADR-180 §2) is the one where nothing else is read at all. ADR-181 §4 said the breaker meets these occurrences *"mostly as the cached answers their chunks committed, as it would have done in an uninterrupted invocation"*. That second half is not true either way: the uninterrupted invocation met them between conversions. `ServiceScopeRefusalInvocationTest` (Tests) shows the wedge.
- **ADR-139 §3's evidence was borrowed.** A fault resolved from a cached refusal is judged on the strength of neighbours that answered in some earlier invocation, possibly under another run. The neighbours that answered in this one say nothing about it.

## Decision

### 1. The extraction cache keeps an answer only if it is about the document

A response is read by `ResponseScope.of` (`extraction/ResponseScope.java:37`), in the order `ExtractionItemProcessor` read it before this record (`pipeline/ExtractionItemProcessor.java:181-198` and `:307-339` at `3143c53`), and the first step that applies decides:

1. **`success` or `partial_success`**: a conversion, whatever errors it carries (ADR-070's pass-through). **Kept.**
2. **Any other status whose errors carry `timeout`**: a Docling-reported timeout, read through ADR-071's streak. **Not kept**, whatever else the errors carry. So `backend_failure` beside a reported `timeout` is a timeout, and is not kept.
3. **`backend_failure` or `inference_failure` anywhere in the errors**: a document-scope failure, unconditionally. **Kept**, even beside `capacity`, `target_unavailable` or `internal`.
4. **`capacity`, `target_unavailable` or `internal` anywhere in the errors**: a service-scope failure. **Not kept.** This overrides every category the next step reads as document scope, so `unknown`, `policy` or `source_unavailable` beside one of these three is service scope, and is not kept.
5. **Everything else**: `policy`, `source_unavailable` or `unknown` with none of those three beside it, or a failure reporting no error at all (ADR-143). A document-scope failure. **Kept.**

"Document scope" in this record means steps 1, 3 and 5. "Service scope" means step 4. Step 2 is neither until the streak reads it, and the cache cannot carry the streak (below).

**The test is the one `ExtractionItemProcessor` applied, moved, not copied.** Before this record the reading was three private predicates and the branch order in `doProcess` and `categorizeFailure` (`pipeline/ExtractionItemProcessor.java:181-198`, `:307-384` at `3143c53`). `extraction` cannot call `pipeline` (AGENTS.md's module rule), so that reading moves into `extraction` as one public type, `ResponseScope` (`extraction/ResponseScope.java`). `ExtractionItemProcessor` decides each occurrence's outcome by switching on it (`pipeline/ExtractionItemProcessor.java:182-200`, with a document-scope failure judged in `judgeDocumentScope`, `:303-304`). `ExtractionCache` decides by it what to keep and what to serve (§2). After this, no scope predicate is left in `pipeline`. This is the first row of [#320](https://github.com/algernon28/vespera/issues/320)'s table ("ADR-070's classification"), taken early because this record cannot be built without it. The streak, the extractor identity's composition, `ExtractionFaultRecorder` and `ConversionDispatch` stay where #320 found them.

**A Docling-reported timeout is not kept, for three reasons.** First, ADR-071 reads it *"exactly like"* the client's own timeout, and that one is never cached, because no answer came back. Caching one and not the other is a difference no record chose. Second, its scope is not in the response: the streak decides it, and the streak is a fact about the pass, not about the file. A row the cache serves later carries no streak, so it cannot say which reading it was given. Third, ADR-140's Consequences say the timeout budget now contains a queue, so a timeout says something about the load as well as about the file. The cost: a file that timed out alone, and was removed as a document-scope `extraction-failed`, is asked about again on every new stage-2 run, at up to its own conversion time. Within one run nothing changes, because that verdict commits in its chunk and a resume keeps it (ADR-181 §1, §4). The case is rare as deployed: Vespera sends docling-serve no per-document timeout, so a reported `timeout` comes only from docling-serve's own limit. How often that happens on the archive has not been measured.

**Rejected: write everything and treat a service-scope row as a miss on read.** It would work, and it costs more in three ways:

- Every refusal would still write a row that nothing may ever serve.
- `put` would have to overwrite on every refusal followed by a conversion, not only on the old rows §2 deals with.
- The cache's meaning would no longer be its rows. `DocumentTitles` and `DocumentPictures` read `extraction_cache` with their own SQL (`extraction/DocumentTitles.java:32`, `extraction/DocumentPictures.java:43`). They, and any later reader, would each have to remember the filter.

Kept only on write, a row in the cache is an answer about the content, whoever reads it. §2 needs a filter on read as well, but only for rows written before this record. It is the same filter, not a second rule.

### 2. Rows already in a working directory are passed over on read and replaced when a new answer is kept

Every working directory that has run stage 2 may already hold service-scope rows. They are handled where they are met:

- **`ExtractionCache.get` never returns a row that §1 would not have written** (`extraction/ExtractionCache.java:55`), so neither does any `DoclingExtractor` read built on it: `cached`, and the lookup inside `convert`. It is treated as a miss, so for a reader that converts, the content goes to the sidecar. Each time a row is passed over, one INFO line names the content hash, the extractor identity and the category the row carries, and says that a step that converts this content asks the converter again. This line is what tells the operator it happened.
- **Keeping a new answer for that key replaces the old row.** `ExtractionCache.put` (`extraction/ExtractionCache.java:92`) writes nothing that §1 does not keep, and writes over an existing row for the same key and does not throw. If the new answer is also a service-scope refusal, nothing is written and the old row stays, still passed over.

The other two options, and what each costs:

- **Delete the rows once.** There is no seam for "once" except a schema version. Moving `ExtractionSchema.VERSION` makes the guard refuse every existing database outright (ADR-059), so the operator would start a new working directory and every conversion in it would go back to Docling. On the archive that is the whole of stage 2's conversion time, to remove perhaps tens of rows. A targeted delete would have to pick the rows out of `errors_json`. Done in SQL, that is a second classification, which §1 forbids. Done in Java at start-up, it reads every row of the largest response table to find a few, and still needs a marker somewhere to say it has been done.
- **Leave them.** The wrongly removed documents in today's working directories, the ones #383 is about, stay removed. They stay removed under the replay §3 requires as well, because the replay would be served their refusals and record them again.

**`ExtractionCache` is not the only reader of the table, and §2 binds only its reads, which every `DoclingExtractor` read goes through.** `DocumentPictures` reads by content hash and extractor identity (`extraction/DocumentPictures.java:43`). A refusal row has no pictures, and a document whose answer was refused is not a survivor, so it never asks. `DocumentTitles.forContentHash` reads by content hash alone, `ORDER BY extractor_identity LIMIT 1` (`extraction/DocumentTitles.java:32-33`), so where an older identity's refusal row sorts ahead of a newer identity's conversion, it picks the refusal and finds no title. That predates this record: it picks an older identity's row today, whatever that row holds. The only cost is a missing title: 6a then derives the cluster's label from the lead document's path (`ArrangementTasklet`, `ClusterLabel.derivedFrom`). This record does not change it, and a row passed over under one identity is not removed (§2 replaces it only under the identity that asks again).

**Passing over on read costs little.** The classification runs on a response `get` has already parsed. A row whose content is never read again stays in the table, a few hundred bytes of refusal with nothing reading it. The operator sees each one passed over once, in the INFO line, when that content is read. The ledger also shows what came of it: the occurrence's metric row or verdict, or a new extraction fault, under the run that asked.

### 3. `extraction`'s implementation version moves, in the same release as ADR-181 and ADR-182

§1 and §2 are commits under `src/main/java/io/algernon/vespera/extraction`, so they move `extraction`'s implementation version (ADR-058). That moves every stage-2 run id, and every later run id that names stage 2's. **No schema version moves**: no table changes, and moving one would refuse every existing database (§2).

**The operator decided on 2026-10-03 that this ships in the same release as #382 (ADR-181) and #384 (ADR-182).** Those two already move the same run ids (ADR-181's Consequences; ADR-182 §2.5). Shipped together, the three cost one replay of stage 2 and of every stage after it, not two. **So no build is installed that contains #382 and #384 without this record's implementation.** Whoever merges the first of the three holds its release until all three are merged.

**What the combined replay does with failures already in the cache.** The replay is a whole stage 2 under a new run id. Every survivor is read, and each cache row is met once:

- **A conversion** is served from the cache, as ADR-182 §2.5 says. No call is made.
- **A document-scope failure** (§1 steps 3 and 5) is served from the cache and judged exactly as before: `extraction-failed` with a metric row, under the new run. No call is made.
- **A service-scope refusal or a Docling-reported timeout** is passed over (§2), and that content goes to the sidecar, once. If it converts, the row is replaced and the occurrence is measured: a document removed under the old run comes back. If the answer is a document-scope failure, the row is replaced and the occurrence earns `extraction-failed`. If the sidecar refuses again, the occurrence is an extraction fault of the new run. Nothing is cached for it, and if the step completes the fault is resolved under ADR-139.
- **The old run's verdicts stay recorded** and remove nothing from the new run (ADR-156).

The replay's extra cost is one call per such row it reaches. Before upgrading, the operator can estimate how many, under the stage-2 run the last build used. Two queries, and their sum is **a lower bound, not a count**. The cache also holds rows for content that run did not read, such as earlier runs, the seed side, and other walks, and nothing in the ledger names those.

- Refusals the converter blamed on itself, and reported timeouts that ADR-071's streak turned into faults, per category: `SELECT category, COUNT(*) FROM extraction_fault WHERE run_id = ? GROUP BY category`.
- Reported timeouts the streak left as document-scope removals: `SELECT COUNT(*) FROM verdict v WHERE v.run_id = ? AND v.kind = 'EXTRACTION_FAILED' AND v.reason LIKE 'timeout:%' AND EXISTS (SELECT 1 FROM extraction_metric m WHERE m.occurrence_id = v.occurrence_id AND m.run_id = v.run_id)`. The reason prefix is `"timeout: " + detail` (`ExtractionItemProcessor.resolveTimeout`, `pipeline/ExtractionItemProcessor.java:287`), unchanged by this record, so it is the prefix the last build wrote too. The metric row is what tells a reported timeout from the client's own, which carries the same prefix and has no metric row, because no response came back (`:284-286`).

**Shipped separately, as the operator declined to do**, this record's later build would move the run ids again: a second replay, and between the two, a first replay that re-faults every one of those documents from the cache and removes them under a new run.

### 4. Resume, the breaker and the streak

**A resume asks the sidecar about every faulted occurrence.** ADR-181 §1 reads every faulted occurrence again. None now has a cache row, so each reaches the sidecar, whether its chunk committed or not. The breaker counts what the sidecar answers now, not refusals stored from before the stop. Five refusals spread through a completed step and read back to back on resume trip the breaker only if the sidecar refuses all five again. If the sidecar has recovered, they convert, each reset the streak, and the resume completes. This removes the wedge described in Context, for every refusal that depended on the sidecar's state.

**What it does not remove**: five or more refusals that the sidecar repeats for the same files, read back to back on resume, still trip the breaker on every invocation, now with real calls. That comes from ADR-181's read order, not from caching. No record has found such a file. It is under "What this does not decide".

**ADR-071's timeout streak** now sees a Docling-reported timeout only from a call made in this invocation, just as it always saw the client's own timeouts. A run of cached timeouts can no longer flip the reading to service scope without a call being made.

**ADR-140 §2 is untouched.** The drain, its order and both counters are as they were. An occurrence whose old row is passed over is dispatched to a worker like any miss (`ConversionDispatch` files it as a call, not as an answer), so ADR-140 §3's rule that a worker only makes the call still holds.

### 5. ADR-139's resolution is unchanged in mechanism and now holds its own evidence

`ExtractionFaultRecorder` holds every service-scope failure of the step and, if the step completes, resolves each into `extraction-failed`. Its listener order, its transaction and its reason are as they were. What changes is what the faults are made of. Each one is a refusal the sidecar gave during this invocation, on the drain between this invocation's neighbours. So ADR-139 §3's inference, that the sidecar answered for the neighbours, is drawn from the neighbours it names. ADR-140 §5's caveat for `capacity` stands as written: eight calls in flight can still provoke a refusal. What this record changes is that a provoked refusal no longer outlives the run it was provoked in.

### 6. ADR-140 §5's retune path, as it now reads

**"Discard stage 2's rows for the run and run it again" now reaches the sidecar** for every occurrence that was an extraction fault. Under ADR-181 the smallest discard that does it is the `finished_step` row of the `extraction` step under that run. ADR-181 §1 then deletes the fault rows and their resolving verdicts and reads exactly the faulted occurrences, so the retune converts only those.

**What the sentence still leaves out, and this record does not decide**: a later stage's run id names stage 2's run (`StageRuns.contentCensus`, `pipeline/StageRuns.java:134-145`), and its finished step is not touched by that discard. So stage 3 and every later stage, already finished, do no work, and do not see what the re-run converted. A retune that reaches the deliverable also has to discard the completion of every later step under those runs. No record says how ([#386](https://github.com/algernon28/vespera/issues/386)). See "What this does not decide".

## Consequences

- **A refusal the converter blamed on itself lasts only as long as the invocation that heard it.** Every re-run, resume, retune and replay asks again. A document wrongly removed because of the sidecar's state comes back as soon as the sidecar answers for it.
- **A document the converter blamed itself for is asked about again on every new stage-2 run**, at the cost of one call, where before it cost none. That is the price, and it is bounded by the number of extraction faults, which `extraction_fault` counts per run.
- **The seed side gains the same.** A seed the converter refused for a service-scope reason was an unusable seed for as long as its row lasted (ADR-083). It is now asked again by the next seed-extraction run that reads it. A seed run already finished does no work (ADR-115), so this reaches a seed only under a new seed run, which this build's replay provides.
- **The relevance report's preview** (`RelevanceReportTasklet`, ADR-152) reads through `DoclingExtractor.cached`, so it never shows an old refusal row as a document's opening. It would show nothing for such a row anyway, and the preview only samples survivors, which a refused occurrence is not.
- **`CONTEXT.md`'s Extraction cache entry** says what the cache keeps: answers about the content, never an answer the converter blamed on itself. It is amended in the same change as this record.
- **Comments that state the old rule change with the code** (ADR-077):
  - `ExtractionCache`'s class javadoc and `put`;
  - `DoclingExtractor`'s class javadoc, `convert`, `cached` and `remember`;
  - `ConversionDispatch`'s class javadoc, which says a hit is filed already complete;
  - the comment above `extraction_cache` in `schema.sql`, which says re-running the same content *"never issues a second HTTP call"*;
  - `ConverterStopsPartwayBeans`'s javadoc (*"The answers are also cached"*);
  - `ExtractionResumeInvocationTest.aStageWhoseEndOfStepWasLostDoesTheFaultAgainAndRecordsItOnce`'s javadoc (*"with it in place, the faulted document would be judged again from its cached refusal"*).

  The last two are tests, and their claims do not change.
- **`ExtractionSchema.VERSION` does not move** (§3).

## Tests

**`ServiceScopeAnswersAreNotCachedTest`** (`src/test/java/io/algernon/vespera/extraction/`) uses the real `DoclingExtractor` over the real `ExtractionCache`, against the shipped schema, with a stubbed document service that refuses any request beyond the count it is allowed:

1. A service-scope answer (`capacity`, `internal`, `target_unavailable`, `capacity` overriding `policy`) and a Docling-reported timeout, each converted twice under one extractor identity: the stub sees two calls, and no row is stored.
2. The same answers handed to `remember`, the stage-2 reader's write: `cached` finds nothing.
3. A conversion, a `partial_success` carrying a `timeout` error, and every document-scope failure (`backend_failure`, `unknown`, `policy` alone, a failure with no error), each converted twice: the stub sees one call, and the second answer is the stored one. `remember` followed by `cached` serves each one.
4. A service-scope row written as an earlier build wrote it, followed by a conversion: the conversion reaches the sidecar, replaces the row, and is served on the next read without a call.
5. The same row, with the sidecar refusing again: the call reaches the sidecar, nothing is thrown, and the next read asks again.
6. The same row, met the way stage 2's reader meets it: `cached` finds nothing, and `remember` of the conversion that a worker then fetched replaces the row without throwing, after which `cached` serves it. A plain `INSERT` on that path would throw a primary-key violation inside the chunk, which is not skippable, so stage 2 would fail on every invocation.
7. One INFO line is written when an old row is passed over, naming the content hash and the category.
8. The precedence of §1, each case once, through `convert` twice: `unknown` beside `capacity` and `backend_failure` beside a reported `timeout` are asked again; `backend_failure` beside `capacity`, `source_unavailable` alone and `inference_failure` are served. The cases are where a reading copied rather than moved would most likely differ.

Tests 1, 2, 4, 5, 6 and 7 fail today, and so do the two cases of test 8 that expect a second call. Test 3 and the three cases of test 8 that expect one call pass today, and have to go on passing: they are what stops an implementation from caching too little.

**`ServiceScopeRefusalInvocationTest`** (`src/test/java/io/algernon/vespera/pipeline/`) drives whole invocations over `ConverterStopsPartwayBeans`. Between invocations it scripts the converter afresh, so every document it is asked about now converts: the sidecar has recovered. It leaves `extraction_cache` as the first invocation left it.

1. **A new stage-2 run.** One document the converter blames on itself and one it cannot convert; a commit to `extraction` then mints a new run over the same walk. The new run asks the converter about the refused document and nothing else. The refused document is measured and not removed. The document the converter could not convert is removed again, from its cached answer: re-asked, it would have converted, so its removal shows it was served from the cache. Fails today: the converter is asked nothing, and the refused document is faulted again.
2. **A resume over five refusals spread through the stage.** The first invocation completes with five extraction faults, never two chunks' worth in a row, so the breaker never trips. Its completion record is then deleted, which is ADR-181's lost-end-of-step state and the smallest form of ADR-140 §5's retune (§6). The resume asks the converter about those five and nothing else, completes, and records no fault. Fails today: the five cached refusals reach the drain back to back, the breaker trips with the converter never asked, and the invocation fails.
3. **A replay over a row an earlier build stored.** The first invocation converts everything. One converted document's cache row is then rewritten, with SQL, into a `capacity` refusal as an earlier build stored it, and a commit to `extraction` mints a new run. The new run asks the converter about that document and nothing else, replaces its row with the conversion, measures it under the new run, and completes. This is the path an upgrade takes: `ConversionDispatch` looks the row up through `cached` and writes through `remember`. Fails today: the old row is served, so the converter is asked nothing and the document is faulted.

`ExtractionResumeInvocationTest` is unchanged and stays green. Every one of its tests empties `extraction_cache` between invocations, so none of its counts depends on what this record changes.

## What this does not decide

- **A resume over five or more refusals the sidecar repeats for the same files.** After any stop partway through the stage, ADR-181's read order puts the faulted occurrences of committed chunks first and back to back (Context), and the breaker trips on every invocation of that run (§4). Under ADR-181's discard-and-redo predecessor they were read between conversions, as on the first pass. **The operator's only way out today is a new stage-2 run id**: a changed degeneracy floor, a changed extractor identity, or a new build that moves `extraction`'s implementation version. Under a new run every survivor is read in its place, as on a first pass. Whether a resume should read faulted occurrences in their place among the rest, or should not feed them to the breaker, is [#385](https://github.com/algernon28/vespera/issues/385). No real file has been seen to refuse this way.
- **A retune procedure.** §6 says what discarding stage 2's rows leaves untouched downstream. How an operator retunes, by command or by documented query, is its own decision: [#386](https://github.com/algernon28/vespera/issues/386).
- **A `partial_success` carrying a service-scope error**, such as pages refused for `capacity`. It is a conversion under ADR-070's pass-through and stays cached. Whether a lost page should be told to the operator was #327, closed as not planned. This record does not reopen it.
- **The rest of #320.** Only the classification moves here (§1).
