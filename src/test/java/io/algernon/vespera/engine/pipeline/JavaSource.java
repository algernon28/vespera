package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A Java source told apart into its comments, one piece a line, its string and character literals and text blocks, and
 * the identifiers of the code between them, for the tests that hold which words our names and texts say (ADR-426,
 * ADR-433). A string's escapes are skipped over, so an escaped quote ends nothing.
 */
record JavaSource(List<Piece> comments, List<Piece> literals, List<Piece> identifiers) {

    /** The jar's code, as the tests run from the repository's root. */
    static final Path THE_JARS_CODE = Path.of("src/main/java/io/algernon/vespera");

    /** The tests' code. */
    static final Path THE_TESTS_CODE = Path.of("src/test/java/io/algernon/vespera");

    /** The two folders a module of the jar lies one folder below (ADR-367). */
    private static final Set<String> MODULE_PARENTS = Set.of("engine", "publish");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** A piece of a source and the line it starts on. */
    record Piece(String text, int line) {

        String where(Path file) {
            return file.toString().replace('\\', '/') + ":" + line;
        }
    }

    /** Every Java file under {@code root}, in the order of their paths. */
    static List<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The module a class of the jar lies in: the folder below {@code engine} or {@code publish}, or none. */
    static Optional<String> moduleOf(Path file) {
        Path relative = THE_JARS_CODE.relativize(file);
        if (relative.getNameCount() > 2 && MODULE_PARENTS.contains(relative.getName(0).toString())) {
            return Optional.of(relative.getName(1).toString());
        }
        return Optional.empty();
    }

    static JavaSource of(Path file) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Piece> comments = new ArrayList<>();
        List<Piece> literals = new ArrayList<>();
        List<Piece> identifiers = new ArrayList<>();
        StringBuilder code = new StringBuilder();
        int codeLine = 1;
        int line = 1;
        int i = 0;
        while (i < text.length()) {
            int end;
            if (text.startsWith("//", i)) {
                end = text.indexOf('\n', i);
                end = end < 0 ? text.length() : end;
                comments.add(new Piece(text.substring(i, end), line));
            } else if (text.startsWith("/*", i)) {
                end = text.indexOf("*/", i + 2);
                end = end < 0 ? text.length() : end + 2;
                int at = line;
                for (String commentLine : text.substring(i, end).split("\n", -1)) {
                    comments.add(new Piece(commentLine, at++));
                }
            } else if (text.startsWith("\"\"\"", i)) {
                end = closing(text, i + 3, "\"\"\"");
                literals.add(new Piece(text.substring(i, end), line));
            } else if (text.charAt(i) == '"' || text.charAt(i) == '\'') {
                end = closing(text, i + 1, String.valueOf(text.charAt(i)));
                literals.add(new Piece(text.substring(i, end), line));
            } else {
                if (code.isEmpty()) {
                    codeLine = line;
                }
                code.append(text.charAt(i));
                if (text.charAt(i) == '\n') {
                    line++;
                }
                i++;
                continue;
            }
            identifiersOf(code, codeLine, identifiers);
            code.setLength(0);
            line += (int) text.substring(i, end).chars().filter(c -> c == '\n').count();
            i = end;
        }
        identifiersOf(code, codeLine, identifiers);
        return new JavaSource(comments, literals, identifiers);
    }

    /** Where the literal opened just before {@code from} ends: after its closing {@code quote}, escapes skipped. */
    private static int closing(String text, int from, String quote) {
        int i = from;
        while (i < text.length()) {
            if (text.charAt(i) == '\\') {
                i += 2;
            } else if (text.startsWith(quote, i)) {
                return i + quote.length();
            } else {
                i++;
            }
        }
        return text.length();
    }

    private static void identifiersOf(CharSequence code, int firstLine, List<Piece> into) {
        Matcher matcher = IDENTIFIER.matcher(code);
        while (matcher.find()) {
            int line = firstLine + (int) code.subSequence(0, matcher.start()).chars().filter(c -> c == '\n').count();
            into.add(new Piece(matcher.group(), line));
        }
    }
}
