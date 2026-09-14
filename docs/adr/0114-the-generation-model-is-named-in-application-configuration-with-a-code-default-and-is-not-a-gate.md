# ADR-114 — The generation model is named in application configuration with a code default, overridable in the profile, and is not a gate

- **Date**: 2026-09-14
- **Status**: accepted
- **Settles what [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) and [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) left unsaid**: both make the model name part of the generator identity, and neither says where the name comes from. [#175](https://github.com/algernon28/vespera/issues/175)'s hand-off spec named this the one thing it could not settle, and took a build-level call so implementation was not blocked. This record settles it, and takes that call.
- **Amends**: [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) — its rule *"Which model is a profile key; where it is served stays configuration"* is narrowed to models whose identity keys a durable artifact. Everything else that record decided stands unchanged, including every ground on which the embedding model is a gated profile key with no default.
- **Amends**: [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) — the arrangement gate's message gains one line, naming the model the next invocation will generate under. Nothing else about that gate changes: it still approves a named 6a run, and it still names the missing value as the single next action.
- **Rests on**: ADR-110 (the generator identity carries the model's artefact digest), [ADR-013](0013-ollama-is-the-default-engine.md) (a serving-side default is application configuration, and changeable without a new ADR), [ADR-012](0012-extraction-engine-is-configurable.md) (the serving runtime is config, not code), [ADR-072](0072-adr-034-is-one-bake-off-the-extraction-engine-has-a-reference-model-not-a-bake-off.md) (there is one bake-off in this design, and it is the embedder's).

## Context

ADR-108 composed the generator's identity as *"the model name plus the options actually sent"*; ADR-110 added the model's artefact digest to it and left `pipeline` to assemble the whole and hand it down. #175 then derived the 6b run id from that identity. So the name is load-bearing before anything reads it: **nothing that mints a generation run can be built until it is known where the name comes from**, which is why this is the first ticket of stage 6b rather than a tidy-up after it.

The research record 6b rests on put *"which model to serve"* out of scope by name, so there is nothing upstream to read the answer off. It has to be decided here.

**The two candidate shapes were already both in the tree**, which is what makes this worth a record:

- **ADR-084**: the embedding model is a **profile key with no default**, and an unset one **gates** stage 5 — the invocation ends, the job succeeds, and no scoring run is minted. Its rule is the heading *"Which model is a profile key; where it is served stays configuration"*. Taken at full width, that rule decides this ticket by itself.
- **ADR-108**: `num_ctx` — another part of the same generator identity — ships as a **code default, overridable in the profile, and explicitly not a gate**, because *"The operator's path is already five invocations (ADR-107), and a sixth required value, for a number most operators cannot reason about, is not worth the stop."*

One identity, assembled from two contradictory sourcing rules, is the thing to avoid.

## Decision

**The generation model's name is application configuration with a code default. The profile may override it. Unset means the default, never a stop.**

### Where the value comes from, in order

1. **`spring.ai.ollama.chat.options.model` in `application.yaml`**, carrying the shipped default. It goes where Spring AI's own auto-configuration already reads it, rather than under a `vespera.*` key, so the bean and the call name one model instead of two.
2. **A `generationModel` key in the profile**, when set, overriding it. It exists for the same reason ADR-108 gave `num_ctx` one: a corpus generated under something other than the default is a judgement about that corpus, and `ProfileValue` is what holds a judgement together with its `provenance`.
3. **`pipeline` resolves the two into one name** and hands it to `synthesis` as a plain string, alongside the digest, exactly as ADR-110 requires — `synthesis` may not name `OllamaClient`, so it never resolves anything itself and never learns there was a default.

**No `Measurement` pointer**, for ADR-084's reason on `embeddingModel`, unchanged: naming a model is not something a measurement pass could inform.

### ADR-084's rule, narrowed rather than contradicted

ADR-084 drew its line from ADR-012's — which it renders as *"the serving runtime is config, not code"* — and put the model name on the profile side of it. **The line it actually drew is about what has to travel with the corpus**, and the reason it gives is specific: *"It has to travel with the database, because a score is meaningless without it."*

That reason holds exactly where a model's identity keys a durable artifact. Every vector carries `embedder_identity`; read a score without knowing the model and you have a number with no scale. **A generation model keys a run whose output is prose.** Nothing later reads a synthesis doc and needs the model name to interpret it — the name is recorded because provenance is owed, not because meaning depends on it.

So the rule is narrowed to that condition rather than widened by exception, and the narrowing is marked as an amendment above, because a future reader taking the rule at full width would decide this question the other way and be entitled to.

### Unset is the default, and blank is unset

`ProfileValue.isSet()` already treats a blank value as unset, so `generationModel: ""` in the file resolves to the default rather than to a fault. That is the behaviour this record wants and it needs no new code: the resolved name is never blank, so the instrument rule `CONTEXT.md` states — *"It refuses a blank value, because an instrument that cannot say what it is cannot be told apart from one that can"* — is satisfied by construction rather than by a check on the profile.

**Two things can still go wrong, and both stop the run rather than faulting a cluster**, because each poisons every cluster and not one of them. Both are checked in `pipeline`, **where the name is resolved and before the 6b run is minted** — ADR-084's placement, for ADR-084's stated reason, which it credits to ADR-080: a run row for a stage that did nothing reads as a pass that found nothing. The placement is borrowed and the shape is not, since neither of these is a gate:

- **A blanked application default.** A misconfiguration rather than an unanswered question, so it is not a gate and gets no `NextAction` line.
- **A resolved name the serving engine has never pulled.** `OllamaClient.artefactOf` throws rather than composing an identity around a missing digest, which is the right stop: there is no honest identity to mint a run under. This binds [#179](https://github.com/algernon28/vespera/issues/179) in one further way — `artefactOf` is written for the embedder and says so in its own javadoc and both of its throw messages, so 6b either generalises it or grows a sibling beside it.

### The shipped default is `qwen3:8b`, chosen on friction

ADR-013's shape, and ADR-013's words: a serving-side default *"Chosen on friction; changeable without a new ADR."* **This is not a measured choice and it is not a candidate that beat anything.** `CONTEXT.md`'s **bake-off** entry is deliberately narrow — the embedding-model selection mechanism specifically, and *"there is exactly one bake-off in this design"* (ADR-072). Naming a generation default mints no second one, and a later comparison of generation models would be an ordinary reading of two deliverables.

What the criteria below are for is the **next** person changing the tag, not a defence of this one. A replacement must be servable by Ollama locally (ADR-013); must honour a JSON schema on `/api/chat`, since ADR-108 imposes one and validates it client-side; must handle the languages the corpus and seed set are in, which ADR-033's recorded criteria give as Italian and English together; and must have a trained context length long enough that ADR-108's explicit `num_ctx` is worth sending. **None of these has been measured here, and no hardware record exists in this tree to size a model against** — what stands behind the tag is friction, exactly as ADR-013 allows, and what stands behind a corpus is the identity that says which weights actually answered.

What makes a drifting default survivable is ADR-110's digest: a re-pull of the same mutable tag over different weights mints a new 6b run rather than quietly extending the old one. A model name that can move is only dangerous when nothing records which weights produced which tree.

### Why this is not the embedding model's treatment

ADR-084 gates on four things, and **not one of them holds for the generation model**:

| ADR-084's ground for the gate | Whether it reaches here |
|---|---|
| The choice is owed to a **bake-off nobody has run** (ADR-034), and a default *"would settle by inertia the one thing ADR-034 says must be settled by measurement"* | No. ADR-072 confines the bake-off to the embedder. Nothing is owed here, so a default pre-empts no measurement |
| The name **keys durable cached artifacts** — every vector carries `embedder_identity`, and two models' output under one identity is a corrupted cache | No. The generation model keys a run, and a run's output is prose. No cache is shared across models and no row is silently mixed |
| **Every relevance verdict rests on it.** A corpus scored under a model nobody chose has verdicts nobody can defend, and ADR-084 prices the remedy as *"one re-chunk and one embedding pass"* | No. 6b writes **no verdict at all** — ADR-111 made each of the four ways it can fail a cluster fault, recorded against the cluster, and a streak of five stops the step without removing anything either. The remedy for disliking the prose is `DELETE` of one run's rows and a re-run, which is what ADR-080 calls *"the ledger's ordinary posture for retuning a threshold"* |
| **Unset has nothing to fall back on.** ADR-033 recorded criteria, not a choice | No. There is a defensible choice, and it is named above |

Underneath all four is one difference worth stating on its own. **The embedding model is gated because its quality is invisible without a measurement** — nobody can read a 4096-dimension vector and tell whether it was any good, which is what *observe before enforce* exists for. **Generation quality is legible in the artifact itself.** The operator opens the deliverable and reads it. Gating a choice the operator can check afterwards, in the one stage whose output is prose, buys a stop and no information.

### The case for a gate, and why it is refused

Stated at full strength: the generation model is the single largest determinant of the deliverable's quality, the deliverable is the product rather than an intermediate (ADR-101), and an operator who never notices the key receives prose written by a model they never chose. Requiring them to name it makes that choice conscious once.

It is refused on three grounds:

- **It buys a stop rather than a decision.** An operator with no basis for choosing answers a required key by copying whatever the documentation shows them — which is the default, arriving by a longer route and now with their name on it.
- **It is a required value ADR-107 has just made expensive.** ADR-108 refused one for `num_ctx` on this exact reasoning one decision earlier; refusing it there and accepting it here would make two parts of one identity answer to opposite rules.
- **Nothing is concealed by the default.** The generator identity is on the 6b run row and reaches the deliverable, so which model wrote the prose is a question the artifact itself answers.

To close the disclosure gap properly rather than by appeal: **the arrangement gate's message names the model the next invocation will generate under.** That is an amendment to ADR-107's message, marked above, and it adds a line rather than changing what the gate wants — the operator is still told the one missing value and the single next action, and is still told a value rather than a stage, which is ADR-098's rule. It is disclosure before the call is spent rather than provenance read afterwards.

## Consequences

**`Profile` gains a seventh key, and it is the first one whose unset state means "a default applies".** Every existing key ships unset meaning either *no threshold* (`relevanceScoreFloor`, `degenerateOutputConfidenceFloor`) or *gate shut* (`seedFolder`, `boilerplateDocumentFrequencyFloor`, `embeddingModel`, `arrangementApproved`). A third meaning now exists, and each key's javadoc has to say which one it carries — a reader who assumes an unset key gates something will be wrong about this one.

**The gate-message amendment is #179's, and its criteria say so.** That ticket mints the generation run and opens its gate, and it gained an eighth criterion for this line — the message itself is shipped 6a code, so the work is an edit to what stage 6a already writes rather than something new beside it.

**`README.md`'s value table gains a row when `generationModel` lands in `Profile`, and the invocation count does not move.** The row is not optional: `docs/check-claims.mjs`'s *"the profile keys README names"* check reads that table's first column and compares it against the `Profile` record's components **both ways**, so a key documented nowhere fails the build as loudly as a documented key that does not exist. The table is not a list of stops — it already carries `degenerateOutputConfidenceFloor`, which README itself calls optional, and the check's own pass message says *"including the one that is not a stop"*. That optional key is the precedent for this one. The invocation count is a separate claim over a separate table, and this decision adds no stop, so it does not move.

**`CONTEXT.md` gains no term, and ADR-084's *"`CONTEXT.md` gains nothing"* stands.** "Model" remains the vocabulary of the tools rather than of the domain. One repair was owed and made: the **Instrument** entry's parenthetical claimed *"model" is the embedder's name*, which stopped being true the moment a second model entered the design. That is a correction to an existing entry, not a new one.

**Changing the generation model does not spend the arrangement approval.** It mints a new 6b run, not a new 6a one, so `arrangementApproved` still names the arrangement it approved and the gate stays open. ADR-107 listed what closes that gate — a new embedding model, a changed relevance floor, a re-walk — and this joins none of them.

**Re-generating under a different model is cheap and leaves both deliverables.** Two model names are two 6b run ids, two directories under ADR-103's one-tree-per-run-id rule, and no shared row between them. Comparing generation models is therefore something an operator can do without this project building anything for it, which is the strongest reason the choice did not need to be made up front.

**The default will age.** A tag named today is not the best local model in a year, and nothing in the tree notices. What makes it survivable is that the cost of being wrong is one re-run, and the digest in the identity means nobody is ever confused about which weights produced which tree.
