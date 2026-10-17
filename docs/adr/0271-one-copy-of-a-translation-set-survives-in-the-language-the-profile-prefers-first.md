# ADR-271 — One copy of a translation set survives, in the language the profile prefers first

- **Date**: 2026-10-17
- **Status**: accepted on 2026-10-17.
- **Amends**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md)'s closed vocabulary, by the one value §1 adds, in the way that record provides for; [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) §7's fifteen step names, which §5 makes sixteen; and what stage 5's relevance-scoring step scores, the survivors of the seed measurement run, of which it scores those carrying no `translation-of` under the scoring run (§5).
- **Keeps**: [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md), a floor that is unset does not stop the run; [ADR-260](0260-a-shingle-reaches-the-boilerplate-floor-by-exact-decimal-arithmetic.md), a floor compared in exact decimal arithmetic; [ADR-262](0262-stage-3-writes-the-page-the-boilerplate-floor-is-read-off.md) §3, a page written when its step works and left as it is when the step is skipped as recorded; [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md), a scoring run reads the vectors of one embedder identity; [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md), a mean vector computed from an occurrence's chunk vectors, read in blocks and discarded; [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md), completion recorded per step; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`.

## Context

An archive can hold one content twice, in two languages. A vendor's API reference is often shipped as one tree per language, under sibling folders named for the language, `en/` and `zh/`, with the same file names beneath each. Nothing up to stage 5 treats the two copies as one: stage 1 compares bytes, and stage 4 compares shingles of words, which two languages do not share. Both copies survive, both are scored and clustered, and the arrangement names a cluster after a title in each language.

Stage 2 already detects the primary language of each converted occurrence, locally, with Lingua, and stores Lingua's name for it in `extraction_metric.primary_language` (ADR-073); `ExtractionMetrics.eachMeasuredFormOf` hands it out for a page of occurrences. The embedding model a scoring run names can be multilingual, as bge-m3 is, and then a text and its translation lie close in its space. Stage 5c has computed and kept every survivor's chunk vectors by the time the relevance score is asked for. Nothing turns either fact into a judgement.

The two signals differ in cost and in certainty. The path costs nothing and, where a tree is copied per language, is certain. The embedding is already paid for and finds a translation wherever it lies, but it is a similarity on a scale, and two pages of one generated reference can be as alike as a page and its translation: the index page of the entries under B and the one of the entries under C share most of their identifiers. The path tells such pages apart, and so measures how far the embedding can be trusted on the corpus at hand.

## Decision

### 1. The verdict

**`VerdictKind` gains `TRANSLATION_OF`, blocking**: the occurrence's content is carried, in another language, by another occurrence that survives. It is persisted as its name, like every kind.

It is a kind of its own and not `REDUNDANT_WITH`, because that kind is stage 4's, decided over shingles, and its `redundant_with` row carries an exact Jaccard or containment score that a translation has no value for. An operator reading the verdicts by kind tells the two apart without a join.

Each verdict has a row in a new table `embedding` owns:

```sql
CREATE TABLE IF NOT EXISTS translation_of (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    translation_of_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    language TEXT NOT NULL,
    kept_language TEXT NOT NULL,
    evidence TEXT NOT NULL,
    PRIMARY KEY (occurrence_id, run_id)
);
```

with an index on `run_id` and one on `translation_of_occurrence_id` (ADR-173). `language` and `kept_language` are ISO 639-1 codes, `evidence` is `parallel-paths`, `embeddings` or `both` (§4). Nothing existing depends on the table, so `EmbeddingSchema`'s version does not move (ADR-197).

### 2. Translations by their paths

**A language folder** is a segment of an occurrence's path (the `OccurrencePath`, beneath the corpus root, separated by `/`), other than its last, that is spelled as two lowercase ASCII letters forming the ISO 639-1 code of a language Lingua detects, alone or followed by `-` or `_` and either two letters (a region: `en_US`, `pt-BR`, `zh-cn`) or four (a script: `zh-Hans`). Its language is the two letters. `IT`, `En`, `english`, `eng` and `xx` are none. The closed list is Lingua's languages, so that a folder names a language a detected one can be compared with.

Lowercase only, because locale trees are written in lowercase, and a segment in capitals that happens to be a code is what a department or a country is called: `IT/budget.pdf` beside `HR/budget.pdf` is not a text in Italian and its Croatian translation.

**Two survivors entering the step are translations by their paths** when their paths have the same number of segments, differ in exactly one, that one is a language folder in both, of two different languages, and neither path holds a second language folder. Nothing else is read: not the size, not the text, and not the detected language, since a page in Chinese whose words are mostly identifiers can be detected as English, and a check of the detected language would refuse the very pages this rule exists for.

### 3. Likenesses by their embeddings

A survivor entering the step is **a candidate** where stage 2 detected its language and it has a mean vector under the scoring run's embedder identity: the mean of its chunk vectors, computed as clustering computes it (ADR-087).

**Two candidates are a likeness** when their detected languages differ and each is the other's nearest, by the cosine of the mean vectors, among the candidates of its own language: nearest each way, for each pair of languages. A tie goes to the lower occurrence id. Two candidates of one language are never a likeness; two copies in one language are stage 4's.

**A likeness is applied** when its cosine is at or above `translationSimilarityFloor`, compared exactly, as ADR-260 compares the boilerplate floor: the cosine's exact binary value against the floor's decimal as written, `new BigDecimal(cosine).compareTo(BigDecimal.valueOf(floor)) >= 0`. An unset or unreadable floor applies none.

**A likeness at or above the floor is applied with no confirmation of each pair.** The floor is the confirmation: it is read off a page (§7) that counts, at each hundredth of similarity, the likenesses the paths confirm and those they refute, so the false pairs the embedding makes on this corpus are a number the floor is set above. Nearest each way keeps an occurrence to one likeness for each other language. A confirmation of each pair would be a second labelling pass, with a file to answer and an invocation to read it, for a judgement the counts already make; and a floor set too low is undone by raising it, which mints another scoring run and brings the copies back (ADR-156).

### 4. The translation set and the copy that survives

Translations by their paths and applied likenesses are the links; **a translation set** is two or more survivors connected through them. A member's language is its folder's where it is a translation by its path, and its detected language otherwise.

**A set two of whose members have one language stands whole**, and nothing of it is removed: two copies in one language are not translations of each other, and which of them a third translates is not known.

**Otherwise one member survives**: the one whose language comes first in `preferredLanguages`, and where no member's language is listed, the one whose path is lowest in code-point order. Every other member is `translation-of` that one, with `evidence` `parallel-paths` where every link of the set is a translation by its paths, `embeddings` where every link is an applied likeness, and `both` otherwise.

**With `preferredLanguages` unset or unreadable, nothing is removed.** The sets are counted on the page and in the step's line (§8). Choosing for the operator would keep a Chinese copy over an English one by the order of two letters.

### 5. Where it runs

**A step of its own, `translation`, between `embedding-scoring` (5c) and `relevance-scoring` (5d), under the scoring run**, named in `StepNames` as the others are. Its lines open with `Stage 5 (translations)`, and no other step's letter moves. The job has sixteen steps.

- After 5c, because a likeness needs the vectors. Before 5d, so that no removed copy is scored, put to a person on the labelling page, judged by the relevance floor, clustered or arranged: 5d scores the survivors of the seed measurement run carrying no `translation-of` under the scoring run, and every step from 5e on reads the scoring run's survivors, as it does now.
- Translations by their paths are decided in this step and not before 5c, though they need no vector, because the copies a path pair removes have to be embedded for the page to count the likenesses the paths confirm and refute. The cost is the embedding of those copies, once, since a vector is kept by content (ADR-085).
- It consults the gates every step of the scoring half consults (`StageFiveGates.modelSeedWalkUsable`), and none of its own (§6).
- It records its completion (ADR-116): what it reads, the survivors, the vectors and the two values of §6, is all named by the scoring run. A step that finds its work unfinished discards its own `translation-of` verdicts and `translation_of` rows under the run and decides again.
- It counts its verdicts as it inserts them, as the steps removing occurrences do, so the progress page shows it as one (ADR-268).

### 6. The profile

Two keys, after `outOfScopeNameEndings`:

- **`translationSimilarityFloor`**, a `NumericValue`: the cosine of two mean vectors at or above which a likeness is applied. Ships unset; unset or unreadable, no likeness is applied, and translations by their paths still are. Not a gate, for ADR-088's reason: the step writes the page the value is read off. `Profile.withTranslationSimilarityFloorMeasurement(Measurement)` points it at the page.
- **`preferredLanguages`**, of a new type, `LanguagesValue`: one line of ISO 639-1 codes, most preferred first, separated by spaces, commas or both, in any case, read as lowercase codes in the order written, each once at its first place (`it, en`). A piece that is not two ASCII letters makes the whole value unreadable, and none of it is read. Ships unset; unset or unreadable, nothing is removed (§4). Not a gate. `Profile.withPreferredLanguagesMeasurement(Measurement)` points it at the page, which lists the languages found.

Both join the scoring run's `config_consumed`, after `relevanceScoreFloor`, as `"translationSimilarityFloor":` the number or `null`, and `"preferredLanguages":` the codes read, `[]` where none is. Setting either mints a new scoring run, which repeats 5c from the vector cache and 5d, as setting the relevance floor does.

### 7. The page

**`translations.html` in the working directory**, one page through `ReportPage`, written whenever the step works and left as it is when the step is skipped as recorded (ADR-262 §3). It holds:

- the scoring run's id, and the embedding model the run was minted under;
- the survivors entering the step, and those with a detected language, counted by language code;
- the sets the translations by their paths form;
- one row per hundredth of similarity, ascending, for every likeness: the cosine rounded down to the hundredth from its exact value, the likenesses on the row, of which **confirmed by their paths**, the two being in one set of translations by their paths, and of which **refuted by their paths**, one of the two being a translation by its path of a third occurrence in the other's language; and the rule: set `translationSimilarityFloor` to a row's value, and the likenesses on that row and every row after it are applied, none on the rows before;
- the translation sets found, those standing with two members in one language, and the copies removed, or with `preferredLanguages` unset, the sets waiting for it;
- the two keys and how they were read.

It holds counts, language codes, a run id and a model's name: no path, no title, no text.

### 8. What the operator is told

- **The step's lines.** The pairing pass is timed as a call (ADR-193 §1), with a line before it and a line after. The step ends on a line naming the scoring run, the translation sets, the occurrences removed as `translation-of` and the sets left standing.
- **`preferredLanguages` unset, with sets found**: the line says nothing was removed, how many sets wait, and to write the languages to keep into `preferredLanguages` in `profile.yaml`, most preferred first, reading `translations.html`.
- **`translationSimilarityFloor` unset, with likenesses found**: the line says how many likenesses were not applied, and that `translationSimilarityFloor` is read off `translations.html`.
- **`NextAction`** gives each of the two keys, where it is unreadable, a line in the shape ADR-120 gives `logTimestampShareFloor`. Unset, neither is reported there, as `outOfScopeNameEndings` is not: each ships unset, and unset removes nothing.
- **The README** names both keys in its table of values, with the page they are read off, and the page among the files of the working directory. `docs/architecture.md` §1.2 names `translation-of` among what stage 5 writes.

### 9. Where the rules live, and what is held

Every rule is `embedding`'s: `Languages`, §2's language folder and a stored Lingua name read as its code; `TranslationCandidates`, §3's likenesses, the mean vectors read in blocks as `NearestNeighbourGraph` reads them; and `Translations`, §2's pairing by paths, §4's sets and the copy kept, and §7's rows. `embedding` names Lingua's `Language` and `IsoCode639_1` for the closed list and the codes. The tasklet in `pipeline` reads the survivors a page at a time, their languages through `ExtractionMetrics.eachMeasuredFormOf` and their paths through the ledger, and hands them on; it holds no rule, and `PipelineHoldsOnlyTheRulesOnRecordTest` lists it so. No class of `corpus`, `extraction` or `similarity` changes.

**What the step holds, and why** (an addition to ADR-220 §9's list): for each survivor whose path holds a language folder, its id and its path; for each candidate, its id, its language and, for each other language, its nearest with the cosine; and the links and the sets. Each grows with the survivors carrying a language folder or a detected language, which in a corpus of several languages is nearly all of them, because the pairing compares paths and nearest neighbours across the whole run and not within a page. The mean vectors are read in blocks and not held.

**What it costs**: for each pair of languages L and L', |L| × |L'| cosines of mean vectors; for 100,000 candidates in two languages of 50,000 each, 2.5 × 10⁹. The step reports its progress over the pass as clustering does (ADR-192 §5).

### 10. Which run ids move

`embedding` changes, which the versions of seed measurement (`embedding`, `extraction`), embedding scoring (`embedding`, `extraction`), arrangement (`synthesis`, `extraction`, `embedding`) and generation name (`StageModules`, ADR-058), so those four move; the scoring run's `config_consumed` gains two values (§6), which moves it too. `profile` changes, which generation's version names. `ledger` and `pipeline` change, which no stage's version names. `corpus`, `extraction` and `similarity` do not change, so the walk and stages 1 to 4 do not move.

## Alternatives refused

- **`REDUNDANT_WITH` for a translation.** Refused in §1: it is stage 4's kind, and its row's score has no meaning for a translation.
- **Translations by their paths in stage 1, cheapest first.** A copy would be removed before either was converted, and a kept copy that then failed conversion or was found degenerate would take the other with it; and its vectors would not exist to calibrate the floor.
- **A step before 5c for the translations by their paths.** A seventeenth step, saving the embedding of the copies it removes, at the cost of the page's confirmed and refuted counts, the only measurement of the floor that does not need a person to read a document.
- **The detected language checked against the folder.** It refuses the pages in which identifiers outnumber words, which are the pages the rule exists for (§2).
- **A language folder in any case, or named in full.** `IT` and `HR` become Italian and Croatian, and a department's files are paired with certainty.
- **A confirmation of each likeness.** Refused in §3.
- **A copy kept by default where `preferredLanguages` is unset**, the lowest path or the corpus's most frequent language. Either removes the copy an operator may want, on a rule they did not write.
- **A gate on either key.** A corpus in one language would stop for a value that decides nothing in it, and the floor is read off a page only the step writes.
- **Another detector, or a question to the Ollama model about the language.** Lingua is the detector stage 2 already runs, locally, and its answer is stored.

## Consequences

- An archive holding a tree per language yields one copy of each text, in the language the operator prefers, once `preferredLanguages` is set; a translation elsewhere is removed once `translationSimilarityFloor` is set off the page.
- The label file, the relevance distribution, the clusters and the arrangement no longer carry a copy in a second language of what they already carry.
- The next invocation mints a new seed measurement run and a new scoring run: seed extraction from the extraction cache, the seed comparison, 5c from the vector cache, the new step, 5d's scores, and every step after them, again. Stages 1 to 4 are skipped as recorded.
- The verdict vocabulary is ten values, nine blocking; the job is sixteen steps; the profile has thirteen keys; a run-keyed table is added.

## Tests

| Method | What it holds |
| --- | --- |
| `ledger.VerdictKindTest.isExactlyTheRecordedVocabulary` | ten kinds |
| `ledger.VerdictKindTest.aTranslationRemovesTheCopy` | `TRANSLATION_OF` is blocking |
| `embedding.LanguagesTest.aLowercaseCodeOfALanguageIsALanguageFolder` | `en`, `zh`, `it`, `de`, `ja` are folders of their language |
| `embedding.LanguagesTest.aRegionOrAScriptAfterTheCodeKeepsItsLanguage` | `en_US`, `pt-BR`, `zh-cn`, `zh-Hans` are folders of `en`, `pt`, `zh`, `zh` |
| `embedding.LanguagesTest.noOtherSegmentIsALanguageFolder` | `IT`, `HR`, `En`, `english`, `eng`, `xx`, `en-`, `en_USA`, `e` and the empty segment are none |
| `embedding.LanguagesTest.aStoredLanguageReadsAsItsCode` | `ENGLISH`, `CHINESE`, `BOKMAL` read as `en`, `zh`, `nb`; no stored language reads as none |
| `embedding.TranslationCandidatesTest.eachIsALikenessOfItsNearestInTheOtherLanguageWhereThatIsMutual` | two pairs across two languages, with their cosines |
| `embedding.TranslationCandidatesTest.twoOfOneLanguageAreNeverALikeness` | two identical vectors in one language are no likeness |
| `embedding.TranslationCandidatesTest.oneWithNoLanguageIsNeverALikeness` | a vector with no language is no likeness |
| `embedding.TranslationCandidatesTest.aNearestThatIsNotMutualIsNoLikeness` | the second-nearest of a language is left out |
| `embedding.TranslationCandidatesTest.everyPairOfLanguagesIsAskedApart` | three languages, three likenesses |
| `embedding.TranslationCandidatesTest.theBlockSizeChangesNothing` | sixty vectors in three languages give the same likenesses read one at a time, seven at a time and all at once |
| `embedding.TranslationsTest.aCopyUnderAnotherLanguageFolderIsATranslationOfTheOneInTheFirstPreferredLanguage` | `zh/…` is `translation-of` `en/…` under `it, en`, by its path, though both are detected English |
| `embedding.TranslationsTest.theOrderOfThePreferredLanguagesChoosesTheCopyKept` | under `zh, en` the `en` copy goes |
| `embedding.TranslationsTest.withNoPreferredLanguageNothingIsRemovedAndTheSetIsCounted` | one set, no removal |
| `embedding.TranslationsTest.whereNoCopyIsInAPreferredLanguageTheLowestPathIsKept` | `en/`, `ja/`, `zh/` under `it`: `en/` is kept |
| `embedding.TranslationsTest.onlyOneLowercaseLanguageFolderInOneDirectoryMakesTranslationsByTheirPaths` | capitals, a file name, two segments apart, two depths, and two language folders in one path make no set |
| `embedding.TranslationsTest.twoCopiesInOneLanguageLeaveTheirSetStanding` | `en/`, `en_GB/`, `zh/` stand whole |
| `embedding.TranslationsTest.aLikenessAtTheFloorIsAppliedAndOneBelowItIsNot` | 0.93 at a floor of 0.93 is applied, 0.92 is not |
| `embedding.TranslationsTest.anUnsetFloorAppliesNoLikenessAndStillCountsThem` | no removal; the two rows are there |
| `embedding.TranslationsTest.aFloorSetToARowsValueAppliesThatRow` | a cosine of 0.29 as a double is on the row 0.28, applied at 0.28 and not at 0.29 |
| `embedding.TranslationsTest.theRowsCountTheLikenessesTheirPathsConfirmAndRefute` | one confirmed at 0.91; one refuted and one neither at 0.81 |
| `embedding.TranslationsTest.aSetJoinedByItsPathsAndALikenessKeepsOneCopy` | `en/` and `zh/` are `translation-of` an Italian copy, by both |
| `profile.LanguagesValueTest.theLanguagesAreReadInTheOrderWritten` | `it, en` reads as `it`, `en` |
| `profile.LanguagesValueTest.spacesCommasAndCapitalsAreReadAlike` | `IT en,De` reads as `it`, `en`, `de` |
| `profile.LanguagesValueTest.aLanguageWrittenTwiceKeepsItsFirstPlace` | `en, it, en` reads as `en`, `it` |
| `profile.LanguagesValueTest.aPieceThatIsNotTwoLettersMakesTheValueUnreadable` | `italian, en`, `it;en` and `e1` are unreadable |
| `profile.LanguagesValueTest.anUnansweredKeyIsUnset` | unset reads as unset |
| `profile.LanguagesValueTest.theProfileCarriesBothKeysAfterTheNameEndings` | the record's keys end `outOfScopeNameEndings`, `translationSimilarityFloor`, `preferredLanguages` |
| `pipeline.TranslationsInvocationTest.withNoPreferredLanguageBothCopiesStandAndAreScored` | first invocation: no `translation-of`, both scored |
| `pipeline.TranslationsInvocationTest.theCopyInTheLanguageNotPreferredIsATranslationOfTheOther` | after `preferredLanguages: en`, `zh/guide.txt` is `translation-of` `en/guide.txt`, by its path |
| `pipeline.TranslationsInvocationTest.aTranslationIsNotScored` | no relevance score for the removed copy |
| `pipeline.TranslationsInvocationTest.preferringALanguageIsAnotherScoringRun` | the two invocations' scoring runs differ |
| `pipeline.TranslationsInvocationTest.thePageNamesTheRunAndHoldsNoPath` | `translations.html` names the run and both keys, and no path |
| `pipeline.TranslationsInvocationTest.theProfilePointsBothKeysAtThePage` | both measurements name the page |
| `pipeline.CensusInvocationTest.runsTheStagesInOrderWithTheContentCensusAfterExtraction` | `translation` between `embedding-scoring` and `relevance-scoring` |
| `pipeline.FinishedStepsInvocationTest.everyStepRecordsItsWorkUnderItsOwnRunAndName` | `embedding-scoring / translation` is recorded finished |
| `pipeline.RunsAndNamesHaveOnePlaceTest.noPipelineSourceWritesAPersistedNameOutsideItsTwoTables` | `translation` is a persisted name, and `StepNames` holds sixteen |
| `pipeline.RunIdentityGoldenTest.embeddingScoring` | the scoring run records `"translationSimilarityFloor":null,"preferredLanguages":[]` |

## What this does not decide

- **A page listing the removed copies by path.** An operator auditing one asks one join of `translation_of`, as for stage 4 (ADR-082).
- **Language folders in capitals or named in full**, or a profile list of folder names.
- **A pass cheaper than every cosine across two languages**, and the measured heap and time of this one.
- **Whether the deliverable names a survivor's removed translations beside it.**
- **The wording of the step's lines and the page's look**, beyond §7 and §8.
