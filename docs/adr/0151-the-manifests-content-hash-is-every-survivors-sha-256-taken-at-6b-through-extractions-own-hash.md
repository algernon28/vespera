# ADR-151 — The manifest's content hash is every survivor's SHA-256, taken at 6b through extraction's own hash

- **Date**: 2026-09-25
- **Status**: accepted
- **Amends**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) — **one sentence only**: "every column is one the ledger already holds, so it costs a query". That is false of `content_hash`, and this record says what the column costs instead. The column list, the header, the meaning of the column and everything else ADR-104 and [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md) decided about the manifest stand.
- **Leaves alone**: [ADR-067](0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md). Stage 1 still hashes only within size-matched groups, and `corpus` still owns the `content_hash` table and the relation of content identity recorded in it ([ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md)).
- **Amends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) — **three sentences in two passages, all made false by this record, and §5 states what each should read**. That record reaches each survivor's pictures by hashing the file through `DoclingExtractor.contentHashFor`. It named #287 as the likely remedy for that re-read of the archive. This record moves the manifest onto the same source and shares the one read between the two. It does not remove the read (§5).
- **Settles** [#287](https://github.com/algernon28/vespera/issues/287).

## Context

`documents.csv` has carried a `content_hash` column since ADR-104. `GenerationTasklet.survivorsFor` fills it from `corpus`'s `content_hash` table, under a stage-1 run id that 6b re-derives. `ListedSurvivor.contentHash` documents the value as "the SHA-256 stage 1 recorded for it (ADR-067)".

ADR-067 hashes only within size-matched groups. An occurrence whose size no other occurrence shares cannot be a byte-exact duplicate, so stage 1 never hashes it. That is correct for stage 1's purpose. It also means the table covers only occurrences that had a same-size peer, and the manifest reads it as though it covered every survivor.

**Measured** on the GesPOS run of 2026-09-24 (`vespera.db`, opened read-only, reproducing `survivorsFor`'s lookup):

| | |
| --- | --- |
| survivors in `document_cluster` | 65 |
| with a row in `content_hash` under the one byte-level-reduction run | 18 |
| without one, so a blank `content_hash` cell | **47** |
| of those 47, how many share a size with any occurrence of the walk | 0 |
| survivors whose SHA-256, recomputed from the archive, is a key of `extraction_cache` | 65 |
| of the 18 stage-1 hashes, how many equal the SHA-256 recomputed from the archive | 18 |

**The two hashes are one digest over the same bytes.** `corpus.ContentHash.sha256` and `extraction.ContentHashing.sha256` are the same streamed SHA-256 over a file's whole content, written as 64 lowercase hex characters. The two bodies differ only in how an `IOException` leaves them. `ContentHashingTest.agreesWithTheHashCorpusRecordsForTheSameBytes` already pins the agreement. Stage 2 keys `extraction_cache` on `corpus`'s value where one is recorded and on its own otherwise, in `ConversionDispatch` and `ExtractionItemProcessor`. Under [ADR-016](0016-the-corpus-is-treated-as-static.md) those give the same value. So the gap is a gap in *where the value is read from*, not in what the value is.

**There is a second way to get a blank cell**, and it hits every row at once. 6b re-derives stage 1's run id from `corpus`'s current implementation version. If `corpus` has changed since stage 1 ran, the re-derived id names no run, and every row of the manifest is blank, including the ones that had a same-size peer.

## Decision

### 1. What the column means

**`content_hash` is the survivor's content identity: the SHA-256 of the file's bytes, as 64 lowercase hex characters.** It serves two readers, and one value serves both:

- **A consumer joining on content identity.** It is the key `extraction_cache` files that document's conversion under. Two rows that carry the same value are byte-identical.
- **A checksum of the archive.** ADR-104 makes the tree re-pointable when the archive moves. An operator who re-points it can recompute the digest of each file and check that the path still names the bytes that were arranged.

The meaning does not change, so **the header does not change**. It stays `occurrence_id,path,content_hash,winning_seed,relevance_score,seed_partition,cluster,partition_order,cluster_order`. The format does not change either: a consumer who read the 18 filled cells before reads the same 18 strings after.

### 2. Where every row's value comes from

**Every row's value is `DoclingExtractor.contentHashFor` over the survivor's file, called by `GenerationTasklet` at 6b.** That is one source for every row. `corpus`'s table is not consulted, and there is no fallback between two sources.

- **Which module computes what.**
  - `corpus` computes stage 1's hashes within size-matched groups and records them in its own `content_hash` table. This is unchanged, and nothing here writes to that table.
  - `extraction` computes the SHA-256 of any file it is handed, through `contentHashFor`. This is its existing public method, and its existing copy of the digest (ADR-040 forbids it from naming `corpus`). It records nothing new.
  - `pipeline` resolves each survivor's root-relative path under the walk's canonical root, calls `contentHashFor`, and puts the result into `ListedSurvivor.contentHash`.
  - `synthesis` writes the value it is handed, as it does today.
- **How the value reaches 6b.** `pipeline` already depends on `extraction`. Every stage after stage 2 already finds a document's cache key this way: clustering, embedding scoring, relevance scoring, the relevance report, 6a's titles and 6b's exemplars. ADR-149 does the same for pictures. The manifest was the one reader that used `corpus`'s table instead.
- **`GenerationTasklet` stops reading `ContentIdentity`**, and stops re-deriving stage 1's run id. Both existed only to fill this column. Removing them also removes the second route to a blank cell described above.
- **One hash per survivor per tree write.** The manifest and ADR-149's picture lookup need the same value for the same file. `GenerationTasklet.writeDeliverable` builds one map from occurrence to hash for each tree it writes, and hands it to both `survivorsFor` and `survivorPictures`. Both read through one `hashOf`. So each survivor's file is read at most once, and warned about at most once, whichever reader asks first.

### 3. A file the archive will not hand over

**Its row is still written, with a blank `content_hash` cell, and a warning names the file and the column.** The same warning says that none of the file's pictures reach the tree, because the picture lookup has no key either. Every survivor is listed (ADR-104), so the row cannot be dropped. The run does not fail: a missing file is a fact about that one document, and `openingChunkOf` already treats it that way, for the same reason.

This is the only blank cell left. It means something different from the old blank cell: the file could not be read when the tree was written, not that nothing ever hashed it.

Falling back to `corpus`'s recorded hash for such a file was refused. A cell would then carry the identity of bytes that 6b could not see. The cell would also have two sources, and a reader would have no way to tell which one filled it.

**An invocation reaches this case in two ways.** Stage 5's relevance report reads a file only if the file is in its sample. `RelevanceReportTasklet.textOpeningOf` runs for `distribution.sample()` alone, which holds at most `RelevanceDistribution.SAMPLED_PER_BAND` documents per band: 12 in each of 5 bands, so at most 60 per scoring run. A survivor outside the sample that cannot be read passes stage 5 without being opened and reaches 6b. So does any file that goes away after the report has run, for example while 6b is waiting on the model. A first attempt at a test locked one document across the invocation and stopped at the report. It stopped there only because both of its two documents were in the sample, which a corpus that small cannot avoid.

**`DeliverableInvocationTest.leavesTheContentHashBlankForADocumentGoneBeforeTheTreeIsWritten` pins the rule.** It deletes one of its two documents while the model is being asked, after that call's documents were read and before `documents.csv` is written. It then checks three things: both rows are still listed, the deleted file's cell is blank while the other carries the SHA-256 of its bytes, and one warning names the file and `content_hash`.

Whether the relevance report should fail the step on one unreadable file is [#289](https://github.com/algernon28/vespera/issues/289)'s question, not this record's.

### 4. What ADR-104's sentence becomes

ADR-104 says "every column is one the ledger already holds, so it costs a query". For `content_hash` that is now false. **It costs one full read of each survivor's file at 6b.** That read adds nothing: ADR-149 §9 already reads every listed survivor on every tree write, and the manifest takes its value from that same read. That is why the cost is accepted without measurement.

ADR-104's refusal to have 6b check that the originals still exist stands. Hashing a file is not an existence check, and a failed read earns no verdict: it earns the blank cell and the warning in §3.

### 5. What this record does to ADR-149's re-read

ADR-149 §9 named #287 as the likely remedy for its read of every surviving original on every tree write. It said that if this record gave each survivor a recorded extraction hash, the re-read would disappear. Its Consequences said settling #287 "may also remove" it.

**It does not remove it.** This record shares the read between the manifest and the pictures, so it is paid once per survivor per tree write instead of twice. Removing the read would need the per-occurrence hash recorded at stage 2, which this record refuses (Alternatives refused). That is left until 6b's reads are measured to matter.

**Three of ADR-149's sentences, in two passages, are made false by this record.** ADR-149's text is not edited, because `docs/adr/README.md` reopens a record only by a later record. This section is that later record, and each correction is made here.

- **Its §9, first bullet**, says of `ListedSurvivor.contentHash`: *"That value is `corpus`'s stage-1 hash, which exists only for survivors that shared a size with another file."* **Read instead**: *that value is `extraction`'s hash of the survivor's file, taken at 6b, and every survivor the archive hands over has one (ADR-151).* The bullet's rule stands: the picture lookup still does not read the `ListedSurvivor` field. It no longer needs a reason to avoid it, because since this record the two are one value by construction, read through the one map of §2.
- **Its "Found while measuring" paragraph** says the column *"is filled from `corpus`'s stage-1 hash"* and that *"`GenerationTasklet` logs a warning for each"* of the 47 blank rows. Both are historical. The column is filled as §2 says, and the only blank cell left is §3's, with its own warning. The paragraph's last clause, that settling #287 *"may also remove §9's re-read of every original"*, is answered above: it does not.

## Alternatives refused

- **Hash every survivor at stage 1.** This would make `corpus`'s table complete by widening ADR-067's scope. ADR-067's scope is correct for its purpose, and widening it would change stage 1's run identity on every archive to serve a 6b need. Stage 2 already hashes every survivor that stage 1 did not.
- **Keep reading `corpus`'s table and fall back to `contentHashFor` on a miss.** This gives the same values for the 18 rows that have one, and the extra branch buys nothing. It would also keep the dependency on a re-derived stage-1 run id, which can blank every row.
- **Record extraction's hash per occurrence in a new table**, so that 6b reads without touching the archive. That would make a second recorded byte identity beside `corpus`'s, need a schema change and a stage-2 re-run, and split the pattern every downstream stage follows. It is the route to take if 6b's reads are ever measured to matter. They have not been.
- **Drop the column.** ADR-104 put it there so an operator could build what comes after the hand-off without opening the database. Joining on content identity and checking a re-pointed archive are both things it is for. Now that every row can carry it, it costs one read per survivor.

## Consequences

- **Every GesPOS row carries a value**: 65 of 65 where there were 18. A value is the same string stage 1 recorded wherever stage 1 recorded one.
- **What pins it.** `DeliverableInvocationTest.fillsTheContentHashOfADocumentWithNoPeerOfItsSize` runs a whole invocation over two documents of different sizes. It first checks that stage 1 recorded no hash for either, then checks that each row's cell equals the SHA-256 of that file, computed in the test with the JDK. `DeliverableInvocationTest.leavesTheContentHashBlankForADocumentGoneBeforeTheTreeIsWritten` pins §3's blank cell and its warning, through `GenerationScriptedBeans.duringEachCall`, which deletes a document while the model is being asked. `DeliverablePicturesInvocationTest` still passes over the shared map, so pictures reach their entries through the same hash. No test counts reads of a readable file, so for one the file is read once only because every reader goes through the code's single map. For an unreadable file, `leavesTheContentHashBlankForADocumentGoneBeforeTheTreeIsWritten`'s single warning pins that the manifest and both picture passes share one attempt.
- **`ListedSurvivor.contentHash`'s javadoc was false**, and it was corrected in the change that ships this record.
- **The manifest is no longer written without touching the archive.** `GenerationTasklet.writeDeliverable`'s javadoc said it was, citing ADR-104. ADR-104 never promised that: it forbade an existence check, not a read.
- **`content_hash` is keyed to the bytes at write time, not at walk time.** Under ADR-016 these are the same. If they are not, the manifest states what 6b read, and so does the picture lookup that shares its hash.
