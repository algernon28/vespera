# ADR-086 — Seed/corpus mismatch is measured before the model gate, and reported rather than enforced

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none — discharges the question [ADR-064](0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md) explicitly parked for "whichever future ticket specifies stage 5", and narrows the signal list [ADR-064](0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md) and [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md) both named

## Context

The failure this exists to catch is the expensive one: **scoring an entire archive against a seed set that was never comparable to it**. Italian corpus against English seeds; clean born-digital seeds against a corpus of scans; thirty-page seed reports against two-page memos. Each drags every score toward the floor, and the result reads as a corpus with nothing relevant in it rather than as a comparison that was never meaningful.

ADR-064 parked the question by name, having established that it could not be answered with stage 0's instrument: "the signals in question (OCR-error rate, chunk count, language mix) don't exist until stage 2/3 are built." [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md) then built them, and found that one of the three does not exist at all — **Docling reports no OCR-error rate**, only a confidence *in* the OCR — so it stored the counters a definition of one can be computed from instead. [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) supplied the other half by giving seed extraction to stage 5.

### The interaction that shapes the answer

**This measurement needs no embedding model.** It reads `extraction_metric` columns on both sides, and every one of them exists after ADR-083's seed extraction and before a single vector is computed.

But [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) gates stage 5 on the model and mints no run when it is unset. Taken naively, that means **the operator who has not yet chosen a model — precisely the person who most needs to know their seed set does not match — never sees this report**, and discovers the mismatch only after paying to embed the corpus.

`CONTEXT.md`'s own **Gate** entry licenses the fix rather than forbidding it: "leave it unset and the run ends there, *having recorded everything it learned*."

## Decision

### It is a measurement and a report: never a verdict, never a gate, never a block

Nothing here removes a document or stops a run.

A mismatch is not an absent value, and `CONTEXT.md` defines a gate as "a value the pipeline requires and does not have" — the seed folder is set, the walk finished, the documents are extracted. This is ADR-083's argument, which that decision records having lost before it won.

The strongest counter-argument — that a seed set in a different language is not a judgement call — does not survive contact with the data it would act on. `primary_language` is a *detected* value carrying a confidence beside it and a documented guard below which `LanguageDetection` declines to guess at all; an Italian-and-English archive is the stated target domain; and OCR damage skews detection precisely on the documents most likely to be misread. Gating would refuse an expensive run on a heuristic's say-so, and refusing overrides the one person who knows the domain — which is what the seed set exists to express.

### The signals compared, all from `extraction_metric`

The corpus side is **survivors** — occurrences carrying no blocking verdict — not every occurrence, because what matters is whether the seeds resemble what would actually be scored.

| Comparison | Read from |
|---|---|
| **Language mix** — proportion by `primary_language` on each side, with documents whose detection declined counted as their own undetermined share rather than dropped | `primary_language`, `language_confidence` |
| **Born-digital against converted** — the proportion of rows whose confidence was never computed | the **null-ness of `mean_score`**: a `.docx` or `.txt` gets no confidence score, a converted PDF or scan always does (ADR-070's "null means not measured, never poor") |
| **Length** — median and quartiles of `word_count`, and of `page_count` where it is non-null | `word_count`, `page_count` |
| **OCR damage proxies** — median `vowelless_word_count / word_count` and `single_character_word_count / word_count` | ADR-073's garbage-text counters, which exist because no OCR-error rate does |

Medians and quartiles rather than means: a single 900-page scan in a seed folder of memos moves a mean and not a median, and the question being asked is what the *typical* document on each side looks like.

**Chunk count is deliberately dropped**, though ADR-064 and ADR-073 both named it. It is word count seen through a tokenizer, so it carries no information the word counts do not, while dragging in the caveat that counts are comparable only within one tokenizer identity (ADR-044, ADR-084) — a caveat that would have to be explained in a report whose whole purpose is to be read quickly by someone deciding whether to spend a day of compute.

### The report states each comparison in words, and stops short of judging it

Not two histograms side by side, which is where a mismatch hides rather than where it shows; and not a single mismatch score, which would be a threshold nobody has measured — the guess **observe before enforce** exists to refuse — and would compress the one thing that can be acted on, *which* signal diverged, into a number that cannot.

So each comparison is stated plainly with both figures present — *"91% of the seed set is English; 4% of the corpus is"* — and the report does not say whether that is acceptable. That is the operator's judgement, and it is the judgement the seed set exists to carry.

### Two outputs, in ADR-075's shape

- **Re-analyzable data**: a table carrying the computed comparison, keyed by this measurement's own run id. Per [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md), a re-run writes a **fresh row set under a new run id** rather than overwriting the old one.
- **One self-contained HTML file**, written beside the database and `profile.yaml` (ADR-054's placement rule — never inside the corpus), hand-assembled without a templating library, exactly as `ConfidenceDistributionReport` already is (ADR-046).

**No new `Profile` pointer**, and this is where the shape departs from ADR-075. That decision added `withDegenerateOutputConfidenceFloorMeasurement` because a *threshold* had to be calibrated from the report. Nothing here is a threshold: no profile key reads this measurement, and ADR-075's own limit — declining to pre-compute summaries nothing consumes — applies to pointers as much as to data. `seedFolder`'s existing pointer at the seed walk answers a different question ("were your seeds found"), and repointing it every stage-5 run only to have census repoint it back on the next invocation would make `refreshedAt` mean nothing.

### It runs before the model gate, under its own run id

Stage 5 mints **two runs, not one**:

1. **A measurement run**, covering ADR-083's seed extraction pass and this comparison. Minted whenever the seed folder is set and its walk has finished, with stage 2's run upstream. This is the run that writes the table and the report.
2. **A scoring run**, minted only once the embedding model is set, with the measurement run upstream.

An invocation against an unset model therefore walks, extracts the seeds, measures the mismatch, writes the report, and ends at the gate **having recorded what it learned** — while ADR-080's rule holds unbroken, because no run row exists for the scoring that did not happen.

The two alternatives were weighed. Folding the comparison into stage 3 would require moving seed extraction earlier and reopening ADR-083, settled two tickets ago. Giving it a stage of its own contradicts `CONTEXT.md`, where a stage is "identified by the verdicts it writes" and this one writes none.

## Consequences

**The report arrives before the money is spent, which was the whole point.** ADR-064 worried that a shape mismatch "silently distorts ADR-020's relevance score by measuring format rather than topic". An operator now learns that their seeds are born-digital English memos and their corpus is Italian scans *before* embedding, and before naming a model.

**Seed extraction happens even on a gated invocation.** A few dozen Docling calls against a seed folder, against the corpus-wide extraction already paid for at stage 2 — and content-addressed, so a seed that is also a corpus member costs nothing (ADR-083). That is the price of having the report early, and it is small.

**Stage 5 has two run ids, and everything downstream reads the second.** The measurement run is upstream of the scoring run, so a changed seed set changes both; the reverse is not true, and a re-run under a new model leaves the measurement untouched.

**This report cannot catch a mismatch of *topic*.** Every signal here is about form — language, length, provenance, OCR damage. A seed set of pristine Italian inspection reports scored against a corpus of pristine Italian purchase orders passes every comparison in this report and is still the wrong seed set. That failure is what the score distribution itself reveals ([ADR-028](0028-relevance-threshold-human-labelling-gated-by-score-distribution.md)'s go/no-go on distribution shape, [#83](https://github.com/algernon28/vespera/issues/83)), and saying so here is what keeps this report from being trusted for more than it measures.

**ADR-064's parked question is discharged**, and one of its three named signals turned out not to exist. The record now says which signals replaced it and why, rather than leaving a list nobody reconciled.

**Nothing here fixes the tables.** What the comparison table's columns are, and how the step is wired alongside the seed extraction pass, are the hand-off spec's ([#85](https://github.com/algernon28/vespera/issues/85)) — the same deferral every prior slice made.
