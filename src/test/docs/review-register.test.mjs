// docs/review-register.mjs edits the review register's data file in place, so that a pass over the register reads
// and writes a file and never the whole published page (docs/review-register.md).
//
//   node --test src/test/docs/review-register.test.mjs

import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT = fileURLToPath(new URL("../../../docs/review-register.mjs", import.meta.url));

const ITEM = (id, status = "open") => ({
  id, area: "Tests", file: "src/test/x.java", line: 1, verdict: "confirmed", status, commit: "", title: `t ${id}`,
  summary: "s", scenario: "sc", source: "Review", why: "",
});

function fresh() {
  const dir = mkdtempSync(join(tmpdir(), "register-"));
  const data = join(dir, "register-data.js");
  writeFileSync(data, `const META = [\n"<code>a</code> first pass"\n];\nconst ITEMS = ${JSON.stringify([ITEM("A01"), ITEM("A02", "fixed")])};\n`);
  return { dir, data };
}

const run = (...args) => {
  try {
    return { code: 0, out: execFileSync(process.execPath, [SCRIPT, ...args], { encoding: "utf8", stdio: "pipe" }) };
  } catch (e) {
    return { code: e.status, out: String(e.stdout) + String(e.stderr) };
  }
};

const read = (data) => new Function(`${readFileSync(data, "utf8")}; return { META, ITEMS };`)();

test("a note is appended to the meta lines and the findings stay as they were", () => {
  const { dir, data } = fresh();
  const note = join(dir, "note.html");
  writeFileSync(note, "<code>b</code> second pass\n");
  assert.equal(run("meta", data, note).code, 0);
  const { META, ITEMS } = read(data);
  assert.deepEqual(META, ["<code>a</code> first pass", "<code>b</code> second pass"]);
  assert.equal(ITEMS.length, 2);
});

test("a finding is appended, and an id already in the register is refused", () => {
  const { dir, data } = fresh();
  const file = join(dir, "item.json");
  writeFileSync(file, JSON.stringify(ITEM("A03")));
  assert.equal(run("item", data, file).code, 0);
  assert.deepEqual(read(data).ITEMS.map((i) => i.id), ["A01", "A02", "A03"]);
  const again = run("item", data, file);
  assert.notEqual(again.code, 0);
  assert.match(again.out, /A03/);
  assert.equal(read(data).ITEMS.length, 3);
});

test("a status is set with its commit, and a status the page has no tile for is refused", () => {
  const { data } = fresh();
  assert.equal(run("status", data, "A01", "fixed", "abc1234").code, 0);
  const a = read(data).ITEMS.find((i) => i.id === "A01");
  assert.equal(a.status, "fixed");
  assert.equal(a.commit, "abc1234");
  const bad = run("status", data, "A01", "done");
  assert.notEqual(bad.code, 0);
  assert.match(bad.out, /fixed.*refuted|statuses/);
  assert.notEqual(run("status", data, "Z99", "fixed").code, 0);
});

test("check passes a sound file and names what is wrong with an unsound one", () => {
  const { data } = fresh();
  assert.equal(run("check", data).code, 0);
  writeFileSync(data, readFileSync(data, "utf8").replace('"id":"A02"', '"id":"A01"'));
  const dup = run("check", data);
  assert.notEqual(dup.code, 0);
  assert.match(dup.out, /A01/);
  writeFileSync(data, "const META = [];\nconst ITEMS = [{\"id\":\"A09\"}];\n");
  const lacking = run("check", data);
  assert.notEqual(lacking.code, 0);
  assert.match(lacking.out, /area/);
});

test("the file written is the one the page reads: a parse of it gives back what was set", () => {
  const { dir, data } = fresh();
  const file = join(dir, "items.json");
  writeFileSync(file, JSON.stringify([ITEM("B01"), ITEM("B02", "intended")]));
  assert.equal(run("item", data, file).code, 0);
  assert.equal(run("check", data).code, 0);
  assert.deepEqual(read(data).ITEMS.map((i) => i.id), ["A01", "A02", "B01", "B02"]);
});
