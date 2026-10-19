# ADR-283 — The private-paths guard reads a deliverable tree as outside the allow list wherever it lies, by its marker

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §8's "The guard is not changed: `deliverable/*/documents/` stays open as the working directory it lies in is", and its Decided here 11, "The guard stays as it is for the copies": the guard refuses every tree that carries a marker, the copies in it included (§2). §8's written rule stands, and reaches every tree wherever it lies (§1).
- **Amends**: [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) §2's "every file in it … `deliverable/`": a working directory under an allowed root stays open to every tool but for a tree drawn in it, which is read as outside the allow list (§2). Its §3's listed commands and §4's Glob are let through in a tree as they are outside the list.
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md)'s Consequence "An agent meets a named root as the place it lies in … open inside it": a named root under an allowed root is refused as an unnamed tree is (§2). Its marker, its §3 and its Decided here 4 stand: this record is the later rule that recognises a tree by one file.
- **Keeps**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), an allow list that fails closed and keeps no list of archives; [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) whole, its §7 deciding who puts this in place (§4); [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), whose command is admitted before any path of it is read and whose `folder=deliverable` counts are numbers; [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) §1, outside the allow list stands for the archive, and its Decided here 3, no question of the file system about a path outside the list (§2).
- **Read with**: [#570](https://github.com/algernon28/vespera/issues/570), which puts each cluster's copies beside its page in `<partition>/<cluster>/`: the rule here names no folder of the tree, so it holds of that layout and of ADR-274's flat `documents/` alike (§2).
- **Settles**: [#569](https://github.com/algernon28/vespera/issues/569).

## Context

A deliverable tree holds a verified copy of every survivor it lists (ADR-274 §2), and so the operator's documents, and the pages and pictures written from them. ADR-274 §8 kept agents from the copies by a written rule alone, because the tree then lay only in the working directory, which ADR-264 §2 opens to every tool under an allowed root. ADR-276 lets the operator name the tree's root anywhere outside the corpus root and the seed folder. A root named outside the allow list is refused like any path there; a root named under an allowed root, a folder in `${TEMP}` or in a checkout, was open to every tool, and so was the tree drawn in a working directory under one.

The guard recognised a working directory by `vespera.db` or `vespera.lock` (ADR-196 §1), and since ADR-264 asks that only for ADR-212's counting command. A deliverable root holds neither. It holds the marker, `vespera-deliverable.txt`, at its top, which ADR-276 §3 writes into every tree, named or not, before the first copy, so that a later rule can recognise a tree wherever it lies by one file.

[#570](https://github.com/algernon28/vespera/issues/570) moves the copies out of `documents/` into each cluster's folder, beside the cluster's page and its pictures, under the copy's own file name. Which file of a cluster's folder is a copy is then a matter of names the guard would have to keep in step with `deliverable`.

## Decision

### 1. The written rule reaches every tree, wherever it lies

**No agent reads a file in a deliverable tree, and none writes there**: the copies, the pages written from them and their pictures, the index, `documents.csv` and the marker, in a tree drawn in a working directory or at a named root, under an allowed root or outside every one. An agent may list a tree's names, as it may an archive's (ADR-264 §3, §4). This extends ADR-274 §8's rule, which kept agents from the copies, to the whole tree, and holds whatever the guard sees (§3). A tree is the operator's: its pages are read in a browser, and a test of the writer draws a tree of its own from fixtures.

### 2. The guard reads a tree as outside the allow list

**A folder that directly holds a name `vespera-deliverable.txt` is the top of a deliverable tree, and every path at or beneath it is refused as a path outside the allow list is**: Glob and a listed command (ADR-264 §3) are let through, and Read, Grep, Edit, Write, NotebookEdit and every other shell command are refused. The refusal names the tree's top and this record.

- **Where the marker is asked.** Of a path that the allow list, by its text and by where its links lead, has already let through, and of no other: from the path itself up to the last folder the list names, each folder once in a call, and for both the path as written and where its links lead. A path outside the list costs no question of the file system, as ADR-264's Decided here 3 has it.
- **What a marker is.** Any name there: a file, a folder or a link. Only an answer that the name is not there is no marker (the guard's `NOT_THERE`); any other answer closes the folder, as a name that cannot be followed is refused (ADR-201). A marker is not read: its two lines are `DeliverableRoot`'s (ADR-276 §3), and a forged or empty one only closes more.
- **No name of the tree is listed.** The rule closes the whole tree from its marker down, so it does not move with the tree's layout, flat or per cluster (#570), with a page split into several (#571), or with a copy's name.
- **A working directory stays open as the place it lies in** (ADR-264 §2) but for the tree drawn in it, `deliverable/<generation run>/`. Its `vespera.db`, its logs and its pages are open as before, and `deliverable/` itself is open as the folder above a tree.
- **The guard's constant** is `DELIVERABLE_MARKER`, equal to `DeliverableRoot.MARKER_FILE_NAME`.

### 3. What the guard does not see

The written rule (§1) is the only protection in each of these, beside what ADR-196 §5 and ADR-215 §8 list:

- **A search, copy, move or removal that names a folder above a tree**, a Grep of a working directory, `grep -r`, `cp -r`, `mv` or `rm -r` of a folder holding a tree: the guard does not walk down from a path it is given (ADR-264 §2).
- **A tree drawn before ADR-276**, which carries no marker until its next drawing.
- **A tree whose top lies above the last folder the allow list names**, since no marker is asked above it.
- **A path a command builds at run time, a wildcard and a rooted POSIX path in a shell command**, which the guard does not read as paths (ADR-196 §5).

### 4. The operator installs it

**The operator need not keep a deliverable root off `allowed-paths.local.txt`**: a root under an allowed root is refused by its marker, and one outside the list is refused as every path there is. Naming a root in a local allow list opens nothing of the tree.

The guard lies in a closed `.claude` folder, so the change is drafted at **`docs/agents/guard-draft/hooks/private-paths-guard.mjs`**, laid out as `.claude` is, which `VESPERA_GUARD_DRAFT` names (ADR-215 §7). The operator puts it in place at `.claude/hooks/private-paths-guard.mjs` and removes `docs/agents/guard-draft` in the same change. Until then §1 is the only protection of a tree under an allowed root.

## Decided here

1. **The written rule reaches every tree and the whole of it** (§1). A tree's pages carry text and pictures of documents, and nothing of the tree is an agent's to read: a writer's test draws its own.
2. **The guard recognises a tree by its marker and closes all of it** (§2). Closing the copies alone would need the guard to tell a copy from a page by its name, which #570 makes a rule of `deliverable`'s; closing from the marker down keeps one name in step, which a test holds.
3. **A tree is read as outside the allow list**, not refused to every tool: what prints names is let through, as it is in an archive.
4. **Any name at the marker's place is a marker, and it is not read** (§2): the guard fails closed, and depends on nothing `DeliverableRoot` writes but the name.
5. **The marker is asked only beneath an allowed root**, so a path outside the list still costs no lookup (§2).
6. **The operator does not keep a root off the allow list** (§4): the marker is what closes it.
7. **The change is a draft in `docs/agents/guard-draft`** (§4), held by its tests while it is there and by the same tests against the installed guard once it is gone.

## Consequences

- **An agent cannot open a tree's index or pages** under an allowed root, in a working directory's `deliverable/` included. It lists their names, and a test of the writer reads the tree it drew.
- **Every call the guard lets through by the allow list asks the file system once for each folder from its path up to the allowed root**, cached for the call: a lookup of a name that is not there, as `describe` already makes.
- **An agent cannot remove a marker** with a tool or a command that names it, since the marker is in the tree.
- **`deliverable`'s marker name is held by the guard**: renaming `MARKER_FILE_NAME` fails `TheGuardKnowsATreeByItsMarkerTest` until the guard is changed with it.
- Nothing under `src/main` changes, so no run id moves.

## What changes, and who puts it in place

- **`.claude/hooks/private-paths-guard.mjs`**: §2, its header and its refusal text, drafted at `docs/agents/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator (ADR-215 §7).
- **`src/test/hooks/deliverable-tree-guard.test.mjs`**, new, and its line in `.github/workflows/build.yml` on both jobs.
- **`AGENTS.md`** and **`docs/agents/private-paths-guard.md`** state §1 to §4.
- **Not** `.claude/allowed-paths.txt`, `.claude/settings.json`, the wrapper, the counting script, `src/test/hooks/private-paths-guard.test.mjs` or anything under `src/main`.

## Alternatives weighed

- **The written rule alone, the operator keeping a root off the allow list.** It leaves the copies open to every tool wherever an allowed root holds a tree, the working directory's own `deliverable/` included, and ADR-276 wrote the marker for this rule.
- **Closing only the copies.** Under #570 a copy and a page share a folder and a copy keeps its own name, so the guard would hold a list of the names `deliverable` gives pages and pictures, and a copy named `index.html` is renamed only by that rule.
- **Closing a tree to every tool, Glob and listers included.** A name is not content (ADR-264 Decided here 2), and an archive's names are listed.
- **Reading the marker's lines.** It ties the guard to `DeliverableRoot`'s format, and a marker that does not parse would have to close the folder all the same.
- **Recognising a tree by `deliverable/` beneath a folder holding `vespera.db`.** It sees no named root.
- **Walking down from a search root to find a tree.** ADR-264 removed that walk, and its bound refused large folders.

## What this does not decide

- How the control panel shows a tree to the operator.
- A guard for a tree drawn before ADR-276, which gains its marker at its next drawing.

## Tests

`src/test/hooks/deliverable-tree-guard.test.mjs`, through the wrapper Claude Code calls, against the draft while it is there and against the installed guard once it is gone; its header lays out the fixture. Two parts of §2 have no case: that no marker is asked outside the allow list (Decided here 5), since a lookup the guard makes is not seen through the wrapper, which reports only its exit code; and that an answer other than "not there" closes a folder, since such an answer, a denied lookup, cannot be made for a process that runs as root, as CI's Linux job does, or on NTFS, and F116 holds the nearest case, a marker that is a folder. `src/test/hooks/private-paths-guard.test.mjs` holds the draft unchanged, with `VESPERA_GUARD_DRAFT` naming a copy of it outside every `.claude` folder.

| Cases | What they hold |
| --- | --- |
| F101 to F109 | Read of a named root's index, a copy in a cluster's folder, a cluster's page, the marker and the manifest; Read of a copy and of the index in a working directory's flat tree; Write of a new file and Edit of the index are refused (§1, §2) |
| F110, F111 | Grep whose path is a tree's top, and Grep whose glob names a tree, are refused (§2) |
| F112 to F115 | `cat` of a tree's index, `cd` to a tree then `cat`, `rm` of the marker, and `cat` of a copy relative to a partition's folder are refused (§2) |
| F116 | a folder whose marker is a folder is a tree (§2) |
| F201 to F203 | Glob beneath a tree, `ls` of a cluster's folder and `stat` of the marker are let through (§2) |
| F301 to F305 | a working directory's page, a folder beside a tree, a tree with no marker, a Grep of a working directory above its tree and `cat` of a working directory's page are let through (§2, §3) |
| F401 | Read through a link into a tree is refused (§2) |
| F402, F403 | a tree outside the allow list is refused to Read and let through to `ls` (§2) |
| F501 | the refusal names the tree's top and this record (§2) |
| `deliverable.TheGuardKnowsATreeByItsMarkerTest.theGuardLooksForTheMarkerTheWriterWrites` | the guard's `DELIVERABLE_MARKER` is `DeliverableRoot.MARKER_FILE_NAME` (§2) |
