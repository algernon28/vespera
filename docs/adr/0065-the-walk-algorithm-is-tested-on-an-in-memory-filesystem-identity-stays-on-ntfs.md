# ADR-065 — The walk algorithm is tested on an in-memory filesystem; identity stays on NTFS

- **Date**: 2026-08-31
- **Status**: accepted
- **Amends**: [ADR-063](0063-census-fixtures-are-generated-in-test-scale-is-measured-not-tested.md)

## Context

[ADR-063](0063-census-fixtures-are-generated-in-test-scale-is-measured-not-tested.md) settled how census is tested: fixtures generated in-test via `@TempDir`, never checked in, with `Assumptions.assumeTrue`/`abort` where a given Windows environment cannot create one. It owed the suite two helpers — a permission-denied directory and an over-long path — and ruled Linux CI out of scope on the grounds that the software is Windows-only.

Building the census slice (issue #20) turned three of those expectations into measurements.

**The over-long path cannot be built as a fixture at all.** ADR-063 assumed it could be, by "nesting directories past NTFS's ~32,000-character limit". An entry has to exist for a walk to meet it, and the entries this case is about are exactly the ones the filesystem refuses to create — a refused component leaves nothing behind to walk. The JDK also opts Windows paths into the long-path form, so the classic 260-character limit is not where a refusal lands either. The helper ADR-063 owed is not merely unwritten; it is not writable.

**The permission-denied fixture skips more than it tests.** Written against `AclFileAttributeView` as ADR-063 directed, it depends on the volume supporting ACLs, on the account being able to deny itself, and on the process not running as an administrator or as root — the last of which is the normal case in a container. Its `assumeTrue` guards are correct and load-bearing, and the result is a test that reports as skipped in exactly the environments most likely to run it unattended.

**The resume arithmetic is the least-tested code in the slice and the most likely to be wrong.** [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md)'s checkpoint is a path of per-level ordinal positions, and `Walk` classifies every entry against it as done, ancestor or pending. That arithmetic breaks on depth and width, and `@TempDir` fixtures of three or four entries are affordable in a unit suite precisely because they exercise none of it. Real disk I/O is what makes a thousand-entry fixture too slow to write casually.

A throwaway probe (outside the repository, per ADR-063's own practice) ran the unmodified `Walk` against `com.github.marschall:memoryfilesystem:2.8.1` and measured:

- The walk runs against an injected `FileSystem` with **no production change**. `Walk`, `WalkRecorder`, `OccurrencePath` and `ProfileStore` touch only `Files.*` and `Path`, which delegate to the path's own provider.
- `chmod 000` is **enforced**: the walk met `AccessDeniedException` through `visitFileFailed`, recorded exactly one `UNPROCESSABLE` anomaly, carried on past it, and the accounting identity held. Deterministic, and independent of volume, privilege and platform.
- There is **no path-length ceiling** to provoke: 300 nested components, ~60,300 characters, accepted without complaint. The library offers no API to impose one.
- **1000 files across 40 directories walked in 10ms**, and a resume from the twentieth checkpoint recorded exactly the 475 files and 19 directories remaining, with the root correctly not recounted.
- Its Windows model **contradicts measured NTFS on the case ADR-051 exists for**. Plain case-insensitivity matches: `Report.txt` and `report.txt` collide, as on NTFS. But after writing `ı.txt` (dotless i), the library reports `I.txt` as existing — folding them together, where NTFS keeps them as different files. That is the exact disagreement [ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md) was written about, and the library gets it backwards.

The last measurement is the important one. Case sensitivity, collation and lookup transformation are all configurable in the library, so the model *could* be bent to match NTFS — at which point the fixture encodes an author's belief about NTFS and the test can only ever confirm that belief. "Measure rather than argue" is what rules that out.

## Decision

**A second fixture mechanism is admitted: an in-memory filesystem**, `com.github.marschall:memoryfilesystem`, test scope. ADR-063's "fixtures are generated in-test, never checked in" is unchanged and now covers both mechanisms — an in-memory tree is still built inside the test that needs it.

**The two mechanisms divide on what the test is evidence for.**

*In-memory, because the question is about the walk's own logic:*

- the accounting identity behind "excludes nothing" ([ADR-056](0056-excludes-nothing-is-checked-by-reconciliation-at-finish.md)), including at a scale worth calling a scale;
- checkpoint ordinals, subtree skipping, and the done/ancestor/pending classification on resume (ADR-055);
- the `UNPROCESSABLE` anomaly kind reached through a denied directory ([ADR-053](0053-the-walk-anomaly-vocabulary-is-three-kinds.md));
- anything where the filesystem is a source of tree shapes rather than a subject of study.

*Real NTFS under `@TempDir`, because the decision rests on measured platform behaviour:*

- path identity, case folding and byte-exact comparison (ADR-051);
- filenames with no UTF-8 encoding (ADR-051, ADR-053);
- soft links, junctions and volume mount points, including a junction reading as `isOther()` rather than `isSymbolicLink()` (ADR-051, ADR-053);
- traversal-order stability, which is what makes resuming sound at all (ADR-055).

**A test in the first group may not be used as evidence for a decision in the second.** The library's Windows model is a model; where a recorded decision rests on how NTFS actually behaves, only NTFS is evidence for it. Its case-sensitivity and collation settings are not to be configured to imitate NTFS.

**The over-long path is recorded as unmanufacturable, and ADR-063's helper for it is withdrawn.** `UNPROCESSABLE` keeps path-too-long among its causes — real archives copied between systems do carry such paths — and the walk's behaviour is identical whichever `IOException` produced it, so the kind is covered by the denied-directory case. The specific cause is met, never manufactured.

**Windows remains the only target.** This decision is about what a fixture is made of, not about portability, and it is not a cross-platform plan. It does, however, leave the walk's algorithmic tests platform-independent as a side effect, which is a fact about them rather than a commitment.

## Consequences

**A test-scope dependency is added**, which under [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) is what this ADR exists to authorise.

**`AwkwardFixtures` mostly disappears.** Around sixty lines of ACL manipulation with three abort paths collapse into a `chmod 000`, and the permission-denied test stops being a skip in containerised and administrator environments.

**Two conversions in `pipeline` become filesystem-aware.** `CensusTasklet` resolves the seed folder with `Path.of(...)` and `WorkingDirectoryPreparer` does the same for the working directory, both binding to the default filesystem. The seed folder should be resolved against the corpus root's filesystem instead — a correctness improvement independent of testing, since a corpus and its seed set belong to one filesystem.

**Fixtures get cheap enough to be honest about scale.** ADR-063 argued no large-N fixture was warranted because the arithmetic is identical at any N. That holds for the counting identity, and does *not* hold for the ordinal arithmetic, where depth and width are the variables. At 10ms per thousand entries, the test that would have been too slow to justify is no longer.

**A skip is still not a pass.** The NTFS tier keeps ADR-063's `assumeTrue` pattern and `AGENTS.md`'s reporting rule, and it is where the environment-dependent cases now live exclusively.

**The trap is that the in-memory tier looks like it could cover everything.** It runs faster, skips nothing, and its Windows model is convincing enough to answer questions it should not be asked. The measured dotless-i disagreement is recorded above so that the boundary rests on evidence rather than on caution.

## Amends

**ADR-063**, in two respects. It admits a second fixture mechanism alongside `@TempDir`, scoped as above; and it withdraws the over-long-path helper it owed the suite, on the finding that no such fixture can exist. Its ruling on committed scale tests is narrowed rather than reversed: a scale question is still answered by a throwaway probe, but the resume ordinals are now cheap enough to test directly rather than to characterise.

It amends neither ADR-051 nor ADR-053, and reaffirms where both are tested.
