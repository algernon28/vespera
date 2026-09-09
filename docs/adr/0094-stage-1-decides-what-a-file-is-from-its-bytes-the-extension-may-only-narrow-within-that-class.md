# ADR-094 — Stage 1 decides what a file is from its bytes; the extension may only narrow within that class

- **Date**: 2026-09-09
- **Status**: accepted
- **Amends**: [ADR-068](0068-broken-is-a-cross-format-floor-plus-per-format-structural-checks-no-new-dependency.md) — its per-format checks are unchanged, but which check a file gets is decided by content rather than by filename, and its format map gains an OLE compound class that carries no check beyond the floor

## Context

[ADR-068](0068-broken-is-a-cross-format-floor-plus-per-format-structural-checks-no-new-dependency.md) settled *which* structural check each format gets and *which* tool performs it. It did not settle how an occurrence is assigned to a format, and the implementation that followed answered that question by reading the filename. Read off `BrokenCheck.check` (`src/main/java/io/algernon/vespera/corpus/BrokenCheck.java:54-76`) rather than assumed:

```java
String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
if (name.endsWith(".docx")) { return checkDocx(file); }
if (name.endsWith(".pdf"))  { return checkPdf(file, size); }
if (isImage(name))          { return checkImage(file); }
return Result.ok();
```

Three facts about the code as it stands shaped this decision.

**Line 66 is the only place in `src/main` that reads a file extension.** Nothing else in the codebase dispatches on one, so there is exactly one site to change and no second name-driven path left behind. What replaces it is not "no extension anywhere" but a strictly bounded use of one, set out under *What the name may still do* below.

**The byte-level knowledge is already there; only the dispatch is name-driven.** `BrokenCheck` already holds the PNG, JPEG, GIF87a, GIF89a and BMP signatures, `%PDF-` and `%%EOF` (lines 27-37), and validates a `.docx` by opening its zip central directory. The checks are content tests already. They are simply gated on a string.

**The cross-format floor is a stat, not an open.** `Files.size` supplies the zero-length test from data census already recorded, so today's `broken` check opens nothing for a file whose extension it does not recognise.

### Why the name is not evidence

An extension is a claim made by whoever last renamed the file, and an archive assembled by people over years is full of files where that claim is wrong: a `.pdf` saved from a browser that is really HTML, a `photo.png` that is a PDF, a `.txt` that is a Word document, a file with no extension at all. Under name dispatch every one of those gets the wrong check or no check, and the failure is silent in the direction that matters — a truncated PDF named `photo.png` passes stage 1 as an unchecked file, and a well-formed PDF named `photo.png` is checked against the image signatures and verdicted `broken`. The second is over-blocking, which [ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md) names as the asymmetric failure: it loses archive, and it loses it invisibly, because a `broken` row looks exactly like a judgement about the file.

The alternative that was **not** taken, and is recorded here so it is not re-proposed: sniffing the content and then comparing it against the extension as a cross-check — using the name to detect a lie. That keeps the name as evidence about *what the file is*, and there is no useful thing to do when the two disagree, because one of them is the bytes and the other is a string. **The design keeps no notion of "the format a file claims to be."** There is no claim. There is content.

### What the name may still do: narrow within a class, never open one

Two of the classes the bytes establish are genuinely shared. The OLE compound container is one signature carrying legacy Word, Excel and PowerPoint documents *and* `Thumbs.db`; plain text is a class with no signature at all, carrying Markdown, HTML, CSV and prose alike. In both, the bytes have already answered as much as a signature can answer, and the remaining question is which member of a class the file is. The filename is the only cheap thing left that says anything about that.

**So the extension is admitted for exactly one job, under three limits, and this is the governing rule of this record:**

1. **It may only narrow within a class the bytes already fixed.** It is read after the class is decided, never before, and never as an input to deciding it.
2. **It may never contradict, re-open or override that class.** A `photo.png` beginning `%PDF-` is a PDF, and no name changes that. Where the bytes are unambiguous — PDF, the image signatures, a zip container — the name is not consulted at all, because there is nothing left for it to narrow.
3. **It may never influence the `broken` verdict.** Narrowing produces a *subtype label*, and no branch of the structural check reads it. The worst a wrong extension can do is hand a wrong subtype downstream. It can never lose an occurrence and never condemn one.

That is a different act from the cross-check rejected above, and the difference is which question the name is allowed to answer. The rejected design let the name argue with the bytes about what a file is. This one lets it speak only where the bytes are silent, and only about a distinction the bytes were never going to make.

**A subtype is therefore a weaker fact than a format, and the record keeps them apart rather than merging them into one value** — a format is what the bytes said, a subtype is what the name added. [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md) stores them in two columns for that reason, so that no reader of the mix report has to be told which half of a value to trust.

### What the name still costs downstream

`DoclingClient.convert` (`src/main/java/io/algernon/vespera/extraction/DoclingClient.java:157-170`) posts a `FileSystemResource` as the `files` multipart part, so the on-disk filename travels to `docling-serve`, which selects its own pipeline from it. [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md) records that this choice is material: the paginated PDF/image pipeline returns confidence scores, the simple `.docx`/`.txt` path returns nulls with `unspecified` grades, and tier 2's degeneracy floor reads exactly those. So a `photo.png` that is a PDF is converted today through the pipeline its name selects. That is a stage-2 defect, it follows from this decision, and it is deliberately **not** fixed here — see [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md), which records where the detected format is stored and names the follow-up that makes stage 2 read it.

## Decision

**Stage 1 decides what a file is by reading its leading bytes. The filename never decides a format, and is consulted only to narrow within a class the bytes have already fixed — under the three limits above.**

### One prefix read, 512 bytes

After the cross-format floor (unreadable, or `size_bytes == 0`, both unchanged from ADR-068), the check opens the file once and reads up to the first **512 bytes**. Every signature test below runs against that one buffer.

512 rather than the length of the longest signature, because the same buffer is also the sample the plain-text branch decodes. Rather than the whole file, because `broken` is the cheapest filter in the cascade ([ADR-017](0017-the-cascade.md)) and a prefix answers the question a signature asks.

### The rule, in order; first match wins

1. **`%PDF-` at offset 0 → PDF.** The structural check is ADR-068's, unchanged: the header, plus `%%EOF` within the last 32 bytes. A PDF whose trailer is missing is `broken`.
2. **A recognised image signature at offset 0 → image.** PNG (`89 50 4E 47 0D 0A 1A 0A`), JPEG (`FF D8 FF`), `GIF87a`, `GIF89a`, BMP (`BM`), TIFF (`49 49 2A 00` or `4D 4D 00 2A`), and WEBP (`RIFF` at 0 with `WEBP` at 8).
   **For an image the signature test *is* the structural check**, so dispatch and check are one step: a file reaching this branch has already passed the only test ADR-068 gives it, and an image is therefore never `broken` beyond the floor. Under name dispatch these were two questions — is it named like an image, and does it carry an image signature — and the second could fail. Under byte dispatch the second question has already been answered by the first.
3. **`PK 03 04` at offset 0 → a zip container**, opened with `java.util.zip.ZipFile` exactly as ADR-068 specified. A `ZipException` or `IOException` here is `broken`. A container that opens is then split by one central-directory lookup:
   - it holds an entry named `word/document.xml` → **a wordprocessing document**;
   - otherwise → **some other zip container**.
4. **`D0 CF 11 E0 A1 B1 1A E1` at offset 0 → an OLE compound file.** [MS-CFB] §2.2 fixes those eight bytes as the Compound File Header's signature, at offset 0, and makes them mandatory. The class holds legacy Word, Excel and PowerPoint documents ([MS-DOC] §2.1: "A Word Binary File is an OLE compound file as specified by [MS-CFB]"; [MS-XLS] §2.1 and [MS-PPT] say the same of their own formats), Outlook `.msg` items, and `Thumbs.db`. It is **narrowed by extension, not split by bytes** — see below. Like plain text it carries no structural check beyond the floor, so an OLE compound file is never `broken`.
5. **No signature matched, and the prefix decodes as text → plain text.** Defined as: it contains no `0x00` byte and decodes as UTF-8 with malformed and unmappable input reported rather than replaced, tolerating one incomplete multi-byte sequence cut off at the 512-byte boundary; or it opens with a UTF-16 or UTF-32 byte-order mark. Plain text has no container to validate, so ADR-068's rule holds: it is never `broken` beyond the floor. It is then narrowed — first by an HTML byte pattern, then by extension — see below.
6. **Otherwise → unrecognised.** The bytes match no signature this rule knows and do not decode as text.

### TIFF and WEBP are added to the image set, and that widening is the point

ADR-068 listed five image signatures. Under name dispatch a `.tif` matched no branch, was never checked, and passed. Under byte dispatch an unlisted signature does not fall through to "no check" — it falls into **unrecognised**, so the list's coverage becomes load-bearing in a way it was not before. TIFF and WEBP are formats `docling-serve` converts, so leaving them out would put real documents in the branch that exists for things that are not documents. The list is widened once, here, with that reasoning attached, so that the next widening is understood as the same kind of act rather than an oversight being patched.

**`BM` is two bytes and is a weak signature**, and the section below sets out what that costs and where it becomes consequential.

### Telling a wordprocessing document from any other zip is a lookup of an entry inside it, not a parse

*(The name looked up here is a zip entry's, inside the container. The file's own name on disk plays no part in this branch at all.)*

`PK 03 04` is shared by `.docx`, `.xlsx`, `.pptx`, `.odt`, `.jar` and a plain zip, so the container has to be looked into. The test is the presence of a central-directory entry named `word/document.xml` — the part ECMA-376 fixes for a WordprocessingML package — which `ZipFile` answers from the directory it has already read, with nothing decompressed.

The alternative was to read `[Content_Types].xml` and take the declared part types, which is more general and covers a package that names its main part unusually. It is rejected: it means inflating an entry and parsing XML, which is real parse work and the exact cost ADR-068 refused when it turned down Apache POI. The container test stays at the container level, and what is inside the part remains stage 2's `extraction-failed` (ADR-068's accepted gap, unchanged).

An **empty zip archive** begins `PK 05 06`, not `PK 03 04`, so it misses this branch, carries `0x00` bytes, and lands in unrecognised. No special case is written for it; that is already the right answer.

### The zip branch is not narrowed by the filename, and never needs to be

Recorded so that the narrowing admitted below is not read as a general licence and re-proposed here. **Every distinction this branch could want is already available from the central directory it has read**, at the same cost as the `word/document.xml` lookup: `xl/workbook.xml` for a spreadsheet, `ppt/presentation.xml` for a presentation, and ODF's `mimetype` entry, which OpenDocument 1.2 part 3 requires to be the package's first file, uncompressed, holding the ASCII media type — a requirement the specification says exists precisely so the document type can be discovered by magic-number mechanisms. Where a byte-level answer of the same cost exists, limit 2 of the governing rule applies and the extension is not consulted.

**That further split is not made today**, and this is the reason rather than an oversight: nothing yet consumes it. `ZIP_CONTAINER` blocks nothing, and stage 2 routes every one of these formats down Docling's simple pipeline, so telling them apart changes no behaviour that exists. The first consumer will be the stage-2 follow-up ADR-095 names, which has to hand Docling a format rather than a path; when it is taken, extending the split is a directory lookup and the new values are *formats*, byte-derived, not subtypes. This is the same kind of widening ADR-068's image list took when TIFF and WEBP were added, and it is understood in advance as such.

### The OLE compound class is narrowed by its extension, and the CFBF parse is priced before it is turned down

One signature, and both real documents and junk behind it. A `.doc`, a `.xls`, a `.ppt`, a `.msg` and a `Thumbs.db` are all Compound File Binary Format containers and all begin with the same eight bytes ([MS-CFB] §2.2; the Windows thumbnail cache stores its thumbnails in an OLE compound file, in both the XP-era layout and the variant Windows 7 and later write onto network shares). This is the one class where "what the bytes say" and "whether this is a document at all" genuinely come apart.

**The bytes can split it, and here is what that costs.** [MS-DOC] §2.1 requires a stream named `WordDocument` in the container; [MS-XLS] requires a `Workbook` stream, [MS-PPT] a `PowerPoint Document` stream. Finding one means reading the CFBF header for the sector size and the first directory sector, following the FAT chain — reachable from the header's own 436-byte DIFAT array only while the file is small enough that 109 FAT sectors cover it, and through DIFAT sectors beyond that — then walking the directory sectors' fixed 128-byte entries and decoding their UTF-16 names. A version-3 sector holds only four of those entries, so a document with a dozen streams costs several sector reads rather than one — still a handful of seeks and small reads, and **the I/O is therefore not the objection; it would be dishonest to pretend it is**. One shortcut that would make it cheap is worth naming as unavailable: scanning the 512-byte prefix for the UTF-16 bytes of `WordDocument` is not sound, because the directory's location is given by a header field and is nowhere near the start of a file of any size.

**The objection is that we would be writing and owning the parser.** The zip lookup is one call against `java.util.zip.ZipFile`, a class the JDK ships and maintains, and the whole malformed-input surface is already someone else's. A CFBF directory walk is a miniature file-system traversal we would implement ourselves — sector arithmetic, chain following, cycle and bounds defences, name decoding — inside stage 1, the cheapest and least forgiving filter in the cascade. The library that does this properly is Apache POI, and [ADR-068](0068-broken-is-a-cross-format-floor-plus-per-format-structural-checks-no-new-dependency.md) rejected POI by name, on the ground that stage 1 should not do real parse work. Hand-rolling POI's compound-document layer to avoid depending on POI is that rejection honoured in the letter and broken in the substance.

**So the OLE class is narrowed by extension**, under the governing rule: `.doc` → legacy wordprocessing, `.xls` → legacy spreadsheet, `.ppt` → legacy presentation. Anything else — `Thumbs.db`, `.msg`, a stray `.db`, no extension at all — is an OLE compound file with **no subtype**. The class itself is never in doubt, because it came from the signature; only the subtype is a name's word, and nothing blocks on a subtype.

**What that buys, and it is the point of the amendment**: the mix report can now tell an operator "this much of your archive is legacy Word" separately from "this much is OLE-shaped material we cannot name", where before both were one undifferentiated count in the unrecognised branch. That is precisely the number [issue #87](https://github.com/algernon28/vespera/issues/87) needs and could not previously get.

**What it costs, stated plainly**: a legacy `.doc` renamed `.dat` is recorded as an unnamed OLE compound file. It is not lost, not `broken`, and still reaches stage 2 — it simply appears in the report under the wrong heading, and its stage-2 pipeline choice is no better than the one its name already gives it today. An unnamed OLE compound file is also the natural shape of a future floor's first candidate; that is a later decision and ADR-095 records why it is not taken now.

**No subtype is minted for `.msg`.** Grouping it with `Thumbs.db` is not a claim that they are alike; it is that stage 2 has no distinct path for either, so a subtype would steer nothing and would assert a confidence the pipeline does not act on.

**One thing the subtype does not settle, and stage 2 will have to.** Docling's supported-formats table lists the legacy binary Office formats and marks them as requiring LibreOffice, where DOCX, HTML, Markdown, CSV and the image formats carry no such requirement. So a legacy `.doc` is unambiguously a real document, and unambiguously the one class in this rule whose conversion depends on how the sidecar is provisioned. That is a stage-2 and ADR-011 question about the container image, not a stage-1 detection question, and it is named here only so that a large legacy count in the mix report is read for what it is.

### Plain text is narrowed by one byte pattern, then by extension

Text has no signature by construction. `.md`, `.html`, `.csv`, `.json`, `.xml` and `.txt` decode identically, and the bytes are exhausted the moment they say "text". But the distinction matters downstream: Docling parses HTML and Markdown structurally and reads CSV as tabular data, where undifferentiated text arrives as a flat string. Sniffing harder means a content heuristic over prose, which is exactly the thing this record replaced.

**HTML is narrowed by bytes first, because a partial signature genuinely exists.** WHATWG's MIME Sniffing standard gives a pattern table for `text/html`; **two of its entries are taken and the rest are deliberately not** — `<!DOCTYPE HTML` and `<HTML`, matched ASCII-case-insensitively after leading whitespace and followed by one of the standard's tag-terminating bytes (`0x20` or `0x3E`). The other fifteen (`<A`, `<B`, `<P`, `<TABLE`, `<!--` and so on) are far too loose for a corpus of prose and Markdown, where a document may legitimately open with an inline tag or an HTML comment. Two anchored, unambiguous patterns are a signature; seventeen loose ones are a heuristic, and the line between them is the whole of this record.

**Where that pattern fires, the name is not consulted** — limit 2 again, and a `.txt` that opens `<!DOCTYPE html>` is HTML. Where it does not, the extension narrows:

- `.html`, `.htm`, `.xhtml` → HTML
- `.md`, `.markdown` → Markdown
- `.csv` → CSV
- `.adoc`, `.asciidoc` → AsciiDoc

**Everything else — an unknown extension, or no extension at all — is plain text with no subtype**, and travels to stage 2 as text. That is the same treatment a `.txt` gets, it removes nothing, and it is the honest answer: the file decoded as text and nothing further is known about it.

**`.json` and `.xml` are deliberately given no subtype**, though they are on the obvious list. Docling's JSON support is its own serialisation format rather than arbitrary JSON, and its XML support is schema-specific (patent and journal-article grammars), so a subtype for either would assert a structural claim stage 2 cannot act on and would be a worse label than none.

### `BM` is two bytes, and the stage-2 follow-up is where that starts to bite

Any file beginning with those two characters reads as an image. It was already in ADR-068's set and it stays, and for stage 1 the consequence is bounded exactly as before: an image is never `broken`, so a false reading costs no occurrence. **What changes is downstream.** Once the stage-2 ticket ADR-095 names makes Docling's pipeline choice read the detected format, a false `BM` reading stops being a label and starts steering conversion — a text file opening "BM" would be sent to the paginated image pipeline, produce nothing usable, and arrive at ADR-070's degeneracy floor as `degenerate-output`, which blocks. That is a change of consequence rather than of likelihood, and it belongs to that ticket; ADR-095 records the obligation where the ticket's other obligations are listed.

Hardening the signature by checking the BMP header's little-endian file-size field at offset 2 against the size census already holds was considered and **not** decided here: that field is set to zero or to the pixel-data size by enough real writers that requiring it would misclassify genuine BMPs, trading an unlikely false positive for a likely false negative. It is named so the stage-2 ticket can weigh it with the risk in front of it rather than rediscover the option.

### The cost, stated honestly

Where today an unrecognised extension costs one `stat`, it now costs one `stat`, one `open` and one 512-byte read. That is a real new cost and it falls on every occurrence stage 1 examines, not only on the ones that were being checked before.

Two things bound it. Anything surviving `broken` and sharing its size with another survivor is fully read moments later by [ADR-067](0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md)'s hashing, so for much of the archive the open lands on a file that was about to be opened anyway. And the fixed cost of an open plus a small sequential read is sub-millisecond against the stage-2 HTTP conversion it stands in front of, which ADR-068 already established is measured in seconds. The ordering principle survives: this is still, by a wide margin, the cheapest filter in the cascade.

### Two accepted blind spots

**A file that is text for 512 bytes and binary afterwards reads as plain text.** Accepted: the prefix is the whole point of the cheapness, and a file whose text runs out mid-way arrives at stage 2's degeneracy floor with whatever it produced (ADR-070).

**BOM-less UTF-16 reads as unrecognised.** Its NUL bytes fail the text test and it carries no signature. Detecting it would need a heuristic over byte-frequency, and a heuristic is precisely what this decision replaced. Recorded rather than solved.

## Consequences

**`BrokenCheck` opens every file it checks.** The method keeps its shape — one call, one `Result` — but it now performs I/O for every occurrence rather than for three extensions, and it returns what it found alongside whether the file is broken: a **detected format**, always from the bytes, and an optional **subtype**, present only for the two classes narrowing applies to. Where those are stored is [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md)'s.

**Two closed enumerations, not one, and the smaller one is the weaker claim.** The formats are `PDF`, `IMAGE`, `WORDPROCESSING`, `ZIP_CONTAINER`, `OLE_COMPOUND`, `PLAIN_TEXT` and `UNRECOGNISED` (ADR-095 adds one more for the floor case). The subtypes are `LEGACY_WORD`, `LEGACY_SPREADSHEET`, `LEGACY_PRESENTATION`, `HTML`, `MARKDOWN`, `CSV` and `ASCIIDOC`, and absent is a legitimate answer for every occurrence. The legacy subtypes are deliberately prefixed so that the byte-derived spreadsheet and presentation *formats* the zip branch may later gain cannot be confused with them.

**Exactly one extension read survives in `src/main`, and no verdict can reach it.** It sits after the format is decided, on two branches only, and its output feeds no branch of the structural check. A reviewer checking that the governing rule is honoured has one place to look, which is the same property that made line 66 removable in the first place.

**Occurrences change verdict in both directions, and both are correct.** A well-formed PDF named `.png` stops being `broken`. A truncated PDF named `.png` starts being `broken`. Neither is a regression; the old answers were the ones derived from a string.

**Two recorded tests contradict this decision and must be rewritten by the change that implements it**, rather than deleted. `BrokenCheckTest.imageWithUnrecognisedSignatureIsBroken` writes the text `this is not an image at all` into `not-really.png` and claims it is `broken`; under this rule those bytes are plain text and pass. `BrokenCheckTest.pdfMissingHeaderIsBroken` does the same with `not a pdf at all, just some bytes\n%%EOF`. `ByteLevelReductionTaskletTest.verdictsOnlyTheBrokenSurvivor` writes `not a pdf at all` into `broken.pdf` for the same reason. Each needs a fixture that is genuinely the corruption it claims to be — a real PDF header with the trailer cut off, a real PNG signature followed by nothing — which is the style `BrokenCheckTest` already uses for the `.docx` cases.

**ADR-068 is not edited.** Its floor, its per-format checks, its tool choices, its rejection of Apache POI and its accepted valid-container/corrupt-content gap all stand exactly as written. What this record replaces is the sentence-level assumption that a format is known from a name — an assumption ADR-068 never stated, because it never asked the question.

**The pom is untouched.** `ZipFile`, `CharsetDecoder` and byte comparison are JDK-standard, so [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) stays clean for this change as it did for ADR-068.

**Nothing here specifies call sites.** The buffer's type, whether the zip lookup reuses the handle the container check already opened, and the exact decoder configuration are the hand-off spec's, the same deferral ADR-068 and ADR-070 made.
