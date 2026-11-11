package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every line the jar writes goes through logback, and the one class that does not is the entry point, for the lines
 * it prints before Spring starts and for {@code vespera serve} (ADR-364 §4).
 *
 * <p>Read off the compiled classes, as {@link ShippedClasses} reads them, because the record names ArchUnit and the pom
 * carries none (ADR-046): a class that reads the field {@code System.out}, {@code System.err},
 * {@code FileDescriptor.out} or {@code FileDescriptor.err}, or calls {@code Throwable.printStackTrace()} with no
 * argument, which prints on standard error, is found whatever it is called.
 */
@Epic("Architecture")
@Feature("Logging")
@Issue("773")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
class OnlyTheEntryPointWritesToTheStandardStreamsTest {

    /** The one class allowed to name the standard streams. */
    private static final String THE_ENTRY_POINT = "io.algernon.vespera.EntryPoint";

    @Test
    @Story("Every line the jar writes goes through logback")
    @DisplayName("No class of the jar but the entry point writes to a standard stream")
    void noClassButTheEntryPointNamesSystemOutOrSystemErr() throws Exception {
        Map<String, List<String>> used = ShippedClasses.standardStreamUseByClass();
        Map<String, List<String>> offenders = new TreeMap<>();
        used.forEach((className, uses) -> {
            if (!className.equals(THE_ENTRY_POINT) && !uses.isEmpty()) {
                offenders.put(className, uses);
            }
        });

        claim(
                "the entry point's own reads were found, so that a scan of nothing cannot pass",
                () -> assertThat(used.get(THE_ENTRY_POINT)).contains("java.lang.System.out", "java.lang.System.err"));
        claim(
                "no other class reads System.out, System.err, FileDescriptor.out or FileDescriptor.err, or prints a"
                        + " stack trace on standard error",
                () -> assertThat(offenders).isEmpty());
    }
}
