package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code documents.csv}: one row per survivor, in the order the survivors arrive, with the place its
 * cluster holds in the arrangement (ADR-104, ADR-112), and quoted under RFC 4180 because it answers to a
 * parser rather than a reader (ADR-136 §5, ADR-137 §4).
 *
 * <p><b>The expected file is what {@code Deliverable.writeManifest} wrote at {@code 4b99a03}</b> for the
 * same arrangement and survivors, with the two columns ADR-274 §1.4 adds at its end: the archive is not there, so
 * each copy is {@code gone} and names nothing.
 *
 * <p>Since ADR-223 the manifest is written a page of survivors at a time and nothing composes it whole, so the
 * lists here go through {@link ListedArrangement}, which hands them over as the tree's writer is handed them.
 */
@Epic("The deliverable")
@Feature("The listing of every document the tree arranges")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class ManifestCsvTest {

    /** The seed that won every survivor below, whose topic every cluster below lies in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The seed's own path, carrying an ampersand and brackets, which names the topic. */
    private static final String SEED_PATH = "seeds/Fire & Safety [2019].docx";

    /** The two clusters' identities, and the order 6a placed them in. */
    private static final int FIRST_ORDINAL = 0;

    private static final int SECOND_ORDINAL = 1;

    @Test
    @Story("A field is quoted under RFC 4180 and nothing else")
    @DisplayName("A field with a comma, a quote, a line feed or a carriage return is quoted, its quotes doubled")
    void quotesAFieldThatWouldBreakARow() {
        claim(
                "a comma would split the field in two, so the field is quoted",
                () -> assertThat(ManifestCsv.quoted("reports/a, b.pdf")).isEqualTo("\"reports/a, b.pdf\""));
        claim(
                "a quote is doubled inside a quoted field, so it is read as one quote and not as the field's end",
                () -> assertThat(ManifestCsv.quoted("reports/\"quoted\".pdf"))
                        .isEqualTo("\"reports/\"\"quoted\"\".pdf\""));
        claim(
                "a line feed would end the row, so the field is quoted and the line feed kept",
                () -> assertThat(ManifestCsv.quoted("line\nbreak.txt")).isEqualTo("\"line\nbreak.txt\""));
        claim(
                "a carriage return is quoted for the same reason",
                () -> assertThat(ManifestCsv.quoted("carriage\rreturn.txt")).isEqualTo("\"carriage\rreturn.txt\""));
    }

    @Test
    @Story("A path a spreadsheet would run as a formula is written so that it is shown as text")
    @DisplayName("A path that begins with =, +, - or @ is written behind a leading quote, so a spreadsheet opening the listing shows it and runs nothing")
    @Link(name = "ADR-235", url = Adr.A_MANIFEST_CELL_A_SPREADSHEET_WOULD_RUN_IS_WRITTEN_BEHIND_A_QUOTE, type = "adr")
    void aPathASpreadsheetWouldRunIsWrittenBehindAQuote() {
        List<ListedSurvivor> formulas = List.of(
                survivor(10, "=SUM(A1).txt", FIRST_ORDINAL, 0.9),
                survivor(11, "+1 notes.pdf", FIRST_ORDINAL, 0.8),
                survivor(12, "-draft.docx", FIRST_ORDINAL, 0.7),
                survivor(13, "@cmd, x.pdf", SECOND_ORDINAL, 0.6),
                survivor(14, "reports/=a.pdf", SECOND_ORDINAL, 0.5));

        String manifest = ListedArrangement.manifestContents(anArrangement(), formulas);

        claim(
                "each path that begins with a sign a spreadsheet reads as the start of a formula is written with a"
                        + " single quote before it, which the spreadsheet shows as text and does not run",
                () -> assertThat(manifest)
                        .contains("\n10,'=SUM(A1).txt,")
                        .contains("\n11,'+1 notes.pdf,")
                        .contains("\n12,'-draft.docx,"));
        claim(
                "a path that also needs quoting is quoted around the quote that neutralises it",
                () -> assertThat(manifest).contains("\n13,\"'@cmd, x.pdf\","));
        claim(
                "and a path whose sign is not its first character is written as the archive spells it",
                () -> assertThat(manifest).contains("\n14,reports/=a.pdf,"));
    }

    /**
     * A survivor's membership is by its topic, and its winning seed is read from {@code relevance_score} alone, so a
     * survivor with no score on record has no winning seed: its {@code winning_seed} cell is empty and its {@code
     * relevance_score} 0.0, as ADR-226 has the score (ADR-409 section 10).
     */
    @Test
    @Story("A survivor with no score on record is listed with no winning seed")
    @DisplayName("A survivor with no score on record is listed with an empty winning seed and a score of 0.0, the row written whole")
    @Issue("832")
    @Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
    @Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
    void aSurvivorWithNoScoreOnRecordIsListedWithNoWinningSeed() {
        ListedSurvivor unscored = new ListedSurvivor(
                new OccurrenceId(20),
                new OccurrencePath("reports/unscored.pdf"),
                "h20",
                Optional.empty(),
                ListedTree.topicOf(THE_SEED),
                ListedTree.topicNameOf(SEED_PATH),
                OptionalInt.empty(),
                FIRST_ORDINAL,
                0.0);
        List<ListedSurvivor> survivors = List.of(survivor(10, "reports/scored.pdf", FIRST_ORDINAL, 0.9), unscored);

        String manifest = ListedArrangement.manifestContents(anArrangement(), survivors);

        claim(
                "the survivor with no score on record is listed, its winning seed's cell empty and its score 0.0",
                () -> assertThat(manifest).contains("\n20,reports/unscored.pdf,h20,,0.0,"));
        claim(
                "its row carries every column of the header, the empty cell holding its place",
                () -> assertThat(rowOf(manifest, "20,").split(",", -1))
                        .as("the cells of the unscored survivor's row")
                        .hasSameSizeAs(manifest.substring(0, manifest.indexOf('\n')).split(",", -1)));
        claim(
                "and a survivor scored against a seed still names that seed",
                () -> assertThat(manifest).contains("\n10,reports/scored.pdf,h10," + THE_SEED.value() + ",0.9,"));
    }

    /**
     * The header names the topic, the subtopic and their orders, where it named a seed partition, and a survivor of a
     * topic with no subtopics leaves the subtopic's two cells empty, each holding its place (ADR-415 §6).
     */
    @Test
    @Story("The listing names each document's topic and subtopic")
    @DisplayName("The listing's header names the topic, the subtopic, the group and their orders, and a document of a topic with no subtopics has empty subtopic cells")
    @Issue("833")
    @Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
    void theHeaderNamesTheTopicAndTheSubtopicAndTheirOrders() {
        String manifest = ListedArrangement.manifestContents(
                anArrangement(), List.of(survivor(10, "reports/scored.pdf", FIRST_ORDINAL, 0.9)));

        claim(
                "the header, whole: the document's identity, its score, then the names of its topic, its subtopic and"
                        + " its group, then their three orders, then its copy",
                () -> assertThat(manifest.substring(0, manifest.indexOf('\n'))).isEqualTo(
                        "occurrence_id,path,content_hash,winning_seed,relevance_score,topic,subtopic,cluster,topic_order,"
                                + "subtopic_order,cluster_order,copy,copy_outcome"));
        claim(
                "the document of a topic with no subtopics names its topic, has an empty subtopic and subtopic order,"
                        + " and names its group and the topic's and group's orders",
                () -> assertThat(manifest).contains("\n10,reports/scored.pdf,h10," + THE_SEED.value() + ",0.9,"
                        + ListedTree.topicNameOf(SEED_PATH) + ",,0,1,,1,"));
    }

    /** The line of {@code manifest} that begins with {@code prefix}. */
    private static String rowOf(String manifest, String prefix) {
        return manifest.lines()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("documents.csv has no row beginning " + prefix));
    }

    /** Two clusters in one topic, 6a's first and second. */
    private static List<RecordedCluster> anArrangement() {
        return List.of(
                ListedTree.cluster(THE_SEED, FIRST_ORDINAL, "Fire Suppression", 1, 3),
                ListedTree.cluster(THE_SEED, SECOND_ORDINAL, "Sprinklers", 2, 2));
    }

    private static ListedSurvivor survivor(long occurrence, String path, int clusterOrdinal, double score) {
        return ListedTree.survivor(occurrence, path, "h" + occurrence, THE_SEED, SEED_PATH, clusterOrdinal, score);
    }
}
