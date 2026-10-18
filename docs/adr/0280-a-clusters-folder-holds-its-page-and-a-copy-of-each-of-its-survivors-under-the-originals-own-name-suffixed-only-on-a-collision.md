# ADR-280 — A cluster's folder holds its page and a copy of each of its survivors under the original's own name, suffixed only on a collision

- **Date**: 2026-10-18
- **Status**: accepted on 2026-10-18.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md), in these places and no others: §1's tree, which no longer holds `documents/` and in which `<partition>/<cluster>/` holds the cluster's page, its pictures and its copies (§0); §1.2's page, which is `index.html` in a folder named as the page's stem was (§0); §1.4's index paragraph, which stops naming `documents/`, and its `copy` column, which names the copy in its cluster's folder (§0); §2.1, a copy's name, which is the original's own name made safe, cut, and suffixed only on a collision (§1); §2.3's and §2.4's "the name it stands under following from the survivor alone", which reads "from the survivor and its cluster's members, both fixed for the tree" (§2); §2.5's survey after the last copy, which gains one count (§3); §2.6's link, `../documents/<name>`, which is `./<name>` percent-encoded (§5); §2.7's bound of 69 characters, which bounds the index, the pages and the pictures and no longer the copies (§3); and §7's closing line, which gains a clause where an earlier drawing left a folder behind (§4). §2.2's verification, §2.3's five outcomes, the gibibyte of §2.5, §2.4's trust in a copy at its own name, and "no row of the database records a copy" stand.
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md), in §1's named root, whose tree holds no `<root>/documents/`, and its bound of 146 characters, which from this record bounds the index, the pages and the pictures and no longer the copies (§3); and in §6, whose clause of the closing line and its test, owed to the record that changes the layout, this record states and pins (§4).
- **Amends**: [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), in "The path carries the order": a cluster's segment of the path is a folder, named with the order prefix and the slug of its cluster label as the page was, and the page in it is `index.html` (§0). The prefix, its padding and the slug, cut to 40 characters (ADR-274 §1.2), stand, and so does "the path is not identity".
- **Amends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §3, in the picture's destination, which from the page inside the folder is `<hex>.<ext>` and no longer `<stem>/<hex>.<ext>`: the folder and the file's name are the ones ADR-149 gives (§0). And ADR-149 §4 and [ADR-150](0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md) §4 are **struck where they say an image survivor is not copied**: ADR-274 §2 governs, and every survivor listed is copied, an image included (§0). That no picture from the cache is written under an image survivor's entry, and that none is shown inline from the archive, stand.
- **Keeps**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), one tree per generation run and "Nothing is ever cleaned up"; ADR-276 §3, nothing is deleted beneath a deliverable root, and §5, the closing line names the root's index; ADR-274 §1.1, every link relative and inside the tree, §1.3, one escaping rule, §5, the tree keys no run, and §8, nothing outside the tree names a document; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), a reader's words say *group*.
- **Read with**: [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §9 and [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §9, whose lists of what is still held gain one cluster's copy names at a time (§2). [#571](https://github.com/algernon28/vespera/issues/571), which may split a cluster's page into `index.html`, `index-2.html`, `index-3.html` and on, every one of which a copy yields to (§1). [#560](https://github.com/algernon28/vespera/issues/560), which may inline the pictures and so empty the folder of them.
- **Settles** [#570](https://github.com/algernon28/vespera/issues/570).
- **Rests on**: a reading of `Copies`, `Deliverable.writeTo`, `IndexPage`, `ClusterPage`, `EntryPictures`, `ManifestCsv` and `DeliverableRoot`, of `NextAction`'s closing line and `GenerationTasklet`'s survey of the copies, and of `document_cluster`, whose primary key is `(occurrence_id, run_id)`. No archive, working directory or tree was opened for this record (ADR-196).

## Context

On 2026-10-18, charting map [#551](https://github.com/algernon28/vespera/issues/551), the operator settled the per-cluster deliverable: `<partition>/<cluster>/` holds the cluster's page and the verified copies of that cluster's survivors, the index stays at the tree's root, and a copy keeps its original's file name, with an occurrence-id suffix only on a collision.

**What the tree is today.** A survivor's copy is `documents/<occurrence>-<slug40>.<ext>` (ADR-274 §2.1), flat, collision-free by construction, its path bounded whatever the archive's depth, and free of every character a path parser can refuse. ADR-274 refused "copies under their root-relative paths" for those reasons. A cluster's page is `<partition>/<cluster>.html`, and its pictures lie in `<partition>/<cluster>/`, a folder named from the page's stem (ADR-149 §3). `documents.csv`'s `copy` column and the index's paragraph both name `documents/`.

**What fixes a cluster's membership.** Each survivor is in exactly one cluster under a generation run's arrangement: `document_cluster`'s primary key is `(occurrence_id, run_id)`. So no survivor is copied twice, and the members of a cluster, their occurrence ids and their root-relative paths, are fixed for the tree a generation run draws.

**What a collision suffix costs.** A name that carries a suffix only on a collision depends on the cluster's other members, where ADR-274 §2.3 and §2.4 rest on "the name follows from the survivor alone": a copy at its name is trusted on resume and neither it nor its original is read.

**What the path budget was.** ADR-274 §2.7 allows the working directory 69 characters, from `deliverable/`, a run id of 64, folders of 45 and a copy named `<id>-<slug40>.<ext8>.part`; ADR-276 §1 turns that into 146 for a named root. An original's file name has no such bound, and Windows compares names without regard to case.

**What a folder's name is.** A partition folder and a cluster's name are the order prefix and a slug (ADR-112): a rendering, not identity. A changed cluster label is a new arrangement, with a generation run and a tree of its own (ADR-276 §3), and nothing is deleted (ADR-103).

**What two earlier records say of images.** ADR-149 §4 and ADR-150 §4 say an image survivor is not copied, which ADR-274 §2 and the code contradict: every survivor listed is copied.

**What ADR-276 §6 owes.** A tree drawn in ADR-274's flat layout and redrawn in a new one keeps its old `documents/` folder, and the closing line says it can be deleted by hand. ADR-276 states the rule and could not test it, there being no second layout.

## Decision

### 0. The cluster's folder and what it holds

**`<partition>/<cluster>/` is named as ADR-112 and ADR-274 §1.2 named the page's stem**, the order prefix and the cluster label's slug cut to 40 characters, without `.html`. It holds:

- **the cluster's page, `index.html`**, and, where #571 splits it, `index-2.html`, `index-3.html` and on;
- **the page's pictures**, until #560 inlines them, at ADR-149's `<16 hex>.<ext>` names: the folder they already lay in, so an `<img>` on the page names the file alone, `<hex>.<ext>`;
- **the verified copy of each of the cluster's survivors**, named by §1.

```
<deliverable root>/
  index.html                          the arrangement, whole, and what produced it
  documents.csv                       one row per survivor
  vespera-deliverable.txt             the marker (ADR-276 §3)
  <partition>/<cluster>/index.html    one page per cluster
  <partition>/<cluster>/<hex>.<ext>   the pictures that page shows (ADR-149)
  <partition>/<cluster>/<copy name>   a verified copy of each of the cluster's survivors
```

**The tree's `index.html` links `<partition>/<cluster>/index.html`.** `documents/` is no longer written.

**`documents.csv`'s `copy` column is `<partition>/<cluster>/<copy name>`**, from the tree's root with `/` between its parts, or nothing where no copy stands. **The index's paragraph stops naming `documents/`**: it says that each group's folder holds its page and a copy of every document listed on it, checked against the digest taken when it was converted.

**ADR-149 §4 and ADR-150 §4 are struck where they say an image survivor is not copied.** ADR-274 §2 governs: every survivor listed is copied, images included.

### 1. A copy's name, and what counts as a collision

1. **It starts from the original's file name**: the last element of its root-relative path, everything after its last `/`, as written.
2. **It is made safe on every operating system**, since the tree may be carried to Windows, in this order:
   - each of `< > : " / \ | ? *` and each control character, U+0000 to U+001F, becomes `_`;
   - trailing dots and spaces go;
   - a name whose part before its first dot is a Windows device name, `CON`, `PRN`, `AUX`, `NUL`, `COM1` to `COM9` or `LPT1` to `LPT9`, without regard to case, gets a leading `_`;
   - a name left empty is the survivor's occurrence id, in decimal.
3. **It is cut** (§3).
4. **The collision key** is the name in Unicode normalisation form C, upper-cased with `Locale.ROOT`. Two names collide when their keys are equal. This is without regard to case on every operating system, and a key that matches too much, as `ß` and `SS` do, costs only a suffix.
5. **The reserved names**, which a copy always yields to, compared by the same key: `index.html`; `index-<digits>.html`, the digits one or more of `0` to `9`; the names of the pictures written into the folder for the cluster's page; and `<another copy's name>.part`, read as the name, made safe and cut, of any other survivor of the cluster, and any name already given in the folder, followed by `.part`.
6. **Who keeps the plain name.** Within one cluster's folder the survivors are taken in ascending occurrence id. Of the survivors whose names collide, the lowest occurrence id whose name collides with no reserved name keeps its plain name; a name that collides with a reserved name is kept by none of them. Every other survivor of those whose names collide gets **`~<occurrence id>` before the extension**: `report.pdf` becomes `report~42.pdf`, and a name with no extension takes it at the end. The plain names kept count as given before the first suffix is, so a suffixed name is compared with every one of them. A suffixed name that still collides with a reserved name or a name already given gets `~<occurrence id>` again, `report~42~42.pdf`, until it is free; each attempt differs from the one before, and the names in a folder are finite, so this ends.
   - **The extension** is the name's last `.`-part, everything after its last dot, kept as written: it is not lower-cased, as ADR-274 §2.1's was.

### 2. How resume finds a copy

**A copy's name is a function of the survivor and its cluster's members**: their occurrence ids and their file names, and the names of the pictures the cluster's page shows. All are fixed for the tree, since membership is `document_cluster` under the tree's generation run and a picture's name is its own bytes'.

So **before a cluster's first copy, the writer works out every name in that cluster from what is recorded alone**, reading no original. ADR-274 §2.4 then holds unchanged: a file at the name worked out was put there by §2.2's move, is taken as copied, and neither it nor its original is read. ADR-274 §2.3's and §2.4's "the name follows from the survivor alone" reads "from the survivor and its cluster's members, both fixed for the tree".

**What is held is one cluster's names at a time**, at worst as many as the largest cluster holds survivors, for ADR-220's reason: the page written after the copies names them. A name worked out again from the same members is the same name.

### 3. The path budget: names are cut, not paths

- **A whole copy name is at most 100 UTF-16 code units**, suffix and extension included.
  - The stem is cut at a code point's boundary, so no lone surrogate is left, and dots and spaces the cut leaves at its end go.
  - The extension is kept whole where it is at most 16 code units, the dot before it not counted. Otherwise the name is taken to have no extension, and the whole of it is the stem.
  - The cut is made before collisions are looked for. A suffix that takes a name past 100 cuts its stem further: the stem of the original name is cut to leave room for every suffix the name carries.
- **No cut depends on where the tree lies**, so a name does not move with the root or the working directory, and resume holds wherever the tree is drawn.
- **A copy no longer has a fixed bound below 260 characters.** ADR-274 §2.7's 69 and ADR-276 §1's 146 still bound the index, the pages and the pictures. Below a deep root a copy may pass 260; the JDK writes it (ADR-103), and what may fail is another program.
- **The survey after the last copy (ADR-274 §2.5) gains a count**: how many copies stand, made or kept, at an absolute path longer than 259 characters. A count and no name. Where it is not 0, the line saying it tells the operator to name a shorter deliverable root in `vespera.deliverable-root`.

### 4. What an earlier drawing leaves behind

- **A cluster's folder changes its name within one tree only when the naming rule in `deliverable` changes.** A changed cluster label is a new arrangement, and so a new generation run with a tree of its own (ADR-276 §3). Nothing is deleted (ADR-103, ADR-276 §3).
- **After drawing, the writer lists the tree's top level and each partition folder it wrote.** A folder this drawing did not write counts as left behind. A file is not counted, and nothing inside a folder this drawing wrote is looked at.
- **The closing line gains one clause where anything was left behind**, as ADR-276 §6 has it, after the count of documents not copied and before the line's last full stop:
  - `documents/` is named: *"; documents/ holds an earlier layout's copies and can be deleted by hand"*;
  - any other folder left behind is counted and not named, since a cluster's folder is named from a cluster label a model wrote (ADR-274 §8): *"; N folders left by an earlier drawing can be deleted by hand"*, `folder` in the singular for 1;
  - where both were left, the second follows the first after *", and "*: *"; documents/ holds an earlier layout's copies and can be deleted by hand, and N folders left by an earlier drawing can be deleted by hand"*;
  - with nothing left behind, or where this invocation drew no tree, there is no clause.
- **This pays ADR-276 §6's debt**: the clause, and its test for a tree drawn flat and redrawn per cluster.

### 5. Links

- **An entry links `./<copy name>`**, every UTF-8 byte of the name outside RFC 3986's unreserved set, `A`–`Z`, `a`–`z`, `0`–`9`, `-`, `.`, `_` and `~`, percent-encoded with two upper-case hexadecimal digits. An original's name carries spaces, `#` and `%`. The destination is then escaped by ADR-274 §1.3's rule, which changes nothing in it.
- **The link's text stays the root-relative path**, escaped as ADR-274 §1.3 has it.

### 6. Where it is written

Every rule above lies in `deliverable`, one class for each as ADR-213 has it, and the closing line in `pipeline`:

- **`deliverable.CopyNames`**, package-private: `static final int LONGEST_NAME = 100`, and `static Map<OccurrenceId, String> of(List<ListedSurvivor> members, Set<String> pictureNames)`, every name in one cluster's folder by §1 and §3, from the members in any order and the names of the pictures written into the folder, reading no file.
- **`Copies.copyAll(Path clusterFolder, List<ListedSurvivor> members, Set<String> pictureNames)`** takes the place of `copyAll(List<ListedSurvivor>)`, working out the cluster's names before its first copy and copying into its folder. The static `Copies.nameOf` and `Copies.pathOf` go, a name being no function of one survivor; the page and the manifest ask for a member's name as `Copies` holds it for its cluster, or work it out again. `Copies.DIRECTORY_NAME` names the earlier layout's folder, for §4.
- **`EntryPictures.appendUnder(StringBuilder page, ListedSurvivor member, Path folder)`** takes the place of the form naming a picture directory beside the page: the pictures are written into `folder`, the page's own, and shown by their file names.
- **`DeliverableProgress.copiesAtALongPath(long copies)`**, told once, after the last copy, of §3's count; and **`DeliverableProgress.leftBehind(boolean documentsFolder, long otherFolders)`**, told once, after the index and the manifest are in place, of §4's listing. Both take only primitives (ADR-274 §8).
- **`NextAction.DeliverableSummary(Path tree, int clustersLeftUnwritten, OptionalInt documentsNotCopied, boolean documentsFolderLeftBehind, int foldersLeftBehind)`**, the generation step handing what it was told to the closing line through the job's execution context, as it hands the count of documents not copied (ADR-274 §7).

### 7. What the documents say

- **`CONTEXT.md`**, in this change: **Copy** is put in its cluster's folder under its original's name, suffixed only on a collision; **Cluster folder** is added.
- **The implementer's, in the change that builds this**: README's "Where things live", which names `documents/`; `AGENTS.md`'s sentence keeping agents from a copy "in a deliverable's `documents/`", which reads "in a deliverable's cluster folders", and its list of what is still held; `docs/architecture.md` §1's 6b; and `docs/running-stage-by-stage.md`'s stage 6b.

### 8. No run id moves

The change lies in `deliverable`, which no stage's version names (ADR-274 §5), and in `pipeline`'s closing line and generation step, which no stage's version names either (ADR-222). `profile` does not change, and no run's `config_consumed` gains a value. **The next invocation that reaches the tree redraws it in place in the new layout** (ADR-274 §6), and copies every survivor again under its new name.

## Decided here

1. **The original's own name, with a suffix only on a collision** (§1), where every copy could have carried its occurrence id: a reader sees the file as it was called, and the id appears only where two names would meet.
2. **A key without case, in normalisation form C, on every operating system** (§1.4): the tree is carried to Windows, and a key that matches too much costs a suffix and nothing else.
3. **The page's names, the pictures' names and every copy's `.part` are reserved** (§1.5): a copy never stands where the page, a picture or another copy's part is written.
4. **The lowest occurrence id keeps the plain name, and the suffix goes before the extension** (§1.6): the choice is fixed by the record alone, and the copy still opens by its extension.
5. **Names are worked out from what is recorded, before a cluster's first copy** (§2), so resume trusts a copy at its name unread, as ADR-274 §2.4 has it.
6. **Names are cut to 100 code units, and paths are not cut** (§3): a name that moved with the root would not be found on resume.
7. **A copy past 260 characters is written and counted, not refused** (§3), the count telling the operator what to change.
8. **A folder left behind is named where it is `documents/` and counted otherwise** (§4), a cluster's folder being named from a model's words.
9. **A link encodes every byte outside the unreserved set** (§5), so a space, `#` or `%` in a name leads to its copy.
10. **An image survivor is copied like every other** (§0), ADR-149 §4 and ADR-150 §4 being struck there.

## Consequences

- **A reader of the tree finds each cluster's page, pictures and documents in one folder**, each document under the name it had in the archive.
- **A tree drawn before this record is redrawn in place at the next invocation that reaches it**: every survivor is copied again under its new name, its old copies stay in `documents/`, and the closing line says that folder can be deleted by hand. The old `<partition>/<cluster>.html` pages are files, and stay without a word.
- **A copy's path may pass 260 characters below a deep root**, and the survey counts how many do.
- **A copy's name holds none of the characters, device names or trailing dots and spaces Windows refuses** (§1.2), whatever the original was called on the system that holds it.
- **A folder put at the top of a named root, or in a partition folder, by anyone but the writer is counted as left behind** by the closing line.
- **What is held while the tree is drawn gains one cluster's copy names.**
- **No run id moves** (§8).

## Alternatives refused

- **The occurrence id on every copy**, as ADR-274 §2.1 had it: the operator asked for the original's name, with a suffix only on a collision.
- **The suffix after the extension**, `report.pdf~42`: the copy would no longer open by its extension.
- **Comparing names as written, or with case on some systems**: two names one case apart would meet on Windows, where the tree may be carried.
- **Copies under their root-relative paths**, refused by ADR-274 for long paths and names a parser refuses: only the last element of the path is kept (§1.1).
- **Cutting a name by where the tree lies**: the name would move with the root or the working directory, and resume would not find a copy that stands (§3).
- **Refusing a copy whose path passes 260 characters**: the JDK writes it, and the operator is told by the count.
- **Reading the originals to name the copies**: a name is worked out from what is recorded (§2).
- **Deleting what an earlier drawing left** (ADR-103, ADR-276 §3), and **naming every folder left behind**, whose names come from a model's words (§4).
- **A table of copy names**: the tree stays the only record of a copy (ADR-274 §2.3), and a name is worked out again from fixed members.

## What this does not decide

- **A page split into `index-2.html` and on** (#571), and **the pictures inlined into the page** (#560).
- **A program opening a copy past 260 characters** (§3).
- **How the manifest, written a page of survivors at a time (ADR-223 §7), comes by each survivor's copy name**: it holds no more than one cluster's names at a time (§2), and works them out again from the cluster's members as the implementer chooses.
- **A name whose repeated suffixes leave no stem within 100 code units**, which takes more collisions within one folder than its members' names can make.
- **A file an earlier drawing left inside a folder this drawing wrote** (§4).
- **How the agent guard recognises a tree** ([#569](https://github.com/algernon28/vespera/issues/569)).

## Tests

| Test | What it holds |
| --- | --- |
| `deliverable.CopyNamesTest.aNameThatDiffersOnlyInCaseOrNormalisationIsKeptPlainByTheLowestOccurrenceOnly` | §1.4 and §1.6: three names one case apart, two spellings of one accented name, and `ß` against `SS`, the lowest occurrence keeping its plain name, the rest `~<id>` before the extension, a name as written kept as written, and the same names whatever order the members arrive in |
| `deliverable.CopyNamesTest.aCopyYieldsToThePagesNamesAndToAPicturesName` | §1.5: `index.html`, `Index.HTML`, `INDEX-2.HTML`, `index-12.html` and a picture's name in upper case each take a suffix, the lowest occurrence among them too, and `index-.html` does not |
| `deliverable.CopyNamesTest.aCopyYieldsToThePartOfAnotherCopy` | §1.5: `notes.txt.part` yields to `notes.txt` though its occurrence is the lower, and `MINUTES.DOCX.PART` to `minutes.docx` |
| `deliverable.CopyNamesTest.aSuffixedNameThatStillCollidesTakesTheSuffixAgain` | §1.6: with `plan~5.pdf` kept plain by a higher occurrence, occurrence 5's `PLAN.PDF` becomes `PLAN~5~5.PDF` |
| `deliverable.CopyNamesTest.aNameIsMadeSafeOnEveryOperatingSystem` | §1.2: each reserved character and a control character as `_`, device names with a leading `_` whatever their case and extension, `COM10` and `CONSOLE` untouched, trailing dots and spaces gone before the device test, and a name left empty as the occurrence id |
| `deliverable.CopyNamesTest.aNameIsCutToAHundredUnitsWithItsSuffixAndItsExtension` | §3: a name of 124 units cut to 100 with its extension, and its collision cut to 100 with `~22` and its extension |
| `deliverable.CopyNamesTest.theCutSplitsNoSurrogatePairAndDropsTheDotsAndSpacesItLeaves` | §3: a cut through a surrogate pair falls before it, and a cut ending in a space and a dot drops both |
| `deliverable.CopyNamesTest.anExtensionLongerThanSixteenUnitsIsPartOfTheStem` | §3 and §1.6: an extension of 20 units takes the suffix after it, one of 16 before it, and a name with no dot at its end |
| `deliverable.CopyNamesTest.namesAreCutBeforeTheyAreCompared` | §3: two names that differ only past the cut collide, the second cut further for its suffix |
| `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.aClustersFolderHoldsItsPageAndItsCopiesAndNoDocumentsFolderIsWritten` | §0: the tree's files are the index, the listing, the marker, and the cluster's `index.html` and two copies under their originals' names; no `documents/`; the index links the folder's `index.html` |
| `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.eachRowOfTheListingNamesItsCopyInItsClustersFolder` | §0 and §1: `documents.csv`'s `copy` for two clusters each holding a `minutes.docx`, both plain, and a third `MINUTES.DOCX` suffixed in its own folder |
| `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.anEntryLinksToItsCopyWithEveryByteOutsideTheUnreservedSetEncoded` | §5: `./50%25%20%231%20draft%20%28v2%29.pdf` under its path as text, followed to the copy and its bytes; an accented name's UTF-8 bytes encoded, aborting where the file system will not create it |
| `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.aSuffixedCopyThatStandsIsKeptWithoutReadingTheArchive` | §2: with both originals deleted, a redrawing keeps `Report.pdf` and `REPORT~43.PDF` unread, their times unchanged and both `copied` |
| `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.anOriginalNamedAsAPictureOfItsClusterYieldsToThePicture` | §0 and §1.5: the picture stands at its name holding its pixels, shown by its name alone, and the original of that name stands suffixed holding its own bytes, its entry linking there |
| `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.theSurveyAfterTheLastCopyCountsTheCopiesPastTheWindowsLimit` | §3: below a working directory deep enough, one copy past 259 characters and one within it, counted once as 1; aborts where the temporary folder is too deep |
| `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.theWriterReportsTheDocumentsFolderAndCountsTheOtherFoldersAnEarlierDrawingLeft` | §4: a first drawing reports nothing left; after `documents/`, a top-level folder, a partition's old cluster folder, an old page file and a folder inside a written cluster folder are put there, a redrawing reports `documents/` and 2 folders, and every one of them stands |
| `pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest.namesTheDocumentsFolderAsAnEarlierLayoutsCopies` | §4: the clause for `documents/`, after the count of documents not copied |
| `pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest.countsTheOtherFoldersLeftBehindWithoutNamingThem` | §4: `1 folder` and `2 folders left by an earlier drawing can be deleted by hand` |
| `pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest.namesTheDocumentsFolderAndCountsTheOthersWhereBothWereLeft` | §4: both, joined by `, and ` |
| `pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest.saysNothingOfAnEarlierDrawingWhereNothingWasLeft` | §4: no clause with nothing left, and none where this invocation drew no tree |
| `pipeline.DeliverableInvocationTest.aTreeDrawnFlatAndDrawnAgainPerClusterKeepsItsDocumentsFolderAndTheClosingLineSaysSo` | §4 and ADR-276 §6: after a tree holding a flat layout's `documents/` is redrawn, that folder and its copy stand, the copies stand in the cluster's folder, and the closing line ends with the `documents/` clause |

**Restated**, each test kept and its claim turned to this record's layout:

- `deliverable.EverySurvivorIsCopiedTest`: every copy is `1-fire-safety/1-fire-doors/<original's name>`, the page `1-fire-safety/1-fire-doors/index.html`, its links `./` and percent-encoded, a row's `copy` the folder's path; `namesEachCopyByItsOccurrenceAndAStemOfFortyCharactersAtMost` is `namesEachCopyByItsOriginalsOwnName`, a Cyrillic name kept as written, a long one whole within 100 units, one with no extension and one with two kept as written; `aDriveWithNoFreeSpaceReadTakesNoCopy` claims no copy stands in the cluster's folder; `aPartLeftByAStoppedWritingIsWrittenOver` leaves its part in that folder.
- `deliverable.ClusterPageTest`: the page is `index.html` in its cluster's folder, made by `Copies.copyAll` into that folder, and each entry links `./` and its copy's name percent-encoded, `b, c.pdf` as `./b%2C%20c.pdf`.
- `deliverable.ClusterFileTest`: the page is `<cluster>/index.html`; `leadsWithTheGeneratedTitleAndNamesTheFileFromTheLabel` is `leadsWithTheGeneratedTitleAndNamesTheFolderFromTheLabel`; an entry links `./retrofit.pdf`; `keepsANameWithASpaceOrAQuoteReadableAndFollowable` links `./follow-up%20report.pdf`, and a quote made `_` in the copy's name; an entry that links nowhere carries no `./`; the javadocs of the entity and backtick routes say why no `&` or backtick is left in a percent-encoded route.
- `deliverable.IndexPageTest`: the paragraph says each group's folder holds its page and a copy of every document listed on it, and names no `documents/`; a row links `<partition>/<cluster>/index.html`.
- `deliverable.TheTreeIsDrawnAtTheDeliverableRootTest`: the copies stand in the cluster's folder at the root; the redrawing makes `documents/` itself before putting an earlier layout's copy there.
- `deliverable.TheTreeIsHtmlTest`, `deliverable.DeliverableTest`, `deliverable.DeliverablePicturesTest`, `deliverable.EntryPicturesTest`, `deliverable.ListedArrangement`, `pipeline.DeliverablePicturesInvocationTest` and `pipeline.UnwrittenPage`: a cluster's page is `index.html` in its folder, a page is found as an `.html` file that is not the tree's own index, an `<img>` names its file alone, the pictures lie in the page's own folder beside it, `EntryPictures.appendUnder` is handed that folder, and a folder that holds the page is no longer a picture directory made only for pictures.
- `pipeline.DeliverableInvocationTest`: the copies are found in the cluster page's folder rather than `documents/`.
- `pipeline.DeliverablePicturesInvocationTest.showsAnImageFileAsItsEntryAndNothingElse`: the image survivor's copy stands beside the page under its own name and its entry links it (§0, ADR-149 §4 and ADR-150 §4 struck), its picture from the cache still written nowhere.
- `DeliverableRulesHaveOneHomeTest.eachCollaboratorIsPackagePrivate`: `CopyNames` is among the package-private collaborators of `deliverable` (§6).
- `pipeline.TheClosingLineCountsTheCopiesThatDoNotStandTest`: its summary carries nothing left behind.

**Not pinned, and why**: the 69 and 146 characters for the index, the pages and the pictures, bounds stated as ADR-274 §2.7's is; the wording of the survey's line beside its count, which `pipeline`'s logging test holds to numbers; and a name whose suffixes leave no stem within 100 units, which no folder's members can be made to reach in a test of reasonable size.
