package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What keeps the next change to {@code similarity} from leaving ADR-219's clause behind (ADR-219 section 2,
 * #473).
 *
 * <p>ADR-219 decides that stage 3's grouping names its index, {@code INDEXED BY shingle_by_run_id}, and
 * that the clause is not shipped on its own: a change to {@code similarity} moves the run ids of stages 2
 * to 6b and has stage 2 do its work again (ADR-058), so the clause rides with the next change that moves
 * that module for a reason of its own. Until then stage 3 is slow wherever it meets {@code
 * shingle_by_hash}.
 *
 * <p>A run id moves with the last commit touching {@code src/main/java/io/algernon/vespera/similarity}, and
 * a test cannot ask git which commit that is on every machine. So this holds the next best thing: the
 * module's sources are, byte for byte but for line endings, the ones ADR-219 was written against, or {@code
 * DocumentFrequency} carries the clause. Any edit to the module without the clause fails it, and the
 * failure says what is owed.
 *
 * <p><b>This class is deleted by the change that ships the clause</b>, which adds the claim that the
 * grouping sent carries it to {@code DocumentFrequencyIsCountedInTheDatabaseTest} (ADR-219, Tests). It
 * passes with the clause in place whatever else changed, so leaving it behind fails nothing.
 */
@Epic("Redundancy")
@Feature("Shingling")
@Issue("473")
@Link(name = "ADR-219", url = Adr.STAGE_3S_GROUPING_IS_PINNED_TO_THE_INDEX_ON_THE_RUN, type = "adr")
class TheGroupingsPinShipsWithTheNextChangeToSimilarityTest {

    /** The module whose every change moves stage 2's run id, and stage 3's and stage 4's with it. */
    private static final Path SIMILARITY = Path.of("src", "main", "java", "io", "algernon", "vespera", "similarity");

    /** The clause ADR-219 section 1 decides for stage 3's grouping. */
    private static final String THE_PIN = "INDEXED BY shingle_by_run_id";

    /**
     * SHA-256 over the module's sources as ADR-219 found them: each file's path beneath the module, then
     * its text with carriage returns taken out, in the order of the paths.
     */
    private static final String AS_ADR_219_FOUND_IT = "9ab91436604ce15c948348fb6812f5d92c150d61412e3eaee141fd8f2f9e0d92";

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("The code that counts repeated word sequences is unchanged since the decision to tell that count which index to read through, or the count already says so")
    void similarityIsAsTheRecordFoundItOrTheGroupingCarriesItsPin() throws IOException, NoSuchAlgorithmException {
        boolean pinned = Files.readString(SIMILARITY.resolve("DocumentFrequency.java")).contains(THE_PIN);
        String sources = digestOfTheSources();

        claim(
                "either the count of repeated word sequences names the index it reads through, or nothing in"
                        + " the code beside it has changed since that was decided",
                () -> {
                    if (!pinned) {
                        assertThat(sources)
                                .as("the similarity module has changed and DocumentFrequency's grouping does"
                                        + " not carry \"%s\". A change to this module already moves the run"
                                        + " ids of stages 2 to 6b, so it owes ADR-219's clause: add it after"
                                        + " \"FROM shingle\" in the grouping, make the test-side edits ADR-219"
                                        + " lists under Tests, and delete this class",
                                        THE_PIN)
                                .isEqualTo(AS_ADR_219_FOUND_IT);
                    }
                });
    }

    private static String digestOfTheSources() throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // Ordered as text, not as paths: a path's order follows the file system's case rule, which differs
        // between the machines this runs on.
        List<String> sources;
        try (Stream<Path> found = Files.walk(SIMILARITY)) {
            sources = found.filter(Files::isRegularFile)
                    .map(source -> SIMILARITY.relativize(source).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        for (String source : sources) {
            digest.update(source.getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readString(SIMILARITY.resolve(source)).replace("\r", "").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
