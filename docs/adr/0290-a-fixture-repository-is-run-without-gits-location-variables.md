# ADR-290 — A fixture repository is run without git's location variables

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-272](0272-a-module-whose-answer-is-the-boundary-of-a-shallow-clone-is-written-as-unknown.md): what the build's stamping script writes, and that its test builds its repositories with git.
- **Settles** [#591](https://github.com/algernon28/vespera/issues/591).

## Context

`TheBuildRecordsNoVersionAShallowCloneCannotGiveTest` builds a repository of three commits with `git`, started through `ProcessBuilder` in a temporary folder. A child process inherits its parent's environment, and git reads `GIT_DIR`, `GIT_WORK_TREE`, `GIT_INDEX_FILE` and the other `GIT_*` variables before it looks at the folder it runs in. A build started from a process that carries them, as the children of a git hook do, therefore has the fixture's `add` and `commit` land in the repository `GIT_DIR` names, which in a checkout is the repository the tests run in.

## Decision

### 1. Every git a fixture runs starts without any variable named `GIT_*`

`FixtureGit` in the test sources starts each command from the process's environment with every variable whose name begins `GIT_` removed. It passes an identity, `commit.gpgsign=false` and `init.defaultBranch=main` to every command. A test that spawns git for a fixture calls it and builds no `ProcessBuilder` of its own for git.

### 2. The test names an outer repository and asserts it is untouched

`FixtureGitStaysInItsFolderTest` builds a throwaway outer repository under a temporary folder, hands the fixture an environment with `GIT_DIR` and `GIT_INDEX_FILE` naming it, and asserts the outer repository keeps its head and has no change waiting, and that the fixture's commit is in the fixture's own folder.

## Decided here

1. **Every `GIT_*` variable is removed, not a list of the location ones**, so a variable git gains, or `GIT_CONFIG_*` injection, cannot redirect a fixture either.
2. **The stamping script is not changed.** It only reads, in the project's base folder, and a build run from a hook wants the repository the hook names.

## Consequences

- **Nothing shipped changes**, and no run id moves.
- **The stamping script's test still reads with an inherited `GIT_DIR`**: the script runs inside the test's JVM, whose environment cannot be changed, so a gate run under `GIT_DIR` can stamp from the outer repository. It writes nothing there.
- **No other `ProcessBuilder` in the test sources starts git**: the rest start the jar or the control panel.
