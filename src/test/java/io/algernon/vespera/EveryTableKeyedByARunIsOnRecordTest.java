package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * The tables that grow by a copy for each run, and that nothing shipped removes another run's rows from
 * them or makes the database file smaller (ADR-229, #481).
 *
 * <p>ADR-229 keeps every run's rows and states the growth table by table. So the tables are held here: a
 * table that gains a reference to {@code run}, or loses one, fails the first test until a record states
 * what it keeps. The second holds what a shipped delete of such a table may be: of the rows of one run,
 * the run the statement is handed, which is a step discarding its own unfinished work (ADR-116), with the
 * one exception named. It holds too which of the tables no shipped statement deletes from at all, {@code
 * shingle} among them. The third holds that no shipped text asks SQLite to give pages back.
 *
 * <p>What a delete is handed is not seen here: a statement is a text, and the run bound to it is its
 * caller's. Owners are read as {@code EachTableIsNamedOnlyByItsOwnerTest} reads them, and the statements
 * from the compiled classes.
 */
@Epic("Architecture")
@Feature("What the database keeps")
@Issue("481")
@Link(name = "ADR-229", url = Adr.EVERY_RUNS_ROWS_ARE_KEPT_AND_THE_FILE_IS_NOT_MADE_SMALLER, type = "adr")
@Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
class EveryTableKeyedByARunIsOnRecordTest {

    private static final Pattern OWNED_TABLE = Pattern.compile(
            "^-- owner: ([a-z]+)\\R^CREATE TABLE IF NOT EXISTS ([a-z_]+) \\((.*?)^\\);", Pattern.MULTILINE | Pattern.DOTALL);

    /** How every table keyed by a run declares it. */
    private static final Pattern KEYED_BY_A_RUN =
            Pattern.compile("^\\s+run_id TEXT NOT NULL REFERENCES run \\(id\\)", Pattern.MULTILINE);

    private static final Pattern A_DELETE = Pattern.compile("^\\s*DELETE FROM ([a-z_]+)\\b(.*)", Pattern.DOTALL);

    /** The twenty-six tables ADR-229 states the growth of, each with the module that owns it. */
    private static final Map<String, String> ON_RECORD = new TreeMap<>(Map.ofEntries(
            Map.entry("run_upstream", "ledger"),
            Map.entry("finished_step", "ledger"),
            Map.entry("verdict", "ledger"),
            Map.entry("content_hash", "corpus"),
            Map.entry("superseded_by", "corpus"),
            Map.entry("detected_format", "corpus"),
            Map.entry("extraction_metric", "extraction"),
            Map.entry("extraction_cache_key", "extraction"),
            Map.entry("confidence_distribution", "extraction"),
            Map.entry("extraction_fault", "extraction"),
            Map.entry("shingle", "similarity"),
            Map.entry("shingle_document_frequency", "similarity"),
            Map.entry("shingle_corpus_size", "similarity"),
            Map.entry("minhash_signature", "similarity"),
            Map.entry("signature_band", "similarity"),
            Map.entry("redundant_with", "similarity"),
            Map.entry("unusable_seed", "embedding"),
            Map.entry("seed_corpus_comparison", "embedding"),
            Map.entry("relevance_score", "embedding"),
            Map.entry("document_cluster", "embedding"),
            Map.entry("relevance_label", "embedding"),
            Map.entry("relevance_label_provenance", "embedding"),
            Map.entry("cluster", "synthesis"),
            Map.entry("synthesis_doc", "synthesis"),
            Map.entry("call_exemplar", "synthesis"),
            Map.entry("cluster_fault", "synthesis")));

    /**
     * The one such table a shipped statement deletes from by something other than a run: a recorded answer's
     * provenance, by path and seed set, when a person's answer replaces a model's (ADR-197).
     */
    private static final String DELETED_BY_PATH_AND_SEED_SET = "relevance_label_provenance";

    /** The tables keyed by a run that no shipped statement deletes a row of. */
    private static final Set<String> NOTHING_DELETES_FROM =
            Set.of("shingle", "run_upstream", "finished_step", "relevance_label", "synthesis_doc");

    @Test
    @Story("The tables that grow with each piece of work are listed")
    @DisplayName("The tables that keep rows by piece of work are the twenty-six on record, each owned by the part of the code on record")
    void theTablesKeyedByARunAreTheOnesOnRecord() throws IOException {
        Map<String, String> keyedByARun = new TreeMap<>();
        Matcher table = OWNED_TABLE.matcher(schema());
        while (table.find()) {
            if (KEYED_BY_A_RUN.matcher(table.group(3)).find()) {
                keyedByARun.put(table.group(2), table.group(1));
            }
        }

        claim(
                "the tables whose rows name the piece of work they were written under are the twenty-six the"
                        + " record lists, with their owners: a table added to them, or taken from them, grows"
                        + " or stops growing with every new piece of work, and the record must say so",
                () -> assertThat(keyedByARun).isEqualTo(ON_RECORD));
    }

    @Test
    @Story("Nothing removes the rows of work done earlier")
    @DisplayName("Every shipped delete from such a table is of the rows of one piece of work it is handed, but one by path and seed folder, and five of the tables are never deleted from")
    void everyShippedDeleteIsOfOneRunsRows() throws IOException, ClassNotFoundException {
        Map<String, String> deletesNotOfOneRun = new TreeMap<>();
        Set<String> deletedFrom = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.stringsByClass().entrySet()) {
            for (String text : shipped.getValue()) {
                Matcher delete = A_DELETE.matcher(text);
                if (!delete.matches() || !ON_RECORD.containsKey(delete.group(1))) {
                    continue;
                }
                deletedFrom.add(delete.group(1));
                if (!delete.group(2).trim().startsWith("WHERE run_id = ?")) {
                    deletesNotOfOneRun.put(delete.group(1), shipped.getKey());
                }
            }
        }
        Set<String> neverDeletedFrom = new TreeSet<>(ON_RECORD.keySet());
        neverDeletedFrom.removeAll(deletedFrom);

        claim(
                "the shipped code deletes from some of these tables, so the claims below read statements that"
                        + " exist",
                () -> assertThat(deletedFrom).isNotEmpty());
        claim(
                "each of those deletes begins by naming one piece of work, handed to it, except the one that"
                        + " takes back who gave an answer, which is by the document's path and the seed folder",
                () -> assertThat(deletesNotOfOneRun.keySet()).containsExactly(DELETED_BY_PATH_AND_SEED_SET));
        claim(
                "and no shipped statement deletes from the five tables named here at all, the word sequences"
                        + " of every extraction among them",
                () -> assertThat(neverDeletedFrom).containsExactlyInAnyOrderElementsOf(NOTHING_DELETES_FROM));
    }

    @Test
    @Story("Nothing removes the rows of work done earlier")
    @DisplayName("No shipped statement or setting asks the database to give back the room of removed rows")
    void nothingShippedMakesTheDatabaseFileSmaller() throws IOException, ClassNotFoundException {
        Pattern givesPagesBack = Pattern.compile("\\bvacuum\\b|auto_vacuum|incremental_vacuum", Pattern.CASE_INSENSITIVE);
        Map<String, String> asking = new TreeMap<>();
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.stringsByClass().entrySet()) {
            for (String text : shipped.getValue()) {
                if (givesPagesBack.matcher(text).find()) {
                    asking.put(shipped.getKey(), text);
                }
            }
        }

        claim(
                "no text of a shipped class names the statement or the setting by which the database file is"
                        + " made smaller",
                () -> assertThat(asking).isEmpty());
        claim("nor does the schema", () -> assertThat(givesPagesBack.matcher(schema()).find())
                .isFalse());
        claim("nor the shipped configuration, where the database's address is written", () -> assertThat(
                        givesPagesBack.matcher(resource("application.yaml")).find())
                .isFalse());
    }

    private static String schema() throws IOException {
        return resource("schema.sql");
    }

    private static String resource(String name) throws IOException {
        return StreamUtils.copyToString(new ClassPathResource(name).getInputStream(), StandardCharsets.UTF_8);
    }
}
