# ADR-115 — A re-walk that observed nothing new is discarded, and a run is continued under its own id

- **Date**: 2026-09-14
- **Status**: accepted
- **Amends**: [ADR-048](0048-walk-and-run-identity.md) — it supplies the *"continuation vs. minting rule"* that record's reconstituted summary claims and its text does not carry, for both identities. Its two identities, its four hashed inputs and its content-derived/surrogate split are untouched.
- **Amends**: [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md) — its minting rule only. *"`WalkRecorder` mints a new `WalkId` only when no unfinished walk exists for the root"* still governs what happens when a traversal **starts**; this adds what happens when one **finishes**. Its resume rule, its checkpoint granularity and its never-a-fraction-of-the-archive rule are untouched.
- **Amends**: [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) — one clause of one consequence. *"a new embedding model, a changed relevance floor or **a re-walk** all mint a new 6a run id"* loses its third item: a re-walk that observed nothing new mints nothing. Everything else that record decided stands, and its fifth invocation becomes reachable for the first time.
- **Amends**: [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) — its **supporting premise only**, not its rule. *"because each invocation mints a new walk, a re-run gets a distinct run id"* is false from today. The rule it supports — a fresh row set under this run's own id, never a rewrite of an earlier run's — is unchanged, and is the reason the run half below is safe.
- **Rests on**: [ADR-056](0056-excludes-nothing-is-checked-by-reconciliation-at-finish.md) (the reconciliation that has to pass before two observations can be compared at all), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (the one stage that already decided how it resumes), [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md) and [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md) (the two expensive artifacts keyed outside the run), [ADR-099](0099-a-stages-upstream-run-is-looked-up-by-stage-and-walk-not-recomputed-and-two-candidates-stop-the-run.md) (the ambiguity rule this leaves alone).

## Context

### Measured: nothing is ever recognised as already done

Three invocations of `vespera run <root>` over one corpus, same files, same profile, same configuration between them:

```
after invocation 1   walks=2  occurrences=2  runs=7
after invocation 2   walks=4  occurrences=4  runs=14
after invocation 3   walks=6  occurrences=6  runs=21
```

Two walks per invocation because the seed folder is walked under its own id (ADR-064). Verdict rows are zero only because that fixture removes nothing; they are written per run against occurrences of that walk, so they duplicate the same way on a corpus that has anything cut from it.

Linear in the number of invocations, with nothing reused. Set that against what `RunId`'s own javadoc says content-derived identity is for:

> two runs that would produce identical verdicts have identical ids, so re-running a stage nothing has changed for is recognisable as such rather than a second opinion

**That property holds for no stage in this system.** A run id hashes the walk it read (ADR-048); `WalkRecorder` resumes only an *unfinished* walk (ADR-055); so every invocation over a root whose last walk finished mints a new walk, and every run downstream of it a fresh id. Content-derived identity is doing no work anywhere.

### How it surfaced: a gate nobody can open

ADR-107 has the operator read `arrangement.html`, copy the first twelve characters of the 6a run id into `arrangementApproved`, and invoke again. `ArrangementGate.approvedArrangement` resolves that prefix against arrangements *of this walk*. The approval was written under walk *N*; the invocation that reads it is looking at walk *N+1*, where that id does not exist. The gate reports itself shut — indistinguishable from a typo, and unopenable by any value the operator could type.

One corpus, two invocations, nothing changed between them:

```
after invocation 1:
  walk   1  root=<root>  finished=1
  run    56edbd49b9bd...  stage=arrangement  walk_id=1

after invocation 2:
  walk   3  root=<root>  finished=1
  run    56edbd49b9bd...  stage=arrangement  walk_id=1
  run    8ddb34d23847...  stage=arrangement  walk_id=3
```

ADR-107 saw the mechanism and priced it — *"a new embedding model, a changed relevance floor or a re-walk all mint a new 6a run id. That is the intended cost."* What it did not account for is that **every** invocation re-walks. The intended cost was "occasionally re-approve"; the actual cost is "the approval is void before it can be read", and the five-invocation path that record describes has no fifth invocation.

### Two directions were eliminated before this record, and the reasoning is kept here

**Take the walk out of the 6a run id.** Impossible as stated. `ArrangementRun`'s upstream is the scoring run and `RunId.of` hashes upstream ids, so the arrangement id still varies through an upstream that still carries the walk. It works only if the walk comes out of *every* run id, which abolishes ADR-048 rather than amending it.

**Resolve the approval against any walk of this root.** It reintroduces exactly the hazard the gate exists for: the generation run would read walk *N+1* while writing over an arrangement derived from walk *N*, and a file changed between the two would be generated over with nobody told. ADR-107 names that outcome as the alternative it rejected — *"an approval that outlives what it approved."*

### The objection this record has to answer

ADR-048 made a walk's identity a surrogate key deliberately, and `RunId`'s javadoc carries the reason verbatim:

> A walk's identity is a surrogate key for the opposite reason — an observation of a filesystem is not determined by anything the tool holds.

If a walk can take a previous walk's id because what it saw matched, is that reconciliation, or is it derivation with an extra step? The Decision answers it directly rather than asserting past it, and the answer turns on *what gets deleted*, not on what gets computed.

### And an insert that has never yet been able to fire

`Ledger.startRun` does a bare `INSERT` into a table whose `id` is a `TEXT PRIMARY KEY`. There is no `ON CONFLICT`, no read-before-insert, nothing that tells "mint this run" apart from "this run exists, carry on under it". `ArrangementRun` and `GenerationRun` both say so in their own javadoc — *"`Ledger.startRun` — which has no continuation clause — is never called twice with one content-derived id"* — and lean on `@JobScope` to guarantee it. Within one invocation that holds. Across invocations, **walk churn is the only reason it holds**, so stopping the churn arms it.

ADR-048's summary line claims *"Continuation vs. minting rule defined"*; the reconstituted text carries no such clause and the code has none. Whether it was decided and lost or never decided, it is undecided now.

It is not only the `run` row. Every table keyed `(occurrence_id, run_id)` — `content_hash`, `superseded_by`, `detected_format`, `extraction_metric`, `minhash_signature`, `redundant_with`, `relevance_score`, `document_cluster` — would collide on a second execution under one run id; `verdict` and `shingle`, which carry no per-run natural key, would silently **double** instead. The first stage that needs the rule is the one this blocks, but the rule was never particular to it.

## Decision

### A walk id is still minted surrogately. A traversal that observed nothing new is discarded

Nothing derives a walk id from anything. A traversal begins by minting one, exactly as ADR-055 says; it visits every entry, records every occurrence and every anomaly under that fresh id, and ADR-056's excludes-nothing reconciliation runs against it as its last step.

**Only then**, holding two complete observations, census compares the walk that just finished against the previous finished walk over the same root. If they are the same observation, **the traversal that just finished is discarded**: its occurrence rows, its anomaly rows and its own walk row are deleted, and census returns the earlier walk's id.

**This is what answers the surrogate-key objection.** ADR-048's premise forbids *deriving* an identity from something the tool holds before it observes, because a filesystem is outside the tool and cannot be predicted. Nothing here predicts anything and nothing derives an id: every walk id in this system is still minted the moment a traversal starts, out of nothing but a sequence. What is added is a **deletion**, taken after the fact, of a row set proven redundant against another row set. At the moment that comparison is made the observation is no longer outside the tool — it is rows in the database, and comparing rows to rows is not the act ADR-048 ruled out.

The distinction is not a word game, and the test of it is what a mistake would look like. A wrong derivation asserts two things are the same because their *inputs* matched, and the assertion is unfalsifiable after the fact, because the thing it claimed about was never recorded. A wrong discard asserts two things are the same because their *outputs* matched, having produced both, and both are sitting in the database to be diffed by anyone who doubts it. One is an assumption; the other is a claim anyone can check — which is the same standard `CONTEXT.md` sets for "excludes nothing" and ADR-056 built the machinery for.

Two precedents make this the house style rather than a novelty:

- **ADR-055 already made a walk id conditional on ledger state.** *"Mints a new `WalkId` only when no unfinished walk exists for the root"* is already a rule in which something the tool holds decides which id a traversal runs under. A walk id has not been purely surrogate since that record; this extends the same conditioning from *is there unfinished work?* to *is this the same observation?*
- **ADR-056 already settles a claim about an observation by counting rows rather than trusting the counters that produced them**, precisely because two independent sources are stronger than one. The comparison here is that shape one step further out.

**`CONTEXT.md` is the plainer argument.** A walk is *"one observation of a filesystem, producing file occurrences"* — an observation, not an execution of a traversal. Two traversals that saw the same thing were always one observation by that definition; until now the code recorded them as two.

### What "the same observation" has to cover: everything the walk recorded

The comparison is total, not a chosen subset:

- **Every occurrence row** — path relative to the root (ADR-051), size in bytes, last-modified time, creation time.
- **Every anomaly row** — path rendering, kind, detail.
- **`directories_entered`.**

`entries_seen` is deliberately *not* compared as a fourth term. ADR-056 fixes the identity `entries_seen == occurrences + anomalies + directories_entered - 1` and checks it at finish on both walks, so once the two row sets and `directories_entered` agree, `entries_seen` follows. Comparing it as well would state one fact twice, which is how two statements of one thing drift apart.

**Walk anomalies are in the comparison, and that was the open question.** An occurrence set can match while the observation differs: add a symlink, and the file occurrences are identical and a new `walk_anomaly` row is not. Discarding that traversal would throw away the only record of the new entry, and a walk anomaly is not incidental — ADR-053's kinds are observations rather than policy, which is what makes the first walk of an archive a measurement. The rule is therefore the one that needs no exception list: **a walk recorded it, so it counts.** Whatever a future kind of row adds to what a walk records joins the comparison by that rule, rather than by somebody remembering to add it.

**Only the immediately preceding finished walk is compared.** A corpus that changed and changed back across three invocations gets a third walk, not the first one back. Two reasons, and neither is cost: comparing against every prior walk reintroduces exactly the "which of two candidates did you mean" ambiguity ADR-099 stops the run over; and an operator who approved the arrangement of the *intermediate* state approved a different shape of the archive, so leaving that approval expired is the honest outcome rather than a loss. A corpus that changed and changed back without any invocation in between was never observed differently by anything, and is one observation on the same terms as any other.

**A walk resumed under ADR-055 is compared at the moment it finishes, like any other.** Its rows and its cumulative counts span every session it took, which is what the comparison reads, so resumption needs no special case.

### A run is continued under its own id, and a finished run does no work

`run` gains a `finished` column, the one `walk` has carried since ADR-055 and for the same reason: a partial thing that looks complete is the failure worth spending a column on.

**`Ledger.startRun` becomes mint-or-continue.** An id already present is not a fault and not a second run — it is this run, already recorded. The insert becomes idempotent, for `run` and for `run_upstream` alike.

**The rule is general to `startRun`, not particular to stages that can be repaired.** A run id is a total function of the four things hashed into it, and those four things are exactly the columns of the row: stage, implementation version, configuration consumed, walk. A re-derived id therefore names a row that agrees with what the caller was about to write in every column it has. There is nothing for a second row to say, and nothing for the caller to choose. Putting the rule in some callers and not others would leave it to be re-decided per stage, which is how one rule becomes fifteen.

What a stage then does about it is the second half, and it is separable:

- **A finished run: the step does no work and writes no rows.** This is ADR-048's stated purpose in its literal form — re-running a stage nothing has changed for is now recognisable as such, and what the system does about recognising it is nothing.
- **A run that exists and is unfinished: the step discards its own rows under that id and does the work again.** ADR-077 is untouched: it forbids rewriting *an earlier run's* rows, and these are this run's own. It is affordable because **the two expensive artifacts in this system are already keyed outside the run** — `extraction_cache` by content hash plus extractor identity (ADR-070), `vector` by chunk key plus embedder identity (ADR-085) — so redoing a stage costs recomputation over material already cached, and never a second call to a sidecar.
- **Stage 6b is the exception, and ADR-111 already wrote it.** Generation's output is the one expensive artifact that *is* keyed by the run, so 6b never discards its own rows: it skips every cluster carrying a `synthesis_doc` row, re-attempts every cluster carrying a `cluster_fault` row, and deletes the fault row when the re-attempt succeeds. That record's repair pass is the first instance of run continuation in this project; this one makes it an instance of a rule rather than a local arrangement.
- **A 6b run is finished only when every cluster of the approved arrangement carries a `synthesis_doc` row.** A run that ended on the consecutive-fault breaker, died on hour nine, or completed with faults is unfinished — which is exactly when ADR-111 says a repair pass is for, and what stops the finished rule above from skipping a faulted cluster forever.

## Consequences

**ADR-107's fifth invocation exists.** An operator who reads the page, copies twelve characters into `arrangementApproved` and invokes again reaches an open gate, because the arrangement they named is still an arrangement of the walk the next invocation reads. Nothing in ADR-107's mechanism changes; it stops being unreachable.

**An approval still expires the moment the archive changes.** A changed corpus is a different observation, mints its own walk, and every run id downstream of it moves — so the approval names nothing and the gate shuts. ADR-107's *"an operator who re-arranges must re-approve"* survives whole and becomes the proportionate cost it was written as, instead of a total loss.

**A second invocation over an unchanged corpus is nearly free, and that side effect is worth having.** Every stage's run is already finished, so fifteen steps resolve to fifteen identity derivations and no work. At hundreds of gigabytes that is the difference between re-invoking to advance one gate and re-curating the archive to advance one gate.

**A skipped step writes no report.** The five HTML pages and `arrangement.html` are outputs of the run that produced the rows behind them, so a second invocation over an unchanged corpus leaves them exactly as they stand. That is correct, and slightly better than before: ADR-075's `refreshedAt` now dates the measurement rather than the last time the tool was started. The cost is that a report file an operator deleted is not written again by re-invoking; recovering it means changing something the run's identity is derived from, or deleting that run's rows. Nobody has asked for it, and inventing a mechanism here would be inventing a requirement.

**`verdict` gains a natural key.** Under the rule above no table is ever written twice under one run id — a finished run writes nothing, an unfinished one deletes its own rows first — so the rule is the guard rather than a constraint being the guard. `verdict` nonetheless gains `UNIQUE (occurrence_id, run_id, kind)`, because a second identical verdict under one run is the same verdict rather than two, and the one table that could double a corpus silently should not be the one table that cannot say so. **`shingle` gains nothing**: a document's own shingle set legitimately repeats a hash, and its schema comment already explains that a uniqueness constraint there would throw away the repeat count stage 3 counts.

**`ledger`'s schema version moves.** `schema.sql` is replayed with `CREATE TABLE IF NOT EXISTS`, which never adds a column to a table that already exists, so a database created before `run.finished` will not grow one. That is precisely the case ADR-059's per-module version catches, and its manual path — delete the module's tables and re-run census — applies.

**A walk no longer records how many times it was observed.** Nothing needs it, and the discarded traversal's counts equalled the surviving walk's by construction, so no measurement is lost. The log line census writes when it discards a traversal is where that fact lives, which is ADR-093's discriminator applied as written: a traversal that turned out to be redundant is a fact about the pipeline's own execution, not about the corpus.

**Two javadoc paragraphs become false and have to move with the code.** `ArrangementRun` and `GenerationRun` each say `startRun` *"has no continuation clause"*, and each justify `@JobScope` by it. `@JobScope` stays — one run bean per invocation is still right, and minting the row only once the gate is open is ADR-080's rule — but the reason given for it has to change, or a future reader trusts a comment that contradicts the method above it. That is the exact failure ADR-077 was written to stop.

**Every test in the tree that invokes twice now exercises a path nothing exercised before.** Those tests invoke twice today and each invocation was independent of the last; from here the second one meets the first one's rows. Establishing which of them were passing for a reason that no longer holds is work this record creates and does not do.

**`relevance_label` is unaffected, and its reasoning gets quietly better.** Its schema comment argues for path keying partly from this defect — *"An occurrence id is per-walk by design — ADR-055 resumes only an unfinished walk, so every ordinary run mints a new one — and a label keyed by it joins to nothing the next run scores."* That premise weakens and the decision does not change, because a corpus that *has* changed still mints a new walk and an answer about a document still has to survive it. Path keying was right for the durable reason, not only for this one.

## What this does not decide

**Whether a comparison this cheap stays cheap.** It reads one walk's rows against another's, which at corpus scale is millions of rows rather than a handful. Nothing here measures it, and "measure rather than argue" applies: if it costs, the answer is two ordered queries streamed in step, not a change to the rule. The rule is decided; the algorithm is not.

**Whether a stage should ever be forced to run again.** There is no "do it anyway" flag, and adding one would be adding an input nobody has asked for. The way to make a stage run again is to change something its identity is derived from, which is the same lever every other decision in this project hands the operator.
