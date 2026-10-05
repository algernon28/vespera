# ADR-192 — Every loop in every stage reports its progress, and no counter floods the log

- **Date**: 2026-10-05
- **Status**: accepted
- **Amends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md), its section *"Stage progress is reported on a percentage/count cadence, where a denominator exists"*, in its cadence and nothing else. Its sentence *"that stage logs a progress line at INFO whenever it crosses **5% or 1,000 items, whichever comes first** since the last report"* now reads: whichever comes first, except that over a total of more than 100,000 items a line is written every 1% of the total, so that no counter over a known total writes more than 100 lines. Its sentence that the walk *"logs a running count only"* now reads with a cadence: a running count writes a line every 1,000 items below 100,000, and from there every tenth of the power of ten at or below the count reached (§8). The line `<label>: N of M (x%)`, its INFO level, and everything else in ADR-093 stand.
- **Amends**: [ADR-190](0190-stage-6bs-loop-and-6as-lead-document-rule-live-in-synthesis-which-still-knows-no-stage.md) §2 and §4. §2's `GenerationProgress` gains two methods with default bodies, `toGoThrough(long clusters)` and `clusterGoneThrough()`; its four methods and their meaning stand. §4's *"**Already written**: counted in `alreadyWritten`, with no call to `exemplars`, no report and no effect on the streak"* now reads *"… with no call to `exemplars`, no report of the four kinds, a call to `clusterGoneThrough()`, and no effect on the streak"*; every other branch of §4 also ends in one call to `clusterGoneThrough()` (§5 here). Nothing else in ADR-190 changes.
- **Extends**: [ADR-188](0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md) §2, whose `HashingProgress` gains three methods with default bodies, and [ADR-189](0189-stage-2s-judging-rules-and-the-extractor-identitys-composition-live-in-extraction-which-still-knows-no-stage.md) §2, whose `ExtractionFaultResolution.resolve` gains an overload that reports each fault resolved (§5).
- **Rests on**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (a capability module knows no stage and logs no stage's line), [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md) (the walk's checkpoint, which this does not move), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which module moves which run id), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (a capability module is handed what it cannot name, and hands back through a callback it owns), [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) (a converter worker runs the call and nothing else), [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) §2 (6a draws its page on both branches), ADR-188, ADR-189, ADR-190 and [ADR-191](0191-stage-3-says-how-many-shingle-rows-it-is-about-to-read-and-how-long-measuring-them-took.md) (where the code now stands, and what each already moves in run ids).
- **Settles** [#412](https://github.com/algernon28/vespera/issues/412), under the operator's decisions recorded there on 2026-10-04 and 2026-10-05, except for the statements §7 leaves to [#411](https://github.com/algernon28/vespera/issues/411).

**Three words here are the record's own.** A *counter* is one `StageProgress` over one loop. A *summed* counter is one counter over an inner loop whose total is added up across every item of its outer loop before the outer loop starts. A *running* counter has no total and states `N so far`.

## Context

### What happened

On the whole-archive run of 2026-10-04 (build `11691d0`, working directory on `H:`, a partition of a USB portable spinning disk), stage 5c, embedding scoring, wrote its starting line at 20:12:41, naming 4,275 corpus survivors and 19 usable seeds, and nothing after it until the operator stopped the invocation at 20:42:40. The log could not say whether the stage was at the hundredth survivor or the four-thousandth. Stage 4b's resolution, which #411's comment puts at about 26 minutes 40 seconds after a 39-minute build of `shingle_by_hash`, also wrote a starting line and a finishing line and nothing between. The durations #412's comments list for that run (`census` 34 s, stage 1 14 min 32 s, stage 2 4 h 23 min 7 s, stage 3 31 min 40 s, 4a 13 min 38 s, 4b 1 h 5 min 40 s, 5a 1 min 24 s, 5b 1 min 47 s, 5c over 30 min and not finished) are Spring Batch's own step durations on that disk. Nothing after 5c was reached.

### What the operator decided

On 2026-10-04: every stage gets a progress tracker whatever its duration; every loop is counted, and nothing with a loop is deferred until it has been measured; `similarity` is touched so that stage 4b's resolution reports through a callback; the census gets a tracker that is not silent in a flat root or a single large directory, a change in `corpus`; stage 5b, which has no loop of its own, goes to #411.

On 2026-10-05, on the four points the re-gate of the previous draft left open:

1. A loop inside a counted loop is covered by the outer counter, unless the inner loop is where the outer item's time goes; then it gets its own counter. Stage 5c's call to the embedding model per chunk is the example of the exception. Each nested loop is named with the side it falls on and why (§3).
2. Log volume is capped: a counter over a very large total writes at a wider interval, and the record says how (§8).
3. The archive's next run waits until all of this record has landed, so that the run ids it moves are minted once (Consequences).
4. Loops that only compute over values already in memory are not counted, both ways: the in-memory loops an earlier draft counted are dropped, and `Communities.of` stays uncounted, as an in-memory loop (§1, §7).

Later on 2026-10-05, on two points this record's pre-implementation gate found it had settled without the operator:

5. A line states `N of M (x%)` or `N so far` and nothing else: no throughput and no time left (§8). This answers the ticket's question 4.
6. Two inner loops that decision 1 would give a counter of their own are left to their outer counter, as approved exceptions: stage 2's converter call per part (`TextParts`) and stage 6b's counting calls (§3).

### Where the code stands on main

This record was first drafted against `7d27eaa`. Since then [ADR-189](0189-stage-2s-judging-rules-and-the-extractor-identitys-composition-live-in-extraction-which-still-knows-no-stage.md) moved stage 2's end-of-step resolution of extraction faults into `extraction.ExtractionFaultResolution`, and ADR-190 moved stage 6b's walk over a run's clusters into `synthesis.ClusterGeneration.write`, with `synthesis.GenerationProgress` reporting only the clusters the walk leaves unwritten (four abstract methods, implemented by `pipeline.GenerationTasklet.progressLines()` and by `ClusterGenerationTest`), and stage 6a's lead-document rule into `synthesis.LeadDocument.of` and `labelled`. ADR-188 had already moved stage 1's passes into `corpus`, reporting through `HashingProgress` and `CheckingProgress`. Stage 5f's pass over pairs of blocks is `embedding.NearestNeighbourGraph.build`, a package-private class `Clustering.clusterAndRecord` calls. Every reference below is to the code on main at `f8f9a18` (no `src/main` change since `b99272b`).

### What the ticket was filed under

#412's constraints said that *"Nothing here should touch `similarity`, which would also replay stage 2"*: the ticket was filed on the premise that `similarity` stays untouched. Two things removed what that premise spared. ADR-188 moves stage 1's run id through `corpus`, and with it every later stage's. ADR-191 put code in `similarity`, which is in stage 2's implementation version. So a database upgraded across them replays stage 2 whatever this record does, and the operator decided on 2026-10-04 that `similarity` is touched.

## Decision

### 1. What is counted

**A loop is counted when its body reads or writes the database, reads or writes a file, or calls the embedding model, the generation model, or the converter.** Each counted loop has its own counter (§4), on `StageProgress`, which `pipeline` alone holds and which alone writes the line.

Four kinds of loop are not counted. §7 names every one found of the last three kinds, and of the first only examples:

- **A loop that only computes over values already in memory** (the operator's decision 4): sorting, grouping, string building, arithmetic over vectors or hashes already read. Such a loop is not counted even where it is long, `Communities.of` among them.
- **A loop over a constant number of items**, set by the code or by a record's shape and not growing with the archive.
- **A statement**, or the row callback of one, or a drain of a reader into memory: one call that SQLite answers, with no loop of the stage's own inside it. These are #411's.
- **An inner loop left to its outer counter** under §3.

**How the list was made complete, and what it is complete over.** Every `for`, `while`, `do` and `forEach` in `src/main/java` under `corpus`, `extraction`, `similarity`, `embedding`, `synthesis` and `pipeline`, 257 of them at `f8f9a18`, was read, and so was every stream there whose lambda reads, writes or calls. **Each loop whose body, or a method its body calls, reads or writes the database or a file, or calls the embedding model, the generation model or the converter, is in §4, §3 or §7**, including the ones outside the job's fifteen steps (§7, last item); one in none of them was missed, and is a defect of this record. **A loop that only computes over values already in memory is not counted as a class**, and is not listed one by one: §7 names such loops where an earlier draft counted one, where a gate asked, or where the loop sits beside a read and could be taken for one, and a loop of that kind §7 does not name is not a gap in this record.

### 2. What one item is, and when it is counted

**An item is one pass of the loop's body, counted when that pass ends, on every path out of it but one that fails the step.** `StageProgress.itemDone()`'s rule, *after the item is finished rather than before*, is kept. In 4b's containment loop an occurrence that phase one removed, or that has no rare shingle to look for, is counted. In 5f a partition the floor emptied is counted. In 6b a cluster is counted whether it was already written, had no sendable document, had nothing that fits the window, had its answer turned down, had no document the engine counts inside the room, or had its answer believed; the cluster whose turned-down answer is the fifth in a row is counted before the walk returns `Stopped`. A stage that stops leaves its counters short of their totals, and that is the counters being right. A pass that throws and fails the step is not counted: the step's own failure line says what happened.

**A loop's unit is its own.** 5c counts occurrences, and the chunks of each occurrence are a second, running counter (§3), not a change of unit. No counter counts transactions: each tasklet counted here runs its work inside one Spring Batch transaction (`TaskletSteps`), and the census commits at its checkpoints, so a commit counts nothing.

### 3. A loop inside a loop

**The operator's rule (2026-10-05, 1): an inner loop is covered by its outer counter, unless it is where the outer item's time goes; then it gets its own counter.** The reasons that decide the side are what one inner item does (a call to the embedding model, the generation model or the converter, a file read, a database read or write) against what the outer item does outside the inner loop, and whether the inner count is bounded by a constant of the code. Where the inner loop is given its own counter, its total is summed across the outer loop's items when it can be known before the outer loop starts, and it is a running counter when it cannot.

**The other way round:** an outer loop all of whose reads and writes happen inside counted inner loops has no counter of its own; its inner counters cover it. Stage 1's loop over size groups, and its loop over the hash groups of one size, are the two such loops.

Every nested loop found, and its side:

| Outer loop (counter) | Inner loop | Side | Why |
|---|---|---|---|
| Census, each entry walked (running) | `WalkRecorder.writeBuffered`, a row insert per buffered entry at a taken checkpoint | covered | The rows are the entries the running count already counted when they were walked. In a flat root they are all written at the end, after the last running line and before the finishing line, and how long that takes for a large flat directory is not measured (Not known). |
| Stage 1, size groups (none: covered by its inner counters) | the hashing loop, a file read per member | own, summed (existing, ADR-188) | A whole-file read per member is the group's work. |
| Stage 1, hash groups of one size (none) | the superseded loop, two writes per duplicate | own, running | It is the hash group's only database work, and how many duplicates there are is known only when every size group has been hashed. |
| Stage 1, broken check (existing) | `BrokenCheck` and `TimestampedLines` over a file's prefix; `BrokenCheck.readSuffix`'s read loop; `BrokenOrOutOfScope.leadingBytesOf`'s rendering, as hex, of an unrecognised file's first bytes | covered | The reads are of a prefix or suffix of fixed length, once each, and the loops over what they read are in memory. |
| Stage 2, each occurrence (existing) | `TextParts.convertInParts`, a converter call per part | covered, and flagged | Each part is a converter call, which makes it the occurrence's time by the rule. It is not given a counter because it runs on a converter worker, where ADR-140 lets nothing but the call run and where `StageProgress` (single-threaded by its own Javadoc) may not be used. A file converted in parts is at most 64,000,000 bytes, cut at line ends into parts of at most 8,000,000 bytes each. Stage 2's per-occurrence lines still bracket it. An exception to decision 1, approved by the operator (decision 6). |
| Stage 2, each occurrence | `Shingler`'s inserts, one statement per 5,000 hashes | covered | One batched insert into the chunk's open transaction, against the occurrence's conversion. |
| Stage 2, each chunk's writer | `ExtractionItemWriter`'s loop over one chunk | covered | At most `CHUNK_SIZE` items, each already counted by the processor. |
| 4a, each occurrence (existing) | MinHash permutations, and `RedundancySignatures.write`'s insert per band row | covered | A constant number per occurrence (the signature's parameters). 4a's counter is ticked in `RedundancySignatureItemWriter.write`'s loop over a chunk, which is the loop it counts: unlike stage 2's writer, that loop is 4a's outer loop, not a nested one. |
| 4b, near-duplicate components | the per-member verdict loop: a Jaccard merge, a shingle-set read where the cache evicted it, a `verdict` row and a `redundant_with` row | own, summed | Outside it a component only chooses its survivor in memory and reads that one set, so the member loop is where the component's time goes. |
| 4b, containment | the per-candidate loop: a `COUNT(*)` once per candidate across the pass, and a shingle-set read where the cache does not hold it | own, running | Outside it an occurrence costs one set read and one `GROUP BY` statement, and inside it there are up to two reads of the same kind per candidate. How many candidates each occurrence finds is known only from its statement, so no total exists before the containment loop starts. |
| 4b, any loop | `ShingleSetCache.load`'s row callback | covered | One statement per set read, inside a counted item. |
| 5c, corpus survivors | the chunk loop, a call to the embedding model per chunk | own, running | The operator's example. A survivor's chunks are known only once it is chunked. |
| 5c, seeds | the same, over a seed's chunks | own, running | The same reason. |
| 5c, corpus survivors and seeds | `ChunkCache.put`, an insert per chunk, which `HybridChunker.chunk` makes on a cache miss before it returns the chunks | covered | One insert per chunk into the step's open transaction, made once per content hash and chunking rule, against the call to the embedding model the same chunk then gets, which the running chunk counter counts. `HybridChunker` is otherwise in memory (§7), and this is its one write. The relevance report's sampled survivors reach the same `chunk` call, for a survivor 5c chunked, since 5c chunks every survivor it scores, so the call finds the chunks cached and inserts nothing. |
| 5d, corpus survivors | `RelevanceScorer`'s loops over chunk vectors | not counted | In memory, over the survivor's vectors already read and the seeds' held resident. |
| 5f, seed partitions | `contentHashesOf`, a file read per member | own, summed | Reading whole files is the partition's largest piece of work that grows with it, after the pass over blocks. |
| 5f, seed partitions | `NearestNeighbourGraph.build`'s pairs of blocks: two block reads and every comparison between them | own, one counter per partition | The N²/2 comparisons ADR-085 priced. The number of blocks depends on the vector width, read inside the partition, so the total is the partition's own. |
| 5f, a pair of blocks | `StoredMeanVectors.block`, a vector read per document | covered | At most `Clustering.BLOCK_DOCUMENTS` (4,096) reads per block, against that pair's comparisons. How long a block's reads take on `H:` is not measured (Not known). |
| 5f, seed partitions | `Communities.of` | not counted | In memory. |
| 5f, seed partitions | `Clustering.clusterAndRecord`'s loop recording each member's cluster, an insert per member | covered | One insert per member in the open transaction, against the partition's file reads and its N²/2 comparisons. The previous draft's `members recorded` counter is dropped for this reason. |
| 6a, clusters | `LeadDocument.labelled`'s scan of the membership | not counted | In memory. Its title and path lookups are one each per cluster, not loops. |
| 6a, page rows | `LeadDocument.of`'s scan | not counted | In memory. |
| 6b, clusters | `GenerationTasklet.exemplarsOf`, called lazily through `ClusterExemplars`: a whole-file read to hash each member and a chunk-cache read | own, running | A cluster's calls to the serving engine do not grow with its size and these reads do, so for a large cluster they are where its time goes. Which costs more for the archive's clusters is not measured. |
| 6b, clusters | `ClusterSynthesis.sentAfterCounting`'s counting calls | covered, and flagged | A search whose next question depends on the last answer: one call where the proposal fits, at most 1 + ⌈log2 n⌉ for a proposal of n documents, and a new proposal for each document passed over. Whether these calls cost more than the cluster's one answering call on the archive is not known. An exception to decision 1, approved by the operator (decision 6), to be revisited once a real run shows what these calls cost. |
| 6b, clusters | `SynthesisDocs.record`'s insert per document the call carried | covered | Bounded by what the window holds, in the open transaction, against the cluster's calls. |
| 6b tree, partitions written | cluster files written | own, summed | The files are the partition's work beyond one directory creation. |
| 6b tree, cluster files written | membership entries, each asked for its pictures through `SurvivorPictures` (a detected-format read, a cache read and a decode) and writing up to ten picture files | own, summed | Outside it a cluster file is string building and one file write. |
| 6b tree, membership entries | the picture files written for one entry | covered | At most `Deliverable.PICTURES_PER_DOCUMENT` (10). |
| 6b tree, pictures listed | each picture's digest and difference hash | not counted | In memory, over pixels just returned. |

### 4. The counters

Every counter is a `StageProgress` made and ticked in `pipeline`. Where the loop is in another module, that module tells `pipeline` the total and each completion through a callback (§5). A label names the stage as its own lines do and, after a comma, the unit. The five counters that exist today (stage 1's broken check and content hash, stage 2, 4a and 5a's processor) stand as they are.

| Stage | Label | One item | Total | Loop in | Part |
|---|---|---|---|---|---|
| 0 | `Stage 0 (census)`, the existing line `… N entries walked so far, D directories entered, under walk W` | an entry walked | none: running, and written by `corpus` (§6) | `corpus` | a |
| 1 | `Stage 1 (byte-level reduction, sizes read)` | a survivor whose recorded size is read to group it | the survivors the pass drained | `corpus` | a |
| 1 | `Stage 1 (byte-level reduction, duplicates recorded)` | a duplicate recorded as superseded and verdicted | none: running | `corpus` | a |
| 2 | `Stage 2 (extraction, faults resolved)` | a held fault written, and verdicted where the step completed | the faults held | `extraction` | b |
| 3 | `Stage 3 (content census, frequency rows)` | a distinct (granularity, hash) gone through, a row written where it was seen in two or more surviving documents | the distinct hashes counted in memory after the read | `similarity` | b |
| 4b | `Stage 4b (redundancy resolution, near-duplicate candidates)` | a candidate pair scored | the pairs the signature buckets formed | `similarity` | b |
| 4b | `Stage 4b (redundancy resolution, occurrence profiles)` | an occurrence whose facts are read for the survivor rule | the members of components of two or more | `similarity` | b |
| 4b | `Stage 4b (redundancy resolution, near-duplicate components)` | a component of two or more resolved | those components | `similarity` | b |
| 4b | `Stage 4b (redundancy resolution, near-duplicate verdicts)` | a member written as redundant with its component's survivor | those members less one survivor per component, summed | `similarity` | b |
| 4b | `Stage 4b (redundancy resolution, containment)` | a signed occurrence checked for a container | the signed occurrences | `similarity` | b |
| 4b | `Stage 4b (redundancy resolution, containment candidates)` | a candidate gone through, for any signed occurrence | none: running | `similarity` | b |
| 5a | `Stage 5a (seed extraction, seeds recorded)` | a seed's rows written after the step | the seeds the step read | `pipeline` | c |
| 5c | `Stage 5c (embedding scoring, corpus survivors)` | a survivor re-chunked and embedded | the survivors | `pipeline` | c |
| 5c | `Stage 5c (embedding scoring, corpus survivor chunks)` | a chunk embedded | none: running | `pipeline` | c |
| 5c | `Stage 5c (embedding scoring, seeds)` | a usable seed re-chunked and embedded | the usable seeds | `pipeline` | c |
| 5c | `Stage 5c (embedding scoring, seed chunks)` | a chunk embedded | none: running | `pipeline` | c |
| 5d | `Stage 5d (relevance scoring, seed files hashed)` | a usable seed whose file is hashed | the usable seeds | `pipeline` | c |
| 5d | `Stage 5d (relevance scoring, seed vectors read)` | a seed whose stored vectors are read, found or not | the seeds handed over | `embedding` | c |
| 5d | `Stage 5d (relevance scoring, corpus survivors)` | a survivor scored | the survivors | `pipeline` | c |
| 5e | `Stage 5e (relevance floor, below-threshold verdicts)` | a verdict written | the occurrences below the floor | `pipeline` | c |
| 5f | `Stage 5f (clustering, seed partitions)` | a partition gone through, an emptied one included | the partitions | `pipeline` | c |
| 5f | `Stage 5f (clustering, files hashed)` | a member whose file is hashed | every partition's members after the floor, summed (§5) | `pipeline` | c |
| 5f | `Stage 5f (clustering, comparison blocks, partition P of M)` | a pair of blocks compared | b(b + 1)/2 for the partition's b blocks | `embedding` | c |
| report | `Stage 5 (relevance report, sampled survivors)` | a sampled survivor whose paths and opening are read | the sample | `pipeline` | c |
| report | `Stage 5 (relevance report, answers matched)` | a recorded answer looked up in this walk, matched or not | the answers recorded for the seed set | `pipeline` | c |
| 6a | `Stage 6a (arrangement, scores read)` | a member whose score is read, found or not | the membership | `embedding` | c |
| 6a | `Stage 6a (arrangement, members gathered)` | a membership row turned into arrangement input, its seed's path read on the seed's first member | the membership | `pipeline` | c |
| 6a | `Stage 6a (arrangement, clusters)` | an arranged cluster labelled and recorded | the arranged clusters | `pipeline` | c |
| 6a | `Stage 6a (arrangement, page rows)` | a recorded cluster drawn as a page row, its lead's path read | the recorded clusters | `pipeline` | c |
| 6a | `Stage 6a (arrangement, page partitions)` | a partition whose seed path is read for the page | the partitions on the page | `pipeline` | c |
| 6b | `Stage 6b (generation, scores read)` | a member whose score is read | the membership | `embedding` | d |
| 6b | `Stage 6b (generation, clusters)` | a recorded cluster gone through, on every path | the recorded clusters | `synthesis` | d |
| 6b | `Stage 6b (generation, cluster documents opened)` | a member of a cluster reached whose file is hashed and opening read | none: running | `pipeline` | d |
| 6b | `Stage 6b (generation, survivors listed)` | a survivor whose facts and file hash are read for the tree | the membership | `pipeline` | d |
| 6b | `Stage 6b (generation, pictures listed)` | a distinct listed survivor asked for its pictures | the distinct survivors | `synthesis` | d |
| 6b | `Stage 6b (generation, partitions written)` | a partition's directory and cluster files written | the partitions of the arrangement | `synthesis` | d |
| 6b | `Stage 6b (generation, cluster files written)` | a cluster file written | the recorded clusters, summed | `synthesis` | d |
| 6b | `Stage 6b (generation, membership entries)` | an entry with a document written, its pictures asked for | the members of every recorded cluster, summed | `synthesis` | d |

- **Labels for 6a and 6b are new wording.** Those steps' own lines read `the arrangement step …` and `The generation step finished under …`, and nothing here renames them.
- **The relevance floor** counts only in the state that removes anything; in its other states there is no loop, and its existing line says why.
- **A loop reached with no item** is announced with a total of zero and writes nothing, because `StageProgress` writes only from `itemDone()`.
- **What writes on a step already recorded.** Every step inside `TaskletSteps.once` runs no loop when it is already recorded, and its counters write nothing, except 6a (§9). Four counted loops are not inside `once`: the relevance floor counts its verdicts on every invocation where its threshold applies, since it discards and rewrites them each time (ADR-118); the relevance report counts on every invocation that reaches it; 5a's seed rows are written only when they are not yet recorded; and stage 2's fault resolution runs only where the step held a fault.
- **6b's tree counters run on each of its three exits**, `Stopped`, `LeftUnfinished` and `Finished`, since `writeDeliverable` runs on each.

### 5. How a total reaches a counter

**A capability module tells its caller a loop's total and each completion through an interface it owns, and writes no line**, in the shape ADR-188 gave `corpus`: `HashingProgress.toHash(long)` once, before the first hash, zero included, and `hashed(…)` after each.

**One interface for each looping class, not one for each module and not one shared type.** `similarity` gets two and `embedding` two; `synthesis` gets one new and has one existing interface extended; `corpus` has one existing interface and one package-private observer extended; `extraction` gets one. A shared type would have to live where every module may depend on it, which is `ledger`, and a progress shape is not identity, verdicts or run identity (CONTEXT.md's **Ledger**). A JDK functional type would make each loop an unnamed argument, and `RedundancyResolution.resolve` would take six.

| Interface | Methods | Called from | Feeds |
|---|---|---|---|
| `corpus.HashingProgress` (gains defaults that do nothing) | `toSize(long survivors)`, `sized()`; `supersededRecorded()` | `ContentIdentityResolution.resolve` | stage 1 sizes read; duplicates recorded |
| `corpus.Walk.Observer` (package-private; gains a default that does nothing) | `entryWalked(Walk.Progress progress)` | `Walk`'s visitor, once after each entry it counts | the census line (§6) |
| `extraction.FaultResolutionProgress` (new) | `toResolve(long faults)`, `faultResolved()` | `ExtractionFaultResolution.resolve(RunId, boolean, FaultResolutionProgress)` | stage 2 faults resolved |
| `similarity.FrequencyProgress` (new) | `toGoThrough(long hashes)`, `hashGoneThrough()` | `DocumentFrequency.measure(RunId, RunId, FrequencyProgress)` | stage 3 frequency rows |
| `similarity.ResolutionProgress` (new) | `toScorePairs(long)`, `pairScored()`; `toReadProfiles(long)`, `profileRead()`; `toResolveComponents(long)`, `componentResolved()`; `toWriteNearDuplicateVerdicts(long)`, `nearDuplicateVerdictWritten()`; `toCheckForContainment(long)`, `checkedForContainment()`; `containmentCandidateGoneThrough()` | `RedundancyResolution.resolve(RunId, RunId, RunId, Set<Long>, ResolutionProgress)` | the six 4b counters |
| `embedding.ScoringProgress` (new) | `toReadSeedVectors(long)`, `seedVectorsRead()`; `toReadScores(long)`, `scoreRead()` | `RelevanceScoring.residentSeedVectors(…, ScoringProgress)` and `scoresFor(RunId, Collection<OccurrenceId>, ScoringProgress)` | 5d seed vectors; 6a and 6b scores read |
| `embedding.ClusteringProgress` (new) | `toCompareBlocks(long blockPairs)`, `blockPairCompared()` | `Clustering.clusterAndRecord(…, ClusteringProgress)`, which hands it to `NearestNeighbourGraph.build(MeanVectors, int, int, ClusteringProgress)` | 5f comparison blocks |
| `synthesis.GenerationProgress` (gains defaults that do nothing) | `toGoThrough(long clusters)`, `clusterGoneThrough()` | `ClusterGeneration.write` | 6b clusters |
| `synthesis.DeliverableProgress` (new) | `toListPictures(long)`, `picturesListed()`; `toWritePartitions(long)`, `partitionWritten()`; `toWriteClusterFiles(long)`, `clusterFileWritten()`; `toWriteMembershipEntries(long)`, `membershipEntryWritten()` | `Deliverable.writeTo(…, Map<ClusterSlot, Unwritten>, DeliverableProgress)` | the four 6b tree counters |

**The contract, for every `to…` method:** called exactly once each time its loop is reached, with the loop's total, before the loop's first item, zero included; its completion method called once after each item, on every path out of the item but one that throws. **A method that returns before a loop is reached calls neither.** `RedundancyResolution.resolve` returns before any loop when no occurrence is signed, and then calls none of its methods; `Clustering.clusterAndRecord` returns before the pass when a partition has no member, and then calls neither. `loadOccurrenceProfiles` today returns early on an empty set; with the callback it announces zero first. **A running counter's completion method has no `to…` partner**, and `pipeline` opens the running counter when the loop it sits in is announced: 4b's candidates when `toCheckForContainment` is called, stage 1's duplicates when `toHash` is called.

**The order of the announcements is fixed where one method has several loops**, so the contract tests can hold it. `RedundancyResolution.resolve`: pairs, profiles, then components and verdicts (in that order, both before the component loop), then containment; within a component its verdicts are reported before the component is. `Deliverable.writeTo`: pictures listed first, and the furniture pass done; then partitions, cluster files and membership entries, in that order, all before the loop over partitions; within it each entry is reported after its pictures are written, each cluster file after its entries, each partition after its files.

**`ClusterGeneration.write` calls `toGoThrough(clusters.size())` once, after reading the slots already written and before the walk, zero included, and `clusterGoneThrough()` once at the end of each cluster's path**: already written; no sendable document, after `noSendableDocument`; nothing fits the window, after `nothingFitsTheWindow`; a fault, after its report and its row, and for the fifth turned-down answer before it returns `Stopped`; a believed answer, after the row is written, the fault row deleted and the streak cleared. The two methods have default bodies, so `GenerationTasklet.progressLines()` and `ClusterGenerationTest`'s anonymous implementation compile unchanged.

**A summed counter is announced once, with the sum, and fed by every inner item.** 4b's verdict counter: `toWriteNearDuplicateVerdicts` is called once, before the component loop, with the members of components of two or more less one survivor each, and `nearDuplicateVerdictWritten()` once per member written. The 6b tree's cluster files and membership entries: announced once, before the loop over partitions. 5f's files hashed is in `pipeline` and has no callback: `ClusteringTasklet` asks `membersOf` for every partition and filters each by the survivors before it clusters the first, keeps those lists, sums their sizes into the counter's total, and then goes through the partitions as today, using the list it kept for each, so `membersOf` is still asked once per partition. Its cost is in Consequences.

**A per-partition counter's label says which.** In `Stage 5f (clustering, comparison blocks, partition P of M)`, M is the size of the list `Clustering.partitions(scoring)` returns, and P is the partition's 1-based position in that list. A partition the floor emptied keeps its number and opens no block counter. `pipeline` makes a new `StageProgress` in each `toCompareBlocks` call.

**The old signatures stay**, each calling the new one with an implementation that does nothing, so every caller and test that uses them stands. `pipeline` uses the new ones everywhere a counter is listed in §4.

### 6. The census reports while a directory is walked

**The census has no total, so it is a running count, and `corpus` writes it, as it has since ADR-093.** `WalkRecorder` writes `Stage 0 (census): N entries walked so far, D directories entered, under walk W`, and a finishing line with the totals. On main it writes the running line only from `Session.checkpoint`, after the transaction that commits the buffered rows and the checkpoint (`WalkRecorder.java`, lines 243 to 258), and only when a commit interval of entries has passed; and `Walk` offers a checkpoint only when it finishes a directory, never for the root. So the files of a flat root, or of any one large directory, were walked with no line until the walk ended. Whether the archive's census wrote a running line on 2026-10-04 is not known: its log is on `H:` and was not opened.

**From this record the running line is written from a callback the walk makes after each entry it counts**, `Walk.Observer.entryWalked(Progress)`, at the running cadence of §8 over the walk's cumulative entries. The checkpoint no longer writes it.

**Where `entryWalked` fires in `Walk`'s visitor:** once after each `entriesSeen++`, when that entry's handling is done, with the visitor's `progress()` as it then stands. In `preVisitDirectory`, for a `PENDING` entry, after the soft-link report on that path, and after `directoriesEntered++` and the descent on the other. In `visitFile`, for a `PENDING` entry, at each of its exits after the increment: after the soft-link report, after the not-a-regular-file report, after the stored occurrence, and after the unstorable-path anomaly. In `visitFileFailed`, for an entry that is not the root, after its report. **Never for the root**, which is entered but is not an entry beneath itself, whether it is visited or fails; **never for a `DONE` or `ANCESTOR` entry**, nor for a file that is not `PENDING`, which an earlier session counted. `postVisitDirectory` counts nothing and does not call it. `WalkRecorder`'s session applies the cadence to `cumulative(progress).entriesSeen()`, the earlier sessions' entries added to this one's, so a line falls on every count the cadence names, and a resumed session's first line is at the first such count past the one it resumed from. A checkpoint is still offered at each finished directory, still taken when `COMMIT_INTERVAL` entries have been walked since the last one taken, and still commits the buffered rows with it in one transaction (ADR-055); none of that moves, and **`entryWalked` commits nothing**.

**Before this record, every count the line stated had been committed, and a later line never stated a lower count than an earlier one**: a resumed session starts from the counts committed at its last checkpoint. **From this record a line can state entries not yet committed**, and if the session dies before its next checkpoint the next session resumes from that checkpoint, so its lines can state a lower count than the last line of the session before. That is accepted: the line says entries *walked*, a fact about the process (ADR-093), and the record of the walk is the ledger, which this does not touch; the resumed session writes `Resuming walk W of R from <checkpoint>` before its first running line, which marks where the count starts again; and writing only committed counts is the silence the operator decided against.

**The census and `StageProgress` follow one cadence, implemented twice.** `corpus` may not depend on `pipeline`, so `WalkRecorder` holds its own copy of the running cadence of §8. A test on each side pins the same positions (Tests).

### 7. What is not counted

**Statements, drains and row callbacks, which are #411's:**

- the drains of a reader into memory: `SurvivorDrain.drain` (both of stage 1's passes), `ItemStreamReaders.drain` (5c, 5d, 5f, and the seed walks in 5c and 5d), `DocumentFrequency.drainSurvivors`, `ConfidenceDistribution.drainSurvivors`, `UnrecordedOccurrences.countOver` and 5b's;
- stage 3's read of a run's shingle rows (ADR-191) and `ConfidenceDistribution`'s read of `extraction_metric`;
- 4b's `RedundancyBoilerplate.hashes()`, `loadSignedOccurrenceIds`, the `signature_band` read, the `extraction_metric` read in `loadOccurrenceProfiles`, `loadDocumentFrequency`, and the build of `shingle_by_hash` (ADR-187);
- stage 5b's whole body, under the operator's decision of 2026-10-04 that 5b goes to #411. Its tasklet has no loop of its own; its loops are in `embedding.SeedCorpusComparison`, which drains the seed walk's reader, and inserts one row per language and per provenance category present and one per spread it compares. Those loops are #411's with the rest of 5b, and not counted here;
- `RelevanceScoring.scoredBelow` (5e); `Clustering.partitions`, `membersOf` and `DocumentClusters.sizesFor` (5f); `RelevanceDistribution.measure`, `spreadOf`, `embedderIdentityFor` and `anyEmbedderIdentity`, and `RelevanceLabels.forSeedSet` (the report); `DocumentClusters.forRun` and `Clusters.forRun` (6a, 6b); `SynthesisDocs.forRun` and `ClusterFaults.forRun` (`ClusterGeneration.write`, `GenerationTasklet.writeDeliverable` and `whyUnwritten`); `UnusableSeeds.forRun` (5c, 5d); `Ledger.extractionFailures` (`ReviewListListener`).

**Loops over a constant number of items:** `ConfidenceDistribution`'s four quality grades, set up and written as rows; `RelevanceDistribution`'s five bands; `DocumentFrequency`'s `shingle_corpus_size` insert, one per shingle granularity, of which the code has one (`ShingleParameters.DEFAULT`); `GenerationTasklet.profileValues`, one per component of the `Profile` record; 4a's band rows per occurrence; `QualityGrade`'s walk of its scored grades to place a score.

**Loops that only compute over values already in memory**, a class (§1), of which these are the ones named: `Communities.of`; `RetainedEdgeSpread`; `Clustering`'s mean of one document's chunk vectors, after the one read `StoredMeanVectors` makes for it (§3); `DoclingDocumentTexts`; `LanguageDetection`; `WindowedStructurelessChunkingFallback`; `TextParts`' merge of the parts' answers, after the calls; `Checkpoint`'s parse and comparison of ordinals; the hex rendering of a digest in `ContentHash` and `ContentHashing`; `NearestNeighbourGraph`'s cosine and top-k loops; `RelevanceScorer`; `RelevanceDistribution`'s banding and its draw of up to twelve a band; `LabelledSpread`; `Arrangement.partitionsOf` and `order`; `LeadDocument.of` and `labelled`'s scans; `UnionFind`; 4b's expansion of signature buckets into pairs (`nearDuplicateCandidates`' loop over `buckets.values()`, which the previous draft counted) and its filter of components by size; the `jaccard` and `containment` merges; `ConfidenceDistribution`'s count of scores per grade; stage 3's grouping inside the row callback; the furniture rules of `Deliverable` (recurring or declared, near copies, same place, which the previous draft counted) and its index, membership and manifest string building; `whyUnwritten`; `ClusterSynthesis`'s check of citations and `whatFitsIn`; `MinHashSignature`; `DifferenceHash`; `ChunkEmbedder`'s and `VectorCache`'s vector arithmetic; `HybridChunker` and `DocumentPicture`'s parsing; `SeedExtractionItemWriter.write`; every report renderer in `pipeline` (`FormatMixReport`, `ConfidenceDistributionReport`, `RelevanceLabellingReport`, `RelevanceLabelFile`, `ClusterSizeReport`, `ArrangementReport`, `ReviewListReport`, `ReportPage`).

**Inner loops left to their outer counter**: §3's rows marked covered.

**Loops outside the job's steps:** `LabelIngestion` and `LabelFileReader` (the `label` command, which is not a stage), `WorkingDirectoryLock`, `StartUpIndexAnnouncement` (start-up, ADR-187), `VectorStoreConfiguration`, `SidecarRecovery`'s wait for `/health` (a wait on a clock, ADR-175), `ConversionDispatch`'s read-ahead and `PendingConversions`' abandon (bounded by `LOOKAHEAD`, plumbing of stage 2's counted items), and the walks up an exception's causes in `CensusTasklet`, `StepFailure`, `MisshapenProfileRefusal`, `WorkingDirectoryInUseRefusal` and `DoclingClient`.

**Stage 2's listeners and 4a's and 5a's after-step hooks, surveyed.** `ExtractionFaultRecorder.afterStep` loops in `ExtractionFaultResolution.resolve`, counted (§4). `ReviewListListener.afterStep` is one statement and one page rendered in memory. `ExtractionHealthCheckListener` and `RunCompletion` hold no loop. 4a's `SignatureStepBoundaryLog` holds no loop, and `ShingleHashIndexBuild` is the one statement ADR-187 covers. 5a's `SeedExtractionItemWriter.afterStep` loops over the seeds' outcomes writing their rows, counted (§4).

### 8. Cadence, and what caps a counter's lines

**Over a known total T, a line every `max(1, min(⌊5T/100⌋, max(1,000, ⌈T/100⌉)))` items.** Up to 100,000 that is ADR-093's rule unchanged: 5% of the total or 1,000, whichever is smaller. Over 100,000 it is 1% of the total. So no counter over a known total writes more than 100 lines, and every total of 100,000 or less writes what it writes today.

**A running counter writes a line at each count n that is a multiple of `max(1,000, 10^(⌊log10 n⌋ − 1))`**: every 1,000 up to 99,000, every 10,000 from 100,000, every 100,000 from 1,000,000, and so on. That is 99 lines up to 99,000 and 90 for each further power of ten. The line is `<label>: N so far`, grouped like the total form's. `StageProgress.running(String label)` makes one.

**The largest totals, and their lines.** Stage 3's frequency rows: no figure for the archive's distinct hashes exists. The largest figure on record that bounds them is the 42,833,917 shingle rows in the whole table that #411's line reported on 2026-10-04, which one run's distinct hashes cannot exceed for the database as it was that evening. Over a total of 42,833,917 a line falls every 428,340 items, 99 lines, the last at 99%. A running counter that reached 42,833,917 would write 312 lines. 4b's pairs: not known for the archive; at most 100 lines. The counter totals known from 2026-10-04 (5,001 stage-2 survivors, 4,275 corpus survivors, 19 usable seeds) are all below 100,000, so their counters write what ADR-093's rule writes: 4,275 survivors in 5c at a line every 213 items, 20 lines.

**Nothing but `N of M (x%)` or `N so far` is stated**: no throughput and no time left (decision 5). Every line carries the log's own timestamp, so two lines give a reader the rate; an estimate would rest on every item costing about the same, which 4b's shingle sets, from a few dozen hashes to hundreds of thousands (#277), do not.

### 9. Stage 6a on an arrangement already recorded

On main `ArrangementTasklet` reads `relevanceScoring.scoresFor` before `TaskletSteps.once`, and its already-recorded branch builds the `ClusteredDocument`s from the membership and the scores and draws the page from `Clusters.forRun`, each lead found by `LeadDocument.of`, because ADR-154 §2 has it write the page on both branches. So on an arrangement already recorded, 6a writes its `scores read`, `members gathered`, `page rows` and `page partitions` counters, and not `clusters`. **That is accepted, and the reads do not move.** The page needs the scores on that branch, since `LeadDocument.of` weighs them, so moving the read after the check would not spare it; the counters report work the step does. Nothing about 6a's behaviour changes.

### 10. Text owed with the code

Sentences that the code of each part makes false, to be changed in the same change:

- `StageProgress`'s class Javadoc, *"it reports a running count of its own from `WalkRecorder`, at its checkpoint cadence, rather than through this class"* and *"Every stage but the walk itself has a denominator before it starts"*: running counters exist now, and the census line has the running cadence (a). Its `over` Javadoc, *"it names the stage the way its start and end lines already do"*, is not true of 6a's and 6b's labels (c, d).
- `WalkRecorder`, lines 249 to 252: *"The checkpoint cadence is this line's cadence too -- both measure entries walked"*; and lines 267 to 268: *"is the only one a corpus smaller than one checkpoint interval ever logs"* (a).
- `Walk.Observer`'s Javadoc, which lists what the walk reports (a).
- `HashingProgress`'s class Javadoc, which names one loop (a).
- `ExtractionFaultResolution.resolve`'s Javadoc (b).
- `GenerationProgress`'s Javadoc, *"What the walk tells its caller about each cluster it leaves unwritten, as it goes"*: it also hears of every cluster gone through (d).
- `ClusterGeneration`'s and `Deliverable.writeTo`'s Javadoc, where they list what the caller is told (d).
- `Adr.java`'s constant for this record, which names its file and its gist.

### 11. Four parts, each landed on its own

Each part compiles, passes and is gated alone, in this order, and leaves a green tree. Each moves its tests from `docs/adr/0192/tests/<part>/` into `src/test` (Tests). The run ids each moves are read off `StageModules` on main: `corpus` is in stage 1's; `extraction` in stages 2 to 6b; `similarity` in stages 2, 3 and 4; `embedding` in stage 5's two runs, 6a and 6b; `synthesis` in 6a and 6b; `pipeline` in stages 3 to 6b. Each later stage names its upstream run (ADR-048), so moving a stage's run id moves every later stage's.

| Part | Modules | Run ids moved | Files to change | Effort |
|---|---|---|---|---|
| (a) `StageProgress`, census and stage 1 | `corpus`, `pipeline` | stages 1 to 6b | `pipeline/StageProgress.java`, `pipeline/ByteLevelReductionTasklet.java`; `corpus/Walk.java`, `corpus/WalkRecorder.java`, `corpus/HashingProgress.java`, `corpus/ContentIdentityResolution.java` | large: the census line moves off the commit in a resumable walk |
| (b) stage 2's fault resolution, stage 3 and 4b | `extraction`, `similarity`, `pipeline` | stages 2 to 6b | `extraction/ExtractionFaultResolution.java`, new `extraction/FaultResolutionProgress.java`; `similarity/DocumentFrequency.java`, `similarity/RedundancyResolution.java`, new `similarity/FrequencyProgress.java`, `similarity/ResolutionProgress.java`; `pipeline/ExtractionFaultRecorder.java`, `pipeline/ContentCensusTasklet.java`, `pipeline/RedundancyResolutionTasklet.java` | large, or medium with the contract tests as its spec: six callbacks on every path of `RedundancyResolution` |
| (c) stage 5 and 6a | `embedding`, `pipeline` | stages 3 to 6b | `embedding/RelevanceScoring.java`, `embedding/Clustering.java`, `embedding/NearestNeighbourGraph.java`, new `embedding/ScoringProgress.java`, `embedding/ClusteringProgress.java`; `pipeline/SeedExtractionItemWriter.java`, `pipeline/EmbeddingScoringTasklet.java`, `pipeline/RelevanceScoringTasklet.java`, `pipeline/RelevanceFloorTasklet.java`, `pipeline/ClusteringTasklet.java`, `pipeline/RelevanceReportTasklet.java`, `pipeline/ArrangementTasklet.java` | small for the counters in `pipeline`; the block counter in `NearestNeighbourGraph.build` and the 5f pre-read want care, and `ClusteringProgressTest` pins both shapes the count can take |
| (d) stage 6b | `synthesis`, `pipeline` | stages 3 to 6b | `synthesis/GenerationProgress.java`, `synthesis/ClusterGeneration.java`, `synthesis/Deliverable.java`, new `synthesis/DeliverableProgress.java`; `pipeline/GenerationTasklet.java` | large: every exit of the walk and of the tree |

**Part (b) also touches `extraction`**, which the ticket's plan for (b) named as `similarity` alone. It adds no run id: stage 2's moves through `similarity` in the same part, and through stage 1's upstream run in (a). Part (b) needs (a)'s `StageProgress.running`. Part (d) needs (a)'s `running` and (c)'s `ScoringProgress`.

## Why this shape, and what the others cost

- **A counter for each nested loop, always.** Refused by the operator's decision 1: it would add counters whose lines say nothing an outer line does not.
- **Members recorded in 5f, buckets expanded in 4b, near copies and places compared in the deliverable.** The previous draft counted them. Dropped: the first is left to its outer counter (§3), the other three compute in memory (§1).
- **A census line from `pipeline`, through `StageProgress.running`.** It would put one cadence in one place. Not taken: the line has been `corpus`'s since ADR-093, `WalkRecorder` is called from `CensusTasklet` with no callback, and the operator's decision for the census is that it is not silent, which this meets with the line where it is.
- **A census line only from committed counts.** It is what main does, and it is the silence of a flat root.
- **One shared progress type.** Refused in §5.
- **A per-partition counter for 5f's files hashed, with no pre-read.** It would write a counter per partition, every member a line in a partition under forty. The summed counter writes at most 100 lines and costs the pre-read.
- **Moving 6a's reads after the already-recorded check.** It would not spare them (§9).
- **Throughput or time left.** Refused by the operator (decision 5), for the reasons in §8.

## Consequences

- **Production code changes in six modules**, in four parts (§11). `ledger` and `profile` are not touched.
- **No schema version moves** (ADR-059): no table, column or index changes.
- **The archive's next run waits until all four parts have landed** (the operator, 2026-10-05), so the run ids they move are minted once, on that run. With ADR-188, ADR-189 and ADR-190 on main, #320 no longer holds that run back; this decision does. **A part that lands after that run costs a replay of the stages it moves**: (a) stages 1 to 6b, (b) stages 2 to 6b, (c) and (d) stages 3 to 6b. A replay is not timed anywhere. On 2026-10-04 stage 2 took 4 h 23 min 7 s over the converter and cached conversions together; a replay of it is over cached conversions (ADR-070), and how much of that duration it repeats is not known.
- **5f reads every partition's members before it clusters the first** (§5): one `membersOf` statement per partition, moved earlier, not added, and every partition's member ids held at once, which is at most one id per scored survivor (4,275 on 2026-10-04). Until those statements return, the stage's first partition is not started; how long they take on the archive is not measured.
- **The census line can state uncommitted entries, and fall after a resume** (§6).
- **6a writes four counters on every invocation that finds its arrangement already recorded** (§9).
- **The relevance report writes its two counters on every invocation that reaches it, and the floor its one counter on every invocation where its threshold applies**, since neither is inside `once` (§4).
- **Lines change by addition only, with two exceptions.** The census line is written in the same words from a different place and at the §8 cadence, which over fewer than 100,000 entries is every 1,000, as before. And §8 thins every existing counter over a total above 100,000 to a line every 1% of it; the existing counters are stage 1's broken check and content hash, stage 2, 4a and 5a's processor. No total of theirs on record for the archive reaches 100,000: stage 2's is 10,406 file occurrences (ADR-182), and 4a's was 5,001 on 2026-10-04. Stage 1's broken check counts the census's file occurrences, and its content hash and 5a's processor count at most stage 1's and the seed walk's occurrences; none of those is stated by any record for the archive.
- **A counter over a known total writes at most 100 lines; a running counter about 90 for each power of ten past 10,000** (§8).
- **`AGENTS.md`, `CONTEXT.md` and `README.md` need no new term or claim.** `README.md` carries no claim about project state.

## Tests

Every whole-job test reads the lines the invocation wrote through a list appender on the application's own logger, and **asserts that every line of every counter it names was written by `StageProgress`'s logger**, so a module that wrote the same words itself fails. Totals are kept under 40 where they can be, so every item is a line and no claim depends on a clock or on the order a file system lists entries; where a total is read off the database, the expected lines are computed from §8's rule by `ProgressLines`, which restates the rule rather than reading it off `StageProgress`.

**Where the tests are, and why.** A test that names a type, method or overload this record adds does not compile until its part does, and one tree is compiled at once, so it would stop the whole test tree compiling. A test that compiles today but is red until a later part lands would make each earlier part's tree red. So:

- **In `src/test`**: parts (a), (b) and (c)'s tests, green since each landed, and the tests that are green and stay green.
- **Under `docs/adr/0192/tests/<part>/`**, as complete files at the path they take under `src/test/java`: every other test, which `spec-implementer` moves into `src/test` when it builds that part. `docs/` is not compiled by Maven, and `docs/check-claims.mjs` reads only the top level of `docs/adr` for records, so files there touch neither the build nor the guards. A whole-job test there that compiles against main today was compiled and run on this branch from `src/test` before it was moved, and failed only at its counter claims.

**Part (a) landed with this record, and parts (b) and (c) after it**, so their tests are in `src/test` and green, and the table says so; the rows for part (d) give their state before it is built.

| Part | Test | Where | State on this branch | Pins |
|---|---|---|---|---|
| — | `SimilarityEmbeddingAndSynthesisDeclareNoLoggerTest` | `src/test` | green, and stays green | no class of the three modules declares a field of SLF4J's `Logger` type |
| (a) | `pipeline.StageProgressVolumeTest` | `src/test` | green since part (a) | §8's cap over 1,000,000 and over 100,001, and no change at 100,000 |
| (a) | `corpus.CensusReportsItsRunningCountTest` | `src/test` | green since part (a), both tests | in a directory beneath the root, 1,002 entries, exactly one running line, at 1,000, and none from the checkpoint taken at 1,002, where main writes its line; in a flat root of 2,001 files, lines at 1,000 and 2,000 |
| (a) | `pipeline.StageOneReportsItsLoopsInvocationTest` | `src/test` | green since part (a) | sizes read over three files; duplicates recorded over 1,002 copies, one line at 1,000; logger names |
| (a) | `pipeline.StageProgressRunningCountTest` | `src/test` | green since part (a) | the running cadence, 99 lines to 99,000, then 100,000 and 110,000 |
| (a) | `corpus.CensusRunningCountCadenceTest` | `src/test` | green since part (a) | the same cadence in `WalkRecorder`; a running line naming 1,000 entries while none is committed; a checkpoint taken at 1,500 entries writing no line and still committing them; the count falling after a resume |
| (a) | `corpus.ContentIdentityResolutionReportsItsLoopsTest` | `src/test` | green since part (a) | sizes announced once, zero included; each duplicate reported |
| (b) | `pipeline.StageTwoReportsItsFaultResolutionInvocationTest` | `src/test` | green since part (b) | one held fault resolved, one line; no fault held, no line; logger names |
| (b) | `pipeline.RedundancyResolutionReportsItsProgressInvocationTest` | `src/test` | green since part (b) | stage 3's frequency rows; 4b's pairs, profiles, components, verdicts and containment; nothing signed, nothing written; already recorded, nothing written; logger names |
| (b) | `extraction.ExtractionFaultResolutionReportsItsCountTest` | `src/test` | green since part (b) | announced once, each fault reported, on completion and on a stop |
| (b) | `similarity.FrequencyProgressTest` | `src/test` | green since part (b) | distinct hashes announced once, a hash in one document counted though no row is written, zero announced |
| (b) | `similarity.RedundancyResolutionReportsItsCountsTest` | `src/test` | green since part (b) | every loop's order and totals; two components summed into the verdict counter; candidates reported inside the containment loop; zero announced; nothing signed, nothing called |
| (c) | `pipeline.StageFiveReportsItsProgressInvocationTest` | `src/test` | green since part (c) | 5a, 5c, 5d, 5e, 5f, the report and 6a's counters, 6a's already-recorded branch, a partition emptied by the floor; logger names |
| (c) | `embedding.ScoringProgressTest` | `src/test` | green since part (c) | both loops, a seed or occurrence with nothing stored still reported, zero announced |
| (c) | `embedding.ClusteringProgressTest` | `src/test` | green since part (c) | b(b + 1)/2 over three blocks of a pass; one block; a counter per `clusterAndRecord` call over two partitions; nothing for an empty partition |
| (d) | `pipeline.GenerationReportsItsProgressInvocationTest` | `docs/…/d/` | compiles today; red by assertion | 6b's clusters on a clean finish, on a stop (the fifth counted, the sixth not reached), on a cluster nothing could be sent for, on a cluster the engine finds no room for, on a window no document fits, and with clusters already written; the tree's counters on each of the three exits; logger names |
| (d) | `synthesis.ClusterGenerationReportsEveryClusterTest` | `docs/…/d/` | names `GenerationProgress`'s new methods | `toGoThrough` once, zero included; `clusterGoneThrough` on every path, the fifth turned down included |
| (d) | `synthesis.DeliverableProgressTest` | `docs/…/d/` | names `DeliverableProgress` | the four loops' order and totals over two partitions; an entry for a document the cluster no longer holds not counted |

**Not pinned, and why:**

- **The running counters' lines in a whole job**: 5c's two chunk counters, 4b's containment candidates and 6b's cluster documents opened write their first line at the 1,000th item, and no fixture reaches 1,000 chunks, candidates or cluster members. The running cadence is pinned in `StageProgressRunningCountTest`, and 4b's candidate callback in its contract test; which loop `pipeline` ticks for the other three is not pinned. Stage 1's duplicates are pinned, over 1,002 copies.
- **5f across two partitions, and `partition P of M` with M above one**: both whole-job fixtures answer every document alike (one converted text, one vector), so every survivor has the same winning seed and there is one partition. Reaching two needs both fixtures to answer by file, which this record does not add. The block callback is pinned per call over two partitions in `ClusteringProgressTest`; `pipeline`'s sum and its P and M are not.
- **The old signatures calling nothing** (§5): they are the signatures the existing tests call.

## Not known

- **How long any counted item takes on `H:`**, and so how far apart the lines of any counter will be. Only the step durations of 2026-10-04 exist.
- **How 4b's about 26 minutes 40 seconds divide** between its statements (§7), its loops and its writes.
- **Whether 6b's counting calls or its exemplar reads cost more than its answering call** for the archive's clusters (§3). 6b was not reached.
- **How long 5f's per-block vector reads take on `H:`** (§3), and how long its pre-read of members takes (Consequences).
- **How long the census's final write of a large flat directory's buffered rows takes** (§3).
- **Whether the archive's census wrote a running line on 2026-10-04** (§6).
- **The archive's number of distinct shingle hashes, and of candidate pairs** (§8).

## What this does not decide

- **How the statements of §7 can report while they run.** That is #411's.
- **Whether 6b's counting calls cost more than its answering call** on the archive's clusters (§3). If a run shows they do, decision 6's exception for them is to be revisited.
- **Whether stage 5c should commit as it goes.** #412 names it as a separate question.
