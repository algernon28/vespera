package io.algernon.vespera.pipeline;

/** No compose file the jar carries builds the converter image the configuration names (ADR-323 §2, §8). */
final class NoComposeFileBuildsTheImageException extends SidecarStartRefusedException {

    NoComposeFileBuildsTheImageException(String configured, String cpuImage, String gpuImage) {
        super("the sidecars are not answering and Vespera cannot start them for vespera.docling.image " + configured
                + ": compose.yaml builds " + cpuImage + " and compose.gpu.yaml builds " + gpuImage
                + ". Set VESPERA_DOCLING_IMAGE to one of the two, or unset it for the first, and run the same command"
                + " again.");
    }
}
