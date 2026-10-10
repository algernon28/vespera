package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Which detected formats list their pictures in the deliverable, decided in {@code extraction} beside the
 * record that decides which pictures a converted occurrence carries (ADR-226, moving ADR-222's rule 6;
 * ADR-150 §4, ADR-167, ADR-168).
 *
 * <p>A picture Docling crops from a standalone picture file, a BMP or a video's frame is a re-sampled part
 * of the original and not a second thing worth carrying beside it, so those three list none. Every other
 * format lists the pictures its conversion carries.
 *
 * <p>Written before {@code DocumentPicture.listedFor} exists, so this class does not compile until the
 * build does.
 */
@Epic("Extraction")
@Feature("A document's pictures")
@Issue("479")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
@Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
class PicturesListedForADetectedFormatTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DetectedFormat.class, names = {"IMAGE", "BMP", "VIDEO"})
    @Story("A survivor that is itself a picture lists no picture")
    @DisplayName("An occurrence detected as a standalone picture, a BMP or a video lists no picture")
    void theseFormatsListNoPicture(DetectedFormat format) {
        claim(
                "its pictures are not listed: what Docling would crop from it is a re-sampled part of the"
                        + " original and not a second thing to carry beside it",
                () -> assertThat(DocumentPicture.listedFor(format)).isFalse());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DetectedFormat.class, names = {"IMAGE", "BMP", "VIDEO"}, mode = EnumSource.Mode.EXCLUDE)
    @Story("A survivor that is itself a picture lists no picture")
    @DisplayName("An occurrence detected as any other format lists the pictures its conversion carries")
    void everyOtherFormatListsItsPictures(DetectedFormat format) {
        claim(
                "its pictures are listed, so no format beyond the three is left without them by this rule",
                () -> assertThat(DocumentPicture.listedFor(format)).isTrue());
    }
}
