// docs/check-claims-lib.mjs holds what the claims check and docs/next-adr-number.mjs compute without reading a file or
// asking the network (ADR-346, ADR-350): AGENTS.md's size as git stores it, and a decision record's number, read,
// shared, taken and chosen, the open pull requests read in full or cut short, and the claim of the numbers this branch
// adds, which never passes where it did not read, and the exit of docs/next-adr-number.mjs; whether the claim of the
// current wayfinder map reads the tracker on an event (readsTrackerState); and the length of a record's slug
// (overlongSlugs, slugClaim). One test asks git whether it stores AGENTS.md as text and whether HEAD's blob of it
// holds a CR. Eight run the scripts themselves and read their lines. Four read one run of docs/check-claims.mjs
// without --with-network (see claims()): AGENTS.md's size, the shared numbers, the numbers this branch adds NOT
// CHECKED, and the slug claim PASS, naming how many records from ADR-350 on it read. Four run a script in an
// environment that asks no network, where the fetch of origin/main fails (see offline()): docs/check-claims.mjs
// --with-network for the numbers this branch adds NOT CHECKED, for the map NOT CHECKED on a pull request, and for the
// map reading the tracker on a push and with no event; and docs/next-adr-number.mjs for its exit. The order of the
// rows of docs/adr/README.md and of the constants of Adr.java (ADR-363 §2) is held on fixtures by adrOrderClaim, and on
// this tree by the claim's line, against the rows and constants the test counts itself. The suite's ceiling of 700
// methods annotated as tests (suiteCeilingClaim), and the reading of docs/retired-tests.txt against the names the
// records' Tests sections yield as missing (retiredNames, reconcileRetired), are held on fixtures (ADR-368).
//
//   node --test src/test/docs/check-claims.test.mjs

import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { copyFileSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, extname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { ADR_JAVA, INDEX, adrJavaOf, constant, indexOf } from "./adr-index-fixtures.mjs";

const ROOT = fileURLToPath(new URL("../../../", import.meta.url));
// Imported in each test, so that each one reports its own failure while the module is missing.
const lib = () => import(new URL("../../../docs/check-claims-lib.mjs", import.meta.url).href);

// A pull request as `gh pr list --json number,headRefName,changedFiles,files` gives it, read in full unless changedFiles
// is given above the files' count.
const PR = (number, headRefName, ...files) => {
  const listed = files.map((f) => (typeof f === "string" ? { path: f, changeType: "ADDED" } : f));
  return { number, headRefName, changedFiles: listed.length, files: listed };
};
// A pull request gh cut short: 100 files listed of the 175 it changes, as #676 was.
const CUT = (number, headRefName) => ({
  number,
  headRefName,
  changedFiles: 175,
  files: Array.from({ length: 100 }, (_, i) => ({ path: `src/main/java/F${i}.java`, changeType: "MODIFIED" })),
});

/* ---------- AGENTS.md's size ---------- */

test("a CRLF copy of a file within the ceiling is within it: each CRLF counts as the one LF git stores", async () => {
  const { agentsMdSize } = await lib();
  // 300 lines of 99 letters: 30,000 bytes with LF endings, 30,300 with CRLF, against the ceiling of 30,000.
  const crlf = ("x".repeat(99) + "\r\n").repeat(300);
  assert.deepEqual(agentsMdSize(crlf), { size: 30000, within: true });
});

test("a file over the ceiling as git stores it is over it, whatever its line endings", async () => {
  const { agentsMdSize } = await lib();
  // The 30,000 bytes above and one more letter: 30,001 bytes stored, one over the ceiling.
  const lf = ("x".repeat(99) + "\n").repeat(300) + "x";
  assert.deepEqual(agentsMdSize(lf), { size: 30001, within: false });
  assert.deepEqual(agentsMdSize(lf.replaceAll("\n", "\r\n")), { size: 30001, within: false });
});

test("the size is in UTF-8 bytes, and a CR standing alone is counted as it stands", async () => {
  const { agentsMdSize } = await lib();
  // é is two bytes, the CRLF one, b one, the lone CR one: five.
  assert.deepEqual(agentsMdSize("é\r\nb\r"), { size: 5, within: true });
});

test("git stores AGENTS.md as text, so its blob has LF endings and counting each CRLF as one byte is its stored size", () => {
  const attr = execFileSync("git", ["check-attr", "text", "--", "AGENTS.md"], { cwd: ROOT, encoding: "utf8" }).trim();
  assert.equal(attr, "AGENTS.md: text: set", "the .gitattributes line /AGENTS.md text is what makes ADR-346 §1's measure exact");
  // The attribute binds a commit made through git's filters; a blob written past them can still hold a CR, which §1's
  // measure would count one byte short. HEAD's blob is what was committed, however it was written.
  const blob = execFileSync("git", ["cat-file", "blob", "HEAD:AGENTS.md"], { cwd: ROOT });
  assert.equal(blob.indexOf(0x0d), -1, `HEAD's blob of AGENTS.md holds a CR at byte ${blob.indexOf(0x0d)}: it was committed past git's text filter`);
});

/* ---------- line endings (ADR-366) ---------- */

// The five kinds of file ADR-366 §1 names, each with a name no tracked file has, so that check-attr reads the rule alone.
const LF_KINDS = ["src/main/java/New.java", "docs/New.md", "docs/new.mjs", ".github/workflows/new.yml", "docs/new.css"];

test("git stores a new .java, .md, .mjs, .yml or .css file as text and writes it to the working copy with LF endings", () => {
  const out = execFileSync("git", ["check-attr", "text", "eol", "--", ...LF_KINDS], { cwd: ROOT, encoding: "utf8" });
  const expected = LF_KINDS.flatMap((p) => [`${p}: text: set`, `${p}: eol: lf`]);
  assert.deepEqual(out.trim().split(/\r?\n/), expected, "each kind ADR-366 §1 names is `text eol=lf` in .gitattributes");
});

test("a new LF file of each of the five kinds is staged with no line-ending warning in a clone set to core.autocrlf=true", () => {
  // A repository of its own, holding this tree's .gitattributes and set as git's Windows default sets a clone, so that
  // the warning is what git would print on a Windows checkout of this repository on any machine the test runs on.
  // core.safecrlf=warn is git's default and what prints the warning; set here, so that a machine's global config
  // turning it off cannot make the test pass by silencing it.
  const dir = mkdtempSync(join(tmpdir(), "vespera-eol-"));
  try {
    const git = (...args) => spawnSync("git", args, { cwd: dir, encoding: "utf8" });
    for (const args of [["init", "-q"], ["config", "core.autocrlf", "true"], ["config", "core.safecrlf", "warn"]]) {
      const step = git(...args);
      assert.equal(step.status, 0, `git ${args.join(" ")} failed: ${step.stderr}`);
    }
    copyFileSync(join(ROOT, ".gitattributes"), join(dir, ".gitattributes"));
    for (const p of LF_KINDS) {
      mkdirSync(join(dir, dirname(p)), { recursive: true });
      writeFileSync(join(dir, p), "first line\nsecond line\n");
    }
    const add = git("add", "--", ...LF_KINDS);
    assert.equal(add.status, 0, `git add failed: ${add.stderr}`);
    assert.equal(add.stderr.split(/\r?\n/).filter((l) => l.includes("will be replaced by")).join("\n"), "",
      "a new file written with LF endings is staged without git warning that it will rewrite them");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("every tracked file of the five kinds is stored with LF endings, so renormalising the tree changes no blob", () => {
  const kinds = LF_KINDS.map((p) => extname(p));
  const rows = execFileSync("git", ["ls-files", "--eol"], { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 })
    .split(/\r?\n/).filter(Boolean)
    .map((row) => ({ index: row.split(/\s+/)[0], path: row.split("\t").pop() }))
    .filter((r) => kinds.some((k) => r.path.endsWith(k)));
  assert.ok(rows.length > 1000, `the tree's files of the five kinds are read: ${rows.length}`);
  assert.deepEqual(rows.filter((r) => r.index !== "i/lf" && r.index !== "i/none").map((r) => `${r.index} ${r.path}`), [],
    "no stored file of the five kinds holds a CRLF or a lone CR, so `git add --renormalize .` would change none");
});

/* ---------- a record's number ---------- */

test("a number is read only from a file NNNN-<title>.md directly under docs/adr", async () => {
  const { adrNumber } = await lib();
  const paths = [
    "docs/adr/0342-a-title.md",
    "docs/adr/0050-x.md",
    "docs/adr/README.md",
    "docs/adr/0342.md",
    "docs/adr/342-a-title.md",
    "docs/adr/0342-a-title.html",
    "docs/adr/drafts/0342-a-title.md",
    "docs/0342-a-title.md",
  ];
  assert.deepEqual(paths.map(adrNumber), [342, 50, null, null, null, null, null, null]);
});

test("two different files of one number are shared, one file listed twice is not, and the index is no record", async () => {
  const { sharedNumbers } = await lib();
  const tree = [
    "docs/adr/0341-a.md",
    "docs/adr/0342-b.md",
    "docs/adr/0342-a.md",
    "docs/adr/0343-c.md",
    "docs/adr/0343-c.md",
    "docs/adr/README.md",
  ];
  assert.deepEqual(sharedNumbers(tree), [{ number: 342, files: ["docs/adr/0342-a.md", "docs/adr/0342-b.md"] }]);
  assert.deepEqual(sharedNumbers(["docs/adr/0341-a.md", "docs/adr/0342-b.md"]), []);
});

/* ---------- the number a new record takes ---------- */

// origin/main's records in these tests: 298, 301 and 341, so 299, 300 and 302 to 340 are gaps under its highest.
const MAIN = ["docs/adr/0298-a.md", "docs/adr/0301-b.md", "docs/adr/0341-c.md", "docs/adr/README.md"];

test("the next number is above origin/main's highest, and a gap under it is never taken", async () => {
  const { nextAdrNumber } = await lib();
  assert.equal(nextAdrNumber({ main: MAIN, branchTree: MAIN, prs: [] }), 342);
});

test("the next number passes over what this branch and every open pull request hold, and takes the lowest left", async () => {
  const { nextAdrNumber } = await lib();
  assert.equal(nextAdrNumber({ main: MAIN, branchTree: MAIN, prs: [PR(704, "x", "docs/adr/0342-x.md")] }), 343);
  assert.equal(
    nextAdrNumber({ main: MAIN, branchTree: [...MAIN, "docs/adr/0342-mine.md"], prs: [PR(704, "x", "docs/adr/0343-x.md")] }),
    344,
  );
  // A copy is a new path, and takes its number as an added file does.
  assert.equal(
    nextAdrNumber({ main: MAIN, branchTree: MAIN, prs: [PR(704, "x", { path: "docs/adr/0342-x.md", changeType: "COPIED" })] }),
    343,
  );
  // An open pull request that skipped 342 leaves it free.
  assert.equal(nextAdrNumber({ main: MAIN, branchTree: MAIN, prs: [PR(704, "x", "docs/adr/0344-x.md")] }), 342);
});

/* ---------- a number this branch takes that another holds ---------- */

test("a record this branch adds collides with one origin/main holds under another name", async () => {
  const { adrCollisions } = await lib();
  const found = adrCollisions({
    added: ["docs/adr/0342-mine.md"],
    main: [...MAIN, "docs/adr/0342-theirs.md"],
    prs: [],
    branchName: "mine",
  });
  assert.deepEqual(found, [{ number: 342, added: "docs/adr/0342-mine.md", other: "docs/adr/0342-theirs.md", where: "origin/main" }]);
});

test("a record this branch adds collides with one an open pull request adds, renames to or copies to, under another name", async () => {
  const { adrCollisions } = await lib();
  const prs = [
    PR(704, "theirs", "docs/adr/0342-theirs.md"),
    PR(705, "moved", { path: "docs/adr/0343-moved.md", changeType: "RENAMED" }),
    PR(706, "copied", { path: "docs/adr/0344-copied.md", changeType: "COPIED" }),
  ];
  const found = adrCollisions({
    added: ["docs/adr/0342-mine.md", "docs/adr/0343-also-mine.md", "docs/adr/0344-mine-too.md"],
    main: MAIN,
    prs,
    branchName: "mine",
  });
  assert.deepEqual(found, [
    { number: 342, added: "docs/adr/0342-mine.md", other: "docs/adr/0342-theirs.md", where: "#704" },
    { number: 343, added: "docs/adr/0343-also-mine.md", other: "docs/adr/0343-moved.md", where: "#705" },
    { number: 344, added: "docs/adr/0344-mine-too.md", other: "docs/adr/0344-copied.md", where: "#706" },
  ]);
});

test("a file of the same name, this branch's own pull request, and a file a pull request only edits, changes, leaves or deletes collide with nothing", async () => {
  const { adrCollisions } = await lib();
  const found = adrCollisions({
    added: ["docs/adr/0342-mine.md", "docs/adr/README.md"],
    main: [...MAIN, "docs/adr/0342-mine.md"],
    prs: [
      PR(703, "mine", "docs/adr/0342-renamed-since-it-was-pushed.md"),
      PR(704, "same", "docs/adr/0342-mine.md", "docs/adr/README.md"),
      PR(705, "edits", { path: "docs/adr/0342-edited.md", changeType: "MODIFIED" }),
      PR(706, "deletes", { path: "docs/adr/0342-deleted.md", changeType: "DELETED" }),
      PR(707, "changes", { path: "docs/adr/0342-changed.md", changeType: "CHANGED" }),
      PR(708, "leaves", { path: "docs/adr/0342-unchanged.md", changeType: "UNCHANGED" }),
    ],
    branchName: "mine",
  });
  assert.deepEqual(found, []);
});

test("a pull request file with no changeType, or with one GitHub may add later, is read as added", async () => {
  const { adrCollisions } = await lib();
  const found = adrCollisions({
    added: ["docs/adr/0342-mine.md", "docs/adr/0343-mine.md"],
    main: MAIN,
    prs: [
      { number: 704, headRefName: "theirs", changedFiles: 1, files: [{ path: "docs/adr/0342-theirs.md" }] },
      PR(705, "new", { path: "docs/adr/0343-new.md", changeType: "SOME_TYPE_NOT_YET_KNOWN" }),
    ],
    branchName: "mine",
  });
  assert.deepEqual(found, [
    { number: 342, added: "docs/adr/0342-mine.md", other: "docs/adr/0342-theirs.md", where: "#704" },
    { number: 343, added: "docs/adr/0343-mine.md", other: "docs/adr/0343-new.md", where: "#705" },
  ]);
});

test("collisions are listed by number, origin/main's before the pull requests', and the pull requests' by number", async () => {
  const { adrCollisions } = await lib();
  const found = adrCollisions({
    added: ["docs/adr/0343-b.md", "docs/adr/0342-a.md"],
    main: [...MAIN, "docs/adr/0342-m.md"],
    prs: [PR(706, "r", "docs/adr/0343-r.md"), PR(705, "p", "docs/adr/0342-p.md"), PR(704, "q", "docs/adr/0342-q.md")],
    branchName: null,
  });
  assert.deepEqual(found.map((c) => `${c.number} ${c.where} ${c.other}`), [
    "342 origin/main docs/adr/0342-m.md",
    "342 #704 docs/adr/0342-q.md",
    "342 #705 docs/adr/0342-p.md",
    "343 #706 docs/adr/0343-r.md",
  ]);
});

/* ---------- the open pull requests, read in full or cut short ---------- */

test("a pull request whose files are fewer than its changedFiles, or that gives no changedFiles, is cut short, this branch's own aside", async () => {
  const { unreadPullRequests } = await lib();
  const noCount = { number: 702, headRefName: "no-count", files: [{ path: "a.md", changeType: "ADDED" }] };
  const prs = [CUT(705, "cut"), PR(704, "whole", "docs/adr/0342-x.md"), noCount, CUT(703, "mine")];
  assert.deepEqual(unreadPullRequests({ prs, limit: 100, branchName: "mine" }), { full: false, incomplete: [702, 705] });
});

test("a list of open pull requests as long as its --limit may have left some out", async () => {
  const { unreadPullRequests } = await lib();
  const prs = [PR(704, "a", "x.md"), PR(705, "b", "y.md")];
  assert.deepEqual(unreadPullRequests({ prs, limit: 2, branchName: "mine" }), { full: true, incomplete: [] });
  assert.deepEqual(unreadPullRequests({ prs, limit: 3, branchName: "mine" }), { full: false, incomplete: [] });
});

test("next-adr-number exits 0 only where origin/main was fetched and every open pull request read in full", async () => {
  const { nextAdrNumberExit } = await lib();
  const prs = [PR(704, "theirs", "docs/adr/0343-theirs.md")];
  assert.equal(nextAdrNumberExit({ fetched: true, prs, limit: 100, branchName: "mine" }), 0);
  // This branch's own pull request, cut short, is not read against it.
  assert.equal(nextAdrNumberExit({ fetched: true, prs: [...prs, CUT(703, "mine")], limit: 100, branchName: "mine" }), 0);
  // A failed fetch is a cause of its own: the ref as it stands may lack a record merged since.
  assert.equal(nextAdrNumberExit({ fetched: false, prs, limit: 100, branchName: "mine" }), 2);
  assert.equal(nextAdrNumberExit({ fetched: true, prs: null, limit: 100, branchName: "mine" }), 2);
  assert.equal(nextAdrNumberExit({ fetched: true, prs, limit: 1, branchName: "mine" }), 2);
  assert.equal(nextAdrNumberExit({ fetched: true, prs: [...prs, CUT(705, "cut")], limit: 100, branchName: "mine" }), 2);
});

/* ---------- the claim of the numbers this branch adds ---------- */

// Every part read in full: the branch adds 0342-mine.md, origin/main holds MAIN, and #704 adds 0343-theirs.md.
const READ = () => ({
  added: ["docs/adr/0342-mine.md"],
  main: MAIN,
  prs: [PR(704, "theirs", "docs/adr/0343-theirs.md")],
  branchName: "mine",
  limit: 100,
});

test("the claim passes where origin/main, the branch's added files and every open pull request were read in full and nothing collides", async () => {
  const { adrCollisionClaim } = await lib();
  assert.equal(adrCollisionClaim(READ()).state, "PASS");
});

test("a collision fails the claim, naming the number, both files and where the other is held", async () => {
  const { adrCollisionClaim } = await lib();
  const { state, detail } = adrCollisionClaim({ ...READ(), prs: [PR(704, "theirs", "docs/adr/0342-theirs.md")] });
  assert.equal(state, "FAIL");
  for (const named of ["342", "docs/adr/0342-mine.md", "docs/adr/0342-theirs.md", "#704"]) assert.ok(detail.includes(named), `"${detail}" names ${named}`);
});

test("a collision found in what was read fails the claim, though another part was not read", async () => {
  const { adrCollisionClaim } = await lib();
  const onMain = { ...READ(), main: [...MAIN, "docs/adr/0342-theirs.md"], prs: null };
  assert.equal(adrCollisionClaim(onMain).state, "FAIL");
  const inACutList = { ...READ(), prs: [PR(704, "theirs", "docs/adr/0342-theirs.md"), CUT(705, "cut")], limit: 2 };
  assert.equal(adrCollisionClaim(inACutList).state, "FAIL");
});

test("where the open pull requests were not read, gh absent or answering with an error, the claim is not checked", async () => {
  const { adrCollisionClaim } = await lib();
  const { state, detail } = adrCollisionClaim({ ...READ(), prs: null });
  assert.equal(state, "NOT CHECKED");
  assert.match(detail, /open pull requests/);
});

test("where origin/main cannot be resolved, the claim is not checked", async () => {
  const { adrCollisionClaim } = await lib();
  const { state, detail } = adrCollisionClaim({ ...READ(), main: null });
  assert.equal(state, "NOT CHECKED");
  assert.match(detail, /origin\/main/);
});

test("where the merge base of origin/main and HEAD cannot be resolved, as in a shallow checkout, the claim is not checked", async () => {
  const { adrCollisionClaim } = await lib();
  const { state, detail } = adrCollisionClaim({ ...READ(), added: null });
  assert.equal(state, "NOT CHECKED");
  assert.match(detail, /merge base/);
});

test("where the list of open pull requests fills its --limit, the claim is not checked and names the limit", async () => {
  const { adrCollisionClaim } = await lib();
  const { state, detail } = adrCollisionClaim({ ...READ(), prs: [PR(704, "a", "x.md"), PR(705, "b", "y.md")], limit: 2 });
  assert.equal(state, "NOT CHECKED");
  assert.match(detail, /limit/);
  assert.match(detail, /\b2\b/);
});

test("where gh cut an open pull request's files short, the claim is not checked and names the pull request", async () => {
  const { adrCollisionClaim } = await lib();
  const { state, detail } = adrCollisionClaim({ ...READ(), prs: [PR(704, "theirs", "docs/adr/0343-theirs.md"), CUT(705, "cut")] });
  assert.equal(state, "NOT CHECKED");
  assert.match(detail, /#705\b/);
  // This branch's own pull request, cut short, is not read against it, so leaves the claim checked.
  assert.equal(adrCollisionClaim({ ...READ(), prs: [...READ().prs, CUT(703, "mine")] }).state, "PASS");
});

/* ---------- whether the tracker's state is read (ADR-350 §1) ---------- */

test("the tracker's state is read under --with-network on a push, a schedule, a dispatch and outside CI, and never on a pull request's CI", async () => {
  const { readsTrackerState } = await lib();
  for (const eventName of ["push", "schedule", "workflow_dispatch", undefined]) {
    assert.equal(readsTrackerState({ withNetwork: true, eventName }), true, `under --with-network, event ${eventName}`);
    assert.equal(readsTrackerState({ withNetwork: false, eventName }), false, `without --with-network, event ${eventName}`);
  }
  for (const eventName of ["pull_request", "pull_request_target"]) {
    assert.equal(readsTrackerState({ withNetwork: true, eventName }), false, `under --with-network, event ${eventName}`);
  }
});

/* ---------- the length of a record's slug (ADR-350 §2) ---------- */

const slug = (n) => "s".repeat(n);

test("a record from ADR-350 on whose slug is past 120 characters fails the claim, naming the record, its length and the limit", async () => {
  const { slugClaim } = await lib();
  const { state, detail } = slugClaim([`docs/adr/0350-${slug(121)}.md`, `docs/adr/0351-${slug(120)}.md`, "docs/adr/README.md"]);
  assert.equal(state, "FAIL");
  assert.match(detail, /\b0350\b/);
  assert.match(detail, /\b121\b/);
  assert.match(detail, /\b120\b/);
  assert.doesNotMatch(detail, /\b0351\b/, "a slug of exactly 120 characters is within the limit");
});

test("a record before ADR-350 keeps its slug, however long, and a tree of slugs within the limit passes naming the limit", async () => {
  const { slugClaim, overlongSlugs } = await lib();
  const tree = [`docs/adr/0334-${slug(200)}.md`, `docs/adr/0349-${slug(121)}.md`, `docs/adr/0350-${slug(120)}.md`, "docs/adr/README.md"];
  assert.deepEqual(overlongSlugs(tree), []);
  const { state, detail } = slugClaim(tree);
  assert.equal(state, "PASS");
  assert.match(detail, /\b120\b/);
  assert.match(detail, /^1 record from ADR-350 on\b/, "the PASS names how many records from ADR-350 on it read");
});

test("the slug is the file name between its number's dash and .md, and the over-long are listed by number", async () => {
  const { overlongSlugs } = await lib();
  const found = overlongSlugs([`docs/adr/0352-${slug(130)}.md`, `docs/adr/0350-${slug(125)}.md`, `docs/adr/drafts/0353-${slug(200)}.md`]);
  assert.deepEqual(found.map(({ number, length }) => [number, length]), [[350, 125], [352, 130]]);
});

/* ---------- the suite's ceiling and the retired list (ADR-368) ---------- */

// A source text holding `n` methods annotated as tests, one annotation of each kind in turn.
const tests = (n) => Array.from({ length: n }, (_, i) => `    @${["Test", "ParameterizedTest", "RepeatedTest", "TestFactory"][i % 4]}\n    void t${i}() {}\n`).join("");

test("700 methods annotated as tests across the sources pass the suite's ceiling, and 701 fail it, naming the count and the ceiling", async () => {
  const { suiteCeilingClaim } = await lib();
  assert.deepEqual(suiteCeilingClaim({ texts: [tests(400), tests(300)], dir: "src/test/java" }), {
    state: "PASS",
    detail: "700 test methods under src/test/java, of the 700 ADR-368 allows",
  });
  assert.deepEqual(suiteCeilingClaim({ texts: [tests(400), tests(301)], dir: "src/test/java" }), {
    state: "FAIL",
    detail: "701 test methods under src/test/java, past the 700 ADR-368 allows: cut one for each added",
  });
});

const RETIRED = "docs/retired-tests.txt";

test("a listed name a record yields as missing is retired, not reported missing, and the list's comments and blank lines are no names", async () => {
  const { reconcileRetired, retiredNames } = await lib();
  const retired = retiredNames("# a comment\n\nADR-157 GoneTest\r\nADR-200 GoneTest.aMethod\n");
  assert.deepEqual(retired, ["ADR-157 GoneTest", "ADR-200 GoneTest.aMethod"]);
  const { missing, retired: seen, faults } = reconcileRetired({
    retired,
    missing: ["ADR-157 GoneTest", "ADR-160 LostTest", "ADR-200 GoneTest.aMethod"],
    file: RETIRED,
  });
  assert.deepEqual(missing, ["ADR-160 LostTest"]);
  assert.deepEqual([...seen], ["ADR-157 GoneTest", "ADR-200 GoneTest.aMethod"]);
  assert.deepEqual(faults, []);
});

test("a listed name of a test that exists, which no section yields as missing, fails as stale", async () => {
  const { reconcileRetired } = await lib();
  const { missing, faults } = reconcileRetired({ retired: ["ADR-157 GoneTest", "ADR-157 StillHereTest"], missing: ["ADR-157 GoneTest"], file: RETIRED });
  assert.deepEqual(missing, []);
  assert.deepEqual(faults, [`${RETIRED} lists 1 names no record names or that exist: ADR-157 StillHereTest`]);
});

test("a listed name no record holds, the record's number wrong, fails as stale, and the name the record does hold stays missing", async () => {
  const { reconcileRetired } = await lib();
  const { missing, faults } = reconcileRetired({ retired: ["ADR-158 GoneTest"], missing: ["ADR-157 GoneTest"], file: RETIRED });
  assert.deepEqual(missing, ["ADR-157 GoneTest"]);
  assert.deepEqual(faults, [`${RETIRED} lists 1 names no record names or that exist: ADR-158 GoneTest`]);
});

test("a line for ADR-368 or later is refused, retiring nothing, and the fault says a later record corrects its Tests section in place", async () => {
  const { reconcileRetired } = await lib();
  const { missing, retired, faults } = reconcileRetired({
    retired: ["ADR-367 GoneTest", "ADR-368 GoneTest", "ADR-401 OtherTest"],
    missing: ["ADR-367 GoneTest", "ADR-368 GoneTest"],
    file: RETIRED,
  });
  assert.deepEqual(missing, ["ADR-368 GoneTest"]);
  assert.deepEqual([...retired], ["ADR-367 GoneTest"]);
  assert.deepEqual(faults, [
    `${RETIRED} lists ADR-368 GoneTest; ADR-401 OtherTest, of ADR-368 or later, which the list does not retire: a later record corrects its Tests section in place, as ADR-337 §5 says`,
  ]);
});

/* ---------- what docs/check-claims.mjs reports ---------- */

let claimsOutput;
/** docs/check-claims.mjs's output, run once from the repository's root without --with-network. */
function claims() {
  claimsOutput ??= spawnSync(process.execPath, ["docs/check-claims.mjs"], { cwd: ROOT, encoding: "utf8" }).stdout;
  return claimsOutput;
}
const claimLine = (name) => claims().split(/\r?\n/).find((l) => l.includes(`  ${name}: `)) ?? `(no line names "${name}")`;

test("check-claims reports AGENTS.md's size as git's index holds it", (t) => {
  if (spawnSync("git", ["diff", "--quiet", "--", "AGENTS.md"], { cwd: ROOT }).status !== 0) {
    t.skip("AGENTS.md differs from the index, whose blob is then not its size as committed");
    return;
  }
  const blob = execFileSync("git", ["cat-file", "-s", ":AGENTS.md"], { cwd: ROOT, encoding: "utf8" }).trim();
  const state = Number(blob) <= 30000 ? "PASS" : "FAIL";
  assert.match(claimLine("how much AGENTS.md carries"), new RegExp(`^${state} +how much AGENTS\\.md carries: AGENTS\\.md is ${blob} bytes\\b`));
});

test("check-claims holds that no two decision records of this tree share a number", () => {
  assert.match(claimLine("no two decision records share a number"), /^PASS +no two decision records share a number: /);
});

test("without --with-network, the numbers of the records this branch adds are reported not checked", () => {
  assert.match(
    claimLine("the numbers of the records this branch adds"),
    /^NOT CHECKED +the numbers of the records this branch adds: .*--with-network/,
  );
});

/* ---------- where the fetch of origin/main fails ---------- */

/**
 * An environment in which a script asks no network: git refuses every transport, so the fetch of origin/main fails
 * at once and the ref is read as it stands; gh is signed out, with no token and an empty configuration; and the
 * script's own fetch() is replaced, by the --import of NO_FETCH, with one that rejects.
 */
function offline() {
  const env = {
    ...process.env,
    GIT_ALLOW_PROTOCOL: "vespera-test-refuses-every-transport",
    GH_CONFIG_DIR: mkdtempSync(join(tmpdir(), "vespera-gh-signed-out-")),
    GH_PROMPT_DISABLED: "1",
  };
  for (const name of ["GH_TOKEN", "GITHUB_TOKEN", "GH_ENTERPRISE_TOKEN", "GITHUB_ENTERPRISE_TOKEN"]) delete env[name];
  return env;
}
const NO_FETCH = "--import=data:text/javascript,"
  + encodeURIComponent('globalThis.fetch = () => Promise.reject(new Error("this test asks no network"));');

test("under --with-network, where the fetch of origin/main fails, the numbers of the records this branch adds are not checked, naming origin/main and the fetch", () => {
  const run = spawnSync(process.execPath, [NO_FETCH, "docs/check-claims.mjs", "--with-network"], { cwd: ROOT, encoding: "utf8", env: offline() });
  const line = run.stdout.split(/\r?\n/).find((l) => l.includes("  the numbers of the records this branch adds: "))
    ?? `(no line names "the numbers of the records this branch adds")\n${run.stdout}\n${run.stderr}`;
  // A ref not fetched may lack a record merged since, and a collision with it would pass unseen.
  assert.match(line, /^NOT CHECKED +the numbers of the records this branch adds: .*origin\/main/);
  assert.match(line, /fetch/);
});

test("on a pull request's CI, under --with-network, the current wayfinder map is not checked and the line says where it is", () => {
  const env = { ...offline(), GITHUB_EVENT_NAME: "pull_request" };
  const run = spawnSync(process.execPath, [NO_FETCH, "docs/check-claims.mjs", "--with-network"], { cwd: ROOT, encoding: "utf8", env });
  const line = run.stdout.split(/\r?\n/).find((l) => l.includes("  the current wayfinder map: "))
    ?? `(no line names "the current wayfinder map")\n${run.stdout}\n${run.stderr}`;
  // fetch() rejects here, so a line that read the tracker would be FAIL "could not reach the tracker".
  assert.match(line, /^NOT CHECKED +the current wayfinder map: .*pull request.*main/);
});

test("on main's push and outside CI, under --with-network, the current wayfinder map reads the tracker", () => {
  // The env is set here, not inherited: CI runs this file with GITHUB_EVENT_NAME pull_request.
  for (const eventName of ["push", undefined]) {
    const env = offline();
    if (eventName === undefined) delete env.GITHUB_EVENT_NAME;
    else env.GITHUB_EVENT_NAME = eventName;
    const run = spawnSync(process.execPath, [NO_FETCH, "docs/check-claims.mjs", "--with-network"], { cwd: ROOT, encoding: "utf8", env });
    const line = run.stdout.split(/\r?\n/).find((l) => l.includes("  the current wayfinder map: "))
      ?? `(no line names "the current wayfinder map")\n${run.stdout}\n${run.stderr}`;
    // fetch() rejects here, so only a line that read the tracker says it could not reach it.
    assert.match(line, /^FAIL +the current wayfinder map: could not reach the tracker/, `event ${eventName}`);
  }
});

test("check-claims holds the slugs of the decision records of this tree within 120 characters, naming how many it read", () => {
  // Counted here, so that a claim that read no record, or not these, goes red. ADR-350 is in this tree, so n >= 1.
  const n = readdirSync(join(ROOT, "docs/adr")).filter((f) => /^\d{4}-.+\.md$/.test(f) && Number(f.slice(0, 4)) >= 350).length;
  assert.ok(n >= 1, "ADR-350 is in this tree");
  const line = claimLine("the length of a decision record's slug");
  assert.match(line, /^PASS +the length of a decision record's slug: .*\b120\b/);
  assert.match(line, new RegExp(`: ${n} records? from ADR-350 on\\b`), `${n} record(s) from ADR-350 on are in this tree`);
});

test("where the fetch of origin/main fails, next-adr-number still prints a number, names origin/main and the fetch on standard error, and exits 2", () => {
  const run = spawnSync(process.execPath, [NO_FETCH, "docs/next-adr-number.mjs"], { cwd: ROOT, encoding: "utf8", env: offline() });
  assert.match(run.stdout.trim(), /^\d{3,4}$/, `stdout: ${run.stdout}\nstderr: ${run.stderr}`);
  assert.match(run.stderr, /origin\/main/);
  assert.match(run.stderr, /fetch/);
  assert.equal(run.status, 2, `stderr: ${run.stderr}`);
});

/* ---------- the order of the decision records in the index and in Adr.java (ADR-363 §2) ---------- */

const ORDER = "the order of the decision records in the index and in Adr.java";

test("rows and constants in order of number pass, the index's rows read across its two tables and two constants of one record side by side among them, naming how many of each were read", async () => {
  const { adrOrderClaim } = await lib();
  const { state, detail } = adrOrderClaim({
    index: indexOf([50]), // ADR-001 and ADR-002 in the first table, ADR-050 in the second
    adrJava: adrJavaOf([6, 11, constant(11, "THE_RECORD_NUMBERED_11_AGAIN"), 50]),
  });
  assert.equal(state, "PASS");
  assert.equal(detail, `3 rows of ${INDEX} and 4 constants of ${ADR_JAVA}, each in ascending order of number`);
});

test("a row after the row of a higher number, and a record's row twice, fail the claim, naming the index and the records", async () => {
  const { adrOrderClaim } = await lib();
  const adrJava = adrJavaOf([6]);
  assert.deepEqual(adrOrderClaim({ index: indexOf([361, 360]), adrJava }), { state: "FAIL", detail: `${INDEX}: ADR-360 after ADR-361` });
  assert.deepEqual(adrOrderClaim({ index: indexOf([360, 360]), adrJava }), { state: "FAIL", detail: `${INDEX}: ADR-360 twice` });
});

test("a constant after the constant of a higher record fails the claim, and a fault in each file is named file by file, the index first", async () => {
  const { adrOrderClaim } = await lib();
  assert.deepEqual(adrOrderClaim({ index: indexOf([]), adrJava: adrJavaOf([53, 15]) }), {
    state: "FAIL",
    detail: `${ADR_JAVA}: ADR-015 after ADR-053`,
  });
  // ADR-001 in the second table, after the first table's ADR-002.
  assert.deepEqual(adrOrderClaim({ index: indexOf([1]), adrJava: adrJavaOf([53, 15]) }), {
    state: "FAIL",
    detail: `${INDEX}: ADR-001 after ADR-002; ${ADR_JAVA}: ADR-015 after ADR-053`,
  });
});

test("a conflict marker git writes, of any of its three kinds, fails the claim in either file, naming the file and the marker's line", async () => {
  const { adrOrderClaim } = await lib();
  // In the index, the line after the fifteen of its head, its first table and the second's head, and the rows of 356 and
  // 357, is line 18.
  for (const marker of ["<<<<<<< HEAD", "||||||| base", ">>>>>>> main"]) {
    assert.deepEqual(
      adrOrderClaim({ index: indexOf([356, 357, marker]), adrJava: adrJavaOf([6]) }),
      { state: "FAIL", detail: `${INDEX}: a conflict marker on line 18` },
      marker,
    );
  }
  // In Adr.java, the line after the seven of its head, the six of the constant of ADR-006 and its blank line is line 15.
  assert.deepEqual(adrOrderClaim({ index: indexOf([]), adrJava: adrJavaOf([6, [">>>>>>> main"]]) }), {
    state: "FAIL",
    detail: `${ADR_JAVA}: a conflict marker on line 15`,
  });
});

test("check-claims holds the rows of this tree's index and the constants of its Adr.java in order of number, naming how many of each it read", () => {
  // Counted here, so that a claim that read no row or no constant, or not these, goes red.
  const rows = readFileSync(join(ROOT, INDEX), "utf8").split(/\r?\n/).filter((l) => /^\| \[ADR-\d+\]/.test(l)).length;
  const constants = [...readFileSync(join(ROOT, ADR_JAVA), "utf8").matchAll(/public static final String \w+\s*=[^;]*"\d{4}-[^"]*\.md"/g)].length;
  assert.ok(rows > 0 && constants > 0, `this tree has rows (${rows}) and constants (${constants})`);
  assert.equal(
    claimLine(ORDER).replace(/^PASS +/, "PASS  "),
    `PASS  ${ORDER}: ${rows} rows of ${INDEX} and ${constants} constants of ${ADR_JAVA}, each in ascending order of number`,
  );
});
