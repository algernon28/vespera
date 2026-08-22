# ADR-032 — Embeddings are durable; the index is disposable

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-032 |
| **Date** | 2026-08-20 |
| **Source** | decision-ledger row for ADR-032, compiled 2026-08-21 |

## Summary

Vectors cached (chunk hash + model identity); ANN index is rebuildable. *Partially retired by ADR-047* (index no longer needs to survive human-paced gates, since the pipeline terminates instead of pausing).

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: [ADR-047](0047-the-pipeline-never-blocks.md)
- **Named by**: [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md), [ADR-047](0047-the-pipeline-never-blocks.md)
- **Discussed in the digest at**: §1.5 Data architecture, §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
