# Vespera

Vespera curates an unknown, multi-format local archive — hundreds of gigabytes of `.txt`, `.docx`, `.pdf` and images — into a publication-ready knowledge base. It measures the corpus before judging it, removes what is mechanically broken, redundant or topically irrelevant, and synthesises connective material over what survives. The engine carries no knowledge of the corpus's subject: the operator supplies that through a **seed set** of known-relevant documents, which also names the published taxonomy.

This file is what an agent needs to start working. Everything it points at is authoritative over it.

## The shape of the system

**A verdict ledger, not a moving pipeline.** Documents never move between stages. One table is populated once by the census; every stage *appends* verdict rows against a file occurrence. "Survivors" is a query asked of one run: the occurrences carrying no blocking verdict under that run or any run upstream of it, never a place things are put. Retuning a threshold mints a new run of that stage beside the old one, and each later stage reads the run the invocation arrived at (ADR-154). Nothing deletes the old run's rows. Its verdicts stay recorded and remove nothing from a run that does not name it upstream, so a loosened floor brings documents back, and putting the stricter value back removes them again at no cost (ADR-156, [#297](https://github.com/algernon28/vespera/issues/297)).

**Seven stages, cheapest filter first**, each defined by the verdicts it writes: census (0), byte-level reduction (1), extraction (2), content census (3), content redundancy (4), relevance (5), arrangement and generation (6a/6b). The run ends at 6b: what it produces is the deliverable — a directory of Markdown in the working directory, one file per cluster, every link in it resolving with no database (ADR-103). The files are written; nothing here carries them to a destination of any kind — no wiki, no site, no upload (ADR-101). Stages never call each other — they read and write only through the ledger.

**Two independent identities.** A **walk** owns file occurrence rows because they are filesystem observations; a **run** owns verdict rows because they are derived under a configuration. Content identity is a relation discovered over occurrences, never a collapse of them.

**Eight capability-shaped modules**, as packages under `io.algernon.vespera`: `ledger`, `corpus`, `extraction`, `similarity`, `embedding`, `synthesis`, `profile`, `pipeline`. All eight exist as packages today. The rule: **a capability module may depend on `ledger` and nothing else horizontal**, with one declared exception — `extraction` also names `corpus`'s two detection enumerations, because Docling's pipeline choice is derived from them (ADR-100); `pipeline` is the composition root and the only module that names a stage. It is also the only module that names a Spring Batch type: the survivors are an `Iterable` read a page of 1,000 at a time. **The ledger's reads in id order sort nothing**: each page of `Verdicts.survivors` and of `Occurrences.occurrencesOf` is planned by the integer primary key, `walk_id` being compared as a value so that its index is not chosen and the walk sorted on every page, and `ReadsInIdOrderSortNothingTest` holds the plans; and none of the six classes that held a set of every survivor of a run holds one now, each reading another table's rows a page of survivors at a time, asking `Verdicts.survivingAmong` about a page of occurrences, or going through the survivors as they are read (ADR-211); and since ADR-220 no class holds a collection as large as a run's survivors or occurrences but those its §9 names with the reason each must, stage 2's resume, the census's comparison of two walks, stage 4b, 5e, the relevance report and stage 2's faults reading a page at a time or asking by key, and 5f's cluster sizes, 6a and 6b held until [#472](https://github.com/algernon28/vespera/issues/472). **On disk, the statements that sort are exceptions to ADR-060's bound, each recorded with its measured size or, for three reads and ten index builds whose rows were too few, or none, to leave memory in the probe, the size of a measured statement of the same plan** (ADR-218): stage 4b's build of `shingle_by_hash` writes 91.6 bytes of temporary files for each row `shingle` keeps and as much again of write-ahead log, so the working directory's drive needs 183 bytes free for each of those rows, measured on synthetic ledgers on a solid-state disk; `EveryStatementThatSortsIsRecordedTest` holds how many statements of each shipped class SQLite plans through temporary storage, with and without `shingle_by_hash`, and which indexes `schema.sql` builds, so a class that gains such a statement, or a new index, fails it until a record has it; it holds no size, plans against empty tables, and does not see one sorting statement swapped for another in the same class or a tail appended to a statement at run time. Two statements of stage 4b's that sort are in none of ADR-218's tables and are recorded in ADR-220 §15: an occurrence's rarest shingles, bounded by one occurrence's `shingle` rows, and a page's candidate pairs, whose rows grow with the signed occurrences that share a bucket, on disk and on the heap, which is excepted as it stands by the operator's choice, with no measured size, its size and its bounded form being [#476](https://github.com/algernon28/vespera/issues/476)'s. And a table is named in SQL only by the module that owns it, which the line above each `CREATE TABLE` in `schema.sql` states, with one exception, the six counts ADR-198 lets `InvocationAccount` take; `OnlyPipelineNamesSpringBatchTest` and `EachTableIsNamedOnlyByItsOwnerTest` hold both (ADR-209).

`docs/architecture.md` §1–§2 carries the full version, including the tech-stack table.

## Where it stands today

**The cascade is built end to end.** `vespera run <root>` walks a corpus and takes it as far as the next missing value, through one Spring Batch job of fifteen steps, each behind its own gate; `vespera label` records the answers written into the relevance label file. Stages 0 to 4 judge, stage 5 measures without judging, stage 6a names and orders what stage 5 measured for the operator to approve, and stage 6b writes the deliverable under `deliverable/<run-id>/` in the working directory. What each stage judges and writes is `docs/architecture.md` §1; the invocations an operator makes, and the value each stop wants, are the README's.

**No defect is known and open against what ships, except the cases ADR-210 leaves open by the operator's decision**: two wrong readings and three limits, each stated there. A defect is opened as an issue on the tracker. A closed one is recorded by its ADR and its issue, and is not added here (ADR-216 §9); `docs/closed-defects.md` keeps, closed to edits, what this file said of the thirty-five closed before.

Java 26, Spring Boot 4.1.1, Spring Batch with `ResourcelessJobRepository` (no batch metadata tables), Spring Modulith for boundary verification only, SQLite as the single store, every vector included, with no vector database (ADR-214: Chroma was removed with the requirement for one, and a reader that wants one is a decision of its own), Docling out-of-process as the document converter (ADR-010) with Ollama as its default serving engine (ADR-012, ADR-013), picocli for a two-command CLI.

## Read before working

**`CONTEXT.md`** is binding vocabulary, not background. Name things as it names them — file occurrence, content identity, verdict, survivor, walk, walk anomaly, census, profile, gate, run, invocation. Each entry lists rejected synonyms under `_Avoid_`, and **the lists bind every name this project gives itself and nothing it renders for a reader outside it**. **ADR-122 states that rule once and enumerates both sides; read it there rather than a paraphrase here** — three paraphrases is how the memberships drifted apart in the first place. What it comes to in practice, so you know whether you need to open it:

- A name of ours uses the entry's own term, down to a test method name and a commit message, and a rejected synonym in one is a defect — unless the word names no such thing at all, as in `Collectors.groupingBy` or SQL's `GROUP BY`, which are untouched.
- Prose written for a reader outside this project is free of the lists altogether; ADR-122 enumerates the audiences, and this is deliberately not a second copy of that list. Where an entry carries a `_Renders as_` line, that is the word to use there; where it carries none, nothing is imposed.
- **Cluster** renders as *group*.

**`docs/adr/`** holds 219 decisions, ADR-001 to ADR-220, and two things about it are invisible from the files:

- **ADR-001 to ADR-049 are reconstituted records.** The original text was lost; each carries a verbatim one-line summary and nothing more. Cite them, but do not mistake a summary for the whole decision — `docs/architecture.md` §1–§2 is the fuller record for most, and every ADR names the sections that discuss it.
- **ADR-050 onward carry their own full text**: context, decision, consequences. That boundary is where `docs/decision-ledger.md`'s condensed table stops being the source.

## Where the work is

Work is charted as a **wayfinder map** on the issue tracker — one issue labelled `wayfinder:map` per slice, holding one child issue per decision, worked one per session. On a closed ticket the **resolution comment is the real spec**, so read comments rather than bodies.

**No wayfinder map is open.** Seven have closed, one per slice: the census [#1](https://github.com/algernon28/vespera/issues/1), stage 1 [#28](https://github.com/algernon28/vespera/issues/28), stage 2 [#38](https://github.com/algernon28/vespera/issues/38), stage 3 [#53](https://github.com/algernon28/vespera/issues/53), stage 4 [#65](https://github.com/algernon28/vespera/issues/65), stage 5 [#78](https://github.com/algernon28/vespera/issues/78), and the stage 6a/6b slice [#151](https://github.com/algernon28/vespera/issues/151), charted on 2026-09-12 and closed on 2026-09-20 once 6a and 6b shipped. **A closed map is still the record**: its body carries the destination, what was settled and what was ruled out of scope; each closed ticket's resolution comment carries the decision itself; and its closing comment carries the retrospective the next map should read before charting. Stage 7 never had a map and never will — ADR-101 struck it.

**So what is takeable is whatever the tracker holds**: bugs, and decisions no slice has been charted for. A map's **frontier** — open child issues with no open blocker and no assignee — is how to read one while a slice is being walked, and nothing is being walked today. **Chart a new map when the next slice of work is too big for one session**; the retrospective on [#151](https://github.com/algernon28/vespera/issues/151) says what the last seven learned, and this sentence and the one above it are what the claims guard checks, so a new map means editing both.

## Building and testing

```
./mvnw test                                                    # unit tests only, no Docker needed
./mvnw verify                                                  # unit tests, then integration tests (*IT)
./mvnw test -Dtest='WalkTest' -DfailIfNoSpecifiedTests=false   # one class
./mvnw -q test-compile                                         # compile only
```

- **A test needing an external tool is an integration test**: named `*IT`, run by failsafe under `./mvnw verify`, excluded from surefire's `./mvnw test`. There are ten — `CliExitIT`, `PackagedJarIT`, `ProfileThatDoesNotLoadIT`, `WorkingDirectoryInUseIT`, `ClusterSynthesisIT`, `DoclingClientIT`, `OllamaClientIT`, `RelevanceReportIT`, `ThinkingModelWritesNoCitationIT`, `VesperaApplicationIT` — and the first four need no Docker daemon, only `verify`: `CliExitIT`, `ProfileThatDoesNotLoadIT` and `WorkingDirectoryInUseIT` launch the packaged jar and `PackagedJarIT` reads it; the other six each need a Docker daemon, starting their sidecar through Testcontainers. Every other class needs neither Docker nor `verify`.
- **A skipped test is not a passing test.** Several abort by assumption when the environment cannot create a symlink or an unusual filename, so report `Skipped` alongside `Tests run`.
- **Surefire's console output truncates the cause.** The real stack is in `target/surefire-reports/<class>.txt`.
- **What a test logs is not on the console** (ADR-194). Surefire and Failsafe write each class's output to `target/surefire-reports/<class>-output.txt` and `target/failsafe-reports/<class>-output.txt`, so a green build prints no test's errors or stack traces. A failing test's assertion still prints on the console; what it logged is in that file, and in CI in the `test-output-linux` or `test-output-ntfs` artifact of the failed job. Add `-Dmaven.test.redirectTestOutputToFile=false` to put it back on the console for one run. An IDE running JUnit itself, as IntelliJ does unless told to delegate to Maven, shows everything.
- **The build needs Java 26 on `PATH` (or `JAVA_HOME`) — the shell default may be older.** `./mvnw` uses whatever `java` it finds first; an older default fails with `class file version ... only recognizes class file versions up to ...` before any test runs.
- **Test configuration is `application-test.yaml`, under the `test` profile**, so it layers over `src/main/resources/application.yaml`. Naming it `application.yaml` would shadow the main file entirely — same classpath resource name, test-classes first — and settings there would silently stop applying.
- **A test that sets SQLite's directory for temporary files puts the default back, before and after each test** (ADR-211 §12). `PRAGMA temp_store_directory` changes one variable of the whole process, and SQLite's documentation forbids changing it while another connection is open, as the connections of the contexts cached across test classes are. `TemporaryFilesInTheWorkingDirectoryTest` changes it all the same, and that is tolerable only while the suite runs one test at a time in one JVM and no pool of the test profile calls SQLite from a thread of its own. Three settings keep the second so: `max-lifetime: 0` and `idle-timeout: 0` in `application-test.yaml`, and `keepalive-time: 0` in `application.yaml`, which the test profile inherits. `NoPoolSetsSqlitesTemporaryDirectoryTest` holds the three on the pool a test context builds; nothing holds the first, so giving surefire `parallel`, `forkCount` or `reuseForks`, or adding a `junit-platform.properties`, means giving that test a JVM of its own. No test context hears the listener that sets the directory when the application starts: `VesperaApplication.main` registers it, and a test context is not built by `main`.
- **`./mvnw verify` leaves `reports/report_<datetime>.html`** — one self-contained Allure page per run, covering both suites, stamped so runs do not overwrite each other. Gitignored, and it outlives a `clean` because it is not under `target/`.
- **The four conventions below are ADR-052**, which also explains the two Allure version lines and
  why the Doxia site wrapper `allure-maven` renders is accepted.
- Assertions are AssertJ, and **every assertion sits inside `TestSteps.claim(...)`**, which names
  it as one report step. The claim is the only place the wording lives, and it has to explain any
  number it mentions — a step reading `has size 1` leaves a reader asking where the 1 came from,
  so name the test's magic numbers as constants and say what they are. `allure-assertj` is
  deliberately not a dependency: it derives steps from the fluent calls instead, which reports
  what ran rather than what was claimed.
- **Report-visible text stands on its own.** `@DisplayName`, `@Story`, `@Epic`, `@Feature`, the
  claims, and the category names in `allurerc.mjs` are read by people with no access to this
  repository, so they carry no ADR id and no phrase that needs `CONTEXT.md` to parse. Cite the
  decision in the javadoc instead, where the reader is looking at the code. Where a term would be
  read wrongly rather than merely be unfamiliar, use the plain word its `CONTEXT.md` entry states
  under `_Renders as_` — *group* for a cluster (ADR-122). The identifiers beneath that text are not
  report-visible and get no such licence.
- **The decision and the ticket travel as links, not as text.** `@Link(type = "adr")` names the
  ADR a test exists because of, with the URL taken from `Adr` (the id-to-file map, since an ADR
  file is `NNNN-its-title.md` and the id alone does not give the path); `@Issue("6")` names the
  wayfinder child issue, resolved by `allure.link.issue.pattern` in `allure.properties`. Both go
  on the class, or on the one test they belong to. A test with no decision behind it is the thing
  to notice: every test should be there because of one.
- **Every test class carries `@Epic` and `@Feature`, every test `@Story` and `@DisplayName`.**
  The report tree groups on those three labels (`groupBy` in `allurerc.mjs`) instead of folding on
  the package and class name, and the failure categories match on `@Feature`, so a test that
  ships unlabelled falls out of both the tree and its category.

## The operator's documents are never read by an agent

**No agent opens an archive, walked or not, a seed set, a working directory, or anything in them**: no document, no `vespera.db`, no `vespera.log`, no report and no deliverable. The archives can hold sensitive documents, and a document is read only by the local models Vespera runs (Docling, and the models Ollama serves). This holds for every agent and subagent, whatever its task, including a run it is driving: labels, floors and approvals are the operator's, or a local model's ([#423](https://github.com/algernon28/vespera/issues/423)), never an agent's. The one thing an agent may read of a working directory is the counts a pinned script prints (ADR-212, below).

`.claude/hooks/private-paths-guard.mjs` enforces it for eight tools: Read, Grep, Glob, Edit, Write, NotebookEdit, Bash and PowerShell. It refuses a call that names a path outside `.claude/allowed-paths.txt`, or inside a folder holding `vespera.db` or `vespera.lock`. In a shell command it reads drive paths, relative paths and paths headed by a variable of the environment, and the command's current directory; a relative path is read against every folder the command names, so `cd` to a folder the hook reads does not get round it. On Windows it reads every path once more as Windows opens its names, each without a stream name after a colon and then without the dots and spaces that end it, so `wd.\report.html` is `wd\report.html`; and it refuses there a name made only of dots, spaces or a stream name, so a command whose text holds `a/.../b`, `./...` or a bare `...`, or a token that begins with a colon, as `'{"n":1}'` and `sed ':a'` hold one, is refused on Windows, and that text goes in a file; a quoted blank argument, as in `tr '\n' ' '`, is not. It also refuses a Grep or Glob that starts in a folder holding a working directory at any depth, so name `src` or `docs`, not the repository root, once a run has written `.vespera` there. It is an allow list, so an archive on a new path is refused without being named. A refused path that is legitimate and holds no document goes in `.claude/allowed-paths.local.txt` (gitignored), by the operator. The hook does not see an `mcp__*` tool, Monitor, a path a script builds at run time, or a rooted POSIX path such as `/tmp/x` in a shell command; this rule covers those, and ADR-196 lists the others that are known, which is not all there are.

ADR-196 is the record: what the hook must refuse, how it fails closed, and what it does not cover. ADR-201 amends it for links, and for four spellings it read wrongly. ADR-215 amends it for the `.claude` folders (below), and ADR-217 for the names Windows opens as other names. ADR-212 amends it for one thing: **an agent may read the counts one pinned script prints about a working directory, and nothing else in one.** The hook admits exactly this command, and reads every other as before:

```
node .claude/hooks/working-directory-counts.mjs <working directory>
```

It is three words and nothing else: no pipe, redirect, second command, option, variable or `..`, the last two words bare or single-quoted, run from an allowed current directory. On Windows neither word begins with `/` or `\`, so name a drive path or a relative one. The script must be the one beside the guard, with the bytes the guard pins by SHA-256, so a change to the script is a change to the guard. It prints counts and sums keyed by walk id, run id and closed vocabularies, and how many files the working directory holds and their size: no path, no file or folder name but a run id's, and no text of the operator's. A count may be cited and compared; it is never a label, a floor or an approval, and a statement an agent writes is refused even when it returns only numbers.

**No agent writes into a `.claude` folder either** (ADR-215): not the home folder's, not this checkout's, not a worktree's. What is in one decides whether the hook runs and what it admits: `settings.json` and `settings.local.json`, `hooks/`, `allowed-paths.txt` and `allowed-paths.local.txt`, and the agent definitions, workflows and skills, each of which can register a hook or start a session. Three names stay an agent's to write beneath, in any `.claude` folder: `projects`, `plans` and `worktrees`. What that opens is `~/.claude/projects/`, which holds the memory directory and a session's transcript, `~/.claude/plans/`, and what lies beneath `.claude/worktrees/` outside each worktree's own `.claude`; a checkout's `.claude/projects/` and `.claude/plans/` are open too, and hold nothing a session obeys. `~/.claude/skills/` and `~/.claude/plugins/` are read and not written, and so is everything else in a checkout's `.claude`, with Read, Grep and Glob. A shell command is refused when any token of its text spells such a path, whatever heads the token, since its text does not say whether it writes: so stage with `git add -A`, and write a commit message that names one with `-F`. The counting command above is the one shell command that may name a path in `.claude/hooks`: the hook recognises it before it reads any path or text of it, and it is not admitted from a current directory that is itself a closed `.claude` folder. The counting script lies in that closed folder, so no agent edits it, which is what its pin wants. Nothing else under `~/.claude` is read at all. A change to any of these files is written as a draft outside every `.claude` folder, held with `VESPERA_GUARD_DRAFT=<folder> node --test src/test/hooks/private-paths-guard.test.mjs` when it is the guard's, and put in place by the operator, who is handed the command. The hook does not see a path a command builds at run time or a program that writes these files without naming them, `git checkout` and `git apply` among them; this rule covers those, and ADR-215 §8 lists the others.

`src/test/hooks/private-paths-guard.test.mjs` holds the hook to all five records and `src/test/hooks/working-directory-counts.test.mjs` holds the script to ADR-212, with no path of the operator's in either:

```
node --test src/test/hooks/private-paths-guard.test.mjs
node --test src/test/hooks/working-directory-counts.test.mjs
```

## Conventions worth knowing

- **The pom carries what a recorded decision requires** (ADR-046), not what current code happens to use. A new dependency wants a decision behind it.
- **A javadoc states its class's own contract and cites the ADR it implements; it does not restate that ADR** (ADR-216). A javadoc that disagrees with its code is corrected in a change that already re-mints its module, or at any time in `ledger` or `profile`, which no stage's version names (ADR-216 §7).
- **The module rule binds only where it is declared.** `ApplicationModules.verify()` constrains a module that declares `allowedDependencies`; an undeclared one is wide open, since the attribute defaults to `*`. `ModuleBoundariesTest` fails when a module ships without a declaration.
- **Census is Windows-first.** Its identity rules rest on measured NTFS behaviour — case folding that disagrees with the JDK, filenames with no UTF-8 encoding, reparse points — so some tests are guarded to Windows and say so.
- **Measure rather than argue.** Decisions here are settled by execution where execution is possible, and the measurement belongs in the record. Probes are throwaway and live outside the repository.

**`README.md` is for the operator; this file is for you.** It documents how the tool is driven — the five
invocations, which value each stop wants, which report informs it. It must carry no claim about
project state: what is built, how many ADRs, how many tests, which stage is part-built all live
here, and two files describing the state is the drift that #132 and #134 each cost a pull request
to correct (ADR-098).

`docs/check-claims.mjs` reads both documents and checks each for its own kind of claim. The
README's are all derived from code — the subcommands, the profile keys, the files written beside the
database — except the invocation count, which is checked against the table underneath it, because
nothing in the tree can produce it.

## The documentation site

`docs/architecture.md` and `docs/decision-ledger.md` are the sources; the `.html` beside them is generated and published by GitHub Pages at [algernon28.github.io/vespera](https://algernon28.github.io/vespera/).

**Why generated at all:** Pages runs Jekyll, which only converts Markdown carrying YAML front matter. These files have none, so Pages would serve them as raw text. The HTML is what actually renders.

```
node docs/render-docs.mjs           # rewrite both pages
node docs/render-docs.mjs --check   # exit 1 if the committed HTML is stale
```

Edit the Markdown, re-run the script, commit both. Editing the HTML directly is lost at the next render.

What the renderer does beyond formatting:

- **It refuses to guess.** It supports exactly the constructs these documents use and throws with a `file:line` on anything else — an image, a nested list, a code fence that is not `mermaid`. A renderer that silently drops a section and leaves a plausible-looking page is the one failure that matters here, so it stops instead.
- **It asserts nothing was lost**, checking that every word of the Markdown survives into the page. That check has already caught a real defect: a placeholder bug that swallowed code spans and turned "all 49 decisions" into one merged token.
- **It links the record together.** Every `ADR-NNN` mention becomes a link to that record, with the id-to-file map read out of the ledger table rather than hardcoded, and relative `.md` links are pointed at whichever page renders them.
- **Diagrams are Mermaid** in fenced blocks, so they render natively on github.com and client-side on the published page. `docs/extract-diagrams.mjs` pulls each one into a standalone file so a parser can check it.

`.github/workflows/docs.yml` runs both guards on any change under `docs/`: the staleness check, and `mmdc` over every diagram — because a broken diagram renders as an error box while every text-level check still passes.

## Agent skills

### Issue tracker

GitHub Issues on `algernon28/vespera`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical roles, unrenamed. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` plus `docs/adr/`. See `docs/agents/domain.md`.

## Delegation

Prefer the subagents in `.claude/agents/` over working in the main session: each is constrained in ways the main session is not, and the constraints are the point. Work here when the task is a question, a one-line answer, or smaller than the handoff costs.

**Reach for one automatically, without waiting to be asked, whenever a task matches its shape:**

- **`analyst`** — a decision is undecided: a wayfinder ticket, a fuzzy requirement, a threshold or verdict with no source behind it. Settles it, records the ADR/spec, writes the pinning tests. Never production code.
- **`spec-implementer`** — a decision is already settled (a closed ticket, a recorded ADR) and what's left is `src/main` code.
- **`tester`** — after any change, to establish whether the tree is actually green, or to find which recorded decisions nothing defends. Read-only.
- **`debugger`** — something is reported broken, flaky, or slow and the cause isn't already obvious.
- **`architect`** — before anything lands on `main`: a diff, branch, or PR ready for review.

`.claude/workflows/settle-and-land.mjs` chains all five (settle → implement → verify → repair → gate) for a work item end to end; reach for it directly rather than re-deriving the sequence by hand.

**No agent commits, pushes or merges without explicit approval.** Finish the work, leave the working tree for review, and report what you would commit and why. This holds even when a task seems to imply it — a pull request, a merge, a release — and it holds for `architect`, whose merge is a commit to `main` like any other.
