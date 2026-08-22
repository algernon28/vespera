# ADR-040 — Modules are capability-shaped, not stage-shaped

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/handoffs/vespera-architecture-and-decisions.md`](../handoffs/vespera-architecture-and-decisions.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-040 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-040, compiled 2026-08-21 |

## Summary

Defines the 9 modules (`ledger`, `corpus`, `extraction`, `similarity`, `embedding`, `synthesis`, `publication`, `profile`, `pipeline`) and the rule: a capability module depends only on `ledger`.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.4 Module boundaries (ADR-040, ADR-041, ADR-042), §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
