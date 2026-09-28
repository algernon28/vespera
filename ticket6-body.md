# Question

What is the actual measured size of this wave? The plan estimated about −1,000 lines in `pipeline`, partly moved rather than deleted. Replace that estimate with the measured diff.

## Notes

This decision requires running the actual build to measure the diff:

1. Run `./mvnw clean test` to get a baseline
2. Implement the moves from this wave
3. Run `./mvnw clean test` again
4. Compare the results to get the actual measured diff

What needs to be measured:
- Lines moved from pipeline to their new modules
- Lines deleted from pipeline
- Lines added to destination modules
- Net change in pipeline module size
- Overall project size change

## Blocking

This decision depends on implementing the actual moves, which requires decisions from:
- Ticket 1: Callback shape
- Ticket 2: ExtractionCircuitBreaker movement
- Ticket 3: Exception naming
- Ticket 4: Profile key derivation
- Ticket 5: ADR-040 amendment
- Ticket 8: Test porting

## Part of

#339