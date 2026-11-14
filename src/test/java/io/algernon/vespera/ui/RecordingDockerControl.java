package io.algernon.vespera.ui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * The window's {@link DockerControl} as a test answers it (ADR-389 §4, §5): no Docker command is run. Each call is kept
 * in order, with the launch values it was given, where it takes them, and whether the window's own child ran as it was
 * made, and is answered with what the test set for its kind.
 */
final class RecordingDockerControl implements DockerControl {

    /** One call: {@code start}, {@code shut-down} or {@code stop-sidecars}, its values, and whether a child ran then. */
    record Call(String kind, Optional<LaunchValues> values, boolean childRan) {
    }

    /** Every call, in order. */
    final List<Call> calls = new CopyOnWriteArrayList<>();

    private final Said startSays;
    private final Said shutDownSays;
    private volatile BooleanSupplier childRuns = () -> false;

    /** Answers a start with {@code startSays}, a shut-down with {@code shutDownSays}, and a stop of the sidecars as done. */
    RecordingDockerControl(Said startSays, Said shutDownSays) {
        this.startSays = startSays;
        this.shutDownSays = shutDownSays;
    }

    /** Reads whether a child of the window runs from {@code state}, at each call. */
    RecordingDockerControl watching(ControlPanelState state) {
        childRuns = state::childRuns;
        return this;
    }

    /** The calls of {@code kind}, in order. */
    List<Call> of(String kind) {
        return calls.stream().filter(call -> call.kind().equals(kind)).toList();
    }

    @Override
    public Said start(LaunchValues values) {
        calls.add(new Call("start", Optional.of(values), childRuns.getAsBoolean()));
        return startSays;
    }

    @Override
    public Said shutDown() {
        calls.add(new Call("shut-down", Optional.empty(), childRuns.getAsBoolean()));
        return shutDownSays;
    }

    @Override
    public Said stopTheSidecars(LaunchValues values) {
        calls.add(new Call("stop-sidecars", Optional.of(values), childRuns.getAsBoolean()));
        return new Said(true, "");
    }
}
