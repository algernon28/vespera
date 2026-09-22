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

    /**
     * A label carrying a backslash immediately before a pipe, which is the one arrangement of
     * characters that defeats a cell rule escaping the pipe alone (ADR-134).
     *
     * <p>Not exotic: the two values a cell carries are a document's own title and what the model
     * wrote, and neither is under any obligation to avoid a character a Windows path is full of.
     */
    private static final String A_NAME_WITH_A_BACKSLASH_BEFORE_A_PIPE = "Retrofits \\| Phase 2";

    /**
     * That name made safe: the backslash doubled first, then the pipe escaped against the doubled
     * pair rather than against the original.
     */
    private static final String THAT_NAME_MADE_SAFE_BACKSLASH_FIRST = "Retrofits \\\\\\| Phase 2";

    /** How many columns the index table has, which is how many a row must still have afterwards. */
    private static final int THREE_COLUMNS = 3;

    /**
     * A name carrying the two characters a renderer reads as markup: an opening tag and an ampersand
     * (ADR-136). Both are ordinary in a title a document gave itself.
     */
    private static final String A_NAME_WITH_A_TAG_AND_AN_AMPERSAND = "Retrofits <b>Phase 2 R&D";

    /** That name made safe: each of the two escaped with a backslash, and nothing else touched. */
    private static final String THAT_NAME_WITH_BOTH_ESCAPED = "Retrofits \\<b>Phase 2 R\\&D";

    /**
     * A cluster title carrying the same two characters, which the model is under no more obligation to
     * avoid than a document is: it lands in a cell of its own, reached by no link.
     */
    private static final String A_TITLE_WITH_A_TAG_AND_AN_AMPERSAND = "Retrofits <draft> R&D, 2018";

    /** That title as the cell has to carry it: each of the two behind a backslash, nothing else touched. */
    private static final String THAT_TITLE_WITH_BOTH_ESCAPED = "Retrofits \\<draft> R\\&D, 2018";

    /**
     * Which piece of a split row states what the cluster was written up as: the third, because the leading
     * pipe leaves an empty piece in front of the first column.
     */
    private static final int THE_WRITTEN_UP_COLUMN = 3;

    /** A seed path carrying a tag, which lands in the one position that escaped nothing (ADR-136). */
    private static final String A_SEED_PATH_WITH_A_TAG = "seeds/<draft> Safety & Standards.docx";

    /** That seed path as its heading must read, folded and with both characters escaped. */
    private static final String THAT_SEED_PATH_ESCAPED = "seeds/\\<draft> Safety \\& Standards.docx";

    /**
     * A cluster label that is itself a whole inline link. Not exotic: NTFS permits every character in
     * it, so a document can really be named this, and a Docling title is arbitrary text off a title
     * block.
     */
    private static final String A_NAME_THAT_IS_ITSELF_A_LINK = "[click me](evil.md) report";

    /** That name as a cell has to carry it, with both brackets behind a backslash and nothing else moved. */
    private static final String THAT_NAME_WITH_ITS_BRACKETS_ESCAPED = "\\[click me\\](evil.md) report";

    /**
     * That escaped name standing as a whole cell, which is what tells its row apart from the heading
     * above it: a partition heading naming the seed carries the same name inside a longer one, and a
     * lookup by the name alone would find the heading and read its columns.
     */
    private static final String THAT_NAME_AS_A_WHOLE_CELL = "| " + THAT_NAME_WITH_ITS_BRACKETS_ESCAPED + " |";

    /** A seed path of the same shape, which is the value the index's own partition heading is given. */
    private static final String A_SEED_PATH_THAT_IS_ITSELF_A_LINK = "seeds/[click me](evil.md) report.docx";

    /** That seed path as its heading has to read. */
    private static final String THAT_SEED_PATH_WITH_ITS_BRACKETS_ESCAPED =
            "seeds/\\[click me\\](evil.md) report.docx";

    /**
     * A cluster title that is itself an image, which is the same two brackets with a bang in front of
     * them. What the model answers is under no filesystem constraint at all, so this is the freest of
     * the three values the index carries.
     */
    private static final String A_TITLE_THAT_IS_ITSELF_AN_IMAGE = "Survey ![shot](https://example.com/x.png) 2019";

    /** That title as a cell has to carry it: the bang is harmless once the bracket after it is not live. */
    private static final String THAT_TITLE_WITH_ITS_BRACKETS_ESCAPED =
            "Survey !\\[shot\\](https://example.com/x.png) 2019";

    /**
     * A doubled backslash in front of a bracket, which is what a link-text rule would write if it
     * escaped the bracket the cell rule had already escaped. It reads as one literal backslash followed
     * by a live bracket, and the link does not survive it.
     */
    private static final String A_BRACKET_ESCAPED_TWICE = "\\\\[";

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
    @Link(name = "ADR-134", url = Adr.A_BREAK_IS_FOLDED_AND_THREE_ESCAPING_RULES_STAND, type = "adr")
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
    @Link(name = "ADR-134", url = Adr.A_BREAK_IS_FOLDED_AND_THREE_ESCAPING_RULES_STAND, type = "adr")
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
    @Story("A name out of the archive cannot rewrite the page it is listed on")
    @DisplayName("A group whose name holds a backslash beside a pipe still occupies one cell")
    @Issue("249")
    @Link(name = "ADR-134", url = Adr.A_BREAK_IS_FOLDED_AND_THREE_ESCAPING_RULES_STAND, type = "adr")
    void keepsTheCellWholeWhenANameCarriesABackslashBeforeAPipe(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, A_NAME_WITH_A_BACKSLASH_BEFORE_A_PIPE, FIRST_PLACE, FIRST_PLACE)),
                List.of(),
                survivors(FIRST_ORDINAL));

        String row = lineOf(tree.resolve(Deliverable.INDEX_FILE_NAME), THAT_NAME_MADE_SAFE_BACKSLASH_FIRST);

        claim(
                "the backslash is doubled before the pipe is escaped, so the escape lands on the pipe"
                        + " rather than on the backslash in front of it: escaping the pipe alone would"
                        + " leave a doubled backslash -- which reads as one literal backslash -- followed"
                        + " by a delimiter that is still live",
                () -> assertThat(row).contains(THAT_NAME_MADE_SAFE_BACKSLASH_FIRST));
        claim(
                "so the row still has the " + THREE_COLUMNS + " columns the table is headed with, read"
                        + " the way a renderer reads it -- a pipe preceded by an even number of"
                        + " backslashes divides, an odd number escapes. The count is "
                        + (THREE_COLUMNS + 2) + " because the leading and trailing pipes each leave an"
                        + " empty piece outside the table",
                () -> assertThat(cellsOf(row)).hasSize(THREE_COLUMNS + 2));
        claim(
                "and the name is the whole of the first column rather than the head of it, so nothing"
                        + " the name carries has been read as the start of the next column",
                () -> assertThat(cellsOf(row).get(1)).isEqualTo(THAT_NAME_MADE_SAFE_BACKSLASH_FIRST));
    }

    @Test
    @Story("A name out of the archive cannot rewrite the page it is listed on")
    @DisplayName("A group named with a tag keeps the tag on the page, in the table and in the heading above it")
    @Issue("251")
    @Link(name = "ADR-136", url = Adr.THE_ANGLE_BRACKET_AND_THE_AMPERSAND_ARE_ESCAPED, type = "adr")
    void keepsATagVisibleWhereverTheIndexCarriesAName(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, A_NAME_WITH_A_TAG_AND_AN_AMPERSAND, FIRST_PLACE, FIRST_PLACE)),
                List.of(writingFor(FIRST_ORDINAL, new OccurrenceId(10))),
                List.of(new ListedSurvivor(
                        new OccurrenceId(10),
                        new OccurrencePath("reports/2019/retrofit.pdf"),
                        "3a7b",
                        THE_SEED,
                        A_SEED_PATH_WITH_A_TAG,
                        FIRST_ORDINAL,
                        A_SCORE)));

        String index = Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME));

        claim(
                "the name carries its tag into the page with a backslash in front of it, so a reader sees"
                        + " the characters the document gave itself: written through, the opening tag is"
                        + " markup -- it turns the rest of the page bold where a renderer honours it, and"
                        + " is deleted outright, word and all, where a renderer sanitises instead",
                () -> assertThat(index).contains(THAT_NAME_WITH_BOTH_ESCAPED));
        claim(
                "and the ampersand beside it is escaped the same way rather than written as an entity:"
                        + " an entity would show the reader the six characters of its own spelling, and"
                        + " a bare ampersand is the one way a run such as the spelling of a copyright"
                        + " sign is read as that sign instead of as itself",
                () -> assertThat(index).doesNotContain("R&amp;D").contains("R\\&D"));
        claim(
                "and the heading naming the seed carries its tag too, which is the position that escaped"
                        + " nothing at all before: a heading is one line, so folding was its whole"
                        + " treatment, and a tag in it reached a renderer exactly as the archive spelled it",
                () -> assertThat(index).contains("## " + THAT_SEED_PATH_ESCAPED));
    }

    /**
     * The one cell whose value passes through link text on no row at all (ADR-136).
     *
     * <p><b>This pins the wiring rather than the rule.</b> The cell rule's two new escapes are already
     * driven by the test above, because {@code asLinkText} is {@code inACell} and then the brackets --
     * strike either escape out of the cell rule and that test fails. What nothing drove is the third
     * column: the title the model wrote is the one value written straight into a cell, and a column
     * folded but never escaped would satisfy every other claim in this class while carrying a live tag
     * into the table. The value is the model's own rather than the archive's, which changes nothing
     * about the hazard -- ADR-134 already treats what comes back from a call as untrusted.
     *
     * <p><b>Not the only untrusted value written into a plain cell.</b> {@code Deliverable.java:249}
     * folds and escapes the cluster's own label into column one, and where nothing was written over a
     * cluster that cell is the whole row's text with no link anywhere on it; {@code
     * keepsTheCellWholeWhenANameCarriesABackslashBeforeAPipe} is what holds that one, and turning
     * {@code inACell} there into {@code onOneLine} fails that test alone. What is singular about the
     * title is not that it reaches a cell unlinked but that it passes through link text <em>nowhere</em>
     * -- the label does, on every row the index gives a link. A cluster file is written for every cluster,
     * the unwritten one included ({@code Deliverable.java:262-268}); what varies is whether the index links
     * to that file, and on the row where it does not the label is the cell's whole text.
     */
    @Test
    @Story("A name out of the archive cannot rewrite the page it is listed on")
    @DisplayName("The name the writing gave a group keeps its tag in the column that states it")
    @Issue("251")
    @Link(name = "ADR-136", url = Adr.THE_ANGLE_BRACKET_AND_THE_AMPERSAND_ARE_ESCAPED, type = "adr")
    void keepsATagVisibleInTheColumnTheWritingNamesTheClusterIn(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(aCluster(FIRST_ORDINAL, THE_LABEL, FIRST_PLACE, FIRST_PLACE)),
                List.of(new RecordedSynthesisDoc(
                        THE_SEED,
                        FIRST_ORDINAL,
                        new SynthesisDoc(
                                A_TITLE_WITH_A_TAG_AND_AN_AMPERSAND,
                                THE_PROSE,
                                List.of(new OccurrenceId(10), new OccurrenceId(11))))),
                survivors(FIRST_ORDINAL));

        String row = lineOf(tree.resolve(Deliverable.INDEX_FILE_NAME), THE_LABEL);

        claim(
                "the column stating what the group was written up as carries the whole of that name"
                        + " with its tag escaped, and is that name rather than merely containing it: this value"
                        + " passes through link text nowhere on the page, so it is the one cell a rule"
                        + " applied where a link is formed would leave unguarded",
                () -> assertThat(cellsOf(row).get(THE_WRITTEN_UP_COLUMN)).isEqualTo(THAT_TITLE_WITH_BOTH_ESCAPED));
        claim(
                "and the ampersand in it is escaped with a backslash rather than written as an entity: an"
                        + " entity spells itself out at a reader opening the file as text, and a bare"
                        + " ampersand is how a run such as the spelling of a copyright sign comes to be"
                        + " read as that sign instead of as itself",
                () -> assertThat(row).doesNotContain("R&amp;D").contains("R\\&D"));
    }

    /**
     * The three positions a bracket reaches in the index, driven from one tree (ADR-138).
     *
     * <p><b>Two of the three escaped nothing at all before.</b> The partition heading goes through
     * {@code inAHeading} and a cell through {@code inACell}, and neither touched a bracket: a value
     * reading {@code [text](url)} was a live link in both, and one reading {@code ![alt](url)} an
     * image, in every renderer configuration measured. The third is the linked first column, which has
     * escaped the bracket since ADR-134 and is here for the opposite reason -- to hold the escape at
     * one backslash. {@code asLinkText} is {@code inACell} and nothing more once the cell rule carries
     * the brackets, and a version that escaped them a second time would write a literal backslash in
     * front of a live bracket and destroy the only link the index gives a reader.
     *
     * <p><b>Which value sits in which position, since the three do not come from one place.</b> The
     * seed path in the partition heading is a filesystem name, and NTFS permits every character in it.
     * The linked first column is the <em>label</em> and never the title: a cluster's derived label is a
     * document's own Docling title (ADR-106), arbitrary text off a title block rather than a filename,
     * so a label can carry a destination no NTFS name could hold. The third column is the generated
     * title, which is what the model answered, under no constraint whatever. The image-shaped value is
     * given as this cluster's label as well as its title deliberately: pinning the linked column takes
     * a label, because the label is what that column carries.
     *
     * <p><b>The cluster page's own heading is claimed in {@code ClusterFileTest}</b>, where the page is.
     * This class is the index, and the same rule reaches four positions across the two.
     */
    @Test
    @Story("A name out of the archive cannot rewrite the page it is listed on")
    @DisplayName("A group named with a link or an image is listed under that name rather than rendered as one")
    @Issue("258")
    @Link(name = "ADR-138", url = Adr.A_BRACKET_IS_ESCAPED_IN_EVERY_SURROUNDING_A_VALUE_IS_READ_IN, type = "adr")
    void keepsABracketedNameFromComposingALinkOfItsOwn(@TempDir Path workingDirectory) throws IOException {
        Path tree = Deliverable.writeTo(
                workingDirectory,
                provenance(THE_SEED_FOLDER_VALUE),
                List.of(
                        aCluster(FIRST_ORDINAL, A_TITLE_THAT_IS_ITSELF_AN_IMAGE, FIRST_PLACE, FIRST_PLACE),
                        aCluster(A_LATER_ORDINAL, A_NAME_THAT_IS_ITSELF_A_LINK, FIRST_PLACE, SECOND_PLACE)),
                List.of(new RecordedSynthesisDoc(
                        THE_SEED,
                        FIRST_ORDINAL,
                        new SynthesisDoc(
                                A_TITLE_THAT_IS_ITSELF_AN_IMAGE,
                                THE_PROSE,
                                List.of(new OccurrenceId(10), new OccurrenceId(11))))),
                membersUnder(A_SEED_PATH_THAT_IS_ITSELF_A_LINK));

        String index = Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME));
        String linkedRow = lineOf(tree.resolve(Deliverable.INDEX_FILE_NAME), THAT_TITLE_WITH_ITS_BRACKETS_ESCAPED);
        String plainRow =
                lineOf(tree.resolve(Deliverable.INDEX_FILE_NAME), THAT_NAME_AS_A_WHOLE_CELL);

        claim(
                "the heading naming the seed carries its brackets behind a backslash, so a name that"
                        + " spells a link is read as a name: written through, the middle of it becomes a"
                        + " link to wherever that name pointed, and the rest of the heading's own text"
                        + " goes with it",
                () -> assertThat(index).contains("## " + THAT_SEED_PATH_WITH_ITS_BRACKETS_ESCAPED));
        claim(
                "and the group's own name in the column that has no link on it does the same, and is"
                        + " that name rather than merely containing it -- this is the one cell whose"
                        + " whole text is a name nobody linked, so a rule applied where a link is being"
                        + " formed never reaches it",
                () -> assertThat(cellsOf(plainRow).get(1)).isEqualTo(THAT_NAME_WITH_ITS_BRACKETS_ESCAPED));
        claim(
                "and the name the writing gave a group keeps every word, bang and all: a bang in front"
                        + " of a live bracket is an image, and an image's words are an attribute rather"
                        + " than text, so the word between the brackets leaves the page altogether and"
                        + " the page fetches a picture from whatever host the name gave",
                () -> assertThat(cellsOf(linkedRow).get(THE_WRITTEN_UP_COLUMN))
                        .isEqualTo(THAT_TITLE_WITH_ITS_BRACKETS_ESCAPED));
        claim(
                "and neither name reaches the page as it was given, anywhere on it, which is what says"
                        + " the escape is applied to every value rather than to the ones that carry a"
                        + " link on the row",
                () -> assertThat(index)
                        .doesNotContain(A_TITLE_THAT_IS_ITSELF_AN_IMAGE)
                        .doesNotContain(A_SEED_PATH_THAT_IS_ITSELF_A_LINK));
        claim(
                "while the column that does carry a link escapes each bracket once and not twice, and"
                        + " still opens the page it names: two backslashes read as one literal backslash"
                        + " followed by a bracket that is live again, which ends the link where the name"
                        + " has a bracket and leaves a reader clicking nothing at all",
                () -> assertThat(linkedRow)
                        .contains("[" + THAT_TITLE_WITH_ITS_BRACKETS_ESCAPED + "](")
                        .doesNotContain(A_BRACKET_ESCAPED_TWICE));
        claim(
                "and the page that link names is really there, which is what says the name and the"
                        + " route are still derived from the one value: the route is read out of the"
                        + " cell rather than out of the name, because a name spelling a link of its own"
                        + " puts a second one of these in front of the real one",
                () -> assertThat(tree.resolve(theRouteOutOf(cellsOf(linkedRow).get(1)))).isRegularFile());
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

    /**
     * Where the link wrapping the whole of {@code cell} leads, as a path relative to the tree's own
     * root.
     *
     * <p>Read from the <em>last</em> {@code ](} in the cell rather than the first, and from one cell
     * rather than from the whole row, because the name inside the link may spell a bracket and a
     * parenthesis of its own: a cluster named after a link or an image carries an escaped {@code ](}
     * in the middle of the text, and a reader taking the first one would follow the name rather than
     * the route. A renderer resolves the same way, the escaped brackets being text to it.
     */
    private static String theRouteOutOf(String cell) {
        int opens = cell.lastIndexOf("](");
        return cell.substring(opens + 2, cell.indexOf(')', opens));
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

    /**
     * {@code row} divided into cells the way a Markdown renderer divides it (ADR-134): at a pipe
     * preceded by an even number of backslashes, since an odd run leaves the last backslash escaping
     * the pipe rather than itself.
     *
     * <p>Written out here rather than split on a regular expression, because the whole question this
     * helper serves is what happens to a backslash standing immediately before a delimiter, and a
     * split that looked only at the one character in front of the pipe would answer it by assuming it.
     *
     * <p>The leading and trailing pipes of a row each leave an empty piece outside the table, so a
     * three-column row yields five.
     */
    private static List<String> cellsOf(String row) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        int backslashes = 0;
        for (int i = 0; i < row.length(); i++) {
            char character = row.charAt(i);
            if (character == '|' && backslashes % 2 == 0) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
            backslashes = character == '\\' ? backslashes + 1 : 0;
        }
        cells.add(cell.toString().strip());
        return cells;
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

    /**
     * The three survivors the two-cluster bracket fixture holds, all in one partition whose seed has
     * the path given, so the index's own heading can be driven from the same call.
     */
    private static List<ListedSurvivor> membersUnder(String seedPath) {
        return List.of(
                new ListedSurvivor(
                        new OccurrenceId(10),
                        new OccurrencePath("reports/2019/retrofit.pdf"),
                        "3a7b",
                        THE_SEED,
                        seedPath,
                        FIRST_ORDINAL,
                        A_SCORE),
                new ListedSurvivor(
                        new OccurrenceId(11),
                        new OccurrencePath("reports/2021/retrofit follow-up.pdf"),
                        "9c11",
                        THE_SEED,
                        seedPath,
                        FIRST_ORDINAL,
                        A_LOWER_SCORE),
                new ListedSurvivor(
                        new OccurrenceId(12),
                        new OccurrencePath("reports/2020/retrofit review.pdf"),
                        "5e40",
                        THE_SEED,
                        seedPath,
                        A_LATER_ORDINAL,
                        A_LOWER_SCORE));
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

    /**
     * The one line of {@code file} mentioning {@code text}, so a claim is about an entry and not a page.
     *
     * <p><b>A miss fails here, in words.</b> Returning an empty line let a caller that splits the result
     * into cells die of an {@link IndexOutOfBoundsException} instead, which is the outcome ADR-052's
     * claim discipline exists to prevent: the report showed an error where it owed a sentence, and the
     * sentence it owed was about the page, not about an index into a list. Failing here names the file
     * that was read and the text that was looked for in it.
     */
    private static String lineOf(Path file, String text) throws IOException {
        List<String> carrying = Files.readAllLines(file).stream()
                .filter(line -> line.contains(text))
                .toList();
        assertThat(carrying)
                .withFailMessage(
                        "a line of %s was expected to carry %s, and no line of it does, so there is no"
                                + " entry for the claims below to be about",
                        file.getFileName(), text)
                .isNotEmpty();
        return carrying.getFirst();
    }
}
