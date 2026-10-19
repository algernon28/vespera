package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.CodeSetNamesByItsName.defaults;
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
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The operator's renames of code sets, read from {@code code-set-names.csv} where a set's name is rendered
 * (ADR-284 §8): which rows are applied, which names are accepted, and which rows are ignored with the default name
 * kept. {@code CodeSetNames} is reached through {@link CodeSetNamesByItsName}, so this compiles before it is built
 * and fails, until it is, on the class being absent.
 */
@Epic("The deliverable")
@Feature("Names of code sets")
@Issue("572")
@Link(name = "ADR-284", url = Adr.CODE_IS_KEPT_IN_CODE_SETS_NAMED_FOR_THEIR_FOLDER, type = "adr")
class CodeSetNamesTest {

    /** The corpus root's own folder name, which the set at the root is named for by default. */
    private static final String ROOT_NAME = "archivio";

    /** A name longer than any limit a folder name might be thought to have: no limit on its length applies. */
    private static final int LONG_NAME_LENGTH = 300;

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A row renames the code set whose folder it names, the root's set by an empty folder, a folder quoted or written behind a single quote as the list of sets writes it, and the file read with a byte-order mark and carriage returns")
    void aRowRenamesTheSetWhoseFolderItNames() {
        SequencedMap<String, String> sets = defaults(
                "", ROOT_NAME, "=calc", "=calc", "proj", "proj", "vecchio, lib", "vecchio, lib");

        CodeSetNamesByItsName.Applied applied = CodeSetNamesByItsName.apply(
                "\uFEFFfolder,name\r\n,radice\r\n'=calc,calcolo\r\n\"vecchio, lib\",\"libreria, vecchia\"\r\n\r\n",
                sets);

        claim(
                "the file the renames are read from is named code-set-names.csv",
                () -> assertThat(CodeSetNamesByItsName.fileName()).isEqualTo("code-set-names.csv"));
        claim(
                "the root's set, the folder behind a single quote and the quoted folder are renamed as their rows say, the"
                        + " set no row names keeps its name, and every set is given in the order handed over",
                () -> assertThat(applied.nameByFolder()).containsExactly(
                        Map.entry("", "radice"),
                        Map.entry("=calc", "calcolo"),
                        Map.entry("proj", "proj"),
                        Map.entry("vecchio, lib", "libreria, vecchia")));
        claim(
                "no row is ignored, and the blank line at the end is no row",
                () -> assertThat(applied.ignoredFolders()).isEmpty());
        claim(
                "a file whose first line is not the header reads that line as a row",
                () -> assertThat(CodeSetNamesByItsName.apply("proj,progetto\n", sets).nameByFolder())
                        .containsEntry("proj", "progetto"));
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A name is accepted only where a folder could be named so: not blank, none of the characters a folder name cannot hold, not ending in a space or a dot, not a device name of Windows, and of any length")
    void aNameIsAcceptedOnlyWhereAFolderCouldBeNamedSo() {
        List<String> refused = List.of(
                "", "   ", "a<b", "a>b", "a:b", "a\"b", "a/b", "a\\b", "a|b", "a?b", "a*b", "a\u0001b", "a\tb",
                "nome ", "nome.", "CON", "con", "Con.txt", "nul.tar.gz", "AUX", "PRN", "COM1", "com9", "LPT1", "lpt9.doc");
        List<String> accepted = List.of(
                "CONSOLE", "COM0", "COM10", "LPT", "nul-x", "a.b", " leading", "motore à scoppio", "projA-core (2)",
                "x".repeat(LONG_NAME_LENGTH));

        for (String name : refused) {
            claim(
                    "the name \"" + name.replace("\u0001", "\\u0001").replace("\t", "\\t") + "\" is refused",
                    () -> assertThat(CodeSetNamesByItsName.accepts(name)).isFalse());
        }
        for (String name : accepted) {
            claim(
                    "the name of " + name.length() + " characters beginning \"" + name.substring(0, Math.min(name.length(), 20))
                            + "\" is accepted",
                    () -> assertThat(CodeSetNamesByItsName.accepts(name)).isTrue());
        }
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A row naming a folder of no code set, a refused name or a name another set carries, a later row for a folder already given, and a row of three fields are ignored in the file's order, the rest applied")
    void aRowThatCannotBeAppliedIsIgnoredAndTheNameKept() {
        SequencedMap<String, String> sets = defaults("a", "a", "b", "b", "c", "c", "d", "d", "f", "f");

        CodeSetNamesByItsName.Applied applied = CodeSetNamesByItsName.apply(
                "folder,name\n"
                        + "nessuno,nome\n" // no set has this folder
                        + "a,a<b\n" // a name a folder cannot carry
                        + "b,C\n" // another set's name, in another case
                        + "c,nuovo\n" // applied
                        + "d,NUOVO\n" // the name the row above gave, in another case
                        + "c,altro\n" // a folder already given
                        + "f,uno,due\n" // three fields
                        + "a,primo\n", // a folder already given, whose first row was refused
                sets);

        claim(
                "only the one row that can be applied is, and every other set keeps its default name",
                () -> assertThat(applied.nameByFolder()).containsExactly(
                        Map.entry("a", "a"), Map.entry("b", "b"), Map.entry("c", "nuovo"), Map.entry("d", "d"),
                        Map.entry("f", "f")));
        claim(
                "each of the seven rows not applied is named by its folder, in the order of the file",
                () -> assertThat(applied.ignoredFolders()).containsExactly("nessuno", "a", "b", "d", "c", "f", "a"));
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("With no file of renames beside the database every code set keeps its default name, and a file there is read")
    void withNoFileEverySetKeepsItsDefaultName(@TempDir Path workingDirectory) throws Exception {
        SequencedMap<String, String> sets = defaults("a", "a", "b", "b");

        CodeSetNamesByItsName.Applied absent = CodeSetNamesByItsName.read(workingDirectory, sets);
        Files.writeString(workingDirectory.resolve("code-set-names.csv"), "folder,name\na,uno\n", StandardCharsets.UTF_8);
        CodeSetNamesByItsName.Applied present = CodeSetNamesByItsName.read(workingDirectory, sets);

        claim(
                "with no file, each set keeps its default name and nothing is ignored",
                () -> {
                    assertThat(absent.nameByFolder()).containsExactly(Map.entry("a", "a"), Map.entry("b", "b"));
                    assertThat(absent.ignoredFolders()).isEmpty();
                });
        claim(
                "with the file written in the working directory, its row is applied",
                () -> assertThat(present.nameByFolder()).containsExactly(Map.entry("a", "uno"), Map.entry("b", "b")));
    }
}
