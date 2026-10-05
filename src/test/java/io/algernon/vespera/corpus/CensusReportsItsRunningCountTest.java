package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * What the census says while a walk is under way (ADR-192 section 6, #412): a running count of the entries
 * walked so far, with no total, written by {@code corpus}'s {@code WalkRecorder} as it has been since
 * ADR-093.
 *
 * <p>On main the line is written only when the walk finishes a directory and a commit interval of entries
 * has passed since the last commit, and the walk never finishes the root as a directory of its own. So the
 * files of a root with no directory in it were walked with nothing said until the walk ended. ADR-192 has
 * the line written from a callback after each entry, at the running cadence of its section 8, which is every
 * 1,000 entries below 100,000.
 *
 * <p>The first test walks {@value #FILES} files in a directory beneath the root: 1,002 entries with the
 * directory. On main that writes one running line, at 1,002, from the checkpoint the finished directory
 * offers. ADR-192 has the checkpoint write no line, so the walk writes one at 1,000 and none at 1,002. <b>Both
 * tests are red until part (a) of ADR-192 lands</b>: the first at its claim that the one running line names
 * 1,000 entries, the second at its claim that the lines are there. That cadence above 100,000 entries, and that the line can
 * name entries not yet committed, are pinned in {@code CensusRunningCountCadenceTest}, which part (a) brings
 * in, because a walk of that many files is not a fixture this suite can afford and the callback it drives
 * does not exist until then.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk recording")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
class CensusReportsItsRunningCountTest {

    /**
     * One more file than a thousand: with the directory that holds them, 1,002 entries, so the walk is past
     * its first running line when the directory ends and its checkpoint is taken.
     */
    private static final int FILES = 1_001;

    /** Two thousand and one files directly in the root: a line at 1,000 and at 2,000, and no third. */
    private static final int FILES_IN_A_FLAT_ROOT = 2_001;

    private static final String RUNNING_LINE = "entries walked so far";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private ListAppender<ILoggingEvent> logged;
    private Logger walkLogger;

    @BeforeEach
    void captureTheWalksLines() {
        logged = new ListAppender<>();
        logged.start();
        walkLogger = (Logger) LoggerFactory.getLogger(WalkRecorder.class);
        walkLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheWalksLines() {
        walkLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A walk with no total says how many entries it has walked so far")
    @DisplayName("A walk of a directory beneath the root writes a running count at 1,000 entries and none from its checkpoint")
    void aWalkOfADirectoryBeneathTheRootWritesARunningCount(@TempDir Path root) throws IOException {
        Path directory = Files.createDirectory(root.resolve("reports"));
        for (int file = 0; file < FILES; file++) {
            Files.writeString(directory.resolve(String.format("entry-%04d.txt", file)), "entry " + file);
        }

        recorder().walk(root);

        List<String> lines = messages();
        claim(
                "the walk wrote a running count of the entries it had walked so far, and it names no total:"
                        + " the census is finding out how many entries there are, so there is no number to count"
                        + " towards",
                () -> assertThat(lines)
                        .anyMatch(line -> line.matches(
                                        "Stage 0 \\(census\\): [\\d,]+ entries walked so far, [\\d,]+ directories"
                                                + " entered, under walk .*")
                                && !line.contains(" of ")));
        claim(
                "exactly one running line, naming 1,000 entries: the 1,002 entries are counted as they are"
                        + " walked, and the checkpoint the finished directory offers at 1,002 is taken and"
                        + " commits but writes no line of its own",
                () -> assertThat(lines.stream().filter(line -> line.contains(RUNNING_LINE)).toList())
                        .singleElement()
                        .satisfies(line ->
                                assertThat(line).startsWith("Stage 0 (census): 1,000 entries walked so far")));
        claim(
                "and the running count comes before the line that says the walk finished",
                () -> assertThat(String.join("\n", lines)).containsSubsequence(RUNNING_LINE, "finished"));
    }

    @Test
    @Story("A walk with no total says how many entries it has walked so far, wherever they are")
    @DisplayName("Files directly in the root get a running count every 1,000 entries, not only when a directory ends")
    void aFlatRootWritesARunningCountEveryThousandEntries(@TempDir Path root) throws IOException {
        for (int file = 0; file < FILES_IN_A_FLAT_ROOT; file++) {
            Files.writeString(root.resolve(String.format("entry-%04d.txt", file)), "entry " + file);
        }

        recorder().walk(root);

        List<String> running = messages().stream().filter(line -> line.contains(RUNNING_LINE)).toList();
        claim(
                "the walk wrote a running count at 1,000 entries and at 2,000, and at no other point: the "
                        + FILES_IN_A_FLAT_ROOT + " files are directly in the root, so no directory ended while"
                        + " they were walked, and a count written only when a directory ends would have written"
                        + " none",
                () -> assertThat(running)
                        .hasSize(2)
                        .satisfies(lines -> {
                            assertThat(lines.get(0)).startsWith("Stage 0 (census): 1,000 entries walked so far");
                            assertThat(lines.get(1)).startsWith("Stage 0 (census): 2,000 entries walked so far");
                        }));
        claim(
                "and the walk still finished and said so, after them",
                () -> assertThat(String.join("\n", messages()))
                        .containsSubsequence("2,000 " + RUNNING_LINE, "finished"));
    }

    private WalkRecorder recorder() {
        return new WalkRecorder(
                new Ledger(jdbcTemplate), new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
    }

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
