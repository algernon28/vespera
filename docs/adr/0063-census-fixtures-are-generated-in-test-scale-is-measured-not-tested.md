# ADR-063 — Census fixtures are generated in-test; scale is measured, not tested

- **Date**: 2026-08-29
- **Status**: accepted

## Context

Nothing in the record addressed how census is tested without a real hundreds-of-GB archive, though `WalkTest` already half-answers it: every fixture it uses is built inside a test via `@TempDir`, nothing is checked into git, and an awkward-to-create fixture (a soft link needing a privilege to create on Windows) already falls back to an OS-specific alternative (`mklink /J`) and skips via `Assumptions.assumeTrue` if neither works. The class's own javadoc names what is still missing: "behaviour under an unreadable directory... is still open — see issue #15." An over-long path has the same gap, for the same reason (awkward to provoke, awkward to commit).

This software targets Windows only, with no cross-platform ambition — a fact that removes "what happens on Linux CI" as a real scenario, though it doesn't remove the underlying reason the existing skip pattern exists: individual Windows environments still differ (admin privilege to create a symlink, long-path support enabled in the registry, a container's stripped-down ACL model), so a fixture can still fail to be creatable in a *given* Windows environment even though the target platform is fixed.

## Decision

**Fixtures are generated in-test, never checked in — codifying, not changing, existing practice.** Every fixture, including the two not yet covered, is built inside the test that needs it via `@TempDir`.

**The two remaining anomaly-provoking fixtures extend the existing `assumeTrue`/`abort` pattern**, not a new mechanism: a permission-denied directory is created via `AclFileAttributeView`, denying read for the current user, cleaned up after the test; an over-long path is built by nesting directories past NTFS's ~32,000-character limit. Each skips (`Assumptions.assumeTrue`, not `@EnabledOnOs`) if the current environment can't create it, for the reason above — a runtime capability check, not an OS-name proxy, exactly as the soft-link test already does.

**"Excludes nothing" stays asserted only at small, hand-built scale.** The identity `entriesSeen == occurrences + anomalies + directoriesEntered - 1` (already tested in `accountsForEveryEntryItMet`) is a structural invariant of the counting logic, true by construction at any N — a fixture with a million entries would exercise the same arithmetic a four-file fixture already exercises, only slower. No large-N fixture is added for this.

**No committed test of walk performance at realistic scale.** Nothing walks a synthetic million-file tree in the standard suite. This project's established alternative — a throwaway, uncommitted probe measuring a fact once, its finding recorded in a ticket resolution or ADR (ticket #14's SQLite/Spring Batch throughput; this map's own NTFS traversal-order-stability probe behind ADR-055) — is the mechanism for scale characterization, exercised only when a real question about scale exists (e.g. a profiler pointing at a bottleneck), not speculatively now.

**No Linux CI concern.** The software is Windows-only with no cross-platform plan; CI, whenever built, runs on Windows runners. The skip pattern above is retained for environment-capability reasons unrelated to OS portability, not to support running the suite anywhere else.

## Consequences

**Two new fixture-building helpers are owed to `WalkTest`** (or a shared test-support class if a third stage's tests need the same fixtures later): one for a permission-denied directory, one for an over-long path, each following the create-with-real-mechanism-then-`assumeTrue` shape already established. For the hand-off spec.

**A skip is not silently invisible.** Per `AGENTS.md`'s existing convention, `Skipped` is reported alongside `Tests run` — an environment that can't create the ACL-denial fixture doesn't get credit for having tested it.

**If a real scale bottleneck is ever found**, the response is a new throwaway probe targeted at that specific concern, not retroactively adding a committed large-N regression test as a general practice.

## Amends

None. This fills the gap `WalkTest`'s own javadoc already named as open, and states explicitly what the existing tests already imply about fixtures and scale.
