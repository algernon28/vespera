# What renderers do with the deliverable

Research record for two issues, measured in two passes on one day. §1 to §3 are
[#251](https://github.com/algernon28/vespera/issues/251) — `<` and `&` in a value taken out of the archive.
§4 and §6 are [#253](https://github.com/algernon28/vespera/issues/253) — the link and the anchor the tool
composes itself. Facts only — no decisions.
Feeds the decisions those tickets ask for, and [ADR-134](../adr/0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md),
which declined to settle the first of them and said what it wanted was a measurement rather than an argument.

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
file's own directory: `1. <a id="document-1"></a>[archive/plain.pdf](../../corpus/archive/plain.pdf)`.

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

With the escaping `Deliverable.fileUrl` already applies — `java.net.URI` quoting plus its own `%28`/`%29` —
`[archive/R&D report \[draft\].pdf](../../corpus/archive/R%26D%20report%20%5Bdraft%5D%20%281%29.pdf)` is a link
in all seven, carrying the whole name.

Three further encoding facts, each measured on a relative destination:

- **A bare `&` needs no encoding.** `../../corpus/R&D.pdf` renders as `href="../../corpus/R&amp;D.pdf"` in
  `markdown-it`, `marked` and `commonmark`, which is `&` to the browser. This matches §1: the hazard for `&`
  is never the character itself.
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

`MEASURED` on 2026-09-21, same environment as §6, extended to GitHub — **six configurations**, and the result is the same in every one.

§1 and §2 above answer the question "should the value be written as `&amp;`", and the answer there stands: it should not. **That is not the only way to neutralise a character in Markdown, and this section measures the other one.** CommonMark allows any ASCII punctuation character to be backslash-escaped, and `<` and `&` are both ASCII punctuation. `\<` and `\&` are a different operation from `&lt;` and `&amp;`, and they behave differently.

Fixtures were written to disk and their bytes confirmed with `cat -A` before rendering. This is not incidental: an earlier attempt embedded the same fixtures in a JavaScript string literal, where `'\<'` is simply `<` — the backslash never reached the renderer, and the run produced a confident, entirely false result claiming the escape does not work. Any re-measurement should read from a file for the same reason.

| Source written | `markdown-it` (both) | `marked` | `commonmark` (both) | GitHub |
|---|---|---|---|---|
| `Roofing \<draft\> surveys` in a paragraph | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` |
| `## Roofing \<draft\> surveys` | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` |
| `\<draft\>` in a table cell | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` | `&lt;draft&gt;` |
| `[Retrofits \<b\>Phase 2](0001.md)` | `&lt;b&gt;` inside the link | same | same | same |
| `Title R\&D Retrofits` | `R&amp;D` → displays `R&D` | same | same | same |
| `Title \&copy; 2019` | `&amp;copy;` → displays `&copy;` | same | same | same |

Every `&lt;` above **displays to the reader as a literal `<`**. Nothing goes live, nothing is deleted, nothing is invisible.

### What this changes

- **`<` is fixable in all four positions, in every renderer measured.** §3's three failure modes — the live tag, the silent deletion, the invisible tag — are all consequences of the character reaching the renderer unescaped, and none of them survives `\<`.
- **`\&` costs nothing, where `&amp;` costs everything.** `R\&D` displays as `R&D`: the plain-text damage §1 records for `&amp;` does not occur, because the renderer consumes the backslash rather than printing it. §1's conclusion — that writing `&amp;` would be wrong — is unchanged and remains true of that operation.
- **The entity hole of §2 closes as a side effect.** `\&copy;` displays as `&copy;`, the text the archive actually holds, in all six.

### The one cost, stated plainly

A reader opening the `.md` in a plain text editor sees the backslashes. [ADR-103](../adr/0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) makes that a supported way to read the tree, so the cost is real rather than theoretical.

It is also **not a new cost**: the deliverable already writes `\|`, `\[`, `\]` and `\\` into the same values under ADR-134's three rules, and a plain-text reader already sees those. What this adds is two more characters to a list a reader of the raw file is already looking at, not a new kind of damage.

### What was not measured

Whether a **doubled** backslash before one of these characters composes correctly — that is, a title genuinely containing `\<`. ADR-134's `inACell` escapes `\` to `\\` before inserting its own escapes, so the ordering that governs it is already recorded and already tested; this section did not re-measure it.
