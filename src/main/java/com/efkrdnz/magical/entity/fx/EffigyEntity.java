package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A living decoy (the ArmorStand pattern: no AI, hittable, has HP) that mobs are lured onto. The
 * skill's behaviour is driven by the {@link SpellBehavior} registered for its skill id, which is
 * ticked here like a SpellEffectEntity's would be (via {@link #controller()}).
 */
public class EffigyEntity extends LivingEntity implements ProfiledEffect {
    private static final EntityDataAccessor<Integer> SKILL_INDEX = SynchedEntityData.defineId(EffigyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(EffigyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(EffigyEntity.class, EntityDataSerializers.INT);
    private ResourceLocation skillId = MagicContent.STARTER_SKILL;
    private UUID ownerUuid;
    private float burstDamage;
    private float burstRadius;
    private int lureRadius;
    private boolean burst;

    public EffigyEntity(EntityType<? extends EffigyEntity> type, Level level) {
        super(type, level);
        setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public static EffigyEntity create(ServerLevel level, MagicSkillDefinition definition, Entity owner, Vec3 pos, int life, float burstDamage, float burstRadius, int lureRadius, float health, int seed) {
        EffigyEntity effigy = new EffigyEntity(MagicalEntities.EFFIGY.get(), level);
        effigy.skillId = definition.id();
        effigy.entityData.set(SKILL_INDEX, MagicContent.skillIndex(definition.id()));
        effigy.entityData.set(LIFE, life);
        effigy.entityData.set(SEED, seed & 63);
        effigy.ownerUuid = owner != null ? owner.getUUID() : null;
        effigy.burstDamage = burstDamage;
        effigy.burstRadius = burstRadius;
        effigy.lureRadius = lureRadius;
        effigy.setPos(pos.x, pos.y, pos.z);
        effigy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        effigy.setHealth(health);
        return effigy;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL_INDEX, -1);
        builder.define(LIFE, 100);
        builder.define(SEED, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = ownerUuid != null ? level.getEntity(ownerUuid) : null;
        if (tickCount % 10 == 0) {
            for (LivingEntity hostile : com.efkrdnz.magical.magic.service.SkillTargets.hostilesWithin(level, owner, position(), lureRadius)) {
                if (hostile instanceof Mob mob) {
                    mob.setTarget(this);
                    mob.setLastHurtByMob(this);
                }
            }
        }
        if (tickCount >= life() || getHealth() <= 0.0F) {
            explode();
        }
    }

    private void explode() {
        if (burst || !(level() instanceof ServerLevel level)) {
            return;
        }
        burst = true;
        Entity owner = ownerUuid != null ? level.getEntity(ownerUuid) : null;
        MagicSkillDefinition definition = definition();
        Vec3 centre = position().add(0.0D, 1.0D, 0.0D);
        for (LivingEntity hostile : com.efkrdnz.magical.magic.service.SkillTargets.hostilesWithin(level, owner, centre, burstRadius)) {
            com.efkrdnz.magical.magic.service.SkillTargets.hurt(level, owner, hostile, burstDamage, definition, true);
            com.efkrdnz.magical.magic.status.MagicStatusService.apply(hostile, com.efkrdnz.magical.magic.status.MagicStatus.DAZZLED, 60, definition.id(), owner);
            hostile.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            hostile.igniteForSeconds(3.0F);
        }
        com.efkrdnz.magical.magic.visual.SpellFx.impact(level, definition, centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.0F);
        com.efkrdnz.magical.magic.visual.SpellFx.decal(level, definition, position(), new Vec3(0.0D, 1.0D, 0.0D), 2.0F);
        discard();
    }

    @Override
    public void die(DamageSource source) {
        explode();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() != null && ownerUuid != null && ownerUuid.equals(source.getEntity().getUUID())) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    public MagicSkillDefinition definition() {
        MagicSkillDefinition definition = MagicContent.get(skillId);
        return definition != null ? definition : MagicContent.get(MagicContent.STARTER_SKILL);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    // ---- LivingEntity plumbing (no equipment, no arms) ----

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return NonNullList.withSize(4, ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("SkillId"));
        if (parsed != null && MagicContent.get(parsed) != null) {
            skillId = parsed;
            entityData.set(SKILL_INDEX, MagicContent.skillIndex(parsed));
        }
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(SEED, tag.getInt("Seed"));
        burstDamage = tag.getFloat("BurstDamage");
        burstRadius = tag.getFloat("BurstRadius");
        lureRadius = tag.getInt("LureRadius");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkillId", skillId.toString());
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("Life", life());
        tag.putInt("Seed", entityData.get(SEED));
        tag.putFloat("BurstDamage", burstDamage);
        tag.putFloat("BurstRadius", burstRadius);
        tag.putInt("LureRadius", lureRadius);
    }

    // ---- ProfiledEffect ----

    @Override
    public int skillIndex() {
        return entityData.get(SKILL_INDEX);
    }

    @Override
    public int effectAge() {
        return tickCount;
    }

    @Override
    public int effectLife() {
        return life();
    }

    @Override
    public int effectSeed() {
        return entityData.get(SEED);
    }

    @Override
    public Vec3 effectDirection() {
        return Vec3.ZERO;
    }

    @Override
    public float effectValue() {
        return getHealth() / Math.max(1.0F, getMaxHealth());
    }

    @Override
    public int effectDrawMode() {
        return 1;
    }
}
