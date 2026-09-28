# ADR-170 — Vespera waits and retries calls after connection failures

- **Date**: 2026-09-28
- **Status**: accepted
- **Extends**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) — this decision extends ADR-071's "Service-scope failures are skipped immediately, no in-process retry" to include connection refused/reset failures.
- **Keeps**: ADR-071's one synchronous call shape for successful retries (no async submit-and-poll).
- **Settles**: point 2 of [#326](https://github.com/algernon28/vespera/issues/326).

## Context

On 2026-09-27, the Docling sidecar died of a segmentation fault (exit 139) on the 10th of 19 seeds. Seed extraction failed, and the invocation with it. The restart policy of ADR-164 had the sidecar back in about 6 seconds, but stage 2 failed on the first dropped call, and the next invocation sent the same file again.

What is measured in ADR-164's probe G is 17 seconds between sidecar crash and `/health` answering again. That's the time a step could wait for the sidecar and retry calls in flight.

Today (the code as of ADR-164), a connection refused or reset failure — anything other than a timeout or a service-scope failure Docling answers with — fails the step immediately, no wait, no retry (§2 of ADR-164). It leaves every rule about calls already in flight as it is, and the operator runs the same command again.

## Decision

### §1. Retry policy for refused/reset connections

When a Docling conversion call fails with a connection refused or reset, **the step waits for `/health` and retries the call once**.

- **Wait time**: 17 seconds, measured as the time between a sidecar crash and `/health` answering again (ADR-164 G), plus a small buffer to account for Docker's restart scheduling.
- **Retry shape**: exactly one synchronous retry, using the same call parameters (no async submit-and-poll, same `/v1/convert/file` endpoint, same export options).
- **Retry trigger**: `/health` answering with status 2xx. If `/health` never answers within the wait ceiling (see §3), the step fails with the connection failure message.
- **Retry semantics**: the retried call counts as a new call toward ADR-071's streak breaker, not toward the failed call's streak. A service-scope failure from the retried call is a new streak, not one the failed call contributed toward.

### §2. Service extraction and stage 2 behavior

Both **seed extraction** and **stage 2 answer the same way for a refused/reset connection**:

- Seed extraction waits and retries, just like stage 2.
- Both skip the streak breaker for the initial failed call; the breaker starts fresh from the retried call.
- Both write no verdict row for the failed connection (the step fails).
- Both use the same wait time (17 seconds) and retry once.

### §3. Bounds for the retry

- **Max wait per call**: 17 seconds, fixed. This is the measured recovery time; varying it would require a new measurement.
- **Max retries**: 1. More than one retry would risk infinite waiting on a permanently dead sidecar, and one is enough to recover from crashes.
- **Stall safety**: If `/health` does not answer within 17 seconds, the step fails immediately with a message that names the wait ceiling.
- **File that crashes every time**: Becomes `extraction-failed` with reason `"connection-refused: sidecar not healthy after wait"` (for stage 2) or `"refused: sidecar not healthy after wait"` (for seed extraction) when the retried call also fails.

### §4. Failure reporting

The operator's message from #311 is updated to mention the wait time:

> "Stage 2 failed: a Docling call was refused and the sidecar did not become healthy within 17 seconds. Run the command again."

The message names the specific file and occurrence for the failed call, just as #311 does today for other failures.

## Alternatives rejected

- **No wait, no retry**: Leaves the operator waiting until they notice a dead sidecar and runs `docker start` (ADR-164 §2). The cost is the wall-clock time until they come back.
- **Wait but no retry**: Brings the sidecar back for the next invocation, but leaves in-flight calls lost — the conversion that died never completes. This is no improvement over the current behavior.
- **Multiple retries**: Risks infinite waiting on a permanently dead sidecar. One retry is enough to recover from transient crashes.
- **Different wait time per step**: The same wait time applies to seed extraction and stage 2 because they share the same sidecar and health check.
- **Async submit-and-poll retry**: Introduced complexity with no measurable gain (ADR-071 explicitly rejected it).

## Consequences

- **Step 2’s wait and retry behavior**: Added to `ExtractionItemProcessor` in the Docling call handling.
- **Seed extraction’s wait and retry**: Added to `SeedExtractionItemProcessor` in the Docling call handling.
- **Operator message**: Updated to mention the 17-second wait ceiling.
- **Test failures**: `ExtractionWithTheSidecarDownTest` and `SeedExtractionWithTheSidecarDownTest` (if exists) pin the new behavior.
- **Existing tests**: `ExtractionStepTest` and `SeedExtractionStepTest` (if exists) need to be updated to expect retry behavior.
- **No behavior change for:** Service-scope failures (still skipped immediately, as ADR-071 requires).

## Tests

`ExtractionWithTheSidecarDownTest` is updated to test:

- `retriedCallAfterConnectionRefused`: when `/health` answers after a refused connection, the retried call succeeds.
- `retriedCallAfterConnectionReset`: when `/health` answers after a connection reset, the retried call succeeds.
- `timedOutWaitsAndFails`: when `/health` does not answer within 17 seconds, the step fails with the updated message.

`SeedExtractionWithTheSidecarDownTest` (if exists) is updated similarly.

A new integration test that uses a real sidecar with a simulated crash tests the full wait-and-retry cycle, using ADR-164's measurements as the expected wait time.
