package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Both readers that find vectors by a model's name alone match that name literally: an underscore or a
 * percent sign in it is the character itself, never a pattern (ADR-084).
 *
 * <p>{@link VectorCache#vectorsFor} reads a survivor's vectors and {@link
 * RelevanceDistribution#embedderIdentityFor} names the one identity a model's scores were made under; each
 * matches the stored identity's leading {@code model=<name>;} with an SQL {@code LIKE} pattern. Until
 * ADR-216 each built and escaped that pattern itself, and no test held what either matched. ADR-216
 * section 4 has the pattern built in one place, the identity's own class; this holds the behaviour that
 * move has to keep, so it passes before the change and after it.
 *
 * <p>The fixtures write vector rows directly through the cache, under two identities whose model names
 * differ only where an unescaped underscore would match any one character.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Embedding")
@Feature("Embedder identity")
@Issue("352")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
class AModelNameMatchesOnlyItselfTest {

    /** A model whose name carries an underscore, which an unescaped pattern reads as any one character. */
    private static final String UNDERSCORED_MODEL = "nomic_embed";

    /** A second model, named so that the first name, read as a pattern, would match it too. */
    private static final String LOOKALIKE_MODEL = "nomicXembed";

    /** A name that is only a percent sign, which an unescaped pattern reads as any text at all. */
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
    @DisplayName("A survivor's vectors are read for the model named, not for a model whose name differs where the first has an underscore")
    void theVectorsReadAreTheNamedModelsOnly() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, UNDERSCORED_MODEL, UNDERSCORED_VECTOR);
        store(cache, LOOKALIKE_MODEL, LOOKALIKE_VECTOR);

        List<float[]> read = cache.vectorsFor(CONTENT_HASH, CHUNKER, CHUNKING_RULE, UNDERSCORED_MODEL);

        claim(
                "exactly the one vector stored under the named model is read: the underscore in its name matched"
                        + " an underscore, so the lookalike's vector, which differs only there, is not read with it",
                () -> assertThat(read).containsExactly(UNDERSCORED_VECTOR));
    }

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("The identity a model's scores were made under is found for the model named, though a lookalike is stored too")
    void theIdentityFoundIsTheNamedModels() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, UNDERSCORED_MODEL, UNDERSCORED_VECTOR);
        store(cache, LOOKALIKE_MODEL, LOOKALIKE_VECTOR);

        claim(
                "one identity is found and it is the named model's: had the underscore matched any character, two"
                        + " identities would answer and the reading would name neither",
                () -> assertThat(new RelevanceDistribution(jdbcTemplate).embedderIdentityFor(UNDERSCORED_MODEL))
                        .contains(identityOf(UNDERSCORED_MODEL)));
    }

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("A name that is only a percent sign matches no stored model")
    void aPercentSignMatchesNothingButItself() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, UNDERSCORED_MODEL, UNDERSCORED_VECTOR);

        claim(
                "no vector is read for a model named by a percent sign, though one model's vectors are stored:"
                        + " the sign matched itself and not any name",
                () -> assertThat(cache.vectorsFor(CONTENT_HASH, CHUNKER, CHUNKING_RULE, A_PERCENT_SIGN)).isEmpty());
        claim(
                "and no identity is found for it either",
                () -> assertThat(new RelevanceDistribution(jdbcTemplate).embedderIdentityFor(A_PERCENT_SIGN))
                        .isEmpty());
    }

    private static void store(VectorCache cache, String model, float[] vector) {
        cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, 0, identityOf(model), vector);
    }

    private static String identityOf(String model) {
        return EmbedderIdentity.withoutInstruction(model, DIGEST, DTYPE, DIMENSION).value();
    }
}
