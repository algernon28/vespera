package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.ItalianWordingOnRecord;
import io.algernon.vespera.ledger.RunId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The deliverable's language end to end (ADR-322 §1, §2, §4, §6): the profile's {@code deliverableLanguage} reaches
 * the question every cluster is asked, the generation run's identity, and every page the run draws; changing it is a
 * generation run of its own; and a language the deliverable cannot be drawn in stops before anything is asked.
 *
 * <p>The scripted model answers whatever it is asked in the words it was scripted with, so what is claimed of the
 * writing is what it was asked for, never what it wrote. The report says <em>group</em> where the code says cluster
 * (ADR-122), as {@link GenerationInvocationTest} explains.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Synthesis")
@Feature("Writing over the groups")
@Link(name = "ADR-322", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
class TheDeliverableLanguageInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** What the profile says for an Italian deliverable. */
    private static final String ITALIAN = "it";

    /** A language code the deliverable has no fixed words for. */
    private static final String A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN = "fr";

    /** What the question asks the answer to be written in, by the language's English name (ADR-322 §3). */
    private static final String WRITE_BOTH_IN_ITALIAN = "Write both in Italian.";

    private static final String WRITE_BOTH_IN_ENGLISH = "Write both in English.";

    /** What two languages of the same arrangement are worth: two generation runs. */
    private static final int TWO_GENERATION_RUNS = 2;

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
    @Story("The deliverable is in the language the profile names")
    @DisplayName("With Italian named, every group is asked for in Italian, the work records it, and its pages are drawn in Italian")
    void asksAndDrawsInItalianWhereTheProfileNamesIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusUpToTheApprovedArrangement(root, seeds);
        setTheLanguageTo(ITALIAN);
        GenerationScriptedBeans.forgetScriptedAnswers();

        cli.run("run", root.toString());
        List<String> asked = GenerationScriptedBeans.promptsSent();
        String generation = generationRuns(root).getLast();
        Path tree = UnwrittenPage.treeOf(workingDirectory, generation);
        String page = UnwrittenPage.textOf(UnwrittenPage.pageHeadedBy(tree, GenerationScriptedBeans.GENERATED_TITLE));
        String index = UnwrittenPage.textOf(tree.resolve(Deliverable.INDEX_FILE_NAME));

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every group was asked about, and each question asks for the heading and the writing in Italian and"
                        + " never names English",
                () -> assertThat(asked).isNotEmpty().allSatisfy(question -> assertThat(question)
                        .contains(WRITE_BOTH_IN_ITALIAN)
                        .doesNotContain("English")));
        claim(
                "the work records the language among the settings it is identified by, so the same groups in another"
                        + " language are another piece of work",
                () -> assertThat(settingsOf(generation)).endsWith(",\"deliverableLanguage\":\"" + ITALIAN + "\"}"));
        claim(
                "the group's page is an Italian page, its list headed in Italian",
                () -> assertThat(page)
                        .contains("<html lang=\"" + ItalianWordingOnRecord.LANG + "\">")
                        .contains("<h2>" + ItalianWordingOnRecord.MEMBERSHIP_HEADING + "</h2>"));
        claim(
                "and the index heads its table of groups in Italian",
                () -> assertThat(index).contains("<th>" + ItalianWordingOnRecord.THE_TABLE_OF_GROUPS.getFirst() + "</th>"));
    }

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("With the language emptied again, the groups are written again under work of their own, asked for in English")
    void aChangedLanguageIsWorkOfItsOwn(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusUpToTheApprovedArrangement(root, seeds);
        setTheLanguageTo(ITALIAN);
        cli.run("run", root.toString());
        setTheLanguageTo(null);
        GenerationScriptedBeans.forgetScriptedAnswers();

        cli.run("run", root.toString());
        List<String> runs = generationRuns(root);

        claim("the invocation after the change reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the work is recorded " + TWO_GENERATION_RUNS + " times, under two names: writing over the same groups"
                        + " in English is not the Italian work over again, and finding the Italian record would leave"
                        + " English readers with Italian pages",
                () -> assertThat(runs).hasSize(TWO_GENERATION_RUNS).doesNotHaveDuplicates());
        claim(
                "the second records English, the language an empty key means",
                () -> assertThat(settingsOf(runs.getLast())).endsWith(",\"deliverableLanguage\":\"en\"}"));
        claim(
                "and every group was asked for again, in English",
                () -> assertThat(GenerationScriptedBeans.promptsSent()).isNotEmpty().allSatisfy(question ->
                        assertThat(question).contains(WRITE_BOTH_IN_ENGLISH).doesNotContain("Italian")));
    }

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("With a language the pages cannot be drawn in, the invocation stops before anything is asked or recorded")
    void aLanguageItCannotBeDrawnInStopsBeforeAnythingIsAsked(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusUpToTheApprovedArrangement(root, seeds);
        setTheLanguageTo(A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN);
        GenerationScriptedBeans.forgetScriptedAnswers();

        cli.run("run", root.toString());

        claim(
                "the invocation does not report success: the operator wrote a language, and drawing the pages in"
                        + " another would hand the readers one nobody chose",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "no group was asked about",
                () -> assertThat(GenerationScriptedBeans.promptsSent()).isEmpty());
        claim(
                "and no record of the work was made, since there is no language to make it in",
                () -> assertThat(generationRuns(root)).isEmpty());
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /**
     * Two corpus documents and one exemplar with every gate before 6b open, taken through the invocation that stops at
     * the arrangement gate, and that arrangement approved.
     */
    private void aCorpusUpToTheApprovedArrangement(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .build());
    }

    /** Writes {@code deliverableLanguage}, leaving every other key as it was. */
    private void setTheLanguageTo(String language) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .deliverableLanguage(language, language == null ? null : "set by this test")
                .build());
    }

    /** The settings the run is identified by, as recorded. */
    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** Every generation run over {@code root}, oldest first, scoped to its walk as {@link GenerationInvocationTest} scopes it. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
