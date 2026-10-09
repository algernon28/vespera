# ADR-201 — The private-paths guard reads a climb out of a link both ways, and refuses a name it cannot follow

> **Partly amended — see [ADR-216](0216-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md).** §1's "A path that holds `..` is read twice, and either reading refuses" is no longer the whole of the readings: on Windows each of the two is read once more, as Windows opens its names, and any of them refuses. Everything else in this record stands.

- **Date**: 2026-10-05
- **Status**: accepted
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), in these places and no others: §3's sentence "A link is followed before the check"; §4's cut at `,`, `{` and `}`, its clause on a path headed by `@`, and its paragraph "How the count is kept, and where it counts too much"; §5's list of what the hook does not cover; §6's sentence "A hook that runs out of time is not known to fail closed", its two measurements and its paragraph on the bound on readings; and Consequences. ADR-196's rule (§1), its allow list (§2), its bounds and every way §6 makes the hook fail closed stand as written.
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

This holds for every path of a call (ADR-196 §3): a file tool's field, a search tool's `path` and the folder of its pattern, the current directory, and every relative path of a shell command against every folder the command names. In a shell command each of the two readings that is a folder is a folder the command names, so `cd in && cd -P .. && cat a/b/notes.txt` is followed to where `-P` goes. The second reading is a real path, so it is judged against the allowed roots and against where those roots lead. So is every relative path read against a folder that was reached as walked: where an allowed root is itself a link, `cd $TEMP/scratch/.. && cat deep/note.txt` lands under the folder the root leads to, and is let through.

**It is decided for every tool, and not for Bash alone.** The text of a call does not say which program will read the path: a PowerShell command can start an MSYS program, and what Read does with `..` on a system that is not Windows is not known.

- **What it lets through**: nothing that was refused before.
- **What it costs**: one more `realpath` for each folder a `..` is taken from, asked once per call and kept. Where no link is on the way the second reading is the first, and is answered from what is kept. One reading against §4's bound is still one token against one folder. A path that climbs out of a link is also refused where the program reading it would fold it as text and find nothing. §7 gives the time.

### 2. A name that is there and cannot be followed to where it leads is refused

A link to a name that is not there, a link in a loop, and any answer from the file system other than "not there" refuse the call. **Only a name that is not there is answered from its parent**, so a path that is not written yet stays usable, as a Write to a new path must. "Not there" is: no such name, a name beneath something that is not a folder, a name too long to exist, and a name the platform cannot hold. The refusal holds for everything beneath such a name.

The guard asks about the name itself before it follows it, so a name that is not there still costs one question.

- **What it lets through**: nothing.
- **What it costs**: a refusal of a command that names a broken link, or a path the guard is denied or finds locked. Nothing can read through such a name, so no document was exposed by the old behaviour. It is refused because ADR-196 §6 fails closed wherever the guard cannot reach a decision, and because on a system that is not Windows a Write through a link to nothing creates the name it leads to.

**A broken link followed at once by `..`, as in `links/nowhere/..`, is not refused.** Neither reading asks about the link. The text reading folds `nowhere` away against the `..`. The walked reading reaches `nowhere`, cannot follow it, puts the name back as text as §1 does for a name it cannot resolve, and the `..` takes it off again. Both readings land on the folder the link is in, which is checked like any other path. Nothing is read through the link: a program that walks the path cannot pass the broken link either. This is the rule as it stands, and L307 holds it.

ADR-196 §3's walk down from a search root still passes over a folder it cannot list. That is a different doubt, and its reason stands.

### 3. A token that is one path followed only by `,` or `}` is counted once

Both readings of it are still made. The piece the cut leaves is the token with that punctuation off, which the token's own reading already is, so its relative reading is not counted a second time. A `..` written once then climbs once.

ADR-196 §4 says "Three things in it count more than a shell would". **Read it as: these three are the ones known.** No number is claimed.

- **What it lets through**: nothing. No reading is dropped, only a count. `..` written twice still climbs twice, and a comma before a `..` hides nothing.

### 4. In a quoted string, one letter and a colon is a key only in a brace group whose every member is keyed

A one-letter name and a colon are the key of an object, and not a drive, only where all of this holds:

- they stand in a string in single or double quotes that holds no `$(` and no backtick;
- they stand in a brace group of that string: the text between a `{` and the next `}`, with no other brace between them;
- **every member of that group is keyed.** The members are what the group's commas part, and a keyed member is, after any white space, a name, a colon, and then something that is not a separator and not the end of the member.

`'{t:.title}'`, `"({a:1,b:2})"`, `'{n:.number, t:.title}'` and `'{number:.number, t:.title}'` are such groups. What follows the colon is still read, as a token of its own. A comma list with no braces is never one, and neither is a group with one member that is a plain name.

**Why every member, and why braces.** The first form of this rule took any one-letter name after `{` or `,` in a quoted string for a key, on the ground that no shell makes a list inside quotes. The fifth gate showed that ground false of the guard: a quoted string is what lies between two quote characters as the guard pairs them (ADR-196 §4), and text the shell does not quote can lie there. An apostrophe in a comment line before a command and another after it, two escaped double quotes, and a here-document that holds one apostrophe each put an ordinary brace list or comma list "in quotes", and its drive member was let through. This was the first rule under which being in quotes removes a reading, so it may not rest on the pairing alone. An object is told by its own shape: every member has a key. A list of paths with one plain name in it is not one.

- **What it lets through**: a brace group, between two quote characters as the guard pairs them, every member of which is a name, a colon and a value, one of them a path on a drive with no separator after the colon: `bash -c "cat {a:1,Q:name}"`, or the same group after a comment line that holds an apostrophe. Only the reading from the drive's root is lost. What follows the colon is still read as a relative path. It stays on the list of what the hook does not cover, narrower than before.
- **What it no longer lets through**: `bash -c "cat {x,Q:name}"` and `powershell -Command "Get-Content x, Q:name"`, which the first form named as its way through, and the four shapes of the fifth gate.
- **What stays refused**: a comma list or a brace list with a drive member and a member that is not keyed, in quotes or out of them; any such list inside `"$(…)"`; a token inside quotes that is headed by a drive and stands in no such group, as in `bash -c 'cat Q:name'`; a member written the Git Bash way; the value after `=`; a key whose value is a relative path that is refused; and a bare drive, one letter and a colon with nothing after it, wherever it stands, so `bash -c "ls {some,Q:}"` and `{ t: .title }` are refused.

### 5. The braces of a variable are not a cut

- **A token, or a piece of a comma list, headed by `${NAME}` or `${env:NAME}` that is replaced by its value is read whole.** What follows the closing brace is not read apart from it: `"${TMPDIR}/x"` is `x` under the temp folder and not the drive `X:`.
- **The variable alone is a token headed by it.** `${TMPDIR}` with nothing after it is replaced by its value, as `$TMPDIR` is. The brace that closes `${` is part of the variable and is not punctuation to trim from a token's end. ADR-196 §4 lists `${USERPROFILE}/x` and "the variable alone" among what is read, and its guard, and this record's first one, trimmed the brace and then read nothing: `cd "${TMPDIR}" && cat working-directory/report.html` and `ls ${USERPROFILE}` were let through.
- **One that is not replaced keeps the readings it had**: a variable with no value, one whose value is a list, one that is not at the head, one with no separator after it. What stands before it and after it is read as a piece.
- **In a Bash command only, `${NAME-word}`, `${NAME=word}`, `${NAME+word}` and `${NAME?word}`, each with or without a colon before the sign, is a variable with a word for when it has no value.** The token is read twice, wherever in it the variable stands: with the word in the variable's place, and, when the name has a value, with that value in its place. Either refuses. What follows the closing brace is read with each and not apart from them. So `${TMPDIR:-some}/working-directory/report.html` is the report under the temp folder, `${f:-none}` is `none`, and `${TMPDIR:-x}/y` is `y` under the temp folder and `x/y`, and not the drive `Y:`.
- **The word runs to the brace that closes the variable**, counting the braces between. A word that holds a variable is read by these same rules, so `${ONE:-${TWO:-../wd/report.html}}` and `${LOG:-${TMPDIR}/wd/report.html}` are read to the path.
- **Two limits on those readings, as the guard has them.** Where the variable is not at the head of a token or a piece, its value is joined to what stands before it; on Windows that is read where it makes a drive path, and elsewhere a rooted value in that place is not read, which is ADR-196 §5's rooted path. And a command is cut into tokens at white space before a variable is looked for, so a word that holds a space, as in `${Y:-a b}`, is not read as one word: it is read in its two halves, and on Windows the first is refused as the drive `Y:`. A quoted string is still read whole.
- **A third limit: the word after an unbraced variable is not read.** In `$ONE${TWO:-../wd/report.html}` the word is joined to `$ONE` before it, and a token headed by a variable with no value is not read, so the path in the word is let through; where `ONE` is empty, bash reads it. The sixth gate found it by running it. It goes on §8's list.
- In a PowerShell command `${Q:-x}` is what a name on drive `Q:` holds, and is read as before.

- **What it lets through**: `${NAME}/q/x` where `NAME` has a value in the hook's environment and none in the command's shell, which is then a Git Bash drive path. That is ADR-196 §5's variable set in the same command.
- **What it closes**: `cat ${OUT:-../wd/report.html}` was let through on every platform unless the name was one letter. The variable alone in braces, the path after `${NAME:-word}`, and a word that is itself a variable were let through by ADR-196's guard too.

### 6. The `@` is taken off a token as it is written

ADR-196 §4 reads a token headed by `@` also without the `@`. The `@` comes off before any punctuation is trimmed from the token's end, so what is left is told a drive, or not, as it is written. It lets nothing through.

### 7. The bound on readings stays at 20,000, and its worst case is about seven seconds

ADR-196 §6 concludes that at a third of a second the bound "costs nothing to keep". The figure is true of tokens that do not exist. A path that exists gets no help from what is kept, and §1 makes a path that climbs out of a link cost a second check.

**Spec-implementer measured, on the operator's machine, after the change that built §1 to §6**, with the guard started directly with `node` and no wrapper, five runs of each. The command names 31 folders and 588 tokens of the form `./g/../fN`, each `fN` a file that exists in every folder: 19,840 readings, just under the bound, and every run let through.

| Form | This record's guard | ADR-196's guard |
| --- | --- | --- |
| ADR-196's form, `./fN`, with no `..` | 3.15–3.20 s | 2.90–2.99 s |
| the `..` form, no link on the way | 3.36–3.45 s | 2.88–2.93 s |
| the `..` form, every folder reached through a link | 6.74–6.87 s | 3.49–4.04 s |
| plain words that do not exist | 0.44–0.46 s | 0.28–0.31 s |

An ordinary `ls` takes 30 to 40 ms started that way, and a Grep of the repository root 0.03 s. The fourth gate had measured 2.5 s for ADR-196's form on ADR-196's guard.

**The fifth gate measured the same guard a second time**, on the same machine: 2.56–2.57 s for ADR-196's form, 2.68–2.71 s for the `..` form with no link on the way, 5.21–5.24 s with all 31 folders reached through a link, and 0.36–0.38 s for plain words. An ordinary `ls` took 39–44 ms with `node` started directly and **73–80 ms through the registered wrapper**, which is what a call pays. The two measurements differ by about a fifth and agree on the shape: a link on the way doubles the cost.

**The worst case is five to seven seconds, and the bound stays at 20,000.** That case needs a command that names 31 folders, each reached through a link, and several hundred paths that climb and exist in every one of them. A lower bound would refuse a long quoted text beside a few folders, which is an everyday command. Halving it would halve the worst case and refuse a 600-word message beside 16 folders.

**What a hook that runs out of time does is now documented, and it does not fail closed.** ADR-196 §6 said it was not known. Claude Code's documentation, read on 2026-10-05 and not executed here, says a command hook's default timeout is 600 s, and that "a timed-out `command`, `http`, or `mcp_tool` hook doesn't block the tool call". `settings.json` sets no timeout for this hook, so the default applies. That is why the worst case is weighed at all. Seven seconds is under the default by a factor of more than eighty. A timeout set in `settings.json` would not help: a hook that times out lets the call through whatever the timeout is. The bound is what keeps the guard well inside it.

### 8. What the hook does not cover, added to ADR-196 §5

- **A brace group between two quote characters, every member of which is a name, a colon and a value, one of them a path on a drive with no separator after the colon** (§4 above): `{a:1,Q:name}`. Its drive is not read; what follows the colon is.
- **A link that only Git Bash's runtime follows.** A symlink written as a file for Cygwin is a plain file to Node, so `realpath` does not see where it leads. Whether Git Bash follows one was not probed.
- **The word of a variable that follows an unbraced variable**, as in `$ONE${TWO:-../wd/report.html}`: §5's third limit.
- **A guard that runs out of time.** Claude Code's documentation says the call goes through (§7). No command within the bounds comes near the default.

### 9. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 80 cases and holds 278: 60 written before the guard was changed, and 20 after the fifth gate.

- **L101 to L107**: a link is followed by every tool and through a link to a link, and a link that leads inside the allow list is let through. #432 asked for a junction and a symlink into a folder holding `vespera.db`: that is R505.
- **L201 to L216**: §1, through Read, Grep's `path` and `glob`, Glob's `pattern`, a Bash relative path, a link as the current directory, `cd` through a link, `cd -P ..`, a link to a link and PowerShell's spelling; and three climbs out of a link that stay inside the allow list and out of every working directory, let through.
- **L301 to L306**: §2, with a path two folders that do not exist yet down an allowed one, and a quoted sentence longer than any name a file system holds, both let through.
- **G101 to G107**: §3. **G201 to G220**: §4 and §5, each allowed spelling beside the refusals the rule must leave standing. **G401 to G404**: §6.
- **L217**: §1's path read against a folder reached as walked, with a guard whose `${TEMP}` is itself a link. **L307**: §2's broken link followed at once by `..`.
- **G501 to G505**: §5's variable alone in braces. **G601 to G607**: §4, the fifth gate's four shapes and the two spellings the first form let through, all refused, beside an object whose keys are a word and one letter, let through. **G701 to G706**: §5's variable with a word, with a path after it and with a variable for its word.

On Windows, ADR-196's guard held 229 of the first 258 and not the other 29, which were written first as what the guard was to be brought to. The guard the fifth gate read held all 258, and 5 of the 20 written after it; the guard this record ships with holds all 278. On Linux, CI started 209 of the 278 and did not start 69, which are Windows forms; 14 of the 20 written after the fifth gate ran there, and the other 6 are drive forms.

## Decided here, open to the operator's overruling

1. §1 judges both readings for every tool, not for Bash alone.
2. §2 refuses a name that cannot be followed, where the old behaviour could have been kept with its reason recorded.
3. §4 leaves one narrow, named way through so that `gh --jq` and `node -e` with an object are not refused. Without the rule, G201 to G203 and G607 are refused and a line in Consequences says so. After the fifth gate the rule was tightened to a brace group whose every member is keyed, where it could have been kept as it was with the four shapes recorded.
4. §5's word for a variable with no value is read in a Bash command only.
5. The bound on readings stays at 20,000 with a worst case of about seven seconds.
6. Nothing is ruled for the space escaped with a backslash. The gate named it beside the cut, every refusal it executed came from the cut, and no false refusal from the join was reproduced.

## Consequences

- **A path that climbs out of a link is refused when either reading of it is refused**, in a PowerShell command and a file tool's field too, where the program may fold it as text.
- **A command that names a broken link, or a path the guard is denied, is refused.**
- **Still refused on Windows, and to be reworded**:
  - an object outside quotes, `echo {a:1}`;
  - a key with a space after its colon, `{ t: .title }`, where `t:` is a token of its own and a bare drive;
  - a one-letter name before a colon inside `${…}` in a form §5 does not name, such as `${f:0:3}`;
  - a quoted object with a one-letter key and a member that has no key, as jq's short form writes it, `'{title, n:.number}'`: not every member is keyed (§4);
  - a quoted object with a one-letter key whose value holds a comma or a brace, `'{a:[1,2]}'` or `'{a:{b:1}}'`: the comma parts a member with no key, and a brace ends the group;
  - a closing tag of one letter in a here-document or a quoted text: a slash and `p`, `b`, `a` or `i` between angle brackets. The angle brackets end a token, and what is left is a Git Bash drive. A longer tag is let through. ADR-196's guard did the same, and this record changes nothing about it.
- **`Get-ChildItem .., main`, `--jq '{t:.title}'`, `node -e` with an object, `"${TMPDIR}/x"`, `${f:-none}` and `${TMPDIR:-x}/y` are let through.**
- **`echo ${HOME:-x}/y` is refused where the home folder is not allowed**, as `$HOME/y` is, and no longer as a drive.
- **ADR-196 Consequences' line on `cd ".."` stands**: a quoted token is still counted for the piece and for the string.
- **A command at the bound on readings can take about seven seconds** (§7).
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **The file system's reading alone.** Rejected: PowerShell, `cmd`, Node and bash's `cd` fold the path as text, so a path refused only as walked would be let through where it is read as text.
- **The second reading for the Bash tool alone.** Rejected in §1: the text does not say which program reads the path.
- **Keeping an unresolvable name as "not there", with the reason recorded.** Weighed, since nothing reads through one. Rejected for §6 of ADR-196: the guard had not reached a decision about where the name leads.
- **A piece of a cut never read as a drive.** Rejected: `ls x,Q:..` and an unquoted brace list are lists a shell does make, and must stay refused.
- **A Consequences line for the object keys and no rule.** Weighed against §4's way through. Rejected because a hook that refuses the commands this repository's notes recommend is a hook that gets removed, which is ADR-196's own argument.
- **Keeping the first form of §4 and recording the fifth gate's four shapes as not covered.** Rejected: each is an ordinary list of paths that ADR-196's guard refused, so the rule would have opened what was closed.
- **Telling what the shell really quotes.** Rejected: it needs a shell's own reading of comments, escapes and here-documents, in two shells. The shape of the group needs none of it.
- **A lower bound on readings.** Rejected in §7.
- **A timeout for the hook in `settings.json`.** Rejected in §7: a hook that times out lets the call through, so a timeout moves nothing.

## What this does not decide

- **`InvocationAccount.refusalToWrite`**, which #432 also names. It is Java under ADR-198, compares paths as text, and is raised as its own ticket.
- **What Claude Code's Read does with `..` on a system that is not Windows.** §1 does not depend on it.
- **Whether Git Bash follows a symlink written as a file** (§8).
- **Whether a timed-out hook lets the call through, by execution.** §7 rests on the documentation.
