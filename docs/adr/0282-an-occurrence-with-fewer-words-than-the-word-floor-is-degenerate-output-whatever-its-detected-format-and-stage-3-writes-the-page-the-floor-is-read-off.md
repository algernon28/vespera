# ADR-282 — An occurrence with fewer words than the word floor is `degenerate-output`, whatever its detected format, and stage 3 writes the page the floor is read off

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Amends**: [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md), its *"`degenerate-output` is a two-tier floor"*: the floor gains a third tier, after the two it has (§1). It carries a note at its head.
- **Keeps**: [ADR-232](0232-a-file-whose-only-text-is-in-page-headers-and-footers-is-degenerate-output-at-stage-2-and-a-seed-of-that-kind-is-an-unusable-seed.md), tier 1 and the text it reads, whole; [ADR-078](0078-tier-2-is-a-floor-on-the-mean-confidence-score-low-score-is-not-distributed.md), tier 2 a floor on `mean_score` alone, whose *"if such a rule is ever wanted it gets a key of its own"* this record follows (§3); [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md), `word_count` and the two garbage counters as measured, the counters still informing no threshold (§2); [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md), no stricter bar for a seed than tier 1 (§7); [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md), tier 1 unconditional; [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md), a numeric key's three states; [ADR-185](0185-stage-2-asks-the-converter-again-under-a-run-of-its-own-when-extractionattempt-is-raised-and-nothing-is-discarded.md) §1, a key of stage 2 read fresh at its mint and written into its identity only where it is set; [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md), a page that informs a threshold names it; [ADR-262](0262-stage-3-writes-the-page-the-boilerplate-floor-is-read-off.md), the shape of a stage-3 page and its pointer; [ADR-275](0275-every-div-of-every-page-vespera-writes-says-on-hover-what-it-shows-and-every-step-of-the-timeline-what-it-does.md), a hover on every `<div>`; [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md), a table named in SQL only by its owner; [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) and [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md), a page of survivors at a time and no set of them held; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`, and §6's reading of `extraction`'s one horizontal dependency, whose purpose widens again here and not its declaration (§4); [ADR-277](0277-code-is-recognised-by-a-name-ending-the-profile-lists-or-by-a-documentation-generators-mark-and-stage-1-takes-it-out-of-the-survivors.md), code kept apart at stage 1 and never converted (§6); [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md), a BMP out of scope at stage 1; [ADR-271](0271-one-copy-of-a-translation-set-survives-in-the-language-the-profile-prefers-first.md), the translation step and a `NULL` language (§8).
- **Settles**: [#576](https://github.com/algernon28/vespera/issues/576). That an occurrence with no recognisable text is removed whatever its detected format, that the measure is a word floor read off the corpus, and that pictures inside documents are untouched are the operator's; §1 to §9 are decided under the operator's delegation of 2026-10-19.

## Context

Stage 2 removes a converted occurrence as `degenerate-output` on two tiers. Tier 1 is unconditional: no letter and no digit outside page headers and footers (ADR-070, ADR-232). Tier 2 is a floor on `mean_score`, shipped unset (ADR-078). One stray glyph passes tier 1, and `mean_score` says nothing about whether a conversion holds text.

Measured on a public sample of 26 images converted by Docling with OCR ([research](https://github.com/algernon28/vespera/blob/research/docling-image-words/docs/research/docling-image-words.md)): of 18 images holding no text, 11 came back with no word, which tier 1 removes, and 7 with one to three stray tokens (`0`, `p`, `O`, `@0`, `Q`, `62`, `C000`), 6 of which pass tier 1. Three images holding a real label, "OK", "Annulla" and "Salva", came back with one word each. `mean_score` was 0.93 to 0.99 on every image, words or none. `ocr_score` was at least 0.999 on every real label and 0.55 to 0.93 on the stray tokens. The sample is not the operator's corpus.

`extraction_metric` stores, for every converted occurrence, `word_count`: the number of pieces the whitespace-normalised extracted text splits into on a space (`TextMetrics.words`), over every text item, page headers and footers included, and table rows (ADR-145). It stores two garbage counters beside it, `vowelless_word_count` and `single_character_word_count` (ADR-073). The first counts a word holding none of `aeiouAEIOU`, so it counts `2026`, every Cyrillic, Greek, CJK or Arabic word, and `rhythm` as garbage; the second counts every one-character word, so a word such as `a` or `7` is counted by both.

No page Vespera writes shows how many words the files it kept hold, or splits anything it measures by detected format: `confidence-distribution.html` is a distribution of `mean_score` over every survivor together. The detected format is stage 1's, in `corpus`'s table `detected_format` (ADR-095), and `DetectedFormats.formatFor` answers it for one occurrence by key.

## Decision

### 1. Tier 3: fewer words than the word floor

**A converted occurrence whose `word_count` is less than the profile's `degenerateOutputWordFloor` is `degenerate-output`.** It is the third tier of the floor, judged by `DegeneracyFloor.evaluate` after tier 1 and tier 2, so an occurrence that fails an earlier tier is given that tier's reason and no other:

1. tier 1, `UsableText.whyUnusable` and `alphanumeric_char_count == 0`, as ADR-232 has them;
2. tier 2, `mean_score` below `degenerateOutputConfidenceFloor`, as ADR-078 has it;
3. tier 3, `word_count` below `degenerateOutputWordFloor`.

Its reason is `String.format(Locale.ROOT, "%d word(s), below the configured word floor %d", wordCount, floor)`, for example `1 word(s), below the configured word floor 2`. The floor reaches the rule as a `Long`, `null` while it is unset or ignored: `DegeneracyFloor.evaluate(ExtractionMetric, Double confidenceFloor, Long wordFloor)`, `ExtractionMetrics.writeAndJudge(OccurrenceId, RunId, DoclingResponse, Double confidenceFloor, Long wordFloor)` and `OccurrenceJudge(ExtractionMetrics, FailuresInARow, RunId, Double confidenceFloor, Long wordFloor)` each gain it as their last parameter. No verdict kind is added: an occurrence below the floor holds no recognisable text, which is what `degenerate-output` says (ADR-057 is not touched).

**It applies to every detected format alike, with one value.** A `.txt` note, a `.url` shortcut, a Word document, a PDF and an image are held to the same number; nothing is keyed by format. A `null` `mean_score`, which exempts a `.docx` or `.txt` from tier 2, exempts nothing from tier 3.

**Pictures inside a document are untouched.** Tier 3 reads the occurrence's own `word_count`; a picture Docling found inside a document is shown with its document and judged by nothing (ADR-149).

### 2. A word is `word_count` as stored

**The measure is `extraction_metric.word_count`, as stage 2 computes and stores it, and nothing is subtracted from it.** It is not net of the garbage counters: those know only the Latin vowels, so subtracting them would remove a year, a part number, or a text in Cyrillic, Greek or CJK as having no words, and a one-character vowelless word would be subtracted twice. A word is whatever stands between two spaces of the normalised text, so `2026` is one word and `Привет мир` two. Page headers and footers count: a one-word body under a three-word header holds four words.

### 3. The key: `degenerateOutputWordFloor`

**`Profile` gains `NumericValue degenerateOutputWordFloor`, declared after `degenerateOutputConfidenceFloor`.** It is a key of its own, as ADR-078 and `Profile`'s javadoc ask of a new measure: the confidence floor is a score between 0 and 1 and this is a count of words.

- **It ships unset, per observe before enforce.** Unset removes nothing, and it is not a gate: no invocation ends for it, and the closing line does not name it while it is unset.
- **It is a whole number from 0 to 2,147,483,647**, read through `pipeline`'s `DegenerateOutputWordFloor.of(NumericValue)`, the shape of `ExtractionAttempt` (ADR-185 §1): `Unset`, `Floor(long words)` where the value is such a number (`2.0` is the floor `2`), or `Ignored(String text)` for anything else — text no number is read from, a fraction, a negative number, `NaN`, an infinity, or a number over the range. An ignored value removes nothing, as ADR-120 ignores an unreadable number, and the closing line says so in one line, quoting it back, after the line for the confidence floor: `Every run value is set, but degenerateOutputWordFloor reads "<text>", which is not a whole number of 0 or more, so this run ignored it and removed nothing for having too few words. Next: write a whole number of words, read off word-count-distribution.html, into degenerateOutputWordFloor in profile.yaml, and run again.` A floor of 0 or 1 removes nothing tier 1 has not, since an occurrence with a letter or a digit holds a word.
- **It enters stage 2's run identity.** `StageRuns.extraction()` reads it fresh from the profile when it mints stage 2's run, from the same load as `extractionAttempt`, and writes it into stage 2's `config_consumed` as `"degenerateOutputWordFloor":<words>`, after `extractionAttempt`, only where it is a `Floor`; unset or ignored, the member is not written, so stage 2's identity is the one it had before the key existed (`@JsonInclude(NON_NULL)` on the component, ADR-185 §1). `StageRuns.degenerateOutputWordFloor()` answers the floor the run was minted under, an `OptionalLong`, minting the run if it has not been; `ExtractionItemProcessor` hands that number to `OccurrenceJudge`, so the number that removes is the number that names the run (ADR-117). A profile edited after the mint is read by the next run.
- **Its `Measurement` points at `word-count-distribution.html`** (§5), through `Profile.withDegenerateOutputWordFloorMeasurement(Measurement)`, which stage 3 calls when it writes the page, as it calls `withDegenerateOutputConfidenceFloorMeasurement`.

### 4. Stage 3 measures the word counts by detected format

**`extraction` gains `WordCountDistribution`**, a `@Component` over `JdbcTemplate` and `Ledger`:

```java
public Distribution measure(
        RunId extractionRunId,
        Function<List<OccurrenceId>, Map<OccurrenceId, DetectedFormat>> formatsOf,
        ExtractionStatementProgress progress)

public static final List<Band> BANDS
public static final List<Long> CANDIDATE_FLOORS

public record Band(long fewestWords, long mostWords) {}

public record Distribution(Map<DetectedFormat, List<Long>> survivorsByFormat) {
    public List<DetectedFormat> formats()
    public long survivors(DetectedFormat format, Band band)
    public long survivors(Band band)
    public long removedBy(long floor, DetectedFormat format)
    public long removedBy(long floor)
}
```

- **What it counts.** Stage 2's survivors under `extractionRunId` that carry a metrics row under it, each once, in the band its `word_count` falls in, under its detected format. A survivor with no metrics row is not counted, as `ConfidenceDistribution` counts none.
- **How it reads.** A page of survivors at a time through `Verdicts.eachPageOfSurvivors`, that page's `word_count`s read from `extraction_metric` by its primary key, and that page's formats asked of `formatsOf`; no set of survivors and no list of word counts is held (ADR-211, ADR-220). A counted occurrence `formatsOf` gives no format is an `IllegalStateException` naming it: ADR-100 gives a survivor of stage 2 a detected format by construction. The read sorts nothing. It tells `progress` as `ConfidenceDistribution` does: `EXTRACTION_METRICS` started once with the span of stage 2's rows, the rows read after each page, ended once.
- **`BANDS`** are fourteen, each `[fewestWords, mostWords]`, the last with `mostWords` `Long.MAX_VALUE`: `[0, 1]`, then `2` to `10` one each, then `[11, 19]`, `[20, 49]`, `[50, 99]` and `[100, ∞)`.
- **`CANDIDATE_FLOORS`** are `2` to `10`, `20`, `50` and `100`: each falls on a band's lower edge, so what a floor removes is the sum of the bands below it. A floor of 1 is not among them for the reason §3 gives.
- **`Distribution`** holds, for each detected format with at least one survivor counted, one count per band in `BANDS`' order; its constructor refuses a list of another length (`IllegalArgumentException`), and `formats()` answers the formats in `DetectedFormat`'s declaration order. `removedBy(floor, format)` is how many survivors of that format hold fewer words than `floor`, and refuses (`IllegalArgumentException`) a floor not in `CANDIDATE_FLOORS`; the two overloads without a format sum the formats.

**`pipeline` composes `formatsOf`** in `ContentCensusTasklet` by asking `corpus`'s `DetectedFormats.formatFor(occurrence, stage1Run)` for each occurrence of the page, `stage1Run` being `StageRuns.upstream(BYTE_LEVEL_REDUCTION)`. `extraction` names `DetectedFormat` for this, a third purpose for the one horizontal dependency it declares beside ADR-100's and ADR-226's two; it names no table of `corpus`, and `corpus` does not change.

### 5. The page: `word-count-distribution.html`

**Stage 3 writes `word-count-distribution.html` in the working directory, beside `confidence-distribution.html` and `document-frequency.html`**, a fixed name held by `ContentCensusTasklet.WORD_COUNT_DISTRIBUTION_FILE_NAME`, through `ReportPage`, rendered by `pipeline`'s `WordCountDistributionReport.render(WordCountDistribution.Distribution, RunId contentCensusRunId)`, which holds no rule. It is written after the confidence distribution's read and before the shares of the documents are read, every time stage 3 measures; a stage 3 already recorded leaves it as it is (ADR-262 §3). The read states its lines as the confidence distribution's does, as `the word counts`, its progress lines under `Stage 3 (content census, reading word counts)`.

It holds:

- the content census run it was written for, by its id;
- **a table of the survivors by their words**, in a table card (`ReportPage.tableCard`) whose hover says what a row is: a header `Words`, then one column per detected format of `Distribution.formats()`, labelled in the words `format-mix.html` uses (`PDF documents`, `Images`, `Text`, …), read from `FormatMixReport`'s one table of labels, then `All formats`; one row per band, labelled `1 or fewer`, `2` … `10`, `11 to 19`, `20 to 49`, `50 to 99` and `100 or more`; and a last row `All`, each column's total;
- **a table of what a word floor would remove**, in a table card of its own whose hover says what a row is: a header `Word floor`, the same format columns, then `All formats`; one row per candidate floor, each cell the survivors of that format with fewer words than it. This is the cost the operator reads before writing a value: a one-line `.txt` note, a `.url` shortcut and a one-word image fall under a floor of 2 alike;
- what a word is (§2), that a floor removes a file with fewer words than it whatever its kind, and that the number is written into `degenerateOutputWordFloor` with its `provenance` (ADR-098), unset removing nothing.

Each count is written by `ReportPage.numberCell(long)`. The two hovers are fixed words, the same for every distribution (ADR-275 §2). **The page holds counts, a run id and the labels of detected formats; no path, no name and no text of a document.**

### 6. Code is reached only where stage 1 did not recognise it

A file stage 1 recognises as code is given the blocking verdict `code` and is never converted ([ADR-277](0277-code-is-recognised-by-a-name-ending-the-profile-lists-or-by-a-documentation-generators-mark-and-stage-1-takes-it-out-of-the-survivors.md)), so no tier of `degenerate-output` reaches it and a code set keeps its small files. This record adds nothing for code. A code file stage 1 does not recognise, its ending not listed in `codeNameEndings` and no documentation generator's mark on it, is converted as any text is, and tier 3 reaches it.

### 7. Seeds are not reached

The seed set's bar stays tier 1 exactly and no stricter (ADR-083): `UsableText` is unchanged and reads no word floor, and a seed of one word is a usable seed. **Unusable seed** in `CONTEXT.md` is unchanged.

### 8. The translation step

An occurrence removed by tier 3 is not a survivor of stage 2 and is never a member of a translation set. The translation step runs after stage 2 as ADR-271 has it, over what survives; an occurrence whose `primary_language` is `NULL`, as a short text's is where Lingua declines, is treated there as ADR-271 treats it, and tier 3 reads no language.

### 9. Run ids

`extraction` changes (§1, §4), which the versions of stage 2 (`extraction`), stage 3 (`content-census`), stage 4 (`content-redundancy`), seed measurement, embedding scoring, arrangement and generation name (`StageModules`, ADR-058), so those stages move; stage 1 and the walk, whose version names `corpus` alone, do not. `profile` and `pipeline` change, which no stage's version names (ADR-226, ADR-274 §5). Stage 2's identity text is unchanged while the key is unset (§3).

**Setting or changing `degenerateOutputWordFloor` mints a new stage-2 run**, and stages 3 to 6b follow through their upstream runs (ADR-048, ADR-154): the converter's answers come back from the extraction cache (ADR-183) and the vectors from the vector cache, keyed by content; relevance labels stay with their documents; the arrangement is approved again. Putting the earlier value back arrives at the earlier runs again (ADR-156).

## Alternatives refused

- **Words net of the garbage counters.** §2: they read every non-Latin script and every number as garbage.
- **A new verdict kind.** An occurrence with too few words holds no recognisable text, which is `degenerate-output`'s meaning; a second kind would split one condition across two names.
- **A floor for each detected format.** One value, read off one page that shows each format's cost, keeps the rule one sentence; a format the operator wants kept below the floor is an argument for a lower floor.
- **A gate the run ends at until the floor is set.** Every corpus would stop for a value most would leave at nothing; unset removes nothing, as the confidence floor's does.
- **The rule in `pipeline`, or the read of `detected_format` in `extraction`.** The first would put a rule where no stage's version sees it (ADR-226); the second names `corpus`'s table outside its owner (ADR-209).
- **Reading the floor once at start-up, as a bean, as the confidence floor is.** The run's identity and the judgement would read two loads of the profile; one read at the mint serves both (ADR-117, ADR-185 §1).

## Consequences

- An occurrence whose extraction holds fewer words than the operator's floor is removed at stage 2, whatever its kind, with a reason stating its words and the floor. **A floor of 2 also removes every image whose only text is a one-word label, such as a button's "OK", and every one-word text file**; the page shows how many of each format a floor removes before a value is written.
- Stage 3 writes a third page, and the profile's new key points at it; the README's table of values and its list of the files of the working directory name both, and `docs/check-claims.mjs` holds them.
- Stage 3 reads `extraction_metric` for the survivors a second time, and asks `detected_format` once per survivor by key.
- No schema, cache key, table or verdict kind changes.

## Tests

| Method | What it holds |
| --- | --- |
| `extraction.DegeneracyFloorTest.fewerWordsThanTheFloorAreDegenerate` | one word under a floor of 2 is degenerate, with the reason `1 word(s), below the configured word floor 2` |
| `extraction.DegeneracyFloorTest.asManyWordsAsTheFloorClearIt` | two words under a floor of 2 are not degenerate |
| `extraction.DegeneracyFloorTest.moreWordsThanTheFloorClearIt` | three words under a floor of 2 are not degenerate |
| `extraction.DegeneracyFloorTest.anUnsetWordFloorRemovesNothing` | one word with no floor set is not degenerate |
| `extraction.DegeneracyFloorTest.noAlphanumericContentIsTheReasonBeforeTooFewWords` | tier 1's reason, not tier 3's, for no letter or digit under a set floor |
| `extraction.DegeneracyFloorTest.aLowConfidenceIsTheReasonBeforeTooFewWords` | tier 2's reason, not tier 3's, for both at once |
| `extraction.DegeneracyFloorTest.aMissingConfidenceScoreExemptsNothingFromTheWordFloor` | a `null` `mean_score` is still held to the word floor |
| `extraction.AWordIsWhatStageTwoCountsTest.aYearAloneIsOneWord` | `2026` alone is one word, stored and judged, under a floor of 2 |
| `extraction.AWordIsWhatStageTwoCountsTest.wordsWithoutALatinVowelAreWords` | `Привет мир` is two words and clears a floor of 2, though the vowelless counter counts both |
| `extraction.AWordIsWhatStageTwoCountsTest.theWordsOfPageHeadersAndFootersCount` | a one-word body under a three-word header holds four words and clears a floor of 2 |
| `extraction.WordCountDistributionTest.theBandsAndTheCandidateFloorsAreTheRecordedOnes` | `BANDS` and `CANDIDATE_FLOORS` as §4 lists them |
| `extraction.WordCountDistributionTest.eachSurvivorIsCountedInItsBandUnderItsDetectedFormat` | survivors of three formats each in their band, formats in declaration order |
| `extraction.WordCountDistributionTest.aFloorRemovesTheSurvivorsWithFewerWordsThanItInEachFormat` | `removedBy` for each candidate floor and format, and summed |
| `extraction.WordCountDistributionTest.anOccurrenceStageTwoRemovedIsNotCounted` | a `degenerate-output` occurrence and another run's row are counted nowhere |
| `extraction.WordCountDistributionTest.theFormatsAreAskedForAPageOfSurvivorsAtATime` | over 2,500 survivors, `formatsOf` is handed pages of 1,000, 1,000 and 500 |
| `extraction.WordCountDistributionTest.aCountedSurvivorWithNoDetectedFormatIsRefused` | a counted survivor with no format handed over throws, naming it |
| `extraction.WordCountDistributionTest.aFloorNotAmongTheCandidatesIsRefused` | `removedBy(15, …)` throws, and so does a format's list of counts of another length |
| `pipeline.DegenerateOutputWordFloorTest` | the reading of §3: unset, whole numbers from 0, `2.0`, and the ignored values |
| `pipeline.StageTwoIsMintedUnderTheWordFloorTest` | unset or ignored writes no member; a floor writes `"degenerateOutputWordFloor":2`; the judge's floor is the one minted, not one saved after |
| `pipeline.WordCountDistributionReportTest` | the two tables' rows and columns, labels in `format-mix.html`'s words, and the key named |
| `pipeline.EveryDivOfAReportPageSaysWhatItShowsTest.bothTablesOfTheWordCountPageSayWhatARowIs` | two table cards, each with its own fixed hover, escaped, the same for two distributions |
| `pipeline.StageThreeWritesTheWordCountPageTest` | the page in the working directory over a real stage 1, naming its run; no path or text; the profile's pointer; a recorded stage leaves it as it is |
| `pipeline.TheWordFloorInvocationTest` | a one-word file removed with its reason under a floor of 2 and the rest kept; unset removes nothing and adds no stop; a one-word seed is usable under the floor |
| `pipeline.NextActionTest.aWordFloorThatIsNotAWholeNumberIsReported` | the closing line names the key, says whole number, quotes the text and names the page |
| `pipeline.NextActionTest.anUnsetWordFloorIsNotNamed` | an unset floor is not named in the closing line |
| `profile.DegenerateOutputWordFloorInTheProfileTest` | census drafts the key unset; an answer round-trips; the pointer method keeps the answer |

`ProfileTest.theRecordDeclaresASingleConstructor` counts fifteen keys, with ADR-277's `codeNameEndings`; `ProfileStoreTest.everyAnswerLandsOnTheKeyThatCarriedIt` answers the new key; `PipelineHoldsOnlyTheRulesOnRecordTest` lists `DegenerateOutputWordFloor` and `WordCountDistributionReport` as holding no rule and stage 3's new collaborators; `RedundancyResolutionReportsItsProgressInvocationTest.countsEveryLoop` holds stage 3's lines with `the word counts` between `the extraction metrics` and `the shares of the documents`; `RunIdentityGoldenTest.extraction` holds that an unset floor leaves stage 2's identity text as it was; `CascadeSliceTest` wires `WordCountDistribution`, and `TheReadsByKeyTakeTheLedgersPageTest` holds that it keeps no page size of its own.

## What this does not decide

- **An `ocr_score` floor.** A word count cannot tell a stray OCR token from a real one-word label; on the public sample `ocr_score` did. `extraction_metric.ocr_score` is stored already. A floor on it is a second measure, which gets its own key and its own record once it is measured on the operator's corpus.
- **A page listing, by path, what tier 3 removed.** A `degenerate-output` occurrence is on neither the review list nor the survivor list (ADR-250).
- **The page's look beyond §5**: its title, its wording and any bar beside a row are the renderer's.
