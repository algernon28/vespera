# ADR-153 — The whole-job tests share one slice, and a stage's configuration class stops being their seam

- **Date**: 2026-09-25
- **Status**: accepted
- **Amends**: [ADR-131](0131-one-module-builds-every-plain-tasklet-step.md), on one point. Its Decision keeps the per-stage `*JobConfiguration` classes for two reasons, and the first of them — "they are the seam the slice tests import" — no longer holds. The second — "the place each stage's step is named" — is not touched here (§4).
- **Keeps**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) (what a run's id is derived from, unchanged), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) (finished work is recognised by its id), [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md) (the test conventions the new tests follow), and [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) (no test-support package becomes a module).
- **Settles** [#294](https://github.com/algernon28/vespera/issues/294).

## Context

**1. What the whole-job tests carried.** Twenty-four test classes in `pipeline` run the whole job, from the command line down to the rows, in a slice that needs neither Chroma nor a live Ollama nor a Docling sidecar. Measured on `main` at `6f16ccc`, every one of them opened with the same five class-level annotations — `@JdbcTest`, `@AutoConfigureTestDatabase(replace = NONE)`, `@ActiveProfiles("test")`, `@Transactional(propagation = NOT_SUPPORTED)`, `@ImportAutoConfiguration(BatchAutoConfiguration.class)` — and then an `@Import` list of 85 to 89 classes. Of those, 84 were named by all 24, and three more (`HybridChunkerBeans`, `ExtractionMetrics`, `LanguageDetection`) by 23 of them. What actually varied from test to test was one of five extraction doubles, `ClusterFaults` in four tests, and a probe in two. One list, `RunUpstreamChainTest`'s, named `SeedMeasurementRun` twice without anyone noticing.

So moving, renaming, adding or deleting one class the job wires meant the same edit in 24 files, together with each file's Java imports: about 3,000 lines that said the same thing 24 times.

**2. Why ADR-131 leaned on that.** ADR-131 folded the construction of every plain tasklet step into `TaskletSteps` and deliberately kept the ten per-stage configuration classes. The first of its two reasons was the test side: "Every whole-context slice test imports each `*JobConfiguration` by name … so the class is the seam through which a stage's step reaches a test's slice. Deleting the classes would force every test to change." That was true while the list was written out 24 times. It is a fact about how the tests were written, not about the job.

**3. What nothing pinned.** A run's id is derived from its stage, its `implementation_version`, its `config_consumed`, its walk and its upstream runs (ADR-048, ADR-058), and a re-invocation recognises finished work only because the same inputs derive the same id (ADR-115). The text of `config_consumed` is built by each stage's own private record and serialised by Jackson. The order of the modules in `implementation_version` is the order each stage passes them to `ImplementationVersions.of`, which joins their SHAs with `+`. No test asserted either as a whole. A refactor that renamed one record field, reordered two, swapped the record for a map, or passed the modules in another order would re-mint every run of that stage for work already done, and every behaviour test would still pass, because the work would still be done correctly, just again.

The module order cannot be checked against the build's real answer. On the tree measured, `extraction`, `pipeline` and `synthesis` all carried the same SHA (`6f16ccc`), and `corpus` and `ledger` shared another. Read back from those, `extraction+pipeline` and `pipeline+extraction` are the same string.

**4. A premise that turned out false.** The ticket expected a class named both in a shared annotation's `@Import` and in a test's own to fail the context loudly, because bean overriding is off. Measured on 2026-09-25 by adding `Ledger` and `CensusTasklet` to `CensusInvocationTest`'s own list on top of the shared one: all seven of its tests still passed. Spring gathers every `@Import` on a class and its meta-annotations into one set before registering anything, so a duplicate is registered once and nothing is said. That is also why `RunUpstreamChainTest`'s double `SeedMeasurementRun` had never failed.

## Decision

**1. One slice annotation carries what every whole-job test shares.** `@CascadeSliceTest`, in `pipeline`'s test package, carries the five class-level annotations and an `@Import` of the 87 classes above: the 84 all 24 named, plus the three 23 of them named. It lives beside the tests because most of those classes are package-private to `pipeline`, and because a test-support package of its own would read as a ninth module to `ApplicationModules` (the reason `TestSteps` and `Adr` sit in the root package).

**2. Each whole-job test names only what makes it that test.** That is its extraction double (`StubbedExtractionBeans`, `SeedScriptedExtractionBeans`, `PictureScriptedExtractionBeans`, `CountingDoclingBeans` or `ExtractionBeans`), and, where it has them, `ClusterFaults` or its probe (`StepCompletionOrderProbe`, `DrainThreadProbe`). `ClusterFaults` stays with the four tests that use it, because the annotation carries only what all of them name. The annotations a test alone carries (`@DirtiesContext` on `ExtractionStepTest`, `@ExtendWith(OutputCaptureExtension.class)` on `ClosingLineInvocationTest`) stay on the test. No test method, claim, label or link changed. The suite ran 709 tests with 0 failures, 0 errors and 9 skipped before the change and after it, class by class.

**3. `UnconfiguredRootTest` is on the annotation too.** It was the one list that differed. It named the real `ExtractionBeans` and none of `HybridChunkerBeans`, `ExtractionMetrics` or `LanguageDetection`, because its invocation refuses before any stage runs. On the annotation, those three are constructed and never called there, and its claims are about the refusal, not about what is wired. Keeping it explicit would have kept one full copy of the list alive for no claim it makes.

**4. ADR-131's first reason is withdrawn, and its second stands until a record weighs it.** A stage's configuration class is no longer the seam the tests import: the tests name `@CascadeSliceTest`, and the annotation is the one place the job's configuration classes are named on the test side. Folding, renaming or merging them is now one edit to the annotation rather than 24. This record does not decide whether they *should* be folded. ADR-131's second reason, that each class is the place a stage's step is named, is a fact about `src/main`, and whoever proposes folding them owes a record that weighs it.

**5. A duplicate is caught by a test, since Spring does not catch it.** `CascadeSliceImportsTest` finds every class in the package carrying `@CascadeSliceTest` and fails, naming the test and the class, if its own `@Import` names anything the annotation already imports. A second copy of the wiring is the thing the annotation exists to remove, and Context §4 is why the context cannot be relied on to say so.

**6. What each kind of run is derived from is pinned as text.** `RunIdentityGoldenTest` runs the whole job once over one corpus document and one seed, the way an operator reaches generation: a first invocation that stops at the arrangement gate, then the arrangement approved and a generation model named, then a second invocation. It then asserts, for each of the eight kinds of run — byte-level reduction, extraction, content census, content redundancy, seed measurement, embedding scoring, arrangement and generation:

- its `config_consumed`, exactly, as a string: the field names, their order, and how each value is written, `null` and `1.0` included;
- its `implementation_version`, exactly, as the module names in order, joined by the real `ImplementationVersions.of`.

The expected text is written out literally, not read from the stage's constants or produced by calling its own `configConsumed`, which would move with the code and pin nothing. The only parts filled in at run time are those that cannot be literal: the corpus root and seed folder, which are temporary directories, and the upstream run ids, each read back from the row it names. The stage names in the queries are literal too, since those are persisted as well (ADR-116).

The module names are made visible by `ModuleNamedVersionsBeans`, in `ledger`'s test package, which records each module's name as its version and is `@Primary` over the real one in that test's context alone. Context §3 is why the build's real SHAs cannot show an order.

**7. Nothing under `src/main` moved.** No production class, no configuration and no build file changed. The implementation version reads only `src/main/java/io/algernon/vespera/<module>` (`.mvn/scripts/implementation-versions.groovy`), so no run is re-minted by this change.

## Consequences

**The 24 tests lost 3,035 lines net** (50 added, 3,085 removed), the class-level `@Import` block and the Java imports it needed. The annotation is 195 lines, most of them its javadoc and one line per class, grouped by stage.

**A change meant to re-mint a stage now edits a literal in the same change.** Bumping the Docling image in `application.yaml` is one: stage 2's identity carries it (ADR-147), so the extraction text in `RunIdentityGoldenTest` names it, and the edit makes the re-mint something a reviewer sees rather than something an operator finds out about. A change not meant to re-mint anything fails there instead of passing silently.

**What the golden tests do not cover.** They pin the text a stage writes, not the id derived from it. `RunId`'s derivation has its own tests. They run one fixture, in which neither the degenerate-output confidence floor nor the relevance score floor is set, so each is pinned as `null` and the way a set one is written is not. They do not cover a second build over the same database with a different implementation version. That belongs to [#290](https://github.com/algernon28/vespera/issues/290).

**The next move of a job class is one edit.** Whoever next folds or renames a configuration class edits `@CascadeSliceTest` once, and the golden tests say whether the refactor moved any run's identity.

**Reversal is cheap.** Inlining the annotation's list back into each test restores the previous shape. Nothing outside `src/test` would notice.
