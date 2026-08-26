# Decision records

One file per architecture decision, ADR-001 through ADR-049.

> **These files are reconstituted records.** The original ADR text was lost before 2026-08-22; what survived is the condensed decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md). Each file here carries that row verbatim — id, date, title, summary, the cross-references named in it, and pointers to the digest sections that discuss it — under a provenance header, and nothing more. No rationale has been reconstructed, because none survives to reconstruct: a Context section written today from a one-line summary would read as recorded history while being invention.

**The digest remains the fuller record.** For most of these decisions the architecture sections (§1, §2) say more than the ledger row does, and each file links to the sections that mention it.

Decisions are append-only. A decision is reopened only by a later ADR that explicitly references and amends it; several summaries note exactly that. New decisions continue from ADR-050 and carry their own full text — the reconstitution rule applies only to the records restored here.

The vocabulary these decisions are written in is defined in [`CONTEXT.md`](../../CONTEXT.md) and is binding.

| ID | Date | Title |
| --- | --- | --- |
| [ADR-001](0001-tech-stack-is-a-fixed-constraint.md) | 2026-08-20 | Tech stack is a fixed constraint |
| [ADR-002](0002-confluence-is-the-publication-target.md) | 2026-08-20 | Confluence is the publication target |
| [ADR-003](0003-domain-agnostic-and-reusable.md) | 2026-08-20 | Domain-agnostic and reusable |
| [ADR-004](0004-relevance-defined-by-an-exemplar-seed-set.md) | 2026-08-20 | Relevance defined by an exemplar seed set |
| [ADR-005](0005-prototype-first.md) | 2026-08-20 | Prototype first |
| [ADR-006](0006-census-measure-before-judging.md) | 2026-08-20 | Census: measure before judging |
| [ADR-007](0007-no-supplied-negative-example-set.md) | 2026-08-20 | No supplied negative example set |
| [ADR-008](0008-sqlite-is-the-census-artifact-store.md) | 2026-08-20 | SQLite is the census artifact store |
| [ADR-009](0009-one-storage-technology-single-database.md) | 2026-08-20 | One storage technology; single database |
| [ADR-010](0010-extraction-via-docling-scanned-pdfs-in-scope.md) | 2026-08-20 | Extraction via Docling; scanned PDFs in scope |
| [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md) | 2026-08-20 | Managed containers; the tool owns its sidecars |
| [ADR-012](0012-extraction-engine-is-configurable.md) | 2026-08-20 | Extraction engine is configurable |
| [ADR-013](0013-ollama-is-the-default-engine.md) | 2026-08-20 | *(config)* Ollama is the default engine |
| [ADR-014](0014-verdict-ledger-model.md) | 2026-08-20 | Verdict-ledger model |
| [ADR-015](0015-identity-is-a-surrogate-key-per-file-occurrence.md) | 2026-08-20 | Identity is a surrogate key per file occurrence |
| [ADR-016](0016-the-corpus-is-treated-as-static.md) | 2026-08-20 | The corpus is treated as static |
| [ADR-017](0017-the-cascade.md) | 2026-08-20 | The cascade |
| [ADR-018](0018-stage-4-uses-minhash-with-lsh-banding.md) | 2026-08-20 | Stage 4 uses MinHash with LSH banding |
| [ADR-019](0019-content-census-is-derived-columns-plus-a-report.md) | 2026-08-20 | Content census is derived columns plus a report |
| [ADR-020](0020-relevance-scoring-function.md) | 2026-08-20 | Relevance scoring function |
| [ADR-021](0021-synthesis-exists-to-make-the-survivor-set-coherent.md) | 2026-08-20 | Synthesis exists to make the survivor set coherent |
| [ADR-022](0022-stage-6-splits-into-arrangement-6a-then-generation-6b.md) | 2026-08-20 | Stage 6 splits into arrangement (6a) then generation (6b) |
| [ADR-023](0023-surviving-originals-stored-in-confluence-as-attachments.md) | 2026-08-20 | Surviving originals stored in Confluence as attachments |
| [ADR-024](0024-publication-is-terminal-and-one-shot.md) | 2026-08-20 | Publication is terminal and one-shot |
| [ADR-025](0025-stage-7-is-an-adapter-not-a-stage.md) | 2026-08-20 | Stage 7 is an adapter, not a stage |
| [ADR-026](0026-generated-content-verified-mechanically-and-by-human-review.md) | 2026-08-20 | Generated content verified mechanically and by human review |
| [ADR-027](0027-clustering-moves-to-stage-5.md) | 2026-08-20 | Clustering moves to stage 5 |
| [ADR-028](0028-relevance-threshold-human-labelling-gated-by-score-distribution.md) | 2026-08-20 | Relevance threshold: human labelling, gated by score distribution |
| [ADR-029](0029-chunking-structure-first-with-a-measured-llm-fallback.md) | 2026-08-20 | Chunking: structure-first, with a measured LLM fallback |
| [ADR-030](0030-late-chunking-not-adopted.md) | 2026-08-20 | *(parked)* Late chunking not adopted |
| [ADR-031](0031-human-gates-are-optional.md) | 2026-08-20 | Human gates are optional |
| [ADR-032](0032-embeddings-are-durable-the-index-is-disposable.md) | 2026-08-20 | Embeddings are durable; the index is disposable |
| [ADR-033](0033-embedding-model-criteria-recorded-model-unset.md) | 2026-08-20 | Embedding model: criteria recorded, model unset |
| [ADR-034](0034-embedding-model-chosen-by-bake-off-not-argument.md) | 2026-08-20 | Embedding model chosen by bake-off, not argument |
| [ADR-035](0035-pipeline-never-publishes-adapter-invoked-separately-never-unattended.md) | 2026-08-20 | Pipeline never publishes; adapter invoked separately, never unattended |
| [ADR-036](0036-spring-batch-with-resourcelessjobrepository-camel-dropped.md) | 2026-08-20 | Spring Batch with `ResourcelessJobRepository`; Camel dropped |
| [ADR-037](0037-spring-modulith-event-publication-registry-dropped.md) | 2026-08-20 | Spring Modulith event publication registry dropped |
| [ADR-038](0038-shingling-moves-to-stage-3-boilerplate-detected-before-it-distorts-anything.md) | 2026-08-20 | Shingling moves to stage 3; boilerplate detected before it distorts anything |
| [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md) | 2026-08-21 | Chroma is derived; SQLite is authoritative for vectors |
| [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) | 2026-08-21 | Modules are capability-shaped, not stage-shaped |
| [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) | 2026-08-21 | `ledger` owns identity and verdicts; capabilities own their own tables |
| [ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md) | 2026-08-21 | `ledger` owns the verdict vocabulary, not the cascade |
| [ADR-043](0043-the-profile-is-authored-as-a-file-and-recorded-in-the-ledger.md) | 2026-08-21 | The profile is authored as a file and recorded in the ledger |
| [ADR-044](0044-the-bake-off-re-chunks-per-candidate-model.md) | 2026-08-21 | The bake-off re-chunks per candidate model |
| [ADR-045](0045-clustering-runs-within-each-seed-partition.md) | 2026-08-21 | Clustering runs within each seed partition |
| [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) | 2026-08-21 | The pom carries what a recorded decision requires |
| [ADR-047](0047-the-pipeline-never-blocks.md) | 2026-08-21 | The pipeline never blocks |
| [ADR-048](0048-walk-and-run-identity.md) | 2026-08-21 | Walk and run identity |
| [ADR-049](0049-verdict-rows-and-schema-versioning-without-a-migration-tool.md) | 2026-08-21 | Verdict rows, and schema versioning without a migration tool |

## Decisions written after the reconstitution

These carry their own full text: context, decision and consequences, as originally intended for an ADR.

| ID | Date | Title |
| --- | --- | --- |
| [ADR-050](0050-the-corpus-is-a-staged-copy.md) | 2026-08-26 | The corpus is a staged copy, exclusively owned by the pipeline |
