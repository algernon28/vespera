package io.algernon.vespera.engine.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * The two keys the translation step read, {@code translationSimilarityFloor} and {@code preferredLanguages}, are
 * retired keys (ADR-392 §4): a profile naming either loads, every other answer read as written; where one holds a
 * value, the first load of the file in this process says so in one line at WARN and no later load says it again; one
 * left unset says nothing; and the next save writes neither. Any other name the profile does not have still fails the
 * load (ADR-120).
 *
 * <p>Each test writes its own file in its own folder, since the line is written once per file in a process. Every
 * claim but the last fails while the profile has a component for either key, or while a load refuses them.
 */
@Epic("Census")
@Feature("Profile")
@Issue("771")
@Link(name = "ADR-392", url = Adr.VESPERA_FINDS_NO_TRANSLATION_SETS, type = "adr")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
class TheRetiredKeysTest {

    private static final String SIMILARITY_FLOOR = "translationSimilarityFloor";

    private static final String PREFERRED_LANGUAGES = "preferredLanguages";

    /** An answer of a key the profile still has, which loading and saving beside the retired keys must leave as written. */
    private static final String RELEVANCE_FLOOR = "0.42";

    private static final String RELEVANCE_FLOOR_PROVENANCE = "read off the labelling page";

    private static final String THE_OTHER_ANSWER = """
            relevanceScoreFloor:
              value: "0.42"
              provenance: read off the labelling page
            """;

    /** Both retired keys holding a value, the floor with the page pointer an earlier version wrote beside it. */
    private static final String BOTH_HOLDING_A_VALUE = THE_OTHER_ANSWER + """
            translationSimilarityFloor:
              value: "0.93"
              provenance: read off the translations page
              measurement:
                source: translations.html
                refreshedAt: "2026-10-20T09:00:00Z"
            preferredLanguages:
              value: it, en
              provenance: the languages to keep
            """;

    /** The languages holding a value, and the floor unset beside it with a provenance and no value. */
    private static final String THE_LANGUAGES_ALONE_HOLDING_A_VALUE = THE_OTHER_ANSWER + """
            translationSimilarityFloor:
              value: null
              provenance: left for later
            preferredLanguages:
              value: it, en
              provenance: the languages to keep
            """;

    /** Both retired keys present and unset: one with a provenance and no value, the other blank. */
    private static final String BOTH_UNSET = THE_OTHER_ANSWER + """
            translationSimilarityFloor:
              value: null
              provenance: left for later
            preferredLanguages:
              value: "  "
              provenance: null
            """;

    /** A name one letter short of a retired key's, which is no key the profile has or had. */
    private static final String A_NAME_NO_PROFILE_HAS = THE_OTHER_ANSWER + """
            preferredLanguage:
              value: it, en
              provenance: the languages to keep
            """;

    @Test
    @Story("A profile written by an earlier version")
    @DisplayName("A profile naming the two keys an earlier version read loads, every other answer as written, and they are no keys of the profile")
    void aProfileNamingARetiredKeyLoadsWithEveryOtherAnswer(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), BOTH_HOLDING_A_VALUE);

        Throwable refusal = catchThrowable(store::load);

        claim("the file loads, though it names the similarity floor for copies in other languages and the languages"
                        + " preferred",
                () -> assertThat(refusal).isNull());
        claim("and the relevance floor beside them reads as written, its value and how it was arrived at",
                () -> {
                    NumericValue floor = store.load().relevanceScoreFloor();
                    assertThat(floor.value()).isEqualTo(RELEVANCE_FLOOR);
                    assertThat(floor.provenance()).isEqualTo(RELEVANCE_FLOOR_PROVENANCE);
                });
        claim("and neither name is a key the profile carries",
                () -> assertThat(ProfileKeys.everyKey()).doesNotContain(SIMILARITY_FLOOR, PREFERRED_LANGUAGES));
    }

    @Test
    @Story("A profile written by an earlier version")
    @DisplayName("Where both keys an earlier version read hold a value, the first load says so in one line naming both, and a second load says nothing more")
    void aRetiredKeyHoldingAValueIsToldOfInOneLineOnce(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), BOTH_HOLDING_A_VALUE);

        List<String> warned = warningsWhile(() -> {
            store.load();
            store.load();
        });

        claim("two loads of the file write one line at WARN, naming the file and both keys in the profile's order,"
                        + " saying they are no longer read since every copy in another language is kept, and that"
                        + " the next write of the profile leaves them out",
                () -> assertThat(warned).containsExactly(store.file() + " holds translationSimilarityFloor and"
                        + " preferredLanguages, which Vespera no longer reads, since every copy of a document in"
                        + " another language is now kept: they are ignored, and left out the next time Vespera"
                        + " writes the profile"));
    }

    @Test
    @Story("A profile written by an earlier version")
    @DisplayName("Where one of the two keys holds a value and the other is unset, the line names the one holding a value alone")
    void aRetiredKeyHoldingAValueIsNamedAloneBesideOneUnset(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), THE_LANGUAGES_ALONE_HOLDING_A_VALUE);

        List<String> warned = warningsWhile(store::load);

        claim("the one line names the languages preferred alone, the floor holding no value",
                () -> assertThat(warned).containsExactly(store.file() + " holds preferredLanguages, which Vespera"
                        + " no longer reads, since every copy of a document in another language is now kept: it is"
                        + " ignored, and left out the next time Vespera writes the profile"));
    }

    @Test
    @Story("A profile written by an earlier version")
    @DisplayName("Where neither key an earlier version read holds a value, the profile loads without a word")
    void aRetiredKeyLeftUnsetPassesSilently(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), BOTH_UNSET);

        List<String> warned = warningsWhile(store::load);

        claim("a key with no value, though it states how it was arrived at, and a key whose value is blank, write no"
                        + " line",
                () -> assertThat(warned).isEmpty());
    }

    @Test
    @Story("A profile written by an earlier version")
    @DisplayName("The next save of a profile naming the two keys an earlier version read writes neither, and every other answer as it was")
    void theNextSaveLeavesTheRetiredKeysOut(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), BOTH_HOLDING_A_VALUE);

        store.save(store.load());
        List<String> lines = Files.readAllLines(store.file());

        claim("the file written names neither key, on any line",
                () -> assertThat(lines).noneMatch(line -> line.contains(SIMILARITY_FLOOR))
                        .noneMatch(line -> line.contains(PREFERRED_LANGUAGES)));
        claim("and the relevance floor is written as it was read, its value and how it was arrived at",
                () -> {
                    NumericValue floor = store.load().relevanceScoreFloor();
                    assertThat(floor.value()).isEqualTo(RELEVANCE_FLOOR);
                    assertThat(floor.provenance()).isEqualTo(RELEVANCE_FLOOR_PROVENANCE);
                });
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A name one letter short of a key an earlier version read is no key, and still fails the load")
    void anyOtherKeyThatIsNotTheProfilesStillFailsTheLoad(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), A_NAME_NO_PROFILE_HAS);

        Throwable refusal = catchThrowable(store::load);

        claim("the load is refused as a misshapen profile, only the two names an earlier version read being let"
                        + " through",
                () -> assertThat(refusal).isInstanceOf(MisshapenProfileException.class));
    }

    /** The lines {@link ProfileStore} writes at WARN while {@code loads} runs, each as it is formatted. */
    private static List<String> warningsWhile(Runnable loads) {
        Logger logger = (Logger) LoggerFactory.getLogger(ProfileStore.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            loads.run();
        } finally {
            logger.detachAppender(logged);
        }
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
