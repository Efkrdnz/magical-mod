package com.efkrdnz.magical.magic.cast;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A handler that means "mobs never cast this" has to say so with {@link MobCastProfile#NONE}.
 *
 * <p>Twelve of them said it with {@code null} instead, and the comment above each one described
 * exactly the right intent - mobs have no editor to draw a blood shape in, so there is no shape for
 * them to cast. But {@code MagicMobCastingService.canMobUse} dereferences the profile, so every one
 * of those was a NullPointerException on the server thread, in entity ticking, the moment an
 * ascendant opponent considered the skill. It crashed the integrated server outright.
 */
class MobCastProfileTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicCastContent.init();
    }

    @Test
    void noHandlerAnswersNullWhenAskedHowAMobWouldUseIt() {
        List<String> nulls = new ArrayList<>();
        for (ResourceLocation id : MagicContent.orderedSkillIds()) {
            if (!SkillCastRegistry.has(id)) {
                continue;
            }
            SkillCastHandler handler = SkillCastRegistry.get(id);
            assertNotNull(handler, id + " is registered but resolves to nothing");
            if (handler.mob() == null) {
                nulls.add(id.toString());
            }
        }
        assertTrue(nulls.isEmpty(),
                "these answer null rather than MobCastProfile.NONE, which crashes the server thread: " + nulls);
    }

    @Test
    void noneIsTheWayToSayAMobNeverCastsIt() {
        assertNotNull(MobCastProfile.NONE);
        assertTrue(!MobCastProfile.NONE.usable(), "NONE must not read as usable");
    }
}
