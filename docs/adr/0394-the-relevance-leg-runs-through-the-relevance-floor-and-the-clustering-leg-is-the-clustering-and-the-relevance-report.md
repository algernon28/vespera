# ADR-394 — The relevance leg runs through the relevance floor, and the clustering leg is the clustering and the relevance report

- **Date**: 2026-11-15
- **Status**: accepted on 2026-11-15.
- **Decided**: by the operator, on 2026-11-15: that the second leg keeps the name ADR-392 gave it, `relevance`, and holds `seed-extraction`, `seed-corpus-comparison`, `embedding-scoring`, `relevance-scoring` and `relevance-floor` (§1); that `relevance-report` goes with `relevance` only where the legs stay consecutive in job order, as ADR-324 §1 has them, so that it stays in `clustering`, the job running it after the clustering (§2); that `deduplication` and `generation` keep their steps; and that a leg's name stays a noun of what the leg does (§1). Under the operator's delegation: that `report.json` stays of format 2, its schema unchanged (§3), and what each record and test comes to (§4, Tests).
- **Amends**: [ADR-324](0324-the-timeline-of-the-steps-folds-into-four-legs-each-one-bar-open-where-the-job-is-and-a-fold-outlives-the-reload-in-the-pages-address.md) §1, whose table, as ADR-392 §3 amends it, ends `relevance` at `embedding-scoring` and opens `clustering` at `relevance-scoring`: `relevance` runs through `relevance-floor`, `clustering` is `clustering` and `relevance-report`, and the boundary inside stage 5 lies after `relevance-floor` (§1). Its sentence *"The relevance report is clustering's"* stands, with §2's reason beside its own. It carries a note at its head.
- **Amends**: [ADR-361](0361-the-cockpits-steps-fold-into-the-progress-pages-four-legs-and-its-copy-of-their-table-is-held-to-the-pages-by-a-test.md) §1, whose `Legs.ALL`, as ADR-392 §3 amends it, holds `relevance` with three steps and `clustering` with four: it is §1's table, over the fifteen steps of `StopCosts.ROWS` in its order (§1). It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, whose `legs` and each step's `leg` follow ADR-324 §1's table: they follow §1's, and `format` stays `2` (§3). It carries a note at its head.
- **Amends**: [ADR-392](0392-vespera-finds-no-translation-sets-the-translation-step-its-verdict-its-report-and-its-two-profile-keys-go.md) §3, whose table of the legs puts `relevance-scoring` and `relevance-floor` in `clustering` and whose boundary inside stage 5 lies after `embedding-scoring`: both steps are `relevance`'s and the boundary lies after `relevance-floor` (§1); and its *What this does not decide*, whose `clustering` keeps its steps until a record of its own regroups them: this is that record. It carries a note at its head.
- **Keeps**: ADR-324 §1's rule that each leg is steps of the job consecutive in job order and every step lies in exactly one, a leg's name written as it is; its §3 and §4, a leg's bar, its state and which legs are open; ADR-361 §2, the control panel's copy held to the report's by a test; [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) §7's fifteen step names and the job's order, `InvocationReport.STEPS_IN_JOB_ORDER`; ADR-392 §3's four leg names, `relevance` written as a literal that `RunsAndNamesHaveOnePlaceTest` admits; ADR-362 §3's rule for raising `format`; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version naming `pipeline` or the control panel.
- **Settles**: [#770](https://github.com/algernon28/vespera/issues/770).

## Context

ADR-392 §3 renamed the second leg `relevance` and left it the three steps the translation step had followed: `seed-extraction`, `seed-corpus-comparison` and `embedding-scoring`. The step that gives each survivor its relevance score, `relevance-scoring`, and the step that removes the survivors below the relevance floor, `relevance-floor`, opened `clustering`, so the two steps that decide relevance were drawn outside the leg named for it.

Stage 5 runs seven steps, in this order (ADR-157 §7, `InvocationReport.STEPS_IN_JOB_ORDER`): `seed-extraction`, `seed-corpus-comparison`, `embedding-scoring`, `relevance-scoring`, `relevance-floor`, `clustering`, `relevance-report`. With the translation step gone, nothing lies between `embedding-scoring` and `relevance-scoring`. ADR-324 §1 folds the job into legs each of which is steps of the job consecutive in job order, every step in exactly one, and ADR-324 §3 draws a leg's bar from the earliest start of its steps to the latest end.

## Decision

### 1. The four legs

**The job's fifteen steps fold into four legs** (ADR-324 §1, ADR-361 §1), in this order:

| Leg | Its steps, in job order | Where it lies in the cascade |
| --- | --- | --- |
| `deduplication` | `census`, `byte-level-reduction`, `extraction`, `content-census`, `redundancy-signature`, `content-redundancy` | stages 0 to 4 |
| `relevance` | `seed-extraction`, `seed-corpus-comparison`, `embedding-scoring`, `relevance-scoring`, `relevance-floor` | stage 5, from the seeds through the relevance floor |
| `clustering` | `clustering`, `relevance-report` | stage 5, after the relevance floor |
| `generation` | `arrangement`, `generation` | stage 6 |

**The names stay**: each is a noun of what its leg does, `relevance` the leg that measures the survivors against the seeds and removes those below the floor. Each boundary but one is a stage's; the one inside stage 5 lies after `relevance-floor`.

`InvocationReport`'s legs and the control panel's `Legs.ALL` hold this table, and `TheControlPanelsLegsAreTheReportsTest` holds the one to the other. `relevance` is still the name of no step and no stage and is written as a literal; `clustering` and `generation` are still read from `StepNames`.

### 2. The relevance report stays in `clustering`

**`relevance-report` would go with `relevance` were the legs to stay consecutive with it there; they would not.** The job runs it after `clustering`, so a `relevance` holding it would hold `relevance-floor` and `relevance-report` with `clustering` between them, and `clustering`, a leg of one step, would lie inside another: ADR-324 §1's rule would not hold, and `relevance`'s bar, from its earliest start to its latest end (ADR-324 §3), would cover the clustering step of another leg. It stays in `clustering`, where ADR-324 §1 put it, and the job's order is not moved for a drawing: a leg judges nothing, mints no run and records nothing (CONTEXT.md).

### 3. `report.json` stays of format 2

**ADR-362 §3 raises `format` for a change that removes, renames or changes the meaning or type of a field, and this change is none of them.** `legs` holds the same four legs, by the same names, in the same order, each with `name`, `state` and `open`; each step's `leg` is still the name of the leg it is drawn in, one of the four. What changes is values: the `leg` of `relevance-scoring` and of `relevance-floor` reads `relevance` where it read `clustering`, and each leg's `state` and `open` are read, by ADR-324 §3 and §4, off the steps §1 gives it. `docs/report/report.schema.json` names no leg and no leg's steps, and does not change; `InvocationReport.FORMAT`, the schema's `const` and `InvocationReportFile.FORMAT` stay `2`.

A `report.json` of format 2, or a copy of one in `logs`, that an earlier version wrote carries ADR-392 §3's table in its `legs` and its steps' `leg`. The window reads it as it reads any of format 2: it folds the steps by `Legs.ALL` and reads each leg's state and whether it is open off the steps (ADR-361 §4, §5), and a row of the list of working directories names the furthest leg the copy's `legs` reached, as written (ADR-393 §5).

### 4. The modules, and run ids

`InvocationReport`, of `pipeline`, and `Legs`, of the control panel, change; no stage's version names either (ADR-222, `StageModules`), **so no run id moves** and no stage is redone. The charts of each stage (ADR-382) are drawn by stage, not by leg, and do not change.

**What the operator sees**: in the window and on `report.html`, `relevance`'s bar runs from the seed extraction's start to the relevance floor's end, and its percent, folded, is the mean of its five steps' fractions (ADR-365 §5, ADR-370); a job running, failed or first stopped at a gate at the relevance scoring or the relevance floor opens `relevance` where it opened `clustering` (ADR-324 §4, ADR-361 §5); `clustering` is the clustering and the relevance report.

## Alternatives refused

- **ADR-392 §3's table**, `relevance-scoring` and `relevance-floor` opening `clustering`. The relevance score is computed and its floor applied outside the leg named `relevance`.
- **A leg named `scoring`** for the second, as #770's proposed layout drew it. `relevance` is what the five steps decide, and it is the name the report and the window have drawn since ADR-392.
- **`relevance-report` in `relevance`, the legs no longer consecutive.** §2: a leg would lie inside another, and a bar would cover a step that is not its leg's.
- **Moving `relevance-report` before `clustering` in the job**, so that it could join `relevance`. It changes what the job runs to change how it is drawn; this record decides no step's place.
- **Raising `format` to 3.** No field is removed, renamed or changed in meaning or type (§3); a reader of format 2 reads the new file as it read the old.

## Consequences

- The legs are `deduplication`, six steps; `relevance`, five; `clustering`, two; and `generation`, two: fifteen, each in one.
- A leg holding the step that decides relevance is named for it; `clustering` is the clustering and the page the relevance threshold is chosen from.
- `report.json` is of format 2, with no field added or removed; its schema is as it was.
- No run id moves.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheSecondLegIsRelevanceTest.theFourLegsAreDeduplicationRelevanceClusteringAndGeneration` | `Legs.ALL`'s four names and their steps, §1's table: `relevance` holding five steps through `relevance-floor`, `clustering` holding `clustering` and `relevance-report` (§1) |
| `pipeline.TheControlPanelsLegsAreTheReportsTest.theControlPanelsTableOfLegsIsTheReports`, unchanged | the report's `legs` and each step's `leg` are `Legs.ALL`, leg by leg, the four together the job's steps once each, in order (§1, §2) |
| `pipeline.InvocationReportTest.eachLegsStateAndWhetherItIsOpenAreAdr324sInEveryJob` | for six jobs, each leg's `state` and `open` as ADR-324 §3 and §4 decide them over §1's table, and each step's `leg`; the job running past a gate stopped at the seed-corpus comparison's gate in `relevance` and running the clustering in `clustering` (§1) |
| `pipeline.TheControlPanelsLegsAreTheReportsTest.eachLegsStateAndWhetherItIsOpenAreTheReportsInEveryJob`, unchanged | for the same six jobs, `Legs.statesOf` and `Legs.openOf` are the report's (§1) |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.aFoldedLegsBarCarriesTheWholePercentOfItsBarAndAnOpenLegsNone` | first stopped at a gate in `relevance`, the seed-corpus comparison at 2 of 4: `relevance` shows 90%, the mean of its five steps (§4) |
| `ui.EachWorkingDirectoryIsARowOfWhatItsReportSaysTest.aRowShowsItsNameRootLastInvocationLegAndNextValue` | a run that wants `relevanceScoreFloor`, `relevance` stopped at a gate: the row's furthest leg is `relevance, stopped at a gate` (§3, ADR-393 §5) |
| `pipeline.TheReportIsOfFormatTwoTest.theJarTheSchemaAndTheWindowSayFormatTwo`, unchanged | `InvocationReport.FORMAT`, the schema's `format` and `InvocationReportFile.FORMAT` are 2 (§3) |

## What this does not decide

- **The job's order**, the relevance report after the clustering, which is ADR-157 §7's and ADR-269 §5's.
- **Whether `report.json` names each leg's steps**, so that a reader need not hold its own copy of §1's table.
