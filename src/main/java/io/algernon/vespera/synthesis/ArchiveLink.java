package io.algernon.vespera.synthesis;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a membership entry links to in the archive (ADR-135, ADR-137): a destination relative to the
 * page that carries it, percent-encoded, or none.
 *
 * <p>The destination is the fifth surrounding a value lands in, and the one that answers to a resolver
 * rather than a reader, so its rule is percent-encoding and not a row of {@link MarkdownSurroundings}
 * (ADR-137 §4). Nothing is stat-ed to build it: a link gone dead because the archive moved is the
 * operator's to re-point, not this writer's to hide (ADR-104).
 */
final class ArchiveLink {

    private ArchiveLink() {}

    /**
     * The destination of the document at {@code relativePath} beneath {@code corpusRoot}, written from
     * a page in {@code pageDirectory}, or empty where no relative route between the two exists (ADR-135):
     * the root this machine's path parser refuses, a relative directory, or another Windows volume. The
     * entry then states the path and links nowhere, because a {@code file:} destination is measured to
     * be a link in only two of seven renderer configurations.
     */
    static Optional<String> from(Path pageDirectory, String corpusRoot, String relativePath) {
        return asDirectory(corpusRoot)
                .filter(root -> hasARelativeRoute(pageDirectory, root))
                .map(root -> relativeDestination(pageDirectory, root, relativePath));
    }

    /**
     * {@code corpusRoot} as a {@link Path}, or empty where this machine's parser refuses it (ADR-135):
     * a recorded root is a directory this tool composed or canonicalised on some machine, but not
     * necessarily this one, and a root carrying a character this machine's path parser refuses — a
     * quote or a NUL, for instance — is one this writer declines to link through rather than fail the
     * whole invocation over (ADR-111). A UNC share or a root written for a different platform parses
     * fine here and is refused earlier, by {@link #hasARelativeRoute}.
     */
    private static Optional<Path> asDirectory(String corpusRoot) {
        try {
            return Optional.of(Path.of(corpusRoot));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether a path from {@code pageDirectory} to {@code corpusRootDirectory} can be composed at all
     * (ADR-135): both absolute, and rooted the same — a Windows drive letter or a UNC prefix a relative
     * path can never cross. A predicate over the two directories rather than a caught exception, because
     * {@link Path#relativize} throws {@link IllegalArgumentException} for the cases this refuses, and a
     * throw escaping {@link Deliverable#writeTo} would roll back every fault row the invocation had
     * already recorded (ADR-111).
     */
    private static boolean hasARelativeRoute(Path pageDirectory, Path corpusRootDirectory) {
        return pageDirectory.isAbsolute()
                && corpusRootDirectory.isAbsolute()
                && Objects.equals(pageDirectory.getRoot(), corpusRootDirectory.getRoot());
    }

    /**
     * {@code relativePath} as it sits beneath {@code corpusRootDirectory}, as a destination relative to
     * {@code pageDirectory} (ADR-135): the two directories relativized, and the occurrence's
     * root-relative path appended to the result as text.
     *
     * <p><b>Never composed by resolving {@code relativePath} itself against a {@link Path}</b>, because
     * the JDK's Windows path parser refuses characters NTFS allows — a quote in a filename is legal on
     * this filesystem and a {@code Path.resolve} of one throws before any URI is built. Only the two
     * directories, which this tool composed or canonicalised, ever go through {@link Path}; the
     * document's own name is appended as text. Every character the URI grammar makes illegal in a
     * path is escaped, spaces included, so a name never opens the link at its first whitespace.
     * Parentheses are escaped on top of the URI's own quoting because a Markdown destination ends at
     * the first unescaped {@code )}. The ampersand joins them on the same ground: the URI grammar
     * permits it in a path, so it survives the URI's own quoting untouched, but a Markdown destination
     * decodes a named entity reference inside it, so a name such as {@code &copy;} would resolve to a
     * document the archive does not hold (ADR-137). Only the named form needs it — the numeric form,
     * {@code &#169;}, is already defused because {@code #} becomes {@code %23} for an unrelated reason.
     */
    private static String relativeDestination(Path pageDirectory, Path corpusRootDirectory, String relativePath) {
        String relativeDirectory =
                pageDirectory.relativize(corpusRootDirectory).toString().replace('\\', '/');
        String rawPath = relativeDirectory.isEmpty() ? relativePath : relativeDirectory + "/" + relativePath;
        try {
            return new URI(null, null, rawPath, null)
                    .toASCIIString()
                    .replace("(", "%28")
                    .replace(")", "%29")
                    .replace("&", "%26");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "could not compose a relative link for " + relativePath + " beneath " + corpusRootDirectory, e);
        }
    }
}
