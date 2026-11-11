# ADR-368 — The test suite holds at most seven hundred tests, and seven integration tests

- **Date**: 2026-11-08
- **Status**: accepted on 2026-11-08.
- **Settles**: the size of the Java test suite, which the operator asked to be cut to no more than six or seven hundred tests, its integration tests to the fewest the build needs.
- **Amends**: [ADR-337](0337-every-claim-can-fail-a-decision-is-held-once-and-a-records-tests-name-tests-that-exist.md) §5, and the rule of `docs/adr/README.md` it cites, for the records up to ADR-367: a record that names a test this decision removed, in its `## Tests` section or anywhere else in its text, keeps the name; a name in its `## Tests` section is listed in `docs/retired-tests.txt` (§4). From ADR-368 on, ADR-337 §5 stands as written.
- **Keeps**: [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md)'s four conventions, and the seven standards a test is held to, for every test that stays; [ADR-320](0320-a-publish-sends-each-page-whole-its-files-before-its-body-before-it-creates-the-next-and-says-how-far-it-has-got.md) §6, the publish's tests tagged `confluence` and skipped by CI.

## Context

The suite had grown to 722 test files and 2,856 methods annotated as tests, most decisions held by a unit test and again by an invocation test that starts a Spring context, and many by several of each. Surefire ran about 3,250 executions, counting each parameterized case, and failsafe 25 integration test classes of about 95 more. A run of the unit tests takes the better part of an hour on a cloud machine.

## Decision

### 1. The ceiling

The Java suite under `src/test/java` holds at most 700 test methods, each annotated `@Test`, `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory` counted once. A test added past it is paid for by one taken out. The node tests under `src/test/hooks`, `src/test/docs` and `src/test/website` are not counted: they hold the guard and the documentation scripts, not the application.

### 2. Which test goes

When a test is added past the ceiling, or the suite is cut to it, a test goes by this order:

1. A claim held twice: a unit test and an invocation test holding the same rule, near-identical variants of one test, a claim also held by a neighbour.
2. Of two tests holding one decision, the dearer: an invocation test, which starts a Spring context, gives way to a plain unit test.
3. Then the least valuable. What stays holds verdict logic, the minting of run identities, the ledger's integrity, the refusals and failure paths that protect the operator's data, the census's identity rules on NTFS, the architecture rules `AGENTS.md` names, the website's sandbox and the publish's host, token and foreign pages, and each copy the control panel holds as text of a table of the jar's, held to the jar's by a test (ADR-361 §2, ADR-344 §6).

A parameterized test keeps one or two representative cases: folding several tests into one parameterized test of as many cases saves nothing.

### 3. Seven integration tests

Seven stay: `CliExitIT` and `PackagedJarIT`, which need only failsafe's phase; `VesperaApplicationIT`, `DoclingClientIT`, `OllamaClientIT` and `ClusterSynthesisIT`, which need a Docker daemon and hold the application's contract with the tools it runs; and `PublishUpdatesOneTreeInPlaceIT`, tagged `confluence`, which holds the publish against its in-process stub. There are no others; what an integration test held of the jar's locks, closing line and refusals is held, where it is held, by the unit and invocation tests beside them.

### 4. The records keep their names

A record up to ADR-367 is not rewritten: the names it holds are what held it when it was accepted. `docs/retired-tests.txt` lists, one `ADR-NNN Name` a line, each name a record's `## Tests` section holds that this decision removed, and `docs/check-claims.mjs` reads a listed name as retired rather than missing. A line naming a test that exists, or a name no record holds, fails the check, so the list cannot hide a name that comes back or a typo made later; and a line for ADR-368 or a later record fails it, since a later record's section is corrected in place by ADR-337 §5.

### 5. Run ids

No run id moves: no class under `src/main` changes.

## Alternatives refused

- **Rewriting each record's Tests section.** Over two hundred records name a deleted test; editing them all would rewrite accepted records for a reason none of them decides.
- **Counting executions rather than methods.** Surefire's count needs a build of the better part of an hour; the claims check reads the sources in a second. The methods are the floor of the executions, and the measured executions are stated below.

## Consequences

- The suite holds 667 methods annotated as tests, from 2,856; failsafe's seven classes hold 24.
- Many decisions are held by one test where they were held by several, and some by none: what a record names in `docs/retired-tests.txt` is a claim no test holds now.
- A name a record holds outside its `## Tests` section, in its Decision or a Keeps line, is not read by the claims check, and may name a removed test.

## What this does not decide

- Which decision a test added later holds; it is held to the seven standards as before.
- The node tests' size.

## Tests

- `docs/check-claims.mjs` holds §1, warning, as ADR-369 §2 amends it, and not failing, when the methods annotated as tests under `src/test/java` pass 700, and §4, reading `docs/retired-tests.txt` and refusing a line of ADR-368 or later; `src/test/docs/check-claims.test.mjs` holds both on fixtures: 700 methods pass and 701 warn, a listed name that exists or that no record holds fails, a listed name is not reported missing, and a line of ADR-368 is refused.
- `docs/check-claims.mjs` holds §3 through `AGENTS.md`'s list of the integration tests, which it compares with the `*IT` classes on disk.
