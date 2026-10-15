# The review register

The register is a published page, <https://claude.ai/artifact/YPFXg8p978qBqJK9vLbTTB>, of every finding of every review of `main` and what became of it. It is two files: a page that holds the layout and the filters, and `register-data.js`, which holds what changes: `META`, one html line for each pass or merge, and `ITEMS`, one object for each finding.

A read of the whole page echoes all of it into the conversation, and with the data in the page that was some thirty thousand tokens each time. The page is now a few kilobytes, and the data is a file that is read by its path, which saves it to disk and does not echo it.

## Updating it

1. Save the two files: `Artifact` with `action: "read"`, the register's `url` and `path: "index.html"` for the page, then again with `path: "register-data.js"` for the data. Each result names where it was saved. The data is saved without being echoed; the page is small enough that its text comes back as well, which is a few thousand tokens.
2. Edit that copy with `docs/review-register.mjs`, which writes it back in the form the page reads:

   - `node docs/review-register.mjs meta <data.js> <note.html>` appends a line to the meta lines. The note is html in a file, so that no quoting is needed.
   - `node docs/review-register.mjs item <data.js> <item.json>` appends a finding, or an array of them. An id that is already there is refused, and so is a finding that lacks a field.
   - `node docs/review-register.mjs status <data.js> <id> <status> [commit]` sets a finding's status, and its commit when one is given. The statuses are `fixed`, `refuted`, `intended`, `partial`, `deferred` and `open`.
   - `node docs/review-register.mjs check <data.js>` holds that every finding has all twelve fields, every id is there once and every status is one the page has a tile for.

3. Publish with the register's `url`, the saved page as `file_path` and `files: {"register-data.js": "<the copy>"}`, and a `label`. The page is a few kilobytes; the data is the part that grows.

A finding has these fields: `id`, `area`, `file`, `line`, `verdict`, `status`, `commit`, `title`, `summary`, `scenario`, `source`, `why`. A new `source` or `area` needs no change to the page except the option in the source filter, which is in the page.

`src/test/docs/review-register.test.mjs` holds the script: `node --test src/test/docs/review-register.test.mjs`.
