# ADR-028 — Relevance threshold: human labelling, gated by score distribution

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/handoffs/vespera-architecture-and-decisions.md`](../handoffs/vespera-architecture-and-decisions.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-028 |
| **Date** | 2026-08-20 |
| **Source** | decision-ledger row for ADR-028, compiled 2026-08-21 |

## Summary

Go/no-go on distribution shape, then calibrate via sampled human labelling at the candidate cut. Cluster-level adjudication and leave-one-out seed scoring rejected as the calibration mechanism.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: [ADR-034](0034-embedding-model-chosen-by-bake-off-not-argument.md)
- **Discussed in the digest at**: §1.3 Core architectural model

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
