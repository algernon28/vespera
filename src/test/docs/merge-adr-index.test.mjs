// docs/merge-adr-index.mjs resolves a merge's conflicts in the ADR index and in Adr.java, run from the repository's root
// with no argument, reading both files itself (ADR-363 §3). Two tests build a git repository in a temporary folder and
// merge main into a branch, so that the conflicts are the ones git writes, under each of its three conflict styles; the
// rest write a conflicted file by hand, outside any git repository, since the script runs no git command. The index of
// every test has two tables, as docs/adr/README.md has, its records' rows in the second.
//
//   node --test src/test/docs/merge-adr-index.test.mjs

import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { ADR_JAVA, INDEX, RECONSTITUTED, adrJavaOf, constant, indexOf, row } from "./adr-index-fixtures.mjs";

const SCRIPT = fileURLToPath(new URL("../../../docs/merge-adr-index.mjs", import.meta.url));

/** The records both sides of every merge here start from. */
const BASE = [356, 357];

const write = (dir, path, text) => {
  mkdirSync(dirname(join(dir, path)), { recursive: true });
  writeFileSync(join(dir, path), text);
};
const read = (dir, path) => readFileSync(join(dir, path), "utf8");
const folder = () => mkdtempSync(join(tmpdir(), "merge-adr-index-"));

/** The script, run with no argument from `dir` as its working directory. */
function resolve(dir) {
  assert.ok(existsSync(SCRIPT), "docs/merge-adr-index.mjs is missing: ADR-363 §3 adds it");
  return spawnSync(process.execPath, [SCRIPT], { cwd: dir, encoding: "utf8" });
}

/* ---------- a conflict written by hand ---------- */

/** A conflict as git's default style writes it, the branch's side first. */
const conflict = (ours, theirs) => ["<<<<<<< HEAD", ...ours, "=======", ...theirs, ">>>>>>> main"];
/** A text's lines, its last line ending dropped. */
const lines = (text) => text.split("\n").slice(0, -1);

/**
 * The index of the records `base` in its second table and `reconstituted` in its first, then, at the end of the second, a
 * conflict between the rows `ours` and `theirs`, each a row's whole text.
 */
const conflictedIndex = (base, ours, theirs, eol = "\n", reconstituted = RECONSTITUTED) =>
  [...lines(indexOf(base, "\n", reconstituted)), ...conflict(ours, theirs)].join(eol) + eol;

/** Adr.java of the records `base`, then a conflict between the constants of the records `ours` and `theirs`. */
function conflictedAdrJava(base, ours, theirs) {
  const head = lines(adrJavaOf(base));
  const tail = head.splice(-3); // private Adr() {, its closing brace and the class's
  const side = (records) => records.flatMap((n) => [...constant(n), ""]);
  return [...head, ...conflict(side(ours), side(theirs)), ...tail].join("\n") + "\n";
}

/* ---------- a conflict written by git ---------- */

let gitEnvironment;
/**
 * git's environment for a repository of a test's own: no system or global configuration, so that neither a machine's
 * core.autocrlf nor its merge.conflictStyle changes what git writes, and an author and a committer of the test's.
 */
function gitEnv() {
  if (!gitEnvironment) {
    const global = join(folder(), "gitconfig");
    writeFileSync(global, "");
    gitEnvironment = {
      ...process.env,
      GIT_CONFIG_NOSYSTEM: "1",
      GIT_CONFIG_GLOBAL: global,
      GIT_AUTHOR_NAME: "A test",
      GIT_AUTHOR_EMAIL: "test@example.com",
      GIT_COMMITTER_NAME: "A test",
      GIT_COMMITTER_EMAIL: "test@example.com",
    };
    for (const name of ["GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY", "GIT_COMMON_DIR"]) delete gitEnvironment[name];
  }
  return gitEnvironment;
}

function git(dir, args, { mayFail = false } = {}) {
  const r = spawnSync("git", args, { cwd: dir, encoding: "utf8", env: gitEnv() });
  if (!mayFail && r.status !== 0) throw new Error(`git ${args.join(" ")} exited ${r.status}: ${r.stderr}`);
  return r;
}

/**
 * A repository whose branch `branch` adds the records `branch` to BASE and whose main adds the records `main`, each to
 * both files, with main merged into the branch under the conflict style given; the merge is left standing.
 */
function mergeOfMain(style, { main, branch }) {
  const dir = folder();
  git(dir, ["init", "-q", "-b", "main"]);
  const commit = (records, message) => {
    write(dir, INDEX, indexOf(records));
    write(dir, ADR_JAVA, adrJavaOf(records));
    git(dir, ["add", "-A"]);
    git(dir, ["commit", "-q", "-m", message]);
  };
  commit(BASE, "the records both sides start from");
  git(dir, ["checkout", "-q", "-b", "branch"]);
  commit([...BASE, ...branch], "the branch's record");
  git(dir, ["checkout", "-q", "main"]);
  commit([...BASE, ...main], "main's records");
  git(dir, ["checkout", "-q", "branch"]);
  const merge = git(dir, ["-c", `merge.conflictStyle=${style}`, "merge", "--no-edit", "main"], { mayFail: true });
  assert.notEqual(merge.status, 0, `the fixture's merge stops on a conflict: ${merge.stdout}`);
  for (const path of [INDEX, ADR_JAVA]) assert.match(read(dir, path), /^<{7} /m, `git left a conflict in ${path}`);
  return dir;
}

/* ---------- what the script resolves ---------- */

for (const style of ["merge", "diff3", "zdiff3"]) {
  test(`under git's conflict style ${style}, a branch adding ADR-361 merged with main that gained ADR-360 resolves both files to ADR-360 then ADR-361, every marker gone, and exits 0 naming both`, () => {
    const dir = mergeOfMain(style, { main: [360], branch: [361] });
    const run = resolve(dir);
    assert.equal(run.status, 0, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
    assert.equal(read(dir, INDEX), indexOf([...BASE, 360, 361]));
    assert.equal(read(dir, ADR_JAVA), adrJavaOf([...BASE, 360, 361]));
    for (const path of [INDEX, ADR_JAVA]) assert.ok(run.stdout.includes(path), `the output names ${path}: ${run.stdout}`);
  });
}

test("a branch adding ADR-359 merged with main that gained ADR-360 and ADR-361 puts the branch's record first, in order of number", () => {
  const dir = mergeOfMain("merge", { main: [360, 361], branch: [359] });
  const run = resolve(dir);
  assert.equal(run.status, 0, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.equal(read(dir, INDEX), indexOf([...BASE, 359, 360, 361]));
  assert.equal(read(dir, ADR_JAVA), adrJavaOf([...BASE, 359, 360, 361]));
});

test("outside any git repository, an index with CRLF line endings is resolved with CRLF line endings, and an Adr.java holding no conflict is left byte for byte", () => {
  const dir = folder();
  write(dir, INDEX, conflictedIndex(BASE, [row(361)], [row(360)], "\r\n"));
  const adrJava = adrJavaOf([...BASE, 360], "\r\n");
  write(dir, ADR_JAVA, adrJava);
  const run = resolve(dir);
  assert.equal(run.status, 0, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.equal(read(dir, INDEX), indexOf([...BASE, 360, 361], "\r\n"));
  assert.equal(read(dir, ADR_JAVA), adrJava);
});

test("where neither file holds a conflict, both are left byte for byte and the script exits 0", () => {
  const dir = folder();
  const index = indexOf([...BASE, 360]);
  const adrJava = adrJavaOf([...BASE, 360]);
  write(dir, INDEX, index);
  write(dir, ADR_JAVA, adrJava);
  const run = resolve(dir);
  assert.equal(run.status, 0, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.equal(read(dir, INDEX), index);
  assert.equal(read(dir, ADR_JAVA), adrJava);
});

/* ---------- which table a merged row goes to ---------- */

test("rows merged at the end of the index's second table stay in it, the first table and the heading, paragraph and table head above the second left where they stand", () => {
  const dir = folder();
  write(dir, INDEX, conflictedIndex(BASE, [row(361)], [row(360)]));
  write(dir, ADR_JAVA, conflictedAdrJava(BASE, [361], [360]));
  const run = resolve(dir);
  assert.equal(run.status, 0, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.equal(read(dir, INDEX), indexOf([...BASE, 360, 361]));
  assert.equal(read(dir, ADR_JAVA), adrJavaOf([...BASE, 360, 361]));
});

test("a merged row numbered below the first row of every table goes to the first table, ahead of its rows, and the other side's row stays in the second", () => {
  const dir = folder();
  // The first table holds ADR-010 and ADR-011, the second BASE; the branch's side adds ADR-005 at the end of the second.
  write(dir, INDEX, conflictedIndex(BASE, [row(5)], [row(360)], "\n", [10, 11]));
  write(dir, ADR_JAVA, adrJavaOf(BASE));
  const run = resolve(dir);
  assert.equal(run.status, 0, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.equal(read(dir, INDEX), indexOf([...BASE, 360], "\n", [5, 10, 11]));
  assert.equal(read(dir, ADR_JAVA), adrJavaOf(BASE));
});

/* ---------- what it refuses ---------- */

test("a row one side holds where the other holds no row, its rows standing in different places though the rest of both sides agrees, is refused with exit 1 naming the index, and neither file is written", () => {
  const dir = folder();
  const index = [
    "# Decision records",
    "",
    ...conflict([], [row(362)]),
    "",
    ...lines(indexOf(BASE)).slice(2),
    "",
  ].join("\n");
  const adrJava = conflictedAdrJava(BASE, [361], [360]);
  write(dir, INDEX, index);
  write(dir, ADR_JAVA, adrJava);
  const run = resolve(dir);
  assert.equal(run.status, 1, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.ok(run.stderr.includes(INDEX), `standard error names ${INDEX}: ${run.stderr}`);
  assert.equal(read(dir, INDEX), index);
  assert.equal(read(dir, ADR_JAVA), adrJava);
});

test("a conflict marker outside any conflict, a lone >>>>>>> left by a merge half resolved by hand, is refused with exit 1 naming the index, and neither file is written, Adr.java's own conflict left as git left it", () => {
  const dir = folder();
  const index = indexOf([...BASE, 360, ">>>>>>> main", 361]);
  const adrJava = conflictedAdrJava(BASE, [361], [360]);
  write(dir, INDEX, index);
  write(dir, ADR_JAVA, adrJava);
  const run = resolve(dir);
  assert.equal(run.status, 1, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.ok(run.stderr.includes(INDEX), `standard error names ${INDEX}: ${run.stderr}`);
  assert.equal(read(dir, INDEX), index);
  assert.equal(read(dir, ADR_JAVA), adrJava);
});

test("a conflict outside the rows is refused with exit 1 naming the index, and neither file is written, Adr.java's own conflict left as git left it", () => {
  const dir = folder();
  const index = [
    "# Decision records",
    "",
    ...conflict(["One file per decision, as the branch words it."], ["One file per decision, as main words it."]),
    "",
    "| ID | Date | Title |",
    "| --- | --- | --- |",
    ...BASE.map((n) => row(n)),
    "",
  ].join("\n");
  const adrJava = conflictedAdrJava(BASE, [361], [360]);
  write(dir, INDEX, index);
  write(dir, ADR_JAVA, adrJava);
  const run = resolve(dir);
  assert.equal(run.status, 1, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.ok(run.stderr.includes(INDEX), `standard error names ${INDEX}: ${run.stderr}`);
  assert.equal(read(dir, INDEX), index);
  assert.equal(read(dir, ADR_JAVA), adrJava);
});

test("a record whose row the two sides hold with different text is refused with exit 1 naming the index and the record, and neither file is written", () => {
  const dir = folder();
  const index = conflictedIndex(
    [356],
    [row(357, "The record numbered 357, as the branch words it"), row(361)],
    [row(357, "The record numbered 357, as main words it"), row(360)],
  );
  const adrJava = conflictedAdrJava(BASE, [361], [360]);
  write(dir, INDEX, index);
  write(dir, ADR_JAVA, adrJava);
  const run = resolve(dir);
  assert.equal(run.status, 1, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.ok(run.stderr.includes(INDEX), `standard error names ${INDEX}: ${run.stderr}`);
  assert.match(run.stderr, /\bADR-357\b/, "standard error names the record the two sides word differently");
  assert.equal(read(dir, INDEX), index);
  assert.equal(read(dir, ADR_JAVA), adrJava);
});

test("run where Adr.java is not, the script exits 2 naming its path and writes nothing, the index's conflict left as git left it", () => {
  const dir = folder();
  const index = conflictedIndex(BASE, [row(361)], [row(360)]);
  write(dir, INDEX, index);
  const run = resolve(dir);
  assert.equal(run.status, 2, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.ok(run.stderr.includes(ADR_JAVA), `standard error names ${ADR_JAVA}: ${run.stderr}`);
  assert.equal(read(dir, INDEX), index);
  assert.equal(existsSync(join(dir, ADR_JAVA)), false, "nothing is written where Adr.java is not");
});
