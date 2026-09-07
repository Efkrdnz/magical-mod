package com.efkrdnz.magical.forge.strike;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Pins the field order and arity of {@link FormStats} and {@link TemperStats} via reflection on
 * their record components.
 *
 * <p>{@code FormDefinition.stats()} and {@code TemperDefinition.stats()} build these records
 * positionally from same-named, same-typed fields on MC-bound records that cannot be imported
 * here (they carry {@code net.minecraft.resources.ResourceLocation}, so they are not on the
 * plain-JUnit classpath). A future reorder of a field in {@code FormDefinition} or
 * {@code TemperDefinition} without a matching reorder here (or vice versa) would compile fine but
 * transpose two same-typed values silently through the adapter. Pinning the exact component
 * name/order lets a reorder on either side break this test instead.
 */
class StatsAdapterContractTest {

    @Test
    void formStatsComponentOrderMatchesWhatFormDefinitionStatsReliesOn() {
        assertComponentNames(FormStats.class, "family", "lightScale", "heavyScale", "reach", "halfWidth",
                "arcDegrees", "speed", "lifeTicks", "knockback", "recoveryTicks");
    }

    @Test
    void temperStatsComponentOrderMatchesWhatTemperDefinitionStatsReliesOn() {
        assertComponentNames(TemperStats.class, "reachDelta", "widthScale", "speedScale", "knockbackScale",
                "critChance", "comboWindowDelta", "recoveryDelta", "chargeThresholdDelta");
    }

    private static void assertComponentNames(Class<?> recordType, String... expectedNamesInOrder) {
        RecordComponent[] components = recordType.getRecordComponents();
        String[] actualNames = Arrays.stream(components).map(RecordComponent::getName).toArray(String[]::new);
        assertArrayEquals(expectedNamesInOrder, actualNames, recordType.getSimpleName()
                + " record component name/order changed - update the matching *Definition.stats() adapter"
                + " (and this pin) together, or a future reorder will transpose values silently");
    }
}
