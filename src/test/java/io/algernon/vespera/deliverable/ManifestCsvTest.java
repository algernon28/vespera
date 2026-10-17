package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
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

    /** The seed whose partition every cluster below sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The seed's own path, carrying an ampersand and brackets that the CSV leaves alone. */
    private static final String SEED_PATH = "seeds/Fire & Safety [2019].docx";

    /** The two clusters' identities, and the order 6a placed them in. */
    private static final int FIRST_ORDINAL = 0;

    private static final int SECOND_ORDINAL = 1;

    /** The whole file for {@link #anArrangement()} and {@link #itsSurvivors()}. */
    private static final String THE_MANIFEST = "occurrence_id,path,content_hash,winning_seed,relevance_score,"
            + "seed_partition,cluster,partition_order,cluster_order,copy,copy_outcome\n"
            + "10,reports/a.docx,h10,1,0.9,seeds/Fire & Safety [2019].docx,0,1,1,,gone\n"
            + "11,\"reports/b, c.pdf\",h11,1,0.5,seeds/Fire & Safety [2019].docx,0,1,1,,gone\n"
            + "14,reports/f.pdf,h14,1,0.6,seeds/Fire & Safety [2019].docx,0,1,1,,gone\n"
            + "12,reports/d.pdf,h12,1,0.7,seeds/Fire & Safety [2019].docx,1,1,2,,gone\n"
            + "13,reports/e.pdf,h13,1,0.7,seeds/Fire & Safety [2019].docx,1,1,2,,gone\n";

    @Test
    @Story("The manifest lists every survivor with its cluster's place")
    @DisplayName("The manifest is the header and one row per survivor, in the order the survivors arrive, with its cluster's place")
    void writesTheWholeManifest() {
        claim(
                "the file is the header naming the columns as the ledger names them, then each survivor in the"
                        + " order it was handed over, with the partition order and cluster order 6a gave its"
                        + " cluster, byte for byte as the deliverable wrote it",
                () -> assertThat(ListedArrangement.manifestContents(anArrangement(), itsSurvivors())).isEqualTo(THE_MANIFEST));
    }

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
    @Story("A field is quoted under RFC 4180 and nothing else")
    @DisplayName("A field with a backslash, brackets or a pipe is written as the archive spells it")
    void leavesMarkdownCharactersAlone() {
        claim(
                "no Markdown escape reaches the manifest: a consumer loading it into a table gets the path as the"
                        + " archive spells it, backslash, brackets and pipe included (ADR-136 §5, ADR-138 §6)",
                () -> assertThat(ManifestCsv.quoted("reports/a \\ [b] | c.pdf")).isEqualTo("reports/a \\ [b] | c.pdf"));
        claim(
                "a field with nothing to quote is written as it is",
                () -> assertThat(ManifestCsv.quoted("reports/plain.pdf")).isEqualTo("reports/plain.pdf"));
    }

    @Test
    @Story("The manifest lists every survivor with its cluster's place")
    @DisplayName("A survivor naming a cluster the arrangement does not carry stops the writer, rather than inventing a place")
    void refusesASurvivorWhoseClusterIsNotArranged() {
        List<ListedSurvivor> withAStray = List.of(
                survivor(10, "reports/a.docx", FIRST_ORDINAL, 0.9), survivor(15, "reports/stray.pdf", 7, 0.4));

        claim(
                "the arrangement is total over its survivors (ADR-105), so a row of 0,0 for the stray would be"
                        + " two plausible numbers in a file built to be loaded into a table; the writer throws,"
                        + " naming the survivor, its cluster and its seed",
                () -> assertThatThrownBy(() -> ListedArrangement.manifestContents(anArrangement(), withAStray))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("survivor 15 names cluster 7 of seed 1, which the arrangement does not carry"));
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

    /** Two clusters in one partition, 6a's first and second. */
    private static List<RecordedCluster> anArrangement() {
        return List.of(
                new RecordedCluster(
                        new ArrangedCluster(THE_SEED, FIRST_ORDINAL, 3, 1, 1), new ClusterLabel("Fire Suppression")),
                new RecordedCluster(
                        new ArrangedCluster(THE_SEED, SECOND_ORDINAL, 2, 1, 2), new ClusterLabel("Sprinklers")));
    }

    /** Five survivors, three in the first cluster and two in the second, in the order they are handed over. */
    private static List<ListedSurvivor> itsSurvivors() {
        return List.of(
                survivor(10, "reports/a.docx", FIRST_ORDINAL, 0.9),
                survivor(11, "reports/b, c.pdf", FIRST_ORDINAL, 0.5),
                survivor(14, "reports/f.pdf", FIRST_ORDINAL, 0.6),
                survivor(12, "reports/d.pdf", SECOND_ORDINAL, 0.7),
                survivor(13, "reports/e.pdf", SECOND_ORDINAL, 0.7));
    }

    private static ListedSurvivor survivor(long occurrence, String path, int clusterOrdinal, double score) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                "h" + occurrence,
                THE_SEED,
                SEED_PATH,
                clusterOrdinal,
                score);
    }
}
