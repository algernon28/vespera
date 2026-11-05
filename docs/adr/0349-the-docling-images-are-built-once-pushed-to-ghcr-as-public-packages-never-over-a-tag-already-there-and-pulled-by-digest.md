# ADR-349 — The Docling images are built once, pushed to GHCR as public packages never over a tag already there, and pulled by digest

> **Superseded — see ADR-354.** [ADR-354](0354-the-docling-images-are-built-where-they-run-from-the-one-containerfile-and-no-workflow-pushes-them-to-a-registry.md) **supersedes this record** whole: the Docling images are built where they run, by the compose files and `TestcontainersConfiguration` from the `Containerfile`, under the `-r2` tags; `.github/workflows/docling-image.yml` goes, nothing is pushed to a registry, and part 2 is not built. The five records this one amended stand as written.

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Decided**: by the operator, who decided that both Docling images, the processor build and the CUDA build, are built once, pushed to the GitHub Container Registry as public packages under `algernon28`, and pulled by their `sha256` digest; that pinning a dated CentOS Stream 9 compose is not the way, because CentOS keeps those composes for about four weeks; and that the move costs one new tag and so one re-extraction of the corpus. The names, the tags, the workflow, its triggers and permissions, the disk, how the digest is pinned and what the starter becomes are decided under delegation.
- **In two parts.** A digest exists only once an image is pushed. Part 1 is §1 to §4: the names, the workflow and the tests that hold its shape. Part 2 is §5 to §7: the pin, made once both packages are pushed and public. Both parts are this decision; part 2 changes what §5 to §7 name and nothing else.
- **Amends**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md), from part 2: *"`compose.yaml` builds it. `TestcontainersConfiguration` builds it from the same file under the same tag"*, and the consequences *"it is built on the operator's machine rather than pulled"* and that a first build of the tests or a first `docker compose up` builds the image. The workflow builds it, once (§2); both pull it by digest (§5). The `Containerfile`, its layers and the image in the extractor identity stand.
- **Amends**: [ADR-163](0163-the-docling-sidecar-pins-docling-parse-7-17-0-and-its-image-is-tagged-for-the-pin.md) §2, from part 2: the image is named in four places, not three, the workflow's matrix beside `compose.yaml`, `vespera.docling.image` and `TestcontainersConfiguration`; `compose.yaml` names it with a digest after the tag, the other three without (§5). The tag still names the base and the parser.
- **Amends**: [ADR-170](0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md) §2 and its consequence *"The GPU build is not exercised in CI"*. From part 2, `compose.gpu.yaml`'s `docling-serve` carries `image` and `deploy` and no `build`, and `DOCLING_SERVE_BASE` is passed by the workflow alone (§2, §5). The workflow builds the GPU image on GitHub's runner each time it pushes one; nothing converts on it there. §1's one `Containerfile` with its base as a build argument stands.
- **Amends**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md) §2's *"Each compose file passes its own image name as `VESPERA_IMAGE`"*: the workflow passes each image its own reference (§2), and from part 2 no compose file builds. §4's revision rule is extended: every push on an unchanged base and parser raises N, and this move takes `-r3` (§1). §3's check stands, character for character (§6); from part 2, §3's two messages, *"the compose files that build `<configured>`"* and *"start the sidecars again with `up -d --build`"*, stop naming a build (§7).
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md), from part 2: its *Keeps* of ADR-147, *"the Docling image is built from `docker/docling-serve`, never pulled"*; §2's comparison, which reads each file's image up to its `@`; §4's `--build`, which goes, and the files the jar carries, which lose `docker/`; and §8's `NoComposeFileBuildsTheImageException`, which becomes `NoComposeFileRunsTheImageException` (§7). `--no-recreate`, the lock, the engine and every other line of §8 stand.
- **Keeps**: [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) and ADR-147's extractor identity, `image=<vespera.docling.image>`, the whole `/version` map and the sent options, with no digest in it (§6). [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md)'s restart policy. [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md): the repository still owns the sidecars, and now also the images it runs.
- **Rests on**: the registry's own manifests of both bases, read from `quay.io` on 2026-11-05; ADR-147's on-disk sizes; GitHub's documentation of its hosted runners, of the Container registry and of `workflow_dispatch`, read the same day; and what `ghcr.io` answered the same day for the processor package, never pushed: an anonymous `docker manifest inspect` is answered `denied`, which §2 step 3 fails on, and a manifest request with a token is answered HTTP 404 `MANIFEST_UNKNOWN`, which Docker prints as `manifest unknown` and step 3 accepts. So the first dispatch, logged in by step 2, passes the check.

## Context

### A build of the `Containerfile` is not one image

`compose.yaml` and `compose.gpu.yaml` build the two Docling images on whichever machine runs `up`, from `docker/docling-serve/Containerfile` (ADR-147, ADR-170), under `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2` and `vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2`. `TestcontainersConfiguration` builds the processor one the same way, and Vespera's own `up` carries `--build` (ADR-323 §4).

The `Containerfile`'s second layer installs LibreOffice with `dnf` from CentOS Stream 9's live repositories. What they serve under `libreoffice-core`, `libreoffice-writer` and `libreoffice-impress`, and under the 115 packages those pull in, changes as the stream moves. So two builds a month apart, under one tag, can hold different packages, and the tag says only what the `Containerfile` asked for. ADR-147 measured what that costs: installing LibreOffice changed the conversion of 5 of 14 PDFs that never reach LibreOffice, and `/version` reported the same versions for both images. Two builds of the same tag with different libraries would be two extractors under one name, which is the failure ADR-147 put the image into the identity to prevent, and which nothing Vespera reads can catch: the image reports the name it was built under (ADR-179 §2), and both builds were built under the same name.

The repositories also move under the build itself: the `Containerfile` reads them from CentOS's primary mirror because, on 2026-11-03, the metalink named a checksum no mirror served and every build stopped.

Pinning the build to a dated compose of Stream 9 would make the package set fixed, but CentOS keeps those composes for about four weeks, so a pinned build stops building within a month. The operator decided the image is built once and kept.

### The bases, measured

The registry's manifests of the two bases at `v1.32.0`, for `linux/amd64`:

| Base | Layers | Compressed | Largest layer, compressed | On disk |
| --- | --- | --- | --- | --- |
| `docling-serve-cpu` | 12 | 2.23 GB | 0.93 GB | 7.62 GB (ADR-147) |
| `docling-serve-cu128` | 12 | 6.01 GB | 4.71 GB | not measured |

ADR-147 measured the LibreOffice layer at about 1 GB on disk (7.62 GB to 8.57 GB). The processor base takes 3.4 times its compressed size on disk. The CUDA base is mostly shared libraries, which compress less well than Python sources, so its size on disk is estimated between twice and 3.4 times its compressed size, 12 to 21 GB; that is an estimate, not a measurement.

### Where it can be built and kept

- **A GitHub-hosted Linux runner** for a public repository is documented with 4 processors, 16 GB of memory and 14 GB of SSD. More is free on the runner's root disk in practice, and most of what fills it is preinstalled software a build of this image does not use.
- **The GitHub Container Registry** limits each layer to 10 GB and each upload to 10 minutes. The largest layer of either image is the CUDA base's 4.71 GB.
- **A package pushed there is private when first pushed.** It is linked to the repository whose workflow pushed it with that workflow's own token, and takes that repository's access permissions; its visibility is set in the package's settings, and a public package cannot be made private again.
- **Docker Hub's `vespera` namespace is not this project's** (ADR-323, probe A), so the names the compose files give today could be answered by a stranger's image on any `up` without `--build`.

## Decision

### §1. Two images, under the repository owner, tagged for base, parser and revision

| Image | Reference |
| --- | --- |
| processor | `ghcr.io/algernon28/vespera-docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r3` |
| CUDA 12.8 | `ghcr.io/algernon28/vespera-docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r3` |

- **One package per image, named flat under `algernon28`.** The `vespera-` prefix says whose image it is among the owner's packages; GHCR accepts a nested name too, and nothing here needs one.
- **The tag is ADR-163's and ADR-179's**: the base release, `-docling-parse-` and the parser pin, then `-rN`. **N rises with every push on an unchanged base and parser**, which extends ADR-179 §4's rule, *"any other change to `Containerfile` or to a file it copies raises N"*: since §2 never pushes over a tag, a push that keeps the tag is refused, so every new image is a new N.
- **This move is `-r3`.** The image the workflow builds is not the `-r2` any machine holds: its LibreOffice layer is whatever the repositories serve on the day of the push. An image not measured does not share a tag with one that was (ADR-179 §4).
- **No floating tag.** No `latest`, and no tag without the revision: a name that moves is what the digest exists to rule out.
- **`VESPERA_IMAGE` is the reference without a digest.** The digest is computed over the image's configuration, which holds `VESPERA_IMAGE`, so an image cannot carry its own digest. It reports `ghcr.io/algernon28/…:…-r3`, and that is what `vespera.docling.image` names (§6).

### §2. The workflow that builds and pushes them

`.github/workflows/docling-image.yml`, named `docling-image`.

**Triggers.** `workflow_dispatch`, and `push` to `main` with `paths: ["docker/docling-serve/**"]`, nothing else.

- A change to the `Containerfile` or to `report_image.py` merged to `main` pushes its images by itself. If the tag was not raised with it, the push is refused (below), `main` shows the workflow failed, and nothing is pushed.
- A change to the workflow alone does not trigger it. A new base release or a new revision written into the matrix is pushed by a dispatch on `main`. Part 2 needs a dispatch anyway: no digest is pinned before its image exists.
- **The job runs on `main` alone**: `if: github.ref == 'refs/heads/main'`, on the job, which covers both triggers, since a push runs on `main` already and a dispatch runs on whatever ref it names. A dispatch from another branch would build that branch's `Containerfile`, which has not reached `main`, and push it under the matrix's tag; and since no push replaces a tag (step 3), that image would stay under the tag for good, where the Consequences say a change to the `Containerfile` is built first on `main`. On any other ref the job is skipped, and nothing is built or pushed. This guards against a dispatch made from the wrong branch by mistake, not against a branch that edits the workflow: a dispatched run reads the workflow file of the ref it runs on, so a branch that drops the `if` runs without it. What stands in front of that is who may dispatch at all, which is whoever may write the repository.
- No `pull_request`. A pull request's run is not given a token that writes packages from a fork, and a build that is not pushed proves nothing the push would not: the push run builds before it pushes, and fails before pushing.

**Permissions**, at the workflow's level and nowhere else: `contents: read` and `packages: write`. No job declares its own. The only secret it names is `GITHUB_TOKEN`.

**Concurrency**: `group: docling-image`, `cancel-in-progress: false`. Runs queue rather than overlap, so two runs never check and push the same tag at once, and none is cancelled between its push and its report.

**One job, `image`, on `ubuntu-latest`, `if: github.ref == 'refs/heads/main'` (above), `timeout-minutes: 120`, over a matrix of two entries** (`strategy.fail-fast: false`, so one image failing leaves the other to finish, and the one that stopped can be run again alone):

| Key | processor | CUDA 12.8 |
| --- | --- | --- |
| `image` | `ghcr.io/algernon28/vespera-docling-serve-cpu-libreoffice` | `ghcr.io/algernon28/vespera-docling-serve-cu128-libreoffice` |
| `tag` | `v1.32.0-docling-parse-7.17.0-r3` | `v1.32.0-docling-parse-7.17.0-r3` |
| `base` | `quay.io/docling-project/docling-serve-cpu:v1.32.0` | `quay.io/docling-project/docling-serve-cu128:v1.32.0` |
| `free-disk` | `false` | `true` |
| `needs-gb` | `12` | `30` |

The job's `env` carries `REFERENCE: ${{ matrix.image }}:${{ matrix.tag }}`, `BASE: ${{ matrix.base }}` and `NEEDS_GB: ${{ matrix.needs-gb }}`. The processor entry passes its base explicitly although it is the `Containerfile`'s default, so the two entries are read alike.

**Its steps, by these names, in this order:**

1. **`Check out the repository`**: `actions/checkout@v4`, with `persist-credentials: false`, since nothing after it speaks to git.
2. **`Log in to ghcr.io`**: `docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin`, the token passed on standard input from `env: GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}`.
3. **`The tag is not in the registry yet`**: the registry is asked for `$REFERENCE`'s manifest. Only an answer that the tag, or the package, is unknown lets the job go on. The tag found fails the job, with a line naming it and saying that a changed image takes a new tag; any other answer, an error or no answer, fails it too, because a check that cannot tell is not a check that passed. This is what keeps a push from ever replacing a tag the registry holds. In shell: `docker manifest inspect "$REFERENCE"` (or `docker buildx imagetools inspect`) as the condition of an `if`, not negated, whose branch exits non-zero; then its output must match `manifest unknown` or `not found`, or the step exits non-zero. Nothing lets a failure pass: the step has no `|| true`, `set +e` or `exit 0`; no step and no job carries `continue-on-error`; and no step, job or workflow names a shell of its own, so every step runs in the runner's `bash -e`.
4. **`Free the runner's disk`**, only `if: matrix.free-disk`: prints the space free on Docker's root directory, as the runner comes, then removes the runner's preinstalled toolchains no build here uses, `/usr/local/lib/android`, `/usr/share/dotnet`, `/opt/ghc`, `/usr/local/.ghcup`, `/opt/hostedtoolcache/CodeQL` and `/usr/local/share/boost`, and the images the runner's Docker already holds, `docker image prune --all --force` (§3).
5. **`The disk has room for the build`**: prints the space free on Docker's root directory (`docker info --format '{{.DockerRootDir}}'`), and fails, naming what is free and `$NEEDS_GB`, when less than `$NEEDS_GB` GB is. A build that cannot fit stops here, before it pulls, rather than partway.
6. **`Build the image`**:

   ```
   docker build --pull \
     --build-arg DOCLING_SERVE_BASE="$BASE" \
     --build-arg VESPERA_IMAGE="$REFERENCE" \
     --label org.opencontainers.image.source=https://github.com/algernon28/vespera \
     --label org.opencontainers.image.revision="$GITHUB_SHA" \
     --label org.opencontainers.image.base.name="$BASE" \
     --label org.opencontainers.image.description="docling-serve with LibreOffice, built once for Vespera" \
     -t "$REFERENCE" \
     -f docker/docling-serve/Containerfile docker/docling-serve
   df -h "$(docker info --format '{{.DockerRootDir}}')"
   ```

   The last line prints the space free on Docker's root directory once the build is done (§3). The `Containerfile` and its two build arguments are ADR-170's and ADR-179's, unchanged. `source` links the package to the repository also when an image is pushed by hand (§3); `revision` names the commit it was built from; `base.name` the base it was built on; `description` is what the registry shows beside the package, the same for both images, and names no version, so a new base or parser does not leave it stale (the tag says those).
7. **`Push the image`**: `docker push "$REFERENCE"`, the only push in the file, of the only tag the job gives. No `docker tag` gives the image a second name.
8. **`The digest the registry holds`**: reads the digest back from the registry, `docker buildx imagetools inspect "$REFERENCE"`, and writes `$REFERENCE@sha256:<digest>` to `$GITHUB_STEP_SUMMARY`. That line, as the registry answers it and not as the local store has it, is what part 2 pins.

**No action but `actions/checkout`.** The job holds a token that writes this repository's packages, and every action is code from outside it run with that token in reach; the Docker CLI on the runner does everything `docker/login-action` and `docker/build-push-action` would, and `actions/checkout` is GitHub's own, run with no credential kept.

### §3. The disk

- **The processor image fits as the runner comes**: about 8.6 GB of image and 2.2 GB of compressed layers while it pulls, 10.8 GB, under the 14 GB documented. Its floor, `needs-gb: 12`, is that and a margin.
- **The CUDA image does not fit as documented**: 12 to 21 GB of base on disk (estimated, Context), 1 GB of LibreOffice, and 6 GB of compressed layers while it pulls, up to 28 GB. Its floor, `needs-gb: 30`, is the top of that estimate and a margin, and its entry frees the runner's disk first (§2, step 4).
- **If the hosted runner cannot make 30 GB free**, step 5 fails before anything is pulled, naming both numbers. The alternative is then the operator's: pushing the CUDA image from the machine that runs it, after the same check, with the same build line and `docker push`, logged in with a token that may write packages. That has two costs, recorded so neither is mistaken for none: the 4.71 GB layer must go up within GHCR's 10 minutes, which takes an upload of at least 63 Mbit/s, and `revision` then names whatever commit that checkout holds. It is not the way while the hosted runner can do it.
- **The floors are reasoned, not measured.** Three steps print the space free on Docker's root directory: step 4, for the CUDA image alone, before it frees anything; step 5, before the build, for both; and step 6, after the build. So the first push of each image is what measures them: what the runner comes with, what freeing gains, and what the build took. The prints are diagnostics, and no test holds them; the floors themselves are held (Tests).

### §4. The packages are public

The operator makes each package public in its settings, once, after its first push: a workflow's token can push a package, and its visibility is the owner's to set. Until both are public an anonymous pull is refused, so part 2 does not land before then: Testcontainers in CI pulls the processor image with no login, and an `up` on a machine that never logged in to `ghcr.io` pulls both. Public also means anyone can pull them; they hold docling-serve, LibreOffice, the parser and the launcher, and nothing of the operator's.

### §5. Part 2: the digest is pinned where the image is pulled

Once both are pushed and public, with the digests step 8 wrote:

- **`compose.yaml`**: `docling-serve.image` is `ghcr.io/algernon28/vespera-docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r3@sha256:<processor digest>`, and the service has no `build`, nor the anchor and `VESPERA_IMAGE` argument that served it. `init`, `cap_drop`, `security_opt`, `restart`, `environment` and `ports` stay. With a digest in the reference Docker pulls that digest and reads the tag as a label for the reader.
- **`compose.gpu.yaml`**: `docling-serve` carries `image`, the CUDA reference with its digest, and `deploy`, and nothing else. `DOCLING_SERVE_BASE` lives in the workflow alone. Compose replaces `image` from the override as before (ADR-170 §2).
- **`TestcontainersConfiguration`**: `DOCLING_SERVE_IMAGE` is the processor reference without its digest, the name the image reports, which `DoclingClientIT` compares with `/version`; a second constant, `DOCLING_SERVE_DIGEST`, holds `sha256:<processor digest>`; and the container is a `GenericContainer` of the reference with its digest, pulled, not built. `ImageFromDockerfile` and the path to the `Containerfile` go. Where Testcontainers does not take a tag and a digest together, it pulls `<name>@sha256:<digest>`, the same image.
- **`application.yaml`**: `vespera.docling.image` is the processor reference without its digest. The README's `VESPERA_DOCLING_IMAGE` lines name the CUDA reference without its digest.
- **The processor digest is written twice**, in `compose.yaml` and `TestcontainersConfiguration`, and a test holds the two equal.

### §6. The extractor identity reads the tag, and the digest is what the tag stands for

The identity is composed as before: `image=<vespera.docling.image>`, the whole `/version` map with its `vespera-image` entry, and the sent options. Both names are the reference without a digest, and ADR-179 §3 compares them character for character, unchanged.

**The digest is not in the identity**, because nothing can read it back. The image cannot hold its own digest (§1), and the JVM has no Docker access, which ADR-179 rejected for reasons that stand. A digest in `vespera.docling.image` would be a claim the check cannot make. **What makes the tag stand for one image** is §2 and §5 together: the registry holds one digest under the tag, because no push replaces a tag; and the compose files pull that digest and build nothing, so no machine makes an image of its own under the name.

**The move changes `vespera.docling.image`**, on both images. Stage 2's run is minted anew, and so is every run downstream of it, with what ADR-170's consequences list: a fresh labelling sample with the answers kept, and an arrangement to approve again. Seed extraction converts every seed again. Every cached conversion is made once more, on whichever image the machine runs. That is the cost the operator accepted.

### §7. Part 2: the starter pulls, and builds nothing

- **The `up` lines lose `--build`**: `docker compose -p vespera -f <folder>/compose.yaml up -d --no-recreate`, and with `-f <folder>/compose.gpu.yaml` after it for the CUDA image. ADR-323 kept `--build` so that a missing image was built and not asked of a registry namespace that is not this project's (probe A). The reference now names this project's namespace and a digest, which no other image can answer, so asking the registry is what is wanted. Compose pulls a missing image itself; `docker compose pull` stays among the commands never run. A first start pulls the base's 2.23 GB of compressed layers, or 6.01 GB for the CUDA image, and the layers Vespera adds; `up` keeps no bound of Vespera's.
- **`--no-recreate` stays**, for ADR-323's probes C and D.
- **§2's comparison** reads each file's `docling-serve.image` up to its `@` and compares that with `vespera.docling.image`, character for character.
- **`NoComposeFileBuildsTheImageException` becomes `NoComposeFileRunsTheImageException`**, since no file builds one, and its line reads: *"the sidecars are not answering and Vespera cannot start them for vespera.docling.image <configured>: compose.yaml runs <processor image> and compose.gpu.yaml runs <CUDA image>. Set VESPERA_DOCLING_IMAGE to one of the two, or unset it for the first, and run the same command again."*, each image named without its digest.
- **`DoclingRunsAnotherImageException`'s two lines stop naming a build.** `notReporting`'s, which today ends *"start the sidecars again with `up -d --build`"*, ends *"start the sidecars again with `docker compose -p vespera up -d`, naming `compose.gpu.yaml` after `compose.yaml` if you run the CUDA image, which pulls the image the compose files name and replaces the container running another"*: without `--no-recreate`, since the running container is the fault, and without `--build`, which has nothing to build. `running`'s *"the compose files that build `<configured>`"* reads *"the compose files that run `<configured>`"*. `ExtractorIdentityCompositionTest.composesNoIdentityForASidecarThatDoesNotSayWhichImage`'s claim *"says to start the sidecars again with a fresh build"*, which holds `--build` in the message, becomes a claim that it names `up -d` and neither `--build` nor `--no-recreate`; `composesNoIdentityForASidecarRunningAnotherImage` gains a claim that its message does not say `build`.
- **The README stops saying the converter is built.** Its paragraph *"The document converter's image is not pulled. It is built on your machine from `docker/docling-serve`…"* says instead that the image is pulled from `ghcr.io/algernon28` by its digest; that it adds LibreOffice to docling-serve's published image so `.doc` and `.ppt` files convert, and is built once, by the `docling-image` workflow, so every machine runs the same one; and that the first `up` pulls 2.23 GB of compressed layers, or 6.01 GB for the CUDA image, which Docker keeps. Its two hand-run `up` lines, for `compose.yaml` and for both files, are `up -d`, without `--build`; its update paragraph's *"`docker compose -p vespera up -d --build`"* is `docker compose -p vespera up -d`, which pulls a newly pinned digest and replaces the converter's container; and the starter's list of what stops it names *"a converter image that neither compose file runs"*, not *builds*.
- **`compose.gpu.yaml`'s header comment** names the line `docker compose -p vespera -f compose.yaml -f compose.gpu.yaml up -d`, without `--build`.
- **`application.yaml`'s comment on `vespera.sidecars`** names `docker compose -p vespera up -d --no-recreate`, and *"where vespera.docling.image is the image it runs"*, not *builds*.
- **The jar carries `compose.yaml` and `compose.gpu.yaml` under `sidecars/`, and nothing under `docker/`**: no file it carries names a build context.
- **The control panel's GPU preselection** (ADR-334 §11, `StartingValues`) reads `compose.gpu.yaml`'s image up to its `@`.
- **`docs/check-claims.mjs`** checks the README's `up` line without `--build`, and no build context.
- **The starter's classes are `pipeline`'s** and decide no verdict, key or text (ADR-222); the run ids that move are §6's, from the image.

## Alternatives rejected

- **Pin a dated compose of CentOS Stream 9 in the `Containerfile`.** CentOS keeps them about four weeks; the build stops building within a month.
- **Keep building on each machine and record the digest afterwards.** Each machine's build is its own image; the digest recorded would be one machine's, and the next machine's would differ under the same tag.
- **Docker Hub.** The `vespera` namespace is not this project's, and GHCR links the package to the repository and pushes with the workflow's own token.
- **Private packages.** Every machine, and CI, would need a token to pull a converter. The operator chose public.
- **Pull by tag alone.** A tag on GHCR can be pushed over by anyone who may write the package; a digest cannot change.
- **The digest in `vespera.docling.image` and the identity.** Nothing can read it back (§6), and ADR-179 §3's check would have to cut it off to compare.
- **`docker/build-push-action` and `docker/login-action`.** Code from outside the repository run beside a token that writes its packages, to do what three CLI lines do.
- **A `pull_request` trigger that builds without pushing.** It builds the CUDA image, on the disk of §3, on every pull request that touches the image, and the push on `main` fails before it pushes anything.
- **GitHub's larger runners.** They are paid, and the hosted runner with its disk freed is expected to do it (§3).
- **The operator's machine as the place it is pushed from.** §3's two costs; it stays the alternative if the hosted runner cannot.
- **A floating `latest` beside the tag.** A name that moves (§1).

## Consequences

- **Part 1 changes nothing under `src/main`, and no run id moves.** It adds the workflow, which pushes nothing before it is on `main` and run.
- **After part 1, one dispatch pushes both images**, and the operator makes the two packages public; then part 2 pins the digests.
- **After part 2, the one re-extraction** of §6, on either image.
- **A change to the `Containerfile` is built first on `main`.** After part 2 no test builds it, since the integration tests pull the digest; a change that does not build fails the workflow on `main`, nothing is pushed, and the compose files keep the digest they pin until a later push succeeds and a later change pins it.
- **The integration tests pull the converter** instead of building it, so a first build of them needs no LibreOffice install.
- **The `-r2` images stay on machines that built them** until removed; nothing names them after part 2.
- **Pulling needs `ghcr.io` to answer**, once per machine and image; a machine that has pulled the digest keeps it.

## What this does not decide

- **Ollama pinned by digest.** It stays `ollama/ollama:0.33.2`.
- **Signing the images, or attaching provenance or a bill of materials** to them.
- **An `arm64` image.** Both bases are read for `linux/amd64`, and the runner builds that alone.
- **Removing old package versions** from the registry.
- **The docling-serve v1.35.0 bump**, which stays ADR-147's to decide.

## Tests

Part 1, `DoclingImageWorkflowTest` (removed: ADR-354 supersedes this record, and the workflow goes) (unit, no Docker, reads `.github/workflows/docling-image.yml` with SnakeYAML and the `Containerfile` as `DoclingSidecarImageTest` reads it):

- `startsOnADispatchOrOnAChangeToTheImageOnMain`: the triggers are exactly `workflow_dispatch` and `push`; `push` names branch `main` alone and path `docker/docling-serve/**` alone; and the job's `if` is `github.ref == 'refs/heads/main'` and nothing else, so a dispatch from another branch runs nothing.
- `holdsNoPermissionButReadingTheRepositoryAndWritingItsPackages`: the workflow's permissions are exactly `contents: read` and `packages: write`, no job declares its own, the only secret named is `GITHUB_TOKEN`, and the checkout keeps no credential.
- `buildsTheTwoImagesSideBySideOnTheHostedRunner`: one job, on `ubuntu-latest`, with a timeout, `fail-fast` false, and a matrix of exactly two entries whose images are the two of §1, both under `ghcr.io/algernon28/`.
- `tagsEachImageForItsBaseItsParserAndItsRevision`: each tag is the `Containerfile`'s base tag, `-docling-parse-`, its pinned parser and `-r3`, and no `latest` is named in the file.
- `buildsEachImageFromTheOneContainerfileOnItsOwnBase`: the processor base is the `Containerfile`'s default, the CUDA base is `docling-serve-cu128` at the same release, `REFERENCE` and `BASE` are the matrix's, and the build line passes `DOCLING_SERVE_BASE` and `VESPERA_IMAGE` from them, tags `$REFERENCE` and builds `docker/docling-serve/Containerfile` in `docker/docling-serve`.
- `labelsEachImageWithTheRepositoryAndTheCommitItIsBuiltFrom`: the build line labels `source` with the repository's address, `revision` with the commit, `base.name` with the base and `description` with §2's text.
- `neverPushesOverATagTheRegistryHolds`: the steps are §2's eight, by name and in order; the registry check comes before the build and the push; it inspects `$REFERENCE` with `docker manifest inspect` or `docker buildx imagetools inspect`, exits non-zero when the inspect succeeds, and goes on only past a `grep -q` for `manifest unknown` that exits non-zero otherwise; it holds no `|| true`, `set +e` or `exit 0`; no job and no step carries `continue-on-error`, and no workflow, job or step names a shell; one step pushes, and it pushes `$REFERENCE`; no step gives the image another tag; the concurrency group is fixed and nothing is cancelled in progress.
- `freesTheDiskBeforeBuildingTheCudaImage`: the CUDA entry frees the disk and needs 30 GB, the processor entry frees nothing and needs 12; the freeing step runs on the matrix's `free-disk`, and the room check reads `$NEEDS_GB`, set from the matrix's `needs-gb`; that both come before the build is the order `neverPushesOverATagTheRegistryHolds` holds.
- `reportsTheDigestTheRegistryHoldsAfterThePush`: the last step reads `$REFERENCE` back from the registry after the push and writes it to the run's summary.

Part 2, owed by the change that pins:

- `DoclingSidecarImageTest`: `namesOneImageEverywhere` reads `compose.yaml`'s image up to its `@`, and that, `vespera.docling.image`, `TestcontainersConfiguration.DOCLING_SERVE_IMAGE` and the workflow's processor reference are one; `compose.gpu.yaml`'s, up to its `@`, is the workflow's CUDA reference; both compose images end in `@sha256:` and 64 hexadecimal digits, and `TestcontainersConfiguration.DOCLING_SERVE_DIGEST` is `compose.yaml`'s; `tagsTheImageForItsBaseAndItsParser` and `tagsTheGpuImageApartWithTheSameSuffix` read each tag up to its `@`; `buildsTheImageAndRunsItLockedDown` becomes a claim that the service pulls, has no `build`, and runs locked down as before; `eachComposeFilePassesTheImageItsOwnName` and `buildsTheGpuImageOnCu128AtTheSameRelease` move to `DoclingImageWorkflowTest` (removed: ADR-354), which held them; the revision is `-r3`.
- `OllamaGpuOverrideTest`: `compose.gpu.yaml`'s `docling-serve` carries exactly `image` and `deploy`.
- `SidecarStartTest`: every `up` carries `--no-recreate` and no `--build`; an image compared up to its `@`; the refusal for an image no file runs.
- `ComposeFilesTest` and `PackagedJarIT`: the jar carries the two compose files under `sidecars/` and nothing under `docker/`.
- `RunIdentityGoldenTest.extraction`: the identity names the `-r3` reference, as `image=` and as `vespera-image=`.
- `StartingValuesTest`: the GPU preselection is the CUDA reference without its digest.
- `ExtractorIdentityCompositionTest`: the two claims of §7 on `DoclingRunsAnotherImageException`'s lines.
- `check-claims.test.mjs`: the README's `up` line without `--build`.
- `DoclingClientIT`: unchanged claim, `/version`'s `vespera-image` is `DOCLING_SERVE_IMAGE`, now over the pulled image.
