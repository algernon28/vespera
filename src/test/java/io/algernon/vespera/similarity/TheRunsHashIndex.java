package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.RunId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the tests of ADR-221 share about {@code shingle_by_hash}: the statement it is built with for one
 * stage-2 run, written once, and the one call of {@link ShingleHashIndex} that builds it.
 *
 * <p><b>The statement is written here a second time on purpose.</b> {@link #statementFor} is ADR-221 section
 * 1's text, character for character, and the tests that read {@code sqlite_master} hold what the code issues
 * to it. A test that asked the code for its own text would hold nothing.
 *
 * <p><b>The call is made by name</b>, through reflection, because these tests were written before the method
 * was (ADR-221, What is to be built): a test that named it would not compile, and no test of the tree could
 * then run. Until it is built {@link #buildFor} fails saying so. Once it is, the body of {@link #buildFor}
 * may be replaced by the call itself, {@code new ShingleHashIndex(jdbcTemplate).buildFor(stage2RunId,
 * progress)}, with nothing else changed.
 */
public final class TheRunsHashIndex {

    /** The index's name, unchanged since ADR-081. */
    public static final String NAME = "shingle_by_hash";

    /** The form of a run id {@code RunId.of} mints: a SHA-256 in lowercase hexadecimal, 64 characters. */
    public static final String A_MINTED_RUN_ID = "[0-9a-f]{64}";

    /** The whole-table form ADR-182 built, which a database last run under an earlier build may still hold. */
    public static final String THE_WHOLE_TABLE_FORM =
            "CREATE INDEX shingle_by_hash ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    private TheRunsHashIndex() {}

    /** ADR-221 section 1's statement for the stage-2 run {@code runId}, as {@code sqlite_master} then keeps it. */
    public static String statementFor(String runId) {
        return "CREATE INDEX shingle_by_hash ON shingle (shingle_parameter_identity, shingle_hash) WHERE run_id = '"
                + runId + "'";
    }

    /** The statement {@code sqlite_master} holds for the index at this moment, or nothing where there is none. */
    public static Optional<String> statementInTheDatabase(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate
                .queryForList(
                        "SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle' AND name = ?",
                        String.class,
                        NAME)
                .stream()
                .findFirst();
    }

    /**
     * {@code ShingleHashIndex.buildFor(RunId, SimilarityStatementProgress)}: what it returns, or the exception
     * it throws, as thrown.
     *
     * @throws AssertionError saying what is not built yet, where the class has no such method
     */
    @SuppressWarnings("unchecked")
    public static Optional<Duration> buildFor(
            JdbcTemplate jdbcTemplate, RunId stage2RunId, SimilarityStatementProgress progress) {
        Method buildFor;
        try {
            buildFor = ShingleHashIndex.class.getMethod("buildFor", RunId.class, SimilarityStatementProgress.class);
        } catch (NoSuchMethodException notBuiltYet) {
            throw new AssertionError(
                    "ADR-221 is not built yet: ShingleHashIndex has no public"
                            + " buildFor(RunId, SimilarityStatementProgress) returning Optional<Duration>",
                    notBuiltYet);
        }
        try {
            return (Optional<Duration>) buildFor.invoke(new ShingleHashIndex(jdbcTemplate), stage2RunId, progress);
        } catch (InvocationTargetException thrown) {
            if (thrown.getCause() instanceof RuntimeException asThrown) {
                throw asThrown;
            }
            throw new AssertionError("buildFor threw what no caller expects", thrown.getCause());
        } catch (IllegalAccessException notPublic) {
            throw new AssertionError("ShingleHashIndex.buildFor is to be public: pipeline calls it", notPublic);
        }
    }
}
