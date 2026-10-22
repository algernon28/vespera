# ADR-308 — Vespera publishes the Confluence rendering to the operator's Cloud site itself, on an explicit publish, with a token read from the environment alone

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Supersedes**: [ADR-304](0304-vespera-never-sends-the-deliverable-tree-to-confluence-itself-and-holds-no-uploader-no-credential-and-no-connection-for-it.md), whole. Vespera sends the Confluence rendering to one Confluence Cloud site, under one parent in one space the operator names, when and only when the operator asks it to (§1 to §3).
- **Amends**: [ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md) §1, whose *"beyond drawing it, nothing in this project renders, uploads or transmits the deliverable anywhere"* gains *"except that `vespera publish` sends the Confluence rendering to the operator's Confluence Cloud site (ADR-308)"*; and ADR-101's Decision as ADR-301 rewrote it, the same clause. The HTML tree is sent nowhere (§1).
- **Amends**: [ADR-102](0102-publication-is-the-state-the-deliverable-is-handed-over-in-never-a-verb-this-project-uses.md): *publish* becomes a verb of this project for one act alone, sending the Confluence rendering to the operator's site; *publication-ready* keeps its sense, and nothing else is said to publish (§4).
- **Amends**: [ADR-305](0305-the-confluence-rendering-is-drawn-in-storage-format-for-cloud-and-data-center-alike-and-loading-it-into-cloud-is-the-operators-act-outside-what-binds-vespera.md) §1: the rendering is drawn in storage format for Confluence Cloud; Data Center is no edition it is drawn or published for, and every fact of it stays unverified and unbuilt on (§9).
- **Keeps**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) §2, [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6 and [ADR-202](0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md) §3, read in their purpose: each keeps the operator's documents from any party but their owner, and a publish sends them to the owner's own Confluence tenancy, so it keeps that line and does not cross it (§2).
- **Amends**: [ADR-047](0047-the-pipeline-never-blocks.md) and [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1, the CLI being `run`, `label` and `publish` (§5); [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, the control panel starting `publish` as it starts the other two (§6).
- **Amends**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) as later records narrowed and widened it: a tenth module, `confluence`, which may depend on `ledger` and nothing else horizontal (§7).
- **Keeps**: [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §8 and [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1 and §2: no file Vespera reads or writes holds the token, and the seven launch values stay seven (§3, §6).
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names no type of `java.net.http`, and opens no connection to Confluence (§6).
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): no dependency is added; the publisher speaks HTTP through Spring's `RestClient`, which the jar already carries (§7).
- **Keeps**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5: no stage reads a value of the publish, and no stage's version names `confluence`, so no run id moves (§10).
- **Decided**: by the operator on 2026-10-21, reversing ruling 2 of map [#604](https://github.com/algernon28/vespera/issues/604): a Spring service inside Vespera publishes the rendering to the operator's Confluence Cloud site with the operator's own API token, under a parent in an existing space; and the questions of #632, the token, egress, the command, the module, republishing, where the target is set and the template that sets it, Cloud only and the verb, as the operator settled them on 2026-10-21.
- **Settles**: [#632](https://github.com/algernon28/vespera/issues/632).

## Context

ADR-304 kept every byte of the deliverable on the operator's machine and left the load to a loader of the operator's own. The operator has Confluence Cloud only: no Data Center site, no right to create a space and none to install an app, and write access to a space of their own in which they name a folder or a page to hold the rendering. Carrying the rendering there by hand was weighed: Cloud's Word import is flat and carries no copies, and its HTML import makes a new space each time. A connector outside this repository was weighed too, and refused for the reason ADR-301 refused option D: it would re-derive the rendering's layout and break unseen at each change of it.

What a Cloud site measured with synthetic pages shows (`docs/research/confluence-loading.md` §5):

- **Confluence rewrites a storage body on save**, entities among it, so a body read back never equals the body sent.
- **A file whose name already stands on a page is refused by a second `POST`**; it is replaced through the file's own update endpoint.
- **A title is unique within a space.**
- **No body or file up to 10 MiB was refused.**
- **A folder takes a page as a parent does**: v2 `POST /pages` with a folder's id as `parentId`, and v1 with it in `ancestors`, each made the page a direct child of the folder, and a page made under that page sits beneath it.

What the code holds: the jar already speaks HTTP to Docling and Ollama through Spring's `RestClient` over the JDK's `HttpClient`; the CLI is `run` and `label`; the control panel starts the jar's two commands as a child, which inherits the operator's environment beside the five variables ADR-292 §4 adds.

## Decision

### 1. What is sent, and where

`vespera publish` sends the Confluence rendering of one generation run, as its page list lists it, to one Confluence Cloud site, as pages under one parent in one space, and the files those pages carry. It sends nothing else: not the HTML tree, not `documents.csv`, not the database, a log or a report. It sends nothing on any other command, and no stage sends anything. A publish reads the rendering's files to send their bytes and reads no document's text: no model runs in it.

### 2. Egress: one host, on one command

The publish opens connections to one host, the host of the configured site, over `https` alone, and to no other: a base URL that is not `https`, or that names a host other than the site's, is refused before anything is sent. **This keeps the promise of ADR-195 §2, ADR-197 §6 and ADR-202 §3.** What they guard is the operator's documents reaching a party other than their owner: a hosted model, a service of someone else's. The Confluence site a publish sends to is a tenancy of the documents' owner, holding them under the owner's own account and permissions, so the documents go from one store of their owner's to another, as ADR-305 §2 already reads a load into Cloud. Nothing reaches a model or a service that is not the owner's, and every stage still runs its models on the machine.

### 3. The token, the account, the site and the parent

| Value | Read from | Never |
| --- | --- | --- |
| the API token | the environment variable `VESPERA_CONFLUENCE_TOKEN` alone | a property, a YAML file, the profile, the launch-value file, a log line, the database, a file the publish writes |
| the account's email | the environment variable `VESPERA_CONFLUENCE_USER` alone | the same |
| the site's base URL | the property `vespera.confluence.base-url`, set by `VESPERA_CONFLUENCE_BASE_URL` | a shipped YAML file, the profile |
| the space's key | the property `vespera.confluence.space-key`, set by `VESPERA_CONFLUENCE_SPACE_KEY` | a shipped YAML file, the profile |
| the parent | the property `vespera.confluence.parent-id`, set by `VESPERA_CONFLUENCE_PARENT_ID`, or `vespera.confluence.parent-title`, set by `VESPERA_CONFLUENCE_PARENT_TITLE` | a shipped YAML file, the profile |

- **The token and email go as HTTP Basic authentication** with a classic API token, to `https://<site>/wiki/…`. A scoped token through `api.atlassian.com` is not decided here.
- **The parent is a page or a folder** of the named space, and the root page is created straight under it either way, set by its id or by its title, one and not both. A title is looked up in that space and must name exactly one page or folder, or the publish refuses; an id must name a page or folder of that space, or it refuses. The publish makes no space and moves nothing it did not make out from under the parent.
- **Each is set for each publish**, by the operator's environment or a property on the command line; none is fixed in code.
- **A template sets them in a shell**: `confluence-env.ps1` and `confluence-env.sh` ship with the jar, naming the five variables and no value. The operator copies one into the working directory, fills in the site, the space and the parent, and runs it in the shell that will run `vespera publish`; it asks for the token and the email at each run and writes neither anywhere. The copy in the working directory is the operator's file, and nothing Vespera writes holds its values.
- **A publish missing any of the five refuses before anything is sent**, and names the variable it lacks, never its value.
- **The site, the space and the parent are never in this repository**: no shipped file holds one, and no test names a real one.

### 4. The word *publish*

ADR-102 keeps *publication-ready* as the state the deliverable is handed over in, and now gives this project one verb from the same root: to **publish** is to send the Confluence rendering to the operator's site by `vespera publish`. Nothing else is said to publish: not stage 6b, which draws, not the control panel, which starts the command. `CONTEXT.md` gains the entry **Publish**, and its Confluence rendering entry's *"drawn, never sent"* reads *"drawn by stage 6b, and sent only by a publish"*. *Upload* stays under that entry's _Avoid_.

### 5. The command

`vespera publish [--db-dir <working directory>]` publishes the Confluence rendering of the generation run the working directory's last drawing drew, found through the ledger. It takes the lock every invocation takes (ADR-177 §1), sends, prints what it sent and what it skipped, and ends. A working directory with no drawn rendering refuses with a line saying so. It is the third command; `run` and `label` are unchanged. The README's invocations (ADR-098) end at the tree as before: a publish is not an invocation that takes the archive further, and the README gives it a section of its own (#621).

### 6. The control panel

The control panel offers a publish on the invocation page once a generation run has drawn a rendering, and starts it as it starts `run` and `label`, as a child with `--db-dir` (ADR-278 §1). The child inherits the five values of §3 from the operator's environment; the control panel neither asks for nor holds any of them, writes none to the launch-value file (ADR-292 §2), and its seven launch values stay seven. It names no type of `java.net.http` (ADR-285 §3).

### 7. The module

The publisher is a tenth module, `io.algernon.vespera.confluence`, declaring `allowedDependencies = "ledger"`. It reads the page list and the files it names from disk, by the page list's contract (#618), and names no class of `deliverable`. `pipeline` names it, as the composition root that declares the command. A module of its own keeps the publisher's changes from moving stage 6b's version (ADR-058), and keeps the one module that opens a connection to Confluence the one place a test looks for it. It speaks through `RestClient`; no Confluence client library and no other dependency enters the pom.

### 8. Republishing: in place, by key and local hash

- **A page is found by its stable key**, which the page list gives (#618), held on the page as a content property the publish sets. A page whose key stands under the parent is updated with the next version number; one whose key does not is created.
- **A page is skipped when its local body's SHA-256 equals the hash the property holds**, the hash of the body the publish sent, never one taken of the body read back, which Confluence rewrites on save.
- **A file is found by its name on its page**: one that stands is replaced through its update endpoint when its size or hash differs, and one that does not is added.
- **Every page is created before any body is written**, so a link between pages, which Confluence resolves by title, finds its page.
- **Nothing is deleted.** A page of an earlier publish whose key the page list no longer holds is left as it stands and counted in the closing line.
- How the rendering's pages are titled so that a title is unique within the space is #613's.

### 9. Cloud only

The rendering is drawn in storage format for Confluence Cloud, and the publish speaks Cloud's REST API: v2 for pages and content properties, v1 for files, which v2 does not upload. ADR-305's Data Center reading is withdrawn: nothing is drawn or published for it, and no part of the rendering is shaped for it.

### 10. Run ids

No stage reads a value of the publish, and no stage's version names `confluence` or `pipeline`, so no run id moves, now or when the publisher is built (#633).

## Alternatives refused

- **Publishing by hand** through Cloud's Word or HTML import: flat, without copies, or a new space each time (Context).
- **A connector outside this repository**: it re-derives the rendering's layout and breaks unseen at each change of it (ADR-301's option D).
- **The publisher in `deliverable`**: each change to it would move stage 6b's version (ADR-058) and re-mint the generation run's drawing for no change in what is drawn.
- **The token in `application-local.yaml` or the launch-value file**: a file holding a token is a file that leaks it; the environment holds it for the life of a shell and no longer.
- **The site and parent as profile keys**: the profile describes the corpus, and a key would move the generation run (ADR-274 §5) for a value no stage reads.
- **Always publishing fresh**: each publish would leave a second tree under the parent, and titles unique within a space make a second tree collide with the first.
- **A control panel that sends itself**: it would name `java.net.http` against ADR-285 §3 and hold the token in a process that serves a browser.

## Consequences

- **`NothingShippedReachesConfluenceTest` is rewritten by #633**: a Confluence REST path or Atlassian's host is named under `confluence` alone; no shipped YAML holds a key for the site's address or a credential; the command tree is `run`, `label` and `publish`.
- **`CONTEXT.md`** gains **Publish** and the changed clause of the Confluence rendering (§4).
- **`AGENTS.md`, `README.md` and `docs/architecture.md`** name ten modules, three commands and a publish; [#606](https://github.com/algernon28/vespera/issues/606) carries this record into them.
- **#613, #617, #618, #619 and #621** read the publisher as the loader: what ADR-304 left to the operator's loader is the publish's.

## Tests

Built by [#633](https://github.com/algernon28/vespera/issues/633), against a stub HTTP server; no test reaches a real site.

| Method | What it holds |
| --- | --- |
| `NothingShippedReachesConfluenceTest.onlyTheConfluenceModuleNamesAConfluenceRestPath` | no file under `src/main` outside `io/algernon/vespera/confluence/` names a Confluence REST path or Atlassian's host (§1, §7) |
| `NothingShippedReachesConfluenceTest.theShippedConfigurationHoldsNoCredential` | no shipped YAML file holds a key for a Confluence site's address or a credential (§3) |
| `NothingShippedReachesConfluenceTest.theCommandHasRunLabelAndPublish` | the command tree is `run`, `label` and `publish` (§5) |
| `confluence.PublishSendsToOneHostTest` | a publish sends to the configured `https` host alone, and refuses another host or `http` before sending (§2) |
| `confluence.PublishReadsItsCredentialFromTheEnvironmentTest` | the token and email are read from the environment alone, a missing one is named and refused before sending, and no line the publish prints holds the token (§3) |
| `confluence.RepublishUpdatesInPlaceTest` | a second publish of the same rendering updates no page whose local hash is unchanged, updates a changed one with the next version, and deletes nothing (§8) |
| `controlpanel.ChildCommandTest.onlyRunLabelAndPublishCanBeBuilt` | the control panel starts the jar's three commands and nothing else (§6) |

## What this does not decide

- **The page list's entries, the stable key and the format**: [#618](https://github.com/algernon28/vespera/issues/618).
- **Titles unique within the space**: [#613](https://github.com/algernon28/vespera/issues/613).
- **Copies a site refuses as too large, and how they are reported**: [#617](https://github.com/algernon28/vespera/issues/617).
- **Pacing on `429` and `Retry-After`**: #633.
