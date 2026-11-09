package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.ClusterPageCeiling;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.deliverable.DeliverableProgress;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.ListedCodeSets;
import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.deliverable.ListedTree;
import io.algernon.vespera.engine.deliverable.SurvivorPictures;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each analysis report writes its JSON beside its page (ADR-362 section 9). A case is added by the ticket of each
 * report's class; this holds the group page sizes', written by {@code deliverable}'s {@code ClusterPageSizes}: its
 * numbers and rows are its page's, in the page's order, a JSON that cannot be moved leaves the earlier pair as it was,
 * and the JSON follows {@code docs/report/cluster-page-sizes.schema.json}. The tree is drawn through the module's public
 * way in, as {@code pipeline} draws it.
 */
@Epic("Reports")
@Feature("Each analysis report writes its numbers")
@Issue("748")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EveryClusterPageSizesReportWritesItsNumbersTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Path CLUSTER_PAGE_SIZES_SCHEMA = Path.of("docs", "report", "cluster-page-sizes.schema.json");

    private static final String CLUSTER_PAGE_SIZES_JSON = "cluster-page-sizes.json";

    private static final String CLUSTER_PAGE_SIZES_PAGE = "cluster-page-sizes.html";

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** Documents of the first group: with paths of near 800 characters, their page passes the ceiling below. */
    private static final int LONG_DOCUMENTS = 40;

    /** Groups of one to this many documents each. */
    private static final int SMALL_GROUPS = 3;

    /** A ceiling that splits the first group's page and no other's. */
    private static final long A_CEILING = 12_000;

    /** What the page says of its sections, in its order, under the ceiling set: the id of each JSON section. */
    private static final List<String> SECTIONS_WITH_A_CEILING = List.of(
            "about", "ceiling", "pages-written-whole", "groups-larger-than-a-mark", "what-the-ceiling-split", "the-largest");

    private static final List<String> SECTIONS_WITH_NONE = List.of(
            "about", "ceiling", "pages-written-whole", "groups-larger-than-a-mark", "the-largest");

    private static final Pattern PARAGRAPH = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);

    private static final Pattern HEADING = Pattern.compile("<h[12]>(.*?)</h[12]>");

    /** A row of a table that states one number under a label, and a row of the table of the largest. */
    private static final Pattern LABELLED_ROW = Pattern.compile("<tr><th>([^<]*)</th><td class=\"count\">([^<]*)</td></tr>");

    private static final Pattern DATA_ROW = Pattern.compile("<tr>((?:<td class=\"count\">[^<]*</td>)+)</tr>");

    private static final Pattern CELL = Pattern.compile("<td class=\"count\">([^<]*)</td>");

    @Test
    @Story("The group page sizes write their JSON")
    @DisplayName("The group page sizes' JSON holds every paragraph, number and row of its page, in the page's order, and the page is still written")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path base) throws IOException {
        drawTheFixture(base, new ClusterPageCeiling.Bytes(A_CEILING));
        Path work = base.resolve("work");

        String page = Files.readString(work.resolve(CLUSTER_PAGE_SIZES_PAGE), StandardCharsets.UTF_8);
        JsonNode report = JSON.readTree(Files.readString(work.resolve(CLUSTER_PAGE_SIZES_JSON), StandardCharsets.UTF_8));

        claim("the JSON names its report and the format 1 the jar writes, and the title is the page's",
                () -> {
                    assertThat(report.get("analysisReport").asString()).isEqualTo("cluster-page-sizes");
                    assertThat(report.get("format").asLong()).isEqualTo(1L);
                    assertThat(report.get("title").asString()).isEqualTo(titlesOf(page).getFirst());
                });
        claim("the sections are the page's parts in its order, each by the id the schema names",
                () -> assertThat(report.get("sections").valueStream().map(section -> section.get("id").asString()))
                        .containsExactlyElementsOf(SECTIONS_WITH_A_CEILING));
        claim("every section's title is the page's heading, in the page's order",
                () -> assertThat(report.get("sections").valueStream().map(section -> section.get("title").asString()))
                        .containsExactlyElementsOf(titlesOf(page)));
        claim("every paragraph of the page is a paragraph of the JSON, in the page's order",
                () -> assertThat(paragraphsOf(report)).isEqualTo(textsOf(PARAGRAPH, page)));
        claim("every row of every table of the page is a row of the JSON, with the same cells, in the page's order",
                () -> assertThat(rowsOf(report)).isEqualTo(rowsOf(page)));
        claim("the group page sizes draw no chart",
                () -> report.get("sections").forEach(section -> assertThat(section.get("chart").isNull()).isTrue()));
        List<List<String>> rows = rowsOf(report);
        claim("the headline states the groups, the largest page and the 95th percentile and median, as the page's rows state them",
                () -> {
                    assertThat(headline(report, "groups")).isEqualTo(Long.parseLong(valueOf(rows, "Groups")));
                    assertThat(headline(report, "largest")).isEqualTo(Long.parseLong(valueOf(rows, "Largest, in bytes")));
                    assertThat(headline(report, "percentile-95"))
                            .isEqualTo(Long.parseLong(valueOf(rows, "95th percentile, in bytes")));
                    assertThat(headline(report, "median")).isEqualTo(Long.parseLong(valueOf(rows, "Median, in bytes")));
                });
        claim("every headline figure is a count or a size in bytes, and a whole number",
                () -> report.get("headline").forEach(figure -> {
                    assertThat(figure.get("unit").asString()).isIn("count", "bytes");
                    assertThat(figure.get("value").isIntegralNumber()).isTrue();
                }));
    }

    @Test
    @Story("The group page sizes write their JSON")
    @DisplayName("With no ceiling set, the JSON holds no section on what it split, and a tree of no group holds none for each size and the groups alone in its headline")
    void aTreeOfNoCeilingOrNoGroupIsStatedAsItsPageStatesIt(@TempDir Path base) throws IOException {
        drawTheFixture(base.resolve("unset"), new ClusterPageCeiling.Unset());
        drawNothing(base.resolve("empty"));

        JsonNode unset = JSON.readTree(
                Files.readString(base.resolve("unset").resolve("work").resolve(CLUSTER_PAGE_SIZES_JSON), StandardCharsets.UTF_8));
        JsonNode empty = JSON.readTree(
                Files.readString(base.resolve("empty").resolve("work").resolve(CLUSTER_PAGE_SIZES_JSON), StandardCharsets.UTF_8));
        String emptyPage = Files.readString(
                base.resolve("empty").resolve("work").resolve(CLUSTER_PAGE_SIZES_PAGE), StandardCharsets.UTF_8);

        claim("with no ceiling the sections are the page's five",
                () -> assertThat(unset.get("sections").valueStream().map(section -> section.get("id").asString()))
                        .containsExactlyElementsOf(SECTIONS_WITH_NONE));
        claim("a tree of no group states none for the largest, the 95th percentile and the median, as its page does",
                () -> {
                    assertThat(rowsOf(empty)).isEqualTo(rowsOf(emptyPage));
                    assertThat(valueOf(rowsOf(empty), "Largest, in bytes")).isEqualTo("none");
                });
        claim("and its headline holds the groups, 0, and no size, which it has none of",
                () -> {
                    assertThat(headline(empty, "groups")).isZero();
                    assertThat(empty.get("headline")).hasSize(1);
                });
    }

    @Test
    @Story("The group page sizes write their JSON")
    @DisplayName("A group page sizes JSON that cannot be moved into place is handled as a page that cannot be written is, and leaves the earlier pair as it was")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(@TempDir Path base)
            throws IOException {
        Path work = Files.createDirectories(base.resolve("work"));
        String earlierPage = "<html>the earlier page</html>";
        Files.writeString(work.resolve(CLUSTER_PAGE_SIZES_PAGE), earlierPage, StandardCharsets.UTF_8);
        Path inItsPlace = Files.createDirectory(work.resolve(CLUSTER_PAGE_SIZES_JSON));
        Files.writeString(inItsPlace.resolve("held"), "a folder is not replaced by a file");

        Path tree = drawTheFixture(base, new ClusterPageCeiling.Unset());

        claim("the drawing fails nothing: the tree's index stands",
                () -> assertThat(tree.resolve("index.html")).isRegularFile());
        claim("the earlier page is as it was, the page being written after its JSON is in place",
                () -> assertThat(Files.readString(work.resolve(CLUSTER_PAGE_SIZES_PAGE), StandardCharsets.UTF_8))
                        .isEqualTo(earlierPage));
        claim("the earlier JSON is as it was, and no part file is left beside either",
                () -> {
                    assertThat(inItsPlace.resolve("held")).isRegularFile();
                    assertThat(work.resolve(CLUSTER_PAGE_SIZES_JSON + ".part")).doesNotExist();
                    assertThat(work.resolve(CLUSTER_PAGE_SIZES_PAGE + ".part")).doesNotExist();
                });
    }

    @Test
    @Story("The group page sizes write their JSON")
    @DisplayName("The group page sizes' JSON follows its schema, with the headline's numbers alone")
    void eachJsonFollowsItsSchema(@TempDir Path base) throws IOException {
        drawTheFixture(base, new ClusterPageCeiling.Bytes(A_CEILING));
        JsonNode schema = JSON.readTree(Files.readString(CLUSTER_PAGE_SIZES_SCHEMA, StandardCharsets.UTF_8));
        JsonNode report = JSON.readTree(
                Files.readString(base.resolve("work").resolve(CLUSTER_PAGE_SIZES_JSON), StandardCharsets.UTF_8));

        claim("the schema uses only the eight keywords and describes every field",
                () -> assertThat(SchemaWalker.keywordProblems(schema)).isEmpty());
        claim("the JSON holds every field the schema requires and none it does not name, each of the type it gives",
                () -> assertThat(SchemaWalker.problems(report, schema)).isEmpty());
        claim("the schema's format is the one the JSON is written in",
                () -> assertThat(schema.get("properties").get("format").get("const").asLong())
                        .isEqualTo(report.get("format").asLong()));
    }

    private static Path drawNothing(Path base) {
        return Deliverable.writeTo(
                base.resolve("work"), DeliverableRoot.UNNAMED, ListedTree.provenance(base.resolve("archive")),
                ListedTree.of(List.of(), Map.of(), List.of(), Map.of()), SurvivorPictures.none(), DeliverableProgress.NONE);
    }

    /** One group of long-pathed documents written over, and small groups of 1 to {@value #SMALL_GROUPS} documents. */
    private static Path drawTheFixture(Path base, ClusterPageCeiling ceiling) {
        List<RecordedCluster> arrangement = new ArrayList<>();
        List<ListedSurvivor> survivors = new ArrayList<>();
        long[] next = {1_000};
        RecordedCluster largest = cluster(THE_SEED, 0, "Hydrant valves", 1, LONG_DOCUMENTS);
        arrangement.add(largest);
        survivors.addAll(members(0, LONG_DOCUMENTS, true, next));
        for (int small = 1; small <= SMALL_GROUPS; small++) {
            arrangement.add(cluster(THE_SEED, small, "Alarm panel " + small, 1 + small, small));
            survivors.addAll(members(small, small, false, next));
        }
        Map<ClusterSlot, SynthesisDoc> written =
                Map.of(slotOf(largest), writing("Inspection of hydrant valves", "Valves [1] and seals [2].", 1_000, 1_001));
        return Deliverable.writeTo(
                base.resolve("work"), DeliverableRoot.UNNAMED, ListedTree.provenance(base.resolve("archive")),
                ListedTree.of(arrangement, written, survivors, Map.of()), ListedCodeSets.NONE, SurvivorPictures.none(),
                ceiling, DeliverableProgress.NONE);
    }

    private static List<ListedSurvivor> members(int ordinal, int count, boolean longPaths, long[] next) {
        List<ListedSurvivor> members = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            long occurrence = next[0]++;
            String path = (longPaths ? "r/".repeat(395) : "reports/") + occurrence + ".docx";
            members.add(survivor(occurrence, path, "0".repeat(64), THE_SEED, "seeds/Fire Safety.docx", ordinal, 0.9 - at * 1e-4));
        }
        return members;
    }

    private static List<String> titlesOf(String page) {
        return textsOf(HEADING, page);
    }

    private static List<String> paragraphsOf(JsonNode report) {
        List<String> paragraphs = new ArrayList<>();
        for (JsonNode section : report.get("sections")) {
            section.get("text").forEach(paragraph -> paragraphs.add(paragraph.asString()));
        }
        return paragraphs;
    }

    /** The cells of each row of each table of the JSON, in order, a null cell as the page says it: none. */
    private static List<List<String>> rowsOf(JsonNode report) {
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode section : report.get("sections")) {
            if (!section.get("table").isNull()) {
                section.get("table").get("rows").forEach(row -> {
                    List<String> cells = new ArrayList<>();
                    row.forEach(cell -> cells.add(cell.isNull() ? "none" : cell.isNumber() ? cell.toString() : cell.asString()));
                    rows.add(cells);
                });
            }
        }
        return rows;
    }

    /** The cells of each data row of the page's tables, in the page's order. */
    private static List<List<String>> rowsOf(String page) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = Pattern.compile("<tr>.*?</tr>").matcher(page);
        while (row.find()) {
            Matcher labelled = LABELLED_ROW.matcher(row.group());
            if (labelled.matches()) {
                rows.add(List.of(labelled.group(1), labelled.group(2)));
                continue;
            }
            Matcher data = DATA_ROW.matcher(row.group());
            if (data.matches()) {
                rows.add(textsOf(CELL, data.group(1)));
            }
        }
        return rows;
    }

    private static String valueOf(List<List<String>> rows, String label) {
        return rows.stream().filter(row -> row.getFirst().equals(label)).findFirst().orElseThrow().get(1);
    }

    private static long headline(JsonNode report, String key) {
        for (JsonNode figure : report.get("headline")) {
            if (figure.get("key").asString().equals(key)) {
                return figure.get("value").asLong();
            }
        }
        throw new AssertionError("the headline holds no figure " + key);
    }

    private static List<String> textsOf(Pattern pattern, String html) {
        List<String> texts = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            texts.add(matcher.group(1).replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"));
        }
        return texts;
    }
}
