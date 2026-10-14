package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestcontainersConfiguration;
import io.algernon.vespera.corpus.BrokenCheck;
import io.algernon.vespera.corpus.DetectedFormat;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * What the pinned sidecar answers for bytes it can name no input format for, posted under the neutral
 * {@code document.bin} that {@link DoclingClient} sends for them (ADR-100): what an {@code extraction-failed}
 * reason of {@code policy: File format not allowed: document.bin} means (ADR-255).
 *
 * <p>The reason reads as though the sidecar refused the {@code .bin} name by a setting. It does not. Docling
 * resolves no input format for
 * these bytes ({@code _guess_format} returns none: {@code filetype} matches nothing or only
 * {@code application/zip}, {@code bin} is in no extension table, and the {@code text/plain} fallback
 * reaches Markdown only for a Markdown or text extension), and {@code DocumentConverter._process_document}
 * answers a format outside its allowed list, which defaults to every format, with {@code skipped},
 * category {@code policy}. An image sent under the same name converts, so the name is not what is refused.
 *
 * <p>Every fixture is made here, each in its own folder (ADR-063), and stage 1's own reading of it is
 * checked first, so the format each is posted as is the one a real walk would have recorded.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("The Docling client")
@Link(name = "ADR-255", url = Adr.A_POLICY_REFUSAL_MEANS_THE_CONVERTER_NAMED_NO_FORMAT, type = "adr")
@Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-063", url = Adr.FIXTURES_ARE_GENERATED_IN_TEST, type = "adr")
class DoclingRefusesBytesOfNoKnownKindIT {

    /** What the sidecar's refusal says for the one name every such upload carries. */
    private static final String REFUSAL_WORDS = "File format not allowed: document.bin";

    @Autowired
    private DoclingClient client;

    @Test
    @Story("What the neutral name reaches")
    @DisplayName("Bytes of no known kind, posted as document.bin, come back refused under policy, with no conversion")
    void bytesOfNoKnownKindAreRefused(@TempDir Path dir) throws IOException {
        Path noKnownKind = bytesOfNoKnownKind(dir.resolve("no-known-kind"));
        claim(
                "stage 1 reads the fixture as of no known kind, so stage 2 would post it under the neutral name",
                () -> assertThat(BrokenCheck.check(noKnownKind).format()).isEqualTo(DetectedFormat.UNRECOGNISED));

        DoclingResponse response = client.convert(noKnownKind, DetectedFormat.UNRECOGNISED, null);

        assertRefusedForNoFormat(response);
    }

    @Test
    @Story("What the neutral name reaches")
    @DisplayName("An archive of another kind, posted as document.bin, comes back refused under policy too")
    void anArchiveOfAnotherKindIsRefused(@TempDir Path dir) throws IOException {
        Path archive = anArchiveOfAnotherKind(dir.resolve("another-kind.zip"));
        claim(
                "stage 1 reads the fixture as an archive of another kind",
                () -> assertThat(BrokenCheck.check(archive).format()).isEqualTo(DetectedFormat.ZIP_CONTAINER));

        DoclingResponse response = client.convert(archive, DetectedFormat.ZIP_CONTAINER, null);

        assertRefusedForNoFormat(response);
    }

    @Test
    @Story("What the neutral name reaches")
    @DisplayName("An image posted under the same name converts, so the name itself is not what the sidecar refuses")
    void anImageUnderTheSameNameConverts(@TempDir Path dir) throws IOException {
        Path image = aPng(dir.resolve("picture.png"));
        claim(
                "stage 1 reads the fixture as an image",
                () -> assertThat(BrokenCheck.check(image).format()).isEqualTo(DetectedFormat.IMAGE));

        DoclingResponse response = client.convert(image, DetectedFormat.IMAGE, null);

        claim(
                "the sidecar reads the image's bytes and converts it, though it was posted as document.bin like"
                        + " the two refused above",
                () -> assertThat(response.status())
                        .as("status for a PNG posted as document.bin; errors: %s", response.errors())
                        .isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "and no policy refusal rides with it",
                () -> assertThat(response.errors())
                        .noneMatch(reported -> reported.category() == FailureCategory.POLICY));
    }

    private static void assertRefusedForNoFormat(DoclingResponse response) {
        claim(
                "the sidecar answers skipped: it resolved no input format for the bytes and never ran a pipeline",
                () -> assertThat(response.status())
                        .as("status; errors: %s", response.errors())
                        .isEqualTo(ConversionStatus.SKIPPED));
        claim(
                "with one reason, in the policy category, naming the neutral name: the refusal stage 2 records as"
                        + " extraction-failed",
                () -> assertThat(response.errors())
                        .singleElement()
                        .satisfies(reported -> {
                            assertThat(reported.category()).isEqualTo(FailureCategory.POLICY);
                            assertThat(reported.errorMessage()).isEqualTo(REFUSAL_WORDS);
                        }));
    }

    /**
     * Four kilobytes no signature matches and no text reading accepts: a fixed seed, so the same bytes
     * every run, with control bytes throughout so neither stage 1 nor the sidecar reads them as text.
     */
    private static Path bytesOfNoKnownKind(Path file) throws IOException {
        byte[] bytes = new byte[4096];
        new Random(20261014L).nextBytes(bytes);
        bytes[0] = 0x7A;
        bytes[1] = (byte) 0x9C;
        bytes[2] = 0x00;
        bytes[3] = 0x11;
        return Files.write(file, bytes);
    }

    /** A zip holding two plain entries and none of the members an office format is told apart by. */
    private static Path anArchiveOfAnotherKind(Path file) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String entry : new String[] {"notes/first.dat", "notes/second.dat"}) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(("contents of " + entry + "\n").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    /** A small PNG of two coloured blocks: a picture of a known kind, for the control. */
    private static Path aPng(Path file) throws IOException {
        BufferedImage picture = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D painter = picture.createGraphics();
        painter.setColor(Color.WHITE);
        painter.fillRect(0, 0, 120, 60);
        painter.setColor(new Color(200, 30, 30));
        painter.fillRect(10, 10, 40, 40);
        painter.setColor(new Color(30, 120, 200));
        painter.fillRect(70, 10, 40, 40);
        painter.dispose();
        ImageIO.write(picture, "png", file.toFile());
        return file;
    }
}
