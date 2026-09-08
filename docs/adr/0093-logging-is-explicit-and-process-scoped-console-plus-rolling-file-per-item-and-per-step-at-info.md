# ADR-093 — Logging is explicit and process-scoped: console plus rolling file, per-item and per-step at INFO, stage progress on a cadence

- **Date**: 2026-09-08
- **Status**: accepted
- **Amends**: none (fills a gap no prior ADR closed — logging was never decided at all)

## Context

Logging exists, but only ad hoc. `CensusTasklet`, `RedundancyResolutionTasklet` (via `RedundancyJobConfiguration.logGateClosed`), `SeedExtractionItemWriter` and `SeedCorpusComparisonTasklet` each declare their own `Logger` and log the events specific to what they happen to already need to explain — a gate being shut, a seed folder producing nothing usable. Nothing else does: `ByteLevelReductionTasklet`, `ContentCensusTasklet`, and every class touching stage 2 (`ExtractionItemProcessor`, `ExtractionItemWriter`, `ExtractionCircuitBreaker`, `ExtractionHealthCheckListener`) had no `Logger` at all before this decision, and nowhere was there a per-item start/end line, a per-step boundary line, or a progress line. SLF4J and Logback are on the classpath only transitively, via `spring-boot-starter-actuator`/`-batch`/`-jdbc` pulling in `spring-boot-starter-logging`; nothing configures either, so the project runs on Spring Boot's default console pattern by accident rather than by decision, and no `logback.xml` or `logback-spring.xml` exists.

This matters more here than in most Spring Boot projects because of two decisions already on record. ADR-035 has the pipeline running **unattended through 6b** — for most of its execution nobody is watching a console. And ADR-071 makes that concrete: a service-scope failure (a Docling timeout streak, a `capacity`/`internal`/`unknown` sidecar error) is designed to write **no Ledger row at all** — "no row written" is the decision's own wording — and `ExtractionCircuitBreaker` can stop a step outright after five such failures in a row. Right now, if that happens on day two of a multi-day run, there is no record anywhere that it happened. That is the gap this ADR closes.

`Ledger` and `AnomalyLog` are not the place to close it. ADR-042 gives Ledger the verdict vocabulary and nothing else; ADR-053 fixes AnomalyLog's vocabulary at three kinds. Both are document-scoped by design — facts about what happened to a file. A circuit breaker tripping, a sidecar health check, a step starting or finishing, are facts about the *process*, not about any document, and forcing them into either table would blur a boundary those ADRs drew on purpose.

ADR-006 already commits to measuring the corpus before judging it, so Census gives most stages a known denominator before they start — this is what a percentage-based progress report can be built on without inventing new counting.

## Decision

### Logs are process-scoped; Ledger and AnomalyLog stay document-scoped

The discriminator: if a fact is queried later as data about the corpus — a verdict, an anomaly, a metric — it belongs in Ledger/AnomalyLog/the metric tables, exactly as already decided. If a fact is about the pipeline's own execution — start/stop, sidecar health, retries, circuit-breaker state, an item entering or leaving a stage — it belongs in a log line, never a row. Nothing that already gets a row is restated in a log line, and nothing that belongs only in a log line is given a row. Service-scope failures are the case that motivated this: they are process-scope by ADR-071's own design, so they are logged, and were never meant to be ledgered.

### SLF4J is declared explicitly; Logback is configured via `logback-spring.xml`

Per ADR-046's convention — the pom states what a decision requires, not just what happens to be transitively present — `slf4j-api` is added as an explicit dependency. `logback-classic` is not added explicitly; it stays BOM-managed through `spring-boot-starter-logging`, the same way every other starter-provided library in this project is left unpinned. Configuration lives in `src/main/resources/logback-spring.xml` rather than plain `logback.xml`, specifically so `<springProfile>` blocks can vary behaviour between the default profile and `local` (`application-local.yaml` already establishes that split).

### Two appenders: console, and a rolling file in the configured working directory

Console output stays for interactive use — a human running a single command and watching it. A rolling file appender writes into the same configured working directory ADR-054 already gives the SQLite database, so the log sits next to the data it describes rather than in a separate, undecided location. It is one continuously-rotating file, not one file per run: ADR-055 has a walk resume under one id across multiple invocations, and per-run files would fragment a single logical execution across several files for no benefit. Correlation across lines is done by including the relevant run/walk/occurrence id in the message text, not by filename.

### Every step and every occurrence is logged at INFO, on start and on end

Job and step boundaries: one INFO line when a step starts, one when it ends, each end line carrying duration and final counts (processed, skipped, by category). Per-occurrence: one INFO line when a stage picks an occurrence up, one when it finishes, the end line naming the outcome as a label (e.g. `extraction-failed`) for correlation, not duplicating the full verdict record that already lives in Ledger. This is deliberately not gated behind DEBUG — every item's entry and exit is visible at the default level, because being able to see exactly where a long unattended run currently stands is the whole point of this decision.

### Stage progress is reported on a percentage/count cadence, where a denominator exists

Once Census has established a stage's total, that stage logs a progress line at INFO whenever it crosses **5% or 1,000 items, whichever comes first** since the last report — dense enough to be useful on a small corpus, sparse enough not to spam a huge one. Stage 1 (the walk itself, and Census) has no a-priori denominator — it is discovering the count as it walks — so it logs a running count only ("14,203 occurrences walked so far"), with no percentage, until the walk finishes and later stages inherit its total.

### Service-scope failures, circuit-breaker events, and sidecar health checks are logged at WARN or ERROR

Each service-scope skip: WARN, naming the failure category and the current consecutive-streak count. The circuit breaker tripping (`ExtractorStoppedAnsweringException`): ERROR, since it stops the step. A failed sidecar health check (ADR-071's once-lazy check before stage 2 begins): ERROR, since nothing in that stage can proceed without it.

## Consequences

**The pom gains one explicit line** — `slf4j-api` — tied to this ADR per ADR-046's convention. No other dependency is added; Logback stays exactly as BOM-managed as it already was.

**A new file, `src/main/resources/logback-spring.xml`, and a cross-cutting implementation obligation.** Every stage's tasklet/processor/listener needs a `Logger` and calls at the points this decision names. That touches every module in `pipeline` and the listener classes in `corpus`/`extraction`, but moves no capability across a module boundary — ADR-040's capability-shaped modules are unaffected; this is observability added inside each module's existing responsibility, not a new one.

**Log volume at full-corpus scale is real, and accepted knowingly.** ADR-071 sizes a working example corpus at 50,000 documents; a per-item start/end pair at every stage a document passes through produces hundreds of thousands of INFO lines over a full run. That is the explicit choice made here — visibility over brevity — but it means the rolling file's rotation policy has to be sized for that volume, not for a chatty-but-modest log. The exact rotation size/count and message field format are left to implementation, the same deferral pattern ADR-071 used for the health-endpoint path.

**Nothing here changes Ledger, AnomalyLog, any verdict vocabulary, or any stage's decision logic.** This is an observability-of-process addition only; ADR-042's ledger boundary and ADR-053's anomaly vocabulary are untouched, and no existing table gains or loses a column.

**What this decision does not settle.** Exact message formats and field names, the rotation size/retention count for the file appender, and which specific classes host each `Logger` call are left as implementation detail — the same kind of deferral ADR-070 and ADR-071 made for their own call-site specifics.
