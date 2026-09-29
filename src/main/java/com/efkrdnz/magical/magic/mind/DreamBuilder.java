package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.DreamEditPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The server end of Daydream in your own dream, where belief is total and what you draw is real: blocks
 * and creatures from your Lexicon, free, inside your plot and within reach; unmaking them; and marking
 * the one thing that is wrong. Every edit is re-checked here; the client only asks.
 */
public final class DreamBuilder {
    private DreamBuilder() {}

    public static boolean apply(ServerPlayer player, DreamEditPayload edit) {
        DreamSession session = DreamService.session(player.getUUID());
        ServerLevel dream = DreamService.dreamLevel(player.server);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (session == null || !session.own || dream == null || player.level() != dream
                || !state.hasAuthority(AuthorityContent.MIND)) {
            return false;
        }
        Dreamscape scape = DreamService.dreamscape(dream, player.getUUID());
        boolean changed = switch (edit.action()) {
            case DreamEditPayload.PLACE -> place(dream, player, state, scape, edit);
            case DreamEditPayload.ERASE -> erase(dream, player, scape, edit);
            case DreamEditPayload.FLAW -> flaw(dream, player, scape, edit);
            default -> false;
        };
        if (changed) {
            DreamPlots.of(dream).changed();
            DreamService.sendState(player);
        }
        return changed;
    }

    private static boolean place(ServerLevel dream, ServerPlayer player, PlayerMagicState state, Dreamscape scape, DreamEditPayload edit) {
        Impression chosen = Impression.parse(edit.impression());
        if (chosen == null || !state.mind().lexicon().knows(chosen.key()) || DreamRules.refused(chosen.id())) {
            return false;
        }
        if (chosen.kind() == Impression.Kind.CREATURE) {
            return !edit.cells().isEmpty() && spawn(dream, player, scape, chosen.id(), edit.cells().get(0));
        }
        BlockState block = Manifestation.stateOf(chosen.id());
        if (block == null || block.isAir() || block.hasBlockEntity()) {
            return false;
        }
        boolean placed = false;
        for (BlockPos pos : edit.cells()) {
            // Whatever the Flaw was marked on stays what was marked: nothing is built over it.
            boolean flawCell = scape.flaw() != null && DreamService.offsetIn(scape.plot(), pos).equals(scape.flaw().block());
            if (!flawCell && usable(player, scape, pos) && dream.getBlockState(pos).canBeReplaced()) {
                dream.setBlock(pos, block, Block.UPDATE_ALL);
                placed = true;
            }
        }
        return placed;
    }

    private static boolean spawn(ServerLevel dream, ServerPlayer player, Dreamscape scape, String creatureId, BlockPos pos) {
        if (!usable(player, scape, pos) || figments(dream, scape) >= DreamRules.MAX_FIGMENTS) {
            return false;
        }
        Entity entity = EntityType.byString(creatureId).map(type -> type.create(dream, EntitySpawnReason.COMMAND)).orElse(null);
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        if (!dream.noCollision(mob)) {
            return false;
        }
        mob.setPersistenceRequired();
        mob.addTag(DreamService.DREAM_TAG);
        return dream.addFreshEntity(mob);
    }

    private static boolean erase(ServerLevel dream, ServerPlayer player, Dreamscape scape, DreamEditPayload edit) {
        if (edit.entity() >= 0) {
            Entity target = dream.getEntity(edit.entity());
            if (target == null || !target.getTags().contains(DreamService.DREAM_TAG) || !usable(player, scape, target.blockPosition())) {
                return false;
            }
            scape.clearIfFlaw(target.getUUID());
            target.discard();
            return true;
        }
        boolean erased = false;
        for (BlockPos pos : edit.cells()) {
            if (usable(player, scape, pos) && !dream.getBlockState(pos).isAir()) {
                dream.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                scape.clearIfFlaw(DreamService.offsetIn(scape.plot(), pos));
                erased = true;
            }
        }
        return erased;
    }

    private static boolean flaw(ServerLevel dream, ServerPlayer player, Dreamscape scape, DreamEditPayload edit) {
        if (edit.entity() >= 0) {
            Entity target = dream.getEntity(edit.entity());
            if (target == null || !target.getTags().contains(DreamService.DREAM_TAG) || !usable(player, scape, target.blockPosition())) {
                return false;
            }
            scape.markFigment(target.getUUID());
            flare(player, target.getBoundingBox().getCenter());
        } else if (edit.cells().size() == 1 && usable(player, scape, edit.cells().get(0))
                && !dream.getBlockState(edit.cells().get(0)).isAir()) {
            scape.markBlock(DreamService.offsetIn(scape.plot(), edit.cells().get(0)));
            flare(player, Vec3.atCenterOf(edit.cells().get(0)));
        } else {
            return false;
        }
        player.displayClientMessage(Component.translatable("message.magical.dream_flaw_marked"), true);
        return true;
    }

    /**
     * Marking the Flaw is felt at once: gold sparks round it and a chime - for the owner alone, because
     * a sleeper in the same plot who saw it would know exactly what to touch to wake.
     */
    private static void flare(ServerPlayer owner, Vec3 at) {
        owner.serverLevel().sendParticles(owner, ParticleTypes.WAX_ON, false, false, at.x, at.y, at.z, 24, 0.4, 0.4, 0.4, 0.0);
        owner.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    private static boolean usable(ServerPlayer player, Dreamscape scape, BlockPos pos) {
        return DreamRules.inside(scape.plot(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                && player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= DreamRules.EDIT_REACH;
    }

    /** Every dreamed creature in this plot's cell of the grid, wherever it has wandered to. */
    private static long figments(ServerLevel dream, Dreamscape scape) {
        long count = 0;
        for (Entity entity : dream.getAllEntities()) {
            if (entity instanceof Mob mob && mob.getTags().contains(DreamService.DREAM_TAG)
                    && DreamRules.plotAt(mob.getX(), mob.getZ()) == scape.plot()) {
                count++;
            }
        }
        return count;
    }
}
