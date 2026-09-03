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
 * same-size peer arrives here never hashed — and the cache has to be filed under a content hash
 * either way. What matters about the hash computed here is that it is the standard one: the values
 * claimed below are the published SHA-256 digests of their inputs, not values read back out of this
 * code, so a change of algorithm, encoding or hex formatting fails here rather than silently
 * re-filing every cached conversion under new keys.
 */
@Epic("Extraction")
@Feature("Hashing content that arrived unhashed")
@Issue("46")
@Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
class ContentHashingTest {

    /** The published SHA-256 of no bytes at all, as lowercase hex. */
    private static final String DIGEST_OF_NOTHING = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** The three ASCII letters of the standard SHA-256 test vector. */
    private static final String KNOWN_INPUT = "abc";

    /** The published SHA-256 of those three letters, as lowercase hex. */
    private static final String DIGEST_OF_KNOWN_INPUT =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** How many characters a SHA-256 digest occupies in hex: 32 bytes, two characters each. */
    private static final int HEX_CHARACTERS_IN_A_DIGEST = 64;

    @Test
    @Story("The hash is the standard one")
    @DisplayName("A file's hash is the published digest of its bytes, in lowercase hexadecimal")
    void matchesThePublishedDigestOfAKnownInput(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("known.txt"), KNOWN_INPUT.getBytes(StandardCharsets.US_ASCII));

        claim(
                "the hash of the three-letter standard test input is the digest published for it, so"
                        + " this is the same content identity every other tool computes and not a"
                        + " local variant of one",
                () -> assertThat(ContentHashing.sha256(file)).isEqualTo(DIGEST_OF_KNOWN_INPUT));
        claim(
                "and it is written as " + HEX_CHARACTERS_IN_A_DIGEST + " hexadecimal characters — two"
                        + " per byte of a 32-byte digest — since the stored key is that text",
                () -> assertThat(ContentHashing.sha256(file)).hasSize(HEX_CHARACTERS_IN_A_DIGEST));
    }

    @Test
    @Story("The hash is the standard one")
    @DisplayName("An empty file hashes to the published digest of no bytes, rather than to nothing")
    void hashesAnEmptyFileToThePublishedDigestOfNoBytes(@TempDir Path dir) throws IOException {
        Path empty = Files.createFile(dir.resolve("empty.txt"));

        claim(
                "a file with no content still hashes, and to the digest published for no bytes: content"
                        + " with nothing in it is still content to file a conversion under",
                () -> assertThat(ContentHashing.sha256(empty)).isEqualTo(DIGEST_OF_NOTHING));
    }

    @Test
    @Story("The hash is stable, and tells content apart")
    @DisplayName("The same file hashes the same twice, and different content hashes differently")
    void isStableAcrossCallsAndSensitiveToContent(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "the content of one document");
        Path copy = Files.writeString(dir.resolve("copy-under-another-name.txt"), "the content of one document");
        Path different = Files.writeString(dir.resolve("b.txt"), "the content of another document");

        claim(
                "hashing the same file twice gives the same answer, without which a cached conversion"
                        + " would be found only by the request that stored it",
                () -> assertThat(ContentHashing.sha256(file)).isEqualTo(ContentHashing.sha256(file)));
        claim(
                "the same bytes under a different name hash the same, since it is the content being"
                        + " identified and not the file",
                () -> assertThat(ContentHashing.sha256(copy)).isEqualTo(ContentHashing.sha256(file)));
        claim(
                "different content hashes differently, without which one document's conversion would"
                        + " be answered for another's",
                () -> assertThat(ContentHashing.sha256(different)).isNotEqualTo(ContentHashing.sha256(file)));
    }
}
