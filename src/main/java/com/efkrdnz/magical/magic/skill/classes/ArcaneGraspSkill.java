package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.HeldEntityService;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * MYSTIC - HOIST_SUSPEND / AIM_GRAB / SUSPENDED_OBJECT. A telekinetic grip closes on whatever the aim
 * ray hits: a living thing is hoisted and hung (bosses only staggered); the struck BLOCK is lifted
 * out of the world as a held rock. Then the grip opens: the entity falls, the block falls as debris
 * onto whatever is under it. Sneak = hold it suspended while the key is held.
 */
public final class ArcaneGraspSkill implements SkillModule {
    private static final int RISE = 6;
    private static final double LIFT = 3.5D;
    private static final int MAX_HOLD = 100;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ARCANE_GRASP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                AimResolver.Result aim = ctx.aim();
                LivingEntity target = aim.living();
                int hang = Math.max(20, ctx.duration());
                if (target != null && SkillTargets.isHostile(ctx.caster(), target)) {
                    if (target.getBbWidth() > 2.5F) {
                        SkillTargets.hurt(ctx.level(), ctx.caster(), target, ctx.damage() * 0.5F, ctx.definition(), true);
                        target.setDeltaMovement(Vec3.ZERO);
                        target.hurtMarked = true;
                        return CastResult.SUCCESS;
                    }
                    SpellEffectEntity grip = SpellEffectEntity.spawn(ctx, target.getBoundingBox().getCenter(), RISE + hang, Math.max(0.8F, target.getBbWidth() * 0.9F), new Vec3(0.0D, 1.0D, 0.0D));
                    grip.setTarget(target);
                    grip.setExtra(ctx.slot());
                    grip.serverData().putDouble("BaseY", target.getY());
                    HeldEntityService.hold(target, target.position(), RISE + hang + 2, true, ctx.caster(), "grasp");
                    return CastResult.SUCCESS;
                }
                if (aim.hitBlock()) {
                    BlockPos pos = aim.blockPos();
                    BlockState state = ctx.level().getBlockState(pos);
                    if (state.isAir() || state.getBlock().defaultDestroyTime() < 0.0F || ctx.level().getBlockEntity(pos) != null) {
                        SpellFx.decal(ctx.level(), ctx.definition(), aim.point(), aim.normal(), 0.8F);
                        return CastResult.CONSUMED_NO_COOLDOWN;
                    }
                    ctx.level().removeBlock(pos, false);
                    Vec3 centre = Vec3.atCenterOf(pos);
                    SpellEffectEntity rock = SpellEffectEntity.spawn(ctx, centre, RISE + hang, 0.9F, new Vec3(0.0D, 1.0D, 0.0D));
                    rock.setMode((byte) ((ctx.sneak() ? 1 : 0) | 2));
                    rock.setExtra(ctx.slot());
                    rock.serverData().putDouble("BaseY", centre.y);
                    rock.serverData().put("Block", NbtUtils.writeBlockState(state));
                    CompoundTag synced = new CompoundTag();
                    synced.putBoolean("Rock", true);
                    rock.setSyncedData(synced);
                    return CastResult.SUCCESS;
                }
                // sky: fizzle, half the mana back
                ctx.state().addMana(ctx.definition().baseManaCost() / 2);
                return CastResult.CONSUMED_NO_COOLDOWN;
            }

            @Override
            public double aimRange() {
                return 10.0D;
            }

            @Override
            public double aimTolerance() {
                return 1.0D;
            }

            @Override
            public boolean holdable() {
                return true;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(2.0F, 10.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                double baseY = entity.serverData().getDouble("BaseY");
                double rise = LIFT * Math.min(1.0D, entity.tickCount / (double) RISE);
                boolean rock = entity.serverData().contains("Block");
                // sneak-hold extends the hang while the key stays down
                if (entity.sneakMode() && entity.tickCount >= entity.life() - 1 && entity.tickCount < MAX_HOLD && entity.owner() instanceof ServerPlayer player && HoldService.isHeld(player, entity.extra())) {
                    entity.setLife(entity.life() + 1);
                }
                if (rock) {
                    entity.setPos(entity.getX(), baseY + rise, entity.getZ());
                    return;
                }
                Entity target = entity.target();
                if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                    entity.finish();
                    return;
                }
                HeldEntityService.Hold hold = HeldEntityService.get(living);
                if (hold == null) {
                    hold = HeldEntityService.hold(living, living.position(), entity.life() - entity.tickCount + 2, true, entity.owner(), "grasp");
                }
                hold.position = new Vec3(living.getX(), baseY + rise, living.getZ());
                hold.remaining = Math.max(hold.remaining, entity.life() - entity.tickCount + 2);
                entity.setPos(living.getX(), baseY + rise + living.getBbHeight() * 0.5D, living.getZ());
                entity.setRadius(Math.max(0.8F, living.getBbWidth() * 0.9F));
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                if (entity.serverData().contains("Block")) {
                    BlockState state = NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), entity.serverData().getCompound("Block"));
                    Vec3 landing = AimResolver.groundBelow(level, entity.position(), 24);
                    Vec3 at = landing != null ? landing : entity.position();
                    for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), at, 1.2D)) {
                        SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    }
                    BlockPos place = BlockPos.containing(at.x, at.y + 0.1D, at.z);
                    if (level.getBlockState(place).canBeReplaced() && level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(place)).isEmpty()) {
                        level.setBlock(place, state, Block.UPDATE_ALL);
                    }
                    SpellFx.impact(level, entity.definition(), at, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.3F);
                    return;
                }
                if (entity.target() instanceof LivingEntity living) {
                    HeldEntityService.release(living);
                    SkillTargets.hurt(level, entity.owner(), living, entity.damage() * 0.45F, entity.definition(), true);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.OPEN_HAND).frame(6).band(GlyphKind.RUNE_BAND, 12, ColorRole.HOT).band(GlyphKind.TICK_BAND, 24).stamps(StampId.HOURGLASS, 4).orbit(3, 0.84F, 4).core(CoreKind.HEX_LENS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.lens(FxKinds.Lens.REFRACTION_BUBBLE, 1.0F, 10))
                .silhouette(Silhouette.custom("arcane_grasp", 0.5F).forModes(1))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DUST, FxKinds.Overlay.PRISM_RING)
                .holdable(true)
                .bounds(3.0F, 5.0F, 5.0F);
    }
}
