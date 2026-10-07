# ADR-041 — `ledger` owns identity and verdicts; capabilities own their own tables

> **Partly amended — see [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md).** The enforcement gap for raw SQL that the summary below records is closed: `schema.sql` names the module that owns each table on the line above it, and `EachTableIsNamedOnlyByItsOwnerTest` fails when a module's SQL names a table another module owns. What it still lets through is ADR-198's one exception, which it now holds to that record's bounds; what it cannot see are the two forms ADR-209 §3.4 names, a table's name assembled at run time and a table reached through a view or a trigger. The side tables per capability stand. The summary is transcribed verbatim and is not edited.

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-041 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-041, compiled 2026-08-21 |

## Summary

Side tables per capability, keyed by `occurrence_id`, driven by cache-key semantics (chunk/vector caches aren't keyed by occurrence). Records a known ArchUnit enforcement gap for raw SQL.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §1.4 Module boundaries (ADR-040, ADR-041, ADR-042)

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
