# ADR-233 — The private-paths guard reads what stands beside the name `.claude`, a leading `~` in a field, and a link out of its own folder

- **Date**: 2026-10-10
- **Status**: accepted.
- **Amends**: [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md), in these places and no others:
  - §3(c), "what stands before `.claude` in a name": a special parameter of the shell (`$*`, `$@`, `$#`, `$?`, `$-`, `$$`, `$!`, and PowerShell's `$^`) and the start of a variable with a word for when it has no value (`${NAME:-` and its kin) are added to what may stand there (§1 below);
  - §3(c), the name after `.claude`: an expansion that may be empty, joined to it, is taken off as a stream name is (§2 below);
  - §3(c)'s "the first name of a token headed by `-`": a token headed by `/` is read the same way, since a Windows program's option runs into its value after a slash (§3 below);
  - §8's item on what a token that is not read as a path passes, which §1 and §2 narrow;
  - §5's links, which gain a link out of the `.claude` folder the guard lies in (§5 below);
  - §7's "a change to the guard, a list, the settings or an agent definition is drafted outside every `.claude` folder": the test that holds the draft is held to the guard's own reading of a `.claude` name (§7 below).
- **Amends**: [ADR-217](0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md) §2, "a name made only of dots, spaces or a stream name": a name of dots with a stream name after it is such a name, and is not read as `.` or `..`; and §1's stream name, which runs to the end of the name, a line terminator in it included (§6 below).
- **Keeps**: ADR-196's rule (§1), its allow list, its "decision is made from the path's text, before the file system is asked" (§2) wherever the file system need not be asked, its bounds and every way §6 makes the hook fail closed; ADR-201 whole; ADR-215's rule (§1), its three checks and its draft-and-install (§7); ADR-217's reading as Windows opens its names; and ADR-212's one admitted command.

## Context

Seven readings of a `.claude` path let a write through the guard, and the operator documents hold a stale sentence the guard now refuses. ADR-215 §3(c) reads the text of a token for a closed `.claude` folder "whether or not the token is read as a path", so that a form ADR-196 §5 leaves unread does not carry a write to the guard's own files. That reading is short in five places, and ADR-217's reading of a Windows name short in one.

### Measured

**On Linux**, with synthetic fixtures only.

- Against the guard before this record: `echo x > $*.claude/settings.json` ended with exit 0, a write let through. `$*` is empty in a `bash -c`, so the token's text is `.claude/settings.json`; ADR-215 §3(c)'s `BEFORE_CLAUDE` did not accept the `$*` before `.claude`, and `readToken` drops a token headed by `$`, so neither rule saw the path. The same held for `$!`, `$@`, `$#`, `$?`, `$-`, `$$`, PowerShell's `$^`, and `${X:-.claude}` in a path from the root.
- `echo x > .claude$X/settings.json`, with `$X` unset, ended with exit 0: the empty expansion after the name hid `.claude` from `CLAUDE_IN_TEXT`, which read `.claude$x` and found no `.claude` name.
- `Read` of `~/.claude/settings.json` and of `~/Documents/x.txt` ended with exit 0: `absoluteOf` resolved a leading `~` in a field against the current directory, where `readToken` reads it as the home folder in a shell command.
- `Write` of `linked-skill/SKILL.md` through `.claude/skills/linked-skill`, a link into `.opencode/skills/` as this repository's `.gitignore` describes, ended with exit 0: the guard does not read a link out of its own `.claude` folder, and a later session loads the skill the link hands it.
- Of the 39 I cases this record adds, 5 are not started on Linux.

**Traced through the code, not executed on Windows** (ADR-217's precedent, the operator's machine being Windows):

- `asOpened("..:x")` gave `".."` and `asOpened(".:x")` gave `"."`, so `openedByWindows` folded a name of dots with a stream name after it as a parent climb or the folder itself, where its comment and ADR-217 §2 say it returns `null` and the call is refused. `asOpened("...:x")` already gave `""`.
- `isClaudeFolder` and `asOpened` took a stream name off with `/:.*$/`, whose `.` does not cross a line terminator and whose `$` is the end of the string, so `".claude:x\ny"` kept its suffix and was not seen as `.claude`.

### Read, and not executed

- POSIX shells: a special parameter is `$` and one of `* @ # ? - $ !`, and PowerShell's `$^` and `$$` are the first and last token of the last line. Each is empty or a short word in a shell started for one command, and joins to a name after it. `${NAME:-word}`, `${NAME-word}`, `${NAME=word}` and `${NAME+word}` substitute `word` when the name is unset (or, with the colon, unset or empty); the word begins right after the sign.
- A Windows program reads an option written with a leading `/`, `/Fe<file>` and the like, as `-` options are read on every platform: the letters run into the value with no separator.

## Decision

### 1. What may stand before the name `.claude` in a token's text

ADR-215 §3(c) lets `.claude` be the folder when nothing stands before it in the name, or what stands there ends in `=`, `:`, `,`, `{`, `}`, `@` or a variable. **Two more are added**, each a thing a shell leaves empty or short before the name, so that a token the guard does not read as a path still names the folder:

- **A special parameter**: `$` and one character that is neither a name's (a letter, a digit or `_`) nor a brace. That is `$*`, `$@`, `$#`, `$?`, `$-`, `$$`, `$!` and PowerShell's `$^`. `readToken` drops a token headed by `$` (ADR-196 §5), so `$*.claude/settings.json` is read by its text alone.
- **The start of a variable with a word for when it has no value**: `${NAME:-`, `${NAME-`, `${NAME=`, `${NAME+`, `${NAME?}` and each with the colon. The word is the name, as in `${X:-.claude}` and `${X-.claude/settings.json}`. In a Bash command `variantsOf` reads the word in the variable's place as a path; this closes it in the text too, and on every platform, since the text asks the file system nothing. A word that only ends in `.claude`, as `${X:-notes.claude}`, is not the folder, as `notes.claude` is not.

### 2. What may follow the name `.claude`: an expansion that may be empty

`CLAUDE_IN_TEXT` took off a stream name and sentence punctuation after `.claude`. **It now takes off an expansion joined to the name as well**: a `$` and whatever follows it, or `%NAME%`, since each may be empty when the command runs and leave the name touching the next separator. So `.claude$X/settings.json`, `.claude${X}/settings.json`, `.claude$(…)/settings.json`, `.claude$*/settings.json` and PowerShell's `.claude$env:X\settings.json` each name the folder. `.claude$X/worktrees/wt/README.md` is read on from `worktrees` and is open, as `.claude/worktrees/wt/README.md` is.

This is a reading of the token's text and nothing more; a token the guard does read as a path has its variable replaced by its value already (ADR-196 §4), and this changes nothing there.

### 3. A token headed by `/` is an option as one headed by `-` is

ADR-215 §3(c) takes the first name of a token headed by `-` for `.claude` whatever stands before it, because an option's letters run into its value. **A token headed by `/` is read the same way**, since a Windows program's option is written with a leading slash and its letters run into the value too: `cl /Fe.claude/hooks/private-paths-guard.mjs x.c` and `/Fe.claude` are refused. It holds for the first name of the token alone, which still has to end in `.claude`, so `/Fo.claude-out/x.obj` is not the folder.

### 4. A leading `~` in a file tool's field is the home folder

`readToken` reads a leading `~`, alone or before a separator, as the home folder in a shell command. **`absoluteOf` and `walkedOf` now read it so in a file tool's field too**, so a `Read`, `Write`, `Edit`, `NotebookEdit`, `Grep` or `Glob` of `~/.claude/settings.json` or `~/Documents/x.txt` is judged at the home folder and refused, where it was resolved against the current directory and let through. `~/.m2/settings.xml` and `~/.claude/projects/…/memory/…` stay allowed, as they are in a shell command. `~name/x` is not the home folder, as ADR-196 §5 has it for a shell command.

### 5. A link out of the guard's own `.claude` folder is closed as that folder is

ADR-215 §5 closes a link **into** a `.claude` folder. The other direction is open: a link **inside** the checkout's `.claude` that leads **out** to an ordinary folder, which a write then edits. This repository's `.gitignore` says `.claude/skills/<skill>` are symlinks an installer makes into `.opencode/skills/`, and a later session loads a skill the link hands it. So:

**A place that a link in the `.claude` folder the guard lies in leads to, outside `projects`, `plans` and `worktrees`, is closed as that folder is.** A write to it, by a tool or a shell command, is refused; Read, Grep and Glob are let through, as they are in the folder itself. The links are found by a breadth-first walk of that one folder that passes over `projects`, `plans` and `worktrees` directly beneath a `.claude`, never follows a link, passes over a folder it cannot list and a link whose target it cannot read, and looks at no more than `WALK_LIMIT` names; a folder with more refuses every write, since a link among the rest cannot be ruled out. A link is followed as far as where it leads is there, so a place a link names before it is made is closed too. The walk is made once for a call.

It is **the guard's own folder alone**, the one its file lies in. A link in another `.claude`, or beneath `projects`, `plans` or `worktrees` in this one, or one made after the walk, is §8's, as a link the guard does not reach always was.

### 6. On Windows a name of dots with a stream name, and a stream name with a line terminator

- **A name of dots with a stream name after it is a name made only of dots, spaces and a stream name**, which ADR-217 §2 refuses on Windows. `asOpened` left `..:x` as `..` and `.:x` as `.`, which a path folds as a climb or as the folder itself; it now leaves each with nothing, as it already did `...:x`, so `openedByWindows` returns `null` and the call is refused. `..` and `.` with no stream are folded by ADR-201 §1 before this reading and are not touched.
- **A stream name runs to the end of its name, whatever it holds.** The guard took a stream name off with `/:.*$/`, whose `.` does not cross a line terminator, so a stream name with a `\n` in it kept its suffix and `.claude:x\ny` was not seen as `.claude`. It is taken off with `/:[^]*$/`, which crosses every character.

### 7. The test that holds the draft reads a `.claude` name as the guard does

ADR-215 §7's test refuses a `VESPERA_GUARD_DRAFT` folder that lies in a `.claude` folder. It compared the path's segments to `.claude` literally, so it missed the spellings the guard itself treats as `.claude` on Windows — another case, a dot or space after the name, a stream name — and where a link or an 8.3 name leads. It now folds each segment's case, takes a stream name and the trailing dots and spaces off on Windows, and reads the path as its links lead, so a draft the guard would judge to be in a `.claude` folder is turned away before a run.

### 8. What is removed, and what stays uncovered

- **`within` is removed.** It was defined in the guard and called nowhere (ADR-216: nothing ships that nothing calls). It is in a `.claude` file, so it goes with this change, which re-mints nothing under `src/main`.
- **Still uncovered** (ADR-215 §8, ADR-196 §5): a path a command builds at run time, a program that writes without naming the path, a link the walk of §5 does not reach, and, in a token not read as a path, anything but the literal name — an 8.3 name, a link, a quote or an escape inside the name. None has a case, on ADR-196 §4's ground that a case would hold the gap in place.

## Decided here

1. **§1: a special parameter is `$` and one character that is neither a name's nor a brace.** It takes `$*`, `$!` and their kin, and no `$NAME` or `${…}`, which are read already.
2. **§1: the whole of `${NAME:-word}` before `.claude` is accepted**, where only the sign could have been. Accepting more refuses more.
3. **§2: an expansion is a `$` and all that follows it, or `%NAME%`.** It is taken off the end of the name because it may be empty. A name that ends in a real suffix after the expansion, were the expansion non-empty, is a path the guard cannot read from the text, and §8 leaves it.
4. **§3: a token headed by `/` is an option.** It costs a token that is a rooted POSIX path whose first name ends in `.claude`, which ADR-196 §5 does not read as a path anyway, and which `/x/.claude/…` already closed through §1's rooted form.
5. **§4: a leading `~` in a field is the home folder.** The other choice was to read it against the current directory, which is how the write slipped through.
6. **§5: the guard walks its own `.claude` folder for links, and refuses every write when the folder is too large to walk.** The other choice was to leave the direction uncovered, which lets a write edit a skill a later session loads.
7. **§6: a name of dots with a stream name is refused, and a stream name crosses a line terminator.** Each refuses more and asks the file system nothing.

## Consequences

- **On every platform a shell command is refused when a token's text names a closed `.claude` folder behind a special parameter, an empty expansion or a `/`-option**, and a file tool's `~`-headed field is judged at the home folder.
- **A write to a place a link in the guard's own `.claude` folder leads to is refused.** Read, Grep and Glob are let through.
- **On Windows a name of dots with a stream name, and a stream name with a line terminator, are refused**, which the operator's machine or CI's NTFS job holds; the I cases that rest on Windows are not started on Linux.
- **The guard's test file holds 929 cases**, 39 of them new; the count in ADR-217 §6 and ADR-215 §9 is behind.
- **Nothing under `src/main` changes, and no run id moves.**
- **Some operator documents are corrected**, outside the guard: `AGENTS.md` no longer tells an agent to run `.claude/workflows/settle-and-land.mjs`, which the guard refuses; the agent definitions name the integration tests as ADR-216 §10's approved text has them, not `VesperaApplicationTests`; and `docs/check-claims.mjs`'s comment on "Running it" names two services and two ports, since Chroma was removed (ADR-214).

## What the implementation changes

This record, its cases and the guard land in one change.

1. `.claude/hooks/private-paths-guard.mjs`, drafted outside every `.claude` folder and installed by the operator, as [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §7 has it: `BEFORE_CLAUDE` gains a special parameter and the start of a variable with a word (§1); `CLAUDE_IN_TEXT` takes an expansion off the name (§2); a token headed by `/` is an option (§3); `fromHome` reads a leading `~` in a field (§4); `placesLinkedFromClosed` walks the guard's own `.claude` for links and `check` refuses a write into one (§5); `asOpened` leaves a name of dots with a stream name empty and `STREAM` crosses a line terminator (§6); `within` is removed (§8); and its header comment.
2. `src/test/hooks/private-paths-guard.test.mjs`: the I cases, the fixture link into `.opencode/skills`, and `VESPERA_GUARD_DRAFT`'s `.claude` check read as a name (§7); and its header comment.
3. `AGENTS.md`, the agent definitions and `docs/check-claims.mjs`, as Consequences says. The agent definitions are drafted outside every `.claude` folder and installed by the operator (ADR-215 §7, ADR-216 §10).
4. `docs/adr/README.md` and `src/test/java/io/algernon/vespera/Adr.java` take this record. ADR-215 and ADR-217 take a pointer to it at their top; their decisions are not edited.
