# ADR-373 — A folder differing in case alone from one the drawing wrote is left behind only where the file system tells them apart

- **Date**: 2026-11-11
- **Status**: accepted on 2026-11-11.
- **Decided**: under delegation, on [#782](https://github.com/algernon28/vespera/issues/782): a folder whose name differs in case alone from one the drawing wrote is the folder it wrote where the file system takes the two names for one folder, and is left behind where it tells them apart.
- **Keeps**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md) §4, what is listed, what a folder this drawing did not write is, that a file is not counted, that nothing inside a folder this drawing wrote is looked at, and that the listing is a courtesy; its §1.4's two keys, unchanged; [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md) §3.1, a set's folder drawn from its typed or default name, its case kept, and §6.3, a folder directly in `code/` this drawing did not write counted; [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md) §4 and [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §2, a plugin's folders looked into as the tree's are; [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §3, nothing beneath a deliverable root is deleted; [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5, a change to `deliverable` alone moves no run id.
- **Rests on**: a reading of `Deliverable.leftBehindBy`, `CodeCopies.draw`, `CodeSetFolderNames`, `IndexPage.partitionDirectoryName`, `IndexPage.clusterFolderName` and `IndexPage.slug`, `PluginDrawings`, and `CopyNames.keysOf`; and of the JDK's `Files.isSameFile`, which on Windows compares the volume and the file index of the two handles it opens and elsewhere the device and the inode, a folder and a link to it alike.

## Context

**ADR-280 §4 counts a folder this drawing did not write as left behind.** `Deliverable.leftBehindBy` lists the tree's top level, each partition folder, `code/`, and each plugin's folder and the partition folders in it, and tells a listed folder from one this drawing wrote by comparing its path from the tree's root with the paths the drawing wrote, **as exact strings**.

**A code set's folder keeps the case of its name.** ADR-296 §3.1 draws it from the set's typed name, or its default name, made safe and cut. A set the operator renames in `code-set-names.yaml` from `Core` to `core` is drawn at `code/core`.

**On a file system that folds case, `code/core` is the `code/Core` an earlier drawing made.** NTFS, and APFS as it is formatted by default, take the two names for one folder: the drawing finds the folder standing, copies the set into it, and listing `code/` returns it spelt `Core`, as it was made. `code/Core` is not among the paths written, so the folder this drawing has just copied the set into is counted as left behind, and the closing line tells the operator a folder holding the tree's code can be deleted by hand.

**On a file system that tells case apart, ext4 for one, they are two folders.** The drawing makes `code/core` beside `code/Core`, which holds the earlier drawing's copies and is left behind as ADR-296 §6.3 has it.

**Only a code set's folder can differ in case alone today.** A partition's folder and a cluster's folder are named by `IndexPage.slug`, lower-case ASCII letters, digits and hyphens; `code`, `confluence`, `website` and `documents` are fixed lower-case names. The rule below is stated for every folder the listing looks at, so a later name that keeps its case needs no record of its own.

## Decision

### 1. A folder this drawing wrote, under another spelling

**A listed folder is one this drawing wrote where its path from the tree's root equals a path the drawing wrote, or where both of these hold:**

1. **a key of its path meets a key of a path the drawing wrote in the same folder**, by ADR-280 §1.4's two keys, `CopyNames.keysOf` taken of each path whole; and
2. **the file system takes the two for one folder**: `Files.isSameFile` of the listed folder and of the written path resolved against the tree answers true.

**Any other listed folder counts as left behind**, as ADR-280 §4 has it: a name that meets a written one by its keys but which the file system holds as a folder of its own is counted.

So `code/Core`, listed after a drawing that wrote `code/core`, is the folder it wrote on NTFS and APFS, and is counted on ext4.

### 2. A folder whose sameness cannot be told is counted

Where `Files.isSameFile` cannot answer, the listed folder counts as left behind, its name differing from every path written, and the listing goes on to the next folder.

### 3. What is held

The keys are of strings the drawing already holds until the listing (ADR-280 §2, ADR-296 §3.4). The keys of the paths written directly in one listed folder are held while that folder is listed and let go after it; nothing else is added to what the drawing holds.

### 4. No run id moves

The change lies in `deliverable` alone, which no stage's version names (ADR-274 §5).

## Decided here

1. **The file system decides whether two spellings are one folder**, and the keys only say which written folder to ask it about: a listing is of what stands on the disk, and it is the disk that says whether `Core` and `core` are one folder.
2. **The keys are ADR-280 §1.4's**, the ones the copies and the set folders are told apart by, so a folder no spelling of a written name could reach is never asked about.

## Alternatives refused

- **Comparing by the keys alone.** On a file system that tells case apart, the earlier drawing's `code/Core`, a folder of its own holding the earlier copies, would no longer be counted: the same wrong count, moved to the other file systems. The keys also over-match each file system by the other's rule, which costs a name a suffix under ADR-280 §1.4 and would cost a folder its count here.
- **Asking the file system alone**, `Files.isSameFile` of a listed folder against every folder written beside it. One call for each pair, for every folder left behind; the keys bring it to the folders whose name could be the same.
- **Probing once whether the tree's file system folds case**, and comparing exactly or by the keys accordingly. The probe writes a file into the tree, which only the drawing's own files are written into, and case folding can be set folder by folder (NTFS's per-folder case sensitivity, ext4's `casefold` attribute); `Files.isSameFile` answers for the folder listed.
- **Respelling the earlier folder on the disk as the set's new name.** A drawing moves its own parts into place and moves no folder holding copies; the index and `code.csv` name `code/core`, which a file system that folds case resolves to the folder already.

## Consequences

- **On NTFS and APFS, a code set renamed in case alone leaves no folder counted as left behind**: its copies are in the folder it had, still spelt as it was first drawn.
- **On ext4, it leaves the earlier folder counted**, as before this record.
- **A link in a listed folder whose name differs in case alone from a folder the drawing wrote beside it, and which leads to that folder, is not counted**; a link leading anywhere else is counted as a folder is, as before.

## What this does not decide

- **The spelling on the disk of a folder whose set was renamed in case alone**, on a file system that folds case: it keeps the spelling it was first made with, and the links to it resolve.
- **Folders other than code sets' that keep their case**: none does today (Context); the rule of §1 holds of them when one does.

## Tests

| Method | What it holds |
| --- | --- |
| `deliverable.CodeSetsInTheTreeTest.aSetRenamedInCaseAloneLeavesNoFolderBehindWhereTheFileSystemFoldsCase` | §1: on a file system that takes a name and its upper case for one file, a set drawn as `Core` and then renamed `core` leaves one folder in `code/`, its copy reached as `code/core/a.c`, and the second drawing reports no documents folder and 0 other folders; aborted by assumption where the file system tells case apart |
| `deliverable.CodeSetsInTheTreeTest.aSetRenamedInCaseAloneLeavesItsEarlierFolderBehindWhereTheFileSystemTellsThemApart` | §1: on a file system that tells case apart, the same rename leaves `Core` and `core` in `code/`, and the second drawing counts 1 other folder; aborted by assumption where the file system folds case |
| `deliverable.CodeSetsInTheTreeTest.aFolderDifferingInCaseAloneThatLeadsToTheSetsFolderIsNotLeftBehind` | §1: a link `code/Core` beside the set's folder `code/core`, leading to it, standing for that folder under a spelling differing in case alone, is not counted: the drawing reports no documents folder and 0 other folders; aborted by assumption where the link cannot be made, as on a file system that folds case |

## What the implementation owes

Beside the tests above, which it turns green where they run, the change gives `Deliverable.leftBehindBy` §1's and §2's comparison at every level it lists, holding §3's keys and nothing more, and cites this record in its javadoc.
