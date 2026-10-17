# ADR-273 — The translation step reads each mean vector only to compare it, and the profile only when its run is minted

- **Date**: 2026-10-17
- **Status**: accepted on 2026-10-17.
- **Amends**: [ADR-271](0271-one-copy-of-a-translation-set-survives-in-the-language-the-profile-prefers-first.md), in these places and no others: §9's `TranslationCandidates`, "the mean vectors read in blocks as `NearestNeighbourGraph` reads them", which §1 below reads for the comparison and for nothing else, with a vector's width read once from the vector cache; §6's two keys, which join the scoring run's `config_consumed` after `relevanceScoreFloor` and are now read with it from one load of the profile (§2); and §7's last item, "the two keys and how they were read", which states how they read at that load (§3).
- **Keeps**: ADR-271 §3, which survivor is a candidate, which two are a likeness, and the tie to the lower index; [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md), a mean vector computed from an occurrence's chunk vectors, read in blocks and discarded; [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), every vector under one embedder identity has the model's dimension; [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) and [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md) §2, an input of the scoring run read when the run is minted and handed to the steps after it; [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md), unset and unreadable are two readings; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`; [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md), no statement that sorts without a record.
- **Settles** [#548](https://github.com/algernon28/vespera/issues/548).

## Context

Three reads in ADR-271's step are made more often than its decision needs.

**Every mean vector is computed once more than it is compared.** `TranslationCandidates.mutualNearest` sizes its table of nearest by the candidates' languages, and learned them by reading every block of `Vectors.block`, each block a list of a language and a mean vector, before the comparison read every block again. In `TranslationOf`, a block computes each candidate's mean from its chunk vectors in the vector cache (`Clustering.meanVectorOf`), and reports a candidate with no stored vector as one with no language. Each candidate's chunk vectors were therefore read from the vector cache once for a language that `TranslationOf.Candidate` already carries, and once more to be compared. Before both, the width of a vector, which sizes the blocks, was taken from the first candidate whose mean was not empty, computing the mean of every candidate before it.

**The scoring run's identity is read from two loads of the profile.** `StageRuns` read `relevanceScoreFloor` through `RelevanceScoreFloorValue.readFrom`, which loads the profile, then loaded it again for `translationSimilarityFloor` and `preferredLanguages`. A profile saved between the two loads gives one run an identity drawn from two profiles.

**The page reads the profile again.** The run's identity holds `null` for a floor unset and for one unreadable, and `[]` for languages unset and for languages unreadable, and the step acts on those values as `StageRuns` hands them over. To tell the page's reader which of the two each key was, `TranslationTasklet` loaded the profile again as it wrote the page. A profile saved after the mint and before the page has the page call a key unreadable that the run read as unset, or unset that it read as unreadable.

## Decision

### 1. A candidate's language is read without its vector, and each mean only by the comparison

`TranslationCandidates.Vectors` hands over a candidate's language by its index, `String language(int index)`, Lingua's name or `null`, without reading a vector; and its blocks are the mean vectors alone, `List<float[]> block(int from, int size)`, an empty array for a candidate with no vector stored. The record `TranslationCandidates.Vector`, which paired the two, is removed.

`mutualNearest` reads every candidate's language through `language(int)` before the comparison and reads no block for them. The comparison reads the blocks as it did, each block once as the outer one and each later block once against it, and **skips a candidate whose mean is empty** as it skips one with no language: a survivor with no vector stored is no candidate (ADR-271 §3). Each candidate's mean is computed by the comparison's own reads of the blocks, and by nothing else.

**The width of a vector is one read of the vector cache**, `VectorCache.dimensionUnder(chunkerIdentity, chunkingRuleIdentity, embedderIdentity)`: the number of components of one vector stored under the three identities the step reads its means under, `0` where none is stored. Any one vector serves, since every vector under one embedder identity has the model's dimension (ADR-084). Where it is `0`, no candidate has a vector, and nothing is compared, as before. The statement is `SELECT length(embedding) FROM vector WHERE chunker_identity = ? AND chunking_rule_identity = ? AND embedder_identity = ? LIMIT 1`, the byte length divided by the four bytes of a component. No index of `vector` leads with those columns, so SQLite reads `vector` in its own order up to the first row that matches, as the read of the embedder identities the step makes before it reads the whole table (ADR-228); it sorts nothing.

**The likenesses are those ADR-271 §3 finds**: the same pairs, with the same cosines, in the same order, with the same tie to the lower index. A language that only candidates with no vector hold adds a column to the table of nearest that no comparison fills.

### 2. The scoring run's mint loads the profile once

`StageRuns` loads the profile once as it mints the scoring run, and reads `relevanceScoreFloor`, `translationSimilarityFloor` and `preferredLanguages` from that one `Profile`. `RelevanceScoreFloorValue` gains `static RelevanceScoreFloorValue from(Profile profile)`, which the mint calls; `readFrom(ProfileStore)` loads the profile and returns `from` of it. `config_consumed` keeps its shape and, for a profile not saved while it is read, its values.

### 3. The page states the readings the run was minted under

From that same load `StageRuns` keeps the two keys' readings, the `NumericValue.Reading` of `translationSimilarityFloor` and the `LanguagesValue.Reading` of `preferredLanguages`, and hands them to the translation step beside the two values it hands it already. `TranslationTasklet` writes the page's statement of the two keys from those readings and does not load the profile to write the page. The words stay ADR-271 §7's: read as the number or as the codes, unreadable, or unset.

### 4. Which run ids move

`embedding` changes (§1), which the versions of seed measurement (`embedding`, `extraction`), embedding scoring (`embedding`, `extraction`), arrangement (`synthesis`, `extraction`, `embedding`) and generation (`synthesis`, `extraction`, `embedding`, `profile`) name (`StageModules`, ADR-058), so those four move. `pipeline` changes (§2, §3), which no stage's version names (ADR-222, ADR-226), and `config_consumed` keeps its shape, so §2 and §3 move no run id of their own. `corpus`, `extraction`, `similarity`, `profile` and `ledger` do not change, so the walk and stages 1 to 4 do not move.

## Alternatives refused

- **The width read from the embedder identity's text**, which names it after `dimension=`. It costs no read, but makes a second reader of a composed key's format, with a failure of its own for a text that names no width.
- **The width of the first candidate's stored vector, read by its key.** One read where the first candidate has a vector, and one more for each candidate before the first that has one: a walk over the candidates, shorter, but a walk.
- **The languages kept in the block beside the means, the first reading taking only the languages.** A block of stored means is computed whole, so reading one for its languages computes its means.
- **`StageRuns` keeping the whole `Profile` of the mint.** The step needs two readings of it, and a profile held for the run invites a later step to act on a key the run's identity does not carry.

## Consequences

- The translation step computes each survivor's mean from the vector cache only as the comparison reads its block, and takes a vector's width from one statement.
- A profile saved while the scoring run is minted, or after it, changes neither that run's identity nor the page written for it; the next invocation reads it.
- The next invocation mints a new seed measurement run and a new scoring run: seed extraction from the extraction cache, the seed comparison, 5c from the vector cache, the translation step, 5d's scores, and every step after them, again. Stages 1 to 4 are skipped as recorded.

## Tests

| Method | What it holds |
| --- | --- |
| `embedding.TranslationCandidatesTest.noMeanVectorIsReadBeforeTheComparison` | six candidates read two at a time: the blocks read start at 0, 2, 4, 2, 4, 4, the comparison's alone |
| `embedding.TranslationCandidatesTest.aCandidateWithNoMeanVectorIsNeverALikeness` | a Chinese candidate with an empty mean, whose cosine of 0 would beat the other Chinese candidate's -1, is paired with nothing: one likeness, 0 with 2, at -1 |
| `embedding.TranslationOfTest.eachMeanVectorIsComputedOnceAndOnlyByTheComparison` | three survivors in one block, the first with no vector stored: each mean computed once |
| `embedding.TranslationOfTest.withNoVectorStoredNoMeanVectorIsComputed` | no vector stored under the step's identities: no mean computed |
| `pipeline.TheScoringRunIsMintedFromOneReadingOfTheProfileTest.theThreeValuesOfTheScoringRunsIdentityAreReadFromOneProfile` | a profile read as 0.5, 0.9 and `en` at its first load and as 0.6, 0.8 and `zh` after it: the scoring run records 0.5, 0.9 and `["en"]` |
| `pipeline.TheTranslationsPageStatesTheKeysAsTheRunReadThemInvocationTest.thePageStatesBothKeysAsTheScoringRunReadThem` | both keys unreadable when the scoring run is minted, and saved unset as the translation step starts: the page calls both unreadable |
| ADR-271's six tests of `embedding.TranslationCandidatesTest`, its three of `embedding.TranslationOfTest`, and `pipeline.RunIdentityGoldenTest.embeddingScoring` | the likenesses and the scoring run's `config_consumed`, unchanged |

## What this does not decide

- **The load, change and save by which the step points the two keys' measurements at the page** (ADR-271 §6). A save between its load and its save is a race with every other writer of the profile, and is not this record's.
- **An index of `vector` for the read of the width.** The read stops at the first row that matches and is made once a step.
