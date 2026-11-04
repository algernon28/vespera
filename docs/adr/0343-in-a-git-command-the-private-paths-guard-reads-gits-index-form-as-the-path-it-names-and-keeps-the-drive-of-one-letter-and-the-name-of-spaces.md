# ADR-343 — In a git command the private-paths guard reads git's index form as the path it names, and keeps the drive of one letter and the name of spaces

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Amends**: [ADR-217](0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md), in these places and no others:
  - §2, "a name made only of dots, spaces or a stream name is refused": in a segment whose program is `git`, a token that begins with git's index form, `:<path>` or `:<N>:<path>`, is read as its path, so the empty name before its colon is no longer such a name (§1 below);
  - its Consequences' first item of harmless commands refused, "a token that begins with a colon", for that one form and for a refspec that deletes a branch, which takes the same reading (§2 below);
  - its "Decided here" 5 and "What this does not decide", "whether the costs of §2 are kept": they are kept but for that form (§3 and §4 below).
- **Keeps**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) §4, its "path on a drive with no separator after the colon", read on Windows from a token headed by one letter and a colon (§3 below); every other reading of §4, its bounds and §6. The rest of an index path is read by those readings, and by nothing looser. Its §5's list of what the hook does not cover gains §5 below, and nothing of it is struck.
- **Keeps**: [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md): the rest is read as text and as walked, and a name of it that is there and cannot be followed refuses.
- **Keeps**: [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) §3: `git` is no lister, and the refusal "outside the allow list" is lifted for no reading this record adds. Its §3(a) cuts a command into segments, and §1 below cuts it the same way.

## Context

On Windows ADR-217 §2 refuses a name made only of dots, spaces or a stream name, since what Windows opens for one is not known. A token of a command that begins with a colon has such a name in front of it: read as a relative path, `:AGENTS.md` is one name, and with its stream name taken off it is empty. So `git show :AGENTS.md`, `git show :0:docs/x.md` and `git diff :2:src/a.java` were refused on Windows, from inside the checkout, though git reads nothing there but a blob of the repository's own index. ADR-217 named the cost and left open whether it is kept (its "What this does not decide").

The user was asked three questions and answered each:

1. Whether ADR-196's drive of one letter is loosened, so that `m:m.group` in a `python -c` lambda, and `Q:x`, are not read as a path on drive `M` or `Q`. **No: "KEEP ADR-196's one-letter-drive rule as it is: `m:m.group` and `Q:x` stay refused; nothing loosens it."**
2. Whether the guard accepts git's index form. **Yes: where a segment's program is `git`, a token `:<path>` or `:<N>:<path>`, N a stage number 0 to 3, has its leading `:` or `:N:` taken off, and the rest is judged as a path relative to the repository, still checked against the allow list like any path.** `git push origin :claude/x`, a refspec, and `sed ':a'` stay as ADR-217 has them unless this record decides otherwise for refspecs.
3. Whether a name made only of spaces, as `sed 's/ /_/g'` holds one between its slashes, is let through. **No: "a name made only of spaces stays refused; awk/sed programs go in a file."**

### Measured

**git 2.55.0.windows.5, on Windows 11, in this repository**, from its `docs` folder, by a script run by its path:

- `git cat-file -e :AGENTS.md` and `:0:AGENTS.md` succeeded, though `docs` holds no `AGENTS.md`: the index form's path, when it does not begin with `./` or `../`, is read from the top of the working tree.
- `:./AGENTS.md` failed with "path 'docs/AGENTS.md' does not exist", and `:../AGENTS.md` and `:0:../AGENTS.md` succeeded: a path that begins with `./` or `../` is read from the current directory.
- `:../../outside.md` failed with "'../../outside.md' is outside repository".
- As a pathspec, `git ls-files -- :adr/README.md` listed `adr/README.md` and `:AGENTS.md` listed nothing: a pathspec's leading colon with no magic letter after it is taken off, and the rest read from the current directory. `:/AGENTS.md` listed `../AGENTS.md`: the `/` after the colon is the pathspec's "from the top".
- `git rev-parse --verify :/ADR-342` printed a commit: as a revision, `:/<text>` names the newest commit whose message matches the text, and no path.

### Read, and not executed

- `gitrevisions(7)`: ":[<n>:]<path> … A colon, optionally followed by a stage number (0 to 3) and a colon, followed by a path, names a blob object in the index at the given path." A stage number of 4 or more makes no stage form, and `:4:x` is the path `4:x`.

## Decision

### 1. In a git segment, a token that begins with the index form is read as its path

The command is cut into segments as ADR-264 §3(a) cuts it, at `;`, `&&`, `||`, `|`, `&` and the line break, without regard to quotes. **A segment's program is `git`** where its first word is the bare word `git`: no separator in it and no quotes around it, compared character for character in a Bash command and with its case folded in a PowerShell command, as ADR-264 §3(d) compares a lister. An assignment before it, a word that runs another program (`env git`, `command git`), `git.exe` and a path to git are not `git`. **A command that holds `(`, `)` or a backtick, or in PowerShell `{` or `}`, has no git segment**, as ADR-264 §3(b) holds of a listed command: a substitution can run another program inside the segment, and that program would be given the reading.

**In a git segment, a token that begins with `:` is read as its rest**: the token with its leading `:<N>:` taken off, where N is one digit from 0 to 3, or else with its leading `:` taken off. A token here is each token ADR-196 §4 reads, as amended: a token cut at white space and the shell's punctuation, a quoted string read whole, two halves joined at an escaped space, and a piece cut at `,`, `{` or `}`. **The rest is read as every token of the command is**: a drive path, a bare drive, a drive of one letter with no separator after the colon on Windows, a home path, a variable at its head, a Git Bash or UNC path, and otherwise a relative path, read against each folder the command names (ADR-196 §4), as text and as walked (ADR-201 §1), and on Windows as Windows opens its names (ADR-217 §1). Each reading is judged by the allow list, its `read` lines, the closed `.claude` folders, a deliverable tree's marker and every other check as any path's is. An empty rest is no path, as a token left empty by trimming its punctuation is none.

**On Windows the token is read as its rest and not as written**: as written, its first name is made only of a stream name and ADR-217 §2 refuses it, which is the cost this record lifts. **Elsewhere it is read both as written and as its rest, and either refuses**: as written it is the name ADR-217 §7 says a colon makes, read as before, and the rest adds a reading of the place git reads.

**Three tokens of a git segment are not given the reading, and keep ADR-217 §2's refusal on Windows:**

- **the target of a redirect**, a token that `<` or `>` stands directly before, with or without white space between: the shell opens it, not git;
- **a value after `=`**, which ADR-196 §4 reads as a token of its own: `git diff --output=:out.txt` names a file git writes by that name, and a value is not git's index form;
- **a token that does not begin with the colon**: `HEAD:README.md` is read as before.

**Relative to the repository.** git reads a rest that begins with `./` or `../` from the current directory, and any other from the top of the working tree (Measured). The guard reads every rest against each folder the command names, the current directory and a `git -C` folder among them, as it reads every relative token. Where git runs in the top of its working tree the two are one place. Where it runs beneath it, git reads a name from a folder above the one the guard reads it against; that is §5's.

### 2. A refspec takes the same reading

A refspec that deletes a branch, `git push origin :claude/x`, is a token that begins with a colon in a git segment. **It is given §1's reading, and is let through where its rest, read as a path, lies inside the allow list**, as `claude/x` read from the checkout does. Telling a refspec from an index path needs git's grammar of each subcommand: `push` and `fetch` take refspecs, `show`, `diff` and `cat-file` revisions, `add` and `rm` pathspecs. That is a second parser to keep in step with git for no gain: a refspec reads no file, and reading it as a path can only refuse what that path refuses. `git push --delete` stays the form that names no path.

### 3. ADR-196's drive of one letter is kept

On the user's word (Context, question 1), a token headed by one letter and a colon is on Windows a path on that drive, in a git segment as anywhere. `python -c "f = lambda m:m.group(0)"` and `git show Q:x` stay refused on Windows. A rest that is such a token is read so too: `git show :0:Q:x` is refused, as is `git show :C:/x`, a drive path. A token that begins with a colon is not headed by a letter, so §1 and this section never meet on one token's head.

### 4. ADR-217's refusal of a name made only of spaces is kept

On the user's word (Context, question 3), "a name made only of spaces stays refused; awk/sed programs go in a file". A name made only of spaces that stands in a longer path is refused on Windows, as ADR-217 §2 has it: `sed 's/ /_/g' README.md`, `cat 'scratch/ /note.txt'`, and in a git segment the rest of `git show ': /x.md'`. ADR-217 §2's one exception, a relative token of a command made only of spaces, stands. A program for `sed` is written to a file and named with `-f`, and one for `awk` likewise.

`sed ':a'` and a JSON value written after its colon with no space, `echo '{"a":1}'`, are tokens that begin with a colon after a program that is not git, and stay refused on Windows.

### 5. What the hook does not cover, added to ADR-196 §5

- **A rest git reads from the top of a working tree that lies above the current directory.** The guard reads it from the current directory. It is the gap `HEAD:<path>` already has, read since ADR-196 §4 as a relative token `HEAD:...` and on Windows as `HEAD` with a stream name. What git reads by the index form is a blob of the repository it runs in, and git refuses a path outside that repository (Measured).
- **A token of a git segment that git opens as a file by its name as written**: an option's value given as the next word, as in `git commit -F :msg.txt`, or a path given to `git apply` or `git diff --no-index`. On Windows the guard reads the rest alone, and what Windows opens for a name that begins with a colon is not measured (ADR-217 §5). Elsewhere the token as written is read too.
- **`:/<text>`**, which is a commit's message to `git show` and the pathspec's "from the top" to `git add`. Its rest begins with `/`, a path rooted on the current drive without its letter, which ADR-196 §4 leaves unread unless it is a Git Bash drive path. As a revision it names no path; as a pathspec, a path inside the repository.

None of these has a case, on ADR-196 §4's ground that a case would hold the gap in place.

### 6. Elsewhere nothing changes

A token that begins with a colon after any other program, in a segment whose program is not git, or in a command with no git segment, is read as ADR-217 has it. A file tool's field and a search's path are not commands, and a name made only of a stream name in one stays refused on Windows (ADR-217 §2, Z303).

### 7. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 27 cases, O101 to O108, O201 to O204, O301 to O309, O401 to O403 and O501 to O503.

- **O101 to O108**, let through on every platform: `git show :AGENTS.md`, `git show :0:docs/x.md`, `git diff :2:src/a.java`, `git show :README.md` in a PowerShell command, `git -C src show :Example.java`, `git push origin :claude/x` (§2), a quoted index path whose name holds a space, and a git segment after another.
- **O201 to O204**, refused: a rest that climbs out of the checkout, a rest that is a drive path, a stage's rest on a drive no allowed root is on, and a rest headed by `$HOME` that lies outside the allow list. O202 and O203 are started on Windows alone, O201 and O204 everywhere.
- **O301 to O309**, refused on Windows: the same token after `cat`, after `echo` with `git` as an argument, in a segment after a git one, in a command quoted for `bash -c`, as a redirect's target, as a value after `=`, inside `$(...)`; `sed ':a…'`; and `echo '{"a":1}'`.
- **O401 to O403**, refused on Windows (§3): `lambda m:m.group(0)`, `git show Q:x`, `git show :0:Q:x`.
- **O501 to O503**, refused on Windows (§4): `sed 's/ /_/g'`, `cat 'scratch/ /note.txt'`, `git show ': /x.md'`.

Against the draft before this record, O101 to O108 are refused on Windows; on Linux they are let through already, and O201 and O204 are let through there, since `:..` and `:$HOME` were names and not a climb or a variable.

## Decided here

1. **§1: the rest is read on every platform**, and elsewhere beside the token as written. The user's answer named the form, and Windows is where its cost fell; reading the rest elsewhere refuses what git reads outside the allow list there too.
2. **§1: a git segment is cut and recognised as ADR-264 §3 recognises a lister's**, a command holding `(`, `)` or a backtick, or a PowerShell brace, having none.
3. **§1: a redirect's target and a value after `=` are not given the reading.**
4. **§1: the rest is read against each folder the command names**, and not against the top of the working tree, which the guard would have to find by asking the file system or git; §5 records the difference.
5. **§2: a refspec takes the index form's reading**, where the user left it as ADR-217 has it unless this record decided otherwise.

## Consequences

- **On Windows `git show :<path>`, `git show :<N>:<path>`, `git diff :<N>:<path>` and `git push origin :<branch>` run from the checkout**, where the rest lies inside the allow list.
- **A rest outside the allow list is refused on every platform**, `:../outside/doc.txt` and `:$HOME/Documents/x.txt` on Linux among them, where before they were names that began with a colon.
- **On Windows these are still refused, each to be reworded as ADR-217 says**: a token that begins with a colon after any program but git, a JSON value written after `":` with no space, a `sed` label, a redirect's target or an option's value that begins with a colon, and any such token in a command that holds `(`, `)` or a backtick. So is `m:m.group`, `Q:x`, and a `sed` or `awk` program that holds a name made only of spaces between separators.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **Refspecs kept refused** (§2). Rejected: it needs git's grammar of each subcommand, and the reading it would avoid only refuses more.
- **Reading the rest from the top of the working tree**, found by walking up to `.git`. Rejected for this record: it asks the file system before the allow list is judged, against ADR-196 §2, or starts git, which ADR-217 weighed and rejected for Windows; and it closes a gap `HEAD:<path>` has open already.
- **The reading for any program**, not git alone. Rejected: `cat :x` opens a name that begins with a colon, whose opening on Windows is not known (ADR-217 §2), and the user's answer named git.
- **Loosening the drive of one letter, or the name made only of spaces.** Refused by the user (Context, questions 1 and 3).

## What this does not decide

- Whether `git.exe`, a path to git, or `git` after `env` or an assignment is ever given the reading.
- What Windows opens for a name that begins with a colon, which ADR-217 left unmeasured.
- Whether ADR-217's other costs, a token of three dots and an elision in a quoted sentence, are kept.

## What the implementation changes

1. `.claude/hooks/private-paths-guard.mjs`, drafted as `docs/agents/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator (ADR-215 §7): §1's git segments and the reading of a token's rest, and its header comment.
2. `src/test/hooks/private-paths-guard.test.mjs`: §7's cases and its header comment, written before the guard.
3. ADR-217 takes a pointer to this record at its top. Its decisions are not edited.
4. `docs/agents/private-paths-guard.md`: the sentence on a token that begins with a colon.
