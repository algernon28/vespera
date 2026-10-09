# ADR-212 — An agent may read a working directory's aggregate counts through one pinned script, and nothing else in it

> **Partly amended — see [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md).** These passages below are no longer the whole of the decision, and that record's §10 says what replaces each: §4.4's "inside the allow list and outside every working directory", since the ordinary check of the current directory now also turns down a closed `.claude` folder; §4's "without the guard reading its command for paths" and its "Any call that fails one of these is read as before", since the admission now also stands before the reading of every token's text, and a call that fails it is read with that reading too; and §5's "That is ADR-196's position, unchanged" with the three items of "What the pin does not cover", of which the third, that `~/.claude/settings.json` can be written, is closed for a call whose text names the file, and the first two are narrowed and not closed. Everything else in this record stands.

- **Date**: 2026-10-08
- **Status**: accepted on 2026-10-08, and built on 2026-10-09: the counting script and the guard's admission (§4) are in `.claude/hooks`, and K101 to K107 are green. Where this record calls a case red or the script not yet built, it states a measurement at `4b99a03`, before that build.
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), in these places and no others:
  - §1's third bullet and the sentence after the list ("The working directory is on the list because…"), which no longer refuse the counts §3 below lists;
  - §2's second bullet ("inside a working directory, wherever that is"), which gains the one exception of §4 below;
  - §2's "A path outside the list is never touched": the guard asks the file system about the working-directory word of §4's command, which need not be on the allow list, and asks only after every other check of §4 has passed (§4);
  - the alternative weighed "Reading a working directory's contents but not its documents", which the operator has now taken up for counts and for nothing else.

  ADR-196's archive and seed set (§1), its allow list (§2), every way it fails closed (§6) and all of [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md) stand as written.
- **Keeps**: [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md): the invocation account; its Context's finding that the log *"is not a thing to filter"*; §1's refusal of a filtering appender; and its alternative refusing *"a summary built by reading `vespera.log` afterwards"*. Together these are why §7 below defers the log. ADR-198 §3's exception to ADR-041 is not extended: the script's bounds are its own (§9.5). [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md): the script never takes the working-directory lock (§2). [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md): what a working directory is.
- **Rests on**:
  - the operator's decision of 2026-10-08, *"agents are allowed to access the working dirs for things like counts or metadata"*, and the scope the operator accepted after it: **aggregate counts only**;
  - the six choices this record had left open, which the operator delegated to the main session on 2026-10-08 ("your call") and then, the same day, confirmed directly that all six recommendations are accepted ("Yes, accept all six");
  - the operator's direct answer of 2026-10-09, after the architect's gate: admit the size sums and walk's integer columns, and let the hash pin replace ADR-198 §3's bound "made in `InvocationAccount` and nowhere else". §9 records each.
- **Related**: [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) and [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md), which say a table's SQL is its owner's. The script's statements name tables of `ledger`, `corpus`, `extraction` and `synthesis` from outside the application. §9.5 records how that was decided.

## Context

ADR-196 refuses every path inside a working directory, `vespera.db` included, to every agent. ADR-198 gave agents one thing to read instead: an invocation account per `vespera run` that starts the job, written outside every working directory, holding step outcomes, durations, run ids and counts grouped by closed vocabularies. The account answers what one invocation did. It does not answer a question asked later of a working directory that already exists:

- how many survivors a given run has;
- how the verdicts fall by kind for a run minted before the account was switched on;
- how large the database file and the deliverable have grown.

Today such a question goes to the operator.

The operator has decided that an agent may have those numbers itself, and nothing more. This record says exactly what that admits, and how the hook can tell it apart from everything it still refuses.

### Why "returns only numbers" is not enough

The starting point for the scope was *aggregate SQL over `vespera.db` that returns only numbers grouped by non-identifying keys*. Read as a rule about the result, that admits too much. A count over a predicate the agent chooses is an oracle:

```
SELECT COUNT(*) FROM file_occurrence WHERE path LIKE 'H:/Archive/P%'
```

It returns one number, grouped by nothing, and asked again with the next letter, it spells out every path in the walk one character at a time. `SUM(LENGTH(prose))`, a `GLOB` over `verdict.reason` and a `CASE` over `cluster.label` do the same for text. A filter on the statement that admits only `COUNT` and `SUM` cannot close this, because the leak is in the `WHERE` clause.

**So the line is drawn at who writes the statement, not at what it returns.** Vespera's repository writes it, once, in a file the hook can pin, and the agent chooses only which working directory it is asked of.

### What the hook can check

The guard reads a command as text (ADR-196 §4). It cannot tell `sqlite3 vespera.db "SELECT COUNT(*) …"` from `sqlite3 vespera.db "SELECT path …"`, and must not try: ADR-196's own alternative of refusing by shape was rejected for being dodged by the shape it leaves out. What it can check mechanically is that a command is exactly one fixed form, naming one fixed file whose bytes it can hash.

### Measured

On the operator's machine, Node 22.23.1, in a scratch folder outside the repository, with a database built from this repository's `schema.sql`:

- **`node:sqlite` is part of Node 22**, and needs no flag and no dependency. It applied `schema.sql` whole: 34 tables. `sqlite_master` lists a 35th, `sqlite_sequence`, which SQLite makes for the four `AUTOINCREMENT` columns.
- **A read-only open of a WAL database at rest creates `vespera.db-wal` and `vespera.db-shm`, and leaves both behind when it closes.** `vespera.db` is in WAL mode (`application.yaml` sets `journal_mode=WAL`), and when Vespera's last connection closes, the two files are removed. A plain read-only open, with or without `mode=ro`, put them back and did not remove them.
- **Opened as `file:<path>?immutable=1`, the same database at rest was read and nothing was created.** The file's modification time did not change.
- **A writer does not block the count.** With a writer holding the database open and a commit still in the write-ahead log, not checkpointed, a read-only reader counted that commit. The writer committed again after the reader closed.
- **A file that is not a database fails with result code 26**, which `node:sqlite` reports as the error's `errcode`.
- **On Windows, a folder whose listing is denied to Everyone** fails `readdir` with `EPERM`.

## Decision

### 1. The rule, as amended

**An agent may read what the counting script prints about a working directory (§3), and nothing else in it.** The script is the only way in.

**Admitted:**

- **counts and sums out of `vespera.db`**, by the script's fixed statements, keyed only by a walk id, a run id, or a name from a closed vocabulary: a stage, a step, a verdict kind, a walk anomaly kind, an extraction fault category, a cluster fault kind. That covers:
  - occurrences and their total bytes per walk;
  - walk's own integer columns: whether it finished, entries seen, directories entered;
  - verdicts per run by kind, and survivors per run;
  - faults by category or kind;
  - clusters and synthesis docs per run;
  - which steps of each run finished.
- **how many files the working directory holds, and their total size**, for the whole of it, for its database files, for its log files, for its deliverable, and for each deliverable tree whose folder is a run id.

**Refused, as before:**

- **any statement an agent writes**, aggregate or not, through `sqlite3`, `node -e`, a script of its own or any other program, a statement whose every result is a number included (Context);
- **every column of text or path, in every table.** Among them, as examples and not a complete list:
  - `walk.root`, `checkpoint_ordinals` and `checkpoint_path`;
  - `file_occurrence.path` and its times;
  - `walk_anomaly.path_rendering` and `detail`;
  - `run.config_consumed` (it holds profile values) and `implementation_version`;
  - `verdict.reason`, and every `detail`;
  - `cluster.label`, and `synthesis_doc.title` and `prose`;
  - the extraction and chunk caches, the relevance labels and their paths;
- **every file in the working directory as a file**: `profile.yaml`, `relevance-labels.yaml`, every page a stage writes for the operator (some carry document openings), the deliverable's pages and its CSV, and `vespera.log` with every file it has rolled into;
- **any name in the working directory** but the fixed ones the script spells, and any file's times;
- **an archive and a seed set**, wholly, as ADR-196 §1 says. How large an archive is, is answered from `vespera.db` (occurrences and bytes per walk), never by listing the archive.

### 2. One script, beside the guard

`.claude/hooks/working-directory-counts.mjs`, started as `node <script> <working directory>`.

- **It imports only `node:` modules**: no package, no file of the repository, nothing a `node_modules` folder or a relative path could put in its place.
- **It takes exactly one argument**, a folder that directly holds a regular file named `vespera.db`. A link named `vespera.db` is not one. With no argument, more than one, or a folder that is not such a folder (a file, a folder holding only `vespera.lock`), it:
  - prints nothing on stdout;
  - writes one line on stderr, naming no path;
  - exits 2.
- **It measures the folder first** (§3's `files` lines), from each entry's name, kind and size. It never follows a link, junction or symlink, and counts none. It reads no byte of any file.
- **Then it opens `vespera.db`, and nothing else, read-only**:
  - as the URI `pathToFileURL(<path>).href + "?immutable=1"` when no `vespera.db-wal` lies beside it. That is a database at rest, measured to create nothing. The path is percent-encoded that way, so a space, a `#` or a `%` in it stays part of the path;
  - read-only as it stands when one does. That means a writer has it or had it, and both auxiliary files are already there;
  - with a busy timeout of 5 seconds.
- **It never opens a connection that can write, and never touches `vespera.lock`.** A run in progress is not refused or slowed by it beyond what a reader costs a WAL checkpoint.
- **Accepted**: a run that starts between the script's look for `vespera.db-wal` and the end of its statements may give it counts that are wrong, or an error. The connection cannot write, so the database file is never changed by it. §3's shapes keep a wrong value from carrying text out.
- **It works out everything before it prints anything, and the whole script runs under one handler that catches every error.**
  - Any error ends the same way: a listing or a stat the file system refuses while the folder is measured, an entry that vanished between the listing and its stat, a busy database, a file that is not a database, or a table an older database lacks. It prints nothing on stdout, writes one line on stderr, and exits 1.
  - That line carries only the error's code: the file system's (`ENOENT`, `EACCES`, `EPERM`), or SQLite's result code as a number (26 for a file that is not a database). Never a message, a path or a stack.
  - A vanished entry is not skipped. It fails the count, which is asked again.
- **On success it exits 0, and writes nothing to stderr**, Node's experimental-feature warning for `node:sqlite` included.

### 3. What it prints

One line per fact: a record name, then `key=value` pairs separated by single spaces. **Every value is checked against its shape before it is printed, and printed as `other` when it fails.** SQLite does not enforce a column's type, and §2's race can put foreign bytes where a number is expected. The shapes:

- **a decimal integer**: a value SQLite holds as an integer of 0 or more. That covers a count, `walk.entries_seen`, `walk.directories_entered`, and a walk id wherever it is a key (`walk`, and the `walk` of `anomalies` and of `run`). A `bytes` sum is printed only when every `size_bytes` it sums is such an integer.
  - `finished` prints 1 for a non-zero integer, 0 for zero, and `other` for anything else.
- **a run id**: printed only when the stored id is 64 lowercase hexadecimal characters, the shape `RunId` gives it (ADR-048). Any other stored id prints as `other`, as a key and inside an `upstream` list alike.
- **a name from one of the closed lists below, or `other`.**
- **`-`** for an empty list.
- **in a `files` line's `folder`**: one of the fixed names below.

**The closed lists are the code's own:** the constant names of `VerdictKind`, `WalkAnomalyKind`, `FailureCategory` and `ClusterFaultKind`; the eight stage names of `StageModules`; the fifteen step names of `StepNames`. A stored value is printed as the constant it equals compared ignoring case, so a category stored as `capacity` prints `CAPACITY`. **Every stored value outside its list is counted together under `other`**, so a value nobody expected is counted and never printed.

**Ignoring case is for printing only.** Survivors compare a verdict's kind with the blocking kinds exactly, as `Verdicts.survivors` does. So a kind stored as `broken` is counted under `kind=BROKEN` and blocks nothing.

The records, in this order:

| Record | One line per | Keys |
| --- | --- | --- |
| `walk` | walk | `walk`, `finished`, `occurrences`, `bytes` (the sum of `size_bytes`), `entries-seen`, `directories-entered` |
| `anomalies` | walk and kind with a walk anomaly | `walk`, `kind`, `count` |
| `run` | run | `run`, `stage`, `walk`, `upstream` (its upstream run ids as printed, in code-point order, so `other` comes after every run id, joined by commas, or `-`) |
| `finished-steps` | run and step with a finished step | `run`, `step`, `count` |
| `verdicts` | run and kind with a verdict | `run`, `kind`, `count` |
| `survivors` | run, every run | `run`, `count` |
| `extraction-faults` | run and category with a fault | `run`, `category`, `count` |
| `clusters` | run with a cluster row | `run`, `count` |
| `synthesis-docs` | run with a synthesis doc | `run`, `count` |
| `cluster-faults` | run and kind with a cluster fault | `run`, `kind`, `count` |
| `files` | folder, below | `folder`, `files`, `bytes` |

**Lines whose printed keys are the same are one line, their counts summed.** So every run whose id prints as `other` shares one line per record and name. The `run` record carries no count: its lines are ordered by the printed run id, and lines with the same printed run id are ordered by the rest of the line.

**Order within a record** (every record but `files`): by walk id as a number, with `other` last, then by printed run id, then by printed name, each in code-point order. So `other` comes after the run ids and the constants it sits beside. **`files` lines follow the order of the list below**, with the `deliverable/<run id>` lines in code-point order.

**`survivors` is the count of what `Verdicts.survivors` returns for the run**: the run's walk's file occurrences that carry no blocking verdict under the run or any run reached from it through `run_upstream`, however many steps back (ADR-156). The blocking kinds are `VerdictKind`'s eight, compared exactly. `PASSED` and any value that is not one of the eight, spelled exactly, block nothing.

**`files` lines** count regular files only, recursively, with links neither followed nor counted:

- `folder=working-directory`: everything in it;
- `folder=database`: `vespera.db`, `vespera.db-wal` and `vespera.db-shm`, directly in it;
- `folder=log`: `vespera.log` and the files `logback-spring.xml` rolls it into, `vespera.<yyyy-MM-dd>.<n>.log`, directly in it;
- `folder=deliverable`: everything under `deliverable/`, printed only when that folder exists;
- `folder=deliverable/<run id>`: one line for each folder directly in `deliverable/` whose name is a run id's shape;
- `folder=deliverable/other`: every file under `deliverable/` that is in no such folder, printed only when there is one.

A partition folder of the deliverable is named after a seed, so its name is never printed: its files are counted in its run's line.

### 4. What the hook admits

**A Bash or PowerShell call is admitted without the guard reading its command for paths only when all of these hold.** They are checked in this order, and a call that fails one is not checked further:

1. **The grammar.**
   - The command is three words and nothing else: `node`, the script, the working directory. They are separated by spaces or tabs, with white space trimmed from both ends. No newline, no second command, no pipe, no redirect, no assignment before `node`, no option to `node`.
   - Each of the two words is bare or single-quoted:
     - a bare word holds only ASCII letters, ASCII digits and `_ . / -`, and in a PowerShell command also `\`;
     - a single-quoted word holds only those characters, `\` and the space;
     - in either, a `:` is allowed only as a drive colon: second in the word, after one ASCII letter, and followed by `/` or `\`. So `counts.mjs:x`, an alternate data stream, is refused, and so is `X:rel`, a path from a drive's own current directory.
   - So there is no `$`, backtick, double quote, `~`, wildcard, brace, comma, `=`, `@`, `%` or `#`. In a Bash command there is no backslash outside single quotes, where Bash would take it as an escape and pass the program a path other than the one the guard read. Neither word has a `..` segment.
   - **Two words that pass the above are not admitted either**, added with the build on 2026-10-09 so that §4.2's path is the one `node` opens:
     - on Windows, a word that begins with `/` or `\`. Git Bash rewrites `/d/x` into `D:/x` before `node` sees it, and does not when `MSYS_NO_PATHCONV` is set, which the guard cannot see. A word such as `/dev/x` it rewrites to a path under Git's own folder, so the guard would read the sibling and `node` would open another file. The rule holds for a PowerShell command on Windows too, where it costs only a spelling;
     - off Windows, a word that holds a `\`, which is part of a name there and parts no folders, so the `..` check above, which reads it as a separator, would be reading a path `node` does not open.
2. **The script word.**
   - Read against the call's current directory, with its separators made one, it is the absolute path `node` will open.
   - That path must equal the path of `working-directory-counts.mjs` beside the guard that is running. They are compared as text, character for character, folded to one case on Windows only.
   - No link and no other spelling stands in for it.
   - **Two spellings the grammar passes are accepted here**, on the operator's answer of 2026-10-09 after the architect's gate, because neither runs any code but the pinned script's:
     - a trailing separator or a doubled one, which reading the word against the current directory drops. `node` then opens the same file, or none;
     - a script word that begins with `-`, which `node` reads as an option. It can equal the script's path only when the checkout lies under a folder whose name begins with `-` and the command is run from above that folder.
3. **The hash.** The bytes of the file at that path, which are the bytes `node` will run, have the guard's pinned SHA-256 (§5).
4. **The current directory.** It passes the guard's ordinary check: inside the allow list and outside every working directory.
5. **The working-directory word**: read against the call's current directory, it is a folder, after links are followed, that directly holds a file named `vespera.db`.
   - It need not be on the allow list: the script reads nothing outside it but its own code.
   - **This is the one place the guard touches a path the allow list does not name**, which amends ADR-196 §2.
   - **It comes last on purpose.** A lookup of an unreachable share can hang, and ADR-201 §7 records that a hook that runs out of time lets the call through. Before the guard asks the file system anything about the word, everything else is settled: the command is the pinned script, run from an allowed directory. A lookup that hangs can then let through only that command.

**Any call that fails one of these is read as before**, so a path into a working directory is refused as before. The guard does not start the script.

What the guard admits tells an agent only that a folder holds `vespera.db`.

### 5. The script is pinned by its hash

The guard holds the SHA-256 of the script as a constant. It is taken over the file's bytes with every CR LF read as LF, since git checks `.mjs` files out with CR LF on Windows and LF on Linux. At each call that passes §4.1 and §4.2, the guard hashes the file at the path §4.2 settled, which is the guard's own sibling and the file `node` will open, and compares.

- **A script with any other byte in it is not admitted, and the ordinary reading refuses the command.** A script that differs only by CR LF for LF is the same script. So a change to the script is a change to the guard, and is reviewed as one.
- **The script lives in `.claude/hooks`, beside the guard**, so whatever protects the guard from an edit protects the script too. An agent that can edit both can admit anything, as an agent that can edit the guard alone can today. That is ADR-196's position, unchanged.

**What the pin does not cover.** These join ADR-196 §5's list; for each, the written rule is all there is.

- **The moment between the guard's check and `node` reading the script.** Anything that can write to `.claude/hooks` in that moment can put another script in its place. That is the same capability as editing the guard, which already admits anything.
- **Which `node` runs, and how it is started.** `NODE_OPTIONS` set in the environment (an `--import` or `--require` loads code before the script), the order of `PATH`, and a shell alias or function named `node` are not in the command's text, so the guard does not see them. An assignment written in the command is refused (§4.1); one already in the environment is not seen.
- **`~/.claude` is on the allow list**, so `~/.claude/settings.json` can be written. Its `env` can set `NODE_OPTIONS` for every command, and it can turn the hooks off. That hole is wider than this record and older than it: it leaves every protection of ADR-196 to the written rule, and is noted here to be added to ADR-196 §5 by a later record.

### 6. What an agent may do with a count

- **Cite it**: in an issue, an ADR, a pull request, a commit message, a test's rationale, or a report to the operator, naming the record and the run id it came from. A count leaves the machine with what it is cited in, and the operator's decision admits that.
- **Do arithmetic on counts**, and compare them across runs and across working directories.
- **Not treat a count as a label, a floor or an approval.** ADR-196 §1 leaves those to the operator, or to a local model. An agent may propose a value and cite counts for it, and the operator decides.
- **Not seek what a count is about.** The script names no file occurrence, and nothing it prints is a key to one.

### 7. `vespera.log` stays refused, including its stage lines

The starting point asked whether the stage start, finish and timing lines of `vespera.log` could be admitted without the rest of the log. **This record defers them.**

- **ADR-198 already decided against filtering the log.** Its Context found that the log *"is not a thing to filter"*: its lines were written for an operator, and a line added next month by someone who does not know about a filter is a leak nothing notices. Its §1 refused a filtering appender for that reason, and its alternatives refused *"a summary built by reading `vespera.log` afterwards"* by name. Admitting a shape of log line would reopen that decision, which is the operator's to reopen and not this record's to settle in passing.
- **Stage lines are not safe by shape.** A stage that fails closes with the failure's message, and ADR-210's stage 1 line names the corpus root.
- **The invocation account already carries what they would**: each step's start, status and duration, for every invocation since `vespera.account-dir` was set.

### 8. What pins it

Both test files are Node tests, as ADR-196 §7 explains for the first, and name no path of the operator's.

**`src/test/hooks/private-paths-guard.test.mjs` gains the K cases**, run through the registered wrapper like every other case. Its fixture copies the counting script into each fixture checkout when the repository has one, and adds:

- a working directory under no allowed root;
- a copy of the script outside `.claude/hooks`;
- a checkout whose script differs by one line;
- a checkout whose script has every LF turned into CR LF.

The cases:

- **K101 to K107, admitted**:
  - the script by a relative and by an absolute path;
  - a working directory whose path holds a space, in single quotes;
  - a relative working directory;
  - PowerShell's spelling (Windows only);
  - a working directory under no allowed root;
  - and, K107, the checkout whose script has CR LF line ends.

  *Red at `4b99a03`, measured on Windows: the script does not exist and the guard has no such rule, so each is refused as a path into a working directory.* On Linux, where ADR-196 §5 leaves a rooted path in a command unread, K102 and K106 were let through by the ordinary reading already when this was written; since ADR-215 the ordinary reading refuses the script's own path, which lies in a closed `.claude` folder, on every platform, so K101 to K107 are each let through by the admission and by nothing else ([#459](https://github.com/algernon28/vespera/issues/459)). The others name the working directory relatively, so they are refused on both platforms.
- **K201 to K230, refused**:
  - the script given `vespera.db` itself, a folder inside a working directory, a folder holding only `vespera.lock`, and a folder that is none;
  - a second argument; a second command after `&&`, `;` or a newline; a pipe; a redirect;
  - an option to `node`; an assignment before it;
  - the working directory in double quotes, behind a variable, through a command substitution, with a `..`, and written with backslashes outside quotes in a Bash command;
  - another script of the hooks folder; a copy of the counting script outside it;
  - `node -e`; `sqlite3` with a count;
  - the form with a working directory as the current directory; the form run inside `bash -c`;
  - K224: a checkout whose counting script differs from the pinned one by one line;
  - K225: the script word with `:x` after it, an alternate data stream (Windows only);
  - K226 to K230: in PowerShell, a `;`, a pipe, `$env:`, a backtick and double quotes.

  *Green at `4b99a03`, and each must stay green.*
- **K301 to K309, refused**: Read of `profile.yaml`, `relevance-labels.yaml`, `relevance-labelling.html`, `extraction-failures.html`, `arrangement.html`, `vespera.log` and a rolled log; Read of the deliverable's `documents.csv`; and a Grep of `vespera.db`. *Green at `4b99a03`, and each must stay green*: they hold §1's refused list in one place.

**`src/test/hooks/working-directory-counts.test.mjs`** starts the script over working directories it builds under the temp folder. Each holds a `vespera.db` made from `src/main/resources/schema.sql`, filled with rows whose every text and path column carries one marker string, and so do some of the folder's file and folder names. *Every test is red at `4b99a03`: the script does not exist, and each says so.* A throwaway reading of §2 and §3, outside the repository and not shipped, passed all thirteen on Windows. That shows the tests agree with each other and with this record, and that §2 and §3 can be built on Node 22 with nothing installed.

- **W01**: a fixture's counts, line for line. Among them:
  - survivors through a chain of upstream runs;
  - a blocking verdict under a sibling run that removes nothing from the chain;
  - an unknown verdict kind, and a kind stored as `broken`, neither of which blocks anything;
  - a category stored in lower case;
  - values outside every list counted as `other`;
  - the `files` lines of a working directory with logs, pages and a deliverable whose partition and stray folders are counted without being named.
- **W02**: every constant of the four enumerations, every stage of `StageModules` and every step of `StepNames`, read out of `src/main`, is printed by its own name and not as `other`. A constant added to the code and not to the script fails here.
- **W03**: the marker, and the fixture's own paths, appear nowhere on stdout or stderr, and every line has §3's shape. The fixture also holds:
  - a run whose id carries the marker, and a run whose upstream is that run;
  - the marker in `entries_seen`, `directories_entered` and `size_bytes`, and as the walk of an anomaly and of a run, written with foreign keys off;
  - each of which prints as `other`.
- **W04**: `vespera.db`'s bytes, and every name, size and time in the working directory, are the same after the script as before.
- **W05**: a WAL database at rest is counted, and nothing is added beside it.
- **W06**: with a writer holding a WAL database open and a commit in the write-ahead log, the count includes that commit, and the writer commits again after.
- **W07**: these each exit 2 with nothing on stdout:
  - no argument, and two arguments;
  - a folder holding no `vespera.db`;
  - `vespera.db` itself;
  - a folder holding only `vespera.lock`.
- **W08**: a `vespera.db` that is not a database exits 1 with nothing on stdout. Stderr is exactly one line, carrying the result code 26 and neither the marker nor a path.
- **W09**: a link in the working directory to a folder of files is neither followed nor counted. *Not started where the platform will not make one.*
- **W10**: a subfolder named with the marker whose listing is denied exits 1. The marker appears on neither stream, and stderr names no path. *Not started where the platform, or a root user, cannot be denied the listing.*
- **W11**: every import of the script, static or dynamic, names a `node:` module.
- **W12**: a working directory whose path holds a space, a `#` and a `%` is counted.
- **W13**: a folder whose `vespera.db` is a symbolic link to a database exits 2 with nothing on stdout. *Not started where the platform will not make a symbolic link to a file.*

**Not pinned, and why:**

- **That the script reads no byte of any file but `vespera.db`'s pages.** No test can watch a read that returns what a stat would.
- **The race of §2.** Nothing in a test can start a run in the instant between two statements.
- **That a working directory being written by a real `vespera run` is counted without slowing it.** W06 shows a writer that goes on writing, and nothing more: the tests run one reader against one writer in one process, and see no contention between them.
- **§5's moment between check and run, and which `node` runs.** These are not covered, and nothing in a test could hold them.

### 9. Decided on 2026-10-08 and 2026-10-09, by the operator

The draft of this record left six choices open, each with a recommendation. On 2026-10-08 the operator delegated them to the main session, which took every recommendation, and then the operator confirmed acceptance of all six directly. The architect's gate then asked how §9.5 squared with ADR-198 §3, and on 2026-10-09 the operator answered that directly. Each is decided as written here:

1. **The mechanism is one pinned script (§2 to §5)**, and SQL an agent writes is refused even when it returns only numbers (Context).
2. **The working-directory argument need not be on the allow list (§4.5).**
   - The way not taken was to make the operator list each working directory in `.claude/allowed-paths.local.txt`.
   - Listing the folder itself admits nothing else, since everything inside it is refused as a working directory.
   - But if the folder ever held neither `vespera.db` nor `vespera.lock`, everything left in it would be let through.
3. **`vespera.log`'s stage lines are deferred (§7).** The invocation account, with `vespera.account-dir` set, is where an agent reads step starts, outcomes and durations.
4. **The script opens a database at rest as immutable (§2)**, accepting the race, rather than leave two files behind in the working directory on every count.
5. **The script is a tool outside the application, and its bounds are its own.**
   - **The tension.** It names the tables of four modules from outside the application. ADR-041 says a capability owns its tables and nothing else reads them. ADR-209 holds that for every shipped class, with ADR-198 §3's counts as the one exception. The script is not a shipped class and not a module, so ADR-209's test does not read it.
   - **ADR-198 §3's three bounds** are: the reads are `COUNT`s and nothing else; each is grouped by a closed column or by none; and they are made in `InvocationAccount` and nowhere else. **The script does not meet the first or the third, and does not claim to.** It reads `SUM(size_bytes)` and walk's three integer columns, `finished`, `entries_seen` and `directories_entered`, as values, and it is not `InvocationAccount`.
   - **The operator decided on 2026-10-09:** admit the size sums and walk's integer columns, and let the hash pin replace the bound "made in `InvocationAccount` and nowhere else".
   - **So the script is bounded so:**
     - it reads `vespera.db` read-only, by statements fixed in it;
     - it selects counts, the sum of `size_bytes`, walk's three integer columns, ids, and the closed columns, each printed only in its shape (§3);
     - every line is keyed by a walk id, a run id, a closed column, or none;
     - those statements are made in the pinned script and nowhere else (§5).
   - **A schema change that breaks a statement fails the hook tests**, because W01 builds its fixture from `schema.sql`.
   - **The way not taken** was to answer the counts from the application (Alternatives, rejected).
6. **The first statement set is §3's and no more, and no reason-prefix count is in it.** A count by a fixed prefix of `verdict.reason`, such as `could not be read: `, would print a number keyed by a constant the script spells. Each is left out: each reads a reason column by a predicate, which is what §1 refuses, and is for a later amendment to weigh one at a time.

## Consequences

- **An agent can answer "how many" about a working directory without the operator**: survivors per run, verdicts by kind, faults by category or kind, clusters and synthesis docs, occurrences and bytes per walk, which steps finished, and how large the database file, logs and deliverable are.
- **Nothing else in a working directory becomes readable.** Every case of ADR-196 and ADR-201 stays as it is.
- **A new count is a change to the script and to the guard's pinned hash**, reviewed together. The W tests hold the statements against `schema.sql` and the closed lists against the code.
- **`vespera.db` is never written by a count**, and a database at rest keeps exactly the files it had.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **Letting agents run SQL that returns only numbers.** Rejected in Context: a chosen predicate turns a count into an oracle for any text.
- **A filter in the guard that admits `sqlite3` with a statement made only of `COUNT` and `SUM`.** Rejected for the same reason, and because the guard reads a command as text and ADR-196 already weighed and rejected refusing by shape.
- **A `vespera counts` subcommand in `pipeline`.** Rejected, for four reasons:
  - The hook cannot pin a jar, which changes on every build, so it could not tell the shipped command from a rebuilt one that prints anything.
  - Every command takes the working-directory lock (ADR-177), so it would be refused while a run is going, which is when the question is asked.
  - Start-up replays `schema.sql`, which writes and can build an index for minutes (ADR-187).
  - A change to `pipeline` moves the run ids of stages 3 to 6b.
- **The invocation account alone.** It stays, and §7 leans on it. It cannot answer a question about a working directory asked after the invocation, or about a run minted before `vespera.account-dir` was set.
- **The script anywhere else in the repository, unpinned.** Rejected: any agent can edit a file under `src` or `scripts`, and the exact command would then run whatever it was made to say.
- **A double-quoted working directory.** Left out: in Bash a double quote expands `$` and a backtick, and the guard would have to read what the shell expands. Single quotes expand nothing in either shell.
- **A plain read-only open of a database at rest.** Rejected on the measurement in Context: it leaves `vespera.db-wal` and `vespera.db-shm` in the working directory.
- **Skipping an entry that vanishes while the folder is measured.** Rejected for one rule: every error fails the count, which is cheap to ask again.

## What this does not decide

- **Whether `vespera.log`'s stage lines are ever admitted** (§7).
- **Counts of an archive or a seed folder by listing it.** Both stay wholly refused. The census's own counts in `vespera.db` answer for an archive.
- **Counts per seed partition**, keyed by a partition's ordinal. A seed's name is never printed, and whether an ordinal is a key enough is not weighed here.
- **Counts by a fixed prefix of a reason** (§9.6).
- **A count of the extraction cache, the chunk cache, the vectors or the shingles.** Their tables are large: ADR-191 measured a 31-minute read of one run's shingle rows on `H:`.
- **`mcp__*` tools and Monitor**, which ADR-196 §5 leaves uncovered, and which are as uncovered for this form as for any other.
- **The `~/.claude` hole of §5**, which a later record adds to ADR-196 §5.

## What the implementation owes

**Two files sit under `.claude/hooks`**: the script and the guard. Where an agent cannot write there, **the operator** writes them from this list. `build.yml` is not under `.claude/hooks`, and `spec-implementer` writes it. All three landed together on 2026-10-09; before that, K101 to K107 were red.

- **`.claude/hooks/working-directory-counts.mjs`**, new, as §2 and §3 state:
  - It imports only `node:` modules (W11).
  - Its statements select only what §9.5 bounds. The closed lists are written in it as literals, and W02 holds them to the code. Every printed value is checked against §3's shape. Kinds are compared exactly for survivors and ignoring case for printing.
  - `node:sqlite` is imported after the process's `warning` listeners are removed, so stderr stays empty on success.
  - The whole script runs under one handler that catches every error, as §2 says.
  - The database URI is `pathToFileURL(<path>).href + "?immutable=1"`.
  - A header comment cites this record.
- **`.claude/hooks/private-paths-guard.mjs`**:
  - §4's admission, checked before the command is read for paths, in §4's order, with §4.1's grammar and §4.2's exact comparison of the script word with the guard's sibling;
  - §5's constant, named `COUNTING_SCRIPT_SHA256`, taken over the script with CR LF read as LF;
  - one paragraph in its header comment saying what is admitted, what §5 does not cover, and citing this record.
- **`.github/workflows/build.yml`**: in both jobs, `node --test src/test/hooks/working-directory-counts.test.mjs`, beside the line that starts the guard's test.
- **`AGENTS.md`**: its section on the operator's documents was rewritten with the build on 2026-10-09, to state the one admitted form and the second test command.
- **Not `.claude/settings.json` and not `.claude/allowed-paths.txt`**: the matcher and the allow list stay as they are (§9.2).
