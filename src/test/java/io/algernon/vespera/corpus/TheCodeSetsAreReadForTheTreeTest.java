package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.KeysetPages;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code corpus}'s reader of the code sets for the tree (ADR-296 §8): {@link CodeSets#setsOf} lists a stage-1 run's
 * sets in the order of their folders, each with its default name and lowest member occurrence, and
 * {@link CodeSets#eachPageOfMembers} hands one set's members a page of {@value KeysetPages#ROWS_IN_A_PAGE} at a time,
 * in ascending occurrence id, each with the size and last-modified time the walk recorded, which
 * {@code Occurrences.sizesAndTimesOf} reads back as the {@code Instant} the census wrote as text.
 *
 * <p>One walk holds two stage-1 runs' sets: the run read here, and another whose rows a read of this run must never
 * return, its one set sorting before every set of this run and its members lower than any of this run's. Every member
 * row of this run is written in descending occurrence id, and the sets out of their folders' order, so neither order
 * comes from the order the rows were written in. Each test writes its rows inside the test's own transaction, which is
 * rolled back after it.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("573")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
class TheCodeSetsAreReadForTheTreeTest {

    /** The run's three sets, by their folders as {@code code_set} holds them; {@code ""} is the root's set. */
    private static final String THE_ROOTS_SET = "";

    private static final String SHOP_CORE = "shop/core";

    private static final String TOOLS = "tools";

    /** The root's set is named for the corpus root's own folder. */
    private static final String THE_ROOTS_NAME = "archivio";

    /** The other run's own set, whose folder sorts before every folder of the run read. */
    private static final String THE_OTHER_RUNS_SET = "aaa";

    /** One page and one member more, so that the set at {@link #TOOLS} is handed over in two pages. */
    private static final int MEMBERS_OF_TOOLS = KeysetPages.ROWS_IN_A_PAGE + 1;

    /** A time with nanoseconds, one at a whole minute and one with a half second, each as the census writes it. */
    private static final Instant TO_THE_NANOSECOND = Instant.parse("2026-03-04T05:06:07.123456789Z");

    private static final Instant AT_A_WHOLE_MINUTE = Instant.parse("2026-03-04T05:06:00Z");

    private static final Instant AT_A_HALF_SECOND = Instant.parse("1999-12-31T23:59:59.500Z");

    /** The first time of the members of {@link #TOOLS}, each a millisecond after the one before. */
    private static final Instant TOOLS_FROM = Instant.parse("2025-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("A run's code sets are listed in the order of their folders, each with its own name and its lowest-numbered file, and another run's sets are not")
    void theSetsOfARunAreListedInFolderOrderEachWithItsLowestMember() {
        TwoRuns ledger = twoRunsOfOneWalk();

        claim(
                "the run's three sets are listed in the order of their folders, the archive's top first, each with its"
                        + " default name and the lowest-numbered of its own files, though its files were recorded"
                        + " highest first; the other run's set and its lower-numbered files are not among them",
                () -> assertThat(new DetectedFormats(jdbcTemplate).codeSets().setsOf(ledger.read()))
                        .containsExactly(
                                new CodeSets.ListedSet(THE_ROOTS_SET, THE_ROOTS_NAME, ledger.lowest(THE_ROOTS_SET)),
                                new CodeSets.ListedSet(SHOP_CORE, "core", ledger.lowest(SHOP_CORE)),
                                new CodeSets.ListedSet(TOOLS, TOOLS, ledger.lowest(TOOLS))));
    }

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("A code set's files are handed over a thousand at a time in the order they were numbered, each with the size and time recorded when the archive was walked")
    void aSetsMembersAreHandedOverAPageAtATimeWithTheWalksSizeAndTime() {
        TwoRuns ledger = twoRunsOfOneWalk();
        CodeSets reader = new DetectedFormats(jdbcTemplate).codeSets();
        Ledger theLedger = new Ledger(jdbcTemplate);

        List<List<CodeSets.ListedMember>> pagesOfTools = new ArrayList<>();
        reader.eachPageOfMembers(theLedger, ledger.read(), TOOLS, pagesOfTools::add);
        List<CodeSets.ListedMember> ofShopCore = new ArrayList<>();
        reader.eachPageOfMembers(theLedger, ledger.read(), SHOP_CORE, ofShopCore::addAll);

        claim(
                "the " + MEMBERS_OF_TOOLS + " files of the large set come in two pages, the first of "
                        + KeysetPages.ROWS_IN_A_PAGE + " and the second of the one left",
                () -> assertThat(pagesOfTools).extracting(List::size).containsExactly(KeysetPages.ROWS_IN_A_PAGE, 1));
        claim(
                "and together they are every file of that set in ascending number, each with its path, role, size and"
                        + " time as recorded",
                () -> assertThat(pagesOfTools.stream().flatMap(List::stream).toList())
                        .containsExactlyElementsOf(ledger.expected(TOOLS)));
        claim(
                "the small set's files come in ascending number, though recorded highest first, each with its own"
                        + " role and generator under this run, not the other run's, and the size and time the walk"
                        + " recorded, a time to the nanosecond, at a whole minute and at a half second each read back"
                        + " equal; the other run's lower-numbered file of the same folder is not among them",
                () -> assertThat(ofShopCore).containsExactlyElementsOf(ledger.expected(SHOP_CORE)));
    }

    /**
     * One walk with two stage-1 runs. The other run's rows are written first, so its occurrences are numbered lowest:
     * a file of its own in {@link #SHOP_CORE}, and one in a set {@link #THE_OTHER_RUNS_SET}; it also lists a file of
     * the run read, in a role of its own.
     */
    private TwoRuns twoRunsOfOneWalk() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("/srv/" + THE_ROOTS_NAME));
        RunId read = ledger.runs().startRun("byte-level-reduction", "read-" + System.nanoTime(), "{}", walk, List.of());
        RunId other = ledger.runs().startRun("byte-level-reduction", "other-" + System.nanoTime(), "{}", walk, List.of());
        CodeSets sets = new DetectedFormats(jdbcTemplate).codeSets();

        OccurrenceId othersOwn = occurrence(ledger, walk, SHOP_CORE + "/Old.java", 7, AT_A_WHOLE_MINUTE);
        OccurrenceId othersOther = occurrence(ledger, walk, THE_OTHER_RUNS_SET + "/a.c", 8, AT_A_WHOLE_MINUTE);

        Map<String, List<CodeSets.ListedMember>> expected = new LinkedHashMap<>();
        expected.put(THE_ROOTS_SET, List.of(
                listed(ledger, walk, THE_ROOTS_SET, "run.py", "code", null, 11, TO_THE_NANOSECOND),
                listed(ledger, walk, THE_ROOTS_SET, "Makefile", "build", null, 12, AT_A_WHOLE_MINUTE)));
        expected.put(SHOP_CORE, List.of(
                listed(ledger, walk, SHOP_CORE, SHOP_CORE + "/pom.xml", "build", null, 21, AT_A_HALF_SECOND),
                listed(ledger, walk, SHOP_CORE, SHOP_CORE + "/src/Core.java", "code", null, 22, TO_THE_NANOSECOND),
                listed(ledger, walk, SHOP_CORE, SHOP_CORE + "/docs/logo.png", "companion", "javadoc", 23,
                        AT_A_WHOLE_MINUTE)));
        List<CodeSets.ListedMember> tools = new ArrayList<>();
        for (int at = 0; at < MEMBERS_OF_TOOLS; at++) {
            tools.add(listed(ledger, walk, TOOLS, "%s/f%04d.py".formatted(TOOLS, at), "code", null, 1_000L + at,
                    TOOLS_FROM.plusMillis(at)));
        }
        expected.put(TOOLS, tools);

        codeSet(other, THE_OTHER_RUNS_SET, THE_OTHER_RUNS_SET);
        codeSet(other, SHOP_CORE, "core");
        sets.member(othersOther, other, THE_OTHER_RUNS_SET, "code", null);
        sets.member(othersOwn, other, SHOP_CORE, "code", null);
        CodeSets.ListedMember core = expected.get(SHOP_CORE).get(1);
        sets.member(core.occurrence(), other, SHOP_CORE, "build", "doxygen");

        codeSet(read, TOOLS, TOOLS);
        codeSet(read, SHOP_CORE, "core");
        codeSet(read, THE_ROOTS_SET, THE_ROOTS_NAME);
        expected.forEach((folder, members) -> members.reversed().forEach(member ->
                sets.member(member.occurrence(), read, folder, member.role(), member.generator())));
        return new TwoRuns(read, expected);
    }

    /** The run read, and the members each of its sets is expected to list, in ascending occurrence id. */
    private record TwoRuns(RunId read, Map<String, List<CodeSets.ListedMember>> members) {

        List<CodeSets.ListedMember> expected(String folder) {
            return members.get(folder);
        }

        OccurrenceId lowest(String folder) {
            return members.get(folder).getFirst().occurrence();
        }
    }

    /** Records an occurrence and the member the reader is expected to list for it. */
    private static CodeSets.ListedMember listed(
            Ledger ledger,
            WalkId walk,
            String folder,
            String path,
            String role,
            String generator,
            long size,
            Instant lastModified) {
        OccurrenceId id = occurrence(ledger, walk, path, size, lastModified);
        return new CodeSets.ListedMember(id, new OccurrencePath(path), folder, role, generator, size, lastModified);
    }

    private static OccurrenceId occurrence(Ledger ledger, WalkId walk, String path, long size, Instant lastModified) {
        ledger.occurrences()
                .fileOccurrence(walk, new OccurrencePath(path), size, lastModified, Instant.parse("2020-01-01T00:00:00Z"));
        return ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
    }

    private void codeSet(RunId run, String folder, String defaultName) {
        jdbcTemplate.update(
                "INSERT INTO code_set (run_id, folder, default_name) VALUES (?, ?, ?)", run.value(), folder, defaultName);
    }
}
