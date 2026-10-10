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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * The two reads {@link RelevanceDistribution} makes of the embedder identities the {@code vector} table
 * keeps answer what they answered and keep nothing in temporary storage (ADR-224 section 1): {@link
 * RelevanceDistribution#anyEmbedderIdentity} asks for the least identity, and {@link
 * RelevanceDistribution#embedderIdentityFor} for the least and the greatest under one embedding model's
 * name, which are the same where exactly one answers to it.
 *
 * <p>Before ADR-224 is built both statements carry {@code DISTINCT} and sort every row of {@code vector}
 * (ADR-218, row 15). The claims on what is answered pass against that code and have to go on passing; the
 * claim on the plans fails against it.
 *
 * <p>The database is the shipped {@code schema.sql} in memory, on a connection of this test's own, with a
 * few rows. A plan over a few rows is not a plan over millions: no size is held here, as none is held by
 * {@code EveryStatementThatSortsIsRecordedTest}.
 */
@Epic("Embedding")
@Feature("Embedder identity")
@Issue("477")
@Link(name = "ADR-224", url = Adr.THE_ACCOUNTS_COUNTS_AND_THE_EMBEDDER_IDENTITY_READS_SORT_NOTHING, type = "adr")
@Link(name = "ADR-218", url = Adr.EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE, type = "adr")
class TheEmbedderIdentityReadsSortNothingTest {

    /** An embedding model whose name sorts first of the two stored. */
    private static final String FIRST_MODEL = "alpha-embed";

    /** An embedding model whose name sorts after {@link #FIRST_MODEL}'s. */
    private static final String SECOND_MODEL = "beta-embed";

    /** An embedding model no vector is stored under. */
    private static final String A_MODEL_NOBODY_STORED = "gamma-embed";

    private static final String DIGEST = "7f9c2ba4e88f827d616045507605853ed73b8093f6efbc88eb1a6eacfa66ef26";

    /** A second manifest digest, so that one model's name carries two identities. */
    private static final String ANOTHER_DIGEST = "0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9";

    private static final String DTYPE = "F16";

    /** Each fixture vector has four components; the length plays no part in what is read. */
    private static final int DIMENSION = 4;

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
    @Story("The identity stamped on a report is read without sorting the vectors")
    @DisplayName("With no vector stored there is no identity to stamp")
    void noIdentityWhereNoVectorIsStored() {
        claim(
                "no identity is answered over a table with no vector in it",
                () -> assertThat(distribution.anyEmbedderIdentity()).isEmpty());
    }

    @Test
    @Story("The identity stamped on a report is read without sorting the vectors")
    @DisplayName("With vectors under two identities the stamp is the one that sorts first")
    void theLeastIdentityIsAnswered() {
        store(SECOND_MODEL, DIGEST);
        store(FIRST_MODEL, DIGEST);

        claim(
                "the identity answered is the first of the two in text order, though it was stored second:"
                        + " which of several is answered does not depend on the order they were written in",
                () -> assertThat(distribution.anyEmbedderIdentity()).contains(identityOf(FIRST_MODEL, DIGEST)));
    }

    @Test
    @Story("An embedding model's identity is named only where exactly one answers to its name")
    @DisplayName("One identity under a name is answered, two under one name answer nothing, and so does a name nobody stored")
    void oneIdentityUnderANameIsAnsweredAndTwoAreNot() {
        store(FIRST_MODEL, DIGEST);
        store(SECOND_MODEL, DIGEST);
        store(SECOND_MODEL, ANOTHER_DIGEST);

        claim(
                "the embedding model stored under one identity answers that identity",
                () -> assertThat(distribution.embedderIdentityFor(FIRST_MODEL)).contains(identityOf(FIRST_MODEL, DIGEST)));
        claim(
                "the embedding model stored under two identities answers neither: naming one would be a guess",
                () -> assertThat(distribution.embedderIdentityFor(SECOND_MODEL)).isEmpty());
        claim(
                "an embedding model no vector is stored under answers nothing",
                () -> assertThat(distribution.embedderIdentityFor(A_MODEL_NOBODY_STORED)).isEmpty());
    }

    @Test
    @Story("The identity stamped on a report is read without sorting the vectors")
    @DisplayName("Neither read of the identities keeps rows in temporary storage")
    void neitherReadKeepsRowsInTemporaryStorage() {
        store(FIRST_MODEL, DIGEST);
        store(SECOND_MODEL, DIGEST);
        log.clear();

        distribution.anyEmbedderIdentity();
        distribution.embedderIdentityFor(FIRST_MODEL);
        List<String> reads = log.said();

        claim(
                "two statements were sent, one for each read, and both read the table of vectors",
                () -> assertThat(reads).hasSize(2).allMatch(sql -> sql.contains("FROM vector")));
        claim(
                "the database plans neither through a temporary B-tree: a read that did would sort a row for"
                        + " every vector stored, under every embedder, to answer one or two identities",
                () -> assertThat(reads)
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .as("the plan of: %s", sql)
                                .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    /** Stores {@value #CHUNKS_UNDER_EACH_IDENTITY} chunks' vectors under the identity of {@code model} and {@code digest}. */
    private void store(String model, String digest) {
        for (int ordinal = 0; ordinal < CHUNKS_UNDER_EACH_IDENTITY; ordinal++) {
            cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, ordinal, identityOf(model, digest), A_VECTOR);
        }
    }

    private static String identityOf(String model, String digest) {
        return EmbedderIdentity.withoutInstruction(model, digest, DTYPE, DIMENSION).value();
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a text, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) "x")
                .toArray();
        return log.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }
}
