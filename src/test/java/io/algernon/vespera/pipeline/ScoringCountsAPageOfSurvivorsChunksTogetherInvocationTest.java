package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 5's scoring step asks how many chunks each survivor was cut into a page of survivors at a time, and
 * not with a statement of its own for each survivor (ADR-244). Scoring refuses a survivor with fewer vectors
 * than chunks (ADR-231 section 2), so the count is asked of every survivor scored; the seeds' counts are asked
 * as the seeds are read, before any survivor is scored, and are not this test's.
 *
 * <p>Every statement the pool's connections prepare is kept, whichever class issues it, as {@code
 * TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest} keeps them.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, ScoringCountsAPageOfSurvivorsChunksTogetherInvocationTest.KeptStatements.class})
@Epic("Relevance")
@Feature("Relevance scoring")
@Link(name = "ADR-244", url = Adr.THE_FLOORS_ANSWERS_ARE_UNDER_THE_RUNS_IDENTITY_AND_A_SHARING_VIOLATION_IS_TRIED_AGAIN, type = "adr")
@Link(name = "ADR-231", url = Adr.A_PULL_WHILE_THE_EMBEDDING_STEP_RUNS_STOPS_IT_AND_SCORING_REFUSES_A_SEED_WHOSE_CHUNKS_HAVE_NO_VECTOR, type = "adr")
class ScoringCountsAPageOfSurvivorsChunksTogetherInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** Documents of the corpus, every one a survivor scoring scores. */
    private static final int SURVIVORS = 12;

    /** The seed folder holds one document, whose chunks are counted by themselves as the seeds are read. */
    private static final int SEEDS = 1;

    /** One content hash's chunks counted by themselves. */
    private static final String ONE_HASHS_CHUNKS_COUNTED = "SELECT COUNT(*) FROM chunk_cache WHERE content_hash = ?";

    /** The chunks of a list of content hashes counted together. */
    private static final String A_LIST_OF_HASHES_CHUNKS_COUNTED = "FROM chunk_cache WHERE content_hash IN (";

    /** Keeps the text of every statement the pool's connections prepare, whichever class issues it. */
    @TestConfiguration
    static class KeptStatements {

        static final List<String> PREPARED = Collections.synchronizedList(new ArrayList<>());

        @Bean
        static BeanPostProcessor keepingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof DataSource pool) || bean instanceof Keeping) {
                        return bean;
                    }
                    return new Keeping(pool);
                }
            };
        }

        private static final class Keeping extends DelegatingDataSource {

            Keeping(DataSource pool) {
                super(pool);
            }

            @Override
            public Connection getConnection() throws SQLException {
                return kept(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return kept(super.getConnection(username, password));
            }
        }

        private static Connection kept(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    KeptStatements.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().startsWith("prepare") && arguments != null && arguments[0] instanceof String sql) {
                            PREPARED.add(sql);
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException thrown) {
                            throw thrown.getCause();
                        }
                    });
        }
    }

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void forgetWhatWasKept() {
        KeptStatements.PREPARED.clear();
    }

    @Test
    @Story("Survivors are scored a page at a time")
    @DisplayName("While the documents are scored against the seeds, how many pieces each was cut into is asked a page of documents at a time")
    void theChunksOfAPageOfSurvivorsAreCountedTogether(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);

        cli.run("run", root.toString());

        List<String> prepared = List.copyOf(KeptStatements.PREPARED);
        claim(
                "the invocation scored all " + SURVIVORS + " documents",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(scoresRecordedFor(root)).isEqualTo(SURVIVORS);
                });
        claim(
                "one document's pieces were counted by themselves only for the " + SEEDS + " seed, and for none of"
                        + " the " + SURVIVORS + " documents scored",
                () -> assertThat(count(prepared, ONE_HASHS_CHUNKS_COUNTED)).isEqualTo(SEEDS));
        claim(
                "the documents' pieces were counted in one statement for their page",
                () -> assertThat(count(prepared, A_LIST_OF_HASHES_CHUNKS_COUNTED)).isOne());
    }

    private static long count(List<String> prepared, String statement) {
        return prepared.stream().filter(sql -> sql.contains(statement)).count();
    }

    private long scoresRecordedFor(Path root) {
        Long scores = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_score s JOIN run ON run.id = s.run_id JOIN walk w ON w.id = run.walk_id"
                        + " WHERE w.root = ?",
                Long.class,
                Walk.canonicalRoot(root).toString());
        return scores == null ? 0 : scores;
    }

    /** Twelve documents and a seed, every gate of stage 5's scoring half open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int document = 0; document < SURVIVORS; document++) {
            Files.writeString(
                    root.resolve("corpus-" + document + ".txt"), "scored document number " + document + " of " + root.getFileName());
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document of " + seeds.getFileName());
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }
}
