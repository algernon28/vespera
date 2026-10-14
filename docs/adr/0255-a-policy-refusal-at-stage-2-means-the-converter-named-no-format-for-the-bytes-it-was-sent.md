# ADR-255 — A `policy` refusal at stage 2 means the converter named no format for the bytes it was sent

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Amends**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md), one sentence of its Context: that a file the converter names no format for *"comes back `ConversionStatus.FAILURE`"*. It carries a note at its head.
- **Keeps**: everything else ADR-100 decides, its table and the `UNRECOGNISED` row of it among them; [ADR-143](0143-an-uncategorised-conversion-failure-is-a-verdict-against-the-file.md), under which a `policy` error with no error of the converter's own beside it is about the file; [ADR-252](0252-compiled-code-fonts-certificates-databases-and-emails-are-out-of-scope.md) §5, under which binary content that matches no signature is sent to the converter and is on the review list.
- **Rests on**: the sources of `docling` v2.124.0 and `filetype` 1.2.0, which `docling-serve` v1.32.0 locks and the pinned image `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2` runs (`application.yaml`, `compose.yaml`, `TestcontainersConfiguration`); the test below, against that image.

## Context

Stage 2 posts each file to the converter under a name derived from its detected format (ADR-100). Where the bytes are decisive and no honest extension exists, the name is the neutral `document.bin` (`DoclingClient.extensionFor`). An answer that is not a conversion earns `extraction-failed`, and its reason is the category of the error that decided it, a colon, and the error's message (`OccurrenceJudge`, ADR-143). That reason is what the review list, `extraction-failures.html`, shows beside each path.

ADR-252 measured those reasons on one corpus: of 3,924 file occurrences that earned `extraction-failed`, 3,740 carried `policy: File format not allowed: document.bin`. The category reads as a setting of the converter's that refuses the `.bin` name. It is not one.

### How the converter names a format, and what it does with none

At `docling` v2.124.0:

- **`_guess_format` asks `filetype` about the bytes before it looks up the extension** ([`document.py:766-856`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L766-L856)). After a check of the name for a DocLang or DCLX extension ([`:772-775`, `:806-809`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L772-L809)), it asks `filetype` for a media type from the bytes ([`:776`, `:812`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L776-L812)), and only where `filetype` names none does it consult the extension ([`:779`, `:819`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L779-L819)); `bin` is in none of the extension tables ([`_mime_from_extension`, `document.py:977-1027`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L977-L1027)). Where the media type is `application/zip`, it looks in the zip's central directory for an Office Open XML or OpenDocument package ([`document.py:820-836`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L820-L836); [`_detect_office_mime_from_zip`, `:868-895`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L868-L895)). Where it still has no media type, it tries an HTML and a CSV sniff of the content, and then takes `text/plain` ([`document.py:842-844`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L842-L844)).
  - It looks the media type up in the converter's table ([`document.py:845`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L845); [`base_models.py:182-273`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/base_models.py#L182-L273)). Where the table has no format for it, `_guess_format` names none ([`document.py:855-856`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L855-L856)). Where the table has several, or the type is `text/plain`, a look at the content decides, and it can name none too ([`_guess_from_content`, `document.py:907-974`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L907-L974)).
- **A format of none is refused by policy, and no pipeline runs.** The input is given the placeholder `_DummyBackend` ([`document.py:685-690`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py#L685-L690)), and `DocumentConverter._process_document` answers every format outside `allowed_formats` with status `SKIPPED` and one error, category `policy`, message `File format not allowed: {file}` ([`document_converter.py:743-763`](https://github.com/docling-project/docling/blob/v2.124.0/docling/document_converter.py#L743-L763)). `allowed_formats` defaults to every format ([`document_converter.py:383-385`](https://github.com/docling-project/docling/blob/v2.124.0/docling/document_converter.py#L383-L385)), and the sidecar builds its converter with format options alone ([`docling_jobkit` v3.5.0, `convert/manager.py:570`](https://github.com/docling-project/docling-jobkit/blob/v3.5.0/docling_jobkit/convert/manager.py#L570)). No setting is behind the refusal: it is what a format of none earns.

### What the test measured

Against the pinned image, each file posted as `document.bin` (Tests):

- four kilobytes from a fixed seed, which stage 1 detects as `UNRECOGNISED`, come back `SKIPPED` with one error, category `policy`, message `File format not allowed: document.bin`;
- a zip holding none of the members the converter's probe looks for, which stage 1 detects as `ZIP_CONTAINER`, comes back the same;
- a PNG, which stage 1 detects as `IMAGE`, is not refused: it does not come back `SKIPPED`, and no `policy` error rides with it.

**The name is not what is refused.** The PNG and the two refused files carry the same name.

### Which detected formats stage 2 posts under the neutral name

Of the detected formats a file occurrence reaches stage 2 with, `extensionFor` posts `IMAGE`, `ZIP_CONTAINER`, `UNRECOGNISED` and `OLE_COMPOUND` with no subtype under `document.bin` (`DoclingClient.java:333-336`). For each, whether the converter refuses a file is decided by its reading of the bytes, as above, and not by the detected format. For instance, `filetype` has a matcher for an animated PNG, `image/apng` ([`types/image.py:51-87`](https://github.com/h2non/filetype.py/blob/v1.2.0/filetype/types/image.py#L51-L87)), which it tries before the one for `image/png` ([`types/__init__.py:20-21`](https://github.com/h2non/filetype.py/blob/v1.2.0/filetype/types/__init__.py#L20-L21)), and the converter's table has no format for `image/apng`.

`BMP`, `VIDEO`, `SPREADSHEET`, `COMPILED_CODE`, `FONT`, `CERTIFICATE`, `DATABASE`, and `OLE_COMPOUND` with the Outlook message subtype, are posted under the neutral name too (`DoclingClient.java:333`, `:336`, `:352`), but `OutOfScope.reasonFor` leaves every file occurrence of them out at stage 1.

### ADR-100's sentence

ADR-100's Context says that text-shaped content whose extension is not a Markdown one *"lands on `_DummyBackend` (`document.py:686-690`) and comes back `ConversionStatus.FAILURE`"*. At the pinned version the first half holds and the second does not: such a file comes back `SKIPPED`, category `policy`. ADR-100's `UNRECOGNISED` row, *"converts to a failure, which is the true answer"*, stands: `SKIPPED` is not a conversion, and earns `extraction-failed` as `FAILURE` would.

## Decision

### 1. What a `policy` refusal means

**An `extraction-failed` verdict whose reason is `policy: File format not allowed: …` means the converter's own reading of what it was sent named no format it converts, and no pipeline ran on the file's content.** That reading starts from the bytes, with `filetype`'s media type for them, and ends in the converter's table of media types (Context). Whatever stage 1 detected the file as, that reading decides, and the neutral name claims no format in it. The refusal is not a conversion that went wrong, and not a setting of the converter's that refuses the name. That is how the operator reads it on the review list, `extraction-failures.html`, and in any count of `extraction-failed` reasons.

### 2. ADR-100's sentence is corrected

Where ADR-100 says such a file *"comes back `ConversionStatus.FAILURE`"*, it comes back `ConversionStatus.SKIPPED`, with one error of category `policy`. The rest of ADR-100 stands. ADR-100 carries a note at its head, and its text is left as written.

### 3. Nothing else changes

No code, verdict, reason, count, posted name or naming version changes, and no run id moves. A refusal is read as about the file (ADR-143) and kept in the extraction cache as such an answer is ([ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) §1), as before.

## Consequences

- A `policy` refusal on the review list points to the bytes, as the converter reads them, and not to the converter's settings or to the name posted.
- A sidecar upgrade can change what a format of none earns. ADR-100 already names the upgrade as the event that invalidates its findings; the test below is what notices this one.

## Tests

Every file is a few bytes built in the test, posted to the pinned sidecar.

| Class | What it holds |
| --- | --- |
| `extraction.DoclingRefusesBytesOfNoKnownKindIT.bytesOfNoKnownKindAreRefused` | four kilobytes stage 1 reads as `UNRECOGNISED`, posted as `document.bin`, come back `SKIPPED` with one error, category `policy`, message `File format not allowed: document.bin` |
| `…anArchiveOfAnotherKindIsRefused` | a zip stage 1 reads as `ZIP_CONTAINER`, holding none of the members the probe looks for, comes back the same |
| `…aPngUnderTheSameNameIsNotRefused` | a PNG stage 1 reads as `IMAGE`, posted under the same name, is not refused: it does not come back `SKIPPED`, and no `policy` error rides with it |

## What this does not decide

- **Whether stage 2 counts or labels these refusals apart from other failures.** A verdict of their own would amend ADR-057's closed list; a reason worded otherwise changes what stage 2 writes and moves the run ids of stages 2 onward.
- **Whether more of them are left out at stage 1.** ADR-252, as amended by ADR-254, decides that by kind, by a signature, and names what would leave out binary content with no signature: a signature yet to be found, or a list of name endings in the profile.
- **The javadoc of `NEUTRAL_EXTENSION` and `extensionFor`**, which does not name the refusal.
