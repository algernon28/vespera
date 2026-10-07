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
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * What stage 2 and seed extraction record of the key each looked the extraction cache up under
 * (ADR-206 sections 1 to 3), through a whole invocation: the recorded key is the SHA-256 of the file and
 * a key the cache holds; a key row is written wherever a metric row is, and nowhere else; the seed side
 * does the same under its own run; and every key is written by the thread the command was invoked on.
 *
 * <p>Nothing here names a class that reads or writes the table, so everything is asked of the tables in
 * SQL. Each expected hash is computed here with the JDK, never read back from the code under test.
 *
 * <p>The corpus holds six files, each with bytes of its own, since the extraction double answers by
 * content once it has stored an answer. Two share a size, so stage 1 hashes both and stage 2 uses its
 * hash; one has no peer of its size, so stage 2 hashes it itself; one the converter refuses, which is a
 * verdict against the file at once (ADR-143) and so has a metric row; one the converter reports a
 * timeout on, which is measured too and is never kept in the cache (ADR-183); and one the converter
 * fails on while blaming itself, which is set aside as a fault and has no metric row (ADR-139).
 *
 * <p>Each test walks folders of its own, so each reads its own runs out of the database the class
 * shares. Every claim about the key table fails until the table exists, by a statement SQLite refuses.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, StageTwoRecordsItsCacheKeyInvocationTest.KeyWriteProbe.class})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("349")
@Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
class StageTwoRecordsItsCacheKeyInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** Two documents of one size and different bytes: stage 1 hashes both, and neither is a copy. */
    private static final String SAME_SIZE_A = "same-size-a.txt";

    private static final String SAME_SIZE_B = "same-size-b.txt";

    /** A document no other file is the size of: stage 1 never hashes it, so stage 2 does. */
    private static final String NO_PEER_OF_ITS_SIZE = "no-peer-of-its-size.txt";

    private static final String REFUSED = SeedScriptedExtractionBeans.REFUSED_CONVERSION;
    private static final String SET_ASIDE = SeedScriptedExtractionBeans.CONVERTER_FAULT;

    /** A file the converter reports a timeout on: measured, and never kept among the conversions. */
    private static final String TIMED_OUT = SeedScriptedExtractionBeans.REPORTED_TIMEOUT;

    private static final String USABLE_SEED = "seed.txt";
    private static final String SEED_WITH_NO_TEXT = SeedScriptedExtractionBeans.EMPTY_SEED;

    /** The prefix {@code ConversionDispatch} names its conversion workers with. */
    private static final String A_CONVERSION_WORKER = "stage-2-conversion-";

    /** The seven kinds of run one invocation mints before anything is approved, as the ledger names them. */
    private static final List<String> THE_STAGES_AS_RECORDED = List.of(
            "byte-level-reduction",
            "extraction",
            "content-census",
            "content-redundancy",
            "seed-measurement",
            "embedding-scoring",
            "arrangement");

    /**
     * Records the thread every statement that inserts into the key table is prepared on, whichever
     * class issues it and however: the connections the pool hands out are wrapped, so nothing here
     * depends on how the write is made.
     */
    @TestConfiguration
    static class KeyWriteProbe {

        static final Set<String> THREADS_THAT_WROTE_A_KEY = ConcurrentHashMap.newKeySet();
        static final AtomicInteger KEY_WRITES = new AtomicInteger();

        static void forget() {
            THREADS_THAT_WROTE_A_KEY.clear();
            KEY_WRITES.set(0);
        }

        private static void seen(Object[] arguments) {
            if (arguments == null || arguments.length == 0 || !(arguments[0] instanceof String sql)) {
                return;
            }
            String statement = sql.toLowerCase(Locale.ROOT);
            if (statement.contains("insert") && statement.contains("extraction_cache_key")) {
                THREADS_THAT_WROTE_A_KEY.add(Thread.currentThread().getName());
                KEY_WRITES.incrementAndGet();
            }
        }

        @Bean
        static BeanPostProcessor keyWriteProbingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof DataSource pool) || bean instanceof Probing) {
                        return bean;
                    }
                    return new Probing(pool);
                }
            };
        }

        private static final class Probing extends DelegatingDataSource {

            Probing(DataSource pool) {
                super(pool);
            }

            @Override
            public Connection getConnection() throws SQLException {
                return watched(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return watched(super.getConnection(username, password));
            }
        }

        private static Connection watched(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    KeyWriteProbe.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        seen(arguments);
                        Object answer = forwarded(connection, method, arguments);
                        return answer instanceof Statement statement && method.getName().equals("createStatement")
                                ? watched(statement)
                                : answer;
                    });
        }

        private static Statement watched(Statement statement) {
            return (Statement) Proxy.newProxyInstance(
                    KeyWriteProbe.class.getClassLoader(), new Class<?>[] {Statement.class}, (proxy, method, arguments) -> {
                        seen(arguments);
                        return forwarded(statement, method, arguments);
                    });
        }

        private static Object forwarded(Object target, java.lang.reflect.Method method, Object[] arguments)
                throws Throwable {
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

    @BeforeEach
    void forgetWhatAnEarlierTestWrote() {
        KeyWriteProbe.forget();
    }

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("The key recorded for a converted document is the SHA-256 of its file, and a key the conversions are kept under")
    void recordsTheKeyEachConversionIsKeptUnder(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusAndItsSeeds(root, seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the precondition: the first stage hashed the two documents that share a size and no"
                        + " other, so the claims below cover a key the converting stage took from that"
                        + " stage's record and a key it had to work out itself",
                () -> assertThat(pathsTheFirstStageHashed(root)).containsExactlyInAnyOrder(SAME_SIZE_A, SAME_SIZE_B));
        Map<String, String> recorded = keysRecorded("extraction", root);
        for (String document : List.of(SAME_SIZE_A, SAME_SIZE_B, NO_PEER_OF_ITS_SIZE)) {
            String expected = sha256Of(root.resolve(document));
            claim(
                    "the key recorded for " + document + " is the SHA-256 of its bytes, written as lowercase"
                            + " hexadecimal and computed here with the JDK: the value recorded is the one"
                            + " the conversion was looked up under, character for character",
                    () -> assertThat(recorded).containsEntry(document, expected));
            claim(
                    "and a conversion is on record under exactly that value for " + document + ", so a"
                            + " later step that reads the key finds the conversion without the file",
                    () -> assertThat(conversionsOnRecordUnder(expected)).isPositive());
        }
    }

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("A key is recorded for every document that was measured, and for no other")
    void recordsAKeyWhereverAMeasurementIsRecorded(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusAndItsSeeds(root, seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the precondition: the converting stage measured the three documents it converted, the"
                        + " one the converter refused and the one it reported a timeout on, each of which"
                        + " is an answer about that file; and it measured nothing for the one the"
                        + " converter failed on while blaming itself, which is no answer about the file",
                () -> assertThat(pathsMeasured("extraction", root))
                        .containsExactlyInAnyOrder(
                                SAME_SIZE_A, SAME_SIZE_B, NO_PEER_OF_ITS_SIZE, REFUSED, TIMED_OUT));
        claim(
                "the documents with a key on record are exactly the documents with a measurement on"
                        + " record: the refused one has its key, since its refusal is kept under it, and"
                        + " the one that was set aside has none, since nothing is kept for it and nothing"
                        + " later asks about it",
                () -> assertThat(keysRecorded("extraction", root).keySet())
                        .containsExactlyInAnyOrderElementsOf(pathsMeasured("extraction", root)));
        claim(
                "the refused document's key is the SHA-256 of its bytes, like any other",
                () -> assertThat(keysRecorded("extraction", root))
                        .containsEntry(REFUSED, sha256Of(root.resolve(REFUSED))));
        String theTimedOutDocumentsHash = sha256Of(root.resolve(TIMED_OUT));
        claim(
                "the document the converter reported a timeout on has its key too, the SHA-256 of its"
                        + " bytes, although no answer is kept under it: a reported timeout is measured and"
                        + " is asked again next time, so the key is on record and the conversions hold"
                        + " nothing for it",
                () -> {
                    assertThat(keysRecorded("extraction", root)).containsEntry(TIMED_OUT, theTimedOutDocumentsHash);
                    assertThat(conversionsOnRecordUnder(theTimedOutDocumentsHash)).isZero();
                });
    }

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("Seed extraction records each converted seed's key too, under its own run")
    void recordsEachSeedsKeyUnderTheMeasurementRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusAndItsSeeds(root, seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the precondition: seed extraction measured both seeds, the one with text and the one"
                        + " the converter found no text in",
                () -> assertThat(pathsMeasured("seed-measurement", seeds))
                        .containsExactlyInAnyOrder(USABLE_SEED, SEED_WITH_NO_TEXT));
        claim(
                "both seeds have a key on record under the run that measured them, each the SHA-256 of"
                        + " its file computed here with the JDK: the steps that embed and score against"
                        + " the seeds read it from there and do not hash a seed again",
                () -> assertThat(keysRecorded("seed-measurement", seeds))
                        .containsOnly(
                                Map.entry(USABLE_SEED, sha256Of(seeds.resolve(USABLE_SEED))),
                                Map.entry(SEED_WITH_NO_TEXT, sha256Of(seeds.resolve(SEED_WITH_NO_TEXT)))));
    }

    @Test
    @Story("One thread writes everything the converting stage records")
    @DisplayName("Every key is written by the thread the command was invoked on, and by no conversion worker")
    @Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
    void writesEveryKeyOnTheThreadTheCommandWasInvokedOn(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusAndItsSeeds(root, seeds);
        String invokedOn = Thread.currentThread().getName();

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "keys were written while this test watched, so the next claim is not about an empty set",
                () -> assertThat(KeyWriteProbe.KEY_WRITES.get()).isPositive());
        claim(
                "every statement that wrote a key was issued from one thread, the one the command was"
                        + " invoked on: conversions run on threads of their own, and a write from one of"
                        + " them would need a second connection while the step holds the only writer's",
                () -> assertThat(KeyWriteProbe.THREADS_THAT_WROTE_A_KEY).containsExactly(invokedOn));
        claim(
                "and none of them is a conversion worker, by name",
                () -> assertThat(KeyWriteProbe.THREADS_THAT_WROTE_A_KEY)
                        .noneMatch(thread -> thread.startsWith(A_CONVERSION_WORKER)));
    }

    @Test
    @Story("What is already recorded keeps its names")
    @DisplayName("The runs and the converting stage's finished step are recorded under the names they had")
    void keepsTheRecordedNames(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusAndItsSeeds(root, seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the " + THE_STAGES_AS_RECORDED.size() + " kinds of run this invocation mints are recorded"
                        + " under the names they have always had, letter for letter: recording a key"
                        + " renames nothing a database already holds",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT DISTINCT r.stage FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ?",
                                String.class,
                                Walk.canonicalRoot(root).toString()))
                        .containsExactlyInAnyOrderElementsOf(THE_STAGES_AS_RECORDED));
        claim(
                "and the converting stage records its one finished step under the name \"extraction\"",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT f.step FROM finished_step f JOIN run r ON r.id = f.run_id"
                                        + " JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'extraction' AND w.root = ?",
                                String.class,
                                Walk.canonicalRoot(root).toString()))
                        .containsExactly("extraction"));
    }

    /** The key recorded for each file under {@code folder}, by path, under runs of {@code stage}. */
    private Map<String, String> keysRecorded(String stage, Path folder) {
        Map<String, String> byPath = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT fo.path, k.content_hash FROM extraction_cache_key k JOIN run r ON r.id = k.run_id"
                        + " JOIN file_occurrence fo ON fo.id = k.occurrence_id JOIN walk w ON w.id = fo.walk_id"
                        + " WHERE r.stage = ? AND w.root = ?",
                row -> {
                    byPath.put(row.getString("path"), row.getString("content_hash"));
                },
                stage,
                Walk.canonicalRoot(folder).toString());
        return byPath;
    }

    /** The paths of the files under {@code folder} with a metric row under a run of {@code stage}. */
    private List<String> pathsMeasured(String stage, Path folder) {
        return jdbcTemplate.queryForList(
                "SELECT fo.path FROM extraction_metric m JOIN run r ON r.id = m.run_id"
                        + " JOIN file_occurrence fo ON fo.id = m.occurrence_id JOIN walk w ON w.id = fo.walk_id"
                        + " WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(folder).toString());
    }

    private List<String> pathsTheFirstStageHashed(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT fo.path FROM content_hash ch JOIN file_occurrence fo ON fo.id = ch.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    private int conversionsOnRecordUnder(String contentHash) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_cache WHERE content_hash = ?", Integer.class, contentHash);
    }

    /** Six corpus files and two seeds, each with bytes of its own, and every gate before stage 6 open. */
    private void aCorpusAndItsSeeds(Path root, Path seeds) throws IOException {
        String stamp = root.getFileName().toString();
        Files.writeString(root.resolve(SAME_SIZE_A), "one of two documents of a size, the first, in " + stamp);
        Files.writeString(root.resolve(SAME_SIZE_B), "one of two documents of a size, the other, in " + stamp);
        Files.writeString(root.resolve(NO_PEER_OF_ITS_SIZE), "a document no other file here is the size of, in " + stamp);
        Files.writeString(root.resolve(REFUSED), "a file the converter refuses to open, whatever it holds, in " + stamp);
        Files.writeString(root.resolve(SET_ASIDE), "a file the converter fails on and blames itself for, kept in " + stamp);
        Files.writeString(root.resolve(TIMED_OUT), "a file the converter says it ran out of time on, here in the folder " + stamp);
        Files.writeString(seeds.resolve(USABLE_SEED), "a seed document with text of its own, for " + stamp);
        Files.writeString(seeds.resolve(SEED_WITH_NO_TEXT), "an intact file the converter finds no text in, for " + stamp);
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** The SHA-256 of {@code file}'s bytes as lowercase hexadecimal, computed with the JDK alone. */
    private static String sha256Of(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
