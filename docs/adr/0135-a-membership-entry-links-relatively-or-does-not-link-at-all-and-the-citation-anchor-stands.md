# ADR-135 — A membership entry links relatively or does not link at all, and the citation anchor stands

- **Date**: 2026-09-21
- **Status**: accepted
- **Amends**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) — one sentence of its Consequences (*"A `file:` link satisfies ADR-103's test"*) and one bullet of *What a link physically is* (*"In the prose, a link is an absolute `file:` target"*). Everything else in that record stands: the originals are still referenced and never copied, the extracted text is still refused, the membership is still complete, the manifest is still root-relative, and nothing is still stat-ed.
- **Answers**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) — it set the test *"every link inside the deliverable resolves with no database, no ledger and no network"* and never said whether the test binds through a renderer. This says it does.
- **Rests on**: [`docs/research/deliverable-markdown-rendering.md`](../research/deliverable-markdown-rendering.md) §4 and §6 — every renderer claim below is one row of that record, measured on 2026-09-21 across `markdown-it` 15.0.2, `marked` 18.0.13, the `commonmark` 0.31.2 reference and GitHub's live `POST /markdown`, in seven configurations. Also on [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (a citation resolves to a membership entry by construction), [ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md) (a stored path is root-relative and `/`-separated) and [ADR-134](0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md) (three escaping rules for three surroundings — this record adds no fourth).
- **Settles** [#253](https://github.com/algernon28/vespera/issues/253).

## Context

ADR-103 gave the deliverable one test and stated it twice: **every link inside the deliverable resolves with no database, no ledger and no network.** ADR-104 then wrote the membership entry as an absolute `file:` target and argued it passes — *"the filesystem resolves it, and no part of this tool is needed to follow it"*.

That argument is true of the filesystem and false of the reader, because a renderer sits between them. Measured, with a link carrying no special characters at all, a `file:` destination reaches the filesystem in **two of seven** renderer configurations (§4a, §6c):

- `markdown-it` refuses the scheme in its documented `validateLink` blocklist, under both `html` settings, and prints `[archive/plain.pdf](file:///D:/corpus/archive/plain.pdf)` at the reader as literal text.
- GitHub strips the `href` and keeps the text.
- `commonmark` with `safe:true` emits `<a>` with no `href` at all.
- `marked` and `commonmark`'s default resolve it.

So the hop this tool owes a reader who doubts a sentence — from a membership entry into the archive — is not a hop at all in most of what could render it, and in two of the failures the page shows Markdown source. Nothing in the tree ever asked the filesystem, so ADR-104's sentence was never wrong about the filesystem; it was about the wrong layer.

### What the question actually is

ADR-103's test could be read two ways, and the research record named both. Either *the target is reachable without the ledger*, in which case a renderer that declines to linkify is out of scope and only ADR-104's sentence needs a footnote; or *a reader can follow the link*, in which case the form of the link is wrong and `Deliverable` changes.

The rest of ADR-103 answers this. It chose Markdown over HTML because "the deliverable is the thing carried somewhere else, and every wiki importer eats Markdown"; it made `index.md` mechanical so "the tree can be read without the database beside it"; ADR-134 rests on reading the `.md` as plain text being a supported way to read it. Every one of those is a statement about a reader. A test about links in a document written to be read, which a reader can never exercise, tests nothing.

## Decision

### 1. ADR-103's test binds through the renderer

**A link resolves when the renderer the tree is opened with emits it as a link, and following it needs no database, no ledger and no network.** A destination that arrives at the reader as printed Markdown source has failed the test, not passed it on a technicality.

This is stated as an amendment rather than a discovery: ADR-103 did not consider renderers, and reading its test as filesystem-only would keep the sentence true while leaving the deliverable's one navigational promise unkept.

### 2. A membership entry's link is relative to the cluster file

The destination is the path from the directory the cluster file sits in to the original in the archive: `../../../../corpus/archive/R%26D%20report.pdf` rather than `file:///D:/corpus/archive/R&D report.pdf`. Measured in **seven configurations of seven** (§6a), the `..` segments and the separators arriving at the reader exactly as written.

It also survives one move the absolute form does not: a deliverable and an archive carried somewhere together keep every link. ADR-104 was explicit that its links "resolve on the machine that produced them, and every one of them dies if the archive moves"; that is now half true instead of wholly true, and §Consequences states the move it costs.

### 3. Where no relative path exists, the entry carries no link at all

On Windows a deliverable on one volume and an archive on another have no relative path between them (§6e: `relativize` throws `IllegalArgumentException`). In that case the entry is the document's root-relative path as text, escaped exactly as the link text is today, and **no `file:` URL is ever written into the deliverable**.

**Why not fall back to today's `file:` link.** It is measured to be a link in two of seven configurations, and in the two `markdown-it` ones the reader is shown `[archive/plain.pdf](file:///…)` as printed source. That is #246's family — a page saying something other than what it means to say — and it would be the only instance of it this tool inflicts on itself rather than inheriting from the archive. The readers it does serve are `marked` and `commonmark`'s default: libraries a program embeds, and a program has `documents.csv`, which ADR-104 wrote precisely so nothing downstream has to parse Markdown. The surfaces a person opens the tree with — a `markdown-it`-based preview, GitHub, or the raw file as text — get nothing from the `file:` form and get a clean path without it.

**Why not write every entry as plain text and drop the conditional.** Because where the relative path exists it works in every renderer measured and costs nothing, and the reader loses a click to spare the writer one predicate. `documents.csv` and the recorded root do give a *tool* mechanical resolution, and neither gives a *reader* anything to click; the deliverable exists for the reader, and the manifest already exists for the tool. The simplification is refused for what it takes from the reader, not for what it saves.

**The fix is therefore partial, and this record says which case it does not help.** A cross-volume install gets a legible unlinked path where it had a link that most renderers printed as source. It does not get the hop. Nothing here closes that, and no option considered does: ADR-104 already refused a junction or symlink `originals/` for the Windows-first reason ADR-103 refused a `latest` link, and copying the originals is the decision ADR-104 exists to refuse.

### 4. Which case applies is decided by two directories, and never by a document's name

The recorded corpus root and the cluster file's own directory are both directories this tool composed or canonicalised, so both can go through `Path`. **A document's own name cannot**: NTFS allows characters the JDK's path parser refuses, and `Path.of("D:/corpus/a\"b.pdf")` throws `InvalidPathException` (§6e) — which is why `fileUrl` composes by escaping rather than by resolving, and that stays true.

So the relative form is used when, and only when, the recorded root and the tree are both absolute and share a root; the document's root-relative path is appended to the result as text. Anything else — different volumes, a UNC share against a drive letter, a recorded root this machine's parser refuses — is the unlinked case. It is a predicate over two paths rather than a caught exception where that is expressible, because a throw escaping the writer rolls back every fault row the invocation has already recorded (ADR-111), and an entry that cannot be linked is not a reason to lose a run's faults.

### 5. No fourth escaping rule, and the encoding rule is the one already written

ADR-134 has three rules because there are three surroundings — a cell, a list, the CSV — and this changes none of them: what moves is the **destination**, and the text beside it is still `escapeLinkText`'s. The destination's encoding is the rule `fileUrl` already applied — `java.net.URI` quoting plus `%28`/`%29`, because a Markdown destination ends at the first unescaped `)` — carried over onto a relative base instead of an absolute one. Measured (§6b): with that encoding the entry is a link in all seven; with a raw space in the destination it is a link in **none** of the seven, the whole construct printed as text. A bare `&` needs nothing, `%` must be `%25` and `#` must be `%23`, all of which that rule already does.

**One ambiguity the relative form has and the absolute form did not**, recorded so nobody has to find it twice: a relative destination whose **first segment** contains a colon — `a:b/doc.pdf` — is read by a renderer as a scheme, and `java.net.URI`'s quoting leaves a colon in a path alone. It is unreachable in practice on two counts: a colon is illegal in a Windows filename, and the first segment is `..` wherever the tree does not sit directly inside the corpus root, which is the only arrangement that produces a destination beginning with a name at all. It is recorded rather than defended against, because a rule guarding an input no supported platform can produce is a rule the next reader has to disprove before touching the line.

### 6. The citation anchor stands, and where it degrades is recorded rather than fixed

`<a id="document-n"></a>` stays exactly as it is, and the citation link stays `#document-n` (ADR-109). Measured (§6d), the pair lands in `markdown-it` `html:true`, `marked` and `commonmark`'s default; under `markdown-it`'s `html:false` default the tag is printed at the reader; under `commonmark safe:true` it is dropped; and on GitHub the anchor survives with its `id` rewritten to `user-content-document-1` while the citation's `href` is left as `#document-1`, so the two no longer name the same thing in the API's own output. Whether github.com's page scripts reconcile them in a browser was not established.

**This is not the same question as the link, and it does not get the same answer, for one reason:** the link's failure is avoidable and the anchor's is not. There is no Markdown construct for an anchor. Every substitute rests on an anchor a renderer derives for itself from a heading's text, which ADR-109 refused on its own ground — two viewers are free to slug differently, and a citation would then resolve in one and silently land elsewhere in the other — and which GitHub's `user-content-` prefix shows is not even stable across one renderer's surfaces. The alternative to a raw anchor is not a better anchor; it is no citation hop at all, which would spend ADR-109 to satisfy one library's default setting.

**The rule this record applies, stated once so the two answers do not read as inconsistent: the tool does not emit a construct whose failure it can avoid.** The `file:` destination has a working replacement, so it goes. The anchor has none, so it stays, measured and written down.

## Consequences

**A membership link now depends on where the tree sits as well as where the archive sits.** Moving the pair together keeps every link, which the absolute form did not; moving the tree alone now breaks them, which the absolute form survived — in the two configurations where the absolute form was a link at all. The trade is taken in that light: what is lost was working for `marked` and `commonmark`, and what is gained works everywhere measured.

**Re-pointing a moved archive is unchanged.** `index.md` still records the root once and `documents.csv` still holds root-relative paths, which is what ADR-104 bought and what an unlinked entry resolves against by hand.

**The cross-volume case keeps today's limitation in a quieter form**, and nothing in the tree announces it. 6b writes no advice to the operator, the entry is legible either way, and a warning about drive letters on a page about fire suppression retrofits is not what this stage exists to say.

**The ordinary installation is in the linked case, and one operator choice silently leaves it.** The shipped default is `vespera.working-dir: ${db-dir:${user.dir}/.vespera}`, which is absolute, so a tree and an archive on one volume link and nothing has to be set for that. An operator who passes a **relative** `--db-dir=` gets the unlinked form on every entry of every page instead, with nothing saying why. That follows from the decision above rather than escaping it — a relative working directory has no route to an absolute archive that survives the reader's own working directory — and it is intended: the alternative is either a link composed against a directory the reader may not be standing in, or a refusal at the terminal stage over a path that is otherwise perfectly usable. What the operator loses is a click, and the entry still names the document beneath the root `index.md` states.

**`ClusterFileTest` and `DeliverableInvocationTest` now have to state the case they are in.** A fixture naming `D:\archive` while the temporary working directory happens to sit on `C:` was pinning the absolute form by accident of the machine; each case is now forced — a corpus root beside the working directory for the relative form, a root on another volume, guarded to Windows, for the unlinked one.

**Nothing in the ledger, the manifest or the index changes**, and no schema moves.

**What stays open is what both #251 and #253 have carried from the start**: which renderers an operator actually opens the tree with. VS Code's preview, IntelliJ's, Obsidian and github.com in a browser are all unmeasured, and the decisions above rest on libraries and one HTTP API. Every option here was chosen to be the one that fails in the fewest measured configurations, which is the strongest available ground and is not the same as knowing the reader.
