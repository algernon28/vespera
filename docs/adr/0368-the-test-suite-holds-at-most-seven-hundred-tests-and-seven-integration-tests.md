# ADR-368 — The test suite holds at most seven hundred tests, and seven integration tests

- **Date**: 2026-11-08
- **Status**: accepted on 2026-11-08.
- **Settles**: the size of the Java test suite, which the operator asked to be cut to no more than six or seven hundred tests, its integration tests to the fewest the build needs.
- **Amends**: every record from ADR-050 on whose Tests section names a test this cut deleted; the record keeps the name, and `docs/retired-tests.txt` says it is gone.
- **Keeps**: [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md)'s four conventions, and the seven standards a test is held to, for every test that stays; [ADR-320](0320-a-publish-sends-each-page-whole-its-files-before-its-body-before-it-creates-the-next-and-says-how-far-it-has-got.md) §6, the publish's tests tagged `confluence` and skipped by CI.

## Context

The suite had grown to 722 test files and 2,856 methods annotated as tests, most decisions held by a unit test and again by an invocation test that starts a Spring context, and many by several of each. Surefire ran about 3,250 executions, counting each parameterized case, and failsafe 25 integration test classes of about 95 more. A run of the unit tests took the better part of an hour on a cloud machine, and was killed for memory before it finished when anything else ran beside it.

## Decision

### 1. The ceiling

The Java suite under `src/test/java` holds at most 700 test methods, each annotated `@Test`, `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory` counted once. A test added past it is paid for by one taken out. The node tests under `src/test/hooks`, `src/test/docs` and `src/test/website` are not counted: they hold the guard and the documentation scripts, not the application.

### 2. What was cut, and in what order

Each module's tests were cut to a share of the ceiling, in this order:

1. A claim held twice: a unit test and an invocation test holding the same rule, near-identical variants of one test, a claim also held by a neighbour.
2. Of two tests holding one decision, the dearer: an invocation test, which starts a Spring context, gave way to a plain unit test.
3. Then the least valuable, until the share was met. What stays holds verdict logic, the minting of run identities, the ledger's integrity, the refusals and failure paths that protect the operator's data, the census's identity rules on NTFS, and the architecture rules `AGENTS.md` names.

A parameterized test keeps one or two representative cases: folding several tests into one parameterized test of as many cases saves nothing.

### 3. Seven integration tests

Seven stay: `CliExitIT` and `PackagedJarIT`, which need only failsafe's phase; `VesperaApplicationIT`, `DoclingClientIT`, `OllamaClientIT` and `ClusterSynthesisIT`, which need a Docker daemon and hold the application's contract with the tools it runs; and `PublishUpdatesOneTreeInPlaceIT`, tagged `confluence`, which holds the publish against its in-process stub. The eighteen others are deleted; what they held of the jar's locks, closing line and refusals is held, where it is still held, by the unit and invocation tests beside them.

### 4. The records keep their names

A record's Tests section is not rewritten: the names it holds are what held it when it was accepted. `docs/retired-tests.txt` lists, one `ADR-NNN Name` a line, each name a record holds that this cut deleted, and `docs/check-claims.mjs` reads a listed name as retired rather than missing. A line naming a test that exists, or a name no record holds, fails the check, so the list cannot hide a name that comes back or a typo made later.

### 5. Run ids

No run id moves: no class under `src/main` changes.

## Alternatives refused

- **Rewriting each record's Tests section.** Over two hundred records name a deleted test; editing them all would rewrite accepted records for a reason none of them decides.
- **Counting executions rather than methods.** Surefire's count needs a build of the better part of an hour; the claims check reads the sources in a second. The methods are the floor of the executions, and the measured executions are stated below.

## Consequences

- After the cut, surefire ran 604 executions, none failing, 6 skipped on assumptions this machine cannot meet, and failsafe holds 13 tests in the seven classes: 617 in all, from about 3,350.
- Many decisions are held by one test where they were held by several, and some by none: what a record names in `docs/retired-tests.txt` is a claim no test holds now.

## What this does not decide

- Which decision a test added later holds; it is held to the seven standards as before.
- The node tests' size.

## Tests

- `docs/check-claims.mjs` holds §1, failing when the methods annotated as tests under `src/test/java` pass 700, and §4, reading `docs/retired-tests.txt`.
- `docs/check-claims.mjs` holds §3 through `AGENTS.md`'s list of the integration tests, which it compares with the `*IT` classes on disk.
