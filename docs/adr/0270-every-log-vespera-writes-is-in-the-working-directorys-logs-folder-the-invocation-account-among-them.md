# ADR-270 — Every log Vespera writes is in the working directory's `logs` folder, the invocation account among them

- **Date**: 2026-10-17
- **Status**: accepted on 2026-10-17.
- **Amends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md), in this place and no other: "Two appenders"'s rolling file "writes into the same configured working directory ADR-054 already gives the SQLite database, so the log sits next to the data it describes". It is written in that working directory's `logs` folder (§1). One file, rotating, with no file per run, stands.
- **Amends**: [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md), in these places and no others: §1's title and its first two paragraphs, which put the account in the folder `vespera.account-dir` names, outside every working directory, write none when it is unset or inside one, and have the operator add the folder to the hook's allow list; §6's line, whose place is now §2's; §7's "The only lines added are the warnings of §1 and §6", which §3 makes one line and §6's; the alternative "The account in the working directory beside `vespera.log`", which is now the decision; the first two Consequences; its Tests' "an unset or enclosed folder writing nothing"; Points settled 1; and What this does not decide's "Where the operator's folder is". The allow list (§2), the counts (§3), the progress lines (§4), the exception types (§5), that a failed write fails nothing (§6), the timing (§8), and Points settled 2 to 8, `vespera label` writing no account among them, stand.
- **Amends**: [ADR-203](0203-the-invocation-accounts-folder-is-judged-by-every-reading-of-its-name-and-a-name-that-cannot-be-followed-is-refused.md), whole but for its measurements in Context, which stay true of Windows: no configured folder is judged, so §1 to §6 have nothing to judge, and its seventeen tests are removed (Tests).
- **Amends**: [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), in these places and no others: §3's `folder=log`, which counts every file under `logs/` (§5); §7's "for every invocation since `vespera.account-dir` was set", Decided here 3's "with `vespera.account-dir` set", and the alternative "The invocation account alone"'s "a run minted before `vespera.account-dir` was set", each of which now reads "since this record's build".
- **Amends**: [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md), in this place and no other: the Consequence "ADR-198's account no longer has the reason it was put outside every working directory, and stays where it is". It moves (§2). What the guard refuses and admits stands: `logs/` is a folder of a working directory, open as the place it lies in (§2 of that record).
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md), in this place and no other: §6's "It names `vespera.progress-interval` beside `vespera.account-dir`". The README names `vespera.progress-interval` alone (§6).
- **Keeps**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md), one working directory, named by `vespera.working-dir` or `--db-dir`; [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §7, by which the counting script's change is a draft the operator installs (§5).
- **Rests on**: ADR-264, which opened a working directory under an allowed root to every agent tool, and so took away ADR-198's one reason for writing the account elsewhere.

## Context

On 2026-10-17 the operator said: *"also, all the logs should be in working dir"*, then *"that is: working dir/logs"*. Earlier the same day, of another change: *"i wouldn't add new flags for now, it makes things needlessly complicated. Just use defaults"*.

**Where the log is written today.** `logback-spring.xml` names the file `${workingDir}/vespera.log` and its rolled files `${workingDir}/vespera.%d{yyyy-MM-dd}.%i.log`, where `workingDir` is a `springProperty` read from `vespera.working-dir`. `application.yaml` sets that to `${db-dir:${user.dir}/.vespera}`, and Spring Boot reads `--db-dir=<path>` on the command line as the property `db-dir`. Spring Boot reads `logback-spring.xml` once the environment is prepared and before the context is built, so the property is known when the file appender starts; before then Logback writes to the console only, and no file. **So the log follows `--db-dir` in every invocation, `vespera run` and `vespera label` alike, and falls back nowhere else.** The one invocation whose log goes elsewhere is one given `--db-dir <path>` with a space, which Spring does not read as the path: it logs into the working directory it did open and refuses (`WorkingDirectoryOption`). No test sees any of this: a test JVM reads `logback-test.xml`, console only.

**What Vespera writes outside the working directory today**, found by reading every file write and appender in `src/main`:

| What | Where | A log? |
|---|---|---|
| the invocation account (`InvocationAccount`) | the folder `vespera.account-dir` names, outside every working directory; none when it is unset | yes: it moves (§2) |
| the console | standard output | the lines of `vespera.log`; no file |
| a large file's parts posted to Docling (`TextParts`) | the system temp folder, `vespera-text-parts-*`, each deleted once posted | no |
| the control conversion's PDF (`DoclingControlConversion`) | the system temp folder, `vespera-control-conversion-*.pdf`, deleted after | no |
| the label file `vespera label <file>` rewrites (`AutoLabelling`) | the file the operator names; `relevance-labels.yaml` in the working directory by default | no |

Everything else, the database and its temporary files (`TemporaryFilesInTheWorkingDirectory`), the profile, every page, the progress page, the label file by default and the deliverable, is in the working directory. The sidecars' own logs are Docker's.

ADR-198 put the account outside every working directory for one reason: ADR-196's hook refused everything inside one, so an account there "would be unreadable to the agent it is for". ADR-264 opened a working directory under an allowed root to every tool, and noted that the account no longer had that reason.

## Decision

### 1. The working directory has a `logs` folder, and `vespera.log` is in it

`logback-spring.xml` names the file `${workingDir}/logs/vespera.log` and its rolled files `${workingDir}/logs/vespera.%d{yyyy-MM-dd}.%i.log`. Logback makes the folder when it opens the file. The rolling policy (100 MB a file, 14 days, 2 GB in all), the pattern and the console stand. Its header comment says the file is in the working directory's `logs` folder.

### 2. The invocation account is written in that folder, by every invocation that starts the job, with no setting

Each `vespera run` that starts the job writes `invocation-account-<UTC start, yyyyMMdd'T'HHmmss'Z'>.txt` in `<working directory>/logs`, `-2` and on for a second in the same second, as ADR-198 §1 had it. The folder is made with `Files.createDirectories` if it is not there. Nothing about the place is configured, and nothing is judged: ADR-203's readings of a configured name, `FolderRuling` and its warnings go. A `logs` that is a link is written through as the platform follows it, as `vespera.log` and every page are.

A folder that cannot be made, or a file that cannot be opened, is ADR-198 §6's one warning, `the invocation account could not be written to {place}: {exception type}`, naming the folder or the file as the working directory names them: `<working directory>/logs` when the folder could not be made. The invocation goes on.

**The account still names no document, no path and not the working directory.** ADR-198 §2's allow list is untouched. It is a file of the working directory now, beside a log that does name paths, and what makes it the one an operator can hand over is still what is in it.

`vespera label` writes no account, as ADR-198's Points settled 6 has it: it starts no job.

### 3. `vespera.account-dir` is no longer read for a place, and set it costs one warning

No flag replaces it. `VesperaJobConfiguration` still reads the property and hands its value to `InvocationAccount`, whose constructor keeps its four parameters, `(Path workingDirectory, Path accountDirectory, JdbcTemplate jdbcTemplate, Clock clock)`; the second is now read for one line only. When it is not `null`, `beforeJob` writes one warning, through `InvocationAccount`'s logger, before the account is opened:

`vespera.account-dir is no longer read, and the invocation account is written to <working directory, absolute>/logs`

the folder being `workingDirectory.toAbsolutePath().resolve("logs")`. It is written once per invocation that starts the job, and the account is written as in §2. **It is not refused at start**: an operator's `application-local.yaml` that still sets it, as one machine's does with `E:/vespera_accounts`, starts and runs as before. `vespera label` writes no line for it. The comment for the key in `application.yaml` goes, and the README no longer names it.

So the lines the account adds to `vespera.log` are two: this one, and §2's when a write fails. The three `no invocation account was written: ...` warnings of ADR-198 and ADR-203 go.

### 4. Vespera moves nothing

Accounts already written in an old account folder stay there. `vespera.log` and the files it rolled into at the top of a working directory stay there too, and Logback no longer rolls or deletes them: its 14 days and 2 GB count the files in `logs/` only. The operator moves or deletes them by hand. Vespera does not look for either.

### 5. The counting script counts the logs folder

ADR-212 §3's `folder=log` line counts **every regular file under `logs/`**, recursively, links neither followed nor counted, as `folder=deliverable` counts `deliverable/`: `vespera.log`, the files it rolls into and the accounts. A `vespera.log` an earlier Vespera left at the top is counted in `folder=working-directory` only. In `.claude/hooks/working-directory-counts.mjs` that is one line of `measure`, the `LOG_FILE` test of a name directly in the working directory becoming `here[0] === "logs" && here.length >= 2`, with `LOG_FILE` gone; and the guard's pinned SHA-256 of the script (ADR-212 §5) changes with it. **Both files lie in a closed `.claude` folder, so the change is a draft the operator installs** (ADR-215 §7). `src/test/hooks/working-directory-counts.test.mjs` holds it now, and W01 fails until the draft is installed.

The guard needs no other change: it names no log, and `logs/` is open as the working directory it lies in is (ADR-264 §2).

### 6. What the documents say

- **README**: "Where things live" names `logs/vespera.log` and `logs/invocation-account-*.txt`, and says that files an earlier version left at the top stay there. The account's sentence under "Commands" says every `vespera run` that starts the job writes one in `logs/`, and that `vespera.account-dir` is no longer read. `vespera.progress-interval` is named on its own.
- **`CONTEXT.md`**: *Working directory* names `logs/` among what it holds, and *Invocation account* says it is written there.
- **`docs/running-stage-by-stage.md`** and **`docs/agents/private-paths-guard.md`** name `logs/vespera.log`.
- **`application.yaml`**: the comment and the commented-out `account-dir:` key go. This, `logback-spring.xml`, `InvocationAccount` and the warning's text in `VesperaJobConfiguration`'s javadoc are the implementer's.

### 7. No run id moves

`InvocationAccount` and `VesperaJobConfiguration` are in `pipeline`, which no stage's implementation version spans (`StageModules`), and `logback-spring.xml` and `application.yaml` lie in no module's folder.

## Decided here

1. **A flat `logs/`**, with the accounts beside `vespera.log`, where it could have had `logs/accounts/`. One folder is what the operator named, and an account is one file an invocation, told apart by its name.
2. **The setting is ignored with a warning, and not refused** (§3). The operator's own configuration sets it, and a refusal would stop their next run for a value that no longer matters. The cost: the parameter stays on the constructor for one line, and the line comes back every run until the operator removes the key.
3. **The warning is written when the job starts, by the account**, where it could have been written once when the application starts. It then sits beside the account it is about, and a test reads it through the listener. The cost: `vespera label` says nothing of a key it never read.
4. **No judgement of `logs/`** (§2). What ADR-203 guarded against was an account under a name the hook would refuse; inside a working directory the hook refuses none.
5. **The counting script's `folder=log` takes the whole folder** (§5), accounts included, where it could have kept to `vespera.log`'s names. The operator called every one of them a log.

## Consequences

- **Every invocation that starts the job writes an account**, with nothing set, where until now one was written only on a machine that set `vespera.account-dir` and allowed its folder.
- **An agent reads the account where it reads `vespera.log`**: in a working directory under an allowed root. A working directory outside the allow list stays outside it, the account with it (ADR-264 §2).
- **A working directory gains one folder, and its top loses the log.** An operator who opens `vespera.log` at the top finds the last lines an earlier version wrote.
- **Seventeen tests go with ADR-203's judgement**, and one with ADR-198's "unset or enclosed folder".
- **W01 of the counting script's test fails until the operator installs the draft** of §5.
- **No run id moves** (§7).

## Alternatives weighed

- **The account beside `vespera.log` at the top of the working directory**, as the brief first had it. Superseded by the operator's "working dir/logs".
- **Keep `vespera.account-dir` as an override.** Rejected: the operator asked for defaults and no flags, and nothing in the code needs the folder elsewhere.
- **Refuse a configured `vespera.account-dir` at start.** Rejected (Decided here 2).
- **Move the old files into `logs/`.** Rejected: Vespera moves nothing an operator may be holding open, and an earlier log is theirs.

## What this does not decide

- **A retention rule** for accounts, as ADR-198 left it.
- **Whether ADR-212's script is still wanted**, as ADR-264 left it.
- **The temporary files `TextParts` and the control conversion write in the system temp folder.** They are not logs.
- **`--db-dir <path>` written with a space**, which logs into the working directory that was opened and refuses, as `WorkingDirectoryOption` has it.

## Tests

| Test | What it holds |
| --- | --- |
| `pipeline.InvocationAccountTest.anAccountIsWrittenInTheLogsFolderWithNoSetting` | with no setting, the account is the one file in `<working directory>/logs`, the top of the working directory holds `logs` alone, and no warning is logged |
| `pipeline.InvocationAccountTest.aConfiguredAccountFolderIsIgnoredWithOneLine` | with a folder configured, the folder gains nothing, the account is in `logs`, and the one warning is §3's line naming `<working directory, absolute>/logs` |
| `pipeline.InvocationAccountTest.aFailedWriteNeverFailsTheInvocation` | a file standing where `logs` goes: both calls return, the file is untouched, and the one warning names `<working directory>/logs` |
| `pipeline.InvocationAccountTest.aLineThatIsNotACounterIsWithheld`, `.aRunLineCarriesTheDigest`, `.anUnknownStoredValueIsWrittenAsOther`, `.aSecondAccountInTheSameSecondTakesASuffix` | ADR-198's claims, unchanged, read from `logs` |
| `pipeline.TheAccountReadsAStepsTimesInTheMachinesZoneTest`, `pipeline.TheAccountsCountsByKindSortNothingTest` | their claims, unchanged, read from `logs` with no setting |
| `pipeline.InvocationAccountInvocationTest` | its five whole invocations, with no `vespera.account-dir`, each find their account in `logs`: the marker, the roots and the working directory occur nowhere in it, which holds that the account still names no document |
| `pipeline.TheAccountFolderSettingIsIgnoredInvocationTest.aConfiguredAccountFolderIsIgnoredWithOneLine` | a whole `vespera run` with `vespera.account-dir` set: the folder gains nothing, one account is in `logs`, and the account's one warning is §3's line |
| `LogsFolderIT.theLabelCommandWritesTheLogInTheLogsFolder`, `.theRunCommandWritesTheLogInTheLogsFolder` | the packaged jar, started from a current directory of its own with `--db-dir=<path>`, writes `<path>/logs/vespera.log` with lines in it, no `<path>/vespera.log`, and no `.vespera` in the current directory, for `vespera label` and for a `vespera run` refused for a missing root |
| `src/test/hooks/working-directory-counts.test.mjs` W01 | `folder=log` counts the two logs and the account under `logs/`, and not a `vespera.log` at the top, which `folder=working-directory` counts |

**Removed with ADR-203's judgement** (§2): `noAccountWhereTheFolderIsUnsetOrInsideAWorkingDirectory`, and ADR-203's seventeen: `noAccountWhereTheFolderIsALinkIntoAWorkingDirectory`, `noAccountWhereAParentOfTheFolderLeadsIntoAWorkingDirectory`, `noAccountWhereOnlyTheTextOfAParentStepLiesInsideAWorkingDirectory`, `anAccountIsWrittenThroughALinkToAFolderOutsideEveryWorkingDirectory`, `noAccountWhereTheFolderIsALinkInAWorkingDirectoryThatLeadsOutOfIt`, `anAccountIsWrittenInAFolderThatDoesNotExistYet`, `noAccountWhereTheFolderDoesNotExistYetBeneathALinkIntoAWorkingDirectory`, `noAccountWhereTheFolderIsALinkToNothing`, `noAccountWhereAFolderAboveItIsALinkToNothing`, `noAccountWhereAParentStepClimbsOutOfALinkToNothing`, `noAccountWhereTheFolderIsALinkInALoop`, `noAccountWhereTheWorkingDirectoryCannotBeFollowed`, `noAccountWhereTheFolderIsALinkToNothingInAWorkingDirectory`, `noAccountWhereAParentStepAfterAMissingFolderLeadsOntoALinkIntoAWorkingDirectory`, `anAccountIsWrittenWhereAParentStepAfterAMissingFolderLeadsOntoALinkOutsideEveryWorkingDirectory`, `theWarningForAFailedWriteAfterAParentStepNamesTheFileBeyondTheLink` and `theWarningForAFailedWriteNamesTheFileBeyondTheLink`.

**Not pinned, and why**: that Logback rolls and deletes only within `logs/`, which takes a day or 100 MB; and that the line of §3 is not written by `vespera label`, which starts no job, so that `beforeJob` never runs.
