# ADR-255 — A `policy` refusal at stage 2 means the converter named no format for the bytes it was sent

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Amends**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md), one sentence of its Context: that a file the converter names no format for *"comes back `ConversionStatus.FAILURE`"*. It carries a note at its head.
- **Keeps**: everything else ADR-100 decides, its table and the `UNRECOGNISED` row of it among them; [ADR-143](0143-an-uncategorised-conversion-failure-is-a-verdict-against-the-file.md), under which a `policy` error with no error of the converter's own beside it is about the file; [ADR-252](0252-compiled-code-fonts-certificates-databases-and-emails-are-out-of-scope.md) §5, under which binary content that matches no signature is sent to the converter and is on the review list.
- **Rests on**: the sources of `docling` v2.124.0, which `docling-serve` v1.32.0 locks and the pinned image `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2` runs (`application.yaml`, `compose.yaml`, `TestcontainersConfiguration`); the test below, against that image.

## Context

Stage 2 posts each file to the converter under a name derived from its detected format (ADR-100). Where the bytes are decisive and no honest extension exists, the name is the neutral `document.bin` (`DoclingClient.extensionFor`). An answer that is not a conversion earns `extraction-failed`, and its reason is the category of the error that decided it, a colon, and the error's message (`OccurrenceJudge`, ADR-143). That reason is what the review list, `extraction-failures.html`, shows beside each path.

ADR-252 measured those reasons on one corpus: of 3,924 file occurrences that earned `extraction-failed`, 3,740 carried `policy: File format not allowed: document.bin`. The category reads as a setting of the converter's that refuses the `.bin` name. It is not one.

### What the converter does with bytes it names no format for

At `docling` v2.124.0:

- **`_guess_format` names no format** ([`document.py:766-856`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L766-L856)). For a posted file it reads up to 8192 bytes and asks `filetype` for a media type; only where `filetype` finds none is the extension consulted, and `bin` is in none of the extension tables ([`_mime_from_extension`, `document.py:977-1027`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L977-L1027)).
  - Bytes `filetype` matches nothing in, and that neither the HTML nor the CSV sniff accepts, fall to `text/plain`, which reaches Markdown only where the extension is `md`, `txt`, `text`, `qmd` or `rmd` ([`_guess_from_content`, `document.py:958-974`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L958-L974)). Under `.bin` the answer is none.
  - A zip is opened and its central directory looked up for `word/document.xml`, `xl/workbook.xml`, `ppt/presentation.xml` and an OpenDocument `mimetype` member ([`_detect_office_mime_from_zip`, `document.py:868-895`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L868-L895)). A zip that holds none of them stays `application/zip`, which maps to no format.
- **A format of none is refused by policy, and no backend runs.** The input is given the placeholder `_DummyBackend` ([`document.py:685-690`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L685-L690)), and `DocumentConverter._process_document` answers every format outside `allowed_formats` with status `SKIPPED` and one error, category `policy`, message `File format not allowed: {file}` ([`document_converter.py:743-763`](https://github.com/docling-project/docling/blob/v2.124.0/docling/document_converter.py#L743-L763)). `allowed_formats` defaults to every format ([`document_converter.py:383-385`](https://github.com/docling-project/docling/blob/v2.124.0/docling/document_converter.py#L383-L385)), and the sidecar builds its converter with format options alone ([`docling_jobkit` v3.5.0, `convert/manager.py:570`](https://github.com/docling-project/docling-jobkit/blob/v3.5.0/docling_jobkit/convert/manager.py#L570)). No setting is behind the refusal: it is what a format of none earns.

**The name is not what is refused.** An image posted as `document.bin` converts, because `filetype` names it from its bytes.

### Which files stage 2 posts under the neutral name, and what each earns

| Detected format, of a file occurrence in the corpus | What the converter answers under `document.bin` |
| --- | --- |
| `IMAGE` | names it from its bytes and converts it |
| `ZIP_CONTAINER` | converts an Office Open XML or OpenDocument package its probe names; refuses any other zip by policy |
| `UNRECOGNISED` | refuses it by policy |
| `OLE_COMPOUND` with no subtype | refuses it by policy; read from the sources, and no test posts one |

`BMP`, `VIDEO`, `SPREADSHEET`, `COMPILED_CODE`, `FONT`, `CERTIFICATE` and `DATABASE` are posted under the neutral name too, but `OutOfScope.reasonFor` leaves every file occurrence of them out at stage 1, so they reach the converter only as seeds.

### ADR-100's sentence

ADR-100's Context says that text-shaped content whose extension is not a Markdown one *"lands on `_DummyBackend` (`document.py:686-690`) and comes back `ConversionStatus.FAILURE`"*. At the pinned version the first half holds and the second does not: such a file comes back `SKIPPED`, category `policy`. Stage 2 posts no text under such an extension; the sentence reaches it through ADR-100's `UNRECOGNISED` row, whose bytes fall to `text/plain` as above. That row's *"converts to a failure, which is the true answer"* stands: `SKIPPED` is not a conversion, and earns `extraction-failed` as `FAILURE` would.

### What `DoclingClient`'s javadoc says

The javadoc of `NEUTRAL_EXTENSION` (`DoclingClient.java:124-128`) and of `extensionFor` (`DoclingClient.java:309-317`) says the neutral name claims no format, so the converter's own reading of the bytes stands and, for a zip, its central-directory probe runs. Both are true. Neither says what follows where that reading names nothing.

## Decision

### 1. What a `policy` refusal means

**An `extraction-failed` verdict whose reason is `policy: File format not allowed: …` means the converter named no format for the bytes it was sent.** No pipeline ran on the file's content. It is not a conversion that went wrong, and not a setting of the converter's that refuses the name. That is how the operator reads it on the review list, `extraction-failures.html`, and in any count of `extraction-failed` reasons.

Under the neutral name such a file is bytes `filetype` names nothing for, an `UNRECOGNISED` file or an `OLE_COMPOUND` file with no subtype, or a zip whose central directory names no package the converter reads.

### 2. ADR-100's sentence is corrected

Where ADR-100 says such a file *"comes back `ConversionStatus.FAILURE`"*, it comes back `ConversionStatus.SKIPPED`, with one error of category `policy`. The rest of ADR-100 stands. ADR-100 carries a note at its head, and its text is left as written.

### 3. Nothing else changes

No code, verdict, reason, count, posted name or naming version changes, and no run id moves. A refusal is read as about the file (ADR-143) and kept in the extraction cache as such an answer is ([ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) §1), as before.

## Consequences

- A `policy` refusal on the review list points to what the file is, which stage 1 decides, and not to the converter's settings.
- A sidecar upgrade can change what a format of none earns. ADR-100 already names the upgrade as the event that invalidates its findings; the test below is what notices this one.

## Tests

Every file is a few bytes built in the test, posted to the pinned sidecar.

| Class | What it holds |
| --- | --- |
| `extraction.DoclingRefusesBytesOfNoKnownKindIT.bytesOfNoKnownKindAreRefused` | four kilobytes stage 1 reads as `UNRECOGNISED`, posted as `document.bin`, come back `SKIPPED` with one error, category `policy`, message `File format not allowed: document.bin` |
| `…anArchiveOfAnotherKindIsRefused` | a zip stage 1 reads as `ZIP_CONTAINER`, holding none of the members the probe looks for, comes back the same |
| `…anImageUnderTheSameNameConverts` | a PNG stage 1 reads as `IMAGE`, posted under the same name, converts, and no `policy` error rides with it |

## What this does not decide

- **Whether stage 2 counts or labels these refusals apart from other failures.** A verdict of their own would amend ADR-057's closed list; a reason worded otherwise changes what stage 2 writes and moves the run ids of stages 2 onward.
- **Whether more of them are left out at stage 1.** ADR-252 decides that by kind, by a signature, and names what would leave out binary content with no signature: a signature yet to be found, or a list of name endings in the profile.
- **The javadoc of `NEUTRAL_EXTENSION` and `extensionFor`**, which is true as written and does not name the refusal.
