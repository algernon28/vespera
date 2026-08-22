# ADR-038 — Shingling moves to stage 3; boilerplate detected before it distorts anything

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/architecture.md`](../architecture.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-038 |
| **Date** | 2026-08-20 |
| **Source** | decision-ledger row for ADR-038, compiled 2026-08-21 |

## Summary

Shingles computed as a derived column during extraction; corpus-wide document frequency identifies boilerplate before it corrupts stage-4 dedup or stage-5 relevance (false smear/abort risk).

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.2 The cascade (ADR-017)

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
