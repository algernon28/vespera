package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.extraction.ConfidenceDistribution;
import io.algernon.vespera.engine.extraction.ExtractorIdentity;
import io.algernon.vespera.engine.extraction.WordCountDistribution;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.similarity.DocumentFrequency;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The fixture of stage 3's reports: three text documents of 3, 30 and 300 words with a score each and the shingles of
 * {@link #SHINGLES}, taken through byte-level reduction and given the rows stage 2 would have written, and the
 * {@link ContentCensusTasklet} over them, shared by the tests of the content census's three reports.
 */
final class ContentCensusFixture {

    /** The words of each document of the fixture. */
    static final long[] WORDS = {3, 30, 300};

    /** The mean score of each document of the fixture: poor, good and excellent. */
    static final double[] SCORES = {0.10, 0.85, 0.95};

    /** The shingle hashes of each document: 11 is in all three, 21 in the first two, 31 and 41 in one each. */
    static final List<List<Long>> SHINGLES = List.of(List.of(11L, 21L), List.of(11L, 21L, 41L), List.of(11L, 31L));

    static final String FIVE_WORDS = "word:5";

    static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    static final Instant RAN_AT = Instant.parse("2026-11-09T10:00:00Z");

    private final Path stage1Working;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final Map<Path, ExecutionContext> invocations = new HashMap<>();

    ContentCensusFixture(JdbcTemplate jdbcTemplate, DataSource dataSource, Path stage1Working) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.stage1Working = stage1Working;
    }

    /** Stage 3's tasklet over the fixture of {@code root}, writing in {@code workingDirectory} at {@link #RAN_AT}. */
    ContentCensusTasklet tasklet(Path root, Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtraction(ledger, versions, root);
        StageRuns stageRuns = new StageRuns(
                ledger,
                versions,
                new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                        .getBeanProvider(ExtractorIdentity.class),
                new DegenerateOutputConfidenceFloor(null),
                null,
                null,
                null,
                new ProfileStore(stage1Working),
                null,
                null,
                null,
                null,
                PresetsFixture.shipped(),
                root,
                invocations.get(root));
        return new ContentCensusTasklet(
                new DocumentFrequency(jdbcTemplate, ledger),
                new ConfidenceDistribution(jdbcTemplate, ledger),
                new WordCountDistribution(jdbcTemplate, ledger),
                new DetectedFormats(jdbcTemplate),
                stageRuns,
                ledger,
                new ProfileStore(workingDirectory),
                Clock.fixed(RAN_AT, ZoneOffset.UTC),
                workingDirectory);
    }

    /** Walks {@code root} over the three documents, runs byte-level reduction, and writes each one's rows under stage 2's run. */
    private void walkedThroughExtraction(Ledger ledger, ImplementationVersions versions, Path root) throws Exception {
        for (int i = 0; i < WORDS.length; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "the content of document " + i + " in " + root.getFileName());
        }
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        versions,
                        new ProfileStore(stage1Working),
                        root,
                        stage1Working)
                .execute(null, step);
        invocations.put(root, InvocationRecordFixture.recordOf(step));
        RunId extractionRunId = new StageRuns(
                        ledger,
                        versions,
                        new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                                .getBeanProvider(ExtractorIdentity.class),
                        new DegenerateOutputConfidenceFloor(null),
                        null,
                        null,
                        null,
                        new ProfileStore(stage1Working),
                        null,
                        null,
                        null,
                        null,
                        PresetsFixture.shipped(),
                        root,
                        invocations.get(root))
                .extraction();
        for (int i = 0; i < WORDS.length; i++) {
            OccurrenceId occurrenceId =
                    ledger.occurrences().occurrenceId(walkId, new OccurrencePath("document-" + i + ".txt")).orElseThrow();
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count, mean_score)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(),
                    extractionRunId.value(),
                    "success",
                    0.5,
                    100,
                    90,
                    WORDS[i],
                    80,
                    0,
                    0,
                    SCORES[i]);
            for (long hash : SHINGLES.get(i)) {
                jdbcTemplate.update(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)",
                        occurrenceId.value(),
                        extractionRunId.value(),
                        FIVE_WORDS,
                        hash);
            }
        }
    }
}
