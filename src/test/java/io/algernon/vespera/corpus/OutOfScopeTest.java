package io.algernon.vespera.corpus;

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

/**
 * {@link OutOfScope}'s three rules, moved from {@code pipeline} into {@code corpus} unchanged (ADR-188):
 * the kinds of file left out whatever they hold (ADR-146, ADR-167, ADR-168), a log (ADR-171), and text
 * too large to convert (ADR-171, amended by ADR-178). Every reason is pinned word for word as today's
 * stage 1 writes it.
 *
 * <p>The size rule's numbers and its in-parts question belong to {@code extraction}, which {@code corpus}
 * cannot name, so they arrive as {@link TextSizeLimits}. The predicate handed in here never reads the
 * file, so the path need not exist; one test hands in numbers far from today's, which is what shows
 * {@code corpus} keeps no number of its own.
 */
@Epic("Byte-level reduction")
@Feature("Out of scope")
@Issue("406")
@Link(name = "ADR-188", url = Adr.STAGE_1_RULES_LIVE_IN_CORPUS, type = "adr")
class OutOfScopeTest {

    private static final String SPREADSHEET_REASON = "a spreadsheet, and spreadsheets are out of scope";
    private static final String BMP_REASON = "a BMP image, and BMP images are out of scope";
    private static final String VIDEO_REASON = "a video, and videos are out of scope";

    @Test
    @Story("Which kinds of file are left out whatever they hold")
    @DisplayName("Spreadsheets, BMP images and videos are left out, each with a reason naming its kind")
    @Link(name = "ADR-146", url = Adr.SPREADSHEETS_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-167", url = Adr.BMP_IMAGES_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-168", url = Adr.VIDEOS_ARE_OUT_OF_SCOPE, type = "adr")
    void leavesOutEveryKindOutOfScope() {
        claim(
                "a workbook archive is a spreadsheet, and is left out as one",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.SPREADSHEET, Optional.empty()))
                        .contains(SPREADSHEET_REASON));
        claim(
                "an older compound file named as a spreadsheet is left out with the same reason",
                () -> assertThat(OutOfScope.reasonFor(
                                DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_SPREADSHEET)))
                        .contains(SPREADSHEET_REASON));
        claim(
                "a BMP image is left out with a reason of its own",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.BMP, Optional.empty())).contains(BMP_REASON));
        claim(
                "a video is left out with a reason of its own",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.VIDEO, Optional.empty())).contains(VIDEO_REASON));
    }

    @Test
    @Story("Which kinds of file are left out whatever they hold")
    @DisplayName("An older Word file, a PDF and a CSV file are not left out for their kind")
    @Link(name = "ADR-146", url = Adr.SPREADSHEETS_ARE_OUT_OF_SCOPE, type = "adr")
    void keepsEveryOtherKind() {
        claim(
                "an older compound file named as a Word file is kept: a spreadsheet is what is out of scope, not"
                        + " every older Office file",
                () -> assertThat(OutOfScope.reasonFor(
                                DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_WORD)))
                        .isEmpty());
        claim(
                "a PDF is kept",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.PDF, Optional.empty())).isEmpty());
        claim(
                "a CSV file is kept for its kind: it is text, not a spreadsheet",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.CSV)))
                        .isEmpty());
    }
}
