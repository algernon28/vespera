# Vespera

Curates a local archive into a publication-ready knowledge base.

You point it at a folder of documents and at a handful of examples of what you care about. It walks the archive, extracts the text, throws out the duplicates and the boilerplate, scores what is left against your examples, and hands you the judgement calls it cannot make for you.

**It never guesses a threshold and it never edits your archive.** Everything it decides, it records; everything it cannot decide, it measures and asks you about.

This file is how to operate the tool. For what each stage does when you run it, and how it resumes after a stop, see [`docs/running-stage-by-stage.md`](./docs/running-stage-by-stage.md). For the state of the project — what is built, what is only designed — see [`AGENTS.md`](./AGENTS.md). For why it works the way it does, see [`docs/adr/`](./docs/adr/README.md).

---

## Step 0 — name your exemplars, before you run anything

Vespera has no idea what you consider relevant, and it will not try to work it out. You supply a **seed folder**: a handful of documents that are examples of what you want kept. That is the only domain knowledge the tool takes, and everything downstream is measured against it.

Put the path in `profile.yaml` in your working directory, under `seedFolder`, beside a line saying how you chose them. The working directory is `.vespera` under the directory you run the command from, unless you move it as "Where things live" describes. Create it if it is not there yet.

```yaml
seedFolder:
  value: 'D:\archive\exemplars'
  provenance: "the twelve reports I would keep without reading them again"
```

Every key in `profile.yaml` takes this shape: the answer under `value`, and how you arrived at it under `provenance`. A bare `seedFolder: D:\archive\exemplars` is refused, and no command starts until the key is written as above. Put a Windows path in single quotes, or write it with forward slashes: inside double quotes, YAML reads a backslash as the start of an escape.

Do this first. Nothing the tool prints can ask you for it, because by the time anything is printed the first invocation has already happened — and discovering at the second invocation that exemplars were needed all along is the worst way to meet this tool.

## The path is five invocations

Getting from a folder of documents to a curated archive takes **five invocations**. Each one stops where a value is missing that only you can supply. Every stop is deliberate: the tool ends the invocation, keeps everything it learned, and tells you the one thing to do next.

| | You set | You run | It does |
|---|---|---|---|
| **1** | — | `vespera run <root>` | walks the archive, extracts the text, takes the census. Stops before deduplication, because the boilerplate floor is read off what this invocation just measured |
| **2** | `boilerplateDocumentFrequencyFloor` | `vespera run` | deduplicates, reads your seed folder, scores every survivor against it, groups them, and writes you sixty documents to judge. **Removes nothing** |
| **3** | sixty answers in `relevance-labels.yaml` | `vespera label` | records your answers. They belong to the documents, not to the run, so they survive everything afterwards |
| **4** | `relevanceScoreFloor` | `vespera run` | applies your threshold. This is the first invocation that removes anything for being irrelevant |
| **5** | `arrangementApproved` | `vespera run` | writes the connecting text over the arrangement you approved, and leaves the finished tree of HTML pages, a page for each group beside a copy of each of its documents, and each set of code copied whole, in your working directory or the folder `vespera.deliverable-root` names |

**You will be told which value is missing, every time.** You do not need this table in front of you and you do not need to know which stage you are at — read the last line of the output and it names what to set next.

**Six is what you get if you set one value per invocation.** Both of invocation 2's prerequisites, the floor and `seedFolder`, are settable as soon as invocation 1 finishes, so setting them together is what makes the path five rather than six.

**To stop being asked about each arrangement, write `auto` into `arrangementApproved`.** Every arrangement is then written over in the invocation that makes it, unread, including the new one a changed value makes, so invocation 4 goes on to write the finished tree. `arrangement.html` is still written each time. Put why in `provenance`. Remove the value, or write a name, and the next arrangement is asked for again.

## What you have to decide, and where to read it

Vespera writes reports beside the database. Each one measures something; none of them chooses for you.

| The value | Read | Which reports |
|---|---|---|
| `seedFolder` | your own knowledge of the archive | — |
| `boilerplateDocumentFrequencyFloor` | how many documents share the same passages | `document-frequency.html` |
| `embeddingModel` | whichever model you can serve locally, if you want one other than `bge-m3:latest` | — |
| `relevanceScoreFloor` | what each possible cut would cost you, in documents | `relevance-labelling.html`, `cluster-sizes.html`, `seed-corpus-comparison.html` |
| `degenerateOutputConfidenceFloor` | how well the text extraction went | `confidence-distribution.html` |
| `degenerateOutputWordFloor` | how many words each kind of file held once its text was extracted, and what each floor would remove | `word-count-distribution.html` |
| `extractionAttempt` | whether the files the converter did not answer about are worth asking again | `extraction-failures.html` |
| `arrangementApproved` | whether the groups the tool formed are worth writing over | `arrangement.html` |
| `generationModel` | whichever model you can serve locally, if you want a different one | — |
| `generationContextWindow` | how much your own machine can read in one go | — |
| `clusterPageByteCeiling` | how large each group's page is when written whole, and how many groups pass each size | `cluster-page-sizes.html` |
| `logTimestampShareFloor` | how much of each text file begins with a date or a time | `format-mix.html` |
| `codeNameEndings` | which text files, or files of no known kind whose bytes read as text, are code by the ending of their name, kept apart from the documents | `format-mix.html` |
| `outOfScopeNameEndings` | which files, of no known kind or text, you know are not documents by the ending of their name | `format-mix.html` |
| `configurationSettingShareFloor` | how much of each text file is settings, which files open with a program's configuration, and which interface definitions were kept | `format-mix.html` |
| `preferredLanguages` | which language's copy you keep of a document held in several | `translations.html` |
| `translationSimilarityFloor` | how alike two documents in different languages must be to be taken for one | `translations.html` |

`document-frequency.html` shows `boilerplateDocumentFrequencyFloor`'s measurement. For each passage of five words found in two documents or more, stage 3 counts how many documents it is in, out of the documents it measured. The floor is a share between 0 and 1 of the documents measured: a passage is boilerplate, and is left out when documents are compared, when the documents it is found in number at least the floor times the documents measured. A passage is stored as a number, not as its words, so the page shows counts and shares and no text, and names no file.

The page names the content census it was written for. Each row is a share of the documents, rounded down to the hundredth, then how many passages are found in that share, the fewest documents any of them is found in, and how many documents were measured; the third divided by the fourth is the exact share of the least-shared of them.

To choose the floor, pick a row and set the floor to the share it shows: every passage on that row and on the rows after it is then boilerplate, and none on the rows before. Taking the row's third number as k and its fourth as N, k divided by N does the same, written in full or cut off after at least as many decimal places as N has digits: for 7 of 25, 0.28. Every passage found in k documents or more is then boilerplate, and none found in fewer. Do not round k divided by N up: 2 / 3 rounded up to 0.67 leaves out the passages found in 2 of 3 documents.

Stage 3 writes the page each time it measures, and a stage 3 already recorded leaves the page as it is. Stage 3's line in the output of `vespera run` names the run it used, as `Stage 3 (content census) finished under run <id>` or `Stage 3 (content census) was already recorded under run <id>`. If that id is not the one the page names, the invocation went back to a content census recorded earlier, as putting a value back to an earlier one, or running a second archive in the same working directory, does. The page is not that run's. To read that run, use the command below, with the id at the end, after a space.

This prints the same rows as the page, for the run you name. Run it from the directory you run `vespera` from, with Python on your machine, while no `vespera` command is running and no `vespera.db-wal` lies beside `vespera.db` (if one does, *Where things live* says what to do); it reads the database without writing to it and leaves no file beside it:

```
python -c "import sqlite3,sys;db=sqlite3.connect('file:.vespera/vespera.db?mode=ro&immutable=1',uri=True);run=sys.argv[1] if sys.argv[1:] else db.execute('select r.id from run r join finished_step s on s.run_id=r.id and s.step=r.stage where r.stage=? order by r.rowid desc limit 1',['content-census']).fetchone()[0];print('run',run);[print(r) for r in db.execute('select 100*f.document_count/c.shingled_document_count/100.0 as share,count(*),min(f.document_count),c.shingled_document_count from shingle_document_frequency f join shingle_corpus_size c on c.run_id=f.run_id and c.shingle_parameter_identity=f.shingle_parameter_identity where f.run_id=? group by share order by share',[run])]"
```

If you moved the working directory, put its `vespera.db` in place of `.vespera/vespera.db`.

The first line names the content census it read: the one whose id you put at the end, or, with no id, of the content censuses that finished, the one whose run was created last. Each line after the first is a row as the page shows it, the share written as Python writes it, `1.0` for `1.00`; the rule for choosing the floor is the same. If the command stops with `TypeError: 'NoneType' object is not subscriptable`, no content census has finished in that database. If it stops with `unable to open database file`, there is no `vespera.db` where it looked, and it has made none: run it from the directory you run `vespera` from, or name the moved `vespera.db`. If it stops with `no such table: run`, the `vespera.db` it opened holds no ledger, as an empty file of that name does: check that it is the working directory's. If it prints the `run` line and nothing else, no run with that id stored any passages.

Twelve of these are **optional** and none of them is one of the five stops; all twelve are here so that you know they exist.

`degenerateOutputConfidenceFloor`, left unset, removes nothing for extracting badly. `degenerateOutputWordFloor`, left unset, removes nothing for having too few words: set it, as a whole number, to remove every file whose extracted text holds fewer words than that, whatever kind of file it is, as holding no recognisable text. A word is anything between two spaces, a year or a word in any alphabet included, counted over all of the text, page headers and footers too. `word-count-distribution.html` shows, for each kind of file, how many of the files kept hold each number of words, and how many of each kind a floor of 2 to 10, 20, 50 or 100 would remove: a one-line note, a shortcut and a picture whose only text is a button's one word go alike. `logTimestampShareFloor`, left unset, removes no logs: set it, as a share between 0 and 1, to leave out as a log every text file of ten lines or more in which at least that share of the lines read begin with a date or a time. `generationModel` and `generationContextWindow`, left unset, write the connecting text with the model and the reading window Vespera ships with — they are the values that already have answers, and setting one only replaces the answer it already had.

`clusterPageByteCeiling`, left unset, writes every group's page as one file, however large. Every time the tree is drawn, `cluster-page-sizes.html` beside the other reports shows how large each group's page is written whole, in bytes, its pictures inside it: the largest, the 95th percentile and the median, how many groups pass 100000, 1000000, 10000000 and 100000000 bytes, and the ten largest with how many documents each lists; it names no group and no document. Set it, as a whole number of bytes of 1 or more, to split every page larger than that at the end of a document's entry: the group's first page stays `index.html`, the next are `index-2.html`, `index-3.html` and on, each no larger than the number unless one document's entry alone is, and every page links the first, the previous, the next and the last. The connecting text stays on the first page, and each citation in it links to its document on whichever page lists it. Nothing is asked of the model again, and no stage runs again: the next `vespera run` that reaches the tree draws it again. A value that is not a whole number of 1 or more is ignored, and the last line says so.

`configurationSettingShareFloor`, left unset, treats no file as a configuration file: set it, as a share between 0 and 1, to treat as one every text file of ten lines or more in which at least that share of the lines read are settings (`key = value`, `key: value` or a `[section]` heading, comments not counted), and every XML file that opens with Maven's, logback's, log4j 2's, .NET's or Spring's configuration, whatever its name. One lying in a folder of code the tool keeps together is kept with that code; any other is left out. A guide about configuration, an XML Schema, a WSDL, an OpenAPI or Swagger description and a JSON Schema are kept.

`outOfScopeNameEndings`, left unset, leaves nothing out for its name. Some files are not documents and hold nothing that says what they are; `format-mix.html` counts them under *What could not be recognised*, grouped by how they begin. To leave such files out, write the endings of their names into `outOfScopeNameEndings`, each beginning with a dot and separated by spaces, with why in `provenance`, and run again. Case does not matter, and `*` is not a wildcard: list each ending. Only a file the tool could not recognise, or a text file, is left out this way; a PDF or a Word document whose name ends with a listed ending is kept. For example:

```yaml
outOfScopeNameEndings:
  value: ".P3A .P3L .P3P .P3S .P3O .P3K .P3T .DGN .LGN .PGN .SGN"
  provenance: "format-mix.html: payment terminal package and signature files, not documents"
```

`preferredLanguages`, left unset, keeps every copy of a document held in several languages. Two files whose paths are the same but for one folder named for a language, written in lowercase, such as `en/api/index.html` and `zh/api/index.html`, are one document in two languages; so are two documents in different languages, each the other's nearest in meaning among the documents of its language, that are at least `translationSimilarityFloor` alike, once that is set. `translations.html` counts them, and the languages it found. Write the languages you want kept into `preferredLanguages`, as two-letter codes, the one you prefer first, such as `it, en`, with why in `provenance`, and run again: of each document, the copy in the first of them present is kept and the others are left out. Where no copy is in a language you listed, the one with the lowest path is kept. A document with two copies in one language keeps every copy.

`translationSimilarityFloor`, left unset, leaves out no copy found by its meaning, only those found by their paths. `translations.html` has a row for each hundredth of likeness, from the least alike pair to the most, with how many pairs are on it, how many of those their paths confirm to be one document, and how many their paths refute, showing them to be two. Pick the lowest row from which on no row has a refuted pair, and set the floor to the likeness it shows: the pairs on that row and on every row after it are then taken for one document, and none on the rows before.

`extractionAttempt`, left unset, is the first attempt at extracting text from your archive. Some files may not have been read because the converter was busy, failing or too slow at the time. `extraction-failures.html` lists those files beside the ones it could not convert. To have the converter asked about them again, write `2` into `extractionAttempt`, with why in `provenance`, and run again. Files it already answered about are not converted again. Everything after extraction is redone, because what was read may have changed: deduplication, scoring, grouping and the connecting text. The groups are new, so you approve the arrangement again before anything is written. Nothing from the first attempt is deleted. Remove the value, or write `1`, and the next run is back on the first attempt, with nothing redone. If you had approved the second attempt's arrangement, write the first one's name back into `arrangementApproved` before the connecting text is written again; the last line of the output names it. Next time, write `3`. Do not delete rows from `vespera.db` to make a stage run again.

The reading window is how much of a group goes into one request. Set it larger and more of each group is read in one go; leave it alone and Vespera uses a size any machine can serve. Groups too large to fit are still written about, from the documents nearest your exemplar, and the finished page says how many of them it was written from.

Every value you set carries a `provenance` field. Write down how you arrived at the number. Nothing checks that you read the report first — what stands between a guess and your archive is what you record there.

## Invocation 3 in more detail, because it costs you two hours

Invocation 2 leaves two files beside the database:

- **`relevance-labelling.html`** — how the scores are spread, the five bands they fall into, and what cutting at each band boundary would cost you in documents kept and documents lost. Open it in a browser; every document links to the original.
- **`relevance-labels.yaml`** — sixty documents drawn evenly across the score range, each with `relevant: null` waiting for a `true` or a `false`. A document you have already answered, against the same seed folder and from the same corpus root, shows your answer instead, so a later run's file starts from what you said last time. The file names the seed folder it was written for; if you change `seedFolder` before running `vespera label`, it is refused, so run again first.

Answer them, then run `vespera label`. At roughly two minutes a document that is about two hours, and the sixty is a ceiling rather than a target: the same run always asks about the same documents, so you can stop and come back.

**Your answers outlive the run that asked.** They are keyed to the documents, so changing the embedding model later re-scores the archive and re-reads the answers you already gave, without asking you anything twice.

## Where things live

Everything the tool writes goes in one working directory, never inside your archive, except the deliverable when you name a folder for it, and one small lock file per corpus root in a folder of yours (both below):

```
profile.yaml                     the values you set, and how you arrived at them
code-set-names.yaml              the names you give your code sets, if you rename any; you or the control panel write it, the jar never does
vespera.db                       the ledger: every file seen, every verdict, every measurement
vespera.lock                     held by the command running now, or by the control panel while it saves, so a second one is refused
next-invocation.properties       what the next command wants, written as a command ends on its last line; read by the control panel
logs/vespera.log                 what the console shows, with dates; older days roll in beside it
logs/invocation-account-*.txt    one per vespera run: its steps and counts, and no document's name
progress.html                    what the command running now has done, opened as it starts
progress-steps.html              the same progress page's steps, as a table
progress-samples.html            the same progress page's samples over time, as a table
progress-runs.html               every run this working directory holds, as a table
format-mix.html                  what kinds of file the archive holds
extraction-failures.html         the files that could not be read, and why
extraction-survivors.html        the files that were read and kept
confidence-distribution.html     how well the text came out
word-count-distribution.html     how many words each kind of file kept holds
document-frequency.html          how many documents share each passage
seed-corpus-comparison.html      how far your exemplars resemble the archive
translations.html                the documents held in several languages, and how alike they are
relevance-labelling.html         the report you read to choose the threshold
relevance-labels.yaml            the sixty questions you answer
cluster-sizes.html               how the survivors grouped under each exemplar
cluster-page-sizes.html          how large each group's page is, in bytes, written each time the tree is drawn
arrangement.html                 the groups, named and in order, for you to approve
confluence-not-carried.csv       the copies a vespera publish could not send because the site refused them as too large, written only when it refused one
deliverable/<run>/index.html     what was written, group by group, and what produced it, opened when written
deliverable/<run>/documents.csv  every surviving document, with its place in the order and its copy
deliverable/<run>/code.csv       every file of every code set, with its set, its role and its copy
deliverable/<run>/code/<set>/<path>  a checked copy of each code set, its folders kept
deliverable/<run>/<exemplar>/<group>/index.html  one page per group, its pictures inside it, or its first page where clusterPageByteCeiling splits it
deliverable/<run>/<exemplar>/<group>/index-<k>.html  a group's further pages, where clusterPageByteCeiling splits it
deliverable/<run>/<exemplar>/<group>/<file>      a checked copy of each of the group's documents, under its own name
deliverable/<run>/vespera-deliverable.txt  which working directory and which run drew this folder
deliverable/<run>/confluence/pages.json  the Confluence rendering's page list: every page, its title, its body and the files it carries
deliverable/<run>/confluence/<exemplar>/<group>/page.xml  each page's body in Confluence's storage format, its pictures' files beside it; the root page's and each exemplar's lie above
```

The deliverable can be drawn in a folder of your choosing instead: name it with `vespera.deliverable-root`, in `application-local.yaml`, as the environment variable `VESPERA_DELIVERABLE_ROOT`, or with `--vespera.deliverable-root=<path>`. The folder is then the deliverable itself, `index.html` at its top, everything listed above under `deliverable/<run>/` directly in it, and nothing under the working directory's `deliverable/`. Left unset, the deliverable goes to `deliverable/<run>/` as above. `vespera run` refuses, in one line and with exit code 1, before it walks anything, a folder that is the corpus root or the seed folder, lies inside either or holds either; and a folder that already holds anything but a deliverable this working directory drew. It is compared where it really leads, links followed. A folder inside the working directory is fine. With no folder named, the working directory's `deliverable/` is checked the same way, so a working directory inside the corpus root or the seed folder is refused. Keeping it out of the rest of your archive is yours to watch: Vespera cannot see where your archive ends. Once a folder holds one run's deliverable, a later run that writes anew needs a new, empty folder: that run stops with one line and exit code 1 before drawing over it, and what it wrote is kept for when you name one. Vespera deletes nothing in the folder.

A `vespera.log` an earlier version wrote at the top of the working directory, with the files it rolled into, stays there, and so do accounts written to a folder `vespera.account-dir` named: Vespera moves nothing. Move or delete them yourself.

Every `vespera run` and `vespera label` opens `progress.html` in your default browser as it starts, and the command's first line, `Progress page: <path>`, names where it is. The progress page of `vespera run` is four tabs, `Overview` (`progress.html`), `Steps` (`progress-steps.html`), `Samples` (`progress-samples.html`) and `Runs` (`progress-runs.html`), linked from a bar at the top of each, and every tab says when the invocation started and, once it has, when it ended. While `vespera run` runs, every tab reloads itself every five seconds (`vespera.progress-interval`), and a tab that is no longer rewritten says so. `Overview` shows how many steps have finished, the step running and how far it has got, a timeline of the steps, and charts of the database's size and the running step's counts over time, with the survivors and the verdicts of the stage removing occurrences, their rates, and the database's growth; a pointer over a chart shows every series' value at the time under it. `Steps` and `Samples` hold the same as tables, and `Runs` lists every run this working directory holds, which finished, which this invocation reached and what each removed, read when the invocation starts and when it ends. The pages fill your browser's window. Below the timeline, the charts of the database's size, the running step's counts and the verdicts stand three to a row in a window at least 1,666 px wide, two to a row from 1,116 px, and one to a row below that; a chart or a table scrolls sideways only when the window is too narrow to read it. `vespera label` writes `progress.html` when it starts and when it ends, with how many answers it recorded. It names no path and no document, so it can be sent to anyone.

Set it with `--db-dir=<path>`, which must be written with the `=`, or with `vespera.working-dir` in configuration. A `vespera.working-dir` set in `application-local.yaml` or as the environment variable `VESPERA_WORKING_DIR` wins over `--db-dir`, the control panel's included, so a command given another `--db-dir` refuses: remove it to name the working directory with `--db-dir`. Both commands take `--db-dir=<path>`, so if you moved the working directory, name it on `vespera label` as well as on `vespera run`. A command given a `--db-dir` other than the directory it actually opened refuses and records nothing.

One command at a time uses a working directory. A second command started on it while the first is still running, from a second terminal or from an IDE, refuses at once with one line naming the command that holds it, its process id and when it started, and exits 1; the first carries on. Wait for the first to finish, or stop it, and run the second again. The control panel holds it too, for the moment it saves a value, answers or names into the working directory, and refuses to save while a command holds it. `vespera.lock` stays in the working directory after every command and is harmless: a command that crashed or was killed releases it as its process ends. Do not delete it while a command is running, because that lets a second one start. If a step fails saying the database file is held by another process, something other than Vespera, such as a database browser, has `vespera.db` open; close it and run the same command again.

One `vespera run` at a time walks a corpus root, whatever working directory each names. A second `vespera run` on a root the first is still walking refuses at once with one line naming the root, the command that holds it, its process id, when it started and its working directory, and exits 1, writing no progress page; the first carries on. `vespera label` is not affected. The lock is a file in `.vespera-root-locks` in your home folder, one per root, named by a code made from the root's path rather than the path itself, and the line inside it names the root; name another folder with `vespera.corpus-root-lock-folder`. Like `vespera.lock`, a file there is harmless after a command ends, a command that crashed or was killed releases it, and it must not be deleted while a command is running. If that folder cannot be written, `vespera run` refuses in one line rather than walk unlocked; make it one you can write, or name another.

While a command is running, two more files sit beside `vespera.db`: `vespera.db-wal`, SQLite's write-ahead log, which holds the most recent changes, and `vespera.db-shm`, its index. When the command ends they are folded back into `vespera.db` and deleted, unless something else, such as a database browser, still has the database open. If `vespera.db-wal` is still there when no command is running, something left it behind: a command cut short, or something else that had the database open when the last command ended, even if it has closed since. Close whatever still has the database open, then run one `vespera` command, `vespera run` again for instance, with nothing else open on the database: it opens `vespera.db` as it starts, and when it ends it folds `vespera.db-wal` back into `vespera.db` and deletes both files beside it. `vespera run` also carries on the run, and once it reaches the relevance questions it writes `relevance-labels.yaml` anew: if you are answering them, record your answers with `vespera label` first. To copy the working directory, copy it after the command has ended and `vespera.db-wal` is gone. If `vespera.db-wal` is still there, or you have to copy while a command is running, copy all three files together, or the copy is missing the latest changes. Keep the working directory on a disk attached to the machine that runs Vespera, not on a network share: the database relies on shared memory that only works on a local disk.

The database also keeps its temporary files in the working directory, not in the system's temporary folder. It writes them while a step has it sort a great many rows and removes them itself; on Windows they show there under names beginning `etilqs_`, and they are no part of a copy. They grow with the archive and can run to gigabytes on a large one, so the disk that holds the working directory needs that much free while a command runs.

## Commands

```
vespera run <root> [--db-dir=<path>]     walk a corpus and take it as far as the next missing value
vespera label [file] [--db-dir=<path>]   record the answers you wrote into the label file
vespera publish [--db-dir=<path>]        send the last Confluence rendering drawn to your Confluence Cloud site
```

`vespera publish` is the one command that sends anything off the machine, and only the Confluence rendering: it reads the site, the space, the parent, your account's email and an API token from `VESPERA_CONFLUENCE_*` environment variables, the last two from the environment alone, and refuses before sending anything when one is missing, writing `confluence-env.sh` and `confluence-env.ps1` into the working directory for you to fill in and run in your shell.

Every `vespera run` that starts the job writes an invocation account in the working directory's `logs` folder: a file with no document's name or words in it, which a hosted model may read. There is nothing to set for it. `vespera.account-dir` is no longer read: left in your configuration, it is ignored, and the run logs one warning naming the `logs` folder. `vespera.progress-interval`, set in `application-local.yaml` or with `--vespera.progress-interval=<duration>`, is how often `vespera run` rewrites `progress.html`: `5s` unless you set it. No browser is opened where the environment variable `CI` is set, or on Linux with no display.

`vespera run` takes the archive root as its argument, falling back to `vespera.corpus-root` in configuration. Given neither, it refuses rather than guessing — a census of the wrong tree reports success.

## Running it

You need Java 26 and a Docker daemon. Run every command below from the root of this repository.

**Build it.** This builds the jar Vespera runs from:

```
./mvnw package
```

**Start the sidecars, once, before the first invocation.** Vespera needs two services running beside it:

- Ollama, which serves the models;
- docling-serve, the document converter.

Nothing in Vespera starts them or stops them. That holds for the jar, for a run from your IDE and for `./mvnw spring-boot:run` alike. If your `.env` sets `SPRING_DOCKER_COMPOSE_ENABLED`, that line no longer does anything, and you can take it out. You start them yourself from `compose.yaml`:

```
docker compose -p vespera up -d --build
```

`-p vespera` names the Compose project `vespera`, whatever your checkout's directory is called. Give it on every `docker compose` command here, so that each one finds the same containers. Without it, Docker Compose names the project after the directory, and a second checkout starts a second set that fights the first for the same ports.

If your machine has an NVIDIA graphics card that Docker can use, let Ollama and the document converter run on it. Ollama runs its models several times faster there than on the processor, and the converter got through a sample of the archive two to two and a half times faster. Docker Desktop on Windows can use one through WSL 2; on Linux, Docker needs NVIDIA's Container Toolkit. Start the sidecars with a second file, `compose.gpu.yaml`, named after the first:

```
docker compose -p vespera -f compose.yaml -f compose.gpu.yaml up -d --build
```

Name both files every time you run `up`, this time and every time after. An `up` without `compose.gpu.yaml` replaces Ollama with one that runs on the processor. The models you gave it are kept, because they live in a volume of their own rather than in the container. It replaces the document converter with the processor build too. `stop`, `exec` and `down` need only `-p vespera`. Without such a card, leave `compose.gpu.yaml` out: with it, `up` stops with an error, and neither Ollama nor the document converter starts. Once a model has answered, `docker compose -p vespera exec ollama ollama ps` shows under `PROCESSOR` `100% GPU` when the model is wholly on the card, a split such as `30%/70% CPU/GPU` when only part of it fits, and `100% CPU` when Ollama is not using the card.

With `compose.gpu.yaml`, the document converter is built from the same `Containerfile` on a base that can use the card, under a name of its own. Its output differs from the processor build's in a few places, and Vespera records beside every conversion the name of the image that made it. That name is the one you give Vespera, so you have to tell it which image you started. Set `VESPERA_DOCLING_IMAGE` in the shell you run `vespera` from, before you run it. In PowerShell:

```
$env:VESPERA_DOCLING_IMAGE = 'vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2'
```

In a POSIX shell:

```
export VESPERA_DOCLING_IMAGE='vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2'
```

If you run Vespera from the IDE instead, add this line to the file `.env` at the root of this repository, creating it if it is not there. The `Local SpringApp` and `Local Vespera Label` run configurations in `.run/` read it, and `java -jar` does not:

```
VESPERA_DOCLING_IMAGE=vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2
```

The converter reports which image it runs, and Vespera checks that against the name you gave it before it converts anything. If you forget, or if you go back to `compose.yaml` alone and leave the variable set, the two do not match. The command then stops before converting a single file, and its last line names both images: the one the converter runs and the one Vespera was told. Set the variable to the image you meant to run, or start the sidecars again with the files that build the other one, and run the same command again. If you go back to `compose.yaml` alone, unset the variable and take the line out of `.env` again.

The first run after you switch to the card converts every file again, once, because conversions recorded under the processor build's name are not reused under the card's. On the card that is still quicker than finishing on the processor. Every step after the conversion runs again too. The answers you already wrote into the label file are kept, but the label file is written anew, and you are asked to approve the arrangement again. If you switch back, the processor build finds its own conversions where it left them.

To check that the converter is using the card, run this. It prints `True` when it is:

```
docker compose -p vespera exec docling-serve python -c "import torch;print(torch.cuda.is_available())"
```

To check that Vespera recorded the card's image, run this from the directory you run `vespera` from, once a run has converted something, with Python on your machine, while no `vespera` command is running and no `vespera.db-wal` lies beside `vespera.db` (if one does, *Where things live* says what to do). It prints what the newest conversion run in the ledger was keyed on, and the image is the part after `image=`. After your first run on the card, that is `vespera/docling-serve-cu128-libreoffice`:

```
python -c "import sqlite3;print(sqlite3.connect('file:.vespera/vespera.db?mode=ro&immutable=1',uri=True).execute('select config_consumed from run where stage=? order by rowid desc limit 1',['extraction']).fetchone())"
```

If you moved the working directory, put its `vespera.db` in place of `.vespera/vespera.db`.

The document converter's image is not pulled. It is built on your machine from `docker/docling-serve`, because it adds LibreOffice to the published image, so that `.doc` and `.ppt` files convert. `--build` builds it the first time and rebuilds it if its `Containerfile` has changed. The first build is the slow one. After that, Docker reuses what it built.

The services listen on ports `11434` and `5001`, which is where Vespera looks for them. If something else on your machine already holds one of those ports, the start fails and names the port.

Leave the sidecars up for all five invocations. They can be days apart.

If a sidecar stops on its own, Docker starts it again, and it does the same after your machine restarts, as long as Docker itself starts. When the document converter goes away while documents are being converted, Vespera waits up to three minutes for it to come back and then asks again about the document it was converting, so a restart of the converter does not stop the command. A document that makes the converter go away twice, or that the converter answers with an error, is marked, skipped and listed in `extraction-failures.html`, and the command carries on with the rest. If five documents in a row make it go away twice, or are turned down for the converter's own reasons, Vespera does not yet know whether the documents or the converter are to blame, so it checks the converter with a small document of its own. If the converter reads that one, the five were the documents' own doing: each is marked, skipped and listed in `extraction-failures.html`, and the command carries on. Only if the converter cannot read Vespera's own document either does the command stop, and it says so. One of your exemplars failing that way does stop the command, and the message names the file. If the converter is not back within three minutes the command stops and says so, and you can run the same command again once it is back. A sidecar you stop with `docker compose -p vespera stop` stays stopped, even across a restart of your machine.

**Give Ollama its models.** Ollama serves only the models it has been given, and Vespera does not fetch them for you:

- The embedding model has to be there before invocation 2. That is `bge-m3:latest`, unless you set `embeddingModel`. If Ollama does not serve it, invocation 2 stops at scoring with a line naming it.
- The model the connecting text is written with has to be there before invocation 5. That is `qwen3:8b`, unless you set `generationModel`.

```
docker compose -p vespera exec ollama ollama pull bge-m3:latest
docker compose -p vespera exec ollama ollama pull qwen3:8b
```

Pulling the embedding model again can change it, because a name can be published again with other contents. Vespera then treats it as another model: the next `vespera run` embeds and scores every document again, the relevance threshold removes nothing until the sample is answered again from the new label file, and the arrangement has to be approved again. Pull it again only when you mean to, and not while `vespera run` is computing vectors: if the model changes under that step, the command stops there with a line saying so, and the next `vespera run` embeds again, storing the vectors that are missing under the model as it is now.

**Run it.** Each `vespera` in this file is this command:

```
java -jar target/vespera-0.0.1-SNAPSHOT.jar
```

So `vespera run <root>` is `java -jar target/vespera-0.0.1-SNAPSHOT.jar run <root>`, and `vespera label` is `java -jar target/vespera-0.0.1-SNAPSHOT.jar label`. Unless you set it as "Where things live" describes, the working directory is `.vespera` under the directory you run the command from.

**Or build and run in one command, headless.** The `headless` profile builds the jar, skipping the tests, and runs one command of it with the Java that runs Maven, from the root of this repository. Name the command and its arguments in `vespera.args`, quoted as one value; given none, the jar runs with no arguments:

```
./mvnw -Pheadless "-Dvespera.args=run <root>"
```

On Windows it is `.\mvnw.cmd -Pheadless "-Dvespera.args=run D:\corpus"`, the quotes kept.

**Stop the sidecars when you are finished:**

```
docker compose -p vespera stop
```

This keeps the models you gave Ollama. `docker compose -p vespera down` removes the containers and keeps the models too, because they live in a volume of their own. `docker compose -p vespera down -v` removes that volume as well, and the models with it. After that, the same `ollama pull` lines as above give them back.

When `compose.yaml` has changed since you started the sidecars, for example after you update your checkout, `docker compose -p vespera up -d --build`, or the `up` line above that names both files if you started them with both, replaces the container of each service whose part of `compose.yaml` or `compose.gpu.yaml` changed. Ollama's models stay. `docker compose -p vespera exec ollama ollama list` shows which it has. Nothing in your working directory is lost.

## Starting the control panel

The control panel runs the same commands from your browser, one at a time, and shows each report in a tab of its own. `./mvnw package` builds it beside the jar, and it starts the jar beside its own with the Java that started it. The sidecars are still yours to start, as above. From the root of this repository:

```
java -jar target/vespera-0.0.1-SNAPSHOT-control-panel.jar
```

Or build both jars and start the control panel in one command, with the `gui` profile, which skips the tests and starts it with the Java that runs Maven. The control panel starts the headless jar for each command it runs, as above; `-Pheadless` stays the way to run a command without it:

```
./mvnw -Pgui
```

On Windows it is `.\mvnw.cmd -Pgui`. `Ctrl+C` ends Maven, the control panel and the invocation it started.

It prints two lines: the jar it will start and the Java it starts it with, then the address to open:

```
open http://127.0.0.1:<port>/?token=<token> in your browser
```

Open that address in your browser on the same machine, by pasting it into the address bar or clicking it in the terminal. It is good for this start only: each start picks another port and another token, and the control panel answers only a browser that came in through the address it printed. If it found no jar beside its own, it prints one line naming where it looked, and exits 1: build both jars with `./mvnw package`, and keep them side by side.

It serves until you end it, with `Ctrl+C` or by closing the terminal it runs in. Ending it that way also ends the invocation it started, if one is running, as Stop would; a forced kill of the control panel, such as `kill -9`, `taskkill /F` or End task in Task Manager, does not, and the control panel started again shows that invocation holding the working directory.

If you moved the corpus-root locks to another folder, the control panel sees that folder only through the environment variable `VESPERA_CORPUS_ROOT_LOCK_FOLDER`, set in the shell you start it from, which the commands it starts inherit. A folder you name with `vespera.corpus-root-lock-folder` in a configuration file or on the command line is one it does not see.

## Where the run ends

Approving the arrangement is the last thing you are asked for. The fifth invocation writes the connecting text over each group and leaves you a tree of HTML pages in your working directory: `index.html`, a listing of every surviving document, `documents.csv`, and beside them one folder per exemplar holding one folder per group, each named and placed the way you approved them. A group's folder holds its page, `index.html`, one file that carries every picture it shows inside it, a PNG or JPEG image among its documents shown there too, and a copy of each of its documents under the document's own file name, made safe to open on Windows and cut to 100 UTF-16 code units, which is 100 characters but for an emoji or another character outside Unicode's Basic Multilingual Plane, each counting as two, the cut keeping an extension of up to 16, and cut further where the name with `.part` after it, which is what a copy is called while it is being made, would pass 255 bytes in UTF-8, the most a name can hold on the usual file systems of Linux and macOS. Where two documents of one group would share a name, case aside, and whichever way an accented letter is written, which is how Windows and macOS compare names, the one with the lowest `occurrence_id` in `documents.csv` keeps it and each of the others takes `~` and its own `occurrence_id` before its extension, as `report~42.pdf`; a document named as the page, as the page with `.part` after it, which is what the page is called while it is being written, or as another's copy with `.part` after it takes that suffix whatever its `occurrence_id`. Each copy is made byte for byte from your archive and checked against the digest taken when the document was converted; one changed or gone since then, one the drive had no room left for, one whose name this computer will not take, or one whose name, with every suffix it took, would still pass 255 bytes with `.part` after it, is not copied, and its entry and its row in `documents.csv` say why. Every invocation that reaches the tree draws it again, without asking the model anything, and makes the copies still missing. Before the first copy a line counts every document listed and the bytes they hold, though some of their copies may stand already, and a copy found standing moves the count of documents copied on, so a tree drawn again reaches 100%. It opens `index.html` in your default browser once the tree is written, and its last line names it: *The deliverable is at `<path>/index.html`, with N groups left unwritten and M documents not copied.* A tree an earlier version drew, with its copies in a folder `documents/`, is drawn again in this layout and every document copied again; `documents/` stays, and the last line adds that it *holds an earlier layout's copies and can be deleted by hand*. Any other folder at the tree's top or in an exemplar's folder that this drawing did not write is counted there, as *N folders left by an earlier drawing can be deleted by hand*, and not named; so is any file in a group's folder that is neither its page nor one of its copies, such as a picture an earlier version wrote beside the page, as *N folders and M files left by an earlier drawing can be deleted by hand*, either count left out where it is 0. After the last copy, where any copy stands, made this time or kept from before, at an absolute path longer than 259 characters, which some programs on Windows cannot open, a warning line of its own says how many and names none: name a folder with a shorter path in `vespera.deliverable-root`. With none, there is no such line.

Each code set is copied whole to `code/<set>/` at the tree's top, its folders below the set's folder kept, each name made safe as a document's copy is; `index.html` lists the sets, and `code.csv` holds a row for each file. A set is named for its folder, or as `code-set-names.yaml` renames it, and where two names meet, case aside, each but the first takes `~` and the lowest `occurrence_id` among its files. A file of code is checked against the size and modification time recorded when the archive was walked; one that differs is `changed` and not copied. Where the run has a code set, the last line adds *, and K files of code not copied*; where `code-set-names.yaml` names folders no set of this run has, it adds *; D names in code-set-names.yaml name no code set of this run*.

A group's page opens with the heading written for it, carries the prose with every citation resolved into a link to that document's numbered place in the list below, and then lists the group entire — including the documents the writing never mentioned. Each entry in that list links to its document's copy inside the tree, so a sentence you doubt is two clicks from the document behind it, and the tree can be carried elsewhere and opened with every link still working. Where a group was written from part of its documents, the page says so and names both numbers. Where an answer was turned down, the group keeps its place and its page says plainly that nothing was written over it, rather than leaving a gap you have to notice. Once every group left unwritten is one whose answer was turned down the last two times it was asked, nothing else is missing, and writing was not stopped by five answers turned down in a row, the work is recorded as done: each such group's page says it stands without writing, running the same command again asks nothing more about it, and the pages can be published. Where `clusterPageByteCeiling` is set and a group's page would be larger, the group's folder holds `index.html` and `index-2.html` on, each listing the next of its documents, numbered on from the page before, with links to the first, previous, next and last page above and below the list; the index links the first. A page an earlier drawing wrote past the last one is counted on the last line with the other files left behind, and is not deleted.

Beside the HTML pages the same drawing writes the Confluence rendering under `confluence/`: the same groups, prose, citations, entries and pictures, as page bodies in Confluence's storage format, one `page.xml` for the root page, one for each exemplar and one for each group (`page-<k>.xml` for a group's further pages), each picture a file of its own beside its group's body, and `pages.json` listing every page, its parent, its title, its body and the files it carries with their sizes and digests. Each title ends with the group's place and the first 12 characters of the run's id, as `Payroll (001.002 · 3fa1c9e07b2d)`, so two runs never share a title, and the root page is `Vespera 3fa1c9e07b2d`. Nothing is sent: the rendering waits for `vespera publish`, below.

Those files are where the run stops. No stage sends them anywhere; the one thing that does is `vespera publish`, and only when you run it (ADR-308). Your archive is untouched throughout: each document is opened only to be read and copied, and nothing is written there (ADR-274). What you do with any of it is yours.

## Publishing to Confluence

`vespera publish` sends the Confluence rendering of the last run that drew one to your Confluence Cloud site, as pages under one page or folder you name in one space you can write to, with each group's copies and pictures as files on its page. It sends nothing else: not the HTML pages, not the database, not a log. It reads no document's text and runs no model. It is Cloud only.

**The token.** Make a classic API token for your Atlassian account at *id.atlassian.com → Security → API tokens*. A token lasts at most a year, and your organisation's admin may turn tokens off. Vespera reads it, with your account's email, from the environment and from nowhere else, and writes neither to any file or log.

**The five values**, set in the shell you publish from, for each publish:

| Variable | What it holds |
| --- | --- |
| `VESPERA_CONFLUENCE_BASE_URL` | your site's address, `https://<your-site>.atlassian.net` |
| `VESPERA_CONFLUENCE_SPACE_KEY` | the key of the space the pages go in |
| `VESPERA_CONFLUENCE_FOLDER_ID` or `VESPERA_CONFLUENCE_FOLDER_TITLE` | the page or folder they go under, by its id (the number in its address) or by its title; name one, not both |
| `VESPERA_CONFLUENCE_EMAIL` | your account's email |
| `VESPERA_CONFLUENCE_TOKEN` | the API token |

`VESPERA_CONFLUENCE_USER`, `VESPERA_CONFLUENCE_PARENT_ID` and `VESPERA_CONFLUENCE_PARENT_TITLE` are no longer read: set the names above instead. A `confluence-env.sh` or `confluence-env.ps1` already in your working directory that sets the old names is not written over; edit it, or delete it and the next publish missing a value writes it afresh.

A publish missing any of them sends nothing, names what is missing, and writes `confluence-env.sh` and `confluence-env.ps1` into the working directory where they are not there already. Fill in the site, the space and the parent in one, then run it in your shell, `. ./confluence-env.sh` or `. .\confluence-env.ps1`: it asks for the email and the token each time and writes neither down. Then:

```
vespera publish --db-dir=<path>
```

An address that is not `https`, or that carries a user, a query or a path past `/wiki`, is refused before anything is sent, and nothing is sent to any other host. A parent named by title must be the one page or folder of the space so titled.

**What it does.** It goes through the pages one at a time, parents first: it creates a page that does not stand yet, sends its files, then its body, and only then goes on to the next page, so a page never shows a file it has not been sent, save one the site refused as too large. A link to a page not created yet shows as a link to create it until that page is reached, and as a link to it from then on. Before it writes anything, it checks that no page in the space already carries one of the rendering's titles without having been made by a publish. Every page carries the label `vespera-<the run's 12 characters>`, so you can find a run's pages by it. Publishing the same rendering again updates it in place: a page whose body has not changed is not sent again, a changed one is sent as its next version, and a file already on its page at the same size and digest is skipped, a changed one replaced. Nothing is deleted: a page of the run that the rendering no longer holds is left as it stands and counted. A page in the space that carries one of the rendering's titles but was not made by a publish stops the publish, and is left as it stands. Where the site asks it to slow down, it waits as long as the site says and sends again.

**What it tells you.** First, before it sends anything, how many pages and files it is about to publish and how many bytes the files hold. Then, as each page starts, `page 3 of 74:` with its title and its files and bytes; a line for each file, `file 41 of 3509:` with its name and size, as it is sent, found unchanged or refused; and once the page's body is sent, a line saying whether the page was created, updated or unchanged and how many of its files were sent. Last, a closing line counting the pages and files. The page and file numbers tell you how far it has got even once the first line has scrolled away. A copy or a picture's file the site refuses as too large is skipped, its entry still naming it: the closing line counts those and their bytes, and `confluence-not-carried.csv` in the working directory lists each by its page, its name and its size; the next publish tries them again. Any other refusal stops the publish with a line saying what the site answered and, once it had reached the pages, which page it stopped at: the pages before it are complete, that page may lack some of its files or its body, and the pages after it are not created yet, or stand as an earlier publish left them. What was sent stays, and publishing again goes on from there.

**What Confluence does with it**, as measured on a Cloud site: a title holds at most 255 characters and is unique within a space, which is why each carries the run's characters; no page body or file up to 10 MiB was refused; and Confluence rewrites a body as it saves it, so a page read back is never byte for byte the body sent, which is why a publish compares what it sent, never what it reads back.

The control panel offers **Publish** on its invocation page once a rendering is drawn. Its child inherits the five values from the shell the control panel was started in, and the control panel neither asks for nor holds any of them.
