# ADR-170 — Docling runs on the GPU under compose.gpu.yaml, with an image tag of its own

- **Date**: 2026-09-28
- **Status**: accepted
- **Amends**: [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md). Its §2, *"docling-serve stays on its CPU image"*, stops being true under `compose.gpu.yaml`: the GPU file now gives the GPU to Ollama **and** to docling-serve. Its §1, *"The file carries nothing but the request"*, stays true of `ollama` and not of `docling-serve`, which the file also gives an image name and a build argument (§2 here). `compose.yaml` on its own still asks for no device, for any service, and everything else in ADR-165 stands.
- **Amends**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md). The derived image has two builds from one `Containerfile`: the CPU build, on `docling-serve-cpu`, which is the default everywhere, and the GPU build, on `docling-serve-cu128` of the same docling-serve release, which only `compose.gpu.yaml` asks for. Each has its own tag.
- **Rests on**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) (stage 2 sends eight conversions at a time, which is how the sample below was sent), [ADR-163](0163-the-docling-sidecar-pins-docling-parse-7-17-0-and-its-image-is-tagged-for-the-pin.md) (the image pins docling-parse 7.17.0 and its tag names the pin), and the extractor-identity decisions, [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) and ADR-147 (the identity is the sidecar's `/version` map, the options the client sends, and the name of the image).
- **Settles** [#365](https://github.com/algernon28/vespera/issues/365).

## Context

Stage 2 is the slow stage on the whole archive: 10,469 conversions, estimated at 7–9 hours on the CPU image. Docling runs on the CPU only. `compose.yaml` pins `docling-serve-cpu` on purpose, and `compose.gpu.yaml` (ADR-165) gives the GPU to Ollama alone. The machine measured has an **NVIDIA GeForce RTX 5060 Ti, 16 GB**, which stage 2 never uses.

Everything below was measured on 2026-09-28 on that machine.

**The test image** was `docker/docling-serve/Containerfile` with only its `FROM` changed to `quay.io/docling-project/docling-serve-cu128:v1.32.0`, keeping the same LibreOffice layer and the same docling-parse 7.17.0 pin. In the container, `torch.cuda.is_available()` is `True`, the device is `NVIDIA GeForce RTX 5060 Ti`, and CUDA is 12.8.

- The RTX 50 series (Blackwell) needs CUDA ≥ 12.8. `docling-serve-cu126` has no v1.32.0 tag; `cu128` and `cu130` both do.

### Speed

The request is shaped as Vespera sends it (`to_formats=json`, `ocr_preset=rapidocr`, `image_export_mode=embedded`, `filename=document.pdf`), with `DOCLING_SERVE_MAX_SYNC_WAIT=600`. The sample is 16 random archive PDFs of 150–900 KB, sent 8 at a time (ADR-140):

| Sidecar | Wall time, 16 PDFs | Slowest single call | `GoFDesignPatternsInJava.pdf` (161 pages) alone |
|---|---|---|---|
| CPU image, 1 container (2 workers) | 230–285 s | 190–239 s | 122 s |
| CPU image, 1 container, 4 workers | 228 s | 222 s | — |
| CPU image, 2 containers, round-robin | 222 s | 190 s | — |
| CPU image, 3 containers, round-robin | 240 s | 214 s | — |
| **cu128 image, 1 container** | **110 s** | **74 s** | **90 s** |

- **More CPU workers or more CPU containers do not help**: the wall time tracks the slowest single file. The GPU makes each file faster: 2–2.5× on the sample, 3× on the slowest call.
- GPU utilisation was about 13% during the run, so the remaining time is CPU-side parsing.
- VRAM used was 4.6 GB, with Ollama's models also loaded, out of 16 GB.

### Output

6 of the 16 PDFs were converted on both images, and their `json_content.texts` compared:

| PDF | texts CPU / GPU | tables CPU / GPU | text similarity | identical |
|---|---|---|---|---|
| ReleaseHistory_SmartPOS_v1_22.pdf | 783 / 783 | 76 / 76 | 1.0000 | yes |
| Distribuzione_MIP_VerifoneVX_0527.pdf | 91 / 91 | 12 / 12 | 1.0000 | yes |
| OKReleaseHistory_EdenredFast_v14.0.pdf | 274 / 275 | 37 / 37 | 0.9997 | no |
| Distribuzione_MIP_PAXAndroid_0527_CONTAI… | 69 / 69 | 16 / 16 | 0.9997 | no |
| Guida all'uso MIP Tester Android_rev6.pdf | 67 / 67 | 1 / 1 | 1.0000 | yes |
| Guida Integrazione Libreria Android Cont… | 33 / 33 | 1 / 1 | 1.0000 | yes |

The output is near-identical but **not** identical. Under ADR-147 and ADR-163, a different image is a different extractor.

### The sidecar cannot tell anyone which image it is

`GET /version` returns the same JSON from both images (docling-serve 1.32.0, docling 2.124.0, docling-parse 7.17.0, …). The extractor identity (`ExtractionJobConfiguration.extractorIdentity`, built from `vespera.docling.image` plus `/version`) therefore depends **entirely** on `vespera.docling.image` naming the image that actually runs. This is ADR-147's finding again, for a second pair of images: two images reporting the same versions convert the same PDF differently, and the name of the image is the only thing in the key that can tell them apart.

## Decision

### §1. One Containerfile, with its base as a build argument

`docker/docling-serve/Containerfile` takes its base as an argument, defaulting to today's:

```dockerfile
ARG DOCLING_SERVE_BASE=quay.io/docling-project/docling-serve-cpu:v1.32.0
FROM ${DOCLING_SERVE_BASE}
```

Everything after `FROM` stays as it is: the same LibreOffice layer and the same docling-parse 7.17.0 pin, so the two builds differ in their base and in nothing Vespera added. The CPU base is the default everywhere. A build that passes no argument, which is every build from `compose.yaml` alone, builds exactly the image it built before.

### §2. `compose.gpu.yaml` gives the GPU to docling-serve too, under a tag of its own

`compose.gpu.yaml` gains a `docling-serve` service beside `ollama`:

```yaml
  docling-serve:
    image: 'vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0'
    build:
      args:
        DOCLING_SERVE_BASE: 'quay.io/docling-project/docling-serve-cu128:v1.32.0'
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
```

- **The base is `docling-serve-cu128`**, because the RTX 50 series needs CUDA ≥ 12.8 and `cu126` has no v1.32.0 tag. It is the **same docling-serve release** as the CPU default, so the two builds differ by the device and nothing else anyone chose.
- **The tag is its own.** Its conversions differ from the CPU image's in a few text blocks (Output), so it is a different extractor, and its cache entries must not mix with the CPU image's. The tag keeps the CPU tag's suffix, `:v1.32.0-docling-parse-7.17.0`, so that the tag still names the base release and the parser pin (ADR-163), and a parser bump changes both tags or is caught.
- **The device request is ADR-165's**, the same one `ollama` carries.
- **Compose merges `build.args` and replaces `image` from the override.** So `context`, `dockerfile`, `init`, `cap_drop`, `security_opt`, `restart` and `ports` all still come from `compose.yaml`, and the GPU sidecar runs as locked down as the CPU one, on the same port, under the same restart policy.

The file's header comment says it now gives the GPU to Ollama and docling-serve, and that an operator on this override must also set `VESPERA_DOCLING_IMAGE` (§3).

### §3. The operator names the GPU image, because nothing else can

On a machine running `compose.gpu.yaml`, `vespera.docling.image` must name the GPU tag:

```
VESPERA_DOCLING_IMAGE=vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0
```

`@Value("${vespera.docling.image}")` resolves it through Spring's environment-variable relaxed binding. The documented place for the line is the untracked `.env` at the repository root, which the run configurations in `.run/` load. `java -jar` does not read `.env`, so an operator who starts the jar from a shell sets the same variable in that shell.

**This step is load-bearing.** `/version` cannot tell the images apart, so nothing Vespera reads from the sidecar can catch a mistake. An operator who runs the GPU sidecar and forgets it caches GPU output under the CPU image's name: every conversion from then on is recorded as the CPU image's, and later mixes with conversions the CPU image did make, which is the failure ADR-147 put the image in the key to prevent. The README says so plainly, next to where it documents `compose.gpu.yaml`.

### §4. The defaults do not change

`compose.yaml`, `application.yaml`'s `vespera.docling.image` and `TestcontainersConfiguration` go on naming the CPU image, `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`. The CPU image stays the default everywhere, a machine without an NVIDIA GPU sees nothing change, and CI, which has no GPU, tests against the CPU image as before.

## Alternatives rejected

- **More CPU workers, or more CPU containers behind a round-robin.** Measured, and no faster (Speed): 4 workers took 228 s, 2 containers 222 s, 3 containers 240 s, against 230–285 s for one container with 2 workers. The wall time tracks the slowest single file, and only the GPU made each file faster.
- **Keep the CPU image's tag for the GPU build.** The output is not identical (Output), and the sidecar cannot say which image it is, so one name for both would key two extractors' conversions as one.
- **Let the extractor identity find out the device by itself**, from the sidecar. `/version` returns the same JSON from both images, and nothing else the sidecar serves was read into the identity. The image name is the part of the key that already exists for exactly this (ADR-147).
- **`docling-serve-cu130`.** It also has a v1.32.0 tag, and also runs on the RTX 50 series. `cu128` is the lowest CUDA the card needs, and it is the one measured.
- **The GPU image as the default.** CI has no GPU, and ADR-165's reason holds: the default has to start on every machine.

## Consequences

- **Switching costs one full re-conversion.** The ~4,600 conversions cached under the CPU identity are not reused under the GPU identity, so all 10,469 are redone, once. At the measured speed that is still roughly 3–4 hours, against 7–9 hours to finish on the CPU. Switching back to the CPU image finds its own cache entries again, since nothing deletes the CPU identity's rows.
- **Only docling-serve's container is replaced when the new `compose.gpu.yaml` is first used.** Ollama's part of the override does not change, so its container, and the models inside it, stay (ADR-165's C).
- **The GPU build is not exercised in CI.** `DoclingSidecarImageTest` pins what can be read from the files: the `ARG` and its CPU default, that the GPU tag differs from the CPU tag and ends with the same suffix, and that the GPU build argument names `docling-serve-cu128` at the CPU default's docling-serve release. That the GPU build converts on the card is the measurement above and the operator's check with `torch.cuda.is_available()`, not a test.
- **`compose.yaml` changes no key.** Its `docling-serve` comment, which says that Ollama is the one service that can be given a GPU, stops being true and is corrected to name docling-serve too; that is a comment-only change.
- **Nothing under `src/main` changes.**

## Tests

`OllamaGpuOverrideTest` (unit, no Docker, reads the compose files with SnakeYAML), as amended here:

- **`composeYamlAloneAsksForNoDevice`**: unchanged.
- **`theOverrideGivesOllamaAndDoclingEveryNvidiaGpu`**: `compose.gpu.yaml` exists; its services are exactly `ollama` and `docling-serve`, both of which `compose.yaml` runs; `ollama` carries `deploy` and nothing else; `docling-serve` carries exactly `image`, `build` and `deploy`; and each one's `deploy.resources.reservations.devices` is one request, for driver `nvidia`, count `all`, capability `gpu`.

`DoclingSidecarImageTest`, new claims:

- **`buildsFromABaseArgumentThatDefaultsToTheCpuBase`**: the `Containerfile`'s `FROM` is `${DOCLING_SERVE_BASE}`, and the `ARG` default is `quay.io/docling-project/docling-serve-cpu:v1.32.0`.
- **`tagsTheGpuImageApartWithTheSameSuffix`**: the GPU tag in `compose.gpu.yaml` differs from the CPU tag, and ends with the same `:v1.32.0-docling-parse-7.17.0` suffix, so a parser bump cannot update one and forget the other.
- **`buildsTheGpuImageOnCu128AtTheSameRelease`**: the GPU build argument names `docling-serve-cu128` at the same docling-serve release as the CPU `ARG` default.
- **`namesOneImageEverywhere`**: unchanged; compose.yaml, application.yaml and Testcontainers all name the CPU tag.
