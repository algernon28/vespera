#!/usr/bin/env node
// Resolves a merge's conflicts in the ADR index and in Adr.java (ADR-363 §3), run from the repository's root with no
// argument while a merge, a rebase or a cherry-pick stands with its conflicts:
//
//   node docs/merge-adr-index.mjs
//
// It reads docs/adr/README.md and src/test/java/io/algernon/vespera/Adr.java itself, so that no conflict marker appears
// in a command's text. It runs no git command and stages nothing; adding the two files and committing the merge are left
// to whoever merges.
//
//   exit 0  neither file holds a conflict marker once it is done; a line for each file names its path
//   exit 1  a file is refused (its sides differ outside the rows or constants, or hold one record with different
//           text, or hold their rows or constants in different places; or it holds a conflict marker where none is
//           allowed, as a `>>>>>>>` with no conflict open or a `|||||||` or `>>>>>>>` before `=======`, or a conflict
//           never closed); neither file is written, and standard error names the file and why
//   exit 2  a file is not under the working directory; nothing is written, and standard error names each

import { existsSync, readFileSync, writeFileSync } from "node:fs";

const INDEX = "docs/adr/README.md";
const ADR_JAVA = "src/test/java/io/algernon/vespera/Adr.java";

const ROW = /^\| \[ADR-(\d+)\][^\n]*(?:\n|$)/gm;
const CONSTANT =
  /^(?:[ \t]*\/\*\*(?:(?!\*\/)[\s\S])*\*\/[ \t]*\n)?[ \t]*public static final String \w+\s*=[^;]*?"(\d{4})-[^"]*\.md"[^;]*;[ \t]*\n(?:[ \t]*\n)?/gm;

const id = (n) => `ADR-${String(n).padStart(3, "0")}`;

/** The two sides of a text holding conflicts, each with its line endings made LF; the base, if any, is dropped. */
function sidesOf(text) {
  const first = [];
  const second = [];
  let where = "outside";
  for (const line of text.split(/\r?\n/)) {
    if (/^<{7}( |$)/.test(line)) {
      if (where !== "outside") throw new Error("a conflict marker opens inside a conflict");
      where = "first";
    } else if (/^\|{7}( |$)/.test(line) && where === "first") {
      where = "base";
    } else if (/^={7}$/.test(line) && (where === "first" || where === "base")) {
      where = "second";
    } else if (/^>{7}( |$)/.test(line) && where === "second") {
      where = "outside";
    } else if (/^(<{7}|\|{7}|>{7})/.test(line)) {
      throw new Error(`a conflict marker stands where none is allowed: ${line.slice(0, 7)}`);
    } else {
      if (where === "outside" || where === "first") first.push(line);
      if (where === "outside" || where === "second") second.push(line);
    }
  }
  if (where !== "outside") throw new Error("a conflict is not closed");
  return [first.join("\n"), second.join("\n")];
}

/**
 * What a side holds besides its blocks, its stretches (blocks standing next to each other) each with where it starts in
 * that and the number of its first block, and its blocks by number.
 */
function split(side, pattern, normal) {
  let rest = "";
  let last = 0;
  const stretches = [];
  const blocks = new Map();
  for (const m of side.matchAll(pattern)) {
    const joins = stretches.length > 0 && m.index === last;
    rest += side.slice(last, m.index);
    const number = Number(m[1]);
    if (!joins) stretches.push({ at: rest.length, first: number });
    last = m.index + m[0].length;
    blocks.set(number, [...(blocks.get(number) ?? []), normal(m[0])]);
  }
  rest += side.slice(last);
  return { rest, stretches, blocks };
}

/** The file's text resolved, or an Error saying why the file is refused. */
function resolved(text, pattern, normal) {
  const [firstSide, secondSide] = sidesOf(text);
  const a = split(firstSide, pattern, normal);
  const b = split(secondSide, pattern, normal);
  if (a.rest !== b.rest) throw new Error("the two sides differ outside its rows or constants");
  if (a.stretches.length !== b.stretches.length || a.stretches.some((s, k) => s.at !== b.stretches[k].at)) {
    throw new Error("the two sides hold their rows or constants in different places");
  }
  const numbers = new Set([...a.blocks.keys(), ...b.blocks.keys()]);
  const stretches = a.stretches.map((s, k) => ({ at: s.at, first: Math.min(s.first, b.stretches[k].first), share: [] }));
  for (const number of [...numbers].sort((x, y) => x - y)) {
    const mine = a.blocks.get(number);
    const theirs = b.blocks.get(number);
    if (mine && theirs && mine.join("") !== theirs.join("")) throw new Error(`${id(number)} is held with different text on the two sides`);
    const owner = stretches.findLast((s) => s.first <= number) ?? stretches[0];
    owner.share.push(...(mine ?? theirs));
  }
  let out = "";
  let from = 0;
  for (const s of stretches) {
    out += a.rest.slice(from, s.at) + s.share.join("");
    from = s.at;
  }
  return out + a.rest.slice(from);
}

const FILES = [
  { path: INDEX, pattern: ROW, normal: (block) => block.replace(/\n$/, "") + "\n" },
  { path: ADR_JAVA, pattern: CONSTANT, normal: (block) => block.replace(/\s+$/, "") + "\n\n" },
];

const missing = FILES.filter((f) => !existsSync(f.path));
if (missing.length) {
  for (const f of missing) console.error(`${f.path}: not found under ${process.cwd()}`);
  process.exit(2);
}

const refused = [];
const writes = [];
for (const f of FILES) {
  const text = readFileSync(f.path, "utf8");
  if (!/^(<{7}|\|{7}|>{7})( |$)/m.test(text)) continue;
  try {
    const out = resolved(text, f.pattern, f.normal);
    writes.push({ path: f.path, text: text.includes("\r\n") ? out.replaceAll("\n", "\r\n") : out });
  } catch (e) {
    refused.push(`${f.path}: refused, ${e.message}`);
  }
}

if (refused.length) {
  for (const r of refused) console.error(r);
  process.exit(1);
}
for (const w of writes) writeFileSync(w.path, w.text);
for (const f of FILES) console.log(`${f.path}: ${writes.some((w) => w.path === f.path) ? "resolved" : "no conflict marker, left as it is"}`);
