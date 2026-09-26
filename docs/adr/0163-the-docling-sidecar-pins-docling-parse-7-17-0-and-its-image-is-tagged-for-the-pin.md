# ADR-163 — The Docling sidecar pins docling-parse 7.17.0, and its image is tagged for the pin

- **Date**: 2026-09-26
- **Status**: accepted
- **Amends**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md), in three places. Its `Containerfile` gains one layer, which replaces the PDF parser the base image ships. Its tag `vespera/docling-serve-cpu-libreoffice:v1.32.0` becomes `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`. Its consequence *"One segmentation fault stays unexplained"* is explained below.
- **Qualifies**: ADR-147's finding 1. It put the 3 PDFs whose output differed between two stock runs, *"each flips between `success` and `partial_success`"*, down to "Docling's own variance". What is measured below is a defect that produces exactly that flip. That those 3 PDFs flipped because of it is consistent with the measurements, and was not measured.
- **Keeps**: ADR-147's extractor identity (`image=<name>`, the `/version` map, the sent options) and its rule that `compose.yaml`, `TestcontainersConfiguration` and `vespera.docling.image` name one image. ADR-147's base, `docling-serve` v1.32.0, and its deferral of the v1.35.0 bump. [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md)'s rule that `partial_success` passes through to the degeneracy floor. [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md)'s rule for a sidecar that stops answering. [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md): the operator starts the sidecars from `compose.yaml`.
- **Rests on**: probes run on 2026-09-26 against the shipped image and against a probe image. The outputs, native crash traces, harnesses and the two parser sources compared are kept outside the repository in `D:\development\vespera-runs\notes\p305-probes\`. The crash that opened the ticket is kept in `D:\development\vespera-runs\2026-09-26-gespos-e2e\crash-1\`.
- **Settles** [#305](https://github.com/algernon28/vespera/issues/305). Its third question, whether an invocation survives the sidecar dying or restarting partway through a step, is [#326](https://github.com/algernon28/vespera/issues/326)'s.

## Context

On 2026-09-26, seed extraction over GesPOS failed on the 10th of 19 seeds. The sidecar, `vespera/docling-serve-cpu-libreoffice:v1.32.0`, died of a segmentation fault (exit 139). A retry with the container restarted converted all 19. A crash of the same kind, on the 10th of 19 seeds, was recorded on 2026-09-23 and is the one ADR-147 left unexplained. The saved trace held only the server's idle event-loop thread, so it did not say where the fault was.

### What was measured

The shipped image carries **docling-parse 7.16.0**, Docling's native PDF parser. The probe image is the shipped image plus one layer, run as UID 1001:

```dockerfile
RUN pip install --no-cache-dir --no-deps docling-parse==7.17.0 && pip check
```

`pip check` passed. Nothing else in the image changed: it still reports `docling-slim` 2.124.0.

Each iteration restarted the container, so its process was cold, waited for `/health`, and sent one PDF with the options Vespera sends (`to_formats=json`, `ocr_preset=rapidocr`, `image_export_mode=embedded`). 30 iterations per row:

| Parser | PDF | Sidecar died (SIGSEGV) | `partial_success`, pages lost | `success` |
| --- | --- | --- | --- | --- |
| 7.16.0 (shipped) | `core14-8.pdf`, synthetic, the 14 standard PDF fonts | **9** | **21** | 0 |
| 7.16.0 (shipped) | `ManualeFunzioni_EasyCheck_20120622.pdf`, a real GesPOS seed | 0 | **9** | 21 |
| 7.17.0 (probe) | `core14-8.pdf` | 0 | 0 | 30 |
| 7.17.0 (probe) | `ManualeFunzioni_EasyCheck_20120622.pdf` | 0 | 0 | 30 |

Every page lost carried the same entry in Docling's `errors[]`: `backend_failure`, *"Page N failed to parse."*, one entry per page. On the real seed a lost conversion lost 1 or 2 of its 6 pages.

**Controls, on 7.16.0, 30 iterations each, from a cold process:**

- The same synthetic PDF, with the single-threaded page decode (`pdf_backend=docling_parse`): 30 of 30 `success`.
- Then, in the same process, the same PDF with the default threaded decode: 30 of 30 `success`. The first call had already loaded every base font the PDF uses.
- Two and four single-threaded decodes of different PDFs at once, in a fresh forked process each time: 200 of 200 clean at each width.

A third container on the 7.16.0 parser, set up to capture the faulting thread, crashed in 2 of 10 iterations and lost pages in the other 8. In both crashes the faulting thread was inside `docling_parse`'s native library, at the same instruction, reading one byte at address `0x8`: a field read through a null pointer. The library is stripped, so the trace does not name the function.

**So the fault needs two things at once:** a process that has not yet loaded the fonts the PDF uses, and several of that PDF's pages decoded in parallel. Either one alone was clean.

### What the source says

docling-parse keeps the standard PDF fonts ("base fonts") in one table per process, shared by every decoding thread, and fills each font's metrics the first time a page asks for it. In **7.16.0**, `base_font::initialise()` does this:

```cpp
if(initialised) { return; }
initialised = true;
// ... then reads the font file and fills its tables
```

The flag is a plain `bool`, set **before** the tables are filled. A second thread that asks for the same font in that window sees `initialised`, returns at once, and reads tables that are still empty or half-built.

In **7.17.0** the flag is a `std::atomic<bool>`. It is checked, then checked again under a mutex, and stored with release ordering only as the function's last statement. Upstream's own comment on that store reads: *"setting it up front is what used to let a second thread take the fast path and read half-populated tables."* The change is commit `0d9cac5`, *"optimization of the parse/render with up to 3.84× speedup"* (#333). It is the only commit between the two releases that touches `base_font.h`.

The measurements and the diff agree: parallel pages, a cold font table, a flag published before the data. Which read faults, and which read throws and loses the page, depends on timing. The trace cannot name the function, so the claim is that the defect upstream fixed produces what was measured. It is not that each crash was traced to that line.

**It fits the crash being seen in seed extraction and not in stage 2**, though this was not measured. A process loads each font once. Stage 2 converts first, so by the time seeds are converted, most of the fonts the corpus uses are already loaded. The race is live again for any font a seed uses and the corpus did not. It also fits the retry passing: the race depends on timing, so the same seeds can convert cleanly on another try.

**Stage 2 is not immune**, because its first conversions start from a cold process too. The ledger of the 2026-09-26 run holds one stage-2 conversion with `partial_success` and a single `backend_failure`, *"Page 2 failed to parse."*, which is this defect's signature. That this one conversion lost its page to the race is consistent with the measurements, and was not measured.

### The same defect loses pages without crashing

The crash was the visible half. **On the real seed, 9 conversions in 30 lost pages and returned `partial_success`, and nothing crashed.** Vespera accepts such a conversion:

- ADR-070 sends `partial_success` to the degeneracy floor like any `success`. It is judged on the text it produced.
- The errors are kept in `extraction_metric.status` and `extraction_metric.error_summary` (`backend_failure`), and in the extraction cache's `errors_json`. No log line, no report and no verdict names them.
- The extraction cache is keyed by the extractor identity. So a conversion that lost a page is served again, with the page still lost, to every later run under the same identity.

A seed with a lost page is still a usable seed if any text is left, and it is measured on what is left. On the 42,851-file archive a lost page is a lost page of the corpus.

### What upstream offers

| `docling-serve` | docling-parse it carries |
| --- | --- |
| v1.32.0 (the base today) | 7.16.0 |
| v1.33.0 | 7.19.1 |
| v1.34.0 | 7.20.0 |
| v1.35.0 | 7.21.0 |

docling-parse itself is at 7.22.0 (2026-09-26). No release of it after 7.17.0 was measured here.

### 7.17.0 changes what PDFs convert to

14 distinct PDFs, from the GesPOS corpus and its seed folder, were converted twice through a sidecar of each parser. The second pass of each was compared, so a cold-process race could not affect the comparison, and every one of those conversions was `success`:

- **13 of 14 differ** in the document JSON. Mostly this is picture bounding boxes, a few hundredths of a point, and picture pixel sizes, a few pixels.
- **In 6 of 14 the text differs**, in 2 to 21 places per PDF, counting each text item added or removed. Examples: a letter `E` from a logo appears as a text item of its own, one table cell's text is split in two, and one item is removed.
- The page counts are the same in all 14.

## Decision

### §1. The `Containerfile` pins docling-parse at exactly 7.17.0

`docker/docling-serve/Containerfile` gains one layer after the LibreOffice one, run as UID 1001, the image's own user. It is the layer the probe image was built with:

```dockerfile
RUN pip install --no-cache-dir --no-deps docling-parse==7.17.0 && pip check
```

- **`==`, not `>=`.** An unpinned parser changes conversions on a rebuild with no change in this repository, and the tag could not say so (§2). 7.17.0 is the version measured, and the one `pip check` passed against this base.
- **`--no-deps`**, so that the layer replaces one package and nothing else. The docling it is paired with stays the one ADR-147 measured.
- **`pip check` in the same `RUN`**, so a build whose packages no longer agree fails at build time and not at the first conversion.

### §2. The image is tagged for the pin, in all three places at once

The image is now `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`. The tag names the base and the parser, which are the two things measured to change conversions. `compose.yaml`'s `image`, `application.yaml`'s `vespera.docling.image` and `TestcontainersConfiguration.DOCLING_SERVE_IMAGE` change together, as ADR-147 requires.

**The tag has to change, even though the identity would change without it.** `/version` reports `docling-parse`, so the version map in the identity moves from `docling-parse=7.16.0` to `docling-parse=7.17.0` on its own. But ADR-147 tags the image *for what it is*. Two different images under one tag is the false statement ADR-147 put the image into the identity to prevent. It would also let a machine keep running the old image under the name of the new one. From now on, a change to the pin is a change to the tag.

**This changes the extractor identity, and stage 2's run is re-minted over an existing working directory.** On the first invocation on the new image, stage 2's `config_consumed` differs, so a new extraction run is minted and every conversion is made again: the extraction cache holds none under the new identity. Every run downstream of it is re-minted as well, because its upstream run is new. Seed extraction converts every seed again, for the same reason. Verdicts under the old runs stay recorded and remove nothing from the new ones (ADR-156).

**That cost is accepted.** It is the cost ADR-100 and ADR-147 paid for the same reason. Here it also buys something: the cache under the old identity holds every conversion that silently lost a page, and a new identity is the only thing that stops serving them. On GesPOS, stage 2 took 3 m 58 s.

### §3. Lost pages stay recorded where they are, and whether to surface them is a separate question

This record removes the cause measured here. It does not change how Vespera treats `partial_success`. ADR-070's rule stands: the conversion goes to the floor and is judged on what it produced. Pages can still fail to parse for reasons of the PDF's own, and that is what ADR-070 was written for.

**What stays true, and is recorded rather than fixed:** a conversion that lost pages is visible only in `extraction_metric` and in the extraction cache. The operator is not told. Whether stage 2's closing line or a report counts `partial_success` conversions, and whether a `backend_failure` page loss earns a second attempt, both reopen ADR-070. That is the operator's decision, not this record's. A separate ticket is recommended. None is filed here.

### §4. Surviving a sidecar that dies partway through a step is #326's question

With the cause removed, no crash is left that is known to recur. The question #305 asked stays open whatever the cause: should `compose.yaml` give the sidecar a restart policy, and should a step wait for `/health` and retry the calls in flight? [#326](https://github.com/algernon28/vespera/issues/326) holds all of it: the restart policy, the retry and its bounds, a file that crashes the converter every time, both steps that call Docling, and the operator's message. This record decides none of it. Until #326 is settled, a sidecar that dies fails the step as ADR-071 and #311 describe, and the operator runs the same command again.

## Alternatives rejected

- **Move the base to a `docling-serve` release that already carries a fixed parser (v1.33.0 to v1.35.0).** This fixes the race too, but it changes docling, `docling-jobkit` and the server as well as the parser. None of those combinations was measured, and ADR-147 left the v1.35.0 bump as a decision of its own because it re-converts every document. It remains that decision. This record pins the parser on the base ADR-147 measured.
- **Pin a later docling-parse, such as 7.22.0.** Not measured. `base_font.h` changed again after 7.17.0 (#351, "missing font-mappings"), which may change conversions again. It is also not known whether it passes `pip check` on this base.
- **Force the single-threaded decode (`pdf_backend=docling_parse`).** Measured clean from a cold process. But it is a sent option, so it too changes the identity and every conversion. It gives up the page parallelism the default decode has, which was not timed here. And it leaves the defective parser in the image, one option away from the race.
- **A warm-up conversion at start-up.** Measured clean once warm, but only for the fonts the warm-up uses. It would need a PDF that uses every base font and alias. It would need to run again after every restart, which Vespera does not see. And it keeps the defect in the image.
- **A restart policy on its own.** It turns a crash into a failed call, and does nothing about the pages lost without a crash. Whether to have one is #326's question (§4).

## Consequences

**The crash measured on 7.16.0 does not occur on 7.17.0**: 0 in 60 cold iterations, against 9 in 30 on the synthetic PDF. That rests on 60 iterations of two PDFs. It shows that the measured race is gone. It does not show that no other native fault exists.

**The pages lost without a crash stop being lost to this cause**: 0 in 60, against 30 in 60 on 7.16.0.

**Conversions change**, as measured above, and every working directory re-converts once (§2).

**AGENTS.md's list of open defects keeps #305 until the image is built from the pinned `Containerfile`.** It is updated by the change that implements this record, not by this record.

**`ExtractorIdentityCompositionTest` does not change.** Its image names are inputs that stand for "two images that report the same versions", as measured on 2026-09-24. They are not the configured image.

## Tests

`DoclingSidecarImageTest` (unit, no Docker, reads the files themselves):

- **`pinsTheParserThatPublishesItsFontsWhole`**: the `Containerfile` installs docling-parse with an exact `==` pin, at 7.17.0, with `--no-deps`, and runs `pip check` in the same instruction. **Fails today**: the `Containerfile` installs no docling-parse.
- **`tagsTheImageForItsBaseAndItsParser`**: the tag `compose.yaml` gives the image is the base's own tag, read from the `Containerfile`'s `FROM`, followed by `-docling-parse-` and the pinned version. Written out, that is `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`. **Fails today**: the tag is `v1.32.0`.
- **`namesOneImageEverywhere`**: unchanged. **Fails today**: `TestcontainersConfiguration` already names the new tag, and `compose.yaml` and `application.yaml` still name the old one.

`RunIdentityGoldenTest.extraction`: the golden `extractorIdentity` names `image=vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`. **Fails today**: `application.yaml` names the old tag. The version map in that test is scripted, so it does not name docling-parse.

What no test here can show is the race itself: it needs the real parser, a cold process and many iterations. The probes above are the evidence for it, and they are not a test.
