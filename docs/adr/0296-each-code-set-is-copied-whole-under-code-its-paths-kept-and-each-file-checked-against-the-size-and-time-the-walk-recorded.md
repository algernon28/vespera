# ADR-296 — Each code set is copied whole under `code/`, its paths kept, and each file is checked against the size and time the walk recorded

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Decided**: under the operator's delegation, on the questions of [#573](https://github.com/algernon28/vespera/issues/573), with the layout the operator settled at charting map [#551](https://github.com/algernon28/vespera/issues/551): each set is copied to `code/<set>/` under the tree's root, its paths below the set's folder kept.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md), in these places and no others: §1's tree, which gains `code/` and `code.csv` (§1, §5); §1.4's index, which gains a section after the groups (§4); §2.2's check, which for a code file is the walk's size and time and not a digest against stage 2's key (§2); §2.3's `changed`, which for a code file is a size or a time that differs from the walk's (§2); §2.5's survey, which code has a pair of lines of its own (§7); §7's closing line, which gains two clauses (§6); §8's lines, which gain the code's survey and a counter (§7); and its refused alternative *"Copies under their root-relative paths"* and Decided here 3, which are reopened for code alone (§3). The five outcomes, the gibibyte of §2.5 (on the tree's drive, ADR-276 §4), §2.4's trust in a copy at its own name, §2.6's archive opened for reading alone, and "no row of the database records a copy" stand for code as they stand for documents.
- **Amends**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md), in these places and no others: §1.1, which keeps only the last element of a path, and its refused alternative *"Copies under their root-relative paths"*, neither of which holds of code (§3); §1.2 to §1.6 and §3, which are applied to a set's folder name and to each element of a code file's kept path (§3); §2, whose names worked out from recorded rows before the first copy hold of a set as of a cluster (§3.4); §3's count of copies past 259 characters, which counts code copies too (§3.3); and §4's listing of what an earlier drawing left, which looks inside `code/` (§6.3). Its keys, its suffix and its cut are unchanged.
- **Amends**: [ADR-284](0284-code-is-kept-in-code-sets-each-below-the-nearest-folder-under-the-corpus-root-holding-a-build-file.md) §8, whose first rule, two names one where equal after NFC and upper case, is replaced: names are told apart after they are made safe and cut, by ADR-280 §1.4's two keys (§3.1); its other rules stand, and what it owes, that no drawing puts one set's copies into another's folder, is paid here. ADR-284 §9, whose dormant entries the closing line counts in the words of §6.2, and whose file refused whole is drawn with the default names and told as a warning (§6.2). ADR-284 §6's *For later stages*, which gains a reader of the sets and members for the drawing (§8).
- **Amends**: [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §9, whose list of what is still held gains the drawn name of each code set of the run for the drawing, and one set's members and kept paths while that set is copied (§3.4).
- **Keeps**: [ADR-277](0277-code-is-recognised-by-a-name-ending-the-profile-lists-or-by-a-documentation-generators-mark-and-stage-1-takes-it-out-of-the-survivors.md), code is never hashed; [ADR-287](0287-a-log-and-a-configuration-file-are-told-by-their-bytes-whatever-their-detected-format-and-a-configuration-file-is-kept-with-its-code-set-or-is-out-of-scope.md) §4, the role `configuration`; [ADR-286](0286-a-clusters-page-is-one-file-written-an-entry-at-a-time-through-its-part-with-its-pictures-and-a-png-or-jpeg-image-inlined.md), a cluster's page and its folder; [ADR-283](0283-the-private-paths-guard-reads-a-deliverable-tree-as-outside-the-allow-list-wherever-it-lies-by-its-marker.md), the guard, which reads `code/` as inside the tree the marker closes; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), a reader's words say *group* for a cluster, and never for a code set; ADR-274 §5, the tree keys no run.
- **Rests on**: a reading of `corpus.CodeSets`, `corpus.Walk`'s record of a file occurrence's size and last-modified time, `ledger.Occurrences`, which stores that time as the text of its `Instant`, `deliverable.Copies`, `CopyNames`, `CodeSetNames`, `IndexPage`, `ManifestCsv` and `Deliverable.writeTo`, `pipeline.NextAction`'s closing line and `GenerationTasklet`'s progress, and of `StageModules`. No archive, working directory or tree was opened for this record (ADR-196).

## Context

**Code has no recorded key.** Stage 1 verdicts code in its first pass and its set pass, before the hashing, and what it verdicts is never hashed (ADR-277, ADR-284 §6); it is never converted, so stage 2's extraction-cache key does not exist for it either. ADR-274 §2.2 checks a copy against `ListedSurvivor.contentHash`, which a code file does not have.

**What is recorded of a code file.** The walk records each occurrence's `size_bytes` and `last_modified` in `file_occurrence`, the time as `BasicFileAttributes.lastModifiedTime().toInstant()` written as its `Instant`'s text. `code_set` holds each set's folder and default name under the stage-1 run, and `code_set_member` each member's occurrence, folder, role (`code`, `companion`, `build`, `configuration`) and generator.

**What nothing rests on.** No conversion, score or writing is made of a code file. Nothing the tree says of one depends on its content.

**What a set is for.** A set is read in its own layout: a package names its folders, an import or a build file names relative paths, and a file taken out of its folder means little.

**What the deliverable is today.** `documents.csv` has eleven columns, `cluster` and `relevance_score` among them, which a code file has none of. The index's reader prose says *group* for a cluster. ADR-280 §3 already dropped the fixed bound on a copy's path: names are cut, not paths.

## Decision

### 1. The tree holds `code/` and `code.csv`

```
<deliverable root>/
  index.html                          the arrangement, then the code sets (§4)
  documents.csv                       one row per survivor, unchanged
  code.csv                            one row per member of every code set (§5)
  vespera-deliverable.txt             the marker
  <partition>/<cluster>/...           as ADR-280 and ADR-286 have it
  code/<set>/<kept path>              a checked copy of each member of each code set (§2, §3)
```

**Every member of every code set of the stage-1 run upstream of the tree's generation run is copied**, whatever its role. **Nothing is written over a code set**: no page, no writing, no picture. **A run with no code set has no `code/`, no `code.csv` and no section of the index.**

### 2. What checks a code file's copy

**A code file's key is what the walk recorded of its member occurrence**: `file_occurrence.size_bytes` and `file_occurrence.last_modified` of the walk the stage-1 run read.

- **Before the original is opened**, its size is read by `Files.size` and its last-modified time by the file system's last-modified time as an `Instant`, written as the census writes it. Both must equal the recorded values; otherwise nothing is opened, nothing is written, and the outcome is `changed`.
- **The bytes are streamed into `<copy>.part`** beside the copy, opened for reading alone, and counted.
- **When the stream ends, the size and the time are read again.** Where both still equal the recorded values and the bytes counted equal the size, the part is moved over the copy's name by `ReplacingMove` (ADR-242). Otherwise the part is deleted, nothing stands at the copy's name, and the outcome is `changed`.
- **No digest is computed or written.** `code.csv` carries no `content_hash`.
- **The other outcomes are ADR-274 §2.3's, unchanged**: `gone` for a file or a folder above it no longer there, `no-room` by §2.5's gibibyte on the tree's drive, `not-copied` for any other failure, a name the path parser refuses or one past 255 bytes with `.part` (ADR-280 §1.6) among them. None fails the step and none throws. A file at a copy's name is taken as copied, and neither it nor the original is read (ADR-274 §2.4).

### 3. Names and paths

#### 3.1 A set's folder in `code/`

- **It starts from the set's typed name**, the `name` of the `code-set-names.yaml` entry whose `folder` is the set's (ADR-284 §9), **or its default name** where there is none.
- **It is made safe by ADR-280 §1.2 and cut by its §3**, to 100 UTF-16 code units and 255 UTF-8 bytes. A folder is written through no `.part` and opens by no extension, so the whole name is its stem and no `.part` is counted. A name left empty after either is the set's lowest member occurrence id, in decimal; the device rule holds after the cut and after any suffix, as ADR-280 §1.3 has it.
- **Then names are told apart, by ADR-280 §1.4's two keys**, on the name made safe and cut: two collide where a key of one equals a key of the other. A typed name outranks a default name; among names of the same kind, the set whose lowest member occurrence id is lower comes first. The first keeps its name, and each other takes `~` and its own lowest member occurrence id at the end of the name, again until free, as ADR-280 §1.6 repeats it: of `core` (lowest member 5), `core~7` (6) and `CORE` (7), the third is `CORE~7~7`.
- **Each set is a folder directly in `code/`**, whatever its folder's depth or nesting: a set at `Work` and one at `Work/shop` are `code/Work/` and `code/shop/`, side by side. So no drawing puts one set's copies in another set's folder.
- **`code` meets no partition folder**: a partition's folder begins with its order's digits and a `-` (ADR-112).

#### 3.2 A member's kept path

**Below its set's folder, each member keeps its path relative to the set's folder in the archive**, the folder as `code_set` holds it, the whole path for the root's set (`''`), element by element.

- **Each element is made safe by ADR-280 §1.2 and cut by its §3**: a file's name as a copy's name is, with `.part`; a folder's as §3.1 cuts a set's. A folder element left empty is the lowest occurrence id of the members beneath it; a file name left empty is its own occurrence id.
- **Folder elements that collide by ADR-280 §1.4's keys within one drawn folder are one folder**, spelt as the member with the lowest occurrence id beneath it spells it: `Src/B.java` of 21 and `src/A.java` of 20 are `src/A.java` and `src/B.java`.
- **File names in one drawn folder collide, and are suffixed, as copies in a cluster folder are** (ADR-280 §1.4 to §1.6), the members taken in ascending occurrence id, `<another file's name>.part` reserved as in §1.5. A drawn folder of a set reserves no page's name: no page is written there.
- **A file and a folder in one drawn folder that collide: the file takes the suffix**, as a file name colliding with a reserved name does, and the folder keeps its name: a file `lib` of 40 beside `LIB/x.c` is `lib~40`.

#### 3.3 Path length

**Names are cut, not paths** (ADR-280 §3). A kept path has no bound, the archive's own depth being the set's layout. ADR-280 §3's count of copies standing at an absolute path past 259 characters counts code copies with the documents', in the one line it already writes.

#### 3.4 Resume, and what is held

**Every name is worked out from recorded rows alone, before the first copy** it bears on: the sets' folder names from the sets, their lowest member occurrence ids and the names file, before the first code copy; a set's kept paths from its members, before that set's first copy. So ADR-274 §2.4 holds unchanged, and a name worked out again from the same rows is the same name.

**What is held is the drawn name of each set of the run for the drawing**, one string for each set, since every set's name must be known before any is given; **and one set's members and kept paths while that set is copied, let go after it**, bounded by the largest set, for ADR-220's reason: its index row and its rows of `code.csv` are written from them once its copies are made, and nothing of a set is held after it. A set's rows of `code.csv` and its row of the index are written as each set ends.

### 4. The index

**After the last group's table, the index gains a section headed `Code`**, which never calls a set a group:

- one sentence: *"Each code set is copied whole under code/, keeping its folders, and each of its files is checked against the size and modification time recorded when the archive was walked."*;
- a table with the head `Code set`, `Folder in the archive`, `Files copied`, `Files not copied`, and **one row for each set, in the order of `code_set`'s folders**, as its primary key reads them: the set's drawn folder name, linking `code/<name>/` with each UTF-8 byte of the name outside RFC 3986's unreserved set percent-encoded (ADR-280 §5); its folder in the archive as text, *"the archive's top"* for the root's set; how many of its copies stand, made or kept; and how many do not. Every value is escaped by ADR-274 §1.3's rule.

### 5. `code.csv`

**A manifest of its own, at the tree's root**, so `documents.csv` keeps its eleven columns and lists no code file:

`occurrence_id,path,code_set,role,generator,size_bytes,last_modified,copy,copy_outcome`

- **One row for each member**, the sets in §4's order and each set's members in ascending occurrence id.
- `path` is the member's root-relative path; `code_set` the set's folder as `code_set` holds it, empty for the root's set; `role` and `generator` as `code_set_member` holds them, `generator` empty where null; `size_bytes` and `last_modified` the walk's, the time as the census wrote it; `copy` the copy's path from the tree's root, `code/<name>/<kept path>`, or empty where no copy stands; `copy_outcome` one of the five words.
- **Quoted as `documents.csv` is**: every text column by RFC 4180, behind ADR-235's single quote where it would begin as a formula.
- It is written through `code.csv.part` and moved into place by `ReplacingMove` after the index, as `documents.csv` is.

### 6. What the operator is told

#### 6.1 The files of code not copied

**Where the tree has at least one code set, the closing line adds, after ADR-274 §7's *"M documents not copied"*, *", and K files of code not copied"***, `file` in the singular for 1 and K stated when it is 0: K is every member's outcome but `copied` in the tree this invocation wrote. A tree with no code set has no such clause, and an invocation that drew no tree has none.

#### 6.2 The names file

- **Where D > 0 entries of `code-set-names.yaml` are dormant** (ADR-284 §9), the line then adds *"; D names in code-set-names.yaml name no code set of this run"*, *"1 name … names"* for 1, whether or not the run has a code set. The clause stands before ADR-280 §4's.
- **A file refused whole** (ADR-284 §9), or one that cannot be read, **fails nothing**: the tree is drawn with every set's default name, no entry is counted as dormant, and the generation step writes one warning line, told by a call of `DeliverableProgress` that takes no argument, naming the file and no entry.

#### 6.3 What an earlier drawing left

**`code/` is a folder this drawing wrote**, where it drew a set. **A folder directly in `code/` that this drawing did not write counts as left behind** in ADR-280 §4's N; nothing deeper in `code/` is looked at, and a file is not counted. A `code/` at the top of a tree whose run has no set is a folder this drawing did not write, and counts as one.

### 7. Lines and progress

What `pipeline` writes of the code copies is numbers and enumerations (ADR-274 §8):

- **before the first code copy**, one line: the sets, the members about to be copied, the bytes the walk recorded for them, and the bytes the tree's drive has free. The bytes are the walk's, so the survey opens no original;
- **a counter**, `Stage 6b (generation, code copied)`, over the members, moved on by each member's outcome, a copy standing included;
- **after the last code copy**, one line: the copies standing, by outcome, and the bytes copied.

No line names a set, a folder, a path or a file.

### 8. Where it is written, and the run ids

- **`corpus`**: a public reader on `CodeSets` of a stage-1 run's sets, in folder order, each with its default name and lowest member occurrence id, and of one set's members a page at a time (ADR-220), each with its occurrence id, root-relative path, role, generator, and the walk's size and last-modified time. `code_set_member` is `corpus`'s and `file_occurrence` the ledger's, and the join is the reader's to make within ADR-209's rule.
- **`pipeline`**: reads them through that reader and hands `deliverable` values of `deliverable`'s own, which may not name `corpus`: `ListedCodeSet` and `ListedCodeFile`, through `ListedCodeSets`; writes §7's lines and §6's clauses.
- **`deliverable`**: the copies, the names and paths, the index's section, `code.csv`, the names file's application, and `DeliverableProgress`'s calls.

**Stage 1 moves**: its version names `corpus`, which this change edits. **Every later stage follows**, each naming the run upstream of it (ADR-089), so the arrangement is minted again and must be approved again, and every cluster is written again under a new generation run with a tree of its own. **A change to `deliverable` alone moves none** (ADR-274 §5).

### 9. What the documents say

- **`CONTEXT.md`, in this change**: **Copy** covers a code set member's file put under `code/<set>/` at its kept path, checked against the walk's size and last-modified time; **Copy outcome**'s `changed` covers a size or a time that differs from the walk's; **Verdict**, **Code**, **Code set**, **Out of scope**, **Arrangement** and **Publication-ready artifact** stop saying the deliverable's code is undecided.
- **The implementer's, in the change that builds this**: README's *Where things live*, which gains `code.csv` and `code/<set>/<path>`, and *Where the run ends*, which gains the code's copies, its check and its two clauses (#577 owns the rest of the README's and the architecture's text for code sets). `docs/architecture.md` §1's 6b names `code/` in this change.

## Decided here

1. **The walk's size and last-modified time check a code file's copy** (§2), where stage 1 could have hashed it: nothing in the tree rests on a code file's content, so the walk's observation is the recorded thing, and hashing in stage 1 would contradict ADR-277 and move stage 1 for no gain. The check is made before the stream and after it.
2. **Every member of every set is copied, every role included, and nothing is written over a set** (§1): no model writes about code.
3. **A manifest of its own, `code.csv`** (§5), since `documents.csv`'s `cluster`, `relevance_score` and `content_hash` mean nothing for code.
4. **An index section headed in plain words, never *group*** (§4), one row a set.
5. **Paths kept below the set's folder** (§3.2), reopening ADR-274's refused alternative for code alone: a code set is usable only in its own layout.
6. **Names told apart once made safe and cut, by ADR-280 §1.4's keys** (§3.1), amending ADR-284 §8: the safe name is what lands on disk, so collisions are judged on it, by the same keys the copies use.
7. **Sets side by side in `code/`, never nested** (§3.1), so no set's copies land in another's folder.
8. **A file yields to a folder beside it** (§3.2), as a copy yields to a reserved name.
9. **One term**: a code file's copy is a **Copy** (§9).
10. **The closing line counts the files of code not copied and the dormant names** (§6), the first even at 0 where a set exists.
11. **A names file refused whole is a warning, not a failure** (§6.2).

## Consequences

- **A reader of the tree finds each code set in its own layout** under `code/`, every file at its kept path, builds and imports resolving as they did in the archive.
- **A code file touched since the walk is not copied**, even where its bytes are unchanged; `code.csv` says `changed`.
- **A code file whose bytes changed and whose size and time were set back to the walk's is copied** as it now is: the check is the walk's observation, not the content.
- **A deep set can put copies past 260 characters**, counted by ADR-280 §3's line.
- **The disk the tree lies on holds the code sets again**, under the gibibyte's rule.
- **This change moves stage 1 and every stage after it** (§8).
- `CONTEXT.md`'s **Copy** and **Copy outcome** cover code (§9).

## Alternatives refused

- **Hashing code in stage 1.** ADR-277 keeps code out of the hashing, and a digest of a file nothing rests on would move stage 1 for no gain (Decided here 1).
- **Flat names for code copies**, as ADR-274 §2.1 had them, or the original's own name alone, as ADR-280 §1.1 has it. A set read out of its layout means little (Decided here 5).
- **Rows for code in `documents.csv`.** Three of its columns mean nothing for a code file, and a program loading it would read empty cells as values (Decided here 3).
- **A page for each set.** Nothing is written about code, and a page listing the files would repeat `code.csv`.
- **A term of its own for a code file's copy.** It is the archive's file put in the tree and checked, as a Copy is; only the key differs (Decided here 9).
- **Telling names apart before they are made safe**, as ADR-284 §8 had it. Two names that differ only in a character made `_` would land on one folder.
- **Nesting a set's folder in its outer set's**, as the archive nests them. A set's copies would land inside another set's folder.

## What this does not decide

- **A program opening a code copy past 260 characters** (§3.3).
- **A code file changed in place with its size and time set back** (Consequences): no check short of a digest sees it.
- **The control panel's screen for a rename** (#566), and **the README's and the architecture's text for code sets** beyond §9's (#577).

## Tests

| Class | What it holds |
| --- | --- |
| `deliverable.CodeSetFolderNamesTest.aTypedNameOutranksADefaultNameAndAmongAlikeTheLowestMemberKeepsIt` | §3.1: a typed `Core` keeps its name against a default `core`, which takes `~` and its lowest member; of two alike defaults the lower lowest member keeps it; the same names whatever order the sets arrive in |
| `…namesAreToldApartOnceTheyAreMadeSafe` | §3.1: `a:b` and `a_b` collide, made safe; `Shop.Core` and `shop.core` collide, the suffix at the end |
| `…aNameIsMadeSafeAndCutAndOneLeftEmptyIsTheLowestMemberOccurrence` | §3.1: `...` is the lowest member's id, `CON` is `_CON`, a name of 120 units is cut to 100 |
| `…aSuffixedNameThatStillCollidesTakesTheSuffixAgain` | §3.1: `core`, `core~7` and `CORE` are `core`, `core~7` and `CORE~7~7` |
| `…anEntryNamingNoSetOfTheRunIsDormantAndCounted` | §6.2: two dormant entries of three counted, and neither changes a name |
| `deliverable.CodePathsTest.aMembersPathBelowItsSetsFolderIsKeptElementByElement` | §3.2: a deep path kept below its set's folder; the root's set keeps the whole path |
| `…eachElementIsMadeSafe` | §3.2: a refused character as `_`, a device folder with `_`, trailing dots gone, a folder left empty as its lowest member's id |
| `…foldersThatCollideAreOneFolderSpeltAsTheLowestMemberBeneathItSpellsIt` | §3.2: `src/A.java` of 20 and `Src/B.java` of 21 in one `src/` |
| `…fileNamesThatCollideInOneFolderAreSuffixedAsCopiesAre` | §3.2: `Readme.txt` and `README.TXT`, and `notes.txt.part` yielding to `notes.txt` |
| `…aFileThatCollidesWithAFolderBesideItTakesTheSuffix` | §3.2: a file `lib` of 40 beside `LIB/x.c` is `lib~40` |
| `deliverable.CodeSetsInTheTreeTest.eachSetIsCopiedWholeUnderCodeItsPathsKeptAndSetsSideBySide` | §1, §3: every member byte for byte at `code/<set>/<kept path>`, every role; `Work` and `Work/shop` side by side |
| `…aFileWhoseSizeOrTimeDiffersFromTheWalksIsChangedAndNotCopied` | §2: one of each `changed`, no copy and no `.part`; a third copied |
| `…aCodeCopyThatStandsIsKeptWithoutReadingTheArchive` | §2: with the original deleted, a redrawing keeps the copy and says `copied` |
| `…aCodeFileGoneIsGoneAndADriveWithNoFreeSpaceReadTakesNoCopyOfCode` | §2: `gone`; with 0 bytes free, `no-room` and nothing under `code/` |
| `…theCodeListingHasARowForEachMemberAndDocumentsCsvNone` | §5: `code.csv`'s header and rows exactly, quoted; `documents.csv` lists no member |
| `…theIndexListsEachSetAfterTheGroupsAndNeverCallsItAGroup` | §4: the heading, the sentence, the head and one row a set, linked percent-encoded, after the last group's table |
| `…aTreeWithNoCodeSetHasNoCodeFolderNoCodeListingAndNoCodeSection` | §1 |
| `…aFolderInCodeThisDrawingDidNotWriteIsCountedAsLeftBehind` | §6.3: one folder in `code/` counted, `code/` itself not |
| `…theTypedNamesAreAppliedAndTheDormantOnesToldAsACount` | §3.1, §6.2 |
| `…aNamesFileThatIsRefusedLeavesTheDefaultNamesAndIsToldOnce` | §6.2 |
| `…theCodeCopiesAreSurveyedBeforeTheFirstInNumbersOnly` | §7: told once with sets, members, the walk's bytes and the free bytes; one outcome told for each member |
| `pipeline.TheClosingLineCountsTheFilesOfCodeNotCopiedTest` | §6.1, §6.2: `0 files`, `1 file` and `2 files of code not copied`; no clause with no set or no tree; `1 name … names` and `2 names … name`, before the clause of what an earlier drawing left |
| `deliverable.WhatTheTreeReportsNamesNoDocumentTest` | §7: every new method of `DeliverableProgress` takes only primitives and enumerations |

**In the change that builds this**: `corpus`'s reader of §8, held against a ledger holding two runs' sets, in folder order, each set's members a page at a time in ascending occurrence id with the walk's size and time; `pipeline`'s §7 lines held as `TheTreeInHtmlInvocationTest.theCopiesAreToldBeforeAndAfterInCountsOnly` holds the documents', every argument a number; and an invocation whose run has a code set ending its closing line with §6.1's clause.

**Not pinned, and why**: a size or a time that changes while the bytes are streamed (§2), which no test can bring about between two reads without a seam the shipped code does not need; a code copy past 259 characters (§3.3), the count being the one ADR-280's test holds; and the time read as the census writes it on a file system coarser than the one the suite runs on.
