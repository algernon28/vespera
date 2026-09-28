# ADR-171 — Bounds on Vespera’s wait-and-retry for sidecar failures

- **Date**: 2026-09-28
- **Status**: accepted
- **Extends**: [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md), [ADR-170](0170-vespera-waits-and-retry-calls-after-connection-failures.md) and [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md).
- **Keeps**: The 17-second wait ceiling from ADR-164 G; the one-retry limit from ADR-170.
- **Settles**: point 3 of [#326](https://github.com/algernon28/vespera/issues/326).

## Context

Point 3 of #326 asks for bounds on Vespera’s wait-and-retry for sidecar failures. This decision sets:

- How long a step waits,
- How many restarts it tolerates,
- How a retried call is kept from counting twice toward ADR-071's streak,
- When a file that crashes the converter every time becomes an `extraction-failed` verdict.

## Decision

### §1. Wait ceiling and retry limits

- **Max wait per call**: 17 seconds. Measured as the time between a sidecar crash and `/health` answering again (ADR-164 G), with a small buffer to account for Docker's restart scheduling.
- **Max retries**: 1. One retry is enough to recover from transient crashes while avoiding infinite waiting on a permanently dead sidecar.
- **Total time budget**: 17 seconds per failed call. If `/health` does not answer within 17 seconds, the step fails immediately with a message that names the wait ceiling.

### §2. Streak breaker bounds

- **Stain mitigation**: A retried call **does not count toward the failed call's streak**. ADR-071's breaker starts fresh from the retried call.
- **Separate streak**: The retried call is a new call toward ADR-071's breaker; a service-scope failure from it is a new streak, not one the failed call contributed toward.
- **Breaker resets**: The breaker resets when the sidecar answers `/health` (since a healthy sidecar means the streak is broken).

### §3. Permanent failure handling

- **File that crashes every time**: Becomes `extraction-failed` with reason `"connection-refused: sidecar not healthy after wait"` (for stage 2) or `"refused: sidecar not healthy after wait"` (for seed extraction) when the retried call also fails.
- **Reason format**: follows ADR-139’s `category: detail` shape, where `category` is from `FailureCategory` and `detail` is a concise explanation.
- **Step fails**: The step fails with the permanent failure message and says to run the command again, but the file is now flagged as `extraction-failed` for future reference.

### §4. Docker restart bounds

- **Sidecar restart ceiling**: Docker’s `unless-stopped` policy keeps trying indefinitely, with a wait that doubles up to one minute and resets after a healthy stretch. This is independent of Vespera’s bounds.
- **Vespera bounds override**: Vespera’s 17-second wait ceiling is shorter than Docker’s restart wait ceiling, so Vespera fails faster than Docker would if the sidecar never answered `/health`.

## Alternatives rejected

- **Variable wait time**: Would require new measurements; fixed at 17 seconds for simplicity and to match ADR-164.
- **More than one retry**: Risks infinite waiting; one retry is sufficient for crash recovery.
- **Let Docker’s restart policy drive Vespera’s bounds**: Docker may wait longer than 17 seconds, and Vespera needs to fail faster for good UX.
- **Let the streak breaker drive Vespera’s wait**: The streak breaker counts five service-scope failures; Vespera’s bounds on connection failures are independent.
- **No permanent failure flag for files that crash every time**: The operator needs a record of which files have permanent issues.

## Consequences

- **Implementation**: Added to `ExtractionItemProcessor` and `SeedExtractionItemProcessor` as bounds for connection-refused/reset failures.
- **Test updates**: `ExtractionWithTheSidecarDownTest` and `SeedExtractionWithTheSidecarDownTest` (if exists) pin the 17-second wait, one-retry and streak-mitigation behavior.
- **Stain mitigation**: The retried call does not stain the streak breaker, tested by asserting that five failed connections do not stop the breaker if the retried calls succeed.
- **Permanent failure**: Integration test that simulates a file that crashes every time validates that it becomes `extraction-failed`.

## Tests

`ExtractionWithTheSidecarDownTest` is extended to test:

- `waitsExactly17Seconds`: when `/health` answers after 17 seconds, the retry succeeds.
- `retriedCallDoesNotCountTowardStreak`: five failed connections followed by five successful retries do not stop the breaker.
- `permanentFailureForCrashingFile`: a file that crashes on both the initial call and the retry becomes `extraction-failed`.

`SeedExtractionWithTheSidecarDownTest` (if exists) is updated similarly.

A new integration test that simulates a sidecar that never recovers tests that the wait ceiling drives Vespera’s failure faster than Docker’s infinite retries.
