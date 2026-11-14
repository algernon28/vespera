#!/usr/bin/env node
// Prints the number a new decision record takes (ADR-346 §2, ADR-389 §1): the lowest number above the highest record
// on origin/main that this branch's tree holds in no record, no open pull request adds, and no branch of origin holds.
//
//   node docs/next-adr-number.mjs
//
// Exit 0: checked against origin/main and every branch of origin as fetched, the branch, and every open pull request,
//         all read in full.
// Exit 2: a number is printed, but something was not read; standard error says what.
// Exit 1: origin/main cannot be resolved; no number is printed.

import { readdirSync } from "node:fs";
import { nextAdrNumber, nextAdrNumberExit, unreadPullRequests } from "./check-claims-lib.mjs";
import { branchName as currentBranch, fetchOrigin, readBranches, readPullRequests, run } from "./adr-numbers-read.mjs";

const fetched = fetchOrigin();
const tree = run("git", ["ls-tree", "-r", "-z", "--name-only", "origin/main", "--", "docs/adr"]);
if (!tree.ok) {
  console.error(`origin/main cannot be resolved: ${tree.err}`);
  process.exit(1);
}
const main = tree.out.split("\0").filter(Boolean);
const branchTree = readdirSync("docs/adr").map((f) => `docs/adr/${f}`);
const branchName = currentBranch();

const notRead = [];
if (!fetched.ok) {
  notRead.push(`origin/main and the branches of origin: the fetch failed (${fetched.err}), so a record merged or pushed since may be missing from them`);
}
const { branches, err: branchesErr } = readBranches();
if (branches === null) notRead.push(`the branches of origin: ${branchesErr}`);
const { prs, err: prsErr } = readPullRequests();
if (prs === null) notRead.push(`the open pull requests: ${prsErr}`);
else for (const n of unreadPullRequests({ prs, branchName }).incomplete) notRead.push(`the files of #${n} were cut short`);

console.log(nextAdrNumber({ main, branchTree, prs, branches }));
if (notRead.length) {
  console.error(`Not read: ${notRead.join("; ")}. The number may be one of theirs.`);
}
process.exitCode = nextAdrNumberExit({ fetched: fetched.ok, prs, branches, branchName });
