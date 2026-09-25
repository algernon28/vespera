# ADR-152 — A survivor whose file will not open is still asked about, without its opening, and a step that records completion stops instead

- **Date**: 2026-09-25
- **Status**: accepted
- **Extends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §9, and the rule `GenerationTasklet.openingChunkOf` states in its javadoc. A file the archive will not open is a fact about that document. It is not a fault in the run. This record applies that rule to stage 5's labelling page, and records where the rule stops.
- **Keeps**: [ADR-050](0050-the-pipeline-has-exclusive-access-to-the-corpus.md) (the walk carries no concurrency defences), [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) as amended by ADR-149 §10 (6b checks no original and records no verdict for a missing one), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) (a finished step does no work, and an unfinished one is continued under its own id), [ADR-118](0118-the-answers-a-person-gave-never-join-a-runs-identity-so-the-two-steps-that-read-them-record-no-completion.md) (the labelling step records no completion), and [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) (the stratified sample, drawn from the run id).
- **Defers to**: [#287](https://github.com/algernon28/vespera/issues/287), being settled as ADR-151. Whether a survivor's extraction hash is recorded, so that nothing after stage 2 has to read the file to find it, is that ticket's question and not this one's.
- **Rests on**: a reading of every step between stage 2 and 6b at commit `6f16ccc`, with the call sites named in §4. No real corpus was run for this record. The invocation tests that ship with it are the execution.
- **Settles** [#289](https://github.com/algernon28/vespera/issues/289).

ADR-150 and ADR-151 are not skipped by mistake. ADR-149 §9 reserves them for [#286](https://github.com/algernon28/vespera/issues/286) and #287, and neither had landed when this was written.

## Context

Stage 5's last step writes the labelling page and the label file (ADR-088). For each sampled survivor, the page shows the first 400 characters of its extracted text. `RelevanceReportTasklet.textOpeningOf` found that text by hashing the file again (`DoclingExtractor.contentHashFor`) and reading the extraction cache under that hash. Hashing reads the whole file. If the file had been locked, had lost its read permission, or had been moved since stage 2 converted it, the hash threw `UncheckedIOException`. Nothing caught it, so the step failed. The page and `relevance-labels.yaml` were not written, and 6a and 6b did not run in that invocation.

**Why this step and not an earlier one.** The walk records only what a directory listing shows: path, size, modified time and creation time (ADR-115). It never opens a file. A file that is locked by another program, or whose permissions have changed, is therefore the same observation as before. Census keeps the earlier walk, and every step that has recorded its completion under that walk's runs is skipped (ADR-115, ADR-116). The labelling step is the one step between stage 2 and 6b that records no completion (ADR-118). It runs again on every invocation, so it is the first to hash the file again, including in invocations where nothing else in stage 5 runs. That includes invocation 3 of the operator's path, the one that brings the answered label file back. A file deleted between invocations does not reach this step at all, because the walk sees a different observation, mints a new walk, and the file stops being a survivor.

**The same method had a second problem.** It read the text through `SeedConversions.convert`, which converts on a cache miss. If a survivor's bytes had changed while its size and modified time stayed the same, its hash missed the cache, and the labelling step made a live Docling call to fill a 400-character preview.

**The project's rule** is that a bad row fails the occurrence and not the run, and a run is stopped only for faults that spoil every row. 6b already follows it for this case: `openingChunkOf` and ADR-149 §9's picture chain catch the exception, log a warning naming the file, and continue.

**ADR-050 is not contradicted.** It removes concurrency defences from the walk and says that a violation of exclusive access degrades quietly. It does not forbid a later step from tolerating a file that will not open, and 6b already does. What it does settle is that this record is not a promise of consistency against a live archive. A tolerated file is reported, not reconciled.

## Decision

### 1. The page shows a stated fallback for that survivor, and a warning names the file

When the file of a sampled survivor cannot be opened while the page is written, its preview reads:

> (the file could not be opened when this page was written, so its opening is not shown)

This is distinct from `(no text was extracted)`, which still means that the document's conversion carried no chunk. The two are different facts: one is about the document, the other about the archive at one moment. A reader who sees the first knows to open the original once it is available again, and knows the document had text.

The step logs one warning for each such survivor. The warning names the occurrence and the file as resolved under the corpus root.

**The catch is narrow.** Only the `UncheckedIOException` from hashing the file is tolerated. Anything else the step meets still fails it.

### 2. The survivor is still asked about

It stays in the sample, on the page, and in `relevance-labels.yaml`, with its path and its winning seed as before.

- **The question is still meaningful.** The score was computed from the cached conversion, so the document was judged on its content, and a label is a fact about the document (CONTEXT.md, *Relevance label*). Its file being unavailable today changes neither.
- **Dropping it would bend the sample.** The sample is drawn from the run id, so it is the same sixty documents on every invocation (ADR-088). Leaving one out when its file cannot be opened would make the page and the label file differ between two invocations of one run, according to the state of someone else's program.
- **Nothing is sealed by this.** The step records no completion (ADR-118), and it rewrites both files on every invocation. The next invocation in which the file opens shows its opening again, and no row anywhere remembers that it once did not.

The page, the label file, the profile's pointer to the page, and the steps after this one (6a and 6b) all go ahead in the same invocation.

### 3. The page reads the extraction cache and never converts

The preview is read from the extraction cache under the file's hash, and from nowhere else. The labelling step never places a conversion. If the cache holds no conversion for that hash, which under one run chain means the file's bytes have changed since stage 2 converted it, the preview reads:

> (no conversion is on record for the file as it is now, so its opening is not shown)

The step logs a warning naming the file in that case too. The stated reason is a claim about the cache, which is all the step knows. It does not say that the file has changed.

Stage 2 is the stage that converts a corpus survivor. A page for a person to read is not a place to spend a sidecar call, and on a changed file that call would convert bytes no score was computed from.

### 4. The steps that re-read the archive after stage 2, and what each does

Every step that runs after stage 2 and before the end of 6b was read for archive access at `6f16ccc`.

| Step | Where it reads the archive | What for | Records completion | Decision |
| --- | --- | --- | --- | --- |
| content census (3) | nowhere | | yes | not affected |
| redundancy signatures and resolution (4) | nowhere | | yes | not affected |
| seed extraction (5) | `SeedExtractionItemProcessor.doProcess` | a **seed's** first conversion, not a re-read of the archive | yes | out of scope (below) |
| seed–corpus comparison (5) | nowhere | | yes | not affected |
| embedding scoring (5c) | `EmbeddingScoringTasklet.rechunkAndEmbed` (survivors and seeds) | the hash that keys the cached conversion and the chunks it embeds | yes | **stops the run** |
| relevance scoring (5d) | `RelevanceScoringTasklet.execute` (survivors) and `seedContentHashes` (seeds) | the hash that finds each document's vectors | yes | **stops the run** |
| relevance floor (5e) | nowhere | | yes | not affected |
| clustering (5f) | `ClusteringTasklet.contentHashesOf` | the hash that finds each member's vectors | yes | **stops the run** |
| labelling page (5) | `RelevanceReportTasklet.textOpeningOf` | a 400-character preview | **no** (ADR-118) | **tolerates** (§1 to §3) |
| arrangement (6a) | `ArrangementTasklet.titleOf` | the lead document's title, which the cluster label is derived from | yes | **stops the run** |
| generation (6b), the call | `GenerationTasklet.openingChunkOf` | an exemplar's opening chunk | per cluster (ADR-111) | tolerates already; unchanged |
| generation (6b), the tree | `GenerationTasklet.hashOf` | a listed survivor's pictures (ADR-149 §9) | as above | tolerates already; unchanged |
| generation (6b), the manifest | nowhere (`corpus`'s `content_hash` table) | | | not affected (#287) |
| arrangement (6a), the page's link | `ArrangementTasklet.linkTo` (`Path.toUri`) | composes a link without reading the file | | not affected |

**The line is where a step records completion.**

- **A step that records its completion and uses the file for what it records stops the run.** Embedding scoring, relevance scoring, clustering and the arrangement each write rows under a run id and then record that step as finished (ADR-116). If one of them skipped the survivor it could not hash, the vectors, the score, the cluster membership or the cluster label would be missing or different under a run recorded as finished. Every later invocation would walk past the step (ADR-115), so the gap would never be closed, and there is no verdict to record it as (ADR-104). ADR-121's permanent hole is the precedent this refuses to repeat. Stopping costs one re-run once the file opens. The walk is the same, so the run ids are the same, the unfinished step is continued under its own id, and everything it redoes is a cache hit on material keyed outside the run (ADR-115). The failure already names the file: `ContentHashing` throws `could not hash <path>`. No change to these four steps is owed.
- **A step that records no completion, and uses the file only for what a person reads, tolerates it.** The labelling page is the only such step. Its use of the file is the preview, and the page is rewritten on every invocation, so a tolerated failure is corrected by the next invocation in which the file opens.
- **6b already tolerates, and is unchanged.** Its tolerance predates this record, and its own repair pass (ADR-111) is what keeps a dropped exemplar from being sealed. A cluster whose every document cannot be opened is left unwritten and the step stays unfinished, as ADR-111 and ADR-121 already say.

**Out of scope: a seed whose file will not open.** `SeedExtractionItemProcessor` hashes a seed before converting it, so a seed file that will not open throws out of seed extraction and fails that step, where ADR-083 would record an unusable seed. That is a first read, not a re-read of the archive after stage 2, and it is filed separately rather than decided here.

### 5. The recorded extraction hash is #287's question

A survivor's extraction hash recorded at stage 2 would let this step read the cache without opening the file at all, and would make §1's fallback unreachable for any survivor with a recorded hash. No table records it today: `extraction_metric` carries no hash, and `extraction_cache` is keyed by one. Adding storage for it is exactly what #287 is deciding. This record does not pre-empt that ticket. If ADR-151 records the hash, the labelling step should read it instead of hashing the file, and §1 and §3 remain as the fallback for a survivor with no recorded hash.

## Consequences

**One file that will not open no longer costs an invocation its labelling page, its label file, 6a and 6b.** It costs that document's preview on the page, and one warning.

**A step that records its completion still stops on such a file.** The reason is recorded here rather than left to be rediscovered. The operator's remedy is the same in every case: release the file and run the same command again.

**The labelling step no longer calls Docling.** A survivor whose bytes changed since conversion gets a stated fallback instead of a conversion nothing asked for.

**The page can differ from one invocation to the next for one reason only**: whether each sampled survivor's file opened when that invocation wrote it. The sample, the questions and the label file's entries do not change.

**What the implementation owes** (`spec-implementer`):

- `RelevanceReportTasklet.textOpeningOf` catches the `UncheckedIOException` from `extractor.contentHashFor(file)`, logs a warning naming the occurrence and the file, and returns §1's fallback, held as a constant beside `TEXT_OPENING_CHARACTERS`.
- It reads the cached conversion with `extractor.cached(contentHash, extractorIdentity)` and chunks that response. It no longer calls `SeedConversions.convert` (whose other read, `BrokenCheck.check`, goes with it). On an empty result it logs a warning naming the file and returns §3's fallback.
- `SeedConversions`' javadoc stops naming the relevance report among its three callers.
- `AGENTS.md`'s count of decisions is raised by one. That is the claims guard's line (ADR-129).

**What pins it**, in `RelevanceReportInvocationTest`:

- A sampled survivor's file is made impossible to open between two invocations of one walk: a held lock on Windows, every permission removed elsewhere. The invocation must succeed, both files must be written, the survivor must still be asked about with §1's fallback shown, the other survivor's opening must still be shown, a warning must name the file, and the arrangement step must be reached. The test is guarded: where the file can still be read, as it can by a superuser, it aborts by assumption.
- A survivor's bytes are rewritten in place with its size and modified time kept, so the walk is unchanged, and the next invocation must succeed without adding a conversion to the cache (§3). This one runs everywhere.
- Embedding scoring meets a file that will not open while its step is unfinished. The invocation must fail, and once the file opens the next invocation must succeed and write the page (§4). Guarded the same way as the first test.
