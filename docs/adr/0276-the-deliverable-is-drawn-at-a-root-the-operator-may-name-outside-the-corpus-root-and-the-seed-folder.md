# ADR-276 — The deliverable is drawn at a root the operator may name, outside the corpus root and the seed folder

- **Date**: 2026-10-18
- **Status**: accepted on 2026-10-18.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md), in these places and no others: §1's tree, drawn at `deliverable/<generation run>/`, which is drawn at the deliverable root and gains one file, the marker (§1, §3); §2.5's "the working directory's drive" and the reason it gives, the gibibyte being read on the drive the tree is written to (§4); §2.7's bound of 69 characters on the working directory's path, which for a named root is a bound of 146 on the root's own (§1); §6's "redraws the tree it already had, in place, under the same name", which holds of a named root only where it holds this working directory's tree of the same generation run (§3); and §7's `<absolute path>/index.html`, which is the root's index (§5). Its Decided here 7, a gibibyte left free, stands, read on that drive.
- **Amends**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), in "It lands in the working directory" and its "`--db-dir=` remains the only thing that moves it", and in the README invariant its Context weighs, *everything the tool writes goes in one working directory*: a named root takes the tree out of the working directory (§1). "There is no new profile key", one tree per generation run, and "Nothing is ever cleaned up", which from this record holds beneath a deliverable root too (§3, §6), stand.
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md), two commands and no new option; [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md), nothing is written into the archive and the working directory is named by configuration; [ADR-066](0066-the-command-line-names-the-root-configuration-is-the-fallback.md), whose `vespera.corpus-root` is the precedent for a `vespera.*` property naming a path, and whose refusal is made before the job; [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) and [ADR-102](0102-publication-is-the-state-the-deliverable-is-handed-over-in-never-a-verb-this-project-uses.md), nothing carries the tree anywhere; ADR-274 §2.4, a copy trusted at its own name and nowhere else, §5, the tree keys no run, and §8, no agent reads a copy; [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) §2, a place is open or refused to an agent as the place it lies in.
- **Read with**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md), one invocation per working directory, whose lock does not reach a named root: the marker keeps a second working directory out of it (§3). [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), whose `folder=deliverable` counts the working directory's `deliverable/` and so no tree drawn at a named root.
- **Settles** [#557](https://github.com/algernon28/vespera/issues/557).
- **Rests on**: a reading of `Deliverable.writeTo` and `usableBytesAt`, of `NextAction`'s tree and closing line, of `GenerationTasklet`'s drawing of the tree and its opening of the index, of `VesperaCommand.Run` and its `vespera.corpus-root`, of `WorkingDirectoryOption`, and of `Walk.canonicalRoot`. No archive, working directory or tree was opened for this record (ADR-196).

## Context

On 2026-10-18 the operator settled where the deliverable is written, for the control panel that map [#551](https://github.com/algernon28/vespera/issues/551) charts: the panel lets them name an output folder, the tree is drawn beneath it with its index at the top, copies are made and nothing is moved, and nothing is written into the archive. With no folder named, the tree goes where it goes today. Earlier, of another change: *"i wouldn't add new flags for now, it makes things needlessly complicated. Just use defaults"* (ADR-274).

**Where the tree is written today.** `Deliverable.writeTo` composes `<working directory>/deliverable/<generation run>` from the working directory it is handed, and `NextAction` composes the same path again for the closing line. Every invocation that reaches the tree draws it in place (ADR-274 §6), and the generation step opens its `index.html` (§7). One tree per generation run, and nothing deletes in the working directory (ADR-103).

**What nothing checks.** The walk excludes nothing beneath the corpus root, and nothing compares the working directory, or the tree, with the corpus root or the seed folder. A tree drawn beneath the corpus root would be walked at the next invocation, each copy a new file occurrence of the archive: a copy of every survivor, scored and arranged beside the survivor it copies.

**What a copy is trusted by.** A copy that stands at its own name is taken as copied and neither it nor the original is read (ADR-274 §2.4). A tree drawn somewhere else, or in another layout, copies every survivor again, and the copies already made stay where they are.

**The gibibyte.** Before each copy the writer reads the usable space of the working directory's file store (ADR-274 §2.5), for the reason that the database is there.

## Decision

### 1. The deliverable root, and how it reaches the jar

**The deliverable root is named by the Spring property `vespera.deliverable-root`**, which the environment sets as `VESPERA_DELIVERABLE_ROOT` and which may be set wherever a property is: `application-local.yaml`, the environment, or `--vespera.deliverable-root=<path>`. `application.yaml` ships the key empty, as it ships `corpus-root:`. The control panel sets it for each invocation through the environment. Its name is written once, as `VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY`, beside ADR-066's `ROOT_PROPERTY`.

- **Unset or blank**, the tree is drawn at `<working directory>/deliverable/<generation run>/`, as today.
- **Named**, the root is the tree itself: `<root>/index.html`, `<root>/documents.csv`, `<root>/documents/`, a folder a partition and the marker (§3), and nothing of it under `<working directory>/deliverable/`. A root that does not exist is made when the tree is drawn.

**One class answers where the tree is, `io.algernon.vespera.deliverable.DeliverableRoot`**: `DeliverableRoot.UNNAMED`, `DeliverableRoot.named(Path)`, `DeliverableRoot.of(String)`, which is `UNNAMED` for `null` or a blank value and otherwise the value stripped and named, and `Path treeOf(Path workingDirectory, String generationRun)`, which is the named root, absolute and normalised, or `<working directory>/deliverable/<generation run>`. The generation step and `NextAction` both ask it, so the path is composed once. Every form of `Deliverable.writeTo` takes the root after the working directory, `writeTo(Path workingDirectory, DeliverableRoot root, DeliverableProvenance provenance, …)`, and returns `root.treeOf(workingDirectory, provenance.runId())`.

**No option and no profile key.** A command-line option is a third value on a two-command surface (ADR-047) the operator asked not to grow. A profile key would be the corpus naming where a machine writes, which ADR-066 refused for the corpus root: the profile records judgements about a corpus, and a root is a place on one machine.

**The root keys no run.** `DeliverableRoot` and the writer lie in `deliverable`, the checks' callers in `pipeline`, and no stage's version names either (ADR-274 §5, ADR-222); no run's `config_consumed` reads the root. So the same generation run draws its tree at whatever root is named, and one tree per generation run stands: a named root holds one.

**A named root's own path is at most 146 characters** for every file of the tree to stay within Windows' 260. ADR-274 §2.7 puts the longest file at 190 characters below the working directory, 77 of them `deliverable/`, the run id and its separator; the remaining 113 lie below the tree, and 146, a separator and 113 make 260. Past that the JDK still writes, as ADR-274 §2.7 has it.

### 2. What `vespera run` refuses before the job

**Every `vespera run`, once it has its corpus root and before the job starts, asks `DeliverableRoot.refusalAtTheStart(Path workingDirectory, Path corpusRoot, Optional<Path> seedFolder)`**, the seed folder being the profile's `seedFolder` where it is set. Where it answers a line, the line is written on standard error, the invocation exits 1 (`CommandLine.ExitCode.SOFTWARE`, as ADR-177's refusal does), and nothing is walked: no job, no progress page, no account. `vespera label` draws no tree, and asks nothing.

**A root equal to, inside, or above the corpus root or the seed folder is refused.** The root compared is the named root, or, where none is named, `<working directory>/deliverable`, the folder every unnamed tree lies in. The corpus root is compared first, then the seed folder; for each, equal, then inside, then above. The line is

`vespera run refused the deliverable root <root>: it is <relation> <folder>. Name a folder outside the corpus root and the seed folder in vespera.deliverable-root.`

where `<relation>` is `the corpus root`, `inside the corpus root`, `above the corpus root`, `the seed folder`, `inside the seed folder` or `above the seed folder`, and `<root>` and `<folder>` are the two paths as they were compared.

**Paths are compared by their real paths**, as `Walk.canonicalRoot` reads a corpus root (ADR-051): a path that exists is its `toRealPath()`, so a link, a junction, a `..` or a mapped drive is followed to where it leads; a path that does not exist yet is the real path of the nearest folder above it that does, with the names below it appended as written. They are compared name by name, so `archive-copies` is not inside `archive`. **On Windows the names are compared without regard to case**, as NTFS opens them; elsewhere as written.

**A root inside the working directory is allowed**: nothing walks the working directory. Nor is anywhere else in the archive refused: Vespera cannot see how far the archive extends above the corpus root and the seed folder, and keeping the root out of the rest of it is the operator's. **The control panel's own check of the root as it is typed is the panel's to record**, not this record's.

**Why at the start, and not only when the tree would be drawn.** Whether an invocation will draw the tree is learned inside the job, after the walk and every stage before 6b, which on a large archive is hours of work. And a tree beneath the corpus root does its harm at the walk: by the time the drawing could refuse it, the copies an earlier invocation drew there have been walked in as new file occurrences. The corpus root and the seed folder are both known at the start, and the check reads no document: it follows paths and lists one folder (§3).

### 3. A named root holds its own tree and nothing else

**Every tree carries a marker, `vespera-deliverable.txt`, at its top**, named or not: `DeliverableRoot.MARKER_FILE_NAME`. It is UTF-8, two lines, each ending in `\n`:

```
working-directory=<the working directory's real path, or its absolute normalised path where it has none>
generation-run=<the generation run's id>
```

It is written after the partitions are asked for, so a partition `ListedPartition` refuses still leaves nothing written, as `Deliverable.writeTo` has it, and **before the first copy**, so a drawing stopped partway leaves a root the next invocation of the same run accepts. It is written over at each drawing. `DeliverableRoot` writes it and reads it, one class for the rule (ADR-213). A marker that cannot be read, or lacks either line, is read as no marker.

**At the start** (§2), after the comparisons, a named root is accepted where it does not exist, where it is an empty folder, or where it is a folder whose marker names this working directory, compared as §2 compares, whatever generation run it names. Anything else is refused with one line and exit 1:

- a root that is not a folder: `vespera run refused the deliverable root <root>: it is not a folder. Name an empty folder in vespera.deliverable-root.`
- a folder that is not empty and holds no marker, an unreadable one, or one naming another working directory: `vespera run refused the deliverable root <root>: it is not empty and holds no tree of this working directory. Name an empty folder in vespera.deliverable-root.`

**At the drawing**, the generation step asks `DeliverableRoot.refusalAtTheDrawing(Path workingDirectory, String generationRun)` before it draws, the generation run being known only then. A named root is accepted where it does not exist, is empty, or holds the marker of this working directory and this generation run, and **is then redrawn in place** (ADR-274 §6). Otherwise the answer is one of

- `the deliverable root <root> holds the tree of another generation run, so no tree was drawn. Name an empty folder in vespera.deliverable-root, and run again.`
- `the deliverable root <root> is not empty and holds no tree of this working directory, so no tree was drawn. Name an empty folder in vespera.deliverable-root, and run again.`

and the step writes it once at error level, draws nothing, opens no index and hands the closing line no count; it ends failed and **throws nothing**, as ADR-202's refusal does, so every row it recorded stands (ADR-111) and its completion is recorded where its work is done. The job fails and the invocation exits 1 with no closing line. The writing is kept: the next invocation, with an empty root named, draws it with no question put (ADR-274 §6). **A new generation run needs a new, empty root.**

**The unnamed root is never refused at the drawing.** Its folder is named for the generation run, so it holds that run's tree or nothing; a tree drawn there before this record carries no marker, is redrawn in place, and gains one.

**Nothing is deleted beneath a deliverable root**, named or not, as nothing is in the working directory (ADR-103): a redrawing writes over the files of its own tree, at their own names, and leaves every other file where it is.

### 4. The gibibyte is read on the drive the tree is written to

The writer reads the usable space of **the tree's file store**, `usableBytesAt(tree)`, before each copy, where it read the working directory's. The copies fill the drive they are written to, and the pages, the index and the manifest written after them are written there too: a full drive there would fail the drawing after its copies were made. Where the root shares the working directory's drive, which the unnamed root always does, this is ADR-274 §2.5's rule exactly, the database's room kept with the tree's. What cannot be read still counts as none.

### 5. The closing line names the root's index, and that index is opened

`NextAction` asks `DeliverableRoot.treeOf` for the tree of the generation run the invocation arrived at, so the closing line names `<root>/index.html`, absolute, in ADR-274 §7's words, and the generation step hands the same index to the `PageOpener`.

### 6. A change of layout leaves the earlier layout's folders, and the closing line says so

**This is the rule a record that changes the tree's layout follows.** Nothing beneath a deliverable root is deleted (§3), and a copy is trusted only at its own name (ADR-274 §2.4), so a tree redrawn in a new layout copies every survivor again under the new names and leaves the earlier layout's folders where they are. The record that changes the layout names those folders, and **the closing line of an invocation that draws over a tree holding one names it and says it can be deleted by hand**. For a tree drawn in ADR-274's flat layout and redrawn in the per-cluster layout of [#570](https://github.com/algernon28/vespera/issues/570), that folder is `documents/`, at the default location and at a named root alike. The per-cluster layout is #570's record, and so is the closing line's clause and its test.

### 7. What the documents say

- **`CONTEXT.md`**, in this change: **Deliverable root** is added; **Working directory** is the directory an invocation writes into, and the deliverable root when one is named, and holds the deliverable only where none is; **Archive**'s "everything an invocation writes goes in a working directory" reads "in a working directory, or at the deliverable root". **Publication-ready artifact** and **Copy** stay as they read: neither says where the tree lies, and `documents/` is still where a copy is put.
- **The implementer's, in the change that builds this**: `application.yaml`'s empty `deliverable-root:` key and its comment; README's "Where things live", which names `vespera-deliverable.txt` and says the tree is drawn at the deliverable root where `vespera.deliverable-root` names one, and its configuration section, which names the property beside `vespera.corpus-root`; `docs/check-claims.mjs`'s check of the files README says it writes, whose pattern reads `.txt` too, since `MARKER_FILE_NAME` is a constant the check reads; `docs/architecture.md` §1's 6b and `docs/running-stage-by-stage.md`'s stage 6b; and `AGENTS.md`'s two sentences that put the deliverable in the working directory.

### 8. No run id moves

`DeliverableRoot`, the marker and the writer's change are in `deliverable`, and the checks are asked from `pipeline`: no stage's version names either (ADR-274 §5, ADR-222). `profile` does not change, and no run's `config_consumed` gains a value.

## Decided here

1. **A property and nothing else** (§1): no option (ADR-047) and no profile key (ADR-066's reason).
2. **The unnamed root is checked too, as `<working directory>/deliverable`** (§2): the term says a deliverable root lies outside the corpus root and the seed folder, named or not, and that folder is where every unnamed tree lies.
3. **The comparisons are made at the start of every `vespera run`** (§2), before the walk that a tree beneath the corpus root would feed, and the run-dependent half of §3 at the drawing, the one place the generation run is known.
4. **The marker is `vespera-deliverable.txt`, two lines naming the working directory and the generation run, in every tree** (§3), the unnamed included: every tree says what drew it, and a later rule can recognise one wherever it lies by one file, which the agent guard's ticket [#569](https://github.com/algernon28/vespera/issues/569) needs. It is written before the first copy, so a stopped drawing is resumed.
5. **At the start a marker of this working directory is accepted whatever run it names** (§3): the run is not known there, and refusing it would refuse every redrawing of the same run.
6. **A drawing refused fails the step and throws nothing** (§3), so the writing it recorded stands.
7. **Real paths, the nearest existing folder for a path not made yet, name by name, without case on Windows** (§2).
8. **The gibibyte is read on the tree's drive** (§4), and on no other.
9. **A change of layout leaves the earlier layout's folders and the closing line names them** (§6).
10. **A named root's path is bounded at 146 characters** for Windows' 260 (§1), stated and not enforced.

## Consequences

- **The operator can have the tree drawn outside the working directory**, on another drive or in a folder they hand over, with nothing copied twice for it.
- **The working directory no longer holds everything an invocation writes**: the tree may lie at the root. Nothing else moves out.
- **A working directory inside the corpus root or the seed folder is refused at the start** while no root is named, since its `deliverable/` lies inside it: such a working directory was already walked into its own census.
- **A tree beneath the corpus root cannot be drawn**, so no copy is walked back in as a file occurrence.
- **Every tree gains one file**, the marker, and a tree drawn before this record gains it at its next drawing.
- **A new generation run needs a new root**: a named root holding another run's tree fails the invocation at the drawing, its writing kept.
- **Two working directories cannot share a root**: the first to draw marks it, and the second is refused.
- **ADR-212's counting script does not count a tree drawn at a named root.**
- **An agent meets a named root as the place it lies in** (ADR-264 §2): refused outside the allow list, open inside it, and kept from every copy by ADR-274 §8's written rule wherever the root lies.
- **The callers of `Deliverable.writeTo` pass the root**: the ten test classes and fixtures that call it pass `DeliverableRoot.UNNAMED`, and draw where they drew.
- **No run id moves** (§8).

## Alternatives refused

- **A command-line option**, `--deliverable-root` (Decided here 1).
- **A profile key** (Decided here 1).
- **Checking only when the tree would be drawn** (§2): after the walk a tree beneath the corpus root has already fed it.
- **Refusing every root that is not empty**: it would refuse redrawing a run's own tree, which ADR-274 §6 makes every invocation do.
- **Drawing over another generation run's tree**: one folder would hold the copies of two runs under one index, and ADR-103's "a re-run under a changed configuration writes a new tree beside the old one" would no longer hold.
- **Deleting what an earlier tree or layout left** (ADR-103, §6).
- **Comparing paths as typed**: a link, a junction or another spelling of the corpus root would let a root beneath it.
- **Refusing a root anywhere in the archive**: Vespera cannot see where the archive ends above the corpus root.
- **A marker naming the generation run alone**: two working directories drawing one run's tree would share a root.
- **A marker in named roots only**: two kinds of tree, and nothing for a later rule to recognise an unnamed one by.
- **The gibibyte read on both drives**: the copies take nothing from the working directory's drive where the root lies elsewhere.

## What this does not decide

- **The control panel's field and its check as the operator types** (map #551's panel tickets).
- **The per-cluster layout and the folders it leaves** (#570).
- **How the agent guard recognises a root by its marker** (#569).
- **A root that cannot be made**, its drive gone or its parent a file: the drawing fails as a tree that cannot be written fails today.
- **Two invocations of two working directories that find one root empty at the same moment**: ADR-177's lock holds one working directory, not a root.
- **A root whose path is longer than 146 characters** (§1).

## Tests

| Test | What it holds |
| --- | --- |
| `deliverable.DeliverableRootTest.anUnnamedRootIsTheGenerationRunsFolderInTheWorkingDirectory` | §1: with no root named, blank or `null`, the tree is `<working directory>/deliverable/<generation run>` |
| `deliverable.DeliverableRootTest.aNamedRootIsTheTreeItself` | §1: a named root, stripped, is the tree, absolute, for any generation run |
| `deliverable.DeliverableRootTest.refusesARootThatIsTheCorpusRootInsideItOrAboveIt` | §2: the three relations to the corpus root, each its whole line, and the check makes no folder |
| `deliverable.DeliverableRootTest.refusesARootThatIsTheSeedFolderInsideItOrAboveIt` | §2: the three relations to the seed folder, each its whole line |
| `deliverable.DeliverableRootTest.acceptsARootBesideTheCorpusRootOrTheSeedFolderOrInsideTheWorkingDirectory` | §2: `archive-copies` beside `archive`, a sibling of the seed folder sharing its name's start, and a root inside the working directory, each accepted, none made |
| `deliverable.DeliverableRootTest.aRootReachedThroughALinkIsComparedWhereTheLinkLeads` | §2: a root beneath a link to the corpus root is inside it; aborts where no link can be made |
| `deliverable.DeliverableRootTest.onWindowsANameIsComparedWithoutRegardToCase` | §2, Windows only: a root under a seed folder spelled in another case, neither yet made, is inside it |
| `deliverable.DeliverableRootTest.anUnnamedRootIsCheckedAsTheWorkingDirectorysDeliverableFolder` | §2 and Decided here 2: a working directory inside the corpus root is refused as `<working directory>/deliverable`, and one beside it is not |
| `deliverable.DeliverableRootTest.atTheStartANamedRootIsAcceptedEmptyOrHoldingATreeOfThisWorkingDirectory` | §3: absent, empty, and marked by this working directory under another run accepted; a stray file, a marker of another working directory, an unreadable marker and a file in the root's place refused, each by its line, and nothing in them changed |
| `deliverable.DeliverableRootTest.atTheDrawingANamedRootIsAcceptedEmptyOrHoldingThisRunsTree` | §3: absent, empty and marked by this working directory and run accepted; another run's tree, a stray file and another working directory's tree refused, each by its line |
| `deliverable.DeliverableRootTest.anUnnamedRootIsNeverRefusedAtTheDrawing` | §3: an unnamed tree with no marker, as drawn before this record, is accepted |
| `deliverable.TheTreeIsDrawnAtTheDeliverableRootTest.everyTreeCarriesAMarkerNamingItsWorkingDirectoryAndItsGenerationRun` | §3: the unnamed tree's marker, byte for byte |
| `deliverable.TheTreeIsDrawnAtTheDeliverableRootTest.aNamedRootIsTheTreeWithItsIndexAtTheTop` | §1: the index, the manifest, the copies and the marker at the root, and nothing under the working directory's `deliverable/` |
| `deliverable.TheTreeIsDrawnAtTheDeliverableRootTest.theMarkerIsInPlaceBeforeTheFirstCopy` | §3: when the writer announces its copies, the marker stands and no copy does |
| `deliverable.TheTreeIsDrawnAtTheDeliverableRootTest.theTreeTheWriterLeavesIsTheOneTheChecksAccept` | §3: after a drawing, both checks accept the root for its working directory and run, and refuse it for another run or working directory |
| `deliverable.TheTreeIsDrawnAtTheDeliverableRootTest.aTreeOfTheSameRunIsRedrawnInPlaceAndNothingBeneathItIsDeleted` | §3 and §6: a second drawing puts the index back and leaves an earlier layout's copy and a stray file as they were |
| `deliverable.TheTreeIsDrawnAtTheDeliverableRootTest.theGibibyteIsReadOnTheDriveTheTreeIsWrittenTo` | §4: with the working directory's free space unreadable and the root's readable, the shipped reading copies both survivors |
| `pipeline.TheDeliverableRootInvocationTest.aNamedRootHoldsTheTreeAndTheClosingLineNamesItsIndex` | §1 and §5: a campaign's tree at the root, its marker, nothing under `deliverable/`, the closing line naming `<root>/index.html`, and that index opened once |
| `pipeline.TheDeliverableRootInvocationTest.aSecondInvocationOfTheSameRunRedrawsTheRootInPlace` | §3: the same run's root redrawn, its index put back, under one generation run |
| `pipeline.TheDeliverableRootInvocationTest.aRootHoldingAnotherRunsTreeIsNotDrawnOver` | §3: exit 1, the one line at error level, the root as it was, no index opened and nothing under `deliverable/` |
| `pipeline.TheDeliverableRootInvocationTest.aRootThatIsNotEmptyAndHoldsNoTreeIsRefusedBeforeAnythingIsWalked` | §2 and §3: exit 1, the one line on standard error, no walk recorded, the root's file as it was |
| `pipeline.TheDeliverableRootInvocationTest.aRootInsideTheCorpusRootIsRefusedBeforeAnythingIsWalked` | §2: exit 1, the one line on standard error, no walk recorded, no root made |

**Restated with the build**: every call of `Deliverable.writeTo` in `DeliverableProgressTest`, `EntryPicturesTest`, `EverySurvivorIsCopiedTest`, `IndexPageTest`, `TreeIsTheSameWrittenAPartitionAtATimeTest`, `TreeThatFailsPartwayLeavesTheOldIndexAndManifestTest`, `TreeThatFailsWritingItsManifestLeavesTheOldIndexAndManifestTest`, `TreeWhoseIndexIsRefusedForAMomentIsStillPutInPlaceTest` and the fixtures `ListedArrangement` and `ListedTree` passes `DeliverableRoot.UNNAMED`, and its claims stand. `TreeIsTheSameWrittenAPartitionAtATimeTest.everyFileIsTheSame` holds the eight files and the marker, every file but the marker byte for byte the same, the two trees being drawn for two working directories that each marker names. `ConfiguredRootTest.refusesWhenNoRootIsNamedAnywhere` and `ProgressPageRunsInvocationTest`, which build `VesperaCommand.Run` themselves, follow its constructor.

**Not pinned, and why**: §6's clause of the closing line, since no layout but ADR-274's exists to draw over, which #570's record pins; that `vespera label` asks nothing, since it never reaches the job; and the 146 characters of §1, a bound stated as ADR-274 §2.7's is.
