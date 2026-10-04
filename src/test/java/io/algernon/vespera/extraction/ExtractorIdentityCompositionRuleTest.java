package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the extractor identity is composed (ADR-012, ADR-090, ADR-147), in {@code extraction} since
 * ADR-189: the engine's name, the image, every entry of the sidecar's {@code /version} sorted by key,
 * and the options this client sends. Asking the sidecar, and checking that it runs the configured image
 * (ADR-179), stay in {@code pipeline}'s lazy bean, so nothing here calls anything.
 *
 * <p>The expected string is today's composition written out, so a change of a separator, of the order or
 * of a prefix fails here before it re-keys every cached conversion. {@code pipeline}'s {@code
 * ExtractorIdentityCompositionTest} pins the same string through the bean, unedited.
 */
@Epic("Extraction")
@Feature("Which engine produced a conversion")
@Issue("407")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
@Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
@Link(name = "ADR-147", url = Adr.THE_DOCLING_SIDECAR_IS_A_DERIVED_IMAGE, type = "adr")
class ExtractorIdentityCompositionRuleTest {

    private static final String IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0-r2";

    @Test
    @Story("The cache key carries the whole engine")
    @DisplayName("The identity is the engine, the image, every version the sidecar reports in key order, and the options sent")
    void theIdentityIsComposedInOneFixedShape() {
        Map<String, String> reported = new LinkedHashMap<>();
        reported.put("vespera-image", IMAGE);
        reported.put("docling-serve", "1.32.0");
        reported.put("docling", "2.124.0");

        ExtractorIdentity identity = ExtractorIdentity.composedOf(IMAGE, reported);

        claim(
                "it reads docling-serve, then the image, then each reported component as name=version in the"
                        + " order of their names, then the options this client sends, all joined by semicolons",
                () -> assertThat(identity.value())
                        .isEqualTo("docling-serve;image=" + IMAGE + ";docling=2.124.0;docling-serve=1.32.0;vespera-image="
                                + IMAGE + ";" + DoclingClient.sentOptions()));
    }

    @Test
    @Story("The cache key carries the whole engine")
    @DisplayName("The same versions reported in another order compose the same identity")
    void theOrderTheSidecarReportsInDoesNotMatter() {
        Map<String, String> oneOrder = new LinkedHashMap<>();
        oneOrder.put("docling", "2.124.0");
        oneOrder.put("docling-ibm-models", "3.9.0");
        oneOrder.put("vespera-image", IMAGE);
        Map<String, String> theReverse = new LinkedHashMap<>();
        theReverse.put("vespera-image", IMAGE);
        theReverse.put("docling-ibm-models", "3.9.0");
        theReverse.put("docling", "2.124.0");

        claim(
                "two runs against one unchanged sidecar key the same cached conversions, however its answer"
                        + " happened to list them -- an identity that reshuffled would convert the corpus again"
                        + " for nothing",
                () -> assertThat(ExtractorIdentity.composedOf(IMAGE, oneOrder))
                        .isEqualTo(ExtractorIdentity.composedOf(IMAGE, theReverse)));
    }
}
