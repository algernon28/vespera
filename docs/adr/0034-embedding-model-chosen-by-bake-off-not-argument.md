# ADR-034 — Embedding model chosen by bake-off, not argument

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-034 |
| **Date** | 2026-08-20 |
| **Source** | decision-ledger row for ADR-034, compiled 2026-08-21 |

## Summary

Same sample/seeds, ADR-028's gate run under each candidate. Abort verdicts must be confirmed against a larger model. *Clarified by ADR-044: "same chunking" means method, not byte-identical output.*

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: [ADR-028](0028-relevance-threshold-human-labelling-gated-by-score-distribution.md), [ADR-044](0044-the-bake-off-re-chunks-per-candidate-model.md)
- **Named by**: _none_
- **Discussed in the digest at**: §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
