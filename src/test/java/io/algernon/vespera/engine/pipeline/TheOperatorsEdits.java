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
 * by the name it carries, the last part of a dotted key, and each edit claims that exactly one line that is not a
 * comment carries it, so a file whose shape moved fails here and not in a claim about a difference.
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

    /** {@code text} with the one line naming the key's last part set to {@code value}, its indentation kept. */
    static String setting(String text, String key, String value) {
        String name = lastPartOf(key);
        List<String> lines = new ArrayList<>();
        for (String line : theOneLineNaming(text, name)) {
            lines.add(line.strip().startsWith(name + ":") ? line.substring(0, line.indexOf(name)) + name + ": " + value : line);
        }
        return String.join("\n", lines);
    }

    /** {@code text} without the one line naming the key's last part. */
    static String without(String text, String key) {
        String name = lastPartOf(key);
        List<String> lines = new ArrayList<>();
        for (String line : theOneLineNaming(text, name)) {
            if (!line.strip().startsWith(name + ":")) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    /** The lines of {@code text}, once one line that is not a comment is claimed to carry {@code name}. */
    private static List<String> theOneLineNaming(String text, String name) {
        List<String> lines = List.of(text.split("\n", -1));
        long naming = lines.stream().filter(line -> line.strip().startsWith(name + ":")).count();
        claim("the file has one line naming " + name + ", the one the operator edits",
                () -> assertThat(naming).as("lines naming " + name).isEqualTo(1L));
        return lines;
    }
}
