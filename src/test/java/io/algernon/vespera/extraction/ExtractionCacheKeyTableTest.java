package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The table stage 2 records its extraction cache key in, as {@code schema.sql} declares it (ADR-206
 * section 1): {@code extraction_cache_key}, keyed by the occurrence and the run together, indexed on the
 * run (ADR-173), and refusing any value that could not be a key of {@code extraction_cache}.
 *
 * <p>Nothing here names a class of {@code extraction}'s that reads or writes the table: the table is the
 * decision, and which class stands in front of it is the implementation's. So every statement below is
 * plain SQL over the schema the test database was built from.
 *
 * <p>Every claim fails until the table exists, by a statement SQLite refuses to prepare.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("The extraction cache")
@Issue("349")
@Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
class ExtractionCacheKeyTableTest {

    private static final String TABLE = "extraction_cache_key";

    /** How many characters a SHA-256 has when written in hexadecimal, two for each of its 32 bytes. */
    private static final int HEX_CHARACTERS_OF_A_SHA_256 = 64;

    /** A value of the shape every extraction cache key has: 64 lowercase hexadecimal characters. */
    private static final String A_KEY = "0123456789abcdef".repeat(HEX_CHARACTERS_OF_A_SHA_256 / 16);

    /** One occurrence for the value that is accepted, and one for each value that is refused. */
    private static final int OCCURRENCES = 7;

    /**
     * How SQLite names the refusal of a row by a table's CHECK, which is the text the driver's error
     * carries. Spring maps no SQLite error code to a narrower exception, so the name is what tells this
     * refusal from any other failed statement.
     */
    private static final String A_CHECK_REFUSED_IT = "SQLITE_CONSTRAINT_CHECK";

    private static final String RECORD =
            "INSERT INTO " + TABLE + " (occurrence_id, run_id, content_hash) VALUES (?, ?, ?)";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("The key table holds one row for a document under a run, and is indexed by the run")
    void isKeyedByTheOccurrenceAndTheRunAndIndexedByTheRun() {
        claim(
                "the table is keyed by the document and the run together, so two runs of the stage each"
                        + " record the key they used for the same document, and a second row for one"
                        + " document under one run is refused",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT name FROM pragma_table_info('" + TABLE + "') WHERE pk > 0 ORDER BY pk",
                                String.class))
                        .containsExactly("occurrence_id", "run_id"));
        claim(
                "its columns are the document, the run and the key, and nothing else: what was measured"
                        + " of a document is kept elsewhere",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT name FROM pragma_table_info('" + TABLE + "') ORDER BY cid", String.class))
                        .containsExactly("occurrence_id", "run_id", "content_hash"));
        claim(
                "an index leads with the run alone, so removing a run's rows, and checking that a run"
                        + " may be removed, does not read the whole table",
                () -> assertThat(indexesLeadingWith("run_id")).isNotEmpty());
    }

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("The key table takes a SHA-256 written as 64 lowercase hexadecimal characters and nothing else")
    void refusesAnythingThatIsNotAKey() {
        OccurrencesUnderARun documents = OccurrencesUnderARun.of(jdbcTemplate, OCCURRENCES);
        String run = documents.run().value();

        claim(
                "a value of " + HEX_CHARACTERS_OF_A_SHA_256 + " lowercase hexadecimal characters is"
                        + " recorded: that is how the conversions on record are keyed",
                () -> assertThatCode(() -> jdbcTemplate.update(RECORD, documents.get(0).value(), run, A_KEY))
                        .doesNotThrowAnyException());

        List<String> notKeys = List.of(
                A_KEY.toUpperCase(java.util.Locale.ROOT),
                A_KEY.substring(1),
                A_KEY + "0",
                "g" + A_KEY.substring(1),
                "",
                " ".repeat(HEX_CHARACTERS_OF_A_SHA_256));
        for (int i = 0; i < notKeys.size(); i++) {
            String notAKey = notKeys.get(i);
            long occurrence = documents.get(i + 1).value();
            claim(
                    "the value \"" + notAKey + "\" (" + notAKey.length() + " characters) is refused: no"
                            + " conversion is on record under a value in upper case, of another length"
                            + " or holding anything but 0-9 and a-f, so a row holding one would lead"
                            + " every later reader to nothing, with nothing to say why",
                    () -> assertThatThrownBy(() -> jdbcTemplate.update(RECORD, occurrence, run, notAKey))
                            .isInstanceOf(DataAccessException.class)
                            .hasMessageContaining(A_CHECK_REFUSED_IT));
        }
    }

    /** The indexes on the table whose first column is {@code column}. */
    private List<String> indexesLeadingWith(String column) {
        return jdbcTemplate.queryForList(
                "SELECT indexes.name FROM pragma_index_list('" + TABLE + "') AS indexes"
                        + " WHERE (SELECT name FROM pragma_index_info(indexes.name) WHERE seqno = 0) = ?",
                String.class,
                column);
    }
}
