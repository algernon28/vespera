# ADR-014 — Verdict-ledger model

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/architecture.md`](../architecture.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-014 |
| **Date** | 2026-08-20 |
| **Source** | decision-ledger row for ADR-014, compiled 2026-08-21 |

## Summary

Documents never move; stages append verdict rows. "Survivors" is a query. Enables cheap retuning and full-audit "why not published" queries.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.3 Core architectural model

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
