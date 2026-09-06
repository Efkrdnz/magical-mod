package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.classes.BlacksmithInfusion;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A flying blade slash thrown by every melee swing. It sails forward as a curved crescent, cutting
 * each enemy it sweeps through once, and - if the weapon is runeforged - carries that rune's element
 * onto everything it hits. All damage is server-side; the renderer just draws the crescent from the
 * synced facing/size.
 */
public final class MeleeArcEntity extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HALF_WIDTH = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ARC = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.FLOAT);
    /** true = a stationary sharp cut struck in front (melee range); false = a flying slash wave. */
    private static final EntityDataAccessor<Boolean> CUT = SynchedEntityData.defineId(MeleeArcEntity.class, EntityDataSerializers.BOOLEAN);

    private float damage = 6.0F;
    private float knockback = 0.4F;
    private float speed = 0.95F;
    private float critChance;
    private int attributeOrdinal = -1;
    private int gradeIndex;
    private boolean divine;
    private UUID ownerUuid;
    private final Set<UUID> hitTargets = new HashSet<>();

    public MeleeArcEntity(EntityType<? extends MeleeArcEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    /** Throw a slash from {@code owner} along {@code direction}. {@code attribute} may be null (unforged). */
    public static MeleeArcEntity spawn(ServerLevel level, LivingEntity owner, Vec3 direction, int color, float halfWidth,
            float arcDegrees, float damage, float knockback, float speed, int life, float critChance, boolean cut,
            BlacksmithInfusion.RuneAttribute attribute, int gradeIndex, boolean divine) {
        MeleeArcEntity arc = new MeleeArcEntity(MagicalEntities.MELEE_ARC.get(), level);
        Vec3 dir = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        arc.ownerUuid = owner.getUUID();
        // Start a bit in front so a point-blank enemy takes the melee hit, not a double from the slash.
        double lead = cut ? 1.4D : 1.2D;
        arc.setPos(owner.getX() + dir.x * lead, owner.getY() + owner.getBbHeight() * 0.55D + dir.y * (lead * 0.8D), owner.getZ() + dir.z * lead);
        arc.damage = damage;
        arc.knockback = knockback;
        arc.speed = speed;
        arc.critChance = critChance;
        arc.attributeOrdinal = attribute == null ? -1 : attribute.ordinal();
        arc.gradeIndex = gradeIndex;
        arc.divine = divine;
        arc.entityData.set(COLOR, color);
        arc.entityData.set(HALF_WIDTH, halfWidth);
        arc.entityData.set(ARC, arcDegrees);
        arc.entityData.set(LIFE, life);
        arc.entityData.set(DIR_X, (float) dir.x);
        arc.entityData.set(DIR_Y, (float) dir.y);
        arc.entityData.set(DIR_Z, (float) dir.z);
        arc.entityData.set(CUT, cut);
        level.addFreshEntity(arc);
        return arc;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xD8E4FF);
        builder.define(HALF_WIDTH, 1.7F);
        builder.define(ARC, 150.0F);
        builder.define(LIFE, 12);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 1.0F);
        builder.define(CUT, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > entityData.get(LIFE)) {
            discard();
            return;
        }
        Vec3 dir = direction();
        Vec3 from = position();
        Vec3 to = from.add(dir.scale(speed));
        if (!level().isClientSide()) {
            BlockHitResult blockHit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS) {
                setPos(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z);
                sweep(dir);
                discard();
                return;
            }
            sweep(dir);
        }
        setPos(to.x, to.y, to.z);
    }

    private void sweep(Vec3 dir) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = ownerEntity();
        float half = entityData.get(HALF_WIDTH);
        BlacksmithInfusion.RuneAttribute attribute = attributeOrdinal >= 0
                ? BlacksmithInfusion.RuneAttribute.values()[attributeOrdinal] : null;
        AABB area = getBoundingBox().inflate(half + 1.0D);
        for (Entity entity : level.getEntities(this, area, candidate ->
                candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            if (!hitTargets.add(entity.getUUID())) {
                continue;
            }
            double reach = half + entity.getBbWidth() * 0.5D + 0.35D;
            if (entity.getBoundingBox().getCenter().subtract(position()).lengthSqr() > reach * reach) {
                hitTargets.remove(entity.getUUID()); // out of range this tick; allow a later tick to catch it
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            float dealt = damage;
            if (critChance > 0.0F && random.nextFloat() < critChance) {
                dealt *= 1.5F;
            }
            living.hurt(damageSources().indirectMagic(this, owner == null ? this : owner), dealt);
            Vec3 push = dir.scale(0.22D + knockback * 0.4D);
            living.push(push.x, 0.06D, push.z);
            if (attribute != null && owner instanceof ServerPlayer player) {
                BlacksmithInfusion.applyElement(level, player, living, attribute, gradeIndex, divine, dealt);
            }
        }
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(ownerUuid);
    }

    public Vec3 direction() {
        Vec3 dir = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        return dir.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : dir.normalize();
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float halfWidth() {
        return entityData.get(HALF_WIDTH);
    }

    public float arc() {
        return entityData.get(ARC);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public boolean cut() {
        return entityData.get(CUT);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(HALF_WIDTH, tag.getFloat("HalfWidth"));
        entityData.set(ARC, tag.getFloat("Arc"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(DIR_X, tag.getFloat("DirX"));
        entityData.set(DIR_Y, tag.getFloat("DirY"));
        entityData.set(DIR_Z, tag.getFloat("DirZ"));
        entityData.set(CUT, tag.getBoolean("Cut"));
        damage = tag.getFloat("Damage");
        knockback = tag.getFloat("Knockback");
        speed = tag.getFloat("Speed");
        critChance = tag.getFloat("Crit");
        attributeOrdinal = tag.getInt("Attribute");
        gradeIndex = tag.getInt("Grade");
        divine = tag.getBoolean("Divine");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("Color", color());
        tag.putFloat("HalfWidth", halfWidth());
        tag.putFloat("Arc", arc());
        tag.putInt("Life", life());
        Vec3 dir = direction();
        tag.putFloat("DirX", (float) dir.x);
        tag.putFloat("DirY", (float) dir.y);
        tag.putFloat("DirZ", (float) dir.z);
        tag.putBoolean("Cut", cut());
        tag.putFloat("Damage", damage);
        tag.putFloat("Knockback", knockback);
        tag.putFloat("Speed", speed);
        tag.putFloat("Crit", critChance);
        tag.putInt("Attribute", attributeOrdinal);
        tag.putInt("Grade", gradeIndex);
        tag.putBoolean("Divine", divine);
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
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource damageSource, float amount) {
        return false;
    }
}
