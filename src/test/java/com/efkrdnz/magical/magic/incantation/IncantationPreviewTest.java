package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A preview spends its uses on a copy: the Grimoire reads the same after two previews as before the first. */
class IncantationPreviewTest {

    private static final int SLOT = 0;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState withAnEmber() {
        PlayerMagicState state = new PlayerMagicState();
        state.grimoire().learnAll(List.of(ProjectileVerses.EMBER));
        assertTrue(state.grimoire().incantation(SLOT).write(List.of(ProjectileVerses.EMBER), 1, VerseContent.CATALOGUE), "the slot takes the ember");
        assertTrue(state.mana() >= 14, "a fresh pool affords an ember");
        return state;
    }

    private static int usesLeft(PlayerMagicState state) {
        return state.grimoire().incantation(SLOT).entries().get(0).usesRemaining();
    }

    @Test
    void twoPreviewsLeaveTheUsesWhereTheyWere() {
        PlayerMagicState state = withAnEmber();
        assertEquals(15, usesLeft(state), "an ember is written with its fifteen uses");
        RecitePlan first = IncantationService.preview(state, SLOT);
        RecitePlan second = IncantationService.preview(state, SLOT);
        assertEquals(1, first.bodies().size(), "the preview plans the ember");
        assertEquals(1, second.bodies().size(), "and plans it again, the copy having been fresh");
        assertEquals(15, usesLeft(state), "and the Grimoire spent none");
    }
}
