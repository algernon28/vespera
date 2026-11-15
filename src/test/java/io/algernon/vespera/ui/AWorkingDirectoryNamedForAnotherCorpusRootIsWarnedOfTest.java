package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Run warns, and does not refuse, where the working directory it passes is named in ADR-393 §2's form for a corpus root
 * of another name (ADR-393 §3, amending ADR-054's Consequences): {@code <X>_<yyyyMMdd>} or {@code <X>_<yyyyMMdd>-<n>},
 * {@code X} compared with the corpus root's folder name, case ignored. A name of no such form is warned of by nothing.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class AWorkingDirectoryNamedForAnotherCorpusRootIsWarnedOfTest {

    private static final Path WORK = Path.of("work").toAbsolutePath();

    private static final Path LETTERS = Path.of("archives", "Letters").toAbsolutePath();

    private static final Path INVOICES = Path.of("archives", "Invoices").toAbsolutePath();

    @ParameterizedTest(name = "{0}")
    @MethodSource("namedForAnother")
    @Story("A new working directory is named for its documents and its day")
    @DisplayName("A working directory named for another documents folder is warned of in one line, and Run starts")
    void aWorkingDirectoryNamedForAnotherRootIsWarnedOf(String folderName) {
        Path workingDirectory = WORK.resolve(folderName);

        claim("the one warning names the folder, the root it is named for and the root Run starts it on",
                () -> assertThat(WorkingDirectoryName.warningToRun(workingDirectory, INVOICES)).contains(
                        "the working directory " + folderName + " is named for a corpus root named Letters, and Run"
                                + " starts it on " + INVOICES));
    }

    static Stream<Arguments> namedForAnother() {
        return Stream.of(Arguments.of("Letters_20261114"), Arguments.of("Letters_20261114-2"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notNamedForAnother")
    @Story("A new working directory is named for its documents and its day")
    @DisplayName("A working directory named for its own documents folder, in any case, or named in no such form, is warned of by nothing")
    void aWorkingDirectoryNamedForItsOwnRootOrForNoneIsNot(String folderName) {
        claim("no warning is given for " + folderName + " on the corpus root Letters",
                () -> assertThat(WorkingDirectoryName.warningToRun(WORK.resolve(folderName), LETTERS)).isEmpty());
    }

    static Stream<Arguments> notNamedForAnother() {
        return Stream.of(
                Arguments.of("letters_20261114-3"),
                Arguments.of("20261114"),
                Arguments.of("Invoices_2026"),
                Arguments.of(".vespera"));
    }
}
