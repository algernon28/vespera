# ADR-164 — Every sidecar restarts unless the operator stopped it

- **Date**: 2026-09-27
- **Status**: accepted
- **Extends**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md). The operator still starts the sidecars from `compose.yaml` and stops them when the curation is done. What this record adds is what happens in between: a sidecar that exits on its own is started again by Docker, and one the operator stopped stays stopped.
- **Keeps**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) as it is: one synchronous call, no in-process retry of a service-scope failure, and the breaker on five consecutive ones. Seed extraction as it is: it fails on the first call that fails, and under [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) a seed extraction that did not complete records nothing and says to run the same command again ([#306](https://github.com/algernon28/vespera/issues/306)). [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md) and [ADR-163](0163-the-docling-sidecar-pins-docling-parse-7-17-0-and-its-image-is-tagged-for-the-pin.md): a restart runs the same image, so the extractor identity does not change.
- **Rests on**: probes run on 2026-09-27 against Docker Engine 29.6.1 under Docker Desktop, with throwaway containers and a throwaway Compose project, all removed afterwards. The running `vespera` containers were not touched. Probes A to G were run while this record was written. Probe H was run separately, the same day, in the same way.
- **Settles** point 1 of [#326](https://github.com/algernon28/vespera/issues/326), the restart policy. Points 2 to 5 stay open there (§3).

## Context

`compose.yaml` runs three services, `chroma`, `ollama` and `docling-serve`, and gives none of them a `restart:` key. So Docker's default applies, `no`: a container that exits stays down until someone starts it.

On 2026-09-26, over GesPOS, the Docling sidecar died of a segmentation fault (exit 139) on the 10th of 19 seeds. Seed extraction failed, and the invocation with it. Seed extraction went on only after the operator noticed, ran `docker start`, and ran the same command again. Nothing was lost on the ledger's side: a re-invocation continues where the last one stopped. **The whole cost of a dead sidecar is the operator's attention, and the wall-clock time until they come back.** On the 42,851-file archive that can be a night.

Two more facts from this project's own record:

- An earlier LibreOffice probe container exited 139 (SIGSEGV), a crash. Two others exited 143 (SIGTERM), which is a stop and not a crash.
- Docker Desktop's engine restarts when the machine does. The operator's handoff notes after a reboot record: *"None of the containers has a restart policy, so none comes back on its own"*, as an inconvenience.

ADR-163 removed the one crash known to recur. It did not show that no other native fault exists, and its §4 left the restart policy to #326.

### What Docker offers

`restart:` takes one of four values. Docker's own reference (`docker container run`, "Restart policies") describes them:

- **`no`**, the default: never restart.
- **`on-failure[:max-retries]`**: restart on a non-zero exit, at most `max-retries` times if given. It *"doesn't restart the container if the daemon restarts."*
- **`always`**: restart whenever the container stops. A container the operator stopped is *"restarted only when Docker daemon restarts or the container itself is manually restarted."*
- **`unless-stopped`**: like `always`, except that a container that was stopped *"isn't restarted even after Docker daemon restarts."*

Between restarts Docker waits 100 ms, then doubles the wait each time up to one minute. The wait goes back to 100 ms once a restarted container has run for 10 seconds.

### What was measured

Each probe ran `nginx:alpine` under `--init`, as `docling-serve` runs under `init: true`, with a shell as the process the init process starts. Probe G ran the shipped sidecar image itself, `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0`, locked down as `compose.yaml` runs it (`--init`, `--cap-drop ALL`, `no-new-privileges`), on a spare port.

**A. A process that dies of SIGSEGV 2 seconds after each start**, observed for 20 seconds:

| Policy | After 20 s |
| --- | --- |
| `no` | exited, code 139, 0 restarts |
| `on-failure` | running, 6 restarts |
| `on-failure:3` | exited, code 139, 3 restarts |
| `unless-stopped` | running, 6 restarts |
| `always` | running, 6 restarts |

So under an init process a segmentation fault is exit 139, as on 2026-09-26, and every policy but `no` and an exhausted `on-failure:N` brings the container back.

**B. How the container was stopped**, on a long-running process, observed 10 seconds later. The same result under `on-failure`, `unless-stopped` and `always`:

| How | Result |
| --- | --- |
| `docker stop` | exited, 0 restarts: a stop is never undone while the engine runs |
| SIGSEGV sent to the server process from inside (`docker exec … kill -SEGV`) | running, 1 restart |
| `docker kill -s SEGV` from outside | still running, 0 restarts: the init process does not pass SIGSEGV on, so this is no model of a crash |

**C. A container that crashes on every start**, as a broken image would:

- Under `unless-stopped`: 7 restarts in the first 10 seconds, 10 in 75 seconds, and status `restarting` throughout. The one-minute ceiling on the wait holds it there. It never stops trying, and it costs next to nothing while it waits.
- Under `on-failure:3`: 3 restarts, then exited for good.

**D. `on-failure:N` does not forget.** A container that ran 12 seconds and then died of SIGSEGV, under `on-failure:2`, was restarted twice and then left exited. Its healthy 12-second stretches did not reset the count. So N bounds the crashes over the container's whole life, not a burst of them. Only a manual start resets it: in probe F below, `docker compose up -d` after a `stop` brought the count back to 0.

**E. The wait resets after a healthy stretch.** Under `unless-stopped`, a container that ran 12 seconds and then crashed was started 6 times in 70 seconds, 12 to 13 seconds apart each time. The wait never grew.

**F. Through Compose.** A throwaway project, `-p p326probe`, whose one service carries `restart: unless-stopped` and `init: true`:

- After `up -d`, the container's `HostConfig.RestartPolicy.Name` is `unless-stopped`. So Compose's key is Docker's policy, unchanged.
- SIGSEGV from inside: running again, 1 restart.
- `docker compose -p p326probe stop`: exited, and still exited 5 seconds later.
- `docker compose -p p326probe up -d`: running again, 0 restarts.

**G. The Docling sidecar itself, under `unless-stopped`.** From `docker run` to the first answer from `/health`: 6.4 s. Then SIGSEGV to the server process, `docling-serve run`, 5 times in a row, each time waiting for `/health` to answer again:

| Crash | SIGSEGV to `/health` answering again |
| --- | --- |
| 1 | 16.8 s |
| 2 | 17.1 s |
| 3 | 17.5 s |
| 4 | 16.9 s |
| 5 | 17.4 s |

The probe ran twice. The first pass crashed the sidecar 3 times, and its timings were not kept, because the arithmetic that computed them failed. The table is the second pass. Docker's event log recorded all 8 crashes of the two passes as exit code 139, and the `docker rm -f` between the passes as 137.

**H. Adding the key replaces the container.** A throwaway project, `-p p326recreate`, on `chromadb/chroma:1.5.9`, with a marker file written into the running container:

- **Created with no `restart:`.** Then `compose.yaml` was given `restart: unless-stopped` and `docker compose -p p326recreate up -d` was run. The result was a new container id, and the marker was gone.
- **The same, but `docker update --restart unless-stopped <id>` first.** The policy read back as `unless-stopped`, and `up -d` still made a new container id, and the marker was gone. So `docker update` does not avoid the replacement once `compose.yaml` carries the key. It only gives the policy to a container nobody runs `up` over again.

**Not measured: a restart of the Docker engine or the machine.** It would have stopped the running `vespera` containers. What each policy does then is taken from Docker's reference, quoted above.

## Decision

### §1. All three services restart unless stopped

`compose.yaml` gives `chroma`, `ollama` and `docling-serve` each `restart: unless-stopped`.

- **A sidecar that dies comes back on its own.** The Docling sidecar answers `/health` again about 17 seconds after a segmentation fault (G). The operator no longer has to notice and run `docker start` before running the command again.
- **A sidecar comes back when the machine does**, if it was running when the machine went down. Invocations can be days apart, and the operator is told to leave the sidecars up across all five.
- **A sidecar the operator stopped stays stopped**, and a restart of the machine does not undo that (B, F, and Docker's reference). `docker compose -p vespera stop`, as the README says to run at the end of a curation, keeps the models Ollama holds and frees the memory for good.

**One policy for all three**, because nothing sets one apart:

- **docling-serve** is the one measured to crash, and the one this record is for.
- **Ollama** keeps the models it was given in its own filesystem, and a restart keeps that filesystem. So a restarted Ollama has its models, and a stopped one keeps them too. What removes them is a new container: `docker compose down`, or `up -d` after Ollama's part of `compose.yaml` has changed, as this record's own change does (H, and Consequences).
- **Chroma** is read by nothing yet (ADR-142), and its contents are a disposable projection (ADR-039). Restarting it costs one small container after a reboot. A policy of its own would be a second rule to state and check for no gain.

### §2. What this does not do: the step still fails

**A restart policy does not let a step survive the sidecar dying partway through it.** It brings the sidecar back for the next invocation. It does nothing for the calls already in flight, and it leaves every rule about them as it is:

- **Seed extraction** still fails on the first call that fails, and the invocation with it.
- **Stage 2 fails on the first dropped call too.** A dropped or refused connection is not a service-scope failure in the code. `DoclingClient.convert` rethrows a `ResourceAccessException` that is not a timeout, and the extraction step skips only `ServiceScopeFailureException`, which is thrown only when Docling *answers* with a service-scope category. So a dying sidecar fails the step at once. ADR-071's breaker never counts the dropped call, no `extraction_fault` row is written for it, the chunk it was in rolls back, and the next invocation sends that conversion again.

**Observed, not decided here:** ADR-140's consequence *"A dead sidecar still trips, and fast"* does not match the code for a dropped connection, since the breaker is never reached. And no test covers a dropped connection in stage 2. ADR-140 is left as it is, because a record is reopened only by a later one, and whether stage 2 should count, wait or retry is #326's points 2 to 4.

In both steps the operator still reads the closing line #311 gave a failed step, "run the same command again". What changes is that by the time they read it, the sidecar is up again. So the cost of a crash drops from "until the operator notices and starts the sidecar" to "until the operator notices". **On the 42,851-file archive, a crash at night still costs the night.** Only a step that waits for the sidecar and retries would change that, and that is §3.

### §3. Deliberately not decided here

These are points 2 to 5 of #326. They stay open there, and this record decides none of them:

- **Point 2, whether Vespera waits and retries.** When a call fails because the connection was refused or reset, should the step wait for `/health` and retry the calls in flight? That would amend ADR-071's *"Service-scope failures are skipped immediately, no in-process retry"*. The 17 seconds measured in G are an input to that decision, not the decision.
- **Point 3, its bounds.** How long a step waits, how many restarts it tolerates, how a retried call is kept from counting twice toward ADR-071's streak, and when a file that crashes the converter every time becomes an `extraction-failed` verdict with a reason of its own.
- **Point 4, both steps that call Docling.** Whether stage 2 and seed extraction answer the same way. For a crash they already do: both fail on the first dropped call (§2). They differ only for failures Docling answers with, which stage 2 skips and counts toward its breaker, and §2 leaves that as it is.
- **Point 5, the operator's message.** What the closing line says if Vespera gives up because the sidecar was not back in time.

**The restart policy does not make point 3 worse.** A file that crashes the converter crashes it once each time Vespera sends it. The step fails with nothing recorded for that conversion, and the next invocation sends it again. So Docker restarts the sidecar once per invocation, and the invocations are the loop, not Docker. A sidecar that crashes *at start* is a different case (C): Docker keeps trying at most once a minute, and stage 2's health check fails as it would against a stopped sidecar.

## Alternatives rejected

- **`no`, as today.** Costs the operator a `docker start` after each crash and after each reboot, and a crash at night leaves the sidecar down until morning.
- **`on-failure`, unbounded.** Behaves exactly like `unless-stopped` on a crash (A, B). It differs in two places, and both favour `unless-stopped`:
  - After a restart of the engine or the machine, `on-failure` leaves the sidecars down, which is the inconvenience the handoff notes record.
  - A service that exits with code 0 stays down.
- **`on-failure:N`.** N counts crashes over the container's whole life, and a healthy stretch does not reset it (D). So on a long curation, N crashes spread over days use it up. The sidecar is then left down exactly as under `no`, with nothing to say why it stopped coming back. No measurement gives a value for N either. Bounding how often Vespera tolerates a crash is point 3's question, and a count Docker keeps where Vespera cannot read it is the wrong place to answer it.
- **`always`.** Undoes the operator's stop. After `docker compose -p vespera stop` at the end of a curation, the next restart of the engine starts all three again, Ollama among them, on ports `8000`, `11434` and `5001`. That is the difference between the two, and it makes the README's "Stop the sidecars when you are finished" true only until the next reboot.
- **A different policy for Chroma or Ollama.** See §1: nothing measured sets them apart.
- **A Compose `healthcheck` on docling-serve, so that Docker restarts it when it stops answering.** Plain Docker does not restart an unhealthy container. Only Swarm and other orchestrators act on a health check. Stage 2 already checks `/health` itself (ADR-071).

## Consequences

- **`compose.yaml` gains `restart: unless-stopped` on each of its three services.** Nothing else in it changes. It is the only change this record needs. Nothing under `src/main` changes, and no `profile.yaml` key is added: this is how the sidecars are run, not a judgement about a corpus.
- **Taking this change replaces all three containers, and Ollama's models go with them.** Every service gains the key, so every service's part of `compose.yaml` changes. An operator whose sidecars were started before this change, and who then runs the README's `docker compose -p vespera up -d --build`, gets three new containers (H). `docker update` beforehand does not prevent it (H).
  - Chroma loses its contents, which are a disposable projection (ADR-039).
  - docling-serve holds nothing to lose. ADR-163's new tag already replaces it on the same command.
  - **Ollama loses every model it was given**: on this machine `bge-m3` and `qwen3:8b`, about 6 GB. The upgrade path is the README's existing `ollama pull` lines, run again after `up -d`: the embedding model before invocation 2, and the generation model before invocation 5.
  - **Nothing in the ledger is lost.** The working directory is on the host, not in any container.

  Compose replaces only the container of a service whose part of `compose.yaml`, or whose image, changed. A change to docling-serve alone leaves Ollama and its models alone. So the README states it in general, for any service whose part changed, and says to check with `ollama list` and pull again where Ollama's was one of them.
- **Whether Ollama's models should outlive a new container is not decided here.** A named volume for Ollama's model directory would keep them across `down` and across a changed `compose.yaml`. That changes what `down` means in the README, and it is a decision of its own.
- **`TestcontainersConfiguration` does not change.** Its containers live as long as one test run, and nothing there is meant to outlive a crash.
- **A crash can now go unseen.** The failed step's closing line is the only sign that the sidecar died. `docker compose -p vespera ps` then shows an uptime of a few seconds, and `docker inspect -f '{{.RestartCount}}' vespera-docling-serve-1` counts its restarts since the last start by hand. That trade is accepted: points 2 and 5 are where Vespera would say more.
- **A restart does not change the extractor identity.** The sidecar comes back from the same image, and reports the same `/version`, so the next invocation continues the same extraction run.
- **After a reboot, the sidecars come back only if Docker does.** With Docker Desktop, that means Docker Desktop is set to start when the operator signs in. The README says so.
- **The README's "Running it" says what the operator can now rely on**, in one paragraph after "Leave the sidecars up for all five invocations". A sidecar that stops on its own is started again, after a restart of the machine too if Docker starts. So after a command stops because the converter went away, the same command can be run again with no `docker start`, once the converter is back: about 17 seconds after a segmentation fault in G, so the README says "usually within half a minute", and says to run the command once more if it stops again. One stopped with `docker compose -p vespera stop` stays stopped, a restart of the machine included. No existing sentence of the README becomes false. `docs/check-claims.mjs` does not check the paragraph against `compose.yaml`; `SidecarRestartPolicyTest` pins the file's half.

## Tests

`SidecarRestartPolicyTest` (unit, no Docker, reads `compose.yaml` with SnakeYAML, as `DoclingSidecarImageTest` does):

- **`everySidecarComesBackUnlessTheOperatorStoppedIt`**: `compose.yaml` runs at least one service, and every service it runs carries `restart: unless-stopped`. A service added later without the key fails it. It failed before `compose.yaml` carried the key, when no service had a `restart:` key at all.

What no unit test can show is Docker's behaviour itself. Probes A to H above are the evidence for it, and they are not a test.
