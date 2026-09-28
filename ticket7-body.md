# Question

Which of the three open tickets lands first in the codebase?
- #317 and #318: Changes to `ClusterSynthesis`'s prompt, reply allowance and answer parsing
- #319: Fixes stage 2's `afterStep` chain when docling-serve is down
- #305: Docling segfaults during seed extraction (may touch `extraction`'s Docling client)

The tickets are owned by another session, and the ADR should say which lands first. Otherwise each merge rewrites the other's diff.

## Notes

Sequencing analysis:

**Ticket #319 (highest priority):**
- Fixes stage 2's `afterStep` chain when docling-serve is down
- The chain reaches `extractorIdentity` through `ExtractionFaultRecorder` and `StageRuns.extraction()`
- Scope 4 moves the bean behind it
- This is critical for stage 2 stability

**Ticket #317/#318 (medium priority):**
- Changes `ClusterSynthesis`'s prompt, reply allowance and answer parsing
- Scope 1 moves the loop that calls it
- These affect stage 6b's cluster synthesis

**Ticket #305 (lowest priority):**
- Docling segfaults during seed extraction
- May touch `extraction`'s Docling client
- This is a sidecar issue, not core architecture

## Blocking

Ticket 1: The callback shape depends on #319 and #317/#318
Ticket 6: Measured size depends on #317/#318 and #319
Ticket 8: Test porting depends on sequencing

## Part of

#339