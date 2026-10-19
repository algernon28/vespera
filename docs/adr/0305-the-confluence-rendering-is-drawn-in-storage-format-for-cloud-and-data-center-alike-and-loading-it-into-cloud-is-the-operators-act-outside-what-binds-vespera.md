# ADR-305 — The Confluence rendering is drawn in storage format for Cloud and Data Center alike, and loading it into Cloud is the operator's act, outside what binds Vespera

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Decided**: by the operator's ruling 3 of map [#604](https://github.com/algernon28/vespera/issues/604), both editions, storage format and the acceptance of Cloud's hosting (§1, §2); §2's sentence, §3 and the scope clause of `CONTEXT.md` (§5) under the operator's delegation of 2026-10-19.
- **Rests on**: ADR-301 ([#607](https://github.com/algernon28/vespera/issues/607)), by which stage 6b draws a Confluence storage-format rendering beside the HTML tree, and whose §3 leaves the edition to this record: "Confluence is again a target a rendering is drawn for, its storage format being what the rendering is written in, for each edition as #609 settles". This record is what that §3 defers to (§1). Rests on ruling 2 of #604 ([#608](https://github.com/algernon28/vespera/issues/608)), by which Vespera never sends the tree anywhere itself: the operator's own loader does.
- **Keeps**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) §2 and [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md), whole and unchanged: nothing Vespera runs sends a document to a hosted model (§2).
- **Keeps**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5 and §6: no value is added that a stage reads, so no run id moves (§3, §4).
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1, [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) and [ADR-047](0047-the-pipeline-never-blocks.md): no launch value, no property and no option is added (§3).
- **Settles** [#609](https://github.com/algernon28/vespera/issues/609).

## Context

ADR-301 decides: "Beside the HTML tree, stage 6b draws a Confluence storage-format rendering of the same tree, as files, for the operator to load; beyond drawing it, nothing in this project renders, uploads or transmits the deliverable anywhere." A loader the operator writes carries the rendering into a Confluence space. Confluence comes in two editions. On **Cloud**, Atlassian hosts the site. On **Data Center** (DC), the site runs on servers the customer operates.

### What is known of the two editions

Every fact below comes from the research of [#605](https://github.com/algernon28/vespera/issues/605), §2, which read open-source clients and search summaries but no Atlassian page whole. Each is **[unverified]**, and the prototype of [#611](https://github.com/algernon28/vespera/issues/611) confirms it on a sandbox space before anything is built on it.

| Fact | Edition |
| --- | --- |
| A page's body is storage format, Confluence's XHTML-based XML, sent with `representation: "storage"` through REST API v1 | both [unverified] |
| REST API v2 exists on Cloud alone; DC has v1 | Cloud, DC [unverified] |
| The largest file a site takes is set by its administrator, 104,857,600 bytes (100 MB) by default | both [unverified] |
| A REST request body is at most 5,242,880 bytes by default, raised by a JVM property and a restart of every node | DC [unverified] |
| No request cap is documented, and two issues report one of 5,242,880 bytes | Cloud [unverified] |
| A classic API token or a scoped one, against different base addresses | Cloud [unverified] |
| A Personal Access Token sent as `Authorization: Bearer` | DC [unverified] |
| No new subscriptions since 2026-03-30; existing customers buy and expand until 2028-03-30; licences expire and sites go read-only on 2029-03-28 | DC [unverified] |

Atlassian Document Format (ADF), the JSON body of Cloud's editor, has no mapping that #604 found, and storage format is the body common to both editions (#604, Out of scope).

### What binds Vespera

- `CONTEXT.md`'s **Archive**: "a document is read only by the local models Vespera runs".
- [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) §2: "Nothing that reads a document leaves the machine: not for selection, not for confirmation, not for a run."
- [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md): a local model answers the relevance questions, so no document reaches a hosted model.

A rendering loaded into a Cloud site carries the copies of the survivors (#604, ruling 5) and the writing stage 6b made over them, and the site lies on Atlassian's hosting. Read without their scope, the three would forbid that load.

### Where a value the operator sets would live

- A profile key a stage reads reaches its run through `config_consumed`, so it moves the generation run (ADR-274 §5).
- A Spring property is not one of the control panel's seven launch values (ADR-292 §1), and cannot be set from it without amending ADR-292 and ADR-285.
- A command-line option meets ADR-047 and the Keeps of ADR-292.

## Decision

### 1. One rendering, in storage format, for Cloud and Data Center alike

Stage 6b draws the Confluence rendering in storage format, and one Confluence rendering serves both editions. No body is drawn in ADF, and no part of the rendering is drawn for one edition only.

### 2. Loading into Cloud is the operator's act, and outside what binds Vespera

**Loading the Confluence rendering into a Cloud site puts the documents it carries, and the writing Vespera made over them, on Atlassian's hosting; the operator accepts this explicitly. `CONTEXT.md`'s Archive, ADR-195 §2 and ADR-197 bind Vespera itself — what it reads, which models read a document, what it writes — and not where the operator carries the deliverable afterwards.**

Two reasons:

- **Vespera's own rule stays whole.** Every model that reads a document is still local, and nothing Vespera runs sends a document off the machine. Narrowing the three records' scope to Vespera is what they already govern; no exception is cut into them.
- **The load is not Vespera's.** Vespera never sends the Confluence rendering or the HTML tree anywhere (#608), so what lies on a Cloud site lies there by the operator's loader and the operator's choice of edition, as a copy carried anywhere else by the operator would.

### 3. Nothing that differs per edition is a value the operator sets

| What differs | Where it is met |
| --- | --- |
| The body | nowhere: one Confluence rendering, in storage format, for both (§1) |
| The largest file a site takes | by the operator's loader, from the sizes the page list gives, skipping and reporting a copy over it (#604, ruling 5; [#617](https://github.com/algernon28/vespera/issues/617)). The site's administrator sets the cap; Vespera neither reads nor holds it |
| The request cap | by the operator's loader, and by how large a page is drawn, which [#571](https://github.com/algernon28/vespera/issues/571) decides from #611's measurement on each edition it reaches |
| The API version, the endpoints and the authentication | by the operator's loader (#604, rulings 2 and 8) |

So **no profile key, no Spring property and no command-line option** is added for the edition, and the control panel gains no value for it. This agrees with #604's ruling 4: the rendering is drawn always, with nothing to turn it on.

### 4. The run ids that move

**None.** No value a stage reads is added (§3), and this record changes no code.

### 5. `CONTEXT.md` scopes the Archive's sentence

The **Archive** entry gains one clause after "a document is read only by the local models Vespera runs": that this binds Vespera, and not where the operator carries the deliverable afterwards, a Confluence Cloud site among them, citing this record. No other entry changes. ADR-195 and ADR-197 are not changed: their text speaks of what Vespera runs, and §2 says so here once.

## Consequences

- **#611 confirms each fact on every edition it can reach**, since the rendering and the loader's contract hold for both; a fact of an edition it cannot reach stays **[unverified]** and nothing is built on it.
- **#617 covers both editions**: the copies carried as files meet each edition's file cap, by the same rule in the loader.
- **[#621](https://github.com/algernon28/vespera/issues/621) covers both editions**: the README's section on loading names what the operator's loader meets differently on each, and that a Cloud load puts the documents and the writing on Atlassian's hosting.
- **Once loaded on Cloud, the documents are off the operator's machine**, and who sees them is decided by the space's permissions (#604, ruling 7), not by Vespera.
- **DC's end of sale changes nothing drawn.** The Confluence rendering is the same for both editions, so no branch for DC is to be retired when DC sites go read-only.
- **No fact of the table above is built on** until #611 has confirmed it; a threshold on the request cap waits on #571 and #611.

## What this does not decide

- Where the Confluence rendering lies in the deliverable ([#612](https://github.com/algernon28/vespera/issues/612)), its space and titles (#613), how a citation lands (#614), the page list's contract (#618) and the loader the README names (#619).
- The oldest DC version the rendering is meant to load on.
- How code sets are carried on Confluence (#604, Not yet specified).

## Tests

None. This record adds no code and no value: §1 and §3 are held by the tests of the rendering's build, which draw one Confluence rendering with no edition in it, and §2 and §5 are prose a test would hold only as wording.
