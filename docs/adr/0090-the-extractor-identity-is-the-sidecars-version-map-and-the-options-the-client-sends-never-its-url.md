# ADR-090 — The extractor identity is the sidecar's version map and the options the client sends, never its URL

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none — discharges [ADR-012](0012-extraction-engine-is-configurable.md)'s "the cache key must carry full extractor identity", which `ExtractorIdentity`'s own javadoc has been deferring since it was written

## Context

`ExtractionJobConfiguration` composes the extractor identity from one thing:

```java
return new ExtractorIdentity("docling-serve;base-url=" + baseUrl);
```

ADR-012 says the serving runtime is configuration rather than code, and that **"the cache key must carry full extractor identity; calibration must not cross engines."** `ExtractorIdentity`'s javadoc names what it expects — "engine name, model/pipeline options, the sidecar's own reported version" — and defers composing it. The base URL was the only engine-selection knob the slice had, and the reasoning was correct when it was written.

Raised as [#89](https://github.com/algernon28/vespera/issues/89) while settling [#80](https://github.com/algernon28/vespera/issues/80): deciding that an embedding vector carries model name, weights digest, dimension and instruction ([ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md)) put the same question to the layer below, and the layer below answers differently.

### What the sidecar actually exposes, established rather than assumed

The ticket named two facts nobody had established. The pinned image (`quay.io/docling-project/docling-serve-cpu:v1.32.0`, the version `compose.yaml` and `TestcontainersConfiguration` both name) was run and probed directly.

**`/version` exists, and returns a component map rather than one string:**

```json
{"docling-serve":"1.32.0","docling-jobkit":"3.5.0","docling":"2.124.0",
 "docling-core":"2.93.0","docling-ibm-models":"4.0.1","docling-parse":"7.16.0",
 "python":"cpython-312 (3.12.13)","plaform":"Linux-…-WSL2-x86_64-with-glibc2.34"}
```

`/health` and `/ready` return only `{"status":"ok"}` and pin nothing.

**Pipeline selection is client-side and per-request.** `POST /v1/convert/file` accepts **52 form fields**, among them `pipeline` (`legacy|standard|vlm|asr`, default `standard`), `ocr_engine` (default `auto`), `vlm_pipeline_model` (16 values, including `granite_docling`, `nanonets_ocr2` and `deepseekocr_ollama`), `table_mode`, `pdf_backend`, `force_ocr` and `do_ocr`. `DoclingClient` sends exactly two: `files` and `to_formats=json`.

**This inverts the ticket's premise.** "Point the same docling-serve at a different OCR backend" is not principally a server-side configuration change the client cannot observe — **it is a request the client would send**. The gap is in what we transmit, not only in what the server reports.

**And one thing genuinely is unobservable.** `ocr_engine` defaults to `auto`, and the container resolves it at startup — the probe's own log reads `Auto OCR model selected rapidocr with onnxruntime`. That choice depends on which models are cached and what hardware is present, it is pinned by no version number, and it appears in no response.

## Decision

### The base URL comes out

**The identity carries what determines the output. A URL determines nothing about it.**

ADR-084 drew this line one layer up: which model is identity, where it is served is `application.yaml`. Two docling-serve instances at different URLs, on the same versions with the same options, produce the same text.

Keeping the URL in the key has it exactly backwards: **moving the sidecar's port invalidates the entire extraction cache for no reason, while a genuine backend change still slips through unnoticed.** One false positive and one false negative from a single field.

### What goes in: the whole version map, and the options the client sends

**The whole `/version` map, not just `docling-serve`.** `docling` 2.124.0 and `docling-ibm-models` 4.0.1 are what actually convert; the serve wrapper's version can move without them, and they can move without it. Recording one number would name the layer least likely to change the output.

**Every option the client sends**, which today is `to_formats=json` — itself output-determining ([ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) chose the JSON export over Docling's default Markdown precisely because it changes what comes back) and currently in no key at all.

**Not all 52 fields.** An option the client does not send is the server's default, and that default is declared by the schema of a version the map pins — so *version map + sent options* covers the rest by construction. Enumerating fifty-two defaults would add a maintenance burden that says nothing the map does not already say, and would go stale against the next image.

### `ocr_engine` is pinned by the client, not recorded as unpinned

**Stop sending nothing and letting `auto` resolve server-side. Send an explicit `ocr_engine`.**

This departs from ADR-084 deliberately, and #89 asked that it be argued rather than copied. ADR-084 recorded the absence of a pin because the embedder genuinely could not be pinned from the client — the identity's honest content was "this runtime pins nothing". **Here the client can pin it.** Recording "unpinned" would be accurate and useless: it would faithfully describe a value that changes what every downstream stage sees, while doing nothing to stop it changing.

Extraction is upstream of everything. An `ocr_engine` that silently resolves differently on another machine re-cuts every chunk, re-shingles every document, moves every confidence score that tier 2's floor is calibrated against, and re-scores every relevance comparison — with no error and no new row set. It is the one knob whose drift is both invisible and total.

**It is not a gate.** Nothing here is a value the pipeline requires and does not have; it is a value the pipeline was leaving to a coin-flip and will now state.

### Retuning the identity strands every cached row, and that is correct

Every `extraction_cache` row today is keyed on `docling-serve;base-url=…`. A richer identity means **new rows; the old ones become unreachable rather than wrong.**

This is [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md)'s posture exactly — a value dependent on a parameter lives under that parameter's identity, so changing the parameter mints new rows instead of silently invalidating old ones. The cost is one re-extraction of any corpus already processed.

**A migration was the alternative and is worse.** Rewriting existing rows under the new identity would have to *assert* that they came from the standard pipeline with auto-resolved OCR on the machine that produced them — which is precisely the fact nobody recorded, and the reason this ADR exists.

## Consequences

**ADR-012's second clause becomes true rather than aspirational.** "Calibration must not cross engines" was unenforceable while the identity moved only with the URL: tier 2's floor (ADR-070, [ADR-078](0078-tier-2-is-a-floor-on-the-mean-confidence-score-low-score-is-not-distributed.md)) is calibrated from `mean_score` values, and those describe a conversion the cache would happily keep serving after the pipeline changed underneath it.

**Configuring a VLM OCR backend is now a visible act.** Sending `pipeline=vlm` with a `vlm_pipeline_model` puts both in the identity, so the corpus is re-extracted under a new key and the two engines' output can never be mixed in one cache. That was the concrete trigger for #89 — chandra-ocr-2 as an alternative to docling's default OCR — and it is worth noting that chandra is not in v1.32.0's `vlm_pipeline_model` enum, so it would arrive through `vlm_pipeline_model_api` or `_local`: client-supplied again, and therefore covered by the same rule.

**`ExtractorIdentity` gains a composer, and the record gains a reason to have one.** The type still only carries a composed value and refuses a blank one; what changes is that composing it now requires an HTTP call to `/version` before the first conversion. `DoclingClient` already makes exactly one such call at that moment — `checkHealth()`, ADR-071's lazy readiness check — so this is a second endpoint on an existing seam rather than a new one.

**A sidecar that cannot be reached cannot yield an identity.** The readiness check already fails the step in that case (ADR-071), so no new failure mode is introduced; but the ordering is now load-bearing rather than incidental, and the hand-off work should say so.

**`/version`'s `plaform` key is misspelled upstream.** Recorded here because a reader will otherwise assume a typo in our own code. Whether the platform string belongs in the identity at all is a fair question — it pins the machine rather than the software — and the answer is that it stays, because it is exactly what distinguishes two deployments that resolve `auto` differently. With `ocr_engine` now pinned that argument weakens, and it may be droppable later.

**Nothing here fixes the wiring.** Which bean calls `/version`, how the map is serialised into the string, what the explicit `ocr_engine` value should be, and whether the sent options belong in `configConsumed` as well as in the cache key are implementation work — the same deferral every prior decision made to its own spec.
