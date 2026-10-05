// Refuses any Claude tool call that would read or write outside the places listed in
// .claude/allowed-paths.txt (plus .claude/allowed-paths.local.txt on this machine), and any path inside
// a Vespera working directory wherever it is. The operator's archives can hold sensitive documents, and
// nothing that reads a document may reach a hosted model: a document is read only by local models.
//
// Allow list, not deny list, so that a knowledge base on a new path is protected without anyone
// remembering to add it. A refused path that is legitimate is added to the allow list; a mistake costs
// a refusal, never an exposed document.
//
// Bash and PowerShell commands are checked for the absolute paths written in their text. A path built
// at run time inside a script is not seen here; the written rule in AGENTS.md covers that.
//
// Protocol: Claude Code passes the tool call as JSON on stdin. Exit code 2 refuses it, and stderr is
// what Claude reads.

import { readFileSync, existsSync, statSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { dirname, isAbsolute, join, resolve } from "node:path";

const here = dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const repo = resolve(here, "..", "..");

const call = JSON.parse(readFileSync(0, "utf8"));
const cwd = call.cwd || repo;
const input = call.tool_input || {};

const norm = (p) => resolve(p).replace(/\\/g, "/").replace(/\/+$/, "").toLowerCase();

const tokens = {
  "${REPO}": repo,
  "${HOME}": homedir(),
  "${TEMP}": process.env.TEMP || process.env.TMPDIR || tmpdir(),
};

function allowList() {
  const roots = [];
  for (const name of ["allowed-paths.txt", "allowed-paths.local.txt"]) {
    const file = join(here, "..", name);
    if (!existsSync(file)) continue;
    for (let line of readFileSync(file, "utf8").split(/\r?\n/)) {
      line = line.trim();
      if (!line || line.startsWith("#")) continue;
      for (const [k, v] of Object.entries(tokens)) line = line.split(k).join(v);
      roots.push(norm(line));
    }
  }
  return roots;
}

// A Git Bash path such as /h/archive or /c/Users is the drive path H:/archive or C:/Users.
function fromGitBash(p) {
  const m = /^\/([A-Za-z])(\/.*)?$/.exec(p);
  return m ? `${m[1]}:${m[2] || "/"}` : p;
}

function pathsInCommand(command) {
  const found = [];
  // Drive paths: C:\x or C:/x, but not the "s://" of a URL scheme.
  for (const m of command.matchAll(/(?<![A-Za-z])([A-Za-z]:[\\/](?![\\/])[^\s"'`;|&<>()]*)/g)) found.push(m[1]);
  // Git Bash drive paths: /h/..., not part of a longer token.
  for (const m of command.matchAll(/(?<![\w.:/-])(\/[A-Za-z](?:\/[^\s"'`;|&<>()]*)?)(?=$|[\s"'`;|&<>()])/g)) found.push(fromGitBash(m[1]));
  // Home-relative paths.
  for (const m of command.matchAll(/(?<![\w/])(~|\$HOME|\$\{HOME\})(\/[^\s"'`;|&<>()]*)?/g)) found.push(homedir() + (m[2] || ""));
  // Punctuation that ends a sentence or a list item is not part of the path.
  return found.map((p) => p.replace(/[,.;:)\]}\\…]+$/, "")).filter(Boolean);
}

function pathsOfCall() {
  const tool = call.tool_name;
  if (tool === "Bash" || tool === "PowerShell") return pathsInCommand(String(input.command || ""));
  return [input.file_path, input.path, input.notebook_path].filter(Boolean).map(String);
}

// A working directory is recognised by what Vespera writes into it, wherever it is.
function insideAWorkingDirectory(p) {
  let dir = p;
  try {
    if (existsSync(dir) && !statSync(dir).isDirectory()) dir = dirname(dir);
  } catch {
    dir = dirname(dir);
  }
  for (let i = 0; i < 64; i++) {
    if (existsSync(join(dir, "vespera.db")) || existsSync(join(dir, "vespera.lock"))) return dir;
    const up = dirname(dir);
    if (up === dir) return null;
    dir = up;
  }
  return null;
}

const roots = allowList();
const refused = [];
for (const raw of pathsOfCall()) {
  const absolute = isAbsolute(raw) ? raw : resolve(cwd, raw);
  const p = norm(fromGitBash(absolute.replace(/\\/g, "/")));
  if (p === "/dev/null" || p.endsWith(":/dev/null")) continue;
  const allowed = roots.some((r) => p === r || p.startsWith(r + "/"));
  if (!allowed) {
    refused.push(`${raw} (outside the allow list)`);
    continue;
  }
  const workingDirectory = insideAWorkingDirectory(resolve(p));
  if (workingDirectory) refused.push(`${raw} (inside the Vespera working directory ${workingDirectory})`);
}

if (refused.length) {
  process.stderr.write(
    "Refused by .claude/hooks/private-paths-guard.mjs: Claude never opens the operator's archives or a " +
      "Vespera working directory, because their documents may be sensitive and are read only by local " +
      "models. Paths: " + refused.join("; ") + ". If a path is legitimate and holds no document, the " +
      "operator adds it to .claude/allowed-paths.local.txt.\n",
  );
  process.exit(2);
}
process.exit(0);
