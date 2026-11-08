# ADR-337 — Every claim can fail, a decision is held once, and a record's Tests name tests that exist

> **Partly amended — see [ADR-368](0368-the-test-suite-holds-at-most-seven-hundred-tests-and-seven-integration-tests.md).** Of §5: a record up to ADR-367 that names a test ADR-368 removed keeps the name, which `docs/retired-tests.txt` lists as retired and the claims check reads so; from ADR-368 on, a record's section is corrected in place as §5 says.

- **Date**: 2026-11-03
- **Status**: accepted on 2026-11-03.
- **Keeps**: [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md): every assertion stays inside `TestSteps.claim`, and every test keeps its `@Story` and `@DisplayName`; a merge never takes a node out of the report's tree (§3).
- **Keeps**: [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) §12: the suite runs one test at a time in one JVM. Nothing here adds `parallel`, `forkCount`, `reuseForks` or a `junit-platform.properties` (§4).
- **Settles**: [#679](https://github.com/algernon28/vespera/issues/679), on the wayfinder map [#677](https://github.com/algernon28/vespera/issues/677).

## Context

`AGENTS.md` holds every test to seven standards, the first being that every claim has a change to the code it holds that turns it red. They are asked of a test when it is written. Nothing asks them of the 2,875 test methods already there, and a read of all of them against those standards, set down in [`docs/research/test-suite-findings.md`](../research/test-suite-findings.md), found:

- **About 113 claims that cannot fail.**
  - About 90 are implied by a claim beside them, e.g. a `doesNotContain` after a `containsExactly` of the same list, or a size after the exact list.
  - The rest read the test's own fixture or constant, or a helper of the tests rather than of the product.
  - About 20 leave the test green while the thing it names is broken.
- **About 112 claims far weaker than their words.**
- **About 300 to 400 methods that hold a claim another test holds,** or that are one table written out case by case. Only 37 of the 2,875 are parameterized.
- **A full-run class that starts the jar's command line once for each test.** Most of its tests rebuild the same corpus and the same profile with helpers copied from class to class: the boilerplate floor of 1.0 is declared in 94 files.
- **A record's `## Tests` section naming tests that are gone.** 147 records carry one, and nothing reads them against the tests: ADR-094 names two methods of `BrokenCheckTest` that no longer exist.

Running each scenario of a full-run class once was measured on three classes. One invocation of the command line costs about 0.2 to 0.5 seconds there; the Spring context the class starts costs most of the rest. Of the 530 seconds the unit tests take, the 110 full-run classes take 188, and running each scenario once across all of them would save well under a minute.

## Decision

### 1. A claim implied by another claim of the same test is a defect, and is removed

The first standard holds of the tests already written, not only of the next one. A claim that no change to the code can turn red while the claims beside it pass is removed. A method left holding no claim is removed with it.

A claim that cannot fail because it reads the test's own fixture, constant or helper is removed in the same way. A precondition of the fixture that the test needs is asserted by assumption, not claimed.

A claim that can fail but leaves the thing its words name unheld is rewritten until the change that breaks that thing turns it red. Where that is not worth its cost, the words are changed to say what it holds.

### 2. An expected value is written out, not worked out by the rule under test

An expected value computed by the same rule the production code applies holds nothing that rule gets wrong. The expected value is a literal, and the claim says where the literal comes from.

### 3. Tests merge only where they share a `@Story`

Two tests holding one claim are one test. A table of cases written out method by method becomes one `@ParameterizedTest`. A merge keeps:

1. **The report's tree.** Only tests that share a `@Story` merge, since each case of a `@ParameterizedTest` carries the method's one `@Story` and `@DisplayName`.
2. **Plain case names.** `@ParameterizedTest(name = "{0}")`, each case named by `Named.of` in a plain phrase with no record's number in it (ADR-052).
3. **Every link.** Each `@Link` and `@Issue` the merged methods carried goes onto the merged one.
4. **The trail.** For each method removed, the pull request names the test that still holds its claim.

### 4. A full-run class shares its setup; running each scenario once is a choice, not a rule

The setup the full-run classes copy lives once, package-private in `src/test/java/io/algernon/vespera/pipeline/`:

- `EveryGateOpen`, the profile that opens every gate up to the approval;
- `RunsOver`, the reads of a root's runs;
- `Once`, the work a class shares, done the first time a test asks and its value or failure handed to every later test.

A class uses `Once` where its tests read one scenario and the claims are clearer for it, never for time alone. A class running a scenario once fills it from a test's body or `@BeforeEach`, never from `@TestInstance(PER_CLASS)` with `@BeforeAll`, because the class's context is built before its static `@TempDir` is set.

What the scenario leaves in the working directory, the scripted beans or the captured log is copied out as soon as it ends, since a later test of the class writes there again. A claim is made in a test, never inside the shared work.

### 5. A record's `## Tests` section names only tests that exist, and the claims check holds it

`docs/check-claims.mjs` reads every record's `## Tests` section and fails where a test it names does not exist under `src/test/java`. It reads two forms:

- **Qualified names**, `Class.method` in backticks, with or without the package's tail, and a bare `Class` in backticks where the section names a class.
- **Bare method names** in backticks under a bullet that names their class, as [ADR-214](0214-chroma-is-removed-and-vectors-live-in-sqlite-alone.md) writes them.

A record whose section names a test that is renamed, merged or removed is corrected in place, in the change that renames it, citing the ticket (`docs/adr/README.md`). A reconstituted record from ADR-001 to ADR-049 carries no such section and is not read.

## Consequences

- **The suite shrinks without losing a decision.** The methods §1 and §3 remove are claims some other test already holds, or claims that hold nothing; the pull requests' trail (§3.4) is how that is checked.
- **A merge can still lose a node** where a merged method's `@Story` was its own. §3.1 forbids that merge, so the cases stay as methods.
- **The claims check gains a failure mode.** A record that names a test by a name the test no longer has fails the docs job until the record is corrected.
- **No run id moves.** Nothing here changes the jar.

## Alternatives refused

- **Mark dead claims and keep them as statements of intent.** A claim the report shows as a passing step reads as something proved; one that cannot fail misleads exactly there.
- **Merge any tests of the same shape.** It takes nodes out of the report's tree, where a failure is found by its `@Feature` and `@Story` (ADR-052).
- **Run every full-run class's scenarios once, as a rule.** The measured saving is well under a minute of the unit tests' time, and the pattern costs each class a snapshot of everything a later test overwrites.
- **A mutation-testing plugin to find claims that cannot fail.** It is a dependency no record requires (ADR-046), and its report would hold no claim's words; the red is shown by hand for each claim rewritten.
- **Check only `Class.method` in the Tests sections.** ADR-214 names its methods bare under a bullet for the class, and a check that skips that form leaves those names unchecked.

## Tests

- `pipeline.GenerationIdentityInvocationTest`, `pipeline.StageTwoRecordsItsCacheKeyInvocationTest` and `pipeline.RelevanceReportInvocationTest` run their shared scenarios through `Once`, `EveryGateOpen` and `RunsOver`, every claim each held before kept (§4).
- `docs/check-claims.mjs`'s check of the Tests sections, built by [#684](https://github.com/algernon28/vespera/issues/684), holds §5.
- §1 to §3 are rules for changing tests, held by the claims those changes leave and by each pull request's trail; no test of their own holds them.

## What this does not decide

- Which claims are removed, rewritten or merged, one by one: that is for the tickets of #677 to decide against this record.
- Whether the remaining full-run classes take `Once`: each class, as its tests are next changed (§4).
