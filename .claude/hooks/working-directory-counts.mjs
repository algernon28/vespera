// Prints the aggregate counts of a Vespera working directory, and nothing else about it: docs/adr/0212
// is the record, and src/test/hooks/working-directory-counts.test.mjs holds this file to it.
//
//   node .claude/hooks/working-directory-counts.mjs <working directory>
//
// An agent may read what this prints (ADR-212 section 1), because the statements below are fixed here
// and the agent chooses only which working directory it is asked of. private-paths-guard.mjs admits
// exactly that command, and pins this file by its SHA-256 (COUNTING_SCRIPT_SHA256), so a change to this
// file is a change to the guard and is reviewed as one.
//
// It pulls in node: modules only. It opens vespera.db and nothing else, read-only, and never takes
// vespera.lock. It measures the folder from each entry's name, kind and size, follows no link, junction
// or symlink, and reads no byte of any file. Every printed value is checked against its shape (ADR-212
// section 3) and is printed as other when it fails, so no text and no path of the operator's reaches
// stdout. It works everything out before it prints anything, and one handler catches every error: nothing
// on stdout, one line on stderr carrying only the error's code, exit 1. A refused argument exits 2.

import { existsSync, lstatSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const EXIT_COUNTED = 0;
const EXIT_NOT_COUNTED = 1;
const EXIT_REFUSED = 2;

// The closed lists are the code's own (ADR-212 section 3); W02 holds them to src/main.
const VERDICT_KINDS = ["BROKEN", "DUPLICATE_OF", "SUPERSEDED_BY", "OUT_OF_SCOPE", "EXTRACTION_FAILED", "DEGENERATE_OUTPUT", "REDUNDANT_WITH", "BELOW_THRESHOLD", "PASSED"];
const BLOCKING_KINDS = ["BROKEN", "DUPLICATE_OF", "SUPERSEDED_BY", "OUT_OF_SCOPE", "EXTRACTION_FAILED", "DEGENERATE_OUTPUT", "REDUNDANT_WITH", "BELOW_THRESHOLD"];
const ANOMALY_KINDS = ["UNPROCESSABLE", "SOFT_LINK_NOT_FOLLOWED", "UNENCODABLE_PATH"];
const FAILURE_CATEGORIES = ["POLICY", "CAPACITY", "SOURCE_UNAVAILABLE", "TARGET_UNAVAILABLE", "TIMEOUT", "INTERNAL", "BACKEND_FAILURE", "INFERENCE_FAILURE", "UNKNOWN"];
const CLUSTER_FAULT_KINDS = ["PROMPT_EVALUATION_CEILING", "ANSWER_RAN_OUT_OF_ROOM", "SCHEMA_VIOLATION", "CITATION_NOT_IN_RANGE"];
const STAGES = ["byte-level-reduction", "extraction", "content-census", "content-redundancy", "seed-measurement", "embedding-scoring", "arrangement", "generation"];
const STEPS = [
  "census",
  "byte-level-reduction",
  "extraction",
  "content-census",
  "redundancy-signature",
  "content-redundancy",
  "seed-extraction",
  "seed-corpus-comparison",
  "embedding-scoring",
  "relevance-scoring",
  "relevance-floor",
  "clustering",
  "relevance-report",
  "arrangement",
  "generation",
];

const OTHER = "other";
const RUN_ID = /^[0-9a-f]{64}$/;
const DATABASE_FILES = new Set(["vespera.db", "vespera.db-wal", "vespera.db-shm"]);
const LOG_FILE = /^vespera(?:\.\d{4}-\d{2}-\d{2}\.\d+)?\.log$/;

/* ---------- the shapes ---------- */

// A decimal integer of 0 or more, as SQLite holds it; anything else prints as other.
const integer = (v) => (typeof v === "bigint" && v >= 0n ? v.toString() : OTHER);
// The same of a number this script summed itself, from sizes the file system gave: exact, or other.
const safeInteger = (n) => (Number.isSafeInteger(n) && n >= 0 ? String(n) : OTHER);
const zeroOrOne = (v) => (typeof v === "bigint" ? (v === 0n ? "0" : "1") : OTHER);
const runId = (v) => (typeof v === "string" && RUN_ID.test(v) ? v : OTHER);
const named = (list) => {
  const byLowerCase = new Map(list.map((name) => [name.toLowerCase(), name]));
  return (v) => (typeof v === "string" ? (byLowerCase.get(v.toLowerCase()) ?? OTHER) : OTHER);
};
const verdictKind = named(VERDICT_KINDS);
const anomalyKind = named(ANOMALY_KINDS);
const failureCategory = named(FAILURE_CATEGORIES);
const clusterFaultKind = named(CLUSTER_FAULT_KINDS);
const stage = named(STAGES);
const step = named(STEPS);

// What SQLite holds, told apart by its type, as a key of a map.
const keyOf = (v) => `${typeof v}:${String(v)}`;

/* ---------- the records ---------- */

const records = new Map();
function add(record, pairs, count = null) {
  let lines = records.get(record);
  if (!lines) records.set(record, (lines = new Map()));
  const key = pairs.map(([, value]) => value).join("\u0000");
  const known = lines.get(key);
  if (known) {
    if (count !== null) known.count += count;
  } else {
    lines.set(key, { pairs, count });
  }
}

const compareText = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
// A walk id is compared as a number, other last; anything else in code-point order.
function compareField(name, a, b) {
  if (name === "walk") {
    if (a === OTHER || b === OTHER) return a === b ? 0 : a === OTHER ? 1 : -1;
    const x = BigInt(a);
    const y = BigInt(b);
    return x < y ? -1 : x > y ? 1 : 0;
  }
  return compareText(a, b);
}

function linesOf(record, byRunOnly = false) {
  const lines = [...(records.get(record)?.values() ?? [])];
  lines.sort((a, b) => {
    if (byRunOnly) {
      const first = compareText(a.pairs[0][1], b.pairs[0][1]);
      if (first !== 0) return first;
      return compareText(a.pairs.slice(1).map(([, v]) => v).join(" "), b.pairs.slice(1).map(([, v]) => v).join(" "));
    }
    for (let i = 0; i < a.pairs.length; i++) {
      const order = compareField(a.pairs[i][0], a.pairs[i][1], b.pairs[i][1]);
      if (order !== 0) return order;
    }
    return 0;
  });
  return lines.map(({ pairs, count }) => [record, ...pairs.map(([k, v]) => `${k}=${v}`), ...(count === null ? [] : [`count=${integer(count)}`])].join(" "));
}

/* ---------- the working directory's files ---------- */

function measure(dir) {
  const tally = () => ({ files: 0, bytes: 0 });
  const everything = tally();
  const database = tally();
  const log = tally();
  const deliverable = tally();
  const otherInDeliverable = tally();
  const runs = new Map();
  let deliverableExists = false;
  const bump = (t, size) => {
    t.files++;
    t.bytes += size;
  };
  const visit = (folder, parts) => {
    for (const name of readdirSync(folder)) {
      const stat = lstatSync(join(folder, name));
      if (stat.isSymbolicLink()) continue;
      const here = [...parts, name];
      if (stat.isDirectory()) {
        if (here.length === 1 && name === "deliverable") deliverableExists = true;
        if (here.length === 2 && here[0] === "deliverable" && RUN_ID.test(name) && !runs.has(name)) runs.set(name, tally());
        visit(join(folder, name), here);
      } else if (stat.isFile()) {
        bump(everything, stat.size);
        if (here.length === 1 && DATABASE_FILES.has(name)) bump(database, stat.size);
        if (here.length === 1 && LOG_FILE.test(name)) bump(log, stat.size);
        if (here[0] === "deliverable" && here.length >= 2) {
          bump(deliverable, stat.size);
          if (here.length >= 3 && RUN_ID.test(here[1])) bump(runs.get(here[1]), stat.size);
          else bump(otherInDeliverable, stat.size);
        }
      }
    }
  };
  visit(dir, []);
  const line = (folder, t) => `files folder=${folder} files=${safeInteger(t.files)} bytes=${safeInteger(t.bytes)}`;
  const lines = [line("working-directory", everything), line("database", database), line("log", log)];
  if (deliverableExists) lines.push(line("deliverable", deliverable));
  for (const id of [...runs.keys()].sort(compareText)) lines.push(line(`deliverable/${id}`, runs.get(id)));
  if (otherInDeliverable.files > 0) lines.push(line("deliverable/other", otherInDeliverable));
  return lines;
}

/* ---------- vespera.db ---------- */

const SURVIVORS =
  "SELECT COUNT(*) AS n FROM file_occurrence" +
  " WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?1)" +
  " AND NOT EXISTS (SELECT 1 FROM verdict" +
  " WHERE verdict.occurrence_id = file_occurrence.id" +
  ` AND verdict.kind COLLATE BINARY IN (${BLOCKING_KINDS.map((kind) => `'${kind}'`).join(", ")})` +
  " AND verdict.run_id IN (WITH RECURSIVE reach(id) AS (SELECT ?1 UNION SELECT run_upstream.upstream_run_id" +
  " FROM reach JOIN run_upstream ON run_upstream.run_id = reach.id) SELECT id FROM reach))";

function countRecords(db) {
  const rows = (sql, ...params) => {
    const statement = db.prepare(sql);
    statement.setReadBigInts(true);
    return statement.all(...params);
  };

  const sizes = new Map();
  for (const row of rows(
    "SELECT walk_id, COUNT(*) AS n," +
      " SUM(CASE WHEN typeof(size_bytes) = 'integer' AND size_bytes >= 0 THEN 0 ELSE 1 END) AS misshapen," +
      " SUM(CASE WHEN typeof(size_bytes) = 'integer' AND size_bytes >= 0 THEN size_bytes ELSE 0 END) AS total" +
      " FROM file_occurrence GROUP BY walk_id",
  )) {
    sizes.set(keyOf(row.walk_id), row);
  }
  for (const row of rows("SELECT id, finished, entries_seen, directories_entered FROM walk")) {
    const held = sizes.get(keyOf(row.id));
    add("walk", [
      ["walk", integer(row.id)],
      ["finished", zeroOrOne(row.finished)],
      ["occurrences", held ? integer(held.n) : "0"],
      ["bytes", held ? (held.misshapen === 0n ? integer(held.total) : OTHER) : "0"],
      ["entries-seen", integer(row.entries_seen)],
      ["directories-entered", integer(row.directories_entered)],
    ]);
  }

  for (const row of rows("SELECT walk_id, kind, COUNT(*) AS n FROM walk_anomaly GROUP BY walk_id, kind")) {
    add("anomalies", [["walk", integer(row.walk_id)], ["kind", anomalyKind(row.kind)]], row.n);
  }

  const upstream = new Map();
  for (const row of rows("SELECT run_id, upstream_run_id FROM run_upstream")) {
    const key = keyOf(row.run_id);
    if (!upstream.has(key)) upstream.set(key, []);
    upstream.get(key).push(runId(row.upstream_run_id));
  }
  const runs = rows("SELECT id, stage, walk_id FROM run");
  for (const row of runs) {
    const ids = (upstream.get(keyOf(row.id)) ?? []).sort(compareText);
    add("run", [["run", runId(row.id)], ["stage", stage(row.stage)], ["walk", integer(row.walk_id)], ["upstream", ids.length ? ids.join(",") : "-"]]);
  }

  for (const row of rows("SELECT run_id, step, COUNT(*) AS n FROM finished_step GROUP BY run_id, step")) {
    add("finished-steps", [["run", runId(row.run_id)], ["step", step(row.step)]], row.n);
  }
  for (const row of rows("SELECT run_id, kind, COUNT(*) AS n FROM verdict GROUP BY run_id, kind")) {
    add("verdicts", [["run", runId(row.run_id)], ["kind", verdictKind(row.kind)]], row.n);
  }

  const survivors = db.prepare(SURVIVORS);
  survivors.setReadBigInts(true);
  for (const row of runs) add("survivors", [["run", runId(row.id)]], survivors.get(row.id).n);

  for (const row of rows("SELECT run_id, category, COUNT(*) AS n FROM extraction_fault GROUP BY run_id, category")) {
    add("extraction-faults", [["run", runId(row.run_id)], ["category", failureCategory(row.category)]], row.n);
  }
  for (const row of rows("SELECT run_id, COUNT(*) AS n FROM cluster GROUP BY run_id")) {
    add("clusters", [["run", runId(row.run_id)]], row.n);
  }
  for (const row of rows("SELECT run_id, COUNT(*) AS n FROM synthesis_doc GROUP BY run_id")) {
    add("synthesis-docs", [["run", runId(row.run_id)]], row.n);
  }
  for (const row of rows("SELECT run_id, kind, COUNT(*) AS n FROM cluster_fault GROUP BY run_id, kind")) {
    add("cluster-faults", [["run", runId(row.run_id)], ["kind", clusterFaultKind(row.kind)]], row.n);
  }
}

/* ---------- the whole script, under one handler ---------- */

// Only a code: the file system's, SQLite's result code as a number, or a fixed word.
function codeOf(caught) {
  if (Number.isInteger(caught?.errcode) && caught.errcode >= 0 && caught.errcode <= 99999) return String(caught.errcode);
  if (typeof caught?.code === "string" && /^[A-Z][A-Z0-9_]{0,39}$/.test(caught.code)) return caught.code;
  return "error";
}

function refuse() {
  process.stderr.write("refused: exactly one argument is wanted, a folder that directly holds a regular file named vespera.db\n");
  return EXIT_REFUSED;
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length !== 1) return refuse();
  const dir = resolve(args[0]);
  const databasePath = join(dir, "vespera.db");
  try {
    if (!lstatSync(databasePath).isFile()) return refuse();
  } catch (caught) {
    if (caught?.code === "ENOENT" || caught?.code === "ENOTDIR") return refuse();
    throw caught;
  }

  const files = measure(dir);

  // node:sqlite warns that it is experimental when it is loaded, and success writes nothing to stderr.
  process.removeAllListeners("warning");
  const { DatabaseSync } = await import("node:sqlite");
  const atRest = !existsSync(`${databasePath}-wal`);
  const db = atRest ? new DatabaseSync(`${pathToFileURL(databasePath).href}?immutable=1`, { readOnly: true }) : new DatabaseSync(databasePath, { readOnly: true });
  try {
    db.exec("PRAGMA busy_timeout = 5000");
    countRecords(db);
  } finally {
    db.close();
  }

  const lines = [
    ...linesOf("walk"),
    ...linesOf("anomalies"),
    ...linesOf("run", true),
    ...linesOf("finished-steps"),
    ...linesOf("verdicts"),
    ...linesOf("survivors"),
    ...linesOf("extraction-faults"),
    ...linesOf("clusters"),
    ...linesOf("synthesis-docs"),
    ...linesOf("cluster-faults"),
    ...files,
  ];
  process.stdout.write(lines.join("\n") + "\n");
  return EXIT_COUNTED;
}

try {
  process.exitCode = await main();
} catch (caught) {
  process.stderr.write(`${codeOf(caught)}\n`);
  process.exitCode = EXIT_NOT_COUNTED;
}
