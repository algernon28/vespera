package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a stored vector says it was produced by (ADR-084, ADR-091): the whole embedder, composed from
 * what the runtime reports plus what the client sends, and never silently short of a part.
 *
 * <p>The composition is the point of the type. An identity assembled through a DTO that drops a
 * field would compose a blank where a digest belongs, and the vectors keyed under it would be filed
 * beside vectors from a different model under the same name — numbers that are not comparable,
 * reading as though they were.
 */
@Epic("Embedding")
@Feature("Embedder identity")
@Issue("103")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
@Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
class EmbedderIdentityTest {

    /** A model name as the profile resolves it, tag included — the tag is part of what was asked for. */
    private static final String MODEL = "qwen3-embedding:0.6b";

    /** A manifest digest as {@code /api/tags} reports it, over the layers and the modelfile. */
    private static final String DIGEST = "7f9c2ba4e88f827d616045507605853ed73b8093f6efbc88eb1a6eacfa66ef26";

    /** The weight dtype {@code /api/tags} reports; {@code F16} for a model nobody quantized. */
    private static final String DTYPE = "Q4_K_M";

    /** The dimension the client asked for — not the model's native one, which is larger. */
    private static final int DIMENSION = 1024;

    @Test
    @Story("An identity carries every part that changes what a vector means")
    @DisplayName("The composed value names the model, the digest, the dtype and the dimension")
    void namesEveryPart() {
        EmbedderIdentity identity = EmbedderIdentity.withoutInstruction(MODEL, DIGEST, DTYPE, DIMENSION);

        claim(
                "every part a reader would need to tell two embedders apart is in the value, rather"
                        + " than only the model name that both would share",
                () -> assertThat(identity.value()).contains(MODEL, DIGEST, DTYPE, String.valueOf(DIMENSION)));
    }

    @Test
    @Story("Empty instruction text and absent instruction text are different values")
    @DisplayName("An absent instruction reads as a sentinel, and an empty one reads as the empty value it is")
    void anAbsentInstructionIsNotAnEmptyOne() {
        EmbedderIdentity absent = EmbedderIdentity.withoutInstruction(MODEL, DIGEST, DTYPE, DIMENSION);
        EmbedderIdentity empty = EmbedderIdentity.withInstruction(MODEL, DIGEST, DTYPE, DIMENSION, "");

        claim(
                "an instruction nobody supplied reads as an explicit sentinel, since an omitted field"
                        + " would read as a field that failed to populate",
                () -> assertThat(absent.value()).contains("instruction=none"));
        claim(
                "a supplied-but-empty instruction is a value, not an absence (ADR-084), so the two"
                        + " compose differently and the vectors under them are keyed apart",
                () -> assertThat(empty.value()).isNotEqualTo(absent.value()));
    }

    @Test
    @Story("Empty instruction text and absent instruction text are different values")
    @DisplayName("An instruction whose text is literally the sentinel does not read as an absent one")
    void aSuppliedInstructionNeverCollidesWithTheSentinel() {
        EmbedderIdentity absent = EmbedderIdentity.withoutInstruction(MODEL, DIGEST, DTYPE, DIMENSION);
        EmbedderIdentity saysNone =
                EmbedderIdentity.withInstruction(MODEL, DIGEST, DTYPE, DIMENSION, EmbedderIdentity.NO_INSTRUCTION);

        claim(
                "an instruction someone actually supplied, whose text happens to be the same word the"
                        + " sentinel uses, composes differently from having supplied none — the one case a"
                        + " bare sentinel cannot tell apart, and it sits in a primary key",
                () -> assertThat(saysNone.value()).isNotEqualTo(absent.value()));
    }

    @Test
    @Story("An identity refuses a part it could not learn")
    @DisplayName("A blank digest is refused, rather than composing an identity with a hole where it belongs")
    void refusesABlankDigest() {
        claim(
                "a digest that arrived blank — the shape a DTO silently dropping the field produces —"
                        + " is refused when the identity is composed, not discovered later as a vector"
                        + " set keyed under a name that describes two different models",
                () -> assertThatThrownBy(() -> EmbedderIdentity.withoutInstruction(MODEL, "  ", DTYPE, DIMENSION))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("An identity refuses a part it could not learn")
    @DisplayName("A dimension of zero is refused, since no vector has zero components")
    void refusesADimensionNoVectorCouldHave() {
        claim(
                "a dimension of zero is what an unread or defaulted field looks like, and no embedder"
                        + " produces a vector with no components",
                () -> assertThatThrownBy(() -> EmbedderIdentity.withoutInstruction(MODEL, DIGEST, DTYPE, 0))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
