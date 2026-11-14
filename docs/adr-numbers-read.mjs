// What docs/check-claims.mjs and docs/next-adr-number.mjs read from git and GitHub for the numbers of the decision
// records (ADR-346 §2, ADR-389): the fetch of origin's branches, the records each branch of origin holds, and the open
// pull requests, read through GitHub's REST API. What is computed from what they read is docs/check-claims-lib.mjs's.

import { spawnSync } from "node:child_process";
import { restPullRequest } from "./check-claims-lib.mjs";

export const REPO = "algernon28/vespera";
const ADR_DIR = "docs/adr";
const ORIGIN = "refs/remotes/origin/";

/** { ok, out, err } of a command, `err` its error or standard error, trimmed. */
export function run(cmd, args) {
  const r = spawnSync(cmd, args, { encoding: "utf8", maxBuffer: 256 * 1024 * 1024 });
  return { ok: r.status === 0 && !r.error, out: r.stdout ?? "", err: (r.error?.message || r.stderr || "").trim() };
}

/** Fetches every branch of origin, dropping the ref of each one origin no longer has: { ok, err }. */
export function fetchOrigin() {
  const r = run("git", ["fetch", "--no-tags", "--prune", "origin", `+refs/heads/*:${ORIGIN}*`]);
  return { ok: r.ok, err: r.err };
}

/** The paths under docs/adr of a ref's tree, or null where git cannot read it. */
function treeOf(ref) {
  const r = run("git", ["ls-tree", "-r", "-z", "--name-only", ref, "--", ADR_DIR]);
  return r.ok ? r.out.split("\0").filter(Boolean) : null;
}

/**
 * { branches, err }: each branch of origin but main as `{ name, tree }`, its name on origin and the paths of its tree
 * under docs/adr, read from the refs as they stand; `branches` is null, and `err` says why, where one is not read.
 */
export function readBranches() {
  const refs = run("git", ["for-each-ref", "--format=%(refname)", ORIGIN.slice(0, -1)]);
  if (!refs.ok) return { branches: null, err: refs.err };
  const branches = [];
  for (const ref of refs.out.split(/\r?\n/).filter(Boolean)) {
    const name = ref.slice(ORIGIN.length);
    if (name === "HEAD" || name === "main") continue;
    const tree = treeOf(ref);
    if (tree === null) return { branches: null, err: `the tree of origin/${name} could not be read` };
    branches.push({ name, tree });
  }
  return { branches, err: null };
}

/**
 * { prs, err }: the open pull requests, every page of the list, each as restPullRequest gives it, its files paged in
 * full; `prs` is null, and `err` says why, where the list is not read. A pull request whose files are not read stays
 * cut short.
 */
export function readPullRequests() {
  const list = run("gh", ["api", "--paginate", `repos/${REPO}/pulls?state=open&per_page=100`, "--jq", ".[] | {number, headRefName: .head.ref}"]);
  if (!list.ok) return { prs: null, err: list.err.split(/\r?\n/)[0] };
  let heads;
  try {
    heads = list.out.split(/\r?\n/).filter(Boolean).map((l) => JSON.parse(l));
  } catch (e) {
    return { prs: null, err: `gh answered something unreadable (${e.message})` };
  }
  const prs = heads.map(({ number, headRefName }) => {
    const paged = run("gh", ["api", "--paginate", `repos/${REPO}/pulls/${number}/files?per_page=100`, "--jq", ".[] | {filename, status}"]);
    let files = null;
    if (paged.ok) {
      try {
        files = paged.out.split(/\r?\n/).filter(Boolean).map((l) => JSON.parse(l));
      } catch {
        // stays cut short
      }
    }
    return restPullRequest({ number, headRefName, files });
  });
  return { prs, err: null };
}

/** This branch's name: `git rev-parse --abbrev-ref HEAD`, or GITHUB_HEAD_REF where HEAD is detached, or null. */
export function branchName() {
  const r = run("git", ["rev-parse", "--abbrev-ref", "HEAD"]);
  return r.ok && r.out.trim() !== "HEAD" ? r.out.trim() : process.env.GITHUB_HEAD_REF || null;
}
