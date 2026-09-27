# ADR-165 — Ollama is given an NVIDIA GPU by an override file, and compose.yaml alone asks for none

- **Date**: 2026-09-27
- **Status**: accepted
- **Extends**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md). The operator still starts the sidecars from `compose.yaml`, and the README's one `up` line still starts all three on any machine with a Docker daemon. What this record adds is a second file, `compose.gpu.yaml`, that an operator with an NVIDIA GPU names beside the first, so that Ollama runs its models on the GPU.
- **Keeps**: [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md) as it is. Its probe H is why this change costs the operator Ollama's models once (Consequences). [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) and [ADR-091](0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md): the embedder identity stays what Ollama reports plus what Vespera sends, and gains no part for the device (§3). [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md)'s generator identity is unchanged too. [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md) and [ADR-163](0163-the-docling-sidecar-pins-docling-parse-7-17-0-and-its-image-is-tagged-for-the-pin.md): the Docling sidecar stays on its CPU image.
- **Rests on**: measurements made on 2026-09-27 on one machine: Windows 11, Docker Desktop over WSL 2, Docker Engine 29.6.1, Docker Compose v5.3.0, an NVIDIA GeForce RTX 5060 Ti with 16 GB. The timings come from a throwaway `docker run --gpus all` container beside the running stack. The Compose probes ran in a throwaway project, `-p p165probe`, and the vector comparison in a throwaway container, `p165-gpu-embed`, on a spare port. All were removed afterwards. The running `vespera` containers were only read from.

## Context

`compose.yaml` runs Ollama as `ollama/ollama:0.33.2` and asks Docker for no device. So on this machine, which has an NVIDIA GPU, Ollama runs on the processor:

- `docker inspect vespera-ollama-1` shows `HostConfig.DeviceRequests` as `null`.
- Its log reads `inference compute id=cpu library=cpu`.
- `ollama ps` shows `100% CPU` under `PROCESSOR` for both models.

A throwaway `docker run --gpus all ollama/ollama:0.33.2` on the same machine logged `library=CUDA compute=12.0 name=CUDA0 description="NVIDIA GeForce RTX 5060 Ti" … total="15.9 GiB"`, and `ollama ps` showed both models wholly in the GPU's memory: `qwen3:8b` in 6.3 GB, `bge-m3` in 0.66 GB.

The same work was sent to both:

| Work | CPU (the `vespera` stack) | GPU (throwaway) |
| --- | --- | --- |
| `bge-m3` embedding 300 chunks of 400 words, 16 per call | 145.3 s | 22.4 s |
| One `qwen3:8b` call shaped like 6b's largest cluster on GesPOS: 4,098 prompt tokens, about 400 out, `num_ctx` 8192, no thinking | 93.9 s | 12.6 s |
| of which reading the prompt | 52.5 s | 1.3 s |
| of which writing the answer | 9.3 tokens/s | 36.0 tokens/s |

The embedding row sent 16 chunks per call. Vespera sends one per call (`ChunkEmbedder.callOnce`), so the speed-up at Vespera's own call shape was not measured.

On the 2026-09-27 GesPOS run, on the processor, stage 5's embedding step took 3 min 43 s and 6b took 14.5 min. Both are almost all Ollama.

Two more facts:

- **Nothing in continuous integration starts anything from `compose.yaml`.** No workflow runs `docker compose`, and the tests and `docs/check-claims.mjs` read the file only as text. The tests start their sidecars through `TestcontainersConfiguration`, and Testcontainers' `OllamaContainer` (2.0.5) already asks for every GPU when, and only when, the Docker daemon lists an `nvidia` runtime. So on this machine the integration tests already run Ollama on the GPU, and the runtime does not.
- **`compose.yaml` says otherwise.** Its `docling-serve` comment reads *"ADR-013's Ollama default is the only serving runtime this repo currently GPU-accelerates"*. ADR-013 says nothing about a GPU, and the compose Ollama is not accelerated. The comment is false as deployed. This record corrects it in `compose.yaml` (Consequences), not in ADR-013.

### What was measured through Compose

Each probe was a one-service project, `-p p165probe`, running `ollama/ollama:0.33.2`, brought up with `docker compose up -d` and read back with `docker inspect` and the container's log.

**A. Both ways of writing the request are accepted, and both reach the GPU.**

| Form in the service | `HostConfig.DeviceRequests` | Log |
| --- | --- | --- |
| `deploy.resources.reservations.devices: [{driver: nvidia, count: all, capabilities: [gpu]}]` | `Driver nvidia, Count -1, Capabilities [[gpu]]` | `library=CUDA … RTX 5060 Ti` |
| the same, `count: 1` | `Driver nvidia, Count 1` | `library=CUDA … RTX 5060 Ti` |
| `gpus: all` | `Driver "", Count -1, Capabilities [[gpu]]` | `library=CUDA … RTX 5060 Ti` |

`docker compose config` renders `count: all` as `-1`, the value `docker run --gpus all` sends.

**B. A request the machine cannot satisfy stops `up`, and Ollama with it.** There is no machine without an NVIDIA GPU at hand, so the probe asked for what this one does not have:

| Request | `docker compose up -d` | Container |
| --- | --- | --- |
| `driver: nosuchgpu`, in `deploy` | exit 1: `could not select device driver "nosuchgpu" with capabilities: [[gpu]]` | created, never started |
| `driver: nosuchgpu`, in `gpus:` | the same | created, never started |
| `driver: nvidia`, `count: 9` | `nvidia-container-cli: device error: 1: unknown device` | created, never started |
| `driver: nosuchgpu`, `count: 0` | exit 1, the same message as the first row | created, never started |

In a two-service project, `chroma` with no request and `ollama` with the impossible one, `up` exited 1: `chroma` was running and `ollama` was left `Created`.

So `count: 0` does not turn the request off: a count read from an environment variable cannot make one file serve both kinds of machine. On a machine whose Docker has no NVIDIA runtime, a request for driver `nvidia` is expected to fail as the first row does, naming `nvidia`. That exact message was not measured.

**C. Adding or dropping the override replaces the container.** A service `ollama` with `restart: unless-stopped` in a base file, and an override file carrying only its `deploy` block:

- `up -d` with the base file, then `up -d` with both: a new container, with the GPU request.
- `up -d` with both again: the same container.
- `up -d` with the base file alone: a new container, with `DeviceRequests` `null`.
- `stop` with the base file alone, then `start`: the same container, still with the GPU request. `exec` with the base file alone reaches it.

A new container is a new filesystem, and ADR-164's probe H measured that the models inside go with it.

**D. The merge.** `docker compose -f compose.yaml -f compose.gpu.yaml config`, over this repository's `compose.yaml` and an override holding only `services.ollama.deploy`, gives `ollama` its image, `restart: unless-stopped`, port `11434` and the device request, and leaves `chroma` and `docling-serve` as they were.

**E. The vectors differ between CPU and GPU, by little.** The same `bge-m3` (digest `79076464…`, `F16` on both) embedded 32 texts from the GesPOS seeds and documents, cut to 200 words, 1,024 dimensions, on the `vespera` Ollama (CPU) and on the throwaway (GPU):

| Comparison | Vectors bit-identical | Lowest cosine | Mean cosine |
| --- | --- | --- | --- |
| CPU, 16 per call, twice | 32 of 32 | 1 | 1 |
| CPU, 16 per call against 1 per call | 32 of 32 | 1 | 1 |
| GPU, 16 per call, twice | 32 of 32 | 1 | 1 |
| GPU, 16 per call against 1 per call | 32 of 32 | 1 | 1 |
| CPU against GPU | 0 of 32 | 0.999854 | 0.999982 |

The largest difference in one component was 0.0018. Over the 496 pairs of the 32 texts, the similarity of a pair moved by at most 0.0014 between CPU and GPU, and by 0.00027 on average. Each device repeats itself exactly, whatever the batch.

## Decision

### §1. The GPU is asked for by `compose.gpu.yaml`, which the operator names beside `compose.yaml`

The repository gains `compose.gpu.yaml`, next to `compose.yaml`. It carries one service, `ollama`, and one key under it:

```yaml
services:
  ollama:
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
```

An operator with an NVIDIA GPU that Docker can use starts the sidecars with both files:

```
docker compose -p vespera -f compose.yaml -f compose.gpu.yaml up -d --build
```

`compose.yaml` asks for no device, for any service. On its own it starts all three sidecars on any machine with a Docker daemon, as the README's "Running it" says today.

**Opt-in, not the default, because the default has to start everywhere.** A GPU request that cannot be satisfied stops `up` with exit 1 and leaves Ollama created and never started (B). If `compose.yaml` carried the request, "Running it" would fail on every Mac, every machine with another maker's GPU, every Linux machine without NVIDIA's Container Toolkit, and every machine with no GPU at all. ADR-158's hand check, that "Running it" gets an invocation past stage 2 on a machine with only Java 26 and an empty Docker daemon, would stop holding. The operator this project knows has the GPU and wants it used. One more `-f` on the lines they run costs them less than a failing first command costs everyone else.

**The `deploy` form, not `gpus:`.** Both reach the GPU on this Compose (A). The `deploy` form is the only one Docker's own page on GPU support in Compose documents (read on 2026-09-27), and it names the driver. `gpus: all` sends an empty driver and leaves the daemon to pick one by capability.

**`count: all`, not `1`.** On this machine they are the same (A). On a machine with two GPUs, `all` lets Ollama spread a model that does not fit on one, and it is what `docker run --gpus all` and Testcontainers' `OllamaContainer` ask for.

**The file carries nothing but the request.** No image, port or restart policy of its own. Those stay in `compose.yaml` alone. `SidecarRestartPolicyTest` reads the restart policy from it. Nothing reads Ollama's image or port from it: `TestcontainersConfiguration` names the same image in its own constant, kept in step by hand, and `DoclingSidecarImageTest` reads only `docling-serve`'s image. So an operator with the GPU and one without run the same images on the same ports under the same policy (D).

### §2. Nothing else asks for a GPU

- **docling-serve stays on its CPU image.** ADR-147 and ADR-163 built and pinned `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`, and the image is part of the extractor identity. A GPU image would be a different image, converting under a different identity, and nothing here measured whether it converts faster or the same. That is a decision of its own, and nobody has asked for it.
- **Chroma** does no inference, and nothing reads it yet (ADR-142).

### §3. No identity changes, and a vector under one identity may have been made on either device

**The embedder identity gains no part for the device.** It stays `model=…;digest=…;dtype=…;dimension=…;instruction=…` (ADR-091). **The generator identity** stays the model, its digest, `num_ctx` and `num_predict` (ADR-110, ADR-159). So a curation that moves to the GPU partway continues the same runs.

This is a choice, and E is why it is not free: CPU and GPU do not produce the same vector. The identity is what Ollama reports plus what Vespera sends (ADR-091), and `/api/tags`, where `OllamaClient` reads the identity's reported parts, carries neither the device nor the server's version. Nothing reads either into the identity. So the record already has an output-determining property of the server outside the identity: an image bump of `ollama/ollama` is not in the key either. The device joins that property rather than being added to the key. The size of the difference is what makes that acceptable:

- **Each device repeats itself exactly** (E). A re-run on the same device reproduces its own vectors, which is what the vector cache (ADR-085) exists to save.
- **The vector cache is keyed by content and identity, not by device.** A re-run on the GPU over a cache the CPU filled reuses every cached vector and embeds only what is not there yet, on the GPU. The cache then holds vectors from both devices under one identity. No stored vector changes, so no score already taken moves.
- **A score taken over vectors from both devices differs from one taken over either alone by about a thousandth.** A pair's similarity moved by at most 0.0014 in E. A relevance score is a mean of top chunk similarities (ADR-020), and the labelling page shows it to two decimals (`RelevanceLabellingReport`), so the difference is below what the operator reads. A document scored within about 0.0014 of the relevance floor could land on the other side of it. That is accepted, and the measurement is recorded so that a reader who sees it knows where to look.
- **Generated text is not repeatable on one device either.** Vespera sends no `temperature` and no `seed` (ADR-108's options, of which only `num_ctx` and `num_predict` are sent), so two calls with the same prompt may already answer differently. The device adds nothing to what the generator identity promises.

Putting the device into the embedder identity was the alternative: a GPU and a CPU vector would never share a key. It would mean reading what served each call, which `/api/ps` reports only for a loaded model and can change between calls when memory is short, and it would re-embed the whole corpus on the first GPU invocation. It is rejected below.

## Alternatives rejected

- **The request in `compose.yaml`, on by default.** Stops `up` on every machine Docker cannot give an NVIDIA GPU to (B), and so breaks "Running it" for every operator without one.
- **The request in `compose.yaml`, with a count or driver from an environment variable.** `count: 0` still stops `up` (B), and an empty driver leaves the daemon to find one by capability, which fails where no GPU driver is registered. Compose has no way to leave a request out on a condition.
- **A Compose profile.** A profile switches whole services on and off. It would take a second service, `ollama-gpu`, on the same port as `ollama`, so the README's `exec ollama` lines, and every command naming the service, would depend on which one the operator started.
- **A committed `compose.override.yaml`.** Compose merges that file whenever it is present, so committing it is the default-on alternative under another name.
- **An operator-local `compose.override.yaml`, or `COMPOSE_FILE` in a `.env` file**, so that a plain `up` finds the GPU file without being told. It would spare the operator the second `-f`. It is rejected because the README's commands would then no longer show which files make up the project: the same line would start Ollama on the GPU on one machine and on the processor on another, with nothing on the line to say which. A copied override would also stop following `compose.gpu.yaml` when the file changes.
- **`gpus: all`.** Works (A), and is rejected in §1 for the `deploy` form.
- **The device in the embedder identity.** Rejected in §3. The difference it would guard against is below the precision the operator reads scores at, and the cost is a full re-embed and a key part read from a source that can change between two calls.
- **A GPU image for docling-serve.** Not measured, a different extractor identity, and out of this record (§2).

## Consequences

- **The repository gains `compose.gpu.yaml`**, carrying §1's block and a comment saying what it is for: it gives Ollama every NVIDIA GPU Docker can reach, it is named with a second `-f` on every `up`, and `compose.yaml` does not carry it because a request no GPU can satisfy stops `up` (ADR-165).
- **`compose.yaml` asks for no device, and its comments say so truly.**
  - The `docling-serve` comment's parenthesis, *"(ADR-013's Ollama default is the only serving runtime this repo currently GPU-accelerates)"*, is false as deployed and comes out. The comment keeps its point: nothing asks the converter for GPU inference, and the CPU image is the one that runs anywhere the rest of the stack does. It says instead that Ollama is the one service that can be given a GPU, and only by `compose.gpu.yaml` (ADR-165).
  - The `ollama` service gains a comment: it runs on the processor from this file alone, and `compose.gpu.yaml` gives it the GPU (ADR-165).
  - Nothing else in `compose.yaml` changes, so an operator without the GPU file sees no container replaced by this change.
- **Taking the GPU costs Ollama's models once, and dropping it costs them again.** Adding `compose.gpu.yaml` changes Ollama's part of the project, so `up` replaces the container, and the models go with it (C, and ADR-164's H). On this machine that is `bge-m3` (1.2 GB) and `qwen3:8b` (5.2 GB), and `qwen2.5vl:7b` (6.0 GB) besides, which nothing in Vespera asks for. They are pulled again with the README's existing `ollama pull` lines. An `up` given `compose.yaml` alone after that replaces Ollama again, on the processor, and the models go again (C). `stop`, `start` and `exec` given `compose.yaml` alone replace nothing (C).
- **Whether Ollama's models should outlive a new container stays open.** A named volume for Ollama's model directory would make both of the above free. ADR-164 left it open because it changes what the README's `down` means, and this record leaves it open for the same reason. This record makes it more pressing: an operator who forgets the second `-f` once loses the models and the GPU together.
- **The README's "Running it" gains a paragraph** after the one on `-p vespera`. An operator whose machine has an NVIDIA GPU that Docker can use (Docker Desktop over WSL 2 on Windows, or NVIDIA's Container Toolkit on Linux) starts the sidecars with `docker compose -p vespera -f compose.yaml -f compose.gpu.yaml up -d --build`, and names both files on every `up` after that, since an `up` without the second file replaces Ollama with one on the processor and its models go. `stop`, `exec` and `down` need only `-p vespera`. Without such a GPU, the second file is left out, and with it `up` stops before Ollama starts. Once a model has answered, `ollama ps` shows under `PROCESSOR` `100% GPU` when the model is wholly on the card, a split such as `30%/70% CPU/GPU` when only part of it fits (not measured), and `100% CPU` when Ollama is not using the card. ADR-164's paragraph on a changed `compose.yaml` gains the same care: the `up` it shows is given `-f compose.yaml -f compose.gpu.yaml` if the sidecars were started with both, and the container replaced is that of each service whose part of `compose.yaml` or `compose.gpu.yaml` changed, since the bare line, copied by an operator with the GPU, replaces Ollama on the processor and its models go. `docs/check-claims.mjs` already requires every `docker compose` command in the section to name `-p vespera`, which the new line does; it does not check that `compose.gpu.yaml` exists. `OllamaGpuOverrideTest` pins the files' half.
- **Nothing under `src/main` changes**, and no `profile.yaml` key is added. This is how the sidecars are run, not a judgement about a corpus.
- **`TestcontainersConfiguration` does not change.** Its `OllamaContainer` already asks for every GPU where the daemon has an NVIDIA runtime, and nowhere else.
- **Neither identity changes** (§3), so no run is minted anew by a move to the GPU, and no cached vector or extraction is invalidated.
- **What the GPU saves was measured on one machine**, on one call and one batch of chunks (Context). It is a fact about that machine. How the whole cascade runs on it is for the next run to show, and a smaller GPU, onto which Ollama puts only part of a model, was not measured.

## Tests

`OllamaGpuOverrideTest` (unit, no Docker, reads `compose.yaml` and `compose.gpu.yaml` with SnakeYAML, as `SidecarRestartPolicyTest` does):

- **`composeYamlAloneAsksForNoDevice`**: no service in `compose.yaml` carries `gpus`, `runtime` or `devices`, or a `deploy.resources.reservations.devices` request, each of which is a way a Compose service can ask Docker for a GPU (`runtime: nvidia`, or a device name such as `nvidia.com/gpu=all` under `devices`, besides the two forms measured in A), so the file starts on any machine. Any other `deploy` key, a memory limit say, is left free. It passes today, and fails if the request is moved into `compose.yaml`.
- **`theOverrideGivesOllamaEveryNvidiaGpu`**: `compose.gpu.yaml` exists; it names exactly one service, `ollama`, which `compose.yaml` runs; that service carries `deploy` and nothing else; and `deploy.resources.reservations.devices` is one request, for driver `nvidia`, count `all`, capability `gpu`. It fails until `compose.gpu.yaml` exists.

What no unit test can show is what Docker does with the request. Probes A to E above are the evidence for it, and they are not a test.
