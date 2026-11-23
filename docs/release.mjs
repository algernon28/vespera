#!/usr/bin/env node
// The rules a release is held to (ADR-431 §6 to §8, ADR-444), held here so that .github/workflows/release.yml carries
// none of its own. Each refusal throws an Error naming what it refuses; run as a command, the refusal's line goes to
// standard error and the exit is 1.
//
//   node docs/release.mjs next
//   node docs/release.mjs check <tag>
//   node docs/release.mjs built <tag> <manifest> <implementation-versions>
//   node docs/release.mjs notes <tag>

import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const NUMBER = "(0|[1-9][0-9]*)";
const RELEASE = new RegExp(`^${NUMBER}\\.${NUMBER}\\.${NUMBER}$`);
const BLOB = "https://github.com/algernon28/vespera/blob";

/** The version of a `vMAJOR.MINOR.PATCH` tag, the tag without its v; §8's first rule. */
export function versionOfTag(tag) {
  if (typeof tag === "string" && tag.startsWith("v") && RELEASE.test(tag.slice(1))) return tag.slice(1);
  throw new Error(`the tag ${tag} is refused: a release tag is v and three numbers, vMAJOR.MINOR.PATCH`);
}

/** §8's second rule: only MAJOR.MINOR.PATCH is published. */
export function checkPublishable(version) {
  if (!RELEASE.test(version)) {
    throw new Error(`the version ${JSON.stringify(version)} is refused: it is not MAJOR.MINOR.PATCH`);
  }
}

/** §8's second and fourth rules, read off the jar's manifest and its implementation-versions.properties. */
export function checkBuilt(version, manifestText, implementationVersionsText) {
  const named = /^Implementation-Version:[ \t]*(.*?)[ \t]*$/m.exec(manifestText.replace(/\r\n?/g, "\n"));
  if (named === null) throw new Error(`the jar's manifest names no Implementation-Version, the tag's being ${version}`);
  if (named[1] !== version) {
    throw new Error(`the jar's manifest names ${named[1]} and the tag's version is ${version}`);
  }
  checkPublishable(named[1]);
  for (const line of implementationVersionsText.replace(/\r\n?/g, "\n").split("\n")) {
    const eq = line.indexOf("=");
    if (eq < 0 || line.startsWith("#")) continue;
    if (line.slice(eq + 1).trim() === "unknown") {
      throw new Error(`the jar's implementation version of the module ${line.slice(0, eq).trim()} is unknown, so it could mint no run`);
    }
  }
}

const parsed = (tag) => {
  const m = /^v(\d+)\.(\d+)(?:\.(\d+))?$/.exec(tag);
  return m === null ? null : [Number(m[1]), Number(m[2]), Number(m[3] ?? 0)];
};
const compare = (a, b) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2];

/** The greatest release tag below `tag`, `v1.2` read as 1.2.0, or null. */
export function previousTag(tag, tags) {
  const own = parsed(tag);
  let best = null;
  let bestKey = null;
  for (const t of tags) {
    const key = parsed(t);
    if (key === null || compare(key, own) >= 0) continue;
    if (bestKey === null || compare(key, bestKey) > 0) {
      best = t;
      bestKey = key;
    }
  }
  return best;
}

const ROW = /^\| \[ADR-(\d+)\]\(([^)]+)\) \| [^|]* \| (.*) \|\s*$/;

function rowsOf(index) {
  const rows = new Map();
  for (const line of index.split(/\r?\n/)) {
    const m = ROW.exec(line);
    if (m === null) continue;
    const title = m[3].replace(/\s+\*\([^]*\)\*\s*$/, "");
    rows.set(Number(m[1]), { file: m[2], title, number: m[1] });
  }
  return rows;
}

/** The notes: the records the index holds at the tag and not at the previous one; §7. */
export function releaseNotes({ tag, previous, currentIndex, previousIndex }) {
  const had = previous === null || previousIndex == null ? new Map() : rowsOf(previousIndex);
  const fresh = [...rowsOf(currentIndex)].filter(([n]) => !had.has(n)).sort((a, b) => a[0] - b[0]);
  if (previous !== null && fresh.length === 0) return `No decision record was merged since ${previous}.\n`;
  const head = previous === null ? "Decision records in this release:" : `Decision records merged since ${previous}:`;
  const lines = fresh.map(([, r]) => `- [ADR-${r.number.padStart(3, "0")}](${BLOB}/${tag}/docs/adr/${r.file}): ${r.title}`);
  return [head, "", ...lines, ""].join("\n");
}

const BUMPS = ["patch", "minor", "major"];
const FIRST = "2.0.0";
const RELEASE_LINE = /^- \*\*Release\*\*: (major|minor|patch)\s*$/m;

/** The bump a record's head names on its `- **Release**:` line, major, minor or patch; ADR-444 §2. */
export function releaseOf(number, recordText) {
  const m = RELEASE_LINE.exec(recordText.replace(/\r\n?/g, "\n"));
  if (m === null) {
    throw new Error(`ADR-${String(number).padStart(3, "0")} names no release on a "- **Release**:" line, major, minor or patch`);
  }
  return m[1];
}

/**
 * The next release's version, ADR-444 §3: the previous tag's version raised by the greatest bump among the records
 * merged since it, a patch where none was; 2.0.0 where there is no previous tag or it lies below 2.0.0 (ADR-431 §9).
 */
export function nextVersion(previous, bumps) {
  const key = previous === null ? null : parsed(previous);
  if (key === null || compare(key, [2, 0, 0]) < 0) return FIRST;
  const greatest = Math.max(0, ...bumps.map((b) => BUMPS.indexOf(b)));
  const [major, minor, patch] = key;
  if (greatest === 2) return `${major + 1}.0.0`;
  if (greatest === 1) return `${major}.${minor + 1}.0`;
  return `${major}.${minor}.${patch + 1}`;
}

/** The numbers of the records `currentIndex` holds and `previousIndex` does not, ascending, with each one's file. */
export function freshRecords(currentIndex, previousIndex) {
  const had = previousIndex == null ? new Map() : rowsOf(previousIndex);
  return [...rowsOf(currentIndex)].filter(([n]) => !had.has(n)).sort((a, b) => a[0] - b[0])
    .map(([n, r]) => ({ number: n, file: r.file }));
}

/** §8's third rule: whether `commit` is an ancestor of `main`. */
export function reachableFromMain(commit, { cwd = process.cwd(), main = "origin/main" } = {}) {
  return spawnSync("git", ["merge-base", "--is-ancestor", commit, main], { cwd }).status === 0;
}

function git(...args) {
  const ran = spawnSync("git", args, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (ran.status !== 0) throw new Error(`git ${args.join(" ")} failed: ${ran.stderr.trim()}`);
  return ran.stdout;
}

function main(argv) {
  const [command, tag, ...rest] = argv;
  if (command === "next") {
    // ADR-444 §3: nothing where HEAD already carries a release tag, else the version its release takes.
    const here = git("tag", "--points-at", "HEAD").split("\n").filter((t) => /^v\d+\.\d+\.\d+$/.test(t));
    if (here.length > 0) return;
    const reached = git("tag", "--merged", "HEAD").split("\n").filter(Boolean);
    const previous = previousTag("v999999.0.0", reached);
    const at = (ref, path) => git("show", `${ref}:${path}`);
    let bumps = [];
    if (previous !== null && compare(parsed(previous), [2, 0, 0]) >= 0) {
      bumps = freshRecords(at("HEAD", "docs/adr/README.md"), at(previous, "docs/adr/README.md"))
        .map(({ number, file }) => releaseOf(number, at("HEAD", `docs/adr/${file}`)));
    }
    console.log(nextVersion(previous, bumps));
  } else if (command === "check") {
    const version = versionOfTag(tag);
    checkPublishable(version);
    if (!reachableFromMain(`${tag}^{commit}`)) {
      throw new Error(`the tag ${tag} is on a commit not reachable from origin/main`);
    }
    console.log(version);
  } else if (command === "built") {
    checkBuilt(versionOfTag(tag), readFileSync(rest[0], "utf8"), readFileSync(rest[1], "utf8"));
  } else if (command === "notes") {
    versionOfTag(tag);
    const tags = git("tag", "--list").split("\n").filter(Boolean);
    const previous = previousTag(tag, tags);
    const index = (t) => git("show", `${t}:docs/adr/README.md`);
    process.stdout.write(
      releaseNotes({ tag, previous, currentIndex: index(tag), previousIndex: previous === null ? null : index(previous) }),
    );
  } else {
    throw new Error("usage: release.mjs next | check <tag> | built <tag> <manifest> <implementation-versions> | notes <tag>");
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    main(process.argv.slice(2));
  } catch (refused) {
    console.error(refused.message);
    process.exit(1);
  }
}
