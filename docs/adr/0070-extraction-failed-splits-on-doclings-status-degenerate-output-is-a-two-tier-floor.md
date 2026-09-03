# ADR-070 — `extraction-failed` splits on Docling's status; `degenerate-output` is a two-tier floor

- **Date**: 2026-09-02
- **Status**: accepted
- **Amends**: none (narrows how ADR-010's "can fail silently" is to be read, the same move ADR-068 made on ADR-067's boundary)

## Context

ADR-057 closed the verdict vocabulary with two blocking values for stage 2 — `extraction-failed` and `degenerate-output` — and neither had a detection mechanism behind it. ADR-010's summary and `docs/architecture.md` §1.2 both say extraction "can fail silently," which reads as though a failed conversion could come back indistinguishable from a good one. Nothing in the record said which of the two verdicts a given Docling outcome earns, what "degenerate" means operationally, or whether ADR-068's line about Apache POI (`OPCPackage.open` would catch OOXML relationship corruption, "but that gap is stage 2's `extraction-failed` territory") committed stage 2 to running POI.

**What a Docling conversion response actually reports** was read off `docling` and `docling-serve` on `main` (`docling/datamodel/base_models.py`, `docling/datamodel/service/responses.py`). One response carries three independent signals:

1. **`status`** — `ConversionStatus` ∈ `pending` · `started` · `failure` · `success` · `partial_success` · `skipped`.
2. **`errors[]`** — `ErrorItem`, each with `component_type`, `module_name`, `error_message`, `page_no` (1-indexed, null for a document-scoped error) and a `category` ∈ `FailureCategory`. That enum's own docstring splits it by scope: **task/service scope only** — `capacity`, `target_unavailable`, `internal`; **document/page scope only** — `backend_failure`, `inference_failure`; **shared** — `policy`, `source_unavailable`, `timeout`. `unknown` is the default for an uncategorised error, deliberately distinct from `internal` ("a known service defect").
3. **`confidence`** — `ConfidenceScores`: `parse_score`, `layout_score`, `table_score`, `ocr_score`, `mean_score`, `low_score` as nullable floats (NaN serialised as null), plus `mean_grade` and `low_grade` ∈ `QualityGrade` (`poor` · `fair` · `good` · `excellent` · `unspecified`). Docling's own score-to-grade cut-offs are `< 0.5` poor, `< 0.8` fair, `< 0.9` good, `>= 0.9` excellent.

Two facts in that reading shaped the decision. **Confidence is page-derived**: the aggregation filling those scores lives in the paginated (PDF/image) pipeline, so a document converted through the simple pipeline — `.docx`, `.txt` — comes back with null scores and `unspecified` grades. A confidence rule has nothing to say about those formats. And **a response is never silent about failing**: it carries a status, a categorised error list and a quality snapshot. What it can be quiet about is whether the *text is right* — a scanned page that OCRs into plausible-looking garbage returns `success`.

The remaining tension is that a verdict blocks (ADR-057: every value except `passed` blocks, and none blocks conditionally), so writing a verdict against an occurrence because the sidecar was at capacity would remove a good document from the survivors on the strength of a service outage. ADR-042's asymmetry argument runs the other way here: over-blocking loses archive, and it loses it invisibly, because a blocking verdict looks exactly like a judgement about the file.

## Decision

**The two verdicts split on `status`, and a service-scope failure is not a verdict at all.**

### `extraction-failed` — the call failed on this document

Written when the response's `status` is `failure` or `skipped` **and** the failure is document-scoped, judged by its `errors[]` categories:

- **Document/page scope** — `backend_failure`, `inference_failure` — is `extraction-failed`. This is where ADR-068's accepted gap lands: a `.docx` with a valid zip container and a corrupt `document.xml` passes stage 1 and arrives here as a `backend_failure`.
- **Shared-scope categories** resolve per occurrence: `policy` and `source_unavailable` are `extraction-failed` when they are properties of this file (an unsupported or unreachable source), and a service-level cause is treated as service scope below.
- **`timeout` is resolved per-occurrence-versus-consecutive.** A timeout on one occurrence while its neighbours convert is document scope — the document is too pathological for the configured budget — and is `extraction-failed`. Timeouts arriving consecutively across occurrences are a statement about the sidecar, not about any file, and are service scope.

### A service-scope failure fails the step and writes nothing

**`capacity`, `target_unavailable`, `internal` and `unknown` are never `extraction-failed`.** They say nothing about the occurrence. They surface as a failure of the extraction step — retry, backoff and skip policy being Spring Batch's job (`docs/architecture.md` §1.6) and the exact policy belonging to this map's Docling-invocation-contract ticket — and leave the occurrence with no verdict row at all, so a later run examines it again (ADR-049's resume predicate distinguishes "examined, fine" from "not yet examined"). `unknown` sits on this side deliberately: an uncategorised error is not evidence about a document, and the safe reading of no evidence is "not judged yet."

### `partial_success` passes through to the floor

**`partial_success` is not `extraction-failed`.** A document some of whose pages failed may still carry usable text, and it is judged on what it produced, not on the fact that something failed inside it. It goes to the degeneracy floor like any `success`. The errors it carried are recorded — in the verdict row's free-text `reason` when a verdict is written (ADR-057), and in stage 3's derived-metric columns regardless — but they do not block on their own.

### `degenerate-output` — a two-tier floor over a converted document

Reachable only from `status` of `success` or `partial_success`.

- **Tier 1 — the hard zero-content floor, enforced now.** Extracted text that is empty, or carries no alphanumeric content once whitespace is normalised, is `degenerate-output`. This tier has no threshold to calibrate: zero is not a tuned number, so "observe before enforce" does not apply to it, and it is the whole of what stage 2 enforces on day one.
- **Tier 2 — a confidence-based quality floor, shipped unset.** A profile threshold over Docling's `ConfidenceScores` (`mean_score` / `low_score`, or their grades) that, when set, makes a low-confidence conversion `degenerate-output`. It ships **unset**, per `CONTEXT.md`'s **observe before enforce**: the score distribution over this corpus has never been measured, and stage 2 is the pass that measures it. Docling's own cut-offs (`< 0.5` poor, `< 0.8` fair, `< 0.9` good, `>= 0.9` excellent) are recorded here as the reference scale a future value can be stated against, **not adopted** as that value.
- **A null score reads as "not measured", never as "poor".** Because confidence aggregation is page-derived, `.docx` and `.txt` arrive with null scores and `unspecified` grades. A null never crosses tier 2's floor, whatever it is eventually set to; those formats are judged by tier 1 alone. Reading a null as a zero would condemn every `.docx` in the archive on the strength of a pipeline detail.

### ADR-068's POI line is a boundary marker, not a mechanism

**Apache POI is not invoked in stage 2, and no secondary validity check is.** ADR-068's sentence named which verdict a POI-detectable defect falls under; it did not adopt POI, at stage 1 or here. Stage 2 runs one Docling conversion per occurrence and reads its three signals — a valid-container/corrupt-content `.docx` is caught because Docling reports `failure` with a `backend_failure`, not because anything checks OOXML relationships a second time. ADR-046 stands: no new dependency, because none is needed.

### Confidence scores are persisted, as ADR-019 columns

**Stage 2 writes the `ConfidenceScores` snapshot as derived-metric columns while the document is open** — ADR-019's mechanism exactly ("not a second traversal"). This is an obligation, not an option: tier 2 cannot be calibrated later from data that was never stored, and re-converting hundreds of gigabytes to recover a number the first pass already had in hand is not a recovery plan. **The exact column list is left to this map's derived-metrics ticket** ("What extraction must produce for stage 2/3's deferred mismatch-detection question", #38), which is deciding that set as a whole; this ADR fixes only that the confidence snapshot is in it.

### The two verdicts are mutually exclusive by construction

`extraction-failed` is decided on `status` before any extracted text exists; `degenerate-output` is only reachable once text does. No occurrence can earn both, and neither blocks conditionally — ADR-057's rule holds without a special case.

### How ADR-010's "can fail silently" should be read

This ADR amends nothing formally, and narrows one phrase. **A Docling call is never silent about failing** — it returns a status, a categorised error list and a confidence snapshot. It is silent only about **whether the text is right**: OCR that produces plausible-looking garbage returns `success`. That is the one silence in the stage, it is exactly what `degenerate-output` exists to catch, and it is why tier 2 has to be measured rather than assumed. Read ADR-010's phrase as being about output fidelity, not about failure reporting. This is the same move ADR-068 made when it restated ADR-067's boundary from the other side: no substance of the earlier decision changes, its wording is made precise now that the mechanism behind it is known.

## Consequences

**Stage 2's day-one behaviour is narrow on purpose.** Only `status` and the hard zero-content floor block anything. The archive is not filtered on quality until a measured run says where the floor belongs, which is the same shape census took with every threshold it left unset.

**A sidecar outage costs throughput, not archive.** Service-scope failures leave no rows, so the occurrences involved are simply unexamined and a later run picks them up. The cost is that "how much of the corpus has stage 2 actually looked at" is a question about absent rows rather than a count of verdicts.

**`timeout`'s per-occurrence-versus-consecutive resolution needs a rule with a number in it** — how many consecutive timeouts read as a sidecar problem. That number is the Docling-invocation-contract ticket's (#38), alongside the timeout budget itself; this ADR fixes only which side of the split each reading falls on.

**The extraction cache gains a reason to store the full response, not just the document.** Status, errors and confidence are all judged, and re-deriving them means re-converting, so what is cached under extractor identity (ADR-010) has to include the signals the verdicts rest on.

**Tier 2 is a profile key with no value**, pointing at the measurement that should inform it (`CONTEXT.md`, **observe before enforce**). Its type, name and place in `profile.yaml` (ADR-061, ADR-062) are the stage-2 hand-off spec's to write.

**Nothing here specifies call sites.** Which Docling endpoint is called, how the response is deserialised, and the exact predicate for "no alphanumeric content" are the hand-off spec's, the same deferral ADR-068 and ADR-069 made to the stage-1 spec.
