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

/**
 * Whether the claim of the current wayfinder map reads the tracker (ADR-350 §1): under --with-network, and never on a
 * pull request's CI, where a label or a close the pull request did not make would turn it red.
 */
export function readsTrackerState({ withNetwork, eventName }) {
  return Boolean(withNetwork) && eventName !== "pull_request" && eventName !== "pull_request_target";
}

/** The longest slug a record from FIRST_CAPPED on may have, and that first record (ADR-350 §2). */
export const SLUG_LIMIT = 120;
export const FIRST_CAPPED = 350;
const ADR_SLUG = /^docs\/adr\/(\d{4})-([^/]+)\.md$/;

/** { number, path, length } for each record from FIRST_CAPPED on whose slug is past SLUG_LIMIT, in order of number. */
export function overlongSlugs(paths) {
  return paths
    .map((p) => ADR_SLUG.exec(String(p).replaceAll("\\", "/")))
    .filter((m) => m && Number(m[1]) >= FIRST_CAPPED && m[2].length > SLUG_LIMIT)
    .map((m) => ({ number: Number(m[1]), path: m[0], length: m[2].length }))
    .sort((a, b) => a.number - b.number);
}

/**
 * { state, detail } of the claim of the records' slugs: FAIL naming each over-long one and the limit, else PASS naming
 * how many records from FIRST_CAPPED on it read and the limit.
 */
export function slugClaim(paths) {
  const over = overlongSlugs(paths);
  const read = paths
    .map((p) => ADR_SLUG.exec(String(p).replaceAll("\\", "/")))
    .filter((m) => m && Number(m[1]) >= FIRST_CAPPED).length;
  if (over.length) {
    return {
      state: "FAIL",
      detail: over.map((o) => `${o.path.slice("docs/adr/".length, "docs/adr/".length + 4)}'s slug is ${o.length} characters`).join("; ")
        + `, over the ${SLUG_LIMIT} a record's slug keeps from ADR-${FIRST_CAPPED} on: shorten it, so that a clone's path stays within Windows' limit`,
    };
  }
  return {
    state: "PASS",
    detail: `${read} record${read === 1 ? "" : "s"} from ADR-${FIRST_CAPPED} on, every slug within ${SLUG_LIMIT} characters`,
  };
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

const ORDER_INDEX = "docs/adr/README.md";
const ORDER_ADR_JAVA = "src/test/java/io/algernon/vespera/Adr.java";
const CONFLICT_MARKER = /^(<{7}|\|{7}|>{7})/;
const INDEX_ROW = /^\| \[ADR-(\d+)\]/;
const ADR_CONSTANT = /public static final String \w+\s*=[^;]*?"(\d{4})-[^"]*\.md"/g;
const adrId = (n) => `ADR-${String(n).padStart(3, "0")}`;

/** The faults of one file's lines, in the order of the file: a conflict marker, or a number not above the one before. */
function orderFaults(path, lines, numbers, allowRepeat) {
  const found = [];
  lines.forEach((line, i) => {
    if (CONFLICT_MARKER.test(line)) found.push({ at: i, said: `${path}: a conflict marker on line ${i + 1}` });
  });
  let before = null;
  for (const { at, number } of numbers) {
    if (before !== null && number < before) found.push({ at, said: `${path}: ${adrId(number)} after ${adrId(before)}` });
    else if (before !== null && number === before && !allowRepeat) found.push({ at, said: `${path}: ${adrId(number)} twice` });
    before = number;
  }
  return found.sort((a, b) => a.at - b.at).map((f) => f.said);
}

/**
 * { state, detail } of the claim of the order of the decision records in the index and in Adr.java (ADR-363 §2), given
 * both files' text: FAIL naming every fault, else PASS naming how many rows and constants it read.
 */
export function adrOrderClaim({ index, adrJava }) {
  const indexLines = index.split(/\r?\n/);
  const rows = [];
  indexLines.forEach((line, at) => {
    const m = INDEX_ROW.exec(line);
    if (m) rows.push({ at, number: Number(m[1]) });
  });
  const javaLines = adrJava.split(/\r?\n/);
  const constants = [];
  for (const m of adrJava.matchAll(ADR_CONSTANT)) {
    constants.push({ at: adrJava.slice(0, m.index).split(/\r?\n/).length - 1, number: Number(m[1]) });
  }
  const faults = [
    ...orderFaults(ORDER_INDEX, indexLines, rows, false),
    ...orderFaults(ORDER_ADR_JAVA, javaLines, constants, true),
  ];
  if (faults.length) return { state: "FAIL", detail: faults.join("; ") };
  return {
    state: "PASS",
    detail: `${rows.length} rows of ${ORDER_INDEX} and ${constants.length} constants of ${ORDER_ADR_JAVA}, each in ascending order of number`,
  };
}

/** The most methods annotated as tests the Java suite may hold (ADR-368 §1). */
export const SUITE_CEILING = 700;
const TEST_ANNOTATION = /^\s*@(?:Test|ParameterizedTest|RepeatedTest|TestFactory)\b/gm;

/** How many methods the given source texts annotate as tests, each once: a parameterized test's cases are not counted. */
export function testMethodCount(texts) {
  return texts.reduce((count, text) => count + (String(text).match(TEST_ANNOTATION) ?? []).length, 0);
}

/**
 * { state, detail } of the claim of the suite's ceiling, given the text of each source file under `dir`: FAIL past
 * `ceiling`, else PASS, each naming the count and the ceiling.
 */
export function suiteCeilingClaim({ texts, dir, ceiling = SUITE_CEILING }) {
  const count = testMethodCount(texts);
  if (count > ceiling) return { state: "FAIL", detail: `${count} test methods under ${dir}, past the ${ceiling} ADR-368 allows: cut one for each added` };
  return { state: "PASS", detail: `${count} test methods under ${dir}, of the ${ceiling} ADR-368 allows` };
}

/** The first record whose Tests section the retired list may not speak for: from it on, a record's section is corrected. */
export const FIRST_UNRETIRED = 368;

/** The names docs/retired-tests.txt lists, one `ADR-NNN Name` a line, in the file's order; blank lines and `#` comments are not. */
export function retiredNames(text) {
  return String(text).split(/\r?\n/).filter((l) => l.trim() && !l.startsWith("#"));
}

/**
 * The names the records' Tests sections yielded as missing, `ADR-NNN Name` each, read against the retired list:
 * { missing, retired, faults }. `missing` keeps, in order, each name the list does not retire; `retired` is the set of
 * listed names a record yielded; `faults` names, as `file` says them, each line for ADR-368 or later, which a later
 * record corrects in place instead (ADR-337 §5) and which retires nothing, and then the stale lines: a name that exists or no record holds.
 */
export function reconcileRetired({ retired, missing, file }) {
  const refused = [];
  const listed = new Set();
  for (const line of new Set(retired)) {
    const m = /^ADR-(\d+) /.exec(line);
    if (m && Number(m[1]) >= FIRST_UNRETIRED) refused.push(line);
    else listed.add(line);
  }
  const seen = new Set();
  const still = [];
  for (const name of missing) {
    if (listed.has(name)) seen.add(name);
    else still.push(name);
  }
  const faults = [];
  if (refused.length) {
    faults.push(`${file} lists ${refused.join("; ")}, of ADR-${FIRST_UNRETIRED} or later, which the list does not retire: a later record corrects its Tests section in place, as ADR-337 §5 says`);
  }
  const stale = [...listed].filter((r) => !seen.has(r));
  if (stale.length) faults.push(`${file} lists ${stale.length} names no record names or that exist: ${stale.join("; ")}`);
  return { missing: still, retired: seen, faults };
}
