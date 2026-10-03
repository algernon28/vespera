package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.SidecarVersionReport;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.HashMap;
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
 *
 * <p>The image in the key is the one configured, and since ADR-179 the sidecar's image reports its own
 * name in {@code /version} as {@code vespera-image}. The identity is composed only when the two agree:
 * a sidecar running another image, or not saying which, would otherwise have its conversions recorded
 * under a name that is not theirs, which is what happened to 1,398 of them on 2026-09-30.
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

    /** The processor build and the GPU build of the same Containerfile, as their tags read after ADR-179. */
    private static final String THE_CPU_IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    private static final String THE_GPU_IMAGE = "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

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

    @Test
    @Story("A conversion is recorded only under the image that made it")
    @DisplayName("A sidecar running the image configured gets its identity, which names the image both as configured and as the sidecar reported it")
    @Issue("373")
    @Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
    void composesTheIdentityForASidecarRunningTheConfiguredImage() {
        ExtractorIdentity identity = new ExtractionJobConfiguration()
                .extractorIdentity(reporting(versionsFrom(THE_CPU_IMAGE)), THE_CPU_IMAGE);

        claim(
                "the identity names the image as configured, as it always has",
                () -> assertThat(identity.value()).contains("image=" + THE_CPU_IMAGE));
        claim(
                "and names it again among the versions the sidecar reported, because the whole report goes in"
                        + " unfiltered",
                () -> assertThat(identity.value()).contains(SidecarVersionReport.IMAGE_ENTRY + "=" + THE_CPU_IMAGE));
    }

    @Test
    @Story("A conversion is recorded only under the image that made it")
    @DisplayName("A sidecar running another image than the one configured gets no identity, and the refusal names both images")
    @Issue("373")
    @Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
    void composesNoIdentityForASidecarRunningAnotherImage() {
        Throwable refused = catchThrowable(() -> new ExtractionJobConfiguration()
                .extractorIdentity(reporting(versionsFrom(THE_GPU_IMAGE)), THE_CPU_IMAGE));

        claim(
                "no identity is composed: the sidecar runs the GPU build while the configuration names the"
                        + " processor build, and every conversion would be recorded under the wrong one",
                () -> assertThat(refused).isInstanceOf(DoclingRunsAnotherImageException.class));
        claim(
                "the refusal names the image running and the image configured, so the operator can tell which"
                        + " of the two is the mistake",
                () -> assertThat(refused).hasMessageContaining(THE_GPU_IMAGE).hasMessageContaining(THE_CPU_IMAGE));
        claim(
                "and names the setting that changes the configured one",
                () -> assertThat(refused).hasMessageContaining("VESPERA_DOCLING_IMAGE"));
    }

    @Test
    @Story("A conversion is recorded only under the image that made it")
    @DisplayName("A sidecar that does not say which image it runs gets no identity, and the refusal names the image configured")
    @Issue("373")
    @Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
    void composesNoIdentityForASidecarThatDoesNotSayWhichImage() {
        Throwable refused = catchThrowable(() -> new ExtractionJobConfiguration()
                .extractorIdentity(reporting(THE_SAME_VERSIONS), THE_CPU_IMAGE));

        claim(
                "no identity is composed: the sidecar was built before its image could say its name, or is not"
                        + " Vespera's image at all",
                () -> assertThat(refused).isInstanceOf(DoclingRunsAnotherImageException.class));
        claim(
                "the refusal names the image configured",
                () -> assertThat(refused).hasMessageContaining(THE_CPU_IMAGE));
        claim(
                "and says to start the sidecars again with a fresh build, which gives the image its name",
                () -> assertThat(refused).hasMessageContaining("--build"));
    }

    /** The identity for a sidecar on {@link #THE_SAME_VERSIONS}, built as {@code image} and configured as it. */
    private static ExtractorIdentity identityOf(String image) {
        return new ExtractionJobConfiguration().extractorIdentity(reporting(versionsFrom(image)), image);
    }

    /** {@link #THE_SAME_VERSIONS}, from a sidecar whose image says it was built as {@code image}. */
    private static Map<String, String> versionsFrom(String image) {
        Map<String, String> versions = new HashMap<>(THE_SAME_VERSIONS);
        versions.put(SidecarVersionReport.IMAGE_ENTRY, image);
        return Map.copyOf(versions);
    }

    private static DoclingClient reporting(Map<String, String> versions) {
        return new DoclingClient("http://unused") {
            @Override
            public Map<String, String> version() {
                return versions;
            }
        };
    }
}
