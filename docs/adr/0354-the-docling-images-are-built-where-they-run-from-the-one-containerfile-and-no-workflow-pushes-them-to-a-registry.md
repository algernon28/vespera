# ADR-354 — The Docling images are built where they run, from the one Containerfile, and no workflow pushes them to a registry

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Decided**: by the operator, who decided that the two Docling images are not made public packages for now; that they go on being built where they run, from `compose.yaml`, `compose.gpu.yaml` and the `Containerfile`; that the workflow which pushed them goes; and that the drift a fresh build brings is accepted. That the tag stays `-r2`, and the tests that hold this, are decided under delegation.
- **Supersedes**: [ADR-349](0349-the-docling-images-are-built-once-pushed-to-ghcr-as-public-packages-never-over-a-tag-already-there-and-pulled-by-digest.md), whole. Its part 1, `.github/workflows/docling-image.yml`, goes (§2). Its part 2, the digests pinned in the compose files and in `TestcontainersConfiguration`, the pulls, the starter's `up` without `--build` and `NoComposeFileRunsTheImageException`, is not built (§1). Its `-r3` names no image (§3). It carries a note at its head saying so.
- **Keeps**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md) as written: *"`compose.yaml` builds it. `TestcontainersConfiguration` builds it from the same file under the same tag"*, the image built on the operator's machine rather than pulled, and the image's name in the extractor identity. [ADR-163](0163-the-docling-sidecar-pins-docling-parse-7-17-0-and-its-image-is-tagged-for-the-pin.md) §2's three places that name the image. [ADR-170](0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md) §1 and §2, and its consequence *"The GPU build is not exercised in CI"*. [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md) §2 to §4, §4's revision rule not extended. [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md)'s *Keeps* of ADR-147, §2's comparison, §4's `--build` and the files the jar carries, and §8's `NoComposeFileBuildsTheImageException`. Each of these five carries a note at its head pointing here, in place of the one ADR-349 gave it.
- **Rests on**: ADR-349's Context, whose account of why a build of the `Containerfile` is not one image, and of why a dated CentOS Stream 9 compose is no pin, stands; what GitHub showed of the workflow's one dispatch; and what `ghcr.io` answered anonymous requests for both packages after it, all on 2026-11-05.

## Context

### What ADR-349 needed to be worth having

ADR-349 moved the build of both images to one place, a workflow on `main`, so that a tag would stand for one image: built once, pushed to `ghcr.io/algernon28` under a tag never pushed over, and pulled by digest everywhere. Its part 2, the pull, rested on both packages being made public (ADR-349 §4), because a package GHCR receives is private when first pushed, and a private one asks every machine and CI's Testcontainers for a login before it gives up a converter. The operator chose not to make the packages public for now. An image pushed and pulled by nobody makes no tag stand for one image, and the workflow would hold a token that writes the repository's packages to no end.

### What the workflow did

It was dispatched once, on 2026-11-05, as GitHub Actions run 42110418495, and the run was cancelled. The processor entry built its image, and its `Push the image` step failed. The CUDA entry was cancelled while `Build the image` ran, and its push was skipped.

What `ghcr.io` answered anonymous requests the same day, after the run:

- **`algernon28/vespera-docling-serve-cpu-libreoffice`**: it hands out a pull token with no login; `/v2/algernon28/vespera-docling-serve-cpu-libreoffice/tags/list` returns `{"name":"algernon28/vespera-docling-serve-cpu-libreoffice","tags":null}`; and the manifest of `v1.32.0-docling-parse-7.17.0-r3` returns HTTP 404 `MANIFEST_UNKNOWN`.
- **`algernon28/vespera-docling-serve-cu128-libreoffice`**: it hands out no token and answers `DENIED`, which is what ADR-349's Rests on records for a package never pushed, and what it answers for a name no package holds.

ADR-349's Rests on records `denied` for the processor package too, before the dispatch. So the processor entry's failed push created the processor package; anyone can read it without logging in, so it reads as public; and it holds no tag. The CUDA package does not exist: its entry never reached its push.

**Not checked**: the processor package's visibility setting, and whether it holds untagged manifests or blobs, which a tag list does not show. Both need a token with the `read:packages` scope, which the token at hand lacks.

### The drift a build brings

The `Containerfile`'s second layer installs LibreOffice with `dnf` from CentOS Stream 9's live repositories, read from `mirror.stream.centos.org` since 2026-11-03, when the metalink named a checksum no server held and every build stopped. Two consequences follow, and both stand under this record:

- **An unchanged tag can convert differently after a rebuild.** A build installs whatever those repositories serve that day under `libreoffice-core`, `libreoffice-writer` and `libreoffice-impress` and the packages they pull in. Two builds of the one `Containerfile` on different days can hold different packages, convert some files differently, and report the same `/version` and the same name, so the extractor identity, which names the image by its tag (ADR-147, ADR-179 §3), records both as one extractor. ADR-147 measured that a change of this layer can move what is converted: installing LibreOffice changed the conversion of 5 of 14 PDFs that never reach it.
- **An outage of CentOS's repositories can stop a build.** A machine with no image and no cached layer for that step cannot build one while the repositories do not answer, or answer as on 2026-11-03.

What bounds both is that a machine builds its image once and keeps it: Docker reuses what it built, layer by layer, while the `Containerfile` and the files it copies are unchanged (the README says so to the operator), so `up -d --build` on a machine that holds the image installs nothing again. The drift comes back with a build on a machine that holds no image, or after the image or its build cache is removed.

## Decision

### §1. The images are built where they run

`compose.yaml` builds the processor image and `compose.gpu.yaml` the CUDA 12.8 one, from `docker/docling-serve/Containerfile`, with each file passing its own image name as `VESPERA_IMAGE` (ADR-179 §2); `TestcontainersConfiguration` builds the processor image from the same file under the same tag (ADR-147); Vespera's own `up` carries `--build` (ADR-323 §4). The images are:

| Image | Name |
| --- | --- |
| processor | `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2` |
| CUDA 12.8 | `vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2` |

Nothing under `src/main`, neither compose file, the `Containerfile`, `application.yaml` nor the README changes: they say what this record says already.

### §2. No workflow pushes them to a registry

`.github/workflows/docling-image.yml` is removed. No workflow of the repository names `ghcr.io`, sends an image to any registry, or is given `packages: write`. CI builds the processor image where its integration tests start it, as before, and builds no CUDA image.

### §3. The tag stays `-r2`

`-r3` was ADR-349's name for an image built on GitHub's runner on the day of its push. No such image is run, and no file names it. The image each machine builds is the `-r2` the `Containerfile` describes, and ADR-179 §4's revision rule stands as written: a change to the `Containerfile` or to a file it copies raises N, and nothing else does.

### §4. The drift is accepted

The two consequences of the Context are accepted, not cured. A rebuild of an unchanged tag can convert differently, and the extractor identity cannot tell the two builds apart; an outage of CentOS's repositories can stop a build on a machine that holds no image. Nothing in Vespera detects either.

### §5. A later record may push them again

Pushing the images to a registry may come back by a later record, as public packages or as private ones pulled with a login. ADR-349's Context, the bases' sizes, the runner's disk and GHCR's limits, and its rule that no push replaces a tag, are what such a record would start from.

## Alternatives rejected

- **Keep the workflow and push private packages, pulled with a login.** Every machine, and CI, would need a token that reads the owner's packages to get a converter. The operator chose not to now.
- **Make the packages public now.** The operator chose not to for now; a public package cannot be made private again.
- **Keep the workflow and pull nothing it pushes.** An image nobody runs makes no tag stand for one image, and the workflow holds a token that writes packages.
- **Pin a dated compose of CentOS Stream 9 in the `Containerfile`.** ADR-349's reason stands: CentOS keeps them about four weeks.
- **Raise both tags to `-r3` anyway.** Nothing that goes into the image changes here, so ADR-179 §4 gives no reason to.

## Consequences

- **Nothing under `src/main` changes, and no run id moves**: `vespera.docling.image`, and so the extractor identity, still name the `-r2` processor image.
- **The drift ADR-349 set out to end stays**, as §4 states it.
- **A first build of the integration tests, and a first `docker compose up`, build the image**, as ADR-147 says.
- **ADR-349's one dispatch left an empty processor package in the registry.** `ghcr.io/algernon28/vespera-docling-serve-cpu-libreoffice` is there with no tag, readable without a login, and no file names it (Context).

## What this does not decide

- **Whether the `Containerfile`'s move to `mirror.stream.centos.org` raises N** under ADR-179 §4. The move changed the `Containerfile`, which the rule counts, and changed where the packages are read from and not which are asked for; the tag was left at `-r2`. This record keeps `-r2` for the images as they are, and decides nothing about that move.
- **The docling-serve v1.35.0 bump**, which stays ADR-147's to decide.
- **Whether the empty processor package is deleted.** The operator decides it, and a deletion needs a token with the `delete:packages` scope. It bears on the operator's choice that the packages are not public for now: the package already reads as public, and a public package cannot be made private again (Alternatives rejected), so if it is public, deleting it, not a change to its settings, is what ends that.

## Tests

- `DoclingImageWorkflowTest` (removed: its subject, the workflow, goes, §2).
- `DoclingSidecarImageTest.noWorkflowPushesTheImagesToARegistry`: the repository's workflows folder holds at least one workflow, `docling-image.yml` is not among them, and none names `ghcr.io`, pushes an image with `docker push`, `--push` or a push action, or is given `packages: write`.
- `DoclingSidecarImageTest`'s other claims hold §1 and §3 as they did: `buildsTheImageAndRunsItLockedDown`, `namesOneImageEverywhere`, `tagsTheImageForItsBaseAndItsParser`, `tagsTheGpuImageApartWithTheSameSuffix`, `eachComposeFilePassesTheImageItsOwnName` and `buildsTheGpuImageOnCu128AtTheSameRelease`. The members ADR-349 opened to the removed test are private again.
