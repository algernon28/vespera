# ADR-071 — Docling's invocation contract is one sync call, a local timeout, and two consecutive-streak breakers

- **Date**: 2026-09-03
- **Status**: accepted
- **Amends**: none (fills in the mechanism ADR-070 left for this ticket — the timeout budget, the consecutive-timeout count, and the retry/backoff/skip policy for service-scope failures)

## Context

ADR-011 and `docs/architecture.md` §1.6 establish the general shape — the app owns its sidecars, and extraction is "only one HTTP call to a managed sidecar" — but neither pins the call's timeout, what happens when the call never returns at all, or the retry/backoff/skip policy for the service-scope failures ADR-070 routed away from any verdict. ADR-070 also left two numbers unpinned: the timeout budget itself, and how many consecutive timeouts read as a sidecar problem rather than a document problem.

No sidecar for Docling exists yet anywhere in this repo. `compose.yaml` and `TestcontainersConfiguration` currently declare only Chroma and Ollama, each started for the whole invocation and never scoped per-stage — the precedent this decision follows.

**What docling-serve's API actually offers** (read from its published REST API docs): synchronous endpoints (`POST /v1/convert/source`, `POST /v1/convert/file`) that block and return the result directly, and asynchronous variants (`.../async`) that return a task handle polled at `/v1/status/poll/{task_id}` or streamed over WebSocket, with an async response carrying a `task_position` queue-depth signal. A health endpoint exists for monitoring; its exact path and payload are a spec-level detail, not decided here.

## Decision

### The call is synchronous, one call per document

`docs/architecture.md` §1.6's "only one HTTP call to a managed sidecar" is read literally: stage 2 calls `POST /v1/convert/file` (or `/source`) and blocks for the result. The async submit-and-poll shape is not adopted — it buys a queue-depth signal nothing here needs, at the cost of a second call shape this repo has not needed anywhere else. Which exact endpoint and how the response is deserialised remain the hand-off spec's, the same deferral ADR-070 made.

### The sidecar's lifecycle mirrors Chroma and Ollama; readiness is checked once, lazily

`docling-serve` is registered in `compose.yaml` and `TestcontainersConfiguration` alongside Chroma and Ollama — started for the whole invocation, stopped at the end, per ADR-011's "app owns its sidecars." Its health is checked once, immediately before stage 2's step begins processing its first occurrence, not at job start (census and stage 1 never touch it) and not before every call (a dead sidecar already fails the call itself, which the skip/breaker machinery below already handles; the only case a per-call check would catch beyond that — "healthy but hangs on convert" — the call timeout below already catches too).

### The timeout budget and both consecutive-streak counts are fixed defaults in code, not Profile gates

`CONTEXT.md`'s **Profile** is "the per-corpus record of every judgement the engine cannot make for itself" — a corpus judgement, which is why ADR-070's confidence-quality floor shipped as an unset gate. A call timeout and a circuit-breaker streak length are not corpus judgements; they're operational parameters about network and hardware capacity, defensible as a fixed default without a person calibrating them first:

- **Call timeout: 5 minutes.** Generous enough for a large scanned PDF, short enough that a wedged sidecar doesn't stall a run for hours on one document.
- **Consecutive-timeout count (ADR-070's split): 3.** Three timeouts in a row flips the reading from document scope (`extraction-failed`) to service scope (no row written); fewer than three stays document scope.
- **Consecutive-service-scope-failure count (the circuit breaker, below): 5.** Deliberately higher than the timeout count, because it has to fire on a *mix* of categories, not repeats of one.

### A client-side timeout — Docling never responds at all — is read exactly like a Docling-reported `timeout`

ADR-070 only defined the reading for Docling's own reported `timeout` category inside a response; a call that never returns is silence, not a signal from Docling, and is a distinct case. It is folded into the *same* document-scope-versus-consecutive-streak logic as the reported case, using the same count (3) above, rather than being treated as automatically service-scope. A single slow-but-otherwise-fine document must still be able to earn `extraction-failed` on its own; routing every client timeout straight to service scope would excuse it from judgement instead.

### Service-scope failures are skipped immediately, no in-process retry

For `capacity`, `target_unavailable`, `internal` and `unknown` (ADR-070's service-scope side): no Spring Batch retry is attempted before skipping. ADR-070 already designed around "a later run examines it again" — an immediate retry against a sidecar that's out of capacity or down is very unlikely to succeed on the next call and adds complexity without changing the outcome the ledger's resume predicate already provides for free.

### The skip is Spring Batch's own mechanism; the circuit breaker is a separate consecutive-streak counter, not `skipLimit`

The step is configured with `faultTolerant().skip(ServiceScopeFailure.class)` — this is the reason Spring Batch is in the stack at all (`docs/architecture.md` §1.6). A generous `skipLimit` stays configured as a backstop against a slowly-degrading sidecar over a very long run, but it is **not** the circuit breaker: Spring Batch's `skipLimit` counts skips *cumulatively* across the whole step, so a corpus of 50,000 documents with ten sparse, harmless service-scope blips spread across a multi-day run would eventually trip it even though nothing was ever actually broken.

The circuit breaker instead needs a **consecutive**-streak count: a `SkipListener` (or `RetryListener`) increments on each service-scope skip and resets to zero on any successful conversion, failing the step once the streak crosses 5 (above). This fires on *any mix* of service-scope categories summed together — a sidecar alternating between `capacity` and `internal` every other call is exactly as dead as one repeating a single category, and per-category counters would let it evade both.

## Consequences

**A dead sidecar now fails loudly, not silently.** Left unchecked, "skip and continue" alone would let a run complete having examined nothing, with an exit code indistinguishable from success. The consecutive-streak breaker trades that for a run that stops with a clear cause once five service-scope failures land in a row.

**Two distinct counters exist for two distinct questions**, and reusing one for the other was considered and rejected: the timeout-count (3) asks "is this timeout mine or the sidecar's," scoped to one category; the breaker-count (5) asks "should the whole run stop," scoped to all service-scope categories together. Collapsing them would either make the breaker trip on a single flapping category too eagerly, or make it blind to a sidecar alternating between error types.

**The extraction cache still needs to store the full response** (ADR-070's consequence stands unchanged) — nothing here changes what's cached, only how the call that produces it is made and retried.

**Nothing here specifies the health endpoint's exact path/payload, or the exact Java classes for the skip/retry configuration** — those, like the call-site details ADR-070 deferred, belong to the stage-2 hand-off spec.
