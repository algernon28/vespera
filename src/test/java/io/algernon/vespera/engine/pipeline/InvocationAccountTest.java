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
import java.nio.file.Path;
import java.util.List;
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
import tools.jackson.databind.JsonNode;

/**
 * What the report counts of an invocation, on its own (ADR-198 section 3, ADR-224, ADR-362 section 3): a built job
 * execution, a working directory of its own and an in-memory database holding only the columns the counts read. The
 * counts are handed to the report, which holds them as {@code counts}; no file of the account is written, as ADR-362
 * section 4 retires it. What the whole job adds is in {@code InvocationAccountInvocationTest}.
 */
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("424")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class InvocationAccountTest {

    /** A word nobody would write into a diagnostic by chance. */
    private static final String MARKER = "ZXQ-the-operators-private-word";

    /** A run id is a 64-character hexadecimal digest. */
    private static final String A_RUN_ID = "0123456789abcdef".repeat(4);

    /** The folder of the working directory every log is written in, the report's copy among them (ADR-270 section 1). */
    private static final String LOGS = "logs";

    /** How the name of an invocation account started, which no invocation writes any more (ADR-362 section 4). */
    private static final String THE_RETIRED_ACCOUNTS_PREFIX = "invocation-account-";

    /** How the one line for a configured {@code vespera.account-dir} begins; the folder written to follows. */
    private static final String THE_SETTING_IS_NO_LONGER_READ =
            "vespera.account-dir is no longer read, and the report's copy is written to ";

    @TempDir
    Path workingDirectory;

    /** A folder outside the working directory, which a configured {@code vespera.account-dir} names. */
    @TempDir
    Path accountDirectory;

    private JdbcTemplate jdbc;
    private InvocationReport report;

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
        report = SixJobsForTests.aReport(workingDirectory);
        report.begins(InvocationReport.RUN);
    }

    @Test
    @Story("A stored value outside a closed vocabulary is never held")
    @DisplayName("A verdict kind that is not in the vocabulary is counted as other, and its text is not in the report")
    void anUnknownStoredValueIsCountedAsOther() {
        JobExecution job = aJob();
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EXTRACTION.stage(), new RunId(A_RUN_ID));
        jdbc.update("INSERT INTO verdict (run_id, kind) VALUES (?, 'BROKEN')", A_RUN_ID);
        jdbc.update("INSERT INTO verdict (run_id, kind) VALUES (?, ?)", A_RUN_ID, MARKER + " as a kind");

        anAccount().afterJob(job);
        report.runEnds(0, null, null);

        JsonNode verdicts = TheReportFile.in(workingDirectory).path("counts").path("verdicts");
        claim(
                "the known kind is counted by its name",
                () -> assertThat(verdicts.path("broken").asLong()).isEqualTo(1));
        claim("and the unknown one as other", () -> assertThat(verdicts.path("other").asLong()).isEqualTo(1));
        claim(
                "and the unknown one's text is nowhere in the report, in any letter case",
                () -> assertThat(TheReportFile.textIn(workingDirectory)).doesNotContainIgnoringCase(MARKER));
    }

    @Test
    @Story("The counts are never a gate")
    @DisplayName("A ledger that cannot be counted leaves the invocation as it was and logs the type of what failed")
    void aFailedCountNeverFailsTheInvocation() {
        JobExecution job = aJob();
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EXTRACTION.stage(), new RunId(A_RUN_ID));
        jdbc.execute("DROP TABLE verdict");

        List<String> logged = linesLoggedBy(() -> anAccount().afterJob(job));

        claim(
                "the call returned, and one line was logged, naming the type of what failed and no message",
                () -> assertThat(logged)
                        .singleElement()
                        .asString()
                        .startsWith("the invocation's counts could not be read: "));
    }

    @Test
    @Story("The report's copy is written in the working directory's logs folder")
    @DisplayName("With no setting at all, nothing is logged and the account writes no file")
    @Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
    void withNoSettingNothingIsLoggedAndNothingIsWritten() {
        List<String> logged = linesLoggedBy(() -> {
            JobExecution job = aJob();
            InvocationAccount account = anAccount();
            account.beforeJob(job);
            account.afterJob(job);
        });

        claim("no warning was logged", () -> assertThat(logged).isEmpty());
        claim(
                "and no file of the retired account is in the logs folder, whatever the report wrote there",
                () -> assertThat(workingDirectory.resolve(LOGS))
                        .satisfiesAnyOf(
                                folder -> assertThat(folder).doesNotExist(),
                                folder -> assertThat(folder.toFile().list())
                                        .noneMatch(name -> name.startsWith(THE_RETIRED_ACCOUNTS_PREFIX))));
    }

    @Test
    @Story("The report's copy is written in the working directory's logs folder")
    @DisplayName("A configured vespera.account-dir sends nothing elsewhere, and one line says where the copy is")
    @Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
    void aConfiguredAccountFolderIsIgnoredWithOneLine() {
        List<String> logged = linesLoggedBy(() -> {
            JobExecution job = aJob();
            InvocationAccount account = new InvocationAccount(workingDirectory, accountDirectory, jdbc, report);
            account.beforeJob(job);
            account.afterJob(job);
        });

        claim(
                "the folder vespera.account-dir names gained nothing",
                () -> assertThat(accountDirectory).isEmptyDirectory());
        claim(
                "the one warning logged says the setting is no longer read and names the logs folder",
                () -> assertThat(logged)
                        .containsExactly(THE_SETTING_IS_NO_LONGER_READ
                                + workingDirectory.toAbsolutePath().resolve(LOGS)));
    }

    private InvocationAccount anAccount() {
        return new InvocationAccount(workingDirectory, null, jdbc, report);
    }

    /** What the account's logger warned of, as the messages an operator reads, while {@code invocation} ran. */
    private static List<String> linesLoggedBy(Runnable invocation) {
        Logger logger = (Logger) LoggerFactory.getLogger(InvocationAccount.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            invocation.run();
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
}
