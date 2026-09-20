# ADR-134 — A line break in a value is folded, and three escaping rules stand because there are three surroundings

- **Date**: 2026-09-20
- **Status**: accepted
- **Reconciles with [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md)**, which consolidated six copies of one escape method into `ReportPage` on the reasoning that *"six copies are six chances for one to drift"*. Nothing in that record moves. This one states the boundary ADR-130 did not have to state, because it never met a second grammar: ADR-130 forbids a **second copy of a rule**, not a **second rule**. The Decision says what distinguishes the two, and what would bring `Deliverable` inside ADR-130's remedy.
- **Rests on**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) (the tree resolves its own links with no database and no network, which is what makes reading the file as plain text a supported way to read it), [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) (the manifest exists so a consumer can load it into a table without parsing anything first), [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md) (a cluster's label is a document's own Docling title, falling back to its filename stem), [ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md) (a stored path is separator-normalised to `/`, which is what makes one of the three label sources provably backslash-free).
- **Raised by the architect gate on [#248](https://github.com/algernon28/vespera/pull/248)** — the fix for [#246](https://github.com/algernon28/vespera/issues/246) — which judged neither question a blocker for that fix and filed them as [#249](https://github.com/algernon28/vespera/issues/249). Both decisions were taken in that pull request and recorded only in javadoc.

## Context

`Deliverable` writes values this project did not compose into structures this project did compose. There are three sources and none of them is ours:

- **A cluster's label** is the lead document's own title as Docling read it, falling back to its filename stem and then to `Cluster %d` (ADR-106, `ClusterLabel.derivedFrom`).
- **A cluster's title** is what the generation model answered.
- **A partition's heading, a membership entry and two manifest columns** are paths out of the archive.

Issue #246 was what happens when such a value is written into a table cell with only the pipe escaped: a title that wrapped in the document it came from ended the table rather than the row, and `index.md` — the first file a reader opens — listed a fraction of what it holds while failing at nothing. #248 fixed it with `onOneLine`, `inACell` and `asLinkText`, and left two questions unanswered in the record and one answered only by an implementer.

### The four questions

1. A line break in a value is **folded** to a space. GFM documents `<br>` as the way to put a break inside a cell, and `<br>` would *preserve* a title that genuinely wrapped rather than flattening it. Folding was chosen anyway, and the argument for it was not written down.
2. **Three escaping rules stand** in one class — `inACell`/`asLinkText`, `escapeLinkText`, `quoted` — where ADR-130 had just finished collapsing six copies of an escape method into one. A reader arriving from ADR-130 reads three-rules-on-purpose as the defect ADR-130 was written against.
3. `onOneLine` matches `\s+`, which in Java is ASCII-only: a non-breaking space passes through untouched. Nothing said whether that is a boundary that was chosen or one that was not noticed.
4. #248 added `.replace("\\", "\\\\")` to `inACell`. That is cheap, but as filed it rested on a claim about what a Docling title may contain — a claim about the data, which is not an implementer's to make.

## Decision

### 1. A break is folded, and `<br>` loses on three counts

**Escaping was never one of the options.** CommonMark has no escape for a line ending inside a table row or a heading: a backslash before a line ending is a hard break *within a paragraph* and nothing else, and an ATX heading is one line by definition. So the real choice is two-way — fold, or emit `<br>` — and `<br>` loses:

**It preserves the wrong thing.** The strongest case for `<br>` is that a title that genuinely wrapped stays wrapped. But the break in a Docling title is a fact about the **page the title was typeset on**, not about the title: `DocumentTitle.of` returns the text of the response's `title`-labelled item, which is assembled from a title block laid out to a column width. Nothing distinguishes a break the author meant from one the typesetter's measure imposed, and the second is overwhelmingly the common case. `<br>` reproduces the source document's column width on our page. A space reproduces the words. Where the author did mean a break, folding costs a break that no reader of an index table wanted; where they did not, `<br>` imports a layout accident into a name. The asymmetry favours folding.

**It does not work in all the places the same value lands.** The label reaches a table cell, a link's text, the cluster page's `#` heading, and — as a path — the partition's `##` heading and two CSV columns. `<br>` is markup in exactly one of those. In a heading it becomes part of the text a renderer slugs into an anchor. In link text it puts markup inside the words a reader clicks. In the manifest it is six literal characters in a data column that ADR-104 says a consumer loads into a table without parsing anything first. Folding is one rule that is right in every one of them; `<br>` would need a companion rule everywhere else and a reason for each.

**It would make the markup ours.** `<br>` is not a value passing through — it is markup Vespera authors on behalf of text it does not trust, on the strength of a guess about what that text meant. That is a different act from failing to escape something, and it is the one we decline. *This is deliberately not the claim that the file contains no HTML*: see "What this record does not decide".

**And the file stays honest read as plain text.** ADR-103's claim for the deliverable is that every link in it resolves with no database and no network, which means reading the `.md` directly is a supported way to read it. `Fire Suppression<br>Retrofits` reads as noise there. `Fire Suppression Retrofits` reads as the title.

**The cost is bounded.** HTML collapses runs of whitespace anyway, so against a rendered page folding loses nothing except the break itself — and the break is the thing the first point says we should not be preserving.

### 2. Three rules stand, and this is not what ADR-130 forbids

**ADR-130 consolidated six copies of the same rule for the same grammar, scattered across six classes.** `escape` was duplicated character-for-character in `ArrangementReport`, `ClusterSizeReport`, `ConfidenceDistributionReport`, `FormatMixReport`, `RelevanceLabellingReport` and `SeedCorpusComparisonReport`, all of them escaping HTML body text. The defect it named was **drift**: six copies could come to disagree, and nothing would notice, so the same value would be escaped one way on one page and another way on another for no reason a reader could name.

**`Deliverable` has three rules for three grammars, in one class, each with one family of call sites.** They are not copies of each other and could not drift apart, because there is nothing for them to drift *from*. The distinction is: ADR-130 forbids a **second copy of a rule**; it does not require **one rule for every grammar**.

**The proof that they are not copies is what merging them would do.** Give all three the union of the rules and every one of them becomes wrong:

- A bracket escaped inside a CSV field is a literal backslash in the data the consumer loads: `[` and `]` are legal in an NTFS filename, so `Report [2019].pdf` would reach the manifest as `Report \[2019\].pdf` — in a file ADR-104 says a consumer loads straight into a table without parsing anything first, and where the path column has to name the document as the archive holds it.
- Folding applied to a membership entry shows a path that is not the path: a run of spaces is legal in an NTFS filename and would come back as one space, and the entry exists so a reader can match it against the archive by eye.
- RFC 4180 quoting applied to a cell wraps a cluster's name in quotation marks a reader sees.
- A pipe is structural in a cell and an ordinary character in a list item; a backslash matters in a path shown verbatim and matters in a cell for a different reason (see 4); a newline ends a table and is legal inside an NTFS filename.

A consolidated rule here would not be broader than the three — it would be *incorrect* in all three of them. Consolidation is right where the copies would be identical and wrong where they would not.

**What is common is already shared.** `onOneLine` is the one rule that is genuinely the same wherever it applies, and it exists once: `inACell` calls it, `asLinkText` reaches it through `inACell`, and the two headings call it directly. That is ADR-130's remedy applied where ADR-130's premise holds.

**The rules are private, named for their surrounding, and are the only way a value reaches it.** That is the weaker form of ADR-130's "escaping by construction". It is weaker because nothing enforces it: a new `append` of an unescaped value compiles. The guard is the tests, not the type system.

**What the drift risk actually is here, and where to look for it.** Not three rules disagreeing — a **fourth surrounding arriving with no rule**, which is #246 exactly, and which is why the escape is applied to every value rather than to the ones that look risky. Or one of these three being copied into a second class, at which point ADR-130's remedy applies verbatim and the copy is the defect.

**Reopen trigger.** A second class under `synthesis` grows a Markdown escaping method. Then there are two copies of one rule for one grammar, ADR-130's premise holds, and the consolidation it decided is the answer.

### 3. The ASCII-only whitespace class is a decision

**`\s+` matches exactly the characters that can end a Markdown construct, plus the ones that are safe to collapse, and no others.** CommonMark's line endings are `\n`, `\r` and `\r\n` and nothing else: U+0085 NEL and U+2028/U+2029 LINE/PARAGRAPH SEPARATOR are not line endings to a CommonMark parser, so a value carrying one does not end a row or a heading. Java's `\s` covers `\n` and `\r` — the whole hazard — along with space, tab, vertical tab and form feed, which are safe to fold.

**Widening it would buy no safety and would cost something real.** `\p{IsWhite_Space}` would additionally collapse U+00A0 and the thin, figure and hair spaces a typeset title uses on purpose, turning an author's spacing into ordinary spaces to defend against a hazard that does not exist. A non-breaking space is a character of the title, not a break in it, and it passes through for the same reason the rest of the title does.

`String.strip` is consistent with this without being made so: `Character.isWhitespace` excludes U+00A0 too, so a value bracketed by non-breaking spaces keeps them at both ends.

**Reopen trigger.** A renderer the deliverable is read with treats some other character as a line ending — at which point the class widens to exactly that character, and still not to every Unicode space.

### 4. The backslash belongs in the cell rule, on a ground that is not about the data

**It stays.** The ground it was filed on — that a Docling title might end in a backslash — is true but is the weaker half of the argument, and filing it that way invites a later reader to delete the rule on discovering that titles "never" carry one.

**The load-bearing ground is composition.** `inACell` *inserts* backslashes, and `asLinkText` inserts more on top of them. A rule that introduces its own escape character without first escaping that character is not a function of its input in the way it claims to be: a literal backslash in the value merges with the escape added after it. Concretely, a label reading `Retrofits \| Phase 2` yields `Retrofits \\| Phase 2` without the rule — an escaped backslash followed by a **live pipe**, which is #246's defect reintroduced by the thing written to prevent it. This holds whatever the data turns out to be; it is a property of the rule, and it is why the backslash is escaped **first** in `inACell`.

**The same ordering in `escapeLinkText` is not carried by this argument, and is kept as cheap defence against an input ADR-051 rules out.** That rule is applied to `member.path().value()`, an `OccurrencePath`, which is separator-normalised to `/` and cannot hold a backslash on NTFS — so the composition hazard above has no input to arise from there. It is left in place because it costs nothing and because a rule that escapes brackets without escaping the escape character is the wrong shape to leave lying about for the next reader to copy; it is not evidence for the decision, and nothing here should be read as claiming a path can carry one.

**What the data can carry was established rather than assumed, and it removes the reason to call the above academic.** `inACell` guards two values, from three sources:

- **The cluster title is what the generation model wrote.** It is unbounded text under no filesystem constraint whatever, and a model writing a Windows path, a UNC share or LaTeX (`\alpha`, `\%`) is ordinary rather than exotic. This source alone settles the question.
- **The Docling title is the text of the response's `title`-labelled item**, filtered for emptiness and nothing else. It is arbitrary characters lifted out of a title block in a PDF, DOCX or PPTX, where a backslash is an ordinary printable character. `Backup\Restore Procedures` is a plausible document title.
- **The filename-stem fallback provably cannot carry one.** A stored path is separator-normalised to `/` (ADR-051) and NTFS forbids a backslash in a filename, so the stem is backslash-free. This is the one tier where "defending against nothing" would have been the right reading — and it is the fallback, not the primary source.

So: one source is provably safe and two are arbitrary text. The rule is recorded as a decision about the **rule**, not as a claim about the data.

## Consequences

**A break costs the break and nothing else.** A two-line title is listed on one line, in the index table, in the link text, in the `##` partition heading and in the cluster page's `#` heading. A reader of the raw `.md` sees a name; a reader of a rendered page sees what a rendered page would have shown for a folded run of whitespace anyway.

**A value with none of these characters is untouched.** All four rules are conditional in effect: `quoted` quotes only where a comma, quote or line ending is present, and the three Markdown rules are `replace` calls that do nothing to a value carrying none of what they escape. An ordinary title reads in the deliverable exactly as it reads in the document it came from.

**Folding is deliberately not applied to a membership entry.** `escapeLinkText` does not call `onOneLine`, and that is the "one rule per surrounding" reasoning having teeth rather than an omission: a link's text may span a line ending in CommonMark, so a break there does not end the link, and the entry is a path the reader matches against the archive by eye, where folding would show a path that is not the path. The residual is narrow and accepted: a filename carrying a **blank line** — two consecutive line endings, legal on NTFS and already the reason `quoted` exists — would end the paragraph the entry sits in. No such name has any other defence anywhere in the tree, and inventing one for this surrounding alone would be the per-value rule the Decision refuses.

**The tests are where the three rules are held apart.** `DeliverableTest` pins each surrounding against the value that would break it: a name carrying a line break and a pipe, a name carrying a bracket, a name carrying a backslash beside a pipe. The last of those is what would fail if the composition argument in (4) were ever taken out on the grounds that a title cannot hold a backslash.

**What that leaves undefended is exactly what (4) says is not load-bearing.** The backslash test exercises the branch of the index row that has no link, so it pins `inACell` and never `asLinkText`'s composition over an already-escaped backslash — that path is covered separately by the bracket test, and it is correct. And the suite would stay green if the backslash were removed from `escapeLinkText` alone, which is the consistent consequence of recording that rule as cheap defence against an input ADR-051 rules out: a test pinning it would be pinning a case that cannot occur.

**A fourth surrounding gets a fourth rule, and says which one it is.** That is the shape this record establishes, and the thing to notice is a value written into a new structure with one of the existing three, or with none.

## What this record does not decide

**`<` and `&` pass through unescaped. That is a defect against shipped code, and it is open as [#251](https://github.com/algernon28/vespera/issues/251).** None of the four rules escapes them, so a title reading `<b>Retrofits` is inline HTML to a CommonMark renderer. It is mild — GitHub sanitises, the values are names rather than script — and it is genuinely of #246's family: untrusted text changing the page it is listed on. It is not settled here because it is a fifth question that arrived while answering four, and because what it wants first is a measurement of which renderers the deliverable is actually read with rather than an argument. The ticket carries that measurement as its first criterion, and closing it restores `AGENTS.md`'s claim that no defect is known and open against what ships.

This means the argument in (1) is precisely *"Vespera does not author markup on behalf of untrusted text"* and deliberately not *"this file contains no HTML"* — the second is not true today, and an ADR that leaned on it would be resting a decision on a claim the tree contradicts.
