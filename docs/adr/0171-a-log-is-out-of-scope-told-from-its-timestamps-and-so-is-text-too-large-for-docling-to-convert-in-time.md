# ADR-171 — A log is out of scope, told from its timestamps, and so is text too large for Docling to convert in time

- **Date**: 2026-09-29
- **Status**: accepted
- **Extends**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), as [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md) and [ADR-168](0168-videos-are-out-of-scope-and-stage-1-recognises-one-by-its-container-signature.md) did. Two more kinds of text file join `pipeline`'s `OutOfScope`, under the same `out-of-scope` verdict and in the same first pass of stage 1. The vocabulary stays at nine values. Unlike the three before them, neither is a detected format: both are decided over a file stage 1 has already found to be `PLAIN_TEXT`, and `DetectedFormat` does not change.
- **Keeps**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md). Both rules read the bytes and the size, and the name plays no part in either.
- **Keeps**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md)'s five-minute `CALL_TIMEOUT` and [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md)'s eight conversions in flight. The size ceiling is measured against both, and moves when either does.
- **Rests on**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), which says a dependency change that matters is captured in a run's configuration consumed, not in its implementation version. Stage 1's configuration stops being empty (§4).
- **Rests on**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md). The profile gains a numeric key, `logTimestampShareFloor`, that ships unset.
- **Not the lasting answer to a timeout**: [#326](https://github.com/algernon28/vespera/issues/326). This record keeps two kinds of file from being sent. It decides nothing about what a failed or abandoned call does, and §6 is a note for #326, not a change to it.
- **Settles** [#370](https://github.com/algernon28/vespera/issues/370).

## Context

### What happened

On 2026-09-29 the whole-archive run (GPU Docling under ADR-170) failed in stage 2 at 12:37 local time, 54% through, with 5,968 read and 2,819 written: *"the extractor set aside 5 occurrences in a row without converting any of them"*. The five were server logs under `WSAEL_log_Q8/…/OLD/`, of 65 MB to 521 MB, each posted to Docling as `document.md`.

docling-serve's own log showed why. Two 61 MB text files arrived at 10:26 UTC. Vespera gave up on each after `CALL_TIMEOUT` (5 minutes), but **docling-serve does not abort a job whose caller has gone**, and its two workers finished them 1,192 s and 1,191 s later. Two 65–67 MB files arrived at 10:33, queued, and finished after 1,735 s and 1,751 s. docling-serve runs two workers, so from about 10:27 to 11:19 UTC both were held by jobs nobody was waiting for. Every other call queued behind them and timed out, and the breaker read that correctly as a sidecar not answering.

Measured afterwards, Docling's own `document_timeout` request option does not stop a Markdown conversion: the whole 65 MB file with `document_timeout=30` got a 504 after 643 s, and the worker was still at 105% CPU eleven minutes later, freed only by restarting the container.

### Where conversion crosses the call timeout

Measured for this record on 2026-09-29, with no Vespera process running (only the IDE's build process was a Java process). The input is the first *N* bytes of `q8_nodo2_transazioni_20230208.txt`, one of the five, posted exactly as `DoclingClient.convert` posts it: the file as `files` named `document.md`, `to_formats=json`, `ocr_preset=rapidocr`, `image_export_mode=embedded`. One call at a time, each bounded by curl at 300 s, the value of `CALL_TIMEOUT`. The sidecar had 2 workers and `DOCLING_SERVE_MAX_SYNC_WAIT=600`.

**GPU image** `vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0`, the running `vespera-docling-serve-1`:

| Bytes sent | Answer | Call time | Docling's `processing_time` | Answer size |
|---|---|---|---|---|
| 2,000,000 | 200 | 7.6 s | — | — |
| 8,000,000 | 200 | 13.5 s | — | — |
| 16,000,000 | 200, `success` | **51.7 s** | 46.0 s | 43.6 MB |
| 24,000,000 | 200, `success` | 101.4 s | 92.2 s | 65.4 MB |
| 32,000,000 | 200, `success` | 166.3 s | 153.3 s | 87.3 MB |
| 40,000,000 | 200, `success` | 265.2 s | 244.8 s | 109.2 MB |
| 48,000,000 | **none**: the client gave up at 300 s | — | — | — |

The first two rows are #370's own. After the 48 MB call was abandoned, worker 0 was still at 107% CPU, and the container was restarted before anything else was sent.

**CPU image** `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`, started beside it on port 5002 with the same settings, the running container untouched:

| Bytes sent | Answer | Call time | `processing_time` |
|---|---|---|---|
| 16,000,000 | 200, `success` | 49.5 s | 44.1 s |
| 24,000,000 | 200, `success` | 97.9 s | 88.5 s |
| 32,000,000 | 200, `success` | 162.0 s | 148.9 s |

The two images agree to within 5%. Docling's Markdown backend runs on the processor in either, so the GPU buys nothing here. **The cost grows faster than the size.** Doubling from 16 MB to 32 MB took 3.2 times as long, and the step from 32 MB to 40 MB, a quarter more, took 1.6 times as long. The slices are 55,512 lines per 16 MB with no blank line anywhere, so the Markdown parser sees each slice as one paragraph.

**The other text backends**, at 16,000,000 bytes, on the CPU probe container, each built from the same log lines in the shape its format takes:

| Posted as | Built as | Call time | `processing_time` | Answer size |
|---|---|---|---|---|
| `document.csv` | five quoted columns per log line | 7.8 s | 3.7 s | 160.5 MB |
| `document.html` | one `<p>` per log line | 11.7 s | 8.6 s | 40.8 MB |
| `document.adoc` | one paragraph per log line | 6.3 s | 2.8 s | 43.7 MB |
| `document.md` | XML, one element per log line (stage 1 has no XML subtype, so XML is posted as Markdown) | 10.1 s | 7.6 s | 39.5 MB |

So the paragraph-less log is the worst case measured, by a factor of four or more. A CSV's answer is ten times its size.

### How the size is spent

The clock of a call starts when it is sent, not when a worker takes it. With eight calls in flight (ADR-140) and two workers, a call can wait for two others ahead of it. And docling-serve has a clock of its own: it answers 504 once a job has waited `max_sync_wait` seconds since it was queued, queue time included. That is 120 s by default, which is what `compose.yaml` runs today, and #362 proposes 600.

- **Alone**, a text file crosses 300 s between 40 MB and 48 MB, and 120 s between 24 MB and 32 MB.
- **Behind another file at the same size**, which two workers and eight calls in flight make ordinary, it waits for that file and then converts. At 16 MB that is about 52 + 52 = 104 s, inside 120 s and far inside 300 s. At 24 MB it is about 202 s, over 120 s.
- In the failed run, the 61 MB files took about 1,190 s with both workers converting at once. Extrapolating the measurements above gives about 620 s for one of them alone. So running two at once roughly doubled the time.

### What the archive holds

The archive has 26 files over 16,000,000 bytes: 14 text files (the WSAEL logs, 61 MB to 522 MB), 5 Java archives, 3 Word documents, 2 videos, a DLL and an Access database. No text file lies between 16 MB and 61 MB. So any ceiling from 16 MB to 61 MB sets aside the same 14 files.

### Logs

The operator decided on 2026-09-29 that stage 1 should also set aside **a log, at any size**, told from its content. The size ceiling then stays as a safety net for large text that is not a log, such as a CSV export or a dump.

The reason is the operator's own rule for this knowledge base, stated on 2026-09-27 while labelling relevance for GesPOS: *"only documentation, keep the operational records out"*. Specifications, manuals, architecture, flow layouts and process descriptions are relevant. Operational records are not, **even when they are on the same subject as the seeds**: flow files full of merchant data, lookup-table exports, logs. A log is the purest case of an operational record. It records what a system did, line after line, each line stamped with when.

Leaving logs to relevance (stage 5) would be wrong twice:

- **It is late.** By stage 5, every log has been hashed, converted (the fourteen WSAEL logs alone are 4.6 GB, and this record opens with what they do to Docling), shingled, chunked and embedded.
- **It is not reliable.** Relevance scores a document by its similarity to the seeds, and a log of the very system the seeds document is on topic. The GesPOS floor was set at 0.579 so as to lose no documentation, below the 0.636 that made the fewest mistakes. At that setting, on-topic records got through. A rule about the kind of file does not trade one against the other.

**The survey.** Every file in the archive was read the way stage 1 reads it. `BrokenCheck.check` decided each file's format, and the 13,987 it found to be `PLAIN_TEXT`, with any subtype and any name, were measured by exactly the rule in §2 below. The probe ran against this branch's compiled classes and lives outside the repository. The share is the share of non-blank lines read that begin with a timestamp, in whole percent, rounded down:

| Non-blank lines read | Share of them beginning with a timestamp | Text files |
|---|---|---|
| fewer than ten | — | 4,081 |
| ten or more | under 10% | 9,549 |
| | 10% to 19% | 18 |
| | 20% to 29% | 32 |
| | 30% to 39% | 151 |
| | 40% to 49% | 36 |
| | 50% to 59% | 12 |
| | 60% to 69% | 12 |
| | 70% to 79% | 18 |
| | 80% to 89% | 8 |
| | **90% to 100%** | **70** |

**The 70 at 90% or more are all logs**, 4.64 GB between them:

- the 14 WSAEL server logs, at 100%;
- POS application logs from test sessions, device logs from blocked-application and unavailable-service tests, and a crash log;
- 26 rotations of a payment tunnel's log, named with numbered suffixes (`.04` to `.30`, `.s16`);
- host-message traces from a test plan, a card-personalisation flow log, a TLS tunnel's log and an HTTP server's log;
- and a POS log saved as RTF, which stage 1 reads as text.

None is documentation.

**Just under the floor**, from 50% to 89%, are 50 files. They are more logs whose entries run over several lines (the same tunnel's and a POS layer's), and terminal software package catalogs, which stamp each component with its build date. None is documentation either, so a lower floor would also be defensible on this archive. The operator chose 90%.

**Under ten lines**, 92 files have every line beginning with what the pattern reads as a timestamp: one-line terminal parameter records, card dumps and table rows, 15 to 800 bytes each. They are records, but one line is not a log, and the rule does not call a share of fewer than ten lines a share. On this archive no text file at 90% or more has between two and nine lines, so any minimum from 2 to 10 sets aside the same 70.

**The name would have been the wrong signal.** Only 2 of the 70 are named `.log`, and 40 are `.txt`. Of the 27 text files named `.log`, 25 fall below the floor, because their entries run over several lines. A rule keyed on the name would have missed 68 of the 70. The operator ruled the name out on 2026-09-29, because file names are an inconsistent signal. ADR-094 already says a name may only narrow within a class the bytes fixed, and whether a file is a log is not a question a name can answer.

## Decision

### 1. Two rules, over text only

Both rules apply to an occurrence stage 1 detected as `PLAIN_TEXT` from its bytes, **with any subtype or none**: Markdown, CSV, HTML, AsciiDoc and plain text, and so also XML, JSON and RTF, which stage 1 reads as text with no subtype. Both are applied in the first pass, after the broken check and after the kind rules of ADR-146, ADR-167 and ADR-168, which no text file can meet. The log rule is applied before the size rule, so a large log is reported as a log.

- **The log rule**: the file is a log (§2) under the floor the profile sets (§3).
- **The size rule**: its size as census recorded it is more than `DoclingClient.TEXT_SIZE_CEILING_BYTES`, **16,000,000 bytes** (§4). A file of exactly 16,000,000 bytes is in scope.

**PDFs, Office files and images are not covered.** Their cost is not a function of their size in the same way, and none of them caused #370. The three Word documents of 28 MB to 62 MB on this archive still reach Docling.

**Why one ceiling for every text subtype.** CSV, HTML and AsciiDoc convert a 16 MB file four to eight times faster than the paragraph-less log, so a separate, higher ceiling for each could be measured. It would set aside nothing different on this archive, where no text file lies between 16 MB and 61 MB. And a CSV's answer is ten times its size: 160 MB for 16 MB, held in memory and written to the extraction cache. A single rule, *"text over 16 MB"*, is also one an operator can predict.

### 2. What a log is

Measured by a new capability in `corpus`, `TimestampedLines`, from the bytes alone:

1. **What is read.** For a file of at most 131,072 bytes, the whole file. For a larger file, its first 65,536 bytes and its last 65,536 bytes, and nothing between them. Two reads, whatever the size, so a 522 MB log costs what a 128 KB one does.
2. **Decoding.** A UTF-32 or UTF-16 byte-order mark at the start names the charset, and the mark is dropped. Otherwise the bytes are decoded as ISO-8859-1, one character per byte. Every character the pattern looks for is ASCII, and it is the same byte in UTF-8 and in every single-byte code page, so no decode can fail.
3. **Lines.** Each window is split at `\r\n`, `\r` or `\n`. Where the file is read in two windows, the head's last line and the tail's first line are dropped, because each ends or begins wherever the cut fell, and a cut inside a timestamp would make a log's line read as prose. (The survey was run both ways, dropping the tail's first line alone and both cut lines, and every count in *Context* is the same either way.) A line is **non-blank** if it holds a character that is not white space.
4. **A timestamp** is matched at the start of a line, after optional white space and an optional `[` or `(`. It is one of these (Java regular expressions, where `\d` is an ASCII digit):
   - `\d{4}[-/.]\d{2}[-/.]\d{2}[ T_]?\d{2}[:.]\d{2}`: year first, as in `2023-02-08 12:00` or `2023.02.08T12.00`;
   - `\d{2}[-/.]\d{2}[-/.]\d{2,4}[ T_]\d{2}[:.]\d{2}`: day or month first, as in `08/02/2023 12:00`;
   - `\d{2}:\d{2}:\d{2}`: a time alone, as in `12:00:00`;
   - `\d{8}[ T_]?\d{6}`: compact, as in `20230208 120000`;
   - `[A-Z][a-z]{2} +\d{1,2} \d{2}:\d{2}:\d{2}`: syslog, as in `Feb  8 12:00:00`.

   The pattern is the one the operator's first survey used. It misses a few formats. The survey found three small `.log` files whose timestamps it does not read, and they stay in scope. That is accepted: the pattern is code in `corpus`, and extending it re-mints stage 1 by itself (ADR-058).
5. **The count** is two numbers, the non-blank lines read and those beginning with a timestamp. Counts, not a ratio, as ADR-073 asks of a derived metric.

A file is **a log** when at least **ten** of the lines read are non-blank, and the timestamped ones divided by the non-blank ones is at least the floor. A file that cannot be read for this measurement is not a log. Its failure is logged as a warning and the pass goes on, because one file is not worth a failed step.

**The name is never read.** Not the extension, and not the subtype, which is itself name-derived. The reasons are above, under *The name would have been the wrong signal*.

### 3. The log floor is a profile key, and it ships unset

`logTimestampShareFloor`, a new numeric key, on a 0-to-1 scale. It is the last component of `Profile`, and its measurement pointer is at `format-mix.html`.

- **Unset** means no log rule. Stage 1 measures every text file and reports the distribution (§5), and removes nothing as a log. This is **observe before enforce**: the share at which a corpus's logs separate from its documents is not known until it has been measured. It is 90% here because this archive's documents score under 10% and its logs over 90%. An archive of timestamped meeting minutes or changelogs could need a different floor, or none.
- **Answered** means the rule applies at that floor.
- **Unreadable** (ADR-120) is treated as unset, and the closing line says so, in the words it already uses for `degenerateOutputConfidenceFloor`.
- **A number outside 0 to 1 is not range-checked**, as none of the floors is: ADR-120 asks only whether a number can be read, and the one numeric key checked for its range, `generationContextWindow` (ADR-121), is not a floor. A floor above 1 removes nothing, and a floor of 0 or below removes every text file with ten or more non-blank lines.

**Why the profile and not a constant.** The size ceiling (§4) is a property of the pinned Docling image and the call timeout: the same number on every corpus, so it is code. The log floor is a property of the corpus: of how its logs are written and how its documents are written. A threshold of that kind lives in the profile, with its provenance beside it, and ships unset. That is what the profile is for (ADR-061), and it is the operator's standing rule for every corpus threshold. A constant would be silently wrong on the next archive.

The minimum of ten lines and the 64 KB windows are **not** profile keys. They define what is measured, as the timestamp pattern does, not where to cut, and they live in `corpus` beside the pattern.

### 4. The size ceiling is a constant beside the call timeout, and stage 1's configuration records both rules

`DoclingClient.TEXT_SIZE_CEILING_BYTES`, a `public static final long` of `16_000_000L`, sits beside `CALL_TIMEOUT`. Its Javadoc names this record and the measurement (51.7 s on the GPU image and 49.5 s on the CPU image at exactly this size; 300 s crossed between 40 MB and 48 MB; 120 s between 24 MB and 32 MB). It also says the value must be re-measured, with the procedure above, whenever the Docling image, `CALL_TIMEOUT`, docling-serve's sync wait or ADR-140's width changes.

**Why 16,000,000.** It is under the smaller crossing, 120 s alone, by a factor of 2.3 in time, and it stays inside that crossing with a file of its own size queued ahead of it. 24 MB would not. The value is written in bytes and reported in bytes, so no reader has to wonder whether it means 10⁶ or 2²⁰.

**Why not a profile key.** The value answers a question about the converter, not about the archive. An operator has nothing to measure it from, and a key would invite a value the sidecar cannot honour. If an operator ever needs to move the list of what is out of scope, that is ADR-146's deferred configurable list ([#278](https://github.com/algernon28/vespera/issues/278)).

**Stage 1's configuration consumed** changes from `{}` to a record of two fields, in this order:

```
{"textSizeCeilingBytes":16000000,"logTimestampShareFloor":0.9}
```

`logTimestampShareFloor` is `null` when the key is unset or unreadable. This is not decoration. Stage 1's implementation version is `corpus`'s alone (ADR-058), and the ceiling lives in `extraction` and the size rule in `pipeline`. Without the constant in the configuration, a later change to the ceiling would derive the same stage-1 run id over the same walk. ADR-115 would then recognise the run as already done, and the files it should now set aside would reach stage 2. ADR-058 names the configuration consumed as the place for exactly this kind of dependency fact. The log floor is there because every profile value a stage reads is (ADR-048).

### 5. What the operator sees

- **The verdict reason**, word for word, with numbers formatted with `,` as the grouping separator whatever the locale:
  - a log: *"a log, and logs are out of scope: 99% of the lines read from its start and end begin with a timestamp"*, where the percentage is timestamped × 100 ÷ non-blank, rounded down, so it never shows the floor for a share just under it;
  - too large: *"a text file of 516,934,452 bytes, and text files over 16,000,000 bytes are out of scope, because the converter cannot finish one in time"*.
- **Stage 1's log line** for the occurrence, *"out of scope: "* and the reason, as for every out-of-scope file.
- **The format-mix page.** Its out-of-scope paragraph reads:

  > Spreadsheets, BMP images and videos are out of scope, whatever they hold. So is a text file over 16,000,000 bytes, because the converter cannot finish one in time, and so is a log, once logTimestampShareFloor is set in profile.yaml. Files left out as out of scope, and not read any further: *N*. Of those, logs: *L*, and text files left out for their size: *S*. They are still counted in the table below, with everything else.

  Both kinds are counted in *N*. They stay in the *Text* row of the format table, because they are text.

  A new section, *"How much of each text file begins with a timestamp"*, comes after the section on filenames. It has one sentence on how the share is read. Then comes one of two sentences, depending on the floor: *"The floor is 90%: a text file of ten lines or more at or above it was left out as a log."* or *"No floor is set, so nothing was left out as a log. Setting logTimestampShareFloor in profile.yaml turns the rule on."* Then comes a table of text files by share, with the rows of the survey table above: *"fewer than ten lines"*, *"under 10%"*, *"10% to 19%"* and so on to *"80% to 89%"*, and *"90% to 100%"*. The bands use the same rounded-down whole percent as the reason. The floor is written as a whole percent when it is one and as the number otherwise.
- **The profile**: after writing the page, stage 1 points `logTimestampShareFloor`'s measurement at `format-mix.html`, as stage 3 does for `degenerateOutputConfidenceFloor`.

### 6. A note for #326: an abandoned call keeps a worker busy

This changes nothing in #326. It is evidence for it:

- docling-serve aborts a job neither when its caller disconnects nor when it answers its own 504. The 504 path has an explicit `TODO: abort task!` (#362). Docling's `document_timeout` does not stop a Markdown conversion either (#370). Measured again here: 48 MB abandoned at 300 s left its worker at 107% CPU. **Only a restart of the container frees the worker.**
- So **retrying a timed-out call once**, as #326 proposes for a dropped call, would send the same file to a sidecar still converting the first copy. The retry takes the second worker, and with two workers one file then holds both. For a timeout, a retry makes things worse, not better. A retry is sound after a **dropped connection**, where the container died and its workers with it. Treating a timeout as retryable would first need a restart, and a restart also kills every other conversion in flight.

### 7. What is not decided here

- **Seeds are unchanged**, as for ADR-167 and ADR-168. Stage 1 never judges a seed, so a log or an oversized text in the seed folder is sent as before. An operator chose it as an exemplar.
- **Operational records that are not logs**, such as flow files full of merchant data and lookup-table exports, are not recognised here. They do not begin their lines with timestamps, and they remain relevance's to remove.
- **Large PDFs, Office files and images.**

## Alternatives rejected

- **Truncating a large text to its first 16 MB, or splitting it.** Either converts part of a file as if it were the whole, and the cache would hold a conversion of bytes that are not the file's. A log is out of scope anyway, and splitting is a design of its own.
- **Raising `CALL_TIMEOUT`.** A 61 MB file took 1,190 s. A 520 MB one would take hours on this curve, and the worker is held whatever Vespera's timeout is.
- **Relying on Docling's `document_timeout`.** Measured not to stop a Markdown conversion.
- **Refusing to send in stage 2 instead** (`DoclingClient` declining an oversized text as `extraction-failed`). It would cover seeds as well. But stage 2 hashes every file stage 1 did not, before it can look in the cache, which for these fourteen is 4.6 GB read for nothing, and #370 settled on stage 1, where the other out-of-scope kinds already are.
- **Recognising a log by its name** (`.log`, `.out`, `.trc`). It would have missed 68 of the 70, and the operator ruled it out.
- **A log as a `DetectedFormat` or `DetectedSubtype`.** A format is what the leading bytes alone say, and a subtype is what the name adds (ADR-095). Whether a file is a log depends on a share and on a floor the operator sets, so it is a verdict's reason, not a format.
- **A log-level pattern** (`INFO`, `ERROR` and the like) in place of, or beside, the timestamp. The operator's first survey, which selected files by extension, measured it too. Of the 42 of the 70 that survey reached, 24 carry a level word on fewer than half their lines, and 4 carry none. A level is the weaker signal on this archive.
- **A constant for the log floor.** See §3.
- **A profile key for the size ceiling.** See §4.
- **Calling either kind `broken`.** Neither is damaged.

## Consequences

**What re-runs over an existing working directory:**

- **Stage 1 mints a new run**, on two counts: `corpus` gains `TimestampedLines`, and stage 1's configuration consumed is no longer `{}`. It re-reads every file's leading bytes, and now up to 128 KB of every text file. With `logTimestampShareFloor` set to 0.9, it writes `out-of-scope` for the 70 logs on this archive, the 14 WSAEL logs among them. Every one of the 14 is a log, so on this archive the size rule adds nothing. With the key unset, it writes `out-of-scope` for the 14 WSAEL logs by their size alone.
- **Stage 2 mints a new run**, because its upstream run is new and `extraction` has changed. **The 2,819 conversions already cached are reused, not sent again.** The extraction cache is keyed on the content hash and the extractor identity. The content hash is extraction's own hash of the file, and the extractor identity is the image name, the sidecar's `/version` map and `DoclingClient.sentOptions()`. None of those changes: no option is added and the naming scheme stays at version 1. The one condition is that the operator still names the same image in `vespera.docling.image` (ADR-170). None of the 14 WSAEL logs was ever converted, so nothing cached is lost. Any of the 56 smaller logs that was converted keeps its cache row, which nothing reads any more.
- **Every later stage mints a new run**, as ADR-167 recorded. Content-keyed caches are reused, and run-keyed results are recomputed.

**The ceiling moves with the converter.** Whoever changes the Docling image, `CALL_TIMEOUT`, the sidecar's sync wait or ADR-140's width re-measures with the procedure in *Context* and updates the constant and its Javadoc. `RunIdentityGoldenTest` pins stage 1's configuration as literal text, so a changed value is a visible edit there.

**The removal is visible.** The verdict, its reason, stage 1's log and the format-mix page all say so. A log the operator wanted is removed with the rest. What brings one back is a lower floor, or none, in the profile. Under ADR-156, putting the value back removes it again at no cost.

**`CONTEXT.md`** gains the entry *Log*, and its entry for *Out of scope* names logs and text too large to convert.

## Tests

Red until the change lands, except the claims that pin what must not change. The size tests go through the stage-1 step as it is constructed today, and the log tests go through the whole job with `profile.yaml` written as text, so all of them compile before the new types exist.

- `ByteLevelReductionTaskletTest`:
  - `removesATextFileOverTheSizeCeilingAsOutOfScope`: two byte-identical text files of 16,000,001 bytes, and a CSV-named text of 16,000,002 bytes, are each removed as `OUT_OF_SCOPE`, the copies on their own account and never hashed. The reason is exactly the size reason above for 16,000,001. The page counts three left out, three *"text files left out for their size"* and none as logs, and says *"So is a text file over 16,000,000 bytes, because the converter cannot finish one in time"*.
  - `keepsATextFileAtTheCeilingAndAPdfOverIt`: a text file of exactly 16,000,000 bytes and an intact PDF of 16,000,001 bytes carry no verdict. This passes today and must keep passing.
  - `keepsALogWhileNoLogFloorIsSet`: with no profile, a twelve-line file whose every line begins with a timestamp carries no verdict. This passes today and pins that an unset floor means no log rule.
- `LogsAreOutOfScopeInvocationTest` (new, the whole job, `logTimestampShareFloor` at 0.9 written into `profile.yaml` before start-up):
  - a file with no extension whose lines use each of the five timestamp forms is removed as a log, with the reason *"… 100% of the lines …"*. So are a CSV-named file of timestamped rows, a file whose 18 of 20 lines are timestamped (90%, at the floor), and a 200 KB file whose first and last 64 KB are timestamped lines and whose middle is prose, which is only reached by reading the two windows;
  - kept: a file named `notes.log` holding prose, a file with 17 of 20 timestamped lines (85%), a file of nine timestamped lines (fewer than ten), and prose whose lines begin with numbers that are not timestamps;
  - stage 1's run records `"logTimestampShareFloor":0.9`;
  - the page counts the four logs and shows 4 in the *"90% to 100%"* row;
  - `profile.yaml` points the key's measurement at `format-mix.html`.
- Added at the gate, each passing against the change:
  - `ByteLevelReductionTaskletTest.anUnreadableLogFloorRemovesNothingAndIsRecordedAsNone`: a floor of `0,9` is recorded as `null` and removes nothing.
  - `ByteLevelReductionTaskletTest.aLargeLogIsReportedAsALog`: a timestamped text file of 16,000,001 bytes, under a floor of 0.9, gets the log reason and is counted among the logs, not for its size.
  - `NextActionTest.aMistypedLogFloorIsReportedToo`: the closing line names the unreadable floor and quotes it back.
  - `TimestampedLinesTest`: UTF-16 and UTF-32 files with their byte-order marks; a large UTF-16 file whose last window begins half-way through a character; ten lines make a log and nine do not.
- `RunIdentityGoldenTest.byteLevelReduction`: stage 1's configuration consumed is exactly `{"textSizeCeilingBytes":16000000,"logTimestampShareFloor":null}` under a profile that sets no floor.
