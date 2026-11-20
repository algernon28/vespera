# ADR-430 — The control panel checks Docker, the containers and the models, and starts nothing until Start Docker is pressed

> **Partly amended — see [ADR-433](0433-docling-and-ollama-are-containers-where-vespera-runs-them-and-never-sidecars-and-the-earlier-names-are-still-honoured.md).** Of §8: the Docker daemon's caption when not asked reads *Docker daemon: not asked, Docling and Ollama are both on another machine*, *Start Docker*'s ends *and where Docling and Ollama are both on another machine.*, and Run's before the first check *Off until Docling and Ollama have been checked.* Of §9: no name of the code says sidecar, `SidecarStarter` being `ContainerStarter`.

- **Date**: 2026-11-19
- **Status**: accepted on 2026-11-19.
- **Decided**: by the operator, in [#889](https://github.com/algernon28/vespera/issues/889): that the window starts nothing as it opens and checks five things instead, the Docker daemon, the Docling container, the Ollama container, the embedding model and the generative model (§1, §3); that the check is shown as a list of two columns in the header, between the titles and the theme switch, *OK* in green, *KO* in red and a dash where a row is not asked, and the status line gets nothing from it (§2); that it starts nothing on Windows either, booting no WSL distribution (§3); that it is done every 5 s and at once after a start, a shut-down, a download and an invocation ending (§4); that a *Start Docker* button stands immediately before *Shut down Docker*, sees to `.wslconfig`, starts the daemon, brings the containers up, waits for them, and then downloads a missing model where `vespera.models.pull` is on, and when it is disabled (§5); that Run is disabled until the daemon on this machine, Docling and Ollama show OK, and a missing model holds it only where `vespera.models.pull` is off, Auto Label being held by the same rule for Ollama and the generative model alone (§6); that a download panel floats at the window's top right, with Hide, Cancel, Retry and Close (§7); that explanations are captions on mouse over and never text in the window, the texts that explain a control today becoming its caption (§8); that what the operator reads says *container* (§9); that whether a container is on this machine is worked out by resolving its host, each container on its own, for `vespera run` and `vespera label --auto` too (§10); and what stays as it is (§11). Under the operator's delegation: the commands that ask the daemon without starting it on each system (§3); that a model's name is matched against the names Ollama lists without regard to case (§3); the rows' words, ids and captions (§2, §8); which explanatory texts become which control's caption (§8); what a host under `.invalid` is (§10); that the check, *Start Docker* and the window's download are for Docling's and Ollama's addresses and the converter's image as the Services section's fields read when each begins, a blank field standing for what the sources name, and the stop as the window closes and *Shut down Docker* for the launch values held (§1, §3, §5, §7); the window's seams (§12).
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §5: *"As it opens. Once the window is shown, `start` is called once"* is gone; nothing is started as the window opens (§1), and *Start Docker* stands in the action row before *Shut down Docker* (§5), its start for the addresses and the image the Services fields hold as it is pressed and not for the launch values held, which the stop as the window closes keeps (§1, §5). §4: `DockerEngine` gains `isOnThisMachine`, `engineAnswers`, `startTheContainers` and an `of` naming the folder of the compose files, its surface still of `java.base` types and itself; `DockerControl` gains `check` and `download`, and its `start` is *Start Docker*'s (§12). §3: the stop of the containers alone, as the window closes, runs where at least one container is on this machine and one of those answers (§10). §7: a host under `.invalid` is one that cannot be resolved, never looked up, and starts nothing, as before (§10). It carries a note at its head.
- **Amends**: [ADR-421](0421-on-windows-vespera-writes-wslconfig-with-no-idle-timeout-where-there-is-none-and-changes-no-file-that-is-there.md) §1: the control panel sees to `.wslconfig` when *Start Docker* is pressed and not as its window opens; the check writes and reads nothing (§3, §5); and a start writes or reads it where at least one container is on this machine, where it did so only where both were (§10). §6: `DockerControl.inProcess`'s start calls `keepTheWslDistributionUp` and then `startTheContainers`, and its line is shown when *Start Docker* has run (§5, §12). It carries a note at its head.
- **Amends**: [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md) §1's *"Nothing else pulls a model"*: the control panel's download panel pulls too, a model the check found Ollama has not, when the operator clicks its *KO* or after *Start Docker* where `vespera.models.pull` is on (§7). §4's request, its never-sent list and §7's interrupted pull hold for it. It carries a note at its head.
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §3's step 2: a host is resolved, and not read as written; each container is judged on its own, and only those on this machine are asked, brought up and waited for (§10); §4's `up` names the one service to start where the other container is not on this machine. It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5, as ADR-390 left it, *"nothing is pulled"*: the window downloads a model on the operator's click, or after *Start Docker* where `vespera.models.pull` is on (§7). It carries a note at its head.
- **Amends**: [ADR-413](0413-save-my-labels-and-auto-label-are-a-labels-section-of-the-sidebar-and-the-window-calls-no-label-an-answer.md) §1: Save my labels and Auto Label each stand on a wrapper carrying its caption, which is no longer a text of the section; §3's words are kept, as the captions (§8). [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §8: a field's one-line hint is its caption and no longer a line beneath it (§8). Both carry a note at their heads.
- **Keeps**: ADR-390 §1, §2, §3's shut-down and §6: each system's one start, *Shut down Docker* and its question, and the lines. ADR-421 §2 to §5: the file, what is read, no `wsl --shutdown`, the lines. ADR-323 §3's steps 3 to 9 for the containers on this machine, §4's `--no-recreate` and never-run list, §6's locks. ADR-329 §2 to §10 for a step's own pull. ADR-395: the window opens only once it holds the machine lock. ADR-423: the questions' titles and colours. ADR-047: nothing prompts but the operator's own presses.
- **Settles**: [#889](https://github.com/algernon28/vespera/issues/889).
- **Rests on**: Microsoft's documentation of `wsl.exe`, read and not measured; the JDK's `InetAddress` and `NetworkInterface`, read; RFC 6761 §6.4 on `.invalid`, read.

## Context

Since ADR-390 §5 the control panel starts the Docker engine as its window opens, through `DockerControl.start`, which on Windows also sees to `.wslconfig` (ADR-421 §6), and its only Docker button in the action row is *Shut down Docker*. Run is enabled whatever answers: an invocation started with the containers down starts them itself (ADR-323), and one started with them on another machine and not answering fails where it fails. Nothing in the window says what answers.

ADR-323 §3's step 2 reads a base URL's host as written: `localhost`, `127.x.y.z` and `::1` are this machine, every other name is another. So this machine's own name, its address on the network, `0.0.0.0` and `host.docker.internal` read as another machine's, and nothing is started for them, though Docker on this machine runs them.

### Read, not measured

- **`wsl --list --running --quiet`** lists the names of the running distributions, one a line, and starts none; `wsl --list --verbose` marks WSL's default distribution with `*` at the head of its line; a command run inside a distribution, `wsl [-d <name>] … --exec …`, starts that distribution where it is stopped. `wsl.exe` writes its lists in UTF-16 unless told otherwise, so a reader of them as single bytes sees a NUL after each character.
- **`systemctl is-active --quiet <unit>`** exits 0 where the unit is active and non-zero where not, and starts nothing; a `docker` command against `docker.socket` with the service stopped starts the service (ADR-390 §3).
- **Colima** leaves no socket while stopped, so `docker version` answers non-zero and starts nothing.
- **`InetAddress.getAllByName`** reads a literal address without a lookup, and resolves a name through the system's resolver, `localhost` through the hosts file; `InetAddress.isLoopbackAddress` and `isAnyLocalAddress` say whether an address is `127.0.0.0/8` or `::1`, and `0.0.0.0` or `::`; `NetworkInterface.networkInterfaces()` gives every address this machine holds.
- **RFC 6761 §6.4**: `.invalid` and every name under it never resolves, and an application may treat such a name as not resolvable without asking.
- **Ollama's `/api/tags`** lists the models it has, each under `name`; `/api/pull` streams `status`, `digest`, `completed` and `total` (ADR-329 §4, §5).

### Not measured

That a pull Ollama is streaming stops when the client closes the connection, and that the next pull resumes the bytes it kept, which ADR-329 §7 reads for a pull that ends any other way; `wsl --list --verbose` on a Windows whose language is not English, of which only the `*` and the names are read; the time a lookup of a name takes where the resolver does not answer, which §4's check waits out on its own thread; and what the check costs on Windows every 5 s, one to three `wsl.exe` processes.

## Decision

### 1. Nothing is started as the window opens

Once the window is shown, the check (§3) runs, on a thread of its own, and nothing else: no engine is started, no container is brought up, no model is downloaded and no `.wslconfig` is written. Closing the window is as ADR-390 §5 has it: the window's child stopped, then the containers on this machine stopped where one answers (§10), for the launch values held, Docker left running. A download running is cancelled first (§7).

### 2. The check is a list in the header

**Where.** In the header, between the titles, the corpus root, the working directory, the invocation and the database's size, and the theme switch, as a node of id `docker-check`. The header's other parts neither move nor change size; the list is in a smaller font than the titles and in two columns of at most three rows, so it is no taller than the titles. Each column's width is fixed, so a row whose mark grows, *KO ↓ 45%* (§7), moves nothing.

**The rows**, each a name and a mark, the name's id `check-<row>` and the mark's `check-<row>-mark`:

| Column | Row | Name |
|---|---|---|
| left | `docker-daemon` | Docker daemon |
| left | `docling` | Docling |
| left | `ollama` | Ollama |
| right | `embedding-model` | Embedding model |
| right | `generative-model` | Generative model |

**The marks.** *OK*, style class `ok`, drawn in the keeping green (ADR-423), where the daemon or the container answers, or where Ollama has the model; *KO*, style class `ko`, drawn in the ending red, where not; *–*, style class `not-asked`, where the row is not asked: the Docker daemon where neither container is on this machine (§10), and a model while Ollama is *KO*, or where no source names it. Until the first check has come back every mark reads *–*.

**The status line**, `outcome`, gets nothing from the check. It shows what a press said, as before.

### 3. What the check asks, and that it starts nothing

**At the addresses the fields hold.** Each check asks the containers, the daemon and the models at Docling's and Ollama's addresses as the Services section's fields *Converter's address* and *Ollama's address* read when the check begins, white space taken off, and not at the launch values held, which change only as an invocation the window starts stores them. A field left blank stands for what the sources name, the environment, `.env` and `application.yaml` beside the jar, and else the jar's own address, as a blank launch value did (ADR-390 §4); never for the launch value held. So an address the operator corrects in its field is asked at the next check, and a row it turns *OK* lets Run go (§6), with no invocation started first to store it, which Run, held by that row, could not start.

**The Docker daemon**, only where at least one container is on this machine (§10), each command killed after 30 s and a program that cannot be started reading as *KO*, no lock taken:

| System | Commands, in order | OK where |
|---|---|---|
| Windows, a distribution named | `wsl --list --running --quiet`; where the distribution is among the names, `wsl -d <distribution> -u root --exec docker version --format {{.Server.Version}}` | the second exits 0 |
| Windows, none named | `wsl --list --verbose`, the name on the line marked `*` being WSL's default; `wsl --list --running --quiet`; where the default is among the names, `wsl -u root --exec docker version --format {{.Server.Version}}` | the third exits 0 |
| Linux | `systemctl is-active --quiet docker.service`; where it exits 0, `docker version --format {{.Server.Version}}` | the second exits 0 |
| macOS | `docker version --format {{.Server.Version}}` | it exits 0 |

A list's names are read with every NUL and byte-order mark taken out, each line stripped, and compared without regard to case. A list that exits non-zero, a distribution not among the running ones, or no line marked `*`, is *KO* with nothing more run. So no command is run into a stopped distribution, and on Linux no `docker` command meets a socket whose service is stopped.

**The containers**: Docling asked `GET /health` and Ollama `GET /api/version` at their addresses, as ADR-323 §3's step 3 asks them, any 2xx an answer, each waiting at most 2 s; a container whose host cannot be resolved (§10) is *KO* and is not asked.

**The models**: where Ollama is *OK*, its `GET /api/tags` is asked once, and a model is *OK* where a name it lists is the model's name, or that name with `:latest` where the model's name has no tag, a tag being what follows a `:` after the name's last `/`. Both are compared without regard to case: Ollama resolves a name in `/api/show`, which `OllamaClient.serves` asks (ADR-384 §5) and whose 404 alone makes a run pull (ADR-329 §3), ignoring case part by part (ADR-329's *What Ollama does*), so a name the operator wrote in another case than Ollama's list is a model a run finds served, and the check reads it *OK* too. The name in force is the working directory's `profile.yaml`'s `embeddingModel` or `generationModel` where it states one not blank, else `spring.ai.ollama.embedding.options.model` or `spring.ai.ollama.chat.options.model` as the window's sources name them, the environment, `.env` and `application.yaml` beside the jar (ADR-344 §3).

**Nothing is written**: the check sees to no `.wslconfig` and takes no `sidecars.lock`, so a start and a check never wait on each other.

### 4. When it is done

- **As the window opens**, and **every 5 s** after while the window is open.
- **At once** after a start (§5) has returned, a shut-down has returned, a download has stopped, done, cancelled or lost (§7), and after the window's own child has ended.
- **Not while a start or a shut-down runs**: the regular check is skipped until it has returned, and is done at once then. A check not finished when the next is due is not started again beside it.
- It stops with the window.

### 5. *Start Docker*

**The button**, id `start-docker`, reading *Start Docker*, stands in the action row immediately before *Shut down Docker*: Run, Stop, *Start Docker*, *Shut down Docker*, then Publish and its boxes as before.

**Pressed**, on a thread of its own, it does what `vespera run`'s start does (ADR-323 §3, ADR-390 §2, ADR-421), for Docling's and Ollama's addresses and the converter's image as the Services section's fields read when it is pressed, white space taken off, a blank address standing for what the sources name as in §3 and a blank image for the one the image's field starts from without the launch values, and not for the launch values held, which it does not change: on Windows `.wslconfig` is seen to; the daemon is started where it does not answer; the containers on this machine are brought up with `up --no-recreate` and waited for, `vespera.sidecars.wait-at-most` at most. Its line, a refusal's or `.wslconfig`'s, is shown in `outcome` where it has one. The window checks at once after (§4), and where that check finds Ollama *OK* and a model *KO*, and Command options' `vespera.models.pull` is ticked, the download panel opens and downloads it (§7).

**Disabled**: while the daemon, where asked, and every container on this machine show *OK*; while a start, a shut-down or a download runs; while the window's own child runs; and where neither container is on this machine. Until the first check has come back it is disabled.

**`.wslconfig`** is seen to by this press and by each start of `vespera run` and `vespera label --auto`, and no longer as the window opens.

***Shut down Docker*** is as ADR-390 §5 has it: its question, the child stopped first, disabled only while its own shut-down runs.

### 6. Run and Auto Label

**Run** is disabled:

- until the first check has come back;
- until the Docker daemon, where it is asked, Docling and Ollama show *OK*;
- where a model shows *KO* and Command options' `vespera.models.pull` is not ticked; ticked, a run downloads it itself (ADR-329), and a missing model holds nothing;
- while a start, a shut-down or a download runs;
- and, as before, while the window's own child runs or another holds the working directory or the corpus root (ADR-344).

**Auto Label** is held by the same rule for Ollama and the generative model alone, which is all it uses: disabled until Ollama shows *OK*, where the generative model shows *KO* and `vespera.models.pull` is not ticked, while a start, a shut-down or a download runs, and as before. Save my labels and Publish are not held by the check.

**`vespera run` and `vespera label --auto` from the command line** are unchanged: they start what does not answer (§11).

### 7. The download panel

**What it is.** A panel of id `download-panel`, not modal, floating at the window's top right over its content, moving nothing beneath it. It downloads one model at a time, the embedding model before the generative model, through Ollama's `POST /api/pull`, as ADR-329 §4 sends it, at Ollama's address as its field reads when that model's download begins, a blank one standing for what the sources name, as in §3, and stops a download that has not moved for Command options' `vespera.models.pull-stalled-at-most`, ADR-329 §6's bound, as one whose connection was lost. The bound is the one the Command options section holds as each download begins: its starting value from the sources (ADR-344 §3) until the operator changes it there, as `vespera.models.pull` is read from its box (§5, §6). It sends nothing else to Ollama, and deletes nothing.

**It opens** when the operator clicks the *KO* of a model's row while Ollama shows *OK*, or after *Start Docker* (§5); either way it downloads every model the last check found *KO*.

**For each model**, a row: its kind and name (`download-<row>-name`), a progress bar (`download-<row>-bar`), and its words (`download-<row>-words`): *waiting* before its download begins, *‹x› of ‹y› GB · downloading* while it runs and *‹y› of ‹y› GB · done* once Ollama has it, `x` and `y` the bytes Ollama has reported `completed` and `total` over the layers named so far, in GB of 1,024³ bytes, to one decimal.

**Hide** (`download-hide`) takes the panel away and the download goes on; the model's row in the header then reads its *KO* and, beside it, `check-<row>-progress`, *↓ ‹n›%*, `n` being `completed × 100 / total`, rounded down. Clicking it brings the panel back.

**Cancel** (`download-cancel`) stops the download running, closing its connection, and drops the ones waiting; the panel closes. What Ollama downloaded stays in Ollama, and the next download resumes from it (ADR-329 §7).

**A lost connection**, an error Ollama sends, or a download that stopped moving, brings the panel back, hidden or not, with the reason in `download-reason` and **Retry** (`download-retry`) beside Cancel; Retry downloads that model again and goes on with the rest.

**Close** (`download-close`) is offered once every model of the panel is done, and takes the panel away.

**Run and Auto Label** are disabled while a download runs (§6). A download a run makes on its own (ADR-329) is not shown in the panel.

### 8. Explanations are captions on mouse over, never text in the window

**A control that can be disabled stands on a wrapper**, a node of id `<control's id>-captioned` holding it alone, of the control's own size with the control at its top left corner (§12), and its caption is installed on the wrapper, which is never disabled: a disabled control receives no mouse event, so a caption installed on it would never show. Run, Stop, *Start Docker*, *Shut down Docker*, Save my labels and Auto Label stand on wrappers; the action row, id `actions`, holds the four wrappers in §5's order.

**The new elements' captions:**

| Element | Caption |
|---|---|
| Run, enabled | Starts vespera run. |
| Run, held by the window's or another invocation | Off while an invocation runs. / Off while another invocation holds the working directory or the corpus root. |
| Run, held by a download | Off until the model downloads finish. |
| Run, held by a start or a shut-down | Off while Docker and the containers start. / Off while Docker shuts down. |
| Run, before the first check | Off until the containers have been checked. |
| Run, held by rows not *OK* | *Off until ‹rows› show OK.*, the rows in §2's order, the last joined by *and*, *shows* for one: *Off until Docling shows OK.* |
| Run, held by a model, pull off | Off until the models are downloaded: vespera.models.pull is off, so a run would not download them. |
| Stop | Stops the running invocation. Asks first. |
| *Start Docker* | Starts Docker and the containers on this machine and waits for them, then downloads a model Ollama lacks where vespera.models.pull is on. Off while they all show OK, while Docker starts or shuts down or a model downloads, while an invocation runs, and where both containers are on another machine. |
| *Shut down Docker* | Stops the containers, then Docker and every other container it runs. Asks first. |
| Docker daemon, asked | Docker daemon: the Docker engine the containers on this machine run in |
| Docker daemon, not asked | Docker daemon: not asked, both containers are on another machine |
| Docling or Ollama | *‹Name›: this machine, ‹address›* / *‹Name›: ‹host›, another machine; Vespera does not start it* / *‹Name›: ‹host› cannot be resolved; Vespera does not ask it or start it* |
| a model's row | *‹Kind›: ‹name›*, then *; click KO to download it into Ollama* where it reads *KO*, or *; asked once Ollama shows OK* where it reads *–*; *‹Kind›: none is named* where no source names it |
| Hide, Cancel, Retry | Hides this panel; the download goes on. / Stops the download; what was downloaded stays in Ollama, and the next download resumes from it. / Downloads it again from where it stopped. |
| *KO ↓ n%* | Downloading. Click to show the download. |

Where more than one thing holds Run, its caption is the first of the table's rows for Run that applies, in the table's order. Auto Label's caption stays ADR-413 §3's words whatever holds it.

**The texts that explain a control today become its caption**, each removed from the window, its words kept:

| Text, by its id or place | Its words | Becomes the caption of |
|---|---|---|
| `label-words`, beneath Save my labels | ADR-413 §3's | `label-captioned` |
| `label-auto-words`, beneath Auto Label | ADR-413 §3's, naming the label model as it does | `label-auto-captioned` |
| `hint-vespera.corpus-root`, `hint-vespera.working-dir`, `hint-vespera.deliverable-root`, `hint-vespera.docling.base-url`, `hint-spring.ai.ollama.base-url`, `hint-vespera.label.model`, beneath each field | `LaunchValues.hintOf`'s | the field's box, `field-<property>`, holding its name, its input or the list of working directories, and its lines |
| `hint-vespera.docling.image`, beneath the converter's image | `LaunchValues.hintOf`'s | `docling-images`, the GPU and CPU choices |
| the words above the Inclusions tab's tree | *Untick a folder holding nothing you want. An unticked folder is never read, and a change makes the next walk another, so every run after it is new.* | `inclusions-tree` |
| the words above the list of folders not relevant | ADR-413 §3's | `not-relevant-list` |
| a step's *A stop here costs ‹cost›.*, beside its Stop | as it reads | that step's Stop |

**Not explanations, and kept as text**: what a field's value is and where it came from (`LaunchField`'s status and starting lines), the image each choice names, the model in use beneath the label model's field and the naming model's line, Command options' names and starting lines, the line *The next invocation wants these values:* above them, the steps' states and percents, the Preflight section's lines, the ending panel, the headings, and every outcome line. The Profile window's rows keep ADR-389's ▸ and their titles' captions, and its form keeps *Name the working directory: profile.yaml lies in it.*, which says what is missing; they are not the cockpit.

### 9. Words

What the operator reads of this record says *container*, never *sidecar*. The names of code that exist keep theirs, `SidecarStarter` among them.

### 10. Whether a container is on this machine

**Resolved, not read.** A base URL's host is resolved, and its container is on this machine where any address it resolves to is a loopback address (`127.0.0.0/8`, `::1`), a wildcard address (`0.0.0.0`, `::`) or one of this machine's own network addresses, read from `NetworkInterface` at each question; where every address it resolves to is none of these, it is on another machine. A literal address is read without a lookup. The answer is asked again each time, nothing remembered, and needs no Docker and starts nothing, so it is asked at each check (§3).

**A host that cannot be resolved** is neither: its container shows *KO* in the check, its caption saying so, and nothing is started for it. A host that is `invalid` or ends in `.invalid`, with or without a final dot and in any case, is one that cannot be resolved, and is never looked up (RFC 6761 §6.4), so ADR-390 §7's tests start nothing and wait on no resolver. A base URL with no host is one that cannot be resolved.

**Each container on its own.** Vespera starts, asks and waits for only the containers on this machine:

- **Neither on this machine**: nothing is started, written or run, as ADR-323's step 2 has it; the daemon's row is not asked.
- **One on this machine**: `.wslconfig` is seen to on Windows (ADR-421 §1); step 3 asks that one alone; `up` names its service, `docling-serve` or `ollama`, after `--no-recreate`, so the other is not created here; step 8 waits for it alone. The other is asked nowhere by the starter, and fails where it fails.
- **Both on this machine**: as before, `up` naming no service.
- **The stop as the window closes** (ADR-390 §3) runs where at least one container is on this machine and one of those answers.

**For every entry point.** The rule holds for `vespera run` and `vespera label --auto`, for *Start Docker* and for the check: `SidecarStarter` asks it, and the control panel asks it through `DockerEngine`.

**Known limit.** On Windows the WSL distribution's own address is not one of Windows' addresses, so a base URL naming it reads as another machine's and nothing is started for it. `localhost`, which WSL forwards (ADR-390 §1), is unaffected.

### 11. What stays as it is

The layout of everything else in the window; closing it; *Shut down Docker*; `vespera run` and `vespera label --auto` from the command line, which still start what does not answer, now judged by §10; a run's own pull of a model (ADR-329).

### 12. The seams

- **`DockerEngine`**, its surface still primitives, `java.base` types and itself (ADR-390 §4):
  - `public static Optional<Boolean> isOnThisMachine(URI baseUrl)`: §10, empty where the host cannot be resolved;
  - `public Optional<Boolean> engineAnswers(URI doclingBaseUrl, URI ollamaBaseUrl)`: §3's daemon check, empty where neither container is on this machine, starting nothing and taking no lock;
  - `public Optional<String> startTheContainers(URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage)`: ADR-323 §3 for the containers on this machine, with the compose files of the engine's compose folder; the refusal's line, empty where they answer or nothing was to be done.
  - `public static DockerEngine of(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder, Path composeFolder)`: the folder the compose files are read from, null for the folder beside the jar (ADR-397 §6); the four-argument `of` stays and is the same as a null `composeFolder`.
  - `startTheEngine` stays, and the window no longer calls it.
- **`SidecarStarter`** gains a constructor taking a `ThisMachine`, which its other constructor and `forTheApplication` give as the system's, and `engineAnswersStartingNothing()`, an `Optional<Boolean>`.
- **`ThisMachine`**, of `pipeline`, holds §10's rule over a resolver and the machine's own addresses, both given, so a test resolves nothing.
- **`DockerControl`** gains `DockerCheck check(LaunchValues values, Models models)`, `Download download(LaunchValues values, String model, Duration stalledAtMost, Consumer<Downloaded> told)` and the records `Models` and `Downloaded`; its `start` is *Start Docker*'s. `stalledAtMost` is §7's bound as Command options holds it when the download begins: the window reads it from the section and passes it, and `inProcess` reads no source for it. `DockerControl.NONE` checks every row *OK* and downloads at once. **What the window gives them**: `check`, `start` and `download` are each given the launch values held with `doclingBaseUrl`, `ollamaBaseUrl` and `doclingImage` replaced by what the fields of `vespera.docling.base-url`, `spring.ai.ollama.base-url` and `vespera.docling.image` read when each begins, stripped, blank where the field is blank, which `inProcess` reads from its `Settings`' sources as it read a blank launch value; the check, which runs on the clock's thread, is given what the fields read on the toolkit's thread, the latest a field's change has left. `stopTheSidecars` is given the launch values held, and `shutDown` none. **`DockerControl.Settings`** gains `Path composeFolder`, after `profileFolder`, which `inProcess` gives `DockerEngine.of`; `ControlPanel.dockerSettings` gives null, the folder beside the jar, so the control panel still names no class of the jar but `DockerEngine`, and a test names the repository's own folder. **`DockerCheck`** is the control panel's record of the five rows. The window's own download reads the stream over `HttpURLConnection` of `java.base`, as its checks do, and names no class of the jar for it.
- **`DockerControl.inProcess(Settings, BiFunction<URI, URI, Optional<Boolean>> engineAnswers)`** is the same control with the daemon's row asked through `engineAnswers`, given the two addresses held, in place of `DockerEngine.engineAnswers`; `inProcess(Settings)` gives it the engine's. So a test asks the containers and Ollama over loopback and runs no `wsl`, `systemctl` or `docker` command.
- **`ModelPull`**, the window's download, gains `start(URI ollama, String model, Duration stalledAtMost, Consumer<Downloaded> told, Executor runner)`, the download handed to `runner` once and run there; the four-argument `start` gives it a thread of its own. So a test cancels a download before it has begun.
- **A wrapper** (§8) takes its control's size and holds it at its top left corner: its layout bounds are the control's, with the control at 0,0, in the action row and in the Labels section alike, so no control moves from where it stood before it had one.

### 13. No run id moves

`ThisMachine`, `SidecarStarter`, `HostSystem` and `DockerEngine` are in `pipeline`, which no stage's version names (ADR-222); the rest is the control panel's. None decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.

## Alternatives rejected

- **The check's result in `outcome` or the output panel.** The operator chose the header: `outcome` is what a press said, and a line there would be written over by the next press.
- **Start Docker bringing up the engine alone, as the window's start did.** The operator chose the whole of `vespera run`'s start, so that a press makes Run usable.
- **Asking the daemon with `docker version` inside WSL as the check.** It boots a stopped distribution, and Docker with it: the check would start what it is asked not to.
- **`wsl --list --verbose`'s state column for the running distributions.** Its words are Windows' language; `--running --quiet` gives names alone.
- **`wsl --list --quiet`'s first name as the default.** Not documented to be the default; `--verbose`'s `*` is.
- **Reading a host as written, as ADR-323 did.** It reads this machine's own name and address as another machine's.
- **Remembering a host's answer.** A machine's addresses change with its network; a lookup each check costs a resolver's answer, cached by the JDK.
- **Captions on the controls themselves.** A disabled control shows none.
- **A modal download dialog.** The operator chose a panel that moves nothing and lets the window be used.
- **Polling the containers from Run itself.** The check every 5 s serves the header and the buttons from one place.
- **The check, *Start Docker* and the download for the launch values held.** They change only as an invocation starts, which Run, held by the check, cannot: an address corrected in its field would never be asked, and Run never let go.
- **Storing the fields' values at each check or at *Start Docker*.** The launch values file keeps what an invocation was started with (ADR-344 §3); the check and the start read the fields and store nothing.

## Consequences

- **Opening the control panel starts nothing.** Docker, the containers and the models are started or downloaded by *Start Docker*, by the download panel, or by a `vespera run` or `vespera label --auto`, from the window or the command line.
- **Run is usable once the containers answer**, and says in its caption what it waits for.
- **A container named by this machine's own name or address, `0.0.0.0` or `host.docker.internal`** is started by Vespera where it was not.
- **An address corrected in its field is checked within 5 s**, and the check, *Start Docker* and a download follow what the Services section shows, which is what a Run started then would use.
- **The README's "Running it"** says the control panel starts nothing as it opens, checks the Docker daemon, the containers and the models in its header, at the addresses the Services section shows, and that *Start Docker* starts them and downloads a missing model; that Vespera tells a container on this machine by resolving its address; and that the window's explanations are captions on mouse over.
- **`CONTEXT.md`'s Control panel entry** says the same in its words.
- **`docs/architecture.md`'s technology table** says the control panel's *Start Docker* starts the engine and the containers, citing this record, and that a container is on this machine where its host resolves here.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists `ThisMachine` as holding no rule.

## What this does not decide

- **The names of the code that say *sidecar***, which [#891](https://github.com/algernon28/vespera/issues/891) renames.
- **A label model named apart from the generative model.** Auto Label is held by the generative model's row; a label model that differs is not checked.
- **The WSL distribution's own address**, §10's known limit.
- **`DockerEngine.startTheEngine` and `SidecarStarter.ensureTheEngineRuns`**, which nothing of the window calls now, and whether they go.
- **A download the operator starts while a run downloads the same model.** Ollama shares a blob's download among the requests that wait on it (ADR-329 §7).

## Tests

| Method | What it holds |
|---|---|
| `pipeline.AContainerIsOnThisMachineWhereItsHostResolvesHereTest.aHostIsThisMachinesWhereAnAddressItResolvesToIsLoopbackWildcardOrOneOfItsOwn` | §10: `localhost`, `::1`, `0.0.0.0`, this machine's name and `host.docker.internal` resolving to its own address, and a name resolving to another address and a loopback one, are this machine; a name resolving to another machine's addresses alone is another; a literal is never looked up |
| `pipeline.AContainerIsOnThisMachineWhereItsHostResolvesHereTest.aHostThatCannotBeResolvedIsNeitherAndAnInvalidOneIsNeverLookedUp` | §10: a name the resolver refuses, a base URL with no host, and `.invalid` hosts in three spellings, never asked of the resolver, cannot be resolved |
| `pipeline.AContainerIsOnThisMachineWhereItsHostResolvesHereTest.theControlPanelAsksTheSameRuleThroughTheDockerEngine` | §10, §12: `DockerEngine.isOnThisMachine` for `localhost`, `127.0.0.1` and an `.invalid` host; `engineAnswers` and `startTheContainers` for containers that cannot be resolved ask and start nothing |
| `pipeline.EachContainerIsStartedOnlyWhereItIsOnThisMachineTest.onlyTheContainerOnThisMachineIsAskedBroughtUpAndWaitedFor` | §10: Docling here and Ollama on another machine, the engine down, the commands whole with `up` naming `docling-serve`, and Ollama never asked |
| `pipeline.EachContainerIsStartedOnlyWhereItIsOnThisMachineTest.aNameResolvingToThisMachinesOwnAddressIsStartedAsLocalhostIs` | §10: both named by a name resolving to the machine's own address, the engine down, the same commands as for `localhost`, `up` naming no service |
| `pipeline.EachContainerIsStartedOnlyWhereItIsOnThisMachineTest.onWindowsTheWslconfigIsSeenToWhereOneContainerIsHereAndNotWhereNone` | §10, ADR-421 §1: one here, `.wslconfig` written; neither here, no command and the folder empty |
| `pipeline.TheCheckOfTheDockerDaemonStartsNothingTest.onWindowsADistributionNotRunningIsNotAskedSoWslBootsNothing` | §3: the list of running distributions alone, nothing run into the distribution, *KO*; running, the engine check inside it, *OK*; NULs in the list read through |
| `pipeline.TheCheckOfTheDockerDaemonStartsNothingTest.onWindowsTheDefaultDistributionIsTheOneMarkedAndIsAskedOnlyWhileItRuns` | §3: no distribution named, `--verbose`'s `*` line names the default; another running, nothing more run; the default running, the engine check with no `-d` |
| `pipeline.TheCheckOfTheDockerDaemonStartsNothingTest.onLinuxTheEngineIsAskedOnlyWhileItsServiceIsActiveAndOnMacosAsItIs` | §3: Linux's service inactive, no `docker` command; active, the engine check; macOS the engine check alone; no start command on any |
| `pipeline.TheCheckOfTheDockerDaemonStartsNothingTest.neitherContainerHereAsksNothingAndTheCheckTakesNoLock` | §3, §10: neither here, no answer and no command; a lock folder that is a file refuses nothing |
| `ui.TheWindowChecksAndStartsNothingAsItOpensTest.asItOpensTheWindowChecksWhatItsServicesFieldsHoldAndTheModelsInForceAndStartsNothing` | §1, §3, §12: a check for the two addresses and the image as the Services fields read, which start from the launch values held, and the models `profile.yaml` and `application.yaml` name; no start; `outcome` empty |
| `ui.TheWindowChecksAndStartsNothingAsItOpensTest.theCheckIsAListOfFiveRowsInTwoColumnsInTheHeaderBetweenTheTitlesAndTheThemeSwitch` | §2: the node's place in the header, the five names in two columns, each mark's text and style class, no taller than the titles |
| `ui.TheWindowChecksAndStartsNothingAsItOpensTest.aRowNotAskedReadsADash` | §2: both containers on another machine, the daemon *–*; Ollama *KO*, both models *–* |
| `ui.TheWindowChecksAndStartsNothingAsItOpensTest.itChecksAgainEveryFiveSecondsAndAtOnceAfterAStartAShutDownAndAnInvocationEnding` | §4: two regular checks 5 s apart; a check within a second of a start, a shut-down and the child's ending |
| `ui.RunAndAutoLabelWaitForTheContainersTest.runIsOffUntilTheDaemonDoclingAndOllamaShowOkAndItsCaptionSaysWhy` | §6, §8: Docling *KO*, Run off and its caption; all *OK*, on; containers on another machine with the daemon *–*, on |
| `ui.RunAndAutoLabelWaitForTheContainersTest.runsCaptionSaysItWaitsForTheFirstCheckAndForAStartOrAShutDown` | §6, §8: the first check held, Run off and *Off until the containers have been checked.*; a start held, *Off while Docker and the containers start.*, ahead of the daemon's row; a shut-down held, *Off while Docker shuts down.* |
| `ui.RunAndAutoLabelWaitForTheContainersTest.aMissingModelHoldsRunOnlyWhereModelsPullIsOff` | §6: a model *KO*, Run on with the box ticked, off with it unticked, and its caption |
| `ui.RunAndAutoLabelWaitForTheContainersTest.autoLabelIsHeldByOllamaAndTheGenerativeModelAlone` | §6: Docling *KO* holds Run and not Auto Label; Ollama *KO* holds both; the embedding model *KO* with pull off holds Run and not Auto Label |
| `ui.TheStartDockerButtonTest.itStandsImmediatelyBeforeShutDownDockerAndStartsOnceForWhatTheServicesFieldsHold` | §5: its words and place; one start, for the two addresses and the image as the Services fields read; a check at once after |
| `ui.TheStartDockerButtonTest.itIsOffWhileEverythingHereAnswersWhileItRunsWhileAnInvocationRunsAndForContainersOnAnotherMachine` | §2, §5: each condition of *Disabled*, the first check held, a start, a download and a shut-down each held, and an invocation running, and on where the daemon is *KO*; the daemon's row a dash before the first check |
| `ui.TheStartDockerButtonTest.afterAStartAMissingModelIsDownloadedWhereModelsPullIsOn` | §5, §7: ticked, the panel opens and the model is downloaded; unticked, nothing is downloaded |
| `ui.TheDownloadPanelTest.clickingAModelsKoDownloadsEachMissingModelOneAtATime` | §4, §7: the panel's rows, words and bars; the second download asked only once the first is done; a check within 2.5 s of the first one's end, not only the last's; Run off with its caption |
| `ui.TheDownloadPanelTest.hideKeepsItDownloadingTheRowReadsKoAndItsPercentAndClickingItReopensThePanel` | §2, §7: hidden, not cancelled, *KO ↓ 50%*, the list's width and column unmoved; clicked, shown again; the panel moves nothing |
| `ui.TheDownloadPanelTest.cancelStopsTheDownloadAndClosesThePanel` | §7: cancelled, panel gone, nothing more downloaded, a check at once |
| `ui.TheDownloadPanelTest.aLostConnectionReopensThePanelWithTheReasonAndRetryDownloadsAgain` | §7: hidden, a lost connection shows it with the reason and Retry; Retry asks the same model again |
| `ui.TheDownloadPanelTest.whenEveryModelIsDownloadedItOffersCloseAndTheWindowChecksAgain` | §4, §7: done, Close offered and pressed; a check at once |
| `ui.TheDownloadPanelTest.eachDownloadIsBoundedByTheStalledAtMostCommandOptionsHolds` | §7, §12: Command options' `vespera.models.pull-stalled-at-most` set to 42 s, both downloads given 42 s |
| `ui.ExplanationsAreCaptionsOnMouseOverTest.eachButtonOfTheActionRowStandsOnAWrapperThatCarriesItsCaption` | §8, §12: the row's wrappers in order, each holding its control and its caption, never disabled with it, and of its control's size with the control at 0,0, in the action row and in the Labels section |
| `ui.ExplanationsAreCaptionsOnMouseOverTest.eachHeaderRowsCaptionSaysWhatItIsAndWhereItWasFound` | §8, §10: each row's caption, here, on another machine, not resolved |
| `ui.ExplanationsAreCaptionsOnMouseOverTest.theTextsThatExplainedAControlAreNowItsCaption` | §8: each text of the table gone from the window and the caption of its control |
| `ui.ExplanationsAreCaptionsOnMouseOverTest.aStepsStopCarriesWhatAStopThereCosts` | §8: the running step's Stop carries the cost, and no text of its row says it |
| `ui.TheShutDownDockerButtonTest.startDockerShowsTheLineOfAStartThatWasRefused` | §5: a refused start's line in `outcome`; one start, for what the Services fields hold |
| `ui.TheWindowAsksWhatItsServicesFieldsHoldTest.anAddressCorrectedInItsFieldIsAskedAtTheNextCheckItsRowReadsOkAndRunIsOn` | §3, §6: the launch values held name a Docling and an Ollama where nothing listens, both *KO* and Run off; each field set to a container answering on loopback, the next check asks it there, its row reads *OK* with its address in its caption, the daemon is asked for the fields' addresses, and Run and Auto Label are on; the launch values held unchanged |
| `ui.TheWindowAsksWhatItsServicesFieldsHoldTest.aBlankFieldIsCheckedAtWhatTheSourcesNameAndNotAtTheStoredAddress` | §3: the launch values held naming addresses where nothing listens and the environment naming two that answer, both fields blanked, both rows *OK* and Run on |
| `ui.TheWindowAsksWhatItsServicesFieldsHoldTest.startDockerIsGivenWhatTheFieldsHoldAndClosingStopsForTheLaunchValuesHeld` | §1, §5, §12: Start Docker given the addresses typed and the GPU image chosen; pressed again with Ollama's field blank, given it blank; the window closed, the containers stopped once for the launch values held, which neither start changed |
| `ui.TheWindowAsksWhatItsServicesFieldsHoldTest.theWindowsDownloadGoesToTheOllamaItsFieldHolds` | §7, §12: a model's *KO* clicked, the download given Ollama's address as its field holds, not the one held |
| `ui.TheCheckAsksEachContainerAndOllamasListOfModelsTest.aContainerIsOkWhereItAnswersTwoXxWithinTwoSecondsAndKoOtherwise` | §3, §12: Docling's `GET /health` answering 204 and Ollama's `GET /api/version` 200, each *OK* on this machine at the address held; 503 and 404 *KO*; 200 after half a second *OK*; 200 after four seconds *KO*, each given up at its 2 s; the daemon asked once for the two addresses, its answer the row's |
| `ui.TheCheckAsksEachContainerAndOllamasListOfModelsTest.aContainerWhoseHostCannotBeResolvedIsKoAndNotAsked` | §3, §10: both under `.invalid`, each *KO* and not resolved, neither asked, seen through a proxy the test routes `.invalid` to; the models and the daemon a dash |
| `ui.TheCheckAsksEachContainerAndOllamasListOfModelsTest.theModelsAreNotAskedWhileOllamaIsKo` | §3: Ollama answering 503, both models a dash and `/api/tags` never asked; the daemon not answering *KO* |
| `ui.TheCheckAsksEachContainerAndOllamasListOfModelsTest.anUntaggedNameMatchesItsLatestAndATaggedNameOnlyItself` | §3: an untagged name matches its `:latest`, a tagged one only itself; a registry's port before the last `/` is no tag; a tag that only begins a listed one is *KO*; `/api/tags` asked once; no name, a dash |
| `ui.TheCheckAsksEachContainerAndOllamasListOfModelsTest.aModelsNameMatchesOllamasListWithoutRegardToCase` | §3: Ollama listing `An-Embedder:latest` and `A-Writer:7B`, `an-embedder` and `a-writer:7b` *OK*; names listed in no case *KO* |
| `ui.TheWindowsDownloadOfAModelTest.itPostsTheModelsNameAloneAndSumsWhatEachLayerReportsUntilSuccessIsDone` | §7, ADR-329 §4: one `POST /api/pull`, its body `{"model": <name>}` alone, the name's case kept; `completed` and `total` summed over the layers named so far; `success` told last, done |
| `ui.TheWindowsDownloadOfAModelTest.anErrorLineOrAStatusThatIsNotTwoXxIsLostWithItsReason` | §7: an `error` line lost with the bytes it had and Ollama's words; a 400 before any line lost, the one thing told, with Ollama's words |
| `ui.TheWindowsDownloadOfAModelTest.aCancelClosesTheConnectionAndTellsNothing` | §7: cancelled while Ollama repeats a line, the connection closed and nothing more told |
| `ui.TheWindowsDownloadOfAModelTest.aCancelBeforeTheDownloadHasBegunSendsNoRequestAndTellsNothing` | §7, §12: handed to an executor that holds it, cancelled, then run: no request and nothing told |
| `ui.TheWindowsDownloadOfAModelTest.aDownloadThatHasNotMovedForItsBoundIsLost` | §7, ADR-358: Ollama silent, or repeating a line unchanged, past a 2 s bound, lost with the bytes it had, no sooner than the bound and within it and the margin of the stub's read; the connection closed |

Amended:

- **`ui.TheShutDownDockerButtonTest.theEngineIsStartedOnceAsTheWindowOpens`** is renamed `startDockerShowsTheLineOfAStartThatWasRefused`, above, and presses *Start Docker*; **`aStartThatWentThroughShowsTheLineItSaid`** presses it too; **`theButtonWarnsFirstAndRunsNothingWhenKept`** reads the row of wrappers.
- **`ui.TheControlPanelsDockerSettingsTest.aConverterTheEnvironmentNamesOnAnotherMachineStartsNothingWhereTheLaunchValuesAreBlank`** and **`onWindowsTheWindowsStartWritesItInTheProfileFolderItIsGiven`**: the start brings the containers up, so containers named on loopback ports where nothing answers, the settings naming the repository's compose folder (§12) and the environment the image `compose.yaml` builds, make a start refused at the sidecar lock in a folder that is a file, its line pinned whole; **`theDistributionAndTheWaitAreReadFromTheSourcesInTheirOrder`** reads the settings' compose folder as null, the folder beside the jar.
- **`ui.TheControlPanelsWindowTest.theLabelCommandsAreTheSidebarsLabelsSection`** and **`theWindowSaysLabelWhereItSaidAnswer`** read ADR-413 §3's words as captions.
- **`ui.TheWorkingDirectoryIsChosenFromAListTest.runWarnsOfAWorkingDirectoryNamedForAnotherRootAndStarts`** waits for Run to be enabled by the first check.
- **`pipeline.DockerShutDownTest.closingStopsTheSidecarsWhereEitherAnswersAndNothingWhereOneIsOnAnotherMachine`**, its name kept as ADR-395 cites it, its words and claims now §10's: one container on this machine and answering, the other on another machine, the stop runs; the one here not answering, or both on another machine, it runs nothing. The hosts on another machine are resolved by the test's own resolver.
- **`pipeline.SidecarStartTest.theControlPanelsStartStartsTheEngineAloneAndAsksNoSidecar`**, its name kept as ADR-390 and ADR-410 cite it though the window no longer calls `startTheEngine`: Ollama alone on this machine, the engine is started for it; both on another machine, nothing is run (§10). The hosts on another machine are resolved by the test's own resolver.
- **`pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.nothingIsWrittenOffWindowsOrForSidecarsOnAnotherMachine`**, its name kept as ADR-421 cites it: on Windows nothing is written, and no command run, where both containers are on another machine, since one here sees to `.wslconfig` (§10). The hosts are resolved by the test's own resolver.
- **`ui.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText`** lays out and measures `outcome`, wrapped beneath the header, where it measured `hint-vespera.working-dir`, which §8 removed, and reads `status-vespera.corpus-root` as a line and `seed-folder-outcome` as a wrapping text.
- **`RecordingDockerControl`** answers checks and downloads as a test scripts them, keeps the bound each download is given, and holds a check, a start or a shut-down until the test releases it; **`ScriptedSidecarHealth`** counts each container's questions apart.

What no unit test shows is what `wsl.exe`, `systemctl` and Colima do with §3's commands, and what Ollama does with a closed pull; the documentation is the evidence (Read).

## What the implementation owes

Beside turning the tests above green: `ThisMachine` and its use in `SidecarStarter`'s step 2, step 3, step 8, `up`, `keepTheWslDistributionUp` and `stopTheSidecars`; `HostSystem`'s commands of §3 and `composeUp` naming a service; `SidecarStarter.engineAnswersStartingNothing`; `DockerEngine`'s three methods and its `of` naming the compose folder; `DockerControl`'s `check`, `download` with its bound, the window giving `check`, `start` and `download` what the Services fields read as each begins and `stopTheSidecars` the launch values held (§12), `Models`, `Downloaded`, `Settings.composeFolder`, `inProcess` and its form taking `engineAnswers`; the check's model names compared without regard to case; `ModelPull.start` taking an `Executor`; `OptionsPane`'s reading of `vespera.models.pull-stalled-at-most` for the window's downloads; `DockerCheck`; the window's header list, *Start Docker*, the download panel, the wrappers of their control's size and captions, the check's clock, and the enabling of Run, Auto Label and *Start Docker*; `control-panel.css`'s rules for the list, its marks and the panel; `PipelineHoldsOnlyTheRulesOnRecordTest`'s line for `ThisMachine`; and every citation of this record in the code naming it ADR-430.
