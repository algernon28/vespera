# ADR-172 — Stage 2 and seed extraction answer the same way for Docling failures

- **Date**: 2026-09-28
- **Status**: accepted
- **Extends**: [ADR-170](0170-vespera-waits-and-retry-calls-after-connection-failures.md) and [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md).
- **Keeps**: That both steps fail on the first refused/reset connection (before ADR-170) and that both now wait and retry (after ADR-170).
- **Settles**: point 4 of [#326](https://github.com/algernon28/vespera/issues/326).

## Context

Point 4 of #326 asks whether stage 2 and seed extraction answer the same way for Docling failures. Currently they already do for refused/reset connections (both fail on the first dropped call, §2 of ADR-164), but they differ for failures Docling answers with, which stage 2 skips and counts toward its breaker, and seed extraction does not.

After ADR-170, both steps wait and retry for refused/reset connections, so they continue to answer the same way for that case, and this decision makes that the rule for all Docling failures.

## Decision

### §1. Unified behavior for all Docling failures

**Stage 2 and seed extraction answer the same way for all Docling failures**:

- **Refused/reset connections**: Both wait 17 seconds and retry once (ADR-170).
- **Timeouts**: Both fail on the first timeout (ADR-071's timeout exception). No wait-and-retry for timeouts.
- **Service-scope failures**: Both skip the step, counted toward ADR-071's breaker (ADR-071). No wait-and-retry.

### §2. Service extraction answer

Seed extraction follows the same rules as stage 2 for all Docling failures:

- **Refused/reset**: Wait 17 seconds, retry once (ADR-170).
- **Timeout**: Fail on first timeout.
- **Service-scope**: Skip, count toward breaker (ADR-071's breaker does not apply to seed extraction; it just skips the step and moves to the next seed).

### §3. No differences

There is **no difference** between stage 2 and seed extraction in how they answer Docling failures:

- The wait time (17 seconds) is the same.
- The retry count (1) is the same.
- The streak behavior (no stain from retries) is the same.
- The permanent failure handling (becoming `extraction-failed` with the same reason shape) is the same.
- The operator message format (naming the file, stating the wait ceiling) is the same.

## Alternatives rejected

- **Different behavior per step**: Would complicate the codebase and confuse operators. Uniform behavior is simpler.
- **Stage 2 waits and retries, seed extraction does not**: Both steps share the same sidecar and health check, so they should have the same wait-and-retry behavior.
- **Different wait times**: Both steps have the same sidecar, so the same measured recovery time (17 seconds) applies.
- **Different retry counts**: One retry is sufficient for both.

## Consequences

- **Implementation**: The unified behavior is implemented in `ExtractionItemProcessor` and `SeedExtractionItemProcessor`.
- **Code duplication**: Reduced because both steps use the same logic for wait-and-retry.
- **Test failures**: `ExtractionWithTheSidecarDownTest` and `SeedExtractionWithTheSidecarDownTest` (if exists) are updated to assert unified behavior.
- **Migration**: No migration needed; the change is a behavior extension, not a breaking change.

## Tests

`ExtractionWithTheSidecarDownTest` is updated to assert that stage 2 follows the unified behavior:

- `stage2WaitsAndRetriesForAllFailures`: stage 2 waits and retries for refused/reset connections, fails for timeouts, skips for service-scope failures.

`SeedExtractionWithTheSidecarDownTest` (if exists) is updated similarly.

A new integration test that compares stage 2 and seed extraction failure handling asserts that they answer the same way for each failure type.
