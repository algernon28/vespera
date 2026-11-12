package io.algernon.vespera.engine.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import java.nio.file.Path;

/**
 * An appender of the invocation's output file (ADR-364 section 2): it encodes an event with the encoder and filters
 * {@code logback-spring.xml} gives it and hands the bytes to the context's one {@link InvocationOutput}, whose lock is
 * the only one on the way to the file. It takes no lock of its own.
 */
public final class InvocationOutputAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private Encoder<ILoggingEvent> encoder;
    private String folder;
    private InvocationOutput sink;

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    /** The folder the file is written in: the working directory's {@code logs}, the folder {@code vespera.log} is in. */
    public void setFolder(String folder) {
        this.folder = folder;
    }

    @Override
    public void start() {
        if (encoder == null || folder == null || folder.isBlank()) {
            addError("an output file's appender needs an encoder and a folder");
            return;
        }
        sink = InvocationOutput.in(getContext(), Path.of(folder));
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        sink.write(encoder.encode(event));
    }
}
