package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.TheShippedCopy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/**
 * Edits an operator makes by hand to a YAML file beside the jar, made to its text line by line so that every other
 * line, its comments, its quoting and its numbers as written, stays as the jar shipped it (ADR-397 §5). A line is found
 * by the dotted key its indentation spells, and each edit claims that exactly one line that is not a comment carries
 * it, so a file whose shape moved fails here and not in a claim about a difference.
 */
final class TheOperatorsEdits {

    private TheOperatorsEdits() {
    }

    /** The text of the shipped copy of the file {@code name} beside the jar, claimed to be carried. */
    static String shipped(String name) throws IOException {
        ClassPathResource copy = TheShippedCopy.of(name);
        claim("the jar carries its own copy of " + name + " where Spring's configuration loading never reads",
                () -> assertThat(copy.exists()).as(copy.getPath() + " on the class path").isTrue());
        try (InputStream in = copy.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The last part of a dotted key: the name its line carries in the YAML file. */
    static String lastPartOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    /** {@code text} with the one line naming the key set to {@code value}, its indentation kept. */
    static String setting(String text, String key, String value) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        int at = theOneLineNaming(lines, key);
        String line = lines.get(at);
        String name = lastPartOf(key);
        lines.set(at, line.substring(0, line.indexOf(name)) + name + ": " + value);
        return String.join("\n", lines);
    }

    /** {@code text} without the one line naming the key. */
    static String without(String text, String key) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        lines.remove(theOneLineNaming(lines, key));
        return String.join("\n", lines);
    }

    /** The index of the one line that is not a comment whose dotted key, spelt by indentation, is {@code key}. */
    private static int theOneLineNaming(List<String> lines, String key) {
        List<Integer> naming = new ArrayList<>();
        List<String> path = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("-") || !stripped.contains(":")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            while (!indents.isEmpty() && indents.getLast() >= indent) {
                indents.removeLast();
                path.removeLast();
            }
            path.add(stripped.substring(0, stripped.indexOf(':')).strip());
            indents.add(indent);
            if (String.join(".", path).equals(key)) {
                naming.add(i);
            }
        }
        claim("the file has one line naming " + key + ", the one the operator edits",
                () -> assertThat(naming).as("lines naming " + key).hasSize(1));
        return naming.getFirst();
    }
}
