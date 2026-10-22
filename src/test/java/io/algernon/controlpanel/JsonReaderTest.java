package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel's own reader of the JSON its sidecars answer with (ADR-302 §5), of the JSON grammar whole: each
 * kind of value read as its Java value, an object's names in the order written, every escape of a string, every form
 * of a number, and anything that is not one JSON value refused with the position it was found at.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class JsonReaderTest {

    /** How deep arrays may nest before the text is refused, so that nesting cannot exhaust the stack. */
    private static final int DEEPEST = 256;

    @Test
    @Story("The control panel reads what its services answer")
    @DisplayName("An object is read with its names in the order written, each value as its kind, a name given twice keeping its last value")
    void eachKindOfValueIsReadAsItsJavaValueAndAnObjectKeepsItsOrder() {
        Object read = JsonReader.read("""
                 {"z": "text", "a": 12, "m": [true, false, null, {}, []], "a": -0.5e1}\t\r
                """);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("z", "text");
        expected.put("a", new BigDecimal("-0.5e1"));
        expected.put("m", Arrays.asList(Boolean.TRUE, Boolean.FALSE, null, Map.of(), List.of()));

        claim("the object is read as a map of its names in the order first written, a string as a string, a number"
                        + " as a decimal, true, false and null as themselves, an empty object and an empty array as"
                        + " empty, and the name given twice holding its last value",
                () -> assertThat(read).isInstanceOf(Map.class)
                        .satisfies(map -> assertThat(List.<Object>copyOf(((Map<?, ?>) map).entrySet()))
                                .containsExactlyElementsOf(List.<Object>copyOf(expected.entrySet()))));
    }

    @Test
    @Story("The control panel reads what its services answer")
    @DisplayName("Answers like the document converter's and the model server's are read to the entry the control panel looks for")
    void theTwoAnswersOfTheSidecarsAreReadToTheEntriesLookedFor() {
        Object version = JsonReader.read("{\"docling-serve\":\"1.0.0\",\"vespera-image\":\"vespera/docling:v1\"}");
        Object tags = JsonReader.read("""
                {"models":[{"name":"bge-m3:latest","size":1157672605},{"name":"qwen3:8b","details":{"family":"qwen3"}}]}
                """);

        claim("the converter's image is the string entry vespera-image of the object answered",
                () -> assertThat(((Map<?, ?>) version).get("vespera-image")).isEqualTo("vespera/docling:v1"));
        claim("and the models served are the string name of each object of the array models, in its order",
                () -> assertThat(((List<?>) ((Map<?, ?>) tags).get("models")).stream()
                                .<Object>map(model -> ((Map<?, ?>) model).get("name")).toList())
                        .containsExactly("bge-m3:latest", "qwen3:8b"));
    }

    @Test
    @Story("The control panel reads what its services answer")
    @DisplayName("Every escape a string may hold is read to the character it stands for, a pair of escapes to one character beyond the first plane")
    void everyEscapeOfAStringIsRead() {
        Object read = JsonReader.read("\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\u00e9 \\uD83D\\uDE00 ł\"");

        claim("each escape is the character it stands for, the two escapes of a surrogate pair one character, and a"
                        + " character written as itself is kept",
                () -> assertThat(read).isEqualTo("\" \\ / \b \f \n \r \t é \uD83D\uDE00 ł"));
    }

    @Test
    @Story("The control panel reads what its services answer")
    @DisplayName("Every form of number the grammar allows is read exactly, as written")
    void everyFormOfNumberIsReadExactly() {
        List<String> numbers = List.of("0", "-0", "7", "-12", "3.25", "1e3", "1E+3", "2.5e-3", "12345678901234567890.5");

        claim("each is read as the decimal it writes, digit for digit and to its scale",
                () -> assertThat(numbers.stream().map(JsonReader::read).toList())
                        .containsExactlyElementsOf(numbers.stream().map(BigDecimal::new).toList()));
    }

    @Test
    @Story("The control panel reads what its services answer")
    @DisplayName("Text that is not one value of the grammar is refused, naming where it was found")
    void textThatIsNotOneJsonValueIsRefusedNamingThePosition() {
        Map<String, Integer> refusedAt = new LinkedHashMap<>();
        refusedAt.put("", 0);
        refusedAt.put("   ", 3);
        refusedAt.put("{} {}", 3);
        refusedAt.put("{\"a\" 1}", 5);
        refusedAt.put("{\"a\":1,}", 7);
        refusedAt.put("{a:1}", 1);
        refusedAt.put("[1,]", 3);
        refusedAt.put("[1 2]", 3);
        refusedAt.put("\"not closed", 11);
        refusedAt.put("\"a\tb\"", 3);
        refusedAt.put("\"\\x\"", 3);
        refusedAt.put("\"\\u12g4\"", 3);
        refusedAt.put("01", 1);
        refusedAt.put("+1", 0);
        refusedAt.put(".5", 0);
        refusedAt.put("1.", 2);
        refusedAt.put("1e", 2);
        refusedAt.put("-", 1);
        refusedAt.put("nul", 0);
        refusedAt.put("True", 0);
        refusedAt.put("NaN", 0);
        refusedAt.put("'a'", 0);
        refusedAt.put("\f1", 0);

        List<String> notRefused = new ArrayList<>();
        Map<String, String> messages = new LinkedHashMap<>();
        refusedAt.forEach((text, position) -> {
            try {
                JsonReader.read(text);
                notRefused.add(text);
            } catch (IllegalArgumentException refused) {
                messages.put(text, refused.getMessage());
            }
        });

        claim("each of the " + refusedAt.size() + " texts that are not one JSON value is refused",
                () -> assertThat(notRefused).isEmpty());
        claim("and each refusal names the character it was found at",
                () -> refusedAt.forEach((text, position) -> assertThat(messages.get(text)).as(text)
                        .startsWith("not JSON at character " + position + ": ")));
    }

    @Test
    @Story("The control panel reads what its services answer")
    @DisplayName("Arrays nested deeper than 256 are refused rather than exhausting the stack, and 256 deep are read")
    void nestingDeeperThanTheBoundIsRefused() {
        String deepest = "[".repeat(DEEPEST) + "]".repeat(DEEPEST);
        String deeper = "[".repeat(DEEPEST + 1) + "]".repeat(DEEPEST + 1);

        claim("arrays nested " + DEEPEST + " deep are read",
                () -> assertThat(JsonReader.read(deepest)).isInstanceOf(List.class));
        claim("and one more level is refused, naming the bound",
                () -> assertThatThrownBy(() -> JsonReader.read(deeper))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageEndingWith("nesting deeper than " + DEEPEST));
    }
}
