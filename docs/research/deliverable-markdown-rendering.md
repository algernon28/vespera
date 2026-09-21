# What renderers do with `<` and `&` in the deliverable

Research record for [issue #251](https://github.com/algernon28/vespera/issues/251). Facts only — no decisions.
Feeds the decision that ticket asks for, and [ADR-134](../adr/0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md),
which declined to settle this and said what it wanted first was a measurement rather than an argument.

## Scope and method

Every claim below is labelled `MEASURED` and was produced by rendering a fixture on the machine described
below on 2026-09-21. Nothing here is inferred from documentation. Where a result has a documented cause, the
primary source is quoted and linked; where I did not establish the cause, the claim says so.

The fixture reproduces the four positions `Deliverable` writes an untrusted value into, built from the format
strings in `src/main/java/io/algernon/vespera/synthesis/Deliverable.java` rather than from a paraphrase of
them:

| Position | Written by | Escaping applied today |
|---|---|---|
| A table cell in `index.md` | `Deliverable.java:276` | `inACell` — folds, escapes `\` then `\|` |
| The text of a link in that table | `Deliverable.java:270` | `asLinkText` — `inACell`, then `\[` and `\]` |
| An ATX `#` / `##` heading | `Deliverable.java:213`, `:327` | `onOneLine` — **folds only, escapes nothing** |
| A numbered membership entry | `Deliverable.java:439` | `escapeLinkText` — escapes `\`, `\[`, `\]` |

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

`MEASURED`. The membership entry is written as
`1. <a id="d1"></a>[archive/…](file:///…)` (`Deliverable.java:433`). Under `markdown-it` defaults
(`html:false`) the anchor the deliverable emits for its own citation targets is escaped into visible literal
text:

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
   which is a separate question from this ticket's and is not answered by it.
