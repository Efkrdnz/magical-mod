package com.efkrdnz.magical.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.MagicVisualContent;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The roster-wide uniqueness rules fail the build; MagicVertex packing round-trips. */
class VisualProfilesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void magicVertexPackingRoundTrips() {
        int packed = MagicVertex.pack(23, 45, 17, 0.5F, 33, 2);
        int x = packed & 0xFFFF;
        int y = (packed >>> 16) & 0xFFFF;
        assertEquals(23, x & 31);
        assertEquals(45, (x >> 5) & 63);
        assertEquals(17, (x >> 11) & 31);
        assertEquals(128, y & 255);
        assertEquals(33, (y >> 8) & 63);
        assertEquals(2, (y >> 14) & 3);
        int rephased = MagicVertex.withPhase(packed, 1.0F);
        assertEquals(255, (rephased >>> 16) & 255);
        assertEquals(x, rephased & 0xFFFF);
    }

    @Test
    void circleScriptHasOneEmblemAndOneHotLayer() {
        CircleScript script = CircleScript.of(SchoolMaterial.FIRE)
                .emblem(EmblemId.FLAME)
                .frame(3)
                .band(GlyphKind.TOOTH_BAND, 24)
                .band(GlyphKind.RUNE_BAND, 12)
                .stamps(StampId.THORN, 6)
                .orbit(5, 0.84F, 3)
                .core(CoreKind.EMBER_PIT)
                .build();
        assertEquals(1, script.emblemLayerCount());
        assertEquals(1, script.hotLayerCount());
        assertTrue(script.layers().size() >= 3 && script.layers().size() <= 14);
        assertEquals(EmblemId.FLAME, script.emblem());
        assertTrue(script.identityOnly().layers().size() < script.layers().size());
        assertTrue(script.signature().startsWith("FLAME|3|TOOTH_BAND:24"));
    }

    @Test
    void paletteDerivationMatchesLegacyRules() {
        Palette palette = Palette.derive(0x10_20_30);
        assertEquals(0x34_3C_74, palette.bright());
        assertEquals(0x5E_62_92, palette.hot());
        assertEquals(0x00_00_14, palette.dim());
    }

    @Test
    void rosterHasNoVisualCollisions() {
        MagicVisualContent.init();
        List<String> hard = VisualProfiles.hardProblems();
        assertTrue(hard.isEmpty(), () -> String.join("\n", hard));
    }
}
