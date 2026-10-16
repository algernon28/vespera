package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The opener every test context holds in place of the system's (ADR-266 section 2): it opens nothing, and records
 * each page it was asked to open with what was at that path at the moment it was asked. A component under the
 * application's package, so every whole application a test builds scans it, and primary, so it wins there over the
 * shipped opener; the cascade slice imports it by name. A test may have it throw, to see that a failing opener fails
 * nothing.
 */
@Component
@Primary
class RecordingPageOpener implements PageOpener {

    /** One request: the URI handed over, whether a page was there then, and its text if it was. */
    record Request(URI page, boolean thePageWasThere, String text) {}

    private final List<Request> requests = new CopyOnWriteArrayList<>();

    private volatile RuntimeException toThrow;

    @Override
    public void open(URI page) throws IOException {
        Path path = Path.of(page);
        boolean there = Files.isRegularFile(path);
        requests.add(new Request(page, there, there ? Files.readString(path, StandardCharsets.UTF_8) : ""));
        RuntimeException failure = toThrow;
        if (failure != null) {
            throw failure;
        }
    }

    /** Every request since the last {@link #forget}, in the order they were made. */
    List<Request> requests() {
        return List.copyOf(requests);
    }

    /** From now on, each request records itself and then throws {@code failure}. */
    void throwing(RuntimeException failure) {
        toThrow = failure;
    }

    /** No request recorded and nothing thrown: what each test starts from, since the bean outlives it. */
    void forget() {
        requests.clear();
        toThrow = null;
    }
}
