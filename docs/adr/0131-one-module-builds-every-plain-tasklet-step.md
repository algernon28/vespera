# ADR-131 — One module builds every plain tasklet step, and each stage's configuration names only its step

- **Date**: 2026-09-19
- **Status**: accepted

## Context

`pipeline` composes the cascade as Spring Batch configuration classes: one `*JobConfiguration` per place a step is named. Fourteen of them existed when this was measured. Ten were a near-identical 22–29-line class whose entire body was three lines of `StepBuilder` — construct from a stage constant, attach one tasklet with the transaction manager, build. The stage constant and the tasklet type were the only things that varied:

`ArrangementJobConfiguration`, `ByteLevelReductionJobConfiguration`, `ClusteringJobConfiguration`, `ContentCensusJobConfiguration`, `EmbeddingModelJobConfiguration`, `GenerationJobConfiguration`, `RelevanceFloorJobConfiguration`, `RelevanceReportJobConfiguration`, `RelevanceScoringJobConfiguration`, `SeedCorpusComparisonJobConfiguration`.

Two more steps had the same shape. Census's step is the same construction plus a `PROPAGATION_NOT_SUPPORTED` attribute (ADR-055), and stage 4's `redundancyResolutionStep` is the plain shape inside a configuration that also holds a chunk-oriented step. The `StepBuilder` call was written out fourteen times, and the one thing a caller actually varies — the step's name and the tasklet behind it — was buried in boilerplate that was identical every time.

**The duplication was not a recorded decision.** Each class's javadoc said the stages were kept apart so that "each ticket contributes its own step bean rather than growing one shared class." That rationale lives in no ADR; it was written into the classes as they were added, one per slice, and inherited. It defended a boundary nobody had decided to keep: the classes are one per stage, but the *construction* inside them never varied and never needed to.

**The classes cannot simply be merged away, and that is the design constraint.** Every whole-context slice test imports each `*JobConfiguration` by name — `CensusInvocationTest`, `ClusteringInvocationTest`, the generation and relevance invocation tests, and the rest — so the class is the seam through which a stage's step reaches a test's slice. Deleting the classes would force every test to change and would remove the one place a stage's step is named. The right unit to collapse is therefore the duplicated construction, not the per-stage class.

**The chunk-oriented steps are a different shape.** Extraction (stage 2), seed extraction (stage 5's first step) and stage 4's signature step each have a reader, a processor or a writer, and fault tolerance of their own. Folding them into the same construction would need a parameter for every difference, which is the shallow-module trap rather than a consolidation.

## Decision

**Introduce `TaskletSteps`, a deep module in `pipeline`, as the one place a plain tasklet step is built.** Its interface is two static methods: `taskletStep(name, jobRepository, transactionManager, tasklet)` for the transactional default every stage but census wants, and `taskletStepOutsideAnyTransaction(...)` for census's `NOT_SUPPORTED` shape. `StepBuilder` and `DefaultTransactionAttribute` are the implementation, hidden behind that interface. In the vocabulary of the codebase-design skill this is a **deep module**: a small interface the callers already have every parameter for, behind the construction knowledge and the one transaction attribute. The **leverage** is that a new plain tasklet step is one call; the **locality** is that how such a step is built, and that census runs outside a transaction, are written once.

**Every plain tasklet step goes through it.** The ten classes above, `CensusJobConfiguration.censusStep`, and `RedundancyJobConfiguration.redundancyResolutionStep` each call `TaskletSteps` and keep only the name and the tasklet. Census's own javadoc still carries the reason its step is outside a transaction; the module carries the mechanism.

**The chunk-oriented steps are left exactly as they are.** Extraction, seed extraction and stage 4's signature step keep their `StepBuilder` construction, their readers and writers, and their fault tolerance.

**The per-stage `*JobConfiguration` classes remain.** They are the seam the slice tests import and the place each stage's step is named; only their construction body and their javadoc change. The javadoc's "each ticket contributes its own step bean" sentence is retired, because it stated as a rule a separation that was doing nothing the module does not now do better.

**Nothing else changes.** Every step's bean name, every step's name, the order in `CensusJobConfiguration.vesperaJob`, and every transaction attribute are identical before and after. No dependency is added.

## Consequences

**One place knows how a tasklet step is built.** Each of the twelve call sites is one line; a change to the construction, or to census's transaction attribute, is a change in `TaskletSteps` rather than in twelve files. The deletion test passes in the direction that matters: remove `TaskletSteps` and the `StepBuilder` boilerplate reappears at twelve call sites.

**The line count barely moves, and that is the honest measurement.** The ten wrappers were 275 lines among them and are 235 after; `TaskletSteps` is 64, most of it the javadoc that explains the module and census's attribute. The gain is locality, not fewer lines: the duplicated *knowledge* is gone, the naming each class did is kept.

**A new stage whose step is a single corpus-wide tasklet now has a one-line addition.** It still adds a configuration class — because its step must be named somewhere and its slice test may import it — but that class carries no construction.

**The per-stage classes now say what a stage's step is, not how a step is built.** That is the division ADR-040 draws between the composition root and the capability modules, applied one level down: name the stage, delegate the mechanism.

**Reversal is cheap.** Re-inlining the `StepBuilder` calls into any or all of the twelve call sites restores the previous shape, and only the javadoc's rationale would need writing back.