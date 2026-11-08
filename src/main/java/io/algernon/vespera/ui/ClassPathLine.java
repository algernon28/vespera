package io.algernon.vespera.ui;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The filter that drops the one line JavaFX writes as its toolkit starts from the class path (ADR-360 §1, §2): a record
 * of the logger {@code javafx} whose message begins {@code Unsupported JavaFX configuration}, at whatever level. Every
 * other record, one with no message among them, passes. The words are matched as they stand, case and spaces included.
 *
 * <p>{@link #install()} sets an instance of this class as the filter of that logger, which the class holds in a static
 * field: the log manager holds a logger weakly, and would otherwise give JavaFX a new one without the filter. The
 * control panel's start calls it before {@code Application.launch}. This is the one class of the control panel that
 * names {@code java.logging} (ADR-360 §3).
 */
final class ClassPathLine implements Filter {

    /** The words JavaFX's line begins with. */
    private static final String OPENING = "Unsupported JavaFX configuration";

    /** The logger JavaFX writes the line on, held so that the log manager keeps the one that carries the filter. */
    private static final Logger JAVAFX_LOGGER = Logger.getLogger("javafx");

    ClassPathLine() {
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        String message = record.getMessage();
        return message == null || !message.startsWith(OPENING);
    }

    /** Sets a {@code ClassPathLine} as the filter of the logger named {@code javafx}; called again, it sets it again. */
    static void install() {
        JAVAFX_LOGGER.setFilter(new ClassPathLine());
    }
}
