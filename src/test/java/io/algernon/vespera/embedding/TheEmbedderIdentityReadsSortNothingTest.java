package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.StatementLog;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * The one read {@link RelevanceDistribution} makes of the embedder identities the {@code vector} table keeps
 * answers the identity a run's vectors carry and keeps nothing in temporary storage (ADR-224 section 1, as
 * ADR-228 amends it): it asks for the least and the greatest identity under an embedding model's name and
 * the artefact a run names for it, the manifest digest and the weight dtype, which are the same where
 * exactly one answers.
 *
 * <p>ADR-224 held two reads here, the second being the least identity over every model, for a stamp on the
 * label file. ADR-228 takes that read away: the file is stamped with the identity of the run it was written
 * under. And it narrows the first from the model's name to the name and the artefact, so that the vectors
 * an earlier pull left under the same name do not make the answer "none".
 *
 * <p>The database is the shipped {@code schema.sql} in memory, on a connection of this test's own, with a
 * few rows. A plan over a few rows is not a plan over millions: no size is held here, as none is held by
 * {@code EveryStatementThatSortsIsRecordedTest}.
 */
@Epic("Embedding")
@Feature("Embedder identity")
@Issue("477")
@Issue("488")
@Link(name = "ADR-224", url = Adr.THE_ACCOUNTS_COUNTS_AND_THE_EMBEDDER_IDENTITY_READS_SORT_NOTHING, type = "adr")
@Link(name = "ADR-218", url = Adr.EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE, type = "adr")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class TheEmbedderIdentityReadsSortNothingTest {

    /** The embedding model the vectors are stored under. */
    private static final String THE_MODEL = "alpha-embed";

    /** An embedding model no vector is stored under. */
    private static final String A_MODEL_NOBODY_STORED = "gamma-embed";

    private static final String DIGEST = "7f9c2ba4e88f827d616045507605853ed73b8093f6efbc88eb1a6eacfa66ef26";

    /** A second manifest digest, so that one model's name carries the identities of two pulls. */
    private static final String ANOTHER_DIGEST = "0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9";

    private static final String DTYPE = "F16";

    /** Each fixture vector has four components. */
    private static final int DIMENSION = 4;

    /** A second number of components, so that one artefact carries two identities. */
    private static final int ANOTHER_DIMENSION = 2;

    private static final float[] A_VECTOR = {1f, 0f, 0f, 0f};

    /** How many chunks are stored under each identity, so that an identity is on several rows. */
    private static final int CHUNKS_UNDER_EACH_IDENTITY = 3;

    private static final String CONTENT_HASH = "content-hash-of-one-survivor";
    private static final String CHUNKER = "chunker-identity";
    private static final String CHUNKING_RULE = "chunking-rule-identity";

    private SingleConnectionDataSource database;
    private StatementLog log;
    private VectorCache cache;
    private RelevanceDistribution distribution;

    @BeforeEach
    void anEmptyDatabaseOfTheShippedSchema() throws SQLException {
        database = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        try (Connection connection = database.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        log = new StatementLog(database);
        cache = new VectorCache(log.jdbcTemplate());
        distribution = new RelevanceDistribution(log.jdbcTemplate());
    }

    @AfterEach
    void closeTheDatabase() {
        database.destroy();
    }

    @Test
    @Story("An embedding model's identity is named only where exactly one answers to its name and artefact")
    @DisplayName("With no vector stored there is no identity to answer")
    void noIdentityWhereNoVectorIsStored() {
        claim(
                "no identity is answered over a table with no vector in it",
                () -> assertThat(identityUnder(THE_MODEL, DIGEST)).isEmpty());
    }

    @Test
    @Story("An embedding model's identity is named only where exactly one answers to its name and artefact")
    @DisplayName("The vectors of an earlier pull under the same name do not hide the identity of the pull asked about")
    void aSecondPullUnderTheNameDoesNotMakeItTwo() {
        store(THE_MODEL, DIGEST, DIMENSION);
        store(THE_MODEL, ANOTHER_DIGEST, DIMENSION);

        claim(
                "asked about the embedding model under the first digest, the identity answered is the one stored"
                        + " under it, though the same name carries a second: a run names the digest it was"
                        + " embedded under, so the other pull's vectors are not its concern",
                () -> assertThat(identityUnder(THE_MODEL, DIGEST)).contains(identityOf(THE_MODEL, DIGEST, DIMENSION)));
        claim(
                "and asked about it under the second digest, the answer is the second identity",
                () -> assertThat(identityUnder(THE_MODEL, ANOTHER_DIGEST))
                        .contains(identityOf(THE_MODEL, ANOTHER_DIGEST, DIMENSION)));
    }

    @Test
    @Story("An embedding model's identity is named only where exactly one answers to its name and artefact")
    @DisplayName("Two identities under one name and one artefact answer nothing, and so does a name nobody stored")
    void twoIdentitiesUnderOneArtefactAreNotAnswered() {
        store(THE_MODEL, DIGEST, DIMENSION);
        store(THE_MODEL, DIGEST, ANOTHER_DIMENSION);

        claim(
                "the embedding model stored under two identities of one digest and one weight format, which"
                        + " differ in the length of their vectors, answers neither: naming one would be a guess",
                () -> assertThat(identityUnder(THE_MODEL, DIGEST)).isEmpty());
        claim(
                "an embedding model no vector is stored under answers nothing",
                () -> assertThat(identityUnder(A_MODEL_NOBODY_STORED, DIGEST)).isEmpty());
    }

    @Test
    @Story("The identity of a run's vectors is read without sorting the vectors")
    @DisplayName("The read of the identities keeps no rows in temporary storage")
    void theReadKeepsNoRowsInTemporaryStorage() {
        store(THE_MODEL, DIGEST, DIMENSION);
        store(THE_MODEL, ANOTHER_DIGEST, DIMENSION);
        log.clear();

        identityUnder(THE_MODEL, DIGEST);
        List<String> reads = log.said();

        claim(
                "one statement was sent, and it read the table of vectors",
                () -> assertThat(reads).hasSize(1).allMatch(sql -> sql.contains("FROM vector")));
        claim(
                "the database does not plan it through a temporary B-tree: a read that did would sort a row for"
                        + " every vector stored, under every embedder, to answer one identity",
                () -> assertThat(reads)
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .as("the plan of: %s", sql)
                                .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    private Optional<String> identityUnder(String model, String digest) {
        return distribution.embedderIdentityFor(model, new ModelArtefact(digest, DTYPE));
    }

    /** Stores {@value #CHUNKS_UNDER_EACH_IDENTITY} chunks' vectors under the identity of the three. */
    private void store(String model, String digest, int dimension) {
        for (int ordinal = 0; ordinal < CHUNKS_UNDER_EACH_IDENTITY; ordinal++) {
            cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, ordinal, identityOf(model, digest, dimension), A_VECTOR);
        }
    }

    private static String identityOf(String model, String digest, int dimension) {
        return EmbedderIdentity.withoutInstruction(model, digest, DTYPE, dimension).value();
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a text, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) "x")
                .toArray();
        return log.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }
}
