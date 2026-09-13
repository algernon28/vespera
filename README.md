# Vespera

Curates a local archive into a publication-ready knowledge base.

You point it at a folder of documents and at a handful of examples of what you care about. It walks the archive, extracts the text, throws out the duplicates and the boilerplate, scores what is left against your examples, and hands you the judgement calls it cannot make for you.

**It never guesses a threshold and it never edits your archive.** Everything it decides, it records; everything it cannot decide, it measures and asks you about.

This file is how to operate the tool. For the state of the project — what is built, what is only designed — see [`AGENTS.md`](./AGENTS.md). For why it works the way it does, see [`docs/adr/`](./docs/adr/README.md).

---

## Step 0 — name your exemplars, before you run anything

Vespera has no idea what you consider relevant, and it will not try to work it out. You supply a **seed folder**: a handful of documents that are examples of what you want kept. That is the only domain knowledge the tool takes, and everything downstream is measured against it.

Put the path in `profile.yaml` in your working directory, under `seedFolder`.

Do this first. Nothing the tool prints can ask you for it, because by the time anything is printed the first invocation has already happened — and discovering at the second invocation that exemplars were needed all along is the worst way to meet this tool.

## The path is five invocations

Getting from a folder of documents to a curated archive takes **five invocations**. Each one stops where a value is missing that only you can supply. Every stop is deliberate: the tool ends the invocation, keeps everything it learned, and tells you the one thing to do next.

| | You set | You run | It does |
|---|---|---|---|
| **1** | — | `vespera run <root>` | walks the archive, extracts the text, takes the census. Stops before deduplication, because the boilerplate floor is read off what this invocation just measured |
| **2** | `boilerplateDocumentFrequencyFloor`, `embeddingModel` | `vespera run` | deduplicates, reads your seed folder, scores every survivor against it, groups them, and writes you sixty documents to judge. **Removes nothing** |
| **3** | sixty answers in `relevance-labels.yaml` | `vespera label` | records your answers. They belong to the documents, not to the run, so they survive everything afterwards |
| **4** | `relevanceScoreFloor` | `vespera run` | applies your threshold. This is the first invocation that removes anything for being irrelevant |
| **5** | `arrangementApproved` | `vespera run` | records that the arrangement you read is the one you want. Nothing is written from it yet — see **What is not built** |

**You will be told which value is missing, every time.** You do not need this table in front of you and you do not need to know which stage you are at — read the last line of the output and it names what to set next.

**Six is what you get if you set one value per invocation.** All three of invocation 2's prerequisites are settable as soon as invocation 1 finishes, so setting them together is what makes the path five rather than six or seven.

## What you have to decide, and where to read it

Vespera writes reports beside the database. Each one measures something; none of them chooses for you.

| The value | Read | Which reports |
|---|---|---|
| `seedFolder` | your own knowledge of the archive | — |
| `boilerplateDocumentFrequencyFloor` | how many documents share the same passages | `format-mix.html` |
| `embeddingModel` | whichever model you can serve locally | — |
| `relevanceScoreFloor` | what each possible cut would cost you, in documents | `relevance-labelling.html`, `cluster-sizes.html`, `seed-corpus-comparison.html` |
| `degenerateOutputConfidenceFloor` | how well the text extraction went | `confidence-distribution.html` |
| `arrangementApproved` | whether the groups the tool formed are worth writing over | `arrangement.html` |

`degenerateOutputConfidenceFloor` is **optional** and is not one of the five stops. Left unset, nothing is removed for extracting badly. It is here so that you know it exists.

Every value you set carries a `provenance` field. Write down how you arrived at the number. Nothing checks that you read the report first — what stands between a guess and your archive is what you record there.

## Invocation 3 in more detail, because it costs you two hours

Invocation 2 leaves two files beside the database:

- **`relevance-labelling.html`** — how the scores are spread, the five bands they fall into, and what cutting at each band boundary would cost you in documents kept and documents lost. Open it in a browser; every document links to the original.
- **`relevance-labels.yaml`** — sixty documents drawn evenly across the score range, each with `relevant: null` waiting for a `true` or a `false`.

Answer them, then run `vespera label`. At roughly two minutes a document that is about two hours, and the sixty is a ceiling rather than a target: the same run always asks about the same documents, so you can stop and come back.

**Your answers outlive the run that asked.** They are keyed to the documents, so changing the embedding model later re-scores the archive and re-reads the answers you already gave, without asking you anything twice.

## Where things live

Everything the tool writes goes in one working directory, never inside your archive:

```
profile.yaml                     the values you set, and how you arrived at them
vespera.db                       the ledger: every file seen, every verdict, every measurement
format-mix.html                  what kinds of file the archive holds
confidence-distribution.html     how well the text came out
seed-corpus-comparison.html      how far your exemplars resemble the archive
relevance-labelling.html         the report you read to choose the threshold
relevance-labels.yaml            the sixty questions you answer
cluster-sizes.html               how the survivors grouped under each exemplar
arrangement.html                 the groups, named and in order, for you to approve
```

Set it with `--db-dir=<path>`, which must be written with the `=`, or with `vespera.working-dir` in configuration.

## Commands

```
vespera run <root>     walk a corpus and take it as far as the next missing value
vespera label [file]   record the answers you wrote into the label file
```

`vespera run` takes the archive root as its argument, falling back to `vespera.corpus-root` in configuration. Given neither, it refuses rather than guessing — a census of the wrong tree reports success.

## Running it

Java 26 and a Docker daemon. Vespera runs its document converter and its embedding model as sidecars and manages them itself.

```
./mvnw verify
```

## What is not built

Generating the connecting text over the survivors — stage 6b — is recorded decisions with no code behind it yet. Everything above works up to and including the arrangement: the survivors are grouped, each group is named after its own leading document, the groups are put in order, and `arrangement.html` asks you to approve them. What that approval gates has not been built, so invocation 5 records your answer and produces nothing from it.

The run ends at the documents stage 6b will generate, and what you do with them is yours: nothing here renders or uploads them anywhere (ADR-101).
