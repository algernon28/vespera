# ADR-207 — A file is hashed through a fixed buffer, so its size sets no limit

- **Date**: 2026-10-07
- **Status**: accepted
- **Amends**: [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md), in one sentence of its Context: *"Hashing already streams, so this is ids, not file contents"*. It was not true when written (Context below) and is true from this record. ADR-200's decision does not rest on it and stands.
- **Amends**: [ADR-151](0151-the-manifests-content-hash-is-every-survivors-sha-256-taken-at-6b-through-extractions-own-hash.md), in one word of its Context: *"the same streamed SHA-256 over a file's whole content"*. Neither method streamed. The sentence's claim, that the two are one digest over the same bytes, was true and stays true (§1, §2).
- **Keeps**: [ADR-067](0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md) (SHA-256, in `corpus`, within a size shared by two or more), [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) (the module rule and its one exception), [ADR-155](0155-a-seed-file-that-will-not-open-is-recorded-under-a-reason-of-its-own-and-seed-extraction-records-no-completion-until-it-opens.md) (a seed file that will not open), [ADR-127](0127-a-database-lock-is-waited-out-by-sqlites-busy-timeout-not-by-hikaris-connection-timeout.md) and [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) (one writer, and the hash taken on the thread the step runs on), [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md) (no schema version moves), [ADR-206](0206-stage-2-records-the-key-it-looked-the-extraction-cache-up-under-and-no-step-after-it-opens-an-archive-file.md) (which three steps hash a file, and its §7 on stage 2).
- **Rests on**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) for which run ids move; a reading of both methods and of every caller at commit `251ee64`; one throwaway probe over sparse synthetic files; and one throwaway invocation test, run once and deleted (Context). No archive and no working directory was opened for this record (ADR-196).
- **Settles** the first point of [#449](https://github.com/algernon28/vespera/issues/449)'s scope, the question its *Not known* section asks, and what ADR-206 §8 found and left. **It does not settle the second point**, what a file that cannot be read means in stage 1 and stage 2: §4 says what happens today, why this record leaves it, and what is still to be decided. That point is carried by [#452](https://github.com/algernon28/vespera/issues/452).

## Context

**Both SHA-256 methods held the whole file in memory.** At `251ee64`, the commit this record was written against, `corpus.ContentHash.sha256`, which stage 1 uses, and `extraction.ContentHashing.sha256`, which stage 2 and seed extraction use for the extraction cache key, each wrapped the file in a `DigestInputStream` and called `readAllBytes()` on it. The digest was fed as the bytes passed, but `readAllBytes()` also gathers every byte into one array, which was thrown away. The javadoc of both said *"streamed rather than loaded whole"*; ADR-151 calls them *"the same streamed SHA-256"*, and ADR-200 says *"Hashing already streams"*. ADR-206 §8 found otherwise while measuring something else and left it.

**Where a file is hashed, at `251ee64`.**

| Step | Call | Which files |
| --- | --- | --- |
| stage 1, second pass | `ContentIdentityResolution.resolveGroupSharingASize` calls `ContentHash.sha256` | every survivor of the first pass that shares its size with another (ADR-067) |
| stage 2, reader | `ConversionDispatch.dispatchIfConvertible` calls `DoclingExtractor.contentHashFor` | every survivor with a detected format that stage 1 recorded no hash for |
| stage 2, per-occurrence step | `ExtractionItemProcessor.contentHashOf` calls the same | the same, and only where the reader filed no key for the occurrence: in the job the reader always files one |
| seed extraction | `SeedExtractionItemProcessor.doProcess` calls the same | every seed, on every invocation (ADR-155 §5) |

`OnlyTheFirstReadHashesAFileTest` holds that no other shipped class calls `contentHashFor`. All three stage-2 and seed calls run on the thread the step runs on, one file at a time; no conversion worker hashes.

**One more method hashes, and no run reaches it.** `DoclingExtractor.convert(Path, ExtractorIdentity, DetectedFormat, DetectedSubtype)` calls `ContentHashing.sha256` itself. It has no caller in `src/main`: the two shipped callers of `convert`, `ExtractionItemProcessor.convertNow` and `SeedConversions.convert`, both pass the hash they already hold.

**The limit, reproduced.** A throwaway probe outside the repository, JDK 26.0.2.1, sparse files on NTFS, with the body `ContentHashing.sha256` had at `251ee64` copied in beside a loop over one buffer. *Allocated* is what the JVM counted for the hashing thread.

| File | Heap | Gathering the file, as both methods did | Through one 64 KiB buffer |
| --- | --- | --- | --- |
| 16 MiB | 256 MiB | hashed; 34,004,736 bytes allocated, twice the file | hashed; 66,648 bytes allocated |
| 512 MiB | 256 MiB | `OutOfMemoryError: Java heap space` | hashed in 0.26 s; 66,584 bytes allocated |
| 2,100 MiB | 4 GiB | `OutOfMemoryError: Required array size too large` | hashed in 1.06 s; 66,800 bytes allocated |
| 2 GiB and 1 MiB | 4 GiB | `OutOfMemoryError: Required array size too large` | hashed in 1.04 s; 66,584 bytes allocated |
| 2 GiB and 1 MiB | 15.4 GiB, this machine's default | `OutOfMemoryError: Required array size too large` | hashed in 1.03 s; 66,800 bytes allocated |

So a file over 2 GiB could not be hashed at any heap, because no Java array is that long. Below that the limit is nearer half the heap than the heap, since gathering allocates twice the file; where exactly it falls was not measured. Wherever both methods produced a value, the values were equal.

**What an invocation does when hashing throws, which #449 had not checked.** Measured at `251ee64` by one throwaway test over the whole job, with synthetic files, run once and deleted. Nothing this record changes alters any row: it changes how the bytes are read and not what a thrown exception or error does. The `Error` was a real `OutOfMemoryError` raised by hand where the file is hashed.

| Where | What was thrown | What happened |
| --- | --- | --- |
| stage 2 | `UncheckedIOException: could not hash <path>`, the file having been moved away as it was read | The step failed and the invocation exited 1. Spring Batch wrapped it as `NonSkippableReadException`: it is thrown from the reader, and the one exception type the step skips is thrown by the per-occurrence step. Stage 2's closing line named `could not hash <path>` and said to run the same command again. No verdict and no metric row was written. |
| stage 2 | `OutOfMemoryError` | The same path: wrapped as `NonSkippableReadException`, the step failed, exit 1, no verdict and no metric row, and the error's message in the closing line. |
| seed extraction | `OutOfMemoryError` | The step failed and the invocation exited 1: *"Stage 5a (seed extraction) failed before it had read the whole seed folder, so stage 5 minted no run and nothing is concluded about the seeds"*. Stages 1 to 4 stayed recorded as finished. |
| seed extraction | `UncheckedIOException` | Caught, since ADR-155: the seed is recorded under a reason of its own, the step records no completion, stage 5 goes no further and the invocation exits 0. Not run again here; `SeedExtractionInvocationTest` and `EmbeddingScoringInvocationTest` hold it. |
| stage 1 | `IOException`, a region in the middle of one of two same-size files being locked by another handle (Windows) | The step failed and the invocation exited 1. **Nothing of stage 1 was left**: no run, no verdict and no content hash for that walk, because stage 1 is one tasklet in one transaction. The first pass had read the same file without complaint: of a text that size it reads the first and last 64 KiB, and the lock was between them. |
| stage 1 | an `Error` | Not run: `corpus` has no seam to raise one at. Read from the code, nothing between `ContentHash.sha256` and the step catches anything, so it takes the path of the row above. |

Two things follow. **An `Error` does not escape and is not swallowed**: no `catch (Throwable` and no `catch (Error` exists in `src/main`, Spring Batch ends the step as failed, and the command returns 1 with a closing line. And **a file too large to hash stopped the same step on every invocation**: stage 2 resumes from its committed chunks (ADR-181) and meets the file again, and stage 1 starts from nothing each time. An archive holding one such file could not get past the stage that hashed it.

## Decision

### 1. Both methods read the file through one buffer of 64 KiB

Each method allocates one `byte[65_536]` per call, reads the file into it until the stream ends, and gives the digest exactly the bytes each read returned. Nothing else is held: what a hash allocates does not grow with the file.

**64 KiB is measured, not assumed.** Over the 2 GiB file, with the default heap:

| Buffer | Time | Allocated |
| --- | --- | --- |
| 8 KiB | 1.32 s | 14,304 bytes |
| 64 KiB | 1.03 s | 66,800 bytes |
| 1 MiB | 1.00 s | 1,049,688 bytes |

8 KiB is about a quarter slower, and 1 MiB buys nothing for sixteen times the memory. The files were sparse and in the operating system's cache, so these are the digest's times; on the archive's disk the read is the cost (ADR-206 §8), and the buffer does not change it.

**Every hash is the value it was.** The digest is given the same bytes in the same order, and the text is the same 64 lowercase hexadecimal characters. So no content identity changes, no `content_hash` row, no extraction cache key, no chunk cache key and no `extraction_cache_key` row: every value recorded in an existing working directory is still the value of its file.

**What does not change in either method**: the signature, the algorithm, the hex text, and how a failed read leaves it. `ContentHash.sha256` still throws the `IOException`. `ContentHashing.sha256` still throws `UncheckedIOException` with the message `could not hash <path>`, which ADR-155 fixed and its tests read.

### 2. The two methods stay two

One shared method was the alternative, and the module rule decides it. `extraction` may depend on `ledger`, and on `corpus` for `DetectedFormat` and `DetectedSubtype` alone (ADR-100). What the build checks is looser than that: `extraction`'s declaration names the whole of `corpus`, so `ApplicationModules.verify()` would accept a call to `corpus.ContentHash`. ADR-100 and the declaration's own comment are what forbid it: *"Widening it to anything else in `corpus` is a decision, not a convenience."*

**It is not widened.** A second dependency on `corpus` would be bought to save a loop of four lines, and `ledger` is no home for it either: it records what exists and what was judged, and reads no file. The cost of two bodies is that they could drift, and that is held by tests at every length that matters, the largest included (What pins it).

### 3. An `Error` thrown while a file is hashed is caught by nothing

After §1 no file's size can raise `OutOfMemoryError` in either method. One that still arrives there says the JVM is out of memory for another reason: up to eight conversions are in flight beside the step thread (ADR-140), and each can hold a text of up to 64 MB (§6).

**It is not caught, at any of the three places.** Catching it to mark the file would be wrong three ways:

- **It would blame a file for a fact about the process.** The file that happened to be in hand is not why memory ran out.
- **It would not stop at one file.** Whatever exhausted the heap is still there for the next file, and the one after: a rule written for one bad row would remove every row that followed, which is the shape ADR-175 §3a was written to prevent.
- **A JVM that has thrown an `Error` promises nothing about its own state.** Writing verdicts from it and carrying on is not a risk worth one file.

What happens instead is what Context measured: the step fails, the invocation exits 1, the closing line names the error, and nothing is recorded against any file. For stage 2 the next invocation resumes from the committed chunks (ADR-181).

ADR-155's catch in seed extraction stays as narrow as it is: `UncheckedIOException` from hashing, and nothing else.

### 4. What a file that still cannot be hashed means: unchanged here, and not yet what the operator's rule asks

After §1 one cause is left: the file system will not hand the file over. It is locked by another program, it has gone since the walk, its permission was removed, or a read fails partway through it.

| Where | Today, and after this record | Is it *"a bad row fails the occurrence, not the run"*? |
| --- | --- | --- |
| seed extraction | ADR-155: recorded as an unusable seed under *"the file could not be opened when seed extraction read it"*, named in a warning, no completion recorded, stage 5 gated, exit 0 | **Yes, and nothing changes.** A seed is not a candidate, so no verdict applies; a missing seed moves every score, so it is the kind of fault that stops stage 5, and ADR-155 stops it without failing the invocation. |
| stage 2 | the step fails on `could not hash <path>`, exit 1 (ADR-206 §7 says as much) | **No.** ADR-175's rule is that a file that fails is marked and skipped, and listed in `extraction-failures.html`. |
| stage 1 | the step fails, exit 1, and the whole stage's work is rolled back | **No**, and it is the costliest of the three: one locked file among those sharing a size costs the broken check and every hash already taken. |

**This record does not change stage 1 or stage 2**, and #449's second point stays open, as #452. The change #449 asks for, the occurrence fails and the step goes on, is not safe to make alone, and what makes it safe is the operator's to decide:

- **Today's stop is the only thing that notices an archive that has gone.** The archive this tool exists for is on a USB disk. If it drops out during stage 2, the first file stage 1 left unhashed stops the step, and nothing is removed. With the occurrence marked and the step going on, every file after it would be marked too, the step would complete, and the invocation would exit 0 with the rest of the archive removed under that run. That is the outcome ADR-175 §3a bounded for a converter that converts nothing, on the operator's decision of 2026-10-03, and marking a file that cannot be read needs its own bound for the same reason. A bound has to say what stands in for the control conversion (ADR-184) when the thing that may be gone is the archive.
- **The same gap is already open beside it, read from the code and not run.** `BrokenCheck.check` turns an `IOException` from a file's size or its first bytes into `broken` (*"the file could not be read: …"*, *"the file could not be opened: …"*), so an archive that goes away during stage 1's first pass would leave every remaining file `broken` and the stage finished. And stage 2 sends a file stage 1 did hash to the converter without reading it first; what a file gone by then earns was not run here. A bound decided for hashing alone would leave both.
- **A lock is a fact about the archive at one moment, and a verdict under a finished run is for good.** ADR-152 and ADR-155 drew that line and refused to seal such a fact under a step that records completion. ADR-175 marks and moves on, and leaves asking again to [#386](https://github.com/algernon28/vespera/issues/386). Which of the two a file that would not open at stage 1 or stage 2 follows is a choice between two recorded rules.

**What is to be decided, so that the ticket that takes it starts from the code:**

- for stage 2, whether the occurrence earns `extraction-failed` with a reason of its own, is listed in the review list, earns no metric row and so no key row (ADR-206 §2), and counts as no evidence about the converter (ADR-184 §4);
- for stage 1, whether it earns `broken`, as `BrokenCheck` already gives a file it cannot open, or is left unhashed for stage 2 to meet;
- for both, and for the first pass and the conversion beside them, what stops the step when it is the archive that has gone and not the file.

Until then a locked or vanished file stops stage 1 or stage 2 as it did, with the file named, and the remedy is ADR-152's: release it, or let the next walk observe that it is gone, and run the same command again.

### 5. Which run ids move, and what is kept

**The commit that carries this record also changes the two methods**, one in `corpus` and one in `extraction`, and a stage's implementation version is the last commit touching its module under `src/main` (ADR-058). So the ids below move at that commit, read against `StageModules`:

- **Stage 1 moves**: its only module is `corpus`, where `ContentHash` is.
- **Stage 2 moves**, through `extraction`, where `ContentHashing` is, and through its upstream run (ADR-048).
- **Stages 3 and 4, both runs of stage 5, 6a and 6b move**, each through `extraction`, which every one of them names, and through the upstream chain.
- The census records a walk, which has no implementation version. `ledger` is in no stage's list.
- **No `ConfigConsumed` record and no module list changes**, so `RunIdentityGoldenTest` is not edited. `TextParts.RULE` and `DoclingClient.sentOptions()` are not touched, so the extractor identity is the one it was.

**No schema version moves.** No table, column or index changes, so `CorpusSchema.VERSION` and `ExtractionSchema.VERSION` stay and `SchemaVersionGuard` accepts a working directory written by the build before.

**An existing working directory is kept, and every stage runs again over it.** Stage 1 reads the archive again: the broken check, and a hash of every file sharing a size. Stage 2 reads every survivor stage 1 recorded no hash for, to find its key, and then finds every conversion in the extraction cache, because the keys are the same values under the same extractor identity; nothing is converted again. The chunk cache and whatever else is keyed by a content hash still match for the same reason. What the replay costs on the whole archive is one full read of each surviving file, stage 1's of those sharing a size and stage 2's of the rest, and no conversion; it is not measured here. ADR-206's own upgrade is a fresh working directory, which has nothing to replay, so a build that ships both changes costs nothing beyond that fresh start.

### 6. The other whole-file reads, checked and bounded

#449 left three reads to be looked at. None meets the limit this record removes.

| Read | Reached only when | Most it holds |
| --- | --- | --- |
| `TextParts.convertInParts`, `Files.readAllBytes` | `TextParts.convertedInParts` said yes, which requires the file's size to be at most `LARGEST_TEXT_BYTES` | 64,000,000 bytes |
| `TextParts.cut(Path, long)`, `Files.readAllBytes` | never in `src/main`: its only callers are in `TextInPartsTest`, and `convertInParts` calls the overload that takes bytes | unbounded, and unreachable from a run |
| `TimestampedLines.count`, `Files.readAllBytes` | the file's size is at most `WHOLE_FILE_LIMIT_BYTES` | 131,072 bytes; a larger file is read as two windows of 65,536 |

**`convertInParts` is bounded but not small.** It runs wherever the converter is called, which is a conversion worker for every call the reader dispatched, and eight workers run at once (ADR-140), so eight texts of 64 MB can be held together, 512 MB, beside the answers each part brings back. That is a fixed ceiling and not a limit that grows with a file, so it is no ticket of this kind; whether it fits a small heap is not measured.

Each of the two guarded reads checks the size and then reads, as two calls. A file that grew between them is read at its new size. ADR-016 treats the corpus as static, and nothing here changes that.

**Not looked at**: whether the converter's client holds a file in memory while it posts it. #449 rules conversion of very large files out of scope until it is measured.

## Alternatives refused

- **One shared method in `corpus` or `ledger`.** §2.
- **A size limit and a warning asking the operator to split or skip a large file.** #449 rules it out, and §1 leaves nothing to warn about: hashing sets no limit.
- **Catching `OutOfMemoryError` and marking the file.** §3.
- **A memory-mapped file or a `FileChannel` with a direct buffer.** A mapping of a file over 2 GiB needs more than one, holds address space the JVM frees only at a collection, and on Windows keeps the file from being deleted or renamed while it is mapped. The measured channel read was no faster than the stream (1.13 s against 1.03 s).
- **Marking the occurrence and going on in stage 1 and stage 2, in this record.** §4: without a bound it turns a stop into the quiet removal of whatever is left of an archive.

## Consequences

- **A file's size no longer decides whether it can be hashed**, in stage 1, stage 2 or seed extraction, at any heap. The file that stopped the same step on every invocation is hashed in memory a small file needs.
- **Stage 1 and stage 2 hash in about 64 KiB each, whatever they read.** ADR-200's bound for stage 1, the survivors sharing one size, is now the whole of what the second pass holds: the sentence that said so is true.
- **Every run id from stage 1 on moves once**, and the working directory is kept (§5).
- **A file that cannot be read still stops stage 1 and stage 2**, which is not what the operator's rule asks, and is stated here as open (§4).
- **`AGENTS.md`** counts this record and says what is closed and what is not.

**What changed in `src/main`**, in the commit that carries this record, and nowhere else for it:

- `corpus/ContentHash.java` and `extraction/ContentHashing.java`: the `DigestInputStream` and its `readAllBytes()` are replaced by one `byte[]` of 65,536 bytes, a loop of `InputStream.read(buffer)` until it returns -1, and `MessageDigest.update(buffer, 0, read)` for each read. Nothing else in either method changed: not the signature, the exception, the message `could not hash <path>`, or the hex text.
- The buffer's length is a private constant in each, `BUFFER_BYTES = 65_536`, with this record cited. The javadoc of each method, which said *"streamed rather than loaded whole"*, now says the content is *"read through one buffer of {@link #BUFFER_BYTES} bytes and never gathered whole (ADR-207)"*.
- `ContentHashing`'s class javadoc, in the same file, was corrected to what ADR-206 made of the hash. It said the hash *"is used only to key `extraction`'s own cache row"*; it now says the hash keys `extraction`'s cache rows, that the steps reading a file for the first time record it in `extraction_cache_key`, and that every later step reads it from there and none hashes the file again. Its sentence about `corpus`'s `content_hash` table is unchanged.
- No catch was added anywhere for §3, and nothing in `pipeline` changed.

**What pins it:**

- `extraction.AFileOfAnySizeIsHashedTest`, four tests, each claim made of both methods:
  - a sparse file of 2,148,532,224 bytes, 2 GiB and 1 MiB, is hashed with nothing thrown, to the value a `FileChannel` reader in the test gives. No array can be that long, so this is proof by construction that the file is not gathered, at whatever heap the suite runs with. **It was red before §1 was built**: it failed on `OutOfMemoryError: Required array size too large`, caught by the test and reported as one failure.
  - hashing 16 MiB and then 256 MiB each allocates under 1 MiB on the hashing thread, read from the JVM's own per-thread count, after one unmeasured call that loads the classes. **It was red before §1 was built**: gathering allocated twice the file.
  - files of 0, 1, 65,535, 65,536, 65,537, 131,072, 131,073 and 1,048,579 seeded bytes hash to the JDK's digest of the same bytes handed over in one piece. It was green before §1 was built and is green after: it is the claim that no value changes.
  - the empty file and `abc` hash to their published digests by both methods. Green before and after likewise; `ContentHashingTest` already held it for `extraction`'s method alone.
- `pipeline.AnErrorWhileHashingStopsTheStepInvocationTest`, two tests, green before §1 was built and after, holding §3: an `OutOfMemoryError` raised where stage 2 hashes a file fails the step, leaves stage 1 finished and no verdict against any file, and is named in the closing line; one raised where a seed is hashed fails seed extraction, leaves stage 2 finished and no run over the seed walk, and so no `unusable_seed` row.
- `extraction.ContentHashingTest`, unchanged and green: the two methods agree, and the published digests.
- `SeedExtractionInvocationTest` and `EmbeddingScoringInvocationTest`, unchanged: §4's row for seed extraction.

**The fixtures are sparse files**, a few bytes written at the start, across the end of the first 64 KiB, across the 2 GiB mark and at the end, and the rest a hole. A test is aborted by assumption, with the reason, and reported as skipped in two cases: the temporary directory's file system is FAT or exFAT by name, or it refuses the file. On a file system with no sparse files that the list does not name, the fixture is written out in full, which is slow and still correct. The class, four tests, ran green in 3.673 s on the machine it was written on (NTFS): it reads the 2 GiB file three times, about one second each.

**Not pinned, and why:**

- the buffer's length: `BUFFER_BYTES` is private in both classes, so no test names it. The allocation bound holds it to under 1 MiB;
- §4's rows for stage 1 and stage 2: they describe behaviour this record says should change, and a test would have to be inverted by the change that follows;
- §3 for stage 1, which has no seam to raise an `Error` at;
- §5: `RunIdentityGoldenTest` holds that no `config_consumed` text moves, and nothing in a test can see a commit.
