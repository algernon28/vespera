package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Which shingles count as boilerplate under one floor (ADR-080), and — the case that matters most —
 * which do not.
 *
 * <p>The filter this class resolves is the one thing in stage 4 that inverts silently when misread:
 * treating a hash the frequency table has no row for as unknown, and defaulting either way, strips
 * exactly the shingles that carry the most signal instead of the least.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Boilerplate")
@Issue("75")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
class BoilerplateShinglesTest {

    /** The corpus stage 3 measured: 10 documents carried at least one shingle. */
    private static final int SHINGLED_DOCUMENTS = 10;

    /** Half of them, so a hash in 5 or more documents is boilerplate and one in 4 is not. */
    private static final double FLOOR = 0.5;

    private static final long HASH_AT_THE_FLOOR = 111L;

    private static final long HASH_BELOW_THE_FLOOR = 222L;

    private static final long HASH_WITH_NO_ROW = 333L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The floor is a proportion of the shingled corpus")
    @DisplayName("A hash reaching the floor is boilerplate and one below it is not")
    void resolvesBoilerplateAgainstTheMeasuredCorpusSize() {
        RunId stage3RunId = fixture();
        documentFrequency(stage3RunId, HASH_AT_THE_FLOOR, SHINGLED_DOCUMENTS / 2);
        documentFrequency(stage3RunId, HASH_BELOW_THE_FLOOR, SHINGLED_DOCUMENTS / 2 - 1);

        Set<Long> boilerplate = new BoilerplateShingles(jdbcTemplate).resolve(stage3RunId, FLOOR);

        claim(
                "a hash in 5 of the 10 shingled documents reaches a floor of 0.5 and is boilerplate",
                () -> assertThat(boilerplate).contains(HASH_AT_THE_FLOOR));
        claim(
                "a hash in 4 of them does not reach it, and a document keeps it as part of what makes it"
                        + " distinctive",
                () -> assertThat(boilerplate).doesNotContain(HASH_BELOW_THE_FLOOR));
    }

    @Test
    @Story("An absent frequency row is the rarest shingle, not an unknown one")
    @DisplayName("A hash with no shingle_document_frequency row is never boilerplate")
    void treatsAnAbsentRowAsTheRarestShingleRatherThanAnUnknownOne() {
        RunId stage3RunId = fixture();
        documentFrequency(stage3RunId, HASH_AT_THE_FLOOR, SHINGLED_DOCUMENTS / 2);

        Set<Long> boilerplate = new BoilerplateShingles(jdbcTemplate).resolve(stage3RunId, FLOOR);

        claim(
                "stage 3 writes a row only for a hash seen in two or more documents (ADR-074), so a hash"
                        + " with no row at all appeared in exactly one -- the rarest thing in the corpus and"
                        + " the opposite of boilerplate. Reading absence as unknown and stripping it would"
                        + " remove precisely the shingles that identify a document",
                () -> assertThat(boilerplate).doesNotContain(HASH_WITH_NO_ROW));
    }

    @Test
    @Story("A corpus nothing was measured over has no boilerplate")
    @DisplayName("With no shingle_corpus_size row, nothing is boilerplate rather than everything")
    void treatsAnUnmeasuredCorpusAsHavingNoBoilerplate() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId stage3RunId = ledger.startRun("content-census", "unmeasured", "{}", walkId, List.of());
        documentFrequency(stage3RunId, HASH_AT_THE_FLOOR, SHINGLED_DOCUMENTS);

        Set<Long> boilerplate = new BoilerplateShingles(jdbcTemplate).resolve(stage3RunId, FLOOR);

        claim(
                "with no denominator recorded there is no proportion to compare against, and answering"
                        + " \"nothing\" leaves every shingle in play -- the direction that removes no"
                        + " document, unlike answering \"everything\", which would leave every document"
                        + " empty and unsignable",
                () -> assertThat(boilerplate).isEmpty());
    }

    /** One walk and one stage-3 run, with the corpus size stage 3 would have measured. */
    private RunId fixture() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId stage3RunId = ledger.startRun("content-census", "abc123", "{}", walkId, List.of());
        jdbcTemplate.update(
                "INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count)"
                        + " VALUES (?, ?, ?)",
                stage3RunId.value(),
                ShingleParameters.DEFAULT.identity(),
                SHINGLED_DOCUMENTS);
        return stage3RunId;
    }

    private void documentFrequency(RunId stage3RunId, long hash, int documentCount) {
        jdbcTemplate.update(
                "INSERT INTO shingle_document_frequency"
                        + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                        + " VALUES (?, ?, ?, ?, ?)",
                stage3RunId.value(),
                ShingleParameters.DEFAULT.identity(),
                hash,
                documentCount,
                documentCount);
    }
}
