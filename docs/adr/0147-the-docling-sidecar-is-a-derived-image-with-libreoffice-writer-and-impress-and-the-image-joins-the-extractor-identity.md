# ADR-147 — The Docling sidecar is a derived image with LibreOffice Writer and Impress, and the image joins the extractor identity

- **Date**: 2026-09-24
- **Status**: accepted
- **Amends**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md). Its section *"LibreOffice is not in the image, and legacy Office therefore cannot convert"* stops being true of the sidecar this repository runs. Its table's `OLE_COMPOUND` row still sends `.doc` and `.ppt` as themselves, and they now convert.
- **Amends**: [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md). The extractor identity was the sidecar's `/version` map and the options the client sends. It gains the name of the image, because two images that report the same versions were measured to convert the same file differently.
- **Keeps**: [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md). The tool still owns its sidecars; one of them is now built rather than pulled.
- **Builds on**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md). With spreadsheets out of scope, what LibreOffice would buy is the older Word and PowerPoint files, and nothing else.
- **Rests on**: [`docs/research/docling-legacy-office.md`](../research/docling-legacy-office.md), written 2026-09-23 and committed with this record, and the measurements of 2026-09-24 below.

## Context

`docling-serve` converts `.doc` and `.ppt` by calling LibreOffice to turn them into `.docx` and `.pptx` first. No `docling-serve` image ships LibreOffice, and no documented switch installs it (research note, §1–§2). So every older Word or PowerPoint file earns `extraction-failed`. Since ADR-143 it earns it at once, as a verdict against the file.

What those files are worth was measured, not assumed. The 158 legacy files copied out of the KT PERIN corpus were run through a LibreOffice-enabled sidecar on 2026-09-24:

- **The 132 `.xls` are out of scope (ADR-146).**
- **All 26 `.doc` and `.ppt` converted.** Three `.ppt` are identical copies and stage 4 removed them, which leaves 23 documents.
- **The 19 `.doc` score a median of 0.736, and 13 of them score at or above the GesPOS q3 of 0.651.** All 4 `.ppt` do too. That is higher than the GesPOS corpus's own documents score. The top of the list is specifications: `POS_NuoveLogichePagamento_21.doc` at 0.970, `Integrazione_eLunch_14.doc`, the two `Specifiche_BMEdenred` documents, and `TracciatoSLE4442`.
- **The operator's seed folder holds a `.doc` of its own**, `Edenred - Protocollo FEP-POS - Ed_22.doc`. The stock image cannot convert it, so today it is an unusable seed.

### Three findings shape how LibreOffice goes in

**1. The image changes conversions that never call LibreOffice.** The GesPOS corpus was converted twice on the stock image and once on the LibreOffice image, and the three conversions of the same 14 PDFs were compared:

| comparison | PDFs whose output differs |
| --- | --- |
| stock against stock | 3 |
| stock against the LibreOffice image | 8 |

The 3 that differ between two stock runs are Docling's own variance: each flips between `success` and `partial_success`. The other 5 convert identically every time on the stock image, and differently on the LibreOffice one. A PDF does not go through LibreOffice at all. *Inference, not established:* installing LibreOffice pulls in 115 packages, and some of them (fonts, rendering libraries) change how pages are rasterised for layout and OCR. Separately, one `.docx` gained 8 pictures, because Docling rasterises DrawingML shapes through LibreOffice once it finds it (research note, §1).

The extractor identity, as ADR-090 composes it, is **identical** for both images, because `/version` reports the same component versions. So a cache holding conversions from one image serves them as if they came from the other.

**2. The sidecar cannot be cut off from the internet with the pinned image and a published port.** On a Compose `internal` network, egress is blocked, but the published port no longer answers from the host (measured). The research note's *"no outbound network"* therefore needs a second container, a proxy, which is more than this change should carry.

**3. One crash is unexplained.** On 2026-09-23 the LibreOffice sidecar, run with 8 server workers, died of a segmentation fault in `docling_parse`, Docling's native PDF parser, while converting one seed PDF. It did not recur:

- in two full replays at 8 workers and at the default;
- in 228 stress conversions across four configurations;
- in either run of 2026-09-24.

It is in the PDF parser, which the stock image ships as well, and not in LibreOffice.

## Decision

**The `docling-serve` sidecar is a derived image**, built by the repository from a `Containerfile` of its own:

```dockerfile
FROM quay.io/docling-project/docling-serve-cpu:v1.32.0
USER 0
RUN dnf -y install --nodocs --setopt=install_weak_deps=False \
      libreoffice-core libreoffice-writer libreoffice-impress \
 && dnf -y clean all && rm -rf /var/cache/dnf
USER 1001
```

- **Writer and Impress, and not Calc.** Spreadsheets are out of scope (ADR-146), so Calc would be installed only to be attacked. Several of 2026's LibreOffice advisories are in spreadsheet import (research note, §6). Removing Calc saves only about 40 MB, so the reason is attack surface, not size.
- **On the pinned v1.32.0.** The derived image is measured; the bump to `docling-serve` v1.35.0 is not. v1.35.0 is the first release carrying upstream's hardening of the LibreOffice call: macros disabled, link updates off, and a timed-out converter's whole process group killed (research note, §1). That bump is its own decision, because it re-converts every document and needs measuring the way this one was.
- **The `Containerfile` is `docker/docling-serve/Containerfile`, and the image is tagged for what it is:** `vespera/docling-serve-cpu-libreoffice:v1.32.0`. `compose.yaml` builds it. `TestcontainersConfiguration` builds it from the same file under the same tag, and runs it with the same settings as below, so tests and runtime still name one image (the rule at the head of `compose.yaml`).

**The Compose service runs with:**
- `init: true`, because without an init process every conversion leaves a dead `soffice` behind (research note, §3);
- `cap_drop: [ALL]` and `security_opt: [no-new-privileges:true]`. A real `.doc` was measured to convert under both.

**The server's worker count stays at its default of two.** ADR-140's eight in flight is the client's width, and the sidecar queues what it cannot run. Both measured configurations converted cleanly. The one crash happened at eight workers, so the default is the configuration with no crash against it.

**The extractor identity gains the image.** `application.yaml` names the image as `vespera.docling.image`, in step with `compose.yaml`. The identity bean in `ExtractionJobConfiguration` composes it as `image=<name>` beside the version map and the sent options, so a conversion made by one image is never served as another's. This is the same reasoning ADR-090 used to leave the URL out: what goes in is what determines the output, and here the image does, measured, where the versions alone do not say so. The sidecar does not report its image, so this is the one part of the identity taken as configured rather than read back.

## Consequences

**Measured on 2026-09-24, from empty caches, through the image above with `--init`, `--cap-drop ALL` and `no-new-privileges`:**

| | legacy corpus | GesPOS |
| --- | --- | --- |
| run | exit 0, 6 m 38 s | exit 0, 9 m 25 s |
| `.doc` / `.ppt` converted | 26 of 26 | the seed's `.doc` |
| scored | 23 | 65 |
| relevance median / q3 | 0.734 / 0.775 | 0.599 / 0.664 |
| unusable seeds | 0 | 0 (1 on the stock image) |

**The older Word and PowerPoint files are scored, and they score high.** On the full corpus they are the 19 `.doc` and 7 `.ppt`, 26 files. The operator's own `.doc` seed stops being unusable, which raises GesPOS's median from 0.587 to 0.599.

**Every cached conversion is re-made once**, because the identity changes. That is the cost ADR-100 paid for the same reason, and it is what makes finding 1 safe.

**A working directory scored on the stock image keeps its `extraction-failed` verdicts for `.doc` and `.ppt`.** A verdict outlives its run (ADR-139), so the new stage-2 run does not re-examine them. Start in a fresh working directory, or delete those verdicts and re-run, as ADR-145 and ADR-146 already require.

**The sidecar can reach the internet, and LibreOffice's macro default is High, not Very High, on this image.** These are accepted and recorded, not mitigated here:
- the sidecar holds nothing but the file it was sent;
- it runs as UID 1001 with every capability dropped;
- stage 2 sends only files stage 1 read as `.doc` or `.ppt`, never an unsubtyped compound file (ADR-100).

The v1.35.0 bump closes the macro and timeout gaps. An egress-blocking proxy closes the network one. Both are named here so that neither is mistaken for done.

**The LibreOffice package is 7.1.8, a RHEL 9 line Red Hat has deprecated and RHEL 10 drops** (research note, §6). If `docling-serve` moves its base to CentOS Stream 10, this `Containerfile` stops building, and The Document Foundation's own RPMs are the route. Its changelog lacks several 2026 import-filter fixes. Whether 7.1 is affected by them was not established.

**The image is about 1 GB larger** (7.62 GB to 8.57 GB on disk), and it is built on the operator's machine rather than pulled.

**One segmentation fault stays unexplained, and it belongs to `docling_parse`, not to this change.** If it recurs, it fails the step. Stage 2 cannot tell a crashed sidecar from a stopped one, and ADR-071's rule for that case stands.

**Nothing in `src/main` changes except the identity.** No new verdict, no new format, and no new stage-2 path: a `.doc` that converts now is judged exactly as a `.docx` always was.

**What pins it:**
- **`ExtractorIdentityCompositionTest`:** two sidecars reporting the same versions from different images key their conversions apart.
- **`DoclingSidecarImageTest`, with no Docker:** `compose.yaml`, `TestcontainersConfiguration` and `vespera.docling.image` name one image, and `compose.yaml` builds it from the repository and runs it with `init`, no capabilities and `no-new-privileges`.
- **`DoclingClientIT`:** an older Word document and an older PowerPoint presentation convert through the real image and carry their own text. Both fixtures are generated in the test (ADR-063): the sidecar's own LibreOffice saves a generated `.docx` as `.doc` and a generated `.odp` as `.ppt`.

**A first `./mvnw verify`, or a first `docker compose up`, builds the image**, which takes under a minute over the pulled base. After that Docker's layer cache reuses it.
