# ADR-036 — Spring Batch with `ResourcelessJobRepository`; Camel dropped

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/handoffs/vespera-architecture-and-decisions.md`](../handoffs/vespera-architecture-and-decisions.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-036 |
| **Date** | 2026-08-20 |
| **Source** | decision-ledger row for ADR-036, compiled 2026-08-21 |

## Summary

Batch used for retry/skip/parallelism on long extraction runs, not restartability (ledger owns that). No integration topology needed, so Camel dropped.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.2 The cascade (ADR-017), §1.6 Orchestration & invocation, §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
