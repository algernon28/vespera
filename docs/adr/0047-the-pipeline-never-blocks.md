# ADR-047 — The pipeline never blocks

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/architecture.md`](../architecture.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-047 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-047, compiled 2026-08-21 |

## Summary

Terminates at a missing gate input, resumes on re-invocation; iteration happens between runs. Sizes the CLI to two commands. Auto-derivation of thresholds is an explicit, off-by-default profile input. Partially retires ADR-032.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: [ADR-032](0032-embeddings-are-durable-the-index-is-disposable.md)
- **Named by**: [ADR-032](0032-embeddings-are-durable-the-index-is-disposable.md)
- **Discussed in the digest at**: §1.3 Core architectural model, §1.5 Data architecture, §1.6 Orchestration & invocation, §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
