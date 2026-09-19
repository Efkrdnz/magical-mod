package com.efkrdnz.magical.magic.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.efkrdnz.magical.magic.AuthorityContent;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The heading over the codex's Authority row is the Authority the player holds, not Space for everyone. */
class AuthorityRowLabelTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theHeldAuthorityNamesTheRow() {
        assertEquals("authority.magical.authority_of_mana", MagicPyramidMenu.authorityRowLabelKey(AuthorityContent.MANA));
        assertEquals("authority.magical.authority_of_space", MagicPyramidMenu.authorityRowLabelKey(AuthorityContent.SPACE));
        assertEquals("authority.magical.authority_of_chaos", MagicPyramidMenu.authorityRowLabelKey(AuthorityContent.CHAOS));
    }

    @Test
    void noAuthorityIsAPlainHeading() {
        assertEquals("screen.magical.authority_row", MagicPyramidMenu.authorityRowLabelKey(null));
        assertEquals("screen.magical.authority_row", MagicPyramidMenu.authorityRowLabelKey(ResourceLocation.fromNamespaceAndPath("magical", "authority_of_nothing")));
    }
}
