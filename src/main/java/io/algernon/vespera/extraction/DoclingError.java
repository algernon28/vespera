package io.algernon.vespera.extraction;

/**
 * One entry of Docling's {@code errors[]} (ADR-070): structured detail about a failure, one item per
 * error rather than a single message, so document-scoped and task-scoped failures inside the same
 * response stay distinguishable by {@link #category()}.
 *
 * @param componentType the Docling component that raised the error (Docling's own {@code
 *     DoclingComponentType})
 * @param moduleName the module inside that component
 * @param errorMessage a human-readable description — free text, never parsed for meaning
 * @param category the failure's scope, per {@link FailureCategory}
 * @param pageNo the 1-indexed page the error is attributable to, or {@code null} for a
 *     document-scoped error
 */
public record DoclingError(
        String componentType, String moduleName, String errorMessage, FailureCategory category, Integer pageNo) {}
