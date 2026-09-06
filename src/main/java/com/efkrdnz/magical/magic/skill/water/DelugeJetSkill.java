package com.efkrdnz.magical.magic.skill.water;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T3 - JET / CHANNELLED_HAND_BEAM / LINE. A pressurized water jet from the hand along the
 * look, steered by looking while the key is held (mana per tick past the base duration): shoves
 * and grinds everything in it, extinguishes, and erodes soft terrain where the tip lands.
 * Sneak = fire it backward over the shoulder.
 */
public final class DelugeJetSkill implements SkillModule {
    private static final double RANGE = 14.0D;
    private static final int MAX_HOLD = 60;
    private static final int MAX_BLOCKS = 24;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.DELUGE_JET;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity jet = SpellEffectEntity.spawn(ctx, ctx.eye(), Math.max(10, ctx.duration()), (float) RANGE, ctx.lookOrBack());
                jet.setExtra(ctx.slot());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public boolean holdable() {
                return true;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.0F, 12.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            if (owner == null || !owner.isAlive()) {
                entity.finish();
                return;
            }
            Vec3 origin = owner.getEyePosition().add(0.0D, -0.2D, 0.0D);
            Vec3 dir = owner.getLookAngle().normalize();
            if (entity.sneakMode()) {
                dir = dir.scale(-1.0D);
            }
            entity.setPos(origin);
            entity.setDirection(dir);
            // sustain while held (1 mana per 2 ticks past the base duration)
            if (owner instanceof ServerPlayer player && entity.tickCount >= entity.duration() && entity.tickCount < MAX_HOLD && HoldService.isHeld(player, entity.extra())) {
                var state = player.getData(MagicalAttachments.MAGIC_STATE);
                if (entity.tickCount % 2 == 0) {
                    if (state.mana() < 1) {
                        return;
                    }
                    state.setMana(state.mana() - 1);
                    state.sync(player);
                }
                entity.setLife(entity.tickCount + 2);
            }
            BlockHitResult hit = level.clip(new ClipContext(origin, origin.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            double length = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(origin) : RANGE;
            entity.setRadius((float) length);
            entity.setValue(Math.min(1.0F, entity.tickCount / 6.0F));
            // push + grind + extinguish along the capsule
            AABB sweep = new AABB(origin, origin.add(dir.scale(length))).inflate(0.9D);
            double push = Math.max(0.15D, entity.speed());
            for (LivingEntity living : SkillTargets.hostilesIn(level, owner, sweep)) {
                Vec3 rel = living.getBoundingBox().getCenter().subtract(origin);
                double along = rel.dot(dir);
                if (along < 0.0D || along > length) {
                    continue;
                }
                if (rel.subtract(dir.scale(along)).length() > 0.9D + living.getBbWidth() * 0.5D) {
                    continue;
                }
                Vec3 v = living.getDeltaMovement().add(dir.scale(push));
                double h = Math.sqrt(v.x * v.x + v.z * v.z);
                if (h > push * 1.6D) {
                    v = new Vec3(v.x / h * push * 1.6D, v.y, v.z / h * push * 1.6D);
                }
                living.setDeltaMovement(v);
                living.fallDistance = 0.0F;
                living.hurtMarked = true;
                living.clearFire();
                if (entity.tickCount % 4 == 0) {
                    SkillTargets.hurt(level, owner, living, entity.damage(), entity.definition().id());
                }
            }
            // erosion where the tip meets soft terrain
            if (hit.getType() == HitResult.Type.BLOCK) {
                CompoundTag data = entity.serverData();
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                boolean erodible = state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL) || state.is(net.minecraft.world.level.block.Blocks.CLAY)
                        || state.is(BlockTags.SNOW) || state.is(net.minecraft.world.level.block.Blocks.MUD) || state.is(BlockTags.SOUL_SPEED_BLOCKS);
                if (erodible && data.getInt("Broken") < MAX_BLOCKS) {
                    long key = pos.asLong();
                    int exposure = data.getInt("Exp" + key) + 1;
                    data.putInt("Exp" + key, exposure);
                    if (exposure >= 8) {
                        level.destroyBlock(pos, false);
                        data.putInt("Broken", data.getInt("Broken") + 1);
                        data.remove("Exp" + key);
                        SpellFx.impact(level, entity.definition(), hit.getLocation(), new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ()), null, owner, 0.8F);
                    }
                }
                if (entity.tickCount % 5 == 0) {
                    SpellFx.burst(level, entity.definition(), hit.getLocation(), new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ()), 1.0F);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.DROP).frame(3).band(GlyphKind.DASHED_RING, 24).spokes(12, 0.3F, true).stamps(StampId.NEEDLE, 3).core(CoreKind.SUNBURST).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.TUBE, FxKinds.Filament.WATER, 1, 0.45F, 14.0F, 4).withOpacity(0.9F))
                .silhouette(Silhouette.filament(Silhouette.Form.HELIX, FxKinds.Filament.PLASMA_TUBE, 2, 0.5F, 14.0F, 2).withOpacity(0.35F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.SHOCK_RING)
                .holdable(true)
                .bounds(15.0F, 3.0F, 3.0F);
    }
}
