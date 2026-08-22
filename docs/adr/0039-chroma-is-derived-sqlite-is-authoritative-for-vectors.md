# ADR-039 — Chroma is derived; SQLite is authoritative for vectors

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/architecture.md`](../architecture.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-039 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-039, compiled 2026-08-21 |

## Summary

Vectors written to SQLite when computed; Chroma populated from that cache, droppable/rebuildable at any time. Reconciles ADR-020 with ADR-032. *(Open condition resolved by ADR-045.)*

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: [ADR-020](0020-relevance-scoring-function.md), [ADR-032](0032-embeddings-are-durable-the-index-is-disposable.md), [ADR-045](0045-clustering-runs-within-each-seed-partition.md)
- **Named by**: [ADR-045](0045-clustering-runs-within-each-seed-partition.md)
- **Discussed in the digest at**: §1.5 Data architecture, §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
