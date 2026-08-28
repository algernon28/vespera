# ADR-052 — The test report is written for a reader outside the project

- **Date**: 2026-08-28
- **Status**: accepted

## Context

Design is far ahead of code here, and the tests are consequently doing a job they usually do not: they are most of the evidence that a recorded decision is real. ADR-051 fixes how a file occurrence's path identifies it, ADR-050 grants the pipeline exclusive access to the corpus, ADR-040 and ADR-041 shape the modules — and for each of those, the only thing that demonstrates the decision holds is a test. That makes the test report a document someone reads to find out where the project stands, not a build by-product consulted when the build is red.

Read as such, the report the build produced was useless. The tree folded on `parentSuite`/`suite`, which are the package and class names, so its top level said `io.algernon.vespera.corpus` — a fact about source layout and about nothing a reader wanted to know. Beneath a test, `allure-assertj` derived the steps from the AssertJ call chain, producing a parent step named `assert 1234` with `has size 1` under it: the shape says what executed rather than what was being claimed, and the numbers in it are the test's own data with no indication of where they came from. Failure messages had the same defect from the other end — `expected: 1235 but was: 1234` is a comparison with the reason for it stripped off, and it is the message that reaches the console, CI and the report's failure banner.

Two other things were unclear enough to be worth recording, because both look like mistakes:

- **Allure is two products on two version lines.** The adapter is `allure-java` — the libraries the tests are woven with, which write `target/allure-results` — and its line is 2.x with no 3.x release. The generator is the TypeScript program that turns those results into a page, and it is the product now at 3.x. Allure 3 reads Allure 2 results; its reader for them ships as part of the generator. A 2.x adapter paired with a 3.x generator is therefore the documented arrangement, not a version skew someone forgot to fix.
- **`allure-maven` renders a Maven site.** Its report goals extend `AbstractMavenReport` and bind to the `site` lifecycle, so generating the report also renders a Doxia site wrapper and the Fluido skin beside it, and emits a warning about a missing parent site URL. None of that can be switched off while the report goal is in use.

## Decision

**Every assertion sits inside `TestSteps.claim(...)`**, which names the report step itself rather than deriving it. The claim is the only place the wording lives, and it must explain every number it mentions — the test's magic numbers become named constants and the claim says what they are, so a step reads *the size recorded is the 1234 bytes the test wrote to `sized.bin`* rather than *has size 1*. On failure the claim is prepended to the message, inside the step, so that the step's own error also carries it; where AssertJ threw an opentest4j `AssertionFailedError` the expected/actual pair is carried across, so an IDE can still offer its comparison. `allure-assertj` is deliberately not a dependency, because deriving the step from the call chain is exactly the behaviour being rejected.

**Every test class carries `@Epic` and `@Feature`, every test `@Story` and `@DisplayName`**, and the report tree groups on those three labels instead of the package. The failure categories in `allurerc.mjs` match on the same `@Feature` labels, so a failure is filed under the capability that broke rather than the exception that surfaced. Skipped cases get a category of their own, because AGENTS.md already holds that a skipped test is not a passing test and a green run that quietly skipped its symlink cases proves less than it appears to.

**Report-visible text stands on its own.** Display names, labels, claims and category names carry no ADR id and no phrase that needs `CONTEXT.md` to parse, because the reader they are written for has neither. The decision travels as a link instead: `@Link(type = "adr")` names the ADR a test exists because of, resolved through `Adr`, which is an id-to-URL map rather than a pattern because an ADR file is `NNNN-its-title.md` and the id alone does not give the path. `@Issue` names the wayfinder child issue, resolved by `allure.link.issue.pattern`. A test with no decision behind it is the thing to notice.

**Allure's own configuration lives in `allurerc.mjs`**, passed to `allure-maven` through `configPath`. The plugin merges that file underneath its own generated config and overrides exactly three keys — the report name, the output directory, and Awesome's `singleFile` — so those three stay in the pom and everything else is described where Allure reads it. Both version lines are pinned as properties with the distinction above written beside them, and `allure-bom` is imported so no Allure dependency carries a version of its own.

**One self-contained page per run, in `reports/`, named `report_<datetime>.html`.** `singleFile` makes the generator emit a single `index.html` with no external references; a step after it copies that file out of `target/`, stamped with the local time, so the artifact worth opening or sending survives a `clean` and says which run produced it. Runs never overwrite each other. The directory is gitignored: a build output worth keeping on disk is not a build output worth committing.

## Consequences

**A claim is a sentence someone has to write.** There is no deriving it from the assertion, which is the point and also the cost: an assertion added without a claim to go with it reports as nothing useful, and a claim that quietly stops describing its assertion is a lie the compiler cannot catch. The convention is recorded in AGENTS.md because nothing in the code enforces it.

**An unlabelled test falls out of the report twice.** It lands nowhere in a tree that groups on epic, feature and story, and it matches no category, since the categories key on `@Feature`. Adding a test class means adding its labels.

**Renaming a `@Feature` silently unfiles its failures.** The categories in `allurerc.mjs` match label values as strings, so the two have to be changed together; a mismatch shows up only as failures that stop appearing under the category that named them.

**`TestSteps` and `Adr` live in the root package**, not in a test-support package of their own. `ApplicationModules` would read such a package as a tenth capability module, and `ModuleBoundariesTest` — which exists to defend ADR-040 — would fail on it. This is a real constraint on where test support can go, not a stylistic choice.

**The Doxia site wrapper and its warning are accepted.** The alternative is dropping `allure-maven` and driving the generator directly, which trades a warning and some ignored files under `target/` for a Node toolchain on the critical path of a Java build. The wrapper costs nothing once it lands in `target/`, and the warning is documented here so the next person does not spend the afternoon on it.

**The report title is not configurable.** `AllureBaseMojo.getName()` returns `"Allure"` unconditionally, and Awesome's own `reportName` option is overridden by the config the plugin generates. Setting it in `allurerc.mjs` is provably ignored, so it is not set there.

**`reports/` grows without bound.** Nothing prunes it, because deleting a report is a decision about which run stopped mattering, and the stamped name exists precisely so that no run's evidence is destroyed by the next one.

## Amends

Applies [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) to the test toolchain: the Allure dependencies, the two version lines, the weaving agent and the copy step are all in the pom because this record requires them, and `allure-assertj` is absent because this record rejects it. Serves [ADR-006](0006-census-measure-before-judging.md), [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md), [ADR-050](0050-the-pipeline-has-exclusive-access-to-the-corpus.md) and [ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md) by making the tests that defend them legible as evidence.
