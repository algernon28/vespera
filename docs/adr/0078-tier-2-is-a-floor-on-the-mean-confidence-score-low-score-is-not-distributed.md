# ADR-078 — Tier 2 is a floor on the mean confidence score; `low_score` is not distributed

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: ADR-070 (its tier-2 definition, which named `mean_score` / `low_score`, or their grades), ADR-075 (its Decision sentence, which named the report as a distribution over `mean_score`/`low_score`)

## Context

ADR-075's Decision names stage 3's report as *"a confidence-score distribution over `extraction_metric`'s `mean_score`/`low_score` columns."* Issue #59 shipped `mean_score` only, and recorded no narrowing. So the record and the code disagree, and the disagreement is not free to leave standing: `confidence_distribution`'s primary key is `(run_id, grade)`, with no column saying which score a bucket summarizes, so adding `low_score` later is a schema change, a new column and an `extraction` VERSION bump — not simply a second set of inserts.

**The ambiguity did not start in ADR-075.** ADR-070 defined tier 2 as *"a profile threshold over Docling's `ConfidenceScores` (`mean_score` / `low_score`, or their grades)"*, and ADR-075 inherited that openness when it named the report's inputs. Narrowing only ADR-075 would leave the source intact to re-seed the same question the next time someone specifies a threshold.

**What the code has already chosen, without a record.** `Profile.degenerateOutputConfidenceFloor` is a single scalar, and `DegeneracyFloor.evaluate` compares `metric.confidence().meanScore()` against it. There is no worst-page comparison anywhere, and no second threshold for one to read.

## Decision

**Tier 2 is a floor on `mean_score`. `low_score` informs no threshold, and stage 3 computes no distribution over it.** `confidence_distribution` keeps its `(run_id, grade)` key and gains no `score_kind` column. `low_score` and `low_grade` stay exactly as ADR-073 left them: stored, re-analyzable data with no threshold reading them.

Three grounds, in the order they decide it.

### A worst-page floor is a usability judgement, not a degeneracy one

Tier 2 sits inside `degenerate-output`, whose question is whether extraction produced usable text at all. `low_score` answers a different question — whether some *part* of the document converted badly — and answering it with a blocking verdict discards documents that converted fine. Two worked cases, on Docling's own scale (`< 0.5` poor, `< 0.8` fair, `< 0.9` good, `>= 0.9` excellent), against a hypothetical floor at 0.5:

| Document | `mean_score` | `low_score` | Mean floor @ 0.5 | Worst-page floor @ 0.5 |
| --- | --- | --- | --- | --- |
| 300-page scanned monograph, one fold-over page | 0.91 excellent | 0.22 poor | keeps | discards the whole book for one page |
| 40-page PDF, 6 scanned appendix pages over 34 clean digital-text pages | 0.83 good | 0.31 poor | keeps | discards 34 good pages |
| 12-page fax-quality memo, uniformly grey | 0.46 poor | 0.38 poor | discards | discards |

Only the third is a document tier 2 exists to catch, and the mean already catches it. In a corpus of hundreds of gigabytes of mixed scans, the first two shapes are the ordinary case rather than the exotic one. Whether a partially-poor document is worth publishing is a real question, but it belongs to arrangement (stage 6a) and would be a selection rule over surviving documents, not a verdict that removes them at stage 2.

### For a single-page document the two distributions are the same numbers

`low_score` is the worst page's score, so wherever `page_count` is 1 it equals `mean_score` exactly. A `low_score` distribution over such a corpus is a partial verbatim copy of the one already written. How partial is measurable rather than arguable — `extraction_metric.page_count` is already stored, and `SELECT count(*) FROM extraction_metric WHERE page_count = 1` says how much of any corpus falls into it before anyone builds a second report.

### A constant column is the generality ADR-075 already declined

Keeping the door open costs a `score_kind` column holding one value on every row that would ever be written, a widened primary key, a VERSION bump, and a `Bucket` field nothing renders. ADR-075 declined exactly this shape for the other `extraction_metric` columns — *"a future threshold on any of those computes its own distribution when it exists, rather than stage 3 pre-computing summaries nothing consumes"* — and the same reasoning applies to a second column of a table it did build.

### If a worst-page rule ever arrives

It gets its own profile key, named for what it measures, and its own ADR — not a value slotted into `degenerateOutputConfidenceFloor`. That is why `degenerateOutputConfidenceFloor` is **not** renamed here: it is a `profile.yaml`-visible key, and under ADR-062's merge a renamed key arrives as null and leaves as unset, silently dropping an operator's answered value on the next save. The javadoc names `mean_score` instead, which costs nothing and misleads no one.

## Consequences

**Two records are narrowed, neither is edited.** ADR-070 and ADR-075 keep their text; this record's `Amends` header and the `docs/adr/README.md` row are how a reader finds the narrowing, the same mechanism ADR-077 used on ADR-075 the day before. Issue #62's acceptance criterion 2 asked that ADR-075's Decision text no longer imply otherwise; it is satisfied by this record rather than by editing an accepted one, which is what the append-only rule in `docs/adr/README.md` exists to prevent.

**No code changes and no schema change.** `confidence_distribution` keeps `(run_id, grade)`, and `ExtractionSchema.VERSION` stays 3 — the schema comment gains a clause, and comments are not what the version guards. `ConfidenceDistribution`, `ConfidenceDistributionReport` and `DegeneracyFloor` were already right; what was missing was the record and the tests.

**Two tests now pin the decision** rather than leaving it in prose: a metric with an excellent mean and a poor low is not degenerate under a set floor, and a survivor with those same two scores lands in the `excellent` bucket and nowhere else. Both are the fold-over-page case, executable. They are the guard against a future contributor reading ADR-075's original sentence and adding `low_score` back in good faith.

**The rendered report is unchanged.** It is read by an operator choosing where to set `degenerateOutputConfidenceFloor`; a note about a column no threshold reads would be noise to that reader.
