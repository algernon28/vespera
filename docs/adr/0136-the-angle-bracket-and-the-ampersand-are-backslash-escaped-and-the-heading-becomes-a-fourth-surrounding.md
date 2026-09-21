# ADR-136 — The angle bracket and the ampersand are backslash-escaped, and the heading becomes a fourth surrounding

- **Date**: 2026-09-21
- **Status**: accepted
- **Extends**: [ADR-134](0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md) — it settled that three escaping rules stand because there are three surroundings, and noted that a fourth *surrounding* would get a fourth rule while a fourth *character class* inside the existing ones "is a different shape and wants stating". This is that shape, and it states it: no rule is added, three rules each gain two characters.
- **Rests on**: [`docs/research/deliverable-markdown-rendering.md`](../research/deliverable-markdown-rendering.md) §1, §2, §3 and **§7** — every claim below is a row of that record, measured on 2026-09-21 across `markdown-it` 15.0.2 (both `html` settings), `marked` 18.0.13, the `commonmark` 0.31.2 reference (both `safe` settings) and GitHub's live `POST /markdown`.
- **Settles** [#251](https://github.com/algernon28/vespera/issues/251).

## Context

`Deliverable` writes values it did not compose into four positions: a table cell in `index.md`, the text of a link in that table, an ATX heading, and a numbered membership entry. A cluster's label is a document's own Docling title falling back to its filename stem (ADR-106); a cluster's title is what the generation model answered; a partition's heading is a path out of the archive.

Under ADR-134 those values are guarded against the pipe, the brackets and the backslash, and a line break is folded. **Nothing guarded `<` or `&`**, and §3 measured three distinct failures, of which only the first is the one a reader would notice:

- the tag goes **live** — `Retrofits & <b>Phase 2` in link text renders with the bold applied, and GitHub supplies a closing `</b>` the source never had;
- the tag is **silently deleted** — `## Roofing & <draft> surveys` renders on GitHub as `Roofing &  surveys`, the word gone, the anchor slug `roofing---surveys`, and nothing to say anything was removed;
- the tag is **invisible but present** — `marked` and `commonmark` emit `<draft>` and a browser renders nothing for it.

The heading is the worst-placed of the four, because `onOneLine` folds and escapes nothing at all, so a heading is the one position where a value reaches a renderer entirely unmodified.

### The question this record had to get right, and got wrong twice

#251 framed `&` as "the harder half": escaping is safe but costly, because a title reading `R&D Retrofits` would become `R&amp;D Retrofits` for a plain-text reader, and ADR-103 makes plain text a supported way to read the tree.

§1 measured that and inverted it. Writing `&amp;` is not safe-but-costly, it is simply **wrong**: every renderer already renders a bare `&` correctly, so `&amp;` damages the rendered page and the plain-text reader alike.

**Both of those are about one operation — replacing the character with an HTML entity — and neither is about the operation this record adopts.** CommonMark permits any ASCII punctuation character to be backslash-escaped. `\<` and `\&` are not `&lt;` and `&amp;`; they are consumed by the renderer rather than printed by it. §7 measured them, and the result is different from both earlier answers.

That the framing moved three times — the ticket, §1, and then §7 — is the reason this section exists. The distinction is easy to collapse and was collapsed by everyone who looked at it, so it is written down here rather than left for a reader to rediscover.

## Decision

### 1. `<` and `&` are backslash-escaped, not entity-encoded

A value written into Markdown has `<` written as `\<` and `&` as `\&`. Measured in six configurations, in all four positions (§7): the reader sees a literal `<` and a literal `&`, nothing goes live, nothing is deleted, nothing is invisible, and `R\&D Retrofits` displays as `R&D Retrofits`.

Writing `&lt;` or `&amp;` instead is refused, on §1's ground: it corrupts the rendered page and the plain-text reader together, and no renderer measured needs it.

### 2. Both of ADR-134's mechanisms apply, each where it fits

ADR-134 records two different shapes and says they get different answers: a fourth **surrounding** gets a fourth rule, and a fourth **character class** inside the surroundings that already exist "is a different shape and wants stating". This change is both at once, and conflating them is what a reader would otherwise do.

- **`<` and `&` are a fourth character class**, and they join the three existing rules rather than forming one. A rule exists here because a *grammar* does — a cell has a pipe, a list item does not, the CSV answers to a parser — and `<` and `&` are hazardous in every Markdown surrounding for the same reason in each. A rule of their own would have no grammar to be about, and ADR-130's argument against a second copy of one rule would apply to it at once.
- **The heading is a fourth surrounding**, and it gets a fourth rule, `inAHeading`. That is ADR-134's own rule applied where its premise holds, and §4 says why the alternative is worse.

### 3. The ordering is fixed: the backslash first, then everything else

`\<` and `\&` insert backslashes, so they land under the composition argument ADR-134 §4 settles: a rule that inserts its own escape character must escape that character first, or the escape it adds merges with a literal one already present. The existing `.replace("\\", "\\\\")` already runs first in `inACell` and `escapeLinkText`, and the two new replacements go after it and before the grammar-specific ones.

### 4. The heading gets its own rule rather than borrowing the cell's

A heading carries only `onOneLine` today because it has no pipe and no brackets to guard. It does have `<` and `&`, and §3 measures it as the worst-placed of the four positions — the one where a value reaches a renderer entirely unmodified.

Reusing `inACell` there was considered and refused. It would escape the pipe as well, and a pipe is not structural in a heading: the result renders correctly but writes `\|` into a value a plain-text reader is looking at, for a hazard that does not exist in that surrounding. ADR-134's principle is that a value is only ever dangerous with respect to the structure it lands in, and borrowing a rule from another structure breaks exactly that.

So `inAHeading` folds, escapes the backslash first, then escapes `<` and `&` — and stops there. It is the fourth surrounding ADR-134 anticipated, and the count in that record's title moves from three to four.

### 5. The CSV manifest is untouched

`quoted` implements RFC 4180 and answers to a parser, not to a reader. A backslash there is data, not an escape: writing `R\&D` into a manifest column would put a literal backslash into whatever loads it. ADR-134's reason for three rules is exactly this — a value is only dangerous with respect to the structure it lands in — and `<` and `&` are not dangerous to a CSV parser at all.

### 6. The entity-shaped run is closed as a consequence, not as a goal

§2 measured that a title containing `&copy;` is decoded to `©` by three of four renderers — the page saying something other than what the archive holds. Escaping `&` closes that case (`\&copy;` displays `&copy;`, §7) without the record needing a separate rule for it. It is recorded here as a consequence so that a later reader removing the `&` escape knows what else they are removing.

## Consequences

**A plain-text reader sees two more backslashes.** ADR-103 makes reading the `.md` directly a supported way to read the tree, so this is a real cost. It is not a new *kind* of cost: `\|`, `\[`, `\]` and `\\` are already written into the same values under ADR-134, and a reader of the raw file is already looking at that list. This adds to it rather than starting it.

**`onOneLine`'s callers divide.** It has been the heading's whole treatment; it is now folding only, with escaping applied around it. Anywhere `onOneLine` is called on a value that does not reach a renderer, nothing changes.

**There are now four surroundings, not three.** ADR-134's title says three, and its reasoning is what produced the fourth rather than what is contradicted by it. If a fifth position appears with a grammar of its own, the same rule applies again. If another character turns out to be hazardous in the surroundings that exist, it joins these three the way `<` and `&` just did, and this record is the precedent for doing so.

**What this does not decide**: which renderers an operator actually opens the tree with. §7 makes that question much less pressing — the escape is correct in all six configurations measured, so no choice between renderers has to be made to justify it — but VS Code, IntelliJ and Obsidian remain unmeasured as applications, and that is still the input a future question about this file would want.
