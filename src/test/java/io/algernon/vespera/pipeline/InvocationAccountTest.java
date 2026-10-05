package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The account's writer on its own (ADR-198): a built job execution, a fixed clock, a folder of its own and
 * an in-memory database holding only the columns the counts read. What the whole job adds is in {@code
 * InvocationAccountInvocationTest}; what only a writer fed by hand can reach is here.
 */
@Epic("Pipeline")
@Feature("The invocation account")
@Issue("424")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
class InvocationAccountTest {

    /** A word nobody would write into a diagnostic by chance. */
    private static final String MARKER = "ZXQ-the-operators-private-word";

    /** The instant the fixed clock reads, so a file name and every line's time are known. */
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    /** The name the account takes at {@link #NOW}. */
    private static final String FIRST_NAME = "invocation-account-20261005T100000Z.txt";

    /** The name a second account takes in the same second. */
    private static final String SECOND_NAME = "invocation-account-20261005T100000Z-2.txt";

    /** A run id is a 64-character hexadecimal digest. */
    private static final String A_RUN_ID = "0123456789abcdef".repeat(4);

    private static final String PROGRESS_LOGGER_NAME = StageProgress.class.getName();

    @TempDir
    Path workingDirectory;

    @TempDir
    Path accountDirectory;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private JdbcTemplate jdbc;

    @BeforeEach
    void anEmptyLedger() {
        jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        jdbc.execute("CREATE TABLE file_occurrence (id INTEGER PRIMARY KEY, walk_id INTEGER)");
        jdbc.execute("CREATE TABLE run (id TEXT PRIMARY KEY, walk_id INTEGER)");
        jdbc.execute("CREATE TABLE verdict (run_id TEXT, kind TEXT)");
        jdbc.execute("CREATE TABLE extraction_fault (run_id TEXT, category TEXT)");
        jdbc.execute("CREATE TABLE cluster_fault (run_id TEXT, kind TEXT)");
        jdbc.execute("CREATE TABLE cluster (run_id TEXT)");
        jdbc.execute("CREATE TABLE synthesis_doc (run_id TEXT)");
    }

    @Test
    @Story("A progress line is carried only if it has a counter's shape")
    @DisplayName("A line that is not a counter's, written through the counters' logger, is withheld and counted")
    void aLineThatIsNotACounterIsWithheld() throws IOException {
        InvocationAccount account = anAccountIn(accountDirectory);
        JobExecution job = aJob();

        account.beforeJob(job);
        LoggerFactory.getLogger(PROGRESS_LOGGER_NAME).info("Stage 2 (extraction): " + MARKER + " so far");
        StageProgress.over("Stage 2 (extraction)", 1).itemDone();
        account.afterJob(job);

        String text = theOneAccount();
        claim(
                "the line that was not a counter's is not in the account, in any letter case",
                () -> assertThat(text).doesNotContainIgnoringCase(MARKER));
        claim("and one line was withheld, which the account says", () -> assertThat(text).contains("progress withheld=1"));
        claim(
                "the real counter's line is there as it was written",
                () -> assertThat(text).contains(" progress Stage 2 (extraction): 1 of 1 (100%)"));
    }

    @Test
    @Story("An account says which runs the invocation arrived at")
    @DisplayName("A run is written as its stage and its 64-character digest")
    void aRunLineCarriesTheDigest() throws IOException {
        InvocationAccount account = anAccountIn(accountDirectory);
        JobExecution job = aJob();
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EXTRACTION.stage(), new RunId(A_RUN_ID));

        account.beforeJob(job);
        account.afterJob(job);

        claim(
                "the account has a run line naming the stage and the 64 hexadecimal characters of its id",
                () -> assertThat(theOneAccount()).contains(" run stage=extraction id=" + A_RUN_ID));
    }

    @Test
    @Story("A stored value outside a closed vocabulary is never written")
    @DisplayName("A verdict kind that is not in the vocabulary is written as other, and its text is not")
    void anUnknownStoredValueIsWrittenAsOther() throws IOException {
        InvocationAccount account = anAccountIn(accountDirectory);
        JobExecution job = aJob();
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EXTRACTION.stage(), new RunId(A_RUN_ID));
        jdbc.update("INSERT INTO verdict (run_id, kind) VALUES (?, 'BROKEN')", A_RUN_ID);
        jdbc.update("INSERT INTO verdict (run_id, kind) VALUES (?, ?)", A_RUN_ID, MARKER + " as a kind");

        account.beforeJob(job);
        account.afterJob(job);

        String text = theOneAccount();
        claim(
                "the known kind is counted by its name, and the unknown one as other",
                () -> assertThat(text)
                        .contains("counts verdict kind=BROKEN count=1")
                        .contains("counts verdict kind=other count=1"));
        claim("and the unknown one's text is nowhere", () -> assertThat(text).doesNotContainIgnoringCase(MARKER));
    }

    @Test
    @Story("Two accounts in one second do not overwrite each other")
    @DisplayName("A second account in the same second takes the suffix -2")
    void aSecondAccountInTheSameSecondTakesASuffix() throws IOException {
        JobExecution first = aJob();
        JobExecution second = aJob();
        InvocationAccount one = anAccountIn(accountDirectory);
        InvocationAccount other = anAccountIn(accountDirectory);

        one.beforeJob(first);
        other.beforeJob(second);
        other.afterJob(second);
        one.afterJob(first);

        claim(
                "the folder holds the account and its suffixed neighbour, and nothing else",
                () -> assertThat(namesIn(accountDirectory)).containsExactlyInAnyOrder(FIRST_NAME, SECOND_NAME));
    }

    @Test
    @Story("The account is never a gate")
    @DisplayName("A folder that cannot be created leaves no account and fails nothing")
    void aFailedWriteNeverFailsTheInvocation() throws IOException {
        Path aFile = Files.writeString(accountDirectory.resolve("a-file-not-a-folder"), "x");
        InvocationAccount account = anAccountIn(aFile.resolve("beneath"));
        JobExecution job = aJob();

        account.beforeJob(job);
        account.afterJob(job);

        claim(
                "both calls returned, and nothing was written beneath a file",
                () -> assertThat(namesIn(accountDirectory)).containsExactly("a-file-not-a-folder"));
    }

    @Test
    @Story("An account is written only outside every working directory")
    @DisplayName("An unset folder, or one inside the working directory, or beneath a vespera.db or vespera.lock, writes nothing")
    void noAccountWhereTheFolderIsUnsetOrInsideAWorkingDirectory() throws IOException {
        Path insideTheWorkingDirectory = workingDirectory.resolve("accounts");
        Path anotherWorkingDirectory = Files.createDirectory(accountDirectory.resolve("another"));
        Files.writeString(anotherWorkingDirectory.resolve("vespera.db"), "");
        Path beneathIt = anotherWorkingDirectory.resolve("accounts");
        Path heldByALockOnly = Files.createDirectory(accountDirectory.resolve("locked"));
        Files.writeString(heldByALockOnly.resolve("vespera.lock"), "");

        for (Path folder : Arrays.asList(null, insideTheWorkingDirectory, beneathIt, heldByALockOnly.resolve("accounts"))) {
            JobExecution job = aJob();
            InvocationAccount account = anAccountIn(folder);
            account.beforeJob(job);
            account.afterJob(job);
        }

        claim(
                "no account was written for an unset folder, for one inside the working directory or for one"
                        + " beneath a folder holding the database",
                () -> {
                    assertThat(namesIn(workingDirectory)).isEmpty();
                    assertThat(namesIn(anotherWorkingDirectory)).containsExactly("vespera.db");
                    assertThat(namesIn(heldByALockOnly)).containsExactly("vespera.lock");
                });
    }

    private InvocationAccount anAccountIn(Path folder) {
        return new InvocationAccount(workingDirectory, folder, jdbc, clock);
    }

    private static JobExecution aJob() {
        JobExecution job = new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters());
        job.setStatus(BatchStatus.COMPLETED);
        StepExecution step = new StepExecution(StepNames.CENSUS, job);
        step.setStatus(BatchStatus.COMPLETED);
        return job;
    }

    private String theOneAccount() throws IOException {
        List<String> names = namesIn(accountDirectory);
        claim("exactly one account was written", () -> assertThat(names).hasSize(1));
        return Files.readString(accountDirectory.resolve(names.getFirst()));
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }
}
