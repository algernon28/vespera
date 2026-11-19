package io.algernon.vespera.engine.pipeline;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Whether a container's base URL names this machine, worked out by resolving its host (ADR-430 §10, amending ADR-323
 * §3's step 2): it is this machine's where any address the host resolves to is a loopback or a wildcard address or one
 * of this machine's own; another machine's where none is; and not resolved where the host cannot be, a host under
 * {@code .invalid} among them, which is never looked up.
 */
final class ThisMachine {

    /** Where a base URL's host is. */
    enum Where {
        THIS_MACHINE,
        ANOTHER_MACHINE,
        NOT_RESOLVED
    }

    /** What resolves a name: {@link InetAddress#getAllByName} in the application, a test's own in a test. */
    @FunctionalInterface
    interface Resolver {
        InetAddress[] allByName(String host) throws UnknownHostException;
    }

    private final Resolver resolver;
    private final Supplier<List<InetAddress>> ownAddresses;

    ThisMachine(Resolver resolver, Supplier<List<InetAddress>> ownAddresses) {
        this.resolver = resolver;
        this.ownAddresses = ownAddresses;
    }

    /** The machine this JVM runs on: the system's resolver, and the addresses of its interfaces read at each call. */
    static ThisMachine system() {
        return new ThisMachine(InetAddress::getAllByName, ThisMachine::addressesOfTheInterfaces);
    }

    private static List<InetAddress> addressesOfTheInterfaces() {
        List<InetAddress> own = new ArrayList<>();
        try {
            NetworkInterface.networkInterfaces().forEach(each -> each.inetAddresses().forEach(own::add));
        } catch (SocketException unreadable) {
            // No interface can be read: only the loopback and wildcard addresses make a host this machine's.
        }
        return own;
    }

    Where where(URI baseUrl) {
        String host = baseUrl.getHost();
        if (host == null || host.isBlank()) {
            return Where.NOT_RESOLVED;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        String name = host.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.equals("invalid") || name.endsWith(".invalid")) {
            return Where.NOT_RESOLVED;
        }
        InetAddress[] resolved;
        try {
            resolved = isLiteral(host) ? new InetAddress[] {InetAddress.ofLiteral(host)} : resolver.allByName(host);
        } catch (UnknownHostException | IllegalArgumentException unresolved) {
            return Where.NOT_RESOLVED;
        }
        if (resolved == null || resolved.length == 0) {
            return Where.NOT_RESOLVED;
        }
        List<InetAddress> own = null;
        for (InetAddress address : resolved) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                return Where.THIS_MACHINE;
            }
            if (own == null) {
                own = ownAddresses.get();
            }
            if (own.contains(address)) {
                return Where.THIS_MACHINE;
            }
        }
        return Where.ANOTHER_MACHINE;
    }

    private static boolean isLiteral(String host) {
        try {
            InetAddress.ofLiteral(host);
            return true;
        } catch (IllegalArgumentException notLiteral) {
            return false;
        }
    }
}
