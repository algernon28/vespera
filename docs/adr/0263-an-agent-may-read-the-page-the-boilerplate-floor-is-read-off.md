# ADR-263 — An agent may read the page the boilerplate floor is read off

- **Date**: 2026-10-15
- **Status**: accepted on 2026-10-15.
- **Amends**: [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md), in these places and no others: §2's table, which gains `document-frequency.html` with Read and no Edit or Write, and its Context table, which gains the page's row. It carries a note at its head.
- **Keeps**: the rest of ADR-261: the list is of names, not of kinds (§2), no page is written (§2), how the guard tells an admitted file (§4), what stays refused to a shell command, Grep, Glob and NotebookEdit (§5), that a floor stays the operator's (§6), and what an agent does with what it reads (§7). [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md): the guard change this record specifies is a draft the operator installs (§7 of that record).
- **Rests on**: [ADR-262](0262-stage-3-writes-the-page-the-boilerplate-floor-is-read-off.md), for what the page holds.

## Context

ADR-262 has stage 3 write `document-frequency.html`, the page `boilerplateDocumentFrequencyFloor` is read off. ADR-261 was written before it and admits pages by name, so the page is refused to every tool, and an agent asked how far a run can go reads stage 4 as gated without seeing what the operator would choose from. ADR-261 §2 says a page a stage writes later is admitted by a record of its own, after reading what its writer puts in it.

### What the page carries, read from its writer

| File | Writer | What it carries | Kind |
| --- | --- | --- | --- |
| `document-frequency.html` | `DocumentFrequencyReport`, from `DocumentFrequency.shares` | the content census run's id; the documents measured, N; one row per share of the documents, rounded down to the hundredth, with the number of passages found in that share, the fewest documents any of them is found in, and N; and the rule of ADR-260 §5 for setting the floor | counts |

`DocumentFrequency.ShareLine` holds a share, a count of passages and a count of documents, and `Shares` adds N: no passage's text, no hash and no path reaches the page. ADR-262 says the same: *"It holds no text of a document, no hash and no path."*

## Decision

**`document-frequency.html` joins ADR-261 §2's list, to Read, directly in a working directory the allow list names, and to nothing else.**

| File | Read | Edit, Write |
| --- | --- | --- |
| `document-frequency.html` | yes | no |

It is admitted by ADR-261 §4 as the other pages are: by its whole name, directly in a folder holding `vespera.db` or `vespera.lock`, on every reading of the path, and only where it is not there or is a regular file with one name. Its `.part` file, a copy in a subfolder, and every tool but Read stay refused.

**Reading the page does not make the floor an agent's.** ADR-261 §6 stands: the agent may cite the rows and ask, and writes `boilerplateDocumentFrequencyFloor` only when the operator states the value and its provenance.

### What changes, and who puts it in place

- **`.claude/hooks/private-paths-guard.mjs`**: `document-frequency.html` joins `READ_IN_A_WORKING_DIRECTORY`; its header and its refusal text say seven pages, and the header cites this record. It lies in a closed `.claude` folder, so the operator puts it in place (ADR-215 §7).
- **`src/test/hooks/private-paths-guard.test.mjs`**: the fixture's working directory holds the page, and K413 is added (Tests).
- **`AGENTS.md`** adds ADR-263 to the records the guard enforces.
- **`docs/agents/private-paths-guard.md`** names the page among the admitted files, and the records the hook is held to.
- **Not** `.claude/allowed-paths.txt`, `.claude/settings.json`, the wrapper, the counting script, or anything under `src/main`.

## Decided here

1. **The page is admitted by its writer, not by its use.** It is read off `DocumentFrequencyReport.render` and `DocumentFrequency.ShareLine`, which carry counts only. The cost: a later change that put a passage on the page would open it unseen; ADR-261 §2's rule of names does not catch that, and a change to the page's writer is to be read against this record.

## Consequences

- **An agent can read the counts the boilerplate floor is chosen from**, and report them to the operator, in any working directory the allow list names.
- **Stage 4's gate stays the operator's to open**: nothing is set without their value and provenance.
- **The guard's test gains one case**, K413, and the fixture one page.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **The operator copying the page out of the working directory.** It needs no change, and costs a step for every run; ADR-261 rejected it for the other pages.
- **The ADR-212 counting script printing the shares.** Rejected: the page already holds them, and a new count is a change to the script and to the guard's pinned hash (ADR-212 §5).

## Tests

`src/test/hooks/private-paths-guard.test.mjs`, through the registered wrapper like every other case. The fixture's working directory holds `document-frequency.html` beside the other pages.

| Cases | What they hold |
| --- | --- |
| K413 | Read of the document-frequency page is let through |

**Not pinned, and why**: Write, Edit, Grep, a shell command, a subfolder copy and a `.part` file of this page, each held by K501 to K517 for one admitted name; the check that refuses them does not depend on which admitted name it is.
