package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.forge.visual.ForgeImpactForm;
import com.efkrdnz.magical.network.ForgeImpactPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The one door a forged strike's impact goes out through: a small packet to everybody near, and
 * each client throws the element's matter for it ({@code ForgeImpactParticles}).
 *
 * <p>Sent rather than spawned with {@code ServerLevel.sendParticles}, for the reason the sword
 * school's {@code SwordImpacts} gives: a hit is a couple of dozen particles each with its own
 * velocity, which is a couple of dozen packets that way and one this way.
 */
public final class ForgeImpacts {

    /** Blocks an impact is sent out to. */
    public static final double RANGE = 48.0D;

    private ForgeImpacts() {}

    /** A blade into {@code target}, travelling along {@code travel}. */
    public static void hit(ServerLevel level, LivingEntity target, StrikeLoadout loadout, Vec3 travel) {
        Vec3 at = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        send(level, at, travel.scale(-1.0D), target.getBbWidth() * 0.5F, ForgeImpactForm.HIT, loadout, null);
    }

    /** A thrown strike meeting the face of a block. */
    public static void block(ServerLevel level, BlockHitResult hit, StrikeLoadout loadout) {
        Vec3 face = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
        send(level, hit.getLocation(), face, 0.0F, ForgeImpactForm.BLOCK, loadout,
                level.getBlockState(hit.getBlockPos()));
    }

    private static void send(ServerLevel level, Vec3 at, Vec3 normal, float radius, ForgeImpactForm form,
            StrikeLoadout loadout, BlockState struck) {
        ElementDefinition element = loadout.element();
        int flags = (loadout.heavy() ? ForgeImpactPayload.HEAVY : 0) | (loadout.echo() ? ForgeImpactPayload.ECHO : 0);
        int block = struck == null || struck.isAir() ? 0 : Block.getId(struck);
        MagicalNetwork.sendForgeImpact(level, at, RANGE, new ForgeImpactPayload(at.x, at.y, at.z,
                (float) normal.x, (float) normal.y, (float) normal.z, radius, form.ordinal(),
                element.kind().ordinal(), loadout.weapon().grade().ordinal(), flags, block,
                element.primaryColor(), element.secondaryColor(), element.edgeColor()));
    }
}
