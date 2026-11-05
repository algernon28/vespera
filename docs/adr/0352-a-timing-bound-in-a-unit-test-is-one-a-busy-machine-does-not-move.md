# ADR-352 — A timing bound in a unit test is one a busy machine does not move

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Decides**: [ADR-238](0238-a-bucket-row-already-in-the-component-of-the-pages-lesser-members-in-that-bucket-is-passed-over-with-one-question.md)'s *What this does not decide*, *"Whether a wall-clock bound is the right guard for a cost no statement shows"*: it is not; the bound is on the processor time of the thread that pays the cost (§1). ADR-238's Tests line, *"in less than 6 s … the bound is a wall clock"*, no longer holds: the bound is 3 s of that thread's processor time. ADR-238 carries a note at its head.
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md)'s Tests, in two places and no others: `StageTwoWaitsForAConverterStillStartingTest`'s *"a server that closes every connection so for 300 ms, longer than the JDK client's own retries of a closed connection take, and then answers"* is a server that closes every connection until the start check has written that it waits (§2); and `SystemProcessRunnerTest`'s *"a command running past its limit is killed and counts as exit -1 with what it printed"* is held over limits doubled from half a second until the command printed (§3). What each test claims stands. ADR-323 carries a note at its head.
- **Keeps**: every decision of ADR-238 and ADR-323, and every line of `src/main`. No run id moves.
- **Settles**: [#698](https://github.com/algernon28/vespera/issues/698).

## Context

Three unit tests held a bound on the clock on the wall, and each failed where other builds kept the machine's processors busy, and passed alone:

- **`RedundancyResolutionTest.aFamilyOfCopiesIsResolvedInTimeThatDoesNotGrowWithTheSquareOfItsSize`** resolves 4,000 copies of one text and claims it takes less than 6 s, measured on the wall. It took 18,137 ms beside two other builds. ADR-238 measured the pass at 17.3 s alone before it and 1.6 s after, and left open whether a clock on the wall is the right guard.
- **`StageTwoWaitsForAConverterStillStartingTest.aConverterClosingConnectionsWhileItStartsIsWaitedFor`** starts a loopback converter that closes every connection without a status line for 300 ms from its creation and answers 200 after, and claims the start check waited for it, writing one warning. The production client's first check retries a closed connection on its own; where the retries took longer than the 300 ms, the last of them was answered, the first check passed, and nothing was waited for.
- **`SystemProcessRunnerTest.aCommandPastItsLimitIsKilledAndCountsAsMinusOne`** gives a Java that prints one line and then sleeps a limit of half a second and claims the line is the output of the command killed. Where the Java took longer than half a second to print, the output was empty.

None of the three shares state with another test: each builds its converter, its runner, its folder and its fixture of its own, and the appender the second reads is attached and detached around each test.

### Measured

On a machine of 16 logical processors, Windows 11, Java 26, each test method run on its own in a loop in one JVM, with a program beside it keeping 128 threads busy, eight for each processor, and without it:

| Test | Alone | 128 threads, before | 128 threads, after |
| --- | --- | --- | --- |
| the copies | passes | 6 of 6 fail, 6,782 to 8,382 ms on the wall | 0 of 6 fail |
| the converter closing connections | 0 of 30 fail | 100 of 100 fail, no warning | 0 of 100 fail |
| the command killed at its limit | passes | 40 of 40 fail, no output | 0 of 30 fail |

- **The copies' processor time.** The thread that resolves spent 484 to 640 ms of processor time alone, and 718 to 953 ms beside the 128 threads, where its time on the wall was 6.8 to 8.4 s. With ADR-238's question taken out of the pair loop, and ADR-239's read kept, it spent 4,453 and 4,703 ms alone, and 9,000 and 9,078 ms beside the 128 threads.
- **A Java's first line.** Started from the tests' Java with the tests' class path, `SleepsUntilKilled` printed its line 75 to 105 ms after it was started alone, 233 to 557 ms beside 16 busy threads, 794 to 2,066 ms beside 32, and 2,988 to 6,430 ms beside 128, in 20 or 30 starts each.

## Decision

### 1. A bound on a cost is taken on the processor time of the thread that pays it

**Where a unit test bounds how long code takes, to tell an algorithm from a slower one, and the code runs on the test's own thread, the bound is on that thread's processor time** (`ThreadMXBean.getCurrentThreadCpuTime`), and the test claims first that the machine can measure it. Time the thread spends waiting for a processor other programs hold is not on it, and a loop that does more work still spends its time on it.

The copies' bound is **3 s** of that time: three times the most the pass took with ADR-238 beside 128 busy threads, and under the least it took without ADR-238's question, alone. The 6 s on the wall did not tell the pass without that question from the pass with it on this machine; 3 s of processor time does, alone and beside the load.

### 2. A fixture's state changes on an event of the code under test, not at a time on the clock

**Where a test's fixture must behave one way until the code under test has reached a point and another way after, it changes on that point, and not after a time measured from its creation.** The converter that closes connections closes every one until the start check has written its warning that it waits, and answers 200 to every one after. Every retry of the first check meets a closed connection, however long the machine makes them take; the wait is what hears the converter answer. The test claims, beside its three claims, that the converter answered a connection, so that the start went on because the converter answered.

### 3. A limit a command must outlast to print is doubled until it does

**Where a test needs a command started by the code under test to have printed before a limit runs out, and the code starts the command itself, the test gives a first limit and doubles it until the command printed, up to a longest limit, and holds every run to what the code owes it.** The command killed at its limit is given half a second, then one second, two, four and so on up to 32 s, until its output holds its line. Every run is claimed to count as exit -1, to have for output its line or, where it was killed before it printed, nothing, and to have returned no sooner than its limit. The last is claimed to have its line for output. Alone, the first run is the last, as before. The output is read past the notes a Java launcher prints first of the options it picked up from the environment (`Picked up JAVA_TOOL_OPTIONS: …`, `NOTE: Picked up JDK_JAVA_OPTIONS: …`): the runner merges the command's errors into its output, and the command inherits the environment of the machine the tests run on, so the claim holds whatever options that machine sets.

After each run the test waits, up to 30 s, for every process started since the run began to be gone, so that a killed command no longer holds the output file in the test's folder when that folder is deleted. The test of a command that leaves a child holding its output does the same for that child: it kills it and waits, up to 30 s, for it to be gone, as its `@TempDir` could not be deleted beside load while the child still held the output file.

## Decided here

1. **§1: processor time, and not a count of the work.** A count of the pair loop's questions would be exact, and would ask `RedundancyResolution` for a counter no stage uses. Processor time keeps ADR-238's claim, a time, on the clock the load does not move.
2. **§1: 3 s, and not 6 s.** 6 s of processor time would hold beside load as well, and would not tell apart the pass with ADR-239's read and without ADR-238's question, measured at 4.5 s alone.
3. **§3: doubling, and not one long limit.** One limit long enough for the Java beside 128 busy threads, 8 s or more, would be spent whole by every run, as the command sleeps until it is killed; doubling spends half a second where the machine is idle.
4. **§3: 32 s.** The slowest first line measured beside 128 busy threads was 6.4 s; 32 s is five times that.

## Consequences

- **The three tests pass beside 128 busy threads** in the loops measured above, and claim what they claimed before.
- **The copies' bound is tighter**: a pass without ADR-238's question fails it alone.
- **A test that adds a timing bound** holds it on the thread's processor time, on an event of the code, or on a limit doubled until the event, as §1 to §3 have it, or says why none applies.
- **Nothing under `src/main` changes, and no run id moves.**

## What this does not decide

- The other tests that take a time on the wall, which no failure beside load has named. Among them is `SystemProcessRunnerTest.aCommandWhoseChildKeepsItsOutputOpenStillReturns`'s claim that the command returned in less than its 10 s limit, measured on the wall, which took 8.2 s beside 128 busy threads and has not failed.
- `ModelPullTest`'s bounds, held on a loaded machine since ADR-329 by bounds of their own.

## Tests

- `similarity.RedundancyResolutionTest.aFamilyOfCopiesIsResolvedInTimeThatDoesNotGrowWithTheSquareOfItsSize`: 4,000 copies resolve to one, in less than 3 s of the processor time of the thread that resolves, which the machine is claimed able to measure (§1).
- `pipeline.StageTwoWaitsForAConverterStillStartingTest.aConverterClosingConnectionsWhileItStartsIsWaitedFor`: the converter closes every connection until the start check's warning is written, and the test claims one warning, a connection closed, and a connection answered (§2).
- `pipeline.SystemProcessRunnerTest.aCommandPastItsLimitIsKilledAndCountsAsMinusOne`: the limits doubled from half a second to at most 32 s, every run killed at its limit and not before, counted as -1, its output past the launcher's notes of options picked up its line or nothing, and the last run's output its line (§3). With the runner changed to give a killed command no output, it fails at the last run, 32 s. With `JAVA_TOOL_OPTIONS` set, it holds.
- `pipeline.SystemProcessRunnerTest.aCommandWhoseChildKeepsItsOutputOpenStillReturns`: the child the command leaves behind is killed and waited for, up to 30 s, to be gone before the test's folder is deleted (§3). Its claim that the command returned in less than 10 s stays a bound on the wall, 8.2 s seen beside 128 busy threads, which this record does not move.
