# What renderers do with the deliverable

Research record for four issues, measured in four passes on one day — the ordinal a section carries counts
rounds of measurement across the whole record, so §1 to §3 are the first, §6 the second, §7 the third and §8
the fourth. §7's round is the one that re-measured it in §6's own environment and regenerated §6b's entry,
which is why it counts as a round though it moved no machine. Where §7 and §8a call a false attempt of their
own "the first pass", that is local to the section and not a step on this counter. §1 to §3 are
[#251](https://github.com/algernon28/vespera/issues/251) — `<` and `&` in a value taken out of the archive.
§4 and §6 are [#253](https://github.com/algernon28/vespera/issues/253) — the link and the anchor the tool
composes itself. §7 is [#251](https://github.com/algernon28/vespera/issues/251) again, re-measured once a
backslash escape was the operation in question rather than an HTML entity, and §8 is
[#259](https://github.com/algernon28/vespera/issues/259) — the same entity-shaped run in the position a value
is resolved rather than read. §8b carries one further measurement, of
[#261](https://github.com/algernon28/vespera/issues/261) — a backtick in a name, which `marked` alone refuses
to make a link of — recorded where the sweep found it and decided nowhere. Facts only — no decisions.
Feeds the decisions those tickets ask for, and [ADR-134](../adr/0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md),
which declined to settle the first of them and said what it wanted was a measurement rather than an argument.

## Scope and method

Every claim below is labelled `MEASURED` and was produced by rendering a fixture on the machine described
below on 2026-09-21. Nothing here is inferred from documentation. Where a result has a documented cause, the
primary source is quoted and linked; where I did not establish the cause, the claim says so.

The fixture reproduces the four positions `Deliverable` writes an untrusted value into, built from the format
strings in `src/main/java/io/algernon/vespera/synthesis/Deliverable.java` rather than from a paraphrase of
them:

| Position | Written by | Escaping applied when §1–§6 were measured |
|---|---|---|
| A table cell in `index.md` | `Deliverable.java:249`, `:279` | `inACell` — folds, escapes `\` then `\|` |
| The text of a link in that table | `Deliverable.java:273` | `asLinkText` — `inACell`, then `\[` and `\]` |
| An ATX `#` / `##` heading | `Deliverable.java:217`, `:336` | `onOneLine` — **folds only, escapes nothing** |
| A numbered membership entry | `Deliverable.java:453` | `escapeLinkText` — escapes `\`, `\[`, `\]` |

The line numbers are against `Deliverable.java` as it stands now; the escaping column is the state the
first two passes measured, and it is no longer the state of the tool. The three rules named there now
escape `<` and `&` as well, and the heading has a rule of its own, `inAHeading`. §7 measures the forms
those rules emit.

Measurement environment:

| | |
|---|---|
| OS | Windows 11 Pro, build 10.0.26200 |
| Node | v22.23.1 |
| `markdown-it` | 15.0.2 |
| `marked` | 18.0.13 |
| `commonmark` (reference implementation) | 0.31.2 |
| GitHub | live `POST /markdown`, `mode=gfm` and `mode=markdown`, via `gh api` |

The probe was throwaway and lived outside the repository, per `AGENTS.md`.

**What was not measured**, and so is not claimed anywhere below: VS Code's Markdown preview, IntelliJ's,
Obsidian, or any other application. `markdown-it` is the engine VS Code's preview is built on, but VS Code
configures it itself and adds its own link handling; measuring the bare library says what the library does,
not what that application does. Several results below would need re-measuring against the real application
before anything is concluded about it.

---

## 1. A bare `&` is safe in every renderer and every position

`MEASURED`. Source `Title R&D Retrofits` produced `<p>Title R&amp;D Retrofits</p>` in `markdown-it` (both
configurations), `marked`, `commonmark` and GitHub. `&amp;` in the HTML **displays as `&`**, which is the
original text.

**Escaping `&` in the source would therefore be wrong, not merely costly.** A title escaped to `R&amp;D`
before writing renders as the literal `R&amp;D` on the page, and reads as `R&amp;D` in plain text too. The
cost issue #251 anticipated — that escaping `&` damages a plain-text reader — is real, and the measurement
adds that it damages the rendered reader equally. No renderer measured needs the help.

## 2. The real `&` hazard is an entity-shaped substring, not a bare `&`

`MEASURED`. Source `Title R&amp;D and &copy; 2019`:

| Renderer | Rendered text |
|---|---|
| `markdown-it` (both configurations) | `Title R&D and © 2019` |
| `commonmark` | `Title R&D and © 2019` |
| GitHub (`gfm` and `markdown`) | `Title R&D and © 2019` |
| `marked` | `Title R&amp;D and &copy; 2019` (left alone) |

So a document whose own title contains the characters `&copy;` is displayed as `©` by three of the four, and
a title containing `&amp;` is displayed as `&`. The page says something other than what the archive holds.

This is narrow — it needs a title that happens to contain an entity-shaped run — and it is the **only** `&`
behaviour measured that loses information. It is also the case the ticket did not anticipate: the ticket
frames `&` as "the harder half" on the assumption that escaping it is the safe-but-costly option, and the
measurement inverts that. Escaping every `&` is wrong (§1); the thing that needs a decision is the entity
case alone.

## 3. `<` is a hazard, and the four renderers disagree in three different ways

`MEASURED`, across all four positions. The fixture carried `<b>Phase 2` (a known tag) and `<draft>` /
`<notes>` (unknown tags) in a heading, a cell, link text and a membership entry.

| Renderer | A known tag such as `<b>` | An unknown tag such as `<draft>` |
|---|---|---|
| `markdown-it` default (`html:false`) | escaped — `&lt;b&gt;`, **shown to the reader as written** | escaped, shown as written |
| `markdown-it` `html:true` | **live** — the page goes bold | passed through; a browser ignores it, so it is **invisible** |
| `marked` default | **live** | passed through — invisible |
| `commonmark` default | **live** | passed through — invisible |
| `commonmark` `safe:true` | replaced by `<!-- raw HTML omitted -->` | same |
| GitHub (`gfm` and `markdown`) | **live** — and GitHub *auto-closes* it | **deleted outright** |

Three distinct failure modes, and only the first row is correct behaviour:

- **The tag goes live.** `Retrofits & <b>Phase 2` in link text rendered on GitHub as
  `<a href="0001-retrofits.md">Retrofits &amp; <b>Phase 2</b></a>` — GitHub supplied a closing `</b>` the
  source never had. The bold is contained here; an unclosed tag of another kind need not be.
- **The tag is silently dropped.** `## Roofing & <draft> surveys` rendered on GitHub as
  `<h2>Roofing &amp;  surveys</h2>`. The word is gone, with nothing to indicate anything was removed, and the
  slug GitHub derives for the anchor is `roofing---surveys`.
- **The tag is invisible but present.** `marked` and `commonmark` emit `<draft>` into the HTML; the browser
  parses it as an unknown element and renders nothing for it.

**GitHub sanitising its output does not make GitHub safe for this.** Issue #251 says the case is milder
because "GitHub's renderer sanitises what it serves"; the measurement shows the sanitiser is what performs the
deletion in the second bullet. It protects the *page*; it does not protect the *text*.

**The heading is the worst-placed of the four positions**, because `onOneLine` escapes nothing at all, so a
heading is the one position where the value reaches the renderer entirely unmodified.

---

## 4. Two findings outside this ticket's question

Both were produced by the same fixture and are recorded here because they bear on
[ADR-103](../adr/0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)'s
claim that every link in the tree resolves with no database and no network. Neither is about `<` or `&`, and
neither is decided here.

### 4a. A `file:` link does not render in `markdown-it` or on GitHub

`MEASURED`, with a plain link carrying no special characters at all —
`[archive/plain.pdf](file:///D:/corpus/archive/plain.pdf)`:

| Renderer | Result |
|---|---|
| `markdown-it` (both configurations) | **not a link** — emitted as the literal text `[archive/plain.pdf](file:///D:/corpus/archive/plain.pdf)` |
| GitHub (`gfm` and `markdown`) | **not a link** — `<p>archive/plain.pdf</p>`, the href stripped, the text kept |
| `marked` | `<a href="file:///D:/corpus/archive/plain.pdf">` — resolves |
| `commonmark` | `<a href="file:///D:/corpus/archive/plain.pdf">` — resolves |

`markdown-it` documents a scheme blocklist for exactly this, in
[`lib/index.mjs`](https://github.com/markdown-it/markdown-it/blob/master/lib/index.mjs) — `validateLink`
rejects `javascript:`, `vbscript:`, `file:` and `data:` unless overridden. That is the documented cause for
that row. I did **not** establish GitHub's cause beyond observing the stripping.

The membership entry is the only place the deliverable writes a `file:` URL, and it is the link that carries a
reader from a claim back into the archive. The relative `.md` links between index and cluster files were also
measured and resolve in all four renderers, so this is specific to the `file:` scheme.

### 4b. `markdown-it` with default options breaks the membership entry outright

`MEASURED`, over the membership entry as the tool wrote it then: an anchor, and after it a link whose
destination was a `file:` URL. Two of the three have changed since. ADR-135 replaced the `file:` destination
with one relative to the page, and ADR-136 put a backslash in front of the `<` and the `&` in the link text.
So over the name this section rendered — `archive/R&D <notes>.pdf` — the shipped writer emits
(`Deliverable.appendMembership`, the entry composed at `Deliverable.java:453`):

```
1. <a id="document-1"></a>[archive/R\&D \<notes>.pdf](../../../corpus/archive/R&D%20%3Cnotes%3E.pdf)
```

— **generated by running that writer**, the way §6b's entry was, rather than composed here. The anchor is the
part that did not change, and the anchor is what this section measured. One thing the pass that wrote it
invented is visible below: the fixture carries `d1`, where the tool composes the anchor from `ANCHOR_PREFIX`
(`Deliverable.java:124`) and emits `document-1`. The abbreviation changes nothing about what a renderer does
to an `<a>` tag, which is the row's whole claim. Under `markdown-it` defaults (`html:false`) the anchor
the deliverable emits for its own citation targets is escaped into visible literal text:

```
<p>&lt;a id=&quot;d1&quot;&gt;&lt;/a&gt;[archive/R&amp;D &lt;notes&gt;.pdf](file:///…)</p>
```

The reader sees the HTML source of the anchor printed on the page, and the link beside it is not a link
(§4a). Under `html:true` the anchor works and the link still does not.

This means **the deliverable already emits raw HTML derived from its own writing** — the `<a id>` anchors —
which is the specific reason ADR-134 declined to rest the folding decision on "it emits no raw HTML derived
from untrusted text". That record's caution is confirmed by measurement: the claim would have been false.

---

## 5. Summary of what a decision now has to answer

Facts, restated compactly, for whoever writes the record:

1. Escaping a bare `&` is **wrong** in all four renderers — §1. It is not a trade-off.
2. An entity-shaped substring is decoded by three of four — §2. This is the only `&` case that loses text.
3. `<` loses or alters text in every renderer except `markdown-it`'s default — §3.
4. The heading position applies **no escaping whatsoever** today — §3, and the method table above.
5. GitHub's sanitiser deletes unknown tags rather than neutralising them — §3.
6. `file:` links, the deliverable's route back into the archive, do not resolve in two of the four — §4a,
   which is a separate question from this ticket's and is not answered by it. §6 goes on to measure what
   else a membership entry could be written as, over seven configurations rather than four: of those
   seven a `file:` destination is a link in two and is not a link in five.

---

## 6. A relative destination, an unlinked entry, and the anchor across every renderer

`MEASURED` on 2026-09-21, second pass, same machine and the same four renderers and versions as the method
table above. This section exists for [#253](https://github.com/algernon28/vespera/issues/253), whose question
§4 raised and did not answer: what a membership entry could be written as instead. The probe was throwaway and
lived outside the repository. Nothing here is inferred.

Seven configurations are reported rather than four, because two of the libraries have a second documented mode
that changes the answer: `markdown-it` `html:true`, and `commonmark` `safe:true`. §4's counts were over four.

### 6a. A relative destination resolves in every renderer and every configuration

Source, the membership entry as `Deliverable` composes it but with the destination relative to the cluster
file's own directory: `1. <a id="document-1"></a>[archive/plain.pdf](../../corpus/archive/plain.pdf)`. **The
number of `..` segments in that fixture is illustrative**, here and in §6d, which reuses it: both were
composed by hand for this section, and over the tree §6b generated its entry from, the shipped writer emits
three of them — `../../../corpus/archive/plain.pdf`. Nothing either section claims turns on the count, no
configuration measured resolving a destination by counting its segments; where §6b says nothing in its line
is adjustable, that is the line the depth comes from.

| Renderer and configuration | The destination |
|---|---|
| `markdown-it` `html:false` (default) | `<a href="../../corpus/archive/plain.pdf">` |
| `markdown-it` `html:true` | `<a href="../../corpus/archive/plain.pdf">` |
| `marked` | `<a href="../../corpus/archive/plain.pdf">` |
| `commonmark` default | `<a href="../../corpus/archive/plain.pdf">` |
| `commonmark` `safe:true` | `<a href="../../corpus/archive/plain.pdf">` |
| GitHub `mode=gfm` | `<a href="../../corpus/archive/plain.pdf">` |
| GitHub `mode=markdown` | `<a href="../../corpus/archive/plain.pdf">` |

**Seven of seven.** The same fixture with a `file:` destination is a link in two of the seven (§6c) — the
count §6c's own table gives, and `commonmark safe:true`'s hrefless `<a>` is not one of them. No
configuration measured refuses a relative destination, and none rewrites it: the `..` segments and the `/`
separators arrive at the reader exactly as written.

### 6b. The encoding is load-bearing, and it is the encoding the tool already applies

`MEASURED`. `[archive/R&D report.pdf](../../corpus/archive/R&D report.pdf)`, with the space left raw, is **not
a link in any of the seven**: every one of them prints the whole construct as literal text, the brackets and
parentheses included. A destination with an unencoded space does not truncate, it fails.

With the escaping the tool already applies where it composes a destination — `java.net.URI` quoting plus its
own `%28`/`%29`, in the method ADR-135 left behind when it removed `Deliverable.fileUrl` — the entry
`1. <a id="document-1"></a>[archive/R\&D report \[draft\] (1).pdf](../../../corpus/archive/R&D%20report%20%5Bdraft%5D%20%281%29.pdf)`
is a link in all seven, carrying the whole name in its link text.

That entry was **generated by running the shipped writer** over that name, with the archive root at `corpus`
beneath the working directory, rather than composed by hand here — which is the only way to be sure of it, and
the way it was not produced for either of the two passes before the one that generated it. The number of
`..` segments is whatever relativizing the page's own directory against the archive root gives, and nothing
in the line is adjustable. Two things the earlier passes invented are visible in it: the destination's ampersand is **raw**,
because `java.net.URI` does not quote one, where this section carried `%26`; and the parentheses are escaped
in the destination and left alone in the link text. That raw ampersand is the form emitted before ADR-137,
which settles that a destination's ampersand is written `%26`; where the raw form leads, and what the same
name emits under that decision, are §8's claims rather than this section's.

Three further encoding facts, each measured on a relative destination:

- **A bare `&` is left unencoded, and that is safe only where it begins nothing.** `../../corpus/R&D.pdf`
  renders as `href="../../corpus/R&amp;D.pdf"` in `markdown-it`, `marked` and `commonmark`, which is `&` to
  the browser, and that is the whole of what this row measured. §1's finding — that the hazard for `&` is
  never the character itself — is about `&` in **text**, and it does not carry over to a destination: a
  renderer decodes an entity reference there, so `reports/&copy; notes.pdf` routes to `reports/© notes.pdf`
  in seven configurations of seven. A destination's `&` does need encoding wherever the run after it is
  entity-shaped, and `java.net.URI` encodes it in no case at all. **ADR-137 settles that it is encoded in
  every case**, as `%26`, which §8 measures across the same seven; the sentence this bullet replaces is the
  one that licensed leaving the destination alone.
- **A literal `%` must be `%25`**, or the two characters after it are read as a hex escape.
  `../../corpus/100%25%20done.pdf` renders with the `%25` intact.
- **`#` must be percent-encoded**, or the destination ends there and the remainder becomes a fragment.
  `java.net.URI`'s own quoting does it: `new URI(null, null, "../../corpus/a#b.pdf", null).toASCIIString()`
  returns `../../corpus/a%23b.pdf` (JDK 26, measured in `jshell`).

An angle-bracket destination — `[text](<../../corpus/archive/R&D report.pdf>)` — is a second form that all
seven accept with a raw space inside it, recorded because it was measured and not because it is needed.

### 6c. An unlinked entry has no failure mode, and `file:` has one more than §4 counted

`MEASURED`. `1. <a id="document-1"></a>archive/plain.pdf`, with no link at all, renders the path exactly as
written in all seven configurations. There is nothing a renderer can refuse.

And one row §4 did not have, because `commonmark safe:true` was not run against a `file:` destination there:

| Renderer and configuration | `[archive/plain.pdf](file:///D:/corpus/archive/plain.pdf)` |
|---|---|
| `markdown-it` `html:false` and `html:true` | not a link — the literal markdown printed at the reader |
| `commonmark` `safe:true` | `<a>archive/plain.pdf</a>` — **an anchor with no `href` at all** |
| GitHub, both modes | not a link — `href` stripped, text kept |
| `marked`, `commonmark` default | resolves |

So a `file:` destination reaches the filesystem in **two of seven** configurations, and in two of the failing
ones the reader is shown Markdown source rather than a page.

### 6d. The `<a id>` anchor, and the citation that lands on it

`MEASURED`, over the pair a citation actually uses: the prose `Claim [1](#document-1).` above, and the
membership entry `1. <a id="document-1"></a>[archive/plain.pdf](../../corpus/archive/plain.pdf)` below.
The fixture is §6a's, so its `..` count is illustrative for the reason given there; nothing measured in
this section turns on the depth, only on the anchor and what lands on it.

| Renderer and configuration | The anchor in the entry | The citation above it |
|---|---|---|
| `markdown-it` `html:false` (default) | escaped to `&lt;a id=&quot;document-1&quot;&gt;&lt;/a&gt;` — **printed at the reader** | `<a href="#document-1">` — a link with nothing on the page to land on |
| `markdown-it` `html:true` | `<a id="document-1"></a>` | lands |
| `marked` | `<a id="document-1"></a>` | lands |
| `commonmark` default | `<a id="document-1"></a>` | lands |
| `commonmark` `safe:true` | replaced by `<!-- raw HTML omitted -->` | a link with nothing to land on |
| GitHub `mode=gfm` and `mode=markdown` | `<a id="user-content-document-1"></a>` — **the `id` is rewritten** | `<a href="#document-1">`, left alone |

Two findings, and they are different in kind:

- **There is no configuration-independent anchor.** The mechanism works where raw inline HTML is allowed and
  fails where it is not, and no Markdown construct exists to replace it. `markdown-it`'s default prints the
  tag; `commonmark safe:true` omits it.
- **On GitHub the anchor survives and the pair stops matching.** The API's own output carries
  `id="user-content-document-1"` against an `href="#document-1"`. Whether github.com's page scripts reconcile
  the two in a browser was **not established** — this measurement is over the `POST /markdown` API and no
  browser was involved. GitHub's cause for the rewrite was not established either, beyond observing it.

### 6e. What the JDK does at the boundary a relative destination needs

`MEASURED` in `jshell` on JDK 26, because these are the cases a writer composing a relative destination has to
tell apart, and they throw rather than return anything:

| Expression | Result |
|---|---|
| `Path.of("D:/work/deliverable/abc/01-seed").relativize(Path.of("D:/corpus"))` | `..\..\..\..\corpus` |
| the same against `Path.of("C:/corpus")` | `IllegalArgumentException: 'other' has different root` |
| the same against `Path.of("//server/share/corpus")` | `IllegalArgumentException: 'other' is different type of Path` |
| the same against a relative `Path.of("corpus")` | `IllegalArgumentException: 'other' is different type of Path` |
| `Path.of("D:/corpus/a\"b.pdf")` | `InvalidPathException: Illegal char <"> at index 11` |

The last row is the one §4 never had to state: a **directory** out of the recorded root can go through `Path`,
and a **document's own name** cannot, because NTFS allows characters the JDK's path parser refuses. It is the
same fact `ClusterFileTest` already pins for `file:` composition.

---

## 7. A backslash escape is not an HTML entity, and it fixes both characters at no cost

`MEASURED` on 2026-09-21, third pass, same machine and the same environment as §6 — **seven configurations**, the seven §6a and §6c count, GitHub's two modes among them here as there, and the result is the same in every one. Re-measured on the same machine and the same seven configurations after the first pass of this section was found to have rendered fixtures the tool does not emit; the rows below are the re-measurement, and every conclusion the first pass drew survives it.

§1 and §2 above answer the question "should the value be written as `&amp;`", and the answer there stands: it should not. **That is not the only way to neutralise a character in Markdown, and this section measures the other one.** CommonMark allows any ASCII punctuation character to be backslash-escaped, and `<` and `&` are both ASCII punctuation. `\<` and `\&` are a different operation from `&lt;` and `&amp;`, and they behave differently.

Fixtures were written to disk and their bytes confirmed with `cat -A` before rendering. This is not incidental: an earlier attempt embedded the same fixtures in a JavaScript string literal, where `'\<'` is simply `<` — the backslash never reached the renderer, and the run produced a confident, entirely false result claiming the escape does not work. Any re-measurement should read from a file for the same reason.

**Every fixture is the form `Deliverable` writes.** The first six rows are read off the escaping rules; the membership entry in the last row is **generated by running the shipped writer** over the name it states, because reading that one off the rules is what produced an invented destination twice (§6b). Only `<` is escaped: no rule touches `>`, so `\<draft>` is what the tool emits, and `\<draft\>` — the form the first pass of this section rendered — is a string it never writes. The entry is carried at its full emitted width, anchor and destination included, and that destination leaves `&` raw because raw is what the tool emits.

| Source written | `markdown-it` (both) | `marked` | `commonmark` (both) | GitHub (both modes) |
|---|---|---|---|---|
| `Roofing \<draft> surveys` in a paragraph | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` |
| `## Roofing \<draft> surveys` | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` |
| `\<draft>` in a table cell | `&lt;draft&gt;` in the cell | same | `&lt;draft&gt;`, in the paragraph it makes of the row | `&lt;draft&gt;` in the cell |
| `[Retrofits \<b>Phase 2](0001.md)` | `&lt;b&gt;` inside the link | same | same | same |
| `Title R\&D Retrofits` | `R&amp;D` → displays `R&D` | same | same | same |
| `Title \&copy; 2019` | `&amp;copy;` → displays `&copy;` | same | same | same |
| `1. <a id="document-1"></a>[reports/R\&D \<notes>.pdf](../../../corpus/reports/R&D%20%3Cnotes%3E.pdf)` | link text `reports/R&amp;D &lt;notes&gt;.pdf` | same | same | same |

`commonmark` implements no tables, so the third row's source is a paragraph there rather than a cell: what that one column measures is the escape and not the position. The last row's anchor behaves exactly as §6d records — printed at the reader under `markdown-it`'s default, omitted under `commonmark safe:true`, re-`id`-ed by GitHub — and the escapes in its link text are unaffected by which of those happens. **What the last row measures is its link text only.** Its destination carries a raw `&`, which is [#259](https://github.com/algernon28/vespera/issues/259)'s subject rather than this section's: no row here is a claim about where a destination leads.

Every `&lt;` above **displays to the reader as a literal `<`**. Nothing goes live, nothing is deleted, nothing is invisible.

### What this changes

- **`<` is fixable in every renderer measured, in each of the four positions §3 measured it in.** The heading, the cell and the link text are rows of the table above; the membership entry is its last row, added when this section was re-measured, because the first pass drew the conclusion for all four positions while rendering no entry at all. §3's three failure modes — the live tag, the silent deletion, the invisible tag — are all consequences of the character reaching the renderer unescaped, and none of them survives `\<`.
- **`\&` costs nothing, where `&amp;` costs everything.** `R\&D` displays as `R&D`: the plain-text damage §1 records for `&amp;` does not occur, because the renderer consumes the backslash rather than printing it. §1's conclusion — that writing `&amp;` would be wrong — is unchanged and remains true of that operation.
- **The entity hole of §2 closes as a side effect, in text.** `\&copy;` displays as `&copy;`, the text the archive actually holds, in all seven. It does not close in a link destination, where no backslash escape applies at all and the same run is decoded into the character — [#259](https://github.com/algernon28/vespera/issues/259).

### The one cost, stated plainly

A reader opening the `.md` in a plain text editor sees the backslashes. [ADR-103](../adr/0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) makes that a supported way to read the tree, so the cost is real rather than theoretical.

It is also **not a new cost**: the deliverable already writes `\|`, `\[`, `\]` and `\\` into the same values under ADR-134's three rules, and a plain-text reader already sees those. What this adds is two more characters to a list a reader of the raw file is already looking at, not a new kind of damage.

### What was not measured

Whether a **doubled** backslash before one of these characters composes correctly — that is, a title genuinely containing `\<`. ADR-134's `inACell` escapes `\` to `\\` before inserting its own escapes, so the ordering that governs it is already recorded and already tested; this section did not re-measure it.

## 8. A destination's ampersand, and the subtraction that says it is the only one

`MEASURED` on 2026-09-21, fourth pass, same machine and the same four renderers and versions as the method
table above, in the seven configurations §6 counts. This section exists for
[#259](https://github.com/algernon28/vespera/issues/259), whose question §6b raised and left to a decision:
a destination's `&` is left raw by `java.net.URI`, and where the run after it is entity-shaped, a renderer
decodes it before anything resolves the path. What this section measures is the **rendered `href`** rather
than the Markdown source — the distinction is the whole of the finding, because the source is right and the
route is wrong, and a claim that reads the destination in its Java form cannot see the difference.

### 8a. How every fixture here was made

**Every fixture in this section was generated by running the shipped writer** — `Deliverable.writeTo`, from
`target/classes`, over one `ListedSurvivor` per name, with the archive root a sibling of the working
directory so a relative route exists — and the membership entry was then read back out of the page the
writer had written. Nothing below was composed by hand. The fixture file's bytes were confirmed with
`cat -A` before any renderer saw them, and every renderer read them from that file rather than from a string
literal: §6b records an invented `%26` surviving two passes, and §7 records a backslash eaten by a JavaScript
string literal, and those are the two ways a measurement of this exact question has already gone wrong.

The one form the shipped writer could not produce at the time of measurement is the one §8c measures, because
it is the form this section's decision proposes; under ADR-137 the writer emits it. It was derived from the
emitted file rather than typed: each emitted line had `&` replaced by `%26` **inside its destination only**,
over the bytes read from the file, and the result was written back to a file and confirmed with `cat -A` the
same way. The derivation was afterwards checked against the writer carrying that replacement: the regenerated
fixtures are byte-identical to the derived ones, so §8c reads as emitted output rather than as a derivation.

The GitHub rows are one `POST /markdown` call per fixture per mode rather than one call carrying all of them,
so that a fixture whose link fails to form cannot shift the reading of the fixture after it.

### 8b. What the emitted destination does in seven configurations

The name `reports/&copy; notes.pdf`, which NTFS allows and a walk can really record, is emitted as

```
1. <a id="document-1"></a>[reports/\&copy; notes.pdf](../../../../archive-that-is-not-there/reports/&copy;%20notes.pdf)
```

and the `href` that reaches the reader is:

| Renderer and configuration | `href` |
|---|---|
| `markdown-it` `html:false` and `html:true` | `.../reports/%C2%A9%20notes.pdf` |
| `commonmark` default and `safe:true` | `.../reports/%C2%A9%20notes.pdf` |
| GitHub `mode=gfm` and `mode=markdown` | `.../reports/%C2%A9%20notes.pdf` |
| `marked` | `.../reports/&copy;%20notes.pdf`, which an HTML attribute parser resolves to the same thing |

`%C2%A9` is UTF-8 for `©`. **Seven configurations of seven route the reader to `reports/© notes.pdf`**, a
document the archive does not hold. Six decode the reference themselves; `marked` passes it into the
attribute, where the browser's own parser decodes it. Nothing reports an error in any of them.

Two neighbouring names, emitted and rendered the same way, say how narrow this is:

- `reports/R&D notes.pdf` is emitted with a raw `&` and arrives as `R&amp;D%20notes.pdf` in all seven, which
  is `R&D` to the browser — the same file the source named. **A bare `&` that begins no entity name is
  harmless in a destination**, which is what §6b's bullet measured and all it measured.
- `reports/&amp; and &#169; notes.pdf` is emitted as `&amp;%20and%20&%23169;%20notes.pdf`, and the two halves
  behave differently: `&amp;` is decoded to `&` in all seven, and `&#169;` is not decoded anywhere, because
  `java.net.URI` has already quoted the `#` to `%23` and `&%23169;` is no longer a character reference at
  all. **The numeric form is already defused, by an encoding applied for an unrelated reason; the named form
  is the live one.**

**The subtraction.** NTFS permits every printable ASCII character except `< > : " / \ | ? *`. Each character
it permits was driven through the writer as `reports/a<c>b.pdf` and rendered in all seven configurations.
`java.net.URI`'s path quoting leaves every letter and digit unencoded, and of the punctuation it permits it
leaves fifteen — the thirteen `!`, `$`, `&`, `'`, `+`, `,`, `-`, `.`, `;`, `=`, `@`, `_` and `~`, plus `(`
and `)`, which `relativeDestination`'s own replacements then encode to `%28` and `%29`, so exactly thirteen
punctuation characters reach the page unencoded. The rest are percent-encoded before the page is written, the
space to `%20`, `#` to `%23`, `%` to `%25`, `[` and `]` to `%5B` and `%5D`, `^` to `%5E`, the backtick to
`%60`, and `{` and `}` to `%7B` and `%7D`. Of the thirteen, **twelve arrive in the `href` naming the same
file the source named, and `&` is the only one that does not.** `marked` writes `'` into the attribute as
`&#39;`, which is `'` to an attribute parser and the same file; no other difference of any kind was observed
across the thirteen.

The characters NTFS forbids need no separate row: `<` and `>` are quoted to `%3C` and `%3E` by the URI
grammar, and `"` to `%22`, so the emitted destination could not carry them even if a name did. `:` is the one
ADR-135 §5 records as unreachable for a second reason, and it stays unreachable.

One further fact, measured here and belonging to neither this question nor
[#258](https://github.com/algernon28/vespera/issues/258): `marked` alone fails to form the link at all for
`reports/a` + backtick + `b.pdf`, printing the entry as source. That is a property of the **link text**,
where the backtick is not escaped, and not of the destination, which is `%60` and is what the other six
configurations resolve. It is recorded because the sweep found it and a later reader re-running this sweep
will see it. It is now [#261](https://github.com/algernon28/vespera/issues/261), and nothing in the decision
record decides it.

### 8c. The same names with `%26` written, in the same seven

Each emitted line above with `&` replaced by `%26` inside its destination:

| Source destination | `href`, all seven configurations |
|---|---|
| `.../reports/%26copy;%20notes.pdf` | `.../reports/%26copy;%20notes.pdf` |
| `.../reports/R%26D%20notes.pdf` | `.../reports/R%26D%20notes.pdf` |
| `.../reports/%26amp;%20and%20%26%23169;%20notes.pdf` | `.../reports/%26amp;%20and%20%26%23169;%20notes.pdf` |
| `.../reports/a%26b.pdf` | `.../reports/a%26b.pdf` |

**Seven of seven, byte for byte.** No configuration decodes it, none rewrites it, and the whole sweep of §8b
was re-run in this form with the same result: every destination arrives naming the file the source named.
The `marked` backtick row is unchanged by it, as it would be, being about the text.

### 8d. Two JDK facts the decision rests on

`MEASURED` in `jshell` on JDK 26, the same machine:

- `new java.net.URI(null, null, "../a&b/c.pdf", null).toASCIIString()` returns `../a&b/c.pdf` — the ampersand
  is left alone, which is where the emitted form in §8b comes from.
- `java.net.URI.create("../corpus/reports/%26copy;%20notes.pdf").getPath()` returns
  `../corpus/reports/&copy; notes.pdf` — **`%26` percent-decodes back to the name the archive holds**, so the
  encoded destination resolves to the same file the raw one was meant to.
- `java.net.URLEncoder.encode("reports/&copy; notes.pdf", UTF_8)` returns `reports%2F%26copy%3B+notes.pdf`.
  The JDK's off-the-shelf stricter encoder implements `application/x-www-form-urlencoded`: the separator
  becomes `%2F` and the space becomes `+`, and a destination in that form does not open the file.

### What was not measured here

Which renderers an operator actually opens the tree with — the same gap §6 and §7 record, unchanged. And
whether any renderer decodes a character reference in a destination it has already refused to linkify: every
configuration that refuses a destination in §6c refuses it for its scheme, and nothing here carries one.
