package com.efkrdnz.magical.forge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.weapon.MagicalWeapons;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;
import com.efkrdnz.magical.registry.MagicalItems;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Building the lines a forged weapon shows, which is the call that crashed a client.
 *
 * <p>The stack was {@code getTooltipLines} to {@code ForgedWeapons.tooltip} to {@code artsLine} to a
 * class initialiser that had thrown - and none of it was reachable from a test, because nothing
 * built a tooltip. Everything above {@code tooltip} in that stack is vanilla dispatching an event;
 * everything from it down is ours, and this walks it.
 *
 * <p>Every weapon in the catalogue gets a turn, against every element and every form, because the
 * crash needed one particular combination to reach the broken index - and a single hand-picked
 * example would only have proved that one combination was fine.
 */
class ForgedWeaponTooltipTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack forged(ItemStack stack, String element, String form, ForgeGrade grade) {
        ForgedWeapons.write(stack, new ForgedWeapon(ForgeIds.id(element), grade, Optional.empty(),
                List.of(ForgeIds.id(form)), List.of(), List.of(), 100, 0L));
        return stack;
    }

    /** Every line renders to a string, which is the whole contract a tooltip has. */
    private static void assertReadable(List<Component> lines, String what) {
        assertFalse(lines.isEmpty(), what + " produced no tooltip at all");
        for (Component line : lines) {
            assertNotNull(line.getString(), what + " produced a line that renders to nothing");
        }
    }

    /**
     * The sweep. Any element on any form, on any weapon in the catalogue, has to build its lines.
     *
     * <p>This is the one that would have caught it: blood on a lunge was the pair that reached an
     * Art the pair index should never have held, and no smaller sweep names that pair on purpose.
     */
    @Test
    void everyWeaponBuildsATooltipForEveryElementAndFormItCouldCarry() {
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            ItemStack stack = new ItemStack(MagicalItems.weapon(weapon.id()).get());
            for (ElementDefinition element : ForgeElements.all()) {
                for (FormDefinition form : ForgeForms.all()) {
                    forged(stack, element.id().getPath(), form.id().getPath(), ForgeGrade.HIGH);
                    assertReadable(ForgedWeapons.tooltip(stack),
                            weapon.path() + " forged " + element.id().getPath() + "/" + form.id().getPath());
                }
            }
        }
    }

    /** A forged vanilla weapon is the other half of what a player can actually be holding. */
    @Test
    void aForgedVanillaWeaponBuildsItsTooltipToo() {
        ItemStack sword = forged(new ItemStack(Items.IRON_SWORD), "fire", "thrust", ForgeGrade.CRUDE);
        assertReadable(ForgedWeapons.tooltip(sword), "a forged iron sword");
    }

    /** And an unforged stack asks for nothing, rather than producing an empty-looking block. */
    @Test
    void anUnforgedStackContributesNoLines() {
        assertTrue(ForgedWeapons.tooltip(new ItemStack(Items.IRON_SWORD)).isEmpty(),
                "a plain iron sword grew forge lines");
        assertTrue(ForgedWeapons.tooltip(ItemStack.EMPTY).isEmpty(),
                "an empty stack grew forge lines");
    }

    /**
     * A pair that carries an Art grows the Arts line; a pair that does not, does not.
     *
     * <p>Both directions, so the sweep above cannot be passing by never reaching the Art lookup at
     * all - which is exactly how it would have looked if {@code artsLine} quietly returned empty
     * for everything.
     *
     * <p>Checked by translation key rather than by rendered text. No language is loaded in a unit
     * test, so {@code getString()} hands back the key with its arguments dropped, and every line
     * would look the same.
     */
    @Test
    void theArtsLineAppearsForAPairThatCarriesAnArtAndNotOtherwise() {
        ResourceLocation fire = ForgeIds.id("fire");
        assertTrue(ForgeSpecials.art(fire, ForgeIds.id("thrust")).isPresent(),
                "fire/thrust no longer carries an Art, so this test needs a new pair");
        assertTrue(ForgeSpecials.art(fire, ForgeIds.id("slash")).isEmpty(),
                "fire/slash now carries an Art, so this test needs a new counter-example");

        ItemStack stack = new ItemStack(MagicalItems.weapon(MagicalWeapons.EMBERBRAND.id()).get());
        assertTrue(hasArtsLine(forged(stack, "fire", "thrust", ForgeGrade.MASTER)),
                "a fire thrust carries CINDER_LANCE but its tooltip never says so");
        assertFalse(hasArtsLine(forged(stack, "fire", "slash", ForgeGrade.MASTER)),
                "a fire slash carries no Art but its tooltip claims one");
    }

    private static boolean hasArtsLine(ItemStack stack) {
        return ForgedWeapons.tooltip(stack).stream().anyMatch(line ->
                line.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("tooltip.magical.forge.arts"));
    }
}
