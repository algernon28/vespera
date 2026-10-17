package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole job over a corpus holding one guide twice, under {@code en/} and {@code zh/} (ADR-271): the
 * translation step finds the two copies by their paths, removes nothing while {@code preferredLanguages} is
 * unset, and once it names English removes the Chinese copy as {@code translation-of} the English one, before
 * anything is scored.
 *
 * <p>Two invocations, once for the class, the way an operator reaches the second: the first with the key
 * unset, then the key written and a second invocation. The scripted extractor gives both copies one text, and
 * stage 4's floor of 1.0 strips every shingle of it, so neither copy is redundant with the other and both
 * reach stage 5.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
class TranslationsInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other whole-job test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model the scripted runtime serves, so the embedding-model gate and everything behind it open. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The English copy of the guide. */
    private static final String ENGLISH_COPY = "en/guide.txt";

    /** The Chinese copy, at the same path but for its language folder. */
    private static final String CHINESE_COPY = "zh/guide.txt";

    /** The reason every translation-of verdict carries (ADR-271 §1), written out because it is persisted. */
    private static final String THE_REASON =
            "the content is carried, in another language, by an occurrence that survives";

    /** The page the step writes in the working directory, by its fixed name. */
    private static final String PAGE = "translations.html";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @TempDir
    static Path scratch;

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Path root;

    /** The scoring run of the invocation with no preferred language. */
    private static String unpreferred;

    /** The scoring run of the invocation preferring English. */
    private static String preferringEnglish;

    /** The profile as the second invocation left it. */
    private static Profile profileAfter;

    /** The page as the second invocation left it. */
    private static String pageAfter;

    @BeforeEach
    void bothInvocationsHaveRun() throws IOException {
        if (root != null) {
            return;
        }
        root = Files.createDirectory(scratch.resolve("corpus"));
        Path seeds = Files.createDirectory(scratch.resolve("seeds"));
        Files.createDirectories(root.resolve("en"));
        Files.createDirectories(root.resolve("zh"));
        Files.writeString(root.resolve(ENGLISH_COPY), "a guide written in English");
        Files.writeString(root.resolve(CHINESE_COPY), "一份用中文写的指南");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
        cli.run("run", root.toString());
        unpreferred = latestScoringRun();

        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .preferredLanguages("en", "set by this test, so the English copy is the one kept")
                .build());
        cli.run("run", root.toString());
        preferringEnglish = latestScoringRun();
        profileAfter = profileStore.load();
        pageAfter = Files.exists(workingDirectory.resolve(PAGE)) ? Files.readString(workingDirectory.resolve(PAGE)) : "";
    }

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("With no preferred language, both copies stand and both are scored")
    void withNoPreferredLanguageBothCopiesStandAndAreScored() {
        claim(
                "the first invocation names no preferred language, so no copy is removed as a translation",
                () -> assertThat(kindsUnder(unpreferred)).doesNotContain("TRANSLATION_OF"));
        claim(
                "and both copies are scored",
                () -> assertThat(scoredUnder(unpreferred)).containsExactlyInAnyOrder(ENGLISH_COPY, CHINESE_COPY));
    }

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("Preferring English, the Chinese copy is a translation of the English one, found by its path")
    void theCopyInTheLanguageNotPreferredIsATranslationOfTheOther() {
        claim(
                "the Chinese copy carries translation-of under the scoring run, and the English copy no verdict"
                        + " of that kind",
                () -> assertThat(verdictsUnder(preferringEnglish))
                        .containsEntry(CHINESE_COPY, "TRANSLATION_OF")
                        .doesNotContainEntry(ENGLISH_COPY, "TRANSLATION_OF"));
        claim(
                "the verdict's reason is the one fixed sentence every such verdict carries, naming no path and no"
                        + " language",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT v.reason FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id"
                                        + " WHERE v.run_id = ? AND v.kind = 'TRANSLATION_OF'",
                                String.class,
                                preferringEnglish))
                        .containsExactly(THE_REASON));
        claim(
                "its row names the English copy as the one kept, the two languages, and that the paths found it",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT removed.path AS removed, kept.path AS kept, t.language, t.kept_language,"
                                        + " t.evidence FROM translation_of t"
                                        + " JOIN file_occurrence removed ON removed.id = t.occurrence_id"
                                        + " JOIN file_occurrence kept ON kept.id = t.translation_of_occurrence_id"
                                        + " WHERE t.run_id = ?",
                                preferringEnglish))
                        .containsExactly(Map.of(
                                "removed", CHINESE_COPY,
                                "kept", ENGLISH_COPY,
                                "language", "zh",
                                "kept_language", "en",
                                "evidence", "parallel-paths")));
    }

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("The copy removed as a translation is never scored")
    void aTranslationIsNotScored() {
        claim(
                "the step runs before relevance scoring, so only the English copy has a score under the run",
                () -> assertThat(scoredUnder(preferringEnglish)).containsExactly(ENGLISH_COPY));
    }

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("Naming a preferred language is a new piece of scoring work")
    void preferringALanguageIsAnotherScoringRun() {
        claim(
                "the preferred languages are part of what identifies the scoring run, so writing one mints a"
                        + " new run rather than changing what the old one removed",
                () -> assertThat(preferringEnglish).isNotEqualTo(unpreferred));
    }

    @Test
    @Story("The page the similarity floor is read off")
    @DisplayName("The page names the run and both keys, and no path")
    void thePageNamesTheRunAndHoldsNoPath() {
        claim(
                "translations.html is in the working directory and names the scoring run it was written for",
                () -> assertThat(pageAfter)
                        .withFailMessage("the page does not name run %s", preferringEnglish)
                        .contains(preferringEnglish));
        claim(
                "it names both keys it is read for",
                () -> assertThat(pageAfter).contains("translationSimilarityFloor").contains("preferredLanguages"));
        claim(
                "and it names neither the archive's root nor a file in it, so it can be copied out",
                () -> assertThat(pageAfter)
                        .doesNotContain(root.toString())
                        .doesNotContain(Walk.canonicalRoot(root).toString())
                        .doesNotContain("guide.txt"));
    }

    @Test
    @Story("The profile points at the page")
    @DisplayName("Both keys' measurements point at the page")
    void theProfilePointsBothKeysAtThePage() {
        String page = workingDirectory.resolve(PAGE).toString();
        List<Measurement> measurements = List.of(
                profileAfter.translationSimilarityFloor().measurement(),
                profileAfter.preferredLanguages().measurement());

        claim(
                "the similarity floor's measurement and the preferred languages' both name translations.html",
                () -> assertThat(measurements)
                        .withFailMessage("the measurements were %s", measurements)
                        .allSatisfy(measurement -> assertThat(measurement.source()).isEqualTo(page)));
    }

    /** The latest scoring run recorded over this test's corpus. */
    private String latestScoringRun() {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = 'embedding-scoring' AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    private List<String> kindsUnder(String run) {
        return jdbcTemplate.queryForList("SELECT kind FROM verdict WHERE run_id = ?", String.class, run);
    }

    /** Each corpus path and the kind of its verdict under {@code run}; a path with two verdicts keeps the last. */
    private Map<String, String> verdictsUnder(String run) {
        Map<String, String> verdicts = new java.util.HashMap<>();
        jdbcTemplate.query(
                "SELECT o.path, v.kind FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id"
                        + " WHERE v.run_id = ? ORDER BY v.id",
                row -> {
                    verdicts.put(row.getString("path"), row.getString("kind"));
                },
                run);
        return verdicts;
    }

    private List<String> scoredUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT o.path FROM relevance_score s JOIN file_occurrence o ON o.id = s.occurrence_id"
                        + " WHERE s.run_id = ?",
                String.class,
                run);
    }
}
