package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.math.BigDecimal;
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
    @DisplayName("The order of the preferred languages chooses the copy kept")
    void theOrderOfThePreferredLanguagesChoosesTheCopyKept() {
        List<Translations.Member> members = List.of(
                member(1, "en/api/index.html", "ENGLISH"),
                member(2, "zh/api/index.html", "CHINESE"));

        Translations.Resolution resolution =
                Translations.resolve(members, List.of(), NO_FLOOR, List.of("zh", "en"));

        claim(
                "with Chinese preferred before English, the English copy is the one removed",
                () -> assertThat(resolution.removals()).containsExactly(new Translations.Removal(
                        id(1), id(2), "en", "zh", Translations.Evidence.PARALLEL_PATHS)));
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
    @DisplayName("Where no copy is in a preferred language, the copy with the lowest path is kept")
    void whereNoCopyIsInAPreferredLanguageTheLowestPathIsKept() {
        List<Translations.Member> members = List.of(
                member(1, "en/x.html", "ENGLISH"),
                member(2, "ja/x.html", "JAPANESE"),
                member(3, "zh/x.html", "CHINESE"));

        Translations.Resolution resolution = Translations.resolve(members, List.of(), NO_FLOOR, List.of("it"));

        claim(
                "Italian is preferred and no copy is Italian, so en/x.html, the lowest of the three paths, is"
                        + " kept and the Japanese and Chinese copies are translations of it",
                () -> assertThat(resolution.removals()).containsExactly(
                        new Translations.Removal(id(2), id(1), "ja", "en", Translations.Evidence.PARALLEL_PATHS),
                        new Translations.Removal(id(3), id(1), "zh", "en", Translations.Evidence.PARALLEL_PATHS)));
    }

    @Test
    @Story("A copy under a folder of another language")
    @DisplayName("Only paths one lowercase language folder apart, with no second one, are translations by their paths")
    void onlyOneLowercaseLanguageFolderInOneDirectoryMakesTranslationsByTheirPaths() {
        List<Translations.Member> members = List.of(
                member(1, "IT/budget.pdf", "ITALIAN"),
                member(2, "HR/budget.pdf", "ITALIAN"),
                member(3, "docs/en", "ENGLISH"),
                member(4, "docs/zh", "CHINESE"),
                member(5, "en/v1/x.html", "ENGLISH"),
                member(6, "zh/v2/x.html", "CHINESE"),
                member(7, "en/y.html", "ENGLISH"),
                member(8, "zh/sub/y.html", "CHINESE"),
                member(9, "en/de/z.html", "ENGLISH"),
                member(10, "zh/de/z.html", "CHINESE"));

        Translations.Resolution resolution = Translations.resolve(members, List.of(), NO_FLOOR, List.of("en"));

        claim(
                "IT and HR are in capitals; en and zh in docs are the files' own names; v1 and v2 make two"
                        + " segments differ; y.html lies at two depths; and en/de and zh/de each hold a second"
                        + " language folder -- so no two of the ten are translations by their paths",
                () -> assertThat(resolution.removals()).isEmpty());
        claim(
                "and there is no set at all",
                () -> assertThat(resolution.sets()).isZero());
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

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("With no floor no likeness is applied, and each is still counted on its row")
    void anUnsetFloorAppliesNoLikenessAndStillCountsThem() {
        List<Translations.Member> members = List.of(
                member(1, "manuali/guida.pdf", "ITALIAN"),
                member(2, "manuals/guide.pdf", "ENGLISH"),
                member(3, "other/a.pdf", "ENGLISH"),
                member(4, "altro/b.pdf", "ITALIAN"));
        List<Translations.Likeness> likenesses = List.of(likeness(1, 2, 0.93), likeness(3, 4, 0.92));

        Translations.Resolution resolution = Translations.resolve(members, likenesses, NO_FLOOR, List.of("it"));

        claim(
                "no floor is written, so no likeness removes anything",
                () -> assertThat(resolution.removals()).isEmpty());
        claim(
                "and the page still shows the two likenesses, one on the row 0.92 and one on 0.93, which is"
                        + " what the floor is read off",
                () -> assertThat(resolution.rows()).containsExactly(
                        new Translations.SimilarityRow(new BigDecimal("0.92"), 1, 0, 0),
                        new Translations.SimilarityRow(new BigDecimal("0.93"), 1, 0, 0)));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("A floor set to a row's value applies that row, compared exactly")
    void aFloorSetToARowsValueAppliesThatRow() {
        List<Translations.Member> members = List.of(
                member(1, "manuali/guida.pdf", "ITALIAN"),
                member(2, "manuals/guide.pdf", "ENGLISH"));
        List<Translations.Likeness> likenesses = List.of(likeness(1, 2, 0.29));

        Translations.Resolution atItsRow = Translations.resolve(members, likenesses, OptionalDouble.of(0.28), List.of("it"));
        Translations.Resolution aboveItsRow =
                Translations.resolve(members, likenesses, OptionalDouble.of(0.29), List.of("it"));

        claim(
                "the double nearest 0.29 is 0.28999999999999998..., so its exact value rounds down to the row"
                        + " 0.28",
                () -> assertThat(atItsRow.rows()).extracting(Translations.SimilarityRow::similarity)
                        .containsExactly(new BigDecimal("0.28")));
        claim(
                "a floor of 0.28, the row's value, applies it, as the page's rule says",
                () -> assertThat(atItsRow.removals()).hasSize(1));
        claim(
                "and a floor of 0.29 does not, so the rule and the row never disagree",
                () -> assertThat(aboveItsRow.removals()).isEmpty());
    }

    @Test
    @Story("The page the similarity floor is read off")
    @DisplayName("Each row counts the likenesses the paths confirm and the ones they refute")
    void theRowsCountTheLikenessesTheirPathsConfirmAndRefute() {
        List<Translations.Member> members = List.of(
                member(1, "en/a.html", "ENGLISH"),
                member(2, "zh/a.html", "CHINESE"),
                member(3, "en/b.html", "ENGLISH"),
                member(4, "zh/b.html", "CHINESE"),
                member(5, "manuali/c.pdf", "ITALIAN"),
                member(6, "docs/d.pdf", "ENGLISH"));
        List<Translations.Likeness> likenesses =
                List.of(likeness(1, 2, 0.91), likeness(2, 3, 0.81), likeness(5, 6, 0.81));

        Translations.Resolution resolution = Translations.resolve(members, likenesses, NO_FLOOR, List.of("en"));

        claim(
                "on 0.81: two likenesses, one refuted, zh/a.html with en/b.html, whose translation into Chinese"
                        + " by its path is zh/b.html, and one the paths say nothing of; on 0.91: one, zh/a.html"
                        + " with en/a.html, which its paths confirm",
                () -> assertThat(resolution.rows()).containsExactly(
                        new Translations.SimilarityRow(new BigDecimal("0.81"), 2, 0, 1),
                        new Translations.SimilarityRow(new BigDecimal("0.91"), 1, 1, 0)));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("A set joined by its paths and by a likeness keeps one copy of the three")
    void aSetJoinedByItsPathsAndALikenessKeepsOneCopy() {
        List<Translations.Member> members = List.of(
                member(1, "en/x.html", "ENGLISH"),
                member(2, "zh/x.html", "CHINESE"),
                member(3, "manuali/x.pdf", "ITALIAN"));
        List<Translations.Likeness> likenesses = List.of(likeness(1, 3, 0.95));

        Translations.Resolution resolution =
                Translations.resolve(members, likenesses, OptionalDouble.of(0.9), List.of("it"));

        claim(
                "the English and Chinese copies are joined by their paths, the English and Italian ones by a"
                        + " likeness over the floor, so the three are one set: the Italian copy is kept and both"
                        + " others are translations of it, by both kinds of evidence",
                () -> assertThat(resolution.removals()).containsExactly(
                        new Translations.Removal(id(1), id(3), "en", "it", Translations.Evidence.BOTH),
                        new Translations.Removal(id(2), id(3), "zh", "it", Translations.Evidence.BOTH)));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("A pair joined both by its paths and by a likeness over the floor records both kinds of evidence")
    void aPairJoinedByItsPathsAndByALikenessRecordsBoth() {
        List<Translations.Member> members = List.of(
                member(1, "en/x.html", "ENGLISH"),
                member(2, "zh/x.html", "CHINESE"));
        List<Translations.Likeness> likenesses = List.of(likeness(1, 2, 0.95));

        Translations.Resolution resolution =
                Translations.resolve(members, likenesses, OptionalDouble.of(0.9), List.of("en"));

        claim(
                "the two paths differ in their language folder alone, and the two copies are each other's nearest"
                        + " over the floor: the one pair is linked both ways, so the Chinese copy is a translation"
                        + " of the English one by both kinds of evidence, not by its paths alone",
                () -> assertThat(resolution.removals()).containsExactly(new Translations.Removal(
                        id(2), id(1), "zh", "en", Translations.Evidence.BOTH)));
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
