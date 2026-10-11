package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.Deliverable;
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
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
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
 * While 6b writes the tree, what it lists of each survivor, its path, its content key and its score, is asked
 * of the database a page of survivors at a time, and not one survivor at a time in each of the tree's three
 * passes over them (ADR-240, which amends ADR-223's "a lookup by key for each survivor").
 *
 * <p><b>Where the tree begins.</b> Every statement the pool's connections prepare is kept, and the tree's are
 * those from the first page of the run's memberships on: that read, a page of {@code document_cluster} in
 * occurrence order, is the one {@code GenerationTasklet} makes for the tree's passes over every survivor and
 * for nothing else. What the generation walk asks before it, a score and a content key for each exemplar it
 * sends, is ADR-223 section 5's and is not counted.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest.KeptStatements.class})
@Epic("Synthesis")
@Feature("The listing of every document the tree arranges")
@Link(name = "ADR-240", url = Adr.THE_TREE_ASKS_ABOUT_A_PAGE_OF_SURVIVORS_AT_A_TIME, type = "adr")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
class TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String THE_READING_WINDOW = "4096";

    /** Documents of the corpus, every one a survivor the tree lists. */
    private static final int SURVIVORS = 12;

    /** The statement that reads a page of the run's memberships, which opens the tree's passes. */
    private static final String A_PAGE_OF_MEMBERSHIPS =
            "SELECT occurrence_id, winning_seed_occurrence_id, cluster_ordinal FROM document_cluster WHERE occurrence_id > ?";

    /** One occurrence's path, size and creation time, asked by its id. */
    private static final String ONE_PATH = "FROM file_occurrence WHERE id = ?";

    /** One occurrence's score under a run, asked by its key. */
    private static final String ONE_SCORE = "FROM relevance_score WHERE occurrence_id = ? AND run_id = ?";

    /** One occurrence's content key under a run, asked by its key. */
    private static final String ONE_CONTENT_KEY = "FROM extraction_cache_key WHERE occurrence_id = ? AND run_id = ?";

    /** The tree reads each survivor's pictures twice, for the furniture rule and for its group's file (ADR-149 section 9). */
    private static final int PICTURE_PASSES = 2;

    /**
     * Keeps the text of every statement the pool's connections prepare, whichever class issues it, as {@code
     * StageTwoRecordsItsCacheKeyInvocationTest} watches its key writes.
     */
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
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        KeptStatements.PREPARED.clear();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("The listing is written a page of documents at a time")
    @DisplayName("While the final documents are written, each document's path and score are asked a page of documents at a time, and its content key only where its pictures are read")
    void theTreeAsksAboutAPageOfSurvivorsAtATime(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        KeptStatements.PREPARED.clear();

        cli.run("run", root.toString());

        List<String> prepared = List.copyOf(KeptStatements.PREPARED);
        int treeBegins = indexOfFirst(prepared, A_PAGE_OF_MEMBERSHIPS);
        List<String> theTrees = treeBegins < 0 ? List.of() : prepared.subList(treeBegins, prepared.size());

        claim(
                "the invocation wrote a tree listing all " + SURVIVORS + " documents, and its reads were watched from"
                        + " the first page of the listing on",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(listedIn(theTreeOf(root))).isEqualTo(SURVIVORS);
                    assertThat(theTrees).isNotEmpty();
                });
        claim(
                "no document's score was asked for by itself while the tree was written: the scores of a page of"
                        + " documents are asked in one statement",
                () -> assertThat(count(theTrees, ONE_SCORE)).isZero());
        claim(
                "a path was asked for by itself fewer times than there are documents: only an exemplar's, once in"
                        + " each pass; a page of documents' paths are asked in one statement",
                () -> assertThat(count(theTrees, ONE_PATH)).isLessThan(SURVIVORS));
        claim(
                "and a content key was asked for by itself only where a document's pictures are read, "
                        + PICTURE_PASSES + " times for each of the " + SURVIVORS + " documents, and not again for"
                        + " the listing of its row",
                () -> assertThat(count(theTrees, ONE_CONTENT_KEY)).isLessThanOrEqualTo(PICTURE_PASSES * SURVIVORS));
    }

    private static int indexOfFirst(List<String> prepared, String statement) {
        for (int i = 0; i < prepared.size(); i++) {
            if (prepared.get(i).startsWith(statement)) {
                return i;
            }
        }
        return -1;
    }

    private static long count(List<String> prepared, String statement) {
        return prepared.stream().filter(sql -> sql.contains(statement)).count();
    }

    private long listedIn(Path tree) throws IOException {
        try (Stream<String> rows = Files.lines(tree.resolve(Deliverable.MANIFEST_FILE_NAME))) {
            return rows.count() - 1;
        }
    }

    private Path theTreeOf(Path root) {
        String generation = jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'generation' AND w.root = ? ORDER BY run.rowid DESC LIMIT 1",
                String.class,
                Walk.canonicalRoot(root).toString());
        return workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(generation);
    }

    /** Twelve documents, an exemplar, every gate before 6b open, walked once and the arrangement approved. */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        for (int document = 0; document < SURVIVORS; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "corpus document number " + document);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .generationContextWindow(THE_READING_WINDOW, "set by this test")
                .build());
        cli.run("run", root.toString());
        String arrangement = jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'arrangement' AND w.root = ? ORDER BY run.rowid DESC LIMIT 1",
                String.class,
                Walk.canonicalRoot(root).toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(new RunId(arrangement)), "read by this test")
                .build());
    }
}
