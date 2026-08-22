# ADR-045 — Clustering runs within each seed partition

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/architecture.md`](../architecture.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-045 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-045, compiled 2026-08-21 |

## Summary

Never corpus-wide. Bounds compute (N²/2 distances per partition, not corpus-wide), keeps the "seed owns 60%" alarm aligned with a real cost, enables Batch-native parallelism, resolves ADR-039's open sizing condition.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md)
- **Named by**: [ADR-027](0027-clustering-moves-to-stage-5.md), [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md)
- **Discussed in the digest at**: §1.2 The cascade (ADR-017), §1.3 Core architectural model, §1.5 Data architecture

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
