# ADR-232 — A file whose only text is in page headers and footers is `degenerate-output` at stage 2, and a seed of that kind is an unusable seed

- **Date**: 2026-10-10
- **Status**: accepted on 2026-10-10, **every point on the call of the session that settled #499, and none on an answer of the operator's to a question about it**. The operator's word of 2026-10-10, as the coordinating session passed it to that session, is *"tell the agents to make all decisions regarding their tickets"*; no question was put to a person, and §8 lists each call with its reasoning and what was put and not taken. One thing here is not the session's: that #499 joins the one build with #496 (§6), which reached the session through the coordinating session as decided. **Built** in the change that carries this record, which wrote the record, its tests and the operator's documents first and `src/main` after them: *What the commit that builds `src/main` owes* is what was built. Until it was built five tests failed (Tests); the test tree compiled. With it built, three tests added after it and one more at the architect's gate, `./mvnw test` ran 1,713 unit tests with none failed or skipped; the integration tests were not run for this record.
- **Amends**: [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md), in one sentence, tier 1's *"Extracted text that is empty, or carries no alphanumeric content once whitespace is normalised, is `degenerate-output`"*: the text tier 1 reads is the extracted text outside page headers and footers (§1). Tier 1 is otherwise as it was, and tier 2 is not touched.
- **Amends**: [ADR-145](0145-a-tables-cells-are-extracted-text-read-once-in-docling-reading-order.md), in two sentences: *"**Keeps**: ADR-070's tier-1 floor exactly as it is. What changes is the text it measures"*, where the text it measures changes once more, and *"`ExtractedText` feeds the metrics and the no-text floor"*, where it still feeds the metrics and the floor reads less than it (§1). The one reading of extracted text, its order and its table rows stand.
- **Amends**: [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md), in one sentence, *"A seed is unusable on exactly the ground stage 2's tier 1 already uses — extraction produced no alphanumeric content at all"*: the ground is still tier 1's, exactly, and tier 1's is now none outside page headers and footers (§2). *"No stricter bar is applied to seeds than to corpus documents"* stands and is why §2 follows from §1.
- **Amends**: [ADR-231](0231-the-embedding-step-stops-unrecorded-where-the-embedding-model-was-pulled-while-it-ran-and-scoring-refuses-a-seed-whose-chunks-have-no-vector.md), in these places and no others. §2a, *"A usable seed can have nothing to embed"* and what follows from it: a seed whose only text is in page headers and footers is not usable, so §2a's leave-out is reached by no such seed (§3). §9, call 13, which put recording such a seed as unusable and did not take it: taken here. Consequences, *"A seed whose only text is page headers and footers is usable, has no vector and wins no document … No report lists it and no row records it"*: it is an unusable seed, with a row. *What this does not decide*, its fifth and sixth bullets: both decided here, and the sixth's *"No ticket holds the behaviour"* is read as *#499 holds it*. And two texts its build wrote, which this record's build rewrites: the second paragraph of `scoreAndRecord`'s javadoc, and the words of the warning for a seed with no chunk (§3, §4). Everything else ADR-231 decides stands, §1 and §2 whole.
- **Applies, and does not amend**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md)'s closed vocabulary, to which nothing is added; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), by which the rule is `extraction`'s and `pipeline` asks it; [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md), nothing here adding a table or a delete; `CONTEXT.md`'s *Unusable seed*, which gains half a sentence (§8, call 9).
- **Rests on**: the code at `3166574e`; one invocation test that fails on it as the ticket said it would (Context); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and `StageModules`, read for which run ids move (§5). No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)), and how often a real archive holds such a file was not measured.
- **Decides** [#499](https://github.com/algernon28/vespera/issues/499).

## Context

Two readers of one conversion disagree about what its text is.

- **Stage 2's tier 1** (`DegeneracyFloor`, over `ExtractedText`) reads every item `DoclingDocumentTexts` returns, whatever its label. A file with a letter or a digit anywhere clears it.
- **The chunker** (`HybridChunker.chunkStructured`) leaves out the items labelled `page_header` and `page_footer`: running headers and footers repeat on every page and carry nothing of the document.

**So a corpus file whose only text is in page headers and footers survives stage 2 and has no chunk.** The embedding step stores no vector for it, and `RelevanceScoring.scoreAndRecord` throws on a survivor with no stored vector. The scoring step fails and records no completion. Nothing changes between invocations, so every later invocation stops at the same file: one such file keeps a whole corpus from being scored. The message calls the case impossible by construction (#108).

**Confirmed, not only read.** On the code at `3166574e`, `pipeline.AFileOfPageHeadersAndFootersOnlyInvocationTest`, with two corpus documents that have a body and one whose conversion is a page header and a page footer: the invocation exits 1, and what it says it stopped on is `occurrence 8 has no stored chunk vectors to score; a corpus survivor with no chunks was confirmed impossible by construction (#108)`.

**The seed side is the same disagreement.** A seed is usable where the text of every item holds a letter or a digit (`SeedExtractionItemProcessor`, through `UsableText`), which ADR-083 fixes as tier 1 exactly. ADR-231 §2a met such a seed, left it out of the seeds scoring reads with a warning, and named recording it as unusable *"the better place for it"*, left because no ticket held what *usable* means. #499 holds it.

## Decision

### 1. Tier 1 reads the text the chunker cuts chunks from

**A conversion with no letter and no digit outside its page headers and footers is `degenerate-output`**, at stage 2, unconditionally, as tier 1 always was. The page headers and footers are the items the chunker leaves out, by the same labels, held in one place in `extraction` so that the two readers cannot disagree again.

**Two reasons, one for each fact.**

- No letter or digit anywhere: `zero alphanumeric content after whitespace normalisation`, as before, word for word.
- A letter or a digit somewhere, and none outside page headers and footers: **`zero alphanumeric content outside page headers and footers`**. A person who opens such a file sees text; the reason says why it was removed all the same.

Tier 1 comes before tier 2, as before, so a file of headers and footers with a low confidence score carries tier 1's reason.

**The existing kind is true of the file.** `degenerate-output` is *"extraction produced text carrying no usable content"*. The vocabulary is closed (ADR-057), and no kind is added.

**The measurement does not change.** `extraction_metric` still counts every item, headers and footers included, in every column; `alphanumeric_char_count` of such a file is not zero. What is measured is what was extracted; what tier 1 judges is whether any of it is the document. No column, no DDL.

**A body of punctuation beside a page header with letters is removed too.** Tier 1 reads empty, whitespace-only and punctuation-only text alike (ADR-070), and it now reads them of the text outside headers and footers. Before this record that file survived, was cut into one chunk of punctuation and was scored.

### 2. A seed with no letter and no digit outside its page headers and footers is an unusable seed

ADR-083 has the seed's bar be tier 1 exactly and no stricter, and `UsableText` exists so that the two are one implementation. §1 moves tier 1, so the seed's bar moves with it: such a seed is recorded in `unusable_seed` at seed extraction, under the same reason as §1's, and is reported where unusable seeds are. It is not among the seeds the embedding step embeds or scoring reads, as no unusable seed is.

**It stops nothing**, as no unusable seed does. Where every seed of the folder is unusable, the invocation is gated as it is today for a folder of seeds with no text.

### 3. What becomes of ADR-231 §2a's leave-out

**It stands, and no seed of this kind reaches it.** `RelevanceScoring.residentSeedVectors` still leaves out a usable seed with no chunk and tells its caller, and `embedding.SeedChunks` and `ScoringProgress.seedLeftOutWithNoChunk` stay. A usable seed now has a letter or a digit outside its headers and footers, so the chunker cuts at least one chunk from it; a usable seed with no chunk stored is therefore one the embedding step never chunked, which ADR-231 §2a already said the chunk cache cannot tell apart and no shipped path reaches.

**The warning's words change, because they would be false where it is reached.** It said the seed's text is all in page headers and footers. It now says only what is known: the seed is recorded as usable and no chunk is stored for it (§4).

### 4. The stop's message

After §1 no shipped path reaches `scoreAndRecord`'s refusal by this way: every file stage 2 lets through under a run of this build has a chunk to cut. The message stops calling the case impossible by construction on the strength of #108, which was wrong about it, and says what makes it so and what is left:

`occurrence N has no stored chunk vectors to score under embedder identity I; stage 2 removes a file with no text outside page headers and footers, so a corpus survivor has a chunk -- either no stage ever examined this occurrence, or another pull of the embedding model wrote its vectors`

### 5. Which run ids move

A stage's implementation version is the last commit touching a module `StageModules` names for it (ADR-058), and a run's id is a hash of its recorded settings and of its upstream runs' ids too (ADR-048).

**The build touches three modules**: `extraction` (the rule, `UsableText`, `ExtractionMetrics`, the chunker's labels), `embedding` (`RelevanceScoring`'s message and javadoc) and `pipeline` (`SeedExtractionItemProcessor`, `RelevanceScoringTasklet`'s line). Nothing of `corpus`, `similarity`, `synthesis`, `profile` or `ledger`.

**Read against `StageModules` at `3166574e`**, where byte-level reduction names `corpus`; extraction `extraction` and `similarity`; content census and content redundancy `similarity` and `extraction`; seed measurement and embedding scoring `embedding` and `extraction`; arrangement `synthesis`, `extraction` and `embedding`; generation `synthesis`, `extraction`, `embedding` and `profile`:

- **Byte-level reduction keeps its run id.** It names `corpus` alone.
- **Extraction, content census, content redundancy, seed measurement, embedding scoring, arrangement and generation are each minted again**, every one naming `extraction`, and each through its upstream runs besides. That is every stage from 2 to 6b.

No recorded setting changes, so `RunIdentityGoldenTest` is not edited.

**This is the dearest move a change can make, and it is the price of putting the rule where it belongs.** A new stage-2 run judges every file again. Conversions are read back from the extraction cache, which is keyed by content and not by a run, and vectors are stored by content and by model; what the later stages wrote under their earlier runs stays and is not read by the new ones.

**In the one build it adds no move**: stages 2 to 6b are minted once already there (ADR-225 §8, ADR-228 §7, ADR-231 §7), **on the condition ADR-228 states**, that no build with one of these changes and without the others is run over a working directory first. Nothing in the repository prevents that.

**What does not move**: no DDL, so no schema version, no table and no index; no cache key, the chunker cutting the chunks it cut and `CHUNKER_IDENTITY` staying; no `run.stage` value, no `finished_step.step` value and no verdict kind. **One verdict reason is new**, and it is an unusable seed's reason too.

### 6. The one build

**#499 joins the one build with #496**, as the coordinating session passed it on, decided. ADR-231 §6, point 5, names #496 as that build's last ticket; with this record #499 is. No build is cut until its change is on `main` too.

### 7. The guards

- `EveryTableKeyedByARunIsOnRecordTest` is not edited: no table, and no statement that deletes from one keyed by a run. A verdict and an unusable seed are each written by the step that already writes them.
- `PipelineHoldsOnlyTheRulesOnRecordTest` loses one type from one list: `extraction.DoclingDocumentTexts` for `SeedExtractionItemProcessor`, which read the conversion's text to ask about it and now hands the conversion over. The rule is `extraction`'s; the processor asks `UsableText`, which it names already, and names no `VerdictKind`. No class is added to `pipeline`.
- `EveryStatementThatSortsIsRecordedTest` and `EachTableIsNamedOnlyByItsOwnerTest` are not edited: no statement is added or changed.

### 8. The calls, every one the session's

On the operator's word quoted under Status, the session that settled #499 made each of these and put no question to a person.

1. **A verdict at stage 2, under the existing kind** (§1). The operator's standing preference is that a bad row fails the occurrence and not the run; this is one row, known as soon as it is converted. *Put and not taken*:
   - **A new verdict kind.** The vocabulary is closed, `degenerate-output` is true of the file, and a kind is a value in `ledger`, an entry in `CONTEXT.md` and a case in every report that reads kinds.
   - **A verdict at stage 5.** Stage 5's kind is `below-threshold`, a measurement of relevance, which nothing measured of this file; `embedding` holds no power to remove a document (ADR-041, ADR-060); and the file would go through content census and content redundancy first, where its headers are shingles. It would move the run ids of stages 5 to 6b only, which in the one build saves nothing.
   - **A recorded fault at stage 5.** A row of a new table keyed by a run, which ADR-229's guard makes a decision of its own, and a survivor with no score that the floor's step, clustering, the relevance report, 6a and 6b would each have to learn to pass over.
   - **Leaving it unscored and reported.** The same survivor with no score, in the deliverable.
   - **The chunker embedding page headers and footers.** It changes the chunks of every document, the chunker's identity, every vector and every threshold read off them, to keep a file with nothing in it.
   - **Naming the case in the message and stopping still.** A stop of the run for one row.
2. **A reason of its own** (§1), not the existing one: *zero alphanumeric content* is false of a file with a header a person can read.
3. **The measurement is left as it is** (§1). Counting only the text outside headers and footers in `extraction_metric` would change what content redundancy ranks by and what the seed/corpus comparison measures, for every document, and is no part of this ticket.
4. **A body of punctuation beside a lettered header is removed** (§1): tier 1 is one rule over one text, and a second text for that one case is how the two readers came apart.
5. **Such a seed is an unusable seed** (§2). *Put and not taken*: **keeping ADR-231 §2a's warning**, which leaves the seed's bar and the corpus's two rules where ADR-083 has one, and tells the operator in a log line what every other unusable seed tells in a row and a report; and **refusing it**, which ADR-231 already put and did not take.
6. **ADR-231 §2a's leave-out stays, with other words** (§3). *Put and not taken*: **taking it out and refusing a usable seed with no chunk**, which is the truer reading of what such a seed now is, and undoes a type, a callback, one list of a guard and three tests for a state no shipped path reaches. It is left for a ticket that meets that state.
7. **The message is rewritten and not removed** (§4): the refusal is still reached by the two ways it names.
8. **The move of every run id from stage 2 on is accepted** (§5), because the one build has made it already. Outside that build it would be the first thing to weigh against call 1, and the verdict at stage 5 the first thing to weigh it with.
9. **`CONTEXT.md`'s *Unusable seed* gains half a sentence**: *"or none outside its page headers and footers (ADR-232)"*. No term is added and no `_Avoid_` line changes. **The operator is to be told of it.**
10. **`docs/running-stage-by-stage.md` is amended** under stage 2 and under 5c and 5d; `README.md` holds no sentence this record makes false and is not edited; `docs/decision-ledger.md` is closed to new entries.
11. **ADR-070, ADR-083, ADR-145 and ADR-231 each carry a note at their heads** naming this record and what it touches. No other line of those files is edited.
12. **ADR-231's fifth invocation test is taken out of its class and written again in this record's**, holding the opposite of what it held.

## Consequences

- **A corpus file whose only text is in page headers and footers is removed at stage 2**, with a reason that says so, and the corpus is scored without it. Before this record it stopped relevance scoring on every invocation.
- **Such a file's shingles are written and count for nothing**: a document carrying `degenerate-output` contributes to no document frequency (`DocumentFrequency`), and is no survivor for content redundancy to compare. Read from the code, not run.
- **A seed of that kind is an unusable seed**, with a row and its reason, wherever unusable seeds are reported. The warning ADR-231 §2a wrote for it is not written for it.
- **A seed folder holding only such seeds is gated as having no usable seed**, where it reached scoring's gated line after a warning for each.
- **Every stage from 2 to 6b runs under a new run** in the first invocation of the build (§5).
- **A file with letters in a page header and only punctuation outside it is removed**, where it was scored on a chunk of punctuation.
- **What a converter labels a page header is taken at its word.** A document whose body Docling mislabels as page headers throughout is removed as having none. The chunker already left that text out, so such a document was never scored on it; it now carries a verdict that says so, in place of stopping the run.
- **`scoreAndRecord`'s refusal no longer names a case that cannot happen and did.**

## Tests

Written with this record, before `src/main`. The test tree compiles: no test names a type or a method the build adds.

| Class | What it holds |
| --- | --- |
| `pipeline.AFileOfPageHeadersAndFootersOnlyInvocationTest` | new, two tests. With two corpus documents that have a body and one that converts to a page header and a page footer: the invocation succeeds, says nothing of a document with no stored vectors, the file carries one verdict, `DEGENERATE_OUTPUT` under the extraction run with §1's reason word for word, the two others are scored and scoring is recorded. With two seeds that have a body and one that converts to a page header: the invocation succeeds, one unusable seed is recorded with the same reason, the two others have vectors, both corpus documents are scored, scoring is recorded, and no line says a seed produced text and no chunk |
| `extraction.TextOnlyInPageHeadersAndFootersIsDegenerateTest` | new, four tests, through `ExtractionMetrics.writeAndJudge`. A page header and a page footer alone are degenerate under §1's reason, and `alphanumeric_char_count` still counts their 27 letters and digits; a page header beside a body of punctuation is degenerate under the same reason; a page header beside a paragraph is not degenerate; and no text at all keeps the reason it had |
| `pipeline.APullWhileTheEmbeddingStepRunsInvocationTest` | its fifth test, `aSeedWithNothingToEmbedIsLeftOutAndScoringGoesOn`, removed with the helper only it called, and its javadoc says where the case went (§8, call 12). Five tests were left, none edited, until the architect's gate added one (below) |
| `pipeline.SeedScriptedExtractionBeans` | a file name, `page-headers-and-footers-only.txt`, that converts to one item labelled `page_header` and one labelled `page_footer` |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | `extraction.DoclingDocumentTexts` off `SeedExtractionItemProcessor`'s list (§7) |

**Three tests were added after the build**, for three claims a tester found nothing held, and each passed at once:

| Class | What it holds |
| --- | --- |
| `extraction.TextOnlyInPageHeadersAndFootersIsDegenerateTest` | a fifth test: with a confidence floor of 0.50 set and a mean confidence of 0.10, a conversion with a paragraph is degenerate for its confidence, so the floor is in force, and one of a page header and footer alone is degenerate under §1's reason and not the confidence's |
| `embedding.ASeedWithNoVectorStopsScoringTest` | a fifth test: `scoreAndRecord` on a survivor with no stored vector throws an `IllegalStateException` that names the occurrence and the embedder identity, says *stage 2 removes a file with no text outside page headers and footers*, and does not say *impossible by construction*. No test held the words it had before |
| `pipeline.AFileOfPageHeadersAndFootersOnlyInvocationTest` | a third test: with a seed of a page header alone as the only seed, what `SeedExtractionInvocationTest.mintsNoRunWhenNoSeedIsUsable` holds of a seed with no text: exit 0, the line *No seed document produced any text … Fix the seed folder and run again*, no seed-measurement run, no scoring run and no `unusable_seed` row; and the line seed extraction writes for the seed gives §1's reason |

**Where no seed is usable the reason is on no row.** An `unusable_seed` row carries the measurement run that found it, and behind that gate there is none (ADR-083), so §2's *"recorded in `unusable_seed`"* holds where at least one other seed is usable. In a folder of such seeds alone the reason is in seed extraction's line for each seed, at info level, and the gated line says *No seed document produced any text*, which of a seed with a page header is loosely said: it produced none outside its headers and footers. The line is not changed here.

**One test was added at the architect's gate on PR 500**, which found that nothing reached the warning for a usable seed with no chunk once ADR-231's fifth test was gone: the two claims left about its words were that it is absent, which a rewording passes. `pipeline.APullWhileTheEmbeddingStepRunsInvocationTest.aUsableSeedWithNoChunkStoredIsLeftOutWithALineAndScoringGoesOn` removes one usable seed's rows from `chunk_cache` by hand and the row recording that scoring finished, as that class's fourth test does for vectors, and holds that the next invocation says *seed occurrence* and *produced text and no chunk*, says nothing of missing vectors, scores both corpus documents under the same run and records scoring. It does not claim the exit code: what the steps after scoring do where the winning seeds have changed under a run already arranged is ADR-231's Context. That class has six tests again.

**ADR-231's note on its tests was corrected in place** for the fifth test removed and restated here, and again for this one added. `docs/adr/README.md` names a test added or renamed as what such a note is corrected for; a test removed and restated elsewhere was read as the same kind of correction, and the architect's gate allowed it.

**Five tests fail until the build**, and no other of these classes:

- `PipelineHoldsOnlyTheRulesOnRecordTest`, the one test that holds the lists, on `SeedExtractionItemProcessor` still naming `extraction.DoclingDocumentTexts`.
- `AFileOfPageHeadersAndFootersOnlyInvocationTest.aCorpusFileOfPageHeadersAndFootersOnlyIsDegenerateOutputAndTheRestAreScored`, on the exit code, which is 1: the ticket's case.
- `AFileOfPageHeadersAndFootersOnlyInvocationTest.aSeedOfPageHeadersOnlyIsAnUnusableSeedAndScoringGoesOn`, on the unusable seeds, which are none. Its claim on the exit code passes before the build, by ADR-231 §2a.
- `TextOnlyInPageHeadersAndFootersIsDegenerateTest.aPageHeaderAndAPageFooterAloneAreDegenerate` and `aPageHeaderBesideABodyOfPunctuationIsDegenerate`, nothing being judged degenerate.

**What no test holds**:

- **The words of the warning for a usable seed with no chunk** beyond *seed occurrence* and *produced text and no chunk*, which the test added at the gate holds by invocation, and of `scoreAndRecord`'s message beyond the three phrases the test added after the build holds.
- **That an invocation in which that warning is written succeeds.** The test that reaches it does not claim the exit code.
- **That the chunker and tier 1 read the labels from one place.** The tests hold that they agree on `page_header` and `page_footer`.
- **A conversion read from Docling's `body` and `furniture` trees**; the fixtures are read from `texts` in list order. `DoclingDocumentTextsTest` holds that the labels come through either way.
- **A text file converted in parts** (ADR-178), whose items were not traced for this record.
- **Any real document.**

## What the commit that builds `src/main` owes

- **`extraction/DocumentText`**: `boolean pageHeaderOrFooter()`, true where the label is `page_header` or `page_footer`. **`extraction/HybridChunker`**: `NOISE_LABELS` goes and `chunkStructured` asks it. The chunks cut are the ones cut before, and `CHUNKER_IDENTITY` stays.
- **`extraction/UsableText`**:
  - `public static final String ONLY_IN_PAGE_HEADERS_AND_FOOTERS = "zero alphanumeric content outside page headers and footers";`
  - `public static Optional<String> whyUnusable(String rawDoclingResponse)`: `NO_ALPHANUMERIC_CONTENT` where no item's text holds a letter or a digit once whitespace is normalised; `ONLY_IN_PAGE_HEADERS_AND_FOOTERS` where some item's does and no item's for which `pageHeaderOrFooter()` is false does; empty otherwise.
  - `hasAlphanumericContent(String)` goes if nothing calls it once the seed pass asks `whyUnusable` (ADR-216).
- **`extraction/ExtractionMetrics.writeAndJudge`**: where `UsableText.whyUnusable` answers a reason, the verdict is degenerate with that reason, before tier 2 is looked at; otherwise as today. `DegeneracyFloor.evaluate(ExtractionMetric, Double)` keeps its signature and what it answers, so that `DegeneracyFloorTest` is not edited. Reading the conversion once for the metric and the rule, and not twice, was left to the build to arrange, **and the build did not arrange it**: `writeAndJudge` parses the conversion once for the metric and once more for the rule, for every corpus document stage 2 judges. That is a known cost left standing, not measured. `extraction_metric`'s values do not change.
- **`pipeline/SeedExtractionItemProcessor.doProcess`**: `UsableText.whyUnusable(response.rawResponse())` in place of `DoclingDocumentTexts.lines` and `hasAlphanumericContent`; where it answers a reason, `SeedExtractionOutcome.unusable(occurrenceId, contentHash, measurement, <that reason>)`. No condition of its own, and it no longer names `DoclingDocumentTexts`, which the guard's list now requires (§7).
- **`embedding/RelevanceScoring.scoreAndRecord`**: the message of §4, as `"occurrence " + occurrenceId.value() + " has no stored chunk vectors to score under embedder identity " + embedderIdentity + "; stage 2 removes a file with no text outside page headers and footers, so a corpus survivor has a chunk -- either no stage ever examined this occurrence, or another pull of the embedding model wrote its vectors"`. Its javadoc's second paragraph ends at two ways in, the third and *"The last is not handled …"* going, with ADR-232 cited for why a survivor has a chunk. No other statement of the method changes.
- **`pipeline/RelevanceScoringTasklet`**, the warning its `ScoringProgress` writes: `seed occurrence {} produced text and no chunk: it is recorded as usable and no chunk is stored for it, so it has no vector and is left out of the seeds every survivor is scored against`. It must still contain `seed occurrence` and `produced text and no chunk`, which the test added at the architect's gate holds; until that test nothing held either.
- **Javadoc that this makes false** (ADR-216 §7), in modules the build touches anyway: `UsableText` and `DegeneracyFloor`, on what tier 1 reads; `DoclingDocumentTexts` and `ExtractedText`, which say the floor reads every item; `HybridChunker`'s line on the labels; `SeedExtractionItemProcessor`, on the bar; `RelevanceScoring.residentSeedVectors`, where it gives page headers and footers as how a usable seed has no chunk.
- **Owed since the architect's gate on PR 500, which found this list short.** Each of these still called an unusable seed one that *produced no text*, which is false of a seed with a page header: `embedding/UnusableSeed`'s javadoc; `embedding/UnusableSeeds`' javadoc; `pipeline/SeedExtractionOutcome`'s javadoc, on the class, on `unusableReason` and on `unusable(…)`; and the comment above `unusable_seed` in `schema.sql`. And the comment in `extraction/ExtractedText.from` that says the floor measures what the chunker reads, which holds of the text outside page headers and footers and not of the text that method joins.
- **`schema.sql`'s comment above `unusable_seed` is corrected, and nothing else of the file.** The first writing of this list said the file is not edited, the comment's *"the bar is stage 2's tier 1 exactly"* staying true. That half does; the half that goes on *"no alphanumeric content at all"* does not. The comment's text alone changes: the line naming the table's owner stays as it is, and there is no DDL.
- **Edit no test and no Markdown.** A test the build finds it has to edit is a finding for the analyst.
- Verify with `./mvnw verify` under Java 26, and the docs gates.

## What this does not decide

- **Refusing a usable seed with no chunk stored**, now that it can only be a seed never chunked (§8, call 6).
- **What `extraction_metric` counts** (§8, call 3).
- **The gated line *"No seed document produced any text"*** (`SeedExtractionItemWriter`, `StageFiveGates`), which is loosely said of a folder whose seeds have a page header a person can read, and which this record's third invocation test now holds word for word. No ticket holds it yet.
- **Reading the conversion once in `writeAndJudge`** (*What the commit that builds `src/main` owes*).
- **A document whose body a converter labels as page headers** (Consequences). Nothing here second-guesses a label.
- **Any other label the chunker might one day leave out.** It would be left out of tier 1 by the same line, which is the point of §1's one place, and is a change to every document's chunks with a record of its own.
- **Telling the operator which files this removes in a working directory an earlier build stopped on.** The verdicts and their reasons are in the ledger and in what stage 2 reports of it; no line is added.
- **When the build ships**, beyond that #499 is its last ticket.
