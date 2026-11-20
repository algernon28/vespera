package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No name of ours says the word ADR-011 gave the Docling and Ollama Vespera runs, and no text the jar prints does, but
 * the earlier names it still reads; no comment does where rewording it moves no run; and the shipped {@code
 * application.yaml} names the settings by their new names; and no name says container of a Docling or an Ollama that
 * may run on another machine (ADR-433 §3 to §5).
 *
 * <p>Each Java source is read by {@link JavaSource}: its comments, its literals, and the identifiers of the code between
 * them. A piece says the earlier word where it holds {@value #THE_EARLIER_WORD} in any case, inside a longer word too.
 *
 * <p>Five claims, one a test:
 *
 * <ul>
 *   <li>the jar's code: no identifier and no literal under {@code src/main/java} says it, but a literal that is exactly
 *       one of the earlier names §5 and §6 still read, and {@value #WAITS_FOR_A_RE_MINT}, which §4 leaves to
 *       {@code extraction}'s next re-mint;
 *   <li>the tests' code: no identifier under {@code src/test/java} says it, but a constant of {@link Adr}, which names a
 *       record by the title it keeps, and no {@code @DisplayName}, {@code @Story}, {@code @Feature} or {@code @Epic} does;
 *       a test's other literals and its comments are not read, since a test may name the earlier names it holds are
 *       still read;
 *   <li>the jar's comments: no comment of a class in a module no stage's version names says it, a module being the folder
 *       below {@code engine} or {@code publish} and the classes outside both lying in none. The modules {@link
 *       StageModules} names are not read: a comment there is reworded only in a change that re-mints the module for its
 *       own reasons (ADR-216 §1 and §7);
 *   <li>the names that said container of a Docling or an Ollama that may run on another machine, which ADR-433 §3
 *       renames, are no identifier of the jar's code or of the tests;
 *   <li>the shipped {@code application.yaml}: none of its lines says it, key or comment.
 * </ul>
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
@Issue("891")
class TheContainersAreNamedSoEverywhereTest {

    /** The word ADR-011 gave, in any case and inside any longer word. */
    private static final String THE_EARLIER_WORD = "sidecar";

    /**
     * The earlier names the jar still reads (ADR-433 §5, §6): the two settings, the distribution's environment variable,
     * which the control panel reads under its own name, and the earlier lock file.
     */
    private static final Set<String> THE_EARLIER_NAMES = Set.of("vespera.sidecars.wait-at-most",
            "vespera.sidecars.wsl-distribution", "VESPERA_SIDECARS_WSL_DISTRIBUTION", "sidecars.lock");

    /** The one identifier of the jar left to its module's next re-mint (ADR-433 §4). */
    private static final String WAITS_FOR_A_RE_MINT = "sidecarGaveUp";

    /** The module that identifier lies in, and the only one where it is let through. */
    private static final String ITS_MODULE = "extraction";

    /**
     * The names ADR-430 gave that say container of a Docling or an Ollama that may run on another machine, which ADR-433
     * §3 renames: of the control panel's code, and of the tests, classes and methods.
     */
    private static final Set<String> NAMES_OF_ONE_WHEREVER_IT_RUNS = Set.of(
            "containerWords",
            "checkTheContainers",
            "RunAndAutoLabelWaitForTheContainersTest",
            "TheCheckAsksEachContainerAndOllamasListOfModelsTest",
            "aContainerIsOkWhereItAnswersTwoXxWithinTwoSecondsAndKoOtherwise",
            "aContainerWhoseHostCannotBeResolvedIsKoAndNotAsked",
            "AContainerIsOnThisMachineWhereItsHostResolvesHereTest",
            "EachContainerIsStartedOnlyWhereItIsOnThisMachineTest",
            "neitherContainerHereAsksNothingAndTheCheckTakesNoLock",
            "itIsOffWhileEverythingHereAnswersWhileItRunsWhileAnInvocationRunsAndForContainersOnAnotherMachine",
            "containerMarks");

    /**
     * Two names too common to be refused everywhere, each refused in its own file: the check's record of a row of Docling
     * or Ollama, here, elsewhere or not resolved, {@code DockerCheck.Container}, and the control panel's question that
     * makes one, {@code DockerControl}'s {@code container(URI, String)}.
     */
    private static final Map<Path, String> NAMES_REFUSED_IN_THEIR_FILE = Map.of(
            Path.of("ui/DockerCheck.java"), "Container",
            Path.of("ui/DockerControl.java"), "container");

    /** The shipped copy of {@code application.yaml} (ADR-397). */
    private static final Path THE_SHIPPED_APPLICATION_YAML = Path.of("src/main/beside-the-jar/application.yaml");

    /** A label of the report a test carries, and its text: one string, or strings joined by {@code +}. */
    private static final Pattern A_REPORT_LABEL = Pattern.compile(
            "@(DisplayName|Story|Feature|Epic)\\(\\s*(\"(?:[^\"\\\\]|\\\\.)*\"(?:\\s*\\+\\s*\"(?:[^\"\\\\]|\\\\.)*\")*)\\s*\\)");

    @Test
    @Story("The jar's code says container where it said the earlier word")
    @DisplayName("No name in Vespera's code, and no text it prints, uses the earlier word, but the earlier settings' and lock's names it still reads")
    void noNameOrTextOfTheJarsCodeSaysTheEarlierWord() {
        List<String> found = new ArrayList<>();
        for (Path file : JavaSource.javaFilesUnder(JavaSource.THE_JARS_CODE)) {
            JavaSource source = JavaSource.of(file);
            boolean inItsModule = JavaSource.moduleOf(file).filter(ITS_MODULE::equals).isPresent();
            source.identifiers().stream()
                    .filter(piece -> saysIt(piece.text()))
                    .filter(piece -> !(inItsModule && piece.text().equals(WAITS_FOR_A_RE_MINT)))
                    .forEach(piece -> found.add(piece.where(file) + " names " + piece.text()));
            source.literals().stream()
                    .filter(piece -> saysIt(piece.text()))
                    .filter(piece -> !THE_EARLIER_NAMES.contains(withoutItsQuotes(piece.text())))
                    .forEach(piece -> found.add(piece.where(file) + " prints " + piece.text()));
        }

        claim("no identifier of the jar's code and no text it writes out says sidecar, but a string that is exactly one"
                        + " of the four earlier names still read, and extraction's one identifier left to its next"
                        + " re-mint",
                () -> assertThat(found)
                        .as("the identifiers and literals of src/main/java that say sidecar")
                        .isEmpty());
    }

    @Test
    @Story("The tests' names say container where they said the earlier word")
    @DisplayName("No name in the tests, and no name a test shows in the report, uses the earlier word, but the names of decision records")
    void noNameOfTheTestsSaysTheEarlierWordButARecordsTitle() throws IOException {
        Set<String> records = Arrays.stream(Adr.class.getFields()).map(Field::getName).collect(Collectors.toSet());
        List<String> found = new ArrayList<>();
        for (Path file : JavaSource.javaFilesUnder(JavaSource.THE_TESTS_CODE)) {
            JavaSource.of(file).identifiers().stream()
                    .filter(piece -> saysIt(piece.text()))
                    .filter(piece -> !records.contains(piece.text()))
                    .forEach(piece -> found.add(piece.where(file) + " names " + piece.text()));
            Matcher label = A_REPORT_LABEL.matcher(Files.readString(file));
            while (label.find()) {
                if (saysIt(label.group(2))) {
                    found.add(file.toString().replace('\\', '/') + " shows @" + label.group(1) + " " + label.group(2));
                }
            }
        }

        claim("no identifier of the tests says sidecar, but a decision record's constant, which keeps the record's"
                        + " title, and no display name, story, feature or epic does",
                () -> assertThat(found)
                        .as("the identifiers and report labels of src/test/java that say sidecar")
                        .isEmpty());
    }

    @Test
    @Story("The jar's comments say container where rewording them moves no run")
    @DisplayName("No comment of Vespera's code uses the earlier word, in every part of it whose change re-runs no stage")
    void noCommentOfAModuleNoStageNamesSaysTheEarlierWord() {
        Set<String> namedByAStage = Arrays.stream(StageModules.values())
                .flatMap(stage -> stage.modules().stream())
                .collect(Collectors.toSet());
        List<String> found = new ArrayList<>();
        int read = 0;
        for (Path file : JavaSource.javaFilesUnder(JavaSource.THE_JARS_CODE)) {
            if (JavaSource.moduleOf(file).filter(namedByAStage::contains).isPresent()) {
                continue;
            }
            read++;
            JavaSource.of(file).comments().stream()
                    .filter(piece -> saysIt(piece.text()))
                    .forEach(piece -> found.add(piece.where(file) + " says " + piece.text().strip()));
        }
        int classesRead = read;

        claim("the modules a stage's version names are left out, and every other class is read: there are such classes",
                () -> assertThat(classesRead).as("how many classes outside the stages' modules were read").isPositive());
        claim("no comment of a class in a module no stage's version names, nor of one outside every module, says"
                        + " sidecar",
                () -> assertThat(found)
                        .as("the comment lines that say sidecar, outside the modules %s", namedByAStage)
                        .isEmpty());
    }

    @Test
    @Story("The code's names say container only of what Vespera runs here")
    @DisplayName("No name of Vespera's code or of the tests calls Docling or Ollama a container where it may run on another machine")
    void noNameSaysContainerOfDoclingOrOllamaWhereverTheyRun() {
        List<String> found = new ArrayList<>();
        for (Path root : List.of(JavaSource.THE_JARS_CODE, JavaSource.THE_TESTS_CODE)) {
            for (Path file : JavaSource.javaFilesUnder(root)) {
                String refusedHere = NAMES_REFUSED_IN_THEIR_FILE.entrySet().stream()
                        .filter(entry -> root == JavaSource.THE_JARS_CODE && file.endsWith(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
                JavaSource.of(file).identifiers().stream()
                        .filter(piece -> NAMES_OF_ONE_WHEREVER_IT_RUNS.contains(piece.text())
                                || piece.text().equals(refusedHere))
                        .forEach(piece -> found.add(piece.where(file) + " names " + piece.text()));
            }
        }

        claim("none of the " + NAMES_OF_ONE_WHEREVER_IT_RUNS.size() + " names that said container of a Docling or an"
                        + " Ollama that may run on another machine is a name of the jar's code or of the tests, and"
                        + " neither the check's record of a row nor the question that makes one is called a container",
                () -> assertThat(found)
                        .as("the identifiers that still say container of one wherever it runs")
                        .isEmpty());
    }

    @Test
    @Story("The settings are named for the containers")
    @DisplayName("No line of the application.yaml the jar ships, key or comment, uses the earlier word")
    void noLineOfTheShippedApplicationYamlSaysTheEarlierWord() throws IOException {
        List<String> lines = Files.readAllLines(THE_SHIPPED_APPLICATION_YAML);
        List<String> found = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (saysIt(lines.get(i))) {
                found.add((i + 1) + ": " + lines.get(i).strip());
            }
        }

        claim("the shipped application.yaml was read, and it is not empty",
                () -> assertThat(lines).as("the lines of %s", THE_SHIPPED_APPLICATION_YAML).isNotEmpty());
        claim("none of its lines says sidecar: the two settings are under vespera.containers, and the comments say"
                        + " container, Docling or Ollama",
                () -> assertThat(found).as("the lines of the shipped application.yaml that say sidecar").isEmpty());
    }

    private static boolean saysIt(String text) {
        return text.toLowerCase(Locale.ROOT).contains(THE_EARLIER_WORD);
    }

    /** A string literal's text without its two quotes; any other literal as it is. */
    private static String withoutItsQuotes(String literal) {
        if (literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"") && !literal.startsWith("\"\"\"")) {
            return literal.substring(1, literal.length() - 1);
        }
        return literal;
    }
}
