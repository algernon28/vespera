package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The two readers of the stored vectors match what they are handed literally and whole: an underscore, a
 * percent sign or a backslash in it is the character itself, never a pattern or an escape, and a name never
 * matches a longer name that begins with it (ADR-084).
 *
 * <p>Since ADR-228 {@link VectorCache#vectorsFor} reads a document's vectors under one whole embedder
 * identity, and {@link RelevanceDistribution} names the one identity the vectors carry under an embedding
 * model's name and the artefact a run names for it, its manifest digest and weight dtype. The second still
 * matches the stored identity's leading parts with an SQL {@code LIKE} pattern, built in the identity's own
 * class (ADR-216 section 4), and each of the three parts is escaped: a weight dtype such as {@code Q4_K_M}
 * carries underscores as a matter of course.
 *
 * <p>The fixtures write vector rows directly through the cache, each pair under two identities that differ
 * only where a pattern built carelessly would let the first match the second.
 *
 * <p><b>Not held: letter case.</b> SQLite's {@code LIKE} folds the case of ASCII letters, so two stored
 * identities that differ only in case both answer to the pattern of either, and the read then names neither.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Embedding")
@Feature("Embedder identity")
@Issue("352")
@Issue("488")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class AModelNameMatchesOnlyItselfTest {

    /** A model whose name carries an underscore, which an unescaped pattern reads as any one character. */
    private static final String UNDERSCORED_MODEL = "acme_embed";

    /** A second model, named so that the first name, read as a pattern, would match it too. */
    private static final String LOOKALIKE_MODEL = "acmeXembed";

    /** A text that is only a percent sign, which an unescaped pattern reads as any text at all. */
    private static final String A_PERCENT_SIGN = "%";

    private static final String CONTENT_HASH = "content-hash-of-one-survivor";
    private static final String CHUNKER = "chunker-identity";
    private static final String CHUNKING_RULE = "chunking-rule-identity";
    private static final String DIGEST = "7f9c2ba4e88f827d616045507605853ed73b8093f6efbc88eb1a6eacfa66ef26";
    private static final String DTYPE = "F16";

    /** Each fixture vector has four components; the length plays no part in what is matched. */
    private static final int DIMENSION = 4;

    /** The one vector stored under the underscored model's identity. */
    private static final float[] UNDERSCORED_VECTOR = {1f, 0f, 0f, 0f};

    /** The one vector stored under the lookalike's identity. */
    private static final float[] LOOKALIKE_VECTOR = {0f, 1f, 0f, 0f};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("A survivor's vectors are read for the identity named, not for one whose model's name differs where the first has an underscore")
    void theVectorsReadAreTheNamedIdentitysOnly() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, UNDERSCORED_MODEL, UNDERSCORED_VECTOR);
        store(cache, LOOKALIKE_MODEL, LOOKALIKE_VECTOR);

        claim(
                "exactly the one vector stored under the identity named is read: the lookalike's vector, whose"
                        + " identity differs only where the first has an underscore, is not read with it",
                () -> assertThat(cache.vectorsFor(CONTENT_HASH, CHUNKER, CHUNKING_RULE, identityOf(UNDERSCORED_MODEL)))
                        .containsExactly(UNDERSCORED_VECTOR));
    }

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("A percent sign matches no stored model, as a name or as a digest")
    void aPercentSignMatchesNothingButItself() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, UNDERSCORED_MODEL, UNDERSCORED_VECTOR);

        claim(
                "no vector is read under a percent sign, though one model's vectors are stored: the sign matched"
                        + " itself and not any identity",
                () -> assertThat(cache.vectorsFor(CONTENT_HASH, CHUNKER, CHUNKING_RULE, A_PERCENT_SIGN)).isEmpty());
        claim(
                "no identity is found for a model named by one",
                () -> assertThat(identityFoundFor(A_PERCENT_SIGN, DIGEST, DTYPE)).isEmpty());
        claim(
                "and none for the stored model where the digest asked for is one: the digest is matched as"
                        + " literally as the name",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, A_PERCENT_SIGN, DTYPE)).isEmpty());
    }

    private Optional<String> identityFoundFor(String model, String digest, String dtype) {
        return new RelevanceDistribution(jdbcTemplate).embedderIdentityFor(model, new ModelArtefact(digest, dtype));
    }

    private static String identityOf(String digest, String dtype) {
        return EmbedderIdentity.withoutInstruction(UNDERSCORED_MODEL, digest, dtype, DIMENSION).value();
    }

    private static void store(VectorCache cache, String model, float[] vector) {
        cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, 0, identityOf(model), vector);
    }

    private static String identityOf(String model) {
        return EmbedderIdentity.withoutInstruction(model, DIGEST, DTYPE, DIMENSION).value();
    }
}
