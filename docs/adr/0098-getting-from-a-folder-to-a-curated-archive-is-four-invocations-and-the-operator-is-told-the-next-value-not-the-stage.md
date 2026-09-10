# ADR-098 — Getting from a folder to a curated archive is four invocations, and the operator is told the next value rather than the stage

- **Date**: 2026-09-10
- **Status**: accepted

## Context

[ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md) wrote the path from a fresh archive to a curated one down end-to-end for the first time, and explicitly left it for its own ticket ([#98](https://github.com/algernon28/vespera/issues/98)). This is that record.

**Every stop in that path was decided separately, and each decision was right about its own stop.** [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) stops stage 4 because a boilerplate floor is an input it cannot work without. [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) makes the embedding model a gate. [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) makes the seed folder one. [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) ships the relevance threshold unset and says in its own title that an unset floor **does not** stop the run. Nothing here relaxes any of them.

**What had never been examined is the sequence as a whole** — which is the classic way a system ends up correct and unusable. Three things fell out of looking at it once, all of them checked against the code rather than assumed.

### The count was wrong, in the record and in the ticket

#98 headlined **five invocations** and tabulated five rows. Its own table shows four operator actions: the fifth row's action is an em-dash. Writing a `below-threshold` verdict *is* the removal, because `Ledger#survivors` anti-joins blocking verdicts, so the fourth row's invocation removes the documents and the fifth row is that same invocation described a second time.

Two further miscounts ran the other way. The table never has the operator naming the **seed folder**, without which stage 5 does nothing — `SeedGate` guards six of the thirteen steps. And it folds **`vespera label`** into the prose "labels sixty documents", though `CONTEXT.md` defines an invocation as "one call of the command", which that is.

**Corrected, and stated as the number that matters:** the path is **four invocations** — three of `vespera run` and one of `vespera label`. Five is what an operator gets by discovering one gate per invocation, which is a measurement of the documentation rather than of the pipeline. The gap between four and five is the defect; the four is the contract.

### Nine locally-correct messages that produce no instruction

With nothing set, invocation 1 emits **nine** "gated" lines across steps 5 to 13 and exits 0. They name three different missing values. The embedding model is announced five times and the seed folder twice, though the seed folder is needed first, and the one thing the operator must actually do next is stated at step 5 and then buried under eight lines about problems they cannot reach yet.

Every one of those lines satisfies ADR-080's requirement to name the missing value and where to read the data informing it. The aggregate still misinforms, and no per-message rule can fix a property of the aggregate.

### One number leaked into operator text with nothing to anchor it

`RelevanceScoringTasklet` prints `gate 3's own step may not have run`. **There is no gate 1 or gate 2 anywhere in the codebase** — the rest of the numbering lives only in javadoc, as "Stage 5's first gate" and "ADR-083's second gate". [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md) already requires report-visible text to stand alone for a reader with no access to this repository, and a bare ordinal fails that outright.

## Decision

### Four invocations, and the seed folder is named before the first

| | The operator has set | The command | What it does |
|---|---|---|---|
| **0** | `seedFolder` | — | not an invocation: the exemplars are the operator's own knowledge and need no measurement from the tool |
| **1** | — | `vespera run <root>` | stages 0–3; stops at stage 4's gate. Cannot be earlier: the boilerplate floor is read off stage 3's own tables |
| **2** | `boilerplateDocumentFrequencyFloor`, `embeddingModel` | `vespera run` | dedups, extracts the seed set, embeds, scores, clusters, writes the reports and sixty blank questions. **Removes nothing** |
| **3** | sixty answers written into the label file | `vespera label` | records the answers, mints no run |
| **4** | `relevanceScoreFloor` | `vespera run` | writes `below-threshold`. Documents are removed |

**Step zero is a decision, not a formatting choice.** The seed folder is unlike every other key: the two floors are read off measurements the tool produced, and the model is a choice from outside, but the seed folder is available on day one and `CONTEXT.md` calls it "the sole carrier of domain knowledge in the system". Asking for it up front reframes the tool — *you supply exemplars, we curate to match* — where discovering at invocation 2 that exemplars were needed all along is the worst moment on the current path.

**It also cannot be delivered by any message the tool prints.** By the time anything is printed, invocation 1 has happened. That is what forces the sequence to be written down somewhere reachable before the first command, and it is why this decision entails a document rather than merely better logging.

### The operator is told the next value, never the stage they are at

**Every invocation ends with one line naming what is set, what is not, and the single next action.** Computed per invocation, so it cannot go stale the way a document can, and it is the smallest thing that addresses the actual defect: nine correct lines adding up to no instruction. The per-step gate messages stay as they are — each is right, and none of them is the problem.

**No ordinal appears in operator-facing text.** Not "gate 3", not "step 2 of 5". The message names the missing key, which every message but one already does. Numbering also fossilises: a scheme is stable only until a gate is added between two others, and this record has just renumbered the sequence once already.

### `vespera run`, re-invoked — and this is chosen, not inherited

[ADR-047](0047-the-pipeline-never-blocks.md)'s "the pipeline never blocks, it terminates and resumes on re-invocation" is what makes the whole shape work. A subcommand per stop was the alternative and is refused:

- **It requires more of the operator, not less.** They would have to know the stage names and their order — strictly more knowledge than "run it again and read the last line".
- **It contradicts `CONTEXT.md`'s `Invocation`**, which says one call "may advance several stages". Invocation 2 advances five.
- **It multiplies the CLI** from three commands to six or more, each needing its own gate handling and its own refusal text.

The virtue of the current design is that the operator never has to know where they are in the cascade — only what value is missing next. That property is what the closing line above delivers, and what a subcommand surface would throw away.

### The sequence lives in a `README.md`, and the state stays in `AGENTS.md`

`docs/architecture.md` is disqualified by its own charter — its status line says it "describes the system as decided, not as built", and this is entirely about the built tool. A published page under `docs/` loses to the repository root for the reader this is for: someone who has the repo and has forgotten the order.

**The division is strict.** The README says how to operate the tool. `AGENTS.md` keeps what state the project is in. Two files describing the state is the drift that [#132](https://github.com/algernon28/vespera/issues/132) and [#134](https://github.com/algernon28/vespera/issues/134) already cost two pull requests to correct.

**The cost is named rather than discovered later:** `docs/check-claims.mjs` reads its claims out of `AGENTS.md` only, so a README is a new operator-facing surface nothing verifies. That is accepted here because the alternative — putting the sequence where the guard can see it — means putting it in the file written for agents, and an operator should not have to read agent instructions to run the tool.

### A report that informs a threshold names that threshold

`relevance-labelling.html` tells the operator to write their number into `relevanceScoreFloor` and to record its provenance. `confidence-distribution.html` measures exactly what informs `degenerateOutputConfidenceFloor` and never names the key, and that key has no log line anywhere in the codebase — an operator can complete all four invocations without learning it exists.

**The asymmetry is unjustified, and the rule is general:** a report that measures what informs a threshold names that threshold and says the number is the operator's to write. This adds no gate — `degenerateOutputConfidenceFloor` stays optional, and unset still means no `degenerate-output` verdict.

## Consequences

**The tool acquires a front door.** A public repository with no README, no description and no license is public by default rather than by intent; this is the first artefact aimed at a person who wants to *use* Vespera rather than develop it.

**"Four invocations" becomes a claim that can rot.** It is true of a thirteen-step job with three gates. Add a gate and it is four plus one, and the README will not notice. Nothing checks it, which is the accepted cost above and the first candidate if `check-claims.mjs` is ever pointed at a second file.

**`CONTEXT.md` needs no change, and that is a finding.** #98 used the word "stop" nine times for a concept the glossary does not hold, and two of the four things it named were not gates. Rather than mint a fourth near-synonym for "the run ended and it is your turn" — the `Gate` entry already lists `pause` and `approval step` under `_Avoid_`, and had to concede `checkpoint` to [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md) after claiming it — the sequence is described with `invocation` and `gate` alone. Invocation 2 is a *completed* invocation that leaves work to do, which needs no word of its own.

**Nothing here moves a gate or sets a threshold.** ADR-047, ADR-080, ADR-083, ADR-084, ADR-086 and ADR-088 are untouched: every gate stays where it is, every threshold still ships unset, and the profile is still authored by a person and never guessed at. This decision is about what the operator is told and what is written down.

**Two operator messages are wrong today, independently of this.** A shut `SeedGate` logs nothing on step 7, so the silence is filled by `0 seed documents were extracted and none of them was usable. Fix the seed folder and run again.` — told to an operator who has named no seed folder, about documents that do not exist. And stage 4's gate message is emitted twice per invocation, from steps 5 and 6. Both are false or noisy regardless of how this record resolves, so they are fixed on their own ticket rather than held behind it.

**Whether four is the right number cannot be checked before a real archive** — like every other number in this map. What can be checked is the shape: that the sequence is four and says so, that an invocation ends by naming the next value, that no ordinal reaches the operator, and that a report informing a threshold names it.
