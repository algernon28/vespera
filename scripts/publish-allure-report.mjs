#!/usr/bin/env node
// Copies the single-file Allure report out of target/ and into the project root, stamped
// with the time it was produced: report_YYYYMMDD-HHMMSS.html.
//
// allure-maven already emits one self-contained index.html (singleFile), but it lands in
// target/allure-report beside a Maven site wrapper, and target/ is wiped. A report worth
// opening or sending to someone should not live somewhere a clean removes, and its name
// should say which run it came from -- hence the stamp rather than a fixed name that each
// run would overwrite.
//
// Colons are illegal in Windows filenames, so the stamp is compact rather than ISO.

import { copyFileSync, existsSync, statSync } from "node:fs";

const SOURCE = "target/allure-report/index.html";

if (!existsSync(SOURCE)) {
  console.error(
    `No report at ${SOURCE}. Run the tests first: ./mvnw verify, which generates it before this step.`,
  );
  process.exit(1);
}

const now = new Date();
const pad = (n) => String(n).padStart(2, "0");
const stamp =
  `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
  `-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;

const target = `report_${stamp}.html`;
copyFileSync(SOURCE, target);

const kb = (statSync(target).size / 1024).toFixed(0);
console.log(`Allure report: ${target} (${kb} kB, self-contained)`);
