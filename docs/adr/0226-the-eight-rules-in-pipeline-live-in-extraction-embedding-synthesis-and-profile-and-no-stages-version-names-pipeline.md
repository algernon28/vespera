# ADR-226 — The eight rules ADR-222 found in `pipeline` live in `extraction`, `embedding`, `synthesis` and `profile`, and no stage's version names `pipeline`: generation's names `profile` in its place

- **Date**: 2026-10-10
- **Status**: accepted on 2026-10-10, on two kinds of answer kept apart in §11: **the operator's four answers**, and **the build-level calls** of the session that drove #479, to which the operator handed every further decision on the ticket. The commit that carries this record writes it and its tests and no line of `src/main`; the commit after it builds what *What the commit that builds `src/main` owes* lists. Between the two the test tree does not compile (Tests).
- **Built**: §2 to §9 were built on 2026-10-10 in the change that carries this record (#479), in the commit after the one that wrote it (84ea8b2a after 282ef7d7); the test tree did not compile between the two, and the build edited no test, the lists of §9 being the compiled classes' as written.
- **Amends**: [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) §3 and its table of the eight rules, which empty; §3's table of homes, whose rows 4 and 7, *"none today"*, each get one (§4, §7); §2's three rows *"unchanged, for rule 1"*, *"unchanged, for rules 2 and 3"* and *"unchanged, for rules 4 to 8"*; §4's fourth test, which holds the classes of five more stage runs (§9); §4's sentence *"In the classes of seed measurement, embedding scoring and generation a missed rule still moves the run id, because those stages name `pipeline`"*; and Consequences' *"it is reached when rules 1 to 3 have moved and rules 4 to 8 have moved or been decided otherwise"*, which this record reaches. Its rule, §1, stands, and its guard with it.
- **Amends**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md)'s Decision as ADR-222 left it: no stage's version names `pipeline`, because no class of `pipeline` holds a rule. And [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) §1's table as `StageModules` carries it, in three rows: `seed-measurement` and `embedding-scoring` lose `pipeline`, and `generation` loses `pipeline` and gains `profile` (§9).
- **Amends**: [ADR-190](0190-stage-6bs-loop-and-6as-lead-document-rule-live-in-synthesis-which-still-knows-no-stage.md): Context's *"What the move cannot take into `synthesis`"*, its first bullet, *"So `GenerationTasklet.exemplarsOf` stays …"*; §5's *"`GenerationTasklet` keeps the gates, the run, `once`, the gathering, …, `whyUnwritten` (ADR-174), `profileValues` (ADR-186) …"* and *"`exemplarsOf`, `openingChunkOf`, `hashOf` and `survivorsFor` keep their names and their bodies, so what ADR-133, ADR-151 and ADR-152 say about them stays true"*; and, in its *Sentences read and left standing*, *"ADR-133's sentence on `exemplarsOf` and ADR-152's table, because those methods stay"* and *"ADR-186's sentences on `profileValues`"*. The gathering, the reason a cluster is unwritten and every profile key as written leave `pipeline` (§4, §5, §7). Its loop, its lead-document rule and its callbacks stand.
- **Amends**: [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md) §3, its heading *"The list is derived from the record's components, in `pipeline`"*, its first sentence, and its bullet *"It stays in `pipeline`. … `profile` gains no method, so this decision moves no module but `pipeline`"*; and its Consequences paragraph *"Implementing this touches only `pipeline`, so it moves the run ids …"*, which no longer says what a change to the profile's keys costs (§7, §12). §1, §2 and §4 stand.
- **Amends**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md)'s purpose for `extraction`'s dependency on `corpus`, *"`corpus`'s two enumerations"* for the knowledge *"which of its pipelines a given class of file reaches"*. The declaration is unchanged: the same two enumerations and nothing else of `corpus`. The purpose widens to two more rules over a detected format, what a seed the floor stopped is sent as and which formats list their pictures (§2, §6).
- **Amends**: [ADR-150](0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md) §4's *"`pipeline` enforces it"* and *"The check sits there rather than in `synthesis` …"*: `extraction` decides it and `pipeline` asks (§6). [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md)'s *"`GenerationTasklet.exemplarsOf` drops a member whose opening chunk this run cannot reach"* and [ADR-152](0152-a-survivor-whose-file-will-not-open-is-still-asked-about-without-its-opening-and-a-step-that-records-completion-stops-instead.md)'s row for 6b's call and its *"the warning `openingChunkOf` logs"*: the drop is `synthesis`'s and the warning is written through `GenerationProgress` (§4).
- **Keeps**: ADR-222 §1, the rule; ADR-058's mechanism and its accepted failure; [ADR-048](0048-walk-and-run-identity.md)'s chain of upstream runs; [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) and [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md): no capability module gains a dependency, `synthesis` names no `profile`, `extraction` no type of `embedding`, `embedding` nothing but `ledger`; [ADR-118](0118-the-answers-a-person-gave-never-join-a-runs-identity-so-the-two-steps-that-read-them-record-no-completion.md): the floor's step withdraws every removal it has standing before it removes any, and records no completion; [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md): the floor's number joins the scoring run's identity and what it is let do does not; [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md) §4's order of reasons; [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md)'s reads, a fault asked for only for a cluster this invocation did not explain and a score asked by key in the loop that opens a member.
- **Rests on**: the design pass of 2026-10-10 on #479, three analysts and a skeptic for each group of rules, every proposed home put to a skeptic and one of them refuted and replaced (§3); its record is comment 6097304556 on #479. [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1, for riding a re-mint already owed. No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Decides** [#479](https://github.com/algernon28/vespera/issues/479).

## Context

ADR-222 read every class of `pipeline` and found eight rules, classes of `pipeline` that decide a verdict, a cache key, a cluster or text of the deliverable. They kept three stages naming `pipeline`: seed measurement for rule 1, embedding scoring for rules 2 and 3, generation for rules 4 to 8. So a change to any file of `pipeline`, a progress line or a command-line option among them, mints seed measurement and embedding scoring again, and through them (ADR-048) arrangement and generation, whose calls are not cached and are the most expensive this system makes. ADR-222 §3 said where six of the eight would go, and that rules 4 and 7 had no home under the module boundaries as they stood.

#479 asks that every rule move, so that no stage names `pipeline`, or that a record keep the ones that cannot.

Two facts shaped the answer.

- **Freeing one stage spares nothing while the stage before it still names `pipeline`.** The stages are a chain: content redundancy, seed measurement, embedding scoring, arrangement, generation (`StageRuns`). A commit to `pipeline` that no longer mints embedding scoring still mints seed measurement, and embedding scoring with it, through its upstream run. A change to `pipeline` alone spares generation only when all three stages have stopped naming it.
- **Rules 1 and 6 go to `extraction`, which every stage from extraction on names.** Moving either is a commit to `extraction`, so it mints stage 2 and every stage after it. ADR-216 §1 has a re-mint of stage 2 ride a change that already owes one. None was pending: the operator was asked (§11, answer 2).

## Decision

### 1. All eight move, in one change

The eight rules leave `pipeline` in one change set, built on top of ADR-223's (#485), which lands first and edits the same three classes, `GenerationTasklet`, `GenerationProgress` and `ClusterGeneration`. ADR-222 §3's allowance is then empty, and no stage names `pipeline` (§9). Each rule is moved as it is: no verdict, cache key, cluster, sentence of the deliverable, persisted name or line an operator reads changes, and the tests of Tests hold each one.

| # | ADR-222's rule | Home | What carries it |
|---|---|---|---|
| 1 | a seed the floor stopped is converted as `UNRECOGNISED` | `extraction` | `DoclingExtractor.convertSeed` (§2) |
| 2, 3 | where the floor applies, the reason a removal carries, and that nothing is removed or withdrawn where the vectors carry no single embedder identity | `embedding` | `FloorReach` (§3) |
| 4 | a cluster's members are sent as their leading chunks, one nothing was chunked from is left out, and one with no score stops the step | `synthesis` | `ClusterExemplars.gathered`, `OpeningChunk`, `OpeningText` (§4) |
| 5 | what this invocation found stands over a fault on record, and a cluster with neither was not reached | `synthesis` | `Unwritten.of(Optional<Unwritten>, Supplier<Optional<ClusterFault>>)` (§5) |
| 6 | an occurrence detected as an image, a BMP or a video lists no picture | `extraction` | `DocumentPicture.listedFor` (§6) |
| 7 | the index states every key of the profile, one with no value as empty | `profile` | `Profile.keysAsWritten` (§7) |
| 8 | a survivor with no score on record is listed with 0.0 | `synthesis` | `ListedSurvivor.of` (§8) |

What stays in `pipeline`, for each, is a lookup with no branch of its own, or the text an operator reads, or the write of a verdict whose kind and reason it is handed. ADR-222's line between a rule and wiring is the one used: wiring hands a capability module the recorded values its rule asks for and acts on the answer.

### 2. Rule 1: what a seed is sent as, in `extraction`

`DoclingExtractor.convertSeed(Path file, String contentHash, ExtractorIdentity extractorIdentity, DetectedFormat detected, DetectedSubtype subtype)` sends `FLOOR_STOPPED` on as `UNRECOGNISED`, every other format as it is, and calls the five-argument `convert` with the rest of the call as handed. It is a rule and not a lookup: it has a branch of its own.

`SeedConversions` stays in `pipeline` as wiring: it runs `BrokenCheck.check` and hands what it found to `convertSeed`. `formatFor` goes. `BrokenCheck` does not move: `extraction` may name `corpus`'s two detection enumerations and nothing else of it (ADR-100), and widening that is a decision this record does not make. The javadoc that went with `formatFor` cited stage 2's refusal, the stage-1 floor and the seed job not being fault-tolerant; `extraction` knows no stage, so on `convertSeed` it is restated without them: an occurrence with no bytes to read converts to a failure, which is the answer.

`ExtractorIdentity` is composed of the image and the versions the converter reports, and of no module's version, so no cache key moves and every conversion on record is found.

### 3. Rules 2 and 3: what the floor lets stage 5e do, in `embedding`

`embedding.FloorReach` answers the two actions the step takes, and not a classification of the floor:

- `static FloorReach of(Optional<String> embedderIdentity, OptionalDouble floor, Supplier<List<RelevanceLabel>> answers)`.
- `boolean withdrawsStandingRemovals()`: false only where there is no single embedder identity.
- `OptionalDouble removesBelow()`: present only where there is an identity, a number, and the answers' distinct identities are none or exactly that identity.
- `OptionalDouble floor()`, the number it was handed, and `List<String> answeredUnder()`, the distinct identities of the answers in the order read, empty where they were not read: for the lines the operator reads (below).
- `public static final String REASON = "relevance score below the floor set in the profile"`, the same bytes the step wrote.

`of` reads the answers once, before it returns, and only where there is an identity and a number: the timed read of the recorded answers is made at the point the step made it, after the embedder identities and before any removal is withdrawn, and not at all where it was not made.

**The first form was refuted.** The design pass first proposed a sealed type of four cases for `pipeline` to switch on. The skeptic showed that the switch would still decide in `pipeline` that nothing is withdrawn where there is no single identity and that only one case writes, so that, once embedding scoring stopped naming `pipeline`, an edit to that branch would change which removals stand and move no run id. A type that answers the actions leaves the step a straight line: withdraw if told to, remove below the number if one is given.

**`pipeline` keeps the wiring.** `RelevanceFloor` stays, with `FloorReach reachFor(Optional<String> currentEmbedderIdentity, String stage)`: it takes the floor's number from `RelevanceScoreFloorValue`, the value the scoring run is identified by, so the number that removes is the number that names the run; it reads the seed folder canonicalised by `Walk.canonicalRoot`, and supplies the answers through the timed `RelevanceLabels.forSeedSet` read, or none where no seed folder is named. Its `State` and three records go. `RelevanceFloorTasklet` reads the identity, asks `reachFor`, withdraws `BELOW_THRESHOLD` where told to, and where a number is given counts and writes `verdict(id, scoring, BELOW_THRESHOLD, FloorReach.REASON)` a page at a time, as now. The four lines it writes stay in `pipeline`, word for word, each chosen by what the reach answered. `RelevanceReportTasklet`'s notice of a floor not applied reads the reach too, the `', '` join of `answeredUnder()` staying in `pipeline` as operator text.

**Today's behaviour is kept exactly**, including that the case with no single identity withdraws nothing, which leaves removals standing after a pull that adds a second identity. That is a defect in the direction ADR-042 warns of, filed as [#486](https://github.com/algernon28/vespera/issues/486), and since the rule is `embedding`'s, its fix moves the run ids it should.

### 4. Rule 4: how a cluster's exemplars are gathered, in `synthesis`

ADR-222 §3 found no home for rule 4: the leading chunk is `extraction`'s to read, the score `embedding`'s and the question `synthesis`'s, and none of the three may name another. ADR-190 §3 met the same shape for a lead's title with a callback `synthesis` owns and `pipeline` implements, `DocumentTitle`. Rule 4 takes the same form:

- `@FunctionalInterface public interface OpeningChunk { Optional<OpeningText> of(OccurrenceId occurrence); }` and `public record OpeningText(String text, int wordCount)`.
- `static List<Exemplar> gathered(List<OccurrenceId> members, Function<OccurrenceId, Optional<Double>> scoreOf, OpeningChunk openingChunk, GenerationProgress progress)` on `ClusterExemplars`, the interface `GenerationTasklet` already implements for the walk. Not a static factory on `ClusterMaterial`, a value record `ClusterCall` carries, and no new class.
- `GenerationProgress` gains `default void nothingChunkedFrom(OccurrenceId occurrence) {}` and `default void occurrenceOpened() {}`.

For each member, in the order given and exactly as `exemplarsOf` did: its score is asked for; with none, the gathering stops with the `IllegalStateException` the step stopped with, in the same words; its opening chunk is asked for; where there is none, `nothingChunkedFrom`; then `occurrenceOpened`; then a member with no opening chunk is left out, and one with an opening becomes an `Exemplar` with its score. The exemplars come back in member order.

`pipeline` implements the two new methods with the line it wrote, at `WARN` under `GenerationTasklet`'s own logger, and with `documentsOpened.itemDone()`. Its `openingChunkOf` becomes a lookup with no branch: the key recorded under the extraction run, then `LeadingChunks.forContentHash`, mapped to an `OpeningText`. `exemplarsOf` goes. The score is read by key, as ADR-223 §5 has it. `StepFailure` renders an exception's message alone, so moving the frame that throws changes nothing the operator reads.

### 5. Rule 5: why a cluster is unwritten, in `synthesis`

`Unwritten.of(Optional<Unwritten> foundThisRun, Supplier<Optional<ClusterFault>> faultOnRecord)`, an overload beside `of(ClusterFaultKind)`: `foundThisRun` where present, else the recorded fault's kind, else `NOT_REACHED`. The fault on record is asked for only where this invocation found nothing, which keeps ADR-223's read. `GenerationTasklet.whyUnwritten` goes, and its javadoc's reasons move onto the overload.

### 6. Rule 6: which formats list their pictures, in `extraction`

`public static boolean listedFor(DetectedFormat format)` on `DocumentPicture`, the record whose `allOf` already decides which pictures a converted occurrence carries: false for `IMAGE`, `BMP` and `VIDEO`, true for every other format. Not on `DocumentPictures`, which is the reader of the extraction cache's pictures (ADR-041) and holds no rule. `pipeline` keeps the recorded-format read and the order of the calls, so an image survivor's cache key is still not read and the pictures are not asked for.

ADR-150 §4 put the check in `pipeline` *"because what it knows is where the pixels came from, which is a fact about extraction"*. It is now in `extraction`. It uses `DetectedFormat` for a rule about the deliverable's pictures, which is a new purpose for the one horizontal dependency the tree declares, and the purpose and not the declaration is what widens (Amends).

### 7. Rule 7: every profile key as written, in `profile`, and generation names `profile`

`@JsonIgnore public SequencedMap<String, String> keysAsWritten()` on `Profile`: every record component in declaration order, its name the key, its value the text the operator wrote, `""` where `value()` is `null`, unmodifiable. It is a method of the record and not a class: the test tree holds `profile.ProfileKeys`, the reading ADR-186 §4 has every test compare with. `@JsonIgnore` keeps it out of `profile.yaml`. `GenerationTasklet.profileValues` becomes a mapping to `NamedValue` with no branch, and `textOf` goes.

**Generation's version names `profile`, in the same commit** (§11, answer 1). `profile` owns the profile's shape (ADR-061), and `synthesis` may not name it (ADR-110), so this is the one home that needs a stage to name a module it did not. A rule in `profile` that no stage names would be ADR-222 §1's *"too lazy"*: the key set could change in a commit to `profile` alone and no run id would move, where ADR-186 §2 accepts only differing values under one run id.

### 8. Rule 8: a survivor with no score is listed with 0.0, in `synthesis`

`ListedSurvivor.of(OccurrenceId occurrence, OccurrencePath path, String contentHash, OccurrenceId winningSeed, String seedPath, int clusterOrdinal, Map<OccurrenceId, Double> scoresOnRecord)`, with `scoresOnRecord.getOrDefault(occurrence, 0.0)`. The canonical constructor stays, so no test that builds a `ListedSurvivor` changes. It moves as it is (§11, answer 3).

**Two homes of 0.0, and both stand.** `LeadDocument` weighs a member with no score as 0.0, for which member leads a cluster. `ListedSurvivor.of` lists one with 0.0, for the manifest's row and the page order. They are two outputs, each with one home; this record does not make them one.

### 9. `StageModules` and the guard

| Stage | Modules before | Modules after |
|---|---|---|
| seed-measurement | `embedding`, `extraction`, `pipeline` | `embedding`, `extraction` |
| embedding-scoring | `embedding`, `extraction`, `pipeline` | `embedding`, `extraction` |
| generation | `synthesis`, `extraction`, `embedding`, `pipeline` | `synthesis`, `extraction`, `embedding`, `profile` |

The other five rows are not edited. `profile` is written after the three it follows, the order `ImplementationVersions.of` joins them in.

`PipelineHoldsOnlyTheRulesOnRecordTest`, as ADR-222 §4 has it:

- **The allowance is empty.** `RULES_ON_RECORD` holds no class, and `SeedConversions`, `RelevanceFloor`, `RelevanceFloorTasklet` and `GenerationTasklet` are on record as holding no rule.
- **The verdict-kind and deliverable lists do not change.** `RelevanceFloorTasklet` still names `VerdictKind`: it withdraws and writes the removals it is told to, of the kind and with the reason it is handed. `GenerationTasklet` and `NextAction` still name `Deliverable`.
- **No stage names `pipeline`**, the third test, now that the allowance is empty.
- **The fourth test holds seventeen more classes**, those of seed measurement, embedding scoring and generation, whose versions no longer name `pipeline`: `SeedConversions`, `SeedExtractionItemProcessor`, `SeedExtractionItemWriter`, `SeedExtractionJobConfiguration`, `SeedExtractionOutcome`, `SeedCorpusComparisonTasklet`, `EmbeddingModelGate`, `SeedGate`, `UsableSeedGate`, `StageFiveGates`, `EmbeddingScoringTasklet`, `RelevanceScoringTasklet`, `RelevanceFloor`, `RelevanceFloorTasklet`, `ClusteringTasklet`, `RelevanceReportTasklet` and `GenerationTasklet`. Three of them, `SeedExtractionJobConfiguration`, `UsableSeedGate` and `StageFiveGates`, name no capability type and are listed with none, so that a first one fails.

**Not held closer, and why.** The pages and lines for the operator (`ClusterSizeReport`, `SeedCorpusComparisonReport`, `RelevanceLabellingReport`, as ADR-222 left `ArrangementReport` out): they write nothing the deliverable or a verdict carries. The labelling classes (`AutoLabelling`, `LabelIngestion`, `LabelFileReader`, `RelevanceLabelFile`, `DocumentOpening`): what they write are the answers, which no run's identity names (ADR-118), so a run id never guarded them. The profile values recorded on a run (`GenerationModel`, `GenerationContextWindow`, `RelevanceScoreFloorValue`): a different reading is a different run. The three are held by the first two tests alone, as ADR-222 §4 says of the classes it did not list.

**The lists are written from the compiled classes at `c3deb13`**, ADR-223's head, with the types the moves add and take away: `embedding.FloorReach` for the three classes that read the reach, `synthesis.OpeningChunk` and `synthesis.OpeningText` for `GenerationTasklet`; `profile.NumericValue` and `embedding.RelevanceLabel` off `RelevanceFloor`, and `profile.ProfileValue` and `synthesis.Exemplar` off `GenerationTasklet`. Run against `c3deb13` with this record's tests, the fourth test's failure names those four classes and no other: the lists of the other thirteen, `SeedConversions`' among them, which the build edits and whose types it does not change, are the compiled classes' exactly. Which names a compiled class carries depends on how its lambdas and locals are written, so a list can differ from the build's by a type in either direction. **The build reads the test's first failure and corrects the lists of these seventeen classes only**, each difference under ADR-222 §4's question: a type the class only hands values or takes an answer from is added or taken off; one it decides something by is a rule, and goes back to the analyst. That is the one edit of a test the build may make.

### 10. What `pipeline` still does that can change an outcome

ADR-222 named four kinds of wiring that can still change an outcome: which run a step reads, the usable seeds, `ChunkingRule.DEFAULT`, and what a step discards when it starts again. Until this record, those in stage 5 and generation were behind a run id that moved with any change to `pipeline`. They no longer are: the usable-seed subtraction in `EmbeddingScoringTasklet` and `RelevanceScoringTasklet`, the four calls that name `ChunkingRule.DEFAULT`, the run each step reads its survivors under and looks a key up under, and the kind `RelevanceFloorTasklet` withdraws, can each now change in a commit that moves no run id. They are wiring, and a wrong one is a defect a behaviour test holds; the fourth test lists the types those classes name, which is where a reader of the change is told to ask.

### 11. The operator's answers, and the build-level calls

**Four answers, given by the operator on 2026-10-10**, through the session that drove #479, each the recommendation put:

1. **Rule 7.** Generation's version names `profile`, and the rule moves to `profile`, accepting that a commit to `profile` alone mints generation again: about one such commit on `main` since 2026-09-01, against about 37 that touched `pipeline` and no other module. The alternative, a standing exception in `pipeline`, would have kept generation naming `pipeline` for good.
2. **One change for all eight**, built on #485, which lands first. Stages 2 to 6b are minted once, conversions are found in the cache, the arrangement is approved once more, and afterwards no stage names `pipeline`.
3. **Rule 8 moves as it is**, byte for byte. Removing it, so that a member with no score stops the write as a missing key does, was put and not taken: it changes behaviour ADR-222 records as a rule.
4. **The defect found on rule 3** is filed as #486 and not fixed here.

**The build-level calls**, made by the session that drove #479, which the operator handed every further decision on the ticket, and reported to the operator:

1. **The homes and shapes of §2 to §8**, the skeptics' amendments folded in: `FloorReach` answering the actions (§3); `ClusterExemplars.gathered` on the existing interface, beside `ClusterGeneration`, not on `ClusterMaterial` and not a new class (§4); the predicate on `DocumentPicture` and not on `DocumentPictures` (§6); the overload `Unwritten.of` in place of a method named for a cluster it does not take (§5); `keysAsWritten` a method of `Profile` and not a main class named `ProfileKeys` (§7).
2. **`RelevanceFloor` stays as wiring**, both floor steps asking it, and `FloorReach` carries `floor()` and `answeredUnder()` so that the lines the operator reads stay in `pipeline` with no second reading of the profile or the answers (§3).
3. **The answers are read inside `FloorReach.of`, once**, so that the timed read stays where it was (§3).
4. **The guard holds seventeen more classes**, with lists the build corrects from the test's first failure (§9), and the labelling, page and recorded-value classes are not among them.
5. **`docs/decision-ledger.md` is not edited.** It says of itself that it is closed to new entries; this record is indexed in `docs/adr/README.md` alone, as ADR-222 to ADR-224 are.
6. **Two tests that rested on a commit to `pipeline` moving a stage change their trigger**, and one is added that holds the aim of #479 by invocation (Tests).

### 12. Which run ids move

A stage's implementation version is the last commit touching a module `StageModules` names for it (ADR-058), and a run's id is a hash of its upstream runs' too (ADR-048).

**This record's build touches five modules**: `extraction` (`DoclingExtractor`, `DocumentPicture`, the module's `package-info`), `embedding` (`FloorReach`), `synthesis` (`ClusterExemplars`, `OpeningChunk`, `OpeningText`, `GenerationProgress`, `Unwritten`, `ListedSurvivor`), `profile` (`Profile`, `NumericValue`'s javadoc) and `pipeline` (`SeedConversions`, `RelevanceFloor`, `RelevanceFloorTasklet`, `RelevanceReportTasklet`, `GenerationTasklet`, `StageModules`, `RelevanceScoreFloorValue`'s javadoc). It touches nothing of `corpus`, `similarity` or `ledger`.

**Read against `StageModules` at `c3deb13`**, where byte-level reduction names `corpus`; extraction `extraction` and `similarity`; content census and content redundancy `similarity` and `extraction`; seed measurement and embedding scoring `embedding`, `extraction` and `pipeline`; arrangement `synthesis`, `extraction` and `embedding`; generation `synthesis`, `extraction`, `embedding` and `pipeline`:

- **Byte-level reduction keeps its run id.** It names `corpus` alone.
- **Extraction, content census and content redundancy are minted again**, by the commit to `extraction`, which each names, and the latter two through their upstream runs.
- **Seed measurement, embedding scoring, arrangement and generation are minted again**, by the commits to `embedding`, `extraction` and `synthesis`, by their upstream runs, and, for three of them, by the change to their module lists. Generation's version names `profile` in the place where it named `pipeline`.

**What that costs, where the build is used on a working directory an earlier build had run.** Stage 2 runs again under a new run and finds every conversion in the cache, writing its shingles again beside the earlier run's, which stay (ADR-221; nothing removes them, [#481](https://github.com/algernon28/vespera/issues/481)); stage 4b builds `shingle_by_hash` again for the new run; stage 5 finds its vectors in the cache, as ADR-222's Consequences say of its own re-mint; 6a mints a new arrangement, which `arrangementApproved` must name; and 6b asks for every synthesis doc again. #485 moves stages 5 to 6b itself (ADR-223 §12), so in the build that carries both, this record adds stages 2 to 4 and nothing after them.

**Afterwards**: a commit to `pipeline` alone mints no run. A commit to `profile` alone mints generation, and generation alone. A commit to `extraction` mints every stage after byte-level reduction, as before.

**What does not move**: no DDL, so no schema version; no cache key; no `run.stage` value, no `finished_step.step` value and no recorded setting.

## Sentences read and left standing

Each of these names a method or type this record moves or removes and stays true when read as follows: ADR-117's and ADR-118's sentences and table row on `RelevanceFloor`, `RelevanceFloor.stateFor` and `RelevanceFloor.CalibratedElsewhere`, read `RelevanceFloor.reachFor` and `FloorReach` for them, since the floor's number is still the operator's number on the run and what it is let do is still not part of the run's identity; ADR-120's row for `RelevanceFloor.stateFor`, the same, a number that cannot be read still removing nothing; ADR-193's and ADR-204's rows for the read of the recorded answers *"through `RelevanceFloor`"*, which it still is; ADR-192's row for 6b's members, read `ClusterExemplars.gathered` for `GenerationTasklet.exemplarsOf`, the counter `cluster documents opened` still ticked by `GenerationTasklet`. The sentences of ADR-149, ADR-151, ADR-155, ADR-183, ADR-206 and ADR-207 that name `SeedConversions`, `openingChunkOf` or `survivorsFor` describe the code of their dates, and those that no longer hold were amended before this record by ADR-206 and ADR-223. ADR-216's rows on `RelevanceFloor.State.removesAnything()` record that change's edits and stay true as its history; its note on `MethodsNothingShippedCallsAreGoneTest` is corrected in place for the test this record changes.

## Consequences

- **No stage's version names `pipeline`.** A change to a progress line, a command-line option or any other file of `pipeline` alone mints no run, and costs no call.
- **A change to `profile` alone mints generation**, whose calls are not cached. It was about one commit in six weeks, and it is now the only way a commit outside the capability modules reaches generation.
- **Run ids move once, for extraction and every stage after it** (§12). The arrangement is a new one, and generation's calls are made again.
- **Persisted names, cache keys and operator text are unchanged.** No `run.stage`, no recorded setting, no cache key, no verdict reason, no line of the log and no byte of the deliverable differs.
- **What a commit to `pipeline` can still change in stage 5 and generation is held by no run id** (§10), and a rule added there is seen by a reader of the change, by the lists of the fourth test, or not at all.
- **ADR-222's guard runs as written, over an empty allowance.** A rule found in `pipeline` from now on is recorded and its stage names `pipeline` again in the same change, or it moves.
- **No test is weakened.** Each test edited keeps every claim it made or makes it of the type that now holds the rule; Tests says how.

## Tests

Written with this record, before `src/main`. Until the build, the test tree does not compile: the new tests and three edited ones, `RelevanceFloorTest`, `UnreadableFloorsTest` and `DoclingExtractorTest`, name `FloorReach`, `RelevanceFloor.reachFor`, `DoclingExtractor.convertSeed`, `ClusterExemplars.gathered`, `OpeningChunk`, `OpeningText`, `GenerationProgress.nothingChunkedFrom` and `occurrenceOpened`, `Unwritten.of(Optional, Supplier)`, `DocumentPicture.listedFor`, `Profile.keysAsWritten` and `ListedSurvivor.of`.

| Class | What it holds |
| --- | --- |
| `embedding.FloorReachTest` (new) | the reason word for word; that with no single identity nothing is removed or withdrawn and no answer is read; that with no number the removals are withdrawn, none is made and no answer is read; that a floor nobody answered for, or answered for under this identity alone, removes below its number, the answers read once by the time the reach is answered; that a floor answered for under another identity, or under two, removes nothing and names them in the order read |
| `synthesis.ExemplarsAreGatheredInSynthesisTest` (new) | that a cluster's exemplars are its members with an opening chunk, each with its score, in member order; that for each member the score, the opening chunk, the line for a member nothing was chunked from and the count of members opened come in that order; that a member with no score stops with the same message before its opening chunk is asked for; that the two new lines of `GenerationProgress` are optional |
| `synthesis.WhyAClusterIsUnwrittenTest` (new) | that what this invocation found stands and the fault on record is then not read; that a fault on record of each kind gives that kind's sentence; that with neither the cluster was not reached |
| `synthesis.ListedSurvivorTest` (new) | that `ListedSurvivor.of` lists the score on record, and 0.0 where none is, with every other value as handed |
| `extraction.PicturesListedForADetectedFormatTest` (new) | that `IMAGE`, `BMP` and `VIDEO` list no picture and every other detected format lists its pictures |
| `profile.ProfileKeysAsWrittenTest` (new) | that `keysAsWritten` holds every key in the record's order, compared with `ProfileKeys`, each value as written and one with none as empty; that it cannot be added to; that saving the profile writes no key for it |
| `extraction.DoclingExtractorTest` | two tests added: that a seed the floor stopped is sent to the keyed conversion as `UNRECOGNISED` with the rest of the call as handed, and that a seed of every other format is sent as detected |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | the allowance empty, the four classes that held rules on record as holding none, the seventeen classes of §9 held closer, and no stage naming `pipeline`. Its texts that said *"the three"* and *"the eight"* are reworded, and the table read is now claimed to hold every stage from content census to generation, so an empty answer is not an empty table |
| `pipeline.RunIdentityGoldenTest` | seed measurement and embedding scoring expect `embedding+extraction`, and generation `synthesis+extraction+embedding+profile`. Its other five tests are not edited |
| `pipeline.UpstreamRunOverAReusedWalkTest` | the row for a commit to `pipeline` becomes a commit to `embedding`, which names the same two stages, so its claims stand over a module that still moves them; a test added, that an invocation after a commit to `pipeline` alone mints no run of any stage it reaches, through the arrangement |
| `pipeline.RelevanceFloorInvocationTest` | the reason on the removal compared with the sentence written out, and not with the constant, which moves with the text |
| `pipeline.RelevanceFloorTest` | each of its six claims made of `RelevanceFloor.reachFor`'s answer instead of the outcome type, the number and the identity answered under still named; a seventh, that with no single identity the floor neither removes nor withdraws |
| `pipeline.UnreadableFloorsTest` | the floor's step with a value no number can be read from removes nothing, read off `reachFor` |
| `MethodsNothingShippedCallsAreGoneTest` | that `FloorReach` declares the two actions and no `removesAnything`, and that `RelevanceFloor$State` is gone |
| `ModuleBoundariesTest` | its javadoc on the one declared exception names the purpose this record widens; no claim changes |

Of the claims that compile against the tree as it stands, these fail until the build: `PipelineHoldsOnlyTheRulesOnRecordTest`'s third and fourth tests (its first two pass before the build as after it: they cannot tell a rule from wiring, which is why the record is what moves the four classes), the three edited tests of `RunIdentityGoldenTest`, the added test of `UpstreamRunOverAReusedWalkTest`, and `MethodsNothingShippedCallsAreGoneTest`'s test of the floor's outcome.

**What no test holds**: that the warning for a member nothing was chunked from is still written under `GenerationTasklet`'s logger, which no test held before; the two-identity join of the operator's lines; that a commit to `profile` alone mints generation, beyond the module list `RunIdentityGoldenTest` pins.

## What the commit that builds `src/main` owes

- §2 to §8, with the signatures named there, and §9's edit to `StageModules`, in one commit with `Profile.keysAsWritten`.
- `RelevanceFloorTasklet.REASON` gone, `FloorReach.REASON` in its place; `RelevanceFloor.State` and its three records gone; `SeedConversions.formatFor`, `GenerationTasklet.exemplarsOf`, `whyUnwritten` and `textOf` gone (ADR-216).
- The four lines of `RelevanceFloorTasklet`, the notice of `RelevanceReportTasklet`, the warning for a member nothing was chunked from and the stop for a member with no score, word for word.
- Javadoc that says otherwise, where it stands: `StageModules`' paragraph on which stages list `pipeline`; `extraction`'s `package-info` on what the dependency on `corpus` is for; `SeedConversions`' and `RelevanceFloor`'s accounts of what they decide; `RelevanceFloorTasklet`'s *"the step runs in all three of `RelevanceFloor`'s states"*; `RelevanceScoreFloorValue`'s reference to `RelevanceFloor.State`; `GenerationTasklet`'s javadoc on `survivorPictures`, `writeDeliverable` and `survivorsOf` where it states a rule that has moved; `profile/NumericValue`'s reference to `RelevanceFloor`.
- **Edit no test but one**: the lists of §9's seventeen classes in `PipelineHoldsOnlyTheRulesOnRecordTest`, corrected from its first failure as §9 says. Any other test the build finds it has to edit is a finding for the analyst.
- Verify with `./mvnw verify` under Java 26, and the docs gates.

## What this does not decide

- **#486**, standing removals that survive a pull adding a second embedder identity.
- **Whether `BrokenCheck` should move to `extraction`**, which widens its dependency on `corpus`.
- **Stage 2's three things that can change its outcome** (ADR-189 §5), under a version that never named `pipeline`.
- **Whether the two homes of a missing score's 0.0 should be one**, `LeadDocument`'s and `ListedSurvivor.of`'s.
- **When the build ships**, beyond after #485. An invocation made between #485's build and this one's pays stages 5 to 6b a second time, a second arrangement to approve and every synthesis doc asked for again; nothing in the repository prevents it.
