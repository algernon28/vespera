# ADR-196 — No agent reads the operator's documents, and an allow-list hook that fails closed refuses every other path

- **Date**: 2026-10-05
- **Status**: accepted
- **Rests on**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md), for the two places the rule names: the corpus root, and the working directory kept apart from it. [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md), for `vespera.lock`, one of the two files a working directory is recognised by.
- **Related**: [#423](https://github.com/algernon28/vespera/issues/423), [#424](https://github.com/algernon28/vespera/issues/424) and [#425](https://github.com/algernon28/vespera/issues/425), which take away the reasons an agent had for reading a document. This record settles none of them.

## Context

An archive Vespera curates can hold sensitive documents. A document is meant to be read only by the local models Vespera runs: Docling, and the models Ollama serves. A Claude Code session is a hosted model, so anything it opens leaves the machine.

Earlier sessions did open them. They labelled relevance by reading `relevance-labelling.html`, which carries the opening of sixty documents, and they read `vespera.log` and `vespera.db` to diagnose runs. Nothing said not to, and nothing stopped it.

A written rule alone is not enough. A session that has been told the rule can still pass a path to a tool without noticing where it leads, and a subagent starts with less context than the session that launched it.

The first version of the hook was registered and gated before it had a committed test. The gate found forms it let through: a drive path with a doubled separator, a relative path in a shell command, a search pattern, and others listed in §4. This record states what the hook must do, and `src/test/hooks/private-paths-guard.test.mjs` holds it to that.

## Decision

### 1. The rule

**No agent opens the operator's documents.** These are:

- **an archive**: anything beneath a corpus root, walked or not;
- **a seed set**: the folder `seedFolder` names;
- **a working directory, and everything in it**: `vespera.db`, `vespera.lock`, `vespera.log`, `profile.yaml`, the pages the stages write for the operator, `relevance-labels.yaml`, and the deliverable.

The working directory is on the list because what is in it is derived from the documents and names them: paths, titles, openings, and prose written from them.

The rule binds every agent and subagent, whatever its task, a run it is driving included. Labels, floors and approvals are the operator's, or a local model's once #423 is built. The rule is stated in `AGENTS.md`, and it is the whole of the protection wherever §5 says the hook does not reach.

### 2. A hook enforces it with an allow list

`.claude/settings.json` registers one `PreToolUse` hook for eight tools: Read, Grep, Glob, Edit, Write, NotebookEdit, Bash and PowerShell. Claude Code passes the call as JSON on stdin. Exit 0 lets it through and exit 2 refuses it.

The hook refuses a call when any path of the call is:

- **outside the allow list.** `.claude/allowed-paths.txt` lists the repository, the temp folder, and four folders under the home folder. `.claude/allowed-paths.local.txt`, gitignored, adds one machine's own.
- **inside a working directory**, wherever that is, under an allowed root too. A working directory is any folder holding `vespera.db` or `vespera.lock`.

It is an allow list and not a list of archives, so an archive on a new path is refused without anyone naming it. The cost of a mistake in the list is a refusal, which the operator lifts by adding the path to the local list.

**The allow-list decision is made from the path's text, before the file system is asked about it.** A path outside the list is never touched, so a refused UNC path costs no network lookup.

### 3. The paths of a call

The hook reads every place a call can carry a path:

1. **A file tool's own field**: `file_path`, `path`, `notebook_path`. A relative one is read against the call's `cwd`.
2. **A search tool's pattern fields**: Grep's `glob` and Glob's `pattern`. An absolute one is checked as written. A relative one is read against `path`, or against `cwd` when there is no `path`. A pattern that climbs out with `..` is checked where it lands.
3. **The directory a call starts in.** A Grep or Glob with no `path` searches `cwd`, so `cwd` is a path of that call. A shell command runs in `cwd`, so `cwd` is a path of that call too. A call whose `cwd` is outside the allow list, or inside a working directory, is refused whatever else it names.
4. **A search that starts above a working directory.** A Grep or Glob whose root (`path`, or `cwd` without one) holds a working directory at any depth is refused, because a recursive search reaches into it.
5. **A shell command's text**, in §4's forms.

A link is followed before the check: a junction or symlink under an allowed root that leads outside the allow list is outside it.

The checkout's own path may hold a space, and the hook still finds its allow list.

### 4. What is read in a shell command

A command is text, and the hook reads paths out of it. It must read:

- **a drive path**, `Q:/x` or `Q:\x`, with one separator after the colon or several (`Q://x`, `Q:\\x`). The `//` after a URL's scheme is not one: `https://example.com/x` is let through.
- **a bare drive**, as in `cd Q:`.
- **a Git Bash drive path**, `/q/x`, and its `/proc/cygdrive/q/x` form.
- **a UNC path**, `//host/share/x` or `\\host\share\x`.
- **a home path**: `~`, `$HOME`, `${HOME}`.
- **a path headed by a variable of the environment**: `$USERPROFILE/x`, `${USERPROFILE}/x`, `$env:USERPROFILE\x`. The variable is replaced by its value in the hook's own environment and the result is checked like any other path, so `$USERPROFILE/.m2` is let through and `$USERPROFILE/Documents` is refused. A variable with no value there is a path built at run time (§5).
- **a relative path**, read against `cwd`: `../../x` is refused when it lands outside the allow list, and `working-directory/report.html` when it lands in a working directory. A relative path to a repository file is let through.
- **a path that ends in dots**: `<repository>/..` is the repository's parent. The punctuation that ends a sentence is trimmed from a path (`see <path>, and <path>.`), and `..` is not punctuation.

`/dev/null` is let through.

### 5. What the hook does not cover

These are stated so nobody takes the hook for the whole of the protection. For each, §1's written rule is all there is.

- **`mcp__*` tools.** A file tool an MCP server offers is not one of the eight, and the hook never sees its call.
- **Monitor**, which runs a shell command and is not one of the eight.
- **A path a script builds at run time.** `cat "$(some-command)"`, a variable set earlier in the same command, a loop over a listing, a program that opens a path it computed. The hook reads text, and the path is not in it.
- **A shell wildcard.** `cat */vespera.log` names no folder the hook can check.
- **A recursive shell command started above a working directory.** §3.4 holds for Grep and Glob. `grep -r`, `rg` and `find` in a shell command are read only for the paths written in them.
- **A session started inside a worktree whose own `.claude/settings.json` registers no hook.** The fallback in §6 is for a worktree that has the registration and lacks the hook's files. A worktree cut from a commit older than the registration has neither, and Claude Code reads that worktree's settings.
- **Other rooted paths in a shell command.** `/tmp/x`, `/etc/x` and `/usr/bin/x` under Git Bash are not decided here (see "What this does not decide").
- **Any agent that is not Claude Code.** The hook is Claude Code's. Another tool working in this checkout is bound by `AGENTS.md` alone.

### 6. The hook fails closed

Claude Code blocks a call only on exit 2. Any other non-zero exit is a hook that failed, and the call goes through. So every way the hook can fail to reach a decision must end in exit 2.

- **Input it cannot read**: empty stdin, text that is not JSON, JSON that is not an object, and JSON that names no tool.
- **`private-paths-guard.mjs` missing.** `run-private-paths-guard.sh` uses the worktree's own copy, else the main checkout's, found through git's common directory. With neither, it refuses.
- **`node` not on `PATH`**, or the guard ending with any exit code but 0 and 2.
- **The wrapper itself failing.** The command registered in `settings.json` starts the wrapper, and must turn any exit code of the wrapper but 0 into 2. A wrapper it cannot find refuses.

### 7. The test

`src/test/hooks/private-paths-guard.test.mjs` is a table of cases, each a tool call given to the real entry point, `run-private-paths-guard.sh`, claiming exit 0 or exit 2. It is started with `node --test src/test/hooks/private-paths-guard.test.mjs` and needs Node 22, bash and git, and nothing installed.

- **It is a Node test and not a JUnit one.** The guard is a Node script behind a bash wrapper, and `node:test` needs no dependency and no `package.json`. A JUnit class that starts bash and node needs external tools, which makes it an `*IT` under Failsafe, and the NTFS job, where most of these path forms live, runs no integration test.
- **It names no path of the operator's.** It copies the hook's files into a checkout it builds under the temp folder and starts that copy, with a temp folder and a home folder of its own, so a machine's local allow list cannot change a result. A working directory is a fixture folder holding an empty `vespera.db`.
- **Windows forms are not started elsewhere.** A case about a drive letter, a Git Bash path, a UNC path or PowerShell is marked as not started on another platform, with the reason in its name. The rest run on both.

**CI is to run it in both jobs of `build.yml`**, on NTFS because the forms are Windows forms, and on Linux for the cases that are portable. That step is owed with the fix and is not in the change that adds this record, because the cases in §3 and §4 that the first version fails would turn the build red until the hook is fixed.

## Consequences

- **A search of a folder that holds a working directory is refused.** With the default `vespera.working-dir`, a run started from this checkout writes `.vespera` in it, and from then on a Grep or Glob of the repository root is refused on that machine. The search has to name `src`, `docs` or another folder. This is the largest cost in this record, and it is paid on purpose: a Glob of the root lists the deliverable's pages by name.
- **Some harmless commands are refused.** A commit message that quotes `../x`, a `sed` expression that reads as a relative path out of the repository, a path with a space in it cut short at the space. Each costs a refusal and a rewording.
- **A shell command started in a working directory is refused**, `ls` included. A session cannot work from inside one.
- **Nothing under `src/main` changes, and no run id moves.**
- **`AGENTS.md` and the hook's own comments say more than this record does.** "Every file and search tool" is eight tools, and "a mistake costs a refusal, never an exposed document" holds only inside what §3 and §4 cover. They are to be narrowed to §5 when the hook is fixed.

## Alternatives weighed

- **A list of the archives to refuse.** Rejected: an archive on a path nobody listed is open, and the failure is silent.
- **The written rule alone.** Rejected, in Context: it had not been enough.
- **Refusing every shell command that carries a variable or a relative path.** Rejected: most commands a session runs carry one, and a hook that refuses the build is a hook that gets removed.
- **Reading a working directory's contents but not its documents**, for instance the log. Rejected: the log names documents, and #424 is where a summary that names none is decided.

## What this does not decide

- **`/tmp/x`, `/etc/x`, `/usr/bin/x` and other rooted paths in a shell command.** Under Git Bash `/tmp` is the temp folder, which is allowed, and `/etc` and `/usr` are Git's own install, which is not. Refusing them all breaks `> /tmp/x.log`. Mapping them needs the mount table. Today they are not read, and no case pins them either way.
- **What a shell command's absolute path is on a machine that is not Windows.** The operator's machine is Windows, and so are the forms in §4.
- **A single letter and a colon that is not a drive**, as in `x: 1` inside a quoted YAML snippet. Whether the bare-drive rule in §4 refuses it is left to the fix. A refusal there is a cost, not an exposure.
- **A wrapper that is present and empty.** It ends with exit 0 having checked nothing. No case pins it.
- **Whether the `mcp__*` file tools and Monitor join the matcher.** They are uncovered today (§5).
