# Running Vespera, stage by stage, and resuming it

[`README.md`](../README.md) tells you how to drive the tool: five invocations, and the value each one stops for. This page goes one level down. For each stage it says what you run, what has to be in place, what the stage leaves behind, where it stops, and what happens when you run the command again after a stop, a crash or a change of mind.

Read the README first if you have not set the tool up. The sidecars, the models and the build are described there and not repeated here.

## The one rule

**To resume, run the same command again.** There is no resume flag, no stage selector and no "start from stage N" option.

```
vespera run <root>
```

Every invocation starts at the first step and goes through all fifteen in order. A step whose work is already recorded does nothing and says so in one line. A step that was interrupted picks its work up or does it again, as its section below describes. A step whose input is missing says it is gated and does nothing. The invocation then ends with one line that names what to set next.

So you never choose where to resume. The ledger in `vespera.db` records what is done, and the command reads it.

Three things follow, and they hold at every stage:

- **Stopping is safe.** Ctrl-C, a closed terminal, a killed process and a power cut all leave a working directory the next invocation can continue from. What is lost is the work since the last commit of the step that was running.
- **Nothing is forced to run again.** There is no "do it anyway" option. A step runs again only when something it depends on has changed. See [Going back](#going-back-making-a-stage-run-again).
- **Do not edit `vespera.db`.** Deleting rows to make a stage run again is never the way. Change the value the stage reads.

## How to tell where you are

You do not need to know the stage to continue. To find out anyway:

- **The last line of the output** names every value the next invocation wants, or says nothing is left to set.
- **A gated step logs why**, in a line containing `is gated:`. The first such line of an invocation is where the run stopped being able to go further.
- **A finished step logs** `was already recorded under run <id>` and does nothing.
- **`vespera.log`** in the working directory holds the same lines as the console, for every invocation, with dates. Older ones roll into `vespera.<date>.<n>.log` beside it.

## The steps, in the order they run

One invocation runs these fifteen steps. The names are the ones the log prints.

| Stage | Step | Needs | Leaves |
|---|---|---|---|
| 0 | `census` | the archive root | the list of files seen |
| 1 | `byte-level-reduction` | a finished census | `format-mix.html` |
| 2 | `extraction` | the document converter running | extracted text, `extraction-failures.html` |
| 3 | `content-census` | stage 2 finished | `confidence-distribution.html` |
| 4a | `redundancy-signature` | `boilerplateDocumentFrequencyFloor` | a signature for each document |
| 4b | `content-redundancy` | the same | near-duplicates removed |
| 5a | `seed-extraction` | `seedFolder`, stage 4 done | your exemplars' text |
| 5b | `seed-corpus-comparison` | exemplars that produced text | `seed-corpus-comparison.html` |
| 5c | `embedding-scoring` | `embeddingModel`, served by Ollama | a vector for each piece of text |
| 5d | `relevance-scoring` | the same | a score for each document |
| 5e | `relevance-floor` | `relevanceScoreFloor`, or nothing | documents below the floor removed |
| 5f | `clustering` | scores | the groups, `cluster-sizes.html` |
| 5 | `relevance-report` | scores | `relevance-labelling.html`, `relevance-labels.yaml` |
| 6a | `arrangement` | groups | `arrangement.html` |
| 6b | `generation` | `arrangementApproved`, the generation model served by Ollama | `deliverable/<run>/` |

`vespera label` is a second command, not a step. It runs between two invocations of `vespera run` and is described under [stage 5](#stage-5--relevance).

## Before the first invocation

1. Build the jar, start the sidecars and give Ollama its models, as the README's "Running it" describes.
2. Create the working directory and write `seedFolder` into `profile.yaml` in it, as the README's "Step 0" describes.
3. Decide where the working directory is. It is `.vespera` under the directory you run the command from, unless you pass `--db-dir=<path>`. **Pass the same `--db-dir` on every invocation**, to `vespera label` too. A different working directory is a different, empty ledger, and the run starts from nothing there.

## Stage 0 — census

**Run:** `vespera run <root>`

**What it does.** Walks the archive root and records every file it sees. It reads no file's content and judges nothing. If `seedFolder` is set, it walks that folder too, as a walk of its own.

**Where it stops.** It does not stop for a value. It fails if the root cannot be walked.

**Resume.** The walk commits its place about every thousand entries.

- **Interrupted mid-walk:** the next invocation continues the same walk and skips the folders it had finished. Entries seen after the last commit are walked again. Run the same command, with the same root.
- **The archive changed while the walk was stopped:** the walk fails and says the tree no longer agrees with where it left off. It does not carry on over a tree it cannot trust.
- **Finished, and the archive is unchanged:** the next invocation still walks the root, compares what it saw with the previous finished walk, finds them the same and discards the new one. Every later step then finds its own work already recorded. This is why re-running over an unchanged archive is cheap.
- **Finished, and the archive changed:** the new walk is kept, and it is a new observation. Every later stage runs again over it. See [the archive changed](#the-archive-changed).

A walk that has not finished is never used by a later stage. Nothing downstream runs over part of an archive.

## Stage 1 — byte-level reduction

**Run:** the same command; it follows census in the same invocation.

**What it does.** Removes what can be judged from bytes alone: broken files, exact duplicates, superseded copies, and the formats Vespera does not convert. It writes `format-mix.html`.

**Optional value.** `logTimestampShareFloor`. Left unset, no logs are removed.

**Where it stops.** It does not stop for a value.

**Resume.** If the step did not finish, the next invocation discards what the step wrote and does it again from the start. It is quick next to extraction. If it finished, it is skipped.

## Stage 2 — extraction

**Run:** the same command. The document converter has to be running.

**What it does.** Sends every surviving file to the document converter and stores the text, with measurements of how well it came out. A file the converter cannot read is marked, skipped and listed in `extraction-failures.html`. This is the long stage: on a large archive it runs for hours or days.

**Optional values.** `degenerateOutputConfidenceFloor` (left unset, nothing is removed for extracting badly) and `extractionAttempt` (see below).

**Where it stops.** It does not stop for a value. It stops if the converter is not there, runs a different image from the one Vespera was told, or stays away for more than three minutes. The message says which. Fix the sidecar and run the same command again.

**Resume.** Extraction commits a small batch of files at a time, and a committed batch is final.

- **Interrupted:** the next invocation keeps every file already recorded and reads only the rest. One line at the start of the step says how many were already recorded. At most the batch that was open when the process stopped is converted again.
- **Files the converter refused for its own reasons** (busy, failing, too slow) are asked about again on a resume, mostly from the answer already stored, not by a second conversion.
- **Conversions are never paid for twice.** The converted text is stored by the file's content and by the converter that made it, not by the run. Even when the whole stage has to run again, a file already converted by the same converter image is read back from the store.
- **Finished:** skipped.

**Asking the converter again.** If `extraction-failures.html` lists files that were not read because the converter was busy or too slow, write `2` into `extractionAttempt` with why in `provenance`, and run again. Files it already answered about are not converted again. Everything after extraction is done again. Nothing from the first attempt is deleted, and removing the value goes back to it. The README's section on `extractionAttempt` has the detail.

**Do not update Vespera in the middle of this stage.** A new build can change what the stage is recorded under, and the stage then starts again from the first file, reading its conversions back from the store. Let a long extraction finish on the build it started on.

## Stage 3 — content census

**Run:** the same command.

**What it does.** One pass over what extraction stored: how many documents share each passage, and how the conversion quality is spread. It writes `confidence-distribution.html`. It removes nothing.

**Where it stops.** It does not stop for a value.

**Resume.** Interrupted, it discards its own rows and does the pass again. Finished, it is skipped.

**After this stage the first invocation ends**, because the next stage wants a value read off what this one measured. The last line names it.

## Stage 4 — content redundancy

**Set first:** `boilerplateDocumentFrequencyFloor` in `profile.yaml`, read off the reports as the README's table says. Set `embeddingModel` in the same edit, and make sure `seedFolder` is there: all three are settable now, and setting them together saves you an invocation each.

**Run:** `vespera run <root>`

**What it does.** Two steps. `redundancy-signature` computes a compact signature for each document, leaving out the passages the floor calls boilerplate. `content-redundancy` compares the signatures and removes near-duplicates.

**Where it stops.** With the floor unset, or holding something that is not a number, both steps are gated, the log says `stage 4 (content redundancy) is gated`, and nothing after them can run. Set the value and run again.

**Resume.** Each of the two steps is recorded on its own. Interrupted, a step discards its own rows and does its work again; the other step's finished work stays. The signatures are recomputed from the stored text, with no call to a sidecar.

**Disk space.** The second step builds an index and sorts a great many rows. The drive that holds the working directory needs free space while it runs; the README's "Where things live" says how the temporary files behave.

## Stage 5 — relevance

Stage 5 is seven steps and spans three invocations, with `vespera label` between them.

### 5a and 5b — your exemplars

**Needs:** `seedFolder`, and stage 4 finished.

**What they do.** `seed-extraction` converts the documents in your seed folder, with the same converter. `seed-corpus-comparison` measures how far they resemble the archive and writes `seed-corpus-comparison.html`. It reports and enforces nothing.

**Where they stop.** They are gated when no seed folder is named, the folder could not be walked, no exemplar produced any text, or an exemplar would not open. The gated line says which. A converter failure on one of your exemplars stops the command and names the file.

**Resume.** Interrupted, each discards its own rows and does its work again, reading conversions back from the store. Seed extraction is not recorded as finished while an exemplar will not open, so it is tried again on every invocation until the file opens or you take it out of the folder.

### 5c and 5d — scoring

**Needs:** `embeddingModel` set, and that model pulled into Ollama.

**What they do.** `embedding-scoring` turns each piece of text into a vector. `relevance-scoring` scores every surviving document against your exemplars.

**Where they stop.** Gated with `no embedding model is named`, or for the same exemplar reasons as above.

**Resume.** Vectors are stored by content and by model, not by the run, so vectors already computed are kept across a stop, across a change of floor, and across a new walk of a changed archive. Interrupted, `relevance-scoring` discards its own scores and computes them again from the stored vectors. Ollama has to be running whenever these two steps do work, even when every vector is already stored.

### 5e — the floor

**Needs:** nothing. With `relevanceScoreFloor` unset it removes nothing, and that is the normal state for the first pass.

**What it does.** Once `relevanceScoreFloor` is set, removes the documents scoring below it. This is the first step that removes anything for being irrelevant.

**Resume.** This step is never recorded as finished. On every invocation it withdraws the removals it made under this scoring and decides again, because the answers you gave with `vespera label` can change what it should do without changing anything else. It calls no sidecar. When an answer changes what it removes, the groups are formed again (5f) and the arrangement is a different one (6a).

### 5f — the groups

**What it does.** `clustering` groups the survivors under each exemplar and writes `cluster-sizes.html`.

**Resume.** Interrupted, it discards its own groups and forms them again from the stored vectors. Finished, it checks that its groups still hold exactly the documents the floor leaves. If they do, it is skipped. If the floor has since removed a grouped document, or brought back one that has no group, it forms every group again from the stored vectors and writes `cluster-sizes.html` again.

### The questions

**What it does.** `relevance-report` writes `relevance-labelling.html` and `relevance-labels.yaml`: sixty documents drawn across the score range, each waiting for your answer.

**Resume.** Like the floor, this step is never recorded as finished, so both files are written anew on every invocation that reaches it, from the answers recorded so far. **Record your answers with `vespera label` before you run `vespera run` again**, so that the new file starts from them.

### Between invocations — `vespera label`

**Run:** `vespera label`, with the same `--db-dir` if you moved the working directory.

**What it does.** Reads `relevance-labels.yaml` and records every `true` or `false` you wrote. It runs no stage and removes nothing.

**Resume.** Labelling is built to be stopped. Answer some, run `vespera label`, answer more another day, run it again. The same run always asks about the same documents. Your answers are kept by document, not by run, so they survive a new embedding model, a new floor and a new walk.

**It refuses, and records nothing,** when there is no label file, when the file was written for another seed folder, or when it names a run this working directory does not hold. The message says which. Run `vespera run` again to have a current file written, then answer that one.

**`vespera label --auto`** has the local model answer the questions in the label file and then sets `relevanceScoreFloor` by a rule that loses no document labelled relevant. It never replaces an answer a person gave, and it leaves a floor a person wrote as it is. It takes no file argument, and it refuses while the file holds answers you have not recorded: run `vespera label` first.

### Setting the floor

Read a number off `relevance-labelling.html`, write it into `relevanceScoreFloor` with how you arrived at it, and run `vespera run <root>` again. A scoring under a different floor is a different scoring: the documents are scored again from the vectors already stored, the floor is applied, and the groups are formed again over what is left. Stages 0 to 4 are skipped.

## Stage 6a — arrangement

**Needs:** groups from stage 5.

**What it does.** Names each group after its highest-scoring document, puts the groups in order, and writes `arrangement.html`. It judges nothing and removes nothing. The last line of the output gives you a twelve-character name for this arrangement.

**Where it stops.** The invocation ends here until you approve. Read `arrangement.html`. If it is the arrangement you want, write the name from the last line into `arrangementApproved`, with what you checked in `provenance`, and run again.

**Resume.** Interrupted, it discards its own rows and arranges again. Finished, it is not arranged again.

**An approval is for one arrangement.** If anything upstream changes afterwards (the archive, the embedding model, the floor, an extraction attempt), the groups are formed again, the arrangement has a new name, and the old approval opens nothing. The last line says the approval does not match and gives you the new name. Read the page again before you copy it. The same holds when your answers change what the floor removes without the floor itself changing: the arrangement with those removals and the one without are two arrangements, each with its own name, approval and written groups, and going back to the earlier answers arrives at the earlier one again.

## Stage 6b — generation

**Needs:** `arrangementApproved` naming the arrangement this invocation arrived at, and the generation model pulled into Ollama (`qwen3:8b`, unless you set `generationModel`).

**Run:** `vespera run <root>`

**What it does.** Asks the model once for each group, checks every answer before believing it, and writes the tree of Markdown under `deliverable/<run>/`. An answer that is turned down leaves its group in place with a note that nothing was written over it.

**Where it stops.** Gated when no arrangement is approved, or the approval names another arrangement. It also stops if the model keeps failing.

**Resume.** Generation is recorded group by group, because each group costs a model call.

- **Interrupted:** the next invocation skips every group already written and asks only about the rest.
- **Finished with some groups unwritten:** the last line says how many. Run the same command again: it asks again about exactly those groups and leaves the written ones alone. The stage counts as finished only when every group is written.
- **Finished with every group written:** skipped. The last line says nothing is left to set and where the tree is.

## Going back: making a stage run again

A stage runs again when something it was computed from changes. What it was computed from is the value you set, the archive as walked, and the build of Vespera. Everything downstream of a changed stage runs again too, because it was computed from that stage.

| You change | Runs again | Kept |
|---|---|---|
| `logTimestampShareFloor` | stage 1 onward | conversions, vectors, your answers |
| `degenerateOutputConfidenceFloor` | stage 2 onward | conversions, vectors, your answers |
| `extractionAttempt` | stage 2 onward | conversions already answered, vectors, your answers |
| `boilerplateDocumentFrequencyFloor` | stage 4 onward | extraction, vectors, your answers |
| `seedFolder` | stage 5 onward | extraction, stage 4; the answers given for the old folder stay with the old folder |
| `embeddingModel` | stage 5 scoring onward | extraction, stage 4, your answers |
| `relevanceScoreFloor` | stage 5 scoring onward, from the stored vectors | extraction, stage 4, vectors, your answers |
| `arrangementApproved` | generation only | everything before it |
| `generationModel`, `generationContextWindow` | generation | everything before it |

Three things to know about this table:

- **Any change above stage 6a ends in a new arrangement to approve.** That is the cost of going back, and it is deliberate: an approval never outlives what it approved.
- **Nothing is deleted when you change a value.** The old stage's results stay recorded beside the new ones. Put the old value back and the next invocation finds that work already recorded and continues from it at no cost. So trying a stricter floor and returning to the looser one is cheap in both directions.
- **The two expensive things are kept across almost everything**: a converted document, as long as the converter image is the same, and a vector, as long as the embedding model is the same.

### The archive changed

A file added, removed, moved or modified makes the next walk a new observation. Every stage runs again over it, and the arrangement has to be approved again. It costs far less than the first time: unchanged files are read back from the conversion store and their vectors from the vector store, so only new or changed content reaches a sidecar. Your recorded answers still apply.

### The build changed

After you update Vespera and rebuild the jar, the stages whose code changed run again, and everything after them. Stages whose code did not change are skipped as usual. Conversions and vectors are kept unless the converter image or the model changed with it.

## When a command is refused or fails

Run the same command again once the cause is fixed. Nothing recorded is lost.

| What you see | What to do |
|---|---|
| a refusal naming a `profile.yaml` key | write the key as `value` and `provenance`, as the README shows |
| `named no root` | give the root as the argument |
| a refusal naming `--db-dir` | pass the working directory this command actually opened, or none |
| a line naming the command that holds the working directory | another `vespera` is running there; wait for it or stop it |
| the database file is held by another process | close whatever else has `vespera.db` open |
| the converter runs another image | set `VESPERA_DOCLING_IMAGE`, or start the sidecars with the other files |
| the converter did not come back | start the sidecars, then run again; extraction continues where it stopped |
| the model did not answer | check Ollama is up and the model is pulled, then run again |
| a walk that no longer agrees with its checkpoint | the archive changed while a walk was unfinished; Vespera will not continue that walk, and running again gives the same refusal until the tree is as the walk left it |

## Copying or moving a working directory mid-run

Copy it only when no command is running and `vespera.db-wal` is gone. If you must copy while one runs, copy `vespera.db`, `vespera.db-wal` and `vespera.db-shm` together. Then pass the new location as `--db-dir` on every invocation. The ledger records the archive by its root, so keep the archive where it was.
