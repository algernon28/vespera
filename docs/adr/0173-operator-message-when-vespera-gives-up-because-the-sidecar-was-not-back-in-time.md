# ADR-173 — Operator message when Vespera gives up because the sidecar was not back in time

- **Date**: 2026-09-28
- **Status**: accepted
- **Extends**: [ADR-311](0311-a-step-that-did-not-complete-says-it-failed-and-is-not-recorded-as-finished.md), [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md), [ADR-170](0170-vespera-waits-and-retry-calls-after-connection-failures.md) and [ADR-171](0171-bounds-on-vesperas-wait-and-retry-for-sidecar-failures.md).
- **Keeps**: The closing line format from ADR-311; the test-based message generation.
- **Settles**: point 5 of [#326](https://github.com/algernon28/vespera/issues/326).

## Context

Point 5 of #326 asks what the operator's message says if Vespera gives up because the sidecar was not back in time. Currently (ADR-311) the step fails with a line like "Run the same command again." With ADR-170-173, the step also names the wait ceiling and the file that failed.

The message today for a connection failure in stage 2 is:

> "Stage 2 failed: run the same command again."

That's from ADR-311, which says a step that completed is not recorded as finished, and says to run the same command again. With ADR-170-173, when a step gives up because the sidecar was not back in time, the message names the wait ceiling and the file.

## Decision

### §1. Message format for wait-and-retry failures

When a step gives up because the sidecar was not back in time:

> "Stage 2 failed: a Docling call was refused and the sidecar did not become healthy within 17 seconds. Run the same command again."

- **Prefix**: "Stage 2 failed:"
- **Cause**: "a Docling call was refused and the sidecar did not become healthy within 17 seconds."
- **Action**: "Run the same command again."

The message names the specific file and occurrence for the failed call, just as ADR-311 does today for other failures.

### §2. Message for seed extraction failures

Seed extraction uses the same message format:

> "Seed extraction failed: a Docling call was refused and the sidecar did not become healthy within 17 seconds. Run the same command again."

- **Prefix**: "Seed extraction failed:"
- **Cause**: "a Docling call was refused and the sidecar did not become healthy within 17 seconds."
- **Action**: "Run the same command again."

### §3. Message for timeouts

If a timeout occurs (not a refused/reset connection), the message is unchanged:

> "Stage 2 failed: run the same command again."

Timeouts are not retried, so there is no wait ceiling to name.

### §4. Message for service-scope failures

If a service-scope failure occurs, the step skips and counts toward ADR-071's breaker. No message is printed for skipped items; the step only prints its closing line when it fails completely.

## Alternatives rejected

- **Message without wait ceiling**: Would be less informative for operators.
- **Message with different wait ceiling per step**: Both steps use the same 17-second ceiling, so one message format is sufficient.
- **Message without file name**: The current format (ADR-311) already names the file and occurrence for other failures; keep consistency.
- **Message with cause name only**: The current format already includes a concise cause.

## Consequences

- **Implementation**: The message format is added to `ExtractionItemProcessor` and `SeedExtractionItemProcessor` in the exception handling for connection failures.
- **Test failures**: `ExtractionWithTheSidecarDownTest` is updated to assert the new message.
- **Seed extraction tests**: `SeedExtractionWithTheSidecarDownTest` (if exists) is updated to assert the new message.
- **Operator UX**: The operator sees exactly what went wrong and what to do.

## Tests

`ExtractionWithTheSidecarDownTest` is updated to assert the new message:

- `waitsAndFailsPrintsUpdatedMessage`: when `/health` does not answer within 17 seconds, the step fails with the updated message that names the wait ceiling.
- `waitsAndSucceedsPrintsNoFailureMessage`: when `/health` answers within 17 seconds and the retry succeeds, no failure message is printed.

`SeedExtractionWithTheSidecarDownTest` (if exists) is updated similarly.

A new integration test that simulates a connection failure validates that the operator message contains the wait ceiling and file name.
