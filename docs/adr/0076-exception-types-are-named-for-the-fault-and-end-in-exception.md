# ADR-076 — Exception types are named for the fault, and end in `Exception`

- **Date**: 2026-09-05
- **Status**: accepted
- **Amends**: none (records a convention that had emerged across six types without ever being decided, and corrects the six)

## Context

Six throwable types existed in `src/main` before this decision, and not one of them carried the `Exception` suffix: `CheckpointMismatch`, `ExcludesNothingViolation`, `DoclingCallTimedOut`, `SchemaVersionMismatch`, `ServiceScopeFailure`, `ServiceScopeCircuitBreakerTripped`. The pattern was perfectly consistent across four modules, and recorded nowhere — not in an ADR, not in `AGENTS.md`, not in `CONTEXT.md`. A reader arriving at `CheckpointMismatch` could not tell whether the missing suffix was a considered choice or six repetitions of the same oversight, and neither could an agent adding the seventh.

That is the real cost of an unrecorded convention: it is indistinguishable from drift, so every new class reopens the argument.

**The trigger was a message, not the names.** `ServiceScopeCircuitBreakerTripped` surfaced from a stage-2 run reading:

> `docling-serve read as dead: 5 consecutive service-scope failures, of any mix of categories`

Three separate faults in one line. *"read as dead"* is ADR-071's reasoning voice — an explanation of why the run stops, not a statement of what happened. *"of any mix of categories"* describes the **policy**, and was actively false about the event that produced it: all five failures in that run were `capacity`. And *`docling-serve`* is hardcoded into a `pipeline` type, while ADR-012 holds the serving runtime to be config.

The class name had the same shape of problem. `ServiceScopeCircuitBreakerTripped` names **the mechanism that noticed** — a circuit breaker, an implementation metaphor — rather than **what is wrong with the archive**: the extractor has stopped answering. `DoclingCallTimedOut`, by contrast, already named a fact. So the six were not uniformly weak; the weakness was concentrated where a name reached for machinery.

## Decision

### Every throwable type's name ends in `Exception`

No exceptions to the rule, including subclasses of `IllegalStateException` and types that read fluently without it. This is the one naming convention in Java that a reader of any codebase already has, and spending it to gain a slightly nicer-reading identifier is a bad trade: `catch (CheckpointMismatch e)` costs a reader a lookup to learn that the type is throwable at all, and `throws SchemaVersionMismatch` reads as a value until proven otherwise.

The suffix is a noun-phrase suffix, so the stem is a noun phrase too. A past participle plus `Exception` — `DoclingCallTimedOutException` — satisfies the letter and not the point; the JDK's own `TimeoutException` is the form to copy.

### The name says what is wrong, not which mechanism noticed

`ExtractorStoppedAnsweringException` over `ServiceScopeCircuitBreakerTripped`. A circuit breaker is how this repo detects the condition today and could be replaced tomorrow by a health poll or a skip policy without the archive's problem changing at all. Naming the detector welds a diagnosis to one implementation, and forces the reader to know the mechanism before they can read the failure.

Where the repo's own words for the condition already exist, they win. `ExtractionCircuitBreakerTest` described this one as *"a converter that has stopped answering"* before the type was renamed to match it.

### The message states the event; the reasoning lives in the javadoc; the specifics live on the cause

An exception message is read by an operator during a failure, not by a reviewer weighing a design. So:

- **State what happened**, in terms of the corpus and the archive: `the extractor set aside 5 occurrences in a row without converting any of them`.
- **Never restate the rule.** A message that explains the policy will eventually describe an event that did not happen, which is exactly how "of any mix of categories" came to assert a mix that a streak of five `capacity` failures never had.
- **Never duplicate the cause.** The chained cause already carries the occurrence, the category and the detail; a message that repeats them goes stale independently of them.
- **Do not hardcode a configurable runtime's name** (ADR-012). Where the identity matters, pass it in.

The rationale belongs in the type's javadoc, where the reader is a person looking at the code and `CONTEXT.md` is available to them — the same split ADR-052 already draws for report-visible text.

### The six existing types are renamed

| was | now |
| --- | --- |
| `ServiceScopeCircuitBreakerTripped` | `ExtractorStoppedAnsweringException` |
| `ServiceScopeFailure` | `ServiceScopeFailureException` |
| `DoclingCallTimedOut` | `DoclingCallTimeoutException` |
| `CheckpointMismatch` | `CheckpointMismatchException` |
| `ExcludesNothingViolation` | `ExcludesNothingViolationException` |
| `SchemaVersionMismatch` | `SchemaVersionMismatchException` |

Only the first changes what the name means; the other five are the suffix alone. ADR-071's reference to `faultTolerant().skip(ServiceScopeFailure.class)` is corrected to match — a factual correction to a record, not a reopening of that decision, which line 55 of ADR-071 had already left the exact Java classes to the hand-off spec.

## Consequences

**The rule is checked rather than remembered.** `ExceptionNamingTest` scans the compiled application packages for `Throwable` subtypes and fails on any whose name does not end in `Exception`. A convention that lives only in a document is one an agent has to read the document to follow; this one fails the build instead. The half of this decision about *what* the name says cannot be tested and stays a matter for review — but the half that can be mechanised is.

**Renaming cost, paid once.** Six files moved and about thirty referencing files touched, across `corpus`, `extraction`, `ledger` and `pipeline` and their tests. Nothing behavioural changed and the suite stayed green, which is the argument for doing it now rather than at the fiftieth exception type.

**One class of operator-facing text is now decided, and the rest are not.** ADR-052 governs report-visible text; this governs exception messages. Log statements, CLI output and the profile's own prose remain unrecorded, and a future decision may want to say the same thing about them in one place rather than three.
