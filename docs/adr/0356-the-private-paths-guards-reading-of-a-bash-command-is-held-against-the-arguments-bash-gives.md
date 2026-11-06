# ADR-356 — The private-paths guard's reading of a Bash command is held against the arguments Bash gives

- **Date**: 2026-11-06
- **Status**: accepted on 2026-11-06.
- **Amends**: [ADR-351](0351-the-private-paths-guard-reads-a-message-a-pattern-a-program-and-a-here-document-into-the-temp-folder-as-no-path.md) §1's third bullet and §2, how `grep`'s options are walked: §3 below ends the walk at a word the guard does not read whole, and nothing else of ADR-351 changes.
- **Keeps**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) §3, which keeps no switch that lowers a bound for a test (§2 below is none); [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §7, by which the guard's change is a draft the operator installs; and ADR-351 §4, its list of trusted programs and its reading of every other command whole.
- **Settles**: what [#717](https://github.com/algernon28/vespera/issues/717) leaves of ADR-351: the readings of Bash nobody had measured, the forms with no real read or write, and the guard re-implementing Bash's parse.

## Context

ADR-351 takes some text of a Bash command out of the guard's reading for paths: a flag's argument, a single-quoted operand, a quoted here-document's lines. What it takes out rests on the guard's own reading of the command: where Bash cuts it into segments and words, which word is each segment's program, which words Bash gives that program once its redirects are taken out (`argumentsOf`), and which of those a program reads as text (`textSpans`). That reading re-implements part of Bash's parse and part of each program's option parsing, and seven drafts of ADR-351 each let through a form where the two disagreed, every one in a command the cut did read. ADR-351 §4 bounds the harm of a command the cut cannot read; nothing held the reading of a command it can.

The premise of C783 to C791, that Bash takes a redirect, its target and a descriptor out of the program's arguments wherever they stand, rested on the Bash reference manual and on `parse.y`, and no run of Bash had measured it.

### Measured

**GNU bash 5.3.15(2)-release (x86_64-pc-cygwin), Git Bash on Windows**, with `printf "[%s]"` standing for the program and its output sent to a file and then printed:

| command | the arguments the program is given |
|---|---|
| `printf "[%s]" a 2>/dev/null b` | `[a][b]` |
| `printf "[%s]" a 2147483648>F b` | `[a][2147483648][b]` |
| `printf "[%s]" a 99999999999999999999>F b` | `[a][99999999999999999999][b]` |
| `printf "[%s]" -t >F --jq x` | `[-t][--jq][x]` |
| `printf "[%s]" -t 2>&1 >F --body y` | `[-t][--body][y]` |
| `printf "[%s]" a 2147483647>/dev/null b` | nothing runs: `2147483647: Bad file descriptor`, exit status 1 |
| `printf "[%s]" -t > >/dev/null --jq x` | nothing runs: `syntax error near unexpected token '>'`, exit status 2 |
| `printf "[%s]" {-e,} -e x` | `[-e][-e][x]` |
| `printf "[%s]" {-f,} -e x` | `[-f][-e][x]` |

So C783 to C791's premise holds: Bash takes the redirect, its target and a descriptor number out of the arguments wherever they stand, and digits more than an int holds stay an argument, in this 64-bit build as ADR-351 §1 reads them. `2147483647>` and a redirect whose target is itself an operator run nothing. And a brace list whose second member is empty gives one word, an option.

**GNU grep 3.0, in the same Git Bash**, in a folder holding `doc.txt`, one line of which holds `-e`: `grep {-e,} -e 'doc.txt'` printed that line and ended with exit status 0. Bash gave `grep` `-e -e doc.txt`, the first `-e` took the second as its pattern, and `doc.txt` was a file grep read.

**The guard ADR-351 installs, read by `bashReading` (§2)**: of `grep {-e,} -e '../outside/doc.txt'` it takes `'../outside/doc.txt'` out as `-e`'s pattern, after `{-e,}`, a word it does not read whole; and the same of `grep $o -e '../outside/doc.txt'`, after `$o`. So both are let through, where each reads `../outside/doc.txt` once Bash makes the word before `-e` an option that takes `-e` as its value (C792, C793).

**§1's test, on Windows, against the draft guard of §2 without §3's change**: of the 121 C cases of Bash, the guard takes text out of 84 and reads 37 whole. 82 of the 84 agree with Bash in every program, argument and span; C792 and C793 disagree, each by an argument taken out after a word the guard does not read whole, which Bash gave as `-e` in C792.

## Decision

### 1. The guard's reading of each C case is held against Bash

**`src/test/hooks/private-paths-guard.test.mjs` runs, in Bash, the command of each of its C5, C6 and C7 cases of Bash that the guard takes anything out of, and compares the arguments Bash gives each program with the guard's reading.** Every program ADR-351 §4 trusts (`git`, `gh`, `grep`, `egrep`, `fgrep`, `awk`, `gawk`, `mawk`, `jq`, `cat`, `cd`, `echo`, `true`, `ls`, `head`, `tail`, `wc`, `sort`) is replaced by a shell function of its name that appends the name, how many arguments it was given and each argument, each ended by a NUL, to a file of the test's own; everything else of the command, its cut, its redirects and here-documents, its quotes and its brace and variable expansion, stays Bash's, and Bash itself decides which word is each program. The command runs in a folder of the test's fixture, with a `TEMP` of its own, `o` unset, and `$temp`, which Bash reads as no variable of the environment, given a value in the fixture, so that every target it writes lands there and nothing but a stand-in runs. Three claims hold each case:

- **Every segment the guard reads whose program is trusted is a call Bash made**: the program is the one called, the arguments up to the first word the guard does not read whole are the ones Bash gave, and where it reads every word whole Bash gave no more.
- **Every call Bash made is such a segment.**
- **Every span the guard takes out as text is the whole of one argument the program is given, or what follows the `=` of one; every argument up to it and it are ones the guard reads whole; and Bash gave that argument as the guard reads it.** What a program reads in an argument rests on every argument before it, so a span after a word whose expansion the guard does not know rests on a guess.

A case the guard reads whole, its cut refused or a program off §4's list, has nothing taken out, and is claimed so and not run. The test runs on CI's Linux runner and Windows runner, as the file always has, and on Git Bash on Windows; where no bash can be started, each comparison fails and says so, and so does each where the guard exports no reading, so that neither ends green having compared nothing.

### 2. The guard exports its reading, and imported for it decides no call

**The guard exports `bashReading(command)`**: null where its cut does not read the command, else whether ADR-351 §4 trusts it and, for each segment, its program's word, the words `argumentsOf` gives the program and the spans `textSpans` takes out, each word with its text as the guard reads the shell as giving it, or null where it does not know all of it. **Imported by its URL with the query `?reading`, the guard decides no call.** node gives the file it is started on a URL with no query, and the wrapper starts the guard by its path, so started as a hook it decides every call; neither a tool call nor its environment reaches the URL, a `--import` of `NODE_OPTIONS` included, which can change what node runs but not the URL of the file it starts. It is no switch of ADR-196 §3's kind: no call can meet it, and it lowers no bound and changes no verdict. `bashReading` reads a command by the functions `textsRead` reads it by, and decides nothing itself.

### 3. A word the guard does not read whole ends `grep`'s walk

**In the walk of ADR-351 §2 over the words of `grep`, `egrep`, `fgrep`, `awk`, `gawk`, `mawk` and `jq`, at a word whose head the guard does not know (`$o`, `{-e,}`, `*.md`), a word that holds a `$` outside quotes (`x$o`), or an option the program's table does not have, nothing more of the segment is taken out**: no argument of a later `-e` or `--regexp`, and, as before, no operand. A text taken out before such a word stays out, as the walks of `git` and `gh` in ADR-351 §1 keep theirs. ADR-351 read such a word as giving the pattern and walked on, so a later `-e` still gave its argument as text; Bash may make the word an option that takes the next word as its value, `-e` or `-f` among them (Measured), and the word after that `-e` is then an operand, a file `grep` reads. An option the table does not have is one whose argument the table cannot count, so it ends the walk as it does in `git`'s and `gh`'s.

### 4. The gate's worktree runs nothing against a head, and that is kept

The architect's gate works in a worktree of its own, where the worktree-isolation hook refuses a Bash command it cannot show stays in that worktree, among them one that starts `bash` or names `git` in a compound form, and no checkout of another branch's head is made. So the gate runs neither the unit build nor the guard's suites against the head it gates, and cannot set `VESPERA_GUARD_DRAFT` to a folder of its own. **That is kept, and no draft of the hooks or settings is made for it**: CI runs `private-paths-guard.test.mjs`, §1's comparison in it, and the build on every head on both runners, so what Bash gives is measured where the gate reads it, in the head's checks; and a hook that let a gate start `bash` or another branch's head in its worktree would open the isolation that keeps one worktree from writing another's.

### 5. The test

`src/test/hooks/private-paths-guard.test.mjs` gains:

- **C792 and C793**, refused (§3): `grep {-e,} -e` and `grep $o -e` of a quoted file outside the allow list, which Bash makes, or may make, an option that takes `-e`, so that the file is read;
- **for each C case of Bash**, a test named by its id: `read as Bash gives its arguments`, holding §1's three claims, for each case the guard takes text out of; and `read whole, as Bash need not be asked`, holding that the guard takes nothing out, for each other.

Against the draft guard of §2 without §3's change, C792 and C793 and their comparisons fail, and the rest pass (Measured).

## Decided here

1. **The oracle is Bash, with the programs replaced and nothing else.** A function in each program's place records its arguments whatever the command's own redirects do with its output, where `printf` in the program's place prints into them (`>/dev/null` among the cases); and Bash picks the program, where replacing the word the guard reads as the program would let the guard's own reading choose what is compared.
2. **Only what the guard takes text out of is run.** A command read whole has no reading of the guard's to compare, and the C7 cases that are read whole define functions, aliases and traps that would run in place of the stand-ins.
3. **The comparison holds the guard to what it knows, and not to the programs' tables.** Bash says which arguments a program is given and nothing of how the program reads them: whether `-t` takes the next word is `gh`'s pflag's, and it stays held by the C cases alone. What Bash can hold is that every argument the guard's tables are read over is one Bash gives, which is what C783 to C793 rest on.
4. **§3 ends the walk, and does not read on.** A word the guard does not read whole can become any number of words, any of them an option of any arity, so no later word's role in the program's reading is known.

## Consequences

- **The guard's reading of every C case of Bash it takes text out of is measured against Bash on each change**, on Linux and on Windows, and a C case of Bash added later is compared with no line of its own.
- **`grep {-e,} -e '<file>'`, `grep $o -e '<file>'` and their kin are refused** for the file once the operator installs a draft that carries §3; until then the installed guard lets them through.
- **A command whose `grep` has a word the guard does not read whole before its `-e` is read whole from that word on**, and is refused where its text after that word names what the allow list refuses; such a pattern goes in a file, or the word is written out.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **`printf "[%s]"` in place of the program's word.** Rejected: Decided here 1.
- **A mode of the guard that prints its reading, set by a variable of the environment or a field of the call.** Rejected: a hook whose answer a call or its environment can change is the switch ADR-196 §3 refuses; an importer's URL is reached by no call.
- **Reading the guard's functions out of its text in the test.** Rejected: it holds the file's layout, and breaks on a reordering that changes nothing the guard does.
- **Narrowing ADR-351 §1 and §2 to the forms of #697, with no redirect, no option that takes a value and nothing before the text flag, and reading everything else whole.** Not taken: the forms it keeps rest on the same reading, unmeasured, and §1 measures every form the C cases hold, those of #697 among them.

## What this does not decide

- Fuzzing the guard with generated commands against §1's oracle; it would use the same stand-ins, and is a later record's.
- Whether each program reads its options as the guard's tables say: a comparison against `git`, `gh`, `grep`, `awk` and `jq` themselves, which would need each installed on both runners.
- A Bash other than the runners' and Git Bash's, or a `bash` whose options a profile or `BASH_ENV` sets: the test unsets `BASH_ENV` and `ENV`, and the guard reads the command, not the machine (ADR-351, What this does not decide).
- A PowerShell command, of which ADR-351 takes nothing out.

## What the implementation changes

1. `.claude/hooks/private-paths-guard.mjs`, drafted as `docs/agents/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator (ADR-215 §7): `bashReading`, exported, and the call of `main` made only where the file's URL has no `?reading` (§2), with the header comment; and in `textSpans`'s walk over the operand programs, the two places that read a word the guard does not know, or an option of kind `unknown`, as giving the pattern and walk on, each return the spans found before it (§3), with the header comment's sentence of `grep`'s `-e`.
2. `src/test/hooks/private-paths-guard.test.mjs`: §5's cases and comparisons, and its header comment, written before the guard.
3. ADR-351 takes a pointer to this record at its top. Its decision is not edited.
4. `docs/agents/private-paths-guard.md`: what ADR-356 holds and amends.
