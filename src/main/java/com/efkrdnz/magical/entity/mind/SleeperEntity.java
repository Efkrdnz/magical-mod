package com.efkrdnz.magical.entity.mind;

import com.efkrdnz.magical.magic.mind.DreamService;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A dreamer's body, lying where they fell asleep. It takes no harm of its own: a blow on it wakes the
 * dreamer and lands on them instead. Never saved; a body whose dreamer has gone is just gone.
 */
public class SleeperEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> DREAMER = SynchedEntityData.defineId(SleeperEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(SleeperEntity.class, EntityDataSerializers.STRING);

    /** How long a body nobody is dreaming in may lie before it unmakes itself: long enough for a capture. */
    public static final int ORPHAN_GRACE_TICKS = 200;

    public SleeperEntity(EntityType<? extends SleeperEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    /** The dreamer's body, where they stand, facing the way they face. */
    public static SleeperEntity of(ServerPlayer dreamer) {
        SleeperEntity body = new SleeperEntity(MagicalEntities.SLEEPER.get(), dreamer.serverLevel());
        body.moveTo(dreamer.getX(), dreamer.getY(), dreamer.getZ(), dreamer.getYRot(), 0.0F);
        body.yBodyRot = dreamer.getYRot();
        body.yHeadRot = dreamer.getYRot();
        body.entityData.set(DREAMER, Optional.of(dreamer.getUUID()));
        body.entityData.set(NAME, dreamer.getGameProfile().getName());
        return body;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DREAMER, Optional.empty());
        builder.define(NAME, "");
    }

    public Optional<UUID> dreamer() {
        return entityData.get(DREAMER);
    }

    public String dreamerName() {
        return entityData.get(NAME);
    }

    /** A body nobody is dreaming in - the capture command's, or one a lost session left - does not lie there for good. */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= ORPHAN_GRACE_TICKS && tickCount % 20 == 0 && !DreamService.isLiveBody(this)) {
            discard();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        DreamService.sleeperStruck(this, level, source, amount);
        return false;
    }

    @Override
    public Component getName() {
        String name = dreamerName();
        return name.isEmpty() ? super.getName() : Component.literal(name);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** A body stays where its dreamer lay, or the dreamer would wake somewhere else. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
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
}
