package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pictures shown under one membership entry (ADR-149, ADR-150), as one class: the first pass over
 * every survivor that finds which pictures are furniture, and the second, one entry at a time, that
 * writes what is left.
 *
 * <p><b>Not {@link DeliverablePicturesTest}.</b> That class holds the same rules end to end, through
 * {@code Deliverable.writeTo} and the whole tree, and is left as it is. This one holds the collaborator
 * itself, so a rule can be read without a tree around it. The class was named {@code EntryPictures}
 * rather than {@code DeliverablePictures} so that the two test classes are not read as one another's
 * unit test (ADR-212 §1).
 *
 * <p><b>Every expected line here is what {@code Deliverable.appendPictures} wrote at {@code 4b99a03}</b>;
 * one file name is pinned as a literal, read off a tree that commit wrote.
 */
@Epic("Synthesis")
@Feature("The pictures a document carries")
@Issue("351")
@Link(name = "ADR-212", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
class EntryPicturesTest {

    /** The directory beside the page that its pictures are written to: the page's name without {@code .md}. */
    private static final String THE_PICTURE_DIRECTORY = "1-fire-suppression-retrofits";

    /** The bytes of a picture only one document carries. */
    private static final byte[] A_DIAGRAM = bytes("a diagram");

    /** That picture's file name as the deliverable wrote it at {@code 4b99a03}: sixteen hex characters of its SHA-256. */
    private static final String THE_DIAGRAMS_FILE = "622f47263cf0fe5d.png";

    /** The bytes of a picture two documents carry, which makes it furniture in both. */
    private static final byte[] A_SHARED_LOGO = bytes("a shared logo");

    /** The bytes of a picture Docling placed in a page header. */
    private static final byte[] IN_THE_HEADER = bytes("in the header");

    /** The indent under a one-digit entry: the width of {@code "1. "}. */
    private static final String UNDER_A_ONE_DIGIT_ENTRY = "   ";

    /** The indent under a two-digit entry: the width of {@code "10. "}. */
    private static final String UNDER_A_TWO_DIGIT_ENTRY = "    ";

    /** A caption carrying a link, an image, a tag, an entity, a code span, a backslash and a blank line. */
    private static final String A_HOSTILE_CAPTION =
            "Fig. 2 a]b ![x](https://example.com/i.png) <b>bold</b> &copy; `c` \\ end\n\nsecond line";

    /** That caption as alt text: folded, then escaped by the membership entry's rule (ADR-149 §5). */
    private static final String THAT_CAPTION_AS_ALT_TEXT =
            "Fig. 2 a\\]b !\\[x\\](https://example.com/i.png) \\<b>bold\\</b> \\&copy; \\`c\\` \\\\ end second line";

    private static final ListedSurvivor FIRST = survivor(10, "reports/a.docx");

    private static final ListedSurvivor SECOND = survivor(11, "reports/b.docx");

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("The first pass asks each document once, however often it is listed, and reports each one")
    void asksEachDocumentOnceInTheFirstPass() {
        Map<OccurrenceId, Integer> asked = new HashMap<>();
        List<String> reported = new ArrayList<>();
        DeliverableProgress recording = new DeliverableProgress() {
            @Override
            public void toListPictures(long survivors) {
                reported.add("to list " + survivors);
            }

            @Override
            public void picturesListed() {
                reported.add("listed");
            }
        };

        EntryPictures.among(
                List.of(FIRST, SECOND, FIRST),
                occurrence -> {
                    asked.merge(occurrence, 1, Integer::sum);
                    return List.of();
                },
                recording);

        claim(
                "each of the two documents is asked for its pictures once in this pass, the one listed twice"
                        + " included, so the second pass's ask is the second and last (ADR-149)",
                () -> assertThat(asked).containsOnlyKeys(FIRST.occurrence(), SECOND.occurrence())
                        .allSatisfy((occurrence, count) -> assertThat(count).isOne()));
        claim(
                "the pass announces the two distinct documents before it begins and reports each as listed",
                () -> assertThat(reported).containsExactly("to list 2", "listed", "listed"));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture two documents share, and one in the converter's page furniture, are furniture; a unique one is not")
    void findsWhatIsFurniture() {
        EntryPictures pictures = EntryPictures.among(
                List.of(FIRST, SECOND),
                source(Map.of(
                        FIRST.occurrence(), List.of(png(A_DIAGRAM), png(A_SHARED_LOGO)),
                        SECOND.occurrence(), List.of(png(A_SHARED_LOGO), furnitureLayer(IN_THE_HEADER)))),
                DeliverableProgress.NONE);

        claim(
                "the furniture is named by each picture's SHA-256: the logo both documents carry, and the picture"
                        + " the converter put in a page header, and not the diagram only one document carries",
                () -> assertThat(pictures.furniture())
                        .containsExactlyInAnyOrder(sha256Hex(A_SHARED_LOGO), sha256Hex(IN_THE_HEADER)));
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("A picture is written beside the page under its digest's name and shown, indented, under its entry")
    void writesAPictureAndShowsItUnderTheEntry(@TempDir Path pageDirectory) throws IOException {
        EntryPictures pictures = EntryPictures.among(
                List.of(FIRST), source(Map.of(FIRST.occurrence(), List.of(png(A_DIAGRAM)))), DeliverableProgress.NONE);
        StringBuilder page = new StringBuilder();

        pictures.appendUnder(page, FIRST, 1, pageDirectory, THE_PICTURE_DIRECTORY);

        claim(
                "the entry gains one line, indented under a one-digit entry, showing the picture with empty alt"
                        + " text from the page's picture directory under the name the deliverable gave it",
                () -> assertThat(page.toString())
                        .isEqualTo(UNDER_A_ONE_DIGIT_ENTRY + "![](" + THE_PICTURE_DIRECTORY + "/" + THE_DIAGRAMS_FILE
                                + ")\n\n"));
        claim(
                "the file holds the picture's own bytes, beside the page",
                () -> assertThat(pageDirectory.resolve(THE_PICTURE_DIRECTORY).resolve(THE_DIAGRAMS_FILE))
                        .hasBinaryContent(A_DIAGRAM));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("An entry whose only pictures are furniture gains nothing, and no picture directory is made")
    void leavesFurnitureOut(@TempDir Path pageDirectory) throws IOException {
        EntryPictures pictures = EntryPictures.among(
                List.of(FIRST, SECOND),
                source(Map.of(
                        FIRST.occurrence(), List.of(png(A_SHARED_LOGO)),
                        SECOND.occurrence(), List.of(png(A_SHARED_LOGO), furnitureLayer(IN_THE_HEADER)))),
                DeliverableProgress.NONE);
        StringBuilder page = new StringBuilder();

        pictures.appendUnder(page, SECOND, 2, pageDirectory, THE_PICTURE_DIRECTORY);

        claim(
                "neither the shared logo nor the header picture is shown, nothing is said about them, and the"
                        + " picture directory is not created for an entry with nothing to show",
                () -> {
                    assertThat(page.toString()).isEmpty();
                    assertThat(pageDirectory.resolve(THE_PICTURE_DIRECTORY)).doesNotExist();
                });
    }

    @Test
    @Story("A picture's alt text is the converter's caption or nothing")
    @DisplayName("A caption becomes alt text folded onto one line and escaped by the membership entry's rule")
    void escapesTheCaptionAsAltText(@TempDir Path pageDirectory) throws IOException {
        EntryPictures pictures = EntryPictures.among(
                List.of(FIRST),
                source(Map.of(FIRST.occurrence(), List.of(new ListedPicture("image/png", A_DIAGRAM, false, A_HOSTILE_CAPTION)))),
                DeliverableProgress.NONE);
        StringBuilder page = new StringBuilder();

        pictures.appendUnder(page, FIRST, 1, pageDirectory, THE_PICTURE_DIRECTORY);

        claim(
                "the caption's blank line is folded away and every character that could form a link, an image, a"
                        + " tag, an entity or a code span is written behind a backslash, the backslash first",
                () -> assertThat(page.toString())
                        .isEqualTo(UNDER_A_ONE_DIGIT_ENTRY + "![" + THAT_CAPTION_AS_ALT_TEXT + "](" + THE_PICTURE_DIRECTORY
                                + "/" + THE_DIAGRAMS_FILE + ")\n\n"));
    }

    @Test
    @Story("A document shows at most ten pictures, and says how many more it has")
    @DisplayName("Twelve pictures show the first ten and say two more are not shown; eleven say one, in the singular")
    void showsTenAndCountsTheRest(@TempDir Path pageDirectory) throws IOException {
        String twelve = picturesUnder(pageDirectory.resolve("twelve"), 12);
        String eleven = picturesUnder(pageDirectory.resolve("eleven"), 11);

        claim(
                "of twelve pictures the first " + Deliverable.PICTURES_PER_DOCUMENT + " are shown and the line after"
                        + " them says the other two are not, in the plural",
                () -> {
                    assertThat(twelve.lines().filter(line -> line.contains("![")).count())
                            .isEqualTo(Deliverable.PICTURES_PER_DOCUMENT);
                    assertThat(twelve)
                            .endsWith(UNDER_A_ONE_DIGIT_ENTRY + "*2 more pictures from this document are not shown.*\n\n");
                });
        claim(
                "of eleven, the line says one more picture is not shown, in the singular",
                () -> assertThat(eleven)
                        .endsWith(UNDER_A_ONE_DIGIT_ENTRY + "*1 more picture from this document is not shown.*\n\n"));
    }

    @Test
    @Story("A picture's file is named from its own bytes")
    @DisplayName("A JPEG is written as .jpg, and a picture of a kind with no extension is counted rather than written")
    void writesAJpegAndCountsAnUnwrittenKind(@TempDir Path pageDirectory) throws IOException {
        byte[] jpeg = bytes("a jpeg");
        EntryPictures pictures = EntryPictures.among(
                List.of(FIRST),
                source(Map.of(
                        FIRST.occurrence(),
                        List.of(new ListedPicture("image/gif", bytes("a gif"), false, ""), new ListedPicture("image/jpeg", jpeg, false, "")))),
                DeliverableProgress.NONE);
        StringBuilder page = new StringBuilder();

        pictures.appendUnder(page, FIRST, 1, pageDirectory, THE_PICTURE_DIRECTORY);

        claim(
                "the JPEG is shown from a .jpg file named from its digest, and the GIF, for which no file is"
                        + " written, is counted in the line saying what is not shown",
                () -> assertThat(page.toString())
                        .isEqualTo(UNDER_A_ONE_DIGIT_ENTRY + "![](" + THE_PICTURE_DIRECTORY + "/" + nameOf(jpeg, ".jpg")
                                + ")\n\n" + UNDER_A_ONE_DIGIT_ENTRY
                                + "*1 more picture from this document is not shown.*\n\n"));
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("Under a two-digit entry a picture is indented four spaces, so it stays inside that entry")
    void indentsByTheWidthOfTheEntrysNumber(@TempDir Path pageDirectory) throws IOException {
        EntryPictures pictures = EntryPictures.among(
                List.of(FIRST), source(Map.of(FIRST.occurrence(), List.of(png(A_DIAGRAM)))), DeliverableProgress.NONE);
        StringBuilder page = new StringBuilder();

        pictures.appendUnder(page, FIRST, 10, pageDirectory, THE_PICTURE_DIRECTORY);

        claim(
                "the picture's line is indented by the width of \"10. \", four spaces, so a renderer keeps it"
                        + " inside the tenth entry rather than ending the list",
                () -> assertThat(page.toString()).startsWith(UNDER_A_TWO_DIGIT_ENTRY + "![]("));
    }

    /** The lines {@code count} distinct PNG pictures of one document add under entry 1. */
    private static String picturesUnder(Path pageDirectory, int count) throws IOException {
        List<ListedPicture> many = IntStream.range(0, count)
                .mapToObj(n -> png(bytes("picture " + n)))
                .toList();
        EntryPictures pictures = EntryPictures.among(
                List.of(FIRST), source(Map.of(FIRST.occurrence(), many)), DeliverableProgress.NONE);
        StringBuilder page = new StringBuilder();
        pictures.appendUnder(page, FIRST, 1, pageDirectory, THE_PICTURE_DIRECTORY);
        return page.toString();
    }

    private static SurvivorPictures source(Map<OccurrenceId, List<ListedPicture>> pictures) {
        return occurrence -> pictures.getOrDefault(occurrence, List.of());
    }

    private static ListedPicture png(byte[] pixels) {
        return new ListedPicture("image/png", pixels, false, "");
    }

    private static ListedPicture furnitureLayer(byte[] pixels) {
        return new ListedPicture("image/png", pixels, true, "");
    }

    private static ListedSurvivor survivor(long occurrence, String path) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                "h" + occurrence,
                new OccurrenceId(1),
                "seeds/Seed One.pdf",
                0,
                0.5);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The file name ADR-149 §3 gives a picture: sixteen hex characters of its digest, then the extension. */
    private static String nameOf(byte[] pixels, String extension) {
        return sha256Hex(pixels).substring(0, 16) + extension;
    }

    private static String sha256Hex(byte[] pixels) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(pixels)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
