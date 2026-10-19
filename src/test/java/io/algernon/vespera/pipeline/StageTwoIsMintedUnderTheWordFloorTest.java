package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 2's run is minted under the word floor the profile holds when it is minted, and the floor the occurrences
 * are judged by is that one (ADR-282 section 3): written into its {@code config_consumed} where it is a whole number,
 * and not written where it is unset or ignored, so that a stage 2 with no floor keeps the settings it had before the
 * key existed, as {@code extractionAttempt} does (ADR-185 section 1).
 *
 * <p>Assembled by hand as {@link StageThreeWritesTheDocumentFrequencyPageTest} is: a real walk taken through
 * byte-level reduction, then stage 2's run minted through {@link StageRuns}, which reads the profile in stage 1's
 * working directory. Each test has its own working directory and its own corpus root.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Text with nothing in it to score")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
@Link(name = "ADR-185", url = Adr.RAISING_THE_EXTRACTION_ATTEMPT_ASKS_THE_CONVERTER_AGAIN, type = "adr")
class StageTwoIsMintedUnderTheWordFloorTest {

    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    /** The member stage 2's settings carry, as the key is named. */
    private static final String THE_MEMBER = "\"degenerateOutputWordFloor\"";

    /** A floor an operator has written. */
    private static final String A_FLOOR_OF_TWO = "2";

    /** The same floor, as stage 2's settings record it. */
    private static final String RECORDED_AS_TWO = THE_MEMBER + ":2";

    /** A value no floor is: a fraction of a word. */
    private static final String A_FRACTION = "2.5";

    /** A floor the operator writes once stage 2's run is already minted. */
    private static final String WRITTEN_AFTER_THE_MINT = "5";

    private static final String WHY = "written by this test";

    @TempDir
    Path stage1Working;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The word floor names stage 2's run")
    @DisplayName("With the word floor unset, stage 2's settings carry no word floor, and the run judges by none")
    void anUnsetFloorLeavesStageTwosSettingsAsTheyWere(@TempDir Path root) throws Exception {
        StageRuns stageRuns = throughByteLevelReduction(root);

        RunId extraction = stageRuns.extraction();

        claim(
                "the settings stage 2 was minted under name no word floor, so they are those of a stage 2 before the"
                        + " key existed",
                () -> assertThat(settingsOf(extraction)).doesNotContain(THE_MEMBER));
        claim(
                "and the floor the occurrences are judged by is none",
                () -> assertThat(stageRuns.degenerateOutputWordFloor()).isEqualTo(OptionalLong.empty()));
    }

    @Test
    @Story("The word floor names stage 2's run")
    @DisplayName("A word floor of two is recorded among stage 2's settings, and is the floor the run judges by")
    void aFloorIsRecordedAmongStageTwosSettings(@TempDir Path root) throws Exception {
        StageRuns stageRuns = throughByteLevelReduction(root);
        writeTheWordFloor(A_FLOOR_OF_TWO);

        RunId extraction = stageRuns.extraction();

        claim(
                "the settings stage 2 was minted under carry " + RECORDED_AS_TWO + ", so a run under another floor"
                        + " is another run",
                () -> assertThat(settingsOf(extraction)).contains(RECORDED_AS_TWO));
        claim(
                "and the floor the occurrences are judged by is that number",
                () -> assertThat(stageRuns.degenerateOutputWordFloor()).isEqualTo(OptionalLong.of(2)));
    }

    @Test
    @Story("The word floor names stage 2's run")
    @DisplayName("A word floor that is not a whole number is not recorded, and the run judges by none")
    void anIgnoredFloorLeavesStageTwosSettingsAsTheyWere(@TempDir Path root) throws Exception {
        StageRuns stageRuns = throughByteLevelReduction(root);
        writeTheWordFloor(A_FRACTION);

        RunId extraction = stageRuns.extraction();

        claim(
                "\"" + A_FRACTION + "\" is ignored, so stage 2's settings name no word floor",
                () -> assertThat(settingsOf(extraction)).doesNotContain(THE_MEMBER));
        claim(
                "and the run removes nothing for too few words",
                () -> assertThat(stageRuns.degenerateOutputWordFloor()).isEqualTo(OptionalLong.empty()));
    }

    @Test
    @Story("The word floor names stage 2's run")
    @DisplayName("A word floor written after stage 2's run is minted changes neither its settings nor the floor it judges by")
    void theFloorJudgedByIsTheOneTheRunWasMintedUnder(@TempDir Path root) throws Exception {
        StageRuns stageRuns = throughByteLevelReduction(root);
        writeTheWordFloor(A_FLOOR_OF_TWO);
        RunId extraction = stageRuns.extraction();

        writeTheWordFloor(WRITTEN_AFTER_THE_MINT);

        claim(
                "the floor the occurrences are judged by is still " + A_FLOOR_OF_TWO + ", the number the run is"
                        + " named by; " + WRITTEN_AFTER_THE_MINT + " is for the next run",
                () -> assertThat(stageRuns.degenerateOutputWordFloor()).isEqualTo(OptionalLong.of(2)));
        claim(
                "and asking for the run again in the same invocation gives the same run",
                () -> assertThat(stageRuns.extraction()).isEqualTo(extraction));
    }

    /** {@code value} written into the word floor of stage 1's profile, every other key as it was. */
    private void writeTheWordFloor(String value) {
        ProfileStore profileStore = new ProfileStore(stage1Working);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .degenerateOutputWordFloor(value, WHY)
                .build());
    }

    private String settingsOf(RunId run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run.value());
    }

    /**
     * Walks {@code root} over one file and runs byte-level reduction over it in a fresh invocation, and answers the
     * holder stage 2 asks for its run through, over that invocation.
     */
    private StageRuns throughByteLevelReduction(Path root) throws Exception {
        Files.writeString(root.resolve("a-document.txt"), "a document of " + root.getFileName());
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)).walk(root);
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        versions,
                        new ProfileStore(stage1Working),
                        root,
                        stage1Working)
                .execute(null, step);
        ExecutionContext invocation = InvocationRecordFixture.recordOf(step);
        return new StageRuns(
                ledger,
                versions,
                new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                        .getBeanProvider(ExtractorIdentity.class),
                new DegenerateOutputConfidenceFloor(null),
                null,
                null,
                null,
                new ProfileStore(stage1Working),
                null,
                null,
                null,
                null,
                root,
                invocation);
    }
}
