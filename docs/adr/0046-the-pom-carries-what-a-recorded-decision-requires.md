# ADR-046 — The pom carries what a recorded decision requires

> **Partly amended — see [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md).** The removals gain `spring-boot-starter-actuator`, `spring-boot-starter-actuator-test`, `spring-boot-starter-batch-test`, `lombok`, and the compiler configuration that only named annotation processors, none of them required by a recorded decision. The additions' *"Ollama starter alongside OpenAI"* no longer holds: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) removed the OpenAI starter, and ADR-216 records it here. *"Not 'what current code uses'"* stands, and both records apply it. The summary is transcribed verbatim and is not edited.

> **Reconstituted record — the original text of this ADR is lost.**
> Rebuilt on 2026-08-22 from the decision-ledger table in [`docs/decision-ledger.md`](../decision-ledger.md), the only surviving record of these decisions. The summary below is transcribed **verbatim** from that digest.
> There are deliberately no Context, Decision or Consequences sections: that rationale was not recorded in the digest, and inferring it would place invented reasoning under an original date. Where a later decision amends this one, the digest says so inside the summary, and it is transcribed as written.

|  |  |
| --- | --- |
| **Id** | ADR-046 |
| **Date** | 2026-08-21 |
| **Source** | decision-ledger row for ADR-046, compiled 2026-08-21 |

## Summary

Not "what current code uses." Lists specific removals (Camel, vector-store advisor, batch-jdbc, document readers, contract-verifier, Modulith observability/actuator) and additions (Ollama starter alongside OpenAI), each tied to an ADR.

## Cross-references

Extracted mechanically from the digest; the direction of an amendment is only as explicit as the summary above makes it.

- **Names**: _none_
- **Named by**: _none_
- **Discussed in the digest at**: §2. Tech stack

Those sections hold surviving detail this row does not. Read them before treating the summary above as the whole decision.
