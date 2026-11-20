#!/usr/bin/env node
// The rules a release is held to (ADR-431 §6 to §8), held here so that .github/workflows/release.yml carries none of its
// own. Each refusal throws an Error naming what it refuses; run as a command, the refusal's line goes to standard error
// and the exit is 1.
//
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
  if (command === "check") {
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
    throw new Error("usage: release.mjs check <tag> | built <tag> <manifest> <implementation-versions> | notes <tag>");
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
