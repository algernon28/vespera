# ADR-415 — A topic is a page carrying its overview, its subtopics are pages beneath it, and a see-also link lands on a group's page

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822) as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) records it: that a topic's overview is its page in the HTML tree and sits above the table of its clusters on Confluence (§2, §7); that the tree is at most topic, subtopic, cluster (§3); that every other topic a survivor fits carries a link to it, the survivor copied once (§5); that "seed partition" retires (§6); and that published pages follow [ADR-328](0328-the-confluence-rendering-is-one-tree-updated-in-place-by-each-publish-its-pages-keyed-and-titled-by-their-place-with-no-run-id-and-a-page-it-no-longer-has-moved-to-the-trash.md) (§7). Under the operator's delegation, in [#833](https://github.com/algernon28/vespera/issues/833), as ADR-396 §8 gives it the three renderings at three levels, a topic's folder and what it is named from; and of what [ADR-402](0402-a-topics-overview-cites-its-written-clusters-by-number-in-every-sentence-and-a-citation-fault-is-asked-once-more.md) §5, [ADR-406](0406-a-topic-of-300-survivors-or-more-is-divided-into-subtopics-its-own-communities-and-its-clusters-are-found-within-each.md) and [ADR-409](0409-topics-placement-names-overviews-and-clusters-within-topics-are-kept-in-new-tables-keyed-by-a-topics-first-passage.md) §10 leave to #833: how a topic, a subtopic, an overview, its citations, its placeholder and disclosure, and a see-also link are drawn on each rendering, a topic's folder name past ADR-409 §10, and the names of `documents.csv`'s columns.
- **Amends**: [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), its *"The path carries the order"*: a topic's folder is named from its recorded name, a crowded topic's clusters lie a level down in a folder for each subtopic, a cluster's folder carries its place in its topic's one sequence, and every slug folds accented letters to their base letter before the rest of its rule (§1, §3); and its `documents.csv` columns, `seed_partition` and `partition_order` renamed `topic` and `topic_order`, `subtopic` and `subtopic_order` added (§6).
- **Amends**: ADR-328 §1 and §3, the titles and keys: a subtopic's page is titled `TEXT (P.S)` and keyed `P.S`, and a crowded topic's cluster pages `TEXT (P.S.C)` and `TEXT (P.S.C page K)`, keyed `P.S.C` and `P.S.C.K`, a key holding at most four numbers (§7).
- **Amends**: ADR-396 §11, its *"The first publish after the top level becomes topics trashes each seed partition's page and adds each topic's"*: a page is its place, so the topic drawn at a place is drawn on the page that place's seed partition had, retitled and updated in place, and only a place the rendering no longer has is moved to the trash (§7).
- **Amends**: ADR-409 §10, which held until this record: every item of it but `winning_seed`'s and the numbering of a crowded topic's clusters in one sequence is replaced by §1 to §8. And [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md) §4, the index's paragraph, whose sentence on `seed_partition` gives way to §6's, in English and in Italian.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §2.7 and [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1, the bounds of 69 and 146 characters: they hold of a topic with no subtopics, and are 46 characters less for the pages of a crowded topic (§3).
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §9, `search-index.js`'s *"its seed partition's name"*: each cluster's row holds its topic's name as `topic` and its subtopic's as `subtopic` (§8).
- **Keeps**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md), a cluster's folder holding its page and its copies and nothing else, each survivor copied once; ADR-328 §2 and §4 to §7, a page is its place; [ADR-309](0309-a-citation-on-a-confluence-page-is-an-anchor-link-naming-the-page-its-entry-is-on-and-where-it-cannot-link-it-is-its-number-as-plain-text.md), a link names its page by its title; [ADR-303](0303-a-clusters-page-past-the-profiles-byte-ceiling-is-split-at-an-entrys-end-into-pages-and-every-drawing-writes-the-pages-sizes.md), a cluster's pages; ADR-402's wording of the placeholders and the disclosure; ADR-409 §7, what the 6a gate shows; [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) and ADR-409 §13, one topic at a time.
- **Settles**: [#833](https://github.com/algernon28/vespera/issues/833).

## Context

ADR-409 §10 has the three renderings draw two levels until #833: a topic where a seed partition stood, its folder named from its recorded name, its heading on the index its name and no page of its own in the HTML tree; a crowded topic's clusters in one sequence with no subtopic drawn; `documents.csv`'s header unchanged, `seed_partition` holding the topic's name; no overview and no link. That is what `Deliverable`, `ConfluencePages` and `WebsiteDrawing` draw.

What the renderings have to draw from is recorded under the runs a drawing reads: the topics and their order and names, each subtopic's name and order, each cluster's topic, subtopic and place (`arranged_cluster`, `topic_name`, `subtopic_name`); each topic's overview, the numbers it was written over, or its fault (`topic_overview`, `topic_overview_source`, `topic_overview_fault`, ADR-402 §7); and each scored document's see-also rows (`see_also`, ADR-400 §2). Nothing reads the overview's rows or the see-also rows yet.

Four records fix what the drawing must hold to. ADR-396 §7: the overview is the topic's page in the HTML tree, and on Confluence sits above the table of its clusters. ADR-402 §2: a cluster's number is its place among its topic's clusters through the subtopic level, the number the overview cites and a reader of the topic's page finds it at. ADR-406 §2: every cluster of a crowded topic lies one level down, so that the topic's page lists subtopics alone. ADR-328 §3 and §4: a Confluence page is keyed by its place, and a page is its place.

## Decision

### 1. A topic's folder, and the slug

**A topic's folder is its order padded to the width of the count of topics drawn, a hyphen, and its recorded name as a slug**, as ADR-409 §10 has it; a subtopic's is its `subtopic_order` padded to the width of its topic's subtopic count, a hyphen, and its recorded name as a slug. The order prefix is what keeps two folders apart: no two siblings share it, so two names that slug alike, as two fallback names may (ADR-407 §7), give two folders, and no suffix is added.

**A slug folds an accented letter to its base letter first**: the text is decomposed (Unicode NFD) and its combining marks removed, then lower-cased, every run of what is not `a` to `z` or `0` to `9` made one hyphen, cut to 40 characters and trimmed of hyphens as before (ADR-112, ADR-274 §1.2). A name is written in the deliverable language (ADR-407), and Italian's *Qualità dell'acqua* slugged `qualit-dell-acqua` loses a letter where it gains a hyphen; folded it is `qualita-dell-acqua`. The rule is one, for every slug of the tree: a cluster's label slugs the same way. A text with no letter or digit of `a` to `z` and `0` to `9` once folded slugs to the empty text, and its folder is its order and a hyphen, as before.

### 2. A topic's page

**A topic's page is `index.html` in its folder.** It holds, in this order:

1. **Its name**, as the page's `<h1>`.
2. **Its overview**, where one is recorded: its writing drawn by the subset of Markdown a cluster's writing is (`Prose`, ADR-274 §1.3), each citation `[n]` drawn as a link reading `[n]` to the first page of the cluster at place `n` among the topic's clusters (§3), and a number that is no cluster's place drawn as the plain text `[n]`. The link is drawn whether or not that cluster has writing: ADR-402 §2 lets an overview cite only a written cluster, and a cluster's page is written either way, so the link lands inside the tree in every case and the drawing does not check the writing a second time. Where the numbers the overview was written over are fewer than the topic's clusters, ADR-402 §5's disclosure stands beneath it, in italics, its two numbers those two counts.
3. **Or, where none is recorded, ADR-402 §5's sentence for its case**, in italics where the overview would be: one of the four faults by the kind recorded (`OverviewUnwritten.of`); *nothing written over any of its groups* where no cluster of the topic has a synthesis doc; *not reached* otherwise. Each in the deliverable language, from `TopicOverviewWording`.
4. **Its table.** For a topic with no subtopics, a row for each of its clusters in its one sequence: the cluster's number, its label, linking to its first page where writing exists as the index's row does (ADR-111), its count of documents, and its title or the index's *nothing was written over it*. For a crowded topic, a row for each subtopic in `subtopic_order`, and no cluster: its name linking to its page, its count of clusters and its count of documents (ADR-406 §2).
5. **Its see-also list**, where it has one (§5).

**The page is written once its last cluster is drawn**, as Confluence's topic page is now: its overview and its rows are known by then, and what is held is one topic's rows (ADR-409 §13).

### 3. A crowded topic's subtopics

**A crowded topic's subtopic has a folder and a page in it**, `<topic>/<subtopic>/index.html`, and its clusters' folders lie in it: `<topic>/<subtopic>/<cluster>/`. A topic with no subtopics keeps its clusters' folders directly in its own.

**A subtopic's page** holds its name as its `<h1>`; one line saying which topic it is part of, the topic's name linking to the topic's page; and its table, a row for each of its clusters in `cluster_order`, as a topic's table of clusters is drawn (§2). A subtopic has no overview: ADR-402 writes one for a topic alone, and a subtopic's clusters have their writing.

**A cluster's number is its place in its topic's one sequence**, subtopic by subtopic in `subtopic_order` and within each in `cluster_order`, counted from 1, whether the topic is crowded or not: the number its folder carries, padded to the width of the topic's cluster count; the number its row shows on the topic's or the subtopic's page; the number the overview cites (ADR-402 §2); and the number `documents.csv` gives it in a crowded topic (ADR-409 §10). So the second subtopic's first cluster of a topic of five clusters, three in the first subtopic, is `3`, in a folder named `3-…` in the second subtopic's folder.

**A crowded topic's pages lie one folder deeper**, by at most 46 characters: four digits of order, a hyphen, 40 of slug, and a separator. ADR-274 §2.7's 69 characters on the working directory's path and ADR-276 §1's 146 on a named root's hold of every file of a topic with no subtopics, and are 46 less for the pages of a crowded topic; past them the JDK still writes the file (ADR-103), as ADR-280 has it of a copy.

### 4. The index

**The index heads each topic with its name linking to its page.** Under a topic with no subtopics stands its table of clusters as now. Under a crowded topic stands, for each subtopic in `subtopic_order`, its name as an `<h3>` linking to its page, and under it its table of clusters, each row linking through the subtopic's folder. The index's tables keep their three columns, group, documents and title: it is the listing of the whole tree (ADR-103), and the numbers are the topic's page's.

### 5. See-also links

**A topic's page lists, under the heading *See also*, an `<h2>` after its table, each survivor of the arrangement placed under another topic that fits this one** (ADR-400 §2): a `see_also` row naming this topic, under the scoring run the arrangement reads, of an occurrence that is a survivor with a cluster in the arrangement. A row of a scored document that is no survivor, and a row naming a topic no survivor is placed under, which has no folder (ADR-396 §2), draw nothing; nor does a line whose topic is not among the topics drawn or whose cluster is not among that topic's clusters, which is skipped without a word, there being no page for it to land on. One sentence stands under the heading; then one line a survivor: its path linking to the first page of its cluster, followed by the name of the topic it is placed under, in brackets. Its copy is not written again (ADR-280).

**The lines go by the topic each survivor is placed under, in topic order, and within one topic in occurrence order**, the order its index reads `see_also` in (ADR-409 §2); so a drawing holds one topic's clusters at a time to name where each line lands. No list is capped: ADR-400 bounds a survivor's links at six, and a topic's list is as long as the survivors that fit it.

**A link lands on its cluster's first page, not on its entry.** An entry's number and the page holding it come from its cluster's writing and page breaks (ADR-133, ADR-303), known only once that cluster is drawn, and a topic's page is written before the topics after it are drawn; the cluster's first page lists the survivor and links its copy, one step further. A topic with no see-also row draws no heading.

### 6. `documents.csv`, and the index's paragraph

**The header is** `occurrence_id,path,content_hash,winning_seed,relevance_score,topic,subtopic,cluster,topic_order,subtopic_order,cluster_order,copy,copy_outcome`. `topic` holds the topic's recorded name where `seed_partition` did, and `topic_order` its order where `partition_order` did; `subtopic` holds the subtopic's recorded name and `subtopic_order` its order, both empty for a survivor of a topic with no subtopics, the empty cell holding its place as `winning_seed`'s does (ADR-409 §10). `cluster` and `cluster_order` hold what ADR-409 §10 has them hold. Names go before orders, as they did.

**The index's paragraph's third sentence**, which ADR-409 §10 gave, reads:

> The topic column is not a path: it holds the name of the topic each document is placed under, and the subtopic column, where that topic is divided, the name of the subtopic the document lies in. Each numbered folder beside this page is a topic's: it holds the topic's page and the folders of its groups, or, where the topic is divided, a folder for each subtopic holding the subtopic's page and the folders of its groups.

and in Italian:

> La colonna topic non è un percorso: contiene il nome dell'argomento sotto cui è collocato ogni documento, e la colonna subtopic, dove quell'argomento è diviso, il nome del sottoargomento in cui si trova il documento. Ogni cartella numerata accanto a questa pagina è di un argomento: contiene la pagina dell'argomento e le cartelle dei suoi gruppi, oppure, dove l'argomento è diviso, una cartella per ogni sottoargomento con la pagina del sottoargomento e le cartelle dei suoi gruppi.

Every other sentence of the paragraph stands. A column is named as the header spells it, and the prose says *group* (*gruppo*) for a cluster (ADR-122).

### 7. The Confluence rendering

**Its pages follow the HTML tree's**, each keyed and titled by its place (ADR-328 §1, §3, ADR-378 §1):

| Page | Key | Title | Parent |
| --- | --- | --- | --- |
| a topic's | `P` | `NAME (P · code)` | the root page |
| a crowded topic's subtopic's | `P.S` | `NAME (P.S · code)` | its topic's |
| a cluster's, its topic not crowded | `P.C`, page K `P.C.K` | as ADR-328 §1 | its topic's |
| a cluster's, its topic crowded | `P.S.C`, page K `P.S.C.K` | `TEXT (P.S.C · code)`, `TEXT (P.S.C page K · code)` | its subtopic's |

`S` is the subtopic's `subtopic_order` and `C` the cluster's place in its topic's one sequence (§3), padded in titles as ADR-310 §2 pads a place and unpadded in keys (ADR-328 §3): `P` to the width of the count of topics, `S` to that of its topic's count of subtopics, and `C` to that of its topic's count of clusters, as the folders are (§1, §3). **A key holds at most four numbers**, and the publish reads one of four as it reads one of three; it removes deepest first by the count of numbers (ADR-328 §5).

**Titles stay apart, and a page is its place.** A suffix still names a place, and two titles of one rendering alike have one suffix and one key (ADR-328 §7). One key may be of another kind in another rendering, `1.2` a cluster's page where topic 1 had no subtopics and a subtopic's where it has, `1.2.3` page 3 of a split cluster or a cluster in a subtopic: as ADR-328 §4 has a page carry whatever has its place now, such a page is retitled and its body sent, its parent being the same key's in both. No title of one key is another key's.

**A topic's page** carries, in this order: its overview, each citation `[n]` an `ac:link` to the title of the page of the cluster at place `n`, its body reading `[n]`, with no anchor; or ADR-402 §5's sentence in italics; the disclosure beneath an overview where §2 has it; its table, as §2's, each label or subtopic's name an `ac:link` to its page by title (ADR-309); and its see-also list, each path an `ac:link` to the title of its cluster's first page, as `ConfluencePages.link` writes one. **A subtopic's page** carries §3's line, the topic's name an `ac:link` to the topic's page, and its table of clusters. **The root page** follows the index (§4): each topic's name an `ac:link` to its page as an `<h2>`, then its table, or each subtopic's name as an `<h3>` linking its page and its table. The link reads the topic's or subtopic's name alone, as the index's heading does, where it read the page's whole title before; the title, place and code, stays the link's target.

**The first publish of this rendering** sends each place's page its new title and body. A topic is drawn at the place a seed partition, or the interim topic of ADR-409 §10, had, and its page is that page, retitled and updated in place; where a topic is crowded, the pages at its `P.C` places become its subtopics' at `P.S` or are moved to the trash where it has fewer subtopics than it had clusters, and its clusters' pages at `P.S.C` are created. Nothing matches a page by a name or a seed: a name is never a key (ADR-328's Alternatives refused).

### 8. The website rendering

**It follows the HTML tree's shape** (ADR-333 §3): a topic's page at `<topic>/index.html`, a subtopic's at `<topic>/<subtopic>/index.html`, a crowded topic's cluster pages a folder deeper, each relative link drawn from its own depth; their content §2's, §3's and §5's, through `WebsitePage`'s opening and closing; the index as §4's. Each page it adds is listed in `files.json` as a page.

**`search-index.js` keeps one row a cluster**, its `partition` field named `topic` and holding the topic's name, and a `subtopic` field after it holding the subtopic's name, or the empty text where the topic has none. The script searches both, and shows both after a group's title where they are not empty. A topic's overview is not searched (*What this does not decide*).

### 9. The 6a gate

**Nothing is added.** ADR-409 §7 has `arrangement.html` show each topic and subtopic under its recorded name, read-only, a fallback marked with why, the operator approving the arrangement of the clusters alone; no overview exists before 6b. That is #833's *arrangement page at the gate*, and it stands.

### 10. The fixed words

Each in the deliverable language (ADR-321 §4), saying *group* (*gruppo*) for a cluster, *topic* (*argomento*) for a topic and *subtopic* (*sottoargomento*) for a subtopic (ADR-406, ADR-402 §5):

| Where | English | Italian |
| --- | --- | --- |
| the heads of a topic's or a subtopic's table of clusters | No., Group, Documents, Written up as | N., Gruppo, Documenti, Titolo del testo |
| the heads of a crowded topic's table of subtopics | Subtopic, Groups, Documents | Sottoargomento, Gruppi, Documenti |
| a subtopic's page, before the topic's name and a full stop | In the topic | Nell'argomento |
| the see-also heading | See also | Vedi anche |
| the see-also sentence | Placed under another topic, these documents also speak of this one. | Collocati sotto un altro argomento, questi documenti parlano anche di questo. |

### 11. What the renderings are handed

`deliverable` asks `ArrangedSurvivors` three things more, a topic at a time, and holds each answer for one topic (ADR-223 §9):

- **`subtopicsOf(ListedTopic)`**: its subtopics in `subtopic_order`, each a `ListedSubtopic(int subtopic, String name, int subtopicOrder)`, empty for a topic with no subtopics. A subtopic with no name is refused where it is made, as `ListedTopic` refuses a topic with none, and `pipeline` building one for a subtopic with no name recorded under the arrangement run stops the drawing, as it does for a topic.
- **`overviewOf(ListedTopic)`**: a `ListedOverview`, `Written(String writing, int clustersSent)` or `Unwritten(OverviewUnwritten why)`, `pipeline` telling the cases apart from the overview tables and the clusters' rows as ADR-402 §7 has them.
- **`eachPageOfSeeAlso(ListedTopic, Consumer<List<ListedSeeAlso>>)`**: the topic's see-also lines a page of 1,000 at a time in §5's order, each a `ListedSeeAlso(OccurrencePath path, ClusterSlot cluster)`; `deliverable` names the cluster's folder or page from the topics it was handed, the subtopics and clusters of the one topic the slot lies in, and its writing's title.

**The reads are their tables' owners'** (ADR-209 §3): `synthesis` reads a topic's overview row, the count of its sources and its fault under the generation run; `embedding` reads a topic's `see_also` rows a page at a time with each document's winning topic and cluster; `pipeline` filters them by `Verdicts.survivingAmong` and composes the answers. No new statement sorts: each read is of one topic's rows by an index ADR-409 §12 builds, and §5's grouping by topic is a read for each topic in topic order.

### 12. Run ids

**The code that builds this record touches `deliverable`, `publish`'s two drawings and the publish's key, `pipeline`, and `synthesis` and `embedding` for their reads (§11).** By `StageModules` (ADR-058, ADR-411): `embedding` moves seed measurement, embedding scoring, the arrangement and generation, and `synthesis` the arrangement and generation. **Seed measurement, embedding scoring, the arrangement and generation move**: the scoring step is done again from the cached vectors, the arrangement approved again unless `arrangementApproved` holds `auto`, and every cluster and overview asked again under the new generation run. **The walk, byte-level reduction, extraction, content census and content redundancy do not move.** `deliverable` and `publish` move no run by themselves (ADR-274 §5).

### 13. The build, in three steps

1. **The seam and the HTML tree**: §11's answers and reads; §1 to §6 and §10 in `deliverable`; `pipeline` handing the answers. It moves the runs §12 names.
2. **The Confluence rendering and the publish's keys**: §7. It moves no run.
3. **The website rendering and its search**: §8, `website.js` with it. It moves no run.

Each step's tests are red until its step is built, and each step leaves the other renderings as they were drawn.

## Alternatives refused

- **A subtopic drawn as a heading on its topic's page, with no folder or page.** ADR-406 §2 has every cluster of a crowded topic one level down so that the topic's page lists subtopics alone, the page staying at 2 to 14 entries; a heading level would put every cluster back on the topic's page.
- **A cluster numbered within its subtopic.** Two clusters of one topic would share a number, the overview's `[3]` would lead to a cluster a reader finds at another number, and the folders, the overview and `documents.csv` would number one cluster three ways (ADR-402 §2).
- **A see-also link to its survivor's entry, `#document-n` on the page holding it.** Its number and page come from that cluster's writing and page breaks, known once it is drawn; drawing topic pages after every cluster would hold every survivor's place, which ADR-220 refuses, or draw the clusters twice.
- **A see-also list in path order, or in the order of where each survivor lies.** Either sorts a list as long as a topic's links on the heap or in a statement (ADR-218); occurrence order is the index's, and the grouping by topic is a read a topic.
- **A see-also line on the survivor's own cluster page, naming the topics it fits.** The operator had the topic it fits carry the link, so a reader of that topic finds it.
- **A Confluence subtopic's key marked by a letter, `P.sS`**, or a crowded topic's cluster keyed `P.C` under its subtopic's page: ADR-328 §3 has a key of decimal numbers, its parent the key less its last, and the removals go deepest first by their count.
- **A slug that transliterates beyond the combining marks** (`ß` to `ss`, `æ` to `ae`): it needs a table or a library the pom does not carry (ADR-046), for letters a deliverable in English or Italian rarely names.
- **The topic's key in a folder name or in `documents.csv`**: it is a seed's path and a chunk's number (ADR-399 §5), naming a seed a topic is not; the order tells two topics of one name apart.

## Consequences

- **A reader of the HTML tree opens a topic as a page**: its overview, its clusters or subtopics by number, and the survivors of other topics that also speak of it; the index links each topic's and subtopic's page.
- **A crowded topic is three levels deep in every rendering**, and its files 46 characters deeper.
- **`documents.csv` has two columns more and two renamed**: a consumer reading `seed_partition` or `partition_order` reads `topic` or `topic_order`.
- **A deliverable's folder named from an accented label or name changes its name** at the first drawing of this build; Confluence's pages are keyed by place and do not move, and the website's publish removes what it no longer lists (ADR-333 §7).
- **The first publish to Confluence after it** retitles every page and changes every topic's body, and a crowded topic's pages change kind as §7 has it.
- **The runs §12 names move**, the arrangement approved again.
- **ADR-112, ADR-274, ADR-276, ADR-321, ADR-328, ADR-333, ADR-396 and ADR-409 carry a note** at their heads saying what of them no longer holds.
- **`CONTEXT.md`**'s Seed partition entry no longer names `seed_partition` and `partition_order` as columns, and its Confluence rendering entry names a subtopic's page.

## Tests

Each built with the step of §13 that needs it.

| Class | What it holds | Step |
| --- | --- | --- |
| `deliverable.TwoTopicsDrawn` | the fixture every rendering's tests below draw: a crowded topic of two subtopics and three clusters, its overview written over all three; an undivided topic of two clusters, one unwritten, its overview over one; and a survivor of the first linked from the second | — |
| `deliverable.ATopicIsAPageCarryingItsOverviewTest.theTopicsPageCarriesItsOverviewEachCitationLinkingTheCitedGroup` | the crowded topic's page: its name, its overview's sentences, `[1]` to `[3]` each linking its cluster's first page through its subtopic's folder, no disclosure, and its table of two subtopics, each linking its page with its counts (§2, §3) | 1 |
| `…anOverviewOverFewerGroupsSaysSoAndTheTableNumbersEachGroup` | the undivided topic's page: the disclosure *1 of the 2*, in italics, beneath the overview; its table's rows numbered 1 and 2, the second saying nothing was written over it (§2) | 1 |
| `…eachCaseOfNoOverviewIsSaidWhereTheOverviewWouldBe` | for each of `OverviewUnwritten`'s six cases, the topic's page carries `TopicOverviewWording`'s sentence in italics and its table beneath (§2) | 1 |
| `…aSubtopicsPageNamesItsTopicAndNumbersItsGroupsInTheTopicsSequence` | the second subtopic's page: its name, the line linking the topic's page, and its one cluster numbered 3, in a folder `3-…` in the subtopic's folder (§3) | 1 |
| `…theIndexLinksEachTopicsPageAndEachSubtopicsPage` | the index's `<h2>` links each topic's page, its `<h3>`s each subtopic's, and each cluster's row its page through its subtopic's folder (§4) | 1 |
| `…aSurvivorFittingAnotherTopicIsLinkedFromThatTopicsPage` | the undivided topic's page: *See also*, its sentence, and the one survivor's path linking its cluster's first page in the crowded topic, followed by that topic's name; the crowded topic's page has no *See also* (§5) | 1 |
| `…aFolderNameFoldsAccentsAndKeepsTheOrderPrefix` | `Qualità dell'acqua` slugs to `qualita-dell-acqua`, and two topics of one name get two folders by their order (§1) | 1 |
| `deliverable.ACrowdedTopicIsDrawnInOneSequenceTest.theClustersOfTwoSubtopicsAreNumberedInOneSequenceAndToldApart` | as ADR-409 holds it, each subtopic's five clusters now in its own folder, still numbered 01 to 10 over the topic, and each survivor's `subtopic` and `subtopic_order` its subtopic's (§3, §6) | 1 |
| `deliverable.ManifestCsvTest.theHeaderNamesTheTopicAndTheSubtopicAndTheirOrders` | the header, whole, and a survivor of a topic with no subtopics carrying empty `subtopic` and `subtopic_order` cells (§6) | 1 |
| `deliverable.ItalianWordingTest.theCodesItalianForEveryCaseIsTheRecords` | the index's paragraph with §6's Italian sentence, as `ItalianWordingOnRecord` holds it | 1 |
| `deliverable.ItalianWordingTest.theTopicsAndSubtopicsPagesWordsAreTheRecordsItalian` | §10's Italian words, as `ItalianWordingOnRecord` holds them | 1 |
| `pipeline.TopicNamesAreStoredAndShownInvocationTest.theTreeNamesTheTopicsFolderAndHeadingByItsName` | through 6b: the one topic's folder, its page headed by its name, the index's heading linking it, the paragraph with §6's English sentence, and `documents.csv`'s `topic` and `topic_order` the name and 1 (§2, §4, §6) | 1 |
| `confluence.TheConfluenceRenderingDrawsTopicsAndSubtopicsTest.eachPageIsKeyedTitledAndParentedByItsPlace` | the page list's keys, titles and parents, §7's table, for the fixture (§7) | 2 |
| `…aTopicsPageCarriesItsOverviewAboveItsTable` | the crowded topic's body: its overview, each `[n]` an `ac:link` to its cluster's page by title, then its table of subtopics (§7) | 2 |
| `…anOverviewOverFewerGroupsAndASeeAlsoLinkAreDrawnOnTheTopicsPage` | the undivided topic's body: the disclosure, its numbered rows, and the see-also `ac:link` to the crowded topic's cluster's page reading the path (§5, §7) | 2 |
| `…aSubtopicsPageLinksItsTopicAndListsItsGroups` | the second subtopic's body: the line linking the topic's page, and its cluster at 3 (§7) | 2 |
| `website.TheWebsiteDrawsTopicsAndSubtopicsTest.eachTopicsAndSubtopicsPageIsDrawnAndListed` | `website/` holds the topic's and subtopic's pages where the HTML tree does, each listed in `files.json` as a page (§8) | 3 |
| `…aTopicsPageCarriesItsOverviewAndItsLinksAsTheTreesDoes` | the crowded topic's page: its overview's citations and its subtopics' links as the HTML tree's, and the undivided topic's see-also link (§2, §5, §8) | 3 |
| `…theSearchIndexNamesEachGroupsTopicAndSubtopic` | each row's `topic` and `subtopic`, the empty text for the undivided topic's, and no `partition` (§8) | 3 |
| `website.TheSearchIndexHoldsNoExtractedTextTest.theIndexHoldsWhatPagesShowAndNoExtractedText` | a group is named by its topic, an empty subtopic and no `partition` (§8) | 3 |
| `src/test/website/website.test.mjs` | a topic's name, and a subtopic's, find their groups, the subtopic's shown with them (§8) | 3 |
| `embedding.ASeeAlsoLineIsReadByTheTopicItFitsAndTheTopicItIsPlacedUnderTest.eachDocumentFittingATopicIsReadUnderTheTopicItIsPlacedUnderWithItsCluster` | over real `see_also`, `winning_topic` and `cluster_member` rows: the documents fitting one topic and placed under another, with their subtopic and cluster, in occurrence order, under the run asked about alone, none no clustering placed, and the topic fitted and the topic placed under never read the other way round (§5, §11) | 1 |
| `confluence.APageKeyedByFourNumbersIsOfTheTreeTest.aPageKeyedByFourNumbersIsMovedToTheTrashAndOneOfFiveIsLeftAlone` | a page keyed `en:1.2.3.2` is found as the tree's and moved to the trash where the rendering no longer has it; one keyed by five numbers is left alone (§7) | 2 |
| `deliverable.TheTreeIsHtmlTest.escapesEveryValueInTextAndInAttributes` | the topic's page shows the topic's name, the label and the title escaped, beside the group's page (§2) | 1 |
| `deliverable.DeliverablePicturesTest.noCharacterOfADocumentsNameOrALabelReachesAPicturesSource` | beneath the topic's folder, the group's page and the topic's page alone, the topic's page carrying no picture (§2) | 1 |
| `deliverable.TreeIsTheSameWrittenAPartitionAtATimeTest.everyFileIsTheSame` | twelve files, the three topics' pages among them (§2) | 1 |
| `deliverable.DeliverableTest.rendersTheStoredOrderRatherThanDerivingItAgain` | the topic's folder holds its groups' folders in order and its page (§2) | 1 |
| `deliverable.DeliverableTest.carriesEverySurvivorIntoTheListingWithPathsRelativeToTheArchive` | `topic_order` and `cluster_order` read at their places among thirteen columns (§6) | 1 |
| `pipeline.DeliverableInvocationTest.copiesEverySurvivorIntoTheTreeAndLeavesTheArchiveAsItWas` | the group's page is found two folders down, beside the topic's page (§2) | 1 |

`deliverable.ListedTree` and `deliverable.ListedArrangement` hand the three new answers, a crowded topic's subtopics named `Subtopic n` by their order, every overview *not reached* and no see-also row, so that every test drawing them draws a topic's page.

## What the implementation owes

- **In `deliverable`**: `ListedSubtopic`, `ListedOverview` with `Written` and `Unwritten`, `ListedSeeAlso` (§11); `ArrangedSurvivors.subtopicsOf`, `overviewOf` and `eachPageOfSeeAlso`, and `ArrangedSurvivors.reading` taking `Function<ListedTopic, List<ListedSubtopic>> subtopicsOf`, `Function<ListedTopic, ListedOverview> overviewOf` and `BiConsumer<ListedTopic, Consumer<List<ListedSeeAlso>>> eachPageOfSeeAlso` after `placesOf`; `IndexPage.slug`'s fold (§1); the subtopic's folder and page, the cluster's folder by its place (§3); the topic's page (§2); the index (§4); `ManifestCsv`'s header and two columns (§6); `FixedWords`' index paragraph and §10's words, `topicTableHeads`, `subtopicTableHeads`, `inTheTopic`, `seeAlso` and `seeAlsoSentence`; and `Drawing`'s calls for a subtopic and for a topic's page, each plugin drawing as §7 and §8 have it.
- **In `pipeline`**: `GenerationTasklet`, or the class building the tree's source, answering §11 from `synthesis`'s and `embedding`'s reads.
- **In `synthesis`**: the read of a topic's overview, its sources' count and its fault under a generation run (§11). **In `embedding`**: the read of a topic's `see_also` rows with each document's cluster (§11).
- **In `confluence`**: `ConfluenceTitles.topic` in place of `partition`, and `subtopic`; `ConfluencePages` drawing §7; `Publisher`'s place of four numbers.
- **In `website`**: `WebsiteDrawing`'s pages and its search's `topic` and `subtopic` (§8); `website.js` reading them.
- **`CONTEXT.md`**, as Consequences says.

## What this does not decide

- **Whether a topic's overview is searched on the website**, a row of its own in `search-index.js`.
- **Whether a cluster's page links back to its topic's or subtopic's page.**
- **The reports of topics with no survivor, of seeds feeding none, and of cluster sizes by subtopic**: [#834](https://github.com/algernon28/vespera/issues/834).
- **What `AGENTS.md`, `README.md` and `docs/architecture.md` say of the renderings, and the names of the code and the tests that still say partition**: [#835](https://github.com/algernon28/vespera/issues/835).
