package io.algernon.vespera.engine.embedding;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A writing's vector, a synthesis doc's or an overview's, is kept in {@code writing_vector} by the SHA-256 of the
 * writing's UTF-8 bytes and the embedder identity, and by no run (ADR-434 §7): a writing embedded once under an
 * identity is read back by its digest, and the same writing under another identity is another row.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Retrieval")
@Feature("Vectors of writings")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
@Link(name = "ADR-085", url = Adr.VECTORS_LIVE_IN_SQLITE, type = "adr")
class AWritingsVectorIsKeptByItsDigestTest {

    /** The SHA-256 of the three bytes {@code abc}, as FIPS 180-2 gives it. */
    private static final String THE_DIGEST_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static final String ONE_IDENTITY = "model=m;digest=one;dtype=F16;dimension=3;instruction=none";

    private static final String ANOTHER_IDENTITY = "model=m;digest=another;dtype=F16;dimension=3;instruction=none";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A writing's vector is read back by its digest")
    @DisplayName("A writing's vector is kept by the digest of its bytes and read back whole, and the same writing under another identity is another row")
    void aWritingsVectorIsReadBackByItsDigestAndIdentity() {
        WritingVectors vectors = new WritingVectors(jdbcTemplate);
        String writing = "abc" + System.nanoTime();
        String digest = WritingEmbedder.digestOf(writing);
        vectors.put(digest, ONE_IDENTITY, new float[] {0.25f, -1.5f, 3.0e-7f});
        vectors.put(digest, ANOTHER_IDENTITY, new float[] {1f, 2f, 3f});

        Optional<float[]> one = vectors.get(digest, ONE_IDENTITY);
        Optional<float[]> another = vectors.get(digest, ANOTHER_IDENTITY);
        Optional<float[]> elsewhere = vectors.get(WritingEmbedder.digestOf(writing + "!"), ONE_IDENTITY);

        claim("the digest of a writing is the lowercase hexadecimal SHA-256 of its UTF-8 bytes",
                () -> assertThat(WritingEmbedder.digestOf("abc")).isEqualTo(THE_DIGEST_OF_ABC));
        claim("the vector kept under one identity is read back component for component, as a float32 does",
                () -> assertThat(one).hasValueSatisfying(vector -> assertThat(vector).containsExactly(0.25f, -1.5f, 3.0e-7f)));
        claim("the same writing under another identity is another row, holding the other vector",
                () -> assertThat(another).hasValueSatisfying(vector -> assertThat(vector).containsExactly(1f, 2f, 3f)));
        claim("another writing under the same identity has none kept",
                () -> assertThat(elsewhere).isEmpty());
        claim("the two rows are the writing's alone, and carry no run",
                () -> {
                    assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM writing_vector WHERE writing_digest = ?", Integer.class, digest))
                            .isEqualTo(2);
                    assertThat(jdbcTemplate.queryForList("SELECT name FROM pragma_table_info('writing_vector')", String.class))
                            .containsExactly("writing_digest", "embedder_identity", "embedding");
                });
    }

    @Test
    @Story("A writing kept is not embedded again")
    @DisplayName("A writing whose vector is kept is answered from the table, asking nothing of the model, and a kept vector stands")
    void aKeptWritingIsNotEmbeddedAgain() {
        WritingVectors vectors = new WritingVectors(jdbcTemplate);
        // No embedder and no runtime are given: a writing kept is answered without either, or this throws.
        WritingEmbedder embedder = new WritingEmbedder(vectors, null, null);
        String writing = "a synthesis doc " + System.nanoTime();
        vectors.put(WritingEmbedder.digestOf(writing), ONE_IDENTITY, new float[] {1f, 2f, 3f});
        vectors.put(WritingEmbedder.digestOf(writing), ONE_IDENTITY, new float[] {9f, 9f, 9f});

        claim("the kept vector is answered, and the second put, of the same key, left the first standing",
                () -> assertThat(embedder.kept(writing, ONE_IDENTITY))
                        .hasValueSatisfying(vector -> assertThat(vector).containsExactly(1f, 2f, 3f)));
        claim("a writing no vector is kept for under that identity is answered as none",
                () -> assertThat(embedder.kept(writing, ANOTHER_IDENTITY)).isEmpty());
    }
}
