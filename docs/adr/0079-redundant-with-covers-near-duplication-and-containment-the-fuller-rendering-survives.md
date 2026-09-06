# ADR-079 — `redundant-with` covers near-duplication and containment; the fuller rendering survives

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none (fills in what ADR-018 left unstated: its reconstituted summary fixes the algorithm family and says nothing about what earns the verdict)

## Context

`REDUNDANT_WITH` has existed in `VerdictKind` since the vocabulary was written, and blocks. Nothing recorded says what earns it.

[ADR-018](0018-stage-4-uses-minhash-with-lsh-banding.md)'s whole surviving text is *"Not SimHash — MinHash catches containment (not just similarity) and supports analytically-derivable LSH parameters."* That is a claim about the algorithm's reach, not a rule about the verdict: it says containment **can** be caught, never that a contained document **should** be removed. And the two readings come apart on documents this archive certainly holds:

| Case | Jaccard | Containment | Near-duplication only | With containment |
| --- | --- | --- | --- | --- |
| The same report exported twice, one with a changed footer | ≈ 0.95 | ≈ 0.97 | redundant | redundant |
| A 2-page abstract filed beside the 40-page paper printing it | ≈ 0.04 | ≈ 1.0 | unrelated | the abstract goes |
| A 12-page chapter filed beside the 400-page proceedings volume | ≈ 0.03 | ≈ 1.0 | unrelated | the chapter goes |

Under near-duplication alone, the second and third pairs both survive whole: the same passage is embedded twice, scored for relevance twice, clustered twice, and can be written about twice at two granularities. That is the redundancy this stage exists to remove, arriving in a shape a Jaccard threshold cannot see.

**The survivor question is separate, and stage 1 already answered a version of it.** [ADR-069](0069-a-duplicate-set-resolves-by-earliest-creation-time-then-path.md) resolves a content-identity group by earliest `creation_time`, then lexicographically-lowest `path`. But its members are **byte-identical**: content cannot discriminate between them, and filesystem metadata is all that is left. Stage 4's members differ in content — that is what makes them stage 4's problem rather than stage 1's — so the same reasoning does not carry over.

## Decision

### One verdict, two relations

**`redundant-with` covers both near-duplication and containment.** No second verdict kind is added. Both say the same thing about a file occurrence — *its content is already published through another survivor* — and `VerdictKind` is a closed vocabulary whose entries name what was decided about a document, not how the decision was reached. Which relation applied belongs in the verdict's free-text `reason` (ADR-057), and the occurrence it resolved to belongs in a `similarity`-owned pointer table, the same shape ADR-069 chose for `superseded_by` rather than encoding a reference into `reason` (ADR-041).

### Near-duplication resolves per set, and the fuller rendering survives

**A near-duplicate set is a connected component of the pairs scoring above the threshold, and exactly one member survives it.** Every other member is `redundant-with`, pointing at that survivor. Resolving per set rather than per pair is what keeps the pointer honest: with pairwise resolution, A losing to B while B loses to C leaves B pointing at a document that is itself removed, and a reader following the pointer arrives nowhere. This is ADR-069's "one rule, not two" shape — the survivor is defined as the one member that never receives the verdict.

**The survivor is the occurrence with the most alphanumeric characters of extracted text**, `extraction_metric.alphanumeric_char_count`, the column ADR-070's tier 1 already reads as "how much real content is there". Ties break by ADR-069's rule: earliest `creation_time`, then lexicographically-lowest `path`.

**Why not ADR-069's rule outright.** Stage 1 reached for filesystem metadata because byte-identical copies offer nothing else; here content does, and it answers a better question. This is a publication-ready knowledge base, so the copy that reaches a reader should be the one that reads best — a digital-text PDF over a scan of the same pages, the export that kept its tables over the one that lost them. "Which file landed in the archive first" is a fact about the archive, not about the document. **The two stages therefore resolve differently on purpose**, and the tie-break is where they agree: once content has failed to discriminate, stage 4 is in exactly stage 1's position, and reaches for exactly stage 1's answer rather than inventing a second one.

**Why not `mean_score`, the other obvious quality signal.** It is null for `.docx` and `.txt` (ADR-070: confidence aggregation is page-derived), which are frequently the *best* text in the corpus. A rule reading it would demote precisely the documents that need no rescuing.

### Containment removes the contained document, and only that direction

**Where one document's content is near-totally contained in another's, the contained document is `redundant-with` the container.** The relation is asymmetric and the direction is fixed: the container never becomes redundant with what it contains.

This is the only lossless direction. Removing the chapter to keep the volume publishes every word of both; removing the volume to keep the chapter discards the other 388 pages. The cost is real and worth stating: the standalone chapter stops being its own unit, so stage 6a can only arrange the volume that holds it. That is accepted as the smaller loss — arrangement at a coarser granularity, against the same passage entering the taxonomy twice.

**Near-total containment, not partial overlap.** A document sharing a third of its text with another is not contained in it; it is a document that quotes. Where the cut sits is [#68](https://github.com/algernon28/vespera/issues/68)'s to fix, along with the estimator that can see containment at all — Jaccard banding cannot, which is why that ticket is blocked on this one.

**Containment resolves against survivors only.** A document is never removed as contained in a document that is itself removed. Near-duplicate resolution runs first, containment second, against what survives it — so every `redundant-with` pointer, of either relation, names an occurrence a reader can actually go and read.

## Consequences

**Stage 4 removes documents no Jaccard threshold would have found**, which is the point: an abstract, a chapter offprint and a press release reproduced in a report are ordinary contents of an archive assembled by people over years, and each of them would otherwise be published twice.

**`similarity` gains a pointer table** in `superseded_by`'s shape, keyed by occurrence and run, naming the surviving occurrence. Its columns are the hand-off spec's to fix ([#70](https://github.com/algernon28/vespera/issues/70)), the same deferral ADR-067 and ADR-069 made.

**Two survivor rules now exist in the pipeline, and they disagree deliberately.** Stage 1 publishes the earliest copy; stage 4 publishes the fullest rendering. A reader finding both should find this paragraph rather than assume one is a mistake: they resolve different situations, and stage 4 falls back to stage 1's rule exactly when its own runs out of signal.

**[#68](https://github.com/algernon28/vespera/issues/68) is unblocked, and its scope is now larger than banding.** Admitting containment means plain MinHash-over-Jaccard banding is not sufficient on its own — a containment ≈ 1.0 pair with Jaccard ≈ 0.03 sits at the flat end of every S-curve. That ticket has to choose an estimator that sees containment, and weigh whatever it costs, including a dependency under ADR-046.

**[#69](https://github.com/algernon28/vespera/issues/69) inherits a second threshold.** Near-duplication and containment cut at different numbers on different measures, so "the stage-4 threshold" is now two values, and whatever calibration artifact that ticket decides on has two distributions to show.

**Nothing here specifies the pointer table's columns, the two thresholds, the estimator, or how sets are assembled in code.** Those are #68, #69 and the hand-off spec's, the same deferral every prior slice made.
