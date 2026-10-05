// Refuses a Claude tool call that names a path outside the places listed in .claude/allowed-paths.txt
// (plus .claude/allowed-paths.local.txt on this machine), or a path inside a Vespera working directory
// wherever it is, or a recursive Grep or Glob that begins above one. The operator's archives can hold
// sensitive documents, and nothing that reads a document may reach a hosted model: a document is read
// only by local models. ADR-196 (docs/adr/0196) is the record and ADR-199 (docs/adr/0199) amends it, and
// src/test/hooks/private-paths-guard.test.mjs holds this file to both.
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
// environment), the value after =, a token headed by @ also without the @ (taken off as the token is
// written, before any punctuation is trimmed from its end, so that what is left is told a drive, or not,
// as written), and relative paths. On Windows, one letter and a colon at the head of a token, as written,
// is a drive whatever follows (Q:. and Q:folder/x too): the drive's root joined with the rest is judged,
// and so is the rest as a relative path, and either refuses. Each quoted string is read whole as well as
// in its pieces, a backslash before a space joins the two sides into one more reading, and each token is
// also cut at , { and } and each piece read. Four rules bear on that cut. A token that is one path
// followed only by , or } is counted once: its piece is the token with that punctuation off, which the
// token's own reading already is, so the piece's relative reading is not counted a second time. In a
// quoted string that holds no $( and no backtick, on Windows, one letter and a colon straight after { or
// , (white space may lie between), with no separator after the colon, is the key of an object and not a
// drive, as in '{t:.title}', and what follows the colon is still read as a token. The braces of a
// variable at the head of a token or of a piece, ${NAME} or ${env:NAME}, that is replaced by its value
// are no cut: the token or piece is read whole. And in a Bash command only, ${NAME-word}, ${NAME=word},
// ${NAME+word} and ${NAME?word}, each with or without a colon before the sign, is a variable with a word
// for when it has no value: its name is not read, and its word is read as a token of its own.
//
// A link is followed before the check. A path that holds .. is read twice, and either reading refuses:
// as text, where .. folds against the name before it, and as the file system walks it, which starts from
// the real path of the folder the path is read against (for an absolute path, from its root) and at each
// .. takes the parent of the real path reached so far, with a name that is not there put back as text.
// The second reading is made only when the first was not refused, since the call is refused already
// then, and not when it is the first again, which it is wherever no link is on the way; it asks the file
// system once about each folder a .. is taken from, whatever the allow list says of that folder. It is
// judged against the allowed roots and against where they lead, and in a shell command each of the two
// readings that is a folder is a folder the command names. A name that is there
// and cannot be followed to where it leads (a link to a name that is not there, a link in a loop, any
// answer of the file system but "not there") is refused, and so is everything beneath it. Only a name
// that is not there is answered from the folder it would be in: no such name, a name beneath something
// that is not a folder, a name too long and a name the platform cannot hold. The allow-list decision is
// made from a path's text before the file system is asked about it, so a refused path costs no lookup,
// and nothing beneath a refused folder is looked up: no relative path is read against the current
// directory, or a folder the command names, once that folder is itself refused. On Windows only it also
// reads Git Bash drive paths (written with forward slashes), /proc/cygdrive and /cygdrive paths, and
// UNC paths.
//
// A relative path in a command is read against the current directory and against every folder the
// command names: a token, in any form read above, that is a folder that exists, and a relative token that
// is a folder under one of those. Folders are reached along chains of tokens, and along one chain a
// token is used no more often than the command writes it, so that a .. written once climbs once and
// not again from the folder it yielded, and written twice climbs twice. Per folder the guard keeps the
// least use of each token over the chains found, which terminates. A command can change directory
// before it reads a path (cd to a folder it names, git -C, pushd, Set-Location, env -C), and no list of
// such words is kept. A bare cd, which goes to the home folder, and cd -, which goes back, name no
// folder in the text and are not followed. The call is refused if any reading of any token is outside
// the allow list or inside a working directory. The folders read against, the current directory among
// them, are bounded at DIRECTORY_LIMIT, and so are the readings at READING_LIMIT: a command that names
// one folder too many, or needs more readings, is refused, because what is left cannot be ruled out.
// One reading is one relative token against one folder, whether the path it makes is read once or twice.
//
// What it does not read, so that this is not taken for the whole of the protection (ADR-196 section 5
// is the list of what is known, and it is not complete): a path a command builds at run time (a
// command substitution, a variable set in the same command, a loop, a program's own computing); a
// variable that is not at the head of a token, and %NAME%\x as cmd writes a variable; an option with
// its value attached and no =, as in -I../x; ~name/x; a brace list with text around it, as in
// ../{a,b}/x, whose pieces are read but which is not expanded as a shell expands it; a path from the
// root of the drive without its letter, such as \archive\x; a shell wildcard; a recursive shell command
// (grep -r, rg, find) that begins above a working directory; a rooted POSIX path such as /tmp/x, so
// that cd /tmp && cat run/x reads run/x against the current directory only; a bare cd or cd -; a
// quoted string inside a quoted string, as in bash -c "cat 'my runs/x'", which is read whole only as
// the outer one; a member of a list inside quotes that is a path on a drive with no separator after the
// colon, as in bash -c "cat {x,Q:name}", whose part after the colon is read and whose drive is not; a
// link that only Git Bash's runtime follows, such as a symlink written as a file for Cygwin, which is a
// plain file to this process; a working directory that the walk down from a search root does not reach
// (below); and any tool outside the eight, among them every mcp__* tool and Monitor. A session whose
// own .claude/settings.json registers no hook never runs this file. For all of those the written rule
// in AGENTS.md is the only protection.
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

import { existsSync, lstatSync, readFileSync, readdirSync, realpathSync, statSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { basename, delimiter, dirname, isAbsolute, join, parse, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const windows = process.platform === "win32";
const here = dirname(fileURLToPath(import.meta.url));
const checkout = resolve(here, "..", "..");

const WALK_LIMIT = 10000;
const NOT_WALKED = new Set([".git", "node_modules", "target"]);
const WORKING_DIRECTORY_FILES = ["vespera.db", "vespera.lock"];
const DIRECTORY_LIMIT = 32;
const READING_LIMIT = 20000;
// What the file system answers of a name that is not there: no such name, a name beneath something that
// is not a folder, a name too long to exist, and a name the platform cannot hold. Any other answer is a
// name that is there and cannot be followed.
const NOT_THERE = new Set(["ENOENT", "ENOTDIR", "ENAMETOOLONG", "EINVAL"]);

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

// The absolute path a field or a token names when the call's current directory is base, with .. folded
// as text. The backslash separates folders in every form this reads, on every platform.
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
// and the names beneath it are put back. Asked once for a path and kept for the call.
function followed(absolute) {
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

const realPaths = new Map();
function realPath(absolute) {
  let known = realPaths.get(absolute);
  if (known === undefined) {
    known = followed(absolute);
    realPaths.set(absolute, known);
  }
  return known;
}

// The path as the file system walks it, when it holds a .. (and null when it holds none): from the real
// path of the folder it is read against, or from its root when it is absolute, and at each .. the parent
// of the real path reached so far, one realPath for each folder a .. is taken from. A name that is not
// there is put back as text, which is what realPath does with it.
function walkedOf(text, base) {
  const s = fromGitBash(String(text).replace(/\\/g, "/"));
  if (!s.split("/").includes("..")) return null;
  const { root } = parse(s);
  let current = root ? resolve(base, root) : realPath(base);
  for (const name of s.slice(root.length).split("/")) {
    if (!name || name === ".") continue;
    current = name === ".." ? dirname(realPath(current)) : join(current, name);
  }
  return current;
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

// What the file system says of the name itself, asked of lstat first: that it is not there, that it is
// there and cannot be followed to where it leads, or that it is there, whether it is a folder, and the
// real path it leads to.
function lookUp(absolute) {
  try {
    lstatSync(absolute);
  } catch (caught) {
    if (NOT_THERE.has(caught?.code)) return { there: false };
    return { blocked: `${absolute} is a name the file system would not answer for (${caught?.code ?? caught})` };
  }
  try {
    const stat = statSync(absolute);
    return { there: true, folder: stat.isDirectory(), real: realpathSync.native(absolute) };
  } catch (caught) {
    return { blocked: `${absolute} is there and cannot be followed to where it leads (${caught?.code ?? caught})` };
  }
}

// What the file system says of a path that the text has already allowed: whether it is there and is a
// folder, where its links lead, the working directory it is inside, if any, and whether it is a name that
// is there and cannot be followed, which refuses it and everything beneath it. Only a name that is not
// there (see NOT_THERE) is answered from its parent, so a name that does not exist costs one question and
// no more.
const described = new Map();
function describe(absolute) {
  const known = described.get(absolute);
  if (known) return known;
  const found = lookUp(absolute);
  let result;
  if (found.blocked) {
    result = { exists: true, folder: false, real: absolute, inside: null, blocked: found.blocked };
  } else if (found.there) {
    result = {
      exists: true,
      folder: found.folder,
      real: found.real,
      inside: workingDirectoryAbove(absolute) ?? workingDirectoryAbove(found.real),
      blocked: null,
    };
  } else {
    const parent = dirname(absolute);
    if (parent === absolute) {
      result = { exists: false, folder: false, real: absolute, inside: null, blocked: null };
    } else {
      const above = describe(parent);
      result = {
        exists: false,
        folder: false,
        real: resolve(above.real, basename(absolute)),
        inside: above.inside,
        blocked: above.blocked,
      };
    }
  }
  described.set(absolute, result);
  return result;
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

const COMMAND_TOKENS = /[^\s"'`;|&<>()]+/g;
const QUOTED_STRINGS = /'([^']*)'|"([^"]*)"/g;
// A variable in braces at the head of a text that readToken replaces by its value: ${NAME} or
// ${env:NAME}, with a separator or the end after it.
const BRACED_VARIABLE = /^\$\{(?:env:)?(\w+)\}(?=$|[\\/])/i;
// A Bash parameter expansion with a word for when the variable has no value.
const PARAMETER_WITH_WORD = /\$\{\w+:?[-=+?]([^}]*)\}/g;

// The pieces of a text cut at , { and }, each with the character it follows (null for the first). The
// braces of a variable at the head of a piece that is replaced by its value are no cut.
function cut(text) {
  const parts = [];
  let start = 0;
  let before = null;
  for (let i = 0; i <= text.length; i++) {
    if (i === start) {
      const head = BRACED_VARIABLE.exec(text.slice(i));
      if (head && valueOf(head[1])) i += head[0].length;
    }
    if (i === text.length || ",{}".includes(text[i])) {
      parts.push({ piece: text.slice(start, i), before });
      before = text[i];
      start = i + 1;
    }
  }
  return parts;
}

// What a command's text names: absolute paths, each as { label, text }, where text is read against the
// current directory by the caller, and relative ones, each as rel -> { label, written }, where written
// is how many times the command writes that token. A relative one is read against a directory by the
// caller. bash says whether ${NAME-word} and its kin are parameter expansions.
function pathsInCommand(command, bash) {
  const absolute = [];
  const relative = new Map();
  // The relative paths a token's own reading made, while it is being recorded, and the ones a piece of
  // that token must not count again.
  let recorded = null;
  let uncounted = null;
  const addAbsolute = (label, text) => absolute.push({ label, text });
  const addRelative = (label, rel) => {
    recorded?.add(rel);
    if (uncounted?.has(rel)) return;
    const known = relative.get(rel);
    if (known) known.written++;
    else relative.set(rel, { label, written: 1 });
  };

  // A drive path, Q:\x or Q:/x, with one separator after the colon or several, wherever in the text it
  // is. A URL's scheme is longer than one letter, so the s of https is preceded by a letter and is not
  // a drive.
  for (const m of command.matchAll(/(?<![A-Za-z])([A-Za-z]):[\\/]+([^\s"'`;|&<>()]*)/g)) {
    addAbsolute(m[0], drive(m[1], trimSentence(m[2])));
  }

  // key says that the token is the key of an object in a quoted string, which is not a drive.
  const readToken = (token, key = false) => {
    // An @ is taken off the token as it is written, before any punctuation is trimmed from its end, so
    // that what is left is told a drive, or not, as written.
    if (token.startsWith("@")) readToken(token.slice(1));
    // One letter and a colon at the head of the token as it is written is a drive, before any
    // punctuation is trimmed off its end: trimmed first, Q:. would lose its dot and its colon.
    const head = /^([A-Za-z]):([\s\S]*)$/.exec(token);
    if (head) {
      const [, letter, rest] = head;
      if (rest === "") return addAbsolute(token, drive(letter, ""));
      if (windows && key && !/^[\\/]/.test(rest)) {
        // The key of an object in a quoted string, as in '{t:.title}': what follows the colon is read as
        // a token, and the key is no drive.
        return readToken(rest);
      }
      if (windows && !/^[\\/]/.test(rest)) {
        // Q:folder/x is a path on drive Q with no separator after the colon. It is a path from that
        // drive's own current directory, which is its root unless something changed it, so it is read
        // from the root and as a relative path from where the command runs, and either may refuse.
        for (const part of new Set([rest, trimSentence(rest)])) {
          if (!part) continue;
          addAbsolute(token, drive(letter, part));
          addRelative(token, part.replace(/\\/g, "/"));
        }
        return;
      }
    }
    const t = trimSentence(token);
    if (!t) return;
    if (t.startsWith("-")) return;
    if (/^[A-Za-z][A-Za-z0-9+.-]+:\/\//.test(t)) return;
    const driven = /^([A-Za-z]):[\\/]+([\s\S]*)$/.exec(t);
    if (driven) return addAbsolute(t, drive(driven[1], trimSentence(driven[2])));
    const s = t.replace(/\\/g, "/");
    if (/^~(?=$|\/)/.test(s)) return addAbsolute(t, homedir() + s.slice(1));
    const variable = /^\$(?:(?:\{env:|env:)(\w+)\}?|(\w+)|\{(\w+)\})(?=$|\/)/i.exec(s);
    if (variable) {
      const value = valueOf(variable[1] ?? variable[2] ?? variable[3]);
      if (!value) return;
      const joined = value + s.slice(variable[0].length);
      if (isAbsolute(fromGitBash(joined.replace(/\\/g, "/")))) return addAbsolute(t, joined);
      return addRelative(t, joined.replace(/\\/g, "/"));
    }
    if (s.startsWith("$")) return;
    if (windows) {
      if (/^(?:\/\/|\\\\)[^\\/]+[\\/][^\\/]/.test(t)) return addAbsolute(t, s);
      const gitBash = fromGitBash(t);
      if (gitBash !== t) return addAbsolute(t, gitBash);
    }
    // A path rooted on the current drive without its letter, \archive\x or /archive/x, was left
    // uncovered on the operator's word of 2026-10-05, because it has the shape of an escape or a
    // pattern such as '\s'. Other rooted POSIX paths, /tmp/x or /etc/x, are not decided. Neither is read.
    if (s.startsWith("/")) return;
    addRelative(t, s);
  };

  // A token, and the value after its first =.
  const readOne = (text, key = false) => {
    readToken(text, key);
    const equals = text.indexOf("=");
    if (equals >= 0) readToken(text.slice(equals + 1));
  };
  // A token, and each piece of it cut at , { and }: a brace list or a comma list carries paths that
  // are not at the head of the token. The braces of a variable that is replaced by its value, at the
  // head of the token or of a piece, are no cut, and neither are those of a Bash ${NAME-word}, whose word
  // is read as a token of its own. A token that is one path followed only by , or } is read once and not
  // again as its own piece, so that a .. written once climbs once. key says that the token's head follows
  // { or , in a quoted string that holds no $( and no backtick, and quoted that the token is in one: the
  // piece after a { or a , in it may be the key of an object.
  const read = (text, key = false, quoted = false) => {
    const body = bash
      ? text.replace(PARAMETER_WITH_WORD, (_, word) => {
          read(word, false, quoted);
          return "}";
        })
      : text;
    const parts = cut(body);
    const single = parts.length > 1 && parts[0].piece !== "" && parts.slice(1).every((p) => p.piece === "" && p.before !== "{");
    if (single) recorded = new Set();
    readOne(text, key);
    if (single) {
      uncounted = recorded;
      recorded = null;
    }
    if (parts.length > 1) {
      for (const { piece, before } of parts) {
        if (piece) readOne(piece, before === null ? key : quoted && (before === "{" || before === ","));
      }
    }
    uncounted = null;
  };

  // A quoted string is a path with its spaces in it as often as it is a sentence. One that holds no $(
  // and no backtick is safe from a second shell's command substitution, so what is cut out of it can
  // be the key of an object.
  const quotes = [];
  for (const m of command.matchAll(QUOTED_STRINGS)) {
    const whole = m[1] ?? m[2];
    quotes.push({ from: m.index + 1, to: m.index + m[0].length - 1, whole, safe: !/\$\(|`/.test(whole) });
  }

  let q = 0;
  for (const m of command.matchAll(COMMAND_TOKENS)) {
    while (q < quotes.length && quotes[q].to <= m.index) q++;
    const inside = q < quotes.length && quotes[q].from <= m.index ? quotes[q] : null;
    let key = false;
    if (inside?.safe) {
      let k = m.index - 1;
      while (k >= inside.from && /\s/.test(command[k])) k--;
      key = k >= inside.from && (command[k] === "{" || command[k] === ",");
    }
    read(m[0], key, Boolean(inside?.safe));
  }
  // A backslash before a space joins what is on either side of it into one path.
  for (const m of command.matchAll(/(?:\\ |[^\s"'`;|&<>()])+/g)) {
    if (m[0].includes("\\ ")) read(m[0].replace(/\\ /g, " "));
  }
  for (const { whole, safe } of quotes) if (whole) read(whole, false, safe);
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
  const decided = new Map();

  // Whether the path is refused, and the reason is recorded once. A path read as the file system walks it
  // is a real path, so it is judged against where the allowed roots lead as well as against their text.
  function check(label, absolute, searchRoot = false, walked = false) {
    const text = norm(absolute);
    const key = `${text}|${searchRoot}|${walked}`;
    if (decided.has(key)) return decided.get(key);
    const turnedDown = (reason) => {
      refused.push(`${label} (${reason})`);
      decided.set(key, true);
      return true;
    };
    if (text === "/dev/null" || text.endsWith(":/dev/null")) {
      decided.set(key, false);
      return false;
    }
    if (!within(text, roots) && !(walked && within(text, realRoots))) return turnedDown("outside the allow list");
    const found = describe(absolute);
    if (found.blocked) return turnedDown(`${found.blocked}, so it is refused, and so is everything beneath it`);
    const realText = norm(found.real);
    if (!within(realText, roots) && !within(realText, realRoots)) {
      return turnedDown(`a link leads from it to ${found.real}, outside the allow list`);
    }
    if (found.inside) return turnedDown(`inside the Vespera working directory ${found.inside}`);
    if (searchRoot) {
      const below = workingDirectoryBelow(found.real);
      if (below.found) return turnedDown(`a recursive search from it reaches the Vespera working directory ${below.found}`);
      if (!below.complete) {
        return turnedDown(`ruling out a working directory beneath it would take more than ${WALK_LIMIT} folders, so search a narrower one`);
      }
    }
    decided.set(key, false);
    return false;
  }

  // Each reading of a path against a base: as text and, when it holds a .. that a link makes mean
  // something else, as the file system walks it. Returns each with whether it was refused. The second is
  // made only when the first was not refused, since the call is refused already then and nothing about
  // a refused path is looked up, and not when it is the first again, which is every path with no link on
  // the way. A base that was itself read as walked makes a real path, so its readings are judged as walked.
  const checkReadings = (label, text, base, searchRoot = false, baseWalked = false) => {
    const asText = absoluteOf(text, base);
    const readings = [{ absolute: asText, turnedDown: check(label, asText, searchRoot, baseWalked) }];
    if (readings[0].turnedDown) return readings;
    const walked = walkedOf(text, base);
    if (walked && norm(walked) !== norm(asText)) {
      readings.push({ absolute: walked, turnedDown: check(`${label}, read as the file system walks its ..`, walked, searchRoot, true) });
    }
    return readings;
  };
  const currentLabel = `the current directory, ${currentDirectory}`;

  if (tool === "Bash" || tool === "PowerShell") {
    const currentChecked = checkReadings(currentLabel, currentDirectory, checkout);
    const { absolute, relative } = pathsInCommand(String(input.command ?? ""), tool === "Bash");

    // The folders the command names, which a relative path may be read against, each with the least use
    // of every relative token over the chains of tokens that reach it. A folder that is refused is not
    // one: nothing is looked up beneath it, and it is refused already.
    const folders = new Map();
    const queue = [];
    let tooMany = false;
    const reach = (path, use) => {
      const key = norm(path);
      const known = folders.get(key);
      if (!known) {
        if (folders.size >= DIRECTORY_LIMIT) {
          if (!tooMany) {
            refused.push(`the command names more than ${DIRECTORY_LIMIT - 1} folders besides the current directory, so what its relative paths are read against cannot be ruled out; split it`);
          }
          tooMany = true;
          return;
        }
        const entry = { path, key, use: new Map(use), queued: true };
        folders.set(key, entry);
        queue.push(entry);
        return;
      }
      let lowered = false;
      for (const [token, used] of known.use) {
        const viaThisChain = use.get(token) ?? 0;
        if (viaThisChain >= used) continue;
        lowered = true;
        if (viaThisChain === 0) known.use.delete(token);
        else known.use.set(token, viaThisChain);
      }
      if (lowered && !known.queued) {
        known.queued = true;
        queue.push(known);
      }
    };

    for (const { absolute: path, turnedDown } of currentChecked) if (!turnedDown) reach(path, new Map());
    for (const { label, text } of absolute) {
      for (const { absolute: path, turnedDown } of checkReadings(label, text, cwd)) {
        if (!turnedDown && describe(path).folder) reach(path, new Map());
      }
    }

    let readings = 0;
    const answered = new Map();
    while (queue.length) {
      const entry = queue.shift();
      entry.queued = false;
      for (const [rel, { label, written }] of relative) {
        const used = entry.use.get(rel) ?? 0;
        if (used >= written) continue;
        const key = `${entry.key}|${rel}`;
        let answer = answered.get(key);
        if (!answer) {
          if (++readings > READING_LIMIT) {
            refused.push(`the command's relative paths need more than ${READING_LIMIT} readings against the folders it names, so what they reach cannot be ruled out; split it`);
            return refused;
          }
          // A plain name that is not there is no more than the folder it would be in, which was checked.
          const plain = !/[\\/]/.test(rel) && rel !== "." && rel !== "..";
          const named = entry.key === norm(cwd) ? label : `${label}, read against ${entry.path}`;
          const asText = absoluteOf("./" + rel, entry.path);
          const found = plain ? describe(asText) : null;
          if (found && !found.exists && !found.blocked) {
            answer = [{ abs: asText, folder: false }];
          } else {
            answer = checkReadings(named, "./" + rel, entry.path).map(({ absolute: abs, turnedDown }) => ({
              abs,
              folder: !turnedDown && describe(abs).folder,
            }));
          }
          answered.set(key, answer);
        }
        for (const { abs, folder } of answer) {
          if (!folder) continue;
          const use = new Map(entry.use);
          use.set(rel, used + 1);
          reach(abs, use);
        }
      }
    }
    return refused;
  }

  const searches = tool === "Grep" || tool === "Glob";
  for (const field of searches ? ["file_path", "notebook_path"] : ["file_path", "path", "notebook_path"]) {
    if (input[field]) checkReadings(String(input[field]), input[field], cwd);
  }
  if (searches) {
    const starts = input.path
      ? checkReadings(String(input.path), input.path, cwd, true)
      : checkReadings(currentLabel, currentDirectory, checkout, true);
    const pattern = tool === "Glob" ? input.pattern : input.glob;
    if (pattern) {
      const prefix = staticPrefix(String(pattern).replace(/\\/g, "/"));
      // The folder the search begins in is the start, or the prefix read against it; a start that is
      // refused is refused already.
      starts.forEach(({ absolute, turnedDown }, i) => {
        if (turnedDown) return;
        if (prefix === "") check(String(pattern), absolute, true, i > 0);
        else checkReadings(String(pattern), prefix, absolute, true, i > 0);
      });
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
