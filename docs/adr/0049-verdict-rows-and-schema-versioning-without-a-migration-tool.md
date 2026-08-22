# ADR-049 — Verdict rows, and schema versioning without a migration tool

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/handoffs/vespera-architecture-and-decisions.md`](../handoffs/vespera-architecture-and-decisions.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-049 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-049, compiled 2026-08-21 |

## Summary

Every stage writes a row per occurrence including non-blocking `passed`; verdicts use a surrogate key (multiple rows allowed). Schema via `schema.sql` + version check; migration tool deferred until real data exists.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.3 Core architectural model, §1.5 Data architecture, §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
