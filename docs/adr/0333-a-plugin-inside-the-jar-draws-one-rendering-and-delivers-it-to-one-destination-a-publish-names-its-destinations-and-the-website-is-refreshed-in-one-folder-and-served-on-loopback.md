# ADR-333 — A plugin inside the jar draws one rendering and delivers it to one destination, a publish names its destinations, and the website is refreshed in one folder and served on loopback

- **Date**: 2026-11-02
- **Status**: accepted on 2026-11-02.
- **Decided**: by the operator on 2026-11-02: the ruling in bold at the head of each of §1 to §12, the order of §13 and the refusals under Alternatives refused marked as theirs. What a section adds beneath its ruling is what building it needs, and Decided here lists each such choice.
- **Amends**: [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §1, where what a publish sends is what the destination's plugin delivers (§6); §4, whose one act is delivering to one or more destinations (§6); §5, whose command gains `--to` and is no longer the last command (§6, §8); §6, whose offer gains a choice of destination (§6); §7, whose `confluence` names `deliverable`'s seam and holds the drawing too, its `Publisher` still reading the page list from disk by its contract (§1); and its Tests' `theCommandHasRunLabelAndPublish`. Its §2, §3, §8 as later records left it, §9 and §10 stand.
- **Amends**: [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md)'s Decision as ADR-301 and ADR-308 rewrote it, and [ADR-102](0102-publication-is-the-state-the-deliverable-is-handed-over-in-never-a-verb-this-project-uses.md) as ADR-308 amended it: `vespera publish` delivers a plugin's rendering to each destination it names, "the Confluence rendering, and nothing else" holding of the Confluence destination alone. The run still ends at stage 6b, a publish is not a stage, and *publication-ready* keeps its sense.
- **Amends**: [ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md) §1 as ADR-308 amended it, stage 6b drawing every plugin's rendering beside the HTML tree (§3, §4); and §4, whose parity test holds three renderings, the cost it accepted being accepted again because something reads the third (§3).
- **Amends**: [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md) §1, §2 and §4, each of which holds of `website/` as of `confluence/`, drawn always included (§3, §4); and §5, a publish reading the state of the drawing beside the page list (§11). §3's 58 and 135 characters stand (§3).
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §1's tree, which gains `website/`, its "no `<script>`" holding of the HTML tree's pages (§3); §2.4's copy trusted at its own name, which the website's copy is not (§7); and §8, a document being named outside the tree by the website alone, and `DeliverableProgress` taking a plugin's name and an exception's type (§7, §11).
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1, the CLI being `run`, `label`, `publish` and `serve`, the deliverable root staying a property (§6, §8); §2, whose refusals gain the website's folder (§7); and §3's "as nothing is in the working directory", and [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)'s "Nothing is ever cleaned up", each of which gains one exception: what a publish removes from the website (§7). Nothing is deleted beneath a deliverable root.
- **Amends**: [ADR-047](0047-the-pipeline-never-blocks.md) as ADR-308 amended it: the CLI is four commands, and `publish` takes one option beyond `--db-dir` (§6, §8). A pipeline that never blocks stands: `vespera serve` is no part of the pipeline.
- **Amends**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) as later records narrowed and widened it: an eleventh module, `website`, and a third declared exception to the module rule: a plugin's module names `deliverable`, whose seam it implements, and `synthesis`, whose records the seam hands it (§1).
- **Amends**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) §1's "Every invocation takes", which `vespera serve` does not: it reads one folder and opens no database (§8).
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, its `publish` child carrying `--to` (§6). It starts no `serve`.
- **Amends**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md) §4 as ADR-312 amended it, whose listing of what an earlier drawing left looks inside `website/` as it looks inside `confluence/` (§3).
- **Amends**: [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md), every fixed word of the website rendering being drawn in the deliverable language, from the same table (§3).
- **Keeps**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §3's "The deliverable is not served", of the control panel, and [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §6: no request path of the control panel maps to a file. `vespera serve` is the jar's own command, on a port of its own (§8). [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1: the launch values stay seven, a destination being none. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names no class of the jar (§6).
- **Keeps**: [ADR-283](0283-the-private-paths-guard-reads-a-deliverable-tree-as-outside-the-allow-list-wherever-it-lies-by-its-marker.md): the guard names no folder and knows a tree by the name of its marker, which the website carries, so the guard is unchanged (§7). ADR-264's and ADR-196's rule that no agent reads the operator's documents holds of the website.
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): no dependency and no plugin of the build is added; the website's script and theme are ours (§3), and `vespera serve` stands on the JDK's `com.sun.net.httpserver` (§8). ADR-223 §9 and ADR-220: a drawing holds one seed partition's rows at most (§2, §9). [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md): the tree still copies every code set (§10). ADR-308 §2 and ADR-305 §2: a publish to Confluence goes to one `https` host of the documents' owner (§8).

## Context

**What the jar delivers today.** `vespera publish` sends the Confluence rendering to one Confluence Cloud site (ADR-308). `VesperaCommand.Publish` names `confluence.Publisher` and its page list, finds the last generation run whose tree holds that list, and returns one closing line. The rendering is drawn by `deliverable.ConfluencePages`, which `Deliverable.writeTo` opens beside the HTML tree and calls at five places as it goes through the seed partitions: `partitionStarted`, `entriesOf`, `clusterDrawn`, `partitionEnded` and `finish`. `ClusterPage` writes the entries of each cluster through what `entriesOf` returned. So the drawing already has the shape of an observer, and nothing but its one name ties it to Confluence.

**What a failure of it does today.** An `IOException` out of `ConfluencePages` leaves `Deliverable.writeTo` as an `UncheckedIOException`, "could not write the deliverable tree", and the generation step ends failed, with the HTML tree's index and manifest possibly in place and the Confluence rendering part drawn.

**A reader without Confluence.** Operators reach the deliverable by opening `index.html` from a folder. A website that a reader can navigate and search, on a machine with no network or on a synced copy, needs a rendering the HTML tree is not: ADR-274 §1 keeps its pages free of every script, and a redrawing's folder is named for its generation run, so it moves with every run.

**What can reach the website safely.** Stage 1 keeps configuration files inside code sets as code (ADR-287), and such files can hold secrets. The operator's own sync of a folder carries whatever lies in it.

**A page opened from a file.** By the browsers' published same-origin rules, a script's `fetch` of a file from a page opened at a `file:` address is refused, and a file named by a `<script src>` there loads. This is not measured. The operator's synced copy is opened so as often as it is served.

## Decision

### 1. A plugin is a Java interface inside the jar, each a Spring bean

**Plugins are a Java interface inside the jar, each a Spring bean; no jar is loaded at run time for now. `confluence.Publisher` goes behind it.**

```java
package io.algernon.vespera.deliverable;

public interface Plugin {
    /** The destination's name on --to, and the plugin's folder at the tree's root: lowercase letters, no digit first. */
    String name();
    /** The file of its rendering, in that folder, a publish finds the rendering by: pages.json, files.json. */
    String listName();
    Drawing drawing(DrawingContext context) throws IOException;
    Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out);

    record Delivery(boolean delivered, String closingLine) {}
    record DrawingContext(Path tree, String generationRun, ReplacingMove moves, Set<String> written, DeliverableLanguage language) {}
}
```

**The modules.** The seam lies in `deliverable`, which drives a drawing and whose records it hands over, and `deliverable` names no plugin: `pipeline` hands `writeTo` the beans. Each plugin is a module of its own:

| Module | Holds | Declares |
| --- | --- | --- |
| `confluence` | `ConfluencePlugin`, `Publisher` and the classes it calls, and, moved from `deliverable`, `ConfluencePages`, `ConfluenceEntries`, `ConfluencePage`, `ConfluenceTitles` and `ProseStorage` | `ledger`, `deliverable`, `synthesis` |
| `website` (new) | `WebsitePlugin`, its drawing, its delivery, and the server of §8 | `ledger`, `deliverable`, `synthesis` |

`Publisher` keeps reading the page list and the files it names from disk, by the page list's contract, and its signature does not change: `ConfluencePlugin.deliver` calls it and hands back its outcome. `NothingShippedReachesConfluenceTest` still holds that no class outside `confluence` names a Confluence REST path.

### 2. A plugin both draws and delivers

**A plugin both draws and delivers. `ConfluencePages`'s five calls become the drawing interface.**

```java
public interface Drawing extends AutoCloseable {
    void partitionStarted(ListedPartition partition, int partitionCount) throws IOException;
    Entries entriesOf(ListedPartition partition, RecordedCluster recorded, List<ListedSurvivor> members,
            Path clusterFolder, Copies copies) throws IOException;
    void clusterDrawn(ListedPartition partition, RecordedCluster recorded, Optional<SynthesisDoc> doc, Unwritten why,
            int entryCount, Optional<ClusterPage.PageBreaks> breaks, Entries entries, boolean writingFinished)
            throws IOException;
    void partitionEnded() throws IOException;
    void finish(Path manifest) throws IOException;
    @Override void close() throws IOException;
}

public interface Entries extends AutoCloseable {
    void start(int number, Optional<ListedSurvivor> entry) throws IOException;
    void copyShown() throws IOException;
    void picture(String digest, boolean png, ListedPicture picture) throws IOException;
    void notShown(int notShown) throws IOException;
    void end() throws IOException;
    @Override void close() throws IOException;
}
```

`Deliverable.writeTo` calls every plugin's drawing at each of those places, in the order of `Plugin.name()`, and `ClusterPage` is handed one `Entries` that passes each call on to every plugin still drawing. What `Deliverable` already makes public through these signatures, `Copies`'s outcome, name and path of a member and `IndexPage`'s folder names among it, is what a plugin reads; a plugin writes only beneath its own folder, and adds each folder it writes to `written` (ADR-312 §4). A drawing holds one seed partition's rows at most, as `ConfluencePages` does (ADR-223 §9).

### 3. The website rendering

**The website is a third rendering, with navigation, search and a theme, its script and theme shipped inside it, no CDN, so it works offline and on a synced copy. It is drawn in the deliverable language (ADR-321) and held by the renderings' parity test for its citations.**

It lies under **`website/`, directly at the tree's root**, as `confluence/` does, and no folder the tree draws can be so named, for the reason ADR-312 §1 gives. A file of it lies 8 characters deeper than its counterpart in the HTML tree, and `confluence/`'s 11 are what ADR-312 §3's bounds allow for, so 58 and 135 stand.

```
website/
  index.html  documents.csv  website.js  website.css  search-index.js
  files.json  drawing.txt
  <partition>/<cluster>/index.html, index-<k>.html
```

- **`files.json`** is the rendering's list, written last, as the page list is: its format and one entry per file of the website, its `path` there, its `source` from `website/`, its `size`, its `sha256` and its `kind`, one of `page`, `asset`, `listing` or `copy`. A `copy`'s `source` is its place in the HTML tree and its `sha256` is the key stage 2 recorded, as the page list has them (ADR-316, ADR-313 §1), so a drawing reads no copy's bytes for it. A copy whose outcome is not `copied` is not listed, and its entry says why, as the HTML page's does.
- **A page** follows the HTML tree's cluster pages: the membership order, the citations, `clusterPageByteCeiling`'s split (ADR-303), each picture inlined (ADR-286), its writing through `ProseHtml` as the HTML tree's is (ADR-274 §1.3). Every fixed word is in `FixedWords` in both languages, and a cluster is a group, or *gruppo* (ADR-321, ADR-122).
- **Self-contained.** Every `src` and `href` is relative and lands inside the website; none names a scheme or a host, and `website.css` names no `@import`, `url(` of a host or `@font-face` of a host. Nothing is fetched: `website.js` reads what `search-index.js` set, so that a page opened at a `file:` address searches as a served one does. `website.js` writes what a search finds as text, never as markup. No third party's script or stylesheet ships.
- **Light and dark** by `prefers-color-scheme`. A narrow window takes one column.
- **`documents.csv`** is carried, as the Confluence rendering's root page carries it (ADR-315); `code.csv` is not (§10).
- **It is read in the website, not in the tree.** A page's link to a copy is to the file beside it, which stands only once a publish has refreshed the website, so `<tree>/website/` is what a publish reads and not a thing to open.

### 4. Every plugin draws on every drawing

**Stage 6b draws every compiled-in plugin on every drawing**, as ADR-312 §2 has it of the Confluence rendering: no profile key, no property and no option turns one on or off. What a plugin costs a drawing is the disk its pages take, the website's copies taking none in the tree.

### 5. The HTML tree is not a plugin

**The HTML tree stays the core and is not a plugin. A plugin may refer to its copies, and their checking stays with it.** `Copies` alone reads the archive and alone checks a copy against stage 2's key (ADR-274 §2). A plugin reads what `Copies` found, and no plugin opens the archive. A failure of the HTML tree stays what it is: the drawing fails.

### 6. `vespera publish --to`

**`vespera publish --to confluence,website` chooses the destinations; there is no profile key. Bare `vespera publish` stays Confluence. Each destination runs in turn, one stopping does not stop the next, each has one closing line, the invocation exits 1 if any stopped, and publishing again resumes. The control panel gains a choice of destination.**

- **`--to`** takes names split at `,`, delivered in the order written, a name written twice delivered once. A name no plugin has is refused before anything is delivered, exit 2: `vespera publish has no destination <name>: it knows confluence, website.`, the names read from the beans.
- **The lock** is held for all of them (ADR-177).
- **Each finds its own rendering.** For each destination, the last generation run whose generation step is recorded finished and whose tree holds that plugin's folder with either its `listName()` or a `drawing.txt` is the one, as `lastDrawn` asks today of the page list (§11). None found refuses that destination with ADR-308 §5's line, which names it: `vespera publish sent nothing to <name>: no <name> rendering is drawn in this working directory. Run vespera run to the deliverable, and publish again.` A bare publish says what it says today.
- **Credentials are the Confluence plugin's**: `--to website` reads none of ADR-308 §3's five, and misses none.
- **The lines.** A plugin's lines and closing line go where ADR-308 §5 and ADR-320 have them, a delivery that stopped on standard error, and the invocation's last line is the last destination's closing line. A plugin that throws is a stop, its closing line naming it and the exception's type and never its message (ADR-274 §8).
- **The control panel.** The form `publish` on the invocation page gains a checkbox `to` per destination, offered where that destination's list is found in the tree, Confluence's checked. `POST /publish` reads them against a closed list of its own, in `io.algernon.controlpanel` (ADR-285 §3), and starts `ChildCommand.publish(java, jar, values, destinations)`, whose arguments are `publish --to <names>`; with none checked it starts nothing and answers one line in `outcome`. A test holds the closed list to the jar's plugin names.

### 7. The website is one folder, refreshed

**`publish --to website` refreshes one stable folder, `<working directory>/website/`, and ends. It updates by digest and removes only files its own previous publish listed, so anything the operator put there stays.** It lies in the working directory whatever deliverable root is named, and its name holds no generation run, so a reader's link and an operator's sync point at one place through every run.

- **In order**, each file through `.part` and moved into place: `vespera-deliverable.txt`; `vespera-website.json` holding what the previous one listed and what `files.json` lists, so a publish stopped partway still knows what it wrote; every file `files.json` lists whose standing file does not hash to its `sha256`, copies first and `index.html` last; `vespera-website.json` holding what `files.json` lists alone; then the removals. A file standing with the right digest is not written. A source that is not at the size and digest its list names stops the delivery, with the line of ADR-308's `unlikeItsList`: redraw and publish again.
- **What is removed**: a file the previous `vespera-website.json` listed, `files.json` no longer lists, and which still hashes to what was listed. One the operator changed stays and is counted. A folder so emptied is removed. Nothing else is touched. A file of the operator's at a name `files.json` lists is replaced, and that is the one thing of theirs a publish can overwrite.
- **Closing line**: `vespera publish refreshed the website at <folder>: <n> files written, <n> standing, <n> removed, <n> changed by hand and kept. Open <folder>/index.html, or run vespera serve.`; stopped: `vespera publish stopped the website: <reason>. <n> of <n> files written. Publish again to go on from there.`
- **To an agent it is a tree.** `vespera-deliverable.txt` at its top makes the guard read the folder as outside the allow list, and no agent reads or writes there (ADR-283). The file holds one line, `website`, and names no path, since an operator's sync carries it. `DeliverableRoot` reads it as no marker (ADR-276 §3).
- **A deliverable root at or inside it is refused** at the start of `vespera run`, after ADR-276 §2's comparisons and by the same reading of paths, since a tree there would be carried by the operator's sync with its code sets: `vespera run refused the deliverable root <root>: it is <relation> of this working directory. Name a folder outside it in vespera.deliverable-root.`, `<relation>` being `the website folder` or `inside the website folder`.
- **The ledger is not written.** A publish to the website mints no run, as ADR-308 §5 has it of a publish.

### 8. `vespera serve` is a command of its own, on loopback

**`vespera serve` is a separate command serving that folder, on loopback alone, with no login. For the website Vespera sends nothing to a remote host: no SFTP, S3 or git transport, remote reach being the operator's own sync or proxy. Any remote transport a later record adds is encrypted only, its access control the operator's.** A publish to Confluence stays as ADR-308 §2 has it, one `https` host of the documents' owner, and meets that rule.

`vespera serve [--db-dir <working directory>]` serves `<working directory>/website/` until its process ends.

- **It binds `127.0.0.1` alone**, which no value changes, at the port the property `vespera.serve.port` names, shipped as 24332 in `application.yaml`. It prints one line, `serving <folder> at http://127.0.0.1:<port>/ until this process ends`, and opens no browser.
- **It refuses in one line, exit 1**: a folder holding no `vespera-website.json`, `vespera serve has nothing to serve: <folder> holds no published website. Run vespera publish --to website first.`; and a port in use, `vespera serve could not listen on port <port>: it is in use. Name another in vespera.serve.port.`
- **It holds no lock and opens no database.** It takes neither `vespera.lock` nor the corpus-root lock, creates no working directory, reads no profile, starts no sidecar and writes no progress page, no account and no next-invocation file, so a publish can refresh the website while it serves, each file arriving by a move. `VesperaApplication.main` keeps it from the listeners ADR-177 and ADR-054 add there.
- **What it answers.** `GET` and `HEAD` alone. `Host` must be `127.0.0.1:<port>` or `localhost:<port>`, and a `Sec-Fetch-Site` of `cross-site` or `same-site` is refused, as ADR-291 §4 and ADR-302 §12 refuse them, because the folder holds the operator's documents and another website in the same browser must not read them. A path is resolved beneath the folder and answered only where its real path lies beneath the folder's. A folder answers its `index.html`, or 404: no listing. `vespera-deliverable.txt`, `vespera-website.json` and any `.part` are 404. Every answer carries `X-Content-Type-Options: nosniff`, and every file `vespera-website.json` does not list as a `page`, `asset` or `listing`, every copy and whatever the operator put there, carries `Content-Security-Policy: sandbox`, so a document that is HTML runs no script in the website's origin.
- **It logs no request**, a request's path naming a document (ADR-274 §8).
- **Publish does not serve.** A publish ends, and a reader's server is a command the operator starts and ends.

### 9. Search is the website's own, in the browser

**Search runs in the browser, over titles, labels, the synthesis writing and entries' names, its index drawn at stage 6b. It reads no document's extracted text, and no server answers it.** `search-index.js` holds, for each cluster, its seed partition's name, its cluster title, its cluster label, the text of its synthesis doc and each entry's name as its page shows it, and each entry's link. It is written a cluster at a time through its `.part`, the drawing holding none of it (ADR-223 §9). A cluster with no synthesis doc is found by its label and its entries.

### 10. Copies are on the website, code sets are not

**The copies are on the website, its links landing inside it (ADR-274). The code sets are not: no `code/` and no `code.csv`, because configuration files can hold secrets and the operator's sync would carry them. They stay in the HTML tree.** The website's index has no row of a code set, and a publish reads no file of `code/`. A copy on the website is written a second time, once in the tree and once in the folder, and that disk is the website's cost (§7).

### 11. A plugin's failed drawing stops that plugin alone

**A plugin's failed drawing is isolated: the tree and the other plugins finish, the closing line names the failed plugin, and publishing to it refuses until a redraw. This changes what a failure of the Confluence rendering does (Context).**

- **What is caught.** An `IOException` or `RuntimeException` out of any call of a plugin's `Drawing` or `Entries`, or out of `Plugin.drawing`. `Deliverable` closes that drawing, swallowing what closing it throws, calls it no more in this drawing and goes on. An `Error` is not caught.
- **The state of a drawing.** `Deliverable` writes `<plugin folder>/drawing.txt`, in place, as ADR-276 §3 writes the marker: `drawing` as it opens the plugin's drawing, `drawn` once its `finish` and `close` return, and `failed` when it is stopped. No plugin writes it, since one that has failed may not be able to, and nothing deletes it (ADR-312 §5).
- **The publish.** A publish reads it before anything else of that destination. Absent, as in a rendering drawn before this record, the list alone decides, as today, and no check of a missing rendering is added (§12). `drawn` goes on. Anything else stops that destination, with no site or folder touched: `vespera publish sent nothing to <name>: the last drawing of its rendering did not finish. Run vespera run to draw it again, and publish again.` Since `lastDrawn` stops at the first run whose folder holds one or the other (§6), an older run's rendering is never delivered in place of the failed one.
- **The closing line.** `NextAction` adds a clause per failed plugin: `the <name> rendering could not be drawn (<exception type>), so a publish to it is refused until a drawing finishes`. `DeliverableProgress` takes the plugin's name and the type, each a word of ours and neither a document's. No exception's message is written (ADR-274 §8).
- **The invocation** exits as it would have: the deliverable's tree is drawn, and the HTML tree's own failure stays the one failure of the drawing.

### 12. A run drawn before this record

**A recorded generation step already redraws the whole tree from stored rows with no question put (`GenerationTasklet`'s recorded path, ADR-274 §6), so `vespera run` draws the website on an earlier run, and no "rendering missing" check is added.** A publish to the website before that is ADR-308 §5's refusal (§6). What that `vespera run` needs before it reaches the drawing is what every invocation needs: the generation step's check of its model comes first, on the recorded path too (ADR-329 §1, §3), so Ollama is asked once whether it serves the model, and pulls or refuses as that record has it, though no question is put to the model.

### 13. The order of the build

**First the seam, with Confluence behind it and no behaviour changing but §11's. Then the website plugin, §3, §6's `--to` and §7. Then `vespera serve`, §8.** §6's control panel comes with the second.

### 14. Run ids

**No run id moves.** `StageModules` lists no `deliverable`, `confluence` or `website` for any stage: generation's list is `synthesis`, `extraction` and `embedding`, and arrangement's the same. None of those three changes for this record, since every type the seam names from `synthesis` is already public. No profile key is added, so no `config_consumed` changes. The next invocation that reaches the tree redraws it, the website rendering with it, at no cost in writing.

### 15. What the documents say

- **`CONTEXT.md`**, with this record: **Plugin**, **Destination**, **Website rendering** and **Website** are added; **Publish**, **Publication-ready artifact**, **Confluence rendering**, **Deliverable root**, **Deliverable language**, **Working directory**, **Working-directory lock**, **Copy**, **Picture**, **Code set** and **Cluster folder** say what is said here.
- **Each with the step that makes it true**: `AGENTS.md`'s modules, commands and its rule on who reads a tree; `README.md`'s commands, `vespera.serve.port` and a section on the website, `check-claims` reading each; `docs/architecture.md` §1, its module table and its diagram; `docs/running-stage-by-stage.md`'s stage 6b; `application.yaml`'s `vespera.serve.port`.

## Decided here

1. **The seam in `deliverable`, each plugin a module of its own, `website` the eleventh, and the Confluence drawing moved into `confluence`** (§1), so that a plugin is one unit, and adding one does not touch `deliverable`. It reverses ADR-308 §7's "names no class of `deliverable`", whose first reason, a change to the publisher moving stage 6b's version, ADR-274 §5 had already removed.
2. **`website/` at the tree's root, its copies only listed there** (§3), as ADR-312 and ADR-316 have it for Confluence.
3. **`search-index.js` set by a script and never fetched** (§3, §9), since a `fetch` from a `file:` page is refused (Context).
4. **`drawing.txt` written by `Deliverable`, in place, three words** (§11), since the page list of an earlier drawing can stand beside a part-drawn rendering, nothing beneath a deliverable root is deleted, and a stop that a plugin has to report cannot be relied on from a plugin that failed.
5. **`lastDrawn` stopping at a folder whose drawing did not finish** (§6, §11), which is what keeps an older run's rendering from being delivered in place of the failed one.
6. **`vespera-deliverable.txt` in the website, one line and no path** (§7), which keeps the guard unchanged and its rule whole.
7. **`vespera-website.json` written first with both lists, and a file changed by hand kept** (§7), so a publish stopped partway leaves nothing it cannot later remove, and "anything the operator put there stays" holds of a file it once wrote.
8. **A deliverable root at or inside the website refused** (§7), since a tree there would hand its code sets to the sync §10 keeps them from.
9. **`vespera serve` holding no lock and opening no database** (§8), so that a reader's server does not refuse the next `vespera publish` or `vespera run`.
10. **Port 24332, a refusal where it is taken** (§8), a port the system picks changing the address every reader holds.
11. **`Host`, `Sec-Fetch-Site` and the sandbox on what is not a page** (§8), no login being the ruling, and a website in the operator's own browser being what is left to keep out.
12. **`documents.csv` on the website, by name and path as the Confluence rendering carries it** (§3).
13. **A failed plugin leaving the invocation's exit as it was** (§11), the deliverable being drawn.

## Known limits

- Without a login, anything local that can reach `127.0.0.1` can read the website while `vespera serve` runs. That is what loopback alone leaves.
- A document may hold what a configuration file holds. §10 keeps code sets from the sync; it reads no document, and a survivor's copy goes where the operator's sync goes.
- A working directory that lies inside the corpus root, where a deliverable root is named elsewhere, is not refused by ADR-276 §2, so its `website/` would be walked at the next invocation, as its other files are.
- A plugin whose folder cannot be made has no `drawing.txt` to say so. The closing line says it, and a publish looks to the rendering of an earlier run.
- The control panel's `last-line` holds the last destination's closing line alone, though an earlier one stopped; `tail` holds them all, and the ending shown is read from the exit.
- `<tree>/website/` cannot be read as a website: its copies are not beside its pages (§3).
- A file the operator put at a name `files.json` lists is replaced (§7).
- The website's pictures are inlined, so a page's size follows the HTML tree's and its ceiling (ADR-303), and `search-index.js` is read whole by each page that searches.

## Alternatives refused

Refused by the operator, the reason beside each being this record's:

- **Jars loaded at run time.** A class-loading surface and a compatibility promise, for plugins nobody else writes.
- **A plugin that only sends**, with the rendering drawn elsewhere. A rendering with no owner, and a page list contract across a module line for every new destination.
- **Drawing at publish time.** A publish would need the ledger's rows and the rules of the tree, and would read what a stage writes; a failure would be found at the last moment. ADR-312 §2 stands.
- **Plugins enabled by the profile.** The profile describes a corpus, and a key would move the generation run (ADR-274 §5).
- **A destination named in the profile.** The pipeline never publishes, ADR-035's rule as ADR-308 restored it: a publish is an act the operator starts each time.
- **The HTML tree as a plugin.** The copies and their checking have to stand under every rendering, so it cannot be the same kind of thing as what refers to them.
- **Binding the network, with TLS or a login.** A credential store and a certificate to keep, for a reach the operator's own proxy gives with an access control of theirs.
- **Search over documents' extracted text.** The index would hold a copy of every document's text on every sync; the copies are there to read.
- **Search answered by a server.** A synced copy would have none.
- **SFTP, S3 or git as transports now.** Each is a credential and a host nobody has asked Vespera to hold, and the operator's own sync already does it.
- **A publish that blocks and serves.** A publish ends with its closing line, and the working-directory lock would be held for as long as anyone read.

Refused here:

- **The plugin beans in `pipeline`, the drawing left in `deliverable` and `confluence` left declaring `ledger` alone.** The smallest move, and it honours ADR-308 §7 to the letter; but one plugin would lie in three modules, and adding one would change `deliverable`.
- **Each plugin's copies drawn into its own folder.** A second copy of every survivor in the tree at every drawing, needed by no publish.
- **The website's copies linked from the tree by `../`.** The folder would not be the whole of what a reader needs, and a sync would carry dead links.
- **`vespera serve` taking the working-directory lock.** Every `vespera run` and `vespera publish` would be refused while a reader reads.
- **A marker that names the working directory.** It would hand that path to the operator's sync, which the rendering otherwise keeps from every page (ADR-315 §1).

## Consequences

- **A failed Confluence drawing no longer fails the generation step**: the tree is whole, and a publish to Confluence is refused until a drawing finishes (§11).
- **An interrupted drawing leaves `drawing`**, so a publish to either destination refuses it where it would have sent what the earlier page list named, or stopped on `unlikeItsList`.
- **The CLI is four commands**, and `publish` takes an option.
- **The working directory's drive holds a second copy of every survivor** once the website is published, where the deliverable root may lie on another. The operator is not warned beyond what `vespera publish` says of a file it cannot write.
- **`confluence` names `deliverable`**, a third declared exception to the module rule; `website` does too.
- **Removing one thing in the working directory is possible for the first time**: a file a publish wrote and no longer lists.
- **A plugin added later is a module, a bean, a folder and a destination**, and states how it keeps its folder clear of the tree's names (ADR-312 §1).
- **`website.js` has no test in the Java build**, which has no JavaScript engine and gains none (ADR-297 §1, ADR-046); it is held by `node --test`, as the guard is.

## Tests

Built with the step of §13 that needs each.

| Method | What it holds |
| --- | --- |
| `ModuleBoundariesTest.aPluginsModuleDeclaresTheSeamAndNothingElseHorizontal` | `confluence` and `website` declare `ledger`, `deliverable` and `synthesis`, and `deliverable` names neither (§1); it replaces `thePublisherIsAModuleOfItsOwn` |
| `NothingShippedReachesConfluenceTest.theCommandHasRunLabelPublishAndServe` | the command tree (§6, §8); it replaces `theCommandHasRunLabelAndPublish` |
| `deliverable.EveryPluginDrawsOnEveryDrawingTest` | two scripted plugins both hear each of the five calls in order, on the recorded path too (§2, §4, §12) |
| `deliverable.APluginsFailedDrawingStopsItAloneTest` | a plugin that throws at each of the calls in turn: the tree byte for byte as with none, the other plugin whole, `drawing.txt` `failed`, `drawn` after the next drawing (§11) |
| `confluence.PublishRefusesARenderingWhoseDrawingDidNotFinishIT` | `failed` and `drawing` send nothing and name the plugin; an absent `drawing.txt` sends as today; an older run's rendering is not taken in place (§6, §11) |
| `pipeline.PublishToEachDestinationInTurnInvocationTest` | bare is Confluence; `--to` in order, twice once; an unknown name exit 2 before anything; one stopped does not stop the next, exit 1, one closing line each, the last last (§6) |
| `website.TheWebsiteIsSelfContainedTest` | no scheme or host in any `src` or `href`, no `url(` of a host, nothing fetched, every link landing inside (§3) |
| `website.TheWebsiteCarriesNoCodeTest` | no `code/`, no `code.csv`, no row of a code set, and nothing in `search-index.js` of a code set's (§10) |
| `website.TheSearchIndexHoldsNoExtractedTextTest` | titles, labels, the writing and names are in it, and a marked sentence of a document's extracted text is not (§9) |
| `website.TheWebsiteIsInTheDeliverableLanguageTest` | Italian's fixed words, `gruppo`, and no English word of the table (§3) |
| `deliverable.TheConfluenceRenderingTest.bothRenderingsCarryTheSameEntriesAndCitations`, extended | all three renderings carry the same entries and citations (§3) |
| `website.PublishRefreshesTheWebsiteByDigestTest` | a second publish writes nothing; a changed source is written; a file the list drops is removed; the operator's file stays; a listed file changed by hand stays and is counted; a stop midway resumes and leaves no orphan (§7) |
| `website.TheWebsiteCarriesAMarkerAndNoPathTest` | the marker's name, its one line, and no path of the working directory in it or in `vespera-website.json` (§7) |
| `deliverable.DeliverableRootTest.refusesARootAtOrInsideTheWebsiteFolder` | each relation, its whole line (§7) |
| `website.ServeAnswersOnLoopbackAloneTest` | bound to `127.0.0.1`, a foreign `Host` and `Sec-Fetch-Site` refused, `POST` refused, a `..` and a link leading out 404, no listing, the marker and the list 404, `sandbox` on a copy and not on a page (§8) |
| `pipeline.ServeHoldsNoLockIT` | `vespera serve` starts while another invocation holds the lock, and `vespera publish --to website` completes while it serves, no `vespera.db` made (§8) |
| `controlpanel.PublishOffersEachDestinationDrawnTest` | a checkbox per rendering drawn, `--to` in the child's arguments, a name outside the closed list refused, the closed list equal to the jar's (§6) |
| `src/test/website/website.test.mjs`, by `node --test` | what a search finds, set as text and not as markup (§3, §9) |

`deliverable.TheTreeIsHtmlTest.everyPageStandsAlone` goes on holding the HTML tree's pages and does not walk `website/`. `PipelineHoldsOnlyTheRulesOnRecordTest` lists what `pipeline` gains.

## What this does not decide

- **The website's look**: its navigation's shape, words beyond the table, its typeface and colours, what a search shows beyond a title and a link.
- **A plugin outside the jar**, and any promise of the seam's stability to one.
- **A remote transport**: §8's rule binds one when a later record adds it, and none is added.
- **A warning about the disk the website takes** before a publish fills a drive.
