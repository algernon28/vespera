# ADR-206 — Stage 2 records the key it looked the extraction cache up under, and no step after it opens an archive file

> **Partly amended — see [ADR-208](0208-vespera-label-auto-needs-no-corpus-root-and-root-is-no-longer-an-option-of-label.md).** It answers the question the fifth bullet of §4 left to the operator: `vespera label --auto` requires no corpus root and `label` takes no `--root`. So that bullet's *"still requires a corpus root"* and *"The requirement itself stands"* in the header below no longer hold, and the refusal §7 lists as reworded is gone, with no sentence in its place. Everything else in this record stands.

- **Date**: 2026-10-07
- **Status**: accepted
- **Amends**: [ADR-151](0151-the-manifests-content-hash-is-every-survivors-sha-256-taken-at-6b-through-extractions-own-hash.md), in its §2 to §5, one refused alternative and two Consequences (§6.1 below). Its §1 stands: what `content_hash` means, the header and the format do not change. It is amended and not superseded, because §1 is the decision a consumer of the manifest reads.
- **Amends**: [ADR-152](0152-a-survivor-whose-file-will-not-open-is-still-asked-about-without-its-opening-and-a-step-that-records-completion-stops-instead.md), in its §1, §3, §4 and §5 (§6.2 below). Its §2 stands: a sampled survivor is always asked about.
- **Amends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md), in three bullets of its §9 and the second point of its §10 (§6.3 below). The furniture rule, the budget, the placement and the two passes stand.
- **Amends**: [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4, in the names of two counters and the units of four (§7 below).
- **Amends**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6, in the reason it gives for requiring a corpus root: the openings are no longer read from the corpus (§4 below). The requirement itself stands.
- **Restores**: two sentences of [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) that ADR-149 §10 and ADR-151 §4 had amended (§6.4 below).
- **Keeps**: [ADR-067](0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md) (stage 1 hashes within size-matched groups, and `corpus` owns its table), [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) (the module rule and its one exception), [ADR-127](0127-a-database-lock-is-waited-out-by-sqlites-busy-timeout-not-by-hikaris-connection-timeout.md) and [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) (one writer: a conversion worker writes nothing), [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md) and [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) (what a fault is and what the cache keeps), [ADR-155](0155-a-seed-file-that-will-not-open-is-recorded-under-a-reason-of-its-own-and-seed-extraction-records-no-completion-until-it-opens.md) (a seed file that will not open at seed extraction), [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) (a stopped stage 2 resumes from its committed chunks), and [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md) (the documents a call carried are recorded).
- **Rests on**: a reading of every caller of `DoclingExtractor.contentHashFor` at commit `039d111`, with the sites named in §Context; one throwaway probe over synthetic files (§8); and a count taken over the test tree (§1). No archive and no working directory was opened for this record (ADR-196), so the cost on a real one is not measured here (§8).
- **Settles** [#349](https://github.com/algernon28/vespera/issues/349).

A *key* in this record is the SHA-256 of a file's bytes, as 64 lowercase hexadecimal characters, under which stage 2 looked the extraction cache up for one file occurrence. It is the value `extraction_cache.content_hash` and `chunk_cache.content_hash` already hold.

## Context

Stage 2 keys the extraction cache by the SHA-256 of an occurrence's bytes. It takes stage 1's hash where stage 1 recorded one (ADR-067) and hashes the file itself otherwise, in `ConversionDispatch.dispatchIfConvertible` for a dispatched call and in `ExtractionItemProcessor.convertNow` for one it places itself. It writes the value nowhere. Every later step that needs a document's conversion, chunks or vectors therefore reads the whole file again to compute the same value.

**The sites, at `039d111`.** #349 took them at `0b4e009`. The lines have moved, one site has moved into a class two commands share, and two things were not in its table.

| Step | Site | Side | What it reads the file for |
| --- | --- | --- | --- |
| embedding scoring (5c) | `EmbeddingScoringTasklet.rechunkAndEmbed`, line 211 | corpus and seed | the key of the cached conversion and of the chunks it embeds |
| embedding scoring (5c) | `SeedConversions.convert`, called on line 212 | corpus and seed | `BrokenCheck.check`, a second read to detect the format, made even when the conversion is a cache hit |
| relevance scoring (5d) | `RelevanceScoringTasklet.execute`, line 145 | corpus | the key of each survivor's vectors |
| relevance scoring (5d) | `RelevanceScoringTasklet.seedContentHashes`, line 178 | seed | the key of each usable seed's vectors |
| clustering (5f) | `ClusteringTasklet.contentHashesOf`, line 243 | corpus | the key of each member's vectors |
| labelling page (5), and `vespera label --auto` | `DocumentOpening.of`, line 80 | corpus | the key of a sampled survivor's cached conversion |
| arrangement (6a) | `ArrangementTasklet.titleOf`, line 300 | corpus | the key of a lead document's cached conversion |
| generation (6b), the call | `GenerationTasklet.openingChunkOf`, line 866 | corpus | the key of an exemplar's opening chunk |
| generation (6b), the tree | `GenerationTasklet.hashOf`, line 618 | corpus | the manifest's `content_hash` cell (ADR-151) and the key of a survivor's pictures (ADR-149) |

That is the seven corpus-side sites #349 counts, and one more read it does not: `BrokenCheck.check` in 5c. On the seed side there are two re-reads, not one, because 5c embeds the usable seeds through the same method as the survivors.

**How often a surviving file is read after stage 2.** In one campaign from stage 5 to the deliverable, a survivor's file is read in full by 5c, 5d, 5f, 6b's call and 6b's tree: five times. It is read a sixth time if it is among the sixty the labelling page samples, on every invocation, and a seventh if it leads a cluster. Every later invocation that writes the tree reads every survivor once more, a repair invocation included (ADR-149 §9). A usable seed is read twice after seed extraction, by 5c and by 5d. Seed extraction itself reads every seed on every invocation, finished or not, because that read is what answers the usable-seed gate (ADR-155 §5); that is its own read and not a re-read, and this record leaves it (§3).

**Each site also decided for itself what a file that will not open means.** 5c, 5d, 5f and 6a fail the step (ADR-152 §4). The labelling page shows a stated fallback and warns (ADR-152 §1). 6b's call leaves the document out and warns (ADR-133). 6b's tree leaves the `content_hash` cell blank, shows no pictures and warns (ADR-151 §3, ADR-149 §9). A file whose bytes changed while its size and times did not is not noticed by the walk (ADR-115), and each site then looks the caches up under a hash nothing was ever stored under.

**The pictures need no byte of the file.** `GenerationTasklet.picturesFor` reads them through `DocumentPictures.forContentHash`, out of `extraction_cache.response_json`, as ADR-149 decided. The file was read only to find the key. #349's brief supposed otherwise, and the code does not bear it out.

**ADR-151 refused to record the key**, as "a second recorded byte identity beside `corpus`'s" that needed "a schema change and a stage-2 re-run", and left it as "the route to take if 6b's reads are ever measured to matter". ADR-152 §5 and ADR-149 §9 both name a recorded key as the remedy for their own re-read.

## Decision

### 1. The key is recorded in a table of its own, `extraction_cache_key`

```sql
CREATE TABLE IF NOT EXISTS extraction_cache_key (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    content_hash TEXT NOT NULL CHECK (length(content_hash) = 64 AND content_hash NOT GLOB '*[^0-9a-f]*'),
    PRIMARY KEY (occurrence_id, run_id)
);

CREATE INDEX IF NOT EXISTS extraction_cache_key_by_run_id ON extraction_cache_key (run_id);
```

- **It is `extraction`'s table** (ADR-041), keyed as `extraction_metric` is. The index is ADR-173's.
- **The column is named for what it refers to.** `content_hash` is the name of the column it must equal in `extraction_cache` and `chunk_cache`.
- **The check refuses anything that is not a key.** A value of another length or case would never match a cache row, and the reader would see a miss with nothing to say why. The expression was run against SQLite 3.49.1: it accepts 64 lowercase hexadecimal characters and refuses upper case, 63 and 65 characters, a letter past `f`, the empty string and 64 spaces.
- **It is not a foreign key into `extraction_cache`.** The cache is keyed by content and instrument, outside any run, and one kind of answer earns a key and no cache row (§2).
- **It is not a second byte identity.** `corpus`'s `content_hash` table records a relation over occurrences, which stage 1 decides duplicates by. This table records which cache key one stage-2 run used for one occurrence. Nothing decides anything from it.

**A column on `extraction_metric` was refused**, for two reasons.

- **A metric row is a measurement of a response, and the key is an address.** They share a primary key and nothing else. `extraction_metric` is what `similarity` and `embedding` read to compare documents, and its rows are made by hand wherever a test needs a document with measurements: 11 test classes in four modules insert one with a plain `INSERT`, counted over the test tree. A `NOT NULL` column there would have each of them invent the hash of a file that never existed.
- **The column could not be nullable instead**, without losing what §4 relies on: that a survivor always has a key.

**What the separate table costs** is that two tables must hold the same occurrences. §2 states that as one rule, and one test holds it.

### 2. A key row is written wherever a metric row is written, on both sides

**Under every run, the occurrences carrying a row in `extraction_cache_key` are exactly the occurrences carrying a row in `extraction_metric`.**

| What stage 2 got for the occurrence | Metric row | Key row | Cache row (ADR-183) |
| --- | --- | --- | --- |
| a conversion, whether it survives or earns `degenerate-output` | yes | yes | yes |
| a failure the converter blamed on the document | yes | yes | yes |
| a timeout the converter reported, below the count that sets it aside | yes | yes | no |
| a failure the converter blamed on itself, set aside as an extraction fault (ADR-139) | no | **no** | no |
| no response: a timeout of our own, an error status, a connection dropped twice | no | no | no |
| no detected format, so no call (ADR-100) | no | no | no |

- **An occurrence whose conversion faulted gets no key.** Nothing is cached for it (ADR-183), and nothing is written for it at all: where the answer is set aside, no metric row is written and so no key row is. Nothing is rolled back to make that so. A skip in processing does not roll the chunk back (ADR-181, *"A skippable exception from the processor does not roll the chunk back"*); the item is dropped and the chunk goes on with what it wrote for the others. A completed step then resolves the fault into `extraction-failed`, so no later step asks about it.
- **The key recorded is the value the lookup used**: stage 1's hash where `ContentIdentity.hashFor` answered, and stage 2's own otherwise. They are one digest over the same bytes (ADR-151, `ContentHashingTest`). No file is hashed a second time to record it.
- **It is written by the thread the step runs on**, in the chunk transaction that writes the metric row, the shingles and the verdict (ADR-127, ADR-140 §3). The value is resolved on that thread before the call is dispatched, and it travels to the write with the answer. A conversion worker still runs the Docling call and nothing else.
- **A stopped stage 2 keeps the key rows its committed chunks wrote**, as it keeps their metric rows (ADR-181), and writes none of them again. Wherever a step deletes a run's `extraction_metric` rows it deletes that run's key rows in the same place, or the second write collides on the primary key.

### 3. Seed extraction records its key too

Seed extraction writes an `extraction_metric` row for every seed it converted, under the measurement run (ADR-092), so §2's rule already covers it: **every seed with a metric row has a key row under the measurement run**, usable or not. A seed whose file would not open has neither (ADR-155).

5c and 5d read a usable seed's key from there. Both seed-side re-reads go.

Seed extraction is still the first read of a seed, and ADR-155 stands as written: a seed file that will not open then is recorded under its own reason and seed extraction records no completion.

**Seed extraction goes on reading every seed on every invocation** (ADR-155 §5). Its processor hashes and converts each seed whether or not the step is finished, because the usable-seed gate is answered from that read for the rest of the invocation; the writer writes nothing where the step is finished, so the key rows are written once. Having it read the recorded key instead would mean deciding the gate from the ledger, which is ADR-083's and ADR-155's ordering and not this record's to move. So over a campaign of two invocations a seed is hashed twice, once in each, where it was hashed four times.

### 4. Every step after stage 2 reads the key, and none opens an archive file

**After stage 2, no step calls `DoclingExtractor.contentHashFor` on a corpus file, and after seed extraction no step calls it on a seed file.** The callers left are the three that take the first hash: `ConversionDispatch`, `ExtractionItemProcessor` and `SeedExtractionItemProcessor`.

- **Which run a key is read under.** A corpus survivor's key is read under the stage-2 run. Inside the job that is the one this invocation arrived at (ADR-154). `vespera label --auto` runs no job, so it follows the recorded upstream chain from the scoring run its label file names (ADR-048). Neither re-derives an id. A usable seed's key is read under the measurement run.
- **A survivor with no key stops the step**, at every site, with an error naming the occurrence and the run. §2 makes it impossible, so it is the ledger disagreeing with itself, which ADR-139 §6 already refuses to turn into a row or a fallback.
- **A scoring run with no stage-2 run upstream of it stops `vespera label --auto`**, with an error naming the run: *"run <id> has no stage-2 run upstream of it, so no extraction cache key can be read for its documents"*. Every scoring run the job mints has one (ADR-048), so this too is the ledger disagreeing with itself. To follow the chain the command asks `ledger` which stage each run was recorded under, through `Ledger.stageOf(RunId)`, which gives the recorded text back and decides nothing from it; what a stage is stays `pipeline`'s (ADR-040).
- **`vespera label --auto` still requires a corpus root, and no longer reads one.** ADR-197 §6 requires `--root` or `vespera.corpus-root` *"because the openings are read from the corpus"*. They are not any more: the command reads the label file, the ledger and the caches. This record leaves the requirement and the option as they are, because what a command line demands is the operator's to change and not a side effect of this wave. It withdraws only the reason: ADR-197's clause is historical, and the refusal's sentence that repeats it is reworded (§7). Whether the requirement goes is open, and is put to the operator.
- **5c reads the cached conversion and never converts.** It reads `DoclingExtractor.cached` under the recorded key, and `SeedConversions.convert` is no longer called from it, so `BrokenCheck.check` goes with it. If the cache holds no conversion under a recorded key, the step stops with an error naming the occurrence, the key and the extractor identity. Converting instead would need the file, and would store the conversion of whatever the file holds now under the hash of what it held at stage 2. Under one run chain the miss cannot happen: stage 2's run id carries the extractor identity, and a conversion is always kept (ADR-183).
- **`SeedConversions` keeps one caller**, seed extraction.

### 5. What a file changed or gone since stage 2 now means

**Nothing after stage 2 notices, and that is intended.** The walk is what observes the archive, on every invocation (ADR-115). A file deleted or resized between invocations makes a different walk, and is no longer a survivor of anything. What is left is a file that goes away during one invocation, a file another program holds, and a file rewritten in place with its size and times put back. For those, every step after stage 2 works from what stage 2 converted, so the scores, the clusters, the labels, the writing and the manifest all describe one reading of the document.

| Site | Before | Now | Sentence amended |
| --- | --- | --- | --- |
| 5c, corpus and seeds | fails the step with `could not hash <path>`; on changed bytes, converts the file again from 5c | **made unreachable**: embeds the chunks of the conversion on record | ADR-152 §4, "stops the run" |
| 5d, corpus and seeds | fails the step | **made unreachable** | ADR-152 §4 |
| 5f | fails the step | **made unreachable** | ADR-152 §4 |
| labelling page, and `label --auto` | *(the file could not be opened…)* and a warning; on changed bytes, *(no conversion is on record for the file as it is now…)* and a warning | **changed**: shows the opening of the conversion on record, with no warning | ADR-152 §1, §3 and §5 |
| 6a, the lead document's title | fails the step | **made unreachable** | ADR-152 §4 |
| 6b, the call | leaves the document out of the call, with a warning; the cluster is then written without it for good | **changed**: the document is sent | ADR-152 §4, its paragraph on 6b; ADR-133's Context |
| 6b, the manifest | a blank `content_hash` cell and a warning | **changed**: the cell carries the recorded key; no cell is blank | ADR-151 §2 and §3 |
| 6b, the pictures | no pictures for that survivor, under the same warning | **changed**: its pictures are shown | ADR-149 §9 |

**What is kept at each site is what does not depend on the file.** A document nothing was ever chunked from is still left out of 6b's call, with its own warning. A sampled survivor whose conversion carried no chunk still shows `(no text was extracted)`. A survivor with no stored vectors still stops 5d (ADR-139 §6).

**The labelling page keeps one fallback, reworded.** `DocumentOpening` reads the cache under the recorded key and the extractor identity now in use. Inside the job that always hits in any state a run leaves: stage 2's run id carries the extractor identity, and a conversion is always kept (ADR-183). It misses only where rows were removed from the cache by something other than a run. Under `vespera label --auto` it also misses when the Docling image was changed after the run the label file names. The page then shows:

> (no conversion is on record for this document, so its opening is not shown)

and a warning names the occurrence and the key recorded for it, where ADR-152's named the file: the step holds no path it has opened. ADR-152 §3's wording said *"for the file as it is now"*, which the step no longer knows anything about.

**A path in the label file that is not in the run's walk gets a warning and no opening.** `vespera label --auto` finds a document's key through its occurrence in the walk of the run the label file names. A path that walk does not hold has no occurrence and so no key. The model is still asked about it, with no opening, and a warning names the path and the run. It arises only from a label file edited by hand: every path a run writes there is one of its own walk. Before this record such a path met a file that would not open, and ADR-152 §1's fallback; it is the one place that outcome is kept rather than made unreachable, and it is a warning and not an error because a hand-edited file is the operator's to correct.

**One route to a cluster nothing can be sent for closes.** A document rewritten in place used to leave 6b with nothing cached under its new hash, and a cluster holding only such documents went unwritten for good (ADR-121). It is now written. The window being too small for any document is still a route to ADR-121's hole, and so is a cluster none of whose documents was ever chunked.

**The manifest states the bytes stage 2 converted.** ADR-151's second reader, an operator who re-points the tree and recomputes each digest, now learns of a file changed since conversion: its digest no longer equals its cell. Before, the cell followed the new bytes while the writing beside it was about the old ones.

**The changed outcomes are all a failure or a gap becoming a result.** Four steps that exited 1 on a file they could not open now finish. No gate moves, no fault row is written or left out differently, and no step that succeeded before fails now, except on the broken invariants §4 names: two stop a step, and a third stops `vespera label --auto`.

### 6. What this amends, sentence by sentence

Earlier records are not edited. Each correction is made here.

**6.1 ADR-151.**

- **§2**, *"Every row's value is `DoclingExtractor.contentHashFor` over the survivor's file, called by `GenerationTasklet` at 6b."* **Read instead**: every row's value is the key stage 2 recorded for that survivor, read by `GenerationTasklet` at 6b. One source for every row still, and `corpus`'s table is still not consulted. *"`extraction` … records nothing new"* is no longer true. *"One hash per survivor per tree write"* becomes one keyed read per survivor per tree write, still shared by the manifest and the pictures.
- **§3**, the blank cell, its warning and `leavesTheContentHashBlankForADocumentGoneBeforeTheTreeIsWritten`: **withdrawn**. No cell is blank. The test is moved to claim the filled cell and the absence of the warning.
- **§4**, *"It costs one full read of each survivor's file at 6b."* **Read instead**: it costs a keyed read of one row.
- **§5**, *"It does not remove it."* **It is removed now**: the read ADR-149 §9 made of every listed survivor is gone.
- **Alternatives refused**, *"Record extraction's hash per occurrence in a new table"*: **taken**, on the condition that paragraph set. What it gave as the cost stands: a schema change and a stage-2 re-run (§9).
- **Consequences**, *"The manifest is no longer written without touching the archive"* and *"`content_hash` is keyed to the bytes at write time, not at walk time"*. **Read instead**: the manifest is written without opening an archive file, and `content_hash` is keyed to the bytes stage 2 converted.

**6.2 ADR-152.**

- **§1**, the fallback *"(the file could not be opened when this page was written, so its opening is not shown)"* and its warning: **withdrawn**, with the constant that holds the text.
- **§3**, *"The preview is read from the extraction cache under the file's hash"*. **Read instead**: under the key stage 2 recorded. The page still never converts. The fallback's text changes as §5 gives it.
- **§4**, the table's four *"stops the run"* rows and its two *"tolerates already"* rows: none of those sites reads the archive. *"The line is where a step records completion"* has nothing left to divide, and the paragraph that lets 6b stand as an exception is withdrawn with the drop it excused: no cluster is written without a document because its file would not open.
- **§5**, *"§1 and §3 remain as the fallback for a survivor with no recorded hash."* **Read instead**: there is no such survivor, and one is an error (§4).
- **Consequences**, *"A step that records its completion still stops on such a file"*: no longer true of any step after stage 2.

**6.3 ADR-149.**

- **§9**, *"For each occurrence it resolves the file under the canonical root, hashes it through `DoclingExtractor.contentHashFor`, asks `DocumentPictures`…"*. **Read instead**: it reads the occurrence's recorded key and asks `DocumentPictures`.
- **§9**, *"A file the archive will not open contributes no pictures"*: **withdrawn**.
- **§9**, *"The cost is a full read of every listed survivor's original, once per tree write"*, and *"The archive is read once, for the hash"*: the archive is not read. Each survivor's cached response is still read and decoded twice, once per pass.
- **§10**, its second amendment of ADR-104: **withdrawn** (§6.4).

**6.4 ADR-104, restored.**

- *"every column is one the ledger already holds, so it costs a query"*, which ADR-151 §4 amended for `content_hash`: the column costs a query again. The table is `extraction`'s, read through `pipeline` as the scores are.
- *"6b does not check that the originals are still there"*, which ADR-149 §10 amended to a full read of every original: 6b opens no original. Writing the tree leaves the archive alone, as ADR-104 first said.

**6.5 ADR-133**, Context: *"reaching it means hashing the file again, and a file deleted, renamed or locked since the walk hands nothing back"* is historical. The drop it describes now has one cause, a document nothing was chunked from. Its decision stands: what a call carried can still be fewer than the cluster holds, so it is still recorded.

### 7. The lines an operator reads

Invariant 4 of the simplification plan is that operator-visible text does not change. This record changes it in the places below and nowhere else.

**Gone, because what they reported cannot happen:**

- the labelling page's *(the file could not be opened…)* and the warning beside it;
- 6b's warning that a document *"could not be read, so it is not among the documents this call was written from"*;
- 6b's warning that a survivor's *"content_hash cell is left blank and none of its pictures reach the tree"*;
- `could not hash <path>` as the failure of 5c, 5d, 5f or 6a. Stage 2 can still fail on it, for a file stage 1 left unhashed. Seed extraction cannot: it catches the same failure and records the seed as one whose file would not open, with a warning (ADR-155), so there it is never the step's failure.

**Reworded:**

- the labelling page's remaining fallback (§5), and the warning beside it, which says the document carries no cached conversion under the key recorded for it;
- the refusal of `vespera label --auto` with no root, which loses its closing clause *"because the openings put to the model are read from it"* (§4). A root is still never guessed (ADR-066).

**New:**

- the three errors of §4: a survivor with no key, a recorded key with no conversion in 5c, and a scoring run with no stage-2 run upstream of it;
- the warning of §5 under `vespera label --auto`: *"document <path> is in the label file but not in the walk of run <id>, so its opening is not shown"*.

**Two counters are renamed, because no file is hashed** (ADR-192 §4):

| Was | Is | Its unit is now |
| --- | --- | --- |
| `Stage 5d (relevance scoring, seed files hashed)` | `Stage 5d (relevance scoring, seed cache keys read)` | a usable seed whose key is read |
| `Stage 5f (clustering, files hashed)` | `Stage 5f (clustering, cache keys read)` | a member whose key is read |

Their totals and cadence stand. ADR-192 names the second counter in prose three more times, as *"5f's files hashed"* in §5 and in an alternative it refused, and as *"the one counter over the files hashed across both"* in its note on what pins two partitions: each reads *cache keys read*, and what each says of the counter stands. `Stage 6b (generation, cluster documents opened)` and `Stage 6b (generation, survivors listed)` keep their names; their units read *key* where ADR-192 wrote *file is hashed* and *file hash*. Each key read is a lookup of one row by its key, inside a loop that already has a counter. ADR-193 leaves such a statement out of scope, in its own words *"a statement whose cost does not grow with a table"*, so it takes no statement line.

### 8. What it saves, and what was not measured

**Counted from the code**: five full reads of every surviving file per campaign, up to two more for some, one more on every later tree write, and two of every usable seed after seed extraction's own (§Context). After this record: none of those. What is left is each stage's first read: stage 1 and stage 2 of a corpus file, once, and seed extraction of every seed, once in every invocation (§3). `DeliverableInvocationTest` counts it over two invocations: each document hashed once, the seed twice.

**Measured**, on synthetic files, JDK 26.0.2.1, with `ContentHashing.sha256`'s body copied into a throwaway probe: 1,000 MB in 100 files hashed in 0.79 to 0.87 s, and 500 MB in 2,000 files in 0.40 to 0.74 s, so 670 to 1,270 MB a second with the files in the operating system's cache. The digest is not the cost. The disk is: ADR-187 puts the archive's USB spinning disk at about 100 MB a second read end to end, and it seeks once per file.

**Not measured: the difference on a real working directory.** #349 asks for it in place of the plan's "removes 7 of the 8 archive re-reads". Taking it means reading an archive or a working directory, which no agent does (ADR-196). The operator already holds the figure for one full re-read: in the log of any run that reached 5f, the time between the first and last line of `Stage 5f (clustering, files hashed)` is a pass over every survivor that does nothing but hash. Five times that is the campaign's saving, to a first reading. It is owed to this record by the operator and is not asserted here.

**Found while measuring, and not decided here.** `ContentHashing.sha256` and `corpus.ContentHash.sha256` both call `readAllBytes` on the digesting stream, so each holds the whole file in memory while hashing it, where their comments say it is streamed. The probe hashed 512 MB under a 256 MB heap and got `OutOfMemoryError: Java heap space`, and 2,100 MB under a 4 GB heap and got `OutOfMemoryError: Required array size too large`: no file over 2 GB can be hashed at any heap. Every re-read site carried that, and after this record only stage 1, stage 2 and seed extraction do. #349 rules the two helpers out of scope, so this is a ticket of its own.

### 9. The schema version, the upgrade and the run ids

**`ExtractionSchema.VERSION` moves from 5 to 6**, in the commit that adds the table. A working directory written before it is refused by `SchemaVersionGuard`, with the message it already gives (ADR-059).

**The upgrade is a fresh working directory.** Nothing migrates the old one, and no key could be filled in for it without reading every file again. The extraction cache and the chunk cache are in that database, so the whole corpus is converted again. On the whole-archive run of 2026-10-04 stage 2 took 4 hours 23 minutes (ADR-192, Context). When to pay that is the operator's decision.

**Which run ids move** (ADR-058, `StageModules`). A fresh working directory mints every run anew, so this matters for a database that could be kept, and there is none. It is stated because the plan asks for it:

- **Stage 2** moves, through `extraction`, which gains the table's class and the version.
- **Stages 3, 4, both runs of stage 5, 6a and 6b** move, each through `extraction`, which every one of them names, through `pipeline`, where the seven sites are, and through the upstream chain.
- **Stage 1 does not move**: its only module is `corpus`, which this record does not touch. The census records a walk, which has no implementation version.
- `ledger` is in no stage's list.
- **No `ConfigConsumed` record and no module list changes**, so `RunIdentityGoldenTest` is not edited. Every `run.stage` and `finished_step` value is unchanged.

## Alternatives refused

- **A column on `extraction_metric`.** §1.
- **Keep an existence check or a cheap stat at each site**, so a missing file is still reported. It is the second, partial census ADR-104 refused, it has no verdict to record its finding as, and it would keep eight answers to one question.
- **Record the key only for survivors**, or only for conversions. Two rules, one per side, where §2 has one, and a failed document's cache row would stay unreachable from its occurrence.
- **Let 5c convert on a cache miss**, as it does today. §4.
- **Migrate version 5 in place.** The key cannot be derived from anything a version-5 database holds: `extraction_cache` is keyed by content, not by occurrence, and `corpus`'s table covers only occurrences with a same-size peer (ADR-151 measured 18 of 65). Filling it means the read of the whole archive this record removes, done once more by a code path nothing else would ever run.

## Consequences

- **No step after stage 2 depends on the archive being readable**, and none can fail, warn or leave a gap because of it. The archive is opened by the walk, by stage 1 and by stage 2. The seed set is opened by its own walk and by seed extraction, on every invocation.
- **A campaign's results describe the documents as stage 2 converted them.** ADR-016 already treats the corpus as static. Where it is not, the manifest's `content_hash` is how a consumer finds out (§5).
- **Existing tests that reached their case through a re-read are moved.** Three in `RelevanceReportInvocationTest` and one in `DeliverableInvocationTest` have their claims inverted. The tests in `GenerationInvocationTest`, `GenerationFaultInvocationTest`, `GenerationBreakerInvocationTest` and `GenerationReportsItsProgressInvocationTest` that used a file rewritten in place to reach a cluster nothing can be sent for keep every claim, and reach the cluster by removing the document's conversion and chunks from the caches instead (`ConversionOffTheRecordFixture`), which obstructs 6b before this record and after it.
- **`extraction` has one more table to keep in step with `extraction_metric`.** Nothing but §2's rule and the test over it keeps them so.

**What the implementation owes** (`spec-implementer`):

- the table and index of §1 in `schema.sql`, `ExtractionSchema.VERSION = 6`, and a reader and writer for the table in `extraction` that depends on `ledger` alone;
- §2's write in stage 2 and in `SeedExtractionItemWriter`, with the discards beside the metric discards;
- the seven corpus-side sites and the two seed-side ones reading the key (§4), 5c reading the cache only, and the three errors of §4;
- `DocumentOpening` without `FILE_COULD_NOT_BE_OPENED_FALLBACK`, with §5's wording;
- the two counter names of §7;
- `Ledger.stageOf(RunId)`, for `label --auto` to follow the upstream chain (§4);
- the javadoc that describes a re-read at each site, and `DoclingExtractor.contentHashFor`'s, which names the manifest as a caller;
- `AGENTS.md`'s count of decisions is raised with this record.

**What pins it:**

- `StageTwoRecordsItsCacheKeyInvocationTest`: the recorded key equals the SHA-256 of the file, computed with the JDK, and is a key of `extraction_cache`, for a document stage 1 hashed and for one it did not; §2's rule over a run holding a conversion, a failure and a fault; the seed side; every write made by the thread the step runs on; and the `run.stage` and `finished_step` names.
- `DeliverableInvocationTest`, three tests: over a campaign of two invocations each document is hashed once and each seed once in each invocation; a document rewritten in place is sent by 6b, listed under the hash of what stage 2 converted, and warned about nowhere; and a document gone before the tree is written keeps its `content_hash` cell (moved from the blank cell of ADR-151 §3).
- `RelevanceReportInvocationTest`, three tests moved from ADR-152's: the labelling page shows the opening of a document whose file will not open, and of one rewritten in place, and 5c, 5d, 5f and 6a finish over a file that will not open.
- `LocalLabellingInvocationTest`, two tests: `label --auto` puts the opening of a document rewritten in place, and asks about a path that is not in the run's walk with no opening and a warning.
- `OnlyTheFirstReadHashesAFileTest`: read off the compiled classes, no shipped class but the three of §4 calls `contentHashFor`, and none but seed extraction calls `SeedConversions.convert`.
- `ExtractionCacheKeyTableTest`: the table, its key, its index and its check.
- `ExtractionSchemaTest`: the version is 6, and a database recording 5 is refused.
- `StageFiveReportsItsProgressInvocationTest`: the two counter names.

**Also pinned**, each in a state no run leaves behind, reached by removing rows between two invocations (`ConversionOffTheRecordFixture`) or by recording none:

- the reworded fallback of §5, word for word, and its warning: `RelevanceReportInvocationTest.aSampledDocumentWithNoConversionOnRecordSaysSo`;
- 5c's error on a recorded key with no conversion, naming the occurrence, the key and the extractor identity: `RelevanceReportInvocationTest.theEmbeddingStepStopsOnADocumentWithNoConversionOnRecord`;
- the error on a document with no key, naming the occurrence and the run: `ExtractionCacheKeysTest`, over the reader itself, which every site goes through;
- §2's row for a reported timeout, and a resumed stage 2 keeping its keys: `StageTwoRecordsItsCacheKeyInvocationTest` and `ExtractionResumeInvocationTest`;
- `Ledger.stageOf`: `LedgerTest`.

**Not pinned:** the third error of §4, a scoring run with no stage-2 run upstream, which needs a `run_upstream` chain cut by hand; and §5's miss under `label --auto` after a changed Docling image, since every test's converter reports one image.
