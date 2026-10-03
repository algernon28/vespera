package io.algernon.vespera.extraction;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How one Docling response reads by scope (ADR-070, ADR-071, ADR-143, ADR-183 section 1): whether it
 * is a conversion, a failure the converter blamed on the document, a timeout the converter reported,
 * or a failure the converter blamed on itself. The one classification of a response there is: the
 * extraction cache decides by it what to keep, and stage 2 decides by it what an occurrence earns.
 *
 * <p>The reading looks at the response alone. It does not know whether a reported timeout is a
 * property of the file or of the pass: that is the streak's to decide (ADR-071), and a fact about the
 * pass, so it is not in the response.
 *
 * <p>The precedence, in order:
 *
 * <ol>
 *   <li>{@code success} and {@code partial_success} are a {@link Conversion}, whatever errors they
 *       carry (ADR-070's pass-through).
 *   <li>Any other status with a {@code timeout} among its errors is a {@link ReportedTimeout}.
 *   <li>Otherwise {@code backend_failure} or {@code inference_failure} anywhere in the errors is a
 *       {@link DocumentScope} failure: under this client's call shape (one uploaded file per call) it
 *       can only be a property of the uploaded document.
 *   <li>Otherwise {@code capacity}, {@code target_unavailable} or {@code internal} anywhere in the
 *       errors is a {@link ServiceScope} failure, overriding a co-occurring {@code policy} or {@code
 *       source_unavailable}: evidence that the whole response is about the converter's own state.
 *   <li>Otherwise it is a {@link DocumentScope} failure again: {@code policy}, {@code
 *       source_unavailable}, {@code unknown} or no error at all, with nothing from the previous step
 *       beside them (ADR-143).
 * </ol>
 */
public sealed interface ResponseScope {

    /** Reads {@code response} by scope. */
    static ResponseScope of(DoclingResponse response) {
        if (response.status() == ConversionStatus.SUCCESS || response.status() == ConversionStatus.PARTIAL_SUCCESS) {
            return new Conversion();
        }
        List<DoclingError> errors = response.errors();

        Optional<DoclingError> reportedTimeout =
                errors.stream().filter(error -> error.category() == FailureCategory.TIMEOUT).findFirst();
        if (reportedTimeout.isPresent()) {
            return new ReportedTimeout(reportedTimeout.get());
        }

        Optional<DoclingError> unconditional = errors.stream()
                .filter(error -> isUnconditionalDocumentScope(error.category()))
                .findFirst();
        if (unconditional.isPresent()) {
            return new DocumentScope(unconditional);
        }

        Optional<DoclingError> serviceScoped =
                errors.stream().filter(error -> isServiceScope(error.category())).findFirst();
        if (serviceScoped.isPresent()) {
            // The error that is actually evidence of service scope, not an overridden entry beside it.
            return new ServiceScope(serviceScoped.get());
        }

        // Every category left is conditional, and nothing overrides it. A named refusal is the better
        // reason than an unexplained error beside it, so it is preferred.
        return new DocumentScope(errors.stream()
                .filter(error -> isConditionalDocumentScope(error.category()))
                .findFirst()
                .or(() -> errors.stream().findFirst()));
    }

    /**
     * Whether the extraction cache keeps a response read this way: a conversion, and a failure the
     * converter blamed on the document. Never a reported timeout and never a failure the converter
     * blamed on itself (ADR-183 section 1).
     */
    boolean keptInCache();

    /**
     * The category that decides this reading, as Docling spells it on the wire, or {@code null} for a
     * reading no error decides: a conversion, and a failure reporting no error at all.
     */
    default String category() {
        return null;
    }

    /** {@code success} or {@code partial_success}. */
    record Conversion() implements ResponseScope {
        @Override
        public boolean keptInCache() {
            return true;
        }
    }

    /**
     * A failure the converter blamed on the document.
     *
     * @param blamed the error that gives the reading its reason: a {@code backend_failure} or {@code
     *     inference_failure} where there is one, else a named {@code policy} or {@code
     *     source_unavailable}, else the first error, else empty for a failure reporting no error
     */
    record DocumentScope(Optional<DoclingError> blamed) implements ResponseScope {
        @Override
        public boolean keptInCache() {
            return true;
        }

        @Override
        public String category() {
            return blamed.map(error -> wire(error.category())).orElse(null);
        }
    }

    /**
     * A failure whose errors carry a Docling-reported {@code timeout}.
     *
     * @param error the first such error
     */
    record ReportedTimeout(DoclingError error) implements ResponseScope {
        @Override
        public boolean keptInCache() {
            return false;
        }

        @Override
        public String category() {
            return wire(error.category());
        }
    }

    /**
     * A failure the converter blamed on itself.
     *
     * @param error the first {@code capacity}, {@code target_unavailable} or {@code internal} error
     */
    record ServiceScope(DoclingError error) implements ResponseScope {
        @Override
        public boolean keptInCache() {
            return false;
        }

        @Override
        public String category() {
            return wire(error.category());
        }
    }

    private static String wire(FailureCategory category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isUnconditionalDocumentScope(FailureCategory category) {
        return switch (category) {
            case BACKEND_FAILURE, INFERENCE_FAILURE -> true;
            case POLICY, SOURCE_UNAVAILABLE, CAPACITY, TARGET_UNAVAILABLE, INTERNAL, UNKNOWN, TIMEOUT -> false;
        };
    }

    private static boolean isConditionalDocumentScope(FailureCategory category) {
        return switch (category) {
            case POLICY, SOURCE_UNAVAILABLE -> true;
            case BACKEND_FAILURE, INFERENCE_FAILURE, CAPACITY, TARGET_UNAVAILABLE, INTERNAL, UNKNOWN, TIMEOUT -> false;
        };
    }

    private static boolean isServiceScope(FailureCategory category) {
        return switch (category) {
            case CAPACITY, TARGET_UNAVAILABLE, INTERNAL -> true;
            case POLICY, SOURCE_UNAVAILABLE, BACKEND_FAILURE, INFERENCE_FAILURE, UNKNOWN, TIMEOUT -> false;
        };
    }
}
