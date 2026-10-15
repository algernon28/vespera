# ADR-261 — An agent may read and write a working directory's profile, and read its pages of paths, counts and reasons, and nothing else in it

> **Extended — see [ADR-263](0263-an-agent-may-read-the-page-the-boilerplate-floor-is-read-off.md).** `document-frequency.html`, which stage 3 writes, joins §2's list, to Read only. The rest of this record stands.

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Amends**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), in these places and no others:
  - §1's third bullet, "a working directory, and everything in it", and the sentence after the list, which no longer refuse the files §2 below names;
  - §2's second bullet, "inside a working directory, wherever that is", which gains the one exception of §4 below, for three file tools;
  - the alternative weighed "Reading a working directory's contents but not its documents", which this record takes up for the files of §2 and for nothing else.

  ADR-196's archive and seed set (§1), its allow list and its "a path outside the list is never touched" (§2), what it does not cover (§5) and every way it fails closed (§6) stand as written.
- **Amends**: [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), in these places and no others:
  - §1's "and nothing else in it" and "The script is the only way in": the script is the only way into `vespera.db`, and §2 below names the files a file tool opens;
  - §1's refused bullet "every file in the working directory as a file", which no longer refuses `profile.yaml`, the label file or the pages §2 below names;
  - §8's K301, K302 and K304, which now claim the call is let through, and its "*Each must stay green*" for K301 to K309, which holds for the other six.
- **Keeps**: the rest of ADR-212: the one counting command, the script, what it prints, and the pin by hash. [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md): `vespera.log` is not a thing to filter, and stays refused whole. [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md): no agent writes into a `.claude` folder, so the guard change this record specifies is a draft the operator installs (§7 of that record, §9 below). [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md), [ADR-217](0217-on-windows-the-private-paths-guard-reads-every-path-as-windows-opens-its-names-without-the-dots-spaces-and-stream-name-that-end-each.md) and [ADR-233](0233-the-private-paths-guard-reads-what-stands-beside-the-name-claude-a-leading-tilde-in-a-field-and-a-link-out-of-its-own-folder.md): every reading of a path, each of which must pass §4. [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md): `vespera.lock` is not opened, and the guard never asks whether it is held.
- **Rests on**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md), for what a working directory holds; [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md), for what the profile is and how a value that cannot be used is read.

## Context

ADR-196 refuses every path in a working directory, because what is in it is derived from the documents and names them. That is true of what the working directory holds taken whole, and not of each file in it. They are of three kinds:

- **the profile**, `profile.yaml`, which the operator authors and Vespera writes back, read-modify-write (`ProfileStore`). Its keys are `seedFolder`, the floors, the models, `generationContextWindow`, `extractionAttempt`, `outOfScopeNameEndings` and `arrangementApproved`, each with the operator's provenance, and the measurements Vespera points at, each a page's path and a time. Nothing in it is taken from a document;
- **pages of paths, counts and reasons**, which Vespera writes so that the operator can supply a value or see what a stage left out;
- **files that carry text of a document**: the opening of its extracted text, its title, prose written from it, or a log line written about it.

The rule this record states is that a document may be sensitive and is read only by local models, and that it is the document's text that is barred: a path, a count or a reason Vespera wrote about it is not, and neither is the operator's own profile. An agent may not write or replace a document, or anything derived from one; it may write the profile. Today an agent that is asked to set a profile value cannot open the file, and an agent asked about a stage's review list cannot read it unless the operator copies it out of the working directory first.

### What each file carries, read from its writer

| File | Writer | What it carries | Kind |
| --- | --- | --- | --- |
| `profile.yaml` | `ProfileStore`, and the operator | the keys above, the operator's provenance, and measurement pointers (a page's path and a time) | the profile |
| `relevance-labels.yaml` | `RelevanceLabelFile`, `AutoLabelling`, and the operator | the run, embedder and seed set it was made under; for each sampled file occurrence its path, score, band, winning seed's path, the recorded answer and the local model that set it | paths and counts |
| `format-mix.html` | `FormatMixReport` | counts by detected format and subtype, by timestamp band, of what was left out, the profile's name endings, and the first four bytes, in hex, of files no signature matched, each with a count | counts |
| `extraction-failures.html`, the review list | `ReviewListReport` | each failed file occurrence's path and the verdict's reason | paths and reasons |
| `extraction-survivors.html`, the survivor list | `SurvivorListReport` | each survivor's path, page count and word count | paths and counts |
| `confidence-distribution.html` | `ConfidenceDistributionReport` | counts by confidence grade, the refused-conversion count and the extraction run's id | counts |
| `seed-corpus-comparison.html` | `SeedCorpusComparisonReport` | shares by detected language and by provenance, and quartiles of word count, page count and two ratios | counts |
| `cluster-sizes.html` | `ClusterSizeReport` | for each seed partition, the seed's path, its document, cluster and singleton counts, and three resemblances | paths and counts |
| `relevance-labelling.html` | `RelevanceLabellingReport` | each sampled file occurrence's path and **the opening of its extracted text** | text of a document |
| `arrangement.html` | `ArrangementReport` | the corpus root, each seed's path, and each cluster's **label, which is the Docling title of its lead document** (`ClusterLabel.derivedFrom`), with a link to it | text of a document |
| `deliverable/` | `Deliverable` | synthesis docs written from the documents, their titles, `index.md` and `documents.csv` | text of a document |
| `vespera.log` and the files it rolls into | Logback | lines written for an operator, some naming a file and some a failure's message (ADR-198) | text, not filtered |
| `vespera.db`, `vespera.db-wal`, `vespera.db-shm` | the application | every table, extracted text and prose among them | text of a document |
| `vespera.lock` | `WorkingDirectoryLock` | the holder's line | neither; not needed |
| `<page>.part` | each page's writer | a page while it is being written | as its page |

The review list's reason is the verdict's, and for a failure the converter answered with it carries the converter's own words about the file (ADR-255). It is Vespera's account of the file and not the file's text.

## Decision

### 1. The rule, as amended

**No agent opens a document, or a file that carries text of one.** Of a working directory, an agent may open the files §2 names, each with the tools §2 gives it, and nothing else. The counting script of ADR-212 stays the only way into `vespera.db`.

The rule binds every agent and subagent, as ADR-196 §1 does. Labels, floors and approvals stay the operator's, or a local model's: being able to write the profile does not make a value an agent's to choose (§6).

### 2. What is admitted, by name

| File | Read | Edit, Write |
| --- | --- | --- |
| `profile.yaml` | yes | yes |
| `relevance-labels.yaml` | yes | no |
| `format-mix.html` | yes | no |
| `extraction-failures.html` | yes | no |
| `extraction-survivors.html` | yes | no |
| `confidence-distribution.html` | yes | no |
| `seed-corpus-comparison.html` | yes | no |
| `cluster-sizes.html` | yes | no |

**The list is of names, not of kinds.** A page a stage writes later is refused until a record adds its name, after reading what its writer puts in it. A rule by kind, "every page beside the database", would have opened `relevance-labelling.html` and `arrangement.html`, each of which carries text of a document, and would open the next one silently.

**The label file is read and not written.** Its answers are relevance labels, which are the operator's or a local model's (ADR-196 §1), and an answer an agent wrote there would be a label the agent chose.

**No page is written.** A page is what a stage measured; it is written by the stage, and an edit to it would be a measurement nobody made.

### 3. What stays closed

Everything else in a working directory, to every tool, as ADR-196 and ADR-212 have it:

- `relevance-labelling.html`, which carries openings;
- `arrangement.html`, which carries the Docling titles of documents as its cluster labels;
- `deliverable/` and everything under it;
- `vespera.log` and every file it rolls into (ADR-198, and ADR-212 §7);
- `vespera.db`, `vespera.db-wal` and `vespera.db-shm`, but for the counts ADR-212's script prints;
- `vespera.lock`;
- every `.part` file;
- every subfolder, and every name §2 does not list, whether Vespera writes it today or not.

### 4. How the guard tells an admitted file

The guard's working-directory check (ADR-196 §2) lets a path through only when every one of these holds, on **every reading** of the path the guard makes: its text with `..` folded, the path as the file system walks it (ADR-201), and on Windows the path as Windows opens its names (ADR-217). A call is refused if any reading fails.

1. **The tool** is Read for a name of §2's first column, and Edit or Write for `profile.yaml`. Every other tool is admitted nothing in a working directory.
2. **The allow list has already admitted the path**, as for every path (ADR-196 §2). The admission lifts the working-directory refusal and nothing before it, so a working directory the allow list does not name admits nothing, and the guard asks the file system nothing about a path the allow list has not admitted.
3. **The name is the last name of the path, compared whole**: character for character, folded to one case on Windows only, where the file system folds it too, as ADR-212 §4.2 compares the script word. So:
   - **a stream name** after the name, `profile.yaml:x` or `profile.yaml::$DATA`, is another name, and refused;
   - **a dot or a space after the name**, `profile.yaml.` or `profile.yaml `, is another name as written, and refused, though Windows opens it as `profile.yaml`: the reading as written fails;
   - **an 8.3 name**, `PROFIL~1.YAM`, is another name as written, and refused;
   - **another case** is the same file on Windows and admitted there, and another name elsewhere, and refused;
   - **a `.part` file** is another name, and refused.
4. **The folder the name is directly in holds `vespera.db` or `vespera.lock`**, the two files a working directory is recognised by (ADR-196 §2), both for the path as written and for its real path, where its links lead. A file of an admitted name in a subfolder of a working directory is refused, and so is one whose links lead anywhere but directly into a working directory.
5. **The file is not there, or is a regular file with one name.** `lstat` says what the name itself is, without following it: a link of any kind, a folder, or a file with a second name (a hard link, which has no target to follow) is refused. A name that is not there is admitted, so that Write can make `profile.yaml` in a working directory that has none yet, and a Read of a page not yet written finds nothing.

A link on the way to the folder is followed, as ADR-212 §4.5 follows one: `links/to-working-directory/profile.yaml` is the working directory's own `profile.yaml` on both readings, and is admitted. A link that leads anywhere else fails item 3 or item 4 on the real path.

**The check asks the file system only about a path the allow list has admitted**, and only after ADR-196's own questions of it: whether a folder holds `vespera.db` or `vespera.lock`, and what `lstat` says of the name. It adds no question of a path outside the allow list.

### 5. Shell commands, Grep, Glob and NotebookEdit stay refused

- **A shell command** that names one of these files is refused, as every path in a working directory is. Its text does not say whether it writes (ADR-215 §4), so a `cat` of a page cannot be told from a `sed -i` of it, or from `cp` of a document over it. Read, Edit and Write each name one path, which §4 checks.
- **Grep and Glob** are refused in a working directory, a single admitted file named as Grep's `path` included. A search with a pattern names what it reaches by the pattern, and ADR-196 §3 refuses a search that begins above a working directory because it reaches into one; a list of names is what Glob prints.
- **NotebookEdit** is refused. None of the files is a notebook.

### 6. Writing the profile is bounded by the written rule, not by the guard

The guard checks which file is written. What is written in it is the agent's conduct, and `AGENTS.md` states it:

- **An agent writes a profile value only when the operator has stated it**, the value and the key, to the agent itself. It does not propose a value by writing it.
- **The provenance is the operator's**: their words, or words they approve, never an account the agent makes up of how the value was arrived at (ADR-061).
- **A label, a floor and an approval stay the operator's.** An agent never writes `arrangementApproved`, a floor, or any key whose value is a judgement, on its own reading of a page or a count; it may cite what it read and ask.
- **Not while an invocation holds the working directory.** Vespera reads the profile when it starts and writes it back read-modify-write, so an edit made during a run can be lost or can change what a later step reads. The guard cannot see the lock, and the agent asks the operator.

### 7. What an agent may do with what it reads

- **Read it, and report from it to the operator**, in the conversation with them: which files a stage left out and why, how many of each kind, which value a page asks for.
- **A path read from a page is not written into the repository**: not into an issue, a pull request, a commit message, a record, a test or a memory file. A path names a document and the archive's layout, and the repository and its tracker are read by others; a count may be cited there as ADR-212 §6 has it, and a reason may be cited without its path.

### 8. A hook that runs out of time

ADR-201 records that a hook that runs out of time lets the call through. The check of §4 adds two questions of the file system, both about a path the allow list has admitted and the guard has already asked about (ADR-196 §3). A lookup that hangs lets through the call that was asked about, as it did before this record; it adds no path outside the allow list to what a hang can let through.

### 9. What changes, and who puts it in place

- **`.claude/hooks/private-paths-guard.mjs`** gains §4's check, with the two lists of §2 written in it, and a paragraph of its header citing this record. It lies in a closed `.claude` folder, so the operator puts it in place (ADR-215 §7).
- **`src/test/hooks/private-paths-guard.test.mjs`**: K301, K302 and K304 claim the call is let through, and the K4 and K5 cases are added (Tests).
- **`AGENTS.md`** states the rules of §6 and §7 and points to `docs/agents/private-paths-guard.md`, which states §2's files and §4's admission.
- **Not** `.claude/allowed-paths.txt`, `.claude/settings.json`, the wrapper or the counting script.

## Decided here

1. **The label file is read and not written** (§2). Its paths, scores and recorded answers carry no text of a document, and reading them costs nothing the review list and the survivor list do not already show. Writing an answer would be an agent choosing a relevance label. The cost: an agent that sees an unanswered entry can only point the operator at it.
2. **A path read from a page stays in the conversation with the operator** (§7). The repository, its tracker and an agent's memory are kept and read by others, and a path names a document and the layout of the archive. The cost: an issue or a record about a file a stage left out names it by its reason and its count, never its path, and the operator supplies the path where one is needed.
3. **The allow list decides first** (§4.2). The check asks the file system nothing about a path the allow list does not name, as ADR-196 §2 has it for every path, so a hook that runs out of time adds nothing outside the list to what it lets through (§8). The cost: a working directory outside the shipped list opens these files only once the operator names it, or a folder of working directories, in `.claude/allowed-paths.local.txt`, and what lies beneath that folder outside every working directory opens with it.
4. **Grep of an admitted file is refused** (§5). Every search of a working directory stays refused by one rule, and Read reads the same text. The cost: a long review list is read a part at a time, with Read's offset and limit, and not searched.
5. **A shell command that names one of these files is refused** (§5). A command's text does not say whether it reads or writes, and Read, Edit and Write each name one path the guard checks. The cost: `cat`, `head`, `Get-Content` and `sed` of the profile or a page are refused, and the file tools are used instead.

## Consequences

- **An agent can set a profile value the operator states**, with Edit or Write, and read the profile, the label file, the review list, the survivor list and the format-mix, confidence-distribution, seed/corpus comparison and cluster-sizes pages, each with Read, in any working directory the allow list names.
- **A working directory outside the allow list stays wholly closed to file tools.** The operator names a folder of working directories in `.claude/allowed-paths.local.txt` to open those files in it; everything else beneath it that lies in a working directory stays refused, and anything beneath it that lies in none is open, as for any allowed root.
- **Some reads stay refused that a person would expect to work**: `cat`, `head` or `Get-Content` of a page, Grep of the review list, a Glob of a working directory, and Read of a page while it is being written. Read is used instead, with its offset and limit for a long page.
- **A page that carries text of a document is not opened by being useful.** The arrangement page asks for an approval and stays closed; the approval name is the operator's to give.
- **The guard's test gains 29 cases**, K401 to K412 and K501 to K517, and three of ADR-212's K cases change what they claim.
- **`AGENTS.md` no longer says that no report is read**, or that the counts are the one thing an agent may read of a working directory; it states the rules of §6 and §7, and `docs/agents/private-paths-guard.md` names §2's files and holds the hook to seven records.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **A rule by kind**: every page beside the database, or every page `ReportPage` renders. Rejected in §2: two of today's pages carry text of a document, and the next page would be opened before anyone read what it holds.
- **Admitting a shell command that reads one of these files**, in one exact form, as ADR-212 admits the counting command. Rejected in §5: Read does the same and names one path, and a command form that only reads has to be told from every form that writes.
- **Admitting the files wherever the working directory is**, as ADR-212 §4.5 admits the counting command's folder. Rejected: the check would ask the file system about paths the allow list does not name, for every Read, Edit and Write whose last name is on §2's list, an archive's files among them, and a lookup that hangs would let that call through (§8). One line of the local allow list does the same for the operator's own folder.
- **Opening the arrangement page**, since it is a gate the operator reads. Rejected: its cluster labels are the Docling titles of documents.
- **A page with the text taken out**, served to an agent. Rejected on ADR-198's ground for the log: a filter is a thing a later page's writer does not know about, and the guard cannot check what it lets through.
- **The operator copying each page out of the working directory to an allowed folder.** It needs no change, and costs the operator a step for every page of every run; the profile cannot be written that way at all.
- **The label file written by an agent.** Rejected in §2: an answer there is a relevance label.

## What this does not decide

- **A page a stage writes later**, which a record admits by name once its writer has been read.
- **Whether the arrangement can be shown without its labels**, for instance by cluster size and seed path alone. The cluster-sizes page carries that much already.
- **`vespera.log`**, which stays refused as ADR-198 and ADR-212 §7 have it.
- **A file of an admitted name whose content something other than Vespera wrote**: an operator who pastes a document's text into a provenance, or a program that writes one of these names. The guard checks the name and the file's kind, never its content, and the written rule is all there is.
- **The moment between the guard's answer and the tool's read**, in which a link or a second name can be put where the file was. It is ADR-215 §8's item, and is of the kind ADR-196 §5 lists for these files.
- **`mcp__*` tools and Monitor**, which ADR-196 §5 leaves uncovered.
- **The converter's own message in a reason**: a reason on the review list can carry Docling's free-text error message, which Vespera passes on unparsed (`DoclingError`), and nothing stops that text from quoting what it read.

## Tests

`src/test/hooks/private-paths-guard.test.mjs`, through the registered wrapper like every other case. The fixture's working directory holds `profile.yaml`, the label file and the six pages, a page's `.part` file, and a `profile.yaml` in its `deliverable/` folder; a second working directory holds a review list that is a hard link to a file outside it.

| Cases | What they hold |
| --- | --- |
| K301, K302, K304 | Read of `profile.yaml`, of the label file and of the review list is let through |
| K303, K305 to K309, unchanged | Read of the labelling page, the arrangement page, `vespera.log`, a rolled log and the deliverable's CSV, and a Grep of `vespera.db`, stay refused |
| K401 to K404 | Edit and Write of `profile.yaml`; Write where there is none yet, in a working directory whose name holds a space; and Write in a folder holding only `vespera.lock`, are let through |
| K405 to K409 | Read of the survivor list and the format-mix, confidence-distribution, seed/corpus comparison and cluster-sizes pages is let through |
| K410, K411 | Read of `profile.yaml` by a relative path from the working directory, and through a link to the working directory, is let through |
| K412 | Read of `PROFILE.YAML` is let through on Windows and refused elsewhere |
| K501 to K503 | Write of the review list, Edit of the survivor list and Write of the label file are refused |
| K504 to K509 | NotebookEdit, Grep and Glob of `profile.yaml`, and `cat`, `sed -i` and `Get-Content` of it, are refused |
| K510, K511 | Read of a `profile.yaml` in a subfolder of a working directory, and of a page's `.part` file, are refused |
| K512 to K514 | Read of `profile.yaml` with a stream name, a dot or a space after it is refused |
| K515 | Write of `profile.yaml` in a working directory under no allowed root is refused |
| K516 | Read of `vespera.lock` is refused |
| K517 | Read of a review list that is a second name of a file outside the working directory is refused. *Not started where the platform will not give a file a second name.* |

**Not pinned, and why**: an 8.3 name of an admitted file, which the fixture cannot be sure its volume makes, and which fails §4.3 as written whatever the volume does; and §6 and §7, which are conduct and not a call the guard sees.
