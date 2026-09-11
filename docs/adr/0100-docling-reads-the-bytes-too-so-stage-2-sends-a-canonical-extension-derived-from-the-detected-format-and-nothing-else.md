# ADR-100 — Docling reads the bytes too, so stage 2 sends a canonical extension derived from the detected format and nothing else

- **Date**: 2026-09-11
- **Status**: accepted
- **Amends**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) — its two named stage-2 obligations are both resolved against what it anticipated: the `BM` risk it deferred does not exist, and the zip split it left for this ticket is not needed. Its rule for stage 1 is untouched.
- **Amends**: [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) — the extractor identity gains the naming scheme, because the filename now changes what a conversion returns and is chosen by us rather than observed from the corpus.

## Context

[ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md) stored the detected format and named this follow-up: make stage 2 send Docling the format the bytes say, not the one the path claims. It listed four obligations — derive the multipart part name from `detected_format`, use the subtype where present, weigh the weak `BM` signature before the format starts steering conversion, and decide whether the zip branch needs splitting further.

All four were written from the same premise, stated in ADR-094's *What the name still costs downstream* and repeated in ADR-095: that `DoclingClient.convert` posts a `FileSystemResource` whose on-disk filename travels to `docling-serve`, "which selects its own pipeline from it." **That premise is wrong, and this record exists mainly because finding out what is actually true changes three of the four answers.**

### What `docling-serve` v1.32.0 actually does with an upload

Read off the pinned sources — `docling-serve` v1.32.0, which pins `docling` v2.124.0 and `filetype` 1.2.0 — and confirmed by executing `_guess_format` and its helpers against the real `filetype` wheel rather than by reading alone.

**The handler keeps the filename and discards everything else.** `docling_serve/app.py:564-579` builds `DocumentStream(name=file.filename, stream=buf)`. The part's `Content-Type` appears exactly once in the whole input path, inside a log line. `DocumentStream` itself has two fields, `name` and `stream`, so there is nowhere for a media type to go.

**The format is decided from the bytes first.** `docling/datamodel/document.py:809-819` reads the first 8192 bytes and calls `filetype.guess_mime(content)`. The extension is consulted **only when that returns `None`**. So a well-formed PDF named `photo.png` is recognised as a PDF and reaches the paginated pipeline **today**, with confidence scores, exactly as ADR-070 wants — the defect ADR-094 recorded as following from its decision does not exist.

**There is no way to tell Docling what a file is.** `from_formats` is declared on the file endpoint and documented at `docling-serve/docs/usage.md:14`, but nothing in the v1.32.0 serving path reads it: the converter is built at `docling_jobkit/convert/manager.py:520-570` with no `allowed_formats=` argument, and `convert_documents` never passes it. Semantically it was an allow-list rather than an override in any case; in this version it is a no-op. No header, form field or per-part media type substitutes for it.

**So the filename is the only lever there is, and it is consulted in exactly two places.**

1. **Text-shaped content**, where `filetype` has no matcher and returns `None`. This is the load-bearing case, and it is sharper than "the name helps here":
   - `MimeTypeToFormat["text/plain"]` holds two entries, so `_guess_from_content` decides, and its Markdown branch fires **only** when the extension is one of `md`, `txt`, `text`, `qmd`, `rmd`, `Rmd` (`document.py:956-971`). Anything else — `.log`, `.dat`, **or no extension at all** — returns `None`, lands on `_DummyBackend` (`document.py:686-690`) and comes back `ConversionStatus.FAILURE`. **An extension-less text upload does not convert.**
   - AsciiDoc is reachable *only* by extension: `filetype` has no matcher, and `_mime_from_extension` maps `adoc`/`asciidoc`/`asc` (`document.py:979-980`).
   - HTML is caught by `_detect_html_xhtml` only when the document starts with `<!doctype html`, `<html`, `<head` or `<body>` after comment-stripping (`document.py:1044-1058`); a fragment opening `<div>` is not, and needs the extension.
   - `_detect_csv` needs only a newline and a sniffable delimiter (`document.py:1088-1089`), so **ordinary prose containing commas converts as a spreadsheet**. This fires for extension-less text and equally for `.txt`, because `.txt` deliberately leaves the mime unset — USPTO text files share the extension with Markdown (`document.py:983-986`) — and so falls through into the HTML and CSV sniffs. `.md` resolves to `text/markdown` directly (`document.py:987-988`) and skips them.
2. **Oversized zip containers.** `filetype` normally identifies DOCX, XLSX, PPTX and ODT outright, and a lying extension is ignored. But `OfficeOpenXml.match_document` scans only the first four local file headers within 6000 bytes, so a container whose identifying entry falls outside that window comes back `application/zip` — and in that branch **the name is consulted before the content probe** (`document.py:820-836`), which is how a DOCX named `.xlsx` becomes an XLSX. Where the name matches no OOXML suffix, `_detect_office_mime_from_zip` (`document.py:867-895`) keys on `word/document.xml`, `xl/workbook.xml`, `ppt/presentation.xml` and the ODF `mimetype` member, and gets it right.

**Everything else is decided on bytes alone**, and the name can neither help nor hurt: PDF, every image signature, and the OLE compound formats, which `filetype` splits into `DOC`/`XLS`/`PPT` by a per-type sector probe on top of the CFB header.

### LibreOffice is not in the image, and legacy Office therefore cannot convert

ADR-094 left this to be established rather than assumed, and it is established. The CPU image is built from the repository's single `Containerfile`, whose only OS-package layer installs the eight packages in `os-packages.txt` — four tesseract packages, `leptonica-devel`, `libglvnd-glx`, `glib2`, `libatomic`. `grep -rni "libreoffice\|soffice"` over the whole `docling-serve` v1.32.0 tree returns nothing.

Docling's own supported-formats table marks `DOC, XLS, PPT` as "Legacy binary Office formats (97–2004); requires LibreOffice", and the code makes that concrete: the backends pre-convert through `convert_to_modern_format` (`docling/backend/docx/drawingml/utils.py:82-118`), which resolves `libreoffice` or `soffice` on `PATH` and raises `RuntimeError` when neither is there. So a `.doc` in the archive is a real document, correctly identified from its bytes, that this sidecar cannot convert. No setting turns it on; only a derived image would.

## Decision

**Stage 2 tells Docling what a file is by the only means Docling offers — the filename — and it derives that name from the detected format and its subtype, never from the path. The name it sends is a transport detail, not a claim about the file.**

### The name sent, by detected format

| `detected_format` | subtype | sent as | why |
|---|---|---|---|
| `PDF` | — | `.pdf` | bytes decide regardless; the name is honest |
| `IMAGE` | — | `.bin` | bytes decide; stage 1 knows it is an image, not which one |
| `WORDPROCESSING` | — | `.docx` | the one case where our name beats a lie in the oversized-zip branch |
| `ZIP_CONTAINER` | — | `.bin` | matches no OOXML suffix, so Docling's own content probe splits it |
| `OLE_COMPOUND` | `LEGACY_WORD` / `LEGACY_SPREADSHEET` / `LEGACY_PRESENTATION` | `.doc` / `.xls` / `.ppt` | honest; fails for want of LibreOffice either way |
| `OLE_COMPOUND` | absent | `.bin` | `Thumbs.db`, `.msg` — nothing to claim |
| `PLAIN_TEXT` | `HTML` | `.html` | catches fragments `_detect_html_xhtml` misses |
| `PLAIN_TEXT` | `CSV` | `.csv` | |
| `PLAIN_TEXT` | `ASCIIDOC` | `.adoc` | the extension is the *only* route to AsciiDoc |
| `PLAIN_TEXT` | `MARKDOWN` or absent | `.md` | see below |
| `UNRECOGNISED` | — | `.bin` | converts to a failure, which is the true answer |

`FLOOR_STOPPED` does not appear: those occurrences carry a blocking verdict from stage 1 and never reach stage 2.

**Unsubtyped plain text is sent as `.md`, and that is the one entry asserting something stage 1 declined to say.** It is taken deliberately. `.txt` and `.md` reach the same `SimplePipeline` Markdown backend, so the choice steers nothing downstream; but `.txt` leaves the mime unset and is therefore exposed to the CSV sniff above, which would convert comma-shaped prose as a spreadsheet, while `.md` resolves in one step. The alternative of sending no extension is not available: it does not fall back to "treat as text", it fails outright. **Making the conversion a function of the detected format alone is the whole point of this record, and `.md` is the only entry that achieves it for this branch.**

**`.bin` is chosen for every case where the bytes are decisive and no honest extension exists.** It is not a claim; it is the absence of one, and it is safe precisely because it matches nothing Docling's extension tables know — which is what lets the zip content probe run.

### The mapping lives in `extraction`

`DoclingClient.convert` takes the detected format and optional subtype alongside the path, and owns the translation. The knowledge being encoded is knowledge *about Docling* — which of its pipelines a given class of file reaches — and it belongs behind the client's interface rather than in the composition root. `extraction` gains a dependency on `corpus`'s two enumerations, which are a stable, dependency-free vocabulary, and `pipeline` keeps knowing only which stage it is running.

This also puts the mapping in the class that already guards against exactly this kind of drift: `sentOptions` lives beside `convert` so that an identity cannot come to claim something the call does not do.

### The naming scheme is part of the extractor identity

`sentOptions()` gains a version marker for the table above — a number bumped when the table changes, not the table itself. ADR-090 defined the identity as the sidecar's version map plus the options the client sends, and the filename is now one of those options in every sense that matters: it changes what comes back, and it is chosen by this code rather than observed from the corpus. Without the marker, a cached response minted under one mapping is served for a call the current mapping would make differently, and nothing in the key notices.

### `BM` is not hardened, and the risk ADR-094 deferred does not exist

`filetype` 1.2.0's BMP matcher is two bytes and no further validation, and the image types are matched before the archive and application types. A text file opening `BM` is classified `image/bmp` by Docling **whatever we name it** — verified for `x.txt`, `x.bmp` and an extension-less name, all three yielding `InputFormat.IMAGE` and the paginated pipeline.

So ADR-094's stated harm — that once the pipeline choice reads the detected format a false `BM` reading "stops being a label and starts steering conversion" — is not something this change introduces and not something it can prevent. Docling was already steering on its own identical misread. **Hardening stage 1's signature would buy a more honest mix report and nothing else**, at the cost ADR-094 already priced: the BMP header's file-size field is zero or the pixel-data size in enough real writers that requiring it trades an unlikely false positive for a likely false negative. **The signature stays as it is.**

The residue is real and is recorded rather than fixed: a text file opening `BM` is converted as an image and arrives at ADR-070's floor. That was true before this decision and remains true after it.

### The zip branch is not split further

ADR-094 deferred splitting `ZIP_CONTAINER` into spreadsheet, presentation and open-document formats "until something consumed it", and named this ticket as the likely first consumer. **It is not one.** `_detect_office_mime_from_zip` performs the identical central-directory lookup, on the same entries ADR-094 listed, whenever the name does not pre-empt it — which the `.bin` in the table guarantees. Splitting would duplicate work Docling does anyway and change no conversion.

What a split would still buy is a finer mix report. That is a stage-1 reporting decision with its own justification, and it is not taken here.

### A missing `detected_format` row fails the occurrence, not the run

Stage 2 reads the row under stage 1's run, which its own run names upstream ([ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md), [ADR-099](0099-a-stages-upstream-run-is-looked-up-by-stage-and-walk-not-recomputed-and-two-candidates-stop-the-run.md)). Detection runs on every occurrence surviving stage 1's floor, so an empty lookup is a broken invariant rather than a property of a file.

It is nonetheless **recorded against that occurrence, and the run continues**: `extraction-failed`, with the free-text `reason` ([ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md)) naming the occurrence and the stage-1 run the row was sought under, and a per-occurrence log line at the level [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md) fixes. **This is deliberately not ADR-099's answer, and the difference is what fails.** An ambiguous upstream run makes every row the stage would write attach to the wrong parent, so it stops the job. One unreadable format row costs one occurrence, and a pass over hundreds of gigabytes that aborts on a single bad row is a worse tool than one that records it and carries on.

**The cost of that choice is named rather than buried**: this is the only `extraction-failed` verdict in the system that Docling's status did not produce, and in the ledger it is indistinguishable from a real conversion failure except by its `reason`. That is why the reason is part of the decision and not left to the implementation.

**The row is never filled in with `UNRECOGNISED` instead.** `UNRECOGNISED` means detection ran and matched nothing — a fact about the file. A missing row is a fact about us. ADR-095 kept `FLOOR_STOPPED` and `UNRECOGNISED` apart for precisely this reason, and the unrecognised count is the number a future junk-floor decision will be taken from. Putting our own wiring failure into it would corrupt the one measurement that record went out of its way to protect.

### Legacy Office is accepted as `extraction-failed`, and the count decides what happens next

A `.doc`, `.xls` or `.ppt` is sent as itself, converts nowhere for want of LibreOffice, and earns `extraction-failed` through [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md)'s status split — the mechanism working as designed, on a document that genuinely did not convert.

**Stage 2 does not skip the call**, though the outcome is knowable in advance. Manufacturing the verdict from the format alone is the shortcut ADR-095 declined when it deferred [#87](https://github.com/algernon28/vespera/issues/87)'s junk floor: it would be a blocking verdict derived from something other than the thing ADR-070 says decides it, and it would hide the cost that makes the case for fixing it. The legacy count in the mix report, plus the conversion time it cost, is what a later decision to provision LibreOffice ([ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md)) would be argued from. That decision is not taken here.

## Consequences

**The ticket's headline case was already working, and the record says so.** A PDF named `photo.png` reached the paginated pipeline before this change. What this change fixes is narrower and real: AsciiDoc and HTML fragments that were converting as plain text or failing, unsubtyped text that failed outright when its extension was unknown, prose that converted as a spreadsheet, and a wordprocessing document that a lying `.xlsx` could divert in the oversized-zip branch.

**Two of ADR-094's four stage-2 obligations are discharged by finding out they were not obligations.** Neither the `BM` hardening nor the zip split is taken, and both refusals are recorded here with the evidence, so the next reader does not re-derive them from the same wrong premise.

**A cache row can no longer straddle two conversions.** `DoclingExtractor` keys on content hash plus extractor identity. Today two occurrences with identical content but different names could convert differently — the name steered Docling — and still share one row. Once the name is a function of the content, that cannot happen; and once the mapping is in the identity, changing the mapping cannot silently reuse the old rows either.

**`ExtractionCache` rows minted before this change are invalidated by the identity bump**, which is the intended effect and the reason the marker exists. The corpus is re-converted at the cost the cache exists to avoid; that cost is paid once, against the alternative of serving responses produced under a naming scheme that no longer exists.

**`extraction` depends on `corpus`.** Both are capability modules, and `AGENTS.md`'s rule is that a capability module may depend on `ledger` and nothing else horizontal. **This is a real boundary change and Spring Modulith will see it**; whether it is expressed as an allowed dependency or by moving the two enumerations somewhere both modules may name is left to the hand-off spec, but it cannot be left to be discovered during implementation.

**`DoclingClient.convert`'s signature changes, and with it the javadoc describing the call as posting the file's own resource.** The `FileSystemResource` still carries the bytes; what travels as the part's filename is now ours.

**Nothing in stage 1 changes.** No detection rule, no enumeration value, no stored row. This record amends what ADR-094 said would *follow* from its decision, never the decision itself, and no run id moves.

**The findings about `docling-serve` are pinned to versions and will rot.** `from_formats` being a no-op, `filetype`'s matcher ordering, the 6000-byte OOXML window and the absent LibreOffice are all facts about `docling-serve` v1.32.0 and `docling` v2.124.0, which `compose.yaml` pins deliberately. A sidecar upgrade is the event that invalidates this record, and the naming-scheme marker in the extractor identity is not a substitute for re-reading it: the marker catches *our* mapping changing, not theirs.
