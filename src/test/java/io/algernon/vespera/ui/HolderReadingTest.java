package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the control panel reads of a lock file whose first line is not a holder line it can parse (ADR-285 §5): a
 * file absent, empty or blank is held by nobody, since the jar leaves the file between invocations, and a line that
 * is not blank and does not parse is held and shown whole, since the control panel cannot rule out a live holder.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
class HolderReadingTest {

    /** A first line that is not blank and is no holder line: no pid field, and no start time a parse can read. */
    private static final String NOT_A_HOLDER_LINE = "pid=unknown started=yesterday command=run";

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A first line that is not blank and is no holder line counts as held, and is shown whole")
    void aLineThatDoesNotParseIsHeldAndShownWhole(@TempDir Path workingDirectory) throws Exception {
        Path lockFile = Files.writeString(
                workingDirectory.resolve("vespera.lock"), NOT_A_HOLDER_LINE + "\r\n", StandardCharsets.UTF_8);
        HolderReading reading = HolderReading.read(lockFile);

        claim("the line counts as held, since the control panel cannot rule out a live holder",
                () -> assertThat(reading.held()).isTrue());
        claim("it is shown whole, its line separator left out",
                () -> assertThat(reading.text()).isEqualTo(Optional.of(NOT_A_HOLDER_LINE)));
        claim("and no holder line is read out of it", () -> assertThat(reading.line()).isEmpty());
    }
}
