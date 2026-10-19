# ADR-279 — One `vespera run` per corpus root, by a lock on a file named for its real path, outside every working directory

- **Date**: 2026-10-18
- **Status**: accepted on 2026-10-18.
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md), whole. The **working-directory lock** is unchanged; this is a second lock beside it, taken later, by one command, on a file in another place.
- **Extends**: [ADR-050](0050-the-pipeline-has-exclusive-access-to-the-corpus.md). ADR-177 left exclusive access to the corpus to the operator. The engine now refuses a second `vespera run` walking a corpus root another holds; any other program on the corpus stays the operator's to arrange.
- **Narrows**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md)'s first Consequence, "no guard against … a root reopened under a different working directory". Two invocations on one corpus root at once are refused (§1). One after the other, under two working directories, is still not guarded, and no root is stamped into a database.
- **Keeps**: [ADR-066](0066-the-command-line-names-the-root-configuration-is-the-fallback.md). The root is known once `run` has read its argument or `vespera.corpus-root`, and the lock is taken there (§5), so nothing parses the command line ahead of picocli.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): the refusal is `run`'s exit code, 1.
- **Keeps**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md): a folder holding `vespera.db` or `vespera.lock` is a working directory to the agent guard, and the folder of corpus-root locks holds neither (§3).
- **Keeps**: [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) and [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md): the lock is in `pipeline`, which no stage's version names, so no run id moves (§8).
- **Keeps**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md): an invocation refused here writes no progress page, so it names none and opens none.
- **Read with**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §3 and §4, which read this lock's holder line (§9).
- **Read with**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §2, whose refusal of a misplaced deliverable root comes just before the lock is taken (§5), so a run refused for its deliverable root takes no corpus-root lock.
- **Settles**, with ADR-278, [#559](https://github.com/algernon28/vespera/issues/559).

## Context

ADR-177 holds a working directory: a second invocation naming the same one is refused before it opens `vespera.db`. Nothing holds a corpus root. Two `vespera run` invocations naming two working directories and one corpus root both start, both walk it, and each converts and judges the same corpus into a ledger of its own, at the same time, through the same converter and the same model server. ADR-054 accepted a root reopened under another working directory as the operator's error; a control panel that starts invocations for the operator (ADR-278) makes the concurrent case one click away, and nothing in it or in the jar would say so.

A file that lives in no working directory is needed, since the two invocations share none, and in no archive, since Vespera writes nothing there (ADR-054). The places considered on Windows:

- **The temporary directory.** Storage Sense deletes files in it on its own schedule, and deleting a held lock's file defeats the lock (ADR-177 Context: the lock is on the file, and a replacement is another file).
- **`C:\ProgramData`.** A file one user creates there cannot be write-locked by another user without granting it an access-control entry first.
- **The user's home.** Written by every invocation of that user, kept by no cleaner, and outside every archive and working directory.

## Decision

### 1. `vespera run` holds its corpus root

`vespera run` takes an operating-system lock on a file named for its corpus root, and holds it until its process ends, so a second `vespera run` walking the same corpus root is refused, whatever working directory it names. This is the **corpus-root lock**. It is a second lock beside ADR-177's working-directory lock, which every invocation still takes first.

### 2. The same corpus root

The same corpus root is the same directory once its spelling, its case and any junction or link on the way are resolved. The lock's **key** is the root's real path, `Path.toRealPath()`. A root that cannot be resolved, because it is not there (and the walk will fail on it anyway), is keyed by its absolute, normalised path, `toAbsolutePath().normalize()`. A root inside another is a different root, with a key of its own.

### 3. Where the file lies, and its name

The files lie in one folder per user, outside the archive and every working directory: `<user home>/.vespera-root-locks` (`user.home`), or the folder the property **`vespera.corpus-root-lock-folder`** names, which tests use to move it. Blank counts as unset. The folder is created when it is not there.

One file per key, named by the first **16** lowercase hexadecimal digits of the SHA-256 of the key's string form in UTF-8, followed by `.lock`: `3f9a0c41d27be865.lock`. The rule is exact, so that another program can find a root's file from the root (§9). Sixteen digits are 64 bits: a collision between two of one user's roots makes those two refuse each other, a refusal too many and never one too few, and among a thousand roots its odds are about 3 in 10^14. The name says nothing of the root to a person listing the folder; the holder line inside does.

The file is never named `vespera.lock`, and the folder never holds `vespera.lock` or `vespera.db`: the agent guard takes a folder holding either for a working directory (ADR-196).

### 4. Taking it

As ADR-177 §1 takes `vespera.lock`:

- The file is opened for writing, created if needed, and locked with `tryLock(Long.MAX_VALUE - 1, 1, false)`, one byte far past the end, so the holder line stays readable to every other program while it is held.
- **Acquired:** the file is truncated and one UTF-8 line written from byte 0, ended by the platform's line separator:

  ```
  pid=<pid> started=<ISO-8601 date and time, with offset, to the second> command=<the arguments, joined by single spaces> root=<the key> working-directory=<the working directory, absolute>
  ```

  ADR-177's three fields come first, in its order, and the two after them are for a person to read; line breaks in the arguments become spaces. The lock is held for the life of the JVM, nothing releases it before, and the operating system releases it however the process ends. The file is never deleted, and its existence means nothing; only the lock does.
- **Refused:** `tryLock` returned `null`, or threw `OverlappingFileLockException` because this JVM holds it already. The line is read without the lock, and the invocation is refused (§6). A line that cannot be read or is blank is said as `its details could not be read`.
- **The folder or the file cannot be created or opened:** the invocation is refused, and never walks unlocked. One line on standard error, exit 1:

  ```
  the corpus-root lock could not be taken in the folder <folder> (<the reason the exception gives>); make that folder one this user can write, or name another in vespera.corpus-root-lock-folder, then run the same command again. vespera run walks no corpus root it has not locked.
  ```

### 5. Who takes it, and when

Only `vespera run`, the one command that walks a root; `vespera label` names none. In `run`, after `--db-dir` is checked against the working directory, the root is known and the deliverable root is refused or allowed (ADR-276 §2), and before anything else: before the `Progress page:` line, before the root is logged, before the progress page is written and before the job starts. A refused invocation writes no progress page, opens no browser and starts no walk. The working-directory lock was taken before, while the environment was prepared (ADR-177). The seed folder, which is walked too, is not locked.

### 6. The refusal

One line on standard error from `run` itself, as its other refusals are, and exit 1 (`CommandLine.ExitCode.SOFTWARE`):

```
another Vespera invocation is already walking the corpus root <root> (<holder's line>); wait for it to finish, or stop it, then run the same command again. Two invocations on one corpus root would convert and judge one corpus twice at once, into two ledgers.
```

`<root>` is the root as this invocation names it. The holding working directory is named by the holder line's `working-directory=` field, quoted whole and not parsed out of it, since a path may hold any character a separator would. Vespera prints nothing else on standard error; the JDK's four `WARNING` lines about sqlite-jdbc's native load come before it, as on every invocation that has opened `vespera.db` (ADR-278 Context), and are the JDK's.

### 7. What it covers

The lock is handed to `run` by `VesperaApplication.main`: the packaged jar and an IDE's run configuration take it. A context built by Spring's test support does not go through `main`, provides none, and takes none, as ADR-177 §1 says of its own; so in-process tests that run `vespera run` over one root many times in one JVM are not refused by it.

### 8. Run ids

The lock's classes are in `pipeline`, whose classes no stage's version names (ADR-222, ADR-226; `StageModules` lists none). No run id moves. `PipelineHoldsOnlyTheRulesOnRecordTest` lists each of them as holding no rule, in the same change.

### 9. The control panel

The control panel reads this lock's holder line as it reads `vespera.lock`'s, with ADR-278 §4's held rule: held while the `pid` is alive and that process started no later than `started` plus 1 second. It finds the file from the corpus root by §2 and §3, in the default folder, since it passes no property to the jar (ADR-278 §1).

## Alternatives refused

- **The lock in the temporary directory, or in `C:\ProgramData`.** Context says why: the first can be deleted while held, the second cannot be locked by a second user without access-control work.
- **A file in the corpus root.** Vespera writes nothing into an archive (ADR-054).
- **The key as typed.** One directory spelled two ways, or reached through a junction, would be two roots.
- **A root inside a held one refused too.** A key is a directory, and nested roots would need every held key compared as a prefix; left as a known limit.
- **Skipping the lock when its folder cannot be used.** An invocation would walk unguarded and say nothing.
- **Taking the lock while the environment is prepared, as ADR-177's is.** The root is not known until `run` reads its argument (ADR-066), and reading it earlier is a second parse of the command line.

## Consequences

- **A second `vespera run` on a corpus root another invocation walks is refused**, with one line naming the root and quoting the holder line with its working directory, before it writes a progress page or walks anything. It has opened its own working directory's `vespera.db` and `logs/vespera.log` by then, as every invocation the working-directory lock admits does.
- **`vespera label`, `--help` and `--version` take no corpus-root lock.**
- **Known limits.** Nested roots both walk. A mapped drive and its UNC path are two roots. Two operating-system users on one root are not excluded, since each has a folder of their own. Deleting a lock's file while it is held defeats it, as ADR-177 says of `vespera.lock`. An invocation started with `vespera.corpus-root-lock-folder` moved is not seen by an invocation, or a control panel, reading another folder.
- **The folder stays in the user's home**, one small file per root ever walked, each holding its last holder's line.

## Tests

The implementer creates, in `io.algernon.vespera.pipeline`: `CorpusRootLock` (its `FOLDER_PROPERTY` = `"vespera.corpus-root-lock-folder"`, `static Path folderNamedBy(String configured)`, `CorpusRootLock(Path folder)`, `static Path key(Path corpusRoot)`, `static String fileName(Path key)`, `void take(Path corpusRoot, Path workingDirectory, String[] args)`, and a package-private `static void release()` for tests), `CorpusRootInUseException(Path corpusRoot, String holder)` and `CorpusRootLockUnavailableException(Path folder, Exception cause)`; `VesperaApplication.main` hands a `CorpusRootLock` to the context, and `run` takes it where §5 says.

| Method | What it holds |
| --- | --- |
| `pipeline.CorpusRootLockKeyTest.spellingsOfOneDirectoryGiveOneKey` | a trailing separator, `.` and `sub/..` spellings of one directory give one key, its real path (§2) |
| `pipeline.CorpusRootLockKeyTest.caseSpellingsOfOneDirectoryGiveOneKey` | on a file system that folds case, the root in capitals keys as its real path, compared as text (§2) |
| `pipeline.CorpusRootLockKeyTest.twoDirectoriesGiveTwoKeys` | two siblings, and a root and one inside it, give different keys (§2) |
| `pipeline.CorpusRootLockKeyTest.aJunctionToTheRootGivesTheRootsKey` | on Windows, a junction to the root keys as the root (§2) |
| `pipeline.CorpusRootLockKeyTest.aLinkOnTheWayGivesTheRootsKey` | a link to the root's parent, followed to the root, keys as the root (§2) |
| `pipeline.CorpusRootLockKeyTest.aRootThatIsNotThereKeysByItsAbsoluteNormalisedPath` | a missing root, relative or with `..`, keys by its absolute normalised path (§2) |
| `pipeline.CorpusRootLockKeyTest.theFileIsNamedBySixteenDigitsOfTheKeysSha256` | the name rule exactly, and never `vespera.lock` or `vespera.db` (§3) |
| `pipeline.CorpusRootLockTest.theFolderDefaultsToOneInTheUsersHome` | unset or blank names `<user home>/.vespera-root-locks`; a value names itself (§3) |
| `pipeline.CorpusRootLockTest.takingACorpusRootRecordsTheHolder` | the folder is created, holds the root's one file, and its one line has §4's shape and values |
| `pipeline.CorpusRootLockTest.theFolderHoldsNeitherFileAWorkingDirectoryIsKnownBy` | after two takes, every name in the folder is §3's, none `vespera.lock` or `vespera.db` |
| `pipeline.CorpusRootLockTest.aSecondTakeInTheSameProcessIsRefused` | refused with §6's line exactly, the holder line left as it was |
| `pipeline.CorpusRootLockTest.oneCorpusRootSpelledTwoWaysIsOneLock` | a second spelling of a held root is refused (§2, §4) |
| `pipeline.CorpusRootLockTest.aCorpusRootHeldByAnotherProcessIsRefused` | a second process's hold refuses this one, naming its pid and working directory; the line is readable while held |
| `pipeline.CorpusRootLockTest.aKilledHolderLeavesTheCorpusRootFree` | after a kill the next take succeeds and rewrites the line (§4) |
| `pipeline.CorpusRootLockTest.anotherCorpusRootIsAccepted` | two roots are held at once (§1) |
| `pipeline.CorpusRootLockTest.aCorpusRootInsideAHeldOneIsAnotherRoot` | a root inside a held one is taken (§2, Consequences) |
| `pipeline.CorpusRootLockTest.aFolderThatCannotBeMadeRefusesTheTake` | a regular file in the folder's place refuses with §4's line, naming the folder and the property |
| `pipeline.CorpusRootLockTest.theRefusalSaysWhatItCouldRead` | §6's line with a holder line, and with none readable |
| `pipeline.CorpusRootLockTest.theHoldersStartIsNoLaterThanStartedPlusOneSecond` | ADR-278 §4's rule holds of this lock's line (§9) |
| `pipeline.CorpusRootInUseIT.aRunOnACorpusRootAnotherInvocationWalksIsRefused` | the packaged jar, `run` on a root this JVM holds from another working directory: exit 1, exactly one line on standard error naming the root, this pid and the holding working directory, and no `progress.html` (§5, §6) |
| `pipeline.CorpusRootInUseIT.aRunOnAnotherCorpusRootIsAccepted` | the jar on another root writes its progress page and prints no refusal |
| `pipeline.CorpusRootInUseIT.aRunAfterTheHolderIsKilledIsAccepted` | after the holding process is killed, its file left behind, the jar on that root writes its progress page and prints no refusal |

`pipeline.HoldsTheCorpusRoot` is the second process both classes start. Every test points the folder at a temporary directory, the unit tests through the constructor and the integration test through `-Dvespera.corpus-root-lock-folder`; the integration test launches the jar with `--enable-native-access=ALL-UNNAMED`, so the JDK's warnings (§6) are not counted as Vespera's lines.

## What this does not decide

- **A root walked under two working directories one after the other.** ADR-054's gap, narrowed and not closed.
- **A lock on the seed folder**, or for programs other than Vespera on the corpus (ADR-050).
- **Nested roots, mapped drives against UNC paths, and two users on one root** (Consequences).
