package com.efkrdnz.magical.magic.status;

import com.efkrdnz.magical.boss.unwaking.UnwakingCapabilities;
import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.StatusSyncPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * One service for every skill-applied status: apply / has / clear, victim sync for the statuses
 * the client must know about, and the shared gameplay consequences hooks consult.
 */
public final class MagicStatusService {
    private static final ResourceLocation REACH_MODIFIER = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "reach_clamped");
    private static final ResourceLocation SCALE_MODIFIER = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "compressed");
    private static final ResourceLocation SCALE_REACH_MODIFIER = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "compressed_reach");

    private MagicStatusService() {}

    private static void setModifier(LivingEntity target, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private static void clearModifier(LivingEntity target, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static MagicStatusData data(Entity entity) {
        return entity.getData(MagicalAttachments.MAGIC_STATUS);
    }

    private static long now(Entity entity) {
        return entity.level().getGameTime();
    }

    public static void apply(LivingEntity target, MagicStatus status, int ticks, int amplifier, float value, ResourceLocation sourceSkill, Entity source) {
        if (target.level().isClientSide()) {
            return;
        }
        if (UnwakingCapabilities.rejectsStatus(target, status)) return;
        // Null Field blanks the mod's own statuses outright; Building Tolerance only shortens them.
        if (com.efkrdnz.magical.magic.passive.ArcanePassives.blocksStatus(target)) {
            return;
        }
        int scaledTicks = Math.max(1, Math.round(ticks * com.efkrdnz.magical.magic.passive.ClassPassiveEffects.statusDurationScale(target, status)));
        long expiry = now(target) + scaledTicks;
        MagicStatusData.Entry existing = data(target).get(status);
        if (existing != null && existing.expiryTick() > expiry && existing.amplifier() >= amplifier) {
            return; // never shorten a stronger instance
        }
        data(target).put(status, new MagicStatusData.Entry(expiry, amplifier, value, sourceSkill, source != null ? source.getUUID() : null, target.getYRot(), target.getXRot()));
        onApplied(target, status, source);
        if (source instanceof ServerPlayer caster) {
            com.efkrdnz.magical.magic.ClassXpService.onStatusApplied(caster, target);
        }
        if (status.syncToVictim() && target instanceof ServerPlayer player) {
            MagicalNetwork.sendStatusSync(player, new StatusSyncPayload(status.ordinal(), scaledTicks, amplifier, value));
        }
    }

    public static void apply(LivingEntity target, MagicStatus status, int ticks, ResourceLocation sourceSkill, Entity source) {
        apply(target, status, ticks, 0, 0.0F, sourceSkill, source);
    }

    public static boolean has(Entity entity, MagicStatus status) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return data(entity).active(status, now(entity));
    }

    public static MagicStatusData.Entry entry(Entity entity, MagicStatus status) {
        if (!(entity instanceof LivingEntity)) {
            return null;
        }
        return has(entity, status) ? data(entity).get(status) : null;
    }

    public static int remainingTicks(Entity entity, MagicStatus status) {
        MagicStatusData.Entry entry = entry(entity, status);
        return entry == null ? 0 : (int) Math.max(0L, entry.expiryTick() - now(entity));
    }

    public static void clear(LivingEntity target, MagicStatus status) {
        if (target.level().isClientSide()) {
            return;
        }
        data(target).remove(status);
        onCleared(target, status);
        if (status.syncToVictim() && target instanceof ServerPlayer player) {
            MagicalNetwork.sendStatusSync(player, new StatusSyncPayload(status.ordinal(), 0, 0, 0.0F));
        }
    }

    /** Cheap per-entity upkeep called from the living tick hook. */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        MagicStatusData data = data(entity);
        if (data.entries().isEmpty()) {
            return;
        }
        long now = now(entity);
        for (MagicStatus status : MagicStatus.values()) {
            MagicStatusData.Entry entry = data.get(status);
            if (entry == null) {
                continue;
            }
            if (entry.expiryTick() <= now) {
                data.remove(status);
                onCleared(entity, status);
                if (status.syncToVictim() && entity instanceof ServerPlayer player) {
                    MagicalNetwork.sendStatusSync(player, new StatusSyncPayload(status.ordinal(), 0, 0, 0.0F));
                }
                continue;
            }
            upkeep(entity, status, entry);
        }
    }

    private static void onApplied(LivingEntity target, MagicStatus status, Entity source) {
        switch (status) {
            case ASLEEP -> {
                if (target instanceof Mob mob) {
                    mob.setNoAi(true);
                    mob.setTarget(null);
                }
            }
            case EXILED, DAZZLED -> {
                if (target instanceof Mob mob) {
                    mob.setTarget(null);
                }
            }
            case TAUNTED -> {
                if (target instanceof Mob mob && source instanceof LivingEntity living) {
                    mob.setTarget(living);
                    mob.setLastHurtByMob(living);
                }
            }
            case REACH_CLAMPED -> setModifier(target, Attributes.ENTITY_INTERACTION_RANGE, REACH_MODIFIER, -2.5D, AttributeModifier.Operation.ADD_VALUE);
            case COMPRESSED -> {
                MagicStatusData.Entry entry = data(target).get(status);
                double scale = entry != null && entry.value() > 0.0F ? entry.value() : 1.0D;
                setModifier(target, Attributes.SCALE, SCALE_MODIFIER, scale - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                setModifier(target, Attributes.ENTITY_INTERACTION_RANGE, SCALE_REACH_MODIFIER, scale - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            default -> { }
        }
    }

    private static void onCleared(LivingEntity target, MagicStatus status) {
        if (status == MagicStatus.ASLEEP && target instanceof Mob mob) {
            mob.setNoAi(false);
        }
        if (status == MagicStatus.PUPPETED && target instanceof Mob mob) {
            mob.setNoAi(false);
        }
        if (status == MagicStatus.REACH_CLAMPED) {
            clearModifier(target, Attributes.ENTITY_INTERACTION_RANGE, REACH_MODIFIER);
        }
        if (status == MagicStatus.COMPRESSED) {
            clearModifier(target, Attributes.SCALE, SCALE_MODIFIER);
            clearModifier(target, Attributes.ENTITY_INTERACTION_RANGE, SCALE_REACH_MODIFIER);
        }
    }

    private static void upkeep(LivingEntity entity, MagicStatus status, MagicStatusData.Entry entry) {
        switch (status) {
            case FACING_PINNED -> {
                entity.setYRot(entry.yaw());
                entity.setXRot(entry.pitch());
                entity.yHeadRot = entry.yaw();
                entity.yBodyRot = entry.yaw();
            }
            case ROOTED -> {
                entity.setDeltaMovement(0.0D, Math.min(0.0D, entity.getDeltaMovement().y), 0.0D);
                if (entity instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
            }
            case TAUNTED -> {
                if (entity instanceof Mob mob && entry.source() != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level && (mob.tickCount % 10) == 0) {
                    Entity source = level.getEntity(entry.source());
                    if (source instanceof LivingEntity living && living.isAlive()) {
                        mob.setTarget(living);
                    }
                }
            }
            case EXILED -> {
                if (entity instanceof Mob mob) {
                    mob.setTarget(null);
                }
            }
            case HARRIED -> {
                if (entity.isSprinting()) {
                    entity.setSprinting(false);
                }
            }
            default -> { }
        }
    }

    /** Casting is refused while any of these is active. */
    public static MagicStatus castBlocker(Entity entity) {
        for (MagicStatus status : new MagicStatus[] {MagicStatus.SILENCED, MagicStatus.EXILED, MagicStatus.ASLEEP, MagicStatus.PUPPETED, MagicStatus.POLYMORPHED}) {
            if (has(entity, status)) {
                return status;
            }
        }
        return null;
    }

    public static UUID sourceOf(Entity entity, MagicStatus status) {
        MagicStatusData.Entry entry = entry(entity, status);
        return entry == null ? null : entry.source();
    }
}
