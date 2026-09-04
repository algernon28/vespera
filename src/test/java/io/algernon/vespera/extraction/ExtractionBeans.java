package io.algernon.vespera.extraction;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@code extraction}'s bean graph, for a narrow context built by an explicit {@code @Import} list
 * rather than by component scanning.
 *
 * <p>This exists because {@link ExtractionCache} is package-private — deliberately, since nothing
 * outside {@code extraction} may name it — so a test in another package cannot put it in its own
 * {@code @Import} list even though the context it is building needs the bean. Naming the three beans
 * here, from inside the package that can see them, is the one place that list has to live.
 *
 * <p>{@link ExtractionSchema} is deliberately not among them: it checks this module's schema version
 * at construction (ADR-059), which is a separate question from whether extraction's beans can be
 * wired, and none of the narrow contexts that need this class registers any other module's version
 * guard either.
 */
@Configuration
@Import({DoclingClient.class, DoclingExtractor.class, ExtractionCache.class})
public class ExtractionBeans {}
