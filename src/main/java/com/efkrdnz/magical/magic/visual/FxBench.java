package com.efkrdnz.magical.magic.visual;

import com.efkrdnz.magical.entity.MagicCircleEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Debug: spawns every registered profile's cast circle in a grid in front of the player and fires
 * its windup / release / impact cues, so the whole library renders at once for eyeballing and
 * frame-time measurement.
 */
public final class FxBench {
    private FxBench() {}

    /** @return the number of profiles staged */
    public static int bench(ServerPlayer player, boolean explicitOnly) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation id : MagicContent.orderedSkillIds()) {
            if (MagicContent.isSubSkill(id)) {
                continue;
            }
            if (explicitOnly && !VisualProfiles.hasExplicit(id)) {
                continue;
            }
            ids.add(id);
        }
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(ids.size())));
        double spacing = 7.0D;
        Vec3 origin = player.position().add(forward.scale(6.0D)).subtract(right.scale((columns - 1) * spacing * 0.5D));
        int i = 0;
        for (ResourceLocation id : ids) {
            MagicSkillDefinition definition = MagicContent.get(id);
            int col = i % columns;
            int row = i / columns;
            Vec3 pos = origin.add(right.scale(col * spacing)).add(forward.scale(row * spacing));
            pos = SpellFx.groundBelow(level, pos.add(0.0D, 2.0D, 0.0D), 8);
            SpellFx.scriptedCircle(level, definition, MagicCircleEffectEntity.ROLE_CAST, pos, VisualProfiles.of(definition).tier().radius(), 200, 0.0F, 90.0F);
            SpellFx.windup(player, definition, pos, forward, false);
            SpellFx.impact(level, definition, pos.add(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, null, 1.0F);
            i++;
        }
        return ids.size();
    }

    /** Stress: n circles + n impact bursts around the player. */
    public static void stress(ServerPlayer player, int circles, int impacts) {
        ServerLevel level = player.serverLevel();
        List<ResourceLocation> ids = MagicContent.orderedSkillIds();
        for (int i = 0; i < circles; i++) {
            ResourceLocation id = ids.get(i % ids.size());
            MagicSkillDefinition definition = MagicContent.get(id);
            double a = i * 2.39996D;
            double r = 3.0D + i * 0.35D;
            Vec3 pos = SpellFx.groundBelow(level, player.position().add(Math.cos(a) * r, 2.0D, Math.sin(a) * r), 8);
            SpellFx.scriptedCircle(level, definition, MagicCircleEffectEntity.ROLE_CAST, pos, 2.4F, 300, 0.0F, 90.0F);
        }
        for (int i = 0; i < impacts; i++) {
            ResourceLocation id = ids.get((i * 7) % ids.size());
            double a = i * 2.39996D + 1.0D;
            double r = 2.0D + i * 0.3D;
            Vec3 pos = player.position().add(Math.cos(a) * r, 1.0D, Math.sin(a) * r);
            SpellFx.impact(level, MagicContent.get(id), pos, new Vec3(0.0D, 1.0D, 0.0D), null, null, 1.0F);
        }
    }
}
