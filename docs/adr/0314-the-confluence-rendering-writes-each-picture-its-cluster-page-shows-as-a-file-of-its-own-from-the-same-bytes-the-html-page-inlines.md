# ADR-314 — The Confluence rendering writes each picture its cluster's page shows as a file of its own, from the same bytes the HTML page inlines

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Amends**: [ADR-286](0286-a-clusters-page-is-one-file-written-an-entry-at-a-time-through-its-part-with-its-pictures-and-a-png-or-jpeg-image-inlined.md) §1 and §4, for the Confluence rendering alone: under `confluence/` a picture is a file of its own and the page names it, where the HTML tree's page carries it as a `data:` URI and writes no picture file (§1, §2). The HTML tree is unchanged.
- **Rests on**: [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md), where the rendering lies, and [ADR-313](0313-a-clusters-confluence-page-carries-its-copies-as-files-and-a-copy-the-site-refuses-is-counted-and-listed-by-the-publish.md), by which a cluster's page carries its copies as files.
- **Keeps**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) and [ADR-150](0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md): the furniture rule, the budget of ten and what is shown, decided once for both renderings (§1).
- **Decided**: under the operator's delegation of 2026-10-21.
- **Settles**: [#615](https://github.com/algernon28/vespera/issues/615).

## Context

A cluster's HTML page shows each picture as a `data:` URI on its `<img>`, PNG or JPEG only and only for bytes that carry the signature they declare, and writes no picture file (ADR-286 §1, §4). Confluence renders no `data:` URI: a picture on a page is `ac:image` over `ri:attachment`, a file on that page. `ListedPicture.pixels` holds the bytes Docling's response carried, which `EntryPictures` encodes into the HTML page. A picture is identified by the SHA-256 of those bytes (ADR-286 §1). A measured Cloud site kept `~`, non-ASCII and spaces in file names (`docs/research/confluence-loading.md` §5).

## Decision

### 1. Which pictures

The Confluence rendering shows on a cluster's page exactly the pictures the HTML page of the same cluster shows, in the same entries and order: the same furniture rule, the same budget of ten, the same signature check of ADR-286 §1. A picture the HTML page does not show is not written.

### 2. Each is a file of its own, from the same bytes

Each picture shown is written under the cluster's folder in `confluence/` as **the picture's file**, its bytes the decoded bytes the HTML page encodes as base64, unchanged. The page shows it as `<ac:image><ri:attachment ri:filename="…"/></ac:image>`, with the alt text the HTML page gives it (ADR-149 §5). A picture's file is written through `<name>.part` and moved into place, as a page is (ADR-286 §3).

**An image survivor's copy shown inline** (ADR-286 §6) is not written again: its entry shows the copy itself, which is already a file on the page (ADR-313 §1), through `ri:attachment` naming the copy.

### 3. The name

A picture's file is named `picture-<the first 16 hexadecimal characters of its SHA-256>.png`, or `.jpg` for JPEG bytes. Two pictures with the same bytes are one picture, and the furniture rule already keeps a picture that recurs off every page. Where a copy on the same page bears a name that collides with a picture's by ADR-280 §1.4's keys, **the copy keeps its name and the picture's takes `~` and its entry's number before the extension**. The name is at most 31 characters, so it adds nothing to ADR-312 §3's budget.

### 4. In the page list

Every picture's file is a file of its page in the page list, with its size and digest (ADR-316), and is sent by a publish as a copy is. A picture's file the site refuses as too large is reported as ADR-313 §3 reports a copy, in the same count and the same `confluence-not-carried.csv`.

### 5. What is stored twice

The picture's bytes lie twice on disk, inline in the HTML page and as the picture's file. This is accepted: the two renderings serve two readers, and a picture's file is no larger than the pixels Docling sent.

### 6. Run ids

The rule lives in `deliverable`, and no value is added; no run id moves.

## Alternatives refused

- **Pictures as `data:` URIs in the storage body**: Confluence does not render them.
- **The picture's file shared between the two renderings**: ADR-286 §1 keeps the HTML page self-contained, and a picture file beside it would undo that.
- **A name from the document's own name or the caption**: a caption is a model's or Docling's text, and the digest is the picture's identity already.

## Tests

Built by [#620](https://github.com/algernon28/vespera/issues/620).

| Method | What it holds |
| --- | --- |
| `…theConfluencePageShowsThePicturesTheHtmlPageShows` | the same pictures, entries and order on both renderings of one cluster (§1) |
| `…aPicturesFileHoldsTheBytesTheHtmlPageInlines` | the file's bytes equal the base64-decoded `data:` URI of the HTML page (§2) |
| `…aPictureCollidingWithACopyTakesItsEntrysNumber` | a copy named as a picture keeps its name, the picture takes `~<n>` (§3) |
| `…anImageSurvivorsCopyIsShownAndNotWrittenAgain` | an image survivor's entry shows its copy by `ri:attachment` and no picture's file is written for it (§2) |
