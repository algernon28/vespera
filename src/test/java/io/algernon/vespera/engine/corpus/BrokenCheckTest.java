package io.algernon.vespera.engine.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link BrokenCheck} against real files, built rather than described: a corrupt container is the
 * point, so the fixture is the corruption itself (ADR-068).
 *
 * <p>No database, no Spring context — {@link BrokenCheck} is a plain method over a filesystem path,
 * so the seam is the method itself, matching {@code WalkTest}'s direct-against-a-real-filesystem
 * style rather than a Batch step's.
 */
@Epic("Byte-level reduction")
@Feature("Broken check")
@Issue("36")
@Link(name = "ADR-068", url = Adr.BROKEN_IS_A_CROSS_FORMAT_FLOOR_PLUS_PER_FORMAT_CHECKS, type = "adr")
class BrokenCheckTest {

    @Test
    @Story("The .docx structural check")
    @DisplayName("A .docx truncated mid-container is broken")
    void truncatedDocxIsBroken(@TempDir Path dir) throws IOException {
        Path wholeDocx = validDocx(dir.resolve("whole.docx"));
        byte[] wholeBytes = Files.readAllBytes(wholeDocx);
        Path truncated = dir.resolve("truncated.docx");
        Files.write(truncated, java.util.Arrays.copyOf(wholeBytes, wholeBytes.length / 2));

        claim(
                "a zip whose central directory was cut off is unreadable as a container, so it is broken",
                () -> assertThat(BrokenCheck.check(truncated).broken()).isTrue());
        claim(
                "the reason names the zip container, not a guess at the document inside it",
                () -> assertThat(BrokenCheck.check(truncated).reason()).contains("zip"));
    }

    private static Path validDocx(Path path) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return path;
    }
}
