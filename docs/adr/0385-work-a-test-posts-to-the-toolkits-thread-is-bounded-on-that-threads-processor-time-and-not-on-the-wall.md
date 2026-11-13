# ADR-385 — Work a test posts to the toolkit's thread is bounded on that thread's processor time, and not on the wall

- **Date**: 2026-11-13
- **Status**: accepted on 2026-11-13.
- **Decides**: for the control panel's window tests, [ADR-352](0352-a-timing-bound-in-a-unit-test-is-one-a-busy-machine-does-not-move.md)'s *What this does not decide*, *"The other tests that take a time on the wall, which no failure beside load has named"*, as far as `HeadlessToolkit.onTheToolkit`'s 10 s read bound goes: that bound has failed beside load, and is a bound on a cost, held as ADR-352 §1 holds one, on the processor time of the thread that pays it, here the toolkit's thread and not the test's (§1).
- **Keeps**: ADR-352 §1 to §3 and [ADR-358](0358-a-bound-the-code-under-test-keeps-in-real-time-is-held-on-the-wall-clock-from-the-event-it-races.md) §1: the read bound is no promise the code under test makes in real time, so ADR-358 does not apply to it. `HeadlessToolkit.start`'s 10 s bound on the toolkit starting, which no failure has named, stays on the wall. Every line of `src/main`.
- **Settles**: [#727](https://github.com/algernon28/vespera/issues/727).

## Context

`HeadlessToolkit.onTheToolkit` posts a test's work to the JavaFX application thread with `Platform.runLater` and waited 10 s on the wall for its answer. `TheReportsOpenInTheWindowTest` and `TheStepsFoldIntoLegsTest` timed out there on Windows under load and passed when run again. Both classes have since left the suite (ADR-361, ADR-362, ADR-368); every window test of `ui` still reads and acts through the same call.

### Measured

On a Linux machine of 4 processors, Java 26, the window test classes `EveryBarOfTheStepsCarriesItsPercentTest`, `TheLiveReportTest`, `TheControlPanelsThemeTest` and `TheOutputPanelTest` (18 tests) run together, the work's time on the wall and the toolkit thread's processor time measured inside each call:

| Run | Tests failed by the read bound | Costliest work, processor time | Same work, on the wall | Longest wait in the queue |
| --- | --- | --- | --- | --- |
| alone | 0 of 18 | 2,723 ms, the first window shown | 3,079 ms | 1 ms |
| two processors, 16 busy threads on them, before | 15 of 18 | 3,754 ms | 30,597 ms | 76,170 ms |
| two processors, 16 busy threads on them, after | 0 of 18 | | | |

A window shown costs the toolkit's thread 0.7 to 0.9 s of processor time, the first of a Java 2.7 s. Beside the busy threads the same work took eight to ten times as long on the wall, past 10 s. Once one call timed out, its work went on running on the toolkit's thread, and every later call, its own and the next tests', waited behind it in the queue, up to 76 s: one timeout became the class's and the next classes'.

No work measured waited on another `runLater`, no `WebView` load held the thread, and no work hung: every call answered in the end. The cause is the bound's clock: a cost bounded on the wall, which a busy machine moves.

## Decision

### 1. The bound is on the toolkit thread's processor time

**`onTheToolkit` fails only once the toolkit's thread has spent more than `WORK_PROCESSOR_TIME`, 10 s, of its processor time since the work was posted, and the work has not answered.** The time counted is the thread's, from the post, so an earlier test's work still running on the thread counts against the wait, and a machine that keeps the thread waiting for a processor does not. 10 s is more than three times the costliest work measured, 3.75 s beside the load. `HeadlessToolkit.start` claims that the Java measures a thread's processor time, and turns the measuring on.

### 2. Work that stands still fails on the wall

**Where the toolkit's thread stands waiting, blocked or asleep, gaining no processor time, for `STANDING_STILL`, 10 s, while the work has not answered, the call fails.** Work waiting on something that does not come, such as a `runLater` posted behind it, spends no processor time, so §1 alone would wait for it forever. A thread kept from a processor by load stands runnable, and is not standing still.

### 3. A ceiling on the wall, and the thread's stack in every failure

**Past `CEILING`, 180 s on the wall, the call fails whatever the thread does**, for work that neither spends its processor time nor stands still, as a nested event loop turning for a dialog no one answers. Each failure is a `TimeoutException` naming the bound passed and the toolkit thread's state and stack, so that a failure on a runner names the work that held the thread.

### 4. Work posted from the toolkit's own thread is refused

**`onTheToolkit` called on the toolkit's thread throws `IllegalStateException` at once**, as its work would be queued behind the call that waits for it.

## Decided here

1. **The toolkit thread's processor time, not a longer wall bound.** A wall bound long enough for the load measured, 30 s and more, is still one a busier machine moves, and it is spent whole by every call behind a timed-out one.
2. **Counted from the post, not from the work's start.** Counted from its start, work queued behind an earlier test's work still running would wait without bound; counted from the post, it fails, naming that work.

## Consequences

- The 18 window tests measured pass beside 16 busy threads on two processors, where 15 failed.
- A test that hangs the toolkit's thread fails after 10 s of standing still, or 180 s on the wall, and not after 10 s.
- Nothing under `src/main` changes, and no run id moves.

## What this does not decide

- `TheOutputPanelTest.itFollowsTheBottomOnlyWhileNothingHoldsIt`'s claim *"at the bottom, each new tick's lines move the view down with them"*, seen to fail once in a whole build and not reproduced beside load: its reads and its claim run in one task of the toolkit's thread, which the read bound does not touch.
- `HeadlessToolkit.start`'s 10 s bound, and `WindowForTests`' 20 s `BOUND` for the window to show what a test waits for, both on the wall.
- Windows: the measures are Linux's.

## Tests

- `ui.ABusyMachineDoesNotTimeOutTheToolkitsWorkTest.workCostingLittleAnswersHoweverLongTheBusyMachineKeepsIt`: work spending 1 s of the toolkit's processor time beside 16 busy threads for each processor answers, past 10 s on the wall (§1); before the change it timed out at 10.5 s. It is skipped where the busy threads did not keep the work past 10 s.
- `ui.ABusyMachineDoesNotTimeOutTheToolkitsWorkTest.workSpendingPastItsBoundFails`: work spinning on the toolkit's thread fails, no sooner than 10 s, with a timeout naming its processor time and the work's frame (§1, §3).
- `ui.ABusyMachineDoesNotTimeOutTheToolkitsWorkTest.workWaitingForWorkPostedBehindItFails`: work waiting for a `runLater` it posted fails no sooner than 10 s and before the ceiling, naming the thread `WAITING` in it, and the thread takes the next work once let go (§2, §3).
- `ui.ABusyMachineDoesNotTimeOutTheToolkitsWorkTest.workPostedFromTheToolkitsOwnThreadIsRefused` (§4).
