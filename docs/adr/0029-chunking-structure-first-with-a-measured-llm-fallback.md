# ADR-029 — Chunking: structure-first, with a measured LLM fallback

> **Partly amended — see [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md).** *"LLM fallback only for measured structureless (scanned) cases, currently off"*: the seam that was off is removed, and so is the windowed fallback that ran in its place and was only ever handed empty text, so a structureless document yields no chunks; a fallback, measured or not, is a record of its own. Decided on 2026-10-09; the code leaves with the next change that re-mints `extraction`. *"Default Docling `HybridChunker`"* and *"Boundaries cached, keyed by content hash + chunker identity"* stand. The summary is transcribed verbatim and is not edited.

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-029 |
| **Date** | 2026-08-20 |
| **Source** | decision-ledger row for ADR-029, compiled 2026-08-21 |

## Summary

Default Docling `HybridChunker`, tokenizer-aligned; LLM fallback only for measured structureless (scanned) cases, currently off. Boundaries cached, keyed by content hash + chunker identity.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.5 Data architecture, §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
