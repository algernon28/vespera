# ADR-215 — No agent writes into a `.claude` folder, and the private-paths guard closes each one but for what it names

> **Partly amended — see [ADR-217](0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md).** §8's item "A `read` line beneath a plain line, where the folder is written with a dot after its name" is closed on Windows, the one platform where it arises, and the first item of "What this does not decide" is decided there. Everything else in this record stands.

- **Date**: 2026-10-09
- **Status**: accepted. The guard, the shipped allow list and this record land in one change.
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), in these places and no others:
  - §2's first bullet, "four folders under the home folder": the home folder's `.claude` is no longer one of them, and §3 below says what stands in its place;
  - §2, which judges a path the same way whichever tool names it: two kinds of place are now read and not written (§4 below);
  - §4, which gains one reading of a shell command's text, made of every token whether or not it is read as a path (§3(c) below);
  - §5's list of what the hook does not cover, which gains §8 below.

  ADR-196's rule (§1), its working-directory refusal, its bounds, every way §6 makes the hook fail closed, and all of [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md) stand as written.
- **Settles**: [#459](https://github.com/algernon28/vespera/issues/459).
- **Amends**: [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), which landed while this record was being built, in these places and no others. §10 below says what replaces each.
  - §4.4, "inside the allow list and outside every working directory": the ordinary check of the counting command's current directory now also turns down a closed `.claude` folder;
  - §4's "without the guard reading its command for paths" and its "Any call that fails one of these is read as before": the admission now also stands before the reading of every token's text (§3(c) below), and a call that fails it is read with that reading too;
  - §5's "That is ADR-196's position, unchanged", and the three items of its "What the pin does not cover": the third, that `~/.claude/settings.json` can be written, is closed for a call whose text names the file, and the first two are narrowed and not closed. ADR-212 left that third item "to be added to ADR-196 §5 by a later record", and this is that record.
- **Keeps**: the rest of ADR-212: the one command it admits, the counting script and what it prints, the pin by hash, and the expectation of every K case as ADR-212 wrote it.

## Context

ADR-196's allow list named `${HOME}/.claude`, because an agent keeps its memory there. The guard judged a path against the list and made no difference between a read and a write, so every file under that folder could be written, `settings.json` among them. That file can set `disableAllHooks`, drop the guard's entry, or set `env` for every later session, for instance `NODE_OPTIONS=--import …`, which loads code into every `node` a session starts, the guard included. Any of these undoes the guard for every session after it, and no archive or working directory has to be touched first.

**The same was true of the checkout.** `${REPO}` is on the list, and these files under it decide whether the guard runs and what it admits:

- `.claude/settings.json`, which registers the hook, and `.claude/settings.local.json`, which Claude Code reads as settings too;
- `.claude/hooks/`, which holds the guard and the script that starts it;
- `.claude/allowed-paths.txt`, and `.claude/allowed-paths.local.txt`, which the guard reads from its own `.claude` folder. An agent that added an archive's drive to that last file had the shortest way round of all, and the file is gitignored, so no review sees it.

Each worktree under `.claude/worktrees/<name>/` carries its own copy of all of these, and the main checkout's copy is what the registered command falls back to (ADR-196 §6).

**Claude Code has a rule of its own for these folders, and it is not a refusal.** Its documentation, read on 2026-10-09 and not executed here, calls `.claude` a protected path whose writes "are never auto-approved, except in `bypassPermissions` mode", where they are "Allowed"; in `auto` mode they are "Routed to the classifier". It excepts `.claude/worktrees/`, the session's own plan files in `~/.claude/plans/`, and Markdown files in the memory directory `~/.claude/projects/<project>/memory/`. So what Claude Code does depends on the permission mode a session is in, and a subagent can be given another. ADR-196's hook exists because a rule that depends on the session was not enough.

### Measured

On the operator's machine, Windows 11, Node 22.23.1, 2026-10-09, with synthetic fixtures only. No folder of the operator's was listed or read: nothing below rests on what the operator's own `~/.claude` holds.

- **The guard before this build held all 278 of its cases**, in 39 s.
- **The guard before this build let through every call this record refuses.** Of the first 407 cases §9 adds, 316 failed against it, each one a call it let through, and 91 passed. Among the 316: `Write` of `~/.claude/settings.json`; `echo x >> $HOME/.claude/settings.json`; `Write` of the checkout's `.claude/settings.json`, of the guard, and of `.claude/allowed-paths.local.txt`.
- **A name written with a dot after it is another name to Node and the same name to PowerShell.** `Get-Content '.claude.\settings.json'` and `Get-Content '.claude\settings.json.'` both printed the fixture file, and `Set-Content '.claude.\written.json'` wrote `written.json` into `.claude`. Node's `lstat` answered `ENOENT` for both spellings, and so did Git Bash's `cat`. The guard is a Node script, so it cannot learn where such a name leads by asking.
- **A space after a file's name is taken off by PowerShell, and one after `.claude` was not.** `Get-Content '.claude/settings.json '` printed the file. `Get-Content '.claude \settings.json'` found nothing.
- **A stream name leads to the file.** `realpath` of `.claude/settings.json::$DATA` and of `.claude::$INDEX_ALLOCATION/settings.json` is `.claude\settings.json`, and so is that of `.CLAUDE/SETTINGS.JSON`.
- **An 8.3 name leads to the folder.** The fixture's `.claude` was given `CLAUDE~1`. `realpath` of `CLAUDE~1/settings.json` is `.claude\settings.json`, and a file not yet there beneath `CLAUDE~1` is answered from `CLAUDE~1` itself, whose real path is `.claude`. Q310 to Q312 hold both.
- **§3(c)'s rule in its first form refused an everyday command.** Read as "a name that ends in `.claude`", it refused `git log main..claude/private-paths-guard`, which is ADR-196's case B14: this repository's branches are named `claude/…`, and a range puts dots before one. In the form §3(c) states, a throwaway copy of the guard with the rule added held all 685 cases, so the rule refuses nothing the suite's allowed cases need: a command run with a worktree as its current directory, `git -C` naming a worktree, a URL and a commit message that name `.claude/worktrees/…`.
- **Found on the way, and not decided here**: the guard lets `Get-Content wd./report.html` through when `wd` holds `vespera.db`, and PowerShell prints the report. That is ADR-196's working-directory check, read with a dot after the folder's name. It is not this ticket's and needs a record of its own (see "What this does not decide").

### Read, and not executed

Claude Code's documentation of hooks, read on 2026-10-09:

- "All matching hooks run in parallel." Where they disagree, "precedence is `deny` > `defer` > `ask` > `allow`", and exit 2 blocks "whether or not you print JSON: even a JSON `permissionDecision` of `"allow"` can't override it". So a second hook cannot overrule the guard's refusal.
- A `PreToolUse` hook may return `updatedInput`, which "replaces a tool's arguments before it runs". Whether another hook is shown the replaced arguments is not stated. **So a second hook may be able to give a tool a path the guard never judged**, and that cannot be ruled out from the text.
- Hooks are registered in settings files, in a plugin's `hooks/hooks.json`, and "directly in skills and subagents using frontmatter". So an agent definition and a skill can each register one.
- `"disableAllHooks": true` in a settings file turns every hook off, and `--settings '{"disableAllHooks": true}'` does it for one run.

### Not measured

- What Claude Code reads from `~/.claude/projects/` besides memory, transcripts and tool results. Its documentation names nothing there that configures a session.
- Whether any session here uses plan mode, and so whether `~/.claude/plans/` is ever written.
- Whether a skill used here starts a script of its own by its path under `~/.claude/skills` or `~/.claude/plugins`.
- Which `node`, `bash` and `git` the registered command finds on `PATH`.

## Decision

### 1. The rule

**No agent writes into a `.claude` folder**: not the home folder's, not a checkout's, not a worktree's. What is in one decides how a later session runs, and whether this guard runs in it at all. Those files are the operator's.

Three names in such a folder are not that and stay an agent's to write beneath (§2). Everything else an agent changes only by the way §7 gives.

The rule binds every agent and subagent, as ADR-196 §1 does, and it is the whole of the protection wherever §8 says the hook does not reach.

### 2. What stays writable, and why

| Place | Who writes there | Why it is open |
| --- | --- | --- |
| `~/.claude/projects/` | an agent, with Write and Edit, in `<project>/memory/`; Claude Code itself, for a session's transcript and its tool results | The memory directory is how an agent carries what it learned from one session to the next. The transcript and the tool results are the session's own, and an agent is told to Read a tool result that was too long to show. |
| `~/.claude/plans/` | an agent in plan mode | Claude Code keeps a session's plan there, and a plan it cannot write is a mode that does not work. |
| `<checkout>/.claude/worktrees/` | every agent that works in a worktree | Each folder beneath it is a checkout. Its sources are written like any other's, and its own `.claude` is closed in turn. |

**`projects` is open whole, and not `memory` alone.** The allow list has no wildcard, and each checkout and each worktree has a project folder of its own there, named after its path. A list of memory directories would have to be kept by hand for every worktree.

**The three names are open beneath every `.claude` folder, so a checkout's `.claude/projects/` and `.claude/plans/` are writable too**, and so would `~/.claude/worktrees/` be if the allow list named it. The guard keeps one list of open names and does not ask which `.claude` it is in: telling the home folder's from a checkout's means comparing against the home folder by its text and by where its links lead, and a rule that is wrong about which folder it is in is wrong in the direction of opening one. It does not matter because Claude Code's documentation names nothing it reads from `projects` or `plans` in a checkout's `.claude`: there they are two folders with nothing in them that a session obeys. M114 to M116 hold that they are open.

**Memory is not harmless, and it is still open.** `MEMORY.md` is read at the start of every later session, so what is written there reaches them. It registers no hook, removes none, and sets no variable. The issue asks for it to stay writable, and it does.

**Two places are read and not written**: `~/.claude/skills/` and `~/.claude/plugins/`. This project enables a plugin of skills in `.claude/settings.json`, and a skill's own files are opened with Read. Neither is written by an agent: a plugin's `hooks/hooks.json` and a skill's frontmatter each register hooks.

**Everything in a checkout's `.claude` can still be read** with Read, Grep and Glob. It is this repository's own text, and the guard has to be read to be worked on.

### 3. How the guard tells them apart: three checks, and a call must pass each

**(a) The allow list is narrowed.** `${HOME}/.claude` has left `.claude/allowed-paths.txt`. Four lines take its place:

```
${HOME}/.claude/projects
${HOME}/.claude/plans
read ${HOME}/.claude/skills
read ${HOME}/.claude/plugins
```

**A line headed by the word `read` and one space names a place for reading.** Read, Grep and Glob are let through beneath it. Edit, Write and NotebookEdit are refused, and so is a Bash or PowerShell command that names a path beneath it (§4). Where a path lies beneath both a `read` line and a plain one, the `read` line decides. Both lists take such lines, the shipped one and `.claude/allowed-paths.local.txt`.

**A line that is the word `read` alone, or `read` and only white space after it, names no place and admits nothing.** Read as a plain line it would be the folder named `read` in whatever folder the guard's process was started in, open to every tool. X01 to X04 hold it.

Everything else under `~/.claude` is now outside the allow list, and is refused as ADR-196 §2 refuses any path outside it: to all eight tools, for reading as for writing.

**(b) A `.claude` folder is closed, whatever the list says.** A path is closed when one of its folders is named `.claude` and that name is the last of the path, or is followed by any name but `projects`, `plans` or `worktrees`. The path is read on from there, so a `.claude` further down closes what is beneath it: `.claude/worktrees/wt/src/Example.java` is open and `.claude/worktrees/wt/.claude/settings.json` is closed. A closed path is not written: §4 says what that means for each tool.

- It holds for **every folder named `.claude`** under an allowed root, a checkout's or not. The guard does not ask what kind of folder it is in.
- It is judged on **every reading of a path that ADR-201 makes**: its text with `..` folded, the path as the file system walks it, and where its links lead. Any of them closed is closed.
- **Names are compared with their case folded**, as the guard compares every path. **On Windows a name is compared with the dots and spaces that end it taken off**, because PowerShell opens `.claude.` as `.claude` and Node does not (Measured), and with a stream name after a colon taken off the name `.claude`. `.` and `..` are not names and are left as they are.
- **The three open names are written in the guard, not in a list.** No line of either allow list opens a closed path.

**(c) A shell command's text is read for the name itself, in every token.** (b) is judged on paths, and ADR-196 §5 lists tokens the guard does not read as a path at all. Four of them can spell a closed path letter for letter: a token headed by two variables in a row, as in `$A$B/.claude/settings.json`; a token headed by a variable that has no value in the hook's environment; an option with its value attached, `-o.claude/settings.json`; and a path from the root with no drive, `/x/.claude/settings.json`, on Windows under Git Bash as elsewhere. So a Bash or PowerShell command is refused when any token of its text, as ADR-196 §4 cuts it at white space, quotes and `;`, `|`, `&`, `<`, `>`, `(` and `)`, names a closed `.claude` folder by the text alone:

- The token is cut at `/` and `\`, and `..` is folded against the name before it.
- **A name of it is `.claude`** when it ends in `.claude`, with its case folded and with any dots, spaces, stream name and sentence punctuation after that taken off, and what stands before `.claude` in that name is nothing, or ends in one of `=`, `:`, `,`, `{`, `}` and `@`, or ends in a variable (`$NAME`, `${NAME}`, `$env:NAME`, `%NAME%`). So `--file=.claude`, `HEAD:.claude`, `$X.claude`, `$env:X.claude` and `%USERPROFILE%\.claude` are each that name, and `main..claude`, `notes.claude`, `.claudeignore` and `.claude.json` are not.
- **In a token headed by `-`, the first name is `.claude` whatever stands before it.** An option's letters run straight into its value, `-o.claude` and `-I.claude`, and the text does not say where the letters stop. So `--author=someone.claude` and `--x=notes.claude/y` are refused, though each names something else. It is the one place a name that only ends in `.claude` is taken for it, it holds for the first name of the token alone, and V117 and V118 hold it on purpose.
- **The command is refused** when such a name is the last of the token or is followed by any name but `projects`, `plans` or `worktrees`. That next name is read with its case folded. **Sentence punctuation is taken off it only where it is the last name of the token**, since punctuation ends a sentence and a token and never stands inside a path; anywhere else it is compared as (b) compares it, with the dots and spaces that end it taken off on Windows and nowhere else. So `.claude/worktrees, and more` is open, and `.claude/worktrees,/x` is a folder that is not `worktrees` and is closed. The token is read on from an open name, as in (b).

Whatever heads the token, and whether or not any other rule reads it as a path, makes no difference. A URL and a revision joined to a path by a colon are tokens like any other. This reading is made beside the others and removes none of them.

**(c) is never the more lenient of the two, except on the last name of a token.** Wherever (b) closes a path, (c) closes the token that spells it, with that one exception; the guard compares the name after `.claude` in one place for both. **The exception is on purpose**: sentence punctuation is taken off the last name of a token, so `.claude/worktrees,` at the end of a token is `worktrees` to (c), and to (b) it is a folder named `worktrees,`, which is closed. What it lets through is a token, not read as a path, whose last name is one of the three open names with punctuation after it: the folder so named is a sibling of the open one, nothing Claude Code is documented to read is in it, and no settings file, hook or allow list can be spelled that way, since each of those is a further name and the punctuation is then not at the token's end. Without it, a sentence that ends in an open name is refused. Y151 and Y152 hold the two outcomes.

**(c) refuses more than (b) in these places**, each on purpose, since a token's text does not say where a name begins or ends:

- sentence punctuation after the name `.claude` itself, as in `.claude,/x`, which is another folder to (b) (Y141, Y142);
- a dot after `.claude` on a system that is not Windows, where it is another folder (Y111, Y112);
- a stream name after `.claude` on a system that is not Windows, where a colon is a character of a name (Y121, Y122);
- the first name of an option, whatever stands before `.claude` in it (V117, V118);
- whatever (c) lets stand before `.claude` in a name: `=`, `:`, `,`, `{`, `}`, `@` and a variable. To (b) a name such as `example:.claude` or `x}.claude` is not the folder (V103, V107, T115).

Y011 to Y152 give the same path to Edit and to a token that is not read as a path, and claim the same outcome of both but where this paragraph and the one above say otherwise.

**(c) sees a literal spelling and nothing else.** It asks the file system nothing, so in a token that is not read as a path it does not know an 8.3 name of a `.claude` folder or a link that leads to one, which (b) follows; and it reads the token as written, so a quote or an escape inside the name hides it (§8).

Checks (b) and (c) ask the file system nothing: they read text the guard already has. ADR-196 §2's "decision is made from the path's text, before the file system is asked" stands.

**Why it fails closed.**

- **A new kind of file under `~/.claude` is refused without being named**, to every tool, because nothing admits it. This is why (a) is a narrower allow list and not a list of files to refuse: a list of `settings.json`, `hooks` and the rest is behind on the day Claude Code reads a new file.
- **A new kind of file in a checkout's `.claude` is closed without being named**, because it is not one of three names. A deny list was the other choice there, since `${REPO}` has to stay on the allow list whole; (b) is the form of it that names what is open and not what is closed.
- **A list that is wrong opens no settings to a write.** If a line names `${HOME}/.claude` whole again, in the local list or by a mistake in the shipped one, (b) and (c) still refuse Edit, Write, NotebookEdit and a shell command for `settings.json`, `hooks` and every unnamed thing there. W01 and W02 hold that. **It does open them to Read, Grep and Glob**: what a `.claude` folder closes is writing, and reading under `~/.claude` is refused by the allow list alone. A local line is the operator's own, and W08 and W09 hold what it does.
- **An older guard that meets a newer list refuses.** It reads `read ${HOME}/.claude/skills` as one path that is nowhere, so nothing is admitted by a line it does not understand.
- **A spelling the guard does not know is closed.** `settings.json.`, `settings.json::$DATA` and `Settings.JSON` are none of the three open names.

### 4. What is refused, by tool

A shell command's text does not say whether it writes. ADR-196 §4 keeps no list of verbs, "a list is dodged by the verb it leaves out", and that holds here: `rm`, `mv`, `cp`, `tee`, `sed -i`, a redirect and `git checkout --` all write. So a path that is read and not written is refused to a shell command outright.

| A path that is… | Read, Grep, Glob | Edit, Write, NotebookEdit | Bash, PowerShell |
| --- | --- | --- | --- |
| under `~/.claude` and outside the allow list (`settings.json`, `settings.local.json`, `CLAUDE.md`, `keybindings.json`, `.credentials.json`, `hooks/`, `agents/`, `commands/`, `rules/`, `output-styles/`, anything unnamed, and `~/.claude` itself) | refused | refused | refused |
| beneath a `read` line (`~/.claude/skills/`, `~/.claude/plugins/`) | let through | refused | refused |
| closed by §3(b) under an allowed root (a checkout's `.claude/settings.json`, `.claude/settings.local.json`, `.claude/hooks/`, `.claude/allowed-paths.txt`, `.claude/allowed-paths.local.txt`, `.claude/agents/`, `.claude/workflows/`, `.claude/skills/`, anything unnamed, the same in a worktree, and the `.claude` folder itself) | let through | refused | refused |
| beneath `projects`, `plans` or `worktrees` and not closed further down | let through | let through | let through |

**Reading is refused under `~/.claude` because no agent needs it there.** `settings.json` and `.credentials.json` are not an agent's to read, and refusing the whole folder is what refuses a new file without naming it.

**For a shell command, "names" is every place ADR-196 §3 and §4 read a path, and every token by §3(c)**: a token in any form §4 reads, the current directory, a folder the command names that a relative path is read against, and the text of any token at all. So `ls` with `.claude/hooks` as its current directory is refused, and so are `cd .claude && rm settings.json` and `cat $A$B/.claude/settings.json`. A Grep or Glob reads, so its `path` and its current directory are judged for reading.

**`agents`, `workflows` and `skills` in a checkout's `.claude` are closed too.** An agent definition and a skill each may register a hook in frontmatter, and a hook may replace a tool's arguments; whether the guard is shown the replaced arguments is not stated (Read, and not executed). `.claude/workflows/settle-and-land.mjs` starts sessions, and the options it starts them with decide which settings they read. None of the three can be ruled out as a way to reconfigure what the guard sees, so none is open.

### 5. Links and other names for a closed place

**A link from a place that is not closed to one that is refuses**, as ADR-201 §1 and §2 have it for the allowed roots. §3(b) is judged on where a path's links lead, so a junction or symlink in the temp folder, in the memory directory, beneath `worktrees` or inside a worktree that leads to a `.claude` folder is that folder. A link to `~/.claude` is refused by ADR-201 already, now that `~/.claude` is outside the list. A path that climbs with `..` out of a link is judged as the file system walks it, so `links/to-hooks/../settings.json` is the settings beside `hooks`.

**A link that leads to an open place is let through**: a Write into the memory directory through a link under the temp folder is a Write into the memory directory.

**An 8.3 name of a `.claude` folder is that folder wherever the guard reads a path**, for a file that is there and for one to be created: the guard follows the name, or the nearest folder above it that is there, to its long name (Measured). In a token of a shell command that is not read as a path, nothing is followed (§8).

**A name that is there and cannot be followed is refused**, as ADR-201 §2 has it. Nothing changes there.

### 6. `env` in settings, and `NODE_OPTIONS`

**Refusing the files is the whole of what this record does about it.** A variable reaches the guard's process from three places: the environment Claude Code was started in, which is the operator's; `env` in a settings file; and nothing else an agent can write through the eight tools. The settings files are `~/.claude/settings.json`, which §3(a) puts outside the allow list, and a checkout's `.claude/settings.json` and `.claude/settings.local.json`, which §3(b) closes. A variable set in a command's own text, `NODE_OPTIONS=… node x`, is set for that command and not for the hook, which is a separate process started by Claude Code.

**The script that starts the guard is not changed.** It could clear `NODE_OPTIONS` before it starts `node`. Whoever can set that variable through a settings file can also remove the hook from the same file, so clearing it closes nothing that §3 leaves open, and `BASH_ENV` would run before the script reaches the line that clears.

**ADR-212's second bullet stands**: which `node` runs, the order of `PATH`, and a shell alias are not in a command's text.

### 7. How these files are changed now that the rule holds

They are still maintained, and mostly by agents. **An agent writes the change as a draft outside every `.claude` folder, and the operator puts it in place.**

1. The agent keeps a draft folder that is laid out as `.claude` is: `hooks/private-paths-guard.mjs`, `hooks/run-private-paths-guard.sh`, `allowed-paths.txt`, `settings.json`, or whichever of them changes. It lies where the guard lets an agent write and is not named `.claude`: under the temp folder, or under `target/`.
2. For the guard, the test is started against the draft: `VESPERA_GUARD_DRAFT=<folder> node --test src/test/hooks/private-paths-guard.test.mjs`. Every case is then held against the draft's files, and a file the draft does not hold is taken from the checkout. A folder that is not there stops the run, and so does one that holds none of the four files, so neither a mistyped name nor an empty folder ends in a green run of what is already installed. P01 and P02 always start the checkout's own wrapper.
3. The agent hands the operator the copy command, as text to paste, and what it changes. The operator runs it in a shell of their own.
4. The test is started again with no variable, which holds what is installed, and the change is committed. `git add -A` and `git commit -a` name no path; `git add .claude/hooks/…` does, and is refused.

**What this buys beside the rule**: the guard that is live is never half-written. A syntax error in `.claude/hooks/private-paths-guard.mjs` refuses every later tool call of every session rooted in that checkout, the one that made the error included, because the hook fails closed.

**The switch is in the test, and lowers nothing.** ADR-196 §3 keeps no switch that lowers a bound for a test, "because such a switch would be a way round it". This one says which files are held, changes no bound and no verdict, and is read by no code under `.claude`. CI sets no such variable, so what is committed is what CI holds.

**This record's own build was drafted this way**, gated as a draft, and installed by the operator.

A worktree gets a changed guard as it gets any other change, by merging the branch that carries it. `git` names no path when it does.

### 8. What the hook does not cover, added to ADR-196 §5

**The acceptance of #459 is met for a file tool's call, and for a shell command one of whose tokens spells the closed path in its text.** That is wider than "in a form ADR-196 §4 reads": §3(c) reads the four forms §5 leaves unread. It is no wider than the text. For each of these the written rule of §1 is all there is.

- **A path a command builds at run time**, which is ADR-196 §5's item and reaches these files as it reaches any other: `node -e` with a path joined from `os.homedir()`, a variable set in the same command whose value holds the name, a script that computes it. **So the hook does not make it impossible for a shell command to switch the guard off.** It refuses the commands whose text says they do.
- **In a token that is not read as a path, anything but the literal name.** §3(c) reads text and asks the file system nothing, so there it is passed by whatever makes the shell or the file system arrive at `.claude` from other letters: a quote or an escape inside the name, of which `.cla""ude`, `.cl\aude` and PowerShell's backtick are three; a wildcard, `.clau*/settings.json`; a brace list with text around it, `.claude/worktrees/{a,../hooks}`, which ADR-196 §5 lists; an 8.3 name of a `.claude` folder; and a link with another name that leads to a closed folder. The last two are refused where the token is read as a path (§5). None of these has a case: ADR-196 §4 gives the reason, "a case would hold the gap in place".
- **A program that writes these files without naming them**: `git checkout`, `git merge`, `git stash pop`, `git apply`, `git reset --hard`, `git clean` and `git worktree add`; `rm -rf`, `mv` or `cp` of a folder above a `.claude`; a build or a test that writes there.
- **A session an agent starts itself.** `claude -p` with `--settings` or `--setting-sources`, or started in a folder whose settings register no hook, never runs this guard. `.claude/workflows/settle-and-land.mjs` starts sessions.
- **Tools outside the eight**, as before, and here they can write: an `mcp__*` file tool, and Monitor.
- **Files outside every `.claude` that tell a session what to do**: `CLAUDE.md`, `CLAUDE.local.md` and `AGENTS.md` at the root of a checkout, and `.mcp.json`, which adds tools outside the eight. They remove no hook. `~/.claude.json` is outside the allow list already.
- **A hard link made before the rule held.** It is a second name for the same file and no link to follow.
- **A `read` line beneath a plain line, where the folder is written with a dot after its name.** The text falls under the plain line only, Node says the dotted name is not there, and PowerShell opens the real folder. The shipped list has no such pair; a local list can. Outside a `.claude` folder nothing takes the dot off.
- **Reading under `~/.claude` when a local line names it whole** (§3).
- **The moment between the guard's answer and the tool's write**, in which a link can be put where a folder was.
- **A checkout cut before this record.** Its guard and its list are the old ones, and Claude Code reads that checkout's settings. Until it takes this change in, a session rooted there is held by the written rule alone.
- **What Claude Code writes itself.** It adds to `.claude/settings.local.json` when the operator answers a permission prompt with "always". No tool call does that, and it is the operator's act.
- **The other allowed roots were not read again for this.** `.git/config` and `.git/hooks` in a checkout, `~/.m2`, `~/.jdks`, `~/.config/gh` and the temp folder each hold things a later command runs or reads. None of them registers or removes this hook.

### 9. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 517 cases from this record and holds 841 with the 46 K cases ADR-212 brought: 278 before either, 46 of ADR-212's, and 517 of this record's. The first 407 of the 517 were run against the guard before this build: all 278 cases written before this record passed, 91 of the 407 passed, and 316 failed, each a call that guard let through. Another 98, U, V and Y, were written against the guard as it was being built, to hold §3(c) by execution. The last 12, K231 and J1601 to J1611, hold where this record meets ADR-212 (§10). The guard this record ships with holds all 841, in about 107 s: 108 s and 107 s on two runs alone against the draft of the merged guard, on the operator's machine. The run on the merged tree itself is the operator's.

- **H0101 to H1111**: §3(a). Eleven paths under `~/.claude` that the allow list no longer names, the last a kind of file nobody listed, each through Read, Edit, Write, NotebookEdit, Grep, Glob, a Bash command written with `~`, one with `$HOME`, one with `${HOME}`, one by a relative path that climbs out of the memory directory, and a PowerShell command with `$env:USERPROFILE`. All refused; all 121 were red against the guard before this build.
- **J0101 to J1611**: §3(b) and §4. Sixteen closed paths, the last added for §10 after the counts below were taken: the checkout's two settings files, the guard, the script that starts it, a file beside them that is not there yet, the two allow lists, an agent definition, a workflow, a skill, an unnamed file, three in a worktree's `.claude`, the settings of a `.claude` folder that is no checkout's, and ADR-212's counting script. Each is refused to Edit, Write, NotebookEdit, a Write by a relative path, a Bash command by a relative path, `cd` to its folder and `rm` by its name, a Bash command by its absolute path and a PowerShell command (120 cases, red before this build), and let through for Read, Grep and Glob (45 cases, green before and after).
- **M101 to M116**: §2. The memory directory, a transcript, a tool result, a project folder that is not there yet and a plan stay usable, through Write, Edit, Read, Grep, both shells and as a current directory; and `projects` and `plans` in the checkout's `.claude` are open. Green before and after.
- **M211 to M229**: §3(a)'s `read` lines. A skill's file and a plugin's are let through for Read, Grep and Glob (green before and after) and refused to Edit, Write, NotebookEdit and both shells (12 cases, red before this build).
- **Q101 to Q118**: the `.claude` folder itself and a closed folder in it, as what a shell command names and as its current directory, for a Write by a plain name too; `git add` of the guard by name and a commit message that names the allow list, which are costs held on purpose; the folder a worktree's copy of the guard is in; `.CLAUDE` as a plain name; and `~/.claude` itself. Q106, a Grep from the folder the guard is in, is let through.
- **Q201 to Q208**: a worktree stays writable outside its own `.claude`. Green before and after.
- **Q301 to Q312**: §3(b)'s spellings: another case, a dot after `.claude` and after the file's name, two stream names, a space after `.claude` and after the file's name, and the 8.3 name of `.claude` with the file there and not there. Q310 to Q312 are not started on a volume that gives the fixture no 8.3 name.
- **T101 to T115**: §3(c). Two variables in a row, a variable with no value, bare and in braces, an option with its value attached in Bash's form and PowerShell's, a path from the root with no drive in three spellings, a variable as `cmd` writes it, a variable joined to the name, a climb out of `worktrees`, a `read` place, a URL and a revision joined by a colon. All refused. **T201 to T207**: what that rule leaves usable: the open names in the same forms, a URL and a commit message that name a worktree, other names that begin with `.claude`, a name that only ends in it, and a range of three dots before a branch named `claude/…`. B14 is the same range with two dots.
- **U101 to U129**: what a session here runs every day, all let through: `git status`, `git add -A`, `git commit -F`, a range, a checkout, a push and a refspec with branches named `claude/…`, `git worktree list`, `git worktree add` beneath `.claude/worktrees` on a branch named `claude/…`, `git -C` and `cd` to a worktree with the Maven wrapper after it, the Maven wrapper after `JAVA_HOME` is set to a JDK under `~/.jdks`, `gh pr create`, `gh pr checks` and `gh issue view` with a quoted jq expression, the claims gate, the check of the rendered pages, this test file, a source file deep in a worktree, and files named `.claude.json`, `.claudeignore`, `notes.claude` and `x.claude/y`.
- **V101 to V131**: each spelling §3(c) refuses, in a Bash command and in a PowerShell one where both write it: each thing that may stand before the name, the name as the first of an option and the two option values that only end in it, the name last in a token, a stream name, one dot and two, a space, another case, a climb that lands on it, a quoted string with a space, the read on from `worktrees`, and a folder named `projects` and a comma by both routes. **V201 to V208**: what it lets through: `plans`, an open name before a comma and before a full stop, another case, a climb that leaves `.claude` and one that lands beneath `worktrees`, and a quoted string with a space.
- **Y011 to Y152**: fifteen paths, each given to Edit and to a token that is not read as a path (§3(c)). The last, an open name and a comma at the end, is the one where the token is let through and Edit is refused. On a system that is not Windows three of them claim another outcome, since a dot or a stream name after a name is part of it there: Y111, Y121, and Y131 with Y132. Those claims were written from the rule and not run, the operator's machine being Windows.
- **L401 to L414**: §5. Links from the temp folder, the memory directory, beneath `worktrees` and inside a worktree to a closed place, through Read, Write, Edit and a Bash command, and a climb with `..` out of one. L405 and L409 are let through: a Write into the memory directory through a link, and a Read of the guard through one.
- **W01 to W09**: a checkout whose local allow list names `${HOME}/.claude` whole and holds a `read` line. Settings stay refused to a write, memory stays writable, the `read` line admits Read and Grep and refuses Write and a shell command, and Read of the settings and of the credentials is let through, as §3 says.
- **X01 to X04**: §3(a)'s line that is the word `read` and no path.

**What a gate's list of spellings to try maps to**: another case, Q301 and Q118; a dot, Q302 to Q304; a space, Q307 to Q309; an 8.3 name, Q310 to Q312; a stream, Q305 and Q306; `%USERPROFILE%`, T110; a link from the temp folder, L401 to L403 and L406 to L408, from the memory directory, L404, from beneath `worktrees`, L410, and from inside a worktree, L413 and L414; `cd` into `.claude` and a bare name, J0106 for a name that is there and J0706 for one that is not; a closed current directory, Q104, Q105 and Q116; and `.claude/worktrees/<name>/.claude/hooks`, J1301 to J1308 and Q117.

### 10. Where this meets ADR-212

ADR-212 admits one shell command: `node`, the counting script `.claude/hooks/working-directory-counts.mjs`, and a folder that directly holds `vespera.db`. This record refuses a shell command that names a path in `.claude/hooks`. The two were built apart and landed a day apart, and they compose as follows.

**The admitted command is recognised before any path or text of it is read, and stays admitted.** ADR-212 §4 admits it "without the guard reading its command for paths", and that now reads: without §3(b)'s reading of its paths and without §3(c)'s reading of its tokens. The guard settles ADR-212 §4's form, script word and hash first; then the current directory; then asks whether the folder holds `vespera.db`; and only a command that fails one of those goes on to be read as every other command is. **Nothing else that names `.claude/hooks` is let through**: a command that is the counting command and one thing more fails the form, is read, and is refused twice over, as a path into a working directory and as a path in a closed folder. K201 to K230 claimed the first before this record and still hold.

**The current directory of the counting command passes the ordinary check, and the ordinary check is now this record's.** ADR-212 §4.4 says the current directory is "inside the allow list and outside every working directory". A closed `.claude` folder does not pass it either (§4 above), so the counting command started from `.claude/hooks`, naming the script by its bare name, is refused. Nothing is lost: the command is run from the checkout's root or from the temp folder. K231 holds it.

**No expectation of ADR-212's cases changes.** K101 to K107 are let through and K201 to K230 and K301 to K309 are refused, as ADR-212 wrote them. One thing about them does change: before this record the ordinary reading had nothing to refuse in K102 and K106 on a system that is not Windows, where a rooted working directory is not read; it now refuses the script's own path on every platform, so each of K101 to K107 is let through by the admission and by nothing else.

**The pinned script lies in a closed folder, so no agent edits it.** ADR-212 §5 puts the script in `.claude/hooks`, "so whatever protects the guard from an edit protects the script too", and adds that "an agent that can edit both can admit anything … That is ADR-196's position, unchanged". It is changed now: Edit, Write and NotebookEdit of the script are refused, and so is every shell command that names it but the admitted one. Read, Grep and Glob of it are let through. A change to the script is drafted and installed as §7 has it, together with the guard's constant that pins its hash. J1601 to J1611 hold it.

**Of ADR-212 §5's "What the pin does not cover", this record closes the third item and narrows the other two.**

- **"`~/.claude` is on the allow list, so `~/.claude/settings.json` can be written."** Closed, for a call whose text names the file: `~/.claude` is off the allow list but for four lines (§3(a)), and its settings are closed whatever a list says (§3(b), §3(c)). What §8 lists is what remains.
- **"The moment between the guard's check and `node` reading the script."** Narrowed, not closed. ADR-212 says anything that can write to `.claude/hooks` in that moment can put another script there. An agent's file tools and a shell command that names the folder no longer can. A program that writes there without naming it still can (§8).
- **"Which `node` runs, and how it is started."** Narrowed, not closed. `NODE_OPTIONS` reached every command through `env` in a settings file, and those files are now closed (§6). Still not covered: the order of `PATH`, a shell alias or function named `node`, a variable in the environment Claude Code was started in, and the script that starts the guard, which does not clear `NODE_OPTIONS` (§6).

ADR-212's §5 says that `~/.claude/settings.json` can be written and that an agent that can edit the guard can admit anything. It is a settled record and its decision is not edited; it takes a pointer to this one at its top, as ADR-196 does. One note of its §8 on which reading lets K102 and K106 through on Linux is corrected in place, citing #459, as the index's rule has it for a note on the tests that hold a record.

## Decided here, open to the operator's overruling

1. **§7: a draft that the operator installs.** The other ways are in Alternatives: a variable the operator sets when starting a session, and a permission prompt for each write.
2. **§4: `agents`, `workflows` and `skills` in a checkout's `.claude` are closed**, on what the documentation does not state. Opening them is three more names in §3(b).
3. **§4: a closed path is refused to a shell command even where the command only reads**, so `git add .claude/hooks/x`, `git diff -- .claude/`, `cat .claude/settings.json` and a commit message that quotes such a path are refused. Read, `git add -A` and `git commit -F` are what is used instead.
4. **§3(c): a shell command is refused for a token whose text spells a closed path, whether or not the token is read as a path.** The other choice was to name the four forms in §8 as not covered. It was taken because decision 3 already refuses a command that so much as quotes such a path, and this is what makes that true of every token. It costs a URL and `git show <revision>:<path>` that name a closed path.
   **Within it: the first name of an option is `.claude` whatever stands before it**, so `--author=someone.claude` is refused. The other choice was to take only an option's letters before the name, which lets that through and still cannot tell `-Inotes.claude` from `-I` and `notes.claude`. **And punctuation is taken off the name after `.claude` only at the end of a token**, so that the text is never more lenient than the path anywhere else. At the end of a token it is: `.claude/worktrees,` is open as a token and closed to Edit. The other choice was to take no punctuation off, which closes that and refuses a sentence that ends in an open name.
   **And what §8 lists as not covered has no case**, on ADR-196 §4's ground that "a case would hold the gap in place". The other choice was a case for each, claiming it is let through, which says what the guard does today and has to be turned over the day one is closed.
5. **§2: `projects` and `plans` are open beneath every `.claude`, a checkout's too**, where they could have been open under the home folder's alone.
6. **§3: a local line that names `~/.claude` whole opens reading there.** The other choice was for the guard to refuse Read under the home folder's `.claude` whatever the list says, which needs it to know which `.claude` is the home folder's.
7. **§4: everything under `~/.claude` outside the four lines is refused for reading too.**
8. **§2: `projects` is open whole**, and not the memory directories alone.
9. **§2: `plans` is open**, though no session here is known to use plan mode.
10. **§2: `skills` and `plugins` under `~/.claude` are read and not written, and refused to a shell command.** A skill that starts a script of its own by its path there is refused; none is known to (Not measured).
11. **§3(b) holds for every folder named `.claude`**, so a scratch one under the temp folder is closed too.
12. **§3(b) takes off a name's trailing spaces as well as its dots** on Windows. A space after a file's name was seen to matter and one after `.claude` was not.
13. **§6: the script that starts the guard does not clear `NODE_OPTIONS`.**
14. **§8: `.mcp.json`, `CLAUDE.md`, `CLAUDE.local.md` and `AGENTS.md` stay writable.** Agents maintain `AGENTS.md` in almost every change.
15. **Two places Claude Code's documentation names are not opened**: `.claude/agent-memory/`, since no agent definition here declares a memory, and `~/.claude/jobs/<id>/tmp/`, a background session's scratch folder.
16. **The word `read` and one space** is how a line names a place for reading, and a line that is that word and no path admits nothing.
17. **§10: ADR-212's counting command is not admitted from a current directory that is a closed `.claude` folder.** The other choice was to let ADR-212 §4.4 mean what it meant the day it was written, the allow list and the working directories alone, which admits the command from `.claude/hooks` and makes one shell command whose current directory is a closed folder.

## Consequences

- **An agent no longer reads or writes anything under `~/.claude` but `projects`, `plans`, and, for reading, `skills` and `plugins`.**
- **An agent no longer writes anything in a checkout's `.claude` but beneath `worktrees`**, and beneath `projects` and `plans`, which hold nothing there. A change to the guard, the allow lists, the settings, an agent definition, a workflow or a project skill is drafted and installed by the operator (§7).
- **Some harmless commands are refused**, each to be reworded: `ls .claude`; `git add`, `git diff`, `git log` or `git blame` with a path in `.claude` other than beneath the three open names; `git show <revision>:.claude/…`; an option whose first name only ends in `.claude`, as in `--author=someone.claude`; `curl` or `gh` with a URL whose path names a closed `.claude` path; `cat` or `head` of the guard, where Read is used instead; a commit message, a pull request body or an issue comment written on the command line that names such a path, where `-F` and `--body-file` are used instead; `ls ../..` from a worktree's root; and any command whose current directory is a `.claude` folder or a closed folder in one.
- **A session cannot work with a `.claude` folder as its current directory**, as ADR-196 has it for a working directory.
- **The guard's test file took about 100 s for 783 cases** where 278 took 39 s, on the operator's machine: 93 s on a quiet run, and 98 s and 106 s on two others at 777. That was before ADR-212's cases joined it. The 841 it holds now take about 107 s against the draft of the merged guard, 108 s and 107 s on two runs alone; the run on the merged tree itself is the operator's. CI pays it in each of its two jobs.
- **The count in ADR-201 §9 is behind**: the table holds 841 cases.
- **The counting command of ADR-212 is the one shell command that may name a path in `.claude/hooks`**, and it is not admitted from a current directory that is a closed `.claude` folder (§10).
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **A list of the files to refuse under `~/.claude`.** Rejected in §3: it is behind on the day a new file is read, and the failure is silent. ADR-196 rejected a list of archives for the same reason.
- **A narrower allow list for the checkout too**, naming `src`, `docs` and the rest. Rejected: a new top-level folder or file would be refused until someone listed it, and the repository is what agents are here to write.
- **Telling a read from a write in a shell command.** Rejected in §4, on ADR-196's own ground against a list of verbs.
- **Naming the four unread forms as not covered, and no §3(c).** Weighed: it is ADR-196's way with every other path. Rejected: for these files a way round is a guard switched off for every later session, the rule costs no question of the file system, and the suite's allowed cases lose nothing to it (Measured).
- **§3(c) for any name that ends in `.claude`.** Rejected on measurement: it refused a range before a branch named `claude/…`.
- **Only `worktrees` open beneath a checkout's `.claude`.** Rejected in §2.
- **The guard refusing Read under the home folder's `.claude` whatever the list says.** Weighed for §3. Rejected for the same reason as the last: it needs the guard to know which `.claude` it is in, and the line that opens reading is one the operator wrote.
- **A variable the operator sets when starting a session, which lifts §3(b) for that session.** Weighed for §7: it changes nothing in how a build is done. Rejected: while it is set the acceptance of #459 is false, the guard that is live is edited in place and can refuse every call at a slip, and it is a switch in the guard, which ADR-196 §3 keeps none of.
- **The guard answering `ask` for a write to a closed path**, so that the operator approves each in the permission prompt. Weighed for §7: it is the operator's approval in the place Claude Code gives for it. Rejected: the documentation says `ask` "prompts the user to confirm" and does not say what that does in `bypassPermissions` mode; a decision that may let the call through there does not fail closed, and it was not executed.
- **Leaving it to Claude Code's protected paths.** Rejected in Context: they are allowed in one mode and judged by a classifier in another.
- **Opening the memory directories alone, not `projects`.** Rejected in §2.
- **Clearing `NODE_OPTIONS` in the script that starts the guard.** Rejected in §6.

## What this does not decide

- **A folder named with a dot after it, outside a `.claude` folder.** `wd./report.html` reaches a working directory through PowerShell (Measured), and a `read` line beneath a plain one is passed the same way (§8). Both are ADR-196's checks and want one ticket; taking the dots off every name, as §3(b) does for `.claude`, is the same remedy.
- **Whether `.mcp.json`, `CLAUDE.md` and `AGENTS.md` are an agent's to write.** §8 lists them as not covered.
- **Whether the `mcp__*` file tools and Monitor join the matcher**, which ADR-196 left open and which now bears on writes too.
- **What an agent may read in `~/.config/gh`, `~/.m2` and the other roots.** They were not read again for this.
- **Whether a hook is shown arguments another hook replaced, by execution.** §4 rests on the documentation not saying.
- **What a second user or a managed settings file changes.** Neither exists on the operator's machine as far as this record knows, and neither was looked for.

## What the build changed

1. `.claude/allowed-paths.txt`: the four lines of §3(a) in place of `${HOME}/.claude`, and its comment.
2. `.claude/hooks/private-paths-guard.mjs`: `read` lines, and a line that is that word alone (§3(a)); the closed `.claude` folder on every reading (§3(b)); the reading of every token's text (§3(c)); the table of §4 for the eight tools, the current directory and the folders a command names included; the refusal's text; and its header comment. ADR-212's admission stands before all of it, in the order §10 gives, and the header no longer says a write to `~/.claude/settings.json` is left to the written rule.
3. `src/test/hooks/private-paths-guard.test.mjs`: §9's cases, and `VESPERA_GUARD_DRAFT`.
4. `.claude/settings.json` and `.claude/hooks/run-private-paths-guard.sh` are not changed.
