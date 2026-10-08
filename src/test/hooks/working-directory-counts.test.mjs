// The counting script, held to its record: docs/adr/0212, accepted and not yet built. An agent may read what
// .claude/hooks/working-directory-counts.mjs prints about a working directory, and nothing else in it:
// counts and sums out of vespera.db by fixed statements, keyed by walk ids, run ids and closed
// vocabularies, and how many files the working directory holds and their size.
//
//   node --test src/test/hooks/working-directory-counts.test.mjs
//
// Every test starts the script itself, with this process's node, over a working directory built under
// the system temp folder: a vespera.db made from src/main/resources/schema.sql, filled with rows whose
// every text and path column, and some of whose file and folder names, carry MARKER. Nothing here names
// a path of the operator's. Until the script is built every test fails, saying so.

import { after, test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, realpathSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// node:sqlite warns that it is experimental when it is loaded, under Node 22. The fixture is built here.
process.removeAllListeners("warning");
const { DatabaseSync } = await import("node:sqlite");

const fwd = (p) => p.replace(/\\/g, "/");
const windows = process.platform === "win32";
const repo = fwd(resolve(dirname(fileURLToPath(import.meta.url)), "..", "..", ".."));
const SCRIPT = `${repo}/.claude/hooks/working-directory-counts.mjs`;
const SCHEMA = readFileSync(`${repo}/src/main/resources/schema.sql`, "utf8");
const JAVA = `${repo}/src/main/java/io/algernon/vespera`;

const COUNTED = 0;
const REFUSED = 2;
const UNREADABLE = 1;

// A string that cannot be one of the script's own words: no closed name has a lower-case letter after
// an upper-case one. It stands for the operator's paths, titles, reasons and prose.
const MARKER = "Zq7Marker";

/* ---------- the closed lists, read out of the code ---------- */

const read = (file) => readFileSync(`${JAVA}/${file}`, "utf8");
const constantsOf = (file) => [...read(file).matchAll(/^ {4}([A-Z][A-Z0-9_]*)\s*(?:\(|,|;|$)/gm)].map((m) => m[1]);
const VERDICT_KINDS = constantsOf("ledger/VerdictKind.java");
const BLOCKING_KINDS = [...read("ledger/VerdictKind.java").matchAll(/^ {4}([A-Z][A-Z0-9_]*)\(true\)/gm)].map((m) => m[1]);
const ANOMALY_KINDS = constantsOf("corpus/WalkAnomalyKind.java");
const FAILURE_CATEGORIES = constantsOf("extraction/FailureCategory.java");
const CLUSTER_FAULT_KINDS = constantsOf("synthesis/ClusterFaultKind.java");
const STAGES = [...read("pipeline/StageModules.java").matchAll(/\("([a-z][a-z-]*)", List\.of/g)].map((m) => m[1]);
const STEPS = [...read("pipeline/StepNames.java").matchAll(/static final String [A-Z_]+ = "([a-z][a-z-]*)";/g)].map((m) => m[1]);
for (const [name, list] of Object.entries({ VERDICT_KINDS, BLOCKING_KINDS, ANOMALY_KINDS, FAILURE_CATEGORIES, CLUSTER_FAULT_KINDS, STAGES, STEPS })) {
  if (list.length < 3) throw new Error(`the fixture read ${list.length} names into ${name} out of src/main, so it no longer reads that list`);
}

// The shape of every line the script prints (docs/adr/0212 section 3), built from those lists.
const RUN_ID = "[0-9a-f]{64}";
const NAMES = [...VERDICT_KINDS, ...ANOMALY_KINDS, ...FAILURE_CATEGORIES, ...CLUSTER_FAULT_KINDS, ...STAGES, ...STEPS, "other"];
const FOLDERS = ["working-directory", "database", "log", "deliverable", `deliverable/${RUN_ID}`, "deliverable/other"];
const VALUE = `(?:\\d+|${RUN_ID}(?:,${RUN_ID})*|-|${NAMES.join("|")}|${FOLDERS.join("|")})`;
const RECORDS = ["walk", "anomalies", "run", "finished-steps", "verdicts", "survivors", "extraction-faults", "clusters", "synthesis-docs", "cluster-faults", "files"];
const LINE = new RegExp(`^(?:${RECORDS.join("|")})(?: [a-z][a-z-]*=${VALUE})+$`);

/* ---------- working directories ---------- */

const base = fwd(realpathSync.native(mkdtempSync(join(tmpdir(), "vespera-counts-"))));
after(() => rmSync(base, { recursive: true, force: true, maxRetries: 3 }));

function put(path, text = "") {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, text);
}

// A working directory holding a vespera.db made from schema.sql, filled by fill, and the files given.
function workingDirectory(name, fill = () => {}, { files = {}, wal = false } = {}) {
  const dir = `${base}/${name}`;
  mkdirSync(dir, { recursive: true });
  const db = new DatabaseSync(`${dir}/vespera.db`);
  db.exec(SCHEMA);
  if (wal) db.exec("PRAGMA journal_mode=WAL");
  fill(db);
  db.close();
  for (const [file, text] of Object.entries(files)) put(`${dir}/${file}`, text);
  return dir;
}

const runId = (digit) => digit.repeat(64);
const R1 = runId("1");
const R1_SIBLING = runId("2");
const R2 = runId("3");
const R3 = runId("4");
const RA = runId("5");
const RG = runId("6");
const RX = runId("7");

// Two walks; seven runs, six of them a chain of upstream runs over walk 1 and one beside it; and every
// table the script counts, with MARKER in every text and path column and in every value no list holds.
function fillLedger(db, { unshapedRun = false } = {}) {
  const run = (sql, ...args) => db.prepare(sql).run(...args);
  run("INSERT INTO walk (id, root, finished, checkpoint_path, entries_seen, directories_entered) VALUES (1, ?, 1, ?, 7, 2)", `${MARKER}/archive`, `${MARKER}/checkpoint`);
  run("INSERT INTO walk (id, root, finished, entries_seen, directories_entered) VALUES (2, ?, 0, 1, 1)", `${MARKER}/seeds`);
  const occurrence = (id, walk, size) =>
    run("INSERT INTO file_occurrence (id, walk_id, path, size_bytes, last_modified, creation_time) VALUES (?, ?, ?, ?, ?, ?)", id, walk, `${MARKER}/document ${id}.pdf`, size, `${MARKER} time`, `${MARKER} time`);
  occurrence(1, 1, 100);
  occurrence(2, 1, 200);
  occurrence(3, 1, 300);
  occurrence(4, 1, 400);
  occurrence(5, 2, 50);
  const anomaly = (kind) => run("INSERT INTO walk_anomaly (walk_id, path_rendering, kind, detail) VALUES (1, ?, ?, ?)", `${MARKER}/entry`, kind, `${MARKER} detail`);
  anomaly("UNPROCESSABLE");
  anomaly("UNPROCESSABLE");
  anomaly("SOFT_LINK_NOT_FOLLOWED");
  anomaly(`${MARKER}_KIND`);
  const aRun = (id, stage, walk, upstream) => {
    run("INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id) VALUES (?, ?, ?, ?, ?)", id, stage, `${MARKER} version`, `seedFolder: ${MARKER}`, walk);
    if (upstream) run("INSERT INTO run_upstream (run_id, upstream_run_id) VALUES (?, ?)", id, upstream);
  };
  aRun(R1, "byte-level-reduction", 1);
  aRun(R1_SIBLING, "byte-level-reduction", 1);
  aRun(R2, "extraction", 1, R1);
  aRun(R3, "content-redundancy", 1, R2);
  aRun(RA, "arrangement", 1, R3);
  aRun(RG, "generation", 1, RA);
  aRun(RX, `${MARKER}-stage`, 2);
  const finished = (id, step) => run("INSERT INTO finished_step (run_id, step) VALUES (?, ?)", id, step);
  finished(R1, "byte-level-reduction");
  finished(R2, "extraction");
  finished(RG, "generation");
  finished(RG, `${MARKER}-step`);
  const verdict = (occurrenceId, id, kind) => run("INSERT INTO verdict (occurrence_id, run_id, kind, reason) VALUES (?, ?, ?, ?)", occurrenceId, id, kind, `${MARKER} reason`);
  verdict(1, R1, "BROKEN");
  verdict(2, R1, "PASSED");
  verdict(3, R1, "PASSED");
  verdict(4, R1, "PASSED");
  verdict(2, R1_SIBLING, "OUT_OF_SCOPE");
  verdict(3, R2, "EXTRACTION_FAILED");
  verdict(4, R2, "PASSED");
  verdict(4, R2, `${MARKER}_VERDICT`);
  verdict(4, R3, "REDUNDANT_WITH");
  const fault = (occurrenceId, category) =>
    run("INSERT INTO extraction_fault (occurrence_id, run_id, category, detail) VALUES (?, ?, ?, ?)", occurrenceId, R2, category, `${MARKER} converter message`);
  fault(1, "capacity");
  fault(3, "TIMEOUT");
  fault(4, `${MARKER}_CATEGORY`);
  const cluster = (ordinal) =>
    run("INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label, document_count, partition_order, cluster_order) VALUES (?, 5, ?, ?, 2, 1, ?)", RA, ordinal, `${MARKER} title`, ordinal);
  cluster(1);
  cluster(2);
  run("INSERT INTO synthesis_doc (run_id, winning_seed_occurrence_id, cluster_ordinal, title, prose) VALUES (?, 5, 1, ?, ?)", RG, `${MARKER} title`, `${MARKER} prose`);
  const clusterFault = (ordinal, kind) =>
    run("INSERT INTO cluster_fault (run_id, winning_seed_occurrence_id, cluster_ordinal, kind, detail) VALUES (?, 5, ?, ?, ?)", RG, ordinal, kind, `${MARKER} detail`);
  clusterFault(2, "SCHEMA_VIOLATION");
  clusterFault(3, `${MARKER}_FAULT`);
  if (unshapedRun) {
    aRun(`${MARKER}-run`, "extraction", 1, R1);
    verdict(1, `${MARKER}-run`, "BROKEN");
  }
}

// What a working directory holds beside vespera.db: a lock, two logs, two pages and a deliverable, one
// of whose partition folders and one stray folder are named with MARKER, as a seed's name would be.
const LOGS = {
  "vespera.log": `a line of the operator's log about ${MARKER}\n`,
  "vespera.2026-10-07.0.log": `an older line about ${MARKER}\n`,
};
const UNDER_THE_RUN = {
  [`deliverable/${RG}/index.md`]: `# ${MARKER}\n`,
  [`deliverable/${RG}/${MARKER} partition/cluster.md`]: `${MARKER} prose\n`,
};
const ELSEWHERE_IN_THE_DELIVERABLE = {
  [`deliverable/${MARKER} folder/x.md`]: "x\n",
  "deliverable/stray.md": "y\n",
};
const OTHER_FILES = {
  "vespera.lock": "",
  "profile.yaml": `seedFolder: ${MARKER}\n`,
  "relevance-labelling.html": `<p>the opening of ${MARKER}</p>\n`,
  [`${MARKER} page.html`]: "<p>a page</p>\n",
};
const FILES = { ...LOGS, ...UNDER_THE_RUN, ...ELSEWHERE_IN_THE_DELIVERABLE, ...OTHER_FILES };
const tally = (files) => ({ files: Object.keys(files).length, bytes: Object.values(files).reduce((sum, text) => sum + Buffer.byteLength(text), 0) });

/* ---------- starting the script ---------- */

function count(args) {
  return spawnSync(process.execPath, [SCRIPT, ...args], { encoding: "utf8", timeout: 120_000, windowsHide: true });
}

function ended(result, expected, what) {
  assert.ok(existsSync(SCRIPT), `${what}: the counting script ${SCRIPT} does not exist yet; docs/adr/0212 says what it owes`);
  assert.equal(
    result.status,
    expected,
    `${what}: the script must exit ${expected}, and it exited ${result.status}` +
      (result.error ? `; it could not be started: ${result.error.message}` : "") +
      `. stderr: ${(result.stderr || "").trim() || "(nothing)"}`,
  );
}

const linesOf = (result) => result.stdout.split(/\r?\n/).filter(Boolean);
const sizeOf = (path) => lstatSync(path).size;

// Every name under a folder, with its kind, size and modification time, links not followed.
function snapshot(dir) {
  const seen = {};
  const walk = (folder, prefix) => {
    for (const entry of readdirSync(folder, { withFileTypes: true })) {
      const path = `${folder}/${entry.name}`;
      const stat = lstatSync(path);
      seen[prefix + entry.name] = { folder: stat.isDirectory(), size: stat.size, modified: stat.mtimeMs };
      if (stat.isDirectory() && !stat.isSymbolicLink()) walk(path, `${prefix}${entry.name}/`);
    }
  };
  walk(dir, "");
  return seen;
}
const sha256 = (path) => createHash("sha256").update(readFileSync(path)).digest("hex");

/* ---------- the tests ---------- */

test("W01 counted: a working directory's counts and sums, line for line", () => {
  const wd = workingDirectory("w01", (db) => fillLedger(db), { files: FILES });
  const database = sizeOf(`${wd}/vespera.db`);
  const everything = tally(FILES);
  const logs = tally(LOGS);
  const underTheRun = tally(UNDER_THE_RUN);
  const elsewhere = tally(ELSEWHERE_IN_THE_DELIVERABLE);
  // Survivors answer to blocking verdicts under the run and every run upstream of it (ADR-156): under R1
  // only occurrence 1 is broken; R1's sibling removes occurrence 2 from itself and from no other run; R2
  // adds occurrence 3, R3 occurrence 4, and the unknown kind under R2 blocks nothing. Walk 2 has one
  // occurrence and no verdict.
  const expected = [
    "walk walk=1 finished=1 occurrences=4 bytes=1000 entries-seen=7 directories-entered=2",
    "walk walk=2 finished=0 occurrences=1 bytes=50 entries-seen=1 directories-entered=1",
    "anomalies walk=1 kind=SOFT_LINK_NOT_FOLLOWED count=1",
    "anomalies walk=1 kind=UNPROCESSABLE count=2",
    "anomalies walk=1 kind=other count=1",
    `run run=${R1} stage=byte-level-reduction walk=1 upstream=-`,
    `run run=${R1_SIBLING} stage=byte-level-reduction walk=1 upstream=-`,
    `run run=${R2} stage=extraction walk=1 upstream=${R1}`,
    `run run=${R3} stage=content-redundancy walk=1 upstream=${R2}`,
    `run run=${RA} stage=arrangement walk=1 upstream=${R3}`,
    `run run=${RG} stage=generation walk=1 upstream=${RA}`,
    `run run=${RX} stage=other walk=2 upstream=-`,
    `finished-steps run=${R1} step=byte-level-reduction count=1`,
    `finished-steps run=${R2} step=extraction count=1`,
    `finished-steps run=${RG} step=generation count=1`,
    `finished-steps run=${RG} step=other count=1`,
    `verdicts run=${R1} kind=BROKEN count=1`,
    `verdicts run=${R1} kind=PASSED count=3`,
    `verdicts run=${R1_SIBLING} kind=OUT_OF_SCOPE count=1`,
    `verdicts run=${R2} kind=EXTRACTION_FAILED count=1`,
    `verdicts run=${R2} kind=PASSED count=1`,
    `verdicts run=${R2} kind=other count=1`,
    `verdicts run=${R3} kind=REDUNDANT_WITH count=1`,
    `survivors run=${R1} count=3`,
    `survivors run=${R1_SIBLING} count=3`,
    `survivors run=${R2} count=2`,
    `survivors run=${R3} count=1`,
    `survivors run=${RA} count=1`,
    `survivors run=${RG} count=1`,
    `survivors run=${RX} count=1`,
    `extraction-faults run=${R2} category=CAPACITY count=1`,
    `extraction-faults run=${R2} category=TIMEOUT count=1`,
    `extraction-faults run=${R2} category=other count=1`,
    `clusters run=${RA} count=2`,
    `synthesis-docs run=${RG} count=1`,
    `cluster-faults run=${RG} kind=SCHEMA_VIOLATION count=1`,
    `cluster-faults run=${RG} kind=other count=1`,
    `files folder=working-directory files=${everything.files + 1} bytes=${everything.bytes + database}`,
    `files folder=database files=1 bytes=${database}`,
    `files folder=log files=${logs.files} bytes=${logs.bytes}`,
    `files folder=deliverable files=${underTheRun.files + elsewhere.files} bytes=${underTheRun.bytes + elsewhere.bytes}`,
    `files folder=deliverable/${RG} files=${underTheRun.files} bytes=${underTheRun.bytes}`,
    `files folder=deliverable/other files=${elsewhere.files} bytes=${elsewhere.bytes}`,
  ];
  const result = count([wd]);
  ended(result, COUNTED, "a fixture working directory");
  assert.deepEqual(linesOf(result), expected, "the script must print exactly the counts and sums of the fixture, in the order the record gives");
  assert.equal(result.stderr, "", "on success the script must write nothing to stderr");
});

test("W02 counted: every name of the code's closed lists is printed as itself and not as other", () => {
  const wd = workingDirectory("w02", (db) => {
    const run = (sql, ...args) => db.prepare(sql).run(...args);
    run("INSERT INTO walk (id, root) VALUES (1, 'x')");
    const occurrences = Math.max(FAILURE_CATEGORIES.length, 1);
    for (let id = 1; id <= occurrences; id++) {
      run("INSERT INTO file_occurrence (id, walk_id, path, size_bytes, last_modified, creation_time) VALUES (?, 1, ?, 1, 't', 't')", id, `p${id}`);
    }
    STAGES.forEach((stage, i) => run("INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id) VALUES (?, ?, 'v', 'c', 1)", String(i + 1).padStart(64, "0"), stage));
    const first = "1".padStart(64, "0");
    for (const kind of ANOMALY_KINDS) run("INSERT INTO walk_anomaly (walk_id, path_rendering, kind) VALUES (1, 'e', ?)", kind);
    for (const step of STEPS) run("INSERT INTO finished_step (run_id, step) VALUES (?, ?)", first, step);
    for (const kind of VERDICT_KINDS) run("INSERT INTO verdict (occurrence_id, run_id, kind) VALUES (1, ?, ?)", first, kind);
    FAILURE_CATEGORIES.forEach((category, i) => run("INSERT INTO extraction_fault (occurrence_id, run_id, category, detail) VALUES (?, ?, ?, 'd')", i + 1, first, category));
    CLUSTER_FAULT_KINDS.forEach((kind, i) => run("INSERT INTO cluster_fault (run_id, winning_seed_occurrence_id, cluster_ordinal, kind, detail) VALUES (?, 1, ?, ?, 'd')", first, i + 1, kind));
  });
  const first = "1".padStart(64, "0");
  const result = count([wd]);
  ended(result, COUNTED, "a working directory holding every name of the closed lists");
  const lines = new Set(linesOf(result));
  const owed = [
    ...STAGES.map((stage, i) => `run run=${String(i + 1).padStart(64, "0")} stage=${stage} walk=1 upstream=-`),
    ...ANOMALY_KINDS.map((kind) => `anomalies walk=1 kind=${kind} count=1`),
    ...STEPS.map((step) => `finished-steps run=${first} step=${step} count=1`),
    ...VERDICT_KINDS.map((kind) => `verdicts run=${first} kind=${kind} count=1`),
    ...FAILURE_CATEGORIES.map((category) => `extraction-faults run=${first} category=${category} count=1`),
    ...CLUSTER_FAULT_KINDS.map((kind) => `cluster-faults run=${first} kind=${kind} count=1`),
  ];
  const missing = owed.filter((line) => !lines.has(line));
  assert.deepEqual(missing, [], "every constant the code declares must be printed by its own name, so a name added to the code and not to the script is caught here");
  assert.ok(!result.stdout.includes("=other"), `nothing in this fixture is outside the lists, and the script printed other: ${result.stdout}`);
});

test("W03 counted: nothing of the operator's reaches the output, and every line has the record's shape", () => {
  const wd = workingDirectory("w03", (db) => fillLedger(db, { unshapedRun: true }), { files: FILES });
  const result = count([wd]);
  ended(result, COUNTED, "a fixture whose every text, path and stray name carries the marker");
  assert.ok(!result.stdout.includes(MARKER), `the marker reached stdout: ${result.stdout}`);
  assert.ok(!result.stderr.includes(MARKER), `the marker reached stderr: ${result.stderr}`);
  assert.ok(!result.stdout.toLowerCase().includes(base.toLowerCase()), "the working directory's own path reached stdout");
  const misshapen = linesOf(result).filter((line) => !LINE.test(line));
  assert.deepEqual(misshapen, [], "every line must be a record name and key=value pairs whose values are numbers, run ids, the code's own names, other, - or a fixed folder name");
  assert.ok(linesOf(result).some((line) => line.startsWith("run run=other stage=extraction ")), "a run whose stored id is not a run id's shape must be printed as other");
});

test("W04 counted: vespera.db and every name, size and time in the working directory are as they were", () => {
  const wd = workingDirectory("w04", (db) => fillLedger(db), { files: FILES });
  const before = snapshot(wd);
  const hash = sha256(`${wd}/vespera.db`);
  ended(count([wd]), COUNTED, "a fixture in the default journal mode");
  assert.equal(sha256(`${wd}/vespera.db`), hash, "vespera.db must not change by a byte");
  assert.deepEqual(snapshot(wd), before, "the script must add, remove, grow or touch nothing in the working directory");
});

test("W05 counted: a WAL database at rest is counted and nothing is added beside it", () => {
  const wd = workingDirectory("w05", (db) => db.prepare("INSERT INTO walk (id, root) VALUES (1, 'x')").run(), { wal: true });
  assert.deepEqual(readdirSync(wd), ["vespera.db"], "the fixture: a WAL database whose last connection has closed holds no write-ahead log beside it");
  const result = count([wd]);
  ended(result, COUNTED, "a WAL database at rest");
  assert.ok(linesOf(result).includes("walk walk=1 finished=0 occurrences=0 bytes=0 entries-seen=0 directories-entered=0"), `the walk must be counted: ${result.stdout}`);
  assert.deepEqual(readdirSync(wd), ["vespera.db"], "opening a database at rest must leave no vespera.db-wal or vespera.db-shm behind");
});

test("W06 counted: a commit still in the write-ahead log is counted, and the writer goes on writing", () => {
  const wd = workingDirectory("w06", (db) => db.prepare("INSERT INTO walk (id, root) VALUES (1, 'x')").run(), { wal: true });
  const writer = new DatabaseSync(`${wd}/vespera.db`);
  try {
    writer.exec("PRAGMA wal_autocheckpoint=0");
    writer.prepare("INSERT INTO walk (id, root) VALUES (2, 'y')").run();
    assert.ok(existsSync(`${wd}/vespera.db-wal`), "the fixture: the writer's commit is in the write-ahead log");
    const result = count([wd]);
    ended(result, COUNTED, "a WAL database a writer holds open");
    assert.ok(linesOf(result).includes("walk walk=2 finished=0 occurrences=0 bytes=0 entries-seen=0 directories-entered=0"), `the walk committed into the write-ahead log must be counted: ${result.stdout}`);
    writer.prepare("INSERT INTO walk (id, root) VALUES (3, 'z')").run();
    assert.equal(writer.prepare("SELECT COUNT(*) AS n FROM walk").get().n, 3, "the writer must still be able to commit after the script has read");
  } finally {
    writer.close();
  }
});

test("W07 refused: anything but one folder that directly holds vespera.db", () => {
  const wd = workingDirectory("w07");
  const empty = `${base}/w07-empty`;
  mkdirSync(empty, { recursive: true });
  const locked = `${base}/w07-locked`;
  put(`${locked}/vespera.lock`);
  for (const [what, args] of [
    ["no argument", []],
    ["two arguments", [wd, wd]],
    ["a folder holding no vespera.db", [empty]],
    ["vespera.db itself", [`${wd}/vespera.db`]],
    ["a folder holding vespera.lock and no vespera.db", [locked]],
  ]) {
    const result = count(args);
    ended(result, REFUSED, what);
    assert.equal(result.stdout, "", `${what}: nothing may be printed on stdout`);
    assert.ok(!result.stderr.toLowerCase().includes(base.toLowerCase()), `${what}: stderr must name no path, and it said: ${result.stderr}`);
  }
});

test("W08 unreadable: a vespera.db that is not a database prints nothing, and says why without its words", () => {
  const dir = `${base}/w08`;
  put(`${dir}/vespera.db`, `${MARKER} is not a database\n`.repeat(200));
  const result = count([dir]);
  ended(result, UNREADABLE, "a vespera.db holding text");
  assert.equal(result.stdout, "", "nothing may be printed on stdout when a statement fails");
  assert.ok(!result.stderr.includes(MARKER), `stderr carries what the file holds: ${result.stderr}`);
  assert.ok(!result.stderr.toLowerCase().includes(base.toLowerCase()), `stderr names a path: ${result.stderr}`);
});

// A junction needs no privilege on Windows; a symlink needs none elsewhere.
const FOLLOWED = `${base}/w09-followed`;
for (const name of ["a.txt", "b.txt", "c.txt"]) put(`${FOLLOWED}/${name}`, `${MARKER}\n`);
const W09 = workingDirectory("w09");
let linked = true;
try {
  symlinkSync(windows ? FOLLOWED.replace(/\//g, "\\") : FOLLOWED, windows ? `${W09}/link`.replace(/\//g, "\\") : `${W09}/link`, windows ? "junction" : "dir");
  linked = existsSync(`${W09}/link/a.txt`);
} catch {
  linked = false;
}

test("W09 counted: a link in the working directory is neither followed nor counted", { skip: linked ? false : "not started on this platform: it would not create a junction or a symlink that leads where it should" }, () => {
  const database = sizeOf(`${W09}/vespera.db`);
  const result = count([W09]);
  ended(result, COUNTED, "a working directory holding a link to a folder of three files");
  assert.ok(linesOf(result).includes(`files folder=working-directory files=1 bytes=${database}`), `only vespera.db may be counted: ${result.stdout}`);
});
