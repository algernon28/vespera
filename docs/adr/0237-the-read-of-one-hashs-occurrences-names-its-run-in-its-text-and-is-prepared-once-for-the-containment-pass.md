# ADR-237 — The read of one hash's occurrences names its run in its text, and is prepared once for the containment pass

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11, on the operator's word (*"do R20 with its ADR-225 amendment"*). How the statement is kept for the pass (§2) is the session's call.
- **Amends**: [ADR-225](0225-stage-4b-reads-its-candidate-pairs-and-containment-candidates-a-thousand-at-a-time-and-shingle-by-hash-ends-in-the-occurrence.md) §2, in *"The run is a bound value, as ADR-221 §2 decides"* and in the statement it shows, whose `run_id = ?` is now the run written into the text: the read, its order, its pages of 1,000 and the merge are unchanged. [ADR-221](0221-shingle-by-hash-is-an-index-on-the-rows-of-the-run-in-hand-and-an-earlier-runs-rows-stay.md) §2, *"Containment retrieval keeps the run as a bound value"*: it no longer does, and the remedy that section names for a SQLite that stopped matching bound values, *"the run written into the statement's text"*, is taken for the cost measured below. Each carries a note at its head.
- **Keeps**: ADR-221 §4, that the run id is the only value joined into a statement's text and only once it is known to be of the minted form; that check is now one method, `ShingleHashIndex.writtenInto`, read by the index's build and by this read alike.
- **Settles**: review register finding R20.
- **Rests on**: `similarity.RedundancyResolutionBoundsItsCandidatesTest.theReadOfOneHashsOccurrencesIsPreparedOnceForThePass`, which found the statement prepared 416 times, with the run bound, in one resolution at `1e89afe`; a throwaway probe outside the repository (Measured). No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

Containment retrieval makes one read for each of an occurrence's rarest hashes, about 32, and more where a hash has more than 1,000 occurrences (ADR-225 §2). Each read was a `JdbcTemplate` query, so a statement was prepared for each, with `run_id = ?`. SQLite matches a bound value to a partial index's `WHERE` only by planning the statement with the value bound (ADR-221 §2), so a statement whose bound value a partial index depends on is prepared again when it is bound. Over a corpus that is two compilations for each read, for every signed occurrence.

## Measured

**Method.** One throwaway probe on 2026-10-11: Java 26 and the sqlite-jdbc the pom carries (3.53.2.1, SQLite 3.53.2), the shipped `schema.sql`, `shingle` holding 2,000,000 rows of two runs (20,000 occurrences each, 50 hashes an occurrence), `shingle_by_hash` built for one of them by the statement `ShingleHashIndex` ships. The read was made 50,000 times in each of four forms, two rounds each, on a Linux container's disk.

| Form | µs a read, round 1 | round 2 |
| --- | ---: | ---: |
| Prepared for each read, the run bound (as shipped) | 29.9 | 23.7 |
| Prepared for each read, the run in the text | 22.0 | 19.5 |
| One statement kept, the run bound | 17.4 | 19.0 |
| One statement kept, the run in the text | 5.2 | 7.7 |

Keeping one statement with the run bound saves little, since binding the run has it prepared again. With the run in the text and the statement kept, a read costs about a quarter of what it did. **Both plans are the same**: `SEARCH shingle USING COVERING INDEX shingle_by_hash (shingle_parameter_identity=? AND shingle_hash=? AND occurrence_id>?)`.

## Decision

### 1. The run is written into the read's text

`SELECT DISTINCT occurrence_id FROM shingle WHERE run_id = '<the stage-2 run>' AND shingle_parameter_identity = ? AND shingle_hash = ? AND occurrence_id > ? ORDER BY occurrence_id LIMIT 1000`, the run as `ShingleHashIndex.writtenInto` gives it, which refuses an id not of the form `RunId.of` mints, as the index's build already did (ADR-221 §4). The text then matches the index's `WHERE run_id = '<the run>'` character for character, which is what SQLite's documentation of partial indexes asks (ADR-221 §2's first bullet), so the plan no longer rests on the planner looking at bound values.

### 2. The statement is prepared once for the containment pass where the pass is one transaction

`RedundancyResolution.OneHashsOccurrences` is made for the pass and closed at its end. Where the connection it gets is the transaction's, as stage 4b's step runs the resolution (`TaskletSteps.taskletStep`), it prepares the statement once on that connection and keeps it; every read binds the granularity, the hash and the occurrence to go on from, reads its page whole and closes the result. Where there is no transaction, each read prepares the statement on a connection it gives back at once, as before: a statement kept outside a transaction would hold a pool's one connection while the merge asks the database about the candidates (ADR-223 §6's reason). *Put and not taken*: a statement kept for each occurrence checked, which would prepare it once for each signed occurrence; and keeping it on a connection held outside a transaction.

## Consequences

- In stage 4b's step, the read of one hash's occurrences is compiled once a containment pass, where it was compiled twice a read.
- `EveryStatementThatSortsIsRecordedTest` reads a run written into a statement's text as the index's run in its second state and as a run no index is built for in its first, and counts as before: the read sorts without the index and not with it.
- **Which run ids move**: the change touches `similarity`, which the branch that carries it already moves (`4f0f242`), so stages 2 to 6b are minted once in that build and this record adds no move to it. On its own it would move them. The candidates read, and so every verdict, are unchanged.

## Tests

- `similarity.RedundancyResolutionBoundsItsCandidatesTest`, which now resolves in one transaction as the step does: the read is prepared once for the pass, with the run in its text; and, as before, what is removed and that nothing is sorted.
- `similarity.ShingleIndexesInTheSchemaTest.containmentRetrievalNeedsTheIndexOnTheHash` and `pipeline.ShingleHashIndexInvocationTest` plan the read as it is now sent.
- `EveryStatementThatSortsIsRecordedTest`, both states.

## What this does not measure

- The whole of stage 4b before and after: the figures are of the read alone.
- A spinning disk, or Windows.
