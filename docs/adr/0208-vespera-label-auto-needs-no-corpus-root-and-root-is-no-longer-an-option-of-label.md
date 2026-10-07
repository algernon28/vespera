# ADR-208 — `vespera label --auto` needs no corpus root, and `--root` is no longer an option of `label`

- **Date**: 2026-10-07
- **Status**: accepted
- **Amends**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6, in its heading's `[--root <path>]` and in its first bullet (§7 below). Everything else in §6 stands: `--auto` with a file refuses, the labeller's model is its own setting, the refusals that come first, and the lines the operator sees.
- **Amends**: [ADR-206](0206-stage-2-records-the-key-it-looked-the-extraction-cache-up-under-and-no-step-after-it-opens-an-archive-file.md), in the fourth bullet of its §4, in one line of its header and in one bullet of its §7 (§7 below). It answers the question that bullet left to the operator.
- **Keeps**: [ADR-066](0066-the-command-line-names-the-root-configuration-is-the-fallback.md) (`vespera run` takes its root from the argument, then from `vespera.corpus-root`, and refuses with neither), [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md) (exit codes), [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md) (the `--db-dir` drift check, on both commands), [ADR-047](0047-the-pipeline-never-blocks.md) (`label` is the deliberate act), [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) (the working-directory lock and its holder's line) and [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md) (`vespera label` writes no invocation account).
- **Rests on**: a reading of the `label` path at commit `251ee64`, with the sites named in §Context; [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and `StageModules` for the run ids (§8); and ADR-206 §4 and §5, which are why the command has nothing to read under a root. No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Settles** [#451](https://github.com/algernon28/vespera/issues/451), by the operator's choice of 2026-10-07 among the three it was put: (a) drop the requirement and go on accepting `--root`, saying it is not used; (b) drop the requirement and remove the option; (c) leave it. The operator chose (b).

## Context

ADR-197 §6 made `vespera label --auto` refuse unless it was given `--root` or `vespera.corpus-root` was set, *"because the openings are read from the corpus and a root is never guessed"*. ADR-206 §4 withdrew the reason, because the command finds each opening under the key stage 2 recorded and opens no archive file. It kept the requirement, in its own words *"because what a command line demands is the operator's to change and not a side effect of this wave"*, and put the question to the operator. #451 is that question.

So an operator had to name a corpus root to a command that never opens it, and a wrong or unreachable root was neither used nor noticed.

### What the code does, read at `251ee64`

**(a) Nothing on the `label --auto` path reads the root.**

| Site | What it does with the root |
| --- | --- |
| `VesperaCommand.Label.call`, lines 299 to 303 | The `--db-dir` drift check (ADR-054). It compares `--db-dir` with `vespera.working-dir` and knows no root. |
| `VesperaCommand.Label.call`, lines 304 to 308 | Refuses `--auto` with a file. Knows no root. |
| picocli, for the field at lines 240 to 245 | Converts the text after `--root` to a `Path`. That checks the text's syntax on this machine and touches no disk. |
| `VesperaCommand.Label.callAuto`, lines 335 to 337 | Takes the option, or else `Path.of(configuredRoot.strip())` from `vespera.corpus-root`. No normalisation, no existence check, no `toRealPath`. |
| `callAuto`, lines 338 to 343 | The refusal, exit 2, when both are absent. |
| `callAuto`, line 344 | Passes the path to `AutoLabelling.run(Path root)`. |
| `AutoLabelling.run`, lines 104 to 241 | **Never reads its parameter.** The only other mention of a root is the comment at lines 160 to 163. |

What the command reads instead: the label file in the working directory (line 109), the ledger (`walkOf` at 130 and 165, `occurrenceId` at 182, `stageOf` and `upstreamRuns` at 257 and 260), the profile (134), the recorded answers, and `DocumentOpening`, which holds no path and opens no file (ADR-206 §4, `OnlyTheFirstReadHashesAFileTest`). A path of the label file reaches an occurrence through the walk of the run the label file names, and that walk already records its own root in `walk.root`.

**Two things nearby touch a path, and neither is the corpus root.**

- `AutoLabelling.run`, line 136, resolves the profile's seed folder with `Walk.canonicalRoot` (`toRealPath` and `isDirectory`), to compare it with the seed set the label file names. That is the seed folder's directory entry. This record leaves it.
- `WorkingDirectoryLock`, lines 89 and 110 to 114, writes the command line as it was typed into `vespera.lock`, as the holder's line (ADR-177). Whatever is typed is written there as text, a refused command line included. Nothing opens it.

`vespera label` starts no job, so it writes no invocation account (ADR-198 §1, and point 6 of its list). The private-paths rules are about what an agent opens (ADR-196) and bind no command.

**(b) Plain `vespera label` had no root requirement and never had one.** `Label.call`, lines 312 to 321, goes to `LabelIngestion.ingest(file)` and to `NextAction.line()` and reads neither the `--root` field nor `vespera.corpus-root`. `LabelIngestion` holds the working directory and no root. ADR-197 §6 names `--root` only with `--auto`, and no earlier record (ADR-088, ADR-169) names a root for `label`. **But the option is declared on the `label` command, not on `--auto`**, so picocli accepts `vespera label --root <path>` with no `--auto`; the value is ignored, and nothing says so.

**(c) Where the requirement was stated.**

| Where | What it says |
| --- | --- |
| ADR-197 §6, heading and first bullet | The requirement and its reason. |
| ADR-197, the block at its top that points to ADR-206 | That ADR-206 withdrew the reason and *"The requirement stands."* |
| ADR-206, header, the line that amends ADR-197 | *"The requirement itself stands."* |
| ADR-206 §4, fourth bullet | *"still requires a corpus root, and no longer reads one … Whether the requirement goes is open"*. |
| ADR-206 §7, under Reworded | The refusal's sentence, which ends *"A root is never guessed."* |
| `VesperaCommand.java`, lines 243 to 244 | The option's description: *"With --auto, the corpus root the label file's documents are under."* False since ADR-206. |
| `VesperaCommand.java`, lines 324 to 327 and 338 to 341 | The javadoc of `callAuto`, and the refusal. |
| `LocalLabellingInvocationTest.refusesWithNoRoot` | Pins that `label --auto` with no root exits non-zero and asks the model nothing. It does not pin the sentence. |

`README.md` does not mention `--auto` or `--root` at all: its command table gives `vespera label [file] [--db-dir=<path>]`, and line 118 states the root rule for `vespera run` only. `AGENTS.md`, `docs/architecture.md` and `docs/decision-ledger.md` state the rule for `vespera run` and say nothing of `label --auto`. The run configuration `.run/Local Vespera Label.run.xml` passes `label` alone and loads the local profile.

**#451 says no test pins the refusal. One does.** `refusesWithNoRoot` holds the exit code and that nothing was asked. What had no test is the wording.

## Decision

### 1. `vespera label --auto` needs no corpus root

With no root named anywhere it runs, and exits as any labelling does: 0 when it labelled, 1 when it recorded nothing, 2 for a command line it will not act on. The refusal *"vespera label --auto named no root …"* is removed, with its exit 2.

`AutoLabelling.run` takes no argument.

### 2. `--root` is removed from `label`, with `--auto` and without

The option is declared once, on the `label` command, so removing it removes it for both forms. There is no way to keep it on plain `label` alone, and no reason to: it never did anything there.

**So two command lines that worked are now usage errors**, and the second is one the operator may meet without expecting it:

- `vespera label --auto --root <path>`, which was the required form until this record;
- `vespera label --root <path>`, which was accepted and ignored in silence. It recorded the answers in the label file as if `--root` were not there. It now records nothing and exits 2.

### 3. What the operator sees for either

picocli refuses the command line before the command is called: its unknown-option message on standard error, naming `--root`, followed by the usage text of `label`, which no longer lists the option. The exit code is 2, the code a command line this project will not act on already returns (ADR-141). Nothing is read, asked or recorded: the label file, the ledger and the model are untouched.

**The sentence is picocli's and is not ours to fix.** What this record holds is the exit code, that standard error names the option, and that nothing was done.

### 4. No hint is added to the usage error

picocli can be given a handler that adds a line to an unknown-option error, here one saying the option was removed and why. It is left out. It needs a parameter-exception handler on the command line, which this project does not have, to explain one option to one operator for as long as old command lines are remembered; and the usage text printed under the error already shows what `label` takes. The record is where the reason is kept.

### 5. `vespera.corpus-root` being set means nothing to `label`

`VesperaCommand.Label` stops reading the property: its constructor loses that argument and its field. A profile that sets it, as an operator's local profile does for `vespera run`, changes nothing about `label` and is not an error. It must not be: the run configuration this repository ships for labelling loads that local profile, so the property being set is the ordinary case.

A property naming a root that does not exist therefore changes nothing either.

### 6. What does not move

- **`vespera run`.** Its root is the argument, never an option; the fallback to `vespera.corpus-root`, the refusal with neither, its sentence and its exit 2 stand (ADR-066). `VesperaCommand.Run` is not edited.
- **Exit codes** (ADR-141). `label` exits 2 for an unknown option as it does for any other; no code is new.
- **The `--db-dir` drift check** (ADR-054). It is declared on both commands and runs first on both, as now.
- **The lock.** `vespera.lock` goes on recording the command line as typed (ADR-177).
- **Everything `label --auto` does once it starts**: ADR-197 §1 to §4 and §6's refusals, and ADR-206 §4 and §5.

### 7. Which sentences no longer hold

Earlier records are not edited in their text. Each correction is made here, and ADR-197 and ADR-206 carry a block at their top that points to this record.

**ADR-197 §6.**

- The heading, *"`vespera label --auto [--root <path>]`"*. **Read instead**: `vespera label --auto`. There is no `--root`.
- The first bullet, *"`--root` falls back to `vespera.corpus-root`, and an invocation with neither refuses (ADR-066), because the openings are read from the corpus and a root is never guessed."* **Withdrawn whole.** ADR-206 had withdrawn its reason; this withdraws the option, the fallback and the refusal. ADR-066's rule is about `vespera run`, where it stands.
- The block at ADR-197's top that points to ADR-206 ends *"The requirement stands."* It stood until this record.

**ADR-206.**

- Header, the line that amends ADR-197: *"The requirement itself stands."* It stood until this record.
- §4, fourth bullet: *"`vespera label --auto` still requires a corpus root, and no longer reads one."* **Read instead**: it requires none, takes none and reads none. *"This record leaves the requirement and the option as they are"* is true of ADR-206 and is history. *"Whether the requirement goes is open, and is put to the operator"*: answered here.
- §7, under Reworded: *"the refusal of `vespera label --auto` with no root, which loses its closing clause … A root is still never guessed (ADR-066)."* **Gone for this command.** No sentence takes its place: the command no longer refuses for want of a root, so there is nothing to word. *"A root is never guessed"* remains true of `vespera run`, the only command that takes one.

**Not amended.** ADR-206 §4's other bullets and its §5, which describe what `label --auto` reads; ADR-066, which this record keeps; ADR-204 §6, whose row for `AutoLabelling` names its statements and no root.

### 8. The run ids

**The mechanism** (ADR-058, `.mvn/scripts/implementation-versions.groovy`): a module's version is the last commit that touches `src/main/java/io/algernon/vespera/<module>`, and a stage's run id carries the versions of the modules `StageModules` lists for it.

**This record's own commits move no run id.** They touch `docs/`, `AGENTS.md` and `src/test`, and nothing under `src/main/java`.

**The implementation moved the run ids of six stages.** `VesperaCommand` and `AutoLabelling` are both in `pipeline`, and `StageModules` names `pipeline` for content census (3), content redundancy (4), seed measurement and embedding scoring (both runs of 5), arrangement (6a) and generation (6b). At the first `vespera run` of a build that carries it, over an existing working directory, each of those is minted anew. Byte-level reduction (1) names `corpus` alone and extraction (2) names `extraction` and `similarity`, so neither moves, and the census records a walk, which has no implementation version. What follows for the operator is what ADR-197's Consequences list: stages 3 to 6b run again over the conversions already cached, the arrangement's name changes so `arrangementApproved` is written again, and the labels survive.

**No shape of the change avoided it.** The requirement was enforced and the option declared in `VesperaCommand.java`, which is in `pipeline`; neither could go without a commit under that path. Leaving `AutoLabelling.run(Path root)` with its unread parameter and changing `VesperaCommand` alone would have moved exactly the same six ids, since both files are under one path, and would have left a method that takes an argument it ignores. So the parameter went (§1). Only leaving everything as it was would have moved nothing.

**The operator wants run ids minted once.** The implementation was therefore committed directly on top of another change that already moves those stages: [#449](https://github.com/algernon28/vespera/issues/449)'s, at `6eb88a2`, which touches `corpus` and `extraction` and so moves every stage from 1 to 6b. A working directory whose next `vespera run` is made by a build carrying both meets the two moves as one.

## Why this shape, and what the others cost

**(a) Drop the requirement and go on accepting `--root`, with one line saying it is not used.** It was the shape two analyses recommended and the one this record was first drafted on.

- What it would have bought: every command line written while the root was required keeps working, and plain `vespera label --root <path>` goes on recording answers.
- What it carried: an option that does nothing, a description saying so, a line at INFO to say it again on each use, and three small rules about that line (only for the option and never for the property, only with `--auto`, and a value never parsed even as a path).
- **It was not taken because the operator chose (b): a command that accepts only what it uses.** That is the operator's choice and this record gives no further reason for it.
- Run ids: the same six as (b).

**(c) Leave it.** No code changes and no run id moves. The operator would go on naming an archive the command does not open, and a wrong root would go on being accepted in silence, which is what #451 describes. The option's description would stay false unless corrected, and correcting it is a commit under `pipeline`.

**Keep `--root` and check it against the root of the walk the label file's run belongs to**, the third meaning #451 lists. It falls away with the option. It would also have needed the archive to be reachable, to canonicalise what was typed as `walk.root` is canonical, and labelling needs no archive (ADR-206).

**A hint on the usage error.** §4.

**Keep the option on plain `label` only, or deprecate it for a release first.** The option is one declaration; splitting it by form needs code that inspects the other flag, to preserve a silent no-op. There is one operator and no release train to deprecate across.

## Consequences

- **An operator labels with `vespera label --auto` and nothing else**, with the archive's disk unplugged if they like.
- **A command line that still passes `--root` to `label` fails**, with or without `--auto`, exit 2, having done nothing. Scripts and notes written since ADR-197 need the two words removed.
- **`vespera.corpus-root` is read by `vespera run` alone.**
- **Six stages' run ids moved with the code** (§8).
- **`README.md` is not edited.** It documents neither `--auto` nor `--root`, and what `docs/check-claims.mjs` holds it to (its subcommands, the commands it gives `--db-dir`, its profile keys and the files written beside the database) does not change.
- **`CONTEXT.md` is not edited.** Its *Corpus root* entry names `vespera run` and `vespera.corpus-root` only.
- **Three existing test classes passed `--root` in fourteen invocations**, and one test pinned the refusal. They changed with the code and not before it: each was kept as an edited copy outside `src/test` until the code landed, and replaced its original then (below; note corrected with [#451](https://github.com/algernon28/vespera/issues/451), when the code landed).

**What the implementation did** (this list was written as what it owed, and is corrected with [#451](https://github.com/algernon28/vespera/issues/451) to what was done):

- `AutoLabelling.run()` has no parameter, and the comment at its lines 160 to 163 is kept, since it is true;
- `VesperaCommand.Label` has no `--root` option, no field for it and no line for it in `forgetPreviousInvocation`; its constructor takes no `configuredRoot` and the field is gone; `callAuto` has no refusal for want of a root and calls `labelling.run()`; its javadoc cites this record in place of *"the root is named by the option or by configuration and never guessed"*;
- the four test files that were kept under `docs/adr/0208/tests/` are in `src/test/java/io/algernon/vespera/pipeline/`, three of them in place of the file of the same name;
- nothing else: no test was edited or deleted by hand, and `VesperaCommand.Run`, `README.md` and `CONTEXT.md` were not touched.

**What pins it.** Every test named here is in `src/test/java/io/algernon/vespera/pipeline/` and passes.

Written with this record, before the code, because each held before the change and holds after it:

- `LabelAutoUnderAConfiguredRootInvocationTest`: with `vespera.corpus-root` naming a directory nobody created and no `--root`, the labelling succeeds, the directory still does not exist, and nothing is said about a root (§5).
- `UnconfiguredRootTest.saysWhatToSupplyAndThatARootIsNeverGuessed`: `vespera run` with no root exits 2 with its sentence (§6). Until this record only its exit code was held.

Moved into `src/test` with the code. Each failed against the code as it was, so until the code landed it was kept as a complete file under `docs/adr/0208/tests/`, followed by the path it takes in the repository, as ADR-193's Tests describe. **All four landed at `ff763d5` byte for byte as the parked copies, and nothing remains under `docs/adr/0208/tests/`.** Afterwards only the javadoc of `LabelAutoNeedsNoRootInvocationTest` was put in the past tense (`b0b2d1a`); no claim, assertion or invocation of any of the four changed (note corrected with [#451](https://github.com/algernon28/vespera/issues/451), when the code landed, and again after its gate). That includes the two tests of the refused option, which could not be run before it: picocli's unknown-option error reaches standard error and names `--root`.

- `LabelAutoNeedsNoRootInvocationTest`, new:
  - `runsWithNoRootNamedAndNoneConfigured`: §1.
  - `refusesTheRemovedOptionWithAuto`: `label --auto --root <path>` exits 2, standard error names `--root`, the model is asked nothing, no answer is recorded and the label file is byte for byte as it was (§2, §3).
  - `refusesTheRemovedOptionWithoutAuto`: `label --root <path>` over a label file holding an answer exits 2, standard error names `--root`, and the answer is not recorded (§2, §3).
  - `theLabelCommandNoLongerListsTheOption`: the command declares no `--root` and its usage text does not mention it.
- `LocalLabellingInvocationTest`, as edited. **Dropped**: `refusesWithNoRoot`, with the refusal it pinned. **Changed**: the eleven invocations that passed `"--root", root.toString()` no longer do, in `putsTheOpeningOfADocumentRewrittenInPlace`, `asksAboutAPathTheRunNeverWalkedWithNoOpening`, `recordsTheAnswersWithTheModelsName`, `setsTheFloorByTheRule`, `leavesAFloorAPersonWrote`, `aPersonsAnswerIsNotReplaced`, `aPersonCanOverruleTheModel`, `refusesWhileAnAnswerIsNotYetRecorded`, `rewritesAFloorTheRuleWrote`, `aLaterRunKeepsTheMark` and `refusesAFileWithAuto`. No claim is changed.
- `OllamaRefusalInvocationTest`, as edited: its two invocations lost `--root`. No claim is changed.
- `RefusingLabellerInvocationTest`, as edited: its one invocation lost `--root`. No claim is changed.

**Not pinned:** picocli's wording around the option's name (§3), by decision; and that `label` does not read `vespera.corpus-root` at all, as distinct from reading it and doing nothing with it, which no behaviour tells apart.
