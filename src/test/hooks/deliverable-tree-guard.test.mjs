// The private-paths guard, held to docs/adr/0283: a deliverable tree is read as outside the allow list
// wherever it lies. A folder that directly holds vespera-deliverable.txt, the marker every tree Vespera
// draws carries at its top (docs/adr/0276 section 3), is the top of a tree, and every path at or beneath
// it that the allow list names is refused as a path outside the list is: Glob and a command of listers
// are let through, and nothing that reads a file's content or may write.
//
//   node --test src/test/hooks/deliverable-tree-guard.test.mjs
//
// docs/adr/0215 section 7: no agent writes into a .claude folder, so the guard that holds ADR-283 is a
// draft in docs/agents/guard-draft, laid out as .claude is, until the operator installs it and removes the
// draft in the same change. The guard held here is VESPERA_GUARD_DRAFT's when that names a folder that
// holds one, else the draft's while the draft is there, else this checkout's installed one. So the cases
// hold the draft now and the installed guard once the draft is gone; with the draft moved aside they hold
// the installed guard, which ADR-283 does not bind until the operator installs it.
//
// Every case goes through the wrapper Claude Code calls, .claude/hooks/run-private-paths-guard.sh, with
// the tool call as JSON on stdin, and claims one exit code: 0 lets the call through, 2 refuses it. A shell
// command names the fixture by ${TMPDIR}, since the guard does not read a rooted POSIX path there. Nothing
// here names a path of the operator's: the guard is copied into a checkout this test builds under the
// system temp folder, whose child is given a TEMP and a home folder of the test's own, so ${TEMP} in the
// shipped allow list is the fixture's temp/.
//
// The fixture tree, under one temp folder:
//
//   checkout/              ${REPO}: the wrapper, the guard and the allow list
//   temp/                  ${TEMP}
//     work/                a working directory: vespera.db, report.html, and the tree drawn in it,
//                          deliverable/run/, in the flat layout: the marker, index.html, documents.csv
//                          and documents/0001-report.pdf
//     named/               a tree drawn at a named root, per cluster: the marker, index.html,
//                          documents.csv, 01-partition/01-cluster/index.html and report.pdf
//     beside/note.txt      a folder beside the named root, holding no marker
//     earlier/deliverable/old/index.html, a tree drawn before the marker, which carries none
//     odd/                 whose vespera-deliverable.txt is a folder, and note.txt
//     links/to-named       a junction (Windows) or symlink to named/
//   home/                  ${HOME}
//   outside/named/         a tree at a named root under no allowed root: the marker and index.html

import { after, test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, realpathSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ALLOWED = 0;
const REFUSED = 2;

const windows = process.platform === "win32";
const fwd = (p) => p.replace(/\\/g, "/");
const repo = fwd(resolve(dirname(fileURLToPath(import.meta.url)), "..", "..", ".."));

const WRAPPER = "hooks/run-private-paths-guard.sh";
const GUARD = "hooks/private-paths-guard.mjs";
const ALLOW_LIST = "allowed-paths.txt";
// DeliverableRoot.MARKER_FILE_NAME, which TheGuardKnowsATreeByItsMarkerTest holds the guard to.
const MARKER = "vespera-deliverable.txt";

// The folder each of the guard's files is held from: VESPERA_GUARD_DRAFT's, then the draft's, then the
// installed .claude, each only where it holds that file. A named draft that is not there stops the run.
const DRAFT = process.env.VESPERA_GUARD_DRAFT ? fwd(resolve(process.env.VESPERA_GUARD_DRAFT)) : null;
if (DRAFT && !existsSync(DRAFT)) throw new Error(`VESPERA_GUARD_DRAFT names ${DRAFT}, which is not there`);
const sources = [DRAFT, `${repo}/docs/agents/guard-draft`, `${repo}/.claude`].filter(Boolean);
const held = (file) => `${sources.find((folder) => existsSync(`${folder}/${file}`)) ?? `${repo}/.claude`}/${file}`;

/* ---------- the fixture ---------- */

const base = fwd(realpathSync.native(mkdtempSync(join(tmpdir(), "vespera-tree-guard-"))));
after(() => rmSync(base, { recursive: true, force: true, maxRetries: 3 }));

function put(path, text = "") {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, text);
}

const C = `${base}/checkout`;
const T = `${base}/temp`;
const H = `${base}/home`;
const O = `${base}/outside`;
for (const file of [WRAPPER, GUARD, ALLOW_LIST]) {
  mkdirSync(dirname(`${C}/.claude/${file}`), { recursive: true });
  copyFileSync(held(file), `${C}/.claude/${file}`);
}
mkdirSync(H, { recursive: true });

const marker = (workingDirectory) => `working-directory=${workingDirectory}\ngeneration-run=${"c".repeat(64)}\n`;

const WORK = `${T}/work`;
const FLAT = `${WORK}/deliverable/run`;
put(`${WORK}/vespera.db`);
put(`${WORK}/report.html`, "<p>fixture</p>\n");
put(`${FLAT}/${MARKER}`, marker(WORK));
put(`${FLAT}/index.html`, "<p>fixture</p>\n");
put(`${FLAT}/documents.csv`, "copy\n");
put(`${FLAT}/documents/0001-report.pdf`, "fixture\n");

const NAMED = `${T}/named`;
const CLUSTER = `${NAMED}/01-partition/01-cluster`;
put(`${NAMED}/${MARKER}`, marker(WORK));
put(`${NAMED}/index.html`, "<p>fixture</p>\n");
put(`${NAMED}/documents.csv`, "copy\n");
put(`${CLUSTER}/index.html`, "<p>fixture</p>\n");
put(`${CLUSTER}/report.pdf`, "fixture\n");

put(`${T}/beside/note.txt`, "beside a tree, not in one\n");
put(`${T}/earlier/deliverable/old/index.html`, "<p>fixture</p>\n");
mkdirSync(`${T}/odd/${MARKER}`, { recursive: true });
put(`${T}/odd/note.txt`, "a folder whose marker is a folder\n");

put(`${O}/named/${MARKER}`, marker(WORK));
put(`${O}/named/index.html`, "<p>fixture</p>\n");

let linked = true;
try {
  mkdirSync(`${T}/links`, { recursive: true });
  symlinkSync(NAMED, `${T}/links/to-named`, windows ? "junction" : "dir");
} catch {
  linked = false;
}

/* ---------- starting the guard ---------- */

function findBash() {
  if (!windows) return "bash";
  // Git's own bash, not whichever bash.exe PATH offers first: on a Windows runner that can be WSL's.
  try {
    const root = resolve(execFileSync("git", ["--exec-path"], { encoding: "utf8" }).trim(), "..", "..", "..");
    for (const candidate of [join(root, "bin", "bash.exe"), join(root, "usr", "bin", "bash.exe")]) {
      if (existsSync(candidate)) return candidate;
    }
  } catch {
    // fall through
  }
  return "bash";
}
const bash = findBash();

// Environment names are case-insensitive on Windows, so an override replaces the name however it is cased.
function childEnv(overrides) {
  const replaced = new Set(Object.keys(overrides).map((k) => k.toUpperCase()));
  const env = {};
  for (const [k, v] of Object.entries(process.env)) if (!replaced.has(k.toUpperCase())) env[k] = v;
  return { ...env, ...overrides };
}

function startGuard(tool, input, cwd = C) {
  return spawnSync(bash, [`${C}/.claude/${WRAPPER}`], {
    input: JSON.stringify({ session_id: "tree-guard-test", hook_event_name: "PreToolUse", cwd, tool_name: tool, tool_input: input }),
    encoding: "utf8",
    timeout: 120_000,
    windowsHide: true,
    env: childEnv({ CLAUDE_PROJECT_DIR: C, TEMP: T, TMP: T, TMPDIR: T, USERPROFILE: H, HOME: H }),
  });
}

/* ---------- the cases ---------- */

// [id, what the call is, tool, input, expected, options]
const CASES = [
  // Every tool that reads content or may write is refused in a tree under an allowed root.
  ["F101", "Read of a named root's index", "Read", { file_path: `${NAMED}/index.html` }, REFUSED],
  ["F102", "Read of a copy in a cluster's folder", "Read", { file_path: `${CLUSTER}/report.pdf` }, REFUSED],
  ["F103", "Read of a cluster's page beside its copies", "Read", { file_path: `${CLUSTER}/index.html` }, REFUSED],
  ["F104", "Read of the marker itself", "Read", { file_path: `${NAMED}/${MARKER}` }, REFUSED],
  ["F105", "Read of a named root's manifest", "Read", { file_path: `${NAMED}/documents.csv` }, REFUSED],
  ["F106", "Read of a copy in the flat layout, in a working directory's tree", "Read", { file_path: `${FLAT}/documents/0001-report.pdf` }, REFUSED],
  ["F107", "Read of a working directory's tree's index", "Read", { file_path: `${FLAT}/index.html` }, REFUSED],
  ["F108", "Write of a file that is not there yet, in a cluster's folder", "Write", { file_path: `${CLUSTER}/new.txt`, content: "x" }, REFUSED],
  ["F109", "Edit of a named root's index", "Edit", { file_path: `${NAMED}/index.html`, old_string: "a", new_string: "b" }, REFUSED],
  ["F110", "Grep whose path is the top of a tree", "Grep", { pattern: "x", path: NAMED }, REFUSED],
  ["F111", "Grep from the temp folder whose glob names a tree", "Grep", { pattern: "x", path: T, glob: "named/**" }, REFUSED],
  ["F112", "cat of a named root's index", "Bash", { command: "cat ${TMPDIR}/named/index.html" }, REFUSED],
  ["F113", "cd to the top of a tree, then cat of its index", "Bash", { command: "cd ${TMPDIR}/named && cat index.html" }, REFUSED],
  ["F114", "rm of the marker", "Bash", { command: `rm \${TMPDIR}/named/${MARKER}` }, REFUSED],
  ["F115", "cat of a relative copy from a partition's folder", "Bash", { command: "cat 01-cluster/report.pdf" }, REFUSED, { cwd: `${NAMED}/01-partition` }],
  ["F116", "Read beside a marker that is a folder: any name there is a marker", "Read", { file_path: `${T}/odd/note.txt` }, REFUSED],
  // What prints names is let through, as it is outside the allow list.
  ["F201", "Glob beneath a tree", "Glob", { pattern: "**/*", path: NAMED }, ALLOWED],
  ["F202", "ls of a cluster's folder", "Bash", { command: "ls ${TMPDIR}/named/01-partition/01-cluster" }, ALLOWED],
  ["F203", "stat of the marker", "Bash", { command: `stat \${TMPDIR}/named/${MARKER}` }, ALLOWED],
  // What is not in a tree stays as it was.
  ["F301", "Read of a working directory's page, beside its tree", "Read", { file_path: `${WORK}/report.html` }, ALLOWED],
  ["F302", "Read beside a named root", "Read", { file_path: `${T}/beside/note.txt` }, ALLOWED],
  ["F303", "Read of a tree drawn before the marker, which carries none", "Read", { file_path: `${T}/earlier/deliverable/old/index.html` }, ALLOWED],
  ["F304", "Grep whose path is a working directory above its tree, which is not walked", "Grep", { pattern: "x", path: WORK }, ALLOWED],
  ["F305", "cat of a working directory's page", "Bash", { command: "cat ${TMPDIR}/work/report.html" }, ALLOWED],
  // A link, and a tree outside the allow list.
  ["F401", "Read through a link that leads into a tree", "Read", { file_path: `${T}/links/to-named/index.html` }, REFUSED, { needsLink: true }],
  ["F402", "Read of a tree under no allowed root", "Read", { file_path: `${O}/named/index.html` }, REFUSED],
  ["F403", "ls of a tree under no allowed root", "Bash", { command: "ls ${TMPDIR}/../outside/named" }, ALLOWED],
];

for (const [id, what, tool, input, expected, { cwd, needsLink } = {}] of CASES) {
  const skip = needsLink && !linked ? "a link could not be made here" : false;
  test(`${id}: ${what}`, { skip }, () => {
    const result = startGuard(tool, input, cwd);
    const said = (code) => (code === ALLOWED ? "let the call through (exit 0)" : code === REFUSED ? "refuse it (exit 2)" : `exit ${code}`);
    assert.equal(
      result.status,
      expected,
      `${tool} ${JSON.stringify(input)}: the guard must ${said(expected)}, and it ended with ${said(result.status)}` +
        (result.error ? `; it could not be started: ${result.error.message}` : "") +
        `. What it wrote: ${(result.stderr || "").trim() || "(nothing)"}`,
    );
  });
}

test("F501: the refusal names the tree and the record", () => {
  const result = startGuard("Read", { file_path: `${CLUSTER}/report.pdf` });
  assert.match(result.stderr, /in the deliverable tree .*named.*read as outside the allow list/, "the refusal must say the path is in a deliverable tree");
  assert.match(result.stderr, /docs\/adr\/0283/, "the refusal must cite the record that closes a tree");
});
