# ADR-262 — Stage 3 writes the page the boilerplate floor is read off

- **Date**: 2026-10-15
- **Status**: accepted on 2026-10-15.
- **Amends**: [ADR-075](0075-stage-3-writes-a-confidence-distribution-report-that-calibrates-tier-2.md), its finding that `boilerplateDocumentFrequencyFloor` *"needs no report of its own"* and its consequence that the key needs no `withXMeasurement` method, *"since its own tables are its pointer target directly"*. It carries a note at its head.
- **Fulfils**: [ADR-074](0074-stage-3-measures-shingle-document-frequency-a-boilerplate-floor-ships-unset.md)'s expectation of a stage-3 report the floor is read off. It carries a note at its head.
- **Keeps**: ADR-074's measurement, its tables and its omission of a hash found in one document; [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md), a page rewritten in place and rows kept per run; [ADR-260](0260-a-shingle-reaches-the-boilerplate-floor-by-exact-decimal-arithmetic.md), the cutoff `ceil(f × N)` and its §5 on the share rounded down to the hundredth; [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md), a table named in SQL only by its owner; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`.

## Context

Every other value an operator reads off a measurement is read off a page Vespera writes beside the database; the README's table of values names a page for each but `seedFolder`, `embeddingModel`, `generationModel` and `generationContextWindow`, which no measurement informs. `boilerplateDocumentFrequencyFloor` is read off `vespera.db` itself: the README gives a Python command that opens the database read-only and prints, for the latest finished content census, one line per share of the documents rounded down to the hundredth, with the number of passages on the line, the fewest documents any of them is found in (k) and the documents measured (N). The command is safe only while no `vespera` command runs and no `vespera.db-wal` lies beside the database, and it needs Python. A page in the working directory needs neither, and it is what can be copied out of the working directory to someone who may not open the database.

ADR-075 found the tables enough for the floor because an operator or stage 4 could query them. Stage 4 does query them (`BoilerplateShingles`, ADR-080); the operator has no page.

The rows the page needs are `similarity`'s: `shingle_document_frequency` and `shingle_corpus_size`, which only `similarity` names in SQL (ADR-209, held by `EachTableIsNamedOnlyByItsOwnerTest`). Stage 3's other page, `confidence-distribution.html`, is computed by its owning module (`extraction.ConfidenceDistribution`), handed back as a value, and rendered and written by `ContentCensusTasklet` in `pipeline`.

## Decision

### 1. The page

**Stage 3 writes `document-frequency.html` in the working directory, beside `confidence-distribution.html`**, a fixed name held by the constant `ContentCensusTasklet.DOCUMENT_FREQUENCY_FILE_NAME`. It is one self-contained page through `ReportPage` (ADR-130), and holds:

- the content census run it was written for, by its id, as stage 3's line in the output names it;
- the documents measured, N, the `shingled_document_count` of that run for `ShingleParameters.DEFAULT`, the granularity stage 4 reads;
- one row per share of the documents, in ascending order, holding the share rounded down to the hundredth and written with two decimal places (`0.66`, `1.00`), the number of passages whose share rounds down to it, the fewest documents any of them is found in, and N;
- the rule of ADR-260 §5, stated for the page: set the floor to the share a row shows, and the passages on that row and on every row after it are boilerplate, none on the rows before; or set it to the row's fewest documents divided by the documents measured, written in full or cut off after at least as many decimal places as N has digits; never rounded up, since 2 / 3 rounded up to 0.67 leaves out the passages found in 2 of 3 documents.

A passage found in one document has no row in `shingle_document_frequency` (ADR-074) and is on no row of the page. Where no document carried a passage, N is 0 and the page has no row.

The page holds counts and shares, and a run id. It holds no text of a document, no hash and no path.

### 2. Where it is computed

**`similarity` computes the rows; `pipeline` renders and writes them**, the shape of `ConfidenceDistribution` and `ConfidenceDistributionReport`. `DocumentFrequency` gains:

```java
public Shares shares(RunId stage3RunId)

public record Shares(long shingledDocumentCount, List<ShareLine> lines) {}

public record ShareLine(BigDecimal share, long shingles, long fewestDocuments) {}
```

`Shares` and `ShareLine` are nested in `DocumentFrequency`. `share` has a scale of 2 and is `floor(100 × k / N) / 100`, the same for every k on its line. `shares` reads the run's rows of `ShingleParameters.DEFAULT` only, and answers `Shares(0, List.of())` where `shingle_corpus_size` has no row for them.

The read sorts nothing: it reads the run's rows of the granularity through the table's primary key and counts them in the heap by `document_count`, of which there are at most N. `EveryStatementThatSortsIsRecordedTest` holds it, and ADR-218 gains no exception. Its cost grows with the run's rows of `shingle_document_frequency`, and the call is reads and arithmetic with no file and no model, so `ContentCensusTasklet` times it as one (ADR-193 §1), with a line before it and a line after (ADR-193 §4.3):

```
Stage 3 (content census) is reading the shares of the documents
Stage 3 (content census) read the shares of the documents in <S> s
```

`<S>` is the call's wall clock in seconds to one decimal place. A stage 3 skipped as already recorded makes no call and writes neither line (§3).

`pipeline` renders `Shares` in a class of its own beside `ConfidenceDistributionReport`, on record in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule: the rounding and the grouping into lines are `similarity`'s.

### 3. When it is written

**It is written every time stage 3 measures**, after the document frequency is measured and before the step is recorded finished, and overwrites whatever page an earlier run left (ADR-077). **A stage 3 skipped as already recorded leaves it as it is**, as it leaves `confidence-distribution.html` (ADR-115, ADR-116): the page was written from those very rows, and rewriting it without measuring would say the stage looked again. The page names its run so that a page left by another run can be told apart.

### 4. The profile points at it

`Profile` gains `withBoilerplateDocumentFrequencyFloorMeasurement(Measurement)`, mirroring `withDegenerateOutputConfidenceFloorMeasurement`, and stage 3 calls it when it writes the page, with the page's path and the time it was written. The operator's value and provenance are left as they are. The javadoc of `Profile`'s `boilerplateDocumentFrequencyFloor` says the key points at the page.

### 5. What the operator is told

- **`NextAction`** says, for `boilerplateDocumentFrequencyFloor`, that it is read off `document-frequency.html`, which stage 3 writes: the line is the same for `vespera label`, which runs no stage 3, and for a `vespera run` whose stage 3 is skipped and writes nothing (§3). It no longer names `similarity`'s tables.
- **Stage 4's gate line** (`RedundancyJobConfiguration.logGateClosed`) says to read `document-frequency.html` (stage 3's measurement) to choose a value. It no longer names the tables.
- **The README** names `document-frequency.html` as the page `boilerplateDocumentFrequencyFloor` is read from, in its table of values and among the files of the working directory, and states the rule of §1. It keeps the Python command for one case: reading a content census other than the one the page names, which happens when an invocation goes back to a run recorded earlier and stage 3 is skipped (§3).
- **`docs/running-stage-by-stage.md`** names the page among what stage 3 leaves, and has stage 4's *Set first* read the floor off it.

## Alternatives refused

- **The page rewritten from the stored rows when stage 3 is skipped.** It would always show the run the invocation used, and the README would need no command; it is refused for the reason §3 gives, which is the rule `confidence-distribution.html` follows.
- **The read written in `pipeline`.** It names `similarity`'s tables, which `EachTableIsNamedOnlyByItsOwnerTest` refuses (ADR-209), and it would leave no stage's run id moved for a page that is part of stage 3's output.
- **A `GROUP BY document_count` in SQL.** SQLite sorts the run's rows of `shingle_document_frequency` for it in temporary storage, a statement whose temporary files grow with the corpus (ADR-218); counting in the heap needs at most N entries.
- **The page named for boilerplate.** A row of the page is a share of the documents; which rows are boilerplate is the floor's, and stage 3 judges nothing (ADR-074).

## Consequences

- An operator reads the floor off a page in the working directory, as every other value read off a measurement is read, with no Python and no access to `vespera.db`.
- `similarity` changes (§2). The versions of stage 2 (`extraction`), stage 3 (`content-census`) and stage 4 (`content-redundancy`) name it (`StageModules`, ADR-058), so those three stages move. Seed measurement, embedding scoring, arrangement and generation move through their upstream runs (ADR-048). `profile` changes (§4), which the version of `generation` names; that stage moves anyway. `pipeline` changes (§1, §5), which no stage's version names (ADR-226). The walk and stage 1, whose version names `corpus` alone, do not move.
- Stage 2's run is new, and its converter answers stay in the extraction cache ([ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md)): the next invocation repeats stage 2 from the cache, writing its shingle rows again under the new run beside the earlier run's, then stage 3, which writes the page, then stage 4 under the floor set.
- No schema, cache key or verdict kind changes.

## Tests

| Method | What it holds |
| --- | --- |
| `similarity.DocumentFrequencySharesTest.eachLineIsAShareRoundedDownWithItsPassagesAndTheFewestDocuments` | over 200 documents, counts of 2 and 3 share the line 0.01, 133 is on 0.66 and 200 on 1.00, with the passages on each line summed and another granularity left out |
| `similarity.DocumentFrequencySharesTest.aRunThatMeasuredNoDocumentHasNoLine` | no `shingle_corpus_size` row: N is 0 and there is no line |
| `similarity.DocumentFrequencySharesTest.everyLineStartsAtTheFirstCountWhoseShareRoundsDownToIt` | for every N up to 300, the lines partition the counts 2 to N, each line's fewest documents being the first count on it |
| `similarity.DocumentFrequencySharesTest.aFloorSetToALinesShareMarksThatLineAndTheLinesAfterIt` | for every N up to 300, the share of each line, read as the profile reads a value and resolved by `BoilerplateShingles`, marks the counts from that line's fewest documents to N and none below |
| `pipeline.StageThreeWritesTheDocumentFrequencyPageTest.thePageShowsEachShareOfTheRunItMeasured` | after stage 3 over three documents sharing passages, `document-frequency.html` exists, names the run, and shows 0.66 for 2 of 3 and 1.00 for 3 of 3 with their passages, fewest documents and N |
| `pipeline.StageThreeWritesTheDocumentFrequencyPageTest.thePageHoldsNoPathAndNoTextOfADocument` | the page names neither the archive, the working directory nor a file of the archive, and states the floor's key |
| `pipeline.StageThreeWritesTheDocumentFrequencyPageTest.theProfilePointsTheFloorAtThePage` | `boilerplateDocumentFrequencyFloor`'s measurement is the page's path and the time stage 3 wrote it |
| `pipeline.StageThreeWritesTheDocumentFrequencyPageTest.aStageAlreadyRecordedLeavesThePageAsItIs` | a second execution of the recorded step does not rewrite the page |
| `pipeline.TheFloorIsReadOffTheDocumentFrequencyPageTest.theLastLineNamesThePage` | with nothing set, `NextAction`'s line says the floor is read off `document-frequency.html, which stage 3 writes`, and names no table of `similarity` |
| `pipeline.TheFloorIsReadOffTheDocumentFrequencyPageTest.theShutGateNamesThePage` | stage 4's gate line names `document-frequency.html`, for an unset key and for an unreadable one |

`NextActionTest.withOnlyTheSeedFolderSetTheLineCreditsIt` holds that what informs a value is said once in the line, counting `shingle_document_frequency`; `RedundancyGateLineTest.anUnansweredKeyIsReportedAsUnset` holds that the gate line points at the tables. Each counts the page's name instead. `RedundancyResolutionReportsItsProgressInvocationTest.countsEveryLoop` holds stage 3's statement lines to an exact list, the two lines of §2 last.

## What this does not decide

- **A page for a content census other than the latest that measured.** The README's command reads one.
- **The page's look beyond §1**: its title, its wording and any bar beside a row are the renderer's.
