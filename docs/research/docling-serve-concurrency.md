# docling-serve: the per-request tick and concurrency scaling

Research record for [#264](https://github.com/algernon28/vespera/issues/264). Facts only — no decisions. Feeds the
ADR the ticket asks for, which records stage 2's concurrency and what "consecutive" means under it.

## Scope and method

Every claim below is labelled `MEASURED` and was produced by driving the sidecar with a throwaway probe on the
machine described below on 2026-09-22. Nothing here is read off the ticket's numbers; the ticket's own heavy-tail
distribution is cited where it is, and told apart from what this record re-measured. The probe lived outside the
repository (per `AGENTS.md`) and is not committed.

The probe posted a short plain-text document to `POST /v1/convert/file` with the same multipart shape the client
sends — `files`, `to_formats=json`, `ocr_preset=rapidocr` — and recorded each round trip plus the `processing_time`
the sidecar reported in the body. Each concurrency level was run three times; the request count per run equals the
concurrency, all fired together and awaited as a set.

Measurement environment:

| | |
|---|---|
| Sidecar image | `quay.io/docling-project/docling-serve-cpu:v1.32.0` (the one `compose.yaml` pins) |
| Reported build | `docling-serve 1.32.0`, `docling 2.124.0`, `docling-ibm-models 4.0.1`, `python cpython-312` |
| Host | Windows 11, WSL2 (`Linux-6.6.87.2-microsoft-standard-WSL2-x86_64`) |
| Docker | 29.6.1; the `vespera-docling-serve-1` container, no CPU or memory cap |
| Client | Node 22, `fetch`, concurrency via `Promise.all` |

What was **not** measured, and so is not claimed here: the conversion-time distribution on a real corpus. The
sidecar saturates on conversion work, not on the tick, and a small plain-text file exercises none of it (`§1`). The
ticket's own figures — a mean of about 1.5 s per document across 129 files, 39 under 10 ms against 9 over 5 s and one
at 58 s — are the record for that, and they are the operator's live run, not this probe's.

---

## 1. The tick is 2 s flat, against milliseconds of conversion

`MEASURED`. A single request costs about two seconds regardless of what it converts. Three round trips through the
same 74-byte plain-text file:

| | round trip | sidecar's own `processing_time` |
| --- | --- | --- |
| 1 | 2.059 s | 0.0256 s |
| 2 | 2.007 s | 0.0062 s |
| 3 | 2.008 s | 0.0017 s |

Two seconds, flat, against a handful of milliseconds of conversion. A hard floor at 2.000 s with almost no variance
is the signature of a sync endpoint polling its own job queue on a two-second tick, seen from outside. The one
reproduction above of the ticket's exact claim (the shape, not just the total) is the point: the floor is in the
current image, this machine, this week.

## 2. Concurrency scales linearly; the sidecar imposes no visible ceiling in the measured range

`MEASURED`. Throughput rises with the request count, and the per-request latency stays at the two-second floor, so the
sidecar drains a concurrent queue rather than serialising it:

| concurrency | wall for the whole batch | per-request round trip (median) | throughput |
| --- | --- | --- | --- |
| 1 | 2.01–2.06 s | 2.01–2.05 s | ~0.49 doc/s |
| 2 | 2.01–2.02 s | 2.01–2.02 s | ~0.99 doc/s |
| 4 | 2.01–2.02 s | 2.01–2.02 s | ~1.98 doc/s |
| 6 | 2.01–2.02 s | 2.01–2.02 s | ~2.98 doc/s |
| 8 | 2.01–2.04 s | 2.01–2.02 s | ~3.95 doc/s |
| 12 | 2.03–2.04 s | 2.02–2.03 s | ~5.90 doc/s |
| 16 | 2.03–2.04 s | 2.02–2.03 s | ~7.86 doc/s |
| 24 | 2.05–2.05 s (two of three) | 2.03–2.04 s | ~11.7 doc/s |

Ranges are the three runs per level. Throughput is the batch size over the batch wall clock; it rises about 0.5 docs
per second per unit of concurrency, which is exactly what a two-second flat tick predicts when nothing is being
serialised behind it. Two facts bound this from below and above:

- **The floor is per-request, not shared.** Six requests finish in the same two seconds one does, and the latency
  holds at ~2 s as the batch grows, so the tick is a per-call property. There is no global two-second door the whole
  batch queues behind.
- **One 24-run was an outlier.** The first of the three runs at 24 took ~4.1 s per request (throughput ~5.8 doc/s
  instead of ~11.7), against ~2.0 s in the other two. It is recorded rather than explained: WSL2 CPU contention on
  this host is the likely cause, but the cause was not established and nothing below leans on it.

The consequence for any width decision is that the sidecar does not itself say where concurrency should stop — at
least not at the small-document size this probe used. The bound on real corpora is the conversion work behind the
tick, which `§1` deliberately did not measure and which is the lumpy half the ticket names.

## 3. What the ADR has to answer that this record does not

Facts, restated compactly, for whoever writes the record:

1. The two-second tick is real and per-request; removing it by concurrency is a near-linear win up to the width
   chosen.
2. The sidecar shows no saturation up to 16 concurrent on trivial documents, and two of three runs at 24 act the same,
   so the width is constrained elsewhere — client resources, and the conversion work on real documents.
3. The conversion-time distribution this does not measure is the thing that will actually saturate the sidecar, and
   it is the ticket's own live-run numbers, not this probe.