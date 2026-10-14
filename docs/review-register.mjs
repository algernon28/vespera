// Edits the review register's data file in place (docs/review-register.md), so that a pass over the register reads
// and writes one file and never the whole published page, which a read of the artifact echoes in full.
//
//   node docs/review-register.mjs meta   <data.js> <note.html>      append a line to the page's meta lines
//   node docs/review-register.mjs item   <data.js> <item.json>      append a finding, or an array of them
//   node docs/review-register.mjs status <data.js> <id> <status> [commit]
//   node docs/review-register.mjs check  <data.js>                  every finding whole, every id once, every status known

import { readFileSync, writeFileSync } from "node:fs";

const STATUSES = ["fixed", "refuted", "intended", "partial", "deferred", "open"];
const FIELDS = ["id", "area", "file", "line", "verdict", "status", "commit", "title", "summary", "scenario", "source", "why"];

const HEAD = "// The register's data: what each pass did (META, html) and every finding (ITEMS). See docs/review-register.md.\n";

function load(path) {
  const text = readFileSync(path, "utf8");
  return new Function(`${text}\nreturn { META, ITEMS };`)();
}

function save(path, { META, ITEMS }) {
  const lines = (list) => `[\n${list.map((x) => JSON.stringify(x)).join(",\n")}\n]`;
  writeFileSync(path, `${HEAD}const META = ${lines(META)};\nconst ITEMS = ${lines(ITEMS)};\n`);
}

function problems({ ITEMS }) {
  const found = [];
  const seen = new Set();
  for (const item of ITEMS) {
    const where = item.id ?? "(no id)";
    for (const field of FIELDS) if (!(field in item)) found.push(`${where} lacks ${field}`);
    if (item.status && !STATUSES.includes(item.status)) found.push(`${where} has status ${item.status}; statuses are ${STATUSES.join(", ")}`);
    if (seen.has(item.id)) found.push(`${where} is in the register twice`);
    seen.add(item.id);
  }
  return found;
}

function refuse(message) {
  console.error(message);
  process.exit(1);
}

const [command, path, ...rest] = process.argv.slice(2);
if (!command || !path) refuse("usage: review-register.mjs meta|item|status|check <data.js> ...");
const register = load(path);

switch (command) {
  case "meta": {
    const note = readFileSync(rest[0], "utf8").trim();
    if (!note) refuse("the note is empty");
    register.META.push(note);
    save(path, register);
    break;
  }
  case "item": {
    const parsed = JSON.parse(readFileSync(rest[0], "utf8"));
    const added = Array.isArray(parsed) ? parsed : [parsed];
    const next = { META: register.META, ITEMS: [...register.ITEMS, ...added] };
    const found = problems(next);
    if (found.length) refuse(found.join("\n"));
    save(path, next);
    break;
  }
  case "status": {
    const [id, status, commit] = rest;
    if (!STATUSES.includes(status)) refuse(`${status} is no status; statuses are ${STATUSES.join(", ")}`);
    const item = register.ITEMS.find((i) => i.id === id);
    if (!item) refuse(`no finding ${id}`);
    item.status = status;
    if (commit !== undefined) item.commit = commit;
    save(path, register);
    break;
  }
  case "check": {
    const found = problems(register);
    if (found.length) refuse(found.join("\n"));
    console.log(`${register.ITEMS.length} findings, ${register.META.length} meta lines, all whole`);
    break;
  }
  default:
    refuse(`no command ${command}`);
}
