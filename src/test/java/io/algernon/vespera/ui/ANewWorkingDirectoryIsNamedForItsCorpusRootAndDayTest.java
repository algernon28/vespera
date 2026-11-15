package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The name New… proposes for a working directory (ADR-393 §2): the corpus root's folder name, {@code _} and the local
 * date as {@code yyyyMMdd}; {@code -2}, {@code -3} and on where an entry of that name is in the parent already, as
 * ADR-362 §3 numbers an invocation's id; the date alone where there is no corpus root's folder name.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class ANewWorkingDirectoryIsNamedForItsCorpusRootAndDayTest {

    /** Half past midnight on 14 November 2026 in Rome, which is still 13 November in UTC. */
    private static final Instant HALF_PAST_MIDNIGHT_IN_ROME = Instant.parse("2026-11-13T23:30:00Z");

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    /** The corpus root's folder name the tests use. */
    private static final String LETTERS = "Letters";

    @TempDir
    Path parent;

    @Test
    @Story("A new working directory is named for its documents and its day")
    @DisplayName("The name is the documents folder's name, an underscore and the local date, a day ahead of UTC's here")
    void theNameIsTheCorpusRootsFolderNameAndTheLocalDate() {
        Path corpusRoot = parent.resolve("archives").resolve(LETTERS);

        Path proposed = WorkingDirectoryName.proposed(parent, Optional.of(corpusRoot), HALF_PAST_MIDNIGHT_IN_ROME, ROME);

        claim("the folder proposed is Letters_20261114 in the parent, the date Rome's and not UTC's 20261113",
                () -> assertThat(proposed).isEqualTo(parent.resolve(LETTERS + "_20261114")));
    }

    @Test
    @Story("A new working directory is named for its documents and its day")
    @DisplayName("Where the name and its -2 are taken the same day, by a folder and by a file, the name proposed ends in -3")
    void aNameTakenTheSameDayTakesTheFirstFreeNumber() throws IOException {
        Files.createDirectory(parent.resolve(LETTERS + "_20261114"));
        Files.writeString(parent.resolve(LETTERS + "_20261114-2"), "");

        Path proposed = WorkingDirectoryName.proposed(
                parent, Optional.of(parent.resolve(LETTERS)), HALF_PAST_MIDNIGHT_IN_ROME, ROME);

        claim("the first free number from 2 is 3, a file of the name taking it as a folder does",
                () -> assertThat(proposed).isEqualTo(parent.resolve(LETTERS + "_20261114-3")));
    }

    @Test
    @Story("A new working directory is named for its documents and its day")
    @DisplayName("With no documents folder named, or one that is a drive's own root, the name is the date alone")
    void aCorpusRootWithNoFolderNameGivesTheDateAlone() {
        Path aFileSystemsRoot = parent.getRoot();

        List<Path> proposed = List.of(
                WorkingDirectoryName.proposed(parent, Optional.empty(), HALF_PAST_MIDNIGHT_IN_ROME, ROME),
                WorkingDirectoryName.proposed(parent, Optional.of(aFileSystemsRoot), HALF_PAST_MIDNIGHT_IN_ROME, ROME));

        claim("no corpus root, and the file system's root, each give 20261114 alone, with no underscore",
                () -> assertThat(proposed).containsExactly(parent.resolve("20261114"), parent.resolve("20261114")));
    }
}
