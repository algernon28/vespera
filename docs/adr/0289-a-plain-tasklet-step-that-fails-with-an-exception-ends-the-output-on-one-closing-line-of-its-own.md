# ADR-289 — A plain tasklet step that fails with an exception ends the output on one closing line of its own

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md)'s *The last line*, and [ADR-281](0281-the-closing-line-is-the-last-line-of-output-and-two-library-loggers-write-to-the-console-only-at-warn.md) §1 and §2.
- **Amends**: ADR-281's *What this does not decide*, its first entry: a step of another stage that fails with an exception now has a closing line.
- **Settles** [#585](https://github.com/algernon28/vespera/issues/585).

## Context

Measured on the packaged jar: embedding scoring failing on a server error from Ollama (exit 1) ended on Spring Batch's `AbstractStep` ERROR and its stack trace; the last line was a frame. Extraction, stage 1 (archive gone) and stage 4a log a closing line from `afterStep`, which Spring Batch calls after it has logged the failure, so theirs is last. Every step built by `TaskletSteps` had none.

## Decision

`StepFailureClosingLine`, one `StepExecutionListener`, is registered by every builder in `TaskletSteps`: census, stages 1, 3, 4b, 5, 6a and 6b. It is registered before a caller's own listener, as Spring Batch runs `afterStep` in reverse order, so the line stays last. When the step did not complete and recorded an exception, `afterStep` logs at ERROR `Step <name> failed and is not recorded as finished: <cause>. Fix what that names and run the same command again.`, the cause read by `StepFailure.named`. It is the last line (ADR-281 §2).

It says nothing for a step that ended FAILED with no exception, which writes its own line from inside the tasklet (embedding scoring's refusals, generation's stop), nor for stage 1's archive gone, whose line is already there. Extraction, seed extraction and stage 4a keep their own lines, which carry counts.

## Alternatives refused

- **A line per stage**, as the three have it: a dozen near-copies of one sentence, and a new step would again end on a trace until someone wrote one.
- **A job-level listener**: Spring Batch's job line follows it, and ADR-281 §1 hides that only on the console.

## Consequences

- **No run id moves.** Nothing is recorded or judged differently; a failed step recorded no completion before and records none now. `StageModules` is unchanged.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.StepFailureClosingLineTest` | one line for a step that failed with an exception, naming the step and the cause; none for a completed step, for a step that failed without an exception, or for stage 1's archive gone |
| `pipeline.TheClosingLineIsTheLastLineIT.aRunWhoseEmbeddingStepFailsPrintsItsClosingLineLast` | the packaged jar, Ollama answering every embed call with 500: exit 1, and the last line that is not blank names `embedding-scoring` as failed and not recorded as finished, ending on its advice |
