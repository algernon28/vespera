# ADR-199 — The private-paths guard reads a climb out of a link both ways, and refuses a name it cannot follow

- **Date**: 2026-10-05
- **Status**: accepted
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), in these places and no others: §3's sentence "A link is followed before the check"; §4's cut at `,`, `{` and `}`, its clause on a path headed by `@`, and its paragraph "How the count is kept, and where it counts too much"; §5's list of what the hook does not cover; §6's two measurements and its paragraph on the bound on readings; and Consequences. ADR-196's rule (§1), its allow list (§2), its bounds and how it fails closed stand as written.
- **Settles**: [#432](https://github.com/algernon28/vespera/issues/432), and the five follow-ups the fourth gate of [#426](https://github.com/algernon28/vespera/pull/426) left.

## Context

ADR-196 was merged after four gates. The fourth passed it with five follow-ups, none blocking. Separately, #432 reported that the guard "reads a link's name, not its target, so a junction into a working directory gets through".

**#432's headline is not true of the merged guard.** It resolves every path it has allowed by its text with `realpathSync.native`, and judges the allow list and the walk up for `vespera.db` on the resolved path. Executed against the merged code: a link that leads outside the allow list is refused, a link into a folder holding `vespera.db` is refused, a junction on Windows and a symlink elsewhere, and a link to a link is refused too. ADR-196's cases R503 to R505 already held the first two.

Two things about links did get through, both executed:

- **A `..` after a link.** The guard folded `..` as text before it asked the file system anything. With `links/in` leading to `deep/a`, the path `links/in/../a/b/notes.txt` is `links/a/b/notes.txt` as text, which does not exist and was let through. A program that hands the path to the file system reaches `deep/a/b/notes.txt`, and `deep/a/b` was a working directory. On Windows the programs that do so are the ones Git Bash starts: `cat` read the fixture file by that path, after `cd` through the link, with the link as the current directory, and after `cd -P ..`. Node, PowerShell's `Get-Content` and `cmd`'s `type` fold the path as text and found nothing, and so does bash's own `cd ..`. So on Windows the exposure was the Bash tool, and any MSYS program started from PowerShell. ADR-196 §3's sentence was false of this form.
- **A name that is there and cannot be followed.** The guard treated every path it could not `stat` as a path that is not there, and answered it from its parent. A link to a name that does not exist and a link to itself were both let through.

The fourth gate's other follow-ups, each executed:

- §4 says three things count a token more often than a shell would. There is a fourth: a token followed by `,` or `}` is counted once trimmed and once as a piece, so `Get-ChildItem .., main` was refused from one folder inside the repository while `..,main` was let through.
- On Windows the cut at `,`, `{` and `}` refused everyday spellings that name no path: `--jq '{t:.title}'` as `t:.title`, `node -e "…({a:1,b:2})"` as `a:1` and `b:2`, `cat "${TMPDIR}/x"` as `/x` read as a Git Bash drive, and `echo ${f:-none}` as `f:-none`. This repository's own notes recommend `gh --jq` and `node -e`.
- §6 gave about 0.36 s as the worst case under the bound on readings. That is the figure for tokens that do not exist. The gate measured 2.5 s for tokens that exist in every folder.
- A token headed by `@` had its punctuation trimmed before the `@` was taken off, so `@` before a drive letter, a colon and a dot lost the dot and the colon and was let through. §4 already says a drive is told from the token as it is written.

## Decision

### 1. A path that holds `..` is read twice, and either reading refuses

**As text**, as before: `..` is folded against the name before it. `cmd`, PowerShell, Node and bash's own `cd` read it so.

**As the file system walks it**: start from the real path of the folder the path is read against, and at each `..` take the parent of the real path reached so far. A name that is not there is put back as text. Every program Git Bash starts reads it so, and every program on a system that is not Windows.

This holds for every path of a call (ADR-196 §3): a file tool's field, a search tool's `path` and the folder of its pattern, the current directory, and every relative path of a shell command against every folder the command names. In a shell command each of the two readings that is a folder is a folder the command names, so `cd in && cd -P .. && cat a/b/notes.txt` is followed to where `-P` goes. The second reading is a real path, so it is judged against the allowed roots and against where those roots lead.

**It is decided for every tool, and not for Bash alone.** The text of a call does not say which program will read the path: a PowerShell command can start an MSYS program, and what Read does with `..` on a system that is not Windows is not known.

- **What it lets through**: nothing that was refused before.
- **What it costs**: one more `realpath` for each folder a `..` is taken from, asked once per call and kept. Where no link is on the way the second reading is the first, and is answered from what is kept. One reading against §4's bound is still one token against one folder. A path that climbs out of a link is also refused where the program reading it would fold it as text and find nothing.

### 2. A name that is there and cannot be followed to where it leads is refused

A link to a name that is not there, a link in a loop, and any answer from the file system other than "not there" refuse the call. **Only a name that is not there is answered from its parent**, so a path that is not written yet stays usable, as a Write to a new path must. "Not there" is: no such name, a name beneath something that is not a folder, a name too long to exist, and a name the platform cannot hold. The refusal holds for everything beneath such a name.

The guard asks about the name itself before it follows it, so a name that is not there still costs one question.

- **What it lets through**: nothing.
- **What it costs**: a refusal of a command that names a broken link, or a path the guard is denied or finds locked. Nothing can read through such a name, so no document was exposed by the old behaviour. It is refused because ADR-196 §6 fails closed wherever the guard cannot reach a decision, and because on a system that is not Windows a Write through a link to nothing creates the name it leads to.

ADR-196 §3's walk down from a search root still passes over a folder it cannot list. That is a different doubt, and its reason stands.

### 3. A token that is one path followed only by `,` or `}` is counted once

Both readings of it are still made. The piece the cut leaves is the token with that punctuation off, which the token's own reading already is, so its relative reading is not counted a second time. A `..` written once then climbs once.

ADR-196 §4 says "Three things in it count more than a shell would". **Read it as: these three are the ones known.** No number is claimed.

- **What it lets through**: nothing. No reading is dropped, only a count. `..` written twice still climbs twice, and a comma before a `..` hides nothing.

### 4. In a quoted string, one letter and a colon after `{` or `,` is a key and not a drive

Inside a string in single or double quotes that holds no `$(` and no backtick, a letter and a colon that come straight after `{` or `,`, or after white space that follows one, with no separator after the colon, are a key of an object: `'{t:.title}'`, `"({a:1,b:2})"`, `'{n:.number, t:.title}'`. What follows the colon is still read, as a token of its own. A quoted string is what lies between two quotes of one kind, as ADR-196 §4 already reads it.

No shell makes a list inside quotes, which is why the rule stops at the quotes. A command substitution inside double quotes is a command again, so a string that holds one keeps every reading.

- **What it lets through**: a path on a drive with no separator after the colon, or a bare drive, written as a member of a list inside quotes for a second shell to split: `bash -c "cat {x,Q:name}"`, `powershell -Command "Get-Content x, Q:name"`. Only the reading from the drive's root is lost. What follows the colon is still read as a relative path. It joins the list of what the hook does not cover.
- **What stays refused**: a comma list or a brace list outside quotes with such a member; the same list inside `"$(…)"`; a token inside quotes that is headed by a drive and follows no `{` or `,`, as in `bash -c 'cat Q:name'`; a member written the Git Bash way; the value after `=`; and a key whose value is a relative path that is refused.

### 5. The braces of a variable are not a cut

- **A token, or a piece of a comma list, headed by `${NAME}` or `${env:NAME}` that is replaced by its value is read whole.** What follows the closing brace is not read apart from it: `"${TMPDIR}/x"` is `x` under the temp folder and not the drive `X:`.
- **One that is not replaced keeps the readings it had**: a variable with no value, one whose value is a list, one that is not at the head, one with no separator after it. What stands before it and after it is read as a piece.
- **In a Bash command only, `${NAME-word}`, `${NAME=word}`, `${NAME+word}` and `${NAME?word}`, each with or without a colon before the sign, is a variable with a word for when it has no value.** The name is not read. The word is read as a token of its own. In a PowerShell command `${Q:-x}` is what a name on drive `Q:` holds, and is read as before.

- **What it lets through**: `${NAME}/q/x` where `NAME` has a value in the hook's environment and none in the command's shell, which is then a Git Bash drive path. That is ADR-196 §5's variable set in the same command.
- **What it closes**: `cat ${OUT:-../wd/report.html}` was let through on every platform unless the name was one letter.

### 6. The `@` is taken off a token as it is written

ADR-196 §4 reads a token headed by `@` also without the `@`. The `@` comes off before any punctuation is trimmed from the token's end, so what is left is told a drive, or not, as it is written. It lets nothing through.

### 7. The bound on readings stays at 20,000, and its worst case is about three seconds

ADR-196 §6 concludes that at a third of a second the bound "costs nothing to keep". The figure is true of tokens that do not exist. Measured on the operator's machine at `58e4466`, the guard started with `node` and no wrapper, five times each:

- a command naming 31 folders with 588 tokens of the form `./fN` that exist in every folder, 19,840 readings, let through: 2.9 to 3.3 s. The fourth gate measured 2.5 s for the same command. A path that exists gets no help from what is kept;
- the same count of plain words: 0.26 to 0.30 s;
- an ordinary `ls`: 36 ms.

The bound stays. Its worst case needs a command that names 31 folders and several hundred paths present in every one of them. A lower bound would refuse a long quoted text beside a few folders, which is an everyday command.

§1 adds about 32 `realpath` calls to that worst case where no link is on the way. Where every folder is reached through a link it makes one more check per reading, so at most about twice the time. **That is derived and not measured**: the change that builds §1 measures it and puts the figure here.

### 8. What the hook does not cover, added to ADR-196 §5

- **A member of a list inside quotes that is a path on a drive with no separator after the colon** (§4 above).
- **A link that only Git Bash's runtime follows.** A symlink written as a file for Cygwin is a plain file to Node, so `realpath` does not see where it leads. Whether Git Bash follows one was not probed.

### 9. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 60 cases and holds 258.

- **L101 to L107**: a link is followed by every tool and through a link to a link, and a link that leads inside the allow list is let through. #432 asked for a junction and a symlink into a folder holding `vespera.db`: that is R505.
- **L201 to L216**: §1, through Read, Grep's `path` and `glob`, Glob's `pattern`, a Bash relative path, a link as the current directory, `cd` through a link, `cd -P ..`, a link to a link and PowerShell's spelling; and three climbs out of a link that stay inside the allow list and out of every working directory, let through.
- **L301 to L306**: §2, with a path two folders that do not exist yet down an allowed one, and a quoted sentence longer than any name a file system holds, both let through.
- **G101 to G107**: §3. **G201 to G220**: §4 and §5, each allowed spelling beside the refusals the rule must leave standing. **G401 to G404**: §6.

On Windows at `58e4466`, 229 hold and 29 do not, and those 29 are what the guard is to be brought to. Linux was not run.

## Decided here, open to the operator's overruling

1. §1 judges both readings for every tool, not for Bash alone.
2. §2 refuses a name that cannot be followed, where the old behaviour could have been kept with its reason recorded.
3. §4 opens a small, named way through so that `gh --jq` and `node -e` with an object are not refused. Without the rule, G201 to G203 are refused and a line in Consequences says so.
4. §5's word for a variable with no value is read in a Bash command only.
5. The bound on readings stays at 20,000 with a worst case of about three seconds.
6. Nothing is ruled for the space escaped with a backslash. The gate named it beside the cut, every refusal it executed came from the cut, and no false refusal from the join was reproduced.

## Consequences

- **A path that climbs out of a link is refused when either reading of it is refused**, in a PowerShell command and a file tool's field too, where the program may fold it as text.
- **A command that names a broken link, or a path the guard is denied, is refused.**
- **Still refused on Windows, and to be reworded**: an object outside quotes, `echo {a:1}`; a key with a space after its colon, `{ t: .title }`, where `t:` is a token of its own and a bare drive; a one-letter name before a colon inside `${…}` in a form §5 does not name, such as `${f:0:3}`.
- **`Get-ChildItem .., main`, `--jq '{t:.title}'`, `node -e` with an object, `"${TMPDIR}/x"` and `${f:-none}` are let through.**
- **ADR-196 Consequences' line on `cd ".."` stands**: a quoted token is still counted for the piece and for the string.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **The file system's reading alone.** Rejected: PowerShell, `cmd`, Node and bash's `cd` fold the path as text, so a path refused only as walked would be let through where it is read as text.
- **The second reading for the Bash tool alone.** Rejected in §1: the text does not say which program reads the path.
- **Keeping an unresolvable name as "not there", with the reason recorded.** Weighed, since nothing reads through one. Rejected for §6 of ADR-196: the guard had not reached a decision about where the name leads.
- **A piece of a cut never read as a drive.** Rejected: `ls x,Q:..` and an unquoted brace list are lists a shell does make, and must stay refused.
- **A Consequences line for the object keys and no rule.** Weighed against §4's way through. Rejected because a hook that refuses the commands this repository's notes recommend is a hook that gets removed, which is ADR-196's own argument.
- **A lower bound on readings.** Rejected in §7.

## What this does not decide

- **`InvocationAccount.refusalToWrite`**, which #432 also names. It is Java under ADR-198, compares paths as text, and is raised as its own ticket.
- **What Claude Code's Read does with `..` on a system that is not Windows.** §1 does not depend on it.
- **Whether Git Bash follows a symlink written as a file** (§8).
