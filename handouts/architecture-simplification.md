# Architecture simplification: a plan

**Date:** 2026-09-25
**Status:** proposal. Nothing here is decided. Every wave below needs its own ADR before any code moves, and follows the usual route: `analyst` → `spec-implementer` → `tester` → `architect`.
**Scope:** `src/main`, the test base that pins it, and the documentation that describes it. It changes nothing an operator does and removes no capability.

---

## 1. Summary

Vespera's *model* is sound: a verdict ledger rather than a moving pipeline, capability-shaped modules, gates as required inputs, and run identity derived from what a run consumed. This plan keeps all of that.

What has drifted is the *code*. It has grown by copying rather than composing:

- `pipeline`, the composition root, is 88 of the 231 main files. Most of it is the same five pieces of scaffolding written once per stage.
- Rules that decide what the deliverable contains live in `pipeline`, not in the module that owns the capability.
- One fact is never recorded: the key stage 2 used for a file occurrence's extraction cache. Six later classes read the archive again to recompute it. #287 and #289 are two symptoms.
- The test base names every pipeline class 24 times over, and production code has twice been bent to keep those lists from growing (ADR-131, ADR-132).

Verifying this plan also turned up **a defect nothing tracked**, now [#290](https://github.com/algernon28/vespera/issues/290). After any build that changes `corpus`, `extraction`, `similarity` or `pipeline`, an operator part-way through a corpus is stopped at their next invocation, and nothing they can set gets them out of it (§2, D5).

The five moves that matter:

1. **One test base** for the whole-job tests, so moving a class no longer means editing 24 files (Wave 0a).
2. **Fix the upgrade trap** before anything else changes a module's implementation version (Wave 0b).
3. **One way to mint a stage's run and one way to shape a step**, replacing seven run classes and ten one-method configuration classes (Wave 1).
4. **Rules go home.** Stage 2's failure classification moves to `extraction`, 6b's generation loop to `synthesis`, and duplicate grouping to `corpus` (Wave 2).
5. **Record stage 2's cache key once**, which removes every later re-read of the archive (Wave 3, folded into #287).

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
| Size-then-hash duplicate grouping | `ByteLevelReductionTasklet.java:218-278` | `corpus` |
| The eight profile keys, written out by hand | `GenerationTasklet.java:460-471` | derived from `Profile` |

This matters for more than tidiness. A rule in `pipeline` versions under `pipeline`'s implementation version, which six of the eight kinds of run include in their identity, so any edit to the wiring re-mints those runs. Once `pipeline` holds no rule that shapes output, it can drop out of run identity (Wave 7).

### D3 — One fact is never recorded, so it is recomputed

Stage 2 keys the extraction cache by the SHA-256 of a file occurrence's bytes (`extraction/DoclingExtractor.java:65`, `contentHashFor`). No table records which key belongs to which occurrence: `corpus`'s `content_hash` holds only occurrences stage 1 hashed for a size collision. So every later consumer reads the file again to recompute the key:

- `EmbeddingScoringTasklet.java:153`
- `RelevanceScoringTasklet.java:148` (and `:177` on the seed side)
- `ClusteringTasklet.java:207`
- `RelevanceReportTasklet.java:287`
- `ArrangementTasklet.java:251`
- `GenerationTasklet.java:434, 664`

That is eight call sites in six classes, each with its own answer to "what if the file cannot be opened any more". #289 is the case where the answer is "fail the invocation". #287 is the manifest's `content_hash` column coming out blank for the same underlying reason. It also means stages 5 and 6 read every surviving byte of the archive again, which on the 42,851-file folder this tool exists for is not free.

### D4 — The test base encodes the wiring

- **Import lists:** 24 whole-job tests (`@JdbcTest` plus `BatchAutoConfiguration`) each carry an `@Import` list of 88 classes, about 2,100 lines of near-identical text. Only the extraction test double and two probes vary between them. See `src/test/java/io/algernon/vespera/pipeline/CensusInvocationTest.java` and its 23 siblings.
- **Visibility:** 67 of the 87 pipeline types are package-private, and all 61 pipeline tests share the package.
- **Effect on production code:**
  - ADR-131 keeps ten one-bean configuration classes because "they are the seam the slice tests import".
  - ADR-132 made `StageFiveGates` static so it would not join those lists.
  - `GenerationTasklet.java:166-175` builds two repositories with `new` rather than injecting them, for the same reason.

  The test base is dictating the shape of the code.
- **Coverage gap:** no test pins a run id's inputs byte for byte. `ExtractionRunTest` checks `contains(...)`. A refactor that changed a run's `config_consumed` would pass today's suite.

### D5 — The upgrade trap (a defect)

Three recorded decisions combine into a trap none of them describes:

- **ADR-058:** a stage's implementation version is the SHA of the last commit to each module it names (`.mvn/scripts/implementation-versions.groovy`). A new build gives a stage a new run id.
- **ADR-115:** a walk that saw nothing new is discarded and the earlier walk reused (`corpus/WalkRecorder.java:140`, `discardIfNothingNewWasSeen`).
- **ADR-099:** a stage looks up its upstream run over the walk and refuses when it finds two (`pipeline/UpstreamRuns.java:58-64`).

Take an operator between invocations 1 and 2 (README) who picks up a build with any commit to `pipeline`:

1. Stage 3 mints a second `content-census` run over the reused walk.
2. Stage 4's `RedundancyRun` finds two and throws `AmbiguousUpstreamRunException`.
3. The message says "Run a fresh walk" (`AmbiguousUpstreamRunException.java:32`), which ADR-115 makes impossible for an unchanged archive.
4. The only way out is to change the archive or delete the working directory.

A commit to `corpus` trips the same wire one stage earlier (stage 2's lookup of stage 1), as does a commit to `extraction` or `similarity` (stage 3's lookup of stage 2).

ADR-099 says the condition "**cannot arise today**" (`docs/adr/0099-…md:63`), because every invocation then minted a fresh walk; ADR-115 made it reachable. This is traced by reading, not yet by a test. Wave 0b starts with the test that proves it.

It also means **every other wave in this plan would trigger it**, which is why it comes first.

### D6 — Sprawl inside the capability modules

- **`Ledger` does four jobs** (walks, occurrences, runs, verdicts and survivors), 27 public methods in 614 lines. The walk half has one client, `corpus/WalkRecorder`. `survivors` and `occurrencesOf` return Spring Batch's `ItemStreamReader` (`ledger/Ledger.java:529`), so a Batch type crosses into `ledger` and three other modules. Five modules each carry a copied "drain it into a set" helper.
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

  The three Markdown surroundings are one rule with increments: `inAHeading` is `escapeLinkText(onOneLine(x))`, `inACell` adds `|`, and `asLinkText` is `inACell`.
- **Six identical `*Schema` classes** each call `SchemaVersionGuard.require(module, version)`, and nothing references them.
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

1. **Persisted names do not change.** Every `run.stage` value and every `finished_step` step name stays byte-identical (ADR-116).
2. **Persisted cache keys do not change.** This covers the extractor, chunker, chunking-rule, embedder, shingle and MinHash identity strings. A refactor may move the code that builds them, never the string it builds.
3. **Run ids do not change except where a wave declares it.** A wave that edits a module named in a stage's implementation version re-mints that stage's runs by construction (ADR-058). Every wave's row in §5 says whether it does, and Wave 0a's golden tests prove the `config_consumed` text itself did not move.
4. **Operator-visible text does not change.** This covers the closing line, gate sentences, report pages and the deliverable. `OperatorTextTest`, `docs/check-claims.mjs` and the README are the guards.
5. **The module rule holds.** Capability modules depend on `ledger` alone, plus the one declared `extraction` → `corpus` exception (ADR-100).
6. **No test is weakened** (the `architect` gate). A test that has to move is moved by the `analyst`, never by the implementer.

---

## 5. The waves

| Wave | Goal | ADRs | Tests | Size (estimate) | Re-mints runs? | Risk |
|---|---|---|---|---|---|---|
| **0a** | One test base; pin run identity | amend 131 | the 24 whole-job tests move onto a shared annotation; new golden tests | −2,000 test lines | no (no `src/main` change) | low |
| **0b** | Fix the upgrade trap | amend 099, 058 | new failing test first | small | yes, once (stages 3–6b) | medium |
| **1** | One way to mint a run, one way to shape a step | amend 131, 099, 080 | golden tests must stay green | −1,100 main lines | yes (`pipeline`) | medium |
| **2** | Rules move to their modules | amend 110, 040 | existing behaviour tests unchanged; new unit tests per moved rule | −1,000 in `pipeline`, partly moved, not deleted | yes (all modules touched) | medium |
| **3** | Record stage 2's cache key | via ADR-151 (#287) | #287's and #289's tests | removes 8 archive re-reads | yes, and a schema bump | medium |
| **4** | `Ledger` and table boundaries | amend 049, 041 | a table-ownership guard; `SchemaVersionDeclarationTest` changes | about −300 | yes where `similarity`/`embedding` change | low–medium |
| **5** | Split `Deliverable` | none new; cites 133–138, 148, 149 | `DeliverableTest` untouched | neutral in lines; the largest class goes | yes (6a/6b) | low |
| **6** | Hygiene | supersede 142, amend 046 | delete the dead code's own tests | −500 plus comments | yes, per module touched | low |
| **7** *(deferred)* | Take `pipeline` out of run identity | amend 058 | — | — | yes, once | medium |

The size figures are estimates from the classes named, not measurements. A wave's ADR should replace them with the measured diff.

### Wave 0a — One test base, and run identity pinned

Owner: `analyst` alone. No `src/main` change, so nothing is re-minted.

- **Add `@CascadeSliceTest`**, a meta-annotation in the `pipeline` test package so package-private types stay reachable. It carries the five class-level annotations the 24 tests share, plus `@Import` of the 82 classes all 24 name. Spring merges meta-annotation imports with a test's own, and bean overriding is off, so a duplicate fails loudly rather than silently. Each test then imports only its extraction double and its probes. `UnconfiguredRootTest` differs (no `ExtractionMetrics`, no `HybridChunker`) and stays explicit, or is checked.
- **Add golden tests**, one per stage. Each runs the whole job over a fixed corpus and asserts that stage's `run.config_consumed` text and `implementation_version` module list exactly, as strings.
- **Add a two-build test:** two invocations over one database with different implementation versions. It is red until Wave 0b.
- **ADR:** amend ADR-131. Its stated reason for keeping ten configuration classes stops holding once the list is shared, and saying so is what licenses Wave 1.

### Wave 0b — The upgrade trap

1. Take [#290](https://github.com/algernon28/vespera/issues/290) and write the failing test first: the two-build test from 0a.
2. **ADR amending ADR-099:** when narrowing to one upstream run, prefer the candidates minted under the current build's implementation version. Ambiguity from a *configuration* change is still refused, as ADR-099 intends. Also correct `AmbiguousUpstreamRunException`'s advice, since a fresh walk is not available for an unchanged archive.
3. **ADR amending ADR-058:** its cost is per build, not per commit, and ADR-115 changed what that cost is. Record what an operator sees when a build lands mid-campaign.
4. **Code:** a `StageIdentity` table (stage name plus module list, the ADR-058 table in one place) and the narrowing inside `UpstreamRuns`.

The fix itself re-mints stages 3 to 6b once. That is the last time a build should strand anyone.

### Wave 1 — One way to mint a run, one way to shape a step

**Minting.** Replace the seven run classes and the inline mint in stage 1 with one helper in `pipeline`:

```java
record StageIdentity(String stage, List<String> modules) {}

final class StageRuns {
    WalkId finishedWalk(Path canonicalRoot, String whatNeedsIt);
    RunId mint(StageIdentity stage, Record configConsumed, WalkId walk, Optional<RunId> upstream);
    RunId mint(StageIdentity stage, String configJson, WalkId walk, Optional<RunId> upstream); // stage 1's "{}"
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

It absorbs `RunCompletion` (one use) and the private copy in `RedundancyJobConfiguration`.

**Wiring:**
- Fold the ten one-bean configuration classes into the job configuration, and rename `CensusJobConfiguration` for what it is.
- Put every step name in one place, with the persisted values unchanged.
- Make `GenerationTasklet.java:381`'s recompute a lookup. It can only be done after Wave 0b, because a lookup there would otherwise hit the trap.
- Collapse the three "cannot name one run" exceptions (`NoUpstreamRunException`, `AmbiguousUpstreamRunException`, `AmbiguousArrangementException`) if their ADR agrees. They are fatal, never caught, and asserted only by type.

### Wave 2 — Rules go home

- **To `synthesis`: 6b's loop, breaker and completion rule** (`GenerationTasklet.java:212-339`). The loop already uses only `synthesis` types plus `Ledger`. Its one outside input is each cluster's exemplars, which come from `embedding`'s membership and scores, `extraction`'s leading chunks, and `ledger`'s facts. Hand them over through a `synthesis`-owned callback, `ClusterExemplars`, on the precedent of `SurvivorPictures` (ADR-149). Keep it lazy per cluster so file reads, warnings and memory stay as they are. The loop returns a sealed result: finished, incomplete, or stopped with its faults. `pipeline` keeps stopping the step, writing the deliverable and recording completion. This is ADR-110's hand-over, extended, and needs an amendment saying so.
- **To `synthesis`: the lead-document and label rule** (`ArrangementTasklet.java:235-264`). Today it is computed twice per cluster.
- **To `extraction`: ADR-070's classification and the timeout streak** (`ExtractionItemProcessor.java:267-376`). `ServiceScopeFailureException` stays the Batch skip marker. `ExtractorStoppedAnsweringException` keeps its fully-qualified name, which `ExceptionNamingTest` pins, or the test moves with it via the `analyst`.
- **To `extraction`: the extractor identity string** (`ExtractionJobConfiguration.java:287-297`). The string must come out byte-identical; `ExtractorIdentityCompositionTest` is the guard.
- **To `corpus`: size-then-hash duplicate grouping** (`ByteLevelReductionTasklet.java:218-278`).
- **Profile keys:** `GenerationTasklet.java:460-471`'s list comes from `Profile`'s own component order, which it already matches.

### Wave 3 — Record stage 2's cache key

Stage 2 records, per occurrence and under its own run, the key it used for the extraction cache. The later sites in D3 look it up instead of reading the archive.

- **Why here:** this is the substance of #287, so it belongs in ADR-151's decision rather than a new one. It also turns ADR-152's (#289) per-step split into a question about one remaining site, since the other four steps stop reading the archive.
- **Cost:** a schema version bump in `extraction`, so `SchemaVersionGuard` refuses an existing database and the operator starts a fresh working directory. Land it at a corpus boundary, never mid-campaign.

### Wave 4 — `Ledger` and table boundaries

- **Split `Ledger` inside `ledger`** into walks, occurrences, runs and verdicts/survivors. `ledger` is in no stage's implementation version, so this re-mints nothing. Moving the walk half into `corpus` is *not* proposed; ADR-041 puts identity in `ledger`.
- **Take `ItemStreamReader` out of the capability APIs**, and give `ledger` one `survivorIds(RunId)` so the five drain helpers go. First check the semantics: `survivors()` is a paged, live query, and a snapshot changes behaviour if a step writes blocking verdicts ahead of its own cursor. The ADR records what was checked.
- **Fix the three table-ownership breaches:**
  - `walk_anomaly` deletion moves to `corpus` (`AnomalyLog`), called in the same transaction by `WalkRecorder`.
  - The two `extraction_metric` reads get their rows from `pipeline`, the way `pipeline` already hands `synthesis` its inputs.
- **One schema file per module** (`schema/ledger.sql` and so on, via `spring.sql.init.schema-locations`), which makes ADR-049's text true. Add a table-ownership guard test that fails when a module's SQL names another module's table. Amend ADR-049.
- **One schema-version declaration per module** instead of six classes. The guard must still fail while the context is being built. `SchemaVersionDeclarationTest` changes with it (`analyst`).

### Wave 5 — Split `Deliverable`

Package-private collaborators inside `synthesis`, each with the rule it carries:

| Collaborator | Carries |
|---|---|
| `MarkdownSurroundings` | one table-driven escaper for heading, cell and link text |
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

- **Delete dead code:** the LLM chunking fallback and its interface (inject the windowed one directly), `Ledger.walkFinished`, `ContentIdentity.representativeFor`, `RelevanceFloor.State.removesAnything`, the test-only constructor, and `RedundancyResolutionTasklet.STEP`, which only aliases `RedundancyRun.STAGE`.
- **Pom:** each removal cites the decision that stops requiring it (ADR-046). Candidates: `lombok`, the actuator starters, `spring-boot-starter-batch-test`, and the OpenAI starter, after checking it is not ADR-072's reference model in waiting.
- **Chroma:** `VectorStoreConfiguration`, the Chroma starter and its test container go under an ADR superseding ADR-142 (nothing reads the projection; re-add it when the first reader is built).
- **Duplicated helpers:** remove them only inside modules another wave is already re-minting, so no wave re-mints a stage just to delete a copy.
- **Javadoc policy:** a class cites the ADR it implements and states its own contract. It does not restate the ADR's reasoning. Correct the stale javadocs in D7.
- **Agent files:** move `AGENTS.md`'s defect history (line 23) and status narrative (line 21) out, keeping every sentence `docs/check-claims.mjs` matches. Fix the stale agent definitions.

### Wave 7 — Take `pipeline` out of run identity (deferred)

Once Wave 2 leaves `pipeline` holding no rule that shapes output, drop it from every stage's module list (amend ADR-058). A wiring change then stops re-minting stages 3 to 6b. Doing it before Wave 2 would make invalidation too lazy, which is exactly the failure ADR-058 exists to prevent.

---

## 6. Not doing, and why

| Tempting move | Why not |
|---|---|
| Replace Spring Batch with plain loops | Saves about 600 lines. It costs a rewrite of the three chunk steps and the exit mapping, ADR-140's read-ahead, 24 tests, and amendments to ADR-036, 047, 131, 140 and 141. The resourceless repository already makes it nearly free. |
| Derive `NextAction` and every gate from one ordered table | The orders differ. `NextAction` names values in README order (seed folder, boilerplate floor, embedding model). The job meets them in cascade order (floor, then seed, then model). One table would change the sentence an operator reads. `UsableSeedGate` is an in-job flag, not a profile value, so it is not a gate in `CONTEXT.md`'s sense; rename it instead. |
| One SHA-256 helper for every module | The copies produce persisted keys in modules with different implementation versions. Merging them re-mints stages for no behavioural gain. Do it only inside Wave 6's rule. |
| De-duplicate the 16 `discardForRun` methods | Each is one to three lines against its own module's table, which ADR-041 wants owned there. |
| Move `Ledger`'s walk half into `corpus` | ADR-041 puts occurrence identity in `ledger`. Split it inside `ledger` instead (Wave 4). |
| Stage-shaped packages under `pipeline` | Rejected in `docs/architecture.md:169` for a reason that still holds. |

---

## 7. What an operator sees, and how to deliver

**A wave that re-mints costs an operator mid-campaign:**

- Stages 3 and 4 redo their work from the database alone.
- Docling conversions and embeddings are cache hits.
- Clustering and 6a recompute, so a new arrangement is minted and has to be approved again.
- 6b calls the generation model once per cluster again and writes a second deliverable tree beside the first.

After Wave 0b that is the whole cost. Before it, the operator is stranded instead (D5).

**Delivery:**

- Merge the waves in order.
- The cost is paid per build, not per commit, so an operator mid-campaign should take one build that carries several waves, at a corpus boundary if possible.
- Wave 3 needs a fresh working directory whenever it lands.

---

## 8. Open decisions

Each belongs in the ADR of the wave that meets it. They are listed here so no wave discovers them late.

1. **Wave 0b:** exactly how the upstream lookup prefers the current build, and what it says when the current build has no run yet.
2. **Wave 1:** whether the three "cannot name one run" exceptions become one.
3. **Wave 4:** whether a snapshot of survivors is safe at every current call site, measured rather than argued.
4. **Wave 4:** per-module schema files. Accept the one-time move, or keep one file and correct the documentation instead.
5. **Wave 6:** which decision, if any, still requires each of the pom entries in D6, and whether the OpenAI starter is ADR-072's reference model.
6. **Wave 6:** the javadoc policy above, recorded once so later changes follow it.

---

## 9. Tracker shape

- **One bug now:** the upgrade trap (D5), filed as [#290](https://github.com/algernon28/vespera/issues/290). It is a defect in what ships and should not wait for a map.
- **Then a wayfinder map, "Architecture simplification":** one child ticket per wave, 0a to 6, with 7 parked. Each ticket's resolution comment is its spec, as usual.
- **Relation to open work:**
  - **#286** (PDF pictures) is independent.
  - **#287** (blank `content_hash`, ADR-151) *is* Wave 3.
  - **#289** (the relevance report and an unopenable file, ADR-152) is settled on its own terms first. Wave 3 later removes the re-reads that ADR-152 decides to fail on.
  - A seed-side twin of #289 is [#291](https://github.com/algernon28/vespera/issues/291): a seed file that cannot be opened fails seed extraction instead of becoming an unusable seed.

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
| "Cannot arise today" | `docs/adr/0099-*.md:63` |
| Approval by id prefix | `pipeline/ArrangementGate.java:70-79` |
| Failure classification in `pipeline` | `pipeline/ExtractionItemProcessor.java:267-376` |
| Extractor identity in `pipeline` | `pipeline/ExtractionJobConfiguration.java:287-297` |
| 6b's loop in `pipeline` | `pipeline/GenerationTasklet.java:212-339` |
| Hand-written profile keys | `pipeline/GenerationTasklet.java:460-471` |
| Label rule in `pipeline` | `pipeline/ArrangementTasklet.java:235-264` |
| Duplicate grouping in `pipeline` | `pipeline/ByteLevelReductionTasklet.java:218-278` |
| The archive re-read | `DoclingExtractor.java:65`; the eight sites in D3 |
| Seed-side first read | `pipeline/SeedExtractionItemProcessor.java:92` |
| Batch type in `ledger` | `ledger/Ledger.java:529` |
| Cross-ownership SQL | `ledger/Ledger.java:188`; `similarity/RedundancyResolution.java:205`; `embedding/SeedCorpusComparison.java:347` |
| Test import lists | the 24 `pipeline/*InvocationTest.java` and sibling whole-job tests, 88 classes each |
| Stage-shaped packages rejected | `docs/architecture.md:169` |

## Appendix B — How the figures were taken

The line and file counts are `wc -l` over `src/main/java` and `src/test/java` at `6f16ccc`. Comment lines are lines whose first non-blank characters are `*`, `/*` or `//`. Call-site counts are `grep -c` over `pipeline`. Import-list sizes count `.class` literals inside each test's `@Import({...})`. The upgrade trap was traced through the code named in D5; it has not been executed.
