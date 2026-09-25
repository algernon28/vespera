# ADR-149 — A survivor's pictures reach its cluster file from the extraction cache, and a picture that recurs is furniture

- **Date**: 2026-09-25
- **Status**: accepted
- **Amends**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md), on two points (§10). First, where it refuses the extracted text ("Why not the extracted text either"): a survivor's pictures are carved out of that refusal, within the rule and the budget below. The extracted text stays refused, and so does any copy of an original, a standalone image file included (§4). Second, where it says 6b does not check that the originals are still there: writing the tree now reads every listed survivor's original in full, once, to hash it. That read still records no verdict and still keeps the entry. It is a read, not an existence check.
- **Extends**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md). The tree gains one directory of picture files beside a cluster file, and only where that file shows a picture. Its test is unchanged: every link resolves with no database, no ledger and no network.
- **Keeps**: [ADR-135](0135-a-membership-entry-links-relatively-or-does-not-link-at-all-and-the-citation-anchor-stands.md) (a picture is linked by a path relative to the page), [ADR-137](0137-a-destinations-ampersand-is-percent-encoded-and-the-destination-is-a-fifth-surrounding.md) and [ADR-148](0148-a-backtick-is-escaped-in-every-surrounding-a-value-is-read-in-and-one-renderers-divergence-moves-no-rule.md) (the escaping rules), [ADR-145](0145-a-tables-cells-are-extracted-text-read-once-in-docling-reading-order.md) (the extracted text, unchanged), [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md) (the extractor identity, unchanged), and [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) and [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (the module boundaries). Nothing new is fetched from Docling.
- **Rests on**: [`docs/research/deliverable-pictures.md`](../research/deliverable-pictures.md), measured on 2026-09-25 read-only over the extraction cache of the 2026-09-24 GesPOS run, and its §7, the chosen shape rendered in the seven configurations the rendering record counts.
- **Settles** [#285](https://github.com/algernon28/vespera/issues/285).

## Context

A chart, a diagram or a screenshot in a surviving document can carry information its text does not, and the deliverable drops all of them. A reader of a cluster file sees the membership entry and can open the original. Nothing on the page shows what the document holds.

The pictures are already on disk. Docling returns them in each response's `DoclingDocument` JSON, and `extraction_cache.response_json` keeps that response verbatim. In the GesPOS run the cache holds 439 pictures, 298 of them with their pixels as a `data:image/png;base64,…` URI. `DoclingDocumentTexts` walks `pictures` only to read the text under them (ADR-145). The pixels are stored and never read.

The operator agreed the scope in #285. The pictures come from the cache, not from a new conversion. They are written into the tree as files, not inlined and not linked remotely. They are placed with the document they came from, and repeated furniture is left out. A model judging a picture is out of scope, and so is relevance per picture: a picture comes along with its document.

What the measurement found that shapes the decision (research §1 to §6):

- **Recurrence is what separates furniture, and `content_layer` alone does not.** Of the 127 survivor pictures with pixels, 36 recur across survivor documents (logos and one database icon) and 65 recur inside one document (diagram stencil icons and a 1×1 spacer). The remaining 26 are unique, and they are what a reader would call content: diagrams, table screenshots, heading banners, and a few product icons. Docling's `furniture` layer marks 14 pictures, every one of them already in the cross-document class.
- **Which documents the recurrence is counted over matters.** Counting over every walk-1 occurrence instead of the survivors removes 3 more of the 26, among them a table screenshot, and moves 2 icons to the cross-document class. All 5 changes come from matching the survivor against its own `REDUNDANT_WITH` twin, which stage 4 has already removed.
- **No size floor separates anything.** Every survivor picture under 30 px on its shorter side already recurs. The smallest unique ones are product icons, the same kind of object the rule removes elsewhere, and the heading banners with readable text are 48–86 px high.
- **The 141 pictures without pixels all come from Docling's PDF pipeline**, the two standalone `.JPG` survivors included. They are located with a bounding box and never cropped, and no rule can be applied to them without bytes.
- **The budget is barely tested here.** After the rule, the most any survivor keeps is 8 pictures. Before it, the most any survivor carries is 57.

## Decision

### 1. A picture is furniture when its bytes recur among the documents the tree lists, or when Docling put it in the furniture layer

A picture with pixels, in a survivor the tree lists, is **furniture**, and is left out of the tree, when either of these holds:

- **(a) Its decoded bytes occur more than once among the pictures of every survivor the tree lists**, whether in two documents or twice in one. Every occurrence of the recurring bytes is furniture, the first included. No copy is kept, because recurrence is the evidence and the evidence does not favour any single copy.
- **(b) Docling placed it in the `furniture` content layer**, meaning its own page-header and page-footer furniture.

**(a) is the rule, and it rests on the measurement.** Over the GesPOS survivors it removes 101 of 127 pictures: the 36 logos and database icons and the 65 repeated stencil icons and spacers. It keeps the 26 that inspection calls content (research §2).

**The population is the survivors this tree lists, `Deliverable`'s own `survivors` argument, and nothing wider.** Stages 1 and 4 have already removed exact copies and near-copies, so bytes that recur among the survivors mean real reuse, such as a letterhead, a logo or a stencil. Widening the population to the walk only adds matches with a survivor's own redundant twin (research §2a). That would remove a table screenshot from the one document left to show it, because its near-copy carried it too. Counting over the survivors also costs nothing extra, since `pipeline` already gathers exactly those documents for the tree.

**(b) is kept although it removed nothing extra here.** It is Docling's own claim that a picture is page furniture. 34 of the 36 pictures with that claim recur in the whole cache, and the other 2 are in documents that did not survive. It covers the one case (a) cannot see: a header logo in a one-page document with no peer in the tree. It costs a boolean read from the same JSON.

**No size floor.** The measurement looked for one and found none that separates furniture from content among the survivors (research §3). A unique 1×1 spacer, which GesPOS does not have, would be written: 98 bytes a reader cannot see. That is a cheaper failure than a floor that removes the heading banners.

**Recurrence is exact.** Two renderings of one logo at different sizes are different bytes and both survive (a). No perceptual hash is used. It would be a second instrument with a threshold of its own, and GesPOS's recurring pictures are byte-identical.

### 2. The 141 pictures without pixels are not written, nothing is said about them, and the extractor identity stays as it is

A picture whose response carries no `image.uri` data is not written, not linked and not counted anywhere in the tree.

**Nothing is said about them on the page, because no count of them would be true.** They cannot be sorted into furniture and content without bytes. The measurement shows most are letterhead logos repeated at the same position on every page (55 of 141 by position alone). "12 pictures could not be shown" under a PDF would mostly be counting its header.

**Getting their pixels is a different decision, and it is not taken here.** The client sends `to_formats` and `ocr_preset` and nothing else, and `docling-serve`'s documented default `image_export_mode=placeholder` leaves the PDF pipeline's crops out of the JSON (research §5, from the documentation and not measured by a second conversion). Sending another export mode changes what is asked of the instrument. That makes it a new extractor identity under ADR-147, and every PDF and image in the archive would be converted again. #285 rules out a new conversion, so **ADR-147's identity is unchanged, and nothing about pictures joins it**. If the operator later wants PDF pictures, it is a ticket of its own. It would weigh a full re-conversion against pictures that the measurement says are mostly letterhead.

### 3. Where a picture sits, and what its file is named

**A picture sits inside its document's membership entry**, as a paragraph of its own after the entry's line, in the document's reading order. The continuation is indented by the width of the entry's list marker: three spaces under `1.`, four under `10.`.

```
1. <a id="document-1"></a>[reports/a.docx](../../archive/reports/a.docx)

   ![Fig. 1](01-fire-suppression-retrofits/0123456789abcdef.png)

   *2 more pictures from this document are not shown.*

2. <a id="document-2"></a>…
```

Measured in seven configurations of seven, the list stays one list, the numbering continues, every image renders inside its entry, and no continuation becomes a code block, the four-space one included (research §7). The entry keeps its number and its anchor, so ADR-133's numbering and every citation are untouched.

**The file sits in a directory beside the cluster file, named after that file**: the cluster file's own name without `.md`. The file is named from the picture's own bytes: the first 16 hexadecimal characters of the SHA-256 of the decoded pixels, in lower case, then `.png` for `image/png` or `.jpg` for `image/jpeg`.

```
deliverable/<run-id>/
  01-industrial-safety-standards/
    01-fire-suppression-retrofits.md
    01-fire-suppression-retrofits/
      0123456789abcdef.png
```

**No character of a document's name, of its path or of its caption reaches a file name or a destination.** The directory name is the cluster file's stem, which `pad` and `slug` have already reduced to `[0-9a-z-]`. The file name is hexadecimal. So the destination is `<stem>/<hex>.<ext>`, relative to the page's own directory (ADR-135), and made only of characters no Markdown, URI or filesystem rule treats as special. A hostile name, such as `../../x`, `](evil)`, `&copy;` or a backtick, has nowhere to land. The destination is written as it is, without going through ADR-137's destination rule, because percent-encoding would leave every one of these characters unchanged.

**Named from the bytes rather than from the entry's number**, for two reasons:

- A later invocation of the same run can renumber a cluster's membership. A cluster that faulted and is written on the repair pass goes from score order to sent-first order (ADR-133). A name taken from the entry number would then point a file name at a different document's picture. A name taken from the bytes is the same on every invocation, so rewriting the tree is idempotent (ADR-103).
- A document's name cannot reach it at all, so nothing needs escaping.

16 hexadecimal characters are 64 bits, and the rule in §1 means no two written pictures share bytes. Two different pictures in one cluster colliding on the prefix would need about 2³² pictures in that cluster before it became likely. The short name keeps the path well under Windows' 260-character limit, since the partition directory and the run id already use part of it.

**A picture is written for a cluster nothing was written over, too.** That page exists and lists its whole membership (ADR-111, ADR-112), and a picture belongs to its document, not to the writing. An entry that states "a document the writing was made from, which this group no longer holds" (ADR-133) has no document, so it has no pictures. The picture directory is created only when a picture is written into it.

### 4. A standalone image file that survived appears as its membership entry, and as nothing else

A survivor whose detected format is `IMAGE` gets the same entry as any other survivor, linked to the original in the archive (ADR-135). **It is not copied into the tree, and it is not shown inline from the archive.**

- Copying it is the copy ADR-104 refuses, and this record does not amend that part.
- An inline image pointing into the archive, `![](../../archive/x.jpg)`, would draw a full-size original of any size into the page. It would also make the page depend on something outside the tree for its content, not only for a link the reader chooses to follow. A reader who wants the image clicks the entry, as for any other document.
- **It has no picture from the cache either.** Docling routes an image through its PDF pipeline, and both `IMAGE` survivors come back as one picture without pixels (research §5), so §2 applies.

### 5. Alt text is Docling's caption or nothing, and it is escaped as link text

**A picture's alt text is the text of Docling's `captions` for it**: each reference resolved to its text item, the texts joined by a single space, then trimmed. **Where Docling gave none, the alt text is empty**, `![](…)`. No file name, no document name, no "picture 3" and no model-written description are invented. A caption is not also written as a visible line. The alt text is the one place it goes, so one escaping rule covers it. GesPOS carries one caption in 439 pictures, on a picture without pixels, so in practice the alt text is empty.

**The alt text is folded onto one line, then escaped by `escapeLinkText`**, the membership entry's rule (ADR-136, ADR-138, ADR-148). An image description is link text in the CommonMark grammar, inside the same list item as the entry's link, so it is the same surrounding and answers to the same reader. It folds as the heading and the cell do, because a caption is arbitrary text that can carry a blank line, and a blank line would end the paragraph and break the image. The entry's own text follows ADR-136's rule, and this record does not change it.

**The escape is load-bearing** (research §7). Written through, a caption reading `a]b ![x](https://example.com/i.png)` replaces the tree's picture with a remote image in seven configurations of seven, and GitHub fetches it through its camo proxy. That breaks ADR-103's test. Escaped, the same caption renders as alt text in all seven, and nothing is fetched. `marked` shortens that alt text, keeping only the nested link's text and decoding `&copy;`. That is a divergence of one renderer from the specification, and under ADR-148 §3 it carries no weight.

### 6. The budget is ten pictures per document, and the rest are counted, not hidden

**A document shows at most ten pictures**: the first ten that are not furniture, in its reading order. `Deliverable.PICTURES_PER_DOCUMENT` fixes the number in code. It is not a profile key. Like ADR-140's eight conversions in flight, it is not a judgement about the corpus: it is about how much one entry of a page can carry before a reader loses the list. `synthesis` also cannot read `Profile` (ADR-110), so a key would need `pipeline` to thread one more value down for a number nobody has a reason to tune.

**What the budget leaves out is counted on the entry**, in one line after its pictures:

- `*1 more picture from this document is not shown.*`
- `*N more pictures from this document are not shown.*` for N of two or more.

The line counts every non-furniture picture with pixels that was not written: the ones past the tenth, and any whose media type is neither `image/png` nor `image/jpeg`, because no file extension is written for a type that was never measured. It does not count furniture, which is left out by rule, or pictures without pixels (§2). Where nothing is left out, no line is written. The line follows ADR-133's subset disclosure: a page that shows part of something says so, in words a reader can check against the original.

**Why ten.** The most any GesPOS survivor keeps is 8 (research §4), so ten cuts nothing measured and leaves room. It stops a document that the rule cannot thin, such as a slide deck of fifty unique screenshots, from turning its entry into a gallery that pushes the rest of the membership off the screen. **No per-picture size cap**: the largest pictures are the diagrams, the most informative kind, and the largest survivor picture kept is 85 KB. **No tree-wide cap**: the per-document count already bounds the tree by the number of survivors. At GesPOS's measured rates (half the occurrences survive, 18 of 65 survivors carry pixels, about 35 KB kept per such survivor), the 42,851-file archive would add roughly 200 MB, not gigabytes.

### 7. `documents.csv` and `index.md` do not change

**The manifest stays one row per survivor with the columns it has** (ADR-104, ADR-112). Pictures would be a list inside a cell of a file whose purpose is to be loaded into a table without parsing (ADR-104). The pictures can be found in the tree, and their file names come from their bytes, so a consumer who wants them lists the directories.

**The index stays the arrangement and its provenance.** A picture count per cluster would tell a reader nothing they act on before opening the page. The page is one click away.

### 8. A document's reading order for pictures is ADR-145's walk

A document's pictures are ordered by where ADR-145's walk reaches each one's reference: the `body` tree first, then `furniture`. Any picture the walk never reaches follows, in the order of the `pictures` array. **ADR-145 is not amended.** The text that walk produces is exactly what it was, and this record only reads the order of the picture references the walk already visits.

### 9. Where the code lives, and which module reads what

**`extraction` reads the pixels, because it owns `extraction_cache` and is the only module that parses Docling's response** (ADR-041, ADR-106's precedent for titles).

- `DocumentPicture` is a public record: `mediaType`, `pixels`, `inFurnitureLayer`, `caption`. Its public static `allOf(String rawDoclingResponse)` returns every picture with pixels in the response, in the reading order of §8. It skips a picture with no `image`, and one whose `uri` is not a `data:` URI carrying base64. **Nothing is ever fetched**: an `http:` or `file:` URI is skipped, not followed. The media type is the one the data URI declares, and `caption` is §5's text, empty where Docling gave none.
- `DocumentPictures` is a public class but not a Spring component. `GenerationTasklet` builds it from its own `JdbcTemplate`, as it builds `ClusterFaults`, so no invocation test that loads the tasklet has to name it. Its `forContentHash(String contentHash)` returns `List<DocumentPicture>`, empty where nothing is cached. It is keyed by content hash alone, ordered by extractor identity and limited to one row, for `DocumentTitles`' stated reason. It is the only new query on `extraction_cache`.

**`pipeline` joins them, as it already does for titles and exemplars** (ADR-110). `GenerationTasklet.writeDeliverable` gives `Deliverable` a source of pictures per survivor. For each occurrence it resolves the file under the canonical root, hashes it through `DoclingExtractor.contentHashFor`, asks `DocumentPictures`, and maps each `DocumentPicture` to a `synthesis` `ListedPicture`. This is the chain `openingChunkOf` and `titleOf` already follow.

- **It does not use `ListedSurvivor.contentHash`.** That value is `corpus`'s stage-1 hash, which exists only for survivors that shared a size with another file: 18 of the 65 GesPOS survivors have one (research, method). `extraction` keys its cache on its own hash of every file.
- **A file the archive will not open contributes no pictures**, and the tasklet logs a warning, as `openingChunkOf` does. An archive is a live filesystem, and a document that has gone away since the walk is a fact about that document. It does not fail the tree.
- **The cost is a full read of every listed survivor's original, once per tree write.** Hashing a file reads all of it. Every invocation that writes the tree does this for every survivor the tree lists, a repair invocation included, whether or not the survivor turns out to carry a picture. The tasklet keeps each hash for the length of the write, so the second pass (below) does not read the file again. On the 42,851-file archive this is a read of every surviving original on every write. Stage 5 and 6b's exemplar step already hash every member of what they process, but only when that work runs; this read covers every listed survivor on every tree write, clusters already written included, so a repair invocation that writes nothing new still reads the whole surviving archive. §10 states what it does to ADR-104.
- **It is not narrowed here, for example to the formats that carried pixels on GesPOS.** A format filter would rest on one corpus, and [#286](https://github.com/algernon28/vespera/issues/286) (PDF pixels, being settled as ADR-150) could make the formats it skips carry pictures. The likely remedy is [#287](https://github.com/algernon28/vespera/issues/287) (the manifest's `content_hash`, being settled as ADR-151). If that gives each survivor a recorded extraction hash, the tasklet reads it instead of the file and the re-read disappears.

**`synthesis` decides everything a reader sees, and names no `extraction` type** (ADR-110).

- `ListedPicture` is a public record: `mediaType`, `pixels`, `inFurnitureLayer`, `caption`. It is a plain value, like `ListedSurvivor`.
- `SurvivorPictures` is a public functional interface: `List<ListedPicture> of(OccurrenceId occurrence)`. It returns the survivor's pictures with pixels, in reading order, empty where it has none. It is asked about each listed survivor twice, once per pass, and must give the same answer each time. It keeps no pixels between calls. `SurvivorPictures.none()` answers empty for every occurrence.
- `Deliverable.writeTo` gains a sixth parameter, `SurvivorPictures pictures`. The five-parameter form stays and calls the six-parameter form with `SurvivorPictures.none()`, so a tree written with no pictures is byte-for-byte the tree written today.
- **The furniture rule, the budget, the file naming, the placement and the alt text's escaping all live in `Deliverable`.** They are all decisions about the tree, and a rule that can be checked without a database belongs where it can be unit-tested (ADR-110's return).
- **Two passes, and pixels are held one document at a time.** The first pass asks the source about every listed survivor and keeps only each picture's digest, which is enough to count recurrence. The second pass, writing one cluster file at a time, asks again for that cluster's members and writes their pictures. Nothing holds every survivor's pixels at once. Each survivor's cached response is read and decoded twice, once per pass. That is a cost of database reads, not of conversions. The archive is read once, for the hash.

**Nothing new crosses a module boundary.** `extraction` gains two public types. `synthesis` gains two public types and one overload. `pipeline`, the composition root, is the only module that names both, which ADR-040 already allows.

### 10. What this amends in ADR-104, stated in its own words

This record amends ADR-104 on two points: the refusal of extracted text, and the section on not checking the originals.

ADR-104 refuses to write "the extracted text beside each cluster", because it would be "a third rendering of every document". **That refusal stands for text, and it no longer covers pictures that pass §1 and §6.** Three differences justify the exception, and they are the reason it is narrow:

- The text is already rendered once in the tree, by the synthesis doc written from it. A picture is rendered nowhere, so the page has no other way to show it.
- A picture has no competing reading. ADR-104's risk is that a reader reads a copy of the text in place of the synthesis. The pictures are the part of a document a synthesis doc cannot carry, so showing them does not compete with it.
- The rule and the budget bound it. It is at most ten pictures per document, and on GesPOS 26 pictures in 9 of 65 survivors. A full rendering of every document it is not.

**It is not a copy of an original.** The pixels are Docling's rendering of a picture embedded in a document, returned as PNG, and not a file the archive holds. An original that is itself an image stays in the archive (§4). ADR-104's "copies nothing" holds as it is.

**ADR-104's "6b does not check that the originals are still there" is amended.** That section says there is no stat and no existence check at write time, because a missing original has no verdict it could be recorded as. After this record, writing the tree reads every listed survivor's original in full, once, to hash it, on every invocation that writes the tree, repair invocations included (§9). What stays true of ADR-104's section:

- **No verdict.** A file that cannot be read records nothing in the ledger. 6a and 6b still remove nothing.
- **The entry is still written.** The unreadable survivor keeps its membership entry and its link, exactly as before. It only contributes no pictures, and the tasklet logs a warning.
- **The read is not an existence check.** Nothing is decided from whether the file is there, and the walk id in `index.md` still says when the links were true.

What is no longer true is that writing the tree leaves the archive alone. 6b's exemplar step already read the members of each cluster it writes over; the tree write now reads every surviving original on every invocation. §9 records that cost and names #287 as its likely remedy.

## Consequences

**A reader sees a document's pictures under its entry, and follows the entry to the original as before.** On GesPOS that is 26 pictures under 9 of the 65 entries, 633 KB in all. The other 9 survivors with pictures show nothing, because everything they carried recurs, and the 12 PDF and image survivors show nothing, because the cache holds no pixels for them.

**The tree gains directories.** A cluster file that shows a picture has a directory of the same name beside it. A plain-text reader of the `.md` file sees `![](stem/hex.png)` lines. Those are paths inside the tree, readable as such.

**The tests that pin the implementation:**

- `synthesis/DeliverablePicturesTest` pins:
  - the furniture rule: both halves, across documents and within one, over the whole tree, and a picture Docling marked as furniture in one document counting towards recurrence when another document carries it in the body;
  - the exact placement and indentation of pictures under two entries;
  - the file naming against hostile document names and labels;
  - the escaping and folding of a caption, and empty alt text;
  - the budget and its line, and the two media types;
  - the unchanged manifest and index;
  - a page written with no pictures, against its literal expected text;
  - the two passes, each listed survivor asked about twice.
- `extraction/DocumentPictureTest` pins reading order, the caption and the layer, and the skipping of a picture without pixels, one at a remote or `file:` address, and one whose base64 does not decode.
- `extraction/DocumentPicturesTest` pins the cache reader.
- `pipeline/DeliverablePicturesInvocationTest` pins the wiring in `GenerationTasklet` through a whole invocation. The converter double the rest of that package shares gives every document the same response, so every picture would recur and the rule would rightly remove all of them. This class uses its own double, `PictureScriptedExtractionBeans`, which answers by file name: one document carries a picture of its own, and both carry a shared one.

**Found while measuring, and not decided here.** `documents.csv`'s `content_hash` column is filled from `corpus`'s stage-1 hash, which exists for 18 of 65 GesPOS survivors. The other 47 rows carry a blank cell, and `GenerationTasklet` logs a warning for each. That is ADR-104's manifest partly empty on a real run. It is recorded here because §9 must not use that value. It is now #287, and settling it may also remove §9's re-read of every original.

**What stays open.** Which applications an operator opens the tree in is still unmeasured, as in every record since ADR-134. An application that confines local images to a workspace root, such as VS Code's preview, shows the pictures when the workspace is the tree or anything above it, and that has not been measured either. The share of furniture and the most pictures a survivor keeps are GesPOS numbers. A corpus that breaks the budget will show its line, and that is the signal to revisit ten.
