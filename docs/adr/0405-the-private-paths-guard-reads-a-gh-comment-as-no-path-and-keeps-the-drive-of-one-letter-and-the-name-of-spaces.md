# ADR-405 — The private-paths guard reads a gh comment as no path, and keeps the drive of one letter and the name of spaces

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Amends**: [ADR-351](0351-the-private-paths-guard-reads-a-message-a-pattern-a-program-and-a-here-document-into-the-temp-folder-as-no-path.md) §1, the flags whose argument is text: `gh`'s gain `-b` and the `-c` and `--comment` of four commands (§1 below), and nothing else of ADR-351 changes.
- **Keeps**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) §4's drive of one letter and §5's list of what the guard does not read; [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §3(c), which still reads every text §1 takes out, and §7, by which the change is a draft the operator installs; [ADR-217](0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md) §2's refusal on Windows of a name made only of dots, spaces or a stream name; [ADR-347](0347-in-a-git-command-the-private-paths-guard-reads-gits-index-form-as-the-path-it-names-and-keeps-the-drive-of-one-letter-and-the-name-of-spaces.md), its Decided 1 and 3 among it; ADR-351 §2, which leaves `sed` out, and §4's list of trusted programs; and [ADR-356](0356-the-private-paths-guards-reading-of-a-bash-command-is-held-against-the-arguments-bash-gives.md).
- **Settles**: [#765](https://github.com/algernon28/vespera/issues/765), texts the guard reads as paths that are none.

## Context

The guard reads a Bash or PowerShell command's text for paths (ADR-196 §4), and on Windows refuses one letter and a colon at the head of a token as a path on that drive, and a name made only of dots, spaces or a stream name (ADR-217 §2). ADR-351 takes out of that reading, in a Bash command it can cut as Bash does, the text a flag or an operand gives a program it names and that the program opens no file by. Issue #765 names three shapes of text the guard still refuses that are no path, and asks which stop being read as one:

1. a regex literal in a `grep`, `awk` or `sed` program, and a script's source text given to an interpreter, such as `/^>>>>>>> /`;
2. a word such as `m:m.group`, one letter and a colon with no separator after it;
3. a lone `/**` or `*/`.

A fourth shape is decided with them: prose in an argument, `gh issue close … --comment "Done on main. Part A: ADR-370 …"`, refused on Windows for `A:`, one letter and a colon read as a drive.

### Measured

**The installed guard, on Windows, by `src/test/hooks/private-paths-guard.test.mjs`** with §5's cases added (the draft's file as it stood before this record, byte for byte the installed one, named by `VESPERA_GUARD_DRAFT`):

- `awk '/^=======$/{n++} /^>>>>>>> /{print NR}' README.md` (C502) and `grep -c '/^>>>>>>> /' README.md` (C821) are let through: ADR-351 §2 already takes a single-quoted `grep` pattern or `awk` program out. `node -e "x.replace(/^>>>>>>> /, '')"` (C825) is let through, `/^>>>>>>>` being a rooted POSIX path, which the guard does not read (ADR-196 §5). `sed -n '/^>>>>>>> /p' README.md` (C848) is refused, its piece `/p` read as Git Bash's drive P;
- `python -c "import re; print(re.sub('a', lambda m:m.group(0), 'a'))"` (C841) is refused for `m:m.group`, "outside the allow list", read as a path on drive M; so is `lambda m: m.group(0)` (C842) for `m:` alone; `lambda mo: mo.group(0)` (C826) is let through;
- a lone `/**` and `*/` are let through: in `node -e "/**"` (C823), in `echo '*/'` (C824), and on lines of their own in a `python -c` source (C822). `echo " /** "` (C846) and `echo 'x */ '` (C847) are refused, each for "a name of it is made only of dots, spaces or a stream name": the space between the separator and the quoted string's edge is such a name;
- `echo "Done on main. Part A: ADR-370."` (C843) is refused for `A:`, "outside the allow list", and so are C811 to C815, the issue's `gh issue close --comment` and its kin and `gh issue comment -b`, each for `A:` or `B:` read so; C816 and C817, a `--comment` and a `-b` whose prose names `../outside/doc.txt`, are refused for that climb, which is read on every platform;
- every case of C83 and C84 is refused, as it claims.

**The draft of this record, on Windows, by the same file**: C811 to C817 are let through, and the 1,523 cases of `private-paths-guard.test.mjs`, `deliverable-tree-guard.test.mjs` and `working-directory-counts.test.mjs` pass.

### Read, and not executed

- gh 2.97.0's help, on this machine: `gh issue close` has `-c, --comment string` ("Leave a closing comment"), `-r, --reason string` and `--duplicate-of string`; `gh issue reopen` and `gh pr reopen` have `-c, --comment string`; `gh pr close` has `-c, --comment string` and `-d, --delete-branch`; each inherits `-R, --repo`. None of the four has a flag that reads the comment from a file. `-b` is `--body` in `issue create`, `issue comment`, `issue edit`, `pr create`, `pr comment`, `pr edit`, `pr merge` and the command that approves a pull request or asks for changes to it; the flag that reads a file is `-F`, `--body-file`.
- Microsoft's "Naming Files, Paths, and Namespaces": "If a file name begins with only a disk designator but not the backslash after the colon, it is interpreted as a relative path to the current directory on the drive with the specified letter", and "C:tmp.txt" refers to a file named "tmp.txt" in the current directory on drive C; the system keeps track of the current directory of each drive letter; `*` and `?` are reserved characters, which no name may hold.

## Decision

### 1. A closing or reopening comment, and `gh`'s `-b`, are no path

**In a Bash command, the argument of these flags is not read as a path**, as ADR-351 §1 reads its flags:

- `gh`'s `-b`, the letter of `--body`, after each command of ADR-351's table whose table has it take an argument: `issue create`, `issue comment`, `issue edit`, `pr create`, `pr comment`, `pr edit`, `pr merge` and the command that approves a pull request or asks for changes to it;
- the `-c` and `--comment` of `gh issue close`, `gh issue reopen`, `gh pr close` and `gh pr reopen`, which join the table with their options as gh reads them: `issue close`'s `-r`/`--reason` and `--duplicate-of` take an argument and give no text, `pr close`'s `-d`/`--delete-branch` takes none, and each takes `-R`/`--repo`.

A flag of these names gives text only where the command's table has it take an argument: the `--comment` of the command that approves a pull request, which only chooses a comment over an approval, and `pr view`'s `-c` take none, and the word after them is read as before (C831, C832). Everything else of ADR-351 §1 holds for these flags: the argument is a literal the shell gives `gh` as one word, the walk of the command's options ends at a word the guard does not read whole (ADR-356 §3), an option before the flag that takes it as its value makes it no flag (C833), nothing is taken out of a command §4 does not trust or of a PowerShell command (C844, C845), and ADR-215 §3(c) still reads the argument for a closed `.claude` folder (C837).

**Why such an argument cannot be a path or a stream name**: `gh` sends the text to GitHub as the comment's or the body's text, and opens nothing by it. No form of these flags names a file (that is `--body-file`, which stays read), so no drive, stream name, device name or wildcard in the text reaches Windows' file system.

### 2. One letter and a colon stays a drive

**A token headed by one letter and a colon is read on Windows as a path on that drive wherever the guard still reads text**, as ADR-196 §4 and ADR-347 Decided 1 have it. `m:m.group(0)` is the name `m.group(0)` in the current folder Windows keeps for drive M, and `A:` alone is that folder itself, which a program that walks a folder it is given (`grep -r`, `cp -r`, `tar`, `Get-ChildItem -Recurse`) reads whole. Any letter may name a drive when the command runs, by a disk, `subst` or `net use`, and the guard asks the file system nothing before the allow list decides. A word of prose and a parameter of a lambda are told from such a path only by what the program does with them, which the guard knows for §1's flags and ADR-351's alone. So `lambda m:m.group(0)` and `lambda m: m.group(0)` stay refused on Windows (C841, C842), and so does `Part A:` in `echo`, in PowerShell, or in any flag but §1's and ADR-351's (C838, C843, C844). The way round is a parameter of more letters than one, `lambda mo: mo.group(0)` (C826), prose in a flag that gives text, or the text in a file.

### 3. A lone `/**` or `*/` is let through, and a name of spaces beside it is not

**A lone `/**` or `*/` is no refusal, and this record holds that it stays none** (C822 to C824). `/**` is a rooted POSIX path, which the guard does not read (ADR-196 §5), and Git Bash's drive form is a slash and a letter, which it is not. `*/` is the relative name `*`, read against each folder the command names as every relative name is, and lying where that folder lies; no Windows name may hold `*`.

**A name made only of spaces stays refused on Windows**, as ADR-217 §2 and ADR-347 Decided 3 have it: ` /** ` at a quoted string's head and `x */ ` at its end each hold one, between the separator and the string's edge (C846, C847). Windows opens a name without the spaces that end it (ADR-217 §2), and what the empty name that leaves opens rests on where it stands: the folder above, or the drive's root where it heads the path. The way round is no space between `/**` or `*/` and the quote, or the text in a file.

### 4. A `sed` program and a script's source are read as before

**A `sed` program stays read** (C848, C849), as ADR-351 §2 has it: `sed` reads a file by its `r` and `R` commands, writes one by `w`, `W` and the `w` flag of `s`, and runs a command by `e` and the `e` flag of `s`. Its regex literals are read with it, so `/p` is Git Bash's drive P on Windows.

**A script's source given to an interpreter stays read**: `python -c`, `node -e`, `bash -c`, and a here-document given to any of them. The script opens whatever its text names, by a call in a language the guard does not parse, and the guard's reading of that text is the only check on what it opens (C850, C851). No rule of a command's syntax tells a literal of the language from a path in it. A regex literal that is a rooted POSIX path is already let through (C825); one whose text is a drive, a name of spaces or a path outside the allow list is refused, and the script goes in a file written with the Write tool and run by its path.

### 5. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 33 cases:

- **C811 to C817**, let through (§1): `gh issue close --comment`, `gh pr close -c`, `gh issue reopen --comment=` and `gh pr reopen -R o/r -c` of prose holding one letter and a colon, the first being the issue's; `gh issue comment -b` of such prose; and `gh issue close --comment` and `gh pr create -b` of prose naming `../outside/doc.txt`;
- **C821 to C826**, let through, as the guard already reads them (§2 to §4): `grep` of a single-quoted regex literal; a `python -c` source with a comment block whose `/**` and `*/` stand on lines of their own; `node -e "/**"`; `echo '*/'`; `node -e` of a regex literal; and a lambda whose parameter has two letters;
- **C831 to C839**, refused (§1): the `--comment` of the command that approves a pull request, and `gh pr view -c`, which take no argument, and `gh issue close -R --comment`, each before a file outside the allow list; `--duplicate-of` of one; a file after a comment's text; a comment that is a command substitution reading one; a comment naming the allow list; on Windows `-r` of prose holding `A:`; and `--comment` after `gh close`, a path the table does not hold;
- **C841 to C851**, refused (§2 to §4): on Windows `lambda m:m.group(0)`, `lambda m: m.group(0)`, `echo` of prose holding `Part A:` and its `gh issue close --comment` in PowerShell; that command naming `../outside/doc.txt` in PowerShell on every platform; on Windows `echo " /** "` and `echo 'x */ '`, and `sed -n '/^>>>>>>> /p'`; `sed`'s `r` of a file outside the allow list; `node -e` reading one; and on Windows `python -c` opening a file on a drive no allowed root is on.

Each C8 case of Bash that the guard cuts and trusts is held against the arguments Bash gives, as ADR-356 §1 holds every C case. Against the installed guard C811 to C817 fail, and the rest pass (Measured).

## Decided here

1. **§1 takes out only flags `gh` reads as text and opens no file by.** A closing or reopening comment, and `-b`, are `--body`'s kin: the text is posted. A flag of the same name that takes no argument makes the next word an operand, which `gh` may read as anything, so a table of each command's options decides, as ADR-351 Decided 1 has it.
2. **§2 keeps the drive of one letter whole.** Prose and a path on a drive share every character the guard sees, and a rule that reads `A:` and a space as prose reads `grep -r x A: B:` the same way. The guard knows a text is prose only where it knows what the program does with it.
3. **§3 keeps the name of spaces whole.** The one form it costs, a space between a lone `/**` or `*/` and a quote, is rewritten by taking the space out.
4. **§4 keeps every program's text read that the guard has no table for.** A script's language and a `sed` program's commands both open files by text the guard would have to parse as their program does, and a mistake there fails open.

## Consequences

- **The issue's fourth command, and any prose with one letter and a colon given to `gh` by `--comment` on closing or reopening, or by `-b`, is let through on every platform.**
- **The issue's other shapes are let through where the guard already reads them as no path**: a single-quoted `grep` pattern or `awk` program, a lone `/**` or `*/`, and a regex literal that is a rooted POSIX path.
- **On Windows `m:m.group`, `m: m.group`, prose holding one letter and a colon outside §1's and ADR-351's flags, a name made only of spaces and a `sed` program's piece that reads as a drive stay refused**, and so does every path a script's source names outside the allow list, on every platform.
- **The installed guard reads none of §1 until the operator installs the draft** (ADR-215 §7).
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **Reading one letter and a colon followed by a space as prose.** Rejected: Decided here 2.
- **Reading a drive of one letter as a path only where a disk is mounted on it.** Rejected: a letter can be mapped between the check and the command, and the guard decides from a path's text before it asks the file system anything.
- **Taking every quoted argument of `gh` out.** Rejected: `--body-file`, `--input` and `-F` name files `gh` reads (ADR-351's C608, C779 and C780).
- **Taking a script's source out of the reading for an interpreter's `-c` or `-e`.** Rejected: Decided here 4; the source names the files the script opens.
- **Reading a name made only of spaces as the name Windows folds it to.** Rejected: Decided here 3; ADR-217 §2 refuses it because what it opens rests on where it stands.

## What this does not decide

- **Whether a single-quoted `sed` program that holds none of `r`, `R`, `w`, `W` and `e` joins ADR-351 §2.** Such a program opens no file and runs no command, and `sed` would join §4's list of trusted programs with it; but it would let through `sed 's/ /_/g'`, the form ADR-347 Decided 3 keeps refused with "awk/sed programs go in a file", which ADR-351 §2 keeps for `sed`. That is a later record's, on the user's word.
- Whether other `gh` flags give text, `--title`, `-t` and `pr merge`'s `--subject` among them, or other programs' flags; each is a later record's, as ADR-351 leaves them.
- Anything of a PowerShell command, out of which nothing is taken.

## What the implementation changes

1. `.claude/hooks/private-paths-guard.mjs`, drafted as `docs/agents/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator (ADR-215 §7): `GH_COMMANDS` gains `issue close`, `issue reopen`, `pr close` and `pr reopen`; `GH_TEXT_FLAGS` gains `b`, `comment` and `c`; `givesText` asks that the flag take an argument in the command's table, and `textSpans` reads `gh`'s flags by it; and the header comment.
2. `src/test/hooks/private-paths-guard.test.mjs`: §5's cases and its header comment, written before the guard.
3. ADR-351 takes a pointer to this record at its top. Its decision is not edited.
4. `docs/agents/private-paths-guard.md`: the flags whose argument is text, and what this record keeps refused.
