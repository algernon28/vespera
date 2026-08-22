#!/usr/bin/env node
// Renders the Markdown documents in docs/ to self-contained HTML pages.
//
//   node docs/render-docs.mjs           rewrite the HTML
//   node docs/render-docs.mjs --check   exit 1 if any page is stale
//
// Why this exists: the Markdown is the source of truth, but GitHub Pages runs
// Jekyll, which only converts Markdown carrying YAML front matter. These files
// have none, so Pages would serve them as raw text; the HTML is what renders.
//
// This is not a general Markdown implementation. It supports exactly the
// constructs these documents use, and THROWS on anything else rather than
// silently dropping it — a renderer that quietly loses a section and leaves a
// plausible-looking page is the one failure that must not happen here.
//
// Pages are self-contained except for Mermaid, loaded from a CDN to draw the
// diagrams. If that fetch fails, the diagram source stays visible as text.

import { readFileSync, writeFileSync } from "node:fs";

const REPO = "https://github.com/algernon28/vespera/blob/main/docs/";
const MERMAID = "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";
const LEDGER = "docs/decision-ledger.md";

const PAGES = [
  {
    src: "docs/architecture.md",
    out: "docs/architecture.html",
    nav: "Architecture",
    eyebrow: "Architecture &amp; tech stack",
  },
  {
    src: LEDGER,
    out: "docs/decision-ledger.html",
    nav: "Decision ledger",
    eyebrow: "49 decisions · closed to new entries",
  },
];

// Relative .md links that point at a page rendered here become .html links; every
// other relative link goes to github.com, where Markdown renders.
const rendered = new Map(PAGES.map((p) => [p.src.replace(/^docs\//, ""), p.out.replace(/^docs\//, "")]));

/* ---------- ADR index ---------- */

// Every ADR-NNN mentioned in any document becomes a link to its record. The
// id-to-filename map is derived from the ledger table rather than hardcoded.
const adrFile = new Map();

function indexAdrs() {
  for (const line of readFileSync(LEDGER, "utf8").split(/\r?\n/)) {
    const m = /^\|\s*(ADR-(\d{3}))\s*\|[^|]*\|\s*([^|]+?)\s*\|/.exec(line);
    if (!m) continue;
    adrFile.set(m[1], `adr/${m[2].padStart(4, "0")}-${slug(m[3].replace(/\*\([^)]*\)\*/g, ""))}.md`);
  }
}

/* ---------- inline ---------- */

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

// Code spans are lifted out before the other inline rules run, and put back last.
// The marker has to be a character that cannot occur in the source, or it collides
// with ordinary prose: a space-delimited number would match "all 49 decisions".
// Built from its code point so this file contains no NUL byte and git sees text.
const SENTINEL = String.fromCharCode(0);
const PLACEHOLDER = new RegExp(SENTINEL + "([0-9]+)" + SENTINEL, "g");

function slug(s) {
  return s
    .replace(/`/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function inline(text, { badges = true } = {}) {
  // Code spans first, swapped for NUL-delimited placeholders, so no other rule can
  // reach inside them. The sentinel is written as an escape rather than a literal
  // NUL byte, so this file stays text as far as git is concerned.
  const code = [];
  let s = text.replace(/`([^`]+)`/g, (_, c) => {
    code.push(`<code>${esc(c)}</code>`);
    return SENTINEL + (code.length - 1) + SENTINEL;
  });

  s = esc(s);

  // ADR references become badge links. Done before link syntax is expanded, and
  // suppressed for contents entries, so a badge never lands inside another anchor.
  if (badges) {
    s = s.replace(/\bADR-(\d{3})\b/g, (whole, num) => {
      const href = adrFile.get(`ADR-${num}`);
      return href ? `<a class="adr" href="${REPO}${href}">${whole}</a>` : `<span class="adr">${whole}</span>`;
    });
  }

  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, href) => {
    let target = href;
    if (!/^(https?:|#|mailto:)/.test(href)) {
      const bare = href.replace(/^\.\//, "").replace(/^\.\.\//, "");
      target = rendered.has(bare) ? "./" + rendered.get(bare) : REPO + bare;
    }
    return `<a href="${target}">${label}</a>`;
  });

  s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  s = s.replace(/\*([^*]+)\*/g, "<em>$1</em>");
  // Underscore emphasis only at word boundaries, so identifiers like schema_version
  // outside a code span are left alone.
  s = s.replace(/(^|[\s(])_([^_]+)_(?=[\s.,;:)]|$)/g, "$1<em>$2</em>");

  return s.replace(PLACEHOLDER, (_, i) => code[Number(i)]);
}

const cells = (line) =>
  line
    .replace(/^\|/, "")
    .replace(/\|\s*$/, "")
    .split("|")
    .map((c) => c.trim());

/* ---------- blocks ---------- */

function renderBlocks(src, lines) {
  const out = [];
  const toc = [];
  let i = 0;
  let diagrams = 0;

  const unsupported = (n, line, what) => {
    throw new Error(`${src}:${n}: unsupported Markdown (${what}): ${line.trim()}`);
  };

  const reject = [
    [/^\s*!\[/, "image"],
    [/^[0-9]+\.\s/, "ordered list"],
    [/^\s+[-*]\s/, "nested list"],
    [/^\s*\[\^/, "footnote"],
    [/^</, "raw HTML block"],
    [/^={3,}\s*$/, "setext heading"],
  ];

  while (i < lines.length) {
    const line = lines[i];
    const n = i + 1;

    for (const [re, what] of reject) if (re.test(line)) unsupported(n, line, what);

    if (line.trim() === "") {
      i++;
      continue;
    }

    // Fenced block: mermaid only. Any other language is a construct this renderer
    // has never been asked to handle, so it stops rather than guessing.
    if (line.startsWith("```")) {
      const lang = line.slice(3).trim();
      if (lang !== "mermaid") unsupported(n, line, `fenced code block (${lang || "no language"})`);
      i++;
      const body = [];
      while (i < lines.length && !lines[i].startsWith("```")) body.push(lines[i++]);
      if (i >= lines.length) unsupported(n, line, "unterminated fence");
      i++;
      diagrams++;
      out.push(`<figure class="diagram"><pre class="mermaid">${esc(body.join("\n"))}</pre></figure>`);
      continue;
    }

    const h = /^(#{1,6})\s+(.*)$/.exec(line);
    if (h) {
      const level = h[1].length;
      const id = slug(h[2]);
      out.push(
        `<h${level} id="${id}">${inline(h[2])}<a class="anchor" href="#${id}" aria-label="Permalink">#</a></h${level}>`,
      );
      if (level === 2 || level === 3) toc.push({ level, id, text: h[2] });
      i++;
      continue;
    }

    if (/^-{3,}\s*$/.test(line)) {
      out.push("<hr>");
      i++;
      continue;
    }

    // Definition block: consecutive **Label:** value lines. Strict CommonMark would
    // fold these into one run-on paragraph; the author means labelled fields.
    if (/^\*\*[^*]+:\*\*\s/.test(line)) {
      const items = [];
      while (i < lines.length && /^\*\*[^*]+:\*\*\s/.test(lines[i])) {
        const dm = /^\*\*([^*]+):\*\*\s+(.*)$/.exec(lines[i]);
        items.push(`<dt>${inline(dm[1])}</dt><dd>${inline(dm[2])}</dd>`);
        i++;
      }
      out.push(`<dl class="meta">${items.join("")}</dl>`);
      continue;
    }

    if (line.startsWith("|")) {
      const header = cells(line);
      const align = lines[i + 1] ?? "";
      if (!/^\|[\s:|-]+\|?\s*$/.test(align)) unsupported(n, line, "table without an alignment row");
      const aligns = cells(align).map((c) =>
        c.startsWith(":") && c.endsWith(":") ? "center" : c.endsWith(":") ? "right" : "left",
      );
      i += 2;
      const body = [];
      while (i < lines.length && lines[i].startsWith("|")) body.push(cells(lines[i++]));
      const th = header.map((c, k) => `<th style="text-align:${aligns[k] ?? "left"}">${inline(c)}</th>`).join("");
      const rows = body
        .map(
          (r) =>
            `<tr>${r.map((c, k) => `<td style="text-align:${aligns[k] ?? "left"}">${inline(c)}</td>`).join("")}</tr>`,
        )
        .join("");
      out.push(`<div class="scroll"><table><thead><tr>${th}</tr></thead><tbody>${rows}</tbody></table></div>`);
      continue;
    }

    if (/^>/.test(line)) {
      const paras = [[]];
      while (i < lines.length && /^>/.test(lines[i])) {
        const content = lines[i].replace(/^>\s?/, "");
        if (content.trim() === "") paras.push([]);
        else paras[paras.length - 1].push(content);
        i++;
      }
      const inner = paras
        .filter((p) => p.length)
        .map((p) => `<p>${inline(p.join(" "))}</p>`)
        .join("");
      out.push(`<blockquote>${inner}</blockquote>`);
      continue;
    }

    if (/^[-*]\s/.test(line)) {
      const items = [];
      while (i < lines.length && /^[-*]\s/.test(lines[i])) {
        items.push(`<li>${inline(lines[i].replace(/^[-*]\s+/, ""))}</li>`);
        i++;
      }
      out.push(`<ul>${items.join("")}</ul>`);
      continue;
    }

    const para = [];
    while (
      i < lines.length &&
      lines[i].trim() !== "" &&
      !/^(#{1,6}\s|[-*]\s|>|\||```|-{3,}\s*$)/.test(lines[i]) &&
      !/^\*\*[^*]+:\*\*\s/.test(lines[i])
    ) {
      para.push(lines[i]);
      i++;
    }
    if (!para.length) unsupported(n, line, "unrecognised line");
    out.push(`<p>${inline(para.join(" "))}</p>`);
  }

  return { blocks: out, toc, diagrams };
}

/* ---------- style ---------- */

const CSS = `
:root{
  --ink:#141821;--ink-soft:#4a5568;--ink-faint:#78849a;
  --paper:#fbfcfe;--panel:#fff;--panel-2:#f3f6fb;
  --rule:#dfe5ee;--rule-soft:#eaeff6;
  --brand:#4c3fb0;--brand-2:#8b5cf6;--brand-3:#0ea5a4;--ember:#c2703a;
  --shadow:0 1px 2px rgba(20,24,33,.05),0 8px 24px rgba(20,24,33,.06);
  --radius:14px;
}
@media (prefers-color-scheme:dark){
  :root{
    --ink:#e8ecf4;--ink-soft:#a9b4c6;--ink-faint:#7f8b9f;
    --paper:#0e1116;--panel:#151a21;--panel-2:#1b212a;
    --rule:#28303b;--rule-soft:#1f262f;
    --brand:#a99cff;--brand-2:#c4b5fd;--brand-3:#5eead4;--ember:#f0a878;
    --shadow:0 1px 2px rgba(0,0,0,.4),0 10px 30px rgba(0,0,0,.35);
  }
}
*{box-sizing:border-box}
html{scroll-behavior:smooth;scroll-padding-top:2rem}
body{margin:0;background:var(--paper);color:var(--ink);
  font:17px/1.7 ui-sans-serif,-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;-webkit-font-smoothing:antialiased}
#progress{position:fixed;top:0;left:0;height:3px;width:0;z-index:50;
  background:linear-gradient(90deg,var(--brand),var(--brand-2),var(--brand-3));transition:width .1s linear}

header.hero{position:relative;overflow:hidden;color:#f4f2ff;
  background:
    radial-gradient(1200px 400px at 10% -10%,rgba(139,92,246,.55),transparent 60%),
    radial-gradient(900px 380px at 90% 0%,rgba(14,165,164,.45),transparent 55%),
    linear-gradient(160deg,#1a1440 0%,#241a5c 45%,#0f1230 100%)}
header.hero::after{content:"";position:absolute;inset:0;pointer-events:none;
  background-image:radial-gradient(rgba(255,255,255,.13) 1px,transparent 1px);background-size:22px 22px;
  mask-image:linear-gradient(180deg,rgba(0,0,0,.7),transparent 75%)}
.hero-inner{position:relative;z-index:1;max-width:1080px;margin:0 auto;padding:3.5rem 1.5rem 3rem}
.sitenav{display:flex;gap:.5rem;flex-wrap:wrap;margin:0 0 2rem}
.sitenav a{font-size:.86rem;padding:.35rem .8rem;border-radius:999px;text-decoration:none;
  border:1px solid rgba(255,255,255,.2);color:#ded9ff;transition:background .15s}
.sitenav a:hover{background:rgba(255,255,255,.12)}
.sitenav a[aria-current]{background:rgba(255,255,255,.92);border-color:transparent;color:#241a5c;font-weight:600}
.eyebrow{display:inline-flex;align-items:center;gap:.5rem;margin:0 0 1rem;font-size:.72rem;letter-spacing:.16em;
  text-transform:uppercase;color:#c8c2ff;border:1px solid rgba(255,255,255,.22);border-radius:999px;padding:.3rem .8rem}
.eyebrow::before{content:"";width:6px;height:6px;border-radius:50%;background:var(--brand-3);box-shadow:0 0 10px var(--brand-3)}
header.hero h1{margin:0;font-size:clamp(2rem,5vw,3.3rem);line-height:1.05;letter-spacing:-.03em;font-weight:700;
  background:linear-gradient(100deg,#fff 20%,#cdbcff 70%,#8ee9df 100%);
  -webkit-background-clip:text;background-clip:text;color:transparent}
header.hero h1 .anchor{display:none}
.hero .meta{margin:2rem 0 0;display:grid;grid-template-columns:max-content 1fr;gap:.55rem 1.1rem;font-size:.92rem;
  border-top:1px solid rgba(255,255,255,.16);padding-top:1.4rem}
.hero .meta dt{font-weight:600;color:#a9a1e8;white-space:nowrap}
.hero .meta dd{margin:0;color:#e9e7fb}
.hero .meta a{color:#9ce8dd;border-bottom-color:rgba(156,232,221,.4)}
.hero .meta code{background:rgba(255,255,255,.12);border-color:rgba(255,255,255,.2);color:#f2f0ff}
.hero .meta .adr{background:rgba(255,255,255,.14);border-color:rgba(255,255,255,.25);color:#f0edff}

.shell{max-width:1080px;margin:0 auto;padding:0 1.5rem 6rem}
@media (min-width:1180px){.shell{max-width:1340px;display:grid;grid-template-columns:250px minmax(0,1fr);gap:3.5rem;align-items:start}}
nav.toc{display:none}
@media (min-width:1180px){
  nav.toc{display:block;position:sticky;top:2rem;max-height:calc(100vh - 4rem);overflow-y:auto;
    padding:2.5rem 0 2rem;font-size:.88rem;border-right:1px solid var(--rule-soft)}
  nav.toc p{margin:0 0 .8rem;font-size:.7rem;letter-spacing:.14em;text-transform:uppercase;color:var(--ink-faint);font-weight:700}
  nav.toc ol{list-style:none;margin:0;padding:0}
  nav.toc ol ol{padding-left:.9rem}
  nav.toc li{margin:.12rem 0}
  nav.toc a{display:block;padding:.3rem .6rem;border-radius:7px;color:var(--ink-soft);text-decoration:none;
    border-left:2px solid transparent;border-bottom:0;transition:background .15s,color .15s}
  nav.toc a:hover{background:var(--panel-2);color:var(--ink)}
  nav.toc a.active{color:var(--brand);border-left-color:var(--brand);background:var(--panel-2);font-weight:600}
}
article{padding-top:2.5rem;min-width:0}

h2,h3,h4{letter-spacing:-.015em;scroll-margin-top:1.5rem}
h2{font-size:1.72rem;margin:3.5rem 0 1rem;padding-bottom:.55rem;border-bottom:1px solid var(--rule);position:relative}
h2::after{content:"";position:absolute;left:0;bottom:-1px;width:72px;height:3px;border-radius:3px;
  background:linear-gradient(90deg,var(--brand),var(--brand-3))}
h3{font-size:1.2rem;margin:2.6rem 0 .6rem}
h4{font-size:1rem;margin:1.8rem 0 .4rem;color:var(--ink-soft)}
.anchor{margin-left:.45rem;color:var(--ink-faint);opacity:0;text-decoration:none;border-bottom:0;font-weight:400;font-size:.8em}
h2:hover .anchor,h3:hover .anchor,h4:hover .anchor{opacity:1}

p{margin:1rem 0}
ul{margin:1rem 0;padding-left:1.3rem}
li{margin:.5rem 0}
li::marker{color:var(--brand)}
a{color:var(--brand);text-decoration:none;border-bottom:1px solid color-mix(in srgb,var(--brand) 30%,transparent)}
a:hover{border-bottom-color:var(--brand)}
strong{font-weight:650}
code{background:var(--panel-2);border:1px solid var(--rule-soft);border-radius:5px;padding:.08em .35em;
  font:.86em/1.4 ui-monospace,SFMono-Regular,"Cascadia Code",Consolas,monospace}
hr{border:0;height:1px;background:linear-gradient(90deg,var(--rule),transparent);margin:3rem 0}

.adr{display:inline-block;padding:.05em .45em;border-radius:6px;background:var(--panel-2);border:1px solid var(--rule);
  font:600 .8em/1.5 ui-monospace,SFMono-Regular,Consolas,monospace;color:var(--brand);text-decoration:none;white-space:nowrap}
a.adr:hover{background:var(--brand);border-color:var(--brand);color:#fff}

blockquote{margin:1.6rem 0;padding:1.1rem 1.3rem;border-radius:var(--radius);background:var(--panel);
  border:1px solid var(--rule);border-left:4px solid var(--ember);box-shadow:var(--shadow)}
blockquote p{margin:.45rem 0;color:var(--ink-soft)}
blockquote p:first-child{margin-top:0}
blockquote p:last-child{margin-bottom:0}
blockquote strong{color:var(--ink)}

dl.meta{display:grid;grid-template-columns:max-content 1fr;gap:.4rem 1rem;margin:1.2rem 0}
dl.meta dt{font-weight:650;color:var(--ink-soft);white-space:nowrap}
dl.meta dd{margin:0}

.scroll{overflow-x:auto;margin:1.5rem 0;border:1px solid var(--rule);border-radius:var(--radius);
  background:var(--panel);box-shadow:var(--shadow)}
table{border-collapse:collapse;width:100%;font-size:.93rem}
thead th{position:sticky;top:0;z-index:1;background:var(--panel-2);font-size:.74rem;letter-spacing:.08em;
  text-transform:uppercase;color:var(--ink-soft);font-weight:700;padding:.7rem .8rem;
  border-bottom:1px solid var(--rule);white-space:nowrap}
td{padding:.6rem .8rem;border-bottom:1px solid var(--rule-soft);vertical-align:top;color:var(--ink-soft)}
tbody tr:last-child td{border-bottom:0}
tbody tr:nth-child(even){background:color-mix(in srgb,var(--panel-2) 55%,transparent)}
tbody tr:hover{background:color-mix(in srgb,var(--brand) 7%,transparent)}
td strong{color:var(--ink)}

figure.diagram{margin:1.8rem 0;padding:1.4rem .8rem;border:1px solid var(--rule);border-radius:var(--radius);
  background:radial-gradient(700px 200px at 50% 0%,color-mix(in srgb,var(--brand) 8%,transparent),transparent 70%),var(--panel);
  box-shadow:var(--shadow);overflow-x:auto;text-align:center}
figure.diagram pre.mermaid{margin:0;font:.8rem/1.5 ui-monospace,Consolas,monospace;color:var(--ink-faint);
  text-align:left;white-space:pre-wrap}
figure.diagram svg{max-width:100%;height:auto}

footer.genby{margin:4rem 0 0;padding-top:1.2rem;border-top:1px solid var(--rule);font-size:.85rem;color:var(--ink-faint)}

@media print{
  #progress,nav.toc,.anchor,.sitenav{display:none}
  header.hero{color:#000;background:none}
  header.hero h1{color:#000;-webkit-text-fill-color:#000}
  .hero .meta dt,.hero .meta dd,.eyebrow{color:#000}
  .shell{display:block}
  .scroll,figure.diagram,blockquote{box-shadow:none}
}
`;

const SCRIPT = `
const bar = document.getElementById("progress");
const onScroll = () => {
  const h = document.documentElement;
  const max = h.scrollHeight - h.clientHeight;
  bar.style.width = (max > 0 ? (h.scrollTop / max) * 100 : 0) + "%";
};
addEventListener("scroll", onScroll, { passive: true });
onScroll();
const links = new Map();
document.querySelectorAll("nav.toc a").forEach((a) => links.set(a.getAttribute("href").slice(1), a));
const spy = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    links.forEach((a) => a.classList.remove("active"));
    links.get(e.target.id)?.classList.add("active");
  }
}, { rootMargin: "-10% 0px -80% 0px" });
links.forEach((_, id) => { const el = document.getElementById(id); if (el) spy.observe(el); });
`;

const MERMAID_INIT = `
import mermaid from "${MERMAID}";
mermaid.initialize({
  startOnLoad: true,
  theme: matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "neutral",
  fontFamily: "ui-sans-serif, -apple-system, Segoe UI, Roboto, sans-serif",
  flowchart: { htmlLabels: true, curve: "basis", padding: 14 },
  themeVariables: { fontSize: "14px" },
});
`;

/* ---------- page assembly ---------- */

function siteNav(current) {
  return `<nav class="sitenav">${PAGES.map((p) => {
    const href = "./" + p.out.replace(/^docs\//, "");
    const flag = p.out === current ? ' aria-current="page"' : "";
    return `<a href="${href}"${flag}>${p.nav}</a>`;
  }).join("")}<a href="${REPO}adr/README.md">Decision records</a></nav>`;
}

function page(page_, { blocks, toc, diagrams }, title) {
  // The hero swallows the h1 and the leading metadata list; everything else is body.
  const rest = blocks.slice();
  const hero = [];
  while (rest.length && /^<h1|^<dl class="meta">/.test(rest[0])) hero.push(rest.shift());
  while (rest.length && rest[0] === "<hr>") rest.shift();

  let nav = "";
  if (toc.length) {
    let depth = 2;
    const parts = ['<nav class="toc"><p>Contents</p><ol>'];
    for (const h of toc) {
      if (h.level > depth) parts.push("<ol>");
      else if (h.level < depth) parts.push("</ol>");
      depth = h.level;
      parts.push(`<li><a href="#${h.id}">${inline(h.text, { badges: false })}</a></li>`);
    }
    if (depth === 3) parts.push("</ol>");
    parts.push("</ol></nav>");
    nav = parts.join("");
  }

  const diagramNote = diagrams
    ? ` ${diagrams} diagram${diagrams === 1 ? "" : "s"} drawn client-side by Mermaid.`
    : "";

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title>
<meta name="description" content="Vespera — a document curation pipeline that measures a corpus before judging it.">
<style>${CSS}</style>
</head>
<body>
<div id="progress"></div>
<header class="hero">
  <div class="hero-inner">
${siteNav(page_.out)}
    <p class="eyebrow">${page_.eyebrow}</p>
${hero.join("\n")}
  </div>
</header>
<div class="shell">
${nav}
<article>
${rest.join("\n")}
<footer class="genby">Generated from <code>${page_.src.replace(/^docs\//, "")}</code> by <code>docs/render-docs.mjs</code>. Do not edit this file; edit the Markdown and re-run the script.${diagramNote}</footer>
</article>
</div>
<script>${SCRIPT}</script>
${diagrams ? `<script type="module">${MERMAID_INIT}</script>` : ""}
</body>
</html>
`;
}

/* ---------- content-loss check ---------- */

const words = (s) => s.toLowerCase().match(/[a-z0-9]+/g) ?? [];

function assertNothingLost(src, md, html) {
  // Keep href values as text, then drop tags, so words living only in a URL still
  // count. Style and script blocks are dropped: those are the renderer's own words.
  const text = html
    .replace(/<style[\s\S]*?<\/style>/g, " ")
    .replace(/<script[\s\S]*?<\/script>/g, " ")
    .replace(/<head[\s\S]*?<\/head>/g, " ")
    .replace(/<[^>]*href="([^"]*)"[^>]*>/g, " $1 ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");

  const have = new Map();
  for (const w of words(text)) have.set(w, (have.get(w) ?? 0) + 1);
  const need = new Map();
  // Fence info strings are markup, not content: the word mermaid on a ``` line is
  // not text the page must contain.
  const mdForCheck = md.split("```mermaid").join(" ").split("```").join(" ");
  for (const w of words(mdForCheck)) need.set(w, (need.get(w) ?? 0) + 1);

  const missing = [];
  for (const [w, count] of need) if ((have.get(w) ?? 0) < count) missing.push(`${w} (${have.get(w) ?? 0}/${count})`);
  if (missing.length) {
    throw new Error(
      `${src}: content lost in rendering: ${missing.slice(0, 12).join(", ")}${missing.length > 12 ? ` … +${missing.length - 12} more` : ""}`,
    );
  }
}

/* ---------- main ---------- */

indexAdrs();

// Newlines are normalised before comparing: core.autocrlf is on, so a checked-out
// HTML file has CRLF on disk while this script writes LF.
const norm = (s) => s.replace(/\r\n/g, "\n");
const check = process.argv.includes("--check");
let stale = 0;

for (const p of PAGES) {
  const md = readFileSync(p.src, "utf8");
  const parsed = renderBlocks(p.src, md.split(/\r?\n/));
  const title = (/^#\s+(.*)$/m.exec(md)?.[1] ?? "Vespera").replace(/`/g, "");
  const html = page(p, parsed, title);
  assertNothingLost(p.src, md, html);

  if (check) {
    let current = "";
    try {
      current = readFileSync(p.out, "utf8");
    } catch {
      console.error(`${p.out} does not exist`);
      stale++;
      continue;
    }
    if (norm(current) !== norm(html)) {
      console.error(`${p.out} is stale`);
      stale++;
    } else {
      console.log(`${p.out} is up to date`);
    }
  } else {
    writeFileSync(p.out, html);
    console.log(
      `wrote ${p.out} (${(Buffer.byteLength(html) / 1024).toFixed(1)} kB, ${parsed.toc.length} sections, ${parsed.diagrams} diagrams)`,
    );
  }
}

if (check && stale) {
  console.error(`${stale} page(s) stale — re-run without --check`);
  process.exit(1);
}
if (!check) console.log(`${adrFile.size} ADR ids linked from the ledger`);
