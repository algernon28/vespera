// Refuses a Claude tool call that names a path outside the places listed in .claude/allowed-paths.txt
// (plus .claude/allowed-paths.local.txt on this machine), or a path inside a Vespera working directory
// wherever it is, or a recursive Grep or Glob that begins above one. The operator's archives can hold
// sensitive documents, and nothing that reads a document may reach a hosted model: a document is read
// only by local models. docs/adr/0196 is the record, and src/test/hooks/private-paths-guard.test.mjs
// holds this file to it.
//
// It is registered for eight tools: Read, Grep, Glob, Edit, Write, NotebookEdit, Bash and PowerShell.
// It is an allow list and not a list of archives, so an archive on a new path is refused without anyone
// naming it. A refused path that is legitimate is added to the allow list, so inside what it reads a
// mistake costs a refusal and not an exposed document.
//
// What it reads of a call: a file tool's own path fields; Grep's glob and Glob's pattern; the current
// directory (the call's cwd, or this checkout when the call gives none) of a Grep or Glob without a path
// and of a Bash or PowerShell command; and the text of that command. In the text it reads drive paths,
// a bare drive, ~ and a variable at the head of a token (replaced by its value in this process's
// environment), the value after =, a token headed by @ also without the @, and relative paths. Each
// quoted string is read whole as well as in its pieces. A link is followed before the check, and the
// allow-list decision is made from a path's text before the file system is asked about it, so a refused
// path costs no lookup. On Windows only it also reads Git Bash drive paths (written with forward
// slashes), /proc/cygdrive and /cygdrive paths, and UNC paths.
//
// A relative path in a command is read against the current directory and against every folder the
// command names: a token, in any form read above, that is a folder that exists, and a relative token that
// is a folder under one of those, repeated until nothing is new. A command can change directory before
// it reads a path (cd, git -C, pushd, Set-Location, env -C), and no list of such words is kept. The call
// is refused if any reading of any token is outside the allow list or inside a working directory. The
// folders named are bounded: more than DIRECTORY_LIMIT of them, or more than READING_LIMIT readings,
// refuses the call, because what is left cannot be ruled out.
//
// What it does not read, so that this is not taken for the whole of the protection (ADR-196 section 5
// is the full list): a path a command builds at run time (a command substitution, a variable set in the
// same command, a loop, a program's own computing); a variable that is not at the head of a token; an
// option with its value attached and no =, as in -I../x; ~name/x; a path from the root of the drive
// without its letter, such as \archive\x; a shell wildcard; a recursive shell command (grep -r, rg,
// find) that begins above a working directory; a rooted POSIX path such as /tmp/x; a working directory
// that the walk down from a search root does not reach (below); and any tool outside the eight, among
// them every mcp__* tool and Monitor. A session whose own .claude/settings.json registers no hook never
// runs this file. For all of those the written rule in AGENTS.md is the only protection.
//
// A Grep or Glob root that holds a working directory at any depth is refused, because a recursive
// search reaches into it. That walk down is breadth-first, looks at no more than WALK_LIMIT folders,
// never enters .git, node_modules or target, never follows a link, passes over a folder it cannot
// list, and stops at the first hit. A working directory inside one of those three folders, behind a
// link, or in a folder it cannot list is therefore not found. A search that would need more than
// WALK_LIMIT folders is refused: it cannot be ruled out.
//
// It fails closed. Input it cannot read, input that names no tool, and any exception end in exit
// code 2, which is the only code Claude Code treats as a refusal.
//
// Protocol: Claude Code passes the tool call as JSON on stdin. Exit code 2 refuses it, exit code 0
// lets it through, and stderr is what Claude reads.

import { existsSync, readFileSync, readdirSync, realpathSync, statSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { basename, delimiter, dirname, isAbsolute, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const windows = process.platform === "win32";
const here = dirname(fileURLToPath(import.meta.url));
const checkout = resolve(here, "..", "..");

const WALK_LIMIT = 10000;
const NOT_WALKED = new Set([".git", "node_modules", "target"]);
const WORKING_DIRECTORY_FILES = ["vespera.db", "vespera.lock"];
const DIRECTORY_LIMIT = 32;
const READING_LIMIT = 20000;

const norm = (p) => resolve(p).replace(/\\/g, "/").replace(/\/+$/, "").toLowerCase();
const within = (path, roots) => roots.some((r) => path === r || path.startsWith(r + "/"));
const drive = (letter, rest) => `${letter.toUpperCase()}:/${rest}`;

// A Git Bash path such as /h/archive, or its /proc/cygdrive/h/archive form, is the drive path H:/archive.
// Only the forward slash makes one: a token led by a backslash is an escape or a pattern, not a drive.
function fromGitBash(p) {
  if (!windows) return p;
  const m = /^\/(?:proc\/cygdrive\/|cygdrive\/)?([A-Za-z])(?:\/(.*))?$/.exec(p);
  return m ? drive(m[1], m[2] ?? "") : p;
}

// The absolute path a field or a token names when the call's current directory is base. The backslash
// separates folders in every form this reads, on every platform.
function absoluteOf(text, base) {
  return resolve(base, fromGitBash(String(text).replace(/\\/g, "/")));
}

const isDirectory = (p) => {
  try {
    return statSync(p).isDirectory();
  } catch {
    return false;
  }
};

// The path with its links followed, as far as it exists: the deepest folder that exists is resolved
// and the names beneath it are put back.
function realPath(absolute) {
  const tail = [];
  let current = absolute;
  for (let i = 0; i < 512; i++) {
    try {
      const resolved = realpathSync.native(current);
      return tail.length ? resolve(resolved, ...tail.reverse()) : resolved;
    } catch {
      // not there: ask about its parent
    }
    const parent = dirname(current);
    if (parent === current) return absolute;
    tail.push(basename(current));
    current = parent;
  }
  return absolute;
}

const tokens = {
  "${REPO}": checkout,
  "${HOME}": homedir(),
  "${TEMP}": process.env.TEMP || process.env.TMPDIR || tmpdir(),
};

function allowedRoots() {
  const roots = [];
  for (const name of ["allowed-paths.txt", "allowed-paths.local.txt"]) {
    const file = join(here, "..", name);
    if (!existsSync(file)) continue;
    for (let line of readFileSync(file, "utf8").split(/\r?\n/)) {
      line = line.trim();
      if (!line || line.startsWith("#")) continue;
      for (const [k, v] of Object.entries(tokens)) line = line.split(k).join(v);
      roots.push(resolve(line));
    }
  }
  return roots;
}

const holdsCache = new Map();
function holdsWorkingDirectory(dir) {
  let held = holdsCache.get(dir);
  if (held === undefined) {
    held = WORKING_DIRECTORY_FILES.some((name) => existsSync(join(dir, name)));
    holdsCache.set(dir, held);
  }
  return held;
}

// A working directory is recognised by what Vespera writes into it, wherever it is.
function workingDirectoryAbove(absolute) {
  let dir = absolute;
  try {
    if (existsSync(dir) && !statSync(dir).isDirectory()) dir = dirname(dir);
  } catch {
    dir = dirname(dir);
  }
  for (let i = 0; i < 64; i++) {
    if (holdsWorkingDirectory(dir)) return dir;
    const parent = dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
  return null;
}

// The first working directory at any depth beneath a folder, breadth-first, and whether the walk
// reached the end of what it was willing to look at.
function workingDirectoryBelow(absolute) {
  if (!isDirectory(absolute)) return { found: null, complete: true };
  const queue = [absolute];
  for (let next = 0; next < queue.length; next++) {
    if (next >= WALK_LIMIT) return { found: null, complete: false };
    let entries;
    try {
      entries = readdirSync(queue[next], { withFileTypes: true });
    } catch {
      continue;
    }
    if (entries.some((e) => WORKING_DIRECTORY_FILES.includes(e.name.toLowerCase()))) {
      return { found: queue[next], complete: true };
    }
    for (const entry of entries) {
      if (!entry.isDirectory() || NOT_WALKED.has(entry.name)) continue;
      if (queue.length >= WALK_LIMIT) return { found: null, complete: false };
      queue.push(join(queue[next], entry.name));
    }
  }
  return { found: null, complete: true };
}

// The punctuation that ends a sentence or a list item is not part of a path. A trailing .. is the
// parent folder and not punctuation, and neither is a trailing single dot after a separator.
function trimSentence(p) {
  for (;;) {
    if (/(^|[\\/])\.{1,2}$/.test(p)) return p;
    const shorter = p.replace(/[,.;:)\]}…]$/, "");
    if (shorter === p) return p;
    p = shorter;
  }
}

function valueOf(name) {
  const value = name.toUpperCase() === "HOME" ? homedir() : process.env[name];
  // A list of paths, such as PATH, is not one path.
  return value && !value.includes(delimiter) ? value : "";
}

const COMMAND_BREAKS = /[\s"'`;|&<>()]+/;

// What a command's text names: absolute paths, each as { label, abs }, and relative ones, each as
// { label, rel }, which are read against a directory by the caller.
function pathsInCommand(command, cwd) {
  const absolute = [];
  const relative = new Map();
  const addAbsolute = (label, abs) => absolute.push({ label, abs });
  const addRelative = (label, rel) => {
    if (!relative.has(rel)) relative.set(rel, label);
  };

  // A drive path, Q:\x or Q:/x, with one separator after the colon or several, wherever in the text it
  // is. A URL's scheme is longer than one letter, so the s of https is preceded by a letter and is not
  // a drive.
  for (const m of command.matchAll(/(?<![A-Za-z])([A-Za-z]):[\\/]+([^\s"'`;|&<>()]*)/g)) {
    addAbsolute(m[0], absoluteOf(drive(m[1], trimSentence(m[2])), cwd));
  }

  const readToken = (token) => {
    if (/^[A-Za-z]:$/.test(token)) return addAbsolute(token, absoluteOf(drive(token[0], ""), cwd));
    const t = trimSentence(token);
    if (!t) return;
    if (t.startsWith("@")) readToken(t.slice(1));
    if (t.startsWith("-")) return;
    if (/^[A-Za-z][A-Za-z0-9+.-]+:\/\//.test(t)) return;
    const driven = /^([A-Za-z]):[\\/]+([\s\S]*)$/.exec(t);
    if (driven) return addAbsolute(t, absoluteOf(drive(driven[1], trimSentence(driven[2])), cwd));
    const s = t.replace(/\\/g, "/");
    if (/^~(?=$|\/)/.test(s)) return addAbsolute(t, absoluteOf(homedir() + s.slice(1), cwd));
    const variable = /^\$(?:(?:\{env:|env:)(\w+)\}?|(\w+)|\{(\w+)\})(?=$|\/)/i.exec(s);
    if (variable) {
      const value = valueOf(variable[1] ?? variable[2] ?? variable[3]);
      if (!value) return;
      const joined = value + s.slice(variable[0].length);
      if (isAbsolute(fromGitBash(joined.replace(/\\/g, "/")))) return addAbsolute(t, absoluteOf(joined, cwd));
      return addRelative(t, joined.replace(/\\/g, "/"));
    }
    if (s.startsWith("$")) return;
    if (windows) {
      if (/^(?:\/\/|\\\\)[^\\/]+[\\/][^\\/]/.test(t)) return addAbsolute(t, absoluteOf(s, cwd));
      const gitBash = fromGitBash(t);
      if (gitBash !== t) return addAbsolute(t, absoluteOf(gitBash, cwd));
    }
    // Any other rooted path, /tmp/x or /etc/x or \archive\x, is not decided and is not read.
    if (s.startsWith("/")) return;
    addRelative(t, s);
  };

  const read = (text) => {
    readToken(text);
    const equals = text.indexOf("=");
    if (equals >= 0) readToken(text.slice(equals + 1));
  };

  for (const token of command.split(COMMAND_BREAKS)) if (token) read(token);
  // A quoted string is a path with its spaces in it as often as it is a sentence.
  for (const m of command.matchAll(/'([^']*)'|"([^"]*)"/g)) {
    const whole = m[1] ?? m[2];
    if (whole) read(whole);
  }
  return { absolute, relative };
}

// The part of a search pattern before its first wildcard, which is the folder the search begins in.
function staticPrefix(pattern) {
  const kept = [];
  for (const segment of pattern.split("/")) {
    if (/[*?[\]{}]/.test(segment)) break;
    kept.push(segment);
  }
  let prefix = kept.join("/");
  if (/^[A-Za-z]:$/.test(prefix)) prefix += "/";
  if (prefix === "" && pattern.startsWith("/")) prefix = "/";
  return prefix;
}

function refusalsOf(call) {
  const tool = call.tool_name;
  const input = call.tool_input && typeof call.tool_input === "object" ? call.tool_input : {};
  const currentDirectory = typeof call.cwd === "string" && call.cwd ? call.cwd : checkout;
  const cwd = absoluteOf(currentDirectory, checkout);

  const rootPaths = allowedRoots();
  const roots = rootPaths.map(norm);
  const realRoots = rootPaths.map((p) => norm(realPath(p)));

  const refused = [];
  const seen = new Set();

  function check(label, absolute, searchRoot = false) {
    const text = norm(absolute);
    const key = `${text}|${searchRoot}`;
    if (seen.has(key)) return;
    seen.add(key);
    if (text === "/dev/null" || text.endsWith(":/dev/null")) return;
    if (!within(text, roots)) {
      refused.push(`${label} (outside the allow list)`);
      return;
    }
    const real = realPath(absolute);
    const realText = norm(real);
    if (!within(realText, roots) && !within(realText, realRoots)) {
      refused.push(`${label} (a link leads from it to ${real}, outside the allow list)`);
      return;
    }
    const inside = workingDirectoryAbove(absolute) ?? workingDirectoryAbove(real);
    if (inside) {
      refused.push(`${label} (inside the Vespera working directory ${inside})`);
      return;
    }
    if (!searchRoot) return;
    const below = workingDirectoryBelow(real);
    if (below.found) {
      refused.push(`${label} (a recursive search from it reaches the Vespera working directory ${below.found})`);
    } else if (!below.complete) {
      refused.push(
        `${label} (ruling out a working directory beneath it would take more than ${WALK_LIMIT} folders, so search a narrower one)`,
      );
    }
  }

  if (tool === "Bash" || tool === "PowerShell") {
    check(`the current directory, ${currentDirectory}`, cwd);
    const { absolute, relative } = pathsInCommand(String(input.command ?? ""), cwd);

    // The folders the command names, which a relative path may be read against. Only a folder inside the
    // allow list is asked about: one outside it is refused from its text.
    const directories = [cwd];
    const named = new Set([norm(cwd)]);
    let tooMany = false;
    const name = (abs) => {
      const text = norm(abs);
      if (named.has(text) || !within(text, roots) || !isDirectory(abs)) return;
      if (directories.length >= DIRECTORY_LIMIT) {
        if (!tooMany) refused.push(`the command names more than ${DIRECTORY_LIMIT} folders, so what its relative paths are read against cannot be ruled out; split it`);
        tooMany = true;
        return;
      }
      named.add(text);
      directories.push(abs);
    };

    for (const { label, abs } of absolute) {
      check(label, abs);
      name(abs);
    }
    let readings = 0;
    for (let i = 0; i < directories.length; i++) {
      for (const [rel, label] of relative) {
        if (++readings > READING_LIMIT) {
          refused.push(`the command's relative paths need more than ${READING_LIMIT} readings against the folders it names, so what they reach cannot be ruled out; split it`);
          return refused;
        }
        const abs = resolve(directories[i], "./" + rel);
        // A plain name that is not there is no more than the folder it would be in, which was checked.
        if (!/[\\/]/.test(rel) && rel !== "." && rel !== ".." && !existsSync(abs)) continue;
        check(i === 0 ? label : `${label}, read against ${directories[i]}`, abs);
        name(abs);
      }
    }
    return refused;
  }

  const searches = tool === "Grep" || tool === "Glob";
  for (const field of searches ? ["file_path", "notebook_path"] : ["file_path", "path", "notebook_path"]) {
    if (input[field]) check(String(input[field]), absoluteOf(input[field], cwd));
  }
  if (searches) {
    const root = input.path ? absoluteOf(input.path, cwd) : cwd;
    check(input.path ? String(input.path) : `the current directory, ${currentDirectory}`, root, true);
    const pattern = tool === "Glob" ? input.pattern : input.glob;
    if (pattern) {
      const prefix = staticPrefix(String(pattern).replace(/\\/g, "/"));
      check(String(pattern), prefix === "" ? root : absoluteOf(prefix, root), true);
    }
  }
  return refused;
}

function refusal(reason) {
  process.stderr.write(`Refused by .claude/hooks/private-paths-guard.mjs: ${reason}\n`);
  return 2;
}

function main() {
  let call;
  try {
    call = JSON.parse(readFileSync(0, "utf8"));
  } catch {
    return refusal("its input is empty or is not JSON, so there is no call to check.");
  }
  if (!call || typeof call !== "object" || Array.isArray(call) || typeof call.tool_name !== "string" || !call.tool_name) {
    return refusal("its input names no tool, so there is no call to check.");
  }
  let refused;
  try {
    refused = refusalsOf(call);
  } catch (caught) {
    return refusal(`it failed before it reached a decision (${caught?.message ?? caught}), and a call it cannot decide on is refused.`);
  }
  if (!refused.length) return 0;
  return refusal(
    "Claude never opens the operator's archives or a Vespera working directory, because their " +
      "documents may be sensitive and are read only by local models. Paths: " + refused.join("; ") +
      ". If a path is legitimate and holds no document, the operator adds it to " +
      ".claude/allowed-paths.local.txt.",
  );
}

process.exitCode = main();
