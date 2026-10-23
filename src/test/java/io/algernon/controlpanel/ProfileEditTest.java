package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.controlpanel.ProfileEdit.Stated;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the control panel writes one profile value (ADR-295 §3, §4): the key's {@code value} and {@code provenance}
 * lines replaced where they stand and every other byte kept, a block or a file added where there is none, an empty
 * value written as {@code null}, a file in another form refused whole, and each value checked as the jar reads it
 * before anything is written. That the jar reads what is written, and cannot act on what is refused, is the contract
 * test's, {@code pipeline.TheJarReadsWhatTheControlPanelWritesTest}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("566")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
class ProfileEditTest {

    /**
     * A profile as the jar writes it: a measurement mapping under the seed folder, and the floor's provenance broken
     * past 80 columns with a backslash and continued four spaces further in.
     */
    private static final String AS_THE_JAR_WROTE_IT = """
            ---
            seedFolder:
              value: null
              provenance: null
              measurement:
                source: "walk 3"
                refreshedAt: "2026-10-19T10:00:00Z"
            relevanceScoreFloor:
              value: "0.4"
              provenance: "read off relevance-labelling.html, the lowest relevant score of the sixty\\
                \\ labelled"
              measurement: null
            extractionAttempt:
              value: "2"
              provenance: "a second attempt"
              measurement: null
            """;

    /** The same file once the relevance floor is saved as 0.5, the provenance escaped as one line. */
    private static final String WITH_THE_FLOOR_SAVED = """
            ---
            seedFolder:
              value: null
              provenance: null
              measurement:
                source: "walk 3"
                refreshedAt: "2026-10-19T10:00:00Z"
            relevanceScoreFloor:
              value: "0.5"
              provenance: "said \\"half\\" \\\\ then\\na tab\\there, \\u0001 and é"
              measurement: null
            extractionAttempt:
              value: "2"
              provenance: "a second attempt"
              measurement: null
            """;

    /** A provenance with a quote, a backslash, a line feed, a tab, a control character and a letter outside ASCII. */
    private static final String AN_ODD_PROVENANCE = "said \"half\" \\ then\na tab\there, \u0001 and é";

    private static final String PROVENANCE = "read off the page";

    private static final String NOT_IN_FORM =
            "profile.yaml is not in the form the jar writes it, so the control panel does not change it";

    /** The prefix of the provenance {@code vespera label --auto} writes, which lets it write over the floor. */
    private static final String THE_AUTO_RULES_WORDS = "set by the rule that loses no documentation";

    @Test
    @Story("The control panel writes a profile value")
    @DisplayName("Saving one key replaces its value and provenance lines where they stand, and keeps every other byte")
    void oneKeyIsRewrittenAndEveryOtherByteKept() {
        String rewritten = ProfileEdit.rewrite(
                Optional.of(AS_THE_JAR_WROTE_IT), ProfileKey.RELEVANCE_SCORE_FLOOR, "  0.5 ", AN_ODD_PROVENANCE);
        String withWindowsLineEnds = AS_THE_JAR_WROTE_IT.replace("\n", "\r\n");

        claim("the floor's two lines, the broken provenance's continuation among them, are replaced by one line each,"
                        + " the value trimmed and the provenance escaped, and every other byte is as it was",
                () -> assertThat(rewritten).isEqualTo(WITH_THE_FLOOR_SAVED));
        claim("a file whose lines end in a carriage return and a line feed keeps them, its new lines included",
                () -> assertThat(ProfileEdit.rewrite(Optional.of(withWindowsLineEnds), ProfileKey.RELEVANCE_SCORE_FLOOR,
                                "0.5", AN_ODD_PROVENANCE))
                        .isEqualTo(WITH_THE_FLOOR_SAVED.replace("\n", "\r\n")));
        claim("the file read back gives the floor as saved and the other keys as they were, the broken line joined",
                () -> assertThat(ProfileEdit.read(rewritten)).containsExactlyInAnyOrderEntriesOf(Map.of(
                        ProfileKey.SEED_FOLDER, new Stated(null, null),
                        ProfileKey.RELEVANCE_SCORE_FLOOR, new Stated("0.5", AN_ODD_PROVENANCE),
                        ProfileKey.EXTRACTION_ATTEMPT, new Stated("2", "a second attempt"))));
        claim("and the jar's broken provenance is read as one line, its escaped space kept",
                () -> assertThat(ProfileEdit.read(AS_THE_JAR_WROTE_IT).get(ProfileKey.RELEVANCE_SCORE_FLOOR))
                        .isEqualTo(new Stated("0.4",
                                "read off relevance-labelling.html, the lowest relevant score of the sixty labelled")));
    }

    @Test
    @Story("The control panel writes a profile value")
    @DisplayName("An entry, a key's block or the whole file that is not there is added")
    void anAbsentEntryBlockOrFileIsAdded() {
        String readmeStyle = "seedFolder:\n  value: \"/seeds\"\n";

        claim("an entry the key's block does not hold is added after the key's line, the one it holds replaced in place",
                () -> assertThat(ProfileEdit.rewrite(Optional.of("extractionAttempt:\n  value: \"2\"\n"),
                                ProfileKey.EXTRACTION_ATTEMPT, "3", PROVENANCE))
                        .isEqualTo("extractionAttempt:\n  provenance: \"" + PROVENANCE + "\"\n  value: \"3\"\n"));
        claim("a key the file does not hold is added at its end as a block of value, provenance and a null measurement",
                () -> assertThat(ProfileEdit.rewrite(Optional.of(readmeStyle), ProfileKey.EXTRACTION_ATTEMPT, "3",
                                PROVENANCE))
                        .isEqualTo(readmeStyle + "extractionAttempt:\n  value: \"3\"\n  provenance: \"" + PROVENANCE
                                + "\"\n  measurement: null\n"));
        claim("a file that is not there is written as a document start and that one block",
                () -> assertThat(ProfileEdit.rewrite(Optional.empty(), ProfileKey.EXTRACTION_ATTEMPT, "3", PROVENANCE))
                        .isEqualTo("---\nextractionAttempt:\n  value: \"3\"\n  provenance: \"" + PROVENANCE
                                + "\"\n  measurement: null\n"));
        claim("a file holding nothing but white space holds no key: its bytes are kept and the block is added after them",
                () -> assertThat(ProfileEdit.rewrite(Optional.of(" \n"), ProfileKey.EXTRACTION_ATTEMPT, "3", PROVENANCE))
                        .isEqualTo(" \nextractionAttempt:\n  value: \"3\"\n  provenance: \"" + PROVENANCE
                                + "\"\n  measurement: null\n"));
    }

    @Test
    @Story("The control panel writes a profile value")
    @DisplayName("An empty value unsets the key: null is written, with the operator's provenance")
    void anEmptyValueUnsetsTheKeyWithTheProvenance() {
        String unset = ProfileEdit.rewrite(
                Optional.of(AS_THE_JAR_WROTE_IT), ProfileKey.EXTRACTION_ATTEMPT, " ", "back to the first attempt");

        claim("the attempt's value is null and its provenance the operator's, and the other keys are as they were",
                () -> assertThat(unset).isEqualTo(AS_THE_JAR_WROTE_IT.replace(
                        "  value: \"2\"\n  provenance: \"a second attempt\"",
                        "  value: null\n  provenance: \"back to the first attempt\"")));
        claim("an empty value is not refused for its emptiness",
                () -> assertThat(ProfileEdit.refusalOf(ProfileKey.EXTRACTION_ATTEMPT, "", "back to the first attempt"))
                        .isEmpty());
    }

    @Test
    @Story("The control panel writes a profile value")
    @DisplayName("A profile in a form the jar does not write is refused whole, with one line naming the file")
    void aFileInAnotherFormIsRefusedWhole() {
        Map<String, String> otherForms = new LinkedHashMap<>();
        otherForms.put("a comment", "# mine\nseedFolder:\n  value: null\n");
        otherForms.put("a plain scalar over two lines", "relevanceScoreFloor:\n  value: 0.4\n    more\n");
        otherForms.put("a single-quoted scalar over two lines", "relevanceScoreFloor:\n  value: '0.4\n    more'\n");
        otherForms.put("a block scalar", "relevanceScoreFloor:\n  value: |\n    0.4\n");
        otherForms.put("a plain scalar holding a colon and a space", "generationModel:\n  value: qwen3: 14b\n");
        otherForms.put("a comment after a plain scalar", "relevanceScoreFloor:\n  value: 0.4 # guessed\n");
        otherForms.put("a plain scalar beginning with an indicator", "generationModel:\n  value: *anchor\n");
        otherForms.put("a plain scalar ending with a colon", "generationModel:\n  value: qwen3:\n");
        otherForms.put("a flow mapping", "relevanceScoreFloor: {value: \"0.4\"}\n");
        otherForms.put("a key the profile does not have", "seedFolders:\n  value: null\n");
        otherForms.put("a key twice", "seedFolder:\n  value: null\nseedFolder:\n  value: null\n");
        otherForms.put("a key written flat", "seedFolder: \"/seeds\"\n");
        otherForms.put("a tab in an indent", "seedFolder:\n\tvalue: null\n");

        otherForms.forEach((what, text) -> claim("a profile holding " + what + " is refused with the one line",
                () -> assertThatThrownBy(() -> ProfileEdit.rewrite(
                                Optional.of(text), ProfileKey.EXTRACTION_ATTEMPT, "2", PROVENANCE))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage(NOT_IN_FORM)));
    }

    @Test
    @Story("The control panel writes a profile value")
    @DisplayName("Single-quoted and plain values, as the README and a hand edit write them, are read as the jar reads them")
    void singleQuotedAndPlainScalarsAreReadAsTheJarReadsThem(@TempDir Path temp) throws IOException {
        Path seeds = Files.createDirectory(temp.resolve("seeds"));
        String byHand = """
                seedFolder:
                  value: 'D:\\archive\\exemplars'
                  provenance: "the twelve reports I would keep without reading them again"
                relevanceScoreFloor:
                  value: 0.35 \s
                  provenance: read off the page
                translationSimilarityFloor:
                  value: -0.2
                  provenance: 'it''s the lowest row with no refuted pair'
                generationModel:
                  value: null
                  provenance: ''
                embeddingModel:
                  value: ~
                  provenance: nomic-embed-text:v1.5
                """;

        claim("the single-quoted Windows path is read with its backslashes, a plain number as its text without the"
                        + " spaces after it, a plain -0.2 as its text, '' as one quote and as nothing, a plain null as"
                        + " null and a plain ~ as its text",
                () -> assertThat(ProfileEdit.read(byHand)).containsExactlyInAnyOrderEntriesOf(Map.of(
                        ProfileKey.SEED_FOLDER, new Stated("D:\\archive\\exemplars",
                                "the twelve reports I would keep without reading them again"),
                        ProfileKey.RELEVANCE_SCORE_FLOOR, new Stated("0.35", "read off the page"),
                        ProfileKey.TRANSLATION_SIMILARITY_FLOOR,
                                new Stated("-0.2", "it's the lowest row with no refuted pair"),
                        ProfileKey.GENERATION_MODEL, new Stated(null, ""),
                        ProfileKey.EMBEDDING_MODEL, new Stated("~", "nomic-embed-text:v1.5"))));
        claim("and saving the seed folder replaces its single-quoted lines with double-quoted ones, every other byte kept",
                () -> assertThat(ProfileEdit.rewrite(Optional.of(byHand), ProfileKey.SEED_FOLDER, seeds.toString(), "moved"))
                        .isEqualTo(byHand.replace("""
                                  value: 'D:\\archive\\exemplars'
                                  provenance: "the twelve reports I would keep without reading them again"
                                """, "  value: \"" + seeds.toString().replace("\\", "\\\\") + "\"\n  provenance: \"moved\"\n")));
    }

    @Test
    @Story("The control panel checks a profile value before saving it")
    @DisplayName("Each key's value is refused with one line where the jar would read it as unreadable, ignore it or stop on it")
    void eachKeysValueIsCheckedAsTheJarReadsIt(@TempDir Path temp) throws IOException {
        Path aFolder = Files.createDirectory(temp.resolve("seeds"));
        Path aFile = Files.createFile(temp.resolve("a-file"));
        List<Sample> accepted = List.of(
                new Sample(ProfileKey.SEED_FOLDER, aFolder.toString()),
                new Sample(ProfileKey.EMBEDDING_MODEL, "nomic-embed-text:v1.5"),
                new Sample(ProfileKey.GENERATION_MODEL, "qwen3:14b"),
                new Sample(ProfileKey.ARRANGEMENT_APPROVED, "0123456789ab"),
                new Sample(ProfileKey.ARRANGEMENT_APPROVED, "auto"),
                new Sample(ProfileKey.RELEVANCE_SCORE_FLOOR, "0.579"),
                new Sample(ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR, "1.5"),
                new Sample(ProfileKey.TRANSLATION_SIMILARITY_FLOOR, "-0.2"),
                new Sample(ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR, "0"),
                new Sample(ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR, "2147483647"),
                new Sample(ProfileKey.EXTRACTION_ATTEMPT, "1"),
                new Sample(ProfileKey.EXTRACTION_ATTEMPT, "2.0"),
                new Sample(ProfileKey.GENERATION_CONTEXT_WINDOW, "1282"),
                new Sample(ProfileKey.GENERATION_CONTEXT_WINDOW, "2147483647"),
                new Sample(ProfileKey.CLUSTER_PAGE_BYTE_CEILING, "1"),
                new Sample(ProfileKey.CLUSTER_PAGE_BYTE_CEILING, "9223372036854775807"),
                new Sample(ProfileKey.OUT_OF_SCOPE_NAME_ENDINGS, ".P3A, .dgn .x.y"),
                new Sample(ProfileKey.CODE_NAME_ENDINGS, ".java"),
                new Sample(ProfileKey.PREFERRED_LANGUAGES, "it, EN"),
                new Sample(ProfileKey.DELIVERABLE_LANGUAGE, "it"),
                new Sample(ProfileKey.DELIVERABLE_LANGUAGE, "EN"));
        Map<Sample, String> refused = new LinkedHashMap<>();
        refused.put(new Sample(ProfileKey.SEED_FOLDER, "seeds"), notAFolder("seeds"));
        refused.put(new Sample(ProfileKey.SEED_FOLDER, temp.resolve("absent").toString()),
                notAFolder(temp.resolve("absent").toString()));
        refused.put(new Sample(ProfileKey.SEED_FOLDER, aFile.toString()), notAFolder(aFile.toString()));
        refused.put(new Sample(ProfileKey.ARRANGEMENT_APPROVED, "0123456789AB"), notAnArrangement("0123456789AB"));
        refused.put(new Sample(ProfileKey.ARRANGEMENT_APPROVED, "0123456789a"), notAnArrangement("0123456789a"));
        refused.put(new Sample(ProfileKey.ARRANGEMENT_APPROVED, "0123456789abc"), notAnArrangement("0123456789abc"));
        refused.put(new Sample(ProfileKey.RELEVANCE_SCORE_FLOOR, "0,4"), "relevanceScoreFloor is not saved: \"0,4\" is not a number");
        refused.put(new Sample(ProfileKey.DEGENERATE_OUTPUT_CONFIDENCE_FLOOR, "NaN"),
                "degenerateOutputConfidenceFloor is not saved: \"NaN\" is not a number");
        refused.put(new Sample(ProfileKey.TRANSLATION_SIMILARITY_FLOOR, "Infinity"),
                "translationSimilarityFloor is not saved: \"Infinity\" is not a number");
        refused.put(new Sample(ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR, "-1"),
                "degenerateOutputWordFloor is not saved: \"-1\" is not a whole number from 0 to 2147483647");
        refused.put(new Sample(ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR, "1.5"),
                "degenerateOutputWordFloor is not saved: \"1.5\" is not a whole number from 0 to 2147483647");
        refused.put(new Sample(ProfileKey.EXTRACTION_ATTEMPT, "0"),
                "extractionAttempt is not saved: \"0\" is not a whole number from 1 to 2147483647");
        refused.put(new Sample(ProfileKey.EXTRACTION_ATTEMPT, "2147483648"),
                "extractionAttempt is not saved: \"2147483648\" is not a whole number from 1 to 2147483647");
        refused.put(new Sample(ProfileKey.GENERATION_CONTEXT_WINDOW, "1281"),
                "generationContextWindow is not saved: \"1281\" is not a whole number of tokens from 1282 to 2147483647");
        refused.put(new Sample(ProfileKey.GENERATION_CONTEXT_WINDOW, "4096.5"),
                "generationContextWindow is not saved: \"4096.5\" is not a whole number of tokens from 1282 to 2147483647");
        refused.put(new Sample(ProfileKey.CLUSTER_PAGE_BYTE_CEILING, "0"),
                "clusterPageByteCeiling is not saved: \"0\" is not a whole number of bytes from 1 to 9223372036854775807");
        refused.put(new Sample(ProfileKey.CLUSTER_PAGE_BYTE_CEILING, "9223372036854775808"),
                "clusterPageByteCeiling is not saved: \"9223372036854775808\" is not a whole number of bytes from 1 to"
                        + " 9223372036854775807");
        refused.put(new Sample(ProfileKey.OUT_OF_SCOPE_NAME_ENDINGS, ".P3A *.dgn"),
                "outOfScopeNameEndings is not saved: \".P3A *.dgn\" is not a list of name endings, each beginning with a dot");
        refused.put(new Sample(ProfileKey.CODE_NAME_ENDINGS, ".."),
                "codeNameEndings is not saved: \"..\" is not a list of name endings, each beginning with a dot");
        refused.put(new Sample(ProfileKey.PREFERRED_LANGUAGES, "it, eng"),
                "preferredLanguages is not saved: \"it, eng\" is not a list of two-letter language codes");
        refused.put(new Sample(ProfileKey.GENERATION_MODEL, "qwen3\n14b"), "generationModel is not saved: a value is one line");
        refused.put(new Sample(ProfileKey.DELIVERABLE_LANGUAGE, "fr"), "deliverableLanguage is not saved: \"fr\" is not en or it");
        refused.put(new Sample(ProfileKey.DELIVERABLE_LANGUAGE, "italian"),
                "deliverableLanguage is not saved: \"italian\" is not en or it");

        for (Sample sample : accepted) {
            claim(sample.key().key() + " \"" + sample.value() + "\" is saved",
                    () -> assertThat(ProfileEdit.refusalOf(sample.key(), sample.value(), PROVENANCE)).isEmpty());
        }
        refused.forEach((sample, line) -> claim(sample.key().key() + " \"" + sample.value() + "\" is refused with its line",
                () -> assertThat(ProfileEdit.refusalOf(sample.key(), sample.value(), PROVENANCE)).contains(line)));
        claim("a refused value is not written: the rewrite throws the same line",
                () -> assertThatThrownBy(() -> ProfileEdit.rewrite(
                                Optional.of(AS_THE_JAR_WROTE_IT), ProfileKey.EXTRACTION_ATTEMPT, "0", PROVENANCE))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("extractionAttempt is not saved: \"0\" is not a whole number from 1 to 2147483647"));
    }

    @Test
    @Story("The control panel checks a profile value before saving it")
    @DisplayName("Every key needs a provenance, and the relevance floor's is never the words the automatic labelling writes")
    void theProvenanceIsRequiredAndNeverTheAutoRules() {
        for (ProfileKey key : ProfileKey.values()) {
            claim(key.key() + " with a blank provenance is refused, set or unset",
                    () -> {
                        assertThat(ProfileEdit.refusalOf(key, "", "  "))
                                .contains(key.key() + " is not saved: write how you arrived at it in provenance");
                        assertThat(ProfileEdit.refusalOf(key, "", null))
                                .contains(key.key() + " is not saved: write how you arrived at it in provenance");
                    });
        }
        claim("the relevance floor with a provenance in the automatic labelling's words is refused",
                () -> assertThat(ProfileEdit.refusalOf(
                                ProfileKey.RELEVANCE_SCORE_FLOOR, "0.5", THE_AUTO_RULES_WORDS + ", by hand"))
                        .contains("relevanceScoreFloor is not saved: a provenance beginning \"" + THE_AUTO_RULES_WORDS
                                + "\" is the one vespera label --auto writes, and would let it write over this value"));
        claim("and the same words are no bar to another key",
                () -> assertThat(ProfileEdit.refusalOf(ProfileKey.EXTRACTION_ATTEMPT, "2", THE_AUTO_RULES_WORDS))
                        .isEmpty());
    }

    private record Sample(ProfileKey key, String value) {}

    private static String notAFolder(String value) {
        return "seedFolder is not saved: \"" + value + "\" is not the absolute path of a folder that exists";
    }

    private static String notAnArrangement(String value) {
        return "arrangementApproved is not saved: \"" + value
                + "\" is not the twelve characters naming an arrangement, each 0 to 9 or a to f, nor auto";
    }
}
