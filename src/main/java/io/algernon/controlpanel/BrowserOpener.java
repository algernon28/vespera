package io.algernon.controlpanel;

import java.io.IOException;
import java.net.URI;

/**
 * What opens the control panel's own address in the operator's browser (ADR-325 §7). The start asks it once, after the
 * address is printed; whatever it throws is written as one line and stops nothing.
 */
interface BrowserOpener {

    /** Opens {@code address} in the browser, without waiting for it. */
    void open(URI address) throws IOException;
}
