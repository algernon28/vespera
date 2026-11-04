// docs/check-claims-lib.mjs holds what the claims check and docs/next-adr-number.mjs compute without reading a file or
// asking the network (ADR-342): AGENTS.md's size as git stores it, and a decision record's number, read, shared, taken
// and chosen, the open pull requests read in full or cut short, and the claim of the numbers this branch adds, which
// never passes where it did not read. One test asks git whether it stores AGENTS.md as text. The last three run
// docs/check-claims.mjs itself, without --with-network, and read its lines.
//
//   node --test src/test/docs/check-claims.test.mjs

import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

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
  assert.equal(attr, "AGENTS.md: text: set", "the .gitattributes line /AGENTS.md text is what makes ADR-342 §1's measure exact");
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
  assert.equal(nextAdrNumber({ main: MAIN, branch: MAIN, prs: [] }), 342);
});

test("the next number passes over what this branch and every open pull request hold, and takes the lowest left", async () => {
  const { nextAdrNumber } = await lib();
  assert.equal(nextAdrNumber({ main: MAIN, branch: MAIN, prs: [PR(704, "x", "docs/adr/0342-x.md")] }), 343);
  assert.equal(
    nextAdrNumber({ main: MAIN, branch: [...MAIN, "docs/adr/0342-mine.md"], prs: [PR(704, "x", "docs/adr/0343-x.md")] }),
    344,
  );
  // A copy is a new path, and takes its number as an added file does.
  assert.equal(
    nextAdrNumber({ main: MAIN, branch: MAIN, prs: [PR(704, "x", { path: "docs/adr/0342-x.md", changeType: "COPIED" })] }),
    343,
  );
  // An open pull request that skipped 342 leaves it free.
  assert.equal(nextAdrNumber({ main: MAIN, branch: MAIN, prs: [PR(704, "x", "docs/adr/0344-x.md")] }), 342);
});

/* ---------- a number this branch takes that another holds ---------- */

test("a record this branch adds collides with one origin/main holds under another name", async () => {
  const { adrCollisions } = await lib();
  const found = adrCollisions({
    added: ["docs/adr/0342-mine.md"],
    main: [...MAIN, "docs/adr/0342-theirs.md"],
    prs: [],
    branch: "mine",
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
    branch: "mine",
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
    branch: "mine",
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
    branch: "mine",
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
    branch: null,
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
  assert.deepEqual(unreadPullRequests({ prs, limit: 100, branch: "mine" }), { full: false, incomplete: [702, 705] });
});

test("a list of open pull requests as long as its --limit may have left some out", async () => {
  const { unreadPullRequests } = await lib();
  const prs = [PR(704, "a", "x.md"), PR(705, "b", "y.md")];
  assert.deepEqual(unreadPullRequests({ prs, limit: 2, branch: "mine" }), { full: true, incomplete: [] });
  assert.deepEqual(unreadPullRequests({ prs, limit: 3, branch: "mine" }), { full: false, incomplete: [] });
});

/* ---------- the claim of the numbers this branch adds ---------- */

// Every part read in full: the branch adds 0342-mine.md, origin/main holds MAIN, and #704 adds 0343-theirs.md.
const READ = () => ({
  added: ["docs/adr/0342-mine.md"],
  main: MAIN,
  prs: [PR(704, "theirs", "docs/adr/0343-theirs.md")],
  branch: "mine",
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
  assert.match(claimLine("how much AGENTS.md carries"), new RegExp(`^(PASS|FAIL) +how much AGENTS\\.md carries: AGENTS\\.md is ${blob} bytes\\b`));
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
