# ADR-080 — The boilerplate floor is a gate, applied before signatures are computed

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none (settles what ADR-074 deferred: it named `boilerplateDocumentFrequencyFloor` and said where the floor is applied is stage 4's to decide)

## Context

[ADR-038](0038-shingling-moves-to-stage-3-boilerplate-detected-before-it-distorts-anything.md) moved shingling forward so that boilerplate is identified "before it corrupts stage-4 dedup or stage-5 relevance". [ADR-074](0074-stage-3-measures-shingle-document-frequency-a-boilerplate-floor-ships-unset.md) then measured document frequency into `shingle_document_frequency` and `shingle_corpus_size`, named `boilerplateDocumentFrequencyFloor` as a proportion of the shingled-document count, shipped it **unset** per observe-before-enforce, and left where the floor is actually applied to stage 4. This is that decision.

Two things sharpen it. **Stage 4's verdict blocks**: `redundant-with` removes a document from everything downstream ([ADR-079](0079-redundant-with-covers-near-duplication-and-containment-the-fuller-rendering-survives.md)), so boilerplate that survives into the comparison does not degrade an answer, it deletes real documents — two unrelated reports sharing a corporate footer read as near-duplicates and one of them goes. And **`CONTEXT.md` already has the word for a value the pipeline needs and does not have**: a *gate* is "not a pause — supply the value and no gate occurs; leave it unset and the run ends there, having recorded everything it learned."

## Decision

### The floor is applied before signatures are computed

**Boilerplate shingles are excluded from a document's shingle set, and its signature is computed over what remains.** A signature therefore represents a document's *distinctive* content, which is what makes a similarity between two signatures mean what stage 4 needs it to mean.

**Why not at comparison time.** A MinHash signature estimates a similarity over the set it was built from, and that estimate cannot have a subset's contribution subtracted from it afterwards — the estimator is over the set, not over a set minus a set. Discounting boilerplate after the fact would mean re-reading both documents' exact shingle sets for every candidate pair and scoring them directly, which is the corpus-wide comparison LSH exists to avoid.

**The floor is part of a signature's identity**, alongside the shingle parameters that produced its input and the permutation set that produced it ([#68](https://github.com/algernon28/vespera/issues/68) fixes the rest). This follows [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md)'s pattern rather than inventing one: a value dependent on a parameter lives under that parameter's identity, so changing the parameter mints new rows instead of silently invalidating old ones.

**Retuning the floor therefore re-signs the corpus** — a `DELETE` of stage 4's rows plus a re-run, which is the ledger's ordinary posture for retuning a threshold, paid here in signature computation rather than only in comparison. Accepted: the alternative buys stable signatures at the cost of an estimator that cannot answer the question.

### Which frequencies, and the absence rule

Boilerplate is every shingle whose `document_count` reaches `floor × shingled_document_count`, read from the **stage-3 run upstream of stage 4's own run**, and matched on `shingle_parameter_identity` — the frequencies and the shingles being signed must come from the same parameter identity or the comparison is between different alphabets.

**A shingle with no row in `shingle_document_frequency` is never boilerplate.** ADR-074 writes rows only for hashes appearing in two or more documents, so an absent hash appeared in exactly one — the rarest thing in the corpus, the opposite of boilerplate. Stating it here because the failure is silent: a reader treating a missing row as a zero-or-unknown and defaulting either way inverts the filter.

### An unset floor is a gate

**With `boilerplateDocumentFrequencyFloor` unset, stage 4 does not run: the invocation ends there, having recorded everything the earlier stages learned.** This is `CONTEXT.md`'s *gate*, and [ADR-047](0047-the-pipeline-never-blocks.md)'s "terminates at a missing gate input, resumes on re-invocation" — not an error, not a pause, and not a failed run.

So out of the box, `vespera run <root>` walks the corpus, reduces it byte-wise, extracts it, takes the content census, and stops before stage 4. The operator reads what stage 3 measured, sets a floor, and re-invokes — iteration between runs, exactly as ADR-047 describes.

**Why not run with no exclusion at all.** That is the one option ADR-038 exists to prevent. An unfiltered pass does not produce a rough answer to be refined later; it writes blocking verdicts against documents whose only similarity is a shared footer, and a wrongly deleted document is not visible as an error afterwards — it is simply absent from everything downstream. **Why not derive a default** from stage 3's distribution: the corpus's own frequency curve says where shingles are common, never which of them are boilerplate rather than genuine shared subject matter, and a value invented so the pipeline can run unattended is the sort of guess the profile exists to refuse (`CONTEXT.md`: "authored by a person, never guessed at").

### A document that is entirely boilerplate survives stage 4

Strip corpus-wide-common shingles from a cover sheet, a fax header page or a standard disclaimer and nothing distinctive remains. **Such a document keeps no verdict from stage 4 and is excluded from candidate generation** rather than compared with an empty signature — an empty set matches everything or nothing depending on how the estimator is written, and neither answer is true.

It is not *redundant*: it is empty, which is a different condition, and `CONTEXT.md`'s **Trash** entry requires the three conditions — broken, redundant, irrelevant — to stay separable and named individually. Nothing in stage 4's vocabulary fits it, and inventing a `VerdictKind` for it would put a stage-2-shaped judgement in stage 4's hands. A page with no distinctive content scores near zero against the seed set, so stage 5 removes it for the reason that is actually true of it.

## Consequences

**Stage 4's upstream run is stage 3's**, since it consumes stage 3's measurement; stage 2's identity comes along inside it, because a run id already folds in the runs it was derived from (ADR-048). The hand-off spec ([#70](https://github.com/algernon28/vespera/issues/70)) records it, but the choice is made here rather than left to be re-derived.

**This half needs no `pipeline` composition.** Everything it reads — `shingle`, `shingle_document_frequency`, `shingle_corpus_size` — and everything it writes are `similarity`'s own, the same self-contained shape ADR-074 noted for stage 3's document-frequency half.

**[#68](https://github.com/algernon28/vespera/issues/68) inherits a constraint**: whatever signature identity it settles on carries the boilerplate floor, and its measurements of banding behaviour are over boilerplate-stripped sets.

**The first invocation of a fresh corpus now ends at stage 4 by design.** Whether it would have anything to do there even with a floor set is [#69](https://github.com/algernon28/vespera/issues/69)'s question — that ticket decides whether stage 4 ships able to judge at all, and a second gate on its own threshold would stop the same run at the same place for a second reason.

**Nothing here fixes the signature table's columns, the permutation set, or how the boilerplate hash set is held in memory during a pass.** Those are #68's and the hand-off spec's.
