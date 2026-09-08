# ADR-092 — The seed side of the mismatch comparison is measured by seed extraction, under the measurement run

- **Date**: 2026-09-08
- **Status**: accepted
- **Amends**: [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md) — one premise of it: that both sides of the comparison can already be read out of `extraction_metric`. Everything ADR-086 decided about *what* is compared, and about the comparison being a report rather than a verdict, stands unchanged.

## Context

ADR-086 settled the seed/corpus mismatch comparison on the strength of a premise it stated plainly: *"This measurement needs no embedding model. It reads `extraction_metric` columns on both sides, and every one of them exists after ADR-083's seed extraction and before a single vector is computed."* The stage-5 hand-off spec ([#94](https://github.com/algernon28/vespera/issues/94) §0) restated it as settled fact — *"`extraction_metric` is shipped with everything §4's comparison reads"* — and [#106](https://github.com/algernon28/vespera/issues/106) repeated it once more: *"Everything the comparison reads already exists in the extraction metrics table."*

**Half of that is true.** The corpus side is exactly as described: stage 2's step writes an `extraction_metric` row for every corpus occurrence it converts, carrying `primary_language`, `language_confidence`, `mean_score`, `word_count`, `page_count`, `vowelless_word_count` and `single_character_word_count` — every column ADR-086's table names.

**The seed side has no such row, and never did.** `SeedExtractionItemProcessor`, delivered under ADR-083 ([#104](https://github.com/algernon28/vespera/issues/104)), converts one seed through the content-addressed extraction cache, tests the result against tier 1's usability bar, and returns a `SeedExtractionOutcome` carrying an occurrence and possibly a reason. It writes no metrics row, on purpose and for a stated reason — *"nothing here needs a run"*, which is what lets the whole seed folder be extracted before the gate decides whether a run should exist at all. Nothing else writes one either: `ExtractionMetrics` is called from stage 2's step and from nowhere else.

So the comparison, written as ADR-086 describes it, would read a populated corpus side against an empty seed side, and report a seed set of no documents. The premise defect is what needs recording: not that the comparison is wrong, but that one of its two inputs was assumed into existence.

**Why this was easy to miss.** ADR-083's own reasoning is what removes the row. Seed extraction is deliberately run-free so that ADR-083's "no usable seed at all" gate can be answered *after* every seed has been converted and *before* any run row exists — and an `extraction_metric` row cannot be written without a `run_id`, because the column is `NOT NULL` and a foreign key into `run`. The two decisions are individually right and were written three days apart; the seam between them is where the row went missing.

## Decision

### Seed extraction writes the seed side's `extraction_metric` rows

The pass that has the document open is the pass that measures it. `ExtractionMetrics.write` — the entry point that records a row and judges nothing — is called for every seed the extractor answered for, from where the seed pass already writes its `unusable_seed` rows, which is the first point at which the run exists.

That placement is forced rather than chosen: the run is minted once the whole folder has been converted, so a per-document write is impossible without reopening ADR-083's gate ordering. What the pass holds until then is one small record per seed, against a seed folder of a few dozen to a few thousand documents.

**The comparison step was the alternative, and it is rejected.** It could recompute both sides' metrics at comparison time from `extraction_cache`, which holds every response verbatim and is content-addressed. Three things rule it out:

- **It is the second traversal ADR-019 and [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md) forbid.** A derived metric is written while the document is open. Recomputing it later is the shape those decisions exist to prevent, and the reason they give — that a corpus-wide pass should read stored values rather than re-derive them — applies whether the re-derivation reads the archive or a cache of it.
- **It would leave the seed set unmeasurable.** An operator could ask what the corpus looked like and not what their own seed folder looked like, and the mismatch report's numbers could never be checked against anything. ADR-075's re-analyzable-data half exists precisely so a report's figures have a queryable source underneath them.
- **It would be a second implementation of one sentence.** `TextMetrics`, `LanguageDetection` and the confidence columns would have to be orchestrated a second way, which is the drift ADR-083 refused when it fixed the seed usability bar as *stage 2's* tier 1 rather than a copy of it.

### The rows are recorded under the measurement run

`extraction_metric` is keyed `(occurrence_id, run_id)`, and the measurement run ([ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md) for its upstream) is the only run a seed occurrence is ever measured under.

**Not stage 2's run**, and this is the load-bearing part. Stage 2's run identity is derived from the corpus root, the extractor identity and the tier-2 floor — it does not name the seed folder. Seed rows written under it would claim that pass measured documents it never saw, and two invocations with different seed folders would write different seed measurements under one identity: a primary-key collision where the seed sets overlap, and a silently mixed seed set where they do not.

Under the measurement run, [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md)'s rule delivers the property ADR-083 wanted for free: the seed folder is part of what the measurement run's identity is derived from, so a corrected seed folder is a different run, and its seed rows are a fresh row set beside the earlier one rather than an overwrite of it. Two seed sets can never be confused, by identity rather than by a check anyone has to remember. It is also the keying `unusable_seed` already uses, for the same reason.

**`extraction_metric` therefore has two writers under two runs, and that is not a defect.** The table's key never claimed a single writer, and the two populations are told apart by something structural: a seed occurrence belongs to the seed walk, a corpus occurrence to the corpus walk. Every existing reader filters by the run id it measured — `ConfidenceDistribution` by stage 2's, `RedundancyResolution` by stage 2's — so none of them can see a seed row.

### A seed's metrics row carries no verdict-shaped meaning, and never will

`ExtractionMetrics.write` is the entry point, never `writeAndJudge`: the tier-2 degeneracy floor is **not** applied to a seed, and a seed whose confidence would fail that floor is still a seed. This is ADR-083's asymmetry ([#79](https://github.com/algernon28/vespera/issues/79)) applied to the new rows rather than restated: every kind in the closed verdict vocabulary exists to remove a document from publication, a seed is never published, and an unreadable seed is an operator's problem to fix.

So, stated so no later slice reads it otherwise: **no stage may derive a verdict from a seed's `extraction_metric` row.** The `status` column on such a row records what the converter reported, which is a fact about a conversion and not a judgement about a document; nothing may turn it into `extraction-failed`, and no threshold may be applied to a seed's columns to exclude it from anything.

### The compared seed population is the seeds that would actually be scored

Two absences, two answers, and they are the same answer ADR-086 already gives the corpus side.

**A seed recorded in `unusable_seed` under this run is outside the compared population.** ADR-086's corpus side is survivors — *"what matters is whether the seeds resemble what would actually be scored"* — and only usable seeds are ever scored, because ADR-020's maximum is taken over the seeds that survived extraction. Its row stays in the table as evidence, and the seed itself is already reported by name with its reason, so nothing is hidden by leaving it out of a comparison of form.

`unusable_seed` is therefore read here the way `verdict` is read on the corpus side: **to choose a population for one measurement, and for nothing else.** It removes nothing, publishes nothing, and it remains not a verdict.

**A seed occurrence with no metrics row at all is outside the population too, and is counted.** This is the page-count rule of ADR-086 applied one level up: an absent measurement is not a measurement of nothing. A declined language detection is a measurement that was taken and answered "cannot say", which is why ADR-086 gives it an undetermined share of its own; no row means nothing was measured about that document, and a category for "never looked at" would put a fact about the run inside a comparison about documents' form.

But it is not left silent, because a seed set that quietly shrank is the failure [ADR-064](0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md) called fatal for the seed walk: **the report states how many seed occurrences carried no measurement, beside the population it compared.** A count in a report, not a gate — ADR-086's "never a gate" is untouched, and an operator who sees 30 of 40 seeds compared knows to look at the other ten.

## Consequences

**ADR-086's premise sentence is corrected rather than left standing.** "Every one of them exists after ADR-083's seed extraction" is true only once seed extraction writes the row this decision gives it. ADR-086's text is not edited — it is a record of what was decided on 2026-09-06 — and this file is what a reader of that sentence is pointed at.

**#106's spec gains one obligation it did not name**: the seed pass writes metrics rows. Its acceptance criteria are otherwise unchanged, and the four comparisons, the medians-not-means rule, the no-summarising-figure rule and the report's placement are all exactly as ADR-086 left them.

**Two of #106's pinning tests are added and one adjusted** to assert the population rules above — a seed recorded unusable is out of the comparison, a seed with no measurement is out of it and counted — and the comparison's value now carries that count beside the two populations. Nothing else about those tests moves: they already assumed the measurement run, and this record is where that assumption's reasoning now lives.

**`ExtractionSchema` does not bump.** No table shape changes; rows arrive in an existing table under a run id it already accommodates. `EmbeddingSchema`'s own bump for `seed_corpus_comparison` is #106's, unaffected by this.

**No module boundary moves.** `pipeline` already depends on `extraction` and already calls `ExtractionMetrics` from stage 2's step; the seed pass calls the same public seam. `embedding` still reads only stored columns, and still cannot see `Profile`.

**A seed that is also a corpus member now has two metrics rows, and that is correct.** The conversion is still paid for once — the extraction cache is content-addressed, which is ADR-083's whole point — but the same content measured as a corpus occurrence and as a seed occurrence is two occurrences under two runs, which is ADR-048's identity rule working rather than duplication.

**What this decision does not settle.** Whether the columns of `seed_corpus_comparison` are the right ones, and how the comparison's own step is wired, remain #106's — the same deferral ADR-086 made. Nor does it revisit ADR-091's finding that chunk count carries no information here: no chunking happens in the seed pass at all.
