# ADR-167 — BMP images are out of scope, and stage 1 recognises one by its file header and the header after it

- **Date**: 2026-09-28
- **Status**: accepted
- **Extends**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md). A second kind of file joins `pipeline`'s `OutOfScope`, under the same `out-of-scope` verdict and the same place in stage 1's first pass. The vocabulary stays at nine values.
- **Builds on**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) and [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md). A BMP image is recognised from its bytes, as a new `DetectedFormat`, and the name plays no part.
- **Keeps**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md). `extraction` names `corpus`'s detection enumerations, so the new value needs a row in its table. It gets the row `IMAGE` already has, the neutral name, so no posted name changes and the naming scheme stays at version 1. ADR-100's *"The signature stays as it is"* is kept too: `IMAGE` is still recognised by `BM` alone.
- **Engages**: ADR-094's *"`BM` is two bytes"* and ADR-095's third obligation, which handed the question of hardening `BM` to the stage-2 ticket, where ADR-100 declined it for good because the file-size field at offset 2 is unreliable. This record leaves `IMAGE`'s signature as it is. It reads a different field, the picture header's length at offset 14, and only to single out `BMP` from `IMAGE` (Decision).
- **Settled**: points 2 to 5 of #326 are now settled by ADRs 170, 171, 172 and 173. A file that crashes the converter every time it is sent becomes `extraction-failed` with a reason of its own (ADR-171 §3).

## Context

On 2026-09-28 the whole-archive run (43,101 files, walk 5) failed in stage 2 at 09:56. Docker recorded `oom` and then `die exit=137` for the Docling sidecar. The call in flight got `HTTP/1.1 header parser received no bytes`, and stage 2 failed with 1,328 read, 631 written and 683 filtered. The restart policy of ADR-164 had the sidecar back in about 6 seconds. Under ADR-164 §2 the step still fails, and the next invocation sends the same file again, so the run cannot get past it.

The main session reproduced it one file at a time, on a freshly restarted sidecar (`vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`, `POST /v1/convert/file`, `to_formats=json`, `image_export_mode=embedded`):

- `…/HOST/LOGHIBPE/cir.bmp` (occurrence 4358) ran 64.7 s, and the sidecar died.
- `…/HOST/LOGHIBPE/sodexo.bmp` (4364) did the same in 64.8 s.
- Three `.M70`, `.M71` and `.MXX` files sent before them converted in 2 s each. JPEG screenshots had converted without trouble on the GesPOS run.

Both files are ordinary Windows bitmaps: `BM`, a 40-byte `BITMAPINFOHEADER`, 95 × 103 pixels, 24 bits per pixel, uncompressed, 29,718 bytes.

### What kills the sidecar is the resolution the file declares, not BMP

Measured for this record on 2026-09-28, against the same sidecar, with copies of `cir.bmp` in a scratch directory. The archive was only read.

`cir.bmp` declares 1 pixel per metre on both axes, which is 0.0254 dots per inch. At that resolution its 95 pixels are about 3,740 inches wide.

| Sent | Result |
| --- | --- |
| The same pixels as a PNG that declares no resolution | converted, 2.0 s |
| `cir.bmp` with only its two resolution fields changed to 3,780 pixels per metre (96 dpi) | converted, 2.0 s, `success`, one page of 71 × 77 points |
| The same pixels as a PNG that declares 0.0254 dpi | no answer after 65.3 s; `OOMKilled` true, restart count 3 to 4, `/health` answering again about 9 s later |

**So BMP decoding is not the cause.** The same BMP converts when its resolution is ordinary, and a PNG crashes the sidecar when it carries the BMP's resolution. What crashes the sidecar is an image declaring a resolution so low that its page is thousands of inches wide.

### Why BMP is still the rule for now

Across walk 5, stage 1 found 12,001 occurrences to be images. Read with Pillow 12.3.0:

| Kind | Occurrences | Declaring under 50 dpi | Of those, surviving stage 1 |
| --- | --- | --- | --- |
| BMP | 410 | 90 at 0.0254 dpi, and 187 declaring 0 | 5 at 0.0254 dpi, 7 declaring 0 |
| PNG | 11,428 | none | none |
| JPEG | 101 | none | none |
| GIF | 19 | none | none |
| WEBP | 43 | none | none |

**On this archive every image that declares a crashing resolution is a BMP.** So leaving BMP images out gets the run past stage 2, and no PNG, JPEG, GIF or WEBP is touched. It does not prevent the same crash from any other image, on another archive.

What it costs here: 34 BMP images survive stage 1 today.

- 5 declare 0.0254 dpi and would crash the sidecar.
- 7 declare a resolution of 0. The one of them already sent, `logo_verifone.bmp` (occurrence 4024), came back `failure` (`backend_failure`) in 5 ms.
- 22 declare an ordinary resolution, and would presumably convert, as `cir.bmp` did at 96 dpi.

All 34 are logos for payment terminals, by their paths (`HOST/LOGHI*`, `…LOGO…`, `Ergonomia/Edenred.bmp` and similar). A logo's pixels were never going to be much of a document.

### The 224 `.bmp` files stage 1 finds unrecognised are not BMP

Of the 634 `.bmp` paths in walk 5, 410 open with `BM` and a 40-byte header, and stage 1 records them as `IMAGE`. The other 224 do not open with `BM` at all. They open with `00 78 00 00 …` (148), `24 1A 80 C0 …` (62) or `00 78 E0 F0 …` (14), are 185 to 620 bytes long, and sit in payment-terminal build folders (`CB2_TELIUM2`, `bin.PAX`). They are headerless bitmaps in a terminal maker's own format, not Windows BMP. Stage 1 is right to call them unrecognised, and this record leaves them there.

Four of them survive stage 1. One was sent to the sidecar for this record, `…/SMARTPOS/HOST/ELUNCH.BMP`: it came back `skipped` in 1 ms, with the error *"File format not allowed"* in category `policy`. Under ADR-139 that becomes `extraction-failed`. So they cost the run nothing.

No file in walk 5 that is not named `.bmp` opens with `BM`.

## Decision

**A BMP image is out of scope, whatever it holds.** Stage 1 removes it with the `out-of-scope` verdict of ADR-146, in the same first pass, before anything hashes or converts it. Its reason reads *"a BMP image, and BMP images are out of scope"*.

**Stage 1 recognises a BMP image as a new `DetectedFormat`, `BMP`.** It is a new format, not a subtype of `IMAGE`, because the bytes decide it. Under ADR-094 and ADR-095 a subtype is what a name adds inside a class the bytes have fixed, and only in two classes. ADR-146 made the same choice when it gave `SPREADSHEET` its own value rather than narrowing `ZIP_CONTAINER`.

**A file is a BMP image when both of these hold:**

- it opens with the two bytes `BM`;
- the four bytes at offset 14, read as a little-endian number, are the length of one of the headers a BMP carries after its file header: 12, 16, 40, 52, 56, 64, 108 or 124.

Both are in the 512 bytes stage 1 already reads, so this costs no second read. `BM` alone is two bytes and a weak signature, as ADR-095 said. With the header length it is a signature. A file that opens with `BM` and has none of those lengths stays `IMAGE`, as today, and stays in scope. So does one shorter than 18 bytes, too short to hold the length. Every other image stays `IMAGE` too.

**The `IMAGE` signature stays `BM` alone, as ADR-100 decided.** ADR-094 and ADR-100 declined to harden it with the file-size field at offset 2, because enough real writers set that field to zero or to the pixel-data size. The header length at offset 14 is a different kind of field: a reader has to have it to parse the file at all, since it says where the header ends. No common reader parses a BMP without it, and a BMP missed here only stays `IMAGE`, in scope and sent exactly as before, so requiring it costs nothing the file-size field would have cost. On walk 5 all 410 files opening with `BM` carry 40 there.

**ADR-150 §4's rule that an image survivor shows no pictures covers `BMP` too**, so a BMP image put back in scope later would carry no cropped copies of itself into its group's page. No `BMP` survivor can exist while this record stands, so no test reaches that branch.

**It is sent to Docling exactly as before.** `BMP` maps to the neutral name `IMAGE` maps to. The naming scheme stays at version 1, and the extractor identity does not change. That matters for a seed, which stage 1 never sees, and for a later decision to put BMP images back.

**The seed side is unchanged.** A BMP image in the seed folder is converted as before. One that declares 0.0254 dpi will still crash the sidecar in seed extraction. That is #326's to fix.

**The format-mix page names them.** BMP images get a row of their own, *"BMP images"*, and its sentence about what is out of scope names them beside spreadsheets. They are counted in *"Files left out as out of scope, and not read any further"*.

## Alternatives rejected

- **A subtype `BMP` on `IMAGE`.** It would keep every `IMAGE` branch reaching BMP images with no edit, and `extraction` would still change, because its two switches over `DetectedSubtype` would need the new constant. It would also break ADR-095's rule that a subtype is the name's word. `HTML` is the one subtype the bytes can give, and it narrows plain text, a class the name may already narrow. `IMAGE` is not one of those classes.
- **Leaving out images that declare under some resolution, of any kind.** It aims at the cause and would catch a PNG like the one measured above. But it needs stage 1 to read a PNG's `pHYs` chunk, a JPEG's JFIF and EXIF density, and a TIFF's tags. That is the parse work ADR-068 keeps out of stage 1. It also needs a floor, and no measurement gives one: nothing between 0.0254 and 50 dpi exists on this archive. And it would still not stop the next file that crashes the converter for a reason nobody has seen yet. #326 would.
- **Recognising a BMP by its extension.** 224 of the 634 `.bmp` files are not BMP, and ADR-094 does not let a name decide a format.
- **Calling it `broken`.** A BMP image that declares 0.0254 dpi is not damaged: it opens, and its pixels are sound. And the rule removes BMP images that declare an ordinary resolution too.

## Consequences

**What re-runs over an existing working directory:**

- **Stage 1 mints a new run.** Its implementation version is `corpus`'s (ADR-058), and `DetectedFormat` and `BrokenCheck` both change. The new run writes `out-of-scope` for the 410 BMP images in walk 5. Its duplicate pass now sees none of them, and the 224 unrecognised `.bmp` files go through it as before.
- **Stage 2 mints a new run**, because `extraction`'s table gains a row and because its upstream run is new. **What was already converted is not converted again.** The extraction cache is keyed on the content hash and the extractor identity, and the identity is the image, the sidecar's reported versions and the options sent, `naming=1` among them. None of those changes. On 2026-09-28 the working directory held 1,331 cached conversions, all under the current identity, and each of them is read back rather than sent.
- **Every later stage mints a new run**, because its upstream run is new, and because `pipeline` and `extraction` change, which their implementation versions span. Conversions, chunks (`chunk_cache`) and vectors (`vector`) are keyed on content, so they are reused, and so are the operator's relevance labels, keyed on path and seed set (ADR-097). Everything else is keyed on the run and is recomputed: shingles, MinHash signatures and bands, redundancy, relevance scores, clusters, and 6b's generated pages and the model calls that write them. On walk 5 no stage after stage 2 has run yet, so there this costs nothing.

**The removal is visible.** The verdict, its reason and the format-mix page all say so. A BMP image an operator wanted is removed with the rest. What brings one back is the configurable list ADR-146 deferred, or this record being reversed once #326 has landed.

**`OutOfScope` is in `pipeline`, and stage 1's implementation version is `corpus`'s alone.** This change re-mints stage 1 because `corpus` changes too, as ADR-146's did. An edit to `OutOfScope` alone would not re-mint stage 1. That is not decided here.

**`CONTEXT.md`'s entry for *Out of scope* names BMP images beside spreadsheets.**

## Tests

Red until the change lands. They name the new value by its string, so they compile before it exists:

- `DetectedFormatTest.aBmpImageIsRecognisedByItsTwoHeaders`: a 70-byte, 2 × 2, 24-bit BMP named `.png`, and a BMP with a 124-byte header, are `BMP`, not broken, with no subtype. A PNG and a JPEG named `.bmp` are `IMAGE`. A file that opens with `BM` and has no header length after it is `IMAGE`.
- `DetectedFormatTest.aBmpTooShortToCarryItsHeaderLengthIsAnOrdinaryImage`: a 10-byte file opening with `BM` is `IMAGE`, and reading it throws nothing. An 18-byte file with 40 at offset 14 is `BMP`.
- `ByteLevelReductionTaskletTest.removesBmpImagesAsOutOfScope`: in stage 1, a BMP image is recorded as `BMP` and removed as `OUT_OF_SCOPE`, with the reason exactly *"a BMP image, and BMP images are out of scope"*. A JPEG and a PNG carry no verdict. The page counts one file left out as out of scope, and one BMP image.
- `DoclingClientTest.postsABmpImageUnderTheNameAnImageIsPosted`: `BMP` is posted as `document.bin`, the name `IMAGE` is posted as, and the options the identity is built from still say `naming=1`.
