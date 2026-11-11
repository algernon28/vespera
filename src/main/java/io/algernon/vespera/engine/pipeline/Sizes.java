package io.algernon.vespera.engine.pipeline;

import java.util.List;
import java.util.Locale;

/**
 * A size of memory, in {@code B}, {@code KB}, {@code MB}, {@code GB} and {@code TB}, each 1,024 of the one before
 * (ADR-371 §1): the one rule the sampler and the report's page write every size, unit and divisor by. A count is never
 * written here (ADR-371 §2).
 */
final class Sizes {

    /** The units of a size, smallest first. */
    static final List<String> UNITS = List.of("B", "KB", "MB", "GB", "TB");

    private static final double BASE = 1024;

    private Sizes() {
    }

    /** What a value in {@code unit} is multiplied by to give bytes. */
    static double divisor(int unit) {
        return Math.pow(BASE, unit);
    }

    /**
     * The largest unit whose value of {@code bytes} is at least 1, moved up to the next where it would read 1,024.00 at
     * two decimals.
     */
    static int unitOf(double bytes) {
        double magnitude = Math.abs(bytes);
        int unit = 0;
        while (unit < UNITS.size() - 1 && magnitude / divisor(unit + 1) >= 1) {
            unit++;
        }
        if (unit > 0 && unit < UNITS.size() - 1 && Math.round(magnitude / divisor(unit) * 100) >= 102_400) {
            unit++;
        }
        return unit;
    }

    /** The unit a value axis spanning {@code bytes}, margins included, is written in: the largest in which it is at least 1. */
    static int unitOfSpan(double bytes) {
        int unit = 0;
        while (unit < UNITS.size() - 1 && bytes / divisor(unit + 1) >= 1) {
            unit++;
        }
        return unit;
    }

    /** The unit {@code name} names, or {@code B} where it names none. */
    static int unitNamed(String name) {
        return Math.max(0, UNITS.indexOf(name));
    }

    /** {@code bytes} written in its unit: whole bytes in {@code B}, two decimals in any other, grouped by thousands. */
    static String size(long bytes) {
        int unit = unitOf(bytes);
        if (unit == 0) {
            return String.format(Locale.ROOT, "%,d", bytes) + " B";
        }
        return String.format(Locale.ROOT, "%,.2f", bytes / divisor(unit)) + " " + UNITS.get(unit);
    }
}
