# ADR-355 — The architect probes how a change behaves against each sentence of the record it implements

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Settles**: what the architect's gate asks of the code a change carries beyond green tests and amendments applied, and what its report says of it ([#695](https://github.com/algernon28/vespera/issues/695)).
- **Keeps**: [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §7: the agent definition lies in a closed `.claude` folder, so the change is a draft the operator installs.

## Context

The architect is the gate before a change lands on `main` (AGENTS.md, "Delegation"). Its eight items check that the build is green, that the docs are not stale, that the change traces to a record, that no test was weakened, that new code has tests, that module boundaries hold, that the vocabulary is the project's, and that `pom.xml` changes cite a record. Each is a check of what was built: none asks whether the code does what a sentence of its record says in a state that no test builds.

A change can pass all eight, its tests green, every amendment asked of it applied, and still contradict its record in such a state. Three kinds are known from ADR-334 §6 and §11:

- **A list rebuilt from a stale copy.** ADR-334 §6 says a save is one action under one hold of the working-directory lock, both files read first, then the list written "with the folder added or the one removed". Code that rebuilds the list from the copy a page carried, and not from the file read under the lock, writes over a folder another tab saved after that page was drawn. Each tab's own save, tested alone, is right.
- **A write of two files that is not one.** ADR-334 §6 says a refusal of either file writes neither. Code that moves the two files into place one by one leaves the first written when the second move fails. No test makes a move fail.
- **A default read as typed.** ADR-334 §11 preselects the GPU image. A form posts the preselected value back with every submit, Browse among them, and code that reads every posted value as named by the operator reads the preselection as typed.

Each is a judgement on the code and the sentence together: what states the code can meet is read from the code, and whether a state breaks the rule is read from the record. A lint reads neither.

## Decision

### 1. The gate gains a ninth item: probe each rule in four states

For each rule of the record the change implements, the architect reads the changed code that carries it out against the sentence that states the rule, and probes it in four states:

- **Concurrent or stale input**: two tabs or windows, a page drawn before another save, a list rebuilt from what a form or a caller carried rather than from what the file or table holds when it is written.
- **A failure partway through a multi-step write**: what stands on disk or in the ledger when the second of two writes, moves or rows fails, against what the record says a refusal leaves.
- **A default value posted back by a form**: a preselected, prefilled or placeholder value that comes back with any submit, Browse and Reset among them, read as one the operator named.
- **The same name in another case or spelling on Windows**: another case, `\` for `/`, a trailing separator or dot, a relative and an absolute spelling of one folder.

A probe follows the code, not the tests: it names the inputs, traces the lines they pass through, and says what is written. A probe that finds the sentence false stops the change as a weakened test does (item 4), with an amendment to `spec-implementer` for the code and one to `analyst` for a test that sets that state up. A probe with nothing to bite on in the change, where it has no form, no write of more than one file or no name compared, says so in one line.

### 2. The report names the sentence each probe tested

The report gains a line, **Probes**, between the evidence for each gate item and the amendments: for each of the four states, the sentence it tested, quoted with its record and section, the inputs traced and the `file:line` they reach, and whether the sentence held; or the one line saying why the state does not arise in the change. A probe left out of the report is a gate item skipped silently, which the definition already says is worse than one that could not be run.

### 3. The probes are in the gate's brief, not in a lint

Which code carries a rule, which states it can meet and what the sentence requires there are read, not matched. No script, test or claim checks code for these states. The four are the states the gate must try, not the only ones it may.

### 4. The definition is drafted, and the operator installs it

`.claude/agents/architect.md` lies in a closed `.claude` folder. The change is drafted at **`docs/agents/agent-draft/agents/architect.md`**, laid out as `.claude` is, apart from `docs/agents/guard-draft`, whose drafts are installed on their own records' terms. The operator copies the draft to `.claude/agents/architect.md` and deletes `docs/agents/agent-draft` in the same change. The draft is the installed definition with item 9 added to the gate and the **Probes** line added to the report, and nothing else changed.

### 5. Run ids

No class under `src/main` changes, so no stage's version moves.

## Consequences

- **A gate costs more reading.** Each rule a change implements is traced through its code four times; most probes on a change with no form and one file end in a line each.
- **A pass now says what was tried.** The report quotes the sentence and the lines traced, so a reader can see which states were probed and which did not arise.
- **The probes catch what the code shows**, not what the gate does not think of: a state outside the four, or one the code reaches only through another module, is still caught only by a test or by the gate's own reading.
- **Until the operator installs the draft, the installed architect has eight items**, and nothing asks the probes of it.

## Alternatives refused

- **A lint or a test per state.** Whether a list is rebuilt from a stale copy, or a write of two files is one, depends on what the record says the operation is. A pattern match would flag code that is right and pass code that is wrong.
- **Ask `spec-implementer` to probe its own change.** The implementer writes the code to its reading of the record; a probe by the same reader repeats that reading. The gate is the reader that did not write it.
- **Leave it to the tests the analyst writes.** A test sets up the states its writer thought of; the probes exist for the states nobody set up.

## Tests

- `TheArchitectProbesEachRuleInTheStatesNoTestSetsUpTest` holds, of the draft while `docs/agents/agent-draft` holds it and of the installed definition once it is gone: the gate's ninth item, followed by the four states of §1, each a whole line of its own in this record's order, and the paragraph saying what a probe comes to (§1); and the **Probes** line of the report, whole, between the evidence and the amendments (§2).
- Not held by a test: that the architect runs the probes, or what it finds. That is the agent's reading, which no test of this repository can run.

## What this does not decide

- Which states a later record adds to the four.
- Whether `analyst` or `spec-implementer` gain a step of their own for these states.
