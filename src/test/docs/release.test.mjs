// docs/release.mjs holds the rules a release is held to (ADR-430 §6 to §8), so that .github/workflows/release.yml
// carries none of its own: the tag's version and the tags refused, a version that is no release refused, a jar whose
// manifest names another version or which could mint no run refused, the previous tag, the release notes read off two
// copies of the index of records, and whether a commit is reachable from main. The last builds a git repository in a
// temporary folder; the rest are pure.
//
//   node --test src/test/docs/release.test.mjs

import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { indexOf, row } from "./adr-index-fixtures.mjs";

const SCRIPT = fileURLToPath(new URL("../../../docs/release.mjs", import.meta.url));

/** The script's exports; a test fails, rather than the file, where the script is not there yet. */
async function release() {
  assert.ok(existsSync(SCRIPT), "docs/release.mjs is missing: ADR-430 §8 adds it");
  return import(SCRIPT);
}

const REPO = "https://github.com/algernon28/vespera/blob";

/** A record's row as the index writes one, its title followed by the italic summary in brackets the index adds. */
const summarised = (n, title) => row(n, `${title} *(amends ADR-001; settles #${n})*`);

/** A manifest as the build writes it, naming `version`. */
const manifest = (version) =>
  ["Manifest-Version: 1.0", "Implementation-Title: vespera", `Implementation-Version: ${version}`, ""].join("\r\n");

/** implementation-versions.properties as the build writes it, `corpus`'s value given. */
const implementationVersions = (corpus) =>
  [`confluence=${"a".repeat(40)}`, `corpus=${corpus}`, `ledger=${"b".repeat(40)}`, ""].join("\n");

/* ---------- the tag ---------- */

test("a v and three numbers is a release tag, its version the numbers", async () => {
  const { versionOfTag } = await release();
  assert.equal(versionOfTag("v2.0.0"), "2.0.0");
  assert.equal(versionOfTag("v0.10.3"), "0.10.3");
});

test("every other v tag is refused, the refusal naming it", async () => {
  const { versionOfTag } = await release();
  for (const tag of ["v1.2", "v2.0.0-rc1", "v02.0.0", "v2.00.0", "v2.0.0.1", "v2.0.0+build", "v", "vx.y.z", "2.0.0"]) {
    assert.throws(() => versionOfTag(tag), (refused) => refused instanceof Error && refused.message.includes(tag), tag);
  }
});

/* ---------- the version built ---------- */

test("only MAJOR.MINOR.PATCH is published, and no SNAPSHOT", async () => {
  const { checkPublishable } = await release();
  assert.doesNotThrow(() => checkPublishable("2.0.0"));
  for (const version of ["0-SNAPSHOT", "2.0.0-SNAPSHOT", "unknown", "", "2.0"]) {
    assert.throws(() => checkPublishable(version), Error, version);
  }
});

test("a jar whose manifest names the tag's version, every module by a commit, is accepted", async () => {
  const { checkBuilt } = await release();
  assert.doesNotThrow(() => checkBuilt("2.0.0", manifest("2.0.0"), implementationVersions("c".repeat(40))));
});

test("a jar whose manifest names another version, or a SNAPSHOT, is refused", async () => {
  const { checkBuilt } = await release();
  const commits = implementationVersions("c".repeat(40));
  assert.throws(() => checkBuilt("2.0.0", manifest("0-SNAPSHOT"), commits), /0-SNAPSHOT/);
  assert.throws(() => checkBuilt("2.0.0", manifest("2.0.1"), commits), /2\.0\.1/);
  assert.throws(() => checkBuilt("2.0.0", "Manifest-Version: 1.0\r\n", commits), Error);
});

test("a jar with a module whose implementation version is unknown, which could mint no run, is refused", async () => {
  const { checkBuilt } = await release();
  assert.throws(() => checkBuilt("2.0.0", manifest("2.0.0"), implementationVersions("unknown")), /corpus/);
});

/* ---------- the previous tag ---------- */

test("the previous tag of 2.0.0 is v1.2, read as 1.2.0, and pre-220 is passed over", async () => {
  const { previousTag } = await release();
  assert.equal(previousTag("v2.0.0", ["pre-220", "v1.2", "v2.0.0"]), "v1.2");
});

test("the previous tag is the greatest release tag below, by number and not by text", async () => {
  const { previousTag } = await release();
  const tags = ["v1.2", "v2.0.0", "v2.0.1", "v2.9.0", "v2.10.0", "v3.0.0", "v2.11.0-rc1"];
  assert.equal(previousTag("v2.10.0", tags), "v2.9.0");
  assert.equal(previousTag("v2.11.0", tags), "v2.10.0");
  assert.equal(previousTag("v2.0.1", tags), "v2.0.0");
});

test("the first release has no previous tag", async () => {
  const { previousTag } = await release();
  assert.equal(previousTag("v2.0.0", ["pre-220", "v2.0.0"]), null);
});

/* ---------- the notes ---------- */

test("the notes list the records the index holds at the tag and not at the previous one, by number, titles alone", async () => {
  const { releaseNotes } = await release();
  const previousIndex = indexOf([summarised(427, "An older record")]);
  const currentIndex = indexOf([
    summarised(427, "An older record"),
    summarised(430, "The record numbered 430"),
    summarised(429, "The record numbered 429"),
  ]);
  assert.equal(
    releaseNotes({ tag: "v2.0.0", previous: "v1.2", currentIndex, previousIndex }),
    [
      "Decision records merged since v1.2:",
      "",
      `- [ADR-429](${REPO}/v2.0.0/docs/adr/0429-the-record-numbered-429.md): The record numbered 429`,
      `- [ADR-430](${REPO}/v2.0.0/docs/adr/0430-the-record-numbered-430.md): The record numbered 430`,
      "",
    ].join("\n"),
  );
});

test("with no previous tag, the notes list every record the index holds", async () => {
  const { releaseNotes } = await release();
  const currentIndex = indexOf([summarised(429, "The record numbered 429")], "\n", [1]);
  assert.equal(
    releaseNotes({ tag: "v2.0.0", previous: null, currentIndex, previousIndex: null }),
    [
      "Decision records in this release:",
      "",
      `- [ADR-001](${REPO}/v2.0.0/docs/adr/0001-the-record-numbered-1.md): The record numbered 1`,
      `- [ADR-429](${REPO}/v2.0.0/docs/adr/0429-the-record-numbered-429.md): The record numbered 429`,
      "",
    ].join("\n"),
  );
});

test("where no record is new, the notes say so in one line", async () => {
  const { releaseNotes } = await release();
  const index = indexOf([summarised(427, "An older record")]);
  assert.equal(
    releaseNotes({ tag: "v2.0.1", previous: "v2.0.0", currentIndex: index, previousIndex: index }),
    "No decision record was merged since v2.0.0.\n",
  );
});

/* ---------- main ---------- */

const git = (cwd, ...args) => {
  const ran = spawnSync("git", args, { cwd, encoding: "utf8" });
  assert.equal(ran.status, 0, `git ${args.join(" ")}: ${ran.stderr}`);
  return ran.stdout.trim();
};

test("a commit of main is reachable from main, and one only on another branch is not", async () => {
  const { reachableFromMain } = await release();
  const repo = mkdtempSync(join(tmpdir(), "release-"));
  git(repo, "init", "-q", "-b", "main");
  git(repo, "config", "user.email", "release-test@example.invalid");
  git(repo, "config", "user.name", "release test");
  writeFileSync(join(repo, "a.txt"), "a\n");
  git(repo, "add", "a.txt");
  git(repo, "commit", "-q", "-m", "on main");
  const onMain = git(repo, "rev-parse", "HEAD");
  git(repo, "checkout", "-q", "-b", "elsewhere");
  writeFileSync(join(repo, "b.txt"), "b\n");
  git(repo, "add", "b.txt");
  git(repo, "commit", "-q", "-m", "elsewhere");
  const elsewhere = git(repo, "rev-parse", "HEAD");

  assert.equal(reachableFromMain(onMain, { cwd: repo, main: "main" }), true);
  assert.equal(reachableFromMain(elsewhere, { cwd: repo, main: "main" }), false);
});
