package io.algernon.vespera.confluence;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where a publish sends and as whom (ADR-308 §2, §3): the site's {@code https} address, the space's key, the parent by
 * its id or its title, and the account's email and API token.
 *
 * <p><b>The token and the email are read from the environment alone</b>, by {@link #TOKEN_VARIABLE} and {@link
 * #USER_VARIABLE}; the other three are properties, which Spring also sets from the environment. Nothing here writes
 * either anywhere, and {@link #toString()} names neither: the token leaves this class only as the {@code
 * Authorization} header's value.
 */
final class ConfluenceSite {

    /** The environment variable the API token is read from, and nothing else (ADR-308 §3). */
    static final String TOKEN_VARIABLE = "VESPERA_CONFLUENCE_TOKEN";

    /** The environment variable the account's email is read from, and nothing else (ADR-308 §3). */
    static final String USER_VARIABLE = "VESPERA_CONFLUENCE_USER";

    /** The property naming the site's address, set by {@code VESPERA_CONFLUENCE_BASE_URL}. */
    static final String BASE_URL_PROPERTY = "vespera.confluence.base-url";

    /** The property naming the space's key, set by {@code VESPERA_CONFLUENCE_SPACE_KEY}. */
    static final String SPACE_KEY_PROPERTY = "vespera.confluence.space-key";

    /** The property naming the parent by its id, set by {@code VESPERA_CONFLUENCE_PARENT_ID}. */
    static final String PARENT_ID_PROPERTY = "vespera.confluence.parent-id";

    /** The property naming the parent by its title, set by {@code VESPERA_CONFLUENCE_PARENT_TITLE}. */
    static final String PARENT_TITLE_PROPERTY = "vespera.confluence.parent-title";

    private final URI site;
    private final String spaceKey;
    private final String parentId;
    private final String parentTitle;
    private final String authorization;

    private ConfluenceSite(URI site, String spaceKey, String parentId, String parentTitle, String authorization) {
        this.site = site;
        this.spaceKey = spaceKey;
        this.parentId = parentId;
        this.parentTitle = parentTitle;
        this.authorization = authorization;
    }

    /** What the publish was given, or why it refuses before anything is sent. */
    sealed interface Read permits Read.Given, Read.Refused {
        record Given(ConfluenceSite site) implements Read {}

        record Refused(String line) implements Read {}
    }

    /**
     * The site the five values name, or the line refusing them (ADR-308 §2, §3): every one missing is named by its
     * variable, never by its value; a parent named both by id and by title is refused; and an address that is not
     * {@code https}, carries a user, a query or a fragment, or a path beyond {@code /wiki}, is refused.
     */
    static Read of(
            Function<String, String> environment,
            String baseUrl,
            String spaceKey,
            String parentId,
            String parentTitle) {
        String token = blankToNull(environment.apply(TOKEN_VARIABLE));
        String user = blankToNull(environment.apply(USER_VARIABLE));
        List<String> missing = new ArrayList<>();
        if (blankToNull(baseUrl) == null) {
            missing.add("VESPERA_CONFLUENCE_BASE_URL");
        }
        if (blankToNull(spaceKey) == null) {
            missing.add("VESPERA_CONFLUENCE_SPACE_KEY");
        }
        if (blankToNull(parentId) == null && blankToNull(parentTitle) == null) {
            missing.add("VESPERA_CONFLUENCE_PARENT_ID or VESPERA_CONFLUENCE_PARENT_TITLE");
        }
        if (user == null) {
            missing.add(USER_VARIABLE);
        }
        if (token == null) {
            missing.add(TOKEN_VARIABLE);
        }
        if (!missing.isEmpty()) {
            return new Read.Refused("vespera publish sent nothing: " + String.join(", ", missing)
                    + (missing.size() == 1 ? " is" : " are") + " not set. Set the site, the space and the parent in"
                    + " confluence-env.sh or confluence-env.ps1 in the working directory, run it in this shell, and"
                    + " publish again.");
        }
        if (blankToNull(parentId) != null && blankToNull(parentTitle) != null) {
            return new Read.Refused("vespera publish sent nothing: the parent is named both by"
                    + " VESPERA_CONFLUENCE_PARENT_ID and by VESPERA_CONFLUENCE_PARENT_TITLE. Name it by one.");
        }
        Optional<URI> site = siteOf(baseUrl.strip());
        if (site.isEmpty()) {
            return new Read.Refused("vespera publish sent nothing: VESPERA_CONFLUENCE_BASE_URL is not the https"
                    + " address of a Confluence Cloud site, https://<site> or https://<site>/wiki, with no user,"
                    + " query or other path.");
        }
        String basic = Base64.getEncoder().encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
        return new Read.Given(new ConfluenceSite(
                site.get(), spaceKey.strip(), blankToNull(parentId) == null ? null : parentId.strip(),
                blankToNull(parentTitle) == null ? null : parentTitle.strip(), "Basic " + basic));
    }

    /** {@code https://<host>[:port]}, the only origin requests go to, or empty where the address is refused. */
    private static Optional<URI> siteOf(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException misspelt) {
            return Optional.empty();
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !(path.isEmpty() || path.equals("/") || path.equals("/wiki") || path.equals("/wiki/"))) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), null,
                    null, null));
        } catch (URISyntaxException unreachable) {
            return Optional.empty();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** {@code https://<host>[:port]}, with no path. */
    URI site() {
        return site;
    }

    String spaceKey() {
        return spaceKey;
    }

    /** The parent's id where it is named by id, otherwise {@code null}. */
    String parentId() {
        return parentId;
    }

    /** The parent's title where it is named by title, otherwise {@code null}. */
    String parentTitle() {
        return parentTitle;
    }

    /** The {@code Authorization} header's value: Basic, of the email and the token. */
    String authorization() {
        return authorization;
    }

    /** The site and the space, never the account or the token. */
    @Override
    public String toString() {
        return site + " space " + spaceKey;
    }
}
