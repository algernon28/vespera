package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.net.URI;

/** Opens the progress page for a person, once an invocation has written it (ADR-266 section 2). Holds no rule. */
interface PageOpener {

    void open(URI page) throws IOException;
}
