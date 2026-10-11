package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
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
 * What stage 6b's tree asks of the database for each survivor it lists, counted through the connections the
 * pool hands out, whichever class asks: the tree lists every survivor three times, for the furniture rule's
 * first pass, in its partition's cluster files and in the manifest (ADR-223 sections 6 and 7), and each listing
 * reads the survivors' scores, paths and cache keys a page at a time, by key (ADR-240). The manifest asks for the
 * places of a page's clusters together, a cluster's synthesis doc is read once, for its file and its row of the
 * index alike, and a survivor's pictures are looked up by the key its listing read (ADR-241), where each was
 * asked one cluster, one row or one survivor at a time.
 *
 * <p><b>The span counted</b> is the tree's: from the line that opens the first pass's read to the line that
 * closes the manifest's, which the step writes on the thread that makes every statement between them. The
 * lookups a survivor's pictures make, its detected format and its cached conversion, are one survivor at a time
 * in each of the two passes, by ADR-149 section 9 and ADR-150 section 4, and are left out of the counts by the
 * two tables they read, which nothing else in the span reads. Their key is the survivor's content hash, which
 * the listing hands them (ADR-241), and not a lookup of their own.
 *
 * <p><b>The fixture</b> is {@code GenerationGoesThroughTwoPartitionsInvocationTest}'s, made larger: thirty text
 * files walked and arranged under one seed, the arrangement approved and then replaced by three clusters of ten,
 * so that a page of survivors spans every cluster and there are many more survivors than clusters.
 *
 * <p>Report text says <em>group</em> where these names say cluster (ADR-122).
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TheTreeReadsEachClustersDocOnceAndAPagesPlacesTogetherInvocationTest.StatementProbe.class})
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-240", url = Adr.THE_TREE_ASKS_ABOUT_A_PAGE_OF_SURVIVORS_AT_A_TIME, type = "adr")
@Link(name = "ADR-241", url = Adr.THE_MANIFEST_ASKS_A_PAGES_PLACES_AND_A_SYNTHESIS_DOC_IS_READ_ONCE, type = "adr")
class TheTreeReadsEachClustersDocOnceAndAPagesPlacesTogetherInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String THE_READING_WINDOW = "4096";

    /** How many survivors the tree lists: many more than its clusters, and fewer than a page of 1,000. */
    private static final int SURVIVORS = 30;

    /** How many clusters the survivors are placed in, ten to a cluster. */
    private static final int CLUSTERS = 3;

    private static final List<String> CLUSTER_NAMES = List.of("Group one", "Group two", "Group three");

    /** An ordinal no cluster is arranged at, where every survivor waits before it is placed. */
    private static final int NO_CLUSTERS_ORDINAL = 99;

    private static final String STAGE_SIX_B = "Stage 6b (generation)";

    /** The line that opens the tree's first read, the first pass's. */
    private static final String THE_TREE_BEGINS = STAGE_SIX_B + " is reading the arranged occurrences, for their pictures";

    /** The line that closes the tree's last read, the manifest's. */
    private static final String THE_TREE_ENDS = STAGE_SIX_B + " read the arranged occurrences, for the manifest in";

    /** What a line of the log is kept as among the statements, so the two cannot be taken for each other. */
    private static final String A_LINE = "line: ";

    /**
     * What a survivor's pictures are looked up through, one survivor at a time: the format it was detected as,
     * and its cached conversion, read by its content hash.
     */
    private static final List<String> THE_PICTURES_LOOKUPS = List.of("from detected_format", "from extraction_cache where");

    /**
     * Keeps, in the order they come, the text of every statement the pool's connections prepare or run, and
     * every line the application writes, while {@link #keeping} is set.
     */
    @TestConfiguration
    static class StatementProbe {

        static final List<String> SAID = Collections.synchronizedList(new ArrayList<>());

        static volatile boolean keeping;

        @Bean
        static BeanPostProcessor statementKeepingDataSource() {
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

        private static void said(Object[] arguments) {
            if (keeping && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                SAID.add(sql);
            }
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
                    StatementProbe.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().startsWith("prepare")) {
                            said(arguments);
                        }
                        Object answer = forwarded(connection, method, arguments);
                        return answer instanceof Statement statement && method.getName().equals("createStatement")
                                ? kept(statement)
                                : answer;
                    });
        }

        private static Statement kept(Statement statement) {
            return (Statement) Proxy.newProxyInstance(
                    StatementProbe.class.getClassLoader(), new Class<?>[] {Statement.class}, (proxy, method, arguments) -> {
                        if (method.getName().startsWith("execute") || method.getName().equals("addBatch")) {
                            said(arguments);
                        }
                        return forwarded(statement, method, arguments);
                    });
        }

        private static Object forwarded(Object target, Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException thrown) {
                throw thrown.getCause();
            }
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

    private final AppenderBase<ILoggingEvent> lines = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            if (StatementProbe.keeping) {
                StatementProbe.SAID.add(A_LINE + event.getFormattedMessage());
            }
        }
    };

    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void keepTheLinesAmongTheStatements() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        SeedScriptedExtractionBeans.stopMovingAway();
        StatementProbe.keeping = false;
        StatementProbe.SAID.clear();
        lines.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(lines);
    }

    @AfterEach
    void stopKeeping() {
        StatementProbe.keeping = false;
        applicationLogger.detachAppender(lines);
        lines.stop();
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("Writing over the groups goes through one exemplar at a time")
    @DisplayName("Writing the final documents asks the database about its documents a page at a time, and reads each group's writing once")
    void theTreeReadsEachClustersDocOnceAndAPagesPlacesTogether(@TempDir Path root, @TempDir Path seeds) throws IOException {
        threeClustersOfTen(root, seeds);
        StatementProbe.keeping = true;

        cli.run("run", root.toString());

        StatementProbe.keeping = false;
        List<String> tree = theTreesStatements();
        Map<String, Integer> byText = new TreeMap<>();
        tree.stream()
                .filter(sql -> THE_PICTURES_LOOKUPS.stream().noneMatch(sql.toLowerCase(Locale.ROOT)::contains))
                .forEach(sql -> byText.merge(sql, 1, Integer::sum));
        long readsOfTheWriting =
                tree.stream().filter(sql -> sql.toLowerCase(Locale.ROOT).contains("from synthesis_doc")).count();

        claim(
                "the final documents were written, and between the first line of their first read and the last line"
                        + " of their last the database was asked something",
                () -> assertThat(byText).isNotEmpty());
        claim(
                "the writing over each of the " + CLUSTERS + " groups is read once, for its page and its row of the"
                        + " index alike",
                () -> assertThat(readsOfTheWriting).isEqualTo(CLUSTERS));
        claim(
                "no question the final documents ask, but the two a document's pictures are looked up by, is asked as"
                        + " many times as there are documents: the " + SURVIVORS + " documents, listed three times"
                        + " over, are looked up a page at a time and not one at a time",
                () -> assertThat(byText).allSatisfy((sql, times) -> assertThat(times)
                        .as("how many times this was asked: %s", sql)
                        .isLessThan(SURVIVORS)));
        claim(
                "all the questions the final documents ask, but the two a document's pictures are looked up by, are"
                        + " fewer than the " + SURVIVORS + " documents they list",
                () -> assertThat(byText.values().stream().mapToInt(Integer::intValue).sum())
                        .as("every question the final documents asked: %s", byText)
                        .isLessThan(SURVIVORS));
    }

    /** The statements made between the line that opens the tree's first read and the line that closes its last. */
    private static List<String> theTreesStatements() {
        List<String> said;
        synchronized (StatementProbe.SAID) {
            said = List.copyOf(StatementProbe.SAID);
        }
        int begins = -1;
        int ends = -1;
        for (int at = 0; at < said.size(); at++) {
            String entry = said.get(at);
            if (begins < 0 && entry.startsWith(A_LINE) && entry.contains(THE_TREE_BEGINS)) {
                begins = at;
            }
            if (begins >= 0 && entry.startsWith(A_LINE) && entry.contains(THE_TREE_ENDS)) {
                ends = at;
            }
        }
        if (begins < 0 || ends < 0) {
            throw new IllegalStateException("the tree's first or last read was not said: " + said);
        }
        return said.subList(begins, ends).stream().filter(entry -> !entry.startsWith(A_LINE)).toList();
    }

    /**
     * A corpus of thirty files, walked once and arranged, with that arrangement approved and then replaced by
     * three clusters of ten under the one seed, the files placed in turn.
     */
    private void threeClustersOfTen(Path root, Path seeds) throws IOException {
        for (int file = 1; file <= SURVIVORS; file++) {
            Files.writeString(root.resolve("corpus-" + file + ".txt"), "corpus document " + file);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every call here reads in the same window")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .build());

        RunId arrangement = theApprovedArrangement(root);
        String scoring = jdbcTemplate.queryForObject(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, arrangement.value());
        List<Long> survivors = jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM document_cluster WHERE run_id = ? ORDER BY occurrence_id", Long.class, scoring);
        if (survivors.size() != SURVIVORS) {
            throw new IllegalStateException("this fixture needs exactly " + SURVIVORS + " survivors grouped under the"
                    + " scoring run, and this walk produced " + survivors.size());
        }
        Long seed = jdbcTemplate.queryForObject(
                "SELECT winning_seed_occurrence_id FROM document_cluster WHERE run_id = ? AND occurrence_id = ?",
                Long.class,
                scoring,
                survivors.getFirst());
        jdbcTemplate.update("UPDATE document_cluster SET cluster_ordinal = ? WHERE run_id = ?", NO_CLUSTERS_ORDINAL, scoring);
        jdbcTemplate.update("DELETE FROM cluster WHERE run_id = ?", arrangement.value());
        for (int at = 0; at < SURVIVORS; at++) {
            jdbcTemplate.update(
                    "UPDATE document_cluster SET cluster_ordinal = ?, winning_seed_occurrence_id = ?"
                            + " WHERE run_id = ? AND occurrence_id = ?",
                    at % CLUSTERS,
                    seed,
                    scoring,
                    survivors.get(at));
            jdbcTemplate.update(
                    "UPDATE relevance_score SET winning_seed_occurrence_id = ? WHERE run_id = ? AND occurrence_id = ?",
                    seed,
                    scoring,
                    survivors.get(at));
        }
        for (int cluster = 0; cluster < CLUSTERS; cluster++) {
            jdbcTemplate.update(
                    "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                            + " document_count, partition_order, cluster_order) VALUES (?, ?, ?, ?, ?, 1, ?)",
                    arrangement.value(),
                    seed,
                    cluster,
                    CLUSTER_NAMES.get(cluster),
                    SURVIVORS / CLUSTERS,
                    cluster + 1);
        }
    }

    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }
}
