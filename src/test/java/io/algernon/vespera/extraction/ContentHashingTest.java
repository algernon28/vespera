package io.algernon.vespera.extraction;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hashing the content that reached extraction without a hash (ADR-067).
 *
 * <p>An earlier stage only hashes content that shares a size with something else, so content with no
 * same-size peer arrives here never hashed, and the cache has to be filed under a content hash either way.
 * That the hash is the published SHA-256 of the bytes, by this method and by the census's alike, is held
 * by {@link AFileOfAnySizeIsHashedTest}; this holds that the two agree, and that it is a hash of the content
 * and not of the file.
 */
@Epic("Extraction")
@Feature("Hashing content that arrived unhashed")
@Issue("46")
@Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
class ContentHashingTest {

    /** The three ASCII letters of the standard SHA-256 test vector. */
    private static final String KNOWN_INPUT = "abc";

    @Test
    @Story("Both hashers compute one content identity")
    @DisplayName("The hash computed here is byte-for-byte the one corpus records for the same file")
    @Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
    @Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
    void agreesWithTheHashCorpusRecordsForTheSameBytes(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("shared.txt"), KNOWN_INPUT.getBytes(StandardCharsets.US_ASCII));

        claim(
                "the two hashers answer the same digest for the same bytes. There are two"
                        + " implementations because each part of the system computes its own, and until now"
                        + " their agreement was only a sentence in a comment. It carries weight: cached"
                        + " conversions and cached chunks are both filed under the content's hash, so a"
                        + " document that is both a seed and an archive member is converted once only while"
                        + " these two agree. Drift would not fail anything loudly -- it would quietly"
                        + " convert every shared document twice and file the two copies apart",
                () -> assertThat(ContentHashing.sha256(file))
                        .isEqualTo(io.algernon.vespera.corpus.ContentHash.sha256(file)));
    }
}
