package com.efkrdnz.magical.magic.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillTuningView;
import com.efkrdnz.magical.magic.MagicTuningStat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The words the codex puts on the tuning buttons. A skill names its stats through its handler
 * view; what is pinned here is that the fifth stat can be named at all, and that blood names every
 * stat it shows in its own words rather than falling back to a generic "Efficiency".
 */
class TuningViewTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicCastContent.init();
    }

    @Test
    void theFifthStatCarriesALabelLikeTheOtherFour() {
        TuningView view = TuningView.NO_SPEED.labels("d", "s", "z", "u", "e");
        assertEquals("d", view.damageLabelKey());
        assertEquals("e", view.efficiencyLabelKey());
        assertFalse(view.speed(), "relabelling never changes which stats are shown");
        assertNull(TuningView.NO_SPEED.labels("d", "s", "z", "u").efficiencyLabelKey(),
                "the four-label form leaves the fifth on its default");
        assertNull(TuningView.DEFAULT.efficiencyLabelKey());
    }

    @Test
    void bloodManipulationNamesEveryStatItShowsAndTheNamesExist() throws IOException {
        Map<MagicTuningStat, String> expected = Map.of(
                MagicTuningStat.DAMAGE, "screen.magical.tuning.bite",
                MagicTuningStat.SIZE, "screen.magical.tuning.blood_reach",
                MagicTuningStat.DURATION, "screen.magical.tuning.linger",
                MagicTuningStat.EFFICIENCY, "screen.magical.tuning.thrift");
        assertEquals(List.of(MagicTuningStat.DAMAGE, MagicTuningStat.DURATION, MagicTuningStat.SIZE,
                MagicTuningStat.EFFICIENCY), MagicSkillTuningView.statsFor(MagicContent.BLOOD_MANIPULATION));
        String lang = lang();
        for (Map.Entry<MagicTuningStat, String> entry : expected.entrySet()) {
            String key = MagicSkillTuningView.labelKey(MagicContent.BLOOD_MANIPULATION, entry.getKey());
            assertEquals(entry.getValue(), key, entry.getKey() + " label");
            assertTrue(lang.contains("\"" + key + "\""), "language file is missing " + key);
        }
    }

    private static String lang() throws IOException {
        try (InputStream in = TuningViewTest.class.getResourceAsStream("/assets/magical/lang/en_us.json")) {
            assertNotNull(in, "missing language file");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
