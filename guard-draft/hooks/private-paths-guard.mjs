// Refuses a Claude tool call that names a path outside the places listed in .claude/allowed-paths.txt
// (plus .claude/allowed-paths.local.txt on this machine), or a path inside a Vespera working directory
// wherever it is, or a recursive Grep or Glob that begins above one, or, to a tool that writes and to a
// shell command, a path in a .claude folder that is closed (below). The operator's archives can hold
// sensitive documents, and nothing that reads a document may reach a hosted model: a document is read
// only by local models. ADR-196 (docs/adr/0196) is the record, ADR-201 (docs/adr/0201), ADR-215
// (docs/adr/0215) and ADR-217 (docs/adr/0217) amend it, and src/test/hooks/private-paths-guard.test.mjs
// holds this file to all four.
//
// It is registered for eight tools: Read, Grep, Glob, Edit, Write, NotebookEdit, Bash and PowerShell.
// It is an allow list and not a list of archives, so an archive on a new path is refused without anyone
// naming it. A refused path that is legitimate and is not in a closed .claude folder is added to the
// allow list, so inside what it reads a mistake costs a refusal and not an exposed document.
//
// A line of an allow list headed by the word read and one space names a place for reading: Read, Grep and
// Glob are let through beneath it, and Edit, Write, NotebookEdit and a Bash or PowerShell command that
// names a path beneath it are refused, since a command's text does not say whether it writes. Where a
// path lies beneath a read line and a plain one, the read line decides. A line that is the word read
// alone, or read and only white space, names no place and admits nothing. And whatever the lists say, a
// .claude folder is closed (ADR-215): a path is closed when one of its folders is named .claude and that
// name is the last of the path or is followed by any name but projects, plans or worktrees, and the path
// is read on from there, so a .claude further down closes what is beneath it. That holds for every folder
// named .claude under an allowed root, a checkout's or not, and on each reading of a path made below: its
// text, the path as the file system walks it, and where its links lead. A name is compared with its case
// folded, and on Windows with the dots and spaces that end it taken off, because PowerShell opens .claude.
// as .claude and Node does not; on Windows a stream name after a colon is taken off a name as well,
// since it leads to the folder it is a stream of. The three open names are written in this file, and no line of a list opens a
// closed path. A closed path is refused to Edit, Write and NotebookEdit and to a Bash or PowerShell
// command that names it, as a path, as its current directory or as a folder it names, and is let through
// to Read, Grep and Glob. A closed path is judged from its text before the file system is asked about it,
// and again, after that question, on where its links lead.
//
// A Bash or PowerShell command, but the one admitted below, is also refused for the text of any token that
// spells a closed .claude folder, whether or not the token is read as a path below (ADR-215 section
// 3(c)), so that a token headed by two variables, by a variable that has no value, by an option with its
// value attached, or by a root with no drive is not let through for not being read. The token is cut at
// / and \ and its .. folded against the name before it. A name of it is .claude, with its case folded and
// any dots, spaces, stream name and sentence punctuation after it taken off, only when nothing stands
// before it in the name, or what stands there ends in one of = : , { } @ or in a variable ($NAME,
// ${NAME}, ${env:NAME}, $env:NAME or %NAME%); so main..claude, notes.claude and .claude.json are not that
// name. The one exception is the first name of a token headed by -, where an option's letters run into its
// value: it is that name whatever stands before .claude in it, so --author=someone.claude is refused. The
// command is refused when
// such a name is the last of the token or is followed by any name but projects, plans or worktrees. That
// next name is compared by the one function the path rule uses as well: with its case folded, and, where
// it is the last name of the token, with sentence punctuation, dots and spaces taken off, so that
// ".claude/worktrees, and more" is open; anywhere else exactly as a path is compared, with the dots and
// spaces that end it taken off on Windows and nowhere else, so ".claude/worktrees,/x" is closed. The
// token is read on from an open name. So the text rule is never more lenient than the path rule, except
// on the last name of a token, where sentence punctuation is taken off on purpose. It refuses more than
// the path rule in these places, each on purpose: sentence punctuation after .claude itself; a dot after
// .claude where the system is not Windows; a stream name after .claude where the system is not Windows;
// the first name of an option, whatever stands before .claude in it; and whatever the rule lets stand
// before .claude in a name (= : , { } @ or a variable), which the path rule does not take for the folder.
// It sees a literal spelling and nothing
// else: it asks the file system nothing, so it does not know an 8.3 name or a link that leads to a
// .claude folder, and a quote or an escape inside the name hides it. It refuses a URL and git show
// HEAD:.claude/settings.json on purpose. A change to this file or to a list is drafted outside every
// .claude folder and installed by the operator.
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
// token's own reading already is, so the piece's relative reading is not counted a second time. On
// Windows, one letter and a colon is the key of an object and not a drive only where all of this holds:
// it stands in a quoted string, as this pairs quote characters, that holds no $( and no backtick; it
// stands in a brace group of that string, the text between a { and the next } with no other brace between;
// and every member of that group, which is what its commas part, is keyed: after white space, a name, a
// colon, and then something that is not a separator and not the end of the member, as in '{t:.title}'
// and '{number:.number, t:.title}'. What follows a key's colon is read as a token on every platform. A
// comma list with no braces is never an object, and a bare drive is always a drive. The braces of a
// variable at the head of a token or of a piece, ${NAME} or ${env:NAME}, that is replaced by its value
// are no cut: the token or piece is read whole, and so is the variable alone, whose closing brace is no
// punctuation to trim. And in a Bash command only, ${NAME-word}, ${NAME=word}, ${NAME+word} and
// ${NAME?word}, each with or without a colon before the sign, is a variable with a word for when it has
// no value, wherever it stands in a token: the token is read with the word in its place and, when the
// name has a value, with the value in its place, what follows the closing brace joined to each and not
// read apart. The name itself is not read. The word runs to the brace that closes the variable, counting
// the braces between, and a word that holds a variable is read by the same rules. A value put in the
// place of a variable that is not at the head of a token or of a piece is joined to what stands before
// it, so it is read as a path only where that makes one, as after an =, and on a system that is not
// Windows a rooted one is not read.
//
// A link is followed before the check. A path that holds .. is read twice, and either reading refuses:
// as text, where .. folds against the name before it, and as the file system walks it, which starts from
// the real path of the folder the path is read against (for an absolute path, from its root) and at each
// .. takes the parent of the real path reached so far, with a name that is not there put back as text.
// The second reading is made only when the first was not refused, since the call is refused already
// then, and not when it is the first again, which it is wherever no link is on the way; it asks the file
// system once about each folder a .. is taken from, whatever the allow list says of that folder. It is
// judged against the allowed roots and against where they lead, and in a shell command each of the two
// readings that is a folder is a folder the command names, and a relative path read against a folder
// that was reached as walked is judged as walked too. A name that is there and cannot be followed to
// where it leads (a link to a name that is not there, a link in a loop, any answer of the file system
// but "not there") is refused, and so is everything beneath it. Only a name that is not there is
// answered from the folder it would be in: no such name, a name beneath something that is not a
// folder, a name too long and a name the platform cannot hold. The allow-list decision is
// made from a path's text before the file system is asked about it, so a refused path costs no lookup,
// and nothing beneath a refused folder is looked up: no relative path is read against the current
// directory, or a folder the command names, once that folder is itself refused. On Windows only it also
// reads Git Bash drive paths (written with forward slashes), /proc/cygdrive and /cygdrive paths, and
// UNC paths.
//
// On Windows each reading of a path, as text and as walked, is read once more as PowerShell opens it
// (ADR-217, docs/adr/0217): each of its names with a stream name after a colon taken off and then the
// dots and spaces that end it, so wd.\report.html is read as wd\report.html, which Node answers "not
// there" for and PowerShell opens. That reading is judged by every check the others are, the allow list,
// a read line, a closed .claude folder, links and a working directory above or below, and refuses as
// they do; in a shell command it is a folder the command names where it is one, and a plain relative
// name that Windows opens as another name is read for that name. A path with a name made only of dots,
// spaces or a stream name, and that is neither . nor .., is refused on Windows, since what Windows opens
// for it is not known. One such name is not refused: a relative token of a command that is only spaces,
// as the ' ' of tr '\n' ' ' is, which is answered from the folder it is read against, as a plain name
// that is not there is. Elsewhere a dot, a space and a colon are part of a name and nothing changes.
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
// is the list of what is known, and it is not complete; a form below that is not read as a path is
// still read for the text of a closed .claude folder, above): a path a command builds at run time (a
// command substitution, a variable set in the same command, a loop, a program's own computing, which
// reaches a closed .claude folder as it reaches any other path); a program that writes a file of a closed
// folder without the command's text naming it (git checkout, git merge, rm -rf of a folder above one, a
// build); a variable that is not at the head of a token, and %NAME%\x as cmd writes a variable; an
// option with its value attached and no =, as in -I../x; ~name/x; a brace list with text around it, as in
// ../{a,b}/x, whose pieces are read but which is not expanded as a shell expands it; a path from the
// root of the drive without its letter, such as \archive\x; a shell wildcard; a recursive shell command
// (grep -r, rg, find) that begins above a working directory; a rooted POSIX path such as /tmp/x, so
// that cd /tmp && cat run/x reads run/x against the current directory only; a bare cd or cd -; a
// quoted string inside a quoted string, as in bash -c "cat 'my runs/x'", which is read whole only as
// the outer one; a brace group between two quote characters, every member of which is a name, a colon
// and a value, one of them a path on a drive with no separator after the colon, as in bash -c "cat
// {a:1,Q:name}", whose part after the colon is read and whose drive is not; a link that only Git Bash's runtime follows, such as a symlink written as a file for Cygwin, which is a
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
// One Bash or PowerShell command is admitted without being read for paths (ADR-212, docs/adr/0212):
// node, working-directory-counts.mjs beside this file, and a folder that directly holds vespera.db, and
// nothing else. That script prints a working directory's aggregate counts by statements fixed in it, and
// they are all an agent may read of one. The command is three words parted by spaces or tabs, the two
// after node bare or single-quoted, of letters, digits and _ . / - (and \ and the space where the shell
// takes them as written), with a colon only as a drive's and no .. segment. The script word, read against
// the current directory, is this file's sibling character for character, folded to one case on Windows
// only, and the bytes there have the SHA-256 pinned in COUNTING_SCRIPT_SHA256, CR LF read as LF. The
// current directory passes the ordinary check, which includes the closed .claude folder rule, so the
// command started from a closed .claude folder is refused. Last of all the file system is asked whether
// the folder directly holds vespera.db: the one path this file touches that the allow list need not name.
// So that the script word is the path node opens, a word that begins with a separator is not admitted on
// Windows, where Git Bash rewrites one, nor a word with a backslash elsewhere, where it is part of a name.
// The admitted command is let through before its paths or the text of its tokens are read, so neither the
// working-directory rule nor the closed .claude folder rules (ADR-215 section 10) apply to its two words,
// the script word and the folder word; they do apply to its current directory, as said above. A call that
// fails any of this is read as every other command is, the text of its tokens included. The script lies
// in a closed .claude folder, so it is refused to Edit, Write and NotebookEdit and to every shell command
// that names it but the admitted one, and is let through to Read, Grep and Glob. The pin does not cover
// the moment between this check and node reading the script, or which node runs and how it is started
// (NODE_OPTIONS, PATH, an alias); for those the written rule is all there is.
//
// It fails closed. Input it cannot read, input that names no tool, and any exception end in exit
// code 2, which is the only code Claude Code treats as a refusal.
//
// Protocol: Claude Code passes the tool call as JSON on stdin. Exit code 2 refuses it, exit code 0
// lets it through, and stderr is what Claude reads.

import { createHash } from "node:crypto";
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

// The tools that read. Any other tool a call names is judged as one that writes.
const READING_TOOLS = new Set(["Read", "Grep", "Glob"]);
// The names that may follow a .claude folder in an open path (ADR-215 section 3(b)), written here and in
// no list.
const OPEN_BENEATH_CLAUDE = new Set(["projects", "plans", "worktrees"]);
// Why a path is refused to a tool that writes and to a shell command, worded to follow "(" or "to X,".
const READ_ONLY = "beneath a place the allow list names for reading only, where Edit, Write, NotebookEdit and a shell command are refused";
const CLOSED =
  "in a .claude folder that no agent writes: only what lies beneath projects, plans or worktrees is open to Edit, Write, NotebookEdit and a shell command";

const norm = (p) => resolve(p).replace(/\\/g, "/").replace(/\/+$/, "").toLowerCase();
const under = (path, root) => path === root || path.startsWith(root + "/");
const within = (path, roots) => roots.some((r) => under(path, r));
const drive = (letter, rest) => `${letter.toUpperCase()}:/${rest}`;

// A name as PowerShell opens it: on Windows the dots and spaces that end a name are not part of it, and
// . and .. are not names.
const withoutTrailing = (name) => (windows && name !== "." && name !== ".." ? name.replace(/[. ]+$/, "") : name);
// Whether a name in a path's text is a .claude folder, with its case folded. On Windows a stream name,
// after a colon, leads to the folder it is a stream of.
const isClaudeFolder = (name) => withoutTrailing((windows ? name.replace(/:.*$/, "") : name).toLowerCase()) === ".claude";
// A name as PowerShell opens it, on Windows: a stream name after a colon taken off, then the dots and
// spaces that end it. Elsewhere the name as written.
const asOpened = (name) => withoutTrailing(windows ? name.replace(/:.*$/, "") : name);

// An absolute path as PowerShell opens it (ADR-217): on Windows each name after its root is taken as
// asOpened takes it, so wd.\report.html is wd\report.html, the name Node answers "not there" for. The
// path itself where no name changes, and everywhere but Windows. null where a name is made only of dots,
// spaces and a stream name and is neither . nor .., since what Windows opens for one is not known.
function openedByWindows(absolute) {
  if (!windows) return absolute;
  const { root } = parse(absolute);
  const names = absolute.slice(root.length).split(/[\\/]/).filter((name) => name !== "");
  const opened = names.map(asOpened);
  if (opened.some((name) => name === "")) return null;
  return opened.every((name, i) => name === names[i]) ? absolute : join(root, ...opened);
}

// Whether a name that follows .claude is one of the open names, with its case folded. The one comparison
// of the path rule and the text rule. Where the name is the last of a token of a command's text,
// sentence punctuation, dots and spaces that end it are taken off: they end a sentence, and a token, and
// never stand inside a path. Anywhere else it is compared as a path is, with the dots and spaces that end
// it taken off on Windows and nowhere else.
const NAME_END = /[,.;:)\]}…\s]+$/;
function opensBeneathClaude(name, lastOfToken) {
  const folded = name.toLowerCase();
  return OPEN_BENEATH_CLAUDE.has(lastOfToken ? folded.replace(NAME_END, "") : withoutTrailing(folded));
}

// Whether a path, already normalised by norm, is closed by ADR-215 section 3(b): one of its folders is
// named .claude and that name is the last of the path or is followed by any name but projects, plans or
// worktrees. The path is read on from an open .claude, so a .claude further down closes what is beneath
// it. Asks the file system nothing.
function closedFolder(path) {
  const names = path.split("/");
  for (let i = 0; i < names.length; i++) {
    if (!isClaudeFolder(names[i])) continue;
    if (i === names.length - 1 || !opensBeneathClaude(names[i + 1], false)) return true;
  }
  return false;
}

// A name of a token that is .claude by ADR-215 section 3(c): .claude, then only a stream name and what
// NAME_END takes off, and before it nothing, or one of = : , { } @, or a variable ($NAME, ${NAME},
// ${env:NAME}, $env:NAME or %NAME%). The lazy prefix lets the first .claude that fits decide, so
// .claude.json and .claudeignore are not that name and neither are main..claude and notes.claude.
const CLAUDE_IN_TEXT = /^([^]*?)\.claude(?::[^]*|[,.;:)\]}…\s])*$/;
const BEFORE_CLAUDE = /(?:^|[=:,{}@]|\$\w+|\$env:\w+|\$\{[^{}]*\}|%\w+%)$/;

// Whether the text of one token of a shell command names a closed .claude folder (ADR-215 section 3(c)),
// whatever heads the token and whether or not anything else reads it as a path. The token is cut at / and
// \ with .. folded against the name before it. A name is .claude when CLAUDE_IN_TEXT fits it and either
// BEFORE_CLAUDE accepts what stands before .claude, or it is the first name of a token headed by -, which
// still has to end in .claude as CLAUDE_IN_TEXT reads it. The token is closed when that name is the last,
// or the next is not an open name by opensBeneathClaude, which takes NAME_END off only where that next
// name is the last of the token, and is read on from an open one, as closedFolder does. Asks the file
// system nothing.
function closedInText(token) {
  const names = [];
  for (const name of token.replace(/\\/g, "/").split("/")) {
    if (name === "..") names.pop();
    else if (name !== "" && name !== ".") names.push(name.toLowerCase());
  }
  const option = token.startsWith("-");
  for (let i = 0; i < names.length; i++) {
    const claude = CLAUDE_IN_TEXT.exec(names[i]);
    if (!claude || !(BEFORE_CLAUDE.test(claude[1]) || (option && i === 0))) continue;
    if (i === names.length - 1 || !opensBeneathClaude(names[i + 1], i + 1 === names.length - 1)) return true;
  }
  return false;
}

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
      // The word read alone, or with only white space after it (taken off by the trim above), names no
      // place. A line headed by the word read and one space names a place for reading only.
      if (line === "read") continue;
      const reading = /^read (.*)$/.exec(line);
      if (reading) line = reading[1].trim();
      for (const [k, v] of Object.entries(tokens)) line = line.split(k).join(v);
      roots.push({ path: resolve(line), read: Boolean(reading) });
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
// there (see NOT_THERE) is answered from its parent, so a name that does not exist costs one question
// for each level of it that is missing, and one more for the folder that is there.
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
// parent folder and not punctuation, and neither is a trailing single dot after a separator, nor the
// brace that closes a ${ : the variable alone, ${NAME}, is a token headed by the variable. On Windows a
// last name of three or more dots is not trimmed down to .., the parent folder: PowerShell was measured
// to open the folder that name is in, so wd/... is read as written and refused as a name made only of
// dots (ADR-217 section 2).
function trimSentence(p) {
  for (;;) {
    if (windows && /(^|[\\/])\.{3,}$/.test(p)) return p;
    if (/(^|[\\/])\.{1,2}$/.test(p)) return p;
    if (/\$\{[^{}]*\}$/.test(p)) return p;
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
// The start of a Bash parameter expansion with a word for when the variable has no value.
const PARAMETER_WITH_WORD = /\$\{(\w+):?[-=+?]/y;
// A brace group of a quoted string: the text between a { and the next }, with no other brace between.
const BRACE_GROUP = /\{([^{}]*)\}/g;
// A keyed member of a brace group: after white space, a name, a colon, and then something that is not a
// separator and not the end of the member.
const KEYED_MEMBER = /^(\s*)\w+:(?![\\/]|$)/;

// The pieces of a text cut at , { and }, each with the character it follows (null for the first) and
// where it starts. The braces of a variable at the head of a piece that is replaced by its value are no
// cut.
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
      parts.push({ piece: text.slice(start, i), before, start });
      before = text[i];
      start = i + 1;
    }
  }
  return parts;
}

// A Bash text read twice: with the word in the place of each ${NAME<sign>word} and, where the name has a
// value, with the value in its place. What follows a closing brace stays joined to what is put in its
// place. The word runs to the brace that closes the variable, counting the braces between, and a word
// that holds an expansion is read by these same rules. A text with no expansion in it is its own two
// readings.
function variantsOf(text) {
  let words = "";
  let values = "";
  let i = 0;
  while (i < text.length) {
    PARAMETER_WITH_WORD.lastIndex = i;
    const start = text[i] === "$" ? PARAMETER_WITH_WORD.exec(text) : null;
    if (start) {
      let depth = 1;
      let end = i + start[0].length;
      for (; end < text.length && depth > 0; end++) {
        if (text[end] === "{") depth++;
        else if (text[end] === "}") depth--;
      }
      if (depth === 0) {
        const [word, wordWithValues] = variantsOf(text.slice(i + start[0].length, end - 1));
        words += word;
        // Where the expansion heads a token or a piece the variable alone stands in its place, which
        // readToken replaces by its value; elsewhere the value itself does, so that the braces of a
        // variable that is not at a head are not cut and what follows is not read apart.
        const value = valueOf(start[1]);
        const heads = values === "" || ",{}".includes(values[values.length - 1]);
        values += value ? (heads ? `\${${start[1]}}` : value) : wordWithValues;
        i = end;
        continue;
      }
    }
    words += text[i];
    values += text[i];
    i++;
  }
  return [words, values];
}

// What a command's text names: absolute paths, each as { label, text }, where text is read against the
// current directory by the caller, and relative ones, each as rel -> { label, written }, where written
// is how many times the command writes that token. A relative one is read against a directory by the
// caller. bash says whether ${NAME-word} and its kin are parameter expansions.
function pathsInCommand(command, bash) {
  const absolute = [];
  const relative = new Map();
  // The relative paths a token's own reading made, while it is being recorded, and the ones a piece of
  // that token must not count again. And the relative paths the first of a Bash token's two readings
  // made, which the second does not count again.
  let recorded = null;
  let uncounted = null;
  let firstReading = null;
  let secondReading = false;
  const addAbsolute = (label, text) => absolute.push({ label, text });
  const addRelative = (label, rel) => {
    recorded?.add(rel);
    if (!secondReading) firstReading?.add(rel);
    if (uncounted?.has(rel)) return;
    if (secondReading && firstReading.has(rel)) return;
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

  // key says that the token stands where a key of an object is told (see the quoted strings below),
  // which is not a drive.
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
      if (key && !/^[\\/]/.test(rest)) {
        // The key of an object in a quoted string, as in '{t:.title}': what follows the colon is read as
        // a token on every platform. Only on Windows is the key then no drive; elsewhere the whole is
        // read as the name it is, as before.
        readToken(rest);
        if (windows) return;
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
  // A quoted string is a path with its spaces in it as often as it is a sentence, and may hold a brace
  // group that is the object of a second program, as in jq '{t:.title}'. Where the first letter of a
  // member of such a group, and a colon after it, is a key and not a drive is told by the shape of the
  // group and by nothing the quote characters say, since this pairs them as it finds them and text that
  // the shell does not quote can lie between two: the string holds no $( and no backtick, and every
  // member of the group is keyed. keys holds the offsets in the command at which a keyed member's name
  // starts.
  const quotes = [];
  const keys = new Set();
  for (const m of command.matchAll(QUOTED_STRINGS)) {
    const whole = m[1] ?? m[2];
    const from = m.index + 1;
    quotes.push({ from, whole });
    if (/\$\(|`/.test(whole)) continue;
    for (const group of whole.matchAll(BRACE_GROUP)) {
      const starts = [];
      let at = group.index + 1;
      for (const member of group[1].split(",")) {
        const keyed = KEYED_MEMBER.exec(member);
        if (!keyed) break;
        starts.push(from + at + keyed[1].length);
        at += member.length + 1;
      }
      if (starts.length === group[1].split(",").length) for (const start of starts) keys.add(start);
    }
  }

  // A token, and each piece of it cut at , { and }: a brace list or a comma list carries paths that
  // are not at the head of the token. The braces of a variable that is replaced by its value, at the
  // head of the token or of a piece, are no cut. A token that is one path followed only by , or } is
  // read once and not again as its own piece, so that a .. written once climbs once. base is where the
  // text starts in the command, or -1 when it does not start there: a piece that starts at an offset
  // in keys is the head of a keyed member.
  const readOnce = (text, base) => {
    const keyAt = (offset) => base >= 0 && keys.has(base + offset);
    const parts = cut(text);
    const single = parts.length > 1 && parts[0].piece !== "" && parts.slice(1).every((p) => p.piece === "" && p.before !== "{");
    if (single) recorded = new Set();
    readOne(text, keyAt(0));
    if (single) {
      uncounted = recorded;
      recorded = null;
    }
    if (parts.length > 1) {
      for (const { piece, start } of parts) if (piece) readOne(piece, keyAt(start));
    }
    uncounted = null;
  };
  // In a Bash command a text with a ${NAME-word} in it is read twice, with the word and with the value in
  // the place of the expansion (see variantsOf), and not as it is written, so that the name is not read.
  // A relative path the first reading made is not counted again by the second.
  const read = (text, base = -1) => {
    if (bash) {
      const [words, values] = variantsOf(text);
      if (words !== text) {
        firstReading = new Set();
        readOnce(words, -1);
        if (values !== words) {
          secondReading = true;
          readOnce(values, -1);
        }
        firstReading = null;
        secondReading = false;
        return;
      }
    }
    readOnce(text, base);
  };

  for (const m of command.matchAll(COMMAND_TOKENS)) read(m[0], m.index);
  // A backslash before a space joins what is on either side of it into one path.
  for (const m of command.matchAll(/(?:\\ |[^\s"'`;|&<>()])+/g)) {
    if (m[0].includes("\\ ")) read(m[0].replace(/\\ /g, " "));
  }
  for (const { whole, from } of quotes) if (whole) read(whole, from);
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

// The counting script of ADR-212, and the SHA-256 of its bytes with every CR LF read as LF. A change to
// that script is a change to this constant, and is reviewed as one.
const COUNTING_SCRIPT = join(here, "working-directory-counts.mjs");
const COUNTING_SCRIPT_SHA256 = "66c4b8e4c71c18ed4111417be6f8410f266563b87846d0ef73e62727f289efd8";
// node and two words, each single-quoted or with no quote and no white space in it, and nothing else.
const COUNTING_COMMAND = /^[ \t]*node[ \t]+('[^']*'|[^\s']+)[ \t]+('[^']*'|[^\s']+)[ \t]*$/;

// A word of the counting command as node receives it, or null when ADR-212 section 4.1 does not admit
// it. A backslash outside single quotes is an escape to Bash, which would hand node another path.
function countingWord(written, bash) {
  const quoted = /^'([A-Za-z0-9_.\/\\: -]+)'$/.exec(written);
  if (!quoted && !(bash ? /^[A-Za-z0-9_.\/:-]+$/ : /^[A-Za-z0-9_.\/\\:-]+$/).test(written)) return null;
  const word = quoted ? quoted[1] : written;
  // A colon only as a drive's: one, second in the word, after a letter and before a separator.
  if (word.includes(":") && (word.indexOf(":") !== word.lastIndexOf(":") || !/^[A-Za-z]:[\\/]/.test(word))) return null;
  if (word.split(/[\\/]/).includes("..")) return null;
  // So that the word read here is the path node opens: Git Bash rewrites a word that begins with a
  // separator, and a backslash is part of a name where it parts no folders.
  if (windows ? /^[\\/]/.test(word) : word.includes("\\")) return null;
  return word;
}

// The folder a command asks the counting script about, when the command is that script's one admitted
// form (ADR-212 section 4.1), names this file's sibling (4.2) and the sibling's bytes are the pinned ones
// (4.3); null otherwise. It asks the file system about nothing but the script.
function countedFolder(command, bash, cwd) {
  const form = COUNTING_COMMAND.exec(command);
  if (!form) return null;
  const script = countingWord(form[1], bash);
  const folder = countingWord(form[2], bash);
  if (script === null || folder === null) return null;
  const fold = (p) => (windows ? p.toLowerCase() : p);
  const opened = resolve(cwd, script);
  if (fold(opened) !== fold(COUNTING_SCRIPT)) return null;
  let bytes;
  try {
    bytes = readFileSync(opened, "latin1");
  } catch {
    return null;
  }
  if (createHash("sha256").update(bytes.replace(/\r\n/g, "\n"), "latin1").digest("hex") !== COUNTING_SCRIPT_SHA256) return null;
  return resolve(cwd, folder);
}

// Whether it is a folder, after links are followed, that directly holds a file named vespera.db.
function directlyHoldsDatabase(folder) {
  try {
    return statSync(folder).isDirectory() && lstatSync(join(folder, "vespera.db")).isFile();
  } catch {
    return false;
  }
}

function refusalsOf(call) {
  const tool = call.tool_name;
  const input = call.tool_input && typeof call.tool_input === "object" ? call.tool_input : {};
  const currentDirectory = typeof call.cwd === "string" && call.cwd ? call.cwd : checkout;
  const cwd = absoluteOf(currentDirectory, checkout);

  // Each allowed place as its text and as where its links lead, and whether the list names it for
  // reading only.
  const places = allowedRoots().map(({ path, read }) => ({ text: norm(path), real: norm(realPath(path)), read }));
  // Whether a path stands under an allowed place, by its text and, when real says it is a real path, by
  // where the places lead, and whether any place it stands under is for reading only: that one decides.
  const standing = (path, real) => {
    const matched = places.filter((p) => under(path, p.text) || (real && under(path, p.real)));
    return matched.length ? { read: matched.some((p) => p.read) } : null;
  };
  // Read, Grep and Glob read. Any other tool, and a shell command, may write, and is refused a place that
  // is for reading only and a closed .claude folder.
  const writes = !READING_TOOLS.has(tool);

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
    const standsAt = standing(text, walked);
    if (!standsAt) return turnedDown("outside the allow list");
    // The text of a path decides these two before the file system is asked about it.
    if (writes && standsAt.read) return turnedDown(READ_ONLY);
    if (writes && closedFolder(text)) return turnedDown(CLOSED);
    const found = describe(absolute);
    if (found.blocked) return turnedDown(`${found.blocked}, so it is refused, and so is everything beneath it`);
    const realText = norm(found.real);
    const leadsTo = standing(realText, true);
    if (!leadsTo) return turnedDown(`a link leads from it to ${found.real}, outside the allow list`);
    if (writes && leadsTo.read) return turnedDown(`a link leads from it to ${found.real}, ${READ_ONLY}`);
    if (writes && closedFolder(realText)) return turnedDown(`a link leads from it to ${found.real}, ${CLOSED}`);
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
  // the way. A base that was itself read as walked is a real path, so every reading made against it is
  // judged as walked, and each reading says whether it was, so that a folder reached by it carries that on.
  // On Windows each reading is also judged as PowerShell opens its names (ADR-217), and a reading with a
  // name whose opening is not known is refused.
  const checkReadings = (label, text, base, searchRoot = false, baseWalked = false) => {
    const asText = absoluteOf(text, base);
    const readings = [{ absolute: asText, walked: baseWalked, turnedDown: check(label, asText, searchRoot, baseWalked) }];
    if (readings[0].turnedDown) return readings;
    const walked = walkedOf(text, base);
    if (walked && norm(walked) !== norm(asText)) {
      readings.push({ absolute: walked, walked: true, turnedDown: check(`${label}, read as the file system walks its ..`, walked, searchRoot, true) });
    }
    for (const { absolute, walked: isWalked, turnedDown } of [...readings]) {
      if (turnedDown) continue;
      const opened = openedByWindows(absolute);
      if (opened === null) {
        refused.push(`${label} (a name of it is made only of dots, spaces or a stream name, and what Windows opens for it cannot be ruled out)`);
        readings.push({ absolute, walked: isWalked, turnedDown: true });
        break;
      }
      if (norm(opened) === norm(absolute) || readings.some((r) => norm(r.absolute) === norm(opened))) continue;
      readings.push({ absolute: opened, walked: isWalked, turnedDown: check(`${label}, read as Windows opens its names`, opened, searchRoot, isWalked) });
    }
    return readings;
  };
  const currentLabel = `the current directory, ${currentDirectory}`;

  if (tool === "Bash" || tool === "PowerShell") {
    const command = String(input.command ?? "");
    // ADR-212 section 4, in its order: the form, the script and its hash, then the current directory, and
    // the folder last, so that a lookup that hangs can let through only the pinned script. The admitted
    // command is let through before any path or token of it is read (ADR-215 section 10).
    const counted = countedFolder(command, tool === "Bash", cwd);
    const currentChecked = checkReadings(currentLabel, currentDirectory, checkout);
    if (counted !== null && currentChecked.every(({ turnedDown }) => !turnedDown) && directlyHoldsDatabase(counted)) return refused;
    // Every token's text, whether or not it is read as a path (ADR-215 section 3(c)).
    for (const [token] of command.matchAll(COMMAND_TOKENS)) {
      if (closedInText(token)) refused.push(`${token} (its text names a path ${CLOSED})`);
    }
    const { absolute, relative } = pathsInCommand(command, tool === "Bash");

    // The folders the command names, which a relative path may be read against, each with the least use
    // of every relative token over the chains of tokens that reach it. A folder that is refused is not
    // one: nothing is looked up beneath it, and it is refused already.
    const folders = new Map();
    const queue = [];
    let tooMany = false;
    const reach = (path, use, walked = false) => {
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
        const entry = { path, key, walked, use: new Map(use), queued: true };
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

    for (const { absolute: path, walked, turnedDown } of currentChecked) if (!turnedDown) reach(path, new Map(), walked);
    for (const { label, text } of absolute) {
      for (const { absolute: path, walked, turnedDown } of checkReadings(label, text, cwd)) {
        if (!turnedDown && describe(path).folder) reach(path, new Map(), walked);
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
          // The name of a .claude folder is not plain: the folder is closed whether or not it is there,
          // and PowerShell opens .claude. as .claude where Node says it is not there.
          // Nor is a name Windows opens as another name, wd. as wd (ADR-217): it is read for that name.
          // A token that is only spaces, as the ' ' of tr '\n' ' ', is plain: PowerShell was measured to
          // open the folder it is read against for one, and nothing else (ADR-217 section 2). It is the
          // token with the punctuation that ends a sentence taken off, as every token is read, so ' .' too.
          const plain = !/[\\/]/.test(rel) && rel !== "." && rel !== ".." && !isClaudeFolder(rel) && (asOpened(rel) === rel || /^ +$/.test(rel));
          const named = entry.key === norm(cwd) ? label : `${label}, read against ${entry.path}`;
          const asText = absoluteOf("./" + rel, entry.path);
          const found = plain ? describe(asText) : null;
          if (found && !found.exists && !found.blocked) {
            answer = [{ abs: asText, walked: entry.walked, folder: false }];
          } else {
            answer = checkReadings(named, "./" + rel, entry.path, false, entry.walked).map(({ absolute: abs, walked, turnedDown }) => ({
              abs,
              walked,
              folder: !turnedDown && describe(abs).folder,
            }));
          }
          answered.set(key, answer);
        }
        for (const { abs, walked, folder } of answer) {
          if (!folder) continue;
          const use = new Map(entry.use);
          use.set(rel, used + 1);
          reach(abs, use, walked);
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
      starts.forEach(({ absolute, walked, turnedDown }) => {
        if (turnedDown) return;
        if (prefix === "") check(String(pattern), absolute, true, walked);
        else checkReadings(String(pattern), prefix, absolute, true, walked);
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
      "documents may be sensitive and are read only by local models, and no agent writes into a .claude " +
      "folder, because what is there decides how a later session runs (a closed one may still be read with " +
      "Read, Grep and Glob). Paths: " + refused.join("; ") +
      ". If a path outside a .claude folder is legitimate and holds no document, the operator adds it to " +
      ".claude/allowed-paths.local.txt; no line of an allow list opens a .claude folder, and a change to " +
      "the guard, a list or the settings is written as a draft outside every .claude folder, which the " +
      "operator installs (docs/adr/0215 section 7).",
  );
}

process.exitCode = main();
