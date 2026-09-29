package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 1 leaves a log out of scope once the profile sets how much of a text file must begin with a
 * timestamp, and reads nothing but the bytes to decide it (ADR-171).
 *
 * <p><b>The floor is written into {@code profile.yaml} as text, before the application starts</b>, the
 * way an operator writes it and in the shape every key takes. That is also what lets this class compile
 * before the key exists: until it does, the profile does not load and every test here fails.
 *
 * <p>The archive is built so that each rule of ADR-171 §2 decides at least one file, and so that the name
 * could only mislead: the files that are logs carry no extension, a comma-separated name and a
 * {@code .txt} name, and the one named {@code .log} holds prose. The job runs once for the class, by
 * whichever test comes first, since every test reads the rows and the page that one invocation leaves.
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Byte-level reduction")
@Feature("Stage 1 step")
@Issue("370")
@Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
class LogsAreOutOfScopeInvocationTest {

    /** The floor this class runs under: the share the operator chose for the archive ADR-171 surveyed. */
    private static final String FLOOR = "0.9";

    /** How many of the archive's files are logs at that floor: the four the claims below name. */
    private static final int LOGS = 4;

    /** Lines a share needs before it is called one, and so the length of the files that must count. */
    private static final int ENOUGH_LINES = 20;

    /** The bytes read from each end of a file too large to read whole. */
    private static final int WINDOW = 65_536;

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path scratch;

    @DynamicPropertySource
    static void workingDirectoryWithAFloor(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        try {
            Files.writeString(
                    workingDirectory.resolve("profile.yaml"),
                    "logTimestampShareFloor:\n  value: '" + FLOOR + "'\n"
                            + "  provenance: \"set by this test before the tool started\"\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Path root;

    @BeforeEach
    void theJobHasRunOverTheArchive() throws IOException {
        if (root != null) {
            return;
        }
        root = Files.createDirectory(scratch.resolve("archive"));
        Files.writeString(root.resolve("server-output"), everyTimestampForm());
        Files.writeString(root.resolve("events.csv"), timestamped(ENOUGH_LINES, "TID-0001;SALE;12.50"));
        Files.writeString(root.resolve("at-the-floor.txt"), mixed(18, 2));
        writeLogAtBothEndsAndProseBetween(root.resolve("windows.txt"));
        Files.writeString(root.resolve("notes.log"), prose(ENOUGH_LINES));
        Files.writeString(root.resolve("under-the-floor.txt"), mixed(17, 3));
        Files.writeString(root.resolve("too-short.txt"), timestamped(9, "a short record"));
        Files.writeString(root.resolve("numbers.txt"), numbersThatAreNotTimestamps());
        cli.run("run", root.toString());
    }

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("Files whose lines begin with timestamps are removed as logs, whatever they are called")
    void removesTheLogsWhateverTheyAreCalled() {
        claim(
                "a file with no extension, a file named as comma-separated values, a file at exactly the floor"
                        + " of 90% and a large file read only at its two ends are each removed as out of scope",
                () -> assertThat(List.of(
                                stageOneVerdictsOn("server-output"),
                                stageOneVerdictsOn("events.csv"),
                                stageOneVerdictsOn("at-the-floor.txt"),
                                stageOneVerdictsOn("windows.txt")))
                        .containsOnly(List.of("OUT_OF_SCOPE")));
        claim(
                "every timestamp form is read: a file whose lines use each of them is 100% timestamped",
                () -> assertThat(stageOneReasonsOn("server-output")).containsExactly(logReason(100)));
        claim(
                "and the share in a reason is the one measured, so a file at the floor says 90%",
                () -> assertThat(stageOneReasonsOn("at-the-floor.txt")).containsExactly(logReason(90)));
        claim(
                "the large file's middle, where no line begins with a timestamp, was never read: only its"
                        + " first and last 64 KB were",
                () -> assertThat(stageOneReasonsOn("windows.txt")).containsExactly(logReason(100)));
    }

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("Prose named as a log, a file under the floor, a file too short and numbered prose are kept")
    void keepsWhatIsNotALog() {
        claim(
                "a file named as a log that holds prose is kept, because the name plays no part; so are a file"
                        + " with 85% of its lines timestamped, one of only nine lines, and prose whose lines begin"
                        + " with numbers that are not times",
                () -> assertThat(List.of(
                                stageOneVerdictsOn("notes.log"),
                                stageOneVerdictsOn("under-the-floor.txt"),
                                stageOneVerdictsOn("too-short.txt"),
                                stageOneVerdictsOn("numbers.txt")))
                        .containsOnly(List.of()));
    }

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("Stage 1 records the floor it ran under, and the page shows what it measured")
    void recordsTheFloorAndShowsTheMeasurement() throws IOException {
        claim(
                "the settings stage 1 records name the floor of " + FLOOR + " this profile sets, so a changed"
                        + " floor is a new piece of work",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT r.config_consumed FROM run r JOIN walk w ON w.id = r.walk_id"
                                        + " WHERE r.stage = 'byte-level-reduction' AND w.root = ?",
                                String.class,
                                Walk.canonicalRoot(root).toString()))
                        .contains("\"logTimestampShareFloor\":" + FLOOR));
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page counts the " + LOGS + " logs among the files left out",
                () -> {
                    assertThat(countIn(html, "left out as out of scope")).isEqualTo(LOGS);
                    assertThat(countIn(html, "Of those, logs")).isEqualTo(LOGS);
                });
        claim(
                "and its table of how much of each text file begins with a timestamp puts those " + LOGS
                        + " in the top band",
                () -> assertThat(countIn(html, "<td>90% to 100%</td>")).isEqualTo(LOGS));
        String profile = Files.readString(workingDirectory.resolve("profile.yaml"));
        claim(
                "the profile's floor now points at that page, where the measurement it was set from is",
                () -> assertThat(profile.substring(profile.indexOf("logTimestampShareFloor:")))
                        .contains(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
    }

    /** The reason a log is left out with, word for word, for a share of {@code percent}. */
    private static String logReason(int percent) {
        return "a log, and logs are out of scope: " + percent
                + "% of the lines read from its start and end begin with a timestamp";
    }

    /** Stage 1's verdicts on the file named {@code fileName} in this archive, by kind. */
    private List<String> stageOneVerdictsOn(String fileName) {
        return stageOne("v.kind", fileName);
    }

    /** Stage 1's reasons for its verdicts on the file named {@code fileName} in this archive. */
    private List<String> stageOneReasonsOn(String fileName) {
        return stageOne("v.reason", fileName);
    }

    private List<String> stageOne(String column, String fileName) {
        return jdbcTemplate.queryForList(
                "SELECT " + column + " FROM verdict v JOIN run r ON r.id = v.run_id"
                        + " JOIN file_occurrence f ON f.id = v.occurrence_id JOIN walk w ON w.id = f.walk_id"
                        + " WHERE r.stage = 'byte-level-reduction' AND w.root = ? AND f.path = ?",
                String.class,
                Walk.canonicalRoot(root).toString(),
                fileName);
    }

    /** The count the page prints on the line for {@code label}. */
    private static int countIn(String html, String label) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        java.util.regex.Pattern.quote(label) + "[^0-9]{0,80}?([0-9]+)")
                .matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("the page has no line for " + label + ": " + html);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Each timestamp form ADR-171 reads, three times over, then two with the bracket a line may open with:
     * year first, day first, a time alone, compact, and syslog's.
     */
    private static String everyTimestampForm() {
        StringBuilder text = new StringBuilder();
        for (int round = 0; round < 3; round++) {
            text.append("2023-02-08 12:00:0").append(round).append(",096 DEBUG handled a transaction\n")
                    .append("08/02/2023 12:0").append(round).append(" handled a transaction\n")
                    .append("12:00:0").append(round).append(" handled a transaction\n")
                    .append("20230208 12000").append(round).append(" handled a transaction\n")
                    .append("Feb  8 12:00:0").append(round).append(" terminal handled a transaction\n");
        }
        text.append("[2023-02-08 12:01:00,000] INFO the day closed\n")
                .append("(12:01:01) the terminal went idle\n");
        return text.toString();
    }

    /** {@code lines} lines, each opening with its own timestamp and then {@code rest}. */
    private static String timestamped(int lines, String rest) {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            text.append(String.format(Locale.ROOT, "2023-02-08 12:%02d:%02d;", line / 60, line % 60))
                    .append(rest)
                    .append('\n');
        }
        return text.toString();
    }

    /**
     * {@code stamped} timestamped lines, then {@code plain} lines of prose, with blank lines between the
     * two that must count for nothing.
     */
    private static String mixed(int stamped, int plain) {
        return timestamped(stamped, "an entry") + "\n\n  \n" + prose(plain);
    }

    /** {@code lines} lines of prose, none beginning with anything a timestamp can be read from. */
    private static String prose(int lines) {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            text.append("The terminal sends the request to the host and waits for its answer.\n");
        }
        return text.toString();
    }

    /** Prose whose lines open with numbers, dates without times and times without seconds. */
    private static String numbersThatAreNotTimestamps() {
        String[] openings = {
            "2023 was the year the terminals were replaced.",
            "12 terminals were returned for repair.",
            "1. Open the administration menu.",
            "3.14 is the version the host expects.",
            "12.05.2023 is the date the release was signed off.",
            "10:30 is when the nightly closing starts.",
            "08/02 is the day the batch runs.",
            "2023-02 is the month the report covers.",
            "20230208 is the batch number printed on the receipt.",
            "Feb is the short name of the month."
        };
        StringBuilder text = new StringBuilder();
        for (int round = 0; round < 2; round++) {
            for (String opening : openings) {
                text.append(opening).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * A file too large to read whole: more than one window of timestamped lines at each end, and between
     * them enough prose that the whole file is well under 90% timestamped, so only a reading of the two
     * windows alone finds it a log.
     */
    private static void writeLogAtBothEndsAndProseBetween(Path file) throws IOException {
        byte[] logLine = "2023-02-08 12:00:00,096 DEBUG a transaction was handled\n".getBytes(StandardCharsets.US_ASCII);
        byte[] proseLine = "The terminal sends the request and waits for the host.\n".getBytes(StandardCharsets.US_ASCII);
        int endLines = WINDOW / logLine.length + 10;
        int middleLines = 2 * endLines;
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(file))) {
            for (int line = 0; line < endLines; line++) {
                out.write(logLine);
            }
            for (int line = 0; line < middleLines; line++) {
                out.write(proseLine);
            }
            for (int line = 0; line < endLines; line++) {
                out.write(logLine);
            }
        }
    }
}
