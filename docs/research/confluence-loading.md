# Routes for loading a deliverable tree into Confluence

Research record for [#605](https://github.com/algernon28/vespera/issues/605), under map
[#604](https://github.com/algernon28/vespera/issues/604). Facts only — no decisions. Feeds the map's grilling
tickets, from [#607](https://github.com/algernon28/vespera/issues/607) on, and the prototype
[#611](https://github.com/algernon28/vespera/issues/611), which confirms every Confluence fact below on a
sandbox space before anything is built on it.

§1 describes the tree as the record and the code draw it. §2 gives Confluence's routes, APIs, storage format and
clients, per edition. §3 compares options A to D. §4 checks the facts the ticket started from.

## Scope and method

**Nothing in §1 to §4 is measured.** No Confluence space was reached by an agent, and none will be (#604, Notes). §5 adds what the operator measured on a Cloud site for #611.

**The tree (§1) is read from the record and the code alone**: ADR-274, ADR-276, ADR-280, ADR-286 and ADR-296,
and the classes of `io.algernon.vespera.deliverable` at main `8886ca1`. No copy, drawn tree, seed set, archive
or operator document was opened (ADR-196, ADR-264, ADR-274 §8, ADR-283).

**Confluence (§2) is read from the web, on 2026-10-19.** The egress proxy refused every Atlassian host tried:
`developer.atlassian.com`, `support.atlassian.com`, `confluence.atlassian.com`, `jira.atlassian.com`,
`community.atlassian.com`, `community.developer.atlassian.com` and `www.atlassian.com`, and the mirrors
`web.archive.org` and `archive.ph`. Only `raw.githubusercontent.com` answered. So two kinds of source stand
behind §2:

- **Read**: the source of four open-source clients, fetched and read whole where cited. They show what a working
  client sends, which is evidence of what Confluence accepts, not Atlassian's statement of it.
  - **F1**, atlassian-python-api, `atlassian/confluence/server/__init__.py` on `master`
    ([repo](https://github.com/atlassian-api/atlassian-python-api)). The old `atlassian/confluence.py` is gone.
  - **F2**, sphinxcontrib-confluencebuilder, `publisher.py`, `rest.py`, `std/confluence.py` and
    `doc/guide-scope-token.rst` on `master` ([repo](https://github.com/sphinx-contrib/confluencebuilder)).
  - **F3**, md2conf, `README.md`, `md2conf/api_v1.py`, `api_v2.py` and `api_base.py` on `master`
    ([repo](https://github.com/hunyadi/md2conf)).
  - **F4**, mark, `README.md` on `master` ([repo](https://github.com/kovetskiy/mark)).
- **Summary only**: a search engine's summary of an Atlassian page, a CONFCLOUD or CONFSERVER issue, or a
  community thread, whose URL is given but whose text was not read. Every such fact is tagged **[unverified]**.

**Tags.** Each fact carries the edition it holds for, **[Cloud]**, **[DC]** or **[both]**, and
**[unverified]** where no source was read. A fact tagged with an edition alone rests on F1 to F4.

---

## 1. The tree, as the record and the code draw it

### 1.1 What is at the deliverable root

```
<deliverable root>/
  index.html                          the arrangement, whole, and what produced it (ADR-274 §1.4)
  documents.csv                       one row per survivor: the manifest (ADR-274 §1.4)
  code.csv                            one row per member of every code set (ADR-296 §5)
  vespera-deliverable.txt             the marker (ADR-276 §3)
  <seed partition>/<cluster>/index.html   one page per cluster (ADR-280 §0)
  <seed partition>/<cluster>/<copy name>  a verified copy of each of the cluster's survivors (ADR-280 §1)
  code/<set>/<kept path>              a checked copy of each member of each code set (ADR-296 §1)
```

The root is `vespera.deliverable-root` where it is named, and `deliverable/<generation run>/` in the working
directory where it is not (ADR-276 §1). One tree per generation run (ADR-103). Nothing beneath a root is deleted,
so a redrawing in a new layout leaves the earlier layout's folders, which the closing line counts (ADR-276 §6,
ADR-280 §4, ADR-286 §4).

### 1.2 The index

`index.html` is headed `Deliverable <generation run>` and lists the run, the walk, the corpus root
(labelled `Archive root`) and every
profile key with its value, then a paragraph on what the folders and `documents.csv` hold (`IndexPage.open`).
**Each seed partition is an `<h2>` naming its seed path, over a table of its clusters**: the cluster's label, its count of
documents, and what it was written up as, the generated title. Only a cluster with writing links its label to
`<seed partition>/<cluster>/index.html`; one without shows ADR-174's sentence in `<em>` and links nothing
(`IndexPage.clusterRow`, ADR-274 §1.4, ADR-280 §0). Where the run has code sets, a `Code`
section follows, one row a set linking `code/<name>/` (ADR-296 §4).

**A seed partition has no page of its own.** It is a heading on the index and a folder on disk, named with its
order prefix and slug (ADR-112, ADR-274 §1.2). The Confluence rendering's parent page for each seed partition
(#604, Destination 2) is a page the HTML tree does not have.

### 1.3 A cluster's page

`<seed partition>/<cluster>/index.html`, one file (ADR-286). It is headed by the generated title, or the
cluster label where nothing was written (`ClusterPage`, ADR-106); then the writing in
`<div class="writing" title="…">`, rendered from ADR-274 §1.3's subset of Markdown by `ProseHtml`, or ADR-174's
sentence in `<em>`; then `The documents in this group` over an `<ol>` numbered as ADR-133 numbers it.

- **Each entry is `<li id="document-n">`** (`ClusterPage.ANCHOR_PREFIX`, `ClusterPage.java:58`). It links
  `./<copy name>`, every byte outside RFC 3986's unreserved set percent-encoded, with the root-relative path as
  its text, or names the path with the reason no copy stands (ADR-280 §5, ADR-274 §2.3).
- **A citation is `<a href="#document-n">[n]</a>`** (`ProseHtml.java:121-124`), landing on its entry in the same
  page. ADR-135 §6 keeps the raw anchor.
- **Pictures are inlined**: an `<img>` whose `src` is a `data:image/png;base64,` or `data:image/jpeg;base64,` URI,
  its `alt` the caption, under its document's entry; no picture file is written (ADR-286 §1, §4). An image
  survivor whose copy is a PNG or a JPEG is shown inline too, from its copy (ADR-286 §6).
- **No size bounds a page.** An inlined picture is about a third larger than its bytes, and a page may be split
  at an entry's end only once #571 chooses a size (ADR-286 §2, §6).
- **Every value is escaped by five entities**: `&amp;`, `&lt;`, `&gt;`, `&quot;` and `&#39;` (ADR-274 §1.3).
  The renderer's only tags are `p`, `br`, `h3`, `h4`, `h5`, `ul`, `ol`, `li`, `strong`, `em`, `code` and `a`.

### 1.4 Copies and code

- **A copy lies in its cluster's folder** under its original's own name, made safe, cut to 100 UTF-16 code units
  and to 255 UTF-8 bytes with `.part`, and suffixed `~<occurrence id>` before the extension only on a collision
  (ADR-280 §1, §3). A name may hold any character but `< > : " / \ | ? *` and controls, `~` and non-ASCII among
  them; it ends in no dot or space, and a Windows device name gets a leading `_` (ADR-280 §1.2, §1.3,
  `CopyNames`). Each is checked against the key stage 2 recorded before it stands (ADR-274 §2.2).
- **A copy has no size bound but the drive's gibibyte** (ADR-274 §2.5).
- **Code sets lie side by side under `code/`**, each member at its kept path, checked by the walk's size and
  time (ADR-296 §2, §3). `code` meets no seed partition's folder, which begins with digits and a `-`
  (ADR-296 §3.1); a `confluence/` folder at the root would meet none either, on a reading of ADR-296 §3.1; reserving it is #604's (ADR-284 §8).
- **Pictures are not files** (ADR-286). On Confluence a picture would have to become a file on its page, so the
  rendering would hold it twice on disk, inlined in the HTML and as a file (#604, Risks).

### 1.5 `documents.csv` and `code.csv`

`documents.csv` has eleven columns: `occurrence_id`, `path`, `content_hash`, `winning_seed`,
`relevance_score`, `seed_partition`, `cluster`, `partition_order`, `cluster_order`, `copy` and `copy_outcome`
(`ManifestCsv.HEADER`). `copy` is `<seed partition>/<cluster>/<copy name>` from the root, and `copy_outcome`
one of `copied`, `changed`, `gone`, `no-room` and `not-copied` (ADR-274 §2.3, ADR-280 §0). It is quoted by
RFC 4180, behind ADR-235's single quote where a cell would begin as a formula. `code.csv` has nine columns:
`occurrence_id`, `path`, `code_set`, `role`, `generator`, `size_bytes`, `last_modified`, `copy` and
`copy_outcome`, with no `content_hash` (`CodeCopies.HEADER`, ADR-296 §2, §5). `code.csv`'s `size_bytes` is the
walk's size of each code file; `documents.csv` gives no copy's size, which the page list a loader reads would
have to (#604, ruling 5).

### 1.6 What a loader meets

| In the tree | Links to | On Confluence (#604, Destination 2) |
| --- | --- | --- |
| `index.html` | each cluster's page that has writing, each `code/<name>/` | the root page |
| a seed partition's `<h2>` and folder | nothing of its own | a parent page, new |
| a cluster's `index.html` | its copies (`./`), its entries (`#document-n`) | a child page |
| a picture, inlined | nothing | a file on the cluster's page |
| a copy | nothing | a file on the cluster's page (ruling 5) |
| `code/` | nothing | not yet specified (#604, Not yet specified) |
| the marker | nothing | never loaded (#604, Out of scope) |
| `documents.csv` | nothing | not settled (#618) |
| `code.csv` | nothing | not yet specified (#604, Not yet specified: code sets) |

**Titles are not unique by construction.** A cluster's page is headed by its generated title or its label, both
a model's words; two clusters may carry the same. The order prefix keeps folder names apart (ADR-274 §1.2), and
nothing keeps titles apart. Confluence is reported to ask a title unique within a space [both] [unverified]
(§2.5).

---

## 2. Confluence, per edition

### 2.1 Editions

| Fact | Tag | Source |
| --- | --- | --- |
| Data Center sells no new subscriptions from 2026-03-30; existing customers may buy and expand until 2028-03-30; licences expire and sites go read-only on 2029-03-28 | [DC] [unverified] | partner summaries, [Seibert](https://us.seibert.group/blog/end-of-life-atlassian-data-center), [Actonic](https://actonic.de/en/statement-regarding-atlassian-data-center-end-of-life/); Atlassian's [blog](https://www.atlassian.com/blog/development/from-data-center-to-cloud-the-next-chapter-for-marketplace-apps) not read. One secondary source gives 2029-03-30 |
| Cloud's REST API is v1 (`/wiki/rest/api/…`) and v2 (`/wiki/api/v2/…`) | [Cloud] | F1, F3 |
| DC's is v1 alone, at `/rest/api/…`: F1's Server class calls no v2, and F3 recommends v1 for *"older Data Center/Server installations"* | [DC] [unverified] | F1, F3 README |

### 2.2 Authentication

| Fact | Tag | Source |
| --- | --- | --- |
| An API token is created with an expiry of 1 to 365 days | [Cloud] [unverified] | [Manage API tokens](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/) |
| Tokens created before 2024-12-15 were given an expiry, falling between 2026-03-14 and 2026-05-12; Atlassian's pages disagree on the dates | [Cloud] [unverified] | same, and [confluence.atlassian.com/x/Vo71Nw](https://confluence.atlassian.com/x/Vo71Nw) |
| A classic token authenticates as Basic `email:token` against the site URL | [Cloud] | F2, F3 |
| A scoped token calls `https://api.atlassian.com/ex/confluence/<cloudId>/`, not the site URL | [Cloud] | F2 `guide-scope-token.rst`, F3 README; [KB](https://support.atlassian.com/confluence/kb/scoped-api-tokens-in-confluence-cloud/) [unverified] |
| `<cloudId>` is read from `https://<site>.atlassian.net/_edge/tenant_info` | [Cloud] | F2 `guide-scope-token.rst` |
| Whether `/wiki/` follows `<cloudId>/` is not settled: F2 and F3 configure the base without it | [Cloud] [unverified] | F2, F3 |
| A scoped token sending pages and files needs, in F2's list, `read:page`, `write:page`, `read:attachment`, `write:attachment`, `delete:attachment`, `read:space`, `read:content-details`, `read:content.metadata`, `read:content.property`, `write:content.property`, `read:label`, `write:label`, `write:watcher` and `delete:page`, each `:confluence` | [Cloud] | F2 `guide-scope-token.rst` |
| A Personal Access Token is sent as `Authorization: Bearer <token>`, since Confluence 7.9 | [DC] [unverified] | [7.9 release notes](https://confluence.atlassian.com/display/DOC/Confluence+7.9+Release+Notes); F3 sends Bearer with no user |

### 2.3 Spaces and pages

| Fact | Tag | Source |
| --- | --- | --- |
| A space is created by `POST /rest/api/space` with `key` and `name` | [both] | F1 `create_space` |
| v2 creates one by `POST /wiki/api/v2/spaces`; it is reported to fail with 500 when `roleAssignments` is sent (CONFCLOUD-83797, CONFCLOUD-84519) | [Cloud] [unverified] | [v2 spaces reference](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-space/), [CONFCLOUD-84519](https://jira.atlassian.com/browse/CONFCLOUD-84519) |
| v1 creates a page by `POST /rest/api/content` with `type: "page"`, `title`, `space.key`, `ancestors: [{id}]` for its parent and `body.storage` holding `value` and `representation: "storage"` | [both] | F1 `create_page`, F2 `publisher.py` |
| v2 creates one by `POST /wiki/api/v2/pages` with `spaceId`, `status`, `title`, `parentId` and a body in representation `storage` | [Cloud] | F3 `api_v2.py`, F2 |
| A page is updated by `PUT` with the next version number; F1's `update_page` takes `parent_id` to move it | [both] | F1 `update_page` |
| A page is looked up by space and title, `GET /rest/api/content?spaceKey=…&title=…&type=page` | [both] | F1 `get_page_by_title` |
| v1 endpoints with a v2 equivalent were deprecated (CHANGE-864, RFC-19), with removal once planned for 2024-12-02; the current list is not known | [Cloud] [unverified] | [RFC-19](https://community.developer.atlassian.com/t/rfc-19-deprecation-of-confluence-cloud-rest-api-v1-endpoints/71752), [timeline](https://community.developer.atlassian.com/t/update-to-confluence-v1-api-deprecation-timeline/79687) |
| Folders: v2 `POST /folders` with `spaceId`, `title` and `parentId`, `GET /folders/{id}/direct-children` | [Cloud] | F3 `api_v2.py` |
| v2 `POST /pages` refuses a folder as `parentId` with a 500; a page is made elsewhere and moved under the folder (CONFCLOUD-79677, open as far as seen) | [Cloud] [unverified] | [CONFCLOUD-79677](https://jira.atlassian.com/browse/CONFCLOUD-79677), [thread](https://community.developer.atlassian.com/t/missing-support-to-create-a-page-in-a-folder-via-rest-api/84850) |
| DC has no folders | [DC] [unverified] | no source states it; F3 says folders need v2, which DC lacks |

### 2.4 Files on a page

Confluence's term is `attachment`; it appears below only in paths and code.

| Fact | Tag | Source |
| --- | --- | --- |
| A file is sent as `multipart/form-data` in a field named `file`, with optional `comment` and `minorEdit` fields | [both] | F1, F2, F3; DC [v900 reference](https://developer.atlassian.com/server/confluence/rest/v900/api-group-attachments) [unverified] |
| Every upload carries `X-Atlassian-Token: no-check`; `nocheck` is the older spelling | [both] | F1, F2 `std/confluence.py`, F3 |
| All three clients list the page's files first, then send a new version to `POST …/child/attachment/{id}/data` where the name exists, and a new file to `POST …/child/attachment` where it does not | [both] | F1 `attach_content`, F2 `publisher.py`, F3 `api_base.py` |
| What `POST …/child/attachment` answers for a name already on the page | [both] [unverified] | no source read gives it |
| `PUT …/child/attachment`, create or update: F1 says Server does not take it, and that Server and DC take only `POST` on the `data` path | [DC] | F1, code comment |
| Whether Cloud takes `PUT …/child/attachment` | [Cloud] [unverified] | no source read |
| v2 has no endpoint that uploads a file; v1's `POST` stays | [Cloud] [unverified] | [CONFCLOUD-77196](https://jira.atlassian.com/browse/CONFCLOUD-77196) |
| A 404 on upload can mean the file passes the site's size limit; a 403, that files are disabled or not permitted | [both] | F1, docstring |
| A new file can answer 503 on Cloud and still be half made; F2 looks again and retries through `/data` | [Cloud] | F2 `publisher.py` |
| A site may refuse file types, by setting or by an app | [both] | F2 |
| Confluence keeps a file's last path element only | [both] | F1 |
| Uploading a file does not put it in the page's body | [both] [unverified] | [KB](https://support.atlassian.com/confluence/kb/using-the-confluence-rest-api-to-upload-an-attachment-to-one-or-more-pages) |
| The site's largest file is an administrator's setting, `Attachment maximum size`, 104,857,600 bytes by default, with no documented ceiling on Cloud | [both] [unverified] | [Cloud](https://support.atlassian.com/confluence-cloud/docs/configure-attachment-size), [DC](https://confluence.atlassian.com/doc/configuring-attachment-size) |

### 2.5 Storage format

| Fact | Tag | Source |
| --- | --- | --- |
| A body is well-formed XML, Confluence's XHTML-based storage format; a character XML refuses breaks the page's loading | [both] [unverified] | [storage format](https://confluence.atlassian.com/doc/confluence-storage-format-790796544.html), [KB](https://support.atlassian.com/confluence/kb/fix-page-loading-errors-caused-by-invalid-characters-in-confluence-cloud/) |
| A page link is `<ac:link><ri:page ri:content-title="…" ri:space-key="…"/></ac:link>`, `ri:space-key` optional within a space | [both] [unverified] | storage format |
| A link to a page by title not yet created saves unresolved; whether it resolves once the page exists | [both] [unverified] | #611, item 2 |
| A picture on its page is `<ac:image><ri:attachment ri:filename="…"/></ac:image>` | [both] [unverified] | storage format |
| A link to a file is `<ac:link><ri:attachment ri:filename="…"/></ac:link>`, its text in `<ac:plain-text-link-body><![CDATA[…]]></ac:plain-text-link-body>` or `<ac:link-body>` | [both] [unverified] | storage format |
| A link to an anchor is `<ac:link ac:anchor="…">`, with `ri:page` for another page; the anchor itself is the `anchor` macro, `<ac:structured-macro ac:name="anchor">` | [both] [unverified]; the macro's form unconfirmed | storage format |
| Whether named HTML entities such as `&nbsp;` are taken; XML's five and numeric references are | [both] [unverified] | no source read |
| A title is at most 255 characters: F2 cites Confluence's `AbstractPage::isValidTitleLength` | [both] | F2 `std/confluence.py` |
| A title is unique within a space; F1 and F3 find a page by title alone | [both] [unverified] | [K15t](https://help.k15t.com/scroll-docs/4.0/cloud/how-can-i-have-duplicate-page-titles-within-the-sa); F1, F3 |

### 2.6 Limits and pacing

| Fact | Tag | Source |
| --- | --- | --- |
| A REST request body is at most 5,242,880 bytes, refused with 413 *"Requests for this resource can be at most 5242880 bytes"*, raised by `-Datlassian.rest.request.maxsize` and a restart of every node | [DC] [unverified] | [KB](https://support.atlassian.com/confluence/kb/rest-call-gives-request-too-large-requests-for-this-resource-can-be-at-most-5242880-bytes-error/) |
| No cap is documented, and two issues report one of 5 MB, 5,242,880 bytes (CONFCLOUD-73881, CONFCLOUD-82042) | [Cloud] [unverified] | [CONFCLOUD-73881](https://jira.atlassian.com/browse/CONFCLOUD-73881), [CONFCLOUD-82042](https://jira.atlassian.com/browse/CONFCLOUD-82042) |
| Whether the cap reaches a multipart file, or only the size setting of §2.4 does | [both] [unverified] | none |
| Over its limit a client gets 429 with `Retry-After` in seconds; `X-RateLimit-Reset` is an ISO 8601 time; `X-RateLimit-NearLimit` and `RateLimit-Reason` are sent; `Beta-Retry-After` and `Beta-RateLimit-*` headers precede a points-per-hour quota | [Cloud] [unverified] | [rate limiting](https://developer.atlassian.com/cloud/confluence/rate-limiting/) |
| Rate limiting since 7.3: 429 with `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-FillRate` and `X-RateLimit-Interval-Seconds` | [DC] [unverified] | [adjusting your code](https://confluence.atlassian.com/doc/adjusting-your-code-for-rate-limiting-992679008.html), [7.3](https://confluence.atlassian.com/doc/preparing-for-confluence-7-3-983794011.html) |
| F2 honours `Retry-After` as seconds or an HTTP date, and logs a `Deprecation` header | [both] | F2 `rest.py` |

### 2.7 Editors

| Fact | Tag | Source |
| --- | --- | --- |
| F1 and F2 set `metadata.properties.editor` to `v2` on a v1 create to get the new editor | [Cloud] | F1 `create_page(editor=…)`, F2 |
| A page made through v2 lands in the new editor | [Cloud] [unverified] | [thread](https://community.developer.atlassian.com/t/confluence-rest-api-v2-struggling-to-create-a-page-with-the-new-editor/75235) |
| Changing a title through the API moves a legacy page to the new editor (CONFCLOUD-71294); restoring a version moves one back (CONFCLOUD-69830) | [Cloud] [unverified] | [CONFCLOUD-71294](https://jira.atlassian.com/browse/CONFCLOUD-71294), [CONFCLOUD-69830](https://jira.atlassian.com/browse/CONFCLOUD-69830) |
| CONFCLOUD-66773, cited by the map for anchor links on Cloud, was found by no search | [Cloud] [unverified] | none |

### 2.8 Confluence's own importers

| Fact | Tag | Source |
| --- | --- | --- |
| The HTML import takes a zip of one folder of `.html` files: the folder becomes a new space, each file a page, its media taken from a folder of the same name, media it does not take left out; it needs create-space rights | [Cloud] [unverified] | [import from HTML](https://support.atlassian.com/confluence-cloud/docs/import-data-from-html-to-confluence/), [FAQ](https://support.atlassian.com/confluence-cloud/docs/faq-import-data-from-html-to-confluence/) |
| The Word import takes 20 MB a file in one community report; the map's 100 MB and bulk figures were not found | [Cloud] [unverified] | [thread](https://answers.atlassian.com/t5/Confluence-questions/What-is-the-maximum-length-of-a-docx-file-for-uploading/qaq-p/2673161) |
| The Office Connector's Word import takes 20 MB uncompressed, raised by `confluence.word.import.maxsize` | [DC] [unverified] | [import a Word document](https://confluence.atlassian.com/doc/import-a-word-document-into-confluence-170493136.html) |
| The XML site and space export reaches end of life on 2026-12-01, announced 2026-06-01; XML import is not affected | [Cloud] [unverified] | [KB](https://support.atlassian.com/confluence/kb/xml-export-end-of-life-confluence-cloud/) |

### 2.9 Clients that load storage format

| Client | What it does | Source |
| --- | --- | --- |
| atlassian-python-api | `create_page(space, title, body, parent_id, representation="storage", editor)`, `update_page`, `get_page_by_title(space_key, title)`, `attach_file` and `attach_content` (list, then `POST` new or `/data`), `create_space(space_key, space_name)`; Server and Cloud classes apart | F1 |
| sphinxcontrib-confluencebuilder | sends pages and their files over v1 or v2, scoped tokens, `Retry-After` pacing, the editor property | F2 |
| md2conf | Markdown to storage format; v2 for pages, v1 for files, folders over v2; an `index.md` becomes its folder's parent page; Basic or Bearer | F3 |
| mark | Markdown to Confluence; parents named in a header or taken from the path, missing parents made, files listed per page | F4 |
| confluence-publisher (AsciiDoc) and Appfire's Confluence CLI | not read | [unverified] |

None of them reads HTML as it is: each converts its own source to storage format.

---

## 3. Options A to D

Four routes, each sourced below: A from #604's Destination, B from ruling 2 and #608, C from #604's Out of
scope, D from #607. **B and D are ruled out** by the operator (#604, rulings 1 and 2), and their rows are kept
as research only.

- **A. Stage 6b draws a Confluence rendering in storage format beside the HTML tree, with a page list; a loader
  of the operator's own sends it** (#604, Destination 2 and 4).
- **B. An uploader in the jar or the control panel sends the tree.** Ruled out: Vespera never sends the tree
  itself (ruling 2, #608).
- **C. The operator loads the HTML tree as it is, through Confluence's own importers.** Out of scope on the map
  (#604, Out of scope: built-in importers).
- **D. A converter of the operator's own, outside the repository, turns the HTML tree into storage format.**
  Ruled out: the rendering belongs inside the project (ruling 1, #607).

| | A, drawn inside | B, uploader (ruled out) | C, built-in importers | D, outside converter (ruled out) |
| --- | --- | --- | --- | --- |
| **Touches** | `deliverable` only (#604, Notes; ADR-274 §4, §5); amends ADR-101 and ADR-102 (#607); README gains the loader's contract | `deliverable` and a new client, credentials and a network connection; among them ADR-195 §2, ADR-197 §6, ADR-202 §3, ADR-047, ADR-084, ADR-291 §8, ADR-292 §2 (#608) | nothing of ours | nothing of ours |
| **Costs** | a second renderer, a page list, a parity test (#604, Destination 3); a loader the operator writes | A's cost, plus a client to keep current with v1 and v2 (§2.3), token handling (§2.2) and pacing (§2.6); no agent can test it against a space | the operator's time per load | the converter, kept by the operator in step with every layout change (#571, #573) |
| **Cannot carry** | code sets, not yet specified (#573); otherwise nothing the HTML carries, so far as §2 reads; a copy past the site's file size (§2.4) is skipped by the loader (ruling 5) | as A | nesting: one folder of pages under a new space, so no root, parents or children; copies whose media it does not take (§2.8); updates | as A, but with nothing our side tests |
| **Risks** | the two renderings drifting; citations reaching the page but not the entry on Cloud (#604, Risks) [unverified]; title links and title collisions (§1.6, §2.5); the editor rewriting a body (§2.7); the request cap (§2.6) | A's, plus a credential and an egress path in the jar | an `index.html` reported to load empty (#604); every load a new space | breaking silently on a layout change (#607) |
| **Reopens** | ADR-101, ADR-102, ADR-002, ADR-023; ADR-274 Decided here 1 (#607) | ADR-195, ADR-197, ADR-202, ADR-047, ADR-084, ADR-098, ADR-040, ADR-276, ADR-278, ADR-285, ADR-291, ADR-292, ADR-046 (#608) | none | none |

---

## 4. The ticket's facts, checked

To-do 4 of #605: each fact the ticket starts from, against what §2 could reach. None was confirmed from an
Atlassian page read whole; each stays for #611 to measure where it bears on a build.

| The ticket's fact | Found | Tag | §2 |
| --- | --- | --- | --- |
| Request cap 5,242,880 bytes by default, raised by `atlassian.rest.request.maxsize` [DC] | as stated; the property is a JVM system property, set on every node with a restart | [DC] [unverified] | §2.6 |
| No documented body cap [Cloud, unverified] | **corrected**: none documented, but two issues report one of 5,242,880 bytes. #571 still waits on #611 | [Cloud] [unverified] | §2.6 |
| API tokens last at most 365 days [Cloud] | as stated: 1 to 365 days, chosen at creation | [Cloud] [unverified] | §2.2 |
| A scoped token calls `https://api.atlassian.com/ex/confluence/<cloudId>/wiki/…`, not the site URL [Cloud] | the base confirmed in F2 and F3, the cloudId's source (`_edge/tenant_info`) in F2 alone; **whether `/wiki/` follows is not settled** | [Cloud]; `/wiki/` [unverified] | §2.2 |
| `X-RateLimit-Reset` [Cloud], `Retry-After` [DC] | **widened**: Cloud sends `Retry-After` too, and DC the `X-RateLimit-*` family, since 7.3 | [both] [unverified] | §2.6 |
| v1 `POST child/attachment` refuses a name that exists | **not confirmed**: no source read gives the answer; the clients read sidestep it by listing first | [both] [unverified] | §2.4 |
| updates go through `PUT child/attachment` or `POST …/child/attachment/{id}/data` | `/data` confirmed by F1 to F3; **`PUT` is not taken on DC** by F1's account, and unread on Cloud | `/data` [both]; `PUT` [DC] by F1, [Cloud] [unverified] | §2.4 |
| v2 has no upload endpoint [both] | **retagged**: on Cloud, as stated; DC is reported to have no v2 at all | [Cloud] [unverified]; DC's half [DC] [unverified] | §2.1, §2.4 |
| v2 cannot create a page directly under a folder [Cloud, unverified] | as stated, a 500, CONFCLOUD-79677 | [Cloud] [unverified] | §2.3 |
| DC has no folders [DC] | stated by no source read | [DC] [unverified] | §2.3 |
| Word import 100 MB a file, bulk 30 files / 50 MB / 10 MB, 5 MB body [Cloud] (#604) | **not found**; one report gives 20 MB | [Cloud] [unverified] | §2.8 |
| DC's Office Connector takes 20 MB [DC] (#604) | as stated, raised by `confluence.word.import.maxsize` | [DC] [unverified] | §2.8 |
| Cloud's XML space export ends 2026-12-01 (#604) | as stated | [Cloud] [unverified] | §2.8 |
| DC dates 2026-03-30, 2028-03-30, 2029-03-28 (#604, ruling 3) | as stated, in partner summaries; one source gives 2029-03-30 | [DC] [unverified] | §2.1 |
| CONFCLOUD-66773 is "Won't Do" [Cloud] (#604, #611) | **the issue was found by no search**; its number and status are open | [Cloud] [unverified] | §2.7 |
| Title length 255 is the common figure [unverified] (#611) | F2 cites Confluence's own check at 255 | [both] | §2.5 |

**Left for #611**, beyond its own eight questions: what a `POST` of an existing file name answers, and whether
Cloud takes `PUT` on files (§2.4); whether the request cap reaches a multipart file (§2.6); whether `/wiki/`
follows the cloudId (§2.2); whether a title must be unique in a space (§2.5); and CONFCLOUD-66773's number.

---

## 5. Measured on a Cloud site (#611)

The operator ran a probe script on 2026-10-21 against their own Confluence Cloud site, in their personal space,
with synthetic pages only, all under one parent page and deleted afterwards. The script sent v2
`POST /wiki/api/v2/pages` with `representation: storage`, v1 `POST /wiki/rest/api/content/{id}/child/attachment`,
and read each page back as `storage` and `view`. No Data Center site was reached, so every [DC] fact above
stays as tagged. Each finding below holds for **[Cloud]**, on that one site, on that day; a site admin may set
caps differently.

| # | Question | Measured |
| --- | --- | --- |
| 1 | Citation links | Both `<ac:link ac:anchor="document-1">` alone and with `<ri:page>` naming the same page save as sent and render as `<a href="#document-1">`. The anchor macro renders a `<span id="document-1">`, beside a second id prefixed with the page title. The `view` form puts the target on the page; whether a click in the browser lands on it was not checked. |
| 2 | Forward page links | A title link to a page not yet created renders as a `createlink`; once the page exists, the same link renders as a link to it, with and without `ri:space-key`. |
| 3 | File names | `~`, `é`, `日本語` and a space are each kept as sent, and `<ri:attachment ri:filename>` resolves each, percent-encoded in the link. |
| 3 | Sending a name that exists | v1 `POST …/child/attachment` answers **400**, "Cannot add a new attachment with same file name as an existing attachment". |
| 4 | Request cap | A page body of 10 MiB is taken, as is a 10 MiB file in a multipart request. **No cap was met at 5 MiB** on either; the ceiling above 10 MiB was not measured. |
| 5 | Titles | 255 characters are taken; **256 are refused** (400). Every character tried is taken: `: / \ \| ? # % & < > " ' * ~ + [ ] { }`, `é`, `日`. A second page with a title already in the space is **refused** (400, "A page already exists with the same TITLE in this space"). |
| 6 | Editor and attributes | `class` on a `<p>` survives; `title` on a `<p>` is **dropped**; `title` and `class` on an `<a>` survive, as does a raw `<a href="#document-1">`. The page has no `editor` content property (v1 answers 404), and v2 gives no `subtype`. |
| 7 | A link to a file never sent | Renders as `<a class="unresolved" href="#">` with the file name as text. |
| 8 | Entities | `&nbsp;`, `&mdash;` and the five XML entities are taken. **Storage is normalised on save**: `&#160;` is stored as `&nbsp;`, `&#x2014;` as `&mdash;`, `&apos;` as `'`. A body read back is not byte-equal to the body sent. |

**Facts above this measurement settles.** §4's "v1 `POST child/attachment` refuses a name that exists": confirmed
[Cloud]. "Whether a title must be unique in a space": confirmed [Cloud]. "Title length 255": confirmed [Cloud].
"Whether the request cap reaches a multipart file": no cap up to 10 MiB [Cloud]. The 5,242,880-byte figure (§2.6)
did not hold on this site. Left open: whether Cloud takes `PUT` on files, whether `/wiki/` follows the cloudId,
CONFCLOUD-66773's number, and anything on Data Center.
