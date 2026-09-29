package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Phantom harm: a lie that hurts is believed more. A believer who touches imagined lava or is bitten by
 * an imagined wolf takes magic damage times their belief and comes away surer
 * ({@link PhantomHarm#RAISE}) - where a doubter who does the same has walked through it.
 */
final class MindHarm {
    private MindHarm() {}

    /** A believer has just crossed into a harmful cluster. */
    static void touched(ServerLevel level, LiveScene scene, LivingEntity viewer, LiveScene.Element element, float belief) {
        hurt(level, scene, null, viewer, PhantomHarm.amount(PhantomHarm.of(element.blockIds()), belief));
        scene.belief().nudge(viewer.getId(), element.index(), PhantomHarm.RAISE);
    }

    /** Believers still inside a harmful cluster burn again every {@link PhantomHarm#INTERVAL} ticks. */
    static void smoulder(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, long now) {
        if ((now - scene.bornAt()) % PhantomHarm.INTERVAL != 0 || scene.inside.isEmpty()) {
            return;
        }
        for (LiveScene.Element element : scene.elements()) {
            float base = element.kind() == LiveScene.Kind.CLUSTER && !scene.manifested(element.index())
                    ? PhantomHarm.of(element.blockIds()) : 0.0F;
            if (base <= 0.0F) {
                continue;
            }
            for (LivingEntity viewer : viewers) {
                float belief = scene.belief().get(viewer.getId(), element.index());
                if (belief >= Belief.CONVINCED && scene.inside.contains(LiveScene.key(viewer.getId(), element.index()))) {
                    hurt(level, scene, null, viewer, PhantomHarm.amount(base, belief));
                }
            }
        }
    }

    /** An imagined creature bites a believer: its kind's attack times their belief. */
    static void struck(ServerLevel level, LiveScene scene, FigmentEntity figment, LivingEntity target,
                       LiveScene.Element element, float belief) {
        float amount = PhantomHarm.amount(KindStats.attack(element.figment().creatureId()), belief);
        if (amount <= 0.0F) {
            return;
        }
        hurt(level, scene, figment, target, amount);
        scene.belief().nudge(target.getId(), element.index(), PhantomHarm.RAISE);
    }

    private static void hurt(ServerLevel level, LiveScene scene, Entity direct, LivingEntity victim, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        // Credited to the wielder when they are online, pushed by nothing: see MindDamageTypes.
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(scene.owner());
        DamageSource source = MindDamageTypes.phantom(level, direct, owner);
        MagicDamageService.hurt(victim, source, amount, MagicContent.UNVEIL.id());
    }
}
