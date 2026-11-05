// What docs/check-claims.mjs and docs/next-adr-number.mjs compute without reading a file or asking the network
// (ADR-346 §4). The scripts call git and gh themselves and pass what they read.

const CEILING = 30000;
const ADR_PATH = /^docs\/adr\/(\d{4})-[^/]+\.md$/;
// A file a pull request leaves as the path it was: these four are not an addition, anything else is.
const NOT_ADDED = new Set(["MODIFIED", "CHANGED", "DELETED", "UNCHANGED"]);

/** AGENTS.md's size as git stores it: UTF-8 bytes, each CRLF counted as the one LF git stores. */
export function agentsMdSize(text) {
  const size = Buffer.byteLength(text.replaceAll("\r\n", "\n"), "utf8");
  return { size, within: size <= CEILING };
}

/** A record's number from its path from the repository's root, or null for a path that is no record. */
export function adrNumber(path) {
  const m = ADR_PATH.exec(String(path).replaceAll("\\", "/"));
  return m ? Number(m[1]) : null;
}

/** { number, files } for each number two different paths take, the files sorted, in order of number. */
export function sharedNumbers(paths) {
  const byNumber = new Map();
  for (const p of new Set(paths)) {
    const n = adrNumber(p);
    if (n !== null) byNumber.set(n, [...(byNumber.get(n) ?? []), p]);
  }
  return [...byNumber]
    .filter(([, files]) => files.length > 1)
    .sort((a, b) => a[0] - b[0])
    .map(([number, files]) => ({ number, files: files.sort() }));
}

/** The paths of records a pull request adds, as ADR-346 §2 reads what it adds. */
function addedBy(pr) {
  return (pr.files ?? []).filter((f) => !NOT_ADDED.has(f.changeType)).map((f) => f.path).filter((p) => adrNumber(p) !== null);
}

/** The lowest number above origin/main's highest that the branch's tree and no open pull request holds. */
export function nextAdrNumber({ main, branchTree, prs }) {
  const highest = Math.max(0, ...main.map(adrNumber).filter((n) => n !== null));
  const taken = new Set([
    ...branchTree.map(adrNumber),
    ...(prs ?? []).flatMap((pr) => addedBy(pr).map(adrNumber)),
  ]);
  let n = highest + 1;
  while (taken.has(n)) n++;
  return n;
}

/** { number, added, other, where } for each record this branch adds that another holds under a different file name. */
export function adrCollisions({ added, main, prs, branchName }) {
  const found = [];
  const others = (prs ?? []).filter((pr) => branchName == null || pr.headRefName !== branchName);
  for (const path of added ?? []) {
    const number = adrNumber(path);
    if (number === null) continue;
    for (const other of main ?? []) {
      if (adrNumber(other) === number && other !== path) found.push({ number, added: path, other, where: "origin/main", rank: 0, order: 0 });
    }
    for (const pr of others) {
      for (const other of addedBy(pr)) {
        if (adrNumber(other) === number && other !== path) found.push({ number, added: path, other, where: `#${pr.number}`, rank: 1, order: pr.number });
      }
    }
  }
  return found
    .sort((a, b) => a.number - b.number || a.rank - b.rank || a.order - b.order || (a.added < b.added ? -1 : a.added > b.added ? 1 : 0) || (a.other < b.other ? -1 : a.other > b.other ? 1 : 0))
    .map(({ number, added: a, other, where }) => ({ number, added: a, other, where }));
}

/** { full, incomplete }: whether the list holds at least `limit` pull requests, and the numbers of those cut short. */
export function unreadPullRequests({ prs, limit, branchName }) {
  const incomplete = prs
    .filter((pr) => branchName == null || pr.headRefName !== branchName)
    .filter((pr) => !Number.isInteger(pr.changedFiles) || (pr.files ?? []).length < pr.changedFiles)
    .map((pr) => pr.number)
    .sort((a, b) => a - b);
  return { full: prs.length >= limit, incomplete };
}

/** docs/next-adr-number.mjs's exit where origin/main resolved: 0 only when every part was read in full. */
export function nextAdrNumberExit({ fetched, prs, limit, branchName }) {
  if (!fetched || prs === null || prs === undefined) return 2;
  const { full, incomplete } = unreadPullRequests({ prs, limit, branchName });
  return full || incomplete.length ? 2 : 0;
}

/** { state, detail } of the claim of the numbers this branch adds; it never passes where it did not read. */
export function adrCollisionClaim({ added, main, prs, branchName, limit }) {
  const collisions = adrCollisions({ added, main, prs, branchName });
  if (collisions.length) {
    return {
      state: "FAIL",
      detail: collisions.map((c) => `${c.number} is taken by ${c.added} here and by ${c.other} on ${c.where}`).join("; "),
    };
  }
  const unread = [];
  if (prs === null || prs === undefined) unread.push("the open pull requests were not read");
  else {
    const { full, incomplete } = unreadPullRequests({ prs, limit, branchName });
    if (full) unread.push(`the list of open pull requests fills its limit of ${limit}, so some may be left out`);
    for (const n of incomplete) unread.push(`the files of #${n} were cut short`);
  }
  if (main === null || main === undefined) unread.push("origin/main was not read");
  if (added === null || added === undefined) unread.push("the merge base of origin/main and HEAD could not be resolved");
  if (unread.length) return { state: "NOT CHECKED", detail: unread.join("; ") };
  return {
    state: "PASS",
    detail: `${added.length} file(s) added over origin/main, read against origin/main and ${prs.length} open pull request(s), and none takes a number another holds`,
  };
}
