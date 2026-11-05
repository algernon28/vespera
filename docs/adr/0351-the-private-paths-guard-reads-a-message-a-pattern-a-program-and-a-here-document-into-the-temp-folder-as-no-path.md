# ADR-351 — The private-paths guard reads a message, a pattern, a program and a here-document into the temp folder as no path

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) §4, what a Bash command's text is read for as paths: §1 to §3 below take some of that text out, and nothing else of §4 changes.
- **Amends**: [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §3(c), the reading of every token's text for a closed `.claude` folder: §3 below takes the body of one kind of here-document out of it, and nothing else. §3(c) still reads every token §1 and §2 take out of the reading for paths.
- **Keeps**: [ADR-217](0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md) §2's refusal on Windows of a name made only of dots, spaces or a stream name, of every text that is still read as a path; [ADR-233](0233-the-private-paths-guard-reads-what-stands-beside-the-name-claude-a-leading-tilde-in-a-field-and-a-link-out-of-its-own-folder.md); [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) §3's listed commands, which are told from the command as written; [ADR-347](0347-in-a-git-command-the-private-paths-guard-reads-gits-index-form-as-the-path-it-names-and-keeps-the-drive-of-one-letter-and-the-name-of-spaces.md), whose git segments and index form are found in the command as written; and [ADR-348](0348-the-private-paths-guard-reads-a-variable-with-a-plus-sign-as-its-word-or-as-nothing-and-never-as-the-names-value.md).

## Context

The guard reads a Bash command's whole text for paths (ADR-196 §4) and every token of it for a closed `.claude` folder (ADR-215 §3(c)). Some of that text is given to a program as text it never opens: a commit message, a `gh` filter, a `grep` pattern, an `awk` program, the lines of a here-document `cat` copies into a file. Issue [#697](https://github.com/algernon28/vespera/issues/697) records five such commands refused, each costing a retry and a workaround: a `gh … --jq` filter holding `\(`, an `awk` program holding `/^>>>>>>> /`, a `git commit -m` message holding a word that begins with a slash and `\|`, a `grep` alternation holding `\|`, and a here-document writing an issue body to the scratchpad whose lines named files in the hooks and agents folders.

### Measured

**The draft guard at commit e4e7eff5, on Windows, by `src/test/hooks/private-paths-guard.test.mjs`** with §5's cases added:

- it refuses C501 to C504, the issue's first four commands, each for its text read as a path: the token `:\` of `'"\(.title):\(.state)"'`, the piece ` /^>>>>>>> /` of `'/^=======$/{n++} /^>>>>>>> /{print NR}'` and the whole of `"Split at \| and keep /: as written"`, each for "a name of it is made only of dots, spaces or a stream name" (ADR-217 §2); and the tokens `x:` and `y:` of `'x: \|y: '`, "outside the allow list", each a drive of one letter (ADR-196 §4);
- it refuses C505, the issue's fifth, on every platform: the here-document's lines `.claude/hooks/private-paths-guard.mjs` and `.claude/agents/architect.md` are tokens whose text names a closed folder (ADR-215 §3(c));
- it refuses C512 to C514, C516, C518 and C519: a message or a body that names `../outside/doc.txt` or a drive no allowed root is on, a message whose `/x/` is read as Git Bash's drive X, and a here-document whose line names a closed folder; and lets through C511, C515 and C517, whose text it reads as no name it refuses;
- C601 to C632 pass: each is refused.

**The draft guard of this record, on Windows, by the same file**: the 1,244 cases of `private-paths-guard.test.mjs`, `deliverable-tree-guard.test.mjs` and `working-directory-counts.test.mjs` pass, the C cases among them.

### Read, and not executed

- The Bash reference manual, "Here Documents": "If any part of word is quoted, the delimiter is the result of quote removal on word, and the lines in the here-document are not expanded." So the lines of a here-document whose delimiter is quoted are given to the program as written: no variable, command substitution or arithmetic is run.
- GNU grep's manual: `-e PATTERNS` and `--regexp=PATTERNS` give the pattern, and "If no -e or -f option is given, the first operand is the pattern"; GNU getopt reads an option after an operand too, and takes a unique prefix of a long option's name.
- The gawk manual: `-f`, `-e`/`--source`, `-i`, `-E` and `-l` give the program or a file of it; a program reads or writes a file only through a string or a variable after `getline <`, `>`, `>>` or `|`, or `@include "file"`.
- The jq manual: a program reads a file only by `import` or `include`; `-f`/`--from-file`, `-L`, `--slurpfile` and `--rawfile` name files.

## Decision

### 1. A flag's text is no path

**In a Bash command, the argument of these flags is not read as a path**:

- `git`'s `-m` and `--message`, after the subcommands `commit`, `tag`, `merge` and `stash`, and `-m` at the end of a cluster of `a`, `e`, `n`, `q`, `s` and `v` (`git commit -am`); the subcommand is the first word after `git` that is no option, `-C` and `-c` each taking the word after it;
- `gh`'s `--jq`, `-q` and `--body`;
- `grep`'s, `egrep`'s and `fgrep`'s `-e` and `--regexp`, and `-e` at the end of a cluster (`grep -ne`).

The argument is the next word, or the text after the `=` of `--message=`, `--jq=`, `--body=` and `--regexp=`. It is taken out only where it is a literal the shell gives the program as one argument: one single-quoted string, one double-quoted string with no `$(` or backtick, or a bare word with no quote, `$`, backtick, wildcard, brace or `~`. The program is the segment's first word, bare. No flag is read so after a word `--`, and the word after a redirect operator is its target, not a flag's argument.

### 2. A single-quoted pattern or program is no path

**In a Bash command, the first operand of `grep`, `egrep` and `fgrep`, of `awk`, `gawk` and `mawk`, and of `jq` is not read as a path where it is single-quoted and no option gives the pattern or the program.** Every word of the segment is walked, as getopt reads it: a short letter or long name that takes an argument takes the next word, a long name may be written by a unique prefix, and `--` ends the options. Where any word gives the pattern, the program or a file of either (`grep`'s `-e`, `-f`, `--regexp`, `--file` and `--exclude-from`; `awk`'s `-f`, `-e`, `-i`, `-E`, `-l`, `-W` (mawk's `-W exec` names a file of the program), `--file`, `--source`, `--include`, `--exec` and `--load`; `jq`'s `-f`, `-L`, `--from-file`, `--library-path`, `--slurpfile`, `--rawfile`, `--argfile` and `--run-tests`), no operand is taken out, since the first is then a file.

An `awk` program is taken out only where it holds no `"`: it names a file only in a string. A `jq` program is taken out only where it holds neither `import` nor `include`. `sed` is not among these: its program reads and writes files by its `r`, `R`, `w` and `W` commands and runs one by `e`, and ADR-347 keeps its refusal on Windows.

### 3. A here-document `cat` writes into the temp folder is no text the guard reads

**In a Bash command, the lines of a here-document are read neither for paths nor for a closed `.claude` folder where all of this holds**:

- its delimiter is quoted, `'EOF'` or `"EOF"`, so its lines are not expanded;
- its line is the segment alone, from the line's start to the line break: `cat`, then `>` or `>>` and the file, and the here-document's operator before or after them, and nothing else;
- the file is one word, a path from a root, or a variable of the environment and a path after it, with no `.` or `..` name, no other `$`, no `~`, wildcard or brace, and it lies beneath the folder the allow list names `${TEMP}`, where the scratchpad lies.

The lines run from the one after the operator's to the delimiter's, which is taken out too. The line of `cat` is read as before, so the file is judged as every path is, a closed `.claude` folder and a deliverable tree included, and every line after the delimiter is read as before.

### 4. Where the reading may not be the shell's, nothing is taken out

These are found by a cut of the command into its words and segments as Bash cuts them: at `;`, `&`, `|` and the line break outside quotes, a `#` at a word's start beginning a comment. Where that cut may not be Bash's, nothing is taken out and the command is read as before: a `(`, `)` or backtick outside quotes, a `$(` or backtick inside double quotes, a `$'`, `$"` or `${` outside quotes, a quote left unpaired, a backslash before a line break or at the end, and a here-string. A here-document's lines are no words of the cut, whatever its delimiter. In a PowerShell command nothing is taken out.

What is taken out is blanked, each character but a line break made a space, so that every other text keeps its offset. The guard reads the blanked command for paths, ADR-347's index form found in the command as written; it reads the command with only §3's lines blanked for a closed `.claude` folder; and it tells ADR-264 §3's listed commands and ADR-212's counting command from the command as written.

### 5. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 48 cases:

- **C501 to C505**, let through on every platform: the five commands of #697, written into the file as fixtures;
- **C511 to C519**, let through: `grep -e` and `--message=` with `\|`; a message and a `--body` that name `../outside/doc.txt`; `gh api`'s `--jq` and `-q`; a message naming a drive no allowed root is on (on Windows); `jq`'s program with `\(`; a here-document appended to a file in the temp folder with its redirect after the delimiter; and one followed by a command that reads a file inside the checkout;
- **C601 to C634**, refused: a file outside the allow list beside a skipped flag or operand (`git commit -m "fix" ../outside/doc.txt`, after `--`, after `--message=`, after `grep -e`, after a quoted pattern, `gh api --jq … --input`); a flag of the same letter that takes no text (`cat -e`, `head -q`, `git am -m`); a file option before a quoted pattern or program (`grep --exclude-from`, `grep -f`, `awk -f`, and C633 and C634, `mawk -W exec` and `jq --run-tests`); a command substitution in a message; an `awk` program with a string and a `jq` program with `import`; `sed`; a message naming the allow list (ADR-215 §3(c) still reads it, as Q108 claims); a message in PowerShell; and here-documents whose delimiter is not quoted, whose file lies outside the allow list, in a closed folder under the temp folder, climbs out of it or lies in the checkout, whose lines go to `bash`, whose line runs a second command, whose program is `tee`, and one followed by a line that reads a file outside the allow list; and, on Windows, a quoted file headed by one letter and a colon after a skipped pattern (C631) and a token that begins with a colon after a skipped message (C632).

Against the draft at commit e4e7eff5, on Windows, C501 to C505, C512 to C514, C516, C518 and C519 fail, and the rest pass (Measured).

## Decided here

1. **§1 and §2 name programs and flags, and no flag is skipped for every program.** `-e`, `-q` and `-m` take no argument in `cat`, `head`, `sort` and `git am`, and a file after them is the file read; a table by program is what keeps it read (C609 to C611).
2. **§1 and §2 take a text out of the reading for paths and not of ADR-215 §3(c)'s.** The issue's commands are refused for paths, and no command of it for a message naming a closed folder; a message that names one still goes in a file with `-F`, as AGENTS.md has it, and Q108 stands.
3. **§3 takes the lines out of both readings, and only for `cat` into the temp folder.** The fifth command is refused by ADR-215 §3(c) alone. A here-document's lines given to `cat` are copied, not run, where its delimiter is quoted; the file is the one thing written, and is judged. Into the temp folder it is what the Write tool may already write there; elsewhere, or by another program, nothing is taken out.
4. **§4: nothing is taken out where the cut may not be the shell's.** A command the cut cannot read is read whole, as before this record, so no reading of this record can let through more than a reading of the command as Bash parses it.
5. **A script written by §3 and run by a later line is not read.** `cat > "$TEMP/x.sh" <<'EOF'` and then `bash "$TEMP/x.sh"` runs lines the guard did not read; so does a file the Write tool wrote and a later command runs, which ADR-196 §5 already lists as not covered ("a path a command builds at run time"). This adds no kind of call the guard did not already let through in two.

## Consequences

- **The five commands of #697 are let through on every platform.**
- **A real path argument and a real write target are read as before**: beside a skipped flag or operand, after a flag of the same letter in another program, and as the file a here-document is written to.
- **A message that names a closed `.claude` folder is still refused**, and still goes in a file with `-F`.
- **On Windows a message, a pattern or a single-quoted `awk` or `jq` program holding a name made only of dots, spaces or a stream name, or one letter and a colon, is let through**; the same text elsewhere in a command is still refused there (ADR-217 §2, ADR-196 §4). A `sed` program is still refused there.
- **The installed guard reads none of this until the operator installs the draft** (ADR-215 §7).
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **Skipping the flags for every program.** Rejected: Decided here 1.
- **Skipping any quoted argument that looks like a regular expression.** Rejected: a quoted path holds the same characters as a pattern, `.` and `/` among them, and "looks like" is no rule a test can hold; the program's own reading of its words is.
- **Skipping a here-document's lines into any file the allow list lets through.** Rejected: the issue asks for the scratchpad, and a file inside the checkout is one a later agent may run or load; the temp folder is where the scratchpad lies.
- **Taking the skipped text out of ADR-215 §3(c)'s reading too.** Rejected: Decided here 2.

## What this does not decide

- Whether `/tmp`, which Git Bash mounts on the temp folder, names it for §3; the guard reads no rooted POSIX path (ADR-196 §5), so a here-document into `/tmp/x` is read as before.
- Whether other programs and flags join §1 and §2's tables; each is a later record.

## What the implementation changes

1. `.claude/hooks/private-paths-guard.mjs`, drafted as `docs/agents/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator (ADR-215 §7): `bashSegments`, `plainValue`, `LITERAL`, `TEXT_FLAGS`, `OPERAND_PROGRAMS`, `longNames`, `textSpans`, `inTempFolder` and `textsRead`; `pathsInCommand` takes the command as written for `gitIndexForms`; `refusalsOf` reads the blanked texts; and the header comment.
2. `src/test/hooks/private-paths-guard.test.mjs`: §5's cases and its header comment, written before the guard.
3. ADR-196 and ADR-215 take a pointer to this record at their top. Their decisions are not edited.
4. `docs/agents/private-paths-guard.md`: what the hook reads in a shell command, and the list of the records that amend ADR-196.
