# ADR-150 — A PDF's pictures are asked for as embedded pixels, and a picture repeated at one place or as a near-copy is furniture

- **Date**: 2026-09-25
- **Status**: accepted
- **Amends**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md). The extractor identity it composes keeps its shape, `docling-serve;image=<name>;<versions>;<sent options>`. It changes value, because the sent options gain `image_export_mode=embedded`. Every conversion cached under the old value is made again once (§2).
- **Amends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md), in four sections. §1's rule gains two conditions for pictures whose bytes do not recur (§3 here). §2's "the extractor identity stays as it is" and its reason are reversed (§1 here). §4 keeps its outcome, and its reason is replaced (§4 here). §9's two picture records each gain the picture's place on the page, `DocumentPictures` reads the row under the current extractor identity instead of any row for the content hash, and `Deliverable` decodes pictures, where ADR-149 said it never did (§5 here).
- **Keeps**: [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) (every option the client sends is in the identity, and an option it does not send is the pinned version's default), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) (one synchronous call per document, with one more form field), [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) (eight conversions in flight), [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) (no original is copied), and ADR-149's placement, naming, escaping, budget and population.
- **Rests on**: [`docs/research/pdf-picture-pixels.md`](../research/pdf-picture-pixels.md), measured on 2026-09-25 against a probe sidecar running the image `compose.yaml` pins.
- **Settles** [#286](https://github.com/algernon28/vespera/issues/286).

## Context

ADR-149 shows a surviving document's pictures from the extraction cache. On GesPOS none of them comes from a PDF. All 78 pictures in the 10 PDF and 2 JPG survivors were cached without pixels, so ADR-149 §2 skips them. The operator asked on 2026-09-25 for the charts in surviving documents to reach the reader, and a PDF is where a chart is most likely to be.

ADR-149 inferred the cause from the documentation: `docling-serve`'s default `image_export_mode` is `placeholder`. It declined to change the request, because #285 had ruled out a new conversion. #286 is the ticket ADR-149 named for that decision. The measurement (research §1 to §10) found:

- **`image_export_mode=embedded` is the one option that returns a PDF's picture pixels, and it is enough on its own.** All 79 pictures in the 12 fixtures came back as PNG `data:` URIs. Under the default, none did. `referenced` returned a file name and no bytes. `include_images=false` returned no pixels in any mode. The sidecar already crops every picture by default, and `placeholder` leaves the crops out of the answer.
- **It costs no conversion time.** Over three interleaved rounds of the 12 files, the sidecar's own processing time was the same with and without it, within the variation between rounds of the same request.
- **It changes nothing else in the answer.** The text and every table cell's text came back identical in all 12 files and all rounds. The Office formats already carried their pixels, and their answers are unchanged too.
- **The answer is bigger.** The 12 answers grew from 2.27 MB to 5.41 MB, which is about 280 KB more per PDF on average.
- **A chart is legible at the default scale**, 144 dpi. The default `images_scale` of 2.0 is also the most the sidecar accepts: 3.0 is refused with an error.
- **Crops do not recur byte for byte, so ADR-149's rule barely applies to them.** The same logo cropped from different pages of one document comes out a pixel or two larger or smaller each time. 57 pictures sit at the same place on two or more pages of their document. Only 10 of them have the same bytes as the crop at that place on another page. 30 have bytes that recur somewhere among the PDF survivors, mostly because two versions of one document share a layout. Under ADR-149's rule as written, 34 of the 77 PDF pictures would be shown, and nearly all of them are page-header logos.
- **A crop is deterministic.** All 79 pictures came back byte-identical in each of the three rounds, so a file named after its bytes stays the same name on every conversion.

## Decision

### 1. Every conversion asks for each picture's pixels embedded in the answer

`DoclingClient` sends one more form field on every call: **`image_export_mode=embedded`**. It sends this for every format, not only for PDFs and images.

- **For every format, because the identity is one value per run.** An option sent to some formats only would make the identity misstate the request for the rest. The Office answers were measured to be unchanged by it, so sending it costs them nothing but the one re-conversion of §2.
- **`embedded`, not `referenced`.** Under `referenced` the synchronous answer names a file, `image_000000_<hash>.png`, and carries no bytes. Reading the picture back would need a second call or a zip target, which is a different call shape from ADR-071's.
- **Nothing else about pictures is sent.** `include_images` already defaults to true, and it is what makes the sidecar crop. `images_scale` defaults to 2.0, which is legible and the maximum the sidecar accepts. Under ADR-090 an option the client does not send is the default of the version the identity pins, so sending either would only restate a default. Picture classification and picture description run a model, and nothing in this design has chosen one.

`DoclingClient.sentOptions()` becomes `to_formats=json;ocr_preset=rapidocr;image_export_mode=embedded;naming=1`, so the identity names the new option.

### 2. The identity changes, and every cached conversion is made again once

The extractor identity is `docling-serve;image=…;<versions>;` followed by the sent options, so its value changes. **Every row in `extraction_cache` becomes unreachable, and every file is converted again on its next run**, the Office files included. This is the cost ADR-090, ADR-100 and ADR-147 each paid for the same reason. It is paid once, because the new rows serve every later run.

**What it costs:**

- **Per call: nothing measurable.** The sidecar's processing time did not move (research §3).
- **On GesPOS: 5 minutes 39 seconds.** Stage 2 took 3 min 10 s and the seed extraction 2 min 29 s in the 2026-09-24 run, out of 9 min 21 s. **The identity change itself redoes only extraction.** The chunk and vector caches are keyed by content hash and chunker, not by the extractor identity, and the text they are made from is unchanged. **The build that ships this record re-mints the runs of stages 2 to 6b all the same**, because it changes `extraction`, `pipeline` and `synthesis`, whose implementation versions feed those stages' run ids (ADR-058). Over a reused walk (ADR-115), each later stage reads the run this invocation arrived at (ADR-154, which settled #290), so the re-minted runs carry an existing working directory through to 6b. The re-converted rows stay in `extraction_cache` either way.
- **On the 42,851-file folder this tool exists for: about 17 to 18 hours, extrapolated and not measured.** GesPOS's stage 2 converted at 1.47 s per walked file occurrence with eight in flight (190 s over 129). At that rate, 42,851 file occurrences take about 63,000 s. The real figure depends on the folder's share of PDFs, which take 19 s each on average against 2 s for a `.docx` in the GesPOS cache, and nobody has measured that share. **This is only paid by a working directory that has already converted the folder under ADR-147's identity.** No such working directory exists among the runs on this machine. ADR-147 changed the identity on 2026-09-24, so every conversion made before then is already due again.
- **In the cache: about 280 KB more per PDF**, on GesPOS's 10. That is 0.28 GB per thousand PDFs, in the single SQLite file.

**Why pay it.** Without the option no PDF picture can ever be shown, and the operator asked for exactly those. The per-call cost is nil. The one-time cost falls on a working directory that ADR-147 has just made re-convert anyway. The alternative, a per-format identity that would spare the Office rows, would redesign ADR-090's composition to save one re-conversion of files whose answers do not change.

### 3. A picture whose bytes do not recur is still furniture when it repeats at one place, or is a near-copy

ADR-149 §1's two conditions stand: (a) the decoded bytes occur more than once among the pictures of every survivor the tree lists, and (b) Docling placed the picture in the `furniture` layer. **Two conditions join them.** A picture with pixels is also furniture when:

- **(c) It is a near-copy of another picture the tree's survivors carry.** Its pixel width and height are each within **2 pixels** of the other picture's, and their difference hashes differ in at most **2 bits**. The two pictures can be in the same document or in different ones.
- **(d) It repeats at one place in its document.** Another picture in the same document sits on a **different page**, each of the four edges of its bounding box is within **3 points** of this one's, and their difference hashes differ in at most **8 bits**.

The population is ADR-149's: every picture of every survivor the tree lists. Every picture a condition matches is furniture, the other picture included. This is ADR-149's "the first included", for the same reason.

**The difference hash** is a 64-bit number computed from the decoded pixels, and it is defined exactly so that it is the same everywhere it is computed:

1. Decode the image, reading the sample values the file stores with no colour-space conversion: a grey image's sample is its R, G and B alike. **A palette image is read through its palette**, as PIL's `convert` does: each pixel is the red, green, blue and alpha of the palette entry its index names, never the index itself, and a transparency chunk gives the entries their alpha. **A sample wider than 8 bits is read by its high byte**, which scales it to 0 to 255 as PIL reads a 16-bit RGB image. Composite any transparency over white, each channel as `(c · a + 255 · (255 − a)) / 255`. Each pixel's luminance is `(299 R + 587 G + 114 B) / 1000`. All of it is integer arithmetic.
2. Divide the image into a grid of 9 columns and 8 rows. Pixel `(x, y)` belongs to column `x · 9 / width` and row `y · 8 / height`, both in integer division. Each cell's value is the integer mean of its pixels' luminance, the sum divided by the count.
3. For each row, and for each column `c` from 0 to 7, the bit is 1 when cell `c + 1` is greater than cell `c`. The 64 bits are read row by row, the first bit being the most significant.

**An image narrower than 9 pixels or shorter than 8 has no difference hash**, and neither has one the reader cannot decode. Conditions (c) and (d) do not apply to such a picture, and (a) and (b) still do.

**Why these numbers** (research §6 and §7):

- **(d)'s 3 points.** Crops of one header logo sit within 1 to 2 points of each other from page to page. Any tolerance from 2 to 6 points finds the same 57 pictures, and 1 point finds 50. None of the 26 Office pictures ADR-149 keeps has a match within 3 points on another page, although slides carry bounding boxes too.
- **(d)'s 8 bits.** It is there so that two different pictures that share a frame, such as a chart per page in one template, are not removed. Each of the 57 same-place pictures differs from its nearest same-place partner by at most 6 bits.
- **(c)'s 2 bits and 2 pixels.** Heading banners in one `.docx` are text in a box, all 1,028 by 56 pixels. Two of them with different text, "SIMPLIGI" and "51HGT&jm", differ by only 3 bits. At 3 bits and 2 pixels, (c) would remove 2 of the 26 pictures ADR-149 keeps. At 2, it removes none. What (c) is for: a logo on the first page of several PDFs, a banner shared by two surviving versions of a document, and small icons that come out 1 pixel apart from one document to the next.

**What it does on GesPOS, once the PDFs carry pixels:**

| | Office survivors | PDF survivors |
| --- | --- | --- |
| pictures with pixels | 127 | 77 |
| kept by ADR-149's rule as written | 26 | 34 |
| kept by this rule | **26**, the same ones | **3** |

The 3 PDF pictures kept are one entity-relationship diagram (1,201 by 616 pixels, the only chart-like picture in the 10 PDFs), one title-page logo that occurs in no other document, and one 23 by 20 pixel telephone icon. The icon is the one four other PDFs carry, but at that size a pixel of crop moves 7 to 10 bits of the hash. The last two are furniture the rule cannot see. That is the cheaper failure, for ADR-149's reason: a leaked logo costs a reader a glance, and a removed chart costs the information the operator asked for.

**Rejected:**

- **Position alone, with no hash.** It would find the same 57 pictures here. But it would remove a real chart that sits in the same frame on two pages of a templated report, and the 8-bit guard is what keeps it.
- **A near-copy rule at 3 bits or more**, because of the banners above.
- **Docling's picture classification** (`do_picture_classification`) as the instrument, **because it is outside the scope the operator set, not because it measured badly.** It is a model judging a picture, and ADR-149 records that #285 ruled that out. Measured, it costs about 1 % more processing time. It labels 76 of the 77 PDF pictures `logo` or `icon`, most at 0.95 or more, and file 03's diagram `flow_chart` at 0.98, so on the PDFs it does better than this rule. On the Office pictures it is unsure: it labels the eight `.docx` heading banners ADR-149 keeps as logos, icons, a table, a bar chart and a photograph, all below 0.6 (research §8). **Adopting it would reopen #285's scope, and that is the operator's call.** The operator was asked on 2026-09-25, before this change shipped, with these numbers, and declined it. Adopting it later is one more sent option, so one more re-conversion.
- **Restricting (c) and (d) to PDFs.** Measured, neither removes any of the 26 Office pictures ADR-149 keeps. One rule for every picture is simpler to state and to check than a rule per origin.

### 4. A standalone image file still shows its entry and nothing else

ADR-149 §4's outcome stands: an `IMAGE` survivor's entry links to the original, and no picture is written under it. **The reason changes.** ADR-149 said the cache held no pixels for one. Now it does: the picture Docling crops from an image file is a region of the original, 44 % and 65 % of its area in the two measured, re-sampled to one and a half times its pixel resolution and re-encoded as PNG. The two GesPOS JPGs, 12 KB and 42 KB, come back as pictures of 60 KB and 211 KB. Writing them would be the copy of an original that ADR-104 refuses, at about five times its size. The reader who wants the image follows the entry, as for any other document.

**`pipeline` enforces it**: the source of survivor pictures that `GenerationTasklet` gives `Deliverable` returns no pictures for an occurrence stage 1 detected as `IMAGE`. It reads the format through `DetectedFormats.formatFor` under the run's byte-level-reduction run id, which is the lookup stage 2 already makes. The check sits there rather than in `synthesis` because what it knows is where the pixels came from, which is a fact about extraction and not about the page. `ListedSurvivor` gains no format.

### 5. What the picture records carry, and where the hash is computed

- **`extraction`'s `DocumentPicture` gains `Optional<PicturePlace> place`, and `synthesis`'s `ListedPicture` gains `Optional<ListedPicturePlace> place`**, each as a fifth record component. Both place records are `(int page, double left, double top, double right, double bottom)`: two records, because neither module names the other's types (ADR-110), as with the pictures themselves. `pipeline` maps one onto the other. Each picture record keeps a four-argument constructor that gives an empty place, so ADR-149's callers and tests stand unchanged. The values are the page number and the four edges of the bounding box, as the response gives them in the picture's first `prov` entry. `place` is empty where there is no `prov` entry or it has no bounding box. The edges are compared only within one document, where Docling gives them in one coordinate origin, so no conversion between origins is made.
- **`DocumentPictures.forContentHash` takes the extractor identity too**, `forContentHash(String contentHash, ExtractorIdentity identity)`, and reads the one row under that identity, or nothing. `GenerationTasklet` passes the identity bean stage 2 converted under. ADR-149 keyed it by content hash alone, "ordered by extractor identity and limited to one row", for `DocumentTitles`' reason: a document has one title, and two identities disagreeing about it are a fact about the instruments. **Pictures are exactly where two identities disagree.** A working directory converted before this change holds a row without PDF pixels and, after its next run, a row with them, under two identities for one content hash. Ordering by identity would pick between them by the spelling of the option names. The current identity's row is the one stage 2 has just made or confirmed for every survivor that converts, so it is always there to read.
- **The difference hash is computed in `synthesis`**, where the rule is (ADR-149 §9). It is a package-private record, `DifferenceHash(long bits, int width, int height)`, with `static Optional<DifferenceHash> of(byte[] pixels)`, which decodes with `javax.imageio` and is empty for an image it cannot decode or one smaller than 9 by 8, and `int bitsApartFrom(DifferenceHash other)`. No dependency is added.
- **The first pass of ADR-149 §9 keeps, for each picture, the digest, the width and height, the difference hash and the place.** That is a few dozen bytes per picture, so pixels are still held one document at a time.
- **ADR-149's claim that `Deliverable` never decodes a picture no longer holds.** A picture that does not decode is still written, subject to (a), (b) and the budget, because none of those needs it decoded.

## Consequences

**A PDF's pictures reach the tree under ADR-149's placement, naming, escaping and budget.** On GesPOS that is 3 pictures in 2 of the 10 PDF survivors, one of them the diagram. The 74 others are furniture.

**The next run in every working directory converts every file again.** On GesPOS that is under six minutes, and the answers are the same apart from the pixels. On the 42,851-file folder it is an extrapolated 17 to 18 hours, and only where that folder has already been converted under ADR-147's identity. The cache grows by about 280 KB for each PDF.

**`vespera.docling.image` does not change**, because the image does not change. Only the request does, and the identity carries the request through the sent options.

**Pictures cached before this change are never read again**, because their rows are under the old identity. ADR-149 §2's rule for a picture without pixels still stands, for any answer that has one.

**The tests that pin the implementation:**

- `extraction/DoclingClientTest` pins that every format is sent `image_export_mode=embedded`, that no other picture option is sent, and that `sentOptions()` names it, so the identity carries it.
- `pipeline/RunIdentityGoldenTest` pins, as literal text, the extractor identity stage 2 records with the option in it, so the re-conversion this record causes is an edit a reviewer sees (ADR-153).
- `extraction/DoclingClientIT` pins, against the real sidecar, that a one-page PDF drawing a bar chart as an image, generated in the test, comes back with one picture whose pixels decode to an image. The same fixture was measured against the pinned image under both modes (research §1).
- `synthesis/DifferenceHashTest` pins the hash against the golden values in research §6c:
  - the direction of a comparison and the order of the bits;
  - the luminance weights, and compositing a transparent pixel over white;
  - a palette image read through its palette, and a palette image's transparency read over white, each against the value its full-colour twin has;
  - the integer assignment of a pixel to a column;
  - the smallest image with a hash, and no hash for bytes that are no image;
  - the size the hash carries and the count of bits two hashes differ in.
- `synthesis/DeliverablePicturesTest` pins (c) and (d), each with its thresholds on both sides: a near-copy exactly 2 pixels and 2 bits apart is furniture, and 3 bits or 3 pixels apart is not; two pictures on different pages exactly 3 points and 8 bits apart are furniture, and 9 bits, 4 points or the same page is not. ADR-149's tests there use pixels that do not decode, so they go on pinning that a picture with no hash is still written.
- `extraction/DocumentPictureTest` pins the place: the page and the four edges from the first `prov` entry, and no place where there is none.
- `extraction/DocumentPicturesTest` pins the reader under an identity: of two rows for one content hash under two identities, only the named one is read. It replaces ADR-149's "the identity that sorts first" test.
- `pipeline/DeliverablePicturesInvocationTest` pins, through a whole invocation, that an `IMAGE` survivor whose conversion carries a unique picture shows its entry and no picture.

**What stays open.** The thresholds rest on 204 pictures from one archive. A corpus of charts laid out in one frame per page, alike to within 8 bits, would lose them to (d), and nothing on the page says so. The share of PDFs in the 42,851-file folder, which sets the re-conversion time and the cache growth, has not been measured.
