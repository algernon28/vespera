# ADR-196 — No agent reads the operator's documents, and an allow-list hook that fails closed refuses every other path

> **Partly amended — see [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md).** These passages below are no longer the whole of the decision, and that record says what replaces each: §3's "A link is followed before the check", which was false of a `..` after a link; §4's cut at `,`, `{` and `}`, its clause on a path headed by `@`, and its "Three things in it count more than a shell would"; §5's list, which gains three forms; §6's "A hook that runs out of time is not known to fail closed", which Claude Code's documentation now answers: it does not block the call; §6's "about 0.36 s" and "At a third of a second it costs nothing to keep", which hold only for tokens that do not exist, the worst case being about seven seconds; and Consequences. §7's count of cases is also behind: the table holds more, and that record names them. **And see [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md)**, which amends three more: §2's first bullet, "four folders under the home folder", of which the home folder's `.claude` is no longer one; §2's one judgement of a path whichever tool names it, since two kinds of place are now read and not written, and with it §4, which gains one reading of every token of a shell command's text; and §5's list, which gains that record's §8. Everything else in this record stands.

> **Partly amended — see [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), accepted on 2026-10-08 and in force since its counting script and the guard's admission landed with it.** These passages below are no longer the whole of the decision, and that record says what replaces each: §1's working-directory bullet and the sentence after the list, which no longer refuse the counts one pinned script prints; §2's second bullet, which gains one exact command the hook admits; §2's "A path outside the list is never touched", since the guard asks the file system about that command's working directory, which need not be on the list, once every other check has passed; and the alternative weighed "Reading a working directory's contents but not its documents". Every other path in a working directory is refused as before.

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

**The allow-list decision is made from the path's text, before the file system is asked about it.** A path outside the list is never touched, so a refused UNC path costs no network lookup. Nothing beneath a refused folder is asked about either: when the current directory is itself refused, no token is looked up under it.

### 3. The paths of a call

The hook reads every place a call can carry a path:

The call's current directory is the `cwd` field of the hook's input. A call that carries none is read with the checkout as its current directory.

1. **A file tool's own field**: `file_path`, `path`, `notebook_path`. A relative one is read against the current directory.
2. **A search tool's pattern fields**: Grep's `glob` and Glob's `pattern`. What is checked is the part of the pattern before its first wildcard, which is the folder the search begins in. An absolute one is checked as written. A relative one is read against `path`, or against the current directory when there is no `path`. A pattern that climbs out with `..` is checked where it lands.
3. **The current directory.** A Grep or Glob with no `path` searches the current directory, so it is a path of that call. A shell command runs in it, so it is a path of that call too. Such a call is refused when its current directory is outside the allow list or inside a working directory, whatever else it names. A file tool, or a search that names a `path`, is checked on the paths it names and not on its current directory.
4. **A search that begins above a working directory.** A Grep or Glob whose root (`path`, or the current directory without one) or whose pattern's folder holds a working directory at any depth is refused, because a recursive search reaches into it.
5. **A shell command's text**, in §4's forms.

A link is followed before the check: a junction or symlink under an allowed root that leads outside the allow list is outside it.

**The walk down from a search root is bounded, and refuses when it reaches its bound.** It goes breadth-first and stops at the first folder holding `vespera.db` or `vespera.lock`. It looks at no more than 10,000 folders. It never enters a folder named `.git`, `node_modules` or `target`, and never follows a symlink or a junction. A root with more folders than the bound is refused, with a line saying to search a narrower folder, because a working directory beneath it cannot be ruled out. The test builds 10,050 folders to hold that; no switch lowers the bound for a test, because such a switch would be a way round it. Those folders lie flat in one folder, so the case holds the refusal at the bound and says nothing of what a deep walk costs.

Two things follow from the walk and are accepted:

- **A working directory inside one of the three folders it does not enter is not found.**
- **A folder it cannot list is passed over, where reaching the bound refuses.** The two are not the same doubt. Past the bound there are folders the search tool will read and the walk did not. A folder the walk cannot list is one the search tool, which runs as the same user, cannot list either, so nothing in it reaches the session. Refusing there would also refuse any Grep of the temp folder while another program holds one subfolder locked.

The checkout's own path may hold a space, and the hook still finds its allow list.

### 4. What is read in a shell command

A command is text, and the hook reads paths out of it. It cuts the text into tokens at white space, quotes, and `;`, `|`, `&`, `<`, `>`, `(` and `)`, and reads each token.

**A path is not always a whole token, and two more readings are made for that.**

- **A space escaped with a backslash joins what is on either side of it.** `cat my\ runs/report.html` names `my runs/report.html`, and the two halves are also read as that one path.
- **A token is also cut at `,`, `{` and `}`, and each piece is read.** A brace list, `cat {note.txt,../wd/report.html}`, and a comma list, as PowerShell writes an array, carry a path that is not at the head of the token.

**A quoted string is also read whole**, as one token, whether its quotes are single or double. A path with a space in it is written in quotes, and cut at the space it is two tokens neither of which is the path: `cat 'my runs/report.html'` must be refused when `my runs` is a working directory. A quoted sentence, such as a commit message, read whole is a relative path to nothing, and is let through unless one of its pieces is refused on its own.

It reads:

- **a drive path**, `Q:/x` or `Q:\x`, with one separator after the colon or several (`Q://x`, `Q:\\x`). The `//` after a URL's scheme is not one: `https://example.com/x` is let through.
- **a bare drive**: a token that is one letter and a colon, as in `cd Q:`. It is the root of that drive, which no allowed root is, so it is refused whatever the letter. A dot or two after the colon, `Q:.` and `Q:..`, is that drive's current directory or its parent, and is a drive too. The drive is told from the token as it is written, before any punctuation is trimmed from its end: trimmed first, `Q:.` loses its dot and then its colon and is left a plain name.
- **a home path**: `~`, `$HOME`, `${HOME}`.
- **a variable of the environment at the head of a token**: `$USERPROFILE/x`, `${USERPROFILE}/x`, `$env:USERPROFILE\x`, `${env:USERPROFILE}\x`, and the variable alone, as in `echo $USERPROFILE`. It is replaced by its value in the hook's own environment and the result is checked like any other path, so `$USERPROFILE/.m2` is let through and `$USERPROFILE/Documents` is refused. A variable with no value there is not read: it is a path built at run time (§5). Neither is one whose value is a list of paths, such as `PATH`.
- **a relative path.** Every token that is none of the other forms is read as one, a command's own name included. It is read against the current directory and against every directory the command names, as the rule below says.
- **a path that ends in dots**: `<repository>/..` is the repository's parent. The punctuation that ends a sentence is trimmed from a path (`see <path>, and <path>.`), and `..` is not punctuation.
- **the value after `=`**, read as a token of its own: `--file=../x` and `OUT=../x`. A token that starts with `-` is an option and is not itself read as a path. That second half has no case: all it lets through is the attached value §5 lists as not covered, and a case would hold the gap in place.
- **a path headed by `@`**, as `curl -d @body.json` writes it. The token is also read without the `@`.

On Windows only, it also reads:

- **a path on a drive with no separator after the colon**, `Q:folder\x`. To the shell it is a path from that drive's own current directory, which is the drive's root unless something changed it. A token headed by exactly one letter, a colon, and then anything but a separator is a path on that drive. Two readings of it are judged, and either refuses: the drive's root joined with the rest, and the rest as a relative path, read as every relative path is. The first is where it lands on a drive whose current directory is its root. The second is where it lands on the drive the command runs on. Judging only the first could let through a path that is allowed from the root and lands in a working directory under the current directory. A word and a colon, `HEAD:README.md` or `localhost:5001`, is not one.
- **a Git Bash drive path**, `/q/x`, and its `/proc/cygdrive/q/x` and `/cygdrive/q/x` forms. It is one only when written with forward slashes.
- **a UNC path**, `//host/share/x` or `\\host\share\x`: two slashes or two backslashes at the head of the token, and not one of each.

`/dev/null` is let through, and so is a token that is a URL.

**A relative path is read against every directory the command names, and is refused if any of those readings is refused.** A command can change directory before it reads a path: `cd <folder> && cat wd/report.html`, `git -C <folder> show wd/report.html`, `pushd`, `Set-Location`, `env -C`, `make -C`, a subshell. Read against the current directory alone, the relative path lands somewhere harmless, and the command reads a working directory.

- The directories a command names are its current directory and every token of it, in any form §4 reads, that is a folder that exists. An absolute one counts as a relative one does: `cd D:/checkout/docs && cat adr/x.md` names `D:/checkout/docs`, and `cd docs && cat adr/x.md` names `docs`. No list of verbs is kept: a list is dodged by the verb it leaves out.
- A relative token that is a folder under one of them is a directory the command names too, so `cd a && cd b` is followed. The directories a command names are therefore reached by chains: from the current directory or an absolute folder, one token after another.
- **Along one chain a token is used no more often than the command writes it.** `..` written once climbs once from each directory the other tokens reach, and the folder it yields is not climbed again by that same `..`. Written twice, as in `cd .. && cd ..`, it climbs twice. The same holds for every token, and `..` is where it matters.
- A relative token is read against every directory that a chain reaches without having used that token up.
- The call is refused if any reading of any token is outside the allow list or inside a working directory.

**Why a token is counted.** The first version of this rule read every token against every directory, the ones that token had itself yielded among them. `..` was read against the current directory, its parent was a folder and so was named, `..` was read against the parent, and so on to the root of the drive, which is outside the allow list. `ls ..` was refused from every directory at every depth, and so were `git -C .. status` and `cd ../.. && ./mvnw -q test`. A shell uses each token it is given once, so a chain that uses `..` more often than it is written is one no command runs. Counting what is written, and not each token once, is what keeps `cd .. && cd .. && cat wd/report.html` refused when `wd` is two folders up: counted once, the second climb would be left out and the path let through.

**The rule is bounded, and refuses at its bounds.** Relative paths are read against no more than 32 folders, the current directory among them, so a command that names 32 folders besides its current directory is refused. One reading is one relative token against one folder, and a command that needs more than 20,000 readings is refused. Each refusal says to split the command. Past either bound there are readings the hook did not make, so it cannot let the call through. Both bounds have a case.

One reading is counted and not judged: a token that is a plain name, with no separator in it, and that does not exist in a folder. That reading is a path directly in the folder, the folder was itself checked, and nothing is there to follow, so the answer is the folder's own.

**How the count is kept, and where it counts too much.** For each folder the hook keeps the least use of each token over the chains it has found to that folder, and it makes each reading of a token against a folder once. Three things in it count more than a shell would. Each reads more and can only refuse more:

- A token inside quotes is counted for the piece and again for the quoted string read whole, so `cd ".."` may climb twice, and is refused from one folder inside the repository.
- A drive letter, a colon and two dots, `Q:..`, also counts as a `..`.
- The least use is kept token by token, so a folder two chains reach may be given a use that neither chain has. Every token it is then read with is one that some chain to it had left.

The rule knows only the directories written in the command. A directory the command reaches without naming it is §5's.

The cost is refusals of harmless commands. Three kinds are known:

- A relative path that climbs with `..` is refused when it leaves the allow list from the current directory, as `cd src/main && cat ../../README.md` does from the repository. That was so before this rule.
- A command that names an allowed folder and, separately, a relative name that happens to be a working directory under that folder.
- A command with two climbing tokens that leave the allow list when one is read after the other, though the command reads each from the current directory: `cp ../README.md ..` from one folder inside the repository. The hook cannot tell it from `cd .. && cat ../README.md`.

**A token led by a backslash is not a path from the root.** `'\n'` in `tr '\n' ' '` and `'\s'` in a `grep` pattern are escapes, and a regular expression literal such as `/\s+/g` is neither a Git Bash drive nor a UNC path. A backslash still separates folders inside a drive path, a UNC path and a relative path. A regular expression literal with no backslash, `/a/g`, cannot be told from the Git Bash path to folder `g` on drive `A:`, and is refused on Windows.

### 5. What the hook does not cover

These are stated so nobody takes the hook for the whole of the protection. For each, §1's written rule is all there is. **It is the list of what is known, and it is not complete**: three gates each found forms that were not on it, and a hook that reads a command as text cannot list every way a shell can spell a path.

- **A brace list with text before or after it**, `../{a,b}/x`. The pieces are read, and so is the token as it is written, but the list is not expanded as the shell expands it.
- **A variable written for `cmd`**, `%USERPROFILE%\Documents\x`. It is read as a relative path of that spelling.

- **`mcp__*` tools.** A file tool an MCP server offers is not one of the eight, and the hook never sees its call.
- **Monitor**, which runs a shell command and is not one of the eight.
- **A path a script builds at run time.** `cat "$(some-command)"`, a variable set earlier in the same command, a loop over a listing, a program that opens a path it computed. The hook reads text, and the path is not in it.
- **A variable that is not at the head of a token**, as in `x/$NAME/y` or `${NAME}suffix`. It is read as the text it is written as.
- **An option with its value attached and no `=`**, as in `-I../x`. The token starts with `-` and is not read. A drive path written straight after a letter, as in `-IQ:/x`, is not read either.
- **Another user's home**, `~name/x`. Only `~` alone or before a separator is the home folder; `~name/x` is read as a relative path of that spelling.
- **A path from the root of the current drive, written without its letter**, as PowerShell's `Get-Content \archive\x` or `/archive/x`. On Windows it is a real path on whatever drive the command runs on. It was left uncovered, with no rule and no case, on the operator's word of 2026-10-05: it has the same shape as `'\s'` in a pattern, which §4 lets through, and the hook cannot tell the two apart.
- **A directory change that a loop repeats.** `for i in 1 2 3; do cd ..; done` writes `..` once and uses it three times. The hook counts what is written.
- **A directory the command changes to without naming it.** `cd` with no argument goes to the home folder, and `cd -` to the one before. The relative paths after it are read against the directories §4 knows, and this is not one.
- **A directory named in a form §4 does not read**, such as `cd /tmp`. Relative paths are not read against it.
- **A quoted string inside a quoted string**, as in `bash -c "cat 'my runs/x'"`. The outer string is read whole and the inner one is not, so the inner path is cut at its space.
- **A shell wildcard.** `cat */vespera.log` names no folder the hook can check.
- **A working directory the walk down does not reach** (§3): one inside a folder named `.git`, `node_modules` or `target`, behind a link, or in a folder that cannot be listed.
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

**A hook that runs out of time is not known to fail closed.** `settings.json` sets no `timeout` for the hook, so Claude Code's default applies, and what Claude Code does with a call whose hook timed out has not been verified. The two slow things the hook does are the walk down and the reading of relative paths against named folders, and each has its bound. All figures are from the operator's machine.

- **The architect measured, at the third gate, on the code of that day:** a command naming 31 folders with 588 tokens that each hold a separator, 19,840 readings and so just under the bound, at 3.6 s; the same count of plain names at 0.39 s; the walk down at its bound of 10,000 folders at 0.4 s.
- **The implementer measured, after the change that followed:** that same worst command, let through, at about 0.36 s, and an ordinary `ls` at 85 ms, of which about 82 ms is the hook starting.

What changed between the two is that a path that does not exist is answered from its parent, once, and the answer kept. Such a path is not a link and holds nothing, so where its links lead and which working directory it is inside are its parent's answers with its name put back; that is what the check gave before, one path at a time. A path that exists is checked as before.

The bound on readings stays at 20,000. At a third of a second it costs nothing to keep, and a lower one would refuse a long commit message beside a few folders, which is an everyday command.

### 7. The test

`src/test/hooks/private-paths-guard.test.mjs` is a table of cases, each a tool call given to the real entry point, `run-private-paths-guard.sh`, claiming exit 0 or exit 2. It is started with `node --test src/test/hooks/private-paths-guard.test.mjs` and needs Node 22, bash and git, and nothing installed.

- **It is a Node test and not a JUnit one.** The guard is a Node script behind a bash wrapper, and `node:test` needs no dependency and no `package.json`. A JUnit class that starts bash and node needs external tools, which makes it an `*IT` under Failsafe, and the NTFS job, where most of these path forms live, runs no integration test.
- **It names no path of the operator's.** It copies the hook's files into a checkout it builds under the temp folder and starts that copy, with a temp folder and a home folder of its own, so a machine's local allow list cannot change a result. A working directory is a fixture folder holding an empty `vespera.db`.
- **Windows forms are not started elsewhere.** A case about a drive letter, a Git Bash path, a UNC path or PowerShell is marked as not started on another platform, with the reason in its name. The rest run on both.

**CI runs it in both jobs of `build.yml`**, on NTFS because the forms are Windows forms, and on Linux for the cases that are portable. Each job sets up Node 22 and starts the test before its Maven step.

## Consequences

- **A search of a folder that holds a working directory is refused.** With the default `vespera.working-dir`, a run started from this checkout writes `.vespera` in it, and from then on a Grep or Glob of the repository root is refused on that machine. The search has to name `src`, `docs` or another folder. This is the largest cost in this record, and it is paid on purpose: a Glob of the root lists the deliverable's pages by name.
- **A search of a folder with more than 10,000 folders beneath it is refused**, whether or not it holds a working directory. The search has to name a narrower folder.
- **Some harmless commands are refused.** Each costs a refusal and a rewording:
  - a commit message that quotes `../x`, or a `sed` expression that reads as a relative path out of the repository;
  - an unquoted path with a space in it, cut short at the space. Quoted, it is read whole (§4);
  - a token that is one letter and a colon, as in a commit message that lists `A:` and `B:`;
  - on Windows, a token headed by one letter and a colon, whatever follows: a `sed` expression with colons for delimiters, `s:a:b:`, a refspec between one-letter names, `a:b`, or a one-letter host before a path. A docker volume between one-letter names is refused when it is a token of its own, `-v a:b`, or the value after `=`, `--volume=a:b`. Attached to its option, `-va:b`, it is not read at all, which is §5's attached value and not a rule;
  - `cd ".."` from one folder inside the repository, because the quoted `..` is counted twice (§4);
  - `echo $SHELL`, and any other variable alone whose value is a path outside the allow list;
  - on Windows, a one-letter switch written with a slash, as in `cmd /c`, which reads as the drive `C:`;
  - on Windows, a regular expression literal with one letter between its slashes, `/a/g`, which reads as a folder on drive `A:`;
  - a command that names an allowed folder and a relative name that is a working directory under it (§4);
  - a commit message, or any other quoted text, that quotes a relative path reaching outside the allow list or into a working directory. Every word of it is a token, and the whole of it is one more;
  - a command that names 32 folders besides its current directory, or whose words and folders need more than 20,000 readings, as a very long quoted text beside many folders does. It has to be split.
- **A shell command whose current directory is a working directory is refused**, `ls` included. A session cannot work from inside one.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **A list of the archives to refuse.** Rejected: an archive on a path nobody listed is open, and the failure is silent.
- **The written rule alone.** Rejected, in Context: it had not been enough.
- **Refusing every shell command that carries a variable or a relative path.** Rejected: most commands a session runs carry one, and a hook that refuses the build is a hook that gets removed.
- **Reading a working directory's contents but not its documents**, for instance the log. Rejected: the log names documents, and #424 is where a summary that names none is decided.

## What this does not decide

- **`/tmp/x`, `/etc/x`, `/usr/bin/x` and other rooted paths in a shell command.** Under Git Bash `/tmp` is the temp folder, which is allowed, and `/etc` and `/usr` are Git's own install, which is not. Refusing them all breaks `> /tmp/x.log`. Mapping them needs the mount table. Today they are not read, and no case pins them either way.
- **What a shell command's absolute path is on a machine that is not Windows.** The operator's machine is Windows, and so are the forms in §4.
- **A wrapper that is present and empty.** It ends with exit 0 having checked nothing. No case pins it.
- **A file system that tells names apart by case.** The hook lowers every path before it compares, so on such a file system a path that differs from an allowed root by case alone is let through.
- **Whether the `mcp__*` file tools and Monitor join the matcher.** They are uncovered today (§5).
