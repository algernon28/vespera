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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The reader of the copy in {@code logs} of the working directory's last {@code vespera run}'s report (ADR-393 §7):
 * of the copies {@code logs/report-<id>.json}, a copy that is of a {@code format} the reader does not know, is not
 * JSON, or whose {@code format} is no number, is passed over for the copy before it; and a file of {@code logs} whose
 * name is not {@code report-} and an invocation's id, {@code yyyyMMdd'T'HHmmss'Z'} with or without its {@code -<n>},
 * is no copy at all. The ordering of ids and the publish's use of the reader are held by {@code
 * PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest}.
 */
@Epic("Operation")
@Feature("The publish")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class LastRunReaderTest {

    /** The generation run the earlier copy, of the format known, arrived at. */
    private static final String EARLIER = "generation-of-the-earlier-copy";

    /** The generation run each copy passed over names, which the reader must never give. */
    private static final String PASSED_OVER = "generation-of-a-copy-passed-over";

    @TempDir
    Path workingDirectory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("copiesPassedOver")
    @Story("A publish delivers what the last run reached")
    @DisplayName("A later copy of a run's report that is of another format, is not JSON or names its format in no number is passed over for the one before it")
    void aCopyNotOfTheFormatKnownIsPassedOver(String what, String later) throws IOException {
        copy("20261113T080000Z", report(InvocationReport.FORMAT, EARLIER));
        copy("20261114T080000Z", later);

        claim("the reader passes over the later copy, " + what + ", and gives the generation run the earlier copy"
                        + " arrived at",
                () -> assertThat(LastRunReader.of(workingDirectory, StageModules.GENERATION.stage()))
                        .contains(new LastRunReader.Arrival(EARLIER)));
    }

    static Stream<Arguments> copiesPassedOver() {
        return Stream.of(
                Arguments.of("of the format after the one known", report(InvocationReport.FORMAT + 1, PASSED_OVER)),
                Arguments.of("of the format before the one known", report(InvocationReport.FORMAT - 1, PASSED_OVER)),
                Arguments.of("its format a text", report(InvocationReport.FORMAT, PASSED_OVER)
                        .replace("\"format\":" + InvocationReport.FORMAT, "\"format\":\"" + InvocationReport.FORMAT + "\"")),
                Arguments.of("its format missing", report(InvocationReport.FORMAT, PASSED_OVER)
                        .replace("\"format\":" + InvocationReport.FORMAT + ",", "")),
                Arguments.of("cut short, so no JSON", report(InvocationReport.FORMAT, PASSED_OVER).substring(0, 40)));
    }

    @Test
    @Story("A publish delivers what the last run reached")
    @DisplayName("A file of the logs folder whose name is not a report's copy named for an invocation is no copy, however late its name sorts")
    void aFileNotNamedForAnInvocationIsNoCopy() throws IOException {
        String misnamed = report(InvocationReport.FORMAT, PASSED_OVER);
        copy("latest", misnamed);
        copy("20261114", misnamed);
        copy("20261114T090000Z-copy", misnamed);
        copy("20261114T090000", misnamed);
        copy("20261114t090000z", misnamed);
        boolean noneBeforeTheCopy = LastRunReader.of(workingDirectory, StageModules.GENERATION.stage()).isEmpty();
        copy("20261113T080000Z", report(InvocationReport.FORMAT, EARLIER));

        claim("with only the five misnamed files in logs, no copy of a run's report is kept, so the publish takes the"
                        + " last generation made",
                () -> assertThat(noneBeforeTheCopy).isTrue());
        claim("and beside the one copy named for an invocation, that copy is read and the five are not",
                () -> assertThat(LastRunReader.of(workingDirectory, StageModules.GENERATION.stage()))
                        .contains(new LastRunReader.Arrival(EARLIER)));
    }

    /**
     * A copy of an ended {@code vespera run}'s report of {@code format}, its {@code arrivedAt} naming {@code generation}
     * under the generation stage after an arrangement run: the fields ADR-393 §7's reader reads, and a few around them.
     */
    private static String report(int format, String generation) {
        return "{\"format\":" + format + ",\"invocation\":{\"id\":\"20261113T080000Z\",\"pid\":4242,\"command\":\"run\","
                + "\"outcome\":\"completed\"},\"arrivedAt\":[{\"stage\":\"" + StageModules.ARRANGEMENT.stage()
                + "\",\"run\":\"an-arrangement-run\"},{\"stage\":\"" + StageModules.GENERATION.stage() + "\",\"run\":\""
                + generation + "\"}]}";
    }

    /** Writes {@code content} as {@code logs/report-<name>.json} of the working directory. */
    private void copy(String name, String content) throws IOException {
        Path logs = Files.createDirectories(workingDirectory.resolve("logs"));
        Files.writeString(logs.resolve("report-" + name + ".json"), content, StandardCharsets.UTF_8);
    }
}
