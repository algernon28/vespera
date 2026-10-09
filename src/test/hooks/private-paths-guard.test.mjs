// The private-paths guard, held to its record: docs/adr/0196. No agent reads the operator's documents,
// and a PreToolUse hook refuses any path outside an allow list and fails closed. docs/adr/0201 amends
// that record, and the L and G cases are held to it. docs/adr/0212 admits one exact command
// that starts the counting script beside the guard on a working directory, and the K cases are held to
// it.
//
//   node --test src/test/hooks/private-paths-guard.test.mjs
//
// Every case goes through the entry point Claude Code calls, .claude/hooks/run-private-paths-guard.sh,
// with the tool call as JSON on stdin, and claims one exit code: 0 lets the call through, 2 refuses it.
//
// Nothing here names a path of the operator's. The guard's three files are copied, byte for byte, into
// a checkout this test builds under the system temp folder, and that copy is what is started. The child
// is given a TEMP and a home folder of the test's own, so ${REPO}, ${TEMP} and ${HOME} in the shipped
// allow list all land inside the fixture, and a machine's own .claude/allowed-paths.local.txt cannot
// change a result. "Outside the allow list" is a folder beside those, or a drive letter nothing is
// mounted on. A working directory is a fixture folder holding an empty vespera.db or vespera.lock.
//
// The fixture tree, under one temp folder:
//
//   checkout/            ${REPO}: the guard's files, README.md, src/Example.java. Holds no working directory.
//   temp/                ${TEMP}
//     scratch/note.txt
//     working-directory/ vespera.db, report.html, deliverable/run/index.md
//     locked/            vespera.lock, vespera.log
//     deep/note.txt      and deep/a/b/vespera.lock, a working directory two folders further down
//     links/out          a junction (Windows) or symlink to outside/
//     links/to-working-directory
//     links/inside       to scratch/, a folder inside the allow list
//     links/in           to deep/a, so that links/in/.. is deep/ to whatever follows the link
//     links/via          to links/in, and links/hop to links/to-working-directory: a link to a link
//     links/nowhere      to outside/not-there, which does not exist
//     links/loop         to itself
//     my runs/           vespera.db, report.html: a working directory whose name holds a space
//     built/note.txt     and working directories only inside built/target, built/node_modules, built/.git
//   home/                ${HOME}: .m2/settings.xml (allowed), Documents/x.txt (not),
//                        .jdks/ (allowed) holding 10,050 empty folders
//   outside/doc.txt      under no allowed root
//   outside/counted/     vespera.db: a working directory under no allowed root
//   temp/scratch/working-directory-counts.mjs, a copy of the counting script outside .claude/hooks
//   altered-counts/checkout/ a ${REPO} whose counting script has one line the pinned one does not
//   crlf-counts/checkout/ a ${REPO} whose counting script has every LF turned into CR LF
//   with space/checkout/ a second ${REPO}, whose path holds a space
//   no-guard/checkout/   the wrapper without private-paths-guard.mjs
//   main/                a git checkout whose worktree, .claude/worktrees/wt, has no copy of the hook

import { after, test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import {
  copyFileSync,
  existsSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ALLOWED = 0;
const REFUSED = 2;

const windows = process.platform === "win32";
const fwd = (p) => p.replace(/\\/g, "/");
const back = (p) => p.replace(/\//g, "\\");
// C:/Users/x is /c/Users/x to Git Bash.
const gitBash = (p) => p.replace(/^([A-Za-z]):/, (_, drive) => "/" + drive.toLowerCase());

const repo = fwd(resolve(dirname(fileURLToPath(import.meta.url)), "..", "..", ".."));
const HOOKS = ".claude/hooks";
const WRAPPER = `${HOOKS}/run-private-paths-guard.sh`;
const GUARD = `${HOOKS}/private-paths-guard.mjs`;
const ALLOW_LIST = ".claude/allowed-paths.txt";
// The counting script of docs/adr/0212, which the guard admits by one exact command. Copied into a
// fixture checkout only when the repository has one: without it, the K1 cases are refused.
const COUNTS = `${HOOKS}/working-directory-counts.mjs`;
const countsShipped = existsSync(`${repo}/${COUNTS}`);
const countsText = () => (countsShipped ? readFileSync(`${repo}/${COUNTS}`, "utf8") : "// a stand-in: the counting script is not built yet\n");

/* ---------- the fixture ---------- */

// realpath, so a short 8.3 name or a symlinked temp folder does not make two spellings of one path.
const base = fwd(realpathSync.native(mkdtempSync(join(tmpdir(), "vespera-guard-"))));
after(() => rmSync(base, { recursive: true, force: true, maxRetries: 3 }));

function put(path, text = "") {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, text);
}

function buildCheckout(dir, { guard = true, wrapper = true } = {}) {
  mkdirSync(`${dir}/${HOOKS}`, { recursive: true });
  if (wrapper) copyFileSync(`${repo}/${WRAPPER}`, `${dir}/${WRAPPER}`);
  if (guard) copyFileSync(`${repo}/${GUARD}`, `${dir}/${GUARD}`);
  if (countsShipped) copyFileSync(`${repo}/${COUNTS}`, `${dir}/${COUNTS}`);
  copyFileSync(`${repo}/${ALLOW_LIST}`, `${dir}/${ALLOW_LIST}`);
  put(`${dir}/README.md`, "# fixture\n");
  put(`${dir}/src/Example.java`, "class Example {}\n");
  return dir;
}

const C = buildCheckout(`${base}/checkout`);
const T = `${base}/temp`;
const H = `${base}/home`;
const O = `${base}/outside`;
const WD = `${T}/working-directory`;
const LOCKED = `${T}/locked`;
const DEEP = `${T}/deep`;
const LINKS = `${T}/links`;
const SPACED = buildCheckout(`${base}/with space/checkout`);
const NO_GUARD = buildCheckout(`${base}/no-guard/checkout`, { guard: false });

put(`${T}/scratch/note.txt`, "scratch\n");
put(`${WD}/vespera.db`);
put(`${WD}/report.html`, "<p>fixture</p>\n");
put(`${WD}/deliverable/run/index.md`, "# fixture\n");
put(`${LOCKED}/vespera.lock`);
put(`${LOCKED}/vespera.log`, "fixture\n");
put(`${DEEP}/note.txt`, "beside a working directory, not in one\n");
put(`${DEEP}/a/b/vespera.lock`);
put(`${H}/.m2/settings.xml`, "<settings/>\n");
put(`${H}/Documents/x.txt`, "fixture\n");
put(`${O}/doc.txt`, "fixture\n");
put(`${DEEP}/a/b/notes.txt`, "fixture\n");
put(`${T}/my runs/vespera.db`);
put(`${T}/my runs/report.html`, "<p>fixture</p>\n");
put(`${C}/my notes/readme.txt`, "fixture\n");
put(`${C}/src/main/App.java`, "class App {}\n");
// docs/adr/0212: a working directory under no allowed root, which the counting command may name; a copy
// of the counting script where the guard does not admit it; and a checkout whose counting script differs
// from the pinned one by one line.
put(`${O}/counted/vespera.db`);
put(`${T}/scratch/working-directory-counts.mjs`, countsText());
const ALTERED = buildCheckout(`${base}/altered-counts/checkout`);
writeFileSync(`${ALTERED}/${COUNTS}`, countsText() + "\n// one line the pinned script does not have\n");
// And a checkout whose counting script has every LF turned into CR LF, as git checks it out on Windows:
// the pin reads CR LF as LF, so it is the same script.
const CRLF = buildCheckout(`${base}/crlf-counts/checkout`);
writeFileSync(`${CRLF}/${COUNTS}`, countsText().replace(/\r\n/g, "\n").replace(/\n/g, "\r\n"));
// Under temp/mirror, the temp folder's own path from its drive's root, ending in a working directory:
// where C:Users/.../temp/scratch lands when it is read as a relative path from temp/mirror.
if (windows) put(`${T}/mirror/${T.slice(3)}/scratch/vespera.db`);
else mkdirSync(`${T}/mirror`, { recursive: true });
// Working directories only inside the three folders the walk down from a search root never enters.
put(`${T}/built/note.txt`, "fixture\n");
put(`${T}/built/target/run/vespera.db`);
put(`${T}/built/node_modules/package/vespera.lock`);
put(`${T}/built/.git/vespera.lock`);
// More folders than the walk down looks at, 10,000, under an allowed root that holds no working
// directory. Built for real: a switch that lowered the bound for a test would be a way round it.
const WIDE = `${H}/.jdks`;
const MORE_FOLDERS_THAN_THE_WALK_LOOKS_AT = 10_050;
mkdirSync(WIDE, { recursive: true });
for (let i = 0; i < MORE_FOLDERS_THAN_THE_WALK_LOOKS_AT; i++) mkdirSync(`${WIDE}/f${i}`);
// The guard reads a command's relative paths against no more than 32 folders, the current directory
// among them, and in no more than 20,000 readings: one reading is one relative token against one folder.
const FOLDERS_READ_AGAINST = 32;
const READINGS_AT_MOST = 20_000;
const NAMED_FOLDERS = Array.from({ length: FOLDERS_READ_AGAINST }, (_, i) => `many/d${String(i).padStart(2, "0")}`);
for (const folder of NAMED_FOLDERS) mkdirSync(`${C}/${folder}`, { recursive: true });
// The current directory and these make 32, the most that is read against.
const AS_MANY_FOLDERS_AS_ARE_READ = NAMED_FOLDERS.slice(0, FOLDERS_READ_AGAINST - 1).join(" ");
const ONE_FOLDER_TOO_MANY = NAMED_FOLDERS.join(" ");
const plainWords = (count) => Array.from({ length: count }, (_, i) => `w${i}`).join(" ");
// echo, 31 folders and 630 words are 662 relative tokens, and against 32 folders that is 21,184 readings.
const WORDS_PAST_THE_READINGS = 630;
// With 100 words it is 132 tokens and 4,224 readings.
const WORDS_WITHIN_THE_READINGS = 100;
if ((1 + FOLDERS_READ_AGAINST - 1 + WORDS_PAST_THE_READINGS) * FOLDERS_READ_AGAINST <= READINGS_AT_MOST) {
  throw new Error("the fixture no longer passes the bound on readings it is there to pass");
}
mkdirSync(`${base}/empty-bin`, { recursive: true });
mkdirSync(LINKS, { recursive: true });

// A junction needs no privilege on Windows; a symlink needs none elsewhere. The backslash separates
// folders on Windows only: anywhere else it is a character of a name, and a link made from such a path
// is one oddly named file in the current directory, pointing nowhere, made without complaint.
const native = (p) => (windows ? back(p) : p);
let linkable = true;
try {
  symlinkSync(native(O), native(`${LINKS}/out`), windows ? "junction" : "dir");
  symlinkSync(native(WD), native(`${LINKS}/to-working-directory`), windows ? "junction" : "dir");
} catch {
  linkable = false;
}
// A link that was made and does not lead where it should is no fixture: the cases that need it are
// not started, so none of them holds or fails for a reason that is not the guard's.
if (!existsSync(`${LINKS}/out/doc.txt`) || !existsSync(`${LINKS}/to-working-directory/vespera.db`)) linkable = false;
const NO_LINK = "this platform would not create a junction or a symlink that leads where it should";

// The links of the L cases, kept apart from the two above so that R503 to R505 are started wherever they
// were before. Two of them lead nowhere on purpose: one to a name that is not there, one to itself.
const isLink = (p) => {
  try {
    return lstatSync(native(p)).isSymbolicLink();
  } catch {
    return false;
  }
};
// A link to the temp folder, for a guard whose ${TEMP} is itself a link: the allowed root is then the
// link, and the folder it leads to is where a walked path lands.
const LINKED_TEMP = `${base}/linked-temp`;
let linkedFurther = linkable;
if (linkedFurther) {
  try {
    for (const [name, target] of [
      ["inside", `${T}/scratch`],
      ["in", `${DEEP}/a`],
      ["via", `${LINKS}/in`],
      ["hop", `${LINKS}/to-working-directory`],
      ["nowhere", `${O}/not-there`],
      ["loop", `${LINKS}/loop`],
    ]) {
      symlinkSync(native(target), native(`${LINKS}/${name}`), windows ? "junction" : "dir");
    }
    symlinkSync(native(T), native(LINKED_TEMP), windows ? "junction" : "dir");
  } catch {
    linkedFurther = false;
  }
}
if (
  !existsSync(`${LINKS}/inside/note.txt`) ||
  !existsSync(`${LINKS}/in/b/notes.txt`) ||
  !existsSync(`${LINKS}/via/b/notes.txt`) ||
  !existsSync(`${LINKS}/hop/vespera.db`) ||
  !isLink(`${LINKS}/nowhere`) ||
  existsSync(`${LINKS}/nowhere`) ||
  !isLink(`${LINKS}/loop`) ||
  existsSync(`${LINKS}/loop`) ||
  !existsSync(`${LINKED_TEMP}/scratch/note.txt`)
) {
  linkedFurther = false;
}

// A quoted sentence is read whole as one relative path. A file system holds no name longer than 255
// characters, and asked about one it does not answer "not there" everywhere: it may say the name is too long.
const LONGEST_NAME_A_FILE_SYSTEM_HOLDS = 255;
const SENTENCE_LONGER_THAN_A_NAME = "The guard reads a quoted sentence whole, and a long one is still a sentence. ".repeat(5).trim();
if (SENTENCE_LONGER_THAN_A_NAME.length <= LONGEST_NAME_A_FILE_SYSTEM_HOLDS || /[\\/]/.test(SENTENCE_LONGER_THAN_A_NAME)) {
  throw new Error("the fixture sentence is no longer one name longer than a file system holds");
}

// A wrapper that ends with a given exit code before it reaches its own mapping of exit codes.
function checkoutWhoseWrapperExits(code) {
  const dir = buildCheckout(`${base}/wrapper-exits-${code}/checkout`);
  writeFileSync(`${dir}/${WRAPPER}`, `#!/usr/bin/env bash\nexit ${code}\n`);
  return dir;
}

// A git checkout holding the hook untracked, and a worktree of it that therefore has none.
const MAIN = `${base}/main`;
const WORKTREE = `${MAIN}/.claude/worktrees/wt`;
let worktreeBuilt = true;
try {
  mkdirSync(MAIN, { recursive: true });
  const git = (...args) =>
    execFileSync(
      "git",
      ["-c", "user.name=fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false", ...args],
      { cwd: MAIN, stdio: "pipe" },
    );
  git("init", "-q");
  put(`${MAIN}/README.md`, "# fixture\n");
  git("add", "README.md");
  git("commit", "-q", "-m", "fixture");
  buildCheckout(MAIN);
  git("worktree", "add", "-q", "--detach", ".claude/worktrees/wt");
  if (existsSync(`${WORKTREE}/${WRAPPER}`)) worktreeBuilt = false;
} catch {
  worktreeBuilt = false;
}
const NO_WORKTREE = "git could not build the fixture worktree here";

/* ---------- starting the guard ---------- */

function findBash() {
  if (!windows) {
    try {
      return execFileSync("/bin/sh", ["-c", "command -v bash"], { encoding: "utf8" }).trim() || "bash";
    } catch {
      return "bash";
    }
  }
  // Git's own bash, not whichever bash.exe PATH offers first: on a Windows runner that can be WSL's.
  try {
    const execPath = execFileSync("git", ["--exec-path"], { encoding: "utf8" }).trim();
    const root = resolve(execPath, "..", "..", "..");
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

// The command string registered in .claude/settings.json, exactly as committed, as a script bash runs.
const settings = JSON.parse(readFileSync(`${repo}/.claude/settings.json`, "utf8"));
const registration = (settings.hooks?.PreToolUse ?? []).find((entry) =>
  (entry.hooks ?? []).some((h) => String(h.command).includes("run-private-paths-guard")),
);
const registeredCommand = registration?.hooks.find((h) => String(h.command).includes("run-private-paths-guard"))?.command;
const REGISTERED = `${base}/registered-hook-command.sh`;
writeFileSync(REGISTERED, (registeredCommand ?? "exit 97") + "\n");

function startGuard({ projectDir = C, script, stdin, env = {} }) {
  return spawnSync(bash, [script ?? `${projectDir}/${WRAPPER}`], {
    input: stdin,
    encoding: "utf8",
    timeout: 120_000,
    windowsHide: true,
    env: childEnv({
      CLAUDE_PROJECT_DIR: projectDir,
      TEMP: T,
      TMP: T,
      TMPDIR: T,
      USERPROFILE: H,
      HOME: H,
      VESPERA_GUARD_FIXTURE: O,
      ...env,
    }),
  });
}

const hookInput = (tool, input, cwd) =>
  JSON.stringify({ session_id: "guard-test", hook_event_name: "PreToolUse", cwd, tool_name: tool, tool_input: input });

function claim(result, expected, what) {
  const said = (code) => (code === ALLOWED ? "let the call through (exit 0)" : code === REFUSED ? "refuse it (exit 2)" : `exit ${code}`);
  assert.equal(
    result.status,
    expected,
    `${what}: the guard must ${said(expected)}, and it ended with ${said(result.status)}` +
      (result.error ? `; it could not be started: ${result.error.message}` : "") +
      `. What it wrote: ${(result.stderr || "").trim() || "(nothing)"}`,
  );
}

/* ---------- the cases ---------- */

const ONLY_WINDOWS = {
  drive: "a drive letter is a Windows path form",
  gitBash: "the /d/... form is Git Bash's, which is on Windows only",
  shellAbsolute: "the record decides what a shell command's absolute path is in its Windows and Git Bash forms only",
  powerShell: "PowerShell's forms use the backslash, which separates folders on Windows only",
};

const NO_DRIVE = "Q:/no-such-folder/x";
// The letter and colon of the drive the fixture is on, as C: is. T.slice(3) is then the temp folder's
// path from that drive's root. Only the cases marked for Windows use either.
const ON_THE_FIXTURES_DRIVE = base.slice(0, 2);

// id, what the call is, the tool, its input, and the exit code claimed. cwd is the checkout unless given.
const cases = [
  /* The repository and the other allowed roots stay usable. */
  ["A01", "Read of a repository file", "Read", { file_path: `${C}/README.md` }, ALLOWED],
  ["A02", "Read of a repository file written the Git Bash way", "Read", { file_path: `${gitBash(C)}/README.md` }, ALLOWED, { windows: ONLY_WINDOWS.gitBash }],
  ["A03", "Read of a repository file by a relative path", "Read", { file_path: "src/Example.java" }, ALLOWED],
  ["A04", "Read of a repository file written with backslashes", "Read", { file_path: back(`${C}/README.md`) }, ALLOWED, { windows: ONLY_WINDOWS.drive }],
  ["A05", "Edit of a repository file", "Edit", { file_path: `${C}/README.md`, old_string: "a", new_string: "b" }, ALLOWED],
  ["A06", "Write of a log under the temp folder", "Write", { file_path: `${T}/scratch/out.log`, content: "x" }, ALLOWED],
  ["A07", "NotebookEdit of a notebook in the repository", "NotebookEdit", { notebook_path: `${C}/notes.ipynb`, new_source: "x" }, ALLOWED],
  ["A08", "Read under the home folder's .m2, which the allow list names", "Read", { file_path: `${H}/.m2/settings.xml` }, ALLOWED],
  ["A09", "Grep with an explicit path inside the repository", "Grep", { pattern: "class", path: `${C}/src` }, ALLOWED],
  ["A10", "Grep of the repository with a glob that stays in it", "Grep", { pattern: "class", path: C, glob: "**/*.java" }, ALLOWED],
  ["A11", "Glob with an explicit path inside the repository", "Glob", { pattern: "**/*.java", path: C }, ALLOWED],
  ["A12", "Grep with no path, with the repository as the current directory", "Grep", { pattern: "class" }, ALLOWED],
  ["A13", "Glob with no path and a relative pattern, with the repository as the current directory", "Glob", { pattern: "src/**/*.java" }, ALLOWED],
  ["A14", "Glob with an absolute pattern inside the repository", "Glob", { pattern: `${C}/src/**/*.java` }, ALLOWED],
  ["A15", "Grep of a temp folder that holds no working directory", "Grep", { pattern: "scratch", path: `${T}/scratch` }, ALLOWED],
  ["A16", "Read of a file beside a working directory and not in one", "Read", { file_path: `${DEEP}/note.txt` }, ALLOWED],

  /* Shell commands that name nothing private stay usable. */
  ["B01", "gh naming a repository by owner and name", "Bash", { command: "gh pr view 426 --repo algernon28/vespera --json body --jq .body" }, ALLOWED],
  ["B02", "curl of an https URL, whose // is not a path", "Bash", { command: "curl -s https://example.com/x" }, ALLOWED],
  ["B03", "curl of an http URL with a port", "Bash", { command: "curl -s http://localhost:5001/health" }, ALLOWED],
  ["B04", "Maven logging to a quoted path under the temp folder", "Bash", { command: `./mvnw -q -o test > "${T}/mvn.log" 2>&1; echo exit=$?` }, ALLOWED],
  ["B05", "a redirect to /dev/null", "Bash", { command: "git status --porcelain > /dev/null 2>&1" }, ALLOWED],
  ["B06", "cat of a repository file by a relative path", "Bash", { command: "cat README.md" }, ALLOWED],
  ["B07", "cat of a repository file by a ./ path", "Bash", { command: "cat ./src/Example.java" }, ALLOWED],
  ["B08", "cat of a relative path that stays under the temp folder", "Bash", { command: "cat scratch/note.txt" }, ALLOWED, { cwd: T }],
  ["B09", "cat of a repository file written the Git Bash way", "Bash", { command: `cat ${gitBash(C)}/README.md` }, ALLOWED, { windows: ONLY_WINDOWS.gitBash }],
  ["B10", "allowed paths followed by a comma and a full stop, as in a sentence", "Bash", { command: `echo see ${C}/README.md, and ${C}/src/Example.java.` }, ALLOWED],
  ["B11", "Get-Content of a repository file written with backslashes", "PowerShell", { command: `Get-Content ${back(C)}\\README.md` }, ALLOWED, { windows: ONLY_WINDOWS.powerShell }],
  ["B12", "ls under ~/.m2", "Bash", { command: "ls ~/.m2/repository" }, ALLOWED],
  ["B13", "git diff over a range written with two dots", "Bash", { command: "git diff HEAD~3..HEAD -- src/Example.java" }, ALLOWED],
  ["B14", "git log over a range between two branch names", "Bash", { command: "git log main..claude/private-paths-guard --oneline" }, ALLOWED],
  ["B15", "git show of a revision and a path joined by a colon", "Bash", { command: "git show HEAD:README.md" }, ALLOWED],
  ["B16", "git log with a colon in its format", "Bash", { command: "git log -1 --format=%h:%s" }, ALLOWED],
  ["B17", "sed over a repository file", "Bash", { command: "sed -n 1,5p README.md" }, ALLOWED],
  ["B18", "Get-Content under $env:USERPROFILE\\.m2", "PowerShell", { command: String.raw`Get-Content $env:USERPROFILE\.m2\settings.xml` }, ALLOWED, { windows: ONLY_WINDOWS.powerShell }],
  ["B19", "cat under $USERPROFILE/.m2", "Bash", { command: "cat $USERPROFILE/.m2/settings.xml" }, ALLOWED],

  /* A file tool's path outside the allow list. */
  ["D01", "Read by a relative .. path that leaves the repository", "Read", { file_path: "../outside/doc.txt" }, REFUSED],
  ["D02", "Read of a file under no allowed root", "Read", { file_path: `${O}/doc.txt` }, REFUSED],
  ["D03", "Read on a drive no allowed root is on", "Read", { file_path: NO_DRIVE }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["D04", "Read on such a drive, written with backslashes", "Read", { file_path: back(NO_DRIVE) }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["D05", "Read on such a drive, written the Git Bash way", "Read", { file_path: gitBash(NO_DRIVE) }, REFUSED, { windows: ONLY_WINDOWS.gitBash }],
  ["D06", "Write under no allowed root", "Write", { file_path: `${O}/new.txt`, content: "x" }, REFUSED],
  ["D07", "Edit under no allowed root", "Edit", { file_path: `${O}/doc.txt`, old_string: "a", new_string: "b" }, REFUSED],
  ["D08", "NotebookEdit under no allowed root", "NotebookEdit", { notebook_path: `${O}/notes.ipynb`, new_source: "x" }, REFUSED],
  ["D09", "Read under the home folder's Documents, which the allow list does not name", "Read", { file_path: `${H}/Documents/x.txt` }, REFUSED],
  ["D10", "Read of the repository's parent, written <repository>/..", "Read", { file_path: `${C}/..` }, REFUSED],

  /* A working directory is refused wherever it is, under an allowed root too. */
  ["D11", "Read of a report in a working directory under the temp folder", "Read", { file_path: `${WD}/report.html` }, REFUSED],
  ["D12", "Read of vespera.db itself", "Read", { file_path: `${WD}/vespera.db` }, REFUSED],
  ["D13", "Read of a deliverable page two folders down a working directory", "Read", { file_path: `${WD}/deliverable/run/index.md` }, REFUSED],
  ["D14", "Read of a path in a working directory that does not exist yet", "Read", { file_path: `${WD}/not-written-yet.html` }, REFUSED],
  ["D15", "Read of the log in a folder holding only vespera.lock", "Read", { file_path: `${LOCKED}/vespera.log` }, REFUSED],
  ["D16", "Read by a relative path, with a working directory as the current directory", "Read", { file_path: "report.html" }, REFUSED, { cwd: WD }],
  ["D17", "Grep whose path is a working directory", "Grep", { pattern: "x", path: WD }, REFUSED],
  ["D18", "Glob whose path is a working directory", "Glob", { pattern: "**/*", path: WD }, REFUSED],

  /* Absolute paths written in a shell command. */
  ["E01", "echo of a path on a drive no allowed root is on", "Bash", { command: `echo ${NO_DRIVE}` }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["E02", "cat of such a path, written the Git Bash way", "Bash", { command: `cat ${gitBash(NO_DRIVE)}` }, REFUSED, { windows: ONLY_WINDOWS.gitBash }],
  ["E03", "Get-Content of such a path, written with backslashes", "PowerShell", { command: `Get-Content ${back(NO_DRIVE)}` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["E04", "cat under ~/Documents", "Bash", { command: "cat ~/Documents/x.txt" }, REFUSED],
  ["E05", "cat under $HOME/Documents", "Bash", { command: "cat $HOME/Documents/x.txt" }, REFUSED],
  ["E06", "cat under ${HOME}/Documents", "Bash", { command: "cat ${HOME}/Documents/x.txt" }, REFUSED],
  ["E07", "cat of the log in a working directory, by its absolute path", "Bash", { command: `cat ${WD}/vespera.log` }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["E08", "cat of a file under no allowed root, by its absolute path", "Bash", { command: `cat ${O}/doc.txt` }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["E09", "a refused path followed by a comma, which hides nothing", "Bash", { command: `echo ${NO_DRIVE}, and more` }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["E10", "ls of an absolute path that leaves the repository through ..", "Bash", { command: `ls ${C}/../outside/doc.txt` }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],

  /* 1. A doubled separator after the drive is still a drive path. */
  ["R101", "cat of a drive path with two slashes after the colon", "Bash", { command: "cat Q://no-such-folder/x" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["R102", "type of a drive path with doubled backslashes", "Bash", { command: String.raw`type Q:\\no-such-folder\\x` }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["R103", "Get-Content of a drive path with doubled backslashes", "PowerShell", { command: String.raw`Get-Content Q:\\no-such-folder\\x` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],

  /* 2. The other ways an absolute path is written in a shell command. */
  ["R201", "cat of a /proc/cygdrive path", "Bash", { command: "cat /proc/cygdrive/q/no-such-folder/x" }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["R202", "cat of a UNC path written with slashes", "Bash", { command: "cat //no-such-host/share/x" }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["R203", "Get-Content of a UNC path written with backslashes", "PowerShell", { command: String.raw`Get-Content \\no-such-host\share\x` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["R204", "cd to a bare drive letter, then cat of a relative name", "Bash", { command: "cd Q: && cat x" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["R205", "Get-Content under $env:USERPROFILE\\Documents", "PowerShell", { command: String.raw`Get-Content $env:USERPROFILE\Documents\x.txt` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["R206", "cat under $USERPROFILE/Documents", "Bash", { command: "cat $USERPROFILE/Documents/x.txt" }, REFUSED],
  ["R207", "cat under ${USERPROFILE}/Documents", "Bash", { command: "cat ${USERPROFILE}/Documents/x.txt" }, REFUSED],
  ["R208", "cat under a variable of the environment whose value is outside the allow list", "Bash", { command: "cat $VESPERA_GUARD_FIXTURE/doc.txt" }, REFUSED],

  /* 3. A relative path in a shell command is read against the current directory. */
  ["R301", "cat of a relative path four folders up", "Bash", { command: "cat ../../../../somewhere/doc.txt" }, REFUSED],
  ["R302", "cat of a relative path into a folder beside the repository", "Bash", { command: "cat ../outside/doc.txt" }, REFUSED],
  ["R303", "cd to .. from the repository, where .. is itself outside the allow list", "Bash", { command: "cd .. && cat outside/doc.txt" }, REFUSED],
  ["R304", "cat of a relative path into a working directory", "Bash", { command: "cat working-directory/report.html" }, REFUSED, { cwd: T }],
  ["R305", "sqlite3 on a relative path to vespera.db", "Bash", { command: "sqlite3 working-directory/vespera.db .tables" }, REFUSED, { cwd: T }],
  ["R306", "Get-Content of a relative path written with backslashes", "PowerShell", { command: String.raw`Get-Content ..\outside\doc.txt` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["R307", "a command naming no path, with a working directory as the current directory", "Bash", { command: "ls" }, REFUSED, { cwd: WD }],
  ["R308", "a command naming no path, with a current directory outside the allow list", "Bash", { command: "ls" }, REFUSED, { cwd: O }],

  /* 4. Every field a search tool takes a path or a pattern in. */
  ["R401", "Grep whose glob climbs out of its path into a working directory", "Grep", { pattern: "x", path: `${T}/scratch`, glob: "../working-directory/*.html" }, REFUSED],
  ["R402", "Grep whose glob climbs out of its path and out of the allow list", "Grep", { pattern: "x", path: `${T}/scratch`, glob: "../../outside/**" }, REFUSED],
  ["R403", "Grep of the repository with an absolute glob into a working directory", "Grep", { pattern: "x", path: C, glob: `${WD}/**` }, REFUSED],
  ["R404", "Glob with an absolute pattern outside the allow list", "Glob", { pattern: `${O}/**/*` }, REFUSED],
  ["R405", "Glob with an absolute pattern on a drive no allowed root is on", "Glob", { pattern: "Q:/no-such-folder/**/*.pdf" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["R406", "Glob with an absolute pattern into a working directory", "Glob", { pattern: `${WD}/**/*.html` }, REFUSED],
  ["R407", "Glob with a relative pattern that leaves the repository", "Glob", { pattern: "../outside/**" }, REFUSED],
  ["R408", "Glob whose pattern climbs out of its path into a working directory", "Glob", { pattern: "../working-directory/**", path: `${T}/scratch` }, REFUSED],
  ["R409", "Grep with no path, with a current directory outside the allow list", "Grep", { pattern: "x" }, REFUSED, { cwd: O }],
  ["R410", "Glob with no path, with a current directory outside the allow list", "Glob", { pattern: "**/*" }, REFUSED, { cwd: O }],
  ["R411", "Grep with no path, with a working directory as the current directory", "Grep", { pattern: "x" }, REFUSED, { cwd: WD }],
  ["R412", "Glob with no path, with a working directory as the current directory", "Glob", { pattern: "**/*" }, REFUSED, { cwd: WD }],

  /* 4, continued. A search that starts above a working directory reaches into it. */
  ["R413", "Grep with no path, with a current directory that holds a working directory", "Grep", { pattern: "x" }, REFUSED, { cwd: T }],
  ["R414", "Glob with no path, with a current directory that holds a working directory", "Glob", { pattern: "**/*" }, REFUSED, { cwd: T }],
  ["R415", "Grep whose path holds a working directory", "Grep", { pattern: "x", path: T }, REFUSED],
  ["R416", "Glob whose path holds a working directory", "Glob", { pattern: "**/*.html", path: T }, REFUSED],
  ["R417", "Grep whose glob names a working directory beneath its path", "Grep", { pattern: "x", path: T, glob: "working-directory/**" }, REFUSED],
  ["R418", "Glob whose pattern names a working directory beneath its path", "Glob", { pattern: "working-directory/**/*.html", path: T }, REFUSED],
  ["R419", "Grep whose path holds a working directory two folders further down", "Grep", { pattern: "x", path: DEEP }, REFUSED],

  /* 5. Forms that resolve somewhere other than where they read. */
  ["R501", "ls of <repository>/.., whose dots are part of the path", "Bash", { command: `ls ${C}/..` }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["R502", "ls of ~/.m2/.., which is the home folder", "Bash", { command: "ls ~/.m2/.." }, REFUSED],
  ["R503", "Read through a link under the temp folder that leads outside the allow list", "Read", { file_path: `${LINKS}/out/doc.txt` }, REFUSED, { needsLink: true }],
  ["R504", "Grep whose path is such a link", "Grep", { pattern: "x", path: `${LINKS}/out` }, REFUSED, { needsLink: true }],
  ["R505", "Read through a link that leads into a working directory", "Read", { file_path: `${LINKS}/to-working-directory/report.html` }, REFUSED, { needsLink: true }],

  /* N1. A relative path is read against every directory the command names, not the current directory alone. */
  ["N101", "cd to an allowed folder, then cat of a relative path into a working directory beneath it", "Bash", { command: "cd .. && cat working-directory/report.html" }, REFUSED, { cwd: `${T}/scratch` }],
  ["N102", "cd to an allowed folder by its absolute path, then cat into a working directory beneath it", "Bash", { command: `cd ${T} && cat working-directory/report.html` }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["N103", "git -C naming an allowed folder, then a relative path into a working directory beneath it", "Bash", { command: "git -C .. show working-directory/report.html" }, REFUSED, { cwd: `${T}/scratch` }],
  ["N104", "git -C naming an allowed folder by its absolute path, then a path into a working directory beneath it", "Bash", { command: `git -C ${T} show working-directory/report.html` }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["N105", "pushd to an allowed folder, then cat into a working directory beneath it", "Bash", { command: "pushd .. && cat working-directory/report.html" }, REFUSED, { cwd: `${T}/scratch` }],
  ["N106", "Set-Location to an allowed folder, then Get-Content into a working directory beneath it", "PowerShell", { command: String.raw`Set-Location ..; Get-Content working-directory\report.html` }, REFUSED, { cwd: `${T}/scratch`, windows: ONLY_WINDOWS.powerShell }],
  ["N107", "cd to .., then a relative path that leaves the allow list from there and not from the current directory", "Bash", { command: "cd .. && cat ../outside/doc.txt" }, REFUSED, { cwd: `${C}/src` }],
  ["N108", "two cd in a row, the second relative to the first, then a path into a working directory", "Bash", { command: "cd .. && cd deep && cat a/b/notes.txt" }, REFUSED, { cwd: `${T}/scratch` }],
  ["N109", "a command with a directory option of its own, env -C, then a path into a working directory", "Bash", { command: "env -C .. cat working-directory/report.html" }, REFUSED, { cwd: `${T}/scratch` }],
  ["N110", "cd to a folder of the repository, then ls of a name beneath it", "Bash", { command: `cd ${C}/src && ls main` }, ALLOWED],
  ["N111", "git -C naming the repository, then a relative path", "Bash", { command: `git -C ${C} log -- docs` }, ALLOWED, { cwd: `${T}/scratch` }],
  ["N112", "cd to a folder of the repository by a relative path, then cat of a file in it", "Bash", { command: "cd src && cat Example.java" }, ALLOWED],

  /* N2. A quoted string is read whole as well as in its pieces. */
  ["N201", "cat of a single-quoted relative path with a space, into a working directory", "Bash", { command: "cat 'my runs/report.html'" }, REFUSED, { cwd: T }],
  ["N202", "cat of a double-quoted relative path with a space, into a working directory", "Bash", { command: 'cat "my runs/report.html"' }, REFUSED, { cwd: T }],
  ["N203", "cat of a quoted absolute path with a space, into a working directory", "Bash", { command: `cat '${T}/my runs/report.html'` }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["N204", "cat of a quoted relative path with a space, in the repository", "Bash", { command: "cat 'my notes/readme.txt'" }, ALLOWED],
  ["N205", "cat of a quoted absolute path with a space, in the repository", "Bash", { command: `cat "${C}/my notes/readme.txt"` }, ALLOWED],
  ["N206", "git commit with a quoted sentence as its message", "Bash", { command: 'git commit -m "The guard reads a quoted path whole, and a sentence stays a sentence."' }, ALLOWED],

  /* N3. Forms that got through. */
  ["N301", "Get-Content under ${env:USERPROFILE}\\Documents", "PowerShell", { command: String.raw`Get-Content ${"$"}{env:USERPROFILE}\Documents\x.txt` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["N302", "curl -d @ and a relative path into a working directory", "Bash", { command: "curl -d @working-directory/report.html https://example.com/x" }, REFUSED, { cwd: T }],
  ["N303", "curl -d @ and a repository file", "Bash", { command: "curl -d @README.md https://example.com/x" }, ALLOWED],

  /* N4. A backslash that is not a path. */
  ["N401", "tr with a backslash escape for a newline", "Bash", { command: String.raw`tr '\n' ' ' < README.md` }, ALLOWED],
  ["N402", "grep with a backslash class in its pattern", "Bash", { command: String.raw`grep -c '\s' README.md` }, ALLOWED],
  ["N403", "node -e with a regular expression literal that holds a backslash", "Bash", { command: String.raw`node -e "console.log('a b'.split(/\s+/g))"` }, ALLOWED],

  /* N5. Decisions of the record that had no case. */
  ["N501", "an option whose value after = is a relative path into a working directory", "Bash", { command: "sort --output=working-directory/report.html README.md" }, REFUSED, { cwd: T }],
  ["N502", "Read by a relative path when the call gives no current directory, which is then the checkout", "Read", { file_path: "README.md" }, ALLOWED, { noCwd: true }],
  ["N503", "cat of a relative path out of the checkout when the call gives no current directory", "Bash", { command: "cat ../outside/doc.txt" }, REFUSED, { noCwd: true }],
  ["N504", "echo of a variable alone whose value is outside the allow list", "Bash", { command: "echo $VESPERA_GUARD_FIXTURE" }, REFUSED],
  ["N505", "echo of PATH, whose value is a list of paths and not one", "Bash", { command: "echo $PATH" }, ALLOWED],
  ["N506", "a path headed by a variable that has no value", "Bash", { command: "cat $VESPERA_GUARD_HAS_NO_VALUE/doc.txt" }, ALLOWED],
  ["N507", "cat of a /cygdrive path", "Bash", { command: "cat /cygdrive/q/no-such-folder/x" }, REFUSED, { windows: ONLY_WINDOWS.shellAbsolute }],
  ["N508", "Grep of a folder whose only working directories are inside .git, node_modules and target", "Grep", { pattern: "x", path: `${T}/built` }, ALLOWED],
  ["N509", "Grep of an allowed folder with more folders beneath it than the walk down looks at", "Grep", { pattern: "x", path: WIDE }, REFUSED],
  ["N510", "Grep of an allowed folder with few folders beneath it and no working directory", "Grep", { pattern: "x", path: `${H}/.m2` }, ALLOWED],

  /* N6. The bounds on reading relative paths against the folders a command names. */
  ["N601", "a command naming as many folders as relative paths are read against, the current directory among them", "Bash", { command: `ls ${AS_MANY_FOLDERS_AS_ARE_READ}` }, ALLOWED],
  ["N602", "a command naming one folder more than relative paths are read against", "Bash", { command: `ls ${ONE_FOLDER_TOO_MANY}` }, REFUSED],
  ["N603", "a command whose relative tokens need more readings against its folders than the guard makes", "Bash", { command: `echo ${AS_MANY_FOLDERS_AS_ARE_READ} ${plainWords(WORDS_PAST_THE_READINGS)}` }, REFUSED],
  ["N604", "a command with the same folders and few enough words to stay within the readings", "Bash", { command: `echo ${AS_MANY_FOLDERS_AS_ARE_READ} ${plainWords(WORDS_WITHIN_THE_READINGS)}` }, ALLOWED],

  /* N7. One letter and a colon at the head of a token is that drive, whatever follows. */
  ["N701", "cat of a path on another drive with no separator after the colon", "Bash", { command: "cat Q:no-such-folder/x" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["N702", "Get-Content of such a path, written with a backslash", "PowerShell", { command: String.raw`Get-Content Q:no-such-folder\x` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["N703", "cat of such a path in quotes, with a space in it", "Bash", { command: "cat 'Q:no such/x'" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["N704", "an option whose value after = is such a path", "Bash", { command: "sort --file=Q:x README.md" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["N705", "such a path on the fixture's own drive, in a folder the allow list does not name", "Bash", { command: `cat ${ON_THE_FIXTURES_DRIVE}no-such-folder-of-the-fixture/x` }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["N706", "such a path on the fixture's own drive that lands under the temp folder", "Bash", { command: `cat ${ON_THE_FIXTURES_DRIVE}${T.slice(3)}/scratch/note.txt` }, ALLOWED, { windows: ONLY_WINDOWS.drive }],
  ["N707", "curl of a host and a port with no scheme", "Bash", { command: "curl -s localhost:5001/health" }, ALLOWED],
  ["N708", "git commit with a quoted sentence that opens with a word and a colon", "Bash", { command: 'git commit -m "Note: the guard reads one letter and a colon as a drive, and a word and a colon as a word."' }, ALLOWED],
  ["N709", "sed with colons for delimiters, whose expression is headed by one letter and a colon", "Bash", { command: "sed -e s:a:b: README.md" }, REFUSED, { windows: ONLY_WINDOWS.drive }],

  /* A1. A token is used along one chain of directories no more often than the command writes it, so a climb is not climbed again by itself. */
  ["A101", "ls of .. from two folders inside the repository", "Bash", { command: "ls .." }, ALLOWED, { cwd: `${C}/src/main` }],
  ["A102", "git -C .. from one folder inside the repository", "Bash", { command: "git -C .. status" }, ALLOWED, { cwd: `${C}/src` }],
  ["A103", "cd ../.. and ls from two folders inside the repository", "Bash", { command: "cd ../.. && ls" }, ALLOWED, { cwd: `${C}/src/main` }],
  ["A104", "cd ../.. and the Maven wrapper from two folders inside the repository", "Bash", { command: "cd ../.. && ./mvnw -q test" }, ALLOWED, { cwd: `${C}/src/main` }],
  ["A105", "cd to .., an allowed folder, then cat of a relative path that stays out of every working directory", "Bash", { command: "cd .. && cat scratch/note.txt" }, ALLOWED, { cwd: `${T}/scratch` }],
  ["A106", "cd .. written twice from two folders inside the repository, which climbs twice and no further", "Bash", { command: "cd .. && cd .. && ls" }, ALLOWED, { cwd: `${C}/src/main` }],
  ["A107", "cd .. written twice, then a relative path into a working directory two folders up", "Bash", { command: "cd .. && cd .. && cat working-directory/report.html" }, REFUSED, { cwd: `${DEEP}/a` }],
  ["A108", "cd to a folder by a relative name with no .. in it, then cat into a working directory beneath it", "Bash", { command: "cd deep && cat a/b/notes.txt" }, REFUSED, { cwd: T }],
  ["A109", "git -C naming a folder by a relative name with no .. in it, then a path into a working directory beneath it", "Bash", { command: "git -C deep show a/b/notes.txt" }, REFUSED, { cwd: T }],
  ["A110", "Set-Location to a folder by a relative name with no .. in it, then Get-Content into a working directory beneath it", "PowerShell", { command: String.raw`Set-Location deep; Get-Content a\b\notes.txt` }, REFUSED, { cwd: T, windows: ONLY_WINDOWS.powerShell }],
  ["A111", "from one folder inside the repository, a path through .. beside a bare .., which read together leave the allow list", "Bash", { command: "cp ../README.md .." }, REFUSED, { cwd: `${C}/src` }],

  /* A2. One letter and a colon is a drive as written, before any punctuation is trimmed from it. */
  ["A201", "ls of a drive letter, a colon and a dot", "Bash", { command: "ls Q:." }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["A202", "ls of a drive letter, a colon and two dots", "Bash", { command: "ls Q:.." }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["A203", "Set-Location to a drive letter, a colon and a dot, then Get-Content of a relative path", "PowerShell", { command: String.raw`Set-Location Q:.; Get-Content folder\doc.txt` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["A204", "a drive path that ends a sentence, followed by a full stop", "Bash", { command: `echo see ${NO_DRIVE}.` }, REFUSED, { windows: ONLY_WINDOWS.drive }],

  /* A3. A path that is not a whole token: after an escaped space, or in a list. */
  ["A301", "cat of a relative path whose space is escaped with a backslash, into a working directory", "Bash", { command: String.raw`cat my\ runs/report.html` }, REFUSED, { cwd: T }],
  ["A302", "cat of a brace list, one of whose items climbs into a working directory", "Bash", { command: "cat {note.txt,../working-directory/report.html}" }, REFUSED, { cwd: `${T}/scratch` }],
  ["A303", "Get-Content of a comma list, one of whose items climbs into a working directory", "PowerShell", { command: "Get-Content note.txt,../working-directory/report.html" }, REFUSED, { cwd: `${T}/scratch` }],
  ["A304", "cat of a repository path whose space is escaped with a backslash", "Bash", { command: String.raw`cat my\ notes/readme.txt` }, ALLOWED],

  /* A7. A path on a drive with no separator after the colon is judged from the drive's root and as a relative path. */
  ["A701", "such a path that is allowed from the drive's root and lands in a working directory under the current directory", "Bash", { command: `cat ${ON_THE_FIXTURES_DRIVE}${T.slice(3)}/scratch/note.txt` }, REFUSED, { cwd: `${T}/mirror`, windows: ONLY_WINDOWS.drive }],

  /* What the record says of two Windows spellings, which had no case. */
  ["C101", "docker with a volume between two one-letter names, written as a token of its own", "Bash", { command: "docker run -v a:b image" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["C102", "Read of a report through the working directory's own stream name", "Read", { file_path: `${WD}::$INDEX_ALLOCATION/report.html` }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["C103", "cat of a relative path through the working directory's own stream name", "Bash", { command: "cat working-directory::$INDEX_ALLOCATION/report.html" }, REFUSED, { cwd: T, windows: ONLY_WINDOWS.drive }],
  ["C104", "Read of a report in a working directory by its data stream name", "Read", { file_path: `${WD}/report.html::$DATA` }, REFUSED, { windows: ONLY_WINDOWS.drive }],

  /* L1. A link is followed before the check, through every tool and through a link to a link. R503 and
     R504 hold a link that leads outside the allow list, and R505 one that leads into a folder holding
     vespera.db: a junction on Windows and a symlink elsewhere. */
  ["L101", "Read through a link under the temp folder that leads to a folder inside the allow list", "Read", { file_path: `${LINKS}/inside/note.txt` }, ALLOWED, { needsFurtherLinks: true }],
  ["L102", "Grep whose path is such a link", "Grep", { pattern: "x", path: `${LINKS}/inside` }, ALLOWED, { needsFurtherLinks: true }],
  ["L103", "Write of a path that does not exist yet, through such a link", "Write", { file_path: `${LINKS}/inside/not-written-yet.txt`, content: "x" }, ALLOWED, { needsFurtherLinks: true }],
  ["L104", "cat of a relative path through a link that leads into a working directory", "Bash", { command: "cat links/to-working-directory/report.html" }, REFUSED, { cwd: T, needsLink: true }],
  ["L105", "Grep whose path is a link that leads into a working directory", "Grep", { pattern: "x", path: `${LINKS}/to-working-directory` }, REFUSED, { needsLink: true }],
  ["L106", "Glob whose pattern names such a link beneath its path", "Glob", { pattern: "to-working-directory/**", path: LINKS }, REFUSED, { needsLink: true }],
  ["L107", "Read through a link to a link that leads into a working directory", "Read", { file_path: `${LINKS}/hop/report.html` }, REFUSED, { needsFurtherLinks: true }],

  /* L2. A path that holds .. is read twice, and either reading refuses: folded as text, and as the file
     system walks it, where a .. is taken from the folder the path before it leads to. links/in leads to
     deep/a, so links/in/.. is links/ as text and deep/ as walked, and deep/a/b is a working directory. */
  ["L201", "Read of a path that climbs with .. out of a link, into a working directory beside where the link leads", "Read", { file_path: `${LINKS}/in/../a/b/notes.txt` }, REFUSED, { needsFurtherLinks: true }],
  ["L202", "Grep whose path climbs out of a link into such a working directory", "Grep", { pattern: "x", path: `${LINKS}/in/../a/b` }, REFUSED, { needsFurtherLinks: true }],
  ["L203", "Grep whose path is a link and .., the parent of where the link leads, which holds a working directory", "Grep", { pattern: "x", path: `${LINKS}/in/..` }, REFUSED, { needsFurtherLinks: true }],
  ["L204", "Glob whose pattern climbs out of a link beneath its path into such a working directory", "Glob", { pattern: "in/../a/b/*.txt", path: LINKS }, REFUSED, { needsFurtherLinks: true }],
  ["L205", "Grep whose glob climbs out of a link beneath its path into such a working directory", "Grep", { pattern: "x", path: LINKS, glob: "in/../a/b/*.txt" }, REFUSED, { needsFurtherLinks: true }],
  ["L206", "Glob with an absolute pattern that climbs out of a link into such a working directory", "Glob", { pattern: `${LINKS}/in/../a/b/*.txt` }, REFUSED, { needsFurtherLinks: true }],
  ["L207", "cat of a relative path that climbs out of a link into such a working directory", "Bash", { command: "cat links/in/../a/b/notes.txt" }, REFUSED, { cwd: T, needsFurtherLinks: true }],
  ["L208", "cd through a link, then cat of a relative path that climbs out of where the link leads", "Bash", { command: "cd in && cat ../a/b/notes.txt" }, REFUSED, { cwd: LINKS, needsFurtherLinks: true }],
  ["L209", "cat of a relative path that climbs, with a link as the current directory", "Bash", { command: "cat ../a/b/notes.txt" }, REFUSED, { cwd: `${LINKS}/in`, needsFurtherLinks: true }],
  ["L210", "Read by a relative path that climbs, with a link as the current directory", "Read", { file_path: "../a/b/notes.txt" }, REFUSED, { cwd: `${LINKS}/in`, needsFurtherLinks: true }],
  ["L211", "cd through a link, cd -P to .., which is the parent of where the link leads, then cat into a working directory beneath it", "Bash", { command: "cd in && cd -P .. && cat a/b/notes.txt" }, REFUSED, { cwd: LINKS, needsFurtherLinks: true }],
  ["L212", "Read of a path that climbs out of a link to a link, into such a working directory", "Read", { file_path: `${LINKS}/via/../a/b/notes.txt` }, REFUSED, { needsFurtherLinks: true }],
  ["L213", "Get-Content of a relative path that climbs out of a link, written with backslashes", "PowerShell", { command: String.raw`Get-Content links\in\..\a\b\notes.txt` }, REFUSED, { cwd: T, needsFurtherLinks: true, windows: ONLY_WINDOWS.powerShell }],
  ["L214", "Read of a path that climbs out of a link and stays inside the allow list and out of every working directory", "Read", { file_path: `${LINKS}/inside/../scratch/note.txt` }, ALLOWED, { needsFurtherLinks: true }],
  ["L215", "cat of such a relative path", "Bash", { command: "cat links/inside/../scratch/note.txt" }, ALLOWED, { cwd: T, needsFurtherLinks: true }],
  ["L216", "cat of a relative path that climbs out of a link to a file beside a working directory and not in one", "Bash", { command: "cat links/in/../note.txt" }, ALLOWED, { cwd: T, needsFurtherLinks: true }],

  /* L3. A name that is there and cannot be followed to where it leads is refused. Only a name that is
     not there is answered from the folder it would be in, so a path not written yet stays usable. */
  ["L301", "Read of a link that leads to a name that is not there", "Read", { file_path: `${LINKS}/nowhere` }, REFUSED, { needsFurtherLinks: true }],
  ["L302", "Write of a path that does not exist yet, beneath such a link", "Write", { file_path: `${LINKS}/nowhere/not-written-yet.txt`, content: "x" }, REFUSED, { needsFurtherLinks: true }],
  ["L303", "Read beneath a link that leads to itself", "Read", { file_path: `${LINKS}/loop/note.txt` }, REFUSED, { needsFurtherLinks: true }],
  ["L304", "ls of a plain name that is a link leading to a name that is not there", "Bash", { command: "ls nowhere" }, REFUSED, { cwd: LINKS, needsFurtherLinks: true }],
  ["L305", "Write of a path that does not exist yet, two folders that do not exist yet down an allowed one", "Write", { file_path: `${T}/scratch/not-made-yet/nor-this/out.log`, content: "x" }, ALLOWED],
  ["L306", "git commit with a quoted sentence longer than any name a file system holds", "Bash", { command: `git commit -m "${SENTENCE_LONGER_THAN_A_NAME}"` }, ALLOWED],

  /* G1. A token that ends in a comma or a closing brace is one path written once, and is counted once. */
  ["G101", "Get-ChildItem of .. and a folder, with a space after the comma, from one folder inside the repository", "PowerShell", { command: "Get-ChildItem .., main" }, ALLOWED, { cwd: `${C}/src` }],
  ["G102", "Get-ChildItem of the same two with no space after the comma", "PowerShell", { command: "Get-ChildItem ..,main" }, ALLOWED, { cwd: `${C}/src` }],
  ["G103", "a .. followed by a closing brace, from one folder inside the repository", "Bash", { command: "echo ..}" }, ALLOWED, { cwd: `${C}/src` }],
  ["G104", "Get-ChildItem of .. written twice with a comma between, which climbs twice", "PowerShell", { command: "Get-ChildItem .., .." }, REFUSED, { cwd: `${C}/src` }],
  ["G105", "ls of .. followed by a comma, from the repository, where one climb leaves the allow list", "Bash", { command: "ls ..," }, REFUSED],
  ["G106", "ls of .. led by a comma, from the repository", "Bash", { command: "ls ,.." }, REFUSED],
  ["G107", "cat of a relative path into a working directory, followed by a comma", "Bash", { command: "cat ../working-directory/report.html, and more" }, REFUSED, { cwd: `${T}/scratch` }],

  /* G2. Spellings that are no path: a key of an object in a quoted string, and the braces of a variable.
     Each allowed case stands beside the refusals the rule must leave standing. */
  ["G201", "gh with a quoted jq object whose key is one letter", "Bash", { command: "gh api repos/algernon28/vespera/issues/432 --jq '{t:.title}'" }, ALLOWED],
  ["G202", "node -e with a quoted object literal whose keys are one letter each", "Bash", { command: 'node -e "console.log(JSON.stringify({a:1,b:2}))"' }, ALLOWED],
  ["G203", "gh with a quoted jq object whose second one-letter key follows a comma and a space", "Bash", { command: "gh pr list --json number,title --jq '.[] | {n:.number, t:.title}'" }, ALLOWED],
  ["G204", "a quoted key of one letter whose value is a relative path into a working directory", "Bash", { command: "jq '{t:../working-directory/report.html}'" }, REFUSED, { cwd: `${T}/scratch` }],
  ["G205", "ls of a comma list, outside quotes, whose second path is a drive letter, a colon and two dots", "Bash", { command: "ls x,Q:.." }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G206", "cat of a brace list, outside quotes, one of whose paths is a drive letter, a colon and a dot", "Bash", { command: "cat {x,Q:.}" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G207", "such a brace list inside a command substitution in double quotes, where the shell does expand it", "Bash", { command: 'echo "$(cat {x,Q:.})"' }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G208", "a quoted command whose token is a drive letter, a colon and a name, after no comma and no brace", "Bash", { command: "bash -c 'cat Q:x'" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G209", "a quoted brace list one of whose paths is written the Git Bash way", "Bash", { command: 'bash -c "cat {x,/q/no-such-folder/x}"' }, REFUSED, { windows: ONLY_WINDOWS.gitBash }],
  ["G210", "an option whose value after = is a drive letter, a colon and a dot", "Bash", { command: "sort --file=Q:. README.md" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G211", "cat of a quoted path headed by ${TMPDIR}, whose next folder has a one-letter name", "Bash", { command: 'cat "${TMPDIR}/x"' }, ALLOWED],
  ["G212", "cat of a quoted path headed by ${TMPDIR}, under the temp folder", "Bash", { command: 'cat "${TMPDIR}/scratch/note.txt"' }, ALLOWED],
  ["G213", "cat of a path headed by ${TMPDIR} into a working directory", "Bash", { command: "cat ${TMPDIR}/working-directory/report.html" }, REFUSED],
  ["G214", "cat of a comma list headed by ${TMPDIR}, whose second path climbs into a working directory", "Bash", { command: "cat ${TMPDIR}/scratch/note.txt,../working-directory/report.html" }, REFUSED, { cwd: `${T}/scratch` }],
  ["G215", "a variable in braces that has no value, followed by a relative path into a working directory", "Bash", { command: "cat ${VESPERA_GUARD_HAS_NO_VALUE}../working-directory/report.html" }, REFUSED, { cwd: `${T}/scratch` }],
  ["G216", "echo of a one-letter variable with a default value", "Bash", { command: "echo ${f:-none}" }, ALLOWED],
  ["G217", "cat of a variable whose default value is a relative path into a working directory", "Bash", { command: "cat ${OUT:-../working-directory/report.html}" }, REFUSED, { cwd: `${T}/scratch` }],
  ["G218", "cat of a variable whose default value is a drive letter, a colon and a dot", "Bash", { command: "cat ${x:-Q:.}" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G219", "Get-Content of ${Q:-x}, which PowerShell reads as a name on drive Q and not as a default value", "PowerShell", { command: "Get-Content ${Q:-x}" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G220", "PowerShell's ${Q:x}, which is what a name on drive Q holds", "PowerShell", { command: "${Q:x}" }, REFUSED, { windows: ONLY_WINDOWS.drive }],

  /* G4. The @ is taken off a token as it is written, before any punctuation is trimmed from its end. */
  ["G401", "ls of @ and a drive letter, a colon and a dot", "Bash", { command: "ls @Q:." }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G402", "ls of @ and a drive letter, a colon and two dots", "Bash", { command: "ls @Q:.." }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G403", "curl -d @ and a bare drive", "Bash", { command: "curl -d @Q: https://example.com/x" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G404", "an option whose value after = is @ and a drive letter, a colon and a dot", "Bash", { command: "curl --data=@Q:. https://example.com/x" }, REFUSED, { windows: ONLY_WINDOWS.drive }],

  /* L, continued. A path read against a folder that was reached as walked is judged as walked: against
     the allowed roots and against where they lead. Here ${TEMP} is itself a link. And the rule as it
     stands for a broken link followed at once by .., which lands on the folder the link is in. */
  ["L217", "cd to a path that climbs under an allowed root that is itself a link, then cat of a relative path beneath where it lands", "Bash", { command: "cd $TEMP/scratch/.. && cat deep/note.txt" }, ALLOWED, { needsFurtherLinks: true, env: { TEMP: LINKED_TEMP, TMP: LINKED_TEMP, TMPDIR: LINKED_TEMP } }],
  ["L307", "Read of a link that leads to a name that is not there, followed at once by ..", "Read", { file_path: `${LINKS}/nowhere/..` }, ALLOWED, { needsFurtherLinks: true }],

  /* G5. A variable in braces that is a whole token is replaced by its value, as one with a path after it is. */
  ["G501", "cd to a quoted ${TMPDIR}, then cat of a relative path into a working directory beneath it", "Bash", { command: 'cd "${TMPDIR}" && cat working-directory/report.html' }, REFUSED],
  ["G502", "git -C ${TMPDIR}, then a relative path into a working directory beneath it", "Bash", { command: "git -C ${TMPDIR} show working-directory/report.html" }, REFUSED],
  ["G503", "cd to ${HOME}, which the allow list does not name", "Bash", { command: "cd ${HOME} && cat Documents/x.txt" }, REFUSED],
  ["G504", "ls of ${USERPROFILE}, which the allow list does not name", "Bash", { command: "ls ${USERPROFILE}" }, REFUSED],
  ["G505", "ls of ${TMPDIR}, which the allow list names", "Bash", { command: "ls ${TMPDIR}" }, ALLOWED],

  /* G6. A one-letter key is a key only in a brace group of a quoted string every member of which is a
     name and a colon. The guard pairs quote characters as it finds them, so text the shell does not
     quote can lie between two of them: an apostrophe in a comment, an escaped quote, a here-document. */
  ["G601", "a brace list with a drive member, between two comment lines that each hold an apostrophe", "Bash", { command: "# don't\ncat {some,Q:name}\n# isn't" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G602", "a comma list with a drive member, between two such comment lines", "PowerShell", { command: "# don't\nGet-Content some, Q:archive\\doc.pdf\n# isn't" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G603", "a brace list with a drive member, between two escaped double quotes", "Bash", { command: 'echo \\" {some,Q:name} \\"' }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G604", "a comma list with a drive member, after a here-document that holds one apostrophe and before a quoted one", "Bash", { command: "cat <<EOF\nit's here\nEOF\ncat some,Q:archive/doc.pdf\necho \"isn't\"" }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G605", "a quoted brace list for a second shell, one of whose members is a drive and another a plain name", "Bash", { command: 'bash -c "cat {x,Q:name}"' }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G606", "a quoted comma list for a second shell, with a drive member and no braces", "Bash", { command: 'powershell -Command "Get-Content x, Q:name"' }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["G607", "gh with a quoted jq object whose keys are a word and one letter", "Bash", { command: "gh pr list --json number,title --jq '.[] | {number:.number, t:.title}'" }, ALLOWED],

  /* G7. A variable with a word for when it has no value is read with its value in its place and with
     the word in its place, and the word runs to the brace that closes the variable. */
  ["G701", "cat of ${TMPDIR:-word} followed by a path into a working directory", "Bash", { command: "cat ${TMPDIR:-some}/working-directory/report.html" }, REFUSED],
  ["G702", "cat of ${TMPDIR:-word} followed by a path under the temp folder", "Bash", { command: "cat ${TMPDIR:-some}/scratch/note.txt" }, ALLOWED],
  ["G703", "echo of ${TMPDIR:-word} followed by a folder with a one-letter name", "Bash", { command: "echo ${TMPDIR:-x}/y" }, ALLOWED],
  ["G704", "cat of a variable whose word is a variable whose word climbs into a working directory", "Bash", { command: "cat ${VESPERA_GUARD_HAS_NO_VALUE:-${VESPERA_GUARD_NOR_THIS:-../working-directory/report.html}}" }, REFUSED, { cwd: `${T}/scratch` }],
  ["G705", "cat of a variable whose word is ${TMPDIR} and a path into a working directory", "Bash", { command: "cat ${VESPERA_GUARD_HAS_NO_VALUE:-${TMPDIR}/working-directory/report.html}" }, REFUSED],
  ["G706", "cat of a variable whose word is ${TMPDIR} and a path under the temp folder", "Bash", { command: "cat ${VESPERA_GUARD_HAS_NO_VALUE:-${TMPDIR}/scratch/out.log}" }, ALLOWED],

  /* K1. docs/adr/0212: node, the counting script beside the guard, and a folder that directly holds
     vespera.db, and nothing else, is admitted.
     The working directory is named relatively from the temp folder wherever the case allows it, so that
     the ordinary reading refuses it on every platform: a rooted POSIX path in a command is not read
     (ADR-196 section 5), so on Linux K102 and K106 are let through by the ordinary reading already. */
  ["K101", "the counting command, naming the working directory by a relative path", "Bash", { command: `node ${C}/${COUNTS} working-directory` }, ALLOWED, { cwd: T }],
  ["K102", "the counting command, naming the script by a relative path and the working directory by an absolute one", "Bash", { command: `node ${COUNTS} ${WD}` }, ALLOWED],
  ["K103", "the counting command, naming a working directory whose name holds a space, in single quotes", "Bash", { command: `node ${C}/${COUNTS} 'my runs'` }, ALLOWED, { cwd: T }],
  ["K104", "the counting command, naming the script in single quotes", "Bash", { command: `node '${C}/${COUNTS}' working-directory` }, ALLOWED, { cwd: T }],
  ["K105", "the counting command in PowerShell, with the script written with backslashes", "PowerShell", { command: `node ${back(`${C}/${COUNTS}`)} working-directory` }, ALLOWED, { cwd: T, windows: ONLY_WINDOWS.powerShell }],
  ["K106", "the counting command, naming a working directory under no allowed root", "Bash", { command: `node ${COUNTS} ${O}/counted` }, ALLOWED],

  /* K2. Every other form stays refused: the command is three words, two of them bare or single-quoted,
     naming the pinned script and a folder that directly holds vespera.db, run from an allowed current
     directory. Each is read as before and refused as a path into a working directory. */
  ["K201", "the counting script given vespera.db itself, a file inside the working directory", "Bash", { command: `node ${C}/${COUNTS} working-directory/vespera.db` }, REFUSED, { cwd: T }],
  ["K202", "the counting script given a folder inside the working directory", "Bash", { command: `node ${C}/${COUNTS} working-directory/deliverable` }, REFUSED, { cwd: T }],
  ["K203", "the counting script given a folder holding vespera.lock and no vespera.db", "Bash", { command: `node ${C}/${COUNTS} locked` }, REFUSED, { cwd: T }],
  ["K204", "the counting script given a folder under no allowed root that holds no vespera.db, by a path that climbs to it", "Bash", { command: `node ${C}/${COUNTS} ../outside` }, REFUSED, { cwd: T }],
  ["K205", "the counting command with a second argument", "Bash", { command: `node ${C}/${COUNTS} working-directory extra` }, REFUSED, { cwd: T }],
  ["K206", "the counting command followed by && and a read of the log", "Bash", { command: `node ${C}/${COUNTS} working-directory && cat working-directory/vespera.log` }, REFUSED, { cwd: T }],
  ["K207", "the counting command followed by ; and another command", "Bash", { command: `node ${C}/${COUNTS} working-directory; ls` }, REFUSED, { cwd: T }],
  ["K208", "the counting command followed by a newline and another command", "Bash", { command: `node ${C}/${COUNTS} working-directory\nls` }, REFUSED, { cwd: T }],
  ["K209", "the counting command piped into another program", "Bash", { command: `node ${C}/${COUNTS} working-directory | head -5` }, REFUSED, { cwd: T }],
  ["K210", "the counting command with its output redirected to a file", "Bash", { command: `node ${C}/${COUNTS} working-directory > scratch/counts.txt` }, REFUSED, { cwd: T }],
  ["K211", "the counting command with an option to node before the script", "Bash", { command: `node --no-warnings ${C}/${COUNTS} working-directory` }, REFUSED, { cwd: T }],
  ["K212", "the counting command with an assignment before node", "Bash", { command: `NODE_OPTIONS=--no-warnings node ${C}/${COUNTS} working-directory` }, REFUSED, { cwd: T }],
  ["K213", "the counting command with the working directory in double quotes", "Bash", { command: `node ${C}/${COUNTS} "working-directory"` }, REFUSED, { cwd: T }],
  ["K214", "the counting command with the working directory behind a variable", "Bash", { command: `node ${C}/${COUNTS} $TMPDIR/working-directory` }, REFUSED],
  ["K215", "the counting command with the working directory from a command substitution", "Bash", { command: `node ${C}/${COUNTS} $(echo working-directory)` }, REFUSED, { cwd: T }],
  ["K216", "the counting command with a .. in the working directory's path", "Bash", { command: `node ${C}/${COUNTS} scratch/../working-directory` }, REFUSED, { cwd: T }],
  ["K217", "the counting command in Bash with the working directory written with backslashes outside quotes", "Bash", { command: `node ${C}/${COUNTS} ${back(WD)}` }, REFUSED, { windows: ONLY_WINDOWS.drive }],
  ["K218", "another script of the hooks folder given a working directory", "Bash", { command: `node ${C}/${GUARD} working-directory` }, REFUSED, { cwd: T }],
  ["K219", "a copy of the counting script outside the hooks folder given a working directory", "Bash", { command: "node scratch/working-directory-counts.mjs working-directory" }, REFUSED, { cwd: T }],
  ["K220", "node -e given vespera.db", "Bash", { command: `node -e "require('node:sqlite')" working-directory/vespera.db` }, REFUSED, { cwd: T }],
  ["K221", "sqlite3 asking vespera.db for a count", "Bash", { command: `sqlite3 working-directory/vespera.db "SELECT COUNT(*) FROM verdict"` }, REFUSED, { cwd: T }],
  ["K222", "the counting command run with a working directory as the current directory", "Bash", { command: `node ${C}/${COUNTS} .` }, REFUSED, { cwd: WD }],
  ["K223", "the counting command inside bash -c", "Bash", { command: `bash -c 'node ${C}/${COUNTS} working-directory'` }, REFUSED, { cwd: T }],
  ["K225", "the counting command with :x after the script, an alternate data stream", "Bash", { command: `node ${C}/${COUNTS}:x working-directory` }, REFUSED, { cwd: T, windows: ONLY_WINDOWS.drive }],
  ["K226", "the counting command in PowerShell followed by ; and another command", "PowerShell", { command: `node ${back(`${C}/${COUNTS}`)} working-directory; Get-ChildItem` }, REFUSED, { cwd: T, windows: ONLY_WINDOWS.powerShell }],
  ["K227", "the counting command in PowerShell piped into another command", "PowerShell", { command: `node ${back(`${C}/${COUNTS}`)} working-directory | Select-Object -First 5` }, REFUSED, { cwd: T, windows: ONLY_WINDOWS.powerShell }],
  ["K228", "the counting command in PowerShell with the working directory behind $env:", "PowerShell", { command: `node ${back(`${C}/${COUNTS}`)} $env:TEMP\\working-directory` }, REFUSED, { windows: ONLY_WINDOWS.powerShell }],
  ["K229", "the counting command in PowerShell with a backtick before the working directory", "PowerShell", { command: `node ${back(`${C}/${COUNTS}`)} \`working-directory` }, REFUSED, { cwd: T, windows: ONLY_WINDOWS.powerShell }],
  ["K230", "the counting command in PowerShell with the working directory in double quotes", "PowerShell", { command: `node ${back(`${C}/${COUNTS}`)} "working-directory"` }, REFUSED, { cwd: T, windows: ONLY_WINDOWS.powerShell }],

  /* K3. What docs/adr/0212 still refuses, named in one place: every file a working directory holds. */
  ["K301", "Read of profile.yaml in a working directory", "Read", { file_path: `${WD}/profile.yaml` }, REFUSED],
  ["K302", "Read of relevance-labels.yaml in a working directory", "Read", { file_path: `${WD}/relevance-labels.yaml` }, REFUSED],
  ["K303", "Read of the labelling page, which carries document openings", "Read", { file_path: `${WD}/relevance-labelling.html` }, REFUSED],
  ["K304", "Read of the review list", "Read", { file_path: `${WD}/extraction-failures.html` }, REFUSED],
  ["K305", "Read of the arrangement page", "Read", { file_path: `${WD}/arrangement.html` }, REFUSED],
  ["K306", "Read of vespera.log", "Read", { file_path: `${WD}/vespera.log` }, REFUSED],
  ["K307", "Read of a log vespera.log has rolled into", "Read", { file_path: `${WD}/vespera.2026-10-08.0.log` }, REFUSED],
  ["K308", "Read of the deliverable's CSV", "Read", { file_path: `${WD}/deliverable/run/documents.csv` }, REFUSED],
  ["K309", "Grep of vespera.db", "Grep", { pattern: "COUNT", path: `${WD}/vespera.db` }, REFUSED],
];

for (const [id, what, tool, input, expected, options = {}] of cases) {
  const skip =
    options.windows && !windows
      ? `not started on this platform: ${options.windows}`
      : (options.needsLink && !linkable) || (options.needsFurtherLinks && !linkedFurther)
        ? `not started on this platform: ${NO_LINK}`
        : false;
  test(`${id} ${expected === ALLOWED ? "allowed" : "refused"}: ${what}`, { skip }, () => {
    const cwd = options.noCwd ? undefined : (options.cwd ?? C);
    const result = startGuard({ stdin: hookInput(tool, input, cwd), env: options.env });
    claim(result, expected, `${tool} ${JSON.stringify(input)} with ${cwd ? `the current directory ${cwd}` : "no current directory given"}`);
  });
}

/* ---------- a checkout whose path holds a space ---------- */

test("R506 allowed: Read of a repository file when the checkout's path holds a space", () => {
  const result = startGuard({ projectDir: SPACED, stdin: hookInput("Read", { file_path: `${SPACED}/README.md` }, SPACED) });
  claim(result, ALLOWED, `Read of ${SPACED}/README.md, the checkout being ${SPACED}`);
});

test("R507 refused: Read under no allowed root when the checkout's path holds a space", () => {
  const result = startGuard({ projectDir: SPACED, stdin: hookInput("Read", { file_path: `${O}/doc.txt` }, SPACED) });
  claim(result, REFUSED, `Read of ${O}/doc.txt, the checkout being ${SPACED}`);
});

/* ---------- the counting script is pinned by its hash ---------- */

// docs/adr/0212: the same command K101 admits, in a checkout whose counting script has one line more than
// the one the guard pins, is read as before and refused.
test("K224 refused: the counting command when the script beside the guard is not the pinned one", () => {
  const call = hookInput("Bash", { command: `node ${ALTERED}/${COUNTS} working-directory` }, T);
  claim(startGuard({ projectDir: ALTERED, stdin: call }), REFUSED, `the counting command through ${ALTERED}/${WRAPPER}, whose counting script was altered`);
});

// The same command again, in a checkout whose counting script has CR LF line ends: the pin reads CR LF as
// LF, so the script is the pinned one and the command is admitted.
test("K107 allowed: the counting command when the script beside the guard has CR LF line ends", () => {
  const call = hookInput("Bash", { command: `node ${CRLF}/${COUNTS} working-directory` }, T);
  claim(startGuard({ projectDir: CRLF, stdin: call }), ALLOWED, `the counting command through ${CRLF}/${WRAPPER}, whose counting script has CR LF line ends`);
});

/* ---------- failing closed ---------- */

const allowedCall = hookInput("Read", { file_path: `${C}/README.md` }, C);

test("F01 refused: input that is not JSON", () => {
  claim(startGuard({ stdin: "{not json" }), REFUSED, "stdin holding {not json");
});

test("F02 refused: empty input", () => {
  claim(startGuard({ stdin: "" }), REFUSED, "empty stdin");
});

test("F03 refused: JSON null", () => {
  claim(startGuard({ stdin: "null" }), REFUSED, "stdin holding null");
});

test("F04 refused: JSON that names no tool", () => {
  claim(startGuard({ stdin: "{}" }), REFUSED, "stdin holding {}");
});

test("F05 refused: an allowed call when private-paths-guard.mjs is missing", () => {
  const call = hookInput("Read", { file_path: `${NO_GUARD}/README.md` }, NO_GUARD);
  claim(startGuard({ projectDir: NO_GUARD, stdin: call }), REFUSED, "the wrapper with no guard beside it and no git checkout to look in");
});

test("F06 refused: an allowed call when node is not on PATH", () => {
  // PATH is one empty folder. Git's bash on Windows adds its own folders, which hold no node.
  const result = startGuard({ stdin: allowedCall, env: { PATH: `${base}/empty-bin` } });
  claim(result, REFUSED, "the wrapper with a PATH that holds no node");
});

test("F07 allowed: a worktree with no copy of the guard uses the main checkout's", { skip: worktreeBuilt ? false : `not started here: ${NO_WORKTREE}` }, () => {
  const call = hookInput("Read", { file_path: `${WORKTREE}/README.md` }, WORKTREE);
  const result = startGuard({ projectDir: WORKTREE, script: `${MAIN}/${WRAPPER}`, stdin: call });
  claim(result, ALLOWED, `Read of ${WORKTREE}/README.md through the main checkout's wrapper`);
});

test("F08 refused: that worktree's call outside the allow list", { skip: worktreeBuilt ? false : `not started here: ${NO_WORKTREE}` }, () => {
  const call = hookInput("Read", { file_path: `${O}/doc.txt` }, WORKTREE);
  const result = startGuard({ projectDir: WORKTREE, script: `${MAIN}/${WRAPPER}`, stdin: call });
  claim(result, REFUSED, `Read of ${O}/doc.txt through the main checkout's wrapper`);
});

/* ---------- the command registered in .claude/settings.json ---------- */

const EVERY_GUARDED_TOOL = ["Read", "Grep", "Glob", "Edit", "Write", "NotebookEdit", "Bash", "PowerShell"];

test("S01 the hook is registered for each of the eight tools the guard reads", () => {
  assert.ok(registration, ".claude/settings.json registers no PreToolUse hook that names run-private-paths-guard");
  const matched = String(registration.matcher).split("|");
  for (const tool of EVERY_GUARDED_TOOL) {
    assert.ok(matched.includes(tool), `the matcher "${registration.matcher}" leaves out ${tool}, so the guard never sees a ${tool} call`);
  }
});

test("S02 allowed: the registered command lets an allowed call through", () => {
  claim(startGuard({ script: REGISTERED, stdin: allowedCall }), ALLOWED, "the registered command over a healthy checkout");
});

test("S03 refused: the registered command refuses a call outside the allow list", () => {
  const call = hookInput("Read", { file_path: `${O}/doc.txt` }, C);
  claim(startGuard({ script: REGISTERED, stdin: call }), REFUSED, "the registered command over a healthy checkout");
});

test("S04 refused: the registered command when no wrapper can be found", () => {
  const nowhere = `${base}/no-checkout`;
  mkdirSync(nowhere, { recursive: true });
  const call = hookInput("Read", { file_path: `${nowhere}/README.md` }, nowhere);
  claim(startGuard({ projectDir: nowhere, script: REGISTERED, stdin: call }), REFUSED, "the registered command in a folder with no hook and no git checkout");
});

test("S05 allowed: the registered command in a worktree with no copy of the hook uses the main checkout's", { skip: worktreeBuilt ? false : `not started here: ${NO_WORKTREE}` }, () => {
  const call = hookInput("Read", { file_path: `${WORKTREE}/README.md` }, WORKTREE);
  claim(startGuard({ projectDir: WORKTREE, script: REGISTERED, stdin: call }), ALLOWED, "the registered command in the fixture worktree");
});

test("S06 refused: the registered command in that worktree, for a call outside the allow list", { skip: worktreeBuilt ? false : `not started here: ${NO_WORKTREE}` }, () => {
  const call = hookInput("Read", { file_path: `${O}/doc.txt` }, WORKTREE);
  claim(startGuard({ projectDir: WORKTREE, script: REGISTERED, stdin: call }), REFUSED, "the registered command in the fixture worktree");
});

// Claude Code blocks a tool call on exit 2 only. A wrapper that ends any other way before it has
// started the guard must not let the call through.
for (const [id, code] of [["S07", 1], ["S08", 126], ["S09", 127]]) {
  test(`${id} refused: the registered command when the wrapper ends with exit ${code}`, () => {
    const dir = checkoutWhoseWrapperExits(code);
    const call = hookInput("Read", { file_path: `${dir}/README.md` }, dir);
    claim(startGuard({ projectDir: dir, script: REGISTERED, stdin: call }), REFUSED, `the registered command over a wrapper that ends with exit ${code}`);
  });
}

/* ---------- the committed files, where they are ---------- */

// Two calls through this checkout's own wrapper, not the copy: the files as git checked them out here.
// Read only, of AGENTS.md and of a fixture file, so neither depends on what this machine's local allow
// list adds.
test("P01 allowed: this checkout's own wrapper lets Read of its AGENTS.md through", () => {
  const call = hookInput("Read", { file_path: `${repo}/AGENTS.md` }, repo);
  claim(startGuard({ projectDir: repo, stdin: call }), ALLOWED, `Read of ${repo}/AGENTS.md through ${repo}/${WRAPPER}`);
});

test("P02 refused: this checkout's own wrapper refuses a Read under no allowed root", () => {
  const call = hookInput("Read", { file_path: `${O}/doc.txt` }, repo);
  claim(startGuard({ projectDir: repo, stdin: call }), REFUSED, `Read of ${O}/doc.txt through ${repo}/${WRAPPER}`);
});
