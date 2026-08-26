# Vespera

Vespera curates an unknown, multi-format local archive — hundreds of gigabytes of `.txt`, `.docx`, `.pdf` and images — into a publication-ready knowledge base. It measures the corpus before judging it, removes what is mechanically broken, redundant or topically irrelevant, and synthesises connective material over what survives. The engine carries no knowledge of the corpus's subject: the operator supplies that through a **seed set** of known-relevant documents, which also names the published taxonomy.

This file is what an agent needs to start working. Everything it points at is authoritative over it.

## The shape of the system

**A verdict ledger, not a moving pipeline.** Documents never move between stages. One table is populated once by the census; every stage *appends* verdict rows against a file occurrence. "Survivors" is a query over occurrences carrying no blocking verdict, never a place things are put. Retuning a threshold is a `DELETE` of one stage's rows plus a re-run.

**Eight stages, cheapest filter first**, each defined by the verdicts it writes: census (0), byte-level reduction (1), extraction (2), content census (3), content redundancy (4), relevance (5), arrangement and generation (6a/6b). Publication (7) is an adapter invoked separately, always by a person. Stages never call each other — they read and write only through the ledger.

**Two independent identities.** A **walk** owns file occurrence rows because they are filesystem observations; a **run** owns verdict rows because they are derived under a configuration. Content identity is a relation discovered over occurrences, never a collapse of them.

**Nine capability-shaped modules**, as packages under `io.algernon.vespera`: `ledger`, `corpus`, `extraction`, `similarity`, `embedding`, `synthesis`, `publication`, `profile`, `pipeline`. The rule: **a capability module may depend on `ledger` and nothing else horizontal**; `pipeline` is the composition root and the only module that names a stage.

`docs/architecture.md` §1–§2 carries the full version, including the tech-stack table.

## Where it stands today

Design is far ahead of code. What exists in `src/main`: `ledger/OccurrencePath` (how a path identifies a file occurrence) and `corpus/Walk` (one observation of a filesystem, emitting occurrences and anomalies to an observer). Nothing is persisted yet — the schema, the database's location and the verdict vocabulary are all still open decisions.

Java 26, Spring Boot 4.1.1, Spring Batch with `ResourcelessJobRepository` (no batch metadata tables), Spring Modulith for boundary verification only, SQLite as the single store, Chroma as a disposable vector projection, Ollama as the default extraction engine, picocli for a two-command CLI.

## Read before working

**`CONTEXT.md`** is binding vocabulary, not background. Name things as it names them — file occurrence, content identity, verdict, survivor, walk, walk anomaly, census, profile, gate, run, invocation. Each entry lists rejected synonyms under `_Avoid_`; keep those words out of identifiers, tests and commit messages.

**`docs/adr/`** holds 52 decisions, and two things about it are invisible from the files:

- **ADR-001 to ADR-049 are reconstituted records.** The original text was lost; each carries a verbatim one-line summary and nothing more. Cite them, but do not mistake a summary for the whole decision — `docs/architecture.md` §1–§2 is the fuller record for most, and every ADR names the sections that discuss it.
- **New decisions start at ADR-052** and carry their own full text: context, decision, consequences.

## Where the work is

Work is charted as a **wayfinder map** on the issue tracker — [issue #1](https://github.com/algernon28/vespera/issues/1) — one child issue per decision, worked one per session. Its open, unblocked children are what is takeable. On a closed ticket the **resolution comment is the real spec**, so read comments rather than bodies.

## Building and testing

```
./mvnw test                                                    # everything
./mvnw test -Dtest='WalkTest' -DfailIfNoSpecifiedTests=false   # one class
./mvnw -q test-compile                                         # compile only
```

- **`VesperaApplicationTests` needs a Docker daemon** — it starts Chroma and Ollama through Testcontainers. The other classes need neither.
- **A skipped test is not a passing test.** Several abort by assumption when the environment cannot create a symlink or an unusual filename, so report `Skipped` alongside `Tests run`.
- **Surefire's console output truncates the cause.** The real stack is in `target/surefire-reports/<class>.txt`.
- **Test configuration is `application-test.yaml`, under the `test` profile**, so it layers over `src/main/resources/application.yaml`. Naming it `application.yaml` would shadow the main file entirely — same classpath resource name, test-classes first — and settings there would silently stop applying.
- Assertions are AssertJ.

## Conventions worth knowing

- **The pom carries what a recorded decision requires** (ADR-046), not what current code happens to use. A new dependency wants a decision behind it.
- **The pages under `docs/` are generated.** Edit the Markdown and run `node docs/render-docs.mjs`; `--check` fails when the committed HTML is stale, and `.github/workflows/docs.yml` runs it along with a Mermaid parse of every diagram.
- **The module rule binds only where it is declared.** `ApplicationModules.verify()` constrains a module that declares `allowedDependencies`; an undeclared one is wide open, since the attribute defaults to `*`. `ModuleBoundariesTest` fails when a module ships without a declaration.
- **Census is Windows-first.** Its identity rules rest on measured NTFS behaviour — case folding that disagrees with the JDK, filenames with no UTF-8 encoding, reparse points — so some tests are guarded to Windows and say so.
- **Measure rather than argue.** Decisions here are settled by execution where execution is possible, and the measurement belongs in the record. Probes are throwaway and live outside the repository.

## Agent skills

### Issue tracker

GitHub Issues on `algernon28/vespera`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical roles, unrenamed. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` plus `docs/adr/`. See `docs/agents/domain.md`.

## Delegation

Five subagents live in `.claude/agents/`: `analyst` decides and records, `spec-implementer` builds from a settled spec, `tester` runs and audits without editing, `debugger` reproduces before fixing, `architect` reviews and merges. Prefer them over working in the main session — each is constrained in ways the main session is not, and the constraints are the point. Work here when the task is a question, a one-line answer, or smaller than the handoff costs.
