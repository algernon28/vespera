package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No name of ours says partition for the seed partition ADR-396 retired, and no comment of the jar's code does
 * (ADR-426, #903).
 *
 * <p>Each Java source is read as what it is made of: its comments, its string and character literals and text blocks,
 * and the code between them, whose identifiers are its names. A word is taken as the seed partition's when it holds
 * {@value #THE_RETIRED_WORD} in any case; the one use of the word that names no seed partition, the MXF header
 * partition's key of SMPTE ST 377-1 in {@code corpus.BrokenCheck}, is taken out of the text before it is read.
 *
 * <p>Three claims, one a test:
 *
 * <ul>
 *   <li>the jar's code: no identifier and no literal under {@code src/main/java} says it, a literal being text the jar
 *       prints, as a progress line's label is;
 *   <li>the tests' code: no identifier under {@code src/test/java} says it, but a constant of {@link Adr}, which names
 *       a record by the title it keeps, and a name that also says {@value #RETIRE}, which names the retirement as
 *       ADR-396 §10 allows; a test's literals and comments are not read, since a test may say what is no longer there;
 *   <li>the jar's comments: no comment of any class under {@code src/main/java} says it, in a module a stage's
 *       version names as in any other, and outside every module (ADR-426 §4, #903).
 * </ul>
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-426", url = Adr.THE_SEED_PARTITIONS_NAMES_ARE_RETIRED_FROM_THE_CODE_AND_THE_TESTS, type = "adr")
@Link(name = "ADR-396", url = Adr.TOPICS_REPLACE_SEED_PARTITIONS, type = "adr")
@Issue("895")
class TheSeedPartitionIsRetiredFromEveryNameTest {

    /** The word the seed partition was named by, in any case and inside any longer word. */
    private static final String THE_RETIRED_WORD = "partition";

    /** What a name of the tests also says where it names the retirement itself. */
    private static final String RETIRE = "retire";

    /** The uses of the word that name no seed partition: the MXF header partition's key and its comment. */
    private static final List<String> OTHER_USES_OF_THE_WORD = List.of("MXF_HEADER_PARTITION_KEY", "MXF header partition");

    @Test
    @Story("The jar's code says topic where it said partition")
    @DisplayName("No name in Vespera's code, and no text it prints, uses the word partition")
    void noNameOrTextOfTheJarsCodeSaysTheRetiredWord() {
        List<String> found = new ArrayList<>();
        for (Path file : JavaSource.javaFilesUnder(JavaSource.THE_JARS_CODE)) {
            JavaSource source = JavaSource.of(file);
            source.identifiers().stream().filter(TheSeedPartitionIsRetiredFromEveryNameTest::saysIt)
                    .forEach(piece -> found.add(piece.where(file) + " names " + piece.text()));
            source.literals().stream().filter(TheSeedPartitionIsRetiredFromEveryNameTest::saysIt)
                    .forEach(piece -> found.add(piece.where(file) + " prints " + piece.text()));
        }

        claim("no identifier of the jar's code and no text it writes out says partition, the MXF header partition's"
                        + " key aside, which is a file format's word and names nothing of ours",
                () -> assertThat(found)
                        .as("the identifiers and literals of src/main/java that say partition")
                        .isEmpty());
    }

    @Test
    @Story("The tests' code says topic where it said partition")
    @DisplayName("No name in the tests uses the word partition, but the names of decision records and of the word's retirement")
    void noNameOfTheTestsSaysTheRetiredWordButARecordsOrItsRetirements() {
        Set<String> records = Arrays.stream(Adr.class.getFields()).map(Field::getName).collect(Collectors.toSet());
        List<String> found = new ArrayList<>();
        for (Path file : JavaSource.javaFilesUnder(JavaSource.THE_TESTS_CODE)) {
            JavaSource.of(file).identifiers().stream()
                    .filter(TheSeedPartitionIsRetiredFromEveryNameTest::saysIt)
                    .filter(piece -> !records.contains(piece.text()))
                    .filter(piece -> !piece.text().toLowerCase(Locale.ROOT).contains(RETIRE))
                    .forEach(piece -> found.add(piece.where(file) + " names " + piece.text()));
        }

        claim("no identifier of the tests says partition, but a decision record's constant, which keeps the record's"
                        + " title, and a name that says the word is retired",
                () -> assertThat(found)
                        .as("the identifiers of src/test/java that say partition")
                        .isEmpty());
    }

    @Test
    @Issue("903")
    @Story("The jar's comments say topic where they said partition")
    @DisplayName("No comment of Vespera's code uses the word partition, in any module or outside them")
    void noCommentOfTheJarsCodeSaysTheRetiredWord() {
        List<String> found = new ArrayList<>();
        for (Path file : JavaSource.javaFilesUnder(JavaSource.THE_JARS_CODE)) {
            JavaSource.of(file).comments().stream().filter(TheSeedPartitionIsRetiredFromEveryNameTest::saysIt)
                    .forEach(piece -> found.add(piece.where(file) + " says " + piece.text().strip()));
        }

        claim("no comment of any class of the jar's code, in any module or outside them, says partition, the"
                        + " comment on the MXF header partition's key aside, which is a file format's word and names"
                        + " nothing of ours",
                () -> assertThat(found)
                        .as("the comment lines of src/main/java that say partition")
                        .isEmpty());
    }

    private static boolean saysIt(JavaSource.Piece piece) {
        String text = piece.text();
        for (String other : OTHER_USES_OF_THE_WORD) {
            text = text.replace(other, "");
        }
        return text.toLowerCase(Locale.ROOT).contains(THE_RETIRED_WORD);
    }
}
