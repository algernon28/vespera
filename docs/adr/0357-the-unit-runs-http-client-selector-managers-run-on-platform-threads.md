# ADR-357 — The unit run's HTTP client selector managers run on platform threads

> **Partly amended — see [ADR-358](0358-a-bound-the-code-under-test-keeps-in-real-time-is-held-on-the-wall-clock-from-the-event-it-races.md).** Of *What this does not decide*: whether `ModelPullTest`'s own bounds hold as ADR-352 has a timing bound hold is decided there: they are held on the clock on the wall, from the stub's read of the request. The decision stands.

- **Date**: 2026-11-06
- **Status**: accepted on 2026-11-06.
- **Settles**: what the unit run does about the JDK HTTP client's selector managers holding the virtual-thread scheduler's carriers ([#718](https://github.com/algernon28/vespera/issues/718)).
- **Keeps**: [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md) whole: what a pull sends, reads and bounds is unchanged, and its tests claim what they claimed. [ADR-352](0352-a-timing-bound-in-a-unit-test-is-one-a-busy-machine-does-not-move.md) whole, and its *What this does not decide* on `ModelPullTest`'s bounds stays open (What this does not decide). Every line of `src/main`. No run id moves.
- **Rests on**: the JDK 26 sources, read and not patched: `java.net.http`'s `HttpClientImpl` (the property `jdk.internal.httpclient.tcp.selector.useVirtualThreads`, read once, and the selector manager's thread chosen from it) and `jdk.internal.net.http.common.Utils.useVTForSelector`; `java.base`'s `VirtualThread.createDefaultScheduler`, `sun.nio.ch.WEPollSelectorImpl.doSelect`, `sun.nio.ch.EPollSelectorImpl` and `sun.nio.ch.Poller.pollSelector`; and thread dumps of the tests' Java on Windows 11. Refs [#718](https://github.com/algernon28/vespera/issues/718).

## Context

### What fails

`ModelPullTest` fails in some full unit runs on Windows, and passes alone: pulls end as stalled at their 30 s bound, are refused, or hold a test past its 15 s timeout. In two full runs it gave 17 failures and 4 errors, then 8 and 3.

### Why

The virtual-thread scheduler's carriers are one resource of the whole JVM, and the unit run holds them.

- **From Java 26 a `java.net.http.HttpClient` starts its selector manager on a virtual thread.** `HttpClientImpl` reads `jdk.internal.httpclient.tcp.selector.useVirtualThreads`, whose default is `default`, and starts the selector manager on a virtual thread for `always` and `default`, and on a daemon platform thread for `never`. `Utils.useVTForSelector` matches the value against those three names, case aside, and reads any other value as `default`: `false`, `no` or `platform` start a virtual thread.
- **On Windows that thread holds a carrier while it waits.** `WEPollSelectorImpl.doSelect` waits in `WEPoll.wait` inside `Blocker.begin`, mounted, and the scheduler starts a carrier to make up for each one so held, up to its ceiling. `VirtualThread.createDefaultScheduler` takes the ceiling from `jdk.virtualThreadScheduler.maxPoolSize` where it is set, and otherwise as the larger of the scheduler's parallelism, `jdk.virtualThreadScheduler.parallelism` or the processors, and 256.
- **On Linux it holds none.** `EPollSelectorImpl` has a virtual thread wait through `Poller.pollSelector`, which parks it and unmounts it from its carrier.
- **Nothing closes the unit run's clients.** One lives in every `ModelPull`, `HttpSidecarHealth` and `DoclingClient` of every Spring context the test framework caches, and in every instance a unit test made and the collector has not yet reclaimed.
- **Once every carrier is held, no virtual thread runs.** A new client's selector manager never starts, so its request is never sent, and `ModelPull`'s reader of the answer, a virtual thread, never reads one. How many clients are still uncollected depends on when the collector runs, which is why only some full runs fail, and `ModelPullTest` alone never does.

### Measured

On a machine of 16 logical processors, Windows 11, Java 26:

- **Thread dumps of the tests' Java**, taken while `ModelPullTest` ran in a full unit run, showed 256 carriers, 145 to 164 of them mounted on `HttpClientImpl$SelectorManager.run` waiting in `WEPoll.wait`.
- **`APullIsSentWhileManyHttpClientsAreOpenTest`** opens 16 more clients than the scheduler's ceiling, makes one pull of a model the stub serves after a `success` line, and closes the clients after. With `-Djdk.internal.httpclient.tcp.selector.useVirtualThreads=default` given on the Maven command line, which overrides the `argLine`'s value in the forked tests' Java, as without the property, the pull is never read and the test fails after 31.5 s, the pull ending as stalled at its 30 s bound. With `never`, it passes in under 2 s.
- **A full unit run with `never`** runs 3,287 tests, none failing, 7 skipped.

## Decision

### §1. Surefire starts the tests' Java with the selector managers on platform threads

Surefire's `argLine` carries `-Djdk.internal.httpclient.tcp.selector.useVirtualThreads=never`, beside the AspectJ agent it already carries. Every `HttpClient` the unit run opens then waits in its selector on a platform thread of its own, as before Java 26, and holds no carrier. The value is `never`, and no other word: the JDK reads any value it does not know as `default` (Context), so another word would put the selector managers back on virtual threads without a sign.

### §2. Failsafe keeps the shipped default

Failsafe's `argLine` does not carry the property. The integration tests' Java opens a few dozen clients at most, counted from its seven Spring contexts and not measured, far under the scheduler's ceiling, and runs as the shipped jar runs.

### §3. The shipped jar keeps the default

Nothing under `src/main`, `application.yaml` or the jar's start changes. The jar holds a handful of clients in one invocation, far under the ceiling, and its selector managers stay on virtual threads as the JDK has them.

## Alternatives rejected

- **Close `ModelPull`'s client when the pull ends.** The cached Spring contexts keep their beans, and so their clients, open whatever `ModelPull` does; the unit run would still hold the carriers.
- **Read the pull's answer on a platform thread.** The pull's own client still starts its selector manager on a virtual thread, which needs a carrier to send the request.
- **Raise `jdk.virtualThreadScheduler.maxPoolSize`.** That moves the ceiling and does not remove it: a unit run that holds more clients reaches it again.

## Consequences

- **The unit run's clients hold no carrier, on Windows or Linux**, and a pull is sent and read however many clients the run holds open.
- **The property is internal to the JDK.** `jdk.internal.httpclient.tcp.selector.useVirtualThreads` is no supported interface, and a later JDK may rename it or drop it. Either would leave the selector managers on virtual threads without a sign, and `APullIsSentWhileManyHttpClientsAreOpenTest` would fail in CI's "Test on NTFS" job, which runs the unit tests on Windows. On Linux the test passes with the property or without it (Context), so the Linux job cannot catch it.
- **The integration tests and the shipped jar run on the JDK's default**, and are not held to what the unit run is: one that came to hold hundreds of clients on Windows would meet the same ceiling.
- **Nothing under `src/main` changes, and no run id moves.**

## What this does not decide

- **Whether #718 was a timing bound.** It was not: the pulls were never sent or read, whatever the bound, and ADR-352's rules on a timing bound do not apply to it. This record decides nothing of ADR-352's.
- **Whether `ModelPullTest`'s own bounds hold as ADR-352 has a timing bound hold**, which ADR-352's *What this does not decide* leaves open, is left open here too.

## Tests

- `pipeline.APullIsSentWhileManyHttpClientsAreOpenTest.aPullEndsWhileMoreClientsAreOpenThanThereAreCarriers`: with 16 clients open beyond the scheduler's ceiling, read as `VirtualThread.createDefaultScheduler` reads it, a pull of a model the stub serves after a `success` line ends with nothing stopping the step, and the one pull was sent and answered (§1). With the property set to `default` it fails on Windows after 31.5 s. On Linux it passes either way.
