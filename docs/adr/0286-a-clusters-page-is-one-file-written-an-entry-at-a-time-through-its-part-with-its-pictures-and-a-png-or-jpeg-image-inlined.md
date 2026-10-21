# ADR-286 — A cluster's page is one file, written an entry at a time through its part, with its pictures and a PNG or JPEG image inlined

> **Partly amended — see [ADR-314](0314-the-confluence-rendering-writes-each-picture-its-cluster-page-shows-as-a-file-of-its-own-from-the-same-bytes-the-html-page-inlines.md).** Of §1 and §4, for the Confluence rendering alone: under `confluence/` each picture a cluster's page shows is a file of its own, from the same bytes, and the page names it. The HTML tree's page is unchanged.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §1.1, whose "no `src` or `href` that names a scheme or a host" admits one exception, an `<img>`'s `src` holding a `data:` URI of §1 here; §1's tree, which holds no picture file; and §2.7's bound of 69 characters, which bounds the index and the pages, there being no picture (§4). Every other rule of §1.1, §1.3's escaping and §2's copies stand.
- **Amends**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md) §0, whose cluster folder holds its page and its copies and no picture (§4); §1.5, whose reserved names lose the pictures' and gain `index.html.part` and `index-<digits>.html.part` (§3, §4); §2, whose "names of the pictures the cluster's page shows" leave what a copy's name is worked out from, and whose "one partition's entry-picture HTML is held until that partition's pages are written" no longer holds (§2, §4); §4, whose closing line gains a clause counting the files an earlier drawing left in a cluster folder, which answers its *What this does not decide* for files (§4); §6's `Copies.copyAll`, `CopyNames.of` and `EntryPictures.appendUnder` (§7); and Decided here 3 and 12, which lose the pictures' names and the entry-picture HTML. Everything else in ADR-280 stands.
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1's bound of 146 characters, which bounds the index and the pages, there being no picture (§4).
- **Amends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §3, whose picture file, its folder, its name of 16 hexadecimal characters and its destination are struck: a picture is shown by a `data:` URI and written into no file, and its digest still decides what the furniture rule decides (§1, §4); §4, whose "it is not shown inline" no longer holds of an image survivor whose copy is a PNG or a JPEG (§6); §6, whose count of what is not shown gains a picture whose bytes are not of the kind it declares (§1); and §9, whose second pass asks a survivor's pictures while its entry is written and holds nothing past it (§2). §1's furniture rule and its population, §2, §5's alt text, §6's budget of ten and §8's reading order stand.
- **Amends**: [ADR-150](0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md) §4, "A standalone image file still shows its entry and nothing else": its entry shows its copy inline where the copy is a PNG or a JPEG (§6). That no picture of the cache is shown under it stands, and so does §3, the furniture rule decided over the whole tree in the first pass.
- **Amends**: [ADR-242](0242-a-page-moved-over-its-place-is-moved-again-a-few-times-where-the-move-is-refused-as-access-denied.md), whose *What this does not decide* leaves "the cluster files and picture files, which are written in place": a cluster's page is moved into place by `ReplacingMove` as the index is, and there is no picture file (§3).
- **Amends**: [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §6, whose "A cluster file is written whole, as now" reads that a cluster's page is written beside its place and moved over it (§3); and ADR-223 §9 and [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §9, whose lists of what is still held, as ADR-280 §2 amended them, lose one partition's entry-picture HTML (§2).
- **Amends**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)'s "Self-contained has a test" and [ADR-135](0135-a-membership-entry-links-relatively-or-does-not-link-at-all-and-the-citation-anchor-stands.md), which says how that test binds: the test is §5's, stricter. "Nothing is ever cleaned up" stands.
- **Keeps**: ADR-274 §2.2, the copies of a partition made before its pages; ADR-274 §1.3, one escaping rule; ADR-274 §5, the tree keys no run; ADR-274 §8, nothing outside the tree names a document; ADR-276 §3, nothing beneath a deliverable root is deleted; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), a reader's words say *group*.
- **Read with**: [#571](https://github.com/algernon28/vespera/issues/571), which may split a cluster's page into `index.html`, `index-2.html` and on: the writer counts the bytes it has written and knows where each entry ends (§2), and the threshold and the splitting are #571's.
- **Settles**: [#560](https://github.com/algernon28/vespera/issues/560).
- **Rests on**: a reading of `ClusterPage`, `EntryPictures`, `Deliverable.writeTo`, `CopyNames`, `Copies`, `Html`, `ledger.ReplacingMove`, `NextAction`'s closing line and `GenerationTasklet`'s progress, and of RFC 2397 and RFC 4648; and on the arithmetic of base64, which writes 4 characters for each 3 bytes, the last group padded. No archive, working directory or tree was opened for this record (ADR-196, ADR-283).

## Context

On 2026-10-18, charting map [#551](https://github.com/algernon28/vespera/issues/551), the operator settled that a cluster's page is one file, its images inlined as base64.

**What the tree is.** A cluster folder holds `index.html`, the verified copy of each survivor of the cluster and the pictures the page shows, each `<16 hex>.png` or `.jpg`, named from its digest (ADR-149 §3, ADR-280 §0). An `<img>` names its file alone. ADR-274 §1.1 refuses any `src` or `href` naming a scheme, and `TheTreeIsHtmlTest.everyPageStandsAlone` holds it.

**How the page is written.** `ClusterPage.write` builds the whole page in one `StringBuilder` and writes it in place with `Files.writeString`; only the index and the manifest are written beside their place and moved (ADR-223 §6, ADR-242). Because a copy yields to a picture's name (ADR-280 §1.5), each cluster's pictures are written before its first copy, and the HTML each entry shows of them is held until the partition's pages are written (ADR-280 §2).

**What a page holds.** A document shows at most ten pictures, PNG and JPEG only (ADR-149 §6). An image survivor shows its entry and nothing else (ADR-150 §4): its copy stands beside the page (ADR-280 §0), and the crop Docling made of it is never shown.

**What self-contained meant.** Every link inside the tree resolves with no database, ledger or network (ADR-103), through the renderer the tree is opened with (ADR-135).

**What no record measured.** How large a cluster's page grows: ADR-108 uses 412 documents only as an example, ADR-274 records an arrangement of 92 clusters, and ADR-211 says a partition can hold nearly every survivor. A picture inlined as base64 is 4 characters for each 3 of its bytes, about a third more than its file.

## Decision

### 1. A picture is a `data:` URI on its `<img>`, and that is the one scheme a page names

- **The exception to ADR-274 §1.1** is exactly this: an `<img>` element's `src` whose value is `data:image/png;base64,` or `data:image/jpeg;base64,` followed by the payload, the bytes in RFC 4648 §4's standard alphabet, `A`–`Z`, `a`–`z`, `0`–`9`, `+` and `/`, padded with `=` to a multiple of four characters, with no line break and no space. Nothing else on any page names a scheme or a host: no `href` holds a `data:` URI, no element but `<img>` holds one, and no media type but those two is written.
- **The writer writes such a URI only for bytes of the kind it declares.** PNG bytes begin with the eight bytes `89 50 4E 47 0D 0A 1A 0A`, and JPEG bytes with `FF D8 FF`. A picture Docling declared `image/png` or `image/jpeg` whose bytes do not begin so is not shown, and is counted on the line ADR-149 §6 writes of the pictures not shown, as a picture of any other media type already is. So the media type a page declares is the one its bytes carry.
- **The alt text stays ADR-149 §5's**, the caption escaped by ADR-274 §1.3's rule, and the paragraph around the image stays `<p><img src="…" alt="…"></p>` inside the entry's `<li>`.
- **A picture's identity is still its SHA-256 digest**, by which the furniture rule decides what recurs (ADR-149 §1, ADR-150 §3). The digest no longer names a file.

### 2. The page is written an entry at a time, and the writer knows how far it has written

- **The page is UTF-8 handed to a stream as it is made**, never built whole: the head, the heading, the writing or the sentence written in its place, the disclosure and the list's heading, then each entry in the order ADR-133 numbers them, then the tail.
- **An entry is written as it is reached.** Its link or its path and reason, then its copy inline where §6 shows one, then its pictures, then the line counting what is not shown, then `</li>`. A survivor's pictures are asked of the source when its entry is written, once in this second pass, as ADR-149 §9 has it; the first pass, which decides the furniture over every picture of every survivor the tree lists, is unchanged (ADR-150 §3, ADR-223 §7).
- **A picture's base64 is encoded straight from its bytes into the stream.** No string of the payload is made. What is held while a page is written is what the source hands for the survivor whose entry is being written, one document's pictures as ADR-149 §9 bounds them, and that entry's few lines of HTML. An image survivor's copy (§6) is read from its file through the encoder a buffer at a time and is not held whole. Nothing is held past its entry, and nothing across a partition: **ADR-280 §2's entry-picture HTML, held for a partition until its pages were written, goes**, and with it ADR-223 §9's and ADR-220 §9's line for it.
- **The writer counts the bytes it has handed to the stream, and is told nothing it has not handed on.** After each entry's `</li>` and the line break after it, it tells its caller how many bytes of the page it has handed on, every one of them already handed to the stream beneath it. That is where #571 can split a page at an entry's end once a page passes a number of bytes. **What that number is, and whether a page is split at all, are #571's**; this record writes one file per cluster.
- **The copies are still made a partition at a time before its pages** (ADR-274 §2.2), so an entry states what stands and §6 can read a copy.

### 3. The page is written through `index.html.part` and moved into place

- **A cluster's page is written to `index.html.part` in its folder** and moved over `index.html` by `ledger.ReplacingMove` once it is whole, as the index and the manifest are (ADR-223 §6, ADR-242), with ADR-242's attempts and waits and ADR-244's to ADR-246's reading of what is attempted again.
- **A page whose move fails after those attempts fails the drawing** as the index's move does: the failure is thrown, and the tree's index and manifest, moved only after every page, are not moved. A reader finds the earlier page whole, or the new one, never part of either; and a first drawing that fails leaves no `index.html` in that folder.
- **A `.part` a stopped drawing left is written over** by the next drawing that writes the page, as a copy's is (ADR-274 §2.4). A finished tree holds no `.part`.
- **So a copy yields to `index.html.part` and to `index-<digits>.html.part`**, compared by ADR-280 §1.4's keys: they join §1.5's reserved names, beside `index.html`, `index-<digits>.html` and another copy's `.part`. `index-<digits>.html.part` is reserved now so that #571's further pages find their parts free.

### 4. A cluster folder holds its page and its copies, and what an earlier drawing left in it is counted

- **No picture file is written.** ADR-149 §3's file, its name of 16 hexadecimal characters and its destination are struck, and the picture names leave ADR-280 §1.5's reserved names: a copy no longer yields to a name a picture once had, and a copy's name is worked out from the cluster's members alone. **A cluster folder holds `index.html` and the copies of its survivors**, and, where #571 splits a page, its further pages.
- **ADR-274 §2.7's 69 characters and ADR-276 §1's 146 bound the index and the pages.** A picture was the longest file they were computed for; a page's `index.html.part` is shorter.
- **After a cluster's page is moved into place, while its names are in hand, the writer lists that cluster's folder.** A regular file that is neither the page, `index.html`, nor a name this drawing gave one of the cluster's survivors counts as left behind by an earlier drawing: a picture an earlier drawing wrote, a copy whose name has changed since, as one that took a suffix to yield to a picture's name does once pictures reserve nothing, or a `.part` no attempt of this drawing wrote over. A folder inside a cluster folder is not looked at, as ADR-280 §4 has it. **The names are compared exactly**, as this drawing wrote them, which is how the file system lists them back; **a cluster folder that cannot be listed counts 0**, the listing being a courtesy, as ADR-280 §4's is. The counts of every cluster folder are summed and told once, with ADR-280 §4's listing.
- **The closing line gains, as ADR-276 §6 and ADR-280 §4 have it, a count of those files, never their names**: *"; M files left by an earlier drawing can be deleted by hand"*, `file` in the singular for 1, after the count of documents not copied and before the line's last full stop. Where ADR-280 §4's folders are counted too, one clause counts both: *"; N folders and M files left by an earlier drawing can be deleted by hand"*; and after `documents/`, *"; documents/ holds an earlier layout's copies and can be deleted by hand, and N folders and M files left by an earlier drawing can be deleted by hand"*, either count left out where it is 0. With nothing left behind, or where this invocation drew no tree, there is no clause.
- **Nothing is deleted** (ADR-103, ADR-276 §3). A tree drawn before this record keeps its pictures, and the closing line counts them.

### 5. Self-contained, as a test of every page

**ADR-103's test, as ADR-135 bound it, is replaced by this one.** On every page of the tree, every `src` and every `href` is one of:

1. a `#` and a fragment naming an `id` on the same page;
2. a relative path, with no scheme and not rooted, that, percent-decoded and resolved from the page's own folder, is an existing regular file inside the tree;
3. on an `<img>`'s `src` only, a `data:` URI of §1 whose payload decodes, by RFC 4648 §4, to bytes beginning with the signature of the media type it declares.

No `<img>`'s `src` names a file. And **each cluster folder holds only its page and the copies `documents.csv` lists for it**, by its `copy` column. So the tree opens anywhere, offline, with no database, ledger or network, and nothing it shows is outside it.

### 6. An image survivor whose copy is a PNG or a JPEG is shown inline on its entry

- **Where a survivor's copy stands**, made or kept (ADR-274 §2.3, §2.4), **and its first bytes are the PNG or the JPEG signature of §1**, its entry shows the copy itself after its link: `<p><img src="data:image/png;base64,…" alt=""></p>`, or `image/jpeg`, the copy's bytes as base64, read from the copy in its cluster folder, written before the page (§2). Its alt text is empty: the link above it names the document.
- **It is not a picture** (`CONTEXT.md`): no furniture rule judges it, it is outside the budget of ten, and it is not counted on the line of what is not shown. The source of pictures is asked about the survivor as about every other, once in each pass, and `pipeline` hands none for an image (ADR-150 §4, ADR-226), so the crop of it in the cache is still shown nowhere.
- **Any other copy is linked only**: a GIF, a TIFF, a BMP, an image whose copy did not stand, and every document that is not an image.
- **A copy that cannot be read is linked only**, as one of another kind is: any `IOException` opening it or reading its first bytes leaves the entry its link and nothing else, and fails neither the entry nor the drawing. The first bytes are read before any `<img>` is begun, so such a failure never leaves part of a tag on the page. The test is the copy's bytes and not its name or its detected format, so it is the one §1 and §5 make.
- **The entry still links its copy** (ADR-280 §5).
- **No size bounds it.** An image is inlined whatever its size, a third larger as base64; a page past the size #571 chooses may then be split at an entry's end.

### 7. Where it is written

Every rule above lies in `deliverable`, and the closing line in `pipeline`, as ADR-280 §6 has it:

- **`ClusterPage.write(OutputStream page, RecordedCluster recorded, SynthesisDoc doc, Unwritten why, List<ListedSurvivor> members, Path folder, Copies copies, EntryPictures pictures, DeliverableProgress progress, LongConsumer entryEnds)`**, package-private, writes the page as UTF-8 to `page` (§2), `folder` being the cluster folder its copies stand in, and tells `entryEnds` after each entry how many bytes it has handed to `page`, all of them handed on. `Deliverable.writeTo` writes it to `index.html.part` and moves it into place by the `ReplacingMove` it already takes (§3).
- **`EntryPictures.writeUnder(OutputStream page, ListedSurvivor member, Optional<Path> copy)`**, package-private, takes the place of `appendUnder`, `writeFor` and `forgetPrepared`: it writes `member`'s copy inline where `copy` names one that stands and is a PNG or a JPEG (§6), then its pictures by the furniture rule and the budget (§1), each payload encoded straight into `page`, asking the source once.
- **`CopyNames.of(List<ListedSurvivor> members)`** and **`Copies.copyAll(Path clusterFolder, List<ListedSurvivor> members)`** lose the names of the pictures (§4); `index.html.part` and `index-<digits>.html.part` join the names `CopyNames` reserves (§3).
- **`DeliverableProgress.filesLeftBehind(long files)`**, told once, right after `leftBehind`, of §4's count, a number and no name (ADR-274 §8); and **`NextAction.DeliverableSummary`** gains **`int filesLeftBehind`** as its last component, which the generation step hands to the closing line as it hands the folders.

### 8. What the documents say

- **In this change**: `CONTEXT.md`'s *Picture*, shown inline under its document's entry and written into no file, and *Cluster folder*, which holds the page and the copies; `docs/architecture.md`'s 6b.
- **The implementer's, in the change that builds this**: README's "Where things live", whose cluster page has "the pictures it shows beside it", and its "Where the run ends", for the clause on files left behind.

### 9. No run id moves

The change lies in `deliverable`, which no stage's version names (ADR-274 §5), and in `pipeline`'s closing line, which no stage's version names (ADR-222). **The next invocation that reaches the tree redraws it in place** (ADR-274 §6): every page is written again through its part, its pictures inlined, and the picture files an earlier drawing wrote stay, counted by the closing line.

## Decided here

1. **One exception to ADR-274 §1.1, on `<img src>` only, and two media types** (§1): a picture needs a `src` and nothing else; an `href` holding a `data:` URI would open bytes a reader cannot see before following it.
2. **A picture is shown only where its bytes are of the kind it declares** (§1), so a page never declares one media type for bytes of another, and §5's test holds of the writer and not only of a fixture.
3. **Streamed an entry at a time, the bytes counted at each entry's end** (§2): a page grows with its cluster and its pictures and is held nowhere whole, and #571 can split it where an entry ends without a second way of writing it.
4. **Through `index.html.part` and `ReplacingMove`** (§3): a page as large as an inlined cluster takes long enough to write that one found half written is a real outcome, and the index and the manifest already go this way.
5. **`index.html.part` and `index-<digits>.html.part` reserved** (§3), so no copy stands where a page's part is written, now or once #571 splits a page.
6. **Files left in a cluster folder are counted, never named or deleted** (§4), as ADR-280 §4 counts folders: a copy's name comes from an original's, and nothing is deleted beneath a root.
7. **The self-contained test reads every `src` and `href` of every page** (§5), and what a cluster folder holds.
8. **An image survivor's PNG or JPEG copy is shown inline, read from the copy and told by its bytes** (§6): the operator asked that the images be inlined, the copy is the verified file, and its bytes are what a `data:` URI declares.

## Consequences

- **A cluster's page opens alone**, its pictures and its PNG and JPEG images in it, carried anywhere without its folder's other files.
- **A page is larger by a third of what it shows**, and a cluster of many pictures or large images makes a large page; #571 decides where one is split.
- **A reader finds a page whole or not at all**: an interrupted drawing leaves the earlier page or none, and a `.part` the next drawing writes over.
- **A document named as a picture once was keeps its own name** in a tree drawn after this record; a tree drawn before keeps that copy under its suffixed name, as a file left behind, beside the copy under its own.
- **The pictures a tree drawn before this record wrote stay in their folders**, and the closing line counts them until the operator deletes them.
- **What is held while the tree is drawn loses one partition's entry-picture HTML**, and gains nothing past one cluster's names.
- **A picture whose bytes are not of the kind Docling declared is counted as not shown**, where it was written under that kind's extension.
- **No run id moves** (§9).

## Alternatives refused

- **A folder of picture files beside the page**, as ADR-149 §3 had it: the operator settled one file per cluster with its images inlined.
- **A `data:` URI on an `href`, or of any media type but PNG and JPEG**: neither is needed to show a picture, and an `href` would open what the page does not show.
- **The media type Docling declared written as it came**: a page would declare PNG for bytes that are not, and §5's test would hold of the fixtures alone.
- **The page built whole, or a partition's entries held until its pages are written**: a page is as large as its cluster and its pictures, and nothing bounds a cluster.
- **Writing the page in place**: a drawing stopped while it writes leaves part of a page under the page's own name, which no reader can tell from a whole one.
- **A size above which an image is linked and not inlined**: the operator asked for the images inlined, and how large a page may be is #571's.
- **Telling an image by its name or its detected format**: a `data:` URI declares its bytes, so the bytes decide.
- **Deleting what an earlier drawing left, or naming it** (ADR-103, ADR-276 §3, ADR-280 §4).

## What this does not decide

- **Splitting a page, and the number of bytes past which one is split** (#571), which §2's count is there for.
- **How large a `data:` URI a browser shows**, which no browser was measured for here.
- **A folder an earlier drawing left inside a cluster folder**, which ADR-280 §4 leaves, and is not counted (§4).
- **An image survivor in any format but PNG and JPEG shown inline** (§6).

## Tests

| Test | What it holds |
| --- | --- |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.aPictureIsShownByABase64DataUriOnItsImageAndNoPictureFileIsWritten` | §1 and §4: a PNG and a JPEG picture shown as `<p><img src="data:image/png;base64,…" alt="">` and `image/jpeg`, each payload the picture's bytes in RFC 4648's standard alphabet, padded, with no line break; the cluster folder holds `index.html` and its copies and nothing else |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.aPictureWhoseBytesAreNotOfTheKindItDeclaresIsCountedAsNotShown` | §1: bytes declared `image/png` that begin with no PNG signature, and declared `image/jpeg` with no JPEG signature, are shown nowhere and counted on the line of what is not shown |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.thePageIsWrittenAnEntryAtATimeAndTellsTheBytesWrittenAtEachEntrysEnd` | §2: written to a stream, each entry's end is told once, in order, as the count of UTF-8 bytes already handed to the stream, which then ends with that entry's `</li>`; a title and a path carrying characters of more than one byte; the second entry's pictures are asked only after the first entry's end was handed on |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.aFirstDrawingWhosePageCannotBeMovedLeavesNoPageInItsFolder` | §3: with every move of a cluster's page refused, the drawing throws, no `index.html` stands in the cluster folder, and the tree's index is not put in place |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.aRedrawingWhosePageCannotBeMovedLeavesTheEarlierPageWhole` | §3: a finished drawing leaves no `.part`; a redrawing whose page's move is refused leaves `index.html` byte for byte the first drawing's |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.theWriterCountsTheFilesAnEarlierDrawingLeftInAClusterFolderAndDeletesNone` | §4: a first drawing counts 0; after an old picture, a copy under an old name and a folder are put in a cluster folder, a redrawing counts 2 files, and every one stands |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.aPngOrJpegImageIsShownInlineFromItsCopyAndAnyOtherImageIsOnlyLinked` | §6: a PNG and a JPEG image survivor's entries link their copies and show them as `data:` URIs of their copies' bytes with empty alt text; a PNG named `plan-renamed.dat` is shown inline as a PNG, its bytes deciding and not its name; a GIF's entry, a file named `not-a-png.png` holding text, and a PNG whose original is gone, link or name the document and show no image |
| `deliverable.AClusterPageIsOneFileWithItsPicturesInlinedTest.aCopyThatCannotBeReadShowsNoImageAndFailsNothing` | §6: handed a folder standing at a copy's place, `EntryPictures.writeUnder` throws nothing and writes nothing, not even the start of an `<img>` |
| `deliverable.CopyNamesTest.aCopyYieldsToThePagesNamesAndTheirPartsAndNotToAPicturesName` | §3 and §4: `index.html.part`, `INDEX-2.HTML.PART` and `index-12.html.part` take a suffix though no member is named as a page, and a name of 16 hexadecimal characters and `.png` keeps its own |
| `deliverable.TheTreeIsHtmlTest.everySourceAndLinkStaysOnItsPageOrInTheTree` | §5: on every page of a tree with pictures, an image survivor, citations and copies, each `href` and `src` is a fragment naming an `id` on the page, a relative path to a file inside the tree, or an `<img>`'s `data:` URI whose payload decodes to its declared signature; no `<img>` names a file; and each cluster folder holds its page and the copies `documents.csv` lists for it, and nothing else |
| `pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest.countsTheFilesLeftBehindWithoutNamingThem` | §4: `1 file` and `3 files left by an earlier drawing can be deleted by hand` |
| `pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest.countsTheFoldersAndTheFilesInOneClause` | §4: `2 folders and 3 files left by an earlier drawing`, and after the `documents/` clause, `, and 2 folders and 3 files` |

**Restated**, each test kept and its claim turned to this record:

- `deliverable.DeliverablePicturesTest`: every picture is found on the page as its `data:` URI and not as a file; a picture left out is shown on no page; the group's folder holds no picture; `namesThePictureFromItsBytesWhateverTheDocumentIsCalled` is `noCharacterOfADocumentsNameOrALabelReachesAPicturesSource`, its `src` made of the base64 alphabet alone; `writesAJpegAsJpgAndCountsAnyOtherKind` is `showsAJpegAsAJpegAndCountsAnyOtherKind`; the pixels of a fixture begin with the signature of the kind they declare.
- `deliverable.EntryPicturesTest`: `EntryPictures.writeUnder` writes to a stream, a picture's `src` its `data:` URI, and nothing is written into any folder.
- `deliverable.ClusterPageTest`: each page is written by the stream form of `ClusterPage.write`; its bytes are unchanged.
- `deliverable.TheTreeIsHtmlTest.everyPageStandsAlone`: its claim of no scheme or host is §5's test's; `escapesEveryValueInTextAndInAttributes` captions a real PNG, a picture of no kind being shown nowhere.
- `deliverable.TreeWhoseIndexIsRefusedForAMomentIsStillPutInPlaceTest`: only the moves at the tree's top are counted and refused, each group's page being moved by the same move.
- `deliverable.ListedTree`: gains `anImage`, a real PNG, JPEG or GIF written by the JDK; and `pipeline.PictureScriptedExtractionBeans`' diagram, letterhead and crop begin with the PNG signature.
- `pipeline.DeliverablePicturesInvocationTest`: the diagram and the chart are shown as `data:` URIs of the pixels the converter returned, and the letterhead and the header's crops on no page; `showsAnImageFileAsItsEntryAndNothingElse` is `showsAnImageFileInlineFromItsCopyAndNoneOfItsConversionsPictures`, the screenshot's entry linking its copy and showing it as a `data:` URI of its copy's bytes, the crop of the cache shown nowhere.
- `pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest`, `pipeline.TheClosingLineCountsTheCopiesThatDoNotStandTest` and `deliverable.ListedArrangement`: the summary is handed 0 files left behind, and `Copies.copyAll` no picture names.

**Retired**: `deliverable.TheClusterFolderHoldsItsPageAndItsCopiesTest.anOriginalNamedAsAPictureOfItsClusterYieldsToThePicture`, a copy yielding to a picture's name, which §4 removes; `CopyNamesTest` holds that such a name is kept.

**Not pinned, and why**: what is held while a page is written (§2), a bound on memory no test here measures beyond the order the stream receives the page in; the base64 written straight from the bytes with no string of the payload, which the bytes on the page cannot tell apart from a string; and ADR-242's attempts at a page's move, which `ledger.AMoveRefusedForAMomentIsTriedAgainTest` holds of the one move every page goes through.
