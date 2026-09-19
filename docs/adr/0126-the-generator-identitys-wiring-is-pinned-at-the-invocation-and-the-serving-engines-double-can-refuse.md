# ADR-126 — The generator identity's wiring is pinned at the invocation, and the serving engine's double can refuse

- **Date**: 2026-09-19
- **Status**: accepted
- **Rests on**: [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (the generator identity carries the model's manifest digest, read in `pipeline` and handed down), [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md) (the two stops, and that both are checked where the name is resolved and before the 6b run is minted), [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) (a run row for a stage that did nothing reads as a pass that found nothing), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) (what made the open-gate path reachable at all), [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md) (the four test conventions this record's tests are written under).
- **Charted as [#196](https://github.com/algernon28/vespera/issues/196)**, under the stage 6a/6b map [#175](https://github.com/algernon28/vespera/issues/175). Surfaced by the architect review of [#193](https://github.com/algernon28/vespera/issues/193).

## Context

### What #179 proved, and what it did not

ADR-110 put the model's manifest digest into the generator identity, for one stated reason: without it *"a mutable tag re-pulled after upstream republishes mints the same 6b run id over different weights"*. #179 built that and pinned it — but only at the level of the composition. `GenerationRunTest` calls the static `GenerationRun.configConsumed(...)` with a digest of its own choosing and claims that digest comes out the other side. That is a true claim about a string builder. It says nothing about the call site.

Replace `ollamaClient.artefactOf(modelName).digest()` with `modelName` in `GenerationRun`'s constructor and every test in the tree still passes. The identity would then carry the generation model's name twice and no digest at all — exactly the failure ADR-110 exists to refuse, shipped green.

Two further properties are recorded and unpinned in the same way:

- **That the generation model resolves before the run is minted.** ADR-114 places both of its stops *"in `pipeline`, where the name is resolved and before the 6b run is minted"*, on ADR-080's ground. Nothing can see the order.
- **ADR-114's second stop itself** — a resolved name the serving engine has never pulled. `EmbeddingScriptedBeans.ollamaClient()` answers with a fixed digest for any name it is handed and has no way to refuse, so no test in the tree can reach that refusal. It is written, and it is unexercised.

### The ticket reached for a unit-level seam, and the reason it gave has expired

#196 framed this as a decision because the obvious seam was awkward: a unit test of `GenerationRun`'s constructor against doubles needs `@JobScope` and a job parameter. It reached for that seam because *"the open-gate path cannot run at all until #191 is settled, so an invocation-level test of the wiring is blocked"*.

**#191 is closed.** ADR-115 stopped walk churn and ADR-116 moved completion onto the step, and with them the open-gate path runs. `GenerationInvocationTest` today invokes the CLI twice, opens the gate on an approval an operator could actually type, and makes thirteen claims past it — one piece of writing per cluster, what was kept against which cluster, what a third invocation under a standing approval does. The blockage this ticket's framing rests on is gone, and the choice is therefore a real one rather than the only thing left.

### Why the unit-level seam is the worse of the two

It is not merely awkward. A unit test of that constructor has to stand up the scope and the job parameter that the constructor reads its root from — which is to say it rebuilds, by hand and in the test, a piece of the very wiring the ticket says is unpinned. What it would then prove is that a hand-assembled `GenerationRun` calls the collaborators the test handed it. The thing actually at risk is whether the *application's* composition reaches the serving engine at all, and a re-assembly cannot see that.

It is also blind to the property ADR-080 cares about. "No run row exists" is a statement about the ledger after an invocation, not about a constructor that threw.

## Decision

### The seam is the invocation, and the claims are made against the ledger

Every one of #196's four criteria is pinned by a `@JdbcTest` driving the CLI over a scripted corpus, in a new class beside the ones already there, reading back what the invocation left in the database.

- **What the identity carries** is claimed against the `config_consumed` column of the `run` row the invocation minted — not against the return value of `configConsumed(...)`. The static method is the composition and is already pinned; the row is what the wiring produced. Swapping the digest for the name at the call site changes the row and nothing else, so the row is where the claim has to live.
- **That the model resolves before the run is minted** is claimed through the row's contents, not through a row left lying about. What the serving engine reported is an *input to the run's own id*, so a wiring that minted first cannot have put that report into what it minted. The digest claim above is therefore the claim that sees the ordering, and it was measured to be so: reordering `GenerationRun`'s constructor to mint under a placeholder and resolve afterwards turns exactly that claim red.

  **A row left behind is not the observable, and this was checked rather than assumed.** The obvious claim — "the refusal leaves no `run` row" — cannot fail. The insert happens inside the step's own transaction, so a step that ends in an exception takes the row back whichever order the two statements were in. A test resting on it would have been the very thing this ticket exists to refuse: green under the defect it names. It is kept, with its reason corrected to the one it actually defends (ADR-080's property, which holds only while the minting happens inside the piece of work the stop ends), and it earns its place by turning red when ADR-114's second stop is removed altogether.
- **ADR-114's second stop** is reached by a serving engine that has never pulled the resolved name, which is the same test read for its other half: the invocation is refused.
- **The serving URL's absence** is pinned twice over — `GenerationRunTest` keeps its claim over the composed string, and the row claim states it again over what was actually recorded.

`GenerationRunTest` is not replaced. It says what the identity is made of; this says the application builds it from what the serving engine reported.

### `EmbeddingScriptedBeans` gains the ability to refuse, by name and off by default

The `OllamaClient` double lives there, and that is where the refusal goes. It is scripted per model name: a name the test marks as never pulled is refused with the shape `OllamaClient.artefactOf` refuses in, and every other name answers as before.

- **Per name, not per fixture.** `artefactOf` serves two callers (ADR-110, ADR-114) and stage 5 composes the embedder identity through the same double. A double that refused everything would stop the cascade three stages before the one under test, and the test would be green for the wrong reason.
- **Serving every model is the default**, so no existing test changes behaviour. The script is static, because the bean belongs to the context and a test cannot reach the instance before the invocation it is scripting — the same reason `GenerationScriptedBeans` holds its answers statically. It is dropped in `@BeforeEach` as well as `@AfterEach`: an `@AfterEach` alone leaks into the next class when a method dies before it, and class order is not fixed.
- **Not a second `OllamaClient` bean** in `GenerationScriptedBeans` or in the test class. Two beans of one type, one shadowing the other, is precisely the hazard `StubbedExtractionBeans`' javadoc documents at length; a fixture that silently replaces another fixture is worse than the one that cannot refuse.

The precedent is the one the ticket names: `EmbeddingScriptedBeans`' own javadoc records that `ChunkEmbedder`'s split-retry path *"has its own unit test against a fake that can refuse on purpose"*. The same thing is now true of the refusal ADR-114 wrote down.

### A class of its own, with a working directory of its own

The new tests go in `GenerationIdentityInvocationTest` rather than into `GenerationInvocationTest`.

One `@TempDir` working directory serves a whole invocation test class, and `profile.yaml` and the written reports sit in it, so methods in one class share state by construction. A class that scripts a serving engine into refusing is scripting the fixture every other method in its class would inherit; keeping it separate keeps that script out of thirteen tests that are about something else. It is also the smallest thing that merges: `GenerationInvocationTest` is where the other open 6b tickets are adding cases.

### No production code changes

`GenerationRun` already reads the digest from the serving engine, already resolves the name before minting, and already leaves no row behind when the resolution refuses. The defect #196 names is entirely in the evidence: three recorded properties that no test could see the loss of. Nothing under `src/main` is edited by this record, and that is the finding rather than an omission — a ticket whose whole content is tests that can actually fail is a ticket that has been done when they can.

## Consequences

**Three recorded properties become breakable.** Swapping the digest for the name, minting before resolving, and dropping ADR-114's second stop each now turn a test red. That is the return, and it is the whole return.

**The refusal ADR-114 wrote down is exercised for the first time.** It was reachable only against a real serving engine, which is to say only in a test the ordinary build does not run.

**`EmbeddingScriptedBeans` is now a scripted fixture rather than a fixed one**, which puts it in the same class of hazard as `GenerationScriptedBeans`: static state that outlives a method. Its reset is named and public to the package for that reason, and the class that scripts it drops the script twice.

**The unit-level seam this ticket reached for is refused rather than deferred.** Nothing is owed here later: if `GenerationRun`'s constructor grows a property that the invocation cannot see, that is the moment to revisit it, and this record is where to start.

**A stale premise cost nothing this time and is worth naming.** #196's reasoning rested on #191 being open, and #191 closed between the two. A ticket body is a statement about the tree on the day it was written; the acceptance criteria are what bind.
