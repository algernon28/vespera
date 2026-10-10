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
    private static final String UNDERSCORED_MODEL = "nomic_embed";

    /** A second model, named so that the first name, read as a pattern, would match it too. */
    private static final String LOOKALIKE_MODEL = "nomicXembed";

    /**
     * A model whose name carries a backslash. Under the reader's escape character, an unescaped backslash
     * would escape the letter after it, so the name would match {@link #UNBACKSLASHED_MODEL} and not itself.
     */
    private static final String BACKSLASHED_MODEL = "a\\b";

    /** The same name with the backslash left out, which a dropped backslash escape would match instead. */
    private static final String UNBACKSLASHED_MODEL = "ab";

    /**
     * A model whose name begins with all of {@link #UNDERSCORED_MODEL}'s, which a pattern that did not end the
     * name where the identity ends it would match as well.
     */
    private static final String LONGER_MODEL = "nomic_embed-v2";

    /** A text that is only a percent sign, which an unescaped pattern reads as any text at all. */
    private static final String A_PERCENT_SIGN = "%";

    private static final String CONTENT_HASH = "content-hash-of-one-survivor";
    private static final String CHUNKER = "chunker-identity";
    private static final String CHUNKING_RULE = "chunking-rule-identity";
    private static final String DIGEST = "7f9c2ba4e88f827d616045507605853ed73b8093f6efbc88eb1a6eacfa66ef26";
    private static final String DTYPE = "F16";

    /** A weight dtype as Ollama reports one for quantized weights: its underscores are part of it. */
    private static final String AN_UNDERSCORED_DTYPE = "Q4_K_M";

    /** A second dtype, written so that the first, read as a pattern, would match it too. */
    private static final String A_LOOKALIKE_DTYPE = "Q4XKXM";

    /** Each fixture vector has four components; the length plays no part in what is matched. */
    private static final int DIMENSION = 4;

    /** The one vector stored under the underscored model's identity. */
    private static final float[] UNDERSCORED_VECTOR = {1f, 0f, 0f, 0f};

    /** The one vector stored under the lookalike's identity. */
    private static final float[] LOOKALIKE_VECTOR = {0f, 1f, 0f, 0f};

    /** The one vector stored under the backslashed model's identity. */
    private static final float[] BACKSLASHED_VECTOR = {0f, 0f, 1f, 0f};

    /** The one vector stored under the identity of the same name without its backslash. */
    private static final float[] UNBACKSLASHED_VECTOR = {0f, 0f, 0f, 1f};

    /** The one vector stored under the longer model's identity. */
    private static final float[] LONGER_VECTOR = {1f, 1f, 0f, 0f};

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
    @DisplayName("The identity a model's vectors carry is found for the model named, though a lookalike is stored too")
    void theIdentityFoundIsTheNamedModels() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, UNDERSCORED_MODEL, UNDERSCORED_VECTOR);
        store(cache, LOOKALIKE_MODEL, LOOKALIKE_VECTOR);

        claim(
                "one identity is found and it is the named model's: had the underscore matched any character, two"
                        + " identities would answer and the reading would name neither",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, DIGEST, DTYPE))
                        .contains(identityOf(UNDERSCORED_MODEL)));
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

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("A name with a backslash in it is matched with the backslash, not as the same name without it")
    void aBackslashIsTheCharacterItself() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, BACKSLASHED_MODEL, BACKSLASHED_VECTOR);
        store(cache, UNBACKSLASHED_MODEL, UNBACKSLASHED_VECTOR);

        claim(
                "exactly the one vector stored under the identity with the backslash is read, not the vector of"
                        + " the same name without it",
                () -> assertThat(cache.vectorsFor(CONTENT_HASH, CHUNKER, CHUNKING_RULE, identityOf(BACKSLASHED_MODEL)))
                        .containsExactly(BACKSLASHED_VECTOR));
        claim(
                "and the identity found for that name is its own: the backslash matched a backslash",
                () -> assertThat(identityFoundFor(BACKSLASHED_MODEL, DIGEST, DTYPE))
                        .contains(identityOf(BACKSLASHED_MODEL)));
    }

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("A name is matched whole, not as the beginning of a longer model's name")
    void aNameIsNotMatchedAsTheBeginningOfALongerOne() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        store(cache, UNDERSCORED_MODEL, UNDERSCORED_VECTOR);
        store(cache, LONGER_MODEL, LONGER_VECTOR);

        claim(
                "one identity is found for the name, its own: the name ends where the identity ends it, and had"
                        + " the longer name matched too, two would answer and the reading would name neither",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, DIGEST, DTYPE))
                        .contains(identityOf(UNDERSCORED_MODEL)));
    }

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("A weight format with underscores in it is matched with them, not as any format of the same length")
    void anUnderscoreInTheWeightDtypeIsTheCharacterItself() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        String underscored = EmbedderIdentity.withoutInstruction(UNDERSCORED_MODEL, DIGEST, AN_UNDERSCORED_DTYPE, DIMENSION)
                .value();
        String lookalike = EmbedderIdentity.withoutInstruction(UNDERSCORED_MODEL, DIGEST, A_LOOKALIKE_DTYPE, DIMENSION)
                .value();
        cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, 0, underscored, UNDERSCORED_VECTOR);
        cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, 0, lookalike, LOOKALIKE_VECTOR);

        claim(
                "one identity is found for the format with underscores, its own: had an underscore matched any"
                        + " character, the lookalike format's identity would answer too and the reading would name"
                        + " neither",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, DIGEST, AN_UNDERSCORED_DTYPE))
                        .contains(underscored));
    }

    @Test
    @Story("A model is matched by its own name and nothing like it")
    @DisplayName("An underscore or a backslash in a digest, and a percent sign or a backslash in a weight format, are the characters themselves")
    void theDigestAndTheWeightDtypeAreMatchedLiterallyToo() {
        VectorCache cache = new VectorCache(jdbcTemplate);
        for (String digest : new String[] {"a_b", "axb", "c\\d", "cd"}) {
            cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, 0, identityOf(digest, DTYPE), UNDERSCORED_VECTOR);
        }
        for (String dtype : new String[] {"e\\f", "ef", "Q8"}) {
            cache.put(CONTENT_HASH, CHUNKER, CHUNKING_RULE, 0, identityOf(DIGEST, dtype), LOOKALIKE_VECTOR);
        }

        claim(
                "the digest with an underscore answers its own identity, though a digest with another character"
                        + " in the underscore's place is stored beside it",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, "a_b", DTYPE)).contains(identityOf("a_b", DTYPE)));
        claim(
                "the digest with a backslash answers its own, not that of the same digest without it",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, "c\\d", DTYPE)).contains(identityOf("c\\d", DTYPE)));
        claim(
                "the weight format with a backslash answers its own, not that of the same format without it",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, DIGEST, "e\\f")).contains(identityOf(DIGEST, "e\\f")));
        claim(
                "and a weight format that is only a percent sign answers nothing, though three formats are"
                        + " stored under that digest: the sign matched itself and not any format",
                () -> assertThat(identityFoundFor(UNDERSCORED_MODEL, DIGEST, A_PERCENT_SIGN)).isEmpty());
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
