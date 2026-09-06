package com.efkrdnz.magical.magic.passive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A class passive is metadata plus a handler branch, and nothing in the type system connects the
 * two. These tests are that connection: registering a passive without implementing it, or
 * implementing one twice, fails the build instead of shipping a tooltip that lies.
 */
class ClassPassiveEffectsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyClassPassiveIsClaimedByExactlyOneHandler() {
        List<ResourceLocation> claims = new ArrayList<>();
        for (ClassPassiveHandler handler : ClassPassiveEffects.handlers()) {
            claims.addAll(handler.handled());
        }
        Set<ResourceLocation> claimed = new LinkedHashSet<>(claims);
        assertEquals(claims.size(), claimed.size(), "two handlers claim the same passive");

        Set<ResourceLocation> registered = MagicPassiveContent.classPassives();
        for (ResourceLocation id : registered) {
            assertTrue(claimed.contains(id), id + " is registered but no handler implements it");
        }
        for (ResourceLocation id : claimed) {
            assertTrue(registered.contains(id), id + " is handled but is not a registered class passive");
        }
    }

    @Test
    void everyClassPassiveIsSingleLevelAndNotACurse() {
        for (ResourceLocation id : MagicPassiveContent.classPassives()) {
            var definition = MagicPassiveContent.get(id);
            // There is no level-up UI, so anything above level 1 would be unreachable in normal play.
            assertEquals(1, definition.maxLevel(), id + " must be single level");
            assertTrue(!definition.curse(), id + " must not be a curse");
            // Class passives are earned from the tree; the tower shop must not also sell them.
            assertEquals(0, definition.shopTier(), id + " must not be purchasable");
        }
    }

    @Test
    void handlersDoNotShareScratchAcrossPlayers() {
        // forget() is the only cleanup path; a handler that keeps state must implement it.
        Set<String> withoutForget = new HashSet<>();
        for (ClassPassiveHandler handler : ClassPassiveEffects.handlers()) {
            boolean declaresForget = false;
            for (var method : handler.getClass().getDeclaredMethods()) {
                if (method.getName().equals("forget")) {
                    declaresForget = true;
                    break;
                }
            }
            if (!declaresForget) {
                withoutForget.add(handler.getClass().getSimpleName());
            }
        }
        assertTrue(withoutForget.isEmpty(), "handlers keeping per-player state must override forget: " + withoutForget);
    }
}
