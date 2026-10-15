# ADR-260 — A shingle reaches the boilerplate floor by exact decimal arithmetic

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Amends**: [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md), its rule that boilerplate is every shingle whose `document_count` *"reaches `floor × shingled_document_count`"*: what reaching it means, and how the product is taken. It carries a note at its head.
- **Keeps**: [ADR-074](0074-stage-3-measures-shingle-document-frequency-a-boilerplate-floor-ships-unset.md)'s floor, a proportion of `shingled_document_count` computed at read time from the stored counts; the rest of ADR-080, its absence rule and its gate among them; [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md)'s reading of a numeric value, `Double.parseDouble` of the trimmed text, and its three states; `RedundancyGate.floorOf`, which takes as the floor any number that reading gives.

## Context

`BoilerplateShingles.resolve` takes the floor as a `double`, multiplies it by `shingled_document_count` in `double` arithmetic, and selects the hashes whose `document_count >= ?` with the product bound. A `double` holds 0.28 as 0.28000000000000002665…, and 0.28 × 25 comes to 7.000000000000001, so at a floor of 0.28 a shingle in 7 of 25 documents, 0.28 of them exactly, is not boilerplate.

It is not one case. Of the pairs 2 ≤ k ≤ N with N below 2,000 for which k / N is a terminating decimal, 245 leave out the shingles in exactly k documents when the floor is that decimal: among them 0.28 and 0.56 of 25, 0.14 of 50, and 0.07, 0.14, 0.28, 0.55 and 0.56 of 100. Up to N = 500 there are 36.

Stage 4's run records the floor as the `double` (`StageRuns.ContentRedundancyConfigConsumed.boilerplateDocumentFrequencyFloor`), so two texts that read as one `double` are one run.

## Decision

### 1. The rule

**A shingle is boilerplate where its `document_count` is at least `ceil(f × N)`**, N being the `shingled_document_count` of the stage-3 run and shingle-parameter identity ADR-080 names, and f the floor's decimal value (§2). The product is taken in exact decimal arithmetic, and the cutoff is an integer. ADR-080's *reaches* means *is at least*: the rule is `document_count / N ≥ f`, held exactly.

### 2. The floor's decimal value

**f is the shortest decimal that reads back as the `double` ADR-120's reading gives** (`BigDecimal.valueOf(double)`). Where the operator's text has 15 significant digits or fewer, which is every floor the README's rule asks for, f is the number the text says: a `double` tells apart every two decimals of 15 significant digits, so the shortest decimal reading back as it is that one. Past 15 significant digits, f is the `double`'s own shortest decimal.

- **Not the text itself.** The run's identity holds the `double` (Context). A cutoff from the text would let one run id stand for two cutoffs: `0.28` and `0.28000000000000000001` read as one `double`, and their products with 25 round up to 7 and to 8.
- **Not an epsilon.** `ceil(f × N − ε)` in `double` needs a width no measurement fixes: one too wide takes a floor just above k / N down to k, and one too narrow misses as N grows. The exact product needs none.

### 3. The edges

No value `floorOf` takes today is refused, and none is newly read otherwise:

- **0 or below**, `-Infinity` among them: the cutoff is 0 or below, and every shingle with a row is boilerplate, which is every shingle in two documents or more (ADR-074).
- **1**: the cutoff is N, and only a shingle in every document is boilerplate.
- **Above 1**, `Infinity` among them, or any floor whose product with N is above N: nothing is boilerplate.
- **`NaN`**: there is no product, and nothing is boilerplate, the direction that removes no document.

`BigDecimal` has no `NaN` or infinity, so the last three are said here rather than left to the arithmetic.

### 4. Where it is computed

`BoilerplateShingles.resolve` keeps its signature, `(RunId, double)`, computes the cutoff as an integer under §1 to §3, and selects `document_count >= ?` with that integer. `RedundancyGate`, `NumericValue`, the schema and stage 4's configuration consumed do not change. The javadoc of `Profile`'s `boilerplateDocumentFrequencyFloor` states the rule, *at least*, and that stage 4 reads the key (ADR-080).

### 5. The share the README's command prints

The README's command prints, for each line, the share `100 × k / N` in integer arithmetic divided by 100, which is k / N rounded down to the hundredth. Set as the floor, a line's share s marks the shingles on that line and on every line after it, and none on the lines before: `ceil(s × N)` is the first count whose printed share is s or more. The exact share k / N, written in full or cut off after at least as many decimal places as N has digits, marks the shingles in k documents or more and none in fewer. Rounded up, it can mark only those in k + 1 or more: 2 / 3 rounded up to 0.67 gives `ceil(0.67 × 3)` = 3.

## Consequences

- A floor written as the exact share k / N marks the shingles in exactly k documents, as ADR-074's proportion says it should.
- A floor of (k − 0.5) / N, written with one more decimal place than N has digits, rounded or cut off, marks the shingles in k documents or more, as it did under the `double` product.
- The change is to `similarity`, which the versions of stage 2 (`extraction`), stage 3 (`content-census`) and stage 4 (`content-redundancy`) name (`StageModules`, ADR-058), so those three stages move. Every stage after them moves through its upstream run (ADR-048). The walk and stage 1, whose version names `corpus` alone, do not move. The javadoc of §4 is a change to `profile`, which the version of `generation` names; that stage moves anyway.
- Stage 2's run is new, and its converter answers stay in the extraction cache ([ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md)): the next invocation repeats stages 2 and 3 from the cache, then stage 4 under the floor set.
- No schema, cache key, verdict kind or report changes.

## Tests

`similarity.ShinglesReachTheFloorExactlyTest` reads each floor from its text as `NumericValue` does and resolves it over a corpus of one hash per document count from 2 to N.

| Method | What it holds |
| --- | --- |
| `aShingleInExactlyTheFloorsShareIsBoilerplate` | at 0.28 of 25, a shingle in 7 or 8 documents is boilerplate and one in 6 is not |
| `sevenOfAHundredReachAFloorOfSevenHundredths` | at 0.07 of 100, a shingle in 7 is boilerplate and one in 6 is not |
| `everyExactShareMarksTheShinglesInThatManyDocumentsOrMore` | for every N up to 500 whose k / N terminates, the floor k / N marks the counts k to N and none below |
| `anyWrittenFloorMarksTheShinglesAtOrPastItsProductWithTheCorpus` | 20,000 floors of one to six decimal places between 0 and 1, over N up to 500, each mark the counts at or above `ceil(f × N)` and none below |
| `theSharePrintedForALineMarksThatLineAndTheLinesAfterIt` | for every N up to 500, each share the README's command prints marks the first count on its line and every count after it (§5) |
| `theEdgesOfTheFloor` | §3: 0, −0.1 and `-Infinity` mark every stored shingle, 1 only the one in every document, 1.5, `1e300`, `Infinity` and `NaN` nothing |

`similarity.BoilerplateShinglesTest`, unchanged, holds ADR-080's absence rule and the floor of 0.5 of 10.

## What this does not decide

- **Whether a floor below 0, above 1 or not a number is refused at the gate.** It is taken as §3 says; refusing it would amend ADR-120's three states.
- **How the README's command prints a share.** §5 holds for the share as it is printed.
