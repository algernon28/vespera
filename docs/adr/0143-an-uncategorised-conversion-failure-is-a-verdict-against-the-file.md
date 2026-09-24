# ADR-143 — An uncategorised conversion failure is a verdict against the file

- **Date**: 2026-09-24
- **Status**: accepted
- **Amends**: [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md). Its §"A service-scope failure fails the step and writes nothing" lists `unknown` among the categories that are "never `extraction-failed`" and says "`unknown` sits on this side deliberately". Both statements are reversed here. The other three categories stay where they were.
- **Amends**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md), whose list of categories skipped without retry loses `unknown`. The breaker, its count and what resets it are unchanged.
- **Amends**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md). The case its §Context was written about no longer reaches the fault row. Its Consequences name "exactly five" categories that can leave a fault row, and there are now four. Its mechanism is untouched.
- **Amends**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §5, which names `unknown` among the categories that can leave a fault row.
- **Rests on**: two runs of the packaged jar from `main` at `88f5599` on 2026-09-24, against the stock `docling-serve-cpu:v1.32.0` image with no LibreOffice, and a read-only survey of walk order over the full corpus. All three are described under §Context.

## Context

Docling answers a legacy `.xls`, `.doc` or `.ppt` it cannot open with `status` `failure` and one error categorised `unknown`: *"An unexpected error occurred while opening the document document.xls."* ADR-070 reads `unknown` as no evidence about the file and routes it to service scope. So the occurrence is skipped, and each skip counts toward ADR-071's breaker, which stops the step after five in a row. ADR-139 then turns the skip into `extraction-failed` at the end of the step, if the step completed.

The refusal is a property of the file. It comes back in an ordinary HTTP 200 response, identically on every call and every run, and when the sidecar is broken it does not look like this: a sidecar that is down returns no response (a transport failure, which fails the step outright), one that is stuck times out (ADR-071's own streak), and one that knows it is at fault says `capacity`, `target_unavailable` or `internal`. Routing the refusal to service scope therefore bought nothing, and it cost the run:

- **Five in a row stop the invocation.** Stage 2 run over `A&RD_SmartPOS_BaseDocumentale\DossierONLINE\DossierONLINE\Test_InvioDatiTecniciOnline`, a folder holding five `.xls` files with no byte-identical copy anywhere in the corpus, ended with `circuit breaker tripped after 5 consecutive service-scope failures` and exit code 1. The same happened on the 158 legacy files copied out of the corpus. The breaker read five unopenable spreadsheets as a dead sidecar.
- **The full corpus reaches that count.** Walked in the order the census walks it, the 43,101 files of `QA - DIGITAL - Recupero KT PERIN` hold exactly one run of legacy files five long, and that is the folder above. The baseline run of 2026-09-23 got through only because its legacy files never came more than three in a row.

The operator's rule settles it: a file Docling fails to parse is a verdict, and it is not investigated further unless the failure is plainly a bug on our side.

## Decision

**A `failure` or `skipped` response whose errors are all `unknown`, or which carries no error at all, earns `extraction-failed` at once.** It is the same outcome as `backend_failure` and gets the same treatment: a metric row, and a reason composed as `category: message`. A failure reporting no error at all reads `unknown: no categorized error was reported`.

**`unknown` joins the conditional pair, `policy` and `source_unavailable`, and follows the same rule.** It is document scope unless a genuine service-scope category — `capacity`, `target_unavailable` or `internal` — appears in the same response, in which case the whole response is about the sidecar and the occurrence is skipped as before. `unknown` alongside such a category is skipped, and `unknown` alongside `policy` is a verdict that names the `policy` error, because a named refusal says more than an unexplained error beside it.

**The breaker is unchanged.** It counts service-scope skips, and those now come only from the three categories above and from timeouts once ADR-071's timeout streak has flipped them. A run of files Docling cannot open writes a run of verdicts and never reaches the breaker.

**Nothing changes on the seed side.** A seed Docling cannot open is already an unusable seed (ADR-083), with no skip and no breaker.

## Consequences

**A folder of files Docling cannot open costs those files and nothing else.** `ExtractionItemProcessorTest.verdictsEveryUncategorisedFailureInARow` drives one more such file than the breaker's count through the processor, and each one is a verdict. `ExtractionFaultInvocationTest.refusedConversionsInARowDoNotStopTheInvocation` drives the same through the whole job, and the invocation exits 0 with every file removed and the readable file beside them scored.

**A sidecar that reported a bare `unknown` for every file would condemn the corpus instead of stopping.** That is the over-blocking ADR-070 was written against, and this decision accepts it on the evidence above: no failure mode of the sidecar measured so far looks like that. The removal is also findable and reversible. Each verdict's reason begins `unknown:`, so a query finds every one, and deleting them and re-running stage 2 is the retune path the ledger already has.

**Adding a converter that can open these files needs that same retune.** A verdict outlives its run (ADR-139, Consequences), so a derived image with LibreOffice converts nothing already marked `extraction-failed` until those verdicts are deleted.

**Fewer occurrences reach ADR-139's fault row, and the confidence-distribution page says so.** The page's line counted fault rows under the words "refused to open", which would now describe the wrong files. It now reads "could not answer for", and names the two causes left: a fault the converter reported against itself, or a run of timeouts. `ExtractionFaultInvocationTest` pins ADR-139's path with an `internal` fault instead of the `unknown` refusal it was written around.
