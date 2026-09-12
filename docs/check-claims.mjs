#!/usr/bin/env node
// Checks the claims this repository's two prose documents make about it, against it.
//
//   AGENTS.md  what state the project is in, read by an agent
//   README.md  how the tool is operated, read by a person
//
// ADR-098 keeps that division strict: two files describing the same thing is the
// drift this file exists to catch, and it has already cost two pull requests.
//
//   node docs/check-claims.mjs                 everything that needs no network
//   node docs/check-claims.mjs --with-network  also: is the named wayfinder map still open
//
// Why this exists: AGENTS.md is what an agent reads before doing anything, and on
// 2026-09-08 six of its claims were false — the ADR count, the boundary between the
// reconstituted records and the full-text ones, which stages had code, which modules
// existed as packages, which wayfinder map was current, and which classes were
// integration tests. Nothing went red. docs.yml filters on docs/**, and this file is
// at the root, so no job had ever opened it.
//
// What this is NOT: evidence that AGENTS.md is true. It checks the claims that are
// countable — names, numbers, whether a file exists — and it prints the ones it
// cannot check every time it runs, passing or failing. A green check misread as
// coverage is a failure this repository has already had once: build.yml carries a
// comment about a job renamed from "verify" because a reviewer read its green as the
// Java build having run, which it never had.
//
// Claims are read OUT of the document rather than restated here. The document stays
// the source; this only asks whether the tree agrees with it. A claim whose sentence
// can no longer be found is therefore a FAILURE and not a skip — a rewording that
// quietly turns a check off is the same defect as a renderer dropping a section.

import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const AGENTS = "AGENTS.md";
const README = "README.md";
const ADR_DIR = "docs/adr";
const MAIN = "src/main/java/io/algernon/vespera";
const TEST = "src/test/java";
const POM = "pom.xml";
const APPLICATION_YAML = "src/main/resources/application.yaml";
const COMMANDS = MAIN + "/pipeline/VesperaCommand.java";
const PROFILE = MAIN + "/profile/Profile.java";
// No trailing slash: the list form is /issues?labels=..., and /issues/?labels=... is a 404.
const ISSUE_API = "https://api.github.com/repos/algernon28/vespera/issues";

/** The label a wayfinder map carries, which is how "none is open" is checked. */
const MAP_LABEL = "wayfinder:map";

const text = readFileSync(AGENTS, "utf8");
const readme = readFileSync(README, "utf8");
const withNetwork = process.argv.includes("--with-network");

/* ---------- recording ---------- */

const results = [];
const pass = (name, detail) => results.push({ state: "PASS", name, detail });
const fail = (name, detail) => results.push({ state: "FAIL", name, detail });
const skip = (name, detail) => results.push({ state: "NOT CHECKED", name, detail });

// A sentence this cannot find is a failure, not a skip. See the header.
function claim(re, name, doc = text) {
  const m = re.exec(doc);
  if (m) return m;
  const where = doc === readme ? README : AGENTS;
  fail(name, `no sentence in ${where} matches ${re}: restore the claim, or update this check`);
  return null;
}

const WORDS = { one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10 };
const wordNumber = (w) => WORDS[w.toLowerCase()];
const backticked = (s) => [...s.matchAll(/`([A-Za-z.]+)`/g)].map((m) => m[1]);
const subdirs = (p) => readdirSync(p, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name);

const adrFiles = readdirSync(ADR_DIR).filter((f) => /^\d{4}-.+\.md$/.test(f));
const adrById = new Map(adrFiles.map((f) => [Number(f.slice(0, 4)), f]));

/* ---------- how many decisions ---------- */

{
  const NAME = "the ADR count";
  const m = claim(/\bholds (\d+) decisions\b/, NAME);
  if (m) {
    const claimed = Number(m[1]);
    if (claimed === adrFiles.length) pass(NAME, `${claimed} decisions`);
    else fail(NAME, `${AGENTS} says ${claimed}; ${ADR_DIR} holds ${adrFiles.length}`);
  }
}

/* ---------- where the reconstituted records stop ---------- */

// The two sentences have to agree with each other and with the files: consecutive
// ids, the last one carrying the reconstitution marker, the first one not. This is
// the claim that was wrong by three when the guard was written.
{
  const NAME = "the full-text boundary";
  const a = claim(/ADR-001 to ADR-(\d{3}) are reconstituted records/, NAME);
  const b = claim(/ADR-(\d{3}) onward carry their own full text/, NAME);
  if (a && b) {
    const MARK = "Reconstituted record";
    const last = Number(a[1]);
    const first = Number(b[1]);
    const marked = (n) => adrById.has(n) && readFileSync(join(ADR_DIR, adrById.get(n)), "utf8").includes(MARK);
    if (first !== last + 1) fail(NAME, `ADR-${a[1]} then ADR-${b[1]} leaves a gap between the two halves`);
    else if (!marked(last)) fail(NAME, `ADR-${a[1]} is named as reconstituted and carries no "${MARK}" marker`);
    else if (marked(first)) fail(NAME, `ADR-${b[1]} is named as full text and carries the "${MARK}" marker`);
    else pass(NAME, `reconstituted through ADR-${a[1]}, full text from ADR-${b[1]}`);
  }
}

/* ---------- which of the nine modules have a package ---------- */

// Three ways this goes wrong and all three are caught: a module claimed to exist
// with no package, a module claimed to be design-only that someone has since built,
// and a package on disk the file names nowhere — the tenth module nobody documented.
{
  const NAME = "the module list";
  const listed = claim(/as packages under `io\.algernon\.vespera`: ([^.]+)\./, NAME);
  const total = claim(/\*\*(\w+) capability-shaped modules\*\*/, NAME);
  const gap = claim(/(\w+) of the \w+ exist as packages today — ([^—]+?) (?:is|are) recorded design and no code/, NAME);
  if (listed && total && gap) {
    const named = backticked(listed[1]);
    const absent = backticked(gap[2]);
    const present = named.filter((n) => !absent.includes(n));
    const onDisk = subdirs(MAIN);
    const wrong = [];
    if (named.length !== wordNumber(total[1])) wrong.push(`the file says ${total[1]} modules and names ${named.length}`);
    if (present.length !== wordNumber(gap[1])) wrong.push(`the file says ${gap[1]} exist and implies ${present.length}`);
    for (const n of present) if (!onDisk.includes(n)) wrong.push(`${n} is claimed to exist and has no package`);
    for (const n of absent) if (onDisk.includes(n)) wrong.push(`${n} is claimed to be design only and has a package`);
    for (const d of onDisk) if (!named.includes(d)) wrong.push(`${d} is a package and is named nowhere`);
    if (wrong.length) fail(NAME, wrong.join("; "));
    else pass(NAME, `${present.length} of ${named.length} exist: ${present.join(", ")}`);
  }
}

/* ---------- which classes are integration tests ---------- */

// Naming one that has since been added matters more than the count: an agent reading
// "these three need Docker" concludes the rest do not, and runs ./mvnw test believing
// it covered a class that failsafe alone reaches.
{
  const NAME = "the integration tests";
  const m = claim(/There are (\w+) — (.+?) — and each needs a Docker daemon/, NAME);
  if (m) {
    const named = backticked(m[2]).sort();
    const found = [];
    (function walk(dir) {
      for (const e of readdirSync(dir, { withFileTypes: true })) {
        if (e.isDirectory()) walk(join(dir, e.name));
        else if (/IT\.java$/.test(e.name)) found.push(e.name.replace(/\.java$/, ""));
      }
    })(TEST);
    found.sort();
    const wrong = [];
    if (named.length !== wordNumber(m[1])) wrong.push(`the file says ${m[1]} and names ${named.length}`);
    if (named.join() !== found.join()) wrong.push(`named ${named.join(", ") || "none"}; on disk ${found.join(", ") || "none"}`);
    if (wrong.length) fail(NAME, wrong.join("; "));
    else pass(NAME, found.join(", "));
  }
}

/* ---------- the two versions the file states outright ---------- */

{
  const NAME = "the Java and Spring Boot versions";
  const m = claim(/\bJava (\d+), Spring Boot ([\d.]+)/, NAME);
  if (m) {
    const pom = readFileSync(POM, "utf8");
    const java = /<java\.version>([^<]+)<\/java\.version>/.exec(pom);
    const boot = /<artifactId>spring-boot-starter-parent<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(pom);
    const wrong = [];
    if (!java || java[1] !== m[1]) wrong.push(`the file says Java ${m[1]}; the pom says ${java ? java[1] : "nothing"}`);
    if (!boot || boot[1] !== m[2]) wrong.push(`the file says Spring Boot ${m[2]}; the pom says ${boot ? boot[1] : "nothing"}`);
    if (wrong.length) fail(NAME, wrong.join("; "));
    else pass(NAME, `Java ${m[1]}, Spring Boot ${m[2]}`);
  }
}

/* ---------- is the named map still open, or is none ---------- */

// The only claim here that can go stale with no commit at all: a map closes on the
// tracker and the file keeps pointing at it. That is exactly what happened to
// issue #1, which closed on 2026-08-29 and was still named as current ten days later.
//
// Two forms are accepted because both are real states, and each is checked against the
// opposite mistake. Naming a map is checked for that map having closed. Saying none is
// open is checked for one having since been opened -- otherwise "no map is open" would
// be a sentence that turns the check off, which is the defect this file exists to catch.
// Neither sentence present is still a failure.
{
  const NAME = "the current wayfinder map";
  const named = /The current map is \[issue #(\d+)/.exec(text);
  const none = /\*\*No wayfinder map is open\.\*\*/.exec(text);

  if (!named && !none) {
    fail(
      NAME,
      `no sentence in ${AGENTS} names a current map or says none is open:`
        + ` restore the claim, or update this check`,
    );
  } else if (!withNetwork) {
    skip(NAME, named ? `#${named[1]} — run with --with-network to check it`
      : "none claimed open — run with --with-network to check it");
  } else {
    const headers = { accept: "application/vnd.github+json" };
    if (process.env.GITHUB_TOKEN) headers.authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
    try {
      if (named) {
        const response = await fetch(`${ISSUE_API}/${named[1]}`, { headers });
        if (!response.ok) fail(NAME, `#${named[1]}: the tracker answered ${response.status}`);
        else {
          const issue = await response.json();
          if (issue.state === "open") pass(NAME, `#${named[1]} is open — ${issue.title}`);
          else fail(NAME, `#${named[1]} closed on ${issue.closed_at}: name the map that replaced it,`
            + ` or say that none is open`);
        }
      } else {
        const response = await fetch(`${ISSUE_API}?labels=${encodeURIComponent(MAP_LABEL)}&state=open&per_page=100`, { headers });
        if (!response.ok) fail(NAME, `the tracker answered ${response.status}`);
        else {
          const open = (await response.json()).filter((i) => !i.pull_request);
          if (open.length === 0) pass(NAME, "none is open, and the tracker holds none");
          else fail(NAME, `the file says none is open, but ${open.map((i) => `#${i.number}`).join(", ")}`
            + ` carries ${MAP_LABEL}: name the current map`);
        }
      }
    } catch (e) {
      fail(NAME, `could not reach the tracker — ${e.message}`);
    }
  }
}

/* ---------- what README.md claims about operating the tool ---------- */

// AGENTS.md is read by an agent and README.md by an operator, and they claim different
// kinds of thing: one describes the state of the project, the other how to drive it.
// ADR-098 keeps that division strict, so these are checked separately -- and they are
// checked at all because ADR-098 named an unverified README as the cost of putting the
// sequence where an operator would actually look.
//
// Every check below is derived from code, never restated here. The one exception is the
// invocation count, which nothing in the tree can produce: three gates imply four
// invocations only if you already know a gate implies a stop. So that one is checked for
// internal consistency instead -- the headline against the table underneath it -- which
// is exactly the drift #98 shipped, where its prose said five and its own table showed
// four.

/** The section of README.md under `heading`, up to the next heading of the same level. */
function readmeSection(heading, name) {
  const start = readme.indexOf(heading);
  if (start < 0) {
    fail(name, `no "${heading}" section in ${README}: restore it, or update this check`);
    return null;
  }
  const rest = readme.slice(start + heading.length);
  const end = rest.search(/\n## /);
  return end < 0 ? rest : rest.slice(0, end);
}

{
  const NAME = "the invocation count";
  const m = claim(/takes \*\*(\w+) invocations\*\*/, NAME, readme);
  const section = readmeSection("## The path is four invocations", NAME);
  if (m && section) {
    const said = wordNumber(m[1]);
    const rows = [...section.matchAll(/^\| \*\*(\d+)\*\* \|/gm)].map((r) => Number(r[1]));
    const commands = [...section.matchAll(/`vespera (?:run|label)`?/g)].length;
    const wrong = [];
    if (said === undefined) wrong.push(`"${m[1]}" is not a number word this check knows`);
    else if (rows.length !== said) wrong.push(`the prose says ${m[1]} and the table has ${rows.length} rows`);
    if (rows.some((r, i) => r !== i + 1)) wrong.push(`the table is numbered ${rows.join(", ")}`);
    if (commands < rows.length) wrong.push(`${rows.length} rows but only ${commands} name a command`);
    if (wrong.length) fail(NAME, wrong.join("; "));
    else pass(NAME, `${m[1]}, and the table has ${rows.length} rows to match`);
  }
}

{
  const NAME = "the commands README names";
  const section = readmeSection("## Commands", NAME);
  if (section) {
    const named = [...section.matchAll(/^vespera (\w+)/gm)].map((c) => c[1]).sort();
    const real = [...readFileSync(COMMANDS, "utf8").matchAll(/@Command\(name = "(\w+)"/g)]
      .map((c) => c[1])
      .sort();
    const wrong = [];
    for (const c of named) if (!real.includes(c)) wrong.push(`${README} documents vespera ${c} and no such subcommand exists`);
    for (const c of real) if (!named.includes(c)) wrong.push(`vespera ${c} exists and ${README} documents it nowhere`);
    if (wrong.length) fail(NAME, wrong.join("; "));
    else pass(NAME, named.map((c) => `vespera ${c}`).join(", "));
  }
}

{
  const NAME = "the profile keys README names";
  const section = readmeSection("## What you have to decide, and where to read it", NAME);
  if (section) {
    // First column of that table only: the other backticked tokens on the page are
    // filenames, commands and configuration properties, and none of them is a key.
    const named = [...section.matchAll(/^\| `(\w+)` \|/gm)].map((k) => k[1]).sort();
    const record = /public record Profile\(([^)]*)\)/.exec(readFileSync(PROFILE, "utf8"));
    if (!record) {
      fail(NAME, `could not read the Profile record's components from ${PROFILE}`);
    } else {
      const real = [...record[1].matchAll(/ProfileValue (\w+)/g)].map((k) => k[1]).sort();
      const wrong = [];
      for (const k of named) if (!real.includes(k)) wrong.push(`${k} is documented and is not a profile key`);
      for (const k of real) if (!named.includes(k)) wrong.push(`${k} is a profile key and is documented nowhere`);
      if (wrong.length) fail(NAME, wrong.join("; "));
      else pass(NAME, `all ${real.length}, including the one that is not a stop`);
    }
  }
}

{
  const NAME = "the files README says it writes";
  const section = readmeSection("## Where things live", NAME);
  if (section) {
    const named = [...section.matchAll(/^([a-z][\w.-]*\.(?:yaml|html|db))\s/gm)].map((f) => f[1]).sort();
    const real = [];
    (function walk(dir) {
      for (const e of readdirSync(dir, { withFileTypes: true })) {
        if (e.isDirectory()) walk(join(dir, e.name));
        else if (e.name.endsWith(".java")) {
          for (const f of readFileSync(join(dir, e.name), "utf8").matchAll(/FILE_NAME = "([^"]+)"/g)) real.push(f[1]);
        }
      }
    })(MAIN);
    // The database is named by the datasource URL rather than by a constant.
    for (const d of readFileSync(APPLICATION_YAML, "utf8").matchAll(/jdbc:sqlite:[^"]*?\/([\w.-]+\.db)/g)) real.push(d[1]);
    const wrong = [];
    for (const f of named) if (!real.includes(f)) wrong.push(`${f} is documented and nothing writes it`);
    for (const f of real) if (!named.includes(f)) wrong.push(`${f} is written and documented nowhere`);
    if (wrong.length) fail(NAME, wrong.join("; "));
    else pass(NAME, `all ${real.length.toString()} of them`);
  }
}

/* ---------- what this does not check ---------- */

// Printed on every run, including a clean one. The point of the list is that the
// green above covers less than a reader would assume, and the only defence against
// that misreading is saying so in the same breath.
const UNCHECKED = [
  'the whole of "The shape of the system" — the ledger model, the two identities, the module rule',
  '"Stages 0 to 4 are built, and stage 5 is part-built", and what stage 5 still owes',
  "the thirteen job steps and their order, and that a later stage is a step on that same job",
  "the ADR-052 test conventions, and whether the report a run produces actually reads that way",
  "docs/architecture.md, which this never opens — its own status line has rotted the same way",
  "whether four invocations is still the right number — three gates imply it, and nothing counts gates",
  "whether the reports README points at actually inform the value it points them at for",
];

/* ---------- report ---------- */

const width = Math.max(...results.map((r) => r.state.length));
for (const r of results) {
  console.log(`${r.state.padEnd(width)}  ${r.name}: ${r.detail}`);
}

console.log(`\nNot checked, and not covered by the result above:`);
for (const u of UNCHECKED) console.log(`  · ${u}`);

const failed = results.filter((r) => r.state === "FAIL");
if (failed.length) {
  console.log(`\n${failed.length} claim(s) no longer describe this tree.`);
  process.exit(1);
}
console.log(`\n${results.length} claim(s) examined; none contradict the tree.`);
