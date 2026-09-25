# What pictures the extraction cache holds, and which of them are furniture

Research record for [#285](https://github.com/algernon28/vespera/issues/285): a surviving document's pictures
reaching the deliverable beside it, read from the extraction cache. It measures what the furniture rule needs
(§1 to §6) and how the shape the decision chose renders (§7). Facts only. The decision is
[ADR-149](../adr/0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md).

## Scope and method

`MEASURED` on 2026-09-25 on Windows 11 Pro build 10.0.26200, Python 3 and Node v22.23.1.

**The run.** `D:\development\vespera-runs\2026-09-24-lo-gespos`, the latest GesPOS run: walk 1 is the archive
(129 file occurrences), walk 2 the seed folder (19). The database was opened read-only, as
`file:…/vespera.db?mode=ro` through Python's `sqlite3`, and nothing was written to the run directory. Scratch
output went to the session scratchpad and is not committed.

**Tying a cache row to an occurrence.** `extraction_cache` is keyed by the SHA-256 `extraction` computes for
itself (`ContentHashing`). `corpus`'s `content_hash` table cannot stand in for it: stage 1 hashes only
survivors that share a size (ADR-067), so it holds 33 rows, and **18 of the 65 survivors** have one. Every
occurrence's file was therefore read from the archive, read-only, and hashed the same way. All 148 files were
readable, and all 59 cache rows that carry a picture tie to an occurrence.

**Survivors** are the walk-1 occurrences with no blocking verdict, the query `Ledger.survivorCount` runs. The
run carries four verdict kinds, all blocking (`EXTRACTION_FAILED` 22, `OUT_OF_SCOPE` 20, `REDUNDANT_WITH` 11,
`SUPERSEDED_BY` 11), which leaves **65 survivors**. They are exactly the 65 rows of `document_cluster`, so every
survivor reaches the deliverable.

**A picture** is one entry of `document.json_content.pictures` in `response_json`. "Pixels" means its
`image.uri` is a `data:` URI, base64-decoded to bytes. Two pictures "recur" when their decoded bytes are
identical, compared by SHA-256.

**Inspection.** The 39 distinct byte strings among the survivors' pictures were written out as files and
looked at, one by one. The descriptions in §2 and §3 ("a logo", "a diagram icon") are from that inspection.
They are labels for a reader, not something the rule reads.

---

## 1. What is in the cache

| | Count |
|---|---|
| `extraction_cache` rows (one extractor identity) | 117 |
| rows whose document carries at least one picture | 59 (40 archive, 19 seed) |
| pictures | **439** |
| with pixels | **298**, every one `image/png`, `dpi` 72 for 288 of them |
| with no image at all | **141**. The `image` key is absent, not empty |
| `label` | `picture` for all 439 |
| `content_layer` | `body` 403 (262 with pixels, 141 without), `furniture` **36** (all with pixels) |
| a caption (`captions` resolving to a text item) | **1** of 439, `Fig. 1`, on a picture with no pixels |
| `annotations`, `meta` | empty on all 439 |
| a text item under a picture with pixels | 0 of 298 |

By the converter's origin mimetype, the pictures with pixels come from `.docx` (158), `.potx`/`.pptx` (88) and
`.doc` through LibreOffice (52). **Every one of the 141 without pixels has origin `application/pdf`.**

**Survivors.** 30 of the 65 survivors carry pictures, **205** in all:

| Survivor format | Survivors | With pictures | Pictures | With pixels |
|---|---|---|---|---|
| `WORDPROCESSING` | 16 | 16 | 47 | 47 |
| `ZIP_CONTAINER` (the two `.pptx`) | 2 | 2 | 80 | 80 |
| `PDF` | 10 | 10 | 76 | 0 |
| `IMAGE` (two `.JPG`) | 2 | 2 | 2 | 0 |
| `PLAIN_TEXT` | 35 | 0 | 0 | 0 |

So **127 survivor pictures in 18 documents carry pixels**. The other 78, in 12 documents, carry none.

## 2. Recurrence: identical bytes within a document and across documents

**Over the whole cache (298 pictures with pixels):** 134 distinct byte strings. 102 pictures are unique,
109 have bytes that recur in two or more documents (12 distinct byte strings), and 87 recur within one
document only (20 distinct).

**Over the survivors (127 pictures with pixels, 18 documents):** 39 distinct byte strings.

| Class | Pictures | Distinct | What they are, by inspection |
|---|---|---|---|
| bytes recur in ≥ 2 survivor documents | **36** | 5 | three Edenred logo variants (365×98 twice, 332×204), a 199×121 logo, a 62×69 database icon |
| bytes recur within one survivor document only | **65** | 8 | diagram stencil icons repeated inside one `.pptx` (116×29 ×18, 111×111 server ×12, 104×69 ×9, 102×102 ×8, 384×384 eyes ×4, 116×116 SMTP server ×2), a 153×91 Word-file icon ×2 in one `.docx`, and a **1×1 spacer ×10** |
| unique among the survivors | **26** | 26 | see below |

The 26 unique pictures are what a reader would call content. There are architecture diagrams (800×450 and
640×360, seven of them), table screenshots (782×112, 1028×86), seven 1023–1028 px wide heading banners that
carry text ("SIMPLIGI", "Procedura GoAnywhere: SwitchFrontEndTSP_movimenti"), and six product icons pasted
individually into a diagram (SFTP 80×65, Pivotal CRM 69×71, Oracle 100×30, a browser strip 457×110, and two
others).

**`content_layer` alone would catch 14 of the 101 recurring survivor pictures.** Every one of the 14
survivor pictures in the `furniture` layer is also in the cross-document class. The same logo is `body` in one
document and `furniture` in another: the 199×121 logo occurs 22 times in 11 documents, in both layers. Across
the whole cache, 34 of the 36 `furniture` pictures recur, and the 2 that do not are in documents that did not
survive.

### 2a. Which documents the recurrence is counted over changes the answer, and why

The same 127 survivor pictures were classed against three populations:

| Recurrence counted over | Cross-document | Within one document | Unique |
|---|---|---|---|
| the 18 survivor documents | 36 | 65 | **26** |
| every walk-1 occurrence (28 documents with pixels) | 49 | 55 | **23** |
| the whole cache, seeds included (43 documents) | 49 | 55 | 23 |

Widening the population past the survivors removes 3 of the 26 unique pictures: the 782×112 table
screenshot, the 457×110 browser strip and the 69×71 CRM icon. It also moves two within-document icons into the
cross-document class. **Every one of those extra matches is with the survivor's own redundant twin**:

- occurrence 3, `AcquiringBancario_ProcessiGestioneParco_1_3.docx` (survivor), shares the table screenshot with
  occurrence 4, `…_2_0_BOZZA.docx`, removed `REDUNDANT_WITH`;
- occurrence 12, `Docs_da_analizzare/GesPOS Logical Architectural map.pptx` (survivor), shares the other four
  with occurrence 67, the same deck under `produzione/`, removed `REDUNDANT_WITH`.

A picture shared only with a document stage 4 has already removed as a near-copy is not reuse across the
archive. It is the same document counted twice.

## 3. Size

Over the 298 pictures with pixels:

| | Bytes |
|---|---|
| min | 98 (the 1×1 spacer) |
| p5 | 339 |
| p25 | 4,943 |
| median | 6,514 |
| p75 | 23,584 |
| p95 | 75,319 |
| max | 735,931 |
| total | 6,137,965 |

| Shorter side below | Pictures |
|---|---|
| 16 px | 24, of which 19 recur. The 5 that do not are in non-survivor documents |
| 32 px | 54 |
| 64 px | 89 |
| 100 px | 157 |

**No size floor separates furniture from content among the survivors.** Every survivor picture under 30 px on
its shorter side recurs (the 1×1 spacer, the 116×29 icon), so recurrence has already caught them. The smallest
unique survivor pictures are product icons (69×71, 80×65, 100×30), the same kind of object as the stencil icons
recurrence catches. The seven heading banners with readable text are 48–86 px high, so any floor high enough to
catch the icons also catches the banners.

**What the survivors' kept pictures cost:** 26 pictures, 632,860 bytes in all, the largest 84,767.

## 4. Pictures per document

**All pictures, per document that carries any (59):** from 1 to 57. The largest are 57, 52, 32, 23 and 22.

**Pictures with pixels, per document that carries any (43):** median 3, max 57. The largest are 57, 52, 23,
19, 12, 11 and 10.

**Per survivor document with pixels, before and after the recurrence rule over the survivors:**

| Survivor | Before | Kept |
|---|---|---|
| `…/produzione/GESPOS architecture v_JM.pptx` | 57 | 7 |
| `…/GesPOS Logical Architectural map.pptx` | 23 | 2 |
| `…/EsitiTelegestione_CARD_ESITI_TLG_A1_Simpligi_v1_1.docx` | 10 | **8**, the most any survivor keeps |
| `…/GesposEvoluzioni.docx` | 5 | 3 |
| `…/GesposIntegrazioniPax.docx` | 4 | 2 |
| four more | 3, 3, 1, 1 | 1 each |
| nine more | one with 4, eight with 2 | 0 |

9 of the 18 keep none. Everything they carried was a logo or an icon that recurs among the survivors.

## 5. The 141 pictures with no pixels

- **All come from Docling's PDF pipeline.** The origin mimetype is `application/pdf` for all 141, in 16
  documents: the 10 `PDF` survivors, 4 seeds, and the 2 `IMAGE` survivors, which Docling routes through the
  same pipeline and each of which comes back as one picture covering the whole image.
- **No document mixes the two kinds.** A document's pictures all carry pixels or none do.
- **Each is located, not cropped.** Every one has a `prov` with a page number and a bounding box, and none has
  an `image`. 110 of them have one or more text items under them, which is OCR read through the picture, for
  example `ACCOR` / `Services` from a letterhead logo. ADR-145 already reads that text.
- **Most look like furniture, and there is no way to check.** 55 of the 141 sit at a bounding box repeated on
  two or more pages of the same document, which is what a page-header logo looks like. With no bytes, the
  recurrence rule of §2 cannot be applied to any of them.
- **Why they have no pixels, from the documentation and not measured by a second conversion.** The client
  sends `to_formats=json` and `ocr_preset=rapidocr` and nothing else (`DoclingClient.sentOptions()`), so
  `docling-serve` applies its own defaults: `include_images=true`, `images_scale=2.0` and
  `image_export_mode=placeholder`
  ([usage](https://github.com/docling-project/docling-serve/blob/main/docs/usage.md)). The word and slide
  backends write each embedded picture's data URI into the document when they parse it. The PDF pipeline
  crops a picture from a rendered page, and under `placeholder` that crop is not serialised. Getting the crops
  would mean sending a different export option. That option would be part of what was asked of the
  instrument, so it would be a new extractor identity (ADR-147), and every PDF and image in the archive would
  have to be converted again.

## 6. Survivors against non-survivors

| | Survivor documents | Non-survivor documents |
|---|---|---|
| documents with pictures | 30 | 29 (19 of them seeds) |
| pictures | 205 | 234 |
| with pixels | 127 | 171 |
| `furniture` layer | 14 | 22 |
| median bytes, with pixels | 6,514 | 7,041 |

The pictures themselves do not differ in any way a rule could use: same encoding, same size range, similar
layer shares. The difference that matters is the one §2a found. **39 of the 127 survivor pictures have bytes
that also occur in a non-survivor document**, and the ones that would change the result are all in a
survivor's redundant twin.

## 7. How the chosen shape renders

`MEASURED` in the seven configurations §6 of
[`deliverable-markdown-rendering.md`](deliverable-markdown-rendering.md) counts: `markdown-it` 15.0.2 with
`html` false and true, `marked` 18.0.13, `commonmark` 0.31.2 with `safe` false and true, and GitHub's
`POST /markdown` in `gfm` and `markdown` modes. **No writer produces this shape yet, so the fixture was written
by hand to the shape ADR-149 specifies**, and `cat -A` confirmed it before any renderer read it. The tests
that pin ADR-149 check the same bytes.

The fixture is a membership list of 11 entries. Entry 1 carries two pictures and a not-shown line. Entry 2 is
unlinked and carries one picture whose alt text is a hostile caption, escaped. Entry 10 has a four-character
marker, so its continuation is indented four spaces, and carries one picture and a not-shown line:

```
1. <a id="document-1"></a>[reports/a.docx](../../archive/reports/a.docx)

   ![Fig. 1](01-t/0123456789abcdef.png)

   ![](01-t/fedcba9876543210.png)

   *3 more pictures from this document are not shown.*

2. <a id="document-2"></a>reports/unlinked.docx

   ![a\]b \[x\](https://example.com/i.png) \<b> \&copy; \`c\` \\ end](01-t/00112233aabbccdd.png)
```

| Result | Configurations |
|---|---|
| one `<ol>`, 11 `<li>`, no `<pre>`: the list is unbroken and no continuation became a code block, the four-space one after entry 10 included | 7 of 7 |
| every `<img>` is inside its entry's `<li>` | 7 of 7 |
| every `src` is the relative path as written, and none goes through a proxy | 7 of 7. GitHub also wraps each image in a link to the same relative path |
| the escaped caption forms no link and no image, and fetches nothing | 7 of 7 |
| alt text reads `a]b [x](https://example.com/i.png) <b> &copy; `c` \ end`, the caption as written | 6 of 7. `marked` reads the nested link as text and keeps only `x`, and decodes `&copy;` to `©`. It forms no link and fetches nothing |
| the not-shown line renders as emphasis inside the entry | 7 of 7 |

**The escape is load-bearing.** The same entry with the caption written through unescaped,
`![a]b ![x](https://example.com/i.png) <b>bold</b> &copy; `c` end](01-t/….png)`, renders in 7 of 7 as one
`<img src="https://example.com/i.png">`. The picture from the tree is gone, and a remote image is fetched in
its place. GitHub routes that fetch through `camo.githubusercontent.com`.

## What was not measured

- Which renderers an operator really opens the tree in: VS Code, IntelliJ and Obsidian are still unmeasured as
  applications, as in every record before this one.
- Whether an application blocks a relative image path from rendering. VS Code's preview, for example, limits
  local resources to the workspace. The pictures sit inside the tree, below the page, so a workspace opened at
  the tree or above it contains them.
- A second conversion with a different `image_export_mode`. §5's explanation comes from the documentation.
- Any corpus other than GesPOS. The share of furniture, and the most pictures any survivor keeps, are this
  archive's numbers.
