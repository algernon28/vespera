package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Code through a whole invocation (ADR-277 §8 to §10): kept apart by stage 1, it is never hashed, converted, signed,
 * scored or clustered, is on neither list stage 2 writes, and is counted in the report's counts and in its runs; and a seed whose name ends with a listed ending is extracted as every seed is, since no seed passes stage 1.
 *
 * <p>The corpus is one document and two identical files whose name ends with a listed ending, the profile opens every
 * gate up to the arrangement, and the job runs once to the arrangement gate. The key is written as YAML through
 * {@link CodeNameEndingsInProfile}.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Code")
@Issue("558")
@Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
class CodeInvocationTest {

    /** The listed ending, and the name the two files kept as code share in two folders. */
    private static final String ENDING = ".java";

    private static final String CODE_NAME = "Widget" + ENDING;

    /** What the two identical files kept as code hold. */
    private static final String CODE_TEXT = "public class Widget {\n    int size() { return 1; }\n}\n";

    /** The corpus's one document, which stage 2 converts. */
    private static final String DOCUMENT = "manuale.txt";

    /** The seed, named with the listed ending. */
    private static final String SEED = "esempio" + ENDING;

    /** The seed recording loop's line: one seed, recorded, so one of one. */
    private static final String SEEDS_RECORDED_LINE = "Stage 5a (seed extraction, seeds recorded): 1 of 1 (100%)";

    /** The code kind as the report's counts and the runs' verdicts name it. */
    private static final String CODE = "code";

    /** The two files kept as code. */
    private static final long TWO_FILES_OF_CODE = 2;

    /** Every table that holds a row for an occurrence a stage after the first read, and the column naming it. */
    private static final List<String> READ_AFTER_STAGE_ONE = List.of(
            "content_hash", "extraction_metric", "shingle", "minhash_signature", "relevance_score", "translation_of",
            "document_cluster");

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The working directory is static, so a profile one method left would be read by the next. */
    @BeforeEach
    @AfterEach
    void putAProfileThatLoadsBack() {
        profileStore.save(ProfileFixture.profile().build());
    }

    @Test
    @Story("Code leaves the survivors in stage 1 and no later stage reads it")
    @DisplayName("Two identical files kept as code are never hashed, converted, signed, scored or clustered, are on neither list stage 2 writes, and are counted in the report and in its runs; the seed named alike is extracted")
    void codeIsReadByStageOneAloneAndCountedWhereTheOperatorLooks(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve(DOCUMENT), "the text of a manual, which is a document");
        Files.writeString(root.resolve(CODE_NAME), CODE_TEXT);
        Files.createDirectory(root.resolve("copia"));
        Files.writeString(root.resolve("copia").resolve(CODE_NAME), CODE_TEXT);
        Files.writeString(seeds.resolve(SEED), "class Esempio { /* a seed whose name ends as code does */ }\n");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        CodeNameEndingsInProfile.write(profileStore, ENDING, "set by this test");
        List<Path> before = TheReportFile.copiesIn(workingDirectory);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        List<Long> code = occurrencesNamed(CODE_NAME);
        long document = occurrencesNamed(DOCUMENT).getFirst();
        claim(
                "each of the two files kept as code carries one verdict, code, written by byte-level reduction",
                () -> {
                    assertThat(code).hasSize(2);
                    for (long occurrence : code) {
                        assertThat(jdbcTemplate.queryForList(
                                        "SELECT r.stage || ' ' || v.kind FROM verdict v JOIN run r ON r.id = v.run_id"
                                                + " WHERE v.occurrence_id = ?",
                                        String.class,
                                        occurrence))
                                .containsExactly("byte-level-reduction CODE");
                    }
                });
        for (String table : READ_AFTER_STAGE_ONE) {
            claim(
                    "no row of " + table + " is about either: no stage after the first read them",
                    () -> assertThat(rowsAbout(table, code)).isZero());
        }
        claim(
                "while the document was read on: stage 2 measured it",
                () -> assertThat(rowsAbout("extraction_metric", List.of(document))).isPositive());
        claim(
                "the survivor list stage 2 writes does not name them, and nor does the review list where one is written",
                () -> {
                    assertThat(page(SurvivorListListener.FILE_NAME)).contains(DOCUMENT).doesNotContain(CODE_NAME);
                    Path reviewList = workingDirectory.resolve(ReviewListListener.FILE_NAME);
                    if (Files.exists(reviewList)) {
                        assertThat(Files.readString(reviewList, StandardCharsets.UTF_8)).doesNotContain(CODE_NAME);
                    }
                });
        JsonNode report = theOneCopyWrittenSince(before);
        claim(
                "the report's copy counts the two code verdicts by the kind the vocabulary names",
                () -> assertThat(report.path("counts").path("verdicts").path(CODE).asLong()).isEqualTo(TWO_FILES_OF_CODE));
        claim(
                "the seed whose name ends with the listed ending was extracted as every seed is, and carries no"
                        + " verdict: seeds never pass stage 1",
                () -> {
                    assertThat(TheReportFile.step(report, StepNames.SEED_EXTRACTION).path("progress").path("line").asString())
                            .isEqualTo(SEEDS_RECORDED_LINE);
                    assertThat(jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM verdict WHERE occurrence_id IN (" + idsOf(occurrencesNamed(SEED)) + ")",
                                    Integer.class))
                            .isZero();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM unusable_seed", Integer.class)).isZero();
                });
        List<JsonNode> runs = new ArrayList<>();
        report.path("runs").path("rows").forEach(runs::add);
        claim(
                "the report's runs count the two as the code verdicts of byte-level reduction's run",
                () -> assertThat(runs)
                        .filteredOn(row -> row.path("stage").asString().equals("byte-level-reduction"))
                        .anySatisfy(row -> assertThat(row.path("verdicts").path(CODE).asLong()).isEqualTo(TWO_FILES_OF_CODE)));
    }

    private List<Long> occurrencesNamed(String name) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE path = ? OR path LIKE ? ORDER BY id",
                Long.class,
                name,
                "%/" + name);
    }

    private int rowsAbout(String table, List<Long> occurrences) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE occurrence_id IN (" + idsOf(occurrences) + ")", Integer.class);
    }

    private static String idsOf(List<Long> occurrences) {
        return occurrences.isEmpty() ? "-1" : String.join(", ", occurrences.stream().map(String::valueOf).toList());
    }

    private static String page(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        claim("the invocation left " + name + " in the working directory", () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    private static JsonNode theOneCopyWrittenSince(List<Path> before) throws IOException {
        List<Path> written = new ArrayList<>();
        for (Path file : TheReportFile.copiesIn(workingDirectory)) {
            if (!before.contains(file)) {
                written.add(file);
            }
        }
        claim("the invocation wrote exactly one copy of its report", () -> assertThat(written).hasSize(1));
        return TheReportFile.parse(Files.readString(written.getFirst(), StandardCharsets.UTF_8));
    }
}
