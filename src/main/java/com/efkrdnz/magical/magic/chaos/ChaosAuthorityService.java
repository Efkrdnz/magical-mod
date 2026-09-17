package com.efkrdnz.magical.magic.chaos;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The four things a wielder of Chaos can actually press.
 *
 * <p>Three of them have no numbers of their own. Burden places one unit of stress and does nothing
 * visible; the Last Grain adds one more unit from nowhere and does nothing unless that was the one
 * that mattered; Criticality has no damage at all and only lowers the ground. Every bit of the
 * violence in this Authority comes out of {@link Pile}, which means it comes out of what the
 * wielder built rather than out of what they just spent.
 */
public final class ChaosAuthorityService {

    /** How far a wielder can reach to burden or to nudge. */
    public static final double REACH = 24.0D;

    /** How long Criticality holds the ground down. */
    public static final int CRITICALITY_TICKS = 200;

    private ChaosAuthorityService() {}

    /** One unit of stress onto whatever the crosshair is on. Sneak puts it on yourself. */
    public static boolean burden(ServerPlayer player, PlayerMagicState state) {
        if (!allowed(player, state, MagicContent.BURDEN)) {
            return false;
        }
        PileSite site = aimedSite(player, true);
        if (site == null) {
            player.displayClientMessage(Component.translatable("message.magical.pile_nothing_there"), true);
            return false;
        }
        Pile pile = PileService.pileFor(player);
        long now = player.serverLevel().getGameTime();
        if (pile.slack(site, now)) {
            player.displayClientMessage(Component.translatable("message.magical.pile_ground_spent"), true);
            return false;
        }
        MagicSkillResolvedStats stats = resolve(state, MagicContent.BURDEN);
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        pile.add(site, 1, now);
        state.setSkillCooldown(MagicContent.BURDEN.id(), stats.cooldownTicks());
        state.sync(player);
        mote(player.serverLevel(), site, 4);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.5F, 1.6F);
        player.displayClientMessage(Component.translatable("message.magical.pile_reading",
                pile.stressAt(site), pile.capacityAt(site, now)), true);
        return true;
    }

    /**
     * One more unit, from nowhere.
     *
     * <p>On a site below capacity this is a click and nothing else, and that is not a failure state
     * - it is the skill. The whole expression is reading the ground and knowing which shoulder is
     * the one that matters.
     */
    public static boolean lastGrain(ServerPlayer player, PlayerMagicState state) {
        if (!allowed(player, state, MagicContent.LAST_GRAIN)) {
            return false;
        }
        PileSite site = aimedSite(player, false);
        Pile pile = PileService.pileFor(player);
        long now = player.serverLevel().getGameTime();
        if (site == null || pile.stressAt(site) <= 0) {
            player.displayClientMessage(Component.translatable("message.magical.pile_nothing_there"), true);
            return false;
        }
        if (pile.slack(site, now)) {
            player.displayClientMessage(Component.translatable("message.magical.pile_ground_spent"), true);
            return false;
        }
        MagicSkillResolvedStats stats = resolve(state, MagicContent.LAST_GRAIN);
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        pile.add(site, 1, now);
        state.setSkillCooldown(MagicContent.LAST_GRAIN.id(), stats.cooldownTicks());
        state.sync(player);
        mote(player.serverLevel(), site, 8);
        boolean going = pile.settling();
        player.level().playSound(null, player.blockPosition(),
                going ? SoundEvents.CALCITE_BREAK : SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS,
                going ? 0.9F : 0.4F, going ? 0.7F : 1.9F);
        if (!going) {
            player.displayClientMessage(Component.translatable("message.magical.pile_reading",
                    pile.stressAt(site), pile.capacityAt(site, now)), true);
        }
        return true;
    }

    /** Lowers every capacity in the wielder Pile, and lets the whole thing go at once. */
    public static boolean criticality(ServerPlayer player, PlayerMagicState state) {
        if (!allowed(player, state, MagicContent.CRITICALITY)) {
            return false;
        }
        Pile pile = PileService.pileFor(player);
        MagicSkillResolvedStats stats = resolve(state, MagicContent.CRITICALITY);
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        long now = player.serverLevel().getGameTime();
        pile.lowerGround(1, now + CRITICALITY_TICKS, now);
        state.setSkillCooldown(MagicContent.CRITICALITY.id(), stats.cooldownTicks());
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 0.5F);
        player.displayClientMessage(Component.translatable(
                pile.settling() ? "message.magical.criticality_given" : "message.magical.criticality_empty"), true);
        return true;
    }

    /** Re-authors the Fracture. Validated again here, whatever the client claims to have sent. */
    public static boolean setFracture(ServerPlayer player, int[] ordinals) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(AuthorityContent.CHAOS) || !state.hasUnlocked(MagicContent.FRACTURE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        if (state.isSkillOnCooldown(MagicContent.FRACTURE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        Fault[] values = Fault.values();
        boolean changed = false;
        for (int i = 0; i < Math.min(Fracture.LENGTH, ordinals.length); i++) {
            int ordinal = ordinals[i];
            if (ordinal < 0 || ordinal >= values.length) {
                continue;
            }
            changed |= state.fracture().get(i) != values[ordinal];
            state.fracture().set(i, values[ordinal]);
        }
        MagicSkillResolvedStats stats = resolve(state, MagicContent.FRACTURE);
        state.setSkillCooldown(MagicContent.FRACTURE.id(), stats.cooldownTicks());
        state.sync(player);
        if (changed) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_BREAK,
                    SoundSource.PLAYERS, 0.7F, 1.3F);
        }
        player.displayClientMessage(Component.translatable("message.magical.fracture_set"), true);
        return true;
    }

    // ---- aiming ------------------------------------------------------------------------------

    /**
     * Whatever the wielder is looking at, body before block.
     *
     * <p>Package-visible because the readout in {@link PileService} asks the same question every few
     * ticks, and asking it two different ways would put the number out of step with the key.
     */
    static PileSite aimedSite(ServerPlayer player, boolean allowSelf) {
        if (allowSelf && player.isShiftKeyDown()) {
            return PileSite.of(player.getId());
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(REACH));
        Entity best = null;
        double nearest = Double.MAX_VALUE;
        List<Entity> candidates = player.level().getEntities(player, new AABB(eye, end).inflate(1.0D),
                entity -> entity.isAlive() && entity.isPickable());
        for (Entity candidate : candidates) {
            var clip = candidate.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (clip.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(clip.get());
            if (distance < nearest) {
                nearest = distance;
                best = candidate;
            }
        }
        BlockHitResult block = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        boolean blockHit = block.getType() == HitResult.Type.BLOCK;
        double blockDistance = blockHit ? eye.distanceToSqr(block.getLocation()) : Double.MAX_VALUE;
        if (best != null && nearest <= blockDistance) {
            return PileSite.of(best.getId());
        }
        return blockHit ? PileSite.of(block.getBlockPos()) : null;
    }

    // ---- shared gates ------------------------------------------------------------------------

    private static boolean allowed(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill) {
        if (!state.hasAuthority(AuthorityContent.CHAOS) || !state.hasUnlocked(skill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        if (state.isSkillOnCooldown(skill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        return true;
    }

    private static MagicSkillResolvedStats resolve(PlayerMagicState state, MagicSkillDefinition skill) {
        return skill.resolve(state.tuningFor(skill.id()));
    }

    private static void mote(ServerLevel level, PileSite site, int count) {
        Vec3 centre = site.isBlock() ? Vec3.atCenterOf(site.block()) : entityCentre(level, site);
        if (centre == null) {
            return;
        }
        level.sendParticles(ParticleTypes.WITCH, centre.x, centre.y + 0.55D, centre.z, count, 0.2D, 0.2D, 0.2D, 0.01D);
    }

    private static Vec3 entityCentre(ServerLevel level, PileSite site) {
        Entity entity = level.getEntity(site.entityId());
        if (entity == null) {
            return null;
        }
        double lift = entity instanceof LivingEntity living ? living.getBbHeight() * 0.6D : 0.5D;
        return entity.position().add(0.0D, lift, 0.0D);
    }
}
