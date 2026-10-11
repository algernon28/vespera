# ADR-234 — No redundancy pointer names a document the same pass removed, a candidate cut is read from the band, and a verdict reason is written in one locale

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11, on the call of the session that worked the review register of 2026-10-10 ("work the To do items ... write a test that fails on the current code first, and drop the finding if the test passes"). No question was put to a person. Each finding below had a test written first that failed on the code before this change, and passes after it.
- **Amends**: [ADR-079](0079-redundant-with-covers-near-duplication-and-containment-the-fuller-rendering-survives.md), in what phase 2 may remove (§1); [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md)'s candidate cuts, in which side of a cut a document on its edge is counted (§2).
- **Settles**: register findings R16, C-E4 and C-S1.
- **Rests on**: the code at `fa908c1`; `RedundancyResolutionTest`, `LabelledSpreadTest`. No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

Three findings of the review register were measured by a test before anything was changed:

- **R16.** Stage 4b's phase 2 skipped only the occurrences phase 1 removed. Of a chapter inside a part inside a volume, the chapter was written as contained in the part (the tie between part and volume going to the lower id), and the part then as contained in the volume: `aChainOfContainmentsLeavesNoPointerAtARemovedDocument` found the pointer `1 -> 2`, at a document the same run removed. And the survivor phase 1 kept of two near-identical documents, lying inside a third, was then contained away, leaving its partner's near-duplicate pointer at a removed document: `aNearDuplicateSurvivorInsideAnotherDocumentLeavesNoPointerAtARemovedDocument` found `1 -> 2`. ADR-079's survivor rule is there so that a pointer names the copy a reader gets; a pointer at a removed document names nothing a reader gets.
- **C-E4.** The relevance report's candidate cut is the next band's lower bound, `lowest + ordinal × width`, compared with `>=`, while the band a score falls in is found with `RelevanceDistribution.bandOf`'s tolerance. Over 0.0 to 0.5, the third cut is `0.30000000000000004`: a document scoring 0.3 was counted in the fourth band and below the cut at that band's edge, which the page shows as 0.30. `aDocumentOnABandsLowerEdgeSurvivesTheCutAtThatEdge` failed on it.
- **C-S1.** Both of stage 4b's verdict reasons, and stage 2's tier-2 reason, were formatted in the JVM's default locale: on a machine set to German the reason read `at Jaccard 0,9802`. `theReasonWritesItsScoreTheSameWayInEveryLocale` failed on it.

## Decision

### 1. Phase 2 removes no document a row of its run already names as kept, and names no removed document as a container

- A candidate container this run has already removed, as a near-duplicate or as contained, is passed over, where only phase 1's removals were.
- An occurrence a row of this run already names as the document kept in another's place, a near-duplicate's survivor or a container, gets no containment row, though its candidates are gone through and counted as before.

So the pass never leaves a pointer at a document it removed. **What it costs**: a document that is both kept for another and wholly inside a third stays, with the third: some containment is not removed. Which one stays depends on the occurrence order the pass goes in, which is the order ADR-225 reads in. That is the direction that discards nothing, and was preferred to two others:

- *Following a chain to its end and re-pointing the row* would keep fewer documents, but would write a score that is not the exact similarity to the occurrence the row points at (ADR-082), or would need a second score the chain does not give: containment does not carry over from A in B and B in C to A in C at the same cut.
- *Removing the kept document and re-pointing its near-duplicates at its container* changes the relation the near-duplicates were found by.

The check is one statement, by the index on `redundant_with_occurrence_id`, asked only of an occurrence a container was found for. It sorts nothing.

### 2. A candidate cut keeps the documents whose band is at or above the band it opens

A document survives a cut where `bandOf` puts it in the band the cut is the lower edge of, or a higher one. That is "at or above the cut" as the floor's step reads a number, for every score but one on an edge that the arithmetic puts a hair above the number the page shows; for that one, the cut now agrees with the band it is counted in and with the number an operator copies from the page. What the cut discards is the rest, of the documents and of the answers, and is no longer counted separately.

### 3. A verdict reason is formatted in `Locale.ROOT`

`near-duplicate of occurrence %d at Jaccard %.4f`, `contained in occurrence %d at containment %.4f` and `mean confidence score %.3f below the configured floor %.3f` are written with a decimal point on every machine, as every number in a report already is. The words of each are unchanged.

## Consequences

- No pointer in `redundant_with` names an occurrence removed under the same run. A few more documents may survive stage 4 than before, each one kept for another.
- The relevance report's cut counts and band counts agree for a document on a band's edge.
- On a machine whose default locale writes a decimal comma, a verdict reason reads differently from what that machine wrote before.
- **Which run ids move**: the change touches `similarity`, `embedding` and `extraction`, each already moved on the branch that carries it (`f64e30c` touches `extraction` and `embedding`), so stages 2 to 6b are minted once in that build, and this record adds no move to it. A `similarity` change on its own would move stages 2 to 6b too. No DDL, no table, no index, no verdict kind, no cache key.

## Tests

- `similarity.RedundancyResolutionTest`: `aChainOfContainmentsLeavesNoPointerAtARemovedDocument`, `aNearDuplicateSurvivorInsideAnotherDocumentLeavesNoPointerAtARemovedDocument`, `theReasonWritesItsScoreTheSameWayInEveryLocale`.
- `embedding.LabelledSpreadTest.aDocumentOnABandsLowerEdgeSurvivesTheCutAtThatEdge`.
- `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest` counts a statement that asks about one occurrence by key, `= ?`, as naming one.

## What this does not decide

- Whether stage 4 should follow a chain of containments to its end (§1's first alternative). It would want a score the chain does not give.
- The manifest's cells that begin with `=`, `+`, `-` or `@` (register finding R32), which change what a machine reads from `documents.csv` and are the operator's to decide.
