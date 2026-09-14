package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The reader a later stage asks for a document's opening chunk through (ADR-108), rather than
 * reaching into the chunk cache's columns itself.
 *
 * <p>Read back through the seam a caller uses, over a real database, with the cache's own writer
 * putting the rows there — mirroring {@code HybridChunkerTest}: a stubbed cache would make the
 * answer an assumption rather than a claim.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Chunking")
@Issue("178")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-029", url = Adr.CHUNKING_STRUCTURE_FIRST_WITH_A_MEASURED_LLM_FALLBACK, type = "adr")
@Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
class LeadingChunksTest {

    /** A content hash standing in for a real one; its 64 hex characters are what the column holds. */
    private static final String CONTENT_HASH = "0".repeat(63) + "1";

    /** Another document's hash, differing from the first, so a lookup cannot match by accident. */
    private static final String ANOTHER_CONTENT_HASH = "0".repeat(63) + "2";

    /** The chunker the rows were cut by, the one value this module's chunker files its rows under. */
    private static final String CHUNKER = HybridChunker.CHUNKER_IDENTITY;

    /** The budgeting rule those boundaries were cut to, as the cache's key spells it. */
    private static final String RULE = ChunkingRule.DEFAULT.identity().value();

    /** The document's opening section, heading-led, which is what makes it usable as an exemplar. */
    private static final String OPENING_TEXT = "Introduction\n\nThis is the opening paragraph.";

    /** Words in {@link #OPENING_TEXT}, counted once here so the claim can name the number. */
    private static final int OPENING_WORDS = 6;

    /** A later section of the same document, so the opening one can be told apart from a middle one. */
    private static final String LATER_TEXT = "Method\n\nThis is the method paragraph, written later.";

    /** Words in {@link #LATER_TEXT}, counted once here so the claim can name the number. */
    private static final int LATER_WORDS = 8;

    /** A second rule, budgeting fewer words, standing in for one tuned to another document kind. */
    private static final ChunkingRule NARROWER_RULE = new ChunkingRule(64);

    /** The narrower rule's key, which cuts the same document into its own, shorter boundaries. */
    private static final String NARROWER = NARROWER_RULE.identity().value();

    /** What the narrower rule's opening chunk holds — the same heading, cut short of the paragraph. */
    private static final String NARROWLY_CUT_OPENING = "Introduction";

    /** Words in {@link #NARROWLY_CUT_OPENING}: one, and a count no other chunk here shares. */
    private static final int NARROWLY_CUT_OPENING_WORDS = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A document's opening chunk")
    @DisplayName("A chunked document answers with its opening chunk and how many words are in it")
    void answersWithTheOpeningChunkAndItsWordCount() {
        ChunkCache cache = new ChunkCache(jdbcTemplate);
        cache.put(CONTENT_HASH, CHUNKER, RULE, List.of(new Chunk(0, OPENING_TEXT, OPENING_WORDS)));
        LeadingChunks leadingChunks = new LeadingChunks(jdbcTemplate);

        claim(
                "the opening chunk comes back whole — the heading-led text an exemplar is built from,"
                        + " and the "
                        + OPENING_WORDS
                        + " words it was stored as, so a caller budgeting in words never has to"
                        + " re-measure the text it is about to send",
                () -> assertThat(leadingChunks.forContentHash(CONTENT_HASH))
                        .contains(new Chunk(0, OPENING_TEXT, OPENING_WORDS)));
    }

    @Test
    @Story("A document nothing was cut from")
    @DisplayName("A document that was never chunked answers with nothing, rather than failing")
    void answersWithNothingForADocumentThatWasNeverChunked() {
        ChunkCache cache = new ChunkCache(jdbcTemplate);
        cache.put(CONTENT_HASH, CHUNKER, RULE, List.of(new Chunk(0, OPENING_TEXT, OPENING_WORDS)));
        LeadingChunks leadingChunks = new LeadingChunks(jdbcTemplate);

        claim(
                "asking about content nothing was cut from answers with nothing, and does not throw:"
                        + " a caller asking after a document that was never chunked is putting a"
                        + " question, not committing a fault",
                () -> assertThat(leadingChunks.forContentHash(ANOTHER_CONTENT_HASH)).isEmpty());
    }

    @Test
    @Story("A document's opening chunk")
    @DisplayName("The chunk read back is the one the document opens with, whatever order the chunks were stored in")
    void answersWithTheFirstChunkRatherThanWhicheverWasStoredFirst() {
        ChunkCache cache = new ChunkCache(jdbcTemplate);
        cache.put(
                CONTENT_HASH,
                CHUNKER,
                RULE,
                List.of(new Chunk(1, LATER_TEXT, LATER_WORDS), new Chunk(0, OPENING_TEXT, OPENING_WORDS)));
        LeadingChunks leadingChunks = new LeadingChunks(jdbcTemplate);

        claim(
                "the document's own first chunk comes back even though the second one was stored"
                        + " first — an exemplar has to open where the document opens, and a chunk"
                        + " from the middle would arrive mid-paragraph instead of under its heading",
                () -> assertThat(leadingChunks.forContentHash(CONTENT_HASH))
                        .contains(new Chunk(0, OPENING_TEXT, OPENING_WORDS)));
    }

    @Test
    @Story("A document cut twice")
    @DisplayName("A document cut under two budgets answers with one budget's opening chunk, the same one every time")
    void answersWithOneRulesChunkRatherThanAMixtureOfBoth() {
        ChunkCache cache = new ChunkCache(jdbcTemplate);
        cache.put(
                CONTENT_HASH,
                CHUNKER,
                NARROWER,
                List.of(new Chunk(0, NARROWLY_CUT_OPENING, NARROWLY_CUT_OPENING_WORDS)));
        cache.put(CONTENT_HASH, CHUNKER, RULE, List.of(new Chunk(0, OPENING_TEXT, OPENING_WORDS)));
        LeadingChunks leadingChunks = new LeadingChunks(jdbcTemplate);

        claim(
                "one rule's opening chunk comes back whole — its "
                        + OPENING_WORDS
                        + " words belong to the text beside them, not to the "
                        + NARROWLY_CUT_OPENING_WORDS
                        + "-word chunk the other rule cut, and a caller counting a budget in words"
                        + " is counting what it is actually about to send. It is the same rule's"
                        + " chunk on every reading, though that rule's rows were written second:"
                        + " one corpus asked twice gives one answer, or a deliverable built from it"
                        + " would change while the archive stood still",
                () -> assertThat(leadingChunks.forContentHash(CONTENT_HASH))
                        .contains(new Chunk(0, OPENING_TEXT, OPENING_WORDS)));
    }
}
