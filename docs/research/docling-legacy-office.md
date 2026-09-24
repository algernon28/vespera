# Legacy Office (DOC/XLS/PPT) through docling-serve: what adding LibreOffice would take

Research record for the operator's decision on provisioning LibreOffice for the `docling-serve` sidecar. It builds on
[ADR-100](../adr/0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md)'s
"LibreOffice is not in the image" section. Everything here is a fact, labelled with how it was found, apart from the
final section, which is a non-binding recommendation.

## Scope and method

Two kinds of evidence, labelled throughout:

- **Source**: a link to the file, release or page that owns the claim. Docling and docling-serve are cited at the tags
  `compose.yaml` pins (`docling` v2.124.0, `docling-serve` v1.32.0) and at the latest releases, `docling` v2.130.0 and
  `docling-serve` v1.35.0, both published this week.
- **MEASURED**: produced on 2026-09-23 on the same host as
  [`docling-serve-concurrency.md`](docling-serve-concurrency.md) (Windows 11, WSL2, Docker 29.6.1). The probe built a
  derived image (`vespera-probe/docling-serve-cpu-lo:v1.32.0`), ran it on port 5002 next to the live sidecar, and posted
  files with the same multipart shape the client uses (`files`, `to_formats=json`, `ocr_preset=rapidocr`). The probe
  was built outside the repository in the session scratchpad and is not committed. Its fixtures were copies of the 7
  `.xls` files under `A&RD_GesPOS_BaseDocumentale`, which are the 7 `.xls` files the live run refused, and of 3 `.msg`
  files. One synthetic `.doc` was also used. The archive itself was only read.

Deliberately **not** covered: `.ppt` was not measured, because the corpus has none and a synthetic one could not be made
in the time available. Nor were long-run memory behaviour or the 120 s timeout path measured.

---

## 1. How Docling converts DOC/XLS/PPT at v2.124.0

**Where it happens.** Each legacy format is converted to its OOXML sibling as the first step of the matching modern
backend's constructor. Source:
[`msword_backend.py#L395-L396`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/msword_backend.py#L395-L396)
(`doc`→`docx`),
[`msexcel_backend.py#L301-L302`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/msexcel_backend.py#L301-L302)
(`xls`→`xlsx`) and
[`mspowerpoint_backend.py#L148-L149`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/mspowerpoint_backend.py#L148-L149)
(`ppt`→`pptx`). After that step the file follows exactly the DOCX/XLSX/PPTX path.

**The function.** `convert_to_modern_format` is in
[`docling/backend/docx/drawingml/utils.py#L82-L155`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/docx/drawingml/utils.py#L82-L155).
Source:

| aspect | at v2.124.0 |
|---|---|
| binary lookup | `shutil.which("libreoffice")`, then `shutil.which("soffice")`, then the macOS app path ([L30-L58](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/docx/drawingml/utils.py#L30-L58)). The lookup uses `PATH` only. |
| env var | **None is read.** `DOCLING_LIBREOFFICE_CMD` appears only in the text of a warning ([`msword_backend.py#L855-L857`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/msword_backend.py#L855-L857)). |
| argv | `<cmd> -env:UserInstallation=file:///tmp/docling_lo_profile_XXXX --headless --convert-to <xlsx\|docx\|pptx> --outdir <tmpdir> <tmpdir>/input.<xls\|doc\|ppt>` |
| profile | A fresh `mkdtemp` profile is created for each call and deleted afterwards. Its docstring says this is so concurrent conversions do not collide on LibreOffice's profile lock ([L61-L79](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/docx/drawingml/utils.py#L61-L79)). |
| timeout | `timeout_s=120` by default, and none of the three callers overrides it. The separate `LIBREOFFICE_TIMEOUT_S = 60` applies only to the to-PDF rendering helper. |
| process handling | `subprocess.run(..., check=True, timeout=...)`, with stdout and stderr sent to `DEVNULL`. |

**Failure modes, all from the same source:**

- There is no binary. The code raises `RuntimeError("LibreOffice is required to convert a .xls file to .xlsx. Install
  LibreOffice and make sure it is on PATH.")`. This is today's refusal. **MEASURED:** through the pinned v1.32.0 sidecar
  the HTTP response shows only `status: failure` with the text "An unexpected error occurred while opening the document
  tipoStatoTerm.xls". The LibreOffice message itself does not reach the response body.
- soffice exits non-zero. This raises `subprocess.CalledProcessError`. Stderr is discarded, so the cause is lost.
- soffice runs past 120 s. This raises `subprocess.TimeoutExpired`. `subprocess.run` kills only the direct child. The
  v2.130.0 fix below says in its own docstring that this "can leave the real worker alive to accumulate".
- soffice exits 0 but writes no output. This raises `RuntimeError("LibreOffice did not produce the expected output")`.

**Side effect of installing LibreOffice: modern formats begin to call it as well.** This is a source fact, and it goes
beyond the 8 refused files. Once `get_docx_to_pdf_converter()` finds a binary:

- DOCX DrawingML shapes other than charts (shapes, SmartArt) are rasterised through soffice
  ([`msword_backend.py#L843-L862`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/msword_backend.py#L843-L862)).
- XLSX EMF/WMF pictures are converted through soffice
  ([`msexcel_backend.py#L1135-L1180`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/msexcel_backend.py#L1135-L1180)).
- Chart rendering in XLSX and PPTX stays opt-in behind `render_chart_images`
  ([`msexcel_backend.py#L1396-L1399`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/msexcel_backend.py#L1396-L1399)).

*Inference:* conversion output for some DOCX and XLSX files that already convert would change, gaining pictures. The
`ExtractionCache` identity does not include "LibreOffice present", so cached rows would not be invalidated.

**Upstream has since hardened this call.** Source:
[docling PR #4228](https://github.com/docling-project/docling/pull/4228), merged 2026-09-21 and released in
[v2.130.0](https://github.com/docling-project/docling/releases/tag/v2.130.0) as "Harden the LibreOffice profile,
flags, and timeout kill". The v2.130.0
[`utils.py`](https://github.com/docling-project/docling/blob/v2.130.0/docling/backend/docx/drawingml/utils.py):

- adds `--norestore --nologo --nolockcheck --nodefault`;
- seeds the throwaway profile's `registrymodifications.xcu` with `MacroSecurityLevel=3`, `DisableMacrosExecution=true`,
  and link update on load set to never for Writer and Calc;
- starts soffice in its own process group and kills the whole group on timeout;
- reads `DOCLING_LIBREOFFICE_CMD` first.

None of this is in v2.124.0. The same `utils.py` fetched at v2.125.0 through v2.129.0 contains none of it either.
`docling-serve` [v1.35.0's `uv.lock`](https://github.com/docling-project/docling-serve/blob/v1.35.0/uv.lock) locks
`docling-slim` 2.130.0, so **v1.35.0 is the first docling-serve release that carries the hardened call**.

## 2. No docling-serve image ships LibreOffice, and there is no documented switch

- The only OS-package layer is `dnf install $(cat os-packages.txt)`. That file lists eight packages, none of them
  LibreOffice, both at [v1.32.0](https://github.com/docling-project/docling-serve/blob/v1.32.0/os-packages.txt) and at
  [v1.35.0](https://github.com/docling-project/docling-serve/blob/v1.35.0/os-packages.txt) (source). The
  [Containerfile](https://github.com/docling-project/docling-serve/blob/v1.35.0/Containerfile) has no build argument
  that installs OS packages. `UV_SYNC_EXTRA_ARGS` only picks Python extras. `grep -rni "libreoffice\|soffice"` over the
  v1.32.0 tree returns nothing, as ADR-100 found.
- Docling's own table says `DOC, XLS, PPT` "requires LibreOffice"
  ([`supported_formats.md#L13`](https://github.com/docling-project/docling/blob/v2.124.0/docs/usage/supported_formats.md?plain=1#L13)).
  It gives no container recipe.
- Searching docling-serve issues and PRs for "libreoffice" returns only
  [#703](https://github.com/docling-project/docling-serve/issues/703) and its open duplicate
  [#704](https://github.com/docling-project/docling-serve/issues/704), which are about something else (§6). Nobody has
  asked to add it to the image, and nobody has discussed doing so.

**The only route is a derived image**: `FROM docling-serve-cpu:<tag>` followed by a `dnf install`.

## 3. The derived image: packages, size, concurrency

**Base and packages.** The base is `quay.io/sclorg/python-312-c9s:c9s`, which is CentOS Stream 9
([Containerfile L1](https://github.com/docling-project/docling-serve/blob/v1.32.0/Containerfile#L1)). **MEASURED:**
AppStream offers `libreoffice-*` at **7.1.8.1** (`-16.el9`). This Containerfile built in 44 s:

```dockerfile
FROM quay.io/docling-project/docling-serve-cpu:v1.32.0
USER 0
RUN dnf -y install --nodocs --setopt=install_weak_deps=False \
      libreoffice-core libreoffice-writer libreoffice-calc libreoffice-impress \
 && dnf -y clean all && rm -rf /var/cache/dnf
USER 1001
```

After the build, `/usr/bin/libreoffice` is on `PATH` and reports `LibreOffice 7.1.8.1`.

**Size. MEASURED** (`docker images`, `docker image inspect`, `docker history`):

| | value |
|---|---|
| packages pulled | 115, 205 MB download, 675 MB installed (dnf's report) |
| new layer | 714 MB |
| image on disk | 7.62 GB → 8.61 GB (+~1.0 GB) |
| image content size | 2.23 GB → 2.50 GB (+~0.28 GB) |

**Real `.xls` converts. MEASURED.** All 7 `.xls` files the live run refused came back `status: success`. Sequentially,
round trips were 2.0–2.2 s, the usual 2 s tick. The exception was the 15.9 MB `datiTecnici.xls`, which took 41.6 s
(27 s `processing_time`) and produced 24 tables and 23.5 M characters of Markdown. The synthetic `.doc`, a heading,
a paragraph and a 2×2 table, converted to the expected heading, paragraph and table. It was produced by LibreOffice
itself, so it only shows that the pipeline runs end to end. It says nothing about fidelity on Word-authored files.

**How many soffice processes run at once.** Source: docling-serve runs `DOCLING_SERVE_ENG_LOC_NUM_WORKERS` workers,
**default 2** ([`settings.py#L187`](https://github.com/docling-project/docling-serve/blob/v1.32.0/docling_serve/settings.py#L187),
[`configuration.md#L279`](https://github.com/docling-project/docling-serve/blob/v1.32.0/docs/configuration.md?plain=1#L279)).
Each worker takes one task and converts it in a thread via `asyncio.to_thread`
([jobkit 3.5.0 `worker.py#L129`](https://github.com/docling-project/docling-jobkit/blob/v3.5.0/docling_jobkit/orchestrators/local/worker.py#L129)).
So with ADR-140's eight in flight, **at most two soffice processes run and the other six requests wait**.
**MEASURED**, by firing 8 conversions at once and sampling live `soffice.bin` processes (`ps`, zombies excluded) every
0.2 s:

| sidecar config | peak live `soffice.bin` | wall for 8 | outcome |
|---|---|---|---|
| default (2 workers), `--init` | 2 | 5.5 s | 8/8 success |
| `ENG_LOC_NUM_WORKERS=8`, `--init`, 3 runs | 8 (runs 2–3) | 11.8 s cold, then 3.5 s, 3.5 s | 24/24 success |

**The shared-profile problem does not arise.** LibreOffice needs write access to its profile, and
`-env:UserInstallation` is the documented way to give each instance its own
([LibreOffice help, start parameters](https://help.libreoffice.org/latest/en-US/text/shared/guide/start_parameters.html)).
Docling already passes a fresh profile on every call (§1). The 8-way run above had no failures.

**Zombie processes accumulate. MEASURED, and the one operational defect found.** The stock image runs `docling-serve`
as PID 1, with no init process. After 31 conversions the container held **31 `soffice.bin <defunct>` zombies** with
PPID 1, one per conversion, none of them reaped. Restarted with `docker run --init`, so that `docker-init` is PID 1,
the count was 0. *Inference:* without an init process, a long curation run leaks one PID-table entry per legacy file.
The fix is `init: true` on the compose service, which Docker supports natively.

## 4. Alternatives

| option | what it is (source) | fidelity for Docling (inference unless marked) |
|---|---|---|
| **Separate unoserver sidecar** | Long-running LibreOffice behind XML-RPC. The README says to run "several unoservers with different ports" for parallelism, with load balancing left to the user, and that it must share LibreOffice's Python ([README](https://github.com/unoconv/unoserver)). | Same LibreOffice filters, so the same output as the in-image route. Adds a service, a second network hop, and a new Stage 2 step (convert, then send the OOXML to Docling). Faster per call than a cold soffice. |
| **Gotenberg** | The LibreOffice route outputs **PDF only**, from inputs including `.doc`, `.xls` and `.ppt` ([docs](https://gotenberg.dev/docs/convert-with-libreoffice/convert-to-pdf)). | **Worse for spreadsheets.** Docling would receive a paginated PDF and rebuild tables through layout and table-structure models, instead of reading cells from openpyxl. For `.doc` the result is acceptable but it runs the heavy PDF pipeline. |
| **JODConverter** | Java library that drives LibreOffice, in local or remote mode, with a Spring Boot starter ([repo](https://github.com/jodconverter/jodconverter)). | Same filters. Local mode needs LibreOffice installed where Vespera runs, which breaks ADR-011's rule that the tool owns its sidecars. Remote mode needs a LibreOffice server anyway. |
| **xlrd** (Python) | Reads `.xls` only. It ignores charts, pictures and formulas (it keeps their cached values), and it cannot open password-protected files ([docs](https://xlrd.readthedocs.io/en/latest/)). | Good cell fidelity, since it read the same non-empty cells as Docling via LibreOffice (§5). But it is Python and would have to live inside a sidecar Vespera does not build. |
| **antiword / catdoc** | C text extractors for `.doc`. | Text only, with no table structure. No primary source was checked for their maintenance status, so it is not claimed here. |
| **Apache POI / Tika, in-process** | Pure Java. POI calls HWPF (`.doc`) "moderately functional" and "an orphan child waiting to be adopted" ([POI HWPF](https://poi.apache.org/components/document/index.html)). HSSF handles `.xls`. | **This bypasses Docling**, so the result would not be a `DoclingDocument`. It gives a second extraction path with its own output shape, cache identity and failure semantics (ADR-070/139). Spreadsheet cell fidelity would be good. Word table fidelity is the weak point. The parsing runs in Vespera's own JVM, where a parser bug is Vespera's crash. |

## 5. Conversion fidelity for Docling's extraction

**xls→xlsx: MEASURED as lossless on cell text for this corpus.** Method: each file was read with `xlrd` 2.0.2 directly
from the `.xls`, and its non-empty cells were compared with the non-empty `table_cells` in Docling's JSON from the
derived image.

| file | xlrd non-empty | Docling non-empty | text cells recalled |
|---|---|---|---|
| tipoStatoTerm | 22 | 22 | 12/12 |
| tipoTermConnFep | 72 | 72 | 56/56 |
| SLA_Report | 13 869 | 13 869 | 6 169/6 169 |
| SLA_time2019_05 | 13 340 | 13 340 | 6 675/6 675 |
| SLA_time2019_05capoluoghi | 17 706 | 17 706 | 10 901/10 901 |
| datiTecnici | 992 644 | 992 667 | 950 769/950 769 |

Every text cell survived. The 23 extra cells on `datiTecnici` were not explained, and Docling split its single sheet
into 24 tables. Numeric values were counted but not compared value by value. Dates came out as `2010-12-13 00:00:00`.
*Inference:* the XLS path is robust for tables because Docling reads the xlsx through openpyxl as a cell grid.
LibreOffice layout differences never enter it.

**doc→docx: not measured on real files**, because the corpus has none. *Inference:* Docling's DOCX backend walks
paragraphs, styles, lists and tables. Whatever LibreOffice's Writer import cannot represent (some text boxes, fields,
complex floating layouts) arrives flattened or missing. Headings survive only when the source used real heading styles.
The synthetic round trip in §3 is not evidence either way.

## 6. Security of running LibreOffice on untrusted archive files

**Macros.** The default profile in the derived image has `MacroSecurityLevel=2` (High) and
`DisableMacrosExecution=false` (**MEASURED**, read from `/usr/lib64/libreoffice/share/registry/main.xcd`). Under High,
signed macros from any source may run ([LibreOffice help, macro security](https://help.libreoffice.org/latest/en-US/text/shared/optionen/macrosecurity_sl.html)).
v2.124.0 seeds nothing into its throwaway profile, so this default applies. v2.130.0 raises it to Very High and
disables macro execution (§1).

**Import-filter and link CVEs are frequent.**
[LibreOffice's advisories](https://www.libreoffice.org/about-us/security/advisories/) list, in 2026 alone:

- CVE-2026-8356, a stack overflow in PPT import;
- CVE-2026-8358, a heap overflow in spreadsheet tracked-changes import;
- CVE-2026-8357, a heap overflow in Calc formula compilation;
- CVE-2026-4430, AgileEngine (OOXML encryption);
- several EMF, WMF and OOXML import overflows;
- two URL-exfiltration issues, CVE-2024-12426 and CVE-2026-63278.

**The package on offer is 7.1.8.1, maintained by backports.** Source: the package changelog
(`rpm -q --changelog libreoffice-core`, **MEASURED**) lists fixes for CVE-2026-8357, CVE-2026-4430 (both July 2026),
CVE-2025-1080 and CVE-2024-6472. It does **not** list CVE-2026-8356, CVE-2026-8358, CVE-2024-12425 or CVE-2024-12426.
Whether 7.1 is affected by those was **not established**. Red Hat deprecated the LibreOffice RPMs in RHEL 9 and
dropped them from RHEL 10
([RHEL 9.3 deprecated functionality](https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/9/html/9.3_release_notes/deprecated-functionality),
[RHEL 10.0 removed features](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/10.0_release_notes/removed-features)).
*Inference:* if docling-serve moves its base to c10s, `dnf install libreoffice` stops working, and the derived image
would have to install The Document Foundation's own RPMs instead.

**Sandboxing, for the options in this record.**

- The sidecar already runs as UID 1001 in a container that holds nothing else of value.
- The `.xls` files are copied into the container per request. The archive is never mounted.
- Compose can add `read_only` with a tmpfs `/tmp`, `cap_drop: [ALL]`, `security_opt: no-new-privileges`, a `pids_limit`,
  and `network_mode`/`internal` so the sidecar cannot reach the internet.

That last point is the direct answer to the URL-fetch and exfiltration class. *Inference:* the in-process POI route has
none of this isolation.

**Upstream misdetection risk (#704).** The issue reports that attachment-bearing `.msg` files are detected as XLS and
sent through LibreOffice as a false success. **MEASURED:** three `.msg` files from this archive, sent as `.bin` the way
ADR-100 sends unsubtyped OLE, came back `skipped` ("File format not allowed") on both the stock and the derived image.
Sent as `.msg`, they converted as `message/rfc822`. #704 was not reproduced here, and ADR-100's naming keeps unsubtyped
OLE away from the XLS route.

---

## Recommendation (non-binding, input to a future ADR building on ADR-100)

The inputs differ in how they were established:

- **Measured:** the evidence favours LibreOffice in a derived sidecar image.
- **Upstream source:** the timing favours doing it with the v1.35.0 bump rather than on v1.32.0.
- **Inference:** the POI route's cost is inferred. Nothing in it was measured.

1. **Provision it as a derived image, not an alternative converter.** It turned 7/7 real refusals into successes with
   full cell-text parity, needs no Vespera code change, and keeps ADR-011's ownership of sidecars. Gotenberg loses
   spreadsheet structure. POI or Tika would add a second extraction path with its own output shape, which costs more
   than 8 files in 129 justify. unoserver buys speed that the 2 s tick hides.
2. **Pair it with the docling-serve v1.35.0 bump (docling v2.130.0), or accept the v2.124.0 call knowingly.** Only
   v2.130.0 disables macros, turns off link updates and kills the whole process group on timeout. On v1.32.0 the
   profile default (High) applies and a timed-out `soffice.bin` can survive. ADR-100 already names a sidecar upgrade as
   the event that invalidates its findings, so that record needs re-reading either way.
3. **Required alongside:**
   - `init: true` on the service, because zombies otherwise accumulate one per conversion;
   - no outbound network for the sidecar;
   - optionally `ENG_LOC_NUM_WORKERS=8`, to match ADR-140's width (measured 8-way clean).
4. **Account for the side effects in the ADR:**
   - DOCX DrawingML and XLSX EMF/WMF content starts to render through LibreOffice, so some output that already
     converts changes.
   - The extractor identity should gain a marker so that cached rows from before LibreOffice are invalidated.
   - The derived image depends on a deprecated RHEL 9 package line, 7.1.8 maintained by backports. Several 2026
     import-filter CVEs are absent from its changelog, which argues for tracking that changelog, or for TDF's RPMs if the
     base moves.
