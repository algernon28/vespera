# ADR-044 — The bake-off re-chunks per candidate model

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-044 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-044, compiled 2026-08-21 |

## Summary

Each embedding candidate is compared with its own tokenizer-aligned chunking, to avoid incumbent-tokenizer bias. Cache key must carry tokenizer identity.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: [ADR-034](0034-embedding-model-chosen-by-bake-off-not-argument.md)
- **Discussed in the digest at**: §1.5 Data architecture, §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
