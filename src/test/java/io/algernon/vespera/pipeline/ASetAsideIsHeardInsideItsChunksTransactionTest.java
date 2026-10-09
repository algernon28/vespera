package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.extraction.ExtractionFaults;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * What ADR-214 section 14 rests on, held of the spring-batch-core this build ships: a set-aside in processing is
 * heard inside the transaction of the chunk it happened in, on that chunk's connection, so a fault row written
 * where it is heard commits with its chunk and needs no second connection. ADR-139 section 2 held every fault to
 * the end of the step because it took the contrary to be true.
 *
 * <p>A step of stage 2's shape, built here and not the assembled one: a chunk of three occurrences, the second
 * set aside by the exception stage 2 skips, the skip heard by a listener that writes the fault row through
 * {@code ExtractionFaults.write}, as {@code ExtractionFaultResolution.record} does, and a writer that looks, while
 * the chunk is still open, at what each connection of a pool of two can see. Nothing fails the step, so nothing
 * logs a stack trace.
 *
 * <p>It names nothing ADR-214 adds, and passes against the code before it: it pins the library, and fails the
 * day an upgrade of spring-batch-core hears a skip outside the chunk's transaction.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("458")
@Link(name = "ADR-214", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
class ASetAsideIsHeardInsideItsChunksTransactionTest {

    private static final int IN_THE_CHUNK = 3;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId run;
    private final List<OccurrenceId> occurrences = new ArrayList<>();

    @BeforeEach
    void threeOccurrences() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-heard-in-the-chunk"));
        run = ledger.runs().startRun("extraction", "t214", "{}", walk, List.of());
        for (int i = 0; i < IN_THE_CHUNK; i++) {
            jdbcTemplate.update(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
                    walk.value(),
                    "f" + i + ".pdf");
        }
        jdbcTemplate
                .queryForList("SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value())
                .forEach(id -> occurrences.add(new OccurrenceId(id)));
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("A file set aside is heard inside its chunk's transaction, so its fault row commits with the chunk")
    void theSkipIsHeardInsideTheChunksTransaction() throws Exception {
        DataSource dataSource = pool.jdbcTemplate().getDataSource();
        JdbcTemplate chunks = new JdbcTemplate(dataSource);
        ExtractionFaults faults = new ExtractionFaults(chunks);
        OccurrenceId setAside = occurrences.get(1);
        List<Boolean> inATransactionWhenHeard = new ArrayList<>();
        List<Connection> connectionWhenHeard = new ArrayList<>();
        List<Connection> connectionOfTheWrite = new ArrayList<>();
        List<Integer> faultRowsTheChunkSees = new ArrayList<>();
        List<Integer> faultRowsAnotherConnectionSees = new ArrayList<>();

        ResourcelessJobRepository jobRepository = new ResourcelessJobRepository();
        Step step = new StepBuilder("extraction-shaped", jobRepository)
                .<OccurrenceId, OccurrenceId>chunk(IN_THE_CHUNK)
                .transactionManager(new JdbcTransactionManager(dataSource))
                .reader(new ListItemReader<>(occurrences))
                .processor(occurrence -> {
                    if (occurrence.equals(setAside)) {
                        throw new ServiceScopeFailureException(occurrence, "internal", "the converter refused it");
                    }
                    return occurrence;
                })
                .writer(chunk -> {
                    connectionOfTheWrite.add(DataSourceUtils.getConnection(dataSource));
                    faultRowsTheChunkSees.add(chunks.queryForObject(
                            "SELECT COUNT(*) FROM extraction_fault WHERE run_id = ?", Integer.class, run.value()));
                    faultRowsAnotherConnectionSees.add(faultRowsSeenByAnotherConnection());
                })
                .faultTolerant()
                .skip(ServiceScopeFailureException.class)
                .skipLimit(IN_THE_CHUNK)
                .listener(new SkipListener<OccurrenceId, OccurrenceId>() {
                    @Override
                    public void onSkipInProcess(OccurrenceId item, Throwable t) {
                        boolean inATransaction = TransactionSynchronizationManager.isActualTransactionActive();
                        inATransactionWhenHeard.add(inATransaction);
                        // Asked only inside a transaction: outside one it would take a connection of the
                        // pool of two and keep it.
                        if (inATransaction) {
                            connectionWhenHeard.add(DataSourceUtils.getConnection(dataSource));
                        }
                        faults.write(item, run, "internal", "the converter refused it");
                    }
                })
                .build();

        JobParameters parameters = new JobParameters();
        JobInstance instance = jobRepository.createJobInstance("extraction-shaped-job", parameters);
        JobExecution jobExecution = jobRepository.createJobExecution(instance, parameters, new ExecutionContext());
        StepExecution stepExecution = jobRepository.createStepExecution("extraction-shaped", jobExecution);
        step.execute(stepExecution);

        claim(
                "the set-aside is heard once, inside a transaction, on the connection the chunk then writes on",
                () -> {
                    assertThat(inATransactionWhenHeard).containsExactly(true);
                    assertThat(connectionWhenHeard).hasSize(1);
                    assertThat(connectionOfTheWrite).hasSize(1);
                    assertThat(connectionWhenHeard.getFirst()).isSameAs(connectionOfTheWrite.getFirst());
                });
        claim(
                "while the chunk is open its fault row is there for the chunk and not yet for another connection:"
                        + " it is part of the chunk's transaction, uncommitted",
                () -> {
                    assertThat(faultRowsTheChunkSees).containsExactly(1);
                    assertThat(faultRowsAnotherConnectionSees).containsExactly(0);
                });
        claim(
                "and once the chunk commits the row stands, the step having completed and set one aside",
                () -> {
                    assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
                    assertThat(stepExecution.getProcessSkipCount()).isEqualTo(1);
                    assertThat(pool.jdbcTemplate()
                                    .queryForList(
                                            "SELECT occurrence_id FROM extraction_fault WHERE run_id = ?",
                                            Long.class,
                                            run.value()))
                            .containsExactly(setAside.value());
                });
    }

    /** The fault rows of the run, as a connection the chunk does not hold sees them. */
    private int faultRowsSeenByAnotherConnection() throws SQLException {
        try (Connection another = pool.connection();
                Statement statement = another.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM extraction_fault WHERE run_id = '" + run.value() + "'")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
