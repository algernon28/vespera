# ADR-216 — Nothing ships that no decision requires and nothing calls, a javadoc states its own contract, and AGENTS.md carries no history

- **Date**: 2026-10-09
- **Status**: accepted. The analyst first wrote the decisions down as twelve questions, each with a recommendation. On 2026-10-09 the operator answered that no `vespera run` had yet been made with a build carrying [#456](https://github.com/algernon28/vespera/issues/456) and [#461](https://github.com/algernon28/vespera/pull/461), and accepted every other recommendation as written (§12).
- **Amends**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md)'s list of removals. It gains the actuator starter and its test starter, the batch test starter, Lombok, and the compiler configuration that only named annotation processors (§2). It also records the removal of the OpenAI starter, which [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) §3 made without putting a block on ADR-046. *"The pom carries what a recorded decision requires"* stands, and this record applies it.
- **Amends**: [ADR-029](0029-chunking-structure-first-with-a-measured-llm-fallback.md), in *"LLM fallback only for measured structureless (scanned) cases, currently off"*.
  - The seam that was off is removed, and so is the windowed fallback that ran in its place, which was only ever handed empty text. A structureless document yields no chunks.
  - Any fallback, measured or not, is a record of its own.
  - Decided here. The code leaves with the next change that re-mints `extraction` (§5).
  - *"Default Docling `HybridChunker`"* and *"Boundaries cached, keyed by content hash + chunker identity"* stand.
- **Read with**: [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §1. `Walks.walkFinished` is one of the nine walk methods its table puts on `Walks`, and it is removed (§3). The other eight stand. Its count of 28 is what the ledger did before it was divided, and it stays true as history.
- **Read with**:
  - [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md): the OpenAI starter is gone already. Neither ADR-034 nor ADR-072 requires anything in the pom.
  - [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md):154: it says `spring-batch-test` *"is not in the pom"*, but the batch test starter brought it until this record. Removing the starter makes the sentence true again. The same holds for `InvocationRecordFixture`'s javadoc.
  - [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md):9: it names the actuator starter as one way logging reached the classpath. Logging still arrives through the starters that remain (§2).
- **Rests on**:
  - [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and `StageModules`, for the run ids (§8).
  - [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md) and [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md), for the tests.
  - The plan in `handouts/architecture-simplification.md` §2 D6 and D7, §5 "Wave 6", §6, and §8 items 5 and 6.
  - A reading of the tree at `3fc6f5d`. `mvn dependency:list` over the pom as it is, and over a copy of the tree with §2's removals and nothing else. The unit suite run over that copy.
  - No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Settles**: the rest of [#352](https://github.com/algernon28/vespera/issues/352), Wave 6 of the architecture simplification, after [ADR-214](0214-chroma-is-removed-and-vectors-live-in-sqlite-alone.md) took its Chroma point. Two of its items are decided here and carried out later (§5). #352's criterion *"None of the dead code above remains"* is met when those two land, not when this record's implementation does.

## Context

**What #352 asked, besides Chroma.**
- Delete the dead or test-only code it named.
- Take out the pom entries no decision requires.
- Remove duplicated helpers, but only inside modules another wave was already re-minting.
- Record a javadoc policy, and correct the javadocs that disagree with their code.
- Move `AGENTS.md`'s history out.
- Fix three agent definitions that name a test that does not exist.

Its line numbers were taken at `0b4e009`. Since then main has moved through ADR-209 to ADR-214, and ADR-209 reshaped `Ledger`.

**What the tree at `3fc6f5d` holds**, read for this record:

- **Dead code.**
  - `extraction/LlmStructurelessChunkingFallback` throws on use. Its property is set nowhere. Setting it would register a second `StructurelessChunkingFallback` bean and stop start-up.
  - `HybridChunker.java:67` calls the fallback in one place, as `structurelessFallback.chunk("", rule)`. The text it hands over is always empty, so `WindowedStructurelessChunkingFallback`, the fallback in effect, only ever returns no chunks. Its windowing loop runs in its own test and nowhere else.
  - `Walks.walkFinished` (`ledger/Walks.java:108`, moved there by ADR-209) is called by tests alone.
  - So are `ContentIdentity.representativeFor` (`corpus/ContentIdentity.java:70`), the only read of `superseded_by`, and `RelevanceFloor.State.removesAnything` (`pipeline/RelevanceFloor.java:54`).
  - `DocumentClusters.partitionsFor` (`embedding/DocumentClusters.java:78`) is called by nothing at all, tests included.
- **The pom.**
  - Four entries are used by nothing in `src`, and no record names them: `spring-boot-starter-actuator` (`pom.xml:86`), `lombok` (`:133`), `spring-boot-starter-actuator-test` (`:138`) and `spring-boot-starter-batch-test` (`:143`).
  - The `maven-compiler-plugin` block (`:263-302`) configures nothing but annotation-processor paths: Lombok's twice, and Spring Boot's configuration processor once. The tree has no Lombok annotation and no `@ConfigurationProperties` class.
  - `spring-ai-starter-model-openai`, which #352 asked to check against ADR-034 and ADR-072, was removed by ADR-195.
- **Duplicated helpers.**
  - SQL `LIKE` escaping is written twice and the embedder identity's `"model="` prefix three times, all in `embedding`: `VectorCache:83,87` and `RelevanceDistribution:214,219`, besides `EmbedderIdentity:73`.
  - Docling JSON `$ref` resolution is written twice in `extraction`: `DoclingDocumentTexts:96` and `DocumentPicture:130`.
  - `WalkRecorder`'s `Pending*` records (`corpus/WalkRecorder.java:332,334`) duplicate `RecordedOccurrence` and `RecordedAnomaly`.
  - SHA-256 to hex is written once in each of `corpus`, `extraction`, `ledger` and `synthesis`. No module holds two copies.
- **Javadocs.**
  - Of the plan's four examples, two are already correct. `CensusJobConfiguration` became `VesperaJobConfiguration` under ADR-157, and `GenerationTasklet`'s picture paragraph was rewritten by ADR-206.
  - Two are still wrong: `ExtractionOutcome` and `RedundancyJobConfiguration`.
  - A scan for phrases that date a comment to its ticket found more (§7).
  - Comments are 11,168 of 29,671 non-blank lines in `src/main`, and 306 of its 316 files cite an ADR.
- **`AGENTS.md`.**
  - Line 23 carries the closed-defect history: 29,971 characters, against the 19,664 #352 counted.
  - Line 21 carries the per-module status narrative: 4,049 characters.
  - Together they are about 60% of the file. `docs/check-claims.mjs` matches no sentence on either line.
- **Agent definitions.**
  - `architect.md:24`, `spec-implementer.md:48` and `tester.md:30` name `VesperaApplicationTests`.
  - `debugger.md:57` and `tester.md:32` say `src/test/resources/application.yaml` shadows the main file.
  - The same lines say the one Docker test runs under `./mvnw test`, or that there are two unit-test classes.

**What deleting costs.** ADR-058 versions a module by the last commit that touches its path, and a run's id carries its upstream runs' ids. So any edit under a module, a comment included, re-mints every stage that names that module and every stage downstream of it. #456 and #461 have already moved stages 3 to 6b, and the operator has made no run since. Anything whose re-mint falls inside stages 3 to 6b therefore rides a recomputation that is already owed. Anything in `corpus` or `extraction` would add stages 1 or 2, and re-reading the archive is the most expensive work there is.

## Decision

### 1. A removal rides a re-mint that is already owed

Everything this record removes from `src/main` lands now only if the run ids it moves are already moving. That is stages 3 to 6b, through `pipeline` and `embedding`, plus `ledger`, which no stage's version names.

What would move stage 1 or stage 2 is decided here, written down in §5, and carried by the next change that already re-mints `extraction` or `corpus`. **This is the ride-along rule.** A copy, a dead method, or a javadoc correction in a module goes when a change that re-mints that module for its own reasons is made. It is never the only reason for the re-mint.

**The rule applies in this record itself.** Removing `partitionsFor` touches `embedding`, so the embedding module's two duplicated helpers go in the same change (§4).

### 2. The pom carries none of the entries no decision requires

These leave `pom.xml`:

| Entry | Lines at `3fc6f5d` | Why no decision requires it |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-actuator` | 86-89 | No class names an actuator, health, metrics or micrometer type, and no property under `management.*` is set. ADR-046's *"Modulith observability/actuator"* removal concerned `spring-modulith-actuator`. ADR-093:9 names this starter only as one way logging arrived. |
| `org.projectlombok:lombok` | 133-137 | No Lombok annotation exists, and there is no `lombok.config`. |
| `org.springframework.boot:spring-boot-starter-actuator-test` | 138-142 | Nothing under `src/test` uses what it brings. |
| `org.springframework.boot:spring-boot-starter-batch-test` | 143-147 | Nothing imports `org.springframework.batch.test`. ADR-154:154 records that adding `spring-batch-test` *"would want a decision"*, and none was made. |
| The `maven-compiler-plugin` block | 263-302 | Its two executions only named annotation processors. One was Lombok's. The other was `spring-boot-configuration-processor`, which has no `@ConfigurationProperties` class to describe. Without the block, the plugin compiles with the parent's defaults. |

**Measured** (§11):
- `mvn dependency:list` over a copy of the tree without these entries loses 17 artifacts: the four entries, the actuator's modules, the micrometer metrics modules and their test modules, `spring-batch-test` and Lombok.
- No remaining artifact changes version. `spring-boot-starter-test`, Logback, `micrometer-observation` and `micrometer-core` stay, through `spring-boot-starter-batch`, `-jdbc`, `-jdbc-test` and Spring AI.
- That copy compiled, and its unit suite ran 1512 tests with no failure and no error. 13 were skipped, which is what this environment's file-system assumptions skip.

**Auto-configuration afterwards.**
- No `ObservationRegistry` or `MeterRegistry` bean is built, so Spring AI's and Spring Batch's observations fall back to their no-op registry. Nothing in the tree reads either.
- No property needs removing: none is set under `management.*`, and the project excludes no auto-configuration.
- The `spring-boot-configuration-processor` path had nothing to process, so no generated metadata is lost.

`docs/architecture.md`'s list *"Explicitly removed from the pom"* gains these entries, with this record, and the OpenAI starter, with ADR-195 (§13, step C).

### 3. The dead code that goes now

| Method | Module | Who called it | What a test asks instead |
| --- | --- | --- | --- |
| `Walks.walkFinished(WalkId)` | `ledger` | Tests only: `WalkRecorderTest`, `CensusInvocationTest` | `Walks.finishedWalkFor(Walk.canonicalRoot(root))`, which is what every stage asks |
| `RelevanceFloor.State.removesAnything()` | `pipeline` | Tests only: `RelevanceFloorTest`, `UnreadableFloorsTest` | Whether the outcome is `RelevanceFloor.Applicable`, the type `RelevanceFloorTasklet:143` switches on |
| `DocumentClusters.partitionsFor(RunId)` | `embedding` | Nothing | — |

Three test-only methods stay, because each is a seam a test needs, not code left over:
- `HybridChunker.chunkCount`
- `EmbedderIdentity.withInstruction`, the identity's way to carry ADR-084's instruction part
- `MinHashSignature.deserialize`, the other half of a round trip a test holds

### 4. The embedding module writes its model-name pattern once

**The rule.** `EmbedderIdentity` gains one package-private method, `static String likePatternFor(String modelName)`. It returns `"model=" + escaped + ";%"`. `escaped` is `modelName` with `\` replaced by `\\`, then `%` by `\%`, then `_` by `\_`, in that order, which is today's escaping exactly.
- `VectorCache.vectorsFor` and `RelevanceDistribution.embedderIdentityFor` pass `EmbedderIdentity.likePatternFor(modelName)` where they passed `"model=" + escapeLikePattern(modelName) + ";%"`.
- Each loses its private `escapeLikePattern`.
- Both statements keep their `ESCAPE '\'`.

**What does not move.** `EmbedderIdentity.value()` is not changed by one byte, nor is any stored identity. The pattern each reader binds is the same string it bound before. The test `AModelNameMatchesOnlyItselfTest`, written with this record, passes before the change and after it.

### 5. Decided now, carried out later

These are decided by this record and carried by the next change that re-mints their module, in that change's own commit (§13, steps D1 and D2).

**D1, `extraction`: the next change that re-mints it.** [#458](https://github.com/algernon28/vespera/issues/458) is the likely one, since stage 2's resume set is `ExtractionMetrics.occurrencesForRun`.
- **The structureless fallback goes, both halves** (ADR-029 as amended).
  - Delete `LlmStructurelessChunkingFallback`, `StructurelessChunkingFallback` and `WindowedStructurelessChunkingFallback`.
  - `HybridChunker`'s constructor becomes `HybridChunker(ChunkCache cache)`, and its field `structurelessFallback` goes.
  - `HybridChunker.java:66-67` becomes `texts.isEmpty() ? List.of() : chunkStructured(texts, rule)`.
  - `CHUNKER_IDENTITY` stays `docling-hybrid-chunker-v2`. A structureless document has always yielded no chunks, so no cached chunk changes.
- **One Docling reference resolver.**
  - `DoclingDocumentTexts.resolve` (`:96-102`) and `DocumentPicture.resolve` (`:130-136`) become one package-private static method in `extraction`, with the same rule: `#/<list>/<n>` resolves to entry `n` of `content.<list>`, and anything else to a missing node.
  - The child walks that call it stay where they are, since each collects something different.
- **The extraction javadoc corrections** §7 found: `DoclingExtractor.java:20-22`.

**D2, `corpus`: the next change that re-mints it.**
- `ContentIdentity.representativeFor` is deleted. The `superseded_by` table, its writes and its discard stay: it is the re-analyzable record ADR-067 and ADR-069 keep, and a person may query it.

**Not done, and why.**
- **One SHA-256 helper.** Plan §6 rules out one helper across modules, and no module holds two copies.
- **`WalkRecorder`'s `Pending*` records.** The gain is two lines, and `corpus` is the most expensive module to re-mint. They stay. A later change to `WalkRecorder` may drop them under §1's rule.

### 6. The `run` command says what it does

Today `VesperaCommand.Run` is described as *"Walks a corpus and records what it holds."* That is the census alone, true when it was written and not since ADR-101. It is what `vespera run --help` and bare `vespera` print.

It becomes **"Walks a corpus and takes it as far as the next missing value."** This is the README's own sentence for the command, in a description's form. This is operator-visible text changing on purpose. The plan's §4 rule, that operator-visible text does not change, yields to it here because the old text is false. `TheRunCommandSaysWhatItDoesTest` holds the description and the README's line together.

### 7. A javadoc states its own contract, and cites its ADR

**The policy, recorded once.**
- A class's or a method's javadoc states its own contract: what it takes, what it returns or writes, what it refuses, and whatever a caller cannot see from the signature.
- It cites the ADR it implements by id.
- It does not restate that ADR's context, the alternatives it refused, or its reasoning. A reader follows the citation.
- It does not date itself to the ticket or slice it was written in: *"this ticket"*, *"this slice"*, *"today"*, *"not yet"*, *"once stage N exists"*. Those are true for a week and wrong after.
- Where a javadoc and its code disagree, the code is the fact.

**When a correction is made.** A correction is made in the change that notices it, if that change already re-mints the module. Otherwise it waits under §1's rule. A javadoc in `ledger` or `profile` costs no run id and may be corrected at any time. No sweep is made: a sweep would read 11,168 comment lines and re-mint every module.

**The mismatches corrected now, all in `pipeline`:**

| Where at `3fc6f5d` | What it says | What is so |
| --- | --- | --- |
| `ExtractionOutcome.java:6-16` | *"always `EXTRACTION_FAILED` in this ticket's slice, since neither `degenerate-output` nor `passed` is decided here"* | `ExtractionItemProcessor:373` writes `DEGENERATE_OUTPUT` through it. |
| `RedundancyJobConfiguration.java:43-45` | *"stage 4 is the last step today … once stage 5 actually exists"* | The job runs fifteen steps, one after another (`VesperaJobConfiguration:68-82`). |
| `GenerationTasklet.java:490-491` | *"the eight profile keys the run consumed"* | `profileValues` writes every component of `Profile`, which has ten. |
| `VesperaJobConfiguration.java:29-30` | ADR-036 *"is carried by the absence of the JDBC starter"* | `spring-boot-starter-jdbc` is in the pom. The starter whose absence carries it is `spring-boot-starter-batch-jdbc`, which ADR-046 removed. |
| `VesperaCommand.java:106` | *"everything this slice does"* | §6. |

**Corrected later:** `DoclingExtractor.java:20-22`, with D1.

**Suspected and not confirmed**, so left alone: `ConfidenceDistribution.java:17`, `Profile.java:35` and `SchemaVersionMismatchException.java:13`.

### 8. The run ids

**What moves now**, read from `StageModules` at `3fc6f5d`:

| Change | Module | Runs it re-mints |
| --- | --- | --- |
| §2, the pom | none: no module's path | none |
| `walkFinished` (§3) | `ledger`, in no `StageModules` row | none |
| `removesAnything` (§3); §6; §7's corrections | `pipeline` | content census (3), content redundancy (4), seed measurement and embedding scoring (5), arrangement (6a), generation (6b) |
| `partitionsFor` (§3); §4 | `embedding` | seed measurement, embedding scoring, arrangement, generation: inside the row above |
| Tests, Markdown, `check-claims.mjs`, this record | none | none |

**In all, stages 3 to 6b move, the same six runs #456 and #461 already move.** No persisted key changes:
- `CHUNKER_IDENTITY`, every embedder identity and every hash are byte-identical.
- No schema version moves.
- The pom changes no remaining library's version.

**The cost is paid once only if this lands before the operator's next `vespera run`.** The operator answered on 2026-10-09 that no run has been made with a build carrying #456 and #461 (§12). If one is made first, this record's implementation costs one more pass of stages 3 to 6b:
- a new arrangement, and its approval;
- every cluster's generation call again;
- a second deliverable tree.

What that pass recomputes, once, for all three changes together:
- Stages 3 and 4 work from the database alone.
- Stage 5 re-scores and re-clusters from the vectors it already holds, with no call to embed.
- 6a names a new arrangement, which the operator approves.
- 6b calls the model once per cluster and writes a new tree.
- The labels survive, being keyed by path and seed set (ADR-097).

**What moves later.**
- D1 moves stages 2 to 6b, but only as part of a change that already moves them.
- D2 moves stages 1 to 6b on the same terms.

### 9. `AGENTS.md` carries no history

`AGENTS.md` says what an agent needs before it works. A defect that is closed is not that.

- **Line 23 moves out.** Its text from *"Thirty-five were, and are closed."* to the end of the line moves, verbatim, to a new file, `docs/closed-defects.md`.
  - The new file is closed to edits, as `docs/decision-ledger.md` is. Its header says what it is and that nothing is added to it.
  - It is kept and not discarded because five of the thirty-five closures were closed with no ADR (#321, #319, #311, #310, #306). The line also carries reasoning that spans several records.
- **What stays in `AGENTS.md` is the open-defect sentence**, rewritten to say:
  - which defects are open: those ADR-210 leaves open by the operator's decision, two wrong readings and three limits, each stated there;
  - that an open defect is an open issue on the tracker;
  - that a closed one is recorded by its ADR and its issue;
  - that `docs/closed-defects.md` keeps what this file once said of the first thirty-five.
- **Line 21 becomes a few sentences of orientation.**
  - The cascade is built end to end.
  - `vespera run` walks a corpus and takes it as far as the next missing value, through one Spring Batch job of fifteen steps.
  - `vespera label` records the answers written into the label file.
  - What each stage judges and writes is `docs/architecture.md` §1. The invocations an operator makes are the README's.
- **The practice of appending a closure to `AGENTS.md` ends.** Eight records carried it forward: ADR-134, ADR-155, ADR-187, ADR-191, ADR-193, ADR-199, ADR-210 and ADR-211. A record that closes a defect says so in its own text and on its issue. `AGENTS.md` changes only when the set of open defects does.
- **What `docs/check-claims.mjs` reads is untouched.** Its sentences are on lines 15, 25, 35, 37, 38, 44 and 57, none on line 21 or 23. Its `UNCHECKED` list (`:578`) names line 21's opening and is reworded to name what line 21 now says.

### 10. The agent definitions

`.claude/agents/` configures agents. ADR-214 §3 edited it only with the operator's approval, and [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) ([#459](https://github.com/algernon28/vespera/issues/459)) closes every `.claude` folder to agents' writes. The operator approved the replacement text below on 2026-10-09, and the operator installs it. Each replacement points at `AGENTS.md`'s own bullet rather than restating it, so that the two cannot drift apart again.

| File and line at `3fc6f5d` | Replace | With |
| --- | --- | --- |
| `architect.md:24` | the sentence *"`VesperaApplicationTests` needs a Docker daemon; if it did not run, say so and do not merge on the strength of the rest."* | `The integration tests (\`*IT\`) run only under \`./mvnw verify\`, and six of them need a Docker daemon (\`AGENTS.md\`, "Building and testing", names them); if they did not run, say so and do not merge on the strength of the rest.` |
| `spec-implementer.md:48` | the whole bullet | `- The integration tests (\`*IT\`) run only under \`./mvnw verify\`, never under \`./mvnw test\`; six of them start their sidecar through Testcontainers, need a Docker daemon and take minutes on a cold run. \`AGENTS.md\`'s "Building and testing" names which. Every other test class needs neither.` |
| `tester.md:30` | the whole bullet | `- **Six integration tests need a Docker daemon.** The \`*IT\` classes run only under \`./mvnw verify\`, and six of them start their sidecar through Testcontainers, taking minutes cold; \`AGENTS.md\`'s "Building and testing" names them. Check with \`docker info\` before blaming the code, and say plainly in your report if they could not run — a suite that did not run is not a suite that passed.` |
| `tester.md:32` | the whole bullet | `- **Test configuration is \`src/test/resources/application-test.yaml\`, under the \`test\` profile**, so it layers over \`src/main/resources/application.yaml\`: a property the test file does not set keeps its main value under test. A test file named \`application.yaml\` would shadow the main file entirely, which is why there is none.` |
| `debugger.md:57` | the sentence *"And `src/test/resources/application.yaml` shadows the main file entirely — same classpath name — so a property set only in the main file does not apply under test."* | `And test configuration is \`src/test/resources/application-test.yaml\` under the \`test\` profile, which layers over the main \`application.yaml\` rather than replacing it, so a property the test file does not set keeps its main value under test.` |

### 11. The measured size

The plan estimated *"−500 plus comments"*. Measured as the line ranges each change removes at `3fc6f5d`:

| Part | Lines |
| --- | --- |
| Pom | −59: dependency blocks of 4, 5, 5 and 5 lines, and the 40-line compiler block |
| `src/main` now | −31: `walkFinished` −7, `removesAnything` −6, `partitionsFor` −8, the two `escapeLikePattern` −5 each. `likePatternFor` adds about +10, so about −21 net. §7's corrections replace text, at about ±0. |
| `src/main` later | D1 about −90: three fallback files of 24, 23 and 40 lines, the constructor and field, and one `resolve` of the two. D2 −12. |
| Tests | Two test classes leave with D1 (−96). The test-side edits of step A change about 20 lines. The tests written with this record add about 450. |
| `AGENTS.md` | About 30,000 characters move to `docs/closed-defects.md`, and about 3,500 of line 21's go. Together about 60% of the file. |

**About −80 lines of code and build now (−59 in the pom, about −21 in `src/main`), and about −200 more when D1 and D2 land.** The rest of the plan's −500 had already been taken by the time this was read:
- Chroma, by ADR-214 (its pull request was +759/−281);
- the `STEP` alias, by Wave 1;
- the test-only `ExtractionItemProcessor` constructor, by Wave 2;
- the citation pattern, the filename stem and the cluster key, by ADR-213.

The implementation's pull request states `git diff --stat` beside these figures.

### 12. Put to the operator, and answered on 2026-10-09

1. **Has a `vespera run` been made with a build carrying #456 and #461?** Answered: no. So everything inside stages 3 to 6b lands now (§1, §8).
2. **Which run ids this pays for.** Answered as recommended: only what stays inside stages 3 to 6b. The `extraction` and `corpus` items are decided here and carried later (§5).
3. **The helpers.** Answered as recommended: a ride-along rule rather than separate work (§1). No single SHA-256 helper, and the `Pending*` records stay (§5). This record applies the rule to `embedding` (§4).
4. **The structureless fallback.** Answered as recommended: both halves go, not only the LLM seam. A structureless document yields no chunks (§5, ADR-029 amended).
5. **The OpenAI starter, ADR-034 and ADR-072.** Answered as recommended: settled by ADR-195 and recorded here (header).
6. **The pom.** Answered as recommended: the four entries and the whole compiler block (§2).
7. **The dead code #352 did not list.** Answered as recommended: `partitionsFor` goes. `chunkCount`, `withInstruction` and `deserialize` stay (§3).
8. **How wide the javadoc work is.** Answered as recommended: the policy, and the confirmed mismatches in modules already moving. No sweep (§7).
9. **The `run` command's description.** Answered as recommended: corrected, as a declared exception to the rule that operator-visible text does not change (§6).
10. **Where the history goes.** Answered as recommended: `docs/closed-defects.md`, closed to edits, and the appending practice ends (§9).
11. **The agent definitions.** Approved. Since ADR-215 no agent session writes there, so the operator installs the text of §10.
12. **How ADR-029 and ADR-046 are amended.** Answered as recommended: a pointer block above each reconstituted header, as ADR-214 did for ADR-001, ADR-032 and ADR-039, with the summary untouched.

### 13. The order of the work, and who does each part

**This record's commit** contains:
- this file, and the blocks on ADR-029, ADR-046 and ADR-209;
- its row and the range line in `docs/adr/README.md`;
- the decision count in `AGENTS.md`;
- `Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS`;
- the five test classes of "What pins it".

It changes nothing under `src/main`, nor the pom or `.claude/`.

**The implementation commit** is one commit made in three steps, so that the build is never left unable to compile:

**A, `analyst`, first.** Test-side edits, each green before step B and after it:

| Test file | What changes |
| --- | --- |
| `corpus/WalkRecorderTest` | Lines 165, 184, 232 and 263: `ledger.walks().walkFinished(X)).isTrue()` becomes `ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))).contains(X)`, for `X` in `resumed` and `walkId`. `ledger.walks().walkFinished(stopped)).isFalse()` becomes `ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))).isEmpty()`. |
| `pipeline/CensusInvocationTest` | Line 97 becomes `ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))).contains(theWalk())`, importing `io.algernon.vespera.corpus.Walk`. |
| `pipeline/RelevanceFloorTest` | Lines 114, 130 and 205: `assertThat(state.removesAnything()).isFalse()` becomes `assertThat(state).isNotInstanceOf(RelevanceFloor.Applicable.class)`. Line 157's `claim("and it says so", … isTrue())` becomes the claim *"and it is the applicable outcome, the only one the floor's step removes anything by"*, over `assertThat(state).isInstanceOf(RelevanceFloor.Applicable.class)`. |
| `pipeline/UnreadableFloorsTest` | Line 135: `….removesAnything()).isFalse()` becomes `assertThat(step.stateFor(AN_EMBEDDER, "Stage 5e (relevance floor)")).isNotInstanceOf(RelevanceFloor.Applicable.class)`. |
| `ledger/LedgerHoldsFourRecordsTest` | `ABOUT_A_WALK` and `EVERYTHING_THE_LEDGER_DID = 28` stay: they record what the ledger did before it was divided. A constant `REMOVED_SINCE_THE_DIVISION = Set.of("walkFinished")` is added, with a javadoc citing this record. The claim on the walks reads *"what is asked about a walk is asked of the walks, but for what has been removed since"*, over `containsAll(without(ABOUT_A_WALK, REMOVED_SINCE_THE_DIVISION))`, with a private helper `without` returning a copy of the first set less the second. The other claims are unchanged. That the method is gone is held by `MethodsNothingShippedCallsAreGoneTest`. |

**B, `spec-implementer`.** Production and build. It does not edit a test, Markdown or `.claude/`.

1. **`pom.xml`.** Remove the four `<dependency>` blocks of §2 (`spring-boot-starter-actuator`, `lombok`, `spring-boot-starter-actuator-test`, `spring-boot-starter-batch-test`) and the whole `<plugin>` block for `maven-compiler-plugin`. Add, change or remove nothing else.
2. **`ledger/Walks.java`.** Delete `walkFinished` and its javadoc.
3. **`pipeline/RelevanceFloor.java`.** Delete `State`'s default method `removesAnything` and its javadoc. `State` becomes `sealed interface State {}` under its existing javadoc.
4. **`embedding/DocumentClusters.java`.** Delete `partitionsFor` and its javadoc, and any import only it used.
5. **`embedding/EmbedderIdentity.java`, `VectorCache.java`, `RelevanceDistribution.java`.** As §4. `likePatternFor`'s javadoc states its contract under §7's policy: the pattern matches every identity `value()` composes for the name, for a statement written with `ESCAPE '\'`; the name's backslash, `%` and `_` are escaped so it matches only literally; it cites ADR-216. `value()` is not touched.
6. **`pipeline/VesperaCommand.java`.**
   - Line 106's javadoc becomes `Walks a corpus and takes it as far as the next missing value: the whole job, each step behind its own gate (ADR-101).`
   - Line 108's `description` becomes `"Walks a corpus and takes it as far as the next missing value."`
7. **`pipeline/ExtractionOutcome.java`.** The javadoc (lines 6-16) becomes: *A verdict `ExtractionItemProcessor` decided to write for one occurrence: `EXTRACTION_FAILED` where the conversion failed or the file could not be read (ADR-070, ADR-210), or `DEGENERATE_OUTPUT` where a converted occurrence fell below the degeneracy floor (ADR-070).* Then a second paragraph, kept from today's: *A survivor is not represented by an instance of this record at all: `ExtractionItemProcessor` returns `null` for it, which Spring Batch reads as "filter this item" rather than "write nothing for it" — an occurrence carries no row until a stage actually judges it.* Write it with `{@link}` and `{@code}` as today's is.
8. **`pipeline/RedundancyJobConfiguration.java`.** In the paragraph *"The gate is checked in both steps"*, the sentence from *"Neither behaves differently"* to *"not before."* becomes: *A closed gate fails neither step: the job goes on to the next step, as it does after every step, and each later step asks its own gates.*
9. **`pipeline/GenerationTasklet.java`.** At line 490-491, *"the eight profile keys the run consumed"* becomes *"every key of the profile, read off its record"*.
10. **`pipeline/VesperaJobConfiguration.java`.** At line 29-30, *"is carried by the absence of the JDBC starter rather than by configuration — adding the starter is what would break it"* becomes *"is carried by the absence of Spring Boot's batch JDBC starter, `spring-boot-starter-batch-jdbc`, rather than by configuration — adding that starter is what would break it"*, with the artifact in `{@code}`.
11. **Run the suite green.** With step A in the tree, every test of "What pins it" passes, and no other test changes outcome.

B cannot come before A. Once `walkFinished` and `removesAnything` are gone, the tests step A rewrites no longer compile, and `spec-implementer` does not edit tests.

**C, `analyst`, last.** Markdown and the claims guard:

| File | What changes |
| --- | --- |
| `AGENTS.md` | Line 21 and line 23 as §9 says. Under "Conventions worth knowing", one bullet: *"A javadoc states its class's own contract and cites the ADR it implements; it does not restate that ADR (ADR-216)."* |
| `docs/closed-defects.md` | New. A header saying what it is, that it is closed to edits, and where a closed defect is recorded instead (§9). Then line 23 of `AGENTS.md` at `3fc6f5d`, from *"Thirty-five were, and are closed."* to its end, verbatim. |
| `docs/check-claims.mjs` | The `UNCHECKED` entry at line 578, *`'"The cascade is built end to end", and the per-stage sentences under it'`*, is reworded to name what line 21 now says. The check-claims run then prints no false description. |
| `docs/architecture.md` and `docs/architecture.html` regenerated | §2's *"Explicitly removed from the pom"* gains `spring-ai-starter-model-openai` (ADR-195), and `spring-boot-starter-actuator`, `spring-boot-starter-actuator-test`, `spring-boot-starter-batch-test`, `lombok` and the compiler block's annotation-processor paths (ADR-216). The Chunking row stays until D1 lands. |
| `.claude/agents/*.md` | Not edited by an agent. The text of §10 is handed to the operator. |

**D1 and D2, later, each in the commit of the change that carries it** (§5). Each step is split the same way: the `analyst` makes the test-side edits first, then `spec-implementer` changes `src/main`.

- **D1, test side:**
  - Delete `LlmStructurelessChunkingFallbackTest` and `WindowedStructurelessChunkingFallbackTest`.
  - `HybridChunkerBeans:24`, `HybridChunkerTest:182` and `TextInPartsTest:733` construct `new HybridChunker(new ChunkCache(jdbcTemplate))`.
  - `HybridChunkerTest.structurelessDocumentProducesNoChunks` keeps its claim, minus the words on a *"disabled-by-default structureless fallback"*.
  - A test pins that the three fallback types are gone, and that one class of `extraction` resolves a Docling reference.
- **D1, documentation:**
  - `docs/architecture.md`'s Chunking row reads *"a structureless document yields no chunks (ADR-029, ADR-216)"* in place of the LLM fallback.
  - ADR-192's list naming `WindowedStructurelessChunkingFallback` describes the tree it was written on, and gets no block.
- **D1, production:** as §5 says, and the `DoclingExtractor` correction of §7.
- **D2, test side:** `ContentIdentityTest:80,83` and `ContentIdentityResolutionTest:98-100` read the representative back from `superseded_by` through a private SQL helper in each test, with the same claims.
- **D2, production:** delete `representativeFor`.

**Recounting the records.** This record was written while ADR-215 was open pull request [#464](https://github.com/algernon28/vespera/pull/464) ([#459](https://github.com/algernon28/vespera/issues/459)). [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) landed first, and the count was redone when main was merged into this record's branch. `docs/adr/` now holds ADR-001 to ADR-216, 216 files. The index lists ADR-215 before ADR-216, and `AGENTS.md` and `docs/adr/README.md` say so.

## Records this touches

Earlier records are not edited in their text. Where this record amends one, or makes a sentence of it describe a tree that has moved, that record carries a block at its top pointing here.

| Record | What it says | Here |
| --- | --- | --- |
| ADR-029 | *"LLM fallback only for measured structureless (scanned) cases, currently off."* | **Amended**: the seam and the windowed fallback go, decided now and carried by D1. Block at its top, above the reconstitution header. |
| ADR-046 | The pom's removals and additions, *"Ollama starter alongside OpenAI"* among them. | **Amended**: §2's removals, and ADR-195's removal of the OpenAI starter, recorded. Block at its top, above the reconstitution header. |
| ADR-209 | §1's table puts `walkFinished` on `Walks`; its count of 28. | **Read with** this record: the method goes, and the count stays true as history. Block at its top. |
| ADR-195 | Removed the OpenAI starter, amending ADR-046 with no block. | **No block.** Applied and recorded here. |
| ADR-154 | Line 154: `spring-batch-test` is not in the pom. | **No block.** It was brought by the batch test starter. Once that starter leaves, the sentence is true. |
| ADR-093 | Line 9: logging arrived through the actuator, batch and JDBC starters. | **No block.** A description of the classpath on 2026-09-04. Logging still arrives through the two that remain. |
| ADR-034, ADR-072 | The reference model, which once explained the OpenAI starter. | **No block.** ADR-195 settled both. |
| ADR-192 | Names `WindowedStructurelessChunkingFallback` among loops over values in memory. | **No block.** A list of the tree as it was read. D1 removes the class. |
| ADR-058 | A module's version is the last commit touching it. | **No block.** Applied (§8). |

## What pins it

Each class is in `src/test/java/io/algernon/vespera/`, written with this record and run against `3fc6f5d`. None names a type or method that does not exist, so the test sources compile, and the red is at run time.

- **`NoDependencyWithoutADecisionTest`**, three tests, all **red** at `3fc6f5d`:
  - `thePomDeclaresNoneOfTheFourEntries`: the pom declares none of the four artifacts. *Red: it declares all four.*
  - `thePomConfiguresNoAnnotationProcessor`: no `<annotationProcessorPaths>`, no `spring-boot-configuration-processor`, and no `maven-compiler-plugin` declaration. *Red: all three are there.*
  - `noClassThoseEntriesBroughtIsOnTheClasspath`: six classes are absent from the test classpath, which contains the runtime one: the actuator's `Endpoint`, `EndpointAutoConfiguration`, `MetricsAutoConfiguration`, `TestObservationRegistry`, `JobOperatorTestUtils` and `lombok.Getter`. *Red: all six are present.*
- **`MethodsNothingShippedCallsAreGoneTest`**, three tests, all **red** at `3fc6f5d`: `Walks` declares no `walkFinished`, `RelevanceFloor.State` no `removesAnything`, and `DocumentClusters` no `partitionsFor`. Read by reflection over each class's declared methods.
- **`TheEmbedderIdentityFormatIsWrittenOnceTest`**, two tests, both **red** at `3fc6f5d`, read off the compiled classes' string texts:
  - Only `EmbedderIdentity` holds a text beginning `model=` in `embedding`. *Red: `RelevanceDistribution` and `VectorCache` do too.*
  - Only `EmbedderIdentity` holds the text `\%`. *Red: `RelevanceDistribution` and `VectorCache` hold it, and `EmbedderIdentity` does not.*
- **`embedding/AModelNameMatchesOnlyItselfTest`**, five tests:
  - `VectorCache.vectorsFor` reads only the named model's vectors when a lookalike differs where the name has an underscore.
  - `RelevanceDistribution.embedderIdentityFor` finds the named model's identity in the same case.
  - A name that is only `%` matches nothing.
  - A name with a backslash (`a\b`, beside a stored `ab`) is matched with the backslash, by both readers.
  - A name is matched whole: `nomic_embed` does not match a stored `nomic_embed-v2`, in either reader.
  
  It holds what §4's move must keep. Nothing held it before.
  
  The first three were written with this record and are green at `3fc6f5d` and after. The last two were added at the architect's review, and are green against the shipped `likePatternFor`. In a scratch copy, dropping the backslash escape turned only the fourth red, and dropping the `;` that ends the name turned only the fifth red.
- **`pipeline/TheRunCommandSaysWhatItDoesTest`**, two tests:
  - `theRunCommandDescribesWhatItDoes`: the `run` command's `@Command` description is the one sentence of §6. *Red: it names the old one.*
  - `theReadmeSaysTheSame`: the README's command line for `run` carries the same words. **Green at `3fc6f5d` and after.**

`AdrLinkTest` holds the new `Adr` constant to this file.

**Not pinned, and why:**
- **The wording of a javadoc.** The policy is followed by the people who write one, and a test of prose would pin the words rather than the contract.
- **That the run ids move only inside stages 3 to 6b.** No test sees a commit.
- **The D1 and D2 items.** They are pinned by the change that carries them.
- **`AGENTS.md`'s shape.** `docs/check-claims.mjs` still holds every claim it checks, and nothing counts history.
- **The agent definitions.** They are not code, and the operator installs them.

## Alternatives refused

- **Remove everything #352 named now, `extraction` and `corpus` included.** That moves stages 1 and 2 too, which means re-reading the archive, for about −100 lines of code that harms nothing while it waits.
- **Keep the windowed fallback and inject it directly.** It only ever receives empty text, so keeping it keeps a loop that never runs in production behind a decision that never asked for it.
- **De-duplicate the helpers as work of their own, or not at all.** The first re-mints modules for a few lines. The second leaves copies that drift whenever their module next changes anyway. The ride-along rule takes them at no cost.
- **Sweep every javadoc.** It would read 11,168 comment lines, and every module touched re-mints its stages.
- **Delete `AGENTS.md`'s history outright, the ADRs and issues holding it.** Five closures have no ADR, and the line carries reasoning across records. Moving it costs one closed file.
- **Keep the actuator for a monitoring surface later.** That is the reasoning ADR-046 rejects. A reader that wants one brings its decision with it.
- **Keep the compiler block with an empty processor list.** It would configure nothing and say that something is configured.

## Consequences

- **Four fewer dependencies and seventeen fewer artifacts** on the classpath. No observation or metrics registry is built, and nothing read one.
- **Six run ids move once** (stages 3 to 6b), with #456's and #461's, provided this lands before the operator's next run.
- **`vespera run --help` says what the command does.**
- **A javadoc has a rule**, and the next change to `extraction` or `corpus` owes the items of §5.
- **`AGENTS.md` loses about 60% of its length**, and no record appends a closure to it again.
- **#352 stays open until D1 and D2 land**, or until the operator moves them to an issue of their own and closes it.
- **`CONTEXT.md` is not edited.** No entry names anything removed here.
- **`docs/decision-ledger.md` is not edited.** It is closed to edits.
