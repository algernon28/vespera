package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a seed document is converted as (ADR-092, ADR-100).
 *
 * <p>A seed arrives outside the cascade: no walk recorded it, so nothing wrote down what it is, and
 * the detection that stage 1 performs for a corpus document happens here instead. The case this
 * class exists for is the one that difference creates — the cross-format floor has a meaning for a
 * walked occurrence that it cannot have for a seed, because no floor ran in front of the seed
 * folder, and an operator may simply have an empty file in it.
 *
 * <p>The seed job is not {@code faultTolerant}, unlike stage 2's — so anything raised here stops the
 * step rather than skipping the document, which is what makes the claim below about one file costing
 * one seed rather than the whole stage worth pinning.
 */
@Epic("Relevance")
@Feature("Seed extraction")
@Issue("124")
@Link(name = "ADR-094", url = Adr.FORMAT_IS_DECIDED_FROM_THE_BYTES, type = "adr")
@Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
class SeedConversionsTest {

    /** Any identity: nothing here turns on which one, since no cache row is read or written. */
    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;test");

    /** Any hash, for the same reason. */
    private static final String CONTENT_HASH = "0".repeat(64);

    @Test
    @Story("A seed is converted as what its bytes say")
    @DisplayName("A seed whose bytes say nothing is still converted, rather than stopping the stage")
    void convertsAnEmptySeedRatherThanRefusingIt(@TempDir Path seeds) throws IOException {
        Path empty = Files.createFile(seeds.resolve("nothing-in-here.txt"));
        ScriptedExtractor docling = new ScriptedExtractor().answering(converted());

        claim(
                "an empty file in the seed folder is an ordinary thing for an operator to have, and no"
                        + " floor ran in front of that folder to stop it -- so it is converted like any other"
                        + " seed, where a refusal would abort a stage that cannot skip the document",
                () -> assertThatCode(() -> SeedConversions.convert(docling, empty, CONTENT_HASH, IDENTITY))
                        .doesNotThrowAnyException());
        claim(
                "and it travels as content nothing recognised, which is what the bytes said: the reading"
                        + " that belongs to stage 1's floor is not one a seed can carry, because the floor is"
                        + " the thing a seed never passed through",
                () -> assertThat(docling.formatsAsked()).containsExactly(DetectedFormat.UNRECOGNISED));
    }

    @Test
    @Story("A seed is converted as what its bytes say")
    @DisplayName("A seed the bytes do recognise is converted as that, whatever it is called on disk")
    void convertsAReadableSeedAsWhatTheBytesSay(@TempDir Path seeds) throws IOException {
        Path prose = Files.writeString(seeds.resolve("exemplar.dat"), "a paragraph of ordinary prose\n");
        ScriptedExtractor docling = new ScriptedExtractor().answering(converted());

        SeedConversions.convert(docling, prose, CONTENT_HASH, IDENTITY);

        claim(
                "the same rule a corpus document is converted under holds for a seed: prose is plain"
                        + " text because its bytes decode as text, and the .dat it happens to be called"
                        + " decides nothing",
                () -> assertThat(docling.formatsAsked()).containsExactly(DetectedFormat.PLAIN_TEXT));
    }

    /** A response Docling answered cleanly with; nothing here judges what came back. */
    private static DoclingResponse converted() {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, "{}");
    }
}
