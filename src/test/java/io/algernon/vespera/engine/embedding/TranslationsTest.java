package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which survivors are translations of which, and which copy of each translation set survives (ADR-271 §2,
 * §4), and the rows of the page the similarity floor is read off (ADR-271 §7).
 *
 * <p>A member is given as the step hands it over: its occurrence, its path beneath the corpus root, and its
 * language as stage 2 stored it, Lingua's name or none. A likeness is given as {@link TranslationCandidates}
 * found it, with the cosine of the two mean vectors.
 */
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
@Link(name = "ADR-260", url = Adr.A_SHINGLE_REACHES_THE_BOILERPLATE_FLOOR_BY_EXACT_DECIMAL_ARITHMETIC, type = "adr")
class TranslationsTest {

    /** No similarity floor written in the profile. */
    private static final OptionalDouble NO_FLOOR = OptionalDouble.empty();

    /** No language written in the profile's preferred languages. */
    private static final List<String> NO_PREFERENCE = List.of();

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("A copy under another language's folder gives way to the one in the first preferred language present")
    void aCopyUnderAnotherLanguageFolderIsATranslationOfTheOneInTheFirstPreferredLanguage() {
        List<Translations.Member> members = List.of(
                member(1, "en/api/index.html", "ENGLISH"),
                member(2, "zh/api/index.html", "ENGLISH"));

        Translations.Resolution resolution =
                Translations.resolve(members, List.of(), NO_FLOOR, List.of("it", "en"));

        claim(
                "the two paths differ only in a folder of English and one of Chinese, so the Chinese copy is a"
                        + " translation of the English one, kept because English is the first preferred language"
                        + " present -- though both were detected as English, which is not read",
                () -> assertThat(resolution.removals()).containsExactly(new Translations.Removal(
                        id(2), id(1), "zh", "en", Translations.Evidence.PARALLEL_PATHS)));
        claim(
                "they are one set, and none stands",
                () -> assertThat(List.of(resolution.sets(), resolution.setsStanding())).containsExactly(1L, 0L));
    }

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("With no preferred language, nothing is removed and the set is counted")
    void withNoPreferredLanguageNothingIsRemovedAndTheSetIsCounted() {
        List<Translations.Member> members = List.of(
                member(1, "en/api/index.html", "ENGLISH"),
                member(2, "zh/api/index.html", "CHINESE"));

        Translations.Resolution resolution = Translations.resolve(members, List.of(), NO_FLOOR, NO_PREFERENCE);

        claim(
                "no copy is chosen for the operator, so neither is removed",
                () -> assertThat(resolution.removals()).isEmpty());
        claim(
                "and the one set waiting for a preference is counted",
                () -> assertThat(resolution.sets()).isEqualTo(1L));
    }

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("A set holding two copies in one language stands whole")
    void twoCopiesInOneLanguageLeaveTheirSetStanding() {
        List<Translations.Member> members = List.of(
                member(1, "en/x.html", "ENGLISH"),
                member(2, "en_GB/x.html", "ENGLISH"),
                member(3, "zh/x.html", "CHINESE"));

        Translations.Resolution resolution = Translations.resolve(members, List.of(), NO_FLOOR, List.of("en"));

        claim(
                "en and en_GB are both English, so the set holds two English copies, neither a translation of"
                        + " the other, and which one the Chinese copy translates is not known: nothing is removed",
                () -> assertThat(resolution.removals()).isEmpty());
        claim(
                "and the one set is counted as standing",
                () -> assertThat(List.of(resolution.sets(), resolution.setsStanding())).containsExactly(1L, 1L));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("A likeness at the floor is applied, one below it is not")
    void aLikenessAtTheFloorIsAppliedAndOneBelowItIsNot() {
        List<Translations.Member> members = List.of(
                member(1, "manuali/guida.pdf", "ITALIAN"),
                member(2, "manuals/guide.pdf", "ENGLISH"),
                member(3, "other/a.pdf", "ENGLISH"),
                member(4, "altro/b.pdf", "ITALIAN"));
        List<Translations.Likeness> likenesses = List.of(likeness(1, 2, 0.93), likeness(3, 4, 0.92));

        Translations.Resolution resolution =
                Translations.resolve(members, likenesses, OptionalDouble.of(0.93), List.of("it"));

        claim(
                "at a floor of 0.93 the likeness of 0.93 is applied and the English copy goes, by its"
                        + " embedding; the likeness of 0.92 is below it and both its copies stand",
                () -> assertThat(resolution.removals()).containsExactly(new Translations.Removal(
                        id(2), id(1), "en", "it", Translations.Evidence.EMBEDDINGS)));
    }

    private static Translations.Member member(long id, String path, String detectedLanguage) {
        return new Translations.Member(id(id), new OccurrencePath(path), detectedLanguage);
    }

    private static Translations.Likeness likeness(long first, long second, double similarity) {
        return new Translations.Likeness(id(first), id(second), similarity);
    }

    private static OccurrenceId id(long value) {
        return new OccurrenceId(value);
    }
}
