package com.efkrdnz.magical.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code /magical class unlock} takes any class and whatever it takes to reach it, and nothing
 * else: the root and each rung on the way, never another tree, and never the XP the player had.
 */
class ClassGrantTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theTopOfTheSwordChainBringsEveryRungBelowIt() {
        PlayerMagicState state = new PlayerMagicState();
        List<ResourceLocation> taken = state.grantClass(null, MagicalClasses.SWORD_GOD);
        assertEquals(List.of(MagicalClasses.SWORD_SUMMONER, MagicalClasses.SWORD_RIDER,
                MagicalClasses.SWORD_SAINT, MagicalClasses.SWORD_GOD), taken);
        taken.forEach(id -> assertTrue(state.hasClass(id), id + " is owned"));
    }

    @Test
    void grantingSpendsNoneOfTheXpThePlayerHad() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockClass(MagicalClasses.SWORD_SUMMONER);
        state.addClassXp(MagicalClasses.SWORD_SUMMONER, 50);
        state.grantClass(null, MagicalClasses.SWORD_SAINT);
        assertEquals(50, state.classProgressFor(MagicalClasses.SWORD_SUMMONER).xp());
    }

    @Test
    void nothingOutsideThePathIsGranted() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantClass(null, MagicalClasses.SWORD_RIDER);
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            boolean onPath = definition.id().equals(MagicalClasses.SWORD_SUMMONER)
                    || definition.id().equals(MagicalClasses.SWORD_RIDER);
            assertEquals(onPath, state.hasClass(definition.id()), definition.id().toString());
        }
    }

    @Test
    void anOwnedParentIsTheWayInWhenThereAreTwo() {
        MagicalClassDefinition converging = MagicalClasses.all().stream()
                .filter(definition -> definition.parents().size() > 1)
                .findFirst().orElseThrow();
        ResourceLocation second = converging.parents().get(1);
        PlayerMagicState state = new PlayerMagicState();
        state.grantClass(null, second);
        List<ResourceLocation> taken = state.grantClass(null, converging.id());
        assertEquals(List.of(converging.id()), taken, "only the node itself: its second parent was owned");
        assertFalse(state.hasClass(converging.parents().get(0)), "the unowned parent is not walked");
    }

    @Test
    void anOwnedClassGrantsNothing() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantClass(null, MagicalClasses.SWORD_RIDER);
        assertEquals(List.of(), state.grantClass(null, MagicalClasses.SWORD_RIDER));
    }
}
