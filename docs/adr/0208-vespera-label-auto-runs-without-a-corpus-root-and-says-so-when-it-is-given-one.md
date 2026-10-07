# ADR-208 — `vespera label --auto` runs without a corpus root, and says so when it is given one

> **Proposed, not accepted.** The operator has been asked to choose among (a), (b) and (c) of [#451](https://github.com/algernon28/vespera/issues/451) and has not answered. This record is written on (a) as an assumption. Nothing in it is in force, and nothing in `src/main` has changed: until the operator chooses, `vespera label --auto` requires a root as [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6 and [ADR-206](0206-stage-2-records-the-key-it-looked-the-extraction-cache-up-under-and-no-step-after-it-opens-an-archive-file.md) §4 leave it. "If the operator chooses otherwise" below says what changes in this file for (b) and for (c).

- **Date**: 2026-10-07
- **Status**: proposed, awaiting the operator's choice among (a), (b) and (c). Not accepted.
- **Would amend**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6, in its heading's `[--root <path>]` and in its first bullet (§8 below). Everything else in §6 stands: `--auto` with a file refuses, the labeller's model is its own setting, the refusals that come first, and the lines the operator sees.
- **Would amend**: [ADR-206](0206-stage-2-records-the-key-it-looked-the-extraction-cache-up-under-and-no-step-after-it-opens-an-archive-file.md), in the fifth bullet of its §4, in one line of its header and in one bullet of its §7 (§8 below). It takes up the question that bullet left open.
- **Keeps**: [ADR-066](0066-the-command-line-names-the-root-configuration-is-the-fallback.md) (`vespera run` takes its root from the argument, then from `vespera.corpus-root`, and refuses with neither), [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md) (exit codes), [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md) (the `--db-dir` drift check, on both commands), [ADR-047](0047-the-pipeline-never-blocks.md) (`label` is the deliberate act), [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) (the working-directory lock and its holder's line) and [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md) (`vespera label` writes no invocation account).
- **Rests on**: a reading of the `label` path at commit `251ee64`, with the sites named in §Context; [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and `StageModules` for the run ids (§9); and ADR-206 §4 and §5, which are why the command has nothing to read under a root. No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Answers** [#451](https://github.com/algernon28/vespera/issues/451), once accepted.

## Context

ADR-197 §6 made `vespera label --auto` refuse unless it was given `--root` or `vespera.corpus-root` was set, *"because the openings are read from the corpus and a root is never guessed"*. ADR-206 §4 withdrew the reason, because the command finds each opening under the key stage 2 recorded and opens no archive file. It kept the requirement, in its own words *"because what a command line demands is the operator's to change and not a side effect of this wave"*, and put the question to the operator. #451 is that question.

So today an operator must name a corpus root to a command that never opens it, and a wrong or unreachable root is neither used nor noticed.

### What the code does, read at `251ee64`

**(a) Nothing on the `label --auto` path reads the root.**

| Site | What it does with the root |
| --- | --- |
| `VesperaCommand.Label.call`, lines 299 to 303 | The `--db-dir` drift check (ADR-054). It compares `--db-dir` with `vespera.working-dir` and knows no root. |
| `VesperaCommand.Label.call`, lines 304 to 308 | Refuses `--auto` with a file. Knows no root. |
| picocli, for the field at lines 240 to 245 | Converts the text after `--root` to a `Path`. That checks the text's syntax on this machine and touches no disk. A value the machine cannot write as a path is refused as a usage error before the command is called. |
| `VesperaCommand.Label.callAuto`, lines 335 to 337 | Takes the option, or else `Path.of(configuredRoot.strip())` from `vespera.corpus-root`. No normalisation, no existence check, no `toRealPath`. |
| `callAuto`, lines 338 to 343 | The refusal, exit 2, when both are absent. |
| `callAuto`, line 344 | Passes the path to `AutoLabelling.run(Path root)`. |
| `AutoLabelling.run`, lines 104 to 241 | **Never reads its parameter.** The only other mention of a root is the comment at lines 160 to 163. |

What the command reads instead: the label file in the working directory (line 109), the ledger (`walkOf` at 130 and 165, `occurrenceId` at 182, `stageOf` and `upstreamRuns` at 257 and 260), the profile (134), the recorded answers, and `DocumentOpening`, which holds no path and opens no file (ADR-206 §4, `OnlyTheFirstReadHashesAFileTest`). A path of the label file reaches an occurrence through the walk of the run the label file names, and that walk already records its own root in `walk.root`.

**Two things nearby touch a path, and neither is the corpus root.**

- `AutoLabelling.run`, line 136, resolves the profile's seed folder with `Walk.canonicalRoot` (`toRealPath` and `isDirectory`), to compare it with the seed set the label file names. That is the seed folder's directory entry. This record leaves it.
- `WorkingDirectoryLock`, lines 88 and 110 to 114, writes the command line as it was typed into `vespera.lock`, as the holder's line (ADR-177). A `--root` that is given is therefore written there as text, and shown to a second invocation that is refused. Nothing opens it.

`vespera label` starts no job, so it writes no invocation account (ADR-198 §1, and point 6 of its list). The private-paths rules are about what an agent opens (ADR-196) and bind no command.

**(b) Plain `vespera label` has no root requirement and never had one.** `Label.call`, lines 309 to 321, goes to `LabelIngestion.ingest(file)` and to `NextAction.line()` and reads neither the `--root` field nor `vespera.corpus-root`. `LabelIngestion` holds the working directory and no root. ADR-197 §6 names `--root` only with `--auto`, and no earlier record (ADR-088, ADR-169) names a root for `label`. picocli does accept `vespera label --root <path>` with no `--auto`, since the option is declared on the command; it is ignored, and nothing says so.

**(c) Where the requirement is stated.**

| Where | What it says |
| --- | --- |
| ADR-197 §6, heading and first bullet | The requirement and its reason. |
| ADR-197, the banner at its top | That ADR-206 withdrew the reason and *"The requirement stands."* |
| ADR-206, header, the line that amends ADR-197 | *"The requirement itself stands."* |
| ADR-206 §4, fifth bullet | *"still requires a corpus root, and no longer reads one … Whether the requirement goes is open"*. |
| ADR-206 §7, under Reworded | The refusal's sentence, which now ends *"A root is never guessed."* |
| `VesperaCommand.java`, lines 243 to 244 | The option's description: *"With --auto, the corpus root the label file's documents are under."* False since ADR-206. |
| `VesperaCommand.java`, lines 324 to 327 and 338 to 341 | The javadoc of `callAuto`, and the refusal. |
| `LocalLabellingInvocationTest.refusesWithNoRoot` | Pins that `label --auto` with no root exits non-zero and asks the model nothing. It does not pin the sentence. |
| `Adr.java`, the javadoc of the ADR-206 constant | *"the reason in ADR-197 section 6"*, which stays true. |

`README.md` does not mention `--auto` or `--root` at all: its command table gives `vespera label [file] [--db-dir=<path>]`, and line 118 states the root rule for `vespera run` only. `AGENTS.md`, `docs/architecture.md` and `docs/decision-ledger.md` state the rule for `vespera run` and say nothing of `label --auto`. The run configuration `.run/Local Vespera Label.run.xml` passes `label` alone.

**#451 says no test pins the refusal. One does.** `refusesWithNoRoot` holds the exit code and that nothing was asked. What has no test is the wording.

## Decision

Sections 1 to 6 are choice (a). They are proposed.

### 1. `vespera label --auto` needs no corpus root

With no `--root` and no `vespera.corpus-root` it runs, and exits as any labelling does: 0 when it labelled, 1 when it recorded nothing, 2 for `--auto` with a file. The refusal *"vespera label --auto named no root …"* and its exit 2 are removed.

`AutoLabelling.run` takes no argument. `VesperaCommand.Label` stops reading `vespera.corpus-root`: its constructor loses that argument and its field.

### 2. `--root` is still accepted, and a given one is said to be unused, once

`vespera label --auto --root <anything>` runs exactly as `vespera label --auto` does. A command line written while the root was required keeps working.

**The value is not read in any sense.** It is not opened, not resolved, not compared with anything, and not converted to a path: the option's field is text. So a root that no longer exists, a drive that is not plugged in, and text this machine cannot write as a path are all accepted alike. Today the third is a usage error, raised by picocli's conversion before the command runs.

**The command says so, in one line**, at INFO, through the logger of `VesperaCommand.Label`, immediately before it calls `AutoLabelling.run`:

> --root is not used: vespera label --auto finds each document through the run the label file names and opens nothing under a corpus root. The option is still accepted, so a command line that names one keeps working.

- **At INFO, not WARN.** Nothing is wrong and nothing is the operator's to fix. It is the same kind of line as `vespera run`'s *"Walking <root>, named on the command line"*.
- **Through the logger, not printed.** A logged line reaches the console and `vespera.log` (ADR-093), so it is on record beside the labelling it belongs to. The refusals of this command are printed on standard error because they are its answer; this is not its answer.
- **It does not repeat the value.** Nothing was done with it, and a line with no path in it is the same text on every machine.
- **Before the labeller is asked anything**, so an invocation that is then refused, for a hosted model or for answers not yet recorded, has still said it. An invocation refused before `callAuto` is reached, on `--db-dir` or on `--auto` with a file, has not.

### 3. `vespera.corpus-root` being set means nothing to `label`, and no line is written for it

The line of §2 is written **only when `--root` is on the command line**. The property is `vespera run`'s fallback (ADR-066). An operator sets it once, in a local profile, for `vespera run`; they gave `label` nothing. A line on every labelling would tell them, each time, about a thing they did not do, and would read as a fault in a profile that has none. The run configuration this repository ships for labelling loads that local profile and passes `label` alone, so this is the ordinary case and not a corner.

It follows that `label` does not read the property at all (§1), so a property naming a root that does not exist changes nothing either.

### 4. A given root is not checked against the walk

#451 lists a third meaning for a given root: compare it with the root of the walk the label file's run belongs to. It is refused.

- **The command already knows the answer.** The label file names a run, the run has one walk, and `walk.root` is that walk's root. There is one right value and the ledger holds it. Asking the operator to state it again adds only a way to be wrong.
- **A comparison needs the root to be reachable, which is what ADR-206 removed.** `walk.root` is the canonical root, taken with `toRealPath`. Comparing fairly means canonicalising what was typed, which fails for an archive on a disk that is not plugged in, and labelling needs no archive. Comparing the texts instead refuses a correct root written with another case, through a link, with a trailing separator, or relative to the current directory.
- **A mismatch has no good outcome.** Refusing brings the requirement back for anyone who passes the option. Warning and carrying on is §2's line with a second, more alarming wording, for a value that still decides nothing.

### 5. Plain `vespera label` does not change

It required no root and requires none. `vespera label --root <path>` with no `--auto` goes on being accepted and ignored, and writes no line: no record ever gave the option a meaning there, so nobody was told to pass it, and §2's line exists for the operator who was.

### 6. The option's description

> Not used. vespera label opens nothing under a corpus root, with --auto or without. Accepted so that a command line written when --auto required one keeps working.

`paramLabel` stays `<root>`. The javadoc of `callAuto` stops saying the root is named and never guessed.

### 7. What does not move

- **`vespera run`.** The argument, the fallback to `vespera.corpus-root`, the refusal with neither, its sentence and its exit 2 (ADR-066). `VesperaCommand.Run` is not edited.
- **Exit codes** (ADR-141). `label --auto` loses one way to exit 2 and gains none.
- **The `--db-dir` drift check** (ADR-054). It runs first on both commands, as now.
- **The lock.** `vespera.lock` goes on recording the command line as typed, `--root` and its value included (ADR-177).
- **Everything `label --auto` does once it starts**: ADR-197 §1 to §4 and §6's refusals, and ADR-206 §4 and §5.

### 8. Which sentences no longer hold

Earlier records are not edited in their text. Each correction is made here, and ADR-197 and ADR-206 carry a block at their top that points to this record.

**ADR-197 §6.**

- The heading, *"`vespera label --auto [--root <path>]`"*. **Read instead**: `vespera label --auto`. `--root <path>` is accepted and not used.
- The first bullet, *"`--root` falls back to `vespera.corpus-root`, and an invocation with neither refuses (ADR-066), because the openings are read from the corpus and a root is never guessed."* **Withdrawn whole.** ADR-206 had withdrawn its reason; this withdraws the fallback and the refusal. ADR-066's rule is about `vespera run`, where it stands.
- The banner at ADR-197's top that points to ADR-206 ends *"The requirement stands."* It stood until this record.

**ADR-206.**

- Header, the line that amends ADR-197: *"The requirement itself stands."* It stood until this record.
- §4, fifth bullet: *"`vespera label --auto` still requires a corpus root, and no longer reads one."* **Read instead**: it requires none and reads none. *"This record leaves the requirement and the option as they are"* is true of ADR-206 and is history. *"Whether the requirement goes is open, and is put to the operator"*: answered here.
- §7, under Reworded: *"the refusal of `vespera label --auto` with no root, which loses its closing clause … A root is still never guessed (ADR-066)."* **Superseded for this command**: the refusal is gone, so there is no sentence to reword. *"A root is never guessed"* remains true of `vespera run`, the only command that needs one.

**Not amended.** ADR-206 §4's other bullets and its §5, which describe what `label --auto` reads; ADR-066, which this record keeps; ADR-204 §6, whose row for `AutoLabelling` names its statements and no root.

**A test's note, corrected in place when the code lands** (`docs/adr/README.md`): ADR-197's Consequences say *"A test pins each"* of the refusals; none of those is the root refusal, so nothing there changes. `LocalLabellingInvocationTest.refusesWithNoRoot` is deleted with the refusal it pins.

### 9. The run ids

**Stating the mechanism first** (ADR-058, `.mvn/scripts/implementation-versions.groovy`): a module's version is the last commit that touches `src/main/java/io/algernon/vespera/<module>`, and a stage's run id carries the versions of the modules `StageModules` lists for it.

**This record's own commit moves no run id.** It touches `docs/`, `AGENTS.md` and `src/test`, and nothing under `src/main/java`.

**The implementation moves the run ids of six stages.** `VesperaCommand` and `AutoLabelling` are both in `pipeline`, and `StageModules` names `pipeline` for content census (3), content redundancy (4), seed measurement and embedding scoring (both runs of 5), arrangement (6a) and generation (6b). At the next `vespera run` over an existing working directory each of those is minted anew. Byte-level reduction (1) names `corpus` alone and extraction (2) names `extraction` and `similarity`, so neither moves, and the census records a walk, which has no implementation version. What follows for the operator is what ADR-197's Consequences list: stages 3 to 6b run again over the conversions already cached, the arrangement's name changes so `arrangementApproved` is written again, and the labels survive.

**No shape of (a) or of (b) avoids it.** The requirement is enforced in `VesperaCommand.java`, which is in `pipeline`; there is no way to stop refusing without a commit under that path. Leaving `AutoLabelling.run(Path root)` with its unread parameter and changing `VesperaCommand` alone moves exactly the same six ids, since both files are under one path, and leaves a method that takes an argument it ignores. So the parameter goes (§1). Only (c) moves nothing.

**The operator wants run ids minted once.** The implementation is therefore to be merged together with, or directly after, another change that already moves those stages, and before the next `vespera run` on a working directory worth keeping. [#449](https://github.com/algernon28/vespera/issues/449)'s change is one: it touches `corpus` and `extraction`, which move every stage from 1 to 6b.

## Why this shape, and what the others cost

**(b) Drop the requirement and remove the option.** `vespera label --auto --root <path>` becomes picocli's *"Unknown option: '--root'"*, exit 2.

- It is the cleanest surface: no option that does nothing, no line to explain it, no description that says "not used".
- It breaks every command line and script written between ADR-197 and now, for a command that costs model minutes, and it breaks them with a message that says nothing of why. The operator is one person and can relearn it; the cost is one failed invocation each time an old line is reused.
- Fourteen invocations in three test classes pass `--root` (`LocalLabellingInvocationTest`, `OllamaRefusalInvocationTest`, `RefusingLabellerInvocationTest`) and would be edited.
- Run ids: the same six as (a).

**(c) Leave it.** No code changes and no run id moves.

- The operator goes on naming an archive the command does not open, and a wrong root goes on being accepted in silence, which is the defect #451 describes.
- The option's description stays false unless it is corrected, and correcting it is a commit under `pipeline`, which moves the six ids for a sentence.
- If (c) is chosen, the refusal's wording gets its test and nothing else is owed.

**Ignore a given root in silence.** Refused: the operator who passes `--root H:\archive` believes the command works on that archive. Today that belief is false and nothing says so; (a) without the line would keep exactly that.

**Check a given root against the walk.** §4.

**Write the line when only the property is set.** §3.

**Write the line for plain `vespera label --root` too.** One rule for the whole command instead of one for `--auto`. Refused as wider than the question: it changes what plain `label` writes, for a combination no record ever described. If the operator prefers one rule, it is one `if` moved up in `Label.call` and one test's last claim inverted.

**Keep the option as a `Path`.** Then text the machine cannot parse as a path is still a usage error, on an option described as not used. Text costs nothing and makes "not used" true without exception.

## If the operator chooses otherwise

**(a) accepted as written.** The block at the top of this file goes; Status becomes `accepted`; *Would amend* becomes *Amends* and *Answers … once accepted* becomes *Settles*; the blocks at the top of ADR-197 and ADR-206 lose *"Proposed, not accepted"* and the sentence that follows it; the row in `docs/adr/README.md` and the javadoc in `Adr.java` lose the word *proposed*. Then `spec-implementer` does what "What the implementation owes" lists.

**(b).** As (a) accepted, and in addition: the title becomes *"… and `--root` is no longer an option of `label`"*, and the file name with it; §2, §3 and §6 are replaced by one section saying the option is removed and an old command line is refused as an unknown option, exit 2; §4 and the last three alternatives above fall away with the option; §5 says plain `label --root` is refused as well. In the tests: `LabelAutoNeedsNoRootInvocationTest` keeps its first test and replaces the other three with one that claims exit 2 for `--root`; `aRootThatDoesNotExistIsNeitherOpenedNorAFailure` and `plainLabelGivenARootRecordsTheAnswersAndSaysNothingAboutIt` in `LocalLabellingInvocationTest` are deleted; every `"--root", root.toString()` in the three classes above is removed. `LabelAutoUnderAConfiguredRootInvocationTest` stands as it is.

**(c).** This record is rewritten to a short one: Context as it is, the decision that the requirement stands and why, and a test of the refusal's sentence. The blocks on ADR-197 and ADR-206 are reworded to say the question ADR-206 left open was answered by keeping the requirement. The parked test file and the two tests named under (b) are deleted; `refusesWithNoRoot` gains a claim on the sentence.

## Consequences

- **An operator labels with `vespera label --auto` and nothing else**, with the archive's disk unplugged if they like.
- **A command line that names a root keeps working and is told, once, that the root was not used.**
- **`vespera.corpus-root` is read by `vespera run` alone.**
- **Six stages' run ids move when the code lands** (§9), which is the whole cost of (a) and of (b).
- **`README.md` is not edited.** It documents neither `--auto` nor `--root`, and what `docs/check-claims.mjs` holds it to (its subcommands, the commands it gives `--db-dir`, its profile keys and the files written beside the database) does not change.
- **`CONTEXT.md` is not edited.** Its *Corpus root* entry names `vespera run` and `vespera.corpus-root` only.

**What the implementation owes** (`spec-implementer`), once (a) is accepted:

- `AutoLabelling.run()` with no parameter, and the comment at its lines 160 to 163 kept, since it is true;
- `VesperaCommand.Label`: the constructor without `configuredRoot`; the `--root` field as `String`, with §6's description; `callAuto` without the refusal, writing §2's line at INFO when the field is not null, immediately before `labelling.run()`; its javadoc citing this record;
- `LocalLabellingInvocationTest.refusesWithNoRoot` deleted;
- `docs/adr/0208/tests/src/test/java/io/algernon/vespera/pipeline/LabelAutoNeedsNoRootInvocationTest.java` moved to `src/test/java/io/algernon/vespera/pipeline/`, and the note under "What pins it" corrected in place.

**What pins it.**

In `src/test` now, because each holds before the change and after it:

- `LocalLabellingInvocationTest.aRootThatDoesNotExistIsNeitherOpenedNorAFailure`: `--root` naming a directory nobody created; the labelling succeeds, the model is put the opening, and the directory still does not exist.
- `LocalLabellingInvocationTest.plainLabelGivenARootRecordsTheAnswersAndSaysNothingAboutIt`: §5.
- `LabelAutoUnderAConfiguredRootInvocationTest`: with `vespera.corpus-root` naming a directory nobody created and no `--root`, the labelling succeeds, the directory still does not exist, and §2's line is not written (§3).
- `UnconfiguredRootTest.saysWhatToSupplyAndThatARootIsNeverGuessed`: `vespera run` with no root exits 2 with its sentence (§7). Until now only its exit code was held.

Parked under `docs/adr/0208/tests/`, followed by the path it takes in the repository, as ADR-193's Tests describe, because each fails until the code lands:

- `LabelAutoNeedsNoRootInvocationTest.runsWithNoRootNamedAndNoneConfigured`: §1.
- `…saysOnceThatAGivenRootIsNotUsed`: §2's line, word for word, written once.
- `…acceptsARootThatIsNoPathAtAll`: text that cannot be a path on any machine is accepted, so the value is not even parsed (§2).
- `…theOptionSaysItIsNotUsed`: §6.

**Not pinned:** that the line is written before a refused labelling (§2, its last point), and that the line is logged and not printed, which a test reading captured output cannot tell apart.
