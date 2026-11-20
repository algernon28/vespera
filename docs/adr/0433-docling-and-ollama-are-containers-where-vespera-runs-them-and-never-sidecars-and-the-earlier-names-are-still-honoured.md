# ADR-433 — Docling and Ollama are containers where Vespera runs them and never sidecars, and the earlier names are still honoured

- **Date**: 2026-11-20
- **Status**: accepted on 2026-11-20.
- **Decided**: by the operator, in [#891](https://github.com/algernon28/vespera/issues/891): that the word is *container* everywhere Vespera writes, says and names the Docling and Ollama it runs, and only where it runs one, Docling or Ollama on another machine being named *Docling*, *Ollama* or *the services* (§1, §2); that `CONTEXT.md` gains a **Container** entry with *sidecar* on its `_Avoid_` line (§7); that the settings are `vespera.containers.*` and `VESPERA_CONTAINERS_WSL_DISTRIBUTION`, the earlier names still read with one logged line naming the new one, the new name winning where both are set, and the earlier names removed in a later MAJOR release as ADR-431 ([#904](https://github.com/algernon28/vespera/issues/904)) defines one (§5); that `sidecars.lock` is `containers.lock`, and that until that MAJOR release a live `sidecars.lock` refuses a start, so that an earlier and a later Vespera never run containers at once (§6); that the rename covers the code's identifiers, the ten types and their methods, the tests' names, the log lines, the control panel, `README.md`, `CONTEXT.md`, `compose.yaml` and `.github` (§3); that a settled record keeps *sidecar* as written and this record says from which record on the word changed, as the index does (§8); that no run id is expected to move, which §9 checks against `StageModules`; that the earlier lock is held beside the new one for the length of an action (§6); that the names ADR-430 gave that say container of a Docling or an Ollama that may run on another machine are renamed in the same change (§3); and that `.github`'s job is *Verify on Linux, with the containers* (§4). Under the operator's rule, and stated here: the words of each line on another machine (§2), the names each type becomes (§3), which comments wait for a re-mint (§4), and when a setting counts as set and what the line says (§5).
- **Amends**: [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md) §8, three captions that call Docling and Ollama on another machine containers: the Docker daemon's when not asked, *Start Docker*'s last clause, and Run's before the first check (§2); and §9, *"The names of code that exist keep theirs, `SidecarStarter` among them"*: no name of ours says sidecar, and the names §12 and its Tests gave that say container of Docling or Ollama wherever they run say service (§3). It carries a note at its head.
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §6: the lock is `containers.lock`, and the earlier `sidecars.lock`, live, refuses a start (§6); §7 and §3's step 8: the one wait is `vespera.containers.wait-at-most`, the earlier name still read (§5). [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §1: the distribution is `vespera.containers.wsl-distribution`, `VESPERA_CONTAINERS_WSL_DISTRIBUTION`, the earlier name still read (§5). Each carries a note at its head.
- **Keeps**: [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md): an `_Avoid_` line binds our names, and an entry's `_Renders as_` line is the word for prose written for a reader outside the project (§7). [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1 and §7: a name or a comment in a module a stage's version names is changed only in a change that re-mints that module for its own reasons (§4). [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), `StageModules` as it is (§9). [ADR-395](0395-one-vespera-runs-on-a-machine-at-a-time-and-the-control-panels-own-child-is-let-through.md): the machine lock, its file and its byte, unchanged (§6). [ADR-397](0397-the-yaml-configuration-lies-beside-the-jar-and-the-jar-writes-its-shipped-copy-where-a-file-is-missing.md) §5: an `application.yaml` beside the jar that differs from its shipped copy is left as it is (§5). [ADR-337](0337-every-claim-can-fail-a-decision-is-held-once-and-a-records-tests-name-tests-that-exist.md) §5: a record naming a renamed test is corrected in place (§8). [ADR-426](0426-the-seed-partitions-names-leave-the-code-and-the-tests-and-a-comment-in-a-stages-module-waits-for-a-re-mint.md) §1 and §4, whose shape this record follows for another word.
- **Settles**: [#891](https://github.com/algernon28/vespera/issues/891).

## Context

**[ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md) called Docling and Ollama sidecars**, infrastructure's word for a service run beside an application, and the records, the code and the operator's documents kept it. An operator reads it as unfamiliar. [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md) §9 already wrote *container* in what the operator reads of the window's check, and left the names of the code to #891.

What says sidecar on `main` at this record, counted in any case:

| Where | Files | Lines |
|---|---|---|
| `src/main/java`, module `extraction` | 10 | 36 |
| `src/main/java`, module `embedding` | 1 | 1 |
| `src/main/java`, module `pipeline` | 40 | 244 |
| `src/main/java`, the control panel's `ui` | 9 | 44 |
| `src/main/java`, `VesperaApplication` | 1 | 3 |
| `src/main/beside-the-jar/application.yaml` | 1 | 8 |
| `src/test` | 71 | 707 |
| `docs/adr` | 106 | 811 |

- **In `pipeline` and `ui`, ten types**: `SidecarStarter`, `SidecarStartSettings`, `SidecarHealth`, `HttpSidecarHealth`, `SidecarRecovery`, `ui.SidecarChecks`, and the exceptions `SidecarStartRefusedException`, `SidecarLockUnavailableException`, `SidecarsDidNotAnswerException` and `SidecarsDidNotStartException`; methods and fields such as `stopTheSidecars`, `stopTheChildAndTheSidecarsThen`, `askTheSidecars` and `sidecarRecovery`; and the bean `sidecarStarter` `VesperaApplication.main` registers.
- **In `extraction`, one identifier**, `DoclingCallTimeoutException.sidecarGaveUp`, and 35 comment lines; **in `embedding`, one comment line**, in `OllamaClient`.
- **Two settings**, `vespera.sidecars.wait-at-most` (ADR-323 §7) and `vespera.sidecars.wsl-distribution` (ADR-390 §1), read by `SidecarStartSettings.from`, by `SidecarRecovery`'s bound, by `EffectiveValuesFile`, by the control panel's `ControlPanel.dockerSettings` through `StartingValues`, and offered by Command options through `ChildCommand`. Spring reads the second from `VESPERA_SIDECARS_WSL_DISTRIBUTION`, the control panel from the environment and `.env` under the same name. Every `application.yaml` an earlier jar wrote beside itself holds both keys, `wait-at-most: 180s` and `wsl-distribution: ""`.
- **One lock file**, `sidecars.lock` in the folder of the corpus-root locks, taken by `SidecarStarter` on the byte `Long.MAX_VALUE - 1` around a start, the engine's start, the stop of the containers and the shut-down, and waited for while another holds it (ADR-323 §6, ADR-390 §3).
- **The lines** the starter, the shut-down, the refusals, stage 2's start check, `WslIdleTimeout` and the machine lock's refusal print, and the control panel's warning, caption, preflight line and Command options' line.

**Some of these lines name Docling or Ollama wherever they run.** Stage 2's start check, the preflight's image check, the launch page's warning on an unnamed image and the wait of `SidecarRecovery` concern a Docling on another machine as much as one on this machine; the starter, the stop, the shut-down, the lock and `WslIdleTimeout` concern only what Vespera runs here (ADR-430 §10). Three of ADR-430's captions call the two on another machine containers.

**What moves a run.** A stage's version is the last commit touching the modules its row of `StageModules` names (ADR-058). Every row from extraction on names `extraction`, and seed measurement and embedding scoring name `embedding`. No row names `pipeline` (ADR-222), and the control panel and `VesperaApplication` lie in no module. A change to `extraction` would move every run from stage 2 on, and one to `embedding` every run from seed measurement on.

**The machine lock already keeps most earlier versions apart.** Since ADR-395 every Vespera but `serve` takes `machine.lock`, in the same folder and on the same byte, before anything else, so a Vespera from ADR-395 on and one of this record do not run at once. A jar from ADR-323 to before ADR-395 takes `sidecars.lock` and no machine lock.

## Decision

### 1. The word

**A container is a Docling or an Ollama that Vespera runs on this machine through Docker**, as ADR-430 §10 decides whether one is on this machine. Where Vespera runs one, what it writes, says and names calls it a container, never a sidecar. Where Docling or Ollama runs on another machine, Vespera runs no container there.

### 2. What the operator reads of one on another machine

**A text the operator reads that concerns Docling or Ollama wherever it runs names it**: *Docling*, *Ollama*, *docling-serve* where the line already names it so, or *the services* for both. It says container only of one on this machine. The lines that change otherwise than by the word:

| Where | Was | Is |
|---|---|---|
| `ExtractionHealthCheckListener`, at ERROR, twice | `Stage 2 (extraction) sidecar health check failed: {}` | `Stage 2 (extraction) Docling health check failed: {}` |
| `ExtractionHealthCheckListener`, at INFO | `Stage 2 (extraction) sidecar is healthy` | `Stage 2 (extraction) Docling is healthy` |
| `DoclingRunsAnotherImageException.notReporting` | *… until the sidecar says it is that image. A sidecar that does not report its image …: start the sidecars again with `up -d --build`* | *… until docling-serve says it is that image. A docling-serve that does not report its image …: start the containers again with `up -d --build`* |
| `DoclingRunsAnotherImageException.running` | *… otherwise start the sidecars again with the compose files that build …* | *… otherwise start the containers again with the compose files that build …* |
| the preflight's image check, `ui` | *the Docling sidecar at ‹address› runs …* | *Docling at ‹address› runs …* |
| `LaunchValues.warningsToRun` | *… so the jar expects the sidecar to run its own default image, …* | *… so the jar expects Docling to run its own default image, …* |
| Command options' line for the wait, `ChildCommand` | *How long the jar waits for the sidecars to answer; no run's identity holds it.* | *How long the jar waits for Docling and Ollama to answer; no run's identity holds it.* |
| the preflight's thread | `the control panel asks the sidecars` | `the control panel asks Docling and Ollama` |

**ADR-430 §8's three captions** that call the two on another machine containers read:

| Element | Was | Is |
|---|---|---|
| Docker daemon, not asked | Docker daemon: not asked, both containers are on another machine | Docker daemon: not asked, Docling and Ollama are both on another machine |
| *Start Docker*, its last clause | … and where both containers are on another machine. | … and where Docling and Ollama are both on another machine. |
| Run, before the first check | Off until the containers have been checked. | Off until Docling and Ollama have been checked. |

**Every other line** that says sidecar concerns what Vespera runs on this machine, and says container where it said sidecar, *the containers* where it said *the sidecars*, the sentence otherwise as it is: the starter's and the shut-down's lines, the refusals of `SidecarStartRefusedException`'s kinds, `ComposeOrBuildxIsNotInstalledException`'s, `ComposeFileUnreadableException`'s, `CommandOutputFileUnusableException`'s and `NoComposeFileBuildsTheImageException`'s, `WslIdleTimeout`'s four, `AnotherVesperaRunsException`'s *so that none stops the containers another is using*, *Shut down Docker*'s warning, which reads *stops Vespera's containers*, and the control panel's log line *the containers were not stopped: ‹why›*.

The rule binds text the operator reads: the jar's lines and refusals, the window, `README.md` and `docs/`. A name of ours is bound by §1's entry, as ADR-122 has it, and §3 says what that comes to.

### 3. No name of ours says sidecar

**No identifier under `src/main/java` or `src/test/java`, no string or text block under `src/main/java`, no line of the shipped `application.yaml`, and no `@DisplayName`, `@Story`, `@Feature` or `@Epic` of a test holds the word sidecar, in any case or inside a longer word**, but:

- **the earlier names §5 and §6 still read**, each a string of `src/main/java` that is exactly `vespera.sidecars.wait-at-most`, `vespera.sidecars.wsl-distribution`, `VESPERA_SIDECARS_WSL_DISTRIBUTION` or `sidecars.lock`;
- **a constant of `Adr`**, which names a record by the title it keeps;
- **`DoclingCallTimeoutException.sidecarGaveUp`**, in `extraction`, which §4 leaves to that module's next re-mint.

A test's strings and comments, a claim among them, are not bound: a test may name the earlier names it holds are still read.

**What each type becomes:**

| Was | Is |
|---|---|
| `pipeline.SidecarStarter` | `pipeline.ContainerStarter` |
| `pipeline.SidecarStartSettings` | `pipeline.ContainerStartSettings` |
| `pipeline.SidecarHealth` | `pipeline.ContainerHealth` |
| `pipeline.HttpSidecarHealth` | `pipeline.HttpContainerHealth` |
| `pipeline.SidecarRecovery` | `pipeline.DoclingRecovery`: it waits for Docling wherever it runs |
| `ui.SidecarChecks` | `ui.ServiceChecks`: it asks Docling and Ollama wherever they run |
| `pipeline.SidecarStartRefusedException` | `pipeline.ContainerStartRefusedException` |
| `pipeline.SidecarLockUnavailableException` | `pipeline.ContainerLockUnavailableException` |
| `pipeline.SidecarsDidNotAnswerException` | `pipeline.ContainersDidNotAnswerException` |
| `pipeline.SidecarsDidNotStartException` | `pipeline.ContainersDidNotStartException` |

`stopTheSidecars`, of the starter and of `DockerControl`, is `stopTheContainers`; the bean is `containerStarter`; the starter's private lock record is `ContainerLock`. Every other name follows §1: container for what Vespera runs here, Docling, Ollama or service for one wherever it runs. The tests' doubles and names follow the same rule; `ScriptedSidecarHealth` is `ScriptedContainerHealth`.

**A name of ours says container only of a Docling or an Ollama Vespera runs here.** The names ADR-430 gave that say it of one that may run on another machine are renamed in the same change:

| Was | Is |
|---|---|
| `ui.DockerCheck.Container`, a row of the check, here, elsewhere or not resolved | `ui.DockerCheck.Service` |
| `ui.CheckList.containerWords` | `serviceWords` |
| `ui.DockerControl`'s private `container(URI, String)`, the question the check asks | `service(URI, String)` |
| `ui.Cockpit.checkTheContainers`, the clock's check | `checkTheServices` |
| `ui.RunAndAutoLabelWaitForTheContainersTest` | `ui.RunAndAutoLabelWaitForDoclingAndOllamaTest` |
| `ui.TheCheckAsksEachContainerAndOllamasListOfModelsTest`, its `aContainerIsOkWhereItAnswersTwoXxWithinTwoSecondsAndKoOtherwise` and `aContainerWhoseHostCannotBeResolvedIsKoAndNotAsked` | `ui.TheCheckAsksEachServiceAndOllamasListOfModelsTest`, `aServiceIsOkWhereItAnswersTwoXxWithinTwoSecondsAndKoOtherwise` and `aServiceWhoseHostCannotBeResolvedIsKoAndNotAsked` |
| `pipeline.AContainerIsOnThisMachineWhereItsHostResolvesHereTest` | `pipeline.AServiceIsOnThisMachineWhereItsHostResolvesHereTest` |
| `pipeline.EachContainerIsStartedOnlyWhereItIsOnThisMachineTest` | `pipeline.EachServiceIsStartedOnlyWhereItIsOnThisMachineTest` |
| `pipeline.TheCheckOfTheDockerDaemonStartsNothingTest.neitherContainerHereAsksNothingAndTheCheckTakesNoLock` | `neitherServiceHereAsksNothingAndTheCheckTakesNoLock` |
| `ui.TheStartDockerButtonTest.itIsOffWhileEverythingHereAnswersWhileItRunsWhileAnInvocationRunsAndForContainersOnAnotherMachine` | `…AndForServicesOnAnotherMachine` |
| `ui.TheWindowAsksWhatItsServicesFieldsHoldTest`'s `containerMarks` | `serviceMarks` |

Names that say container of what Vespera runs here keep it: `DockerEngine.startTheContainers`, `stopTheContainers`, `EachServiceIsStartedOnlyWhereItIsOnThisMachineTest`'s `onlyTheContainerOnThisMachineIsAskedBroughtUpAndWaitedFor` and `onWindowsTheWslconfigIsSeenToWhereOneContainerIsHereAndNotWhereNone`. A record whose Tests section names a test renamed here is corrected in place as §8 has it, ADR-430's and this record's among them.

### 4. Comments, and what waits for a re-mint

**The comments that say sidecar in `pipeline`, `ui`, `VesperaApplication` and the shipped `application.yaml` are reworded now**, by §1 and §2: no stage's version names them, so rewording them moves no run.

**`extraction`'s and `embedding`'s wait** under ADR-216 §1, and are reworded, `sidecarGaveUp` with them as `doclingGaveUp`, by the first change that re-mints each module for its own reasons: `ConfidenceScores`, `ConversionStatus`, `DoclingCallTimeoutException`, `DoclingClient`, `DoclingConnectionLostException`, `ExtractorIdentity`, `FailureCategory`, `FailuresInARow`, `OccurrenceJudge` and `QualityGrade` in `extraction`, and `OllamaClient` in `embedding`.

**`docker/docling-serve/Containerfile`'s first comment** waits too, for a change that edits that file for its own reasons: ADR-397 §5 compares a file of the build context by its bytes, so a comment changed there writes `Containerfile.new` beside every operator's.

**`compose.yaml`'s comment and `.github`'s job name** are reworded now: ADR-397 §5 compares `compose.yaml` by its keys, and a comment is none. The job in `.github/workflows/build.yml` is *Verify on Linux, with the containers*.

### 5. The settings

**The names are `vespera.containers.wait-at-most` and `vespera.containers.wsl-distribution`**, `VESPERA_CONTAINERS_WSL_DISTRIBUTION` in the environment and in `.env`; the shipped `application.yaml` holds them under `vespera.containers`, with the values and the meaning ADR-323 §7 and ADR-390 §1 give them.

**The earlier names are still read**, `vespera.sidecars.wait-at-most` and `vespera.sidecars.wsl-distribution`, from every source the new name is read from, `VESPERA_SIDECARS_WSL_DISTRIBUTION` among them:

- **A name is set where a source gives it a value that is not blank.** A blank distribution is WSL's default already, so the `wsl-distribution: ""` every earlier jar wrote sets nothing.
- **The new name wins where both are set**, whichever source gives each: an earlier name in the environment does not come before the new one in `application.yaml`.
- **Where only the earlier name is set**, its value is read as the new name's, by every reader of the setting: the starter, `DoclingRecovery`'s bound, `effective-values.properties`, which writes it under the new name with the earlier name's source, and the control panel's Docker settings.
- **Each earlier name set is said in one line**, whether or not the new one is set too:

  *‹earlier› is set: it is the earlier name of ‹new›, and is read only where ‹new› is not set. Rename it: a later major version reads only ‹new›.*

  The jar logs it at WARN once an invocation, as `VesperaApplication.main` registers the starter, for `wsl-distribution` before `wait-at-most`; an earlier name set as its environment variable is said by its property's name. The control panel says its own at WARNING in its log as it reads its Docker settings, once a window.

**The seams.** `pipeline.EarlierSettingNames.linesFor(Environment)`, a `List<String>` of the lines in their order, which `VesperaApplication.main` logs; `ContainerStartSettings.from(Environment)` reading each setting as above; in the control panel, which names no class of the jar but `DockerEngine`, `ControlPanel.dockerSettings(Sources, Path, Path)` reading each as above, and `ControlPanel.earlierSettingNames(StartingValues.Sources)`, a `List<String>` of the same lines.

**The earlier names are removed in a later MAJOR release**, as ADR-431 ([#904](https://github.com/algernon28/vespera/issues/904)) defines one: a setting renamed whose earlier name is no longer read is a change an existing configuration no longer works under.

**An `application.yaml` beside the jar that an earlier jar wrote** differs from the shipped copy at four keys, the two earlier names and the two new ones: as ADR-397 §5 has it, it is left as it is, `application.yaml.new` is written beside it, and its line names the keys. The operator's file keeps working through the earlier names, `wait-at-most: 180s` saying its line.

### 6. The lock

**The lock file is `containers.lock`**, in the same folder, on the same byte, taken around the same four actions and waited for as ADR-323 §6 and ADR-390 §3 have it.

**`sidecars.lock`, live, refuses the action.** Once it holds `containers.lock`, each of the four actions tries the earlier file's lock on the same byte, creating the file where it is missing, and does not wait for it:

- **Held by another process, or by another lock of this process**, the action is refused before it runs any command, and `containers.lock` is released. A start of the containers, from `vespera run`, `vespera label --auto` or *Start Docker*, and the engine's start are refused by a `ContainerStartRefusedException`, one line and exit 1 on the command line:

  *the containers are not answering and Vespera cannot start them: an earlier version of Vespera holds the lock file ‹absolute path of sidecars.lock›, and may be starting or stopping them. Let it finish, or end it, and run the same command again.*

  The stop of the containers says *the containers were not stopped: an earlier version of Vespera holds the lock file ‹path›.*, and the shut-down *Docker was not shut down: an earlier version of Vespera holds the lock file ‹path›.*, each as ADR-390 §6's lines say a lock that cannot be used.
- **Not held**, the earlier lock is kept with `containers.lock` for the length of the action and released before it, so that an earlier Vespera starting meanwhile waits for it, as it waits for another of its own. A file that is there and that nobody holds means nothing, as ADR-323 §6's never did.
- **The earlier file cannot be opened or locked**: the action is refused as `ContainerLockUnavailableException` refuses it, naming that file.

**A later MAJOR release drops the earlier lock**, as it drops the earlier names. The file is never deleted.

### 7. The vocabulary

**`CONTEXT.md` gains a Container entry**, in Operation after the Machine lock: the definition of §1, an `_Avoid_` line opening with *sidecar* and naming this record, and a `_Renders as_` line saying *container* where Vespera runs it and *Docling*, *Ollama* or *the services* where they run on another machine, never container. It is not to be confused with the containing document of a containment, nor with a file format's container, which keep the word. The Extraction fault, the Machine lock, the Preset and the Folder beside the jar, which said sidecar, say the converter, the containers, docling-serve and the containers. No other line of `CONTEXT.md` says sidecar.

### 8. The records

**A settled record keeps *sidecar* as it was written**: the word is a name of the code it was written against, as ADR-426's records keep theirs. From this record on, a record says container, and names Docling, Ollama or the services as §2 has it. The index's row of this record says so, and no other row changes. `docs/decision-ledger.md` is closed and gains nothing.

**A record whose Tests section names a test §3 renames** is corrected in place, in the change that renames it, with the words "named for the container by ADR-433, #891" beside it, as ADR-337 §5 and ADR-426 §3 have it.

### 9. Run ids

**None moves.** `StageModules` names `corpus`, `extraction`, `similarity`, `embedding` and `synthesis`, and no row names `pipeline` (ADR-222). What changes now lies in `pipeline`, in `ui` and `VesperaApplication`, which are in no module, in the shipped `application.yaml`, which lies in none, and in the tests; `similarity`, `synthesis` and `corpus` hold no use of the word; and `extraction` and `embedding` change nothing until §4's change re-mints each for its own reasons. Neither setting enters a run's identity (ADR-323 §7, ADR-390 §1).

## Alternatives refused

- ***Container* for Docling and Ollama wherever they run**, as ADR-430's three captions had it. It names a thing Vespera does not run, which is the unfamiliarity the word was chosen to remove.
- **Refusing the earlier names at once**, or reading them silently. The first breaks every `application.yaml` an earlier jar wrote; the second leaves an operator unaware the name will stop working.
- **Renaming `sidecarGaveUp` and `extraction`'s and `embedding`'s comments now.** It would move every run from stage 2 on for no change of behaviour, the sweep ADR-216 §7 refused.
- **Only trying the earlier lock, not keeping it.** An earlier Vespera starting after the check would start the containers beside a later one; the earlier versions that ADR-395's machine lock does not keep apart are exactly those that take only `sidecars.lock`.

## Consequences

- **The operator reads container** for what Vespera runs, and Docling, Ollama or the services for what runs on another machine.
- **A configuration written for an earlier jar keeps working**, and says once an invocation which names to rename; every operator whose `application.yaml` an earlier jar wrote sees the line for `vespera.sidecars.wait-at-most` until the file is renamed or replaced by its `.new`.
- **A start beside an earlier Vespera that is starting or stopping the containers is refused**, and says how to go on; a `sidecars.lock` file lies in the folder of the locks of every machine a later Vespera started containers on.
- **A new name that says sidecar fails the build**, but the earlier names' strings, a record's constant and `sidecarGaveUp`.
- **`.github`'s job is *Verify on Linux, with the containers***: the repository's branch protection, which names the check by its job's name, is set to the new name by the operator.
- **`vespera.containers.wait-at-most` bounds the wait for a Docling on another machine too** (ADR-323 §7): the operator named the group by what the setting mostly bounds.

## What this does not decide

- **When `extraction` and `embedding` are next re-minted**, and with them `sidecarGaveUp` and 36 comment lines (§4).
- **The MAJOR release** that drops the earlier names and the earlier lock: ADR-431 ([#904](https://github.com/algernon28/vespera/issues/904)) decides what a release is.

## Tests

| Test | Holds |
|---|---|
| `pipeline.TheContainersAreNamedSoEverywhereTest.noNameOrTextOfTheJarsCodeSaysTheEarlierWord` | §3: the identifiers and literals of `src/main/java`, but the four earlier names whole and `sidecarGaveUp` |
| `pipeline.TheContainersAreNamedSoEverywhereTest.noNameOfTheTestsSaysTheEarlierWordButARecordsTitle` | §3: the identifiers of `src/test/java` and the text of each `@DisplayName`, `@Story`, `@Feature` and `@Epic`, but `Adr`'s constants |
| `pipeline.TheContainersAreNamedSoEverywhereTest.noCommentOfAModuleNoStageNamesSaysTheEarlierWord` | §4: the comments of every class in a module `StageModules` does not name, or in none |
| `pipeline.TheContainersAreNamedSoEverywhereTest.noNameSaysContainerOfDoclingOrOllamaWhereverTheyRun` | §3: none of the names the second table renames is an identifier of `src/main/java` or `src/test/java`, and `DockerCheck` declares no `Container` |
| `pipeline.TheContainersAreNamedSoEverywhereTest.noLineOfTheShippedApplicationYamlSaysTheEarlierWord` | §3, §4, §5: the shipped copy's keys and comments |
| `TheVocabularyNamesTheContainerTest.theContainerEntryRefusesTheEarlierWordAndSaysWhatTheOperatorReadsOnAnotherMachine` | §7: the entry, its `_Avoid_` line opening with the earlier word and naming this record, and its `_Renders as_` line, whole |
| `TheVocabularyNamesTheContainerTest.noLineOfTheVocabularyButTheContainersAvoidLineSaysTheEarlierWord` | §7 |
| `pipeline.TheSettingsEarlierNamesAreStillReadTest.anEarlierNameAloneIsReadAsTheNewOneAndSaidInOneLine` | §5: both earlier names set, read as the new ones, two lines whole in their order |
| `pipeline.TheSettingsEarlierNamesAreStillReadTest.theNewNameWinsWhereBothAreSetAndTheEarlierIsStillSaid` | §5 |
| `pipeline.TheSettingsEarlierNamesAreStillReadTest.aBlankEarlierNameSetsNothingAndSaysNothing` | §5: the `wsl-distribution: ""` an earlier jar wrote |
| `ui.TheControlPanelReadsTheSettingsEarlierNamesTest.theControlPanelReadsTheEarlierNamesFromItsSourcesAndSaysThem` | §5: the environment's and `.env`'s earlier names, the new name in `application.yaml` winning, a blank earlier key said for nothing |
| `pipeline.AnEarlierVersionsLockRefusesAStartTest.aLiveEarlierLockRefusesAStartBeforeAnyCommand` | §6: `sidecars.lock` held, the start refused in its line, whole, no command run, `containers.lock` free afterwards |
| `pipeline.AnEarlierVersionsLockRefusesAStartTest.aLiveEarlierLockRefusesTheEnginesStartTheStopAndTheShutDown` | §6: the three other actions, each line whole |
| `pipeline.AnEarlierVersionsLockRefusesAStartTest.anEarlierLockNobodyHoldsRefusesNothing` | §6 |
| `pipeline.AnEarlierVersionsLockRefusesAStartTest.theEarlierLockIsKeptForTheLengthOfAStartAndReleasedAfter` | §6: held by this process while the start waits for the containers, free once it is refused |
| `ui.ExplanationsAreCaptionsOnMouseOverTest.eachHeaderRowsCaptionSaysWhatItIsAndWhereItWasFound` | §2: the daemon's caption, not asked |
| `ui.ExplanationsAreCaptionsOnMouseOverTest.eachButtonOfTheActionRowStandsOnAWrapperThatCarriesItsCaption` | §2: *Start Docker*'s caption, whole |
| `ui.RunAndAutoLabelWaitForTheContainersTest.runsCaptionSaysItWaitsForTheFirstCheckAndForAStartOrAShutDown` | §2: Run's caption before the first check |

What no unit test shows: that the jar logs §5's lines once an invocation, which rests on its one call in `VesperaApplication.main`; that `DoclingRecovery`'s bound and `effective-values.properties` read the earlier wait, which `ContainerStartSettings.from`'s reading does not prove of them; and the words of §2's first table, which §3's test holds only by the word they no longer say.

## What the implementation owes

Beside turning the tests above green: the renames of §3's two tables in `src/main/java`, in `src/test/java` and in `pom.xml`'s comments that name `HttpSidecarHealth`; the lines of §2; the comments of §4 that are reworded now; the reading of the earlier names and their lines (§5), in the jar and in the control panel, and `effective-values.properties`' key; `containers.lock` and the earlier lock (§6); the shipped `application.yaml`'s keys and comments; `PipelineHoldsOnlyTheRulesOnRecordTest`'s lines for the renamed classes of `pipeline`; `README.md`'s 14 lines, `docs/architecture.md`'s two and `docs/running-stage-by-stage.md`'s eight, the settings' names among them; `compose.yaml`'s comment and `.github/workflows/build.yml`'s job name, *Verify on Linux, with the containers*; the Tests sections of the records naming a test either table of §3 renames, ADR-430's and this record's among them, corrected in place (§8); and every citation of this record in the code naming it ADR-433.
