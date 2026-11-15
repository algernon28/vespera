# ADR-392 — Vespera finds no translation sets: the translation step, its verdict, its report and its two profile keys go

> **Partly amended — see [ADR-394](0394-the-relevance-leg-runs-through-the-relevance-floor-and-the-clustering-leg-is-the-clustering-and-the-relevance-report.md).** Of §3: `relevance` also holds `relevance-scoring` and `relevance-floor`, `clustering` holding `clustering` and `relevance-report`, and the boundary inside stage 5 lies after `relevance-floor`. Of *What this does not decide*: the regrouping of `clustering` is ADR-394's. The rest of this record stands.

- **Date**: 2026-11-14
- **Status**: accepted on 2026-11-14.
- **Decided**: by the operator, on 2026-11-07, that "translation is going to be dropped": Vespera finds no translation sets and the translation step leaves the job, the deliverable's language being the profile's to name (ADR-321). By the operator, on 2026-11-14: that every copy of a content in another language survives and is clustered and generated from, the README naming `foldersNotWalked` as the way to leave a language's tree out, and stage 2 detecting each occurrence's language as it does (§1); that nothing is migrated, `TRANSLATION_OF` and `translation_of` going and a database that holds either keeping it unread (§2); that the two keys are taken as retired keys, warned of in one line only where one holds a value, and dropped at the next save (§4); that `translations.html`, its JSON, its schema and the `translation-of` fields of `report.json` go, `format` going to 2 (§5); which records are superseded and which amended (Supersedes, Amends); and that the leg named `translation` is renamed `relevance`, settling #770 (§3). Under the operator's delegation: the warning's words and when it is written, what holds a value, and what the control panel's form does with a retired key (§4); and what each table and count of the other records comes to (§2, §3, §5).
- **Supersedes**: [ADR-271](0271-one-copy-of-a-translation-set-survives-in-the-language-the-profile-prefers-first.md) whole: no translation set is found, nothing is `translation-of`, and its step, table, page, rules and keys go. It carries a note at its head.
- **Supersedes**: [ADR-273](0273-the-translation-step-reads-each-mean-vector-only-to-compare-it-and-the-profile-only-when-its-run-is-minted.md) whole, whose reads are of a step that no longer runs. The scoring run's identity is still read from one load of the profile when the run is minted, as [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) and [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md) §2 have it. It carries a note at its head.
- **Amends**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md)'s closed vocabulary, in the way that record provides for: `TRANSLATION_OF` leaves it, which is ten values, nine of them blocking (§2).
- **Amends**: [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) §7, which ADR-271 made sixteen step names: `StepNames` holds the fifteen §7 names, `translation` gone (§3).
- **Amends**: [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §5's table of the steps each run records, as `report.json`'s `runs` carries it (ADR-362 §3): a scoring run records three, `embedding-scoring`, `relevance-scoring` and `clustering`, and its verdicts are counted with no `translation-of` (§2, §3).
- **Amends**: [ADR-282](0282-an-occurrence-with-fewer-words-than-the-word-floor-is-degenerate-output-whatever-its-detected-format-and-stage-3-writes-the-page-the-floor-is-read-off.md) §8, whose translation step no longer runs: tier 3 still reads no language (§1).
- **Amends**: [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §2's row of the keys named one at a time where they hold something nothing can be read from: `translationSimilarityFloor` and `preferredLanguages` leave it (§4).
- **Amends**: [ADR-298](0298-the-embedding-model-ships-with-a-default-in-application-configuration-which-the-profile-overrides-and-stage-5-is-gated-only-where-neither-names-one.md) §4, whose name the survivors were scored under is recorded on the translations page too: it is recorded in the scoring run's settings and the label file's stamp (§5).
- **Amends**: [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md) §1, whose `deliverableLanguage` is written in ISO 639-1 "as `preferredLanguages` writes a language": it is written as two letters of ISO 639-1, `preferredLanguages` being no key (§4); and its refused alternative "the first of `preferredLanguages`", whose key is gone.
- **Amends**: [ADR-324](0324-the-timeline-of-the-steps-folds-into-four-legs-each-one-bar-open-where-the-job-is-and-a-fold-outlives-the-reload-in-the-pages-address.md) §1, whose second leg is `translation` and ends at the translation step: it is `relevance`, `seed-extraction`, `seed-corpus-comparison` and `embedding-scoring`, and the boundary inside stage 5 lies after `embedding-scoring` (§3).
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, whose control panel edits every value of `profile.yaml`: the two retired keys are no values of it, and the form admits their lines without a row (§4); and §9, whose `clusterResolution` follows `preferredLanguages` in the scoring run's `ConfigConsumed` and is unreadable "as `translationSimilarityFloor` applies nothing": it follows `relevanceScoreFloor`, and an unreadable one applies the preset (§6).
- **Amends**: [ADR-353](0353-the-embedding-models-shipped-default-is-qwen3-embedding-8b-and-only-a-profile-naming-no-model-moves-its-scoring-run.md) §3, whose `translationSimilarityFloor` enters the new scoring run as written, and §4, whose translation step takes clustering's block: neither key nor step exists.
- **Amends**: [ADR-361](0361-the-cockpits-steps-fold-into-the-progress-pages-four-legs-and-its-copy-of-their-table-is-held-to-the-pages-by-a-test.md) §1, whose `Legs.ALL` is `deduplication`, `translation`, `clustering` and `generation`: it is `deduplication`, `relevance`, `clustering` and `generation`, ADR-324 §1's table as this record amends it, over fifteen steps (§3).
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, whose `report.json` is of format 1: it is of format 2, with no `translation` step, no `translation-of` kind and no `translations` report (§5); §9, whose analysis reports are twelve: they are eleven, `translations` gone (§5); and §13's fifth step, whose ticket for `TranslationTasklet` has no class to write for.
- **Amends**: [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §4, whose texts hold a title, a short description and a tooltip for each of the two keys: they go; §5, whose `Reports.TITLES` holds `translations`: it does not; and §7, whose three list values are tags: two are, `codeNameEndings` and `outOfScopeNameEndings` (§4, §5).
- **Amends**: [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md) §2, whose stage 5 draws the translations and plots `translation-of`: its chart draws the seven steps of stage 5 §3 leaves, and of the kinds plots `below-threshold` alone (§3).
- **Keeps**: [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md) and stage 2's Lingua detection: `extraction_metric.primary_language` is detected and stored as before (§1).
- **Keeps**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) and [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md), which nothing here touches.
- **Keeps**: [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md): a key the profile does not have still fails the load; the two retired keys are named, and no other key is admitted (§4).
- **Keeps**: [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md): an earlier run's rows stay, and nothing deletes them because a newer run exists (§2).
- **Keeps**: ADR-362 §6's rule for a file an earlier version wrote, which stays where it lies and is read by nothing (§5).
- **Settles**: [#771](https://github.com/algernon28/vespera/issues/771) and [#770](https://github.com/algernon28/vespera/issues/770).

## Context

ADR-271 put a step, `translation`, between stage 5's embedding scoring and its relevance scoring. It joined occurrences carrying one content in different languages into a translation set, by their paths, the same path but for one language folder, or by their embeddings, two occurrences of different detected languages each nearest the other at or above `translationSimilarityFloor`, and kept the one in the language `preferredLanguages` names first, the rest each `translation-of` it, a blocking kind of its own with a row in `translation_of`. The step wrote `translations.html`, and since ADR-362 §9 `translations.json`; `report.json` counts `translation-of` wherever it counts verdicts by kind; the second leg of the report and the cockpit is named for the step. ADR-273 narrowed the step's reads.

The deliverable's language is a profile value, `deliverableLanguage` (ADR-321), so nothing in Vespera translates, and the operator has dropped the step: which of several copies in different languages is kept is no longer Vespera's to decide.

Stage 2 detects each converted occurrence's primary language with Lingua, locally, and stores it (ADR-073). Stage 4 removes copies within one language, by their shingles, which two languages do not share.

## Decision

### 1. Every copy in another language survives

**No translation set is found and nothing is removed for its language.** An occurrence carrying, in another language, a content another occurrence carries is a survivor as any other: it is scored, put on the labelling page where it is drawn, judged by the relevance floor, clustered, arranged and generated from, and the deliverable lists it. Two such copies may fall in one cluster or in two, and the arrangement may name clusters in either language, as it would for any two documents.

**Stage 2 still detects each occurrence's language** and stores it in `extraction_metric.primary_language` (ADR-073), and the content census reads it as it does. Nothing of stage 5 or 6 reads it.

**An operator who wants one language's tree left out** names its folder in `foldersNotWalked`, which the walk counts and never enters (ADR-340 §8): the README says so, beside that key's row, in the change that builds this. Nothing else collapses copies in different languages.

### 2. The verdict and its table go, and nothing is migrated

**`VerdictKind` loses `TRANSLATION_OF`**: ten values, `BROKEN`, `DUPLICATE_OF`, `SUPERSEDED_BY`, `OUT_OF_SCOPE`, `CODE`, `EXTRACTION_FAILED`, `DEGENERATE_OUTPUT`, `REDUNDANT_WITH`, `BELOW_THRESHOLD` and `PASSED`, in that order, nine of them blocking. Every reading of the verdicts by kind, `Verdicts.countsOfRun`, `recorded` and `discarded`, `InvocationAccount`'s counts and the job's samples, names the nine and no `translation-of`.

**`schema.sql` no longer creates `translation_of`** or its two indexes. A database an earlier version wrote keeps the table, its rows and its indexes, and no statement names them; nothing drops them, as nothing removes a run's rows (ADR-229). The tables keyed by a run are twenty-eight, `EveryTableKeyedByARunIsOnRecordTest` and `EveryStatementThatSortsIsRecordedTest` losing `translation_of` and its two indexes.

**A verdict row of kind `TRANSLATION_OF` an earlier version recorded stays**, under the scoring run it was recorded under. It removes nothing from any run an invocation arrives at: the survivors are those carrying no verdict of a kind `VerdictKind` declares blocking, which no longer names it, and no invocation arrives at a scoring run of an earlier version (§7). It is counted in no column, the counts by kind naming the nine kinds; where ADR-198 §3's counts meet a stored kind outside the enumeration, they count it as `other`, as they do now.

### 3. The step goes; the job is fifteen steps, and the second leg is `relevance`

**`translation` leaves the job.** `StepNames` holds the fifteen names ADR-157 §7 lists, in that order; `VesperaJobConfiguration` builds no translation step, and no line opens `Stage 5 (translations)`. **Relevance scoring scores every survivor of the seed measurement run**, as it did before ADR-271, and every step from 5e on reads the scoring run's survivors, as it does.

**A scoring run records three steps**, `embedding-scoring`, `relevance-scoring` and `clustering`, in job order (ADR-269 §5, `InvocationReport.STEPS_OF_A_STAGE`). A `finished_step` row of step `translation` an earlier version wrote stays, and is counted among no run's finished steps.

**The legs** (ADR-324 §1, ADR-361 §1), with every other leg as it was:

| Leg | Steps | Spans |
| --- | --- | --- |
| `deduplication` | `census`, `byte-level-reduction`, `extraction`, `content-census`, `redundancy-signature`, `content-redundancy` | stages 0 to 4 |
| `relevance` | `seed-extraction`, `seed-corpus-comparison`, `embedding-scoring` | stage 5, from the seeds to the embedding scoring |
| `clustering` | `relevance-scoring`, `relevance-floor`, `clustering`, `relevance-report` | stage 5, after the embedding scoring |
| `generation` | `arrangement`, `generation` | stage 6 |

`relevance` is the name of no step and no stage, so `InvocationReport` writes it as a literal, which `RunsAndNamesHaveOnePlaceTest` admits. `Legs.ALL` holds the same four, and `TheControlPanelsLegsAreTheReportsTest` holds it to the report's. The leg is drawn under its name and, in prose for the operator, as a *part*, as every leg is.

**What the operator sees of the steps**: the cockpit's fifteen rows and `StopCosts.ROWS`' fifteen, the translation step's description gone from `StepNames`; stage 5's chart (ADR-382 §2) drawing seed extraction, the seed-corpus comparison, the embedding scoring, the relevance scoring, the relevance floor, the clustering and the relevance report, and of the kinds `below-threshold` alone.

### 4. The two profile keys are retired: read, warned of, and left out of the next save

**`Profile` has no component for `translationSimilarityFloor` or `preferredLanguages`**, and `LanguagesValue` goes. No reader of the profile reads either.

**`ProfileStore.load` accepts both as retired keys.** A file that holds either, in the form a key takes (ADR-061), with `value`, `provenance` and `measurement` beneath it as an earlier version wrote them, loads as though it did not hold it, every other key read as it was; ADR-120 stands for every other name, a key the profile does not have still failing the load. `ProfileShapeCheck` passes such a file, being a load.

**A retired key holds a value** where its `value` is neither null nor blank. Where one does, the load writes one line at WARN, the first time this process loads the file, and never again in that process however often it is loaded:

```
<file> holds <keys>, which Vespera no longer reads, since every copy of a document in another language is now kept: it is ignored, and left out the next time Vespera writes the profile
```

`<file>` being the profile's path as `ProfileStore.file()` gives it, and `<keys>` the retired keys holding a value in this order: `translationSimilarityFloor`, `preferredLanguages`, `translationSimilarityFloor and preferredLanguages`; with both, *they are ignored* in place of *it is ignored*. **A retired key unset passes silently**: absent, `null`, a `value` null or blank, whatever its provenance or measurement say.

**The next save leaves both out.** `ProfileStore.save` writes the profile `Profile` holds, which has neither, so a profile census, a step or `vespera label` writes again holds neither key; every other key's value, provenance and measurement are written as they were read.

**The control panel** (ADR-295 §3, ADR-330 §6): `ProfileKey` loses `TRANSLATION_SIMILARITY_FLOOR`, `PREFERRED_LANGUAGES` and the kind `LANGUAGES`, and `profile-texts.json` their texts. Its form admits a retired key's block as it admits a whole line of comment (ADR-384 §6), drawing no row for it and keeping it byte for byte through every write, as it keeps every line it does not own; a file holding one is not refused and not repaired. The jar's next save is what removes it.

**What else stops naming them**: `NextAction` has no line for either (ADR-294 §2); `InvocationReport.REPORTS_OF_A_KEY` and `Reports.OF_A_KEY` no entry; the README's table of values no row, `docs/check-claims.mjs` reading it against `Profile`.

### 5. The page, its JSON and its fields go; `report.json` is of format 2

**`translations.html` and `translations.json` are written by nothing**: `TranslationReport` and `TranslationTasklet` go, and `docs/report/translations.schema.json` with them. A pair an earlier version wrote stays where it lies, read by nothing and listed nowhere, as ADR-362 §6 has it for the progress pages.

**The analysis reports are eleven**: `format-mix`, `extraction-failures`, `extraction-survivors`, `confidence-distribution`, `word-count-distribution`, `document-frequency`, `seed-corpus-comparison`, `relevance-labelling`, `cluster-sizes`, `arrangement` and `cluster-page-sizes`. `InvocationReport.ANALYSIS_REPORTS` and `FORMAT_OF_A_REPORT`, `Reports.ALL` and `Reports.TITLES`, and `AnalysisReportFile.FORMATS` name these and no `translations`, each list keeping its own order.

**`report.json` loses** the step `translation` from `steps` and every leg's list; the kind `translation-of` from `removing.verdicts`, `removing.perMinute`, `runs.rows[].verdicts` and `counts.verdicts`; the report `translations` from `analysisReports` and the enumeration of its names; and `translations.html` from every `gates[].analysisReports`. Its second leg is named `relevance`. **These remove fields and rename a value, so `format` is `2`**: `InvocationReport.FORMAT`, the schema's `const`, and the window's `InvocationReportFile.FORMAT`, all three in the one change. A `report.json` of format 1 left by an earlier version is one the window does not read, as ADR-362 §3 has it, until the next invocation writes its own.

### 6. The scoring run's settings

The scoring run's `config_consumed` loses `translationSimilarityFloor` and `preferredLanguages`: it is `root`, `embeddingModel`, `embeddingModelDigest`, `embeddingModelWeightDtype`, `measurementRunId` and `relevanceScoreFloor`, then ADR-330 §9's three, each written where its value is not the preset's, `clusterResolution` first. An unreadable `clusterResolution` applies the preset, as ADR-330 §9 decides.

### 7. Which run ids move

`embedding` changes: `Languages`, `TranslationCandidates`, `TranslationOf` and `Translations` go, `Clustering` no longer serving a translation step, and `embedding` names Lingua no longer. `StageModules` names `embedding` in the versions of seed measurement, embedding scoring, the arrangement and generation, **so those four move**; the scoring run's `config_consumed` losing two values (§6) moves it as well. `ledger`, `profile`, `pipeline` and the control panel change, which no stage's version names (ADR-222, ADR-274 §5). `corpus`, `extraction` and `similarity` do not change, **so the walk and stages 1 to 4 do not move.**

A relevance label is keyed by its path and its seed set, and not by a run (ADR-097), so every answer in the database carries over to the new scoring run; the labelling sample is drawn again under it, as under any new scoring run (ADR-088).

## Alternatives refused

- **Keeping the step and dropping its removal**, so that the page still counts the sets. A page whose numbers set no value is a report nobody acts on, and its step costs every cosine across two languages.
- **Collapsing copies by another rule**, the detected language against a profile list, or the corpus's most frequent language. Each removes a document by a rule the operator has said Vespera no longer applies; `foldersNotWalked` leaves a tree out where the operator names it.
- **Refusing a profile that still names a key.** ADR-120's reason for refusing an unknown key is a typo nobody would be told of; these two are known, and refusing a file an earlier version wrote would cost the pass that was about to rewrite it.
- **Ignoring the keys silently.** An operator who set `preferredLanguages` expects copies to be removed; one line says they no longer are.
- **Warning on every load.** The profile is loaded many times in one invocation, and the same line many times says nothing more.
- **Migrating the verdicts**, deleting `translation-of` rows or dropping the table. Nothing reads them, the scoring runs that hold them are not arrived at again, and ADR-229 deletes no run's rows.
- **Keeping `report.json` at format 1**, the fields left in, always empty. A field that can hold nothing is a field a reader has to be told about; ADR-362 §3 raises the format for a field removed.

## Consequences

- An archive holding a tree per language yields every copy in each language, scored, clustered and generated from, unless the operator names a tree in `foldersNotWalked`.
- The verdict vocabulary is ten values, nine blocking; the job is fifteen steps; the scoring run records three; the analysis reports are eleven; the tables keyed by a run are twenty-eight; `report.json` is of format 2.
- The next invocation mints a new seed measurement run, a new scoring run, a new arrangement run and a new generation run: seed extraction from the extraction cache, the seed comparison, 5c from the vector cache, the scores, the clusters and every step after them, again, with the labelling sample drawn again. Stages 1 to 4 are skipped as recorded. A new arrangement waits for approval unless `arrangementApproved` is `auto`.
- A working directory an earlier version used keeps `translation_of`, its `translation-of` verdicts, its `translation` completions and its `translations.html` and `translations.json`, read by nothing.
- A profile naming either key loads, says so once in the first invocation that loads it, and loses it at the next save.

## Tests

| Method | What it holds |
| --- | --- |
| `ledger.VerdictKindTest.theVocabularyIsTenValuesAndNoneSaysACopyIsInAnotherLanguage` | the ten values in order, no `TRANSLATION_OF` (§2) |
| `ledger.VerdictKindTest.onlyOneVerdictIsNonBlocking` | `PASSED` alone is not blocking (§2), its claim no longer naming `translation-of` |
| `pipeline.TheJobRunsFifteenStepsTest.theJobRunsTheFifteenStepsInOrderAndNoTranslationStep` | `StepNames` holds fifteen names, `InvocationReport.STEPS_IN_JOB_ORDER` is they in order, and neither holds `translation` (§3) |
| `pipeline.TheJobRunsFifteenStepsTest.aScoringRunRecordsThreeSteps` | the scoring run's steps are `embedding-scoring`, `relevance-scoring` and `clustering` (§3) |
| `ui.TheSecondLegIsRelevanceTest.theFourLegsAreDeduplicationRelevanceClusteringAndGeneration` | `Legs.ALL`'s four names and their steps, §3's table; `pipeline.TheControlPanelsLegsAreTheReportsTest` holds the report's to it (§3) |
| `pipeline.TheReportIsOfFormatTwoTest.theJarTheSchemaAndTheWindowSayFormatTwo` | `InvocationReport.FORMAT`, the schema's `format` and `InvocationReportFile.FORMAT` are 2 (§5) |
| `pipeline.TheReportIsOfFormatTwoTest.noFieldOfTheSchemaNamesACopyInAnotherLanguage` | the schema names no `translation-of`, no `translations` and no `translation`, and `docs/report/translations.schema.json` is gone (§5) |
| `pipeline.TheReportIsOfFormatTwoTest.theAnalysisReportsAreEleven` | `InvocationReport.ANALYSIS_REPORTS` and the window's `Reports.ALL` are the eleven, each in its own order (§5) |
| `profile.TheRetiredKeysTest.aProfileNamingARetiredKeyLoadsWithEveryOtherAnswer` | a file holding both keys loads, its other answers as written (§4) |
| `profile.TheRetiredKeysTest.aRetiredKeyHoldingAValueIsToldOfInOneLineOnce` | both holding a value: one line at WARN in §4's words, naming both, and none at a second load of the file (§4) |
| `profile.TheRetiredKeysTest.aRetiredKeyHoldingAValueIsNamedAloneBesideOneUnset` | the languages holding a value and the floor unset: the line names `preferredLanguages` alone, *it is ignored* (§4) |
| `profile.TheRetiredKeysTest.aRetiredKeyLeftUnsetPassesSilently` | a `value` of null with a provenance, and a blank `value`, write no line (§4) |
| `profile.TheRetiredKeysTest.theNextSaveLeavesTheRetiredKeysOut` | after a load and a save the file names neither key, and the other answers stand (§4) |
| `profile.TheRetiredKeysTest.anyOtherKeyThatIsNotTheProfilesStillFailsTheLoad` | `preferredLanguage`, one letter short, fails the load (§4, ADR-120) |
| `pipeline.RunIdentityGoldenTest.embeddingScoring` | the scoring run's `config_consumed` ends at `relevanceScoreFloor` (§6) |

ADR-271's and ADR-273's tests go with the classes they hold, and are listed in `docs/retired-tests.txt` against this record; each test that counts sixteen steps, four steps of a scoring run, twelve analysis reports, eleven kinds, twenty-nine run-keyed tables or the leg `translation` counts as this record does.

## What this does not decide

- **Whether the deliverable marks two documents of one content in different languages**, beside each other or in one cluster.
- **A profile list of languages to leave out**, in place of naming their folders in `foldersNotWalked`.
- **Removing `translation_of` and the earlier rows from a database**, which a record of its own would decide for every table an earlier version left.
- **#770's other legs.** `clustering` and `generation` keep their steps; regrouping them is a record of its own.
