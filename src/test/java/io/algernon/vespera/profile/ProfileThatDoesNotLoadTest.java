package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code profile.yaml} the reader cannot load says which file, which key, and what shape a key
 * takes, in one line (#321).
 *
 * <p>The reader is strict about shape (ADR-120): a key written flat, as {@code seedFolder: /seeds},
 * fails the load, and so does a file that is not YAML at all. Both used to reach the operator as
 * Jackson's own message, naming a Java type the operator never wrote and no file, from whichever
 * start-up component asked for the profile first. These pin what the load says instead; that the
 * operator sees it as the whole of the invocation's output, with the exit code reaching the shell, is
 * {@code ProfileThatDoesNotLoadIT}'s, since only a launched process can show it.
 */
@Epic("Census")
@Feature("Profile")
@Issue("321")
@Link(name = "ADR-061", url = Adr.PROFILE_IS_YAML_TYPED_RECORDS, type = "adr")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
class ProfileThatDoesNotLoadTest {

    /** What the README's Step 0 used to lead an operator to write: the answer on the key's own line. */
    private static final String A_KEY_WRITTEN_FLAT = "seedFolder: /seeds\n";

    /** The key the flat file above answers, which is the key the operator has to be told about. */
    private static final String THE_FLAT_KEY = "seedFolder";

    /**
     * A Windows path inside double quotes, where YAML reads the backslash as the start of an escape
     * and {@code \d} is none it knows. Built from a backslash character rather than written with one,
     * so no tool on the way to this file can halve it.
     */
    private static final String NOT_YAML = "seedFolder:\n  value: \"D:" + '\\' + "docs\"\n  provenance: mine\n";

    /** The line of {@link #NOT_YAML} the parser stops on, counted from one as an editor counts. */
    private static final int THE_LINE_THE_PARSER_STOPS_ON = 2;

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A key written flat is refused in one line naming the file, the key and the shape a key takes")
    void aKeyWrittenFlatIsRefusedInOneLine(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), A_KEY_WRITTEN_FLAT);

        Throwable refusal = catchThrowable(store::load);

        claim("the load is refused, since a key written flat carries no provenance and no reader can tell"
                        + " a value from a mistake",
                () -> assertThat(refusal).isNotNull());
        claim("and what it says is one line, because it is the whole of what the operator sees",
                () -> assertThat(refusal.getMessage()).doesNotContain("\n").doesNotContain("\r"));
        claim("naming the file, so the operator knows which one to open",
                () -> assertThat(refusal.getMessage()).contains(store.file().toString()));
        claim("naming the key the operator wrote flat, rather than a component that happened to ask first",
                () -> assertThat(refusal.getMessage()).contains(THE_FLAT_KEY));
        claim("and saying what shape a key takes: the answer under value, and how it was arrived at under"
                        + " provenance, both nested beneath the key",
                () -> assertThat(refusal.getMessage()).contains("value:").contains("provenance:"));
        claim("with no Java type in it, since the operator wrote YAML and never met one",
                () -> assertThat(refusal.getMessage()).doesNotContain("io.algernon").doesNotContain("`"));
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A file that is not YAML is refused in one line naming the file, the line and the shape a key takes")
    void aFileThatIsNotYamlIsRefusedInOneLine(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), NOT_YAML);

        Throwable refusal = catchThrowable(store::load);

        claim("the load is refused",
                () -> assertThat(refusal).isNotNull());
        claim("and what it says is one line, though the parser's own account of it runs to several",
                () -> assertThat(refusal.getMessage()).doesNotContain("\n").doesNotContain("\r"));
        claim("naming the file",
                () -> assertThat(refusal.getMessage()).contains(store.file().toString()));
        claim("and the line the parser stopped on, line " + THE_LINE_THE_PARSER_STOPS_ON
                        + ", which is where the double-quoted Windows path is",
                () -> assertThat(refusal.getMessage()).contains("line " + THE_LINE_THE_PARSER_STOPS_ON));
        claim("and saying what shape a key takes, so the operator rewriting the line writes it in that shape",
                () -> assertThat(refusal.getMessage()).contains("value:").contains("provenance:"));
    }
}
