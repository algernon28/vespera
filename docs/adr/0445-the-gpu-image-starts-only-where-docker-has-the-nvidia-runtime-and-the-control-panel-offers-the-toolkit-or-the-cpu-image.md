# ADR-445 — The GPU image starts only where Docker has the nvidia runtime, and the control panel offers the toolkit or the CPU image

- **Date**: 2026-11-23
- **Status**: accepted on 2026-11-23.
- **Release**: minor
- **Decided**: by the operator, in [#860](https://github.com/algernon28/vespera/issues/860):
  - that the control panel installs what the GPU image needs in Docker, where ADR-410 rejected installing anything (§6);
  - that NVIDIA's cards alone are used: a machine whose card is AMD's or Intel's is told so in one line and runs on the CPU image, and macOS stays on the CPU image (§3);
  - that the GPU stays all or nothing under `vespera.docling.image`, ADR-323 §2 unchanged (§1);
  - that the control panel never falls back to the CPU image silently: the operator confirms, the confirmation saying that the CPU image is another extractor identity, so that every stage from extraction on starts new runs, and offering the install as the other answer (§5);
  - that `vespera run` and `vespera label --auto`, which nobody can answer, refuse in one line naming the fix and the CPU image (§2);
  - that on Linux the control panel asks for the password `sudo` wants, in a field that does not show it, for that one install alone, keeping it nowhere and handing it to `sudo` on its standard input; on Windows the install runs through `wsl -u root`, with no password (§6, §8).

  Under the operator's delegation:
  - that the check runs only for the GPU image, after the engine answers and Compose and buildx are found, before `up`, under the containers' lock, and is whether Docker lists an `nvidia` runtime, with no probe container (§1);
  - that the driver's version and the card's compute capability only warn (§4);
  - that the control panel shows it in the Preflight section and as *Start Docker*'s question (§5);
  - that the Preflight section says it in rows of ADR-440's shape, asked once while the Docker daemon answers and not at every check (§5);
  - that the install's lines, like the start's, are shown in the output panel as each is logged, as ADR-437 has a press's lines shown (§5, §6);
  - that the install is the NVIDIA Container Toolkit alone, at a version the jar pins, behind one confirmation listing every command (§6);
  - that on Linux each command runs through `sudo -k -S`, so that `sudo` reads the password line itself at every command and none is left for the command (§6, §8);
  - that the install holds the containers' lock while its commands run, as the stop and the shut-down do, and lets it go before the start runs again (§6);
  - the install's line where its lock cannot be taken or a command's output file cannot be written or read (§6);
  - that the start run again after *Use the CPU image* reads the Services fields as they hold when it runs (§5);
  - that an answer of the card still being asked when a check finds the daemon not answering is not kept (§5);
  - that a confirmed fallback sets the converter's image field to the CPU image (§7);
  - the commands, their limits and the lines (§1 to §6);
  - the Services section's words (§9);
  - the seams (§10);
  - what stays out (*What this does not decide*).
- **Amends**: [ADR-410](0410-a-missing-docker-compose-or-buildx-is-named-with-the-command-that-installs-it-before-up.md):
  - its rejected alternative *"Install them. Nothing of Vespera's installs a package or runs under `sudo` (ADR-047); what a machine has installed is the operator's"* holds of everything but the NVIDIA Container Toolkit, which the control panel installs once the operator confirms (§6), on Linux through `sudo` with the password the operator types (§8);
  - its *Keeps* of ADR-047, *"nothing is installed"*, the same.
  - §2: once Compose and buildx are found, and only for the GPU image, the nvidia runtime is asked for before `up` (§1).

  It carries a note at its head.
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md):
  - §1's one-time setup, *"the operator's to make and Vespera's to neither write nor read"*, as ADR-421 left it: NVIDIA's container toolkit, which its Consequences name for a GPU on Windows, is checked before the GPU image starts (§1) and installed by the control panel once the operator confirms it (§6);
  - §2 and §3's *"Nothing is run under `sudo`"*: nothing is, but the install's commands on Linux (§6).

  It carries a note at its head.
- **Amends**: [ADR-421](0421-on-windows-vespera-writes-wslconfig-with-no-idle-timeout-where-there-is-none-and-changes-no-file-that-is-there.md)'s Consequence *"The one-time setup on Windows is Docker Engine, with Compose and buildx, and `systemd=true`"*: for the GPU image, the NVIDIA Container Toolkit too, which the control panel installs once the operator confirms it (§6). `.wslconfig` is no longer the one file Vespera writes outside the working directory and the deliverable root: the install writes three more inside the WSL distribution or on the Linux machine (§6). It carries a note at its head.
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md):
  - §3: between ADR-410's checks and step 7, the GPU image only, the nvidia runtime is asked for, and on macOS the GPU image is refused before step 5 (§1, §3);
  - §8: two lines of its own, `NvidiaRuntimeIsMissingException`'s and `NoNvidiaCardException`'s (§2, §3).

  It carries a note at its head.
- **Amends**: [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md):
  - §5: a start of *Start Docker* refused for the graphics card asks the operator, in place of showing its line (§5);
  - §12: `DockerEngine`, `DockerControl` and `Dialogs` gain members, `DockerEngine`'s still of `java.base` types and itself (§10).

  It carries a note at its head.
- **Amends**: [ADR-437](0437-start-dockers-and-shut-down-dockers-lines-are-shown-in-the-output-panel-as-each-is-logged.md):
  - §1: a press of *Start Docker* shows too the lines the toolkit's install logs and §4's warnings, and the lines of a start run again after the question (§5, §6);
  - §4: the press's line, shown last, is §5's;
  - §7: `ContainerStarter.telling` tells the install's lines and the warnings, and `DockerEngine.installTheNvidiaToolkit` and `DockerControl.installTheNvidiaToolkit` take a `Consumer<String>` (§10).

  It carries a note at its head.
- **Amends**: [ADR-440](0440-the-preflight-section-is-the-folders-and-their-holders-a-row-each-of-a-name-a-mark-and-words-a-holder-said-once.md):
  - §1: after `docling-image`, a row `graphics-card` and a row for each warning of the card, each present only where §5's condition holds;
  - §2: their marks and words (§5);
  - §8: the window asks `DockerControl.graphicsCard` once while the daemon answers, and keeps the answer (§5, §10).

  It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md)'s words of the converter's image, *GPU: needs an NVIDIA graphics card. Much faster.* and *CPU: runs on any machine. Slower.* (§9). It carries a note at its head.
- **Keeps**:
  - [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §2: `vespera.docling.image` alone chooses the compose files; no setting of a GPU of its own is added.
  - ADR-323 §4's `up`, with `--build` and `--no-recreate`, and its never-run list, `pull` among them.
  - [ADR-170](0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md) §3 and [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md): the image the operator names is the extractor's identity.
  - [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md): `compose.gpu.yaml`'s one device request, for driver `nvidia`.
  - [ADR-354](0354-the-docling-images-are-built-where-they-run-from-the-one-containerfile-and-no-workflow-pushes-them-to-a-registry.md): the images are built where they run, and pushed nowhere.
  - [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1: nothing of Docker is a child of the control panel.
  - [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §4: the control panel names `DockerEngine` alone of the jar.
  - [ADR-433](0433-docling-and-ollama-are-containers-where-vespera-runs-them-and-never-sidecars-and-the-earlier-names-are-still-honoured.md): the word is container, its §3 names, and its §6 lock, `containers.lock` with the earlier file beside it.
  - [ADR-437](0437-start-dockers-and-shut-down-dockers-lines-are-shown-in-the-output-panel-as-each-is-logged.md) §2's cleaning of what a command printed, and §4's emptying of the panel at the press and by the next child.
  - [ADR-440](0440-the-preflight-section-is-the-folders-and-their-holders-a-row-each-of-a-name-a-mark-and-words-a-holder-said-once.md): the section has no Check, and says nothing of Docling's or Ollama's answers.
  - [ADR-397](0397-the-yaml-configuration-lies-beside-the-jar-and-the-jar-writes-its-shipped-copy-where-a-file-is-missing.md): the files beside the jar are never written over.
  - [ADR-047](0047-the-pipeline-never-blocks.md): `vespera run` and `vespera label --auto` prompt nothing, and the control panel asks only on the operator's press.
- **Settles**: [#860](https://github.com/algernon28/vespera/issues/860).
- **Rests on**: the documentation and source read below, all on 2026-11-20; nothing was measured.

## Context

A first `vespera run` on a new Windows machine, with Docker Engine in a WSL 2 distribution as ADR-390 §1 has it and the GPU image configured, stopped at `up`:

```
Error response from daemon: could not select device driver "nvidia" with capabilities: [[gpu]]
```

`nvidia-smi` listed the card inside the distribution. The NVIDIA Container Toolkit was not installed, so the engine had no `nvidia` device driver for `compose.gpu.yaml`'s request (ADR-165). The line reached the operator as ADR-323 §8's line of a failed `up`, which names the command and its last line, and nothing else. The operator installed the toolkit by hand, as root in the distribution.

- **ADR-390's Consequences already named the requirement**, *"a GPU there also needs NVIDIA's container toolkit in the distribution, as on Linux"*. Nothing checked it.
- **The README said the opposite.** Its words were *"On Windows, Docker inside WSL 2 can use one; on Linux, Docker needs NVIDIA's Container Toolkit"*. That was true of Docker Desktop, which ADR-390 removed.
- **The window as it stands.** The Preflight section is a grid of rows, a name, a mark and words each, and holds no Check: the header's check, every 5 s, is the one place Docling and Ollama are said (ADR-440). A press of *Start Docker* empties the output panel and shows each line the start logs, its own line last (ADR-437).

### Read, not measured

- **The NVIDIA Container Toolkit.** NVIDIA's [install guide](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) installs it on Ubuntu and Debian in these steps:
  - add the signing key from `https://nvidia.github.io/libnvidia-container/gpgkey`, dearmored into `/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg`;
  - add the source list from `https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list`, with that keyring written in, to `/etc/apt/sources.list.d/nvidia-container-toolkit.list`;
  - `apt-get update`;
  - install `nvidia-container-toolkit`, `nvidia-container-toolkit-base`, `libnvidia-container-tools` and `libnvidia-container1`, each pinned at `1.20.2-1` in the guide's commands;
  - `nvidia-ctk runtime configure --runtime=docker`, which updates `/etc/docker/daemon.json`;
  - `systemctl restart docker`.

  Every step is root's. The toolkit is under the Apache 2.0 licence ([repository](https://github.com/NVIDIA/nvidia-container-toolkit)).
- **CUDA on WSL.** NVIDIA's [CUDA on WSL guide](https://docs.nvidia.com/cuda/wsl-user-guide/index.html) says:
  - only the Windows driver is installed, and *"you must not install any NVIDIA Linux GPU driver within WSL"*;
  - Docker is supported with the toolkit through the Docker command line or Docker Desktop;
  - with the toolkit, *"only --gpus all is supported"*;
  - inside WSL, `nvidia-smi` is at `/usr/lib/wsl/lib/nvidia-smi`, off the root user's path.
- **The engine's NVIDIA device driver.** Moby's `daemon/devices_nvidia_linux.go` registers the device drivers that `--gpus` and a Compose device request select. It does so only where `nvidia-cdi-hook` or `nvidia-container-runtime-hook` is on the daemon's path when the daemon starts; where neither is, nothing is registered, and a request for driver `nvidia` fails as the Context's line did. Both hooks come with the toolkit's packages.
- **What the engine lists.** `docker info --format {{json .Runtimes}}` gives the runtimes the daemon was configured with, as one JSON object keyed by name. `nvidia-ctk runtime configure` adds `nvidia` to it, and the restart that follows also registers the device driver.
- **The CUDA 12.8 floor.** The [CUDA 12.8 release notes](https://docs.nvidia.com/cuda/archive/12.8.0/cuda-toolkit-release-notes/index.html), Table 3, give the GA release's least driver as 570.26 on Linux and 570.65 on Windows. Maxwell, Pascal and Volta are feature-complete there, not removed.
- **What the GPU image runs.** docling-serve's [README](https://github.com/docling-project/docling-serve) builds `docling-serve-cu128` with PyTorch from the cu128 index.
  - PyTorch removed Maxwell and Pascal from its CUDA 12.8 builds from release 2.8 ([2025-07-14](https://dev-discuss.pytorch.org/t/cuda-toolkit-version-and-architecture-support-update-maxwell-and-pascal-architecture-support-removed-in-cuda-12-8-and-12-9-builds/3128)), and Volta from release 2.11 ([notice](https://dev-discuss.pytorch.org/t/notice-cuda-12-6-wheels-will-no-longer-be-published-from-pytorch-2-15-drops-maxwell-pascal-volta/3432)).
  - So the converter on the GPU image wants compute capability 7.5 or later, Turing or newer. Which PyTorch release docling-serve v1.32.0 carries was not read.
  - `docling-serve-cu126` has no v1.32.0 tag (ADR-170), so no image at the pinned release serves an older card.
- **AMD and Intel.** These are left to [#911](https://github.com/algernon28/vespera/issues/911) and [#912](https://github.com/algernon28/vespera/issues/912).
  - docling-serve publishes no ROCm image: one is *"supported but not published due to its large size"*, on ROCm 6.3 (README).
  - ROCm on WSL 2 reaches the card through `/dev/dxg` and ROCDXG, from ROCm 7.1, for a list of Radeon RX 9000, RX 7000 and Radeon PRO cards ([AMD's WSL matrix](https://rocm.docs.amd.com/projects/radeon-ryzen/en/latest/docs/compatibility/compatibilityrad/wsl/wsl_compatibility.html), [ROCDXG](https://github.com/ROCm/librocdxg)).
  - Ollama's ROCm image is documented with `/dev/kfd` and `/dev/dri` alone, and its pages do not mention WSL ([Ollama in Docker](https://docs.ollama.com/docker)).
  - Docling's own GPU page names CUDA alone ([Docling GPU](https://docling-project.github.io/docling/usage/gpu)).
- **PCI vendor ids.** `10de` is NVIDIA's, `1002` AMD's and `8086` Intel's. Windows' `Win32_VideoController` gives each display adapter's `PNPDeviceID`, which names the vendor as `VEN_<id>`. Linux gives each PCI device's class and vendor in `/sys/bus/pci/devices/*/class` and `vendor`; a display controller's class begins `0x03`.

### Not measured

- the install's commands run through `wsl -u root --exec` or `sudo -k -S`;
- `bash -c` given its script through `wsl.exe`'s command line;
- that `nvidia-smi` inside WSL reports the Windows driver's version;
- `--query-gpu=compute_cap` on a driver older than the query;
- `apt-get` behind a proxy;
- the PowerShell query on a Windows whose language is not English, of which only the `VEN_` ids are read.

## Decision

### 1. The nvidia runtime is asked for before `up`, for the GPU image alone

Where `vespera.docling.image` is the image `compose.gpu.yaml` builds, so that `up` names that file (ADR-323 §2), the start runs this order. It is the same for `vespera run`, for `vespera label --auto` and for the control panel's *Start Docker*:

1. the engine is made to answer (ADR-323 §3 step 6);
2. Compose and buildx are found (ADR-410 §2);
3. the nvidia runtime is asked for;
4. `up` is run (step 7).

All of these hold the containers' lock, `containers.lock` with the earlier `sidecars.lock` kept beside it (ADR-323 §6, ADR-433 §6). The command:

| System | The check |
|---|---|
| Windows | `wsl [-d <distribution>] -u root --exec docker info --format {{json .Runtimes}}` |
| Linux | `docker info --format {{json .Runtimes}}` |

- **How its answer is read.** Listed where it exits 0 and its output holds `"nvidia":`, the runtime's name as a key of the object. Any other answer is not listed: another output, another exit, or a command killed at its limit.
- **Killed after 30 s**, the engine check's limit (ADR-390 §2).
- **Failures.** A program that cannot be started is Docker not installed (ADR-390 §6). An output file that cannot be written or read refuses in ADR-326's line.
- **Not run at all** for the CPU image, or for any start that ends before the engine is asked: a container on another machine, every container answering, or an image no compose file builds (ADR-323 §3, ADR-430 §10).
- **No probe container.** No `docker run --gpus all … nvidia-smi` is run: it would need an image to be pulled or built first, and ADR-323 §4 runs no `pull`.
- **Not told, and logged nowhere**: it is a command run only to ask, as ADR-437 §1 has the engine check.

**Why the runtime and not the device driver.** The request fails where the daemon registered no `nvidia` device driver, and nothing the engine lists says whether it did (Read). The runtime is what NVIDIA's own steps add, in the same change that installs both hooks and restarts the daemon, which is when the driver is registered. So a runtime listed is an engine that took the toolkit in, and a runtime missing is an engine the install fixes. The case where the runtime is missing but the device driver is registered is left open (*What this does not decide*).

**Why `docker info`, which ADR-410 rejected for plugins.** ADR-410 rejected it because Compose and buildx are the client's, and the client answers for each without asking the engine. A runtime is the engine's, and only the engine says which it has. The engine has just answered. The one thing read is a key's name.

### 2. A runtime missing refuses the start in one line

Where the runtime is not listed, the graphics cards are read (§3), and the start is refused before `up`. Where an NVIDIA card is among them, or none could be read, the refusal is `NvidiaRuntimeIsMissingException`, extending `ContainerStartRefusedException`, one line, exit 1:

> The graphics card cannot be used: the Docker engine &lt;where&gt; answers but has no nvidia runtime, which the NVIDIA Container Toolkit gives it. Install the toolkit, which Start Docker in the control panel offers to do, or as NVIDIA's guide at https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html has it; or set VESPERA_DOCLING_IMAGE to &lt;cpu image&gt;, which converts on the processor and starts new runs from extraction on; and run the same command again.

`<where>` is one of:

- `in the WSL distribution <distribution>`, as ADR-410 §3 names it;
- `in WSL's default distribution`;
- `on this machine`.

`<cpu image>` is the image `compose.yaml` builds, read where ADR-323 §2 reads it.

**The command line.** `vespera run` and `vespera label --auto` print the line on standard error and exit 1 before the job or the labeller, as every line of ADR-323 §8 does. Nobody is there to answer a question, so nothing is installed and no image is changed. The line names both ways on.

### 3. NVIDIA's cards alone, and the others told so

**Reading the cards.** Only where the runtime is not listed, on Windows and Linux. Each command is killed after 30 s:

| System | The command | What is read |
|---|---|---|
| Windows, on Windows itself | `powershell -NoProfile -NonInteractive -Command "Get-CimInstance Win32_VideoController \| ForEach-Object PNPDeviceID"` | each `VEN_<id>`, in any case |
| Linux | `sh -c 'for d in /sys/bus/pci/devices/*; do case "$(cat "$d/class")" in 0x03*) cat "$d/vendor";; esac; done'` | each line `0x<id>` |

The command is run as one argument after `-Command`, and the script as one argument after `-c`, with no shell of Vespera's around either. An exit other than 0, or a program that cannot be started, reads no card: as for an NVIDIA card, line §2's is given. Neither command is told.

**No NVIDIA card**, where the cards were read and none is NVIDIA's: `NoNvidiaCardException`, extending `ContainerStartRefusedException`, one line, exit 1:

> The graphics card cannot be used: &lt;the cards&gt;, and Vespera runs the containers on NVIDIA cards alone. Set VESPERA_DOCLING_IMAGE to &lt;cpu image&gt;, which converts on the processor and starts new runs from extraction on, and run the same command again.

`<the cards>` is one of:

- `this machine's graphics card is AMD's`;
- `this machine's graphics card is Intel's`;
- `this machine's graphics cards are AMD's and Intel's`;
- `no NVIDIA graphics card was found on this machine`, where none of the three is read.

**macOS.** Colima's engine reaches no card. The GPU image is refused as soon as it is found to be `compose.gpu.yaml`'s, before the containers' lock and before any command, in `NoNvidiaCardException`'s line with `<the cards>` reading `on macOS Docker reaches no graphics card`.

**Why not detect AMD or Intel and use them.** The operator decided NVIDIA alone. No published docling-serve image runs on either, and ROCm under WSL reaches the card another way than Ollama's image documents (Read; #911, #912).

### 4. The driver and the card warn, and refuse nothing

Where the runtime is listed, before `up`, the cards are asked, killed after 30 s:

| System | The query |
|---|---|
| Windows | `wsl [-d <distribution>] -u root --exec /usr/lib/wsl/lib/nvidia-smi --query-gpu=name,driver_version,compute_cap --format=csv,noheader` |
| Linux | `nvidia-smi --query-gpu=name,driver_version,compute_cap --format=csv,noheader` |

Each line of its output is one card: its name, its driver's version and its compute capability, split at `, `. Two lines are possible for each card, logged at WARN by the starter and told, as ADR-437 §1 tells the `.wslconfig` line, and the start goes on:

- **A driver under the floor**, 570.65 on Windows and 570.26 on Linux (CUDA 12.8 GA, Read), the versions compared number by number:
  > The NVIDIA driver of &lt;name&gt; is &lt;version&gt;, and the GPU image's CUDA 12.8 needs &lt;floor&gt; or later, so the containers may not start on the card: update the driver&lt;, on Windows,&gt; or choose the CPU image.

  The words `, on Windows,` are there on Windows alone.
- **A compute capability under 7.5**:
  > &lt;name&gt; has compute capability &lt;capability&gt;, and the GPU image's converter needs 7.5 or later, a Turing card (GeForce RTX 20 or GTX 16) or newer, so it may not convert on the card: choose the CPU image if it does not.

**Nothing is said** where the query exits other than 0, cannot be started, or prints a line not of three parts or with a number that cannot be read. The query itself is not told.

**Why warn and not refuse.** Ollama runs on cards older than the converter does (Read). A driver Vespera read wrongly would refuse a machine that runs. The floors are read, and the operator's machine has the last word.

### 5. The control panel

**Start Docker.** Where *Start Docker*'s start (ADR-430 §5) is refused in one of §2's or §3's lines, the window asks the operator in place of showing the line. It asks under the title *The graphics card*, through `Dialogs.choose` (ADR-389 §4, ADR-423), with no header or icon.

- **Where the toolkit is offered**, the line §2's, the sentence is:

  ```
  <the line's first sentence>

  Install the toolkit runs these commands as root <where>[, asking for your password for sudo first]:

  <each command of §6, one a line>

  They add NVIDIA's package source and its signing key, install the NVIDIA Container Toolkit, write its nvidia runtime into /etc/docker/daemon.json and restart Docker. Restarting Docker stops every container running on it, Vespera's and any other, and only those set to restart come back.

  Use the CPU image runs the converter on the processor instead, and remembers that choice. The CPU image converts documents differently, so it is another converter: every stage from extraction on starts new runs, and a document it has not converted before is converted again.
  ```

  The details:
  - `<the line's first sentence>` is the line up to and including its first `. `'s period.
  - `<where>` is §2's, `on this machine` on Linux.
  - The bracketed words are there on Linux alone.
  - The answers are, in order, *Use the CPU image*, *Install the toolkit* and *Cancel*.
- **Where it is not**, §3's line, the sentence is the line's first sentence and the last paragraph above, and the answers are *Use the CPU image* and *Cancel*.

**What each answer does:**

- **Use the CPU image**: the converter's image field is set to the CPU image, as its *CPU* choice sets it (§7). The start then runs again, for what the Services fields hold now: read as it runs again, so a field the operator edited while the first start ran is the edited one, and not the values the press was given.
- **Install the toolkit**:
  - On Linux the window first asks for the password (§8). Cancelled, nothing is installed and the refusal's line is the press's.
  - The install runs (§6), on the press's thread. *Start Docker*, Run and Auto Label stay disabled while it runs, as while a start runs (ADR-430 §5, §6).
  - Where it is done, the start runs again; a start refused again for the graphics card is not asked about a second time, and its line is the press's.
  - Where it is not done, its line is the press's, and nothing more is run.
- **Cancel**, or the question closed with no answer: the refusal's line is the press's, as ADR-430 §5 shows a refused start's, and nothing else is run.

**The press's line**, shown in `outcome` and last in the output panel as ADR-437 §4 has it, is:

- where the start ran again, that start's line, or, where it has none, the install's line where an install ran, and none where none did, as a start that went through with no line shows none (ADR-430 §5);
- where an install ran and was not done, the install's line;
- otherwise, the question cancelled, closed or its password's field cancelled, the refusal's line.

**The output panel** is emptied once, at the press (ADR-437 §4), and holds, in order, the lines the first start told, the lines the install told where it ran, the lines the second start told where it ran, and the press's line last. The question and the password's field put nothing in it; the refusal is not told, as ADR-437 §3 has a refusal thrown and not told, and is in the panel only where it is the press's line.

The window checks at once once the press has returned (ADR-430 §4).

**The Preflight section.** After its rows of ADR-440 §1, `docling-image` among them, it gains, each present only where its condition holds:

| Row | Name | Present where | Mark | Words | Caption |
|---|---|---|---|---|---|
| `graphics-card` | Graphics card | the converter's image field, white space taken off, is the image its *GPU* choice sets, and the window keeps an answer of the card whose runtime is known | *OK* where the runtime is listed, *KO* where it is not | `Docker has the nvidia runtime`, or `Docker has no nvidia runtime; Start Docker offers to install the NVIDIA Container Toolkit` | none where *OK*; where *KO*, *The GPU image starts only where Docker has the nvidia runtime, which the NVIDIA Container Toolkit gives it.* |
| `graphics-card-warning-<n>`, from 1 | Warning | `graphics-card` is drawn and the answer kept holds a warning, one row for each of §4's lines, in their order | *KO* | the warning, whole | the warning, whole |

The marks and the grid are ADR-440 §2's; a warning's words are cut with an ellipsis where they are wider than the column, and the caption holds them whole.

**When the card is asked.** The section has no Check (ADR-440 §1), and the header's check every 5 s gains no question of the card. Instead:

- **Asked once.** When a check of ADR-430 §3 that found the Docker daemon *OK* comes back, the converter's image field names the GPU image and the window keeps no answer of the card, the window asks `DockerControl.graphicsCard` for the launch values that check was for, off the toolkit's thread, and keeps what it answers, a runtime unknown included.
- **Asked again** only after the answer kept is dropped: where a check comes back with the daemon other than *OK*, and where a press of *Start Docker* returns, an install among it.
- **An answer that comes back after a drop is not kept.** Where such a check comes back, or such a press returns, while the card is still being asked, the answer it gives is thrown away when it comes back, and the next check that finds the daemon *OK* asks again.
- **What is asked.** `graphicsCard` asks the engine only where ADR-430 §3's commands, which start nothing, find it answering. So it boots no WSL distribution and starts no engine. It asks the runtime (§1) and, where it is listed, the query of §4; it reads no card where the runtime is not listed. On macOS it asks nothing, and its runtime is unknown.

**Why once, and not at each check.** The answer changes only with an install or a restart of Docker, and `wsl -u root --exec docker info` twelve times a minute would ask that for nothing. A daemon that stopped answering, or a press, is when it may have changed.

### 6. The install

**What it is.** The NVIDIA Container Toolkit at **1.20.2-1**, the version NVIDIA's guide pinned when read, pinned in the jar. These commands are run in this order, each root's, each killed after **10 minutes**. They are written here as the confirmation lists them: the words of a command joined by spaces, a word holding a space between double quotes.

```
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl gnupg2
bash -c "set -euo pipefail; curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | gpg --batch --yes --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg"
bash -c "set -euo pipefail; curl -fsSL https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' > /etc/apt/sources.list.d/nvidia-container-toolkit.list"
apt-get update
apt-get install -y nvidia-container-toolkit=1.20.2-1 nvidia-container-toolkit-base=1.20.2-1 libnvidia-container-tools=1.20.2-1 libnvidia-container1=1.20.2-1
nvidia-ctk runtime configure --runtime=docker
systemctl restart docker
```

**How each is run:**

- **On Windows**, inside the distribution as root: `wsl [-d <distribution>] -u root --exec <command>`. Nothing is written on its standard input.
- **On Linux**, through `sudo`: `sudo -k -S <command>`. Its standard input is given the password the operator typed and one line end, an empty line where none was typed (§8).
- **On macOS** nothing is run, and the install answers *Vespera installs the NVIDIA Container Toolkit on Windows and Linux alone.*

**Why `-k`.** With no terminal, `sudo`'s default `timestamp_type=tty` keys its cached credentials on the parent process, which is the same JVM for all eight commands. Without `-k`, every `sudo` after the first would use the cache and read nothing. The password line would then stay on the standard input that `apt-get`, `dpkg`, `bash`, `nvidia-ctk` and `systemctl` inherit. `-k` given with a command makes `sudo` ignore the cache for that command, so it asks, and reads the line, every time.

**What `-k` leaves.** A sudoers rule granting the user `NOPASSWD` asks for no password, so `sudo` reads nothing and the line passes to the command's standard input. The commands read no password there; the line is the operator's own password, given to their own commands, and is not shown, logged or kept by Vespera.

**The lock.** The install holds the containers' lock, `containers.lock` with the earlier `sidecars.lock` beside it, as the stop of the containers and the shut-down do (ADR-390 §3, ADR-433 §6), from before its first command until it returns, done or not. Its last command restarts Docker, which no start, stop or other Vespera may run beside. It waits for its own lock held by another process, as they do. It lets both go as it returns, before the start that runs again takes them (§5). On macOS it takes none.

**`bash -c` for the two pipelines** is so that `pipefail` fails a download whose `curl` fails. A plain `sh` would give the exit of the last command of the pipe, and the key or the list would be written empty. The scripts are the jar's, and no value the operator gave is put into them.

**The first command that does not exit 0 ends the install.** That includes a command killed at its limit, and a program that cannot be started, which counts as exit 127 with its reason (ADR-390 §2). Nothing after it is run, and the line is:

> The NVIDIA Container Toolkit was not installed: &lt;command, as listed&gt; exited &lt;code&gt;: &lt;last line&gt;. Nothing after it was run.

**A lock that cannot be taken**, ADR-433 §6's earlier lock held or either file not opened or locked, ends the install before its first command, in:

> The NVIDIA Container Toolkit was not installed: &lt;why&gt;. Nothing was run.

`<why>` is the words ADR-390 §6's and ADR-433 §6's lines give the lock, as the shut-down's line has them: *an earlier version of Vespera holds the lock file &lt;path&gt;*, or the file that cannot be used and why.

**An output file that cannot be written or read** (ADR-326) for a command ends the install at that command, in:

> The NVIDIA Container Toolkit was not installed: &lt;why&gt;. Nothing after it was run.

`<why>` is ADR-326's words for the file.

**Done**, every command having exited 0:

> The NVIDIA Container Toolkit 1.20.2-1 is installed, and Docker was restarted with its nvidia runtime.

**What it writes outside Vespera's folders**, inside the WSL distribution on Windows and on the machine on Linux:

- the keyring `/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg`;
- the package source `/etc/apt/sources.list.d/nvidia-container-toolkit.list`;
- `/etc/docker/daemon.json`, which `nvidia-ctk` writes the runtime into;
- the packages `apt-get` installs, and its lists, refreshed twice;
- Docker's restart, which stops every container on the engine, Vespera's and any other.

Nothing of Windows is written, and no driver is installed.

**Not installed, ever:** Docker Engine, Compose or buildx (ADR-410's line names them), the WSL distribution, WSL itself, or a driver on Windows or Linux.

**What it logs and tells**, on the starter's logger at INFO, each line told as ADR-437 §3 tells a start's:

- before each command, `Installing the NVIDIA Container Toolkit: <command, as listed>`;
- once it has exited, or been killed at its limit, each line it printed, as ADR-437 §2 has them: NULs and byte-order marks taken out, trailing white space taken off, blank lines left out. A command whose program cannot be started prints nothing.

The done line and the line of an install not done are returned, and neither told nor logged by the install, as ADR-437 §3 has a refusal returned and not told. The password is in no line (§8).

### 7. The CPU image, confirmed, is the image field's

The operator's *Use the CPU image* sets the Services section's converter's image field to the CPU image, as its *CPU* choice does (ADR-344).

- **It is kept as a launch value.** The field's value is remembered with the next invocation the window starts (ADR-292, ADR-430 §12), so every later start and Run from the window is for the CPU image, until the operator chooses the GPU again.
- **Nothing is written beside the jar.** `application.yaml`, `application-local.yaml` and `.env` are not written (ADR-397).
- **A `vespera run` from the command line** reads its own sources, and still refuses in §2's or §3's line until `VESPERA_DOCLING_IMAGE` names the CPU image there.

**Why the field, and not this press alone.** A choice for one press would leave the next Run on the GPU image, which the same refusal stops, and would switch the extractor identity back and forth between invocations (ADR-170 §3). Kept, the identity stays as the operator confirmed it.

### 8. The password

On Linux, *Install the toolkit* first asks through `Dialogs.secret`:

- under the title *Install the NVIDIA Container Toolkit*;
- with the sentence *Your password, for sudo. Vespera hands it to sudo for this install alone and keeps it nowhere.*;
- with the answers *Install* and *Cancel*;
- in a field that does not show what is typed.

The details:

- **What the window holds.** What is typed comes to the window as a `char[]`, which the window hands to `DockerControl.installTheNvidiaToolkit` and does not keep. The field is cleared once the dialog closes.
- **Where it goes.** The install writes it, followed by a line end, on the standard input of each `sudo -k -S` it starts (§6), and nowhere else:
  - not in a command line or an environment variable;
  - not in a log line, a line told, the output panel, the invocation report or `effective-values.properties`;
  - not in the launch values file, the profile, or any file;
  - never shown in the window.
- **Once the install has returned**, done or not, every character of the array is set to `\0`.
- **What cannot be cleared.** JavaFX's `PasswordField` holds what is typed as a `String`. The field is cleared, and the string is left to the garbage collector; Java gives no way to clear it. The same holds of the bytes written to a command's standard input: the `Process`'s output stream is a `BufferedOutputStream` whose buffer keeps a copy of them, which no public member reaches, and which is left to the garbage collector with the process.
- **The jar holds no credential** (ADR-359's rule for the Confluence token stands): the password is typed into the window and used once.

**Why `sudo -k -S` on standard input.** A password on a command line is read by every process of the machine, and one in the environment by every child. On standard input it reaches `sudo`, which reads the line before it starts the command, `-k` making it read it at every command (§6); only under a `NOPASSWD` rule does it reach the command instead (§6). `-S` is how `sudo` takes it with no terminal.

### 9. Words

- **The Services section.** The converter's image is offered as *GPU: an NVIDIA graphics card, with NVIDIA's toolkit in Docker. Much faster.* and *CPU: any machine, AMD and Intel cards included. Slower.*
- **The README's "Running it"** loses *"On Windows, Docker inside WSL 2 can use one"*. It says:
  - that Docker needs NVIDIA's Container Toolkit for the card on Windows, inside the WSL distribution, as on Linux;
  - that Vespera asks for its runtime before it starts the containers on the GPU image, and refuses with how to get it;
  - that *Start Docker* offers to install it, asking for the `sudo` password on Linux, or to take the CPU image, and that the output shows the install as it runs;
  - that the Preflight section says whether Docker has the runtime;
  - that AMD and Intel cards run on the CPU image;
  - that the CPU image starts new runs from extraction on.

### 10. The seams

**`pipeline`:**

- `ProcessRunner` gains `ProcessOutcome runFeeding(List<String> command, Duration timeout, char[] input)`: as `run`, with `input` written on the command's standard input, encoded as UTF-8, and the input closed. A null `input` writes nothing and closes it.
- `NvidiaRuntimeIsMissingException` and `NoNvidiaCardException`, each extending `ContainerStartRefusedException` (§2, §3).
- `ContainerStarter` gains:
  - `String installTheNvidiaToolkit(char[] sudoPassword)`: §6, under the containers' lock, its line, done or not, the done line being `ContainerStarter.NVIDIA_TOOLKIT_INSTALLED`; the array cleared on return; a starter made by `telling` tells §6's lines;
  - `List<String> nvidiaToolkitCommands()`: §6's commands as listed, none on macOS;
  - `Optional<Boolean> nvidiaRuntimeListedStartingNothing()`;
  - `List<String> graphicsCardWarningsStartingNothing()`.

  The last two ask nothing where ADR-430 §3's check finds no engine answering, and tell nothing. `telling` (ADR-437 §7) tells §4's warnings in a start too.

**`DockerEngine`**, its surface still primitives, `java.base` types and itself (ADR-390 §4):

- `public static final String NVIDIA_TOOLKIT_INSTALLED`: §6's done line.
- `public static Optional<Boolean> offersTheNvidiaToolkit(String refusal)`: true for §2's line, false for §3's, and empty for any other line.
- `public String installTheNvidiaToolkit(char[] sudoPassword, Consumer<String> told)`: §6, `told` given each line it tells.
- `public List<String> nvidiaToolkitCommands()`.
- `public String whereTheToolkitIsInstalled()`: §2's `<where>`.
- `public boolean installAsksForAPassword()`: true on Linux.
- `public Optional<Boolean> nvidiaRuntimeListed(URI doclingBaseUrl, URI ollamaBaseUrl)` and `public List<String> graphicsCardWarnings(URI doclingBaseUrl, URI ollamaBaseUrl)`, for the Preflight section.

**`DockerControl`** gains:

- `record CardRefused(boolean offersTheToolkit, String why, List<String> commands, String where, boolean asksForAPassword)`;
- `Optional<CardRefused> cardRefused(String refusal)`;
- `Said installTheNvidiaToolkit(char[] sudoPassword, Consumer<String> told)`;
- `record CardCheck(Optional<Boolean> runtimeListed, List<String> warnings)`;
- `CardCheck graphicsCard(LaunchValues values)`.

`NONE` refuses nothing for the card, installs at once telling nothing, and checks nothing, its `CardCheck` of a runtime unknown and no warning.

**`Dialogs`** gains `Optional<char[]> secret(String title, String sentence, String yes, String no)`. `NONE` answers empty.

### 11. No run id moves

`NvidiaRuntimeIsMissingException`, `NoNvidiaCardException`, `ContainerStarter`, `HostSystem`, `ProcessRunner`, `SystemProcessRunner` and `DockerEngine` are in `pipeline`, which no stage's version names (ADR-222). The rest is the control panel's. None decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.

The operator's *Use the CPU image* names another extractor, so the next run mints new runs from stage 2 on (ADR-170's Consequences). That is the operator's choice, made knowing it, and not this record's.

## Alternatives rejected

- **Read `up`'s line for `could not select device driver` and rewrite it.** ADR-410's reason: a guess at Docker's words, and after `up` has created the containers.
- **A probe container, `docker run --gpus all … nvidia-smi`.** It needs an image pulled or built first, which ADR-323 §4 never pulls. It adds a container to a check that a key of the engine's answers.
- **Check that the hook is on the daemon's path.** The driver is registered only at the daemon's start (Read), so a hook installed since then is a request that still fails.
- **Fall back to the CPU image silently.** The operator decided against it: another extractor would mint new runs behind the operator's back (ADR-170 §3).
- **A fallback for one press alone.** §7: the next Run would meet the same refusal, and the identity would switch between invocations.
- **Write `vespera.docling.image` into `application.yaml` or `.env`.** ADR-397: a file there is the operator's and never written over.
- **A setting of a GPU of its own, or a GPU for Ollama alone.** ADR-323 §2 stands, by the operator's decision. Such a setting could say GPU while the image said CPU.
- **A `vespera setup` command, or the install as a child.** ADR-278 §1 and ADR-390 §4: Docker's work is `DockerEngine`'s, in process.
- **Install Docker Engine, the WSL distribution or the drivers too.** The operator decided the toolkit alone. Docker Engine and WSL keep ADR-410's and ADR-390's lines. A driver is the card maker's to install, on Windows outside WSL.
- **The vendor's current version.** A version pinned in the jar is the one the confirmation names, and a later toolkit is a change of its own.
- **The password on the command line, in `SUDO_ASKPASS` or in the environment.** §8: each is read by more than `sudo`.
- **Ask for the password on Windows too.** `wsl -u root` asks none (ADR-390 §1).
- **A row in the header's check, or the card asked at each of its checks.** §5: twelve questions of the engine a minute for an answer only an install or a restart changes; and the header's rows say what answers, its columns fixed (ADR-440's refused alternatives).
- **A Check in the Preflight section for the card.** ADR-440 removed the section's Check; the answer asked once while the daemon answers is as current as a press would make it.
- **Rows saying the card was not checked or not asked.** ADR-440 draws a row only where its condition holds; the section says nothing of the card for the CPU image or where Docker does not answer, as it says nothing of a holder where nothing holds.
- **The refusal's line in the output panel before the install's lines.** The question says it, and ADR-437 §3 tells no refusal; the panel ends on the press's line.
- **AMD or Intel now.** The operator split them out, #911 and #912.

## Consequences

- **The Context's line never reaches the operator from Vespera's own `up`.** On a machine without the toolkit, `vespera run` refuses before `up`, naming the toolkit and the CPU image. *Start Docker* asks.
- **A Windows or Linux machine with an NVIDIA card gets the toolkit from the control panel** in one answer, plus a password on Linux. It writes the three files of §6 and restarts Docker. The output panel shows each command as it runs and what it printed.
- **A machine whose card is AMD's or Intel's**, and every Mac, is told in one line that it runs on the CPU image.
- **The GPU image adds two commands to a start that reaches the engine**: the runtime check, and the card query or the read of the cards. The window adds the same two each time the daemon comes to answer, and after each press.
- **`CONTEXT.md`'s Archive and Working directory entries** name, among what is written outside the working directory, the three files the install writes and Docker's restart, once the operator confirms it. Its Control panel entry says that *Start Docker* asks where the graphics card cannot be used, that the window asks for the `sudo` password on Linux and keeps it nowhere, and that the Preflight section says whether Docker has the runtime; its Output panel entry that the install's lines are among a start's.
- **The README's "Running it"** says what §9 says, and its Preflight clause names the card's row.
- **`docs/architecture.md`'s technology table** says that a start for the GPU image asks for the nvidia runtime first, and that the control panel installs NVIDIA's toolkit once the operator confirms it, citing this record.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists `NvidiaRuntimeIsMissingException` and `NoNvidiaCardException` as holding no rule.

## What this does not decide

- **AMD cards**, [#911](https://github.com/algernon28/vespera/issues/911), and **Intel cards**, [#912](https://github.com/algernon28/vespera/issues/912).
- **A runtime missing while the device driver is registered**: the toolkit installed, Docker restarted, and `nvidia-ctk runtime configure` never run. The request would succeed, and §1 refuses it. The install is harmless there and lists the runtime.
- **Containers an earlier, failed `up` created** on the GPU image. `up --no-recreate` starts them as they were, so the CPU image's start meets the same device request until the operator removes them with `docker compose -p vespera rm`.
- **A distribution without `apt-get`**, Debian's archive apart (ADR-410's same limit), and a Linux without `sudo`. The install stops at its first command, naming it.
- **Proxies and machines with no network.** `apt-get` and `curl` fail in their own words, in the install's line.
- **Uninstalling or repairing the toolkit**, and moving to a later version of it.
- **NVIDIA's signing key changing.** The key is read from NVIDIA at each install.
- **Which model fits the card's memory**, and choosing one card among several: WSL gives the containers every card (Read).
- **A driver too old to answer `compute_cap`.** The query fails, and §4 says nothing.
- **Docker restarted outside Vespera while the daemon keeps answering**, the toolkit installed by hand among it: the Preflight rows keep the answer until the daemon stops answering at a check or *Start Docker* is pressed.

## Tests

| Method | What it holds |
|---|---|
| `pipeline.TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest.whereTheRuntimeIsMissingTheStartIsRefusedBeforeUpNamingTheToolkitAndTheCpuImage` | §1 to §3: on Windows with a distribution named and with the default one, and on Linux, an NVIDIA card read or no card read, `NvidiaRuntimeIsMissingException` in §2's line, whole, after exactly the engine check, the checks for Compose and buildx, the runtime check and the read of the cards, and no `up` |
| `pipeline.TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest.aMachineWhoseCardIsNotNvidiasIsToldItRunsOnTheProcessor` | §3: AMD's, Intel's, both, and none of the three, on Windows and Linux, a read exiting 0 naming no vendor of the three among them: `NoNvidiaCardException` in §3's line, whole, and no `up` |
| `pipeline.TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest.onMacosTheGpuImageIsRefusedBeforeAnyCommand` | §3: on macOS, §3's line naming macOS, and no command run |
| `pipeline.TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest.whereTheRuntimeIsListedTheCardsAreAskedAndUpNamesBothFiles` | §1, §4: on Windows and Linux, the runtime check, then the card query, then `up` naming both files; each check given 30 s |
| `pipeline.TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest.anOldDriverOrACardBeforeTuringIsWarnedAndTheStartGoesOn` | §4: a driver under each system's floor and a compute capability under 7.5 give their lines at WARN, whole, each told too, and `up` still runs; a card above both, and a query that fails, say nothing |
| `pipeline.TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest.theCpuImageAsksNothingOfTheCard` | §1: the CPU image, no runtime check and no query |
| `pipeline.TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest.theControlPanelTellsTheTwoRefusalsFromEveryOther` | §10: `DockerEngine.offersTheNvidiaToolkit` true for §2's line, false for §3's, empty for ADR-410's and ADR-323's |
| `pipeline.TheNvidiaToolkitIsInstalledAsRootTest.onWindowsEachCommandRunsAsRootInsideTheDistributionWithNothingOnItsInput` | §6: the eight commands, whole and in order, inside the distribution named, nothing fed, each given 10 minutes; the done line |
| `pipeline.TheNvidiaToolkitIsInstalledAsRootTest.onLinuxEachCommandRunsThroughSudoFedThePasswordOnItsInputAndNowhereElse` | §6, §8: `sudo -k -S` before each command, `-k` claimed on its own, each fed the password and a line end; exactly the eight `Installing …` lines told; no command word, no line told and no logged line holds it; the array all `\0` after |
| `pipeline.TheNvidiaToolkitIsInstalledAsRootTest.theFirstCommandThatFailsEndsTheInstallAndItsLineNamesIt` | §6, §8: a command exiting 100, `sudo` refusing the password, and a program not found as exit 127, each ending the install at that command, in the line, whole, the lines told exactly each command run and what it printed; the array cleared |
| `pipeline.TheNvidiaToolkitIsInstalledAsRootTest.theInstallHoldsTheContainersLockWhileItRunsAndLetsItGoBeforeTheStartRunsAgain` | §6: while each of the eight commands runs, `containers.lock` and `sidecars.lock` held by this process; both free once it returns; the earlier lock held, the lock's line, whole, no command run, the array cleared |
| `pipeline.TheNvidiaToolkitIsInstalledAsRootTest.theCommandsListedAreTheOnesRunAtThePinnedVersionAndMacosInstallsNothing` | §6, §10: the listed commands, whole, with `1.20.2-1`; on macOS none listed, its line, and no command run |
| `ui.TheGraphicsCardQuestionTest.aStartRefusedForTheMissingRuntimeAsksWithTheCommandsAndBothWaysOn` | §5: the question's title, sentence and three answers, whole; closed with none, the refusal's line in `outcome` and nothing more run |
| `ui.TheGraphicsCardQuestionTest.useTheCpuImageSetsTheImageFieldAndStartsAgainForIt` | §5, §7: the field reads the CPU image, a second start given it, no install |
| `ui.TheGraphicsCardQuestionTest.theStartAfterUseTheCpuImageIsForWhatTheServicesFieldsHoldThen` | §5: the Docling address edited while the first start is held; *Use the CPU image* answered; the second start for the CPU image and the edited address, the first for neither |
| `ui.TheGraphicsCardQuestionTest.installTheToolkitOnWindowsAsksNoPasswordAndStartsAgainOnceInstalled` | §5, §6: one install, no password asked, then a second start; the done line shown; the output panel exactly the first start's lines, the install's, the second start's and the done line; a start refused again for the card not asked twice |
| `ui.TheGraphicsCardQuestionTest.onLinuxTheInstallAsksThePasswordInASecretFieldAndKeepsItNowhere` | §5, §8: the secret's title, sentence and answers; the install given what was typed; no file under the test's folder and no text of the window holds it; cancelled, no install and the refusal's line |
| `ui.TheGraphicsCardQuestionTest.aCardThatIsNotNvidiasOffersTheCpuImageAlone` | §5: §3's question, its two answers, whole |
| `ui.TheGraphicsCardQuestionTest.thePreflightSectionSaysWhetherDockerHasTheNvidiaRuntimeAskingOnceWhileTheDaemonAnswers` | §5: for the GPU image with the daemon *OK*, the rows `graphics-card`, *KO*, its words and caption, and its warning's row after `working-directory`, the card asked once over later checks; for the CPU image, no such row; after a check with the daemon *KO*, the GPU image again, asked a second time, the row *OK* with its words |
| `ui.TheGraphicsCardQuestionTest.anAnswerOfTheCardStillBeingAskedWhenDockerStopsAnsweringIsNotKept` | §5: the card's question held, a check finding the daemon *KO* comes back, the question then answered: no row of the card while the daemon stays *KO*; once it answers, the card asked a second time and its row that answer's |
| `ui.TheGraphicsCardQuestionTest.theServicesSectionSaysTheGpuImageIsForNvidiasCardsAlone` | §9: the two choices' words |

Amended:

- **`ScriptedDocker`** answers the runtime check as listed unless `withoutTheNvidiaRuntime`, records what `runFeeding` is fed, and runs what `whenFed` sets as each fed command runs.
- **`NoCommandOpensAnythingTest`**'s runner passes `runFeeding` to it.
- **`RecordingDockerControl`** answers `cardRefused`, `installTheNvidiaToolkit` and `graphicsCard` as a test scripts them, an install telling the lines `tellingOnEachInstall` sets, a check of the card held until `holdingCardChecksUntil` releases it, and its starts in turn.
- **`AnsweringDialogs`** answers `secret`.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists the two exceptions.

What no unit test shows is what `apt-get`, `nvidia-ctk`, `sudo`, `wsl.exe` and PowerShell do with these commands; the documentation is the evidence (Read).

## What the implementation owes

Beside turning the tests above green:

- **`HostSystem`**: the runtime check, the card query and the read of the cards, for each system.
- **`ContainerStarter`**:
  - the check between `checkComposeAndBuildx` and `bringUp`, for the GPU image alone;
  - macOS's refusal where the image is found to be the GPU file's;
  - the warnings at WARN, told;
  - its four new methods (§10), the install's lines logged and told;
  - `sudo -k -S` on Linux, and the install under the containers' lock, with its line where the lock cannot be taken.
- **The two exceptions** and their lines.
- **`ProcessRunner.runFeeding`**, and `SystemProcessRunner`'s, writing the input and closing it.
- **`DockerEngine`**'s members (§10).
- **`DockerControl`**'s records, its three methods and `inProcess`'s and `NONE`'s answers. `cardRefused` reads `why` as the line's first sentence and the rest from `DockerEngine`.
- **`Dialogs.secret`**, over a `PasswordField`, cleared once it closes.
- **The window**: *Start Docker*'s question and its answers, the install on the press's thread, the press's lines and its line in the output panel, and the Preflight rows, the card asked once while the daemon answers and the answer dropped as §5 has it, one coming back after a drop not kept; and the start after *Use the CPU image* for the fields as they hold then.
- **The Services section's words.**

The README, `CONTEXT.md`, `docs/architecture.md`, the notes at the heads of the records amended, and `PipelineHoldsOnlyTheRulesOnRecordTest`'s lines change with this record.
