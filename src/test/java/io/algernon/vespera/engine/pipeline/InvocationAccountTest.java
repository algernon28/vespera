package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.RunId;
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
 * The account's writer on its own (ADR-198, ADR-270): a built job execution, a fixed clock, a working
 * directory of its own and an in-memory database holding only the columns the counts read. The account is
 * written in the working directory's {@value #LOGS} folder, with no setting. What the whole job adds is in
 * {@code InvocationAccountInvocationTest}; what only a writer fed by hand can reach is here.
 */
@Epic("Pipeline")
@Feature("The invocation account")
@Issue("424")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
@Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
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

    /** What the account writes in place of a step name, a stage or a run id that is not one it knows. */
    private static final String UNKNOWN = "unknown";

    /** How many causes beneath a failed step's exception the account names; a deeper one is left out. */
    private static final int MOST_CAUSES = 3;

    private static final String PROGRESS_LOGGER_NAME = StageProgress.class.getName();

    /** The folder of the working directory every log is written in, the account among them (ADR-270 section 1). */
    private static final String LOGS = "logs";

    /** How the one line for a configured {@code vespera.account-dir} begins; the folder written to follows. */
    private static final String THE_SETTING_IS_NO_LONGER_READ =
            "vespera.account-dir is no longer read, and the invocation account is written to ";

    @TempDir
    Path workingDirectory;

    /** A folder outside the working directory, which a configured {@code vespera.account-dir} names. */
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
        InvocationAccount account = anAccount();
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
        InvocationAccount account = anAccount();
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
        InvocationAccount account = anAccount();
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
        InvocationAccount one = anAccount();
        InvocationAccount other = anAccount();

        one.beforeJob(first);
        other.beforeJob(second);
        other.afterJob(second);
        one.afterJob(first);

        claim(
                "the logs folder holds the account and its suffixed neighbour, and nothing else",
                () -> assertThat(namesInTheLogsFolder()).containsExactlyInAnyOrder(FIRST_NAME, SECOND_NAME));
    }

    @Test
    @Story("The account is never a gate")
    @DisplayName("A logs folder that cannot be created leaves no account, fails nothing and says where it tried")
    @Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
    void aFailedWriteNeverFailsTheInvocation() throws IOException {
        Path aFileWhereTheFolderGoes = Files.writeString(workingDirectory.resolve(LOGS), "x");

        List<String> logged = linesLoggedByAnInvocationOf(anAccount());

        claim(
                "both calls returned, and the file standing where the logs folder goes is still a file",
                () -> assertThat(aFileWhereTheFolderGoes).isRegularFile().hasContent("x"));
        claim(
                "one line was logged, saying the account could not be written to the logs folder of the working"
                        + " directory",
                () -> assertThat(logged)
                        .singleElement()
                        .asString()
                        .startsWith("the invocation account could not be written to " + aFileWhereTheFolderGoes + ": "));
    }

    @Test
    @Story("The account is written in the working directory's logs folder")
    @DisplayName("With no setting at all, the account is written in the working directory's logs folder and nothing is logged")
    @Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
    void anAccountIsWrittenInTheLogsFolderWithNoSetting() throws IOException {
        List<String> logged = linesLoggedByAnInvocationOf(anAccount());

        claim(
                "the logs folder holds the account, and nothing else",
                () -> assertThat(namesInTheLogsFolder()).containsExactly(FIRST_NAME));
        claim(
                "the top of the working directory holds the logs folder and no account",
                () -> assertThat(namesIn(workingDirectory)).containsExactly(LOGS));
        claim("and no warning was logged", () -> assertThat(logged).isEmpty());
    }

    @Test
    @Story("The account is written in the working directory's logs folder")
    @DisplayName("A configured vespera.account-dir sends the account nowhere else, and one line says where it is")
    @Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
    void aConfiguredAccountFolderIsIgnoredWithOneLine() throws IOException {
        List<String> logged = linesLoggedByAnInvocationOf(
                new InvocationAccount(workingDirectory, accountDirectory, jdbc, clock));

        claim(
                "the folder vespera.account-dir names gained nothing",
                () -> assertThat(namesIn(accountDirectory)).isEmpty());
        claim(
                "the account is in the working directory's logs folder, as it is with no setting",
                () -> assertThat(namesInTheLogsFolder()).containsExactly(FIRST_NAME));
        claim(
                "the one warning logged says the setting is no longer read and names the logs folder",
                () -> assertThat(logged)
                        .containsExactly(THE_SETTING_IS_NO_LONGER_READ
                                + workingDirectory.toAbsolutePath().resolve(LOGS)));
    }

    /**
     * A step whose name is not one the pipeline declares is written as {@value #UNKNOWN}, so a name built
     * from anything else never reaches the file (ADR-198 section 2).
     */
    @Test
    @Story("A name outside the pipeline's own steps and stages is never written")
    @DisplayName("A step whose name is not one of the pipeline's steps is written as unknown, and its name is not")
    void aStepNotAmongThePipelinesStepsIsWrittenAsUnknown() throws IOException {
        InvocationAccount account = anAccount();
        JobExecution job = aJob();
        StepExecution step = new StepExecution(MARKER + "-step", job);
        step.setStatus(BatchStatus.COMPLETED);
        job.addStepExecutions(List.of(step));

        account.beforeJob(job);
        account.afterJob(job);

        String text = theOneAccount();
        claim(
                "the step is written, under the name unknown",
                () -> assertThat(text).contains(" step name=" + UNKNOWN + " status=COMPLETED "));
        claim("and the name it was given is nowhere, in any letter case", () -> assertThat(text)
                .doesNotContainIgnoringCase(MARKER));
    }

    /**
     * A run recorded under a stage the pipeline does not declare is written with the stage {@value #UNKNOWN}
     * (ADR-198 section 2).
     */
    @Test
    @Story("A name outside the pipeline's own steps and stages is never written")
    @DisplayName("A run recorded under a stage that is not one of the pipeline's is written with the stage unknown")
    void aRunOfAStageNotAmongThePipelinesIsWrittenAsUnknown() throws IOException {
        InvocationAccount account = anAccount();
        JobExecution job = aJob();
        new InvocationRuns(job.getExecutionContext()).record(MARKER + "-stage", new RunId(A_RUN_ID));

        account.beforeJob(job);
        account.afterJob(job);

        String text = theOneAccount();
        claim(
                "the run is written with the stage unknown and its digest",
                () -> assertThat(text).contains(" run stage=" + UNKNOWN + " id=" + A_RUN_ID));
        claim("and the stage it was recorded under is nowhere, in any letter case", () -> assertThat(text)
                .doesNotContainIgnoringCase(MARKER));
    }

    /**
     * A run id that is not a hexadecimal digest is written as {@value #UNKNOWN}, so a value that is not what
     * a run is minted under never reaches the file (ADR-198 section 2).
     */
    @Test
    @Story("An account says which runs the invocation arrived at")
    @DisplayName("A run id that is not a hexadecimal digest is written as unknown, and its text is not")
    void aRunIdThatIsNotADigestIsWrittenAsUnknown() throws IOException {
        InvocationAccount account = anAccount();
        JobExecution job = aJob();
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EXTRACTION.stage(), new RunId(MARKER));

        account.beforeJob(job);
        account.afterJob(job);

        String text = theOneAccount();
        claim(
                "the run is written under its stage with the id unknown",
                () -> assertThat(text).contains(" run stage=extraction id=" + UNKNOWN));
        claim("and the id's own text is nowhere, in any letter case", () -> assertThat(text)
                .doesNotContainIgnoringCase(MARKER));
    }

    /**
     * A failed step is written as the class names of its exception and of at most {@value #MOST_CAUSES} causes,
     * never a message, and a step name the pipeline does not declare is written as {@value #UNKNOWN}
     * (ADR-198 sections 2 and 5).
     */
    @Test
    @Story("A failed step is named by the type of what failed, and its message stays in the operator's log")
    @DisplayName("A failed step is written as its exception's type and its first three causes' types, with no message")
    void aFailureIsWrittenAsTypesAlone() throws IOException {
        InvocationAccount account = anAccount();
        JobExecution job = aJob();
        StepExecution step = new StepExecution(MARKER + "-step", job);
        step.setStatus(BatchStatus.FAILED);
        job.addStepExecutions(List.of(step));
        step.addFailureException(new IllegalStateException(MARKER + " failed", new IOException(MARKER + " one",
                new IllegalArgumentException(MARKER + " two", new UnsupportedOperationException(MARKER + " three",
                        new ArithmeticException(MARKER + " four"))))));

        account.beforeJob(job);
        account.afterJob(job);

        String text = theOneAccount();
        claim(
                "the failure line names the step unknown, the exception's type and the types of its first "
                        + MOST_CAUSES + " causes, and ends there, so the cause beneath those is not written",
                () -> assertThat(linesOf(text))
                        .anyMatch(line -> line.endsWith(" failure step=" + UNKNOWN
                                + " type=java.lang.IllegalStateException cause=java.io.IOException"
                                + " cause=java.lang.IllegalArgumentException"
                                + " cause=java.lang.UnsupportedOperationException")));
        claim("and the type of the cause beneath those is nowhere", () -> assertThat(text)
                .doesNotContain("ArithmeticException"));
        claim("and no message, nor the step's own name, is anywhere, in any letter case", () -> assertThat(text)
                .doesNotContainIgnoringCase(MARKER));
    }

    private static List<String> linesOf(String text) {
        return text.lines().toList();
    }

    private InvocationAccount anAccount() {
        return new InvocationAccount(workingDirectory, null, jdbc, clock);
    }

    /** What the account's logger warned of, as the messages an operator reads, while one invocation went by. */
    private List<String> linesLoggedByAnInvocationOf(InvocationAccount account) {
        Logger logger = (Logger) LoggerFactory.getLogger(InvocationAccount.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            JobExecution job = aJob();
            account.beforeJob(job);
            account.afterJob(job);
        } finally {
            logger.detachAppender(logged);
        }
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static JobExecution aJob() {
        JobExecution job = new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters());
        job.setStatus(BatchStatus.COMPLETED);
        StepExecution step = new StepExecution(StepNames.CENSUS, job);
        step.setStatus(BatchStatus.COMPLETED);
        return job;
    }

    private String theOneAccount() throws IOException {
        List<String> names = namesInTheLogsFolder();
        claim("exactly one account was written", () -> assertThat(names).hasSize(1));
        return Files.readString(workingDirectory.resolve(LOGS).resolve(names.getFirst()));
    }

    /** The names in the working directory's logs folder, after a plain claim that the folder was made. */
    private List<String> namesInTheLogsFolder() throws IOException {
        Path logs = workingDirectory.resolve(LOGS);
        claim("the working directory has a logs folder, where the account is written", () -> assertThat(logs)
                .isDirectory());
        return namesIn(logs);
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }
}
