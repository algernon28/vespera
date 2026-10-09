# ADR-179 — No entry point starts the sidecars, the Docling sidecar reports the image it runs, and Ollama's models live in a volume

> **Read with [ADR-214](0214-chroma-is-removed-and-vectors-live-in-sqlite-alone.md).** Chroma is removed, so §5's *"Chroma stays without a volume"* has no object, and the Context's account of three tests setting `spring.docker.compose.enabled=false`, `VectorStoreIsReachedOnFirstUseTest` among them, is what was measured then. The note on `anIdeRunStartsNoContainer`, which names Chroma's connection-details class, is corrected when that test drops it, with ADR-214's implementation. This record's decision stands.

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md). Its *"ADR-011's single-command start still holds where Spring Boot's compose support is on the classpath, which is the development entry points only"* stops being true. No entry point starts or stops a sidecar any more. The operator starts them with the `up` line the README gives, and that holds for the jar, the IDE and `spring-boot:run` alike. Its *Keeps ADR-046* paragraph is replaced by §1 here: the two compose artifacts leave `pom.xml`.
- **Amends**: [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md), further than ADR-158 did. The repository still owns the sidecars, because `compose.yaml` declares them and pins their images and ports. No Vespera process starts them.
- **Amends**: [ADR-170](0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md) §3 and [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md). ADR-147's *"The sidecar does not report its image"* and ADR-170's *"nothing Vespera reads from the sidecar can catch a mistake"* stop being true. The image now reports its own name in `/version` (§2), and a step that would record conversions under `vespera.docling.image` first checks that the sidecar runs that image (§3). The operator still names the GPU image in `VESPERA_DOCLING_IMAGE`. Forgetting to now stops the step rather than poisoning the extraction cache.
- **Extends**: [ADR-163](0163-the-docling-sidecar-pins-docling-parse-7-17-0-and-its-image-is-tagged-for-the-pin.md) §2. The tag names the base release and the parser pin, and now also a revision of Vespera's own layers, `-r2` (§4).
- **Amends**: [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md)'s consequences and ADR-158's *"`compose.yaml` declares no volume, so `docker compose down` discards them with the container"*. Ollama's models live in a named volume (§5), so replacing or removing the container keeps them.
- **Keeps**: [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md) unchanged. Every service keeps `restart: unless-stopped`, and a restart runs the same image, so the identity does not change. [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) unchanged as well: the identity still carries the whole `/version` map, unfiltered, and the new entry goes into it like any other.
- **Settles** [#373](https://github.com/algernon28/vespera/issues/373).

## Context

### What happened

On 2026-09-30 the operator had started the sidecars the way the README says for a GPU machine:

```
docker compose -p vespera -f compose.yaml -f compose.gpu.yaml up -d --build
```

and `.env` set `VESPERA_DOCLING_IMAGE` to the GPU image. At 10:18 a whole-archive run was started from IntelliJ's `Local SpringApp` configuration. Spring Boot's Docker Compose support then ran its own `up`, from `compose.yaml` alone:

```
DockerComposeLifecycleManager - Using Docker Compose file …\compose.yaml
Container vespera-docling-serve-1 Recreate
Container vespera-ollama-1 Recreate
```

There were four consequences:

1. **docling-serve was replaced by the CPU image.** This was before ADR-172 put `DOCLING_SERVE_MAX_SYNC_WAIT` into `compose.yaml` itself, so the replacement waited only the default 120 s. Stage 2 stopped twice on a 504.
2. **1,398 conversions were cached under the GPU image's identity, although the CPU image made them.** The identity takes the image from `vespera.docling.image`, and both images report the same `/version` map (ADR-170, Output). So nothing noticed. This is the failure ADR-147 put the image into the key to prevent.
3. **Ollama was replaced too.** `compose.yaml` gives it no volume, so its models went with the old container, and `ollama list` was empty afterwards.
4. **When the run ended, Spring's lifecycle stopped all three containers.**

The operator has worked around it since by putting `SPRING_DOCKER_COMPOSE_ENABLED=false` in `.env`.

### Why an IDE run starts containers and the jar does not

`spring-boot-docker-compose` and `spring-ai-spring-boot-docker-compose` are `runtime` and `<optional>` in `pom.xml`. ADR-158 measured that the repackaged jar nests neither. An IDE does not run the jar, though. `Local SpringApp` runs `VesperaApplication` from `target/classes` on the module's runtime classpath, and that classpath includes optional dependencies. So the compose support is present there. Spring Boot skips it only when a test frame is on the stack, and a `main` launch has none. It then looks for `compose.yaml` in the working directory, which `Local SpringApp` sets to the project root. It knows nothing of `compose.gpu.yaml` or of any override, so it reconciles the running project down to `compose.yaml` alone. With the default `spring.docker.compose.lifecycle-management`, `start-and-stop`, it also stops the services when the application exits.

ADR-158 knew this support ran under the development entry points and kept it on purpose, for ADR-011's single command. That was written before ADR-165 and ADR-170 made a second compose file part of the operator's start command. Since then, *any* `up` that names `compose.yaml` alone replaces whatever the override changed.

### What the sidecar can say about itself

ADR-170 measured that the CPU image and the GPU image report identical `/version` maps. Their versions are the same; only the base differs. So the identity has to be told which image ran, and until now that was the configured value. Nothing compared the configured value with what was actually running.

The JVM has no Docker access, and nothing in Vespera should need it, because ADR-158's jar runs anywhere it is copied to. So the running image has to be readable over the one channel Vespera already has, docling-serve's HTTP API. The ways to make it readable were read from docling-serve v1.32.0 inside the running container on 2026-10-02, and the second point was read again on 2026-10-03:

- **`/version` returns a module-level dict, `docling_serve.helper_functions.DOCLING_VERSIONS`.** It is built once at import, from `importlib.metadata`, and `app.py` imports it and returns it as it stands. No setting adds a key to it.
- **The server is started by `docling-serve run`.** The base image's `ENTRYPOINT` is `container-entrypoint` and its `CMD` is `docling-serve run` (`docker image inspect`). The console script calls `docling_serve.__main__.main`. That runs `uvicorn.run("docling_serve.app:create_app", factory=True, reload=uvicorn_settings.reload, workers=uvicorn_settings.workers)`. Both settings come from docling-serve's uvicorn settings, `UVICORN_RELOAD` and `UVICORN_WORKERS`, and both are unset in the image. With both unset, uvicorn builds the app in the interpreter that launched it, in one process.
- **No other channel exists.** `/static` is mounted only for offline docs assets, there is no free-form metadata endpoint, and `/health` returns a fixed body.

**Measured on 2026-10-02.** A throwaway container ran `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0` with its command replaced by a five-line launcher. The launcher imports `DOCLING_VERSIONS`, sets `DOCLING_VERSIONS["vespera-image"]` from an environment variable, then calls `docling_serve.__main__.main()`. Run as `python launcher.py run --host 0.0.0.0 --port 5099`, its `/version` answered:

```json
{"docling-serve":"1.32.0","docling-jobkit":"3.5.0","docling":"2.124.0","docling-core":"2.93.0","docling-ibm-models":"4.0.1","docling-parse":"7.17.0","python":"cpython-312 (3.12.13)","plaform":"Linux-6.6.87.2-microsoft-standard-WSL2-x86_64-with-glibc2.34","vespera-image":"probe/tag:x"}
```

The container was removed afterwards, and the running `vespera` containers were not touched.

### Ollama's models

`ollama/ollama:0.33.2` declares no `VOLUME` (`docker image inspect`, `.Config.Volumes` is `null`). It keeps its models, and the key pair it generates, under `/root/.ollama` in the container's own layer. On 2026-10-02 the running `vespera-ollama-1` had no mounts. So every replacement of the container loses the models: an `up` after `compose.yaml` changed, an `up` with a different set of files, or `down`. The README already says so. The cost is a re-pull of each model, gigabytes for the generation model.

## Decision

### §1. No entry point starts a sidecar: both compose artifacts leave `pom.xml`

`spring-boot-docker-compose` and `spring-ai-spring-boot-docker-compose` are removed from `pom.xml`. With neither on any classpath, an IDE run of `VesperaApplication`, `./mvnw spring-boot:run` and `TestVesperaApplication` start no container and stop none, exactly as the jar does. The README's `up` line is the one way the sidecars are started, on every machine and from every entry point.

**Why remove them, rather than set `spring.docker.compose.enabled: false` in `application.yaml`.** Both were weighed, and the ticket recommended either:

- **A property can be turned back on.** One environment variable, `-D` flag or profile file does it, and the operator's own workaround shows how easily that is set. Removing the artifacts leaves nothing to turn on.
- **ADR-046: the pom carries what a recorded decision requires.** After this record no decision requires either artifact. Spring AI's one holds only connection-details factories, which read back containers Spring Boot's support started. Nothing starts them any more, so it would read nothing.
- **Nothing else uses them.** Nothing under `src/main` names either. Tests reach their containers through Testcontainers' `@ServiceConnection`, which lives in `spring-boot-testcontainers` and `spring-ai-spring-boot-testcontainers`, and neither of those depends on a compose artifact (`dependency:tree`, 2026-10-03). Three tests set `spring.docker.compose.enabled=false`: `CliExitIT` and `ProfileThatDoesNotLoadIT` on the jar they launch, and `VectorStoreIsReachedOnFirstUseTest` in its context. That becomes a property nothing reads, which is harmless, and they keep it. `PackagedJarIT` asserts that the jar nests neither artifact, and it still holds.

**Why not keep the support and point it at the README's files** (`spring.docker.compose.file` takes a list) with `lifecycle-management: start-only`. That is a second copy of the operator's start command, in a second syntax, and it would drift from the README the first time one of them changed. It still cannot know which optional files a machine uses: `compose.gpu.yaml` exists in every checkout, and naming it would make the IDE refuse to start on a machine without an NVIDIA GPU (ADR-165 measured that `up` fails there). It would also put Docker in front of `vespera label` and `vespera --version` from the IDE, the cost ADR-158 rejected for the jar.

**What this costs.** ADR-011's single command is gone from the development entry points too. A developer starts the sidecars once, as the operator does, and leaves them up. `TestVesperaApplication` still adds its throwaway Testcontainers containers, and now nothing else. ADR-158 had left unmeasured which of two container sets that entry point's connection details pointed at, and that question no longer arises.

### §2. The Docling image reports its own name in `/version`, as `vespera-image`

`docker/docling-serve/Containerfile` gains these, after its `FROM`. They go after the parser layer, at the end of the file, so that the LibreOffice and parser layers stay cached and shared between the CPU and GPU builds:

- **`ARG VESPERA_IMAGE`, with no default.** The name the image is built under, passed by whoever builds it. A `RUN test -n "${VESPERA_IMAGE}"` fails the build when nothing is passed, so an image can never answer with an empty name.
- **`ENV VESPERA_IMAGE=${VESPERA_IMAGE}`.** The name, kept in the image for the launcher to read at start.
- **A launcher, `docker/docling-serve/report_image.py`.** It is copied into the image and made its `CMD`, which runs it with `run` as its argument, in place of the base's `docling-serve run`. It does what the measured probe did: it sets `DOCLING_VERSIONS["vespera-image"]` from `VESPERA_IMAGE`, then calls `docling_serve.__main__.main()`. The base's `ENTRYPOINT` (`container-entrypoint`) is left alone, and the `Containerfile` declares none of its own, so whatever environment the s2i base sets up still applies. The launcher refuses to start when `VESPERA_IMAGE` is unset or blank. A container started from an image that somehow lacks it then fails its health check, rather than reporting nothing.

The key is `vespera-image`, prefixed so that no future docling component can collide with it. Its value is the full `repository:tag` the image was built under, and it is compared character for character with `vespera.docling.image` (§3).

**Each compose file passes its own image name as `VESPERA_IMAGE`.** In `compose.yaml` the `docling-serve` service's `build` gains `args: VESPERA_IMAGE:`, set to the same value as its `image:`. A YAML anchor on `image:` and an alias in `args` let the value be written once. `compose.gpu.yaml` does the same with the GPU tag. Compose merges `build.args` key by key, so the GPU file's `VESPERA_IMAGE` replaces the CPU one and `DOCLING_SERVE_BASE` stays its own. `TestcontainersConfiguration` passes `withBuildArg("VESPERA_IMAGE", DOCLING_SERVE_IMAGE)`.

**The launcher relies on docling-serve's internals, and that is the cost.** `DOCLING_VERSIONS` is not public API. It held at v1.32.0, the release ADR-163 pins. A base bump could rename or move it. Then the launcher fails at import, the container never answers `/health`, and `DoclingClientIT` fails on the bump that broke it. Nothing degrades silently. The launcher also assumes docling-serve's server runs in the interpreter that launched it, so `UVICORN_WORKERS` and `UVICORN_RELOAD` must stay unset. Either one makes uvicorn serve from a process of its own, which rebuilds the dict without the entry, and §3 would then stop every step.

### §3. A step that composes the extractor identity first checks the image, and stops on a mismatch

The extractor identity is composed in one place, the `@Lazy` bean method `ExtractionJobConfiguration.extractorIdentity`. Before composing it, that method compares `/version`'s `vespera-image` entry with `vespera.docling.image`:

- **Equal: the identity is composed exactly as before.** It still contains `image=<configured>`, the whole sorted `/version` map, which now includes `vespera-image=<the same name>`, and the client's sent options. The identity therefore names the image twice. That is the cost of not filtering the map: ADR-090's rule stands, and this entry is no exception to it.
- **Different: no identity is composed, and the exception says both.** `io.algernon.vespera.pipeline.DoclingRunsAnotherImageException`, a `RuntimeException`, is thrown (ADR-076: the name is the fault), with a message an operator can act on, naming the image running and the image configured:

  > docling-serve is running `<reported>`, and `vespera.docling.image` names `<configured>`; every conversion is recorded under the name configured, so none can be made until the two agree. If `<reported>` is the image you meant to run, set `VESPERA_DOCLING_IMAGE` to it; otherwise start the sidecars again with the compose files that build `<configured>`.

- **Absent: the same exception.** The message names the configured image and says the sidecar does not report which image it runs, which means it was built from a `Containerfile` older than this record or is not Vespera's image at all. It says to start the sidecars again with `up -d --build`.

**The check runs where the identity is first needed, which is before stage 2's run is minted and before any conversion is asked for.** `StageRuns.extraction` composes the identity to mint stage 2's run, and the step-scoped `extractionReader` calls it first thing, when the step opens its reader. So a mismatch fails stage 2 with no stage-2 run recorded and nothing converted, the same shape as a sidecar that refuses `/version` (#319, ADR-157). Stage 2's closing line (#311) names the failure through `StepFailure.named`. That line must carry both image names, whichever wrapper Spring's bean creation adds around the exception. `StepFailure.named` unwraps only Spring Batch's own wrappers. Spring's bean-creation exceptions repeat their cause's message in their own, so the names reach the line either way, behind Spring's prefix. Whether to unwrap further is the implementer's call. Every later step that takes the same identity, which is seed extraction, the embedding step, the relevance report and generation, stops the same way if the sidecar was swapped between invocations. This record does not change any of their closing lines.

**Stopping, not adopting the running image.** Taking the identity from `vespera-image` alone would make `vespera.docling.image` redundant. It would also have turned 2026-09-30 into a silent CPU conversion of the whole archive under a CPU identity, with the operator believing they were on the GPU. That cache would be correct. The run it served would not be the one asked for, and nothing would say so. A mismatch means the sidecars are not the ones the operator started, and only the operator knows which of the two is the mistake. The check costs one comparison against a call the identity already makes.

**What stays the operator's.** ADR-170 §3's step remains: on `compose.gpu.yaml`, `VESPERA_DOCLING_IMAGE` names the GPU tag. It is no longer load-bearing for correctness. Forgetting it now costs a stopped invocation and a line naming both images. Before, it cost a cache keyed under a lie.

### §4. Both Docling tags are bumped to `-r2`

The `Containerfile` change produces a different image, so it gets a different name. Conversions should not change: the LibreOffice layer, the parser pin and the base are unchanged, and `/version` gains one entry that docling does not read. The bump is required anyway, for three reasons:

- **This project has measured an image change altering output that its versions hid** (ADR-147, ADR-170). An unmeasured image does not share a name with a measured one.
- **Compose does not rebuild a tag it already holds unless told to with `--build`.** A machine still holding the old image under an unchanged name would run it, and §3 would then stop every step with "does not report".
- **The old GPU identity holds 1,398 conversions the CPU image made.** A new name keys none of them again. Nothing deletes them: they stay under an identity that no image will ever report again.

The tags become:

- `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2` in `compose.yaml`, `application.yaml`'s `vespera.docling.image` and `TestcontainersConfiguration`;
- `vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2` in `compose.gpu.yaml`, and in the README's `VESPERA_DOCLING_IMAGE` lines.

`-rN` counts revisions of Vespera's own layers on an unchanged base and parser, starting from 2. The tag before this record is revision 1. A change to the base release or to the parser pin changes the parts of the tag ADR-163 names. Any other change to `Containerfile` or to a file it copies raises N.

### §5. Ollama's models live in a named volume

`compose.yaml`'s `ollama` service mounts a named volume, `ollama-models`, at `/root/.ollama`, and the file declares that volume at top level with no options. That gives a Docker-managed volume, `vespera_ollama-models` under `-p vespera`. It is not a bind mount, so it depends on no host path, and it is not `external`, so `up` creates it. `compose.gpu.yaml` gives `ollama` nothing but its device request, as before, so the override neither adds a mount nor removes one.

- **`down` keeps the models now.** Only `down -v` removes the volume, and the models with it. The README says both.
- **Replacing the container keeps them.** That covers an `up` after `compose.yaml` changed, an `up` with or without `compose.gpu.yaml`, and an image bump of Ollama. The models stay, because the new container mounts the same volume.
- **The whole of `/root/.ollama`, not only `models/`.** Ollama's key pair lives beside the models. Mounting the parent keeps it from being regenerated on each replacement, and needs no `OLLAMA_MODELS` override.
- **Chroma stays without a volume.** It is a disposable projection of what SQLite holds (ADR-039).
- **ADR-164 is unaffected.** A restart keeps the same container and so the same mount. ADR-165's `deploy` merge is about devices, and the merge leaves volumes alone.

## Alternatives rejected

- **`spring.docker.compose.enabled: false` in `application.yaml`.** It can be turned back on, and it keeps two artifacts no decision requires (§1).
- **Keep the support and give it the README's files, with `start-only`.** That is a second copy of the start command, and it is wrong on every machine whose set of files differs (§1).
- **Read the running image through the Docker API from the JVM.** It needs Docker access, and the jar is meant to run without it (ADR-158). A remote or rootless daemon would also have to be configured twice.
- **A `LABEL` on the image.** Only Docker can read a label, which has the same problem.
- **Tell the CPU and GPU images apart by what `/version` already reports.** The two reports are identical (ADR-170). The torch build (`2.11.0+cu128`) differs, but no endpoint exposes it, and it would tell a CPU image from a GPU one and nothing else, such as a stale image.
- **A `sitecustomize.py` that adds the entry in every interpreter.** It would survive separate uvicorn workers. But it imports docling-serve into every Python process in the container, the README's `python -c "import torch…"` check included, to patch one dict. The launcher is scoped to the one process that serves `/version`.
- **A second HTTP port serving the image name.** That is one more pinned port and one more process, to say one string.
- **Adopt the reported image as the identity and drop `vespera.docling.image`.** A wrong sidecar would then go unnoticed (§3).
- **Leave the tags alone, since conversions should not change.** "Should not" is what ADR-147 measured to be false once already (§4).
- **A bind mount for Ollama's models.** It ties `compose.yaml` to a host path that differs on every machine. On Docker Desktop for Windows it would also be slower than a volume.

## Consequences

- **Every cached conversion is redone once, on both images.** Both tags change, so both identities change. On the GPU that is roughly 3 to 4 hours for the 10,469-file folder (ADR-170's measured rate). The change mints new stage-2 runs and everything downstream of them, with the knock-on effects ADR-170's consequences list: a fresh labelling sample, answers kept, and an arrangement to approve again.
- **The first `up` after this record replaces Ollama's container, and its models are lost one last time.** The `ollama` service changed, so the container is recreated. The new volume starts empty, and the README's `ollama pull` lines fill it. After that the models survive.
- **An IDE run now needs the sidecars already up.** `Local SpringApp` and `Local Vespera Label` start nothing. A run with nothing on port 5001 fails stage 2's health check with the line that names the converter, as the jar always has. The operator's `SPRING_DOCKER_COMPOSE_ENABLED=false` in `.env` becomes a line nothing reads, and can be deleted.
- **A swapped sidecar stops the invocation and records nothing.** That is the 2026-09-30 case: the sidecar running the CPU image while `VESPERA_DOCLING_IMAGE` names the GPU one. The closing line names both images.
- **The README changes in three places.** "Running it" says that no way of running Vespera, the IDE included, starts or stops a sidecar, and that a `.env` line setting `SPRING_DOCKER_COMPOSE_ENABLED` can go. The GPU section says an `up` without `compose.gpu.yaml` keeps Ollama's models, says a mismatch stops the invocation and names both images rather than saying nothing can catch it, and shows the `-r2` tag in its `VESPERA_DOCLING_IMAGE` lines. "Stopping" says `down` keeps the models and `down -v` removes them, and no longer says a changed `compose.yaml` costs the models.
- **`docs/architecture.md`'s technology table and its list of artifacts removed from the pom change.** The sidecars row says no entry point starts or stops one, and cites this record. The two compose artifacts join the removed list.
- **The comments that say the opposite of this record are corrected with the code.** `compose.yaml`'s header says the compose support resolves ports under `./mvnw spring-boot:run`. `compose.gpu.yaml`'s header, `application.yaml`'s `vespera.docling.image` comment and `ExtractionJobConfiguration.extractorIdentity`'s javadoc say the sidecar cannot report its image. `TestVesperaApplication`'s javadoc says the compose containers come up beside its own.
- **`docs/check-claims.mjs` must still read `compose.yaml`'s services correctly.** Its services pattern took every two-space key after `services:` to the end of the file. A top-level `volumes:` block would put `ollama-models` among the services, and the README check would then fail with "compose.yaml runs ollama-models and the section does not know it". The pattern stops at the next top-level key.
- **`DOCLING_VERSIONS` is now load-bearing.** A docling-serve bump re-checks that the launcher still finds it. `DoclingClientIT` fails if not (§2).

## Tests

New, under `src/test`, red until the change lands:

- **`SidecarsAreStartedByTheOperatorTest`** (unit, no Docker, reads the classpath and `pom.xml`):
  - `anIdeRunStartsNoContainer`: Spring Boot's compose lifecycle (`org.springframework.boot.docker.compose.lifecycle.DockerComposeLifecycleManager`) is not on the classpath the tests run on, which contains the runtime classpath an IDE launches `VesperaApplication` with, optional dependencies included. Spring AI's compose connection details (`org.springframework.ai.docker.compose.service.connection.ollama.OllamaDockerComposeConnectionDetailsFactory` and its Chroma sibling) are not on it either.
  - `thePomDeclaresNoComposeSupport`: `pom.xml` names neither `spring-boot-docker-compose` nor `spring-ai-spring-boot-docker-compose`.
- **`OllamaKeepsItsModelsTest`** (unit, no Docker, reads the compose files with SnakeYAML, in `SidecarRestartPolicyTest`'s style):
  - `ollamaKeepsItsModelsInANamedVolume`: `compose.yaml`'s `ollama` mounts `ollama-models:/root/.ollama`, and the top-level `volumes` declares `ollama-models` as neither `external` nor a bind.
  - `theGpuOverrideLeavesOllamasVolumeAlone`: `compose.gpu.yaml`'s `ollama` carries no `volumes`.
- **`DoclingImageCheckInvocationTest`** (invocation, `@CascadeSliceTest`, with a stub `DoclingClient` whose reported image each test sets, reset in `@BeforeEach`, and a fresh context per test because the identity is a lazily built singleton):
  - `aSidecarRunningAnotherImageStopsExtraction`: the sidecar reports the GPU tag while `vespera.docling.image` names the CPU one. The invocation exits non-zero, stage 2's closing line says it failed and names both images, no extraction is recorded over the corpus, and nothing is converted.
  - `aSidecarThatDoesNotReportItsImageStopsExtraction`: the same, with no `vespera-image` entry. The line names the configured image.
  - `aSidecarRunningTheConfiguredImageIsExtractedFrom`: the matching case completes stage 2. It passes today and guards against a check that refuses everything.
- **`ExtractorIdentityCompositionTest`** gains `composesNoIdentityForASidecarRunningAnotherImage` and `composesNoIdentityForASidecarThatDoesNotSayWhichImage`, at unit level against the identity method itself, and `composesTheIdentityForASidecarRunningTheConfiguredImage`, which pins the equal case: the identity names the image as configured and as reported.

Amended:

- **`ExtractorIdentityCompositionTest.keysTheSameVersionsFromTwoImagesApart`**: each stub sidecar reports the image it is composed for as `vespera-image`, or the check would refuse both.
- **`DoclingSidecarImageTest`**: the tag is `…-docling-parse-7.17.0-r2` (`tagsTheImageForItsBaseAndItsParser`, `tagsTheGpuImageApartWithTheSameSuffix`). It gains `theContainerfileBakesInTheNameItIsBuiltUnder`: `ARG VESPERA_IMAGE` with no default, after `FROM`, a `RUN` that fails on an empty one, and an `ENV` carrying it. It gains `theImageStartsThroughTheLauncherThatReportsItsName`: `report_image.py` sits beside the `Containerfile`, is copied in, and is the `CMD`, run with `run`, and the `Containerfile` declares no `ENTRYPOINT`. It also gains `eachComposeFilePassesTheImageItsOwnName`: `build.args.VESPERA_IMAGE` equals `image` in `compose.yaml`, and in `compose.gpu.yaml`.
- **`OllamaGpuOverrideTest`**: the GPU file's `docling-serve.build.args` carries exactly `DOCLING_SERVE_BASE` and `VESPERA_IMAGE`.
- **`DoclingClientIT`**: the Testcontainers sidecar's `/version` reports `vespera-image` equal to `TestcontainersConfiguration.DOCLING_SERVE_IMAGE`, which becomes public so the test can name it.
- **`RunIdentityGoldenTest.extraction`**: the identity literal names the `-r2` image twice, as `image=` and as `vespera-image=`.
- **The stub `DoclingClient`s** (`StubbedExtractionBeans`, `SeedScriptedExtractionBeans`, `PictureScriptedExtractionBeans`, `CountingDoclingBeans`, `ConverterStopsAnsweringBeans`, and ADR-181's `ConverterStopsPartwayBeans`) report `vespera-image` as the configured `vespera.docling.image`. A real sidecar built from this record's image reports the same, and the check does not stop them. `ExtractionWithTheSidecarDownTest`'s small HTTP sidecar is left as it is: each of its tests fails before the identity is composed.
