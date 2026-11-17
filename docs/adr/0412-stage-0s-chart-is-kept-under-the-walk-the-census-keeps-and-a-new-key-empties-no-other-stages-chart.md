# ADR-412 — Stage 0's chart is kept under the walk the census keeps, and a new key empties no other stage's chart

- **Date**: 2026-11-17
- **Status**: accepted on 2026-11-17.
- **Decided**: on [#861](https://github.com/algernon28/vespera/issues/861), a defect of [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md) §7 that none of its Known limits covers: what stage 0's chart is kept under while the census walks and once it hands the job its walk, and which walk's samples are written as they are taken (§1); how the samples of a walk the census discards are kept under the walk it keeps, its count done with them (§1, §2); that a stage's new key empties no other stage's chart, each later stage's chart answering for its own key alone (§3); and so that a stage going through finished under the key its file names keeps its frozen file (§4).
- **Amends**: ADR-382 §7, *"the corpus root's walk the census walked or continued, which `CensusTasklet` hands the job beside its runs and which the census's own lines name before it"*: until the census hands the job its walk, stage 0's chart is kept under the walk its lines name, written only where its file names that walk, and from then on under the walk handed (§1); and its second bullet, *"and the file the working directory holds of every stage after it is written over with no key, no `finishedAt` and no line"*: no other stage's file is written (§3).
- **Amends**: ADR-382 §5, *"for the census, the entries walked that the census's own line … says of the corpus root's walk"*: of the walk its lines name before it hands the job its walk, whichever walk it hands (§2).
- **Amends**: ADR-382's Consequences, *"A threshold retuned starts its stage's chart afresh, and empties the charts after it until the invocation reaches them"*, and its fifth Known limit, *"unless a stage before it began afresh"*: a later stage's chart is drawn as its file holds it until the invocation reaches its run (§3, Consequences).
- **Keeps**: ADR-382 §4, the file, its shape, its `format` 1 and that the job alone writes it; §6, a chart frozen once every step of its stage stands `finished` and never written again under its key; §7's first and third bullets, a run continued continuing its chart and a run put back freezing with one sample; §1 to §3 and §8 to §10, the window's charts, unchanged. [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md), an unfinished walk continued from its checkpoint; [ADR-064](0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md), the seed folder walked whether or not the corpus root's walk failed; and [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), every invocation walking the corpus root and a walk that observed what the walk before it recorded discarded, the earlier walk handed back. [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §2, a reading making no query; [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §4, a reading that fails failing nothing; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version naming `pipeline`.
- **Settles**: [#861](https://github.com/algernon28/vespera/issues/861).

## Context

**Two walks name stage 0's chart under ADR-382.** `JobSampler` keys the census's chart on `InvocationRuns.corpusWalk()`, which `CensusTasklet` records once `WalkRecorder` has walked the corpus root and before it walks the seed folder; until then, on the first walk the census's own lines name, *`Stage 0 (census): N entries walked so far, …, under walk W`*. Its count done at a sample is what those lines last said of the walk the key names, `entriesWalked` by that walk. ADR-382 §7 takes the two to name the same walk.

**They name two walks whenever the census discards the walk it walked.** Every invocation walks the corpus root (ADR-115): `WalkRecorder` continues the root's unfinished walk from its checkpoint (ADR-055) or mints a new one, and the lines name that walk. Once it finishes, a walk that observed what the walk finished before it recorded is discarded, its rows deleted, and the earlier walk is what `WalkRecorder.walk` returns and `CensusTasklet` hands the job. So over a corpus root unchanged since its last finished walk W, the lines name a walk W′ and the job is handed W.

**What follows under ADR-382 as written**, a working directory keeping stage 0's chart frozen under W and stages 1 to 3's frozen under runs R1 to R3:

1. At the first reading at which the census's lines name W′, stage 0's key is W′, which its file names otherwise: the chart is started afresh under W′, and §7's second bullet writes the files of stages 1 to 6b over with no key and no line.
2. At the first reading after the census hands the job W, the key is W again, which the file now names otherwise: the chart is started afresh under W, its count done read from the entries the lines said of W, which no line named. The census has finished, so the chart freezes with no point of `done`, and stages 1 to 6b are emptied again.
3. Stages 1 to 3, finished under R1 to R3, go through between two readings and are never seen `running`. At the next reading each one's file, emptied, names no key, so each is started afresh and freezes at once: one sample, its kinds from the runs reading at the start, no `done`, and the time it went through for its `finishedAt`. That is §7's *run put back* on runs nothing put back, and §7's first bullet, *"a frozen one stays as it is"*, does not hold.

**The first walk the lines name need not be the corpus root's.** `CensusTasklet` captures a failure of the corpus root's walk, hands the job no walk, and walks the seed folder all the same (ADR-064), under a walk of its own whose lines are said as the corpus root's are. Where the corpus root's walk fails before its first line, at fewer than 1,000 entries, the seed folder's walk is the first the lines name.

**The emptying compares a file, not a run.** ADR-382 §7 empties every later stage's file where a stage's key differs from what its own file names, so as to empty early a chart whose run the invocation is about to make new, since a later stage's run names the one before it upstream. But a file can name another key, or none, while the stage's run is unchanged: a file emptied as above, one a reader cannot read, or one of another `format`. The sampler cannot tell a run the invocation minted from one it continued without a query (ADR-268 §2) or a record `InvocationRuns` does not keep. Each such file empties the files of every stage after it, whose runs are unchanged, and those stages, finished, then freeze with one sample.

## Decision

### 1. Stage 0 is kept under the walk walked until the hand-over, and under the walk handed from then on

**The hand-over** is the census recording the corpus root's walk for the job, `InvocationRuns.recordCorpusWalk`, read as `corpusWalk()`. **The walk walked** is the first walk the census's own lines name in the invocation before the hand-over: the corpus root's walk as `WalkRecorder` continued or minted it, which the census may yet discard, or, where the corpus root's walk said no line, the seed folder's. **Before the hand-over, stage 0's chart is kept under the walk walked**, as the bullets below say; **from the hand-over on, under the walk handed alone**.

**Before the hand-over** the census's chart takes its samples as ADR-382 §5 has them, at each reading at which the census runs and at the invocation's last reading where it ran, as the samples of the walk walked:

- **where stage 0's file names the walk walked and is not frozen**, each sample is written to it as it is taken, after the file's points, as §7 continues a chart: a walk continued is drawn as the census walks. Under this record a file names a walk only where a census handed that walk over as the corpus root's, so the lines that name it are the corpus root's;
- **otherwise**, the file naming another walk, naming the walk walked frozen, or naming no walk, being absent, unreadable, of another `format` or keyed by nothing, the samples are held in the invocation's memory and the file is left as it is. A walk no file names is the corpus root's only once the hand-over shows it.

**At the first reading at which the census has handed the job its walk**, the walk handed decides the chart:

- **the file names the walk handed and is frozen**: it is left as it is (ADR-382 §6), and the samples held are dropped. A walk discarded as one the walk kept already recorded leaves the walk kept's frozen chart unchanged, as a finished step going through does.
- **the file names the walk handed and is not frozen**: the chart is continued by the samples held, after the file's points, their seconds as ADR-382 §5 has a continued chart's, the file's last second plus `vespera.progress-interval` plus the seconds since the first of them.
- **otherwise**: the chart is started afresh under the walk handed, **holding the walk walked's chart**: the points stage 0's file held where it named the walk walked, then the samples taken in this invocation. The samples of a walk the census discards are the walk it keeps.

Then the chart takes its samples and freezes as ADR-382 §5 and §6 have it, under the walk handed.

**A census whose corpus root's walk does not finish** keeps its samples: where its traversal stops early, `WalkRecorder.walk` returns the walk unfinished and `CensusTasklet` hands it over, so the chart is kept under it, not frozen, to be continued. **A census that ends before the hand-over**, the corpus root's walk failed or the process ended, drops the samples it held and writes nothing, so the seed folder's walk is never drawn as stage 0's. **A census whose lines name no walk before the hand-over**, a corpus root of fewer than 1,000 entries, takes its first sample at the reading after it, with no `done`, as ADR-382 has it.

### 2. The census's count done is what its lines said of the walk walked

**`done` at a sample of stage 0's chart is the entries the census's own lines last said of the walk walked**, whichever walk the census hands the job. The lines the census says after the hand-over, of the seed folder's walk, are no point of stage 0's `done`. Where its lines named no walk before the hand-over, `done` has no point (ADR-382's second Known limit).

### 3. A new key empties no other stage's chart

**A stage's new key starts that stage's chart afresh, and writes no other stage's file.** ADR-382 §7's emptying of the stages after it is struck: each stage's chart is started afresh, continued or left frozen at the first reading at which the invocation has reached that stage's own key, by comparing that key with what that stage's own file names, as §7's first bullet and the first half of its second have it. A stage whose run the invocation has not reached keeps its file, as it did.

### 4. A stage going through finished under the key its file names keeps its file

**A stage whose steps stand `finished` under the key its file names, frozen, leaves its file as it is**, whether or not a step of it was seen `running` (ADR-382 §6). No earlier stage writes over that file now (§3), so a stage going through under an unchanged run keeps its frozen chart. A stage going through finished under a key its file does not name is ADR-382 §7's run put back, unchanged: started afresh, it freezes holding one sample and no `done`.

**No file is repaired.** A chart frozen under its key is never written again under it, whatever it holds, one emptied or frozen with one sample among them.

### 5. The modules, and run ids

**In `pipeline`**: `JobSampler`, its stage-0 key, the walk walked's samples written or held and their hand-over, its `done` for the census and the emptying struck. `CensusTasklet` and `InvocationRuns` are unchanged: the census still hands the job the walk `WalkRecorder.walk` returns, and the sampler reads the walk walked from the lines it already admits. `corpus` and `WalkRecorder` are unchanged; `ui` is unchanged, drawing what the files hold. No stage's version names `pipeline` (ADR-222), so **no run id moves**. The chart file's `format` stays 1; `report.json`, its `format` and `report.html` are unchanged.

## Alternatives refused

- **Every sample held until the hand-over, a walk continued too.** The corpus root's walk continued across invocations is the longest the census makes, and it would draw nothing on stage 0's chart until it ended.
- **A first census written as it walks, into a file that names no walk.** Where the corpus root's walk fails before its first line, the walk walked is the seed folder's (Context), and its counts would be drawn as stage 0's, not frozen, until a later census handed a walk over.
- **A record of the hand-over made where the corpus root's walk fails too, so that the lines after it are known for the seed folder's.** It would let a first census be drawn as it walks, at the cost of a change to `CensusTasklet` and `InvocationRuns` for that alone; the steps' bars and the output show a first census meanwhile, and its chart is drawn whole at the hand-over.
- **The walk walked's samples written over every file, and the frozen file written back at the hand-over.** The window would draw a frozen chart replaced and then put back; and a census failing before the hand-over would leave a frozen chart written over by a walk the next invocation may discard.
- **The ledger asked at the hand-over whether the walk walked was discarded, and into which walk.** A query at a reading (ADR-268 §2); and the walk handed already says it, a walk handed other than the walk walked being the one the census kept in its place.
- **The emptying kept for a run-keyed stage alone.** It compares the stage's file, not its run: a file that names no key, or another, while the run is unchanged would still empty every later stage's chart whose run is unchanged (Context), and telling a run minted from one continued needs a query or a new record.
- **The emptying kept and the emptied files written back once the invocation reaches their runs unchanged.** The sampler would hold every later stage's file through the invocation to undo a write that need not be made; and an invocation stopped before reaching them would leave them emptied.

## Consequences

- **A census over a corpus root unchanged since its last finished walk leaves stage 0's frozen chart, and every later stage's chart frozen under an unchanged run, as they were.**
- A walk continued is drawn as the census walks; a walk the census walks for the first time is drawn at the hand-over, whole, from the samples held; a walk the census discards is drawn under the walk it keeps where that walk's chart is not frozen, with the entries the census said of it.
- While the census walks a walk its file does not name, the window draws stage 0's file as it is until the hand-over; the steps' bars and the output show the walk.
- **A stage's chart is drawn as its file holds it until the invocation reaches its run**, even where a stage before it began afresh: ADR-382's fifth Known limit holds without its *"unless"*, and a retuned threshold no longer empties the charts after its stage.
- A file already holding a chart emptied, or frozen with one sample, stays as it is under its key (§4).

## Known limits

- **A census walking a walk its file does not name draws nothing on stage 0's chart until the hand-over**, a first census of a working directory among them.
- **A census that ends before the hand-over keeps none of the samples it held**, its walk failed or its process ended.
- **A later stage's chart of an earlier run is drawn, frozen with its time, until the invocation reaches the stage**, while a stage before it works under a new run (Consequences).
- **The census's `done` stops at its last running line**, *`N entries walked so far`*, and not at the walk's end line, which ADR-382 §5 does not admit.

## What this does not decide

- **The walk's end line, *`walk W finished — N entries walked`*, as the census's last point of `done`.** ADR-382 §5 admits the running line alone, and this record leaves it so.
- **A way to tell the window that a later stage's chart is of a run the invocation will replace.** It draws what the files hold (ADR-382 §4).
- **A repair of a file written before this record**, or any deletion of one by the job.

## Tests

Where a reading has to fall between two of the census's lines, or between its lines and the hand-over, the test makes, on its own thread, the reading the sampler's thread makes at each interval, on a clock it moves on 10 s between two readings.

| Method | What it holds |
| --- | --- |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.aWalkTheCensusDiscardsLeavesEveryFrozenChartAsItWas` | §1, §3, §4: a working directory keeping stage 0's chart frozen under walk 46 and stages 1 to 3's frozen under their runs; an invocation whose census's lines say 28,000 entries under walk 82, a reading after them, and whose census fails before the hand-over leaves the four files as they were; a second, whose census's lines say 45,000 under walk 82, a reading after them, and which then hands the job walk 46 and finishes, stages 1 to 3 finished under the runs their files name, leaves them as they were |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.theChartOfAWalkTheCensusDiscardsIsKeptUnderTheWalkItKeeps` | §1, §2: no chart of stage 0; an invocation whose census's lines say 28,000 under walk 82 writes nothing at the reading after them, then hands the job walk 82 unfinished and is stopped, and its chart is kept under walk 82, not finished, `done` 28,000 at seconds 0 and 10; a second, whose lines say 45,000 under walk 82, writes it at the reading after them at second 310, then hands the job walk 46, says 2,000 under the seed folder's walk 83 and finishes, and the chart is under walk 46, frozen at the census's end, `done` 28,000, 28,000, 45,000 and 45,000 at seconds 0, 10, 310 and 320 |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.aWalkTheCensusDiscardsContinuesTheChartOfTheWalkItKeepsWhereItIsNotFrozen` | §1, §2: stage 0's chart under walk 46, not finished, `done` 10 and 20 at seconds 0 and 5; an invocation whose census's lines say 45,000 under walk 82 leaves the file as it was at the reading after them, then hands the job walk 46 and finishes, and the chart is continued by the sample held and the last one, `done` 10, 20, 45,000 and 45,000 at seconds 0, 5, 305 and 315, frozen at the census's end |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.aCensusThatHandsTheJobNoWalkDrawsNothingOfTheSeedFoldersWalk` | §1: no chart of stage 0; an invocation whose census hands the job no walk, its lines saying 2,000 under the seed folder's walk 83 alone, a reading after them, and which fails writes no chart of stage 0 |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.aNewRunStartsTheStagesChartAfreshAndEmptiesTheChartsAfterIt` | §3, under the name ADR-382's Tests give it: a working directory keeping stage 1's, stage 2's and stage 3's charts, frozen under their runs; an invocation whose extraction runs under another run and is stopped writes stage 2's afresh under it, its one sample this invocation's and no `finishedAt`, and leaves stage 1's and stage 3's as they were |

`EachStagesChartIsKeptUnderItsRunTest.anInvocationUnderTheSameRunContinuesTheChartAndOneFinishedStaysFrozen` (ADR-382) stands unchanged.

## What the implementation owes

- `JobSampler`: stage 0's key, the walk walked until the hand-over and `InvocationRuns.corpusWalk()` from it on; the walk walked, the first walk the census's lines name before the hand-over, its samples written or held as §1 says and handed over at the first reading after the hand-over, held ones dropped where the census ends without one; the census's `done` from the entries its lines said of the walk walked (§2); the emptying of the later stages' files removed (§3). Its javadoc, and `keepTheCharts`', cite this record beside ADR-382.
- `JobSampler.scheduledWrite`, the reading its own thread makes at each interval, keeps its name and its lack of parameters: the tests above make a reading between two of the census's lines through it.
- The tests above passing.
