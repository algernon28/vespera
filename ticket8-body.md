# Question

What are the exact test files that need to be created or ported for each moved rule, and what specific changes are needed for each?

## Notes

**Ticket 1 (callback shape):**
- Need to create new tests in `synthesis` for `ClusterExemplars` callback
- Tests for the sealed result type (finished/incomplete/stopped)
- Tests for the integration with generation's loop

**Ticket 2 (ExtractionCircuitBreaker):**
- Move existing tests from `extraction` (already there, no changes needed?)
- Ensure thread-safety guarantees are maintained
- Update any references to verify the breaker works with the timeout streak

**Ticket 3 (ExceptionNaming):**
- Update `ExceptionNamingTest.java:58` to check new location if moved
- Ensure the exception is accessible where needed in extraction
- Update any tests that reference this exception

**Ticket 4 (Profile key derivation):**
- Create tests for the `profile` method or reflection approach
- Tests for deterministic key derivation from Profile components
- Integration tests to ensure `synthesis` can't read `profile`

**Ticket 5 (ADR-040 amendment):**
- No test changes needed for this decision itself

**Ticket 6 (Measured size):**
- Run `./mvnw clean test` to get baseline
- Implement all moves
- Run `./mvnw clean test` again
- Document the actual measured diff

**For `ExtractionItemProcessorTest`'s 18 tests:**
- Need to move from `extraction` to `extraction` (wait, that's confusing)
- Actually need to move from `pipeline` to `extraction` if classification and timeout streak move there
- Tests use a test-only constructor - need to port that constructor to extraction
- Update any references to pipeline-specific code

## What needs to be done:

1. **Identify all test files** that need to be moved
2. **Determine which test constructors** need to be ported
3. **Update test imports** to reflect new locations
4. **Ensure test coverage** is maintained
5. **Run all tests** to verify nothing is broken
6. **Document the exact changes** for the measured size calculation

## Blocking

- Ticket 1: Callback shape affects test structure
- Ticket 2: Breaker movement affects test updates
- Ticket 3: Exception location affects test assertions
- Ticket 4: Profile derivation method affects test approach
- Ticket 7: Sequencing determines order of test porting

## Part of

#339