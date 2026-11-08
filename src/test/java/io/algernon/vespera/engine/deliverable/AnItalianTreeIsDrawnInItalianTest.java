package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ItalianWordingTest.asRead;
import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A tree whose deliverable language is Italian, drawn whole, and what every page of it says (ADR-321 §4, §5): the HTML
 * cluster pages and index. The Confluence rendering's root page, seed partition's page, cluster pages and titles moved
 * with the rendering into {@code confluence} (ADR-333 §1), where {@code TheConfluenceRenderingIsDrawnInItalianTest}
 * holds them over this class's fixture, drawn with the Confluence rendering's plugin.
 * The writing itself is the model's and is handed in here as it would be written; what is held is every word Vespera
 * writes around it. Every fixture is synthetic: an archive written here, a run id of the ledger's shape.
 *
 * <p><b>One drawing holds every fixed word at once</b>: two clusters of one seed, the first written over, citing three
 * documents of its four and a number its cluster no longer holds, one of its copies gone from the archive, and split a
 * page an entry by a ceiling of one byte; the second turned down for the answer's shape in a run whose writing is
 * finished, so it stands without writing.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122); the pages say <em>gruppo</em>.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
public class AnItalianTreeIsDrawnInItalianTest {

    private static final DeliverableLanguage ITALIAN = DeliverableLanguage.ITALIAN;

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Sicurezza.docx";

    /** How many documents the written cluster holds, and how many entries its list has with the one it no longer holds. */
    public static final int FOUR_DOCUMENTS = 4;

    public static final int FIVE_ENTRIES = 5;

    /** How many documents its call sent: two it holds and one it no longer does. */
    public static final int THREE_SENT = 3;

    /** A ceiling of one byte, which splits a cluster a page an entry (ADR-303). */
    private static final long ONE_BYTE = 1;

    private static final RecordedCluster DOORS = cluster(THE_SEED, 0, "Porte", 1, FOUR_DOCUMENTS);

    private static final RecordedCluster DAMPERS = cluster(THE_SEED, 1, "Serrande", 2, 1);

    /** The title the model wrote, in Italian, as an Italian deliverable asks it to. */
    public static final String DOORS_TITLE = "Porte tagliafuoco e loro ispezione";

    /** Writing citing the three documents the call sent. */
    private static final String PROSE = "Le porte [1] e i telai [2] sono descritti insieme [3].";

    /** A document the call sent that the cluster no longer holds (ADR-133). */
    private static final long NO_LONGER_HELD = 99;

    /** The copy that cannot be made, its original never written into the archive. */
    public static final String GONE_FROM_THE_ARCHIVE = "reports/d.txt";

    /** The key the index lists the language under, as the profile spells it. */
    private static final String THE_KEY = "deliverableLanguage";

    /**
     * Words every English page of the tree carries and no Italian one may: each fixed English phrase of ADR-321 §4's
     * table, as far as it is told apart from a path or a value, and the navigation's four words as links. The line
     * of pictures not shown is not among them: this tree's documents carry no picture, and ItalianWordingTest holds
     * that line's Italian.
     */
    public static final List<String> ENGLISH_FIXED_WORDS = List.of(
            "The documents in this group",
            "Written from the first",
            "othing was written over this group",
            "othing has been written over this group",
            "Its answer was turned down",
            "a document the writing was made from",
            "What the local model wrote",
            "Page 1 of",
            ">first<",
            ">previous<",
            ">next<",
            ">last<",
            "Deliverable ",
            "Each group's page carries",
            "<th>Group</th>",
            "Written up as",
            "Archive root",
            "Run: ",
            "Walk: ",
            "no longer in the archive",
            "lang=\"en\"");

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("No page of an Italian tree carries a fixed English word")
    void noPageCarriesAFixedEnglishWord(@TempDir Path base) throws IOException {
        Path tree = draw(base);
        Map<String, String> pages = filesUnder(tree, ".html").stream()
                .collect(Collectors.toMap(page -> tree.relativize(page).toString(), page -> asRead(textOf(page))));

        claim(
                "the " + pages.size() + " HTML pages carry none of the English the same tree would carry in English;"
                        + " the Confluence rendering's pages are held to the same words by its plugin's test",
                () -> assertThat(pages).allSatisfy((name, page) -> assertThat(page)
                        .as(name)
                        .doesNotContain(ENGLISH_FIXED_WORDS)));
    }

    /** The fixture drawn into {@code base}, with no plugin. */
    private static Path draw(Path base) throws IOException {
        return draw(base, List.of());
    }

    /**
     * The fixture drawn into {@code base}: its archive, its working directory and its tree, in Italian, with {@code
     * plugins} drawing beside it; {@code confluence.TheConfluenceRenderingIsDrawnInItalianTest} draws it with the
     * Confluence rendering's (ADR-333 §1).
     */
    public static Path draw(Path base, List<Plugin> plugins) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(archive.resolve("reports"));
        Files.createDirectories(archive.resolve("notes"));
        Files.writeString(archive.resolve("reports/a.txt"), "il primo documento");
        Files.writeString(archive.resolve("reports/b.txt"), "il secondo documento");
        Files.writeString(archive.resolve("reports/c.txt"), "il terzo documento");
        Files.writeString(archive.resolve("notes/serranda.txt"), "una serranda");
        List<ListedSurvivor> survivors = List.of(
                survivor(10, "reports/a.txt", sha256Of(bytes("il primo documento")), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/b.txt", sha256Of(bytes("il secondo documento")), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, "reports/c.txt", sha256Of(bytes("il terzo documento")), THE_SEED, SEED_PATH, 0, 0.7),
                survivor(13, GONE_FROM_THE_ARCHIVE, sha256Of(bytes("never there")), THE_SEED, SEED_PATH, 0, 0.6),
                survivor(20, "notes/serranda.txt", sha256Of(bytes("una serranda")), THE_SEED, SEED_PATH, 1, 0.5));
        Map<ClusterSlot, SynthesisDoc> written =
                Map.of(slotOf(DOORS), writing(DOORS_TITLE, PROSE, 10, 11, NO_LONGER_HELD));
        Map<ClusterSlot, Unwritten> unwritten = Map.of(slotOf(DAMPERS), Unwritten.FAULT_SCHEMA_VIOLATION);
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                new DeliverableProvenance(
                        ListedTree.RUN_ID, ListedTree.WALK, archive.toString(),
                        List.of(new NamedValue(THE_KEY, ItalianWordingOnRecord.LANG))),
                ListedTree.of(List.of(DOORS, DAMPERS), written, survivors, unwritten, true, ITALIAN),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Bytes(ONE_BYTE),
                plugins,
                DeliverableProgress.NONE);
    }

    /** A link of a cluster page's navigation to the page named {@code name}, reading {@code word}. */
    private static String link(String name, String word) {
        return "<a href=\"./" + name + "\">" + word + "</a>";
    }

    /** Every file under {@code tree} whose name ends with {@code ending}, sorted. */
    private static List<Path> filesUnder(Path tree, String ending) throws IOException {
        try (Stream<Path> walk = Files.walk(tree)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(ending))
                    .sorted()
                    .toList();
        }
    }

    private static String textOf(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
