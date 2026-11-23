package io.algernon.vespera.engine.pipeline;

/**
 * The Docker engine answers but lists no {@code nvidia} runtime, where an NVIDIA card is there or none could be read, so
 * the GPU image cannot start (ADR-445 §2). The line names the NVIDIA Container Toolkit and the CPU image.
 */
final class NvidiaRuntimeIsMissingException extends ContainerStartRefusedException {

    /** What the line begins with, which the control panel tells it by (ADR-445 §10). */
    static final String BEGINNING = "The graphics card cannot be used: the Docker engine ";

    NvidiaRuntimeIsMissingException(String where, String cpuImage) {
        super(BEGINNING + where + " answers but has no nvidia runtime, which the NVIDIA Container Toolkit gives it."
                + " Install the toolkit, which Start Docker in the control panel offers to do, or as NVIDIA's guide at"
                + " https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html has it;"
                + " or set VESPERA_DOCLING_IMAGE to " + cpuImage + ", which converts on the processor and starts new"
                + " runs from extraction on; and run the same command again.");
    }
}
