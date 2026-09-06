package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A weapon wrenched out of someone's hand: hovers untouchable under the skill's sigil for a while,
 * then flies home to the hand it came from (or drops at the owner's feet if that hand is full).
 */
public class WrenchedItemEntity extends Entity implements ProfiledEffect {
    private static final EntityDataAccessor<Integer> SKILL_INDEX = SynchedEntityData.defineId(WrenchedItemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(WrenchedItemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(WrenchedItemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(WrenchedItemEntity.class, EntityDataSerializers.ITEM_STACK);

    private ResourceLocation skillId = MagicContent.STARTER_SKILL;
    private UUID victimUuid;
    private boolean offHand;
    private boolean returned;

    public WrenchedItemEntity(EntityType<? extends WrenchedItemEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static WrenchedItemEntity create(ServerLevel level, MagicSkillDefinition definition, LivingEntity victim, ItemStack stack, boolean offHand, Vec3 pos, int life, int seed) {
        WrenchedItemEntity entity = new WrenchedItemEntity(MagicalEntities.WRENCHED_ITEM.get(), level);
        entity.skillId = definition.id();
        entity.entityData.set(SKILL_INDEX, MagicContent.skillIndex(definition.id()));
        entity.entityData.set(LIFE, life);
        entity.entityData.set(SEED, seed & 63);
        entity.entityData.set(STACK, stack.copy());
        entity.victimUuid = victim.getUUID();
        entity.offHand = offHand;
        entity.setPos(pos.x, pos.y, pos.z);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SKILL_INDEX, -1);
        builder.define(LIFE, 60);
        builder.define(SEED, 0);
        builder.define(STACK, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (tickCount >= life()) {
            returnHome(level);
        }
    }

    private void returnHome(ServerLevel level) {
        if (returned) {
            return;
        }
        returned = true;
        ItemStack stack = stack();
        Entity victim = victimUuid != null ? level.getEntity(victimUuid) : null;
        if (victim instanceof LivingEntity living && living.isAlive()) {
            EquipmentSlot slot = offHand ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
            if (living.getItemBySlot(slot).isEmpty()) {
                living.setItemSlot(slot, stack);
            } else {
                living.spawnAtLocation(level, stack);
            }
        } else if (!stack.isEmpty()) {
            spawnAtLocation(level, stack);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!returned && level() instanceof ServerLevel level && reason != RemovalReason.UNLOADED_TO_CHUNK && reason != RemovalReason.UNLOADED_WITH_PLAYER) {
            returnHome(level);
        }
        super.remove(reason);
    }

    public ItemStack stack() {
        return entityData.get(STACK);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("SkillId"));
        if (parsed != null && MagicContent.get(parsed) != null) {
            skillId = parsed;
            entityData.set(SKILL_INDEX, MagicContent.skillIndex(parsed));
        }
        if (tag.hasUUID("Victim")) {
            victimUuid = tag.getUUID("Victim");
        }
        offHand = tag.getBoolean("OffHand");
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(SEED, tag.getInt("Seed"));
        HolderLookup.Provider provider = level().registryAccess();
        entityData.set(STACK, tag.contains("Item") ? ItemStack.parseOptional(provider, tag.getCompound("Item")) : ItemStack.EMPTY);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("SkillId", skillId.toString());
        if (victimUuid != null) {
            tag.putUUID("Victim", victimUuid);
        }
        tag.putBoolean("OffHand", offHand);
        tag.putInt("Life", life());
        tag.putInt("Seed", entityData.get(SEED));
        ItemStack stack = stack();
        if (!stack.isEmpty()) {
            tag.put("Item", stack.save(level().registryAccess()));
        }
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
    public int effectDrawMode() {
        return 1;
    }
}
