# Vespera

Curates a local archive into a publication-ready knowledge base.

You point it at a folder of documents and at a handful of examples of what you care about. It walks the archive, extracts the text, throws out the duplicates and the boilerplate, scores what is left against your examples, and hands you the judgement calls it cannot make for you.

**It never guesses a threshold and it never edits your archive.** Everything it decides, it records; everything it cannot decide, it measures and asks you about.

This file is how to operate the tool. For the state of the project — what is built, what is only designed — see [`AGENTS.md`](./AGENTS.md). For why it works the way it does, see [`docs/adr/`](./docs/adr/README.md).

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
| **2** | `boilerplateDocumentFrequencyFloor`, `embeddingModel` | `vespera run` | deduplicates, reads your seed folder, scores every survivor against it, groups them, and writes you sixty documents to judge. **Removes nothing** |
| **3** | sixty answers in `relevance-labels.yaml` | `vespera label` | records your answers. They belong to the documents, not to the run, so they survive everything afterwards |
| **4** | `relevanceScoreFloor` | `vespera run` | applies your threshold. This is the first invocation that removes anything for being irrelevant |
| **5** | `arrangementApproved` | `vespera run` | writes the connecting text over the arrangement you approved, and leaves the finished tree of Markdown in your working directory |

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
| `generationModel` | whichever model you can serve locally, if you want a different one | — |
| `generationContextWindow` | how much your own machine can read in one go | — |
| `logTimestampShareFloor` | how much of each text file begins with a date or a time | `format-mix.html` |

Four of these are **optional** and none of them is one of the five stops; all four are here so that you know they exist.

`degenerateOutputConfidenceFloor`, left unset, removes nothing for extracting badly. `logTimestampShareFloor`, left unset, removes no logs: set it, as a share between 0 and 1, to leave out as a log every text file of ten lines or more in which at least that share of the lines read begin with a date or a time. `generationModel` and `generationContextWindow`, left unset, write the connecting text with the model and the reading window Vespera ships with — they are the values that already have answers, and setting one only replaces the answer it already had.

The reading window is how much of a group goes into one request. Set it larger and more of each group is read in one go; leave it alone and Vespera uses a size any machine can serve. Groups too large to fit are still written about, from the documents nearest your exemplar, and the finished page says how many of them it was written from.

Every value you set carries a `provenance` field. Write down how you arrived at the number. Nothing checks that you read the report first — what stands between a guess and your archive is what you record there.

## Invocation 3 in more detail, because it costs you two hours

Invocation 2 leaves two files beside the database:

- **`relevance-labelling.html`** — how the scores are spread, the five bands they fall into, and what cutting at each band boundary would cost you in documents kept and documents lost. Open it in a browser; every document links to the original.
- **`relevance-labels.yaml`** — sixty documents drawn evenly across the score range, each with `relevant: null` waiting for a `true` or a `false`. A document you have already answered, against the same seed folder and from the same corpus root, shows your answer instead, so a later run's file starts from what you said last time. The file names the seed folder it was written for; if you change `seedFolder` before running `vespera label`, it is refused, so run again first.

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
deliverable/<run>/index.md       what was written, group by group, and what produced it
deliverable/<run>/documents.csv  every surviving document, with its place in the order
```

Set it with `--db-dir=<path>`, which must be written with the `=`, or with `vespera.working-dir` in configuration. Both commands take `--db-dir=<path>`, so if you moved the working directory, name it on `vespera label` as well as on `vespera run`. A command given a `--db-dir` other than the directory it actually opened refuses and records nothing.

While a command is running, two more files sit beside `vespera.db`: `vespera.db-wal`, SQLite's write-ahead log, which holds the most recent changes, and `vespera.db-shm`, its index. When the command ends they are folded back into `vespera.db` and deleted. To copy the working directory, copy it after the command has ended. If you have to copy it while a command is running, copy all three files together, or the copy is missing the latest changes. Keep the working directory on a disk attached to the machine that runs Vespera, not on a network share: the database relies on shared memory that only works on a local disk.

## Commands

```
vespera run <root> [--db-dir=<path>]     walk a corpus and take it as far as the next missing value
vespera label [file] [--db-dir=<path>]   record the answers you wrote into the label file
```

`vespera run` takes the archive root as its argument, falling back to `vespera.corpus-root` in configuration. Given neither, it refuses rather than guessing — a census of the wrong tree reports success.

## Running it

You need Java 26 and a Docker daemon. Run every command below from the root of this repository.

**Build it.** This builds the jar Vespera runs from:

```
./mvnw package
```

**Start the sidecars, once, before the first invocation.** Vespera needs three services running beside it:

- Chroma, the vector store;
- Ollama, which serves the models;
- docling-serve, the document converter.

The jar does not start them or stop them. You start them yourself from `compose.yaml`:

```
docker compose -p vespera up -d --build
```

`-p vespera` names the Compose project `vespera`, whatever your checkout's directory is called. Give it on every `docker compose` command here, so that each one finds the same containers. Without it, Docker Compose names the project after the directory, and a second checkout starts a second set that fights the first for the same ports.

If your machine has an NVIDIA graphics card that Docker can use, let Ollama and the document converter run on it. Ollama runs its models several times faster there than on the processor, and the converter got through a sample of the archive two to two and a half times faster. Docker Desktop on Windows can use one through WSL 2; on Linux, Docker needs NVIDIA's Container Toolkit. Start the sidecars with a second file, `compose.gpu.yaml`, named after the first:

```
docker compose -p vespera -f compose.yaml -f compose.gpu.yaml up -d --build
```

Name both files every time you run `up`, this time and every time after. An `up` without `compose.gpu.yaml` replaces Ollama with one that runs on the processor, and the models you gave it go with it. It replaces the document converter with the processor build too. `stop`, `exec` and `down` need only `-p vespera`. Without such a card, leave `compose.gpu.yaml` out: with it, `up` stops with an error, and neither Ollama nor the document converter starts. Once a model has answered, `docker compose -p vespera exec ollama ollama ps` shows under `PROCESSOR` `100% GPU` when the model is wholly on the card, a split such as `30%/70% CPU/GPU` when only part of it fits, and `100% CPU` when Ollama is not using the card.

With `compose.gpu.yaml`, the document converter is built from the same `Containerfile` on a base that can use the card, under a name of its own. Its output differs from the processor build's in a few places, and Vespera records beside every conversion the name of the image that made it. The converter cannot say which image it is, so you have to tell Vespera. Set `VESPERA_DOCLING_IMAGE` in the shell you run `vespera` from, before you run it. In PowerShell:

```
$env:VESPERA_DOCLING_IMAGE = 'vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0'
```

In a POSIX shell:

```
export VESPERA_DOCLING_IMAGE='vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0'
```

If you run Vespera from the IDE instead, add this line to the file `.env` at the root of this repository, creating it if it is not there. The `Local SpringApp` and `Local Vespera Label` run configurations in `.run/` read it, and `java -jar` does not:

```
VESPERA_DOCLING_IMAGE=vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0
```

If you forget, every conversion made on the card is recorded under the name of the processor build, and nothing will tell you. If you go back to `compose.yaml` alone, unset the variable and take the line out again, or the processor build's conversions are recorded under the card's name.

The first run after you switch to the card converts every file again, once, because conversions recorded under the processor build's name are not reused under the card's. On the card that is still quicker than finishing on the processor. Every step after the conversion runs again too. The answers you already wrote into the label file are kept, but the label file is written anew, and you are asked to approve the arrangement again. If you switch back, the processor build finds its own conversions where it left them.

To check that the converter is using the card, run this. It prints `True` when it is:

```
docker compose -p vespera exec docling-serve python -c "import torch;print(torch.cuda.is_available())"
```

To check that Vespera recorded the card's image, run this from the directory you run `vespera` from, once a run has converted something, with Python on your machine. It prints what the newest conversion run in the ledger was keyed on, and the image is the part after `image=`. After your first run on the card, that is `vespera/docling-serve-cu128-libreoffice`:

```
python -c "import sqlite3;print(sqlite3.connect('file:.vespera/vespera.db?mode=ro',uri=True).execute('select config_consumed from run where stage=? order by rowid desc limit 1',['extraction']).fetchone())"
```

If you moved the working directory, put its `vespera.db` in place of `.vespera/vespera.db`.

The document converter's image is not pulled. It is built on your machine from `docker/docling-serve`, because it adds LibreOffice to the published image, so that `.doc` and `.ppt` files convert. `--build` builds it the first time and rebuilds it if its `Containerfile` has changed. The first build is the slow one. After that, Docker reuses what it built.

The services listen on ports `8000`, `11434` and `5001`, which is where Vespera looks for them. If something else on your machine already holds one of those ports, the start fails and names the port.

Leave the sidecars up for all five invocations. They can be days apart.

If a sidecar stops on its own, Docker starts it again, and it does the same after your machine restarts, as long as Docker itself starts. So when a command stops because the document converter went away, you can run the same command again without starting anything by hand. The converter usually comes back within half a minute, and a command run before then stops again, so wait a moment and run it once more. A sidecar you stop with `docker compose -p vespera stop` stays stopped, even across a restart of your machine.

**Give Ollama its models.** Ollama serves only the models it has been given, and Vespera does not fetch them for you:

- The embedding model you name in `embeddingModel` has to be there before invocation 2.
- The model the connecting text is written with has to be there before invocation 5. That is `qwen3:8b`, unless you set `generationModel`.

```
docker compose -p vespera exec ollama ollama pull <embeddingModel>
docker compose -p vespera exec ollama ollama pull qwen3:8b
```

**Run it.** Each `vespera` in this file is this command:

```
java -jar target/vespera-0.0.1-SNAPSHOT.jar
```

So `vespera run <root>` is `java -jar target/vespera-0.0.1-SNAPSHOT.jar run <root>`, and `vespera label` is `java -jar target/vespera-0.0.1-SNAPSHOT.jar label`. Unless you set it as "Where things live" describes, the working directory is `.vespera` under the directory you run the command from.

**Stop the sidecars when you are finished:**

```
docker compose -p vespera stop
```

This keeps the models you gave Ollama. `docker compose -p vespera down` removes the containers, and the models inside them go too.

The same happens when `compose.yaml` has changed since you started the sidecars, for example after you update your checkout. `docker compose -p vespera up -d --build`, or the `up` line above that names both files if you started them with both, then replaces the container of each service whose part of `compose.yaml` or `compose.gpu.yaml` changed. If Ollama's is one of them, its models go with it: `docker compose -p vespera exec ollama ollama list` shows which it still has, and the same `ollama pull` lines as above give them back. Nothing in your working directory is lost.

## Where the run ends

Approving the arrangement is the last thing you are asked for. The fifth invocation writes the connecting text over each group and leaves you a tree of Markdown in your working directory: an index and a listing of every surviving document at its root, and beneath it one directory per exemplar holding one file per group, each named and placed the way you approved them.

A group's file opens with the heading written for it, carries the prose with every citation resolved into a link to that document's numbered place in the list below, and then lists the group entire — including the documents the writing never mentioned. Each entry in that list links to the original where it already sits in your archive, so a sentence you doubt is two clicks from the document behind it. Where a group was written from part of its documents, the page says so and names both numbers. Where an answer was turned down, the group keeps its place and its file says plainly that nothing was written over it, rather than leaving a gap you have to notice.

Those files are where Vespera stops. It does not turn them into a wiki, a site or a page anywhere, and it does not upload or send them (ADR-101). Your archive is untouched throughout: the tree links to your documents where they already sit and copies none of them (ADR-104). What you do with any of it is yours.
