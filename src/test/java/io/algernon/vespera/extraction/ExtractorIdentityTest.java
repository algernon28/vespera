package io.algernon.vespera.extraction;

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
 * The identity of the engine a conversion was produced under refuses to be empty (ADR-012).
 *
 * <p>This is the refusal that protects the cache. Which runtime serves is configuration, so the only
 * thing keeping one engine's output from being read back for another is that the whole engine
 * identity is part of the key — and an empty identity is the one value that would collapse every
 * engine into the same key while still looking like a key. Refusing it at construction is why no
 * later reader has to check.
 */
@Epic("Extraction")
@Feature("Which engine produced a conversion")
@Issue("46")
@Link(name = "ADR-012", url = Adr.EXTRACTION_ENGINE_IS_CONFIGURABLE, type = "adr")
class ExtractorIdentityTest {

    /** An identity composed the way whoever mints a run against a configured engine composes it. */
    private static final String A_COMPOSED_IDENTITY = "docling-serve/1.9.0;pdf-pipeline=standard";

    @Test
    @Story("An engine identity is never empty")
    @DisplayName("An engine identity that was never supplied is refused")
    void refusesAMissingIdentity() {
        claim(
                "no identity at all is refused where it is built, rather than becoming a key every"
                        + " engine's output would share",
                () -> assertThatThrownBy(() -> new ExtractorIdentity(null))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("identity"));
    }

    @Test
    @Story("An engine identity is never empty")
    @DisplayName("An engine identity that is empty, or only spacing, is refused too")
    void refusesAnEmptyOrBlankIdentity() {
        claim(
                "an empty identity is refused, since it names no engine",
                () -> assertThatThrownBy(() -> new ExtractorIdentity(""))
                        .isInstanceOf(IllegalArgumentException.class));
        claim(
                "and so is one made only of spacing, which names no engine either but would otherwise"
                        + " pass an emptiness check",
                () -> assertThatThrownBy(() -> new ExtractorIdentity("   \t"))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("An engine identity is never empty")
    @DisplayName("A supplied engine identity is carried exactly as composed")
    void carriesAComposedIdentityVerbatim() {
        claim(
                "the composed identity is carried through untouched — this type refuses an empty value"
                        + " and interprets nothing else, because what belongs in it is the caller's to"
                        + " decide",
                () -> assertThat(new ExtractorIdentity(A_COMPOSED_IDENTITY).value()).isEqualTo(A_COMPOSED_IDENTITY));
    }
}
