# ADR-304 — Vespera never sends the deliverable tree to Confluence itself, and holds no uploader, no credential and no connection for it

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md)'s Decision as ADR-301 §1 amends it, *"beyond drawing it, nothing in this project renders, uploads or transmits the deliverable anywhere"*, and ADR-301 §1's *"Putting those files into a wiki is the operator's own doing, through a loader of their own"*. This record states what that sentence rules out on the side of sending, and rules it out for the Confluence rendering as for the HTML tree (§1).
- **Keeps**: [ADR-102](0102-publication-is-the-state-the-deliverable-is-handed-over-in-never-a-verb-this-project-uses.md): publication is the state the files are handed over in, and nothing in this project publishes.
- **Keeps**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) §2, [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6 and [ADR-202](0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md) §3: what reads a document stays on the operator's machine (§2).
- **Keeps**: [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §8 and [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §2: no file Vespera reads or writes holds a credential for a service off the machine (§3).
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md), [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md), [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1, [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) and [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md): two commands, no option and no tenth module (§4).
- **Keeps**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) as later records narrowed and widened it, the nine modules `AGENTS.md` lists, and [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): no client library for Confluence enters the pom (§4).
- **Decided**: under the operator's ruling 2 of map [#604](https://github.com/algernon28/vespera/issues/604), of 2026-10-19, that Vespera never sends the HTML tree, nor the Confluence rendering, to Confluence itself, holds no credential for it, opens no connection to it and adds no command or option for it (§1 to §4); and under the operator's delegation, §5 to §7.
- **Settles**: [#608](https://github.com/algernon28/vespera/issues/608).

## Context

ADR-101 ends the run at stage 6b. ADR-301 §1 amends its Decision so that stage 6b draws, beside the HTML tree, the Confluence rendering: the same tree in Confluence's storage format, as files for the operator's loader, and *"beyond drawing it, nothing in this project renders, uploads or transmits the deliverable anywhere"*. ADR-301 leaves to #608 that Vespera never sends the tree, holds no credentials, opens no connection and adds no command.

With the Confluence rendering drawn, one step remains between the files and a Confluence space: carrying them there. That step could be taken by an uploader in the jar, by one the control panel drives, or by the loader, the operator's own program outside this project.

What the code holds today:

- **No file under `src/main` names Confluence or Atlassian**, and none names a REST path of either. The pom declares no artifact of `com.atlassian`.
- **The jar talks to two services, both the operator's sidecars**: Docling, at `vespera.docling.base-url`, and Ollama, at `spring.ai.ollama.base-url`. `application.yaml` holds no key for a token, a password, an API key or a secret.
- **The CLI is two commands**, `run` and `label`, declared on `VesperaCommand`.
- **The control panel names only types of its own package and of the JDK's `java.base` and `jdk.httpserver`** (ADR-285 §3). The JDK's HTTP client, `java.net.http`, is in neither.

## Decision

### 1. Vespera sends nothing to Confluence

Neither the jar nor the control panel sends the deliverable to Confluence: not the HTML tree, not the Confluence rendering ADR-301 draws, not a page, a copy, a picture's file or a code set of either, whole or in part. No class of either opens a connection to a Confluence site, a Cloud site or a Data Center one, on the operator's machine or another. Stage 6b draws files and stops there.

### 2. Why nothing is sent: what reads a document stays on the machine

The tree carries a verified copy of every survivor it lists, the synthesis docs written over them, and the pictures inlined in its pages (ADR-274, ADR-286). Sending it is sending the operator's documents.

- **ADR-195 §2**: *"Nothing that reads a document leaves the machine: not for selection, not for confirmation, not for a run."*
- **ADR-197 §6**: the labeller runs *"with no hosted endpoint reachable"*, and *"An Ollama on another machine of the operator's own is refused too."* A Confluence of the operator's own network is another machine in the same sense.
- **ADR-202 §3**: a model whose remoteness cannot be established is refused, and *"nothing is sent"*; the check fails closed.

An uploader would be the one part of Vespera whose purpose is to move documents off the machine. Every record above draws the line at the machine, and this record draws it there too.

### 3. No credential, anywhere Vespera reads or writes

No Confluence token, personal access token, password, user name or base URL is held in `application.yaml`, `application-local.yaml`, `profile.yaml`, the launch-value file, the child's environment the control panel builds, or any other file the jar or the control panel reads or writes. No property, profile key or launch value names one.

- **ADR-084** keeps *"base URLs and API keys"* in `application.yaml`, as deployment. The services it names there are the operator's local sidecars; it gives no service off the machine a key there, and this record gives none.
- **ADR-292 §2**: the launch-value file holds *"no token, no password, no text of any document"*.
- **ADR-291 §8**: the control panel *"writes no file holding text of a document or the token"*; the one secret it holds, its own start's token, lives only in memory.

An uploader needs a Confluence credential somewhere it can read. Each of the three places it could live is closed by a record.

### 4. No command, no option, no dependency, no module

- **No command**: the CLI stays `run` and `label` (ADR-047). ADR-101 withdrew `vespera publish`; no `upload` or other command takes its place. The invocations the README lists (ADR-098) end at the tree, and none loads it.
- **No option, property or profile key** turns a load on. ADR-276 §1 named the deliverable root by a property and refused `--deliverable-root`, a third value on a two-command surface; a load would be a larger one. ADR-278 keeps ADR-047 and adds no command and no option.
- **No dependency**: a Confluence client library, or an HTTP client brought for one, would need a decision behind it (ADR-046). None is recorded.
- **No module**: no `publication` module returns, and no tenth module is added; the control panel stays outside the nine (ADR-285 §1), and its classes keep to `java.base` and `jdk.httpserver` (ADR-285 §3).

### 5. The control panel offers no load

The control panel shows no button, link or form that starts a load of either tree into Confluence, and starts no process but the jar's two commands (ADR-278 §1). Showing the tree's index, or the folder it lies in, is not a load and is not decided here.

### 6. The operator loads, with the loader

The operator carries the files into Confluence with the loader, a program of their own outside this project, as ADR-301 §1 says. What that loader is handed and may rely on is described in a section of the README, which [#618](https://github.com/algernon28/vespera/issues/618) and [#619](https://github.com/algernon28/vespera/issues/619) settle, and not here. Where a load places the operator's documents, Atlassian's hosting for a Cloud site among them, is the operator's act and not Vespera's; [#609](https://github.com/algernon28/vespera/issues/609) settles which editions the Confluence rendering is drawn for.

### 7. Out of scope, not deferred

An uploader is out of this project's scope. It is not an open question waiting for a ticket. An uploader in the jar, one driven by the control panel, or one switched on by a profile key, a property, an option or an environment variable is a new record superseding this one, and nothing short of that.

### 8. Why not an uploader, beyond the records

Nothing in this project can hold an uploader by its tests. No test can reach a real Confluence space: an agent has no credential for one, and §3 gives the build none; a stub of Confluence's REST API holds the stub and not the service. A part that moves the operator's documents off the machine and that no test the project runs can hold is the part least able to be wrong unseen.

### 9. Run ids

This record changes no code under `src/main`, no setting and no module. No run id moves.

## Alternatives refused

- **An uploader in the jar**, a command or an option of `run` that loads the Confluence rendering once it is drawn. It sends documents off the machine (§2), needs a credential (§3), and adds a command or an option and a dependency (§4).
- **An uploader the control panel drives**, in its own classes or as a process it starts. The same, and it would need the control panel to name a module outside `java.base` and `jdk.httpserver` or to hand-write an HTTP client over `java.base` (ADR-285 §3).
- **An uploader off by default, turned on by a property or a profile key.** It ships the code, the dependency and the credential's key whether or not it is turned on, and a profile key would be the corpus naming a service on one network.

## Consequences

- **The operator runs one more program after the last invocation**: the loader, over files Vespera drew. This is the cost ADR-101 named, kept.
- **`CONTEXT.md` gains nothing.** **Publication-ready artifact** already says that nothing in this project carries the files anywhere.
- **`AGENTS.md`, `README.md` and `docs/architecture.md`** say the run ends at files and nothing carries them anywhere; [#606](https://github.com/algernon28/vespera/issues/606) carries this record into them with ADR-301.

## Tests

| Method | What it holds |
| --- | --- |
| `NothingShippedReachesConfluenceTest.noShippedFileNamesAConfluenceRestPathOrAtlassiansHost` | no file under `src/main`, the jar's and the control panel's alike, names a Confluence REST path or an Atlassian Cloud host (§1) |
| `NothingShippedReachesConfluenceTest.theShippedConfigurationHoldsNoCredential` | `application.yaml`, read as Spring reads it, holds no key naming a token, a password, an API key or a secret (§3) |
| `NothingShippedReachesConfluenceTest.theBuildDeclaresNoAtlassianArtifact` | the pom declares no artifact of `com.atlassian` (§4) |
| `NothingShippedReachesConfluenceTest.theCommandHasRunAndLabelAndNoOther` | `vespera` declares `run` and `label` as its subcommands, in that order, and no other (§4) |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest.theControlPanelNamesOnlyTypesOfItsOwnPackageAndOfTheJdksBaseAndHttpServerModules` | the control panel names no type of `java.net.http` (§4, held under ADR-285 §3) |
| `controlpanel.ChildCommandTest.onlyRunAndLabelCanBeBuilt` | the control panel starts the jar's `run` and `label` and nothing else (§5, held under ADR-278 §1) |

## What this does not decide

- **What the operator's loader is handed and may rely on**: #618 and #619.
- **Which editions the Confluence rendering is drawn for, and what a Cloud load means for the operator's documents**: #609.
- **Which space the Confluence rendering is loaded into, and how**: [#613](https://github.com/algernon28/vespera/issues/613).
- **Whether the control panel shows the tree's index or its folder** once a generation run has drawn it.
