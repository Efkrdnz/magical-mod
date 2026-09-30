package com.efkrdnz.magical.magic.skill.primordial;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.primordial.FaultPath;
import com.efkrdnz.magical.magic.primordial.PrimordialService;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import com.efkrdnz.magical.magic.service.Bodies;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * PRIMORDIAL T-4 - the earth opens and bites shut.
 *
 * <p>A crack races from the caster's feet along their gaze and the ground along it falls away into
 * a chasm two wide; what stood on it drops in. After a while it slams shut, and whatever is still
 * inside - any body overlapping a cell the earth gives back - is crushed and thrown up to the
 * surface. The owner and their allies are lifted out unhurt. Wellspring: stone, which sets how deep
 * the chasm goes and how hard it bites.
 */
public final class FaultLineSkill implements SkillModule {
    public static final int BASE_LENGTH = 20;
    public static final int WIDTH = 2;
    public static final double SPEED = 1.5D;
    public static final int MIN_DEPTH = 3;
    public static final int MAX_DEPTH = 6;
    public static final int NONE = Integer.MIN_VALUE;
    private static final int WARN_BEFORE = 20;
    private static final int ALLY_FLOAT = 30;
    public static final float CRUSH = 3.0F;
    private static final String KEY_COLUMNS = "Cols";
    private static final String KEY_TOPS = "Tops";
    private static final String KEY_OPENED = "Open";
    private static final String KEY_DEPTH = "Depth";
    private static final String KEY_CLOSE = "Close";
    private static final String KEY_WELL = "Well";
    private static final String KEY_EDIT = "Edit";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.FAULT_LINE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                Vec3 look = ctx.look();
                Vec3 dir = new Vec3(look.x, 0.0D, look.z);
                dir = dir.lengthSqr() > 1.0E-6D ? dir.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
                Vec3 origin = ctx.feet();
                int length = Math.max(6, Math.round(BASE_LENGTH * ctx.size()));
                List<FaultPath.Cell> cells = FaultPath.columns(origin.x, origin.z, dir.x, dir.z, length, WIDTH);
                int[] columns = new int[cells.size() * 3];
                int[] tops = new int[cells.size()];
                List<BlockPos> found = new ArrayList<>();
                for (int i = 0; i < cells.size(); i++) {
                    FaultPath.Cell cell = cells.get(i);
                    columns[i * 3] = cell.x();
                    columns[i * 3 + 1] = cell.z();
                    columns[i * 3 + 2] = cell.step();
                    BlockPos top = PrimordialService.surface(level, new Vec3(cell.x() + 0.5D, origin.y, cell.z() + 0.5D), 4, 6);
                    tops[i] = top == null ? NONE : top.getY();
                    if (top != null) {
                        found.add(top);
                    }
                }
                if (found.isEmpty()) {
                    PrimordialService.refuse(ctx.caster(), "message.magical.primordial.no_ground");
                    return CastResult.FAILED;
                }
                float stone = PrimordialService.stone(level, found, MAX_DEPTH);
                PrimordialService.say(ctx.caster(), "stone", stone);
                int depth = Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, Math.round(MIN_DEPTH + (MAX_DEPTH - MIN_DEPTH) * (stone - 0.5F))));
                int travel = (int) Math.ceil(length / SPEED) + 1;
                int close = travel + ctx.duration();
                SpellEffectEntity fault = SpellEffectEntity.spawn(ctx, origin.add(dir.scale(FaultPath.START)), close + 2, 1.0F, dir);
                CompoundTag data = fault.serverData();
                data.putIntArray(KEY_COLUMNS, columns);
                data.putIntArray(KEY_TOPS, tops);
                data.putInt(KEY_OPENED, 0);
                data.putInt(KEY_DEPTH, depth);
                data.putInt(KEY_CLOSE, close);
                data.putFloat(KEY_WELL, stone);
                data.putUUID(KEY_EDIT, ConjuredTerrainService.begin(level).id());
                fault.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2F, 0.35F);
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.5F, 0.7F);
                return CastResult.SUCCESS;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity fault) {
                CompoundTag data = fault.serverData();
                if (!data.contains(KEY_CLOSE)) {
                    return;
                }
                int t = fault.tickCount;
                int close = data.getInt(KEY_CLOSE);
                if (t < close) {
                    open(fault, t);
                    if (t == close - WARN_BEFORE) {
                        warn(fault);
                    }
                } else if (t == close) {
                    slam(fault);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity fault) {
                giveBack(fault);
            }
        };
    }

    private static ConjuredTerrainService.Edit edit(SpellEffectEntity fault) {
        CompoundTag data = fault.serverData();
        return data.hasUUID(KEY_EDIT) ? ConjuredTerrainService.lookup(fault.serverLevel(), data.getUUID(KEY_EDIT)) : null;
    }

    private static void giveBack(SpellEffectEntity fault) {
        PrimordialService.giveBack(fault.serverLevel(), edit(fault), s -> s.isAir() || s.canBeReplaced());
        fault.serverData().remove(KEY_EDIT);
    }

    /**
     * Open every column the crack has reached by now. The effect itself stays where the crack began,
     * beside its caster: walked out to the tip it could stand in a chunk that no longer ticks
     * entities and never shut the chasm it opened.
     */
    private static void open(SpellEffectEntity fault, int t) {
        ServerLevel level = fault.serverLevel();
        CompoundTag data = fault.serverData();
        int[] columns = data.getIntArray(KEY_COLUMNS);
        int[] tops = data.getIntArray(KEY_TOPS);
        int depth = data.getInt(KEY_DEPTH);
        int opened = data.getInt(KEY_OPENED);
        ConjuredTerrainService.Edit edit = edit(fault);
        double reached = t * SPEED * Math.max(0.35F, fault.speed());
        Entity owner = fault.owner();
        int n = tops.length;
        while (opened < n && columns[opened * 3 + 2] <= reached) {
            int x = columns[opened * 3];
            int z = columns[opened * 3 + 1];
            int top = tops[opened];
            opened++;
            if (top == NONE || edit == null) {
                continue;
            }
            BlockState face = level.getBlockState(new BlockPos(x, top, z));
            for (int d = 0; d < depth; d++) {
                if (PrimordialService.carve(level, edit, new BlockPos(x, top - d, z), Blocks.AIR.defaultBlockState()) == null) {
                    break; // a column the earth will not open further is left as it stands below
                }
            }
            AABB above = new AABB(x, top + 0.8D, z, x + 1.0D, top + 3.0D, z + 1.0D);
            for (LivingEntity body : Bodies.of(level, LivingEntity.class, above, LivingEntity::isAlive)) {
                if (SkillTargets.isHostile(owner, body)) {
                    Vec3 v = body.getDeltaMovement();
                    body.setDeltaMovement(v.x * 0.3D, -0.6D, v.z * 0.3D);
                    body.hurtMarked = true;
                    SkillTargets.hurt(level, owner, body, fault.damage(), fault.skillId());
                } else {
                    // the earth lowers its own gently
                    body.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ALLY_FLOAT, 0, false, false));
                }
            }
            if (!face.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, face), x + 0.5D, top + 0.9D, z + 0.5D, 10, 0.4D, 0.3D, 0.4D, 0.18D);
                if ((opened & 1) == 0) {
                    level.playSound(null, x + 0.5D, top, z + 0.5D, face.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.2F, 0.55F);
                }
            }
            level.sendParticles(ParticleTypes.DUST_PLUME, x + 0.5D, top + 0.6D, z + 0.5D, 3, 0.3D, 0.1D, 0.3D, 0.02D);
        }
        data.putInt(KEY_OPENED, opened);
    }

    /** A second's warning: the walls shed dust and the ground groans. */
    private static void warn(SpellEffectEntity fault) {
        ServerLevel level = fault.serverLevel();
        CompoundTag data = fault.serverData();
        int[] columns = data.getIntArray(KEY_COLUMNS);
        int[] tops = data.getIntArray(KEY_TOPS);
        for (int i = 0; i < tops.length; i += 2) {
            if (tops[i] == NONE) {
                continue;
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState()),
                    columns[i * 3] + 0.5D, tops[i] + 0.5D, columns[i * 3 + 1] + 0.5D, 4, 0.4D, 0.2D, 0.4D, 0.0D);
        }
        level.playSound(null, fault.getX(), fault.getY(), fault.getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    /** Shut: give the ground back and crush whatever is still in it. */
    private static void slam(SpellEffectEntity fault) {
        ServerLevel level = fault.serverLevel();
        CompoundTag data = fault.serverData();
        int[] columns = data.getIntArray(KEY_COLUMNS);
        int[] tops = data.getIntArray(KEY_TOPS);
        int depth = data.getInt(KEY_DEPTH);
        int opened = data.getInt(KEY_OPENED);
        float well = data.contains(KEY_WELL) ? data.getFloat(KEY_WELL) : 1.0F;
        Entity owner = fault.owner();
        List<LivingEntity> caught = new ArrayList<>();
        List<Integer> caughtTop = new ArrayList<>();
        for (int i = 0; i < opened && i < tops.length; i++) {
            if (tops[i] == NONE) {
                continue;
            }
            int x = columns[i * 3];
            int z = columns[i * 3 + 1];
            AABB cell = new AABB(x, tops[i] - depth + 1, z, x + 1.0D, tops[i] + 1.0D, z + 1.0D);
            for (LivingEntity body : Bodies.of(level, LivingEntity.class, cell, LivingEntity::isAlive)) {
                int at = caught.indexOf(body);
                if (at < 0) {
                    caught.add(body);
                    caughtTop.add(tops[i]);
                } else if (tops[i] > caughtTop.get(at)) {
                    // straddling two columns: out onto the higher of them
                    caughtTop.set(at, tops[i]);
                }
            }
        }
        giveBack(fault);
        for (int i = 0; i < caught.size(); i++) {
            LivingEntity body = caught.get(i);
            PrimordialService.unbury(level, body, caughtTop.get(i) + 1.02D);
            if (SkillTargets.isHostile(owner, body)) {
                SkillTargets.hurt(level, owner, body, fault.damage() * CRUSH * well, fault.definition(), true);
                body.setDeltaMovement(0.0D, 0.75D, 0.0D);
            } else {
                body.setDeltaMovement(0.0D, 0.4D, 0.0D);
            }
            body.hurtMarked = true;
        }
        for (int i = 0; i < opened && i < tops.length; i += 3) {
            if (tops[i] == NONE) {
                continue;
            }
            double x = columns[i * 3] + 0.5D;
            double z = columns[i * 3 + 1] + 0.5D;
            BlockState face = level.getBlockState(new BlockPos(columns[i * 3], tops[i], columns[i * 3 + 1]));
            if (!face.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, face), x, tops[i] + 1.0D, z, 16, 0.6D, 0.2D, 0.6D, 0.3D);
            }
            level.sendParticles(ParticleTypes.CLOUD, x, tops[i] + 1.0D, z, 4, 0.5D, 0.1D, 0.5D, 0.05D);
        }
        level.playSound(null, fault.getX(), fault.getY(), fault.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.45F);
        level.playSound(null, fault.getX(), fault.getY(), fault.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.35F);
        fault.setPhase(SpellEffectEntity.PHASE_DONE);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.PRIMORDIAL)
                .circle(CircleScript.of(SchoolMaterial.PRIMORDIAL).emblem(EmblemId.FAULT).frame(3)
                        .band(GlyphKind.TOOTH_BAND, 10, ColorRole.BRIGHT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.TOOTH, 8).core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.DUST, 6, 0.8F).withRole(ColorRole.DIM).withOpacity(0.6F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DUST, FxKinds.Overlay.CRACKED_GLASS)
                .budget(3)
                .bounds(2.0F, 2.0F, 2.0F);
    }
}
