# ADR-107 — The arrangement gate approves a named 6a run, and the operator's path becomes five invocations

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) — the count only. Four becomes five, and the table gains a row. Everything else that record decided stands: the operator is told the next *value* and never the stage, no ordinal appears in operator-facing text, and the shape is `vespera run` re-invoked rather than a subcommand per stop.

## Context

[ADR-022](0022-stage-6-splits-into-arrangement-6a-then-generation-6b.md) gates generation on a human reading the arrangement. [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md) gave 6a rows to write and [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md) gave each cluster a derived label, so there is now something a person can actually review. What was never decided is how the approval is expressed, and what it costs the operator's path.

**This is a gate in [ADR-031](0031-gates-are-required-inputs-not-pauses.md)'s sense, and not the `RelevanceFloor` case.** That class is explicitly *not* a gate, and its javadoc says why: the run is what produces the data the threshold is calibrated from, so gating on it would mean never producing the report that lets anyone set it. The arrangement is the opposite — 6a has already run, written its rows and written its report before approval is asked for. So [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md)'s shape applies unchanged.

## Decision

### `arrangementApproved` holds the first twelve characters of the 6a run id

The arrangement report prints it, and the gate message hands it over ready to paste. Twelve rather than sixty-four is the whole of the concession to typing: 48 bits is far past collision risk at any corpus size, and the ledger keeps the full id.

**Why it names a run rather than being `true`.** An approval is of *a specific arrangement*. A boolean never expires — an operator who approved one state of the corpus would ship a re-clustered one later having looked at nothing, and ADR-022's gate would have become a switch flipped once. Naming the run means a re-arrangement closes the gate again, which is the only property that makes the gate worth building.

**A prefix matching no 6a run for this walk leaves the gate closed**, and says so rather than proceeding. A prefix matching two stops the run, which is the rule [ADR-099](0099-a-stages-upstream-run-is-looked-up-by-stage-and-walk-not-recomputed-and-two-candidates-stop-the-run.md) already sets for an ambiguous upstream.

**`provenance` carries what was checked**, in the operator's words — "read arrangement.html, 312 clusters under 9 seeds, spot-checked the eight largest". Nothing verifies it, exactly as nothing verifies that the relevance floor was read off the labelling page: provenance is free text and that is what it is for (ADR-031).

### The gate is ADR-080's shape, applied a fourth time

The invocation ends, the job succeeds, no 6b run is minted, and nothing is removed. `NextAction` names the missing value and the single next action.

### What the operator reads: `arrangement.html`

Beside the database, by the same machinery as the five reports already written there: every seed partition, its clusters, each cluster's label and document count, and the exemplar documents behind each label, linked to the originals so a reviewer can open one and disagree with it.

Nothing on that page was written by a model. ADR-106 put generation after this gate for exactly that reason: the review is of a derivation, and a reviewer checking a generated label would be reviewing the thing the gate exists to authorise.

### Five invocations

Invocation 4 now removes what the relevance floor cuts, arranges what survives, writes `arrangement.html` and ends here. A fifth follows:

| | The operator has set | The command | What it does |
|---|---|---|---|
| **5** | `arrangementApproved` | `vespera run` | generates the synthesis docs and writes the deliverable |

**The fifth cannot be folded into the fourth.** An approval given before the arrangement exists approves nothing, and ADR-047's rule is that the pipeline terminates at a missing input and resumes on re-invocation. The number changes because a gate was added between two stages, which is precisely the case ADR-098 anticipated when it refused to number the stops in operator-facing text: "a scheme is stable only until a gate is added between two others".

## Consequences

**ADR-098's headline number is wrong from today**, and is amended here rather than left to drift. Its reasoning is untouched, and its own refusal to print ordinals is what keeps the change invisible to the operator: no message says "step 4 of 5", so nothing in the product has to be renumbered.

**`README.md` does not change yet.** It documents how the tool is driven *today*, and today 6a and 6b are not built — a five-row table would describe an invocation nobody can make. `docs/check-claims.mjs` checks the count in that document against the rows beneath it, so the two move together when the code ships. The hand-off spec collects it.

**`NextAction` gains a value to name**, and the hazard its javadoc already records applies: the line must name every value the next invocation wants, not one at a time. `arrangementApproved` is unlike the others in that it cannot be set early — it names a run that does not exist until the invocation before it has run — so it is the first value in this system that is genuinely sequential rather than merely unset.

**An operator who re-arranges must re-approve**, including after a change they did not make deliberately: a new embedding model, a changed relevance floor or a re-walk all mint a new 6a run id. That is the intended cost. The alternative is an approval that outlives what it approved.
