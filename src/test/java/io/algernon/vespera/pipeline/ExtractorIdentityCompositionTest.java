package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the extraction cache keys a conversion by (ADR-090, ADR-147): the sidecar's reported versions,
 * the options the client sends, and the image the sidecar runs.
 *
 * <p>The image is in the key because it was measured to matter where the versions do not say so.
 * On 2026-09-24 the stock image and the one with LibreOffice reported the same {@code /version} map,
 * and 5 of 14 PDFs converted identically on two stock runs and differently on the LibreOffice one.
 */
@Epic("Extraction")
@Feature("Which engine produced a conversion")
@Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
@Link(name = "ADR-147", url = Adr.THE_DOCLING_SIDECAR_IS_A_DERIVED_IMAGE, type = "adr")
class ExtractorIdentityCompositionTest {

    /** The versions both images reported on 2026-09-24, word for word. */
    private static final Map<String, String> THE_SAME_VERSIONS = Map.of(
            "docling-serve", "1.32.0",
            "docling", "2.124.0",
            "docling-core", "2.93.0");

    private static final String THE_STOCK_IMAGE = "quay.io/docling-project/docling-serve-cpu:v1.32.0";

    private static final String THE_LIBREOFFICE_IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0";

    @Test
    @Story("A conversion is keyed by everything that decides it")
    @DisplayName("Two sidecars reporting the same versions from different images key their conversions apart")
    void keysTheSameVersionsFromTwoImagesApart() {
        ExtractorIdentity stock = identityOf(THE_STOCK_IMAGE);
        ExtractorIdentity withLibreOffice = identityOf(THE_LIBREOFFICE_IMAGE);

        claim(
                "the two identities differ, so a conversion one image made is never read back as the other's"
                        + " -- which is what happened to five PDFs when only the versions were in the key",
                () -> assertThat(stock).isNotEqualTo(withLibreOffice));
        claim(
                "and the identity names the image, so an operator reading a cache row can tell which one"
                        + " made it",
                () -> assertThat(withLibreOffice.value()).contains("image=" + THE_LIBREOFFICE_IMAGE));
        claim(
                "while the versions and the options the client sends are still in it, as ADR-090 put them",
                () -> assertThat(withLibreOffice.value())
                        .contains("docling=2.124.0")
                        .contains(DoclingClient.sentOptions()));
    }

    private static ExtractorIdentity identityOf(String image) {
        DoclingClient reportingTheSameVersions = new DoclingClient("http://unused") {
            @Override
            public Map<String, String> version() {
                return THE_SAME_VERSIONS;
            }
        };
        return new ExtractionJobConfiguration().extractorIdentity(reportingTheSameVersions, image);
    }
}
