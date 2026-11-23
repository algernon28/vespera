package io.algernon.vespera.engine.pipeline;

/**
 * The GPU image is refused because the machine's graphics card is not NVIDIA's, none was found, or the system is macOS,
 * where Docker reaches no card (ADR-444 §3). The line says the containers run on NVIDIA cards alone and names the CPU
 * image.
 */
final class NoNvidiaCardException extends ContainerStartRefusedException {

    /** What the line begins with, which the control panel tells it by (ADR-444 §10). */
    static final String BEGINNING = "The graphics card cannot be used: ";

    NoNvidiaCardException(String cards, String cpuImage) {
        super(BEGINNING + cards + ", and Vespera runs the containers on NVIDIA cards alone. Set VESPERA_DOCLING_IMAGE to "
                + cpuImage + ", which converts on the processor and starts new runs from extraction on, and run the same"
                + " command again.");
    }
}
