# Architecture simplification: a plan

**Date:** 2026-09-25
**Status:** proposal, revised after an `architect` review (verdict: sound with amendments, all applied here). Nothing here is decided except Wave 0a, which is in review as #295 (ADR-153). Every other wave needs its own ADR before any code moves, and follows the usual route: `analyst` → `spec-implementer` → `tester` → `architect`.
**Vocabulary:** a *wave* here is one increment of this refactor. ADR-140 and stage 2's code already use "wave" for one round of eight conversions (`ExtractionJobConfiguration.java:43-54`, `ConversionDispatch.java:46`). The two never meet in a sentence below; a wave's own ADR should say "refactor wave" wherever they could.
**Scope:** `src/main`, the test base that pins it, and the documentation that describes it. It changes nothing an operator does and removes no capability.

---

## 1. Summary

Vespera's *model* is sound: a verdict ledger rather than a moving pipeline, capability-shaped modules, gates as required inputs, and run identity derived from what a run consumed. This plan keeps all of that.

What has drifted is the *code*. It has grown by copying rather than composing:

- `pipeline`, the composition root, is 88 of the 231 main files. Most of it is the same five pieces of scaffolding written once per stage.
- Rules that decide what the deliverable contains live in `pipeline`, not in the module that owns the capability.
- One fact is never recorded: the key stage 2 used for a file occurrence's extraction cache. Six later classes read the archive again to recompute it. #287 and #289 are two symptoms.
- The test base names every pipeline class 24 times over, and production code has twice been bent to keep those lists from growing (ADR-131, ADR-132).

Verifying this plan also turned up **a defect**, now [#290](https://github.com/algernon28/vespera/issues/290). Over a walk that ADR-115 reused, anything that gives one stage a second run makes the next stage refuse, and nothing the operator can set gets them out of it. A build that changes `corpus`, `extraction`, `similarity` or `pipeline` does it, and so does setting `degenerateOutputConfidenceFloor` after invocation 1, which the README invites (§2, D5). ADR-099 foresaw the case and left its remedy to whichever change made walks reusable; ADR-115 made them reusable without shipping it. A sibling, [#296](https://github.com/algernon28/vespera/issues/296), lets an old arrangement approval keep opening 6b's gate over a superseded arrangement.

The five moves that matter:

1. **One test base** for the whole-job tests, so moving a class no longer means editing 24 files (Wave 0a).
2. **Fix the second-run refusal and the stale approval** before anything else changes a module's implementation version (Wave 0b).
3. **One way to mint a stage's run and one way to shape a step**, replacing seven run classes and ten one-method configuration classes (Wave 1).
4. **Rules go home.** Stage 2's failure classification moves to `extraction`, 6b's generation loop to `synthesis`, and content-identity resolution to `corpus` (Wave 2).
5. **Record stage 2's cache key once**, which removes seven of the eight later re-reads of the archive (Wave 3, folded into #287).

Start with Wave 0a, then 0b. Each later wave is independent enough to land on its own, in the order given.

---

## 2. Where it stands, measured

| | Files | Lines | Of which comment lines |
|---|---:|---:|---:|
| `src/main` | 231 | 21,818 | 7,892 (36%) |
| `pipeline` alone | 88 | 9,568 | 3,112 (33%) |
| `src/test` | 165 | 40,965 | — |

| Module | Files | Lines |
|---|---:|---:|
| `ledger` | 16 | 1,088 |
| `corpus` | 17 | 1,715 |
| `extraction` | 39 | 2,463 |
| `similarity` | 12 | 1,290 |
| `embedding` | 24 | 2,758 |
| `synthesis` | 27 | 2,460 |
| `profile` | 7 | 455 |
| `pipeline` | 88 | 9,568 |

The job is one Spring Batch job of fifteen steps, chained linearly in `pipeline/CensusJobConfiguration.java:31-66`. It has no flows or deciders; every gate is checked inside its own step.

### D1 — Scaffolding in `pipeline` is copied, not composed

| Pattern | Copies | Evidence |
|---|---|---|
| A class that mints one stage's run | 7 classes, 845 lines, plus stage 1's run minted inline | `ExtractionRun`, `ContentCensusRun`, `RedundancyRun`, `SeedMeasurementRun`, `ScoringRun`, `ArrangementRun`, `GenerationRun`; inline at `ByteLevelReductionTasklet.java:104-109` |
| A configuration class holding one step bean | 10 classes of 21–28 lines each, differing only in names | e.g. `ClusteringJobConfiguration.java`, `RelevanceFloorJobConfiguration.java` |
| The skip-if-done shell: "gate open? → already finished? → discard → work → record finished" | about 9 tasklets; 12 `stepFinished` and 12 `finishStep` calls | e.g. `ClusteringTasklet`, `EmbeddingScoringTasklet`, `ArrangementTasklet` |
| Resolving an occurrence to its path | 9 private helpers, 12 throws of "no facts recorded" | e.g. `ArrangementTasklet`, `ClusteringTasklet`, `RelevanceReportTasklet` |
| Writing a report page | 6 copied write helpers; 7 file-name constants in 7 classes; a two-decimal formatter copied 3 times | the six `*Report` classes |

**What a stage's run class actually adds** is about fifteen lines: find the finished walk, find the upstream run, and call `ledger.startRun` with a module list and a JSON record of what it consumed. The rest is javadoc, a copied `JsonMapper`, `*_MODULE` constants and, in four of the seven, a constructor that re-checks the gate its caller already checked.

The upstream run is found five different ways:
- a ledger lookup through `UpstreamRuns`;
- injecting the previous stage's run bean;
- matching an approval prefix (`ArrangementGate`);
- `ledger.upstreamRuns` with a hand-rolled "exactly one" check;
- recomputing the id with `RunId.of`, at `GenerationTasklet.java:381-385`.

Four of these are deliberate. The last is exactly what ADR-099 replaced, and its javadoc still says "the way `ExtractionRun` already re-derives it", which stopped being true then.

### D2 — Rules that shape output live in the composition root

`pipeline` is meant to be wiring. These are rules:

| Rule | Where it is | Where it belongs |
|---|---|---|
| ADR-070's failure classification and the ADR-071 timeout streak | `ExtractionItemProcessor.java:267-376` | `extraction` |
| The extractor identity string (the persisted extraction-cache key) | `ExtractionJobConfiguration.java:287-297` | `extraction` |
| 6b's per-cluster loop, the five-in-a-row breaker and the completion rule | `GenerationTasklet.java:212-339` | `synthesis` |
| The lead-document and cluster-label rule (ADR-106) | `ArrangementTasklet.java:235-264` | `synthesis` |
| Content-identity resolution: size, then hash (ADR-067, ADR-069) | `ByteLevelReductionTasklet.java:218-278` | `corpus` |
| The eight profile keys, written out by hand | `GenerationTasklet.java:460-471` | derived from `Profile` |

This matters for more than tidiness, in two opposite ways.

- **Stages 3 to 6b over-invalidate.** A rule in `pipeline` versions under `pipeline`'s implementation version, which those six kinds of run include in their identity, so any edit to the wiring re-mints them.
- **Stages 1 and 2 under-invalidate.** Neither names `pipeline`: stage 1 names `corpus` alone, stage 2 `extraction` and `similarity` (ADR-073). Yet ADR-058:21 says a stage's version includes its orchestration class in `pipeline`. So the ADR-070 classification, the timeout streak and stage 1's content-identity resolution version under nothing today, and a change to them re-mints no run.

Wave 2 moves those rules into the modules their stages already name, which versions them for the first time. Once `pipeline` holds no rule that shapes output, it can drop out of stages 3 to 6b's run identity (Wave 7).

### D3 — One fact is never recorded, so it is recomputed

Stage 2 keys the extraction cache by the SHA-256 of a file occurrence's bytes (`extraction/DoclingExtractor.java:65`, `contentHashFor`). No table records which key belongs to which occurrence: `corpus`'s `content_hash` holds only occurrences stage 1 hashed for a size collision. So every later consumer reads the file again to recompute the key:

- `EmbeddingScoringTasklet.java:153`
- `RelevanceScoringTasklet.java:148` (and `:177` on the seed side)
- `ClusteringTasklet.java:207`
- `RelevanceReportTasklet.java:287`
- `ArrangementTasklet.java:251`
- `GenerationTasklet.java:434, 664`

That is eight call sites in six classes (seven on the corpus side; `:177` hashes seed occurrences, which stage 2 never sees), each with its own answer to "what if the file cannot be opened any more". #289 is the case where the answer is "fail the invocation". #287 is the manifest's `content_hash` column coming out blank for the same underlying reason. It also means stages 5 and 6 read every surviving byte of the archive again, which on the 42,851-file folder this tool exists for is not free.

### D4 — The test base encodes the wiring

- **Import lists:** 24 whole-job tests (`@JdbcTest` plus `BatchAutoConfiguration`) each carry an `@Import` list of 85 to 89 classes (16 at 88, 7 at 89, 1 at 85), about 2,100 lines of near-identical text. All 24 name the same 84 classes. What varies is the extraction test double (`SeedScriptedExtractionBeans` in 18 of them), `ClusterFaults` in 4, and two probes. See `src/test/java/io/algernon/vespera/pipeline/CensusInvocationTest.java` and its 23 siblings.
- **Visibility:** 67 of the 87 pipeline types are package-private, and all 61 pipeline tests share the package.
- **Effect on production code:**
  - ADR-131 keeps ten one-bean configuration classes because "they are the seam the slice tests import".
  - ADR-132 made `StageFiveGates` static so it would not join those lists.
  - `GenerationTasklet.java:166-175` builds two repositories with `new` rather than injecting them, for the same reason.

  The test base is dictating the shape of the code.
- **Coverage gap:** no test pins a run id's inputs byte for byte. `ExtractionRunTest` checks `contains(...)`. A refactor that changed a run's `config_consumed` would pass today's suite.

### D5 — A second run of one stage over a reused walk stops the next stage (a defect)

Three recorded decisions meet here:

- **ADR-058:** a run id hashes the stage's implementation version, which is the SHA of the last commit to each module it names (`.mvn/scripts/implementation-versions.groovy`). It also hashes the configuration the run consumed. So a new build, or a new value in `config_consumed`, gives the stage a new run id.
- **ADR-115:** a walk that saw nothing new is discarded and the earlier walk reused (`corpus/WalkRecorder.java:140`, `discardIfNothingNewWasSeen`).
- **ADR-099:** a stage looks up its upstream run over the walk and refuses when it finds two (`pipeline/UpstreamRuns.java:58-64`).

**This is not a case no record foresaw.** ADR-099 described it and assigned its remedy to whichever change made walks reusable (`docs/adr/0099-…md:63-68`; `:85`, "whatever makes a walk reusable must ship the means of choosing, at which point this fault becomes a gate"). ADR-115 made walks reusable and left ADR-099's rule alone (`0115-…md:9`). So the obligation was never discharged, and ADR-099:63's "cannot arise today" stopped being true.

**The routes in**, traced by reading:

| Change | Second run of | Refused by |
|---|---|---|
| A build with a commit to `corpus` | byte-level reduction (`ByteLevelReductionTasklet.java:109`) | stage 2, `ExtractionRun.java:70` |
| A build with a commit to `extraction` or `similarity` | extraction (`ExtractionRun.java:73`) | stage 3, `ContentCensusRun.java:75` |
| A build with a commit to `pipeline` | content census | stage 4, `RedundancyRun.java:92` |
| `degenerateOutputConfidenceFloor` set after invocation 1 | extraction (its `config_consumed` records the floor) | stage 3, `ContentCensusRun.java:75` |
| `boilerplateDocumentFrequencyFloor` retuned after stage 5 ran (ADR-099's own example, `:51`) | content redundancy | seed measurement, `SeedMeasurementRun.java:100` |
| A Docling image or version change, which alters the extractor identity (ADR-147) | extraction | stage 3 |

- **The fourth route is on the README's own path:** that floor is read off `confidence-distribution.html`, which invocation 1 writes.
- **There is no way out from inside the tool.** In every case the refusal advises "Run a fresh walk" (`AmbiguousUpstreamRunException.java:32`), which ADR-115 makes impossible for an unchanged archive. Reverting the build or the value does not help, because both run rows stay. The only way out is to change the archive or delete the working directory.

**A sibling, [#296](https://github.com/algernon28/vespera/issues/296): an old approval keeps opening 6b's gate.** `ArrangementGate.approvedArrangement` (`ArrangementGate.java:70-80`) matches the approval against every arrangement of the walk.

- **How it happens:** over a reused walk, after a re-arrangement (a changed `relevanceScoreFloor`, say), the old approval still names the old arrangement and opens the gate on it. That contradicts ADR-107:19 and ADR-117:69.
- **What the operator sees:** if generation over the old arrangement already finished, nothing is regenerated. Meanwhile `arrangement.html` shows the new arrangement and the closing line says nothing is left to set.

Both are traced by reading, not yet by a test; Wave 0b starts with the tests that prove them. And **every other wave in this plan would trigger the first**, which is why it comes first.

### D6 — Sprawl inside the capability modules

- **`Ledger` does four jobs** (walks, occurrences, runs, verdicts and survivors), 27 public methods in 614 lines. The walk-recording half has one client, `corpus/WalkRecorder` (`finishedWalkFor` itself has 11 callers). `survivors` and `occurrencesOf` return Spring Batch's `ItemStreamReader` (`ledger/Ledger.java:529`), so a Batch type crosses into `ledger` and three other modules. Five copies of a "drain it into a set" helper sit in four modules. Each already breaks ADR-060, which makes survivors a reader precisely so that nothing holds a million-plus ids in a list.
- **`synthesis/Deliverable.java` is 1,015 lines with nine seams:**
  - orchestration;
  - index page;
  - cluster page;
  - membership numbering (ADR-133);
  - citation links;
  - pictures (ADR-149), with each picture hashed up to three times;
  - link destinations (ADR-135, ADR-137);
  - the CSV manifest;
  - Markdown escaping for the surroundings of ADR-134/136/138/148.

  The three Markdown surroundings share one escape set with increments: `inAHeading` is `escapeLinkText(onOneLine(x))`, `inACell` adds `|`, and `asLinkText` is `inACell`. ADR-138:77-79 records that convergence as a result, not a merger (and ADR-137 §4 keeps the destination apart). So a table with one row per surrounding fits the record, and one merged rule does not.
- **Six identical `*Schema` classes** each call `SchemaVersionGuard.require(module, version)`. Nothing calls them; they run at start-up by construction, because ADR-059 has each module check its own schema version while the context is built.
- **One `schema.sql`** of 711 lines and 32 tables, where ADR-049 and `docs/architecture.md` §1.5 say "schema.sql per module". Ownership is stated only in comments.
- **Three SQL reads across table ownership**, invisible to the Modulith boundary test because they are strings, not imports:
  - `Ledger.java:188` deletes `corpus`'s `walk_anomaly`;
  - `similarity/RedundancyResolution.java:205` reads `extraction`'s `extraction_metric`;
  - `embedding/SeedCorpusComparison.java:347` reads `extraction`'s `extraction_metric`.
- **Dead or test-only code:**
  - `extraction/LlmStructurelessChunkingFallback` throws on use, and enabling its property would register a second `StructurelessChunkingFallback` bean and fail start-up;
  - `pipeline/VectorStoreConfiguration` (nothing reads a vector store; ADR-142);
  - `Ledger.walkFinished`;
  - `ContentIdentity.representativeFor`;
  - `RelevanceFloor.State.removesAnything`;
  - a ten-argument `ExtractionItemProcessor` constructor kept for one test.
- **Dependencies with no use in the code:**
  - `lombok`: the annotation processor is configured twice, and no Lombok annotation exists;
  - `spring-boot-starter-actuator` and its test starter;
  - `spring-boot-starter-batch-test`;
  - `spring-ai-starter-model-openai` (the pom's own comment says nothing consumes it).
- **Duplicated helpers:**
  - SHA-256 to hex (4 copies);
  - SQL `LIKE` escaping (3);
  - the embedder-identity prefix (3);
  - Docling JSON `$ref` resolution (2), plus a double parse in `ExtractedText.from`;
  - the citation pattern (2);
  - the filename stem (2);
  - a cluster key record (2);
  - `WalkRecorder`'s `Pending*` records, which duplicate `RecordedOccurrence` and `RecordedAnomaly` field for field.

### D7 — Documentation mass

- **Comments:** 36% of `src/main` lines, most of them restating an ADR's reasoning in the class that implements it. Each ADR amendment then needs the same prose corrected in several javadocs, and several already disagree with the code:
  - `CensusJobConfiguration.java:13-14`;
  - `GenerationTasklet.java:368-372`;
  - `ExtractionOutcome.java:7-8`;
  - `RedundancyJobConfiguration.java:36-38`.
- **`AGENTS.md`:** line 23 alone is 10,751 characters of closed-defect history, and line 21 is 3,331 characters of per-module status narrative. Both are history, not instruction.
- **Stale agent definitions:** `.claude/agents/architect.md`, `spec-implementer.md` and `tester.md` name a `VesperaApplicationTests` that does not exist. `debugger.md` and `tester.md` describe a `src/test/resources/application.yaml` that shadows the main file; the real file is `application-test.yaml`, and it layers on top.

---

## 3. Target shape

**Principles:**

- **`pipeline` is wiring.** It holds the Batch job, its steps, the gates, and the step shell around each stage's work. It holds no rule that decides what is written. The test: could a change to this class alter a verdict, a cache key, a cluster or a sentence in the deliverable? If so, it belongs in a capability module.
- **One declaration per fact:**
  - one table of stages, each with its persisted name, the modules its implementation version spans, and how it finds its upstream run;
  - one place for step names;
  - the profile's keys come from `Profile` itself;
  - the report file names in one place.
- **Each table is read and written only by the module that owns it**, and a test says so, because the Modulith test cannot see SQL.
- **Tests describe behaviour, not wiring.** A whole-job test names its test double and nothing else.

**Kept on purpose:**

- **Spring Batch.** ADR-140's read-ahead is Batch's own chunk loop, the resourceless job repository costs nothing, and the exit-code mapping (ADR-141) rests on it.
- **The eight capability-shaped modules.** `docs/architecture.md:169` records why stage-shaped packages were rejected; nothing here reopens that.
- **The verdict ledger, the walk/run split, the gate model and the profile as a record.**

---

## 4. Invariants every wave keeps

1. **Persisted names do not change.** Every `run.stage` value and every `finished_step` step name stays byte-identical. A name is recorded, not hashed (`Ledger.startRun`), so a rename re-mints nothing. But upstream lookups and completion records go by name: a renamed stage finds no upstream run, and a renamed step redoes its work once (ADR-116:92).
2. **Persisted cache keys do not change.** This covers the extractor, chunker, chunking-rule, embedder, shingle and MinHash identity strings. A refactor may move the code that builds them, never the string it builds.
3. **Run ids do not change except where a wave declares it, and a re-mint is transitive.** A wave that edits a module named in a stage's implementation version re-mints that stage's runs and every run downstream of it (ADR-058, ADR-048). Every wave's row in §5 says whether it does, and Wave 0a's golden tests prove that the `config_consumed` text and the module lists did not move.
4. **Operator-visible text does not change.** This covers the closing line, gate sentences, report pages, the deliverable, and the profile keys an operator types. `OperatorTextTest`, `docs/check-claims.mjs` and the README are the guards.
5. **The module rule holds.** Capability modules depend on `ledger` alone, plus the one declared `extraction` → `corpus` exception (ADR-100).
6. **Gates and faults keep their outcomes.** No run is minted while its gate is shut (ADR-080). What is a gate, what is a fault, and every exit code stay as they are (ADR-099, ADR-141).
7. **The schema changes only where a wave declares it.** DDL and each module's schema version stay put unless a wave's ADR says otherwise (ADR-049, ADR-059), because a version bump makes the guard refuse an existing database.
8. **The job keeps its shape.** Step order, census's transaction setting (ADR-055), stage 2's chunk size and concurrency (ADR-140) and listener order (ADR-139) change only where a wave's ADR says so.
9. **One writer, bounded memory.** Nothing new writes from a worker thread, and nothing new holds the whole corpus in memory (ADR-127, ADR-140, ADR-060, ADR-149 §9).
10. **No test is weakened** (the `architect` gate). A test that has to move is moved by the `analyst`, never by the implementer.

---

## 5. The waves

| Wave | Goal | ADRs | Tests | Size (estimate) | Re-mints runs? | Risk |
|---|---|---|---|---|---|---|
| **0a** | One test base; pin run identity | ADR-153 amends 131 | done (#295): the 24 whole-job tests share `@CascadeSliceTest`; 8 golden tests; an import guard | −3,035 test lines (measured) | no (no `src/main` change) | low; in review |
| **0b** | Stop a second run over a reused walk from stranding the operator, and a stale approval from opening 6b (#290, #296) | amend 099, 058; 107 for #296 | failing tests first: two builds, the confidence floor set, the boilerplate floor retuned, a stale approval | small | yes, once (stages 3–6b) | medium |
| **1** | One way to mint a run, one way to shape a step | amend 131 (its second reason), 099, 080 | golden tests stay green | −1,100 main lines | yes (`pipeline`: stages 3–6b) | medium |
| **2** | Rules move to their modules | amend 110, 040; cites 070, 071, 111, 139, 140 | behaviour tests unchanged; unit tests per moved rule; `ExtractionItemProcessorTest`'s 18 tests ported | −1,000 in `pipeline`, partly moved, not deleted | yes (every stage whose modules it touches) | medium |
| **3** | Record stage 2's cache key | via ADR-151 (#287) | #287's and #289's tests | removes 7 of the 8 archive re-reads (8 if seed extraction records its key too) | yes, and a schema bump | medium |
| **4** | `Ledger` and table boundaries | amend 049, 041; weigh 059, 060 | a table-ownership guard; `SchemaVersionDeclarationTest` changes | about −300 | yes: moving `walk_anomaly` deletion to `corpus` re-mints stage 1 and everything downstream | low–medium |
| **5** | Split `Deliverable` | none new, if the surroundings become one table with a row each (ADR-134, 137, 138); cites 133, 148, 149 | `DeliverableTest` untouched | neutral in lines; the largest class goes | yes (6a/6b) | low |
| **6** | Hygiene | supersede 142; amend 046, 029, 039; check 034 | delete the dead code's own tests | −500 plus comments | yes, per module touched | low |
| **7** *(deferred)* | Take `pipeline` out of stages 3–6b's run identity | amend 058 | — | — | yes, once | medium |

The size figures are estimates from the classes named, not measurements. A wave's ADR should replace them with the measured diff.

### Wave 0a — One test base, and run identity pinned (done: #294, in review as #295)

Owner: `analyst` alone. No `src/main` change, so nothing is re-minted. What landed, measured:

- **`@CascadeSliceTest`**, a meta-annotation in the `pipeline` test package so package-private types stay reachable.
  - It carries the five class-level annotations and an 87-class `@Import`: the 84 classes all 24 tests name, plus 3 that 23 of them name (`HybridChunkerBeans`, `ExtractionMetrics`, `LanguageDetection`).
  - Each test imports only its extraction double, `ClusterFaults` in four tests, and its probes.
  - `UnconfiguredRootTest` uses it too; its invocation refuses before any stage runs, so the extra beans are built and never called.
- **A duplicate import does not fail loudly.** Measured: Spring merges a class imported by both the annotation and the test into one set, silently. So `CascadeSliceImportsTest` fails any test that repeats a shared import.
- **`RunIdentityGoldenTest`:** one test per kind of run (eight), pinning the exact `config_consumed` text and the module list and order of the implementation version. The order is made visible by a test-only `ModuleNamedVersionsBeans`. Both floors are pinned only as unset.
- **ADR-153** amends ADR-131. Its first reason ("the seam the slice tests import") no longer holds. Its second ("the place each stage's step is named") is Wave 1's to weigh.
- **Result:** 709 → 718 tests, 0 failures. The 24 files lose 3,035 lines.

### Wave 0b — A second run over a reused walk, and a stale approval ([#290](https://github.com/algernon28/vespera/issues/290), [#296](https://github.com/algernon28/vespera/issues/296))

1. **Failing tests first.** Each runs two invocations over one database and asserts that the second completes:
   - with a changed implementation version for one module. This is the two-builds test, moved here from 0a so 0a lands green; it can vary one module's version through 0a's `ModuleNamedVersionsBeans` seam;
   - with `degenerateOutputConfidenceFloor` set between them;
   - with `boilerplateDocumentFrequencyFloor` retuned after stage 5.

   And one for #296: approve an arrangement, change `relevanceScoreFloor`, invoke again, and 6b must not proceed over the older arrangement.
2. **An ADR amending ADR-099 on how a stage chooses its upstream run once one walk holds two.** This is an open decision with three options:
   - ADR-099's own answer: a gate naming the run to continue from.
   - Take the upstream run this invocation minted or continued. Since ADR-115 every invocation starts at census, so the id is in hand; this amends ADR-099:23's reasoning.
   - Narrow by the current build's implementation version only. That fixes the build routes and leaves the configuration routes stranding the operator, so it is not enough alone.

   Whichever is chosen, correct `AmbiguousUpstreamRunException`'s advice. The same ADR, or a sibling amending ADR-107, settles #296: the approval matches only the arrangement this invocation minted or continued.
3. **An ADR amending ADR-058.** Its cost is per build, not per commit, and ADR-115 changed what that cost is. Record what an operator sees when a build lands mid-campaign, and the stage 1–2 gap in D2.
4. **Code:** a `StageModules` table and the chosen lookup. The table holds each stage's name and the modules its implementation version spans, which is the ADR-058 table in one place. It is named so as not to borrow the instruments' word "identity".

The fix itself re-mints stages 3 to 6b once.

### Wave 1 — One way to mint a run, one way to shape a step

**Minting.** Replace the seven run classes and the inline mint in stage 1 with one helper in `pipeline`, reusing Wave 0b's `StageModules`:

```java
record StageModules(String stage, List<String> modules) {} // from Wave 0b

final class StageRuns {
    WalkId finishedWalk(Path canonicalRoot, String whatNeedsIt);
    RunId mint(StageModules stage, Record configConsumed, WalkId walk, Optional<RunId> upstream);
    RunId mint(StageModules stage, String configJson, WalkId walk, Optional<RunId> upstream); // stage 1's "{}"
}
```

Three conditions keep every id byte-identical, and Wave 0a's golden tests enforce them:
- Each stage keeps its **own private `ConfigConsumed` record**. Their field names differ (`root` in `ScoringRun`, `corpusRoot` in `ArrangementRun`), and unifying them re-mints for nothing.
- Each stage keeps its **module list in its current order**.
- Each stage keeps **its current way of finding its upstream run**. Switching the scoring run's consumers to a lookup would hit ADR-099's refusal on the operator's normal path, because invocation 4 sets `relevanceScoreFloor` and so mints a second scoring run over the same walk.

**Scope.** Replace the seven `@JobScope`/`@StepScope` run beans, their `ObjectProvider` plumbing and the four constructors that re-check a gate with one `@JobScope InvocationRuns`. It remembers each id lazily and is called explicitly after the step's own gate. Since ADR-115, the only thing scoping buys is "derived once per invocation", and one bean keeps that. Record this against ADR-080, whose rule ("no run row behind a shut gate") the explicit call keeps.

**The step shell.** One function, not a base class (clustering and arrangement check their gates in a different order):

```java
static RepeatStatus once(Ledger ledger, RunId run, String step, Runnable discard, Runnable work)
```

It does not absorb `RunCompletion` or `RedundancyJobConfiguration`'s `SignatureStepCompletion`. Those are completion listeners on chunk steps, and ADR-139 pins `ExtractionFaultRecorder`'s registration after `RunCompletion`. The two listeners can become one gated listener beside `once(...)`, not inside it.

**Wiring:**
- Fold the ten one-bean configuration classes into the job configuration, and rename `CensusJobConfiguration` for what it is. ADR-153 left ADR-131's second reason ("the place each stage's step is named") for this wave's ADR to weigh.
- Put every step name in one place, with the persisted values unchanged.
- Make `GenerationTasklet.java:381`'s recompute a lookup. It can only be done after Wave 0b, because a lookup there would otherwise hit the trap.
- Collapse the three "cannot name one run" exceptions (`NoUpstreamRunException`, `AmbiguousUpstreamRunException`, `AmbiguousArrangementException`) if their ADR agrees. They are fatal, never caught, and asserted only by type.

### Wave 2 — Rules go home

- **To `synthesis`: 6b's loop, breaker and completion rule** (`GenerationTasklet.java:212-339`). The loop already uses only `synthesis` types plus `Ledger`. Its one outside input is each cluster's exemplars, which come from `embedding`'s membership and scores, `extraction`'s leading chunks, and `ledger`'s facts. Hand them over through a `synthesis`-owned callback, `ClusterExemplars`, on the precedent of `SurvivorPictures` (ADR-149). Keep it lazy per cluster so file reads, warnings and memory stay as they are. The loop returns a sealed result: finished, incomplete, or stopped with its faults. `pipeline` keeps stopping the step, writing the deliverable and recording completion. The callback must also supply the winning seed's path (`GenerationTasklet.java:274`), and ADR-111's breaker moves with the loop. This is ADR-110's hand-over, extended, and needs an amendment saying so.
- **To `synthesis`: the lead-document and label rule** (`ArrangementTasklet.java:235-264`). Today it is computed twice per cluster.
- **To `extraction`: ADR-070's classification and the timeout streak** (`ExtractionItemProcessor.java:267-376`), together with what ADR-071, ADR-139 and ADR-140 say about them. ADR-140's rule that both streak counters are observed on one thread must survive the move. `ExtractionItemProcessorTest`'s 18 tests, which use the test-only constructor, are ported to the new home, not deleted. `ServiceScopeFailureException` stays the Batch skip marker. `ExtractorStoppedAnsweringException` keeps its fully-qualified name, which `ExceptionNamingTest` pins, or the test moves with it via the `analyst`.
- **To `extraction`: the extractor identity string** (`ExtractionJobConfiguration.java:287-297`). The string must come out byte-identical; `ExtractorIdentityCompositionTest` is the guard.
- **To `corpus`: content-identity resolution, size then hash** (`ByteLevelReductionTasklet.java:218-278`; ADR-067, ADR-069). ADR-040 is a reconstituted record, and the reading of it this changes is the one at `ByteLevelReductionTasklet.java:55-57`; the amendment should quote it.
- **What this buys for stages 1 and 2:** they name no `pipeline` version (D2), so these moves version their rules for the first time.
- **Profile keys:** `GenerationTasklet.java:460-471`'s list comes from `Profile`'s own component order, which it already matches.

### Wave 3 — Record stage 2's cache key

Stage 2 records, per occurrence and under its own run, the key it used for the extraction cache. Seven of the eight later sites in D3, the corpus-side ones, look it up instead of reading the archive. The eighth, `RelevanceScoringTasklet.java:177`, hashes seed occurrences, which stage 2 never sees; it goes too only if seed extraction records its key as well (`SeedExtractionItemProcessor.java:92`).

- **Why here:** this is the substance of #287, so it belongs in ADR-151's decision rather than a new one, provided ADR-151, already being written, decides that stage 2 records the key. It also turns ADR-152's (#289) per-step split into a question about one remaining site, since the other four steps stop reading the archive.
- **Cost:** a schema version bump in `extraction`, so `SchemaVersionGuard` refuses an existing database and the operator starts a fresh working directory. Land it at a corpus boundary, never mid-campaign.

### Wave 4 — `Ledger` and table boundaries

- **Split `Ledger` inside `ledger`** into walks, occurrences, runs and verdicts/survivors. `ledger` is in no stage's implementation version, so the split itself re-mints nothing. Moving the walk half into `corpus` is *not* proposed; ADR-041 puts identity in `ledger`.
- **Take `ItemStreamReader` out of the capability APIs without breaking ADR-060.**
  - ADR-060 makes survivors a Spring Batch reader, "never a materialized `List`" of what could be a million-plus ids. So the replacement is a streaming shape without the Batch type, such as a cursor or a `Stream<OccurrenceId>` its caller closes, not a `survivorIds` list.
  - The five drain helpers already break ADR-060, and go with this change.
  - First check the semantics: `survivors()` is a paged, live query, and any shape that reads ahead changes behaviour if a step writes blocking verdicts ahead of its own cursor.
  - The ADR records what was checked, and amends ADR-060 if the Batch type itself is what that record named.
- **Fix the three table-ownership breaches:**
  - `walk_anomaly` deletion moves to `corpus` (`AnomalyLog`), called in the same transaction by `WalkRecorder`. This is a `corpus` change, so it re-mints stage 1 and, through the upstream chain, every stage after it.
  - The two `extraction_metric` reads get their rows from `pipeline`, the way `pipeline` already hands `synthesis` its inputs.
- **One schema file per module** (`schema/ledger.sql` and so on, via `spring.sql.init.schema-locations`), which makes ADR-049's text true. Add a table-ownership guard test that fails when a module's SQL names another module's table. Amend ADR-049.
- **One schema-version declaration per module** instead of six classes. They run at start-up by construction (ADR-059), and the replacement must still fail while the context is being built. `SchemaVersionDeclarationTest` changes with it (`analyst`).

### Wave 5 — Split `Deliverable`

Package-private collaborators inside `synthesis`, each with the rule it carries:

| Collaborator | Carries |
|---|---|
| `MarkdownSurroundings` | one table with a row per surrounding (heading, cell, link text). The rules move here from `Deliverable` and are not copied, so ADR-134's reopen trigger (a second class under `synthesis` with an escaping method) is not tripped. One table keeps ADR-138:77-79 and ADR-137 §4, where one merged rule would not. |
| `ArchiveLink` | relative destinations and their percent-encoding |
| `ManifestCsv` | RFC 4180 quoting |
| `ClusterPage` | the cluster file, including membership numbering from the call's exemplars |
| `IndexPage` | the index file |
| `DeliverablePictures` | pictures, with one digest per picture |

`Deliverable` stays the orchestrator and keeps its public surface, so `DeliverableTest` does not change. Also:
- one citation pattern, shared with `ClusterSynthesis`;
- one filename stem, shared with `ClusterLabel`;
- one public cluster key, which absorbs the `Recorded*` wrappers that are only "(winning seed, cluster ordinal) plus X".

### Wave 6 — Hygiene

- **Delete dead code:**
  - the LLM chunking fallback and its interface, injecting the windowed one directly. Its "currently off" seam is ADR-029's recorded shape, so this amends ADR-029;
  - `Ledger.walkFinished`;
  - `ContentIdentity.representativeFor`;
  - `RelevanceFloor.State.removesAnything`;
  - `RedundancyResolutionTasklet.STEP`, which only aliases `RedundancyRun.STAGE`.

  The test-only `ExtractionItemProcessor` constructor goes in Wave 2, with its 18 tests ported.
- **Pom:** each removal cites the decision that stops requiring it (ADR-046). Candidates: `lombok`, the actuator starters, `spring-boot-starter-batch-test`, and the OpenAI starter. The OpenAI starter's pom comment cites ADR-034 (the hosted ceiling model in the embedding bake-off), so check ADR-034 and ADR-072 first.
- **Chroma:** `VectorStoreConfiguration`, the Chroma starter and its test container go only under an ADR that also weighs ADR-039, which makes Chroma the disposable projection and which ADR-142 keeps. "Re-add it when the first reader is built" is the reasoning ADR-046 rejects (the pom carries what a decision requires), so the ADR has to retire the requirement, not just the dependency.
- **Duplicated helpers:** remove them only inside modules another wave is already re-minting, so no wave re-mints a stage just to delete a copy.
- **Javadoc policy:** a class cites the ADR it implements and states its own contract. It does not restate the ADR's reasoning. Correct the stale javadocs in D7.
- **Agent files:** move `AGENTS.md`'s defect history (line 23) and status narrative (line 21) out, keeping every sentence `docs/check-claims.mjs` matches. Fix the stale agent definitions.

### Wave 7 — Take `pipeline` out of run identity (deferred)

Once Wave 2 leaves `pipeline` holding no rule that shapes output, drop it from stages 3 to 6b's module lists (amend ADR-058). A wiring change then stops re-minting them. Doing it before Wave 2 would make invalidation too lazy, which is exactly the failure ADR-058 exists to prevent, and which D2 shows is already true of stages 1 and 2.

---

## 6. Not doing, and why

| Tempting move | Why not |
|---|---|
| Replace Spring Batch with plain loops | Saves about 600 lines. It costs a rewrite of the three chunk steps and the exit mapping, ADR-140's read-ahead, 24 tests, and amendments to ADR-036, 047, 131, 140 and 141. The resourceless repository already makes it nearly free. |
| Derive `NextAction` and every gate from one ordered table | The orders differ. `NextAction` names values in README order (seed folder, boilerplate floor, embedding model). The job meets them in cascade order (floor, then seed, then model). One table would change the sentence an operator reads. |
| One SHA-256 helper for every module | The copies produce persisted keys in modules with different implementation versions. Merging them re-mints stages for no behavioural gain. Do it only inside Wave 6's rule. |
| De-duplicate the 15 `discardForRun` methods (16 with `Ledger.discardVerdicts`) | Each is one to three lines against its own module's table, which ADR-041 wants owned there. |
| Move `Ledger`'s walk half into `corpus` | ADR-041 puts occurrence identity in `ledger`. Split it inside `ledger` instead (Wave 4). |
| Stage-shaped packages under `pipeline` | Rejected in `docs/architecture.md:169` for a reason that still holds. |

---

## 7. What an operator sees, and how to deliver

**What a wave that re-mints costs an operator mid-campaign.** This is after Wave 0b; before it, the operator is stranded instead (D5).

- A re-minted stage 2 re-hashes every surviving file (`ExtractionItemProcessor.java:203-205`), though its conversions are cache hits.
- Stages 3 and 4 redo their work from the database alone.
- Embeddings are cache hits. Until Wave 3, though, every re-minted step in stages 5 to 6b re-reads the archive at the D3 sites.
- Clustering and 6a recompute and mint a new arrangement. Whether the operator is asked to approve it depends on #296: today the old approval keeps opening the gate over the old arrangement.
- 6b calls the generation model once per cluster again for any arrangement it has not finished, and writes a second deliverable tree beside the first.

**Delivery:**

- Merge the waves in order.
- The cost is paid per build, not per commit, so an operator mid-campaign should take one build that carries several waves, at a corpus boundary if possible.
- **The same holds for the open tickets.** #286, #287, #289 (in #293) and #291 each edit `extraction` or `pipeline`. Until Wave 0b lands, each merge re-mints and strands an operator part-way through a corpus. Release them at a corpus boundary too.
- Wave 3 needs a fresh working directory whenever it lands.

---

## 8. Open decisions

Each belongs in the ADR of the wave that meets it. They are listed here so no wave discovers them late.

1. **Wave 0b:**
   - how a stage chooses its upstream run once one walk holds two (the three options in Wave 0b);
   - how the arrangement approval is matched over a reused walk (#296).
2. **Wave 1:** whether the three "cannot name one run" exceptions become one.
3. **Wave 4:** which streaming shape replaces the Batch reader, and whether ADR-060 is kept or amended, measured at every current call site rather than argued.
4. **Wave 4:** per-module schema files. Accept the one-time move, or keep one file and correct the documentation instead.
5. **Wave 6:**
   - which decision, if any, still requires each pom entry in D6 (ADR-034 for the OpenAI starter, ADR-072 for the reference model);
   - whether ADR-039's requirement for Chroma is retired.
6. **Wave 6:** the javadoc policy above, recorded once so later changes follow it.

---

## 9. Tracker shape

- **Bugs now:** [#290](https://github.com/algernon28/vespera/issues/290) (a second run of one stage over a reused walk) and its sibling [#296](https://github.com/algernon28/vespera/issues/296) (a stale approval). They are defects in what ships and should not wait for a map.
- **Wave 0a** is [#294](https://github.com/algernon28/vespera/issues/294), in review as #295.
- **Then a wayfinder map, "Architecture simplification":** one child ticket per remaining wave, with 7 parked. Each ticket's resolution comment is its spec, as usual.
  - Charting it means editing the two `AGENTS.md` sentences that `docs/check-claims.mjs` checks (lines 44–46, starting "**No wayfinder map is open.**"), in the same change that opens the map. Otherwise CI's claims job fails.
- **Relation to open work:**
  - **#286** (PDF pictures) is independent of the waves, but see §7 on when to release it.
  - **#287** (blank `content_hash`, ADR-151) *is* Wave 3, provided ADR-151 decides that stage 2 records the key.
  - **#289** (the relevance report and a file that will not open, ADR-152, in #293) is settled on its own terms first. Wave 3 later removes the re-reads that ADR-152 decides to fail on.
  - **#291** is a seed-side twin of #289: a seed file that cannot be opened fails seed extraction instead of becoming an unusable seed.

---

## Appendix A — Evidence index

| Finding | Where |
|---|---|
| The job and its fifteen steps | `pipeline/CensusJobConfiguration.java:31-66` |
| Stage 1's inline mint | `pipeline/ByteLevelReductionTasklet.java:104-109` |
| Recomputed upstream id | `pipeline/GenerationTasklet.java:381-385` |
| Upstream refusal | `pipeline/UpstreamRuns.java:58-64`; `AmbiguousUpstreamRunException.java:32` |
| Walk reuse | `corpus/WalkRecorder.java:140` |
| Per-commit implementation versions | `.mvn/scripts/implementation-versions.groovy` |
| "Cannot arise today", and the remedy handed on | `docs/adr/0099-*.md:63-68, 85`; `docs/adr/0115-*.md:9` |
| Floors in run identity | `pipeline/ExtractionRun.java:73, 88-90`; `pipeline/SeedMeasurementRun.java:100` |
| Approval by id prefix, over every arrangement of the walk (#296) | `pipeline/ArrangementGate.java:70-80` |
| Failure classification in `pipeline` | `pipeline/ExtractionItemProcessor.java:267-376` |
| Extractor identity in `pipeline` | `pipeline/ExtractionJobConfiguration.java:287-297` |
| 6b's loop in `pipeline` | `pipeline/GenerationTasklet.java:212-339` |
| Hand-written profile keys | `pipeline/GenerationTasklet.java:460-471` |
| Label rule in `pipeline` | `pipeline/ArrangementTasklet.java:235-264` |
| Content-identity resolution in `pipeline` | `pipeline/ByteLevelReductionTasklet.java:218-278` |
| The archive re-read | `DoclingExtractor.java:65`; the eight sites in D3 |
| Seed-side first read | `pipeline/SeedExtractionItemProcessor.java:92` |
| Batch type in `ledger` | `ledger/Ledger.java:529` |
| Cross-ownership SQL | `ledger/Ledger.java:188`; `similarity/RedundancyResolution.java:205`; `embedding/SeedCorpusComparison.java:347` |
| Test import lists | the 24 `pipeline/*InvocationTest.java` and sibling whole-job tests, 85 to 89 classes each |
| Stage-shaped packages rejected | `docs/architecture.md:169` |

## Appendix B — How the figures were taken

The line and file counts are `wc -l` over `src/main/java` and `src/test/java` at `6f16ccc`. Comment lines are lines whose first non-blank characters are `*`, `/*` or `//`. Call-site counts are `grep -c` over `pipeline`. Import-list sizes count `.class` literals inside each test's `@Import({...})`. D5's routes and #296 were traced through the code named there; they have not been executed. After the `architect` review, the import counts were re-taken from git objects at `6f16ccc`, and Wave 0a's figures were measured on its own commit (`9490d2c`, #295).
