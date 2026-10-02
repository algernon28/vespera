# ADR-172 — docling-serve's synchronous wait outlasts Vespera's call timeout

- **Date**: 2026-10-01
- **Status**: accepted
- **Amends**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md). Its local call timeout decides a slow file only if the sidecar has not given up on the call first; this record makes sure it has not.
- **Rests on**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md), whose eight calls in flight queue behind docling-serve's workers, and [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md), which already gives `compose.yaml`'s docling-serve service the settings an operator should not have to remember.
- **Settles** [#362](https://github.com/algernon28/vespera/issues/362).

## Context

### What happened

On 2026-09-28 at 16:57 the whole-archive run failed in stage 2 after reading 4,624 files, on `504 Gateway Timeout: "{"detail":"Conversion is taking too long. The maximum wait time is configure as DOCLING_SERVE_MAX_SYNC_WAIT=120."}"`. The file was a 1.25 MB, 161-page PDF, which docling-serve's worker took 140 s to convert. On 2026-09-30 two more invocations stopped the same way, after the sidecar had been recreated from `compose.yaml` alone (#373).

### How docling-serve answers the synchronous call

In docling-serve 1.32.0, `POST /v1/convert/file` queues the job and polls its status every 2 s. Once `max_sync_wait` seconds have passed since the job was **queued**, so that time spent waiting for a worker counts too, it answers HTTP 504 with the body above. It does not abort the job: the source says `# TODO: abort task!`, and the job above finished 20 s after the 504. The setting is read from `DOCLING_SERVE_MAX_SYNC_WAIT`, an integer number of seconds, default 120; a probe container read it back as 600 when it was set so.

### Why that stops stage 2

`DoclingClient.CALL_TIMEOUT` is 5 minutes (ADR-071). A call that runs past it becomes `DoclingCallTimeoutException`, which ADR-071's breakers handle: an isolated timeout marks that file `extraction-failed` and the run goes on. With docling-serve's default of 120 s, that path is never reached. The 504 arrives first, as an HTTP error the step does not skip, and stage 2 fails on it. (#326 decides how every per-file error is handled; this record only makes sure that a slow file is a timeout.)

### Measurements

Taken on 2026-09-28 on a 16-CPU, 30 GB machine, with the request Vespera sends (`to_formats=json`, `ocr_preset=rapidocr`, `image_export_mode=embedded`, the file named `document.pdf`), on the CPU image:

| Setup | Result |
|---|---|
| The 161-page PDF alone, wait 600 | HTTP 200 in 122 s (`processing_time` 120 s), `status: success` |
| 16 random archive PDFs (150–900 KB), 8 in flight as ADR-140 sends them, 2 workers, wait 120 | wall time 233 s; at least one 504 |
| The same 16, 2 workers, wait 600 | wall time 230 s; every call HTTP 200; call times 12–189 s, queue wait included |
| The same 16, 4 workers (`DOCLING_SERVE_ENG_LOC_NUM_WORKERS=4`), wait 600 | wall time 228 s, no faster; every call HTTP 200; the slowest call 222 s |

Conversion is CPU-bound (each worker runs with `OMP_NUM_THREADS=4`). Measured, four workers finish the batch no sooner than two and make each call slower, which brings calls closer to the 300 s client timeout.

## Decision

1. **`compose.yaml` sets `DOCLING_SERVE_MAX_SYNC_WAIT: '600'` on the docling-serve service**, twice `DoclingClient.CALL_TIMEOUT`, so that Vespera's own timeout is always the one that fires. `compose.gpu.yaml` names no environment for the service, so the value holds under it too.
2. **`TestcontainersConfiguration` gives the test sidecar the same value**, from a named constant, so the Docker integration tests run docling-serve the way compose does.
3. **The worker count stays at docling-serve's default of 2.** The measurements above show more workers buy nothing and cost call time.
4. **`DoclingSyncWaitTest` holds the rule**: compose declares the variable, its value is greater than `DoclingClient.CALL_TIMEOUT` in seconds, and the test sidecar's constant equals it. `CALL_TIMEOUT` is made public so that test, in another package, can read it rather than copy 300.

## Consequences

- **A slow file is decided by Vespera, not by the sidecar.** A file whose call runs past 5 minutes, queue time included, is a timeout under ADR-071: an isolated one marks the file `extraction-failed` and the run goes on; ADR-071's streaks still stop a sidecar that times out on everything.
- **A file that takes between 120 s and 300 s is now converted** instead of stopping the run.
- **A call Vespera abandons still holds a worker until it finishes**, because neither side aborts it (ADR-171 records the same for text). Raising the wait does not change that; it only changes which side gives up first.
- **The extractor identity does not change.** It is built from `vespera.docling.image` and `/version`, not from the environment, so every cached conversion stays valid.
- **An existing stack picks the value up on `docker compose -p vespera up -d docling-serve`**, naming `compose.gpu.yaml` too if the stack was started with it. No rebuild is needed: the image is unchanged. A local override file that set the same value can be removed.
