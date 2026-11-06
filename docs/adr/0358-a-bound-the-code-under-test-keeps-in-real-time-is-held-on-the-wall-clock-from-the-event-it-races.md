# ADR-358 — A bound the code under test keeps in real time is held on the wall clock, from the event it races

- **Date**: 2026-11-06
- **Status**: accepted on 2026-11-06.
- **Decides**: both items of [ADR-352](0352-a-timing-bound-in-a-unit-test-is-one-a-busy-machine-does-not-move.md)'s *What this does not decide*: `ModelPullTest`'s bounds (§2 to §6), and `SystemProcessRunnerTest.aCommandWhoseChildKeepsItsOutputOpenStillReturns`'s claim that the command returned within its limit, on the wall (§7). [ADR-357](0357-the-unit-runs-http-client-selector-managers-run-on-platform-threads.md)'s *What this does not decide* left the first of them open in the same words, and it is decided here.
- **Amends**: ADR-352's Consequences, *"A test that adds a timing bound holds it on the thread's processor time, on an event of the code, or on a limit doubled until the event, as §1 to §3 have it, or says why none applies"*: a fourth way is added, the clock on the wall, for a bound the code under test keeps in real time (§1). ADR-352 §3's *"That test is given 60 s, more than its command's 10 s limit and the 30 s wait together"* and its Tests' *"Its claim that the command returned in less than 10 s stays a bound on the wall … which this record does not move"*: the command is given 60 s and the test 100 s (§7). ADR-352 carries a note at its head.
- **Amends**: [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md)'s Tests, in `ModelPullTest`'s bullets and no other: *"each within the bound and a margin"* is within the bound and a margin of when the stub read the pull's request, and no sooner than the bound after the pull was begun (§1, §2); *"a status that is not 2xx sent three quarters of the way into a bound of two seconds and its body then stopping, given up with that status kept no sooner than the bound after the request and within it and half of it"* is a status sent half way into a bound of ten seconds, given up no sooner than the bound after the pull was begun and within the bound and half of it after the stub read the request (§5); and the pull given up at the bound whose connection the stub finds closed is found so within the bound and a margin of the stub's read (§2). ADR-329 carries a note at its head.
- **Keeps**: every decision of ADR-329, ADR-352 §1 to §3 and ADR-357, and every line of `src/main`. No run id moves.

## Context

ADR-352 holds a timing bound in a unit test in one of three ways: on the processor time of the thread that pays a cost (§1), on an event of the code where a fixture must change (§2), or on a limit doubled until a command has printed (§3). It left two sets of bounds on the clock on the wall, which no failure beside load had then named.

- **`ModelPullTest`'s bounds.** A pull that has stopped moving is given up once `vespera.models.pull-stalled-at-most` has passed since it last moved (ADR-329 §6). The tests give a bound of 1 s to the pulls that stop moving or never move, 4 s to the two refusals whose body stops coming after the status, and 30 s, which no pull here reaches, to every other. Each test of a bound claimed it was given up no sooner than the bound and within the bound and a margin of 5 s, both timed from before `refusalOf` was called; the late refusal's stub sent its status 2 s after reading the request; and the tests a pull that never ends could hold had a timeout of 15 s, under the 30 s bound of four of them.
- **`SystemProcessRunnerTest.aCommandWhoseChildKeepsItsOutputOpenStillReturns`** gives a Java that starts a child holding its output, and exits, a limit of 10 s, and claims the runner returned in less than that, on the wall. ADR-352 saw it take 8.2 s beside 128 busy threads.

What each of these holds is a promise the code under test makes in real time: that it gives a pull up at the bound and not later, and that it returns when the command exits and not at the limit.

### Measured

On a machine of 16 logical processors, Windows 11, Java 26, with surefire's `-Djdk.internal.httpclient.tcp.selector.useVirtualThreads=never` (ADR-357), each test's timed call was made 20 times in a loop in a Java of its own, through the production `ModelPull`, `OllamaClient` and `SystemProcessRunner` and the tests' `StubOllama`, as ADR-352 measured: alone, and beside a program keeping 16, 32 and 128 threads busy. The overrun is the time past the bound, timed from before `refusalOf` was called, as the tests timed it.

| What is timed | Bound | Alone | 16 threads | 32 threads | 128 threads |
| --- | --- | --- | --- | --- | --- |
| lines that keep coming and say nothing new | 1 s | 463 ms | 1,346 ms | 5,873 ms | 23,452 ms |
| an answer kept open and silent | 1 s | 526 ms | 811 ms | 2,955 ms | 14,832 ms |
| no status at all | 1 s | 405 ms | 854 ms | 4,894 ms | 13,928 ms |
| until the stub found the client gone | 1 s | 767 ms | 4,205 ms | 4,683 ms | 18,700 ms |
| a refusal whose body stops coming | 4 s | 352 ms | 775 ms | 4,130 ms | 16,752 ms |
| the same, its status sent 2 s late | 4 s | 479 ms | 833 ms | 3,997 ms | 11,452 ms |
| a pull under the 30 s bound, the whole of it | 30 s | 458 ms | 2,408 ms | 4,568 ms | 15,272 ms |
| the command leaving a child, until it returned | 10 s | 230 ms | 977 ms | 3,562 ms | 10,225 ms |

The slowest overrun in each cell; the last two rows give the slowest time taken, not an overrun. Two things the table does not show:

- **The slowest run is the first of its Java.** Beside 128 busy threads, every scenario's slowest pull is the first its Java makes, while it loads and compiles the HTTP client, `ModelPull` and the JSON reader. Over runs 2 to 20 the slowest overruns are 2,056 ms until the stub found the client gone, 1,698 ms for the lines that say nothing new, 1,480 ms for the answer kept open, 1,226 ms for a refusal and 973 ms for a late one, and the slowest whole pull under the 30 s bound took 1,920 ms. The first late refusal beside 128 threads did not keep its 400: the stub read the request 13.4 s after the pull was begun, and its status, 2 s after that, came past the 4 s bound.
- **The overrun comes before the stub reads the request.** Timed from the stub's read of the pull's request, `StubOllama.Request.readAt`, the pull was given up at most 30 ms past the bound, at every load: 0 ms alone, 12 ms beside 16 threads, and at most 30 ms beside 128. What the load adds is the client's way to the stub, the two questions to `/api/show` and the connection, and the test's thread waiting for a processor once the pull has ended. The code's own give-up past its bound takes milliseconds.

Beside 128 busy threads, with the bounds below, `ModelPullTest` ran its 28 tests with none failing, in 86 s. `SystemProcessRunnerTest`'s claims held, and once its folder for the commands' output could not be deleted after the test of the command that leaves a child, the output file still held by a process once that child was gone.

## Decision

### 1. A bound the code under test keeps in real time is held on the clock on the wall

**Where a unit test bounds when code gives something up, returns or acts, and the bound is a promise the code makes in real time, the test holds it on the clock on the wall.** It claims the code acted **no sooner than the bound**, timed from before the code was called, and **within the bound and a margin**, timed from the event the code races where the fixture records it, and from before the code was called where it records none.

None of ADR-352's three ways applies to such a bound. The thread that waits for a bound to pass spends no processor time waiting, so its processor time (§1 there) does not tell a pull given up at the bound from one given up a minute later. What the code races is the absence of an event, a line that does not come, so a fixture changing on an event of the code (§2 there) has none to change on. The bound is the code's own, set by the test, so doubling it until something happens (§3 there) would hold another bound than the one set. A clock put into `src/main` in place of the system's would let the test move time, and would test another program than the one shipped.

The upper claim is timed from the event because a loaded machine delays the code's way to it by seconds and the code's act past the bound by milliseconds (Measured): timed from the test's start, a margin wide enough for the first is no claim on the second. The lower claim is timed from the start, before the event, so it holds whatever the delay.

### 2. The margin is measured, and in `ModelPullTest` it is timed from the stub's read

**The margin is at least five times the slowest overrun measured beside 128 busy threads, rounded up to whole seconds, timed as the claim times it.** Timed from the stub's read of the request, the slowest is 30 ms, which gives 1 s. **`MARGIN` stays 5 s**: a passing test ends at the bound, not at the margin, so the wider margin costs a passing run nothing, and it is no less than the late refusal's room (§5).

The claims timed from the stub's read are the three pulls that stop moving, the refusal whose body stops coming, the late refusal, and the connection the stub finds closed. `StubOllama` records, beside the time it read each request, the time an answer to a pull found its client gone, and the last of those claims is that this came within the bound and the margin of the pull's request being read.

### 3. One pull before the first test

**`ModelPullTest` makes one pull, ending in success, of a model a stub of its own does not serve, before its first test**, so that no test's pull is the first of its Java. The first pull's cost is the client's loading and compiling, and is the same for every test of the class; once made, the slowest overrun from the test's start beside 128 busy threads is 2.06 s (Measured), which §5's late refusal must leave room for.

### 4. `BOUND` and the bound no pull here reaches

**`BOUND` stays 1 s and `A_BOUND_NO_PULL_HERE_REACHES` 30 s.** The 1 s bound is held from below from the start and from above from the stub's read, so the load moves neither claim. The 30 s bound is one no pull here should reach: the slowest whole pull under it took 15.3 s beside 128 busy threads as the first of its Java, and 1.9 s after.

### 5. The late refusal

**`LATE_STATUS` stays a fixture timed on the clock, in exception to ADR-352 §2**: what it shows is that the status and the body share one bound counted from the request, not one bound each (ADR-329 §6), and the time that has really passed is what shows it. A fixture changing on an event of the code would show nothing of how long the code waited.

**`REFUSAL_BOUND` is 10 s and `LATE_STATUS` 5 s, half of it.** The test claims the pull was given up no sooner than the bound after it was begun, and within the bound and `LATE_STATUS` after the stub read the request; a fresh bound for the body would end no sooner than the bound and `LATE_STATUS` after the read. So `LATE_STATUS` is the claim's margin, and it is no less than `MARGIN`. The status must also come within the bound, or the pull ends as one Ollama sent no status for, which leaves the client the other 5 s to reach the stub, where it took at most 2.06 s once its Java had made a pull (§3). The two refusal tests take about 20 s together on a passing run.

### 6. A timeout means only that the pull never ended

**Each `@Timeout` of `ModelPullTest` is the test's largest bound, `MARGIN` and 10 s**, written from `long` constants so that the annotation's value is a constant of the compiler: 16 s for a test whose largest bound is `BOUND`, 25 s for `REFUSAL_BOUND`, and 45 s for `A_BOUND_NO_PULL_HERE_REACHES`. A pull that ended late fails the test's claim before its timeout, and a timeout is left to a pull that never ended.

### 7. The command that leaves a child

**The command that leaves a child holding its output is given 60 s**, five times the slowest return measured beside 128 busy threads, 10.2 s, rounded up, **and its test 100 s**: the limit, the 30 s wait for the child to be gone (ADR-352 §3) and 10 s. The test claims the runner returned within the limit, on the wall: the runner's promise is to return when the command exits and not at the limit, which the command's own start of a Java comes before, so a limit of five times that start is the claim's margin. The limit costs a passing run nothing, as the runner returns once the command has exited; it is the test's own, and nothing under `src/main` sets it.

## Decided here

1. **§1: the wall clock, timed from the event, and not a clock put into the code.** A clock of the test's own in `ModelPull` or `SystemProcessRunner` would move time for a program the jar does not ship.
2. **§2: from the stub's read, and not from the test's start, for the upper claim.** Timed from the start, the margin beside 128 busy threads would be five times 23.5 s, and the refusal tests would take minutes; timed from the read, the claim is on the code's give-up itself.
3. **§2: 5 s, and not 1 s.** Both hold; a passing test spends neither, and 5 s keeps the claim's room as it was and the late refusal's room no less than it.
4. **§3: one pull before the first test, and not a margin wide enough for the first pull.** The first pull's cost belongs to the Java and not to any one test, and a margin to cover it would hold no test's own pull.
5. **§5: 10 s and 5 s, and not a late status of 2 s.** 2 s left the client 2 s to reach the stub before the 4 s bound, which a loaded machine overran; 5 s leaves it more than twice the slowest measured once warm, and the two tests stay within 30 s of a passing run.
6. **§7: one limit of 60 s, and not a limit doubled.** The runner returns once the command has exited, so a passing run spends none of the limit, and a doubled one would add nothing.

## Consequences

- **A test that adds a timing bound** holds it on the thread's processor time, on an event of the code, on a limit doubled until the event, as ADR-352 §1 to §3 have it, or on the clock on the wall, as §1 has it for a bound the code keeps in real time, timed from the event it races where the fixture records one; or says why none applies.
- **`ModelPullTest`'s two refusal tests take about 20 s together on a passing run**, where they took about 8 s.
- **A pull that ends late fails a claim**, naming the times from the start and from the stub's read; a timeout is a pull that never ended.
- **Nothing under `src/main` changes, no run id moves, and `CONTEXT.md` is unchanged.**

## What this does not decide

- The other unit tests that take a time on the wall, which no failure beside load has named.
- Which process still held the output file of the command that leaves a child, once beside 128 busy threads, after the child was killed and gone, so that `SystemProcessRunnerTest`'s folder for the commands' output could not be deleted; and whether the test should wait for it as ADR-352 §3 waits for the child. No claim of the test failed, and the limit of §7 has no part in it.
- Whether a later Java's first pull costs more than this one's. The pull before the first test covers whatever it costs, as long as the test's own pulls are not the first.

## Tests

- `pipeline.ModelPullTest.aPullThatStopsMovingIsGivenUp`: each of the three pulls that stop moving is given up as stalled no sooner than the 1 s bound after the pull was begun, and within the bound and the 5 s margin after the stub read the pull's request, under a timeout of 16 s (§1, §2, §6).
- `pipeline.ModelPullTest.aRefusalWhoseBodyStallsIsGivenUp`: the refusal whose body stops coming keeps its 400 and is given up no sooner than the 10 s bound after the pull was begun, and within the bound and the margin after the stub read the request, under a timeout of 25 s (§1, §2, §5, §6).
- `pipeline.ModelPullTest.aLateRefusalWhoseBodyStallsIsGivenUpWithinOneBound`: the status sent 5 s after the stub read the request, the pull is given up with the 400 kept, no sooner than the 10 s bound after the pull was begun and within the bound and the 5 s after the stub read the request, under a timeout of 25 s (§5, §6).
- `pipeline.ModelPullTest.aPullGivenUpHasItsConnectionClosed`: the stub, still sending, finds the connection closed within the bound and the margin of its read of the pull's request, under a timeout of 16 s (§2, §6).
- `pipeline.ModelPullTest.aStalledPullSendsOnlyTheQuestionsAndThePull`, `pipeline.ModelPullTest.aRefusalWhoseBodyIsCutOffKeepsItsStatus`, `pipeline.ModelPullTest.aRefusedConnectionIsReportedByItsInnermostCause`, `pipeline.ModelPullTest.aConnectionDroppedMidStreamIsReportedByItsInnermostCause` and `pipeline.ModelPullTest.aDroppedPullSendsOnlyTheQuestionsAndThePull`: claim what they claimed, under timeouts of 16 s for the first and 45 s for the four under the 30 s bound (§6).
- `pipeline.ModelPullTest` makes one pull before its first test (§3).
- `pipeline.SystemProcessRunnerTest.aCommandWhoseChildKeepsItsOutputOpenStillReturns`: the command that leaves a child returns within the 60 s it is given, with its own exit code 0, under a timeout of 100 s (§7).
- `pipeline.StubOllama` records the time an answer to a pull found its client gone, beside the time it read each request (§2).

None of these claims fails before the change: the change holds the same code to the same promises, on a clock the load moves less.
