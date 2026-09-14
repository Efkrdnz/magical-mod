package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of the deep, called up: a tentacle, an eye or a maw, drawn from the geometry the user
 * modelled and moved by the code.
 *
 * <p>A spell effect with three more synced fields: which creature it is, what it is anchored to
 * (the ground where it erupted, its owner, or its target) and how large it is drawn. Everything
 * else - the owner, the target, the stats, the phase, the life, the scratch tag - is the effect
 * entity's, and the skill behaviours tick it like any other.
 */
public class EldritchConstructEntity extends SpellEffectEntity {
    public static final String MODEL_TENTACLE = "tentacle";
    public static final String MODEL_EYE = "eye";
    public static final String MODEL_MAW = "maw";

    public static final byte ANCHOR_GROUND = 0;
    public static final byte ANCHOR_OWNER = 1;
    public static final byte ANCHOR_TARGET = 2;

    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(EldritchConstructEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> ANCHOR = SynchedEntityData.defineId(EldritchConstructEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(EldritchConstructEntity.class, EntityDataSerializers.FLOAT);

    /** Renderer memory: where the eye is looking now, eased toward where it should. Client only. */
    public float gazeYaw;
    public float gazePitch;

    public EldritchConstructEntity(EntityType<? extends EldritchConstructEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static EldritchConstructEntity spawn(CastContext ctx, String model, byte anchor, Vec3 pos, int life, float radius, float scale, Vec3 dir) {
        SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), pos, life, radius, dir, (int) (ctx.seed() & 63));
        template.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
        return place(ctx.level(), template, model, anchor, pos, scale);
    }

    /** A construct a running effect calls up beside itself: same skill, owner and stats. */
    public static EldritchConstructEntity spawnChild(SpellEffectEntity parent, String model, byte anchor, Vec3 pos, int life, float radius, float scale, Vec3 dir) {
        SpellEffectEntity template = SpellEffectEntity.create(parent.serverLevel(), parent.definition(), null, parent.owner(), pos, life, radius, dir, parent.seed());
        template.copyStatsFrom(parent);
        // Mode bit 2 marks a child of a controller: never offered as a counter, never sneak-flipped.
        template.setMode((byte) 2);
        return place(parent.serverLevel(), template, model, anchor, pos, scale);
    }

    private static EldritchConstructEntity place(ServerLevel level, SpellEffectEntity template, String model, byte anchor, Vec3 pos, float scale) {
        EldritchConstructEntity entity = new EldritchConstructEntity(MagicalEntities.ELDRITCH_CONSTRUCT.get(), level);
        CompoundTag tag = new CompoundTag();
        template.addAdditionalSaveData(tag);
        entity.readAdditionalSaveData(tag);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.entityData.set(MODEL, model);
        entity.entityData.set(ANCHOR, anchor);
        entity.entityData.set(SCALE, scale);
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODEL, MODEL_TENTACLE);
        builder.define(ANCHOR, ANCHOR_GROUND);
        builder.define(SCALE, 1.0F);
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public byte anchor() {
        return entityData.get(ANCHOR);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public void setScale(float scale) {
        entityData.set(SCALE, scale);
    }

    /** The constructs of one skill this owner has standing within range of them. */
    public static List<EldritchConstructEntity> ownedBy(ServerLevel level, Entity owner, ResourceLocation skillId, double range) {
        return level.getEntities(MagicalEntities.ELDRITCH_CONSTRUCT.get(), owner.getBoundingBox().inflate(range),
                construct -> construct.owner() == owner && skillId.equals(construct.skillId()) && !construct.isRemoved());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Model", model());
        tag.putByte("Anchor", anchor());
        tag.putFloat("Scale", scale());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Model")) {
            entityData.set(MODEL, tag.getString("Model"));
            entityData.set(ANCHOR, tag.getByte("Anchor"));
            entityData.set(SCALE, tag.getFloat("Scale"));
        }
    }
}
