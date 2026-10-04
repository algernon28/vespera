package io.algernon.vespera.profile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every key the profile carries, read off {@link Profile}'s own record components, in the order the
 * record declares them (ADR-186).
 *
 * <p><b>The one list of profile keys a test may hold.</b> A test that needs "every key" asks here
 * rather than spelling the keys out, because a list spelled out by hand is the drift ADR-186 records:
 * {@code DeliverableInvocationTest} carried eight keys while the record carried ten, so the claim that
 * the index states every key passed with two missing. The record is the schema (ADR-061), so the
 * record is what every such claim is compared with.
 *
 * <p>{@code ProfileTest} keeps its own count of the constructor's parameters on purpose: that claim
 * is about the record's one way in (ADR-119), it already fails when a key is added, and deriving it
 * from the components would make it compare the record with itself.
 */
public final class ProfileKeys {

    private ProfileKeys() {
    }

    /** Every key, named as the operator names it in {@code profile.yaml}, in the record's order. */
    public static List<String> everyKey() {
        return Arrays.stream(Profile.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    /** Every key of {@code profile} with the value it holds, in the record's order. */
    public static Map<String, ProfileValue> valuesOf(Profile profile) {
        Map<String, ProfileValue> values = new LinkedHashMap<>();
        for (RecordComponent component : Profile.class.getRecordComponents()) {
            try {
                values.put(component.getName(), (ProfileValue) component.getAccessor().invoke(profile));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("could not read profile key " + component.getName(), e);
            }
        }
        return values;
    }
}
