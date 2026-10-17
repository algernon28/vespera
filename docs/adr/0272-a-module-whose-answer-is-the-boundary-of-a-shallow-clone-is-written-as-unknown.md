# ADR-272 — A module whose answer is the boundary of a shallow clone is written as unknown

- **Date**: 2026-10-17
- **Status**: accepted on 2026-10-17.
- **Amends**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), in this place and no other: the mechanism, `git log -1 --format=%H -- src/main/java/io/algernon/vespera/<module>`, which is now asked for the commit and its parents, and whose answer is written as `unknown` where it is the boundary of a shallow clone (§1, §2). That the version is the last commit touching the module, that it is computed at build time, that dependency versions are excluded, and the accepted failure stand.
- **Keeps**: ADR-058's stance that a build git cannot answer for still compiles, writes `unknown`, and is refused at mint time by `ImplementationVersions`; [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md), under which §4 is the decision that puts Groovy on the test classpath.
- **Settles** [#543](https://github.com/algernon28/vespera/issues/543).

## Context

`.mvn/scripts/implementation-versions.groovy`, run by gmavenplus when the build generates its resources, writes one `<module>=<sha>` line for each folder under `src/main/java/io/algernon/vespera`, the SHA being what `git log -1 --format=%H -- <module path>` prints. It writes `unknown` only where git exits non-zero or prints something other than forty hexadecimal characters, and `ImplementationVersions` refuses to mint a run against `unknown`.

**A shallow clone answers wrongly, with a well-formed SHA.** The oldest commit a shallow clone holds, its boundary, is held without its parents, so git compares it with nothing and reports it as adding every file in its tree. `git log -1 -- <module path>` therefore answers with the boundary for every module whose last change lies before the clone's window. The answer is forty hexadecimal characters, so nothing is written as `unknown`, nothing is logged, and a jar built from that clone mints a run of every stage it reaches under a version that names a commit that never touched the module. In a clone one commit deep every module is answered with that one commit. A clone deeper than the last change to every module answers every module correctly.

**A commit inside the window answers exactly.** Its parents are in the clone, so the change git reports for it is the change it made. Only the boundary claims files it did not change, and git reports a shallow clone as one: `git rev-parse --is-shallow-repository` prints `true`.

The build's checkout in CI holds the whole history. A build from a shallow clone, on a workstation or for a release, does not.

## Decision

### 1. The script asks for the commit and its parents, and whether the clone is shallow

For each module the script runs `git log -1 -- src/main/java/io/algernon/vespera/<module>` in the project's base folder with the one format argument `--format=%H %P`: the commit's SHA, then the SHAs of its parents as the clone holds them, none for a commit held without parents. Once, before the modules, it runs `git rev-parse --is-shallow-repository` there; the clone is shallow when that exits zero and prints `true`, and is not otherwise.

### 2. A boundary answer is written as `unknown`, with the same warning

A module is written as `unknown` where any of these holds:

- git exits non-zero;
- the first field is not forty lowercase hexadecimal characters;
- **the clone is shallow and the answering commit has no parent.**

Each is the same line in the generated file, `<module>=unknown`, and the same warning, `No commit found for module <module>; recording it as unknown`. `ImplementationVersions` then refuses any stage that names the module, as it refuses any `unknown`; nothing in `src/main/java` changes.

A module answered by a commit with a parent keeps that commit's SHA, shallow clone or not. In a clone that is not shallow, a commit with no parent is the repository's real first commit, and keeps its SHA.

### 3. The script says so

The script's opening comment states the shallow case beside the existing one: a module git answers only with the boundary of a shallow clone is written as `unknown`, because the boundary is reported as adding every file.

### 4. The test runs the script itself, with Groovy on the test classpath

`org.apache.groovy:groovy`, at the `groovy.version` the gmavenplus plugin already runs, is a dependency of `test` scope. The test evaluates the real script through `GroovyShell`, binding `project` to the clone's base folder and a build folder of its own and `log` to a recorder, against repositories it builds and clones with git. This record is the decision ADR-046 asks a dependency for. It is a test of the surefire suite: git is what the build already runs to stamp the modules, and it needs no Docker and no packaged jar.

## Decided here

1. **Per module, not per build.** A module answered by a commit inside the window keeps its version, so a clone deep enough for every module's last change builds a jar that mints as a full clone's does.
2. **The answering commit having no parent is the test**, where the script could have read `.git/shallow` for the boundary's SHA. Git states both, and asking `%P` beside `%H` is one field more in the call already made; the file is git's own layout.
3. **The existing warning, unchanged**, where a line of its own could have named the shallow clone. The operator reads the same consequence from both: the module has no version, and a stage naming it is refused.

## Consequences

- **A jar built from a shallow clone refuses every stage that names a module whose last change lies before the window**, where it minted runs under a commit that never touched the module. A clone one commit deep refuses every stage, the module changed by that one commit included: its answer is the boundary. `git fetch --unshallow` before the build gives every module its version.
- **A real first commit in a shallow clone is written as `unknown` too.** It can be the answer only where every change to the module is in that one commit and the clone holds the whole history while still being marked shallow, and the cost is a refusal, not a wrong version.
- **On a build from the whole history nothing changes.** Every answer has a parent, but for a module changed only by the first commit, and the script lies in no module's folder, so no module's version moves and no run id moves.
- **A test that mints through the build's own versions cannot pass in a shallow checkout whose window misses a module.** Those that build `ImplementationVersions` with its no-argument constructor, or take the real bean from a Spring context, refuse as the jar does. The suite is run on a checkout deep enough for every module's last change, as CI's is.
- **The test classpath carries Groovy** (§4). Nothing shipped does.

## Alternatives weighed

- **Every module `unknown` whenever the clone is shallow.** Rejected: it throws away answers that are exact. A clone deep enough for every module's last change answers every module correctly, and would build a jar that refuses every stage.
- **Fail the build in a shallow clone.** Rejected: ADR-058 has a build git cannot answer for compile and refuse at mint time, and a shallow clone is one more such build.
- **Hold the decision with a test that starts a Maven build, or reads the script's text, or starts the Groovy jar from the local Maven repository.** Rejected: a nested build is minutes and a second build tool inside the suite; the script's text holds words and not what git answers; the local repository's place is a setting of each machine.

## What this does not decide

- **What CI's checkout depth is.** Its comment in `build.yml` describes the shallow case and is corrected with this record; the depth stays the whole history.
- **History with merges.** The test builds a linear history, as `main`'s is.

## Tests

| Test | What it holds |
| --- | --- |
| `TheBuildRecordsNoVersionAShallowCloneCannotGiveTest.aFullCloneRecordsEachModulesLastCommit` | in a clone of the whole of a three-commit history, each of three modules is written with the commit that last changed it, the first commit included, and nothing is warned |
| `TheBuildRecordsNoVersionAShallowCloneCannotGiveTest.aCloneOfTheLatestCommitRecordsEveryModuleAsUnknown` | in a clone one commit deep, all three are written as `unknown` and each has the warning, in the order of the modules |
| `TheBuildRecordsNoVersionAShallowCloneCannotGiveTest.aCloneTwoCommitsDeepKeepsTheModuleChangedInsideIt` | in a clone two commits deep, the module changed by the latest commit keeps its SHA and the two answered by the boundary are written as `unknown`, each with the warning |

**Not pinned, and why**: a real first commit in a clone marked shallow, whose `unknown` is a consequence of §2 and no rule of its own; and git exiting non-zero, which ADR-058's mechanism already wrote as `unknown`.
