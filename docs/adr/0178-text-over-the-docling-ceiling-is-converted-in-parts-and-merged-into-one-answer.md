# ADR-178 — Text over the Docling ceiling is converted in parts and merged into one answer

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-171](0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md) §1, §4 and §5, for its second rule. A text file over `DoclingClient.TEXT_SIZE_CEILING_BYTES` that is not a log stops being out of scope as such. Text with no subtype or a Markdown subtype is converted in parts up to 64,000,000 bytes (§3, §6). What stays out of scope over the ceiling is HTML, CSV and AsciiDoc, text written in UTF-16 or UTF-32, and any text over 64,000,000 bytes (§3). ADR-171's *Alternatives rejected* entry *"Truncating a large text to its first 16 MB, or splitting it … splitting is a design of its own"* is that design, and this record is it. Its log rule, its floor, its ceiling's value and its seed rule are unchanged.
- **Amends**: [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md)'s options, as [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) and [ADR-150](0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md) did before it. `DoclingClient.sentOptions()` gains one entry, the cut rule (§5).
- **Keeps**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §2 and §3. The parts of one file are posted one after another by the worker that took the file (§7). The drain, both streaks and the single writer are untouched.
- **Keeps**: [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) §1. The merged answer is read by `ResponseScope` like any other, and a file whose part the converter refused for its own reasons is not kept (§4).
- **Rests on**: [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md)'s pass-through, [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md)'s call budget, which now bounds each part's call, and [ADR-145](0145-a-tables-cells-are-extracted-text-read-once-in-docling-reading-order.md)'s single reading of extracted text, which is what makes one merged answer enough for every reader.
- **Settles** [#371](https://github.com/algernon28/vespera/issues/371).

## Context

### What ADR-171 left

ADR-171 sets aside two kinds of text file stage 1 finds to be `PLAIN_TEXT`: a log, at any size, once the profile sets a floor, and a text file over 16,000,000 bytes, the most Docling converts well inside the call timeout. It called the second a stop-gap. A large text that is not a log might be documentation, and set aside it never reaches relevance scoring. No text file in the whole PERIN archive needs this today: every text file over 10 MB there is a log. So the design is measured on synthetic files.

### The constraint

Stage 2 stores **one Docling answer per content hash** in the extraction cache, under the extractor identity. Everything downstream reads that one answer's `document.json_content`, and since ADR-145 they read it through one reading, `DoclingDocumentTexts.parse`:

- `DoclingDocumentTexts.lines` feeds the shingles (`ExtractionItemProcessor`) and the seed pass (`SeedExtractionItemProcessor`);
- `ExtractedText`, inside `ExtractionMetrics`, gives the metrics and the language;
- `HybridChunker.chunk` gives the chunks (`EmbeddingScoringTasklet`, `RelevanceReportTasklet`);
- `DocumentPicture` reads `pictures` through the same `body` tree.

Each of them walks `body.children` and resolves `#/texts/N`, `#/groups/N`, `#/tables/N` and `#/pictures/N` references. So several part conversions either become one `DoclingDocument` or every reader learns to read a file's parts.

### Measured for this record

On 2026-10-03, against a throwaway container of `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2` on port 5098, started with `DOCLING_SERVE_MAX_SYNC_WAIT=600` and removed afterwards. The running `vespera` containers were not touched. Every call was posted as `DoclingClient.convert` posts it: the bytes as `files`, named `document.md` (or `.html`, `.csv`, `.adoc` where so stated), `to_formats=json`, `ocr_preset=rapidocr`, `image_export_mode=embedded`, one call at a time. The probes live outside the repository.

**docling-core has a concatenation, and it runs only in Python.** The image's docling-core is 2.93.0, and `DoclingDocument.concatenate(docs)` exists. It indexes each document's items in traversal order, renumbers every `self_ref`, parent and child reference, rewrites captions, references and footnotes, and shifts page numbers by the pages before it. docling-serve exposes no route that calls it.

**What a Markdown answer looks like.** The Markdown backend makes **one text item per line**, with or without a blank line between lines. A paragraph-less run of lines is one inline group holding one text item per line. Every confidence score is null and both grades are `unspecified`. `pages` is `{}`, and `furniture` has no children.

**Whole against parts.** For each text, the whole was converted once, the parts once each, and the items read in `body` order, as `DoclingDocumentTexts` reads them (label and text per text item, cell count per table):

| Text | Cut | Parts | Items, whole and parts | Same items in the same order |
|---|---|---|---|---|
| 1,200,123 bytes, paragraphs, 46 tables, fenced blocks | after a blank line, outside a fence | 4 | 13,394 | yes |
| 1,200,017 bytes, no blank line anywhere | after a line end | 4 | 11,709 | yes |
| 300,039 bytes, paragraphs, tables, fenced blocks | after any line end | 44 | 3,347 | yes |
| 22 lines: a table, a fenced block | after a line end inside the table | 2 | 6 against 7 | **no**: the 8-cell table came back as 4 cells and 2, so one row was lost |
| the same | after a line end inside the fenced block | 2 | 6 against 8 | **no**: two code lines became text, and the line after the block became code |
| HTML, one `<p>` per line and a table | between two paragraphs | 2 | 121 | yes |
| the same | inside the table | 2 | 121 against 131 | **no** |
| CSV, 201 rows | after a line end | 2 | one table of 804 cells against two of 400 and 404 | **no** |
| AsciiDoc, a title and paragraphs | after a blank line | 2 | 101 | yes |

The third row passed by chance: none of its 43 cuts fell inside a table or a fence. The fourth and fifth rows are what a cut in the wrong place does.

For the first text, the merged `json_content` (merged as §2 says) was **identical to the whole answer's** except `origin.binary_hash`. For the second, the items were identical and the groups were not: the whole had one inline group, the merge four, one per part. No reader reads a group except to reach the text items under it.

**The Java merge reproduces docling-core's.** The merge of §2, written as a probe, was compared with `DoclingDocument.concatenate` over the same parts. `texts`, `groups`, `tables`, `pictures`, `body`, `furniture` and `pages` were equal on the 1,200,123-byte text (13,348 texts, 3,571 groups, 46 tables) and on the 10,398-byte fixture the tests carry (three parts).

**Cost.** Short-line text, about 100 bytes a line, one call at a time on the CPU image:

| Bytes | Call time | Docling's `processing_time` | Answer size |
|---|---|---|---|
| 4,000,000 | 4.9 s | 3.9 s | 17.2 MB |
| 8,000,000 | 9.8 s | 7.3 s | 34.5 MB |
| 12,000,000 | 16.0 s | 11.1 s | 51.8 MB |

That text's cost grew in step with its size, and its answer was 4.3 times its size. ADR-171's real log grew faster than its size: 13.5 s for 8 MB and 51.7 s for 16 MB on the GPU image. ADR-171 also measured a CSV's answer at ten times its size, and HTML and AsciiDoc at about 2.6 times.

**Holding a merged answer.** Eight copies of the 8 MB part's text items in one answer made a 247 MB JSON string. Parsing it into a Jackson tree (`tools.jackson` 3.1.5, JDK 26) took 0.96 s and about 970 MB of heap over the string. sqlite-jdbc 3.53.2.1 (SQLite 3.53.2) stored it as one `TEXT` value. Its limit is exactly 1,000,000,000 bytes: `zeroblob(1000000000)` is accepted, and one byte more is refused with `SQLITE_TOOBIG`.

## Decision

### 1. One merged answer, made in Java (the ticket's option (a))

A text over the ceiling that §3 lets through is cut into parts (§2). Each part is converted by its own call, and the answers are merged into **one `DoclingResponse` whose `json_content` is one `DoclingDocument`**. That response is what the caller gets, what the cache keeps under the file's content hash (§5), and what every reader above reads, unchanged.

**Why (a) and not (b).** Every reader stays as it is. The merge was measured to give the readers the same items as the whole file, and the same JSON as docling-core's own concatenation. Recording parts in the ledger would touch every reader above, the cache's key and ADR-183's classification, to answer a question (a) answers in one place.

**Why in Java, and not docling-core's `concatenate` in the sidecar.** docling-serve has no route that calls it. Adding one means a further launcher patch into docling-serve's internals, beyond ADR-179's single dictionary entry. Every part's answer would also travel to the sidecar and back a second time, about 4.3 times the file's size each way. The Java merge does what `concatenate` does for these answers (measured above), and a fixture pins it to `concatenate`'s output.

### 2. Where a part ends, and how parts are merged

**The cut**, over the file's bytes, each part starting where the one before it ended:

1. If what is left is at most `TextParts.PART_BYTES`, **8,000,000 bytes**, it is the last part.
2. Otherwise the part ends within the next 8,000,000 bytes, its window. A **line end** is a `\n` byte, or a `\r` byte not followed by `\n`. A line is **blank** if it holds nothing but spaces and tabs. A **fence line** is one whose first non-space character, after at most three spaces, begins a run of three or more backticks or tildes. The lines from a fence line to the next fence line of the same character, both included, are **inside a fence**, tracked from the start of the file, not of the part.
3. The part ends **just after the line end of the last blank line** that ends inside the window, is not inside a fence, and ends at or after the window's midpoint.
4. If there is no such blank line, it ends **just after the last line end** in the window.
5. If the window holds no line end at all, the file cannot be cut: nothing is sent (§4).

So a part never ends inside a line, and never inside a UTF-8 sequence, because a `\n` or `\r` byte is never part of a multi-byte UTF-8 sequence, nor of a character in any single-byte code page. The parts, joined, are the file byte for byte.

**Markdown structure matters, and only at blank lines.** The table above shows why. A cut inside a table loses a row, and a cut inside a fenced block turns code into text and text into code. A blank line ends a table, and outside a fence it ends every block the Markdown backend makes. Text with no blank line in the back half of its window, which is a log-like or data-dump text, is cut at a line end. Each line is its own item there, so nothing changes but the group. The midpoint keeps a part from coming out tiny where a blank line sits early in the window.

**Why 8,000,000 bytes.** It is half the ceiling, the ticket's *"less a margin"*:

- **On the worst text measured, smaller parts cost less in total.** ADR-171's log took 13.5 s for 8 MB and 51.7 s for 16 MB, so two 8 MB parts take about half the time of the 16 MB they add up to.
- **It holds inside the call budget under load** (§7).
- **It does not make parts tiny.** A part costs one more call and one more 2-second tick (ADR-140), which is nothing beside its conversion.

**The merge.** Answers `a1 … an`, all `success` or `partial_success`, give one response:

- **`json_content`**: `schema_name`, `version`, `name` and `origin` are `a1`'s. `body` and `furniture` are `a1`'s, with `children` built as below. For each of `texts`, `groups`, `tables`, `pictures`, `key_value_items`, `form_items`, `field_regions` and `field_items` that any part carries, the merged list is the parts' lists in part order. In part *k*, every value of a `$ref` or `self_ref` key, at any depth, of the form `#/<list>/<n>` becomes `#/<list>/<n + the length of that list in parts 1 … k−1>`. `#/body` and `#/furniture` are left alone. Then `body.children` and `furniture.children` are the parts' children, in part order. **`pages`** are shifted as `concatenate` shifts them: part *k*'s page numbers, and every `prov.page_no` in it, move up by the highest page number of the parts before it less its own lowest page number plus one. Every text answer measured had `{}`, so nothing moved.
- **`status`**: `partial_success` if any part's is, otherwise `success`.
- **`errors`**: every part's errors, in part order. Each error's `error_message` is prefixed with its part, in the form `part 2 of 3 (bytes 8,000,001 to 16,000,000): `. Bytes are counted from 1, and both ends are included.
- **`processing_time`**: the parts' sum.
- **`confidence`**: each score is the lowest any part reports, or null where none reports one. Each grade is the worst any part reports, in the order `poor`, `fair`, `good`, `excellent`, and `unspecified` only where every part's is. Every text answer measured was all null and `unspecified`.
- **The raw body** is `a1`'s, with `document.json_content`, `status`, `errors`, `processing_time` and `confidence` replaced by the above and `timings` emptied, so that the record's fields and the body that the cache keeps and the readers parse agree.

### 3. Which text is cut, and what stays out of scope

A file is converted in parts when **all** of these hold:

- stage 1 found it `PLAIN_TEXT`, with **no subtype or `MARKDOWN`**: plain text, and so also XML, JSON and RTF, which travel as `document.md` (ADR-100, ADR-171 §1);
- its size is over `DoclingClient.TEXT_SIZE_CEILING_BYTES` (16,000,000) and at most `TextParts.LARGEST_TEXT_BYTES`, **64,000,000** (§6);
- it does not begin with a UTF-16 or UTF-32 byte-order mark (`FE FF`, `FF FE`, `00 00 FE FF`).

**HTML, CSV and AsciiDoc are not cut.** Each has structure that runs across line ends, and a cut can break it. That was measured for HTML, where a cut inside a table turned 121 items into 131, and for CSV, where one table became two, besides the quoted fields a line end may fall inside. AsciiDoc's delimited blocks may hold blank lines, which the fence rule above does not know. A rule for each would be three more cut rules, and a CSV's answer, at ten times its size, would need a bound of its own (§6). **Text in UTF-16 or UTF-32 is not cut** either: a `\n` there is two or four bytes, and every part but the first would lack the byte-order mark.

**Stage 1's size rule becomes three rules**, applied in this order after ADR-171's log rule, which is unchanged and still comes first. Each removes the file as `out-of-scope`, with these reasons, word for word, numbers grouped with `,` whatever the locale:

1. **Over 64,000,000 bytes, any text**: *"a text file of 64,000,001 bytes, and text files over 64,000,000 bytes are out of scope, because the converter's answer for one would be too large to keep"*.
2. **Over the ceiling, HTML, CSV or AsciiDoc**: *"a CSV file of 16,000,002 bytes, and HTML, CSV and AsciiDoc files over 16,000,000 bytes are out of scope, because the converter cannot finish one in time and cutting one into parts breaks its structure"*, where the kind reads *an HTML file*, *a CSV file* or *an AsciiDoc file*.
3. **Over the ceiling, UTF-16 or UTF-32**: *"a text file of 16,000,002 bytes in UTF-16 or UTF-32, and such text files over 16,000,000 bytes are out of scope, because the converter cannot finish one in time and one is not cut into parts"*.

**Any other text over the ceiling is kept** and reaches stage 2, which converts it in parts. Whether a file is cut is decided in one place, `TextParts`, which stage 1 asks and the extractor asks, so the two cannot disagree. A file whose first bytes cannot be read for the byte-order mark is treated as having none. Stage 2 then meets it as it meets any unreadable file today.

**The format-mix page.** Its out-of-scope paragraph reads:

> Spreadsheets, BMP images and videos are out of scope, whatever they hold, and so is a log, once logTimestampShareFloor is set in profile.yaml. So is a text file over 64,000,000 bytes, because the converter's answer for one would be too large to keep, and so is an HTML, CSV or AsciiDoc file, or a text file written in UTF-16 or UTF-32, over 16,000,000 bytes, because the converter cannot finish one in time and such a file is not cut into parts. Any other text file over 16,000,000 bytes is converted in parts. Files left out as out of scope, and not read any further: *N*. Of those, logs: *L*, and text files left out for their size: *S*. They are still counted in the table below, with everything else.

*S* counts all three size rules.

**Stage 1's configuration consumed** gains the rule, between the two fields ADR-171 put there:

```
{"textSizeCeilingBytes":16000000,"textParts":"v1,over=16000000,upto=64000000,part=8000000","logTimestampShareFloor":null}
```

`textParts` is `TextParts.RULE`, the same string the extractor identity carries (§5). Stage 1's implementation version is `corpus`'s alone (ADR-058), and the rule lives in `extraction` and `pipeline`, so without it a change to which text is cut would derive the same stage-1 run id over the same walk. That is ADR-171 §4's argument, made again for this rule.

**PDFs are out of scope here.** Docling's `page_range` could convert a large PDF in parts in the same way. That is a possible follow-up, and no PDF caused #370.

### 4. One part failing fails the file, and the reason names the part

Parts are posted in order, and the **first part that does not convert ends the file**. No later part is sent, and the parts already converted are discarded.

- **An answer whose status is neither `success` nor `partial_success`**: the file's response is that part's answer, with each error's `error_message` prefixed with its part as in §2, in the record and in the raw body alike. If the part reported no error, one error is supplied: `component_type` `vespera`, `module_name` `text-parts`, category `unknown`, message `part 2 of 3 (bytes 8,000,001 to 16,000,000): no categorized error was reported`. A failure that reports no error and one whose only error is `unknown` are the same reading under ADR-143 and ADR-183 §1, step 5, so the supplied error changes the reason's words and not its reading. `ResponseScope` then reads the response exactly as it would have read that part's own answer:
  - **A document-scope failure** is kept in the cache and earns `extraction-failed`, and stage 2's reason is `<category>: part 2 of 3 (bytes …): <Docling's message>`, built by `ExtractionItemProcessor` as it builds every reason today.
  - **A service-scope failure or a reported timeout** is not kept (ADR-183). It is faulted and asked again later, whole: every part is cut and sent again.
- **No answer**: whatever a part's call throws ends the file with that exception, rethrown as it is, never wrapped. That covers `DoclingCallTimeoutException`, and #326's rejected and dropped calls once they land. Stage 2 then decides the file as it decides a whole file that threw the same, and ADR-071's streak counts it once. Every one of these messages names the path that was posted, and each part is posted from a temporary file named `<the file's own name>.part-<k>-of-<n>`, so the message names the part without any exception type changing.
- **A window with no line end** (§2, step 5): nothing is sent. The response is a failure with one error, `component_type` `vespera`, `module_name` `text-parts`, category `unknown`, message `the text cannot be cut into parts: the line beginning at byte 1 is longer than 8,000,000 bytes`, with the byte where that line begins. It has no document, `processing_time` 0 and no confidence. It is a document-scope failure: kept, and `extraction-failed`. Sending the file whole instead is the case ADR-171 opens with: one paragraph of tens of megabytes, holding a Docling worker long after the call has given up.

**Why fail the whole file.** A partial document would be shingled, measured, chunked and scored as though it were the file. It would score as something it is not, and nothing downstream could tell. The ticket recommended this, and nothing measured argues otherwise.

### 5. Identity and cache: the rule joins the extractor identity, and the merged answer is cached like any other

`TextParts.RULE` is `v1,over=16000000,upto=64000000,part=8000000`, composed from `DoclingClient.TEXT_SIZE_CEILING_BYTES`, `TextParts.LARGEST_TEXT_BYTES` and `TextParts.PART_BYTES`. `DoclingClient.sentOptions()` ends with `;text_parts=` and that rule:

```
to_formats=json;ocr_preset=rapidocr;image_export_mode=embedded;naming=1;text_parts=v1,over=16000000,upto=64000000,part=8000000
```

`v1` names the version of everything §2 to §4 fix: the cut, the subtypes and encodings that are cut, the merge and the failure rules. **Whoever changes any of them bumps it.** A change to any of the three sizes changes the string by itself.

**Why in the identity.** How a file is sent changes what comes back: one inline group per part, the prefixed errors, the summed `processing_time`. ADR-100 put the naming scheme in the identity for that reason, and ADR-150 put the picture mode there although only some formats carry pictures, because the identity is one value per run and describes the request for every format. Leaving the rule out would let a seed converted whole by an earlier build be served as the conversion this build makes in parts, and would let a later change of part size serve merged answers cut another way.

**Why `sentOptions` and not the composing site.** ADR-090 keeps the options beside the client so that they cannot drift from what the client sends. `TextParts.RULE` sits beside the cut and the merge that make it true.

**The cache is unchanged.** The merged response is kept under the file's content hash and the extractor identity, by `ExtractionCache.put`, under ADR-183's rule. Parts are never cached on their own, because no content hash names a part.

### 6. A file over 64,000,000 bytes is still set aside

**Why a bound at all.** The merged answer is one Java string, parsed whole by every reader above and written as one SQLite value, and SQLite refuses a value over 1,000,000,000 bytes (measured). A 500 MB text would answer with more than 2 GB at the ratio measured, past both SQLite's limit and what a Jackson tree can be expected to hold.

**Why 64,000,000.** It is eight parts. At the worst ratio measured for text this record cuts, 4.3 times, the answer is about 276 MB: 3.6 times under SQLite's limit, and about 1.1 GB of tree per reader, measured as 970 MB for 247 MB. A file of this size on the worst text measured, ADR-171's log at about 13.5 s per 8 MB part, takes about two minutes of one worker. The bound sets aside nothing different on this archive, where every text over 16 MB is a log of 61 MB or more.

**Why a constant.** The bound answers a question about the converter's answer and the store, not about the archive, as ADR-171 §4 says of the ceiling. It sits on `TextParts`, and it is in the identity and in stage 1's configuration through `TextParts.RULE`.

### 7. Parts are posted one after another, on the worker that took the file

The extractor makes the part calls inside `DoclingExtractor.convertUncached`, the method `ConversionDispatch`'s workers already run, on the same worker, one part at a time. They do not go through `ConversionDispatch`'s pool.

- **ADR-140 §3 still holds.** The worker makes Docling calls, writes and deletes its temporary files, and merges JSON. It touches no Spring Batch object, no `JdbcTemplate`, no `Ledger` and neither streak bean. The cache lookup before it and the write after it stay on the step thread.
- **Docling has two workers.** Through the pool, one file's eight parts would take all eight slots and queue every other occurrence's call behind them, inside each call's 300 s budget. That is the shape of the 2026-09-29 failure ADR-171 opens with. One part at a time, a file in parts holds one Docling worker. Eight such files in one wave queue at most four parts per Docling worker: about 4 × 13.5 s on the worst text measured, or twice that if two concurrent conversions double each other's time, as ADR-171 extrapolated. Either way that is inside `CALL_TIMEOUT`, which bounds each part's call and not the file.
- **#369's read-ahead window is untouched.** One file is still one read, one dispatch and one pending answer, so the window counts files as it always has. What changes is that one pending answer can take minutes. The drain waits on it in read order while the other workers finish what they hold. That costs some throughput on a file in parts, which this archive does not have, and it costs nothing else.
- **Seeds** convert through `DoclingExtractor.convert`, which calls the same method, so a seed over the ceiling that §3 lets through is cut too. ADR-171 §7 still holds for every seed §3 does not let through: it is sent whole, as before.

### 8. Where the code goes

One new type in `extraction`, `TextParts`, and one change of behaviour in `DoclingExtractor.convertUncached`. Nothing in `pipeline` changes for stage 2. Stage 1's `OutOfScope`, `ByteLevelReductionTasklet` and `FormatMixReport` carry §3. `DoclingClient` gains the `text_parts` option in `sentOptions()`, and `TEXT_SIZE_CEILING_BYTES`' javadoc stops saying it is the largest text stage 1 lets through. Each part is posted through `DoclingClient`'s existing package-private `convert(Path, DetectedFormat, DetectedSubtype)`, from a temporary file, with the file's own format and subtype, so the posted name is `document.md` as for the whole file. The temporary directory is removed when the call returns or throws.

## Alternatives rejected

- **Parts as cache rows under a part key, read in order by every reader** (the ticket's option (b)). Rejected in §1.
- **docling-core's `concatenate` in the sidecar.** Rejected in §1.
- **Cut at any line end.** Measured to lose a table row and to turn code into text (§2).
- **Cut HTML, CSV and AsciiDoc too.** Measured to break HTML tables and to split one CSV table into two (§3). Each would need a rule of its own.
- **Parts of 16,000,000 bytes, the ceiling itself.** On the worst text measured that costs about twice as much in total as 8,000,000-byte parts, and it leaves no margin under load (§2, §7).
- **Parts through `ConversionDispatch`'s pool.** Rejected in §7.
- **A partial result when a part fails.** Rejected in §4.
- **No upper bound.** Rejected in §6.
- **Leaving the rule out of the extractor identity, so that cached conversions survive.** Rejected in §5. The cost is under *Consequences*.
- **Sending a file whole when its window has no line end.** Rejected in §4.

## Consequences

**What re-runs over an existing working directory:**

- **Stage 1 mints a new run.** Its configuration consumed gains `textParts`. Under the profile's log floor of 0.9, it removes the same 70 logs on this archive as before, the 14 WSAEL logs among them. With the floor unset, the 14 WSAEL logs are removed by size: any under 64,000,000 bytes, among them the two of about 61 MB that ADR-171 opens with, are now kept and reach stage 2 to be converted in parts, and the rest are set aside by the 64,000,000-byte rule. How many lie under the bound is not counted here. **An operator who wants logs set aside sets the floor**, which is what ADR-171 already advised.
- **Stage 2 mints a new run, and every cached conversion is made again once.** The extractor identity changes for every row, not only for files in parts, because the identity is one value (§5). On the GPU that is the 3 to 4 hours for the 10,469-file folder that ADR-179's consequences priced. **ADR-179 already moves the identity for every row, since both Docling tags move to `-r2`.** On 2026-10-03 the GPU sidecar on this machine still ran the image before `-r2`, so no conversion has yet been made under `-r2` here. If this record's implementation ships before the operator's first stage 2 under `-r2`, the two changes cost one re-conversion, not two. That is the operator's call, as ADR-183 §3's release was.
- **Every later stage mints a new run**, as ADR-171 recorded.

**What the operator sees:**

- the format-mix page's paragraph (§3), and the reasons of §3 on each verdict and in stage 1's log line;
- for a file in parts, nothing of its own in stage 2: one metric row, its shingles and its chunks, as for any file;
- a failing part named in the `extraction-failed` reason (§4).

**What changes for a file in parts.** Its `processing_time` is the parts' sum. Its metric row's `error_summary` carries the prefixed messages. Its `json_content` holds one inline group per part where a paragraph-less run was cut at a line end, and no reader reads that.

**`CONTEXT.md`'s Out of scope entry** names what stays out of scope over the ceiling, in the same change as this record.

**The comments that say the opposite are corrected with the code**: `TEXT_SIZE_CEILING_BYTES`' javadoc (*"the largest text file stage 1 lets through"*), `OutOfScope.sizeReason`'s, and `DoclingExtractor`'s class javadoc, which says a miss issues exactly one call.

**The ceiling and the part size move together.** Whoever re-measures the ceiling under ADR-171's procedure re-measures the part size beside it. Either changes `TextParts.RULE`, and so the identity and stage 1's configuration.

## Tests

Red until the change lands, except where a claim pins what must not change.

**`TextInPartsTest`** (new, `src/test/java/io/algernon/vespera/extraction/`, `@JdbcTest` for the chunker's cache). It uses the real `DoclingExtractor` over a stub `DoclingClient`, a subclass overriding `convert(Path, DetectedFormat, DetectedSubtype)`, which records each posted file's name and bytes and answers as the Markdown backend was measured to: one text item per non-blank line. The class counts its calls in an instance field, fresh per test.

1. `aTextOverTheCeilingIsConvertedInPartsAndAnsweredAsOne`: a 17,000,000-byte text of paragraphs, with a paragraph-less stretch, converted through `convertUncached`. More than one call. Every part is at most 8,000,000 bytes and ends at a line end. The parts, joined, are the file byte for byte. Each part is posted under the file's format and subtype from a file named `<name>.part-<k>-of-<n>`, and none of those files is left afterwards. The one response is `success`, its `processing_time` is the parts' sum, and `DoclingDocumentTexts.lines` of it is the file's non-blank lines, in order, none lost or repeated.
2. `aTextAtTheCeilingIsSentWhole`: 16,000,000 bytes, one call, posting the file itself. Passes today.
3. `textThatIsNotCutIsSentWhole`: a CSV-subtyped text and a UTF-16 text, each 16,000,100 bytes, and a 64,000,100-byte text, each one call, posting the file itself. Passes today.
4. `oneFailingPartFailsTheWholeFileAndNamesThePart`: lines of exactly 100 bytes, 16,000,100 bytes in all, so the parts are 8,000,000, 8,000,000 and 100 bytes. Part 2 answers `failure` with a `backend_failure`. Two calls, not three. The response is `failure`, and its error keeps its category and reads `part 2 of 3 (bytes 8,000,001 to 16,000,000): ` and then Docling's message, in the record and in the raw body. `ResponseScope` reads it as document scope.
5. `aFailingPartThatReportsNoErrorIsNamedToo`: the same, with part 2 reporting no error. One `unknown` error is supplied, naming the part.
6. `aPartTheConverterRefusedForItselfIsNotKept`: part 2 answers `capacity`. `ResponseScope` reads the response as service scope, and `remember` followed by `cached` finds nothing.
7. `aPartThatTimesOutEndsTheFile`: part 2's call throws `DoclingCallTimeoutException`. It reaches the caller unwrapped, its message names `part-2-of-3`, part 3 is never sent, and no temporary file is left.
8. `aPartialPartMakesAPartialAnswer`: part 1 answers `partial_success` with one error, and the merged response is `partial_success`, carrying that error with its part.
9. `aLineLongerThanAPartIsNotSent`: a text whose first line runs past 8,000,000 bytes. No call, and the response is the failure of §4, read as document scope.
10. `cutsAfterABlankLineOutsideAFence`, `cutsAtALineEndWhereTheBackHalfHasNoBlankLine`, `aLoneCarriageReturnEndsALine` and `neverCutsInsideAUtf8Sequence`: `TextParts.cut` at small limits.
11. `theMergedAnswerReadsAsTheWholeFileDoes`: the fixture under `src/test/resources/io/algernon/vespera/extraction/text-in-parts/`, a 10,398-byte Markdown text, its three parts' real answers, the whole text's real answer and docling-core's concatenation, all recorded for this record against the sidecar above. `TextParts.cut` at 4,096 bytes ends the parts at bytes 3,485 and 7,502. The first is the blank line before a fenced block that holds three blank lines, so a cut that ignores fences would end it at 4,077, inside the block. The second is a line end in a paragraph-less stretch. `TextParts.merged` of the three answers then gives the same `DoclingDocumentTexts.parse` as the whole answer, so the same shingle lines, the same `ExtractedText`, the same metric bar `processing_time`, and the same chunks from `HybridChunker`. Its `texts`, `groups`, `tables`, `pictures`, `body`, `furniture` and `pages` equal docling-core's `concatenate`, and every `$ref` in it resolves to an item whose `self_ref` is that reference.
12. `theRuleIsPartOfTheExtractorIdentity`: `DoclingClient.sentOptions()` ends with `;text_parts=v1,over=16000000,upto=64000000,part=8000000`.

**`ByteLevelReductionTaskletTest`**:

- `removesATextFileOverTheSizeCeilingAsOutOfScope`, ADR-171's, is replaced by **`removesTextThatIsNotCutAndKeepsTextConvertedInParts`**. Plain text of 16,000,001 bytes and Markdown of 16,000,005 bytes carry no verdict. A CSV file, an HTML file and an AsciiDoc file just over the ceiling, and a UTF-16 text of 16,000,002 bytes, are each removed with §3's reason. The page counts four left out for their size and says *"Any other text file over 16,000,000 bytes is converted in parts"*.
- **`removesATextFileOverTheLargestSizeConvertedInParts`**: 64,000,001 bytes is removed with §3's first reason, and 64,000,000 bytes is kept.
- `aLargeLogIsReportedAsALog` and `keepsATextFileAtTheCeilingAndAPdfOverIt` are unchanged, and both pass: a log over the ceiling is still set aside as a log.

**Amended literals**: `RunIdentityGoldenTest.byteLevelReduction` (§3's configuration), `RunIdentityGoldenTest.extraction` (the identity ends with §5's option), and `DoclingClientTest.namesTheNamingSchemeAmongTheOptionsItSends` (the options stated whole).

## What this does not decide

- **Large PDFs**, through Docling's `page_range` (§3).
- **Cutting HTML, CSV or AsciiDoc**, each by a rule of its own. Open a ticket when one is met.
- **A text over 64,000,000 bytes that is not a log.** It stays set aside. Raising the bound means changing how an answer is stored, not this rule.
