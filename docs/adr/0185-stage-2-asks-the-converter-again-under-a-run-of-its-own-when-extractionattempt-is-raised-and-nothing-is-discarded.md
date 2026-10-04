# ADR-185 — Stage 2 asks the converter again under a run of its own when `extractionAttempt` is raised, and nothing is discarded

- **Date**: 2026-10-04
- **Status**: accepted
- **Amends**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §5, third bullet. *"The retune path is the ledger's usual one, unchanged: discard stage 2's rows for the run and run it again"* becomes: the retune path is the ledger's usual one, **a new run beside the old one**. The operator raises `extractionAttempt` in the profile (§1), and nothing is discarded (§3). The rest of the bullet, and the rest of §5, stand.
- **Amends**: [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) §6. Its first paragraph stays true as a description of ADR-181's resume, but it no longer describes the retune path: deleting the `finished_step` row is not a procedure (§5). Its second paragraph, the open question, is decided here. Its "What this does not decide" entry *"A retune procedure"* is decided here. Its entry on a resume over repeated refusals gains a fourth way out beside the three it lists: raising `extractionAttempt`, which needs no change of floor, converter or build (§6).
- **Amends**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md), Consequences, the sentence *"The retune path is unchanged and is the one the ledger model already has: delete that stage's rows for the run, and run it again."* No rows are deleted. The path is §1's.
- **Amends**: [ADR-143](0143-an-uncategorised-conversion-failure-is-a-verdict-against-the-file.md), Consequences, *"deleting them and re-running stage 2 is the retune path the ledger already has"* and *"converts nothing already marked `extraction-failed` until those verdicts are deleted"*. An `unknown` failure is a document-scope answer, which the extraction cache keeps (ADR-183 §1), so raising `extractionAttempt` does not ask about it again (§2). What asks again is a different converter: a new extractor identity is a new cache key and a new stage-2 run, and under [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md) the old run's verdicts remove nothing from it. No verdict has to be deleted for either.
- **Amends**: [ADR-145](0145-a-tables-cells-are-extracted-text-read-once-in-docling-reading-order.md), Consequences, the second way out, *"delete the old stage-2 run's `degenerate-output` verdicts and re-run"*. Since ADR-156 the new stage-2 run that change minted is not removed from by those verdicts, so there is nothing to delete.
- **Rests on**: [ADR-048](0048-walk-and-run-identity.md) and [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (a run id folds in what the stage consumed, and a dependency that matters is recorded there), [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md) (the profile is a record, and census adds a new key unset), [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) (a regenerated measurement is a fresh row set under its own id), [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) (an approval names one arrangement), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (6b under the same id repairs and does not regenerate), [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) (a profile value read fresh when its run is minted), [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md) (an unreadable number), [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) and [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md) (a later stage reads the run this invocation arrived at, and a verdict under any other run removes nothing), [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) §3 (only a different run id redoes the whole stage), [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) §1 (what the extraction cache keeps).
- **Settles** [#386](https://github.com/algernon28/vespera/issues/386).

## Context

### What the ticket found

ADR-140 §5 said a `capacity` removal that the conversion width provoked is "answerable by a query rather than by re-converting the corpus", and that the retune is "discard stage 2's rows for the run and run it again". #386, found while gating #383, points out two gaps:

1. **No procedure exists.** No command, README section or documented SQL says how to discard stage 2's rows for a run.
2. **Later stages stay finished.** If the operator deletes stage 2's `finished_step` row, stage 2 runs again under the **same** run id. Stage 3's run id hashes stage 2's (`StageRuns.contentCensus` puts `extractionRunId` into its `ContentCensusConfigConsumed`), so it does not change either. Stage 3 and every stage after it keep their own `finished_step` rows and do no work. What stage 2 now writes differently never reaches them.

ADR-183 removed a third gap: a cached refusal is no longer served, so a re-run does reach the sidecar.

### What the record already says, read together

Four records written after ADR-140 change what "discard and run again" can mean.

- **[ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md)**: a verdict under a run this invocation did not arrive at removes nothing. So a **new** stage-2 run never needs the old run's verdicts deleted. ADR-139, ADR-143 and ADR-145 each told the operator to delete verdicts. They were written when a verdict removed a file occurrence under every run, and ADR-156 ended that.
- **[ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md)**: deleting the `finished_step` row of the `extraction` step is the state a lost end of step leaves. The next invocation deletes the fault rows and the verdicts that resolved them, and reads only the faulted occurrences again, under the same run. Its §3 says that a different run id is the only thing that redoes the whole stage.
- **[ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md)**: the extraction cache keeps a conversion and a failure the converter blamed on the document. It never keeps a refusal the converter blamed on itself, or a timeout. So any stage-2 run, new or resumed, asks the sidecar again about exactly the occurrences it got no answer about, and is served everything else.
- **The verdict rule in `CONTEXT.md`**: a verdict is deleted only by the step that wrote it, redoing its own unfinished work under the same run. An operator's discard is not that step.

### What the operator can do today, and what each costs

| Route | What it asks the sidecar again | What happens downstream |
|---|---|---|
| Delete the `finished_step` row of `extraction` under the run, by hand | the faulted occurrences only | Stage 3 onward keep their run ids and their `finished_step` rows, so they do no work and never see the answers. |
| Delete that and every later step's `finished_step` row, by hand | the faulted occurrences only | Stages 3 to 5 redo their work under the same ids. **6a re-arranges under the id the operator already approved**, so the approval opens 6b over an arrangement nobody read, which ADR-107 names a specific arrangement to prevent. **6b keeps every synthesis doc it wrote** (ADR-111), over cluster memberships that may have changed. |
| Change the degeneracy floor | everything uncached, under a new run | Every later stage runs under a new id. But the floor is a judgement about conversion quality, and changing it to get a new run changes that judgement too. |
| Change the extractor identity (a new image) | every occurrence, because the cache key changes | Every later stage runs under a new id, at the cost of converting the whole corpus again. |
| Install a build that moves `extraction` | everything uncached, under a new run | Every later stage runs under a new id. The operator does not control when it happens. |

The new-run routes are sound, because every later stage's id names the stage-2 id it read, and a new id upstream is a new id all the way down. None of them can say "ask again" without saying something else as well. The same-id routes say only "ask again", and are unsound from 6a onward.

## Decision

### 1. The retune path is a new stage-2 run, minted by a profile key, `extractionAttempt`

The profile gains one key, **`extractionAttempt`**, a number (`NumericValue`, ADR-120). It joins stage 2's run identity and nothing else:

- **Unset, unreadable, or `1`**: stage 2's identity is exactly what it is today. `ExtractionConfigConsumed` serialises to the same text, character for character. `RunIdentityGoldenTest.extraction` pins that text, and it does not change.
- **Any other number**: the number is appended to `ExtractionConfigConsumed` as its last member, `"extractionAttempt"`, written as the JSON number Jackson writes for a `Double` (so `2` is recorded as `2.0`). A different number mints a different stage-2 run. `2` and `2.0` are the same attempt.

`1` is the first attempt, so writing it changes nothing. The operator can therefore start counting at the run they already have. Without that rule, writing `1` would mint a new run and replay every stage for nothing.

**An unreadable value is ignored, as ADR-120 ignores one in every numeric key**: stage 2 arrives at the first attempt's run. The closing line names it in the words it uses for the other two floors that are not run values. `NextAction` reports it after `theLogFloorUnreadable`, in the shape *"Every run value is set, but extractionAttempt reads "two", which is not a number, so this run ignored it and extracted under the first attempt. Next: write a whole number into extractionAttempt in profile.yaml, or remove it, and run again."*

**It is read when stage 2's run is minted, not when the application starts.** `StageRuns.extraction()` reads it from `ProfileStore`, as `embeddingScoring()` reads the relevance floor (ADR-117). It is not a singleton bean built at start-up the way `DegenerateOutputConfidenceFloor` is. In production each invocation is a new process, so the difference does not show. In the test slice the application context outlives several invocations, and a value read once at start-up would never change between them.

**Why a profile key, not a command.** A run id is derived from what a stage consumed. The next `vespera run` has to arrive at the same run as the one that asked again, or it goes back to the old run, and the arrangement, the approval and the deliverable go back with it. So whatever asks again has to be persisted, and has to be an input to stage 2's id. The profile is the place an operator's persisted inputs already live, and every other stop already asks the operator to edit it. A command would only write the profile value for them. It would also add a third subcommand, for a step taken perhaps once per archive.

**Why it is a judgement the engine cannot make for itself** (`CONTEXT.md`, Profile). Whether a run of `capacity` refusals was the sidecar's load or something about the files, and whether the answers are worth a replay of every later stage, is the operator's call. They make it after reading `extraction-failures.html`, and record why in `provenance`.

**It is not a gate.** Unset means the first attempt, and an unset value never ends an invocation. `generationModel` and `generationContextWindow` are the precedents for a key whose unset state means "a default applies".

### 2. What a raised attempt asks again, and what it does not

A new stage-2 run has nothing recorded under it, so it reads every survivor of the stage-1 run it names (ADR-181 §1 and §3). Each occurrence goes one of two ways:

- **Answered from the extraction cache, with no call**: a conversion, and a failure the converter blamed on the document (`backend_failure`, `inference_failure`, `policy`, `source_unavailable` and `unknown` with no service-scope category beside them, and a failure with no error) (ADR-183 §1). The new run records these exactly as the first run did.
- **Asked again**: everything the cache does not keep. That is a refusal the converter blamed on itself (`capacity`, `target_unavailable`, `internal`), a timeout Docling reported, a timeout of the call itself, and a call that failed outright (ADR-175). These are exactly the occurrences the old run could not get an answer about. Whatever the sidecar says now is this run's answer.

**A document-scope answer is not asked again, on purpose.** The same converter, asked the same question about the same bytes, gives the same answer. Not having to ask again is the reason the cache exists. An operator who believes a document-scope answer is wrong is saying the converter is wrong, and the remedy is a different converter. A new image is a new extractor identity, so it is a new cache key and a new stage-2 run, and ADR-156 keeps the old run's verdicts from removing anything under it. That is ADR-143's case, amended above.

**The attempt does not join the extraction cache's key.** If it did, every occurrence would be converted again, which on the archive is the whole of stage 2's conversion time, to re-ask the few that were refused.

### 3. Nothing is discarded

A raised attempt deletes no row. Here is what it does with each kind of row, since #386 asked:

| Rows | Under the old stage-2 run | Under the new one |
|---|---|---|
| `finished_step` | kept. The old run stays finished, and an invocation that arrives at it again does no work | written when the new stage completes |
| `extraction_metric`, `shingle` | kept | written afresh for every occurrence the new run measures, from the cached conversion or the new answer |
| `verdict` (`extraction-failed`, `degenerate-output`) | kept, and removes nothing from any run that does not name the old run upstream (ADR-156) | written afresh |
| `extraction_fault` | kept. Together with the old run's verdicts they record what the old run could not get answered | written for whatever the sidecar refuses again, and resolved under ADR-139 if the step completes |
| `extraction_cache` | not owned by any run (ADR-070). Every row stays | a new answer that §1 of ADR-183 keeps is written. It replaces a row only where it replaces a refusal an earlier build stored (ADR-183 §2) |
| every later stage's rows, and its `finished_step` | kept, under the old later runs | written under new later runs (§4) |
| `deliverable/<run>/` | the old generation run's tree stays on disk | a new tree is written under the new generation run's id |

The cost of keeping everything is disk: a second set of stage-2 metric and shingle rows, the largest tables the ledger holds. That is the cost ADR-077 already accepts for any regenerated measurement. The working directory is the operator's to delete when it is no longer wanted.

### 4. Every later stage runs again under a run of its own, because its id names stage 2's

Nothing new is needed for this. Stage 3's identity includes the stage-2 run id it read, stage 4's includes stage 3's, and so on to 6b (ADR-048, `StageRuns`). A new stage-2 run is therefore a new run of every later stage. Each of them runs as it would on a first invocation, under the rules it already has:

- **Stages 3 and 4** measure and judge over the new stage-2 run's survivors, the occurrences the new run converted included.
- **Stage 5**: seed extraction runs under the new seed-measurement run, so a seed the converter refused while blaming itself is asked again too (ADR-183, Consequences). Stored vectors are keyed by content and instrument, outside any run, so they are reused (ADR-085). Relevance labels belong to the documents, so they outlive the run (ADR-169).
- **6a** makes a new arrangement under a new id. `arrangementApproved` names the old one, so the gate is shut, and the closing line asks the operator to read the new `arrangement.html` and approve it (ADR-154 §2). This is the point of ADR-107's naming a specific arrangement, and the reason the same-id route is refused.
- **6b** writes a new deliverable under the new generation run.

This is a replay of every stage after stage 2, and it is the honest price. What stage 2 now converts can change what stage 3 counts, what stage 4 keeps, what stage 5 scores, how the documents group and what is written about them. No stage after 2 can take the new answers without reading the whole set again.

### 5. Putting the value back arrives at the old runs again

Removing `extractionAttempt`, or writing `1`, gives stage 2 its first identity again. The next invocation arrives at the first attempt's runs, all finished, and does no work. Raising it to `3` mints a third run beside both. Writing `2` again arrives at the second attempt's runs. This is ADR-156's "putting a value back" and costs nothing.

**Deleting rows by hand is not a procedure, and no command will do it.** Deleting the `extraction` step's `finished_step` row puts the run in ADR-181's lost-end-of-step state. The next invocation asks again about the faulted occurrences under the same run and leaves every later stage finished, so the answers reach nothing (Context). Deleting the later rows as well makes 6a re-arrange under an approved id (Context). The README says to raise `extractionAttempt` instead.

### 6. The way out of ADR-183's repeated-refusal wedge

ADR-183's "What this does not decide" names one case it leaves: five or more refusals the sidecar repeats for the same files, read back to back on a resume, stop the step on every invocation of that run. It names three ways out, all of which change something else as well: a new floor, a new converter, or a new build. Raising `extractionAttempt` is a fourth. A new run reads every survivor in its place, as a first run does, so the refused occurrences sit among converted ones again. Whether a resume should read them that way itself is [#385](https://github.com/algernon28/vespera/issues/385), and nothing here depends on how it is settled.

### 7. What the README tells the operator

`extractionAttempt` joins the README's table of values, beside `degenerateOutputConfidenceFloor`, read off `extraction-failures.html`, and "Four of these are **optional**" becomes five. `docs/check-claims.mjs` requires every profile key to be named in the README, so the README change ships with the `Profile` change. The paragraph it adds, in the README's own voice:

> `extractionAttempt`, left unset, is the first attempt at extracting text from your archive. Some files may not have been read because the converter was busy, failing or too slow at the time. `extraction-failures.html` lists those files beside the ones it could not convert. To have the converter asked about them again, write `2` into `extractionAttempt`, with why in `provenance`, and run again. Files it already answered about are not converted again. Everything after extraction is redone, because what was read may have changed: deduplication, scoring, grouping and the connecting text. The groups are new, so you approve the arrangement again before anything is written. Nothing from the first attempt is deleted. Remove the value, or write `1`, and the next run is back on the first attempt, with nothing redone. Next time, write `3`. Do not delete rows from `vespera.db` to make a stage run again.

## Consequences

- **#386 is closed by one profile key and one line of identity.** No subcommand, no SQL in the README, and no change to how any later stage derives its id.
- **Implementing this moves no stage-2 run id.** The key lives in `profile`, its reading in `pipeline`, and an unset value serialises as today. Stage 2's implementation version names `extraction` and `similarity` only. Every later stage names `pipeline`, so the implementing commit moves their ids, and they replay once on the first build that ships it. Every `pipeline` commit does that (ADR-058), and this record adds nothing to it.
- **A raised attempt costs a replay of stage 2 over the cache, plus every later stage.** On the archive, stage 2's share is SQLite time for metrics and shingles. ADR-181 measured that at about 80 minutes for 70% of the survivors before ADR-182 took the by-hash index out of stage 2. Its share of sidecar time is one call per occurrence the old run got no answer about. The later stages cost what they cost on any invocation that reaches them, 6b's writing calls included. The operator chooses when to pay it. `extraction-failures.html` says how many files are involved before they decide.
- **ADR-140 §5's three bounds now end in a lever.** A `capacity` removal the width provoked is findable by its category (ADR-139) and is no longer served from the cache (ADR-183). Now the operator can also act on it without touching the code, the image or a judgement.
- **`CONTEXT.md` needs no new term.** *Run* already says it is "minted when the configuration changes". *Verdict* already says who may delete one, and this record deletes none. `extractionAttempt` is a profile key, named like the others.
- **`ProfileFixture`** (a test helper) builds a `Profile` positionally, so it gains the component in the same change, with a builder method beside the others.

## Tests

**`ExtractionAttemptInvocationTest`** (`src/test/java/io/algernon/vespera/pipeline/`) drives whole invocations over `ConverterStopsPartwayBeans`, as `ServiceScopeRefusalInvocationTest` does, with nothing emptying `extraction_cache` between invocations. The profile names nothing else, so every invocation ends after stage 3. It writes `extractionAttempt` into `profile.yaml` through the YAML tree rather than through `ProfileFixture`, so that it compiles before the key exists.

1. **A raised attempt asks again only what got no answer, and discards nothing.** The first invocation completes, with one occurrence the converter blamed on itself and one it could not convert. With the converter now converting everything and `extractionAttempt` at `2`, the next invocation mints a second stage-2 run. Its recorded settings are the first run's with `"extractionAttempt":2.0` appended last. It asks the converter about the refused occurrence and nothing else, and measures it. It removes the unconvertible one again, from the cached answer. The first run's `finished_step`, metric, shingle, verdict and fault rows are exactly as they were. The extraction cache gains exactly one row.
2. **Every later stage runs again under a run of its own.** After the same two invocations, content census has two runs. The newer one names the second stage-2 run upstream, is finished, and counts one more shingled document than the first. The first content census is still finished.
3. **Putting the value back arrives at the first attempt again.** After the same two invocations, `extractionAttempt` is removed. The third invocation mints no run of either stage and asks the converter nothing. The review list it writes names the refused occurrence again, as the first attempt removed it.
4. **An attempt of `1` is the first attempt.** Written after the first invocation, it mints no run and asks the converter nothing.
5. **An unreadable attempt is ignored.** `two`, written after the first invocation, mints no run, asks the converter nothing, and the invocation completes.

**`NextActionTest.aMistypedExtractionAttemptIsReportedToo`**: with every run value set and `extractionAttempt` reading `two`, loaded from YAML through `ProfileStore`, the closing line names the key, says it is not a number, quotes `two`, and is one line.

**`RunIdentityGoldenTest.extraction`** is unchanged. It pins that an unset attempt leaves stage 2's recorded settings character for character as they are.

Every test above but the last fails before this is built. `ProfileStore` refuses a profile with a key it does not know (#321), so each invocation after `extractionAttempt` is written ends without running a stage, and the `NextActionTest` case fails to load its profile.

## What this does not decide

- **Asking again about one category only**, such as `capacity` alone. A raised attempt asks again about everything the cache does not keep. No record has wanted it narrower, and the narrower ask would need a second identity input.
- **A raised attempt for a stage other than 2.** Stage 6b already asks again under the same id, in a repair pass (ADR-111). No other stage has been seen to need it.
- **Whether a resume reads faulted occurrences in their place** ([#385](https://github.com/algernon28/vespera/issues/385)). §6 is a way out that does not depend on it.
- **A count of what a raised attempt will ask again, shown before the operator decides.** `extraction-failures.html` lists the files with their reasons. Whether that page should mark which of them a raised attempt would ask again is a question for that page, not for this record.
