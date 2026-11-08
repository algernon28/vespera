package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.ListedDoc;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tree stage 6b leaves on disk (ADR-103, ADR-104, ADR-112, ADR-274, #186): where it lands, what its
 * names are, and what the two mechanical files at its root say.
 *
 * <p>Written against the writer rather than against an invocation, because three of the claims here
 * cannot be reached from one. A fixture corpus produces one seed partition holding one cluster, so
 * nothing driven end to end can ask what the ordinal prefix does at the boundary between nine
 * clusters and ten; and an archive that is not there at all cannot be arranged behind a walk that has
 * just read the same files.
 *
 * <p><b>The archive these fixtures name is not on this machine.</b> Each survivor's copy is then {@code gone}
 * (ADR-274 §2.3), which no claim here is about: the copies are {@code EverySurvivorIsCopiedTest}'s. What a tree
 * written over such an archive shows is that a copy that cannot be made costs that copy and nothing else.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122). The tree is
 * one of that record's outside-facing surfaces: every word an operator reads in it renders the term,
 * and every name this project gives itself here does not. {@link
 * Deliverable#NOTHING_WAS_WRITTEN_OVER_IT} is both halves of that split on one line and is claimed
 * as such below.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("186")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class DeliverableTest {

    /** A run id of the shape the ledger mints: sixty-four hexadecimal characters (ADR-048). */
    private static final String RUN_ID = "a".repeat(48) + "0123456789abcdef";

    /** The walk the run read, which is what says which archive the tree was drawn from (ADR-104). */
    private static final long WALK = 7L;

    /** The seed whose partition every cluster below sits in, unless a test says otherwise. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The seed document's own filename, which is what the partition directory is named from. */
    private static final String SEED_PATH = "seeds/Industrial Safety Standards.docx";

    /** The stem of that filename, slugged, which is what the directory name has to come out as. */
    private static final String SEED_STEM_SLUGGED = "industrial-safety-standards";

    /** What stage 6a called the one cluster most of these tests arrange. */
    private static final String THE_LABEL = "Fire Suppression Retrofits";

    /** That label slugged, which is what the cluster's own filename has to come out as. */
    private static final String THE_LABEL_SLUGGED = "fire-suppression-retrofits";

    /** What stage 6b called it, which is a different name for the same cluster (ADR-106). */
    private static final String THE_TITLE = "Retrofitting Suppression, 2018 to 2021";

    /** The writing kept against it, whose text is not what this class is about. */
    private static final String THE_PROSE = "Both of them [1] describe the same retrofit.";

    /** How many documents the cluster holds, which is the number the index states. */
    private static final int DOCUMENTS_IN_THE_CLUSTER = 2;

    /** The first cluster ordinal these fixtures hand out, which is the identity 6a minted. */
    private static final int FIRST_ORDINAL = 0;

    /** A cluster ordinal minted later than the first, which is identity and never a place in the order. */
    private static final int A_LATER_ORDINAL = 9;

    /** What the substantial cluster below holds, against the lone document placed ahead of it. */
    private static final int FORTY_DOCUMENTS = 40;

    /** And what that lone one holds. */
    private static final int ONE_DOCUMENT = 1;

    /** A name placed first while sorting last of the two, so alphabetical order cannot be what decided. */
    private static final String THE_NAME_THAT_SORTS_LAST = "Zinc Coating Trials";

    /** Its counterpart, placed second while sorting first. */
    private static final String THE_NAME_THAT_SORTS_FIRST = "Access Platforms";

    /** The first place in an order, which both levels count from (ADR-112). */
    private static final int FIRST_PLACE = 1;

    /** The second place in that order. */
    private static final int SECOND_PLACE = 2;

    /** The third place, which no partition order of a fixture here shares with its cluster order. */
    private static final int THIRD_PLACE = 3;

    /** The prefix the only member of a level of one carries: one digit, because the count is one. */
    private static final String THE_ONLY_ONE_PREFIX = "1-";

    /** The archive the rest of these fixtures were walked from, recorded once in the index. */
    private static final String THE_ARCHIVE_ROOT = Path.of("D:", "archive").toString();

    /** The one line above the listing's rows, which names the columns rather than carrying a document. */
    private static final int THE_HEADER_ROW = 1;

    /** The relevance score of the first survivor listed, which the manifest carries as it stands. */
    private static final double A_SCORE = 0.81;

    /** The score of the second, lower so the two are told apart in the listing. */
    private static final double A_LOWER_SCORE = 0.42;

    /** How many rows a manifest listing two survivors carries beneath that header. */
    private static final int TWO_SURVIVORS = 2;

    /** Where the document's own path sits among those columns, counting from zero: second of eleven. */
    private static final int THE_PATH_COLUMN = 1;

    /** Where its seed partition's place in the order sits: eighth of eleven. */
    private static final int THE_PARTITION_ORDER_COLUMN = 7;

    /** And where its own place within that partition sits: ninth of eleven. */
    private static final int THE_CLUSTER_ORDER_COLUMN = 8;

    /** What the profile key naming the archive's own documents was set to when the run read it. */
    private static final String THE_SEED_FOLDER_VALUE = "D:/archive/seeds";

    /** The name of that key, which the index states beside its value so the tree stands alone. */
    private static final String THE_SEED_FOLDER_KEY = "seedFolder";

    @Test
    @Story("The tree is named after the run that produced it")
    @DisplayName("The tree lands in the working directory under the full name of the run that wrote it")
    void namesTheTreeAfterTheRunThatProducedIt(@TempDir Path workingDirectory) {
        Path tree = ListedArrangement.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, FIRST_PLACE, FIRST_PLACE)),
                List.of(writingFor(FIRST_ORDINAL)),
                survivors(FIRST_ORDINAL));

        claim(
                "with no deliverable root named, the tree sits under the working directory, the directory the"
                        + " database is in",
                () -> assertThat(tree).startsWithRaw(workingDirectory));
        claim(
                "and the directory is named with the run's whole name, all " + RUN_ID.length()
                        + " characters of it: the name is worked out from what the run read, so an"
                        + " unchanged re-run writes this very path and a changed one writes a new tree"
                        + " beside it -- and a shortened name would be a name two runs could share",
                () -> assertThat(tree.getFileName().toString()).isEqualTo(RUN_ID));
        claim(
                "the index sits at its root, which is what the reader opens",
                () -> assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME))
                        .exists()
                        .isRegularFile());
        claim(
                "and the listing of the documents beside it, so an operator can build what comes after"
                        + " the hand-off without reading a word of the prose or opening the database",
                () -> assertThat(tree.resolve(Deliverable.MANIFEST_FILE_NAME))
                        .exists()
                        .isRegularFile());
        claim(
                "the one directory beneath it is named after the operator's own seed document, so the"
                        + " top of the tree reads in their vocabulary rather than in an author's -- and"
                        + " the page inside it in a folder named after the name stage 6a derived, never after the heading"
                        + " the model wrote, because a path that depended on the writing succeeding would"
                        + " not exist for a group nothing was written over",
                () -> assertThat(tree.resolve(THE_ONLY_ONE_PREFIX + SEED_STEM_SLUGGED)
                                .resolve(THE_ONLY_ONE_PREFIX + THE_LABEL_SLUGGED)
                                .resolve(Deliverable.CLUSTER_PAGE_NAME))
                        .isRegularFile());
    }

    @Test
    @Story("The order the operator approved is the order the reader receives")
    @DisplayName("The groups are listed in the order they were stored in, never sorted again on the way out")
    void rendersTheStoredOrderRatherThanDerivingItAgain(@TempDir Path workingDirectory) throws IOException {
        RecordedCluster lastButPlacedFirst = new RecordedCluster(
                new ArrangedCluster(THE_SEED, A_LATER_ORDINAL, ONE_DOCUMENT, FIRST_PLACE, FIRST_PLACE),
                new ClusterLabel(THE_NAME_THAT_SORTS_LAST));
        RecordedCluster firstButPlacedSecond = new RecordedCluster(
                new ArrangedCluster(THE_SEED, FIRST_ORDINAL, FORTY_DOCUMENTS, FIRST_PLACE, SECOND_PLACE),
                new ClusterLabel(THE_NAME_THAT_SORTS_FIRST));

        Path tree = ListedArrangement.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(lastButPlacedFirst, firstButPlacedSecond),
                List.of(),
                survivors(A_LATER_ORDINAL));

        String index = Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME));
        claim(
                "the group stored in place " + FIRST_PLACE + " is listed ahead of the one stored in place"
                        + " " + SECOND_PLACE + ", though it holds " + ONE_DOCUMENT + " document against "
                        + FORTY_DOCUMENTS + " and its name sorts last of the two: the sequence a person"
                        + " approved and the sequence a reader receives have to be one sequence, and"
                        + " anything that sorted again here would quietly hand over an arrangement nobody"
                        + " looked at",
                () -> assertThat(index).containsSubsequence(THE_NAME_THAT_SORTS_LAST, THE_NAME_THAT_SORTS_FIRST));
        claim(
                "and the file names carry the same sequence, so the listing on disk says what the index"
                        + " says",
                () -> assertThat(fileNamesUnder(tree.resolve(THE_ONLY_ONE_PREFIX + SEED_STEM_SLUGGED)))
                        .containsExactlyInAnyOrder("1-zinc-coating-trials", "2-access-platforms"));
    }

    @Test
    @Story("Whatever comes after the hand-off can be built without reading the prose")
    @DisplayName("The listing carries every document with its place in the archive and its place in the order")
    void carriesEverySurvivorIntoTheListingWithPathsRelativeToTheArchive(@TempDir Path workingDirectory)
            throws IOException {
        Path tree = ListedArrangement.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, SECOND_PLACE, THIRD_PLACE)),
                List.of(writingFor(FIRST_ORDINAL)),
                survivors(FIRST_ORDINAL));
        List<String> lines = Files.readAllLines(tree.resolve(Deliverable.MANIFEST_FILE_NAME));

        claim(
                "all " + TWO_SURVIVORS + " documents are listed -- " + TWO_SURVIVORS
                        + " rows and the " + THE_HEADER_ROW + " header line above them -- which is every"
                        + " document arranged into the group rather than the ones its writing happened to"
                        + " mention",
                () -> assertThat(lines).hasSize(TWO_SURVIVORS + THE_HEADER_ROW));
        claim(
                "each path is the one beneath the archive's own root rather than a whole path from a"
                        + " drive letter down: the root is stated once in the index",
                () -> assertThat(rowsOf(tree))
                        .allSatisfy(row -> assertThat(columnsOf(row)[THE_PATH_COLUMN])
                                .doesNotContain(THE_ARCHIVE_ROOT)
                                .startsWith("reports/")));
        claim(
                "and every row carries both places the arrangement gave its group -- "
                        + SECOND_PLACE + " among the seeds and " + THIRD_PLACE + " among that seed's"
                        + " groups -- so a consumer rebuilding anything from this file has the order"
                        + " without parsing the index or falling back to alphabetical",
                () -> assertThat(rowsOf(tree)).allSatisfy(row -> {
                    assertThat(columnsOf(row)[THE_PARTITION_ORDER_COLUMN]).isEqualTo(String.valueOf(SECOND_PLACE));
                    assertThat(columnsOf(row)[THE_CLUSTER_ORDER_COLUMN]).isEqualTo(String.valueOf(THIRD_PLACE));
                }));
    }

    /** What produced the tree, with one profile value stated so the index has one to carry. */
    private static DeliverableProvenance provenance(String seedFolder) {
        return new DeliverableProvenance(
                RUN_ID, WALK, THE_ARCHIVE_ROOT, List.of(new NamedValue(THE_SEED_FOLDER_KEY, seedFolder)));
    }

    /** One cluster of the one seed partition these fixtures arrange, in the place it was given. */
    private static RecordedCluster aCluster(int ordinal, String label, int partitionOrder, int clusterOrder) {
        return new RecordedCluster(
                new ArrangedCluster(THE_SEED, ordinal, DOCUMENTS_IN_THE_CLUSTER, partitionOrder, clusterOrder),
                new ClusterLabel(label));
    }

    /** The writing stage 6b kept against the cluster identified by {@code ordinal}, over both its documents. */
    private static ListedDoc writingFor(int ordinal) {
        return writingFor(ordinal, 10L);
    }

    /**
     * The same, over the two documents numbered from {@code firstOccurrenceId}.
     *
     * <p>Which documents the call carried is stated rather than counted, because the page numbers its
     * membership from that list (ADR-133): a fixture naming documents the cluster does not hold is
     * the integrity failure the writer refuses, so each of these names the survivors beside it.
     */
    private static ListedDoc writingFor(int ordinal, long firstOccurrenceId) {
        return writingFor(ordinal, new OccurrenceId(firstOccurrenceId), new OccurrenceId(firstOccurrenceId + 1));
    }

    /** The same again, over exactly the documents named, in the order the call's ordinals were minted. */
    private static ListedDoc writingFor(int ordinal, OccurrenceId... sent) {
        return new ListedDoc(
                THE_SEED, ordinal, new SynthesisDoc(THE_TITLE, THE_PROSE, List.of(sent)));
    }

    /** The two survivors that cluster holds, both of them under the one seed. */
    private static List<ListedSurvivor> survivors(int ordinal) {
        return survivors(ordinal, 10L);
    }

    /** Those same two survivors, numbered from {@code firstOccurrenceId} so two clusters do not collide. */
    private static List<ListedSurvivor> survivors(int ordinal, long firstOccurrenceId) {
        return List.of(
                new ListedSurvivor(
                        new OccurrenceId(firstOccurrenceId),
                        new OccurrencePath("reports/2019/retrofit.pdf"),
                        "3a7b",
                        THE_SEED,
                        SEED_PATH,
                        ordinal,
                        A_SCORE),
                new ListedSurvivor(
                        new OccurrenceId(firstOccurrenceId + 1),
                        new OccurrencePath("reports/2021/retrofit follow-up.pdf"),
                        "9c11",
                        THE_SEED,
                        SEED_PATH,
                        ordinal,
                        A_LOWER_SCORE));
    }

    /** The names of everything directly beneath {@code directory}, in no particular order. */
    private static List<String> fileNamesUnder(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> entry.getFileName().toString()).toList();
        }
    }

    /** The manifest's rows, without the header that names their columns. */
    private static List<String> rowsOf(Path tree) throws IOException {
        return Files.readAllLines(tree.resolve(Deliverable.MANIFEST_FILE_NAME)).stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .toList();
    }

    /**
     * One manifest row split into the columns the header names, read as a consumer of the format
     * would read it.
     *
     * <p>Split on the format rather than on the character: a path may legally carry a comma, in which
     * case the field arrives quoted, and a splitter that cut on every comma would report one column too
     * many for that row and silently move every value after the path one place along.
     *
     * <p>Quotes are stripped and doubled quotes collapsed, so what comes back is the value the writer
     * was given rather than its rendering.
     */
    private static String[] columnsOf(String row) {
        List<String> columns = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int at = 0; at < row.length(); at++) {
            char character = row.charAt(at);
            if (inQuotes) {
                if (character != '"') {
                    field.append(character);
                } else if (at + 1 < row.length() && row.charAt(at + 1) == '"') {
                    field.append('"');
                    at++;
                } else {
                    inQuotes = false;
                }
            } else if (character == '"') {
                inQuotes = true;
            } else if (character == ',') {
                columns.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        columns.add(field.toString());
        return columns.toArray(new String[0]);
    }

}
