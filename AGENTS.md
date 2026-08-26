# Vespera

Vespera curates an unknown, multi-format local archive into a publication-ready knowledge base. It measures the corpus before judging it, removes what is broken, redundant or topically irrelevant, and synthesises connective material over what survives. The engine carries no knowledge of the corpus's subject; the operator supplies that through a seed folder of known-relevant documents.

## Read before working

**`CONTEXT.md`** is binding vocabulary, not background. Name things as it names them — file occurrence, content identity, verdict, survivor, walk, walk anomaly, census, profile, gate, run. Each entry lists rejected synonyms under `_Avoid_`; keep those words out of identifiers, tests and commit messages.

**`docs/adr/`** holds the decisions, and two things about it are invisible from the files themselves:

- **ADR-001 to ADR-049 are reconstituted records.** The original text was lost; each carries a verbatim one-line summary and nothing more. Cite them, but do not mistake a summary for the whole decision.
- **`docs/architecture.md` §1–§2 is the fuller record** for most of them, and every ADR names the sections that discuss it.

New decisions start at **ADR-052** and carry their own full text: context, decision, consequences.

## Where the work is

Work is charted as a **wayfinder map** on the issue tracker — [issue #1](https://github.com/algernon28/vespera/issues/1) — one child issue per decision. Its open, unblocked children are what is takeable. On a closed ticket the **resolution comment is usually the real spec**, so read comments rather than bodies.

## Agent skills

### Issue tracker

GitHub Issues on `algernon28/vespera`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical roles, unrenamed. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` plus `docs/adr/`. See `docs/agents/domain.md`.

## What the repository will not tell you

Each of these has already cost someone an hour.

- **A skipped test is not a passing test.** Several tests abort by assumption when the environment cannot create a symlink or an unusual filename, so report `Skipped` alongside `Tests run`.
- **Surefire's console output truncates the cause.** The real stack is in `target/surefire-reports/<class>.txt`.
- **`VesperaApplicationTests` needs a Docker daemon** — it starts Chroma and Ollama through Testcontainers. The other test classes need neither.
- **Test configuration lives in `application-test.yaml`, under the `test` profile**, so it layers over `src/main/resources/application.yaml`. Naming it `application.yaml` instead would shadow the main file entirely — same classpath resource name, test-classes first — and settings there would silently stop applying.
- **The pages under `docs/` are generated.** Edit the Markdown and run `node docs/render-docs.mjs`; `--check` fails when the committed HTML is stale, and CI runs it. Edits made to the HTML are lost at the next render.
- **The pom carries what a recorded decision requires** (ADR-046), not what current code happens to use. A new dependency wants a decision behind it.
- **Census is Windows-first.** Its identity rules rest on measured NTFS behaviour — case folding that disagrees with the JDK, filenames with no UTF-8 encoding, reparse points — so some tests are guarded to Windows and say so.
- **The module boundary rule is only enforced where it is declared.** `ApplicationModules.verify()` constrains a module that declares `allowedDependencies`; an undeclared one is wide open, because the attribute defaults to `*`. `ModuleBoundariesTest` fails when a module ships without a declaration, which is what keeps ADR-040 alive.

## Delegation

Prefer the subagents in `.claude/agents/` over working in the main session: each is constrained in ways the main session is not, and the constraints are the point. Work here when the task is a question, a one-line answer, or smaller than the handoff costs.
