package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.VerseContent;
import com.efkrdnz.magical.magic.incantation.VerseIds;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Grimoire rides on the state like the Fracture does: copied, saved, loaded, and emptied with the Authority. */
class PlayerMagicStateGrimoireTest {

    private static final List<ResourceLocation> VERSES = List.of(VerseIds.of("needle"), VerseIds.of("weight"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState written() {
        PlayerMagicState state = new PlayerMagicState();
        state.grimoire().learnAll(VERSES);
        assertTrue(state.grimoire().incantation(1).write(VERSES, 3, VerseContent.CATALOGUE), "the slot takes a known incantation");
        return state;
    }

    @Test
    void aCopyKeepsTheGrimoire() {
        PlayerMagicState copy = written().copy();
        assertEquals(2, copy.grimoire().incantation(1).size());
        assertEquals(3, copy.grimoire().incantation(1).breath());
        assertTrue(copy.grimoire().knows(VerseIds.of("weight")));
    }

    @Test
    void aSaveAndLoadKeepsTheGrimoire() {
        PlayerMagicState loaded = PlayerMagicState.load(written().save());
        assertEquals(2, loaded.grimoire().incantation(1).size());
        assertEquals(3, loaded.grimoire().incantation(1).breath());
        assertEquals(VerseIds.of("needle"), loaded.grimoire().incantation(1).entries().get(0).id());
        assertTrue(loaded.grimoire().knows(VerseIds.of("needle")));
    }

    @Test
    void clearingTheAuthorityEmptiesTheGrimoire() {
        PlayerMagicState state = written();
        state.clearAuthority();
        assertTrue(state.grimoire().incantation(1).isEmpty(), "the incantation went with the Authority");
        assertTrue(state.grimoire().known().isEmpty(), "and so did what it knew");
    }
}
