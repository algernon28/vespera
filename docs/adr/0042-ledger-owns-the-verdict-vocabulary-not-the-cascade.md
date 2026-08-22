# ADR-042 — `ledger` owns the verdict vocabulary, not the cascade

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-042 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-042, compiled 2026-08-21 |

## Summary

Fixed, closed verdict vocabulary with blocking-ness, exposed via `survivors(runId)`. Runtime/opaque verdict registries rejected — the failure mode of drift is asymmetric (over-publishing).

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.2 The cascade (ADR-017), §1.3 Core architectural model, §1.4 Module boundaries (ADR-040, ADR-041, ADR-042)

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
