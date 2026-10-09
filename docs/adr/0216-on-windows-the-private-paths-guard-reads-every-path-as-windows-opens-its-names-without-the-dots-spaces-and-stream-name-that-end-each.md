# ADR-216 — On Windows the private-paths guard reads every path as Windows opens its names, without the dots, spaces and stream name that end each

- **Date**: 2026-10-09
- **Status**: accepted. The guard's change is drafted outside every `.claude` folder and put in place by the operator, as [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §7 has it. This record, its cases and the guard land in one change.
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), in these places and no others:
  - §3, the paths of a call: on Windows each is read once more, as Windows opens its names (§1 below);
  - §4's "One reading is counted and not judged: a token that is a plain name, with no separator in it, and that does not exist in a folder": it no longer holds for a plain name that Windows opens as another name (§3 below);
  - §5's list of what the hook does not cover, which gains §5 below.

  ADR-196's rule (§1), its allow list and its "decision is made from the path's text, before the file system is asked" (§2), its bounds, and every way §6 makes the hook fail closed stand as written.
- **Amends**: [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md) §1, "A path that holds `..` is read twice, and either reading refuses": on Windows each of the two readings is read once more, and any of them refuses. ADR-201 §2 stands, and §2 below adds a refusal made in its manner.
- **Amends**: ADR-215, in these places and no others: §8's item "A `read` line beneath a plain line, where the folder is written with a dot after its name", which is closed on Windows and does not arise elsewhere; and the first item of its "What this does not decide", which this record decides. ADR-215 §3(b) compares a name with `.claude` as it did, and that comparison is now also one case of §1 below.
- **Keeps**: [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md)'s one admitted command, recognised before any path or text of it is read. Its current directory is checked with every reading of §1, as every current directory is.
- **Settles**: [#465](https://github.com/algernon28/vespera/issues/465).

## Context

ADR-196 refuses a path inside a folder that holds `vespera.db` or `vespera.lock`. To find such a folder the guard asks Node about the path, and a name Node says is not there is answered from the folder it would be in (ADR-201 §2). On Windows, Node says `wd.` is not there, and PowerShell opens `wd.\report.html` as `wd\report.html`. So the guard answered `wd.\report.html` from the folder above `wd`, found no working directory there, and let the call through.

**The same spelling passed a `read` line beneath a plain line.** Where a local allow list names `shelf` plainly and `read shelf/reference`, the text `shelf/reference./x` falls under the plain line only, Write is let through, and PowerShell writes into the folder the `read` line names. The shipped list has no such pair. A local list can.

ADR-215 §3(b) already compares a name with `.claude` without the dots and spaces that end it and without a stream name after a colon. The working-directory check and the allow-list comparison did not, and ADR-215 left both here.

The issue asks three things: whether every name of a path is read so on Windows, or only before the working-directory check and the allow-list comparison; whether a stream name is taken off as well; and whether any other tool opens such a name as PowerShell does.

### Measured

**On the operator's machine**, Windows 11, Node 22.23.1, on 2026-10-09, while #459 was settled, with synthetic fixtures only. These are recorded in ADR-215's Measured section and in #465, and were not run again for this record:

- `Get-Content wd./report.html`, with `wd` holding `vespera.db`: the guard ended with exit 0, and PowerShell printed the file. `Get-Content wd/report.html`: exit 2.
- `Get-Content '.claude.\settings.json'` and `Get-Content '.claude\settings.json.'` both printed the fixture file, and `Set-Content '.claude.\written.json'` wrote into `.claude`. Node's `lstat` answered `ENOENT` for both spellings, and so did Git Bash's `cat`.
- `Get-Content '.claude/settings.json '` printed the file, and `Get-Content '.claude \settings.json'` found nothing. A space after a file's name was taken off, and one after a folder's name was not.
- `realpath` of `.claude/settings.json::$DATA` and of `.claude::$INDEX_ALLOCATION/settings.json` is `.claude\settings.json`. A stream name leads to the file or folder it is a stream of, where that is there.

**On Linux, in this session**, on 2026-10-09. No Windows machine was at hand.

- `node --test src/test/hooks/private-paths-guard.test.mjs` with the cases of §6: 883 tests, 719 passed, 164 skipped, none failed, against the guard installed in the checkout; and the same with `VESPERA_GUARD_DRAFT=target/guard-draft`, the draft of this record's guard. On Linux 13 of §6's 42 cases are started and 29 are not, since their outcome rests on Windows.
- A throwaway copy of the draft guard was started with `process.platform` set to `win32`, so that its Windows branches ran over POSIX paths, on a fixture of its own under the temp folder. It refused every spelling §6 claims refused, and let through every one §6 claims let through. It also refused `echo '{"a":1}'`, `sed ':a;N;$!ba;s/x/y/g' README.md`, `git push origin :claude/old-branch` and `git commit -m "Changed src/.../Guard.java"` (Consequences). This holds what the draft's rule decides of a text. It says nothing of what Windows, Node on Windows or PowerShell answers.

### Read, and not executed

Read on 2026-10-09:

- **Why Node says "not there".** In Node 22's `src/node_file.cc`, `LStat` passes its path through `ToNamespacedPath`, which on Windows (`src/path.cc`) gives an absolute path its `\\?\` form. Microsoft's "Naming Files, Paths, and Namespaces" says that prefix "tells the Windows APIs to disable all string parsing and to send the string that follows it straight to the file system". So nothing takes a trailing dot off before Node asks. **The guard cannot learn what PowerShell opens by asking Node.**
- The same page: "Do not end a file or directory name with a space or a period. Although the underlying file system may support such names, the Windows shell and user interface does not."
- **How Windows takes them off.** .NET's "File path formats on Windows systems", on a path that does not begin with `\\?\`: "If a segment ends in a single period, that period is removed. (A segment of a single or double period is normalized in the previous step. A segment of three or more periods isn't normalized and is actually a valid file/directory name.)", and "If the path doesn't end in a separator, all trailing periods and spaces (U+0020) are removed." PowerShell runs on .NET. By that text Windows takes one dot off a name that is not the last, and every dot and every space off the last. The measurements above agree with it, the space after `.claude` that was not taken off included. It says nothing of a stream name.

### Not measured

- **Anything on Windows for this record.** The cases of §6 that are started on Windows alone were written from the rule and traced through the guard's code, and run in the simulation above. Their first run on Windows is CI's NTFS job and the operator's.
- **Whether Claude Code's own Read, Edit and Write open `wd.\x` as `wd\x`.** Node and Git Bash's `cat` were measured to answer "not there". No other program was tried.
- **What Windows opens for a name made only of dots and spaces**, such as `...` or `.. `, beyond what .NET's page says of three or more dots; and for a name that is only a stream name, such as `:x`.
- How long the extra reading takes. It is one more check where a name changes and none where none does (§1).

## Decision

### 1. On Windows every path is read once more, as Windows opens its names

The guard reads a path as its text, with `..` folded, and as the file system walks it (ADR-201 §1). **On Windows each of those readings gets one more: the path as Windows opens it.** It is made from that reading's absolute path by taking off each name after the root, the drive or the UNC share, first a stream name after a colon and then the dots and spaces that end it. So `wd.\report.html`, `wd..\report.html`, `wd \report.html` and `wd::$INDEX_ALLOCATION\report.html` are each read as `wd\report.html` as well, and `report.html.`, `report.html ` and `report.html::$DATA` as `report.html`.

**That reading is judged as every other reading is, and any reading that refuses refuses the call:**

- the allow list and its `read` lines: a path written with a dot after the name of a folder a `read` line names is beneath that line as Windows opens it, and there the `read` line decides, as ADR-215 §3(a) has it;
- a closed `.claude` folder (ADR-215 §3(b));
- where its links lead, and a name that is there and cannot be followed (ADR-201 §2);
- a working directory above it;
- for a search root, a working directory beneath it (ADR-196 §3.4).

**It is every name of the path, and every check.** The issue's other choice was the working-directory check and the allow-list comparison alone. One reading through one check is what makes those two see the same path, and the closed `.claude` folder, the links and the walk beneath a search root then see it too, with nothing to keep in step. Windows takes a dot off a name wherever it stands (Read, and not executed), and the measured spellings stand at both places, after a folder's name and after a file's.

**It takes off more than Windows does, on purpose.** By .NET's page, Windows takes one dot off a name that is not the last, and no space. This reading takes every dot and every space off every name. So `wd..\x` and `wd \x` are read as `wd\x`, where by that page Windows opens a folder named `wd.` or `wd `, which its own shell does not make. Taking more off can only refuse more. The page is read and not executed, one Windows build was measured, and ADR-215 §3(b) takes the same off `.claude` on the same ground (its decision 12). **A space after a folder's name was measured not to be taken off, and a path that holds one is refused all the same**: refusing it costs nothing an agent needs, and one reading for every name is simpler to hold than one that tells a folder's name from a file's.

**A stream name is taken off too, on every name**, as ADR-215 §3(b) takes it off `.claude`. A stream name leads to the folder or file it is a stream of (Measured). `realpath` already followed one where the name is there; this reading follows it in the text, where the name is not there and in the allow-list comparison.

**Which tool opens a name so no longer matters.** Every tool's paths go through the same readings: a file tool's field, a search tool's `path` and the folder of its pattern, the current directory, and every path a shell command names, against every folder it names. Whichever program opens `wd.` as `wd`, the reading as Windows opens it is refused.

**The reading is made from text.** It asks the file system nothing until the check of that reading does, so ADR-196 §2's "decision is made from the path's text, before the file system is asked" stands. Where no name of a path changes, the reading is the path itself and is not checked again.

### 2. On Windows a name made only of dots, spaces or a stream name is refused

A name that is left with nothing once its stream name and the dots and spaces that end it are taken off, and that is neither `.` nor `..`, is one whose opening is not known: `...`, `.. ` and `:x` are three. .NET's page says three or more dots are a valid name. Nothing measured says what PowerShell, `cmd` or another program opens for one, and nothing read says anything of `.. ` or of a name that is only a stream. **So on Windows a path that holds such a name is refused, as one whose opening cannot be ruled out**, in the manner of ADR-201 §2's refusal of a name the guard cannot follow. The refusal says that a name of the path is made only of dots, spaces or a stream name, and that what Windows opens for it cannot be ruled out.

`.` and `..` are folded by ADR-201 §1's readings before this one is made, and are not names here.

### 3. A plain name that Windows opens as another name is read for that name

ADR-196 §4 counts and does not judge one reading: a token that is a plain name, with no separator in it, and that is not there in the folder it is read against, since that reading is the folder's own answer. **A plain name whose opened form is not itself is no longer that reading.** It is read against each folder with every reading of §1, so `Get-ChildItem 'my runs. '`, from the folder that holds the working directory `my runs`, is refused. A plain name that Windows opens as itself is answered from its folder as before.

A dot that ends a token was taken off before this record, as punctuation that ends a sentence (ADR-196 §4), so `Get-ChildItem wd.` was refused already. A plain name reached the counted reading with its dots still on where a space followed them, as in a quoted `'my runs. '`, and with a stream name in it where the name before the colon was not there.

### 4. A folder a command names is reached by its opened reading too

In a shell command each reading that is a folder is a folder the command names, as ADR-201 §1 has it for the walked reading, and the reading of §1 is one of them. So `Set-Location deep.\; Get-Content a\b\notes.txt`, where `deep\a\b` is a working directory, reads `a\b\notes.txt` against `deep` and is refused.

Where the folder named is itself a working directory, `Set-Location wd.\; Get-Content report.html`, its own token refuses first. `Set-Location wd. ; Get-Content report.html`, whose dot ends its token, was refused before this record.

### 5. What the hook does not cover, added to ADR-196 §5

- **A dotted name a command builds at run time**: `Get-Content ("wd" + ".\report.html")`, or a variable set in the same command that holds `wd.`. It is ADR-196 §5's item, and reaches a working directory as any other built path does.
- **A form ADR-196 §5 leaves unread, with dots in it.** §1 reads what the guard reads as a path, and adds no reading of a token's text. A token headed by a variable with no value, `$X/wd./report.html`, is not read, as `$X/wd/report.html` is not.
- **An 8.3 name of a working directory that is not there when the guard asks.** Nothing answers for a name that is not there but the folder it would be in (ADR-201 §2). An 8.3 name of a folder that is there is followed by `realpath` to its long name (ADR-215 §5), and with a dot after it, through the reading of §1.
- **What a name made only of dots, spaces or a stream name means to Windows.** §2 refuses it and does not measure it.

None of these has a case, on ADR-196 §4's ground that "a case would hold the gap in place".

### 6. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 42 cases and holds 883. On Linux 13 of them are started and pass against both the guard installed in the checkout and the draft; the other 29 are started on Windows alone. Traced through the code of the guard before this build, 27 of the 42 are refused by the draft and let through by that guard, on Windows; the other 15 hold before and after. That was not run on Windows (Not measured).

- **Z101 to Z113**: §1, §3 and §4 in a PowerShell command, from the folder that holds the fixture's working directories. The working directory's name with a dot after it, which is the issue's call (Z101), with two dots and a backslash, with a space after it in quotes, the same for a working directory whose name holds a space, with a dot after that one's name, and through its stream name; a dot and a data stream after the file's name; the folder that holds only `vespera.lock`, with a dot; `Set-Location` to the working directory's name with a dot and a space, and with a dot and a backslash; `Set-Location` to an allowed folder named with a dot, then a path into a working directory beneath the folder it opens (§4); and a quoted plain name with a dot and a space after it (§3). All refused. Four are started on every platform, each refused there for a reason its words give: Z103, whose token is cut at the space and is then the working directory itself; Z107 and Z108, whose folder is the working directory by its plain name; and Z110, whose dot ends its token.
- **Z201 to Z213**: §1 through Read, Write, Edit, NotebookEdit, a relative Read, Grep and Glob by their path, Glob by an absolute pattern and Grep by a glob that climbs: the working directory's name with a dot, two dots and a space after it, and the folder that holds only `vespera.lock`. All refused. Z213, a dot after the file's name in the working directory by its plain name, is started on every platform.
- **Z301 to Z304**: §2. A folder named with three dots, with two dots and a space, and a name that is only a stream name, through Write and PowerShell. Refused on Windows.
- **Z401 to Z405**: what stays usable, let through on every platform: a dot inside a folder's name; a dotted name of an allowed folder through Read, PowerShell and Grep, which on Windows opens the folder; and a dot after a file's name outside every working directory.
- **Z501 to Z507**: §1 and the `read` line. A checkout whose local allow list names `${HOME}/shelf` and `read ${HOME}/shelf/reference`. Write, Edit and `Set-Content` through `reference.`, and Write through its stream name, are refused on Windows; Write beneath `reference` by its plain name is refused, Write beneath `shelf` and Read through `reference.` are let through, on every platform.

**What the issue's acceptance maps to**: a dot after any name of the path, Z101, Z105, Z109, Z111, Z112, Z201 to Z204, Z207 to Z212; two dots, Z102 and Z205; a space, Z103, Z104, Z113 and Z206; a stream name, Z106, Z108 and Z504; a dot after the file's name, Z107 and Z213; and the `read` line beneath a plain line, Z501 to Z504.

### 7. Elsewhere nothing changes

On a system that is not Windows a dot, a space and a colon are characters of a name. The reading of §1 is the path itself there, §2 refuses nothing, and §3 answers every plain name as before.

## Decided here, open to the operator's overruling

1. **§1: every name, and every check**, where the issue offered the working-directory check and the allow-list comparison alone.
2. **§1: every dot and every space off every name**, where .NET's page has Windows take one dot off a name that is not the last, and spaces off the last alone.
3. **§1: a space after a folder's name is taken off and refused**, though it was measured not to open the folder.
4. **§1: a stream name is taken off every name**, and not off `.claude` alone.
5. **§2: on Windows a name made only of dots, spaces or a stream name is refused.** It costs the commands Consequences lists, a token that begins with a colon among them. The other choice is in Alternatives: such a name, where it is the last of a path, read as the folder it is in.
6. **§3: a plain name Windows opens as another name is read against every folder**, where it was answered from the folder alone.

## Consequences

- **On Windows a call that names a working directory, or a place a `read` line names, with a dot, a space or a stream name after any name of the path is refused**, through every tool and in every form the guard reads as a path. The acceptance of #465 is met for those forms.
- **Some harmless commands are refused on Windows**, each to be reworded, because §2 reads every relative token of a shell command as a path:
  - a token that begins with a colon: a JSON value written after `":` with no space, as in `echo '{"a":1}'` or a here-document body for `gh api --input -`; a `sed` label, as in `sed ':a;N;$!ba'`; and a refspec that deletes a branch, `git push origin :claude/x`. A body goes in a file and is named, `git push --delete` deletes a branch, and a `sed` script goes in a file with `-f`;
  - an elision with three dots between separators in a quoted sentence, as in `git commit -m "Changed src/.../Guard.java"`. A message goes in a file with `-F`.
- **A path whose names change costs one more check on Windows**, and a path whose names do not change costs none. A plain name whose opened form differs is read against each folder the command names, within ADR-196 §4's 20,000 readings.
- **On a system that is not Windows nothing changes.**
- **The count in ADR-215 §9 is behind**: the table holds 883 cases.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **The working-directory check and the allow-list comparison alone.** Rejected in §1: each other check would need the same, and one reading through one check is fewer places to be wrong.
- **Windows's own rule as .NET's page states it**: one dot off a name that is not the last, and every dot and space off the last. Weighed: it is the documented rule, and it refuses less. Rejected in §1: it is read and not executed, one Windows build was measured, and the wider rule refuses only names Windows's own shell does not make.
- **Asking Windows what it opens**, by starting PowerShell's `Resolve-Path` or .NET's `GetFullPath` from the guard. Rejected: Node's file system calls give a path its `\\?\` form (Read, and not executed), so asking Node is what failed, and asking Windows means a process started for every path of every call, which costs time on every call and is one more thing that can fail or hang; and its answer is .NET's, not that of every program a command can start.
- **Refusing on Windows every path one of whose names ends in a dot, a space or a stream name.** Weighed: it is simpler, and fails closed. Rejected: such a path is the same place as the path without them, and the reading of §1 judges that place, so refusing it refuses nothing more of a working directory or a `read` place, and costs `scratch.\note.txt` and every other dotted spelling of an allowed place.
- **Reading a name made only of dots, spaces or a stream name, where it is the last of a path, as the folder it is in**, and refusing it only where another name follows. Not taken here, and open (Decided here, 5). Whatever such a last name opens is the folder it is in or a stream of that folder, by .NET's rule for the last name and by what a stream name was measured to lead to, and the folder's own reading judges that. It would keep `'{"a":1}'` and `sed ':a'` usable. `git push origin :claude/x` and `src/.../x` would stay refused, since a name follows.

## What this does not decide

- **Whether Claude Code's own Read, Edit and Write open a dotted name as PowerShell does.** §1 makes the answer not matter to the guard.
- **What Windows opens for a name made only of dots, spaces or a stream name.** §2 refuses it, and nothing measured it.
- **A dotted name a command builds at run time, and an 8.3 name of a working directory that is not there when the guard asks** (§5).
- **Whether the costs of §2 are kept** (Decided here, 5).

## What the build changed

1. `.claude/hooks/private-paths-guard.mjs`, drafted at `target/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator: a name as Windows opens it, and a path as Windows opens it, with no path where a name is made only of dots, spaces or a stream name (§1, §2); that reading added to each reading a path is checked with, and refused where there is no such path; the counted reading of a plain name kept for a name Windows opens as itself (§3); a search's pattern judged against each start with that start's own walked flag, where it took every start after the first for a walked one, since the reading of §1 now stands among the starts; and its header comment.
2. `src/test/hooks/private-paths-guard.test.mjs`: §6's cases, the fixture checkout for Z501 to Z507, and its header comment.
3. ADR-196, ADR-201 and ADR-215 take a pointer to this record at their top. Their decisions are not edited.
