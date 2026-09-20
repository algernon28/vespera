package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
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
 * The tree stage 6b leaves on disk (ADR-103, ADR-104, ADR-112, #186): where it lands, what its
 * names are, and what the two mechanical files at its root say.
 *
 * <p>Written against the writer rather than against an invocation, because three of the claims here
 * cannot be reached from one. A fixture corpus produces one seed partition holding one cluster, so
 * nothing driven end to end can ask what the ordinal prefix does at the boundary between nine
 * clusters and ten; and an archive that is not there at all is the one state that tells a writer
 * which reads the archive apart from one which does not, and it cannot be arranged behind a walk
 * that has just read the same files.
 *
 * <p><b>The archive is never touched, and that is what the missing root is for.</b> ADR-104 says no
 * original is copied and none is stat-ed at write time. A tree written over a corpus root that does
 * not exist is the only mechanical form of that claim: a writer that copied an original would fail
 * for want of it, and one that checked a link before writing it would either fail or write a
 * different document, so a complete tree with every link in it is the evidence.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122). The tree is
 * one of that record's outside-facing surfaces: every word an operator reads in it renders the term,
 * and every name this project gives itself here does not. {@link
 * Deliverable#NOTHING_WAS_WRITTEN_OVER_IT} is both halves of that split on one line and is claimed
 * as such below.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("186")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class DeliverableTest {

    /** A run id of the shape the ledger mints: sixty-four hexadecimal characters (ADR-048). */
    private static final String RUN_ID = "a".repeat(48) + "0123456789abcdef";

    /** The walk the run read, which is what says when the links in the tree were true (ADR-104). */
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

    /**
     * A label carrying what a Docling title can carry and a table cannot: a line break and a pipe.
     *
     * <p>Neither is exotic. A title is read off the document's own first heading, which wraps in the
     * document it came from, and a pipe is an ordinary character in one.
     */
    private static final String A_NAME_WITH_A_BREAK_AND_A_PIPE = "Fire Suppression\nRetrofits | Phase 2";

    /** That same name as a reader should meet it: on one line, with the pipe left as a pipe. */
    private static final String THAT_NAME_MADE_SAFE = "Fire Suppression Retrofits \\| Phase 2";

    /** How many clusters the hostile-name fixture arranges, which is how many rows its table must keep. */
    private static final int TWO_CLUSTERS = 2;

    /** A generated heading carrying a break of its own, which the model is under no obligation to avoid. */
    private static final String A_HEADING_WITH_A_BREAK = "Suppression Retrofits,\n2018 to 2021";

    /** That heading as a cell should carry it. */
    private static final String THAT_HEADING_MADE_SAFE = "Suppression Retrofits, 2018 to 2021";

    /** A label carrying the bracket that would close a link early, which a filename stem can hold. */
    private static final String A_NAME_WITH_A_BRACKET = "Retrofits [2019] and after";

    /** And that one as link text: the brackets escaped, so the whole name stays inside the link. */
    private static final String THAT_NAME_AS_LINK_TEXT = "Retrofits \\[2019\\] and after";

    /** Enough of that escaped name to find its row, rather than a bare word repeated elsewhere. */
    private static final String A_NAME_WITH_A_BRACKET_ESCAPED_START = "Retrofits \\[2019\\]";

    /** The first place in an order, which both levels count from (ADR-112). */
    private static final int FIRST_PLACE = 1;

    /** The second place in that order. */
    private static final int SECOND_PLACE = 2;

    /**
     * How many clusters it takes for the prefix to need two digits: ten, because nine is the last
     * count a single digit can order and the tenth is the one a file browser would sort between the
     * first and the second without a prefix.
     */
    private static final int TEN_CLUSTERS = 10;

    /** How many partitions the same boundary needs at the top level, for the same reason. */
    private static final int TEN_PARTITIONS = 10;

    /** The prefix the tenth of ten has to carry, padded to the width of the count at its level. */
    private static final String THE_TENTH_PREFIX = "10-";

    /** The prefix the first of ten has to carry: padded to the same width, and not "1-". */
    private static final String THE_FIRST_OF_TEN_PREFIX = "01-";

    /** The prefix the only member of a level of one carries: one digit, because the count is one. */
    private static final String THE_ONLY_ONE_PREFIX = "1-";

    /** A corpus root that is not on this machine, so anything reaching the archive fails or shows. */
    private static final String AN_ARCHIVE_THAT_IS_NOT_THERE =
            Path.of("D:", "an-archive-that-was-never-mounted").toString();

    /** The archive the rest of these fixtures were walked from, recorded once in the index. */
    private static final String THE_ARCHIVE_ROOT = Path.of("D:", "archive").toString();

    /** What the tree holds when one cluster was written over: the index, the listing, and one page. */
    private static final int THE_INDEX_THE_LISTING_AND_ONE_PAGE = 3;

    /** The one line above the listing's rows, which names the columns rather than carrying a document. */
    private static final int THE_HEADER_ROW = 1;

    /** The relevance score of the first survivor listed, which the manifest carries as it stands. */
    private static final double A_SCORE = 0.81;

    /** The score of the second, lower so the two are told apart in the listing. */
    private static final double A_LOWER_SCORE = 0.42;

    /** Every column the manifest carries, in order, and the header row it writes them under. */
    private static final String MANIFEST_HEADER = "occurrence_id,path,content_hash,winning_seed,"
            + "relevance_score,seed_partition,cluster,partition_order,cluster_order";

    /** How many rows a manifest listing two survivors carries beneath that header. */
    private static final int TWO_SURVIVORS = 2;

    /** How many columns the header names, and therefore how many every row has to read back as. */
    private static final int NINE_COLUMNS = 9;

    /** Where the document's own path sits among those columns, counting from zero: second of nine. */
    private static final int THE_PATH_COLUMN = 1;

    /** Where its seed partition's place in the order sits: eighth of nine. */
    private static final int THE_PARTITION_ORDER_COLUMN = 7;

    /** And where its own place within that partition sits: ninth and last. */
    private static final int THE_CLUSTER_ORDER_COLUMN = 8;


    /** Where the seed its group sits under is named: sixth of nine. */
    private static final int THE_SEED_PARTITION_COLUMN = 5;

    /** How many rows a manifest listing one survivor carries beneath that header. */
    private static final int ONE_SURVIVOR = 1;

    /**
     * A document whose own name carries a comma and a pair of quotes. Both are legal in a filename on
     * the filesystem this project is built for, so neither is a contrivance.
     */
    private static final String A_PATH_PUNCTUATED_LIKE_THE_FORMAT =
            "reports/2019/retrofit, phase \"two\".pdf";

    /** A seed whose own name carries a comma too, so the column naming it is exercised as well. */
    private static final String A_SEED_PATH_WITH_A_COMMA = "seeds/Industrial Safety, Standards.docx";

    /** The table the index opens each partition with, whose header is settled rather than an author's. */
    private static final String INDEX_TABLE_HEADER = "| Group | Documents | Written up as |";

    /** What the index says in place of a title for a cluster nothing was written over (ADR-111). */
    private static final String THE_HOLE = "*nothing was written over this group*";

    /** What the profile key naming the archive's own documents was set to when the run read it. */
    private static final String THE_SEED_FOLDER_VALUE = "D:/archive/seeds";

    /** The name of that key, which the index states beside its value so the tree stands alone. */
    private static final String THE_SEED_FOLDER_KEY = "seedFolder";

    @Test
    @Story("The tree is named after the run that produced it")
    @DisplayName("The tree lands in the working directory under the full name of the run that wrote it")
    void namesTheTreeAfterTheRunThatProducedIt(@TempDir Path workingDirectory) {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, FIRST_PLACE, FIRST_PLACE)),
                List.of(writingFor(FIRST_ORDINAL)),
                survivors(FIRST_ORDINAL));

        claim(
                "the tree sits under the working directory rather than anywhere the operator has to name:"
                        + " the directory the database is in is the one thing that moves what this tool"
                        + " writes, and a second place to set would be a value on a path that exists to"
                        + " have as few as possible",
                () -> assertThat(tree).startsWithRaw(workingDirectory));
        claim(
                "and the directory is named with the run's whole name, all " + RUN_ID.length()
                        + " characters of it: the name is worked out from what the run read, so an"
                        + " unchanged re-run writes this very path and a changed one writes a new tree"
                        + " beside it -- and a shortened name would be a name two runs could share",
                () -> assertThat(tree.getFileName().toString()).isEqualTo(RUN_ID));
        claim(
                "the listing and the index both sit at its root, which is what the reader opens and what"
                        + " whatever comes next loads",
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
                        + " the page inside it after the name stage 6a derived, never after the heading"
                        + " the model wrote, because a path that depended on the writing succeeding would"
                        + " not exist for a group nothing was written over",
                () -> assertThat(tree.resolve(THE_ONLY_ONE_PREFIX + SEED_STEM_SLUGGED)
                                .resolve(THE_ONLY_ONE_PREFIX + THE_LABEL_SLUGGED + ".md"))
                        .isRegularFile());
    }

    /**
     * The ten clusters here are ordinals 1 to 10, and the survivors are given the first of them rather
     * than a number picked independently: the arrangement is total over the survivors it was built
     * from, so a survivor naming a cluster the arrangement does not carry stops the writer outright
     * (ADR-105). Nothing in this test is about which cluster the two survivors sit in — it reads file
     * names and never the manifest — but a fixture that states an impossible arrangement is not
     * entitled to be written at all.
     */
    @Test
    @Story("A file browser sorting by name agrees with the order that was approved")
    @DisplayName("With ten groups under one seed, every file name is padded to two digits")
    void padsEveryClusterToTheWidthOfItsOwnLevelsCount(@TempDir Path workingDirectory) throws IOException {
        List<RecordedCluster> arrangement = new ArrayList<>();
        for (int place = FIRST_PLACE; place <= TEN_CLUSTERS; place++) {
            arrangement.add(aCluster(place, THE_LABEL + " " + place, FIRST_PLACE, place));
        }

        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                arrangement,
                List.of(),
                survivors(arrangement.getFirst().cluster().ordinal()));

        List<String> names = fileNamesUnder(tree.resolve(THE_ONLY_ONE_PREFIX + SEED_STEM_SLUGGED));
        claim(
                "all " + TEN_CLUSTERS + " groups have a file of their own beneath the one seed they sit"
                        + " under, which is what the padding claim below is about -- asserted first, so a"
                        + " level that came out short fails saying so",
                () -> assertThat(names).hasSize(TEN_CLUSTERS));
        claim(
                "the first of " + TEN_CLUSTERS + " is prefixed \"" + THE_FIRST_OF_TEN_PREFIX + "\" rather"
                        + " than \"" + THE_ONLY_ONE_PREFIX + "\": a file browser sorts by name, and one"
                        + " digit beside two would put the tenth between the first and the second --"
                        + " stating an order in the index and contradicting it in the listing beside it",
                () -> assertThat(names).anyMatch(name -> name.startsWith(THE_FIRST_OF_TEN_PREFIX)));
        claim(
                "and the tenth carries \"" + THE_TENTH_PREFIX + "\", so the last of the ten sorts last",
                () -> assertThat(names).anyMatch(name -> name.startsWith(THE_TENTH_PREFIX)));
    }

    @Test
    @Story("A file browser sorting by name agrees with the order that was approved")
    @DisplayName("Each level is padded to its own count, so ten seeds holding one group each are not padded alike")
    void padsEachLevelToItsOwnCount(@TempDir Path workingDirectory) throws IOException {
        List<RecordedCluster> arrangement = new ArrayList<>();
        List<ListedSurvivor> survivors = new ArrayList<>();
        for (int place = FIRST_PLACE; place <= TEN_PARTITIONS; place++) {
            OccurrenceId seed = new OccurrenceId(place);
            arrangement.add(new RecordedCluster(
                    new ArrangedCluster(seed, FIRST_ORDINAL, DOCUMENTS_IN_THE_CLUSTER, place, FIRST_PLACE),
                    new ClusterLabel(THE_LABEL)));
            survivors.add(new ListedSurvivor(
                    new OccurrenceId(100L + place),
                    new OccurrencePath("reports/" + place + ".pdf"),
                    "hash-" + place,
                    seed,
                    "seeds/seed " + place + ".docx",
                    FIRST_ORDINAL,
                    A_SCORE));
        }

        Path tree = Deliverable.writeTo(
                workingDirectory, provenance(THE_SEED_FOLDER_VALUE), arrangement, List.of(), survivors);

        claim(
                "the first of " + TEN_PARTITIONS + " seed directories is prefixed \""
                        + THE_FIRST_OF_TEN_PREFIX + "\", because there are ten of them at that level",
                () -> assertThat(fileNamesUnder(tree))
                        .anyMatch(name -> name.startsWith(THE_FIRST_OF_TEN_PREFIX)));
        claim(
                "while the single group inside each of them is prefixed \"" + THE_ONLY_ONE_PREFIX + "\":"
                        + " the padding is to the count at that level and not to the deepest count in the"
                        + " tree, so a seed holding one group is not made to look like one holding ten",
                () -> assertThat(fileNamesUnder(tree.resolve(THE_FIRST_OF_TEN_PREFIX + "seed-1")))
                        .allMatch(name -> name.startsWith(THE_ONLY_ONE_PREFIX)));
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

        Path tree = Deliverable.writeTo(
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
                () -> assertThat(index.indexOf(THE_NAME_THAT_SORTS_LAST))
                        .isLessThan(index.indexOf(THE_NAME_THAT_SORTS_FIRST)));
        claim(
                "and the file names carry the same sequence, so the listing on disk says what the index"
                        + " says",
                () -> assertThat(fileNamesUnder(tree.resolve(THE_ONLY_ONE_PREFIX + SEED_STEM_SLUGGED)))
                        .anyMatch(name -> name.startsWith(FIRST_PLACE + "-zinc-coating-trials")));
    }

    @Test
    @Story("A group nothing was written over is a hole a reader can see")
    @DisplayName("A group left unwritten keeps its place in the index and carries no link")
    @Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
    void rendersAnUnwrittenClusterAsAnEntryWithNoLink(@TempDir Path workingDirectory) throws IOException {
        RecordedCluster unwritten = aCluster(FIRST_ORDINAL, THE_LABEL, FIRST_PLACE, FIRST_PLACE);
        RecordedCluster written = aCluster(A_LATER_ORDINAL, THE_NAME_THAT_SORTS_FIRST, FIRST_PLACE, SECOND_PLACE);

        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(unwritten, written),
                List.of(writingFor(A_LATER_ORDINAL, 12L)),
                membersOfBothClusters());

        String entry = lineOf(tree.resolve(Deliverable.INDEX_FILE_NAME), THE_LABEL);
        claim(
                "the group appears under the name stage 6a derived for it, so the gap is legible as a gap"
                        + " rather than as a group that was never there -- which is why there is no sixth"
                        + " page anywhere reporting this, the tree being the report",
                () -> assertThat(entry).contains(THE_LABEL));
        claim(
                "and the entry carries no link, though the group does keep a page of its own: the page"
                        + " is what holds the group's slot in the directory listing, so the listing still"
                        + " agrees with the order that was approved and filling the gap later renames"
                        + " nothing -- and the index withholds the link because there is nothing written"
                        + " on that page to send a reader to",
                () -> assertThat(entry).doesNotContain("]("));
        claim(
                "it says plainly that nothing was written over it, rather than leaving a blank a reader"
                        + " has to interpret",
                () -> assertThat(entry).contains(THE_HOLE));
        claim(
                "the page it would have had is there all the same, in the slot the order gave it: a"
                        + " directory listing is the second place the approved order is stated, and a"
                        + " missing file would leave that listing saying something the index does not",
                () -> assertThat(tree.resolve(THE_ONLY_ONE_PREFIX + SEED_STEM_SLUGGED)
                                .resolve(THE_ONLY_ONE_PREFIX + THE_LABEL_SLUGGED + ".md"))
                        .isRegularFile());
        claim(
                "and it keeps its place, in front of the group that was written: a group that sank to the"
                        + " bottom for being unwritten would move every file beneath it the moment a later"
                        + " invocation filled it in",
                () -> assertThat(Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME))
                                .indexOf(THE_LABEL))
                        .isLessThan(Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME))
                                .indexOf(THE_NAME_THAT_SORTS_FIRST)));
    }

    @Test
    @Story("A name out of the archive cannot rewrite the page it is listed on")
    @DisplayName("A group named across two lines is listed on one, and the table below it survives")
    @Issue("246")
    void keepsTheTableWholeWhenANameCarriesALineBreakOrAPipe(@TempDir Path workingDirectory) throws IOException {
        RecordedCluster hostile = aCluster(FIRST_ORDINAL, A_NAME_WITH_A_BREAK_AND_A_PIPE, FIRST_PLACE, FIRST_PLACE);
        RecordedCluster after = aCluster(A_LATER_ORDINAL, THE_NAME_THAT_SORTS_FIRST, FIRST_PLACE, SECOND_PLACE);

        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(hostile, after),
                List.of(writingOverTheHostileCluster()),
                membersOfBothClusters());

        String index = Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME));

        claim(
                "the name is listed on one line with its pipe escaped, because a line break ends the"
                        + " table where it stands and a pipe ends the cell: a title is read off the"
                        + " document's own heading, which wrapped in the document it came from, and"
                        + " neither character says anything about the group it names",
                () -> assertThat(index).contains(THAT_NAME_MADE_SAFE));
        claim(
                "and the heading the model wrote is folded the same way, in the cell beside it: it comes"
                        + " from further away than the name does and is no more obliged to be one line",
                () -> assertThat(index).contains(THAT_HEADING_MADE_SAFE));
        claim(
                "so every line of the table is a row of it -- which is the claim that separates a table"
                        + " that survived from one that ended early. A break does not cost a row: it"
                        + " splits one in two, leaving a remainder that no longer opens with a pipe and"
                        + " that a renderer reads as the prose the rest of the table becomes",
                () -> assertThat(linesOfTheTableIn(index)).allSatisfy(line -> assertThat(line)
                        .startsWith("|")
                        .endsWith("|")));
        claim(
                "and both of the " + TWO_CLUSTERS + " groups are still listed under it, so the table is"
                        + " whole rather than merely well-formed: a table truncated to its header would"
                        + " satisfy the claim above and list nothing at all",
                () -> assertThat(index).contains(THAT_NAME_MADE_SAFE).contains(THE_NAME_THAT_SORTS_FIRST));
    }

    @Test
    @Story("A name out of the archive cannot rewrite the page it is listed on")
    @DisplayName("A group whose name holds a bracket is linked by the whole name, not a fragment of it")
    @Issue("246")
    void keepsTheLinkWholeWhenANameCarriesABracket(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, A_NAME_WITH_A_BRACKET, FIRST_PLACE, FIRST_PLACE)),
                List.of(writingFor(FIRST_ORDINAL)),
                survivors(FIRST_ORDINAL));

        String row = lineOf(tree.resolve(Deliverable.INDEX_FILE_NAME), A_NAME_WITH_A_BRACKET_ESCAPED_START);

        claim(
                "the brackets in the name are escaped, so the link carries the whole name: an unescaped"
                        + " ] closes the link where it appears, leaving a reader clicking a fragment of"
                        + " the name with the rest of it sitting beside a bare path",
                () -> assertThat(row).contains("[" + THAT_NAME_AS_LINK_TEXT + "]("));
        claim(
                "and what it points at is a file that is really there: the link's text and its"
                        + " destination are derived from the one name, so a rule that escaped the text"
                        + " and left the path behind would send a reader to nothing at all",
                () -> assertThat(tree.resolve(destinationOf(row))).isRegularFile());
    }

    @Test
    @Story("Everything the operator reads is in their words, and everything we name is in ours")
    @DisplayName("The index speaks of groups, and the constants behind it speak of clusters")
    @Link(name = "ADR-122", url = Adr.THE_VOCABULARY_BINDS_OUR_NAMES_NOT_RENDERED_PROSE, type = "adr")
    void rendersTheTermForTheReaderWhileNamingClustersInTheCode(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, FIRST_PLACE, FIRST_PLACE)),
                List.of(),
                survivors(FIRST_ORDINAL));

        claim(
                "the table a reader meets is headed in plain words -- \"" + INDEX_TABLE_HEADER + "\" --"
                        + " because the person holding this tree cannot open the glossary that would tell"
                        + " them our word for a group, and the everyday sense of ours is the one that"
                        + " glossary spends a sentence refusing",
                () -> assertThat(Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME)))
                        .contains(INDEX_TABLE_HEADER));
        claim(
                "and the sentence standing in for a piece of writing that was never produced is in the"
                        + " same plain words, though the constant holding it is one of ours and is named"
                        + " in ours: a name and the sentence it holds are answered by different rules, and"
                        + " sitting on one line of source joins them not at all",
                () -> assertThat(Deliverable.NOTHING_WAS_WRITTEN_OVER_IT).isEqualTo(THE_HOLE));
    }

    @Test
    @Story("The archive is referenced and never read at the moment the tree is written")
    @DisplayName("A tree is written whole over an archive this machine cannot reach at all")
    void writesTheWholeTreeWithoutReachingTheArchive(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                new DeliverableProvenance(
                        RUN_ID,
                        WALK,
                        AN_ARCHIVE_THAT_IS_NOT_THERE,
                        List.of(new NamedValue(THE_SEED_FOLDER_KEY, THE_SEED_FOLDER_VALUE))),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, FIRST_PLACE, FIRST_PLACE)),
                List.of(writingFor(FIRST_ORDINAL)),
                survivors(FIRST_ORDINAL));

        claim(
                "the archive really is out of reach, which is what the claims below rest on -- asserted"
                        + " first, so a machine that happened to have such a directory fails here rather"
                        + " than passing three claims that checked nothing",
                () -> assertThat(Path.of(AN_ARCHIVE_THAT_IS_NOT_THERE)).doesNotExist());
        claim(
                "the tree was still written whole: nothing here asks the archive whether a document is"
                        + " still where it was, because a document that has moved has no verdict it could"
                        + " be recorded as, and asking would turn the last stage into a second, partial"
                        + " look at the corpus",
                () -> assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).exists());
        claim(
                "every one of the " + TWO_SURVIVORS + " documents is listed, rather than the reachable"
                        + " ones: the listing states the arrangement, and an arrangement filtered by what"
                        + " one machine could see at one moment is a different thing altogether",
                () -> assertThat(rowsOf(tree)).hasSize(TWO_SURVIVORS));
        claim(
                "and nothing was copied into the tree -- it holds "
                        + THE_INDEX_THE_LISTING_AND_ONE_PAGE + " files, being the index, the listing and"
                        + " the one group's own page, and nothing else: copying hundreds of gigabytes would"
                        + " be the first thing in this system to move a document rather than arrange it",
                () -> assertThat(everyFileUnder(tree)).hasSize(THE_INDEX_THE_LISTING_AND_ONE_PAGE));
    }

    @Test
    @Story("Whatever comes after the hand-off can be built without reading the prose")
    @DisplayName("The listing carries every document with its place in the archive and its place in the order")
    void carriesEverySurvivorIntoTheListingWithPathsRelativeToTheArchive(@TempDir Path workingDirectory)
            throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, SECOND_PLACE, SECOND_PLACE)),
                List.of(writingFor(FIRST_ORDINAL)),
                survivors(FIRST_ORDINAL));
        List<String> lines = Files.readAllLines(tree.resolve(Deliverable.MANIFEST_FILE_NAME));

        claim(
                "the listing is headed by the columns it carries, named as the ledger names them and not"
                        + " in the reader's plain words: this file is loaded into a table by a program"
                        + " before any person reads it, and a column nobody can select without quoting"
                        + " costs that program a special case for no reader's benefit",
                () -> assertThat(lines.getFirst()).isEqualTo(MANIFEST_HEADER));
        claim(
                "and all " + TWO_SURVIVORS + " documents are listed beneath it -- " + TWO_SURVIVORS
                        + " rows and the " + THE_HEADER_ROW + " header line above them -- which is every"
                        + " document arranged into the group rather than the ones its writing happened to"
                        + " mention",
                () -> assertThat(lines).hasSize(TWO_SURVIVORS + THE_HEADER_ROW));
        claim(
                "each path is the one beneath the archive's own root rather than a whole path from a"
                        + " drive letter down: the root is stated once in the index, so an archive that"
                        + " moves is re-pointed by replacing one line rather than every row here",
                () -> assertThat(rowsOf(tree))
                        .allSatisfy(row -> assertThat(columnsOf(row)[THE_PATH_COLUMN])
                                .doesNotContain(THE_ARCHIVE_ROOT)
                                .startsWith("reports/")));
        claim(
                "and every row carries both places the arrangement gave its group -- "
                        + SECOND_PLACE + " among the seeds and " + SECOND_PLACE + " among that seed's"
                        + " groups -- so a consumer rebuilding anything from this file has the order"
                        + " without parsing the index or falling back to alphabetical",
                () -> assertThat(rowsOf(tree)).allSatisfy(row -> {
                    assertThat(columnsOf(row)[THE_PARTITION_ORDER_COLUMN]).isEqualTo(String.valueOf(SECOND_PLACE));
                    assertThat(columnsOf(row)[THE_CLUSTER_ORDER_COLUMN]).isEqualTo(String.valueOf(SECOND_PLACE));
                }));
    }

    @Test
    @Story("Whatever comes after the hand-off can be built without reading the prose")
    @DisplayName("A document whose name carries a comma still reads back as one column, not two")
    void quotesAValueThatWouldOtherwiseSplitTheRowInTwo(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, SECOND_PLACE, SECOND_PLACE)),
                // Written over the one document this fixture holds, because the page numbers its
                // membership from what the call carried (ADR-133) and this cluster holds one.
                List.of(writingFor(FIRST_ORDINAL, new OccurrenceId(10))),
                List.of(new ListedSurvivor(
                        new OccurrenceId(10),
                        new OccurrencePath(A_PATH_PUNCTUATED_LIKE_THE_FORMAT),
                        "3a7b",
                        THE_SEED,
                        A_SEED_PATH_WITH_A_COMMA,
                        FIRST_ORDINAL,
                        A_SCORE)));
        List<String> rows = rowsOf(tree);

        claim(
                "the one document listed still reads back as the " + NINE_COLUMNS + " columns the header"
                        + " names, though its own name carries a comma: a value written straight through"
                        + " would have been read as two columns, moving every value after it one place"
                        + " along and leaving the last column with nothing in it",
                () -> assertThat(rows).hasSize(ONE_SURVIVOR).allSatisfy(row -> assertThat(columnsOf(row))
                        .hasSize(NINE_COLUMNS)));
        claim(
                "and the path reads back as the one it was given, punctuation and all, rather than the"
                        + " rendering it was written under: what a program loads from this file is the"
                        + " name the document actually has, or it cannot go and find it",
                () -> assertThat(columnsOf(rows.getFirst())[THE_PATH_COLUMN])
                        .isEqualTo(A_PATH_PUNCTUATED_LIKE_THE_FORMAT));
        claim(
                "and so does the seed naming the group it sits under, which carries a comma of its own --"
                        + " the two columns holding a name a person chose are the two this can happen to,"
                        + " and neither is left to chance",
                () -> assertThat(columnsOf(rows.getFirst())[THE_SEED_PARTITION_COLUMN])
                        .isEqualTo(A_SEED_PATH_WITH_A_COMMA));
        claim(
                "and the row really is written in the format rather than merely read back in it: the"
                        + " punctuated name arrives wrapped, and the pair of quotes inside it doubled,"
                        + " which is what a reader that is not this one will expect to find",
                () -> assertThat(rows.getFirst())
                        .contains("\"reports/2019/retrofit, phase \"\"two\"\".pdf\""));
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

    /** The writing kept against the hostile-name cluster, whose heading carries a break of its own. */
    private static RecordedSynthesisDoc writingOverTheHostileCluster() {
        return new RecordedSynthesisDoc(
                THE_SEED,
                FIRST_ORDINAL,
                new SynthesisDoc(
                        A_HEADING_WITH_A_BREAK,
                        THE_PROSE,
                        List.of(new OccurrenceId(10), new OccurrenceId(11))));
    }

    /** The destination of the one link in {@code row}, as a path relative to the tree's own root. */
    private static String destinationOf(String row) {
        int opens = row.indexOf("](");
        return row.substring(opens + 2, row.indexOf(')', opens));
    }

    /**
     * Every line of the index that belongs to a table: the header, its separator and its rows.
     *
     * <p>Found by where they sit rather than by what they start with, because what a broken table
     * leaves behind is a line that does <em>not</em> start with a pipe, and a filter keyed on the pipe
     * would drop exactly the evidence. A table runs from its separator to the next blank line.
     */
    private static List<String> linesOfTheTableIn(String index) {
        List<String> lines = index.lines().toList();
        List<String> inTables = new ArrayList<>();
        boolean inTable = false;
        for (String line : lines) {
            if (line.startsWith("|---")) {
                inTable = true;
                inTables.add(line);
            } else if (inTable && line.isBlank()) {
                inTable = false;
            } else if (inTable) {
                inTables.add(line);
            }
        }
        return inTables;
    }

    /** The writing stage 6b kept against the cluster identified by {@code ordinal}, over both its documents. */
    private static RecordedSynthesisDoc writingFor(int ordinal) {
        return writingFor(ordinal, 10L);
    }

    /**
     * The same, over the two documents numbered from {@code firstOccurrenceId}.
     *
     * <p>Which documents the call carried is stated rather than counted, because the page numbers its
     * membership from that list (ADR-133): a fixture naming documents the cluster does not hold is
     * the integrity failure the writer refuses, so each of these names the survivors beside it.
     */
    private static RecordedSynthesisDoc writingFor(int ordinal, long firstOccurrenceId) {
        return writingFor(ordinal, new OccurrenceId(firstOccurrenceId), new OccurrenceId(firstOccurrenceId + 1));
    }

    /** The same again, over exactly the documents named, in the order the call's ordinals were minted. */
    private static RecordedSynthesisDoc writingFor(int ordinal, OccurrenceId... sent) {
        return new RecordedSynthesisDoc(
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

    /**
     * The members of both clusters the hole-vs-link fixture arranges.
     *
     * <p>The cluster that was written over needs a membership of its own, because a citation in its
     * writing resolves against that membership (ADR-109): a fixture that wrote over a cluster holding
     * no documents would be asking the writer to resolve a citation against nothing, which is a state
     * the pipeline never produces.
     */
    private static List<ListedSurvivor> membersOfBothClusters() {
        List<ListedSurvivor> members = new ArrayList<>(survivors(FIRST_ORDINAL));
        members.addAll(survivors(A_LATER_ORDINAL, 12L));
        return List.copyOf(members);
    }

    /** The names of everything directly beneath {@code directory}, in no particular order. */
    private static List<String> fileNamesUnder(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> entry.getFileName().toString()).toList();
        }
    }

    /** Every file anywhere in the tree, which is what a claim about copying nothing counts. */
    private static List<Path> everyFileUnder(Path tree) throws IOException {
        try (Stream<Path> entries = Files.walk(tree)) {
            return entries.filter(Files::isRegularFile).toList();
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
     * One manifest row split into the nine columns the header names, read as a consumer of the format
     * would read it.
     *
     * <p>Split on the format rather than on the character: a path may legally carry a comma, in which
     * case the field arrives quoted, and a splitter that cut on every comma would report ten columns
     * for that row and silently move every value after the path one place along. The claim this file
     * exists for is that a program can load it into a table without parsing anything first — a fixture
     * that could not itself read a quoted field would be claiming that against the one row where it
     * matters and getting the wrong answer.
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

    /** The one line of {@code file} mentioning {@code text}, so a claim is about an entry and not a page. */
    private static String lineOf(Path file, String text) throws IOException {
        return Files.readAllLines(file).stream()
                .filter(line -> line.contains(text))
                .findFirst()
                .orElse("");
    }
}
