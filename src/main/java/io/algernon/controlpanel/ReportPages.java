package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * The fifteen reports re-served read-only (ADR-302 §3): each at one fixed path, {@code /report/} and its file name,
 * answering with the file of that name in the working directory held, as written, under a second policy,
 * {@code sandbox allow-scripts}. No part of a request names a file, and a name that is a link is not followed. The
 * handlers change nothing (ADR-302 §1).
 */
final class ReportPages {

    private static final String SANDBOX = "sandbox allow-scripts";

    private final PanelSession session;

    ReportPages(PanelSession session) {
        this.session = session;
    }

    void register(Map<String, HttpHandler> pages) {
        for (String report : ControlPanelPages.REPORTS) {
            pages.put("/report/" + report, exchange -> serve(exchange, report));
        }
    }

    private void serve(HttpExchange exchange, String report) throws IOException {
        if (!Exchanges.isRead(exchange)) {
            Exchanges.methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        Optional<Path> file = session.workingDirectory().map(folder -> folder.resolve(report))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
        if (file.isEmpty()) {
            Exchanges.notFound(exchange, "the working directory holds no " + report);
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.get());
        } catch (IOException unreadable) {
            Exchanges.notFound(exchange, "the working directory holds no " + report);
            return;
        }
        exchange.getResponseHeaders().add("Content-Security-Policy", SANDBOX);
        Exchanges.send(exchange, 200, "text/html; charset=utf-8", bytes);
    }
}
