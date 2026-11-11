# ADR-369 — Seven hundred tests is a reference for the suite's size, and not a ceiling

- **Date**: 2026-11-11
- **Status**: accepted on 2026-11-11.
- **Decided**: by the operator: that ADR-368's 700 is a reference that keeps the number of tests in check, and not a hard limit.
- **Amends**: [ADR-368](0368-the-test-suite-holds-at-most-seven-hundred-tests-and-seven-integration-tests.md) §1, its sentence that a test added past 700 is paid for by one taken out, and §2's first clause, which made a test added past it a reason for one to go; and its Tests section's first bullet, corrected in place by ADR-337 §5.
- **Keeps**: ADR-368 §2's order, §3, §4 and §5; ADR-052's conventions and the seven standards a test is held to.

## Context

ADR-368 cut the Java suite to at most 700 methods annotated as tests, and `docs/check-claims.mjs` failed once the count passed 700, so every test added past it had to be paid for by one removed. The operator meant the number as a reference that keeps the suite from growing back to its former size, not as a limit a new test must buy its way under.

## Decision

### 1. A reference

The Java suite under `src/test/java` is kept near 700 methods annotated as tests, counted as ADR-368 §1 counts them. A count past 700 is no failure: a test added past it is not paid for by one taken out. It is a sign to look for tests to cut, by ADR-368 §2's order, when the suite is next cut.

### 2. The claims check says so and passes

`docs/check-claims.mjs` names the count and the reference on every run. At 700 or fewer the line is `PASS`; past 700 it is `WARN`, names how far past the reference the count lies, and does not fail the check.

### 3. Run ids

No run id moves: no class under `src/main` changes.

## Alternatives refused

- **A higher ceiling.** Any fixed limit would make the next test past it pay as this one did; the operator wanted a reference, not a new number to fail on.
- **No count at all.** Without it nothing would show the suite growing back, which ADR-368 cut it for.

## Consequences

- A change adding a test no longer removes one to make room for it; one that removes a test does so because the test's claim is held twice or is worth less, by ADR-368 §2.
- The claims check's output carries a `WARN` line while the suite is past 700.

## What this does not decide

- When the suite is next cut, and to what.

## Tests

- `src/test/docs/check-claims.test.mjs`'s test of the suite's size: 700 methods pass, naming the count and the reference, and 701 are a warning, naming the count, the reference and how far past it, and not a failure (§2).
