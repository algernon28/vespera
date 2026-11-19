package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.GATE_SEALS_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.HINGES_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.MOTORS_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.MOTOR_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.PUMPING;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.PUMPING_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.PUMPING_NAME;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.SEALS_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.SLUICES;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.SLUICES_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.THE_LINKED_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.OverviewUnwritten;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The HTML tree draws a topic as a page of its own, {@code index.html} in its folder, carrying its overview above its
 * table, and a crowded topic's subtopics as a folder and a page each, its clusters a level down and numbered over the
 * topic (ADR-415 §1 to §5). Drawn from {@link TwoTopicsDrawn}, which the Confluence and website renderings' tests draw
 * too.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("833")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class ATopicIsAPageCarryingItsOverviewTest {

    /** The file a topic's, a subtopic's and a cluster's first page is, in its folder. */
    private static final String PAGE = Deliverable.INDEX_FILE_NAME;

    /** The words a disclosure stands in, for one written group of the undivided topic's two. */
    private static final String ONE_OF_TWO = "Written from what was written on 1 of the 2 groups in this topic.";

    @Test
    @Story("A topic's page carries its overview, each citation leading to the group it cites")
    @DisplayName("A divided topic's page is headed by its name, carries its overview with each numbered citation linking that group's page through its subtopic's folder, and lists its subtopics alone")
    void theTopicsPageCarriesItsOverviewEachCitationLinkingTheCitedGroup(@TempDir Path workingDirectory)
            throws IOException {
        Path tree = TwoTopicsDrawn.drawn(workingDirectory, List.of());
        String page = read(tree, PUMPING_FOLDER + "/" + PAGE);

        claim("the topic's page is headed by the topic's name",
                () -> assertThat(page).contains("<h1>" + PUMPING_NAME + "</h1>"));
        claim("its overview's words are on it, and each of its three numbers links the first page of the group at that"
                        + " place among the topic's groups, the first two in the first subtopic's folder and the third"
                        + " in the second's, in the order the overview cites them",
                () -> assertThat(page).containsSubsequence(
                        "Seals and impellers wear together ",
                        "<a href=\"1-seals-and-impellers/1-seal-wear/" + PAGE + "\">[1]</a>",
                        "<a href=\"1-seals-and-impellers/2-impeller-damage/" + PAGE + "\">[2]</a>",
                        "Motors run hot ",
                        "<a href=\"2-motors/3-motor-overheating/" + PAGE + "\">[3]</a>"));
        claim("an overview written over every group of its topic says nothing of how many it read",
                () -> assertThat(page).doesNotContain("Written from what was written on"));
        claim("beneath the overview, its table lists its two subtopics in their order, each named and linking its page,"
                        + " with how many groups and documents it holds",
                () -> assertThat(page).containsSubsequence(
                        "[3]</a>",
                        "<tr><th>Subtopic</th><th>Groups</th><th>Documents</th></tr>",
                        "<tr><td><a href=\"1-seals-and-impellers/" + PAGE + "\">Seals and Impellers</a></td><td>2</td>"
                                + "<td>2</td></tr>",
                        "<tr><td><a href=\"2-motors/" + PAGE + "\">Motors</a></td><td>1</td><td>1</td></tr>"));
        claim("and lists no group of its own: a divided topic's groups are listed on its subtopics' pages",
                () -> assertThat(page).doesNotContain(">Seal Wear<").doesNotContain(">Motor Overheating<"));
        claim("no document of another topic fits this one, so it has no See also",
                () -> assertThat(page).doesNotContain("See also"));
    }

    @Test
    @Story("A topic's page carries its overview, each citation leading to the group it cites")
    @DisplayName("An undivided topic's overview written over one of its two groups says so beneath it, and its table numbers each group, the unwritten one unlinked")
    void anOverviewOverFewerGroupsSaysSoAndTheTableNumbersEachGroup(@TempDir Path workingDirectory) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(workingDirectory, List.of());
        String page = read(tree, SLUICES_FOLDER + "/" + PAGE);
        String nothing = Html.escape(FixedWords.nothingWasWrittenOverIt(DeliverableLanguage.ENGLISH));

        claim("the page is headed by the topic's name, and its overview cites its first group, linking that group's page"
                        + " in the topic's own folder",
                () -> assertThat(page).containsSubsequence(
                        "<h1>" + TwoTopicsDrawn.SLUICES_NAME + "</h1>",
                        "Hinges rust in winter ",
                        "<a href=\"1-gate-hinges/" + PAGE + "\">[1]</a>"));
        claim("the overview read one of the topic's two groups, so the line saying so stands beneath it, in italics,"
                        + " before the table",
                () -> assertThat(page).containsSubsequence(
                        "[1]</a>", "<p><em>" + ONE_OF_TWO + "</em></p>", "<table>"));
        claim("the table's heads put the group's number first",
                () -> assertThat(page)
                        .contains("<tr><th>No.</th><th>Group</th><th>Documents</th><th>Written up as</th></tr>"));
        claim("its rows are the two groups in order, numbered 1 and 2, the written one linking its page and showing its"
                        + " title, the unwritten one unlinked and saying nothing was written over it",
                () -> assertThat(page).containsSubsequence(
                        "<tr><td>1</td><td><a href=\"1-gate-hinges/" + PAGE + "\">Gate Hinges</a></td><td>1</td>"
                                + "<td>" + TwoTopicsDrawn.HINGES_TITLE + "</td></tr>",
                        "<tr><td>2</td><td>Gate Seals</td><td>1</td><td><em>" + nothing + "</em></td></tr>"));
        claim("each group's folder lies directly in the topic's, numbered by its place",
                () -> assertThat(List.of(tree.resolve(HINGES_FOLDER), tree.resolve(GATE_SEALS_FOLDER)))
                        .allMatch(Files::isDirectory));
    }

    @Test
    @Story("A topic's page says why it carries no overview")
    @DisplayName("For each case of no overview, the topic's page carries that case's sentence in italics where the overview would be, its table beneath")
    void eachCaseOfNoOverviewIsSaidWhereTheOverviewWouldBe(@TempDir Path base) throws IOException {
        for (OverviewUnwritten why : OverviewUnwritten.values()) {
            Map<TopicKey, ListedOverview> none = Map.of(
                    PUMPING, new ListedOverview.Unwritten(why), SLUICES, new ListedOverview.Unwritten(why));
            Path tree = TwoTopicsDrawn.drawn(base.resolve(why.name()), List.of(), none);
            String page = read(tree, SLUICES_FOLDER + "/" + PAGE);
            String sentence = Html.escape(TopicOverviewWording.of(why, DeliverableLanguage.ENGLISH));

            claim("with no overview, " + why + ": the sentence for that case, in italics, after the name and before the"
                            + " table of groups",
                    () -> assertThat(page).containsSubsequence(
                            "<h1>" + TwoTopicsDrawn.SLUICES_NAME + "</h1>",
                            "<p><em>" + sentence + "</em></p>",
                            "<tr><th>No.</th>"));
            claim("and no line saying how many groups an overview read, there being none",
                    () -> assertThat(page).doesNotContain("Written from what was written on"));
        }
    }

    @Test
    @Story("A divided topic's subtopics are pages beneath it")
    @DisplayName("A subtopic's page is headed by its name, links the topic it is part of, and lists its one group under the number it has among the topic's groups")
    void aSubtopicsPageNamesItsTopicAndNumbersItsGroupsInTheTopicsSequence(@TempDir Path workingDirectory)
            throws IOException {
        Path tree = TwoTopicsDrawn.drawn(workingDirectory, List.of());
        String page = read(tree, MOTORS_FOLDER + "/" + PAGE);

        claim("the second subtopic's page is in its own folder within the topic's, headed by its name",
                () -> assertThat(page).contains("<h1>" + TwoTopicsDrawn.MOTORS_NAME + "</h1>"));
        claim("it says which topic it is part of, the topic's name linking the topic's page",
                () -> assertThat(page).contains(
                        "<p>In the topic <a href=\"../" + PAGE + "\">" + PUMPING_NAME + "</a>.</p>"));
        claim("its one group is numbered 3, its place among the topic's three groups, the first subtopic holding two,"
                        + " and links its page",
                () -> assertThat(page).contains("<tr><td>3</td><td><a href=\"3-motor-overheating/" + PAGE
                        + "\">Motor Overheating</a></td><td>1</td><td>" + TwoTopicsDrawn.MOTOR_TITLE + "</td></tr>"));
        claim("and that group's folder is in the subtopic's folder, named with that number, holding its page",
                () -> assertThat(tree.resolve(MOTOR_FOLDER).resolve(PAGE)).isRegularFile());
        claim("the first subtopic's page lies in its folder too",
                () -> assertThat(tree.resolve(SEALS_FOLDER).resolve(PAGE)).isRegularFile());
    }

    @Test
    @Story("The index leads to each topic's and subtopic's page")
    @DisplayName("The index heads each topic with a link to its page, each subtopic with a link to its page, and links each group's page through its subtopic's folder")
    void theIndexLinksEachTopicsPageAndEachSubtopicsPage(@TempDir Path workingDirectory) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(workingDirectory, List.of());
        String index = read(tree, Deliverable.INDEX_FILE_NAME);

        claim("the topics, in their order, each an h2 linking its page; the divided topic's subtopics, in their order,"
                        + " each an h3 linking its page, before the next topic",
                () -> assertThat(index).containsSubsequence(
                        "<h2><a href=\"" + PUMPING_FOLDER + "/" + PAGE + "\">" + PUMPING_NAME + "</a></h2>",
                        "<h3><a href=\"" + SEALS_FOLDER + "/" + PAGE + "\">Seals and Impellers</a></h3>",
                        "<h3><a href=\"" + MOTORS_FOLDER + "/" + PAGE + "\">Motors</a></h3>",
                        "<h2><a href=\"" + SLUICES_FOLDER + "/" + PAGE + "\">" + TwoTopicsDrawn.SLUICES_NAME
                                + "</a></h2>"));
        claim("a group of the divided topic is linked through its subtopic's folder, under its subtopic's heading",
                () -> assertThat(index).containsSubsequence(
                        "<h3><a href=\"" + MOTORS_FOLDER + "/" + PAGE + "\">",
                        "<a href=\"" + MOTOR_FOLDER + "/" + PAGE + "\">Motor Overheating</a>"));
    }

    @Test
    @Story("A document that fits another topic is linked from that topic's page")
    @DisplayName("A document placed under one topic that fits another is listed under See also on the other topic's page, its path linking its group's page, followed by the name of the topic it is placed under")
    void aSurvivorFittingAnotherTopicIsLinkedFromThatTopicsPage(@TempDir Path workingDirectory) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(workingDirectory, List.of());
        String page = read(tree, SLUICES_FOLDER + "/" + PAGE);

        claim("after the table, the heading See also and the sentence saying what it lists",
                () -> assertThat(page).containsSubsequence(
                        "</table>",
                        "<h2>See also</h2>",
                        "<p>Placed under another topic, these documents also speak of this one.</p>"));
        claim("then the one document, its path linking the first page of its group in the other topic, through that"
                        + " topic's subtopic's folder, and the name of the topic it is placed under",
                () -> assertThat(page).contains("<li><a href=\"../" + TwoTopicsDrawn.SEAL_WEAR_FOLDER + "/" + PAGE
                        + "\">" + THE_LINKED_PATH + "</a> (" + PUMPING_NAME + ")</li>"));
        claim("the document is copied once, into its own group's folder, and into no folder of the topic linking it",
                () -> assertThat(ListedTree.filesUnder(tree.resolve(SLUICES_FOLDER)))
                        .noneMatch(file -> file.endsWith("seal and gate.pdf")));
    }

    /**
     * A folder of the tree a drawing did not write is counted at every level of the arrangement it draws into, a
     * subtopic's folder as a topic's (ADR-415 §3, ADR-280 §4): a group's folder an earlier drawing left inside a
     * subtopic's folder is counted, as one left inside a topic's folder is.
     */
    @Test
    @Story("A divided topic's subtopics are pages beneath it")
    @DisplayName("A folder an earlier drawing left inside a subtopic's folder is counted among the folders left behind, as one left inside a topic's folder is")
    void aFolderLeftInsideASubtopicsFolderIsCountedAsLeftBehind(@TempDir Path workingDirectory) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(workingDirectory, List.of());
        Files.createDirectories(tree.resolve(SLUICES_FOLDER).resolve("9-a-group-an-earlier-drawing-left"));
        Files.createDirectories(tree.resolve(SEALS_FOLDER).resolve("9-another-group-an-earlier-drawing-left"));
        long[] leftBehind = {-1};

        TwoTopicsDrawn.drawn(workingDirectory, List.of(), new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                leftBehind[0] = otherFolders;
            }
        });

        claim("the drawing of the same tree again counts both folders it did not write: the one in the undivided topic's"
                        + " folder, and the one in the divided topic's first subtopic's folder",
                () -> assertThat(leftBehind[0]).isEqualTo(2));
    }

    @Test
    @Story("A topic's folder is named from its name")
    @DisplayName("A name's accented letters are folded to their base letter in its folder's name, and two topics of one name get two folders by their order")
    void aFolderNameFoldsAccentsAndKeepsTheOrderPrefix() {
        TopicKey first = new TopicKey("seeds/acqua.docx", 0);
        TopicKey second = new TopicKey("seeds/acque.docx", 0);

        claim("an Italian name's accented letter keeps its base letter in the slug, where it was a hyphen",
                () -> assertThat(IndexPage.slug("Qualità dell'acqua")).isEqualTo("qualita-dell-acqua"));
        claim("every accented letter folds, capitals among them",
                () -> assertThat(IndexPage.slug("Èra Già Così")).isEqualTo("era-gia-cosi"));
        claim("two topics named alike are drawn in two folders, told apart by their order",
                () -> assertThat(List.of(
                                IndexPage.topicDirectoryName(new ListedTopic(first, "Qualità dell'acqua", 1, 1), 2),
                                IndexPage.topicDirectoryName(new ListedTopic(second, "Qualità dell'acqua", 2, 1), 2)))
                        .containsExactly("1-qualita-dell-acqua", "2-qualita-dell-acqua"));
    }

    private static String read(Path tree, String file) throws IOException {
        return Files.readString(tree.resolve(file), StandardCharsets.UTF_8);
    }
}
