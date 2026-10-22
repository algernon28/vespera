# ADR-317 — `arrangementApproved` may hold `auto`, a standing approval the operator writes, that opens the gate on every arrangement this invocation arrives at

- **Date**: 2026-10-22
- **Status**: accepted on 2026-10-22.
- **Decided**: by the operator, who asked that a changed arrangement no longer stop the run; the form, a value of the existing key with the default unchanged, is decided under delegation.
- **Amends**: [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md), "Why it names a run rather than being `true`": the approval may also be one word that names no run.
- **Keeps**: [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) §2: the gate is matched against the one arrangement this invocation made or continued, and with none it stays shut.
- **Keeps**: [ADR-031](0031-human-gates-are-optional.md): a gate is a required input. `auto` is an input the operator supplies, with provenance, as any other.

## Context

ADR-107 made the approval name one arrangement, so that every re-arrangement shuts the gate again. A changed relevance floor, embedding model, extraction attempt or re-walk each mint a new arrangement, and each then ends the invocation before generation until the new short name is copied into `arrangementApproved`. For an operator who iterates on the values ahead of the arrangement and reads the deliverable rather than `arrangement.html`, that is a stop on every change with nothing decided at it.

ADR-107 refused a boolean because it never expires: an operator who approved one arrangement would go on shipping later ones having looked at nothing. That refusal is about an approval that silently outlives what it approved. A value that says, in its own words, that every arrangement is approved unread does not outlive anything: it is the operator's standing decision, and it reads as one in `profile.yaml`.

## Decision

### 1. `auto` is a value of `arrangementApproved`

`arrangementApproved: auto` approves whichever arrangement the invocation arrives at. The gate opens on it as on a matching short name, and generation runs in the same invocation that arranged. Case is ignored and the value is trimmed. No arrangement's short name can be read as `auto`: a run id is lowercase hexadecimal, and `u`, `t` and `o` are not hex digits.

With no arrangement this invocation, the gate stays shut, as for any approval (ADR-154 §2).

### 2. The default is unchanged

An unset `arrangementApproved` keeps the gate shut and the closing line asks for the arrangement by name, as ADR-107 has it. Only the operator writing `auto` turns the stop off, and removing it, or writing a short name, turns it back on. A default of `auto` was refused: it would remove the read ADR-022 put before generation for every working directory without anyone having chosen to, which is ADR-107's objection to a boolean.

### 3. What reads the value

`ArrangementGate.approvedArrangement` opens on `auto`. `vespera label`'s closing line with `auto` written says the next run writes over whatever arrangement the documents are then in, unread, instead of saying it checks the value. The control panel accepts `auto` for `arrangementApproved` beside a twelve-character name, and its cost line for keys that re-arrange says the approval is asked for again unless `auto` is written.

### 4. The run ids that move

**None.** The generation run's identity names the full id of the arrangement it opened on (ADR-108), which is the same whether the gate opened by name or by `auto`. No key is added.

## Consequences

- **A changed value ahead of the arrangement is written over in the same invocation**, so the path is four invocations for an operator who writes `auto` with `relevanceScoreFloor`.
- **`arrangement.html` is still written** every time the documents are arranged, so the operator can read it after the fact.
- **The operator who writes `auto` has decided not to read each arrangement before it is written over**, and `provenance` is where that is stated.

## Tests that hold it

- `ArrangementApprovalOverAReusedWalkTest`: with `auto` written, each arrangement, the first and the one a changed relevance floor makes, is written over in the invocation that made it.
- `ArrangementGateAutomaticTest`: `auto`, in any case, opens on any arrangement, and with no arrangement the gate stays shut; a short name still opens only on the arrangement it names.
- `NextActionNamesTheNextInvocationTest`: `vespera label`'s line with `auto` written wants no key.
- `ProfileEditTest` and `TheJarReadsWhatTheControlPanelWritesTest`: the control panel saves `auto` and the jar reads it as an answer.
