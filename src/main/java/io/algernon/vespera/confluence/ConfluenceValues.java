package io.algernon.vespera.confluence;

import java.nio.file.Path;

/**
 * The five plain values of a publish that a working directory's profile names (ADR-359 §1, §5): the site's address, the
 * space's key, the parent by its id or by its title, and the account's email. Each is {@code null} where the profile
 * names none. The token is no component: it is read from the environment alone (ADR-308 §3).
 *
 * <p>{@code confluence} names no class of {@code profile}; {@code pipeline} hands these values over through {@link
 * InTheProfile}.
 */
public record ConfluenceValues(String baseUrl, String spaceKey, String folderId, String folderTitle, String email) {

    /** No value named: what a publish reads where no profile names any, so that it reads the shell alone. */
    public static final ConfluenceValues NONE = new ConfluenceValues(null, null, null, null, null);

    /** The values the profile of a working directory names (ADR-359 §5). */
    @FunctionalInterface
    public interface InTheProfile {

        /** The values {@code workingDirectory}'s profile names, {@link #NONE} where it names none or is not read. */
        ConfluenceValues of(Path workingDirectory);
    }
}
