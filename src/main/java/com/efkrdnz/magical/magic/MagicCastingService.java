package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.DivineDividerWaveEntity;
import com.efkrdnz.magical.entity.FlareTriangleEntity;
import com.efkrdnz.magical.entity.AbyssalDischargeEntity;
import com.efkrdnz.magical.entity.BlackFlameProjectileEntity;
import com.efkrdnz.magical.entity.DimensionalGuillotineEntity;
import com.efkrdnz.magical.entity.GabrielHolyFieldEntity;
import com.efkrdnz.magical.entity.JudgementBeamEntity;
import com.efkrdnz.magical.entity.MagicCircleEffectEntity;
import com.efkrdnz.magical.entity.SingularityEntity;
import com.efkrdnz.magical.entity.SovereignAegisEntity;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MagicCastingService {
    private MagicCastingService() {}

    public static void castSlot(ServerPlayer player, int slot) {
        castSlot(player, slot, player.isShiftKeyDown());
    }

    public static void castSlot(ServerPlayer player, int slot, boolean sneakDown) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        ResourceLocation skillId = state.equippedSkill(slot);
        castResolved(player, state, skillId, slot + 1, sneakDown);
    }

    public static void castWheelSkill(ServerPlayer player, ResourceLocation skillId) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (skillId != null && !state.hasWheelSkill(skillId)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_not_in_wheel"), true);
            return;
        }
        castResolved(player, state, skillId, -1, player.isShiftKeyDown());
    }

    public static void castSovereignAegisMode(ServerPlayer player, int slot, int mode) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        ResourceLocation parentId = slot >= 0 && slot < MagicContent.LOADOUT_SIZE ? state.equippedSkill(slot) : null;
        castSovereignAegisMode(player, state, parentId, mode, false);
    }

    public static void castWheelSubSkill(ServerPlayer player, ResourceLocation parentId, int mode) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (parentId == null || !state.hasWheelSkill(parentId)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_not_in_wheel"), true);
            return;
        }
        if (MagicContent.GABRIEL.id().equals(parentId)) {
            castSovereignAegisMode(player, state, parentId, mode, true);
        } else if (MagicContent.BLACK_FLAMES.id().equals(parentId)) {
            castBlackFlamesMode(player, state, parentId, mode, true);
        } else if (MagicContent.SPATIAL_ARSENAL.id().equals(parentId)) {
            castSpaceOffenseMode(player, state, parentId, mode, true);
        }
    }

    private static void castSovereignAegisMode(ServerPlayer player, PlayerMagicState state, ResourceLocation parentId, int mode, boolean fromWheel) {
        boolean gabriel = MagicContent.GABRIEL.id().equals(parentId);
        if (!gabriel || (fromWheel && !state.hasWheelSkill(parentId))) {
            return;
        }
        if (!state.hasUnlocked(parentId) || SovereignAegisEntity.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }

        MagicSkillDefinition subSkill = aegisSubSkill(mode, gabriel);
        if (subSkill == null || !state.hasUnlocked(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (isPerfectSealSubSkill(subSkill) && tryUndoPerfectSeal(player, subSkill.resolve(state.tuningFor(subSkill.id())))) {
            return;
        }
        if (isUltimateProtectionSubSkill(subSkill) && SovereignAegisEntity.hasUltimate(player)) {
            SovereignAegisEntity.toggleUltimate(player.serverLevel(), player, subSkill.resolve(state.tuningFor(subSkill.id())));
            return;
        }
        if (state.isSkillOnCooldown(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }

        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, subSkill.resolve(state.tuningFor(subSkill.id())));
        if (isOffensive(subSkill) && SovereignAegisEntity.isInsideOffenseBlockingSanctuary(player)) {
            player.displayClientMessage(Component.translatable("message.magical.sanctuary_blocks_offense"), true);
            return;
        }
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }

        boolean cast;
        if (MagicContent.GABRIEL_JUDGEMENT.id().equals(subSkill.id())) {
            cast = castJudgement(player, stats);
        } else if (MagicContent.GABRIEL_HOLY_FIELD.id().equals(subSkill.id())) {
            cast = castGabrielHolyField(player, stats);
        } else if (isUltimateProtectionSubSkill(subSkill)) {
            cast = SovereignAegisEntity.toggleUltimate(player.serverLevel(), player, stats);
        } else if (isPerfectSealSubSkill(subSkill)) {
            cast = castPerfectSeal(player, stats);
        } else {
            cast = false;
        }
        if (!cast) {
            state.setMana(state.mana() + stats.manaCost());
            state.sync(player);
            return;
        }
        state.setSkillCooldown(subSkill.id(), state.consumeCooldownEcho(subSkill.id()) ? 0 : stats.cooldownTicks());
        state.setSkillCooldown(parentId, Math.min(stats.cooldownTicks(), 80));
        MagicSinService.afterSuccessfulCast(player, state, subSkill);
        state.sync(player);
    }

    public static void castBlackFlamesMode(ServerPlayer player, int slot, int mode) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        ResourceLocation parentId = slot >= 0 && slot < MagicContent.LOADOUT_SIZE ? state.equippedSkill(slot) : null;
        castBlackFlamesMode(player, state, parentId, mode, false);
    }

    private static void castBlackFlamesMode(ServerPlayer player, PlayerMagicState state, ResourceLocation parentId, int mode, boolean fromWheel) {
        if (!MagicContent.BLACK_FLAMES.id().equals(parentId) || (fromWheel && !state.hasWheelSkill(parentId))) {
            return;
        }
        if (!state.hasUnlocked(MagicContent.BLACK_FLAMES.id()) || SovereignAegisEntity.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }

        MagicSkillDefinition subSkill = blackFlamesSubSkill(mode);
        if (subSkill == null || !state.hasUnlocked(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (state.isSkillOnCooldown(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }

        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, subSkill.resolve(state.tuningFor(subSkill.id())));
        if (SovereignAegisEntity.isInsideOffenseBlockingSanctuary(player)) {
            player.displayClientMessage(Component.translatable("message.magical.sanctuary_blocks_offense"), true);
            return;
        }
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }

        boolean cast;
        if (MagicContent.BLACK_FLAMES_CAST.id().equals(subSkill.id())) {
            cast = castBlackFlamesCast(player, stats);
        } else if (MagicContent.BLACK_FLAMES_IMBUE.id().equals(subSkill.id())) {
            cast = BlackFlamesService.castImbue(player, state, stats);
        } else if (MagicContent.BLACK_FLAMES_BRAND.id().equals(subSkill.id())) {
            cast = BlackFlamesService.castBrand(player, stats);
        } else {
            cast = false;
        }
        if (!cast) {
            state.setMana(state.mana() + stats.manaCost());
            state.sync(player);
            return;
        }
        int cooldown = state.consumeCooldownEcho(subSkill.id()) ? 0 : stats.cooldownTicks();
        state.setSkillCooldown(subSkill.id(), cooldown);
        state.setSkillCooldown(MagicContent.BLACK_FLAMES.id(), Math.min(cooldown, 80));
        MagicSinService.afterSuccessfulCast(player, state, subSkill);
        state.sync(player);
    }

    public static void castSpaceOffenseMode(ServerPlayer player, int slot, int mode) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        ResourceLocation parentId = slot >= 0 && slot < MagicContent.LOADOUT_SIZE ? state.equippedSkill(slot) : null;
        castSpaceOffenseMode(player, state, parentId, mode, false);
    }

    private static void castSpaceOffenseMode(ServerPlayer player, PlayerMagicState state, ResourceLocation parentId, int mode, boolean fromWheel) {
        if (!MagicContent.SPATIAL_ARSENAL.id().equals(parentId) || (fromWheel && !state.hasWheelSkill(parentId))) {
            return;
        }
        if (!state.hasAuthority(AuthorityContent.SPACE) || !state.hasUnlocked(MagicContent.SPATIAL_ARSENAL.id()) || SovereignAegisEntity.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }

        MagicSkillDefinition subSkill = spatialArsenalSubSkill(mode);
        if (subSkill == null || !state.hasUnlocked(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (state.isSkillOnCooldown(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }

        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, subSkill.resolve(state.tuningFor(subSkill.id())));
        if (SovereignAegisEntity.isInsideOffenseBlockingSanctuary(player)) {
            player.displayClientMessage(Component.translatable("message.magical.sanctuary_blocks_offense"), true);
            return;
        }
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }

        boolean cast;
        if (MagicContent.SINGULARITY.id().equals(subSkill.id())) {
            cast = castSingularity(player, stats);
        } else if (MagicContent.DIMENSIONAL_GUILLOTINE.id().equals(subSkill.id())) {
            cast = castDimensionalGuillotine(player, stats);
        } else {
            cast = false;
        }
        if (!cast) {
            state.setMana(state.mana() + stats.manaCost());
            state.sync(player);
            return;
        }
        int cooldown = state.consumeCooldownEcho(subSkill.id()) ? 0 : stats.cooldownTicks();
        state.setSkillCooldown(subSkill.id(), cooldown);
        state.setSkillCooldown(MagicContent.SPATIAL_ARSENAL.id(), Math.min(cooldown, 80));
        MagicSinService.afterSuccessfulCast(player, state, subSkill);
        state.sync(player);
    }

    private static MagicSkillDefinition blackFlamesSubSkill(int mode) {
        return switch (mode) {
            case 0 -> MagicContent.BLACK_FLAMES_CAST;
            case 1 -> MagicContent.BLACK_FLAMES_IMBUE;
            case 2 -> MagicContent.BLACK_FLAMES_BRAND;
            default -> null;
        };
    }

    private static MagicSkillDefinition spatialArsenalSubSkill(int mode) {
        return switch (mode) {
            case 0 -> MagicContent.SINGULARITY;
            case 1 -> MagicContent.DIMENSIONAL_GUILLOTINE;
            default -> null;
        };
    }

    private static MagicSkillDefinition aegisSubSkill(int mode, boolean gabriel) {
        if (gabriel) {
            return switch (mode) {
                case SovereignAegisEntity.MODE_ULTIMATE_PROTECTION -> MagicContent.GABRIEL_ULTIMATE_PROTECTION;
                case SovereignAegisEntity.MODE_SANCTUARY -> MagicContent.GABRIEL_JUDGEMENT;
                case SovereignAegisEntity.MODE_PERFECT_SEAL -> MagicContent.GABRIEL_PERFECT_SEAL;
                case 3 -> MagicContent.GABRIEL_HOLY_FIELD;
                default -> null;
            };
        }
        return null;
    }

    private static boolean isUltimateProtectionSubSkill(MagicSkillDefinition skill) {
        return MagicContent.GABRIEL_ULTIMATE_PROTECTION.id().equals(skill.id());
    }

    private static boolean isPerfectSealSubSkill(MagicSkillDefinition skill) {
        return MagicContent.GABRIEL_PERFECT_SEAL.id().equals(skill.id());
    }

    private static boolean isOffensive(MagicSkillDefinition definition) {
        return definition.type() != MagicSkillType.BARRIER && definition.baseDamage() > 0.0F;
    }

    /** Cast any skill by id as if it were pressed from a loadout slot (debug command entry point). */
    public static void castById(ServerPlayer player, ResourceLocation skillId, boolean sneak) {
        castResolved(player, player.getData(MagicalAttachments.MAGIC_STATE), skillId, 1, sneak);
    }

    private static void castResolved(ServerPlayer player, PlayerMagicState state, ResourceLocation skillId, int slotDisplay, boolean sneakDown) {
        if (skillId == null) {
            if (slotDisplay > 0) {
                player.displayClientMessage(Component.translatable("message.magical.empty_slot", slotDisplay), true);
            }
            return;
        }

        if (SovereignAegisEntity.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.magical.perfect_seal_locked"), true);
            return;
        }

        if (state.isSkillOnCooldown(skillId)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }

        MagicSkillDefinition definition = MagicContent.get(skillId);
        if (definition == null || !state.hasUnlocked(skillId)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        com.efkrdnz.magical.magic.status.MagicStatus blocker = com.efkrdnz.magical.magic.status.MagicStatusService.castBlocker(player);
        if (blocker != null) {
            player.displayClientMessage(Component.translatable("message.magical.status_blocks_cast." + blocker.name().toLowerCase(java.util.Locale.ROOT)), true);
            return;
        }
        if (com.efkrdnz.magical.magic.cast.SkillCastRegistry.has(skillId)) {
            castViaRegistry(player, state, definition, slotDisplay - 1, sneakDown);
            return;
        }
        if (MagicContent.isSubSkill(definition.id())) {
            if (MagicContent.SINGULARITY.id().equals(definition.id()) || MagicContent.DIMENSIONAL_GUILLOTINE.id().equals(definition.id())) {
                player.displayClientMessage(Component.translatable("message.magical.spatial_arsenal_hold"), true);
            } else if (MagicContent.SOUL_VALLEY.id().equals(definition.id())) {
                player.displayClientMessage(Component.translatable("message.magical.soul_vow_hold"), true);
            } else if (MagicContent.blackFlamesSubSkills().stream().anyMatch(subSkill -> subSkill.id().equals(definition.id()))) {
                player.displayClientMessage(Component.translatable("message.magical.black_flames_hold"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.magical.gabriel_parent"), true);
            }
            return;
        }
        // every castable skill is a registry handler now
        player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
    }

    /**
     * Registry-driven dispatch: validation already done by castResolved. Self-managed handlers run
     * before mana/stats; everything else gets stats, mana, an always-successful aim, the windup and
     * release cues, and the cooldown / sin / sync bookkeeping around its {@code cast}.
     */
    private static void castViaRegistry(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, int slot, boolean sneakDown) {
        com.efkrdnz.magical.magic.cast.SkillCastHandler handler = com.efkrdnz.magical.magic.cast.SkillCastRegistry.get(definition.id());
        com.efkrdnz.magical.magic.visual.VisualProfile profile = com.efkrdnz.magical.magic.visual.VisualProfiles.of(definition);
        boolean sneak = sneakDown || player.isShiftKeyDown();
        long seed = player.serverLevel().getGameTime() * 31L + player.getId();
        if (handler.selfManaged() || handler.holdGated()) {
            handler.cast(com.efkrdnz.magical.magic.cast.CastContext.forPlayer(player, state, definition, definition.resolve(state.tuningFor(definition.id())), sneak, slot, profile, null, seed));
            return;
        }
        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, definition.resolve(state.tuningFor(definition.id())));
        if (isOffensive(definition) && SovereignAegisEntity.isInsideOffenseBlockingSanctuary(player)) {
            player.displayClientMessage(Component.translatable("message.magical.sanctuary_blocks_offense"), true);
            return;
        }
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }
        com.efkrdnz.magical.magic.cast.AimResolver.Result aim = com.efkrdnz.magical.magic.cast.AimResolver.resolve(
                player.serverLevel(), player, player.getLookAngle(), handler.aimRange(), handler.aimTolerance(), handler.aimDropsToGround(), 8, null);
        com.efkrdnz.magical.magic.cast.CastContext ctx = com.efkrdnz.magical.magic.cast.CastContext.forPlayer(player, state, definition, stats, sneak, slot, profile, aim, seed);
        com.efkrdnz.magical.magic.visual.SpellFx.windup(player, definition, aim.point(), player.getLookAngle(), sneak);
        com.efkrdnz.magical.magic.cast.CastResult result;
        try {
            result = handler.cast(ctx);
        } catch (RuntimeException exception) {
            com.efkrdnz.magical.MagicalMod.LOGGER.error("Cast handler for {} threw", definition.id(), exception);
            result = com.efkrdnz.magical.magic.cast.CastResult.FAILED;
        }
        switch (result) {
            case FAILED -> {
                state.setMana(state.mana() + stats.manaCost());
                state.sync(player);
            }
            case CONSUMED_NO_COOLDOWN -> {
                com.efkrdnz.magical.magic.visual.SpellFx.release(player, definition, player.getLookAngle());
                state.setSkillCooldown(definition.id(), Math.min(40, stats.cooldownTicks()));
                state.sync(player);
            }
            case SUCCESS -> {
                com.efkrdnz.magical.magic.visual.SpellFx.release(player, definition, player.getLookAngle());
                state.setSkillCooldown(definition.id(), state.consumeCooldownEcho(definition.id()) ? 0 : stats.cooldownTicks());
                MagicSinService.afterSuccessfulCast(player, state, definition);
                ClassXpService.onSpellCast(player);
                state.sync(player);
            }
            case HANDLED -> state.sync(player);
        }
    }

    /** Kept-skill bridge: Abyssal Discharge's original cast, callable from its registry handler. */
    /**
     * Flare Ring places up to three ground points; two make a burning wall, three close the triangle.
     * Placing a point spends mana but starts no cooldown, which {@link CastResult} already expresses,
     * so the old three-state result record is gone.
     */
    public static com.efkrdnz.magical.magic.cast.CastResult legacyFlareRing(ServerPlayer player, MagicSkillResolvedStats stats) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(32.0D + stats.size() * 2.5D));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            player.displayClientMessage(Component.translatable("message.magical.flare_ring_no_ground"), true);
            return com.efkrdnz.magical.magic.cast.CastResult.FAILED;
        }

        Vec3 point = hit.getLocation().add(0.0D, 0.06D, 0.0D);
        FlareTriangleEntity active = activeFlareTriangle(level, player);
        boolean completed;
        if (active == null) {
            active = FlareTriangleEntity.create(level, player, stats, point);
            level.addFreshEntity(active);
            completed = false;
        } else {
            completed = active.addPoint(point);
        }

        spawnCastingCircle(level, player, stats, 1.85F + stats.size() * 0.45F, 18, true);
        if (completed) {
            level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.85F, 0.58F);
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.92F, 0.44F);
            MagicalNetwork.playFirstPersonImpact(player, 0xFF641F, 12, 0.25F, 5, 0.28F, 0, 1.5F);
        }
        return completed
                ? com.efkrdnz.magical.magic.cast.CastResult.SUCCESS
                : com.efkrdnz.magical.magic.cast.CastResult.CONSUMED_NO_COOLDOWN;
    }

    private static FlareTriangleEntity activeFlareTriangle(ServerLevel level, ServerPlayer player) {
        AABB search = player.getBoundingBox().inflate(48.0D);
        for (FlareTriangleEntity triangle : level.getEntitiesOfClass(FlareTriangleEntity.class, search, entity -> player.getUUID().equals(entity.ownerUuid()) && entity.pointCount() < 3)) {
            return triangle;
        }
        return null;
    }

    /** Sword-gated divine cut. Kept as a legacy body so the registry handler stays a thin wrapper. */
    public static boolean legacyDivineDivider(ServerPlayer player, MagicSkillResolvedStats stats) {
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("message.magical.divine_divider_requires_sword"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        DivineDividerWaveEntity wave = new DivineDividerWaveEntity(level, player, stats);
        level.addFreshEntity(wave);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.45F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.82F, 0.62F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.72F, 1.82F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.88F, 1.35F);
        MagicalNetwork.playFirstPersonImpact(player, 0xF8FCFF, 14, 0.28F, 8, 0.34F, 1, 3.0F);
        return true;
    }

    public static boolean legacyAbyssalDischarge(ServerPlayer player, MagicSkillResolvedStats stats) {
        return castAbyssalDischarge(player, stats);
    }

    public static void refillBarrier(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (state.refillBarrierFromMana()) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.55F, 1.35F);
            state.sync(player);
        } else {
            player.displayClientMessage(Component.translatable("message.magical.barrier_refill_failed"), true);
        }
    }




    /**
     * Where a ground-targeted construct should anchor. Prefers a living target the aim passes near
     * (with a generous tolerance so it need not be pixel-perfect), then a block the aim strikes, and
     * failing both projects the aim to full range and drops onto the ground beneath it - so a miss
     * lands out in front of the caster instead of uselessly at their feet.
     */
    private static Vec3 aimTarget(ServerPlayer player, double range) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reachSqr = blockHit.getType() != HitResult.Type.MISS ? eye.distanceToSqr(blockHit.getLocation()) : range * range;

        double tolerance = 1.6D;
        LivingEntity best = null;
        double bestSqr = reachSqr;
        AABB search = new AABB(eye, end).inflate(tolerance);
        for (Entity entity : level.getEntities(player, search, t -> t instanceof LivingEntity living && living.isAlive() && t != player)) {
            Optional<Vec3> clip = entity.getBoundingBox().inflate(tolerance).clip(eye, end);
            if (clip.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(clip.get());
            if (distance <= bestSqr) {
                bestSqr = distance;
                best = (LivingEntity) entity;
            }
        }
        if (best != null) {
            return new Vec3(best.getX(), best.getY() + 0.06D, best.getZ());
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            return blockHit.getLocation().add(0.0D, 0.06D, 0.0D);
        }
        BlockHitResult ground = level.clip(new ClipContext(end, end.add(0.0D, -range, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (ground.getType() != HitResult.Type.MISS) {
            return ground.getLocation().add(0.0D, 0.06D, 0.0D);
        }
        return end;
    }


    private static boolean castBlackFlamesCast(ServerPlayer player, MagicSkillResolvedStats stats) {
        ServerLevel level = player.serverLevel();
        spawnCastingCircle(level, player, stats, 2.55F + stats.size() * 0.62F, 24, true);
        level.addFreshEntity(new BlackFlameProjectileEntity(level, player, stats));
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.74F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.62F, 0.62F);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.18F, 1.18F);
        MagicalNetwork.playFirstPersonImpact(player, 0x1A071F, 14, 0.22F, 5, 0.2F, 1, -0.9F);
        return true;
    }


    private static boolean castSingularity(ServerPlayer player, MagicSkillResolvedStats stats) {
        ServerLevel level = player.serverLevel();
        spawnCastingCircle(level, player, stats, 2.1F + stats.size() * 0.48F, 22, true);
        SingularityEntity singularity = SingularityEntity.create(level, player, stats);
        level.addFreshEntity(singularity);
        Vec3 pos = singularity.position();
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.35F, 0.28F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.42F, 0.55F);
        MagicalNetwork.playFirstPersonImpact(player, 0x050714, 16, 0.22F, 6, 0.18F, 1, -0.75F);
        return true;
    }

    private static boolean castDimensionalGuillotine(ServerPlayer player, MagicSkillResolvedStats stats) {
        ServerLevel level = player.serverLevel();
        spawnCastingCircle(level, player, stats, 2.35F + stats.size() * 0.42F, 18, true);
        DimensionalGuillotineEntity rift = DimensionalGuillotineEntity.create(level, player, stats);
        level.addFreshEntity(rift);
        level.playSound(null, rift.getX(), rift.getY(), rift.getZ(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.2F, 0.45F);
        MagicalNetwork.playFirstPersonImpact(player, 0x82E8FF, 14, 0.18F, 5, 0.14F, 1, -0.55F);
        return true;
    }





    private static boolean castPerfectSeal(ServerPlayer player, MagicSkillResolvedStats stats) {
        LivingEntity target = findLookedAtLiving(player, 32.0D + stats.size() * 2.0D);
        if (target == null || target == player) {
            player.displayClientMessage(Component.translatable("message.magical.perfect_seal_no_target"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        level.addFreshEntity(SovereignAegisEntity.createSeal(level, player, target, stats));
        level.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.1F, 0.52F);
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.85F, 1.42F);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.62F);
        MagicalNetwork.playFirstPersonImpact(player, 0xFFE27A, 18, 0.14F, 4, 0.15F, 0, 0.65F);
        return true;
    }

    private static boolean tryUndoPerfectSeal(ServerPlayer player, MagicSkillResolvedStats stats) {
        LivingEntity target = findLookedAtLiving(player, 32.0D + stats.size() * 2.0D);
        if (target == null || target == player) {
            return false;
        }
        return SovereignAegisEntity.closeSealForTarget(player.serverLevel(), player, target);
    }

    private static LivingEntity findLookedAtLiving(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));
        AABB search = new AABB(eye, end).inflate(1.35D);
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : player.serverLevel().getEntities(player, search, target -> target instanceof LivingEntity living && living.isAlive() && target != player)) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.75D).clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = (LivingEntity) entity;
            }
        }
        return closest;
    }






    private static boolean castJudgement(ServerPlayer player, MagicSkillResolvedStats stats) {
        LivingEntity target = findJudgementTarget(player, 40.0D);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.magical.judgement_no_target"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        level.addFreshEntity(JudgementBeamEntity.create(level, player, target, stats));
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 8.0F, 0.42F);
        level.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 7.0F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 6.0F, 0.62F);
        MagicalNetwork.playFirstPersonImpact(player, 0xFFDC38, 18, 0.22F, 4, 0.18F, 0, 1.5F);
        return true;
    }

    private static boolean castGabrielHolyField(ServerPlayer player, MagicSkillResolvedStats stats) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(42.0D + stats.size() * 3.0D));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().add(0.0D, 0.08D, 0.0D) : player.position().add(0.0D, 0.08D, 0.0D);
        level.addFreshEntity(GabrielHolyFieldEntity.create(level, player, center, stats));
        spawnCastingCircle(level, player, stats, 2.75F + stats.size() * 0.55F, 32, true);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 5.0F, 0.92F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 4.0F, 0.44F);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 3.0F, 0.72F);
        MagicalNetwork.playFirstPersonImpact(player, stats.definition().color(), 22, 0.26F, 5, 0.22F, 1, 2.0F);
        return true;
    }

    private static boolean castAbyssalDischarge(ServerPlayer player, MagicSkillResolvedStats stats) {
        ServerLevel level = player.serverLevel();
        level.addFreshEntity(AbyssalDischargeEntity.create(level, player, stats));
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.55F, 0.42F);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.72F, 0.78F);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.65F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 0.32F);
        MagicalNetwork.playFirstPersonImpact(player, 0x341052, 32, 0.4F, 11, 0.52F, 1, -2.0F);
        return true;
    }

    private static LivingEntity findJudgementTarget(ServerPlayer player, double range) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistanceSqr = range * range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistanceSqr = eye.distanceToSqr(blockHit.getLocation());
        }

        AABB search = new AABB(eye, end).inflate(2.0D);
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(player, search, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != player)) {
            AABB bounds = entity.getBoundingBox().inflate(1.1D);
            Optional<Vec3> hit = bounds.clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.get());
            if (distance > maxDistanceSqr || distance >= closestDistance) {
                continue;
            }
            closestDistance = distance;
            closest = (LivingEntity) entity;
        }
        return closest;
    }







    private static void spawnCastingCircle(ServerLevel level, ServerPlayer player, MagicSkillResolvedStats stats, float radius, int life, boolean forward) {
        int style = circleStyleFor(stats);
        int color = stats.definition().color();
        int detail = MagicCircleEffectEntity.detailForTier(stats.definition().tier());
        if (forward) {
            Vec3 look = player.getLookAngle();
            if (look.lengthSqr() < 0.0001D) {
                look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
            }
            look = look.normalize();
            Vec3 position = player.getEyePosition().add(look.scale(1.35D));
            float yaw = (float) (Mth.atan2(look.x, look.z) * Mth.RAD_TO_DEG);
            float pitch = (float) (-Math.asin(look.y) * Mth.RAD_TO_DEG);
            level.addFreshEntity(MagicCircleEffectEntity.createStatic(level, position, radius, color, life, style, yaw, pitch, 0.0F, detail));
            return;
        }
        level.addFreshEntity(MagicCircleEffectEntity.createStatic(level, player.position().add(0.0D, 0.06D, 0.0D), radius, color, life, style, 0.0F, 90.0F, 0.0F, detail));
    }

    private static int circleStyleFor(MagicSkillResolvedStats stats) {
        return switch (stats.definition().school()) {
            case FIRE -> MagicCircleEffectEntity.STYLE_CINDER_MARK;
            case WATER -> MagicCircleEffectEntity.STYLE_GLACIER_WAVE;
            case LIGHT -> MagicCircleEffectEntity.STYLE_PRISM_GUARD;
            case VOID -> MagicContent.BLACK_FLAMES_CAST.id().equals(stats.definition().id()) ? MagicCircleEffectEntity.STYLE_BLACK_FLAMES : MagicCircleEffectEntity.STYLE_CINDER_MARK;
            case SPATIAL -> MagicCircleEffectEntity.STYLE_ANCHOR_SIGIL;
            default -> MagicCircleEffectEntity.STYLE_ANCHOR_SIGIL;
        };
    }


}
