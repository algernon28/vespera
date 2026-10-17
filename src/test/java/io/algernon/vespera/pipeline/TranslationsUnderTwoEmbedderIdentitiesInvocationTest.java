package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The translation step where the vectors under the run's model carry no single embedder identity (ADR-271 §5):
 * no mean vector can be read on one scale, so the step compares nothing, removes nothing, writes no page,
 * records no completion, and is shown as stopped at a gate.
 *
 * <p>A first invocation over {@code en/guide.txt} and {@code zh/guide.txt}, preferring English, removes the
 * Chinese copy. The test then stores every vector once more under a second identity of the same model, differing
 * in the dimension alone, as {@code RelevanceFloorInvocationTest} does, and takes away the step's completion
 * record and its page, so that the next invocation of the same scoring run does the step's work again.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class TranslationsUnderTwoEmbedderIdentitiesInvocationTest {

    private static final String PAGE = "translations.html";

    private static final String STEPS_PAGE = "progress-steps.html";

    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);

    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("With no single embedder identity, the step stops at its gate")
    @DisplayName("With two embedder identities under the run's model, the step removes nothing, writes no page, records no completion and stops at a gate")
    void withTwoIdentitiesTheStepStopsAtAGate(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.createDirectories(root.resolve("en"));
        Files.createDirectories(root.resolve("zh"));
        Files.writeString(root.resolve("en/guide.txt"), "a guide written in English");
        Files.writeString(root.resolve("zh/guide.txt"), "一份用中文写的指南");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor("1.0", "set by this test, so stage 4's gate is open")
                .embeddingModel("qwen3-embedding:0.6b", "set by this test, so the embedding-model gate is open")
                .preferredLanguages("en", "set by this test, so the Chinese copy is removed")
                .build());
        cli.run("run", root.toString());
        String scoring = latestScoringRun(root);
        long removedUnderOneIdentity = translationsUnder(scoring);
        String identity = jdbcTemplate.queryForObject("SELECT DISTINCT embedder_identity FROM vector", String.class);
        String aSecondIdentity = identity.replace(";dimension=", ";dimension=1");
        jdbcTemplate.update(
                "INSERT INTO vector SELECT content_hash, chunker_identity, chunking_rule_identity, ordinal, ?,"
                        + " embedding FROM vector",
                aSecondIdentity);
        jdbcTemplate.update("DELETE FROM finished_step WHERE run_id = ? AND step = 'translation'", scoring);
        Files.delete(workingDirectory.resolve(PAGE));
        try {
            cli.run("run", root.toString());
        } finally {
            jdbcTemplate.update("DELETE FROM vector WHERE embedder_identity = ?", aSecondIdentity);
        }

        claim(
                "under one identity the step removed the Chinese copy, so there was a removal to take back",
                () -> assertThat(removedUnderOneIdentity).isEqualTo(1L));
        claim(
                "the second invocation is of the same scoring run, so the step did its work again under it",
                () -> assertThat(latestScoringRun(root)).isEqualTo(scoring));
        claim(
                "with two identities it compared nothing and removed nothing: no copy stands removed as a"
                        + " translation under the run",
                () -> assertThat(translationsUnder(scoring)).isZero());
        claim(
                "it wrote no page",
                () -> assertThat(workingDirectory.resolve(PAGE)).doesNotExist());
        claim(
                "it recorded no completion, so the next invocation asks again",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = 'translation'",
                                Long.class,
                                scoring))
                        .isZero());
        claim(
                "and the progress page shows the step as stopped at a gate",
                () -> assertThat(stepRowsOf(Files.readString(workingDirectory.resolve(STEPS_PAGE))))
                        .anySatisfy(row -> assertThat(row).startsWith("translation", "stopped at a gate")));
    }

    private String latestScoringRun(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = 'embedding-scoring' AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    private long translationsUnder(String run) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id = ? AND kind = 'TRANSLATION_OF'", Long.class, run);
    }

    /** The cells of each body row of the steps page, tags taken out. */
    private static List<List<String>> stepRowsOf(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(html);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = A_CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replaceAll("<[^>]*>", "").trim());
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }
}
