#!/usr/bin/env node
// Extracts every Mermaid diagram from the Markdown documents into standalone .mmd
// files, so a real Mermaid parser can be pointed at them.
//
//   node docs/extract-diagrams.mjs [outdir]
//
// Default outdir is a temporary directory; the paths are printed, one per line, for
// a caller to feed to mmdc. Nothing is written into the repository.
//
// The point of this script is CI: a Mermaid syntax error renders as an error box in
// the browser, and every text-level check on the generated HTML still passes. Only
// a parser can tell the difference, so CI runs one over these files.

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";
import { tmpdir } from "node:os";

const SOURCES = ["docs/architecture.md", "docs/decision-ledger.md"];
const outdir = process.argv[2] ?? join(tmpdir(), "vespera-diagrams");

mkdirSync(outdir, { recursive: true });

let total = 0;
const written = [];

for (const src of SOURCES) {
  const lines = readFileSync(src, "utf8").split(/\r?\n/);
  const stem = basename(src, ".md");
  let n = 0;

  for (let i = 0; i < lines.length; i++) {
    if (!lines[i].startsWith("```mermaid")) continue;
    const body = [];
    let j = i + 1;
    while (j < lines.length && !lines[j].startsWith("```")) body.push(lines[j++]);
    if (j >= lines.length) throw new Error(`${src}:${i + 1}: unterminated mermaid fence`);
    if (!body.length) throw new Error(`${src}:${i + 1}: empty mermaid fence`);

    n++;
    total++;
    // The first non-blank line names the diagram type; useful in a failure message.
    const kind = (body.find((l) => l.trim()) ?? "").trim().split(/\s+/)[0];
    const file = join(outdir, `${stem}-${String(n).padStart(2, "0")}-${kind}.mmd`);
    writeFileSync(file, body.join("\n") + "\n");
    written.push({ file, src, line: i + 1, kind });
    i = j;
  }
}

for (const w of written) console.log(w.file);
console.error(`extracted ${total} diagram(s) from ${SOURCES.length} document(s) into ${outdir}`);
for (const w of written) console.error(`  ${w.src}:${w.line}  ${w.kind}  ->  ${basename(w.file)}`);

if (!total) {
  console.error("no diagrams found — if that is unexpected, the fences moved");
  process.exit(1);
}
