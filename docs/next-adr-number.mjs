#!/usr/bin/env node
// Prints the number a new decision record takes (ADR-346 §2): the lowest number above the highest record on
// origin/main that this branch's tree holds in no record and no open pull request adds.
//
//   node docs/next-adr-number.mjs
//
// Exit 0: checked against origin/main as fetched, the branch and every open pull request, all read in full.
// Exit 2: a number is printed, but something was not read; standard error says what.
// Exit 1: origin/main cannot be resolved; no number is printed.

import { spawnSync } from "node:child_process";
import { readdirSync } from "node:fs";
import { nextAdrNumber, nextAdrNumberExit, unreadPullRequests } from "./check-claims-lib.mjs";

const REPO = "algernon28/vespera";
const LIMIT = 100;
const run = (cmd, args) => {
  const r = spawnSync(cmd, args, { encoding: "utf8", maxBuffer: 256 * 1024 * 1024 });
  return { ok: r.status === 0 && !r.error, out: r.stdout ?? "", err: (r.error?.message ?? r.stderr ?? "").trim() };
};

const fetchMain = run("git", ["fetch", "--no-tags", "origin", "+refs/heads/main:refs/remotes/origin/main"]);
const tree = run("git", ["ls-tree", "-r", "-z", "--name-only", "origin/main", "--", "docs/adr"]);
if (!tree.ok) {
  console.error(`origin/main cannot be resolved: ${tree.err}`);
  process.exit(1);
}
const main = tree.out.split("\0").filter(Boolean);
const branchTree = readdirSync("docs/adr").map((f) => `docs/adr/${f}`);

let branchName = run("git", ["rev-parse", "--abbrev-ref", "HEAD"]);
branchName = branchName.ok && branchName.out.trim() !== "HEAD" ? branchName.out.trim() : process.env.GITHUB_HEAD_REF || null;

const notRead = [];
let prs = null;
const list = run("gh", ["pr", "list", "--repo", REPO, "--state", "open", "--limit", String(LIMIT), "--json", "number,headRefName,changedFiles,files"]);
if (list.ok) {
  try {
    prs = JSON.parse(list.out);
  } catch (e) {
    notRead.push(`the open pull requests: gh answered something unreadable (${e.message})`);
  }
} else notRead.push(`the open pull requests: ${list.err}`);

if (prs) {
  for (const n of unreadPullRequests({ prs, limit: Infinity, branchName }).incomplete) {
    const pr = prs.find((p) => p.number === n);
    const paged = run("gh", ["api", `repos/${REPO}/pulls/${n}/files`, "--paginate", "--jq", ".[] | {filename, status}"]);
    if (!paged.ok) continue;
    try {
      pr.files = paged.out.split(/\r?\n/).filter(Boolean).map((l) => {
        const f = JSON.parse(l);
        const status = f.status === "removed" ? "DELETED" : String(f.status ?? "").toUpperCase();
        return { path: f.filename, changeType: status || undefined };
      });
    } catch {
      // stays cut short
    }
  }
  const { full, incomplete } = unreadPullRequests({ prs, limit: LIMIT, branchName });
  if (full) notRead.push(`the open pull requests: the list fills its limit of ${LIMIT}, so some may be left out`);
  for (const n of incomplete) notRead.push(`the files of #${n} were cut short`);
}

if (!fetchMain.ok) notRead.unshift(`origin/main: the fetch failed (${fetchMain.err}), so a record merged since may be missing from it`);

console.log(nextAdrNumber({ main, branchTree, prs }));
const exit = nextAdrNumberExit({ fetched: fetchMain.ok, prs, limit: LIMIT, branchName });
if (notRead.length) {
  console.error(`Not read: ${notRead.join("; ")}. The number may be one of theirs.`);
}
process.exitCode = exit;
