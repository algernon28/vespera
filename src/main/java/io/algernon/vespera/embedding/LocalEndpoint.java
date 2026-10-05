package io.algernon.vespera.embedding;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a service URL names this machine (ADR-197 §6).
 *
 * <p>Only the loopback names count. A host on the operator's own network is another machine and is
 * refused, on purpose: what leaves this machine is decided by a name, not by whose network it is.
 */
public final class LocalEndpoint {

    private static final Set<String> THIS_MACHINE = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private LocalEndpoint() {}

    /** True when the URL's host is {@code localhost}, {@code 127.0.0.1} or {@code ::1}. */
    public static boolean isLocal(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(baseUrl.strip()).getHost();
            return host != null && THIS_MACHINE.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException notAUrl) {
            return false;
        }
    }
}
