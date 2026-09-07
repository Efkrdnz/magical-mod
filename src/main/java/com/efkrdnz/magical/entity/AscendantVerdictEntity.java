package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.entity.ascendant.AscendantTier;
import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Ascendant's telegraph: a wind-up the player has to answer with a key press.
 *
 * <p>It is a separate entity per verdict on purpose. {@code MagicCounterService} remembers one
 * prompt per threat entity for the life of that entity, so hanging the mechanic off the boss itself
 * would prompt exactly once per fight and then go quiet.
 *
 * <p>Unlike every other counterable threat in the mod, missing this one costs something. The
 * ordinary counter system is opt-in relief - if you happen to own a skill of the right attribute
 * you may cancel an incoming spell, and if you do not, nothing happens either way. A boss telegraph
 * that behaved like that would be invisible to most players, so this asks for the key alone and
 * takes a bite out of anyone who does not press it.
 */
public final class AscendantVerdictEntity extends Entity implements CounterableSkillThreat {

    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(AscendantVerdictEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE =
            SynchedEntityData.defineId(AscendantVerdictEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIER =
            SynchedEntityData.defineId(AscendantVerdictEntity.class, EntityDataSerializers.INT);

    /** Colour of the gathering ring, matching Judgement's own gold. */
    private static final int VERDICT_COLOR = 0xFFD166;

    /** Ticks of wind-up before the window opens, so the ring is seen before it is answered. */
    private static final int TELEGRAPH_LEAD = 3;

    private UUID ownerUuid;
    private UUID targetUuid;

    public AscendantVerdictEntity(EntityType<? extends AscendantVerdictEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static AscendantVerdictEntity create(ServerLevel level, Entity owner, LivingEntity target,
            AscendantTier tier) {
        AscendantVerdictEntity verdict =
                new AscendantVerdictEntity(MagicalEntities.ASCENDANT_VERDICT.get(), level);
        verdict.ownerUuid = owner == null ? null : owner.getUUID();
        verdict.targetUuid = target.getUUID();
        verdict.entityData.set(TARGET_ID, target.getId());
        verdict.entityData.set(TIER, tier.tier());
        // The window is the whole difficulty of the mechanic, so it comes from the tier and the
        // lead-in is added on top rather than eaten out of it.
        verdict.entityData.set(CHARGE, TELEGRAPH_LEAD + tier.counterWindowTicks());
        verdict.setPos(target.getX(), target.getY(0.6D), target.getZ());
        return verdict;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_ID, -1);
        builder.define(CHARGE, 23);
        builder.define(TIER, AscendantTier.MIN_TIER);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = targetEntity();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isRemoved()) {
            discard();
            return;
        }
        setPos(target.getX(), target.getY(0.72D), target.getZ());
        if (tickCount == 1) {
            level.addFreshEntity(MagicCircleEffectEntity.createFollowing(
                    level, living, 1.6F, VERDICT_COLOR, chargeTicks() + 10,
                    MagicCircleEffectEntity.STYLE_MANA_FLIGHT));
            level.playSound(null, living.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.HOSTILE, 0.9F, 1.6F);
        }
        if (living instanceof ServerPlayer player && tickCount >= TELEGRAPH_LEAD && tickCount < chargeTicks()) {
            MagicCounterService.offerForcedCounter(player, this,
                    player.getEyePosition().add(0.0D, -0.25D, 0.0D), chargeTicks() - tickCount);
        }
        if (tickCount >= chargeTicks()) {
            if (living instanceof ServerPlayer player) {
                MagicCounterService.expirePrompt(player, this);
                land(level, player);
            }
            discard();
        }
    }

    /**
     * What ignoring the telegraph costs.
     *
     * <p>Damage is a share of the player's <em>max</em> health rather than a flat number, so the
     * promise the tiers make - one miss is survivable at full health, three are not - holds for a
     * player in leather and a player in netherite alike.
     */
    private void land(ServerLevel level, ServerPlayer player) {
        AscendantTier tier = tier();
        player.hurtServer(level, damageSources().indirectMagic(this, ownerEntity()),
                tier.failDamage(player.getMaxHealth()));
        applyDebuffs(player, tier);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                SoundSource.HOSTILE, 1.0F, 0.7F);
        player.displayClientMessage(Component.translatable("message.magical.verdict_landed"), true);
    }

    /** Cumulative: each tier keeps everything the tiers below it applied. */
    private static void applyDebuffs(ServerPlayer player, AscendantTier tier) {
        int rank = tier.tier();
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
        if (rank >= 7) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 160, 1));
        }
        if (rank >= 8) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
        }
        if (rank >= 9) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        }
        if (rank >= 10) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 160, 2));
            // Authority takes the answer as well as the health: half the pool you would have cast
            // your way out with.
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            state.setMana(state.mana() / 2);
            state.sync(player);
        }
    }

    private AscendantTier tier() {
        return AscendantTier.byTier(entityData.get(TIER)).orElse(AscendantTier.ECHO);
    }

    private int chargeTicks() {
        return entityData.get(CHARGE);
    }

    private Entity targetEntity() {
        Entity byId = level().getEntity(entityData.get(TARGET_ID));
        if (byId != null) {
            return byId;
        }
        if (!(level() instanceof ServerLevel level) || targetUuid == null) {
            return null;
        }
        return level.getEntity(targetUuid);
    }

    private Entity ownerEntity() {
        if (!(level() instanceof ServerLevel level) || ownerUuid == null) {
            return null;
        }
        return level.getEntity(ownerUuid);
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    /**
     * The verdict borrows Judgement's identity for the prompt.
     *
     * <p>The HUD needs a real skill to name and colour, and Judgement is what this is: a boss
     * gathering light overhead and dropping it on one target.
     */
    @Override
    public ResourceLocation counterSkillId() {
        return MagicContent.JUDGEMENT.id();
    }

    @Override
    public MagicAttribute counterAttribute() {
        return MagicContent.JUDGEMENT.attribute();
    }

    @Override
    public Entity counterOwner() {
        return ownerEntity();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill,
            Vec3 clashPosition) {
        onForcedCounter(level, defender, clashPosition);
    }

    /**
     * Answering staggers the boss.
     *
     * <p>Every other counter in the mod merely cancels what was coming. That is enough when the
     * counter is a spell you paid mana for, and not enough when it is a key press - reading the
     * tell has to buy an opening, or the mechanic is only a tax on inattention.
     */
    @Override
    public void onForcedCounter(ServerLevel level, ServerPlayer defender, Vec3 clashPosition) {
        MagicCounterService.spawnClash(level, clashPosition, MagicContent.JUDGEMENT.color(), VERDICT_COLOR);
        level.playSound(null, clashPosition.x, clashPosition.y, clashPosition.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 1.4F);
        if (ownerEntity() instanceof MagicOpponentEntity opponent) {
            opponent.stagger();
        }
        defender.displayClientMessage(Component.translatable("message.magical.verdict_countered"), true);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        if (tag.hasUUID("Target")) {
            targetUuid = tag.getUUID("Target");
        }
        entityData.set(TARGET_ID, tag.getInt("TargetId"));
        entityData.set(CHARGE, Math.max(1, tag.getInt("Charge")));
        entityData.set(TIER, tag.getInt("Tier"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
        tag.putInt("TargetId", entityData.get(TARGET_ID));
        tag.putInt("Charge", chargeTicks());
        tag.putInt("Tier", entityData.get(TIER));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource damageSource,
            float amount) {
        return false;
    }
}
