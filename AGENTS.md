# Vespera

Vespera curates an unknown, multi-format local archive — hundreds of gigabytes of `.txt`, `.docx`, `.pdf` and images — into a publication-ready knowledge base. It measures the corpus before judging it, removes what is mechanically broken, redundant or topically irrelevant, and synthesises connective material over what survives. The engine carries no knowledge of the corpus's subject: the operator supplies that through a **seed set** of known-relevant documents, which also names the published taxonomy.

This file is what an agent needs to start working. Everything it points at is authoritative over it.

## The shape of the system

**A verdict ledger, not a moving pipeline.** Documents never move between stages. One table is populated once by the census; every stage *appends* verdict rows against a file occurrence. "Survivors" is a query over occurrences carrying no blocking verdict, never a place things are put. Retuning a threshold is a `DELETE` of one stage's rows plus a re-run.

**Seven stages, cheapest filter first**, each defined by the verdicts it writes: census (0), byte-level reduction (1), extraction (2), content census (3), content redundancy (4), relevance (5), arrangement and generation (6a/6b). The run ends at 6b: what it produces is the deliverable — a directory of Markdown in the working directory, one file per cluster, every link in it resolving with no database (ADR-103). The files are written; nothing here carries them to a destination of any kind — no wiki, no site, no upload (ADR-101). Stages never call each other — they read and write only through the ledger.

**Two independent identities.** A **walk** owns file occurrence rows because they are filesystem observations; a **run** owns verdict rows because they are derived under a configuration. Content identity is a relation discovered over occurrences, never a collapse of them.

**Eight capability-shaped modules**, as packages under `io.algernon.vespera`: `ledger`, `corpus`, `extraction`, `similarity`, `embedding`, `synthesis`, `profile`, `pipeline`. All eight exist as packages today. The rule: **a capability module may depend on `ledger` and nothing else horizontal**, with one declared exception — `extraction` also names `corpus`'s two detection enumerations, because Docling's pipeline choice is derived from them (ADR-100); `pipeline` is the composition root and the only module that names a stage.

`docs/architecture.md` §1–§2 carries the full version, including the tech-stack table.

## Where it stands today

**The cascade is built end to end**: stages 0 to 4 judge, stage 5 measures without judging, stage 6a names what stage 5 measured without judging either, and stage 6b writes the deliverable the run ends at. What exists in `src/main`: `ledger` (occurrence and run identity, the verdict vocabulary, the survivors reader, one schema version per module), `corpus` (the walk, its anomalies, its resumable recorder, and content identity), `extraction` (the Docling client, chunking and its structureless fallbacks, confidence and degeneracy), `similarity` (shingles, MinHash signatures, document frequency, redundancy resolution), `embedding` (the Ollama client, the vector and score caches, relevance scoring, the seed–corpus comparison, clustering), `synthesis` (a cluster's derived label, the order the arrangement is given, the cluster rows that order is written into, the exemplar-first call and the four checks on what comes back, the documents that call carried under the ordinals it minted, the fault a turned-down answer leaves, and the Markdown tree all of it is written into), `profile` (`profile.yaml` as typed records), and `pipeline` (the picocli CLI and one Spring Batch job, fifteen steps long). `vespera run <root>` walks a corpus and writes it to SQLite in the configured working directory; the root falls back to `vespera.corpus-root` when the command names none, and an invocation with neither refuses (ADR-066). Stage 5 extracts the seed set, compares it against the corpus, embeds, scores, applies the relevance floor and clusters each seed's partition; `below-threshold` is written where a threshold is set on the run's own scale, and the labelling page states what each candidate cut would cost. Stage 6a names each cluster after its own highest-scoring document, gives the arrangement its order, and writes `arrangement.html` for the operator to approve by name. Stage 6b opens over the approved arrangement, sends one exemplar-first call per cluster, verifies every answer before believing it, records a turned-down one as a `cluster_fault` against that cluster rather than stopping the run — five consecutive ones do stop the step — and writes the deliverable: a Markdown tree under `deliverable/<run-id>/`, an index and a manifest at its root and one file per cluster beneath a directory per seed partition, every citation resolved into a link to the membership entry of the document it was written from — the ones the call carried are numbered first, from the record of what it sent (ADR-133) — and every entry into the archive by a path relative to the page that carries it, or, where no relative path exists, stated without a link (ADR-135). A re-run under the same id repairs what faulted rather than regenerating what succeeded — **except for a cluster nothing fits into**, which is never called for at all (ADR-121): it earns no fault row, its hole is the missing `synthesis_doc` row against 6a's `cluster` row, and no later invocation can close it, so this step never records completion for that run. ADR-121 accepts that permanence rather than mitigating it, and widening the window is not the repair, since the window is consumed into the run's own id. The CLI is two commands: ADR-101's follow-up removed `vespera publish`, so the tree is left on disk and nothing here takes it anywhere.

**No defect is known and open against what ships.** Nine were. The most recent is a rendering finding ([#261](https://github.com/algernon28/vespera/issues/261)): a backtick is legal in an NTFS name and no rule escaped one. It was opened as a `marked`-only finding — a membership entry for a document whose name carries one backtick prints as its own source in `marked` and is a link in the other six configurations — and measured in every position it turned out to be half of one: **two backticks in one value are a code span in all seven configurations**, in every heading, cell and membership entry, so both characters leave the rendered name with nothing to say they were there, and a bracket between them is shown with the backslash another rule wrote in front of it, in every configuration but `marked` inside link text, because no escape is processed inside a code span. It is closed by ADR-148 and the change that ships with it: the backtick is escaped as `` \` `` in the cell, the heading and the membership entry, unconditionally, on the pair and not on `marked`; the CSV is untouched and the destination already carries `%60`. **Its other half is the part that generalises**: a rendering hazard present only in one renderer that diverges from the specification is recorded and moves no rule — `marked`'s refusal of a lone backtick in link text would have been declined under it, and `marked`'s image inside escaped link text, which ADR-138 recorded and left undecided, is declined under it, leaving ADR-103's no-network claim residual in `marked` on purpose until someone measures that an application operators really use is the divergent one. The one before it is not a rendering finding: the CLI finished its command and then never exited, a JVM held open by a scheduler this application never asked for ([#269](https://github.com/algernon28/vespera/issues/269)) — and it is closed by ADR-141 and the change that ships with it: the process exits with the command's exit code, and the scheduler is dropped at its source. The one before that is not a rendering finding either: stage 2 waited on a fixed two-second `docling-serve` tick once per file occurrence and waited on it one occurrence at a time, which on the 42,851-file folder this tool exists for is roughly twenty-four hours of wall clock spent on the tick before a second of conversion, and was more than half the wall clock of the 129-file run it was measured on ([#264](https://github.com/algernon28/vespera/issues/264)) — and it is closed by ADR-140 and the change that ships with it: eight conversions in flight as a fixed default in code, "consecutive" redefined as consecutive on the drain every completed conversion is observed at, ADR-071's synchronous call shape stated to stand rather than left assumed, and ADR-127's single writer kept by parallelising only the Docling call — a worker runs that call and nothing else, the cache lookup before it and every write after it staying on the one thread the step runs on, so no worker ever holds a connection and the two streak counters are observed by one thread by construction. **What made the read-ahead free is Spring Batch's own chunk loop**, which reads a whole chunk before processing any of it, so a chunk's worth of calls is dispatched — two whole waves of eight, the chunk having been sized to the width — before its first verdict is decided, and nothing holds more than a chunk ahead — the shape that drained the entire corpus before writing a verdict was built first and refused, because on that folder it holds every conversion in memory at once and lets a sidecar that died on the tenth call be sent all 42,851 before the breaker can see one. And the one before that is not a rendering finding either: a document the converter refuses to open was skipped by stage 2 and left no `extraction_metric` row and no verdict, so nothing removed it, it stayed a corpus survivor, and stage 5 threw on it and failed the whole invocation — found on the first real corpus this tool has been run against, where eight legacy compound-format files did exactly that, while the seed side records the very same converter response as an unusable seed and carries on (ADR-083) ([#265](https://github.com/algernon28/vespera/issues/265)) — and it is closed by ADR-139 and the change that ships with it: a refused conversion leaves an `extraction_fault` row, a step that completed resolves every fault under its own run into `extraction-failed` with the reported category in the reason, ADR-071's streak goes on counting exactly what it counted, and stage 5's assertion message stops naming the one rule the measurement exonerated — **the second route the ticket suspected does not exist**, tier 1 having removed all 22 of the occurrences it was inferred from, measured. The remaining five are rendering findings, and one measurement of how the deliverable actually renders (`docs/research/deliverable-markdown-rendering.md`) closed the last four of them. The first — a citation's ordinal and the membership list's numbering were not the same numbering wherever a member was dropped between the cluster and the call, so a citation could lead to the wrong document ([#236](https://github.com/algernon28/vespera/issues/236)) — is closed by ADR-133: `synthesis`'s fourth table records which documents each call carried under which ordinal, and a cluster file numbers its membership from that record. The second — a membership entry's `file:` link was not a link at all in `markdown-it` or on GitHub, the scheme refused and no anchor emitted, so the filesystem was never asked ([#253](https://github.com/algernon28/vespera/issues/253)) — is closed by ADR-135: an entry links by a path relative to the page that carries it, measured to resolve in seven renderer configurations of seven against two of seven for `file:`, and where no relative path exists it states the document and links nowhere. The third — `<` and `&` reached every renderer unescaped, so a title reading `<b>Retrofits` went live and one reading `<draft>` was deleted outright by GitHub's sanitiser with nothing left to say a word was removed ([#251](https://github.com/algernon28/vespera/issues/251)) — is closed by ADR-136, and **what closed it is a distinction three successive readings of that ticket collapsed**: `\<` and `\&` are backslash escapes, not the HTML entities `&lt;` and `&amp;`, and they behave differently. Writing the entity is wrong, as the ticket's own cost argument and the first measurement both found; writing the backslash is correct in all seven configurations measured, costs a plain-text reader two more characters of a kind ADR-134 already writes, and closes the entity-decoding case of `&copy;` in a value a reader sees as a side effect — in a link destination, where no backslash escape applies at all, the same run needed a different operation, and that is the fourth below. The heading became a fourth surrounding under ADR-134's own rule, being the one position that escaped nothing at all. The fourth — a membership entry's destination was composed through `java.net.URI`, which quotes a space and a `#` and leaves `&` alone, so a document named `reports/&copy; notes.pdf` was listed under the name the archive really holds while its link routed to `reports/© notes.pdf` in seven renderer configurations of seven, six of them decoding the entity reference and `marked` passing it into the `href` for the browser's attribute parser to decode ([#259](https://github.com/algernon28/vespera/issues/259)) — is closed by ADR-137: a destination's `&` is percent-encoded as `%26`, joining the `%28` and `%29` already written there for the same kind of reason, and **the destination is counted as a fifth surrounding**, its rule escaping by percent-encoding because it answers to a resolver where the three Markdown positions answer to a reader and the CSV to a parser. **The entry named the right document and led somewhere else**, which is the failure mode hardest for a reviewer to notice, and three rounds of measurement missed it for two reasons worth keeping: the destination was the one position no record had enumerated, so the sweep that closed #251 never reached it, and the fixture that would have shown it carried an invented `%26` until it was regenerated by running the shipped writer. The fifth — a bracket was escaped only where the tool was itself composing a link, so a document's own title or a model-written cluster title reading `[text](url)` was a live link in an ATX heading and in a plain table cell in seven renderer configurations of seven, and one reading `![alt](https://host/x.png)` was an image GitHub rewrites through its camo proxy and serves from its own origin, which is a network request out of a tree ADR-103 says resolves every link with no database, no ledger and no network ([#258](https://github.com/algernon28/vespera/issues/258)) — is closed by ADR-138: `[` and `]` are escaped in the heading and the cell too, unconditionally, and `asLinkText` stops escaping them a second time because `inACell` now does — a composition failure measured in six configurations of seven, where the doubled escape leaves a live `[` that forms an inline link inside the cell to the very host the value named — so the naive shape destroys the index's only navigation **and** reopens #258 in the same row, which is ADR-134's own argument reaching a rule it was not written about. **That behaviour was older than its ticket, and one sentence of the record was not**: ADR-134 guarded the bracket exactly where a link is formed, coherent on what it then knew, and ADR-136 asserted the hazard away while justifying the shape of its own rule, carrying a second false sentence beside it — `onOneLine`'s callers were said to divide where they converged, it having exactly two, both escaping rules and both reaching a renderer. ADR-138 amends both sentences in its own text rather than editing that file, because `docs/adr/README.md` reopens a record only by a later record. The silent half is what it shares with the third rather than what sets it above the others: an image's alt text is an attribute rather than text content, so the word leaves the heading, and GitHub slugs `Survey ![shot](…) 2019` to `survey--2019` with nothing to say a word was removed — #251's own deletion failure arriving by a second route, in the position ADR-136 had four sentences earlier called safe. What none of the five needed in the end is the thing the record still deliberately does not have: which renderers an operator actually opens the tree with, VS Code, IntelliJ and Obsidian among them — every decision above holds in every configuration measured, so none of them had to choose.

Java 26, Spring Boot 4.1.1, Spring Batch with `ResourcelessJobRepository` (no batch metadata tables), Spring Modulith for boundary verification only, SQLite as the single store, Chroma as a disposable vector projection (reached only when the vector store is first used, and nothing reads it yet, so no command needs Chroma running — the first code that does read it must ask for the store where it is used or through an `ObjectProvider`, never by constructor injection into a bean built at start-up, ADR-142), Docling out-of-process as the document converter (ADR-010) with Ollama as its default serving engine (ADR-012, ADR-013), picocli for a two-command CLI.

## Read before working

**`CONTEXT.md`** is binding vocabulary, not background. Name things as it names them — file occurrence, content identity, verdict, survivor, walk, walk anomaly, census, profile, gate, run, invocation. Each entry lists rejected synonyms under `_Avoid_`, and **the lists bind every name this project gives itself and nothing it renders for a reader outside it**. **ADR-122 states that rule once and enumerates both sides; read it there rather than a paraphrase here** — three paraphrases is how the memberships drifted apart in the first place. What it comes to in practice, so you know whether you need to open it:

- A name of ours uses the entry's own term, down to a test method name and a commit message, and a rejected synonym in one is a defect — unless the word names no such thing at all, as in `Collectors.groupingBy` or SQL's `GROUP BY`, which are untouched.
- Prose written for a reader outside this project is free of the lists altogether; ADR-122 enumerates the audiences, and this is deliberately not a second copy of that list. Where an entry carries a `_Renders as_` line, that is the word to use there; where it carries none, nothing is imposed.
- **Cluster** renders as *group*.

**`docs/adr/`** holds 148 decisions, ADR-001 to ADR-148, and two things about it are invisible from the files:

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

- **A test needing an external tool is an integration test**: named `*IT`, run by failsafe under `./mvnw verify`, excluded from surefire's `./mvnw test`. There are six — `CliExitIT`, `ClusterSynthesisIT`, `DoclingClientIT`, `OllamaClientIT`, `RelevanceReportIT`, `VesperaApplicationIT` — and the first of them launches the packaged jar and needs no Docker daemon, only `verify`; the other five each need a Docker daemon, starting their sidecar through Testcontainers. Every other class needs neither Docker nor `verify`.
- **A skipped test is not a passing test.** Several abort by assumption when the environment cannot create a symlink or an unusual filename, so report `Skipped` alongside `Tests run`.
- **Surefire's console output truncates the cause.** The real stack is in `target/surefire-reports/<class>.txt`.
- **The build needs Java 26 on `PATH` (or `JAVA_HOME`) — the shell default may be older.** `./mvnw` uses whatever `java` it finds first; an older default fails with `class file version ... only recognizes class file versions up to ...` before any test runs.
- **Test configuration is `application-test.yaml`, under the `test` profile**, so it layers over `src/main/resources/application.yaml`. Naming it `application.yaml` would shadow the main file entirely — same classpath resource name, test-classes first — and settings there would silently stop applying.
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

## Conventions worth knowing

- **The pom carries what a recorded decision requires** (ADR-046), not what current code happens to use. A new dependency wants a decision behind it.
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
