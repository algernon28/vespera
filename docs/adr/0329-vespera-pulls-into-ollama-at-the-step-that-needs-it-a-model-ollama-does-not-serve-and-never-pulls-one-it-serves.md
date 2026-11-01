# ADR-329 — Vespera pulls into Ollama, at the step that needs it, a model Ollama does not serve, and never pulls one it serves

- **Date**: 2026-10-31
- **Status**: accepted on 2026-10-31.
- **Decided**: by the operator, who decided that Vespera pulls into Ollama, through `/api/pull` and showing its progress, a model the profile names, or the default, that Ollama does not serve, before the step that needs it: the embedding model for stage 5's scoring step and the generation model for the generation step; that a model Ollama already serves is never pulled again; and that `hf.co/…` GGUF names are covered as Ollama accepts them. Where the pull is made, which commands make it, the opt-out, how progress shows, the bound on the wait, an interrupted pull, the failure lines and the tests are decided under delegation.
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md), its *What this does not decide*, *"Pulling Ollama's models"*, which this record decides (§1 to §9). Its rejected alternative, *"Pull Ollama's models after `up`"*, stays rejected in that place: the starter pulls nothing, and the pull is made at the step (§1). §4's *"Never run: … `pull`"* is `docker compose pull`, an image, and stands: nothing here runs a compose command.
- **Amends**: [ADR-202](0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md) §2, *"Embedding scoring and generation each ask once per invocation"*: where the check refuses and §3 below pulls the model, the check is asked a second time, after the pull, and its second answer is the one that stands (§3). §3's third case, a 404 from `/api/show` refused as a model never pulled, is met by those two steps only where pulling is off, the name's tag ends in `cloud`, or the model is still not served once a pull has ended (§3). §1's one implementation of the rule stands: the check is `LocalOllamaModel.refusalOf`, unchanged, and the pull is asked around it, never in place of it.
- **Amends**: [ADR-298](0298-the-embedding-model-ships-with-a-default-in-application-configuration-which-the-profile-overrides-and-stage-5-is-gated-only-where-neither-names-one.md) §6, *"A default nobody pulled is refused, not gated"* and its remedy, *"`ollama pull bge-m3:latest`, or another model named in the profile"*: a default nobody pulled is pulled at stage 5's scoring step, and refused as §6 says only where pulling is off or the pull fails (§8, §9).
- **Keeps**: [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md) and [ADR-231](0231-the-embedding-step-stops-unrecorded-where-the-embedding-model-was-pulled-while-it-ran-and-scoring-refuses-a-seed-whose-chunks-have-no-vector.md): the embedder identity is read from what Ollama reports once the pull has ended, before the scoring run is minted, as it is read today; nothing changes in how a vector is keyed (§10).
- **Keeps**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) and ADR-202 §3: a model whose tag ends in `cloud` is never pulled, and a model pulled is sent nothing until ADR-202's check, asked again, says it runs on this machine (§3).
- **Keeps**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5: the control panel pulls nothing itself; the jar it starts as its child does what this record says.
- **Keeps**: [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md): every class this record adds or changes is in `pipeline`, and none decides a verdict, a cache key, a cluster or text of the deliverable. No run id moves (§11).
- **Settles**: [#669](https://github.com/algernon28/vespera/issues/669).
- **Rests on**: Ollama's source at `v0.33.2`, the version `compose.yaml` pins, read and not run against the image: `server/routes.go` (`PullHandler`, `ShowHandler`, `streamResponse`, `getExistingName`), `server/images.go` (`PullModel`, `PruneLayers`), `server/download.go` and `api/types.go`.

## Context

### What holds before this record

A model Ollama does not serve stops the step that would send it text. Stage 5's scoring step and the generation step each put the model's name through `LocalOllamaModel.refusalOf` once their gates are open and before their run is minted (ADR-202 §2), and Ollama answers `/api/show` with 404 for a name it has never pulled, which the check refuses as *"Ollama serves no model named `<name>`: it has not been pulled on this machine, so nothing is sent to it"* (ADR-202 §3). The operator then pulls it by hand, with the README's `docker compose -p vespera exec ollama ollama pull …` lines, and runs the same command again. ADR-298 §6 names that pull as the remedy for a default nobody pulled, and ADR-323 left pulling out of what Vespera starts.

The model is the one `profile.yaml` names, `embeddingModel` or `generationModel`, or the default in `application.yaml` where the profile names none (ADR-114, ADR-298 §1). Naming another embedding model, such as one of Qwen's GGUF files on Hugging Face as `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:<quantization>`, therefore costs a stop, a pull by hand of several gigabytes, and a second invocation.

### What Ollama does, read at `v0.33.2`

- **The request.** `POST /api/pull` takes `PullRequest`: `model`, and `stream`, a pointer that streams unless it is `false`. The answer streams one `ProgressResponse` per line, `status`, `digest`, `total` and `completed`, the last three omitted when empty. With `stream` false the handler waits for the end and answers once.
- **The lines.** `PullModel` sends `pulling manifest`; then, for each layer, `pulling <12 hex digits of its digest>` with the layer's `total` and `completed` bytes, sent again as the layer goes on; then `verifying sha256 digest`, `writing manifest` and `success`.
- **The manifest is written last.** Every layer is downloaded, then every layer is verified, and only then is the manifest written. A model is served only once its manifest exists, so a pull that stops part-way leaves no model that `/api/show`, `/api/tags` or a call can find.
- **Failures.** A name that cannot be parsed is answered before the stream with HTTP 400 and `{"error": …}`. A failure inside `PullModel` comes as a line `{"error": …}` within an answer that has already begun with status 200: a name the registry does not have as `pull model manifest: file does not exist`, a download that failed six times as `max retries exceeded: <the last error>`, and a write to a full drive as the operating system's error, unretried. No free space is checked beforehand.
- **A part that stops sending is started again, without limit.** `download.go` restarts a stalled part without counting it against the six retries, so a connection that stays open and sends nothing can keep a pull going for as long as it lasts.
- **What an interrupted pull leaves.** A blob is downloaded into `<blob>-partial`, with its parts' progress in `<blob>-partial-<n>`; neither is removed on a failure or a cancellation, and the next pull of a model with that blob resumes each part where it stopped. A download carries on while any request waits on it and is cancelled once none does, so a client that closes its connection stops it, keeping what it has written. `PruneLayers` removes, as invalid blobs, files of the blobs folder that are older than an hour and are not a blob's digest, which its comment says includes partial downloads, so a pull resumed more than an hour after it stopped may start over.
- **A pull of a name already served fetches its manifest again.** `PullModel` always asks the registry for the manifest and replaces the local one, removing layers the new one no longer names. A name published again with other contents therefore changes under a pull: another digest, and so another embedder identity (ADR-228).
- **`/api/show`** answers 404 with `model '<name>' not found` for a name it has no manifest for. Before that, `getExistingName` matches the name against the manifests it has, part by part and ignoring case, and fills in the tag a name without one is given.
- **`hf.co` names** are pulled through the same handler, from Hugging Face's registry, the name parsed as host `hf.co`, namespace, repository and tag.

**Not measured:** no pull was run against an Ollama container: the lines and failures above are read from the source. How long a pull of several gigabytes takes, the words Hugging Face's registry answers with for a quantization a repository does not hold, whether pulling a cloud model's stub needs a sign-in, and when `PruneLayers` is called.

## Decision

### 1. The pull is made at the step that needs the model, not when the sidecars are started

**Stage 5's scoring step pulls the embedding model, and the generation step the generation model**, each where it asks ADR-202's check today: once its gates are open, and before its run is resolved. Nothing else pulls a model.

- **The name is known only there.** The step reads it from the profile and the configuration through its gates (ADR-202 §2, ADR-244), and the starter runs before the profile is read. A model pulled at the start could be one the step never sends to, or not the one it does: the profile edited between the two, or the embedding model blanked to hold scoring (ADR-298 §2).
- **A gated step pulls nothing.** An invocation that stops at the boilerplate floor, the seed folder or the arrangement's approval does not spend minutes, and gigabytes, on a model it will not reach. The generation model, `qwen3:8b` unless set, is pulled at invocation 5 and not at invocation 1.
- **It is pulled whoever started the sidecars.** The starter does nothing where `VESPERA_SIDECARS_START` is false, or where a base URL names another machine (ADR-323 §3); the step's pull does not depend on either (§9).

### 2. `vespera run` pulls; `vespera label --auto` does not

The two steps are `vespera run`'s, so `vespera run` pulls. `vespera label --auto` embeds nothing: it asks the labeller's chat model, `vespera.label.model` or else `spring.ai.ollama.chat.options.model` (ADR-197), which is neither the profile's embedding model nor its generation model. It pulls nothing, and a labeller's model Ollama does not serve is refused as ADR-197 §6 and ADR-202 §3 refuse it today. `vespera label` with a file and `vespera publish` ask Ollama nothing.

### 3. What the step does, in order

1. **ADR-202's check is asked**, `LocalOllamaModel.refusalOf(name, ollamaClient)`, as today. No reason: the model is served on this machine, nothing is pulled, and the step goes on. Ollama is asked about it once, as ADR-202 §2 has it.
2. **`vespera.models.pull` is false**: the check's reason stands, and the step stops as ADR-202 §3 says (§9).
3. **The name's tag ends in `cloud`**, read as ADR-202 §3's first case reads it, lower-cased: the check's reason stands. Such a model is refused from its name alone, and a stub pulled for it would be sent nothing.
4. **`/api/show` is asked again**, through `OllamaClient.isRemote`, the client the check asks. A 404 is a model Ollama does not serve: it is pulled (5). Any other answer, 2xx, another status or none, means the reason was something else, a model reported as remote or a daemon that cannot answer, and the check's reason stands. **A model Ollama serves is never pulled**: a 404 is the one way to `/api/pull`. The name is the one the step's gates read and the check was asked about, and it is never rewritten: Ollama's own reading of it, ignoring case and filling in a tag, decides whether it is served, and the same reading decides what it pulls.
5. **The model is pulled** (§4 to §7). A pull that does not end in `success` stops the step with §8's reason.
6. **ADR-202's check is asked again**, and its answer is final: no reason, and the step goes on, its run resolved and its identity read from Ollama as today (§10); a reason, a model reported as remote once pulled or one Ollama still does not serve, and the step stops as ADR-202 §3 says, nothing sent.

**A stop is ADR-202's stop.** One line at error level, from the step's own words: *"stage 5's scoring step stopped: the embedding model <name> was refused, so nothing was embedded and no run was minted -- <reason>"* and the generation step's line, as today, with §8's reason where the pull failed. The step is failed, no exception is thrown, no run is minted, and nothing is sent to the model. The invocation exits non-zero.

### 4. The request

`POST /api/pull` to `spring.ai.ollama.base-url`, `http://localhost:11434` unless set, the daemon `OllamaClient` asks (ADR-202's *Which daemon `/api/show` is asked* stands), whatever its host. The body is `{"model": "<name>"}` and nothing else, so the answer streams. HTTP/1.1, as ADR-323 §3 asks Ollama. The name is sent exactly as resolved: an `hf.co/<namespace>/<repository>:<tag>` name is sent as written, its case kept.

**Never sent**: `/api/delete`, `/api/copy`, `/api/create`, `/api/push`, `"stream": false`, or a second `/api/pull` in one step. Nothing in Ollama's volume is removed by Vespera, whatever ends the pull.

### 5. Progress: lines on the console, the progress page and the invocation account

Three kinds of line, at INFO, in the step's own voice (ADR-093), `<stage>` being `Stage 5c (embedding scoring)` or `Stage 6b (generation)`:

```
<stage> Ollama serves no model named <name>; pulling it
Stage 5c (embedding scoring, megabytes pulled): <done> of <total> (<p>%)
<stage> pulled <name> into Ollama in <n> seconds
```

- **The counter line** is `StageProgress`'s shape, `Stage 6b (generation, megabytes pulled): …` for the generation step. `<done>` is the bytes Ollama has reported `completed`, and `<total>` the bytes of its `total`, each summed over the layers it has named so far and divided by 1,000,000, rounded down and grouped as `StageProgress` groups a count; `<p>` is `done × 100 / total` in bytes, rounded down. One line is written each time the pulled bytes reach a twentieth of that total greater than the last one written, and none at nought: at most one line per 5%, and nothing per line Ollama sends (ADR-192). A layer named after the others, which raises the total, writes nothing until a further twentieth is reached.
- **It reaches the progress page and the invocation account** as every counter line does: written on the step's thread in `COUNTER_LINE`'s shape, it is the step's last progress line, drawn with its bar and its chart (ADR-265, ADR-267), and kept in the account (ADR-198). It names no model, so it carries nothing the page and the account keep out.
- **The first and last lines name the model** and reach the console and the log, not the page. `<n>` is whole seconds since the request was sent.

### 6. The bound: on a pull that has stopped moving, not on its length

A pull of several gigabytes takes as long as the network lets it, and no bound on the whole of it would fit both a model of 600 megabytes and one of 16 gigabytes. `vespera.sidecars.wait-at-most` is how long a container takes to start (ADR-323 §7) and is not this.

**`vespera.models.pull-stalled-at-most`, 600 seconds unless set**, bounds the time since the pull last moved. It moved when a line arrives whose `status` differs from the line before it, or whose `completed`, for the layer its `digest` names, is greater than that layer's before. A line that repeats the last one changes nothing, and neither does silence. At the bound Vespera closes the connection and the step stops with §8's reason. The bound holds whether Ollama goes on sending lines that change nothing or sends nothing at all: the wait ends no later than the bound after the last change. Nothing has moved before the first line, so the wait for the answer's status, and for the whole body of an answer whose status is not 2xx, ends no later than the bound after the request was sent. It is one bound for the two waits together, not one for each: a status that comes late leaves the body what remains of the bound, so the two never take twice it.

600 seconds because Ollama starts a stalled part again without limit (Context), so without a bound of Vespera's a connection that stays open and sends nothing could hold the step for as long as it lasts; and long enough for the one stretch in which a whole pull reports nothing new, Ollama checking the digest of a layer of 16 gigabytes after the last byte, which a slow drive takes minutes over. Reasoned, not measured. It is an application property and no profile value, for ADR-175 §2's reason: it says how this machine is connected, not what has been judged about a corpus.

### 7. An interrupted pull

A pull ends without `success` when §6's bound passes, when Ollama answers with an error, when the connection drops, or when the Vespera process ends. In every case:

- **No model is half there.** Ollama writes the manifest only after every layer is downloaded and verified (Context), so the name is still not served: the next check finds a 404 and nothing is sent under it.
- **What was downloaded is Ollama's.** Its partial files stay in Ollama's volume, and the next pull resumes them. The next `vespera run` that reaches the step pulls again, from where Ollama stopped. Vespera deletes nothing, as §4 says; a pull resumed more than an hour later may start over, if Ollama has pruned what it kept.
- **Nothing was recorded.** The pull comes before the step's run is resolved (§1), so no run is minted, no vector or answer stored, and the ledger holds what it held before the step began.

Two invocations on two working directories that reach a pull of the same model at once each send their own `/api/pull`, and Ollama downloads each blob once for both: a blob's download is shared by every request waiting on it. Vespera takes no lock of its own for it.

### 8. The reasons a pull fails with

Each is one line, the `<reason>` of §3's stop line. `<name>` is the model as resolved; `<Ollama's words>` is the `error` Ollama answered with, or, for an answer whose status is not 2xx and whose body is not JSON naming an `error`, that body stripped of the space around it, each line break in it replaced by one space, so the line stays one; `<n>` is `vespera.models.pull-stalled-at-most` in whole seconds.

- **Ollama answers the request with a status that is not 2xx**, a name it cannot parse among them:
  > Ollama would not pull <name> (<status code>: <Ollama's words>), so nothing of it was pulled; correct the name and run the same command again
- **Ollama sends an error line**, a name the registry does not have, a registry it cannot reach or a drive that is full among them:
  > Ollama could not pull <name> (<Ollama's words>); it serves nothing of it until a pull ends, and the next pull resumes what was downloaded. Correct the name, make room on Ollama's drive or restore its network, as Ollama's words say, and run the same command again
- **The answer ends without `success` and without an error**, or the connection fails, `<what happened>` being `the answer ended before the pull did` where it ended, and the failure as the transport reported it otherwise. That is the message of the innermost cause the transport's exception carries, or the simple name of that cause's class where it has no message or a blank one: the JDK's client reports a connection dropped in the middle of the answer as `closed`, around the failure that closed it, and a wrapper's words, or none, tell the operator nothing. The walk from the exception to its innermost cause stops at a cause it has already passed, so a chain of causes that leads back into itself ends at the last cause before the loop, and the reason is still written:
  > Ollama stopped answering while pulling <name> (<what happened>); it serves nothing of it until a pull ends, and the next pull resumes what was downloaded. Run the same command again once Ollama answers
- **The pull stalls past the bound** (§6), an answer whose status has not come within it among them:
  > Ollama pulled nothing more of <name> for <n> seconds, so Vespera stopped waiting; it serves nothing of it until a pull ends, and the next pull resumes what was downloaded. Check the network of the machine Ollama runs on and run the same command again, or set vespera.models.pull-stalled-at-most longer
- **Ollama answers with a status that is not 2xx, and the rest of that answer does not come within the bound** (§6) **or cannot be read**, `<what went wrong>` being `had not come <n> seconds after the pull was asked for` where it did not come, `<n>` counted from the request as §6 counts it, and `could not be read: <what happened>` otherwise, `<what happened>` being the failure as the transport reported it, as the line for a connection that fails says. The status Ollama sent is kept: it already says that Ollama refused the pull and that nothing was pulled, which the two lines above would hide behind advice about the network or about Ollama answering again. The remedy is the first line's, as a refusal is about the name and not the network:
  > Ollama would not pull <name> (it answered <status code>, and the rest of its answer <what went wrong>), so nothing of it was pulled; correct the name and run the same command again

**Ollama's words are quoted and not sorted.** An unknown name, a network that is down and a full drive each reach the operator in one line naming the model, with what Ollama said and a remedy that covers the three. Vespera does not read the words to choose among them: they are Go errors Ollama wraps as it goes, they differ between Ollama's registry and Hugging Face's, and a remedy chosen from a misread word would send the operator after the wrong fault. A name Ollama cannot resolve is the first line, where it cannot parse it, or the second, where the registry has nothing under it.

### 9. The opt-out is a setting of its own

**`vespera.models.pull`, true unless set, `VESPERA_MODELS_PULL=false` in the environment.** False, the step never asks `/api/show` a second time and never sends `/api/pull`, and a model Ollama does not serve is refused in ADR-202 §3's words, as before this record.

Not `VESPERA_SIDECARS_START=false`. That setting is for a machine whose sidecars are started some other way, Podman, rootless Docker, another machine, or kept stopped, and each of those Ollamas can pull. It is also what the integration tests that launch the jar set. An operator who wants the sidecars left alone still wants a missing model pulled; one who wants no model pulled behind their back, on a metered connection or to choose every pull, may still want the sidecars started. It is an application property and no profile value, for §6's reason.

`application-test.yaml` sets it false, so no test context sends `/api/pull` to an Ollama on the machine running the tests; a test that means to pull sets it true and points `spring.ai.ollama.base-url` at a stub.

### 10. The identity is read after the pull, and nothing changes in how vectors are keyed

The step resolves its run after §3 has ended, so the embedder identity of the scoring run (ADR-084, ADR-228) and the generator identity of the generation run (ADR-114) are read from `/api/tags` once the pulled model is served, through `StageRuns` as today: the digest and weight dtype of what Ollama now serves. A model pulled by Vespera is keyed as the same model pulled by hand.

**A pull here never changes a model a run was minted under.** Only a name Ollama does not serve is pulled, so no pull of this record replaces a manifest, changes a digest, or leaves a scoring run behind (ADR-228); and it comes before the scoring run is minted, so ADR-231's check at the end of the step is unchanged and is met only by a pull made by someone else while the step embeds. The README's warning about pulling the embedding model again stands, for a pull made by hand.

### 11. Where it lives, and no run id moves

**`ModelPull`**, a class of `pipeline` built by Spring from the environment and the `OllamaClient` bean, or by a test from a `ModelPullSettings` and a client, holds §3 to §8: `refusalOf(String stage, String modelName)` answers the reason the model must not be sent anything, as `LocalOllamaModel.refusalOf` answers it, after pulling it where §3 says to. **`ModelPullSettings`**, a record of `pipeline`, reads `vespera.models.pull`, `spring.ai.ollama.base-url` and `vespera.models.pull-stalled-at-most`, whose defaults are written once in it, as `SidecarStartSettings` writes its own (ADR-323 §7).

`EmbeddingScoringTasklet` and `GenerationTasklet` call `ModelPull.refusalOf` with their stage, `Stage 5c (embedding scoring)` and `Stage 6b (generation)`, where they call `LocalOllamaModel.refusalOf` today, and no longer name `LocalOllamaModel` or `OllamaClient`; `ModelPull` names both. Their stop lines are unchanged.

**`embedding` is not touched**, its javadoc included: `LocalOllamaModel` and `OllamaClient` are called as they are. The stages whose versions name `embedding`, seed measurement, embedding scoring, the arrangement and generation, would otherwise move their run ids for a change that decides nothing they record. No stage's version names `pipeline` (ADR-222), and `ModelPull` decides no verdict, cache key, cluster or text of the deliverable: it decides whether Ollama is asked to fetch something. No run id moves, and no pass is owed.

## Alternatives rejected

- **Pull in ADR-323's starter, after `up`.** §1: the profile is not read there, a gated invocation would pull models it never sends to, and an Ollama the starter does not start, on another machine or under Podman, would never be given one.
- **Spring AI's `spring.ai.ollama.init.pull-model-strategy`.** It pulls the models named in `application.yaml` when the context starts: not the profile's, on every command including `vespera publish` and `--help`, with Spring AI's own timeout and lines.
- **Ask `/api/tags` whether a model is served.** It lists names as Ollama stores them, `bge-m3:latest` for a profile's `bge-m3`, and comparing them would copy Ollama's reading of a name into Vespera; where the copy fell short, a model Ollama serves would be pulled again, which is what may change it. `/api/show` is Ollama's own reading.
- **Pull whatever the step names, every time, to keep it current.** A pull of a served name fetches its manifest again (Context): the embedding model could change under the operator's corpus, the scoring run and every run after it would move, and the arrangement would need approving again (ADR-228, ADR-107).
- **`"stream": false`.** Nothing to show while it runs, and no way to tell a stalled pull from a slow one.
- **A bound on the pull's whole length, or `vespera.sidecars.wait-at-most`.** §6: any length that fits a large model holds a stalled small one, and three minutes cuts a pull of a large one.
- **`VESPERA_SIDECARS_START=false` as the opt-out.** §9: the two are about different things, and each is wanted without the other.
- **Sort Ollama's words into an unknown name, a network down and a full drive, each with its own remedy.** §8.
- **Delete what an interrupted pull left, through `/api/delete` or otherwise.** Ollama resumes from it; deleting would make every interrupted pull start over, and the partial files are not a model anything can send to.
- **Pull the labeller's model in `vespera label --auto`.** §2: it embeds nothing, and its model is neither of the two the profile names.
- **Pull both models at the job's start.** §1's reasons, and the generation model would be pulled at the first invocation, four before it is used.

## Consequences

- **A fresh machine with no models** pulls `bge-m3:latest`, 1.2 GB, inside stage 5's scoring step of invocation 2, and `qwen3:8b` inside the generation step of invocation 5, each in the log and on the progress page as it goes. A profile naming `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:<quantization>` pulls that file there instead.
- **The pull runs inside the step's transaction**, before the step writes anything; it holds no write of the ledger open, and no other invocation can reach the same working directory meanwhile (ADR-177).
- **`application.yaml` gains `vespera.models.pull: true` and `vespera.models.pull-stalled-at-most: 600s`**, each with a comment citing this record; **`application-test.yaml` gains `vespera.models.pull: false`** (§9).
- **The README's "Give Ollama its models"** says Vespera pulls each model at the step that needs it, how to turn that off, what an interrupted pull leaves and what a failure says, and keeps the `ollama pull` lines for a machine where pulling is off and for `vespera label --auto`'s model. **`docs/running-stage-by-stage.md`'s** two *Needs* lines and the stop it describes for a model Ollama does not serve say the same.
- **ADR-202, ADR-298 and ADR-323 carry a note at their heads.**
- **The control panel's launch page** still warns that Ollama does not serve a model named, as ADR-302 has it; the run it starts pulls that model at the step that needs it.

## What this does not decide

- **`vespera label --auto`'s model** (§2). Whether it should be pulled too is left open.
- **The control panel's warning.** Whether it should say that the run pulls the model.
- **A name Ollama serves under another spelling.** Where the name differs from Ollama's stored one in case or in a tag left off, `/api/show` finds it, so it is served and not pulled; whether `OllamaClient.artefactOf`, which reads `/api/tags` by the name exactly, then finds it is ADR-202's *Consequences* case, unchanged.
- **Pulling into an Ollama on another machine.** It is pulled there, as the step sends there; whether the two steps should refuse an Ollama that is not this machine stays ADR-202 §5's open question.
- **Free space.** Neither Ollama nor Vespera checks it before a pull; a full drive is §8's second line.
- **Ollama's behaviour, run.** Every fact of the Context about Ollama is read from its source at `v0.33.2`.

## Tests

New, under `src/test`, all in `io.algernon.vespera.pipeline` and needing no Docker and no Ollama:

- **`StubOllama`** (test support): a loopback server answering `/api/show` with 200 for each name it serves and 404 with `model '<name>' not found` for any other, and `/api/pull` from a script per name: lines of NDJSON, each sent after a pause of its own, then the answer ended or kept open; or a status and a body before any line; or no status at all; or a status that is not 2xx, sent after a pause of its own, and the start of a body whose rest never comes, or after which the connection is dropped; or lines and then the connection dropped. A pull that ends in `success` adds the name to those it serves, or runs a callback in its place. It records every request's path and body, in order, and each pull whose client it found gone while it was still sending.
- **`ModelPullTest`** (unit, a `ModelPull` built on a `StubOllama`, a real `OllamaClient` pointed at it and a short bound):
  - a model served is not pulled: no `/api/pull`, and `/api/show` asked once;
  - pulling off: a model not served is refused in ADR-202's never-pulled words, and no `/api/pull` is sent;
  - a name whose tag ends in `cloud` is refused as a cloud model and not pulled;
  - `/api/show` answering 500: refused in ADR-202's could-not-be-established words, not pulled;
  - a model not served is pulled once, with the body `{"model":"<name>"}` alone, and then served: no reason;
  - an `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q8_0` name is sent as written, case and all;
  - the progress lines: a layer of 100,000,000 bytes reported at 0, 25, 50, 75 and 100 million gives exactly four counter lines, 25 to 100 of 100, each matching `StageProgress.COUNTER_LINE`, between the first line and the last;
  - a model reported as remote once pulled is refused in ADR-202's remote words;
  - §8's reasons, each whole: a 400 with `invalid model name`; an error line `pull model manifest: file does not exist`; an error line about a full drive; an error line about a registry that cannot be reached; an error carrying a line break, which the reason carries as a space; an answer ended after two lines; a stream of lines that change nothing past the bound; silence past the bound; each of the last two ending within the bound and a margin.
  - the transport's failures and the bound's other waits: a 502 whose body is not JSON, quoted stripped; a connection refused, and one dropped after two lines, each quoting the innermost cause of the transport's failure, never `null` or `closed`; no status at all past the bound, given up as stalled, and a status that is not 2xx whose body stops coming, given up with that status kept, each within the bound and a margin, under a timeout that fails rather than hangs; a status that is not 2xx sent three quarters of the way into a bound of two seconds and its body then stopping, given up with that status kept no sooner than the bound after the request and within it and half of it, which one wait of the bound for the status and another for the body would overrun; a status that is not 2xx whose body the connection drops, the status kept and the innermost cause of the transport's failure quoted; and a pull given up at the bound whose connection the stub, still sending, finds closed. A cause chain that leads back into itself, and an innermost cause with no message, are not built: the JDK's client makes neither from anything a loopback server sends, and `ModelPull` takes no transport a test could put in its place.
- **`ModelPullSettingsTest`** (unit): pulling is on, the bound 600 seconds and Ollama at `http://localhost:11434` unless set; each is read from its property; `vespera.sidecars.start` false leaves pulling on.
- **`AModelOllamaDoesNotServeIsPulledInvocationTest`** (`@CascadeSliceTest`, pulling on, `spring.ai.ollama.base-url` at a `StubOllama` whose pulls tell `EmbeddingScriptedBeans` the model is now served, the embedding model's under a new digest):
  - an embedding model Ollama does not serve is pulled once at stage 5's scoring step, chunks are embedded, and the scoring run's settings name the digest Ollama reports after the pull;
  - an embedding model Ollama serves sends no `/api/pull`;
  - a pull that fails with an error line stops the invocation non-zero with one line at error level naming the step, the model and Ollama's words, no stack trace, no chunk embedded and no scoring run;
  - a generation model Ollama does not serve is pulled at the generation step, questions are put, and a generation run is minted.
- **`ShippedConfigurationTest`** gains: `application.yaml` ships `vespera.models.pull` true and `vespera.models.pull-stalled-at-most` 600 s.

Amended:

- **`EmbeddingScriptedBeans`** gains `hasNowPulled(name)`: a name scripted as never pulled is served from then on, under the digest the fixture reports.
- **`CascadeSliceTest`** imports `ModelPull`.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`**: `ModelPull` and `ModelPullSettings` are on record as holding no rule; `ModelPull` names `embedding.LocalOllamaModel` and `embedding.OllamaClient`, and `EmbeddingScoringTasklet` and `GenerationTasklet` name neither.
- **`application-test.yaml`**: `vespera.models.pull: false`.

`EmbeddingRefusesAModelNotServedHereInvocationTest` and `GenerationRefusesAModelNotServedHereInvocationTest` are unchanged: under the test profile pulling is off, and their never-pulled cases are refused in ADR-202's words, as §9 says.

What no test here shows is what a real Ollama sends: the lines and failures the stub sends are those read from Ollama's source.
