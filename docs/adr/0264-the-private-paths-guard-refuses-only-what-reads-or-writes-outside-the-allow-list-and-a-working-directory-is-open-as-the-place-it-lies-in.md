# ADR-264 — The private-paths guard refuses only what reads or writes outside the allow list, and a working directory is open as the place it lies in

> **Partly amended — see [ADR-270](0270-every-log-vespera-writes-is-in-the-working-directorys-logs-folder-the-invocation-account-among-them.md).** The Consequence "ADR-198's account no longer has the reason it was put outside every working directory, and stays where it is" no longer holds: the account is written in the working directory's `logs` folder, where `vespera.log` and the files it rolls into are too. The guard's rules stand, and `logs/` is open as the working directory it lies in is.

- **Date**: 2026-10-15
- **Status**: accepted on 2026-10-15.
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), in these places and no others:
  - §1's third bullet, "a working directory, and everything in it", and the sentence after the list: a working directory is no longer one of the places the rule closes (§2 below);
  - §2's first bullet, "outside the allow list", which no longer refuses Glob (§4) or a shell command whose every program is listed (§3);
  - §2's second bullet, "inside a working directory, wherever that is", which is struck (§2);
  - §3.3's "or inside a working directory", which is struck, and its refusal of a shell command whose current directory is outside the allow list, which no longer holds for a command of listers (§3);
  - §3.4, "A search that begins above a working directory", and the paragraphs on the walk down from a search root, which are struck (§2);
  - the alternative weighed "Reading a working directory's contents but not its documents", which this record goes past: the documents' text in a working directory is opened too.

  ADR-196's archive and seed set (§1), its allow list, its "a path outside the list is never touched" (§2), its reading of a shell command (§4), what it does not cover (§5) and every way it fails closed (§6) stand.
- **Amends**: [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), in these places and no others: §1's "and nothing else in it", as ADR-261 left it, and "the script is the only way into `vespera.db`"; §7, "`vespera.log` stays refused, including its stage lines"; and §8's K219 to K222, which now claim the call is let through. The counting command, the script, what it prints and its pin stand. The script is now one way into the counts among others, and stays admitted.
- **Amends**: [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md), in these places and no others: §1's "and nothing else" and its "The counting script of ADR-212 stays the only way into `vespera.db`"; §2's table, as the list of what the guard admits; §3; §4; §5; §8; §9's first two items; Decided here 3 to 5; and its Tests, whose K303, K305 to K309 and K501 to K514, K516 and K517 now claim the call is let through. §6 and §7 stand, and §5 below extends §7. §2's "The label file is read and not written" and "No page is written" stand as rules of conduct, no longer held by the guard.
- **Amends**: [ADR-263](0263-an-agent-may-read-the-page-the-boilerplate-floor-is-read-off.md): its admission of `document-frequency.html` by name is no longer needed, since the page is open as every file of a working directory the allow list names is. What it says the page carries, and that the floor stays the operator's, stand.
- **Amends**: [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md): its Context's "Claude is not to see anything from the operator's documents: a path, a file name, a title, an opening or a word of generated prose", which no longer holds of what a working directory carries; its "Rests on" ADR-196's refusal of everything inside a working directory, and §1's reason that an account beside `vespera.log` "would be unreadable to the agent it is for", which the guard no longer makes true. The account, where it is written and what it holds stand, and so does "The log is therefore not a thing to filter": it is not filtered, and is opened whole.
- **Keeps**: [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) whole: no agent writes into a `.claude` folder; a shell command whose text spells a closed `.claude` path is refused whatever its program; a place an allow list names for reading only is refused to every shell command; and a change to the guard is a draft the operator installs (its §7). [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md), [ADR-217](0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md) and [ADR-233](0233-the-private-paths-guard-reads-what-stands-beside-the-name-claude-a-leading-tilde-in-a-field-and-a-link-out-of-its-own-folder.md): every reading of a path the guard makes stands; what changes is what a reading is refused for. [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md): the guard never asks whether `vespera.lock` is held.
- **Rests on**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md), for the working directory kept apart from the archive and for Vespera writing nothing into the archive.

## Context

On 2026-10-15 the operator said: *"change the guards: They should only stop you from reading content of the archive, not doing everything else"*. Asked four questions, they answered:

1. **A shell command that names a path outside the allow list**: an allow list of programs. Only a command whose every program is on a fixed list is let through, and every other command that names such a path stays refused.
2. **The files in a working directory that carry document text** (`vespera.db` and its `-wal` and `-shm`, `vespera.log` and the files it rolls into, `deliverable/`, `relevance-labelling.html`, `arrangement.html`): open them. They were told these carry extracted text, titles and openings, and chose to open them.
3. **Writing into a `.claude` folder**: stays barred, as ADR-215 has it.
4. **Writing into the archive**, moving, deleting or editing a file there: stays refused.

The guard keeps no list of archives. ADR-196 §2 made it an allow list on purpose, so that an archive on a new path is refused without anyone naming it. So for the guard, **a path outside the allow list stands for one that may lie in an archive**, and "content of the archive" is the content of a file outside the allow list.

What the guard refuses today and that reads no file's content: Glob outside the allow list, which prints names; `ls` or `Get-ChildItem` of a path there; and every file of a working directory but the nine ADR-261 and ADR-263 admit by name, with every search that begins above one. What it refuses that writes nothing: running the Vespera jar on an archive, which an agent driving a run has to hand the operator as a brief.

## Decision

### 1. The rule, as amended

**No agent reads the content of a file in an archive or a seed set, and none writes there.** The guard takes every path outside the allow list for one that may lie in an archive. Of a path there it refuses what reads a file's content or may write, and lets through what prints names: Glob (§4), and a shell command whose every program is on the list of §3.

A working directory is no longer a place the rule closes (§2). The rule binds every agent and subagent, as ADR-196 §1 does, and the written rule of §5 carries what the guard does not see.

### 2. A working directory is open as the place it lies in

**A working directory under an allowed root is open to every tool as that root is**, every file in it: `vespera.db`, `vespera.db-wal`, `vespera.db-shm`, `vespera.log` and the files it rolls into, `profile.yaml`, `relevance-labels.yaml`, every page, `deliverable/`, `vespera.lock` and every `.part` file. Under a line the allow list names for reading only it is read only, and in a `.claude` folder ADR-215's rules close it as they close anything there.

**A working directory outside the allow list stays outside it**, and is refused as every path there is: it may lie in an archive. `E:/vespera_working_dir`, which `.claude/allowed-paths.local.txt` names on the operator's machine, is open by that line, as it was for ADR-261's files.

So the guard no longer asks whether a folder holds `vespera.db` or `vespera.lock`, but for ADR-212's counting command, which it still recognises by that. No search is refused for what lies beneath its root, and the walk down from a search root is not made: a working directory was all it looked for. `READ_IN_A_WORKING_DIRECTORY`, `WRITTEN_IN_A_WORKING_DIRECTORY` and the check of ADR-261 §4 go with it.

### 3. A shell command that names a path outside the allow list

A Bash or PowerShell command whose ordinary reading (ADR-196 §4, as amended) refuses it **only** for a path outside the allow list is let through when all of this holds.

**(a) Its segments.** The text is cut at `;`, `&&`, `||`, `|`, `&` and the line break, without regard to quotes, so that a separator inside a quoted name makes a segment of what follows it, which then names no listed program and refuses. An empty segment is passed over, as the one before PowerShell's call operator `&` is.

**(b) What it holds nowhere.** No `(`, `)` or backtick, which covers `$(`, `<(`, `>(` and a PowerShell sub-expression; in a PowerShell command no `{` or `}` either; and no `<` or `>` but in these forms, each whole and followed by white space, `;`, `&`, `|` or the end of the text: `2>&1` in either shell; `>/dev/null` and `2>/dev/null` in a Bash command only; and `>$null` and `2>$null` in a PowerShell command only, where `/dev/null` names a file. So `>/dev/nullx` and `2>/dev/null/../x` refuse.

**(c) The program of each segment** is its first word. An assignment before it (`NAME=value ls`), and a word that runs another program (`env`, `command`, `exec`, `sudo`), is that word and not a listed one, and refuses.

**(d) The listers.** `ls`, `dir`, `stat`, `Get-ChildItem`, `gci`, `Get-Item` and `Test-Path`, each bare: no separator in it and no quotes around it. In a Bash command the word is compared character for character; in a PowerShell command with its case folded, since PowerShell folds it. A lister takes any arguments.

**(e) The Vespera jar.** The command's one segment, with no other beside it, listers included, of these words and no others:

1. `java` or `java.exe`, bare or by a path whose last name is one of them, in single or double quotes or none;
2. `-jar`, with no option before it;
3. a word whose last name begins `vespera-` and ends `.jar`, folded to one case on Windows only;
4. `run`;
5. at most one word that does not begin with `-` or `@`, the root, and at most one `--db-dir=<path>`, in either order.

The root is refused, and the command is then not a listed one, when it holds `,`, `{` or `}`, or ends in the punctuation that ends a sentence, so that the guard's reading of it with that punctuation taken off is not the word as written. An `@` word is an argument file, which picocli expands into arguments the guard never sees (`VesperaCommand`), and a comma or a brace makes the guard read pieces of the word, each of which would be lifted as the root.

The `--db-dir` value is held to the same rule: it is refused when it holds `,`, `{` or `}` or ends in that punctuation. And the command is refused when java's word, the jar word or the `--db-dir` value, as written or in a piece at a space, is one of the root's forms or begins with one and a comma: the lift is read off the labels the guard gives a path, and a word sharing a label with the root would be lifted with it.

**(f) What is lifted, and what is not.** The refusal "outside the allow list", on every reading of a path the guard makes (as text, as walked, as Windows opens it, and "a link leads from it to a place outside the allow list"), is lifted for every word of a lister's segment, for the current directory of a command of listers, and, in the jar's command, for the root word alone: the readings of the root as written, and of each of its pieces at a space, with sentence punctuation after it or not. It is not lifted for the jar's other words, java's path, the jar and the `--db-dir` value, or for the current directory of the jar's command: Vespera writes its working directory under the current directory, as `.vespera`, or where `--db-dir` names, and a write into the archive stays refused (Context, item 4). The listers write nothing.

**(g) What still refuses** a command of listed programs, as every other: the text of a closed `.claude` folder in any token and a closed `.claude` path on any reading (ADR-215); a place named for reading only (ADR-215); a name that is there and cannot be followed (ADR-201), which may lead into a closed `.claude` folder for all the guard can tell; a name made only of dots, spaces or a stream name on Windows (ADR-217); and the bounds on the folders read against and on the readings (ADR-196 §4), past which what the command reaches cannot be ruled out. Each of these applies to a path the guard reads. From a current directory outside the allow list whose refusal is lifted, no relative token is read at all (h), so the rules on a place named for reading only and on a name that cannot be followed have nothing to apply to there; the text of a closed `.claude` folder still refuses.

**(h) No question of the file system about a path outside the allow list.** A path a lister names outside the list is passed over: not looked up, and not a folder the command names, so no relative token is read against it. ADR-196 §2's "a path outside the list is never touched" holds for these commands as for every call.

Each program, and why it is on the list or off it:

| Program | | Why |
| --- | --- | --- |
| `ls`, `dir` (Bash) | on | prints names and attributes; no option of it reads a file |
| `stat` | on | prints a file's attributes; its format options print attributes only |
| `Get-ChildItem`, `gci`, `dir` and `ls` (PowerShell) | on | return items, printed as names, sizes and times |
| `Get-Item`, `Test-Path` | on | return an item, or whether it is there |
| the Vespera jar, `run` | on | reads the archive through the local models; writes only its working directory, which (f) checks |
| the Vespera jar, `label` | off | names no archive path, so the ordinary rules decide it |
| `du` | off | `--files0-from` reads a file's text as names and prints each in an error |
| `find` | off | `-exec` runs a program, `-printf` and `-fprint` print and write |
| `echo`, `printf`, `tree` | off | not needed to list; off a list nothing is let through |
| `cat`, `type`, `Get-Content`, `head`, `tail`, `less`, `more`, `grep`, `rg`, `sed`, `awk`, `sort`, `strings`, `xxd` | off | print a file's content |
| `python`, `node`, `bash`, `sh`, `powershell`, `pwsh`, `cmd` | off | run code that reads anything |
| `cp`, `rsync`, `scp`, `curl`, `tar`, `zip` | off | copy content out |
| `mv`, `rm`, `Remove-Item`, `Move-Item`, `touch`, `Set-Content` | off | write into the archive |

### 4. A file tool or a search outside the allow list

- **Read, Grep, Edit, Write and NotebookEdit stay refused** there, as ADR-196 §2 has it: Read and Grep read content, and the rest write.
- **Glob is let through whatever it names.** It prints paths and opens no file, and its pattern is matched against names only, so no pattern makes it read content. The guard asks nothing of a Glob call but that it is one. That reaches the names in the home folder's `.claude`, which ADR-215 closes to writing; reading there was closed by the allow list, which Glob no longer consults.
- **A recursive Grep that begins above an archive begins outside the allow list**, and is refused. One that begins under an allowed root reads only under it, links aside, which the walk down never followed either.

### 5. What binds by the written rule

The guard checks paths and program names. What an agent does with what it reads, and what the guard cannot see, is conduct, stated in `AGENTS.md`:

- **No agent reads a document in an archive or a seed set**, by any route the guard does not see: ADR-196 §5's list, a path built at run time, a program that answers to a listed name but is another (an alias, a function, a file on `PATH`), or a jar that prints what it reads.
- **Text an agent reads in a working directory stays in the conversation with the operator**: what it reads in `vespera.db`, `vespera.log`, `deliverable/`, the labelling page and the arrangement page is never written into the repository, an issue, a pull request, a commit message, a record, a test or a memory file, and is never sent anywhere. ADR-261 §7 says the same of a path read from a page, and it holds too of a path an agent lists in an archive.
- **No agent writes a page, the label file's answers, `vespera.db` or the deliverable.** A page is what a stage measured, an answer is the operator's or a local model's, and the database and the deliverable are Vespera's. ADR-261 §2 held these by the guard; they are now held by the written rule. ADR-261 §6 on the profile stands.
- **The jar an agent runs on an archive is built from code no agent changed to print or send a document's text, and its models stay local.** No agent points Vespera at a hosted model by an argument, the environment or a configuration file: the guard sees the jar's arguments (§3(e)) and nothing of what Spring Boot reads.

### 6. What changes, and who puts it in place

- **`.claude/hooks/private-paths-guard.mjs`**: §2 to §4, and its header and refusal text. It lies in a closed `.claude` folder, so the operator puts it in place (ADR-215 §7).
- **`src/test/hooks/private-paths-guard.test.mjs`**: every case whose claim this record changes, each keeping its id, and the K6 cases and W10 (Tests).
- **`AGENTS.md`** states §1 and §5; **`docs/agents/private-paths-guard.md`** states §2 to §4.
- **`CONTEXT.md`**'s *Archive* says an agent reads no document there and may list its names.
- **Not** `.claude/allowed-paths.txt`, `.claude/settings.json`, the wrapper, the counting script, or anything under `src/main`.

## Decided here

1. **Outside the allow list stands for the archive** (§1). The guard keeps no list of archives, and one is not added: ADR-196 rejected it, and an archive on a new path would be open until named. The cost: what lies outside the list and in no archive, the home folder's own files among them, is refused as an archive is.
2. **Glob is let through everywhere** (§4). A name is not content. The cost: an agent can list the names in an archive and in the home folder's `.claude`, which a path rule of conduct (§5) keeps out of the repository.
3. **A working directory is open as the place it lies in, and one outside the allow list stays outside** (§2). One could sit in an archive, and opening it wherever it is would make the guard ask the file system about paths outside the list for every call, which ADR-261 rejected for the same reason. The cost: a working directory on a new path is named in the local allow list by the operator, as before.
4. **A lister is a bare word, and java may be a path** (§3(d), (e)). A lister named by a path may be any program, a script an agent wrote among them, and needs none: it is on `PATH`. The `java` on `PATH` here is too old for the jar, so the JDK's own is named by its path. The cost: a `java` an agent wrote under an allowed root passes as java; the written rule (§5) carries it.
5. **No filter after a lister** (§3(a)). `head`, `sort` and `grep` read their own options' files (`grep -f`, `sort --files0-from`), and Glob lists. The cost: `ls <archive> | head` is refused, and the whole listing is read, or Glob is used.
6. **No redirect but to nowhere** (§3(b)). A redirect reads a file into a program or writes one, and a listing is wanted in the conversation. Each form is matched whole, in the one shell where it means nowhere, so that a longer name beginning with one is not taken for it. The cost: the jar's output is not kept in a file by the command, and `vespera.log` in the working directory has it.
7. **No option before `-jar`, and after `run` only a root and `--db-dir`** (§3(e)). `-javaagent`, `-cp` and `-XX:` options load code, and `-D` or a `--spring.…` argument sets a property, among them a model's address. The CLI's own options of `run` are a root and `--db-dir` (`VesperaCommand.Run`, `WorkingDirectoryOption`). The cost: no `-Xmx`, and no `VESPERA_DOCLING_IMAGE=… java …`, so the image's name has to be in the environment Claude Code is started in.
8. **ADR-215 binds a command of listed programs as every other** (§3(g)). The operator kept it whole. The cost: `ls .claude` stays refused, and `ls` of a place named for reading only is refused while `ls` of a path outside the list is let through; Glob lists both.
9. **A name that cannot be followed stays refused** (§3(g)). It may lead into a closed `.claude` folder, and the guard cannot tell. The cost: `ls` of a broken link is refused.
10. **The guard lets a write in a working directory through** (§2, §5). The operator asked for a guard that stops reading archive content and nothing else, and what a stage measured is not the archive. The cost: an agent that writes a page or a label is stopped by the written rule alone.
11. **The jar runs alone, and its root is a plain word** (§3(e), (f)). The guard reads a command's paths out of its whole text and not segment by segment, so a lister beside the jar would carry its lift onto the jar's `--db-dir`, and a root that is an argument file, a list or a word with punctuation after it is read as pieces or as another word, each of which the lift would reach. The cost: a root whose name holds a comma or a brace, or ends in a full stop, is run by the operator.

## Consequences

- **An agent can read every file of a working directory the allow list names**, with every tool, the database, the log, the deliverable and the two pages that carry text of documents included, and search above one.
- **An agent can list an archive**, with Glob or a lister, and can **run the Vespera jar on one**, from an allowed current directory with its working directory in an allowed place.
- **Reading a file in an archive, and writing there, stays refused** to every file tool but Glob and to every shell command but those of §3.
- **ADR-212's counting script stays admitted** and is now one way in among others.
- **ADR-198's account no longer has the reason it was put outside every working directory**, and stays where it is.
- **The guard's test** claims let-through for 163 cases that claimed a refusal, 151 in its table, K412 on every platform and the eleven Glob cases of the H group, and gains 79 cases: K601 to K604, K610 to K621, K630 to K646, K650 to K660, K701 to K714, K801 to K820, and W10. The fixture gains one link, `deep/far away`, to `outside/`.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **A list of programs that read content, refusing those and letting the rest through.** The operator rejected it for an allow list of programs: a program off it, a new reader or an interpreter, would read the archive, and a list of readers is never complete.
- **A list of archives.** Rejected by ADR-196 §2 and here (Decided here 1).
- **Letting through any command whose text shows no reading program.** Rejected for (a) above: a command's text does not say what a program does with its arguments (ADR-215 §4).
- **Admitting a filter after a lister** when it names no path. Rejected (Decided here 5).
- **Keeping ADR-261's admission by name.** The operator chose to open the files that carry document text.
- **Opening a working directory wherever it is.** Rejected (Decided here 3).
- **Lifting ADR-215's rules for a lister**, which writes nothing. Rejected: the operator kept ADR-215 whole.
- **Admitting harmless JVM options**, `-Xmx` and `-Dfile.encoding`. Rejected: `-D` sets any property, and telling the harmless from the rest is a list of its own.

## What this does not decide

- **A program that answers to a listed name and is another**: an alias, a shell function, a file earlier on `PATH`. The guard reads names, and §5 carries it.
- **What the jar reads besides its arguments**: an `application.yaml` beside the current directory, the environment, and the code it was built from.
- **Whether the Grep tool follows a link** beneath its root to a place outside the allow list. The walk down never followed one, and ADR-196 §5 lists it among what the hook does not cover.
- **Whether ADR-212's script, or ADR-198's account, is still wanted.**
- **`mcp__*` tools and Monitor**, which ADR-196 §5 leaves uncovered.

## Tests

`src/test/hooks/private-paths-guard.test.mjs`, through the registered wrapper like every other case. `../outside`, from the checkout, is the fixture's folder under no allowed root, named relatively so that every platform reads it.

| Cases | What they hold |
| --- | --- |
| D11 to D18, E07, R304, R305, R307, R401, R403, R406, R408, R411 to R419, R505, N101 to N106, N108, N109, N201 to N203, N302, N501, A107 to A110, A301 to A303, A701, C102 to C104, L104 to L107, L201 to L213, G107, G204, G213 to G215, G217, G501, G502, G701, G704, G705, Z101 to Z113, Z201 to Z213, I605 | every reading of a path into a working directory under an allowed root, by every tool, is let through |
| K219 to K222, K303, K305 to K309, K412, K501 to K514, K516, K517 | every file of a working directory, to every tool and every shell command, is let through |
| N509 | a search of a folder with more than 10,000 folders beneath it is let through |
| R404, R405, R407, R410, H0106 to H1106, Q115, I405, K601, K602 | Glob outside the allow list is let through |
| K603, K604, and D02, D06, D07, R302 unchanged | Grep, Read, Write, Edit and `cat` outside the allow list are refused, a working directory there included |
| E10, R308, R501, R502, G104 to G106, G205, G401, G402, G504, A201, A202, K610 to K621 | a command of listers naming a path outside the allow list is let through |
| K630 to K646 | every other command naming such a path is refused |
| K650 to K653 | the Vespera jar run on a root outside the allow list is let through |
| K654 to K660 | every other form of it, and one that would write outside the allow list, is refused |
| K701 to K706 | the jar beside another segment, and a root or `--db-dir` value that is an argument file, a list, or a word with punctuation after it, are refused |
| K707 to K712 | a redirect that only begins with one of the forms, `/dev/null` in PowerShell, `LS` in Bash, a line break before a reader, and a PowerShell script block are refused |
| K713, K714 | a `--db-dir` value that is the root, a comma and more, and one that is a piece of a quoted root, are refused |
| K801 to K820 | each reading whose other cases are all let through since this record still refuses: a relative path after `git -C`, `pushd`, `env -C`, `Set-Location` and `cd -P`; a `..` walked out of a link, for Read, Grep and a shell command; an escaped space; an item of a brace list and of a comma list; a variable's nested word; and on Windows a name with a dot or a stream name after it |
| W10, and Q101, Q110, Q111, L304, N602, Z114, Z115 unchanged | a lister in a place named for reading only, of a closed `.claude` folder, of a name that cannot be followed or of a name of dots, and past the bounds, is refused |

**Not pinned, and why**: a separator inside a quoted name, which refuses whatever program follows it; `env`, `command`, `exec` and `sudo` before a lister, held by K645's assignment, which fails §3(c) the same way; and §5, which is conduct and not a call the guard sees.
